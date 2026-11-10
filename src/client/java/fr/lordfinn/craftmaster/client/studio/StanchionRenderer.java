package fr.lordfinn.craftmaster.client.studio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.studio.StanchionBlock;
import fr.lordfinn.craftmaster.studio.StanchionBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The velvet ropes between stanchions: from this stanchion's top ring to the next one east and south (each rope is
 * drawn once, by its western / northern end), sagging in the middle.
 */
public class StanchionRenderer implements BlockEntityRenderer<StanchionBlockEntity, StanchionRenderer.State> {

    public static final Identifier TEXTURE = CraftMaster.id("textures/entity/velvet_rope.png");
    private static final Geo.Tex TEX = new Geo.Tex(16, 16);
    /** Height of the ring the rope hangs from (blocks) and the rope's thickness. */
    private static final double RING = 13.5 / 16, THICK = 1.2 / 16;
    private static final int SEGMENTS = 12;

    public static class State extends BlockEntityRenderState {
        final List<Vec3> ends = new ArrayList<>();
    }

    public StanchionRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(StanchionBlockEntity be, State s, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, camera, crumbling);
        s.ends.clear();
        if (be.getLevel() == null) return;
        for (boolean east : new boolean[]{true, false}) {
            BlockPos to = StanchionBlock.ropeTarget(be.getLevel(), be.getBlockPos(), east);
            if (to != null) s.ends.add(Vec3.atLowerCornerOf(to.subtract(be.getBlockPos())));
        }
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (s.ends.isEmpty()) return;
        int light = s.lightCoords;
        List<Vec3> ends = List.copyOf(s.ends);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (p, c) -> {
            for (Vec3 end : ends) rope(c, p, new Vec3(0.5, RING, 0.5), end.add(0.5, RING, 0.5), light);
        });
    }

    /** A sagging rope as a chain of short square tubes (side faces only). */
    private static void rope(VertexConsumer c, PoseStack.Pose p, Vec3 from, Vec3 to, int light) {
        double span = from.distanceTo(to);
        double sag = 0.12 * span;
        Vec3 prev = from;
        boolean alongX = Math.abs(to.x - from.x) > Math.abs(to.z - from.z);
        for (int i = 1; i <= SEGMENTS; i++) {
            double t = i / (double) SEGMENTS;
            Vec3 cur = from.lerp(to, t).add(0, -4 * sag * t * (1 - t), 0);
            tube(c, p, prev, cur, alongX, light, (i - 1) * 16f / SEGMENTS, i * 16f / SEGMENTS);
            prev = cur;
        }
    }

    private static void tube(VertexConsumer c, PoseStack.Pose p, Vec3 a, Vec3 b, boolean alongX, int light, float u0, float u1) {
        float h = (float) THICK / 2;
        // Side offsets: across the rope horizontally (sx, sz) and vertically
        float sx = alongX ? 0 : h, sz = alongX ? h : 0;
        int w = 0xFFFFFFFF;
        float ax = (float) a.x, ay = (float) a.y, az = (float) a.z, bx = (float) b.x, by = (float) b.y, bz = (float) b.z;
        // top, bottom, both sides; each with both windings so twists never hide a face
        face(c, p, ax - sx, ay + h, az - sz, ax + sx, ay + h, az + sz, bx + sx, by + h, bz + sz, bx - sx, by + h, bz - sz, u0, u1, w, light);
        face(c, p, ax - sx, ay - h, az - sz, ax + sx, ay - h, az + sz, bx + sx, by - h, bz + sz, bx - sx, by - h, bz - sz, u0, u1, w, light);
        face(c, p, ax - sx, ay + h, az - sz, ax - sx, ay - h, az - sz, bx - sx, by - h, bz - sz, bx - sx, by + h, bz - sz, u0, u1, w, light);
        face(c, p, ax + sx, ay + h, az + sz, ax + sx, ay - h, az + sz, bx + sx, by - h, bz + sz, bx + sx, by + h, bz + sz, u0, u1, w, light);
    }

    private static void face(VertexConsumer c, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, float u0, float u1, int w, int light) {
        Geo.quad(c, p, TEX, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, u0, 6, u1, 8, w, light, 0, 1, 0);
        Geo.quad(c, p, TEX, x3, y3, z3, x2, y2, z2, x1, y1, z1, x0, y0, z0, u1, 6, u0, 8, w, light, 0, 1, 0);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
