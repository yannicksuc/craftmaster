package fr.lordfinn.craftmaster.regie;

import fr.lordfinn.craftmaster.trial.kit.Arena;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a trial's room holds, for the régie agent to work out how it is solved (« lire zone », « lire mecaniques »):
 * the signs (both sides), what lies in every container (chests, barrels, shulkers, shelves, pots, campfires,
 * lecterns, item frames, armour stands, items on the ground) with the items' names, lore and adventure rules
 * (can_break, can_place_on), the flower pots, the buttons, levers, targets, pressure plates, doors and trapdoors,
 * the villagers and their trades, the other creatures; in coordinates relative to the room's corner (the datapack's
 * {@code ~x ~y ~z}), grouped by sector of the floor. The room's mechanics are its datapack's functions.
 */
public final class ZoneReader {

    private ZoneReader() {}

    private static final int SECTOR = 12;
    private static final int MAX_CHARS = 60_000;

    /** The room of {@code arena} (its reference), read block by block. */
    public static String read(MinecraftServer server, Arena a) {
        ServerLevel level = null;
        for (ServerLevel l : server.getAllLevels()) if (l.dimension().identifier().toString().equals(a.world)) level = l;
        if (level == null) return "Monde « " + a.world + " » introuvable.";
        int x0 = Math.min(a.x0, a.x1), y0 = Math.min(a.y0, a.y1), z0 = Math.min(a.z0, a.z1);
        int x1 = Math.max(a.x0, a.x1), y1 = Math.max(a.y0, a.y1), z1 = Math.max(a.z0, a.z1);
        Map<String, List<String>> sectors = new TreeMap<>();
        // Every kind of block: how many, and the box they fill (feuilles de chêne, bûches, minerais : ce qu'on récolte)
        Map<String, int[]> kinds = new TreeMap<>();
        int unloaded = 0;
        for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            if (!level.hasChunkAt(p)) {
                unloaded++;
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
            int rx0 = p.getX() - x0, ry0 = p.getY() - y0, rz0 = p.getZ() - z0;
            int[] k = kinds.computeIfAbsent(id, i -> new int[]{0, rx0, ry0, rz0, rx0, ry0, rz0});
            k[0]++;
            k[1] = Math.min(k[1], rx0); k[2] = Math.min(k[2], ry0); k[3] = Math.min(k[3], rz0);
            k[4] = Math.max(k[4], rx0); k[5] = Math.max(k[5], ry0); k[6] = Math.max(k[6], rz0);
            String line = null;
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof SignBlockEntity sign) {
                String front = signText(sign.getFrontText()), back = signText(sign.getBackText());
                if (!front.isBlank() || !back.isBlank())
                    line = "panneau " + id + " : « " + front + " »" + (back.isBlank() ? "" : " / dos « " + back + " »")
                            + (hasClick(sign.getFrontText()) ? " (cliquable)" : "");
            } else if (be instanceof DecoratedPotBlockEntity pot) {
                line = "pot décoré : " + (pot.getTheItem().isEmpty() ? "vide" : item(pot.getTheItem()));
            } else if (be instanceof CampfireBlockEntity fire) {
                List<String> in = new ArrayList<>();
                for (ItemStack st : fire.getItems()) if (!st.isEmpty()) in.add(item(st));
                boolean lit = s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                        && s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
                line = id + (lit ? " (allumé)" : " (éteint)") + " : " + (in.isEmpty() ? "vide" : String.join(", ", in));
            } else if (be instanceof LecternBlockEntity lectern) {
                line = "pupitre : " + (lectern.getBook().isEmpty() ? "vide" : item(lectern.getBook()));
            } else if (be instanceof Container c) {
                List<String> in = new ArrayList<>();
                for (int i = 0; i < c.getContainerSize(); i++) if (!c.getItem(i).isEmpty()) in.add(item(c.getItem(i)));
                line = id + (be.components().has(DataComponents.CUSTOM_NAME) ? " « " + be.components().get(DataComponents.CUSTOM_NAME).getString() + " »" : "")
                        + " : " + (in.isEmpty() ? "vide" : String.join(", ", in)) + access(level, p.immutable(), s);
            } else if (id.startsWith("potted_")) {
                line = "pot de fleur : " + id.substring(7);
            } else if (id.equals("flower_pot")) {
                line = "pot de fleur vide";
            } else if (s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_HONEY)) {
                int honey = s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_HONEY);
                // Only the full ones fill a bottle: say which (the others are decor)
                if (honey >= 5) line = id + " PLEINE (miel 5/5 : une fiole vide s'y remplit de miel, une seule fois)";
            } else if (s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_CAULDRON)) {
                int lvl = s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_CAULDRON);
                line = id + " (eau " + lvl + "/3 ; un seau ne s'y remplit qu'à 3/3, une fiole d'eau versée ajoute 1, une fiole vide en prend 1)";
            } else if (s.is(BlockTags.BUTTONS)) {
                line = "bouton (" + id + ")";
            } else if (id.equals("lever")) {
                line = "levier";
            } else if (id.equals("target")) {
                line = "cible";
            } else if (s.is(BlockTags.PRESSURE_PLATES)) {
                line = "plaque de pression (" + id + ")";
            } else if (s.is(BlockTags.DOORS) || s.is(BlockTags.TRAPDOORS) || s.is(BlockTags.FENCE_GATES)) {
                if (!s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                        || s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                        == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
                    line = "porte / trappe " + id + (s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)
                            && s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN) ? " (ouverte)" : " (fermée)");
            } else if (Set_INTERESTING.contains(id)) {
                line = id;
            }
            if (line != null) {
                // Far above the floor under it: out of a player's reach unless something lets them climb (4,5 blocs des yeux)
                int up = 0;
                BlockPos.MutableBlockPos q = p.mutable().move(net.minecraft.core.Direction.DOWN);
                while (up < 16 && q.getY() >= y0 && level.getBlockState(q).getCollisionShape(level, q).isEmpty()) { up++; q.move(net.minecraft.core.Direction.DOWN); }
                if (up >= 3) line += " [" + up + " blocs de vide sous lui jusqu'au sol]";
                add(sectors, p.getX() - x0, p.getZ() - z0, rel(p.getX() - x0, p.getY() - y0, p.getZ() - z0) + " " + line);
            }
        }
        // Entities: item frames, armour stands, items, villagers and their trades, the others counted
        Map<String, Integer> others = new TreeMap<>();
        for (Entity e : level.getEntities((Entity) null, new AABB(x0, y0, z0, x1 + 1, y1 + 1, z1 + 1), e -> !(e instanceof Player))) {
            int rx = (int) Math.floor(e.getX()) - x0, ry = (int) Math.floor(e.getY()) - y0, rz = (int) Math.floor(e.getZ()) - z0;
            String name = e.hasCustomName() ? " « " + e.getCustomName().getString() + " »" : "";
            String line = null;
            if (e instanceof ItemFrame f) line = "cadre" + name + " : " + (f.getItem().isEmpty() ? "vide" : item(f.getItem()));
            else if (e instanceof ItemEntity it) line = "objet par terre : " + item(it.getItem());
            else if (e instanceof ArmorStand st) {
                List<String> worn = new ArrayList<>();
                for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values())
                    if (!st.getItemBySlot(slot).isEmpty()) worn.add(item(st.getItemBySlot(slot)));
                if (!worn.isEmpty()) line = "support d'armure" + name + " : " + String.join(", ", worn);
            } else if (e instanceof Merchant v) {
                List<String> trades = new ArrayList<>();
                for (MerchantOffer o : v.getOffers())
                    trades.add(item(o.getCostA()) + (o.getCostB().isEmpty() ? "" : " + " + item(o.getCostB())) + " → " + item(o.getResult())
                            + " (" + (o.getMaxUses() - o.getUses()) + " fois)");
                line = "villageois" + name + " : " + (trades.isEmpty() ? "aucun échange" : String.join(" ; ", trades));
            }
            if (line != null) add(sectors, rx, rz, rel(rx, ry, rz) + " " + line);
            else {
                String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
                if (type.equals("marker") || type.contains("display") || type.equals("interaction")) continue;
                others.merge(type + name, 1, Integer::sum);
            }
        }
        StringBuilder b = new StringBuilder();
        b.append("Salle « ").append(a.name).append(" » (manche ").append(a.manche).append(", monde ").append(a.world).append(") : ")
                .append(x1 - x0 + 1).append(" × ").append(y1 - y0 + 1).append(" × ").append(z1 - z0 + 1)
                .append(" blocs. Coordonnées relatives au coin (").append(x0).append(" ").append(y0).append(" ").append(z0)
                .append(") = les ~x ~y ~z du pack de la salle ; y = hauteur depuis le bas de la salle.\n");
        if (a.hasSpawn) b.append("Arrivée des joueurs : ").append(rel((int) Math.floor(a.sx) - x0, (int) Math.floor(a.sy) - y0, (int) Math.floor(a.sz) - z0)).append('\n');
        var m = fr.lordfinn.craftmaster.camera.Lieux.manche(a.manche);
        if (m != null && m.task != null && !m.task.isEmpty())
            b.append("Tâche « ").append(m.task.title).append(" » : ").append(m.task.text.replace('\n', ' ')).append('\n');
        if (a.kit != null && !a.kit.isEmpty()) b.append("Kit donné à chaque candidat : ").append(a.kit).append('\n');
        if (unloaded > 0) b.append("(").append(unloaded).append(" blocs dans des chunks non chargés, non lus)\n");
        for (var e : sectors.entrySet()) {
            b.append("\n## ").append(e.getKey()).append('\n');
            for (String l : e.getValue()) b.append("- ").append(l).append('\n');
        }
        b.append("\n## Les blocs de la salle (nombre, et la boîte qu'ils occupent)\n");
        List<String> kindLines = new ArrayList<>();
        kinds.forEach((id, k) -> kindLines.add(id + " " + k[0] + (k[0] == 1 ? " " + rel(k[1], k[2], k[3]) : " " + rel(k[1], k[2], k[3]) + "→" + rel(k[4], k[5], k[6]))));
        b.append(String.join(" ; ", kindLines)).append('\n');
        if (!others.isEmpty()) {
            b.append("\n## Autres créatures\n");
            others.forEach((k, v) -> b.append("- ").append(v).append(" × ").append(k).append('\n'));
        }
        String out = b.toString();
        return out.length() > MAX_CHARS ? out.substring(0, MAX_CHARS) + "\n… (coupé)" : out;
    }

    /** Blocks worth naming by themselves (workstations, things a puzzle uses). */
    private static final java.util.Set<String> Set_INTERESTING = java.util.Set.of("crafting_table", "furnace", "smoker", "blast_furnace",
            "cauldron", "water_cauldron", "beehive", "bee_nest", "note_block", "jukebox", "anvil", "smithing_table", "stonecutter",
            "loom", "grindstone", "enchanting_table", "brewing_stand", "composter", "copper_bulb", "waxed_copper_bulb", "redstone_lamp",
            "iron_ore", "coal_ore", "raw_iron_block", "farmland", "sugar_cane", "wheat", "cake", "dispenser", "dropper", "observer",
            "trial_spawner", "vault", "spawner", "bell", "lodestone", "respawn_anchor", "end_portal_frame", "command_block");

    private static void add(Map<String, List<String>> sectors, int rx, int rz, String line) {
        int sx = Math.floorDiv(rx, SECTOR) * SECTOR, sz = Math.floorDiv(rz, SECTOR) * SECTOR;
        sectors.computeIfAbsent(String.format(Locale.ROOT, "secteur x %02d-%02d, z %02d-%02d", sx, sx + SECTOR - 1, sz, sz + SECTOR - 1),
                k -> new ArrayList<>()).add(line);
    }

    private static String rel(int x, int y, int z) {
        return "(" + x + " " + y + " " + z + ")";
    }

    private static String signText(net.minecraft.world.level.block.entity.SignText t) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String l = t.getMessage(i, false).getString().strip();
            if (!l.isEmpty()) lines.add(l);
        }
        return String.join(" | ", lines);
    }

    private static boolean hasClick(net.minecraft.world.level.block.entity.SignText t) {
        for (int i = 0; i < 4; i++) if (t.getMessage(i, false).getStyle().getClickEvent() != null) return true;
        return false;
    }

    /** "3 × blé « Farine » [lore …] {casse : …}" */
    /**
     * " [ouvert par : dessus libre, nord à travers un coffre…]": the faces of a container a player can aim at — open
     * (nothing there), through a block that is not full (a chest leaves a 1/16 gap on its sides and top, a slab,
     * stairs, a pot, a sign… let the aim pass beside them), or none ("enfermé"). A shulker box also needs room on its
     * lid's side to open; a chest, no full block on top.
     */
    static String access(net.minecraft.world.level.Level level, BlockPos p, BlockState s) {
        List<String> open = new ArrayList<>(), through = new ArrayList<>();
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            BlockPos q = p.relative(d);
            BlockState n = level.getBlockState(q);
            net.minecraft.world.phys.shapes.VoxelShape shape = n.getShape(level, q);
            String face = switch (d) {
                case UP -> "dessus"; case DOWN -> "dessous"; case NORTH -> "nord"; case SOUTH -> "sud"; case WEST -> "ouest"; case EAST -> "est";
            };
            if (shape.isEmpty()) open.add(face + (n.getFluidState().isEmpty() ? "" : " (eau)"));
            else if (!net.minecraft.world.level.block.Block.isShapeFullBlock(shape))
                through.add(face + " à travers " + BuiltInRegistries.BLOCK.getKey(n.getBlock()).getPath() + " (bloc non plein : on vise par le jour à côté)");
        }
        String lid = "";
        if (s.hasProperty(net.minecraft.world.level.block.ShulkerBoxBlock.FACING)) {
            net.minecraft.core.Direction f = s.getValue(net.minecraft.world.level.block.ShulkerBoxBlock.FACING);
            lid = level.getBlockState(p.relative(f)).getCollisionShape(level, p.relative(f)).isEmpty() ? ", couvercle libre (" + f.getName() + ")"
                    : ", couvercle bloqué (" + f.getName() + ") : ne s'ouvre pas";
        }
        if (open.isEmpty() && through.isEmpty()) return " [enfermé : aucune face à viser" + lid + "]";
        List<String> all = new ArrayList<>(open);
        all.addAll(through);
        return " [faces à viser : " + String.join(", ", all) + lid + "]";
    }

    static String item(ItemStack st) {
        StringBuilder b = new StringBuilder();
        if (st.getCount() > 1) b.append(st.getCount()).append(" × ");
        b.append(BuiltInRegistries.ITEM.getKey(st.getItem()).getPath());
        Component custom = st.get(DataComponents.CUSTOM_NAME);
        if (custom != null) b.append(" « ").append(custom.getString()).append(" »");
        var lore = st.get(DataComponents.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            List<String> ls = new ArrayList<>();
            for (Component c : lore.lines()) ls.add(c.getString());
            b.append(" [").append(String.join(" / ", ls)).append("]");
        }
        var breakRule = st.get(DataComponents.CAN_BREAK);
        if (breakRule != null) b.append(" {peut casser : ").append(rule(breakRule.toString())).append("}");
        var placeRule = st.get(DataComponents.CAN_PLACE_ON);
        if (placeRule != null) b.append(" {se pose sur : ").append(rule(placeRule.toString())).append("}");
        if (st.has(DataComponents.CUSTOM_DATA)) b.append(" {données : ").append(st.get(DataComponents.CUSTOM_DATA)).append("}");
        return b.toString();
    }

    /** A block predicate's text, without its Java noise. */
    private static String rule(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("minecraft:([a-z0-9_]+)").matcher(s);
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        while (m.find()) ids.add(m.group(1));
        return ids.isEmpty() ? s.substring(0, Math.min(120, s.length())) : String.join(", ", ids);
    }

    // ---------------------------------------------------------------- the room's datapack

    /**
     * The functions of the datapacks whose namespace is {@code ns} (« pomme »): the room's mechanics, written in
     * Minecraft commands with their comments; the generated ones (building, checking) are left out.
     */
    public static String mechanics(MinecraftServer server, String ns) {
        var rm = server.getResourceManager();
        java.util.Set<String> namespaces = new java.util.TreeSet<>();
        Map<Identifier, net.minecraft.server.packs.resources.Resource> all = rm.listResources("function", id -> id.getPath().endsWith(".mcfunction"));
        for (Identifier id : all.keySet()) namespaces.add(id.getNamespace());
        namespaces.remove("minecraft");
        if (ns == null || ns.isBlank() || !namespaces.contains(ns))
            return "Mécaniques : donne le nom d'un pack parmi " + namespaces + " (une salle « pomme » = pack « pomme »).";
        StringBuilder b = new StringBuilder("Pack « " + ns + " » : ses données (règles des objets, butins, progrès), puis ses fonctions "
                + "(commandes Minecraft ; ~x ~y ~z = depuis le coin de la salle).\n");
        data(rm, ns, b);
        Map<Identifier, net.minecraft.server.packs.resources.Resource> mine = new TreeMap<>();
        all.forEach((k, v) -> {
            if (k.getNamespace().equals(ns)) mine.put(k, v);
        });
        for (var e : mine.entrySet()) {
            String path = e.getKey().getPath().substring("function/".length()).replace(".mcfunction", "");
            if (path.startsWith("construire/") || path.equals("verif") || path.equals("salle/echanges")) {
                b.append("\n### ").append(ns).append(':').append(path).append(" (généré : construction / vérification, non montré)\n");
                continue;
            }
            b.append("\n### ").append(ns).append(':').append(path).append('\n');
            try (BufferedReader r = e.getValue().openAsReader()) {
                int n = 0;
                for (String line : (Iterable<String>) r.lines()::iterator) {
                    if (++n > 120) {
                        b.append("…\n");
                        break;
                    }
                    b.append(line).append('\n');
                }
            } catch (Exception ex) {
                b.append("(illisible : ").append(ex.getMessage()).append(")\n");
            }
            if (b.length() > MAX_CHARS) {
                b.append("\n… (coupé)");
                break;
            }
        }
        return b.toString();
    }

    /** The pack's data besides its functions: rules of the items, loot (oak leaves → apples), advancements. */
    private static void data(net.minecraft.server.packs.resources.ResourceManager rm, String ns, StringBuilder b) {
        for (String dir : List.of("item_modifier", "loot_table", "advancement", "predicate")) {
            Map<Identifier, net.minecraft.server.packs.resources.Resource> data = new TreeMap<>(rm.listResources(dir, id -> id.getPath().endsWith(".json")));
            for (var e : data.entrySet()) {
                boolean ours = e.getKey().getNamespace().equals(ns) || e.getValue().sourcePackId().contains(ns);
                if (!ours || b.length() > MAX_CHARS) continue;
                b.append("\n### ").append(e.getKey()).append(" (").append(e.getValue().sourcePackId()).append(")\n");
                try (BufferedReader r = e.getValue().openAsReader()) {
                    String json = r.lines().map(String::strip).collect(java.util.stream.Collectors.joining(""));
                    b.append(json, 0, Math.min(json.length(), 16000)).append(json.length() > 16000 ? "…" : "").append('\n');
                } catch (Exception ex) {
                    b.append("(illisible)\n");
                }
            }
        }
    }
}
