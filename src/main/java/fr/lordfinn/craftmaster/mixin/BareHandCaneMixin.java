package fr.lordfinn.craftmaster.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * In « La pomme » the sugar cane breaks with a bare hand, even in adventure mode (where only the items allowed by
 * {@code can_break} may break a block). Runs on both sides: the client must let the punch start, the server must
 * accept it. Only the sugar cane, only in the pomme world, only with an empty main hand (the iron hoe already has
 * its own {@code can_break} rule).
 */
@Mixin(Player.class)
public abstract class BareHandCaneMixin {

    /** Worlds where the sugar cane breaks by hand. */
    private static final Set<String> WORLDS = Set.of("minecraft:pomme");

    @Inject(method = "blockActionRestricted", at = @At("HEAD"), cancellable = true)
    private void craftmaster$bareHandCane(Level level, BlockPos pos, GameType mode, CallbackInfoReturnable<Boolean> cir) {
        if (!mode.isBlockPlacingRestricted() || mode == GameType.SPECTATOR) return;
        Player self = (Player) (Object) this;
        if (!self.getMainHandItem().isEmpty()) return;
        if (!WORLDS.contains(level.dimension().identifier().toString())) return;
        if (level.getBlockState(pos).is(Blocks.SUGAR_CANE)) cir.setReturnValue(false);
    }
}
