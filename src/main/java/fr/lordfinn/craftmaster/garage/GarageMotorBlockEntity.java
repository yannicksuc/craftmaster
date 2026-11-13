package fr.lordfinn.craftmaster.garage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A garage door's motor: the door's panel (the builder's blocks, kept here while the door is open), where it is,
 * its movement, its name and speed, and the block it looks like.
 * <p>
 * While the door moves, its doorway holds invisible barriers, freed row by row as the panel rises past them (and put
 * back as it comes down, after pushing out whoever stands there); the clients draw the panel sliding
 * ({@code client/garage/GarageMotorRenderer}). Closed, the door is the builder's real blocks; open, its doorway is
 * empty.
 */
public class GarageMotorBlockEntity extends BlockEntity {

    /** A signal held at least this long (a lever, not a button) closes the door again when it goes (ticks). */
    public static final int HELD_SIGNAL_TICKS = 40;

    /** Server-side motors currently loaded (the commands look the named doors up among them first). */
    private static final Set<GarageMotorBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    private String name = "";
    private int ticksPerBlock = GarageMotion.DEFAULT_TICKS_PER_BLOCK;
    /** The panel as a box relative to the motor (x0 y0 z0 x1 y1 z1), or null: found from the block under the motor. */
    private int[] box;
    /** Where the door is, or where it is going while it moves. */
    private boolean open;
    private GaragePanel panel = GaragePanel.EMPTY;
    private BlockState camouflage;

    private boolean moving;
    private long moveStart;
    private double moveFrom, moveTo;
    private int moveDuration;

    private boolean powered;
    private long risingTick;
    private boolean risingTarget;

    public GarageMotorBlockEntity(BlockPos pos, BlockState state) {
        super(GarageDoors.MOTOR_BE, pos, state);
    }

    public static List<GarageMotorBlockEntity> loaded() {
        synchronized (LOADED) {
            return LOADED.stream().filter(b -> !b.isRemoved() && b.level != null).toList();
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (!level.isClientSide()) synchronized (LOADED) { LOADED.add(this); }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        synchronized (LOADED) { LOADED.remove(this); }
    }

    /** The motor is broken or replaced: the door is put back closed, so the builder's blocks are never lost. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server && (open || moving)) {
            moving = false;
            open = false;
            restore(server);
        }
    }

    // ---------------------------------------------------------------- settings

    public String name() { return name; }
    public boolean isOpen() { return open; }
    public boolean isMoving() { return moving; }
    public GaragePanel panel() { return panel; }
    public BlockState camouflage() { return camouflage; }
    public int ticksPerBlock() { return ticksPerBlock; }
    public int[] box() { return box == null ? null : box.clone(); }

    public Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(GarageMotorBlock.FACING) ? s.getValue(GarageMotorBlock.FACING) : Direction.NORTH;
    }

    void setName(String name) {
        this.name = name == null ? "" : name;
        setChanged();
    }

    public void setTicksPerBlock(int ticks) {
        ticksPerBlock = Math.clamp(ticks, GarageMotion.MIN_TICKS_PER_BLOCK, GarageMotion.MAX_TICKS_PER_BLOCK);
        setChanged();
    }

    /** Sets the panel's box (relative to the motor), or null to find the panel automatically. */
    public void setBox(int[] box) {
        this.box = box == null ? null : box.clone();
        if (!open && !moving) panel = findPanel();
        setChanged();
    }

    public void setCamouflage(BlockState state) {
        camouflage = state;
        sync();
    }

    /** The panel as the world is now (only meaningful while the door is closed). */
    public GaragePanel findPanel() {
        if (level == null) return GaragePanel.EMPTY;
        return box != null ? GaragePanel.inBox(level, worldPosition, box, facing()) : GaragePanel.detect(level, worldPosition, facing());
    }

    // ---------------------------------------------------------------- moving

    /** The door's opening now (blocks risen, 0 = closed), with the client's partial tick if any. */
    public double opening(double time) {
        if (moving) return GarageMotion.opening(moveFrom, moveTo, moveStart, moveDuration, time);
        return open ? panel.height() : 0;
    }

    /**
     * Opens or closes the door. Moving again while it moves turns it back from where it is.
     *
     * @param target true to open, false to close, null to switch
     * @return the state the door is heading to, or null if there is no door under the motor
     */
    public Boolean move(Boolean target) {
        if (!(level instanceof ServerLevel server)) return null;
        boolean to = target != null ? target : !open;
        if (!moving && open == to) return to;
        long now = server.getGameTime();
        double s = opening(now);
        if (!moving && !open) {
            // Opening a closed door: its panel is what is built there now
            GaragePanel found = findPanel();
            if (found.isEmpty()) return null;
            panel = found;
            for (GaragePanel.Cell c : panel.cells())
                server.setBlock(c.pos(worldPosition), Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS);
            s = 0;
        }
        open = to;
        moving = true;
        moveFrom = s;
        moveTo = to ? panel.height() : 0;
        moveStart = now;
        moveDuration = GarageMotion.duration(moveFrom, moveTo, ticksPerBlock);
        server.playSound(null, worldPosition, to ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
                SoundSource.BLOCKS, 0.9f, 0.55f);
        sync();
        return to;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GarageMotorBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        if (be.moving) be.step(server, now);
        if ((now & 1) == 0) be.pollRedstone(server, now);
        // While closed, the panel follows what is built (the redstone of a door not opened yet is read around it)
        if (!be.moving && !be.open && Math.floorMod(now + pos.hashCode(), 40) == 0) {
            GaragePanel found = be.findPanel();
            if (!found.cells().equals(be.panel.cells())) {
                be.panel = found;
                be.setChanged();
            }
        }
    }

    private void step(ServerLevel server, long now) {
        double s = opening(now);
        boolean closing = !open;
        for (GaragePanel.Cell c : panel.cells()) {
            BlockPos p = c.pos(worldPosition);
            int row = panel.row(c), bottom = panel.columnBottom(c);
            boolean blocks = GarageMotion.blocks(row, bottom, s);
            BlockState here = server.getBlockState(p);
            if (closing) {
                // Whoever stands where the panel is coming down is pushed out of the doorway first
                double from = Math.max(row, bottom + s);
                if (from < row + 1) push(server, p, from - row);
                if (blocks && here.isAir()) server.setBlock(p, Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS);
            } else if (!blocks && here.is(Blocks.BARRIER)) {
                server.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        if ((now - moveStart) % 6 == 0 && now < moveStart + moveDuration)
            server.playSound(null, worldPosition, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.35f, 0.7f);
        if (now >= moveStart + moveDuration) {
            moving = false;
            if (open) {
                for (GaragePanel.Cell c : panel.cells()) {
                    BlockPos p = c.pos(worldPosition);
                    if (server.getBlockState(p).is(Blocks.BARRIER)) server.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            } else {
                restore(server);
                server.playSound(null, worldPosition.offset(0, panel.bottomDy(), 0), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 0.6f);
            }
            sync();
        }
    }

    /** Puts the builder's blocks back where the doorway is empty (or holds our barriers). */
    private void restore(ServerLevel server) {
        for (GaragePanel.Cell c : panel.cells()) {
            BlockPos p = c.pos(worldPosition);
            BlockState here = server.getBlockState(p);
            if (here.isAir() || here.is(Blocks.BARRIER)) {
                push(server, p, 0);
                server.setBlock(p, c.state(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * Pushes the living things standing in a cell of the doorway (from {@code fromY} in it to its top) out of the
     * door's plane, to the side they are on (in front of the motor if right in the middle).
     */
    private void push(ServerLevel server, BlockPos cell, double fromY) {
        AABB box = new AABB(cell.getX() + 0.02, cell.getY() + fromY, cell.getZ() + 0.02,
                cell.getX() + 0.98, cell.getY() + 1, cell.getZ() + 0.98);
        Direction.Axis axis = panel.normal();
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, e -> !e.isSpectator() && e.isAlive())) {
            double centre = (axis == Direction.Axis.X ? cell.getX() : cell.getZ()) + 0.5;
            double at = axis == Direction.Axis.X ? e.getX() : e.getZ();
            int side = Double.compare(at, centre);
            if (side == 0) side = facing().getAxis() == axis ? facing().getAxisDirection().getStep() : 1;
            double to = centre + side * (0.5 + e.getBbWidth() / 2 + 0.02);
            if (axis == Direction.Axis.X) e.teleportTo(to, e.getY(), e.getZ());
            else e.teleportTo(e.getX(), e.getY(), to);
        }
    }

    /**
     * A redstone signal on the motor or around the doorway: a pulse (button) opens or closes the door; a signal
     * held (lever) keeps it the way it went, and losing it sends the door back.
     */
    private void pollRedstone(ServerLevel server, long now) {
        boolean p = server.hasNeighborSignal(worldPosition);
        if (!p) {
            for (GaragePanel.Cell c : panel.cells()) {
                if (server.hasNeighborSignal(c.pos(worldPosition))) {
                    p = true;
                    break;
                }
            }
        }
        if (p == powered) return;
        powered = p;
        setChanged();
        if (p) {
            risingTick = now;
            risingTarget = !open;
            move(risingTarget);
        } else if (now - risingTick >= HELD_SIGNAL_TICKS && open == risingTarget) {
            move(!risingTarget);
        }
    }

    // ---------------------------------------------------------------- save and sync

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        name = in.getStringOr("Name", "");
        ticksPerBlock = in.getIntOr("TicksPerBlock", GarageMotion.DEFAULT_TICKS_PER_BLOCK);
        box = in.getIntArray("Box").filter(b -> b.length == 6).orElse(null);
        open = in.getBooleanOr("Open", false);
        powered = in.getBooleanOr("Powered", false);
        camouflage = in.read("Camouflage", BlockState.CODEC).orElse(null);
        moving = in.getBooleanOr("Moving", false);
        moveStart = in.getLongOr("MoveStart", 0);
        moveFrom = in.getDoubleOr("MoveFrom", 0);
        moveTo = in.getDoubleOr("MoveTo", 0);
        moveDuration = in.getIntOr("MoveDuration", 0);
        List<GaragePanel.Cell> cells = new ArrayList<>();
        for (ValueInput c : in.childrenListOrEmpty("Panel")) {
            BlockState s = c.read("State", BlockState.CODEC).orElse(null);
            if (s != null) cells.add(new GaragePanel.Cell(c.getIntOr("X", 0), c.getIntOr("Y", 0), c.getIntOr("Z", 0), s));
        }
        Direction.Axis normal = "x".equals(in.getStringOr("Normal", "z")) ? Direction.Axis.X : Direction.Axis.Z;
        panel = new GaragePanel(cells, normal);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("Name", name);
        out.putInt("TicksPerBlock", ticksPerBlock);
        if (box != null) out.putIntArray("Box", box);
        out.putBoolean("Open", open);
        out.putBoolean("Powered", powered);
        if (camouflage != null) out.store("Camouflage", BlockState.CODEC, camouflage);
        out.putBoolean("Moving", moving);
        out.putLong("MoveStart", moveStart);
        out.putDouble("MoveFrom", moveFrom);
        out.putDouble("MoveTo", moveTo);
        out.putInt("MoveDuration", moveDuration);
        out.putString("Normal", panel.normal() == Direction.Axis.X ? "x" : "z");
        ValueOutput.ValueOutputList list = out.childrenList("Panel");
        for (GaragePanel.Cell c : panel.cells()) {
            ValueOutput o = list.addChild();
            o.putInt("X", c.dx());
            o.putInt("Y", c.dy());
            o.putInt("Z", c.dz());
            o.store("State", BlockState.CODEC, c.state());
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
