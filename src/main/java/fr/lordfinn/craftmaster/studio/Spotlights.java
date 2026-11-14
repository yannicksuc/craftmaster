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
 * Orders given to several spotlights at once: all of them, one by name, or a group: a name nobody has exactly, or
 * {@code face*}, means every spotlight whose name starts with it ("face" = face1, face2…). Used by the commands, the voice control and Stream Deck ({@code spotlight.*}
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
        // "@x,y,z": the spotlight at that place (the chat panel's buttons)
        if (t.matches("@-?\\d+,-?\\d+,-?\\d+")) {
            String[] c = t.substring(1).split(",");
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
            List<SpotlightBlockEntity> out = new ArrayList<>();
            for (SpotlightBlockEntity s : all) if (s.getBlockPos().equals(pos)) out.add(s);
            return out;
        }
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

    /** Shows or hides what the clients draw of a spotlight: its beam ({@code beam}) or its pool. */
    public static void setDrawn(SpotlightBlockEntity s, boolean beam, boolean on) {
        if (beam) s.setBeam(on);
        else s.setPool(on);
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
        if (set.aimAt() != null || set.yaw() != null) parts.add("pointés");
        else if (set.follow() != null) parts.add(set.follow().isEmpty() ? "ne suivent plus personne" : "suivent " + SpotlightFollow.describe(set.follow()));
        return parts.isEmpty() ? "rien à changer" : String.join(", ", parts);
    }

    static String seconds(double s) {
        return (s == Math.rint(s) ? String.valueOf((long) s) : String.valueOf(Math.round(s * 10) / 10.0).replace('.', ',')) + " s";
    }

    /** "@x,y,z" of a spotlight, for the chat panel's buttons. */
    public static String at(SpotlightBlockEntity s) {
        return "@" + s.getBlockPos().getX() + "," + s.getBlockPos().getY() + "," + s.getBlockPos().getZ();
    }

    /**
     * Turns every spotlight of the target by {@code dYaw} / {@code dPitch} degrees (relative to each one's own aim),
     * or points it the other way ({@code flip}: half a turn, the tilt mirrored), in {@code fade} seconds. A
     * spotlight following someone stops following first, from where it points.
     */
    public static String turn(String target, float dYaw, float dPitch, boolean flip, double fade) {
        List<SpotlightBlockEntity> list = select(target);
        if (list.isEmpty()) return SpotlightBlockEntity.loaded().isEmpty() ? "Aucun projecteur chargé." : "Aucun projecteur « " + target + " ».";
        for (SpotlightBlockEntity s : list) {
            float yaw = s.yaw() + dYaw + (flip ? 180 : 0), pitch = flip ? -s.pitch() : s.pitch() + dPitch;
            apply(s, new Settings(null, null, null, null, "", null, yaw, pitch), fade);
        }
        String what = flip ? "inversé(s)" : (dYaw != 0 ? "tourné(s) de " + Math.round(dYaw) + "°" : "")
                + (dYaw != 0 && dPitch != 0 ? ", " : "") + (dPitch != 0 ? "incliné(s) de " + Math.round(dPitch) + "°" : "");
        return list.size() + " projecteur(s) " + what + (fade > 0 ? " en " + seconds(fade) : "") + ".";
    }

    /** One spotlight's settings in the chat, with buttons to change them (organisers). */
    public static net.minecraft.network.chat.Component panel(SpotlightBlockEntity s) {
        String t = at(s);
        String base = "/cfsp groupe \"" + t + "\" ";
        net.minecraft.network.chat.MutableComponent c = net.minecraft.network.chat.Component.literal(
                "◉ Projecteur" + (s.name().isEmpty() ? "" : " « " + s.name() + " »") + " — horizontal " + Math.round(s.yaw())
                        + "°, vertical " + Math.round(s.pitch()) + "°, " + (s.lit() ? "allumé " + s.intensity() + " %" : "éteint")
                        + ", cône " + Math.round(s.cone()) + "°" + (s.follow().isEmpty() ? "" : ", suit " + SpotlightFollow.describe(s.follow())) + "\n ")
                .withStyle(net.minecraft.ChatFormatting.GOLD);
        c.append(button("[◀ 15°]", base + "tourner -15 0.5", "Tourner vers la gauche"));
        c.append(button("[15° ▶]", base + "tourner 15 0.5", "Tourner vers la droite"));
        c.append(button("[▲ 10°]", base + "incliner -10 0.5", "Lever"));
        c.append(button("[▼ 10°]", base + "incliner 10 0.5", "Baisser"));
        c.append(button("[⇄ inverser]", base + "inverser 1", "Pointer dans l'autre sens"));
        c.append(button("[◎ vers moi]", base + "ici 0.5", "Pointer vers toi"));
        c.append(button(s.lit() ? "[éteindre]" : "[allumer]", base + (s.lit() ? "eteint 0.5" : "allume 0.5"), ""));
        c.append(button("[angle exact…]", null, "Taper l'angle : /cfsp groupe \"" + t + "\" angle <horizontal> <vertical> [secondes]"));
        return c;
    }

    private static net.minecraft.network.chat.MutableComponent button(String text, String command, String hover) {
        String suggest = command == null ? null : command;
        return net.minecraft.network.chat.Component.literal(" " + text).withStyle(st -> {
            st = st.withColor(net.minecraft.ChatFormatting.GREEN);
            if (suggest != null) st = st.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(suggest));
            else st = st.withClickEvent(new net.minecraft.network.chat.ClickEvent.SuggestCommand(hover.substring(hover.indexOf('/'), hover.indexOf(" <")) + " "));
            if (!hover.isEmpty()) st = st.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(net.minecraft.network.chat.Component.literal(hover)));
            return st;
        });
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
