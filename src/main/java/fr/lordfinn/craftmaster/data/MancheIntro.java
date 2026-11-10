package fr.lordfinn.craftmaster.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * The animated intro of a round ("intro de manche", chantier E): a list of shots played one after the other on the
 * broadcast — fixed cameras, recorded travellings or follow shots — each with its length, the transition into it,
 * an optional screen effect and an optional card (big title or lower third with the round's name). Optional music
 * cue for the whole intro, and {@link #auto}: play it by itself when the round starts.
 *
 * <p>Part of the round's data ({@code intro:} under the round in config.yml, absent when empty), so every change is
 * undoable like the rest. Pure data and timing (no Minecraft); played by {@code broadcast.MancheIntros}.
 */
public final class MancheIntro {

    /** Shot kinds: a camera (reference resolved in the round's lieu), a travelling, a follow shot. */
    public static final String CAMERA = "camera", PATH = "trajet", FOLLOW = "suivi";
    /** Cards: none, the big title card, the lower third. */
    public static final String NO_CARD = "", TITLE = "titre", LOWER_THIRD = "synthe";
    /** Default length of a camera or follow shot; length of a travelling = its own unless given (seconds). */
    public static final double DEFAULT_SECONDS = 4, MAX_SECONDS = 120;
    public static final int MAX_SHOTS = 24;

    /**
     * One shot of the intro.
     *
     * @param ref        camera reference ("2", "large", "ensuri:1", "studio:3"), path name or player
     * @param seconds    length, 0 = default (a travelling's own length)
     * @param transition coupe | fondu | enchaine, "" = enchaine
     * @param effect     screen effect id ("noir_blanc", "sepia"…), "" = none
     * @param card       {@link #TITLE}, {@link #LOWER_THIRD} or ""
     * @param text       card text ("" = the round's name; "Titre | sous-titre" for both lines)
     */
    public record Shot(String kind, String ref, double seconds, String transition, String effect, String card, String text) {
        public Shot {
            kind = normalizeKind(kind);
            ref = ref == null ? "" : ref.trim();
            seconds = Double.isFinite(seconds) ? Math.max(0, Math.min(MAX_SECONDS, seconds)) : 0;
            transition = transition == null ? "" : transition.trim().toLowerCase(Locale.ROOT);
            effect = effect == null ? "" : effect.trim();
            card = normalizeCard(card);
            text = text == null ? "" : text.trim();
        }

        public Shot withSeconds(double s) { return new Shot(kind, ref, s, transition, effect, card, text); }
        public Shot withTransition(String t) { return new Shot(kind, ref, seconds, t, effect, card, text); }
        public Shot withEffect(String e) { return new Shot(kind, ref, seconds, transition, e, card, text); }
        public Shot withCard(String c, String t) { return new Shot(kind, ref, seconds, transition, effect, c, t); }

        /** "caméra large, 4 s, enchaîné, effet sepia, titre" */
        public String describe() {
            StringBuilder b = new StringBuilder(switch (kind) {
                case PATH -> "trajet " + ref;
                case FOLLOW -> "suivi de " + ref;
                default -> "caméra " + ref;
            });
            b.append(", ").append(seconds > 0 ? fmt(seconds) : kind.equals(PATH) ? "durée du trajet" : fmt(DEFAULT_SECONDS));
            if (!transition.isEmpty()) b.append(", ").append(transition);
            if (!effect.isEmpty()) b.append(", effet ").append(effect);
            if (!card.isEmpty()) b.append(", ").append(card.equals(TITLE) ? "titre" : "synthé").append(text.isEmpty() ? "" : " « " + text + " »");
            return b.toString();
        }

        Map<String, Object> toYaml() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", kind);
            m.put("ref", ref);
            if (seconds > 0) m.put("seconds", seconds);
            if (!transition.isEmpty()) m.put("transition", transition);
            if (!effect.isEmpty()) m.put("effect", effect);
            if (!card.isEmpty()) m.put("card", card);
            if (!text.isEmpty()) m.put("text", text);
            return m;
        }

        static Shot fromYaml(Map<String, Object> m) {
            return new Shot(Maps.str(m.get("kind"), CAMERA), Maps.str(m.get("ref"), ""), Maps.toDouble(m.get("seconds")),
                    Maps.str(m.get("transition"), ""), Maps.str(m.get("effect"), ""), Maps.str(m.get("card"), ""), Maps.str(m.get("text"), ""));
        }
    }

    /** A shot placed on the intro's timeline (seconds from the start). */
    public record Timed(int index, Shot shot, double start, double length) {}

    /** Play by itself when the round starts. */
    public boolean auto;
    /** Music cue: "" = none, "theme" = the show's theme, else a sound id ("minecraft:music_disc.otherside"). */
    public String music = "";
    /** With no card on any shot: the title card goes on the last shot (false = no card at all). */
    public boolean titleCard = true;
    public final List<Shot> shots = new ArrayList<>();

    public boolean isEmpty() {
        return shots.isEmpty() && !auto && music.isEmpty() && titleCard;
    }

    public static String normalizeKind(String k) {
        if (k == null) return CAMERA;
        return switch (k.trim().toLowerCase(Locale.ROOT)) {
            case "trajet", "path", "travelling", "traveling" -> PATH;
            case "suivi", "follow", "suis", "joueur", "player" -> FOLLOW;
            default -> CAMERA;
        };
    }

    public static String normalizeCard(String c) {
        if (c == null) return NO_CARD;
        String k = java.text.Normalizer.normalize(c.trim().toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return switch (k) {
            case "titre", "title", "carton", "title_card", "grand titre" -> TITLE;
            case "synthe", "lower_third", "lowerthird", "bandeau", "nom" -> LOWER_THIRD;
            default -> NO_CARD;
        };
    }

    /** "" / "aucune" / "non" → ""; "theme" / "générique" → "theme"; anything else is a sound id. */
    public static String normalizeMusic(String m) {
        if (m == null) return "";
        String k = m.trim().toLowerCase(Locale.ROOT);
        return switch (k) {
            case "", "aucune", "aucun", "non", "off", "none", "rien", "silence" -> "";
            case "theme", "thème", "generique", "générique", "craftmaster:show.theme" -> "theme";
            default -> k.contains(":") ? k : "minecraft:" + k;
        };
    }

    /**
     * Each shot's start and length: a travelling without a length lasts {@code pathSeconds} (0 or less: the
     * default), any other shot {@link #DEFAULT_SECONDS}.
     */
    public List<Timed> timeline(ToDoubleFunction<String> pathSeconds) {
        List<Timed> out = new ArrayList<>();
        double t = 0;
        for (int i = 0; i < shots.size(); i++) {
            Shot s = shots.get(i);
            double len = s.seconds();
            if (len <= 0 && s.kind().equals(PATH)) len = pathSeconds.applyAsDouble(s.ref());
            if (len <= 0) len = DEFAULT_SECONDS;
            out.add(new Timed(i, s, t, len));
            t += len;
        }
        return out;
    }

    /** Total length in seconds. */
    public double duration(ToDoubleFunction<String> pathSeconds) {
        List<Timed> tl = timeline(pathSeconds);
        return tl.isEmpty() ? 0 : tl.getLast().start() + tl.getLast().length();
    }

    /** Index of the shot that shows the automatic title card (no shot has a card, {@link #titleCard} on): the last one; -1 otherwise. */
    public int autoCardShot() {
        if (!titleCard || shots.isEmpty()) return -1;
        for (Shot s : shots) if (!s.card().isEmpty()) return -1;
        return shots.size() - 1;
    }

    public MancheIntro copy() {
        MancheIntro c = new MancheIntro();
        c.auto = auto;
        c.music = music;
        c.titleCard = titleCard;
        c.shots.addAll(shots);
        return c;
    }

    static String fmt(double s) {
        return (s == Math.rint(s) ? String.valueOf((long) s) : String.format(Locale.ROOT, "%.1f", s).replace('.', ',')) + " s";
    }

    Map<String, Object> toYaml() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (auto) m.put("auto", true);
        if (!music.isEmpty()) m.put("music", music);
        if (!titleCard) m.put("titleCard", false);
        List<Object> list = new ArrayList<>();
        for (Shot s : shots) list.add(s.toYaml());
        if (!list.isEmpty()) m.put("shots", list);
        return m;
    }

    static MancheIntro fromYaml(Object o) {
        MancheIntro i = new MancheIntro();
        if (!(o instanceof Map<?, ?>)) return i;
        Map<String, Object> m = Maps.map(o);
        i.auto = Maps.bool(m.get("auto"));
        i.music = normalizeMusic(Maps.str(m.get("music"), ""));
        i.titleCard = m.get("titleCard") == null || Maps.bool(m.get("titleCard"));
        for (Object s : Maps.list(m.get("shots"))) if (s instanceof Map<?, ?>) i.shots.add(Shot.fromYaml(Maps.map(s)));
        return i;
    }
}
