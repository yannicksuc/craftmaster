package fr.lordfinn.craftmaster.client.mixin;

import fr.lordfinn.craftmaster.client.broadcast.Broadcast;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Broadcast mode: no toasts (tutorial hints, advancements, recipes) on the TV picture. */
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void craftmaster$noToasts(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (Broadcast.active()) ci.cancel();
    }
}
