# Steampunk (fête foraine victorienne) : valse à 3 temps, boîte à musique (carillon), orgue de manège
# (flûte + onde carrée à l'unisson), horloge et cliquetis d'engrenages.
BPM = 168
exec(open(os.path.join(os.path.dirname(PART), "theme.py"), encoding="utf-8").read())
for bar in range(11):
    o = bar * 3
    root = 38 if bar % 2 == 0 else 45
    n("basse", "bass", o, 1, root, .75)
    for t in (1, 2):
        for m in ((62, 66, 69) if bar % 2 == 0 else (61, 64, 69)):
            n("accords", "iron_xylophone", o + t, .5, m, .09, -.25)
    for k in range(3):
        n("batterie", "hat", o + k, .25, 0, .12 if k == 0 else .07, .3)
    if bar % 4 == 3:
        for k in range(6):
            n("batterie", "snare", o + 2 + k / 6, 1 / 6, 0, .05 + .01 * k)
    for k, m in enumerate([86, 90, 93, 98, 93, 90]):   # boîte à musique
        n("boite", "chime", o + k * .5, .5, m, .12 if bar < 2 else .06, .4)
VALSE = [(0, 1, 0), (1, .5, 1), (1.5, 1.5, 0), (3, 1, 7), (4, .5, 8), (4.5, 1.5, 7),
         (6, 1, 5), (7, 1, 4), (8, 1, 1), (9, 3, 0)]


def orgue(o, root, vol):
    for t, d, iv in VALSE:
        n("orgue", "flute", o + t, d, root + 12 + iv, vol, .1)
        n("orgue", "bit", o + t, d, root + iv, vol * .6, -.1)


orgue(6, 62, .42)
for t, d, iv in VALSE:
    n("boite", "chime", 18 + t, d, 86 + iv, .30, .4)
orgue(18, 62, .20)
for t, m in [(30, 45), (30.75, 44), (31.5, 43)]:
    n("wah", "didgeridoo", t, .75, m, .5)
    n("wah", "bit", t, .75, m + 12, .22)
FIN = appel(33, .9, lead="flute", cuivre="bit", corde="iron_xylophone", cloche="chime")
