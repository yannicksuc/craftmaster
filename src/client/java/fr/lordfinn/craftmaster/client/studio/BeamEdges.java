package fr.lordfinn.craftmaster.client.studio;

import fr.lordfinn.craftmaster.studio.SpotlightBlockEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * How far each side of a spotlight's beam goes before it meets a block. The beam is a cone of flat strips: cut by the
 * depth test where it sank into the floor, a step, a bed or a wall, each strip ended at its own place in a hard edge,
 * a zigzag that changed with where it was seen from (inside the beam or not, near or far). Each strip's edge is now
 * traced on its own and the strip fades to nothing just before it, so nothing visible is ever cut.
 * Cached per spotlight; traced again when the aim or the cone moves, and twice a second for blocks being changed.
 */
final class BeamEdges {

    private BeamEdges() {}

    /** Lengths along the beam's axis (blocks, head space), one per side, for the wide and the narrow layer. */
    record Edges(float[] outer, float[] inner) {}

    /** Where the strips start being traced: past the spotlight's own block whatever the aim. */
    private static final double START = 0.9;
    /** How far before the block a strip ends (blocks): its last bit is dark, nothing left to cut. */
    private static final double MARGIN = 0.08;

    private record Key(float yaw, float pitch, float cone, double length, long tick) {}

    private static final Map<SpotlightBlockEntity, Key> KEYS = new WeakHashMap<>();
    private static final Map<SpotlightBlockEntity, Edges> CACHE = new WeakHashMap<>();

    /**
     * @param pivot  the head's pivot (world)
     * @param z0     where the beam starts (head space)
     * @param length the axis' length (to the block it lands on, or the open length)
     * @param landed did the axis land on a block (the far side of a slanted beam then goes on past it, to the floor)
     * @param r0     radius at z0 (outer layer), {@code r1} at {@code length}; the narrow layer is 0.55 / 0.45 of them
     */
    static Edges of(SpotlightBlockEntity be, Level level, Vec3 pivot, float yaw, float pitch, float cone, double z0,
                    double length, boolean landed, double r0, double r1, int sides, double max) {
        Key key = new Key(yaw, pitch, cone, Math.round(length * 100) / 100.0, level.getGameTime() / 10);
        Edges cached = CACHE.get(be);
        if (cached != null && key.equals(KEYS.get(be)) && cached.outer().length == sides) return cached;
        Matrix3f rot = new Matrix3f().rotateY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(pitch));
        double reach = landed ? Math.min(max, length * 1.6 + 1) : length;
        Edges edges = new Edges(
                trace(level, pivot, rot, z0, length, reach, r0, r1, sides),
                trace(level, pivot, rot, z0, length, reach, r0 * 0.55, r1 * 0.45, sides));
        CACHE.put(be, edges);
        KEYS.put(be, key);
        return edges;
    }

    /** Radius of a layer at {@code z}: straight from r0 at z0 to r1 at the axis' end, going on the same way past it. */
    static double radius(double z, double z0, double length, double r0, double r1) {
        return length <= z0 ? r1 : r0 + (r1 - r0) * (z - z0) / (length - z0);
    }

    private static float[] trace(Level level, Vec3 pivot, Matrix3f rot, double z0, double length, double reach,
                                 double r0, double r1, int sides) {
        float[] out = new float[sides];
        for (int i = 0; i < sides; i++) {
            double a = 2 * Math.PI * i / sides;
            if (reach <= START + MARGIN) {
                out[i] = (float) length;
                continue;
            }
            Vec3 from = point(pivot, rot, a, START, radius(START, z0, length, r0, r1));
            Vec3 to = point(pivot, rot, a, reach, radius(reach, z0, length, r0, r1));
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
            double z = reach;
            if (hit.getType() == HitResult.Type.BLOCK) {
                double t = hit.getLocation().distanceTo(from) / Math.max(1e-6, to.distanceTo(from));
                z = START + t * (reach - START);
            }
            out[i] = (float) Math.max(START, z - MARGIN);
        }
        return out;
    }

    private static Vec3 point(Vec3 pivot, Matrix3f rot, double a, double z, double r) {
        Vector3f v = rot.transform(new Vector3f((float) (Math.cos(a) * r), (float) (Math.sin(a) * r), (float) z));
        return pivot.add(v.x, v.y, v.z);
    }
}
