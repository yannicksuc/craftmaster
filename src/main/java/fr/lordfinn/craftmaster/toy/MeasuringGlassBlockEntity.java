package fr.lordfinn.craftmaster.toy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The water of a {@link MeasuringGlassBlock}, in millilitres, and the organisers' line. */
public class MeasuringGlassBlockEntity extends BlockEntity {

    private final GlassLevel glass;

    public MeasuringGlassBlockEntity(BlockPos pos, BlockState state) {
        super(Toys.GLASS_ENTITY, pos, state);
        glass = new GlassLevel(state.getBlock() instanceof MeasuringGlassBlock b ? b.size().capacity : 1000);
    }

    public GlassLevel level() {
        return glass;
    }

    /** After a change: the block shows the new level, comparators see it. */
    public void changed() {
        setChanged();
        if (level == null) return;
        BlockState st = getBlockState();
        if (st.hasProperty(MeasuringGlassBlock.FILL) && st.getValue(MeasuringGlassBlock.FILL) != glass.stage())
            level.setBlock(worldPosition, st.setValue(MeasuringGlassBlock.FILL, glass.stage()), 3);
        level.updateNeighbourForOutputSignal(worldPosition, st.getBlock());
    }

    /** Water running into it from above (a stream, a dispenser's water, a leaking pipe) fills it a little. */
    void serverTick(ServerLevel level, BlockPos pos) {
        if ((level.getGameTime() + pos.asLong()) % 10 != 0 || glass.full()) return;
        int in = 0;
        if (level.getFluidState(pos.above()).is(FluidTags.WATER)) in = GlassLevel.TRICKLE;
        else if (level.isRainingAt(pos.above())) in = 5;
        if (in > 0 && glass.pour(in) > 0) changed();
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        glass.set(in.getIntOr("Ml", 0));
        glass.setLine(in.getIntOr("Line", -1));
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Ml", glass.ml());
        out.putInt("Line", glass.line());
    }
}
