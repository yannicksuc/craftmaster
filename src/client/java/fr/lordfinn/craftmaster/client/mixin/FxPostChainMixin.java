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

    //? if <26.3 {
    /*// Minecraft 26.2: the chain's id is only known while it is loaded
    @Inject(method = "load", at = @At("RETURN"))
    private static void craftmaster$fxId(net.minecraft.client.renderer.PostChainConfig config,
                                         net.minecraft.client.renderer.texture.TextureManager textures,
                                         java.util.Set<net.minecraft.resources.Identifier> externalTargets,
                                         net.minecraft.resources.Identifier id, net.minecraft.client.renderer.Projection projection,
                                         net.minecraft.client.renderer.ProjectionMatrixBuffer projectionBuffer,
                                         org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<PostChain> cir) {
        FxUniforms.loaded(cir.getReturnValue(), id);
    }
    *///?}
}
