package fr.lordfinn.craftmaster.camera;

/**
 * How a camera moves on air (pure maths, shared by the broadcast clients and the tests): a tripod is never perfectly
 * still on TV, so the view "breathes" a little, pushes in slowly (dolly) or pans gently. All movements are smooth
 * functions of the time since the shot started, slow enough to stay readable, and the same on every broadcast game.
 */
public final class CameraMotion {

    private CameraMotion() {}

    /** A viewpoint: position (blocks), yaw / pitch (degrees, Minecraft convention). */
    public record View(double x, double y, double z, float yaw, float pitch) {}

    /** Breathing: a few centimetres and a fraction of a degree, on periods that never line up. */
    static final double BREATH_POS = 0.035, BREATH_YAW = 0.35, BREATH_PITCH = 0.22;
    /** Dolly: blocks per second, eased out, and its maximum distance. */
    static final double DOLLY_SPEED = 0.08, DOLLY_MAX = 1.6;
    /** Pan: degrees each side, one sweep period (seconds). */
    static final double PAN_DEGREES = 7, PAN_PERIOD = 26;

    /** The view of camera {@code s}, {@code seconds} after it went on air. */
    public static View view(CameraSpot s, double seconds) {
        double x = s.x(), y = s.y(), z = s.z();
        double yaw = s.yaw(), pitch = s.pitch();
        String m = s.motion();
        if (!CameraSpot.FIXED.equals(m)) {
            // Breathing (also under the dolly and the pan, a bit smaller)
            double k = CameraSpot.BREATHING.equals(m) ? 1 : 0.6;
            x += k * BREATH_POS * Math.sin(seconds * 2 * Math.PI / 7.3 + 0.4);
            y += k * BREATH_POS * 0.7 * Math.sin(seconds * 2 * Math.PI / 9.1 + 1.9);
            z += k * BREATH_POS * Math.sin(seconds * 2 * Math.PI / 11.7 + 3.1);
            yaw += k * BREATH_YAW * Math.sin(seconds * 2 * Math.PI / 13.3 + 0.7);
            pitch += k * BREATH_PITCH * Math.sin(seconds * 2 * Math.PI / 10.9 + 2.3);
        }
        if (CameraSpot.DOLLY.equals(m)) {
            double d = dolly(seconds);
            double[] f = forward(s.yaw(), s.pitch());
            x += f[0] * d;
            y += f[1] * d;
            z += f[2] * d;
        } else if (CameraSpot.PAN.equals(m)) {
            yaw += PAN_DEGREES * Math.sin(seconds * 2 * Math.PI / PAN_PERIOD);
        }
        return new View(x, y, z, (float) yaw, (float) pitch);
    }

    /** Distance pushed in after {@code seconds}: starts at the dolly speed, slows down to stop at {@link #DOLLY_MAX}. */
    static double dolly(double seconds) {
        if (seconds <= 0) return 0;
        // DOLLY_MAX * (1 - e^(-v t / max)): derivative v at t = 0, never beyond the maximum
        return DOLLY_MAX * (1 - Math.exp(-DOLLY_SPEED * seconds / DOLLY_MAX));
    }

    /** Unit vector of a Minecraft yaw / pitch (yaw 0 = +z, 90 = -x; pitch positive = down). */
    public static double[] forward(double yaw, double pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new double[]{-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)};
    }

    /** Yaw and pitch (degrees) to look from {@code (fx,fy,fz)} at {@code (tx,ty,tz)}. */
    public static float[] lookAt(double fx, double fy, double fz, double tx, double ty, double tz) {
        double dx = tx - fx, dy = ty - fy, dz = tz - fz;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, h));
        return new float[]{yaw, pitch};
    }

    /**
     * Where the follow shot wants to be for a player standing at {@code (x,y,z)} (feet) and turned to
     * {@code bodyYaw}: in front of them, a bit to the side and above, so the face is seen (a "reaction" shot).
     */
    public static double[] followPosition(double x, double y, double z, float bodyYaw, double distance, double side, double height) {
        double[] f = forward(bodyYaw, 0);
        // Right of the player (as they face): forward turned by -90°
        double rx = -f[2], rz = f[0];
        return new double[]{x + f[0] * distance + rx * side, y + height, z + f[2] * distance + rz * side};
    }

    /** Exponential smoothing factor for a frame of {@code dt} seconds and a time constant {@code tau}. */
    public static double smoothing(double dt, double tau) {
        if (tau <= 0) return 1;
        return 1 - Math.exp(-Math.max(0, dt) / tau);
    }

    /** Shortest-path interpolation between two angles (degrees). */
    public static float lerpAngle(float from, float to, double t) {
        float d = ((to - from) % 360 + 540) % 360 - 180;
        return (float) (from + d * t);
    }
}
