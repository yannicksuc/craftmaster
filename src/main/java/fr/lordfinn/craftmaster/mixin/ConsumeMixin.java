package fr.lordfinn.craftmaster.mixin;

import fr.lordfinn.craftmaster.trial.kit.Finish;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player finishes eating / drinking an item: a trial's marked item (« la pomme ») ends their attempt. */
@Mixin(LivingEntity.class)
public abstract class ConsumeMixin {

    @Shadow
    protected ItemStack useItem;

    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void craftmaster$consumed(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer p && useItem != null && !useItem.isEmpty()) Finish.onConsumed(p, useItem.copy());
    }
}
