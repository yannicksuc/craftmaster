"""Understanding through Claude Code (`claude -p`) and the user's Claude subscription: no API key needed.

Same prompt and same action list as the API path (regie.understand), but Claude Code has no custom tools in print
mode, so the actions come back as structured JSON ({"actions": [{"tool", "input"}], "reply"}) checked against a
JSON schema, then go through the same parse/validation as tool calls. Slower than the API (about 4-6 s per order,
Claude Code starts each time) but free with the subscription.

On Windows, Claude Code usually lives in WSL: the command goes through `wsl.exe` when `claude` is not on the PATH.
The default WSL distribution can be another one (Docker Desktop's): REGIE_WSL_DISTRO picks it (Ubuntu).
REGIE_CLAUDE_CMD overrides the command (e.g. "claude" or "wsl.exe -e /home/me/.local/bin/claude").
"""
from __future__ import annotations

import json
import logging
import os
import shlex
import shutil
import subprocess
import tempfile
import time
from types import SimpleNamespace

from .config import Settings
from .context import ShowContext
from .prompt import SYSTEM_PROMPT, user_message
from .understand import Understander, Understanding

log = logging.getLogger("regie.subscription")

OUTPUT_RULES = """

## Format de réponse (mode abonnement)
Tu n'as pas d'outils à appeler ici : tu réponds UNIQUEMENT par l'objet JSON demandé.
- "actions" : la liste des outils que tu aurais appelés, dans l'ordre, chacun {"tool": <nom d'outil>, "input": {...}}
  avec exactement les champs de son input_schema ci-dessous (les mêmes règles que si tu appelais les outils).
- "reply" : une phrase courte pour le régisseur si tu n'as aucune action à faire, sinon "".
Pour dire quelque chose (doute, question), utilise l'outil "say" comme d'habitude.

## Outils disponibles
"""


def output_schema(tool_names: list[str]) -> dict:
    return {
        "type": "object",
        "properties": {
            "actions": {"type": "array", "items": {
                "type": "object",
                "properties": {"tool": {"type": "string", "enum": tool_names}, "input": {"type": "object"}},
                "required": ["tool", "input"]}},
            "reply": {"type": "string"},
        },
        "required": ["actions", "reply"],
    }


def _to_wsl_path(path: str) -> str:
    drive, rest = os.path.splitdrive(os.path.abspath(path))
    return "/mnt/" + drive[0].lower() + rest.replace("\\", "/")


class SubscriptionUnderstander(Understander):
    """runner(argv, stdin_text, timeout) -> stdout text; replaceable in tests."""

    def __init__(self, settings: Settings, runner=None):
        super().__init__(settings, client=None)
        self.runner = runner or self._run
        self.schema = output_schema([t["name"] for t in self.tools])
        tools_doc = json.dumps([{"name": t["name"], "description": t["description"], "input_schema": t["input_schema"]}
                                for t in self.tools], ensure_ascii=False)
        self.system_prompt = SYSTEM_PROMPT + OUTPUT_RULES + tools_doc
        # The prompt is ~37 kB: too long for a Windows command line, so it goes in a file
        fd, self._prompt_file = tempfile.mkstemp(prefix="regie-prompt-", suffix=".md")
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(self.system_prompt)

    def command(self) -> list[str]:
        override = os.environ.get("REGIE_CLAUDE_CMD", "").strip()
        via_wsl = False
        if override:
            base = shlex.split(override, posix=os.name != "nt")
            via_wsl = os.path.basename(base[0]).lower().startswith("wsl")
        elif os.name == "nt" and not shutil.which("claude"):
            # Login shell so ~/.local/bin is on the PATH; cd to a neutral folder so no project file is picked up
            base = ["wsl.exe", "-d", os.environ.get("REGIE_WSL_DISTRO", "Ubuntu"), "--exec", "bash", "-lc", 'cd /tmp && exec claude "$@"', "claude"]
            via_wsl = True
        else:
            base = ["claude"]
        prompt_file = _to_wsl_path(self._prompt_file) if via_wsl else self._prompt_file
        s = self.settings
        return base + [
            "-p", "--model", s.model, "--effort", s.effort,
            "--tools", "", "--strict-mcp-config", "--setting-sources", "", "--disable-slash-commands",
            "--no-session-persistence", "--system-prompt-file", prompt_file,
            "--output-format", "json", "--json-schema", json.dumps(self.schema, separators=(",", ":")),
        ]

    def _run(self, argv: list[str], stdin_text: str, timeout: float) -> str:
        cwd = tempfile.gettempdir()
        p = subprocess.run(argv, input=stdin_text, capture_output=True, text=True, encoding="utf-8",
                           timeout=timeout, cwd=cwd,
                           creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        if p.returncode != 0 and not p.stdout.strip():
            raise RuntimeError(f"claude a quitté avec le code {p.returncode} : {p.stderr.strip()[:300]}")
        return p.stdout

    def understand(self, ctx: ShowContext, transcript: str) -> Understanding:
        if not transcript.strip():
            return Understanding(reply="Je n'ai rien entendu.", error="empty_transcript")
        t = time.perf_counter()
        try:
            out = self.runner(self.command(), user_message(ctx.summary(), transcript),
                              max(self.settings.timeout_s, 30.0))
            data = json.loads(out)
            if data.get("is_error"):
                raise RuntimeError(str(data.get("result") or data.get("subtype") or "erreur")[:300])
            answer = data.get("structured_output")
            if answer is None:
                answer = json.loads(data.get("result") or "{}")
        except FileNotFoundError:
            return Understanding(reply="Claude Code introuvable sur ce PC (commande claude).", error="claude_error",
                                 seconds=time.perf_counter() - t)
        except Exception as e:  # timeout, bad JSON, not logged in...: say it, never crash the service
            log.warning("Claude Code call failed: %s: %s", type(e).__name__, e)
            return Understanding(reply=f"Claude injoignable ({type(e).__name__}).", error="claude_error",
                                 seconds=time.perf_counter() - t)
        blocks = [SimpleNamespace(type="tool_use", name=a.get("tool"), input=a.get("input") or {})
                  for a in answer.get("actions") or [] if isinstance(a, dict)]
        if answer.get("reply", "").strip():
            blocks.append(SimpleNamespace(type="text", text=answer["reply"]))
        result = self.parse(SimpleNamespace(content=blocks, stop_reason="end_turn", usage=None), ctx)
        result.seconds = time.perf_counter() - t
        return result
