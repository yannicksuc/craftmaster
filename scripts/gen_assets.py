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

    # Velvet texture: vertical folds (light in the middle of each pleat); the hem version adds a darker bottom band
    r0, g0, b0 = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    for suffix, hem in (("", True), ("_plain", False)):
        rnd = random.Random(color)  # same velvet grain on both, so stacked blocks match
        rows = []
        for y in range(16):
            row = []
            for x in range(16):
                fold = 0.78 + 0.30 * math.sin((x % 4 + 0.5) / 4 * math.pi)
                shade = fold * (0.97 + rnd.random() * 0.06)
                if hem and y >= 14:
                    shade *= 0.78
                row.append((min(255, int(r0 * shade)), min(255, int(g0 * shade)), min(255, int(b0 * shade)), 255))
            rows.append(row)
        write_png(os.path.join(A, f"textures/block/{name}{suffix}.png"), 16, 16, rows)

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
for wood, (_, en_name, fr_name) in WOODS.items():
    en[f"block.craftmaster.{wood}_chair"] = f"{en_name} Chair"
    fr[f"block.craftmaster.{wood}_chair"] = f"Chaise {fr_name}"
for color, (_, en_name, fr_name) in COLORS.items():
    en[f"block.craftmaster.{color}_curtain"] = f"{en_name} Curtain"
    fr[f"block.craftmaster.{color}_curtain"] = f"Rideau {fr_name}"
write_json(os.path.join(A, "lang/en_us.json"), en)
write_json(os.path.join(A, "lang/fr_fr.json"), fr)
print("ok")
