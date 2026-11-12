package fr.lordfinn.craftmaster.studio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The name on a contestant's desk plate ("" = blank plate), and its drawer (9 slots, not sent to the clients). */
public class DeskBlockEntity extends SyncedBlockEntity {

    public static final int MAX_NAME = 24;

    private String name = "";
    private final StudioStorage storage = new StudioStorage(this, 9);

    public DeskBlockEntity(BlockPos pos, BlockState state) {
        super(Studio.DESK_BE, pos, state);
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        String n = name == null ? "" : name.strip();
        this.name = n.length() > MAX_NAME ? n.substring(0, MAX_NAME) : n;
        sync();
    }

    public StudioStorage storage() {
        return storage;
    }

    /** The desk is broken: what was in its drawer falls on the floor. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) storage.dropAll(level, pos);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        name = in.getStringOr("Name", "");
        storage.load(in);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("Name", name);
        storage.save(out);
    }

    /** The clients get the name, not what is in the drawer. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.remove("Storage");
        return tag;
    }
}
