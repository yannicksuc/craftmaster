package fr.lordfinn.craftmaster.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure model of the referee's score entry screen for one round (no Minecraft dependency).
 *
 * <p>Rule (validated with LordFinn): every place has a base score, and every player has his own bonus / penalty.
 * A player's score = base of his place + his own modifier, kept in [-10, 10]. Moving a player gives him the base of
 * his new place and the others take the base of theirs; +1 / -1 only changes the modifier of that one player, which
 * follows him when he moves. Example: places 5, 3, 1 for A, B, C; +1 on A (6); swap A and C → C 5, B 3, A 1 + 1 = 2.
 */
public final class ScoreBoard {

    public static final int MIN = -10;
    public static final int MAX = 10;

    public record Row(String player, int score) {}

    private final List<String> players = new ArrayList<>();
    private final List<Integer> base = new ArrayList<>();
    private final Map<String, Integer> modifiers = new HashMap<>();
    private final Map<String, Integer> initial = new LinkedHashMap<>();

    /** Rows in their order; each row's score becomes the base of its place (modifiers start at 0). */
    public ScoreBoard(List<Row> rows) {
        for (Row r : rows) {
            int s = clamp(r.score());
            players.add(r.player());
            base.add(s);
            initial.put(r.player(), s);
        }
    }

    /**
     * The board of a round: players sorted by their current score (high to low, ties in the given order). When nobody
     * has a score yet, the places take the points of {@code scale} (place 1 = scale[0]…, 0 past its end), so ordering
     * the players is enough to give the points.
     *
     * @param scores current score of each player (absent = no score), in the season's order of players
     */
    public static ScoreBoard of(List<String> players, Map<String, Integer> scores, int[] scale) {
        List<String> order = new ArrayList<>(players);
        boolean none = players.stream().noneMatch(scores::containsKey);
        if (none) {
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < order.size(); i++) rows.add(new Row(order.get(i), i < scale.length ? scale[i] : 0));
            ScoreBoard b = new ScoreBoard(rows);
            // Nothing was given yet: "initial" is 0 for everyone, so the scale counts as a change to send
            for (String p : order) b.initial.put(p, 0);
            return b;
        }
        order.sort((a, b) -> Integer.compare(scores.getOrDefault(b, 0), scores.getOrDefault(a, 0)));
        List<Row> rows = new ArrayList<>();
        for (String p : order) rows.add(new Row(p, scores.getOrDefault(p, 0)));
        return new ScoreBoard(rows);
    }

    public static int clamp(int v) {
        return Math.max(MIN, Math.min(MAX, v));
    }

    public int size() {
        return players.size();
    }

    public String player(int index) {
        check(index);
        return players.get(index);
    }

    public int score(int index) {
        check(index);
        return clamp(base.get(index) + modifiers.getOrDefault(players.get(index), 0));
    }

    public int modifier(String player) {
        return modifiers.getOrDefault(player, 0);
    }

    public int indexOf(String player) {
        return players.indexOf(player);
    }

    public List<Row> rows() {
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) out.add(new Row(players.get(i), score(i)));
        return out;
    }

    /** Click/click: the two players trade places (each takes the base of his new place, keeps his modifier). */
    public void swap(int a, int b) {
        check(a);
        check(b);
        if (a == b) return;
        String p = players.get(a);
        players.set(a, players.get(b));
        players.set(b, p);
    }

    /** Shift-click: the player goes to the top, the ones above shift down by one. */
    public void toFront(int index) {
        moveTo(index, 0);
    }

    /** The player leaves {@code from} and is inserted at {@code to}; the ones in between shift by one. */
    public void moveTo(int from, int to) {
        check(from);
        check(to);
        players.add(to, players.remove(from));
    }

    /** +1 / -1 on one player only (his modifier), the score kept in [-10, 10]; returns the new score. */
    public int bump(int index, int delta) {
        int now = score(index);
        int next = clamp(now + delta);
        String p = players.get(index);
        modifiers.put(p, modifiers.getOrDefault(p, 0) + (next - now));
        return next;
    }

    /**
     * A player being moved (picked up) gets a +1 / -1 on another place: the move is applied first (he is inserted
     * there, the others shift), then the modifier. Returns the new score.
     */
    public int moveAndBump(int from, int to, int delta) {
        moveTo(from, to);
        return bump(to, delta);
    }

    /** What changed since the board was built: player → new score, in the order of the rows. */
    public Map<String, Integer> changes() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < players.size(); i++) {
            String p = players.get(i);
            int s = score(i);
            if (s != initial.getOrDefault(p, 0)) out.put(p, s);
        }
        return out;
    }

    /** Has a score changed (something to send with the update button)? */
    public boolean dirty() {
        return !changes().isEmpty();
    }

    private void check(int i) {
        if (i < 0 || i >= players.size()) throw new IndexOutOfBoundsException("row " + i);
    }
}
