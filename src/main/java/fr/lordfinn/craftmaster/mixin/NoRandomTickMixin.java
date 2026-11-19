package fr.lordfinn.craftmaster.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Set;

/**
 * No random ticks in the worlds of the decorated trial rooms (« La pomme »): nothing grows, spreads, withers or
 * oxidises there, so the room stays exactly as built (crops standing on stone, dry farmland...). The game's
 * {@code random_tick_speed} rule is server-wide, so it can't be set for one world only.
 */
@Mixin(ServerLevel.class)
public abstract class NoRandomTickMixin {

    /** Worlds without random ticks. */
    private static final Set<String> FROZEN = Set.of("minecraft:pomme");

    @ModifyVariable(method = "tickChunk", at = @At("HEAD"), argsOnly = true)
    private int craftmaster$noRandomTicks(int randomTickSpeed) {
        return FROZEN.contains(((ServerLevel) (Object) this).dimension().identifier().toString()) ? 0 : randomTickSpeed;
    }
}
