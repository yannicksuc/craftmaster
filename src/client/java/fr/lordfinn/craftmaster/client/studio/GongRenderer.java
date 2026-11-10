package fr.lordfinn.craftmaster.client.studio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.studio.GongBlock;
import fr.lordfinn.craftmaster.studio.GongBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * The gong's disc (the wooden frame is the block model): a golden disc hung by two cords under the beam, swinging
 * after a strike ({@link GongBlockEntity#swing}).
 */
public class GongRenderer implements BlockEntityRenderer<GongBlockEntity, GongRenderer.State> {

    public static final Identifier TEXTURE = CraftMaster.id("textures/entity/gong.png");
    private static final Geo.Tex TEX = new Geo.Tex(32, 16);

    public static class State extends BlockEntityRenderState {
        float yaw, swing;
    }

    public GongRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GongBlockEntity be, State s, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, camera, crumbling);
        s.yaw = be.getBlockState().getValue(GongBlock.FACING).toYRot();
        long time = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        s.swing = GongBlockEntity.swing(time - be.struckAt() + partialTick);
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = s.lightCoords;
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        poseStack.rotate(Axis.YP.rotationDegrees(-s.yaw));
        poseStack.scale(1 / 16f, 1 / 16f, 1 / 16f);
        // Hangs from the beam's underside (y = 13 px), swings around the x axis
        poseStack.translate(0, 13, 0);
        poseStack.rotate(Axis.XP.rotationDegrees(s.swing));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (p, c) -> {
            int white = 0xFFFFFFFF;
            // Disc 12 × 12 px, 1 px thick: front (audience, +z) and back, cut out round by the texture
            Geo.front(c, p, TEX, -6, -13, 6, -1, 0.5f, 0, 0, 12, 12, white, light);
            Geo.quad(c, p, TEX, 6, -1, -0.5f, 6, -13, -0.5f, -6, -13, -0.5f, -6, -1, -0.5f, 12, 0, 24, 12, white, light, 0, 0, -1);
            // Two cords from the beam to the rim
            for (float x : new float[]{-3.5f, 3.5f}) {
                Geo.quad(c, p, TEX, x - 0.25f, 0, 0, x - 0.25f, -2.2f, 0, x + 0.25f, -2.2f, 0, x + 0.25f, 0, 0, 24, 0, 25, 4, white, light, 0, 0, 1);
                Geo.quad(c, p, TEX, x + 0.25f, 0, 0, x + 0.25f, -2.2f, 0, x - 0.25f, -2.2f, 0, x - 0.25f, 0, 0, 24, 0, 25, 4, white, light, 0, 0, -1);
            }
        });
        poseStack.popPose();
    }
}
