package fr.lordfinn.craftmaster.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure model of the referee's score entry for one round (no Minecraft dependency): a draft, sent only when the
 * referee validates it.
 *
 * <p>Rule (validated with LordFinn): every place is a <b>step</b> with a base score painted on it, every player
 * placed on a step has his own <b>token</b> (bonus / penalty) that follows him. Score = base of his step + his token.
 * Moving a player gives him the base of his new step; +1 / -1 only changes his token. Example: steps 5, 3, 1 for A,
 * B, C; +1 on A (6); swap A and C → C 5, B 3, A 1 + 1 = 2.
 *
 * <p>Players not ranked yet wait on the <b>bench</b> (a round never scored starts with everyone there, its steps
 * taking the scale's points): a click sends a player to the next free step, in the order they finished. A player
 * left on the bench keeps his score (nothing is sent for him). Every gesture can be undone / redone locally.
 */
public final class ScoreBoard {

    public static final int MIN = -10;
    public static final int MAX = 10;

    public record Row(String player, int score) {}

    /** Base score of each step (as many steps as players). */
    private List<Integer> steps = new ArrayList<>();
    /** Players on the steps, step 0 first. */
    private List<String> placed = new ArrayList<>();
    private List<String> bench = new ArrayList<>();
    private Map<String, Integer> tokens = new HashMap<>();
    /** Score of each player when the board was built (absent: no score yet). */
    private final Map<String, Integer> initial = new LinkedHashMap<>();

    private record Snapshot(List<Integer> steps, List<String> placed, List<String> bench, Map<String, Integer> tokens) {}

    private final Deque<Snapshot> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

    /** Rows in their order, all placed; each row's score becomes the base of its step (tokens start at 0). */
    public ScoreBoard(List<Row> rows) {
        for (Row r : rows) {
            // A score already outside [-10, 10] (given by a command) is kept as is: the screen must not rewrite it
            placed.add(r.player());
            steps.add(r.score());
            initial.put(r.player(), r.score());
        }
    }

    private ScoreBoard() {}

    /**
     * The board of a round. Players with a score stand on the steps, best first (ties in the given order), each step
     * keeping that score as its base; the others wait on the bench, and the free steps take the scale's points
     * (place n = scale[n-1], 0 past its end). A round never scored: everyone on the bench, steps = the scale.
     *
     * @param players the season's players, in its order
     * @param scores current score of each player (absent = no score)
     */
    public static ScoreBoard of(List<String> players, Map<String, Integer> scores, int[] scale) {
        ScoreBoard b = new ScoreBoard();
        List<String> scored = new ArrayList<>(players.stream().filter(scores::containsKey).toList());
        scored.sort((x, y) -> Integer.compare(scores.get(y), scores.get(x)));
        for (int i = 0; i < players.size(); i++)
            b.steps.add(i < scored.size() ? scores.get(scored.get(i)) : i < scale.length ? scale[i] : 0);
        b.placed.addAll(scored);
        for (String p : players) if (!scores.containsKey(p)) b.bench.add(p);
        for (String p : scored) b.initial.put(p, scores.get(p));
        return b;
    }

    public static int clamp(int v) {
        return Math.max(MIN, Math.min(MAX, v));
    }

    // ---------------------------------------------------------------- reading

    /** Players on the steps. */
    public int size() {
        return placed.size();
    }

    /** Number of steps (= players of the board). */
    public int steps() {
        return steps.size();
    }

    public int stepBase(int step) {
        return steps.get(step);
    }

    public String player(int index) {
        check(index);
        return placed.get(index);
    }

    public int score(int index) {
        check(index);
        return steps.get(index) + tokens.getOrDefault(placed.get(index), 0);
    }

    /** The player's own bonus / penalty (follows him from step to step). */
    public int modifier(String player) {
        return tokens.getOrDefault(player, 0);
    }

    public int indexOf(String player) {
        return placed.indexOf(player);
    }

    public List<String> bench() {
        return List.copyOf(bench);
    }

    public boolean onBench(String player) {
        return bench.contains(player);
    }

    /** The player's score before the draft (null: none). */
    public Integer initialScore(String player) {
        return initial.get(player);
    }

    public List<Row> rows() {
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < placed.size(); i++) out.add(new Row(placed.get(i), score(i)));
        return out;
    }

    // ---------------------------------------------------------------- gestures (each one can be undone)

    /** The two players trade steps (each takes the base of his new step, keeps his token). */
    public void swap(int a, int b) {
        check(a);
        check(b);
        if (a == b) return;
        record();
        String p = placed.get(a);
        placed.set(a, placed.get(b));
        placed.set(b, p);
    }

    /** The player goes to the top step, the ones above shift down by one. */
    public void toFront(int index) {
        moveTo(index, 0);
    }

    /** The player leaves step {@code from} and is inserted at {@code to}; the ones in between shift by one. */
    public void moveTo(int from, int to) {
        check(from);
        check(to);
        if (from == to) return;
        record();
        placed.add(to, placed.remove(from));
    }

    /**
     * +1 / -1 on one player only (his token); returns the new score. It never leaves [-10, 10] (a score already
     * outside only moves back towards it).
     */
    public int bump(int index, int delta) {
        int now = score(index);
        int next = Math.max(Math.min(MIN, now), Math.min(Math.max(MAX, now), now + delta));
        if (next == now) return now;
        record();
        String p = placed.get(index);
        tokens.put(p, tokens.getOrDefault(p, 0) + (next - now));
        return next;
    }

    /**
     * +1 / -1 for a player wherever he is (the board view): one on the bench first goes to the next free step (one
     * gesture). Returns the new score, or null if he cannot be placed.
     */
    public Integer bumpPlayer(String player, int delta) {
        if (bench.contains(player)) {
            if (placed.size() >= steps.size()) return null;
            record();
            bench.remove(player);
            placed.add(player);
            int i = placed.size() - 1, now = score(i);
            int next = Math.max(Math.min(MIN, now), Math.min(Math.max(MAX, now), now + delta));
            tokens.put(player, tokens.getOrDefault(player, 0) + (next - now));
            return next;
        }
        int i = placed.indexOf(player);
        return i < 0 ? null : bump(i, delta);
    }

    /** A player being moved gets +1 / -1 on another step: moved there first (the others shift), then the token. */
    public int moveAndBump(int from, int to, int delta) {
        moveTo(from, to);
        return bump(to, delta);
    }

    /** From the bench to the next free step ("finished next"); false if not on the bench. */
    public boolean fromBench(String player) {
        return fromBench(player, placed.size());
    }

    /** From the bench to step {@code at} (the others shift down); false if not on the bench. */
    public boolean fromBench(String player, int at) {
        if (!bench.contains(player) || placed.size() >= steps.size()) return false;
        record();
        bench.remove(player);
        placed.add(Math.max(0, Math.min(placed.size(), at)), player);
        return true;
    }

    /** Back to the bench (his score will not be touched); his token stays with him. */
    public void toBench(int index) {
        check(index);
        record();
        bench.add(placed.remove(index));
    }

    /** "The rest tied last": every player on the bench goes to the step after the last placed one, same base. */
    public void benchTiedLast() {
        if (bench.isEmpty()) return;
        record();
        int last = placed.size();
        int base = last < steps.size() ? steps.get(last) : 0;
        for (String p : new ArrayList<>(bench)) {
            placed.add(p);
            steps.set(placed.size() - 1, base);
        }
        bench.clear();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /** Undoes the last gesture; false if there was none. */
    public boolean undo() {
        if (undo.isEmpty()) return false;
        redo.push(snapshot());
        restore(undo.pop());
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        undo.push(snapshot());
        restore(redo.pop());
        return true;
    }

    private Snapshot snapshot() {
        return new Snapshot(new ArrayList<>(steps), new ArrayList<>(placed), new ArrayList<>(bench), new HashMap<>(tokens));
    }

    private void restore(Snapshot s) {
        steps = new ArrayList<>(s.steps());
        placed = new ArrayList<>(s.placed());
        bench = new ArrayList<>(s.bench());
        tokens = new HashMap<>(s.tokens());
    }

    /** Before every gesture: what to go back to; a new gesture empties the redo. */
    private void record() {
        undo.push(snapshot());
        if (undo.size() > 100) undo.removeLast();
        redo.clear();
    }

    // ---------------------------------------------------------------- sending

    /** What to send: every placed player whose score differs from before (bench: untouched), in step order. */
    public Map<String, Integer> changes() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < placed.size(); i++) {
            String p = placed.get(i);
            int s = score(i);
            Integer before = initial.get(p);
            if (before == null || s != before) out.put(p, s);
        }
        return out;
    }

    /** Has a score changed (something to validate)? */
    public boolean dirty() {
        return !changes().isEmpty();
    }

    private void check(int i) {
        if (i < 0 || i >= placed.size()) throw new IndexOutOfBoundsException("row " + i);
    }
}
