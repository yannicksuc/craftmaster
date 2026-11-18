package fr.lordfinn.craftmaster.mixin;

import fr.lordfinn.craftmaster.trial.kit.GroundEnd;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A contestant whose attempt ended on the ground ({@link GroundEnd}) cannot change their inventory any more: clicks
 * in it are refused (and the screen put back as it was), and so are dropping an item and swapping hands.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class InventoryLockMixin {

    @Shadow
    public ServerPlayer player;

    private static final String ENSURE = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V";

    @Inject(method = "handleContainerClick", cancellable = true, at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER))
    private void craftmaster$lockClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (!GroundEnd.locked(player)) return;
        player.containerMenu.sendAllDataToRemote();
        player.sendOverlayMessage(Component.literal("Inventaire verrouillé : tu as touché le sol").withStyle(ChatFormatting.RED));
        ci.cancel();
    }

    @Inject(method = "handlePlayerAction", cancellable = true, at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER))
    private void craftmaster$lockDrop(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        var a = packet.getAction();
        if (a != ServerboundPlayerActionPacket.Action.DROP_ITEM && a != ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
                && a != ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) return;
        if (!GroundEnd.locked(player)) return;
        player.containerMenu.sendAllDataToRemote();
        ci.cancel();
    }
}
