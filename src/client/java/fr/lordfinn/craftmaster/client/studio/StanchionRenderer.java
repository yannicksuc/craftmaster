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
 * drawn once, by its western / northern end), sagging in the middle: a twisted velvet cord.
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
        // One face per side, wound outward, back faces culled: nothing drawn twice in the same place (the old rope
        // drew every face both ways, which flickered all along it)
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutCull(TEXTURE), (p, c) -> {
            for (Vec3 end : ends) rope(c, p, new Vec3(0.5, RING, 0.5), end.add(0.5, RING, 0.5), light);
        });
    }

    /**
     * A sagging rope: one continuous square tube through {@link #SEGMENTS} points of the curve. Its four sides keep
     * the same offsets all along (across the rope horizontally, and up), so neighbouring segments share their edges
     * exactly: no overlap, no gap. Each side has its own outward normal (lit like a block's sides).
     */
    private static void rope(VertexConsumer c, PoseStack.Pose p, Vec3 from, Vec3 to, int light) {
        double span = from.distanceTo(to);
        double sag = 0.12 * span;
        Vec3 along = new Vec3(to.x - from.x, 0, to.z - from.z).normalize();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 across = along.cross(up).normalize();     // horizontal, perpendicular to the rope
        double h = THICK / 2;
        // Sides: outward normal n, width axis w = along x n (so that the quad winds outward)
        Vec3[][] sides = {{up, along.cross(up)}, {up.scale(-1), along.cross(up.scale(-1))},
                {across, along.cross(across)}, {across.scale(-1), along.cross(across.scale(-1))}};
        Vec3 prev = from;
        for (int i = 1; i <= SEGMENTS; i++) {
            double t = i / (double) SEGMENTS;
            Vec3 cur = from.lerp(to, t).add(0, -4 * sag * t * (1 - t), 0);
            float u0 = (i - 1) * 16f / SEGMENTS, u1 = i * 16f / SEGMENTS;
            for (Vec3[] side : sides) {
                Vec3 o = side[0].scale(h), w = side[1].normalize().scale(h);
                Vec3 a0 = prev.add(o).subtract(w), a1 = prev.add(o).add(w), b1 = cur.add(o).add(w), b0 = cur.add(o).subtract(w);
                Geo.quad(c, p, TEX, (float) a0.x, (float) a0.y, (float) a0.z, (float) a1.x, (float) a1.y, (float) a1.z,
                        (float) b1.x, (float) b1.y, (float) b1.z, (float) b0.x, (float) b0.y, (float) b0.z,
                        u0, 6, u1, 8, 0xFFFFFFFF, light, (float) side[0].x, (float) side[0].y, (float) side[0].z);
            }
            prev = cur;
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
