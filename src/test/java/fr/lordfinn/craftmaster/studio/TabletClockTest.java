package fr.lordfinn.craftmaster.studio;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static fr.lordfinn.craftmaster.studio.TabletClock.Format.*;
import static org.junit.jupiter.api.Assertions.*;

class TabletClockTest {

    @Test
    void timerCountsDownPausesAndResumes() {
        TabletClock.Settings s = new TabletClock.Settings();
        s.seconds = 30;
        TabletClock c = new TabletClock();
        assertEquals(30_000, c.shown(s, 0));
        c.start(1000);
        assertEquals(25_000, c.shown(s, 6000));
        c.pause(6000);
        assertEquals(25_000, c.shown(s, 60_000), "paused: frozen");
        c.start(60_000);
        assertEquals(20_000, c.shown(s, 65_000));
        assertFalse(c.done(s, 65_000));
        assertTrue(c.done(s, 99_000));
        assertEquals(0, c.shown(s, 99_000), "never below 0");
        c.reset();
        assertEquals(30_000, c.shown(s, 99_000));
    }

    @Test
    void stopwatchCountsUp() {
        TabletClock.Settings s = new TabletClock.Settings();
        s.mode = TabletClock.Mode.STOPWATCH;
        TabletClock c = new TabletClock();
        c.restart(0);
        assertEquals(25_700, c.shown(s, 25_700));
        assertFalse(c.done(s, 10_000_000));
    }

    @Test
    void formats() {
        assertEquals("25.7", TabletClock.format(25_700, TENTHS, false));
        assertEquals("25.7", TabletClock.format(25_610, TENTHS, true), "a countdown rounds up");
        assertEquals("3:07", TabletClock.format(187_000, MINUTES, false));
        assertEquals("1:02:05", TabletClock.format(3_725_000, MINUTES, false));
        assertEquals("1:07.42", TabletClock.format(67_420, HUNDREDTHS, false));
        assertEquals("187", TabletClock.format(187_400, SECONDS, false));
        assertEquals("188", TabletClock.format(187_400, SECONDS, true));
        // AUTO: tenths in the last 10 s of a timer, 0:01 until the real end
        assertEquals("9.5", TabletClock.format(9_420, AUTO, true));
        assertEquals("0:11", TabletClock.format(10_200, AUTO, true));
        assertEquals("0.0", TabletClock.format(0, AUTO, true));
        // ... and tenths in the first minute of a stopwatch
        assertEquals("25.7", TabletClock.format(25_700, AUTO, false));
        assertEquals("1:05", TabletClock.format(65_000, AUTO, false));
        assertEquals("14:05", TabletClock.clock(LocalTime.of(14, 5, 9), AUTO));
        assertEquals("14:05:09", TabletClock.clock(LocalTime.of(14, 5, 9), TENTHS));
    }

    @Test
    void durationsAreReadLikeTheyAreTyped() {
        assertEquals(90, TabletClock.parseDuration("1:30"));
        assertEquals(90, TabletClock.parseDuration("90"));
        assertEquals(3725, TabletClock.parseDuration("1:02:05"));
        assertEquals(-1, TabletClock.parseDuration("abc"));
        assertEquals(-1, TabletClock.parseDuration("0"));
    }

    @Test
    void settingsSurviveJsonAndAreClamped() {
        TabletClock.Settings s = new TabletClock.Settings();
        s.mode = TabletClock.Mode.CLOCK;
        s.group = "  Plateau ";
        s.size = 9;
        TabletClock.Settings back = TabletClock.Settings.fromJson(s.toJson());
        assertEquals(TabletClock.Mode.CLOCK, back.mode);
        assertEquals("plateau", back.group);
        assertEquals(4, back.size);
        assertEquals(6, TabletClock.Settings.fromJson(s.toJson(), 6).size, "giant timer: up to 6");
        TabletClock.Settings bad = TabletClock.Settings.fromJson("{\"mode\":\"NOPE\",\"seconds\":-5}");
        assertEquals(TabletClock.Mode.TIMER, bad.mode, "unknown value → default");
        assertEquals(1, bad.seconds);
        assertEquals(TabletClock.Mode.TIMER, TabletClock.Settings.fromJson("not json").mode);
    }

    @Test
    void setCopiesAnotherTabletsState() {
        TabletClock c = new TabletClock();
        c.set(true, 5000, 10_000);
        assertEquals(7000, c.elapsed(12_000));
        c.set(false, 4000, 10_000);
        assertEquals(4000, c.elapsed(50_000));
    }
}
