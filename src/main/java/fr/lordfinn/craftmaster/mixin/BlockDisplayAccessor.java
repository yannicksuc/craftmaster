package fr.lordfinn.craftmaster.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The block a vanilla block display shows (the flowers of the vase's bouquet: crossed planes, seen from everywhere). */
@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayAccessor {
    @Invoker("setBlockState")
    void craftmaster$setBlockState(BlockState state);
}
