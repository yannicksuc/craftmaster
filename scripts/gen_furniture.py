"""LordFinn's own furniture, from the ItemsAdder pack of his server (pack "sandbox", copied in scripts/ia_sandbox):
the throne, the contestants' chair, the frame, the studio lights and softboxes (item models drawn by
client/studio/FurnitureRenderer like the server's item frames / item displays), the tripod and its rod (block models,
also drawn under the cameras), the black and white tiles and the globe panel. (ia_sandbox also keeps the server's task icons, task.png and
task_enveloppe.png, unused here.)

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

    for name, src in ITEM_MODELS.items():
        wj(os.path.join(A, f"models/item/furniture/{name}.json"), load(src))
        item_def(f"furniture/{name}", f"craftmaster:item/furniture/{name}")
    for name, src in BLOCK_MODELS.items():
        wj(os.path.join(A, f"models/block/furniture/{name}.json"), load(src))
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

    # Plain blocks
    for block in ("tripod", "tripod_rod", "globe_panel"):
        wj(os.path.join(A, f"blockstates/{block}.json"), {"variants": {"": {"model": f"craftmaster:block/furniture/{block}"}}})
        item_def(block, f"craftmaster:block/furniture/{block}")
    wj(os.path.join(A, "models/block/furniture/checker_tile.json"),
       {"parent": "minecraft:block/cube_all", "textures": {"all": "craftmaster:block/furniture/black_and_white_tile"}})
    wj(os.path.join(A, "blockstates/checker_tile.json"), {"variants": {"": {"model": "craftmaster:block/furniture/checker_tile"}}})
    item_def("checker_tile", "craftmaster:block/furniture/checker_tile")

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
