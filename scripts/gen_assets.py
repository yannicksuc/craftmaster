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
        cube([2, 9, 12], [4, 21, 14], "#log"), cube([12, 9, 12], [14, 21, 14], "#log"),  # back posts
        cube([4, 12, 12.5], [12, 20, 13.5], "#planks"),                # backrest
        cube([4, 20, 12], [12, 21, 14], "#log"),                       # top rail
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
    write_json(os.path.join(A, f"models/block/{name}.json"),
               {"parent": "craftmaster:block/curtain_template", "textures": {"fabric": f"craftmaster:block/{name}"}})
    # Open: nothing drawn (the particles still use the fabric when it is broken)
    write_json(os.path.join(A, f"models/block/{name}_open.json"), {"textures": {"particle": f"craftmaster:block/{name}"}})
    variants = {}
    for f, y in FACING_Y.items():
        for is_open in ("false", "true"):
            m = f"craftmaster:block/{name}" + ("_open" if is_open == "true" else "")
            v = {"model": m}
            if y:
                v["y"] = y
            variants[f"facing={f},open={is_open}"] = v
    write_json(os.path.join(A, f"blockstates/{name}.json"), {"variants": variants})
    write_json(os.path.join(A, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"craftmaster:block/{name}"}})
    write_json(os.path.join(D, f"craftmaster/loot_table/blocks/{name}.json"), self_drop(name))

    # Velvet texture: vertical folds (light in the middle of each pleat), a darker hem at the bottom
    r0, g0, b0 = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    rnd = random.Random(color)
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            fold = 0.78 + 0.30 * math.sin((x % 4 + 0.5) / 4 * math.pi)
            shade = fold * (0.97 + rnd.random() * 0.06)
            if y >= 14:
                shade *= 0.78
            row.append((min(255, int(r0 * shade)), min(255, int(g0 * shade)), min(255, int(b0 * shade)), 255))
        rows.append(row)
    write_png(os.path.join(A, f"textures/block/{name}.png"), 16, 16, rows)

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
    "screen.craftmaster.other_tasks": "Other tasks",
    "screen.craftmaster.hidden_season": "Ranking hidden until the reveal",
    "message.craftmaster.ranking_unavailable": "No ranking to show yet",
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
    "screen.craftmaster.other_tasks": "Autres tâches",
    "screen.craftmaster.hidden_season": "Classement caché jusqu'à la révélation",
    "message.craftmaster.ranking_unavailable": "Aucun classement à afficher pour l'instant",
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
for wood, (_, en_name, fr_name) in WOODS.items():
    en[f"block.craftmaster.{wood}_chair"] = f"{en_name} Chair"
    fr[f"block.craftmaster.{wood}_chair"] = f"Chaise {fr_name}"
for color, (_, en_name, fr_name) in COLORS.items():
    en[f"block.craftmaster.{color}_curtain"] = f"{en_name} Curtain"
    fr[f"block.craftmaster.{color}_curtain"] = f"Rideau {fr_name}"
write_json(os.path.join(A, "lang/en_us.json"), en)
write_json(os.path.join(A, "lang/fr_fr.json"), fr)
print("ok")
