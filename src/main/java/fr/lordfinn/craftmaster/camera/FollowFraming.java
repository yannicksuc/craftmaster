package fr.lordfinn.craftmaster.camera;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.ToDoubleFunction;

/**
 * The framings of the follow shot (pure maths, no Minecraft; the client checks the blocks, {@code FollowCamera}).
 * A framing is a place on a sphere around the followed player's head: an azimuth (0 = in front of them, positive =
 * to their right as they face), an elevation (positive = above, looking down; negative = below, looking up), a
 * distance, and slow moves while it is held (orbit, push in / pull out). The camera glides from one framing to the
 * next along the sphere (the parameters are interpolated, not the positions: it turns round the player instead of
 * cutting through them), and only to a framing that the blocks leave room for.
 */
public final class FollowFraming {

    private FollowFraming() {}

    /**
     * A framing.
     *
     * @param name      shown in the development log only
     * @param azimuth   degrees round the player, 0 = in front of them
     * @param elevation degrees, positive = from above
     * @param distance  blocks from the head
     * @param orbit     degrees per second turned round the player while held
     * @param dolly     blocks per second (negative = pushes in), at most {@link #DOLLY_MAX} in all
     * @param thirds    blocks the aim is moved sideways, so the player sits on a third of the picture
     * @param focus     height looked at, as a fraction of the player's eye height (1 = the eyes)
     */
    public record Plan(String name, double azimuth, double elevation, double distance, double orbit, double dolly,
                       double thirds, double focus) {}

    /** The interpolated state of the camera on its sphere. */
    public record Params(double azimuth, double elevation, double distance, double thirds, double focus) {

        /** The parameters a fraction {@code t} of the way to {@code o}, the azimuth the short way round. */
        public Params lerp(Params o, double t) {
            double da = ((o.azimuth - azimuth) % 360 + 540) % 360 - 180;
            return new Params(azimuth + da * t, elevation + (o.elevation - elevation) * t,
                    distance + (o.distance - distance) * t, thirds + (o.thirds - thirds) * t, focus + (o.focus - focus) * t);
        }
    }

    /** Push in / pull out of a held framing: never more than this (blocks). */
    public static final double DOLLY_MAX = 0.9;
    /** Elevation and distance bounds (also those of the hand-held camera). */
    public static final double MIN_ELEVATION = -30, MAX_ELEVATION = 75, MIN_DISTANCE = 1.2, MAX_DISTANCE = 12;
    /** How long a framing is held (seconds), and the glide to the next one. */
    public static final double HOLD_MIN = 7, HOLD_MAX = 12, GLIDE = 2.4;
    /** A framing is chosen only if the blocks leave it at least this share of its distance. */
    public static final double ROOM_NEEDED = 0.85;

    /** The framings, the first one being the opening framing of a follow shot. */
    public static final List<Plan> PLANS = List.of(
            new Plan("trois-quarts", 30, 10, 3.4, 1.2, 0, 0.45, 0.92),
            new Plan("trois-quarts gauche", -35, 14, 3.7, -1.1, -0.05, -0.45, 0.92),
            new Plan("profil", 88, 4, 3.9, 0, -0.06, 0.5, 0.9),
            new Plan("contre-plongée", 22, -20, 3.0, 1.6, 0, 0.3, 0.8),
            new Plan("plongée", -40, 42, 4.8, 2.2, 0.05, -0.3, 0.75),
            new Plan("plan large", 15, 22, 7.0, 1.0, -0.07, 0.6, 0.6),
            new Plan("gros plan", 12, 3, 2.0, 0.7, 0, 0.25, 0.97),
            new Plan("face", 0, 8, 3.6, 0, 0.05, 0.3, 0.9),
            new Plan("par-dessus l'épaule", 155, 16, 2.7, 0, 0, 0.6, 0.95),
            new Plan("de dos", 180, 12, 3.2, 0, 0, 0, 0.85),
            new Plan("trois-quarts dos", -140, 24, 4.2, -1.5, 0, -0.45, 0.85));

    /** The parameters of {@code plan} {@code seconds} after it started being held. */
    public static Params params(Plan plan, double seconds) {
        double d = Math.max(-DOLLY_MAX, Math.min(DOLLY_MAX, plan.dolly() * seconds));
        return new Params(plan.azimuth() + plan.orbit() * seconds, plan.elevation(),
                Math.max(MIN_DISTANCE, plan.distance() + d), plan.thirds(), plan.focus());
    }

    /**
     * Unit vector from the head to the camera for parameters {@code p}, around a player turned to {@code yaw}
     * (Minecraft yaw: 0 = +z, 90 = -x).
     */
    public static double[] direction(Params p, double yaw) {
        double a = Math.toRadians(yaw + p.azimuth()), e = Math.toRadians(p.elevation());
        // Facing of the yaw turned by the azimuth (forward of Minecraft: (-sin, cos))
        return new double[]{-Math.sin(a) * Math.cos(e), Math.sin(e), Math.cos(a) * Math.cos(e)};
    }

    /** Smooth start and stop over 0..1 (zero speed and acceleration at both ends). */
    public static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /** How long a framing is held, between {@link #HOLD_MIN} and {@link #HOLD_MAX}. */
    public static double hold(Random random) {
        return HOLD_MIN + random.nextDouble() * (HOLD_MAX - HOLD_MIN);
    }

    /**
     * The next framing: one the blocks leave room for ({@code room}: share of its distance that is free, 0..1),
     * never the current one nor the one before, picked at random among those with room ({@code weight}: how much
     * each one is wanted now, e.g. the ones in front of a walking player); if none has enough room, the one with the
     * most free space in blocks (in a corridor: a framing along it). Null only if there is no framing at all.
     */
    public static Plan choose(Plan current, Plan previous, ToDoubleFunction<Plan> room, ToDoubleFunction<Plan> weight, Random random) {
        List<Plan> ok = new ArrayList<>();
        Plan best = null;
        double bestFree = -1, total = 0;
        for (Plan p : PLANS) {
            double r = room.applyAsDouble(p);
            double free = r * p.distance();
            if (p != current && free > bestFree) {
                best = p;
                bestFree = free;
            }
            if (p == current || p == previous || r < ROOM_NEEDED) continue;
            ok.add(p);
            total += Math.max(0, weight.applyAsDouble(p));
        }
        if (!ok.isEmpty()) {
            if (total <= 0) return ok.get(random.nextInt(ok.size()));
            double x = random.nextDouble() * total;
            for (Plan p : ok) {
                x -= Math.max(0, weight.applyAsDouble(p));
                if (x < 0) return p;
            }
            return ok.getLast();
        }
        return best != null ? best : current;
    }

    /** Free blocks of a framing with that much room (to compare a framing with another one). */
    public static double free(Plan p, double room) {
        return room * p.distance();
    }
}
