package fr.lordfinn.craftmaster.home;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The title screen's maths, kept free of Minecraft classes (unit tested): the camera, the row of frames in real
 * perspective, the frames' sway, the background's cover fit and the parallax easing.
 * <p>
 * "Camera space": blocks, the eye at the origin looking down -z, x to the right, y up. "Screen": GUI pixels, origin
 * at the top left, y down; {@code w × h} is the screen's size.
 */
public final class HomeLayout {

    private HomeLayout() {}

    /** Focal length in pixels for a vertical field of view of {@code fovDegrees} on a screen {@code h} pixels high. */
    public static double focal(double fovDegrees, double h) {
        return h / 2 / Math.tan(Math.toRadians(fovDegrees) / 2);
    }

    /** The camera space point seen at screen pixel (sx, sy), {@code depth} blocks in front of the eye. */
    public static Vector3d unproject(double sx, double sy, double depth, double w, double h, double f) {
        return new Vector3d((sx - w / 2) * depth / f, -(sy - h / 2) * depth / f, -depth);
    }

    /** Screen pixel {x, y} of a camera space point in front of the eye. */
    public static double[] project(Vector3d p, double w, double h, double f) {
        double depth = -p.z;
        return new double[]{w / 2 + f * p.x / depth, h / 2 - f * p.y / depth};
    }

    /**
     * Where the frames hang: one straight row in camera space, evenly spaced, seen in real perspective.
     *
     * @param count        frames in the row (player 1 first, the nearest)
     * @param firstX       screen pixel of the nearest frame's centre
     * @param vanishX      screen pixel the row recedes toward (the row's centres line up from first to it)
     * @param height       screen height of the nearest frame, pixels
     * @param frameW       frame width, blocks
     * @param frameH       frame height, blocks
     * @param spacing      distance between two frames' centres, in frame widths (camera space)
     * @param farthest     size of the farthest frame on screen relative to the nearest (depth ratio), 0..1
     * @return the frames' centres, nearest first
     */
    public static List<Vector3d> row(int count, double firstX, double firstY, double vanishX, double vanishY, double height,
                                     double frameW, double frameH, double spacing, double farthest,
                                     double w, double h, double f) {
        List<Vector3d> out = new ArrayList<>();
        if (count <= 0) return out;
        double z1 = f * frameH / height;
        Vector3d p1 = unproject(firstX, firstY, z1, w, h, f);
        out.add(p1);
        if (count == 1) return out;
        double zn = z1 / Math.max(0.05, Math.min(1, farthest));
        double ux = vanishX - firstX, uy = vanishY - firstY, len = Math.hypot(ux, uy);
        if (len < 1e-9) {
            ux = -1;
            uy = 0;
        } else {
            ux /= len;
            uy /= len;
        }
        // The farthest centre: at depth zn, on the screen ray from the first centre toward the vanishing zone,
        // (count - 1) spacings away from the first in camera space. Q(t) = q0 + t·v, |Q(t) - p1| = L: a quadratic.
        double target = (count - 1) * spacing * frameW;
        Vector3d q0 = unproject(firstX, firstY, zn, w, h, f);
        Vector3d v = new Vector3d(ux * zn / f, -uy * zn / f, 0);
        Vector3d d0 = new Vector3d(q0).sub(p1);
        double a = v.lengthSquared(), b = v.dot(d0), c = d0.lengthSquared() - target * target;
        double disc = b * b - a * c;
        double t = disc < 0 ? -b / a : (-b + Math.sqrt(disc)) / a;
        Vector3d pn = new Vector3d(v).mul(Math.max(0, t)).add(q0);
        for (int i = 1; i < count; i++) out.add(new Vector3d(p1).lerp(pn, i / (double) (count - 1)));
        return out;
    }

    /**
     * The frame's turn around the vertical axis, degrees: {@code base} plus a slow smooth sway of {@code amplitude},
     * each frame with its own period (7 to 11 s) and phase, on a real time clock.
     */
    public static double swayAngle(double base, double amplitude, double seconds, int index) {
        double r1 = unit(index * 0x9E3779B97F4A7C15L + 1), r2 = unit(index * 0xC2B2AE3D27D4EB4FL + 7);
        double period = 7 + 4 * r1, phase = 2 * Math.PI * r2;
        return base + amplitude * Math.sin(2 * Math.PI * seconds / period + phase);
    }

    /**
     * The turn around the vertical axis (degrees, positive toward +x) that makes something at {@code p} face the
     * eye: a frame on the right of the screen is already seen a little from its left side.
     */
    public static double facingYaw(Vector3d p) {
        return Math.toDegrees(Math.atan2(-p.x, -p.z));
    }

    /** A pseudo random number in [0, 1) from a seed (same seed, same number). */
    static double unit(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) / (double) (1L << 53);
    }

    /**
     * Size of a {@code imgW × imgH} picture covering a {@code w × h} screen (no bar), times {@code zoom}:
     * {drawn width, drawn height, horizontal margin, vertical margin} (the margins are how far it can move
     * either way before an edge shows).
     */
    public static double[] cover(double imgW, double imgH, double w, double h, double zoom) {
        double s = Math.max(w / imgW, h / imgH) * Math.max(1, zoom);
        double dw = imgW * s, dh = imgH * s;
        return new double[]{dw, dh, (dw - w) / 2, (dh - h) / 2};
    }

    /** Exponential easing toward the target, frame rate independent ({@code rate} per second). */
    public static double ease(double current, double target, double dtSeconds, double rate) {
        double k = 1 - Math.exp(-rate * Math.max(0, Math.min(0.25, dtSeconds)));
        return current + (target - current) * k;
    }

    /** The mouse as -1..1 from the screen's centre (left / top negative). */
    public static double[] mouse(double mx, double my, double w, double h) {
        return new double[]{clamp((mx - w / 2) / (w / 2)), clamp((my - h / 2) / (h / 2))};
    }

    private static double clamp(double v) {
        return Double.isFinite(v) ? Math.max(-1, Math.min(1, v)) : 0;
    }

    /** A screen offset of (dx, dy) pixels at {@code depth}, as a camera space move. */
    public static Vector3d screenShift(double dx, double dy, double depth, double f) {
        return new Vector3d(dx * depth / f, -dy * depth / f, 0);
    }

    /** A multiply tint (opaque ARGB): white mixed toward {@code haze} (RGB) by {@code amount}, fading a frame into the background. */
    public static int hazeTint(int haze, double amount) {
        amount = Math.max(0, Math.min(1, amount));
        int r = (int) Math.round(255 + ((haze >> 16 & 255) - 255) * amount);
        int g = (int) Math.round(255 + ((haze >> 8 & 255) - 255) * amount);
        int b = (int) Math.round(255 + ((haze & 255) - 255) * amount);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
