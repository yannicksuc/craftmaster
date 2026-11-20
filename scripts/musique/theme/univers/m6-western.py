# Maladresse western : banjo en tête, guitare en contretemps, sabots (cloche de vache), basse qui alterne
BPM = 120
exec(open(os.path.join(os.path.dirname(PART), "maladresse_monde.py"), encoding="utf-8").read())

def groove(o, bar):
    for t, m in [(0, 38), (1, 45), (2, 38), (3, 45)]:
        n("basse", "bass", o + t, .5, m, .7)
    for t in (.5, 1.5, 2.5, 3.5):
        for m in (50, 54, 57):
            n("accords", "guitar", o + t, .25, m, .11, -.25)
    for t in (0, .75, 2, 2.75):
        n("batterie", "hat", o + t, .25, 0, .10, .3)
        n("accords", "cow_bell", o + t, .25, 78, .06, .35)
    n("batterie", "snare", o + 1, .25, 0, .12); n("batterie", "snare", o + 3, .25, 0, .12)
FIN = jouer(groove, "banjo", 62, "guitar", 50, wah=("didgeridoo", "guitar"),
            appel_kw=dict(lead="banjo", cuivre="guitar", corde="banjo", cloche="cow_bell"))
