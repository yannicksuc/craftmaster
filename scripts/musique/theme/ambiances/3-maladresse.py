# Maladresse : le thème au xylophone, puis la reprise trébuche (finit trop bas, wah-wah)
BPM = 108
exec(open(os.path.join(os.path.dirname(PART), "theme.py"), encoding="utf-8").read())
for bar in range(7):
    o = bar * 4
    for t, m in [(0, 38), (1, 45), (2, 38), (3, 44)]:
        n("basse", "bass", o + t, .5, m, .7)
    n("batterie", "snare", o + 1, .25, 0, .18)
    n("batterie", "snare", o + 3, .25, 0, .18)
    n("batterie", "basedrum", o, 1, 0, .45)
    for t in (.5, 1.5, 2.5, 3.5):
        n("accords", "banjo", o + t, .25, 62, .15, -.25)
        n("accords", "banjo", o + t, .25, 66, .11, -.25)
theme("melodie", "xylophone", 4, 86, .4, .2, hold=False)
theme("melodie", "cow_bell", 12, 74, .35, .1, hold=False)
for t, d, m in [(20, .5, 86), (20.5, .5, 87), (21, 1, 86), (22, .5, 93), (22.5, .5, 94), (23, 1, 93),
                (24, .5, 91), (24.5, .5, 90), (25, .5, 87), (25.5, .5, 86)]:
    n("melodie", "xylophone", t, d, m, .4, .2)
for t, m in [(26, 45), (26.75, 44), (27.5, 43)]:
    n("melodie", "didgeridoo", t, .75, m, .55)
    n("melodie", "bit", t, .75, m + 12, .25)
FIN = appel(28, .9)
