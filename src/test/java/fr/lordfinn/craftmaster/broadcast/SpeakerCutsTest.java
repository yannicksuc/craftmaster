package fr.lordfinn.craftmaster.broadcast;

import fr.lordfinn.craftmaster.broadcast.SpeakerCuts.Decision;
import fr.lordfinn.craftmaster.broadcast.SpeakerCuts.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SpeakerCutsTest {

    /** Runs {@code ms} of time in 50 ms steps with {@code talking} heard, airing every decision; returns them. */
    private static List<Decision> run(SpeakerCuts c, long[] clock, long ms, String... talking) {
        List<Decision> out = new ArrayList<>();
        for (long t = 0; t < ms; t += 50) {
            clock[0] += 50;
            Decision d = c.update(clock[0], Set.of(talking));
            if (d.kind() != Kind.NONE) {
                c.aired(clock[0], d);
                out.add(d);
            }
        }
        return out;
    }

    @Test
    void aShortSoundDoesNotCut() {
        SpeakerCuts c = new SpeakerCuts();
        long[] t = {0};
        assertTrue(run(c, t, 350, "Ensuri").isEmpty());   // a cough, a « oui »
        assertTrue(run(c, t, 2000).isEmpty());
    }

    @Test
    void someoneTalkingGetsTheirShotOnce() {
        SpeakerCuts c = new SpeakerCuts();
        long[] t = {0};
        List<Decision> d = run(c, t, 8000, "Ensuri");
        assertEquals(1, d.size());
        assertEquals(new Decision(Kind.SOLO, List.of("Ensuri")), d.getFirst());
    }

    @Test
    void neverEverySecond() {
        // Two people alternating every second for 20 s: a handful of cuts at most, not twenty
        SpeakerCuts c = new SpeakerCuts();
        long[] t = {0};
        List<Decision> all = new ArrayList<>();
        for (int i = 0; i < 20; i++) all.addAll(run(c, t, 1000, i % 2 == 0 ? "Ensuri" : "Cyllu"));
        assertTrue(all.size() <= 4, "cuts: " + all);
        // and the back-and-forth ends on the two of them together
        assertEquals(new Decision(Kind.GROUP, List.of("Cyllu", "Ensuri")), all.getLast());
    }

    @Test
    void aConversationGoesToTheGroupShot() {
        SpeakerCuts c = new SpeakerCuts();
        long[] t = {0};
        run(c, t, 5000, "Ensuri");          // Ensuri's shot
        List<Decision> d = run(c, t, 5000, "Cyllu");  // Cyllu answers right away: both
        assertEquals(new Decision(Kind.GROUP, List.of("Cyllu", "Ensuri")), d.getFirst());
    }

    @Test
    void twoAtOnceIsAGroup() {
        SpeakerCuts c = new SpeakerCuts();
        long[] t = {0};
        List<Decision> d = run(c, t, 3000, "Ensuri", "Cyllu");
        assertEquals(Kind.GROUP, d.getFirst().kind());
    }

    @Test
    void aShotIsHeldBeforeTheNext() {
        SpeakerCuts c = new SpeakerCuts();
        long[] t = {0};
        run(c, t, 1200, "Ensuri");            // on Ensuri at ~0.5 s
        List<Decision> d = run(c, t, 1000, "Cyllu");  // Cyllu counts at ~1.7 s, but Ensuri's shot holds until 2.5 s
        assertTrue(d.isEmpty(), "no cut during the hold: " + d);
    }

    @Test
    void aNewSpeakerIsTakenQuickly() {
        // A voice is on air half a second after it starts
        SpeakerCuts c = new SpeakerCuts();
        long[] t = {0};
        List<Decision> first = run(c, t, 700, "Ensuri");
        assertEquals(new Decision(Kind.SOLO, List.of("Ensuri")), first.getFirst());
        // and someone who starts talking after the hold gets the shot within a second, a long silence later
        run(c, t, 8000);
        run(c, t, 3000);
        List<Decision> next = run(c, t, 1000, "Cyllu");
        assertEquals(new Decision(Kind.SOLO, List.of("Cyllu")), next.getFirst());
    }

    @Test
    void silenceGoesBackWide() {
        SpeakerCuts c = new SpeakerCuts();
        long[] t = {0};
        run(c, t, 4000, "Ensuri");
        List<Decision> d = run(c, t, SpeakerCuts.SILENCE + 1000);
        assertEquals(Kind.WIDE, d.getFirst().kind());
    }

    @Test
    void afterAnotherShotTheSpeakerComesBack() {
        SpeakerCuts c = new SpeakerCuts();
        long[] t = {0};
        run(c, t, 4000, "Ensuri");
        c.otherShot(t[0]);                     // someone scored: the director cut elsewhere
        // Back to a long silence then a new voice: a fresh solo, the old one forgotten
        run(c, t, 10000);
        List<Decision> d = run(c, t, 3000, "LordFinn");
        assertEquals(new Decision(Kind.SOLO, List.of("LordFinn")), d.getFirst());
    }
}
