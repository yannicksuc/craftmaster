package fr.lordfinn.craftmaster.portrait;

import fr.lordfinn.craftmaster.portrait.Pose.Part;
import org.joml.Matrix4d;
import org.joml.Vector3d;

import java.util.List;

/**
 * What the mouse points at on the pose editor's 3D preview: a part of the figure or the frame. The preview is an
 * orthographic view of portrait space ({@link PortraitGeometry}) turned by {@link View}; the same view is used to
 * draw it, so a click lands on what is drawn under the cursor.
 */
public final class PoseHitTest {

    private PoseHitTest() {}

    /**
     * How the preview shows portrait space: turned by {@code yaw} (around the vertical, degrees, positive: the
     * portrait's right side comes toward the viewer) then {@code pitch} (around the horizontal, positive: seen from
     * above), {@code scale} GUI pixels per portrait pixel, the frame's centre at ({@code cx}, {@code cy}).
     */
    public record View(double yaw, double pitch, double scale, double cx, double cy) {

        /** Portrait space → view space (x right, y up, z toward the viewer, portrait pixels). */
        public Matrix4d rotation() {
            return new Matrix4d().rotateX(Math.toRadians(pitch)).rotateY(Math.toRadians(-yaw));
        }

        /** Where a point of portrait space is drawn: {screen x, screen y, depth (bigger = nearer)}. */
        public double[] project(Vector3d p) {
            Vector3d v = rotation().transformPosition(new Vector3d(p));
            return new double[]{cx + v.x * scale, cy - v.y * scale, v.z};
        }

        public View turned(double dyaw, double dpitch) {
            return new View(yaw + dyaw, Math.max(-80, Math.min(80, pitch + dpitch)), scale, cx, cy);
        }
    }

    /** A hit: the part (null = the frame) and how near the viewer it is (view space z). */
    public record Hit(Part part, double depth) {
        public boolean frame() {
            return part == null;
        }
    }

    public static Hit pick(Pose pose, View view, double sx, double sy, boolean slim) {
        return pick(pose, view, sx, sy, slim, false);
    }

    /**
     * The frame's pieces that stop the mouse: the canvas too, except with the window look seen from the front
     * (one sees through the opening into the room); from behind, its back plate hides everything.
     */
    private static List<PortraitGeometry.Box> frameBoxes(Pose pose, View view, boolean window) {
        if (!window) return PortraitGeometry.FRAME_BOXES;
        Vector3d facing = view.rotation().transformDirection(PortraitGeometry.frameMatrix(pose).transformDirection(new Vector3d(0, 0, 1)));
        return facing.z > 0 ? PortraitGeometry.MOULDINGS : PortraitGeometry.FRAME_BOXES;
    }

    /**
     * What is drawn under the screen point (sx, sy): the part or the frame nearest to the viewer, so a leg behind
     * the canvas is not picked through it. Hidden parts cannot be hit. Null: nothing there.
     */
    public static Hit pick(Pose pose, View view, double sx, double sy, boolean slim, boolean window) {
        // The ray, in view space: from far in front of the screen point straight into the screen
        Matrix4d toPortrait = view.rotation().invert();
        Vector3d origin = toPortrait.transformPosition(new Vector3d((sx - view.cx()) / view.scale(), -(sy - view.cy()) / view.scale(), 1e4));
        Vector3d dir = toPortrait.transformDirection(new Vector3d(0, 0, -1));
        Hit best = null;
        for (Part p : PortraitGeometry.BODY_PARTS) {
            if (pose.isHidden(p)) continue;
            double t = rayBox(PortraitGeometry.partMatrix(pose, p), PortraitGeometry.partBox(p, slim), origin, dir);
            if (t >= 0 && (best == null || 1e4 - t > best.depth())) best = new Hit(p, 1e4 - t);
        }
        Matrix4d frame = PortraitGeometry.frameMatrix(pose);
        for (PortraitGeometry.Box b : frameBoxes(pose, view, window)) {
            double t = rayBox(frame, b, origin, dir);
            if (t >= 0 && (best == null || 1e4 - t > best.depth())) best = new Hit(null, 1e4 - t);
        }
        return best;
    }

    /**
     * The point under (sx, sy) on the nearest visible surface, figure and frame alike (where to tie the string), in
     * the space of what was hit: a part's own space, or frame space. Null: nothing there.
     */
    public static Pose.Attach pickAttach(Pose pose, View view, double sx, double sy, boolean slim, boolean window) {
        // Seen from the front with the window look, the canvas plane is open: the frame pieces are only the mouldings
        // (and a part in the room is skipped above); from behind, the back plate catches the click
        Matrix4d toPortrait = view.rotation().invert();
        Vector3d origin = toPortrait.transformPosition(new Vector3d((sx - view.cx()) / view.scale(), -(sy - view.cy()) / view.scale(), 1e4));
        Vector3d dir = toPortrait.transformDirection(new Vector3d(0, 0, -1));
        double bestT = Double.MAX_VALUE;
        Pose.Attach best = null;
        for (Part p : PortraitGeometry.BODY_PARTS) {
            if (pose.isHidden(p)) continue;
            Matrix4d m = PortraitGeometry.partMatrix(pose, p);
            double t = rayBox(m, PortraitGeometry.partBox(p, slim), origin, dir);
            if (t >= 0 && t < bestT) {
                Vector3d at = new Vector3d(dir).mul(t).add(origin);
                // Window look: what is behind the canvas plane is an illusion in the room, the string cannot reach it
                if (window && new Matrix4d(PortraitGeometry.frameMatrix(pose)).invert().transformPosition(new Vector3d(at)).z
                        < PortraitGeometry.CANVAS_FRONT - 1e-3) continue;
                bestT = t;
                Vector3d local = new Matrix4d(m).invert().transformPosition(at);
                best = new Pose.Attach(p, round(local.x), round(local.y), round(local.z));
            }
        }
        Matrix4d frame = PortraitGeometry.frameMatrix(pose);
        for (PortraitGeometry.Box b : frameBoxes(pose, view, window)) {
            double t = rayBox(frame, b, origin, dir);
            if (t >= 0 && t < bestT) {
                bestT = t;
                Vector3d local = new Matrix4d(frame).invert().transformPosition(new Vector3d(dir).mul(t).add(origin));
                best = new Pose.Attach(null, round(local.x), round(local.y), round(local.z));
            }
        }
        return best;
    }

    /** To a hundredth of a pixel: poses.json stays readable. */
    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /**
     * Distance along the ray (portrait space, {@code dir} of length 1) to a box given in its own space mapped by
     * {@code m}, or −1 if the ray misses it.
     */
    static double rayBox(Matrix4d m, PortraitGeometry.Box box, Vector3d origin, Vector3d dir) {
        Matrix4d inv = new Matrix4d(m).invert();
        Vector3d o = inv.transformPosition(new Vector3d(origin));
        Vector3d d = inv.transformDirection(new Vector3d(dir));
        double tmin = -Double.MAX_VALUE, tmax = Double.MAX_VALUE;
        double[] os = {o.x, o.y, o.z}, ds = {d.x, d.y, d.z};
        double[] lo = {box.x0(), box.y0(), box.z0()}, hi = {box.x1(), box.y1(), box.z1()};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(ds[i]) < 1e-12) {
                if (os[i] < lo[i] || os[i] > hi[i]) return -1;
                continue;
            }
            double t1 = (lo[i] - os[i]) / ds[i], t2 = (hi[i] - os[i]) / ds[i];
            tmin = Math.max(tmin, Math.min(t1, t2));
            tmax = Math.min(tmax, Math.max(t1, t2));
        }
        if (tmax < tmin || tmax < 0) return -1;
        // The parameter is the same in both spaces (affine map); dir has length 1 in portrait space
        return Math.max(0, tmin);
    }
}
