package fr.lordfinn.craftmaster.trial.kit;

import fr.lordfinn.craftmaster.trial.Judging;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * An arena played by everyone on the same ground ({@link Arena#terrainCommun}): nothing is copied, each contestant
 * starts at their own place on the real floor, their path is only recorded (nothing is ever put on the ground), and
 * while the trial runs they stay inside the box and neither break nor place anything.
 */
public final class CommonGround {

    private CommonGround() {}

    /** Least distance between two automatic starts, in blocks. */
    private static final int START_SPACING = 12;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 5 == 0) tick(server);
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> !locked(player, level, pos));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide()) return InteractionResult.PASS;
            ItemStack st = player.getItemInHand(hand);
            if (plays(player) && NameBooks.blocksUse(player, level, hit.getBlockPos())) return InteractionResult.FAIL;
            return st.getItem() instanceof BlockItem && locked(player, level, hit.getBlockPos()) ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        // The banners work anywhere on the ground, aimed at the sky too: nothing is planted
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer sp) || player.isSpectator()) return InteractionResult.PASS;
            return banner(sp, player.getItemInHand(hand)) ? InteractionResult.FAIL : InteractionResult.PASS;
        });
    }

    /** The trial of this arena is running (from its launch until « Revenir au plateau »). */
    static boolean running(Arena a) {
        return a.terrainCommun && a.manche != null && a.manche.equals(TrialRun.trialManche());
    }

    /** The common arena whose trial is running at this place, or null. */
    static Arena at(Level level, BlockPos pos) {
        String dim = level.dimension().identifier().toString();
        for (Arena a : Arenas.all()) if (running(a) && a.world.equals(dim) && a.contains(pos, null)) return a;
        return null;
    }

    private static boolean plays(Player p) {
        if (!(p instanceof ServerPlayer sp) || Judging.contestant(sp) == null) return false;
        GameType g = sp.gameMode();
        return g != GameType.CREATIVE && g != GameType.SPECTATOR;
    }

    private static boolean locked(Player p, Level level, BlockPos pos) {
        return plays(p) && at(level, pos) != null;
    }

    /** A red or green banner in hand on a common ground: starts or ends the drawing, plants nothing. */
    static boolean banner(ServerPlayer sp, ItemStack st) {
        boolean red = st.is(Items.BANNER.red()), green = st.is(Items.BANNER.green()) || st.is(Items.BANNER.lime());
        if (!red && !green) return false;
        Arena a = at(sp.level(), sp.blockPosition());
        if (a == null) return false;
        Arenas.Owner o = new Arenas.Owner(a, null);
        if (red) Drawing.start(sp, o);
        else Drawing.stop(sp, o, false);
        return true;
    }

    private static void tick(MinecraftServer server) {
        for (Arena a : Arenas.all()) {
            if (!running(a)) continue;
            ServerLevel level = Arenas.level(server, a);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.level() != level || !plays(p)) continue;
                if (!a.joueursVisibles) p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false));
                if (a.contains(p.blockPosition(), null)) continue;
                double x = Math.clamp(p.getX(), a.x0 + 1.5, a.x1 - 0.5), z = Math.clamp(p.getZ(), a.z0 + 1.5, a.z1 - 0.5);
                BlockPos g = ground(level, a, (int) Math.floor(x), (int) Math.floor(z));
                double y = g != null ? g.getY() : Math.clamp(p.getY(), a.y0 + 1, a.y1);
                p.teleportTo(level, x, y, z, Set.of(), p.getYRot(), p.getXRot(), false);
                p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                p.sendOverlayMessage(Component.literal("Limite du terrain").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    // ---------------------------------------------------------------- starts

    /** Where a player can stand on the real floor at this column (not in a tree, not in water), or null. */
    static BlockPos ground(ServerLevel level, Arena a, int x, int z) {
        // From the top of the box down, through its barrier ceiling, the air and what one walks through (grass, flowers)
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, a.y1, z);
        while (at.getY() > a.y0) {
            BlockState s = level.getBlockState(at);
            if (!s.getFluidState().isEmpty()) return null;
            if (!s.is(Blocks.BARRIER) && !s.getCollisionShape(level, at).isEmpty()) break;
            at.move(Direction.DOWN);
        }
        if (at.getY() <= a.y0 || at.getY() + 2 > a.y1) return null;
        BlockState floor = level.getBlockState(at);
        if (floor.is(BlockTags.LEAVES) || floor.is(BlockTags.LOGS) || !floor.isFaceSturdy(level, at, Direction.UP)) return null;
        BlockPos feet = at.above().immutable();
        for (BlockPos b : List.of(feet, feet.above())) {
            BlockState st = level.getBlockState(b);
            if (!st.getFluidState().isEmpty() || !st.getCollisionShape(level, b).isEmpty()) return null;
        }
        return feet;
    }

    /**
     * Each contestant's start, in the order of the sorted names (the same at every launch): spread on a grid over the
     * ground, each cell's nearest free floor, at least {@link #START_SPACING} blocks apart; a start set by hand
     * ({@code /cm arene depart}) wins.
     */
    static double[] start(ServerLevel level, Arena a, String name, List<String> everyone) {
        double[] set = a.departs.get(name.toLowerCase(Locale.ROOT));
        if (set != null && set.length >= 3) return set;
        List<String> sorted = new ArrayList<>();
        for (String n : everyone) sorted.add(n.toLowerCase(Locale.ROOT));
        sorted.sort(null);
        int index = Math.max(0, sorted.indexOf(name.toLowerCase(Locale.ROOT)));
        List<BlockPos> taken = new ArrayList<>();
        for (double[] d : a.departs.values()) if (d.length >= 3) taken.add(BlockPos.containing(d[0], d[1], d[2]));
        int cols = (int) Math.ceil(Math.sqrt(Math.max(1, sorted.size()))), rows = (int) Math.ceil(sorted.size() / (double) cols);
        BlockPos found = null;
        for (int k = 0; k <= index; k++) {
            if (a.departs.containsKey(sorted.get(k))) continue;
            int cx = a.x0 + (int) ((k % cols + 0.5) * a.sizeX() / cols), cz = a.z0 + (int) ((k / cols + 0.5) * a.sizeZ() / Math.max(1, rows));
            found = nearest(level, a, cx, cz, taken);
            if (found != null) taken.add(found);
        }
        if (found == null) return Arenas.spawn(a, null);
        return new double[]{found.getX() + 0.5, found.getY(), found.getZ() + 0.5};
    }

    private static BlockPos nearest(ServerLevel level, Arena a, int cx, int cz, List<BlockPos> taken) {
        int reach = Math.max(a.sizeX(), a.sizeZ());
        for (int r = 0; r <= reach; r += 2)
            for (int dx = -r; dx <= r; dx += 2)
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = cx + dx, z = cz + dz;
                    if (x < a.x0 + 2 || x > a.x1 - 2 || z < a.z0 + 2 || z > a.z1 - 2) continue;
                    BlockPos g = ground(level, a, x, z);
                    if (g == null) continue;
                    boolean close = false;
                    for (BlockPos t : taken) if (Math.hypot(t.getX() - x, t.getZ() - z) < START_SPACING) close = true;
                    if (!close) return g;
                }
        return null;
    }
}
