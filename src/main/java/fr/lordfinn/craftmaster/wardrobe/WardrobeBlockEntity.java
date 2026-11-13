package fr.lordfinn.craftmaster.wardrobe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The outfits of a wardrobe (lower half). A <b>show</b> wardrobe holds every outfit of the game ({@link Catalogue#all()})
 * until an organiser chooses its outfits (any change does: the list becomes its own); everyone wears copies, it never
 * empties, nothing drops when it is broken. A <b>storage</b> wardrobe holds real clothes that swap with what the
 * player wears (they drop when it is broken); it starts empty.
 */
public class WardrobeBlockEntity extends BlockEntity {

    public static final int MAX_OUTFITS = 1024;

    /** Its own outfits (when {@link #custom}). */
    private final List<Outfit> outfits = new ArrayList<>();
    private boolean show;
    /** The outfits were chosen (by an organiser, or it is a storage wardrobe): otherwise, every outfit. */
    private boolean custom;
    /** Set up once (mode) when first placed; a copied wardrobe keeps its own. */
    private boolean configured;
    /** Players with the outfit screen open: the doors stay open while there is one. */
    private final Set<UUID> viewers = new HashSet<>();

    public WardrobeBlockEntity(BlockPos pos, BlockState state) {
        super(Wardrobes.WARDROBE_ENTITY, pos, state);
    }

    /** What it holds now (do not change the list: use the methods below). */
    public List<Outfit> outfits() {
        return custom ? outfits : Catalogue.all();
    }

    public boolean show() {
        return show;
    }

    public boolean custom() {
        return custom;
    }

    public boolean configured() {
        return configured;
    }

    /** First placement: a show wardrobe holds every outfit, a storage one starts empty. */
    public void setup(boolean show) {
        this.show = show;
        custom = !show;
        configured = true;
        setChanged();
    }

    public void setShow(boolean show) {
        if (this.show == show) return;
        // A storage wardrobe starts empty: its clothes are real (they would all drop when it breaks)
        if (!show) {
            outfits.clear();
            custom = true;
        } else if (outfits.isEmpty()) {
            // An empty storage wardrobe made a costume one: every outfit
            custom = false;
        }
        this.show = show;
        configured = true;
        setChanged();
    }

    /** Back to every outfit (its own list is forgotten). Show wardrobes only. */
    public void setAuto() {
        if (!show) return;
        custom = false;
        outfits.clear();
        setChanged();
    }

    /** Before a change: from now on the outfits are its own (a copy of every outfit, the first time). */
    private void ensureCustom() {
        if (custom) return;
        outfits.clear();
        outfits.addAll(Catalogue.all());
        custom = true;
    }

    /** Appends outfits (up to {@link #MAX_OUTFITS}); returns how many were added. */
    public int addAll(List<Outfit> list) {
        ensureCustom();
        int n = 0;
        for (Outfit o : list) {
            if (outfits.size() >= MAX_OUTFITS) break;
            outfits.add(o);
            n++;
        }
        setChanged();
        return n;
    }

    public Outfit get(int index) {
        List<Outfit> list = outfits();
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    public void set(int index, Outfit outfit) {
        if (get(index) == null) return;
        ensureCustom();
        outfits.set(index, outfit);
        setChanged();
    }

    public void remove(int index) {
        if (get(index) == null) return;
        ensureCustom();
        outfits.remove(index);
        setChanged();
    }

    public void move(int index, int delta) {
        int to = index + delta;
        if (get(index) == null || get(to) == null) return;
        ensureCustom();
        outfits.add(to, outfits.remove(index));
        setChanged();
    }

    // ---------------------------------------------------------------- doors

    /** Opens the outfit screen for {@code player} (and the doors). */
    public void open(ServerPlayer player) {
        if (viewers.add(player.getUUID()) && viewers.size() == 1) setDoors(true);
        WardrobeNetwork.sendOpen(player, this);
    }

    public void closedBy(ServerPlayer player) {
        if (viewers.remove(player.getUUID()) && viewers.isEmpty()) setDoors(false);
    }

    private void setDoors(boolean open) {
        Level level = getLevel();
        if (level == null) return;
        BlockState state = getBlockState();
        if (!state.is(Wardrobes.WARDROBE) || state.getValue(WardrobeBlock.OPEN) == open) return;
        level.setBlock(worldPosition, state.setValue(WardrobeBlock.OPEN, open), Block.UPDATE_ALL);
        level.playSound(null, worldPosition.above(), open ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.6f, 0.75f);
    }

    /** Every second: whoever left (or walked away) no longer holds the doors open. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, WardrobeBlockEntity w) {
        if (level.getGameTime() % 20 != 0 || !(level instanceof ServerLevel sl)) return;
        if (!w.viewers.isEmpty()) {
            w.viewers.removeIf(id -> {
                ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
                return p == null || p.level() != level || p.distanceToSqr(Vec3.atCenterOf(pos)) > 100;
            });
            if (w.viewers.isEmpty()) w.setDoors(false);
        } else if (state.getValue(WardrobeBlock.OPEN)) {
            w.setDoors(false);
        }
    }

    /** Within reach of {@code player} (actions sent from the screen). */
    public boolean reachable(ServerPlayer player) {
        return !isRemoved() && player.level() == getLevel() && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) < 100;
    }

    // ---------------------------------------------------------------- saving

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        // A storage wardrobe gives its clothes back; a show wardrobe only holds copies
        if (!show && custom && level != null) {
            for (Outfit o : outfits)
                for (ItemStack s : o.pieces())
                    if (!s.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s.copy());
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        outfits.clear();
        in.read("Outfits", Outfit.CODEC.listOf()).ifPresent(outfits::addAll);
        show = in.getBooleanOr("Show", false);
        configured = in.getBooleanOr("Configured", false);
        custom = in.getBooleanOr("Custom", !show);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (custom) out.store("Outfits", Outfit.CODEC.listOf(), List.copyOf(outfits));
        out.putBoolean("Show", show);
        out.putBoolean("Configured", configured);
        out.putBoolean("Custom", custom);
    }
}
