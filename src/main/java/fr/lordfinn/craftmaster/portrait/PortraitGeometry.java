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
 */
public final class PortraitGeometry {

    private PortraitGeometry() {}

    /** Outer size of the frame, width of its moulding, its depth and the canvas thickness (pixels). */
    public static final double FRAME_W = 26, FRAME_H = 36, BORDER = 2, DEPTH = 3, CANVAS = 1;

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

    private static final double HW = FRAME_W / 2, HH = FRAME_H / 2, HD = DEPTH / 2;

    /** Frame mouldings (top, bottom, left, right) then the canvas, in frame space (portrait space before the tilt). */
    public static final List<Box> FRAME_BOXES = List.of(
            new Box(-HW, HH - BORDER, -HD, HW, HH, HD),
            new Box(-HW, -HH, -HD, HW, -HH + BORDER, HD),
            new Box(-HW, -HH + BORDER, -HD, -HW + BORDER, HH - BORDER, HD),
            new Box(HW - BORDER, -HH + BORDER, -HD, HW, HH - BORDER, HD),
            new Box(-HW + BORDER, -HH + BORDER, -HD, HW - BORDER, HH - BORDER, -HD + CANVAS));

    /** Where the canvas' front face is (z), the figure should stay in front of it. */
    public static final double CANVAS_FRONT = -HD + CANVAS;

    /** The figure's base cube for a part (model space, relative to the part's pivot); slim arms are 3 px wide. */
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

    /** Pivot of a part in model space (same as the vanilla player model). */
    public static Vector3d pivot(Part part) {
        return switch (part) {
            case RIGHT_ARM -> new Vector3d(-5, 2, 0);
            case LEFT_ARM -> new Vector3d(5, 2, 0);
            case RIGHT_LEG -> new Vector3d(-1.9, 12, 0);
            case LEFT_LEG -> new Vector3d(1.9, 12, 0);
            default -> new Vector3d();
        };
    }

    /** The parts that have a cube, in drawing order. */
    public static final List<Part> BODY_PARTS = List.of(Part.HEAD, Part.BODY, Part.RIGHT_ARM, Part.LEFT_ARM, Part.RIGHT_LEG, Part.LEFT_LEG);

    /** Height of the hips above the feet in model space: the figure and the upper body turn around them. */
    private static final double HIPS = 12;

    private static Matrix4d rotate(Matrix4d m, Pose.Rotation r) {
        return m.rotateZYX(Math.toRadians(r.z()), Math.toRadians(r.y()), Math.toRadians(r.x()));
    }

    /** Frame space → portrait space: the frame's tilt around its centre. */
    public static Matrix4d frameMatrix(Pose pose) {
        return new Matrix4d().rotateX(Math.toRadians(pose.framePitch())).rotateZ(Math.toRadians(pose.frameRoll()));
    }

    /** Model space → portrait space for the whole figure (before the part's own pivot and rotation). */
    private static Matrix4d figureMatrix(Pose pose) {
        Matrix4d m = new Matrix4d()
                .translate(pose.offsetX(), pose.offsetY(), pose.offsetZ())
                .scale(pose.scale())
                // feet (model y = 24) on the origin, y up, the face toward the viewer: a half turn around X
                .translate(0, 24, 0)
                .rotateX(Math.PI)
                .translate(0, HIPS, 0);
        rotate(m, pose.rotation(Part.FIGURE));
        return m.translate(0, -HIPS, 0);
    }

    /** Part space (its cube's coordinates) → portrait space. */
    public static Matrix4d partMatrix(Pose pose, Part part) {
        Matrix4d m = figureMatrix(pose);
        boolean upper = part == Part.HEAD || part == Part.BODY || part == Part.RIGHT_ARM || part == Part.LEFT_ARM;
        if (upper) {
            m.translate(0, HIPS, 0);
            rotate(m, pose.rotation(Part.BODY));
            m.translate(0, -HIPS, 0);
        }
        if (part == Part.BODY) return m;
        Vector3d p = pivot(part);
        m.translate(p.x, p.y, p.z);
        return rotate(m, pose.rotation(part));
    }

    /** One weighted piece of the portrait, already placed in portrait space. */
    private record Piece(Box box, Matrix4d matrix) {}

    private static List<Piece> pieces(Pose pose, boolean slim) {
        List<Piece> out = new ArrayList<>();
        Matrix4d frame = frameMatrix(pose);
        for (Box b : FRAME_BOXES) out.add(new Piece(b, frame));
        for (Part p : BODY_PARTS) if (!pose.isHidden(p)) out.add(new Piece(partBox(p, slim), partMatrix(pose, p)));
        return out;
    }

    /**
     * Centre of gravity of the frame and the visible figure, each cuboid weighing its volume (after the pose's
     * scale), in portrait space. Same density everywhere: a figure holding the frame above its head pulls the
     * centre down, an arm stretched to the side pulls it sideways.
     */
    public static Vector3d centreOfGravity(Pose pose, boolean slim) {
        Vector3d sum = new Vector3d();
        double total = 0;
        for (Piece piece : pieces(pose, slim)) {
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
     * Where the string is tied: on the top moulding of the frame, right above the centre of gravity (same x),
     * so the portrait hangs straight. When the centre of gravity is beyond the frame's width (an arm stretched
     * far out), the knot goes to the nearest end of the moulding.
     */
    public static Vector3d hangPoint(Pose pose, boolean slim) {
        Vector3d cog = centreOfGravity(pose, slim);
        Matrix4d f = frameMatrix(pose);
        // Top edge of the frame, centre line: (t, HH, 0) for t in [-HW, HW], tilted by the frame matrix
        Vector3d a = f.transformPosition(new Vector3d(-HW, HH, 0));
        Vector3d b = f.transformPosition(new Vector3d(HW, HH, 0));
        double t = Math.abs(b.x - a.x) < 1e-9 ? 0.5 : (cog.x - a.x) / (b.x - a.x);
        t = Math.max(0, Math.min(1, t));
        return new Vector3d(a).lerp(b, t);
    }

    public static Vector3d hangPoint(Pose pose) {
        return hangPoint(pose, false);
    }

    /** Bounds of the frame and visible figure in portrait space: {minX, minY, minZ, maxX, maxY, maxZ}. */
    public static double[] bounds(Pose pose, boolean slim) {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Piece piece : pieces(pose, slim)) {
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
     * Where the portrait's pieces are relative to the hook, in blocks, before the facing and the sway: the hang
     * point sits {@code hangLength} blocks below the hook and the centre of gravity is right under it.
     * Returned as the translation to apply to portrait space (divided by 16) — x, y, z in blocks.
     */
    public static Vector3d portraitOrigin(Pose pose, boolean slim, double hangLength) {
        Vector3d cog = centreOfGravity(pose, slim);
        Vector3d knot = hangPoint(pose, slim);
        return new Vector3d(-cog.x / 16, -hangLength - knot.y / 16, -cog.z / 16);
    }
}
