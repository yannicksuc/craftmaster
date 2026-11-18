package fr.lordfinn.craftmaster.mixin;

import fr.lordfinn.craftmaster.command.CommandTree;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player typing an old command name: it runs as its new form, and the new form shows in grey (to learn it). */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class LegacyChatCommandMixin {

    @Shadow
    public ServerPlayer player;

    @Shadow
    protected abstract void performUnsignedChatCommand(String command);

    @Inject(method = "performUnsignedChatCommand", at = @At("HEAD"), cancellable = true)
    private void craftmaster$legacy(String command, CallbackInfo ci) {
        String gone = CommandTree.gone(command);
        if (gone != null) {
            player.sendSystemMessage(Component.literal("Commande supprimée : utilise " + gone).withStyle(ChatFormatting.RED));
            ci.cancel();
            return;
        }
        String now = CommandTree.translate(command);
        if (now == null) return;
        player.sendSystemMessage(Component.literal("Nouveau nom : /" + now).withStyle(st -> st.withColor(ChatFormatting.GRAY).withItalic(true)
                .withClickEvent(new ClickEvent.SuggestCommand("/" + now))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Les anciennes commandes marchent encore ; /cm aide pour l'arbre")))));
        ci.cancel();
        performUnsignedChatCommand(now);
    }
}
