# Génère le pack de données vanilla « craftmaster-pomme » depuis le schéma de LordFinn.
# Toutes les positions sont relatives au coin de la salle (marqueur « pomme_salle » en ~0.5 ~ ~0.5).
import sys, os, json, re, shutil
import nbtlib
from nbtlib import serialize_tag

SCHEM, OUT = sys.argv[1], sys.argv[2]
sch = nbtlib.load(SCHEM)['Schematic']
W, H, L = int(sch['Width']), int(sch['Height']), int(sch['Length'])
pal = {int(v): k for k, v in sch['Blocks']['Palette'].items()}
raw = bytes(x & 0xff for x in sch['Blocks']['Data']); arr = []; i = 0
while i < len(raw):
    v = s = 0
    while True:
        b = raw[i]; i += 1; v |= (b & 0x7f) << s; s += 7
        if not b & 0x80: break
    arr.append(v)
# Les blocs de commande du schéma sont les consignes écrites de LordFinn (mises en œuvre par ce pack) :
# chacun est remplacé par le bloc qui l'entoure, pour qu'aucun ne reste visible.
OVERRIDE = {
    (49, 2, 28): "minecraft:dirt", (49, 2, 29): "minecraft:dirt", (49, 2, 30): "minecraft:dirt",   # « jouer son 1..4 »
    (49, 2, 31): "minecraft:dirt", (49, 2, 32): "minecraft:dirt",                                  # riricococo + bouton bleu
    (27, 3, 17): "minecraft:stripped_spruce_log[axis=y]",       # bouton rouge, code de l'étagère, porte
    (38, 12, 31): "minecraft:beehive[facing=north,honey_level=0]",   # bouton du plafond : petit 10 s
    (36, 5, 16): "craftmaster:timer_tablet[pose=stand,rotation=2]",  # le minuteur posé par LordFinn dans sa salle (19/11)
    # Deux fours parmi les hauts-fourneaux (20/11) : le sable ne cuit qu'au four (verre → fioles → miel → sucre)
    (34, 5, 37): "minecraft:furnace[facing=north,lit=false]", (35, 5, 37): "minecraft:furnace[facing=north,lit=false]",
    # Trois sables (20/11), que casse la pelle en fer : un dans le trou du poulailler, sur la terre cuite jaune,
    # deux dans le sol du couloir qui mène au poulailler (à la place des planches)
    (47, 3, 28): "minecraft:sand", (42, 3, 30): "minecraft:sand", (43, 3, 30): "minecraft:sand",
}
# Les changements que LordFinn fait ensuite dans la salle de référence du serveur (import_ref.py → modifs.json) :
# blocs, contenus de conteneurs et décors (cadres, tableaux, displays), par-dessus le schéma et les surcharges ci-dessus
def load_modifs():
    p = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'modifs.json')
    return json.load(open(p, encoding='utf-8')) if os.path.exists(p) else {}
MODIFS = load_modifs()
for _k, _v in MODIFS.get('blocks', {}).items():
    OVERRIDE[tuple(int(c) for c in _k.split(','))] = _v
# Le minuteur de LordFinn (même aspect) : il montre le chrono du candidat de la salle, de sa lettre à sa pomme
TIMER = {"Settings": nbtlib.String('{"mode":"STOPWATCH","format":"MINUTES","seconds":60,"label":"","showLabel":false,"color":"RED","warnSeconds":10,"warnColor":"RED","ghostSegments":true,"frame":"OAK","mat":"NONE","size":1,"pose":"STAND","tilt":15,"clickToggles":false,"redstone":"OFF","startOnRound":false,"stopOnRoundEnd":false,"stopWithTimer":false,"signalWhenDone":false,"endSound":"NONE","blinkWhenDone":false,"followContestant":true,"group":""}'),
         "Running": nbtlib.Byte(0), "Powered": nbtlib.Byte(0), "Banked": nbtlib.Long(0), "StartedAt": nbtlib.Long(0)}
def blk(x, y, z): return OVERRIDE.get((x, y, z)) or pal[arr[x + z * W + y * W * L]]
def name(x, y, z): return blk(x, y, z).split('[')[0].replace('minecraft:', '')
MIN = (-361, -64, -167)          # coin du schéma dans le monde d'origine (Origin + Offset)
REF = (0, 60, 0)                 # coin de la salle de référence dans minecraft:pomme
STRIDE = 65                      # copies : 60 de large + écart 3 + murs 2 (comme le mod)
COPIES = 5                       # Arreaubaz, Cyllu, SuneeToo, le_toad_jaune, LordFinn (arenas.json)
DIM = "minecraft:pomme"

if os.path.exists(OUT): shutil.rmtree(OUT)
F = {}
def fn(path, lines):
    F.setdefault(path, []).extend(lines if isinstance(lines, list) else [lines])
def snbt(t): return serialize_tag(t, compact=True)
def j(o): return json.dumps(o, ensure_ascii=False, separators=(',', ':'))

# ------------------------------------------------------------ règles des objets (mode aventure)
RULES = {
    "iron_hoe":      ({"can_break": {"blocks": ["wheat"]}, "can_place_on": {"blocks": ["dirt", "coarse_dirt", "rooted_dirt", "grass_block"]}},
                      ["Casse : blé", "Laboure : terre, terre stérile, terre racinée"]),
    # + le béton blanc : le béton en poudre au-dessus du coffre de la tête de Steve, durci au seau d'eau
    "iron_pickaxe":  ({"can_break": {"blocks": ["iron_ore", "coal_ore", "raw_iron_block", "white_concrete"]}},
                      ["Casse : minerai de fer, minerai de charbon,", "bloc de fer brut, béton blanc"]),
    "iron_shovel":   ({"can_break": {"blocks": ["sand"]}}, ["Casse : sable"]),
    "iron_axe":      ({"can_break": {"blocks": ["acacia_log", "acacia_wood", "stripped_acacia_log", "stripped_acacia_wood"]}}, ["Casse : bois d'acacia"]),
    "bucket":        ({"can_place_on": {"blocks": ["cauldron", "water_cauldron"]}}, ["Utilisable sur : chaudron, vache"]),
    "water_bucket":  ({"can_place_on": {"blocks": ["white_concrete_powder"]}}, ["Se vide sur : béton en poudre blanc"]),
    "glass_bottle":  ({"can_place_on": {"blocks": ["cauldron", "water_cauldron", "beehive", "bee_nest"]}}, ["Utilisable sur : chaudron, ruche"]),
    "potion":        ({"can_place_on": {"blocks": ["cauldron", "water_cauldron"]}}, ["Utilisable sur : chaudron"]),
    "wooden_hoe":    ({"can_break": {"blocks": ["oak_leaves"]}}, ["Casse : feuilles de chêne"]),
    "wooden_axe":    ({"can_break": {"blocks": ["crafting_table"]}}, ["Casse : table de craft"]),
    "crafting_table": ({"can_place_on": {"blocks": ["pale_oak_wall_sign"]}}, ["Se pose contre : panneau blanc"]),
    "mace":          ({"can_break": {"blocks": ["light_gray_stained_glass_pane"]}}, ["Casse : vitres gris clair"]),
    "stone_pickaxe": ({"can_break": {"blocks": ["iron_ore"]}}, ["Casse : minerai de fer"]),
    "wheat_seeds":   ({"can_place_on": {"blocks": ["farmland"]}}, ["Se plante sur : terre labourée"]),
    "bone_meal":     ({"can_place_on": {"blocks": ["wheat", "carrots", "beetroots"]}}, ["Utilisable sur : blé, carottes, betteraves"]),
    "cow_spawn_egg": ({"can_place_on": [{}]}, []),
}
HIDE = {"hidden_components": ["minecraft:can_break", "minecraft:can_place_on"]}
def rule_components(item):
    c, lore = RULES[item]
    out = {"minecraft:" + k: v for k, v in c.items()}
    out["minecraft:tooltip_display"] = HIDE
    if lore: out["minecraft:lore"] = [{"text": t, "italic": False, "color": "gray"} for t in lore]
    return out
def comp_snbt(c):
    return ",".join("%s=%s" % (k, re.sub(r'"(\w+)":', r'\1:', j(v))) for k, v in c.items())
# Composants que d'anciennes règles avaient mis et que les objets déjà distribués perdent (20/11 : la Farine
# ne fait plus pousser la canne à sucre, retirée de la salle)
STRIP = {"bone_meal": ["minecraft:consumable"]}
mods = []
for item in RULES:
    comps = rule_components(item)
    for k in STRIP.get(item, []): comps["!" + k] = {}
    mods.append({"function": "minecraft:filtered", "item_filter": {"items": "minecraft:" + item},
                 "on_pass": {"function": "minecraft:set_components", "components": comps}})
F_JSON = {"data/pomme/item_modifier/regles.json": mods}

def with_rules(item_tag):
    """Adds the rule components to an item compound from the schematic (chests, shelves...)."""
    iid = str(item_tag['id']).replace('minecraft:', '')
    if iid in RULES:
        comps = item_tag.setdefault('components', nbtlib.Compound())
        for k, v in rule_components(iid).items():
            comps[k] = nbtlib.parse_nbt(re.sub(r'"([A-Za-z_]\w*)":', r'\1:', j(v)).replace(':true', ':1b').replace(':false', ':0b'))
    return item_tag

# ------------------------------------------------------------ blocs (collage sans aucune mise à jour)
be = {}
for e in sch['Blocks']['BlockEntities']:
    if tuple(int(x) for x in e['Pos']) not in OVERRIDE: be[tuple(int(x) for x in e['Pos'])] = e['Data']
# Objets ajoutés au schéma de LordFinn, cachés dans des conteneurs ({position: [(objet, nombre)]}). Vide depuis le
# 20/11 : les 3 sables des tonneaux sont devenus des blocs (OVERRIDE), voir salle/sans_canne pour les salles construites
CACHETTES = {}
DROP = {'id', 'x', 'y', 'z', 'keepPacked', 'PublicBukkitValues', 'Bukkit.MaxEntities'}
def be_snbt(pos):
    d = be.get(pos)
    if d is None: return ""
    d = nbtlib.Compound({k: v for k, v in d.items() if k not in DROP and not k.startswith('Bukkit') and not k.startswith('Paper')})
    if '%d,%d,%d' % pos in MODIFS.get('contents', {}):
        d['Items'] = nbtlib.parse_nbt(MODIFS['contents']['%d,%d,%d' % pos])
    if 'Items' in d:
        for it in d['Items']: with_rules(it)
    if pos in CACHETTES:
        used = {int(it['Slot']) for it in d.get('Items', [])}
        items = d.setdefault('Items', nbtlib.List[nbtlib.Compound]())
        for iid, n in CACHETTES[pos]:
            slot = next(k for k in range(26, -1, -1) if k not in used)
            used.add(slot)
            items.append(nbtlib.Compound({'Slot': nbtlib.Byte(slot), 'id': nbtlib.String('minecraft:' + iid), 'count': nbtlib.Int(n)}))
    if pos == (28, 4, 27):   # plus de canne à sucre (20/11) : le sucre du gâteau vient seulement du miel
        d['Items'] = nbtlib.List[nbtlib.Compound]([it for it in d['Items'] if str(it['id']) != 'minecraft:sugar_cane'])
    if pos == (38, 2, 25):   # la tête de Steve du coffre
        for it in d['Items']:
            if str(it['id']) == 'minecraft:player_head':
                it.setdefault('components', nbtlib.Compound())['minecraft:lore'] = nbtlib.parse_nbt(
                    '[{text:"Cette tête n\'est pas encore décrépie,",italic:0b,color:"gray"},{text:"mais peut etre que plus bas elle le sera.",italic:0b,color:"gray"}]')
    if pos == (49, 6, 32):   # le panneau « ? : » : un clic ouvre la fenêtre du code (le jeu ne laisse pas écrire en aventure)
        d['is_waxed'] = nbtlib.Byte(1)
        d['front_text'] = nbtlib.parse_nbt('{has_glowing_text:0b,color:"black",messages:[{text:"? :",click_event:{action:"run_command",command:"function pomme:code/ouvrir"}},{text:""},{text:""},{text:""}]}')
    if 'components' in d and not len(d['components']): del d['components']
    return snbt(d) if len(d) else ""

blocks = []
for y in range(H):
    for z in range(L):
        x = 0
        while x < W:
            b = blk(x, y, z); pos = (x, y, z)
            nb = be_snbt(pos) if pos in be else (snbt(nbtlib.Compound(TIMER)) if pos == (36, 5, 16) else "")
            if nb:
                blocks.append("setblock ~%d ~%d ~%d %s%s strict" % (x, y, z, b, nb)); x += 1; continue
            x2 = x
            while x2 + 1 < W and blk(x2 + 1, y, z) == b and (x2 + 1, y, z) not in be and (x2 + 1, y, z) != (36, 5, 16): x2 += 1
            if x2 == x: blocks.append("setblock ~%d ~%d ~%d %s strict" % (x, y, z, b))
            else: blocks.append("fill ~%d ~%d ~%d ~%d ~%d ~%d %s strict" % (x, y, z, x2, y, z, b))
            x = x2 + 1
fn("construire/blocs", blocks)

# ------------------------------------------------------------ entités du schéma (+ ajouts)
ENT_DROP = {'UUID', 'Pos', 'Motion', 'block_pos', 'Id', 'Paper.Origin', 'Paper.OriginWorld', 'WorldUUIDMost', 'WorldUUIDLeast',
            'Brain', 'HurtByTimestamp', 'FallFlying', 'fall_distance', 'OnGround', 'Air', 'PortalCooldown', 'Fire', 'DeathTime', 'HurtTime'}
ents = []
DECOR = {'minecraft:item_frame', 'minecraft:glow_item_frame', 'minecraft:painting', 'minecraft:block_display', 'minecraft:item_display'}
for e in sch['Entities']:
    d = e['Data']; eid = str(e['Id'])
    if MODIFS.get('decor') is not None and eid in DECOR: continue     # the decor as it is now in the reference (below)
    nb = nbtlib.Compound({k: v for k, v in d.items() if k not in ENT_DROP and not k.startswith(('Bukkit', 'Paper', 'Spigot'))})
    if 'block_pos' in d:
        bp = [int(d['block_pos'][k]) - MIN[k] for k in range(3)]
        # A painting of even height (Pos on a whole block) is stored one block above where it hung: lowered back
        # (« pond », 3×4, then hides the niche of the chest below it as built).
        if eid == 'minecraft:painting' and abs(float(d['Pos'][1]) - round(float(d['Pos'][1]))) < 1e-6: bp[1] -= 1
        px, py, pz = bp[0] + 0.5, bp[1] + 0.5, bp[2] + 0.5
    else:
        ap = [float(x) for x in d['Pos']]
        px, py, pz = ap[0] - MIN[0], ap[1] - MIN[1], ap[2] - MIN[2]
    if eid == 'minecraft:item':
        # Eggs lying in the schematic would look laid by the hens (which never lay here): left out
        if str(d.get('Item', {}).get('id', '')).endswith('egg'): continue
        nb['Age'] = nbtlib.Short(-32768); nb['PickupDelay'] = nbtlib.Short(0)
    if eid == 'minecraft:chicken':
        nb['PersistenceRequired'] = nbtlib.Byte(1); nb['EggLayTime'] = nbtlib.Int(999999)
    if 'Item' in nb: with_rules(nb['Item'])
    tags = list(nb.get('Tags', [])) + ['pomme_e']
    nb['Tags'] = nbtlib.List[nbtlib.String](tags)
    ents.append("summon %s ~%.4f ~%.4f ~%.4f %s" % (eid, px, py, pz, snbt(nb)))

# Les décors de la salle de référence tels que LordFinn les a laissés (modifs.json), à leur place exacte
for dec in MODIFS.get('decor') or []:
    nb = nbtlib.parse_nbt(dec['nbt'])
    nb = nbtlib.Compound({k: v for k, v in nb.items() if k not in ENT_DROP})
    if 'Item' in nb: with_rules(nb['Item'])
    nb['Tags'] = nbtlib.List[nbtlib.String](list(nb.get('Tags', [])) + ['pomme_e'])
    ents.append("summon %s ~%.4f ~%.4f ~%.4f %s" % ((dec['id'],) + tuple(dec['pos']) + (snbt(nb),)))

def air(x, y, z): return name(x, y, z) in ('air', 'cave_air', 'light')
def free(x, z, y=4): return air(x, y, z) and air(x, y + 1, z) and not air(x, y - 1, z)

# --- Villageois de la boulangerie : derrière le comptoir en acacia (entre le comptoir x=22 et le mur x=26)
def offer(buy, n, sell, k, uses, buyB=None):
    o = {"buy": {"id": buy[0], "count": n}, "sell": {"id": sell[0], "count": k}, "maxUses": uses, "uses": 0, "xp": 0,
         "rewardExp": False, "priceMultiplier": 0.0, "demand": 0, "specialPrice": 0}
    if len(buy) > 1: o["buy"]["components"] = buy[1]
    if len(sell) > 1: o["sell"]["components"] = sell[1]
    if buyB: o["buyB"] = {"id": buyB[0], "count": buyB[1]}
    return o
E = ("emerald",)
FARINE = ("white_dye", {"custom_name": "Farine"})
POMME = ("golden_apple", {"custom_name": {"text": "Pomme d'or", "italic": False, "color": "gold"},
                          "lore": [{"text": "Mangez-la pour finir la salle.", "italic": False, "color": "gray"}],
                          "custom_data": {"craftmaster_goal": "pomme"}})
# (nom, type, métier, échanges) — chaque échange : (objet, quantité, émeraudes, fois)
VILLAGEOIS = [
    ("Monique la boulangère", "plains", "farmer", [
        (FARINE, 16, 1, 13), (("bread",), 6, 1, 4), (("cookie",), 3, 1, 3), (("pumpkin_pie",), 7, 1, 2),
        (("wheat",), 3, 1, 5), (("wheat_seeds",), 8, 1, 2),
        (("egg",), 16, 1, 4), (("cake",), 1, 5, 1)]),
    ("Gaston le boucher", "taiga", "butcher", [
        (("cooked_beef",), 1, 1, 5), (("beef",), 1, 1, 5), (("baked_potato",), 2, 1, 3), (("potato",), 2, 1, 3),
        (("milk_bucket",), 1, 2, 3), (("apple",), 1, 2, 4)]),
    ("Firmin le charpentier", "savanna", "fletcher", [
        (("acacia_planks",), 8, 1, 8), (("charcoal",), 2, 1, 6), (("stick",), 16, 1, 1), (("crafting_table",), 1, 1, 1),
        (("wooden_hoe",), 1, 1, 1), (("wooden_axe",), 1, 1, 1), (("wooden_pickaxe",), 1, 1, 1), (("wooden_shovel",), 1, 1, 1),
        (("wooden_sword",), 1, 1, 1)]),
    ("Léon le forgeron", "snow", "toolsmith", [
        (("raw_iron",), 4, 1, 9), (("raw_iron_block",), 1, 4, 4), (("iron_ingot",), 4, 1, 8), (("coal",), 2, 1, 3),
        (("iron_pickaxe",), 1, 2, 1), (("iron_axe",), 1, 2, 1), (("iron_hoe",), 1, 2, 1), (("iron_shovel",), 1, 2, 1),
        (("bucket",), 1, 2, 3), (("mace",), 1, 5, 1)]),
    ("Rose l'apicultrice", "jungle", "cleric", [
        (("honey_bottle",), 1, 2, 4), (("glass_bottle",), 1, 1, 4), (("potion",), 1, 1, 4), (("cocoa_beans",), 1, 1, 4)]),
    ("Edmond le collectionneur", "swamp", "librarian", [
        (("skeleton_skull",), 1, 3, 1), (("player_head",), 1, 3, 1), (("leather_helmet",), 1, 1, 1), (("leather_chestplate",), 1, 1, 1),
        (("leather_leggings",), 1, 1, 1), (("leather_boots",), 1, 1, 1), (("poppy",), 1, 1, 2), (("allium",), 1, 1, 2),
        (("blue_orchid",), 1, 1, 2), (("orange_tulip",), 1, 1, 2), (("red_tulip",), 1, 1, 2), (("pink_tulip",), 1, 1, 2),
        (("white_tulip",), 1, 1, 2), (("cornflower",), 1, 1, 2), (("wither_rose",), 1, 1, 2), (("cactus",), 1, 1, 2),
        (("dead_bush",), 1, 1, 2)]),
]
PRIX_POMME = (64, 16)   # 80 émeraudes
def villager_nbt(nom, typ, metier, offers, rot=90.0, tags=()):
    return {"CustomName": nom, "CustomNameVisible": False, "NoAI": True, "Invulnerable": True, "PersistenceRequired": True, "Silent": True,
            "VillagerData": {"type": "minecraft:" + typ, "profession": "minecraft:" + metier, "level": 5}, "Xp": 250,
            "Offers": {"Recipes": offers}, "Rotation": [rot, 0.0], "Tags": ["pomme_e"] + list(tags)}
def to_snbt(o):
    s = j(o)
    s = re.sub(r'"([A-Za-z_][\w]*)":', r'\1:', s)
    return s.replace(':true', ':1b').replace(':false', ':0b')

spots = [(24, z) for z in (14, 16, 18, 20, 22, 24, 26, 28) if free(24, z)]
spots += [(23, z) for z in range(14, 29) if free(23, z) and (23, z) not in spots]
print("places villageois libres :", spots, file=sys.stderr)
vlist = []
for k, (nom, typ, met, tr) in enumerate(VILLAGEOIS):
    offers = [offer(it, n, E, em, uses) for it, n, em, uses in tr]
    vlist.append((nom, typ, met, offers))
vlist.append(("Augustin le marchand de pommes", "desert", "cartographer",
              [offer(E, PRIX_POMME[0], POMME, 1, 1, buyB=("emerald", PRIX_POMME[1]))]))
for k, (nom, typ, met, offers) in enumerate(vlist):
    x, z = spots[k]
    ents.append("summon villager ~%.1f ~4 ~%.1f %s" % (x + 0.5, z + 0.5, to_snbt(villager_nbt(nom, typ, met, offers))))

# --- Mannequins (clients de la boulangerie) près des deux tables
def leather(item, color): return {"id": "minecraft:leather_" + item, "count": 1, "components": {"minecraft:dyed_color": color}}
BRUN, GRIS, ROUGE, JAUNE = 0x6B4A2B, 0x7D7D7D, 0xB02E26, 0xF9C80E
MANNEQUINS = [
    ("Patoche", "wide/steve", {"legs": leather("leggings", 0xF38BAA), "chest": leather("chestplate", BRUN)}),
    ("Lucie", "slim/alex", {"chest": leather("chestplate", 0x3CB043), "legs": leather("leggings", GRIS)}),
    ("Remy", "wide/kai", {"head": leather("helmet", 0x3B5BDB), "chest": leather("chestplate", GRIS)}),
    ("Zoé", "slim/noor", {"feet": leather("boots", 0x1D1D21), "legs": leather("leggings", BRUN)}),
    ("Mireille", "slim/sunny", {"chest": leather("chestplate", ROUGE), "feet": leather("boots", BRUN)}),
    ("Gégé", "wide/zuri", {"head": leather("helmet", JAUNE), "legs": leather("leggings", GRIS)}),
]
# (nom, x, y, z, yaw, pitch, assis) ; assis = sur un support invisible au ras du siège (marche basse de l'escalier)
PLACES = {
    "Remy":     (15.5, 4.5, 13.75, 0.0, 12.0, True),     # chaise nord de la table 1, penché vers la table, casquette bien visible
    "Lucie":    (19.5, 4.5, 16.25, 165.0, -4.0, True),   # chaise sud, tournée vers Remy, haut vert visible
    "Mireille": (16.5, 4.5, 26.75, 10.0, 18.0, True),    # chaise nord de la table 2, tête baissée vers la table
    "Gégé":     (18.5, 4.5, 29.25, 195.0, 0.0, True),    # chaise sud, en face de Mireille
    "Patoche":  (18.8, 4.0, 21.5, -95.0, 6.0, False),    # debout au milieu, pantalon rose bien visible, parle à Zoé
    "Zoé":      (20.3, 4.0, 21.4, 80.0, 10.0, False),    # debout en face de lui, chaussures noires bien visibles
}
for nom, skin, eq in MANNEQUINS:
    x, y, z, yaw, pitch, assis = PLACES[nom]
    model = "slim" if skin.startswith("slim") else "wide"
    m = {"id": "minecraft:mannequin", "CustomName": nom, "CustomNameVisible": True, "Invulnerable": True, "immovable": True,
         "hide_description": True, "profile": {"texture": "entity/player/" + skin, "model": model}, "equipment": eq,
         "Rotation": [yaw, pitch], "Tags": ["pomme_e"]}
    if assis:
        seat = {"Rotation": [yaw, 0.0], "Tags": ["pomme_e", "pomme_siege"], "Passengers": [m]}
        ents.append("summon item_display ~%.2f ~%.2f ~%.2f %s" % (x, y, z, to_snbt(seat)))
    else:
        del m["id"]
        ents.append("summon mannequin ~%.2f ~%.2f ~%.2f %s" % (x, y, z, to_snbt(m)))
fn("construire/entites", ents)
# Échanges et noms des villageois remis à jour sans refaire la salle (à lancer positionné au coin d'une salle)
maj = []
for nom, typ, met, offers in vlist:
    maj.append("execute as @e[type=villager,name=\"%s\",%s] run data merge entity @s {CustomNameVisible:0b,Offers:{Recipes:%s}}"
               % (nom, "dx=59,dy=17,dz=41", to_snbt(offers)))
maj.append("execute as @e[type=villager,tag=pomme_baptiste,dx=59,dy=17,dz=41] run data merge entity @s {CustomNameVisible:0b}")
fn("salle/echanges", maj)

# ------------------------------------------------------------ construction et copies
fn("construire/reference", [
    "# Salle de référence en 0 60 0 (à lancer : function pomme:construire/reference)",
    "execute in %s run forceload add -16 -16 %d 57" % (DIM, STRIDE * COPIES + 75),
    "execute in %s positioned %d.0 %d %d.0 run function pomme:construire/blocs" % ((DIM,) + REF),
    "execute in %s positioned %d.0 %d %d.0 run function pomme:construire/entites" % ((DIM,) + REF),
    "execute in %s run summon marker %.1f %d %.1f {Tags:[\"pomme_salle\",\"pomme_ref\"]}" % (DIM, REF[0] + .5, REF[1], REF[2] + .5),
])
copies = ["# Refait les %d salles des joueurs depuis la référence, sans aucune mise à jour de blocs" % COPIES]
for i in range(1, COPIES + 1):
    x0 = REF[0] + STRIDE * i
    copies.append("execute in %s positioned %d.0 %d %d.0 run function pomme:salle/refaire" % (DIM, x0, REF[1], REF[2]))
    # murs de barrières à 1 bloc de la salle, là où il y a de l'air
    xa, xb, za, zb, ya, yb = x0 - 1, x0 + W, REF[2] - 1, REF[2] + L, REF[1], REF[1] + H
    for a in [(xa, ya, za, xb, yb, za), (xa, ya, zb, xb, yb, zb), (xa, ya, za, xa, yb, zb), (xb, ya, za, xb, yb, zb), (xa, yb, za, xb, yb, zb)]:
        copies.append("execute in %s run fill %d %d %d %d %d %d barrier replace air strict" % ((DIM,) + a))
fn("construire/copies", copies)

# Refaire une salle (bouton rouge, ou l'organisateur) : à lancer positionné au coin de la salle
fn("salle/refaire", [
    "tag @e[type=!player,type=!marker,dx=%d,dy=%d,dz=%d] add pomme_mort" % (W - 1, H - 1, L - 1),
    "tp @e[tag=pomme_mort,type=!item_frame,type=!glow_item_frame,type=!painting] ~ -500 ~",
    "kill @e[tag=pomme_mort]",
    "clone %d %d %d %d %d %d ~ ~ ~ strict" % (REF[0], REF[1], REF[2], REF[0] + 29, REF[1] + H - 1, REF[2] + L - 1),
    "clone %d %d %d %d %d %d ~30 ~ ~ strict" % (REF[0] + 30, REF[1], REF[2], REF[0] + W - 1, REF[1] + H - 1, REF[2] + L - 1),
    "function pomme:construire/entites",
    "kill @e[type=marker,tag=pomme_salle,distance=..1]",
    "summon marker ~0.5 ~ ~0.5 {Tags:[\"pomme_salle\"]}",
])

# ------------------------------------------------------------ mécaniques (tick)
VOL = "dx=%d,dy=%d,dz=%d" % (W - 1, H - 1, L - 1)
fn("tick", [
    "execute in %s as @e[type=marker,tag=pomme_salle,x=-1,y=0,z=-1,dx=%d,dy=200,dz=100] at @s align xyz run function pomme:salle/tick" % (DIM, STRIDE * (COPIES + 4)),
    "execute in %s positioned 150 64 20 run function pomme:monde" % DIM,
])
fn("monde", [
    "# Aucune poule ne pond, aucun œuf n'éclot, les règles d'outils sur tous les objets, épée et lance interdites",
    "execute as @e[type=chicken,distance=..600] run data modify entity @s EggLayTime set value 999999",
    "execute as @e[type=chicken,distance=..600,predicate=pomme:bebe] run tag @s add pomme_mort",
    "tp @e[tag=pomme_mort,distance=..600] ~ -500 ~",
    "kill @e[tag=pomme_mort]",
] + ["item modify entity @a[distance=..600] container.%d pomme:regles" % k for k in range(36)] + [
    "item modify entity @a[distance=..600] weapon.offhand pomme:regles",
    "item modify entity @a[distance=..600] player.cursor pomme:regles",
    "clear @a[distance=..600] iron_sword",
    "clear @a[distance=..600] iron_spear",
    "# Petit pendant 10 s",
    "scoreboard players remove @a[distance=..600,scores={pomme_petit=1..}] pomme_petit 1",
    "execute as @a[distance=..600,scores={pomme_petit=1}] at @s run function pomme:petit/grandir",
])
fn("charger", [
    "scoreboard objectives add pomme_t dummy",
    "scoreboard objectives add pomme_petit dummy",
])
F_JSON["data/pomme/predicate/bebe.json"] = {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"flags": {"is_baby": True}}}
F_JSON["data/minecraft/tags/function/tick.json"] = {"values": ["pomme:tick"]}
F_JSON["data/minecraft/tags/function/load.json"] = {"values": ["pomme:charger"]}
F_JSON["data/pomme/loot_table/oeuf.json"] = {"pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:egg"}]}]}
F_JSON["data/pomme/tags/block/libre.json"] = {"values": ["#minecraft:air", "minecraft:light", "minecraft:vine", "minecraft:short_grass", "minecraft:tall_dry_grass",
                                                         "minecraft:leaf_litter", "minecraft:pink_petals", "#minecraft:wool_carpets", "#minecraft:buttons", "minecraft:rail"]}

# Feuilles de chêne : 100 % de pomme dans le monde de la pomme, comme le jeu ailleurs
vanilla_leaves = json.load(open(os.path.join(os.path.dirname(__file__), "oak_leaves.json"), encoding="utf-8"))
in_pomme = {"condition": "minecraft:location_check", "predicate": {"dimension": DIM}}
for p in vanilla_leaves["pools"]:
    p.setdefault("conditions", []).append({"condition": "minecraft:inverted", "term": in_pomme})
vanilla_leaves["pools"].insert(0, {"rolls": 1, "conditions": [in_pomme], "entries": [{"type": "minecraft:item", "name": "minecraft:apple"}]})
F_JSON["data/minecraft/loot_table/blocks/oak_leaves.json"] = vanilla_leaves

def edge(tag, cond, then):
    """Something happens once when cond becomes true (button pressed), again only after it went false."""
    return ["execute if entity @s[tag=!%s] %s run %s" % (tag, cond, then),
            "execute if entity @s[tag=!%s] %s run tag @s add %s" % (tag, cond, tag),
            "execute if entity @s[tag=%s] %s run tag @s remove %s" % (tag, cond.replace("if block", "unless block", 1), tag)]
PLAYERS = "@a[%s]" % VOL
tick = [
    "# 1. L'œuf sur la cible : une pioche en pierre (3 utilisations, casse le minerai de fer), une seule fois",
    "execute if entity @s[tag=!p_pioche] unless block ~40 ~10 ~35 target[power=0] run function pomme:salle/pioche",
    "# 2. Poules sur les gorgones : la blanche sous le panneau blanc, la noire sous le panneau noir",
    "execute if entity @s[tag=!p_blanc] positioned ~41.5 ~5 ~13.5 if entity @e[type=chicken,distance=..0.8,nbt={variant:\"minecraft:temperate\"}] run function pomme:salle/ancrer_blanc",
    "execute if entity @s[tag=!p_noir] positioned ~41.5 ~5 ~14.5 if entity @e[type=chicken,distance=..0.8,nbt={variant:\"minecraft:cold\"}] run function pomme:salle/ancrer_noir",
    "execute if entity @s[tag=p_blanc,tag=p_noir,tag=!p_bulbs] run function pomme:salle/ampoules",
    "execute if entity @s[tag=p_bulbs] run scoreboard players add @s pomme_t 1",
    "execute if score @s pomme_t matches 200.. run function pomme:salle/oeufs",
    "# 3. Les 4 pots devant les diamants (de gauche à droite) : tulipe rose, cactus, bleuet, rose de wither",
    "execute if entity @s[tag=!p_baptiste] if block ~25 ~5 ~8 potted_pink_tulip if block ~26 ~5 ~8 potted_cactus if block ~27 ~5 ~8 potted_cornflower if block ~28 ~5 ~8 potted_wither_rose run function pomme:salle/baptiste",
    "# 4. Le code de la porte sur l'étagère : crâne de squelette, gâteau, coquelicot",
    "execute if entity @s[tag=!p_porte] if items block ~28 ~6 ~17 container.* skeleton_skull if items block ~28 ~6 ~17 container.* cake if items block ~28 ~6 ~17 container.* poppy run function pomme:salle/porte",
    "# 5. Le code « riricococo » (panneau « ? : ») puis le bouton bleu : 3 poules de chaque couleur",
    "execute if entity @s[tag=!p_code] if entity @a[%s,tag=cm_code_poules] run function pomme:salle/code_bon" % VOL,
    "tag @a[%s,tag=cm_code_poules] remove cm_code_poules" % VOL,
]
tick += edge("p_bleu", "if block ~49 ~4 ~32 #minecraft:buttons[powered=true]", "function pomme:salle/bouton_bleu")
tick.append("# 6. Les quatre boutons des poules : un son chacun")
for k, z in enumerate((28, 29, 30, 31), 1):
    tick += edge("p_son%d" % k, "if block ~49 ~4 ~%d #minecraft:buttons[powered=true]" % z, "function pomme:son/%d" % k)
tick.append("# 7. Bouton rouge : la salle et l'inventaire du joueur reviennent au début (pas son temps)")
tick += edge("p_rouge", "if block ~28 ~4 ~17 #minecraft:buttons[powered=true]", "function pomme:salle/bouton_rouge")
tick.append("# 8. Bouton au plafond : le joueur devient petit 10 s")
tick += edge("p_petit", "if block ~38 ~11 ~31 #minecraft:buttons[powered=true]", "execute positioned ~38.5 ~11 ~31.5 as @p[distance=..12] run function pomme:petit/retrecir")
fn("salle/tick", tick)

fn("salle/pioche", [
    "tag @s add p_pioche",
    "execute positioned ~40.5 ~10 ~35.5 run give @p[%s] stone_pickaxe[max_damage=3,can_break={blocks:\"iron_ore\"},custom_name={text:\"Pioche fragile\",italic:false}]" % VOL.replace("dx=", "dx=").replace("dy", "dy"),
    "execute positioned ~40.5 ~10 ~35.5 as @p[%s] at @s run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1 1.2" % VOL,
])
# @p with dx relative to the room corner: positioned at the corner for the volume
F["salle/pioche"][1] = "give @p[%s] stone_pickaxe[max_damage=3,can_break={blocks:\"iron_ore\"},custom_name={text:\"Pioche fragile\",italic:false}]" % VOL
F["salle/pioche"][2] = "execute as %s at @s run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1 1.2" % PLAYERS
for c, var, z in (("blanc", "temperate", 13), ("noir", "cold", 14)):
    fn("salle/ancrer_" + c, [
        "tag @s add p_" + c,
        "execute positioned ~41.5 ~5 ~%d.5 as @e[type=chicken,distance=..0.8,nbt={variant:\"minecraft:%s\"},limit=1,sort=nearest] run function pomme:poule/ancrer" % (z, var),
        "playsound minecraft:block.amethyst_block.chime master %s ~41.5 ~5 ~%d.5 1 1" % (PLAYERS, z),
    ])
fn("poule/ancrer", ["tp @s ~ ~ ~", "data merge entity @s {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Silent:1b}", "tag @s add pomme_ancree",
                    "particle minecraft:happy_villager ~ ~0.5 ~ 0.3 0.3 0.3 0 10"])
fn("salle/ampoules", [
    "tag @s add p_bulbs",
    "setblock ~41 ~6 ~13 air strict", "setblock ~41 ~6 ~14 air strict",
    "setblock ~42 ~7 ~13 waxed_copper_bulb[lit=true] strict", "setblock ~42 ~7 ~14 waxed_copper_bulb[lit=true] strict",
    "playsound minecraft:block.copper_bulb.turn_on master %s ~42 ~7 ~13.5 1 1" % PLAYERS,
    "scoreboard players set @s pomme_t 199",
])
fn("salle/oeufs", ["scoreboard players set @s pomme_t 0", "loot insert ~41 ~4 ~13 loot pomme:oeuf", "loot insert ~41 ~4 ~14 loot pomme:oeuf"])

flowers = [("white_tulip", "poppy"), ("dandelion", "poppy"), ("allium", "red_tulip"), ("blue_orchid", "cornflower"),
           ("orange_tulip", "pink_tulip"), ("red_tulip", "white_tulip"), ("oxeye_daisy", "azure_bluet"), ("lily_of_the_valley", "dandelion")]
bap = villager_nbt("Baptiste le fleuriste", "plains", "farmer", [offer((a,), 1, (b,), 1, 16) for a, b in flowers], rot=0.0, tags=("pomme_baptiste",))
fn("salle/baptiste", [
    "tag @s add p_baptiste",
    "summon villager ~27 ~4 ~7 " + to_snbt(bap),
    "particle minecraft:happy_villager ~27 ~5 ~7 0.6 0.8 0.6 0 30",
    "playsound minecraft:entity.villager.celebrate master %s ~27 ~4 ~7 1 1" % PLAYERS,
    "playsound minecraft:ui.toast.challenge_complete master %s ~27 ~4 ~7 1 1" % PLAYERS,
])
door = ["setblock ~%d ~%d ~%d air strict" % (x, y, z) for y in range(H) for z in range(18, 22) for x in (26,) if name(x, y, z) in ('spruce_shelf', 'brown_stained_glass')]
fn("salle/porte", ["tag @s add p_porte"] + door + [
    "playsound minecraft:block.wooden_door.open master %s ~26 ~5 ~19.5 1 0.8" % PLAYERS,
    "playsound minecraft:ui.toast.challenge_complete master %s ~26 ~5 ~19.5 1 1" % PLAYERS])
fn("salle/code_bon", [
    "tag @s add p_code",
    "data modify block ~49 ~6 ~32 front_text.messages[1] set value {text:\"riricococo\",color:\"dark_green\"}",
    "playsound minecraft:block.note_block.chime master %s ~49 ~6 ~32 1 1.4" % PLAYERS,
])
summ = []
for var in ("temperate", "cold", "warm"):
    for _ in range(3):
        summ.append("execute at @p[%s] run summon chicken ~ ~ ~ {variant:\"minecraft:%s\",PersistenceRequired:1b,EggLayTime:999999,Tags:[\"pomme_e\"]}" % (VOL, var))
fn("salle/bouton_bleu", [
    "execute if entity @s[tag=p_code,tag=!p_poules] run function pomme:salle/poules",
    "execute if entity @s[tag=!p_code] run playsound minecraft:block.note_block.bass master %s ~49 ~4 ~32 1 0.6" % PLAYERS,
])
fn("salle/poules", ["tag @s add p_poules"] + summ + [
    "execute as %s at @s run playsound minecraft:entity.player.levelup master @s ~ ~ ~ 1 1.2" % PLAYERS])
SONS = {1: ("cocori", 1.6), 2: ("cot", 1.0), 3: ("cotcot", 1.2), 4: ("cocotcot", 0.8)}
for k, (mot, pitch) in SONS.items():
    fn("son/%d" % k, ["# « %s » : l'enregistrement de LordFinn (sons du mod CraftMaster, assets/craftmaster/sounds/pomme)" % mot,
                      "playsound craftmaster:pomme.%s master %s ~49 ~4 ~%d 1 1" % (mot, PLAYERS, 27 + k)])
fn("salle/bouton_rouge", [
    "execute as %s run function pomme:petit/normal" % PLAYERS,
    "clear %s" % PLAYERS,
    "tag %s remove cm_code_poules" % PLAYERS,
    "tp %s ~31.5 ~4 ~15.5" % PLAYERS,
    "tag @s remove p_pioche", "tag @s remove p_blanc", "tag @s remove p_noir", "tag @s remove p_bulbs", "tag @s remove p_baptiste",
    "tag @s remove p_porte", "tag @s remove p_code", "tag @s remove p_poules", "scoreboard players reset @s pomme_t",
    "function pomme:salle/refaire",
    "tellraw %s {text:\"La salle revient au début (ton temps continue).\",color:\"yellow\"}" % PLAYERS,
    "execute as %s at @s run playsound minecraft:block.beacon.deactivate master @s ~ ~ ~ 1 1" % PLAYERS,
])
fn("petit/retrecir", ["attribute @s minecraft:scale base set 0.25", "scoreboard players set @s pomme_petit 200",
                      "playsound minecraft:entity.illusioner.mirror_move master @s ~ ~ ~ 1 1.5"])
fn("petit/grandir", [
    "execute if block ~ ~0.2 ~ #pomme:libre if block ~ ~1 ~ #pomme:libre if block ~ ~1.8 ~ #pomme:libre run function pomme:petit/normal",
    "# pas la place de grandir : on reste petit et on réessaie dans une seconde",
    "execute unless score @s pomme_petit matches 0 run scoreboard players set @s pomme_petit 20",
])
fn("petit/normal", ["attribute @s minecraft:scale base set 1", "scoreboard players set @s pomme_petit 0"])
# Le panneau « ? : » : la fenêtre du mod CraftMaster (réponses et variantes : /cm arene code pomme poules ;
# espaces, majuscules, accents et ponctuation ne comptent pas). Une bonne réponse met le tag cm_code_poules.
fn("code/ouvrir", ["cm arene demander poules"])
# Tout remettre à zéro (organisateur) : la référence ne bouge pas, les 4 salles sont refaites
fn("tout_refaire", ["execute in %s run function pomme:construire/copies" % DIM])

# ------------------------------------------------------------ cachettes ajoutées dans des salles déjà construites
# (à lancer positionné au coin d'une salle : function pomme:salle/cachettes ; le tag p_cachettes évite de doubler)
cach = ["execute if entity @e[type=marker,tag=pomme_salle,tag=p_cachettes,distance=..1] run return 0"]
for (x, y, z), its in CACHETTES.items():
    for iid, n in its:
        F_JSON["data/pomme/loot_table/cachette/%s_%d.json" % (iid, n)] = {"pools": [{"rolls": 1, "entries": [
            {"type": "minecraft:item", "name": "minecraft:" + iid, "functions": [{"function": "minecraft:set_count", "count": n}]}]}]}
        cach.append("loot insert ~%d ~%d ~%d loot pomme:cachette/%s_%d" % (x, y, z, iid, n))
cach.append("tag @e[type=marker,tag=pomme_salle,distance=..1,limit=1] add p_cachettes")
fn("salle/cachettes", cach)

# ------------------------------------------------------------ salles déjà construites : sans canne, avec les sables (20/11)
# (positionné au coin d'une salle, référence comprise : function pomme:salle/sans_canne ; sans effet la 2e fois)
fn("salle/sans_canne", [
    "data remove block ~28 ~4 ~27 Items[{id:\"minecraft:sugar_cane\"}]",
    "data remove block ~29 ~9 ~13 Items[{id:\"minecraft:sand\"}]",
    "data remove block ~33 ~9 ~13 Items[{id:\"minecraft:sand\"}]",
    "data remove block ~39 ~9 ~13 Items[{id:\"minecraft:sand\"}]",
] + ["setblock ~%d ~%d ~%d %s strict" % (x, y, z, b) for (x, y, z), b in OVERRIDE.items() if b == "minecraft:sand"])

# ------------------------------------------------------------ vérification bloc à bloc (0 attendu)
ver = ["scoreboard players set #diff pomme_t 0"]
for y in range(H):
    for z in range(L):
        for x in range(W):
            ver.append("execute unless block ~%d ~%d ~%d %s run scoreboard players add #diff pomme_t 1" % (x, y, z, blk(x, y, z)))
fn("verif", ver)

# ------------------------------------------------------------ écriture
F_JSON["pack.mcmeta"] = {"pack": {"description": "CraftMaster — La pomme (escape room, vanilla)", "min_format": [107, 0], "max_format": [107, 1]}}
for path, lines in F.items():
    p = os.path.join(OUT, "data/pomme/function", path + ".mcfunction")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, "w", encoding="utf-8", newline="\n").write("\n".join(lines) + "\n")
for path, o in F_JSON.items():
    p = os.path.join(OUT, path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(o, ensure_ascii=False, indent=1))
print("fonctions:", {k: len(v) for k, v in F.items() if len(v) > 50}, file=sys.stderr)
