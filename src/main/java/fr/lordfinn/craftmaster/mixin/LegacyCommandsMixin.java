package fr.lordfinn.craftmaster.mixin;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.command.CommandTree;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The old command names (/cfma, /cam, /tache...) run as their new form under /cm ({@link CommandTree#translate}):
 * the console, RCON, command blocks, the cue lists and the AI control go through here.
 */
@Mixin(Commands.class)
public abstract class LegacyCommandsMixin {

    @Shadow
    public abstract void performPrefixedCommand(CommandSourceStack source, String command);

    @Inject(method = "performPrefixedCommand", at = @At("HEAD"), cancellable = true)
    private void craftmaster$legacy(CommandSourceStack source, String command, CallbackInfo ci) {
        String gone = CommandTree.gone(command);
        if (gone != null) {
            source.sendFailure(Component.literal("Commande supprimée : utilise " + gone));
            ci.cancel();
            return;
        }
        String now = CommandTree.translate(command);
        if (now == null) return;
        CraftMaster.LOG.info("[Commandes] ancienne commande /{} -> /{}", command.startsWith("/") ? command.substring(1) : command, now);
        ci.cancel();
        performPrefixedCommand(source, now);
    }
}
