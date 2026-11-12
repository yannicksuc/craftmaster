package fr.lordfinn.craftmaster.studio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The four prizes lying on a prize table (slot = quarter of the top: 0 north-west, 1 north-east, 2 south-west,
 * 3 south-east), and the shelf hidden under its drape (27 slots, not sent to the clients).
 */
public class PrizeTableBlockEntity extends SyncedBlockEntity {

    public static final int SLOTS = 4;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final StudioStorage storage = new StudioStorage(this, 27);

    public PrizeTableBlockEntity(BlockPos pos, BlockState state) {
        super(Studio.PRIZE_TABLE_BE, pos, state);
    }

    public NonNullList<ItemStack> items() {
        return items;
    }

    public StudioStorage storage() {
        return storage;
    }

    /** Centre of a slot on the top, in block coordinates (x, z). */
    public static double[] slotOffset(int slot) {
        return new double[]{(slot % 2 == 0) ? 0.27 : 0.73, (slot < 2) ? 0.27 : 0.73};
    }

    /** The wanted slot if it is free, else the first free one, else -1. */
    int freeSlot(int wanted) {
        if (items.get(wanted).isEmpty()) return wanted;
        for (int i = 0; i < SLOTS; i++) if (items.get(i).isEmpty()) return i;
        return -1;
    }

    /** The wanted slot if it holds something, else the last filled one, else -1. */
    int takenSlot(int wanted) {
        if (!items.get(wanted).isEmpty()) return wanted;
        for (int i = SLOTS - 1; i >= 0; i--) if (!items.get(i).isEmpty()) return i;
        return -1;
    }

    void put(int slot, ItemStack stack) {
        items.set(slot, stack);
        sync();
    }

    ItemStack take(int slot) {
        ItemStack s = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        sync();
        return s;
    }

    /** The table is broken: its prizes and its shelf fall on the floor. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level == null) return;
        Containers.dropContents(level, pos, items);
        storage.dropAll(level, pos);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items.clear();
        ContainerHelper.loadAllItems(in, items);
        storage.load(in);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items, true);
        storage.save(out);
    }

    /** The clients get the prizes on show, not the hidden shelf. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.remove("Storage");
        return tag;
    }
}
