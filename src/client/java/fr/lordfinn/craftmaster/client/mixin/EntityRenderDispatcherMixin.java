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
 * Broadcast mode: what must never be in the picture — the studio cameras (the broadcast sees through them) and the
 * spectators (the broadcast games, kept in spectator mode under the lens, and viewers floating around the set). A
 * player in broadcast mode who is not a spectator is in the picture like anyone (he sees himself).
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
        boolean feed = fr.lordfinn.craftmaster.client.broadcast.ShowFeedClient.active()
                || fr.lordfinn.craftmaster.client.broadcast.ThumbCapture.capturing();
        if (!Broadcast.active() && !feed) return;
        // A player sees himself in the picture (F8 or F7); the broadcast game (OBS) is a spectator, never drawn
        if (entity instanceof CameraEntity || (entity instanceof Player p && p.isSpectator())) cir.setReturnValue(false);
    }
}
