package fr.lordfinn.craftmaster.block;

import fr.lordfinn.craftmaster.entity.SeatEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/** A chair: right-click to sit, sneak to get up. FACING is where the seated player looks. */
public class ChairBlock extends Block {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Height of the seat top (block/chair_template.json: seat at y 7..9 px). */
    public static final double SEAT_TOP = 9 / 16.0;

    // Facing north: seat, 4 legs, backrest on the south side (same boxes as the model, backrest cut at 16 px)
    private static final Map<Direction, VoxelShape> SHAPES = Shapes4.of(
            new double[]{2, 7, 2, 14, 9, 14},
            new double[]{2, 0, 2, 4, 7, 4}, new double[]{12, 0, 2, 14, 7, 4},
            new double[]{2, 0, 12, 4, 7, 14}, new double[]{12, 0, 12, 14, 7, 14},
            new double[]{2, 9, 12, 14, 16, 14});

    public ChairBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** Height of the seat top in blocks, where the seated player's hips go (studio seats override it). */
    public double seatTop() {
        return SEAT_TOP;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // The seat faces the player who places it, the backrest is on the far side
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown() || player.isPassenger()) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (SeatEntity.occupied(server, pos)) {
            if (player instanceof ServerPlayer sp)
                sp.sendOverlayMessage(Component.translatable("message.craftmaster.chair_taken").withStyle(ChatFormatting.GRAY));
            return InteractionResult.CONSUME;
        }
        SeatEntity.sit(server, pos, state.getValue(FACING), player);
        return InteractionResult.SUCCESS_SERVER;
    }
}
