"""The local HTTP service the mod talks to (127.0.0.1 only).

GET  /health   -> {ok, whisper, apiKey, model}
POST /command  <- {"audio": base64 WAV (16 kHz mono 16-bit), "context": {...}}   (or "text" instead of "audio")
               -> {transcript, actions[], reply, warnings[], error, timings{stt, claude, total}}

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

log = logging.getLogger("regie")
MAX_BODY = 8 * 1024 * 1024


class Regie:
    """Everything the handler needs; the transcriber and the Claude client can be replaced in tests."""

    def __init__(self, settings: Settings, transcriber=None, understander=None):
        self.settings = settings
        self.transcriber = transcriber or Transcriber(settings.whisper_model, settings.whisper_device, settings.whisper_compute)
        self.understander = understander or Understander(settings)
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
                "apiKey": api_key_present(), "model": self.settings.model}

    def command(self, body: dict) -> dict:
        t0 = time.perf_counter()
        ctx = ShowContext(body.get("context"), idle_minutes=self.settings.idle_minutes)
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
        out = {"transcript": transcript, "actions": u.actions, "reply": u.reply, "warnings": u.warnings,
               "error": u.error, "timings": timings}
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
            if self.path == "/health":
                self._send(200, regie.health())
            else:
                self._send(404, {"error": "not_found"})

        def do_POST(self):
            if self.path != "/command":
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
    if not api_key_present():
        log.warning("ANTHROPIC_API_KEY is not set: transcription works, understanding will answer an error")
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
