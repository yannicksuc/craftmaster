package fr.lordfinn.craftmaster.curtain;

import fr.lordfinn.craftmaster.block.CurtainBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Opens and closes whole curtains with an animation: column by column, from the middle outwards when opening
 * (the two halves part) and from the sides inwards when closing. That is the blocks' state (what can be aimed at,
 * what is saved); the players' game draws the fabric sliding continuously over the same time
 * ({@link CurtainNetwork.Slide}, {@link CurtainCloth}).
 */
public final class Curtains {

    /**
     * Ticks between two columns: a 9-block-wide curtain opens in about 2 s, a 15-block one in 3 s, slow enough to
     * read as a stage curtain on the TV picture (at 4 ticks it was over in under a second).
     */
    public static final int STEP_TICKS = 8;
    /** Biggest curtain handled at once. */
    public static final int MAX_BLOCKS = 4096;

    private record Change(boolean open, long due, boolean sound) {}

    private static final Map<ResourceKey<Level>, Map<BlockPos, Change>> PENDING = new HashMap<>();

    private Curtains() {}

    /**
     * Starts opening or closing the curtain containing {@code start}.
     *
     * @param open true to open, false to close, null to switch
     * @return the state the curtain is heading to, or null if there is no curtain there
     */
    public static Boolean animate(ServerLevel level, BlockPos start, Boolean open) {
        BlockState first = level.getBlockState(start);
        if (!(first.getBlock() instanceof CurtainBlock)) return null;
        Direction facing = first.getValue(CurtainBlock.FACING);
        boolean target = open != null ? open : !isHeadingOpen(level, start, first);

        Direction side = facing.getClockWise();
        Set<BlockPos> group = group(level, start);

        // Columns ordered by their distance to the middle of the curtain
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        Map<Integer, Integer> lowestY = new HashMap<>();
        for (BlockPos p : group) {
            int c = column(p, side);
            min = Math.min(min, c);
            max = Math.max(max, c);
            lowestY.merge(c, p.getY(), Math::min);
        }
        double middle = (min + max) / 2.0;
        double far = (max - min) / 2.0;
        long now = level.getGameTime();
        Map<BlockPos, Change> pending = PENDING.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        for (BlockPos p : group) {
            int c = column(p, side);
            double dist = Math.abs(c - middle);
            long step = Math.round(target ? dist : far - dist);
            pending.put(p, new Change(target, now + 1 + step * STEP_TICKS, p.getY() == lowestY.get(c)));
        }
        // The players watching it draw the fabric sliding continuously (client/curtain), in step with the columns
        CurtainNetwork.Slide slide = new CurtainNetwork.Slide(start.immutable(), target,
                CurtainCloth.durationTicks(max - min + 1, STEP_TICKS));
        for (ServerPlayer player : PlayerLookup.tracking(level, start)) ServerPlayNetworking.send(player, slide);
        return target;
    }

    /**
     * The curtain containing {@code start}: the touching curtain blocks (left, right, above, below) facing the same
     * way, in its vertical plane, whatever their colour. Same rule on both sides (the client draws with it).
     */
    public static Set<BlockPos> group(BlockGetter level, BlockPos start) {
        Set<BlockPos> group = new HashSet<>();
        BlockState first = level.getBlockState(start);
        if (!(first.getBlock() instanceof CurtainBlock)) return group;
        Direction facing = first.getValue(CurtainBlock.FACING);
        Direction side = facing.getClockWise();
        List<Direction> dirs = List.of(side, side.getOpposite(), Direction.UP, Direction.DOWN);
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        todo.add(start.immutable());
        group.add(start.immutable());
        while (!todo.isEmpty() && group.size() < MAX_BLOCKS) {
            BlockPos p = todo.poll();
            for (Direction d : dirs) {
                BlockPos n = p.relative(d);
                if (group.contains(n)) continue;
                BlockState s = level.getBlockState(n);
                if (s.getBlock() instanceof CurtainBlock && s.getValue(CurtainBlock.FACING) == facing) {
                    group.add(n);
                    todo.add(n);
                }
            }
        }
        return group;
    }

    /** Registers the slide packet (server → clients). */
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(CurtainNetwork.Slide.TYPE, CurtainNetwork.Slide.CODEC);
    }

    /** Where the block is going: its pending change if it is moving, else its current state. */
    public static boolean isHeadingOpen(ServerLevel level, BlockPos pos, BlockState state) {
        Map<BlockPos, Change> pending = PENDING.get(level.dimension());
        Change c = pending == null ? null : pending.get(pos);
        return c != null ? c.open() : state.getValue(CurtainBlock.OPEN);
    }

    public static int column(BlockPos p, Direction side) {
        return side.getAxis() == Direction.Axis.X ? p.getX() : p.getZ();
    }

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        Iterator<Map.Entry<ResourceKey<Level>, Map<BlockPos, Change>>> levels = PENDING.entrySet().iterator();
        while (levels.hasNext()) {
            Map.Entry<ResourceKey<Level>, Map<BlockPos, Change>> e = levels.next();
            ServerLevel level = server.getLevel(e.getKey());
            if (level == null) {
                levels.remove();
                continue;
            }
            long now = level.getGameTime();
            Iterator<Map.Entry<BlockPos, Change>> it = e.getValue().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockPos, Change> pc = it.next();
                Change c = pc.getValue();
                if (c.due() > now) continue;
                it.remove();
                BlockPos pos = pc.getKey();
                BlockState s = level.getBlockState(pos);
                if (!(s.getBlock() instanceof CurtainBlock) || s.getValue(CurtainBlock.OPEN) == c.open()) continue;
                // No neighbour update: the curtain's own redstone wiring must not react to the animation
                level.setBlock(pos, s.setValue(CurtainBlock.OPEN, c.open()), Block.UPDATE_CLIENTS);
                if (c.sound())
                    level.playSound(null, pos, SoundEvents.WOOL_HIT, SoundSource.BLOCKS, 0.7f, c.open() ? 1.2f : 0.9f);
            }
            if (e.getValue().isEmpty()) levels.remove();
        }
    }

    public static void clear() {
        PENDING.clear();
    }
}
