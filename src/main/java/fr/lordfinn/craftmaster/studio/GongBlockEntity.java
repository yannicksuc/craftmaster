package fr.lordfinn.craftmaster.studio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The gong's disc, drawn by the client: when it was last struck, for its swing. Nothing is saved. */
public class GongBlockEntity extends BlockEntity {

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
}
