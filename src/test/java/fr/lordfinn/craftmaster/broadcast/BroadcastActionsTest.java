package fr.lordfinn.craftmaster.broadcast;

import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.show.Actions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BroadcastActionsTest {

    @TempDir
    Path dir;

    @Test
    void cutKeepsTheReferenceAndParsesTheTransition() {
        Map<String, Object> a = BroadcastActions.validate("camera.cut", Map.of("camera", "Plan large", "transition", "fondu enchaîné"));
        assertEquals("Plan large", a.get("camera"));
        assertEquals(BroadcastNetwork.CROSSFADE, a.get("transition"));
        assertFalse(BroadcastActions.validate("camera.cut", Map.of("camera", 2.0)).containsKey("transition"));
        assertEquals(BroadcastNetwork.CUT, BroadcastActions.parseTransition("Coupe"));
        assertEquals(BroadcastNetwork.FADE, BroadcastActions.parseTransition("fade"));
    }

    @Test
    void missingOrWrongFieldsAreRefused() {
        assertThrows(Actions.Invalid.class, () -> BroadcastActions.validate("camera.cut", Map.of()));
        assertThrows(Actions.Invalid.class, () -> BroadcastActions.validate("camera.follow", Map.of()));
        assertThrows(Actions.Invalid.class, () -> BroadcastActions.validate("camera.cut", Map.of("camera", "1", "transition", "wipe")));
        assertThrows(Actions.Invalid.class, () -> BroadcastActions.validate("camera.auto", Map.of("enabled", "peut-être")));
        assertThrows(Actions.Invalid.class, () -> BroadcastActions.validate("broadcast.lowerthird", Map.of("subtitle", "x")));
        assertThrows(Actions.Invalid.class, () -> BroadcastActions.validate("broadcast.task", Map.of("seconds", 0)));
        assertThrows(Actions.Invalid.class, () -> BroadcastActions.validate("broadcast.scores", Map.of("mode", "parfois")));
    }

    @Test
    void defaultsAndBooleans() {
        assertEquals(true, BroadcastActions.validate("camera.auto", Map.of("enabled", "oui")).get("enabled"));
        assertEquals(false, BroadcastActions.validate("broadcast.letterbox", Map.of("enabled", false)).get("enabled"));
        assertEquals(12, BroadcastActions.validate("broadcast.task", Map.of()).get("seconds"));
        assertEquals("show", BroadcastActions.validate("broadcast.scores", Map.of()).get("mode"));
        assertEquals("off", BroadcastActions.validate("broadcast.scores", Map.of("mode", "jamais")).get("mode"));
        Map<String, Object> lt = BroadcastActions.validate("broadcast.lowerthird", Map.of("text", "Pause", "seconds", 4.0));
        assertEquals("Pause", lt.get("text"));
        assertEquals(4, lt.get("seconds"));
    }

    @Test
    void theExecutorTurnsThemIntoEffectsWithoutTouchingTheData() {
        CraftMasterData data = new CraftMasterData(dir.resolve("config.yml"), LoggerFactory.getLogger("test"));
        Actions.Outcome o = Actions.apply(data, List.of(
                Map.of("type", "camera.cut", "camera", "2"),
                Map.of("type", "show.intro"),
                Map.of("type", "broadcast.lowerthird", "player", "Ensuri")));
        assertTrue(o.ok());
        assertFalse(o.changed());
        assertTrue(data.history().isEmpty());
        assertEquals(3, o.effects().size());
        assertInstanceOf(Actions.Broadcast.class, o.effects().getFirst());
        assertEquals("camera.cut", ((Actions.Broadcast) o.effects().getFirst()).action().get("type"));
        assertTrue(Actions.TYPES.containsAll(BroadcastActions.TYPES));
    }

    @Test
    void anInvalidDirectionActionCancelsTheWholeList() {
        CraftMasterData data = new CraftMasterData(dir.resolve("config.yml"), LoggerFactory.getLogger("test"));
        Actions.Outcome o = Actions.apply(data, List.of(
                Map.of("type", "season.create", "name", "Saison test"),
                Map.of("type", "camera.cut")));
        assertFalse(o.ok());
        assertTrue(o.lines().getFirst().contains("camera.cut"));
        assertTrue(data.seasonMap().isEmpty());
    }
}
