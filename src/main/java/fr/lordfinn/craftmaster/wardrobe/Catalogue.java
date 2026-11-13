package fr.lordfinn.craftmaster.wardrobe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import fr.lordfinn.craftmaster.CraftMaster;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The ready outfits: what a new show wardrobe holds, and what "Fill with the ready outfits" adds. Organisers add
 * outfits from the screen ("Add to the ready outfits"); they are kept in {@code config/craftmaster/tenues.json},
 * which can also be edited by hand. Until then, the built-in outfits: Selfexpression's clothes when that mod is
 * there (a doctor, a police officer, a knight...), costumes made of vanilla armour otherwise.
 */
public final class Catalogue {

    private Catalogue() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Codec<List<Outfit>> LIST = Outfit.CODEC.listOf();

    private static HolderLookup.Provider registries;
    private static List<Outfit> saved;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("craftmaster/tenues.json");
    }

    /** Server start: reads tenues.json (if any). */
    public static void load(HolderLookup.Provider regs) {
        registries = regs;
        saved = null;
        all = null;
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            JsonElement json = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
            JsonElement list = json.isJsonObject() ? json.getAsJsonObject().get("tenues") : json;
            saved = new ArrayList<>(LIST.parse(RegistryOps.create(JsonOps.INSTANCE, regs), list)
                    .resultOrPartial(e -> CraftMaster.LOG.warn("tenues.json: {}", e)).orElse(List.of()));
        } catch (Exception e) {
            CraftMaster.LOG.warn("tenues.json could not be read, built-in outfits used", e);
        }
    }

    /** Copies of the ready outfits. */
    public static List<Outfit> outfits() {
        List<Outfit> src = saved != null ? saved : builtIn();
        List<Outfit> out = new ArrayList<>();
        for (Outfit o : src) out.add(Outfit.of(o.name(), o.pieces()));
        return out;
    }

    private static List<Outfit> gameSets, all;

    /**
     * Every set of the game's items: helmet / chestplate / leggings / boots sharing a name (two pieces at least),
     * unnamed (called after their body piece): Selfexpression's 322 outfits, other mods' armours, vanilla armour last.
     */
    public static List<Outfit> gameSets() {
        if (gameSets != null) return gameSets;
        String[] suffixes = {"_helmet", "_chestplate", "_leggings", "_boots"};
        java.util.Map<String, Item[]> sets = new java.util.TreeMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            for (int i = 0; i < 4; i++) {
                if (id.getPath().endsWith(suffixes[i])) {
                    String key = id.getNamespace() + ":" + id.getPath().substring(0, id.getPath().length() - suffixes[i].length());
                    sets.computeIfAbsent(key, k -> new Item[4])[i] = item;
                }
            }
        }
        List<Outfit> modded = new ArrayList<>(), vanilla = new ArrayList<>();
        sets.forEach((key, items) -> {
            List<ItemStack> pieces = new ArrayList<>();
            int n = 0;
            for (Item it : items) {
                pieces.add(it == null ? ItemStack.EMPTY : new ItemStack(it));
                if (it != null) n++;
            }
            if (n >= 2) (key.startsWith("minecraft:") ? vanilla : modded).add(Outfit.of("", pieces));
        });
        modded.addAll(vanilla);
        gameSets = List.copyOf(modded);
        return gameSets;
    }

    /** What a wardrobe holds unless an organiser chose its outfits: the ready outfits, then every other set. */
    public static List<Outfit> all() {
        if (all != null) return all;
        List<Outfit> out = outfits();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Outfit o : out) seen.add(o.key());
        for (Outfit o : gameSets()) if (seen.add(o.key())) out.add(o);
        all = List.copyOf(out);
        return all;
    }

    public static boolean contains(String name) {
        return outfits().stream().anyMatch(o -> o.name().equalsIgnoreCase(name));
    }

    /** Adds (or replaces the one of the same name) and writes tenues.json. */
    public static void put(Outfit outfit) {
        List<Outfit> list = outfits();
        list.removeIf(o -> o.name().equalsIgnoreCase(outfit.name()));
        list.add(outfit);
        save(list);
    }

    public static boolean remove(String name) {
        List<Outfit> list = outfits();
        boolean removed = list.removeIf(o -> o.name().equalsIgnoreCase(name));
        if (removed) save(list);
        return removed;
    }

    private static void save(List<Outfit> list) {
        saved = list;
        all = null;
        if (registries == null) return;
        try {
            JsonElement json = LIST.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), list).getOrThrow();
            var root = new com.google.gson.JsonObject();
            root.addProperty("_aide", "Tenues prêtes des garde-robes CraftMaster : name = nom affiché, pieces = tête, corps, jambes, pieds (id d'objet, ou {} pour rien). Voir docs/wiki/GardeRobe.md");
            root.add("tenues", json);
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException | IllegalStateException e) {
            CraftMaster.LOG.warn("tenues.json could not be written", e);
        }
    }

    // ---------------------------------------------------------------- built-in outfits

    /** Selfexpression sets (head, body, legs, feet; "" = nothing) with their French name. */
    private static final String[][] SELFEXPRESSION = {
            {"Médecin", "doctor_helmet", "doctor_chestplate", "doctor_leggings", "doctor_boots"},
            {"Policier", "", "police_chestplate", "police_leggings", "police_boots"},
            {"Chevalier", "knight_helmet", "knight_chestplate", "knight_leggings", "knight_boots"},
            {"Roi", "medieval_king_helmet", "medieval_king_chestplate", "medieval_king_leggings", "medieval_king_boots"},
            {"Bouffon", "jester_helmet", "jester_chestplate", "jester_leggings", "jester_boots"},
            {"Mafieux", "mafia_helmet", "mafia_chestplate", "mafia_leggings", "mafia_boots"},
            {"Capitaine", "captain_helmet", "captain_chestplate", "captain_leggings", "captain_boots"},
            {"Mineur", "miner_helmet", "miner_chestplate", "miner_leggings", "miner_boots"},
            {"Ouvrier du BTP", "builder_helmet", "builder_chestplate", "builder_leggings", "builder_boots"},
            {"Jardinier", "", "gardener_chestplate", "gardener_leggings", ""},
            {"Forgeron", "", "blacksmith_chestplate", "blacksmith_leggings", "blacksmith_boots"},
            {"Ingénieur", "engineer_helmet", "engineer_chestplate", "engineer_leggings", "engineer_boots"},
            {"Avocat", "", "lawer_chestplate", "lawer_leggings", "lawer_boots"},
            {"Costume strict", "", "strict_chestplate", "strict_leggings", "strict_boots"},
            {"Cowboy", "autumn_cowboy_helmet", "autumn_cowboy_chestplate", "autumn_cowboy_leggings", "autumn_cowboy_boots"},
            {"Soldat", "soldier_helmet", "soldier_chestplate", "soldier_leggings", "soldier_boots"},
            {"Prisonnier", "", "prisoner_chestplate", "prisoner_leggings", "prisoner_boots"},
            {"Lutin de Noël", "christmass_elf_helmet", "christmass_elf_chestplate", "christmass_elf_leggings", "christmass_elf_boots"},
            {"Pilote spatial", "space_pilot_helmet", "space_pilot_chestplate", "space_pilot_leggings", "space_pilot_boots"},
            {"Vagabond", "tramp_helmet", "tramp_chestplate", "tramp_leggings", "tramp_boots"},
    };

    static List<Outfit> builtIn() {
        List<Outfit> out = new ArrayList<>();
        if (FabricLoader.getInstance().isModLoaded("selfexpression")) {
            for (String[] s : SELFEXPRESSION) {
                List<ItemStack> pieces = new ArrayList<>();
                for (int i = 1; i <= 4; i++) pieces.add(item("selfexpression:" + s[i]));
                Outfit o = Outfit.of(s[0], pieces);
                if (!o.isEmpty()) out.add(o);
            }
            if (!out.isEmpty()) return out;
        }
        // Without Selfexpression: vanilla armour, leather dyed like a costume
        out.add(Outfit.of("Chevalier", List.of(new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE),
                new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS))));
        out.add(Outfit.of("Roi", List.of(new ItemStack(Items.GOLDEN_HELMET), leather(Items.LEATHER_CHESTPLATE, 0xB02E26, "Manteau royal"),
                leather(Items.LEATHER_LEGGINGS, 0xF9FFFE, "Chausses"), new ItemStack(Items.GOLDEN_BOOTS))));
        out.add(Outfit.of("Chef cuisinier", List.of(leather(Items.LEATHER_HELMET, 0xF9FFFE, "Toque"), leather(Items.LEATHER_CHESTPLATE, 0xF9FFFE, "Veste de chef"),
                leather(Items.LEATHER_LEGGINGS, 0x1D1D21, "Pantalon"), leather(Items.LEATHER_BOOTS, 0x1D1D21, "Sabots"))));
        out.add(Outfit.of("Bleu de travail", List.of(ItemStack.EMPTY, leather(Items.LEATHER_CHESTPLATE, 0x3C44AA, "Bleu de travail"),
                leather(Items.LEATHER_LEGGINGS, 0x3C44AA, "Bleu de travail"), leather(Items.LEATHER_BOOTS, 0x835432, "Chaussures de sécurité"))));
        out.add(Outfit.of("Plongeur", List.of(new ItemStack(Items.TURTLE_HELMET), leather(Items.LEATHER_CHESTPLATE, 0x169C9C, "Combinaison"),
                leather(Items.LEATHER_LEGGINGS, 0x169C9C, "Combinaison"), leather(Items.LEATHER_BOOTS, 0x1D1D21, "Palmes"))));
        out.add(Outfit.of("Citrouille", List.of(new ItemStack(Items.CARVED_PUMPKIN), leather(Items.LEATHER_CHESTPLATE, 0xF9801D, "Costume orange"),
                leather(Items.LEATHER_LEGGINGS, 0x5E7C16, "Tige"), leather(Items.LEATHER_BOOTS, 0x5E7C16, "Tige"))));
        out.add(Outfit.of("Cowboy", List.of(leather(Items.LEATHER_HELMET, 0x835432, "Chapeau"), leather(Items.LEATHER_CHESTPLATE, 0xB02E26, "Chemise"),
                leather(Items.LEATHER_LEGGINGS, 0x3C44AA, "Jean"), leather(Items.LEATHER_BOOTS, 0x835432, "Santiags"))));
        out.add(Outfit.of("Squelette", List.of(new ItemStack(Items.SKELETON_SKULL), new ItemStack(Items.CHAINMAIL_CHESTPLATE),
                new ItemStack(Items.CHAINMAIL_LEGGINGS), new ItemStack(Items.CHAINMAIL_BOOTS))));
        return out;
    }

    private static ItemStack item(String id) {
        if (id.endsWith(":")) return ItemStack.EMPTY;
        Identifier key = Identifier.tryParse(id);
        if (key == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(key);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static ItemStack leather(Item item, int rgb, String name) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
        s.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(st -> st.withItalic(false)));
        return s;
    }
}
