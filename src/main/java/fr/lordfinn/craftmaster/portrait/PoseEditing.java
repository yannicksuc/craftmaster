package fr.lordfinn.craftmaster.portrait;

import fr.lordfinn.craftmaster.portrait.Pose.Part;
import fr.lordfinn.craftmaster.portrait.Pose.Rotation;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The pose editor's operations, as pure functions of a {@link Pose}: mirror left ↔ right, copy a limb onto the
 * other side, reset a part, turn a part by a mouse drag or a wheel step.
 * <p>
 * Mirroring is the reflection through the figure's middle plane (model x → −x): a rotation (x, y, z) becomes
 * (x, −y, −z), the right and left limbs swap, the sideways offset and the frame's roll change sign.
 */
public final class PoseEditing {

    private PoseEditing() {}

    /** Rotation axes. */
    public enum Axis { X, Y, Z }

    /** Degrees per pixel of mouse drag (GUI pixels). */
    public static final double DEGREES_PER_PIXEL = 0.8;

    public static Rotation mirrored(Rotation r) {
        return new Rotation(r.x(), clean(-r.y()), clean(-r.z()));
    }

    /** The same limb on the other side (head, body and figure have none: themselves). */
    public static Part opposite(Part part) {
        return switch (part) {
            case RIGHT_ARM -> Part.LEFT_ARM;
            case LEFT_ARM -> Part.RIGHT_ARM;
            case RIGHT_LEG -> Part.LEFT_LEG;
            case LEFT_LEG -> Part.RIGHT_LEG;
            default -> part;
        };
    }

    /** True for the parts that have a left and a right. */
    public static boolean sided(Part part) {
        return opposite(part) != part;
    }

    /** The whole pose seen in a mirror. */
    public static Pose mirror(Pose pose) {
        Map<Part, Rotation> r = new EnumMap<>(Part.class);
        for (Part p : Part.values()) r.put(opposite(p), mirrored(pose.rotation(p)));
        Set<Part> hidden = EnumSet.noneOf(Part.class);
        for (Part p : pose.hidden()) hidden.add(opposite(p));
        return new Pose(r, hidden, clean(-pose.offsetX()), pose.offsetY(), pose.offsetZ(), pose.scale(),
                pose.framePitch(), clean(-pose.frameRoll()));
    }

    /** Gives the other limb the mirror of {@code part}'s rotation (and visibility): a symmetric pose. */
    public static Pose copyToOtherSide(Pose pose, Part part) {
        if (!sided(part)) return pose;
        Part other = opposite(part);
        return pose.withRotation(other, mirrored(pose.rotation(part))).withHidden(other, pose.isHidden(part));
    }

    /** Back to standing straight: the part's rotation, and for the whole figure its place and size too. */
    public static Pose reset(Pose pose, Part part) {
        Pose out = pose.withRotation(part, Rotation.ZERO);
        if (part == Part.FIGURE)
            out = out.withOffset(Pose.NEUTRAL.offsetX(), Pose.NEUTRAL.offsetY(), Pose.NEUTRAL.offsetZ()).withScale(Pose.NEUTRAL.scale());
        return out;
    }

    /** The frame back to straight. */
    public static Pose resetFrame(Pose pose) {
        return pose.withFrame(0, 0);
    }

    /** Adds {@code degrees} around one axis, kept within −180..180 and rounded to 0.1°. */
    public static Pose turn(Pose pose, Part part, Axis axis, double degrees) {
        Rotation r = pose.rotation(part);
        Rotation n = switch (axis) {
            case X -> new Rotation(wrap(r.x() + degrees), r.y(), r.z());
            case Y -> new Rotation(r.x(), wrap(r.y() + degrees), r.z());
            case Z -> new Rotation(r.x(), r.y(), wrap(r.z() + degrees));
        };
        return pose.withRotation(part, n);
    }

    /**
     * The two axes a mouse drag turns, {horizontal drag, vertical drag}: the head, body and figure turn (Y) and
     * nod (X); arms and legs swing sideways (Z) and forward or back (X). With {@code alternate} (Shift held) the
     * horizontal drag uses the remaining axis instead: tilt (Z) for the head, body and figure, twist (Y) for limbs.
     */
    public static Axis[] dragAxes(Part part, boolean alternate) {
        boolean limb = part == Part.RIGHT_ARM || part == Part.LEFT_ARM || part == Part.RIGHT_LEG || part == Part.LEFT_LEG;
        Axis horizontal = limb ? (alternate ? Axis.Y : Axis.Z) : (alternate ? Axis.Z : Axis.Y);
        return new Axis[]{horizontal, Axis.X};
    }

    /**
     * Turns a part by a mouse drag of (dx, dy) GUI pixels, the figure seen from the front: what is under the mouse
     * follows it (drag right = the limb's end, the face, goes right; drag down = it goes down / forward).
     */
    public static Pose drag(Pose pose, Part part, double dx, double dy, boolean alternate) {
        Axis[] axes = dragAxes(part, alternate);
        // Model space has y down and the face toward −z, turned to face the viewer: +θ around Y moves the face
        // to the viewer's left, +θ around Z moves a hanging limb's end to the left but the top of the head to the
        // right, +θ around X lowers the face and brings a hanging limb's end back
        boolean upright = !sided(part);
        double h = (axes[0] == Axis.Z && upright ? dx : -dx) * DEGREES_PER_PIXEL;
        double v = dy * DEGREES_PER_PIXEL;
        return turn(turn(pose, part, axes[0], h), part, axes[1], v);
    }

    /** Frame tilt by a drag: vertical = lean (pitch), horizontal = turn in its plane (roll). */
    public static Pose dragFrame(Pose pose, double dx, double dy) {
        return pose.withFrame(clamp(round(pose.framePitch() + dy * DEGREES_PER_PIXEL * 0.5), -60, 60),
                clamp(round(pose.frameRoll() - dx * DEGREES_PER_PIXEL * 0.5), -60, 60));
    }

    /** Moves the whole figure by a drag of (dx, dy) portrait pixels (y up). */
    public static Pose move(Pose pose, double dx, double dy) {
        return pose.withOffset(clamp(round(pose.offsetX() + dx), -24, 24), clamp(round(pose.offsetY() + dy), -48, 24), pose.offsetZ());
    }

    public static double wrap(double degrees) {
        double d = degrees % 360;
        if (d > 180) d -= 360;
        if (d <= -180) d += 360;
        return round(d);
    }

    private static double round(double v) {
        return clean(Math.round(v * 10) / 10.0);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /** No "-0.0" in poses.json. */
    private static double clean(double v) {
        return v == 0 ? 0 : v;
    }
}
