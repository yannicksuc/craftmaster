package fr.lordfinn.craftmaster.mixin;

import fr.lordfinn.craftmaster.show.Spectators;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.GameModeCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** /gamemode by an organiser: the player leaves their spectator view, F7 and F8 ({@link Spectators#organiserMoved}). */
@Mixin(GameModeCommand.class)
public abstract class OrganiserGameModeMixin {

    @Inject(method = "setGameMode(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/GameType;)Z", at = @At("HEAD"))
    private static void craftmaster$released(CommandSourceStack source, ServerPlayer target, GameType mode, CallbackInfoReturnable<Boolean> ci) {
        Spectators.organiserMoved(source, target);
    }
}
