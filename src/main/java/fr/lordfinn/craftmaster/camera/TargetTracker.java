package fr.lordfinn.craftmaster.camera;

/**
 * Follows a moving target the way a good operator does (pure maths, shared by the follow camera and the
 * follow-spots): the position is smoothed, but aimed slightly ahead of where the target is going, so the smoothing
 * never lags behind a walking player (exponential smoothing at speed v lags by v × tau: aiming v × tau ahead
 * cancels it) and a little more is kept in front of them, like a cameraman leading the action.
 * <ul>
 *   <li>velocity estimated from the samples, itself smoothed (a jump or a stop does not jerk);</li>
 *   <li>only the horizontal motion is anticipated (a jump is not "led");</li>
 *   <li>the lead is capped at {@code maxLead} blocks;</li>
 *   <li>a teleport (more than {@link #JUMP} blocks at once) snaps instead of sliding across the map.</li>
 * </ul>
 */
public final class TargetTracker {

    /** A sample this far from the last one is a teleport (blocks). */
    public static final double JUMP = 8;

    private final double tau, velocityTau, extraLead, maxLead;
    private double x, y, z, vx, vz, lastX, lastY, lastZ;
    private boolean started;

    /**
     * @param tau         smoothing time constant (seconds)
     * @param velocityTau smoothing of the velocity estimate (seconds)
     * @param extraLead   seconds of motion kept in front of the target, on top of the smoothing's own lag
     * @param maxLead     cap of the lead (blocks)
     */
    public TargetTracker(double tau, double velocityTau, double extraLead, double maxLead) {
        this.tau = tau;
        this.velocityTau = velocityTau;
        this.extraLead = extraLead;
        this.maxLead = maxLead;
    }

    /** Puts the tracker on this point at once, standing still. */
    public void snap(double px, double py, double pz) {
        x = lastX = px;
        y = lastY = py;
        z = lastZ = pz;
        vx = vz = 0;
        started = true;
    }

    /** A new sample of the target, {@code dt} seconds after the last one. */
    public void update(double px, double py, double pz, double dt) {
        if (!started || Math.abs(px - lastX) + Math.abs(py - lastY) + Math.abs(pz - lastZ) > JUMP) {
            snap(px, py, pz);
            return;
        }
        if (dt <= 0) return;
        double kv = smoothing(dt, velocityTau);
        vx += ((px - lastX) / dt - vx) * kv;
        vz += ((pz - lastZ) / dt - vz) * kv;
        lastX = px;
        lastY = py;
        lastZ = pz;
        // Aim ahead: the smoothing's lag (v × tau) plus the extra lead, capped
        double lx = vx * (tau + extraLead), lz = vz * (tau + extraLead), l = Math.hypot(lx, lz);
        if (l > maxLead + tau * Math.hypot(vx, vz)) {
            double cap = (maxLead + tau * Math.hypot(vx, vz)) / l;
            lx *= cap;
            lz *= cap;
        }
        double k = smoothing(dt, tau);
        x += (px + lx - x) * k;
        y += (py - y) * k;
        z += (pz + lz - z) * k;
    }

    public boolean started() { return started; }

    public double x() { return x; }

    public double y() { return y; }

    public double z() { return z; }

    /** Horizontal velocity (blocks per second). */
    public double vx() { return vx; }

    public double vz() { return vz; }

    /** Horizontal speed (blocks per second). */
    public double speed() { return Math.hypot(vx, vz); }

    private static double smoothing(double dt, double tau) {
        return tau <= 0 ? 1 : 1 - Math.exp(-Math.max(0, dt) / tau);
    }
}
