package fr.lordfinn.craftmaster.client.mixin;

import fr.lordfinn.craftmaster.client.broadcast.Broadcast;
import fr.lordfinn.craftmaster.client.broadcast.FollowCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Broadcast mode: the mouse wheel goes from one camera to the next (instead of the hotbar or the fly speed); on a
 * follow shot, the wheel and the mouse move that shot's camera round the player instead of turning the player.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void craftmaster$cameraWheel(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (Minecraft.getInstance().gui.screen() == null && Broadcast.onScroll(yOffset)) ci.cancel();
    }

    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void craftmaster$followCameraMouse(LocalPlayer player, double dx, double dy) {
        if (!FollowCamera.turn(dx, dy)) player.turn(dx, dy);
    }
}
