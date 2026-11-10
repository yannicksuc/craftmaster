#!/usr/bin/env python3
"""Generates the PROVISIONAL CraftMaster theme (opening / closing credits): an original little fanfare synthesised
from scratch (brass-like lead, string pad, bass, timpani, cymbal), about 32 s, royalty-free since nothing is sampled.

    python scripts/gen_theme.py            (Windows Python: needs numpy, and ffmpeg in the PATH)

Writes src/main/resources/assets/craftmaster/sounds/show/theme.ogg (Ogg Vorbis, mono, 44.1 kHz). LordFinn can
replace it with his own music in a resource pack: assets/craftmaster/sounds/show/theme.ogg (docs/wiki/Realisation.md).
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

RATE = 44100
BPM = 112
BEAT = 60 / BPM
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "craftmaster", "sounds", "show", "theme.ogg")

NOTES = {"C": -9, "C#": -8, "D": -7, "D#": -6, "E": -5, "F": -4, "F#": -3, "G": -2, "G#": -1, "A": 0, "A#": 1, "B": 2}


def freq(name):
    """'A4' → 440 Hz, 'F#5'…"""
    n, octave = name[:-1], int(name[-1])
    return 440.0 * 2 ** ((NOTES[n] + 12 * (octave - 4)) / 12)


def env(n, attack, release, sustain=1.0):
    t = np.arange(n) / RATE
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    r = np.clip((n / RATE - t) / max(release, 1e-4), 0, 1)
    return a * r * sustain


def brass(f, dur, vel=1.0):
    n = int(dur * RATE)
    t = np.arange(n) / RATE
    # Harmonics brighten as the note opens (a brass "blat"), slight vibrato after the attack
    vib = 1 + 0.004 * np.sin(2 * np.pi * 5.2 * t) * np.clip(t / 0.25, 0, 1)
    bright = np.clip(t / 0.08, 0.3, 1)
    sig = np.zeros(n)
    for h in range(1, 9):
        sig += (bright ** (h / 3)) / h ** 1.1 * np.sin(2 * np.pi * f * h * vib * t)
    return 0.22 * vel * sig * env(n, 0.03, 0.12)


def pad(freqs, dur, vel=1.0):
    n = int(dur * RATE)
    t = np.arange(n) / RATE
    sig = np.zeros(n)
    for f in freqs:
        for detune in (-0.003, 0, 0.003):
            ff = f * (1 + detune)
            # Soft saw (few harmonics): strings
            for h in range(1, 6):
                sig += np.sin(2 * np.pi * ff * h * t) / (h * h)
    return 0.035 * vel * sig * env(n, 0.5, 0.6)


def bass(f, dur, vel=1.0):
    n = int(dur * RATE)
    t = np.arange(n) / RATE
    sig = np.sin(2 * np.pi * f * t) + 0.3 * np.sin(2 * np.pi * 2 * f * t)
    return 0.22 * vel * sig * env(n, 0.01, 0.15) * np.exp(-t * 1.5)


def timpani(f, dur, vel=1.0):
    n = int(dur * RATE)
    t = np.arange(n) / RATE
    pitch = f * (1 + 0.15 * np.exp(-t * 30))
    sig = np.sin(2 * np.pi * np.cumsum(pitch) / RATE) + 0.4 * np.random.default_rng(3).normal(0, 1, n) * np.exp(-t * 40)
    return 0.4 * vel * sig * np.exp(-t * 3.5)


def cymbal(dur, vel=1.0):
    n = int(dur * RATE)
    t = np.arange(n) / RATE
    noise = np.random.default_rng(11).normal(0, 1, n)
    # Bright noise: difference of samples (high-pass)
    hp = np.concatenate([[0], np.diff(noise)])
    return 0.08 * vel * hp * np.exp(-t * 1.6)


def hat(vel=1.0):
    n = int(0.05 * RATE)
    noise = np.random.default_rng(int(vel * 1000)).normal(0, 1, n)
    hp = np.concatenate([[0], np.diff(noise)])
    return 0.03 * vel * hp * np.exp(-np.arange(n) / RATE * 60)


def main():
    bars = 16
    total = bars * 4 * BEAT + 3.0
    mix = np.zeros(int(total * RATE))

    def add(sig, at_beats):
        i = int(at_beats * BEAT * RATE)
        j = min(len(mix), i + len(sig))
        mix[i:j] += sig[: j - i]

    # Chords per bar (D major): I I IV V | vi IV ii V | I I IV V | IV V I I
    chords = [("D3", "F#3", "A3"), ("D3", "F#3", "A3"), ("G3", "B3", "D4"), ("A3", "C#4", "E4"),
              ("B2", "D3", "F#3"), ("G3", "B3", "D4"), ("E3", "G3", "B3"), ("A3", "C#4", "E4"),
              ("D3", "F#3", "A3"), ("D3", "F#3", "A3"), ("G3", "B3", "D4"), ("A3", "C#4", "E4"),
              ("G3", "B3", "D4"), ("A3", "C#4", "G4"), ("D3", "F#3", "A3"), ("D3", "F#3", "A3")]
    roots = ["D2", "D2", "G2", "A2", "B1", "G2", "E2", "A2", "D2", "D2", "G2", "A2", "G2", "A2", "D2", "D2"]

    # Intro: timpani roll swelling on D, then A
    for k in range(16):
        add(timpani(freq("D2") if k < 12 else freq("A2"), 0.4, 0.25 + k * 0.05), k * 0.25)

    for b in range(bars):
        start = b * 4
        add(pad([freq(n) for n in chords[b]], 4 * BEAT + 0.4, 1.0 if b >= 1 else 0.5), start)
        if b >= 1:
            for beat in (0, 2):
                add(bass(freq(roots[b]), 1.6 * BEAT), start + beat)
            for e in range(8):
                add(hat(0.6 if e % 2 else 1.0), start + e * 0.5)
            if b % 4 == 0:
                add(timpani(freq(roots[b]) * 2, 1.0, 0.8), start)

    # Lead: an original 8-bar theme (beats, note, length), played twice, the second time an octave-up ending
    phrase = [
        (0, "A4", 1), (1, "D5", 1.5), (2.5, "E5", 0.5), (3, "F#5", 1),
        (4, "E5", 0.5), (4.5, "D5", 0.5), (5, "B4", 1), (6, "A4", 2),
        (8, "B4", 1), (9, "D5", 1), (10, "G5", 1.5), (11.5, "F#5", 0.5),
        (12, "E5", 2), (14, "C#5", 1), (15, "A4", 1),
        (16, "B4", 1.5), (17.5, "D5", 0.5), (18, "F#5", 2),
        (20, "G5", 1), (21, "F#5", 1), (22, "E5", 1), (23, "D5", 1),
        (24, "E5", 1), (25, "G5", 1), (26, "F#5", 1), (27, "E5", 1),
        (28, "D5", 1), (29, "C#5", 1), (30, "E5", 2),
    ]
    for offset in (4, 36):
        for at, note, length in phrase:
            add(brass(freq(note), length * BEAT * 0.95, 1.0), offset + at)
            # A third below in the second pass: fuller
            if offset == 36 and length >= 1:
                add(brass(freq(note) * 2 ** (-4 / 12), length * BEAT * 0.95, 0.45), offset + at)
    # Final cadence: big D chord, cymbal, timpani
    end = 64
    for n in ("D4", "F#4", "A4", "D5"):
        add(brass(freq(n), 2.6, 0.7), end)
    add(pad([freq("D3"), freq("A3"), freq("F#4")], 3.0, 1.4), end)
    add(bass(freq("D2"), 2.5, 1.2), end)
    add(timpani(freq("D2"), 2.0, 1.2), end)
    add(cymbal(3.0, 1.2), end)

    mix = np.tanh(mix * 1.4) / np.tanh(1.4)       # gentle limiter
    mix *= 0.89 / max(1e-6, np.max(np.abs(mix)))
    pcm = (mix * 32767).astype(np.int16)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "theme.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", OUT], check=True)
    print(f"{OUT} ({len(pcm) / RATE:.1f} s)")


if __name__ == "__main__":
    main()
