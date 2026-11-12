"""Assets of chantier H (tasks: props, buzzers, judging tools, creative objects), called from gen_assets.py.

Models, blockstates, item model definitions, loot tables, recipes, textures (pure Python PNG) and the fr_fr /
en_us texts. Kept in its own file so the other chantiers editing gen_assets.py do not collide with it.
Style: Minecraft 16 px, few cubes, detail in the texture; gold + red velvet + cream paper like the show's poster.
"""
import math
import os


def generate(A, D, en, fr, write_json, write_png, self_drop, shaped, colors):
    def tex(name, rows, kind="block"):
        write_png(os.path.join(A, f"textures/{kind}/{name}.png"), len(rows[0]), len(rows), rows)

    def model(name, obj):
        write_json(os.path.join(A, f"models/block/{name}.json"), obj)

    def item_def(name, model_id):
        write_json(os.path.join(A, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": model_id}})

    def flat_item(name):
        write_json(os.path.join(A, f"models/item/{name}.json"),
                   {"parent": "minecraft:item/generated",
                    "textures": {"layer0": f"craftmaster:item/{name}"}})
        item_def(name, f"craftmaster:item/{name}")

    def el(frm, to, tx, skip=(), uv=None):
        f = {}
        for d in ("north", "south", "east", "west", "up", "down"):
            if d in skip:
                continue
            f[d] = {"texture": tx}
            if uv and d in uv:
                f[d]["uv"] = uv[d]
        return {"from": frm, "to": to, "faces": f}

    # ---------------------------------------------------------------- texture helpers
    def clamp(v):
        return max(0, min(255, int(round(v))))

    def sh(c, f):
        return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), c[3] if len(c) > 3 else 255)

    def ramp(palette, t):
        """palette: list of colours dark → light; t in [0, 1] picks one (no blending: pixel art)."""
        i = max(0, min(len(palette) - 1, int(t * len(palette))))
        c = palette[i]
        return (c[0], c[1], c[2], 255)

    def hash2(x, y, s=0):
        h = (x * 73856093) ^ (y * 19349663) ^ (s * 83492791)
        h ^= h >> 13
        h = (h * 0x5DEECE66D) & 0xFFFFFFFF
        return ((h >> 8) & 0xFFFF) / 65536.0

    GOLD = [(122, 78, 20), (168, 112, 28), (212, 154, 44), (240, 194, 72), (255, 228, 128)]
    CHARCOAL = [(30, 26, 30), (42, 37, 42), (54, 48, 54), (66, 60, 68)]
    VELVET = [(70, 10, 18), (98, 16, 26), (128, 24, 34), (156, 34, 42), (182, 50, 56)]
    CREAM = [(196, 182, 150), (214, 202, 172), (230, 220, 194), (242, 234, 212)]
    WOOD = [(58, 36, 20), (78, 50, 28), (98, 64, 36), (116, 78, 44)]
    ALU = [(120, 126, 134), (150, 156, 164), (180, 186, 194), (208, 212, 218)]

    def metal(palette, salt, w=16, h=16, vertical=True):
        """Clean metal: light at the top, dark at the bottom, a few short streaks (no noise)."""
        rows = []
        for y in range(h):
            row = []
            for x in range(w):
                t = 1 - (y / (h - 1) if vertical else x / (w - 1)) * 0.75
                if hash2(x // 3, y, salt) > 0.86:
                    t += 0.18
                row.append(ramp(palette, t))
            rows.append(row)
        return rows

    def velvet(salt):
        rows = []
        for y in range(16):
            row = []
            for x in range(16):
                fold = 0.5 + 0.5 * math.sin((x % 4 + 0.5) / 4 * math.pi)
                t = 0.15 + 0.6 * fold - y / 15 * 0.2 + (0.1 if hash2(x, y // 3, salt) > 0.9 else 0)
                row.append(ramp(VELVET, t))
            rows.append(row)
        return rows

    def border(rows, palette, width=1, top_only=False):
        h, w = len(rows), len(rows[0])
        for y in range(h):
            for x in range(w):
                edge = y < width if top_only else (x < width or y < width or x >= w - width or y >= h - width)
                if edge:
                    rows[y][x] = ramp(palette, 0.9 if y < width else 0.4)
        return rows

    # ---------------------------------------------------------------- big red button
    # A charcoal pedestal with a gold band, a big red dome on top (pressed: the dome goes down).
    ped = metal(CHARCOAL, 1)
    for x in range(16):
        # The model shows rows 7..15 on the sides: gold band at the top of that
        ped[7][x] = ramp(GOLD, 0.95)
        ped[8][x] = ramp(GOLD, 0.6)
        ped[15][x] = ramp(CHARCOAL, 0)
        # A thin gold line lower down
        ped[11][x] = ramp(GOLD, 0.45)
    tex("big_red_button_side", ped)
    top = metal(CHARCOAL, 2)
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if 4.5 < d < 5.6:
                top[y][x] = ramp(GOLD, 0.8 if x + y < 15 else 0.4)
            elif d >= 6.5:
                top[y][x] = ramp(GOLD, 0.95 if y < 8 else 0.55)
    tex("big_red_button_top", top)
    RED = [(110, 10, 14), (150, 18, 22), (192, 30, 34), (222, 52, 52), (250, 120, 112)]
    dome = []
    for y in range(16):
        row = []
        for x in range(16):
            t = 0.75 - y / 15 * 0.55
            if 2 <= x <= 5 and 1 <= y <= 3:
                t = 1.0  # highlight, top left
            row.append(ramp(RED, t))
        dome.append(row)
    tex("big_red_button_dome", dome)
    dome_top = []
    for y in range(16):
        row = []
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            t = 0.85 - d / 8 * 0.4
            if 4 <= x <= 6 and 4 <= y <= 6:
                t = 1.0
            row.append(ramp(RED, t))
        dome_top.append(row)
    tex("big_red_button_dome_top", dome_top)

    def button_model(name, dome_h):
        model(name, {"parent": "minecraft:block/block",
                     "textures": {"particle": "craftmaster:block/big_red_button_dome", "side": "craftmaster:block/big_red_button_side",
                                  "top": "craftmaster:block/big_red_button_top", "dome": "craftmaster:block/big_red_button_dome",
                                  "dome_top": "craftmaster:block/big_red_button_dome_top"},
                     "elements": [
                         el([2, 0, 2], [14, 9, 14], "#side", uv={"up": [2, 2, 14, 14]}) | {"faces": {
                             "north": {"texture": "#side", "uv": [2, 7, 14, 16]}, "south": {"texture": "#side", "uv": [2, 7, 14, 16]},
                             "east": {"texture": "#side", "uv": [2, 7, 14, 16]}, "west": {"texture": "#side", "uv": [2, 7, 14, 16]},
                             "up": {"texture": "#top", "uv": [2, 2, 14, 14]}, "down": {"texture": "#side", "uv": [2, 2, 14, 14]}}},
                         {"from": [4, 9, 4], "to": [12, 9 + dome_h, 12], "faces": {
                             d: {"texture": "#dome", "uv": [4, 2, 12, 2 + dome_h]} for d in ("north", "south", "east", "west")
                         } | {"up": {"texture": "#dome_top", "uv": [4, 4, 12, 12]}}},
                     ]})

    button_model("big_red_button", 4)
    button_model("big_red_button_pressed", 1)
    write_json(os.path.join(A, "blockstates/big_red_button.json"), {"variants": {
        "powered=false": {"model": "craftmaster:block/big_red_button"},
        "powered=true": {"model": "craftmaster:block/big_red_button_pressed"}}})
    item_def("big_red_button", "craftmaster:block/big_red_button")
    write_json(os.path.join(D, "craftmaster/loot_table/blocks/big_red_button.json"), self_drop("big_red_button"))

    # ---------------------------------------------------------------- buzzers (one per dye colour)
    # The base in vanilla textures (polished blackstone, a band of gold block). The dome is painted like the toy
    # ducks (gen_toys.py of the task objects): one map of ramp levels (0 deepest shadow, 4 the dye colour, 6
    # highlight) written in each dye colour with a hue-shifted ramp. Top lit evenly with a small L of shine, sides a
    # step darker row by row and at their vertical edges. Lit: everything a step lighter, a bright centre, glowing.
    def hue_ramp(rgb, n=7, base=4):
        import colorsys
        r, g, b = ((rgb >> 16) & 255) / 255, ((rgb >> 8) & 255) / 255, (rgb & 255) / 255
        h, sat, v = colorsys.rgb_to_hsv(r, g, b)
        grey = sat < 0.18
        if not grey:
            sat, v = min(1, sat * 1.12 + 0.05), min(1, max(v, 0.62) * 1.08)
        out = []
        for i in range(n):
            k = i - base
            hh, ss, vv = h, sat, v
            if not grey:
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
                hh, ss = 0.62, (0.06 if k < 0 else 0.03)
            cr, cg, cb = colorsys.hsv_to_rgb(hh % 1, max(0, min(1, ss)), max(0, min(1, vv)))
            out.append((round(cr * 255), round(cg * 255), round(cb * 255), 255))
        return out

    def dome_levels(lit):
        """Ramp levels of the dome atlas: top 6 x 6 at (0, 0), sides 6 x 3 at (0, 6)."""
        lv = {}
        for y in range(6):
            for x in range(6):
                lv[(x, y)] = 5
        for p in ((1, 1), (2, 1), (1, 2)):
            lv[p] = 6
        for y, row in enumerate((5, 4, 3)):
            for x in range(6):
                lv[(x, 6 + y)] = row - (1 if (x in (0, 5) and y > 0) else 0)
        lv[(1, 6)] = 6
        if lit:
            lv = {p: min(6, v + 1) for p, v in lv.items()}
        return lv

    def buzzer_model(name, dome, lit):
        dome_el = {"from": [5, 4, 5], "to": [11, 7, 11], "faces": {
            **{d: {"texture": "#dome", "uv": [0, 6, 6, 9]} for d in ("north", "south", "east", "west")},
            "up": {"texture": "#dome", "uv": [0, 0, 6, 6]}}}
        if lit:
            dome_el["light_emission"] = 15
        model(name, {"parent": "minecraft:block/block", "ambientocclusion": False,
                     "textures": {"particle": dome, "dome": dome, "base": "minecraft:block/polished_blackstone",
                                  "band": "minecraft:block/gold_block"},
                     "elements": [
                         {"from": [3, 0, 3], "to": [13, 3, 13], "faces": {
                             **{d: {"texture": "#base", "uv": [3, 13, 13, 16]} for d in ("north", "south", "east", "west")},
                             "down": {"texture": "#base", "uv": [3, 3, 13, 13]}}},
                         {"from": [3, 3, 3], "to": [13, 4, 13], "faces": {
                             **{d: {"texture": "#band", "uv": [3, 12, 13, 13]} for d in ("north", "south", "east", "west")},
                             "up": {"texture": "#band", "uv": [3, 3, 13, 13]}}},
                         dome_el]})

    for color, (rgb, en_name, fr_name) in colors.items():
        name = f"{color}_buzzer"
        ramp_ = hue_ramp(rgb)
        for lit in (False, True):
            rows = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
            for (x, y), v in dome_levels(lit).items():
                rows[y][x] = ramp_[v]
            if lit:
                rows[2][2] = rows[2][3] = rows[3][2] = rows[3][3] = (255, 255, 240, 255)
            tex(f"{name}_dome" + ("_lit" if lit else ""), rows)
        buzzer_model(name, f"craftmaster:block/{name}_dome", False)
        buzzer_model(name + "_lit", f"craftmaster:block/{name}_dome_lit", True)
        write_json(os.path.join(A, f"blockstates/{name}.json"), {"variants": {
            f"lit={lit},powered={pw}": {"model": f"craftmaster:block/{name}" + ("_lit" if lit == "true" else "")}
            for lit in ("false", "true") for pw in ("false", "true")}})
        item_def(name, f"craftmaster:block/{name}")
        write_json(os.path.join(D, f"craftmaster/loot_table/blocks/{name}.json"), self_drop(name))
        shaped(name, ["D", "B", "I"], {"D": f"minecraft:{color}_dye", "B": "minecraft:stone_button", "I": "minecraft:iron_ingot"},
               name, 1, "redstone")
        en[f"block.craftmaster.{name}"] = f"{en_name} Buzzer"
        fr[f"block.craftmaster.{name}"] = f"Buzzer {fr_name}"

    # ---------------------------------------------------------------- scales (balance)
    tex("balance_gold", metal(GOLD, 4))
    pan = metal([(90, 60, 22), (120, 82, 30), (150, 106, 40), (176, 130, 52)], 5)
    tex("balance_pan", pan)
    model("balance", {"parent": "minecraft:block/block",
                      "textures": {"particle": "craftmaster:block/balance_gold", "g": "craftmaster:block/balance_gold",
                                   "p": "craftmaster:block/balance_pan", "b": "craftmaster:block/big_red_button_side"},
                      "elements": [
                          el([4, 0, 4], [12, 1, 12], "#b"),                    # foot
                          el([7, 1, 7], [9, 10, 9], "#g"),                     # column
                          el([1, 10, 7], [15, 11, 9], "#g"),                   # beam
                          el([1, 5, 5], [6, 6, 11], "#p"),                     # left pan
                          el([10, 5, 5], [15, 6, 11], "#p"),                   # right pan
                          el([3, 6, 7.5], [4, 10, 8.5], "#g", skip=("up", "down")),   # strings
                          el([12, 6, 7.5], [13, 10, 8.5], "#g", skip=("up", "down")),
                      ]})
    write_json(os.path.join(A, "blockstates/balance.json"), {"variants": {"": {"model": "craftmaster:block/balance"}}})
    item_def("balance", "craftmaster:block/balance")
    write_json(os.path.join(D, "craftmaster/loot_table/blocks/balance.json"), self_drop("balance"))
    shaped("balance", ["GGG", " G ", "PGP"], {"G": "minecraft:gold_ingot", "P": "minecraft:heavy_weighted_pressure_plate"}, "balance")

    # ---------------------------------------------------------------- display plinth
    # Minecraft's own materials (LordFinn: no flat saturated gold): a smooth quartz base and cap, a quartz pillar,
    # a red wool cushion on top
    model("display_plinth", {"parent": "minecraft:block/block",
                             "textures": {"particle": "minecraft:block/quartz_pillar_side", "q": "minecraft:block/quartz_block_bottom",
                                          "p": "minecraft:block/quartz_pillar_side", "pt": "minecraft:block/quartz_pillar_top",
                                          "w": "minecraft:block/red_wool"},
                             "elements": [
                                 el([1, 0, 1], [15, 2, 15], "#q"),
                                 el([3, 2, 3], [13, 12, 13], "#p", skip=("up", "down")),
                                 el([1, 12, 1], [15, 14, 15], "#q"),
                                 el([2, 14, 2], [14, 15, 14], "#w", skip=("down",)),
                             ]})
    write_json(os.path.join(A, "blockstates/display_plinth.json"), {"variants": {"": {"model": "craftmaster:block/display_plinth"}}})
    item_def("display_plinth", "craftmaster:block/display_plinth")
    write_json(os.path.join(D, "craftmaster/loot_table/blocks/display_plinth.json"), self_drop("display_plinth"))
    shaped("display_plinth", ["W", "Q", "G"], {"W": "minecraft:red_wool", "Q": "minecraft:quartz_pillar", "G": "minecraft:gold_ingot"},
           "display_plinth", 1, "building")

    # ---------------------------------------------------------------- items (pixel art, light from the top left)
    def art(name, lines, pal):
        tex(name, [[pal[ch] for ch in row] for row in lines], "item")
        flat_item(name)

    T = (0, 0, 0, 0)
    base_pal = {".": T, "K": (40, 26, 18, 255), "G": (212, 154, 44, 255), "Y": (240, 194, 72, 255), "W": (255, 246, 214, 255),
                "D": (54, 40, 44, 255), "R": (192, 30, 34, 255), "r": (140, 18, 24, 255), "S": (176, 182, 190, 255),
                "s": (120, 126, 134, 255), "L": (226, 230, 236, 255), "C": (242, 234, 212, 255), "c": (214, 202, 172, 255),
                "B": (60, 88, 160, 255), "N": (46, 58, 51, 255), "n": (34, 44, 38, 255), "w": (116, 78, 44, 255),
                "o": (78, 50, 28, 255), "g": (168, 112, 28, 255), "A": (150, 156, 164, 255), "P": (226, 108, 48, 255),
                "E": (90, 160, 70, 255)}
    flat_item("prop_wand")
    flat_item("judge_stopwatch")
    flat_item("measuring_tape")
    flat_item("tally_counter")
    flat_item("canvas")
    flat_item("slate")
    flat_item("whiteboard")

    # Cleaner icons drawn from shapes (scripts/gen_icons.py) replace the first pixel-art ones; the organiser's
    # prop wand is drawn as a clipboard (LordFinn: real objects, no magic wands)
    import gen_icons
    for name, fn in (("prop_wand", gen_icons.clipboard), ("judge_stopwatch", gen_icons.judge_stopwatch),
                     ("measuring_tape", gen_icons.measuring_tape), ("tally_counter", gen_icons.tally_counter),
                     ("slate", gen_icons.slate_icon), ("whiteboard", gen_icons.whiteboard_icon), ("canvas", gen_icons.canvas_icon)):
        tex(name, fn(), "item")

    # ---------------------------------------------------------------- recipes of the tools
    shaped("big_red_button", ["R", "B", "I"], {"R": "minecraft:red_dye", "B": "minecraft:stone_button", "I": "minecraft:iron_block"},
           "big_red_button", 1, "redstone")
    shaped("judge_stopwatch", [" G ", "GCG", " G "], {"G": "minecraft:gold_nugget", "C": "minecraft:clock"}, "judge_stopwatch", 1, "equipment")
    shaped("measuring_tape", ["YI", "S "], {"Y": "minecraft:yellow_dye", "I": "minecraft:iron_ingot", "S": "minecraft:string"},
           "measuring_tape", 1, "equipment")
    shaped("tally_counter", ["B", "I"], {"B": "minecraft:stone_button", "I": "minecraft:iron_ingot"}, "tally_counter", 1, "equipment")
    shaped("canvas", ["GGG", "GWG", "GGG"], {"G": "minecraft:gold_nugget", "W": "minecraft:white_wool"}, "canvas", 1, "misc")
    shaped("slate", ["SSS", "SDS", "SSS"], {"S": "minecraft:stick", "D": "minecraft:polished_deepslate"}, "slate", 1, "misc")
    shaped("whiteboard", ["III", "IQI", "III"], {"I": "minecraft:iron_nugget", "Q": "minecraft:white_concrete"}, "whiteboard", 1, "misc")

    # ---------------------------------------------------------------- texts
    texts = {
        # key: (en, fr)
        "block.craftmaster.big_red_button": ("Big Red Button", "Gros bouton rouge"),
        "block.craftmaster.balance": ("Scales", "Balance"),
        "block.craftmaster.display_plinth": ("Display Plinth", "Socle d'exposition"),
        "item.craftmaster.prop_wand": ("Organiser's Clipboard", "Porte-bloc d'organisateur"),
        "item.craftmaster.prop_wand.hint": ("On a block: puts a task prop there (looks like the item in your other hand)",
                                            "Sur un bloc : y pose un objet d'épreuve (à l'apparence de l'objet dans l'autre main)"),
        "item.craftmaster.prop_wand.hint2": ("On a prop (or sneak + use it): its settings", "Sur un objet (ou accroupi + clic droit) : ses réglages"),
        "item.craftmaster.judge_stopwatch": ("Judge's Stopwatch", "Chronomètre du juge"),
        "item.craftmaster.judge_stopwatch.hint": ("On a contestant: start / stop their time · In the air: all the stopwatches",
                                                  "Sur un candidat : départ / arrêt de son temps · En l'air : tous les chronos"),
        "item.craftmaster.measuring_tape": ("Measuring Tape", "Mètre ruban"),
        "item.craftmaster.measuring_tape.hint": ("Use on a point, then on another: the exact distance, a tape shown for 10 s · Sneak: start over",
                                                 "Sur un point puis sur un autre : la distance exacte, ruban affiché 10 s · Accroupi : recommencer"),
        "item.craftmaster.tally_counter": ("Tally Counter", "Compteur"),
        "item.craftmaster.tally_counter.hint": ("On anything holding items (chest, barrel, hopper, minecart, donkey, armour stand, player...): counts them (only the kind held in the other hand, if any)",
                                                "Sur tout ce qui contient des objets (coffre, tonneau, entonnoir, wagonnet, âne, porte-armure, joueur…) : les compte (seulement ceux de l'autre main, s'il y en a)"),
        "item.craftmaster.canvas": ("Painter's Canvas", "Toile de peintre"),
        "item.craftmaster.slate": ("Slate", "Ardoise"),
        "item.craftmaster.whiteboard": ("Whiteboard", "Tableau blanc"),
        "item.craftmaster.board.hint": ("Hang it on a wall · Dye: paint (hold) · Sneak + dye: fill · Sponge: rub out",
                                        "Se pose sur un mur · Teinture : peindre (maintenir) · Accroupi + teinture : remplir · Éponge : gommer"),
        "item.craftmaster.board.hint2": ("Empty hand: write · Sneak + hit: take it down", "Main vide : écrire · Accroupi + frapper : décrocher"),
        "entity.craftmaster.trial_prop": ("Task Prop", "Objet d'épreuve"),
        "entity.craftmaster.board": ("Drawing Board", "Tableau à dessin"),
        "key.craftmaster.results": ("Results sheet (organisers)", "Feuille de résultats (organisateurs)"),
        # Prop settings screen
        "screen.craftmaster.prop": ("Task prop", "Objet d'épreuve"),
        "screen.craftmaster.prop.tab.look": ("Look", "Apparence"),
        "screen.craftmaster.prop.tab.effects": ("Effects", "Effets"),
        "screen.craftmaster.prop.tab.actions": ("Actions", "Actions"),
        "screen.craftmaster.prop.tab.rules": ("Rules", "Règles"),
        "screen.craftmaster.prop.save": ("Save", "Enregistrer"),
        "screen.craftmaster.prop.test": ("Test", "Tester"),
        "screen.craftmaster.prop.test.tip": ("Saves, then uses it as a contestant would", "Enregistre puis l'utilise comme un candidat"),
        "screen.craftmaster.prop.bad_number": ("A number is not valid", "Un nombre n'est pas valide"),
        "screen.craftmaster.prop.hint": ("Organisers: sneak + use the prop to come back here", "Organisateurs : accroupi + clic droit sur l'objet pour revenir ici"),
        "screen.craftmaster.prop.name": ("Name", "Nom"),
        "screen.craftmaster.prop.name.tip": ("Shown above it and in the messages; empty = the item's name", "Affiché au-dessus et dans les messages ; vide = nom de l'objet"),
        "screen.craftmaster.prop.show_name": ("Name shown", "Nom affiché"),
        "screen.craftmaster.prop.show_name.tip": ("Its name floats above it", "Son nom flotte au-dessus"),
        "screen.craftmaster.prop.glow": ("Glows", "Brille"),
        "screen.craftmaster.prop.glow.tip": ("Outlined, seen through walls", "Contour visible à travers les murs"),
        "screen.craftmaster.prop.scale": ("Size", "Taille"),
        "screen.craftmaster.prop.scale.tip": ("1 = one block, from 0.1 to 8", "1 = un bloc, de 0,1 à 8"),
        "screen.craftmaster.prop.rotation": ("Turn (°)", "Rotation (°)"),
        "screen.craftmaster.prop.rotation.tip": ("Around the vertical axis", "Autour de l'axe vertical"),
        "screen.craftmaster.prop.pitch": ("Tilt (°)", "Inclinaison (°)"),
        "screen.craftmaster.prop.pitch.tip": ("Forward / backward, -90 to 90", "Vers l'avant / l'arrière, de -90 à 90"),
        "screen.craftmaster.prop.spin": ("Spins", "Tourne"),
        "screen.craftmaster.prop.spin.tip": ("Turns slowly on itself", "Tourne lentement sur lui-même"),
        "screen.craftmaster.prop.bob": ("Floats", "Flotte"),
        "screen.craftmaster.prop.bob.tip": ("Floats gently up and down", "Monte et descend doucement"),
        "screen.craftmaster.prop.look_hand": ("Look = item in my hand", "Apparence = l'objet que je tiens"),
        "screen.craftmaster.prop.look_hand.tip": ("Any block or item, with its name, colours, player head...", "N'importe quel bloc ou objet, avec son nom, ses couleurs, une tête de joueur..."),
        "screen.craftmaster.prop.look_now": ("Shows: %s", "Montre : %s"),
        "screen.craftmaster.prop.message": ("Message", "Message"),
        "screen.craftmaster.prop.message.tip": ("{player} = their name, {count} = their uses, {total} = all uses", "{player} = son nom, {count} = ses utilisations, {total} = toutes"),
        "screen.craftmaster.prop.message_all": ("Message to everyone", "Message à tous"),
        "screen.craftmaster.prop.message_all.tip": ("Off: only the player who used it reads it", "Non : seul le joueur qui l'utilise le lit"),
        "screen.craftmaster.prop.sound": ("Sound", "Son"),
        "screen.craftmaster.prop.pitch_sound.tip": ("Pitch of the sound, 0.5 to 2", "Hauteur du son, de 0,5 à 2"),
        "screen.craftmaster.prop.give": ("Gives", "Donne"),
        "screen.craftmaster.prop.give.tip": ("Item id (minecraft:diamond); empty = nothing", "Identifiant (minecraft:diamond) ; vide = rien"),
        "screen.craftmaster.prop.give_count.tip": ("How many", "Combien"),
        "screen.craftmaster.prop.hand": ("Hand", "Main"),
        "screen.craftmaster.prop.give_hand.tip": ("Gives a copy of the item in my hand (its name, enchantments...)", "Donne une copie de l'objet que je tiens (son nom, ses enchantements...)"),
        "screen.craftmaster.prop.give_kept": ("Given: %s", "Donne : %s"),
        "screen.craftmaster.prop.note": ("Note", "Note"),
        "screen.craftmaster.prop.note_title.tip": ("Title of the note opened on the player's screen", "Titre de la note qui s'ouvre chez le joueur"),
        "screen.craftmaster.prop.note_text.placeholder": ("Text of the note (empty = no note)", "Texte de la note (vide = pas de note)"),
        "screen.craftmaster.prop.teleport": ("Teleport", "Téléporte"),
        "screen.craftmaster.prop.teleport.tip": ("Sends the player to x y z", "Envoie le joueur en x y z"),
        "screen.craftmaster.prop.tp.tip": ("x, y, z", "x, y, z"),
        "screen.craftmaster.prop.here": ("Here", "Ici"),
        "screen.craftmaster.prop.here.tip": ("Where I stand, looking where I look", "Là où je suis, en regardant où je regarde"),
        "screen.craftmaster.prop.pulse": ("Redstone", "Redstone"),
        "screen.craftmaster.prop.pulse.tip": ("A redstone block appears for a moment at this offset (must be air)", "Un bloc de redstone apparaît un instant à ce décalage (doit être de l'air)"),
        "screen.craftmaster.prop.pulse_offset.tip": ("Offset from the prop: x, y, z (default 0 -2 0: under the floor)", "Décalage depuis l'objet : x, y, z (défaut 0 -2 0 : sous le sol)"),
        "screen.craftmaster.prop.pulse_ticks.tip": ("Duration in ticks (20 = 1 s)", "Durée en ticks (20 = 1 s)"),
        "screen.craftmaster.prop.start_attempt": ("Starts the attempt", "Lance l'essai"),
        "screen.craftmaster.prop.start_attempt.tip": ("Starts the player's stopwatch", "Lance le chrono du joueur"),
        "screen.craftmaster.prop.end_attempt": ("Ends the attempt", "Termine l'essai"),
        "screen.craftmaster.prop.end_attempt.tip": ("Stops the player's stopwatch and writes the time on the results sheet", "Arrête le chrono du joueur et note le temps sur la feuille de résultats"),
        "screen.craftmaster.prop.once": ("Once per player", "Une fois par joueur"),
        "screen.craftmaster.prop.once.tip": ("Each player can use it only once", "Chaque joueur ne peut l'utiliser qu'une fois"),
        "screen.craftmaster.prop.contestants_only": ("Contestants only", "Candidats seulement"),
        "screen.craftmaster.prop.contestants_only.tip": ("The audience cannot trigger it", "Le public ne peut pas le déclencher"),
        "screen.craftmaster.prop.cooldown": ("Delay (s)", "Délai (s)"),
        "screen.craftmaster.prop.cooldown.tip": ("Before the same player can use it again", "Avant que le même joueur puisse le réutiliser"),
        "screen.craftmaster.prop.announce_first": ("Announce 1st", "Annonce le 1er"),
        "screen.craftmaster.prop.announce_first.tip": ("A title tells everyone who touched it first", "Un titre annonce à tous qui l'a touché en premier"),
        "screen.craftmaster.prop.memory": ("What it remembers", "Ce dont il se souvient"),
        "screen.craftmaster.prop.reset_stats": ("Reset its memory", "Remettre à zéro"),
        "screen.craftmaster.prop.remove": ("Remove the prop", "Supprimer l'objet"),
        "screen.craftmaster.prop.remove.tip": ("Removes it for good", "Le retire pour de bon"),
        # Results sheet
        "screen.craftmaster.results": ("Results sheet", "Feuille de résultats"),
        "screen.craftmaster.results.title": ("Results sheet", "Feuille de résultats"),
        "screen.craftmaster.results.manche": ("Round %s — %s", "Manche %s — %s"),
        "screen.craftmaster.results.current": ("  (playing)", "  (en cours)"),
        "screen.craftmaster.results.prev": ("Previous round", "Manche précédente"),
        "screen.craftmaster.results.next": ("Next round", "Manche suivante"),
        "screen.craftmaster.results.kind": ("Measure", "Mesure"),
        "screen.craftmaster.results.kind.tip": ("What is written for each contestant", "Ce qu'on note pour chaque candidat"),
        "screen.craftmaster.results.kind.time": ("Time", "Temps"),
        "screen.craftmaster.results.kind.count": ("Count", "Nombre"),
        "screen.craftmaster.results.kind.distance": ("Distance", "Distance"),
        "screen.craftmaster.results.kind.mark": ("Jury mark", "Note du jury"),
        "screen.craftmaster.results.kind.weight": ("Weight", "Poids"),
        "screen.craftmaster.results.lower": ("Smallest wins", "Le plus petit gagne"),
        "screen.craftmaster.results.higher": ("Biggest wins", "Le plus grand gagne"),
        "screen.craftmaster.results.order": ("Order", "Ordre"),
        "screen.craftmaster.results.order.tip": ("Which way the ranking goes", "Dans quel sens va le classement"),
        "screen.craftmaster.results.scale": ("Scale", "Barème"),
        "screen.craftmaster.results.scale.tip": ("Points of the 1st, 2nd, 3rd... (Enter to keep it). Ties share a place.", "Points du 1er, 2e, 3e... (Entrée pour le garder). Les ex-aequo partagent la place."),
        "screen.craftmaster.results.value_hint": ("value", "valeur"),
        "screen.craftmaster.results.value.tip": ("42 · 1:05 · 12,5 — Enter: next line · empty: clear", "42 · 1:05 · 12,5 — Entrée : ligne suivante · vide : effacer"),
        "screen.craftmaster.results.dq_on": ("DQ", "DQ"),
        "screen.craftmaster.results.dq_off": ("DQ", "DQ"),
        "screen.craftmaster.results.dq.tip": ("Disqualified: 0 point (click again to lift)", "Disqualifié : 0 point (recliquer pour lever)"),
        "screen.craftmaster.results.watch_start.tip": ("Start their stopwatch (solo attempt, everyone sees it)", "Lancer son chrono (essai en solo, tout le monde le voit)"),
        "screen.craftmaster.results.watch_stop.tip": ("Stop their stopwatch: the time is written", "Arrêter son chrono : le temps est noté"),
        "screen.craftmaster.results.apply": ("Apply the scale", "Appliquer le barème"),
        "screen.craftmaster.results.apply.tip": ("Gives each contestant the proposed points (score of the round, undoable)", "Donne à chacun les points proposés (score de la manche, annulable)"),
        "screen.craftmaster.results.clear": ("Clear", "Vider"),
        "screen.craftmaster.results.clear.tip": ("Clears every value of this sheet (undoable)", "Efface toutes les valeurs de la feuille (annulable)"),
        "screen.craftmaster.results.col.player": ("Contestant", "Candidat"),
        "screen.craftmaster.results.col.value": ("Result", "Résultat"),
        "screen.craftmaster.results.col.place": ("Place", "Place"),
        "screen.craftmaster.results.col.points": ("Points", "Points"),
        "screen.craftmaster.results.empty": ("No contestant in the active season", "Aucun candidat dans la saison active"),
        # Boards
        "screen.craftmaster.board": ("Write on the board", "Écrire sur le tableau"),
        "screen.craftmaster.board.placeholder": ("A few lines, centred on the board", "Quelques lignes, centrées sur le tableau"),
    }
    sounds = {"none": ("None", "Aucun"), "bell": ("Bell", "Cloche"), "chime": ("Chime", "Carillon"), "pling": ("Pling", "Pling"),
              "levelup": ("Level up", "Niveau gagné"), "gong": ("Big bell", "Grosse cloche"), "buzz": ("Buzz", "Bzzz"),
              "yes": ("Villager yes", "Villageois oui"), "no": ("Villager no", "Villageois non"), "anvil": ("Anvil", "Enclume"),
              "firework": ("Firework", "Feu d'artifice"), "chest": ("Chest", "Coffre"), "cat": ("Cat", "Chat"),
              "orb": ("XP orb", "Orbe d'XP"), "door": ("Door", "Porte")}
    for k, v in sounds.items():
        texts[f"screen.craftmaster.prop.sound.{k}"] = v
    for k, (e, f) in texts.items():
        en[k] = e
        fr[k] = f
