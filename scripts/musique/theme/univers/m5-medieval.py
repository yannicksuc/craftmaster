# Médiéval, version barde de taverne : gigue à 6/8 (un temps = une croche), le luth (banjo) mène avec des
# ornements, accords grattés en arpège (harpe), flûte à bec en contrechant, tambour à main et tambourin,
# petit bourdon de vielle léger (guitare). Pas de grave de cuivre.
BPM = 252
exec(open(os.path.join(os.path.dirname(PART), "theme.py"), encoding="utf-8").read())
DORIEN = {1: 2, 8: 9, 4: 3}     # ré mi ré — la si la — sol fa mi ré
ACC = [(62, 65, 69), (62, 65, 69), (60, 64, 67), (62, 65, 69)]   # ré m, ré m, do, ré m
for bar in range(9):
    o = bar * 6
    ch = ACC[bar % 4]
    for t in (0, 3):
        for k, m in enumerate(ch):           # accord gratté (roulé)
            n("luth", "harp", o + t + k * .15, 2.5, m, .13, -.3)
        n("bourdon", "guitar", o + t, 3, 50, .08, -.2)
        n("bourdon", "bass", o + t, 3, 38, .22)
        n("tambour", "basedrum", o + t, 1, 0, .35)
    for t in (1, 2, 4, 5):
        n("tambour", "hat", o + t, 1, 0, .09 if t in (2, 5) else .06, .3)   # tambourin
    n("tambour", "snare", o + 5, 1, 0, .08)
    if bar % 2 == 1:   # « clap » de taverne
        n("tambour", "snare", o + 3, 1, 0, .14)
SIX8 = [(0, 2, 0), (2, 1, 1), (3, 3, 0), (6, 2, 7), (8, 1, 8), (9, 3, 7),
        (12, 2, 5), (14, 1, 4), (15, 3, 1), (18, 6, 0)]


def luth(o, root, vol):
    """Le thème au luth : chaque note longue est ornée (tour : note, au-dessus, note, en dessous, note)."""
    for t, d, iv in SIX8:
        m = root + DORIEN.get(iv, iv)
        if d >= 3:
            for k, dm in enumerate([0, 2, 0, -2 if m - 2 >= 54 else 0, 0]):
                n("air", "banjo", o + t + k * .5, .5, m + dm, vol * (1 if k == 0 else .65), .2)
            n("air", "banjo", o + t + 2.5, d - 2.5, m, vol * .55, .2)
        else:
            n("air", "banjo", o + t, d, m, vol, .2)


luth(6, 62, .42)
luth(30, 62, .40)
for t, d, iv in SIX8:                        # la flûte à bec rejoint à la reprise, une octave au-dessus
    n("air", "flute", 30 + t, d, 74 + DORIEN.get(iv, iv), .26, .1)
# la fin commune, deux fois plus lente (un temps de la fin = une noire), au luth et à la flûte
_n = n
O = 54


def n(voice, inst, t, d, m, v=1.0, pan=0.0):
    _n(voice, inst, O + (t - O) * 2, d * 2, m, v, pan)


FIN = O + (appel(O, .85, lead="flute", cuivre="banjo", corde="harp", cloche="bell", grave=False,
                 accord=(62, 65, 69, 74)) - O) * 2
