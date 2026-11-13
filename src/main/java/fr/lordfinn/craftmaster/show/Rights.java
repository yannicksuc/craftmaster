package fr.lordfinn.craftmaster.show;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which right each show action needs (docs/design/regie-moderateurs.md §2). The executor checks it for every list,
 * whoever sends it (voice, Stream Deck, moderators' director in game or on the site), so a moderator who only drives
 * cameras cannot give points by any route.
 * <ul>
 * <li>{@link Right#CAMERA} — camera.*, broadcast.*, show.intro/outro/stop, spotlights, screen effects on the broadcast
 * (permission {@code craftmaster:camera})</li>
 * <li>{@link Right#TV} — tele.show (the studio TVs: clip, link, a contestant's live, image, off; {@code craftmaster:tv} or
 * {@code craftmaster:image})</li>
 * <li>{@link Right#REPLAY} — replay.* ({@code craftmaster:replay})</li>
 * <li>{@link Right#GAME} — live points, round start / end, timer, stopwatches, buzzers, results, timer tablets
 * ({@code craftmaster:jeu})</li>
 * <li>{@link Right#ADMIN} — everything else: publishing, the reveal, undo, seasons, players, rounds' setup...</li>
 * </ul>
 * {@code say} needs nothing (a line of text). {@code craftmaster:modo} gives CAMERA, TV and REPLAY.
 */
public final class Rights {

    private Rights() {}

    public enum Right {
        ADMIN("admin"), GAME("jeu"), CAMERA("camera"), TV("tv"), REPLAY("replay");

        /** Name in commands, files and permissions ({@code craftmaster:<id>}). */
        public final String id;

        Right(String id) {
            this.id = id;
        }

        public static Right byId(String id) {
            String s = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
            for (Right r : values()) if (r.id.equals(s)) return r;
            if (s.equals("game") || s.equals("points") || s.equals("manches")) return GAME;
            if (s.equals("cameras") || s.equals("camera")) return CAMERA;
            if (s.equals("teles") || s.equals("télés") || s.equals("image")) return TV;
            if (s.equals("replays")) return REPLAY;
            return null;
        }
    }

    /** The default moderator: cameras, TVs and replays (points and rounds stay with the organiser). */
    public static final Set<Right> MODO = EnumSet.of(Right.CAMERA, Right.TV, Right.REPLAY);

    private static final List<String> GAME_TYPES = List.of("score.add", "score.set", "score.remove", "manche.start", "manche.end",
            "timer.stop", "stopwatch.start", "stopwatch.stop", "stopwatch.reset", "buzzer.reset", "result.set", "result.dq",
            "result.remove", "tablet.start", "tablet.pause", "tablet.toggle", "tablet.reset", "tablet.restart");

    /** The right an action needs; null = none ({@code say}). */
    public static Right required(String type, Map<String, Object> action) {
        String t = type == null ? "?" : type.trim().toLowerCase(Locale.ROOT);
        if (t.equals("say")) return null;
        if (t.startsWith("camera.") || t.startsWith("broadcast.") || t.equals("show.intro") || t.equals("show.outro")
                || t.equals("show.stop") || t.startsWith("spotlight.")) return Right.CAMERA;
        if (t.equals("fx.set") || t.equals("fx.clear")) {
            // On the broadcast only (the default target of fx.set); forcing an effect on players stays with the organiser
            Object target = action == null ? null : action.get("target");
            String s = target == null ? (t.equals("fx.set") ? "diffusion" : "tout") : String.valueOf(target).trim().toLowerCase(Locale.ROOT);
            return s.equals("diffusion") || s.equals("broadcast") ? Right.CAMERA : Right.ADMIN;
        }
        if (t.startsWith("tele.") || t.startsWith("tv.")) return Right.TV;
        if (t.startsWith("replay.")) return Right.REPLAY;
        if (GAME_TYPES.contains(t)) return Right.GAME;
        return Right.ADMIN;
    }

    /** Null when {@code rights} allow every action of the list, else the French refusal for the first one refused. */
    public static String refusal(Set<Right> rights, List<Map<String, Object>> actions) {
        if (rights.contains(Right.ADMIN) || actions == null) return null;
        for (Map<String, Object> a : actions) {
            String type = Actions.type(a);
            Right need = required(type, a);
            if (need != null && !rights.contains(need))
                return "✗ « " + type + " » demande le droit « " + need.id + " ».";
        }
        return null;
    }
}
