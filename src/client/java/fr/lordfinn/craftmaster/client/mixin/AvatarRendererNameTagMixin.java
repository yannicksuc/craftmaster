package fr.lordfinn.craftmaster.client.mixin;

import fr.lordfinn.craftmaster.client.broadcast.Broadcast;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Broadcast mode: no name floating above the players' heads. The TV picture names them with the lower third, and
 * a name tag in the middle of the shot clutters it (it also overlapped the round titles). Players are drawn by
 * {@link AvatarRenderer}, which overrides {@code shouldShowName}: the injection has to be there.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererNameTagMixin {

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
    private void craftmaster$noPlayerNamesOnAir(Avatar entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (Broadcast.active()) cir.setReturnValue(false);
    }
}
