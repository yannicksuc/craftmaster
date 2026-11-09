package fr.lordfinn.craftmaster.portrait;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Who goes where in a gallery when scores are published. Pure logic, unit-tested.
 * <p>
 * A gallery is the row of portraits of one season; its columns are the places the portraits hang at, from left
 * to right as seen by the audience, and column 0 is rank 1. A portrait either follows a rank (it carries its
 * player along when the order changes, and takes the rank that player now has) or a fixed player.
 * <p>
 * Moving portraits never cross: those going left step forward, those going right step back, and if two of them
 * going the same way would swap places they use two different planes (planes 1, 2… in front, -1, -2… behind).
 * With every slide sharing the same start, duration and easing, two portraits of a plane keep their order, so
 * neither they nor their strings ever meet.
 */
public final class GalleryPlanner {

    private GalleryPlanner() {}

    /**
     * A portrait as it is now: its column (index in the gallery), the fixed player it follows (null: it follows a
     * rank) and the player it shows (null: nobody).
     */
    public record Slot(int column, String fixedPlayer, String shownPlayer) {}

    /**
     * Where a portrait goes: from / to column, the player it now shows (null: nobody), that player's rank (0: not
     * ranked) and the plane it slides in (0: it does not move).
     */
    public record Placement(int fromColumn, int toColumn, String player, int rank, int plane) {
        public boolean moves() {
            return fromColumn != toColumn;
        }
    }

    private static String key(String player) {
        return player == null ? null : player.toLowerCase(Locale.ROOT);
    }

    /**
     * @param slots   the gallery's portraits, slot i being in column i (the list must be in column order)
     * @param ranking the season's players, best first
     * @return one placement per slot, in the same order; the target columns are a permutation of 0..n-1
     */
    public static List<Placement> plan(List<Slot> slots, List<String> ranking) {
        int n = slots.size();
        Map<String, Integer> rankOf = new HashMap<>();
        for (int i = 0; i < ranking.size(); i++) rankOf.putIfAbsent(key(ranking.get(i)), i + 1);

        // 1. Fixed portraits keep their player
        String[] player = new String[n];
        Set<String> claimed = new HashSet<>();
        for (int i = 0; i < n; i++) {
            String fixed = slots.get(i).fixedPlayer();
            if (fixed != null && !fixed.isBlank()) {
                player[i] = fixed;
                claimed.add(key(fixed));
            }
        }
        // 2. Portraits following a rank share the best players not shown by a fixed portrait. One that already
        //    shows one of them keeps it (it will slide to that player's new column); the others take the rest.
        List<Integer> rankSlots = new ArrayList<>();
        for (int i = 0; i < n; i++) if (player[i] == null) rankSlots.add(i);
        List<String> pool = new ArrayList<>();
        for (String p : ranking) {
            if (pool.size() == rankSlots.size()) break;
            if (!claimed.contains(key(p)) && pool.stream().noneMatch(q -> key(q).equals(key(p)))) pool.add(p);
        }
        Set<String> kept = new HashSet<>();
        List<Integer> waiting = new ArrayList<>();
        for (int i : rankSlots) {
            String shown = slots.get(i).shownPlayer();
            String match = shown == null ? null : pool.stream().filter(p -> key(p).equals(key(shown))).findFirst().orElse(null);
            if (match != null && kept.add(key(match))) player[i] = match;
            else waiting.add(i);
        }
        List<String> left = pool.stream().filter(p -> !kept.contains(key(p))).toList();
        for (int k = 0; k < waiting.size(); k++) player[waiting.get(k)] = k < left.size() ? left.get(k) : null;

        // 3. Columns by rank: ranked players first, then fixed players without a score, then empty portraits;
        //    ties keep the current left-to-right order
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        final String[] shownBy = player;
        java.util.Arrays.sort(order, Comparator.<Integer>comparingInt(i -> sortKey(shownBy[i], rankOf)).thenComparingInt(i -> i));
        int[] target = new int[n];
        for (int c = 0; c < n; c++) target[order[c]] = c;

        int[] plane = planes(target);
        List<Placement> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Integer rank = player[i] == null ? null : rankOf.get(key(player[i]));
            out.add(new Placement(slots.get(i).column(), target[i], player[i], rank == null ? 0 : rank, plane[i]));
        }
        return out;
    }

    private static int sortKey(String player, Map<String, Integer> rankOf) {
        if (player == null) return Integer.MAX_VALUE;
        Integer r = rankOf.get(key(player));
        return r != null ? r : Integer.MAX_VALUE - 1;
    }

    /**
     * Planes of the moving portraits (slot i goes from column i to target[i]). Leftward movers are spread over
     * planes 1, 2… so that inside a plane the order is kept (first fit, which uses as few planes as there are
     * movers in the longest chain of mutual swaps); rightward movers likewise over -1, -2…
     */
    static int[] planes(int[] target) {
        int n = target.length;
        int[] plane = new int[n];
        for (int dir : new int[]{-1, 1}) {
            // Last target column of each plane so far, for movers processed from left to right
            List<Integer> lastTarget = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int d = Integer.signum(target[i] - i);
                if (d != dir) continue;
                int p = 0;
                while (p < lastTarget.size() && lastTarget.get(p) > target[i]) p++;
                if (p == lastTarget.size()) lastTarget.add(target[i]);
                else lastTarget.set(p, target[i]);
                // Going left (d = -1) means stepping forward: positive planes
                plane[i] = -dir * (p + 1);
            }
        }
        return plane;
    }
}
