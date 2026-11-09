package fr.lordfinn.craftmaster.portrait;

import fr.lordfinn.craftmaster.portrait.GalleryPlanner.Placement;
import fr.lordfinn.craftmaster.portrait.GalleryPlanner.Slot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class GalleryPlannerTest {

    private static List<Slot> ranked(String... shown) {
        List<Slot> s = new ArrayList<>();
        for (int i = 0; i < shown.length; i++) s.add(new Slot(i, null, shown[i]));
        return s;
    }

    private static int[] targets(List<Placement> plan) {
        return plan.stream().mapToInt(Placement::toColumn).toArray();
    }

    /** Two movers of the same plane never swap their order (so they and their strings never meet). */
    private static void assertNoCrossing(List<Placement> plan) {
        for (int a = 0; a < plan.size(); a++) {
            for (int b = a + 1; b < plan.size(); b++) {
                Placement pa = plan.get(a), pb = plan.get(b);
                if (pa.plane() == 0 || pa.plane() != pb.plane()) continue;
                assertTrue((pa.fromColumn() - pb.fromColumn()) * (pa.toColumn() - pb.toColumn()) > 0,
                        "slots " + a + " and " + b + " cross in plane " + pa.plane());
            }
        }
        for (Placement p : plan) {
            if (!p.moves()) assertEquals(0, p.plane(), "a portrait that stays stays in the row");
            else if (p.toColumn() < p.fromColumn()) assertTrue(p.plane() > 0, "going left = stepping forward");
            else assertTrue(p.plane() < 0, "going right = stepping back");
        }
    }

    private static void assertPermutation(List<Placement> plan) {
        List<Integer> t = new ArrayList<>(plan.stream().map(Placement::toColumn).toList());
        Collections.sort(t);
        for (int i = 0; i < t.size(); i++) assertEquals(i, t.get(i));
    }

    @Test
    void unchangedOrderDoesNotMove() {
        List<Placement> plan = GalleryPlanner.plan(ranked("A", "B", "C"), List.of("A", "B", "C"));
        assertArrayEquals(new int[]{0, 1, 2}, targets(plan));
        plan.forEach(p -> assertEquals(0, p.plane()));
        assertEquals(List.of(1, 2, 3), plan.stream().map(Placement::rank).toList());
    }

    @Test
    void portraitsCarryTheirPlayerToItsNewColumn() {
        // C climbs from 3rd to 1st, A and B go down one place
        List<Placement> plan = GalleryPlanner.plan(ranked("A", "B", "C"), List.of("C", "A", "B"));
        assertArrayEquals(new int[]{1, 2, 0}, targets(plan));
        assertEquals("A", plan.get(0).player());
        assertEquals(2, plan.get(0).rank());
        assertEquals("C", plan.get(2).player());
        assertEquals(1, plan.get(2).rank());
        assertTrue(plan.get(2).plane() > 0, "C goes left, in front");
        assertTrue(plan.get(0).plane() < 0 && plan.get(1).plane() < 0, "A and B go right, behind");
        assertNoCrossing(plan);
    }

    @Test
    void sameDirectionSwapsUseTwoPlanes() {
        // A B C D -> D C A B: D and C both go left and swap their order
        List<Placement> plan = GalleryPlanner.plan(ranked("A", "B", "C", "D"), List.of("D", "C", "A", "B"));
        assertArrayEquals(new int[]{2, 3, 1, 0}, targets(plan));
        assertNotEquals(plan.get(2).plane(), plan.get(3).plane());
        assertNoCrossing(plan);
    }

    @Test
    void fullReversalNeverCrosses() {
        List<Placement> plan = GalleryPlanner.plan(ranked("A", "B", "C", "D", "E"), List.of("E", "D", "C", "B", "A"));
        assertArrayEquals(new int[]{4, 3, 2, 1, 0}, targets(plan));
        assertEquals(0, plan.get(2).plane(), "the middle one stays");
        assertNoCrossing(plan);
    }

    @Test
    void randomOrdersNeverCross() {
        Random rnd = new Random(42);
        for (int round = 0; round < 500; round++) {
            int n = 1 + rnd.nextInt(9);
            List<String> players = new ArrayList<>();
            for (int i = 0; i < n; i++) players.add("P" + i);
            List<String> ranking = new ArrayList<>(players);
            Collections.shuffle(ranking, rnd);
            List<Placement> plan = GalleryPlanner.plan(ranked(players.toArray(String[]::new)), ranking);
            assertPermutation(plan);
            assertNoCrossing(plan);
            for (Placement p : plan) assertEquals(p.player(), ranking.get(p.toColumn()), "column c shows rank c+1");
        }
    }

    @Test
    void newPlayersTakeTheFreedPortraitsAndEmptyOnesGoLast() {
        // Only two players scored: the third portrait shows nobody and stays at the end
        List<Placement> plan = GalleryPlanner.plan(ranked(null, null, null), List.of("A", "B"));
        assertEquals("A", plan.get(0).player());
        assertEquals("B", plan.get(1).player());
        assertNull(plan.get(2).player());
        assertEquals(0, plan.get(2).rank());
        assertArrayEquals(new int[]{0, 1, 2}, targets(plan));

        // X dropped out of the top 3, Z entered: X's portrait (column 0) is given to Z and goes where Z ranks
        plan = GalleryPlanner.plan(ranked("X", "A", "B"), List.of("A", "B", "Z", "X"));
        assertEquals("Z", plan.get(0).player());
        assertArrayEquals(new int[]{2, 0, 1}, targets(plan));
        assertNoCrossing(plan);
    }

    @Test
    void namesAreCaseInsensitive() {
        List<Placement> plan = GalleryPlanner.plan(ranked("lordfinn", "Ensuri"), List.of("ensuri", "LordFinn"));
        assertArrayEquals(new int[]{1, 0}, targets(plan));
        assertEquals("LordFinn", plan.get(0).player(), "the ranking's spelling is kept");
    }

    @Test
    void fixedPortraitsKeepTheirPlayer() {
        // Column 1 always shows Cyllu; the others follow the ranks among the remaining players
        List<Slot> slots = List.of(new Slot(0, null, "A"), new Slot(1, "Cyllu", "Cyllu"), new Slot(2, null, "B"));
        List<Placement> plan = GalleryPlanner.plan(slots, List.of("B", "Cyllu", "A"));
        assertEquals("Cyllu", plan.get(1).player());
        assertEquals(2, plan.get(1).rank());
        assertArrayEquals(new int[]{2, 1, 0}, targets(plan));
        assertNoCrossing(plan);

        // A fixed player without a score goes after the ranked ones
        plan = GalleryPlanner.plan(List.of(new Slot(0, "Guest", null), new Slot(1, null, null)), List.of("A"));
        assertEquals("Guest", plan.get(0).player());
        assertEquals(0, plan.get(0).rank());
        assertArrayEquals(new int[]{1, 0}, targets(plan));
    }

    @Test
    void duplicatedPlayerIsShownOnce() {
        List<Placement> plan = GalleryPlanner.plan(ranked("A", "A"), List.of("A", "B"));
        assertEquals("A", plan.get(0).player());
        assertEquals("B", plan.get(1).player());
    }

    @Test
    void planesAreMinimal() {
        // Two movers each way that swap: two planes in front, two behind
        int[] planes = GalleryPlanner.planes(new int[]{3, 2, 1, 0});
        assertArrayEquals(new int[]{-1, -2, 1, 2}, planes);
        // 3 movers each way in a full chain of swaps need 3 planes, not more
        int[] p = GalleryPlanner.planes(new int[]{5, 4, 3, 2, 1, 0});
        assertEquals(3, java.util.Arrays.stream(p).max().orElse(0));
        assertEquals(-3, java.util.Arrays.stream(p).min().orElse(0));
    }
}
