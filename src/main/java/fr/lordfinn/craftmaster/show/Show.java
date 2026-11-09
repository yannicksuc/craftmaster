package fr.lordfinn.craftmaster.show;

import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.data.Spot;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What everyone sees and hears during the show: round titles, teleports, the round timer (boss bar, countdown of
 * the last 10 seconds), the live "+3" announcements. Runtime state only (a restart forgets a running timer).
 */
public final class Show {

    private Show() {}

    // ---------------------------------------------------------------- titles and sounds

    public static void title(ServerPlayer p, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle == null ? Component.empty() : subtitle));
        p.connection.send(new ClientboundSetTitleTextPacket(title == null ? Component.empty() : title));
    }

    public static void titleAll(MinecraftServer server, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) title(p, title, subtitle, fadeIn, stay, fadeOut);
    }

    public static void clearTitles(MinecraftServer server) {
        server.getPlayerList().broadcastAll(new ClientboundClearTitlesPacket(true));
    }

    /** Plays a sound for the player only, where they stand (heard the same everywhere). */
    public static void sound(ServerPlayer p, Holder<SoundEvent> sound, float volume, float pitch) {
        p.connection.send(new ClientboundSoundPacket(sound, SoundSource.MASTER, p.getX(), p.getY(), p.getZ(), volume, pitch,
                p.getRandom().nextLong()));
    }

    public static void soundAll(MinecraftServer server, Holder<SoundEvent> sound, float volume, float pitch) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sound(p, sound, volume, pitch);
    }

    public static Holder<SoundEvent> holder(SoundEvent e) {
        return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(e);
    }

    // ---------------------------------------------------------------- rounds

    /**
     * Starts a round: the season's online players are teleported to its TP points (shared out in turn), everyone
     * gets the big title and a horn, and the timer starts if the round has one.
     *
     * @return the number of players teleported
     */
    public static int startManche(MinecraftServer server, Actions.MancheStarted m) {
        int moved = 0;
        if (!m.tp().isEmpty()) {
            int i = 0;
            for (String name : m.cast()) {
                ServerPlayer p = server.getPlayerList().getPlayerByName(name);
                if (p == null) continue;
                Spot spot = m.tp().get(i++ % m.tp().size());
                ServerLevel level = Busts.level(server, spot.world());
                if (level == null) continue;
                p.teleportTo(level, spot.x(), spot.y(), spot.z(), Set.of(), spot.yaw(), spot.pitch(), true);
                moved++;
            }
        }
        // "Manche 3" in big, the round's name below: a long name would not fit in the title's size
        Component title = Component.translatable("show.craftmaster.manche_title", m.number()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component sub = Component.literal(m.name() + (m.timer() > 0 ? " · " + Actions.clock(m.timer()) : "")).withStyle(ChatFormatting.YELLOW);
        titleAll(server, title, sub, 10, 60, 20);
        soundAll(server, SoundEvents.GOAT_HORN_SOUND_VARIANTS.getFirst(), 1f, 1f);
        stopTimer(server);
        if (m.timer() > 0) startTimer(server, m.name(), m.timer());
        return moved;
    }

    public static void endManche(MinecraftServer server, String name) {
        stopTimer(server);
        titleAll(server, Component.translatable("show.craftmaster.manche_end").withStyle(ChatFormatting.GOLD),
                Component.literal(name).withStyle(ChatFormatting.YELLOW), 5, 40, 15);
        soundAll(server, holder(SoundEvents.BELL_BLOCK), 1f, 1f);
    }

    /** "LordFinn +3 (Le Pont)" in everyone's action bar; the player also gets "+3" as a subtitle. */
    public static void announceScore(MinecraftServer server, Actions.ScoreGiven s) {
        String delta = (s.delta() > 0 ? "+" : "") + s.delta();
        Component bar = Component.literal(s.player() + " ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(delta).withStyle(s.delta() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal(" (" + s.manche() + ")").withStyle(ChatFormatting.GRAY));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendOverlayMessage(bar);
            sound(p, holder(SoundEvents.EXPERIENCE_ORB_PICKUP), 0.6f, s.delta() > 0 ? 1.2f : 0.7f);
            if (p.getGameProfile().name().equalsIgnoreCase(s.player()))
                title(p, Component.empty(), Component.literal(delta).withStyle(s.delta() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD), 3, 25, 10);
        }
    }

    /** Everyone hears the ranking was updated. */
    public static void announcePublished(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendOverlayMessage(Component.translatable("show.craftmaster.published").withStyle(ChatFormatting.GOLD));
            sound(p, SoundEvents.NOTE_BLOCK_CHIME, 0.8f, 1.4f);
        }
    }

    // ---------------------------------------------------------------- timer

    private static ServerBossEvent bar;
    private static String timerName;
    private static long timerEnd, timerTotal;
    private static int lastShownSecond;

    public static boolean timerRunning() {
        return bar != null;
    }

    /** Seconds left on the round timer, or -1 when none runs. */
    public static int timerRemaining(MinecraftServer server) {
        return bar == null ? -1 : (int) Math.ceil(Math.max(0, timerEnd - server.getTickCount()) / 20.0);
    }

    public static void startTimer(MinecraftServer server, String name, int seconds) {
        stopTimer(server);
        timerName = name;
        timerTotal = seconds * 20L;
        timerEnd = server.getTickCount() + timerTotal;
        lastShownSecond = seconds + 1;
        bar = new ServerBossEvent(UUID.randomUUID(), barName(seconds), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) bar.addPlayer(p);
    }

    /** @return true if a timer was running */
    public static boolean stopTimer(MinecraftServer server) {
        if (bar == null) return false;
        bar.removeAllPlayers();
        bar = null;
        return true;
    }

    private static Component barName(int seconds) {
        return Component.literal(timerName + " — " + Actions.clock(seconds)).withStyle(ChatFormatting.YELLOW);
    }

    /** Players joining while a timer runs see it too. */
    public static void onJoin(ServerPlayer p) {
        if (bar != null) bar.addPlayer(p);
    }

    public static void tick(MinecraftServer server) {
        Reveal.tick(server);
        if (bar == null) return;
        long left = timerEnd - server.getTickCount();
        int seconds = (int) Math.ceil(Math.max(0, left) / 20.0);
        bar.setProgress(Mth.clamp(left / (float) timerTotal, 0f, 1f));
        if (seconds != lastShownSecond) {
            lastShownSecond = seconds;
            bar.setName(barName(seconds));
            if (seconds <= 10 && seconds > 0) {
                bar.setColor(BossEvent.BossBarColor.RED);
                titleAll(server, Component.literal(String.valueOf(seconds)).withStyle(seconds <= 3 ? ChatFormatting.RED : ChatFormatting.GOLD, ChatFormatting.BOLD),
                        null, 0, 22, 2);
                soundAll(server, SoundEvents.NOTE_BLOCK_HAT, 1f, 1f + (10 - seconds) * 0.06f);
            }
        }
        if (left <= 0) {
            stopTimer(server);
            titleAll(server, Component.translatable("show.craftmaster.time_up").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    Component.literal(timerName).withStyle(ChatFormatting.YELLOW), 0, 50, 20);
            soundAll(server, SoundEvents.RAID_HORN, 1f, 1f);
            soundAll(server, holder(SoundEvents.BELL_BLOCK), 1f, 0.8f);
        }
    }

    /** Forgets the runtime state (server stopping). */
    public static void reset(MinecraftServer server) {
        stopTimer(server);
        Reveal.stop(server, false);
    }

    /** Lines describing what runs now, for the organisers. */
    public static List<String> status(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        if (bar != null) out.add("Chrono : " + timerName + " — " + Actions.clock(timerRemaining(server)) + " restant");
        if (Reveal.running()) out.add("Révélation en cours.");
        return out;
    }
}
