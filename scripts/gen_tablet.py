"""Timer tablet assets: blockstate, models (the block itself is drawn by TimerTabletRenderer; the item is a little
3D picture frame showing "25.7"), textures, loot, recipe and translations (block + settings screen).
Called by gen_assets.py."""
import os

import gen_studio

RED = (255, 54, 40)
DIM = (40, 10, 8)

# 3 × 5 digit shapes (rows top to bottom)
DIGITS = {
    "2": ["###", "..#", "###", "#..", "###"],
    "5": ["###", "#..", "###", "..#", "###"],
    "7": ["###", "..#", "..#", "..#", "..#"],
}


# ---------------------------------------------------------------------------------------------------------------
# The atlas drawn by TimerTabletRenderer (textures/entity/timer_tablet.png, 160 x 576): one 48 px row per layer,
# and in each row the four sizes side by side (x 0, 16, 48, 96; 16 x 12 px per size step, 1 texel = 1 px on the
# tablet, so every size keeps the same pixel size). Rows: frames (7 materials), passe-partouts (3), screen, back.
# Painted from each texel's place on the frame: light from the top left, light at the top / dark at the bottom,
# limited palettes, gradients by short 2-3 px strokes, outlines in the darkest tone (never black).

FRAMES = ["black", "walnut", "oak", "gold", "steel", "white", "red"]
MATS = ["white", "cream", "black"]
SIZE_X = {1: 0, 2: 16, 3: 48, 4: 96}
ROW_H = 48

PAL = {
    "black": [(84, 84, 92), (52, 52, 58), (36, 36, 41), (27, 27, 31), (19, 19, 23)],
    "walnut": gen_studio.WALNUT,
    "oak": [(204, 166, 108), (184, 146, 90), (162, 126, 76), (136, 104, 62), (104, 78, 46)],
    "gold": gen_studio.GOLD,
    "steel": [(226, 230, 236), (192, 196, 204), (162, 166, 176), (130, 134, 144), (96, 98, 110)],
    "white": [(252, 250, 245), (240, 236, 228), (226, 220, 210), (206, 200, 190), (174, 168, 158)],
    "red": gen_studio.VELVET,
}
KIND = {"black": "lacquer", "walnut": "wood", "oak": "wood", "gold": "metal", "steel": "metal", "white": "lacquer",
        "red": "lacquer"}
MAT_PAL = {
    "white": [(252, 251, 246), (243, 240, 232), (233, 229, 218), (219, 213, 200), (196, 190, 176)],
    "cream": [(248, 238, 210), (238, 226, 194), (224, 210, 174), (206, 190, 152), (178, 162, 124)],
    "black": [(60, 60, 66), (44, 44, 49), (33, 33, 38), (25, 25, 29), (18, 18, 22)],
}
SCREEN = [(36, 38, 44), (26, 27, 32), (18, 18, 22), (11, 11, 14)]
GLASS = (50, 53, 62)
KRAFT = [(180, 146, 102), (164, 131, 89), (146, 114, 76), (124, 96, 62)]


def rail(x, y, w, h):
    """Which side of a mitred frame a texel belongs to, and how deep into it (0 = outer edge)."""
    dt, db, dl, dr = y, h - 1 - y, x, w - 1 - x
    m = min(dt, db, dl, dr)
    side = "top" if dt == m else "bottom" if db == m else "left" if dl == m else "right"
    return side, m


def stroke_tone(t, x, y, levels):
    """Vertical gradient (t 0 top .. 1 bottom) quantised to `levels` tones, transitions by 3 px strokes."""
    f = t * (levels - 1)
    i = int(f)
    if f - i > 0.55 and ((x // 3) + y) % 2 == 0:
        i += 1
    return min(levels - 1, i)


def paint_frame(img, ox, oy, s, name):
    """The frame layer (transparent inside): mitred rails, moulding profile, grain or brushing."""
    w, h, b = 16 * s, 12 * s, s
    pal, kind = PAL[name], KIND[name]
    seed = sum(map(ord, name))
    for y in range(h):
        for x in range(w):
            side, k = rail(x, y, w, h)
            if k >= b:
                continue
            # Rails facing the light are lighter (top, left), the others darker
            if b > 1:
                tone = {"top": 1, "left": 1, "right": 2, "bottom": 3}[side]
            else:
                tone = {"top": 1, "left": 1, "right": 3, "bottom": 3}[side]
            u = x if side in ("top", "bottom") else y
            if b > 1:
                if k == 0:
                    tone = 3 if side in ("top", "left") else 4  # outer edge: darkest tones, never black
                elif k == b - 1:
                    tone = min(4, tone + 1)  # rabbet: a step darker
                elif k == 1 and side in ("top", "left"):
                    tone = 0  # lit moulding crest
            if kind == "wood":
                # Grain along the rail: short dark strokes, offset row by row
                if (b <= 2 or 0 < k < b - 1) and (u + 5 * k + seed) % 7 < 2 and (k + seed) % 2 == 0:
                    tone = min(4, tone + 1)
                # Mitre joint: a darker line on the corner diagonals (wide frames)
                if b > 2 and 0 < k < b - 1 and (x == y or w - 1 - x == y or x == h - 1 - y or w - 1 - x == h - 1 - y):
                    tone = min(4, tone + 1)
            elif kind == "metal":
                # Brushed: thin light and dark streaks along the rail
                r = (u * 3 + k * 11 + seed) % 13
                if r < 2:
                    tone = max(0, tone - 1)
                elif r > 10:
                    tone = min(4, tone + 1)
            else:
                # Lacquer: a glossy reflection on the top rail
                if side == "top" and k == min(1, b - 1) and (u + seed) % 11 < 4:
                    tone = 0
            img.set(ox + x, oy + y, pal[tone])


def paint_mat(img, ox, oy, s, name):
    """The passe-partout layer (between the frame and the screen): gradient, frame shadow, bevel cut."""
    w, h, b, m = 16 * s, 12 * s, s, s
    pal = MAT_PAL[name]
    for y in range(h):
        for x in range(w):
            side, k = rail(x, y, w, h)
            if k < b or k >= b + m:
                continue
            tone = stroke_tone(y / (h - 1), x, y, 4)
            j = k - b  # 0 next to the frame .. m-1 at the cut
            if j == 0 and side in ("top", "left"):
                tone = min(4, tone + 1)  # the frame's shadow
            if m > 1 and j == m - 1:
                # Bevel: the cut faces up-left on the bottom / right edges (lit), down-right on the others
                tone = 0 if side in ("bottom", "right") else min(4, tone + 2)
            img.set(ox + x, oy + y, pal[tone])


def paint_screen(img, ox, oy, s):
    """Dark glass over the whole face (frame and mat layers cover its edges): gradient and a soft reflection."""
    w, h = 16 * s, 12 * s
    for y in range(h):
        for x in range(w):
            c = SCREEN[stroke_tone(y / (h - 1), x, y, len(SCREEN))]
            d = x + 2 * y - 4 * s  # a diagonal reflection band near the top left
            if 0 <= d < 2 * s and y < h * 0.6:
                c = GLASS
            img.set(ox + x, oy + y, c)


def paint_back_panel(img, ox, oy, s, name):
    """The back of a metal or lacquered tablet: the frame's own material as a panel (brushed metal with four screws,
    or smooth lacquer with a hanging slot), light at the top, dark at the bottom."""
    w, h = 16 * s, 12 * s
    pal, kind = PAL[name], KIND[name]
    seed = sum(map(ord, name))
    for y in range(h):
        for x in range(w):
            tone = 1 + stroke_tone(y / (h - 1), x, y, 3)
            if kind == "metal":
                r = (x * 3 + y * 11 + seed) % 13
                if r < 2:
                    tone -= 1
                elif r > 10:
                    tone += 1
            if x == 0 or y == 0:
                tone = 2
            if x == w - 1 or y == h - 1:
                tone = 4
            img.set(ox + x, oy + y, pal[max(0, min(4, tone))])
    if kind == "metal":
        # Four screws, inset from the corners: a dark ring and a lit head (one dark pixel at size 1)
        k = 1 + s
        for cx, cy in ((k, k), (w - 1 - k, k), (k, h - 1 - k), (w - 1 - k, h - 1 - k)):
            if s == 1:
                img.set(ox + cx, oy + cy, pal[4])
                continue
            for dy in range(-1, 1):
                for dx in range(-1, 1):
                    img.set(ox + cx + dx, oy + cy + dy, pal[4])
            img.set(ox + cx - 1, oy + cy - 1, pal[0])
    elif s > 1:
        # Hanging slot near the top: a dark rounded slot, lit lower edge
        y0 = 2 * s
        for x in range(w // 2 - 2 * s, w // 2 + 2 * s):
            img.set(ox + x, oy + y0, pal[4])
            img.set(ox + x, oy + y0 + 1, pal[0])


def paint_back(img, ox, oy, s):
    """Kraft cardboard back with fibres along it."""
    w, h = 16 * s, 12 * s
    for y in range(h):
        for x in range(w):
            tone = stroke_tone(y / (h - 1), x, y, 3)
            if (x * 7 + y * 3) % 17 < 2:
                tone = min(3, tone + 1)
            img.set(ox + x, oy + y, KRAFT[tone])


def atlas(g):
    # Rows: frames, passe-partouts, glass, then one back per frame material (wood: kraft, unused by the renderer)
    img = gen_studio.Img(160, ROW_H * (len(FRAMES) * 2 + len(MATS) + 1))
    for s, ox in SIZE_X.items():
        for i, f in enumerate(FRAMES):
            paint_frame(img, ox, i * ROW_H, s, f)
        for i, mname in enumerate(MATS):
            paint_mat(img, ox, (len(FRAMES) + i) * ROW_H, s, mname)
        paint_screen(img, ox, (len(FRAMES) + len(MATS)) * ROW_H, s)
        for i, f in enumerate(FRAMES):
            row = (len(FRAMES) + len(MATS) + 1 + i) * ROW_H
            if KIND[f] == "wood":
                paint_back(img, ox, row, s)
            else:
                paint_back_panel(img, ox, row, s, f)
    img.save(g, "entity/timer_tablet")


def item_face(g):
    """The item's face (16 x 12 in a 16 x 16 texture): black frame, white passe-partout, glass, red "25.7"."""
    t = gen_studio.Img(16, 16)
    paint_screen(t, 0, 0, 1)
    tmp = gen_studio.Img(16, 12)
    paint_mat(tmp, 0, 0, 1, "white")
    paint_frame(tmp, 0, 0, 1, "black")
    for y in range(12):
        for x in range(16):
            c = tmp.get(x, y)
            if c[3]:
                t.set(x, y, c)
    x = 2
    for ch in "25.7":
        if ch == ".":
            t.set(x, 8, RED)
            x += 1
            continue
        for row, line in enumerate(DIGITS[ch]):
            for col, c in enumerate(line):
                t.set(x + col, 4 + row, RED if c == "#" else DIM)
        x += 4
    t.rect(0, 12, 16, 16, (0, 0, 0, 0))
    t.save(g, "block/studio/timer_tablet")


def generate(g):
    A, D = g.A, g.D
    wj = g.write_json

    atlas(g)
    item_face(g)

    tex = "craftmaster:block/studio/timer_tablet"
    # Block: nothing drawn by the model (the renderer draws it), particles from the texture
    wj(os.path.join(A, "models/block/timer_tablet.json"), {"textures": {"particle": tex}, "elements": []})
    wj(os.path.join(A, "blockstates/timer_tablet.json"), {"variants": {"": {"model": "craftmaster:block/timer_tablet"}}})
    # Item: a 14 × 10.5 px frame, 1.2 deep, the face on the south side (towards the viewer in the inventory)
    face = {"texture": "#face", "uv": [0, 0, 16, 12]}
    edge = {"texture": "#face", "uv": [0, 0, 16, 1]}
    side = {"texture": "#face", "uv": [0, 0, 1, 12]}
    wj(os.path.join(A, "models/item/timer_tablet.json"), {
        "parent": "minecraft:block/block",
        "textures": {"face": tex, "particle": tex},
        "elements": [{"from": [0, 2, 7], "to": [16, 14, 8],
                      "faces": {"south": face, "north": face, "east": side, "west": side, "up": edge, "down": edge}}],
        "display": {
            "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.1, 1.1, 1.1]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
            # Held up in front, face towards the others (like Taskmaster's assistant)
            "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 2.5, 1], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_lefthand": {"rotation": [0, 180, 0], "translation": [0, 2.5, 1], "scale": [0.6, 0.6, 0.6]},
            "firstperson_righthand": {"rotation": [0, 160, 0], "translation": [1, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "firstperson_lefthand": {"rotation": [0, 200, 0], "translation": [-1, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "head": {"rotation": [0, 180, 0], "translation": [0, 4, -6], "scale": [1, 1, 1]},
        },
    })
    wj(os.path.join(A, "items/timer_tablet.json"), {"model": {"type": "minecraft:model", "model": "craftmaster:item/timer_tablet"}})
    wj(os.path.join(D, "craftmaster/loot_table/blocks/timer_tablet.json"), g.self_drop("timer_tablet"))
    g.shaped("timer_tablet", ["PCP", "PGP"], {"P": "minecraft:black_concrete", "C": "minecraft:clock", "G": "minecraft:glass_pane"},
             "timer_tablet", 1, "redstone")

    T = "screen.craftmaster.tablet."
    texts = {
        "block.craftmaster.timer_tablet": ("Timer Tablet", "Tablette chrono"),
        T + "title": ("Timer tablet", "Tablette chrono"),
        T + "tab.mode": ("Mode", "Mode"),
        T + "tab.look": ("Look", "Apparence"),
        T + "tab.triggers": ("Triggers", "Déclencheurs"),
        T + "tab.pose": ("Position", "Position"),
        T + "start": ("▶ Start", "▶ Démarrer"),
        T + "start.tip": ("Starts (the whole group). A finished timer starts again from the top",
                          "Démarre (tout le groupe). Un minuteur fini repart du début"),
        T + "pause": ("⏸ Pause", "⏸ Pause"),
        T + "reset": ("Reset", "Remettre"),
        T + "reset.tip": ("Back to the start, stopped", "Retour au début, à l'arrêt"),
        T + "restart": ("Restart", "Relancer"),
        T + "restart.tip": ("Back to the start and running", "Retour au début et c'est parti"),
        T + "state.running": ("● running", "● en marche"),
        T + "state.stopped": ("■ stopped", "■ à l'arrêt"),
        T + "in_group": ("group « %s »", "groupe « %s »"),
        T + "seconds": ("last %s s", "%s dernières s"),
        T + "mode": ("Shows", "Affiche"),
        T + "mode.tip": ("Timer: counts down · Stopwatch: counts up · Clock: time of day · Show: the round's timer",
                         "Minuteur : compte à rebours · Chrono : compte · Heure : l'heure · Émission : le chrono de la manche"),
        T + "mode.timer": ("Timer", "Minuteur"),
        T + "mode.stopwatch": ("Stopwatch", "Chrono"),
        T + "mode.clock": ("Clock", "Heure"),
        T + "mode.show": ("Show's timer", "Chrono de l'émission"),
        T + "duration": ("Duration", "Durée"),
        T + "duration.tip": ("1:30, 90 or 1:00:00", "1:30, 90 ou 1:00:00"),
        T + "format": ("Format", "Format"),
        T + "format.tip": ("Auto: 3:07, tenths under 10 s (9.4) · Tenths: 25.7 · Hundredths: 1:07.42",
                           "Auto : 3:07, dixièmes sous 10 s (9.4) · Dixièmes : 25.7 · Centièmes : 1:07.42"),
        T + "format.auto": ("Auto", "Auto"),
        T + "format.minutes": ("3:07", "3:07"),
        T + "format.tenths": ("25.7", "25.7"),
        T + "format.hundredths": ("1:07.42", "1:07.42"),
        T + "format.seconds": ("187", "187"),
        T + "warn": ("Warning", "Alerte"),
        T + "warn.tip": ("The last seconds blink in the warning colour", "Les dernières secondes clignotent dans la couleur d'alerte"),
        T + "warn_color": ("Warning colour", "Couleur d'alerte"),
        T + "color": ("Digits", "Chiffres"),
        T + "digitcolor.red": ("Red", "Rouge"),
        T + "digitcolor.amber": ("Amber", "Ambre"),
        T + "digitcolor.green": ("Green", "Vert"),
        T + "digitcolor.blue": ("Blue", "Bleu"),
        T + "digitcolor.white": ("White", "Blanc"),
        T + "digitcolor.gold": ("Gold", "Or"),
        T + "ghost": ("Unlit segments", "Segments éteints"),
        T + "ghost.tip": ("Faintly visible, like a real display", "Visibles en sombre, comme un vrai afficheur"),
        T + "frame": ("Frame", "Cadre"),
        T + "framecolor.black": ("Black lacquer", "Laque noire"),
        T + "framecolor.walnut": ("Walnut", "Noyer"),
        T + "framecolor.oak": ("Oak", "Chêne"),
        T + "framecolor.steel": ("Brushed steel", "Acier brossé"),
        T + "framecolor.gold": ("Brass", "Laiton doré"),
        T + "framecolor.white": ("White paint", "Blanc peint"),
        T + "framecolor.red": ("Red lacquer", "Laque rouge"),
        T + "mat": ("Passe-partout", "Passe-partout"),
        T + "mat.white": ("White", "Blanc"),
        T + "mat.cream": ("Cream", "Crème"),
        T + "mat.black": ("Black", "Noir"),
        T + "mat.none": ("None", "Aucun"),
        T + "size": ("Size", "Taille"),
        T + "label": ("Text", "Texte"),
        T + "label.tip": ("Under the digits (empty in Show mode: the round's name)", "Sous les chiffres (vide en mode Émission : le nom de la manche)"),
        T + "show_label": ("Show the text", "Afficher le texte"),
        T + "click": ("Players' click", "Clic des joueurs"),
        T + "click.tip": ("Anyone's right click starts / pauses it (organisers: Sneak + right click changes the pose)",
                          "Le clic droit de n'importe qui démarre / met en pause (organisateurs : accroupi + clic droit change la position)"),
        T + "redstone": ("Redstone", "Redstone"),
        T + "redstone.tip": ("What a redstone signal on the tablet does", "Ce que fait un signal redstone sur la tablette"),
        T + "redstone.off": ("Nothing", "Rien"),
        T + "redstone.toggle": ("Start / pause", "Démarre / pause"),
        T + "redstone.run_while_powered": ("Runs while powered", "Tourne tant qu'alimentée"),
        T + "redstone.restart": ("Restarts", "Relance"),
        T + "round_start": ("Starts with the round", "Démarre avec la manche"),
        T + "round_start.tip": ("Restarts when a round starts (/craftMasterManche start, the voice)",
                                "Repart de zéro au lancement d'une manche (/craftMasterManche start, la voix)"),
        T + "round_end": ("Stops at round end", "S'arrête en fin de manche"),
        T + "end_sound": ("End sound", "Son de fin"),
        T + "endsound.none": ("None", "Aucun"),
        T + "endsound.beep": ("Beep", "Bip"),
        T + "endsound.bell": ("Bell", "Cloche"),
        T + "endsound.gong": ("Gong", "Gong"),
        T + "endsound.buzzer": ("Buzzer", "Buzzer"),
        T + "blink": ("Blinks at the end", "Clignote à la fin"),
        T + "signal": ("Comparator at the end", "Comparateur à la fin"),
        T + "signal.tip": ("A comparator reads 15 once the timer is over (doors, lamps, a gong…)",
                           "Un comparateur lit 15 quand le minuteur est fini (portes, lampes, gong…)"),
        T + "group": ("Group", "Groupe"),
        T + "group.tip": ("Tablets of the same group start and stop together; /craftMasterTablet start <group>",
                          "Les tablettes d'un même groupe démarrent et s'arrêtent ensemble ; /craftMasterTablet start <groupe>"),
        T + "pose": ("Position", "Position"),
        T + "pose.stand": ("Standing", "Debout"),
        T + "pose.easel": ("On an easel", "Sur chevalet"),
        T + "pose.flat": ("Lying flat", "À plat"),
        T + "pose.wall": ("On the wall", "Au mur"),
        T + "tilt": ("Easel tilt", "Inclinaison"),
        T + "wall": ("Hung on a wall (place it on another face to change)", "Accrochée au mur (la reposer ailleurs pour changer)"),
        T + "pose_hint": ("Placing: on a wall = hung, on the ground = standing, sneaking = flat. Facing you, 16 directions.",
                          "Pose : sur un mur = accrochée, au sol = debout, accroupi = à plat. Tournée vers vous, 16 directions."),
    }
    for k, (e, f) in texts.items():
        g.en[k] = e
        g.fr[k] = f
