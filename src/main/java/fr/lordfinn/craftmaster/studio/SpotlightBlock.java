package fr.lordfinn.craftmaster.studio;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Orientable stage spotlight, standing on the floor or hung under a ceiling. Right click: on / off. Sneak + right
 * click with an empty hand: points where you look. A dye: colours the beam. Following a player:
 * {@code /craftMasterSpotlight follow} (or the voice control).
 */
public class SpotlightBlock extends BaseEntityBlock {

    // Minecraft 26.2 blocks must give a codec (26.3 dropped the requirement)
    //? if <26.3 {
    /*@Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }
    *///?}

    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty HANGING = BlockStateProperties.HANGING;

    private static final VoxelShape STANDING = Block.box(3, 0, 3, 13, 15, 13);
    private static final VoxelShape HUNG = Block.box(3, 1, 3, 13, 16, 13);

    public SpotlightBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(LIT, true).setValue(HANGING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LIT, HANGING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(HANGING, ctx.getClickedFace() == Direction.DOWN);
    }

    /** A new spotlight points away from the player who placed it, slightly down (or up from the floor). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide() && placer != null && level.getBlockEntity(pos) instanceof SpotlightBlockEntity be)
            be.aim(placer.getYRot(), state.getValue(HANGING) ? 35 : -10);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayBuild() || !(level.getBlockEntity(pos) instanceof SpotlightBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        // Organisers: the spotlight's menu (colour, intensity, cone, direction, follow, effect)
        if (player.isShiftKeyDown() && player instanceof net.minecraft.server.level.ServerPlayer sp && fr.lordfinn.craftmaster.Permissions.isAdmin(sp)
                && SpotlightNetwork.open(sp, pos)) {
            level.playSound(null, pos, SoundEvents.SPYGLASS_USE, SoundSource.BLOCKS, 0.6f, 1.6f);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (player.isShiftKeyDown()) {
            // Aims along the player's view, from the spotlight: stand beside it and look at the target
            be.aim(player.getYRot(), player.getXRot());
            player.sendOverlayMessage(Component.translatable("message.craftmaster.spotlight_aimed").withStyle(ChatFormatting.GOLD));
            level.playSound(null, pos, SoundEvents.SPYGLASS_USE, SoundSource.BLOCKS, 0.8f, 1.4f);
            // Organisers get its settings in the chat, with buttons to turn, tilt, flip it
            if (player instanceof net.minecraft.server.level.ServerPlayer sp && fr.lordfinn.craftmaster.Permissions.isAdmin(sp))
                sp.sendSystemMessage(Spotlights.panel(be));
        } else {
            setLit(level, pos, state, !state.getValue(LIT));
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.get(net.minecraft.core.component.DataComponents.DYE) == null || !player.mayBuild() || !(level.getBlockEntity(pos) instanceof SpotlightBlockEntity be))
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) {
            DyeColor dye = stack.get(net.minecraft.core.component.DataComponents.DYE);
            be.setColor(beamColor(dye));
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    /** Beam colour of a dye: lighter than the dye itself (it is light), white gives the warm default. */
    public static int beamColor(DyeColor dye) {
        if (dye == null || dye == DyeColor.WHITE) return SpotlightBlockEntity.DEFAULT_COLOR;
        int c = dye.getTextureDiffuseColor() & 0xFFFFFF;
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        // Halfway to white keeps the hue but reads as light, not paint
        r = (r + 255) / 2;
        g = (g + 255) / 2;
        b = (b + 255) / 2;
        return (r << 16) | (g << 8) | b;
    }

    public static void setLit(Level level, BlockPos pos, BlockState state, boolean lit) {
        if (state.getValue(LIT) == lit) return;
        level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
        level.playSound(null, pos, lit ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF, SoundSource.BLOCKS, 0.8f, 0.8f);
        if (level.getBlockEntity(pos) instanceof SpotlightBlockEntity be) be.updateLight();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(HANGING) ? HUNG : STANDING;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpotlightBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, Studio.SPOTLIGHT_BE, SpotlightBlockEntity::serverTick);
    }
}
