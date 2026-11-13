package fr.lordfinn.craftmaster.stage;

import fr.lordfinn.craftmaster.studio.SpotlightFollow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StageWordsTest {

    @Test
    void lookPartsInFrench() {
        assertEquals(Look.LIGHTS, Look.part("Lumières"));
        assertEquals(Look.LIGHTS, Look.part("projos"));
        assertEquals(Look.CURTAINS, Look.part("rideau"));
        assertEquals(Look.PORTRAITS, Look.part("cadres"));
        assertEquals(Look.SCREENS, Look.part("télés"));
        assertNull(Look.part("banane"));
    }

    @Test
    void followRules() {
        assertEquals(SpotlightFollow.STAGE, SpotlightFollow.parse("@scène"));
        assertEquals(SpotlightFollow.STAGE, SpotlightFollow.parse("sur scène"));
        assertEquals(SpotlightFollow.NEAREST, SpotlightFollow.parse("@proche"));
        assertEquals(SpotlightFollow.CONTESTANT, SpotlightFollow.parse("candidat"));
        assertEquals(SpotlightFollow.MOMENT, SpotlightFollow.parse("@moment"));
        assertEquals("@zone:podium", SpotlightFollow.parse("@zone:Podium"));
        assertNull(SpotlightFollow.parse("Ensuri"), "a player's name is not a rule");
        assertTrue(SpotlightFollow.isRule("@scene"));
        assertFalse(SpotlightFollow.isRule("Ensuri"));
    }

    @Test
    void numbersAndNames() {
        assertEquals(2.5, Cues.number("2,5", 0));
        assertEquals(3, Cues.number("3s", 0));
        assertEquals(7, Cues.number("bientôt", 7));
        assertEquals("plateau ouvert", Scenography.key("  Plateau   Ouvert "));
    }
}
