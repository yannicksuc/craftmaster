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
 * the last 10 seconds), the live "+3" announcements. Runtime state; the round timer can be paused (show frozen after
 * a crash) and is saved by {@link fr.lordfinn.craftmaster.reprise.Reprise} to be put back after a restart.
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
        int moved = teleport(server, m);
        announce(server, m, true);
        return moved;
    }

    /** The round's teleports: the season's online players on its TP points, shared out in turn. */
    public static int teleport(MinecraftServer server, Actions.MancheStarted m) {
        // Where everyone stands before (back there at the end of the trial)
        fr.lordfinn.craftmaster.trial.kit.TrialRun.rememberCast(server, m);
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
                // Respawn where the round put them (a death during a trial brings them back to its start)
                fr.lordfinn.craftmaster.trial.kit.Arenas.setRespawn(p, level, spot.x(), spot.y(), spot.z(), spot.yaw());
                moved++;
            }
        }
        // A trial: each contestant at the start of their arena copy, in its game mode, with its kit
        moved += fr.lordfinn.craftmaster.trial.kit.TrialRun.placeCast(server, m);
        return moved;
    }

    /** "Manche 3" in big, the round's name below (a long name would not fit in the title's size). */
    public static void roundTitle(ServerPlayer p, Actions.MancheStarted m) {
        Component title = Component.translatable("show.craftmaster.manche_title", m.number()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component sub = Component.literal(m.name() + (m.timer() > 0 ? " · " + Actions.clock(m.timer()) : "")).withStyle(ChatFormatting.YELLOW);
        title(p, title, sub, 10, 60, 20);
    }

    /** The round's go: its title for everyone ({@code title}), a horn, and its timer if it has one. */
    public static void announce(MinecraftServer server, Actions.MancheStarted m, boolean title) {
        if (title) for (ServerPlayer p : server.getPlayerList().getPlayers()) roundTitle(p, m);
        soundAll(server, SoundEvents.GOAT_HORN_SOUND_VARIANTS.getFirst(), 1f, 1f);
        stopTimer(server);
        // Its timer now, or when a letter is read / its start button clicked (task.RoundStart)
        if (!fr.lordfinn.craftmaster.task.RoundStart.arm(server, m) && m.timer() > 0) startTimer(server, m.name(), m.timer());
    }

    /** What else starts with a round: the timer tablets set to, and the show's listeners (direction, recordings…). */
    public static void roundStarted(MinecraftServer server, Actions.MancheStarted m) {
        // Held timer: the tablets start with it (task.RoundStart)
        if (!fr.lordfinn.craftmaster.task.RoundStart.waiting()) fr.lordfinn.craftmaster.studio.ClockBlockEntity.onRoundStart(server);
        // Its trial set up (copies, contestants in place, kit) and its envelopes handed out
        fr.lordfinn.craftmaster.trial.kit.TrialRun.onRoundStarted(server, m);
        ShowEvents.onMancheStarted(server, m.season(), m.number(), m.name());
    }

    public static void endManche(MinecraftServer server, String name) {
        fr.lordfinn.craftmaster.task.RoundStart.clear(server);
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
        announcePublished(server, -1);
    }

    /**
     * "Scores published" for everyone; each player of the season also gets, in the chat, what changed for them:
     * the rounds whose score moved (+3: 4 → 7), their total and their rank (▲ / ▼). Nothing detailed while the
     * season is hidden (it would spoil the reveal).
     */
    public static void announcePublished(MinecraftServer server, int season) {
        var data = fr.lordfinn.craftmaster.CraftMaster.data();
        fr.lordfinn.craftmaster.data.Season s = data == null || season < 0 ? null : data.season(season);
        java.util.Map<String, fr.lordfinn.craftmaster.data.PublishDiff.Change> diff = null;
        if (s != null && !s.hidden && s.published != null) {
            java.util.Set<String> cast = new java.util.LinkedHashSet<>();
            s.players.forEach((n, pl) -> {
                if (!pl.archived) cast.add(n);
            });
            diff = fr.lordfinn.craftmaster.data.PublishDiff.diff(s.previousPublished == null ? null : s.previousPublished.scores(), s.published.scores(), cast);
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendOverlayMessage(Component.translatable("show.craftmaster.published").withStyle(ChatFormatting.GOLD));
            sound(p, SoundEvents.NOTE_BLOCK_CHIME, 0.8f, 1.4f);
            fr.lordfinn.craftmaster.data.PublishDiff.Change c = diff == null ? null : fr.lordfinn.craftmaster.data.PublishDiff.of(diff, p.getGameProfile().name());
            if (c == null || !c.changed()) continue;
            net.minecraft.network.chat.MutableComponent msg = Component.translatable("show.craftmaster.published.you").withStyle(ChatFormatting.GOLD);
            for (fr.lordfinn.craftmaster.data.PublishDiff.Round r : c.rounds()) {
                int d = r.delta();
                msg.append(Component.literal("\n  " + r.label() + " : ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal((d > 0 ? "+" : "") + d).withStyle(d > 0 ? ChatFormatting.GREEN : ChatFormatting.RED))
                        .append(Component.literal(" (" + r.from() + " → " + r.to() + ")").withStyle(ChatFormatting.DARK_GRAY));
            }
            int d = c.delta();
            msg.append(Component.literal("\n  ").append(Component.translatable("show.craftmaster.published.total", c.totalAfter())).withStyle(ChatFormatting.WHITE));
            if (d != 0) msg.append(Component.literal(" (" + (d > 0 ? "+" : "") + d + ")").withStyle(d > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
            msg.append(Component.literal(" · #" + c.rankAfter()).withStyle(ChatFormatting.YELLOW));
            if (c.rankBefore() > 0 && c.rankBefore() != c.rankAfter()) {
                int up = c.rankBefore() - c.rankAfter();
                msg.append(Component.literal(up > 0 ? " ▲" + up : " ▼" + (-up)).withStyle(up > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            p.sendSystemMessage(msg);
        }
    }

    // ---------------------------------------------------------------- timer

    private static ServerBossEvent bar;
    private static String timerName;
    private static long timerEnd, timerTotal;
    private static int lastShownSecond;
    /** Paused (show frozen): the ticks that were left when it stopped, and the last tick seen. */
    private static boolean paused;
    private static long pausedLeft, lastTick;

    /** The round timer's boss bar, if one runs (a replay file starting now must be told it exists). */
    public static ServerBossEvent timerBar() {
        return bar;
    }

    public static boolean timerRunning() {
        return bar != null;
    }

    /** Name of the running timer's round, or null (TV overlay, chantier E). */
    public static String timerName() {
        return bar == null ? null : timerName;
    }

    /** Server tick when the running timer ends, or -1; and its total length in ticks (TV overlay, chantier E). */
    public static long timerEndTick() {
        // Paused: always "the ticks left from now" (the clients do not count down while the game is frozen)
        return bar == null ? -1 : paused ? lastTick + pausedLeft : timerEnd;
    }

    public static int timerTotalTicks() {
        return bar == null ? 0 : (int) timerTotal;
    }

    /** Seconds left on the round timer, or -1 when none runs. */
    public static int timerRemaining(MinecraftServer server) {
        return bar == null ? -1 : (int) Math.ceil(timerLeftTicks(server) / 20.0);
    }

    /** Ticks left on the round timer (exact, standing still while paused), or -1 when none runs. */
    public static long timerLeftTicks(MinecraftServer server) {
        return bar == null ? -1 : paused ? pausedLeft : Math.max(0, timerEnd - server.getTickCount());
    }

    public static boolean timerPaused() {
        return bar != null && paused;
    }

    /** The round timer stands still where it is (show frozen). @return true if one was running */
    public static boolean pauseTimer(MinecraftServer server) {
        if (bar == null || paused) return false;
        pausedLeft = Math.max(0, timerEnd - server.getTickCount());
        paused = true;
        lastTick = server.getTickCount();
        bar.setColor(BossEvent.BossBarColor.WHITE);
        return true;
    }

    /** It counts down again from the ticks it had left. @return true if one was paused */
    public static boolean resumeTimer(MinecraftServer server) {
        if (bar == null || !paused) return false;
        paused = false;
        timerEnd = server.getTickCount() + pausedLeft;
        bar.setColor(lastShownSecond <= 10 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW);
        return true;
    }

    /** Puts back, paused, a timer saved before a restart: its round's name, total and ticks left. */
    public static void restoreTimer(MinecraftServer server, String name, long totalTicks, long leftTicks) {
        stopTimer(server);
        timerName = name;
        timerTotal = Math.max(1, totalTicks);
        long left = Math.max(0, Math.min(leftTicks, timerTotal));
        int seconds = (int) Math.ceil(left / 20.0);
        lastShownSecond = seconds;
        paused = true;
        pausedLeft = left;
        lastTick = server.getTickCount();
        timerEnd = lastTick + left;
        bar = new ServerBossEvent(UUID.randomUUID(), barName(seconds), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
        bar.setProgress(Mth.clamp(left / (float) timerTotal, 0f, 1f));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) bar.addPlayer(p);
    }

    public static void startTimer(MinecraftServer server, String name, int seconds) {
        stopTimer(server);
        timerName = name;
        timerTotal = seconds * 20L;
        timerEnd = server.getTickCount() + timerTotal;
        lastShownSecond = seconds + 1;
        bar = new ServerBossEvent(UUID.randomUUID(), barName(seconds), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) bar.addPlayer(p);
        // Started while the show is frozen: it waits for the go like the others
        if (fr.lordfinn.craftmaster.reprise.Reprise.frozen()) pauseTimer(server);
    }

    /** @return true if a timer was running */
    public static boolean stopTimer(MinecraftServer server) {
        if (bar == null) return false;
        bar.removeAllPlayers();
        bar = null;
        paused = false;
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
        lastTick = server.getTickCount();
        if (bar == null || paused) return;
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
        if (bar != null) out.add("Chrono : " + timerName + " — " + Actions.clock(timerRemaining(server)) + " restant" + (paused ? " (en pause)" : ""));
        if (Reveal.running()) out.add("Révélation en cours.");
        out.addAll(fr.lordfinn.craftmaster.task.RoundStart.status(server));
        return out;
    }
}
