"""Fanfare d'ouverture CraftMaster (originale) : partition + rendu en sons de blocs musicaux Minecraft.

Usage : python fanfare.py <dossier_sortie>
Écrit fanfare.mid, fanfare.wav (et un récapitulatif des voix) dans le dossier de sortie.
"""
import json, os, struct, subprocess, sys
import numpy as np

SR = 44100
BPM = 112
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

# Version 3 : mystique, tension latente, « Orient Express ». Mode hijaz sur ré (ré mi♭ fa♯ sol la si♭ do),
# ostinato grave, rythme de train, flûte ornée, cuivres en tresillo. Pas de harpe ni de cloche (le côté comptine).
# Bourdon et ostinato
for b in range(28):
    n("basse", "didgeridoo", b, 1, 38, 0.20 + (0.10 if b >= 16 else 0))
osti = [50, 45, 50, 51, 50, 45, 50, 48]  # ré la ré mi♭ / ré la ré do (guitare, croches)
for bar in range(7):
    for i, m in enumerate(osti):
        n("basse", "guitar", bar * 4 + i * .5, .5, m, 0.42 if i % 4 == 0 else 0.28, -0.2)
    n("basse", "bass", bar * 4, 2, 38, 0.8)
    n("basse", "bass", bar * 4 + 2, 2, 45 if bar % 2 == 0 else 39, 0.6)  # la, puis mi♭ grave : la tension

# Rythme de train : charleston en doubles croches accentuées, caisse claire feutrée
for bar in range(7):
    o = bar * 4
    for k in range(16):
        acc = 0.16 if k % 4 == 0 else (0.10 if k % 2 == 0 else 0.06)
        n("batterie", "hat", o + k / 4, .25, 0, acc + (0.04 if bar >= 4 else 0), 0.25)
    n("batterie", "snare", o + 1, .25, 0, 0.22); n("batterie", "snare", o + 3, .25, 0, 0.22)
    n("batterie", "basedrum", o, 1, 0, 0.75)
    if bar >= 4:
        n("batterie", "basedrum", o + 2.5, 1, 0, 0.45)
# roulement de timbales qui monte avant la fin
for k in range(24):
    n("batterie", "basedrum", 24 + k / 6, 1 / 6, 0, 0.18 + 0.03 * k)

# Flûte (mélodie), doublée une octave plus bas par la guitare, très discrète
mel = [
    (8, 1.5, 81), (9.5, .25, 82), (9.75, .25, 81), (10, .5, 79), (10.5, .5, 78), (11, 1, 75),
    (12, .75, 74), (12.75, .25, 75), (13, 1, 78), (14, .5, 79), (14.5, .5, 78), (15, .5, 75), (15.5, .5, 74),
    (16, 1, 86), (17, .5, 84), (17.5, .5, 82), (18, 1, 81), (19, .5, 79), (19.5, .5, 81),
    (20, 1.5, 82), (21.5, .25, 81), (21.75, .25, 79), (22, 1, 78), (23, 1, 75),
    (24, .25, 74), (24.25, .25, 75), (24.5, .5, 74), (25, .5, 72), (25.5, .5, 74), (26, 1, 75), (27, 1, 74),
]
for t, d, m in mel:
    n("melodie", "flute", t, d, m, 0.75, 0.1)
    if m - 24 >= 42:
        n("melodie", "guitar", t, d, m - 24, 0.14, -0.1)
    if d >= 1:  # tenue : vibrato de souffle en notes répétées
        k = 0.25
        while k < d - 0.01:
            n("melodie", "flute", t + k, .25, m, 0.26 * (1 - k / d) + 0.10, 0.1)
            k += 0.25
    if d >= .75 and t >= 16:  # ornement : mordant avant les notes longues
        n("melodie", "flute", t - 0.125, .125, m + 1 if m + 1 <= 90 else m - 1, 0.35, 0.1)

# Cuivres graves en tresillo (3+3+2) à partir de la mesure 5
STAB = {16: [62, 66, 69], 20: [67, 70, 74], 23: [63, 67, 70]}
for t0, ch in [(16, [62, 66, 69]), (20, [67, 70, 74])]:
    for t in (t0, t0 + 1.5, t0 + 3):
        for m in ch:
            n("accords", "bit", t, .5, m, 0.30, -0.15)
        n("accords", "didgeridoo", t, .5, ch[0] - 24, 0.25)
for t in (24, 25.5):
    for m in (63, 67, 70):  # mi♭ majeur : la couleur « orientale » (napolitain)
        n("accords", "bit", t, .5, m, 0.34, -0.15)
for t in (27, 27 + 1/3, 27 + 2/3):
    for m in (62, 66, 69):
        n("accords", "bit", t, 1/3, m, 0.30 + (t - 27) * 0.15, -0.15)

# Fin : accord de ré (majeur, couleur hijaz), grave et large, puis le mi♭→ré en appoggiature
END = 28
for m in (62, 66, 69, 74):
    n("accords", "bit", END, 2, m, 0.5, -0.15)
n("basse", "bass", END, 2, 38, 1.0); n("basse", "bass", END, 2, 50, 0.6)
n("basse", "didgeridoo", END, 2, 38, 0.45); n("basse", "guitar", END, 2, 50, 0.5, -0.2)
n("melodie", "flute", END, 2, 86, 0.7, 0.1)
n("batterie", "basedrum", END, 1, 0, 1.0); n("batterie", "snare", END, 1, 0, 0.35)
k = 0.25
while k < 2.5:
    f = 1 - k / 2.5
    n("melodie", "flute", END + k, .25, 86, 0.30 * f, 0.1)
    n("basse", "didgeridoo", END + k, .25, 38, 0.25 * f)
    k += 0.25
FIN = END + 2.5
n("basse", "guitar", FIN, .25, 51, 0.45, -0.2)        # mi♭…
n("basse", "guitar", FIN + .25, 2, 50, 0.6, -0.2)     # …ré, qui reste en suspens
n("basse", "bass", FIN + .25, 2, 38, 0.8)
n("basse", "didgeridoo", FIN + .25, 2, 38, 0.35)
FIN = FIN + .25

# --- Version 4 : retouches de finition sur la v3 (tout est gardé) ---
# 1. Signature : appel grave des cuivres « ré – mi♭ – ré » en ouverture, repris juste avant la fin
for base_t, vol in [(0, 0.34), (2, 0.28)]:
    for t, d, m in [(0, .5, 62), (.5, .5, 63), (1, 1, 62)]:
        n("accords", "bit", base_t + t, d, m, vol, -0.15)
        n("accords", "didgeridoo", base_t + t, d, m - 24, vol * 0.8)
for t, d, m in [(26, .5, 62), (26.5, .5, 63)]:
    n("accords", "bit", t, d, m, 0.40, -0.15)
# 2. Corps de la mélodie dans la seconde moitié : la flûte doublée une octave plus bas par les cuivres, doucement
for t, d, m in mel:
    if t >= 16 and 54 <= m - 12 <= 78:
        n("melodie", "bit", t, d, m - 12, 0.16, -0.05)
# 3. Grandeur : basse doublée à l'octave sur les mesures 5 à 7
for bar in range(4, 7):
    n("basse", "bass", bar * 4, 2, 50, 0.35)
# 4. Coup de timbale final, plus profond
n("batterie", "basedrum", FIN, 1, 0, 0.9)
# 5. Nuances : crescendo progressif (75 % au début → 100 % à la mesure 5), sans toucher la fin
for i, (v, inst, t, d, m, vol, pan) in enumerate(notes):
    if t < 16:
        notes[i] = (v, inst, t, d, m, vol * (0.75 + 0.25 * t / 16), pan)

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
