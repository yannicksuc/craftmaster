# Suspense : la tête du thème (ré mi♭ ré) qui monte d'un demi-ton à chaque fois, puis le thème entier
BPM = 84
exec(open(os.path.join(os.path.dirname(PART), "theme.py"), encoding="utf-8").read())
for k in range(20):
    n("batterie", "basedrum", k, 1, 0, .35 + .015 * k)
    n("basse", "bass", k, 1, 38, .45 + .012 * k)
    n("basse", "didgeridoo", k, 1, 38, .14 + .006 * k)
for i, root in enumerate([62, 63, 64]):
    o = i * 4
    for t, d, iv in [(0, 1, 0), (1, 1, 1), (2, 2, 0)]:
        n("accords", "bit", o + t, d, root + iv, .2 + .03 * i, -.15)
        n("accords", "guitar", o + t, d, root + iv - 12, .16 + .02 * i, -.3)
theme("melodie", "flute", 12, 74, .45, .1)
theme("melodie", "bit", 12, 62, .3, -.1)
FIN = appel(20, 1.0)
