package fr.lordfinn.craftmaster.fx;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FocusPullTest {

    private static final FocusPull.Picker PICKER = new FocusPull.Picker() {
        @Override
        public String point() {
            return "p:10.5,64,-3";
        }

        @Override
        public String entity() {
            return "e:00000000-0000-0000-0000-000000000001";
        }

        @Override
        public String player(String name) {
            return name.equals("ensuri") ? "j:Ensuri" : null;
        }
    };

    @Test
    void wordsSetTargetsTimingAndBlur() {
        // « Sur la fleur à gauche puis la salle »: aim at the flower (ici), then the background
        FocusPull f = FocusPull.parseWords("de ici vers fond duree 2t delai 0.5 flou 150 courbe sortie garder non", null, 0.5, PICKER);
        assertEquals("p:10.5,64,-3", f.from());
        assertEquals(FocusPull.BACKGROUND, f.to());
        assertEquals(1.0, f.duration(), 1e-9, "2 beats at 120 BPM");
        assertEquals(0.5, f.delay(), 1e-9);
        assertEquals(150, f.blur());
        assertEquals("sortie", f.easing());
        assertFalse(f.keep());
        // Round trip through the stored text
        assertEquals(f, FocusPull.parseCanonical(f.canonical()));

        FocusPull g = FocusPull.parseWords("de 3 blocs vers 12.5 82 -7", null, 0, PICKER);
        assertEquals("d:3", g.from());
        assertEquals("p:12.5,82,-7", g.to());
        assertEquals("e:00000000-0000-0000-0000-000000000001", FocusPull.parseWords("vers entite", null, 0, PICKER).to());
        assertEquals("j:Ensuri", FocusPull.parseWords("vers ensuri", null, 0, PICKER).to());
        // Only what is typed changes
        FocusPull h = FocusPull.parseWords("flou 50", g, 0, PICKER);
        assertEquals("d:3", h.from());
        assertEquals(50, h.blur());
    }

    @Test
    void mistakesAreExplained() {
        assertThrows(IllegalArgumentException.class, () -> FocusPull.parseWords("vers lune", null, 0, PICKER));
        assertThrows(IllegalArgumentException.class, () -> FocusPull.parseWords("duree 2t", null, 0, PICKER));
        assertThrows(IllegalArgumentException.class, () -> FocusPull.parseWords("courbe zigzag", null, 0, PICKER));
        assertThrows(IllegalArgumentException.class, () -> FocusPull.parseWords("flou 900", null, 0, PICKER));
        assertThrows(IllegalArgumentException.class, () -> FocusPull.parseWords("de ici", null, 0, null));
    }

    @Test
    void progressEasesAndTheBlurCanFadeAway() {
        FocusPull f = new FocusPull("centre", "fond", 1, 2, "lineaire", 100, false);
        assertEquals(0, f.progress(0.5), 1e-9);
        assertEquals(0.5, f.progress(2), 1e-9);
        assertEquals(1, f.progress(5), 1e-9);
        assertEquals(1, f.strength(3), 1e-9);
        assertEquals(0, f.strength(3.6), 1e-9);
        assertTrue(new FocusPull("centre", "fond", 0, 1, "lisse", 100, true).strength(99) > 0.99);
    }

    @Test
    void theFocusRidesAfterTheEffects() {
        FocusPull f = FocusPull.preset("mise_au_point_inverse");
        String spec = FocusPull.attach("macro+look_intro", f);
        assertEquals("macro+look_intro", FocusPull.effectsOf(spec));
        assertEquals(f, FocusPull.of(spec));
        assertNull(FocusPull.of("macro"));
        assertEquals(List.of("macro", "look_intro"), ScreenFx.parseLenient(spec));
    }
}
