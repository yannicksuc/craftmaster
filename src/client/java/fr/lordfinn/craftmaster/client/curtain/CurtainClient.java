package fr.lordfinn.craftmaster.client.curtain;

import fr.lordfinn.craftmaster.block.CurtainBlock;
import fr.lordfinn.craftmaster.client.portrait.PortraitClock;
import fr.lordfinn.craftmaster.curtain.CurtainBlockEntity;
import fr.lordfinn.craftmaster.curtain.CurtainCloth;
import fr.lordfinn.craftmaster.curtain.CurtainNetwork;
import fr.lordfinn.craftmaster.curtain.Curtains;
import fr.lordfinn.craftmaster.registry.ModContent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Client side of the curtains: which curtain each block belongs to ({@link Layout}, found like the server does)
 * and how far its fabric has slid ({@link #openness}), for {@link CurtainRenderer}.
 */
public final class CurtainClient {

    private CurtainClient() {}

    /** The shape of a whole curtain, as the renderer of each of its blocks needs it. */
    public record Layout(Direction facing, Direction side, int minColumn, int maxColumn, int topY, int bottomY) {
        int columns() {
            return maxColumn - minColumn + 1;
        }

        /** 0 at the top edge of the curtain (world y {@code topY + 1}), 1 at its bottom edge: the lower fabric trails behind. */
        double depth(double y) {
            return Math.max(0, Math.min(1, (topY + 1 - y) / (topY + 1.0 - bottomY)));
        }
    }

    /** A slide in progress (or just finished, until the blocks' state agrees), shared by the curtain's blocks. */
    private static final class Motion {
        final double from, to, start, duration;

        Motion(double from, double to, double start, double duration) {
            this.from = from;
            this.to = to;
            this.start = start;
            this.duration = duration;
        }

        double at(double time) {
            return CurtainCloth.progress(from, to, time - start, duration);
        }

        double end() {
            return start + duration;
        }
    }

    /** Fabric still swinging a little after it stopped: ticks, and how far (share of the opening) it swings. */
    private static final double SETTLE_TICKS = 30, SETTLE_SWING = 0.035;
    /** The bottom of a tall curtain follows the top this many ticks later. */
    private static final double LAG_TICKS = 3;

    private static final Map<BlockPos, Layout> LAYOUTS = new HashMap<>();
    private static final Map<BlockPos, Motion> MOTIONS = new HashMap<>();

    public static void init() {
        BlockEntityRendererRegistry.register(ModContent.CURTAIN_BLOCK_ENTITY, CurtainRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(CurtainNetwork.Slide.TYPE, (payload, ctx) -> {
            if (ctx.client().level != null) slide(ctx.client().level, payload);
        });
        // Curtains placed before the block entity existed have none: give them one so they are drawn
        ClientChunkEvents.CHUNK_LOAD.register(CurtainClient::addMissingBlockEntities);
        // Blocks added or removed change the curtains' shapes: found again when next drawn
        ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((be, level) -> {
            if (be instanceof CurtainBlockEntity) LAYOUTS.clear();
        });
        ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((be, level) -> {
            if (be instanceof CurtainBlockEntity) LAYOUTS.clear();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LAYOUTS.clear();
            MOTIONS.clear();
        });
    }

    private static void addMissingBlockEntities(ClientLevel level, LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(s -> s.getBlock() instanceof CurtainBlock)) continue;
            int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
            BlockPos added = null;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        if (!(section.getBlockState(x, y, z).getBlock() instanceof CurtainBlock)) continue;
                        BlockPos pos = chunk.getPos().getBlockAt(x, baseY + y, z);
                        if (chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK) == null) {
                            chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
                            added = pos;
                        }
                    }
            // Rebuild the section so its new block entities get drawn
            if (added != null) {
                BlockState s = level.getBlockState(added);
                level.sendBlockUpdated(added, s, s, 0);
            }
        }
    }

    /** The curtain containing {@code pos} (found once, then shared by all its blocks until the curtains change). */
    static Layout layout(Level level, BlockPos pos, BlockState state) {
        Layout l = LAYOUTS.get(pos);
        if (l != null && l.facing() == state.getValue(CurtainBlock.FACING)) return l;
        Set<BlockPos> group = Curtains.group(level, pos);
        Direction facing = state.getValue(CurtainBlock.FACING);
        Direction side = facing.getClockWise();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, top = Integer.MIN_VALUE, bottom = Integer.MAX_VALUE;
        for (BlockPos p : group) {
            int c = Curtains.column(p, side);
            min = Math.min(min, c);
            max = Math.max(max, c);
            top = Math.max(top, p.getY());
            bottom = Math.min(bottom, p.getY());
        }
        if (group.isEmpty()) {
            min = max = Curtains.column(pos, side);
            top = bottom = pos.getY();
        }
        l = new Layout(facing, side, min, max, top, bottom);
        for (BlockPos p : group) LAYOUTS.put(p, l);
        LAYOUTS.put(pos.immutable(), l);
        return l;
    }

    /** The server started a slide: the whole curtain moves from where its fabric is now. */
    private static void slide(ClientLevel level, CurtainNetwork.Slide slide) {
        BlockState state = level.getBlockState(slide.pos());
        if (!(state.getBlock() instanceof CurtainBlock)) return;
        double now = PortraitClock.ticks(level.getGameTime());
        double from = openness(slide.pos(), state, now, 0);
        double to = slide.open() ? 1 : 0;
        double duration = Math.max(4, slide.ticks() * Math.abs(to - from));
        Motion m = new Motion(from, to, now, duration);
        for (BlockPos p : Curtains.group(level, slide.pos())) MOTIONS.put(p, m);
        dropFinished(level, now);
    }

    /**
     * How open the fabric of the block at {@code pos} is at {@code time} (0 closed, 1 open, a little outside while
     * it swings), for a row {@code depth} down the curtain (0 top, 1 bottom: it trails behind and swings more).
     */
    static double openness(BlockPos pos, BlockState state, double time, double depth) {
        Motion m = MOTIONS.get(pos);
        if (m == null) return state.getValue(CurtainBlock.OPEN) ? 1 : 0;
        double t = time - LAG_TICKS * depth;
        double f = m.at(t);
        double since = t - m.end();
        if (since > 0 && since < SETTLE_TICKS) {
            // A damped swing past the end and back
            double dir = Math.signum(m.to - m.from);
            f += dir * SETTLE_SWING * depth * Math.exp(-since / 8) * Math.sin(since * 0.45);
        }
        return f;
    }

    /** Leading edge speed (share of the opening per tick) at {@code time}, for the billowing. */
    static double speed(BlockPos pos, double time) {
        Motion m = MOTIONS.get(pos);
        if (m == null) return 0;
        return Math.abs(m.at(time) - m.at(time - 1));
    }

    /** Forgets slides that are over once the blocks' state has caught up (no jump back meanwhile). */
    private static void dropFinished(ClientLevel level, double now) {
        Iterator<Map.Entry<BlockPos, Motion>> it = MOTIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Motion> e = it.next();
            Motion m = e.getValue();
            if (now < m.end() + SETTLE_TICKS + LAG_TICKS) continue;
            BlockState s = level.getBlockState(e.getKey());
            if (!(s.getBlock() instanceof CurtainBlock) || s.getValue(CurtainBlock.OPEN) == (m.to > 0.5)) it.remove();
        }
    }

    /** Called by the renderer: cleans up finished slides once a second. */
    static void frame(ClientLevel level, double now) {
        if (MOTIONS.isEmpty() || Math.abs(now - lastCleanup) < 20) return;
        lastCleanup = now;
        dropFinished(level, now);
    }

    private static double lastCleanup;
}
