package fr.lordfinn.craftmaster.mixin;

import fr.lordfinn.craftmaster.regie.AgentBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** What a command of the régie's agent says to the player (WorldEdit answers there) is its answer too. */
@Mixin(ServerPlayer.class)
public abstract class AgentHearsMixin {

    @Inject(method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"))
    private void craftmaster$heard(Component message, boolean overlay, CallbackInfo ci) {
        AgentBridge.heard((ServerPlayer) (Object) this, message);
    }
}
