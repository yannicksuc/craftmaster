package fr.lordfinn.craftmaster.client.studio;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import fr.lordfinn.craftmaster.CraftMaster;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
//? if >=26.3 {
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
//?} else {
/*import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.rendertype.OutputTarget;
*///?}

/**
 * The spotlights' light (beams and pools): additive like lightning, tested against the world but NOT writing depth.
 * With lightning's own type a beam in front hid the beams behind it, and two pools on the same ceiling fought
 * (z-fighting); now overlapping lights add up, as light does.
 */
final class SpotLightRenderType {

    private SpotLightRenderType() {}

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(CraftMaster.id("pipeline/spot_light"))
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
            .build();

    /**
     * The pools: the same light, pulled toward the camera in the depth test like the block-breaking cracks (vanilla's
     * own bias, 1 × slope + 10 units). A fixed lift off the surface was not enough: depth precision falls with distance
     * and grazing angles, and the pool flickered with the floor depending on where it was seen from.
     */
    private static final RenderPipeline POOL_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(CraftMaster.id("pipeline/spot_pool"))
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false, 1.0f, 10.0f))
            .build();

    /**
     * The type to draw with now: ours, or vanilla's lightning while an Iris shader pack is on (Iris only knows
     * vanilla's pipelines: ours would not show at all under a shader pack; the lightning one shows, overlapping as before).
     */
    static RenderType light() {
        // Under a shader pack: vanilla's lightning (assigning ours to Iris's "basic" program draws it far too dark)
        return shaderPackOn() ? net.minecraft.client.renderer.rendertype.RenderTypes.lightning() : LIGHT;
    }

    /** The type for the pools (see {@link #POOL_PIPELINE}); vanilla's lightning under a shader pack, as {@link #light()}. */
    static RenderType pool() {
        return shaderPackOn() ? net.minecraft.client.renderer.rendertype.RenderTypes.lightning() : POOL;
    }

    private static Object irisApi;
    private static java.lang.reflect.Method inUse;
    private static boolean irisChecked;

    /** Is an Iris shader pack in use (through Iris's API, if Iris is installed)? */
    static boolean shaderPackOn() {
        if (!irisChecked) {
            irisChecked = true;
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) {
                try {
                    Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    irisApi = api.getMethod("getInstance").invoke(null);
                    inUse = api.getMethod("isShaderPackInUse");
                } catch (ReflectiveOperationException | LinkageError e) {
                    irisApi = null;
                }
            }
        }
        if (irisApi == null) return false;
        try {
            return (Boolean) inUse.invoke(irisApi);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    static final RenderType LIGHT = RenderType.create("craftmaster_spot_light",
            //? if >=26.3 {
            RenderSetup.builder(PIPELINE).sortOnUpload().createRenderSetup());
            //?} else {
            /*RenderSetup.builder(PIPELINE).setOutputTarget(OutputTarget.WEATHER_TARGET).sortOnUpload().createRenderSetup());
            *///?}

    static final RenderType POOL = RenderType.create("craftmaster_spot_pool",
            //? if >=26.3 {
            RenderSetup.builder(POOL_PIPELINE).sortOnUpload().createRenderSetup());
            //?} else {
            /*RenderSetup.builder(POOL_PIPELINE).setOutputTarget(OutputTarget.WEATHER_TARGET).sortOnUpload().createRenderSetup());
            *///?}
}
