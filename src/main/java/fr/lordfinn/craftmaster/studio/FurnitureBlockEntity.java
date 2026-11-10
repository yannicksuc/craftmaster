package fr.lordfinn.craftmaster.studio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds nothing: it only lets the client draw a piece of furniture whose model is bigger than a block (see
 * {@link Furniture} and client FurnitureRenderer). Everything the block needs is in its block state.
 */
public class FurnitureBlockEntity extends BlockEntity {

    public FurnitureBlockEntity(BlockPos pos, BlockState state) {
        super(Furniture.BE, pos, state);
    }
}
