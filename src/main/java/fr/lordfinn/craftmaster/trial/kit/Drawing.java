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
 * Drawing by walking in a field ({@link Arena#field}): between the red banner (start) and the green one (end), each
 * floor block the player walks on turns to ink. The blocks are the drawing: the jury looks from above. The round's
 * timer ending stops every drawing.
 */
public final class Drawing {

    private Drawing() {}

    private record Key(String arena, String player) {}

    /** Drawings in progress → number of blocks inked. */
    private static final Map<Key, int[]> DRAWING = new HashMap<>();
    /** Finished drawings (a red banner does not start them again before the reset). */
    private static final Set<Key> DONE = new HashSet<>();
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
        DRAWING.put(k, new int[1]);
        Show.title(p, Component.literal("Dessin commencé").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                Component.literal("Marche pour dessiner ; bannière verte pour finir").withStyle(ChatFormatting.GRAY), 3, 40, 10);
        Show.sound(p, SoundEvents.NOTE_BLOCK_PLING, 1f, 1.2f);
        String name = Arenas.playerName(p);
        Finish.tellOrganisers(p.level().getServer(), p, Component.literal("✎ ").withStyle(ChatFormatting.GOLD).append(Finish.name(name))
                .append(Component.literal(" commence son dessin").withStyle(ChatFormatting.GOLD)), name + " commence son dessin");
    }

    static void stop(ServerPlayer p, Arenas.Owner o, boolean timeUp) {
        Key k = key(o, p);
        int[] n = DRAWING.remove(k);
        if (n == null) {
            if (!timeUp && !DONE.contains(k))
                p.sendOverlayMessage(Component.literal("Plante d'abord la bannière rouge pour commencer").withStyle(ChatFormatting.YELLOW));
            return;
        }
        DONE.add(k);
        finished(p.level().getServer(), p, Arenas.playerName(p), n[0], timeUp);
    }

    private static void finished(MinecraftServer server, ServerPlayer p, String name, int blocks, boolean timeUp) {
        if (p != null) {
            Show.title(p, Component.literal(timeUp ? "Temps écoulé" : "Dessin terminé").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                    Component.literal(blocks + " blocs tracés").withStyle(ChatFormatting.GRAY), 3, 40, 10);
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
    }

    public static void tick(MinecraftServer server) {
        boolean running = Show.timerRunning();
        boolean timeUp = timerWasRunning && !running;
        timerWasRunning = running;
        if (DRAWING.isEmpty()) return;
        for (Iterator<Map.Entry<Key, int[]>> it = DRAWING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Key, int[]> e = it.next();
            ServerPlayer p = null;
            for (ServerPlayer q : server.getPlayerList().getPlayers()) if (Arenas.playerName(q).equalsIgnoreCase(e.getKey().player)) p = q;
            if (timeUp) {
                it.remove();
                DONE.add(e.getKey());
                finished(server, p, p != null ? Arenas.playerName(p) : e.getKey().player, e.getValue()[0], true);
                continue;
            }
            if (p == null || !p.onGround() || p.isSpectator()) continue;
            BlockPos below = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
            Arenas.Owner o = Arenas.ownerAt(p.level(), below);
            if (o == null || !o.arena().name.equalsIgnoreCase(e.getKey().arena) || !Arenas.mayUse(p, o) || !Arenas.inField(o, below, true)) continue;
            Arena.Field f = o.arena().field;
            ServerLevel level = (ServerLevel) p.level();
            BlockState paper = Arenas.block(f.paper, Blocks.CONCRETE.white().defaultBlockState());
            if (level.getBlockState(below).getBlock() != paper.getBlock()) continue;
            level.setBlock(below, Arenas.block(f.ink, Blocks.CONCRETE.black().defaultBlockState()), Block.UPDATE_CLIENTS);
            e.getValue()[0]++;
        }
    }
}
