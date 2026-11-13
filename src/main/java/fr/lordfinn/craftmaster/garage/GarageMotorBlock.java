package fr.lordfinn.craftmaster.garage;

import fr.lordfinn.craftmaster.Permissions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Garage door motor: placed right above a door built of any blocks, it makes it slide up into the lintel and back
 * down ({@link GarageMotorBlockEntity}). Its front faces the player who places it.
 * <p>
 * Opened and closed by: a redstone signal on the motor or around the doorway (a button switches it, a lever holds it),
 * a right click with an empty hand in creative, {@code /craftMasterGarage}, the show's actions (garage.open...).
 * Right click with a block (organisers): the motor takes that block's look, to disappear into the lintel; sneak +
 * right click with an empty hand: gives its own look back, or tells what door it found.
 */
public class GarageMotorBlock extends BaseEntityBlock {

    // Minecraft 26.2 blocks must give a codec (26.3 dropped the requirement)
    //? if <26.3 {
    /*@Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }
    *///?}

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Drawn as another block (the block model is empty, the renderer draws the chosen block). */
    public static final BooleanProperty CAMOUFLAGED = BooleanProperty.create("camouflaged");

    public GarageMotorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CAMOUFLAGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, CAMOUFLAGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    /** Tells the builder which door the motor found under it. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof GarageMotorBlockEntity be)
            player.sendOverlayMessage(describe(be.findPanel()));
    }

    /** "Porte trouvée : 3 × 3 (9 blocs)" or how to give one. */
    public static Component describe(GaragePanel panel) {
        if (panel.isEmpty())
            return Component.literal("Pas de porte sous le moteur : posez-le juste au-dessus du panneau.").withStyle(ChatFormatting.RED);
        return Component.literal("Porte trouvée : " + panel.width() + " × " + panel.height() + " (" + panel.size() + " blocs)")
                .withStyle(ChatFormatting.GOLD);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof GarageMotorBlockEntity be)) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            if (!player.mayBuild()) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (state.getValue(CAMOUFLAGED)) {
                setCamouflage(level, pos, state, be, null);
            } else {
                player.sendOverlayMessage(describe(be.isOpen() || be.isMoving() ? be.panel() : be.findPanel()));
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        // Like the curtains: the organisers building the set try the door by hand
        if (!player.isCreative()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (be.move(null) == null) player.sendOverlayMessage(describe(GaragePanel.EMPTY));
        return InteractionResult.SUCCESS_SERVER;
    }

    /** A block in hand (organisers): the motor looks like it. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof BlockItem item) || item.getBlock() instanceof GarageMotorBlock
                || !(level.getBlockEntity(pos) instanceof GarageMotorBlockEntity be))
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        BlockState look = item.getBlock().defaultBlockState();
        if (look.getRenderShape() != RenderShape.MODEL) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !Permissions.isAdmin(sp) || !player.mayBuild()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        setCamouflage(level, pos, state, be, look);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void setCamouflage(Level level, BlockPos pos, BlockState state, GarageMotorBlockEntity be, BlockState look) {
        level.setBlock(pos, state.setValue(CAMOUFLAGED, look != null), Block.UPDATE_CLIENTS);
        be.setCamouflage(look);
        level.playSound(null, pos, look != null ? SoundEvents.ITEM_FRAME_ADD_ITEM : SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8f, 1f);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GarageMotorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, GarageDoors.MOTOR_BE, GarageMotorBlockEntity::serverTick);
    }
}
