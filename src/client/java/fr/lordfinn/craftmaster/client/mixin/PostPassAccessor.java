package fr.lordfinn.craftmaster.client.mixin;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Screen effects: the uniform buffers of a post pass (by block name), updated every frame (client.fx.FxUniforms). */
@Mixin(PostPass.class)
public interface PostPassAccessor {

    @Accessor("customUniforms")
    Map<String, GpuBuffer> craftmaster$customUniforms();
}
