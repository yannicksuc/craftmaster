package fr.lordfinn.craftmaster.task;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

/**
 * An envelope lying on a table, as on the show's set: right-click picks it up (it goes to the hand), crouching
 * with an empty hand opens it in place. The wax colour and the opened state are block states (their own
 * textures); the content is in {@link EnvelopeBlockEntity}.
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

    private static final VoxelShape NORTH_SOUTH = Block.box(2, 0, 4, 14, 1, 12), EAST_WEST = Block.box(4, 0, 2, 12, 1, 14);

    public EnvelopeBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WAX, WaxColor.RED).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, WAX, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
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
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? EAST_WEST : NORTH_SOUTH;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                || !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
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
        if (player.isShiftKeyDown()) {
            // Opened where it lies: it stays on the table, opened
            Tasks.openEnvelope(sp, env, opened -> {
                be.setEnvelope(opened);
                server.setBlock(pos, stateFor(state, opened), Block.UPDATE_ALL);
            });
            return InteractionResult.SUCCESS_SERVER;
        }
        ItemStack stack = TaskComponents.setEnvelope(new ItemStack(TaskContent.ENVELOPE), env);
        server.removeBlock(pos, false);
        if (player.getMainHandItem().isEmpty()) player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        else if (!player.getInventory().add(stack)) player.spawnAtLocation((ServerLevel) player.level(), stack);
        server.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.2f);
        sp.sendOverlayMessage(Component.translatable(env.opened() ? "message.craftmaster.envelope_taken_open" : "message.craftmaster.envelope_taken")
                .withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS_SERVER;
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
