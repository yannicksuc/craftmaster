package fr.lordfinn.craftmaster.studio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The gong's disc, drawn by the client: when it was last struck, for its swing. Nothing is saved. */
public class GongBlockEntity extends BlockEntity {

    /** Length of the swing after a strike (ticks). */
    public static final int SWING_TICKS = 60;

    private long struckAt = Long.MIN_VALUE / 2;

    public GongBlockEntity(BlockPos pos, BlockState state) {
        super(Studio.GONG_BE, pos, state);
    }

    void struck(long gameTime) {
        struckAt = gameTime;
    }

    public long struckAt() {
        return struckAt;
    }

    /**
     * Swing angle (degrees) {@code ticks} after a strike: a damped pendulum, pushed away from the striker first.
     * Pure, unit tested.
     */
    public static float swing(double ticks) {
        if (ticks < 0 || ticks > SWING_TICKS) return 0;
        double t = ticks / 20.0;
        return (float) (-14 * Math.exp(-1.6 * t) * Math.sin(2 * Math.PI * 1.1 * t));
    }
}
