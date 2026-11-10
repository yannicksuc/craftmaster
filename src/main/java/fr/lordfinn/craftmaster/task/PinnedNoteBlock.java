package fr.lordfinn.craftmaster.task;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * A sheet of paper pinned on a face of a block: the underside of a table, the back of a wardrobe, the inside of a
 * fireplace. {@code FACING} is the side the paper shows (its support is behind it). Which note it is lives in the
 * notes' registry (a {@code PINNED} hiding place at this position, see {@link Notes}).
 */
public class PinnedNoteBlock extends Block {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.NORTH, Block.box(3, 3, 15, 13, 13, 16));
        SHAPES.put(Direction.SOUTH, Block.box(3, 3, 0, 13, 13, 1));
        SHAPES.put(Direction.EAST, Block.box(0, 3, 3, 1, 13, 13));
        SHAPES.put(Direction.WEST, Block.box(15, 3, 3, 16, 13, 13));
        SHAPES.put(Direction.UP, Block.box(3, 0, 3, 13, 1, 13));
        SHAPES.put(Direction.DOWN, Block.box(3, 15, 3, 13, 16, 13));
    }

    public PinnedNoteBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Someone who may not read it (the notes' callback let it through): just paper with unreadable writing. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        player.sendOverlayMessage(Component.translatable("message.craftmaster.note_unreadable").withStyle(ChatFormatting.GRAY));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Notes.onPinnedRemoved(level, pos);
    }
}
