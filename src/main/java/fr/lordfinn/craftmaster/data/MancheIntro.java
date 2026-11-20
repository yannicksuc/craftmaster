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
    /**
     * The task intro music ({@code craftmaster:show.intro}, intro-epreuve-v2: note blocks, 5 bars at 120 BPM): tempo,
     * length in whole beats (20; the file rings 0.1 s longer), and when its final chord comes (the task location
     * shot); the dark hit just before it is on the last half-beat of bar 4 (the master's look, its last beat).
     */
    public static final double INTRO_BPM = 120, INTRO_MUSIC_SECONDS = 10, INTRO_CHORD_SECONDS = 8;
    /** Music cue of the task intro music. */
    public static final String INTRO_MUSIC = "intro";

    /**
     * One shot of the intro.
     *
     * @param ref        camera reference ("2", "large", "ensuri:1", "studio:3"), path name or player
     * @param seconds    length, 0 = default (a travelling's own length)
     * @param transition coupe | fondu | enchaine | miroir | flash | surimpression, "" = enchaine
     * @param effect     screen effect id ("noir_blanc", "sepia"…), "" = none
     * @param card       {@link #TITLE}, {@link #LOWER_THIRD} or ""
     * @param text       card text ("" = the round's name; "Titre | sous-titre" for both lines)
     * @param focus      focus pull of its depth of field ({@link fr.lordfinn.craftmaster.fx.FocusPull#canonical}), "" = the effect's preset
     */
    public record Shot(String kind, String ref, double seconds, String transition, String effect, String card, String text, String focus,
                       boolean fixed) {
        public Shot(String kind, String ref, double seconds, String transition, String effect, String card, String text) {
            this(kind, ref, seconds, transition, effect, card, text, "", false);
        }

        public Shot(String kind, String ref, double seconds, String transition, String effect, String card, String text, String focus) {
            this(kind, ref, seconds, transition, effect, card, text, focus, false);
        }

        public Shot {
            focus = focus == null ? "" : focus.trim();
            kind = normalizeKind(kind);
            ref = ref == null ? "" : ref.trim();
            seconds = Double.isFinite(seconds) ? Math.max(0, Math.min(MAX_SECONDS, seconds)) : 0;
            transition = transition == null ? "" : transition.trim().toLowerCase(Locale.ROOT);
            effect = effect == null ? "" : effect.trim();
            card = normalizeCard(card);
            text = text == null ? "" : text.trim();
        }

        public Shot withSeconds(double s) { return new Shot(kind, ref, s, transition, effect, card, text, focus, fixed); }
        public Shot withTransition(String t) { return new Shot(kind, ref, seconds, t, effect, card, text, focus, fixed); }
        public Shot withEffect(String e) { return new Shot(kind, ref, seconds, transition, e, card, text, focus, fixed); }
        public Shot withCard(String c, String t) { return new Shot(kind, ref, seconds, transition, effect, c, t, focus, fixed); }
        public Shot withFocus(String f) { return new Shot(kind, ref, seconds, transition, effect, card, text, f, fixed); }
        /** Fixed by hand ({@code plan N duree 3}): its length in seconds stays when the music changes. */
        public Shot withFixed(boolean f) { return new Shot(kind, ref, seconds, transition, effect, card, text, focus, f); }

        /** Its focus pull, or null (the effect's preset). */
        public fr.lordfinn.craftmaster.fx.FocusPull focusPull() {
            return fr.lordfinn.craftmaster.fx.FocusPull.parseCanonical(focus);
        }

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
            if (fixed) b.append(" (fixée)");
            if (!transition.isEmpty()) b.append(", ").append(transition);
            if (!effect.isEmpty()) b.append(", effet ").append(effect);
            fr.lordfinn.craftmaster.fx.FocusPull f = focusPull();
            if (f != null) b.append(", focus ").append(f.describe());
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
            if (!focus.isEmpty()) m.put("focus", focus);
            if (fixed) m.put("fixed", true);
            return m;
        }

        static Shot fromYaml(Map<String, Object> m) {
            return new Shot(Maps.str(m.get("kind"), CAMERA), Maps.str(m.get("ref"), ""), Maps.toDouble(m.get("seconds")),
                    Maps.str(m.get("transition"), ""), Maps.str(m.get("effect"), ""), Maps.str(m.get("card"), ""), Maps.str(m.get("text"), ""),
                    Maps.str(m.get("focus"), ""), Boolean.TRUE.equals(m.get("fixed")) || "true".equals(String.valueOf(m.get("fixed"))));
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

    /**
     * What goes to the games for a shot: its effects with the look, and its focus pull after a {@code |}
     * ({@link fr.lordfinn.craftmaster.fx.FocusPull#attach}).
     */
    public String effectWithFocus(Shot s, String effects) {
        fr.lordfinn.craftmaster.fx.FocusPull f = s.focusPull();
        return f == null || effects.isEmpty() ? effects : fr.lordfinn.craftmaster.fx.FocusPull.attach(effects, f);
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
            default -> {
                // The theme's moods and worlds (IntroMusic): « maladresse », « japonais », « theme.univers.grec »…
                IntroMusic.Track t = IntroMusic.find(k);
                if (t != null) yield t.key().equals(INTRO_MUSIC) ? INTRO_MUSIC : t.sound();
                yield k.contains(":") ? k : "minecraft:" + k;
            }
        };
    }

    /**
     * Each shot's start and length: a travelling without a length lasts {@code pathSeconds} (0 or less: the
     * default), any other shot {@link #DEFAULT_SECONDS}.
     */
    public List<Timed> timeline(ToDoubleFunction<String> pathSeconds) {
        List<Timed> out = new ArrayList<>();
        Grid grid = grid();
        if (grid != null) {
            // On the music: every cut on a beat (the first shot also covers the music's lead-in). A shot whose length
            // was fixed by hand keeps it (its cut may fall between two beats); the next cut is back on a beat.
            int count = 0;
            double t = 0;
            for (int i = 0; i < shots.size(); i++) {
                Shot s = shots.get(i);
                double len = s.seconds();
                if (len <= 0 && s.kind().equals(PATH)) len = pathSeconds.applyAsDouble(s.ref());
                double end;
                if (s.fixed() && len > 0) {
                    end = t + len;
                    count = Math.max(count, grid.near(end));
                } else {
                    if (grid.beat > 0) count += len > 0 ? (int) Math.max(1, Math.round(len / grid.beat)) : defaultBeats();
                    else count = Math.max(count + 1, grid.near(t + (len > 0 ? len : DEFAULT_SECONDS)));
                    end = grid.at(count);
                    while (end <= t + 1e-6) end = grid.at(++count);
                }
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

    /**
     * The beats the cuts fall on: a steady tempo ({@link #bpm}, first beat at {@link #offset}), or the beats of a
     * music of the theme whose tempo changes (the Greek one speeds up); null = shots at their own lengths.
     */
    Grid grid() {
        if (bpm > 0) return new Grid(60 / bpm, offset, null);
        IntroMusic.Track t = IntroMusic.ofCue(music);
        return t != null && t.bpm() <= 0 ? new Grid(0, 0, t.beatTimes()) : null;
    }

    /** Beat k at {@code offset + k × beat}, or the k-th of {@code times} (then the last gap repeated). */
    record Grid(double beat, double offset, double[] times) {
        double at(int k) {
            if (times == null) return offset + k * beat;
            if (k < times.length) return times[Math.max(0, k)];
            double gap = times.length > 1 ? times[times.length - 1] - times[times.length - 2] : 0.5;
            return times[times.length - 1] + (k - times.length + 1) * gap;
        }

        /** Index of the beat nearest to {@code time}. */
        int near(double time) {
            if (times == null) return (int) Math.max(0, Math.round((time - offset) / beat));
            int best = 0;
            for (int k = 1; at(k) <= time + 10; k++) if (Math.abs(at(k) - time) < Math.abs(at(best) - time)) best = k;
            return best;
        }
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
     * intro music (20 beats at {@link #INTRO_BPM}):
     * <ol>
     *   <li>bars 1 to 4, one motif per bar: the round's wide camera (timelapse, out of the studio with the "miroir"
     *       transition), then its other cameras (up to 4), macro / focus pull / stop motion; the first shot takes the
     *       bars nobody else needs, a 5th shot shares bar 4;</li>
     *   <li>the task location shot ({@link #TASK}) on the final chord, from {@link #INTRO_CHORD_SECONDS} to the end,
     *       in a dissolve ("fondu"), the task typed at the bottom (no title card: the round's name is typed on black at the
     *       launch that follows).</li>
     * </ol>
     * No studio camera (no throne): a trial's intro stays where its players arrive ({@code IntroZone}).
     * Plays chained (black, teleports, title) and by itself at the round's start. Pure: the caller gives the cameras.
     *
     * @param wide    reference of the round's wide camera, or null
     * @param inserts references of the round's other cameras, in order (only the first 4 are used)
     * This layout is the task intro v2's; without a music given, the intro is built on {@link IntroMusic#DEFAULT_AUTO}
     * (maladresse since 20/11, {@link #autoBuild(String, List, IntroMusic.Track)}).
     */
    public static MancheIntro autoBuild(String wide, List<String> inserts) {
        return autoBuild(wide, inserts, IntroMusic.find(IntroMusic.DEFAULT_AUTO));
    }

    /**
     * The same on one of the theme's musics ({@link IntroMusic}): the cuts on its bars (shots of {@link IntroMusic#MIN_SHOT}
     * or more, the first one takes what is left), the task location shot from the start of its « appel » to its end, cut on
     * its tempo (none for the Greek one, which speeds up: the shots keep their exact lengths). Null or the task intro v2:
     * {@link #autoBuild(String, List)}'s 20 beats.
     */
    public static MancheIntro autoBuild(String wide, List<String> inserts, IntroMusic.Track music) {
        if (music != null && !music.key().equals(INTRO_MUSIC)) return autoBuildOn(wide, inserts, music);
        MancheIntro i = new MancheIntro();
        i.auto = true;
        i.chained = true;
        i.music = INTRO_MUSIC;
        i.bpm = INTRO_BPM;
        i.look = AUTO_LOOK;
        // No card over the last shot (it would hide the task): the round's name is typed on black right after
        i.titleCard = false;
        double beat = 60 / INTRO_BPM;
        double chord = INTRO_CHORD_SECONDS / beat;       // 16 beats: the 4 bars before the final chord
        List<String> ins = inserts == null ? List.of() : inserts.subList(0, Math.min(4, inserts.size()));
        List<String> before = new java.util.ArrayList<>();
        if (wide != null) before.add(wide);
        before.addAll(ins);
        int n = before.size();
        double[] beats = new double[n];
        if (n == 5) beats = new double[]{4, 4, 4, 2, 2};
        else for (int k = 0; k < n; k++) beats[k] = k == 0 ? chord - 4 * (n - 1) : 4;
        for (int k = 0; k < n; k++) {
            boolean ambiance = k == 0 && wide != null;
            int insert = wide != null ? k - 1 : k;
            i.shots.add(new Shot(CAMERA, before.get(k), beats[k] * beat, k == 0 ? "miroir" : "coupe",
                    ambiance ? "timelapse" : AUTO_INSERT_EFFECTS.get(insert), NO_CARD, ""));
        }
        double task = i.shots.isEmpty() ? INTRO_MUSIC_SECONDS : INTRO_MUSIC_SECONDS - INTRO_CHORD_SECONDS;
        i.shots.add(new Shot(TASK, "", task, i.shots.isEmpty() ? "miroir" : "fondu", "", NO_CARD, ""));
        return i;
    }

    private static MancheIntro autoBuildOn(String wide, List<String> inserts, IntroMusic.Track t) {
        MancheIntro i = new MancheIntro();
        i.auto = true;
        i.chained = true;
        i.music = t.sound();
        i.bpm = t.bpm();
        i.look = AUTO_LOOK;
        i.titleCard = false;
        List<String> ins = inserts == null ? List.of() : inserts.subList(0, Math.min(4, inserts.size()));
        List<String> before = new java.util.ArrayList<>();
        if (wide != null) before.add(wide);
        before.addAll(ins);
        List<Double> starts = IntroMusic.cuts(t, before.size());
        for (int k = 0; k < starts.size(); k++) {
            double end = k + 1 < starts.size() ? starts.get(k + 1) : t.appel();
            boolean ambiance = k == 0 && wide != null;
            int insert = wide != null ? k - 1 : k;
            i.shots.add(new Shot(CAMERA, before.get(k), end - starts.get(k), k == 0 ? "miroir" : "coupe",
                    ambiance ? "timelapse" : AUTO_INSERT_EFFECTS.get(insert), NO_CARD, ""));
        }
        i.shots.add(new Shot(TASK, "", i.shots.isEmpty() ? t.seconds() : t.seconds() - t.appel(), i.shots.isEmpty() ? "miroir" : "fondu", "", NO_CARD, ""));
        return i;
    }

    /**
     * Fits the shots to the intro's music ({@link IntroMusic#ofCue}), as when the music changes: the last shot from the
     * start of the « appel » to the end of the music; the others share the time before it in their present proportions,
     * each cut on the nearest bar start (else beat) of the new music — the Greek one's beats speed up, the steampunk
     * waltz has 3 beats a bar, the medieval jig 6 quavers; a focus pull keeps its place in its shot, on the new beats.
     * A shot whose length was fixed by hand ({@link Shot#fixed}) keeps it: its cut, and the appel, may then fall off
     * the beat ({@link #offBeat}). The tempo becomes the music's. Returns what was done, or null when the music is
     * not one of the theme's (or there is no shot).
     */
    public String fitToMusic(ToDoubleFunction<String> pathSeconds) {
        IntroMusic.Track t = IntroMusic.ofCue(music);
        if (t == null || shots.isEmpty()) return null;
        bpm = t.bpm();
        offset = 0;
        double[] beats = t.beatTimes();
        double beat = beats.length > 1 ? beats[1] - beats[0] : 0.5;
        int n = shots.size();
        double[] lengths = new double[n];
        double free = 0, fixed = 0;
        for (int k = 0; k < n - 1; k++) {
            Shot s = shots.get(k);
            double len = s.seconds();
            if (len <= 0 && s.kind().equals(PATH)) len = pathSeconds.applyAsDouble(s.ref());
            if (len <= 0) len = DEFAULT_SECONDS;
            lengths[k] = len;
            if (s.fixed()) fixed += len;
            else free += len;
        }
        double room = Math.max(0, t.appel() - fixed);
        double t0 = 0, acc = 0, fixedSoFar = 0;
        for (int k = 0; k < n - 1; k++) {
            Shot s = shots.get(k);
            double end;
            if (s.fixed()) {
                end = t0 + lengths[k];
                fixedSoFar += lengths[k];
            } else {
                acc += lengths[k];
                double after = 0;
                boolean lastFree = true;
                for (int j = k + 1; j < n - 1; j++) {
                    if (shots.get(j).fixed()) after += lengths[j];
                    else lastFree = false;
                }
                // its place among the free shots, after the fixed ones before it
                double target = (free <= 0 ? room : acc / free * room) + fixedSoFar;
                end = lastFree ? t.appel() - after : snap(t, beats, Math.min(target, t.appel() - after));
                if (end < t0 + beat - 1e-6) end = nextBeat(beats, t0 + beat - 1e-6);
            }
            shots.set(k, retimed(s, t0, end, beats));
            t0 = end;
        }
        Shot last = shots.get(n - 1);
        double lastStart = n == 1 ? 0 : t0;
        if (!last.fixed()) shots.set(n - 1, retimed(last, lastStart, t.seconds(), beats));
        String off = offBeat(pathSeconds);
        boolean onAppel = n == 1 || Math.abs(lastStart - t.appel()) < 0.06;
        return n + " plan(s) calés sur « " + t.label() + " » (" + t.tempo() + " BPM, " + t.meter() + ") : le plan " + n
                + (onAppel ? (n == 1 ? " sur tout le morceau" : " sur l'appel, de " + fmt(lastStart) + " à " + fmt(t.seconds()))
                : " à " + fmt(lastStart) + " (l'appel est à " + fmt(t.appel()) + ")") + (off.isEmpty() ? "" : " ; ⚠ " + off);
    }

    /** The shot now from {@code start} to {@code end}; its focus pull moved with it, its times on the new beats. */
    private static Shot retimed(Shot s, double start, double end, double[] beats) {
        double old = s.seconds(), len = end - start;
        if (s.fixed()) return s;
        Shot out = s.withSeconds(len);
        fr.lordfinn.craftmaster.fx.FocusPull f = s.focusPull();
        if (f == null || old <= 0) return out;
        double ratio = len / old;
        double from = snapBeat(beats, start + f.delay() * ratio), to = snapBeat(beats, start + (f.delay() + f.duration()) * ratio);
        double delay = Math.max(0, Math.min(len, from - start)), dur = Math.max(0.05, Math.min(len - delay, to - from));
        return out.withFocus(new fr.lordfinn.craftmaster.fx.FocusPull(f.from(), f.to(), delay, dur, f.easing(), f.blur(), f.keep()).canonical());
    }

    /** Nearest bar start (or beat, when no bar is close) to {@code time}. */
    private static double snap(IntroMusic.Track t, double[] beats, double time) {
        double best = -1;
        for (double c : t.bars()) if (best < 0 || Math.abs(c - time) < Math.abs(best - time)) best = c;
        double beat = beats.length > 1 ? beats[1] - beats[0] : 0.5;
        return best >= 0 && Math.abs(best - time) <= Math.max(0.6, beat * 1.5) ? best : snapBeat(beats, time);
    }

    private static double snapBeat(double[] beats, double time) {
        double best = time, d = Double.MAX_VALUE;
        for (double b : beats) if (Math.abs(b - time) < d) { d = Math.abs(b - time); best = b; }
        return best;
    }

    private static double nextBeat(double[] beats, double time) {
        for (double b : beats) if (b >= time) return b;
        return time;
    }

    /**
     * The cuts that do not fall on a beat of the music (a shot whose length was fixed by hand), as told in the editor:
     * "plan 2 fixé à 3 s : coupe 0,2 s après le temps"; "" when all are on the beat (or no music of the theme).
     */
    public String offBeat(ToDoubleFunction<String> pathSeconds) {
        IntroMusic.Track t = IntroMusic.ofCue(music);
        if (t == null) return "";
        double[] beats = t.beatTimes();
        List<String> out = new ArrayList<>();
        for (Timed x : timeline(pathSeconds)) {
            if (!x.shot().fixed() || x.index() >= shots.size() - 1) continue;
            double cut = x.start() + x.length(), near = snapBeat(beats, cut);
            if (Math.abs(cut - near) > 0.04)
                out.add("plan " + (x.index() + 1) + " fixé à " + fmt(x.shot().seconds()) + " : coupe " + fmt(Math.abs(cut - near))
                        + (cut > near ? " après" : " avant") + " le temps");
        }
        return String.join(", ", out);
    }

    /** "plan final sur l'appel" / "plan final 1,2 s avant l'appel"… for the strip of the editor; "" when the music is not one of the theme's. */
    public String musicFit(ToDoubleFunction<String> pathSeconds) {
        IntroMusic.Track t = IntroMusic.ofCue(music);
        List<Timed> tl = timeline(pathSeconds);
        if (t == null || tl.isEmpty()) return "";
        double last = tl.getLast().start(), total = last + tl.getLast().length();
        double cue = t.key().equals(INTRO_MUSIC) ? t.chord() : t.appel();
        String word = t.key().equals(INTRO_MUSIC) ? "l'accord" : "l'appel";
        String start = tl.size() == 1 ? "" : Math.abs(last - cue) < 0.06 ? "plan final sur " + word
                : "plan final " + fmt(Math.abs(last - cue)) + (last < cue ? " avant " : " après ") + word;
        String end = Math.abs(total - t.seconds()) < 0.3 ? "" : total < t.seconds() ? ", coupe la musique " + fmt(t.seconds() - total) + " avant sa fin"
                : ", " + fmt(total - t.seconds()) + " après la musique";
        String off = offBeat(pathSeconds);
        return (start.isEmpty() ? "dure " + fmt(total) : start) + end + (off.isEmpty() ? "" : " · ⚠ " + off);
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
