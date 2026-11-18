package fr.lordfinn.craftmaster.trial;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.show.Show;
import fr.lordfinn.craftmaster.trial.block.BuzzerBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The buzzers of the server, all in one game. Its {@link Mode} (saved with the show, {@code /cm epreuve buzzer
 * mode}): "first" (the first press lights its buzzer, tells everyone, locks the others out until the reset; later
 * presses are still noted 2nd, 3rd with the delay), "order" (everyone may buzz once, every buzzer lights up and the
 * order is announced), "auto" (like first, re-armed by itself after a few seconds), "free" (every press flashes its
 * buzzer and sounds, nothing locks). Reset: {@code buzzer.reset} (command, voice, StreamDeck), an organiser sneaking
 * + using a buzzer, or a redstone pulse into any buzzer.
 */
public final class Buzzers {

    private Buzzers() {}

    public enum Mode {
        FIRST("first", "Premier qui buzze", "le premier allume son buzzer et bloque les autres jusqu'à la remise à zéro"),
        ORDER("order", "Tous dans l'ordre", "chacun buzze une fois, tous s'allument, l'ordre d'arrivée est annoncé"),
        AUTO("auto", "Réarmement automatique", "comme « premier », puis les buzzers se réarment seuls après le délai"),
        FREE("free", "Libre", "chaque appui fait clignoter son buzzer et sonne, sans blocage ni ordre");

        public final String id, label, help;

        Mode(String id, String label, String help) {
            this.id = id;
            this.label = label;
            this.help = help;
        }

        public static Mode byId(String id) {
            for (Mode m : values()) if (m.id.equalsIgnoreCase(id)) return m;
            return FIRST;
        }
    }

    public static Mode mode() {
        return Mode.byId(fr.lordfinn.craftmaster.CraftMaster.data().stage().buzzerMode);
    }

    public static int rearmSeconds() {
        return Math.max(1, fr.lordfinn.craftmaster.CraftMaster.data().stage().buzzerRearm);
    }

    /** Server tick at which the buzzers re-arm by themselves ("auto"), or -1. */
    private static long rearmAt = -1;
    /** "free" flashes: buzzer -> tick at which it goes off. */
    private static final java.util.Map<GlobalPos, Long> FLASHES = new java.util.HashMap<>();

    public static final BuzzerLock LOCK = new BuzzerLock();
    /** Buzzers lit since the last reset (to switch them off). */
    private static final Set<GlobalPos> LIT = new LinkedHashSet<>();

    /** A player presses the buzzer at {@code pos}. */
    public static void press(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        MinecraftServer server = level.getServer();
        String name = Judging.contestant(player) != null ? Judging.contestant(player) : player.getGameProfile().name();
        Mode mode = mode();
        if (mode == Mode.FREE) {
            level.setBlock(pos, state.setValue(BuzzerBlock.LIT, true), 3);
            FLASHES.put(GlobalPos.of(level.dimension(), pos.immutable()), (long) server.getTickCount() + 20);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1.2f);
            TrialLog.add(server, "Buzzer : " + name);
            return;
        }
        BuzzerLock.Press press = LOCK.press(name, System.currentTimeMillis());
        if (press == null) {
            player.sendOverlayMessage(Component.literal("Tu as déjà buzzé.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (mode == Mode.ORDER) {
            level.setBlock(pos, state.setValue(BuzzerBlock.LIT, true), 3);
            LIT.add(GlobalPos.of(level.dimension(), pos.immutable()));
            String delay = press.first() ? "" : String.format(java.util.Locale.ROOT, " (+%.2f s)", press.delayMs() / 1000.0).replace('.', ',');
            Component line = Component.literal("🔔 " + press.order() + (press.order() == 1 ? "er" : "e") + " : ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(name + delay).withStyle(ChatFormatting.WHITE));
            for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(line);
            Show.soundAll(server, SoundEvents.NOTE_BLOCK_BIT, 1f, press.first() ? 1.2f : 0.9f);
            TrialLog.add(server, "Buzzer : " + name + " " + press.order() + "e" + delay);
            if (press.first()) fr.lordfinn.craftmaster.show.ShowEvents.spotlight(server, player.getGameProfile().name(), "buzzer");
            return;
        }
        if (press.first()) {
            level.setBlock(pos, state.setValue(BuzzerBlock.LIT, true), 3);
            LIT.add(GlobalPos.of(level.dimension(), pos.immutable()));
            if (mode == Mode.AUTO) rearmAt = server.getTickCount() + 20L * rearmSeconds();
            Show.titleAll(server, Component.literal(name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.literal("a buzzé en premier !").withStyle(ChatFormatting.YELLOW), 2, 40, 10);
            Show.soundAll(server, SoundEvents.NOTE_BLOCK_DIDGERIDOO, 1f, 0.7f);
            Show.soundAll(server, SoundEvents.NOTE_BLOCK_BIT, 1f, 1.2f);
            TrialLog.add(server, "Buzzer : " + name + " en premier");
            // The automatic TV direction cuts to the winner
            fr.lordfinn.craftmaster.show.ShowEvents.spotlight(server, player.getGameProfile().name(), "buzzer");
        } else {
            String delay = String.format(java.util.Locale.ROOT, "%.2f", press.delayMs() / 1000.0).replace('.', ',');
            player.sendOverlayMessage(Component.literal("Trop tard ! " + press.order() + "e, +" + delay + " s").withStyle(ChatFormatting.RED));
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 0.5f);
            TrialLog.add(server, "Buzzer : " + name + " " + press.order() + "e (+" + delay + " s)");
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (Permissions.isAdmin(p))
                    p.sendSystemMessage(Component.literal("🔔 " + name + " — " + press.order() + "e (+" + delay + " s)").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Every server tick: "free" flashes go off, "auto" re-arms. */
    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (rearmAt >= 0 && now >= rearmAt) {
            rearmAt = -1;
            reset(server);
        }
        for (java.util.Iterator<java.util.Map.Entry<GlobalPos, Long>> it = FLASHES.entrySet().iterator(); it.hasNext(); ) {
            java.util.Map.Entry<GlobalPos, Long> e = it.next();
            if (now < e.getValue()) continue;
            switchOff(server, e.getKey());
            it.remove();
        }
    }

    private static boolean switchOff(MinecraftServer server, GlobalPos g) {
        ServerLevel level = server.getLevel(g.dimension());
        if (level == null || !level.isLoaded(g.pos())) return false;
        BlockState s = level.getBlockState(g.pos());
        if (s.getBlock() instanceof BuzzerBlock && s.getValue(BuzzerBlock.LIT)) {
            level.setBlock(g.pos(), s.setValue(BuzzerBlock.LIT, false), 3);
            return true;
        }
        return false;
    }

    /** Buzzers lit since the last reset (saved for a restart after a crash). */
    public static Set<GlobalPos> lit() {
        return Set.copyOf(LIT);
    }

    /** Buzzers lit before a restart, to switch them off at the next reset. */
    public static void restoreLit(java.util.Collection<GlobalPos> lit) {
        LIT.clear();
        LIT.addAll(lit);
    }

    /** Switches every buzzer off and opens the game again. @return the number of buzzers switched off */
    public static int reset(MinecraftServer server) {
        rearmAt = -1;
        LOCK.reset();
        int n = 0;
        for (GlobalPos g : LIT) {
            ServerLevel level = server.getLevel(g.dimension());
            if (level == null || !level.isLoaded(g.pos())) continue;
            BlockState s = level.getBlockState(g.pos());
            if (s.getBlock() instanceof BuzzerBlock && s.getValue(BuzzerBlock.LIT)) {
                level.setBlock(g.pos(), s.setValue(BuzzerBlock.LIT, false), 3);
                n++;
            }
        }
        LIT.clear();
        Show.soundAll(server, SoundEvents.NOTE_BLOCK_CHIME, 0.6f, 1.5f);
        return n;
    }
}
