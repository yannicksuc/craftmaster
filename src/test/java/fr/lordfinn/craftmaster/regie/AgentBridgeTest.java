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

    private static AgentBridge.Kind kind(String line) {
        return AgentBridge.classify(AgentBridge.words(line));
    }

    @Test
    void theShowsSetupRunsSeenThingsWaitForAClick() {
        // The setup: at once
        assertEquals(AgentBridge.Kind.FREE, kind("cm tv intro la-pomme plan 3 effet macro"));
        assertEquals(AgentBridge.Kind.FREE, kind("cm tv intro la-pomme plan 3 focus de centre vers fond"));
        assertEquals(AgentBridge.Kind.FREE, kind("cm camera placer la-pomme:4 10 64 3 vers 12 63 8"));
        assertEquals(AgentBridge.Kind.FREE, kind("cam zoom la-pomme:4 40"));
        assertEquals(AgentBridge.Kind.FREE, kind("cam name la-pomme:6 Le marché"));
        assertEquals(AgentBridge.Kind.FREE, kind("cam motion la-pomme:6 travelling"));
        assertEquals(AgentBridge.Kind.FREE, kind("ambiance modifier tension contre couleur rouge"));
        // Reading
        assertEquals(AgentBridge.Kind.READ, kind("cm tv intro la-pomme voir"));
        assertEquals(AgentBridge.Kind.READ, kind("cm arene liste"));
        assertEquals(AgentBridge.Kind.READ, kind("cm statut"));
        assertEquals(AgentBridge.Kind.READ, kind("list"));
        assertEquals(AgentBridge.Kind.READ, kind("execute in minecraft:pomme if block 28 72 13 minecraft:beehive[honey_level=5]"));
        assertEquals(AgentBridge.Kind.CONFIRM, kind("execute in minecraft:pomme if block 28 72 13 beehive run setblock 28 72 13 air"));
        assertEquals(AgentBridge.Kind.CONFIRM, kind("execute store result score #x t if block 0 0 0 air"));
        // Seen by the players or the stream, deleting, the console: a click first
        assertEquals(AgentBridge.Kind.CONFIRM, kind("cm tv intro la-pomme jouer"));
        assertEquals(AgentBridge.Kind.CONFIRM, kind("cm epreuve lancer la-pomme"));
        assertEquals(AgentBridge.Kind.CONFIRM, kind("cam cut 13 flash"));
        assertEquals(AgentBridge.Kind.CONFIRM, kind("ambiance lancer tension"));
        assertEquals(AgentBridge.Kind.CONFIRM, kind("cm camera supprimer la-pomme:4"));
        assertEquals(AgentBridge.Kind.CONFIRM, kind("cm fx diffusion sepia"));
        assertEquals(AgentBridge.Kind.CONFIRM, kind("tp LordFinn 0 64 0"));
        assertEquals(AgentBridge.Kind.CONFIRM, kind("gamemode creative Ensuri"));
        // Never
        assertEquals(AgentBridge.Kind.REFUSED, kind("stop"));
        assertEquals(AgentBridge.Kind.REFUSED, kind("op Ensuri"));
        assertEquals(AgentBridge.Kind.REFUSED, kind("cm ia confirmer 3"));
        assertEquals(AgentBridge.Kind.REFUSED, kind("minecraft:ban Ensuri"));
    }

    @Test
    void addressesAreMasked() {
        assertEquals("serveur ‹adresse masquée› prêt", AgentBridge.mask("serveur 10.0.0.12:25565 prêt"));
        assertEquals("caméra 13 à 7.5 s", AgentBridge.mask("caméra 13 à 7.5 s"));
    }
}
