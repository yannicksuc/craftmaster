"""Screen effects assets (docs/wiki/Effets.md): the post effect chains (post_effect/fx/*.json), the wearable glasses,
masks and headsets (item sprites, worn 3D models, item definitions, textures, recipes) and the texts.

Called by scripts/gen_assets.py (generate(g) with the gen_assets module, before the translations are written).
The GLSL sources are hand-written in assets/craftmaster/shaders/ (not generated).

Wearables: a 16 px sprite in the inventory and the hand, and a small 3D model on the head (item model "select" on
the display context "head"), drawn like a carved pumpkin by the head layer: the model's 16-unit space covers the
head plus one skin pixel on each side, the front (north face) on the face. Few cubes (a frame and two arms for the
glasses, a box and a strap for the goggles), the detail in the texture; outlines in the darkest tone of each
material, light from the top left.
"""
import os

T = (0, 0, 0, 0)

# id -> (effect, english name, french name)
WEARABLES = {
    "blurry_glasses": ("flou", "Blurry Glasses", "Lunettes floues"),
    "sepia_glasses": ("sepia", "Sepia Glasses", "Lunettes sépia"),
    "noir_blanc_glasses": ("noir_blanc", "Black & White Glasses", "Lunettes noir et blanc"),
    "pixel_glasses": ("pixel", "Pixel Shades", "Lunettes pixel"),
    "peephole_glasses": ("fisheye+masque_rond", "Peephole Goggles", "Lunettes judas"),
    "pumpkin_mask": ("citrouille", "Pumpkin Mask", "Masque citrouille"),
    "vintage_glasses": ("vieux_film+vignette", "Vintage Glasses", "Lunettes vintage"),
    "vhs_headset": ("vhs", "VHS Headset", "Casque VHS"),
    "thermal_goggles": ("infrarouge", "Thermal Goggles", "Lunettes thermiques"),
    "heart_glasses": ("reve", "Heart Glasses", "Lunettes cœur"),
    "wobbly_glasses": ("ondule", "Wobbly Glasses", "Lunettes tourbillon"),
    "binocular_goggles": ("jumelles", "Binocular Goggles", "Jumelles frontales"),
}

# Effect id -> (english, french): keep in sync with fx/ScreenFx.ORDER
EFFECTS = {
    "ondule": ("wobble", "ondulation"), "fisheye": ("fisheye", "fish-eye"), "pixel": ("pixelated", "pixelisé"),
    "flou": ("blur", "flou"), "reve": ("dream glow", "rêve"), "noir_blanc": ("black & white", "noir et blanc"),
    "sepia": ("sepia", "sépia"), "infrarouge": ("thermal", "infrarouge"), "negatif": ("negative", "négatif"),
    "bd": ("comic", "bande dessinée"), "vhs": ("VHS tape", "cassette VHS"), "vieux_film": ("old film", "vieux film"),
    "vignette": ("vignette", "vignette"), "citrouille": ("pumpkin mask", "masque citrouille"),
    "jumelles": ("binoculars", "jumelles"), "masque_rond": ("round mask", "masque rond"),
}


class Img:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.p = [[T for _ in range(w)] for _ in range(h)]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[y][x] = c if len(c) == 4 else tuple(c) + (255,)

    def get(self, x, y):
        return self.p[y][x]

    def art(self, x0, y0, rows, pal):
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != "." and ch != " ":
                    self.set(x0 + x, y0 + y, pal[ch])

    def rect(self, x0, y0, w, h, c):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self.set(x, y, c)


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


# ------------------------------------------------------------------ palettes (dark → light)
BLACK = [(20, 18, 24), (34, 31, 40), (52, 48, 60), (78, 74, 88)]
GOLD = [(122, 78, 20), (180, 124, 32), (226, 172, 50), (250, 214, 110)]
TORTOISE = [(58, 30, 14), (98, 54, 22), (142, 84, 34), (186, 124, 56)]
STEEL = [(44, 46, 54), (70, 74, 84), (104, 110, 122), (150, 156, 168)]
RED = [(92, 14, 20), (150, 26, 34), (204, 48, 52), (240, 104, 96)]
PINK = [(120, 30, 70), (196, 58, 116), (240, 110, 160), (255, 190, 214)]
BRASS = [(96, 60, 18), (150, 100, 30), (206, 150, 52), (244, 206, 110)]
PUMPKIN = [(110, 44, 6), (168, 74, 10), (220, 116, 22), (246, 160, 52)]
STEM = [(46, 52, 20), (74, 88, 30), (104, 122, 44)]
WHITE = (246, 246, 240)


# ------------------------------------------------------------------ glasses: sprite templates
# o = frame outline (darkest), f = frame, F = frame light (top), a = arm, L = lens, l = lens shade (bottom),
# H = lens highlight, S = lens second colour
GLASSES_ICON = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "..a..........a..",
    "...a........a...",
    "oFFFFFo..oFFFFFo",
    "fHLLLLf..fHLLLLf",
    "fLLLLLfoofLLLLLf",
    "fLLLLLf..fLLLLLf",
    "flllllf..flllllf",
    "offfffo..offfffo",
    "................",
    "................",
    "................",
]
ROUND_ICON = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "..a..........a..",
    "...a........a...",
    "..oFFo....oFFo..",
    ".oHLLLo..oHLLLo.",
    "ofLLLLLooLLLLLfo",
    "ofLLLLf..fLLLLfo",
    ".ollllo..ollllo.",
    "..offo....offo..",
    "................",
    "................",
    "................",
]
HEART_ICON = [
    "................",
    "................",
    "................",
    "................",
    "..a..........a..",
    "...a........a...",
    ".oo.oo....oo.oo.",
    "oHLoLLo..oHLoLLo",
    "oLLLLLooooLLLLLo",
    "oLLLLLo..oLLLLLo",
    ".olllo....olllo.",
    "..olo......olo..",
    "...o........o...",
    "................",
    "................",
    "................",
]
SHADES_ICON = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "..a..........a..",
    "...a........a...",
    "oooooooooooooooo",
    "ooHHLLLooHHLLLoo",
    ".oHLLLLooHLLLLo.",
    ".oLLLLLooLLLLLo.",
    "..ooooo..ooooo..",
    "................",
    "................",
    "................",
    "................",
]

# Worn front (16 x 5): the frame across the face; x 0 and 15 hold the arms' hinges (o / f / L as above)
GLASSES_FRONT = [
    ".oFFFFo..oFFFFo.",
    ".fHLLLfoofHLLLf.",
    "ofLLLLf..fLLLLfo",
    ".fLLLLf..fLLLLf.",
    ".offffo..offffo.",
]
ROUND_FRONT = [
    "..oFFo....oFFo..",
    ".oHLLLooooHLLLo.",
    "ofLLLLf..fLLLLfo",
    ".olLLlo..olLLlo.",
    "..offo....offo..",
]
HEART_FRONT = [
    ".oo.oo....oo.oo.",
    ".oHLLLooooHLLLo.",
    "ooLLLLo..oLLLLoo",
    "..olLo....olLo..",
    "...oo......oo...",
]
SHADES_FRONT = [
    "oooooooooooooooo",
    ".oHLLLooooHLLLo.",
    "ooHLLLo..oHLLLoo",
    "..oLLLo..oLLLo..",
    "...ooo....ooo...",
]


def glasses_pal(frame, lens, lens_alpha=200, highlight=WHITE, second=None):
    return {"o": rgba(frame[0]), "f": rgba(frame[1]), "F": rgba(frame[2]), "a": rgba(frame[1]),
            "L": rgba(lens[2], lens_alpha), "l": rgba(lens[1], lens_alpha), "H": rgba(highlight, 230),
            "S": rgba(second or lens[0], lens_alpha)}


def swirl(img, x0, y0, w, h, c1, c2, alpha):
    """Hypnotic lens: concentric square rings alternating two colours (only over lens pixels already painted)."""
    cx, cy = x0 + (w - 1) / 2, y0 + (h - 1) / 2
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px = img.get(x, y)
            if px[3] == 0 or px[3] == 255:
                continue
            ring = int(max(abs(x - cx), abs(y - cy)) + 0.5)
            img.set(x, y, rgba(c1 if ring % 2 == 0 else c2, alpha))


GLASSES = {
    # id: (icon template, front template, palette, post-paint(img, worn?))
    "blurry_glasses": (GLASSES_ICON, GLASSES_FRONT, glasses_pal(BLACK, [(150, 170, 186), (190, 206, 218), (226, 234, 240)], 210), "frost"),
    "sepia_glasses": (ROUND_ICON, ROUND_FRONT, glasses_pal(TORTOISE, [(120, 70, 20), (170, 108, 40), (206, 146, 64)], 190), None),
    "noir_blanc_glasses": (GLASSES_ICON, GLASSES_FRONT, glasses_pal(BLACK, [(30, 30, 30), (60, 60, 60), (90, 90, 90)], 215), "bw"),
    "pixel_glasses": (SHADES_ICON, SHADES_FRONT, glasses_pal(BLACK, [(8, 8, 10), (16, 16, 20), (26, 26, 32)], 235, (236, 236, 236)), None),
    "vintage_glasses": (ROUND_ICON, ROUND_FRONT, glasses_pal(GOLD, [(92, 70, 40), (128, 100, 62), (160, 132, 90)], 170), None),
    "heart_glasses": (HEART_ICON, HEART_FRONT, glasses_pal(PINK, [(200, 40, 100), (236, 80, 140), (255, 140, 186)], 190), None),
    "wobbly_glasses": (GLASSES_ICON, GLASSES_FRONT, glasses_pal(STEEL, [(120, 40, 160), (160, 80, 200), (220, 220, 240)], 210), "swirl"),
}


def paint_glasses(img, x0, y0, rows, pal, extra):
    img.art(x0, y0, rows, pal)
    w, h = len(rows[0]), len(rows)
    if extra == "frost":
        # Frosted lenses: milky diagonal streaks
        for y in range(h):
            for x in range(w):
                if rows[y][x] == "L" and (x + y) % 3 == 0:
                    img.set(x0 + x, y0 + y, (250, 252, 255, 225))
    elif extra == "bw":
        # Left lens black, right lens white
        for y in range(h):
            for x in range(w // 2, w):
                if rows[y][x] in "Ll":
                    img.set(x0 + x, y0 + y, (232, 232, 228, 225) if rows[y][x] == "L" else (196, 196, 192, 225))
    elif extra == "swirl":
        half = w // 2
        swirl(img, x0, y0, half, h, (140, 50, 190), (236, 236, 250), 215)
        swirl(img, x0 + half, y0, half, h, (140, 50, 190), (236, 236, 250), 215)


def generate(g):
    A, D = g.A, g.D
    wj, wp = g.write_json, g.write_png
    en, fr = g.en, g.fr

    def save(img, path):
        wp(os.path.join(A, f"textures/{path}.png"), img.w, img.h, img.p)

    def item_def(name, worn_model):
        wj(os.path.join(A, f"items/{name}.json"), {"model": {
            "type": "minecraft:select", "property": "minecraft:display_context",
            "cases": [{"when": "head", "model": {"type": "minecraft:model", "model": worn_model}}],
            "fallback": {"type": "minecraft:model", "model": f"craftmaster:item/{name}"}}})
        wj(os.path.join(A, f"models/item/{name}.json"),
           {"parent": "minecraft:item/generated", "textures": {"layer0": f"craftmaster:item/{name}"}})

    def face(tex, uv):
        return {"texture": tex, "uv": uv}

    # ---------------------------------------------------------------- glasses (frame + two arms, 3 cubes)
    for name, (icon, front, pal, extra) in GLASSES.items():
        sprite = Img(16, 16)
        paint_glasses(sprite, 0, 0, icon, pal, extra)
        save(sprite, f"item/{name}")
        worn = Img(16, 16)
        paint_glasses(worn, 0, 0, front, pal, extra)  # rows 0-4: front / back of the frame
        # rows 6-7: the arms (side, then top), frame colour with a light top edge
        for x in range(12):
            worn.set(x, 6, pal["f"])
            worn.set(x, 7, pal["F"])
        worn.set(11, 6, pal["o"])  # temple tip, darker
        save(worn, f"item/fx/{name}_worn")
        tex = "#t"
        # Top and bottom of the frame: row 0 / row 4 of the front, 1 px deep
        frame = {"from": [0, 5, -0.5], "to": [16, 10, 0.5], "faces": {
            "north": face(tex, [0, 0, 16, 5]), "south": face(tex, [16, 0, 0, 5]),
            "up": face(tex, [0, 0, 16, 1]), "down": face(tex, [0, 4, 16, 5]),
            "east": face(tex, [15, 0, 16, 5]), "west": face(tex, [0, 0, 1, 5])}}
        arms = []
        for x1 in (0, 15):
            arms.append({"from": [x1, 7, 0.5], "to": [x1 + 1, 8, 12], "faces": {
                "east": face(tex, [0, 6, 11.5, 7]), "west": face(tex, [11.5, 6, 0, 7]),
                "up": face(tex, [0, 7, 11.5, 8], ) | {"rotation": 90}, "down": face(tex, [0, 7, 11.5, 8]) | {"rotation": 90},
                "south": face(tex, [11, 6, 12, 7])}})
        wj(os.path.join(A, f"models/item/fx/{name}_worn.json"), {
            "textures": {"t": f"craftmaster:item/fx/{name}_worn", "particle": f"craftmaster:item/{name}"},
            "elements": [frame] + arms})
        item_def(name, f"craftmaster:item/fx/{name}_worn")

    # ---------------------------------------------------------------- goggles (box on the eyes + strap)
    # Texture 32 x 32; model UVs are in 16ths of the texture, so pixel coordinates are halved.
    def U(*px):
        return [v / 2 for v in px]

    def goggles(name, body, strap, front_painter, tubes=()):
        img = Img(32, 32)
        o, f, F, L = body
        # box: north (front) 12x6 at (0,0); up 12x4 at (0,6); down 12x4 at (0,10); east/west 4x6 at (12,0)/(16,0)
        img.rect(0, 0, 12, 6, f)
        front_painter(img)
        img.rect(0, 6, 12, 4, F)
        for x in range(12):
            img.set(x, 9, f)
        img.rect(0, 10, 12, 4, o)
        for side in (12, 16):
            img.rect(side, 0, 4, 6, f)
            for y in range(6):
                img.set(side + 3, y, o)
            for x in range(4):
                img.set(side + x, 0, F)
                img.set(side + x, 5, o)
        # strap: 15 x 2 at (0,16): light top row, dark bottom row, a buckle
        so, sf = strap
        for x in range(15):
            img.set(x, 16, sf)
            img.set(x, 17, so)
        img.rect(6, 16, 3, 2, (170, 170, 178))
        img.set(7, 16, so)
        # tubes: front 4x4 at (20,0), sides 3x4 at (24,0), top 4x3 at (20,4)
        tl = (34, 36, 42)
        img.rect(20, 0, 4, 4, tl)
        img.rect(21, 1, 2, 2, (120, 190, 230))
        img.set(21, 1, (230, 245, 255))
        img.rect(24, 0, 3, 4, (52, 54, 62))
        img.rect(20, 4, 4, 3, (70, 72, 82))
        save(img, f"item/fx/{name}_worn")
        t = "#t"
        box = {"from": [2, 4, -3], "to": [14, 10, 1], "faces": {
            "north": face(t, U(0, 0, 12, 6)), "up": face(t, U(0, 6, 12, 10)), "down": face(t, U(0, 10, 12, 14)),
            "east": face(t, U(12, 0, 16, 6)), "west": face(t, U(16, 0, 20, 6))}}
        band = {"from": [0.5, 6, 0.5], "to": [15.5, 8, 15.5], "faces": {
            "east": face(t, U(0, 16, 15, 18)), "west": face(t, U(0, 16, 15, 18)), "south": face(t, U(0, 16, 15, 18))}}
        els = [box, band]
        for (x1, y1, w, h, depth) in tubes:
            els.append({"from": [x1, y1, -3 - depth], "to": [x1 + w, y1 + h, -3], "faces": {
                "north": face(t, U(20, 0, 24, 4)), "east": face(t, U(24, 0, 27, 4)), "west": face(t, U(24, 0, 27, 4)),
                "up": face(t, U(20, 4, 24, 7)), "down": face(t, U(20, 4, 24, 7))}})
        wj(os.path.join(A, f"models/item/fx/{name}_worn.json"), {
            "textures": {"t": f"craftmaster:item/fx/{name}_worn", "particle": f"craftmaster:item/{name}"},
            "elements": els})
        item_def(name, f"craftmaster:item/fx/{name}_worn")

    body_black = (rgba(BLACK[0]), rgba(BLACK[1]), rgba(BLACK[2]), None)

    def vhs_front(img):
        # A tape seen from the front: label strip, two reel windows with their hubs
        img.rect(0, 0, 12, 6, rgba(BLACK[1]))
        img.rect(1, 1, 10, 1, (236, 228, 204))
        img.set(2, 1, (210, 60, 50))
        img.set(3, 1, (210, 60, 50))
        for cx in (3, 8):
            img.rect(cx - 1, 2, 3, 3, (60, 58, 70))
            img.set(cx, 3, (210, 206, 196))
        img.rect(5, 3, 2, 1, (44, 40, 52))
        for x in range(12):
            img.set(x, 5, rgba(BLACK[0]))
    goggles("vhs_headset", body_black, (rgba(BLACK[0]), rgba(BLACK[2])), vhs_front)

    def thermal_front(img):
        img.rect(0, 0, 12, 6, rgba(STEEL[1]))
        for cx in (3, 8):
            img.rect(cx - 2, 1, 4, 4, rgba(STEEL[0]))
            img.rect(cx - 1, 2, 2, 2, (220, 60, 30))
            img.set(cx - 1, 2, (255, 200, 80))
        for x in range(12):
            img.set(x, 0, rgba(STEEL[2]))
            img.set(x, 5, rgba(STEEL[0]))
        img.set(5, 1, (90, 220, 90))
    goggles("thermal_goggles", (rgba(STEEL[0]), rgba(STEEL[1]), rgba(STEEL[2]), None), (rgba(BLACK[0]), rgba(BLACK[2])), thermal_front)

    def brass_front(img):
        img.rect(0, 0, 12, 6, rgba(BRASS[1]))
        for x in range(12):
            img.set(x, 0, rgba(BRASS[2]))
            img.set(x, 5, rgba(BRASS[0]))
        for (x, y) in [(1, 1), (10, 1), (1, 4), (10, 4)]:
            img.set(x, y, rgba(BRASS[3]))  # rivets
    goggles("peephole_glasses", (rgba(BRASS[0]), rgba(BRASS[1]), rgba(BRASS[2]), None), (rgba(TORTOISE[0]), rgba(TORTOISE[2])),
            brass_front, tubes=[(6, 5, 4, 4, 2)])

    def bino_front(img):
        img.rect(0, 0, 12, 6, rgba(BLACK[2]))
        for x in range(12):
            img.set(x, 0, rgba(BLACK[3]))
            img.set(x, 5, rgba(BLACK[0]))
    goggles("binocular_goggles", body_black, (rgba(BLACK[0]), rgba(BLACK[2])), bino_front,
            tubes=[(3, 5, 4, 4, 3), (9, 5, 4, 4, 3)])

    # Sprites of the goggles (front view, three-quarter top)
    def sprite(name, rows, pal):
        img = Img(16, 16)
        img.art(0, 0, rows, pal)
        save(img, f"item/{name}")

    sprite("vhs_headset", [
        "................",
        "................",
        "................",
        "...ssssssssss...",
        "..s..........s..",
        ".oFFFFFFFFFFFFo.",
        ".fwwwwrwwwwwwwf.",
        ".fbbhbbbbbhbbbf.",
        ".fbhkhbbbhkhbbf.",
        ".fbbhbbbbbhbbbf.",
        ".ffffffffffffff.",
        ".oooooooooooooo.",
        "................",
        "................",
        "................",
        "................",
    ], {"s": rgba(BLACK[2]), "o": rgba(BLACK[0]), "f": rgba(BLACK[1]), "F": rgba(BLACK[3]), "w": (236, 228, 204, 255),
        "r": (210, 60, 50, 255), "b": (52, 50, 62, 255), "h": (200, 196, 186, 255), "k": (24, 22, 28, 255)})
    sprite("thermal_goggles", [
        "................",
        "................",
        "................",
        "...ssssssssss...",
        "..s..........s..",
        ".oFFFFFFFFFFFFo.",
        ".fooooffooooof..",
        ".foyrroforrrof..",
        ".forrrofforrrof.",
        ".fooooffoooooof.",
        ".ffffffgffffff..",
        "..oooooooooooo..",
        "................",
        "................",
        "................",
        "................",
    ], {"s": rgba(BLACK[2]), "o": rgba(STEEL[0]), "f": rgba(STEEL[1]), "F": rgba(STEEL[3]), "r": (220, 60, 30, 255),
        "y": (255, 200, 80, 255), "g": (90, 220, 90, 255)})
    sprite("peephole_glasses", [
        "................",
        "................",
        "...ssssssssss...",
        "..s..........s..",
        ".oFFFFFFFFFFFFo.",
        ".fRffkkkkkkffRf.",
        ".fffkbbbbbbkfff.",
        ".fffkbhbbbbkfff.",
        ".fffkbbbbbbkfff.",
        ".fRffkkkkkkffRf.",
        ".oooooooooooooo.",
        "................",
        "................",
        "................",
        "................",
        "................",
    ], {"s": rgba(TORTOISE[2]), "o": rgba(BRASS[0]), "f": rgba(BRASS[1]), "F": rgba(BRASS[3]), "R": rgba(BRASS[3]),
        "k": (34, 36, 42, 255), "b": (90, 160, 210, 255), "h": (230, 245, 255, 255)})
    sprite("binocular_goggles", [
        "................",
        "................",
        "..kkkk....kkkk..",
        ".kbbbbk..kbbbbk.",
        ".kbhbbk..kbhbbk.",
        ".kbbbbk..kbbbbk.",
        ".kkkkkkkkkkkkkk.",
        ".oFFFFFFFFFFFFo.",
        ".ffffffffffffff.",
        ".ffffffffffffff.",
        ".oooooooooooooo.",
        "..s..........s..",
        "...ssssssssss...",
        "................",
        "................",
        "................",
    ], {"s": rgba(BLACK[2]), "o": rgba(BLACK[0]), "f": rgba(BLACK[2]), "F": rgba(BLACK[3]), "k": (34, 36, 42, 255),
        "b": (90, 160, 210, 255), "h": (230, 245, 255, 255)})

    # ---------------------------------------------------------------- pumpkin mask (a carved head + its stem)
    P = {"o": rgba(PUMPKIN[0]), "p": rgba(PUMPKIN[1]), "P": rgba(PUMPKIN[2]), "Q": rgba(PUMPKIN[3]),
         "k": (40, 16, 4, 255), "y": (255, 214, 90, 255), "Y": (255, 168, 40, 255), "g": rgba(STEM[1]), "G": rgba(STEM[2]),
         "d": rgba(STEM[0])}

    def ribs(img, x0, y0, light_top=True):
        # Vertical ribs: light band, mid, dark crease, every 4 px; lighter at the top
        for y in range(16):
            for x in range(16):
                k = x % 4
                c = "Q" if k == 1 else ("P" if k in (0, 2) else "p")
                if y >= 12 and c == "Q":
                    c = "P"
                if y == 15 or (y >= 13 and k == 3):
                    c = "o" if k == 3 else "p"
                img.set(x0 + x, y0 + y, P[c])

    side = Img(16, 16)
    ribs(side, 0, 0)
    save(side, "item/fx/pumpkin_mask_side")
    front = Img(16, 16)
    ribs(front, 0, 0)
    front.art(0, 0, [
        "................",
        "................",
        "................",
        "...k........k...",
        "..kyk......kyk..",
        ".kYYYk....kYYYk.",
        ".kkkkk....kkkkk.",
        "................",
        "................",
        "..k..k....k..k..",
        "..kkkkkkkkkkkk..",
        "..kYYYYYYYYYYk..",
        "...kkYkkkkYkk...",
        "................",
        "................",
        "................",
    ], P)
    save(front, "item/fx/pumpkin_mask_front")
    top = Img(16, 16)
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            top.set(x, y, P["Q" if d < 3 else ("P" if d < 6 else "p")])
    for (x, y) in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        top.set(x, y, P["d"])
    save(top, "item/fx/pumpkin_mask_top")
    stem = Img(16, 16)
    stem.rect(0, 0, 16, 16, P["g"])
    for y in range(16):
        stem.set(0, y, P["G"])
        stem.set(3, y, P["d"])
    save(stem, "item/fx/pumpkin_mask_stem")
    wj(os.path.join(A, "models/item/fx/pumpkin_mask_worn.json"), {
        "textures": {"front": "craftmaster:item/fx/pumpkin_mask_front", "side": "craftmaster:item/fx/pumpkin_mask_side",
                     "top": "craftmaster:item/fx/pumpkin_mask_top", "stem": "craftmaster:item/fx/pumpkin_mask_stem",
                     "particle": "craftmaster:item/fx/pumpkin_mask_side"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
                "north": {"texture": "#front", "uv": [0, 0, 16, 16]}, "south": {"texture": "#side", "uv": [0, 0, 16, 16]},
                "east": {"texture": "#side", "uv": [0, 0, 16, 16]}, "west": {"texture": "#side", "uv": [0, 0, 16, 16]},
                "up": {"texture": "#top", "uv": [0, 0, 16, 16]}}},
            {"from": [7, 16, 7], "to": [9, 19, 9], "rotation": {"angle": 22.5, "axis": "z", "origin": [8, 16, 8]}, "faces": {
                "north": {"texture": "#stem", "uv": [0, 0, 2, 3]}, "south": {"texture": "#stem", "uv": [2, 0, 4, 3]},
                "east": {"texture": "#stem", "uv": [0, 0, 2, 3]}, "west": {"texture": "#stem", "uv": [2, 0, 4, 3]},
                "up": {"texture": "#stem", "uv": [0, 0, 2, 2]}}}]})
    item_def("pumpkin_mask", "craftmaster:item/fx/pumpkin_mask_worn")
    sprite("pumpkin_mask", [
        "................",
        ".......dg.......",
        "........g.......",
        "...oPPPPPPPPo...",
        "..oPQPPQPPQPPo..",
        ".oPkkPPQPPkkPPo.",
        ".oPkykPQPkykPPo.",
        ".oPPPPPQPPPPPPo.",
        ".oPkPPkPkPPkPPo.",
        ".oPkkkkkkkkkPPo.",
        ".opPkYYYYYkPpPo.",
        "..oppkkkkkppoo..",
        "...oopppppoo....",
        ".....oooooo.....",
        "................",
        "................",
    ], P)

    # ---------------------------------------------------------------- post effect chains
    def fx_uniforms(mode=None):
        u = {"FxConfig": [{"name": "Fx", "type": "vec4", "value": [1.0, 0.0, 0.0, 0.0]}]}
        if mode is not None:
            u["FxMode"] = [{"name": "Mode", "type": "vec4", "value": list(map(float, mode))}]
        return u

    def p(frag, inputs, out, mode=None, fx=True):
        d = {"vertex_shader": "minecraft:core/screenquad", "fragment_shader": frag,
             "inputs": [{"sampler_name": s, "target": tgt, **({"bilinear": True} if bil else {})} for (s, tgt, bil) in inputs],
             "output": out}
        if fx:
            d["uniforms"] = fx_uniforms(mode)
        return d

    blit = {"vertex_shader": "minecraft:core/screenquad", "fragment_shader": "minecraft:post/blit",
            "inputs": [{"sampler_name": "In", "target": "swap"}], "output": "minecraft:main",
            "uniforms": {"BlitConfig": [{"name": "ColorModulate", "type": "vec4", "value": [1.0, 1.0, 1.0, 1.0]}]}}

    def simple(frag, mode=None, bilinear=False):
        return {"targets": {"swap": {}}, "passes": [p(frag, [("In", "minecraft:main", bilinear)], "swap", mode), blit]}

    chains = {
        "ondule": simple("craftmaster:post/fx_warp", (2, 0, 0, 0), True),
        "fisheye": simple("craftmaster:post/fx_warp", (0, 0, 0, 0), True),
        "pixel": simple("craftmaster:post/fx_warp", (1, 0, 0, 0)),
        "noir_blanc": simple("craftmaster:post/fx_color", (0, 0, 0, 0)),
        "sepia": simple("craftmaster:post/fx_color", (1, 0, 0, 0)),
        "infrarouge": simple("craftmaster:post/fx_color", (2, 0, 0, 0)),
        "negatif": simple("craftmaster:post/fx_color", (3, 0, 0, 0)),
        "bd": simple("craftmaster:post/fx_comic"),
        "vhs": simple("craftmaster:post/fx_vhs", None, True),
        "vieux_film": simple("craftmaster:post/fx_film", None, True),
        "vignette": simple("craftmaster:post/fx_mask", (0, 0, 0, 0)),
        "masque_rond": simple("craftmaster:post/fx_mask", (1, 0, 0, 0)),
        "jumelles": simple("craftmaster:post/fx_mask", (3, 0, 0, 0)),
        # Blur: two Gaussian passes in each direction, the second wider (strong, smooth, cheap)
        "flou": {"targets": {"a": {}, "b": {}}, "passes": [
            p("craftmaster:post/fx_blur", [("In", "minecraft:main", True)], "a", (1, 0, 3, 0)),
            p("craftmaster:post/fx_blur", [("In", "a", True)], "b", (0, 1, 3, 0)),
            p("craftmaster:post/fx_blur", [("In", "b", True)], "a", (1, 0, 7, 0)),
            p("craftmaster:post/fx_blur", [("In", "a", True)], "minecraft:main", (0, 1, 7, 0))]},
        # Dream: a blurred copy glowing over the sharp picture
        "reve": {"targets": {"a": {}, "b": {}, "swap": {}}, "passes": [
            p("craftmaster:post/fx_blur", [("In", "minecraft:main", True)], "a", (1, 0, 6, 0)),
            p("craftmaster:post/fx_blur", [("In", "a", True)], "b", (0, 1, 6, 0)),
            p("craftmaster:post/fx_dream", [("In", "minecraft:main", False), ("Blur", "b", True)], "swap"),
            blit]},
    }
    # "citrouille" is vanilla's carved pumpkin overlay (textures/misc/pumpkinblur.png), drawn on the HUD by the
    # client (client.fx.FxOverlay): no post effect for it
    assert set(chains) | {"citrouille"} == set(EFFECTS), "post effect chains and effect list differ"
    stale = os.path.join(A, "post_effect/fx/citrouille.json")
    if os.path.exists(stale):
        os.remove(stale)
    for fx_id, chain in chains.items():
        wj(os.path.join(A, f"post_effect/fx/{fx_id}.json"), chain)

    # ---------------------------------------------------------------- recipes (simple, optional)
    g.shaped("blurry_glasses", ["I I", "GSG"], {"I": "minecraft:iron_nugget", "G": "minecraft:glass_pane", "S": "minecraft:snowball"}, "blurry_glasses", category="equipment")
    g.shaped("sepia_glasses", ["I I", "GSG"], {"I": "minecraft:iron_nugget", "G": "minecraft:glass_pane", "S": "minecraft:brown_dye"}, "sepia_glasses", category="equipment")
    g.shaped("noir_blanc_glasses", ["I I", "BGW"], {"I": "minecraft:iron_nugget", "G": "minecraft:glass_pane", "B": "minecraft:black_dye", "W": "minecraft:white_dye"}, "noir_blanc_glasses", category="equipment")
    g.shaped("pixel_glasses", ["I I", "GBG"], {"I": "minecraft:iron_nugget", "G": "minecraft:tinted_glass", "B": "minecraft:black_dye"}, "pixel_glasses", category="equipment")
    g.shaped("vintage_glasses", ["N N", "GPG"], {"N": "minecraft:gold_nugget", "G": "minecraft:glass_pane", "P": "minecraft:paper"}, "vintage_glasses", category="equipment")
    g.shaped("heart_glasses", ["I I", "GPG"], {"I": "minecraft:iron_nugget", "G": "minecraft:glass_pane", "P": "minecraft:pink_dye"}, "heart_glasses", category="equipment")
    g.shaped("wobbly_glasses", ["I I", "GPG"], {"I": "minecraft:iron_nugget", "G": "minecraft:glass_pane", "P": "minecraft:purple_dye"}, "wobbly_glasses", category="equipment")
    g.shaped("peephole_glasses", ["LCL", " G "], {"L": "minecraft:leather", "C": "minecraft:copper_ingot", "G": "minecraft:glass"}, "peephole_glasses", category="equipment")
    g.shaped("thermal_goggles", ["LIL", "RGR"], {"L": "minecraft:leather", "I": "minecraft:iron_ingot", "R": "minecraft:redstone", "G": "minecraft:glass_pane"}, "thermal_goggles", category="equipment")
    g.shaped("vhs_headset", ["LBL", "BRB"], {"L": "minecraft:leather", "B": "minecraft:black_dye", "R": "minecraft:redstone"}, "vhs_headset", category="equipment")
    g.shaped("binocular_goggles", ["LIL", "GIG"], {"L": "minecraft:leather", "I": "minecraft:iron_ingot", "G": "minecraft:glass"}, "binocular_goggles", category="equipment")
    g.shaped("pumpkin_mask", ["S", "P"], {"S": "minecraft:string", "P": "minecraft:carved_pumpkin"}, "pumpkin_mask", category="equipment")

    # ---------------------------------------------------------------- texts
    for name, (effect, en_name, fr_name) in WEARABLES.items():
        en[f"item.craftmaster.{name}"] = en_name
        fr[f"item.craftmaster.{name}"] = fr_name
    for fx_id, (en_name, fr_name) in EFFECTS.items():
        en[f"fx.craftmaster.{fx_id}"] = en_name
        fr[f"fx.craftmaster.{fx_id}"] = fr_name
    en["item.craftmaster.screen_fx.effect"] = "Worn: %s"
    fr["item.craftmaster.screen_fx.effect"] = "Porté : %s"
    en["item.craftmaster.screen_fx.hint"] = "Wear it on your head to see the world through it"
    fr["item.craftmaster.screen_fx.hint"] = "À porter sur la tête : on voit le monde à travers"
