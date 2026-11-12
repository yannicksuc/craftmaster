package fr.lordfinn.craftmaster.studio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Pieces of the set that join their neighbours into one long piece (contestants' desks, prize tables): two blocks of
 * the same kind side by side, facing the same way, lose the sides between them. LEFT / RIGHT: joined on the
 * counter-clockwise / clockwise side of the block's facing (west / east of a block facing north, as in its model).
 */
public final class SideJoins {

    private SideJoins() {}

    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    /** The state with LEFT / RIGHT set from the blocks on both sides. */
    public static BlockState join(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FacingEntityBlock.FACING);
        return state.setValue(LEFT, joins(state, level.getBlockState(pos.relative(facing.getCounterClockWise())), facing))
                .setValue(RIGHT, joins(state, level.getBlockState(pos.relative(facing.getClockWise())), facing));
    }

    private static boolean joins(BlockState self, BlockState other, Direction facing) {
        return other.getBlock() == self.getBlock() && other.getValue(FacingEntityBlock.FACING) == facing;
    }
}
