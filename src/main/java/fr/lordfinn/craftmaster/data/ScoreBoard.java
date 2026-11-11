package fr.lordfinn.craftmaster.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure model of the referee's score entry screen (no Minecraft dependency).
 * Rows are ordered; the scores are "slots" attached to positions: moving a player takes the score of
 * its new position (ranking), while +1/-1 only changes that player. Scores stay in [-10, 10].
 */
public final class ScoreBoard {

    public static final int MIN = -10;
    public static final int MAX = 10;

    public record Row(String player, int score) {}

    private final List<String> players;
    private final List<Integer> slots; // slots.get(i) = score of the row at position i

    public ScoreBoard(List<Row> rows) {
        players = new ArrayList<>();
        slots = new ArrayList<>();
        for (Row r : rows) {
            players.add(r.player());
            slots.add(clamp(r.score()));
        }
    }

    public static int clamp(int v) {
        return Math.max(MIN, Math.min(MAX, v));
    }

    public List<Row> rows() {
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) out.add(new Row(players.get(i), slots.get(i)));
        return out;
    }

    /** Click/click: the two players trade places (and so their score slots). */
    public void swap(int a, int b) {
        check(a);
        check(b);
        if (a == b) return;
        String p = players.get(a);
        players.set(a, players.get(b));
        players.set(b, p);
        applyRanking();
    }

    /** Shift-click: the player goes to the top, the ones above shift down by one. */
    public void toFront(int index) {
        check(index);
        players.add(0, players.remove(index));
        applyRanking();
    }

    /** +1 / -1 on one player only, clamped; returns the new score. Other players are untouched. */
    public int bump(int index, int delta) {
        check(index);
        int v = clamp(slots.get(index) + delta);
        slots.set(index, v);
        return v;
    }

    // Ranking = the scores sorted high to low are handed out in the order of the rows.
    // Called when the order changes; bump() can then break the sort on purpose (a bonus or a penalty).
    private void applyRanking() {
        // slots stay attached to positions already: nothing more to do, kept as the single place to change the rule.
    }

    private void check(int i) {
        if (i < 0 || i >= players.size()) throw new IndexOutOfBoundsException("row " + i);
    }
}
