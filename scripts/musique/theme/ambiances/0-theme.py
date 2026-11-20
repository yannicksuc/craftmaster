# Le thème seul, version de référence (couleur fanfare v4 : ostinato de train, cuivres, flûte)
BPM = 112
exec(open(os.path.join(os.path.dirname(PART), "theme.py"), encoding="utf-8").read())
osti = [50, 45, 50, 51, 50, 45, 50, 48]
for bar in range(6):
    o = bar * 4
    for i, m in enumerate(osti):
        n("basse", "guitar", o + i * .5, .5, m, .34 if i % 4 == 0 else .22, -.2)
    for k in range(16):
        n("batterie", "hat", o + k / 4, .25, 0, .12 if k % 4 == 0 else .06, .25)
    n("batterie", "basedrum", o, 1, 0, .6)
    n("batterie", "snare", o + 3, .25, 0, .22)
    n("basse", "bass", o, 4, 38, .7)
theme("melodie", "bit", 8, 62, .5)
theme("melodie", "didgeridoo", 8, 38, .25)
theme("melodie", "flute", 16, 74, .6, .1)
theme("melodie", "bit", 16, 62, .35)
FIN = appel(24)
