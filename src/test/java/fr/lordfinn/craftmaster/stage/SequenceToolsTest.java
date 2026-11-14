package fr.lordfinn.craftmaster.stage;

import fr.lordfinn.craftmaster.studio.SpotlightEffects;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SequenceToolsTest {

    @Test
    void mirrorNamesSwapTheStageSides() {
        assertEquals("face droite", SequenceTools.mirrorName("face gauche"));
        assertEquals("contre jardin", SequenceTools.mirrorName("contre cour"));
        assertEquals("faced", SequenceTools.mirrorName("faceg"));
        assertEquals("spot_r", SequenceTools.mirrorName("spot_l"));
        assertEquals("droite2", SequenceTools.mirrorName("gauche2"));
        assertEquals("poursuite", SequenceTools.mirrorName("poursuite"));
        assertEquals("tout", SequenceTools.mirrorName("tout"));
    }

    @Test
    void mirrorYawReflectsAroundTheAudiencesGaze() {
        assertEquals(-30, SequenceTools.mirrorYaw(30, 0), 1e-4);
        assertEquals(120, SequenceTools.mirrorYaw(60, 90), 1e-4);
        assertEquals(180, SequenceTools.mirrorYaw(180, 0), 1e-4);
    }

    @Test
    void mirroredTrackKeepsKeysAndSwapsTarget() {
        Sequence.Track tr = new Sequence.Track(Sequence.LIGHT, "face gauche");
        Sequence.Key k = new Sequence.Key(2);
        k.horizontal = 20f;
        k.vertical = 10f;
        k.vise = "@point:trone gauche";
        tr.cles.add(k);
        Sequence.Track m = SequenceTools.mirror(tr, 0, null);
        assertEquals("face droite", m.cible);
        assertEquals(-20, m.cles.getFirst().horizontal, 1e-4);
        assertEquals(10, m.cles.getFirst().vertical, 1e-4);
        assertEquals("@point:trone droite", m.cles.getFirst().vise);
        assertEquals(20, tr.cles.getFirst().horizontal, 1e-4, "the original is untouched");
    }

    @Test
    void reversePlaysChangesFromTheEnd() {
        Sequence s = new Sequence();
        s.duree = 10;
        Sequence.Track tr = new Sequence.Track(Sequence.LIGHT, "tout");
        Sequence.Key a = new Sequence.Key(0);
        a.fondu = 2;
        Sequence.Key b = new Sequence.Key(7);
        tr.cles.add(a);
        tr.cles.add(b);
        s.pistes.add(tr);
        SequenceTools.reverse(s);
        assertEquals(3, tr.cles.get(0).t, 1e-9);
        assertEquals(8, tr.cles.get(1).t, 1e-9, "the transition now ends where it started");
    }

    @Test
    void pasteKeepsSpacingFromThePlayhead() {
        Sequence.Key a = new Sequence.Key(1), b = new Sequence.Key(2.5);
        List<Sequence.Key> out = SequenceTools.pasteAt(List.of(a, b), 6, 10);
        assertEquals(6, out.get(0).t, 1e-9);
        assertEquals(7.5, out.get(1).t, 1e-9);
        assertNotSame(a, out.get(0));
        assertEquals(10, SequenceTools.pasteAt(List.of(a, b), 9.5, 10).get(1).t, 1e-9, "kept inside the timeline");
    }

    @Test
    void stretchScalesTimesAndTransitions() {
        Sequence s = new Sequence();
        s.duree = 10;
        Sequence.Track tr = new Sequence.Track(Sequence.CURTAIN, "fond");
        Sequence.Key k = new Sequence.Key(4);
        k.fondu = 1;
        tr.cles.add(k);
        s.pistes.add(tr);
        SequenceTools.stretch(s, 2);
        assertEquals(20, s.duree, 1e-9);
        assertEquals(8, k.t, 1e-9);
        assertEquals(2, k.fondu, 1e-9);
    }

    @Test
    void laterKeysOverrideEarlierOnesWhenSeeking() {
        Sequence.Key a = new Sequence.Key(0);
        a.couleur = "rouge";
        a.intensite = 50;
        a.suit = "@scene";
        Sequence.Key b = new Sequence.Key(3);
        b.intensite = 90;
        b.horizontal = 10f;
        b.vertical = 20f;
        Sequence.Key m = Sequencer.merge(a, b);
        assertEquals("rouge", m.couleur);
        assertEquals(90, m.intensite);
        assertNull(m.suit, "a fixed aim replaces the follow");
        assertEquals(10, m.horizontal, 1e-4);
    }

    @Test
    void handWrittenJsonIsCleaned() {
        Sequence s = Sequences.parse("{\"duree\": 5, \"pistes\": [{\"type\": \"RIDEAU\", \"cible\": \"fond\", \"cles\": [{\"t\": 9, \"ouvert\": true}, {\"t\": 1, \"ouvert\": false}]}, {\"type\": \"bidule\"}]}");
        assertEquals(Sequence.CURTAIN, s.pistes.get(0).type);
        assertEquals(1, s.pistes.get(0).cles.get(0).t, 1e-9, "sorted");
        assertEquals(5, s.pistes.get(0).cles.get(1).t, 1e-9, "clamped to the length");
        assertEquals(Sequence.LIGHT, s.pistes.get(1).type, "unknown type: a light track");
        assertEquals("", s.pistes.get(1).cible);
    }

    @Test
    void effectsStayBetweenZeroAndOne() {
        for (String fx : Sequence.EFFECTS)
            for (double t = 0; t < 10; t += 0.037) {
                float f = SpotlightEffects.factor(fx.equals("aucun") ? "" : fx, 1.3f, 0.25f, t);
                assertTrue(f >= 0 && f <= 1.0001f, fx + " at " + t + " = " + f);
            }
        assertEquals(1, SpotlightEffects.factor("", 1, 0, 3), 1e-6);
    }
}
