"""Speech to text with faster-whisper (CTranslate2), French, vocabulary hints taken from the show context.

GPU: CTranslate2 needs cuBLAS 12 and cuDNN 9. Its Windows wheel ships cudnn64_9.dll; cuBLAS comes from the pip
wheel nvidia-cublas-cu12 (and cudart from nvidia-cuda-runtime-cu12), whose DLL folders are not on the search path
by default: they are added here before the model loads. Without a working GPU the CPU (int8) is used with a
smaller model so a short order still transcribes in about a second or two.
"""
from __future__ import annotations

import io
import logging
import os
import sys
import threading
import time
import wave
from dataclasses import dataclass

import numpy as np

log = logging.getLogger("regie.stt")

GPU_MODEL = "large-v3-turbo"   # best accuracy / latency trade-off on an RTX 3070 (float16)
CPU_MODEL = "small"


def add_cuda_dll_dirs() -> list[str]:
    """Makes the pip-installed NVIDIA DLLs (site-packages/nvidia/*/bin) loadable on Windows."""
    added = []
    if sys.platform != "win32":
        return added
    for base in sys.path:
        root = os.path.join(base, "nvidia")
        if not os.path.isdir(root):
            continue
        for lib in os.listdir(root):
            bin_dir = os.path.join(root, lib, "bin")
            if os.path.isdir(bin_dir):
                os.add_dll_directory(bin_dir)
                os.environ["PATH"] = bin_dir + os.pathsep + os.environ.get("PATH", "")
                added.append(bin_dir)
    return added


def gpu_status() -> dict:
    """What the GPU path looks like: device count and which CUDA libraries are missing."""
    add_cuda_dll_dirs()
    import ctypes
    import ctranslate2

    status = {"cuda_devices": ctranslate2.get_cuda_device_count(), "missing": []}
    if sys.platform == "win32":
        for dll in ("cublas64_12.dll", "cublasLt64_12.dll", "cudnn64_9.dll"):
            try:
                ctypes.WinDLL(dll)
            except OSError:
                # cudnn64_9.dll sits next to ctranslate2.dll
                path = os.path.join(os.path.dirname(ctranslate2.__file__), dll)
                try:
                    ctypes.WinDLL(path)
                except OSError:
                    status["missing"].append(dll)
    return status


def wav_to_float32(data: bytes) -> np.ndarray:
    """Decodes a PCM WAV (any rate / channel count, 16-bit) to 16 kHz mono float32 in [-1, 1]."""
    with wave.open(io.BytesIO(data)) as w:
        rate, ch, width = w.getframerate(), w.getnchannels(), w.getsampwidth()
        frames = w.readframes(w.getnframes())
    if width != 2:
        raise ValueError(f"WAV must be 16-bit PCM (got {width * 8}-bit)")
    x = np.frombuffer(frames, dtype="<i2").astype(np.float32) / 32768.0
    if ch > 1:
        x = x.reshape(-1, ch).mean(axis=1)
    if rate != 16000:
        n = int(len(x) * 16000 / rate)
        x = np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x).astype(np.float32)
    return x


@dataclass
class Transcript:
    text: str
    seconds: float
    audio_seconds: float


class Transcriber:
    def __init__(self, model: str = "auto", device: str = "auto", compute: str = "auto"):
        self.requested = (model, device, compute)
        self.model = None
        self.description = "not loaded"
        self._lock = threading.Lock()

    def load(self) -> None:
        from faster_whisper import WhisperModel

        model, device, compute = self.requested
        if device == "auto":
            st = gpu_status()
            device = "cuda" if st["cuda_devices"] > 0 and not st["missing"] else "cpu"
            if device == "cpu":
                log.warning("GPU unusable (devices=%s, missing=%s): using the CPU", st["cuda_devices"], st["missing"])
        else:
            add_cuda_dll_dirs()
        if model == "auto":
            model = GPU_MODEL if device == "cuda" else CPU_MODEL
        if compute == "auto":
            compute = "float16" if device == "cuda" else "int8"
        t = time.perf_counter()
        try:
            self.model = WhisperModel(model, device=device, compute_type=compute)
        except Exception as e:  # e.g. a missing cuDNN sub-library only shows up here
            if device != "cuda":
                raise
            log.warning("Whisper on the GPU failed (%s): falling back to the CPU", e)
            device, compute = "cpu", "int8"
            model = CPU_MODEL if self.requested[0] == "auto" else model
            self.model = WhisperModel(model, device=device, compute_type=compute)
        self.description = f"{model} on {device} ({compute})"
        # Warm-up: the first decode pays for CUDA kernels / allocations (segments are lazy: consume them)
        noise = (np.random.default_rng(0).standard_normal(32000) * 0.01).astype(np.float32)
        list(self.model.transcribe(noise, language="fr", beam_size=5, initial_prompt="Régie CraftMaster.")[0])
        log.info("Whisper ready: %s in %.1f s", self.description, time.perf_counter() - t)

    def transcribe(self, wav: bytes, prompt: str = "", hotwords: str = "") -> Transcript:
        if self.model is None:
            self.load()
        audio = wav_to_float32(wav)
        t = time.perf_counter()
        with self._lock:  # one GPU job at a time
            segments, _info = self.model.transcribe(
                audio,
                language="fr",
                task="transcribe",
                beam_size=5,
                initial_prompt=prompt or None,
                hotwords=hotwords or None,
                condition_on_previous_text=False,
                vad_filter=False,
                without_timestamps=True,
            )
            text = " ".join(s.text.strip() for s in segments).strip()
        return Transcript(text=text, seconds=time.perf_counter() - t, audio_seconds=len(audio) / 16000)
