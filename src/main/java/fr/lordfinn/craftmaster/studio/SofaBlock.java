package fr.lordfinn.craftmaster.studio;

import fr.lordfinn.craftmaster.block.Shapes4;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * The contestants' velvet sofa: each block is one seat, and sofas side by side (same direction) join into one long
 * sofa: the armrests only stay at both ends. LEFT / RIGHT: joined on the seated player's left / right.
 */
public class SofaBlock extends StudioSeatBlock {

    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    // Facing north: base and cushion, backrest on the south side (the armrests are left out of the outline)
    private static final Map<Direction, VoxelShape> SHAPES = Shapes4.of(
            new double[]{0, 1, 1, 16, 9, 16},
            new double[]{0, 9, 13, 16, 20, 16});

    public SofaBlock(Properties props) {
        super(props, 9, SHAPES);
        registerDefaultState(defaultBlockState().setValue(LEFT, false).setValue(RIGHT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(LEFT, RIGHT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return joined(super.getStateForPlacement(ctx), ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        return dir.getAxis().isHorizontal() ? joined(state, level, pos) : state;
    }

    private static BlockState joined(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        // Seated, looking toward FACING: the left hand is counter-clockwise from it
        return state.setValue(LEFT, joins(level.getBlockState(pos.relative(facing.getCounterClockWise())), facing))
                .setValue(RIGHT, joins(level.getBlockState(pos.relative(facing.getClockWise())), facing));
    }

    private static boolean joins(BlockState other, Direction facing) {
        return other.getBlock() instanceof SofaBlock && other.getValue(FACING) == facing;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }
}
