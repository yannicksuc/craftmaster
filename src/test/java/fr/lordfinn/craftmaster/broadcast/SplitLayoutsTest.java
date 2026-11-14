package fr.lordfinn.craftmaster.broadcast;

import fr.lordfinn.craftmaster.show.Actions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitLayoutsTest {

    private static final float W = 480, H = 270;

    @Test
    void namesSaidOrClicked() {
        assertEquals(SplitLayouts.FRAMES, SplitLayouts.parse("Cadres"));
        assertEquals(SplitLayouts.FRAMES, SplitLayouts.parse("côte à côte"));
        assertEquals(SplitLayouts.EDGE, SplitLayouts.parse("bord à bord"));
        assertEquals(SplitLayouts.EDGE, SplitLayouts.parse("coupé en deux"));
        assertEquals(SplitLayouts.MAIN, SplitLayouts.parse("principal"));
        assertNull(SplitLayouts.parse("n'importe quoi"));
        // Replays recorded before the layouts: "" in the shot = framed
        assertEquals(SplitLayouts.FRAMES, SplitLayouts.of(""));
    }

    @Test
    void everyLayoutHasOneTilePerShotInsideTheScreen() {
        for (String l : SplitLayouts.ALL)
            for (int n = 2; n <= SplitLayouts.MAX; n++) {
                List<float[]> tiles = SplitLayouts.layout(l, n, W, H);
                assertEquals(n, tiles.size(), l + " " + n);
                for (int i = 0; i < n; i++) {
                    float[] r = tiles.get(i);
                    assertTrue(r[0] >= -0.01f && r[1] >= -0.01f && r[2] <= W + 0.01f && r[3] <= H + 0.01f, l + " " + n + " tile " + i + " outside");
                    assertTrue(r[4] >= 0 && r[5] >= 0 && r[6] <= 1 && r[7] <= 1 && r[6] > r[4] && r[7] > r[5], l + " " + n + " crop");
                    for (int j = 0; j < i; j++) assertTrue(!overlap(r, tiles.get(j)), l + " " + n + ": tiles " + j + " and " + i + " overlap");
                }
            }
        assertTrue(SplitLayouts.layout(SplitLayouts.FRAMES, 1, W, H).isEmpty());
        assertEquals(SplitLayouts.MAX, SplitLayouts.layout(SplitLayouts.FRAMES, 9, W, H).size());
    }

    @Test
    void framesKeepTheScreenShapeAndEdgeShowsTheMiddleWithoutStretching() {
        for (float[] r : SplitLayouts.layout(SplitLayouts.FRAMES, 5, W, H)) assertEquals(W / H, (r[2] - r[0]) / (r[3] - r[1]), 0.01);
        // Two halves of the whole picture: each shows the middle of its shot, at the half's proportions
        List<float[]> halves = SplitLayouts.layout(SplitLayouts.EDGE, 2, W, H);
        float[] left = halves.getFirst();
        assertEquals(0, left[0], 0.01);
        assertEquals(H, left[3], 0.01);
        float cell = (left[2] - left[0]) / (left[3] - left[1]), shown = (left[6] - left[4]) * W / ((left[7] - left[5]) * H);
        assertEquals(cell, shown, 0.01);
        assertEquals(0.5, (left[4] + left[6]) / 2, 0.001);
    }

    @Test
    void mainShotIsTheLargestAndTheOthersAreBelow() {
        List<float[]> t = SplitLayouts.layout(SplitLayouts.MAIN, 4, W, H);
        float mainArea = area(t.getFirst());
        for (int i = 1; i < t.size(); i++) {
            assertTrue(area(t.get(i)) < mainArea / 4);
            assertTrue(t.get(i)[1] >= t.getFirst()[3]);
        }
    }

    @Test
    void followTilesHaveTheirOwnKeys() {
        assertEquals("follow:Ensuri", SplitLayouts.followKey("Ensuri"));
        assertEquals("Ensuri", SplitLayouts.followed("follow:Ensuri"));
        assertNull(SplitLayouts.followed("studio:2"));
        assertNull(SplitLayouts.followed("follow:"));
    }

    @Test
    void splitActionIsChecked() {
        Map<String, Object> a = BroadcastActions.validate("camera.split", Map.of("cameras", "3, 5", "layout", "bord à bord"));
        assertEquals(List.of("3", "5"), a.get("cameras"));
        assertEquals(SplitLayouts.EDGE, a.get("layout"));
        assertEquals(List.of("2", "galerie"), BroadcastActions.validate("camera.split", Map.of("cameras", List.of(2.0, "galerie"))).get("cameras"));
        assertEquals("2", BroadcastActions.validate("camera.split", Map.of("main", 2)).get("main"));
        assertEquals(false, BroadcastActions.validate("camera.split", Map.of("enabled", "non")).get("enabled"));
        assertThrows(Actions.Invalid.class, () -> BroadcastActions.validate("camera.split", Map.of()));
        assertThrows(Actions.Invalid.class, () -> BroadcastActions.validate("camera.split", Map.of("layout", "zigzag")));
        assertEquals(true, BroadcastActions.validate("camera.cut", Map.of("camera", "3", "pair", true)).get("pair"));
    }

    private static boolean overlap(float[] a, float[] b) {
        return a[0] < b[2] - 0.01f && b[0] < a[2] - 0.01f && a[1] < b[3] - 0.01f && b[1] < a[3] - 0.01f;
    }

    private static float area(float[] r) {
        return (r[2] - r[0]) * (r[3] - r[1]);
    }
}
