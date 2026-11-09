import base64
import io
import json
import threading
import urllib.error
import urllib.request
import wave

import numpy as np
import pytest
from conftest import FakeClient, make_context, tool_use

from regie.config import Settings
from regie.service import Regie, make_handler
from regie.stt import Transcript, wav_to_float32
from regie.understand import Understander
from http.server import ThreadingHTTPServer


class FakeTranscriber:
    description = "fake"

    def __init__(self, text):
        self.text = text
        self.prompts = []

    def load(self):
        pass

    def transcribe(self, wav, prompt="", hotwords=""):
        self.prompts.append((prompt, hotwords))
        return Transcript(self.text, 0.01, 1.0)


def wav_bytes(seconds=0.5, rate=16000, channels=1):
    out = io.BytesIO()
    with wave.open(out, "wb") as w:
        w.setnchannels(channels)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(np.zeros(int(seconds * rate) * channels, dtype="<i2").tobytes())
    return out.getvalue()


@pytest.fixture
def service():
    transcriber = FakeTranscriber("trois points pour Juste pour le pont")
    client = FakeClient(([tool_use("score_add", manche="pont", player="JustEdible", points=3)], "tool_use"))
    regie = Regie(Settings(), transcriber=transcriber, understander=Understander(Settings(), client=client))
    regie.load_async()
    server = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(regie))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    yield f"http://127.0.0.1:{server.server_address[1]}", transcriber, client
    server.shutdown()
    server.server_close()


def post(url, payload, raw=None):
    req = urllib.request.Request(url + "/command", data=raw if raw is not None else json.dumps(payload).encode(),
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())


def test_health(service):
    url, _, _ = service
    with urllib.request.urlopen(url + "/health", timeout=5) as r:
        h = json.loads(r.read())
    assert h["whisper"] == "fake" and "apiKey" in h and h["model"] == "claude-opus-5-5"


def test_command_with_audio(service):
    url, transcriber, client = service
    status, out = post(url, {"audio": base64.b64encode(wav_bytes()).decode(), "context": make_context()})
    assert status == 200
    assert out["transcript"] == "trois points pour Juste pour le pont"
    assert out["actions"] == [{"type": "score.add", "manche": "pont", "player": "JustEdible", "points": 3}]
    assert out["error"] is None and "total" in out["timings"]
    prompt, hotwords = transcriber.prompts[0]
    assert "JustEdible" in prompt and "Cycy" in hotwords


def test_command_with_text(service):
    url, transcriber, _ = service
    status, out = post(url, {"text": "trois points pour Juste", "context": make_context()})
    assert status == 200 and out["transcript"] == "trois points pour Juste" and transcriber.prompts == []


def test_bad_requests(service):
    url, _, _ = service
    assert post(url, None, raw=b"not json")[0] == 400
    assert post(url, None, raw=b"[1, 2]")[0] == 400
    status, out = post(url, {"audio": "%%%", "context": {}})
    assert status == 200 and out["error"] == "bad_request"
    status, out = post(url, {"context": {}})
    assert out["error"] == "bad_request"


def test_wav_decoding_resamples_and_downmixes():
    x = wav_to_float32(wav_bytes(seconds=1.0, rate=48000, channels=2))
    assert x.dtype == np.float32 and abs(len(x) - 16000) <= 1
