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
        assertEquals(t.appel() / 3, tl.get(1).start(), 60 / 144.0, "proportions kept, on a beat");
        // Not one of the theme's: nothing done
        MancheIntro other = new MancheIntro();
        other.music = "theme";
        other.shots.add(new MancheIntro.Shot(MancheIntro.CAMERA, "1", 4, "coupe", "", "", ""));
        assertNull(other.fitToMusic(n -> 0));
    }
}
