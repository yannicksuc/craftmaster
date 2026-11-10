package fr.lordfinn.craftmaster.trial;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Results sheet: ranking with ties, scales, disqualifications, values typed or said, storage. */
class ResultSheetTest {

    private static final List<String> CAST = List.of("JustEdible", "Ensuri", "Cyllu", "Arreaubaz");

    private static ResultSheet.Placed of(List<ResultSheet.Placed> ranking, String player) {
        return ranking.stream().filter(p -> p.player().equals(player)).findFirst().orElseThrow();
    }

    @Test
    void timesRankSmallestFirstWithTheDefaultScale() {
        ResultSheet s = new ResultSheet();
        s.kind = ResultSheet.Kind.TIME;
        s.set("JustEdible", 61.2);
        s.set("Ensuri", 42);
        s.set("Cyllu", 55.5);
        s.set("Arreaubaz", 90);
        List<ResultSheet.Placed> r = s.rank(CAST);
        assertEquals(List.of("Ensuri", "Cyllu", "JustEdible", "Arreaubaz"), r.stream().map(ResultSheet.Placed::player).toList());
        assertEquals(List.of(5, 4, 3, 2), r.stream().map(ResultSheet.Placed::points).toList());
        assertEquals(List.of(1, 2, 3, 4), r.stream().map(ResultSheet.Placed::place).toList());
    }

    @Test
    void countsRankBiggestFirstAndOrderCanBeForced() {
        ResultSheet s = new ResultSheet();
        s.set("Ensuri", 3);
        s.set("Cyllu", 12);
        assertEquals("Cyllu", s.rank(CAST).getFirst().player());
        s.lowerWins = true;
        assertEquals("Ensuri", s.rank(CAST).getFirst().player());
    }

    @Test
    void tiesShareThePlaceAndItsPointsThenThePlaceIsSkipped() {
        ResultSheet s = new ResultSheet();
        s.set("JustEdible", 10);
        s.set("Ensuri", 10);
        s.set("Cyllu", 7);
        s.set("Arreaubaz", 7);
        List<ResultSheet.Placed> r = s.rank(CAST);
        assertEquals(1, of(r, "JustEdible").place());
        assertEquals(1, of(r, "Ensuri").place());
        assertEquals(5, of(r, "Ensuri").points());
        assertEquals(3, of(r, "Cyllu").place());
        assertEquals(3, of(r, "Arreaubaz").points());
        // Tied players keep the show order
        assertEquals(List.of("JustEdible", "Ensuri", "Cyllu", "Arreaubaz"), r.stream().map(ResultSheet.Placed::player).toList());
    }

    @Test
    void valuesAreComparedAtTheHundredth() {
        ResultSheet s = new ResultSheet();
        s.set("Ensuri", 4.001);
        s.set("Cyllu", 4.004);
        assertEquals(1, of(s.rank(CAST), "Cyllu").place());
        assertEquals(1, of(s.rank(CAST), "Ensuri").place());
    }

    @Test
    void disqualifiedGetZeroAndMissingGetNoProposal() {
        ResultSheet s = new ResultSheet();
        s.kind = ResultSheet.Kind.TIME;
        s.set("Ensuri", 30);
        s.set("Cyllu", 20);
        s.disqualify("Cyllu", true);
        List<ResultSheet.Placed> r = s.rank(CAST);
        assertEquals("Ensuri", r.getFirst().player());
        assertEquals(5, r.getFirst().points());
        ResultSheet.Placed dq = of(r, "Cyllu");
        assertTrue(dq.dq());
        assertEquals(0, dq.points());
        assertEquals(0, dq.place());
        assertNull(of(r, "JustEdible").points());
        // Lifting the disqualification gives the value back
        s.disqualify("Cyllu", false);
        assertEquals("Cyllu", s.rank(CAST).getFirst().player());
        // A disqualification without value, then lifted: the line disappears
        s.disqualify("Arreaubaz", true);
        assertTrue(s.entries.containsKey("Arreaubaz"));
        s.disqualify("Arreaubaz", false);
        assertFalse(s.entries.containsKey("Arreaubaz"));
    }

    @Test
    void customScaleAndPlacesPastItsEnd() {
        ResultSheet s = new ResultSheet();
        s.scale = "3,1";
        s.set("JustEdible", 4);
        s.set("Ensuri", 3);
        s.set("Cyllu", 2);
        List<ResultSheet.Placed> r = s.rank(CAST);
        assertEquals(List.of(3, 1, 0), r.subList(0, 3).stream().map(ResultSheet.Placed::points).toList());
    }

    @Test
    void someoneOutsideTheCastIsRankedToo() {
        ResultSheet s = new ResultSheet();
        s.set("Invité", 99);
        assertEquals("Invité", s.rank(CAST).getFirst().player());
    }

    @Test
    void scaleParsing() {
        assertArrayEquals(new int[]{5, 4, 3, 2, 1}, Scale.parse("5,4,3,2,1"));
        assertArrayEquals(new int[]{5, 4, 3, 2, 1}, Scale.parse("5-4-3-2-1"));
        assertArrayEquals(new int[]{10, 6, 3}, Scale.parse(" 10 ; 6 ;3 "));
        assertArrayEquals(new int[]{3, -1}, Scale.parse("3, -1"));
        assertEquals("5,3,1", Scale.normalize("5 3 1"));
        assertThrows(IllegalArgumentException.class, () -> Scale.parse(""));
        assertThrows(IllegalArgumentException.class, () -> Scale.parse("5,quatre"));
        assertEquals(0, Scale.pointsFor(new int[]{5, 4}, 3));
        assertEquals(0, Scale.pointsFor(new int[]{5, 4}, 0));
    }

    @Test
    void valuesTypedOrSaid() {
        assertEquals(42.0, ResultSheet.parse("42"));
        assertEquals(42.5, ResultSheet.parse("42,5"));
        assertEquals(42.5, ResultSheet.parse("42.5 s"));
        assertEquals(62.5, ResultSheet.parse("1:02.5"));
        assertEquals(90.0, ResultSheet.parse("1m30"));
        assertEquals(1.3, ResultSheet.parse(ResultSheet.Kind.DISTANCE, "1m30"), 1e-9);
        assertEquals(120.0, ResultSheet.parse("2 min"));
        assertEquals(12.0, ResultSheet.parse("12 m"));
        assertEquals(3.2, ResultSheet.parse("3,2 kg"));
        assertEquals(8.0, ResultSheet.parse("8/10"));
        assertEquals(3723.0, ResultSheet.parse("1:02:03"));
        assertNull(ResultSheet.parse("beaucoup"));
        assertNull(ResultSheet.parse(""));
    }

    @Test
    void valuesShown() {
        assertEquals("42,3 s", ResultSheet.format(ResultSheet.Kind.TIME, 42.3));
        assertEquals("1:02,5", ResultSheet.format(ResultSheet.Kind.TIME, 62.5));
        assertEquals("17", ResultSheet.format(ResultSheet.Kind.COUNT, 17));
        assertEquals("12,5 m", ResultSheet.format(ResultSheet.Kind.DISTANCE, 12.5));
        assertEquals("3,25 kg", ResultSheet.format(ResultSheet.Kind.WEIGHT, 3.25));
    }

    @Test
    void kindNames() {
        assertEquals(ResultSheet.Kind.TIME, ResultSheet.Kind.parse("temps"));
        assertEquals(ResultSheet.Kind.TIME, ResultSheet.Kind.parse("time"));
        assertEquals(ResultSheet.Kind.MARK, ResultSheet.Kind.parse("Note"));
        assertEquals(ResultSheet.Kind.WEIGHT, ResultSheet.Kind.parse("poids"));
        assertNull(ResultSheet.Kind.parse("couleur"));
    }

    @Test
    void storageRoundTrip() {
        ResultSheet s = new ResultSheet();
        s.kind = ResultSheet.Kind.DISTANCE;
        s.lowerWins = true;
        s.scale = "3,2,1";
        s.set("Ensuri", 12.5);
        s.disqualify("Cyllu", true);
        ResultSheet back = ResultSheet.fromYaml(s.toYaml());
        assertEquals(ResultSheet.Kind.DISTANCE, back.kind);
        assertEquals(Boolean.TRUE, back.lowerWins);
        assertEquals("3,2,1", back.scale);
        assertEquals(12.5, back.entries.get("Ensuri").value());
        assertTrue(back.entries.get("Cyllu").dq());
        assertNull(back.entries.get("Cyllu").value());
        assertTrue(new ResultSheet().isEmpty());
        assertTrue(ResultSheet.fromYaml(null).isEmpty());
    }
}
