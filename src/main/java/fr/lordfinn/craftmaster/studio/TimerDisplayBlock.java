package fr.lordfinn.craftmaster.studio;

import fr.lordfinn.craftmaster.block.Shapes4;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Giant timer: a big panel showing the current round and its timer in seven-segment digits (drawn by the client,
 * the panel grows beyond the block: {@link TimerDisplayBlockEntity#size()}). Hung on a wall it faces out of it.
 * Right click: bigger, sneak + right click: smaller.
 */
public class TimerDisplayBlock extends FacingEntityBlock {

    public TimerDisplayBlock(Properties props) {
        // Facing north: a bracket against the wall on the south side
        super(props, Shapes4.of(new double[]{2, 0, 13, 14, 12, 16}), true);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayBuild() || !(level.getBlockEntity(pos) instanceof TimerDisplayBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        int size = be.size() + (player.isShiftKeyDown() ? -1 : 1);
        if (size > TimerDisplayBlockEntity.MAX_SIZE) size = 1;
        if (size < 1) size = TimerDisplayBlockEntity.MAX_SIZE;
        be.setSize(size);
        player.sendOverlayMessage(Component.translatable("message.craftmaster.timer_size", size).withStyle(ChatFormatting.GOLD));
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, 1.2f);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TimerDisplayBlockEntity(pos, state);
    }
}
