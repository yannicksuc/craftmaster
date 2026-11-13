package fr.lordfinn.craftmaster.wardrobe;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutfitTagsTest {

    private static boolean has(List<OutfitTags.Tag> tags, String category, String name) {
        return tags.contains(new OutfitTags.Tag(category, name));
    }

    @Test
    void jobsThemesAndKindsFromEnglishNames() {
        var t = OutfitTags.auto("Médecin Medical mask Doctor's robe Doctor's trousers Doctor's boots");
        assertTrue(has(t, "Métier", "Médecin"));
        assertTrue(has(t, "Contexte", "Hôpital"));
        assertTrue(has(t, "Type", "Robe"));
        assertTrue(has(t, "Type", "Masque"));
        assertTrue(has(t, "Type", "Chaussures"));
        assertFalse(has(t, "Type", "Armure"));
    }

    @Test
    void coloursAndThemes() {
        var t = OutfitTags.auto("Red empire clothes");
        assertTrue(has(t, "Couleur", "Rouge"));
        assertTrue(has(t, "Thème", "Empire"));
        assertTrue(has(t, "Matière", "Tissu"));
        var k = OutfitTags.auto("Medieval king's crown Medieval king's mantle");
        assertTrue(has(k, "Thème", "Médiéval"));
        assertTrue(has(k, "Métier", "Royauté"));
        assertTrue(has(k, "Contexte", "Château"));
    }

    @Test
    void wholeWordsOnly() {
        assertFalse(has(OutfitTags.auto("Scarlet scarf"), "Type", "Chapeau"), "cap is not in scarlet");
        assertTrue(has(OutfitTags.auto("Captain's cap"), "Type", "Chapeau"));
    }

    @Test
    void bodyParts() {
        var b = OutfitTags.body(new boolean[]{true, true, true, true});
        assertEquals(5, b.size());
        assertTrue(has(b, OutfitTags.BODY, "Tenue complète"));
        var p = OutfitTags.body(new boolean[]{false, true, true, false});
        assertEquals(List.of(new OutfitTags.Tag(OutfitTags.BODY, "Corps"), new OutfitTags.Tag(OutfitTags.BODY, "Jambes")), p);
    }

    @Test
    void organiserTags() {
        assertEquals(List.of("épreuve 3", "Rouge"), OutfitTags.parse(" épreuve 3, Rouge ,, rouge #"));
        var t = OutfitTags.of("White sweater", new boolean[]{false, true, false, false}, List.of("Défilé"));
        assertTrue(has(t, OutfitTags.CUSTOM, "Défilé"));
        assertTrue(has(t, "Couleur", "Blanc"));
        assertTrue(has(t, "Type", "Sweat"));
    }
}
