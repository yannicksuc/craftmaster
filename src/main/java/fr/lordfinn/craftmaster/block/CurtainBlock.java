package fr.lordfinn.craftmaster.block;

import fr.lordfinn.craftmaster.curtain.CurtainBlockEntity;
import fr.lordfinn.craftmaster.curtain.Curtains;
import fr.lordfinn.craftmaster.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * Stage curtain: a pleated fabric panel. Touching curtain blocks facing the same way form one curtain, which opens
 * from the middle outwards and closes from the sides inwards (see {@link Curtains}). An open curtain is invisible.
 * <p>
 * Opened and closed by: a redstone signal, the curtain remote, a right-click in creative, or /cm plateau rideau bloc.
 * <p>
 * In the world the fabric is drawn by the client ({@link CurtainBlockEntity}, client/curtain/CurtainRenderer), so it
 * slides continuously and gathers at the sides; the block models only serve the item and the particles.
 */
public class CurtainBlock extends Block implements EntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** Lowest block of its column: only it shows the darker hem, so a tall curtain has one hem at the bottom. */
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");

    // Pleats: z 6..10 px when facing north (block/curtain_template.json)
    private static final Map<Direction, VoxelShape> SHAPES = Shapes4.of(new double[]{0, 0, 6, 16, 16, 10});

    public CurtainBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(POWERED, false)
                .setValue(BOTTOM, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, OPEN, POWERED, BOTTOM);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        // Continuing a curtain: same direction and same open/closed state as the curtain next to it
        for (Direction d : Direction.values()) {
            BlockState n = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(d));
            if (n.getBlock() instanceof CurtainBlock) {
                state = state.setValue(FACING, n.getValue(FACING)).setValue(OPEN, n.getValue(OPEN));
                break;
            }
        }
        return withBottom(state, ctx.getLevel().getBlockState(ctx.getClickedPos().below()));
    }

    /** The hem shows unless a curtain hanging the same way continues below. */
    private static BlockState withBottom(BlockState state, BlockState below) {
        boolean continues = below.getBlock() instanceof CurtainBlock && below.getValue(FACING) == state.getValue(FACING);
        return state.setValue(BOTTOM, !continues);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        return direction == Direction.DOWN ? withBottom(state, neighbour) : state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        // An open curtain can still be aimed at (to break or link it) while holding the remote or a curtain
        if (state.getValue(OPEN) && !ctx.isHoldingItem(ModContent.CURTAIN_REMOTE)
                && ModContent.CURTAINS.values().stream().noneMatch(c -> ctx.isHoldingItem(c.asItem())))
            return Shapes.empty();
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CurtainBlockEntity(pos, state);
    }

    /** The fabric is drawn by the block entity's renderer. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /**
     * Creative players (the organisers building the stage) can open/close a curtain with an empty hand. With an item
     * in hand the click goes to the item (the remote links itself, a curtain block is placed).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isCreative() || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (level instanceof ServerLevel server) Curtains.animate(server, pos, null);
        return InteractionResult.SUCCESS;
    }

    /** A redstone signal on any block of the curtain opens it; losing the signal closes it. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean movedByPiston) {
        if (!(level instanceof ServerLevel server)) return;
        boolean powered = server.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) return;
        server.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        Curtains.animate(server, pos, powered);
    }
}
