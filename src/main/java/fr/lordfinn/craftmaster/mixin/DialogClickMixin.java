package fr.lordfinn.craftmaster.mixin;

import fr.lordfinn.craftmaster.trial.kit.Dialogs;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Answers of the vanilla dialogs opened by the trial kit ({@link Dialogs}): the game only hands the server the
 * button's id and the typed values, not who sent them; here the player is still known. Runs on the server thread
 * (after the game's own thread check).
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class DialogClickMixin {

    @Inject(method = "handleCustomClickAction", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;handleCustomClickAction(Lnet/minecraft/resources/Identifier;Ljava/util/Optional;)V"))
    private void craftmaster$dialogAnswer(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        if ((Object) this instanceof ServerGamePacketListenerImpl game && Dialogs.handle(game.player, packet.id(), packet.payload())) ci.cancel();
    }
}
