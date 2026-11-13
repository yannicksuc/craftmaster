package fr.lordfinn.craftmaster.client.garage;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lordfinn.craftmaster.garage.GarageMotion;
import fr.lordfinn.craftmaster.garage.GarageMotorBlockEntity;
import fr.lordfinn.craftmaster.garage.GaragePanel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a moving garage door: each block of the builder's panel where the door's movement has brought it this
 * frame ({@link GarageMotion}), squeezed into the lintel as it goes in there. Drawn like a block pushed by a piston
 * (its own model, the light and colours of where it stands). Also draws the block a camouflaged motor looks like.
 */
public class GarageMotorRenderer implements BlockEntityRenderer<GarageMotorBlockEntity, GarageMotorRenderer.State> {

    /** The moving blocks are drawn this much inside their cell, so they never flicker against the walls around. */
    private static final float INSET = 0.002f;

    public record Piece(float x, float y, float z, float height, MovingBlockRenderState block) {}

    public static class State extends BlockEntityRenderState {
        final List<Piece> pieces = new ArrayList<>();
        MovingBlockRenderState camouflage;
    }

    public GarageMotorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GarageMotorBlockEntity be, State s, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, camera, crumbling);
        s.pieces.clear();
        s.camouflage = null;
        if (!(be.getLevel() instanceof ClientLevel level)) return;
        BlockPos motor = be.getBlockPos();
        if (be.camouflage() != null) s.camouflage = block(level, motor, be.camouflage());
        if (!be.isMoving()) return;
        GaragePanel panel = be.panel();
        double opening = be.opening(level.getGameTime() + partialTick);
        for (GaragePanel.Cell c : panel.cells()) {
            double[] slice = GarageMotion.slice(panel.row(c), panel.height(), opening);
            if (slice == null) continue;
            s.pieces.add(new Piece(c.dx(), (float) (panel.bottomDy() + slice[0]), c.dz(), (float) slice[1],
                    block(level, c.pos(motor), c.state())));
        }
    }

    /**
     * A moving block that sees the real blocks beside it (the pillars, the ground in front), so its corners are
     * shaded like the door's real blocks; its own column is empty (the doorway, the ground right under it), so its
     * bottom face is drawn once it has risen.
     */
    static final class DoorBlock extends MovingBlockRenderState {
        ClientLevel level;

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (pos.equals(blockPos)) return blockState;
            if (level == null || (pos.getX() == blockPos.getX() && pos.getZ() == blockPos.getZ())) return Blocks.AIR.defaultBlockState();
            BlockState s = level.getBlockState(pos);
            return s.is(Blocks.BARRIER) ? Blocks.AIR.defaultBlockState() : s;
        }
    }

    private static MovingBlockRenderState block(ClientLevel level, BlockPos pos, BlockState state) {
        DoorBlock m = new DoorBlock();
        m.level = level;
        m.randomSeedPos = pos;
        m.blockPos = pos;
        m.blockState = state;
        m.biome = level.getBiome(pos);
        m.cardinalLighting = level.cardinalLighting();
        m.lightEngine = level.getLightEngine();
        return m;
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (s.camouflage != null) collector.submitMovingBlock(poseStack, s.camouflage, 0);
        for (Piece p : s.pieces) {
            float h = p.height() - 2 * INSET;
            if (h <= 0) continue;
            poseStack.pushPose();
            poseStack.translate(p.x() + INSET, p.y() + INSET, p.z() + INSET);
            poseStack.scale(1 - 2 * INSET, h, 1 - 2 * INSET);
            collector.submitMovingBlock(poseStack, p.block(), 0);
            poseStack.popPose();
        }
    }

    /** The door reaches past the motor's own block. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
