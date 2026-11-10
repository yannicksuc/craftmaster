package fr.lordfinn.craftmaster.client.mixin;

import fr.lordfinn.craftmaster.client.fx.ScreenEffects;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Screen effects: a fisheye sees wider (the barrel distortion then squeezes it into the frame), binoculars zoom in.
 * Meant to apply after the broadcast camera's own field of view (higher priority than CameraMixin).
 */
@Mixin(value = Camera.class, priority = 1500)
public abstract class FxCameraMixin {

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void craftmaster$fxFov(float partialTick, CallbackInfoReturnable<Float> cir) {
        float fov = cir.getReturnValue();
        float changed = ScreenEffects.fov(fov);
        if (changed != fov) cir.setReturnValue(changed);
    }
}
