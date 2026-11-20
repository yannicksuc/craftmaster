# Réflexion : le thème à la flûte, lent et en suspens, sur des arpèges de harpe
BPM = 92
exec(open(os.path.join(os.path.dirname(PART), "theme.py"), encoding="utf-8").read())
for bar in range(6):
    o = bar * 4
    n("basse", "bass", o, 4, 38, .5)
    n("basse", "didgeridoo", o, 4, 38, .1)
    for i, m in enumerate([62, 69, 62, 69, 63, 69, 62, 69]):
        n("accords", "harp", o + i * .5, .5, m, .2 if i % 4 == 0 else .13, -.3)
    n("batterie", "hat", o + 1, .25, 0, .06, .3)
    n("batterie", "hat", o + 3, .25, 0, .06, .3)
theme("melodie", "flute", 4, 74, .45, .15)
theme("melodie", "flute", 12, 78, .4, .15)  # repris plus haut, comme une question
for t, m in [(3, 93), (11.5, 90), (19, 97)]:
    n("cloche", "chime", t, 1, m, .15, .4)
FIN = appel(20, .8)
