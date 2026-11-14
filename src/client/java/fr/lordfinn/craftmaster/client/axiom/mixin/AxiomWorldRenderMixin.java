package fr.lordfinn.craftmaster.client.axiom.mixin;

import com.moulberry.axiom.hooks.WorldRenderHook;
import com.moulberry.axiom.render.AxiomWorldRenderContext;
import fr.lordfinn.craftmaster.client.axiom.TravellingGizmos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the travelling steps' gizmos where Axiom draws its marker / display entity gizmos. */
@Mixin(value = WorldRenderHook.class, remap = false)
public class AxiomWorldRenderMixin {
    @Inject(method = "renderHook", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/axiom/marker/MarkerEntityManipulator;render(Lcom/moulberry/axiom/render/AxiomWorldRenderContext;)V"))
    private static void craftmaster$travellings(AxiomWorldRenderContext rc, CallbackInfo ci) {
        TravellingGizmos.get().render(rc);
    }
}
