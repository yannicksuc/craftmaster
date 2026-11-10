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

    @Shadow @Final private List<Identifier> requestedPostEffects;

    @Inject(method = "update", at = @At("TAIL"))
    private void craftmaster$screenEffects(DeltaTracker delta, CallbackInfo ci) {
        requestedPostEffects.addAll(ScreenEffects.frame());
    }
}
