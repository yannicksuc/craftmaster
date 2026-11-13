package fr.lordfinn.craftmaster.client.toy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.toy.BalloonEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Draws a tied balloon: the balloon's 3D item model (8 px wide) scaled to its size, swaying slowly on its knot.
 */
public class BalloonRenderer extends EntityRenderer<BalloonEntity, BalloonRenderer.State> {

    public static class State extends EntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        float size, yaw, swayX, swayZ, bob;
    }

    private final ItemModelResolver items;

    public BalloonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        items = ctx.getItemModelResolver();
        shadowRadius = 0.25f;
        shadowStrength = 0.35f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(BalloonEntity e, State s, float partialTick) {
        super.extractRenderState(e, s, partialTick);
        items.updateForNonLiving(s.item, e.stack(), ItemDisplayContext.NONE, e);
        float t = e.tickCount + partialTick + e.getId() * 13;
        s.size = e.size();
        s.yaw = e.getYRot();
        s.swayX = (float) Math.sin(t * 0.045) * 4;
        s.swayZ = (float) Math.cos(t * 0.037) * 4;
        s.bob = (float) Math.sin(t * 0.06) * 0.03f * s.size;
        s.shadowRadius = s.size * 0.35f;
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, s.bob, 0);
        poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(180 - s.yaw));
        poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.XP.rotationDegrees(s.swayX));
        poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.ZP.rotationDegrees(s.swayZ));
        // Model: body 8 px wide (0.5 block) → scale to the balloon's size
        float k = s.size / 0.5f;
        poseStack.scale(k, k, k);
        poseStack.translate(0, 0.5, 0);
        s.item.submit(poseStack, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
        poseStack.popPose();
        super.submit(s, poseStack, collector, camera);
    }
}
