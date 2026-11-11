package fr.lordfinn.craftmaster.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UndoJournalTest {

    @TempDir
    Path dir;

    private CraftMasterData data;

    @BeforeEach
    void setUp() {
        data = new CraftMasterData(dir.resolve("config.yml"), LoggerFactory.getLogger("test"));
        data.load(null);
    }

    private CraftMasterData reloaded() {
        CraftMasterData d = new CraftMasterData(dir.resolve("config.yml"), LoggerFactory.getLogger("test"));
        d.load(null);
        return d;
    }

    @Test
    void undoPutsBackTheStateBeforeTheChange() {
        data.setScore(1, "Pont", "Bob", 3);
        data.addScore(1, "Pont", "Bob", 4);
        assertEquals(7, data.total(1, "Bob"));
        assertEquals(List.of("Score : Bob = 7 (Pont)"), data.undo(1));
        assertEquals(3, data.total(1, "Bob"));
        assertEquals(3, reloaded().total(1, "Bob"), "the undo is saved");
    }

    @Test
    void severalChangesAreUndoneAtOnceMostRecentFirst() {
        data.addTask(1, "A");
        data.addTask(1, "B");
        data.addTask(1, "C");
        assertEquals(List.of("Manche ajoutée : C", "Manche ajoutée : B"), data.undo(2));
        assertEquals(List.of("A"), data.tasks(1));
        assertEquals(1, data.history().size());
        assertEquals(List.of("Manche ajoutée : A"), data.undo(5), "only what is left");
        assertTrue(data.undo(1).isEmpty(), "nothing more to undo");
    }

    @Test
    void redoPutsBackWhatWasUndoneInOrder() {
        data.addTask(1, "A");
        data.addTask(1, "B");
        data.addTask(1, "C");
        data.undo(2);
        assertEquals(2, data.redoSize());
        assertEquals(List.of("Manche ajoutée : B"), data.redo(1));
        assertEquals(List.of("A", "B"), data.tasks(1));
        assertEquals(List.of("Manche ajoutée : C"), data.redo(5), "only what is left");
        assertEquals(List.of("A", "B", "C"), data.tasks(1));
        assertEquals(List.of("A", "B", "C"), reloaded().tasks(1), "the redo is saved");
        assertTrue(data.redo(1).isEmpty());
        // Redone changes can be undone again
        assertEquals(List.of("Manche ajoutée : C"), data.undo(1));
        assertEquals(List.of("A", "B"), data.tasks(1));
    }

    @Test
    void aNewChangeEmptiesTheRedo() {
        data.setScore(1, "Pont", "Bob", 3);
        data.setScore(1, "Pont", "Bob", 5);
        data.undo(1);
        data.setScore(1, "Pont", "Ann", 1);
        assertEquals(0, data.redoSize());
        assertTrue(data.redo(1).isEmpty());
        assertEquals(3, data.total(1, "Bob"));
    }

    @Test
    void nestedChangesMakeOneEntry() {
        data.change("Préparation", () -> {
            data.addTask(1, "A");
            data.setScore(1, "A", "Bob", 2);
            data.setHidden(1, true);
        });
        assertEquals(1, data.history().size());
        assertEquals("Préparation", data.history().getFirst().description());
        data.undo(1);
        assertTrue(data.seasons().isEmpty());
    }

    @Test
    void aChangeThatModifiesNothingIsNotRecorded() {
        data.addTask(1, "A");
        assertFalse(data.addTask(1, "A"));
        assertFalse(data.removeTask(1, "Z"));
        assertEquals(1, data.history().size());
    }

    @Test
    void aFailingChangeLeavesTheDataUntouched() {
        data.addTask(1, "A");
        assertThrows(IllegalStateException.class, () -> data.change("Boom", () -> {
            data.addTask(1, "B");
            throw new IllegalStateException("boom");
        }));
        assertEquals(List.of("A"), data.tasks(1));
        assertEquals(1, data.history().size());
    }

    @Test
    void theJournalKeepsTheLast50Changes() {
        for (int i = 0; i < 60; i++) data.setScore(1, "A", "Bob", i);
        assertEquals(CraftMasterData.JOURNAL_SIZE, data.history().size());
        data.undo(100);
        assertEquals(9, data.total(1, "Bob"), "back to the oldest state still known");
    }

    @Test
    void liveStateIsUndoneToo() {
        data.change("Manche en cours", () -> {
            data.addTask(1, "A");
            data.live().current = new Live.Current(1, 1, "a");
        });
        assertNotNull(data.currentManche());
        data.undo(1);
        assertNull(data.currentManche());
    }
}
