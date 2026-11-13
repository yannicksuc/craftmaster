package fr.lordfinn.craftmaster.studio;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Orders given to several spotlights at once: all of them, one by name, or a group ({@code face*} = every spotlight
 * whose name starts with "face"). Used by the commands, the voice control and Stream Deck ({@code spotlight.*}
 * actions), the stage looks and cue lists. Each order returns a French line saying what was done.
 */
public final class Spotlights {

    private Spotlights() {}

    public static final String ALL = "tout";

    /**
     * The loaded spotlights a target names: {@code tout} / {@code all} / empty, a name, or a group {@code prefix*}.
     */
    public static List<SpotlightBlockEntity> select(String target) {
        List<SpotlightBlockEntity> all = SpotlightBlockEntity.loaded();
        String t = target == null ? "" : target.strip().toLowerCase(Locale.ROOT);
        if (t.isEmpty() || t.equals(ALL) || t.equals("all") || t.equals("tous") || t.equals("*")) return all;
        List<SpotlightBlockEntity> out = new ArrayList<>();
        boolean prefix = t.endsWith("*");
        String key = prefix ? t.substring(0, t.length() - 1) : t;
        for (SpotlightBlockEntity s : all)
            if (prefix ? s.name().startsWith(key) : s.name().equals(key)) out.add(s);
        // A name nobody has exactly: try it as a group ("face" also finds face1, face2)
        if (out.isEmpty() && !prefix) for (SpotlightBlockEntity s : all) if (!s.name().isEmpty() && s.name().startsWith(key)) out.add(s);
        return out;
    }

    /** Names in use, for completion. */
    public static List<String> names() {
        return SpotlightBlockEntity.loaded().stream().map(SpotlightBlockEntity::name).filter(n -> !n.isEmpty()).distinct().sorted().toList();
    }

    /** What to change; null fields stay as they are. */
    public record Settings(Boolean lit, Integer color, Integer intensity, Float cone, String follow, Vec3 aimAt, Float yaw, Float pitch) {
        public static Settings none() {
            return new Settings(null, null, null, null, null, null, null, null);
        }
    }

    /** Applies settings to every spotlight of the target, reaching them in {@code fadeSeconds} (0: right away). */
    public static String apply(MinecraftServer server, String target, Settings set, double fadeSeconds) {
        List<SpotlightBlockEntity> list = select(target);
        if (list.isEmpty()) return SpotlightBlockEntity.loaded().isEmpty() ? "Aucun projecteur chargé." : "Aucun projecteur « " + target + " ».";
        for (SpotlightBlockEntity s : list) apply(s, set, fadeSeconds);
        return list.size() + " projecteur(s)" + (isAll(target) ? "" : " « " + target + " »") + " : " + describe(set)
                + (fadeSeconds > 0 ? " en " + seconds(fadeSeconds) : "") + ".";
    }

    public static void apply(SpotlightBlockEntity s, Settings set, double fadeSeconds) {
        s.setFade((int) Math.round(fadeSeconds * 20));
        if (set.color() != null) s.setColor(set.color());
        if (set.intensity() != null) s.setIntensity(set.intensity());
        if (set.cone() != null) s.setCone(set.cone());
        if (set.aimAt() != null) s.aimAt(set.aimAt());
        if (set.yaw() != null && set.pitch() != null) s.aim(set.yaw(), set.pitch());
        if (set.follow() != null) s.follow(set.follow());
        // Following someone switches the light on
        Boolean lit = set.lit() != null ? set.lit() : set.follow() != null && !set.follow().isEmpty() ? Boolean.TRUE : null;
        if (lit != null) SpotlightBlock.setLit(s.getLevel(), s.getBlockPos(), s.getBlockState(), lit);
    }

    private static boolean isAll(String target) {
        String t = target == null ? "" : target.strip().toLowerCase(Locale.ROOT);
        return t.isEmpty() || t.equals(ALL) || t.equals("all") || t.equals("tous") || t.equals("*");
    }

    public static String describe(Settings set) {
        List<String> parts = new ArrayList<>();
        if (set.lit() != null) parts.add(set.lit() ? "allumé(s)" : "éteint(s)");
        if (set.color() != null) parts.add("couleur " + colorName(set.color()));
        if (set.intensity() != null) parts.add("intensité " + set.intensity() + " %");
        if (set.cone() != null) parts.add("cône " + seconds(set.cone()).replace(" s", "") + "°");
        if (set.follow() != null) parts.add(set.follow().isEmpty() ? "ne suivent plus personne" : "suivent " + SpotlightFollow.describe(set.follow()));
        if (set.aimAt() != null || set.yaw() != null) parts.add("pointés");
        return parts.isEmpty() ? "rien à changer" : String.join(", ", parts);
    }

    static String seconds(double s) {
        return (s == Math.rint(s) ? String.valueOf((long) s) : String.valueOf(Math.round(s * 10) / 10.0).replace('.', ',')) + " s";
    }

    // ---------------------------------------------------------------- colours

    private static final Map<String, String> FRENCH = Map.ofEntries(
            Map.entry("blanc", "white"), Map.entry("rouge", "red"), Map.entry("bleu", "blue"), Map.entry("vert", "green"),
            Map.entry("jaune", "yellow"), Map.entry("orange", "orange"), Map.entry("violet", "purple"), Map.entry("rose", "pink"),
            Map.entry("magenta", "magenta"), Map.entry("cyan", "cyan"), Map.entry("bleu clair", "light_blue"),
            Map.entry("bleu ciel", "light_blue"), Map.entry("vert clair", "lime"), Map.entry("citron", "lime"),
            Map.entry("gris", "gray"), Map.entry("gris clair", "light_gray"), Map.entry("noir", "black"), Map.entry("marron", "brown"));

    /** Extra colours a light board has, by French name. */
    private static final Map<String, Integer> NAMED = Map.of(
            "chaud", SpotlightBlockEntity.DEFAULT_COLOR, "blanc chaud", SpotlightBlockEntity.DEFAULT_COLOR,
            "froid", 0xDDEBFF, "blanc froid", 0xDDEBFF, "ambre", 0xFFC46B, "or", 0xFFD966, "dore", 0xFFD966,
            "lavande", 0xC9B8FF, "turquoise", 0x7FF0E0);

    /** A dye name (English or French), a named colour (ambre, froid…) or #RRGGBB; null if unknown. */
    public static Integer parseColor(String s) {
        if (s == null) return null;
        String k = fr.lordfinn.craftmaster.camera.CameraList.fold(s.strip()).replace('_', ' ');
        if (k.matches("#?[0-9a-f]{6}")) return Integer.parseInt(k.replace("#", ""), 16);
        if (NAMED.containsKey(k)) return NAMED.get(k);
        String dyeName = FRENCH.getOrDefault(k, k.replace(' ', '_'));
        DyeColor dye = DyeColor.byName(dyeName, null);
        return dye == null ? null : SpotlightBlock.beamColor(dye);
    }

    /** Colour words for completion. */
    public static final List<String> COLOR_WORDS = List.of("blanc", "chaud", "froid", "ambre", "or", "rouge", "orange", "jaune",
            "vert", "cyan", "bleu", "bleu_clair", "violet", "rose", "magenta", "lavande", "turquoise");

    private static String colorName(int rgb) {
        for (String w : COLOR_WORDS) {
            Integer c = parseColor(w);
            if (c != null && c == rgb) return w.replace('_', ' ');
        }
        return String.format("#%06X", rgb);
    }

    // ---------------------------------------------------------------- older orders (kept for the voice control)

    /** All spotlights follow a player (by exact name, case ignored) or an automatic rule. */
    public static String follow(MinecraftServer server, String player) {
        String rule = SpotlightFollow.parse(player);
        String name = player;
        if (rule != null) name = rule;
        else {
            ServerPlayer p = server.getPlayerList().getPlayerByName(player);
            if (p != null) name = p.getGameProfile().name();
        }
        return apply(server, ALL, new Settings(true, null, null, null, name, null, null, null), 0);
    }

    /** All spotlights stop following (they keep their last direction). */
    public static String stop(MinecraftServer server) {
        return apply(server, ALL, new Settings(null, null, null, null, "", null, null, null), 0);
    }

    public static String setLit(MinecraftServer server, boolean lit) {
        return apply(server, ALL, new Settings(lit, null, null, null, null, null, null, null), 0);
    }

    public static String color(MinecraftServer server, DyeColor dye) {
        return apply(server, ALL, new Settings(null, SpotlightBlock.beamColor(dye), null, null, null, null, null, null), 0);
    }
}
