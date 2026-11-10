package fr.lordfinn.craftmaster.trial;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What the scales say an item weighs: a playful but consistent table (a block of netherite is heavier than a block
 * of iron, a feather weighs almost nothing). The first keyword of the item id that matches decides the weight of
 * one item, in kilograms; a block (full block item) weighs much more than the same material as an ingot or a
 * nugget. Pure: tested, and easy to tune.
 */
public final class Weights {

    private Weights() {}

    /** Keyword of the item id → kg for one item. Order matters: the first match wins. */
    private static final Map<String, Double> TABLE = new LinkedHashMap<>();

    static {
        // Very light things
        TABLE.put("feather", 0.005);
        TABLE.put("paper", 0.005);
        TABLE.put("string", 0.005);
        TABLE.put("petal", 0.002);
        TABLE.put("seeds", 0.002);
        TABLE.put("nugget", 0.01);
        TABLE.put("dust", 0.01);
        TABLE.put("redstone", 0.01);
        TABLE.put("sugar", 0.01);
        TABLE.put("leaf", 0.02);
        TABLE.put("leaves", 0.3);
        TABLE.put("wool", 1.0);
        TABLE.put("carpet", 0.4);
        TABLE.put("flower", 0.02);
        TABLE.put("sapling", 0.1);
        // Metals and gems: blocks first (keywords are tried in order)
        TABLE.put("netherite_block", 900.0);
        TABLE.put("netherite", 12.0);
        TABLE.put("ancient_debris", 300.0);
        TABLE.put("gold_block", 600.0);
        TABLE.put("gold", 3.0);
        TABLE.put("iron_block", 400.0);
        TABLE.put("anvil", 300.0);
        TABLE.put("iron", 2.0);
        TABLE.put("copper_block", 350.0);
        TABLE.put("copper", 1.8);
        TABLE.put("diamond_block", 200.0);
        TABLE.put("diamond", 0.5);
        TABLE.put("emerald_block", 180.0);
        TABLE.put("emerald", 0.4);
        TABLE.put("lapis_block", 150.0);
        TABLE.put("lapis", 0.2);
        TABLE.put("amethyst", 0.6);
        // Stones, earth, glass, wood
        TABLE.put("obsidian", 250.0);
        TABLE.put("bedrock", 999.0);
        TABLE.put("deepslate", 180.0);
        TABLE.put("stone", 150.0);
        TABLE.put("cobble", 150.0);
        TABLE.put("brick", 120.0);
        TABLE.put("sand", 100.0);
        TABLE.put("gravel", 110.0);
        TABLE.put("dirt", 90.0);
        TABLE.put("clay", 95.0);
        TABLE.put("glass_pane", 3.0);
        TABLE.put("glass", 25.0);
        TABLE.put("log", 60.0);
        TABLE.put("planks", 30.0);
        TABLE.put("slab", 15.0);
        TABLE.put("stairs", 25.0);
        TABLE.put("door", 20.0);
        TABLE.put("chest", 25.0);
        TABLE.put("barrel", 25.0);
        // Food and mobs' drops
        TABLE.put("melon", 4.0);
        TABLE.put("pumpkin", 5.0);
        TABLE.put("cake", 1.2);
        TABLE.put("bread", 0.4);
        TABLE.put("apple", 0.2);
        TABLE.put("carrot", 0.1);
        TABLE.put("potato", 0.2);
        TABLE.put("beef", 0.5);
        TABLE.put("porkchop", 0.5);
        TABLE.put("egg", 0.06);
        TABLE.put("bone", 0.2);
        TABLE.put("bucket", 1.0);
        TABLE.put("head", 3.0);
        TABLE.put("skull", 3.0);
        // Tools, armour
        TABLE.put("sword", 1.5);
        TABLE.put("pickaxe", 2.5);
        TABLE.put("axe", 2.5);
        TABLE.put("shovel", 1.5);
        TABLE.put("helmet", 2.0);
        TABLE.put("chestplate", 8.0);
        TABLE.put("leggings", 6.0);
        TABLE.put("boots", 2.0);
        TABLE.put("stick", 0.05);
    }

    /** Weight of {@code count} items of that id ("minecraft:iron_block"). {@code isBlock}: a block item. */
    public static double of(String itemId, int count, boolean isBlock) {
        return perItem(itemId, isBlock) * Math.max(0, count);
    }

    /** Weight of one item, kg. */
    public static double perItem(String itemId, boolean isBlock) {
        String id = itemId == null ? "" : itemId.toLowerCase(Locale.ROOT);
        int colon = id.indexOf(':');
        if (colon >= 0) id = id.substring(colon + 1);
        for (Map.Entry<String, Double> e : TABLE.entrySet()) if (id.contains(e.getKey())) return e.getValue();
        return isBlock ? 50.0 : 0.5;
    }
}
