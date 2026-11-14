package fr.lordfinn.craftmaster.regie;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The régie agent's guards (pure logic of {@link AgentBridge}). */
class AgentBridgeTest {

    @Test
    void quotedNamesStayWhole() {
        assertEquals(List.of("ambiance", "modifier", "\"plateau ouvert\"", "face", "couleur", "rouge"),
                AgentBridge.words("ambiance modifier \"plateau ouvert\" face couleur rouge"));
    }

    @Test
    void cueLinesAreChecked() {
        assertEquals("attendre 2", AgentBridge.embeddedLine("conduite", AgentBridge.words("conduite ajouter ouverture attendre 2")));
        assertEquals("op Ensuri", AgentBridge.embeddedLine("conduite", AgentBridge.words("conduite inserer ouverture 2 op Ensuri")));
        assertNull(AgentBridge.embeddedLine("conduite", AgentBridge.words("conduite voir ouverture")));
        assertNull(AgentBridge.lineRefusal("ambiance tension 1"));
        assertNull(AgentBridge.lineRefusal("cam cut 13 flash"));
        assertNull(AgentBridge.lineRefusal("playsound minecraft:block.note_block.snare master @a"));
        assertNotNull(AgentBridge.lineRefusal("op Ensuri"));
        assertNotNull(AgentBridge.lineRefusal("/stop"));
    }

    @Test
    void addressesAreMasked() {
        assertEquals("serveur ‹adresse masquée› prêt", AgentBridge.mask("serveur 10.0.0.12:25565 prêt"));
        assertEquals("caméra 13 à 7.5 s", AgentBridge.mask("caméra 13 à 7.5 s"));
    }
}
