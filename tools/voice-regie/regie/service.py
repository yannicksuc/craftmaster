"""The local HTTP service the mod talks to (127.0.0.1 only).

GET  /health   -> {ok, whisper, apiKey, model}
POST /command  <- {"audio": base64 WAV (16 kHz mono 16-bit), "context": {...}}   (or "text" instead of "audio")
               -> {transcript, actions[], reply, warnings[], error, timings{stt, claude, total}, agentJob}
Régie agent (regie/agent.py), driven by the game client:
POST /agent/start   <- {"text", "context"?, "player"?}          -> {job}
GET  /agent/poll?job=<id>                                        -> {calls[{cid, tool, args}], done, reply, error, count}
POST /agent/result  <- {"job", "cid", "ok", "output"}            -> {ok}
GET  /agent/tools   (MCP tool list)    POST /agent/tool <- {"job", "tool", "args"} -> {ok, output}  (agent_mcp.py, blocking)

    python -m regie.service          (see start.ps1)
"""
from __future__ import annotations

import base64
import binascii
import json
import logging
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from .config import Settings, api_key_present
from .context import ShowContext
from .stt import Transcriber
from .understand import Understander
from .agent import AgentManager, mcp_tools

log = logging.getLogger("regie")
MAX_BODY = 8 * 1024 * 1024


class Regie:
    """Everything the handler needs; the transcriber and the Claude client can be replaced in tests."""

    def __init__(self, settings: Settings, transcriber=None, understander=None, agent=None):
        self.settings = settings
        self.transcriber = transcriber or Transcriber(settings.whisper_model, settings.whisper_device, settings.whisper_compute)
        if understander is None:
            if settings.understanding == "offline":
                from .offline import OfflineUnderstander
                understander = OfflineUnderstander()
            elif settings.understanding == "abonnement" or not api_key_present():
                # No API key: Claude Code with the Claude subscription (regie.subscription)
                from .subscription import SubscriptionUnderstander
                understander = SubscriptionUnderstander(settings)
            else:
                understander = Understander(settings)
        self.understander = understander
        self.agent = agent if agent is not None else AgentManager(settings)
        self.ready = threading.Event()
        self.load_error: str | None = None

    def load_async(self) -> None:
        def run():
            try:
                self.transcriber.load()
            except Exception as e:
                self.load_error = f"{type(e).__name__}: {e}"
                log.exception("Whisper failed to load")
            finally:
                self.ready.set()
        threading.Thread(target=run, name="whisper-load", daemon=True).start()

    def health(self) -> dict:
        return {"ok": self.load_error is None, "whisper": getattr(self.transcriber, "description", "?"),
                "whisperReady": self.ready.is_set(), "whisperError": self.load_error,
                "apiKey": api_key_present(), "model": self.settings.model,
                "understanding": self.settings.understanding,
                "via": type(self.understander).__name__}

    def command(self, body: dict) -> dict:
        t0 = time.perf_counter()
        ctx = ShowContext(body.get("context"), idle_minutes=self.settings.idle_minutes)
        note = getattr(self.agent, "recent_note", None)
        ctx.agent_note = note() if note else ""
        timings = {}
        if isinstance(body.get("text"), str):
            transcript = body["text"].strip()
        else:
            try:
                wav = base64.b64decode(body.get("audio") or "", validate=True)
            except (binascii.Error, ValueError):
                return _error("bad_request", "audio n'est pas du base64 valide")
            if not wav:
                return _error("bad_request", "ni audio ni texte dans la requête")
            if not self.ready.wait(timeout=120) or self.load_error:
                return _error("stt_error", f"Whisper indisponible : {self.load_error or 'chargement trop long'}")
            try:
                tr = self.transcriber.transcribe(wav, prompt=ctx.whisper_prompt(), hotwords=ctx.hotwords())
            except Exception as e:
                log.exception("transcription failed")
                return _error("stt_error", f"transcription impossible : {e}")
            transcript, timings["stt"] = tr.text, round(tr.seconds, 3)
        u = self.understander.understand(ctx, transcript)
        timings["claude"] = round(u.seconds, 3)
        timings["total"] = round(time.perf_counter() - t0, 3)
        actions, agent_job = u.actions, None
        # "Change the setup" goes to the agent, which runs now; the game polls it (agentJob)
        asks = [a for a in actions if a.get("type") == "agent.request"]
        if asks and body.get("agent") is False:
            # Turned off in the game (Simple Voice Chat's menu): say so, start nothing
            actions = [a for a in actions if a.get("type") != "agent.request"]
            u.reply = "L'agent de régie est coupé (bouton dans le menu de Voice Chat)."
            asks = []
        if asks:
            actions = [a for a in actions if a.get("type") != "agent.request"]
            request = transcript if len(asks) == 1 else " ; ".join(a["demande"] for a in asks)
            ctx_text = ""
            try:
                ctx_text = ctx.summary()
            except Exception:
                pass
            job = self.agent.start(request + (f" (compris : {asks[0]['demande']})" if len(asks) == 1 and asks[0]["demande"] != transcript else ""),
                                   ctx_text, str((body.get("context") or {}).get("me") or ""))
            agent_job = job.id
        out = {"transcript": transcript, "actions": actions, "reply": u.reply, "warnings": u.warnings,
               "error": u.error, "timings": timings, "agentJob": agent_job}
        log.info("%r -> %s | %s | %s %s", transcript, json.dumps(u.actions, ensure_ascii=False), u.reply, timings,
                 u.usage or "")
        return out


def _error(code: str, message: str) -> dict:
    return {"transcript": "", "actions": [], "reply": message, "warnings": [], "error": code, "timings": {}}


def make_handler(regie: Regie):
    class Handler(BaseHTTPRequestHandler):
        server_version = "CraftMasterRegie/1"

        def log_message(self, fmt, *args):  # quiet: our own log line per command is enough
            log.debug(fmt, *args)

        def _send(self, status: int, payload: dict) -> None:
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            path, _, query = self.path.partition("?")
            if path == "/health":
                self._send(200, regie.health())
            elif path == "/agent/tools":
                self._send(200, {"tools": mcp_tools()})
            elif path == "/agent/poll":
                from urllib.parse import parse_qs
                job = regie.agent.get((parse_qs(query).get("job") or [""])[0])
                if job is None:
                    self._send(404, {"error": "unknown_job"})
                    return
                calls = job.take()
                self._send(200, {"calls": calls, "done": job.done and not calls, "reply": job.reply if job.done else "",
                                 "error": job.error, "count": job.count})
            else:
                self._send(404, {"error": "not_found"})

        def do_POST(self):
            if self.path not in ("/command", "/agent/start", "/agent/result", "/agent/tool"):
                self._send(404, {"error": "not_found"})
                return
            length = int(self.headers.get("Content-Length") or 0)
            if length <= 0 or length > MAX_BODY:
                self._send(400, _error("bad_request", "corps vide ou trop gros"))
                return
            try:
                body = json.loads(self.rfile.read(length).decode("utf-8"))
                if not isinstance(body, dict):
                    raise ValueError("not an object")
            except (ValueError, UnicodeDecodeError):
                self._send(400, _error("bad_request", "JSON invalide"))
                return
            try:
                if self.path == "/agent/start":
                    text = str(body.get("text") or "").strip()
                    if not text:
                        self._send(400, {"error": "bad_request"})
                        return
                    ctx = body.get("context")
                    summary = ""
                    if isinstance(ctx, dict) and ctx:
                        try:
                            summary = ShowContext(ctx, idle_minutes=regie.settings.idle_minutes).summary()
                        except Exception:
                            summary = ""
                    job = regie.agent.start(text, summary, str(body.get("player") or ""))
                    self._send(200, {"job": job.id})
                elif self.path == "/agent/result":
                    job = regie.agent.get(str(body.get("job") or ""))
                    ok = job is not None and job.answer(int(body.get("cid") or 0), bool(body.get("ok")), str(body.get("output") or ""))
                    self._send(200, {"ok": ok})
                elif self.path == "/agent/tool":
                    job = regie.agent.get(str(body.get("job") or ""))
                    if job is None:
                        self._send(200, {"ok": False, "output": "Demande inconnue ou terminée."})
                        return
                    ok, out = job.call(str(body.get("tool") or ""), body.get("args") or {})
                    self._send(200, {"ok": ok, "output": out})
                else:
                    self._send(200, regie.command(body))
            except Exception as e:  # never kill the server thread on a bad request
                log.exception("command failed")
                self._send(500, _error("internal", f"erreur interne : {type(e).__name__}"))

    return Handler


def main() -> None:
    settings = Settings.from_env()
    os.makedirs(settings.log_dir, exist_ok=True)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s",
                        handlers=[logging.StreamHandler(),
                                  logging.FileHandler(os.path.join(settings.log_dir, "regie.log"), encoding="utf-8")])
    if settings.host not in ("127.0.0.1", "localhost", "::1"):
        log.warning("Listening on %s: the service has no authentication, keep it on the local machine", settings.host)
    regie = Regie(settings)
    if settings.understanding == "offline":
        log.warning("OFFLINE understanding (REGIE_UNDERSTANDING=offline): fixed patterns only, no Claude")
    elif settings.understanding == "abonnement" or not api_key_present():
        log.info("Understanding through Claude Code and the Claude subscription (no API key)")
    regie.load_async()
    server = ThreadingHTTPServer((settings.host, settings.port), make_handler(regie))
    log.info("Régie vocale on http://%s:%d (Claude model %s, effort %s)", settings.host, settings.port,
             settings.model, settings.effort)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
