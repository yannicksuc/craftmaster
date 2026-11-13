package fr.lordfinn.craftmaster.show;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Moderators' rights (docs/design/regie-moderateurs.md §2): which right each action needs, the list on disk. */
class RightsTest {

    @TempDir
    Path dir;

    @AfterEach
    void tearDown() {
        Moderators.reset();
    }

    private static Map<String, Object> a(String type, Object... fields) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        for (int i = 0; i < fields.length; i += 2) m.put((String) fields[i], fields[i + 1]);
        return m;
    }

    @Test
    void eachFamilyNeedsItsRight() {
        assertEquals(Rights.Right.CAMERA, Rights.required("camera.cut", a("camera.cut")));
        assertEquals(Rights.Right.CAMERA, Rights.required("camera.auto", a("camera.auto")));
        assertEquals(Rights.Right.CAMERA, Rights.required("broadcast.lowerthird", null));
        assertEquals(Rights.Right.CAMERA, Rights.required("spotlight.follow", null));
        assertEquals(Rights.Right.TV, Rights.required("tv.show", null));
        assertEquals(Rights.Right.REPLAY, Rights.required("replay.control", null));
        assertEquals(Rights.Right.GAME, Rights.required("score.add", null));
        assertEquals(Rights.Right.GAME, Rights.required("manche.start", null));
        assertEquals(Rights.Right.GAME, Rights.required("tablet.pause", null));
        assertEquals(Rights.Right.ADMIN, Rights.required("scores.publish", null));
        assertEquals(Rights.Right.ADMIN, Rights.required("reveal.start", null));
        assertEquals(Rights.Right.ADMIN, Rights.required("undo", null));
        assertEquals(Rights.Right.ADMIN, Rights.required("season.delete", null));
        assertEquals(Rights.Right.ADMIN, Rights.required("inconnue", null));
        assertNull(Rights.required("say", null));
    }

    @Test
    void screenEffectsOnPlayersStayWithTheOrganiser() {
        assertEquals(Rights.Right.CAMERA, Rights.required("fx.set", a("fx.set", "effect", "noir_blanc")));
        assertEquals(Rights.Right.CAMERA, Rights.required("fx.set", a("fx.set", "target", "diffusion")));
        assertEquals(Rights.Right.ADMIN, Rights.required("fx.set", a("fx.set", "target", "Ensuri")));
        assertEquals(Rights.Right.ADMIN, Rights.required("fx.clear", a("fx.clear")));
    }

    @Test
    void aModeratorIsRefusedPointsButNotCameras() {
        assertNull(Rights.refusal(Rights.MODO, List.of(a("camera.cut", "camera", 2), a("camera.auto"))));
        String r = Rights.refusal(Rights.MODO, List.of(a("camera.cut", "camera", 2), a("score.add", "player", "Ensuri", "points", 3)));
        assertNotNull(r);
        assertTrue(r.contains("score.add") && r.contains("jeu"), r);
        assertNull(Rights.refusal(EnumSet.of(Rights.Right.ADMIN), List.of(a("scores.publish"), a("undo"))));
        assertNotNull(Rights.refusal(EnumSet.noneOf(Rights.Right.class), List.of(a("camera.cut"))));
        assertNull(Rights.refusal(EnumSet.noneOf(Rights.Right.class), List.of(a("say", "text", "bonjour"))));
    }

    @Test
    void tabletOrdersAreShowActions() {
        var data = new fr.lordfinn.craftmaster.data.CraftMasterData(dir.resolve("config.yml"), org.slf4j.LoggerFactory.getLogger("test"));
        data.load(null);
        Actions.Outcome o = Actions.apply(data, List.of(a("tablet.pause", "group", "Plateau"), a("tablet.restart")));
        assertTrue(o.ok(), o.lines().toString());
        assertEquals(List.of(new Actions.TabletOrder("pause", "plateau"), new Actions.TabletOrder("restart", "")), o.effects());
    }

    @Test
    void theListIsKeptOnDiskAndNeverGivesTheOrganisersRight() throws Exception {
        Path file = dir.resolve("craftmaster/moderateurs.json");
        Moderators.load(file);
        Moderators.put("Ensuri_TV", "Ensuri", EnumSet.of(Rights.Right.CAMERA, Rights.Right.ADMIN, Rights.Right.GAME));
        assertTrue(Files.isRegularFile(file));
        Moderators.load(file);
        Moderators.Entry e = Moderators.byTwitch("ensuri_tv");
        assertNotNull(e);
        assertEquals("Ensuri", e.minecraft);
        assertEquals(EnumSet.of(Rights.Right.CAMERA, Rights.Right.GAME), e.rightSet());
        assertSame(e, Moderators.byMinecraft("ensuri"));
        assertEquals(EnumSet.of(Rights.Right.CAMERA, Rights.Right.GAME), Actor.ofTwitch("ENSURI_TV").rights());

        // A hand-written "admin" in the file is ignored too
        Files.writeString(file, "{\"moderators\":[{\"twitch\":\"x\",\"rights\":[\"admin\",\"tv\"]}]}");
        Moderators.load(file);
        assertEquals(EnumSet.of(Rights.Right.TV), Moderators.byTwitch("x").rightSet());

        Moderators.setPaused(true);
        assertFalse(Actor.ofTwitch("x").any());
        Moderators.setPaused(false);
        assertTrue(Actor.ofTwitch("x").any());
        assertFalse(Actor.ofTwitch("inconnu").any());

        assertTrue(Moderators.remove("x"));
        assertFalse(Moderators.remove("x"));
    }
}
