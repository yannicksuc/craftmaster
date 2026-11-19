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

    /** La pomme: the code is an enigma of « co », « cot » and « ri »; « cote » and « cotte » are « cot ». */
    private static final Map<String, List<String>> CO_COT_RI = new java.util.LinkedHashMap<>(Map.of(
            "co", List.of(), "cot", List.of("cote", "cotte"), "ri", List.of()));

    @Test
    void syllablesWithFreeSeparators() {
        assertEquals(List.of("co", "cot", "ri"), TextMatch.syllables("cocotri", CO_COT_RI));
        assertEquals(List.of("co", "cot", "ri"), TextMatch.syllables("Co-Cot-Ri", CO_COT_RI));
        assertEquals(List.of("co", "cot", "ri"), TextMatch.syllables(" co  côte  RI ", CO_COT_RI));
        assertEquals(List.of("co", "cot", "ri"), TextMatch.syllables("co-cotte-ri", CO_COT_RI));
        assertEquals(List.of("co", "co", "ri", "co"), TextMatch.syllables("cocorico", CO_COT_RI));
        assertEquals(null, TextMatch.syllables("cotx", CO_COT_RI));
        assertEquals(null, TextMatch.syllables("cocorica", CO_COT_RI));
        assertEquals(null, TextMatch.syllables("  ", CO_COT_RI));
    }

    @Test
    void theRoomCodeRightAnswers() {
        List<String> code = List.of("riricococo");
        for (String ok : List.of("riricococo", "Riricococo", "RI RI CO CO CO", "ri-ri-co-co-co", " riri coco co ", "Ri, ri, co, co, co !"))
            assertTrue(TextMatch.matchesSyllables(ok, code, CO_COT_RI), ok);
        List<String> withCot = List.of("co cot ri");
        for (String ok : List.of("cocotri", "co côte ri", "CO-COTTE-RI", "cocoteri", "cocotteri"))
            assertTrue(TextMatch.matchesSyllables(ok, withCot, CO_COT_RI), ok);
    }

    @Test
    void theRoomCodeNearMissesFail() {
        List<String> code = List.of("riricococo");
        for (String wrong : List.of(
                "riricoco",          // a syllable missing
                "riricocococo",      // an extra syllable
                "riricotcoco",       // cot instead of co
                "ricoricoco",        // same syllables, other order
                "cocorico",          // not the code
                "riricococox",       // something left that is no syllable
                "riricocoka",
                ""))
            assertFalse(TextMatch.matchesSyllables(wrong, code, CO_COT_RI), wrong);
        List<String> withCot = List.of("co cot ri");
        for (String wrong : List.of("cocori", "cotcotri", "cocotecot ri", "co cot ri ri", "cot co ri", "co coton ri"))
            assertFalse(TextMatch.matchesSyllables(wrong, withCot, CO_COT_RI), wrong);
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
