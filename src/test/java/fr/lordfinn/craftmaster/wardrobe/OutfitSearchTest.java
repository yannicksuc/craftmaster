package fr.lordfinn.craftmaster.wardrobe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutfitSearchTest {

    private static final String DOCTOR = "Médecin Medical mask Doctor's robe Doctor's trousers selfexpression:doctor_boots";
    private static final String MAFIA = "Mafieux Mafia hat Mafia jacket Mafia trousers Mafia shoes";

    @Test
    void emptyQueryMatchesEverything() {
        assertTrue(OutfitSearch.matches("", DOCTOR));
        assertTrue(OutfitSearch.matches("   ", MAFIA));
    }

    @Test
    void ignoresCaseAndAccents() {
        assertTrue(OutfitSearch.matches("medecin", DOCTOR));
        assertTrue(OutfitSearch.matches("MÉDECIN", DOCTOR));
        assertTrue(OutfitSearch.matches("maf", MAFIA));
    }

    @Test
    void frenchWordsFindEnglishNames() {
        assertTrue(OutfitSearch.matches("chapeau", MAFIA));
        assertTrue(OutfitSearch.matches("docteur", DOCTOR));
        assertTrue(OutfitSearch.matches("masque", DOCTOR));
        assertTrue(OutfitSearch.matches("chaussures", MAFIA));
        assertTrue(OutfitSearch.matches("chaussure", MAFIA), "singular finds the plural entry");
        assertFalse(OutfitSearch.matches("chapeau", DOCTOR));
    }

    @Test
    void everyWordMustMatch() {
        assertTrue(OutfitSearch.matches("mafia veste", MAFIA));
        assertFalse(OutfitSearch.matches("mafia couronne", MAFIA));
    }

    @Test
    void idsAreSearchedToo() {
        assertTrue(OutfitSearch.matches("doctor boots", DOCTOR));
        assertTrue(OutfitSearch.matches("selfexpression", DOCTOR));
    }

    @Test
    void translationsAreWholeWords() {
        assertFalse(OutfitSearch.matches("lutin", DOCTOR), "elf is not a word of selfexpression");
        assertFalse(OutfitSearch.matches("chapeau", "Capitaine Captain's jacket"), "cap is not a word of captain");
        assertTrue(OutfitSearch.matches("lutin", "Christmas elf's hat selfexpression:christmass_elf_helmet"));
        assertTrue(OutfitSearch.matches("botte", "Knight's boots"));
    }

    @Test
    void expressionsStayTogether() {
        assertTrue(OutfitSearch.matches("bonhomme de neige", "Snowman's head"));
        assertTrue(OutfitSearch.matches("noel", "Christmas elf's hat"));
    }
}
