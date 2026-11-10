package fr.lordfinn.craftmaster.client.trial;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.trial.PropConfig;
import fr.lordfinn.craftmaster.trial.TrialPropEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a task prop: the item it shows (a block item as the full block) at its size, turned and tilted as set,
 * optionally spinning or floating, with its name above it.
 */
public class TrialPropRenderer extends EntityRenderer<TrialPropEntity, TrialPropRenderer.State> {

    public static class State extends EntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        float scale = 1, yaw, pitch, bob;
    }

    private final ItemModelResolver items;

    public TrialPropRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        items = ctx.getItemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TrialPropEntity e, State s, float partialTick) {
        super.extractRenderState(e, s, partialTick);
        PropConfig c = e.config();
        items.updateForNonLiving(s.item, e.look(), ItemDisplayContext.NONE, e);
        float time = e.tickCount + partialTick;
        s.scale = c.scale;
        s.yaw = c.yaw + (c.spin ? time * 2.5f : 0);
        s.pitch = c.pitch;
        s.bob = c.bob ? (float) Math.sin(time * 0.08) * 0.06f * Math.max(0.5f, c.scale) + 0.06f * c.scale : 0;
        if (c.showName && s.distanceToCameraSq < 32 * 32) {
            s.nameTag = Component.literal(e.displayName()).withStyle(ChatFormatting.GOLD);
            s.nameTagAttachment = new Vec3(0, c.scale + s.bob + 0.1, 0);
        } else {
            s.nameTag = null;
        }
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, s.scale / 2 + s.bob, 0);
        poseStack.rotate(Axis.YP.rotationDegrees(-s.yaw));
        poseStack.rotate(Axis.XP.rotationDegrees(s.pitch));
        poseStack.scale(s.scale, s.scale, s.scale);
        s.item.submit(poseStack, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
        poseStack.popPose();
        super.submit(s, poseStack, collector, camera);
    }
}
