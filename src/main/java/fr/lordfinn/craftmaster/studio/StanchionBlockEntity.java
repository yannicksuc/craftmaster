package fr.lordfinn.craftmaster.studio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** No data: only there so the client draws the stanchion's ropes (see client/studio/StanchionRenderer). */
public class StanchionBlockEntity extends BlockEntity {

    public StanchionBlockEntity(BlockPos pos, BlockState state) {
        super(Studio.STANCHION_BE, pos, state);
    }
}
