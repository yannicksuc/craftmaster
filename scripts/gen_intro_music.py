#!/usr/bin/env python3
"""Generates the PROVISIONAL task intro music (the short piece under a round's intro, before the black and the
round's title): an original, slightly mischievous little piece synthesised from scratch (plucked strings, staccato
bassoon, glockenspiel, woodblock, timpani, a final sting), 120 BPM, 40 beats (20 s) plus the sting's ring. Royalty
free since nothing is sampled.

    python scripts/gen_intro_music.py      (Windows Python: needs numpy, and ffmpeg in the PATH)

Writes src/main/resources/assets/craftmaster/sounds/show/intro.ogg (sound event craftmaster:show.intro, "musique
intro" in /tv intro). Cuts on the music: /tv intro <manche> rythme intro (120 BPM, first beat at 0 s). LordFinn can
replace it with his own music in a resource pack (docs/wiki/Realisation.md).

Layout (beats): 0-8 pizzicato bass and woodblock alone, 8-32 the tune on the glockenspiel and bassoon, 32-40 the
build (timpani roll, rising bassoon), 40 the sting. An auto-built intro (/tv intro <manche> auto) is cut to fit.
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_theme import RATE, env, freq, timpani, cymbal, pad, bass  # noqa: E402

BPM = 120
BEAT = 60 / BPM
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "craftmaster", "sounds", "show", "intro.ogg")
TOTAL_BEATS = 40


def pluck(f, dur, vel=1.0, seed=1):
    """Plucked string (Karplus-Strong): pizzicato."""
    n = int(dur * RATE)
    period = max(2, int(RATE / f))
    buf = np.random.default_rng(seed).uniform(-1, 1, period)
    out = np.zeros(n)
    for i in range(n):
        out[i] = buf[i % period]
        buf[i % period] = 0.5 * (buf[i % period] + buf[(i + 1) % period]) * 0.996
    return 0.35 * vel * out * env(n, 0.002, 0.05)


def bassoon(f, dur, vel=1.0):
    """Reedy staccato: odd-ish harmonics, nasal, short."""
    n = int(dur * RATE)
    t = np.arange(n) / RATE
    sig = np.zeros(n)
    for h, a in ((1, 1.0), (2, 0.55), (3, 0.6), (4, 0.25), (5, 0.3), (6, 0.1), (7, 0.12)):
        sig += a * np.sin(2 * np.pi * f * h * t)
    return 0.12 * vel * sig * env(n, 0.015, 0.06)


def glock(f, dur, vel=1.0):
    """Glockenspiel: bright, inharmonic partials, long ring."""
    n = int(dur * RATE)
    t = np.arange(n) / RATE
    sig = np.sin(2 * np.pi * f * t) + 0.35 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t * 6) \
        + 0.15 * np.sin(2 * np.pi * f * 5.4 * t) * np.exp(-t * 12)
    return 0.16 * vel * sig * np.exp(-t * 2.2) * env(n, 0.001, 0.05)


def woodblock(vel=1.0, pitch=1.0):
    n = int(0.08 * RATE)
    t = np.arange(n) / RATE
    sig = np.sin(2 * np.pi * 900 * pitch * t) + 0.5 * np.sin(2 * np.pi * 1650 * pitch * t)
    return 0.09 * vel * sig * np.exp(-t * 70)


def main():
    total = TOTAL_BEATS * BEAT + 3.5
    mix = np.zeros(int(total * RATE))

    def add(sig, at_beats):
        i = int(at_beats * BEAT * RATE)
        j = min(len(mix), i + len(sig))
        mix[i:j] += sig[: j - i]

    # G minor, a sly walking pizzicato bass: one note per beat, per bar of 4 beats
    walk = [["G2", "D3", "G2", "A#2"], ["C3", "G2", "C3", "D3"], ["D#3", "A#2", "D#3", "D3"], ["D3", "A2", "F#2", "D2"]]
    for b in range(TOTAL_BEATS // 4):
        bar = walk[b % 4]
        for k, note in enumerate(bar):
            add(pluck(freq(note), BEAT * 0.9, 0.9 if k % 2 == 0 else 0.7, seed=b * 4 + k + 1), b * 4 + k)
    # Woodblock on the off-beats, a tick-tock that never stops
    for e in range(TOTAL_BEATS * 2):
        if e % 2:
            add(woodblock(0.8, 1.0 if (e // 2) % 2 else 1.25), e * 0.5)

    # The tune (beats from 8): glockenspiel lead doubled by the bassoon an octave and a half lower
    tune = [
        (0, "D5", 0.5), (0.5, "G5", 0.5), (1, "A#5", 1), (2, "A5", 0.5), (2.5, "G5", 0.5), (3, "D5", 1),
        (4, "D#5", 0.5), (4.5, "G5", 0.5), (5, "C6", 1), (6, "A#5", 0.5), (6.5, "A5", 0.5), (7, "G5", 1),
        (8, "A#5", 0.5), (8.5, "A5", 0.5), (9, "G5", 0.5), (9.5, "F#5", 0.5), (10, "G5", 1), (11, "D5", 1),
        (12, "D#5", 1), (13, "D5", 0.5), (13.5, "C5", 0.5), (14, "A4", 1), (15, "F#4", 1),
    ]
    for rep in (8, 24):
        for at, note, length in tune:
            add(glock(freq(note), 1.6, 1.0 if rep == 8 else 1.1), rep + at)
            add(bassoon(freq(note) / 4, length * BEAT * 0.55, 0.9), rep + at)
        add(pad([freq("G3"), freq("A#3"), freq("D4")], 8 * BEAT, 0.5), rep)
        add(pad([freq("D#3"), freq("G3"), freq("A#3")], 4 * BEAT, 0.5), rep + 8)
        add(pad([freq("D3"), freq("F#3"), freq("A3")], 4 * BEAT, 0.6), rep + 12)

    # Build: timpani roll swelling on D, the bassoon climbing chromatically
    for k in range(32):
        add(timpani(freq("D2"), 0.3, 0.15 + k * 0.03), 32 + k * 0.25)
    for k, note in enumerate(["D3", "D#3", "E3", "F3", "F#3", "G3", "G#3", "A3"]):
        add(bassoon(freq(note), BEAT * 0.8, 0.8 + k * 0.05), 32 + k)

    # The sting: a big G minor hit with a sharp glock top, cymbal, ring
    end = TOTAL_BEATS
    for n in ("G2", "D3", "G3", "A#3", "D4"):
        add(bassoon(freq(n), 0.35, 1.2), end)
    add(pad([freq("G3"), freq("A#3"), freq("D4"), freq("G4")], 3.0, 1.6), end)
    add(glock(freq("G6"), 3.0, 1.3), end)
    add(glock(freq("D6"), 3.0, 0.9), end)
    add(bass(freq("G1"), 2.5, 1.3), end)
    add(timpani(freq("G2"), 2.0, 1.3), end)
    add(cymbal(3.0, 1.1), end)

    mix = np.tanh(mix * 1.4) / np.tanh(1.4)
    mix *= 0.89 / max(1e-6, np.max(np.abs(mix)))
    pcm = (mix * 32767).astype(np.int16)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "intro.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", OUT], check=True)
    print(f"{OUT} ({len(pcm) / RATE:.1f} s)")


if __name__ == "__main__":
    main()
