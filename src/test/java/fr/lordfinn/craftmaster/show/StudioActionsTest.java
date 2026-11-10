package fr.lordfinn.craftmaster.show;

import fr.lordfinn.craftmaster.data.CraftMasterData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The studio set's actions (chantier F): spotlight orders, which change nothing in the data. */
class StudioActionsTest {

    @TempDir
    Path dir;

    private CraftMasterData data;

    @BeforeEach
    void setUp() {
        data = new CraftMasterData(dir.resolve("config.yml"), LoggerFactory.getLogger("test"));
        data.load(null);
    }

    private static Map<String, Object> a(String type, Object... fields) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        for (int i = 0; i < fields.length; i += 2) m.put((String) fields[i], fields[i + 1]);
        return m;
    }

    @Test
    void followResolvesNicknamesAndKeepsGuests() {
        assertTrue(Actions.apply(data, List.of(a("season.create", "name", "Saison 4"),
                a("player.add", "pseudo", "Ensuri", "aliases", "Ensu, Enzo"))).ok());
        int before = data.history().size();
        Actions.Outcome o = Actions.apply(data, List.of(a("spotlight.follow", "player", "enzo")));
        assertTrue(o.ok(), o.lines().toString());
        assertEquals(List.of(new Actions.SpotlightOrder("follow", "Ensuri")), o.effects());
        assertFalse(o.changed());
        assertEquals(before, data.history().size());
        // Not in the cast (the master, a guest): the exact pseudo is kept
        assertEquals(List.of(new Actions.SpotlightOrder("follow", "LordFinn")),
                Actions.apply(data, List.of(a("spotlight.follow", "player", "LordFinn"))).effects());
    }

    @Test
    void stopOnOffAndMissingPlayer() {
        assertEquals(List.of(new Actions.SpotlightOrder("stop", null), new Actions.SpotlightOrder("off", null), new Actions.SpotlightOrder("on", null)),
                Actions.apply(data, List.of(a("spotlight.stop"), a("spotlight.off"), a("spotlight.on"))).effects());
        assertFalse(Actions.apply(data, List.of(a("spotlight.follow"))).ok());
        assertTrue(Actions.TYPES.containsAll(List.of("spotlight.follow", "spotlight.stop", "spotlight.on", "spotlight.off")));
    }
}
