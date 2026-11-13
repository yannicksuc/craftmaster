package fr.lordfinn.craftmaster.toy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What the toys remember for the organisers, per player, since the last reset: hidden objects found (by kind and
 * colour), catches in the target containers, the longest throw, golf strokes per hole, balloons popped, eggs
 * broken. Pure (tested); {@link Toys#TALLY} is the server's.
 */
public final class ToyTally {

    /** Per player (case kept as first seen), per label: a count. */
    private final Map<String, Map<String, Integer>> found = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> catches = new LinkedHashMap<>();
    private final Map<String, Double> bestThrow = new LinkedHashMap<>();
    private final Map<String, Double> bestCatch = new LinkedHashMap<>();
    private final Map<String, List<Integer>> golf = new LinkedHashMap<>();
    private final Map<String, Integer> pops = new LinkedHashMap<>();
    private final Map<String, Integer> broken = new LinkedHashMap<>();

    private static String key(Map<String, ?> m, String player) {
        for (String k : m.keySet()) if (k.equalsIgnoreCase(player)) return k;
        return player;
    }

    /** A hidden object was found; returns the player's total of found objects. */
    public int found(String player, String label) {
        found.computeIfAbsent(key(found, player), k -> new TreeMap<>()).merge(label, 1, Integer::sum);
        return foundTotal(player);
    }

    public int foundTotal(String player) {
        Map<String, Integer> m = found.get(key(found, player));
        return m == null ? 0 : m.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Something landed in a target; returns the player's count in that target. */
    public int caught(String player, String target, double distance) {
        String p = key(catches, player);
        int n = catches.computeIfAbsent(p, k -> new TreeMap<>()).merge(target, 1, Integer::sum);
        if (distance > 0) bestCatch.merge(key(bestCatch, player), distance, Math::max);
        return n;
    }

    public int caughtTotal(String player) {
        Map<String, Integer> m = catches.get(key(catches, player));
        return m == null ? 0 : m.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** The farthest catch of a player (0 if none). */
    public double bestCatch(String player) {
        return bestCatch.getOrDefault(key(bestCatch, player), 0.0);
    }

    /** A throw landed this far; returns true if it is the player's best. */
    public boolean thrown(String player, double distance) {
        String p = key(bestThrow, player);
        double before = bestThrow.getOrDefault(p, -1.0);
        if (distance <= before) return false;
        bestThrow.put(p, distance);
        return true;
    }

    public double bestThrow(String player) {
        return bestThrow.getOrDefault(key(bestThrow, player), 0.0);
    }

    /** A golf ball dropped in a hole after this many strokes; returns the player's total over the holes played. */
    public int holed(String player, int strokes) {
        List<Integer> l = golf.computeIfAbsent(key(golf, player), k -> new ArrayList<>());
        l.add(strokes);
        return l.stream().mapToInt(Integer::intValue).sum();
    }

    public List<Integer> holes(String player) {
        return List.copyOf(golf.getOrDefault(key(golf, player), List.of()));
    }

    public int popped(String player) {
        return pops.merge(key(pops, player), 1, Integer::sum);
    }

    public int brokenEgg(String player) {
        return broken.merge(key(broken, player), 1, Integer::sum);
    }

    public boolean isEmpty() {
        return found.isEmpty() && catches.isEmpty() && bestThrow.isEmpty() && golf.isEmpty() && pops.isEmpty() && broken.isEmpty();
    }

    public void reset() {
        found.clear();
        catches.clear();
        bestThrow.clear();
        bestCatch.clear();
        golf.clear();
        pops.clear();
        broken.clear();
    }

    /** Every player that appears somewhere, in first-seen order. */
    public List<String> players() {
        List<String> out = new ArrayList<>();
        for (Map<String, ?> m : List.<Map<String, ?>>of(found, catches, bestThrow, golf, pops, broken))
            for (String p : m.keySet()) if (out.stream().noneMatch(p::equalsIgnoreCase)) out.add(p);
        return out;
    }

    /** One line per thing a player did: "trouvés 3 (2 canard en plastique jaune, 1 …)", "golf 3 + 4 = 7"… */
    public List<String> describe(String player) {
        List<String> out = new ArrayList<>();
        Map<String, Integer> f = found.get(key(found, player));
        if (f != null) out.add("trouvé(s) " + foundTotal(player) + " : " + join(f));
        Map<String, Integer> c = catches.get(key(catches, player));
        if (c != null) out.add("dans la cible " + caughtTotal(player) + " : " + join(c)
                + (bestCatch(player) > 0 ? " (le plus loin : " + ToyPhysics.blocks(bestCatch(player)) + " blocs)" : ""));
        if (bestThrow.containsKey(key(bestThrow, player))) out.add("plus long lancer : " + ToyPhysics.blocksText(bestThrow(player)));
        List<Integer> g = golf.get(key(golf, player));
        if (g != null) out.add("golf : " + String.join(" + ", g.stream().map(String::valueOf).toList())
                + (g.size() > 1 ? " = " + g.stream().mapToInt(Integer::intValue).sum() : "") + " coup(s)");
        Integer p = pops.get(key(pops, player));
        if (p != null) out.add("ballons crevés : " + p);
        Integer b = broken.get(key(broken, player));
        if (b != null) out.add("œufs cassés : " + b);
        return out;
    }

    private static String join(Map<String, Integer> m) {
        List<String> parts = new ArrayList<>();
        m.forEach((k, v) -> parts.add(v + " " + k));
        return String.join(", ", parts);
    }
}
