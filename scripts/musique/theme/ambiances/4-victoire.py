# Victoire : le thème en majeur, cuivres et flûte, batterie de marche
BPM = 126
exec(open(os.path.join(os.path.dirname(PART), "theme.py"), encoding="utf-8").read())
CH = {38: (62, 66, 69), 43: (62, 67, 71), 45: (61, 64, 69)}
for bar in range(8):
    o = bar * 4
    root = [38, 38, 43, 45, 38, 38, 43, 45][bar]
    n("basse", "bass", o, 2, root, .85)
    n("basse", "bass", o + 2, 2, root + 7 if root + 7 <= 54 else root - 5, .7)
    n("batterie", "basedrum", o, 1, 0, .8)
    n("batterie", "basedrum", o + 2, 1, 0, .6)
    n("batterie", "snare", o + 1, .25, 0, .32)
    n("batterie", "snare", o + 3, .25, 0, .32)
    for k in range(8):
        n("batterie", "hat", o + k * .5, .25, 0, .08, .3)
    for t in (.5, 1.5, 2.5, 3.5):
        for m in CH[root]:
            n("accords", "harp", o + t, .5, m, .15, -.3)
for o in (0, 8, 16, 24):
    theme("melodie", "bit", o, 62, .55, 0, MAJEUR)
    theme("melodie", "flute", o, 74, .25 if o < 16 else .4, .1, MAJEUR)
for t in (0, 16):
    n("cloche", "bell", t, 2, 86, .22, .35)
FIN = appel(32)
