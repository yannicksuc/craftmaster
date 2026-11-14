package fr.lordfinn.craftmaster.client.axiom.mixin;

import com.moulberry.axiom.UserAction;
import fr.lordfinn.craftmaster.client.axiom.TravellingGizmos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The travelling gizmos answer Axiom's clicks and keys first, like Axiom's own entity manipulators. */
@Mixin(value = UserAction.class, remap = false)
public class AxiomUserActionMixin {
    @Inject(method = "call", at = @At("HEAD"), cancellable = true)
    private void craftmaster$travellingsFirst(Object argument, CallbackInfoReturnable<UserAction.ActionResult> cir) {
        if (TravellingGizmos.get().callAction((UserAction) (Object) this, argument) == UserAction.ActionResult.USED_STOP)
            cir.setReturnValue(UserAction.ActionResult.USED_STOP);
    }
}
