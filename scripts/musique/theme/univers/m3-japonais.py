# Japonais, version zen : gamme in (ré mi♭ sol la si♭), koto (harpe) en arpèges et trémolos, shamisen (banjo)
# pincé et rare, shakuhachi (flûte) doux et soufflé, carillons à vent, bois frappés. Aucun grave (pas de basse,
# pas de cuivre grave) ; seulement un taiko feutré en début de phrase.
BPM = 66
exec(open(os.path.join(os.path.dirname(PART), "theme.py"), encoding="utf-8").read())
IN = {4: 5}
GAMME = [55, 57, 58, 62, 63, 67, 69, 70, 74, 75]

# glissandos montants du koto en début de phrase (doux)
for o in (0, 10):
    for k, m in enumerate(GAMME):
        n("koto", "harp", o + k / 10, .5, m, .06 + .008 * k, -.3)

for bar in range(5):
    o = bar * 4
    # koto : arpège lent, chaque note doublée d'un écho très doux
    for t, m in [(0, 62), (.5, 69), (1, 74), (2, 70), (2.5, 67), (3, 69)]:
        n("koto", "harp", o + t, .5, m, .12, -.3)
        n("koto", "harp", o + t + .25, .25, m, .035, -.3)
    # shamisen : deux pincements par mesure
    for t, m in ([(1.5, 62), (3.5, 69)] if bar % 2 == 0 else [(1.5, 63), (3.5, 67)]):
        n("shamisen", "banjo", o + t, .25, m, .055, .25)
    # bois frappés (hyoshigi) et taiko feutré
    if bar % 2 == 0:
        n("bois", "xylophone", o, .25, 98, .10, .1)
        n("taiko", "basedrum", o, 1, 0, .30)
    n("bois", "xylophone", o + 3.75, .25, 98, .05, .1)

# carillons à vent, partout mais légers
import random
random.seed(7)
for k in range(18):
    t = k * 1.1 + random.random() * .6
    n("vent", "chime", t, 1, random.choice([86, 87, 91, 93, 94, 98]), .06 + random.random() * .05, .45)


def shaku(o, root, vol):
    """Le thème au shakuhachi : attaque douce, souffle qui gonfle sur les notes longues."""
    for t, d, iv in THEME:
        m = root + IN.get(iv, iv)
        n("flute", "flute", o + t, d, m, vol * .8, .15)
        k = .5
        while k < d - 1e-6:
            n("flute", "flute", o + t + k, .5, m, vol * (.35 + .15 * (k / d)), .15)
            k += .5


shaku(2, 74, .40)
theme("koto", "harp", 2, 62, .12, -.3, IN, hold=False)
# la reprise trébuche : le koto finit trop bas, en dégringolant doucement
for t, d, iv in [(0, .5, 0), (.5, .5, 1), (1, 1, 0), (2, .5, 7), (2.5, .5, 8), (3, 1, 7), (4, .5, 5), (4.5, .5, 5), (5, .5, 1)]:
    n("flute", "flute", 10 + t, d, 74 + iv, .36, .15)
for k, m in enumerate([74, 70, 69, 67, 63, 62, 58, 57, 55]):
    n("koto", "harp", 15.5 + k / 4, .5, m, .16 - .01 * k, -.3)
n("shamisen", "banjo", 17.75, .25, 55, .05, .25)
FIN = appel(18, .75, lead="flute", cuivre="harp", corde="guitar", cloche="chime", grave=False,
            tete=[(0, .5, 0), (.5, .5, 1), (1, .5, 0)], accord=(62, 67, 69, 74))
for k, m in enumerate([86, 91, 93, 98]):     # les petites cloches qui ferment
    n("vent", "chime", FIN - 1 + k * .15, 1, m, .10, .45)
