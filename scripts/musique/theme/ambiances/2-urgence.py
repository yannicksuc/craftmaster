# Urgence : le thème martelé deux fois plus vite, ostinato serré, qui monte
BPM = 144
exec(open(os.path.join(os.path.dirname(PART), "theme.py"), encoding="utf-8").read())
osti = [50, 50, 51, 50, 45, 50, 51, 48]
for bar in range(8):
    o = bar * 4
    for i, m in enumerate(osti):
        n("basse", "guitar", o + i * .5, .5, m, .38 if i % 2 == 0 else .28, -.2)
    for k in range(16):
        n("batterie", "hat", o + k * .25, .25, 0, .12 if k % 4 == 0 else .06, .3)
    n("batterie", "basedrum", o, 1, 0, .8)
    n("batterie", "basedrum", o + 2, 1, 0, .7)
    n("batterie", "snare", o + 1, .25, 0, .3)
    n("batterie", "snare", o + 3, .25, 0, .3)
    n("basse", "bass", o, 2, 38, .8)
    n("basse", "bass", o + 2, 2, 39 if bar % 2 else 38, .7)
for o, r, v in [(0, 62, .4), (8, 65, .45), (16, 69, .5)]:
    theme("melodie", "bit", o, r, v, -.1, stretch=.5, hold=False)
    theme("melodie", "bit", o + 4, r, v * .9, -.1, stretch=.5, hold=False)
for k in range(8):
    n("cloche", "iron_xylophone", 24 + k, .25, 74, .25 + .03 * k, .35)
FIN = appel(32)
