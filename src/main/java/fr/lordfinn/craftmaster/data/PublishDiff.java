package fr.lordfinn.craftmaster.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What a publish changed for each player: the rounds whose score moved (from, to), the total and the rank before and
 * after. Ranks count the season's players without points as 0 (like the ranking) and share ties.
 */
public final class PublishDiff {

    private PublishDiff() {}

    public record Round(String label, int from, int to) {
        public int delta() {
            return to - from;
        }
    }

    /** One player's changes; {@code rankBefore} 0: was not ranked yet. */
    public record Change(String player, List<Round> rounds, int totalBefore, int totalAfter, int rankBefore, int rankAfter) {
        public int delta() {
            return totalAfter - totalBefore;
        }

        public boolean changed() {
            return !rounds.isEmpty() || rankBefore != rankAfter;
        }
    }

    /**
     * @param before published scores before (null: never published)
     * @param after  published scores now
     * @param cast   the season's players (counted at 0 when they have no score)
     */
    public static Map<String, Change> diff(Map<String, Map<String, Integer>> before, Map<String, Map<String, Integer>> after, Set<String> cast) {
        Map<String, Map<String, Integer>> b = before == null ? Map.of() : before;
        Set<String> players = new LinkedHashSet<>();
        players.addAll(after.keySet());
        players.addAll(b.keySet());
        players.addAll(cast);
        Map<String, Integer> ranksBefore = ranks(b, players), ranksAfter = ranks(after, players);
        Map<String, Change> out = new LinkedHashMap<>();
        for (String p : players) {
            Map<String, Integer> was = b.getOrDefault(p, Map.of()), now = after.getOrDefault(p, Map.of());
            Set<String> labels = new LinkedHashSet<>(now.keySet());
            labels.addAll(was.keySet());
            List<Round> rounds = new ArrayList<>();
            for (String l : labels) {
                int from = was.getOrDefault(l, 0), to = now.getOrDefault(l, 0);
                if (from != to) rounds.add(new Round(l, from, to));
            }
            out.put(p, new Change(p, rounds, sum(was), sum(now), before == null ? 0 : ranksBefore.get(p), ranksAfter.get(p)));
        }
        return out;
    }

    private static int sum(Map<String, Integer> m) {
        int t = 0;
        for (int v : m.values()) t += v;
        return t;
    }

    /** Competition ranks (1, 2, 2, 4) by total. */
    static Map<String, Integer> ranks(Map<String, Map<String, Integer>> scores, Set<String> players) {
        List<String> order = new ArrayList<>(players);
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (String p : order) totals.put(p, sum(scores.getOrDefault(p, Map.of())));
        order.sort((x, y) -> Integer.compare(totals.get(y), totals.get(x)));
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < order.size(); i++) {
            String p = order.get(i);
            int rank = i > 0 && totals.get(order.get(i - 1)).equals(totals.get(p)) ? out.get(order.get(i - 1)) : i + 1;
            out.put(p, rank);
        }
        return out;
    }

    /** Case-insensitive lookup of a player's change. */
    public static Change of(Map<String, Change> diff, String player) {
        for (Map.Entry<String, Change> e : diff.entrySet()) if (e.getKey().toLowerCase(Locale.ROOT).equals(player.toLowerCase(Locale.ROOT))) return e.getValue();
        return null;
    }
}
