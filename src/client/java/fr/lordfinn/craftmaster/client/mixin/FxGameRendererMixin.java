package fr.lordfinn.craftmaster.client.mixin;

import fr.lordfinn.craftmaster.client.fx.ScreenEffects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Screen effects: vanilla rebuilds the list of post effects every frame (end of frame effect, the player's, the
 * spectated mob's); ours are added after them. They are applied to the world view before the HUD is drawn, so the
 * HUD and the broadcast overlay stay sharp on top.
 */
@Mixin(GameRenderer.class)
public abstract class FxGameRendererMixin {

    //? if >=26.3 {
    @Shadow @Final private List<Identifier> requestedPostEffects;

    @Inject(method = "update", at = @At("TAIL"))
    private void craftmaster$screenEffects(DeltaTracker delta, CallbackInfo ci) {
        requestedPostEffects.addAll(ScreenEffects.frame());
    }
    //?} else {
    /*// Minecraft 26.2 has a single post effect (the spectated mob's): ours run right after the world is drawn, the
    // same way vanilla runs its own
    @Shadow @Final private net.minecraft.client.Minecraft minecraft;
    @Shadow @Final private com.mojang.blaze3d.pipeline.RenderTarget mainRenderTarget;
    @Shadow @Final private com.mojang.blaze3d.resource.CrossFrameResourcePool resourcePool;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
    private void craftmaster$screenEffects(DeltaTracker delta, boolean advanceGameTime, CallbackInfo ci) {
        for (Identifier id : ScreenEffects.frame()) {
            net.minecraft.client.renderer.PostChain chain = minecraft.getShaderManager().getPostChain(id, net.minecraft.client.renderer.LevelTargetBundle.MAIN_TARGETS);
            if (chain != null) chain.process(mainRenderTarget, resourcePool);
        }
    }
    *///?}
}
