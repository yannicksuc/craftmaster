"""Garage door motor (garage/): textures, models, blockstate, item, loot table, recipe, names.

Called by gen_assets.py (generate(g), like gen_tablet.py). No dependency: the PNGs are written pixel by pixel.

The housing is the cream plastic of a real garage motor: a hue-shifted ramp (lights towards pale yellow, shadows
towards a soft violet grey), soft shadows, the top face lit to its border at the level of the sides' top row (the
sides start darkening on their second row).
"""
import os

NAME = "garage_door_motor"

# Cream plastic ramp: 0 darkest .. 5 lightest
CREAM = [(118, 110, 128), (152, 145, 156), (190, 184, 182), (214, 208, 196), (232, 227, 210), (246, 243, 226)]
# Grille: dark warm grey
GRILLE = [(46, 42, 52), (70, 64, 74), (98, 92, 100)]
AMBER = [(150, 70, 20), (232, 140, 40), (255, 214, 120)]


def _face(top):
    px = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if top:
                t = 4
            else:
                t = 3
                if y == 0 or x == 0:
                    t = 4
                if x == 15:
                    t = 3 if y == 0 else 2
                if y == 15:
                    t = 2
            px[y][x] = CREAM[t]
    return px


def front():
    px = _face(False)
    # Bevelled grille panel
    for y in range(3, 10):
        for x in range(3, 13):
            px[y][x] = CREAM[2]
    for x in range(3, 13):
        px[3][x] = CREAM[1]
        px[10][x] = CREAM[4]
    for y in range(3, 11):
        px[y][3] = CREAM[1]
        px[y][12] = CREAM[4]
    # Slots: dark lines with a lit lower lip
    for y in (4, 6, 8):
        for x in range(4, 12):
            px[y][x] = GRILLE[0]
            px[y + 1][x] = GRILLE[2]
    # A thin seam, then the amber status light
    for x in range(2, 14):
        px[11][x] = CREAM[2]
    px[12][11] = AMBER[2]
    px[12][10] = AMBER[1]
    px[13][11] = AMBER[0]
    px[13][10] = AMBER[0]
    return px


def side():
    px = _face(False)
    # Four screws: lit top-left pixel, shadow bottom-right
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[y][x] = CREAM[5]
        px[y + 1][x + 1] = CREAM[1]
        px[y][x + 1] = CREAM[2]
        px[y + 1][x] = CREAM[2]
    return px


def top():
    px = _face(True)
    # The rail mount: a darker metal strip across the middle
    for y in range(16):
        for x in (6, 7, 8, 9):
            px[y][x] = GRILLE[2] if x in (6, 9) else GRILLE[1]
        px[y][10] = CREAM[2]
    return px


def generate(g):
    A, D = g.A, g.D
    wj = g.write_json
    for part, px in (("front", front()), ("side", side()), ("top", top())):
        g.write_png(os.path.join(A, f"textures/block/{NAME}_{part}.png"), 16, 16, [[c + (255,) for c in row] for row in px])

    tex = f"craftmaster:block/{NAME}"
    wj(os.path.join(A, f"models/block/{NAME}.json"), {
        "parent": "minecraft:block/orientable",
        "textures": {"front": tex + "_front", "side": tex + "_side", "top": tex + "_top"},
    })
    # Camouflaged: nothing drawn by the block model (the renderer draws the chosen block)
    wj(os.path.join(A, f"models/block/{NAME}_hidden.json"), {"textures": {"particle": tex + "_side"}, "elements": []})
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for camo in ("false", "true"):
            v = {"model": tex + ("_hidden" if camo == "true" else "")}
            if y and camo == "false":
                v["y"] = y
            variants[f"camouflaged={camo},facing={facing}"] = v
    wj(os.path.join(A, f"blockstates/{NAME}.json"), {"variants": variants})
    wj(os.path.join(A, f"items/{NAME}.json"), {"model": {"type": "minecraft:model", "model": tex}})
    wj(os.path.join(D, f"craftmaster/loot_table/blocks/{NAME}.json"), g.self_drop(NAME))
    g.shaped(NAME, ["III", "IPI", "IRI"], {"I": "minecraft:iron_ingot", "P": "minecraft:piston", "R": "minecraft:redstone"},
             NAME, category="redstone")

    g.en[f"block.craftmaster.{NAME}"] = "Garage Door Motor"
    g.fr[f"block.craftmaster.{NAME}"] = "Moteur de porte de garage"
