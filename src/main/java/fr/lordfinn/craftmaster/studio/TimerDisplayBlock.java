package fr.lordfinn.craftmaster.studio;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.block.Shapes4;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Giant timer: a big panel with a title band and seven-segment digits (drawn by the client, the panel grows beyond
 * the block: {@link TimerDisplayBlockEntity#size()}). Hung on a wall it faces out of it. It works like the timer
 * tablet ({@link ClockBlockEntity}): right click, organisers open its settings (sneak: one size smaller); anyone
 * starts / pauses it when "click" is one of its triggers, otherwise builders change its size (sneak: smaller).
 * Redstone and a comparator as on the tablet.
 */
public class TimerDisplayBlock extends FacingEntityBlock {

    public TimerDisplayBlock(Properties props) {
        // Facing north: a bracket against the wall on the south side
        super(props, Shapes4.of(new double[]{2, 0, 13, 14, 12, 16}), true);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TimerDisplayBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) player;
        boolean admin = Permissions.isAdmin(sp);
        if (admin && !player.isShiftKeyDown()) {
            StudioNetwork.openTablet(sp, be);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!admin && be.settings().clickToggles) {
            be.controlGroup("toggle");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!player.mayBuild()) return InteractionResult.PASS;
        // Organisers sneaking: one size smaller; builders: bigger, sneaking smaller (as before the settings)
        int size = be.size() + (player.isShiftKeyDown() ? -1 : 1);
        if (size > TimerDisplayBlockEntity.MAX_SIZE) size = 1;
        if (size < 1) size = TimerDisplayBlockEntity.MAX_SIZE;
        be.setSize(size);
        player.sendOverlayMessage(Component.translatable("message.craftmaster.timer_size", size).withStyle(ChatFormatting.GOLD));
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, 1.2f);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean moving) {
        // A desk bell next to it stops it on its own (DeskBellBlock): its pulse must not toggle it back on
        if (block instanceof DeskBellBlock) return;
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof ClockBlockEntity be) be.redstone(level.hasNeighborSignal(pos));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction dir) {
        return level.getBlockEntity(pos) instanceof ClockBlockEntity be ? be.signal() : 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TimerDisplayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, Studio.TIMER_DISPLAY_BE, ClockBlockEntity::serverTick);
    }
}
