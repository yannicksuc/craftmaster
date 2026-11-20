# Futuriste (synthwave) : grosse caisse à chaque temps, basse en octaves, nappes, arpèges rapides,
# lasers, le thème en onde carrée avec un écho (delay) au piano électrique.
BPM = 124
exec(open(os.path.join(os.path.dirname(PART), "theme.py"), encoding="utf-8").read())
PROG = [(38, (62, 66, 69)), (34, (58, 62, 65)), (36, (60, 64, 67)), (33, (57, 61, 64))]
for bar in range(7):
    o = bar * 4
    root, ch = PROG[bar % 4]
    for k in range(4):
        n("batterie", "basedrum", o + k, 1, 0, .8)
        n("batterie", "hat", o + k + .5, .25, 0, .14, .3)
    n("batterie", "snare", o + 1, .25, 0, .35)
    n("batterie", "snare", o + 3, .25, 0, .35)
    for i in range(8):
        n("basse", "bass", o + i * .5, .5, root if i % 2 == 0 else root + 12, .6 if i % 2 == 0 else .42)
    for m in ch:
        n("nappe", "bit", o, 4, m, .07, -.2)
    arp = list(ch) + [ch[0] + 12, ch[2], ch[1]]
    for k in range(16):
        n("accords", "pling", o + k * .25, .25, arp[k % len(arp)], .10 if k % 4 == 0 else .07, -.35 + .05 * (k % 6))
    if bar in (1, 5):      # laser qui monte
        for k in range(12):
            n("effet", "bit", o + 3 + k / 12, 1 / 12, 56 + 2 * k, .10, .4)
theme("melodie", "bit", 4, 62, .45, 0)
theme("echo", "pling", 4.75, 62, .14, .4)
theme("melodie", "pling", 12, 66, .40, 0)
theme("echo", "bit", 12.75, 66, .12, -.4)
for t, d, iv in [(0, .5, 0), (.5, .5, 1), (1, 1, 0), (2, .5, 7), (2.5, .5, 8), (3, 1, 7),
                 (4, .5, 5), (4.5, .5, 4), (5, .5, 1), (5.5, .5, 0)]:
    n("melodie", "bit", 20 + t, d, 62 + iv, .45)
for k in range(16):    # le laser retombe (le « wah-wah » électronique)
    n("effet", "bit", 26 + k / 8, 1 / 8, max(54, round(78 - 1.5 * k)), .18, .2)
FIN = appel(28, .9, lead="bit", cuivre="pling", corde="iron_xylophone", cloche="chime")
