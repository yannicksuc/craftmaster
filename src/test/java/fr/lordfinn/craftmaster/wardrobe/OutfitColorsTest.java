package fr.lordfinn.craftmaster.wardrobe;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutfitColorsTest {

    @Test
    void namedColours() {
        assertEquals("Rouge", OutfitColors.classify(0xB02E26));
        assertEquals("Bleu", OutfitColors.classify(0x3C44AA));
        assertEquals("Bleu", OutfitColors.classify(0x2F6FC9));
        assertEquals("Jaune", OutfitColors.classify(0xFED83D));
        assertEquals("Jaune", OutfitColors.classify(0xE8C020));
        assertEquals("Vert", OutfitColors.classify(0x5E7C16));
        assertEquals("Vert", OutfitColors.classify(0x80C71F));
        assertEquals("Orange", OutfitColors.classify(0xF9801D));
        assertEquals("Marron", OutfitColors.classify(0x835432));
        assertEquals("Marron", OutfitColors.classify(0x5A3A22));
        assertEquals("Violet", OutfitColors.classify(0x8932B8));
        assertEquals("Rose", OutfitColors.classify(0xF38BAA));
        assertEquals("Cyan", OutfitColors.classify(0x169C9C));
        assertEquals("Noir", OutfitColors.classify(0x1D1D21));
        assertEquals("Blanc", OutfitColors.classify(0xF9FFFE));
        assertEquals("Gris", OutfitColors.classify(0x8A8A8A));
    }

    /** A 4×4 icon: an outline of black around 2×2 inner pixels. */
    private static int[] icon(int inner1, int inner2) {
        int o = 0xFF101010;
        return new int[]{
                0, o, o, 0,
                o, 0xFF000000 | inner1, 0xFF000000 | inner2, o,
                o, 0xFF000000 | inner1, 0xFF000000 | inner2, o,
                0, o, o, 0};
    }

    @Test
    void outlineIgnored() {
        assertEquals(List.of("Jaune"), OutfitColors.ofImage(icon(0xFED83D, 0xFED83D), 4, 4));
        List<String> two = OutfitColors.ofImage(icon(0x3C44AA, 0x835432), 4, 4);
        assertTrue(two.contains("Bleu") && two.contains("Marron"));
        assertFalse(two.contains("Noir"));
    }

    @Test
    void smallDetailsDoNotCount() {
        int[] img = new int[100];
        for (int i = 0; i < 100; i++) img[i] = 0xFF3C44AA;
        img[55] = 0xFFFED83D;   // one yellow button
        assertEquals(List.of("Bleu"), OutfitColors.ofImage(img, 10, 10));
    }

    @Test
    void transparentImage() {
        assertTrue(OutfitColors.ofImage(new int[16], 4, 4).isEmpty());
    }
}
