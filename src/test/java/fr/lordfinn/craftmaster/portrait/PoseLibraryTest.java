package fr.lordfinn.craftmaster.portrait;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.portrait.Pose.Part;
import fr.lordfinn.craftmaster.portrait.Pose.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PoseLibraryTest {

    private static final Logger LOG = LoggerFactory.getLogger("test");

    @Test
    void presetsOfThePoster() {
        assertEquals(List.of("classique", "sort du cadre", "assis dans le cadre", "porte le cadre", "se penche dehors",
                "salut", "bras croisés", "trône", "sort par derrière"), List.copyOf(PoseLibrary.presets().keySet()));
        Pose bust = PoseLibrary.presets().get("classique");
        assertTrue(bust.isHidden(Part.RIGHT_LEG) && bust.isHidden(Part.LEFT_LEG), "a bust has no legs");
        assertFalse(bust.isHidden(Part.HEAD));
    }

    @Test
    void poseRoundTrip() {
        Pose p = Pose.NEUTRAL.withRotation(Part.RIGHT_ARM, new Rotation(-90, 12.5, 3))
                .withRotation(Part.FIGURE, new Rotation(0, 15, 0))
                .withHidden(Part.LEFT_LEG, true)
                .withOffset(1, -20, 4.25)
                .withScale(1.4)
                .withFrame(5, -3);
        Pose back = Pose.fromJson(JsonParser.parseString(p.toJson().toString()).getAsJsonObject());
        assertEquals(p, back);
    }

    @Test
    void jsonIsCompactAndReadable() {
        JsonObject o = Pose.NEUTRAL.withRotation(Part.HEAD, new Rotation(0, 10, 0)).toJson();
        assertEquals("[0,-10,2]", o.get("offset").toString());
        assertEquals("[0,10,0]", o.get("head").toString());
        assertFalse(o.has("rightArm"), "parts without rotation are left out");
        assertFalse(o.has("frame"));
        assertFalse(o.has("scale"));
        assertFalse(o.has("hidden"));
    }

    @Test
    void lenientReading() {
        Pose p = Pose.fromJson(JsonParser.parseString(
                "{\"head\": [1, 2], \"body\": [\"a\", 0, 0], \"rightArm\": [10, 20, 30], \"hidden\": [\"figure\", \"leftLeg\", 3, \"nope\"], \"scale\": -2, \"what\": 1}")
                .getAsJsonObject());
        assertEquals(Rotation.ZERO, p.rotation(Part.HEAD), "wrong size");
        assertEquals(Rotation.ZERO, p.rotation(Part.BODY), "not a number");
        assertEquals(new Rotation(10, 20, 30), p.rotation(Part.RIGHT_ARM));
        assertFalse(p.isHidden(Part.FIGURE), "the whole figure cannot be hidden");
        assertTrue(p.isHidden(Part.LEFT_LEG));
        assertEquals(1, p.scale(), "invalid scale");
        assertEquals(Pose.NEUTRAL.offsetY(), p.offsetY(), "default offset");
    }

    @Test
    void libraryRoundTripKeepsOrderAndNames() {
        PoseLibrary lib = PoseLibrary.withPresets();
        assertEquals("ma pose", lib.put("  Ma   Pose ", Pose.NEUTRAL));
        assertTrue(lib.has("MA POSE"));
        PoseLibrary back = PoseLibrary.fromJson(lib.toJson());
        assertEquals(lib.names(), back.names());
        assertEquals(lib.all(), back.all());
        assertNull(lib.put("   ", Pose.NEUTRAL), "empty names are refused");
    }

    @Test
    void missingPoseFallsBackToTheDefault() {
        PoseLibrary lib = PoseLibrary.withPresets();
        assertEquals(lib.get("classique"), lib.get("inconnue"));
        assertTrue(lib.remove("classique"));
        assertFalse(lib.remove("classique"));
        // Even without its default the library answers with the built-in one
        assertEquals(PoseLibrary.presets().get("classique"), lib.get("inconnue"));
        assertEquals(PoseLibrary.presets().get("salut"), lib.get("Salut"));
    }

    @Test
    void fileIsCreatedThenKeptAsEdited(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("craftmaster/poses.json");
        PoseLibrary lib = PoseLibrary.load(file, LOG);
        assertTrue(Files.exists(file));
        assertEquals(PoseLibrary.presets().keySet(), lib.keySet());

        // A deleted preset stays deleted, a new pose is kept
        lib.remove("salut");
        lib.put("victoire", Pose.NEUTRAL.withRotation(Part.LEFT_ARM, new Rotation(0, 0, -150)));
        lib.save(file, LOG);
        PoseLibrary again = PoseLibrary.load(file, LOG);
        assertFalse(again.has("salut"));
        assertEquals(lib.get("victoire"), again.get("victoire"));
    }

    @Test
    void brokenFileIsSetAsideAndReplaced(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("poses.json");
        Files.writeString(file, "{ not json", StandardCharsets.UTF_8);
        PoseLibrary lib = PoseLibrary.load(file, LOG);
        assertEquals(PoseLibrary.presets().keySet(), lib.keySet());
        assertTrue(Files.exists(dir.resolve("poses.json.broken")));
        assertEquals("{ not json", Files.readString(dir.resolve("poses.json.broken")));
    }
}
