"""Studio set assets (chantier F, docs/wiki/Plateau.md): models, blockstates, item definitions, textures, recipes,
loot tables, sounds and translations of the throne, seats, lights, spotlight, giant timer, studio screen, desks,
prize table, gong, bell, podium, trophy, "EN DIRECT" sign and stanchions.

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


def gold_ornament(x, y, w, h):
    """Gold with a bevelled border and an inner fillet, a small diamond every 8 px: the throne's carved frame."""
    if x in (0, w - 1) or y in (0, h - 1):
        return bevel(GOLD, x, y, w, h)
    if x in (1, w - 2) or y in (1, h - 2):
        return GOLD[2]
    cx, cy = (x - 2) % 8, (y - 2) % 8
    d = abs(cx - 3.5) + abs(cy - 3.5)
    if d < 1.6:
        return GOLD[0]
    if d < 2.6:
        return GOLD[3] if cx > 3.5 or cy > 3.5 else GOLD[1]
    return GOLD[1] if (x + y) % 7 else GOLD[2]


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
    gold = Img(16, 16)
    gold.paint(0, 0, 16, 16, gold_ornament)
    gold.save(g, "block/studio/gold")

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

    # Throne back panel: tufted velvet framed by gold piping, the CM seal in the middle (12 x 16 used: x 2..14)
    back = Img(16, 16)
    back.paint(0, 0, 16, 16, lambda x, y, w, h: tufted(VELVET, x, y, w, h))
    for y in range(16):
        back.set(2, y, GOLD[2]); back.set(13, y, GOLD[3])
    for x in range(2, 14):
        back.set(x, 0, GOLD[1]); back.set(x, 15, GOLD[3])
    # The CM seal: a gold medallion (10 x 10), letters in dark velvet
    for yy in range(10):
        for xx in range(10):
            r = math.hypot(xx - 4.5, yy - 4.5)
            if r < 5.1:
                back.set(3 + xx, 3 + yy, GOLD[4] if r > 4.3 else GOLD[0] if xx + yy < 6 else GOLD[1] if xx + yy < 11 else GOLD[2])
    letters = ["###.#...#", "#...##.##", "#...#.#.#", "#...#...#", "###.#...#"]
    for yy, row in enumerate(letters):
        for xx, ch in enumerate(row):
            if ch == "#":
                back.set(3 + xx, 5 + yy, VELVET[4])
    back.save(g, "block/studio/throne_back")

    walnut = Img(16, 16)
    walnut.paint(0, 0, 16, 16, lambda x, y, w, h: wood_grain(WALNUT, x, y, w, h))
    walnut.save(g, "block/studio/walnut")

    # Desk top: walnut with a gold edge all round
    desk_top = Img(16, 16)
    desk_top.paint(0, 0, 16, 16, lambda x, y, w, h: GOLD[1] if x in (0, 15) or y in (0, 15) else wood_grain(WALNUT, x, y, w, h))
    desk_top.save(g, "block/studio/desk_top")

    # Desk front: velvet panel with gold trims top and bottom and gold side stiles
    def desk_front(x, y, w, h):
        if y in (3, 4) or y == 15:
            return GOLD[1] if y != 4 else GOLD[3]
        if x in (0, 15):
            return GOLD[2]
        if y < 3:
            return wood_grain(WALNUT, x, y, w, h)
        return velvet_folds(VELVET, x, y, w, h, 8)
    df = Img(16, 16)
    df.paint(0, 0, 16, 16, desk_front)
    df.save(g, "block/studio/desk_front")

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

        def shade(x, y, w, h, lit=lit):
            if y in (0, 15):
                return GOLD[1]
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

    green = Img(16, 16)
    green.paint(0, 0, 16, 16, lambda x, y, w, h: GREEN_GLASS[0] if y == 1 and 2 < x < 13 else GREEN_GLASS[1] if y < 4 else GREEN_GLASS[2] if y < 12 else GREEN_GLASS[3])
    green.save(g, "block/studio/green_glass")

    chain = Img(16, 16)
    for y in range(16):
        if y % 4 in (0, 1, 2):
            chain.set(7 if y % 8 < 4 else 8, y, GOLD[2] if y % 4 else GOLD[0])
            chain.set(8 if y % 8 < 4 else 7, y, GOLD[3])
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
        # Sides and top (40..64 x 0..16): dark lacquer with a gold line
        s.paint(40, 0, 64, 16, lambda x, y, w, h: frame[2] if y == 0 else (34, 20, 22))
        s.paint(0, 10, 40, 16, lambda x, y, w, h: frame[2] if y == 0 else (34, 20, 22))
        s.save(g, "block/studio/on_air" + ("_lit" if lit else ""))

    # Podium sides: white marble, band of the medal colour at the top, big numeral (bottom aligned to the height)
    # Bold numerals (2 px strokes, 6 x 8) for the 1st and 2nd steps, a small one (3 x 5) for the low 3rd step
    numerals = {
        1: ["..##..", ".###..", "####..", "..##..", "..##..", "..##..", "..##..", "######"],
        2: [".####.", "##..##", "....##", "...##.", "..##..", ".##...", "##....", "######"],
        3: ["###", "..#", ".##", "..#", "###"],
    }
    for rank, (pal, height) in {1: (GOLD, 16), 2: (SILVER, 12), 3: (BRONZE, 8)}.items():
        s = Img(16, 16, T)
        top = 16 - height
        band = 2 if height <= 8 else 3

        def side(x, y, w, h, top=top, pal=pal, band=band):
            if y < top:
                return None
            if y == top:
                return pal[0]
            if y < top + band:
                return pal[1] if y == top + 1 else pal[3]
            if y == 15:
                return MARBLE[3]
            vein = (x * 2 + y * 3) % 13 == 0
            return MARBLE[2] if vein else MARBLE[1] if x % 8 else MARBLE[0]
        s.paint(0, 0, 16, 16, side)
        glyph = numerals[rank]
        gw, gh = len(glyph[0]), len(glyph)
        ox = (16 - gw) // 2
        oy = top + band + max(0, (15 - (top + band) - gh) // 2)
        for gy, row in enumerate(glyph):
            for gx, px in enumerate(row):
                if px == "#":
                    s.set(ox + gx, oy + gy, pal[2] if gy < gh / 2 else pal[3])
        s.save(g, f"block/studio/podium_{rank}")
        topt = Img(16, 16)
        topt.paint(0, 0, 16, 16, lambda x, y, w, h, pal=pal: pal[1] if x in (0, 15) or y in (0, 15) else MARBLE[1] if (x + y) % 6 else MARBLE[0])
        topt.save(g, f"block/studio/podium_{rank}_top")

    # Trophy cup: polished gold, a vertical highlight, the CM letters engraved on the front
    cup = Img(16, 16)

    def cup_px(x, y, w, h):
        if y == 0:
            return GOLD[0]
        if x in (5, 6):
            return GOLD[0]
        if x >= 12:
            return GOLD[3]
        return GOLD[1] if x < 9 else GOLD[2]
    cup.paint(0, 0, 16, 16, cup_px)
    for (x, y) in [(6, 6), (6, 7), (6, 8), (7, 5), (7, 9), (9, 5), (9, 6), (9, 7), (9, 8), (9, 9), (10, 6), (11, 5), (11, 6), (11, 7), (11, 8), (11, 9)]:
        cup.set(x - 1, y, GOLD[4])
    cup.save(g, "block/studio/trophy_cup")

    # Gong frame wood (dark lacquered), with gold caps
    lacq = Img(16, 16)
    lacq.paint(0, 0, 16, 16, lambda x, y, w, h: (64, 24, 22) if x % 5 else (50, 18, 18))
    lacq.save(g, "block/studio/lacquer")

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

    rope = Img(16, 16)
    rope.paint(0, 6, 16, 8, lambda x, y, w, h: VELVET[1] if (x + y) % 3 else VELVET[3])
    rope.save(g, "entity/velvet_rope")

    # ================================================================== models and blockstates
    G, GP, V, VF, VP, W = tex("gold"), tex("gold_plain"), tex("velvet_tufted"), tex("velvet_folds"), tex("velvet"), tex("walnut")

    # Throne (facing north: the seated player looks north, backrest at the south)
    model("throne", [
        el([0, 0, 0], [16, 2, 16], "#gold"),
        el([1, 2, 1], [15, 7, 14], "#gold"),
        el([1, 7, 1], [15, 10, 13], "#velvet"),
        el([0, 2, 1], [2, 13, 13], "#gold", faces={"up": "#velvet"}),
        el([14, 2, 1], [16, 13, 13], "#gold", faces={"up": "#velvet"}),
        el([0, 2, 13], [16, 16, 16], "#gold"),
        el([0, 16, 13], [16, 30, 16], "#gold"),
        el([2, 11, 12], [14, 27, 13], "#back", skip=("south",)),
        el([5, 30, 13], [11, 32, 16], "#gold"),
    ], {"particle": G, "gold": G, "velvet": V, "back": tex("throne_back")})
    facing_states("throne", lambda c: "throne")
    item_block("throne")

    model("assistant_chair", [
        el([2, 0, 2], [4, 7, 4], "#gold"), el([12, 0, 2], [14, 7, 4], "#gold"),
        el([2, 0, 12], [4, 7, 14], "#gold"), el([12, 0, 12], [14, 7, 14], "#gold"),
        el([2, 7, 2], [14, 10, 14], "#velvet"),
        el([2, 10, 12], [14, 16, 14], "#velvet"), el([2, 16, 12], [14, 21, 14], "#velvet"),
        el([1, 21, 11], [15, 23, 15], "#gold"),
    ], {"particle": V, "gold": GP, "velvet": V})
    facing_states("assistant_chair", lambda c: "assistant_chair")
    item_block("assistant_chair")

    # Sofa: armrests only on the sides that are not joined (left = west when facing north)
    for left in ("false", "true"):
        for right in ("false", "true"):
            els = [
                el([1, 0, 2], [3, 1, 4], "#gold"), el([13, 0, 2], [15, 1, 4], "#gold"),
                el([1, 0, 12], [3, 1, 14], "#gold"), el([13, 0, 12], [15, 1, 14], "#gold"),
                el([0, 1, 1], [16, 7, 15], "#folds"),
                el([0, 7, 1], [16, 9, 12], "#velvet"),
                el([0, 7, 12], [16, 16, 15], "#velvet"), el([0, 16, 12], [16, 19, 15], "#velvet"),
            ]
            if left == "false":
                els.append(el([0, 7, 0], [3, 13, 12], "#velvet", faces={"up": "#plain"}))
            if right == "false":
                els.append(el([13, 7, 0], [16, 13, 12], "#velvet", faces={"up": "#plain"}))
            model(f"sofa_{'l' if left == 'true' else ''}{'r' if right == 'true' else ''}".rstrip("_") or "sofa", els,
                  {"particle": V, "gold": GP, "velvet": V, "folds": VF, "plain": VP})
    sofa_name = lambda c: "sofa" + ("_" + ("l" if c["left"] == "true" else "") + ("r" if c["right"] == "true" else "") if "true" in (c["left"], c["right"]) else "")
    facing_states("sofa", sofa_name, {"left": ["false", "true"], "right": ["false", "true"]})
    item_block("sofa")

    # Floor lamp (2 blocks high in one block: the shade is above)
    for lit in (False, True):
        e = 15 if lit else 0
        model("floor_lamp" + ("_lit" if lit else ""), [
            el([5, 0, 5], [11, 1, 11], "#brass"),
            el([7, 1, 7], [9, 16, 9], "#brass"), el([7, 16, 7], [9, 21, 9], "#brass"),
            el([3, 21, 3], [13, 31, 13], "#shade", faces={"down": "#glow", "up": "#glow"}, emission=e),
        ], {"particle": tex("brass"), "brass": tex("brass"), "shade": tex("lampshade_lit" if lit else "lampshade"),
            "glow": tex("lamp_glow" if lit else "lamp_off")})
    facing_states("floor_lamp", lambda c: "floor_lamp" + ("_lit" if c["lit"] == "true" else ""), {"lit": ["false", "true"]})
    item_block("floor_lamp", "floor_lamp_lit")

    for lit in (False, True):
        model("desk_lamp" + ("_lit" if lit else ""), [
            el([4, 0, 5], [12, 1, 11], "#brass"),
            el([7, 1, 7], [9, 6, 9], "#brass"),
            el([3, 6, 5], [13, 9, 11], "#glass", faces={"down": "#glow"}, emission=12 if lit else 0),
            el([7, 9, 7], [9, 10, 9], "#brass"),
        ], {"particle": tex("green_glass"), "brass": tex("brass"), "glass": tex("green_glass"),
            "glow": tex("lamp_glow" if lit else "lamp_off")})
    facing_states("desk_lamp", lambda c: "desk_lamp" + ("_lit" if c["lit"] == "true" else ""), {"lit": ["false", "true"]})
    item_block("desk_lamp", "desk_lamp_lit")

    chain_planes = [
        {"from": [8, 9, 6.5], "to": [8, 15, 9.5], "rotation": {"angle": 45, "axis": "y", "origin": [8, 8, 8]}, "shade": False,
         "faces": {"east": {"texture": "#chain", "uv": [6.5, 1, 9.5, 7]}, "west": {"texture": "#chain", "uv": [6.5, 1, 9.5, 7]}}},
        {"from": [6.5, 9, 8], "to": [9.5, 15, 8], "rotation": {"angle": 45, "axis": "y", "origin": [8, 8, 8]}, "shade": False,
         "faces": {"north": {"texture": "#chain", "uv": [6.5, 1, 9.5, 7]}, "south": {"texture": "#chain", "uv": [6.5, 1, 9.5, 7]}}},
    ]
    for lit in (False, True):
        model("ceiling_pendant" + ("_lit" if lit else ""), [
            el([5, 15, 5], [11, 16, 11], "#gold"),
            *chain_planes,
            el([3, 3, 3], [13, 9, 13], "#gold", faces={"down": "#glow"}),
            el([6, 1, 6], [10, 3, 10], "#glow", emission=15 if lit else 0),
        ], {"particle": GP, "gold": GP, "chain": tex("chain"), "glow": tex("lamp_glow" if lit else "lamp_off")})
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
                "up": {"texture": t, "uv": [0, 10, 10, 10.75]}, "down": {"texture": t, "uv": [0, 10, 10, 10.75]}}}],
            {"particle": tex("on_air_lit" if lit else "on_air"), "box": tex("on_air_lit" if lit else "on_air")})
    facing_states("on_air_sign", lambda c: "on_air_sign" + ("_lit" if c["lit"] == "true" else ""),
                  {"lit": ["false", "true"], "powered": ["false", "true"]})
    item_block("on_air_sign", "on_air_sign_lit")

    # Giant timer: a wall bracket (the panel is drawn by TimerDisplayRenderer); flat item icon
    model("timer_display", [el([4, 0, 15], [12, 6, 16], "#metal"), el([6, 1, 13], [10, 4, 15], "#metal")], {"particle": M, "metal": M})
    facing_states("timer_display", lambda c: "timer_display")
    item_flat("timer_display")

    # Contestant desk (front at north = toward the audience)
    model("contestant_desk", [
        el([0, 13, 0], [16, 16, 16], "#top", faces={"north": "#wood", "south": "#wood", "east": "#wood", "west": "#wood"}),
        el([0, 0, 1], [16, 13, 4], "#front", faces={"up": "#wood", "down": "#wood", "east": "#wood", "west": "#wood", "south": "#wood"}),
        el([0, 0, 4], [2, 13, 15], "#wood"), el([14, 0, 4], [16, 13, 15], "#wood"),
        el([3, 6, 0], [13, 10, 1], "#plate"),
    ], {"particle": W, "top": tex("desk_top"), "wood": W, "front": tex("desk_front"), "plate": tex("name_plate")})
    facing_states("contestant_desk", lambda c: "contestant_desk")
    item_block("contestant_desk")

    for pressed in (False, True):
        els = [el([4, 0, 4], [12, 1, 12], "#wood"), el([5, 1, 5], [11, 4, 11], "#dome")]
        els.append(el([7, 4, 7], [9, 4.5 if pressed else 5, 9], "#gold"))
        model("desk_bell" + ("_pressed" if pressed else ""), els, {"particle": tex("bell_dome"), "wood": W, "dome": tex("bell_dome"), "gold": GP})
    facing_states("desk_bell", lambda c: "desk_bell" + ("_pressed" if c["powered"] == "true" else ""), {"powered": ["false", "true"]})
    item_block("desk_bell")

    L = tex("lacquer")
    model("gong", [
        el([0, 0, 5], [2, 1, 11], "#wood"), el([14, 0, 5], [16, 1, 11], "#wood"),
        el([0, 1, 7], [2, 14, 9], "#wood"), el([14, 1, 7], [16, 14, 9], "#wood"),
        el([0, 14, 6], [16, 16, 10], "#wood", faces={"east": "#gold", "west": "#gold"}),
    ], {"particle": L, "wood": L, "gold": GP})
    facing_states("gong", lambda c: "gong", {"powered": ["false", "true"]})
    item_block("gong")

    model("prize_table", [
        el([0, 12, 0], [16, 16, 16], "#cloth", faces={"up": "#top"}),
        el([1, 0, 1], [15, 12, 15], "#folds", skip=("up",)),
    ], {"particle": V, "cloth": tex("prize_cloth"), "top": VP, "folds": VF})
    cloth = Img(16, 16)
    cloth.paint(0, 0, 16, 16, lambda x, y, w, h: VELVET[2] if y < 12 else GOLD[1] if y == 12 else (GOLD[2] if x % 2 else None) if y < 15 else (GOLD[3] if x % 2 else None))
    cloth.save(g, "block/studio/prize_cloth")
    facing_states("prize_table", lambda c: "prize_table")
    item_block("prize_table")

    for rank, name, height in ((1, "podium_first", 16), (2, "podium_second", 12), (3, "podium_third", 8)):
        side = tex(f"podium_{rank}")
        model(name, [el([0, 0, 0], [16, height, 16], "#side", faces={"up": "#top", "down": "#bottom"})],
              {"particle": side, "side": side, "top": tex(f"podium_{rank}_top"), "bottom": tex(f"podium_{rank}_top")})
        facing_states(name, lambda c, name=name: name)
        item_block(name)

    model("trophy", [
        el([5, 0, 5], [11, 2, 11], "#wood"),
        el([7, 2, 7], [9, 5, 9], "#gold"), el([6, 5, 6], [10, 6, 10], "#gold"),
        el([4, 6, 4], [12, 13, 12], "#cup", faces={"up": "#gold", "down": "#gold"}),
        el([2, 8, 7], [4, 12, 9], "#gold"), el([12, 8, 7], [14, 12, 9], "#gold"),
    ], {"particle": tex("trophy_cup"), "wood": W, "gold": GP, "cup": tex("trophy_cup")})
    facing_states("trophy", lambda c: "trophy")
    item_block("trophy")

    model("stanchion", [
        el([5, 0, 5], [11, 1, 11], "#brass"), el([7, 1, 7], [9, 13, 9], "#brass"), el([6, 13, 6], [10, 15, 10], "#gold"),
    ], {"particle": tex("brass"), "brass": tex("brass"), "gold": GP})
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
    blocks = ["throne", "assistant_chair", "sofa", "floor_lamp", "desk_lamp", "ceiling_pendant", "spotlight", "on_air_sign",
              "timer_display", "contestant_desk", "desk_bell", "gong", "prize_table", "podium_first", "podium_second",
              "podium_third", "trophy", "stanchion"]
    for b in blocks:
        loot(b)
    # Axe: the chairs (written again with them) and the wooden studio pieces
    wj(os.path.join(D, "minecraft/tags/block/mineable/axe.json"), {"replace": False, "values":
        [f"craftmaster:{w}_chair" for w in g.WOODS] + [f"craftmaster:{b}" for b in
                                                        ("throne", "assistant_chair", "sofa", "contestant_desk", "gong", "prize_table")]})
    wj(os.path.join(D, "minecraft/tags/block/mineable/pickaxe.json"), {"replace": False, "values": [
        f"craftmaster:{b}" for b in ("floor_lamp", "desk_lamp", "ceiling_pendant", "spotlight", "on_air_sign", "timer_display",
                                     "desk_bell", "podium_first", "podium_second", "podium_third", "trophy", "stanchion")]})

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
    shaped("assistant_chair", ["R  ", "RRR", "N N"], {"R": "minecraft:red_wool", "N": "minecraft:gold_nugget"}, "assistant_chair", 1, "building")
    shaped("sofa", ["R  ", "RRR", "PPP"], {"R": "minecraft:red_wool", "P": "#minecraft:planks"}, "sofa", 3, "building")
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
    shaped("podium_first", ["GGG", "QQQ", "QQQ"], {"G": "minecraft:gold_ingot", "Q": "minecraft:quartz_block"}, "podium_first", 1, "building")
    shaped("podium_second", ["III", "QQQ"], {"I": "minecraft:iron_ingot", "Q": "minecraft:quartz_block"}, "podium_second", 1, "building")
    shaped("podium_third", ["CCC", "QQQ"], {"C": "minecraft:copper_ingot", "Q": "minecraft:quartz_slab"}, "podium_third", 1, "building")
    shaped("trophy", ["G G", "GGG", " S "], {"G": "minecraft:gold_ingot", "S": "minecraft:dark_oak_slab"}, "trophy", 1, "misc")
    shaped("stanchion", ["N", "S", "G"], {"N": "minecraft:gold_nugget", "S": "minecraft:stick", "G": "minecraft:gold_ingot"}, "stanchion", 2, "building")

    # ================================================================== translations
    names = {
        "throne": ("Master's Throne", "Trône du maître du jeu"),
        "assistant_chair": ("Assistant's Armchair", "Fauteuil de l'assistant"),
        "sofa": ("Velvet Sofa", "Canapé de velours"),
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
        "podium_first": ("Podium (1st)", "Podium (1er)"),
        "podium_second": ("Podium (2nd)", "Podium (2e)"),
        "podium_third": ("Podium (3rd)", "Podium (3e)"),
        "trophy": ("CraftMaster Trophy", "Trophée CraftMaster"),
        "stanchion": ("Rope Stanchion", "Poteau à cordon"),
    }
    for b, (e, f) in names.items():
        en[f"block.craftmaster.{b}"] = e
        fr[f"block.craftmaster.{b}"] = f
    en.update({
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
        "message.craftmaster.bell_rung": "🔔 %s rang the bell",
        "message.craftmaster.bell_rung_timer": "🔔 %s rang the bell (%s left)",
        "screen.craftmaster.studio_screen": "Studio screen",
        "screen.craftmaster.studio_screen.url": "Image (URL)",
        "screen.craftmaster.studio_screen.url_hint": "Address of a PNG, JPEG or GIF image (http:// or https://). Empty: test card.",
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
    })
    fr.update({
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
        "message.craftmaster.bell_rung": "🔔 %s a sonné",
        "message.craftmaster.bell_rung_timer": "🔔 %s a sonné (%s restantes)",
        "screen.craftmaster.studio_screen": "Écran du studio",
        "screen.craftmaster.studio_screen.url": "Image (adresse)",
        "screen.craftmaster.studio_screen.url_hint": "Adresse d'une image PNG, JPEG ou GIF (http:// ou https://). Vide : la mire.",
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
    })
