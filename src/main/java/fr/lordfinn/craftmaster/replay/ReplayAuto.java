package fr.lordfinn.craftmaster.replay;

import fr.lordfinn.craftmaster.replay.ReplayNetwork.Marker;

import java.util.List;
import java.util.Locale;

/**
 * The automatic direction of a replay, recomputed from the recording's markers like the live one does on the
 * show's events: who scores is followed for 7 s, who is put in the spotlight (envelope, buzzer, projecteur) for 9 s,
 * the wide shot otherwise.
 */
public final class ReplayAuto {

    private ReplayAuto() {}

    /** How long the automatic direction stays on a player: points, and the spotlight moments (ms). */
    public static final long SCORE_MS = 7_000, SPOTLIGHT_MS = 9_000;

    /**
     * The player the automatic direction is on at {@code now} (ms): the last marker naming a contestant ("Ensuri +3",
     * "Ensuri ouvre son enveloppe", "Ensuri buzze", "Ensuri (projecteur)") still within its time; points taken away
     * do not count. Null: the wide shot.
     */
    public static String player(List<Marker> markers, long now, List<String> contestants) {
        String best = null;
        long bestAt = -1;
        for (Marker m : markers) {
            if (m.ms() > now || m.ms() < bestAt) continue;
            String who = null;
            for (String c : contestants)
                if ((m.name().equalsIgnoreCase(c) || m.name().regionMatches(true, 0, c + " ", 0, c.length() + 1))
                        && (who == null || c.length() > who.length())) who = c;
            if (who == null) continue;
            // Just the name: a spotlight in files recorded before its marker said "(projecteur)"
            String rest = m.name().length() == who.length() ? "" : m.name().substring(who.length() + 1).toLowerCase(Locale.ROOT);
            long window;
            if (rest.startsWith("+")) window = SCORE_MS;
            else if (rest.startsWith("-")) continue;
            else window = SPOTLIGHT_MS;
            if (now - m.ms() > window) continue;
            best = who;
            bestAt = m.ms();
        }
        return best;
    }
}
