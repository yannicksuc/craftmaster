package fr.lordfinn.craftmaster.trial;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextMatchTest {

    @Test
    void spacesCaseAccentsAndPunctuationDontCount() {
        List<String> a = List.of("pomme de terre");
        assertTrue(TextMatch.matches("Pomme-de-terre", a));
        assertTrue(TextMatch.matches(" pomme  de terre ", a));
        assertTrue(TextMatch.matches("POMME DE TERRE !", a));
        assertTrue(TextMatch.matches("pommedeterre", a));
        assertTrue(TextMatch.matches("Crème brûlée", List.of("creme brulee")));
        assertTrue(TextMatch.matches("Œuf", List.of("oeuf")));
        assertTrue(TextMatch.matches("garçon", List.of("garcon")));
        assertTrue(TextMatch.matches("aujourd'hui", List.of("aujourdhui")));
        assertFalse(TextMatch.matches("pomme", a));
        assertFalse(TextMatch.matches("   ", a));
        assertFalse(TextMatch.matches("", List.of("")));
    }

    @Test
    void theRoomCodeWithSpacesOrCapitals() {
        List<String> code = List.of("riricococo");
        assertTrue(TextMatch.matches("Riricococo", code));
        assertTrue(TextMatch.matches("riri coco co", code));
        assertTrue(TextMatch.matches("RIRI-COCOCO.", code));
        assertFalse(TextMatch.matches("ririkokoko", code));
    }

    @Test
    void variantsOfAWord() {
        Map<String, List<String>> v = Map.of("cot", List.of("cote", "cotte"));
        assertTrue(TextMatch.matches("cot", List.of("cot"), v));
        assertTrue(TextMatch.matches("Côte", List.of("cot"), v));
        assertTrue(TextMatch.matches(" COTTE ", List.of("cot"), v));
        assertTrue(TextMatch.matches("cocori cote cotcot", List.of("cocori cot cotcot"), v));
        assertFalse(TextMatch.matches("coton", List.of("cot"), v));
        assertFalse(TextMatch.matches("cote", List.of("cot"), Map.of()));
    }

    @Test
    void firstLetterAfterNormalising() {
        assertEquals("e", TextMatch.initial("  Éléphant"));
        assertEquals("a", TextMatch.initial("'Arbre"));
        assertEquals("p", TextMatch.initial("Pomme-de-terre"));
        assertEquals("", TextMatch.initial(" - "));
        assertEquals("pomme de terre", TextMatch.words("  Pomme--de   terre! "));
    }
}
