package fr.lordfinn.craftmaster.mixin;

import fr.lordfinn.craftmaster.show.Spectators;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.LookAt;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/** /tp by an organiser: the player leaves their spectator view, F7 and F8 ({@link Spectators#organiserMoved}). */
@Mixin(TeleportCommand.class)
public abstract class OrganiserTeleportMixin {

    @Inject(method = "performTeleport", at = @At("HEAD"))
    private static void craftmaster$released(CommandSourceStack source, Entity target, ServerLevel level, double x, double y, double z,
                                             Set<Relative> relatives, float yaw, float pitch, LookAt lookAt, CallbackInfo ci) {
        Spectators.organiserMoved(source, target);
    }
}
