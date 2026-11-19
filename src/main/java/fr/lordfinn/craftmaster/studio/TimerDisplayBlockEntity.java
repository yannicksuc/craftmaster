package fr.lordfinn.craftmaster.studio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The giant timer: a {@link ClockBlockEntity} on a big panel ({@code size} blocks high, 2.5 × {@code size} wide).
 * By default it shows the show's round timer in gold, the last ten seconds in red; organisers change it like a
 * tablet (mode, duration, colours, text, group, triggers).
 */
public class TimerDisplayBlockEntity extends ClockBlockEntity {

    public static final int MAX_SIZE = 6, DEFAULT_SIZE = 2;

    public TimerDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(Studio.TIMER_DISPLAY_BE, pos, state);
    }

    @Override
    protected TabletClock.Settings defaults() {
        TabletClock.Settings s = new TabletClock.Settings();
        s.mode = TabletClock.Mode.SHOW;
        s.color = TabletClock.DigitColor.GOLD;
        s.warnColor = TabletClock.DigitColor.RED;
        s.warnSeconds = 10;
        s.size = DEFAULT_SIZE;
        return s;
    }

    @Override
    public int maxSize() {
        return MAX_SIZE;
    }

    @Override
    public boolean giant() {
        return true;
    }

    public int size() {
        return settings.size;
    }

    public void setSize(int size) {
        TabletClock.Settings s = settings.copy();
        s.size = Math.max(1, Math.min(MAX_SIZE, size));
        apply(s);
    }

    /** Saved before it had settings: the show's timer at its old size. */
    @Override
    protected TabletClock.Settings loadSettings(ValueInput in) {
        String json = in.getStringOr("Settings", "");
        if (!json.isBlank()) return read(json);
        TabletClock.Settings s = defaults();
        s.size = Math.max(1, Math.min(MAX_SIZE, in.getIntOr("Size", DEFAULT_SIZE)));
        return s;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        // Also the old field (a jar from before still reads its size)
        out.putInt("Size", settings.size);
    }
}
