"""LordFinn's own furniture, from the ItemsAdder pack of his server (pack "sandbox", copied in scripts/ia_sandbox):
the throne, the contestants' chair, the frame, the studio lights and softboxes (item models drawn by
client/studio/FurnitureRenderer like the server's item frames / item displays), the tripod and its rod (block models,
also drawn under the cameras), the black and white tiles and the globe panel. (ia_sandbox also keeps the server's task icons, task.png and
task_enveloppe.png: the task envelope's icons, see gen_assets.py.)

The models are copied as they are; only their texture names change (sandbox:... -> craftmaster:block/furniture/...).
Called by gen_assets.py after the studio set.
"""
import json
import os
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "ia_sandbox")

# mod name -> (source model, texture names): item models shown by FurnitureRenderer
ITEM_MODELS = {
    "throne": "craftmaster-chair",
    "candidate_chair": "craftmaster-basic-chair",
    "decor_frame": "craftmaster-frame",
    **{f"studio_light_{i}": f"studio_light_{i}" for i in range(1, 6)},
    **{f"softbox_{i}": f"softbox_{i}" for i in range(1, 5)},
}
# Inventory icons fitted to the slot (gen_toys.gui_fit): px of their larger side, in LordFinn's gui angle
GUI_FIT = {"throne": 15, "candidate_chair": 14, "softbox_2": 14}
# Block models (placed as they are)
BLOCK_MODELS = {"tripod": "tripod", "tripod_rod": "tripod_rod", "globe_panel": "craftmaster-globe"}
TEXTURES = ["craftmaster-chair", "craftmaster-basic-chair", "craftmaster-frame", "studio_light", "softbox", "tripod",
            "tripod_rod", "black_and_white_tile", "white_stained_glass_with_door"]


def tex_name(src):
    return src.replace("-", "_")


def convert(model):
    """The server's model with our texture names; editor-only keys and the item/paper parent dropped."""
    m = {k: v for k, v in model.items() if k not in ("format_version", "credit", "parent", "groups")}
    m["textures"] = {k: ("craftmaster:block/furniture/" + tex_name(v.split("/")[-1]) if v.startswith("sandbox:") else v)
                     for k, v in model["textures"].items()}
    return m


def generate(g):
    A, D, wj = g.A, g.D, g.write_json
    en, fr = g.en, g.fr

    os.makedirs(os.path.join(A, "textures/block/furniture"), exist_ok=True)
    for t in TEXTURES:
        shutil.copyfile(os.path.join(SRC, "textures", t + ".png"), os.path.join(A, "textures/block/furniture", tex_name(t) + ".png"))

    def load(name):
        with open(os.path.join(SRC, "models", name + ".json"), encoding="utf-8") as f:
            return convert(json.load(f))

    def item_def(path, model):
        wj(os.path.join(A, f"items/{path}.json"), {"model": {"type": "minecraft:model", "model": model}})

    from gen_toys import gui_fit
    for name, src in ITEM_MODELS.items():
        m = load(src)
        if name in GUI_FIT:
            gui = m["display"]["gui"]
            m["display"]["gui"] = gui_fit(m["elements"], GUI_FIT[name], tuple(gui["rotation"]))
        wj(os.path.join(A, f"models/item/furniture/{name}.json"), m)
        item_def(f"furniture/{name}", f"craftmaster:item/furniture/{name}")
    for name, src in BLOCK_MODELS.items():
        m = load(src)
        if name == "globe_panel":
            m = {"parent": "minecraft:block/block", **m}   # inventory, hand, ground: like a block
        wj(os.path.join(A, f"models/block/furniture/{name}.json"), m)
        item_def(f"furniture/{name}", f"craftmaster:block/furniture/{name}")

    # Blocks drawn by FurnitureRenderer: an empty block model (only the breaking particles)
    def drawn(block, particle, icon):
        wj(os.path.join(A, f"models/block/furniture/{block}_empty.json"), {"textures": {"particle": f"craftmaster:block/furniture/{particle}"}})
        wj(os.path.join(A, f"blockstates/{block}.json"), {"variants": {"": {"model": f"craftmaster:block/furniture/{block}_empty"}}})
        item_def(block, f"craftmaster:item/furniture/{icon}")

    drawn("throne", "craftmaster_chair", "throne")
    drawn("candidate_chair", "craftmaster_basic_chair", "candidate_chair")
    drawn("decor_frame", "craftmaster_frame", "decor_frame")
    drawn("studio_light", "studio_light", "studio_light_3")
    drawn("softbox", "softbox", "softbox_2")

    rig(g)

    # Plain blocks
    for block in ("tripod", "globe_panel"):
        wj(os.path.join(A, f"blockstates/{block}.json"), {"variants": {"": {"model": f"craftmaster:block/furniture/{block}"}}})
        item_def(block, f"craftmaster:block/furniture/{block}")
    wj(os.path.join(A, "models/block/furniture/checker_tile.json"),
       {"parent": "minecraft:block/cube_all", "textures": {"all": "craftmaster:block/furniture/black_and_white_tile"}})
    wj(os.path.join(A, "blockstates/checker_tile.json"), {"variants": {"": {"model": "craftmaster:block/furniture/checker_tile"}}})
    item_def("checker_tile", "craftmaster:block/furniture/checker_tile")
    item_def("tripod_rod", "craftmaster:block/furniture/tripod_rod")

    # Rig rods are climbed like a ladder (a pole)
    path = os.path.join(D, "minecraft/tags/block/climbable.json")
    current = {"replace": False, "values": []}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            current = json.load(f)
    if "craftmaster:tripod_rod" not in current["values"]:
        current["values"].append("craftmaster:tripod_rod")
    wj(path, current)

    blocks = ["candidate_chair", "studio_light", "softbox", "decor_frame", "tripod", "tripod_rod", "checker_tile", "globe_panel"]
    for b in blocks:
        wj(os.path.join(D, f"craftmaster/loot_table/blocks/{b}.json"), g.self_drop(b))
    for tag, values in (("axe", ["candidate_chair", "decor_frame"]),
                        ("pickaxe", ["studio_light", "softbox", "tripod", "tripod_rod", "checker_tile"])):
        path = os.path.join(D, f"minecraft/tags/block/mineable/{tag}.json")
        with open(path, encoding="utf-8") as f:
            current = json.load(f)
        current["values"] += [f"craftmaster:{b}" for b in values if f"craftmaster:{b}" not in current["values"]]
        wj(path, current)

    names = {
        "candidate_chair": ("Contestant's Chair", "Chaise de candidat"),
        "studio_light": ("Studio Light", "Lumière de studio"),
        "softbox": ("Softbox", "Softbox"),
        "decor_frame": ("Frame", "Cadre"),
        "tripod": ("Tripod Stand", "Pied de trépied"),
        "tripod_rod": ("Tripod Rod", "Barre de trépied"),
        "checker_tile": ("Black and White Tile", "Carrelage noir et blanc"),
        "globe_panel": ("CraftMaster Globe Panel", "Paroi du globe CraftMaster"),
    }
    for b, (e, f) in names.items():
        en[f"block.craftmaster.{b}"] = e
        fr[f"block.craftmaster.{b}"] = f
    en.update({
        "message.craftmaster.furniture_tilt": "Angle %s / %s (sneak + right-click: turn it)",
        "message.craftmaster.furniture_turned": "Turned by 45°",
    })
    fr.update({
        "message.craftmaster.furniture_tilt": "Angle %s / %s (accroupi + clic droit : la tourner)",
        "message.craftmaster.furniture_turned": "Tournée de 45°",
    })


# ------------------------------------------------------------------ rig: tripod rods linked on all 6 sides
# The rod (RigRodBlock) is a multipart of LordFinn's own rod pieces (tripod_rod.json, same texture and thickness):
# one arm per linked side, from the centre to the face, ending in his 4x4 cap (the plate / coupler), and a 4x4x4
# knuckle where arms meet at an angle. The conditions mirror RigLinks.arms / RigLinks.knuckle: a rod using a single
# axis (or none) goes right through its block.
SIDES = ["down", "up", "north", "south", "west", "east"]
OPP = {"down": "up", "up": "down", "north": "south", "south": "north", "west": "east", "east": "west"}
AXIS = {"down": "y", "up": "y", "north": "z", "south": "z", "west": "x", "east": "x"}
# Blockstate rotation of the up arm toward each side (like the end rod)
ARM_ROT = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180},
           "east": {"x": 90, "y": 90}, "west": {"x": 90, "y": 270}}


def rig(g):
    A, wj = g.A, g.write_json
    tex = {"0": "craftmaster:block/furniture/tripod_rod", "particle": "craftmaster:block/furniture/tripod_rod"}
    # LordFinn's cap: 4x4, 1 px thick, its own UVs
    cap_faces = {
        "north": {"uv": [8, 8, 12, 9], "texture": "#0"},
        "east": {"uv": [8, 9, 12, 10], "texture": "#0"},
        "south": {"uv": [8, 10, 12, 11], "texture": "#0"},
        "west": {"uv": [8, 11, 12, 12], "texture": "#0"},
        "up": {"uv": [12, 4, 8, 0], "texture": "#0", "cullface": "up"},
        "down": {"uv": [12, 4, 8, 8], "texture": "#0"},
    }
    # Up arm: the top half of his tube (y 8..15, the upper 7 rows of its sides) and the top cap; no inner end face
    wj(os.path.join(A, "models/block/furniture/rig_arm.json"), {"textures": tex, "elements": [
        {"from": [7, 8, 7], "to": [9, 15, 9], "faces": {
            "north": {"uv": [0, 0, 2, 7], "texture": "#0"},
            "east": {"uv": [2, 0, 4, 7], "texture": "#0"},
            "south": {"uv": [4, 0, 6, 7], "texture": "#0"},
            "west": {"uv": [6, 0, 8, 7], "texture": "#0"}}},
        {"from": [6, 15, 6], "to": [10, 16, 10], "faces": cap_faces},
    ]})
    # Also drawn by FurnitureRenderer as the wall bracket of a studio light (the cap is the plate on the wall)
    wj(os.path.join(A, "items/furniture/rig_arm.json"),
       {"model": {"type": "minecraft:model", "model": "craftmaster:block/furniture/rig_arm"}})
    # Knuckle: a 4x4x4 clamp, the cap's face on all sides
    wj(os.path.join(A, "models/block/furniture/rig_knuckle.json"), {"textures": tex, "elements": [
        {"from": [6, 6, 6], "to": [10, 10, 10], "faces": {
            f: {"uv": [12, 4, 8, 8] if f == "down" else [12, 4, 8, 0], "texture": "#0"} for f in SIDES}},
    ]})
    # Also drawn by CameraRenderer where a camera's own arm meets a rod at an angle
    wj(os.path.join(A, "items/furniture/rig_knuckle.json"),
       {"model": {"type": "minecraft:model", "model": "craftmaster:block/furniture/rig_knuckle"}})

    parts = []
    for side in SIDES:
        axis = AXIS[side]
        # Linked, or the opposite side is the only link on this axis and nothing else is linked: goes through
        when = [{side: "true"}, {OPP[side]: "true", **{s: "false" for s in SIDES if AXIS[s] != axis}}]
        if axis == "y":
            when.append({s: "false" for s in SIDES})  # a lone rod stands upright
        parts.append({"when": {"OR": when}, "apply": {"model": "craftmaster:block/furniture/rig_arm", **ARM_ROT[side]}})
    pairs = [{a: "true", b: "true"} for i, a in enumerate(SIDES) for b in SIDES[i + 1:] if AXIS[a] != AXIS[b]]
    parts.append({"when": {"OR": pairs}, "apply": {"model": "craftmaster:block/furniture/rig_knuckle"}})
    wj(os.path.join(A, "blockstates/tripod_rod.json"), {"multipart": parts})
