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


def hexs(*cs):
    return [((c >> 16) & 255, (c >> 8) & 255, c & 255) for c in cs]


# Ramps (darkest → lightest, 7 levels, 4 = the base) sampled from the game's own textures, so the objects sit
# next to vanilla blocks: bucket item (steel), egg item, cauldron inside, target block, quartz, glass, dirt.
STEEL_R = hexs(0x353535, 0x545454, 0x727272, 0x969696, 0xA8A8A8, 0xD8D8D8, 0xFFFFFF)
EGG_R = hexs(0x554E3B, 0x746B51, 0x8E825B, 0xB3A57D, 0xDFCE9B, 0xF0E6C6, 0xFBF6E6)
GOLF_R = hexs(0x7E8A94, 0x9CA6AE, 0xBAC2C8, 0xD4DADE, 0xE8ECEE, 0xF7F8F9, 0xFFFFFF)
INNER_R = hexs(0x1E1E22, 0x27272B, 0x2D2D32, 0x343438, 0x3F3E42, 0x4F4F4F, 0x595858)
CERAMIC_R = hexs(0x9C968C, 0xB8B2A8, 0xCFCAC0, 0xDDD9CB, 0xE9E5DE, 0xF2EFED, 0xFBFAF8)
TARGET_RED = hexs(0x8E2424, 0xB93232, 0xC82F2F, 0xD53535)
TARGET_CREAM = hexs(0xEBD7BA, 0xEFE2D0, 0xF7F2EB)
GLASS_R = hexs(0x7BAEB7, 0x8BC1CD, 0xA8D0D9, 0xD0EAE9)
NATURAL_BAG = 0xC9A66B
# Inside of the mug: tea stains on the ceramic
TEA_R = hexs(0x6E5A48, 0x7E6852, 0x8C765E, 0x9A8468, 0xA89274, 0xB8A284, 0xC8B296)


def levels_img(L, ramp, w=None, h=None):
    """A texture from a level map: ints index the ramp (clamped), tuples are fixed colours, None is transparent."""
    h = h or len(L)
    w = w or len(L[0])
    im = img(w, h)
    for y in range(h):
        for x in range(w):
            v = L[y][x]
            if v is None:
                continue
            im[y][x] = rgba(v) if isinstance(v, tuple) else rgba(ramp[max(0, min(len(ramp) - 1, v))])
    return im


def lmap(w, h):
    return [[None] * w for _ in range(h)]


def put(L, x0, y0, w, h, fn):
    for y in range(h):
        for x in range(w):
            v = fn(x, y, w, h)
            L[y0 + y][x0 + x] = v


def box_side(rows, salt=0, strokes=True):
    """A side face of a soft box: one level per row (light on top, dark below, the change broken by 2-3 px
    strokes of the upper tone), the vertical edges a step darker from the second row down, so the top row meets
    the top face (painted at rows[0]) with the same pixels."""
    def fn(x, y, w, h):
        lv = rows[min(y, len(rows) - 1)]
        if strokes and y > 0 and lv < rows[min(y - 1, len(rows) - 1)] and hash2(x // 3, y, salt) > 0.55:
            lv += 1
        if y > 0 and (x == 0 or x == w - 1):
            lv -= 1
        return lv
    return fn


def box_top(level, shine=()):
    return lambda x, y, w, h: 6 if (x, y) in shine else level


def box_bottom(level):
    """Bottom face: its border at the side faces' bottom row level, the corners a step darker (the side edges)."""
    return lambda x, y, w, h: level - (1 if x in (0, w - 1) and y in (0, h - 1) else 0)


def dye_variants(name, L, w, h, natural=None):
    """Writes the texture of a dyeable toy in the 16 dye colours (hue-shifted ramps) and, if given, its undyed
    colour (natural); returns the texture id of the undyed look."""
    for i, dye in enumerate(DYES):
        write_png(os.path.join(A, f"textures/item/toy/{name}_{dye}.png"), levels_img(L, hue_ramp(DYE_RGB[i]), w, h))
    if natural is not None:
        write_png(os.path.join(A, f"textures/item/toy/{name}_natural.png"), levels_img(L, hue_ramp(natural), w, h))
        return f"craftmaster:item/toy/{name}_natural"
    return None


def dye_select(model_for, fallback):
    """Item model picking the model of the dye colour (minecraft:dyed_color); undyed → fallback."""
    return {"type": "minecraft:select", "property": "minecraft:component", "component": "minecraft:dyed_color",
            "cases": [{"when": signed(DYE_RGB[i]), "model": {"type": "minecraft:model", "model": model_for(dye)}}
                      for i, dye in enumerate(DYES)],
            "fallback": {"type": "minecraft:model", "model": fallback}}


def cube_toy(name, frm, to, paint_fn, disp, ramp=None, dyed=False, default_dye=None, natural=None, tex_size=16):
    """A small toy as one cube. paint_fn(L) fills the level map: side face at (0, 0), top at (0, 8), bottom at
    (8, 8) (in texels). Dyed: 16 textures + an item model choosing by colour; otherwise one texture from ramp."""
    w, hgt, d = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
    L = lmap(tex_size, tex_size)
    paint_fn(L, w, hgt, d)
    t = "#tex"
    s = 16 / tex_size
    els = [box(frm, to, {
        "up": face(t, uv(0, 8, w, d, s)), "down": face(t, uv(8, 8, w, d, s)),
        "north": face(t, uv(0, 0, w, hgt, s)), "south": face(t, uv(0, 0, w, hgt, s)),
        "east": face(t, uv(0, 0, d, hgt, s)), "west": face(t, uv(0, 0, d, hgt, s))})]
    old = os.path.join(A, f"textures/item/toy/{name}.png")
    if dyed:
        nat = dye_variants(name, L, tex_size, tex_size, natural)
        base_tex = nat or f"craftmaster:item/toy/{name}_{default_dye}"
        if os.path.exists(old):
            os.remove(old)
        model(f"toy/{name}", {"textures": {"tex": base_tex, "particle": base_tex}, "elements": els, "display": disp})
        for dye in DYES:
            model(f"toy/{name}_{dye}", {"parent": f"craftmaster:item/toy/{name}",
                                         "textures": {"tex": f"craftmaster:item/toy/{name}_{dye}",
                                                      "particle": f"craftmaster:item/toy/{name}_{dye}"}})
        write_json(os.path.join(A, f"items/{name}.json"), {"model": dye_select(
            lambda dye: f"craftmaster:item/toy/{name}_{dye}",
            f"craftmaster:item/toy/{name}" if nat else f"craftmaster:item/toy/{name}_{default_dye}")})
    else:
        write_png(old, levels_img(L, ramp, tex_size, tex_size))
        model(f"toy/{name}", {"textures": {"tex": f"craftmaster:item/toy/{name}", "particle": f"craftmaster:item/toy/{name}"},
                              "elements": els, "display": disp})
        item_def(name, f"craftmaster:item/toy/{name}")


def toys():
    rubber_duck()

    # Bouncy ball, 4 px: smooth rubber, a small shine off the edges
    def ball(L, w, h, d):
        put(L, 0, 0, w, h, lambda x, y, ww, hh: 6 if (x, y) == (1, 1) else box_side([5, 5, 4, 3])(x, y, ww, hh))
        put(L, 0, 8, w, d, box_top(5, shine={(1, 1)}))
        put(L, 8, 8, w, d, box_bottom(3))
    cube_toy("bouncy_ball", [6, 0, 6], [10, 4, 10], ball, display(gui=2.2, ground=0.8, hand=1.0, gui_y=2),
             dyed=True, default_dye="red")

    # Bean bag, 6×3×6: matte cloth, a running stitch one pixel inside the top's border, no shine
    def bag(L, w, h, d):
        put(L, 0, 0, w, h, box_side([5, 4, 4], salt=5))

        def top(x, y, ww, hh):
            # The seam: a continuous line one pixel inside the border, the cushion inside it lit
            if min(x, y, ww - 1 - x, hh - 1 - y) == 1:
                return 4
            return 5
        put(L, 0, 8, w, d, top)
        put(L, 8, 8, w, d, box_bottom(4))
    cube_toy("bean_bag", [5, 0, 5], [11, 3, 11], bag, display(gui=1.8, ground=0.7, hand=0.9, gui_y=2.5),
             dyed=True, natural=NATURAL_BAG)

    # Fragile egg, 4×5×4, the game's egg colours; rounded by cutting the top corners (cut-out)
    def egg(L, w, h, d):
        def side(x, y, ww, hh):
            if y == 0 and x in (0, ww - 1):
                return None
            if (x, y) == (1, 1):
                return 6
            return box_side([5, 5, 4, 4, 3], salt=7)(x, y, ww, hh)
        put(L, 0, 0, w, h, side)
        put(L, 0, 8, w, d, lambda x, y, ww, hh: None if x in (0, ww - 1) and y in (0, hh - 1) else (6 if (x, y) == (1, 1) else 5))
        put(L, 8, 8, w, d, box_bottom(3))
    cube_toy("fragile_egg", [6, 0, 6], [10, 5, 10], egg, display(gui=2.0, ground=0.8, hand=0.9, gui_y=1.5), ramp=EGG_R)

    # Golf ball, 3 px: white with cool grey shading, a shine on top
    def golf(L, w, h, d):
        put(L, 0, 0, w, h, box_side([5, 4, 3], strokes=False))
        put(L, 0, 8, w, d, box_top(5, shine={(1, 1)}))
        put(L, 8, 8, w, d, box_bottom(3))
    cube_toy("golf_ball", [6, 0, 6], [9, 3, 9], golf, display(gui=2.6, ground=1.0, hand=1.1, gui_y=2.5), ramp=GOLF_R)

    golf_club()
    balloon()


def golf_club():
    """Flat handheld sprite in the game's tool colours: black rubber grip bottom left, iron shaft, iron putter head
    top right (light top edge, dark bottom edge, like the iron tools)."""
    GRIP = hexs(0x181818, 0x2A2A2A, 0x3C3C3C)
    im = img(16, 16)
    for i in range(12):
        x, y = 2 + i, 13 - i
        if i < 4:
            im[y][x] = rgba(GRIP[2] if i % 2 == 0 else GRIP[1])
            im[y + 1][x] = rgba(GRIP[0])
        else:
            im[y][x] = rgba(STEEL_R[5])
            im[y + 1][x] = rgba(STEEL_R[2])
    for y in range(1, 4):
        for x in range(10, 16):
            c = STEEL_R[6] if y == 1 else STEEL_R[4] if y == 2 else STEEL_R[2]
            if x in (10, 15):
                c = STEEL_R[1]
            im[y][x] = rgba(c)
    im[0][11:15] = [rgba(STEEL_R[1])] * 4
    im[4][11:15] = [rgba(STEEL_R[0])] * 4
    write_png(os.path.join(A, "textures/item/toy/golf_club.png"), im)
    model("toy/golf_club", {"parent": "minecraft:item/handheld", "textures": {"layer0": "craftmaster:item/toy/golf_club"}})
    item_def("golf_club", "craftmaster:item/toy/golf_club")


def balloon():
    """Body 8×10×8 and knot 2×2×2, one shape for every colour (16 painted textures), three hand sizes."""
    L = lmap(32, 32)

    def side(x, y, w, h):
        if 2 <= x <= 3 and 2 <= y <= 4:
            return 6       # the shine of stretched rubber: a soft upright patch, top left
        if x == 4 and 2 <= y <= 3:
            return 5
        return box_side([5, 5, 5, 5, 4, 4, 4, 4, 3, 3], salt=9)(x, y, w, h)
    put(L, 0, 0, 8, 10, side)
    put(L, 16, 0, 8, 8, box_top(5, shine={(2, 2), (3, 2), (2, 3), (3, 3)}))
    put(L, 16, 8, 8, 8, lambda x, y, w, h: 2 if 3 <= x <= 4 and 3 <= y <= 4 else box_bottom(3)(x, y, w, h))
    put(L, 24, 0, 2, 2, lambda x, y, w, h: 3 if y == 0 else 2)
    dye_variants("balloon", L, 32, 32)
    old = os.path.join(A, "textures/item/toy/balloon.png")
    if os.path.exists(old):
        os.remove(old)
    t, s = "#tex", 0.5
    els = [
        box([4, 2, 4], [12, 12, 12], {d: face(t, uv(0, 0, 8, 10, s)) for d in ("north", "south", "east", "west")}
            | {"up": face(t, uv(16, 0, 8, 8, s)), "down": face(t, uv(16, 8, 8, 8, s))}),
        box([7, 0, 7], [9, 2, 9], {d: face(t, uv(24, 0, 2, 2, s)) for d in ("north", "south", "east", "west", "down")}),
    ]
    red = "craftmaster:item/toy/balloon_red"
    model("toy/balloon", {"textures": {"tex": red, "particle": red}, "elements": els})
    sizes = []
    for i, k in enumerate((0.55, 0.85, 1.25)):
        model(f"toy/balloon_{i}", {"parent": "craftmaster:item/toy/balloon",
                                   "display": display(gui=0.55 + i * 0.25, ground=0.4 + i * 0.15, hand=k, gui_y=-1)})
        sizes.append(i)
    for dye in DYES:
        tex = {"tex": f"craftmaster:item/toy/balloon_{dye}", "particle": f"craftmaster:item/toy/balloon_{dye}"}
        model(f"toy/balloon_{dye}", {"parent": "craftmaster:item/toy/balloon", "textures": tex})
        for i in sizes:
            model(f"toy/balloon_{i}_{dye}", {"parent": f"craftmaster:item/toy/balloon_{i}", "textures": tex})
    hand = lambda i: dye_select(lambda dye: f"craftmaster:item/toy/balloon_{i}_{dye}", f"craftmaster:item/toy/balloon_{i}_red")
    write_json(os.path.join(A, "items/balloon.json"), {"model": {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": "none", "model": dye_select(lambda dye: f"craftmaster:item/toy/balloon_{dye}", "craftmaster:item/toy/balloon_red")}],
        "fallback": {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0,
                     "entries": [{"threshold": 0.3, "model": hand(1)}, {"threshold": 0.65, "model": hand(2)}],
                     "fallback": hand(0)}}})


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


def golf_hole():
    """The game's grass block (same textures, tinted by the biome like the grass around it: it disappears into the
    lawn) with the cup as an overlay on top: square rings of the game's dirt, darker towards the black middle."""
    DIRT = hexs(0x2A1C12, 0x3E2A1C, 0x593D29, 0x79553A)
    cup = img(16, 16)
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            d = max(dx, dy)
            corner = dx == dy
            if d > 3.5:
                continue
            if d > 2.5:   # the cut edge of the turf, lit on the far side, in shade on the near side
                c = DIRT[3] if y < 8 else DIRT[2]
                if corner:
                    continue  # rounded: the corners stay grass
            elif d > 1.5:
                c = DIRT[1]
            else:
                c = DIRT[0]
            cup[y][x] = rgba(c)
    write_png(os.path.join(A, "textures/block/toy/golf_hole_cup.png"), cup)
    for old in ("golf_hole_top", "golf_hole_side", "golf_hole_bottom"):
        p = os.path.join(A, f"textures/block/toy/{old}.png")
        if os.path.exists(p):
            os.remove(p)
    full = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
        "down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"},
        "up": {"uv": [0, 0, 16, 16], "texture": "#top", "cullface": "up", "tintindex": 0},
        **{d: {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": d} for d in ("north", "south", "east", "west")}}}
    overlay = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
        d: {"uv": [0, 0, 16, 16], "texture": "#overlay", "tintindex": 0, "cullface": d} for d in ("north", "south", "east", "west")}}
    cup_el = {"from": [0, 16.02, 0], "to": [16, 16.02, 16], "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#cup", "cullface": "up"}}}
    model("toy/golf_hole", {"parent": "minecraft:block/block", "textures": {
        "particle": "minecraft:block/dirt", "bottom": "minecraft:block/dirt", "top": "minecraft:block/grass_block_top",
        "side": "minecraft:block/grass_block_side", "overlay": "minecraft:block/grass_block_side_overlay",
        "cup": "craftmaster:block/toy/golf_hole_cup"}, "elements": [full, overlay, cup_el]}, "block")
    simple_blockstate("golf_hole")
    write_json(os.path.join(A, "items/golf_hole.json"), {"model": {
        "type": "minecraft:model", "model": "craftmaster:block/toy/golf_hole",
        "tints": [{"type": "minecraft:grass", "downfall": 1.0, "temperature": 0.5}]}})
    loot("golf_hole")


def hollow(name, a, b, height, wall, side_fn, rim_level, ramp, inner_ramp, extra=(), disp=None, extra_tex=None):
    """A hollow container in one cube: outer faces, the top as a ring (the wall's thickness) with a cut-out middle,
    and planes facing in for the inner walls and floor, in the darker inner ramp. The ring is painted at the side
    faces' top-row level, so the rim reads as one edge."""
    w = b - a
    t = "#tex"
    els = [box([a, 0, a], [b, height, b], {
        "north": face(t, uv(0, 0, w, height, 1.0)), "south": face(t, uv(0, 0, w, height, 1.0)),
        "east": face(t, uv(0, 0, w, height, 1.0)), "west": face(t, uv(0, 0, w, height, 1.0)),
        "up": face("#top", [a, a, b, b]), "down": face("#bottom", [a, a, b, b], cull="down")})]
    i0, i1 = a + wall, b - wall
    els.append(box([i0, wall, i1], [i1, height, i1], {"north": face("#inside", [i0, 16 - height, i1, 16 - wall])}))
    els.append(box([i0, wall, i0], [i1, height, i0], {"south": face("#inside", [i0, 16 - height, i1, 16 - wall])}))
    els.append(box([i0, wall, i0], [i0, height, i1], {"east": face("#inside", [i0, 16 - height, i1, 16 - wall])}))
    els.append(box([i1, wall, i0], [i1, height, i1], {"west": face("#inside", [i0, 16 - height, i1, 16 - wall])}))
    els.append(box([i0, wall, i0], [i1, wall, i1], {"up": face("#inside_bottom", [i0, i0, i1, i1])}))
    els.extend(extra)
    tex = {"tex": f"craftmaster:block/toy/{name}_side", "top": f"craftmaster:block/toy/{name}_top",
           "inside": f"craftmaster:block/toy/{name}_inside", "inside_bottom": f"craftmaster:block/toy/{name}_inside_bottom",
           "bottom": f"craftmaster:block/toy/{name}_bottom",
           "particle": f"craftmaster:block/toy/{name}_side"}
    if extra_tex:
        tex.update(extra_tex)
    model(f"toy/{name}", {"parent": "minecraft:block/block", "textures": tex, "elements": els,
                          **({"display": disp} if disp else {})}, "block")
    L = lmap(16, 16)
    put(L, 0, 0, w, height, side_fn)
    write_png(os.path.join(A, f"textures/block/toy/{name}_side.png"), levels_img(L, ramp))
    # Top ring (also used underneath: the ring's outer border is the bottom row's level there is no need to see)
    top = img(16, 16)
    for y in range(16):
        for x in range(16):
            if a <= x < b and a <= y < b and not (i0 <= x < i1 and i0 <= y < i1):
                top[y][x] = rgba(ramp[rim_level])
            elif i0 <= x < i1 and i0 <= y < i1:
                top[y][x] = rgba(inner_ramp[2])   # the floor seen from below / the hollow (only for the bottom face)
    # The bottom face reuses the top texture; make the centre of the top transparent by a separate texture
    ring = [[top[y][x] if not (i0 <= x < i1 and i0 <= y < i1) else T for x in range(16)] for y in range(16)]
    write_png(os.path.join(A, f"textures/block/toy/{name}_top.png"), ring)
    bottom = [[rgba(ramp[2]) if a <= x < b and a <= y < b else T for x in range(16)] for y in range(16)]
    write_png(os.path.join(A, f"textures/block/toy/{name}_bottom.png"), bottom)
    # Inner walls: dark at the bottom, lighter near the rim (light falls in from above)
    ins = img(16, 16)
    for y in range(16):
        for x in range(16):
            yy = y - (16 - height)   # 0 at the rim
            ins[y][x] = rgba(inner_ramp[max(0, min(6, 5 - (yy * 4) // max(1, height - 1)))])
    write_png(os.path.join(A, f"textures/block/toy/{name}_inside.png"), ins)
    fl = img(16, 16)
    for y in range(16):
        for x in range(16):
            edge = min(x - i0, y - i0, i1 - 1 - x, i1 - 1 - y) == 0
            fl[y][x] = rgba(inner_ramp[0 if edge else 1])
    write_png(os.path.join(A, f"textures/block/toy/{name}_inside_bottom.png"), fl)
    facing_blockstate(name)
    item_def(name, f"craftmaster:block/toy/{name}")
    loot(name)


def target_sticker(cx, cy):
    """A 5 px sticker of the game's target block (red ring, cream ring, red middle), square rings with soft corners."""
    def fn(x, y):
        dx, dy = abs(x - cx), abs(y - cy)
        d = max(dx, dy)
        if d > 2:
            return None
        if d == 2:
            return None if dx == dy else TARGET_RED[1]
        if d == 1:
            return TARGET_CREAM[1] if dx != dy else TARGET_CREAM[0]
        return TARGET_RED[3]
    return fn


def targets():
    # Bucket: the game's bucket steel, a lit rim, a ridge, a target sticker, a wire handle (plane, both faces)
    st = target_sticker(4, 4)

    def bucket_side(x, y, w, h):
        s = st(x, y)
        if s:
            return s
        rows = [5, 3, 4, 5, 4, 4, 4, 3, 2]
        lv = rows[y]
        if y > 0 and (x == 0 or x == w - 1):
            lv -= 1
        return lv
    handle_tex = img(16, 16)
    for x in range(3, 13):
        k = (x - 7.5) / 5.0
        hy = int(round(3.6 * (1 - k * k)))
        handle_tex[6 - hy][x] = rgba(STEEL_R[3])
    write_png(os.path.join(A, "textures/block/toy/bucket_target_handle.png"), handle_tex)
    handle = box([3, 9, 8], [13, 14, 8], {"north": face("#handle", [3, 2, 13, 7]), "south": face("#handle", [13, 2, 3, 7])})
    hollow("bucket_target", 3, 13, 9, 1, bucket_side, 5, STEEL_R, INNER_R, [handle],
           extra_tex={"handle": "craftmaster:block/toy/bucket_target_handle"})

    # Bin: green painted steel (the game's green dye, toned down), vertical ribs, a lit rim, a target sticker
    BIN_R = [(int(r * 0.82), int(g * 0.82), int(b_ * 0.82)) for r, g, b_ in hue_ramp(0x5E7C16)]
    st2 = target_sticker(5, 6)

    def bin_side(x, y, w, h):
        s = st2(x, y)
        if s:
            return s
        if y == 0:
            return 5
        if y == 1:
            return 3
        lv = 4 if y < h - 3 else 3
        if y == h - 1:
            lv = 2
        if 1 <= x <= w - 2 and x % 3 == 1:
            lv += 1        # rib, lit
        elif 1 <= x <= w - 2 and x % 3 == 2:
            lv -= 1        # its shade
        if x == 0 or x == w - 1:
            lv -= 1
        return lv
    hollow("bin_target", 2, 14, 15, 1, bin_side, 5, BIN_R, INNER_R)

    # Mug: the game's quartz-white ceramic, a red band (target red), a handle (plane with a C shape)
    def mug_side(x, y, w, h):
        if y == 1:
            return TARGET_RED[1] if (x == 0 or x == w - 1) else TARGET_RED[2]
        return box_side([5, 5, 5, 4, 4, 3], strokes=False)(x, y, w, h)
    mug_handle = img(16, 16)
    for (x, y) in ((11, 11), (12, 11), (12, 12), (12, 13), (11, 14), (12, 14)):
        mug_handle[y][x] = rgba(CERAMIC_R[4] if y < 13 else CERAMIC_R[3])
    write_png(os.path.join(A, "textures/block/toy/mug_target_handle.png"), mug_handle)
    mh = box([11, 1, 8], [13, 5, 8], {"north": face("#handle", [11, 11, 13, 15]), "south": face("#handle", [13, 11, 11, 15])})
    hollow("mug_target", 5, 11, 6, 1, mug_side, 5, CERAMIC_R, TEA_R,
           [mh], disp={**block_display(), "gui": {"rotation": [30, 225, 0], "translation": [0, 2, 0], "scale": [1.1, 1.1, 1.1]}},
           extra_tex={"handle": "craftmaster:block/toy/mug_target_handle"})


def glasses():
    """Measuring glass (6×10×6) and tank (14×16×14), drawn like the game's glass: an opaque light frame on the
    edges and a couple of streaks, the rest clear (cut-out, no sorting trouble), white graduation marks on the
    left; the tank's frame is the bucket's steel. Water inside in 8 steps, the game's water blue."""
    water = img(16, 16)
    for y in range(16):
        for x in range(16):
            water[y][x] = (98, 148, 232, 205) if y == 0 else (63, 118, 228, 170)
    write_png(os.path.join(A, "textures/block/toy/measured_water.png"), water)
    for name, a, b, height, marks, frame in (("measuring_glass", 5, 11, 10, 4, False), ("measuring_tank", 1, 15, 16, 8, True)):
        w = b - a
        top_y = 16 - height
        tex = img(16, 16)
        F = STEEL_R if frame else [GLASS_R[0], GLASS_R[1], GLASS_R[2], GLASS_R[3], GLASS_R[3], GLASS_R[3], (240, 250, 250)]
        for y in range(top_y, 16):
            yy = y - top_y
            for x in range(w):
                edge_x, edge_y = x in (0, w - 1), yy in (0, height - 1)
                c = None
                if edge_x or edge_y:
                    # light on top, darker down the edges (as on the game's glass)
                    lv = 5 if yy == 0 else 2 if yy == height - 1 else (4 if yy < height // 2 else 3)
                    c = F[lv]
                elif (x == w - 3 and yy == 2) or (x == w - 2 and yy == 3):
                    c = GLASS_R[2]                         # a streak of the glass, top right
                else:
                    # Graduations: quarters long (2-3 px), eighths on the tank short (1 px), from the left frame
                    inner = height - 1
                    steps = 8 if frame else 4
                    for k in range(1, steps):
                        my = inner - round(k * (inner - 1) / steps)
                        long_mark = steps == 4 or k % 2 == 0
                        if yy == my and x <= (3 if frame and long_mark else 2 if long_mark else 1):
                            c = (242, 246, 248)
                if c is not None:
                    tex[y][x] = c if len(c) == 4 else rgba(c)
        write_png(os.path.join(A, f"textures/block/toy/{name}.png"), tex)
        g = "#glass"
        shell = box([a, 0, a], [b, height, b], {d: face(g, [0, top_y, w, 16]) for d in ("north", "south", "east", "west")}
                    | {"down": face(g, [0, top_y, w, top_y + w] if w <= height else [0, 0, w, w], cull="down")})
        inner_planes = [box([a, 0, b - 0.01], [b, height, b - 0.01], {"north": face(g, [w, top_y, 0, 16])}),
                        box([a, 0, a + 0.01], [b, height, a + 0.01], {"south": face(g, [w, top_y, 0, 16])}),
                        box([b - 0.01, 0, a], [b - 0.01, height, b], {"west": face(g, [w, top_y, 0, 16])}),
                        box([a + 0.01, 0, a], [a + 0.01, height, b], {"east": face(g, [w, top_y, 0, 16])})]
        variants = {}
        for stage in range(9):
            els = [shell] + inner_planes
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
        item_def(name, f"craftmaster:block/toy/{name}_{3 if name == 'measuring_glass' else 5}")
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
