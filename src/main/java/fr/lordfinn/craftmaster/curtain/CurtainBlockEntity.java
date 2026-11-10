package fr.lordfinn.craftmaster.curtain;

import fr.lordfinn.craftmaster.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds no data: it only lets the client draw the curtain's fabric (client/curtain/CurtainRenderer) instead of a
 * fixed block model, so the fabric can slide smoothly across blocks. Blocks placed before it existed get one on
 * the client when their chunk loads.
 */
public class CurtainBlockEntity extends BlockEntity {

    public CurtainBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.CURTAIN_BLOCK_ENTITY, pos, state);
    }
}
