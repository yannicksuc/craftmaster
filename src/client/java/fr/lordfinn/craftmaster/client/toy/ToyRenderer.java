package fr.lordfinn.craftmaster.client.toy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.toy.ToyEntity;
import fr.lordfinn.craftmaster.toy.ToyKind;
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
 * Draws a toy with its item model (the 3D model of the item, in its colour), standing on its position and facing
 * where it goes; it tumbles in the air and rocks gently on water.
 */
public class ToyRenderer extends EntityRenderer<ToyEntity, ToyRenderer.State> {

    public static class State extends EntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        float yaw, tumble, rock, bob;
    }

    private final ItemModelResolver items;

    public ToyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        items = ctx.getItemModelResolver();
        shadowRadius = 0.15f;
        shadowStrength = 0.6f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ToyEntity e, State s, float partialTick) {
        super.extractRenderState(e, s, partialTick);
        items.updateForNonLiving(s.item, e.stack(), ItemDisplayContext.NONE, e);
        float time = e.tickCount + partialTick;
        s.yaw = e.getYRot(partialTick);
        boolean water = e.isInWater();
        boolean air = !e.onGround() && !water && e.getDeltaMovement().lengthSqr() > 0.01;
        // A tumble while flying (eggs and ducks less than balls)
        s.tumble = air ? time * (e.kind() == ToyKind.BALL || e.kind() == ToyKind.GOLF_BALL ? 40 : 18) : 0;
        s.rock = water ? (float) Math.sin(time * 0.12 + e.getId()) * 6 : 0;
        s.bob = water ? (float) Math.sin(time * 0.09 + e.getId() * 0.7) * 0.015f : 0;
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        // The item model is centred on the origin; its bottom (y = 0 px) goes on the toy's feet
        poseStack.translate(0, s.bob, 0);
        poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(180 - s.yaw));
        if (s.tumble != 0) {
            poseStack.translate(0, s.boundingBoxHeight / 2, 0);
            poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.XP.rotationDegrees(s.tumble));
            poseStack.translate(0, -s.boundingBoxHeight / 2, 0);
        }
        if (s.rock != 0) poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.ZP.rotationDegrees(s.rock));
        poseStack.translate(0, 0.5, 0);
        s.item.submit(poseStack, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
        poseStack.popPose();
        super.submit(s, poseStack, collector, camera);
    }
}
