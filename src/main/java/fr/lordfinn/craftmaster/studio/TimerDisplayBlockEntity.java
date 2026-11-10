package fr.lordfinn.craftmaster.studio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The giant timer's size: the panel is {@code size} blocks high and 2.5 × {@code size} wide. */
public class TimerDisplayBlockEntity extends SyncedBlockEntity {

    public static final int MAX_SIZE = 6, DEFAULT_SIZE = 2;

    private int size = DEFAULT_SIZE;

    public TimerDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(Studio.TIMER_DISPLAY_BE, pos, state);
    }

    public int size() {
        return size;
    }

    public void setSize(int size) {
        this.size = Math.max(1, Math.min(MAX_SIZE, size));
        sync();
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        size = Math.max(1, Math.min(MAX_SIZE, in.getIntOr("Size", DEFAULT_SIZE)));
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Size", size);
    }
}
