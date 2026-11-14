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

    static final RenderType LIGHT = RenderType.create("craftmaster_spot_light",
            //? if >=26.3 {
            RenderSetup.builder(PIPELINE).sortOnUpload().createRenderSetup());
            //?} else {
            /*RenderSetup.builder(PIPELINE).setOutputTarget(OutputTarget.WEATHER_TARGET).sortOnUpload().createRenderSetup());
            *///?}
}
