package fr.lordfinn.craftmaster.data;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ScoreEntryBoardTest {

    @TempDir
    Path dir;

    @Test
    void theBoardListsRoundsPlayersScoresScaleAndUndo() {
        CraftMasterData data = new CraftMasterData(dir.resolve("config.yml"), LoggerFactory.getLogger("test"));
        data.load(null);
        data.addTask(1, "Pont");
        data.addTask(1, "Tour");
        data.setScore(1, "Pont", "Bob", 3);
        data.setScore(1, "Pont", "Ann", -2);
        data.undo(1);
        JsonObject b = ScoreEntryBoard.build(data, 1);
        assertEquals(1, b.get("season").getAsInt());
        assertEquals(2, b.getAsJsonArray("manches").size());
        JsonObject pont = b.getAsJsonArray("manches").get(0).getAsJsonObject();
        assertEquals("Pont", pont.get("name").getAsString());
        assertEquals(1, pont.get("number").getAsInt());
        assertEquals(3, pont.getAsJsonObject("scores").get("Bob").getAsInt());
        assertFalse(pont.getAsJsonObject("scores").has("Ann"), "undone");
        assertEquals(5, pont.getAsJsonArray("scale").get(0).getAsInt(), "default scale 5-4-3-2-1");
        assertTrue(b.getAsJsonArray("players").toString().contains("Bob"));
        assertEquals(1, b.get("redo").getAsInt());
        assertNull(ScoreEntryBoard.build(data, 42));
    }
}
