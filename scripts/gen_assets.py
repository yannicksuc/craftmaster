#!/usr/bin/env python3
"""Generates the mod's blockstates, models, item definitions, loot tables, textures and translations.

Run from the project root:  python3 scripts/gen_assets.py
Pure Python (no Pillow): the PNGs are written by hand.
"""
import json
import math
import os
import random
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
A = os.path.join(ROOT, "assets", "craftmaster")
D = os.path.join(ROOT, "data")

WOODS = {  # id: (log texture, english, french)
    "oak": ("stripped_oak_log", "Oak", "en chêne"),
    "spruce": ("stripped_spruce_log", "Spruce", "en sapin"),
    "birch": ("stripped_birch_log", "Birch", "en bouleau"),
    "jungle": ("stripped_jungle_log", "Jungle", "en acajou"),
    "acacia": ("stripped_acacia_log", "Acacia", "en acacia"),
    "dark_oak": ("stripped_dark_oak_log", "Dark Oak", "en chêne noir"),
    "mangrove": ("stripped_mangrove_log", "Mangrove", "en palétuvier"),
    "cherry": ("stripped_cherry_log", "Cherry", "en cerisier"),
    "pale_oak": ("stripped_pale_oak_log", "Pale Oak", "en chêne pâle"),
    "bamboo": ("stripped_bamboo_block", "Bamboo", "en bambou"),
    "crimson": ("stripped_crimson_stem", "Crimson", "carmin"),
    "warped": ("stripped_warped_stem", "Warped", "biscornue"),
}

COLORS = {  # DyeColor id: (rgb, english, french)
    "white": (0xF9FFFE, "White", "blanc"), "orange": (0xF9801D, "Orange", "orange"),
    "magenta": (0xC74EBD, "Magenta", "magenta"), "light_blue": (0x3AB3DA, "Light Blue", "bleu clair"),
    "yellow": (0xFED83D, "Yellow", "jaune"), "lime": (0x80C71F, "Lime", "vert clair"),
    "pink": (0xF38BAA, "Pink", "rose"), "gray": (0x474F52, "Gray", "gris"),
    "light_gray": (0x9D9D97, "Light Gray", "gris clair"), "cyan": (0x169C9C, "Cyan", "cyan"),
    "purple": (0x8932B8, "Purple", "violet"), "blue": (0x3C44AA, "Blue", "bleu"),
    "brown": (0x835432, "Brown", "marron"), "green": (0x5E7C16, "Green", "vert"),
    "red": (0xB02E26, "Red", "rouge"), "black": (0x1D1D21, "Black", "noir"),
}

FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}

# Sound events of the mod (assets/craftmaster/sounds.json, written at the end): each part adds its own entries
SOUNDS = {}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def write_png(path, w, h, pixels):
    """pixels: list of rows of (r, g, b, a)."""
    raw = b"".join(b"\x00" + b"".join(struct.pack("4B", *p) for p in row) for row in pixels)

    def chunk(t, data):
        return struct.pack(">I", len(data)) + t + data + struct.pack(">I", zlib.crc32(t + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def faces(tex, skip=()):
    return {d: {"texture": tex} for d in ("north", "south", "east", "west", "up", "down") if d not in skip}


def cube(frm, to, tex):
    return {"from": frm, "to": to, "faces": faces(tex)}


def tall_cube(frm, to, tex):
    """A cube reaching above the block (y > 16): Minecraft's automatic UVs would leave the texture there, so each
    face gets explicit UVs taken from the bottom of the texture, height for height."""
    (x1, y1, z1), (x2, y2, z2) = frm, to
    h = y2 - y1
    v1, v2 = 16 - h, 16
    return {"from": frm, "to": to, "faces": {
        "north": {"texture": tex, "uv": [16 - x2, v1, 16 - x1, v2]},
        "south": {"texture": tex, "uv": [x1, v1, x2, v2]},
        "east": {"texture": tex, "uv": [16 - z2, v1, 16 - z1, v2]},
        "west": {"texture": tex, "uv": [z1, v1, z2, v2]},
        "up": {"texture": tex, "uv": [x1, z1, x2, z2]},
        "down": {"texture": tex, "uv": [x1, z1, x2, z2]},
    }}


def self_drop(block):
    """Loot table of a block that drops itself (26.x format: one "condition" with a "type")."""
    return {"type": "minecraft:block",
            "pools": [{"condition": {"type": "minecraft:survives_explosion"}, "rolls": 1,
                       "entries": [{"type": "minecraft:item", "name": f"craftmaster:{block}"}]}],
            "random_sequence": f"craftmaster:blocks/{block}"}


# ------------------------------------------------------------------ chairs
# Facing north = the seated player looks north; the backrest is on the south side.
chair_template = {
    "parent": "minecraft:block/block",
    "textures": {"particle": "#planks"},
    "elements": [
        cube([2, 0, 2], [4, 7, 4], "#log"), cube([12, 0, 2], [14, 7, 4], "#log"),
        cube([2, 0, 12], [4, 7, 14], "#log"), cube([12, 0, 12], [14, 7, 14], "#log"),
        cube([2, 7, 2], [14, 9, 14], "#planks"),                       # seat
        tall_cube([2, 9, 12], [4, 21, 14], "#log"), tall_cube([12, 9, 12], [14, 21, 14], "#log"),  # back posts
        tall_cube([4, 12, 12.5], [12, 20, 13.5], "#planks"),           # backrest
        tall_cube([4, 20, 12], [12, 21, 14], "#log"),                  # top rail
    ],
}
write_json(os.path.join(A, "models/block/chair_template.json"), chair_template)
for wood, (log, _, _) in WOODS.items():
    name = f"{wood}_chair"
    write_json(os.path.join(A, f"models/block/{name}.json"), {
        "parent": "craftmaster:block/chair_template",
        "textures": {"planks": f"minecraft:block/{wood}_planks", "log": f"minecraft:block/{log}"}})
    write_json(os.path.join(A, f"blockstates/{name}.json"), {"variants": {
        f"facing={f}": {"model": f"craftmaster:block/{name}", **({"y": y} if y else {})} for f, y in FACING_Y.items()}})
    write_json(os.path.join(A, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"craftmaster:block/{name}"}})
    write_json(os.path.join(D, f"craftmaster/loot_table/blocks/{name}.json"), self_drop(name))
write_json(os.path.join(D, "minecraft/tags/block/mineable/axe.json"),
           {"replace": False, "values": [f"craftmaster:{w}_chair" for w in WOODS]})

# ------------------------------------------------------------------ curtains
# Facing north: fabric in the X/Y plane, 4 pleats alternating between z 6..8 and z 8..10.
pleats = []
for i in range(4):
    x1, x2 = i * 4, i * 4 + 4
    z1, z2 = (6, 8) if i % 2 == 0 else (8, 10)
    pleats.append({"from": [x1, 0, z1], "to": [x2, 16, z2], "faces": {
        "north": {"texture": "#fabric", "uv": [x1, 0, x2, 16]},
        "south": {"texture": "#fabric", "uv": [16 - x2, 0, 16 - x1, 16]},
        "east": {"texture": "#fabric", "uv": [x2 - 2 if x2 > 2 else 0, 0, x2, 16]},
        "west": {"texture": "#fabric", "uv": [x1, 0, x1 + 2, 16]},
        "up": {"texture": "#fabric", "uv": [x1, 0, x2, 2]},
        "down": {"texture": "#fabric", "uv": [x1, 14, x2, 16]},
    }})
write_json(os.path.join(A, "models/block/curtain_template.json"),
           {"parent": "minecraft:block/block", "textures": {"particle": "#fabric"}, "elements": pleats})
for color, (rgb, _, _) in COLORS.items():
    name = f"{color}_curtain"
    # Two looks: with the darker hem (lowest block of a column, and the item) and plain (blocks above another one)
    write_json(os.path.join(A, f"models/block/{name}.json"),
               {"parent": "craftmaster:block/curtain_template", "textures": {"fabric": f"craftmaster:block/{name}"}})
    write_json(os.path.join(A, f"models/block/{name}_plain.json"),
               {"parent": "craftmaster:block/curtain_template", "textures": {"fabric": f"craftmaster:block/{name}_plain"}})
    # Open: nothing drawn (the particles still use the fabric when it is broken)
    write_json(os.path.join(A, f"models/block/{name}_open.json"), {"textures": {"particle": f"craftmaster:block/{name}"}})
    variants = {}
    for f, y in FACING_Y.items():
        for is_open in ("false", "true"):
            for bottom in ("false", "true"):
                if is_open == "true":
                    m = f"craftmaster:block/{name}_open"
                else:
                    m = f"craftmaster:block/{name}" + ("" if bottom == "true" else "_plain")
                v = {"model": m}
                if y:
                    v["y"] = y
                variants[f"bottom={bottom},facing={f},open={is_open}"] = v
    write_json(os.path.join(A, f"blockstates/{name}.json"), {"variants": variants})
    write_json(os.path.join(A, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"craftmaster:block/{name}"}})
    write_json(os.path.join(D, f"craftmaster/loot_table/blocks/{name}.json"), self_drop(name))

    # --- chantier G: deeper velvet folds -------------------------------------------------------------------
    # Each 4 px pleat (the model's and the sliding renderer's pleats are 4 px wide) is painted as a fold: a dark
    # crease, the turning side, a bright crest, a softer return. A handful of tones per colour, no random grain;
    # a few 2-3 px vertical strokes one tone off break the stripes without isolated pixels. Rows 0 and 15 of the
    # plain texture match, so stacked blocks join seamlessly. The hem version adds a stitched, darker bottom band.
    r0, g0, b0 = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    # Light colours need darker shadows to show their folds, dark ones brighter crests
    lum = (0.3 * r0 + 0.59 * g0 + 0.11 * b0) / 255
    deep = 0.50 + 0.12 * (1 - lum)
    tones = [deep, 0.80, 1.12 + 0.18 * (1 - lum), 0.96]       # crease, side, crest, return (x % 4)

    def velvet(shade, lift=0.0):
        def c(v):
            v = v * shade + 255 * lift * max(0.0, shade - 1)  # very dark colours: crests lift toward white a bit
            return max(0, min(255, int(round(v))))
        return (c(r0), c(g0), c(b0), 255)

    # Vertical strokes: (x, first row, length, tone step): one tone darker/lighter, never a lone pixel
    strokes = [(2, 3, 3, -1), (6, 9, 2, -1), (10, 1, 3, -1), (14, 7, 3, -1), (3, 12, 2, 1), (11, 10, 2, 1)]
    for suffix, hem in (("", True), ("_plain", False)):
        rows = []
        for y in range(16):
            row = []
            for x in range(16):
                t = tones[x % 4]
                for sx, sy, sl, step in strokes:
                    if x == sx and sy <= y < sy + sl:
                        t *= 0.88 if step < 0 else 1.08
                if hem:
                    if y == 13:
                        t *= 0.86                                       # shadow above the hem
                    elif y >= 14:
                        t *= 0.70 if y == 15 else 0.78                  # the folded-back hem band
                        if y == 14 and x % 2 == 0:
                            t *= 1.12                                   # its stitches
                row.append(velvet(t, 0.25 if lum < 0.15 else 0.0))
            rows.append(row)
        write_png(os.path.join(A, f"textures/block/{name}{suffix}.png"), 16, 16, rows)
    # --- end chantier G ---------------------------------------------------------------------------------------

# ------------------------------------------------------------------ remote
write_json(os.path.join(A, "models/item/curtain_remote.json"),
           {"parent": "minecraft:item/handheld", "textures": {"layer0": "craftmaster:item/curtain_remote"}})
write_json(os.path.join(A, "items/curtain_remote.json"), {"model": {"type": "minecraft:model", "model": "craftmaster:item/curtain_remote"}})
T = (0, 0, 0, 0)
K = (34, 30, 40, 255)      # outline
B = (70, 64, 82, 255)      # body
L = (104, 96, 120, 255)    # body light
R = (214, 48, 48, 255)     # red button
G = (240, 196, 64, 255)    # gold button
W = (250, 240, 200, 255)   # light
art = [
    "................",
    "..........KK....",
    ".........KWK....",
    "........KLLBK...",
    ".......KLRRBK...",
    "......KLLRRBK...",
    ".....KLBBBBK....",
    "....KLGBGBK.....",
    "...KLBBBBK......",
    "..KLGBGBK.......",
    ".KLBBBBK........",
    ".KBBBBK.........",
    "..KBBK..........",
    "...KK...........",
    "................",
    "................",
]
pal = {".": T, "K": K, "B": B, "L": L, "R": R, "G": G, "W": W}
write_png(os.path.join(A, "textures/item/curtain_remote.png"), 16, 16, [[pal[c] for c in row] for row in art])

# ------------------------------------------------------------------ mod icon (128 px): a stage with red curtains
S = 32
icon = []
for y in range(S):
    row = []
    for x in range(S):
        c = (24, 18, 30, 255)                                   # dark stage
        if y >= 26:
            c = (120, 78, 44, 255) if (x + y) % 4 else (100, 64, 36, 255)  # wooden floor
        side = min(x, S - 1 - x)
        if y < 26 and side < 10 - y // 6:                      # parted curtains
            f = 0.75 + 0.3 * math.sin((x % 4 + 0.5) / 4 * math.pi)
            c = (int(176 * f), int(46 * f), int(38 * f), 255)
        if y < 4:                                               # valance
            f = 0.8 + 0.25 * math.sin((x % 4 + 0.5) / 4 * math.pi)
            c = (int(176 * f), int(46 * f), int(38 * f), 255)
        if y == 4:
            c = (240, 196, 64, 255)                             # gold trim
        if 11 <= y <= 19 and 13 <= x <= 18 and not (y == 11 and x in (13, 18)):
            c = (240, 196, 64, 255)                             # trophy cup
        if 20 <= y <= 21 and 15 <= x <= 16 or (y == 22 and 13 <= x <= 18):
            c = (200, 150, 40, 255)
        row.append(c)
    icon.append(row)
big = [[icon[y // 4][x // 4] for x in range(128)] for y in range(128)]
write_png(os.path.join(A, "icon.png"), 128, 128, big)

# ------------------------------------------------------------------ portraits (hanging golden frames)
# portrait_frame.png (128 x 64): box UVs of the frame pieces built by PortraitRenderer from
# PortraitGeometry.FRAME_BOXES (top, bottom, left, right mouldings, canvas), plus the brass hook, the string and
# the highlight glow. Box UV layout of a (w, h, d) cube at (u, v): up (u+d, v), down (u+d+w, v), side (u, v+d),
# front (u+d, v+d), other side (u+d+w, v+d), back (u+2d+w, v+d). The "front" is the side the audience sees.
FW, FH, FB, FD = 18, 24, 2, 3   # PortraitGeometry.FRAME_W, FRAME_H, BORDER, DEPTH (sized for the baby figure)
TEX = [[(0, 0, 0, 0)] * 128 for _ in range(64)]
GOLD_HI, GOLD, GOLD_MID, GOLD_LO, GOLD_DARK = (255, 236, 140), (240, 196, 64), (214, 160, 44), (170, 116, 30), (120, 78, 22)
BACK_WOOD = (92, 62, 38)
rnd = random.Random(7)


def put(x, y, c, a=255):
    TEX[y][x] = (c[0], c[1], c[2], a)


def jitter(c, amount=10):
    d = rnd.randint(-amount, amount)
    return tuple(max(0, min(255, v + d)) for v in c)


def face(u, v, w, h, painter):
    for y in range(h):
        for x in range(w):
            put(u + x, v + y, painter(x, y, w, h))


def moulding_front(kind):
    """Front of a 2 px moulding: bright outer edge, darker inner edge, a bead every 4 px on the outer edge."""
    def p(x, y, w, h):
        along, across = (x, y) if kind in ("top", "bottom") else (y, x)
        outer = {"top": y == 0, "bottom": y == h - 1, "left": x == 0, "right": x == w - 1}[kind]
        if outer:
            return GOLD_HI if along % 4 == 1 else jitter(GOLD, 6)
        return GOLD_LO if along % 4 == 3 else jitter(GOLD_MID, 6)
    return p


def gold_side(x, y, w, h):
    return jitter(GOLD_MID if (x + y) % 3 else GOLD, 8)


def gold_top(x, y, w, h):
    return jitter(GOLD, 8)


def wood_back(x, y, w, h):
    return jitter(BACK_WOOD, 8)


def box_uv(u, v, w, h, d, front, top=gold_top, side=gold_side, back=wood_back):
    face(u + d, v, w, d, top)
    face(u + d + w, v, w, d, top)
    face(u, v + d, d, h, side)
    face(u + d, v + d, w, h, front)
    face(u + d + w, v + d, d, h, side)
    face(u + 2 * d + w, v + d, w, h, back)


box_uv(0, 0, FW, FB, FD, moulding_front("top"))
box_uv(0, 5, FW, FB, FD, moulding_front("bottom"))
box_uv(0, 10, FB, FH - 2 * FB, FD, moulding_front("left"))
box_uv(10, 10, FB, FH - 2 * FB, FD, moulding_front("right"))


def velvet(x, y, w, h, folds):
    """Theatre red velvet: vertical folds catching the light, a warm spotlight glow, darker toward the edges."""
    fold = 0.78 + 0.22 * math.sin((x + 0.5) / w * folds * 2 * math.pi) ** 2
    cx, cy = (w - 1) / 2, h * 0.35
    r = math.hypot((x - cx) / w, (y - cy) / h) * 2.0
    glow = max(0.45, 1.15 - r * 0.6)
    f = fold * glow
    c = (int(min(255, 196 * f)), int(min(255, 28 * f + 6 * (f > 1))), int(min(255, 40 * f)))
    if (x * 5 + y * 3) % 13 == 0:   # velvet pile
        c = tuple(max(0, v - 12) for v in c)
    return c


def poster_red(x, y, w, h, fillet=True):
    """The poster's red velvet backdrop, framed by a thin gold fillet."""
    c = velvet(x, y, w, h, 3)
    # inner gold fillet along the moulding
    if fillet and (x == 0 or y == 0 or x == w - 1 or y == h - 1):
        c = GOLD_DARK
    return c


def canvas(x, y, w, h):
    return poster_red(x, y, w, h)


def white_back(x, y, w, h):
    """Back of the canvas: plain white primed canvas."""
    return jitter((236, 232, 222), 4)


box_uv(20, 10, FW - 2 * FB, FH - 2 * FB, 1, canvas, top=wood_back, side=wood_back, back=white_back)
# Window look (Antichamber): white outside of the box (64..68 x 0..4), red backdrop inside (64..96 x 32..64)
face(64, 0, 4, 4, white_back)
face(64, 32, 32, 32, lambda x, y, w, h: poster_red(x, y, w, h, fillet=False))

# Window look backdrop (its own 64 x 64 texture): a velvet curtain, gold fringe at the bottom
curtain = []
for y in range(64):
    row = []
    for x in range(64):
        c = velvet(x, y, 64, 64, 7)
        if y >= 58:
            c = GOLD if (x + y) % 3 else GOLD_LO
        elif y == 57:
            c = GOLD_DARK
        row.append(c + (255,))
    curtain.append(row)
write_png(os.path.join(A, "textures/entity/portrait_backdrop.png"), 64, 64, curtain)

# Brass hook (112..116 x 0..4) and string (120..121 x 0..16)
face(112, 0, 4, 4, lambda x, y, w, h: (186, 140, 60) if (x + y) % 2 else (150, 108, 44))
face(120, 0, 1, 16, lambda x, y, w, h: (226, 214, 186))   # uniform: the string reads as one straight line

# Highlight glow (96..128 x 32..64): white, fading out from the frame's edge (the quad is the frame + 7 px)
for y in range(32):
    for x in range(32):
        px = (x + 0.5) / 32 * (FW + 14) - (FW + 14) / 2
        py = (y + 0.5) / 32 * (FH + 14) - (FH + 14) / 2
        dist = math.hypot(max(0, abs(px) - FW / 2), max(0, abs(py) - FH / 2))
        a = int(255 * max(0.0, 1 - dist / 7) ** 1.6)
        TEX[32 + y][96 + x] = (255, 255, 255, a)

write_png(os.path.join(A, "textures/entity/portrait_frame.png"), 128, 64, TEX)

# Item: a small golden frame on its string, a player's head in it
write_json(os.path.join(A, "models/item/portrait.json"),
           {"parent": "minecraft:item/generated", "textures": {"layer0": "craftmaster:item/portrait"}})
write_json(os.path.join(A, "items/portrait.json"), {"model": {"type": "minecraft:model", "model": "craftmaster:item/portrait"}})
art = [
    ".......SS.......",
    "......S..S......",
    ".....S....S.....",
    "...HGGGGGGGGH...",
    "...GgggggggdG...",
    "...Gg......dG...",
    "...Gg.hhhh.dG...",
    "...Gg.hfhf.dG...",
    "...Gg.hfff.dG...",
    "...Gg..bb..dG...",
    "...Gg.bbbb.dG...",
    "...Gg.bbbb.dG...",
    "...GgddddddDG...",
    "...HGGGGGGGGH...",
    "................",
    "................",
]
pal = {".": T, "S": (226, 214, 186, 255), "H": (255, 236, 140, 255), "G": (240, 196, 64, 255),
       "g": (214, 160, 44, 255), "d": (170, 116, 30, 255), "D": (120, 78, 22, 255),
       "h": (74, 52, 34, 255), "f": (226, 172, 132, 255), "b": (40, 160, 170, 255)}
# Canvas behind the figure
pix = []
for row in art:
    line = []
    for c in row:
        line.append(pal[c] if c != "." else T)
    pix.append(line)
for y in range(5, 12):
    for x in range(5, 11):
        if pix[y][x] == T:
            pix[y][x] = (112, 26, 38, 255)
write_png(os.path.join(A, "textures/item/portrait.png"), 16, 16, pix)

# ==================================================================================================================
# TV direction (chantier E): studio camera (entity texture, item), the CM wax seal of the broadcast overlay, the
# show's theme sound event. Everything of chantier E is in this block (and its translations further down).
# ==================================================================================================================
# camera.png (64 x 32): box UVs of CameraRenderer.createLayer() — body (6,7,11) at 0,0; lens (4,4,3) at 34,0;
# hood (3,2,4) at 48,0; hub (4,2,4) at 34,8; tally (1,1,1) at 50,8; pan bar (1,1,5) at 34,15; legs (1,22,1) at 56,8.
# The model's front (lens side) is the box "front" face (u+d, v+d).
CAM = [[(0, 0, 0, 0)] * 64 for _ in range(32)]
cam_rnd = random.Random(31)
CAM_METAL = [(28, 26, 32), (40, 38, 45), (52, 50, 58), (66, 64, 74), (86, 84, 96)]   # dark → light
CAM_BRASS = [(110, 72, 22), (160, 110, 34), (206, 156, 52), (240, 196, 72), (255, 232, 150)]
CAM_WOOD = [(58, 36, 22), (78, 50, 30), (96, 64, 38)]


def cam_put(x, y, c):
    CAM[y][x] = (c[0], c[1], c[2], 255)


def cam_face(u, v, w, h, painter):
    for y in range(h):
        for x in range(w):
            cam_put(u + x, v + y, painter(x, y, w, h))


def cam_box(u, v, w, h, d, front, top, side, back, bottom=None):
    cam_face(u + d, v, w, d, top)
    cam_face(u + d + w, v, w, d, bottom or side)
    cam_face(u, v + d, d, h, side)
    cam_face(u + d, v + d, w, h, front)
    cam_face(u + d + w, v + d, d, h, side)
    cam_face(u + 2 * d + w, v + d, w, h, back)


def metal(level):
    def p(x, y, w, h):
        # Light from the top: one step brighter on the first row, darker on the last
        k = level + (1 if y == 0 else -1 if y == h - 1 else 0)
        return CAM_METAL[max(0, min(4, k))]
    return p


def body_side(x, y, w, h):
    # Black body, brass trim along the front and back edges, a brass plate in the middle (the number is drawn on it)
    if x == 0 or x == w - 1:
        return CAM_BRASS[2] if y % 3 else CAM_BRASS[1]
    if 3 <= x <= w - 4 and 2 <= y <= h - 3:
        edge = x == 3 or x == w - 4 or y == 2 or y == h - 3
        return CAM_BRASS[1] if edge else CAM_METAL[0]
    return metal(2)(x, y, w, h)


def body_top(x, y, w, h):
    if (x in (1, w - 2)) and y % 4 == 1:
        return CAM_METAL[4]   # screws
    return CAM_METAL[3] if y % 5 else CAM_METAL[2]


def body_front(x, y, w, h):
    # Around the lens: a brass bezel two pixels from the edge
    if x in (0, w - 1) or y in (0, h - 1):
        return CAM_BRASS[2]
    return metal(1)(x, y, w, h)


def body_back(x, y, w, h):
    # Operator side: a small monitor and two knobs
    if 1 <= x <= 4 and 1 <= y <= 3:
        return (40, 70, 92) if (x, y) != (1, 1) else (120, 170, 200)
    if y == 5 and x in (1, 4):
        return CAM_BRASS[3]
    return metal(1)(x, y, w, h)


def lens_front(x, y, w, h):
    # Square concentric rings: brass bezel, dark ring, glass with a highlight
    ring = min(x, y, w - 1 - x, h - 1 - y)
    if ring == 0:
        return CAM_BRASS[3] if (x + y) % 2 else CAM_BRASS[2]
    if (x, y) == (1, 1):
        return (150, 190, 230)
    return (24, 34, 58) if (x + y) % 3 else (36, 52, 86)


def lens_side(x, y, w, h):
    # Focus ring ridges across the barrel
    return CAM_BRASS[3] if x % 2 == 0 else CAM_BRASS[1] if y == h - 1 else CAM_BRASS[2]


def brass(x, y, w, h):
    return CAM_BRASS[3] if y == 0 else CAM_BRASS[2] if (x + y) % 3 else CAM_BRASS[1]


def hood(x, y, w, h):
    return CAM_METAL[1] if y > 0 else CAM_METAL[2]


def leg(x, y, w, h):
    # Walnut leg, brass foot on the last two pixels (the leg's lower end)
    if y >= h - 2:
        return CAM_BRASS[2] if y == h - 2 else CAM_BRASS[1]
    if y == 0:
        return CAM_BRASS[2]
    return CAM_WOOD[(x + y // 3) % 3] if y % 5 else CAM_WOOD[0]


def tally(x, y, w, h):
    return (236, 48, 44)


def grip(x, y, w, h):
    return CAM_BRASS[2] if x == w - 1 else (30, 28, 30) if (x + y) % 2 else (44, 40, 44)


cam_box(0, 0, 6, 7, 11, body_front, body_top, body_side, body_back, bottom=metal(0))
cam_box(34, 0, 4, 4, 3, lens_front, lens_side, lens_side, metal(0))
cam_box(48, 0, 3, 2, 4, metal(0), hood, hood, metal(1))
cam_box(34, 8, 4, 2, 4, brass, brass, brass, brass)
cam_box(50, 8, 1, 1, 1, tally, tally, tally, tally)
cam_box(34, 15, 1, 1, 5, grip, grip, grip, grip)
cam_box(56, 8, 1, 22, 1, leg, brass, leg, leg, bottom=lambda *a: CAM_BRASS[1])
write_png(os.path.join(A, "textures/entity/camera.png"), 64, 32, CAM)

# Item: the camera on its tripod, side view (lens to the left)
write_json(os.path.join(A, "models/item/camera.json"),
           {"parent": "minecraft:item/generated", "textures": {"layer0": "craftmaster:item/camera"}})
write_json(os.path.join(A, "items/camera.json"), {"model": {"type": "minecraft:model", "model": "craftmaster:item/camera"}})
art = [
    "................",
    ".......kkk..r...",
    "...kkkkKKKkkkk..",
    ".YykMMMMMMMMMMk.",
    "YgOkMmMMMMMMMMk.",
    "YoOkMmmbbbbMMMkk",
    "YgOkMmmbbbbMMMk.",
    ".YykMMMMMMMMMMk.",
    "...kkkkkYYkkkkk.",
    "........YY......",
    ".......wYYw.....",
    "......w.ww.w....",
    ".....w..ww..w...",
    "....w...ww...w..",
    "...Y....ww....Y.",
    "........YY......",
]
pal = {".": T, "k": (28, 26, 32, 255), "K": (52, 50, 58, 255), "M": (52, 50, 58, 255), "m": (70, 68, 78, 255),
       "b": (206, 156, 52, 255), "Y": (206, 156, 52, 255), "y": (160, 110, 34, 255), "g": (36, 52, 86, 255),
       "o": (150, 190, 230, 255), "O": (24, 34, 58, 255), "r": (236, 48, 44, 255), "w": (96, 64, 38, 255)}
write_png(os.path.join(A, "textures/item/camera.png"), 16, 16, [[pal[c] for c in row] for row in art])

# The CM wax seal of the overlay (32 x 32): a round blob of red wax, uneven rim, embossed ring and letters
SEAL = [[(0, 0, 0, 0)] * 32 for _ in range(32)]
WAX = [(78, 8, 16), (112, 14, 24), (150, 24, 34), (186, 44, 50), (222, 92, 88)]
LETTERS = [
    "..XXXX...XX...XX",
    ".XX..XX..XXX.XXX",
    ".XX......XX.X.XX",
    ".XX......XX...XX",
    ".XX..XX..XX...XX",
    "..XXXX...XX...XX",
]
seal_rnd = random.Random(5)
def rim_at(angle):
    # Wax spreads unevenly: a slow wobble of the edge (smooth, no stray pixels)
    return 14.6 + 0.7 * math.sin(angle * 3 + 0.5) + 0.4 * math.sin(angle * 5 + 2)



for y in range(32):
    for x in range(32):
        dx, dy = x - 15.5, y - 15.5
        r = math.hypot(dx, dy)
        edge = rim_at(math.atan2(dy, dx))
        if r > edge:
            continue
        shade = 2
        if r > edge - 1.5:                       # rolled rim: light top-left, dark bottom-right
            shade = 3 if dx + dy < 0 else 1
        elif 9.5 <= r <= 10.6:                      # embossed ring
            shade = 1 if dx + dy < 0 else 3
        if dx + dy < -14 and shade == 2:
            shade = 3
        SEAL[y][x] = WAX[shade] + (255,)
lx0, ly0 = 8, 13
for j, row in enumerate(LETTERS):
    for i, ch in enumerate(row):
        if ch == "X":
            SEAL[ly0 + j + 1][lx0 + i + 1] = WAX[0] + (255,)    # pressed-in shadow
for j, row in enumerate(LETTERS):
    for i, ch in enumerate(row):
        if ch == "X":
            SEAL[ly0 + j][lx0 + i] = WAX[4] + (255,)
write_png(os.path.join(A, "textures/gui/broadcast/seal.png"), 32, 32, SEAL)

# Sound events: the theme (scripts/gen_theme.py makes the provisional sounds/show/theme.ogg; a resource pack with
# assets/craftmaster/sounds/show/theme.ogg replaces it). Other chantiers: add their events to SOUNDS.
SOUNDS.update({
    "show.theme": {"category": "record", "subtitle": "subtitles.craftmaster.show.theme",
                   "sounds": [{"name": "craftmaster:show/theme", "stream": True}]},
})
# ==================================================================================================================


# ------------------------------------------------------------------ recipes
def shaped(name, pattern, key, result, count=1, category="misc"):
    """category: building, redstone, equipment or misc (the crafting book tabs)."""
    write_json(os.path.join(D, f"craftmaster/recipe/{name}.json"), {
        "type": "minecraft:crafting_shaped", "category": category, "pattern": pattern, "key": key,
        "result": {"id": f"craftmaster:{result}", "count": count}})


for wood in WOODS:
    # Planks for the seat and backrest, sticks for the legs: 2 chairs
    shaped(f"{wood}_chair", ["P  ", "PPP", "S S"], {"P": f"minecraft:{wood}_planks", "S": "minecraft:stick"},
           f"{wood}_chair", 2, "building")
for color in COLORS:
    # A string to hang it, two wool of the colour: 2 curtains
    shaped(f"{color}_curtain", ["T", "W", "W"], {"T": "minecraft:string", "W": f"minecraft:{color}_wool"},
           f"{color}_curtain", 2, "building")
shaped("curtain_remote", ["R", "I", "I"], {"R": "minecraft:redstone", "I": "minecraft:iron_ingot"}, "curtain_remote", 1, "redstone")
# A painting in a gold frame, with its string
# TV direction (chantier E): a studio camera, iron body, glass lens, on a stick tripod
shaped("camera", [" I ", "IGI", "S S"], {"I": "minecraft:iron_ingot", "G": "minecraft:glass", "S": "minecraft:stick"}, "camera")
shaped("portrait", [" S ", "GPG", " G "], {"S": "minecraft:string", "G": "minecraft:gold_ingot", "P": "minecraft:painting"}, "portrait")

# ------------------------------------------------------------------ translations
en = {
    "itemGroup.craftmaster": "CraftMaster",
    "item.craftmaster.curtain_remote": "Curtain Remote",
    "item.craftmaster.curtain_remote.unlinked": "Right-click a curtain to link it",
    "item.craftmaster.curtain_remote.linked": "Linked to the curtain at %s %s %s",
    "entity.craftmaster.seat": "Seat",
    "key.category.craftmaster.main": "CraftMaster",
    "key.craftmaster.ranking": "CraftMaster ranking",
    "message.craftmaster.chair_taken": "Someone is already sitting here",
    "message.craftmaster.remote_unlinked": "Right-click a curtain to link this remote",
    "message.craftmaster.remote_lost": "The linked curtain is gone",
    "message.craftmaster.curtain_opening": "Curtain opening…",
    "message.craftmaster.curtain_closing": "Curtain closing…",
    "message.craftmaster.no_server_mod": "This server does not have CraftMaster",
    "screen.craftmaster.ranking": "CraftMaster ranking",
    "screen.craftmaster.season": "Season %s",
    "screen.craftmaster.player": "Player",
    "screen.craftmaster.points": "Points",
    "screen.craftmaster.empty": "No scores yet for this season",
    "screen.craftmaster.hint": "◀ ▶ season · hover a player for the detail · K to close",
    "screen.craftmaster.hidden": " · hidden from players",
    "screen.craftmaster.other_tasks": "Other rounds",
    "screen.craftmaster.hidden_season": "Ranking hidden until the reveal",
    "screen.craftmaster.live": " · live scores",
    "screen.craftmaster.unpublished": "%s not published",
    "screen.craftmaster.published_total": "Published: %s",
    "message.craftmaster.ranking_unavailable": "No ranking to show yet",
    "show.craftmaster.manche_title": "Round %s",
    "show.craftmaster.manche_end": "Round over",
    "show.craftmaster.time_up": "Time's up!",
    "show.craftmaster.published": "Ranking updated!",
    "show.craftmaster.reveal_title": "The reveal!",
    "show.craftmaster.rank.1": "1st",
    "show.craftmaster.rank.2": "2nd",
    "show.craftmaster.rank.3": "3rd",
    "show.craftmaster.rank.n": "%sth",
    "show.craftmaster.points": "%s pts",
    "key.craftmaster.regie": "Voice control (hold and speak)",
    "hud.craftmaster.regie.listening": "● Voice control listening",
    "hud.craftmaster.regie.thinking": "⏳ Understanding%s",
    "hud.craftmaster.regie.executing": "⏳ Running%s",
    "hud.craftmaster.regie.heard": "«\u00a0%s\u00a0»",
    "hud.craftmaster.regie.undo_hint": "Say « annule » to undo",
    "hud.craftmaster.regie.nothing_done": "Nothing was done",
    "message.craftmaster.regie.not_admin": "Voice control is for organisers only",
    "message.craftmaster.regie.busy": "Voice control is still handling the previous order",
    "message.craftmaster.regie.too_short": "Too short: hold the key while you speak",
    "message.craftmaster.regie.mic_unavailable": "Microphone unavailable: %s",
    "message.craftmaster.regie.unreachable": "Voice control service unreachable (start tools/voice-regie/start.ps1)",
    "message.craftmaster.regie.no_api_key": "The voice control service has no ANTHROPIC_API_KEY",
    "message.craftmaster.regie.empty": "I heard nothing",
    "message.craftmaster.regie.service_error": "Voice control service error: %s",
    "message.craftmaster.regie.no_context": "The server did not answer (show context)",
    "message.craftmaster.regie.no_result": "The server did not confirm the actions",
}
fr = {
    "itemGroup.craftmaster": "CraftMaster",
    "item.craftmaster.curtain_remote": "Télécommande de rideaux",
    "item.craftmaster.curtain_remote.unlinked": "Clic droit sur un rideau pour la lier",
    "item.craftmaster.curtain_remote.linked": "Liée au rideau en %s %s %s",
    "entity.craftmaster.seat": "Siège",
    "key.category.craftmaster.main": "CraftMaster",
    "key.craftmaster.ranking": "Classement CraftMaster",
    "message.craftmaster.chair_taken": "Quelqu'un est déjà assis ici",
    "message.craftmaster.remote_unlinked": "Clic droit sur un rideau pour lier la télécommande",
    "message.craftmaster.remote_lost": "Le rideau lié a disparu",
    "message.craftmaster.curtain_opening": "Ouverture du rideau…",
    "message.craftmaster.curtain_closing": "Fermeture du rideau…",
    "message.craftmaster.no_server_mod": "Ce serveur n'a pas CraftMaster",
    "screen.craftmaster.ranking": "Classement CraftMaster",
    "screen.craftmaster.season": "Saison %s",
    "screen.craftmaster.player": "Joueur",
    "screen.craftmaster.points": "Points",
    "screen.craftmaster.empty": "Aucun score pour cette saison",
    "screen.craftmaster.hint": "◀ ▶ saison · survole un joueur pour le détail · K pour fermer",
    "screen.craftmaster.hidden": " · cachée aux joueurs",
    "screen.craftmaster.other_tasks": "Autres manches",
    "screen.craftmaster.hidden_season": "Classement caché jusqu'à la révélation",
    "screen.craftmaster.live": " · scores en direct",
    "screen.craftmaster.unpublished": "%s non publié",
    "screen.craftmaster.published_total": "Publié : %s",
    "message.craftmaster.ranking_unavailable": "Aucun classement à afficher pour l'instant",
    "show.craftmaster.manche_title": "Manche %s",
    "show.craftmaster.manche_end": "Fin de la manche",
    "show.craftmaster.time_up": "Temps écoulé !",
    "show.craftmaster.published": "Classement mis à jour !",
    "show.craftmaster.reveal_title": "La révélation !",
    "show.craftmaster.rank.1": "1er",
    "show.craftmaster.rank.2": "2e",
    "show.craftmaster.rank.3": "3e",
    "show.craftmaster.rank.n": "%se",
    "show.craftmaster.points": "%s pts",
    "key.craftmaster.regie": "Régie vocale (maintenir et parler)",
    "hud.craftmaster.regie.listening": "● Régie à l'écoute",
    "hud.craftmaster.regie.thinking": "⏳ Compréhension%s",
    "hud.craftmaster.regie.executing": "⏳ Exécution%s",
    "hud.craftmaster.regie.heard": "«\u00a0%s\u00a0»",
    "hud.craftmaster.regie.undo_hint": "Dis « annule » pour annuler",
    "hud.craftmaster.regie.nothing_done": "Rien n'a été fait",
    "message.craftmaster.regie.not_admin": "La régie vocale est réservée aux organisateurs",
    "message.craftmaster.regie.busy": "La régie traite encore l'ordre précédent",
    "message.craftmaster.regie.too_short": "Trop court : maintiens la touche pendant que tu parles",
    "message.craftmaster.regie.mic_unavailable": "Micro indisponible : %s",
    "message.craftmaster.regie.unreachable": "Service de régie injoignable (lance tools/voice-regie/start.ps1)",
    "message.craftmaster.regie.no_api_key": "Le service de régie n'a pas de clé ANTHROPIC_API_KEY",
    "message.craftmaster.regie.empty": "Je n'ai rien entendu",
    "message.craftmaster.regie.service_error": "Erreur du service de régie : %s",
    "message.craftmaster.regie.no_context": "Le serveur n'a pas répondu (contexte de la partie)",
    "message.craftmaster.regie.no_result": "Le serveur n'a pas confirmé les actions",
}
# Hanging portraits (chantier B)
en.update({
    "item.craftmaster.portrait": "Portrait",
    "item.craftmaster.portrait.hint": "Use it under a ceiling block to hang it",
    "entity.craftmaster.portrait": "Portrait",
    "message.craftmaster.portrait_ceiling": "Hang it under a block: use it on the underside of the ceiling",
    "message.craftmaster.portrait_saved": "Portrait updated",
    "message.craftmaster.portrait_bad_url": "Invalid skin address (http:// or https:// expected)",
    "message.craftmaster.pose_saved": "Pose “%s” saved",
    "message.craftmaster.pose_deleted": "Pose “%s” deleted",
    "message.craftmaster.no_client_mod": "Your game needs the CraftMaster mod for this screen",
    "screen.craftmaster.portrait": "Portrait",
    "screen.craftmaster.portrait.season": "Season (gallery)",
    "screen.craftmaster.portrait.follow": "Followed rank or player",
    "screen.craftmaster.portrait.by_rank": "Rank",
    "screen.craftmaster.portrait.by_player": "Player",
    "screen.craftmaster.portrait.name": "Shown name",
    "screen.craftmaster.portrait.skin": "Skin (URL)",
    "screen.craftmaster.portrait.pose": "Pose",
    "screen.craftmaster.portrait.edit_pose": "Edit…",
    "screen.craftmaster.portrait.hang": "String (blocks)",
    "screen.craftmaster.portrait.hang_hint": "auto = varies",
    "screen.craftmaster.portrait.window": "Window",
    "screen.craftmaster.portrait.window_label": "Antichamber px %",
    "screen.craftmaster.portrait.window_depth": "Depth of the window's room (px)",
    "screen.craftmaster.portrait.window_size": "Size of the window's room (% of the opening)",
    "screen.craftmaster.portrait.bad_window": "Window: depth 4 to 96 px, size 100 to 200 %",
    "screen.craftmaster.portrait.save": "Save",
    "screen.craftmaster.portrait.hint": "Empty name / skin = the player's own",
    "screen.craftmaster.portrait.bad_season": "The season must be a number",
    "screen.craftmaster.portrait.bad_rank": "The rank must be 1 or more",
    "screen.craftmaster.portrait.bad_player": "Type a player name (no spaces)",
    "screen.craftmaster.portrait.bad_hang": "String: \"auto\" or 0 to 16 blocks",
    "screen.craftmaster.pose_editor": "Pose editor",
    "screen.craftmaster.pose.figure": "Whole",
    "screen.craftmaster.pose.head": "Head",
    "screen.craftmaster.pose.body": "Body",
    "screen.craftmaster.pose.right_arm": "R. arm",
    "screen.craftmaster.pose.left_arm": "L. arm",
    "screen.craftmaster.pose.right_leg": "R. leg",
    "screen.craftmaster.pose.left_leg": "L. leg",
    "screen.craftmaster.pose.frame": "Frame",
    "screen.craftmaster.pose.rot_x": "Turn X: %s°",
    "screen.craftmaster.pose.rot_y": "Turn Y: %s°",
    "screen.craftmaster.pose.rot_z": "Turn Z: %s°",
    "screen.craftmaster.pose.scale": "Size: %s",
    "screen.craftmaster.pose.offset_x": "Move X: %s px",
    "screen.craftmaster.pose.offset_y": "Move Y: %s px",
    "screen.craftmaster.pose.offset_z": "Move Z: %s px",
    "screen.craftmaster.pose.pitch": "Lean: %s°",
    "screen.craftmaster.pose.roll": "Tilt: %s°",
    "screen.craftmaster.pose.shown": "Shown",
    "screen.craftmaster.pose.hidden": "Hidden",
    "screen.craftmaster.pose.name": "Pose name",
    "screen.craftmaster.pose.save_new": "Save as new",
    "screen.craftmaster.pose.overwrite": "Overwrite",
    "screen.craftmaster.pose.delete": "Delete",
    "screen.craftmaster.pose.load": "Start from",
    "screen.craftmaster.pose.use": "Use this pose",
})
fr.update({
    "item.craftmaster.portrait": "Tableau",
    "item.craftmaster.portrait.hint": "S'utilise sous un bloc du plafond pour l'y suspendre",
    "entity.craftmaster.portrait": "Tableau",
    "message.craftmaster.portrait_ceiling": "Un tableau se suspend : utilise-le sous le bloc du plafond",
    "message.craftmaster.portrait_saved": "Tableau mis à jour",
    "message.craftmaster.portrait_bad_url": "Adresse de skin invalide (http:// ou https:// attendu)",
    "message.craftmaster.pose_saved": "Pose « %s » enregistrée",
    "message.craftmaster.pose_deleted": "Pose « %s » supprimée",
    "message.craftmaster.no_client_mod": "Il faut le mod CraftMaster dans ton jeu pour cet écran",
    "screen.craftmaster.portrait": "Tableau",
    "screen.craftmaster.portrait.season": "Saison (galerie)",
    "screen.craftmaster.portrait.follow": "Rang ou joueur suivi",
    "screen.craftmaster.portrait.by_rank": "Rang",
    "screen.craftmaster.portrait.by_player": "Joueur",
    "screen.craftmaster.portrait.name": "Nom affiché",
    "screen.craftmaster.portrait.skin": "Skin (adresse)",
    "screen.craftmaster.portrait.pose": "Pose",
    "screen.craftmaster.portrait.edit_pose": "Éditer…",
    "screen.craftmaster.portrait.hang": "Ficelle (blocs)",
    "screen.craftmaster.portrait.hang_hint": "auto = varie",
    "screen.craftmaster.portrait.window": "Fenêtre",
    "screen.craftmaster.portrait.window_label": "Antichamber px %",
    "screen.craftmaster.portrait.window_depth": "Profondeur de la pièce derrière la fenêtre (px)",
    "screen.craftmaster.portrait.window_size": "Taille de la pièce (% de l'ouverture)",
    "screen.craftmaster.portrait.bad_window": "Fenêtre : profondeur 4 à 96 px, taille 100 à 200 %",
    "screen.craftmaster.portrait.save": "Enregistrer",
    "screen.craftmaster.portrait.hint": "Nom / skin vides = ceux du joueur",
    "screen.craftmaster.portrait.bad_season": "La saison doit être un nombre",
    "screen.craftmaster.portrait.bad_rank": "Le rang doit valoir 1 ou plus",
    "screen.craftmaster.portrait.bad_player": "Tape un pseudo (sans espace)",
    "screen.craftmaster.portrait.bad_hang": "Ficelle : « auto » ou de 0 à 16 blocs",
    "screen.craftmaster.pose_editor": "Éditeur de poses",
    "screen.craftmaster.pose.figure": "Ensemble",
    "screen.craftmaster.pose.head": "Tête",
    "screen.craftmaster.pose.body": "Corps",
    "screen.craftmaster.pose.right_arm": "Bras D",
    "screen.craftmaster.pose.left_arm": "Bras G",
    "screen.craftmaster.pose.right_leg": "Jambe D",
    "screen.craftmaster.pose.left_leg": "Jambe G",
    "screen.craftmaster.pose.frame": "Cadre",
    "screen.craftmaster.pose.rot_x": "Rotation X : %s°",
    "screen.craftmaster.pose.rot_y": "Rotation Y : %s°",
    "screen.craftmaster.pose.rot_z": "Rotation Z : %s°",
    "screen.craftmaster.pose.scale": "Taille : %s",
    "screen.craftmaster.pose.offset_x": "Décalage X : %s px",
    "screen.craftmaster.pose.offset_y": "Décalage Y : %s px",
    "screen.craftmaster.pose.offset_z": "Décalage Z : %s px",
    "screen.craftmaster.pose.pitch": "Bascule : %s°",
    "screen.craftmaster.pose.roll": "Inclinaison : %s°",
    "screen.craftmaster.pose.shown": "Visible",
    "screen.craftmaster.pose.hidden": "Caché",
    "screen.craftmaster.pose.name": "Nom de la pose",
    "screen.craftmaster.pose.save_new": "Nouvelle pose",
    "screen.craftmaster.pose.overwrite": "Écraser",
    "screen.craftmaster.pose.delete": "Supprimer",
    "screen.craftmaster.pose.load": "Partir de",
    "screen.craftmaster.pose.use": "Utiliser la pose",
})
# TV direction (chantier E)
en.update({
    "item.craftmaster.camera": "Studio Camera",
    "item.craftmaster.camera.hint": "Use it on the floor to stand a camera (directors only)",
    "entity.craftmaster.camera": "Studio Camera",
    "key.craftmaster.broadcast": "Broadcast mode (TV picture)",
    "subtitles.craftmaster.show.theme": "CraftMaster theme plays",
})
fr.update({
    "item.craftmaster.camera": "Caméra de studio",
    "item.craftmaster.camera.hint": "Pose une caméra au sol (réservé à la réalisation)",
    "entity.craftmaster.camera": "Caméra de studio",
    "key.craftmaster.broadcast": "Mode diffusion (image TV)",
    "subtitles.craftmaster.show.theme": "Générique CraftMaster",
})


# ==================================================================== chantier D: tasks, envelopes, hidden notes
# Items (envelope sealed / opened with a tinted wax layer, wax seal, note, bottle, UV lamp), the envelope lying on
# a table (one texture per wax colour, sealed / opened), the pinned note, and the GUI pieces of the envelope
# opening (scripts draw them; the screens tint the grey wax with its colour).
D_WAX = {  # WaxColor id: (rgb, english, french)
    "red": (0xB3202A, "red", "rouge"), "gold": (0xD9A426, "gold", "or"), "black": (0x26222A, "black", "noir"),
    "white": (0xE8E2D4, "white", "blanc"), "blue": (0x2F4FA8, "blue", "bleu"), "green": (0x3C7A2E, "green", "vert"),
    "purple": (0x6E3394, "purple", "violet"), "pink": (0xD9668C, "pink", "rose"), "orange": (0xD9701E, "orange", "orange"),
    "cyan": (0x1F8C8C, "cyan", "cyan"), "brown": (0x6E4424, "brown", "marron"), "gray": (0x6E6E74, "gray", "gris"),
}
D_T = (0, 0, 0, 0)
# Envelope paper (warm cream), from light to outline
D_P1, D_P2, D_P3, D_P4, D_PO = (247, 238, 214), (236, 223, 190), (220, 203, 164), (196, 176, 134), (138, 112, 74)
D_RED1, D_RED2, D_RED3 = (176, 36, 48), (138, 24, 36), (98, 14, 24)   # velvet lining of the flap
D_G1, D_G2 = (240, 196, 64), (196, 146, 40)                            # gold pinstripes
d_rnd = random.Random(42)


def d_img(w, h, c=D_T):
    return [[c for _ in range(w)] for _ in range(h)]


def d_rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def d_shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def d_rgb(i):
    return ((i >> 16) & 255, (i >> 8) & 255, i & 255)


def d_art(rows, pal):
    return [[pal[ch] if ch in pal else D_T for ch in row] for row in rows]


def d_save(path, img):
    write_png(os.path.join(A, path), len(img[0]), len(img), img)


def d_wax_value(dx, dy, r):
    """Grey value of a round wax drop at (dx, dy) from its centre: light top-left, dark bottom-right."""
    d = math.hypot(dx, dy) / r
    light = 1.0 - 0.35 * d - 0.18 * (dx + dy) / r
    return max(90, min(255, int(205 * light + 30)))


# ------------------------------------------------------------------ GUI: wax seal 64 px with an embossed "CM"
D_GLYPH = {
    "C": [".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."],
    "M": ["#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"],
}


def d_seal_img(size=64):
    img = d_img(size, size)
    c = size / 2
    rr = random.Random(5)
    # Irregular rim: the radius wobbles like poured wax
    rim = [size * 0.43 + rr.uniform(-1.6, 1.6) for _ in range(24)]
    for y in range(size):
        for x in range(size):
            dx, dy = x + 0.5 - c, y + 0.5 - c
            ang = (math.atan2(dy, dx) + math.pi) / (2 * math.pi) * 24
            i0 = int(ang) % 24
            f = ang - int(ang)
            r = rim[i0] * (1 - f) + rim[(i0 + 1) % 24] * f
            d = math.hypot(dx, dy)
            if d > r:
                continue
            v = d_wax_value(dx, dy, r)
            if d > r - 2.2:
                v = int(v * 0.72)                       # rolled edge
            elif d > r * 0.66 and d < r * 0.66 + 1.4:
                v = int(v * 0.8)                        # ring pressed by the stamp
            elif d > r * 0.66 + 1.4 and d < r * 0.66 + 2.6:
                v = min(255, int(v * 1.12))
            img[y][x] = (v, v, v, 255)
    # "CM" pressed in the middle: dark letters, a light lip under them (lit from the top left)
    scale = max(1, size // 22)
    text = "CM"
    tw = (5 * 2 + 1) * scale
    ox, oy = int(c - tw / 2), int(c - 7 * scale / 2)
    for li, ch in enumerate(text):
        for gy, row in enumerate(D_GLYPH[ch]):
            for gx, bit in enumerate(row):
                if bit != "#":
                    continue
                for sy in range(scale):
                    for sx in range(scale):
                        x = ox + (li * 6 + gx) * scale + sx
                        y = oy + gy * scale + sy
                        if img[y][x][3]:
                            v = img[y][x][0]
                            img[y][x] = (int(v * 0.6),) * 3 + (255,)
                            if y + 1 < size and img[y + 1][x][3] and img[y + 1][x][0] > v * 0.7:
                                lv = min(255, int(img[y + 1][x][0] * 1.18))
                                img[y + 1][x] = (lv, lv, lv, 255)
    return img


d_seal = d_seal_img(64)
d_save("textures/gui/task/wax_seal.png", d_seal)
# The two halves once cracked: a jagged line down the middle
crack = [32 + (1 if (y // 3) % 2 else -1) + (1 if y % 7 == 0 else 0) for y in range(64)]
left, right = d_img(64, 64), d_img(64, 64)
for y in range(64):
    for x in range(64):
        p = d_seal[y][x]
        if not p[3]:
            continue
        if x < crack[y]:
            left[y][x] = p if x < crack[y] - 1 else (int(p[0] * 0.7),) * 3 + (255,)
        elif x > crack[y]:
            right[y][x] = p if x > crack[y] + 1 else (int(p[0] * 0.7),) * 3 + (255,)
d_save("textures/gui/task/wax_half_l.png", left)
d_save("textures/gui/task/wax_half_r.png", right)

# ------------------------------------------------------------------ GUI: paper grain (tinted by the screens) and edges
paper = d_img(256, 256)
pr = random.Random(11)
blob = [[pr.uniform(-1, 1) for _ in range(17)] for _ in range(17)]   # soft large-scale unevenness
for y in range(256):
    for x in range(256):
        fx, fy = x / 16, y / 16
        x0, y0 = int(fx), int(fy)
        tx, ty = fx - x0, fy - y0
        b = (blob[y0][x0] * (1 - tx) * (1 - ty) + blob[y0][x0 + 1] * tx * (1 - ty)
             + blob[y0 + 1][x0] * (1 - tx) * ty + blob[y0 + 1][x0 + 1] * tx * ty)
        v = 246 + int(b * 4)
        paper[y][x] = (v, v - 1, v - 4, 255)
for _ in range(420):  # fibres: short light / dark streaks
    x, y, ln = pr.randrange(256), pr.randrange(256), pr.randint(3, 9)
    dv = pr.choice((-9, -6, 5))
    for i in range(ln):
        xx = (x + i) % 256
        r_, g_, b_, _ = paper[y][xx]
        paper[y][xx] = (max(0, min(255, r_ + dv)), max(0, min(255, g_ + dv)), max(0, min(255, b_ + dv)), 255)
d_save("textures/gui/task/paper.png", paper)
edges = d_img(256, 256)
for y in range(256):
    for x in range(256):
        d = min(x, y, 255 - x, 255 - y)
        wob = 3 * math.sin(x * 0.21 + y * 0.07) + 2 * math.sin(y * 0.33)
        a = min(1.0, max(0.0, 1 - (d + wob) / 22.0))
        edges[y][x] = (255, 255, 255, int(255 * a * a))
d_save("textures/gui/task/paper_edges.png", edges)


# ------------------------------------------------------------------ GUI: the envelope (128 x 80), back side
def d_paper_px(x, y, base):
    k = 1 + 0.008 * math.sin(x * 0.9 + y * 0.4) + d_rnd.uniform(-0.01, 0.01)
    return d_rgba(d_shade(base, k))


W_, H_, TIP = 128, 80, 46
inner = d_img(W_, H_)
for y in range(H_):
    for x in range(W_):
        # Inside of the envelope: darker, in shadow toward the top
        inner[y][x] = d_paper_px(x, y, d_shade(D_P3, 0.82 + 0.18 * y / H_))
for x in range(W_):
    inner[0][x] = inner[H_ - 1][x] = d_rgba(D_PO)
for y in range(H_):
    inner[y][0] = inner[y][W_ - 1] = d_rgba(D_PO)
d_save("textures/gui/task/envelope_inner.png", inner)

pocket = d_img(W_, H_)
cx, meet = W_ / 2, 40
for y in range(H_):
    for x in range(W_):
        notch = y < meet * (1 - abs(x + 0.5 - cx) / cx)
        if notch:
            continue
        # Bottom flap (lighter) over the two side flaps
        bottom = y > H_ - (H_ - meet) * (1 - abs(x + 0.5 - cx) / cx)
        base = D_P1 if bottom else D_P2
        pocket[y][x] = d_paper_px(x, y, base)
# Fold edges: the notch (shadow under its lip) and the bottom flap's two edges
for x in range(W_):
    t = abs(x + 0.5 - cx) / cx
    yn = int(meet * (1 - t))
    for k, col in ((0, D_PO), (1, D_P4)):
        if 0 <= yn + k < H_:
            pocket[yn + k][x] = d_rgba(col)
    yb = int(H_ - (H_ - meet) * (1 - t))
    if 0 <= yb < H_:
        pocket[yb][x] = d_rgba(D_P4)
        if yb - 1 >= 0 and pocket[yb - 1][x][3]:
            pocket[yb - 1][x] = d_rgba(d_shade(D_P2, 0.94))
for x in range(W_):
    pocket[H_ - 1][x] = d_rgba(D_PO)
for y in range(H_):
    for x in (0, W_ - 1):
        if pocket[y][x][3]:
            pocket[y][x] = d_rgba(D_PO)
d_save("textures/gui/task/envelope_pocket.png", pocket)

flap, flap_in = d_img(W_, 48), d_img(W_, 48)
for y in range(48):
    for x in range(W_):
        t = abs(x + 0.5 - cx) / cx
        edge = TIP * (1 - t)
        if y >= edge:
            continue
        border = edge - y < 1.6 or y == 0
        flap[y][x] = d_rgba(D_PO) if border else d_paper_px(x, y, d_shade(D_P1, 1.0 - 0.06 * y / TIP))
        if border:
            flap_in[y][x] = d_rgba(D_PO)
        elif edge - y < 5 or y < 3:
            flap_in[y][x] = d_paper_px(x, y, D_P2)          # cream margin of the lining
        else:
            stripe = (x + y) % 9 == 0
            base = D_RED1 if (x // 3 + y // 3) % 2 else D_RED2
            flap_in[y][x] = d_rgba(D_G2 if stripe else d_shade(base, 1.0 - 0.25 * y / TIP))
d_save("textures/gui/task/envelope_flap.png", flap)
# The lining is seen with the flap standing up: drawn upside down (the screens cannot mirror a quad)
d_save("textures/gui/task/envelope_flap_in.png", flap_in[::-1])


# ------------------------------------------------------------------ items
def d_item_model(name, layers):
    write_json(os.path.join(A, f"models/item/{name}.json"), {
        "parent": "minecraft:item/generated",
        "textures": {f"layer{i}": f"craftmaster:item/{t}" for i, t in enumerate(layers)}})


D_IPAL = {"o": D_PO, "1": D_P1, "2": D_P2, "3": D_P3, "4": D_P4, "r": D_RED1, "R": D_RED3, "w": (252, 250, 242),
          "l": (150, 140, 120), "g": D_G1}
envelope_rows = [
    "................",
    "................",
    "................",
    ".oooooooooooooo.",
    ".o44111111114o4.",
    ".o2441111114412.",
    ".o2224411444222.",
    ".o2222244442222.",
    ".o2222222222222.",
    ".o3322222222333.",
    ".o3333322333333.",
    ".o3333333333333.",
    ".oooooooooooooo.",
    "................",
    "................",
    "................",
]
d_save("textures/item/envelope.png", d_art(envelope_rows, {k: d_rgba(v) for k, v in D_IPAL.items()}))
envelope_open_rows = [
    "................",
    "...ooooooooo....",
    "...oRrrrrrRo....",
    "....oRrrrRo.....",
    ".oooowwwwwwoooo.",
    ".o44wllllwww4o..",
    ".o24wwwwwwww4o..",
    ".o224wlllww422o.",
    ".o2224wwww42220.",
    ".o2222244422222.",
    ".o3322222222333.",
    ".o3333322333333.",
    ".o3333333333333.",
    ".oooooooooooooo.",
    "................",
    "................",
]
envelope_open_rows = [r.replace("0", "o") for r in envelope_open_rows]
d_save("textures/item/envelope_open.png", d_art(envelope_open_rows, {k: d_rgba(v) for k, v in D_IPAL.items()}))


def d_wax_item(cxp, cyp, r, broken=False):
    img = d_img(16, 16)
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cxp, y + 0.5 - cyp
            if math.hypot(dx, dy) > r:
                continue
            if broken and abs(dx) < 0.6:
                continue
            v = d_wax_value(dx, dy, r)
            if math.hypot(dx, dy) > r - 0.9:
                v = int(v * 0.75)
            img[y][x] = (v, v, v, 255)
    return img


d_save("textures/item/envelope_wax.png", d_wax_item(8, 8.5, 2.6))
d_save("textures/item/envelope_wax_broken.png", d_wax_item(8, 9.5, 2.4, broken=True))
d_item_model("envelope", ["envelope", "envelope_wax"])
d_item_model("envelope_open", ["envelope_open", "envelope_wax_broken"])
D_TINTS = [{"type": "minecraft:constant", "value": -1},
           {"type": "minecraft:custom_model_data", "index": 0, "default": D_WAX["red"][0]}]
write_json(os.path.join(A, "items/envelope.json"), {"model": {
    "type": "minecraft:condition", "property": "minecraft:custom_model_data", "index": 0,
    "on_true": {"type": "minecraft:model", "model": "craftmaster:item/envelope_open", "tints": D_TINTS},
    "on_false": {"type": "minecraft:model", "model": "craftmaster:item/envelope", "tints": D_TINTS}}})

# Wax seal: a turned wooden handle, a brass collar, a drop of wax of its colour (tinted layer)
seal_rows = [
    "..........hH....",
    ".........hHHd...",
    "........hHHd....",
    ".......hHHd.....",
    "......hHHd......",
    ".....hHHd.......",
    "....bBBb........",
    "...bBYBb........",
    "..bBYBBb........",
    "..bBBBb.........",
    "...bbb..........",
    "................",
    "................",
    "................",
    "................",
    "................",
]
d_save("textures/item/wax_seal.png", d_art(seal_rows, {"h": d_rgba((150, 96, 56)), "H": d_rgba((118, 72, 40)), "d": d_rgba((82, 48, 26)),
                                                      "b": d_rgba((150, 104, 30)), "B": d_rgba((214, 160, 44)), "Y": d_rgba((255, 230, 140))}))
d_save("textures/item/wax_seal_wax.png", d_wax_item(11.5, 11.5, 3.6))
d_item_model("wax_seal", ["wax_seal", "wax_seal_wax"])
write_json(os.path.join(A, "items/wax_seal.json"), {"model": {"type": "minecraft:model", "model": "craftmaster:item/wax_seal", "tints": D_TINTS}})

note_rows = [
    "................",
    "................",
    "..oooooooooo....",
    "..o11111111oo...",
    "..o1lllll11o1o..",
    "..o111111111oo..",
    "..o1llllll111o..",
    "..o1111111111o..",
    "..o1lllll1111o..",
    "..o1111111111o..",
    "..o1llll11111o..",
    "..o1111111111o..",
    "..o2222222222o..",
    "..oooooooooooo..",
    "................",
    "................",
]
d_save("textures/item/note.png", d_art(note_rows, {"o": d_rgba(D_PO), "1": d_rgba(D_P1), "2": d_rgba(D_P3), "l": d_rgba((92, 80, 110))}))
d_item_model("note", ["note"])
write_json(os.path.join(A, "items/note.json"), {"model": {"type": "minecraft:model", "model": "craftmaster:item/note"}})

bottle_rows = [
    "................",
    "......cc........",
    ".....cCCc.......",
    "......kk........",
    ".....gwwg.......",
    "....g.ww.g......",
    "...g.pppp.g.....",
    "...gw1111.g.....",
    "...gw1ll1.g.....",
    "...g.1111.g.....",
    "...g.1ll1.g.....",
    "...g.pppp.g.....",
    "...g......g.....",
    "....gggggg......",
    "................",
    "................",
]
d_save("textures/item/note_bottle.png", d_art(bottle_rows, {
    "c": d_rgba((150, 104, 64)), "C": d_rgba((188, 140, 90)), "k": d_rgba((120, 80, 48)),
    "g": d_rgba((150, 200, 210), 230), "w": d_rgba((230, 250, 255), 200), "p": d_rgba(D_P4), "1": d_rgba(D_P1), "l": d_rgba((110, 90, 70))}))
d_item_model("note_bottle", ["note_bottle"])
write_json(os.path.join(A, "items/note_bottle.json"), {"model": {"type": "minecraft:model", "model": "craftmaster:item/note_bottle"}})

lamp_rows = [
    "................",
    "...........vv...",
    "..........vVVv..",
    ".........mVWVv..",
    "........mmVVv...",
    ".......mMMmv....",
    "......mMMm......",
    ".....mMMm.......",
    "....mMsm........",
    "...mMMm.........",
    "..mMMm..........",
    "..mMm...........",
    "...m............",
    "................",
    "................",
    "................",
]
d_save("textures/item/uv_lamp.png", d_art(lamp_rows, {
    "m": d_rgba((52, 46, 66)), "M": d_rgba((92, 84, 112)), "s": d_rgba((200, 60, 60)),
    "v": d_rgba((122, 76, 255)), "V": d_rgba((176, 140, 255)), "W": d_rgba((240, 230, 255))}))
d_item_model("uv_lamp", ["uv_lamp"])
write_json(os.path.join(A, "items/uv_lamp.json"), {"model": {"type": "minecraft:model", "model": "craftmaster:item/uv_lamp"}})


# ------------------------------------------------------------------ blocks: envelope on a table, pinned note
def d_envelope_top(wax, opened):
    """16 x 16 top texture; the envelope covers x 2..13, y 4..11 (the element's area), facing north."""
    img = d_img(16, 16)
    waxc = d_rgb(D_WAX[wax][0])
    x0, y0, w, h = 2, 4, 12, 8
    for y in range(h):
        for x in range(w):
            t = abs(x + 0.5 - w / 2) / (w / 2)
            base = D_P2 if y < 3 else D_P3 if y > 5 else D_P2
            img[y0 + y][x0 + x] = d_rgba(base)
            if x in (0, w - 1) or y in (0, h - 1):
                img[y0 + y][x0 + x] = d_rgba(D_PO)
            elif not opened and abs(y - 4.2 * (1 - t)) < 0.6:
                img[y0 + y][x0 + x] = d_rgba(D_P4)       # edge of the closed flap
    if opened:
        # Notch open: the card inside shows, the wax broken in two
        for x in range(4, 10):
            img[y0 + 1][x0 + x] = d_rgba((252, 250, 242))
            img[y0 + 2][x0 + x] = d_rgba((252, 250, 242)) if 5 <= x <= 8 else img[y0 + 2][x0 + x]
        for (x, y) in ((5, 4), (8, 4)):
            img[y0 + y][x0 + x] = d_rgba(waxc)
    else:
        for (x, y, k) in ((5, 3, 1.15), (6, 3, 1.0), (5, 4, 0.9), (6, 4, 0.75)):
            img[y0 + y][x0 + x] = d_rgba(d_shade(waxc, k))
    return img


env_model = {"parent": "minecraft:block/block", "textures": {"particle": "#top"}, "elements": [{
    "from": [2, 0, 4], "to": [14, 1, 12], "faces": {
        "up": {"texture": "#top", "uv": [2, 4, 14, 12]},
        "down": {"texture": "#top", "uv": [2, 4, 14, 12]},
        "north": {"texture": "#top", "uv": [2, 4, 14, 5]}, "south": {"texture": "#top", "uv": [2, 11, 14, 12]},
        "east": {"texture": "#top", "uv": [2, 4, 10, 5]}, "west": {"texture": "#top", "uv": [2, 4, 10, 5]}}}]}
write_json(os.path.join(A, "models/block/envelope_template.json"), env_model)
env_variants = {}
for wax in D_WAX:
    for opened in (False, True):
        name = f"envelope_{'open_' if opened else ''}{wax}"
        d_save(f"textures/block/{name}.png", d_envelope_top(wax, opened))
        write_json(os.path.join(A, f"models/block/{name}.json"),
                   {"parent": "craftmaster:block/envelope_template", "textures": {"top": f"craftmaster:block/{name}"}})
        for f, yrot in FACING_Y.items():
            env_variants[f"facing={f},open={'true' if opened else 'false'},wax={wax}"] = {
                "model": f"craftmaster:block/{name}", **({"y": yrot} if yrot else {})}
write_json(os.path.join(A, "blockstates/envelope_block.json"), {"variants": env_variants})

pinned_rows = [
    "................",
    "................",
    "................",
    "...oooooRooo....",
    "...o111111lo....",
    "...o1lllll1o....",
    "...o111111lo....",
    "...o1llll11o....",
    "...o11111l1o....",
    "...o1lll111o....",
    "...o1111111o....",
    "...o2222222o....",
    "...ooooooooo....",
    "................",
    "................",
    "................",
]
d_save("textures/block/pinned_note.png", d_art(pinned_rows, {
    "o": d_rgba(D_PO), "1": d_rgba(D_P1), "2": d_rgba(D_P3), "l": d_rgba((92, 80, 110)), "R": d_rgba((200, 40, 40))}))
write_json(os.path.join(A, "models/block/pinned_note.json"), {
    "parent": "minecraft:block/block", "textures": {"particle": "craftmaster:block/pinned_note", "note": "craftmaster:block/pinned_note"},
    "elements": [{"from": [3, 3, 15], "to": [13, 13, 16], "faces": {
        "north": {"texture": "#note", "uv": [3, 3, 13, 13]},
        "south": {"texture": "#note", "uv": [13, 3, 3, 13]},
        "up": {"texture": "#note", "uv": [3, 3, 13, 4]}, "down": {"texture": "#note", "uv": [3, 12, 13, 13]},
        "east": {"texture": "#note", "uv": [3, 3, 4, 13]}, "west": {"texture": "#note", "uv": [12, 3, 13, 13]}}}]})
write_json(os.path.join(A, "blockstates/pinned_note.json"), {"variants": {
    "facing=north": {"model": "craftmaster:block/pinned_note"},
    "facing=east": {"model": "craftmaster:block/pinned_note", "y": 90},
    "facing=south": {"model": "craftmaster:block/pinned_note", "y": 180},
    "facing=west": {"model": "craftmaster:block/pinned_note", "y": 270},
    "facing=up": {"model": "craftmaster:block/pinned_note", "x": 270},
    "facing=down": {"model": "craftmaster:block/pinned_note", "x": 90}}})

# Recipes of the organisers' tools (the envelopes come from the seal, the notes from /craftMasterNote)
shaped("wax_seal", ["  G", " S ", "H  "], {"G": "minecraft:gold_ingot", "S": "minecraft:stick", "H": "minecraft:honeycomb"}, "wax_seal", 1, "equipment")
shaped("uv_lamp", ["  A", " I ", "I  "], {"A": "minecraft:amethyst_shard", "I": "minecraft:iron_ingot"}, "uv_lamp", 1, "equipment")

D_EN = {
    "item.craftmaster.envelope": "Sealed Envelope",
    "item.craftmaster.envelope.opened": "Opened Envelope",
    "item.craftmaster.envelope.to": "To: %s",
    "item.craftmaster.envelope.round": "Task of round: %s",
    "item.craftmaster.envelope.wax": "Wax: %s",
    "item.craftmaster.envelope.hint": "Right-click to break the seal",
    "item.craftmaster.envelope.hint_open": "Right-click to read the card again",
    "item.craftmaster.envelope.hint_place": "Crouch + right-click on a table to lay it there",
    "block.craftmaster.envelope_block": "Envelope",
    "block.craftmaster.pinned_note": "Pinned Note",
    "item.craftmaster.wax_seal": "Wax Seal",
    "item.craftmaster.wax_seal.wax": "Last wax: %s",
    "item.craftmaster.wax_seal.hint": "Organisers: right-click to seal envelopes",
    "item.craftmaster.note": "Note",
    "item.craftmaster.note.tool": "Hiding tool (organisers)",
    "item.craftmaster.note.hint_hide": "Right-click a block: hide it there (carpet, bookshelf, chest, pot…)",
    "item.craftmaster.note.hint_pin": "Crouch + right-click: pin it on that face",
    "item.craftmaster.note.hint_remove": "Left-click a hiding place: remove it",
    "item.craftmaster.note.hint_edit": "Right-click in the air: edit the note",
    "item.craftmaster.note.hint_read": "Right-click to read",
    "item.craftmaster.note_bottle": "Message in a Bottle",
    "item.craftmaster.note_bottle.hint": "Right-click to uncork",
    "item.craftmaster.uv_lamp": "UV Lamp",
    "item.craftmaster.uv_lamp.hint": "Hold it while reading: invisible ink shows up",
    "key.craftmaster.task": "Current task",
    "screen.craftmaster.paper": "Card",
    "paper.craftmaster.round": "Round %s · %s",
    "paper.craftmaster.to": "To %s",
    "paper.craftmaster.rules": "Rules",
    "paper.craftmaster.duration": "⌛ Time: %s",
    "paper.craftmaster.secret": "Task still secret",
    "paper.craftmaster.secret_hint": "It is revealed when the envelope is opened or the round starts.",
    "paper.craftmaster.blank": "The sheet looks blank…",
    "paper.craftmaster.missing_piece": "— piece %s missing —",
    "paper.craftmaster.found": "Found %s",
    "paper.craftmaster.piece": "piece %s/%s",
    "paper.craftmaster.preview": "Preview",
    "note.craftmaster.where.under": "under the carpet",
    "note.craftmaster.where.book": "in a book",
    "note.craftmaster.where.behind": "behind the painting",
    "note.craftmaster.where.false_bottom": "in a false bottom",
    "note.craftmaster.where.inside": "hidden inside",
    "note.craftmaster.where.pinned": "pinned out of sight",
    "note.craftmaster.where.bottle": "in a bottle",
    "note.craftmaster.where.given": "handed over",
    "note.craftmaster.where.prop": "with a task prop",
    "note.craftmaster.taken_by": "taken by %s",
    "note.craftmaster.look.paper": "Paper",
    "note.craftmaster.look.parchment": "Parchment",
    "note.craftmaster.look.notebook": "Notebook",
    "note.craftmaster.look.postit": "Sticky note",
    "note.craftmaster.readers.all": "Everybody",
    "note.craftmaster.readers.cast": "Contestants",
    "note.craftmaster.readers.players": "Players…",
    "note.craftmaster.readers.all.tip": "Anyone can find and read it",
    "note.craftmaster.readers.cast.tip": "Only the season's contestants: for the others the hiding place stays ordinary",
    "note.craftmaster.readers.players.tip": "Only the players listed on the right",
    "message.craftmaster.envelope_taken": "Envelope picked up: right-click to open it",
    "message.craftmaster.envelope_taken_open": "Opened envelope picked up",
    "message.craftmaster.envelope_empty": "This envelope is empty (its round no longer has a task)",
    "message.craftmaster.envelope_opened": "✉ %s opens their task",
    "message.craftmaster.envelope_received": "✉ You receive a sealed envelope",
    "message.craftmaster.task_revealed": "✉ The task of round %s (%s) is revealed!",
    "message.craftmaster.no_task": "No task right now",
    "message.craftmaster.admin_only": "For organisers only",
    "message.craftmaster.round_not_found": "Round “%s” not found",
    "message.craftmaster.seal_empty": "Write a title or a text first",
    "message.craftmaster.sealed": "%s envelope(s) sealed (%s), %s wax",
    "message.craftmaster.note_unreadable": "Some writing you cannot make out…",
    "message.craftmaster.note_gone": "This note no longer exists",
    "message.craftmaster.note_bottle_stuck": "The cork will not budge…",
    "message.craftmaster.note_found.under": "You lift the carpet… a note was slipped under it!",
    "message.craftmaster.note_found.book": "You pull out a book… a note slides from its pages!",
    "message.craftmaster.note_found.behind": "You tilt the painting… a note was hidden behind it!",
    "message.craftmaster.note_found.false_bottom": "You feel the bottom… a false bottom! A note was hidden there.",
    "message.craftmaster.note_found.inside": "You search… and find a note!",
    "message.craftmaster.note_found.pinned": "A note, pinned out of sight!",
    "message.craftmaster.note_found.bottle": "You uncork the bottle… a message!",
    "message.craftmaster.note_found_admin": "%s found “%s” (%s)",
    "message.craftmaster.note_hidden": "Note “%s” hidden: %s (%s)",
    "message.craftmaster.note_how.under": "To find it: crouch + right-click the block",
    "message.craftmaster.note_how.book": "To find it: right-click the bookshelf",
    "message.craftmaster.note_how.behind": "To find it: right-click the painting",
    "message.craftmaster.note_how.false_bottom": "To find it: crouch + right-click the container (opened normally, it looks empty)",
    "message.craftmaster.note_how.inside": "To find it: right-click the block",
    "message.craftmaster.note_how.pinned": "To find it: right-click the sheet",
    "message.craftmaster.note_unhidden": "%s hiding place(s) removed",
    "message.craftmaster.note_no_spot": "No hiding place here",
    "message.craftmaster.note_no_room": "No room to pin the note on that face",
    "message.craftmaster.note_pinned_here": "A note is already pinned here",
    "message.craftmaster.note_already_here": "This note is already hidden here",
    "message.craftmaster.note_unknown": "Unknown note “%s”",
    "message.craftmaster.note_created": "Note “%s” created (id %s): its hiding tool is in your inventory",
    "message.craftmaster.note_saved": "Note “%s” saved",
    "message.craftmaster.note_tool_given": "Hiding tool of “%s” given",
    "message.craftmaster.note_bottled": "“%s” put in a bottle",
    "message.craftmaster.note_reset": "“%s”: finds forgotten, taken notes back in place",
    "message.craftmaster.note_unhidden_all": "“%s”: every hiding place removed",
    "message.craftmaster.note_deleted": "Note “%s” deleted",
    "message.craftmaster.twitch_status": "Twitch chat: %s",
    "screen.craftmaster.task_editor": "Tasks",
    "screen.craftmaster.task.title": "Title",
    "screen.craftmaster.task.title_hint": "A short title (“The highest tower”)",
    "screen.craftmaster.task.text": "Text",
    "screen.craftmaster.task.text_hint": "The task as read on the card. A blank line starts a new paragraph.",
    "screen.craftmaster.task.rules": "Rules (one per line)",
    "screen.craftmaster.task.rules_hint": "Optional: shown as bullets",
    "screen.craftmaster.task.seconds": "Time shown",
    "screen.craftmaster.task.seconds_label": "Time",
    "screen.craftmaster.task.seconds.tip": "Duration written on the card, in seconds or m:ss. Empty = the round's timer.",
    "screen.craftmaster.task.on_start": "Revealed at the start",
    "screen.craftmaster.task.on_envelope": "Envelope only",
    "screen.craftmaster.task.on_start.tip": "Starting the round reveals the task to everybody (or an envelope opened before)",
    "screen.craftmaster.task.on_envelope.tip": "Only an opened envelope (or your “Reveal”) reveals it",
    "screen.craftmaster.task.save": "Save",
    "screen.craftmaster.task.preview": "Preview",
    "screen.craftmaster.task.preview.tip": "Plays the envelope opening with the text as typed (only on your screen)",
    "screen.craftmaster.task.reveal": "Reveal to all",
    "screen.craftmaster.task.reveal.tip": "Everybody (spectators, broadcast) can now read it",
    "screen.craftmaster.task.reveal_short": "Reveal",
    "screen.craftmaster.task.show_short": "On screens",
    "screen.craftmaster.task.show": "Show on screens",
    "screen.craftmaster.task.show.tip": "The task card shows on every screen for 12 s (revealed first)",
    "screen.craftmaster.task.edit": "Edit",
    "screen.craftmaster.task.edit.tip": "Open the task editor",
    "screen.craftmaster.task.envelopes": "Envelopes",
    "screen.craftmaster.task.envelopes.tip": "A sealed envelope for each online contestant, with their name",
    "screen.craftmaster.task.unsaved": "not saved",
    "screen.craftmaster.task.no_rounds": "No round in the active game: /craftMasterManche create <name>",
    "screen.craftmaster.task.state_none": "No task yet",
    "screen.craftmaster.task.state_secret": "Task written, still secret",
    "screen.craftmaster.task.state_revealed": "Task revealed",
    "screen.craftmaster.seal": "Wax seal",
    "screen.craftmaster.seal.content": "Inside",
    "screen.craftmaster.seal.free": "Free text…",
    "screen.craftmaster.seal.text_hint": "The text of the card",
    "screen.craftmaster.seal.wax": "Wax",
    "screen.craftmaster.seal.to": "To",
    "screen.craftmaster.seal.to_hint": "Name written on it (optional)",
    "screen.craftmaster.seal.count": "%s envelope(s)",
    "screen.craftmaster.seal.per_cast": "One per contestant (%s)",
    "screen.craftmaster.seal.each": "each contestant",
    "screen.craftmaster.seal.announce_on": "Opening announced",
    "screen.craftmaster.seal.announce_off": "Opened quietly",
    "screen.craftmaster.seal.announce.tip": "“X opens their task” is shown to everybody when it is opened",
    "screen.craftmaster.seal.seal": "Seal",
    "screen.craftmaster.note_editor": "Note",
    "screen.craftmaster.note.new": "New note",
    "screen.craftmaster.note.title_hint": "Title (also the item's name)",
    "screen.craftmaster.note.text_hint": "What the finder reads",
    "screen.craftmaster.note.look": "Look",
    "screen.craftmaster.note.readers": "Readers",
    "screen.craftmaster.note.pieces": "Pieces",
    "screen.craftmaster.note.pieces.tip": "Torn in pieces hidden apart: the text is whole once all are found",
    "screen.craftmaster.note.once_on": "Taken by the 1st",
    "screen.craftmaster.note.once_off": "Stays in place",
    "screen.craftmaster.note.once_on.tip": "The first finder takes it (it goes into their inventory); the hiding place is then empty",
    "screen.craftmaster.note.once_off.tip": "Every allowed player can find and read it where it is",
    "screen.craftmaster.note.ink": "Invisible ink",
    "screen.craftmaster.note.ink_off": "Normal ink",
    "screen.craftmaster.note.ink.tip": "The sheet looks blank unless read with the UV lamp in hand",
    "screen.craftmaster.note.players": "Players",
    "screen.craftmaster.note.players_hint": "Names, comma separated",
    "screen.craftmaster.note.create": "Create",
    "screen.craftmaster.note.tool": "Hiding tool",
    "screen.craftmaster.note.tool.tip": "Gives the note: right-click blocks to hide it",
    "screen.craftmaster.note.bottle": "In a bottle",
    "screen.craftmaster.note.bottle.tip": "Gives a message in a bottle carrying this note",
    "screen.craftmaster.note.reset": "Reset",
    "screen.craftmaster.note.reset.tip": "Forgets the finds and puts taken notes back (new rehearsal)",
    "screen.craftmaster.note.unhide": "Remove hiding places",
    "screen.craftmaster.note.unhide.tip": "The note stays, hidden nowhere",
    "screen.craftmaster.note.delete": "Delete",
    "screen.craftmaster.note.delete_confirm": "Click again: delete",
    "screen.craftmaster.note.edit": "Edit the note",
    "screen.craftmaster.note.spots": "Hiding places (%s)",
    "screen.craftmaster.note.finds": "Found by (%s)",
    "screen.craftmaster.note.no_spot": "Hidden nowhere yet",
    "screen.craftmaster.note.no_find": "Nobody yet",
}
D_FR = {
    "item.craftmaster.envelope": "Enveloppe scellée",
    "item.craftmaster.envelope.opened": "Enveloppe ouverte",
    "item.craftmaster.envelope.to": "Pour : %s",
    "item.craftmaster.envelope.round": "Tâche de la manche : %s",
    "item.craftmaster.envelope.wax": "Cire : %s",
    "item.craftmaster.envelope.hint": "Clic droit pour briser le sceau",
    "item.craftmaster.envelope.hint_open": "Clic droit pour relire la carte",
    "item.craftmaster.envelope.hint_place": "Accroupi + clic droit sur une table pour l'y poser",
    "block.craftmaster.envelope_block": "Enveloppe",
    "block.craftmaster.pinned_note": "Note épinglée",
    "item.craftmaster.wax_seal": "Sceau à cire",
    "item.craftmaster.wax_seal.wax": "Dernière cire : %s",
    "item.craftmaster.wax_seal.hint": "Organisateurs : clic droit pour sceller des enveloppes",
    "item.craftmaster.note": "Note",
    "item.craftmaster.note.tool": "Outil de cachette (organisateurs)",
    "item.craftmaster.note.hint_hide": "Clic droit sur un bloc : l'y cacher (tapis, bibliothèque, coffre, pot…)",
    "item.craftmaster.note.hint_pin": "Accroupi + clic droit : l'épingler sur cette face",
    "item.craftmaster.note.hint_remove": "Clic gauche sur une cachette : la retirer",
    "item.craftmaster.note.hint_edit": "Clic droit dans le vide : modifier la note",
    "item.craftmaster.note.hint_read": "Clic droit pour lire",
    "item.craftmaster.note_bottle": "Bouteille à la mer",
    "item.craftmaster.note_bottle.hint": "Clic droit pour la déboucher",
    "item.craftmaster.uv_lamp": "Lampe UV",
    "item.craftmaster.uv_lamp.hint": "À tenir en lisant : l'encre invisible apparaît",
    "key.craftmaster.task": "Tâche en cours",
    "screen.craftmaster.paper": "Carte",
    "paper.craftmaster.round": "Manche %s · %s",
    "paper.craftmaster.to": "Pour %s",
    "paper.craftmaster.rules": "Règles",
    "paper.craftmaster.duration": "⌛ Durée : %s",
    "paper.craftmaster.secret": "Tâche encore secrète",
    "paper.craftmaster.secret_hint": "Elle sera révélée à l'ouverture de l'enveloppe ou au lancement de la manche.",
    "paper.craftmaster.blank": "La feuille semble vierge…",
    "paper.craftmaster.missing_piece": "— morceau %s manquant —",
    "paper.craftmaster.found": "Trouvée %s",
    "paper.craftmaster.piece": "morceau %s/%s",
    "paper.craftmaster.preview": "Aperçu",
    "note.craftmaster.where.under": "sous le tapis",
    "note.craftmaster.where.book": "dans un livre",
    "note.craftmaster.where.behind": "derrière le tableau",
    "note.craftmaster.where.false_bottom": "dans un double fond",
    "note.craftmaster.where.inside": "cachée dedans",
    "note.craftmaster.where.pinned": "épinglée à l'abri des regards",
    "note.craftmaster.where.bottle": "dans une bouteille",
    "note.craftmaster.where.given": "remise en main propre",
    "note.craftmaster.where.prop": "avec un objet d'épreuve",
    "note.craftmaster.taken_by": "prise par %s",
    "note.craftmaster.look.paper": "Papier",
    "note.craftmaster.look.parchment": "Parchemin",
    "note.craftmaster.look.notebook": "Carnet",
    "note.craftmaster.look.postit": "Post-it",
    "note.craftmaster.readers.all": "Tout le monde",
    "note.craftmaster.readers.cast": "Candidats",
    "note.craftmaster.readers.players": "Joueurs…",
    "note.craftmaster.readers.all.tip": "N'importe qui peut la trouver et la lire",
    "note.craftmaster.readers.cast.tip": "Seulement les candidats de la saison : pour les autres, la cachette reste ordinaire",
    "note.craftmaster.readers.players.tip": "Seulement les joueurs écrits à droite",
    "message.craftmaster.envelope_taken": "Enveloppe ramassée : clic droit pour l'ouvrir",
    "message.craftmaster.envelope_taken_open": "Enveloppe ouverte ramassée",
    "message.craftmaster.envelope_empty": "Cette enveloppe est vide (sa manche n'a plus de tâche)",
    "message.craftmaster.envelope_opened": "✉ %s ouvre sa tâche",
    "message.craftmaster.envelope_received": "✉ Tu reçois une enveloppe scellée",
    "message.craftmaster.task_revealed": "✉ La tâche de la manche %s (%s) est révélée !",
    "message.craftmaster.no_task": "Aucune tâche en ce moment",
    "message.craftmaster.admin_only": "Réservé aux organisateurs",
    "message.craftmaster.round_not_found": "Manche « %s » introuvable",
    "message.craftmaster.seal_empty": "Écris d'abord un titre ou un texte",
    "message.craftmaster.sealed": "%s enveloppe(s) scellée(s) (%s), cire %s",
    "message.craftmaster.note_unreadable": "Une écriture que tu n'arrives pas à déchiffrer…",
    "message.craftmaster.note_gone": "Cette note n'existe plus",
    "message.craftmaster.note_bottle_stuck": "Le bouchon ne veut pas bouger…",
    "message.craftmaster.note_found.under": "Tu soulèves le tapis… une note était glissée dessous !",
    "message.craftmaster.note_found.book": "Tu tires un livre… une note glisse d'entre les pages !",
    "message.craftmaster.note_found.behind": "Tu écartes le tableau… une note était cachée derrière !",
    "message.craftmaster.note_found.false_bottom": "Tu tâtes le fond… un double fond ! Une note y était cachée.",
    "message.craftmaster.note_found.inside": "Tu fouilles… et tu trouves une note !",
    "message.craftmaster.note_found.pinned": "Une note, épinglée à l'abri des regards !",
    "message.craftmaster.note_found.bottle": "Tu débouches la bouteille… un message !",
    "message.craftmaster.note_found_admin": "%s a trouvé « %s » (%s)",
    "message.craftmaster.note_hidden": "Note « %s » cachée : %s (%s)",
    "message.craftmaster.note_how.under": "Pour la trouver : accroupi + clic droit sur le bloc",
    "message.craftmaster.note_how.book": "Pour la trouver : clic droit sur la bibliothèque",
    "message.craftmaster.note_how.behind": "Pour la trouver : clic droit sur le tableau",
    "message.craftmaster.note_how.false_bottom": "Pour la trouver : accroupi + clic droit sur le coffre (ouvert normalement, il paraît vide)",
    "message.craftmaster.note_how.inside": "Pour la trouver : clic droit sur le bloc",
    "message.craftmaster.note_how.pinned": "Pour la trouver : clic droit sur la feuille",
    "message.craftmaster.note_unhidden": "%s cachette(s) retirée(s)",
    "message.craftmaster.note_no_spot": "Aucune cachette ici",
    "message.craftmaster.note_no_room": "Pas de place pour épingler la note sur cette face",
    "message.craftmaster.note_pinned_here": "Une note est déjà épinglée ici",
    "message.craftmaster.note_already_here": "Cette note est déjà cachée ici",
    "message.craftmaster.note_unknown": "Note « %s » inconnue",
    "message.craftmaster.note_created": "Note « %s » créée (id %s) : son outil de cachette est dans ton inventaire",
    "message.craftmaster.note_saved": "Note « %s » enregistrée",
    "message.craftmaster.note_tool_given": "Outil de cachette de « %s » donné",
    "message.craftmaster.note_bottled": "« %s » mise en bouteille",
    "message.craftmaster.note_reset": "« %s » : trouvailles oubliées, notes prises remises en place",
    "message.craftmaster.note_unhidden_all": "« %s » : toutes les cachettes retirées",
    "message.craftmaster.note_deleted": "Note « %s » supprimée",
    "message.craftmaster.twitch_status": "Chat Twitch : %s",
    "screen.craftmaster.task_editor": "Tâches",
    "screen.craftmaster.task.title": "Titre",
    "screen.craftmaster.task.title_hint": "Un titre court (« La tour la plus haute »)",
    "screen.craftmaster.task.text": "Texte",
    "screen.craftmaster.task.text_hint": "La tâche telle qu'on la lit sur la carte. Une ligne vide = nouveau paragraphe.",
    "screen.craftmaster.task.rules": "Règles (une par ligne)",
    "screen.craftmaster.task.rules_hint": "Facultatif : affichées en puces",
    "screen.craftmaster.task.seconds": "Durée affichée",
    "screen.craftmaster.task.seconds_label": "Durée",
    "screen.craftmaster.task.seconds.tip": "Durée écrite sur la carte, en secondes ou m:ss. Vide = le chrono de la manche.",
    "screen.craftmaster.task.on_start": "Révélée au lancement",
    "screen.craftmaster.task.on_envelope": "Enveloppe seulement",
    "screen.craftmaster.task.on_start.tip": "Lancer la manche révèle la tâche à tous (ou une enveloppe ouverte avant)",
    "screen.craftmaster.task.on_envelope.tip": "Seule une enveloppe ouverte (ou ton « Révéler ») la révèle",
    "screen.craftmaster.task.save": "Enregistrer",
    "screen.craftmaster.task.preview": "Aperçu",
    "screen.craftmaster.task.preview.tip": "Joue l'ouverture de l'enveloppe avec le texte tapé (sur ton écran seulement)",
    "screen.craftmaster.task.reveal": "Révéler à tous",
    "screen.craftmaster.task.reveal.tip": "Tout le monde (spectateurs, diffusion) peut maintenant la lire",
    "screen.craftmaster.task.reveal_short": "Révéler",
    "screen.craftmaster.task.show_short": "À l'écran",
    "screen.craftmaster.task.show": "Montrer à l'écran",
    "screen.craftmaster.task.show.tip": "La carte de la tâche s'affiche 12 s sur tous les écrans (révélée d'abord)",
    "screen.craftmaster.task.edit": "Modifier",
    "screen.craftmaster.task.edit.tip": "Ouvrir l'éditeur de tâches",
    "screen.craftmaster.task.envelopes": "Enveloppes",
    "screen.craftmaster.task.envelopes.tip": "Une enveloppe scellée à chaque candidat connecté, à son nom",
    "screen.craftmaster.task.unsaved": "non enregistré",
    "screen.craftmaster.task.no_rounds": "Aucune manche dans la partie active : /craftMasterManche create <nom>",
    "screen.craftmaster.task.state_none": "Pas encore de tâche",
    "screen.craftmaster.task.state_secret": "Tâche écrite, encore secrète",
    "screen.craftmaster.task.state_revealed": "Tâche révélée",
    "screen.craftmaster.seal": "Sceau à cire",
    "screen.craftmaster.seal.content": "Contenu",
    "screen.craftmaster.seal.free": "Texte libre…",
    "screen.craftmaster.seal.text_hint": "Le texte de la carte",
    "screen.craftmaster.seal.wax": "Cire",
    "screen.craftmaster.seal.to": "Pour",
    "screen.craftmaster.seal.to_hint": "Nom écrit dessus (facultatif)",
    "screen.craftmaster.seal.count": "%s enveloppe(s)",
    "screen.craftmaster.seal.per_cast": "Une par candidat (%s)",
    "screen.craftmaster.seal.each": "chaque candidat",
    "screen.craftmaster.seal.announce_on": "Ouverture annoncée",
    "screen.craftmaster.seal.announce_off": "Ouverture discrète",
    "screen.craftmaster.seal.announce.tip": "« X ouvre sa tâche » s'affiche chez tout le monde à l'ouverture",
    "screen.craftmaster.seal.seal": "Sceller",
    "screen.craftmaster.note_editor": "Note",
    "screen.craftmaster.note.new": "Nouvelle note",
    "screen.craftmaster.note.title_hint": "Titre (aussi le nom de l'objet)",
    "screen.craftmaster.note.text_hint": "Ce que lit celui qui la trouve",
    "screen.craftmaster.note.look": "Aspect",
    "screen.craftmaster.note.readers": "Lecteurs",
    "screen.craftmaster.note.pieces": "Morceaux",
    "screen.craftmaster.note.pieces.tip": "Déchirée en morceaux cachés séparément : le texte est entier quand tous sont trouvés",
    "screen.craftmaster.note.once_on": "Prise par le 1er",
    "screen.craftmaster.note.once_off": "Reste en place",
    "screen.craftmaster.note.once_on.tip": "Le premier qui la trouve la prend (elle va dans son inventaire) ; la cachette est ensuite vide",
    "screen.craftmaster.note.once_off.tip": "Chaque joueur autorisé peut la trouver et la lire sur place",
    "screen.craftmaster.note.ink": "Encre invisible",
    "screen.craftmaster.note.ink_off": "Encre normale",
    "screen.craftmaster.note.ink.tip": "La feuille paraît vierge, sauf lue avec la lampe UV en main",
    "screen.craftmaster.note.players": "Joueurs",
    "screen.craftmaster.note.players_hint": "Pseudos, séparés par des virgules",
    "screen.craftmaster.note.create": "Créer",
    "screen.craftmaster.note.tool": "Outil de cachette",
    "screen.craftmaster.note.tool.tip": "Donne la note : clic droit sur des blocs pour la cacher",
    "screen.craftmaster.note.bottle": "En bouteille",
    "screen.craftmaster.note.bottle.tip": "Donne une bouteille à la mer qui contient cette note",
    "screen.craftmaster.note.reset": "Réinitialiser",
    "screen.craftmaster.note.reset.tip": "Oublie les trouvailles et remet les notes prises en place (nouvelle répétition)",
    "screen.craftmaster.note.unhide": "Retirer les cachettes",
    "screen.craftmaster.note.unhide.tip": "La note reste, mais n'est plus cachée nulle part",
    "screen.craftmaster.note.delete": "Supprimer",
    "screen.craftmaster.note.delete_confirm": "Cliquer encore : supprimer",
    "screen.craftmaster.note.edit": "Modifier la note",
    "screen.craftmaster.note.spots": "Cachettes (%s)",
    "screen.craftmaster.note.finds": "Trouvée par (%s)",
    "screen.craftmaster.note.no_spot": "Cachée nulle part pour l'instant",
    "screen.craftmaster.note.no_find": "Personne pour l'instant",
}
for wax, (_, en_name, fr_name) in D_WAX.items():
    D_EN[f"wax.craftmaster.{wax}"] = en_name
    D_FR[f"wax.craftmaster.{wax}"] = fr_name
en.update(D_EN)
fr.update(D_FR)
# ==================================================================== end of chantier D

for wood, (_, en_name, fr_name) in WOODS.items():
    en[f"block.craftmaster.{wood}_chair"] = f"{en_name} Chair"
    fr[f"block.craftmaster.{wood}_chair"] = f"Chaise {fr_name}"
for color, (_, en_name, fr_name) in COLORS.items():
    en[f"block.craftmaster.{color}_curtain"] = f"{en_name} Curtain"
    fr[f"block.craftmaster.{color}_curtain"] = f"Rideau {fr_name}"
# --- chantier G: pose editor (3D preview, undo, mirror, shortcuts), portrait settings -------------------------
en.update({
    "screen.craftmaster.pose.pick_tip": "Edit: %s (key %s)\nor click it on the preview",
    "screen.craftmaster.pose.hide_tip": "Show or hide this part (H)",
    "screen.craftmaster.pose.save_tip": "Saves the pose under this name in the server's library (every player gets it)",
    "screen.craftmaster.pose.load_tip": "Starts from a saved pose (Ctrl+Z to come back)",
    "screen.craftmaster.pose.use_tip": "Saves the pose and gives it to this portrait",
    "screen.craftmaster.pose.tool.undo": "Undo (Ctrl+Z)",
    "screen.craftmaster.pose.tool.redo": "Redo (Ctrl+Y or Ctrl+Shift+Z)",
    "screen.craftmaster.pose.tool.mirror": "Mirror the whole pose, left ↔ right (M)",
    "screen.craftmaster.pose.tool.copy_side": "Copy this arm / leg onto the other side, mirrored (C)",
    "screen.craftmaster.pose.tool.reset": "Reset this part: straight again (R)",
    "screen.craftmaster.pose.tool.hide": "Show / hide this part (H)",
    "screen.craftmaster.pose.tool.view": "Turn the preview back to the front (Home)",
    "screen.craftmaster.pose.preview_head": "%s · wheel: %s",
    "screen.craftmaster.pose.help_short": "[?]",
    "screen.craftmaster.pose.help.1": "Click a part (or the frame) to edit it",
    "screen.craftmaster.pose.help.2": "Drag it: turn it (Shift: the third axis)",
    "screen.craftmaster.pose.help.3": "Ctrl + drag: move the whole figure",
    "screen.craftmaster.pose.help.4": "Right drag / drag the background: turn the view",
    "screen.craftmaster.pose.help.5": "Wheel or arrows: 1° on the axis shown (Shift: 5°), X Y Z: choose it",
    "screen.craftmaster.pose.help.6": "Ctrl + wheel: zoom, Home: front view",
    "screen.craftmaster.pose.help.7": "1-8: pick a part, R: reset it, H: hide it",
    "screen.craftmaster.pose.help.8": "M: mirror the pose, C: copy a limb to the other side",
    "screen.craftmaster.pose.help.9": "Ctrl+Z: undo, Ctrl+Y: redo",
    "screen.craftmaster.pose.hover": "%s: click to edit, drag to turn",
    "screen.craftmaster.portrait.skin_tip": "Address of a skin PNG: 64×64, 64×32 or HD (128, 256, 512, 1024 px). Empty: the player's own skin (and cape)",
    "screen.craftmaster.portrait.follow_tip": "Rank: shows whoever holds this rank in the season. Player: always this player",
    "screen.craftmaster.portrait.name_tip": "Name on the plaque. Empty: the player's name",
    "screen.craftmaster.portrait.pose_tip": "Click to try the poses on the portrait (live)",
    "screen.craftmaster.portrait.edit_pose_tip": "Open the pose editor",
    "screen.craftmaster.portrait.hang_tip": "Length of the string in blocks; auto = each portrait its own, so a row is not too regular",
    "screen.craftmaster.portrait.window_tip": "Window look: the figure stands in a room behind the frame",
    "screen.craftmaster.portrait.season_tip": "Season whose ranking this portrait shows",
    # Shorter labels: they ran into their fields (the tooltips now say the rest)
    "screen.craftmaster.portrait.season": "Season",
    "screen.craftmaster.portrait.window_label": "Window",
})
fr.update({
    "screen.craftmaster.pose.pick_tip": "Éditer : %s (touche %s)\nou clic dessus dans l'aperçu",
    "screen.craftmaster.pose.hide_tip": "Montrer ou cacher cette partie (H)",
    "screen.craftmaster.pose.save_tip": "Enregistre la pose sous ce nom dans la bibliothèque du serveur (tous les joueurs la reçoivent)",
    "screen.craftmaster.pose.load_tip": "Part d'une pose enregistrée (Ctrl+Z pour revenir)",
    "screen.craftmaster.pose.use_tip": "Enregistre la pose et la donne à ce tableau",
    "screen.craftmaster.pose.tool.undo": "Annuler (Ctrl+Z)",
    "screen.craftmaster.pose.tool.redo": "Refaire (Ctrl+Y ou Ctrl+Maj+Z)",
    "screen.craftmaster.pose.tool.mirror": "Miroir de toute la pose, gauche ↔ droite (M)",
    "screen.craftmaster.pose.tool.copy_side": "Copier ce bras / cette jambe de l'autre côté, en miroir (C)",
    "screen.craftmaster.pose.tool.reset": "Remettre cette partie droite (R)",
    "screen.craftmaster.pose.tool.hide": "Montrer / cacher cette partie (H)",
    "screen.craftmaster.pose.tool.view": "Remettre l'aperçu de face (Origine)",
    "screen.craftmaster.pose.preview_head": "%s · molette : %s",
    "screen.craftmaster.pose.help_short": "[?]",
    "screen.craftmaster.pose.help.1": "Clic sur une partie (ou le cadre) pour l'éditer",
    "screen.craftmaster.pose.help.2": "La tirer : la tourner (Maj : le troisième axe)",
    "screen.craftmaster.pose.help.3": "Ctrl + tirer : déplacer tout le personnage",
    "screen.craftmaster.pose.help.4": "Clic droit tiré / tirer le fond : tourner la vue",
    "screen.craftmaster.pose.help.5": "Molette ou flèches : 1° sur l'axe affiché (Maj : 5°), X Y Z : le choisir",
    "screen.craftmaster.pose.help.6": "Ctrl + molette : zoom, Origine : vue de face",
    "screen.craftmaster.pose.help.7": "1-8 : choisir une partie, R : la remettre droite, H : la cacher",
    "screen.craftmaster.pose.help.8": "M : miroir de la pose, C : copier un membre de l'autre côté",
    "screen.craftmaster.pose.help.9": "Ctrl+Z : annuler, Ctrl+Y : refaire",
    "screen.craftmaster.pose.hover": "%s : clic pour l'éditer, tirer pour la tourner",
    "screen.craftmaster.portrait.skin_tip": "Adresse d'un PNG de skin : 64×64, 64×32 ou HD (128, 256, 512, 1024 px). Vide : le skin du joueur (et sa cape)",
    "screen.craftmaster.portrait.follow_tip": "Rang : montre celui qui a ce rang dans la saison. Joueur : toujours ce joueur",
    "screen.craftmaster.portrait.name_tip": "Nom sur la plaque. Vide : le pseudo du joueur",
    "screen.craftmaster.portrait.pose_tip": "Clic pour essayer les poses sur le tableau (en direct)",
    "screen.craftmaster.portrait.edit_pose_tip": "Ouvrir l'éditeur de poses",
    "screen.craftmaster.portrait.hang_tip": "Longueur de la ficelle en blocs ; auto = chaque tableau la sienne, pour une rangée moins régulière",
    "screen.craftmaster.portrait.window_tip": "Effet fenêtre : le personnage est dans une pièce derrière le cadre",
    "screen.craftmaster.portrait.season_tip": "Saison dont ce tableau montre le classement",
    "screen.craftmaster.portrait.season": "Saison",
    "screen.craftmaster.portrait.window_label": "Fenêtre",
})
# --- end chantier G -----------------------------------------------------------------------------------------


# ------------------------------------------------------------------ chantier H: tasks (props, buzzers, judging tools,
# drawing boards, plinth, costume trunk) — everything in scripts/gen_trials.py
import gen_trials  # noqa: E402
gen_trials.generate(A, D, en, fr, write_json, write_png, self_drop, shaped, COLORS)


# ------------------------------------------------------------------ studio set (chantier F): scripts/gen_studio.py
import sys  # noqa: E402
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_studio  # noqa: E402
gen_studio.generate(sys.modules[__name__])

# ------------------------------------------------------------------ LordFinn's furniture from his server: scripts/gen_furniture.py
import gen_furniture  # noqa: E402
gen_furniture.generate(sys.modules[__name__])

if SOUNDS:
    write_json(os.path.join(A, "sounds.json"), SOUNDS)
write_json(os.path.join(A, "lang/en_us.json"), en)
write_json(os.path.join(A, "lang/fr_fr.json"), fr)
print("ok")
