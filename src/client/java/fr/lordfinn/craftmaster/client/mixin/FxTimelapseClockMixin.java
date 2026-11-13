package fr.lordfinn.craftmaster.client.mixin;

import fr.lordfinn.craftmaster.client.fx.ScreenEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Screen effect "timelapse": on this game only, the world's clocks read ahead by {@link ScreenEffects#timelapseTicks}
 * (a day in 20 s while the effect is on, back on time as soon as it is off). The sky, the sun, the moon, the stars
 * and the light of the day all follow the clocks (timelines), so they move fast; the server's time is not touched.
 */
//? if >=26.3 {
@Mixin(targets = "net.minecraft.client.ClientClockManager$ClientClockInstance")
//?} else {
/*@Mixin(net.minecraft.client.ClientClockManager.class)
*///?}
public abstract class FxTimelapseClockMixin {

    //? if >=26.3 {
    @Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true)
    private void craftmaster$timelapse(CallbackInfoReturnable<Long> cir) {
    //?} else {
    /*@Inject(method = "getTotalTicks", at = @At("RETURN"), cancellable = true)
    private void craftmaster$timelapse(net.minecraft.core.Holder<net.minecraft.world.clock.WorldClock> clock, CallbackInfoReturnable<Long> cir) {
    *///?}
        long ahead = ScreenEffects.timelapseTicks();
        if (ahead != 0) cir.setReturnValue(cir.getReturnValue() + ahead);
    }
}
