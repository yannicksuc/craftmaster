package fr.lordfinn.craftmaster.client.studio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lordfinn.craftmaster.studio.SpotlightBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A spotlight's pool of light laid on the blocks it lands on, instead of one flat disc: the disc hung in the air past
 * the edge of a step and sank into walls. The disc's area is cut into half-block cells on the world grid (block and
 * slab edges fall on it); each cell finds the surface it lights (the landing plane, a step up to half a block above,
 * or down to a block and a half below, shifted along the beam as light would be), and is dropped where there is none
 * or where a block stands over it. Recomputed when the pool moves, and twice a second for blocks being changed.
 */
final class PoolCells {

    private PoolCells() {}

    /** How far below the landing plane a lower surface still catches the pool (blocks). */
    private static final double MAX_DROP = 1.5, MAX_RISE = 0.5, CELL = 0.5;

    /** Cells in block space: axis {@code a} is the face's, {@code b} and {@code c} the plane's. */
    record Cells(int a, int b, int c, int sign, double plane, double centerB, double centerC, double shiftB, double shiftC,
                 double radius, int sub, BlockPos origin, double[] cellB, double[] cellC, double[] height) {}

    private record Key(Vec3 hit, Direction face, double radius, long tick) {}

    private static final Map<SpotlightBlockEntity, Key> KEYS = new WeakHashMap<>();
    private static final Map<SpotlightBlockEntity, Cells> CACHE = new WeakHashMap<>();

    static Cells of(SpotlightBlockEntity be, Level level, Vec3 hit, Direction face, double radius, double[] dir) {
        Key key = new Key(hit, face, Math.round(radius * 100) / 100.0, level.getGameTime() / 10);
        Cells cached = CACHE.get(be);
        Key old = KEYS.get(be);
        if (cached != null && old != null && old.face == key.face && old.radius == key.radius && old.tick == key.tick
                && old.hit.distanceToSqr(hit) < 1e-6) return cached;
        Cells cells = compute(level, be.getBlockPos(), hit, face, radius, dir);
        CACHE.put(be, cells);
        KEYS.put(be, key);
        return cells;
    }

    private static double get(Vec3 v, int axis) {
        return axis == 0 ? v.x : axis == 1 ? v.y : v.z;
    }

    private static double get(double[] v, int axis) {
        return v[axis];
    }

    private static Cells compute(Level level, BlockPos origin, Vec3 hit, Direction face, double radius, double[] dir) {
        int a = face.getAxis().ordinal();
        int b = a == 0 ? 1 : 0, c = a == 2 ? 1 : 2;
        int sign = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
        double plane = get(hit, a), hb = get(hit, b), hc = get(hit, c);
        // Light reaching a surface lower by d has gone on along the beam: its pool is shifted by d / cos in the plane
        double cos = -get(dir, a) * sign;
        double shiftB = 0, shiftC = 0;
        if (cos > 0.2) {
            shiftB = get(dir, b) / cos;
            shiftC = get(dir, c) / cos;
        }
        double reach = radius + MAX_DROP * Math.min(3, Math.hypot(shiftB, shiftC));
        List<double[]> found = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double b0 = Math.floor((hb - reach) / CELL) * CELL, c0 = Math.floor((hc - reach) / CELL) * CELL;
        for (double cb = b0; cb < hb + reach; cb += CELL) {
            for (double cc = c0; cc < hc + reach; cc += CELL) {
                // Skip cells the pool (wherever it is shifted to) cannot reach
                double nb = Math.max(cb, Math.min(hb, cb + CELL)), nc = Math.max(cc, Math.min(hc, cc + CELL));
                if (Math.hypot(nb - hb, nc - hc) > reach) continue;
                double mb = cb + CELL / 2, mc = cc + CELL / 2;
                Double h = surface(level, pos, a, b, c, sign, plane, mb, mc);
                if (h == null) continue;
                double sb = hb - shiftB * h, sc = hc - shiftC * h;
                double db = Math.max(cb, Math.min(sb, cb + CELL)) - sb, dc = Math.max(cc, Math.min(sc, cc + CELL)) - sc;
                if (Math.hypot(db, dc) > radius) continue;
                found.add(new double[]{cb, cc, h});
            }
        }
        double[] cellB = new double[found.size()], cellC = new double[found.size()], height = new double[found.size()];
        for (int i = 0; i < found.size(); i++) {
            cellB[i] = found.get(i)[0];
            cellC[i] = found.get(i)[1];
            height[i] = found.get(i)[2];
        }
        int sub = radius < 1.6 ? 4 : radius < 3 ? 2 : 1;
        return new Cells(a, b, c, sign, plane, hb, hc, shiftB, shiftC, radius, sub, origin.immutable(), cellB, cellC, height);
    }

    /**
     * Height (along the face's normal, from the landing plane) of the surface lit at this point of the plane, or null:
     * no surface within reach, or a block standing over it.
     */
    private static Double surface(Level level, BlockPos.MutableBlockPos pos, int a, int b, int c, int sign, double plane,
                                  double mb, double mc) {
        int ib = (int) Math.floor(mb), ic = (int) Math.floor(mc);
        // Blocks along the normal, from the one at the top of the reach down to the bottom
        double top = plane + sign * MAX_RISE, bottom = plane - sign * MAX_DROP;
        int k0 = (int) Math.floor(top - (sign > 0 ? 1e-6 : -1e-6)), k1 = (int) Math.floor(bottom);
        for (int k = k0; sign > 0 ? k >= k1 : k <= k1; k -= sign) {
            set(pos, a, k, b, ib, c, ic);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            Double best = null;
            boolean blocked = false;
            for (AABB box : state.getShape(level, pos).toAabbs()) {
                double lb = mb - ib, lc = mc - ic;
                if (lb <= min(box, b) || lb >= max(box, b) || lc <= min(box, c) || lc >= max(box, c)) continue;
                double hi = sign > 0 ? k + max(box, a) - plane : plane - (k + min(box, a));
                double lo = sign > 0 ? k + min(box, a) - plane : plane - (k + max(box, a));
                if (lo >= MAX_RISE) continue;
                if (hi > MAX_RISE + 1e-6) blocked = true;
                else if (best == null || hi > best) best = hi;
            }
            if (blocked) return null;
            if (best != null) return best >= -MAX_DROP ? best : null;
        }
        return null;
    }

    private static void set(BlockPos.MutableBlockPos pos, int a, int va, int b, int vb, int c, int vc) {
        int[] v = new int[3];
        v[a] = va;
        v[b] = vb;
        v[c] = vc;
        pos.set(v[0], v[1], v[2]);
    }

    private static double min(AABB box, int axis) {
        return axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ;
    }

    private static double max(AABB box, int axis) {
        return axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;
    }

    /** The pool, soft and bright in the middle, on every lit cell (block space of the spotlight). */
    static void draw(VertexConsumer out, PoseStack.Pose p, Cells s, int rgb, float strength, float lift) {
        float[] xyz = new float[3];
        double step = CELL / s.sub();
        int inner = Math.round(120 * strength);
        if (inner <= 0) return;
        for (int i = 0; i < s.cellB().length; i++) {
            double h = s.height()[i];
            // Lower surfaces get a little less (the light spreads on its way down)
            double fade = 1 - Math.max(0, -h) / (MAX_DROP + 0.5);
            double sb = s.centerB() - s.shiftB() * h, sc = s.centerC() - s.shiftC() * h;
            double along = s.plane() + s.sign() * (h + lift);
            for (int u = 0; u < s.sub(); u++) {
                for (int v = 0; v < s.sub(); v++) {
                    double b0 = s.cellB()[i] + u * step, c0 = s.cellC()[i] + v * step, b1 = b0 + step, c1 = c0 + step;
                    int a00 = alpha(b0, c0, sb, sc, s.radius(), inner, fade), a10 = alpha(b1, c0, sb, sc, s.radius(), inner, fade);
                    int a11 = alpha(b1, c1, sb, sc, s.radius(), inner, fade), a01 = alpha(b0, c1, sb, sc, s.radius(), inner, fade);
                    if ((a00 | a10 | a11 | a01) == 0) continue;
                    // Both windings: lit from either side
                    vertex(out, p, s, along, b0, c0, a00, rgb, xyz);
                    vertex(out, p, s, along, b1, c0, a10, rgb, xyz);
                    vertex(out, p, s, along, b1, c1, a11, rgb, xyz);
                    vertex(out, p, s, along, b0, c1, a01, rgb, xyz);
                    vertex(out, p, s, along, b0, c1, a01, rgb, xyz);
                    vertex(out, p, s, along, b1, c1, a11, rgb, xyz);
                    vertex(out, p, s, along, b1, c0, a10, rgb, xyz);
                    vertex(out, p, s, along, b0, c0, a00, rgb, xyz);
                }
            }
        }
    }

    private static int alpha(double pb, double pc, double sb, double sc, double radius, int inner, double fade) {
        double t = 1 - Math.hypot(pb - sb, pc - sc) / radius;
        return t <= 0 ? 0 : (int) Math.round(inner * t * fade);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose p, Cells s, double along, double pb, double pc, int alpha,
                               int rgb, float[] xyz) {
        xyz[s.a()] = (float) (along - coord(s.origin(), s.a()));
        xyz[s.b()] = (float) (pb - coord(s.origin(), s.b()));
        xyz[s.c()] = (float) (pc - coord(s.origin(), s.c()));
        Geo.light(out, p, xyz[0], xyz[1], xyz[2], Geo.argb(alpha, rgb));
    }

    private static int coord(BlockPos pos, int axis) {
        return axis == 0 ? pos.getX() : axis == 1 ? pos.getY() : pos.getZ();
    }
}
