package fr.lordfinn.craftmaster.studio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/** A {@link FacingEntityBlock} that joins its neighbours of the same kind into one long piece ({@link SideJoins}). */
public abstract class JoiningBlock extends FacingEntityBlock {

    protected JoiningBlock(Properties props, Map<Direction, VoxelShape> shapes) {
        super(props, shapes, false);
        registerDefaultState(defaultBlockState().setValue(SideJoins.LEFT, false).setValue(SideJoins.RIGHT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(SideJoins.LEFT, SideJoins.RIGHT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return SideJoins.join(super.getStateForPlacement(ctx), ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        return dir.getAxis().isHorizontal() ? SideJoins.join(state, level, pos) : state;
    }
}
