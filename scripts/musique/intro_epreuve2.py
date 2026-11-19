"""Fanfare d'ouverture CraftMaster (originale) : partition + rendu en sons de blocs musicaux Minecraft.

Usage : python fanfare.py <dossier_sortie>
Écrit fanfare.mid, fanfare.wav (et un récapitulatif des voix) dans le dossier de sortie.
"""
import json, os, struct, subprocess, sys
import numpy as np

SR = 44100
BPM = 120
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

# Intro d'épreuve v2 : une seule idée qui monte, sans éléments au hasard.
# Pulsation régulière dès le début (tic-tac + ostinato), le motif ré–mi♭–ré énoncé 3 fois en montant
# (ré, puis fa, puis la : séquence), chaque énoncé un peu plus fort et plus fourni, coup sec, accord final.
osti = [50, 45, 50, 51, 50, 45, 50, 51]  # ré la ré mi♭, en boucle (guitare, croches)
for bar in range(4):
    o = bar * 4
    for i, m in enumerate(osti):
        n("basse", "guitar", o + i * .5, .5, m, (0.30 if i % 4 == 0 else 0.20) + 0.04 * bar, -0.2)
    for k in range(8):  # tic-tac en croches, qui se densifie en doubles croches à la mesure 4
        n("batterie", "hat", o + k * .5, .25, 0, 0.14 if k % 2 == 0 else 0.08, 0.3)
        if bar == 3:
            n("batterie", "hat", o + k * .5 + .25, .25, 0, 0.07, 0.3)
    n("basse", "bass", o, 4, 38, 0.6 + 0.1 * bar)
    n("basse", "didgeridoo", o, 4, 38, 0.18 + 0.05 * bar)
    if bar >= 1:
        n("batterie", "basedrum", o, 1, 0, 0.55 + 0.1 * bar)
        n("batterie", "snare", o + 3, .25, 0, 0.18 + 0.05 * bar)

# Le motif, 3 fois en montant : mesure 2 (ré), mesure 3 (fa), mesure 4 (la)
for bar, root, vol in [(1, 62, .34), (2, 65, .40), (3, 69, .46)]:
    o = bar * 4
    for t, d, iv in [(0, .5, 0), (.5, .5, 1), (1, 1.5, 0)]:
        m = root + iv
        n("accords", "bit", o + t, d, m, vol, -0.15)
        n("accords", "didgeridoo", o + t, d, m - 24 if m - 24 >= 30 else m - 12, vol * 0.6)
        if bar >= 2:
            n("melodie", "flute", o + t, d, m + 12, vol * 0.8, 0.1)  # la flûte rejoint, à l'octave
    # réponse de la flûte sur la 2e moitié de la mesure, toujours la même (ré–do–ré), plus haut à chaque fois
    for t, d, iv in [(2.5, .5, 0), (3, .5, -2), (3.5, .5, 0)]:
        m = root + 12 + iv
        if 66 <= m <= 90:
            n("melodie", "flute", o + t, d, m, vol * 0.55, 0.1)

# Montée : roulement de timbales sur la mesure 4
for k in range(12):
    n("batterie", "basedrum", 14 + k / 6, 1 / 6, 0, 0.25 + 0.05 * k)
# Le « regard du maître » : coup sec et sombre sur le dernier temps de la mesure 4… puis un demi-temps de silence
n("basse", "bass", 15.5, .5, 39, 1.0); n("basse", "didgeridoo", 15.5, .5, 39, 0.5)
n("batterie", "snare", 15.5, .25, 0, 0.6)

# L'accord final de ré (le lieu de la tâche), qui respire
END = 16
for m in (62, 66, 69, 74):
    n("accords", "bit", END, 2, m, 0.5, -0.15)
n("melodie", "flute", END, 2, 86, 0.6, 0.1)
n("basse", "bass", END, 2, 38, 1.0); n("basse", "bass", END, 2, 50, 0.6)
n("basse", "didgeridoo", END, 2, 38, 0.45); n("basse", "guitar", END, 2, 50, 0.45, -0.2)
n("batterie", "basedrum", END, 1, 0, 1.0)
k = 0.25
while k < 3.5:
    f = 1 - k / 3.5
    n("melodie", "flute", END + k, .25, 86, 0.24 * f, 0.1)
    n("accords", "bit", END + k, .25, 69, 0.13 * f, -0.15)
    n("basse", "didgeridoo", END + k, .25, 38, 0.22 * f)
    k += 0.25
FIN = END + 3.5

# ------------------------------------------------------------------ vérifications
for v, inst, t, d, m, vol, pan in notes:
    base = INSTR[inst][1]
    if base is not None and not (base - 12 <= m <= base + 12):
        raise SystemExit(f"hors plage : {inst} {m} (plage {base-12}..{base+12}) à {t}")

# ------------------------------------------------------------------ rendu
samples = {k: load(f) for k, (f, _) in INSTR.items()}
total = int((FIN * BEAT + 4.0) * SR)
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
for delay, g in [(0.029, .30), (0.053, .26), (0.083, .22), (0.121, .19), (0.167, .16), (0.223, .13), (0.29, .10), (0.37, .08), (0.46, .06), (0.57, .04)]:
    k = int(delay * SR)
    wet[k:, 0] += out[:-k, 1] * g
    wet[k:, 1] += out[:-k, 0] * g
out = out + wet * 0.8
# coupe à la fin de la résonance + fondu
last = np.max(np.nonzero(np.max(np.abs(out), axis=1) > 1e-3)) + 1
out = out[: min(last, total)]
fade = int(0.25 * SR)
out[-fade:] *= np.linspace(1, 0, fade)[:, None]
out *= 0.89 / np.max(np.abs(out))  # crête -1 dBFS avant le réglage de sonie

dst = sys.argv[1]
os.makedirs(dst, exist_ok=True)
pcm = (np.clip(out, -1, 1) * 32767).astype("<i2")
with open(os.path.join(dst, "fanfare.wav"), "wb") as f:
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
with open(os.path.join(dst, "fanfare.mid"), "wb") as f:
    f.write(b"MThd" + struct.pack(">IHHH", 6, 1, len(tracks), PPQ))
    for tr in tracks: f.write(b"MTrk" + struct.pack(">I", len(tr)) + tr)

print(f"{len(notes)} notes, {len(out)/SR:.2f} s, voix : {', '.join(voices)}")
