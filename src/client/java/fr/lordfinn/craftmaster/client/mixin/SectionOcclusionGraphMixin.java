package fr.lordfinn.craftmaster.client.mixin;

import fr.lordfinn.craftmaster.client.broadcast.Mosaic;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Split screen: the camera jumps between the tiles every frame, which would invalidate the chunk visibility graph at
 * every frame (it is rebuilt when the camera changes 8-block cell), so it never finishes and far chunks go missing.
 * Once the graph has been built without smart culling, it is kept for the whole split screen; each tile still gets
 * its own frustum culling.
 */
@Mixin(SectionOcclusionGraph.class)
public abstract class SectionOcclusionGraphMixin {

    @Inject(method = "invalidateIfNeeded", at = @At("HEAD"), cancellable = true)
    private void craftmaster$keepGraphForMosaic(CameraRenderState camera, int fov, CallbackInfo ci) {
        if (Mosaic.graphFrozen()) ci.cancel();
    }
}
