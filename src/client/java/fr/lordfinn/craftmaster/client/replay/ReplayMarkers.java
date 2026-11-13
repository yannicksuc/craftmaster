package fr.lordfinn.craftmaster.client.replay;

import fr.lordfinn.craftmaster.replay.ReplayNetwork.Marker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The key moments on the replay's bar: one small icon per kind (death, result, timer, bell, flags…) in the colour of
 * the player it is about, moments too close together drawn as one (the bubble lists them), and the filter the voice
 * and the Stream Deck set ("only the deaths", "Ensuri's moments", "the moments of zone B"): previous / next follow it.
 */
final class ReplayMarkers {

    private ReplayMarkers() {}

    /** A family of kinds, as the voice names them. */
    record Family(String label, Set<String> kinds) {}

    static final List<Family> FAMILIES = List.of(
            new Family("morts", Set.of("death")),
            new Family("résultats", Set.of("result", "dq", "time")),
            new Family("chronos", Set.of("timer", "time")),
            new Family("cloches", Set.of("bell")),
            new Family("drapeaux", Set.of("flag", "player_flag")),
            new Family("drapeaux des joueurs", Set.of("player_flag")),
            new Family("points", Set.of("score")),
            new Family("manches", Set.of("round", "task", "reveal")),
            new Family("sorties de zone", Set.of("outing", "back")),
            new Family("projecteurs", Set.of("spotlight")));

    /** The filter: null kinds = every kind; "" = every player / every zone. */
    private static Family family;
    private static String player = "", zone = "";

    static void clear() {
        family = null;
        player = "";
        zone = "";
    }

    static boolean filtered() {
        return family != null || !player.isEmpty() || !zone.isEmpty();
    }

    static boolean visible(Marker m) {
        if (family != null && !family.kinds().contains(m.kind())) return false;
        if (!player.isEmpty() && !m.player().equalsIgnoreCase(player)) return false;
        // A zone: its moments, and those about no one (the round, the timer) which belong to them all
        return zone.isEmpty() || m.player().isEmpty() || sameZone(m.zone(), zone);
    }

    /** "zone B", "b" and "zoneb" are the same zone. */
    static boolean sameZone(String a, String b) {
        String x = fold(a).replace(" ", "").replaceFirst("^zone", ""), y = fold(b).replace(" ", "").replaceFirst("^zone", "");
        return !x.isEmpty() && x.equals(y);
    }

    static List<Marker> visible() {
        List<Marker> out = new ArrayList<>();
        for (Marker m : ReplayClient.markers()) if (visible(m)) out.add(m);
        return out;
    }

    /**
     * Sets the filter (any of the three may be null = unchanged, "" or "tout" = cleared); the caption it shows, or an
     * error starting with "!".
     */
    static String filter(String kind, String who, String where) {
        if (kind != null) {
            String k = fold(kind);
            if (k.isEmpty() || k.equals("tout") || k.equals("tous")) family = null;
            else {
                Family f = find(k);
                if (f == null) return "!Type de repère inconnu : " + kind;
                family = f;
            }
        }
        if (who != null) player = fold(who).equals("tout") || fold(who).equals("tous") ? "" : who.trim();
        if (where != null) zone = fold(where).equals("tout") || fold(where).equals("toutes") ? "" : where.trim();
        return caption();
    }

    private static Family find(String k) {
        if (k.startsWith("drapeau") && k.contains("joueur")) return FAMILIES.get(5);
        for (Family f : FAMILIES) if (fold(f.label()).equals(k)) return f;
        // "mort", "drapeau", "cloche", or a kind's own name ("death")
        for (Family f : FAMILIES) if (fold(f.label()).startsWith(k) || k.startsWith(fold(f.label()).replaceAll("s$", ""))) return f;
        for (Family f : FAMILIES) if (f.kinds().contains(k)) return f;
        return switch (k) {
            case "mort", "deces", "elimination" -> FAMILIES.get(0);
            case "reussite", "reussites", "resultat", "victoire" -> FAMILIES.get(1);
            case "minuteur", "chrono", "fin du chrono" -> FAMILIES.get(2);
            case "sortie", "sorties", "hors zone" -> FAMILIES.get(8);
            default -> null;
        };
    }

    static String caption() {
        if (!filtered()) return "Tous les repères";
        StringBuilder b = new StringBuilder("Repères : ");
        b.append(family == null ? "tous" : family.label());
        if (!player.isEmpty()) b.append(" — ").append(player);
        if (!zone.isEmpty()) b.append(" — zone ").append(zone);
        return b.toString();
    }

    static String describe() {
        return caption();
    }

    static String fold(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    // ---------------------------------------------------------------- icons

    /** 5 × 5 pixel icons ('#' = drawn), bottom row on the timeline's top. */
    private static String[] icon(String kind) {
        return switch (kind) {
            case "death" -> new String[]{"#...#", ".#.#.", "..#..", ".#.#.", "#...#"};
            case "result" -> new String[]{"....#", "...#.", "#.#..", ".#...", "....."};
            case "dq" -> new String[]{".###.", "#..##", "#.#.#", "##..#", ".###."};
            case "time", "timer" -> new String[]{"#####", ".#.#.", "..#..", ".#.#.", "#####"};
            case "bell" -> new String[]{"..#..", ".###.", ".###.", "#####", "..#.."};
            case "flag" -> new String[]{"###..", "####.", "###..", "#....", "#...."};
            case "player_flag" -> new String[]{"#....", "##...", "#.#..", "##...", "#...."};
            case "score" -> new String[]{".....", ".###.", ".###.", ".###.", "....."};
            case "spotlight" -> new String[]{"..#..", ".###.", "#####", ".###.", "..#.."};
            case "outing" -> new String[]{"#####", "#...#", "#...#", "#...#", "#####"};
            case "back" -> new String[]{".....", ".###.", ".#.#.", ".###.", "....."};
            case "task", "reveal" -> new String[]{"..#..", "..#..", "#####", "..#..", "..#.."};
            default -> null;
        };
    }

    /** One marker's icon centred on {@code x}, standing on {@code baseY}. */
    static void draw(GuiGraphicsExtractor g, Marker m, int x, int baseY, float alpha, boolean big) {
        int color = m.color() & 0xFFFFFF;
        if (m.kind().equals("round") || m.kind().isEmpty()) {
            // A round's start or end: a taller gold line; an old marker: the small tick
            int h = m.kind().isEmpty() ? 3 : 7;
            g.fill(x, baseY - h, x + 1, baseY, argb(color, alpha));
            return;
        }
        String[] rows = icon(m.kind());
        if (rows == null) {
            g.fill(x, baseY - 3, x + 1, baseY, argb(color, alpha));
            return;
        }
        int s = big ? 2 : 1, left = x - 2 * s, top = baseY - 6 * s;
        // A soft shadow, so it reads on a bright sky
        for (int r = 0; r < 5; r++)
            for (int c = 0; c < 5; c++)
                if (rows[r].charAt(c) == '#') g.fill(left + c * s + 1, top + r * s + 1, left + c * s + s + 1, top + r * s + s + 1, argb(0, alpha * 0.45f));
        for (int r = 0; r < 5; r++)
            for (int c = 0; c < 5; c++)
                if (rows[r].charAt(c) == '#') g.fill(left + c * s, top + r * s, left + c * s + s, top + r * s + s, argb(color, alpha));
    }

    static int argb(int rgb, float alpha) {
        return ((int) (255 * Math.max(0, Math.min(1, alpha))) << 24) | (rgb & 0xFFFFFF);
    }

    // ---------------------------------------------------------------- groups

    /** Markers drawn as one: those closer than {@link #GROUP_PX} on the bar. */
    record Group(int x, List<Marker> markers) {
        Marker first() {
            return markers.getFirst();
        }
    }

    static final int GROUP_PX = 8;

    static List<Group> groups(int left, int right, long duration) {
        List<Group> out = new ArrayList<>();
        long dur = Math.max(1, duration);
        Group cur = null;
        for (Marker m : visible()) {
            int x = left + (int) Math.round((right - left) * Math.min(1.0, m.ms() / (double) dur));
            if (cur != null && x - cur.x() < GROUP_PX) {
                cur.markers().add(m);
                continue;
            }
            cur = new Group(x, new ArrayList<>(List.of(m)));
            out.add(cur);
        }
        return out;
    }
}
