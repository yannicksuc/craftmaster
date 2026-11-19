package fr.lordfinn.craftmaster.studio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A timer tablet: a {@link ClockBlockEntity} in a picture frame, standing, on an easel, flat or on a wall. */
public class TimerTabletBlockEntity extends ClockBlockEntity {

    public TimerTabletBlockEntity(BlockPos pos, BlockState state) {
        super(Studio.TIMER_TABLET_BE, pos, state);
    }

    @Override
    public int maxSize() {
        return TabletClock.Settings.MAX_SIZE;
    }

    /** A new pose moves the block (its shape follows). */
    @Override
    protected void applied(TabletClock.Settings before) {
        if (settings.pose == before.pose || level == null) return;
        BlockState st = getBlockState();
        if (st.getBlock() instanceof TimerTabletBlock) level.setBlock(worldPosition, st.setValue(TimerTabletBlock.POSE, settings.pose), 3);
    }
}
