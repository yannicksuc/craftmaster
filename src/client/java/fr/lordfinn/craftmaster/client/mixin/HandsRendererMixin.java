package fr.lordfinn.craftmaster.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lordfinn.craftmaster.client.broadcast.Broadcast;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Broadcast mode: no hand or held item in the picture. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class HandsRendererMixin {

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void craftmaster$noHands(float partialTick, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState player,
                                     FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
        if (Broadcast.active()) ci.cancel();
    }
}
