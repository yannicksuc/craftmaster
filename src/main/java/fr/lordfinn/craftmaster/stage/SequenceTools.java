package fr.lordfinn.craftmaster.stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * Editing tools on timelines, without the game: used by the in-game editor (client) and the commands (server).
 * Mirrors swap the stage's sides (names and angles), reversing plays a timeline backwards, shifting and stretching
 * move keypoints in time.
 */
public final class SequenceTools {

    private SequenceTools() {}

    /** Snap step of the editor (seconds). */
    public static final double SNAP = 0.25;

    public static double snap(double t) {
        return Math.round(t / SNAP) * SNAP;
    }

    /** Pairs of words for the two sides of a stage (theatre: cour = right seen from the audience, jardin = left). */
    private static final String[][] SIDES = {{"gauche", "droite"}, {"jardin", "cour"}, {"left", "right"}, {"g", "d"}, {"l", "r"}};

    /**
     * The other side's name: "face gauche" → "face droite", "contre-jardin" → "contre-cour", "faceg" → "faced",
     * "spot_l" → "spot_r". A name with no side is kept.
     */
    public static String mirrorName(String name) {
        if (name == null || name.isEmpty()) return name;
        String lower = name.toLowerCase(Locale.ROOT);
        for (String[] pair : SIDES) {
            for (int i = 0; i < 2; i++) {
                String a = pair[i], b = pair[1 - i];
                // Whole words first (or the end of the name for one-letter sides)
                int at = indexOfWord(lower, a, a.length() == 1);
                if (at >= 0) return name.substring(0, at) + b + name.substring(at + a.length());
            }
        }
        return name;
    }

    private static int indexOfWord(String s, String w, boolean endOnly) {
        if (endOnly) {
            if (s.length() > w.length() && s.endsWith(w)) {
                char before = s.charAt(s.length() - w.length() - 1);
                // "faceg", "face_g", "face-g", "face g": a one-letter side only at the very end
                if (Character.isLetter(before) || before == '_' || before == '-' || before == ' ') return s.length() - w.length();
            }
            return -1;
        }
        int from = 0;
        while (true) {
            int i = s.indexOf(w, from);
            if (i < 0) return -1;
            boolean startOk = i == 0 || !Character.isLetter(s.charAt(i - 1)) || true;
            boolean endOk = i + w.length() == s.length() || !Character.isLetter(s.charAt(i + w.length())) || Character.isDigit(s.charAt(i + w.length()));
            if (startOk && endOk) return i;
            from = i + 1;
        }
    }

    /** A yaw seen in the mirror whose axis is the audience's gaze {@code front}. */
    public static float mirrorYaw(float yaw, float front) {
        float y = 2 * front - yaw;
        while (y <= -180) y += 360;
        while (y > 180) y -= 360;
        return y;
    }

    /**
     * The track for the other side: its target and named points swapped, fixed angles mirrored around the
     * audience's gaze, coordinates through {@code mirrorPlace} (null: kept).
     */
    public static Sequence.Track mirror(Sequence.Track tr, float front, UnaryOperator<String> mirrorPlace) {
        Sequence.Track m = new Sequence.Track(tr.type, mirrorName(tr.cible));
        m.muette = tr.muette;
        for (Sequence.Key k : tr.cles) {
            Sequence.Key c = k.copy();
            if (c.horizontal != null) c.horizontal = mirrorYaw(c.horizontal, front);
            if (c.vise != null) {
                if (c.vise.startsWith("@point:")) c.vise = "@point:" + mirrorName(c.vise.substring(7));
                else if (!c.vise.startsWith("@") && mirrorPlace != null) c.vise = mirrorPlace.apply(c.vise);
            }
            if (c.suit != null && !c.suit.startsWith("@")) c.suit = c.suit; // a player stays a player
            m.cles.add(c);
        }
        return m;
    }

    /**
     * Plays the timeline backwards: each change happens as far from the end as it was from the start (its
     * transition then ends where it used to start).
     */
    public static void reverse(Sequence s) {
        for (Sequence.Track tr : s.pistes) reverse(tr, s.duree);
    }

    public static void reverse(Sequence.Track tr, double duree) {
        for (Sequence.Key k : tr.cles) k.t = clamp(duree - k.t - k.fondu, 0, duree);
        tr.sort();
    }

    /** Moves keys by {@code dt} seconds, kept inside the timeline. */
    public static void shift(List<Sequence.Key> keys, double dt, double duree) {
        for (Sequence.Key k : keys) k.t = clamp(k.t + dt, 0, duree);
    }

    /** Stretches (factor > 1) or squeezes the whole timeline, transitions included. */
    public static void stretch(Sequence s, double factor) {
        if (!(factor > 0)) return;
        s.duree = Math.min(3600, s.duree * factor);
        for (Sequence.Track tr : s.pistes)
            for (Sequence.Key k : tr.cles) {
                k.t *= factor;
                k.fondu *= factor;
            }
    }

    /** Copies of keys, moved so the first one is at {@code at} (paste at the playhead). */
    public static List<Sequence.Key> pasteAt(List<Sequence.Key> copied, double at, double duree) {
        List<Sequence.Key> out = new ArrayList<>();
        if (copied.isEmpty()) return out;
        double first = copied.stream().mapToDouble(k -> k.t).min().orElse(0);
        for (Sequence.Key k : copied) {
            Sequence.Key c = k.copy();
            c.t = clamp(at + (k.t - first), 0, duree);
            out.add(c);
        }
        return out;
    }

    public static Sequence.Track copy(Sequence.Track tr) {
        Sequence.Track c = new Sequence.Track(tr.type, tr.cible);
        c.muette = tr.muette;
        for (Sequence.Key k : tr.cles) c.cles.add(k.copy());
        return c;
    }

    /** A whole timeline, deep (through its JSON form, so nothing is shared). */
    public static Sequence copy(Sequence s) {
        Sequence c = new Sequence();
        c.duree = s.duree;
        c.boucle = s.boucle;
        c.description = s.description;
        for (Sequence.Track tr : s.pistes) c.pistes.add(copy(tr));
        return c;
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
