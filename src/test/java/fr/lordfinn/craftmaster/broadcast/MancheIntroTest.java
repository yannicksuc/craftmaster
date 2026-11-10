package fr.lordfinn.craftmaster.broadcast;

import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.MancheIntro;
import fr.lordfinn.craftmaster.show.Actions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Round intros: the sequence's timing, the intro actions, the file and undo. */
class MancheIntroTest {

    @TempDir
    Path dir;

    private CraftMasterData data;

    @BeforeEach
    void setUp() {
        data = new CraftMasterData(dir.resolve("config.yml"), LoggerFactory.getLogger("test"));
        data.load(null);
        Actions.Outcome o = run(a("season.create", "name", "Saison 4"), a("player.add", "pseudo", "Ensuri"),
                a("manche.create", "name", "Le Pont"), a("manche.create", "name", "La Tour"));
        assertTrue(o.ok(), o.lines().toString());
    }

    private static Map<String, Object> a(String type, Object... fields) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        for (int i = 0; i < fields.length; i += 2) m.put((String) fields[i], fields[i + 1]);
        return m;
    }

    @SafeVarargs
    private Actions.Outcome run(Map<String, Object>... actions) {
        return Actions.apply(data, List.of(actions));
    }

    private Manche pont() {
        return data.activeSeason().activePartie().manches.getFirst();
    }

    @Test
    void timelineUsesPathLengthsAndDefaults() {
        MancheIntro in = new MancheIntro();
        in.shots.add(new MancheIntro.Shot("camera", "large", 0, "", "", "", ""));
        in.shots.add(new MancheIntro.Shot("trajet", "pont", 0, "", "sepia", "", ""));
        in.shots.add(new MancheIntro.Shot("trajet", "inconnu", 0, "", "", "", ""));
        in.shots.add(new MancheIntro.Shot("suivi", "Ensuri", 2.5, "", "", "synthe", ""));
        List<MancheIntro.Timed> tl = in.timeline(n -> n.equals("pont") ? 9 : 0);
        assertEquals(List.of(0.0, 4.0, 13.0, 17.0), tl.stream().map(MancheIntro.Timed::start).toList());
        assertEquals(19.5, in.duration(n -> n.equals("pont") ? 9 : 0), 1e-9);
        // One shot has a card: no automatic title
        assertEquals(-1, in.autoCardShot());
        in.shots.set(3, in.shots.get(3).withCard("", ""));
        assertEquals(3, in.autoCardShot());
        in.titleCard = false;
        assertEquals(-1, in.autoCardShot());
    }

    @Test
    void normalizations() {
        assertEquals(MancheIntro.PATH, MancheIntro.normalizeKind("travelling"));
        assertEquals(MancheIntro.FOLLOW, MancheIntro.normalizeKind("follow"));
        assertEquals(MancheIntro.TITLE, MancheIntro.normalizeCard("Titre"));
        assertEquals(MancheIntro.LOWER_THIRD, MancheIntro.normalizeCard("synthé"));
        assertEquals("", MancheIntro.normalizeCard("aucune"));
        assertEquals("theme", MancheIntro.normalizeMusic("Thème"));
        assertEquals("", MancheIntro.normalizeMusic("aucune"));
        assertEquals("minecraft:music_disc.cat", MancheIntro.normalizeMusic("music_disc.cat"));
        assertEquals("noir_blanc", BroadcastActions.effectId("Noir et blanc"));
        assertEquals("masque_rond", BroadcastActions.effectId("masque rond"));
        assertEquals("", BroadcastActions.effectId("aucun"));
    }

    @Test
    void actionsBuildTheIntroAndUndoWorks() {
        Actions.Outcome o = run(a("intro.shot.add", "manche", "Le Pont", "camera", 1.0, "seconds", 3),
                a("intro.shot.add", "manche", "Le Pont", "path", "survol", "effect", "Sépia", "transition", "fondu"),
                a("intro.set", "manche", "Le Pont", "auto", true, "music", "theme"));
        assertTrue(o.ok(), o.lines().toString());
        MancheIntro in = pont().intro;
        assertEquals(2, in.shots.size());
        assertEquals("1", in.shots.get(0).ref());
        assertEquals(3, in.shots.get(0).seconds());
        assertEquals("trajet", in.shots.get(1).kind());
        assertEquals("sepia", in.shots.get(1).effect());
        assertEquals("fondu", in.shots.get(1).transition());
        assertTrue(in.auto);
        assertEquals("theme", in.music);

        assertTrue(run(a("intro.shot.set", "manche", "Le Pont", "index", 2, "card", "titre", "text", "Le Pont | Manche 1")).ok());
        assertEquals("titre", pont().intro.shots.get(1).card());
        assertEquals("sepia", pont().intro.shots.get(1).effect());
        assertTrue(run(a("intro.shot.add", "manche", "Le Pont", "follow", "Ensuri", "at", 1)).ok());
        assertEquals("suivi", pont().intro.shots.getFirst().kind());

        // The file keeps it
        data.save();
        CraftMasterData again = new CraftMasterData(dir.resolve("config.yml"), LoggerFactory.getLogger("test"));
        again.load(null);
        MancheIntro read = again.activeSeason().activePartie().manches.getFirst().intro;
        assertEquals(3, read.shots.size());
        assertEquals("Le Pont | Manche 1", read.shots.get(2).text());
        assertTrue(read.auto);

        // Undo takes the last change back
        assertTrue(run(a("intro.shot.remove", "manche", "Le Pont", "index", 1)).ok());
        assertEquals(2, pont().intro.shots.size());
        assertTrue(run(a("undo")).ok());
        assertEquals(3, pont().intro.shots.size());
        assertTrue(run(a("intro.clear", "manche", "Le Pont")).ok());
        assertTrue(pont().intro.isEmpty());
    }

    @Test
    void wrongIntroActionsChangeNothing() {
        assertFalse(run(a("intro.shot.add", "manche", "Le Pont")).ok());
        assertFalse(run(a("intro.shot.add", "manche", "Le Pont", "camera", "2", "seconds", 500)).ok());
        assertFalse(run(a("intro.shot.remove", "manche", "Le Pont", "index", 1)).ok());
        assertFalse(run(a("intro.set", "manche", "Le Pont")).ok());
        assertFalse(run(a("intro.play", "manche", "Le Pont")).ok());
        assertFalse(run(a("intro.shot.add", "manche", "Le Pont", "camera", "2", "transition", "volet")).ok());
        assertTrue(pont().intro.isEmpty());
        // A round without an intro is not written with one
        assertFalse(data.toYaml().toString().contains("intro"));
    }

    @Test
    void playIsAnEffectForTheDirector() {
        assertTrue(run(a("intro.shot.add", "manche", "Le Pont", "camera", "large")).ok());
        Actions.Outcome o = run(a("intro.play", "manche", "Le Pont"));
        assertTrue(o.ok(), o.lines().toString());
        Actions.Broadcast b = assertInstanceOf(Actions.Broadcast.class, o.effects().getFirst());
        assertEquals("intro.play", b.action().get("type"));
        assertEquals(pont().id, b.action().get("manche"));
    }

    @Test
    void newCameraActionsAreChecked() {
        assertEquals("2", BroadcastActions.validate("camera.cut", Map.of("camera", 2.0)).get("camera"));
        assertEquals("sepia", BroadcastActions.validate("camera.cut", Map.of("camera", "1", "effect", "sépia")).get("effect"));
        assertEquals("pont", BroadcastActions.validate("camera.path", Map.of("path", "pont")).get("path"));
        assertThrows(Actions.Invalid.class, () -> BroadcastActions.validate("camera.path", Map.of()));
        assertThrows(Actions.Invalid.class, () -> BroadcastActions.validate("camera.focus", Map.of()));
        assertEquals("ensuri", BroadcastActions.validate("camera.focus", Map.of("player", "ensuri")).get("player"));
        assertThrows(Actions.Invalid.class, () -> BroadcastActions.validate("camera.lieu", Map.of("camera", "3")));
        assertEquals(false, BroadcastActions.validate("camera.mosaic", Map.of("enabled", "off")).get("enabled"));
        assertTrue(Actions.TYPES.containsAll(BroadcastActions.TYPES));
    }
}
