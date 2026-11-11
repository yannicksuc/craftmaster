package fr.lordfinn.craftmaster.studio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Brass stanchion of the audience area: a red velvet rope hangs from it to the next stanchion in line (east or
 * south, up to {@link #MAX_GAP} blocks away, same height). The rope is drawn by the client; it has no collision.
 */
public class StanchionBlock extends BaseEntityBlock {

    // Minecraft 26.2 blocks must give a codec (26.3 dropped the requirement)
    //? if <26.3 {
    /*@Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }
    *///?}

    public static final int MAX_GAP = 4;
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 15, 11);
    private static final VoxelShape COLLISION = Block.box(6, 0, 6, 10, 24, 10);

    public StanchionBlock(Properties props) {
        super(props);
    }

    /** The stanchion the rope from {@code pos} goes to along +x ({@code east}) or +z, or null. */
    public static BlockPos ropeTarget(BlockGetter level, BlockPos pos, boolean east) {
        for (int i = 1; i <= MAX_GAP; i++) {
            BlockPos p = east ? pos.east(i) : pos.south(i);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof StanchionBlock) return p;
            // A wall between the two stops the rope
            if (!s.getCollisionShape(level, p).isEmpty() && !(s.getBlock() instanceof StanchionBlock)) return null;
        }
        return null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return COLLISION;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StanchionBlockEntity(pos, state);
    }
}
