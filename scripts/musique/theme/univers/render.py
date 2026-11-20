"""Fanfare d'ouverture CraftMaster (originale) : partition + rendu en sons de blocs musicaux Minecraft.

Usage : python fanfare.py <dossier_sortie>
Écrit fanfare.mid, fanfare.wav (et un récapitulatif des voix) dans le dossier de sortie.
"""
import json, os, struct, subprocess, sys
import numpy as np

SR = 44100
BPM = 112
PART = sys.argv[2]
_pre = open(PART, encoding="utf-8").read()
import re as _re
_m = _re.search(r"^BPM\s*=\s*([0-9.]+)", _pre, _re.M)
if _m: BPM = float(_m.group(1))
BEAT = 60.0 / BPM
OBJ = os.path.join(os.environ["APPDATA"], "ModrinthApp", "meta", "assets", "objects")
IDX = os.path.join(os.environ["APPDATA"], "ModrinthApp", "meta", "assets", "indexes", "5.json")

# Instrument -> (fichier, note MIDI jouée à hauteur 1.0) ; plage jouable : base-12 .. base+12
INSTR = {
    "harp": ("harp", 66), "bit": ("bit", 66), "pling": ("pling", 66), "banjo": ("banjo", 66),
    "iron_xylophone": ("iron_xylophone", 66), "bass": ("bass", 42), "didgeridoo": ("didgeridoo", 42),
    "guitar": ("guitar", 54), "flute": ("flute", 78), "cow_bell": ("cow_bell", 78),
    "bell": ("bell", 90), "chime": ("icechime", 90), "xylophone": ("xylobone", 90),
    "basedrum": ("bd", None), "snare": ("snare", None), "hat": ("hat", None),
}

def load(name):
    h = json.load(open(IDX))["objects"][f"minecraft/sounds/note/{name}.ogg"]["hash"]
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", os.path.join(OBJ, h[:2], h), "-f", "f32le", "-ac", "1",
                          "-ar", str(SR), "-"], capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.float32).copy()

# ------------------------------------------------------------------ partition (temps en noires)
notes = []  # (voix, instrument, début, durée, midi, volume, pan)

def n(voice, inst, t, d, m, v=1.0, pan=0.0):
    notes.append((voice, inst, t, d, m, v, pan))

exec(compile(_pre, PART, "exec"))
BEAT = 60.0 / BPM
# ------------------------------------------------------------------ vérifications
for v, inst, t, d, m, vol, pan in notes:
    base = INSTR[inst][1]
    if base is not None and not (base - 12 <= m <= base + 12):
        raise SystemExit(f"hors plage : {inst} {m} (plage {base-12}..{base+12}) à {t}")

# ------------------------------------------------------------------ rendu
samples = {k: load(f) for k, (f, _) in INSTR.items()}
total = int((FIN * BEAT + 3.0) * SR)
out = np.zeros((total, 2), dtype=np.float64)
for v, inst, t, d, m, vol, pan in notes:
    s = samples[inst]
    base = INSTR[inst][1]
    pitch = 1.0 if base is None else 2 ** ((m - base) / 12)
    idx = np.arange(0, len(s) - 1, pitch)
    seg = np.interp(idx, np.arange(len(s)), s)
    start = int(t * BEAT * SR)
    seg = seg[: total - start]
    l, r = np.cos((pan + 1) * np.pi / 4), np.sin((pan + 1) * np.pi / 4)
    out[start:start + len(seg), 0] += seg * vol * l
    out[start:start + len(seg), 1] += seg * vol * r

# petite réverbération de salle (échos décroissants) pour lier les notes pincées
wet = np.zeros_like(out)
for delay, g in [(0.023, .22), (0.041, .18), (0.067, .14), (0.097, .11), (0.131, .08), (0.173, .06)]:
    k = int(delay * SR)
    wet[k:, 0] += out[:-k, 1] * g
    wet[k:, 1] += out[:-k, 0] * g
out = out + wet * 0.6
# coupe à la fin de la résonance + fondu
last = np.max(np.nonzero(np.max(np.abs(out), axis=1) > 1e-3)) + 1
out = out[: min(last, total)]
fade = int(0.25 * SR)
out[-fade:] *= np.linspace(1, 0, fade)[:, None]
out *= 0.89 / np.max(np.abs(out))  # crête -1 dBFS avant le réglage de sonie

dst = sys.argv[1]
NAME = os.path.splitext(os.path.basename(PART))[0]
os.makedirs(dst, exist_ok=True)
pcm = (np.clip(out, -1, 1) * 32767).astype("<i2")
with open(os.path.join(dst, NAME + ".wav"), "wb") as f:
    data = pcm.tobytes()
    f.write(b"RIFF" + struct.pack("<I", 36 + len(data)) + b"WAVEfmt " + struct.pack("<IHHIIHH", 16, 1, 2, SR, SR * 4, 4, 16)
            + b"data" + struct.pack("<I", len(data)) + data)

# ------------------------------------------------------------------ MIDI (une piste par voix)
GM = {"bit": 80, "pling": 81, "harp": 46, "bass": 32, "didgeridoo": 109, "bell": 14}
PPQ = 480
def vlq(x):
    b = [x & 0x7F]; x >>= 7
    while x: b.insert(0, (x & 0x7F) | 0x80); x >>= 7
    return bytes(b)
tracks = []
tempo = vlq(0) + b"\xff\x51\x03" + struct.pack(">I", int(60e6 / BPM))[1:] + vlq(0) + b"\xff\x2f\x00"
tracks.append(tempo)
voices = sorted({x[0] for x in notes})
for ch, voice in enumerate(voices):
    drum = voice == "batterie"
    c = 9 if drum else ch
    ev = []
    progs = sorted({x[1] for x in notes if x[0] == voice})
    for v, inst, t, d, m, vol, pan in notes:
        if v != voice: continue
        if drum: m = {"basedrum": 36, "snare": 38, "hat": 42}[inst]
        vel = max(1, min(127, int(vol * 110)))
        ev.append((int(t * PPQ), 1, 0x90 | c, m, vel)); ev.append((int((t + d) * PPQ) - 1, 0, 0x80 | c, m, 0))
    ev.sort()
    body = b"" if drum else vlq(0) + bytes([0xC0 | c, GM.get(progs[0], 0)])
    now = 0
    for tick, _, st, m, vel in ev:
        body += vlq(tick - now) + bytes([st, m, vel]); now = tick
    body += vlq(0) + b"\xff\x2f\x00"
    tracks.append(body)
with open(os.path.join(dst, NAME + ".mid"), "wb") as f:
    f.write(b"MThd" + struct.pack(">IHHH", 6, 1, len(tracks), PPQ))
    for tr in tracks: f.write(b"MTrk" + struct.pack(">I", len(tr)) + tr)

print(f"{len(notes)} notes, {len(out)/SR:.2f} s, voix : {', '.join(voices)}")
