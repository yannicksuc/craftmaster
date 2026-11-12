package fr.lordfinn.craftmaster.toy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a target container caught: the objects (one slot each, so the tally counter and hoppers see them) and who
 * threw each one.
 */
public class CatchTargetBlockEntity extends BlockEntity implements Container {

    public static final int SIZE = 54;

    private final List<ItemStack> items = new ArrayList<>();
    private final List<String> throwers = new ArrayList<>();

    public CatchTargetBlockEntity(BlockPos pos, BlockState state) {
        super(Toys.TARGET_ENTITY, pos, state);
    }

    public boolean add(ItemStack stack, String who) {
        if (items.size() >= SIZE) return false;
        items.add(stack);
        throwers.add(who == null ? "" : who);
        setChanged();
        return true;
    }

    public int count() {
        return items.size();
    }

    /** "Ensuri 3, Cyllu 1" */
    public String summary() {
        Map<String, Integer> by = new LinkedHashMap<>();
        for (String t : throwers) by.merge(t.isEmpty() ? "?" : t, 1, Integer::sum);
        List<String> parts = new ArrayList<>();
        by.forEach((k, v) -> parts.add(k + " " + v));
        return String.join(", ", parts);
    }

    public List<ItemStack> takeAll() {
        List<ItemStack> out = new ArrayList<>(items);
        items.clear();
        throwers.clear();
        setChanged();
        return out;
    }

    // ---------------------------------------------------------------- Container

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        if (slot < 0 || slot >= items.size()) return ItemStack.EMPTY;
        ItemStack s = items.get(slot);
        ItemStack out = s.split(count);
        if (s.isEmpty()) {
            items.remove(slot);
            throwers.remove(slot);
        }
        setChanged();
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= items.size()) return ItemStack.EMPTY;
        throwers.remove(slot);
        return items.remove(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < items.size()) {
            if (stack.isEmpty()) {
                items.remove(slot);
                throwers.remove(slot);
            } else items.set(slot, stack);
        } else if (!stack.isEmpty() && items.size() < SIZE) {
            items.add(stack);
            throwers.add("");
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return false;
    }

    @Override
    public void clearContent() {
        items.clear();
        throwers.clear();
        setChanged();
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items.clear();
        throwers.clear();
        items.addAll(in.read("Items", ItemStack.CODEC.listOf()).orElse(List.of()));
        List<String> t = in.read("Throwers", com.mojang.serialization.Codec.STRING.listOf()).orElse(List.of());
        for (int i = 0; i < items.size(); i++) throwers.add(i < t.size() ? t.get(i) : "");
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("Items", ItemStack.CODEC.listOf(), List.copyOf(items));
        out.store("Throwers", com.mojang.serialization.Codec.STRING.listOf(), List.copyOf(throwers));
    }
}
