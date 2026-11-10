package fr.lordfinn.craftmaster.camera;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A recorded camera move ("trajet", a travelling): steps (keyframes) with a position, a look and an optional zoom,
 * how long the camera takes to reach each step, how long it holds there, and the curve of the move. Pure maths,
 * shared by the server (registry, file, preview particles) and the broadcast clients, which sample it every frame.
 *
 * <p><b>Playback</b>: the camera glides through the steps on a centripetal Catmull-Rom spline (no loops, no
 * overshoot on uneven spacing), the look and the zoom on a Catmull-Rom of their own (yaw taken the short way round).
 * A step with a hold ({@code pause} &gt; 0) is a stop: the moves between two stops form one "run" whose time is eased
 * as a whole, so the camera starts gently, flows through the intermediate steps without jerks, and lands gently.
 * {@link #LINEAR} on the arriving segment removes the easing at that end (a constant-speed move).
 *
 * @param name  unique name (lower case, dashes): "pont", "intro-tour"
 * @param lieu  the lieu the move belongs to ({@link CameraSpot#STUDIO} or a round id), for the lists and the intros
 * @param world dimension id
 */
public record CameraPath(String name, String lieu, String world, List<Step> steps) {

    /** Curves: eased (default), constant speed, slow start only, slow end only. */
    public static final String SMOOTH = "lisse", LINEAR = "lineaire", EASE_IN = "entree", EASE_OUT = "sortie";
    public static final List<String> CURVES = List.of(SMOOTH, LINEAR, EASE_IN, EASE_OUT);
    /** Default time to reach a new step, the longest travel or hold of a step (seconds). */
    public static final double DEFAULT_TRAVEL = 3, MAX_SECONDS = 600;
    /** Minecraft's default field of view, used for the steps without a zoom when others have one. */
    static final float DEFAULT_FOV = 70;

    /**
     * One keyframe.
     *
     * @param travel seconds to come from the previous step (ignored on the first one)
     * @param hold   seconds the camera stays here once arrived
     * @param fov    field of view, 0 = the viewer's own
     * @param curve  curve of the move that arrives here, one of {@link #CURVES}
     */
    public record Step(double x, double y, double z, float yaw, float pitch, float fov, double travel, double hold, String curve) {
        public Step {
            pitch = Math.max(-90, Math.min(90, pitch));
            fov = fov <= 0 ? 0 : Math.max(CameraSpot.MIN_FOV, Math.min(CameraSpot.MAX_FOV, fov));
            travel = clampSeconds(travel);
            hold = clampSeconds(hold);
            curve = normalizeCurve(curve);
        }

        public Step withTravel(double t) { return new Step(x, y, z, yaw, pitch, fov, t, hold, curve); }
        public Step withHold(double h) { return new Step(x, y, z, yaw, pitch, fov, travel, h, curve); }
        public Step withFov(float f) { return new Step(x, y, z, yaw, pitch, f, travel, hold, curve); }
        public Step withCurve(String c) { return new Step(x, y, z, yaw, pitch, fov, travel, hold, c); }
        public Step withPlace(double nx, double ny, double nz, float nyaw, float npitch) {
            return new Step(nx, ny, nz, nyaw, npitch, fov, travel, hold, curve);
        }

        public String describe(int index) {
            return String.format(Locale.ROOT, "%d · %.1f %.1f %.1f, regard %.0f° / %.0f°%s%s%s%s", index + 1, x, y, z, yaw, pitch,
                    index == 0 ? "" : ", en " + seconds(travel), hold > 0 ? ", pause " + seconds(hold) : "",
                    fov > 0 ? ", zoom " + Math.round(fov) + "°" : "", curve.equals(SMOOTH) ? "" : ", " + curve);
        }
    }

    /** A sampled view. */
    public record Pose(double x, double y, double z, float yaw, float pitch, float fov) {}

    public CameraPath {
        name = normalizeName(name);
        lieu = CameraSpot.normalizeLieu(lieu);
        steps = List.copyOf(steps);
    }

    static double clampSeconds(double s) {
        return Double.isFinite(s) ? Math.max(0, Math.min(MAX_SECONDS, s)) : 0;
    }

    public static String normalizeCurve(String c) {
        if (c == null) return SMOOTH;
        return switch (CameraList.fold(c)) {
            case "lineaire", "linear", "constant", "constante", "droit" -> LINEAR;
            case "entree", "ease-in", "in", "demarrage", "depart" -> EASE_IN;
            case "sortie", "ease-out", "out", "arrivee", "atterrissage" -> EASE_OUT;
            default -> SMOOTH;
        };
    }

    /** "Le Pont !" → "le-pont"; never empty. */
    public static String normalizeName(String name) {
        String n = name == null ? "" : CameraList.fold(name).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
        return n.isEmpty() ? "trajet" : n;
    }

    public static String seconds(double s) {
        return (s == Math.rint(s) ? String.valueOf((long) s) : String.format(Locale.ROOT, "%.1f", s).replace('.', ',')) + " s";
    }

    public CameraPath withSteps(List<Step> s) {
        return new CameraPath(name, lieu, world, s);
    }

    public CameraPath withLieu(String l) {
        return new CameraPath(name, l, world, steps);
    }

    public CameraPath plus(Step s) {
        List<Step> out = new ArrayList<>(steps);
        out.add(s);
        return withSteps(out);
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /** Whole length in seconds (travels after the first step, and every hold). */
    public double duration() {
        double t = 0;
        for (int i = 0; i < steps.size(); i++) t += (i == 0 ? 0 : steps.get(i).travel()) + steps.get(i).hold();
        return t;
    }

    /** Seconds from the start when the camera reaches step {@code i}. */
    public double arrival(int i) {
        double t = 0;
        for (int k = 0; k <= i && k < steps.size(); k++) t += (k == 0 ? 0 : steps.get(k).travel()) + (k < i ? steps.get(k).hold() : 0);
        return t;
    }

    // ---------------------------------------------------------------- sampling

    /** The view {@code t} seconds after the start (held on the first step before, on the last one after). */
    public Pose sample(double t) {
        int n = steps.size();
        if (n == 0) return new Pose(0, 0, 0, 0, 0, 0);
        float[] yaws = unwrappedYaws();
        float[] fovs = fovs();
        if (n == 1 || t <= 0) return at(0, yaws, fovs);
        // Walk the runs: a run goes from a stop (start or step with a hold) to the next stop
        double clock = 0;
        int i = 0;
        while (i < n - 1) {
            double hold = steps.get(i).hold();
            if (t < clock + hold) return at(i, yaws, fovs);
            clock += hold;
            int j = i + 1;
            while (j < n - 1 && steps.get(j).hold() <= 0) j++;
            // Run i → j, intermediate steps without a hold
            double length = 0;
            for (int k = i + 1; k <= j; k++) length += steps.get(k).travel();
            if (length <= 1e-9) {
                i = j;
                continue;
            }
            if (t < clock + length) {
                boolean easeStart = !LINEAR.equals(steps.get(i + 1).curve()) && !EASE_OUT.equals(steps.get(i + 1).curve());
                boolean easeEnd = !LINEAR.equals(steps.get(j).curve()) && !EASE_IN.equals(steps.get(j).curve());
                double local = ease((t - clock) / length, easeStart, easeEnd) * length;
                // Which segment of the run
                int k = i;
                while (k < j - 1 && local > steps.get(k + 1).travel()) {
                    local -= steps.get(k + 1).travel();
                    k++;
                }
                double seg = steps.get(k + 1).travel();
                double u = seg <= 1e-9 ? 1 : Math.max(0, Math.min(1, local / seg));
                return segment(k, u, i, j, yaws, fovs);
            }
            clock += length;
            i = j;
        }
        return at(n - 1, yaws, fovs);
    }

    private Pose at(int i, float[] yaws, float[] fovs) {
        Step s = steps.get(i);
        return new Pose(s.x(), s.y(), s.z(), yaws[i], s.pitch(), fovs[i]);
    }

    /**
     * Between step {@code k} and {@code k+1} at {@code u} in 0..1, inside the run {@code runStart..runEnd}: the
     * neighbours outside the run are mirrored, so the curve heads straight for the stops.
     */
    private Pose segment(int k, double u, int runStart, int runEnd, float[] yaws, float[] fovs) {
        int a = k - 1, b = k, c = k + 1, d = k + 2;
        double[] p0, p1 = pos(b), p2 = pos(c), p3;
        p0 = a < runStart ? mirror(p1, p2) : pos(a);
        p3 = d > runEnd ? mirror(p2, p1) : pos(d);
        double[] p = centripetal(p0, p1, p2, p3, u);
        double y0 = a < runStart ? 2 * yaws[b] - yaws[c] : yaws[a], y3 = d > runEnd ? 2 * yaws[c] - yaws[b] : yaws[d];
        double yaw = catmull(y0, yaws[b], yaws[c], y3, u);
        double pi0 = a < runStart ? 2 * steps.get(b).pitch() - steps.get(c).pitch() : steps.get(a).pitch();
        double pi3 = d > runEnd ? 2 * steps.get(c).pitch() - steps.get(b).pitch() : steps.get(d).pitch();
        double pitch = Math.max(-90, Math.min(90, catmull(pi0, steps.get(b).pitch(), steps.get(c).pitch(), pi3, u)));
        float fov = 0;
        if (fovs[b] > 0) {
            double f0 = a < runStart ? 2 * fovs[b] - fovs[c] : fovs[a], f3 = d > runEnd ? 2 * fovs[c] - fovs[b] : fovs[d];
            fov = (float) Math.max(CameraSpot.MIN_FOV, Math.min(CameraSpot.MAX_FOV, catmull(f0, fovs[b], fovs[c], f3, u)));
        }
        return new Pose(p[0], p[1], p[2], (float) yaw, (float) pitch, fov);
    }

    private double[] pos(int i) {
        Step s = steps.get(i);
        return new double[]{s.x(), s.y(), s.z()};
    }

    private static double[] mirror(double[] p, double[] q) {
        return new double[]{2 * p[0] - q[0], 2 * p[1] - q[1], 2 * p[2] - q[2]};
    }

    /** Each yaw moved by whole turns to be the closest to the previous one (350° → 10° goes through 0°). */
    float[] unwrappedYaws() {
        float[] out = new float[steps.size()];
        for (int i = 0; i < out.length; i++) {
            float y = steps.get(i).yaw();
            if (i > 0) {
                float prev = out[i - 1];
                y = prev + (((y - prev) % 360 + 540) % 360 - 180);
            }
            out[i] = y;
        }
        return out;
    }

    /** All 0 (the viewer's own) or, as soon as one step has a zoom, the default for the others. */
    private float[] fovs() {
        float[] out = new float[steps.size()];
        boolean any = false;
        for (Step s : steps) any |= s.fov() > 0;
        for (int i = 0; i < out.length; i++) out[i] = any ? (steps.get(i).fov() > 0 ? steps.get(i).fov() : DEFAULT_FOV) : 0;
        return out;
    }

    /** Easing of a run's time, 0..1 → 0..1: sine in-out, or one end only, or linear. */
    static double ease(double u, boolean start, boolean end) {
        u = Math.max(0, Math.min(1, u));
        if (start && end) return (1 - Math.cos(Math.PI * u)) / 2;
        if (start) return 1 - Math.cos(Math.PI * u / 2);
        if (end) return Math.sin(Math.PI * u / 2);
        return u;
    }

    /** Uniform Catmull-Rom between p1 and p2. */
    static double catmull(double p0, double p1, double p2, double p3, double u) {
        double u2 = u * u, u3 = u2 * u;
        return 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
    }

    /** Centripetal Catmull-Rom (alpha 0.5, Barry-Goldman) between p1 and p2; safe with repeated points. */
    static double[] centripetal(double[] p0, double[] p1, double[] p2, double[] p3, double u) {
        double t0 = 0;
        double t1 = t0 + knot(p0, p1);
        double t2 = t1 + knot(p1, p2);
        double t3 = t2 + knot(p2, p3);
        if (t2 - t1 < 1e-9) return p1.clone();
        double t = t1 + (t2 - t1) * u;
        double[] out = new double[3];
        for (int c = 0; c < 3; c++) {
            double a1 = lerp(p0[c], p1[c], t0, t1, t), a2 = lerp(p1[c], p2[c], t1, t2, t), a3 = lerp(p2[c], p3[c], t2, t3, t);
            double b1 = lerp(a1, a2, t0, t2, t), b2 = lerp(a2, a3, t1, t3, t);
            out[c] = lerp(b1, b2, t1, t2, t);
        }
        return out;
    }

    private static double knot(double[] a, double[] b) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        return Math.max(1e-4, Math.pow(dx * dx + dy * dy + dz * dz, 0.25));
    }

    private static double lerp(double a, double b, double ta, double tb, double t) {
        return tb - ta < 1e-12 ? b : a + (b - a) * (t - ta) / (tb - ta);
    }

    // ---------------------------------------------------------------- network

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(name);
        buf.writeUtf(lieu);
        buf.writeUtf(world);
        buf.writeVarInt(steps.size());
        for (Step s : steps) {
            buf.writeDouble(s.x());
            buf.writeDouble(s.y());
            buf.writeDouble(s.z());
            buf.writeFloat(s.yaw());
            buf.writeFloat(s.pitch());
            buf.writeFloat(s.fov());
            buf.writeDouble(s.travel());
            buf.writeDouble(s.hold());
            buf.writeUtf(s.curve());
        }
    }

    public static CameraPath read(FriendlyByteBuf buf) {
        String name = buf.readUtf(), lieu = buf.readUtf(), world = buf.readUtf();
        List<Step> steps = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--)
            steps.add(new Step(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readDouble(), buf.readDouble(), buf.readUtf()));
        return new CameraPath(name, lieu, world, steps);
    }
}
