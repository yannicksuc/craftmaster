package fr.lordfinn.craftmaster.trial;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.data.SeasonPlayer;
import fr.lordfinn.craftmaster.show.Actions;
import fr.lordfinn.craftmaster.show.Show;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The judge's tools in the world: what the judging actions show (a result written, a disqualification), the
 * contestants' stopwatches as boss bars everyone sees (a solo attempt is watched by all), and the "write it down"
 * buttons the measuring tools put in the organisers' chat.
 */
public final class Judging {

    private Judging() {}

    /** A stopwatch's bar, kept a few seconds after the stop to show the time. */
    private static final class Bar {
        final ServerBossEvent event;
        long removeAt = Long.MAX_VALUE;

        Bar(ServerBossEvent event) {
            this.event = event;
        }
    }

    private static final Map<String, Bar> BARS = new LinkedHashMap<>();

    /** The stopwatches' boss bars shown now (a replay file starting now must be told they exist). */
    public static List<ServerBossEvent> bossBars() {
        List<ServerBossEvent> out = new java.util.ArrayList<>();
        for (Bar b : BARS.values()) out.add(b.event);
        return out;
    }

    // ---------------------------------------------------------------- effects of the actions

    public static void play(MinecraftServer server, Actions.Effect effect, List<String> lines) {
        switch (effect) {
            case Actions.ResultRecorded r -> announceResult(server, r);
            case Actions.StopwatchStarted w -> startStopwatch(server, w.player());
            case Actions.StopwatchStopped w -> stopStopwatch(server, w.player(), w.ms(), w.quiet());
            case Actions.StopwatchReset w -> resetStopwatch(w.player());
            case Actions.BuzzersReset b -> lines.add(Buzzers.reset(server) + " buzzer(s) éteint(s).");
            default -> {}
        }
    }

    private static void announceResult(MinecraftServer server, Actions.ResultRecorded r) {
        if (r.dq()) {
            Component bar = Component.literal("✖ " + r.player() + " — disqualifié").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(Component.literal(" (" + r.manche() + ")").withStyle(ChatFormatting.GRAY));
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendOverlayMessage(bar);
                Show.sound(p, Show.holder(SoundEvents.ANVIL_LAND), 0.5f, 0.6f);
                if (p.getGameProfile().name().equalsIgnoreCase(r.player()))
                    Show.title(p, Component.literal("Disqualifié").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                            Component.literal(r.manche()).withStyle(ChatFormatting.GRAY), 3, 40, 12);
            }
            return;
        }
        Component bar = Component.literal("✎ " + r.player() + " — ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(r.text()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" (" + r.manche() + ")").withStyle(ChatFormatting.GRAY));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (r.quiet() && !toldPrivately(p, r.player())) continue;
            p.sendOverlayMessage(bar);
            Show.sound(p, Show.holder(SoundEvents.VILLAGER_WORK_CARTOGRAPHER), 0.7f, 1.2f);
        }
    }

    // ---------------------------------------------------------------- stopwatches

    private static void startStopwatch(MinecraftServer server, String player) {
        Stopwatch.SHARED.start(player, System.currentTimeMillis());
        Bar old = BARS.remove(player);
        if (old != null) old.event.removeAllPlayers();
        ServerBossEvent e = new ServerBossEvent(UUID.randomUUID(), barName(player, 0, true), BossEvent.BossBarColor.GREEN,
                BossEvent.BossBarOverlay.NOTCHED_10);
        e.setProgress(0f);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) e.addPlayer(p);
        BARS.put(player, new Bar(e));
        ServerPlayer runner = server.getPlayerList().getPlayerByName(player);
        if (runner != null) {
            Show.title(runner, Component.literal("Top départ !").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), null, 2, 20, 8);
        }
        Show.soundAll(server, SoundEvents.NOTE_BLOCK_PLING, 0.8f, 1.6f);
    }

    /** {@code ms}: the time the action measured (and wrote on the sheet), shown as is. */
    /**
     * A private result (a trial where nobody must know who finished): the player themself, the organisers and the
     * spectators (everyone who is not another contestant).
     */
    public static boolean toldPrivately(ServerPlayer p, String player) {
        if (p.getGameProfile().name().equalsIgnoreCase(player) || fr.lordfinn.craftmaster.Permissions.isAdmin(p)) return true;
        return contestant(p) == null;
    }

    private static void stopStopwatch(MinecraftServer server, String player, long ms, boolean quiet) {
        Stopwatch.SHARED.stop(player, System.currentTimeMillis());
        Bar bar = BARS.get(player);
        if (bar != null && ms >= 0) {
            bar.event.setName(barName(player, ms, false));
            bar.event.setColor(BossEvent.BossBarColor.YELLOW);
            bar.event.setProgress(1f);
            bar.removeAt = server.getTickCount() + 20 * 6;
            if (quiet) for (ServerPlayer p : server.getPlayerList().getPlayers()) if (!toldPrivately(p, player)) bar.event.removePlayer(p);
        }
        ServerPlayer runner = server.getPlayerList().getPlayerByName(player);
        if (runner != null && ms >= 0)
            Show.title(runner, Component.literal("Stop !").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.literal(Stopwatch.format(ms)).withStyle(ChatFormatting.YELLOW), 2, 30, 10);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (!quiet || toldPrivately(p, player)) Show.sound(p, SoundEvents.NOTE_BLOCK_BELL, 1f, 1.0f);
    }

    private static void resetStopwatch(String player) {
        if (player == null) {
            Stopwatch.SHARED.resetAll();
            BARS.values().forEach(b -> b.event.removeAllPlayers());
            BARS.clear();
            return;
        }
        String known = Stopwatch.SHARED.find(player);
        if (known != null) Stopwatch.SHARED.reset(known);
        Bar b = BARS.remove(known != null ? known : player);
        if (b != null) b.event.removeAllPlayers();
    }

    private static Component barName(String player, long ms, boolean running) {
        return Component.literal("⏱ " + player + "  ").withStyle(running ? ChatFormatting.GREEN : ChatFormatting.GOLD)
                .append(Component.literal(Stopwatch.format(ms)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
    }

    /** Called every tick: running bars show their time (tenths), finished ones go away after a few seconds. */
    public static void tick(MinecraftServer server) {
        if (BARS.isEmpty()) return;
        long now = System.currentTimeMillis();
        BARS.entrySet().removeIf(e -> {
            Bar b = e.getValue();
            if (server.getTickCount() >= b.removeAt) {
                b.event.removeAllPlayers();
                return true;
            }
            long ms = Stopwatch.SHARED.peek(e.getKey(), now);
            if (ms >= 0 && server.getTickCount() % 2 == 0) {
                b.event.setName(barName(e.getKey(), ms, true));
                // The notches fill once a minute
                b.event.setProgress((ms % 60_000) / 60_000f);
            }
            return false;
        });
    }

    /** Bars for the stopwatches put back after a restart ({@link Stopwatch#restore}). */
    public static void restoreBars(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (String player : Stopwatch.SHARED.runningPlayers()) {
            if (BARS.containsKey(player)) continue;
            long ms = Math.max(0, Stopwatch.SHARED.peek(player, now));
            ServerBossEvent e = new ServerBossEvent(UUID.randomUUID(), barName(player, ms, true), BossEvent.BossBarColor.GREEN,
                    BossEvent.BossBarOverlay.NOTCHED_10);
            e.setProgress((ms % 60_000) / 60_000f);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) e.addPlayer(p);
            BARS.put(player, new Bar(e));
        }
    }

    public static void onJoin(ServerPlayer p) {
        BARS.values().forEach(b -> b.event.addPlayer(p));
    }

    public static void reset() {
        BARS.values().forEach(b -> b.event.removeAllPlayers());
        BARS.clear();
        Stopwatch.SHARED.resetAll();
    }

    // ---------------------------------------------------------------- helpers for the tools

    /** The active season's contestants (not archived), in show order. */
    public static List<String> contestants() {
        Season s = CraftMaster.data() == null ? null : CraftMaster.data().activeSeason();
        List<String> out = new ArrayList<>();
        if (s != null) for (SeasonPlayer p : s.players.values()) if (!p.archived) out.add(p.name);
        return out;
    }

    /** The contestant of the season with this game name (ignoring case), or null. */
    public static String contestant(ServerPlayer p) {
        String name = p.getGameProfile().name();
        for (String c : contestants()) if (c.equalsIgnoreCase(name)) return c;
        return null;
    }

    /** The current round, or null. */
    public static Manche currentManche() {
        return CraftMaster.data() == null ? null : CraftMaster.data().currentManche();
    }

    /**
     * A measure for the organiser: "⚖ 12,4 kg" then one clickable "[Ensuri]" button per contestant that writes it
     * on the current round's results sheet (with its kind). Others just get the measure.
     */
    public static void offerMeasure(ServerPlayer to, Component measure, double value, ResultSheet.Kind kind) {
        to.sendSystemMessage(measure);
        if (!Permissions.isAdmin(to)) return;
        Manche m = currentManche();
        List<String> cast = contestants();
        if (m == null || cast.isEmpty()) {
            to.sendSystemMessage(Component.literal("  (lancez une manche pour noter ce résultat)").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        MutableComponent line = Component.literal("  Noter pour : ").withStyle(ChatFormatting.GRAY);
        String v = String.format(Locale.ROOT, "%.2f", value);
        for (String p : cast) {
            String cmd = "/cm epreuve feuille regler " + quote(p) + " " + v + " " + kind.id;
            line.append(Component.literal("[" + p + "]").withStyle(s -> s.withColor(ChatFormatting.GOLD)
                    .withClickEvent(new ClickEvent.RunCommand(cmd))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Noter " + ResultSheet.format(kind, value) + " pour " + p
                            + " (" + m.name + ")")))));
            line.append(Component.literal(" "));
        }
        to.sendSystemMessage(line);
    }

    /** Quotes a name for a command if it needs it. */
    public static String quote(String s) {
        return s.matches("[A-Za-z0-9_.+-]+") ? s : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
