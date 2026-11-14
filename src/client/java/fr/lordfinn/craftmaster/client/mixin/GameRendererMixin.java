package fr.lordfinn.craftmaster.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lordfinn.craftmaster.client.broadcast.Broadcast;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Broadcast mode: a studio camera does not bob with the player's steps nor shake when they are hurt. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void craftmaster$noBob(CameraRenderState camera, PoseStack poseStack, CallbackInfo ci) {
        if (Broadcast.active() || fr.lordfinn.craftmaster.client.broadcast.DirectorCamera.previewing() || fr.lordfinn.craftmaster.client.studio.ControlView.active()) ci.cancel();
    }

    /** Split screen: the world just drawn for one tile is kept for that tile (before the interface is drawn). */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
    private void craftmaster$mosaicTile(CallbackInfo ci) {
        if (Broadcast.active()) fr.lordfinn.craftmaster.client.broadcast.Mosaic.afterLevel();
        // In-game clip: the world picture, without the interface
        fr.lordfinn.craftmaster.client.media.Clips.afterLevel();
    }

    /** Videos of the studio screens: WaterMedia's OpenGL work and the newest pictures, before anything is drawn. */
    @Inject(method = "render", at = @At("HEAD"))
    private void craftmaster$videoFrame(CallbackInfo ci) {
        fr.lordfinn.craftmaster.client.media.Media.frameStart();
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void craftmaster$noHurtShake(CameraRenderState camera, PoseStack poseStack, CallbackInfo ci) {
        if (Broadcast.active() || fr.lordfinn.craftmaster.client.broadcast.DirectorCamera.previewing() || fr.lordfinn.craftmaster.client.studio.ControlView.active()) ci.cancel();
    }
}
