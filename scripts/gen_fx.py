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

    # ---------------------------------------------------------------- the one pair of effect glasses
    # LordFinn: "pour toutes les lunettes un seul item coloré". One drawing, the lenses tinted with the effect's
    # colour (minecraft:dyed_color, set by FxContent). Icon: frame (layer 0) + grey lenses (layer 1, tinted). Worn:
    # the frame and its arms (3 cubes), the lenses on their own faces of the same box, tinted too.
    import gen_icons
    save_rows = lambda rows, path: wp(os.path.join(A, f"textures/{path}.png"), 16, 16, rows)
    save_rows(gen_icons.glasses_base(), "item/glasses")
    save_rows(gen_icons.glasses_lens(), "item/glasses_lens")
    pal = glasses_pal(BLACK, [(150, 150, 150), (190, 190, 190), (226, 226, 226)], 255)
    frame_img, lens_img = Img(16, 16), Img(16, 16)
    for y, row in enumerate(GLASSES_FRONT):
        for x, ch in enumerate(row):
            if ch in "LlHS":
                v = {"H": 255, "L": 220, "l": 186, "S": 220}[ch]
                lens_img.set(x, y, (v, v, v, 255))
            elif ch != ".":
                frame_img.set(x, y, pal[ch])
    for x in range(12):
        frame_img.set(x, 6, pal["f"])
        frame_img.set(x, 7, pal["F"])
    frame_img.set(11, 6, pal["o"])
    save(frame_img, "item/fx/glasses_worn")
    save(lens_img, "item/fx/glasses_worn_lens")
    tex = "#t"
    frame = {"from": [0, 5, -0.5], "to": [16, 10, 0.5], "faces": {
        "north": face(tex, [0, 0, 16, 5]), "south": face(tex, [16, 0, 0, 5]),
        "up": face(tex, [0, 0, 16, 1]), "down": face(tex, [0, 4, 16, 5]),
        "east": face(tex, [15, 0, 16, 5]), "west": face(tex, [0, 0, 1, 5])}}
    lenses = {"from": [0, 5, -0.5], "to": [16, 10, 0.5], "faces": {
        "north": {"texture": "#l", "uv": [0, 0, 16, 5], "tintindex": 1},
        "south": {"texture": "#l", "uv": [16, 0, 0, 5], "tintindex": 1}}}
    arms = []
    for x1 in (0, 15):
        arms.append({"from": [x1, 7, 0.5], "to": [x1 + 1, 8, 12], "faces": {
            "east": face(tex, [0, 6, 11.5, 7]), "west": face(tex, [11.5, 6, 0, 7]),
            "up": face(tex, [0, 7, 11.5, 8]) | {"rotation": 90}, "down": face(tex, [0, 7, 11.5, 8]) | {"rotation": 90},
            "south": face(tex, [11, 6, 12, 7])}})
    wj(os.path.join(A, "models/item/fx/glasses_worn.json"), {
        "textures": {"t": "craftmaster:item/fx/glasses_worn", "l": "craftmaster:item/fx/glasses_worn_lens",
                     "particle": "craftmaster:item/glasses"},
        "elements": [frame, lenses] + arms})
    wj(os.path.join(A, "models/item/glasses.json"),
       {"parent": "minecraft:item/generated", "textures": {"layer0": "craftmaster:item/glasses", "layer1": "craftmaster:item/glasses_lens"}})
    tints = [{"type": "minecraft:constant", "value": -1}, {"type": "minecraft:dye", "default": -1}]
    glasses_def = {"model": {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": "head", "model": {"type": "minecraft:model", "model": "craftmaster:item/fx/glasses_worn", "tints": tints}}],
        "fallback": {"type": "minecraft:model", "model": "craftmaster:item/glasses", "tints": tints}}}
    wj(os.path.join(A, "items/glasses.json"), glasses_def)
    # The old items (kept for old worlds) look exactly the same
    for name in WEARABLES:
        wj(os.path.join(A, f"items/{name}.json"), glasses_def)

    # (No pumpkin mask item: the vanilla carved pumpkin already gives the "citrouille" view)

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
    # Each recipe gives the one pair of glasses with its effect, lens colour and name (FxContent.COLORS)
    GLASSES_COLORS = {'blurry_glasses': 10275052, 'sepia_glasses': 11039804, 'noir_blanc_glasses': 9342606, 'pixel_glasses': 6142783, 'peephole_glasses': 2536638, 'vintage_glasses': 14912044, 'vhs_headset': 9129942, 'thermal_goggles': 14235179, 'heart_glasses': 15764671, 'wobbly_glasses': 12729524, 'binocular_goggles': 15911226}

    def glasses_recipe(name, pattern, key):
        wj(os.path.join(D, f"craftmaster/recipe/{name}.json"), {
            "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern, "key": key,
            "result": {"id": "craftmaster:glasses", "count": 1, "components": {
                "craftmaster:screen_fx": WEARABLES[name][0], "minecraft:dyed_color": GLASSES_COLORS[name],
                "minecraft:item_name": {"translate": f"item.craftmaster.{name}"}}}})
    glasses_recipe("blurry_glasses", ["I I", "GSG"], {"I": "minecraft:iron_nugget", "G": "minecraft:glass_pane", "S": "minecraft:snowball"})
    glasses_recipe("sepia_glasses", ["I I", "GSG"], {"I": "minecraft:iron_nugget", "G": "minecraft:glass_pane", "S": "minecraft:brown_dye"})
    glasses_recipe("noir_blanc_glasses", ["I I", "BGW"], {"I": "minecraft:iron_nugget", "G": "minecraft:glass_pane", "B": "minecraft:black_dye", "W": "minecraft:white_dye"})
    glasses_recipe("pixel_glasses", ["I I", "GBG"], {"I": "minecraft:iron_nugget", "G": "minecraft:tinted_glass", "B": "minecraft:black_dye"})
    glasses_recipe("vintage_glasses", ["N N", "GPG"], {"N": "minecraft:gold_nugget", "G": "minecraft:glass_pane", "P": "minecraft:paper"})
    glasses_recipe("heart_glasses", ["I I", "GPG"], {"I": "minecraft:iron_nugget", "G": "minecraft:glass_pane", "P": "minecraft:pink_dye"})
    glasses_recipe("wobbly_glasses", ["I I", "GPG"], {"I": "minecraft:iron_nugget", "G": "minecraft:glass_pane", "P": "minecraft:purple_dye"})
    glasses_recipe("peephole_glasses", ["LCL", " G "], {"L": "minecraft:leather", "C": "minecraft:copper_ingot", "G": "minecraft:glass"})
    glasses_recipe("thermal_goggles", ["LIL", "RGR"], {"L": "minecraft:leather", "I": "minecraft:iron_ingot", "R": "minecraft:redstone", "G": "minecraft:glass_pane"})
    glasses_recipe("vhs_headset", ["LBL", "BRB"], {"L": "minecraft:leather", "B": "minecraft:black_dye", "R": "minecraft:redstone"})
    glasses_recipe("binocular_goggles", ["LIL", "GIG"], {"L": "minecraft:leather", "I": "minecraft:iron_ingot", "G": "minecraft:glass"})

    # ---------------------------------------------------------------- texts
    for name, (effect, en_name, fr_name) in WEARABLES.items():
        en[f"item.craftmaster.{name}"] = en_name
        fr[f"item.craftmaster.{name}"] = fr_name
    for fx_id, (en_name, fr_name) in EFFECTS.items():
        en[f"fx.craftmaster.{fx_id}"] = en_name
        fr[f"fx.craftmaster.{fx_id}"] = fr_name
    en["item.craftmaster.glasses"] = "Glasses"
    fr["item.craftmaster.glasses"] = "Lunettes"
    en["item.craftmaster.screen_fx.effect"] = "Worn: %s"
    fr["item.craftmaster.screen_fx.effect"] = "Porté : %s"
    en["item.craftmaster.screen_fx.hint"] = "Wear it on your head to see the world through it"
    fr["item.craftmaster.screen_fx.hint"] = "À porter sur la tête : on voit le monde à travers"
