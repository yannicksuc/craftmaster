#!/usr/bin/env python3
"""Assets of the task toys (src/main/java/.../toy): rubber duck, bouncy ball, bean bag, fragile egg, golf ball and
club, golf hole, balloon, target containers (bucket, bin, mug), measuring glass and tank.

Runs on its own and only writes its own files:  python scripts/gen_toys.py
  - models, item definitions, blockstates, loot tables, recipes, textures (pure Python PNG);
  - sounds (numpy + ffmpeg, skipped if missing: the .ogg files are committed);
  - its translations and sound events are ADDED to lang/*.json and sounds.json without touching the other keys.
gen_assets.py can also call generate(...) so a full regeneration keeps them (same contract as gen_trials.py).

Style (skill minecraft-modeling): few cubes, detail in the 16 px texture, light from the top left, outlines in
the darkest tone of the material (never black), tinted parts painted in greys (the dye colour multiplies them).
"""
import json
import math
import os
import shutil
import struct
import subprocess
import sys
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
A = os.path.join(ROOT, "assets", "craftmaster")
D = os.path.join(ROOT, "data", "craftmaster")

DYES = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
        "blue", "brown", "green", "red", "black"]
FR_M = ["blanc", "orange", "magenta", "bleu clair", "jaune", "vert clair", "rose", "gris", "gris clair", "cyan", "violet",
        "bleu", "marron", "vert", "rouge", "noir"]
FR_F = ["blanche", "orange", "magenta", "bleu clair", "jaune", "vert clair", "rose", "grise", "gris clair", "cyan",
        "violette", "bleue", "marron", "verte", "rouge", "noire"]
EN = ["White", "Orange", "Magenta", "Light Blue", "Yellow", "Lime", "Pink", "Gray", "Light Gray", "Cyan", "Purple", "Blue",
      "Brown", "Green", "Red", "Black"]


# ---------------------------------------------------------------- files

def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def write_png(path, img):
    h, w = len(img), len(img[0])
    raw = b"".join(b"\x00" + b"".join(struct.pack("4B", *p) for p in row) for row in img)

    def chunk(t, data):
        return struct.pack(">I", len(data)) + t + data + struct.pack(">I", zlib.crc32(t + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


# ---------------------------------------------------------------- painting

T = (0, 0, 0, 0)


def img(w, h, c=T):
    return [[c for _ in range(w)] for _ in range(h)]


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def ramp(pal, t):
    """pal: dark → light; t in [0, 1] picks a colour (no blending: pixel art)."""
    i = max(0, min(len(pal) - 1, int(t * len(pal))))
    return rgba(pal[i])


def paint(im, x0, y0, w, h, fn):
    """Paints a w×h face at (x0, y0); fn(x, y, w, h) gives the colour (or None to leave it)."""
    for y in range(h):
        for x in range(w):
            c = fn(x, y, w, h)
            if c is not None:
                im[y0 + y][x0 + x] = c


def hash2(x, y, s=0):
    v = (x * 73856093) ^ (y * 19349663) ^ (s * 83492791)
    v ^= v >> 13
    v = (v * 0x5DEECE66D) & 0xFFFFFFFF
    return ((v >> 8) & 0xFFFF) / 65536.0


# Greys for tinted parts (the dye multiplies them): light top, dark bottom, outline
TINT = [(92, 92, 96), (138, 138, 142), (178, 178, 182), (212, 212, 214), (236, 236, 236), (255, 255, 255)]
ORANGE = [(150, 62, 10), (196, 92, 18), (232, 128, 30), (250, 166, 58)]
EYE = (34, 30, 38)
CREAM = [(150, 128, 96), (196, 176, 140), (222, 206, 172), (238, 226, 198), (250, 244, 228)]
WHITE = [(150, 152, 156), (190, 192, 196), (218, 220, 222), (236, 237, 238), (250, 250, 250)]
STEEL = [(70, 74, 82), (104, 110, 120), (138, 144, 154), (172, 178, 186), (206, 210, 216), (234, 236, 240)]
GREEN_BIN = [(26, 52, 34), (36, 72, 46), (48, 92, 58), (62, 112, 72), (82, 134, 90)]
FELT = [(40, 110, 46), (52, 134, 56), (66, 156, 68), (84, 176, 82), (104, 194, 98)]
SOIL = [(62, 42, 28), (80, 56, 36), (98, 70, 46), (116, 84, 56)]
CERAMIC = [(170, 166, 160), (204, 200, 194), (226, 224, 220), (242, 240, 236), (252, 252, 250)]
RED = [(110, 20, 22), (150, 30, 30), (186, 44, 40), (214, 68, 58)]
GRIP = [(26, 24, 28), (40, 38, 44), (56, 54, 60)]


def round_face(pal, light=1.0, salt=0, corner=True):
    """A face of a round object drawn on a square: radial light from the top left, dark rim, corners darker."""
    def fn(x, y, w, h):
        cx, cy = (x + 0.5) / w - 0.38, (y + 0.5) / h - 0.36
        d = math.sqrt(cx * cx + cy * cy)
        t = (1.0 - d * 1.15) * light
        edge = min(x, y, w - 1 - x, h - 1 - y)
        if corner and (x in (0, w - 1)) and (y in (0, h - 1)):
            t -= 0.35
        elif edge == 0:
            t -= 0.12
        return ramp(pal, max(0.0, min(0.999, t)))
    return fn


def vertical(pal, top=0.95, bottom=0.35, rim=True):
    """Light at the top, dark at the bottom, in short steps; a lighter rim row on top."""
    def fn(x, y, w, h):
        t = top - (top - bottom) * (y / max(1, h - 1))
        if rim and y == 0:
            t = min(0.999, t + 0.1)
        return ramp(pal, t)
    return fn


# ---------------------------------------------------------------- model helpers

def face(tex, uv, tint=False, cull=None):
    f = {"texture": tex, "uv": uv}
    if tint:
        f["tintindex"] = 0
    if cull:
        f["cullface"] = cull
    return f


def box(frm, to, faces, rot=None):
    e = {"from": frm, "to": to, "faces": faces}
    if rot:
        e["rotation"] = rot
    return e


def uv(x, y, w, h, s=1.0):
    """Texel rectangle → model uv (s = 16 / texture size)."""
    return [x * s, y * s, (x + w) * s, (y + h) * s]


def display(gui=1.0, ground=0.5, hand=0.6, gui_y=0.0, fixed=None):
    """Display transforms for a small object drawn by a block-style model."""
    return {
        "gui": {"rotation": [30, 225, 0], "translation": [0, gui_y, 0], "scale": [gui, gui, gui]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [ground, ground, ground]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [fixed or gui * 0.8] * 3},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 0], "scale": [1.0, 1.0, 1.0]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [hand, hand, hand]},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [hand, hand, hand]},
        "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [0, 3, 0], "scale": [hand, hand, hand]},
        "firstperson_lefthand": {"rotation": [0, 135, 0], "translation": [0, 3, 0], "scale": [hand, hand, hand]},
    }


def block_display():
    return {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    }


def cuboid(frm, to, tex, rects, s, tint=False, skip=()):
    """rects: {dir: (x, y, w, h)} in texels."""
    return box(frm, to, {d: face(tex, uv(*r, s), tint) for d, r in rects.items() if d not in skip})


def model(name, obj, kind="item"):
    write_json(os.path.join(A, f"models/{kind}/{name}.json"), obj)


def item_def(name, mdl, tint_default=None):
    m = {"type": "minecraft:model", "model": mdl}
    if tint_default is not None:
        m["tints"] = [{"type": "minecraft:dye", "default": tint_default}]
    write_json(os.path.join(A, f"items/{name}.json"), {"model": m})


def signed(rgb):
    return rgb - (1 << 32) if rgb & 0x80000000 else rgb


def argb(rgb):
    return signed(0xFF000000 | rgb)


# ================================================================ toys

DYE_RGB = [0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52, 0x9D9D97, 0x169C9C,
           0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21]


def hue_ramp(rgb, n=7, base=4):
    """A pixel-art ramp of n colours around a dye colour (index base = the dye itself). Shading is not just
    darker: shadows turn the hue towards red / purple and get more saturated, lights turn it towards yellow and
    get paler, the way painted plastic catches the light."""
    import colorsys
    r, g, b = ((rgb >> 16) & 255) / 255, ((rgb >> 8) & 255) / 255, (rgb & 255) / 255
    h, sat, v = colorsys.rgb_to_hsv(r, g, b)
    grey = sat < 0.18
    if not grey:
        # Bright toy plastic: lift dull dyes a little
        sat, v = min(1, sat * 1.12 + 0.05), min(1, max(v, 0.62) * 1.08)
    out = []
    for i in range(n):
        k = i - base  # < 0 shadows, > 0 lights
        hh, ss, vv = h, sat, v
        if not grey:
            # Shadows towards red-orange for warm hues, towards purple for cool ones; lights towards yellow
            shadow_hue = 0.0 if (h < 0.17 or h > 0.83) else 0.75
            light_hue = 0.16
            if k < 0:
                if abs(shadow_hue - h) < 0.5:
                    hh = h + (shadow_hue - h) * min(0.2, -k * 0.05)
                ss = min(1, sat - k * 0.03)
                vv = v * (1 + k * 0.07)
            elif k > 0:
                if abs(light_hue - h) < 0.5:
                    hh = h + (light_hue - h) * k * 0.07
                ss = max(0, sat - k * 0.12)
                vv = min(1, v + k * (1 - v) * 0.35 + k * 0.02)
        else:
            vv = max(0.08, min(1, v + k * 0.06 if v < 0.5 else v * (1 + k * 0.045)))
            hh, ss = 0.62, (0.06 if k < 0 else 0.03)  # a touch of cold blue in the grey shadows
        cr, cg, cb = colorsys.hsv_to_rgb(hh % 1, max(0, min(1, ss)), max(0, min(1, vv)))
        out.append((round(cr * 255), round(cg * 255), round(cb * 255)))
    return out


def rubber_duck():
    """Body 6x4x8, head 4x4x4, beak 2x2x2: three cubes, one model. The shading is painted once as a map of ramp
    levels (0 = deepest shadow, 4 = the dye colour, 6 = highlight) and written in each of the 16 dye colours with a
    hue-shifted ramp; the item model picks the texture from the duck's dye colour (minecraft:dyed_color)."""
    L = [[None] * 32 for _ in range(32)]   # a ramp level, or a fixed colour (eye, beak)
    EYE_B, EYE_W = (24, 20, 28), (250, 250, 246)
    BEAK = [(176, 64, 14), (214, 96, 20), (240, 132, 32), (252, 170, 64), (255, 204, 112)]

    def put(x0, y0, w, h, fn):
        for y in range(h):
            for x in range(w):
                L[y0 + y][x0 + x] = fn(x, y, w, h)

    def side(levels, salt, dark_edges=True):
        """Rows light to dark broken by short strokes; the vertical edges a step down (the surface turns away)."""
        def fn(x, y, w, h):
            lv = levels[min(y, len(levels) - 1)]
            if y > 0 and lv < levels[min(y - 1, len(levels) - 1)] and hash2(x // 3, y, salt) > 0.55:
                lv += 1
            # The vertical edges turn away, from the second row down: the top row meets the top face at its level
            if dark_edges and (x == 0 or x == w - 1) and y > 0:
                lv -= 1
            return lv
        return fn

    def top(w, h, shade_rows=(), shine=(), front=5):
        """Top face: lit evenly up to its border, so each edge has the same value as the side face's top row (5;
        the front row takes the front face's top row level). The head's shade is inside, away from the edges."""
        def fn(x, y, ww, hh):
            lv = front if y == 0 else 5
            if y in shade_rows and 0 < x < w - 1:
                lv = 4
            if (x, y) in shine:
                lv = 6
            return lv
        return fn

    # Body (x 5..11, y 0..4, z 4..12): the head covers its top rows 0-2, row 3 is in its shade
    put(0, 0, 6, 8, top(6, 8, shade_rows=(3,), shine={(1, 5), (2, 5), (1, 6)}, front=4))
    put(6, 0, 6, 8, lambda x, y, w, h: 2)

    def body_side(flip, salt):
        base = side([5, 4, 4, 3], salt)

        def fn(x, y, w, h):
            xx = w - 1 - x if flip else x  # 0 = front
            if (y == 1 and 2 <= xx <= 5) or (y == 0 and xx == 6):
                return 6 if (xx, y) == (2, 1) else 5      # the wing, catching the light
            if (y == 2 and 2 <= xx <= 5) or (y == 1 and xx == 6):
                return 3                                  # its seam
            return base(x, y, w, h)
        return fn
    put(12, 0, 8, 4, body_side(True, 11))
    put(12, 4, 8, 4, body_side(False, 12))
    put(20, 0, 6, 4, side([4, 4, 3, 3], 13))   # chest, under the head
    put(20, 4, 6, 4, side([5, 5, 4, 3], 14))   # tail
    # Head (x 6..10, y 4..8, z 3..7)
    put(0, 8, 4, 4, top(4, 4, shine={(1, 1), (2, 1)}))
    put(4, 8, 4, 4, lambda x, y, w, h: 3)

    def head_side(flip, salt):
        base = side([5, 5, 4, 3], salt)

        def fn(x, y, w, h):
            xx = w - 1 - x if flip else x  # 0 = front
            if 1 <= y <= 2 and xx == 1:
                return EYE_B                              # pupil, to the front
            if 1 <= y <= 2 and xx == 2:
                return EYE_W if y == 1 else EYE_B         # white of the eye
            return base(x, y, w, h)
        return fn
    put(8, 8, 4, 4, head_side(True, 21))
    put(12, 8, 4, 4, head_side(False, 22))
    put(16, 8, 4, 4, side([5, 5, 5, 4], 23))   # face
    put(20, 8, 4, 4, side([5, 4, 4, 3], 24))   # back of the head
    # Beak: its own orange ramp, light on top, dark underneath
    put(0, 12, 2, 2, lambda x, y, w, h: BEAK[4] if (x, y) == (0, 0) else BEAK[3])
    put(2, 12, 2, 2, lambda x, y, w, h: BEAK[0])
    put(4, 12, 2, 2, lambda x, y, w, h: BEAK[3] if y == 0 else BEAK[1])

    t, s = "#duck", 0.5
    els = [
        box([5, 0, 4], [11, 4, 12], {
            "up": face(t, uv(0, 0, 6, 8, s)), "down": face(t, uv(6, 0, 6, 8, s)),
            "east": face(t, uv(12, 0, 8, 4, s)), "west": face(t, uv(12, 4, 8, 4, s)),
            "north": face(t, uv(20, 0, 6, 4, s)), "south": face(t, uv(20, 4, 6, 4, s))}),
        box([6, 4, 3], [10, 8, 7], {
            "up": face(t, uv(0, 8, 4, 4, s)), "down": face(t, uv(4, 8, 4, 4, s)),
            "east": face(t, uv(8, 8, 4, 4, s)), "west": face(t, uv(12, 8, 4, 4, s)),
            "north": face(t, uv(16, 8, 4, 4, s)), "south": face(t, uv(20, 8, 4, 4, s))}),
        box([7, 4, 1], [9, 6, 3], {
            "up": face(t, uv(0, 12, 2, 2, s)), "down": face(t, uv(2, 12, 2, 2, s)),
            "east": face(t, uv(4, 12, 2, 2, s)), "west": face(t, uv(4, 12, 2, 2, s)),
            "north": face(t, uv(0, 12, 2, 2, s))}),
    ]
    model("toy/rubber_duck", {"textures": {"duck": "craftmaster:item/toy/rubber_duck_yellow",
                                           "particle": "craftmaster:item/toy/rubber_duck_yellow"},
                              "elements": els, "display": display(gui=1.6, ground=0.6, hand=0.75, gui_y=1)})
    old = os.path.join(A, "textures/item/toy/rubber_duck.png")
    if os.path.exists(old):
        os.remove(old)
    cases = []
    for i, dye in enumerate(DYES):
        ramp_ = hue_ramp(DYE_RGB[i])
        im = img(32, 32)
        for y in range(32):
            for x in range(32):
                v = L[y][x]
                if v is None:
                    continue
                im[y][x] = rgba(v) if isinstance(v, tuple) else rgba(ramp_[max(0, min(6, v))])
        write_png(os.path.join(A, f"textures/item/toy/rubber_duck_{dye}.png"), im)
        name = "craftmaster:item/toy/rubber_duck" + ("" if dye == "yellow" else f"_{dye}")
        if dye != "yellow":
            model(f"toy/rubber_duck_{dye}", {"parent": "craftmaster:item/toy/rubber_duck",
                                              "textures": {"duck": f"craftmaster:item/toy/rubber_duck_{dye}",
                                                           "particle": f"craftmaster:item/toy/rubber_duck_{dye}"}})
        cases.append({"when": signed(DYE_RGB[i]), "model": {"type": "minecraft:model", "model": name}})
    # Undyed = yellow; any other colour is snapped to the nearest dye by the game (ToyItem)
    write_json(os.path.join(A, "items/rubber_duck.json"), {"model": {
        "type": "minecraft:select", "property": "minecraft:component", "component": "minecraft:dyed_color",
        "cases": cases, "fallback": {"type": "minecraft:model", "model": "craftmaster:item/toy/rubber_duck"}}})


def small_cube(name, frm, to, side, top, bottom, tint_default=None, disp=None):
    """A small round object as one cube: a 16×16 texture, side / top / bottom tiles."""
    w, h, d = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
    im = img(16, 16)
    paint(im, 0, 0, w, h, side)
    paint(im, 0, 8, w, d, top)
    paint(im, 8, 8, w, d, bottom)
    write_png(os.path.join(A, f"textures/item/toy/{name}.png"), im)
    t = "#tex"
    tint = tint_default is not None
    els = [box(frm, to, {
        "up": face(t, uv(0, 8, w, d), tint), "down": face(t, uv(8, 8, w, d), tint),
        "north": face(t, uv(0, 0, w, h), tint), "south": face(t, uv(0, 0, w, h), tint),
        "east": face(t, uv(0, 0, d, h), tint), "west": face(t, uv(0, 0, d, h), tint)})]
    model(f"toy/{name}", {"textures": {"tex": f"craftmaster:item/toy/{name}", "particle": f"craftmaster:item/toy/{name}"},
                          "elements": els, "display": disp})
    item_def(name, f"craftmaster:item/toy/{name}", tint_default)


def toys():
    rubber_duck()
    # Bouncy ball: 4 px, round shading, a lighter band round the middle (rubber ball)
    def ball_side(x, y, w, h):
        c = round_face(TINT, 1.1)(x, y, w, h)
        return ramp(TINT, 0.999) if (x, y) == (1, 0) else c
    small_cube("bouncy_ball", [6, 0, 6], [10, 4, 10], ball_side, round_face(TINT, 1.2), round_face(TINT, 0.6), argb(0xB02E26),
               display(gui=2.2, ground=0.8, hand=1.0, gui_y=2))

    # Bean bag: 6×3×6, cloth with a stitched seam round the top
    def bag_top(x, y, w, h):
        edge = min(x, y, w - 1 - x, h - 1 - y)
        if edge == 0:
            return ramp(TINT, 0.45 if (x + y) % 2 == 0 else 0.6)   # stitches
        t = 0.95 - ((x - 2.5) ** 2 + (y - 2.5) ** 2) * 0.03
        return ramp(TINT, t if hash2(x, y, 7) < 0.85 else t - 0.12)

    def bag_side(x, y, w, h):
        if y == 0:
            return ramp(TINT, 0.6 if x % 2 == 0 else 0.45)
        return ramp(TINT, 0.78 - y * 0.18)
    small_cube("bean_bag", [5, 0, 5], [11, 3, 11], bag_side, bag_top, lambda x, y, w, h: ramp(TINT, 0.3), argb(0xC9A66B),
               display(gui=1.8, ground=0.7, hand=0.9, gui_y=2.5))

    # Fragile egg: 4×5×4, cream, rounded by cutting the top corners (cutout), a few faint speckles
    def egg_side(x, y, w, h):
        if y == 0 and x in (0, w - 1):
            return None
        t = 0.95 - y * 0.12 - (0.12 if x in (0, w - 1) else 0)
        if (x, y) == (1, 1):
            t = 0.999
        if (x, y) in ((2, 3), (1, 4)):
            t -= 0.2
        return ramp(CREAM, t)

    def egg_top(x, y, w, h):
        if x in (0, w - 1) and y in (0, h - 1):
            return None
        return ramp(CREAM, 0.95 if (x, y) == (1, 1) else 0.8)
    small_cube("fragile_egg", [6, 0, 6], [10, 5, 10], egg_side, egg_top, lambda x, y, w, h: ramp(CREAM, 0.35), None,
               display(gui=2.0, ground=0.8, hand=0.9, gui_y=1.5))

    # Golf ball: 3 px, white, dimples by a darker centre pixel
    def golf(x, y, w, h):
        if (x, y) == (1, 1):
            return ramp(WHITE, 0.55)
        return ramp(WHITE, 0.95 - (0.25 if x == w - 1 or y == h - 1 else 0) - (0.15 if (x, y) == (0, 0) else 0))
    small_cube("golf_ball", [6, 0, 6], [9, 3, 9], golf, golf, lambda x, y, w, h: ramp(WHITE, 0.3), None,
               display(gui=2.6, ground=1.0, hand=1.1, gui_y=2.5))

    golf_club()
    balloon()


def golf_club():
    """Flat handheld sprite: black grip bottom left, steel shaft, a putter head top right."""
    im = img(16, 16)
    for i in range(12):
        x, y = 2 + i, 13 - i
        if i < 4:
            im[y][x] = rgba(GRIP[2 if i % 2 == 0 else 1])
            im[y + 1][x] = rgba(GRIP[0])
        else:
            im[y][x] = rgba(STEEL[4])
            im[y + 1][x] = rgba(STEEL[1])
    # Head: 5×2 block, light top, dark bottom, outline in the darkest steel
    for y in range(1, 4):
        for x in range(10, 16):
            c = STEEL[5] if y == 1 else STEEL[3] if y == 2 else STEEL[1]
            if x in (10, 15):
                c = STEEL[0]
            im[y][x] = rgba(c)
    im[0][11:15] = [rgba(STEEL[0])] * 4
    im[4][11:15] = [rgba(STEEL[0])] * 4
    write_png(os.path.join(A, "textures/item/toy/golf_club.png"), im)
    model("toy/golf_club", {"parent": "minecraft:item/handheld", "textures": {"layer0": "craftmaster:item/toy/golf_club"}})
    item_def("golf_club", "craftmaster:item/toy/golf_club")


def balloon():
    """Body 8×10×8 and knot 2×2×2 (two cubes), all tinted. One shape, three hand sizes by its fullness."""
    im = img(32, 32)

    def side(x, y, w, h):
        cx, cy = (x + 0.5) / w - 0.36, (y + 0.5) / h - 0.32
        d = math.sqrt(cx * cx + cy * cy)
        t = 1.02 - d * 1.25
        if (x, y) in ((2, 2), (2, 3), (3, 2)):
            t = 1.2  # shine
        if y == h - 1 and x in (0, w - 1) or y == 0 and x in (0, w - 1):
            t -= 0.4
        elif x in (0, w - 1):
            t -= 0.12
        return ramp(TINT, max(0.0, min(0.999, t)))
    paint(im, 0, 0, 8, 10, side)
    paint(im, 8, 0, 8, 10, side)
    paint(im, 16, 0, 8, 8, round_face(TINT, 1.15))
    paint(im, 16, 8, 8, 8, lambda x, y, w, h: ramp(TINT, 0.2 if 3 <= x <= 4 and 3 <= y <= 4 else 0.35))
    paint(im, 24, 0, 2, 2, lambda x, y, w, h: ramp(TINT, 0.55 - y * 0.2))
    write_png(os.path.join(A, "textures/item/toy/balloon.png"), im)
    t, s = "#tex", 0.5
    els = [
        box([4, 2, 4], [12, 12, 12], {
            "north": face(t, uv(0, 0, 8, 10, s), True), "south": face(t, uv(8, 0, 8, 10, s), True),
            "east": face(t, uv(8, 0, 8, 10, s), True), "west": face(t, uv(0, 0, 8, 10, s), True),
            "up": face(t, uv(16, 0, 8, 8, s), True), "down": face(t, uv(16, 8, 8, 8, s), True)}),
        box([7, 0, 7], [9, 2, 9], {d: face(t, uv(24, 0, 2, 2, s), True) for d in ("north", "south", "east", "west", "down")}),
    ]
    tex = {"tex": "craftmaster:item/toy/balloon", "particle": "craftmaster:item/toy/balloon"}
    model("toy/balloon", {"textures": tex, "elements": els})
    for i, k in enumerate((0.55, 0.85, 1.25)):
        model(f"toy/balloon_{i}", {"parent": "craftmaster:item/toy/balloon",
                                   "display": display(gui=0.55 + i * 0.25, ground=0.4 + i * 0.15, hand=k, gui_y=-1)})
    tints = [{"type": "minecraft:dye", "default": argb(0xB02E26)}]
    write_json(os.path.join(A, "items/balloon.json"), {"model": {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": "none", "model": {"type": "minecraft:model", "model": "craftmaster:item/toy/balloon", "tints": tints}}],
        "fallback": {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0,
                     "entries": [{"threshold": 0.3, "model": {"type": "minecraft:model", "model": "craftmaster:item/toy/balloon_1", "tints": tints}},
                                 {"threshold": 0.65, "model": {"type": "minecraft:model", "model": "craftmaster:item/toy/balloon_2", "tints": tints}}],
                     "fallback": {"type": "minecraft:model", "model": "craftmaster:item/toy/balloon_0", "tints": tints}}}})


# ================================================================ blocks

def simple_blockstate(name, variants=None):
    write_json(os.path.join(A, f"blockstates/{name}.json"), {"variants": variants or {"": {"model": f"craftmaster:block/toy/{name}"}}})


def facing_blockstate(name):
    v = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for powered in ("false", "true"):
            e = {"model": f"craftmaster:block/toy/{name}"}
            if y:
                e["y"] = y
            v[f"facing={facing},powered={powered}"] = e
    write_json(os.path.join(A, f"blockstates/{name}.json"), {"variants": v})


def loot(name):
    write_json(os.path.join(D, f"loot_table/blocks/{name}.json"), {
        "type": "minecraft:block", "pools": [{"condition": {"type": "minecraft:survives_explosion"}, "rolls": 1,
                                              "entries": [{"type": "minecraft:item", "name": f"craftmaster:{name}"}]}],
        "random_sequence": f"craftmaster:blocks/{name}"})


def block_item(name, mdl):
    item_def(name, mdl)


def golf_hole():
    im = img(16, 16)

    def top(x, y, w, h):
        # Concentric square rings round the 6 px cup (rim, light ring, dark ring, black centre), corners softened
        d = max(abs(x - 7.5), abs(y - 7.5))
        corner = abs(x - 7.5) == abs(y - 7.5)
        if d <= 2.5:
            if d <= 1.5:
                return (14, 12, 12, 255)
            return (30, 26, 24, 255) if not corner else (44, 40, 36, 255)
        if d <= 3.5:
            return rgba(WHITE[3]) if not corner else rgba(FELT[3])
        # Short mown stripes
        t = 0.62 if (y // 2) % 2 == 0 else 0.5
        if hash2(x, y, 3) > 0.9:
            t += 0.15
        return ramp(FELT, t)
    paint(im, 0, 0, 16, 16, top)
    write_png(os.path.join(A, "textures/block/toy/golf_hole_top.png"), im)
    side = img(16, 16)

    def sfn(x, y, w, h):
        if y < 3:
            return ramp(FELT, 0.6 - y * 0.12 if not (y == 2 and hash2(x, 0, 5) > 0.5) else 0.2)
        return ramp(SOIL, 0.75 - (y - 3) * 0.04 + (0.15 if hash2(x // 2, y, 9) > 0.85 else 0))
    paint(side, 0, 0, 16, 16, sfn)
    write_png(os.path.join(A, "textures/block/toy/golf_hole_side.png"), side)
    bottom = img(16, 16)
    paint(bottom, 0, 0, 16, 16, lambda x, y, w, h: ramp(SOIL, 0.5 + (0.2 if hash2(x // 2, y, 4) > 0.8 else 0)))
    write_png(os.path.join(A, "textures/block/toy/golf_hole_bottom.png"), bottom)
    model("toy/golf_hole", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": "craftmaster:block/toy/golf_hole_top", "side": "craftmaster:block/toy/golf_hole_side",
        "bottom": "craftmaster:block/toy/golf_hole_bottom"}}, "block")
    simple_blockstate("golf_hole")
    block_item("golf_hole", "craftmaster:block/toy/golf_hole")
    loot("golf_hole")


def hollow(name, a, b, height, wall, out_side, out_top, inside, bottom_tex, extra=(), disp=None):
    """A hollow container in one cube (outer faces, the top as a ring with a cut-out middle) and planes facing in
    for the inner walls and floor, a tone darker."""
    w = b - a
    t = "#tex"
    els = [box([a, 0, a], [b, height, b], {
        "north": face(t, uv(0, 0, w, height, 1.0)), "south": face(t, uv(0, 0, w, height, 1.0)),
        "east": face(t, uv(0, 0, w, height, 1.0)), "west": face(t, uv(0, 0, w, height, 1.0)),
        "up": face("#top", [a, a, b, b]), "down": face("#bottom", [a, a, b, b], cull="down")})]
    i0, i1 = a + wall, b - wall
    # Inner walls: planes facing inwards (a plane is an element of zero thickness on one axis)
    els.append(box([i0, wall, i1], [i1, height, i1], {"north": face("#inside", [i0, 16 - height, i1, 16 - wall])}))
    els.append(box([i0, wall, i0], [i1, height, i0], {"south": face("#inside", [i0, 16 - height, i1, 16 - wall])}))
    els.append(box([i0, wall, i0], [i0, height, i1], {"east": face("#inside", [i0, 16 - height, i1, 16 - wall])}))
    els.append(box([i1, wall, i0], [i1, height, i1], {"west": face("#inside", [i0, 16 - height, i1, 16 - wall])}))
    els.append(box([i0, wall, i0], [i1, wall, i1], {"up": face("#inside_bottom", [i0, i0, i1, i1])}))
    els.extend(extra)
    model(f"toy/{name}", {"parent": "minecraft:block/block", "textures": {
        "tex": f"craftmaster:block/toy/{name}_side", "top": f"craftmaster:block/toy/{name}_top",
        "bottom": bottom_tex, "inside": f"craftmaster:block/toy/{name}_inside",
        "inside_bottom": f"craftmaster:block/toy/{name}_inside", "particle": f"craftmaster:block/toy/{name}_side",
        **({"handle": f"craftmaster:block/toy/{name}_handle"} if extra else {})},
        "elements": els, **({"display": disp} if disp else {})}, "block")
    # Textures: side (w × height at the top-left), top ring (full 16 × 16 at the block's coordinates), inside
    side = img(16, 16)
    paint(side, 0, 0, w, height, out_side)
    write_png(os.path.join(A, f"textures/block/toy/{name}_side.png"), side)
    top = img(16, 16)
    for y in range(16):
        for x in range(16):
            if a <= x < b and a <= y < b:
                ring = not (i0 <= x < i1 and i0 <= y < i1)
                top[y][x] = out_top(x - a, y - a, w, w) if ring else T
    write_png(os.path.join(A, f"textures/block/toy/{name}_top.png"), top)
    ins = img(16, 16)
    paint(ins, 0, 0, 16, 16, inside)
    write_png(os.path.join(A, f"textures/block/toy/{name}_inside.png"), ins)
    facing_blockstate(name)
    block_item(name, f"craftmaster:block/toy/{name}")
    loot(name)


def bullseye(cx, cy, x, y):
    """A small red and white target sticker (square rings), or None outside it."""
    d = max(abs(x - cx), abs(y - cy))
    if d > 1.5:
        return None
    if d <= 0.5:
        return rgba(RED[2])
    return rgba(WHITE[4]) if abs(x - cx) != abs(y - cy) else rgba(RED[1])


def targets():
    # Bucket: galvanised steel, two ridges, a target sticker on the front, a wire handle (a plane, both faces)
    def bucket_side(x, y, w, h):
        s = bullseye(4.5, 4.5, x, y)
        if s:
            return s
        t = 0.85 - y * 0.06
        if y in (0, 8):
            t += 0.12 if y == 0 else -0.2
        if y in (2, 6):
            t -= 0.15
        if x == 0 or x == w - 1:
            t -= 0.1
        return ramp(STEEL, t)
    handle_tex = img(16, 16)
    for x in range(3, 13):
        # an arc of wire from the rim up to 4 px above, round
        k = (x - 7.5) / 5.0
        hy = int(round(3.6 * (1 - k * k)))
        handle_tex[15 - (9 + hy) + 0][x] = rgba(STEEL[2])
    handle = box([3, 9, 8], [13, 14, 8], {"north": face("#handle", [3, 2, 13, 7]), "south": face("#handle", [13, 2, 3, 7])})
    hollow("bucket_target", 3, 13, 9, 1, bucket_side, lambda x, y, w, h: ramp(STEEL, 0.95 if x in (0, w - 1) or y in (0, h - 1) else 0.6),
           vertical(STEEL, 0.5, 0.15, False), "craftmaster:block/toy/bucket_target_side", [handle])
    write_png(os.path.join(A, "textures/block/toy/bucket_target_handle.png"), handle_tex)

    # Bin: dark green steel with vertical ribs and a light rim, a target sticker
    def bin_side(x, y, w, h):
        s = bullseye(5.5, 6.5, x, y)
        if s:
            return s
        t = 0.75 - y * 0.03
        if y == 0:
            t = 0.95
        if y == 1:
            t = 0.4
        if x % 3 == 1:
            t += 0.12
        if x % 3 == 2:
            t -= 0.08
        if y == h - 1:
            t -= 0.2
        return ramp(GREEN_BIN, t)
    hollow("bin_target", 2, 14, 15, 1, bin_side, lambda x, y, w, h: ramp(GREEN_BIN, 0.95),
           vertical(GREEN_BIN, 0.35, 0.05, False), "craftmaster:block/toy/bin_target_side")

    # Mug: white ceramic with a red band, a handle (plane with a C shape cut out)
    def mug_side(x, y, w, h):
        if y in (1,):
            return rgba(RED[2])
        t = 0.9 - y * 0.07 - (0.15 if x in (0, w - 1) else 0)
        return ramp(CERAMIC, t)
    mug_handle_tex = img(16, 16)
    for (x, y) in ((11, 11), (12, 11), (12, 12), (12, 13), (12, 14), (11, 14)):
        mug_handle_tex[y][x] = rgba(CERAMIC[2])
    mug_handle = box([11, 1, 8], [13, 5, 8], {"north": face("#handle", [11, 11, 13, 15]), "south": face("#handle", [13, 11, 11, 15])})
    hollow("mug_target", 5, 11, 6, 1, mug_side, lambda x, y, w, h: ramp(CERAMIC, 0.99),
           vertical(CERAMIC, 0.45, 0.15, False), "craftmaster:block/toy/mug_target_side", [mug_handle],
           disp={**block_display(), "gui": {"rotation": [30, 225, 0], "translation": [0, 2, 0], "scale": [1.1, 1.1, 1.1]}})
    write_png(os.path.join(A, "textures/block/toy/mug_target_handle.png"), mug_handle_tex)


def glasses():
    """Measuring glass (6×10×6) and tank (14×16×14): translucent glass with white marks, water inside in 8 steps."""
    water = img(16, 16)
    paint(water, 0, 0, 16, 16, lambda x, y, w, h: (52, 112, 214, 170) if y else (110, 170, 240, 200))
    write_png(os.path.join(A, "textures/block/toy/measured_water.png"), water)
    for name, a, b, height, marks, frame in (("measuring_glass", 5, 11, 10, 4, False), ("measuring_tank", 1, 15, 16, 8, True)):
        w = b - a
        glass = img(16, 16)
        top_y = 16 - height

        def gfn(x, y, ww, hh, w=w, height=height, marks=marks, frame=frame):
            yy = y - (16 - height)  # 0 = top of the glass
            if yy < 0 or x >= w:
                return T
            if frame and (yy in (0, height - 1) or x in (0, w - 1)):
                return rgba(STEEL[3 if yy == 0 else 1])
            # Graduation: a long mark every quarter, short ones between, on the left of the face
            inner = height - 1
            for k in range(1, marks * 2):
                my = inner - round(k * (inner - 1) / (marks * 2))
                if yy == my and x < (3 if k % 2 == 0 else 2) + (1 if frame else 0) and x >= (1 if frame else 0):
                    return (236, 240, 244, 230)
            if x == (1 if frame else 0) + 0 and 1 <= yy < height - 1 and not frame:
                return (210, 226, 236, 120)
            if yy == (1 if frame else 0) and not frame:
                return (220, 236, 246, 150)
            return (200, 222, 236, 70)
        paint(glass, 0, 0, 16, 16, gfn)
        write_png(os.path.join(A, f"textures/block/toy/{name}.png"), glass)
        g = "#glass"
        shell = box([a, 0, a], [b, height, b], {d: face(g, [0, top_y, w, 16]) for d in ("north", "south", "east", "west")}
                    | {"down": face(g, [0, top_y, w, top_y + w] if w <= height else [0, 0, w, w], cull="down")})
        inner = [box([a, 0, b - 0.01], [b, height, b - 0.01], {"north": face(g, [w, top_y, 0, 16])}),
                 box([a, 0, a + 0.01], [b, height, a + 0.01], {"south": face(g, [w, top_y, 0, 16])}),
                 box([b - 0.01, 0, a], [b - 0.01, height, b], {"west": face(g, [w, top_y, 0, 16])}),
                 box([a + 0.01, 0, a], [a + 0.01, height, b], {"east": face(g, [w, top_y, 0, 16])})]
        variants = {}
        for stage in range(9):
            els = [shell] + inner
            if stage:
                top = 0.5 + (height - 1) * stage / 8
                wa, wb = a + 0.5, b - 0.5
                els.append(box([wa, 0.5, wa], [wb, top, wb], {
                    "up": face("#water", [wa, wa, wb, wb]),
                    **{d: face("#water", [wa, 16 - top, wb, 15.5]) for d in ("north", "south", "east", "west")}}))
            mdl = {"parent": "minecraft:block/block", "ambientocclusion": False,
                   "textures": {"glass": f"craftmaster:block/toy/{name}", "water": "craftmaster:block/toy/measured_water",
                                "particle": f"craftmaster:block/toy/{name}"}, "elements": els}
            if name == "measuring_glass":
                mdl["display"] = {**block_display(), "gui": {"rotation": [30, 225, 0], "translation": [0, 1, 0], "scale": [1.0, 1.0, 1.0]}}
            model(f"toy/{name}_{stage}", mdl, "block")
            variants[f"fill={stage}"] = {"model": f"craftmaster:block/toy/{name}_{stage}"}
        simple_blockstate(name, variants)
        block_item(name, f"craftmaster:block/toy/{name}_{3 if name == 'measuring_glass' else 5}")
        loot(name)


# ================================================================ recipes

def shaped(name, pattern, key, count=1):
    write_json(os.path.join(D, f"recipe/{name}.json"), {"type": "minecraft:crafting_shaped", "category": "misc",
                                                        "pattern": pattern, "key": key,
                                                        "result": {"id": f"craftmaster:{name}", "count": count}})


def shapeless(name, ingredients, count=1, file=None):
    write_json(os.path.join(D, f"recipe/{file or name}.json"), {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                                "ingredients": ingredients,
                                                                "result": {"id": f"craftmaster:{name}", "count": count}})


def dye_recipe(name):
    write_json(os.path.join(D, f"recipe/{name}_dyed.json"), {"type": "minecraft:crafting_dye", "dye": "#minecraft:dyes",
                                                             "group": "craftmaster_toy_dye",
                                                             "result": {"id": f"craftmaster:{name}"}, "target": f"craftmaster:{name}"})


def recipes():
    shaped("rubber_duck", [" Y", "SS"], {"Y": "minecraft:yellow_dye", "S": "minecraft:slime_ball"}, 2)
    shapeless("bouncy_ball", ["minecraft:slime_ball", "minecraft:slime_ball", "minecraft:red_dye"], 4)
    shapeless("bean_bag", ["minecraft:paper", "minecraft:wheat_seeds", "minecraft:string"], 2)
    shapeless("fragile_egg", ["minecraft:egg", "minecraft:paper"], 1)
    shapeless("golf_ball", ["minecraft:bone_meal", "minecraft:slime_ball"], 4)
    shaped("golf_club", ["S ", "S ", "SI"], {"S": "minecraft:stick", "I": "minecraft:iron_ingot"})
    shapeless("golf_hole", ["minecraft:grass_block", "minecraft:white_wool", "minecraft:stick"], 1)
    shapeless("balloon", ["minecraft:slime_ball", "minecraft:string", "minecraft:red_dye"], 3)
    shapeless("bucket_target", ["minecraft:bucket", "minecraft:target"])
    shapeless("bin_target", ["minecraft:cauldron", "minecraft:target"])
    shapeless("mug_target", ["minecraft:flower_pot", "minecraft:target"])
    shapeless("measuring_glass", ["minecraft:glass_bottle", "minecraft:stick"])
    shapeless("measuring_tank", ["minecraft:cauldron", "minecraft:glass", "minecraft:stick"])
    for n in ("rubber_duck", "bouncy_ball", "bean_bag", "balloon"):
        dye_recipe(n)


# ================================================================ texts

def texts():
    en, fr = {}, {}

    def both(key, e, f):
        en[key] = e
        fr[key] = f
    both("item.craftmaster.rubber_duck", "Rubber Duck", "Canard en plastique")
    both("item.craftmaster.rubber_duck.colored", "%s Rubber Duck", "Canard en plastique %s")
    both("item.craftmaster.bouncy_ball", "Bouncy Ball", "Balle rebondissante")
    both("item.craftmaster.bouncy_ball.colored", "%s Bouncy Ball", "Balle rebondissante %s")
    both("item.craftmaster.bean_bag", "Bean Bag", "Sachet")
    both("item.craftmaster.bean_bag.colored", "%s Bean Bag", "Sachet %s")
    both("item.craftmaster.fragile_egg", "Fragile Egg", "Œuf fragile")
    both("item.craftmaster.fragile_egg.thin", "Thin-Shelled Egg", "Œuf à coquille fine")
    both("item.craftmaster.fragile_egg.blocks", "Breaks if dropped from more than %s blocks", "Casse au-delà d'une chute de %s blocs")
    both("item.craftmaster.fragile_egg.any", "Breaks at the least knock", "Casse au moindre choc")
    both("item.craftmaster.golf_ball", "Golf Ball", "Balle de golf")
    both("item.craftmaster.golf_ball.hint", "Hit it with the golf club, towards a golf hole", "Se frappe au club de golf, vers un trou de golf")
    both("item.craftmaster.golf_club", "Golf Club", "Club de golf")
    both("item.craftmaster.golf_club.hint", "Hit a ball: wait for the charge for a full swing; sneaking: a putt",
         "Frapper une balle : attendre la charge pour un plein coup ; accroupi : un putt")
    both("item.craftmaster.toy.hint", "On a block: put it down there · in the air: hold, then let go to throw",
         "Sur un bloc : le poser là · en l'air : maintenir puis relâcher pour lancer")
    both("item.craftmaster.balloon", "Balloon", "Ballon de baudruche")
    both("item.craftmaster.balloon.colored", "%s Balloon", "Ballon %s")
    both("item.craftmaster.balloon.size", "%s blocks wide", "%s blocs de large")
    both("item.craftmaster.balloon.hint", "Hold to blow it up (it may burst!) · inflated, on a block: tie it there",
         "Maintenir pour gonfler (il peut éclater !) · gonflé, sur un bloc : l'attacher là")
    both("block.craftmaster.golf_hole", "Golf Hole", "Trou de golf")
    both("block.craftmaster.bucket_target", "Target Bucket", "Seau-cible")
    both("block.craftmaster.bin_target", "Target Bin", "Poubelle-cible")
    both("block.craftmaster.mug_target", "Target Mug", "Tasse-cible")
    both("block.craftmaster.measuring_glass", "Measuring Glass", "Verre doseur")
    both("block.craftmaster.measuring_tank", "Graduated Tank", "Cuve graduée")
    both("entity.craftmaster.toy", "Toy", "Objet d'épreuve")
    both("entity.craftmaster.balloon", "Balloon", "Ballon")
    both("subtitles.craftmaster.toy.squeak", "Rubber duck squeaks", "Couinement de canard")
    both("subtitles.craftmaster.toy.pop", "Balloon pops", "Un ballon éclate")
    both("subtitles.craftmaster.toy.puff", "Balloon inflates", "On gonfle un ballon")
    for i, d in enumerate(DYES):
        en[f"color.craftmaster.{d}.m"] = EN[i]
        en[f"color.craftmaster.{d}.f"] = EN[i]
        fr[f"color.craftmaster.{d}.m"] = FR_M[i]
        fr[f"color.craftmaster.{d}.f"] = FR_F[i]
    return en, fr


SOUNDS = {
    "toy.squeak": {"subtitle": "subtitles.craftmaster.toy.squeak", "sounds": ["craftmaster:toy/squeak1", "craftmaster:toy/squeak2"]},
    "toy.pop": {"subtitle": "subtitles.craftmaster.toy.pop", "sounds": ["craftmaster:toy/pop1", "craftmaster:toy/pop2"]},
    "toy.puff": {"subtitle": "subtitles.craftmaster.toy.puff", "sounds": ["craftmaster:toy/puff1", "craftmaster:toy/puff2"]},
}


def merge_json(path, add):
    """Adds the keys to a JSON object file, keeping every other key and their order (new keys at the end)."""
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    for k, v in add.items():
        data[k] = v
    write_json(path, data)


# ================================================================ sounds

def sounds():
    try:
        import numpy as np
    except ImportError:
        print("numpy missing: sounds kept as they are")
        return
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        print("ffmpeg missing: sounds kept as they are")
        return
    sr = 44100
    out = os.path.join(A, "sounds", "toy")
    os.makedirs(out, exist_ok=True)
    rng = np.random.default_rng(7)

    def env(n, attack, release):
        e = np.ones(n)
        a, r = int(attack * sr), int(release * sr)
        e[:a] = np.linspace(0, 1, a)
        e[-r:] *= np.linspace(1, 0, r) ** 2
        return e

    def save(name, x):
        x = x / (np.max(np.abs(x)) + 1e-9) * 0.85
        pcm = (x * 32767).astype("<i2").tobytes()
        wav = os.path.join(out, name + ".wav")
        with open(wav, "wb") as f:
            f.write(b"RIFF" + struct.pack("<I", 36 + len(pcm)) + b"WAVEfmt " + struct.pack("<IHHIIHH", 16, 1, 1, sr, sr * 2, 2, 16)
                    + b"data" + struct.pack("<I", len(pcm)) + pcm)
        subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", os.path.join(out, name + ".ogg")], check=True)
        os.remove(wav)

    def squeak(dur, f0, f1, f2):
        n = int(dur * sr)
        t = np.arange(n) / sr
        # Pitch rises fast then sags: the rubber reed of a squeeze toy
        k = t / dur
        f = np.where(k < 0.3, f0 + (f1 - f0) * (k / 0.3), f1 + (f2 - f1) * ((k - 0.3) / 0.7))
        f = f * (1 + 0.025 * np.sin(2 * np.pi * 38 * t))
        ph = 2 * np.pi * np.cumsum(f) / sr
        tone = np.sin(ph) + 0.55 * np.sin(2 * ph) + 0.3 * np.sin(3 * ph) + 0.12 * np.sin(5 * ph)
        air = rng.normal(0, 1, n)
        air = np.convolve(air, np.ones(6) / 6, mode="same") * 0.25
        return (tone + air) * env(n, 0.012, dur * 0.45)

    save("squeak1", squeak(0.26, 1150, 1650, 1250))
    save("squeak2", squeak(0.21, 1250, 1850, 1450))

    def pop(dur, low):
        n = int(dur * sr)
        t = np.arange(n) / sr
        noise = rng.normal(0, 1, n) * np.exp(-t * 45)
        thump = np.sin(2 * np.pi * low * t * (1 - t * 2)) * np.exp(-t * 30) * 0.9
        crack = rng.normal(0, 1, n) * np.exp(-t * 400) * 1.5
        x = noise + thump + crack
        return np.convolve(x, np.ones(3) / 3, mode="same") * env(n, 0.0005, 0.03)

    save("pop1", pop(0.22, 140))
    save("pop2", pop(0.2, 180))

    def puff(dur, centre):
        n = int(dur * sr)
        t = np.arange(n) / sr
        noise = rng.normal(0, 1, n)
        # Band of breath noise, then a squeak of stretched rubber at the end
        spec = np.fft.rfft(noise)
        fr_ = np.fft.rfftfreq(n, 1 / sr)
        spec *= np.exp(-((fr_ - centre) / (centre * 0.6)) ** 2)
        breath = np.fft.irfft(spec, n)
        breath = breath / (np.max(np.abs(breath)) + 1e-9)
        e = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 1.5
        rub = np.sin(2 * np.pi * np.cumsum(420 + 260 * t / dur) / sr) * np.clip((t / dur - 0.6) * 2.5, 0, 1) * 0.25
        return breath * e + rub * e

    save("puff1", puff(0.3, 900))
    save("puff2", puff(0.26, 1200))


# ================================================================ entry points

def write_assets():
    toys()
    golf_hole()
    targets()
    glasses()
    recipes()


def generate(en, fr, sound_events):
    """Called by gen_assets.py: writes the files and adds the texts and sound events to its dictionaries."""
    write_assets()
    e, f = texts()
    en.update(e)
    fr.update(f)
    sound_events.update(SOUNDS)


if __name__ == "__main__":
    write_assets()
    if "--no-sound" not in sys.argv:
        sounds()
    e, f = texts()
    merge_json(os.path.join(A, "lang/en_us.json"), e)
    merge_json(os.path.join(A, "lang/fr_fr.json"), f)
    merge_json(os.path.join(A, "sounds.json"), SOUNDS)
    print("toys: assets written")
