#!/usr/bin/env python3
"""Assets of the wardrobe (docs/wiki/GardeRobe.md): blockstate, models, textures, loot table, recipe, texts.

Run from the project root:  python -I scripts/gen_wardrobe.py
Standalone (not called by gen_assets.py) so it never collides with the other parts; its texts go in their own lang
files (assets/craftmaster_wardrobe/lang/*.json: Minecraft merges the lang files of every namespace).
Pure Python (no Pillow). Style: Minecraft 16 px, few cubes, detail in the texture; walnut and gold like the studio.

The armoire, facing north (front = -z), two blocks high:
  lower half  plinth  [0,0,1]-[16,2,16]      body [1,2,2]-[15,16,16]   doors [1|8,2,1]-[8|15,16,2]
  upper half  body    [1,0,2]-[15,13,16]     cornice [0,13,1]-[16,16,16] doors [1|8,0,1]-[8|15,13,2]
Open: the doors swing out on their outer hinges (x 0-1 and 15-16, z -6 to 1), the body becomes 5 panels around an
inside with a gold rail and three hanging clothes; the mirror is on the front of the right door.
"""
import json
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
A = os.path.join(ROOT, "assets", "craftmaster")
L = os.path.join(ROOT, "assets", "craftmaster_wardrobe", "lang")
D = os.path.join(ROOT, "data", "craftmaster")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def write_png(path, rows):
    h, w = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + b"".join(struct.pack("4B", *p) for p in row) for row in rows)

    def chunk(t, data):
        return struct.pack(">I", len(data)) + t + data + struct.pack(">I", zlib.crc32(t + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


# ---------------------------------------------------------------- palettes (dark → light)
WALNUT = [(44, 27, 17), (60, 37, 23), (77, 48, 30), (95, 61, 38), (113, 75, 47), (132, 90, 57)]
GOLD = [(122, 78, 20), (168, 112, 28), (212, 154, 44), (240, 194, 72), (255, 228, 128)]
GLASS = [(92, 116, 132), (118, 146, 162), (146, 176, 190), (176, 204, 214), (214, 232, 238)]
INSIDE = [(30, 19, 12), (38, 24, 15), (47, 30, 19)]
CLEAR = (0, 0, 0, 0)


def c(p, i):
    i = max(0, min(len(p) - 1, i))
    r, g, b = p[i]
    return (r, g, b, 255)


def h2(x, y, s=0):
    v = (x * 73856093) ^ (y * 19349663) ^ (s * 83492791)
    v ^= v >> 13
    v = (v * 0x5DEECE66D) & 0xFFFFFFFF
    return ((v >> 8) & 0xFFFF) / 65536.0


def grain(x, y, salt, base=3):
    """Walnut with a vertical grain: long 1 px streaks (no noise), lighter at the top of the piece."""
    streak = h2(x, y // 5, salt)
    i = base
    if streak > 0.78:
        i -= 1
    elif streak < 0.12:
        i += 1
    return i


def blank(w=16, h=16, col=CLEAR):
    return [[col for _ in range(w)] for _ in range(h)]


# ---------------------------------------------------------------- textures
def side(salt, rows_top, rows_bottom):
    """A side panel: a frame (rails and stiles) around a recessed panel (one tone darker, shadow top and left)."""
    t = blank()
    for y in range(16):
        for x in range(16):
            t[y][x] = c(WALNUT, grain(x, y, salt, 3))
    for y in range(16):
        for x in range(16):
            inner = 3 <= x <= 12 and rows_top <= y <= rows_bottom
            if inner:
                t[y][x] = c(WALNUT, grain(x, y, salt + 1, 2))
                if x == 3 or y == rows_top:
                    t[y][x] = c(WALNUT, 1)
                if x == 12 or y == rows_bottom:
                    t[y][x] = c(WALNUT, 4)
    return t


def plinth():
    t = blank()
    for y in range(16):
        for x in range(16):
            t[y][x] = c(WALNUT, grain(x, y, 7, 2) - (1 if y >= 15 else 0))
    for x in range(16):
        t[0][x] = c(GOLD, 3)   # the gold line on top of the plinth (2 px high: rows 0-1 of a 2 px face)
        t[1][x] = c(WALNUT, 1)
    return t


def cornice():
    """Cornice: a moulding (light top lip, gold band, shadow), used 3 px high (rows 0-2) on the sides and front."""
    t = blank()
    for y in range(16):
        for x in range(16):
            t[y][x] = c(WALNUT, grain(x, y, 9, 3))
    for x in range(16):
        t[0][x] = c(WALNUT, 5)
        t[1][x] = c(GOLD, 3 if x % 4 else 4)
        t[2][x] = c(WALNUT, 1)
    return t


def top():
    t = blank()
    for y in range(16):
        for x in range(16):
            t[y][x] = c(WALNUT, grain(y, x, 11, 3))
    for i in range(16):
        t[0][i] = t[15][i] = t[i][0] = t[i][15] = c(WALNUT, 4)
    return t


def inside():
    t = blank()
    for y in range(16):
        for x in range(16):
            t[y][x] = c(INSIDE, 2 if h2(x, y // 4, 13) > 0.3 else 1)
    return t


# A door is 7 px wide and 27 px high (14 in the lower half, 13 in the upper one). Painted whole, then cut.
DOOR_H = 27


def door_px(x, y, kind, salt):
    """kind: 'panel' (left door front), 'mirror' (right door front), 'back' (inside face of a door)."""
    edge = x == 0 or x == 6 or y == 0 or y == DOOR_H - 1
    base = c(WALNUT, grain(x, y, salt, 3))
    if edge:
        return c(WALNUT, 4 if (x == 0 or y == 0) else 1)
    if kind == "back":
        return c(WALNUT, grain(x, y, salt, 2))
    if kind == "mirror":
        # Gold fillet around a glass from row 2 to 24, two diagonal highlights
        if 1 <= x <= 5 and 2 <= y <= 24:
            if x == 1 or x == 5 or y == 2 or y == 24:
                return c(GOLD, 3 if (x == 1 or y == 2) else 1)
            d = (x + y) % 9
            g = 2 - (y * 2 // 24)        # lighter at the top
            if d in (0, 1) and 4 <= y <= 18:
                g = 4
            elif d == 5 and 8 <= y <= 22:
                g = 3
            return c(GLASS, g + 1)
        return base
    # Panel door: two recessed panels, a gold knob near the middle on the opening side (right edge)
    for (y0, y1) in ((2, 11), (14, 24)):
        if 2 <= x <= 4 and y0 <= y <= y1:
            if x == 2 or y == y0:
                return c(WALNUT, 1)
            if x == 4 or y == y1:
                return c(WALNUT, 4)
            return c(WALNUT, grain(x, y, salt + 2, 2))
    if x == 5 and y in (12, 13):
        return c(GOLD, 4 if y == 12 else 2)
    return base


def door_texture(lower, kinds):
    """16×16: the door at x 1-8 (the viewer's right: the mirror) in columns 0-6, the door at x 8-15 (the viewer's left,
    knob towards the middle) in columns 8-14 (rows 0-13 lower, 3-15 upper)."""
    t = blank(col=c(WALNUT, 2))
    for col0, (kind, salt, mirror_x) in zip((0, 8), kinds):
        for row in range(16):
            y = row + 13 if lower else row - 3
            if not (0 <= y < DOOR_H):
                continue
            if lower and row > 13:
                continue
            for x in range(7):
                px = 6 - x if mirror_x else x
                t[row][col0 + x] = door_px(px, y, kind, salt)
    return t


def clothes():
    """Three hanging clothes, each in its own 4-column band: a red jacket, a cream shirt, a blue long coat.
    Rows 0-1: the gold hanger hook and shoulders; lighter at the top (folds as 1 px darker vertical lines)."""
    t = blank()
    bands = [((176, 46, 38), (128, 30, 26), (210, 80, 64)), ((232, 222, 196), (190, 178, 150), (250, 244, 226)),
             ((60, 68, 170), (40, 46, 120), (92, 102, 200))]
    for b, (mid, dark, light) in enumerate(bands):
        for y in range(16):
            for x in range(4):
                X = b * 4 + x
                col = mid
                if y < 3:
                    col = light
                if x in (1,) and y > 4 and y % 5 != 0:
                    col = dark
                if y == 15:
                    col = dark
                t[y][X] = (*col, 255)
            # Buttons on the jacket and the coat
            if b != 1:
                for y in (5, 8, 11):
                    t[y][b * 4 + 2] = c(GOLD, 3)
    # Columns 12-15: the rail (gold) and the hanger hooks
    for y in range(16):
        for x in range(12, 16):
            t[y][x] = c(GOLD, 3 if y < 8 else 1)
    return t


TEX = {
    "wardrobe_side_lower": side(1, 1, 13),
    "wardrobe_side_upper": side(2, 2, 14),
    "wardrobe_plinth": plinth(),
    "wardrobe_cornice": cornice(),
    "wardrobe_top": top(),
    "wardrobe_inside": inside(),
    "wardrobe_door_lower": door_texture(True, [("mirror", 4, False), ("panel", 3, False)]),
    "wardrobe_door_upper": door_texture(False, [("mirror", 4, False), ("panel", 3, False)]),
    "wardrobe_door_back_lower": door_texture(True, [("back", 5, False), ("back", 6, False)]),
    "wardrobe_door_back_upper": door_texture(False, [("back", 5, False), ("back", 6, False)]),
    "wardrobe_clothes": clothes(),
}


# ---------------------------------------------------------------- models
def face(tex, uv, rot=None):
    f = {"texture": tex, "uv": uv}
    if rot:
        f["rotation"] = rot
    return f


def box(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def side_uvs(frm, to, v_offset=0):
    """UVs of a cube's side faces from its position (texel = model pixel), v from the top of the half (y 16 = v 0)."""
    (x1, y1, z1), (x2, y2, z2) = frm, to
    v1, v2 = 16 - y2 + v_offset, 16 - y1 + v_offset
    return {"north": [16 - x2, v1, 16 - x1, v2], "south": [x1, v1, x2, v2],
            "east": [16 - z2, v1, 16 - z1, v2], "west": [z1, v1, z2, v2],
            "up": [x1, z1, x2, z2], "down": [x1, 16 - z2, x2, 16 - z1]}


def cube(frm, to, tex, only=None, skip=(), v_offset=0, per=None):
    uv = side_uvs(frm, to, v_offset)
    out = {}
    for d in ("north", "south", "east", "west", "up", "down"):
        if d in skip or (only and d not in only):
            continue
        t = (per or {}).get(d, tex)
        out[d] = face(t, uv[d])
    return box(frm, to, out)


def door(x1, x2, y1, y2, lower, opened):
    """A door: closed = 7×h×1 in front of the body; open = turned 90° outwards on its outer hinge."""
    left = x1 == 1
    h = y2 - y1
    col = [0, 7] if left else [8, 15]
    v = [0, h] if lower else [3, 3 + h]
    front = "#door_lower" if lower else "#door_upper"
    back = "#door_back_lower" if lower else "#door_back_upper"
    edge = "#side_lower" if lower else "#side_upper"
    if not opened:
        return box([x1, y1, 1], [x2, y2, 2], {
            "north": face(front, [col[0], v[0], col[1], v[1]]),
            "east": face(edge, [0, v[0], 1, v[1]]), "west": face(edge, [0, v[0], 1, v[1]]),
            "up": face(edge, [x1, 1, x2, 2]), "down": face(edge, [x1, 1, x2, 2]),
        })
    # Open: the left door sticks out on the west edge (x 0-1), the right one on the east edge (x 15-16), z -6..1.
    # Its front now faces outwards (west / east), its inside face looks into the opening.
    if left:
        return box([0, y1, -6], [1, y2, 1], {
            "west": face(front, [col[0], v[0], col[1], v[1]]),
            "east": face(back, [col[1], v[0], col[0], v[1]]),
            "north": face(edge, [0, v[0], 1, v[1]]),
            "up": face(edge, [0, 0, 1, 7]), "down": face(edge, [0, 0, 1, 7]),
        })
    return box([15, y1, -6], [16, y2, 1], {
        "east": face(front, [col[0], v[0], col[1], v[1]]),
        "west": face(back, [col[1], v[0], col[0], v[1]]),
        "north": face(edge, [0, v[0], 1, v[1]]),
        "up": face(edge, [0, 0, 1, 7]), "down": face(edge, [0, 0, 1, 7]),
    })


def body(lower, opened):
    y1, y2 = (2, 16) if lower else (0, 13)
    s = "#side_lower" if lower else "#side_upper"
    if not opened:
        return [cube([1, y1, 2], [15, y2, 16], s, skip=("north",) + (("up",) if lower else ("down",)))]
    els = [
        # Back panel, its front face is the inside
        cube([1, y1, 15], [15, y2, 16], s, per={"north": "#inside"}, skip=(("up",) if lower else ("down",))),
        # Side panels, their inner faces are the inside
        cube([1, y1, 2], [2, y2, 15], s, per={"east": "#inside"}, skip=(("up",) if lower else ("down",))),
        cube([14, y1, 2], [15, y2, 15], s, per={"west": "#inside"}, skip=(("up",) if lower else ("down",))),
    ]
    if lower:
        els.append(cube([2, 2, 2], [14, 3, 15], "#inside", skip=("down",), per={"north": s}))
    else:
        els.append(cube([2, 12, 2], [14, 13, 15], "#inside", skip=("up",), per={"north": s}))
    return els


def hanging(lower):
    """The gold rail and three clothes hanging from it (upper half), the long coat going on into the lower half."""
    if lower:
        return [box([10, 10, 7], [13, 16, 10], {
            "north": face("#clothes", [8, 9, 11, 15]), "south": face("#clothes", [8, 9, 11, 15]),
            "east": face("#clothes", [8, 9, 11, 15]), "west": face("#clothes", [8, 9, 11, 15]),
            "down": face("#clothes", [8, 15, 11, 16])})]
    els = [box([2, 10, 8], [14, 11, 9], {d: face("#clothes", [12, 0, 15, 1]) for d in ("north", "south", "up", "down")})]
    for b, (x1, x2, y1) in enumerate(((3, 6, 2), (6, 9, 3), (10, 13, 0))):
        u = b * 4
        hgt = 10 - y1
        fs = {"north": face("#clothes", [u, 0, u + 3, hgt]), "south": face("#clothes", [u, 0, u + 3, hgt]),
              "east": face("#clothes", [u, 0, u + 3, hgt]), "west": face("#clothes", [u, 0, u + 3, hgt])}
        if y1 > 0:
            fs["down"] = face("#clothes", [u, 15, u + 3, 16])
        els.append(box([x1, y1, 7], [x2, 10, 10], fs))
    return els


TEXTURES = {"particle": "craftmaster:block/wardrobe_side_lower", "side_lower": "craftmaster:block/wardrobe_side_lower",
            "side_upper": "craftmaster:block/wardrobe_side_upper", "plinth": "craftmaster:block/wardrobe_plinth",
            "cornice": "craftmaster:block/wardrobe_cornice", "top": "craftmaster:block/wardrobe_top",
            "inside": "craftmaster:block/wardrobe_inside", "clothes": "craftmaster:block/wardrobe_clothes",
            "door_lower": "craftmaster:block/wardrobe_door_lower", "door_upper": "craftmaster:block/wardrobe_door_upper",
            "door_back_lower": "craftmaster:block/wardrobe_door_back_lower",
            "door_back_upper": "craftmaster:block/wardrobe_door_back_upper"}


def half(lower, opened):
    els = []
    if lower:
        els.append(box([0, 0, 1], [16, 2, 16], {
            d: face("#plinth", uv) for d, uv in {"north": [0, 0, 16, 2], "south": [0, 0, 16, 2], "east": [1, 0, 16, 2],
                                                 "west": [0, 0, 15, 2], "up": [0, 1, 16, 16], "down": [0, 0, 16, 15]}.items()}))
        # The plinth's top shows only around the body: its face is the plinth texture, lighter edge
    else:
        els.append(box([0, 13, 1], [16, 16, 16], {
            "north": face("#cornice", [0, 0, 16, 3]), "south": face("#cornice", [0, 0, 16, 3]),
            "east": face("#cornice", [1, 0, 16, 3]), "west": face("#cornice", [0, 0, 15, 3]),
            "up": face("#top", [0, 1, 16, 16]), "down": face("#plinth", [0, 1, 16, 16])}))
    els += body(lower, opened)
    y1, y2 = (2, 16) if lower else (0, 13)
    els.append(door(1, 8, y1, y2, lower, opened))
    els.append(door(8, 15, y1, y2, lower, opened))
    if opened:
        els += hanging(lower)
    return {"parent": "minecraft:block/block", "textures": TEXTURES, "elements": els}


def item_model():
    """The item: the whole armoire in 3D (both halves, closed). Two blocks high, so it is drawn smaller than a block
    and moved down by its extra height (centre y 16 instead of 8): s = scale, translation y = -8 s."""
    lower, upper = half(True, False), half(False, False)
    els = [json.loads(json.dumps(e)) for e in lower["elements"]]
    for e in upper["elements"]:
        e = json.loads(json.dumps(e))
        e["from"][1] += 16
        e["to"][1] += 16
        els.append(e)

    def d(rot, s, ty=0.0):
        return {"rotation": rot, "translation": [0, round(-8 * s + ty, 3), 0], "scale": [s, s, s]}
    return {"parent": "minecraft:block/block", "textures": TEXTURES, "elements": els,
            "display": {"gui": d([30, 225, 0], 0.4), "ground": d([0, 0, 0], 0.2, 3), "fixed": d([0, 180, 0], 0.45),
                        "thirdperson_righthand": d([75, 45, 0], 0.3, 2.5), "thirdperson_lefthand": d([75, 45, 0], 0.3, 2.5),
                        "firstperson_righthand": d([0, 45, 0], 0.3, 2), "firstperson_lefthand": d([0, 225, 0], 0.3, 2)}}


# ---------------------------------------------------------------- texts
EN = {
    "screen.craftmaster.wardrobe.tags": "# Tags",
    "screen.craftmaster.wardrobe.tags.n": "# Tags (%s)",
    "screen.craftmaster.wardrobe.tags.tip": "Find outfits by theme: job, theme, setting, colour, kind, material, use, hobby, body part. Tick tags: the outfits having all of them are shown.",
    "screen.craftmaster.wardrobe.tags.clear": "✕ Untick all",
    "screen.craftmaster.wardrobe.tags.edit": "Tags",
    "screen.craftmaster.wardrobe.tags.edit.hint": "Its own tags: parade, round 3, red…",
    "screen.craftmaster.wardrobe.tags.save": "Save the tags",
    "screen.craftmaster.wardrobe.tags.save.tip": "The chosen outfit gets these tags (separated by commas), on top of the ones found in its names.",
    "screen.craftmaster.wardrobe.hint.tags": "Tags: %s",
    "screen.craftmaster.wardrobe.tip.tags": "Tags: %s",
    "screen.craftmaster.wardrobe.tip.restore": "Not in this wardrobe: click to put it back",
    "screen.craftmaster.wardrobe.strip": "Take all off",
    "screen.craftmaster.wardrobe.strip.tip": "Takes everything off (to make a new outfit from nothing). Your own clothes stay kept aside: My clothes gives them back.",
    "message.craftmaster.wardrobe.stripped": "Everything taken off (My clothes gives yours back)",
    "message.craftmaster.wardrobe.restored": "Back in the wardrobe: %s",
    "message.craftmaster.wardrobe.tagged": "Tags saved: %s",
    "screen.craftmaster.wardrobe.tip.worn": "Click: its outfit · right-click: take it off",
    "screen.craftmaster.wardrobe.not_here": "%s is not an outfit of this wardrobe",
    "message.craftmaster.wardrobe.taken_off": "Taken off: %s",
    "screen.craftmaster.wardrobe.auto.already": "It already holds every outfit of the game; it keeps its own list as soon as you change it.",
    "screen.craftmaster.wardrobe.type.storage.tip": "Storage: the clothes are real; putting an outfit on swaps it with what you wear. Click: make it a costume wardrobe (copies, every outfit).",
    "screen.craftmaster.wardrobe.type.show.tip": "Costumes for the show: everyone wears copies, the wardrobe never empties and your own clothes are kept aside until « My clothes ». Click: make it a storage wardrobe (real clothes, for survival; it starts empty).",
    "screen.craftmaster.wardrobe.type.storage": "Type: storage",
    "screen.craftmaster.wardrobe.type.show": "Type: costumes",
    "screen.craftmaster.wardrobe.count": "%s outfits",
    "screen.craftmaster.wardrobe.count.one": "1 outfit",
    "screen.craftmaster.wardrobe.edit": "✎ Edit",
    "screen.craftmaster.wardrobe.edit.tip": "Edit this wardrobe's outfits: add what you wear, rename, replace, delete, move. As soon as you change it, the wardrobe keeps its own list.",
    "screen.craftmaster.wardrobe.favourites": "★ Favourites",
    "screen.craftmaster.wardrobe.favourites.tip": "Only your favourite outfits (right-click an outfit to add or remove it).",
    "screen.craftmaster.wardrobe.hint.edit": "Edit mode: click an outfit to choose it",
    "screen.craftmaster.wardrobe.hint.chosen": "Chosen: %s",
    "screen.craftmaster.wardrobe.move_up": "◀",
    "screen.craftmaster.wardrobe.move_down": "▶",
    "screen.craftmaster.wardrobe.move.tip": "Moves the chosen outfit one place (left / right).",
    "screen.craftmaster.wardrobe.auto": "Back to every outfit",
    "screen.craftmaster.wardrobe.auto.tip": "The wardrobe holds every outfit of the game again (its own list is forgotten).",
    "screen.craftmaster.wardrobe.tip.choose": "Click to choose it",
    "screen.craftmaster.wardrobe.tip.wear_piece": "Click: put on only %s",
    "screen.craftmaster.wardrobe.tip.favourite": "Right-click: add to favourites",
    "screen.craftmaster.wardrobe.tip.unfavourite": "Right-click: remove from favourites",
    "screen.craftmaster.wardrobe.mix": "Mixed outfit",
    "screen.craftmaster.wardrobe.empty.favourites": "No favourite yet: right-click an outfit",
    "message.craftmaster.wardrobe.wearing_piece": "You put on: %s",
    "message.craftmaster.wardrobe.already": "Already in this wardrobe: %s",
    "message.craftmaster.wardrobe.auto": "Every outfit again (%s)",
    "block.craftmaster.wardrobe": "Wardrobe",
    "item.craftmaster.wardrobe.costume_copy": "Costume: goes back to the wardrobe",
    "screen.craftmaster.wardrobe": "Wardrobe",
    "screen.craftmaster.wardrobe.search": "Search",
    "screen.craftmaster.wardrobe.search.hint": "Search: doctor, hat…",
    "screen.craftmaster.wardrobe.back": "My clothes",
    "screen.craftmaster.wardrobe.back.tip": "Take back your own clothes (also: sneak + click the wardrobe).",
    "screen.craftmaster.wardrobe.replace": "Replace",
    "screen.craftmaster.wardrobe.replace.tip": "The chosen outfit becomes what you wear now (head, body, legs, feet).",
    "screen.craftmaster.wardrobe.rename": "Rename",
    "screen.craftmaster.wardrobe.rename.tip": "Gives the chosen outfit the name typed on the left.",
    "screen.craftmaster.wardrobe.delete": "Delete",
    "screen.craftmaster.wardrobe.delete.tip": "Removes the chosen outfit from this wardrobe (it stays in grey here: a click puts it back).",
    "screen.craftmaster.wardrobe.take_out": "Take out",
    "screen.craftmaster.wardrobe.take_out.tip": "Puts the chosen outfit's clothes in your inventory.",
    "screen.craftmaster.wardrobe.name": "Name",
    "screen.craftmaster.wardrobe.name.hint": "Outfit name",
    "screen.craftmaster.wardrobe.add": "Add my outfit",
    "screen.craftmaster.wardrobe.add.tip": "Adds what you wear now (head, body, legs, feet) as a new outfit, with the name typed on the left.",
    "screen.craftmaster.wardrobe.store": "Store my outfit",
    "screen.craftmaster.wardrobe.store.tip": "Puts what you wear away in the wardrobe as a new outfit.",
    "screen.craftmaster.wardrobe.hint": "Click: put on · click a piece: only it · right-click: ★ favourite",
    "screen.craftmaster.wardrobe.empty.show": "No outfit: put clothes on, then Edit › Add what I wear",
    "screen.craftmaster.wardrobe.empty.storage": "Empty: Edit › Store what I wear",
    "screen.craftmaster.wardrobe.no_match": "Nothing for « %s »",
    "screen.craftmaster.wardrobe.piece.head": "Head: %s",
    "screen.craftmaster.wardrobe.piece.body": "Body: %s",
    "screen.craftmaster.wardrobe.piece.legs": "Legs: %s",
    "screen.craftmaster.wardrobe.piece.feet": "Feet: %s",
    "screen.craftmaster.wardrobe.nothing": "nothing",
    "screen.craftmaster.wardrobe.tip.wear": "Click: put on the whole outfit",
    "screen.craftmaster.wardrobe.own_clothes": "My clothes",
    "screen.craftmaster.wardrobe.preview": "Preview",
    "screen.craftmaster.wardrobe.in_costume": "In costume",
    "screen.craftmaster.wardrobe.you": "You",
    "message.craftmaster.wardrobe.wearing": "You are wearing: %s",
    "message.craftmaster.wardrobe.own": "Back in your own clothes",
    "message.craftmaster.wardrobe.back": "Back in your own clothes",
    "message.craftmaster.wardrobe.nothing_back": "You are already in your own clothes",
    "message.craftmaster.wardrobe.full": "This wardrobe is full",
    "message.craftmaster.wardrobe.wear_something": "Wear the outfit first (head, body, legs, feet)",
    "message.craftmaster.wardrobe.added": "Outfit added: %s",
    "message.craftmaster.wardrobe.replaced": "Outfit replaced: %s",
    "message.craftmaster.wardrobe.deleted": "Outfit deleted: %s",
    "message.craftmaster.wardrobe.taken_out": "Clothes taken out: %s",
    "message.craftmaster.wardrobe.catalogue_added": "Ready outfit saved: %s",
    "message.craftmaster.wardrobe.catalogue_removed": "No longer a ready outfit: %s",
    "message.craftmaster.wardrobe.catalogue_missing": "%s is not a ready outfit",
}
FR = {
    "screen.craftmaster.wardrobe.tags": "# Tags",
    "screen.craftmaster.wardrobe.tags.n": "# Tags (%s)",
    "screen.craftmaster.wardrobe.tags.tip": "Trouver des tenues par thème : métier, thème, contexte, couleur, type, matière, utilité, loisir, partie du corps. Coche des tags : les tenues qui les ont tous s'affichent.",
    "screen.craftmaster.wardrobe.tags.clear": "✕ Tout décocher",
    "screen.craftmaster.wardrobe.tags.edit": "Tags",
    "screen.craftmaster.wardrobe.tags.edit.hint": "Ses tags à elle : défilé, manche 3, rouge…",
    "screen.craftmaster.wardrobe.tags.save": "Enregistrer les tags",
    "screen.craftmaster.wardrobe.tags.save.tip": "La tenue choisie reçoit ces tags (séparés par des virgules), en plus de ceux trouvés dans ses noms.",
    "screen.craftmaster.wardrobe.hint.tags": "Tags : %s",
    "screen.craftmaster.wardrobe.tip.tags": "Tags : %s",
    "screen.craftmaster.wardrobe.tip.restore": "Pas dans cette garde-robe : clic pour la remettre",
    "screen.craftmaster.wardrobe.strip": "Tout enlever",
    "screen.craftmaster.wardrobe.strip.tip": "Enlève tout (pour composer une tenue à partir de rien). Tes vêtements restent de côté : Mes affaires te les rend.",
    "message.craftmaster.wardrobe.stripped": "Tout est enlevé (Mes affaires te rend tes vêtements)",
    "message.craftmaster.wardrobe.restored": "De retour dans la garde-robe : %s",
    "message.craftmaster.wardrobe.tagged": "Tags enregistrés : %s",
    "screen.craftmaster.wardrobe.tip.worn": "Clic : voir sa tenue · clic droit : l'enlever",
    "screen.craftmaster.wardrobe.not_here": "%s ne vient pas d'une tenue de cette garde-robe",
    "message.craftmaster.wardrobe.taken_off": "Enlevé : %s",
    "screen.craftmaster.wardrobe.auto.already": "Elle contient déjà toutes les tenues du jeu ; elle garde sa propre liste dès que tu la modifies.",
    "screen.craftmaster.wardrobe.type.storage.tip": "Rangement : les habits sont réels ; enfiler une tenue l'échange avec ce que tu portes. Clic : en faire une garde-robe à costumes (copies, toutes les tenues).",
    "screen.craftmaster.wardrobe.type.show.tip": "Costumes pour l'émission : chacun porte des copies, la garde-robe ne se vide jamais et tes vêtements sont mis de côté jusqu'à « Mes affaires ». Clic : en faire une garde-robe de rangement (vrais habits, pour la survie ; elle repart vide).",
    "screen.craftmaster.wardrobe.type.storage": "Type : rangement",
    "screen.craftmaster.wardrobe.type.show": "Type : costumes",
    "screen.craftmaster.wardrobe.count": "%s tenues",
    "screen.craftmaster.wardrobe.count.one": "1 tenue",
    "screen.craftmaster.wardrobe.edit": "✎ Modifier",
    "screen.craftmaster.wardrobe.edit.tip": "Modifier les tenues de cette garde-robe : ajouter ce que tu portes, renommer, remplacer, supprimer, déplacer. Dès la première modification, la garde-robe garde sa propre liste.",
    "screen.craftmaster.wardrobe.favourites": "★ Favoris",
    "screen.craftmaster.wardrobe.favourites.tip": "Seulement tes tenues favorites (clic droit sur une tenue pour l'ajouter ou la retirer).",
    "screen.craftmaster.wardrobe.hint.edit": "Modification : clique sur une tenue pour la choisir",
    "screen.craftmaster.wardrobe.hint.chosen": "Tenue choisie : %s",
    "screen.craftmaster.wardrobe.move_up": "◀",
    "screen.craftmaster.wardrobe.move_down": "▶",
    "screen.craftmaster.wardrobe.move.tip": "Déplace la tenue choisie d'une place (vers la gauche / la droite).",
    "screen.craftmaster.wardrobe.auto": "Remettre toutes les tenues",
    "screen.craftmaster.wardrobe.auto.tip": "La garde-robe contient de nouveau toutes les tenues du jeu (sa liste à elle est oubliée).",
    "screen.craftmaster.wardrobe.tip.choose": "Clic pour la choisir",
    "screen.craftmaster.wardrobe.tip.wear_piece": "Clic : enfiler seulement %s",
    "screen.craftmaster.wardrobe.tip.favourite": "Clic droit : ajouter aux favoris",
    "screen.craftmaster.wardrobe.tip.unfavourite": "Clic droit : retirer des favoris",
    "screen.craftmaster.wardrobe.mix": "Tenue mélangée",
    "screen.craftmaster.wardrobe.empty.favourites": "Pas encore de favori : clic droit sur une tenue",
    "message.craftmaster.wardrobe.wearing_piece": "Tu as enfilé : %s",
    "message.craftmaster.wardrobe.already": "Déjà dans cette garde-robe : %s",
    "message.craftmaster.wardrobe.auto": "De nouveau toutes les tenues (%s)",
    "block.craftmaster.wardrobe": "Garde-robe",
    "item.craftmaster.wardrobe.costume_copy": "Costume : retourne à la garde-robe",
    "screen.craftmaster.wardrobe": "Garde-robe",
    "screen.craftmaster.wardrobe.search": "Rechercher",
    "screen.craftmaster.wardrobe.search.hint": "Chercher : médecin, chapeau…",
    "screen.craftmaster.wardrobe.back": "Mes affaires",
    "screen.craftmaster.wardrobe.back.tip": "Reprendre tes vêtements (aussi : accroupi + clic sur la garde-robe).",
    "screen.craftmaster.wardrobe.replace": "Remplacer",
    "screen.craftmaster.wardrobe.replace.tip": "La tenue choisie devient ce que tu portes maintenant (tête, corps, jambes, pieds).",
    "screen.craftmaster.wardrobe.rename": "Renommer",
    "screen.craftmaster.wardrobe.rename.tip": "Donne à la tenue choisie le nom tapé à gauche.",
    "screen.craftmaster.wardrobe.delete": "Supprimer",
    "screen.craftmaster.wardrobe.delete.tip": "Retire la tenue choisie de cette garde-robe (elle reste ici en gris : un clic la remet).",
    "screen.craftmaster.wardrobe.take_out": "Sortir",
    "screen.craftmaster.wardrobe.take_out.tip": "Met les habits de la tenue choisie dans ton inventaire.",
    "screen.craftmaster.wardrobe.name": "Nom",
    "screen.craftmaster.wardrobe.name.hint": "Nom de la tenue",
    "screen.craftmaster.wardrobe.add": "Ajouter ma tenue",
    "screen.craftmaster.wardrobe.add.tip": "Ajoute ce que tu portes maintenant (tête, corps, jambes, pieds) comme nouvelle tenue, avec le nom tapé à gauche.",
    "screen.craftmaster.wardrobe.store": "Ranger ma tenue",
    "screen.craftmaster.wardrobe.store.tip": "Range ce que tu portes dans la garde-robe, comme nouvelle tenue.",
    "screen.craftmaster.wardrobe.hint": "Clic : enfiler · clic sur une pièce : elle seule · clic droit : ★ favori",
    "screen.craftmaster.wardrobe.empty.show": "Aucune tenue : enfile des vêtements, puis Modifier › Ajouter ce que je porte",
    "screen.craftmaster.wardrobe.empty.storage": "Vide : Modifier › Ranger ce que je porte",
    "screen.craftmaster.wardrobe.no_match": "Rien pour « %s »",
    "screen.craftmaster.wardrobe.piece.head": "Tête : %s",
    "screen.craftmaster.wardrobe.piece.body": "Corps : %s",
    "screen.craftmaster.wardrobe.piece.legs": "Jambes : %s",
    "screen.craftmaster.wardrobe.piece.feet": "Pieds : %s",
    "screen.craftmaster.wardrobe.nothing": "rien",
    "screen.craftmaster.wardrobe.tip.wear": "Clic : enfiler toute la tenue",
    "screen.craftmaster.wardrobe.own_clothes": "Mes affaires",
    "screen.craftmaster.wardrobe.preview": "Aperçu",
    "screen.craftmaster.wardrobe.in_costume": "En costume",
    "screen.craftmaster.wardrobe.you": "Toi",
    "message.craftmaster.wardrobe.wearing": "Tu portes : %s",
    "message.craftmaster.wardrobe.own": "Tu as repris tes affaires",
    "message.craftmaster.wardrobe.back": "Tu as repris tes affaires",
    "message.craftmaster.wardrobe.nothing_back": "Tu portes déjà tes affaires",
    "message.craftmaster.wardrobe.full": "Cette garde-robe est pleine",
    "message.craftmaster.wardrobe.wear_something": "Enfile d'abord la tenue (tête, corps, jambes, pieds)",
    "message.craftmaster.wardrobe.added": "Tenue ajoutée : %s",
    "message.craftmaster.wardrobe.replaced": "Tenue remplacée : %s",
    "message.craftmaster.wardrobe.deleted": "Tenue supprimée : %s",
    "message.craftmaster.wardrobe.taken_out": "Habits sortis : %s",
    "message.craftmaster.wardrobe.catalogue_added": "Tenue prête enregistrée : %s",
    "message.craftmaster.wardrobe.catalogue_removed": "Ce n'est plus une tenue prête : %s",
    "message.craftmaster.wardrobe.catalogue_missing": "%s n'est pas une tenue prête",
}


def main():
    for name, rows in TEX.items():
        write_png(os.path.join(A, f"textures/block/{name}.png"), rows)
    for lower in (True, False):
        for opened in (False, True):
            n = "wardrobe_" + ("lower" if lower else "upper") + ("_open" if opened else "")
            write_json(os.path.join(A, f"models/block/{n}.json"), half(lower, opened))
    write_json(os.path.join(A, "models/item/wardrobe.json"), item_model())
    write_json(os.path.join(A, "items/wardrobe.json"), {"model": {"type": "minecraft:model", "model": "craftmaster:item/wardrobe"}})
    fy = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for f, y in fy.items():
        for half_ in ("lower", "upper"):
            for o in ("false", "true"):
                v = {"model": f"craftmaster:block/wardrobe_{half_}" + ("_open" if o == "true" else "")}
                if y:
                    v["y"] = y
                variants[f"facing={f},half={half_},open={o}"] = v
    write_json(os.path.join(A, "blockstates/wardrobe.json"), {"variants": variants})
    # Either half drops the wardrobe (the other half goes away without dropping anything)
    write_json(os.path.join(D, "loot_table/blocks/wardrobe.json"), {
        "type": "minecraft:block",
        "pools": [{"condition": {"type": "minecraft:survives_explosion"}, "rolls": 1,
                   "entries": [{"type": "minecraft:item", "name": "craftmaster:wardrobe"}]}],
        "random_sequence": "craftmaster:blocks/wardrobe"})
    # Planks around a chest, a gold ingot for the trim, a glass pane for the mirror
    write_json(os.path.join(D, "recipe/wardrobe.json"), {
        "type": "minecraft:crafting_shaped", "category": "building",
        "pattern": ["PGP", "PCM", "PPP"],
        "key": {"P": "#minecraft:planks", "G": "minecraft:gold_ingot", "C": "minecraft:chest", "M": "minecraft:glass_pane"},
        "result": {"id": "craftmaster:wardrobe", "count": 1}})
    write_json(os.path.join(L, "en_us.json"), EN)
    write_json(os.path.join(L, "fr_fr.json"), FR)
    print("ok")


if __name__ == "__main__":
    main()
