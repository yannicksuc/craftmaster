package fr.lordfinn.craftmaster.show;

import fr.lordfinn.craftmaster.CraftMaster;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Who did what: every list the executor runs for an {@link Actor} (done or refused), newest first, the last 200 kept in
 * memory and each one written to the server log. The organiser reads it in the director (Journal tab) and on the site.
 */
public final class ActionJournal {

    private ActionJournal() {}

    /** {@code at}: ms epoch; {@code lines}: the executor's French lines. */
    public record Line(long at, String actor, String origin, boolean ok, List<String> lines) {}

    private static final int MAX = 200;
    private static final Deque<Line> LINES = new ArrayDeque<>();
    private static long version;

    public static synchronized void add(Actor actor, boolean ok, List<String> lines) {
        Line l = new Line(System.currentTimeMillis(), actor.name(), actor.origin(), ok, List.copyOf(lines));
        LINES.addFirst(l);
        while (LINES.size() > MAX) LINES.removeLast();
        version++;
        CraftMaster.LOG.info("[Régie] {} ({}) {} : {}", actor.name(), actor.origin(), ok ? "fait" : "refusé", String.join(" ; ", lines));
    }

    /** The last {@code n} lines, newest first. */
    public static synchronized List<Line> last(int n) {
        List<Line> out = new ArrayList<>(Math.min(n, LINES.size()));
        for (Line l : LINES) {
            if (out.size() >= n) break;
            out.add(l);
        }
        return out;
    }

    /** Changes each time a line is added (to send the journal again only when it moved). */
    public static synchronized long version() {
        return version;
    }

    static synchronized void clear() {
        LINES.clear();
        version++;
    }
}
