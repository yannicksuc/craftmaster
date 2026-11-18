package fr.lordfinn.craftmaster.task;

import com.mojang.brigadier.Command;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.show.Actions;
import fr.lordfinn.craftmaster.show.Show;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * When a round's timer starts ({@link Manche#timerStart}): at the launch (as before), when a player of the round
 * first reads its letter, or when one clicks « Commencer l'épreuve » in it. In that last mode every player of the
 * round stays where they were teleported, unable to move, break, place or use anything but the letter, until they
 * click it themselves; the letter opens on their screen at the launch (J reopens it), and the chat has the same
 * button for games without the mod. The first click (or reading) starts the round's timer for everyone, and the
 * timer tablets that follow the round. An organiser can start it at once ({@code timer.go}: it frees everyone).
 */
public final class RoundStart {

    private RoundStart() {}

    public static final String LAUNCH = "lancement", LETTER = "lettre", BUTTON = "bouton";
    public static final List<String> MODES = List.of(LAUNCH, LETTER, BUTTON);

    /** "lettre", "à l'ouverture", "button"... → the stored mode, or null. */
    public static String parse(String s) {
        if (s == null) return null;
        String k = fr.lordfinn.craftmaster.camera.CameraList.fold(s).replaceAll("[^a-z]+", " ").strip();
        return switch (k) {
            case "lancement", "launch", "start", "debut", "depart", "manche", "au lancement", "tout de suite", "aucun" -> LAUNCH;
            case "lettre", "letter", "ouverture", "a l ouverture", "lecture", "enveloppe", "envelope", "open" -> LETTER;
            case "bouton", "button", "clic", "click", "commencer", "fige", "figes", "freeze" -> BUTTON;
            default -> null;
        };
    }

    /** French words for messages. */
    public static String describe(String mode) {
        return switch (mode == null ? LAUNCH : mode) {
            case LETTER -> "à l'ouverture de la lettre";
            case BUTTON -> "au bouton « Commencer » de la lettre (joueurs figés jusque-là)";
            default -> "au lancement";
        };
    }

    private static final net.minecraft.resources.Identifier FREEZE = CraftMaster.id("round_freeze");

    /** The round waiting for its go, or null. */
    private record Waiting(int season, String mancheId, String name, int seconds, String mode, Set<String> cast) {}

    private static Waiting waiting;
    /** Players held still (button mode), where they stand. */
    private static final Map<UUID, Vec3> FROZEN = new HashMap<>();
    /** Those who clicked already: a reconnection does not freeze them again. */
    private static final Set<UUID> RELEASED = new HashSet<>();
    private static int reminder;
    private static final Map<UUID, Integer> HINTED = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(RoundStart::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            waiting = null;
            FROZEN.clear();
            RELEASED.clear();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> onJoin(handler.getPlayer())));
        ServerPlayNetworking.registerGlobalReceiver(TaskNetwork.StartRound.TYPE, (p, ctx) -> clicked(ctx.player(), p.season(), p.mancheId()));
        // A frozen player only reads: the letter (item or block), nothing else
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> !frozen(player)
                || level.getBlockState(hit.getBlockPos()).getBlock() instanceof EnvelopeBlock ? InteractionResult.PASS : held(player));
        UseItemCallback.EVENT.register((player, level, hand) -> !frozen(player)
                || player.getItemInHand(hand).getItem() instanceof EnvelopeItem ? InteractionResult.PASS : held(player));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> frozen(player) ? held(player) : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> frozen(player) ? held(player) : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> frozen(player) ? held(player) : InteractionResult.PASS);
        // The same button in the chat (games without the mod), for the players of the round only
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("craftMasterGo")
                .executes(c -> {
                    if (c.getSource().getEntity() instanceof ServerPlayer p && waiting != null) clicked(p, waiting.season, waiting.mancheId);
                    return Command.SINGLE_SUCCESS;
                })));
    }

    private static InteractionResult held(Player player) {
        if (player instanceof ServerPlayer sp) hint(sp);
        return InteractionResult.FAIL;
    }

    public static boolean frozen(Player player) {
        return !player.level().isClientSide() && FROZEN.containsKey(player.getUUID());
    }

    /** A round waits for its go (its timer starts on a letter). */
    public static boolean waiting() {
        return waiting != null;
    }

    /** The player must still click the start button of this round's letter. */
    public static boolean awaitsButton(ServerPlayer p, int season, String mancheId) {
        return waiting != null && waiting.mode.equals(BUTTON) && waiting.season == season && waiting.mancheId.equals(mancheId)
                && FROZEN.containsKey(p.getUUID());
    }

    // ---------------------------------------------------------------- the round's start

    /**
     * The round starts (its teleports are done): its timer now, or held until a letter is read / its button clicked.
     *
     * @return true if the timer is held
     */
    public static boolean arm(MinecraftServer server, Actions.MancheStarted m) {
        clear(server);
        String mode = m.timerStart() == null ? LAUNCH : m.timerStart();
        if (mode.equals(LAUNCH) || (mode.equals(LETTER) && m.timer() <= 0)) return false;
        Set<String> cast = new HashSet<>();
        for (String n : m.cast()) cast.add(n.toLowerCase(Locale.ROOT));
        waiting = new Waiting(m.season(), m.mancheId(), m.name(), m.timer(), mode, cast);
        reminder = 0;
        HINTED.clear();
        CraftMaster.LOG.info("[Manche] « {} » : chrono {}", m.name(), describe(mode));
        if (mode.equals(BUTTON)) for (ServerPlayer p : server.getPlayerList().getPlayers()) if (inCast(p)) holdAndOpen(p);
        return true;
    }

    private static boolean inCast(ServerPlayer p) {
        return waiting != null && waiting.cast.contains(p.getGameProfile().name().toLowerCase(Locale.ROOT));
    }

    /** Freezes the player where they stand and puts the round's letter in front of them. */
    private static void holdAndOpen(ServerPlayer p) {
        if (RELEASED.contains(p.getUUID())) return;
        FROZEN.put(p.getUUID(), p.position());
        setFrozen(p, true);
        Tasks.openStartLetter(p, waiting.season, waiting.mancheId, waiting.name);
        p.sendSystemMessage(Component.literal("Lis la tâche, puis ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("[Commencer l'épreuve]").withStyle(st -> st.withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent.RunCommand("/cm go"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Lance l'épreuve : tu pourras bouger")))))
                .append(Component.literal(" (J pour relire la lettre)").withStyle(ChatFormatting.GRAY)));
    }

    private static void setFrozen(ServerPlayer p, boolean on) {
        for (var attr : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
            AttributeInstance a = p.getAttribute(attr);
            if (a == null) continue;
            if (on) a.addOrUpdateTransientModifier(new AttributeModifier(FREEZE, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            else a.removeModifier(FREEZE);
        }
    }

    private static void onJoin(ServerPlayer p) {
        // Left frozen when the server stopped or the round moved on
        setFrozen(p, false);
        if (waiting != null && waiting.mode.equals(BUTTON) && inCast(p)) holdAndOpen(p);
    }

    /** A player of the round read its letter (lettre mode: the timer starts). */
    static void letterRead(ServerPlayer p, int season, String mancheId) {
        if (waiting == null || !waiting.mode.equals(LETTER) || waiting.season != season || !waiting.mancheId.equals(mancheId) || !inCast(p)) return;
        go(p.level().getServer(), p);
    }

    /** « Commencer l'épreuve » clicked: the player is free, and the first click starts the timer. */
    static void clicked(ServerPlayer p, int season, String mancheId) {
        if (waiting == null || !waiting.mode.equals(BUTTON) || waiting.season != season || !waiting.mancheId.equals(mancheId)) return;
        if (FROZEN.remove(p.getUUID()) == null) return;
        RELEASED.add(p.getUUID());
        setFrozen(p, false);
        Show.sound(p, SoundEvents.NOTE_BLOCK_BELL, 1f, 1.4f);
        p.sendOverlayMessage(Component.literal("C'est parti !").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        MinecraftServer server = p.level().getServer();
        if (!timerStarted) go(server, p);
        if (FROZEN.isEmpty()) waiting = null;
    }

    private static boolean timerStarted;

    /** Starts the waiting round's timer ({@code by}: who started it, or null for the organiser). */
    private static void go(MinecraftServer server, ServerPlayer by) {
        if (waiting == null || timerStarted) return;
        timerStarted = true;
        Waiting w = waiting;
        if (w.seconds > 0) Show.startTimer(server, w.name, w.seconds);
        fr.lordfinn.craftmaster.studio.TimerTabletBlockEntity.onRoundStart(server);
        Show.soundAll(server, SoundEvents.NOTE_BLOCK_BELL, 1f, 1.2f);
        Component msg = Component.literal(by == null ? "Chrono lancé" : "Chrono lancé par " + by.getGameProfile().name()).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (p != by) p.sendOverlayMessage(msg);
        CraftMaster.LOG.info("[Manche] « {} » : chrono lancé{}", w.name, by == null ? " par la régie" : " par " + by.getGameProfile().name());
        if (!w.mode.equals(BUTTON)) waiting = null;
    }

    /**
     * The organiser's go ({@code timer.go}): the waiting round's timer starts now and everyone is free.
     *
     * @return false if no round was waiting
     */
    public static boolean goNow(MinecraftServer server) {
        if (waiting == null) return false;
        go(server, null);
        release(server);
        waiting = null;
        return true;
    }

    /** Round ended, timer stopped, another round: nobody waits nor stays frozen any more. */
    public static void clear(MinecraftServer server) {
        release(server);
        waiting = null;
        timerStarted = false;
        RELEASED.clear();
    }

    private static void release(MinecraftServer server) {
        for (UUID id : FROZEN.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) setFrozen(p, false);
        }
        FROZEN.clear();
    }

    private static void tick(MinecraftServer server) {
        if (FROZEN.isEmpty()) return;
        reminder++;
        for (Map.Entry<UUID, Vec3> e : FROZEN.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            Vec3 at = e.getValue();
            // Pushed, flying, falling off: back where they stand (the look stays free)
            if (p.position().distanceToSqr(at) > 0.04) {
                p.teleportTo(p.level(), at.x, at.y, at.z, Set.of(), p.getYRot(), p.getXRot(), false);
                hint(p);
            }
            // Trying to walk or jump (nothing happens: speed and jump are at zero): say why
            var in = p.getLastClientInput();
            if (in.forward() || in.backward() || in.left() || in.right() || in.jump()) hint(p);
        }
    }

    /** Says why they cannot move (at most every two seconds: not over the letter while they read it). */
    private static void hint(ServerPlayer p) {
        Integer last = HINTED.get(p.getUUID());
        if (last != null && reminder - last < 40) return;
        HINTED.put(p.getUUID(), reminder);
        p.sendOverlayMessage(Component.literal("Clique sur « Commencer l'épreuve » dans la lettre (J pour la rouvrir)").withStyle(ChatFormatting.YELLOW));
    }

    /** Lines for the organisers' status. */
    public static List<String> status(MinecraftServer server) {
        if (waiting == null) return List.of();
        String who = FROZEN.isEmpty() ? "" : " — " + FROZEN.size() + " joueur(s) figé(s)";
        return List.of("Chrono de « " + waiting.name + " » " + (timerStarted ? "lancé" : "en attente") + " " + describe(waiting.mode) + who + ".");
    }
}
