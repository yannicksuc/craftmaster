package fr.lordfinn.craftmaster.client.curtain;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lordfinn.craftmaster.block.CurtainBlock;
import fr.lordfinn.craftmaster.client.portrait.PortraitClock;
import fr.lordfinn.craftmaster.curtain.CurtainBlockEntity;
import fr.lordfinn.craftmaster.curtain.CurtainCloth;
import fr.lordfinn.craftmaster.curtain.Curtains;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws one curtain block's fabric: its 4 pleats ({@link CurtainCloth}), wherever the slide has taken them. While
 * the curtain opens they slide to the side of their half and gather there into deep folds; the lower rows trail a
 * little behind the top and billow, and the fabric swings once when it stops. A closed, still curtain looks
 * exactly like the block model (same pleats, same texture, hem on the lowest block).
 */
public class CurtainRenderer implements BlockEntityRenderer<CurtainBlockEntity, CurtainRenderer.State> {

    public static class State extends BlockEntityRenderState {
        Identifier texture;
        boolean alongX;
        /**
         * Per pleat, at the top then at the bottom of the block: x0, x1 (along the curtain), z0, z1 (across it), then
         * the billow's shift across at x0 and at x1, in blocks, relative to this block. Top and bottom differ while
         * the lower fabric trails behind; the shift is taken at the pleat's edges so neighbours stay joined.
         */
        final float[] pleats = new float[CurtainCloth.PLEATS_PER_BLOCK * 12];
        final int[] slices = new int[CurtainCloth.PLEATS_PER_BLOCK];
        boolean top, bottom;
    }

    private static final Map<Block, Identifier[]> TEXTURES = new HashMap<>();
    /** Overlap between neighbouring pieces of fabric (blocks). */
    private static final float SEAM = 0.002f, Y0 = -SEAM, Y1 = 1 + SEAM;

    public CurtainRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    /** {fabric with hem, plain fabric} of a curtain block: the same textures as its block models. */
    private static Identifier[] textures(Block block) {
        return TEXTURES.computeIfAbsent(block, b -> {
            Identifier id = BuiltInRegistries.BLOCK.getKey(b);
            return new Identifier[]{
                    id.withPath("textures/block/" + id.getPath() + ".png"),
                    id.withPath("textures/block/" + id.getPath() + "_plain.png")};
        });
    }

    @Override
    public void extractRenderState(CurtainBlockEntity be, State s, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, camera, crumbling);
        if (!(be.getLevel() instanceof ClientLevel level)) return;
        BlockPos pos = be.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CurtainBlock)) {
            s.texture = null;
            return;
        }
        s.texture = textures(state.getBlock())[state.getValue(CurtainBlock.BOTTOM) ? 0 : 1];

        CurtainClient.Layout layout = CurtainClient.layout(level, pos, state);
        double now = PortraitClock.ticks(level.getGameTime());
        CurtainClient.frame(level, now);
        // Billowing while the fabric moves: stronger lower down, a slow wave along the curtain
        double edgeSpeed = CurtainClient.speed(pos, now) * layout.columns() * 8;
        double billow = Math.min(1.5, edgeSpeed * 0.35);

        s.alongX = layout.side().getAxis() == Direction.Axis.X;
        s.top = !(level.getBlockState(pos.above()).getBlock() instanceof CurtainBlock);
        s.bottom = state.getValue(CurtainBlock.BOTTOM);
        int column = Curtains.column(pos, layout.side()) - layout.minColumn();
        for (int edge = 0; edge < 2; edge++) {
            // The trailing and the billowing grow smoothly down the curtain: computed at the block's top and bottom
            double depth = layout.depth(pos.getY() + 1 - edge);
            double open = CurtainClient.openness(pos, state, now, depth);
            double amp = billow * (0.25 + 0.75 * depth);
            for (int k = 0; k < CurtainCloth.PLEATS_PER_BLOCK; k++) {
                int j = column * CurtainCloth.PLEATS_PER_BLOCK + k;
                CurtainCloth.Pleat p = CurtainCloth.pleat(j, layout.columns(), open);
                int o = k * 12 + edge * 6;
                s.pleats[o] = (float) (p.x0() / 16 - column);
                s.pleats[o + 1] = (float) (p.x1() / 16 - column);
                s.pleats[o + 2] = (float) (p.z0() / 16);
                s.pleats[o + 3] = (float) (p.z1() / 16);
                s.pleats[o + 4] = (float) (amp * Math.sin(p.x0() * 0.18 + now * 0.45) / 16);
                s.pleats[o + 5] = (float) (amp * Math.sin(p.x1() * 0.18 + now * 0.45) / 16);
                s.slices[k] = k;
            }
        }
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (s.texture == null) return;
        int light = s.lightCoords;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(s.texture), (p, c) -> {
            for (int k = 0; k < CurtainCloth.PLEATS_PER_BLOCK; k++) pleat(c, p, s, k * 12, s.slices[k], light);
        });
    }

    /**
     * A pleat: a box from the top to the bottom of the block (its top and bottom ends may be shifted apart),
     * sampling its 4 px slice of the fabric texture.
     */
    private static void pleat(VertexConsumer c, PoseStack.Pose p, State s, int o, int slice, int light) {
        float[] q = s.pleats;
        // Corners: a = top, b = bottom; 0 / 1 = the pleat's two edges; n = across (back / front face), shifted
        // (each pleat a hair thicker than its place, so it overlaps its neighbours where their corners meet)
        float a0 = q[o], a1 = q[o + 1], ta0 = q[o + 2] + q[o + 4] - SEAM, tf0 = q[o + 3] + q[o + 4] + SEAM,
                ta1 = q[o + 2] + q[o + 5] - SEAM, tf1 = q[o + 3] + q[o + 5] + SEAM;
        float b0 = q[o + 6], b1 = q[o + 7], ba0 = q[o + 8] + q[o + 10] - SEAM, bf0 = q[o + 9] + q[o + 10] + SEAM,
                ba1 = q[o + 8] + q[o + 11] - SEAM, bf1 = q[o + 9] + q[o + 11] + SEAM;
        if (a1 - a0 < 1e-4f && b1 - b0 < 1e-4f) return;
        float u0 = slice * 4 / 16f, u1 = u0 + 4 / 16f, us = u0 + 2 / 16f;
        boolean x = s.alongX;
        // A hair beyond the block and wider (fronts): no hairline cracks with the neighbouring blocks' fabric
        a0 -= SEAM;
        a1 += SEAM;
        b0 -= SEAM;
        b1 += SEAM;
        // Front and back (facing across the curtain)
        quad(c, p, x, a0, Y1, tf0, a1, Y1, tf1, b1, Y0, bf1, b0, Y0, bf0, u0, 0, u1, 1, 0, 0, 1, light);
        quad(c, p, x, a1, Y1, ta1, a0, Y1, ta0, b0, Y0, ba0, b1, Y0, ba1, u0, 0, u1, 1, 0, 0, -1, light);
        // Sides of the fold (facing along the curtain)
        quad(c, p, x, a0, Y1, ta0, a0, Y1, tf0, b0, Y0, bf0, b0, Y0, ba0, u0, 0, us, 1, -1, 0, 0, light);
        quad(c, p, x, a1, Y1, tf1, a1, Y1, ta1, b1, Y0, ba1, b1, Y0, bf1, u0, 0, us, 1, 1, 0, 0, light);
        // Top and bottom edges, only where the curtain ends
        if (s.top) quad(c, p, x, a0, Y1, ta0, a1, Y1, ta1, a1, Y1, tf1, a0, Y1, tf0, u0, 0, u1, 2 / 16f, 0, 1, 0, light);
        if (s.bottom) quad(c, p, x, b0, Y0, bf0, b1, Y0, bf1, b1, Y0, ba1, b0, Y0, ba0, u0, 14 / 16f, u1, 1, 0, -1, 0, light);
    }

    /**
     * A quad given in curtain axes (a along the curtain, y up, n across), turned into the block's axes. The normal
     * is given as (along, up, across) too.
     */
    private static void quad(VertexConsumer c, PoseStack.Pose p, boolean alongX,
                             float a1, float y1, float n1, float a2, float y2, float n2,
                             float a3, float y3, float n3, float a4, float y4, float n4,
                             float u0, float v0, float u1, float v1, float na, float ny, float nn, int light) {
        vertex(c, p, alongX, a1, y1, n1, u0, v0, na, ny, nn, light);
        vertex(c, p, alongX, a2, y2, n2, u1, v0, na, ny, nn, light);
        vertex(c, p, alongX, a3, y3, n3, u1, v1, na, ny, nn, light);
        vertex(c, p, alongX, a4, y4, n4, u0, v1, na, ny, nn, light);
    }

    private static void vertex(VertexConsumer c, PoseStack.Pose p, boolean alongX, float a, float y, float n,
                               float u, float v, float na, float ny, float nn, int light) {
        float x = alongX ? a : n, z = alongX ? n : a;
        float nx = alongX ? na : nn, nz = alongX ? nn : na;
        c.addVertex(p, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }

    /** Drawn even when its block is off screen: its fabric may have slid into view. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    /** A stage curtain is seen from the back of the hall (and by the broadcast camera). */
    @Override
    public int getViewDistance() {
        return 160;
    }
}
