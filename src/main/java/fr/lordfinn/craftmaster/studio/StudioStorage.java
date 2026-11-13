package fr.lordfinn.craftmaster.studio;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The storage hidden in a piece of the set (the drawer of a contestant's desk, the shelf under the prize table's
 * drape): a plain inventory, saved with its block entity under "Storage", never sent to the clients (what is hidden
 * stays hidden), dropped when the block is broken.
 */
public final class StudioStorage extends SimpleContainer {

    private final BlockEntity owner;

    public StudioStorage(BlockEntity owner, int size) {
        super(size);
        this.owner = owner;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        owner.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(owner, player);
    }

    /** Opens it for the player (9 slots: a dispenser-like grid, else chest rows), with a drawer sound. */
    public void open(Player player, Component title) {
        Level level = owner.getLevel();
        if (level == null || level.isClientSide()) return;
        int size = getContainerSize();
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> size == 9 ? new DispenserMenu(id, inv, this) : ChestMenu.threeRows(id, inv, this), title));
        BlockPos pos = owner.getBlockPos();
        level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.6f, 1.2f);
    }

    public void dropAll(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, this);
    }

    public void save(ValueOutput out) {
        ContainerHelper.saveAllItems(out.child("Storage"), getItems(), true);
    }

    public void load(ValueInput in) {
        getItems().clear();
        in.child("Storage").ifPresent(c -> ContainerHelper.loadAllItems(c, getItems()));
    }
}
