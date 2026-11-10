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

    /** Records a press; null if this player already pressed since the last reset (ignored). */
    public Press press(String player, long now) {
        for (Press p : presses) if (p.player().equalsIgnoreCase(player)) return null;
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
}
