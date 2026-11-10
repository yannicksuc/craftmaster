package fr.lordfinn.craftmaster.client.mixin;

import fr.lordfinn.craftmaster.client.broadcast.Broadcast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Broadcast mode: the mouse wheel goes from one camera to the next (instead of the hotbar or the fly speed). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void craftmaster$cameraWheel(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (Minecraft.getInstance().gui.screen() == null && Broadcast.onScroll(yOffset)) ci.cancel();
    }
}
