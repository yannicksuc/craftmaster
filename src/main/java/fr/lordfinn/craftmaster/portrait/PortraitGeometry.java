package fr.lordfinn.craftmaster.portrait;

import fr.lordfinn.craftmaster.portrait.Pose.Part;
import org.joml.Matrix4d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Shapes of a portrait: the frame and the posed figure, as cuboids. Shared by the renderer (which draws exactly
 * these transforms) and by the hang point computation, so the string always ends where the drawing expects it.
 * <p>
 * "Portrait space": pixels (1/16 block), origin at the centre of the frame, x to the viewer's right, y up,
 * z toward the viewer. Figure cubes are given like the vanilla player model ("model space": y down, the face
 * looking at -z, the feet at y = 24) and placed in portrait space by {@link #partMatrix}.
 * <p>
 * The figure has <b>baby proportions</b> ("baby zombie mode"), exactly those of vanilla baby humanoids
 * ({@code HumanoidModel.BABY_TRANSFORMER} = {@code BabyModelTransform(true, 16, 0, 2, 2, 24, {"head"})}): the head
 * is moved down 16 px then scaled by 1.5 / 2 = 0.75, the body, arms and legs are moved down 24 px then scaled by
 * 1 / 2 (pivots scaled too). The figure is 18 px tall with a big head; the renderer bakes the player model through
 * that same transformer and checks it against {@link #pivot} and {@link #partScale}.
 * <p>
 * The canvas is a real cut (done by the renderer): in front of the painted surface the figure is solid, behind it
 * the figure is inside the painting and not drawn — a figure leaning out keeps its legs "in the picture". A figure
 * turned toward the back ({@link #behindCanvas}) comes out of the flat back instead, and is cut by the back plane. With the "window" look (Antichamber) the figure is pushed behind the opening and drawn as an illusion
 * flattened onto the canvas plane (see PortraitRenderer): from outside the portrait stays a thin frame.
 */
public final class PortraitGeometry {

    private PortraitGeometry() {}

    /** Outer size of the frame, width of its moulding, its depth and the canvas thickness (pixels). */
    public static final double FRAME_W = 18, FRAME_H = 24, BORDER = 2, DEPTH = 3, CANVAS = 1;

    /** An axis-aligned cuboid in its own space. */
    public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
        public double volume() {
            return (x1 - x0) * (y1 - y0) * (z1 - z0);
        }

        public Vector3d center() {
            return new Vector3d((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2);
        }

        public List<Vector3d> corners() {
            List<Vector3d> c = new ArrayList<>(8);
            for (int i = 0; i < 8; i++)
                c.add(new Vector3d((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1));
            return c;
        }
    }

    /** Half sizes of the frame (outer) and of its opening. */
    public static final double HW = FRAME_W / 2, HH = FRAME_H / 2, HD = DEPTH / 2,
            OPEN_HW = HW - BORDER, OPEN_HH = HH - BORDER;

    /** Frame mouldings (top, bottom, left, right), in frame space (portrait space before the tilt). */
    public static final List<Box> MOULDINGS = List.of(
            new Box(-HW, HH - BORDER, -HD, HW, HH, HD),
            new Box(-HW, -HH, -HD, HW, -HH + BORDER, HD),
            new Box(-HW, -HH + BORDER, -HD, -HW + BORDER, HH - BORDER, HD),
            new Box(HW - BORDER, -HH + BORDER, -HD, HW, HH - BORDER, HD));

    /** The painted canvas closing the opening at the back (not drawn with the window look). */
    public static final Box CANVAS_BOX = new Box(-OPEN_HW, -OPEN_HH, -HD, OPEN_HW, OPEN_HH, -HD + CANVAS);

    /** Mouldings then canvas. */
    public static final List<Box> FRAME_BOXES = List.of(MOULDINGS.get(0), MOULDINGS.get(1), MOULDINGS.get(2), MOULDINGS.get(3), CANVAS_BOX);

    /** Where the canvas' front face is (z): a pose in front of the canvas keeps its figure in front of that. */
    public static final double CANVAS_FRONT = -HD + CANVAS;
    /** Back of the canvas (z). */
    public static final double CANVAS_BACK = -HD;
    /** With the window look, the figure stands this far (px) behind the canvas plane. */
    public static final double WINDOW_DEPTH = 2;

    /**
     * The figure's base cube for a part, as in the adult player model (relative to the part's pivot, before
     * {@link #partScale}); slim arms are 3 px wide.
     */
    public static Box partBox(Part part, boolean slim) {
        return switch (part) {
            case HEAD -> new Box(-4, -8, -4, 4, 0, 4);
            case BODY -> new Box(-4, 0, -2, 4, 12, 2);
            case RIGHT_ARM -> slim ? new Box(-2, -2, -2, 1, 10, 2) : new Box(-3, -2, -2, 1, 10, 2);
            case LEFT_ARM -> slim ? new Box(-1, -2, -2, 2, 10, 2) : new Box(-1, -2, -2, 3, 10, 2);
            case RIGHT_LEG, LEFT_LEG -> new Box(-2, 0, -2, 2, 12, 2);
            case FIGURE -> throw new IllegalArgumentException("the whole figure has no cube of its own");
        };
    }

    /** Vanilla baby transform: head offset and scale, body (and limbs) offset and scale. */
    // The head is bigger than vanilla's babies (0.95 instead of 0.75, LordFinn's choice: a chibi look); its pivot
    // stays on top of the body (12 px), so the neck does not move
    public static final double BABY_HEAD_SCALE = 0.95, BABY_HEAD_Y = 12 / BABY_HEAD_SCALE, BABY_BODY_Y = 24, BABY_BODY_SCALE = 1.0 / 2;

    /** Pivot of a part in the adult player model. */
    private static Vector3d adultPivot(Part part) {
        return switch (part) {
            case RIGHT_ARM -> new Vector3d(-5, 2, 0);
            case LEFT_ARM -> new Vector3d(5, 2, 0);
            case RIGHT_LEG -> new Vector3d(-1.9, 12, 0);
            case LEFT_LEG -> new Vector3d(1.9, 12, 0);
            default -> new Vector3d();
        };
    }

    /** Pivot of a part in model space, baby proportions: (adult pivot + offset) × scale, like PartPose.translated().scaled(). */
    public static Vector3d pivot(Part part) {
        Vector3d p = adultPivot(part);
        return part == Part.HEAD
                ? p.add(0, BABY_HEAD_Y, 0).mul(BABY_HEAD_SCALE)
                : p.add(0, BABY_BODY_Y, 0).mul(BABY_BODY_SCALE);
    }

    /** Scale of a part's cubes (baby proportions). */
    public static double partScale(Part part) {
        return part == Part.HEAD ? BABY_HEAD_SCALE : BABY_BODY_SCALE;
    }

    /** The parts that have a cube, in drawing order. */
    public static final List<Part> BODY_PARTS = List.of(Part.HEAD, Part.BODY, Part.RIGHT_ARM, Part.LEFT_ARM, Part.RIGHT_LEG, Part.LEFT_LEG);

    /** The hips in model space (y of the legs' pivot): the figure and the upper body turn around them. */
    private static final double HIPS = 18;

    private static Matrix4d rotate(Matrix4d m, Pose.Rotation r) {
        return m.rotateZYX(Math.toRadians(r.z()), Math.toRadians(r.y()), Math.toRadians(r.x()));
    }

    /** Frame space → portrait space: the frame's tilt around its centre. */
    public static Matrix4d frameMatrix(Pose pose) {
        return new Matrix4d().rotateX(Math.toRadians(pose.framePitch())).rotateZ(Math.toRadians(pose.frameRoll()));
    }

    /** Model space → portrait space for the whole figure, moved by {@code shiftZ} px toward the viewer. */
    private static Matrix4d figureMatrix(Pose pose, double shiftZ) {
        Matrix4d m = new Matrix4d()
                .translate(pose.offsetX(), pose.offsetY(), pose.offsetZ() + shiftZ)
                .scale(pose.scale())
                // feet (model y = 24) on the origin, y up, the face toward the viewer: a half turn around X
                .translate(0, 24, 0)
                .rotateX(Math.PI)
                .translate(0, HIPS, 0);
        rotate(m, pose.rotation(Part.FIGURE));
        return m.translate(0, -HIPS, 0);
    }

    private static Matrix4d partMatrix(Pose pose, Part part, double shiftZ) {
        Matrix4d m = figureMatrix(pose, shiftZ);
        boolean upper = part == Part.HEAD || part == Part.BODY || part == Part.RIGHT_ARM || part == Part.LEFT_ARM;
        if (upper) {
            m.translate(0, HIPS, 0);
            rotate(m, pose.rotation(Part.BODY));
            m.translate(0, -HIPS, 0);
        }
        Vector3d p = pivot(part);
        m.translate(p.x, p.y, p.z);
        // The body's own rotation is the upper body's, applied above around the hips
        if (part != Part.BODY) rotate(m, pose.rotation(part));
        return m.scale(partScale(part));
    }

    /** z range {min, max} of the visible figure, before any shift. */
    private static double[] figureDepth(Pose pose) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (Part p : BODY_PARTS) {
            if (pose.isHidden(p)) continue;
            Matrix4d m = partMatrix(pose, p, 0);
            for (Vector3d c : partBox(p, false).corners()) {
                m.transformPosition(c);
                min = Math.min(min, c.z);
                max = Math.max(max, c.z);
            }
        }
        return min > max ? new double[]{0, 0} : new double[]{min, max};
    }

    /**
     * True when the figure comes out of the flat back of the portrait: it faces the back (whole figure turned
     * more than 90° around Y). It is then cut by the canvas' back instead of its painted face.
     */
    public static boolean behindCanvas(Pose pose) {
        return Math.cos(Math.toRadians(pose.rotation(Part.FIGURE).y())) < 0;
    }

    /**
     * The plane cutting the figure, in frame space: z = this value. A front pose keeps what is in front of the
     * canvas' painted face, a pose coming out of the back keeps what is behind the canvas' back.
     */
    public static double cutPlane(Pose pose) {
        return behindCanvas(pose) ? CANVAS_BACK : CANVAS_FRONT;
    }

    /**
     * How far (px, toward the viewer) the figure is moved: not at all normally (the canvas cuts it), and with the
     * window look, behind the canvas plane so it is entirely "inside" the window.
     */
    public static double figureShift(Pose pose, boolean window) {
        if (!window) return 0;
        double[] z = figureDepth(pose);
        return Math.min(0, CANVAS_FRONT - WINDOW_DEPTH - z[1]);
    }

    /** z of the back of the figure as drawn (portrait space): the window's room must be deeper than that. */
    public static double figureBackZ(Pose pose, boolean window) {
        return figureDepth(pose)[0] + figureShift(pose, window);
    }

    /** Part space (its cube's coordinates) → portrait space, as drawn (normal look). */
    public static Matrix4d partMatrix(Pose pose, Part part) {
        return partMatrix(pose, part, false);
    }

    /** Part space (its cube's coordinates) → portrait space, as drawn. */
    public static Matrix4d partMatrix(Pose pose, Part part, boolean window) {
        return partMatrix(pose, part, figureShift(pose, window));
    }

    /** One weighted piece of the portrait, already placed in portrait space. */
    private record Piece(Box box, Matrix4d matrix) {}

    private static List<Piece> pieces(Pose pose, boolean window) {
        List<Piece> out = new ArrayList<>();
        Matrix4d frame = frameMatrix(pose);
        for (Box b : window ? MOULDINGS : FRAME_BOXES) out.add(new Piece(b, frame));
        double shift = figureShift(pose, window);
        for (Part p : BODY_PARTS)
            if (!pose.isHidden(p)) out.add(new Piece(partBox(p, false), partMatrix(pose, p, shift)));
        return out;
    }

    /**
     * Centre of gravity of the frame and the visible figure, each cuboid weighing its volume (after the baby
     * proportions and the pose's scale), in portrait space. Same density everywhere: a figure holding the frame
     * above its head pulls the centre down, an arm stretched to the side pulls it sideways. The parts hidden in
     * the painting still weigh (they are "there"); the window look's backdrop is an illusion and weighs nothing.
     */
    public static Vector3d centreOfGravity(Pose pose, boolean window) {
        Vector3d sum = new Vector3d();
        double total = 0;
        for (Piece piece : pieces(pose, window)) {
            // An affine map sends a box's centre to the centre of its image; its volume scales by the determinant
            double v = piece.box().volume() * Math.abs(piece.matrix().determinant());
            Vector3d c = piece.matrix().transformPosition(piece.box().center());
            sum.add(c.mul(v));
            total += v;
        }
        return total > 0 ? sum.div(total) : sum;
    }

    public static Vector3d centreOfGravity(Pose pose) {
        return centreOfGravity(pose, false);
    }

    /**
     * Where the string is tied: on the top face of the top moulding, right above the centre of gravity (same x
     * and z), so the string hangs perfectly vertical and the portrait straight under it. When the centre of
     * gravity is beyond the moulding (an arm stretched far out), the knot goes to the nearest point of it.
     */
    public static Vector3d hangPoint(Pose pose, boolean window) {
        Vector3d cog = centreOfGravity(pose, window);
        Matrix4d f = frameMatrix(pose);
        // Top face of the frame: f · (t, HH, s), t in [-HW, HW], s in [-HD, HD]; x and z are linear in (t, s)
        Vector3d o = f.transformPosition(new Vector3d(0, HH, 0));
        Vector3d dt = f.transformDirection(new Vector3d(1, 0, 0));
        Vector3d ds = f.transformDirection(new Vector3d(0, 0, 1));
        double det = dt.x * ds.z - ds.x * dt.z;
        double ex = cog.x - o.x, ez = cog.z - o.z;
        double t = Math.abs(det) < 1e-9 ? 0 : (ex * ds.z - ds.x * ez) / det;
        double s = Math.abs(det) < 1e-9 ? 0 : (dt.x * ez - ex * dt.z) / det;
        t = Math.max(-HW, Math.min(HW, t));
        s = Math.max(-HD, Math.min(HD, s));
        return f.transformPosition(new Vector3d(t, HH, s));
    }

    public static Vector3d hangPoint(Pose pose) {
        return hangPoint(pose, false);
    }

    /** Bounds of the frame and visible figure in portrait space: {minX, minY, minZ, maxX, maxY, maxZ}. */
    public static double[] bounds(Pose pose, boolean window) {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        List<Piece> pieces = pieces(pose, window);
        for (Piece piece : pieces) {
            for (Vector3d c : piece.box().corners()) {
                piece.matrix().transformPosition(c);
                b[0] = Math.min(b[0], c.x);
                b[1] = Math.min(b[1], c.y);
                b[2] = Math.min(b[2], c.z);
                b[3] = Math.max(b[3], c.x);
                b[4] = Math.max(b[4], c.y);
                b[5] = Math.max(b[5], c.z);
            }
        }
        return b;
    }

    /**
     * Where portrait space sits relative to the hook, in blocks, before the facing and the sway: the knot is
     * {@code hangLength} blocks straight below the hook (so the string is one vertical line) and the centre of
     * gravity right under the knot.
     */
    public static Vector3d portraitOrigin(Pose pose, boolean window, double hangLength) {
        Vector3d knot = hangPoint(pose, window);
        return new Vector3d(-knot.x / 16, -hangLength - knot.y / 16, -knot.z / 16);
    }
}
