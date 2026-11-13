package fr.lordfinn.craftmaster.trial;

import java.util.ArrayList;
import java.util.List;

/**
 * Who buzzed first: the first press wins and locks the buzzers out; the later presses are still recorded (second,
 * third, with their delay) until the judge resets. A player only counts once per round of buzzing. Pure: the clock
 * is passed in.
 */
public final class BuzzerLock {

    /** A press: order (1 = first), player, delay after the first press in ms. */
    public record Press(int order, String player, long delayMs) {
        public boolean first() {
            return order == 1;
        }
    }

    private final List<Press> presses = new ArrayList<>();
    private long firstAt;
    /** When the show was frozen (the delays stand still), -1 when it runs. */
    private long frozenAt = -1;

    /** Records a press; null if this player already pressed since the last reset (ignored). */
    public Press press(String player, long now) {
        for (Press p : presses) if (p.player().equalsIgnoreCase(player)) return null;
        if (frozenAt >= 0) now = frozenAt;
        if (presses.isEmpty()) firstAt = now;
        Press p = new Press(presses.size() + 1, player, presses.isEmpty() ? 0 : Math.max(0, now - firstAt));
        presses.add(p);
        return p;
    }

    /** Someone already buzzed: the others are locked out. */
    public boolean locked() {
        return !presses.isEmpty();
    }

    /** The winner, or null. */
    public String winner() {
        return presses.isEmpty() ? null : presses.getFirst().player();
    }

    public List<Press> presses() {
        return List.copyOf(presses);
    }

    public void reset() {
        presses.clear();
    }

    // ---------------------------------------------------------------- freeze after a crash (reprise)

    /** The delay since the first press stands still from {@code now}. */
    public void freeze(long now) {
        if (frozenAt < 0) frozenAt = now;
    }

    /** It counts again from {@code now}, from where it stood. */
    public void thaw(long now) {
        if (frozenAt < 0) return;
        firstAt = now - Math.max(0, frozenAt - firstAt);
        frozenAt = -1;
    }

    /** Time since the first press (ms; standing still while frozen), or -1 if nobody buzzed. */
    public long sinceFirst(long now) {
        return presses.isEmpty() ? -1 : Math.max(0, (frozenAt >= 0 ? frozenAt : now) - firstAt);
    }

    /** Puts back the presses saved before a restart, the first one {@code sinceFirst} ms ago. */
    public void restore(List<Press> saved, long sinceFirst, long now) {
        presses.clear();
        presses.addAll(saved);
        firstAt = (frozenAt >= 0 ? frozenAt : now) - Math.max(0, sinceFirst);
    }
}
