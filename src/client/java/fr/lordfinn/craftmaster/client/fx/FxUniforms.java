package fr.lordfinn.craftmaster.client.fx;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import /*$ gpu_buffer_class {*/com.mojang.renderpearl.api.buffers.GpuBuffer/*$}*/;
import /*$ gpu_device_class {*/com.mojang.renderpearl.api.device.GpuDevice/*$}*/;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.client.mixin.PostChainAccessor;
import fr.lordfinn.craftmaster.client.mixin.PostPassAccessor;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Live uniforms of our post effects. Every pass of a {@code craftmaster:fx/*} chain declares the uniform block
 * {@code FxConfig { vec4 Fx; }} (see shaders/include/fx_common.glsl): before the chain runs, it gets
 * {@code Fx = (intensity, time in seconds, seconds since switched on, effect-specific)} ({@link ScreenEffects#uniform}). Vanilla creates the post pass uniform buffers once from the JSON
 * values, without the copy usage needed to update them: the first time, the buffer is replaced by an updatable one
 * of the same size.
 */
public final class FxUniforms {

    private FxUniforms() {}

    public static final String BLOCK = "FxConfig";
    private static final String PREFIX = "fx/";

    //? if <26.3 {
    /*// Minecraft 26.2 chains do not know their id: kept when they are loaded (FxPostChainMixin)
    private static final java.util.Map<PostChain, Identifier> IDS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static void loaded(PostChain chain, Identifier id) {
        if (chain != null) IDS.put(chain, id);
    }
    *///?}

    /** Called at the start of {@link PostChain#process} (mixin). */
    public static void beforeProcess(PostChain chain) {
        //? if >=26.3 {
        Identifier id = chain.id();
        //?} else {
        /*Identifier id = IDS.get(chain);
        if (id == null) return;
        *///?}
        if (!CraftMaster.MOD_ID.equals(id.getNamespace()) || !id.getPath().startsWith(PREFIX)) return;
        float[] fx = ScreenEffects.uniform(id.getPath().substring(PREFIX.length()));
        GpuDevice device = RenderSystem.getDevice();
        for (PostPass pass : ((PostChainAccessor) chain).craftmaster$passes()) {
            Map<String, GpuBuffer> uniforms = ((PostPassAccessor) pass).craftmaster$customUniforms();
            GpuBuffer buffer = uniforms.get(BLOCK);
            if (buffer == null) continue;
            if ((buffer.usage() & GpuBuffer.USAGE_COPY_DST) == 0) {
                GpuBuffer updatable = device.createBuffer(() -> "CraftMaster screen effect " + id, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, buffer.size());
                uniforms.put(BLOCK, updatable);
                buffer.close();
                buffer = updatable;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = Std140Builder.onStack(stack, (int) buffer.size()).putVec4(fx[0], fx[1], fx[2], fx[3]).get();
                device.createCommandEncoder().writeToBuffer(buffer.slice(), data);
            }
        }
    }
}
