package fr.lordfinn.craftmaster.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoundedHistoryTest {

    @Test
    void undoThenRedo() {
        BoundedHistory<Integer> h = new BoundedHistory<>();
        h.record(1);
        assertEquals(1, h.undo(2).orElseThrow());
        assertEquals(2, h.redo(1).orElseThrow());
        assertTrue(h.redo(2).isEmpty());
    }

    @Test
    void newChangeClearsRedoAndLimitIsKept() {
        BoundedHistory<Integer> h = new BoundedHistory<>(50);
        for (int i = 0; i < 80; i++) h.record(i);
        assertEquals(50, h.undoSize());
        h.undo(80);
        h.record(99);
        assertEquals(0, h.redoSize());
    }
}
