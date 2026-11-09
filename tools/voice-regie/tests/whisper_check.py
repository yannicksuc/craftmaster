"""Whisper accuracy / latency check on the synthesized phrases (tests/audio, made by make_tts_wavs.py).

    python tests/whisper_check.py [model] [device] [compute]      (defaults: auto)

For every phrase: transcript without and with the context vocabulary (prompt + hotwords), and the time taken.
Not a pytest test: it needs the model files and, for real numbers, the GPU.
"""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from conftest import make_context  # noqa: E402
from regie.context import ShowContext, norm  # noqa: E402
from regie.stt import Transcriber  # noqa: E402

HERE = Path(__file__).resolve().parent
DIGITS = dict(zip("12345", ["un", "deux", "trois", "quatre", "cinq"]))


def same(a: str, b: str) -> bool:
    """Equal once case, accents, punctuation and digits-vs-words are ignored ("4 points" == "Quatre points")."""
    w = lambda s: norm("".join(DIGITS.get(c, c) for c in s))
    return w(a) == w(b)


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    args = sys.argv[1:] + ["auto"] * (3 - len(sys.argv[1:]))
    t = Transcriber(*args[:3])
    t0 = time.perf_counter()
    t.load()
    print(f"model: {t.description} (load + warm-up {time.perf_counter() - t0:.1f} s)")
    ctx = ShowContext(make_context())
    phrases = dict(line.split("|", 1) for line in (HERE / "tts_phrases.txt").read_text(encoding="utf-8").splitlines()
                   if line.strip() and not line.startswith("#"))
    exact, times = 0, []
    for name, expected in phrases.items():
        wav = (HERE / "audio" / f"{name}.wav").read_bytes()
        bare = t.transcribe(wav)
        hinted = t.transcribe(wav, prompt=ctx.whisper_prompt(), hotwords=ctx.hotwords())
        times.append(hinted.seconds)
        ok = same(hinted.text, expected)
        exact += ok
        print(f"{'OK ' if ok else '-- '}{name:18s} {hinted.audio_seconds:4.1f}s audio | "
              f"bare {bare.seconds:5.2f}s: {bare.text!r}\n{'':22s}hinted {hinted.seconds:5.2f}s: {hinted.text!r}")
    times.sort()
    print(f"\n{exact}/{len(phrases)} exact (ignoring case, punctuation, digits vs words) with the context vocabulary; "
          f"hinted latency median {times[len(times) // 2]:.2f}s, max {times[-1]:.2f}s")


if __name__ == "__main__":
    main()
