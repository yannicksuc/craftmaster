package fr.lordfinn.craftmaster.client.mixin;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Screen effects: the passes of a post chain, to update their live uniforms (client.fx.FxUniforms). */
@Mixin(PostChain.class)
public interface PostChainAccessor {

    @Accessor("passes")
    List<PostPass> craftmaster$passes();
}
