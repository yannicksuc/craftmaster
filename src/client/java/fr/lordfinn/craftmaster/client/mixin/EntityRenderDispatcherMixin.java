package fr.lordfinn.craftmaster.client.mixin;

import fr.lordfinn.craftmaster.camera.CameraEntity;
import fr.lordfinn.craftmaster.client.broadcast.Broadcast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Broadcast mode: what must never be in the picture — the studio cameras (the broadcast sees through them), the
 * broadcast player itself, and the spectators (other broadcast games and viewers floating around the set).
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    //? if >=26.3 {
    private <E extends Entity> void craftmaster$hideOffAir(E entity, Frustum frustum, double x, double y, double z, float partialTick,
    //?} else {
    /*private <E extends Entity> void craftmaster$hideOffAir(E entity, Frustum frustum, double x, double y, double z,
    *///?}
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (!Broadcast.active()) return;
        if (entity instanceof CameraEntity || entity == Minecraft.getInstance().player
                || (entity instanceof Player p && p.isSpectator())) cir.setReturnValue(false);
    }
}
