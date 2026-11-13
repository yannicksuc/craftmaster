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

    /**
     * Shot kinds: a camera (reference resolved in the round's lieu), a travelling, a follow shot, and the "task
     * location" shot (a viewpoint found at play time on the arrival point and the envelope placed there).
     */
    public static final String CAMERA = "camera", PATH = "trajet", FOLLOW = "suivi", TASK = "tache";
    /** Cards: none, the big title card, the lower third. */
    public static final String NO_CARD = "", TITLE = "titre", LOWER_THIRD = "synthe";
    /** Default length of a camera or follow shot; length of a travelling = its own unless given (seconds). */
    public static final double DEFAULT_SECONDS = 4, MAX_SECONDS = 120;
    public static final int MAX_SHOTS = 24;
    /** Tempo of the show's theme (scripts/gen_theme.py), used by "rythme theme". */
    public static final double THEME_BPM = 112;
    /** Wanted length of a shot cut on the music when none is given (seconds; rounded to whole beats). */
    public static final double BEAT_SHOT_SECONDS = 3;
    public static final double MIN_BPM = 40, MAX_BPM = 240;
    /** The task intro music ({@code craftmaster:show.intro}, scripts/gen_intro_music.py): tempo and length. */
    public static final double INTRO_BPM = 120, INTRO_MUSIC_SECONDS = 20;
    /** Music cue of the task intro music. */
    public static final String INTRO_MUSIC = "intro";

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
            return describe(seconds > 0 ? fmt(seconds) : kind.equals(PATH) ? "durée du trajet" : fmt(DEFAULT_SECONDS));
        }

        /** With the length as played (cut on the music: whole beats). */
        public String describe(Timed played, double beatSeconds) {
            if (beatSeconds <= 0) return describe();
            long beats = Math.round(played.length() / beatSeconds);
            return describe(fmt(Math.round(played.length() * 10) / 10.0) + " (" + beats + " temps)");
        }

        private String describe(String length) {
            StringBuilder b = new StringBuilder(switch (kind) {
                case PATH -> "trajet " + ref;
                case FOLLOW -> "suivi de " + ref;
                case TASK -> "lieu de la tâche";
                default -> "caméra " + ref;
            });
            b.append(", ").append(length);
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
    /**
     * Cuts on the music: tempo in beats per minute (0 = off, shots keep their own lengths). Every shot then lasts a
     * whole number of beats (its length rounded, or {@link #beats} when it has none) so each cut falls on a beat.
     */
    public double bpm;
    /** Default shot length in beats when cutting on the music (0 = about {@link #BEAT_SHOT_SECONDS}). */
    public int beats;
    /** Seconds from the start of the music to its first beat (the first shot is that much longer). */
    public double offset;
    /**
     * Played at the round's start ({@link #auto}): the round itself (teleports, title, timer) waits for the end of
     * the intro, then fade to black, teleports in the dark, the round's title, and the picture opens on the players
     * (false = the round starts at once and the intro plays over it).
     */
    public boolean chained = true;
    /** Effect over the whole intro, added to each shot's own ("look_intro"; "" = none). */
    public String look = "";
    /** The task typed at the bottom of the picture during the last shots, when it is revealed with the round. */
    public boolean typedTask = true;
    public final List<Shot> shots = new ArrayList<>();

    public boolean isEmpty() {
        return shots.isEmpty() && !auto && music.isEmpty() && titleCard && bpm == 0 && beats == 0 && offset == 0 && chained
                && look.isEmpty() && typedTask;
    }

    /** A shot's effect with the intro's look: "macro" + "look_intro" → "macro+look_intro". */
    public String effectOf(Shot s) {
        if (look.isEmpty()) return s.effect();
        if (s.effect().isEmpty()) return look;
        return s.effect() + "+" + look;
    }

    /** Length of a beat in seconds (0 when not cutting on the music). */
    public double beatSeconds() {
        return bpm > 0 ? 60 / bpm : 0;
    }

    /** Default shot length in beats when cutting on the music. */
    public int defaultBeats() {
        if (beats > 0) return beats;
        double b = beatSeconds();
        return b <= 0 ? 0 : (int) Math.max(1, Math.round(BEAT_SHOT_SECONDS / b));
    }

    /** "rythme 112 BPM, 6 temps par plan" or "" */
    public String rhythmText() {
        if (bpm <= 0) return "";
        return "rythme " + fmtNumber(bpm) + " BPM, " + defaultBeats() + " temps par plan (" + fmt(defaultBeats() * beatSeconds()) + ")"
                + (offset > 0 ? ", 1er temps à " + fmt(offset) : "");
    }

    /** "theme" → {@link #THEME_BPM}, "off" / "non" / "0" → 0, a number → that tempo (bounded); null if not understood. */
    public static Double parseBpm(String text) {
        if (text == null) return null;
        String k = text.trim().toLowerCase(Locale.ROOT);
        switch (k) {
            case "theme", "thème", "generique", "générique" -> { return THEME_BPM; }
            case "intro", "musique intro", "musique_intro" -> { return INTRO_BPM; }
            case "off", "non", "aucun", "0", "" -> { return 0.0; }
            default -> {
                try {
                    double v = Double.parseDouble(k.replace(',', '.').replace("bpm", "").trim());
                    if (!Double.isFinite(v) || v <= 0) return 0.0;
                    return Math.max(MIN_BPM, Math.min(MAX_BPM, v));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
    }

    public static String normalizeKind(String k) {
        if (k == null) return CAMERA;
        return switch (k.trim().toLowerCase(Locale.ROOT)) {
            case "trajet", "path", "travelling", "traveling" -> PATH;
            case "suivi", "follow", "suis", "joueur", "player" -> FOLLOW;
            case "tache", "tâche", "task", "lieu", "lieu de la tache", "lieu de la tâche", "lieu_tache", "enveloppe" -> TASK;
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
            case "intro", "musique intro", "musique_intro", "craftmaster:show.intro" -> INTRO_MUSIC;
            default -> k.contains(":") ? k : "minecraft:" + k;
        };
    }

    /**
     * Each shot's start and length: a travelling without a length lasts {@code pathSeconds} (0 or less: the
     * default), any other shot {@link #DEFAULT_SECONDS}.
     */
    public List<Timed> timeline(ToDoubleFunction<String> pathSeconds) {
        List<Timed> out = new ArrayList<>();
        double beat = beatSeconds();
        if (beat > 0) {
            // On the music: every cut on a beat (the first shot also covers the music's lead-in)
            long count = 0;
            double t = 0;
            for (int i = 0; i < shots.size(); i++) {
                Shot s = shots.get(i);
                double len = s.seconds();
                if (len <= 0 && s.kind().equals(PATH)) len = pathSeconds.applyAsDouble(s.ref());
                long n = len > 0 ? Math.max(1, Math.round(len / beat)) : defaultBeats();
                count += n;
                double end = offset + count * beat;
                out.add(new Timed(i, s, t, end - t));
                t = end;
            }
            return out;
        }
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

    /** Effects given in turn to the auto-built inserts (at least one focus pull), the look over everything. */
    static final List<String> AUTO_INSERT_EFFECTS = List.of("macro", "mise_au_point", "stop_motion", "macro");
    public static final String AUTO_LOOK = "look_intro";

    /**
     * The intro made from what the round already has (docs/design/intro-epreuve.md, "Mode automatique"), on the task
     * intro music: 40 beats at {@link #INTRO_BPM} ending on its sting.
     * <ol>
     *   <li>ambiance: the round's wide camera, timelapse, out of the studio with the "miroir" transition (8 beats,
     *       more when there are few inserts);</li>
     *   <li>up to 4 inserts (the round's other cameras), long and short in turn, macro / focus pull / stop motion;</li>
     *   <li>the master's look: the studio's throne camera, short and dark (4 beats), when there is one;</li>
     *   <li>the task location shot ({@link #TASK}), the longest, on a white flash; the title card comes on it and the
     *       task is typed at the bottom.</li>
     * </ol>
     * Plays chained (black, teleports, title) and by itself at the round's start. Pure: the caller gives the cameras.
     *
     * @param wide    reference of the round's wide camera, or null
     * @param inserts references of the round's other cameras, in order (only the first 4 are used)
     * @param throne  reference of the studio's throne camera, or null
     */
    public static MancheIntro autoBuild(String wide, List<String> inserts, String throne) {
        MancheIntro i = new MancheIntro();
        i.auto = true;
        i.chained = true;
        i.music = INTRO_MUSIC;
        i.bpm = INTRO_BPM;
        i.look = AUTO_LOOK;
        double beat = 60 / INTRO_BPM;
        List<String> ins = inserts == null ? List.of() : inserts.subList(0, Math.min(4, inserts.size()));
        int[] pattern = switch (ins.size()) {
            case 0 -> new int[0];
            case 1 -> new int[]{8};
            case 2 -> new int[]{8, 8};
            case 3 -> new int[]{6, 4, 6};
            default -> new int[]{6, 2, 6, 2};
        };
        int used = 0;
        for (int b : pattern) used += b;
        int ambiance = wide == null ? 0 : 8 + (16 - used);
        int throneBeats = throne == null ? 0 : 4;
        int task = 40 - ambiance - used - throneBeats;
        if (wide != null) i.shots.add(new Shot(CAMERA, wide, ambiance * beat, "miroir", "timelapse", NO_CARD, ""));
        for (int k = 0; k < ins.size(); k++)
            i.shots.add(new Shot(CAMERA, ins.get(k), pattern[k] * beat, k == 0 && wide == null ? "miroir" : "coupe",
                    AUTO_INSERT_EFFECTS.get(k), NO_CARD, ""));
        if (throne != null) i.shots.add(new Shot(CAMERA, throne, throneBeats * beat, "coupe", "vignette", NO_CARD, ""));
        i.shots.add(new Shot(TASK, "", task * beat, i.shots.isEmpty() ? "miroir" : "flash", "", NO_CARD, ""));
        return i;
    }

    public MancheIntro copy() {
        MancheIntro c = new MancheIntro();
        c.auto = auto;
        c.music = music;
        c.titleCard = titleCard;
        c.bpm = bpm;
        c.beats = beats;
        c.offset = offset;
        c.chained = chained;
        c.look = look;
        c.typedTask = typedTask;
        c.shots.addAll(shots);
        return c;
    }

    static String fmt(double s) {
        return fmtNumber(s) + " s";
    }

    public static String fmtNumber(double s) {
        return s == Math.rint(s) ? String.valueOf((long) s) : String.format(Locale.ROOT, "%.1f", s).replace('.', ',');
    }

    Map<String, Object> toYaml() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (auto) m.put("auto", true);
        if (!music.isEmpty()) m.put("music", music);
        if (!titleCard) m.put("titleCard", false);
        if (bpm > 0) m.put("bpm", bpm);
        if (beats > 0) m.put("beats", beats);
        if (offset > 0) m.put("offset", offset);
        if (!chained) m.put("chained", false);
        if (!look.isEmpty()) m.put("look", look);
        if (!typedTask) m.put("typedTask", false);
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
        double bpm = Maps.toDouble(m.get("bpm"));
        i.bpm = Double.isFinite(bpm) && bpm > 0 ? Math.max(MIN_BPM, Math.min(MAX_BPM, bpm)) : 0;
        double beats = Maps.toDouble(m.get("beats"));
        i.beats = Double.isFinite(beats) ? (int) Math.max(0, Math.min(64, Math.round(beats))) : 0;
        double offset = Maps.toDouble(m.get("offset"));
        i.offset = Double.isFinite(offset) ? Math.max(0, Math.min(30, offset)) : 0;
        i.chained = m.get("chained") == null || Maps.bool(m.get("chained"));
        i.look = Maps.str(m.get("look"), "").trim();
        i.typedTask = m.get("typedTask") == null || Maps.bool(m.get("typedTask"));
        for (Object s : Maps.list(m.get("shots"))) if (s instanceof Map<?, ?>) i.shots.add(Shot.fromYaml(Maps.map(s)));
        return i;
    }
}
