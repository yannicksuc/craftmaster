package fr.lordfinn.craftmaster.client.studio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.studio.DeskBlock;
import fr.lordfinn.craftmaster.studio.DeskBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

/** The name engraved on a contestant's desk plate (the plate itself is in the block model). */
public class DeskRenderer implements BlockEntityRenderer<DeskBlockEntity, DeskRenderer.State> {

    /** Plate front (pixels, block facing north): x 3..13, y 6..10, at z = -1. */
    private static final float PLATE_W = 9f / 16, PLATE_CENTER_Y = 8f / 16, PLATE_FRONT = 0.5f + 1f / 16 + 0.002f;

    public static class State extends BlockEntityRenderState {
        float yaw;
        String name = "";
    }

    private final Font font;

    public DeskRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DeskBlockEntity be, State s, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, camera, crumbling);
        s.yaw = be.getBlockState().getValue(DeskBlock.FACING).toYRot();
        s.name = be.name();
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (s.name.isEmpty()) return;
        FormattedCharSequence text = Component.literal(s.name).getVisualOrderText();
        int w = Math.max(1, font.width(text));
        // As big as the plate allows: at most 3 px high, at most the plate's width
        float scale = Math.min(3.2f / 16 / font.lineHeight, PLATE_W / w);
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        poseStack.rotate(Axis.YP.rotationDegrees(-s.yaw));
        poseStack.translate(0, PLATE_CENTER_Y + (font.lineHeight - 1) * scale / 2, PLATE_FRONT);
        poseStack.scale(scale, -scale, scale);
        // Dark engraving on the brass plate, lit like the desk
        collector.submitText(poseStack, -w / 2f, 0, text, false, Font.DisplayMode.POLYGON_OFFSET, s.lightCoords, 0xFF3A2410, 0, 0);
        poseStack.popPose();
    }
}
