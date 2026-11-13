"""Studio set assets (chantier F, docs/wiki/Plateau.md): models, blockstates, item definitions, textures, recipes,
loot tables, sounds and translations of the throne, seats, lights, spotlight, giant timer, studio screen, desks,
prize table, gong, bell, "EN DIRECT" sign and stanchions.

Called by scripts/gen_assets.py (generate(g) with the gen_assets module, before the translations are written).
Textures are painted from each texel's place on the object: limited palettes (gold, red velvet, walnut, black
metal, cream), light from the top left, no random noise.
"""
import math
import os

GOLD = [(255, 238, 150), (244, 202, 72), (222, 168, 48), (182, 124, 32), (128, 84, 24)]
VELVET = [(222, 70, 74), (184, 38, 48), (156, 28, 40), (122, 20, 32), (86, 12, 22)]
WALNUT = [(140, 96, 60), (118, 78, 48), (96, 62, 38), (76, 48, 30), (56, 34, 22)]
METAL = [(96, 98, 108), (70, 72, 82), (52, 54, 62), (38, 38, 46), (26, 26, 32)]
CREAM = [(255, 248, 226), (246, 234, 204), (230, 214, 178), (204, 184, 146), (170, 148, 112)]
MARBLE = [(250, 248, 242), (238, 234, 226), (222, 216, 206), (198, 190, 178), (160, 150, 138)]
SILVER = [(250, 252, 255), (214, 220, 228), (182, 188, 198), (140, 146, 158), (98, 102, 114)]
BRONZE = [(246, 186, 132), (214, 140, 84), (184, 108, 58), (146, 80, 40), (100, 54, 26)]
GREEN_GLASS = [(150, 220, 160), (70, 170, 100), (40, 132, 72), (26, 98, 52), (16, 66, 36)]
T = (0, 0, 0, 0)


class Img:
    def __init__(self, w, h, fill=T):
        self.w, self.h = w, h
        self.p = [[fill if len(fill) == 4 else fill + (255,) for _ in range(w)] for _ in range(h)]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[y][x] = c if len(c) == 4 else tuple(c) + (255,)

    def get(self, x, y):
        return self.p[y][x]

    def rect(self, x0, y0, x1, y1, c):
        """Fills x0..x1-1, y0..y1-1."""
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, c)

    def paint(self, x0, y0, x1, y1, f):
        """f(x, y, w, h) with local coordinates."""
        w, h = x1 - x0, y1 - y0
        for y in range(h):
            for x in range(w):
                c = f(x, y, w, h)
                if c is not None:
                    self.set(x0 + x, y0 + y, c)

    def save(self, g, rel):
        g.write_png(os.path.join(g.A, "textures", rel + ".png"), self.w, self.h, self.p)


def bevel(pal, x, y, w, h, flat=1):
    """Raised panel: light top / left edge, dark bottom / right edge, flat middle."""
    if y == 0 or x == 0:
        return pal[0]
    if y == h - 1 or x == w - 1:
        return pal[3]
    return pal[flat]


def tufted(pal, x, y, w, h, step=4, buttons=GOLD[1]):
    """Button-tufted velvet: diamonds lit from the top left, a button at each corner of the grid."""
    gx, gy = x % step, y % step
    if gx == step // 2 and gy == step // 2:
        return buttons
    # Distance to the nearest button, shading the diamond facets
    dx, dy = gx - step / 2, gy - step / 2
    if abs(dx) + abs(dy) >= step / 2 + 0.5:
        return pal[3]                      # crease between diamonds
    if dx + dy < -0.5:
        return pal[0] if dx + dy < -1.5 else pal[1]
    return pal[2] if dx + dy > 0.5 else pal[1]


def velvet_folds(pal, x, y, w, h, period=4):
    """Vertical folds catching the light (curtain look)."""
    f = math.sin((x % period + 0.5) / period * math.pi)
    i = 3 - int(f * 2.99)
    return pal[max(0, min(4, i))]


def wood_grain(pal, x, y, w, h):
    """Walnut: horizontal grain lines, a darker line every 4 px with a small shift."""
    line = (y + (x // 6) % 2) % 4
    return pal[1] if line == 0 else pal[2] if line in (1, 3) else pal[1] if x % 9 == 4 else pal[2]


def generate(g):
    A, D = g.A, g.D
    wj, cube, self_drop = g.write_json, g.cube, g.self_drop
    from gen_toys import gui_fit  # inventory icon centred in the slot at a set size
    FY = g.FACING_Y
    en, fr = g.en, g.fr

    def model(name, elements, textures, parent="minecraft:block/block", extra=None):
        m = {"parent": parent, "textures": textures, "elements": elements}
        if extra:
            m.update(extra)
        wj(os.path.join(A, f"models/block/{name}.json"), m)

    def el(frm, to, tex, faces=None, emission=0, skip=(), uv=None):
        """Element with automatic UVs taken from its place in its 16 px band (works above the block too); faces:
        a dict face -> texture overriding tex for some faces; uv: per-face explicit uv."""
        (x1, y1, z1), (x2, y2, z2) = frm, to
        band = math.floor(y1 / 16) if y1 < 32 else 1
        b = band * 16
        v1, v2 = 16 - (y2 - b), 16 - (y1 - b)

        def wrap(a, c):
            # Keep horizontal UVs inside 0..16 for elements sticking out of the block
            if a < 0 or c > 16:
                shift = math.floor(a / 16) * 16
                return a - shift, c - shift
            return a, c
        ux1, ux2 = wrap(x1, x2)
        uz1, uz2 = wrap(z1, z2)
        auto = {
            "north": [16 - ux2, v1, 16 - ux1, v2], "south": [ux1, v1, ux2, v2],
            "east": [16 - uz2, v1, 16 - uz1, v2], "west": [uz1, v1, uz2, v2],
            "up": [ux1, uz1, ux2, uz2], "down": [ux1, 16 - uz2, ux2, 16 - uz1],
        }
        out = {}
        for f in ("north", "south", "east", "west", "up", "down"):
            if f in skip:
                continue
            t = (faces or {}).get(f, tex)
            if t is None:
                continue
            out[f] = {"texture": t, "uv": (uv or {}).get(f, auto[f])}
        e = {"from": list(frm), "to": list(to), "faces": out}
        if emission:
            e["light_emission"] = emission
        return e

    def facing_states(name, models_for, props=None):
        """Blockstate with the 4 facings (+ other properties): models_for(dict of property values) -> model name."""
        variants = {}
        keys = sorted((props or {}).keys())

        def combos(i, cur):
            if i == len(keys):
                yield dict(cur)
                return
            for v in props[keys[i]]:
                cur[keys[i]] = v
                yield from combos(i + 1, cur)
        for f, y in FY.items():
            for combo in combos(0, {}):
                combo_all = dict(combo, facing=f)
                key = ",".join(f"{k}={combo_all[k]}" for k in sorted(combo_all))
                v = {"model": f"craftmaster:block/{models_for(combo)}"}
                if y:
                    v["y"] = y
                variants[key] = v
        wj(os.path.join(A, f"blockstates/{name}.json"), {"variants": variants})

    def item_block(name, model_name=None):
        wj(os.path.join(A, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"craftmaster:block/{model_name or name}"}})

    def item_flat(name):
        wj(os.path.join(A, f"models/item/{name}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"craftmaster:item/{name}"}})
        wj(os.path.join(A, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"craftmaster:item/{name}"}})

    def loot(name):
        wj(os.path.join(D, f"craftmaster/loot_table/blocks/{name}.json"), self_drop(name))

    def tex(name):
        return f"craftmaster:block/studio/{name}"

    # ================================================================== textures (block atlas)

    plain_gold = Img(16, 16)
    plain_gold.paint(0, 0, 16, 16, lambda x, y, w, h: GOLD[1] if (x * 3 + y * 5) % 11 else GOLD[2] if y > 7 else GOLD[0])
    plain_gold.save(g, "block/studio/gold_plain")

    velvet = Img(16, 16)
    velvet.paint(0, 0, 16, 16, lambda x, y, w, h: tufted(VELVET, x, y, w, h))
    velvet.save(g, "block/studio/velvet_tufted")

    vfold = Img(16, 16)
    vfold.paint(0, 0, 16, 16, lambda x, y, w, h: velvet_folds(VELVET, x, y, w, h))
    vfold.save(g, "block/studio/velvet_folds")

    vplain = Img(16, 16)
    vplain.paint(0, 0, 16, 16, lambda x, y, w, h: VELVET[2] if (x + 2 * y) % 5 else VELVET[3])
    vplain.save(g, "block/studio/velvet")


    walnut = Img(16, 16)
    walnut.paint(0, 0, 16, 16, lambda x, y, w, h: wood_grain(WALNUT, x, y, w, h))
    walnut.save(g, "block/studio/walnut")

    def joined_suffix(left, right):
        return ("_" + ("l" if left else "") + ("r" if right else "")) if (left or right) else ""

    # Brass name plate (front 10 x 4 at x 3..13, y 6..10 of the block = rows 6..9 of the texture)
    plate = Img(16, 16, GOLD[2])
    plate.paint(0, 0, 16, 16, lambda x, y, w, h: GOLD[1])
    plate.paint(3, 6, 13, 10, lambda x, y, w, h: bevel([GOLD[0], CREAM[1], CREAM[1], GOLD[3]], x, y, w, h))
    plate.save(g, "block/studio/name_plate")

    metal = Img(16, 16)
    metal.paint(0, 0, 16, 16, lambda x, y, w, h: METAL[1] if y % 4 else METAL[2] if x % 2 else METAL[1])
    metal.save(g, "block/studio/metal")

    brass = Img(16, 16)
    brass.paint(0, 0, 16, 16, lambda x, y, w, h: GOLD[0] if x == 7 else GOLD[1] if x in (6, 8) else GOLD[2] if x % 4 else GOLD[3])
    brass.save(g, "block/studio/brass")

    # Lamp shade: cream pleated fabric, gold trims top and bottom; lit = brighter, warm glow at the bottom
    for lit in (False, True):
        s = Img(16, 16)

        # Rows 1..5: the narrow top part of the shade, rows 5..11: the wide bottom part (see the floor lamp model)
        def shade(x, y, w, h, lit=lit):
            if y in (1, 10):
                return GOLD[1] if y == 1 else GOLD[2]
            if y == 5:
                return CREAM[3]
            c = CREAM[1 if x % 3 else 2] if not lit else CREAM[0 if x % 3 else 1]
            if lit and y > 10:
                c = (255, 236, 170)
            return c
        s.paint(0, 0, 16, 16, shade)
        s.save(g, "block/studio/lampshade" + ("_lit" if lit else ""))
    glow = Img(16, 16)
    glow.paint(0, 0, 16, 16, lambda x, y, w, h: (255, 250, 220) if max(abs(x - 7.5), abs(y - 7.5)) < 3 else (255, 226, 150) if max(abs(x - 7.5), abs(y - 7.5)) < 5.5 else CREAM[2])
    glow.save(g, "block/studio/lamp_glow")
    dark_glow = Img(16, 16)
    dark_glow.paint(0, 0, 16, 16, lambda x, y, w, h: CREAM[3] if max(abs(x - 7.5), abs(y - 7.5)) < 3 else CREAM[4])
    dark_glow.save(g, "block/studio/lamp_off")

    # Banker's desk lamp atlas: base top 8 x 6 (0, 0), stem 2 x 5 (8, 0), knob 2 x 2 (12, 0), base sides (0, 6) and
    # (8, 6), green shade sides 10 x 3 (0, 7) and 6 x 3 (10, 7), shade top 10 x 6 (0, 10): lit from the top left
    dl = Img(16, 16)
    dl.paint(0, 0, 8, 6, lambda x, y, w, h: bevel(GOLD, x, y, w, h))
    dl.paint(8, 0, 10, 5, lambda x, y, w, h: GOLD[0] if x == 0 else GOLD[2])
    dl.paint(12, 0, 14, 2, lambda x, y, w, h: GOLD[0] if x == 0 and y == 0 else GOLD[1] if x + y < 2 else GOLD[3])
    dl.rect(0, 6, 8, 7, GOLD[3])
    dl.rect(8, 6, 14, 7, GOLD[3])

    def shade_side(x, y, w, h):
        if y == 0:
            return GREEN_GLASS[1]
        if y == 1:
            return GREEN_GLASS[0] if 1 <= x <= 2 else GREEN_GLASS[2]
        return GOLD[2]
    dl.paint(0, 7, 10, 10, shade_side)
    dl.paint(10, 7, 16, 10, shade_side)
    dl.paint(0, 10, 10, 16, lambda x, y, w, h: (200, 240, 206) if (y == 2 and x in (1, 2))
             else [GREEN_GLASS[2], GREEN_GLASS[1], GREEN_GLASS[0], GREEN_GLASS[1], GREEN_GLASS[2], GREEN_GLASS[3]][y])
    dl.save(g, "block/studio/desk_lamp")

    # Ceiling pendant atlas: canopy 6 x 6 (0, 0) and its side (0, 6), small shade side 6 x 2 (6, 0), big shade side
    # 10 x 4 (6, 2), big shade top 10 x 10 (6, 6): polished gold, light at the top
    pd = Img(16, 16)
    pd.paint(0, 0, 6, 6, lambda x, y, w, h: bevel(GOLD, x, y, w, h))
    pd.rect(0, 6, 6, 7, GOLD[2])
    pd.paint(6, 0, 12, 2, lambda x, y, w, h: GOLD[1] if y == 0 else GOLD[2])
    pd.paint(6, 2, 16, 6, lambda x, y, w, h: GOLD[0] if (y == 1 and x in (2, 3)) else [GOLD[0], GOLD[1], GOLD[2], GOLD[3]][y])
    pd.paint(6, 6, 16, 16, lambda x, y, w, h: GOLD[1] if max(abs(x - 4.5), abs(y - 4.5)) < 3.5 else GOLD[0] if x + y < 9 else GOLD[2])
    pd.save(g, "block/studio/pendant")


    # Gold chain, laid out like the vanilla chain (columns 0..2: links seen flat, 3..5: links seen edge-on), so the
    # two crossed planes show a continuous chain
    chain_rows = ["...D.D", "LMMDDM", "M.D...", "DDMLMM", "...M.D", "...D.D", "LMMDDM", "M.D...",
                  "D.D...", "DDMMMM", "...M.D", "...D.D", "LMMDDM", "M.D...", "DDMLMM", "...M.D"]
    chain_pal = {"L": GOLD[0], "M": GOLD[2], "D": GOLD[4]}
    chain = Img(16, 16)
    for y, row in enumerate(chain_rows):
        for x, c in enumerate(row):
            if c != ".":
                chain.set(x, y, chain_pal[c])
    chain.save(g, "block/studio/chain")

    # "EN DIRECT" light box, 64 x 16 (front 40 x 10 at the top left), off and lit
    font = {
        "E": ["###", "#..", "##.", "#..", "###"], "N": ["#..#", "##.#", "#.##", "#..#", "#..#"],
        "D": ["##.", "#.#", "#.#", "#.#", "##."], "I": ["#", "#", "#", "#", "#"],
        "R": ["##.", "#.#", "##.", "#.#", "#.#"], "C": [".##", "#..", "#..", "#..", ".##"],
        "T": ["###", ".#.", ".#.", ".#.", ".#."], " ": ["..", "..", "..", "..", ".."],
    }
    word = "EN DIRECT"
    width = sum(len(font[ch][0]) + 1 for ch in word) - 1
    for lit in (False, True):
        s = Img(64, 16, (40, 14, 16))
        frame = GOLD if lit else [tuple(int(v * 0.8) for v in c) for c in GOLD]
        s.paint(0, 0, 40, 10, lambda x, y, w, h: frame[1] if y == 0 or x == 0 else frame[3] if y == h - 1 or x == w - 1
                else ((150, 24, 26) if lit else (54, 18, 20)) if (x + y) % 2 else ((166, 30, 30) if lit else (60, 20, 22)))
        x = (40 - width) // 2
        for ch in word:
            glyph = font[ch]
            for gy, row in enumerate(glyph):
                for gx, px in enumerate(row):
                    if px == "#":
                        s.set(x + gx, 3 + gy - 1 + 1, (255, 246, 214) if lit else (120, 70, 66))
            x += len(glyph[0]) + 1
        # Back (40..64): dark; sides (40..43 x 0..10) and top / bottom (0..40 x 10..13): the gold frame, wrapping round
        s.paint(40, 0, 64, 16, lambda x, y, w, h: (34, 20, 22))
        s.paint(40, 0, 43, 10, lambda x, y, w, h, frame=frame: frame[1] if y == 0 else frame[3] if y == h - 1 else frame[2] if x == 1 else frame[1])
        s.paint(0, 10, 40, 13, lambda x, y, w, h, frame=frame: frame[1] if y == 1 else frame[2] if y == 0 else frame[3])
        s.save(g, "block/studio/on_air" + ("_lit" if lit else ""))

    # Bell dome: polished gold with a round highlight
    dome = Img(16, 16)
    dome.paint(0, 0, 16, 16, lambda x, y, w, h: GOLD[0] if (x - 6) ** 2 + (y - 6) ** 2 < 5 else GOLD[1] if (x - 7) ** 2 + (y - 7) ** 2 < 30 else GOLD[3])
    dome.save(g, "block/studio/bell_dome")

    # ================================================================== textures (entities, drawn by renderers)
    # Studio screen bezel (32 x 32): glossy black front (0..16), dark sides (16..18), back with a vent grid (16..32, 16..32)
    bezel = Img(32, 32, (20, 20, 24))
    bezel.paint(0, 0, 16, 16, lambda x, y, w, h: (34, 34, 40) if y == 0 or x == 0 else (12, 12, 16) if y == h - 1 or x == w - 1 else (18, 18, 22))
    bezel.paint(16, 0, 18, 16, lambda x, y, w, h: (28, 28, 34))
    bezel.paint(16, 16, 32, 32, lambda x, y, w, h: METAL[3] if (x % 4 == 1 and 3 < y < 12) else METAL[2])
    bezel.save(g, "entity/studio_screen")

    # Test card (128 x 72): colour bars, a gold-framed velvet roundel with "CM", a grey scale at the bottom
    tc = Img(128, 72)
    bars = [(235, 235, 235), (240, 200, 60), (60, 200, 210), (80, 190, 70), (200, 70, 190), (210, 50, 50), (50, 60, 200)]
    for x in range(128):
        for y in range(72):
            c = bars[min(6, x * 7 // 128)]
            if y >= 54:
                c = (255, 255, 255) if (x // 16) % 2 else (16, 16, 20) if y < 62 else tuple([int(255 * (x / 127))] * 3)
            tc.set(x, y, c)
    cx, cy = 63.5, 29.5
    for x in range(128):
        for y in range(56):
            r = math.hypot((x - cx), (y - cy))
            if r < 22:
                c = VELVET[1] if r < 20 else GOLD[1] if r < 21.2 else GOLD[3]
                if r < 20 and (x - cx) + (y - cy) < -14:
                    c = VELVET[0]
                tc.set(x, y, c)
    big = {"C": [".####", "#....", "#....", "#....", "#....", "#....", ".####"],
           "M": ["#...#", "##.##", "#.#.#", "#...#", "#...#", "#...#", "#...#"]}
    x0 = 50
    for ch in "CM":
        for gy, row in enumerate(big[ch]):
            for gx, px in enumerate(row):
                if px == "#":
                    for sy in range(2):
                        for sx in range(2):
                            tc.set(x0 + gx * 2 + sx, 22 + gy * 2 + sy, GOLD[0] if gy < 3 else GOLD[1])
        x0 += 14
    tc.save(g, "entity/studio_test_card")

    # Giant timer (64 x 32): panel face 40 x 16 (gold frame, black field, velvet band at the top), white (40..48, 0..8),
    # gold metal for the sides (48..64, 0..16)
    tm = Img(64, 32, (0, 0, 0))

    def face(x, y, w, h):
        if x == 0 or y == 0:
            return GOLD[0]
        if x == w - 1 or y == h - 1:
            return GOLD[3]
        if x == 1 or y == 1 or x == w - 2 or y == h - 2:
            return GOLD[2]
        if y <= 4:                                  # velvet band (panel rows 12..15 from the bottom)
            return GOLD[3] if y == 4 else VELVET[2] if x % 4 else VELVET[3]
        return (14, 10, 10) if (x + y) % 2 else (18, 12, 12)
    tm.paint(0, 0, 40, 16, face)
    tm.rect(40, 0, 48, 8, (255, 255, 255))
    tm.paint(48, 0, 64, 16, lambda x, y, w, h: GOLD[1] if y % 4 else GOLD[2])
    tm.save(g, "entity/studio_timer")

    # Spotlight (32 x 32): lens (0..8, 0..8), front ring (8..16, 0..8), back vents (16..24, 0..8), yoke metal
    # (24..26, 0..8), sides (0..10, 8..16), top / bottom (0..8, 16..26)
    sp = Img(32, 32, METAL[3])
    sp.paint(0, 0, 8, 8, lambda x, y, w, h: (255, 255, 255) if (x - 3.5) ** 2 + (y - 3.5) ** 2 < 12 else (230, 230, 230))
    sp.paint(8, 0, 16, 8, lambda x, y, w, h: METAL[0] if x in (0, 7) or y in (0, 7) else (210, 214, 220) if (x - 3.5) ** 2 + (y - 3.5) ** 2 < 6 else (150, 156, 166))
    sp.paint(16, 0, 24, 8, lambda x, y, w, h: METAL[4] if y % 2 and 0 < x < 7 else METAL[2])
    sp.paint(24, 0, 26, 8, lambda x, y, w, h: METAL[1] if x == 0 else METAL[2])
    sp.paint(0, 8, 10, 16, lambda x, y, w, h: METAL[1] if y == 0 else METAL[3] if y == h - 1 else METAL[2] if x % 3 else METAL[1])
    sp.paint(0, 16, 8, 26, lambda x, y, w, h: METAL[1] if x in (0, 7) else METAL[2])
    sp.save(g, "entity/spotlight")

    # Gong (32 x 16): front disc (0..12), back (12..24), cord (24..25): round, with concentric rings and a boss
    gong = Img(32, 16)
    for side, ox in ((0, 0), (1, 12)):
        for y in range(12):
            for x in range(12):
                r = math.hypot(x - 5.5, y - 5.5)
                if r > 6.0:
                    continue
                if r > 5.2:
                    c = GOLD[3]
                elif r < 1.6:
                    c = GOLD[0] if side == 0 else GOLD[2]
                elif int(r * 2) % 3 == 0:
                    c = GOLD[2]
                else:
                    c = GOLD[1] if (x + y) < 11 else GOLD[2]
                if side == 1:
                    c = tuple(int(v * 0.8) for v in c)
                gong.set(ox + x, y, c)
    gong.rect(24, 0, 25, 4, (150, 30, 30))
    gong.save(g, "entity/gong")

    # Stanchion atlas: foot top 6 x 6 (0, 0) and side (0, 6), post side 2 x 12 (6, 0), cap top 4 x 4 (8, 0) and side 4 x 2
    # (8, 4): polished brass, a light edge on the lit side, darker toward the bottom
    st = Img(16, 16)
    st.paint(0, 0, 6, 6, lambda x, y, w, h: bevel(GOLD, x, y, w, h))
    st.rect(0, 6, 6, 7, GOLD[3])
    st.paint(6, 0, 8, 12, lambda x, y, w, h: (GOLD[0] if y < 6 else GOLD[1]) if x == 0 else (GOLD[2] if y < 8 else GOLD[3]))
    st.paint(8, 0, 12, 4, lambda x, y, w, h: bevel(GOLD, x, y, w, h))
    st.paint(8, 4, 12, 6, lambda x, y, w, h: GOLD[1] if y == 0 else GOLD[3])
    st.save(g, "block/studio/stanchion")

    # Velvet cord (rows 6..7): a twisted cord, strands running diagonally, lit on the upper row
    rope = Img(16, 16)
    rope.paint(0, 6, 16, 8, lambda x, y, w, h: (VELVET[1] if y == 0 else VELVET[2]) if (x + y) % 4 else (VELVET[3] if y == 0 else VELVET[4]))
    rope.save(g, "entity/velvet_rope")

    # ================================================================== models and blockstates
    GP, V, VF, VP, W = tex("gold_plain"), tex("velvet_tufted"), tex("velvet_folds"), tex("velvet"), tex("walnut")

    # Throne: LordFinn's own model from his server, see scripts/gen_furniture.py

    # Item views of the pieces bigger than a block: smaller, centred in the slot / the hand / the frame
    TALL_DISPLAY = {
        "gui": {"rotation": [30, 225, 0], "translation": [0, -3.25, 0], "scale": [0.4, 0.4, 0.4]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 1, 0], "scale": [0.2, 0.2, 0.2]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, -3.5, 0], "scale": [0.4, 0.4, 0.4]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -2, 0], "scale": [0.25, 0.25, 0.25]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, -2, 0], "scale": [0.25, 0.25, 0.25]},
    }
    WIDE_DISPLAY = {
        "gui": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.2, 0.2, 0.2]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.38, 0.38, 0.38]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.25, 0.25, 0.25]},
        "firstperson_righthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]},
    }

    # Floor lamp (2 blocks high in one block: the shade is above)
    for lit in (False, True):
        e = 15 if lit else 0
        model("floor_lamp" + ("_lit" if lit else ""), [
            el([5, 0, 5], [11, 1, 11], "#brass"),
            el([7, 1, 7], [9, 16, 9], "#brass"), el([7, 16, 7], [9, 21, 9], "#brass"),
            el([3, 21, 3], [13, 27, 13], "#shade", faces={"down": "#glow", "up": "#shade_top"}, emission=e),
            el([4, 27, 4], [12, 31, 12], "#shade", faces={"down": None, "up": "#glow"}, emission=e),
        ], {"particle": tex("brass"), "brass": tex("brass"), "shade": tex("lampshade_lit" if lit else "lampshade"),
            "shade_top": tex("lampshade_lit" if lit else "lampshade"), "glow": tex("lamp_glow" if lit else "lamp_off")},
            extra={"ambientocclusion": False, "display": TALL_DISPLAY})
    facing_states("floor_lamp", lambda c: "floor_lamp" + ("_lit" if c["lit"] == "true" else ""), {"lit": ["false", "true"]})
    item_block("floor_lamp", "floor_lamp_lit")

    def uvs(sides_long, sides_short, up=None, down=None):
        """Faces of a box facing north: long sides north / south, short ones east / west."""
        f = {"north": sides_long, "south": sides_long, "east": sides_short, "west": sides_short}
        if up:
            f["up"] = up
        if down:
            f["down"] = down
        return f

    def box(frm, to, tex_ref, uv, emission=0):
        e = {"from": frm, "to": to, "faces": {k: {"texture": tex_ref, "uv": v} for k, v in uv.items()}}
        if emission:
            e["light_emission"] = emission
        return e

    # Banker's lamp: brass base and stem, green glass shade, the light on a plane under the shade (only it glows)
    DL = tex("desk_lamp")
    for lit in (False, True):
        els = [
            box([4, 0, 5], [12, 1, 11], "#lamp", uvs([0, 6, 8, 7], [8, 6, 14, 7], [0, 0, 8, 6], [0, 0, 8, 6])),
            box([7, 1, 7], [9, 6, 9], "#lamp", uvs([8, 0, 10, 5], [8, 0, 10, 5])),
            box([3, 6, 5], [13, 9, 11], "#lamp", uvs([0, 7, 10, 10], [10, 7, 16, 10], [0, 10, 10, 16])),
            box([3, 6, 5], [13, 6, 11], "#glow", {"down": [3, 5, 13, 11]}, 12 if lit else 0),
            box([7, 9, 7], [9, 10, 9], "#lamp", uvs([12, 0, 14, 1], [12, 0, 14, 1], [12, 0, 14, 2])),
        ]
        # A small lamp on its own: drawn bigger and centred in the slot (it filled 8 x 9 px, low)
        model("desk_lamp" + ("_lit" if lit else ""), els, {"particle": DL, "lamp": DL, "glow": tex("lamp_glow" if lit else "lamp_off")},
              extra={"ambientocclusion": False, "display": {"gui": gui_fit(els, 13)}})
    facing_states("desk_lamp", lambda c: "desk_lamp" + ("_lit" if c["lit"] == "true" else ""), {"lit": ["false", "true"]})
    item_block("desk_lamp", "desk_lamp_lit")

    # Ceiling pendant: canopy under the ceiling, a vanilla-style chain right down to the shade (no gap), two-tier gold
    # shade, glowing bulb and the light inside the shade
    PD = tex("pendant")
    chain_planes = [
        {"from": [6.5, 10, 8], "to": [9.5, 15, 8], "rotation": {"angle": 45, "axis": "y", "origin": [8, 8, 8]}, "shade": False,
         "faces": {"north": {"texture": "#chain", "uv": [3, 0, 0, 5]}, "south": {"texture": "#chain", "uv": [0, 0, 3, 5]}}},
        {"from": [8, 10, 6.5], "to": [8, 15, 9.5], "rotation": {"angle": 45, "axis": "y", "origin": [8, 8, 8]}, "shade": False,
         "faces": {"west": {"texture": "#chain", "uv": [6, 0, 3, 5]}, "east": {"texture": "#chain", "uv": [3, 0, 6, 5]}}},
    ]
    for lit in (False, True):
        model("ceiling_pendant" + ("_lit" if lit else ""), [
            box([5, 15, 5], [11, 16, 11], "#pendant", uvs([0, 6, 6, 7], [0, 6, 6, 7], None, [0, 0, 6, 6])),
            *chain_planes,
            box([5, 8, 5], [11, 10, 11], "#pendant", uvs([6, 0, 12, 2], [6, 0, 12, 2], [0, 0, 6, 6])),
            box([3, 4, 3], [13, 8, 13], "#pendant", uvs([6, 2, 16, 6], [6, 2, 16, 6], [6, 6, 16, 16])),
            box([3, 4, 3], [13, 4, 13], "#glow", {"down": [3, 3, 13, 13]}, 15 if lit else 0),
            box([6, 3, 6], [10, 4, 10], "#glow", uvs([6, 7, 10, 8], [6, 7, 10, 8], None, [6, 6, 10, 10]), 15 if lit else 0),
        ], {"particle": PD, "pendant": PD, "chain": tex("chain"), "glow": tex("lamp_glow" if lit else "lamp_off")},
            extra={"ambientocclusion": False})
    facing_states("ceiling_pendant", lambda c: "ceiling_pendant" + ("_lit" if c["lit"] == "true" else ""), {"lit": ["false", "true"]})
    item_block("ceiling_pendant", "ceiling_pendant_lit")

    # Spotlight: the base only (yoke and head are drawn by SpotlightRenderer); the item shows the whole light
    M = tex("metal")
    model("spotlight", [el([3, 0, 3], [13, 2, 13], "#metal"), el([7, 2, 7], [9, 3, 9], "#metal")], {"particle": M, "metal": M})
    model("spotlight_hanging", [el([3, 14, 3], [13, 16, 13], "#metal"), el([7, 13, 7], [9, 14, 9], "#metal")], {"particle": M, "metal": M})
    model("spotlight_item", [
        el([3, 0, 3], [13, 2, 13], "#metal"),
        el([2, 3, 7], [3, 10, 9], "#metal"), el([13, 3, 7], [14, 10, 9], "#metal"), el([2, 2, 7], [14, 3, 9], "#metal"),
        el([4, 5, 3], [12, 13, 13], "#metal", faces={"north": "#lens"}),
    ], {"particle": M, "metal": M, "lens": tex("lamp_glow")})
    states = {}
    for hanging in ("false", "true"):
        for lit in ("false", "true"):
            states[f"hanging={hanging},lit={lit}"] = {"model": "craftmaster:block/spotlight" + ("_hanging" if hanging == "true" else "")}
    wj(os.path.join(A, "blockstates/spotlight.json"), {"variants": states})
    item_block("spotlight", "spotlight_item")

    # "EN DIRECT" sign: a 40 x 10 px light box against the wall (wider than the block)
    for lit in (False, True):
        t = "#box"
        model("on_air_sign" + ("_lit" if lit else ""), [{
            "from": [-12, 3, 13], "to": [28, 13, 16], **({"light_emission": 12} if lit else {}),
            "faces": {
                "north": {"texture": t, "uv": [0, 0, 10, 10]}, "south": {"texture": t, "uv": [10, 0, 16, 10]},
                "east": {"texture": t, "uv": [10, 0, 10.75, 10]}, "west": {"texture": t, "uv": [10, 0, 10.75, 10]},
                "up": {"texture": t, "uv": [0, 10, 10, 13]}, "down": {"texture": t, "uv": [0, 10, 10, 13]}}}],
            {"particle": tex("on_air_lit" if lit else "on_air"), "box": tex("on_air_lit" if lit else "on_air")},
            extra={"ambientocclusion": False, "display": {**WIDE_DISPLAY, "gui": gui_fit([{"from": [-12, 3, 13], "to": [28, 13, 16]}], 15, (0, 180, 0))}})
    facing_states("on_air_sign", lambda c: "on_air_sign" + ("_lit" if c["lit"] == "true" else ""),
                  {"lit": ["false", "true"], "powered": ["false", "true"]})
    item_block("on_air_sign", "on_air_sign_lit")

    # Giant timer: nothing but the panel drawn by TimerDisplayRenderer (the old wall bracket stuck out through the
    # panel's face and flickered); flat item icon
    model("timer_display", [], {"particle": M})
    facing_states("timer_display", lambda c: "timer_display")
    item_flat("timer_display")

    # Contestant desk (front at north = toward the audience), in Minecraft's own textures (LordFinn): spruce planks,
    # a red wool front panel, a thin gold-block moulding on the free edges of the top. Desks side by side (same
    # direction) join into one long desk: no side panel or moulding between them (left = west when facing north)
    SP, RW, GB = "minecraft:block/spruce_planks", "minecraft:block/red_wool", "minecraft:block/gold_block"
    for left in (False, True):
        for right in (False, True):
            sfx = joined_suffix(left, right)
            skip = tuple(f for f, j in (("west", left), ("east", right)) if j)
            els = [
                el([0, 13, 0], [16, 16, 16], "#wood", skip=skip),
                el([0, 0, 1], [16, 13, 4], "#wood", faces={"north": "#front"}, skip=skip),
                el([3, 6, 0], [13, 10, 1], "#plate"),
                # Moulding: front and back edges always, the ends where not joined (half a pixel proud)
                el([0, 16, 0], [16, 16.5, 1], "#gold"), el([0, 16, 15], [16, 16.5, 16], "#gold"),
            ]
            if not left:
                els += [el([0, 0, 4], [2, 13, 15], "#wood"), el([0, 16, 1], [1, 16.5, 15], "#gold")]
            if not right:
                els += [el([14, 0, 4], [16, 13, 15], "#wood"), el([15, 16, 1], [16, 16.5, 15], "#gold")]
            model("contestant_desk" + sfx, els, {"particle": SP, "wood": SP, "front": RW, "gold": GB,
                                                 "plate": tex("name_plate")},
                  extra={"ambientocclusion": False, "display": {"gui": gui_fit(els, 15)}})
    facing_states("contestant_desk", lambda c: "contestant_desk" + joined_suffix(c["left"] == "true", c["right"] == "true"),
                  {"left": ["false", "true"], "right": ["false", "true"]})
    item_block("contestant_desk")

    for pressed in (False, True):
        els = [el([4, 0, 4], [12, 1, 12], "#wood"), el([5, 1, 5], [11, 4, 11], "#dome")]
        els.append(el([7, 4, 7], [9, 4.5 if pressed else 5, 9], "#gold"))
        model("desk_bell" + ("_pressed" if pressed else ""), els, {"particle": tex("bell_dome"), "wood": W, "dome": tex("bell_dome"), "gold": GP},
              extra={"display": {"gui": gui_fit(els, 12)}})
    facing_states("desk_bell", lambda c: "desk_bell" + ("_pressed" if c["powered"] == "true" else ""), {"powered": ["false", "true"]})
    item_block("desk_bell")

    # Gong frame in Minecraft's dark oak: stripped log posts and beam (grain along the beam, rings at its ends), plank
    # feet
    beam_side = {"texture": "#log", "uv": [0, 0, 2, 16], "rotation": 90}
    model("gong", [
        el([0, 0, 5], [2, 1, 11], "#planks"), el([14, 0, 5], [16, 1, 11], "#planks"),
        el([0, 1, 7], [2, 14, 9], "#log", faces={"up": "#log_top", "down": "#log_top"}),
        el([14, 1, 7], [16, 14, 9], "#log", faces={"up": "#log_top", "down": "#log_top"}),
        {"from": [0, 14, 6], "to": [16, 16, 10], "faces": {
            "north": beam_side, "south": beam_side,
            "up": {"texture": "#log", "uv": [0, 0, 4, 16], "rotation": 90},
            "down": {"texture": "#log", "uv": [4, 0, 8, 16], "rotation": 90},
            "east": {"texture": "#log_top", "uv": [6, 7, 10, 9]}, "west": {"texture": "#log_top", "uv": [6, 7, 10, 9]}}},
    ], {"particle": "minecraft:block/stripped_dark_oak_log", "log": "minecraft:block/stripped_dark_oak_log",
        "log_top": "minecraft:block/stripped_dark_oak_log_top", "planks": "minecraft:block/dark_oak_planks"},
        extra={"ambientocclusion": False})
    facing_states("gong", lambda c: "gong", {"powered": ["false", "true"]})
    item_block("gong")

    # Prize table: tables side by side (same direction) join into one long table under one drape
    for left in (False, True):
        for right in (False, True):
            skip = tuple(f for f, j in (("west", left), ("east", right)) if j)
            model("prize_table" + joined_suffix(left, right), [
                el([0, 12, 0], [16, 16, 16], "#cloth", faces={"up": "#top"}, skip=skip),
                el([0 if left else 1, 0, 1], [16 if right else 15, 12, 15], "#folds", skip=("up",) + skip),
            ], {"particle": V, "cloth": tex("prize_cloth"), "top": VP, "folds": VF})
    cloth = Img(16, 16)
    # The drape's sides are 4 px high (rows 0..3): velvet, a gold band, then the gold fringe
    cloth.paint(0, 0, 16, 16, lambda x, y, w, h: VELVET[1] if y == 0 else VELVET[2] if y == 1 else GOLD[1] if y == 2
                else (GOLD[2] if x % 2 else GOLD[4]) if y == 3 else VELVET[2])
    cloth.save(g, "block/studio/prize_cloth")
    facing_states("prize_table", lambda c: "prize_table" + joined_suffix(c["left"] == "true", c["right"] == "true"),
                  {"left": ["false", "true"], "right": ["false", "true"]})
    item_block("prize_table")

    # Rope stanchion: brass foot, post and cap on one atlas (1 texel per pixel, light from the top left), no ambient
    # occlusion (it darkened the thin post unevenly)
    ST = tex("stanchion")
    model("stanchion", [
        box([5, 0, 5], [11, 1, 11], "#s", uvs([0, 6, 6, 7], [0, 6, 6, 7], [0, 0, 6, 6], [0, 0, 6, 6])),
        box([7, 1, 7], [9, 13, 9], "#s", uvs([6, 0, 8, 12], [6, 0, 8, 12])),
        box([6, 13, 6], [10, 15, 10], "#s", uvs([8, 4, 12, 6], [8, 4, 12, 6], [8, 0, 12, 4], [8, 0, 12, 4])),
    ], {"particle": ST, "s": ST}, extra={"ambientocclusion": False})
    wj(os.path.join(A, "blockstates/stanchion.json"), {"variants": {"": {"model": "craftmaster:block/stanchion"}}})
    item_block("stanchion")

    item_flat("studio_screen")

    # ================================================================== item icons (16 x 16)
    icon = Img(16, 16)
    icon.paint(1, 3, 15, 12, lambda x, y, w, h: (40, 40, 48) if x in (0, w - 1) or y in (0, h - 1) else None)
    tc_small = [(235, 235, 235), (240, 200, 60), (60, 200, 210), (80, 190, 70), (200, 70, 190), (210, 50, 50)]
    icon.paint(2, 4, 14, 11, lambda x, y, w, h: tc_small[x * 6 // w] if y < 5 else (16, 16, 20) if x % 2 else (240, 240, 240))
    icon.rect(6, 12, 10, 13, (40, 40, 48))
    icon.rect(4, 13, 12, 14, (28, 28, 34))
    icon.save(g, "item/studio_screen")

    ti = Img(16, 16)
    ti.paint(0, 4, 16, 12, lambda x, y, w, h: GOLD[0] if y == 0 or x == 0 else GOLD[3] if y == h - 1 or x == w - 1 else (18, 12, 12))
    seg = {"3": [(1, 0), (2, 0), (3, 1), (2, 2), (3, 3), (1, 4), (2, 4)], "0": [(1, 0), (2, 0), (0, 1), (3, 1), (0, 3), (3, 3), (1, 4), (2, 4), (0, 2), (3, 2)]}
    for i, d in enumerate("300"):
        ox = 2 + i * 4 + (1 if i else 0)
        for (x, y) in seg[d]:
            ti.set(ox + x - (1 if i else 0), 6 + y - 0, (255, 200, 60))
    ti.set(6, 7, (255, 200, 60)); ti.set(6, 9, (255, 200, 60))
    ti.save(g, "item/timer_display")

    # ================================================================== loot, tags, sounds, recipes
    blocks = ["throne", "floor_lamp", "desk_lamp", "ceiling_pendant", "spotlight", "on_air_sign",
              "timer_display", "contestant_desk", "desk_bell", "gong", "prize_table", "stanchion"]
    for b in blocks:
        loot(b)
    # Axe: the chairs (written again with them) and the wooden studio pieces
    wj(os.path.join(D, "minecraft/tags/block/mineable/axe.json"), {"replace": False, "values":
        [f"craftmaster:{w}_chair" for w in g.WOODS] + [f"craftmaster:{b}" for b in
                                                        ("throne", "contestant_desk", "gong", "prize_table")]})
    wj(os.path.join(D, "minecraft/tags/block/mineable/pickaxe.json"), {"replace": False, "values": [
        f"craftmaster:{b}" for b in ("floor_lamp", "desk_lamp", "ceiling_pendant", "spotlight", "on_air_sign", "timer_display",
                                     "desk_bell", "stanchion")]})

    # Sounds: vanilla sounds re-pitched, replaceable by a resource pack (same event names)
    g.SOUNDS.update({
        "studio.gong": {"subtitle": "subtitles.craftmaster.studio.gong", "sounds": [
            {"name": "minecraft:block/bell/bell_use01", "pitch": 0.5, "volume": 1.0},
            {"name": "minecraft:block/bell/bell_use02", "pitch": 0.5, "volume": 1.0}]},
        "studio.desk_bell": {"subtitle": "subtitles.craftmaster.studio.desk_bell", "sounds": [
            {"name": "minecraft:note/bell", "pitch": 1.6, "volume": 0.9}]},
    })

    shaped = g.shaped
    shaped("throne", ["G G", "GRG", "GBG"], {"G": "minecraft:gold_ingot", "R": "minecraft:red_wool", "B": "minecraft:gold_block"}, "throne", 1, "building")
    shaped("floor_lamp", ["W", "T", "G"], {"W": "minecraft:white_wool", "T": "minecraft:torch", "G": "minecraft:gold_ingot"}, "floor_lamp", 1, "building")
    shaped("desk_lamp", ["E", "T", "N"], {"E": "minecraft:green_stained_glass", "T": "minecraft:torch", "N": "minecraft:gold_nugget"}, "desk_lamp", 1, "building")
    shaped("ceiling_pendant", ["N", "G", "T"], {"N": "minecraft:gold_nugget", "G": "minecraft:gold_ingot", "T": "minecraft:torch"}, "ceiling_pendant", 1, "building")
    shaped("spotlight", [" I ", "IGI", "III"], {"I": "minecraft:iron_ingot", "G": "minecraft:glowstone"}, "spotlight", 1, "redstone")
    shaped("on_air_sign", ["PRP"], {"P": "minecraft:black_stained_glass_pane", "R": "minecraft:redstone_lamp"}, "on_air_sign", 1, "redstone")
    shaped("timer_display", ["GCG", "RBR"], {"G": "minecraft:gold_ingot", "C": "minecraft:clock", "R": "minecraft:redstone", "B": "minecraft:black_concrete"}, "timer_display", 1, "redstone")
    shaped("studio_screen", ["BBB", "BGB"], {"B": "minecraft:black_concrete", "G": "minecraft:glass_pane"}, "studio_screen", 1, "misc")
    shaped("contestant_desk", ["SSS", "RGR", "P P"], {"S": "minecraft:dark_oak_slab", "R": "minecraft:red_wool", "G": "minecraft:gold_ingot", "P": "minecraft:dark_oak_planks"}, "contestant_desk", 1, "building")
    shaped("desk_bell", ["N", "G", "S"], {"N": "minecraft:gold_nugget", "G": "minecraft:gold_ingot", "S": "#minecraft:wooden_slabs"}, "desk_bell", 1, "redstone")
    shaped("gong", ["SSS", "SGS", "S S"], {"S": "minecraft:stick", "G": "minecraft:gold_block"}, "gong", 1, "misc")
    shaped("prize_table", ["RRR", "PNP", "P P"], {"R": "minecraft:red_wool", "P": "#minecraft:planks", "N": "minecraft:gold_nugget"}, "prize_table", 1, "building")
    shaped("stanchion", ["N", "S", "G"], {"N": "minecraft:gold_nugget", "S": "minecraft:stick", "G": "minecraft:gold_ingot"}, "stanchion", 2, "building")

    # ================================================================== translations
    names = {
        "throne": ("Master's Throne", "Trône du maître du jeu"),
        "floor_lamp": ("Floor Lamp", "Lampadaire"),
        "desk_lamp": ("Desk Lamp", "Lampe de bureau"),
        "ceiling_pendant": ("Ceiling Pendant", "Suspension"),
        "spotlight": ("Spotlight", "Projecteur"),
        "on_air_sign": ("\"EN DIRECT\" Sign", "Enseigne « EN DIRECT »"),
        "timer_display": ("Giant Timer", "Chrono géant"),
        "contestant_desk": ("Contestant's Desk", "Pupitre de candidat"),
        "desk_bell": ("Counter Bell", "Sonnette de comptoir"),
        "gong": ("Gong", "Gong"),
        "prize_table": ("Prize Table", "Table des prix"),
        "stanchion": ("Rope Stanchion", "Poteau à cordon"),
    }
    for b, (e, f) in names.items():
        en[f"block.craftmaster.{b}"] = e
        fr[f"block.craftmaster.{b}"] = f
    en.update({
        "container.craftmaster.desk_drawer": "Desk Drawer",
        "container.craftmaster.prize_table": "Under the Drape",
        "item.craftmaster.studio_screen": "Studio Screen",
        "item.craftmaster.studio_screen.hint": "Use it on a wall; /image <URL> while looking at it",
        "entity.craftmaster.studio_screen": "Studio Screen",
        "subtitles.craftmaster.studio.gong": "Gong rings",
        "subtitles.craftmaster.studio.desk_bell": "Bell dings",
        "message.craftmaster.screen_wall": "A studio screen goes on a wall",
        "message.craftmaster.screen_saved": "Screen updated",
        "message.craftmaster.spotlight_aimed": "Spotlight aimed where you look",
        "message.craftmaster.timer_size": "Giant timer size: %s",
        "message.craftmaster.desk_named": "Desk plate: %s",
        "message.craftmaster.gong_no_round": "No round in progress",
        "message.craftmaster.bell_rung": "♪ %s rang the bell",
        "message.craftmaster.bell_rung_timer": "♪ %s rang the bell (%s left)",
        "screen.craftmaster.studio_screen": "Studio screen",
        "screen.craftmaster.studio_screen.url": "Image or video (URL)",
        "screen.craftmaster.studio_screen.url_hint": "A PNG, JPEG or GIF image, or with WaterMedia a video (YouTube, Twitch, MP4...). Empty: test card.",
        "screen.craftmaster.studio_screen.width": "Width",
        "screen.craftmaster.studio_screen.height": "Height",
        "screen.craftmaster.studio_screen.size": "Size (blocks)",
        "screen.craftmaster.studio_screen.fit": "Picture",
        "screen.craftmaster.studio_screen.fit_whole": "Whole image",
        "screen.craftmaster.studio_screen.fit_stretch": "Stretched",
        "screen.craftmaster.studio_screen.test_card": "Test card",
        "screen.craftmaster.studio_screen.hint": "Each player's game downloads the image",
        "screen.craftmaster.studio_screen.bad_size": "Size: 1 to %s blocks wide, 1 to %s high",
        "screen.craftmaster.studio_screen.loading": "Loading…",
        "screen.craftmaster.studio_screen.failed": "Image unavailable: %s",
        "screen.craftmaster.studio_screen.video_failed": "Video unavailable: %s",
        "screen.craftmaster.studio_screen.no_video": "Not an image: videos need the WaterMedia mod",
    })
    fr.update({
        "container.craftmaster.desk_drawer": "Tiroir du pupitre",
        "container.craftmaster.prize_table": "Sous le drapé",
        "item.craftmaster.studio_screen": "Écran du studio",
        "item.craftmaster.studio_screen.hint": "S'utilise sur un mur ; /image <URL> en le regardant",
        "entity.craftmaster.studio_screen": "Écran du studio",
        "subtitles.craftmaster.studio.gong": "Coup de gong",
        "subtitles.craftmaster.studio.desk_bell": "Sonnette",
        "message.craftmaster.screen_wall": "Un écran du studio se pose sur un mur",
        "message.craftmaster.screen_saved": "Écran mis à jour",
        "message.craftmaster.spotlight_aimed": "Projecteur orienté là où tu regardes",
        "message.craftmaster.timer_size": "Taille du chrono géant : %s",
        "message.craftmaster.desk_named": "Plaque du pupitre : %s",
        "message.craftmaster.gong_no_round": "Aucune manche en cours",
        "message.craftmaster.bell_rung": "♪ %s a sonné",
        "message.craftmaster.bell_rung_timer": "♪ %s a sonné (%s restantes)",
        "screen.craftmaster.studio_screen": "Écran du studio",
        "screen.craftmaster.studio_screen.url": "Image ou vidéo (adresse)",
        "screen.craftmaster.studio_screen.url_hint": "Une image PNG, JPEG ou GIF, ou avec WaterMedia une vidéo (YouTube, Twitch, MP4…). Vide : la mire.",
        "screen.craftmaster.studio_screen.width": "Largeur",
        "screen.craftmaster.studio_screen.height": "Hauteur",
        "screen.craftmaster.studio_screen.size": "Taille (blocs)",
        "screen.craftmaster.studio_screen.fit": "Image",
        "screen.craftmaster.studio_screen.fit_whole": "Image entière",
        "screen.craftmaster.studio_screen.fit_stretch": "Étirée",
        "screen.craftmaster.studio_screen.test_card": "Mire",
        "screen.craftmaster.studio_screen.hint": "Le jeu de chaque joueur télécharge l'image",
        "screen.craftmaster.studio_screen.bad_size": "Taille : de 1 à %s blocs de large, de 1 à %s de haut",
        "screen.craftmaster.studio_screen.loading": "Chargement…",
        "screen.craftmaster.studio_screen.failed": "Image indisponible : %s",
        "screen.craftmaster.studio_screen.video_failed": "Vidéo indisponible : %s",
        "screen.craftmaster.studio_screen.no_video": "Pas une image : les vidéos demandent le mod WaterMedia",
    })
