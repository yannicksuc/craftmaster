package fr.lordfinn.craftmaster.replay;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork;
import fr.lordfinn.craftmaster.broadcast.Director;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.show.Actions;
import fr.lordfinn.craftmaster.show.Show;
import fr.lordfinn.craftmaster.studio.DeskBellBlock;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The key moments of a recording, as typed markers on its timeline: each has a kind (its icon on the replay's bar),
 * the player it is about (their colour, the filters) and the zone they were in. Besides the show's events
 * ({@link Replays}), it notes deaths, results and disqualifications, stopwatch times, the end of the round timer,
 * the desk bells, and the flags: anyone presses a key at a moment worth seeing again (an organiser's flag takes the
 * colour of who is on air, neutral on a wide shot; a player's flag has its own shape and a delay between two).
 */
public final class ReplayMoments {

    private ReplayMoments() {}

    // The kinds (the client draws one icon each); "" = an old marker, a plain tick
    public static final String ROUND = "round", SCORE = "score", SPOTLIGHT = "spotlight", TASK = "task", REVEAL = "reveal",
            OUTING = "outing", BACK = "back", DEATH = "death", RESULT = "result", DQ = "dq", TIME = "time", TIMER = "timer",
            BELL = "bell", FLAG = "flag", PLAYER_FLAG = "player_flag";
    public static final List<String> KINDS = List.of(ROUND, SCORE, SPOTLIGHT, TASK, REVEAL, OUTING, BACK, DEATH, RESULT, DQ,
            TIME, TIMER, BELL, FLAG, PLAYER_FLAG);

    /** One colour per contestant, in the season's order: far apart, all readable on the dark shade of the bar. */
    static final int[] PALETTE = {0xE57373, 0x64B5F6, 0x81C784, 0xFFD54F, 0xBA68C8, 0x4DD0E1, 0xFF8A65, 0xA1887F,
            0xF06292, 0x9575CD, 0xAED581, 0x4DB6AC, 0xFFB74D, 0x90A4AE, 0xDCE775, 0x7986CB};
    /** Moments about no one in particular (and an organiser's flag on a wide shot). */
    public static final int NEUTRAL = 0xEDE3C8, GOLD = 0xF2C14E;
    /** A player's two flags are this far apart at least (ms); an organiser's, just against a double press. */
    static final long PLAYER_FLAG_GAP_MS = 20_000, ORGANISER_FLAG_GAP_MS = 1_500;
    /** At most this many flags per player and recording. */
    static final int PLAYER_FLAGS_MAX = 30;

    private static final Map<UUID, Long> LAST_FLAG = new HashMap<>();
    private static final Map<UUID, Integer> FLAGS = new HashMap<>();
    private static int flagCount;
    private static int lastTimerLeft = -1;
    private static long lastBellTick = -1;

    static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer p) || !Replays.recording()) return;
            String name = p.getGameProfile().name();
            if (!Replays.isContestant(name)) return;
            Replays.marker(name + " meurt", colorOf(name), DEATH, name);
        });
        // The desk bell: read here, the block itself is left as it is
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer p) || !Replays.recording()) return InteractionResult.PASS;
            BlockState state = level.getBlockState(hit.getBlockPos());
            if (!(state.getBlock() instanceof DeskBellBlock) || state.getValue(DeskBellBlock.POWERED) || p.isSecondaryUseActive())
                return InteractionResult.PASS;
            long tick = level.getServer().getTickCount();
            if (tick - lastBellTick < 5) return InteractionResult.PASS;
            lastBellTick = tick;
            String name = p.getGameProfile().name();
            Replays.marker(name + " sonne la cloche", colorOf(name), BELL, name);
            return InteractionResult.PASS;
        });
        // Registered here, with or without Server Replay: the key works whenever CraftMaster records
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(ReplayNetwork.ReplayFlag.TYPE, ReplayNetwork.ReplayFlag.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ReplayNetwork.ReplayFlag.TYPE, (payload, ctx) -> {
            String said = flag(ctx.player(), payload.note());
            ctx.player().sendOverlayMessage(Component.literal(said));
        });
    }

    /** A recording starts: the flags' counters and the timer's state start again. */
    static void reset() {
        LAST_FLAG.clear();
        FLAGS.clear();
        flagCount = 0;
        lastTimerLeft = -1;
    }

    /** Once a second while recording: the round timer reaching zero. */
    static void everySecond(MinecraftServer server) {
        int left = Show.timerRemaining(server);
        if (lastTimerLeft > 0 && (left == 0 || left < 0 && lastTimerLeft <= 2))
            Replays.marker("Fin du chrono" + (Show.timerName() == null ? "" : " — " + Show.timerName()), GOLD, TIMER, null);
        lastTimerLeft = left;
    }

    /** The show's actions with a moment worth marking (results, disqualifications, stopwatch times). */
    public static void onEffect(MinecraftServer server, Actions.Effect effect) {
        if (!Replays.recording()) return;
        switch (effect) {
            case Actions.ResultRecorded r when r.dq() -> Replays.marker(r.player() + " disqualifié", colorOf(r.player()), DQ, r.player());
            // A stopwatch stopped writes its time as the result too: one moment, the stopwatch's
            case Actions.ResultRecorded r -> {
                if (!justTimed(r.player(), RESULT))
                    Replays.marker(r.player() + " : " + r.text(), colorOf(r.player()), RESULT, r.player());
            }
            case Actions.StopwatchStopped w when w.ms() >= 0 -> {
                if (!justTimed(w.player(), TIME))
                    Replays.marker(w.player() + " ⏱ " + fr.lordfinn.craftmaster.trial.Stopwatch.format(w.ms()), colorOf(w.player()), TIME, w.player());
            }
            default -> {}
        }
    }

    private static String lastTimed;
    private static String lastTimedKind = "";
    private static long lastTimedAt;

    /** True when the player's result (or time) was marked just before by the other one of the pair. */
    private static boolean justTimed(String player, String kind) {
        long now = System.currentTimeMillis();
        boolean twin = player.equalsIgnoreCase(lastTimed == null ? "" : lastTimed) && !lastTimedKind.equals(kind) && now - lastTimedAt < 1_000;
        lastTimed = player;
        lastTimedKind = kind;
        lastTimedAt = now;
        return twin;
    }

    /**
     * A flag at this moment, by {@code by} (key, {@code /cmreplay drapeau}, voice, Stream Deck; null = the console):
     * an organiser's takes the colour of who is on air, a player's has its own shape. What to tell them.
     */
    public static String flag(ServerPlayer by, String note) {
        if (!Replays.recording()) return "Aucun enregistrement en cours.";
        boolean organiser = by == null || Permissions.isAdmin(by);
        long now = System.currentTimeMillis();
        if (by != null) {
            Long last = LAST_FLAG.get(by.getUUID());
            long gap = organiser ? ORGANISER_FLAG_GAP_MS : PLAYER_FLAG_GAP_MS;
            if (last != null && now - last < gap)
                return organiser ? "Drapeau déjà posé." : "Encore " + (gap - (now - last) + 999) / 1000 + " s avant un autre drapeau.";
            if (!organiser && FLAGS.getOrDefault(by.getUUID(), 0) >= PLAYER_FLAGS_MAX) return "Plus de drapeau pour cette manche.";
            LAST_FLAG.put(by.getUUID(), now);
            if (!organiser) FLAGS.merge(by.getUUID(), 1, Integer::sum);
        }
        String who = by == null ? "régie" : by.getGameProfile().name();
        String text = note == null || note.isBlank() ? "" : " : " + (note.length() > 40 ? note.substring(0, 40) : note.strip());
        // Numbered: two flags never share a name (the index finds each one's colour and kind by it)
        String name = "Drapeau " + ++flagCount + " (" + who + ")" + text;
        if (organiser) {
            String onAir = onAir();
            Replays.marker(name, onAir == null ? NEUTRAL : colorOf(onAir), FLAG, onAir);
        } else {
            Replays.marker(name, colorOf(who), PLAYER_FLAG, who);
        }
        CraftMaster.LOG.info("[Replay] {}", name);
        return "⚑ Drapeau posé";
    }

    /** The player on air (followed or seen through their eyes), or null on a camera, a travelling, a mosaic. */
    static String onAir() {
        BroadcastNetwork.Shot s = Director.shot();
        if ((s.kind() == BroadcastNetwork.SHOT_FOLLOW || s.kind() == BroadcastNetwork.SHOT_POV) && !s.player().isEmpty()) return s.player();
        return null;
    }

    /** The contestant's colour: their place in the recorded season's cast; neutral for anyone else. */
    public static int colorOf(String player) {
        if (player == null) return NEUTRAL;
        Season s = CraftMaster.data() == null ? null : CraftMaster.data().season(Replays.recordingSeason());
        if (s == null) return NEUTRAL;
        int i = 0;
        for (String n : s.players.keySet()) {
            if (n.equalsIgnoreCase(player)) return PALETTE[i % PALETTE.length];
            i++;
        }
        return NEUTRAL;
    }
}
