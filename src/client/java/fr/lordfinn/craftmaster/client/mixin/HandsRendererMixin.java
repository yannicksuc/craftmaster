package fr.lordfinn.craftmaster.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lordfinn.craftmaster.client.broadcast.Broadcast;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Broadcast mode: no hand or held item in the picture. */
//? if >=26.3 {
@Mixin(net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer.class)
//?} else {
/*@Mixin(net.minecraft.client.renderer.ItemInHandRenderer.class)
*///?}
public abstract class HandsRendererMixin {

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    //? if >=26.3 {
    private void craftmaster$noHands(float partialTick, PoseStack poseStack, SubmitNodeCollector collector,
                                     net.minecraft.client.renderer.state.level.PlayerRenderState player,
                                     net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
    //?} else {
    /*private void craftmaster$noHands(float partialTick, PoseStack poseStack, SubmitNodeCollector collector,
                                     net.minecraft.client.player.LocalPlayer player, int light, CallbackInfo ci) {
    *///?}
        if (Broadcast.active() || fr.lordfinn.craftmaster.client.broadcast.DirectorCamera.previewing()) ci.cancel();
    }
}
