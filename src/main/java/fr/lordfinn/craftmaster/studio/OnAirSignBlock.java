package fr.lordfinn.craftmaster.studio;

import fr.lordfinn.craftmaster.block.Shapes4;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The "EN DIRECT" light box hung on a studio wall (the model is wider than the block). Lit by a right click or by
 * redstone (lit while powered).
 */
public class OnAirSignBlock extends DecorBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public OnAirSignBlock(Properties props) {
        // Facing north: the box against the wall on the south side
        super(props, Shapes4.of(new double[]{0, 3, 13, 16, 13, 16}));
        registerDefaultState(defaultBlockState().setValue(LIT, false).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(LIT, POWERED);
    }

    /** On a wall: facing out of it; on the floor or a ceiling: toward the player. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        boolean powered = ctx.getLevel().hasNeighborSignal(ctx.getClickedPos());
        return defaultBlockState().setValue(FACING, facing).setValue(POWERED, powered).setValue(LIT, powered);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayBuild()) return InteractionResult.PASS;
        setLit(level, pos, state, !state.getValue(LIT));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean moving) {
        if (!(level instanceof ServerLevel)) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) return;
        BlockState now = state.setValue(POWERED, powered);
        level.setBlock(pos, now, Block.UPDATE_ALL);
        setLit(level, pos, now, powered);
    }

    static void setLit(Level level, BlockPos pos, BlockState state, boolean lit) {
        if (state.getValue(LIT) == lit) return;
        level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
        level.playSound(null, pos, lit ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF, SoundSource.BLOCKS, 0.5f, 0.9f);
    }
}
