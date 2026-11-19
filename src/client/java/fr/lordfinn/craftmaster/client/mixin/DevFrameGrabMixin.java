package fr.lordfinn.craftmaster.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Development: the « rec » captures of DevInput are taken here, the frame fully drawn (world, effects, interface). */
@Mixin(GameRenderer.class)
public class DevFrameGrabMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void craftmaster$grabFrame(CallbackInfo ci) {
        fr.lordfinn.craftmaster.client.DevInput.afterFrame();
    }
}
