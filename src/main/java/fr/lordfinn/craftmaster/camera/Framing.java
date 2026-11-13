package fr.lordfinn.craftmaster.camera;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of a generated viewpoint (pure, no Minecraft): the aim from one point to another in Minecraft's angles,
 * and the positions to try around a subject, best first. Used for the "task location" shot of an intro
 * ({@code broadcast.TaskFraming}).
 */
public final class Framing {

    private Framing() {}

    /** A point (x, y, z). */
    public record Vec(double x, double y, double z) {
        public Vec add(double dx, double dy, double dz) {
            return new Vec(x + dx, y + dy, z + dz);
        }

        public double distance(Vec o) {
            return Math.sqrt((x - o.x) * (x - o.x) + (y - o.y) * (y - o.y) + (z - o.z) * (z - o.z));
        }

        /** The point a fraction {@code t} of the way to {@code o}. */
        public Vec lerp(Vec o, double t) {
            return new Vec(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
        }
    }

    /** Minecraft yaw (degrees, 0 = towards +z, 90 = towards -x) looking from {@code from} to {@code to}. */
    public static float yaw(Vec from, Vec to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    /** Minecraft pitch (degrees, positive = looking down) from {@code from} to {@code to}. */
    public static float pitch(Vec from, Vec to) {
        double h = Math.hypot(to.x - from.x, to.z - from.z);
        return (float) -Math.toDegrees(Math.atan2(to.y - from.y, h));
    }

    /** Unit horizontal direction a yaw looks towards: (dx, dz). */
    public static double[] facing(double yaw) {
        double r = Math.toRadians(yaw);
        return new double[]{-Math.sin(r), Math.cos(r)};
    }

    /**
     * Lens positions to try around {@code subject}, best first: behind {@code arrivalYaw} (the way the players will
     * face when they arrive, so the shot shows what they are about to see), a little above, then turning round on
     * both sides, closer and further, lower and higher.
     */
    public static List<Vec> candidates(Vec subject, double arrivalYaw) {
        List<Vec> out = new ArrayList<>();
        double[] distances = {4.5, 3.5, 6, 2.5, 8};
        double[] heights = {1.6, 2.6, 0.6, 4};
        int[] turns = {0, 25, -25, 50, -50, 80, -80, 120, -120, 180};
        for (double d : distances)
            for (double h : heights)
                for (int turn : turns) {
                    // Behind the arrival: opposite its facing
                    double[] f = facing(arrivalYaw + 180 + turn);
                    out.add(subject.add(f[0] * d, h, f[1] * d));
                }
        return out;
    }
}
