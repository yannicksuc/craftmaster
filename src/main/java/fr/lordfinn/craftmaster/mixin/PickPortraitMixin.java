package fr.lordfinn.craftmaster.mixin;

import fr.lordfinn.craftmaster.portrait.PortraitEntity;
import fr.lordfinn.craftmaster.portrait.PortraitItem;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ctrl + middle click on a portrait (the game's "pick with data"): a portrait item carrying its settings (season,
 * rank or player, name, skin, pose, string, window), placed again as a copy. The game itself keeps no entity data.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PickPortraitMixin {

    @Shadow
    public ServerPlayer player;

    @Shadow
    private void tryPickItem(ItemStack stack) {}

    @Inject(method = "handlePickItemFromEntity", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"))
    private void craftmaster$pickPortraitWithData(ServerboundPickItemFromEntityPacket packet, CallbackInfo ci) {
        if (!packet.includeData()) return;
        if (!(player.level().getEntityOrPart(packet.id()) instanceof PortraitEntity portrait)) return;
        if (!player.isWithinEntityInteractionRange(portrait, 3.0)) return;
        tryPickItem(PortraitItem.withSettings(portrait));
        ci.cancel();
    }
}
