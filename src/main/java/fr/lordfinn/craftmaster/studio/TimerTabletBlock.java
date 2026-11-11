package fr.lordfinn.craftmaster.studio;

import fr.lordfinn.craftmaster.Permissions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The timer tablet (a framed digital timer, like the one held up in Taskmaster): placed like a sign, standing on
 * its foot in 16 directions, hung on a wall, lying flat on a table or tilted on an easel; drawn by the client
 * ({@code TimerTabletRenderer}). Right click: organisers open its settings (sneak: cycle the pose); anyone starts /
 * pauses it when "click" is one of its triggers. Redstone starts / pauses / restarts it (setting); a comparator
 * reads 15 once a timer is over.
 */
public class TimerTabletBlock extends BaseEntityBlock {

    // Minecraft 26.2 blocks must give a codec (26.3 dropped the requirement)
    //? if <26.3 {
    /*@Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }
    *///?}

    public static final EnumProperty<TabletClock.Pose> POSE = EnumProperty.create("pose", TabletClock.Pose.class);
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;

    private static final VoxelShape STANDING = Block.box(3, 0, 3, 13, 12, 13), FLAT = Block.box(2, 0, 2, 14, 2, 14);
    private static final java.util.Map<Direction, VoxelShape> WALL = Shapes4Wall.shapes();

    public TimerTabletBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(POSE, TabletClock.Pose.STAND).setValue(ROTATION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(POSE, ROTATION);
    }

    /** Against a wall: hung on it. On top of a block: standing facing the player, or flat when sneaking. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        if (face.getAxis().isHorizontal())
            return defaultBlockState().setValue(POSE, TabletClock.Pose.WALL).setValue(ROTATION, RotationSegment.convertToSegment(face));
        TabletClock.Pose pose = ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown() ? TabletClock.Pose.FLAT : TabletClock.Pose.STAND;
        return defaultBlockState().setValue(POSE, pose).setValue(ROTATION, RotationSegment.convertToSegment(ctx.getRotation() + 180));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TimerTabletBlockEntity be) {
            TabletClock.Settings s = be.settings().copy();
            s.pose = state.getValue(POSE);
            be.apply(s);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(POSE)) {
            case WALL -> WALL.getOrDefault(RotationSegment.convertToDirection(state.getValue(ROTATION)).orElse(Direction.NORTH), STANDING);
            case FLAT -> FLAT;
            default -> STANDING;
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // Everything is drawn by the block entity renderer (any of 16 directions, live digits)
        return RenderShape.INVISIBLE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TimerTabletBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) player;
        boolean admin = Permissions.isAdmin(sp);
        if (admin && player.isShiftKeyDown()) {
            // Next pose: standing → easel → flat → standing (a hung tablet stays on its wall)
            TabletClock.Settings s = be.settings().copy();
            s.pose = state.getValue(POSE);
            if (s.pose != TabletClock.Pose.WALL) {
                s.pose = switch (s.pose) {
                    case STAND -> TabletClock.Pose.EASEL;
                    case EASEL -> TabletClock.Pose.FLAT;
                    default -> TabletClock.Pose.STAND;
                };
                be.apply(s);
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        if (admin) {
            StudioNetwork.openTablet(sp, be);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (be.settings().clickToggles) {
            be.controlGroup("toggle");
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean moving) {
        // A desk bell next to it stops it on its own (DeskBellBlock): its pulse must not toggle it back on
        if (block instanceof DeskBellBlock) return;
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof TimerTabletBlockEntity be) be.redstone(level.hasNeighborSignal(pos));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction dir) {
        return level.getBlockEntity(pos) instanceof TimerTabletBlockEntity be ? be.signal() : 0;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(ROTATION, rot.rotate(state.getValue(ROTATION), 16));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(ROTATION, mirror.mirror(state.getValue(ROTATION), 16));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TimerTabletBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, Studio.TIMER_TABLET_BE, TimerTabletBlockEntity::serverTick);
    }

    /** Wall shapes: a thin frame against the wall behind (the tablet faces out of it). */
    private static final class Shapes4Wall {
        static java.util.Map<Direction, VoxelShape> shapes() {
            return fr.lordfinn.craftmaster.block.Shapes4.of(new double[]{2, 3, 14, 14, 13, 16});
        }
    }
}
