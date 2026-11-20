package fr.lordfinn.craftmaster.broadcast;

import fr.lordfinn.craftmaster.data.IntroMusic;
import fr.lordfinn.craftmaster.data.MancheIntro;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The theme's intro musics: names, the automatic intro on their bars, the last shot on the « appel ». */
class IntroMusicTest {

    @Test
    void namesAndCues() {
        assertEquals("maladresse", IntroMusic.find("Maladresse").key());
        assertEquals("univers.japonais", IntroMusic.find("japonais").key());
        assertEquals("univers.japonais", IntroMusic.find("zen").key());
        assertEquals("univers.medieval", IntroMusic.find("médiéval").key());
        assertEquals("univers.grec", IntroMusic.find("craftmaster:theme.univers.grec").key());
        assertNull(IntroMusic.find("theme"), "the show's theme is not one of them");
        assertNull(IntroMusic.find("defaite"), "not taken");
        assertEquals("craftmaster:theme.maladresse", MancheIntro.normalizeMusic("maladresse"));
        assertEquals("craftmaster:theme.univers.western", MancheIntro.normalizeMusic("western"));
        assertEquals(MancheIntro.INTRO_MUSIC, MancheIntro.normalizeMusic("intro"));
        assertEquals("theme", MancheIntro.normalizeMusic("theme"));
        assertEquals("maladresse", IntroMusic.ofCue("craftmaster:theme.maladresse").key());
        assertEquals(12, IntroMusic.ALL.size() - 1);
        for (IntroMusic.Track t : IntroMusic.ALL) {
            assertTrue(t.appel() < t.chord() || t.key().equals(MancheIntro.INTRO_MUSIC), t.key());
            assertTrue(t.chord() < t.seconds(), t.key());
        }
    }

    @Test
    void autoIntroPutsTheTaskShotOnTheAppel() {
        for (IntroMusic.Track t : IntroMusic.ALL) {
            if (t.key().equals(MancheIntro.INTRO_MUSIC)) continue;
            MancheIntro in = MancheIntro.autoBuild("a:1", List.of("a:2", "a:3", "a:4", "a:5"), t);
            List<MancheIntro.Timed> tl = in.timeline(n -> 0);
            MancheIntro.Timed last = tl.getLast();
            assertEquals(MancheIntro.TASK, last.shot().kind(), t.key());
            assertEquals("fondu", last.shot().transition(), t.key());
            assertEquals(t.appel(), last.start(), 0.02, t.key() + " : the task location on the appel");
            assertEquals(t.seconds(), last.start() + last.length(), Math.max(0.3, in.beatSeconds() / 2 + 0.02), t.key() + " : as long as the music");
            assertEquals(t.sound(), in.music);
            assertTrue(tl.size() >= 3, t.key() + " : at least a wide shot, an insert and the task");
            for (int k = 0; k < tl.size() - 1; k++) {
                assertTrue(tl.get(k).length() >= IntroMusic.MIN_SHOT - 0.02, t.key() + " shot " + k + " too short: " + tl.get(k).length());
                if (k > 0) {
                    double cut = tl.get(k).start();
                    boolean onBar = false;
                    for (double b : t.bars()) onBar |= Math.abs(b - cut) < 0.02;
                    assertTrue(onBar, t.key() + " : cut " + cut + " on a bar");
                }
            }
            assertTrue(in.musicFit(n -> 0).startsWith("plan final sur l'appel"), t.key() + " : " + in.musicFit(n -> 0));
        }
        // No camera: the task location over the whole music
        MancheIntro bare = MancheIntro.autoBuild(null, List.of(), IntroMusic.find("maladresse"));
        assertEquals(1, bare.shots.size());
        assertEquals(17.2, bare.duration(n -> 0), 0.3);
        // The default: maladresse
        assertEquals("craftmaster:theme.maladresse", MancheIntro.autoBuild("a:1", List.of("a:2")).music);
    }

    @Test
    void fitMovesTheLastShotOnTheAppel() {
        MancheIntro in = new MancheIntro();
        in.music = MancheIntro.normalizeMusic("urgence");
        in.shots.add(new MancheIntro.Shot(MancheIntro.CAMERA, "1", 4, "coupe", "", "", ""));
        in.shots.add(new MancheIntro.Shot(MancheIntro.CAMERA, "2", 4, "coupe", "", "", ""));
        in.shots.add(new MancheIntro.Shot(MancheIntro.CAMERA, "3", 4, "coupe", "", "", ""));
        in.shots.add(new MancheIntro.Shot(MancheIntro.TASK, "", 2, "fondu", "", "", ""));
        assertNotNull(in.fitToMusic(n -> 0));
        IntroMusic.Track t = IntroMusic.find("urgence");
        assertEquals(144, in.bpm);
        List<MancheIntro.Timed> tl = in.timeline(n -> 0);
        assertEquals(t.appel(), tl.getLast().start(), 0.02);
        assertEquals(t.appel() / 3, tl.get(1).start(), 4 * 60 / 144.0, "proportions kept, on a bar");
        // Not one of the theme's: nothing done
        MancheIntro other = new MancheIntro();
        other.music = "theme";
        other.shots.add(new MancheIntro.Shot(MancheIntro.CAMERA, "1", 4, "coupe", "", "", ""));
        assertNull(other.fitToMusic(n -> 0));
    }

    /** Every cut but the last on a beat of the music, the last shot from the appel to the end. */
    private static void onTheMusic(MancheIntro in, IntroMusic.Track t) {
        double[] beats = t.beatTimes();
        List<MancheIntro.Timed> tl = in.timeline(n -> 0);
        for (int k = 0; k < tl.size() - 1; k++) {
            if (tl.get(k).shot().fixed()) continue;
            double cut = tl.get(k).start() + tl.get(k).length();
            double d = Double.MAX_VALUE;
            for (double b : beats) d = Math.min(d, Math.abs(b - cut));
            assertTrue(d < 0.02, t.key() + " : cut " + k + " at " + cut + " off the beat by " + d);
        }
        assertEquals(t.appel(), tl.getLast().start(), 0.02, t.key() + " : last shot on the appel");
        assertEquals(t.seconds(), tl.getLast().start() + tl.getLast().length(), Math.max(0.3, in.beatSeconds() / 2 + 0.02), t.key());
    }

    @Test
    void changingTheMusicRefitsTheMontage() {
        MancheIntro in = MancheIntro.autoBuild("a:1", List.of("a:2", "a:3", "a:4"), IntroMusic.find("maladresse"));
        // a focus pull on shot 2, at the middle of the shot
        MancheIntro.Shot two = in.shots.get(1);
        in.shots.set(1, two.withFocus(new fr.lordfinn.craftmaster.fx.FocusPull("centre", "fond", two.seconds() / 2, 0.5, "douce", 100, true).canonical()));
        onTheMusic(in, IntroMusic.find("maladresse"));
        for (String m : List.of("japonais", "grec", "steampunk", "medieval", "urgence", "maladresse")) {
            IntroMusic.Track t = IntroMusic.find(m);
            in.music = MancheIntro.normalizeMusic(m);
            assertNotNull(in.fitToMusic(n -> 0), m);
            assertEquals(t.bpm(), in.bpm, m);
            assertEquals(5, in.shots.size(), m + " : no shot lost");
            onTheMusic(in, t);
            // the focus pull moved with its shot, its start on a beat of the new music
            MancheIntro.Timed shot2 = in.timeline(n -> 0).get(1);
            fr.lordfinn.craftmaster.fx.FocusPull f = shot2.shot().focusPull();
            assertNotNull(f, m);
            double at = shot2.start() + f.delay(), d = Double.MAX_VALUE;
            for (double b : t.beatTimes()) d = Math.min(d, Math.abs(b - at));
            assertTrue(d < 0.02, m + " : focus at " + at + " off the beat");
            assertTrue(f.delay() <= shot2.length(), m);
        }
        // The Greek one's cuts follow its acceleration: its beats get closer
        double[] g = IntroMusic.find("grec").beatTimes();
        assertTrue(g[1] - g[0] > 2 * (g[g.length - 1] - g[g.length - 2]), "the Greek one speeds up");
    }

    @Test
    void aShotFixedByHandKeepsItsLengthAndIsReported() {
        MancheIntro in = MancheIntro.autoBuild("a:1", List.of("a:2", "a:3"), IntroMusic.find("maladresse"));
        in.shots.set(1, in.shots.get(1).withSeconds(3.0).withFixed(true));
        in.music = MancheIntro.normalizeMusic("japonais");
        String done = in.fitToMusic(n -> 0);
        assertNotNull(done);
        assertEquals(3.0, in.shots.get(1).seconds(), 1e-9, "kept");
        assertEquals(3.0, in.timeline(n -> 0).get(1).length(), 1e-9, "played as fixed");
        onTheMusic(in, IntroMusic.find("japonais"));
        assertFalse(in.offBeat(n -> 0).isEmpty(), "its cut off the beat is reported: " + in.offBeat(n -> 0));
        assertTrue(in.musicFit(n -> 0).contains("fixé à 3 s"), in.musicFit(n -> 0));
    }
}
