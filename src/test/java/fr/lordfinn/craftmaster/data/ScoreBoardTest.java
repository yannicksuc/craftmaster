package fr.lordfinn.craftmaster.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScoreBoardTest {

    private static ScoreBoard.Row r(String p, int s) {
        return new ScoreBoard.Row(p, s);
    }

    private ScoreBoard board() {
        return new ScoreBoard(List.of(r("A", 5), r("B", 3), r("C", 1)));
    }

    @Test
    void swapTradesPlacesAndTakesThePlaceScore() {
        ScoreBoard b = board();
        b.swap(0, 2);
        assertEquals(List.of(r("C", 5), r("B", 3), r("A", 1)), b.rows());
    }

    @Test
    void toFrontShiftsTheOthersDown() {
        ScoreBoard b = board();
        b.toFront(2);
        assertEquals(List.of(r("C", 5), r("A", 3), r("B", 1)), b.rows());
    }

    @Test
    void bumpOnlyChangesOnePlayerAndIsClamped() {
        ScoreBoard b = board();
        b.toFront(2);
        assertEquals(6, b.bump(0, 1));
        assertEquals(List.of(r("C", 6), r("A", 3), r("B", 1)), b.rows());
        for (int i = 0; i < 20; i++) b.bump(1, 1);
        assertEquals(10, b.score(1));
        for (int i = 0; i < 40; i++) b.bump(1, -1);
        assertEquals(-10, b.score(1));
        // Clamping does not pile up a hidden modifier: one +1 from -10 gives -9
        assertEquals(-9, b.bump(1, 1));
    }

    /** The rule validated with LordFinn: the bonus follows the player. */
    @Test
    void bonusFollowsThePlayerWhenMoved() {
        ScoreBoard b = board();
        b.bump(0, 1); // A 6
        b.swap(0, 2);
        assertEquals(List.of(r("C", 5), r("B", 3), r("A", 2)), b.rows());
        b.toFront(2);
        assertEquals(List.of(r("A", 6), r("C", 3), r("B", 1)), b.rows());
    }

    @Test
    void movedThenBumpedAppliesTheRankingFirst() {
        ScoreBoard b = board();
        // C picked up, then +1 on the first place: C goes first, A and B shift, then C gets +1
        assertEquals(6, b.moveAndBump(2, 0, 1));
        assertEquals(List.of(r("C", 6), r("A", 3), r("B", 1)), b.rows());
    }

    @Test
    void outOfRangeScoresAreKeptAndOnlyGoBackTowardsTheRange() {
        ScoreBoard b = new ScoreBoard(List.of(r("A", 65), r("B", -20)));
        assertEquals(65, b.score(0), "a score given by a command is not rewritten");
        assertFalse(b.dirty());
        assertEquals(65, b.bump(0, 1), "+1 does not go further out");
        assertEquals(64, b.bump(0, -1));
        assertEquals(-20, b.bump(1, -1));
        assertEquals(-19, b.bump(1, 1));
    }

    @Test
    void ofSortsByScoreAndListsOnlyChanges() {
        ScoreBoard b = ScoreBoard.of(List.of("A", "B", "C"), Map.of("A", 1, "B", 4, "C", 2), new int[]{5, 4, 3});
        assertEquals(List.of(r("B", 4), r("C", 2), r("A", 1)), b.rows());
        assertFalse(b.dirty());
        b.swap(0, 1);
        assertEquals(Map.of("C", 4, "B", 2), b.changes());
    }

    @Test
    void anUnscoredRoundStartsWithEveryoneOnTheBenchAndNothingToSend() {
        ScoreBoard b = ScoreBoard.of(List.of("A", "B", "C", "D"), Map.of(), new int[]{5, 4, 3});
        assertEquals(0, b.size());
        assertEquals(List.of("A", "B", "C", "D"), b.bench());
        assertEquals(4, b.steps());
        assertEquals(List.of(5, 4, 3, 0), List.of(b.stepBase(0), b.stepBase(1), b.stepBase(2), b.stepBase(3)));
        assertFalse(b.dirty(), "no ghost changes");
        // Clicked in the order they finished: C first, then A
        assertTrue(b.fromBench("C"));
        assertTrue(b.fromBench("A"));
        assertEquals(List.of(r("C", 5), r("A", 4)), b.rows());
        assertEquals(Map.of("C", 5, "A", 4), b.changes(), "the bench is not sent");
        assertFalse(b.fromBench("C"), "already placed");
    }

    @Test
    void benchAndStepsMove() {
        ScoreBoard b = ScoreBoard.of(List.of("A", "B", "C"), Map.of(), new int[]{5, 4, 3});
        b.fromBench("A");
        b.fromBench("B");
        b.fromBench("C", 0);
        assertEquals(List.of(r("C", 5), r("A", 4), r("B", 3)), b.rows(), "inserted at the top");
        b.toBench(1);
        assertEquals(List.of(r("C", 5), r("B", 4)), b.rows());
        assertEquals(List.of("A"), b.bench());
        b.benchTiedLast();
        assertEquals(List.of(r("C", 5), r("B", 4), r("A", 3)), b.rows());
    }

    @Test
    void aScoredRoundPutsPlayersWithoutScoreOnTheBench() {
        ScoreBoard b = ScoreBoard.of(List.of("A", "B", "C"), Map.of("B", 2, "A", 7), new int[]{5, 4, 3});
        assertEquals(List.of(r("A", 7), r("B", 2)), b.rows());
        assertEquals(List.of("C"), b.bench());
        assertEquals(3, b.stepBase(2), "a free step takes the scale");
        b.fromBench("C");
        assertEquals(Map.of("C", 3), b.changes());
    }

    @Test
    void everyGestureCanBeUndoneAndRedone() {
        ScoreBoard b = board();
        b.bump(0, 1);
        b.moveTo(0, 2);
        b.toBench(0);
        // B left: C and A move up a step, A keeps his +1
        assertEquals(List.of(r("C", 5), r("A", 4)), b.rows());
        assertTrue(b.undo());
        assertEquals(List.of(r("B", 5), r("C", 3), r("A", 2)), b.rows());
        assertTrue(b.undo());
        assertTrue(b.undo());
        assertEquals(List.of(r("A", 5), r("B", 3), r("C", 1)), b.rows());
        assertFalse(b.undo());
        assertTrue(b.redo());
        assertEquals(6, b.score(0));
        b.swap(1, 2);
        assertFalse(b.canRedo(), "a new gesture empties the redo");
    }
}
