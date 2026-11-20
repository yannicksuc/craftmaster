# Grec (façon sirtaki) : ça part lentement et accélère jusqu'à la fin, bouzouki en trémolo (banjo),
# oum-pa à deux temps, tambourin. BPM = 60 : les temps sont convertis en secondes par l'accélération.
BPM = 60
exec(open(os.path.join(os.path.dirname(PART), "theme.py"), encoding="utf-8").read())
import math
T0, T1, B = 72.0, 176.0, 28.0


def sec(b):
    if b <= B:
        return 60 * B / (T1 - T0) * math.log((T0 + (T1 - T0) * b / B) / T0)
    return sec(B) + (b - B) * 60 / T1


_n = n


def n(voice, inst, t, d, m, v=1.0, pan=0.0):
    s = sec(t)
    _n(voice, inst, s, max(.05, sec(t + d) - s), m, v, pan)


for bar in range(7):
    o = bar * 4
    for t, m in [(0, 38), (1, 45), (2, 38), (3, 45)]:
        n("basse", "bass", o + t, .5, m, .65)
        for x in (50, 54, 57):
            n("accords", "guitar", o + t + .5, .25, x, .10, -.25)
    for k in range(8):
        n("batterie", "hat", o + k * .5, .25, 0, .10 if k % 2 else .05, .3)
    n("batterie", "snare", o + 1.5, .25, 0, .10)
    n("batterie", "snare", o + 3.5, .25, 0, .10)


def tremolo(inst, o, root, vol, pan):
    for t, d, iv in THEME:
        k = 0.0
        while k < d - 1e-6:
            n("melodie", inst, o + t + k, .25, root + iv, vol * (1 if k == 0 else .7), pan)
            k += .25


tremolo("banjo", 4, 62, .40, .2)
tremolo("banjo", 12, 69, .40, .2)
tremolo("banjo", 20, 62, .42, .2)
theme("melodie", "flute", 20, 74, .32, .1)
FIN = sec(appel(28, .95, lead="flute", cuivre="banjo", corde="guitar", cloche="bell"))
