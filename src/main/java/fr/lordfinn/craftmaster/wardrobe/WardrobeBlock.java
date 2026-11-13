package fr.lordfinn.craftmaster.wardrobe;

import fr.lordfinn.craftmaster.Permissions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The wardrobe: a walnut armoire two blocks high, with gold trim and a mirror on its right door (as seen from the front). Its doors open
 * while someone chooses an outfit. Click: the outfit screen. Sneak + click: take back your own clothes at once.
 * Only the lower half holds the outfits ({@link WardrobeBlockEntity}).
 */
public class WardrobeBlock extends Block implements EntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    // Body 14 deep against the back of the block; the cornice and the plinth overhang by one pixel
    private static final VoxelShape[] LOWER = shapes(false), UPPER = shapes(true);

    public WardrobeBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(OPEN, false));
    }

    /** By facing (2D data value order: south, west, north, east). */
    private static VoxelShape[] shapes(boolean upper) {
        VoxelShape[] out = new VoxelShape[4];
        for (Direction d : Direction.Plane.HORIZONTAL) {
            // North-facing box (front at z = 1), turned
            double[] b = upper ? new double[]{0, 0, 1, 16, 16, 16} : new double[]{0, 0, 1, 16, 16, 16};
            out[d.get2DDataValue()] = rotated(b, d);
        }
        return out;
    }

    private static VoxelShape rotated(double[] b, Direction facing) {
        double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        return switch (facing) {
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, HALF, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(ctx)) return null;
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        // An organiser's (or a creative player's) wardrobe is a show wardrobe, filled with the ready outfits
        if (level instanceof ServerLevel && placer instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof WardrobeBlockEntity w
                && !w.configured()) {
            w.setup(Permissions.isAdmin(sp) || sp.isCreative());
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (dir.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (dir == Direction.UP)) {
            // The other half: gone → this half goes too; changed (doors) → follow it
            if (!neighbour.is(this) || neighbour.getValue(HALF) == half) return Blocks.AIR.defaultBlockState();
            return state.setValue(FACING, neighbour.getValue(FACING)).setValue(OPEN, neighbour.getValue(OPEN));
        }
        return super.updateShape(state, level, ticks, pos, dir, neighbourPos, neighbour, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Creative: breaking the top must not drop the wardrobe from the bottom (like a door)
        if (!level.isClientSide() && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState b = level.getBlockState(below);
            if (b.is(this) && b.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(b));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return (state.getValue(HALF) == DoubleBlockHalf.UPPER ? UPPER : LOWER)[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new WardrobeBlockEntity(pos, state) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != Wardrobes.WARDROBE_ENTITY) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<WardrobeBlockEntity>) WardrobeBlockEntity::serverTick;
    }

    /** The lower half, which holds the outfits. */
    public static BlockPos base(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(base(pos, state)) instanceof WardrobeBlockEntity w)) return InteractionResult.PASS;
        if (sp.isShiftKeyDown()) Wardrobes.takeBack(sp, w);
        else w.open(sp);
        return InteractionResult.SUCCESS_SERVER;
    }
}
