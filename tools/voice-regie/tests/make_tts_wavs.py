"""Synthesizes the French test phrases of tts_phrases.txt into 16 kHz mono 16-bit WAVs (tests/audio/<name>.wav).

Windows only: uses the OneCore voices (Hortense, Julie, Paul) through WinRT
(pip install winrt-runtime winrt-Windows.Media.SpeechSynthesis winrt-Windows.Storage.Streams winrt-Windows.Foundation).

    python tests/make_tts_wavs.py [voice substring, default: first fr-FR voice]
"""
import asyncio
import io
import sys
import wave
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent


async def synth(text: str, voice_hint: str | None) -> bytes:
    from winrt.windows.media.speechsynthesis import SpeechSynthesizer
    from winrt.windows.storage.streams import DataReader

    s = SpeechSynthesizer()
    voices = [v for v in SpeechSynthesizer.all_voices if v.language.startswith("fr")]
    if voice_hint:
        voices = [v for v in voices if voice_hint.lower() in v.display_name.lower()] or voices
    if voices:
        s.voice = voices[0]
    stream = await s.synthesize_text_to_stream_async(text)
    reader = DataReader(stream.get_input_stream_at(0))
    await reader.load_async(stream.size)
    buf = bytearray(stream.size)
    reader.read_bytes(buf)
    return bytes(buf)


def to_16k_mono(wav_bytes: bytes) -> bytes:
    with wave.open(io.BytesIO(wav_bytes)) as w:
        rate, ch, width = w.getframerate(), w.getnchannels(), w.getsampwidth()
        pcm = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16 if width == 2 else np.uint8)
    x = pcm.astype(np.float32).reshape(-1, ch).mean(axis=1)
    if rate != 16000:
        n = int(len(x) * 16000 / rate)
        x = np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x)
    out = io.BytesIO()
    with wave.open(out, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        w.writeframes(np.clip(x, -32768, 32767).astype(np.int16).tobytes())
    return out.getvalue()


async def main():
    hint = sys.argv[1] if len(sys.argv) > 1 else None
    out = HERE / "audio"
    out.mkdir(exist_ok=True)
    for line in (HERE / "tts_phrases.txt").read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        name, text = line.split("|", 1)
        (out / f"{name}.wav").write_bytes(to_16k_mono(await synth(text, hint)))
        print(f"{name}.wav <- {text}")


if __name__ == "__main__":
    asyncio.run(main())
