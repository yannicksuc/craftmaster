package fr.lordfinn.craftmaster.studio;

/**
 * Aiming maths of the spotlights, in Minecraft's angles: yaw 0 = south (+z), 90 = west (-x); pitch positive =
 * down. Pure (no game classes) so it is unit tested.
 */
public final class SpotlightAim {

    private SpotlightAim() {}

    /** Steepest a spotlight can point (straight up or down is allowed, slightly less to keep the yoke readable). */
    public static final float MAX_PITCH = 89f;

    /** {yaw, pitch} in degrees pointing along (dx, dy, dz); a zero vector gives {0, 0}. */
    public static float[] angles(double dx, double dy, double dz) {
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1e-9 && Math.abs(dy) < 1e-9) return new float[]{0, 0};
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, horizontal));
        return new float[]{wrap(yaw), clampPitch(pitch)};
    }

    /** Unit vector {x, y, z} for a yaw and a pitch in degrees. */
    public static double[] direction(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        double cos = Math.cos(p);
        return new double[]{-Math.sin(y) * cos, -Math.sin(p), Math.cos(y) * cos};
    }

    public static float clampPitch(float pitch) {
        return Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch));
    }

    /** An angle brought into [-180, 180). */
    public static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    /**
     * Moves an angle toward a target by at most {@code maxStep} degrees, the short way round (a spotlight
     * turning from 170° to -170° crosses 180°, not 0°).
     */
    public static float approach(float current, float target, float maxStep) {
        float delta = wrap(target - current);
        if (Math.abs(delta) <= maxStep) return target;
        return wrap(current + Math.signum(delta) * maxStep);
    }

    /**
     * Smooth follow for the rendered angles: an eased move covering {@code rate} of the remaining distance per
     * second (frame-rate independent), never slower than {@code minSpeed} degrees per second near the end.
     */
    public static float ease(float current, float target, double seconds, double rate, double minSpeed) {
        float delta = wrap(target - current);
        double factor = 1 - Math.exp(-rate * seconds);
        double step = Math.max(Math.abs(delta) * factor, minSpeed * seconds);
        return approach(current, target, (float) step);
    }
}
