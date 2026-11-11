package fr.lordfinn.craftmaster.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScoreBoardTest {

    private ScoreBoard board() {
        return new ScoreBoard(List.of(new ScoreBoard.Row("A", 5), new ScoreBoard.Row("B", 3), new ScoreBoard.Row("C", 1)));
    }

    @Test
    void swapTradesPlacesAndTakesTheSlotScore() {
        ScoreBoard b = board();
        b.swap(0, 2);
        assertEquals(List.of(new ScoreBoard.Row("C", 5), new ScoreBoard.Row("B", 3), new ScoreBoard.Row("A", 1)), b.rows());
    }

    @Test
    void toFrontShiftsTheOthersDown() {
        ScoreBoard b = board();
        b.toFront(2);
        assertEquals(List.of(new ScoreBoard.Row("C", 5), new ScoreBoard.Row("A", 3), new ScoreBoard.Row("B", 1)), b.rows());
    }

    @Test
    void bumpOnlyChangesOnePlayerAndIsClamped() {
        ScoreBoard b = board();
        b.toFront(2);
        assertEquals(6, b.bump(0, 1));
        assertEquals(List.of(new ScoreBoard.Row("C", 6), new ScoreBoard.Row("A", 3), new ScoreBoard.Row("B", 1)), b.rows());
        for (int i = 0; i < 20; i++) b.bump(1, 1);
        assertEquals(10, b.rows().get(1).score());
        for (int i = 0; i < 40; i++) b.bump(1, -1);
        assertEquals(-10, b.rows().get(1).score());
    }

    @Test
    void outOfRangeInitialScoresAreClamped() {
        assertEquals(10, new ScoreBoard(List.of(new ScoreBoard.Row("A", 99))).rows().get(0).score());
    }
}
