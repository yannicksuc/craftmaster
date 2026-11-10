package fr.lordfinn.craftmaster.studio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * A studio seat drawn from LordFinn's own furniture model (the throne, the contestants' chair): sits exactly like a
 * {@link StudioSeatBlock}; the block model is empty and {@link FurnitureBlockEntity} draws the furniture.
 */
public class FurnitureSeatBlock extends StudioSeatBlock implements EntityBlock {

    public FurnitureSeatBlock(Properties props, double seatTopPixels, Map<Direction, VoxelShape> shapes) {
        super(props, seatTopPixels, shapes);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FurnitureBlockEntity(pos, state);
    }
}
