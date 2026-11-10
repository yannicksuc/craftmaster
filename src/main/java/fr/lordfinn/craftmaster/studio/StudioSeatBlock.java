package fr.lordfinn.craftmaster.studio;

import fr.lordfinn.craftmaster.block.ChairBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * A seat of the studio set (the master's throne, the assistant's armchair): sits like a {@link ChairBlock}, with its
 * own outline and seat height. The model may reach above the block (the throne's backrest), the outline stops at
 * {@code shapes}.
 */
public class StudioSeatBlock extends ChairBlock {

    private final double seatTop;
    private final Map<Direction, VoxelShape> shapes;

    public StudioSeatBlock(Properties props, double seatTopPixels, Map<Direction, VoxelShape> shapes) {
        super(props);
        this.seatTop = seatTopPixels / 16.0;
        this.shapes = shapes;
    }

    @Override
    public double seatTop() {
        return seatTop;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapes.get(state.getValue(FACING));
    }
}
