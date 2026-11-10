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
        if (Broadcast.active() || fr.lordfinn.craftmaster.client.broadcast.DirectorCamera.previewing()) ci.cancel();
    }

    /** Split screen: the world just drawn for one tile is kept for that tile (before the interface is drawn). */
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void craftmaster$mosaicTile(CallbackInfo ci) {
        if (Broadcast.active()) fr.lordfinn.craftmaster.client.broadcast.Mosaic.afterLevel();
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void craftmaster$noHurtShake(CameraRenderState camera, PoseStack poseStack, CallbackInfo ci) {
        if (Broadcast.active() || fr.lordfinn.craftmaster.client.broadcast.DirectorCamera.previewing()) ci.cancel();
    }
}
