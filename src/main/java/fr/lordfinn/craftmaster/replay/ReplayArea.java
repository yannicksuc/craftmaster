package fr.lordfinn.craftmaster.replay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The chunks recorded for a round: the bounding box of what belongs to it (its cameras, the trial zones those
 * cameras watch, its teleport points, or else where its players stand), in the world that has most of it, plus a
 * margin. Pure arithmetic, unit tested.
 *
 * <p>Margin: the game never draws the outermost recorded chunk (Server Replay's README), and a camera looks a
 * little beyond what it stands on, so the box grows by {@code margin} chunks (2 by default) on every side. A box
 * wider than {@code maxSpan} chunks is cut down around its centre.
 */
public final class ReplayArea {

    /** A block box in a world (a single point when min == max). */
    public record Box(String world, double x0, double z0, double x1, double z1) {
        public static Box point(String world, double x, double z) {
            return new Box(world, x, z, x, z);
        }
    }

    /** Chunk corners (included) in {@code world}. */
    public record Chunks(String world, int fromX, int fromZ, int toX, int toZ) {
        public int width() { return toX - fromX + 1; }
        public int depth() { return toZ - fromZ + 1; }

        public String describe() {
            return String.format(Locale.ROOT, "%s, chunks (%d, %d) à (%d, %d), %d × %d", world, fromX, fromZ, toX, toZ, width(), depth());
        }
    }

    private ReplayArea() {}

    /** Null when there is nothing to frame. */
    public static Chunks of(List<Box> boxes, int margin, int maxSpan) {
        if (boxes.isEmpty()) return null;
        // The world with the most pieces (a round lives in one world; a stray studio camera elsewhere is left out)
        Map<String, Integer> count = new HashMap<>();
        for (Box b : boxes) count.merge(b.world(), 1, Integer::sum);
        String world = null;
        for (Box b : boxes) if (world == null || count.get(b.world()) > count.get(world)) world = b.world();
        List<Box> in = new ArrayList<>();
        for (Box b : boxes) if (b.world().equals(world)) in.add(b);

        double x0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (Box b : in) {
            x0 = Math.min(x0, Math.min(b.x0(), b.x1()));
            z0 = Math.min(z0, Math.min(b.z0(), b.z1()));
            x1 = Math.max(x1, Math.max(b.x0(), b.x1()));
            z1 = Math.max(z1, Math.max(b.z0(), b.z1()));
        }
        int fx = chunk(x0) - margin, fz = chunk(z0) - margin, tx = chunk(x1) + margin, tz = chunk(z1) + margin;
        int[] xs = clamp(fx, tx, maxSpan), zs = clamp(fz, tz, maxSpan);
        return new Chunks(world, xs[0], zs[0], xs[1], zs[1]);
    }

    static int chunk(double block) {
        return Math.floorDiv((int) Math.floor(block), 16);
    }

    private static int[] clamp(int from, int to, int maxSpan) {
        if (maxSpan <= 0 || to - from + 1 <= maxSpan) return new int[]{from, to};
        int centre = Math.floorDiv(from + to, 2);
        int start = centre - (maxSpan - 1) / 2;
        return new int[]{start, start + maxSpan - 1};
    }

    // ---------------------------------------------------------------- file names

    /**
     * Folder name of a recording: {@code cm-s1-p3-m2-le-pont} for the round's zone, {@code cm-s1-p3-m2-le-pont-ensuri}
     * for a player's view ({@code m0} = the whole game, recorded in "partie" mode). Server Replay saves each file as
     * {@code <that folder>/<date-time>.mcpr}, and {@code /replay view chunks|players <folder> <date-time>} plays it.
     */
    public static String folder(int season, int partie, int manche, String mancheId, String player) {
        StringBuilder b = new StringBuilder("cm-s").append(season).append("-p").append(partie);
        if (manche > 0) b.append("-m").append(manche);
        if (mancheId != null && !mancheId.isBlank()) b.append('-').append(slug(mancheId));
        if (player != null && !player.isBlank()) b.append('-').append(slug(player));
        return b.toString();
    }

    static String slug(String s) {
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "-").replaceAll("^-+|-+$", "");
        return n.isEmpty() ? "x" : n;
    }
}
