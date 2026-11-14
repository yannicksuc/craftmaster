package fr.lordfinn.craftmaster.camera;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * How well a placed camera shows players (pure maths; the blocks are asked through {@link Sight}): the share of the
 * picture each one fills and whether it is really seen. Used by the follow shot (cut to a placed camera that sees the
 * player), the cuts on who speaks and the silent moments (the camera that sees the most players).
 * <ul>
 *   <li>a player counts if at least two of their head, chest and feet are seen from the lens, and if their box is in
 *       the picture (its centre inside, not cut by the near plane);</li>
 *   <li>the more of the picture they fill, the more the camera is wanted, up to {@link #BEST}; beyond, less and less,
 *       and not at all from {@link #TOO_CLOSE} (they are "eating the lens");</li>
 *   <li>smaller than {@link #TINY}: not a shot of them.</li>
 * </ul>
 */
public final class ShotCoverage {

    private ShotCoverage() {}

    /** Shares of the picture (0..1): below, not a shot of them; best; too close from there. */
    public static final double TINY = 0.12, BEST = 0.55, TOO_CLOSE = 0.95;
    /** Picture proportions when not known (the broadcast is 16:9). */
    public static final double ASPECT = 16 / 9.0;

    /** Is the segment between two points free of blocks? (the caller's level) */
    @FunctionalInterface
    public interface Sight {
        boolean clear(double[] from, double[] to);
    }

    /** A player's box: feet centre, width, height, eye height. */
    public record Body(String name, double x, double y, double z, double width, double height) {
        double[] head() { return new double[]{x, y + height * 0.88, z}; }
        double[] chest() { return new double[]{x, y + height * 0.55, z}; }
        double[] feet() { return new double[]{x, y + height * 0.1, z}; }
    }

    /** A lens: position, Minecraft yaw / pitch, vertical field of view (degrees). */
    public record Lens(double x, double y, double z, double yaw, double pitch, double fov) {
        public static Lens of(CameraSpot s, double defaultFov) {
            return new Lens(s.x(), s.y(), s.z(), s.yaw(), s.pitch(), s.fov() > 0 ? s.fov() : defaultFov);
        }
    }

    /**
     * The share of the picture's height {@code body}'s box fills seen from {@code lens} (clipped to
     * the frame), 0 if its centre is out of the picture or behind; 1 if it is level with the lens and right next to it, or if it
     * overflows the frame at the top and the bottom (on the lens).
     */
    public static double share(Lens lens, double aspect, Body b) {
        double[] f = CameraMotion.forward(lens.yaw(), lens.pitch());
        double yr = Math.toRadians(lens.yaw());
        double[] r = {-Math.cos(yr), 0, -Math.sin(yr)};
        // up = right × forward
        double[] u = {r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]};
        double tv = Math.tan(Math.toRadians(lens.fov()) / 2), th = tv * aspect;
        double minX = 9, maxX = -9, minY = 9, maxY = -9;
        double hw = b.width() / 2;
        int behind = 0;
        for (int i = 0; i < 8; i++) {
            double[] p = {b.x() + ((i & 1) == 0 ? -hw : hw), b.y() + ((i & 2) == 0 ? 0 : b.height()), b.z() + ((i & 4) == 0 ? -hw : hw)};
            double[] s = project(lens, f, r, u, p);
            if (s == null) {
                behind++;
                continue;
            }
            minX = Math.min(minX, s[0] / th);
            maxX = Math.max(maxX, s[0] / th);
            minY = Math.min(minY, s[1] / tv);
            maxY = Math.max(maxY, s[1] / tv);
        }
        if (behind == 8) return 0;
        // Level with the lens: on it if right next to it, else beside it (not in the picture)
        if (behind > 0) return Math.hypot(b.x() - lens.x(), b.z() - lens.z()) < 1.2 + hw ? 1 : 0;
        // Cut at the top and at the bottom of the frame: on the lens
        if ((minY < -1 && maxY > 1 && maxX > -1 && minX < 1)) return 1;
        double[] c = project(lens, f, r, u, b.chest());
        // The chest well inside the picture, not on its edge
        if (c == null || Math.abs(c[0] / th) > 0.8 || Math.abs(c[1] / tv) > 0.85) return 0;
        // Height of the picture they fill (a full figure at 6 blocks: about a quarter; waist up: more than half)
        double h = Math.max(0, Math.min(1, maxY) - Math.max(-1, minY));
        return h / 2;
    }

    /** Screen coordinates (x / depth, y / depth) of a point, or null if it is behind or on the lens. */
    private static double[] project(Lens l, double[] f, double[] r, double[] u, double[] p) {
        double dx = p[0] - l.x(), dy = p[1] - l.y(), dz = p[2] - l.z();
        double z = dx * f[0] + dy * f[1] + dz * f[2];
        if (z < 0.1) return null;
        return new double[]{(dx * r[0] + dy * r[1] + dz * r[2]) / z, (dx * u[0] + dy * u[1] + dz * u[2]) / z};
    }

    /** Are at least two of the head, chest and feet seen from the lens? */
    public static boolean visible(Lens lens, Body b, Sight sight) {
        double[] eye = {lens.x(), lens.y(), lens.z()};
        int seen = 0;
        for (double[] p : new double[][]{b.head(), b.chest(), b.feet()}) if (sight.clear(eye, p) && ++seen >= 2) return true;
        return false;
    }

    /** How much a camera is wanted for a player filling {@code share} of its picture: 0 (not a shot of them) .. 1. */
    public static double weight(double share) {
        if (share < TINY || share >= TOO_CLOSE) return 0;
        if (share <= BEST) return 0.15 + 0.85 * (share - TINY) / (BEST - TINY);
        return (TOO_CLOSE - share) / (TOO_CLOSE - BEST);
    }

    /** A placed camera judged for some players: those it shows well, and how much it is wanted. */
    public record Seen(CameraSpot camera, List<String> players, double weight) {}

    /** What {@code camera} shows of {@code bodies}: each one visible with a weight above zero counts. */
    public static Seen judge(CameraSpot camera, Lens lens, double aspect, List<Body> bodies, Sight sight) {
        return judge(camera, lens, aspect, bodies, sight, 0);
    }

    /**
     * Same, with a wish: {@code size} +1 = wider (players small in the picture are preferred), -1 = tighter (big ones
     * preferred, never on the lens), 0 = as it comes.
     */
    public static Seen judge(CameraSpot camera, Lens lens, double aspect, List<Body> bodies, Sight sight, int size) {
        double scale = size > 0 ? 1.7 : size < 0 ? 0.6 : 1;
        List<String> who = new ArrayList<>();
        double w = 0;
        for (Body b : bodies) {
            double s = share(lens, aspect, b);
            if (s >= TOO_CLOSE) return new Seen(camera, List.of(), 0);   // someone on the lens spoils the shot
            double k = weight(Math.min(TOO_CLOSE * 0.999, s * scale));
            if (weight(s) <= 0 && s >= TINY) k = 0;
            if (k <= 0 || !visible(lens, b, sight)) continue;
            who.add(b.name());
            w += k;
        }
        return new Seen(camera, who, w);
    }

    /**
     * A camera at random among those that show all of {@code wanted} (at least one name), the more they fill the
     * picture the likelier (up to {@link #BEST}); null if none.
     */
    public static CameraSpot pickFor(List<Seen> seen, List<String> wanted, Random random) {
        List<Seen> ok = new ArrayList<>();
        double total = 0;
        for (Seen s : seen) {
            if (wanted.isEmpty() || !containsAll(s.players(), wanted) || s.weight() <= 0) continue;
            ok.add(s);
            total += s.weight();
        }
        return weighted(ok, total, random);
    }

    /** The camera that shows the most players (at random between those showing as many, by weight); null if none. */
    public static CameraSpot pickMost(List<Seen> seen, Random random) {
        int most = 0;
        for (Seen s : seen) most = Math.max(most, s.players().size());
        if (most == 0) return null;
        List<Seen> ok = new ArrayList<>();
        double total = 0;
        for (Seen s : seen) if (s.players().size() == most) {
            ok.add(s);
            total += s.weight();
        }
        return weighted(ok, total, random);
    }

    private static CameraSpot weighted(List<Seen> ok, double total, Random random) {
        if (ok.isEmpty()) return null;
        double x = random.nextDouble() * total;
        for (Seen s : ok) {
            x -= s.weight();
            if (x < 0) return s.camera();
        }
        return ok.getLast().camera();
    }

    private static boolean containsAll(List<String> have, List<String> wanted) {
        for (String w : wanted) {
            boolean found = false;
            for (String h : have) if (h.equalsIgnoreCase(w)) found = true;
            if (!found) return false;
        }
        return true;
    }
}
