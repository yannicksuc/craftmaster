"""The régie agent: a Claude agent that edits the show's setup during the show (docs/wiki/RegieIA.md).

LordFinn speaks (or types /ia …); the quick understanding routes "change the setup" requests here (agent.request).
The agent runs on this PC, in this service, with five tools (TOOLS). Its tool calls cannot reach the game server
directly: they are queued in the Job, the game client polls them (GET /agent/poll), sends each to the server
(packet AgentCall, run as LordFinn with his permissions), and posts the answer back (POST /agent/result).
The final reply goes to LordFinn's chat.

    API key present  -> ApiRunner: Messages API tool loop (anthropic SDK)
    otherwise        -> CliRunner: `claude -p` (Claude subscription) with an MCP server (regie/agent_mcp.py)
                        that forwards each tool call to this service (POST /agent/tool)

Follow-ups keep the conversation for AGENT_MEMORY_S (« oui, confirme », « annule ce que tu as fait »).
"""
from __future__ import annotations

import json
import logging
import os
import re
import tempfile
import threading
import time
import uuid
from dataclasses import dataclass, field

from .agent_prompt import AGENT_PROMPT
from .config import Settings, api_key_present

log = logging.getLogger("regie.agent")

AGENT_MEMORY_S = 20 * 60
TOOL_TIMEOUT_S = 45
JOB_TIMEOUT_S = 600
CLIENT_GONE_S = 15           # the game stopped polling: the job is abandoned
MAX_TOOL_CALLS = 60
VISION_TIMEOUT_S = 300      # « voir »: the game draws every picture (a long intro video takes a while)
ADDRESS = re.compile(r"\b\d{1,3}(?:\.\d{1,3}){3}(?::\d{1,5})?\b")

TOOLS = [
    {"name": "lire",
     "description": "Lit l'état du show (rien n'est changé).",
     "input_schema": {"type": "object", "properties": {
         "sujet": {"type": "string", "enum": ["resume", "manches", "intro", "effets", "ambiance", "sequence", "conduite", "cameras",
                                              "trajet", "projecteurs", "arenes", "zone", "mecaniques", "musiques", "joueurs", "blocs", "commandes",
                                              "statut", "journal", "log"]},
         "nom": {"type": "string", "description": "Le nom de l'ambiance, de la séquence, de la conduite, du travelling, la manche de "
                                                  "l'intro (id ou nom : la-pomme, « La pomme »), l'arène, le groupe de commandes "
                                                  "(« tv intro », « camera »), la salle (zone : pomme), le pack de la salle (mecaniques : pomme), le bloc à chercher autour de LordFinn (fleur, pot, coffre, minecraft:poppy), "
                                                  "ou un mot à chercher dans le log."}},
         "required": ["sujet"]}},
    {"name": "commande",
     "description": "Exécute une commande du serveur au nom de LordFinn (toutes les /cm … et les commandes du serveur) et renvoie sa "
                    "réponse. Le réglage du show passe tout de suite ; ce qui supprime, ce que les joueurs ou le stream voient, et les "
                    "commandes du serveur attendent son clic (réponse « CONFIRMATION REQUISE »).",
     "input_schema": {"type": "object", "properties": {
         "ligne": {"type": "string", "description": "La commande, sans /, ex. « cm tv intro la-pomme plan 3 effet macro »."}},
         "required": ["ligne"]}},
    {"name": "completer",
     "description": "Ce que la touche Tab propose après un début de commande (mots possibles, arguments, infobulles) : pour découvrir "
                    "une commande sans deviner.",
     "input_schema": {"type": "object", "properties": {
         "ligne": {"type": "string", "description": "Le début de la commande, sans /, ex. « cm tv intro la-pomme plan 3 »."}},
         "required": ["ligne"]}},
    {"name": "voir",
     "description": "Regarde ce que filme une caméra, un travelling ou une intro, dans le rendu du flux de l'émission (effets, look et "
                    "focus compris) : tu reçois les images, LordFinn aussi dans son chat. Rien n'est changé ni joué à l'antenne.",
     "input_schema": {"type": "object", "properties": {
         "cible": {"type": "string", "enum": ["camera", "trajet", "intro", "plan"]},
         "nom": {"type": "string", "description": "camera : la-pomme:4 (ou 4 avec manche) ; trajet : son nom ; intro : la manche."},
         "manche": {"type": "string", "description": "La manche (id) : pour intro, plan, ou une caméra donnée par son numéro."},
         "plan": {"type": "integer", "description": "cible plan : le numéro du plan de l'intro."},
         "images": {"type": "integer", "description": "Images par plan (intro, plan) ou le long du trajet (défaut 1, 3, 6)."},
         "secondes": {"type": "string", "description": "camera / trajet : les instants à montrer, ex. « 0 2 4 »."},
         "effet": {"type": "string", "description": "camera / trajet : un effet à essayer sans rien changer (macro, fisheye+vignette…)."},
         "focus": {"type": "string", "description": "camera : une mise au point à essayer, ex. « de 3 vers fond duree 2 »."},
         "video": {"type": "boolean", "description": "intro : 8 images par seconde, assemblées en vidéo pour LordFinn (tu reçois la planche)."}},
         "required": ["cible"]}},
    {"name": "wiki",
     "description": "Lit la documentation du mod (pages du wiki) : sans argument la liste, page = une page, cherche = les lignes qui "
                    "contiennent un mot.",
     "input_schema": {"type": "object", "properties": {
         "page": {"type": "string", "description": "ex. Effets, Realisation, Commandes, Arenes, RegieIA."},
         "cherche": {"type": "string"}}}},
    {"name": "ecrire_sequence",
     "description": "Crée ou remplace une timeline (séquence) entière.",
     "input_schema": {"type": "object", "properties": {
         "nom": {"type": "string"},
         "sequence": {"type": "object", "description": "La séquence complète : duree, boucle, description, pistes."}},
         "required": ["nom", "sequence"]}},
    {"name": "confirmer",
     "description": "Exécute une suppression en attente, quand LordFinn vient de la confirmer dans sa demande.",
     "input_schema": {"type": "object", "properties": {"numero": {"type": "integer"}}, "required": ["numero"]}},
    {"name": "annuler",
     "description": "Défait tout ce que la demande précédente a changé (scénographie, caméras, travellings).",
     "input_schema": {"type": "object", "properties": {}}},
]
TOOL_NAMES = {t["name"] for t in TOOLS}
LOCAL_TOOLS = {"wiki"}       # answered by this service, not by the game


def mcp_tools() -> list[dict]:
    return [{"name": t["name"], "description": t["description"], "inputSchema": t["input_schema"]} for t in TOOLS]


def mask(text: str) -> str:
    return ADDRESS.sub("‹adresse masquée›", text or "")


# ---------------------------------------------------------------------------------------------- jobs


@dataclass
class Call:
    cid: int
    tool: str
    args: dict
    event: threading.Event = field(default_factory=threading.Event)
    ok: bool = False
    output: str = ""


class Job:
    def __init__(self, text: str, context: str, player: str):
        self.id = uuid.uuid4().hex[:16]
        self.text, self.context, self.player = text, context, player
        self.created = self.last_poll = time.time()
        self.lock = threading.Lock()
        self.queue: list[Call] = []          # waiting to be taken by the game
        self.calls: dict[int, Call] = {}     # taken, waiting for the answer
        self.next_cid = 1
        self.count = 0
        self.done = False
        self.reply = ""
        self.error: str | None = None
        self.steps: list[str] = []           # "lire resume", "commande …" (progress, log)

    def alive(self) -> bool:
        return not self.done and time.time() - self.last_poll < CLIENT_GONE_S and time.time() - self.created < JOB_TIMEOUT_S

    def call(self, tool: str, args: dict) -> tuple[bool, str]:
        """Called from the agent's thread: queue the call for the game, wait for its answer."""
        if tool not in TOOL_NAMES:
            return False, f"Outil inconnu : {tool}"
        if tool in LOCAL_TOOLS:
            from .vision import wiki
            ok, out = wiki(args if isinstance(args, dict) else {})
            with self.lock:
                self.steps.append(tool + " " + json.dumps(args, ensure_ascii=False)[:160])
            return ok, mask(out)
        with self.lock:
            if self.count >= MAX_TOOL_CALLS:
                return False, "Trop d'appels pour une seule demande : arrête-toi et résume ce qui est fait."
            self.count += 1
            c = Call(self.next_cid, tool, args if isinstance(args, dict) else {})
            self.next_cid += 1
            self.queue.append(c)
            self.steps.append(tool + " " + json.dumps(args, ensure_ascii=False)[:160])
        deadline = time.time() + (VISION_TIMEOUT_S if tool == "voir" else TOOL_TIMEOUT_S)
        while not c.event.wait(0.25):
            if time.time() > deadline or not self.alive():
                return False, "Le jeu ne répond plus (déconnecté ?) : rien de plus ne peut être fait."
        from .vision import text as vision_text
        log.info("[%s] %s %s -> %s", self.id, tool, json.dumps(args, ensure_ascii=False)[:300], vision_text(c.output)[:300].replace("\n", " | "))
        return c.ok, c.output

    def take(self) -> list[dict]:
        with self.lock:
            self.last_poll = time.time()
            out = []
            for c in self.queue:
                self.calls[c.cid] = c
                out.append({"cid": c.cid, "tool": c.tool, "args": c.args})
            self.queue.clear()
            return out

    def answer(self, cid: int, ok: bool, output: str) -> bool:
        with self.lock:
            self.last_poll = time.time()
            c = self.calls.pop(cid, None)
        if c is None:
            return False
        c.ok, c.output = ok, mask(output)
        c.event.set()
        return True

    def finish(self, reply: str, error: str | None = None) -> None:
        self.reply, self.error, self.done = mask(reply).strip(), error, True


class AgentManager:
    def __init__(self, settings: Settings, runner=None):
        self.settings = settings
        self.jobs: dict[str, Job] = {}
        self.lock = threading.Lock()
        self.base_url = f"http://127.0.0.1:{settings.port}"
        if runner is None:
            runner = ApiRunner(settings) if api_key_present() and settings.understanding != "abonnement" else CliRunner(settings, self.base_url)
        self.runner = runner
        self.last: tuple[float, str] | None = None   # (time, reply) of the last finished request

    def recent_note(self, within_s: float = 180) -> str:
        """For the quick understanding: what the agent just said, so that « oui », « la deuxième », « annule ça »
        go back to it."""
        if not self.last or time.time() - self.last[0] > within_s:
            return ""
        return ("Conversation en cours avec l'agent de régie, qui vient de répondre : « " + self.last[1][:400] + " ». "
                "Une réponse à cette réponse (oui, non, confirme, un choix, une précision, « annule ce que tu as fait ») "
                "= agent_request.")

    def start(self, text: str, context: str = "", player: str = "") -> Job:
        job = Job(text, context, player)
        with self.lock:
            # Old jobs go; a running one of the same player is left to finish (its answers still arrive)
            for k in [k for k, j in self.jobs.items() if j.done and time.time() - j.created > 600]:
                del self.jobs[k]
            self.jobs[job.id] = job
        threading.Thread(target=self._run, args=(job,), name=f"agent-{job.id}", daemon=True).start()
        log.info("[%s] %s: %r", job.id, player or "?", text)
        return job

    def get(self, job_id: str) -> Job | None:
        with self.lock:
            return self.jobs.get(job_id)

    def _run(self, job: Job) -> None:
        t = time.perf_counter()
        try:
            reply = self.runner.run(job)
            job.finish(reply or "C'est fait.")
        except Exception as e:  # never kill the service; say it in the chat
            log.exception("[%s] agent failed", job.id)
            job.finish(f"L'agent a échoué ({type(e).__name__}) : {str(e)[:200]}", error="agent_error")
        self.last = (time.time(), job.reply)
        log.info("[%s] done in %.1f s, %d tool call(s): %s", job.id, time.perf_counter() - t, job.count, job.reply)


def user_text(job: Job) -> str:
    parts = [f"<demande>{job.text}</demande>"]
    if job.context:
        parts.insert(0, f"<contexte_emission>\n{job.context[:6000]}\n</contexte_emission>")
    return "\n".join(parts)


# ---------------------------------------------------------------------------------------------- API runner


class ApiRunner:
    """Messages API tool loop; memory = the last exchanges as plain text (request -> final reply)."""

    def __init__(self, settings: Settings, client=None):
        self.settings = settings
        self._client = client
        self.memory: list[tuple[float, str, str]] = []

    def client(self):
        if self._client is None:
            import anthropic
            self._client = anthropic.Anthropic(timeout=60, max_retries=1)
        return self._client

    def run(self, job: Job) -> str:
        now = time.time()
        self.memory = [m for m in self.memory if now - m[0] < AGENT_MEMORY_S][-6:]
        messages = []
        for _, q, a in self.memory:
            messages += [{"role": "user", "content": q}, {"role": "assistant", "content": a or "(fait)"}]
        messages.append({"role": "user", "content": user_text(job)})
        s = self.settings
        final = ""
        for _ in range(MAX_TOOL_CALLS):
            msg = self.client().messages.create(
                model=agent_model(s), max_tokens=8000, tools=TOOLS,
                system=[{"type": "text", "text": AGENT_PROMPT, "cache_control": {"type": "ephemeral"}}],
                messages=messages, output_config={"effort": agent_effort(s)})
            if getattr(msg, "stop_reason", None) == "refusal":
                return "Claude a refusé cette demande."
            messages.append({"role": "assistant", "content": msg.content})
            texts = [b.text for b in msg.content if getattr(b, "type", "") == "text" and b.text.strip()]
            if texts:
                final = " ".join(texts)
            uses = [b for b in msg.content if getattr(b, "type", "") == "tool_use"]
            if not uses:
                break
            results = []
            for b in uses:
                if not job.alive():
                    return "Le jeu ne répond plus : arrêté."
                ok, out = job.call(b.name, dict(b.input) if isinstance(b.input, dict) else {})
                from .vision import blocks
                content = blocks(out, "api") if ok else None
                results.append({"type": "tool_result", "tool_use_id": b.id, "content": content or out, "is_error": not ok})
            messages.append({"role": "user", "content": results})
        self.memory.append((time.time(), user_text(job), final))
        return final


# ---------------------------------------------------------------------------------------------- Claude Code runner


def _to_wsl_path(path: str) -> str:
    drive, rest = os.path.splitdrive(os.path.abspath(path))
    return "/mnt/" + drive[0].lower() + rest.replace("\\", "/")


def agent_model(s: Settings) -> str:
    return os.environ.get("REGIE_AGENT_MODEL", "").strip() or s.model


def agent_effort(s: Settings) -> str:
    return os.environ.get("REGIE_AGENT_EFFORT", "").strip() or "medium"


class CliRunner:
    """`claude -p` with the craftmaster MCP server; memory = the Claude Code session, resumed (--resume)."""

    def __init__(self, settings: Settings, base_url: str, runner=None):
        self.settings = settings
        self.base_url = base_url
        self.runner = runner
        self.session: tuple[float, str] | None = None
        fd, self.prompt_file = tempfile.mkstemp(prefix="regie-agent-", suffix=".md")
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(AGENT_PROMPT)
        self.lock = threading.Lock()   # one Claude Code session at a time (the resumed session is shared)

    def base_command(self) -> tuple[list[str], bool]:
        override = os.environ.get("REGIE_CLAUDE_CMD", "").strip()
        import shlex
        import shutil
        if override:
            base = shlex.split(override, posix=os.name != "nt")
            return base, os.path.basename(base[0]).lower().startswith("wsl")
        if os.name == "nt" and not shutil.which("claude"):
            return ["wsl.exe", "-d", os.environ.get("REGIE_WSL_DISTRO", "Ubuntu"), "--exec", "bash", "-lc",
                    'cd /tmp && exec claude "$@"', "claude"], True
        return ["claude"], False

    def mcp_config(self, job: Job, via_wsl: bool) -> str:
        import sys
        script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "agent_mcp.py")
        python = sys.executable
        if via_wsl:   # Claude Code in WSL starts the Windows Python (interop): 127.0.0.1 is then this PC
            cfg = {"command": _to_wsl_path(python), "args": [script.replace("\\", "/"), self.base_url, job.id]}
        else:
            cfg = {"command": python, "args": [script, self.base_url, job.id]}
        fd, path = tempfile.mkstemp(prefix="regie-mcp-", suffix=".json")
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump({"mcpServers": {"craftmaster": {"type": "stdio", **cfg}}}, f)
        return path

    def run(self, job: Job) -> str:
        import subprocess
        with self.lock:
            base, via_wsl = self.base_command()
            cfg = self.mcp_config(job, via_wsl)
            try:
                s = self.settings
                argv = base + ["-p", "--model", agent_model(s), "--effort", agent_effort(s),
                               "--tools", "", "--strict-mcp-config",
                               "--mcp-config", _to_wsl_path(cfg) if via_wsl else cfg,
                               "--allowedTools", "mcp__craftmaster", "--setting-sources", "", "--disable-slash-commands",
                               "--max-turns", str(MAX_TOOL_CALLS),
                               "--system-prompt-file", _to_wsl_path(self.prompt_file) if via_wsl else self.prompt_file,
                               "--output-format", "json"]
                if self.session and time.time() - self.session[0] < AGENT_MEMORY_S:
                    argv += ["--resume", self.session[1]]
                if self.runner:
                    out = self.runner(argv, user_text(job), JOB_TIMEOUT_S)
                else:
                    p = subprocess.run(argv, input=user_text(job), capture_output=True, text=True, encoding="utf-8",
                                       timeout=JOB_TIMEOUT_S, cwd=tempfile.gettempdir(),
                                       creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
                    if p.returncode != 0 and not p.stdout.strip():
                        raise RuntimeError(f"claude a quitté avec le code {p.returncode} : {p.stderr.strip()[:300]}")
                    out = p.stdout
                data = json.loads(out)
                if data.get("session_id"):
                    self.session = (time.time(), data["session_id"])
                if data.get("is_error") and not data.get("result"):
                    raise RuntimeError(str(data.get("subtype") or "erreur"))
                return str(data.get("result") or "")
            except subprocess.TimeoutExpired:
                return "Trop long : j'ai arrêté (une partie peut être faite, regarde le journal)."
            finally:
                try:
                    os.remove(cfg)
                except OSError:
                    pass
