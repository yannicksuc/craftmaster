package fr.lordfinn.craftmaster.task;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Locale;

/**
 * An envelope put somewhere on the set, as on the show: lying on a table, pinned to a wall, stuck to the ceiling or
 * hanging from a chain ({@link Mount}, from the face it was put on: under a chain it hangs; a chain used on a ceiling
 * envelope hangs it, again puts it back flat). Right-click reads it in place, crouching takes it back. The wax colour
 * and the opened state are block states (their own textures); the content is in {@link EnvelopeBlockEntity}.
 */
public class EnvelopeBlock extends BaseEntityBlock {

    // Minecraft 26.2 blocks must give a codec (26.3 dropped the requirement)
    //? if <26.3 {
    /*@Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }
    *///?}

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<WaxColor> WAX = EnumProperty.create("wax", WaxColor.class);
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final EnumProperty<Mount> MOUNT = EnumProperty.create("mount", Mount.class);

    /**
     * Where the envelope is: on the block below (floor, facing = where the player looked, the flap away from them),
     * against the block behind it (wall, facing = out of the wall), under the block above, flat (ceiling, facing as on
     * the floor) or hanging from it by a short chain (hanging, facing = the side its front shows).
     */
    public enum Mount implements StringRepresentable {
        FLOOR, WALL, CEILING, HANGING;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final VoxelShape NORTH_SOUTH = Block.box(2, 0, 4, 14, 1, 12), EAST_WEST = Block.box(4, 0, 2, 12, 1, 14),
            CEILING_NS = Block.box(2, 15, 4, 14, 16, 12), CEILING_EW = Block.box(4, 15, 2, 12, 16, 14),
            WALL_N = Block.box(2, 4, 15, 14, 12, 16), WALL_S = Block.box(2, 4, 0, 14, 12, 1),
            WALL_E = Block.box(0, 4, 2, 1, 12, 14), WALL_W = Block.box(15, 4, 2, 16, 12, 14),
            HANGING_NS = Block.box(2, 1, 7, 14, 16, 9), HANGING_EW = Block.box(7, 1, 2, 9, 16, 14);

    public EnvelopeBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WAX, WaxColor.RED).setValue(OPEN, false)
                .setValue(MOUNT, Mount.FLOOR));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, WAX, OPEN, MOUNT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        Direction look = ctx.getHorizontalDirection();
        if (face == Direction.UP) return defaultBlockState().setValue(FACING, look);
        if (face == Direction.DOWN) {
            BlockPos above = ctx.getClickedPos().above();
            return ctx.getLevel().getBlockState(above).is(BlockTags.CHAINS)
                    ? defaultBlockState().setValue(MOUNT, Mount.HANGING).setValue(FACING, look.getOpposite())
                    : defaultBlockState().setValue(MOUNT, Mount.CEILING).setValue(FACING, look);
        }
        return defaultBlockState().setValue(MOUNT, Mount.WALL).setValue(FACING, face);
    }

    /** The block holding it up. */
    private static BlockPos support(BlockState state, BlockPos pos) {
        return switch (state.getValue(MOUNT)) {
            case FLOOR -> pos.below();
            case WALL -> pos.relative(state.getValue(FACING).getOpposite());
            case CEILING, HANGING -> pos.above();
        };
    }

    /** The middle of the envelope in its block (0..1), what a camera frames. */
    public static double[] center(BlockState state) {
        if (!state.hasProperty(MOUNT)) return new double[]{0.5, 0.05, 0.5};
        Direction f = state.getValue(FACING);
        return switch (state.getValue(MOUNT)) {
            case FLOOR -> new double[]{0.5, 0.05, 0.5};
            case CEILING -> new double[]{0.5, 0.95, 0.5};
            case HANGING -> new double[]{0.5, 0.3, 0.5};
            case WALL -> new double[]{0.5 - f.getStepX() * 0.45, 0.5, 0.5 - f.getStepZ() * 0.45};
        };
    }

    /** The state showing this envelope: its wax, opened or not. */
    public static BlockState stateFor(BlockState base, TaskComponents.Envelope e) {
        return base.setValue(WAX, WaxColor.of(e.wax())).setValue(OPEN, e.opened());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnvelopeBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction f = state.getValue(FACING);
        boolean x = f.getAxis() == Direction.Axis.X;
        return switch (state.getValue(MOUNT)) {
            case FLOOR -> x ? EAST_WEST : NORTH_SOUTH;
            case CEILING -> x ? CEILING_EW : CEILING_NS;
            case HANGING -> x ? HANGING_EW : HANGING_NS;
            case WALL -> switch (f) {
                case SOUTH -> WALL_S;
                case EAST -> WALL_E;
                case WEST -> WALL_W;
                default -> WALL_N;
            };
        };
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos s = support(state, pos);
        Direction toward = switch (state.getValue(MOUNT)) {
            case FLOOR -> Direction.UP;
            case WALL -> state.getValue(FACING);
            case CEILING, HANGING -> Direction.DOWN;
        };
        BlockState held = level.getBlockState(s);
        return held.isFaceSturdy(level, s, toward) || !held.getCollisionShape(level, s).isEmpty();
    }

    /** An iron or copper chain (the chains block tag: 26.2 has no item tag constant for it). */
    private static boolean isChain(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.BlockItem b && b.getBlock().defaultBlockState().is(BlockTags.CHAINS);
    }

    /** A chain used on a ceiling envelope hangs it from the ceiling; on a hanging one, puts it back flat. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        Mount mount = state.getValue(MOUNT);
        if (!isChain(stack) || (mount != Mount.CEILING && mount != Mount.HANGING)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!player.mayBuild()) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(MOUNT, mount == Mount.CEILING ? Mount.HANGING : Mount.CEILING)
                    .setValue(FACING, state.getValue(FACING).getOpposite()), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
        }
        return InteractionResult.SUCCESS;
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
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof EnvelopeBlockEntity be)) return InteractionResult.PASS;
        TaskComponents.Envelope env = be.envelope();
        if (!player.isShiftKeyDown()) {
            // Read where it is, as if in hand: it stays there (opened once its seal is broken). Breaking
            // it, or sneaking, takes it back
            Tasks.openEnvelope(sp, env, opened -> {
                be.setEnvelope(opened);
                server.setBlock(pos, stateFor(state, opened), Block.UPDATE_ALL);
            });
            return InteractionResult.SUCCESS_SERVER;
        }
        ItemStack stack = TaskComponents.setEnvelope(new ItemStack(TaskContent.ENVELOPE), env);
        server.removeBlock(pos, false);
        if (player.getMainHandItem().isEmpty()) player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        else if (!player.getInventory().add(stack)) player.spawnAtLocation((ServerLevel) player.level(), stack);
        server.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.2f);
        sp.sendOverlayMessage(Component.translatable(env.opened() ? "message.craftmaster.envelope_taken_open" : "message.craftmaster.envelope_taken")
                .withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Broken in creative (no drops there): the envelope goes into the player's inventory, never lost. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && level.getBlockEntity(pos) instanceof EnvelopeBlockEntity be) {
            ItemStack stack = TaskComponents.setEnvelope(new ItemStack(TaskContent.ENVELOPE), be.envelope());
            if (!player.getInventory().add(stack)) player.spawnAtLocation((ServerLevel) level, stack);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Broken or blown up: the envelope drops with its content (never lost). */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        TaskComponents.Envelope env = be instanceof EnvelopeBlockEntity e ? e.envelope() : TaskComponents.Envelope.BLANK;
        return List.of(TaskComponents.setEnvelope(new ItemStack(TaskContent.ENVELOPE), env));
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        TaskComponents.Envelope env = level.getBlockEntity(pos) instanceof EnvelopeBlockEntity e ? e.envelope() : TaskComponents.Envelope.BLANK;
        return TaskComponents.setEnvelope(new ItemStack(TaskContent.ENVELOPE), env);
    }
}
