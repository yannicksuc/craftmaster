package fr.lordfinn.craftmaster.trial.kit;

import fr.lordfinn.craftmaster.show.Show;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Drawing by walking in a field ({@link Arena#field}): between the red banner (start) and the green one (end), the
 * player's path is recorded. In a <b>blind</b> field ({@link Arena.Field#blind}) nothing shows while they walk; else
 * each floor block walked on turns to ink. Either way the path becomes a picture at the end ({@link DrawingImages}),
 * for the jury's screen. The round's timer ending stops every drawing.
 */
public final class Drawing {

    private Drawing() {}

    private record Key(String arena, String player) {}

    /** A drawing in progress: the path walked, in blocks from the field's corner (NaN, NaN = the pen lifted). */
    static final class Trace {
        final java.util.List<float[]> points = new java.util.ArrayList<>();
        int steps;
        float lastX = Float.NaN, lastZ = Float.NaN;
    }

    /** Drawings in progress. */
    private static final Map<Key, Trace> DRAWING = new HashMap<>();
    /** Finished drawings (a red banner does not start them again before the reset). */
    private static final Set<Key> DONE = new HashSet<>();
    /** The finished paths, until the arena starts over (the live preview still shows them). */
    private static final Map<Key, Trace> LAST = new HashMap<>();
    private static boolean timerWasRunning;

    private static Key key(Arenas.Owner o, ServerPlayer p) {
        return new Key(o.arena().name.toLowerCase(), Arenas.playerName(p).toLowerCase());
    }

    static void start(ServerPlayer p, Arenas.Owner o) {
        Key k = key(o, p);
        if (DONE.contains(k)) {
            p.sendOverlayMessage(Component.literal("Ton dessin est déjà terminé").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (DRAWING.containsKey(k)) return;
        DRAWING.put(k, new Trace());
        Arena.Field field = o.arena().drawingField();
        boolean blind = field != null && field.blind;
        Show.title(p, Component.literal("Dessin commencé").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                Component.literal(blind ? "Marche pour dessiner (tu ne vois rien) ; bannière verte pour finir"
                        : "Marche pour dessiner ; bannière verte pour finir").withStyle(ChatFormatting.GRAY), 3, 40, 10);
        Show.sound(p, SoundEvents.NOTE_BLOCK_PLING, 1f, 1.2f);
        String name = Arenas.playerName(p);
        Finish.tellOrganisers(p.level().getServer(), p, Component.literal("✎ ").withStyle(ChatFormatting.GOLD).append(Finish.name(name))
                .append(Component.literal(" commence son dessin").withStyle(ChatFormatting.GOLD)), name + " commence son dessin");
    }

    static void stop(ServerPlayer p, Arenas.Owner o, boolean timeUp) {
        Key k = key(o, p);
        Trace n = DRAWING.remove(k);
        if (n == null) {
            if (!timeUp && !DONE.contains(k))
                p.sendOverlayMessage(Component.literal("Plante d'abord la bannière rouge pour commencer").withStyle(ChatFormatting.YELLOW));
            return;
        }
        DONE.add(k);
        finished(p.level().getServer(), p, Arenas.playerName(p), o.arena(), n, timeUp);
    }

    private static void finished(MinecraftServer server, ServerPlayer p, String name, Arena arena, Trace trace, boolean timeUp) {
        int blocks = trace.steps;
        LAST.put(new Key(arena.name.toLowerCase(), name.toLowerCase()), trace);
        DrawingImages.save(server, arena, name, trace);
        if (p != null) {
            Show.title(p, Component.literal(timeUp ? "Temps écoulé" : "Dessin terminé").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                    Component.literal(arena.drawingField() != null && arena.drawingField().blind ? "Ton dessin est enregistré" : blocks + " blocs tracés")
                            .withStyle(ChatFormatting.GRAY), 3, 40, 10);
            Show.sound(p, SoundEvents.NOTE_BLOCK_CHIME, 1f, 1f);
        }
        Finish.tellOrganisers(server, p, Component.literal("✎ ").withStyle(ChatFormatting.GOLD).append(Finish.name(name))
                .append(Component.literal((timeUp ? " : temps écoulé, " : " a fini son dessin, ") + blocks + " blocs").withStyle(ChatFormatting.GOLD)),
                name + (timeUp ? " : temps écoulé" : " a fini son dessin") + " (" + blocks + " blocs)");
    }

    /** The arena was rebuilt (or removed): its drawings start over. */
    static void forget(String arena) {
        DRAWING.keySet().removeIf(k -> k.arena.equalsIgnoreCase(arena));
        DONE.removeIf(k -> k.arena.equalsIgnoreCase(arena));
        LAST.keySet().removeIf(k -> k.arena.equalsIgnoreCase(arena));
    }

    /** A path as the live preview reads it. */
    public record View(java.util.List<float[]> points, int w, int d, boolean live) {}

    public static boolean anyTrace() {
        return !DRAWING.isEmpty() || !LAST.isEmpty();
    }

    /** Is this contestant drawing right now? */
    public static boolean drawing(String player) {
        for (Key k : DRAWING.keySet()) if (k.player.equalsIgnoreCase(player)) return true;
        return false;
    }

    /** This contestant's path (being drawn, else the last finished one), or null. */
    public static View view(String player) {
        for (boolean live : new boolean[]{true, false})
            for (Map.Entry<Key, Trace> e : (live ? DRAWING : LAST).entrySet()) {
                if (!e.getKey().player.equalsIgnoreCase(player)) continue;
                Arena a = Arenas.get(e.getKey().arena);
                Arena.Field f = a == null ? null : a.drawingField();
                if (f != null) return new View(e.getValue().points, f.x1 - f.x0 + 1, f.z1 - f.z0 + 1, live);
            }
        return null;
    }

    public static void tick(MinecraftServer server) {
        boolean running = Show.timerRunning();
        boolean timeUp = timerWasRunning && !running;
        timerWasRunning = running;
        if (DRAWING.isEmpty()) return;
        for (Iterator<Map.Entry<Key, Trace>> it = DRAWING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Key, Trace> e = it.next();
            ServerPlayer p = null;
            for (ServerPlayer q : server.getPlayerList().getPlayers()) if (Arenas.playerName(q).equalsIgnoreCase(e.getKey().player)) p = q;
            Arena arena = Arenas.get(e.getKey().arena);
            if (timeUp) {
                it.remove();
                DONE.add(e.getKey());
                if (arena != null) finished(server, p, p != null ? Arenas.playerName(p) : e.getKey().player, arena, e.getValue(), true);
                continue;
            }
            Trace t = e.getValue();
            if (p == null || !p.onGround() || p.isSpectator()) {
                lift(t);
                continue;
            }
            BlockPos below = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
            Arenas.Owner o = Arenas.ownerAt(p.level(), below);
            if (o == null || !o.arena().name.equalsIgnoreCase(e.getKey().arena) || !Arenas.mayUse(p, o) || !Arenas.inField(o, below, true)) {
                lift(t);
                continue;
            }
            Arena.Field f = o.arena().drawingField();
            int[] off = o.offset();
            float fx = (float) (p.getX() - f.x0 - (off == null ? 0 : off[0])), fz = (float) (p.getZ() - f.z0 - (off == null ? 0 : off[2]));
            // A point every 0.15 block of walk (standing still adds nothing)
            if (Float.isNaN(t.lastX) || Math.hypot(fx - t.lastX, fz - t.lastZ) >= 0.15) {
                t.points.add(new float[]{fx, fz});
                t.lastX = fx;
                t.lastZ = fz;
            }
            if (f.blind) {
                t.steps = t.points.size();
                continue;
            }
            ServerLevel level = (ServerLevel) p.level();
            BlockState paper = Arenas.block(f.paper, Blocks.CONCRETE.white().defaultBlockState());
            if (level.getBlockState(below).getBlock() != paper.getBlock()) continue;
            level.setBlock(below, Arenas.block(f.ink, Blocks.CONCRETE.black().defaultBlockState()), Block.UPDATE_CLIENTS);
            t.steps++;
        }
    }

    /** Leaving the floor (a jump, out of the field): the next point starts a new stroke. */
    private static void lift(Trace t) {
        if (Float.isNaN(t.lastX)) return;
        t.points.add(new float[]{Float.NaN, Float.NaN});
        t.lastX = Float.NaN;
        t.lastZ = Float.NaN;
    }
}
