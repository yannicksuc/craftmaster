package fr.lordfinn.craftmaster.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import fr.lordfinn.craftmaster.client.fx.FxUniforms;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Screen effects: our post chains get their intensity and time just before they run. */
@Mixin(PostChain.class)
public abstract class FxPostChainMixin {

    @Inject(method = "process", at = @At("HEAD"))
    private void craftmaster$fxUniforms(RenderTarget target, GraphicsResourceAllocator allocator, CallbackInfo ci) {
        FxUniforms.beforeProcess((PostChain) (Object) this);
    }
}
