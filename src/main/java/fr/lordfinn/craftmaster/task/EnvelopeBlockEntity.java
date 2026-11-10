package fr.lordfinn.craftmaster.task;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The envelope lying on a table: keeps the envelope's data (round or free text, addressee) until picked up. */
public class EnvelopeBlockEntity extends BlockEntity {

    private TaskComponents.Envelope envelope = TaskComponents.Envelope.BLANK;

    public EnvelopeBlockEntity(BlockPos pos, BlockState state) {
        super(TaskContent.ENVELOPE_BLOCK_ENTITY, pos, state);
    }

    public TaskComponents.Envelope envelope() {
        return envelope;
    }

    public void setEnvelope(TaskComponents.Envelope e) {
        envelope = e;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("envelope", TaskComponents.Envelope.CODEC, envelope);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        envelope = in.read("envelope", TaskComponents.Envelope.CODEC).orElse(TaskComponents.Envelope.BLANK);
    }

    // Picked with the middle click / placed from an item: the envelope travels as the item component
    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        TaskComponents.Envelope e = components.get(TaskComponents.ENVELOPE);
        if (e != null) envelope = e;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(TaskComponents.ENVELOPE, envelope);
    }
}
