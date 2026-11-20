TENUES = False  # notes tenues désactivées : LordFinn préfère les sons courts d'origine (20/11)
# Le thème de base CraftMaster, repris partout : 2 mesures faciles à chanter.
# « ré mi♭ ré — la si♭ la — sol fa♯ mi♭ ré »  (mode hijaz sur ré, le même que la fanfare v4)
THEME = [(0, .5, 0), (.5, .5, 1), (1, 1, 0), (2, .5, 7), (2.5, .5, 8), (3, 1, 7),
         (4, .5, 5), (4.5, .5, 4), (5, .5, 1), (5.5, 2.5, 0)]
MAJEUR = {1: 2, 8: 9}      # victoire : ré mi ré — la si la — sol fa♯ mi ré
MINEUR = {4: 3}            # tristesse : fa naturel au lieu de fa♯


def fit(inst, m):
    """Ramène une note dans la plage jouable de l'instrument (par octaves)."""
    b = INSTR[inst][1]
    while m > b + 12: m -= 12
    while m < b - 12: m += 12
    return m


def theme(voice, inst, o, root=62, vol=.5, pan=0.0, mode=None, stretch=1.0, hold=True):
    for t, d, iv in THEME:
        iv2 = (mode or {}).get(iv, iv)
        n(voice, inst, o + t * stretch, (d if hold else min(d, .5)) * stretch, root + iv2, vol, pan)


def appel(o, intensite=1.0, lead="flute", cuivre="bit", corde="harp", cloche="bell", tete=None, grave=True, accord=(62, 66, 69, 74)):
    """La fin commune : « ré-mi♭-ré » sec, roulement, puis le grand accord de ré qui ouvre l'épreuve."""
    # Version douce : la tête du thème à la flûte, posée, puis un arpège de ré qui s'ouvre vers le haut
    # (ré fa♯ la ré) et une cloche tenue. Pas de timbales ni de coup sec : ça se pose, comme une porte qui s'ouvre.
    # Fin franche mais pas brutale : la tête du thème, une levée sur la, puis un accord de ré net
    # (cuivres + harpe + flûte, une seule grosse caisse feutrée), qui sonne et s'arrête. Pas de fondu ni de roulement.
    v = intensite
    tete = tete or [(0, .5, 0), (.5, .5, 1), (1, .5, 0)]
    for t, d, iv in tete:
        n("appel", lead, o + t, d, fit(lead, 74 + iv), .45 * v, .1)
        n("appel", cuivre, o + t, d, fit(cuivre, 62 + iv), .30 * v, -.1)
    if grave:
        n("appel", "bass", o, 1.5, 38, .55 * v)
    n("appel", cuivre, o + 1.5, .5, fit(cuivre, 69), .32 * v, -.1)        # levée
    n("appel", corde, o + 1.5, .5, fit(corde, 57), .25 * v, -.3)
    h = o + 2
    for m in accord:
        n("appel", cuivre, h, 1, fit(cuivre, m), .34 * v, -.1)
        n("appel", corde, h, 1, fit(corde, m), .30 * v, -.3)
    n("appel", lead, h, 1, fit(lead, 86), .40 * v, .1)
    n("appel", cloche, h, 1, fit(cloche, 86), .20 * v, .35)
    if grave:
        n("appel", "bass", h, 1, 38, .85 * v)
        n("appel", "bass", h, 1, 50, .5 * v)
        n("appel", "basedrum", h, 1, 0, .55 * v)
    return h + 1
