package fr.lordfinn.craftmaster.client.trial;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.trial.block.PlinthBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** The object on show above a display plinth: floating, turning slowly, its custom name above it. */
public class PlinthRenderer implements BlockEntityRenderer<PlinthBlockEntity, PlinthRenderer.State> {

    public static class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        boolean empty = true;
        float angle, bob;
        Component name;
    }

    private final ItemModelResolver items;

    public PlinthRenderer(BlockEntityRendererProvider.Context ctx) {
        items = ctx.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PlinthBlockEntity be, State s, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, s, crumbling);
        ItemStack stack = be.item();
        s.empty = stack.isEmpty();
        if (s.empty) {
            s.item.clear();
            return;
        }
        items.updateForTopItem(s.item, stack, ItemDisplayContext.FIXED, be.getLevel(), null, (int) be.getBlockPos().asLong());
        float time = (be.getLevel() == null ? 0 : be.getLevel().getGameTime() % 72000) + partialTick;
        s.angle = time * 1.2f;
        s.bob = (float) Math.sin(time * 0.06) * 0.04f;
        s.name = stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName() : null;
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (s.empty) return;
        poseStack.pushPose();
        poseStack.translate(0.5, 14 / 16.0 + 0.42 + s.bob, 0.5);
        poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(s.angle));
        poseStack.scale(0.75f, 0.75f, 0.75f);
        s.item.submit(poseStack, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
        if (s.name != null) {
            poseStack.pushPose();
            poseStack.translate(0.5, 0, 0.5);
            collector.submitNameTag(poseStack, new Vec3(0, 1.75, 0), 0, s.name, true, s.lightCoords, camera);
            poseStack.popPose();
        }
    }
}
