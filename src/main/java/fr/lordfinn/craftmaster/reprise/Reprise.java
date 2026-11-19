package fr.lordfinn.craftmaster.reprise;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.broadcast.Director;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Live;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.data.SeasonPlayer;
import fr.lordfinn.craftmaster.show.Actions;
import fr.lordfinn.craftmaster.show.Show;
import fr.lordfinn.craftmaster.studio.TabletClock;
import fr.lordfinn.craftmaster.studio.ClockBlockEntity;
import fr.lordfinn.craftmaster.trial.BuzzerLock;
import fr.lordfinn.craftmaster.trial.Buzzers;
import fr.lordfinn.craftmaster.trial.Judging;
import fr.lordfinn.craftmaster.trial.Stopwatch;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The show after a crash: a server restarted while a round was being played does not start again by itself. It is
 * frozen — players can neither move nor act, mobs and every entity stand still (the game's own tick freeze), every
 * clock (round timer, timer tablets, contestants' stopwatches, buzzers' delays) stands where it was at the crash —
 * until the organiser gives the go ({@code /cm reprise reprendre}, refused while a contestant is missing
 * unless forced). Then each clock goes on with exactly the time it had left.
 *
 * <p>For that, the clocks are saved every second in {@code config/craftmaster/reprise.json} ({@link RepriseState}).
 * Organisers who are not contestants, and spectators (the broadcast cameras), stay free to move.
 */
public final class Reprise {

    private Reprise() {}

    /** A save a second at most. */
    private static final int SAVE_TICKS = 20;
    private static final Identifier HOLD = CraftMaster.id("reprise_hold");
    private static final Identifier EARLY = CraftMaster.id("reprise");
    private static final List<Holder<Attribute>> HELD_ATTRIBUTES = List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH,
            Attributes.BLOCK_INTERACTION_RANGE, Attributes.ENTITY_INTERACTION_RANGE);

    private static Path file;
    private static boolean frozen;
    /** Why ("redémarrage", "manuel") and since when (epoch ms). */
    private static String reason = "";
    private static long frozenAt;
    /** The game's tick freeze was set by us (an organiser's own /tick freeze is left alone). */
    private static boolean weFroze;
    /** Tablets paused by the freeze, started again at the go. */
    private static final Set<GlobalPos> tabletsToResume = new HashSet<>();
    /**
     * Tablets whose chunk is not loaded (saved before the restart, or unloaded since: nobody near): their time,
     * still counting while the show runs, put back when they load and kept in the save.
     */
    private static final Map<GlobalPos, Away> away = new LinkedHashMap<>();
    /** Tablets loaded at the last save, as they were then (remembered once their chunk unloads). */
    private static Map<GlobalPos, RepriseState.Tablet> seen = new HashMap<>();
    private static long seenAt;

    /** A tablet away: its state at {@code at} (epoch ms). */
    private static final class Away {
        final RepriseState.Tablet t;
        long at;

        Away(RepriseState.Tablet t, long at) {
            this.t = t;
            this.at = at;
        }

        /** Its time at {@code now}: a running tablet counts on, except while the show is frozen. */
        long elapsed(long now) {
            return t.elapsedMs + (t.running && !frozen ? Math.max(0, now - at) : 0);
        }
    }
    /** Where each held player must stay. */
    private static final Map<UUID, Vec3> anchors = new HashMap<>();
    private static String lastWritten;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CraftMaster reprise");
        t.setDaemon(true);
        return t;
    });

    public static boolean frozen() {
        return frozen;
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(Reprise::onStarted);
        // Registered before the other mods' parts: saved before they forget their clocks
        ServerLifecycleEvents.SERVER_STOPPING.register(Reprise::onStopping);
        ServerTickEvents.END_SERVER_TICK.register(Reprise::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(server, handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onLeave(server, handler.getPlayer()));

        // Held players do nothing: these come before every other listener (the measuring tools…)
        UseBlockCallback.EVENT.register(EARLY, (player, level, hand, hit) -> held(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(EARLY, (player, level, hand) -> held(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseItemCallback.EVENT.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.register(EARLY, (player, level, hand, entity, hit) -> held(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
        AttackBlockCallback.EVENT.register(EARLY, (player, level, hand, pos, dir) -> held(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackBlockCallback.EVENT.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
        AttackEntityCallback.EVENT.register(EARLY, (player, level, hand, entity, hit) -> held(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> !held(player));
        // Nobody gets hurt while frozen (fire, drowning, falls, mobs…)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !frozen);
    }

    // ---------------------------------------------------------------- server start and stop

    private static void onStarted(MinecraftServer server) {
        file = FabricLoader.getInstance().getConfigDir().resolve("craftmaster/reprise.json");
        lastWritten = null;
        CraftMasterData data = CraftMaster.data();
        Live.Current cur = data == null ? null : data.live().current;
        if (cur == null) return;
        RepriseState saved = read();
        freeze(server, "redémarrage");
        if (saved == null) {
            CraftMaster.LOG.warn("[Reprise] Une manche était en cours mais aucun état des chronos n'a été trouvé ({}).", file);
            return;
        }
        if (!saved.sameRound(cur.season(), cur.partie(), cur.manche())) {
            CraftMaster.LOG.warn("[Reprise] L'état des chronos ({}) est celui d'une autre manche : seules les tablettes sont remises.", file);
            restoreTablets(saved);
            return;
        }
        restore(server, saved);
        CraftMaster.LOG.info("[Reprise] Show gelé après le redémarrage ; chronos remis à l'état du {} (/cm reprise).",
                java.time.Instant.ofEpochMilli(saved.savedAt));
    }

    private static void onStopping(MinecraftServer server) {
        if (file != null) {
            RepriseState s = snapshot(server);
            s.savedAt = System.currentTimeMillis();
            String json = s.toJson();
            // After the saves already queued, and waited for: the process may end right after
            try {
                IO.submit(() -> write(json)).get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                CraftMaster.LOG.warn("[Reprise] Dernière sauvegarde des chronos non terminée : {}", e.toString());
            }
        }
        long now = System.currentTimeMillis();
        Stopwatch.SHARED.thaw(now);
        Buzzers.LOCK.thaw(now);
        if (weFroze) server.tickRateManager().setFrozen(false);
        frozen = false;
        weFroze = false;
        tabletsToResume.clear();
        away.clear();
        seen = new HashMap<>();
        anchors.clear();
        reason = "";
    }

    private static void restore(MinecraftServer server, RepriseState s) {
        long now = System.currentTimeMillis();
        if (s.timer != null) Show.restoreTimer(server, s.timer.name, s.timer.totalTicks, s.timer.leftTicks);
        Stopwatch.SHARED.restore(s.stopwatches, s.lastTimes, now);
        Judging.restoreBars(server);
        List<BuzzerLock.Press> presses = new ArrayList<>();
        for (RepriseState.Buzz b : s.buzzes) presses.add(new BuzzerLock.Press(b.order, b.player, b.delayMs));
        if (!presses.isEmpty()) Buzzers.LOCK.restore(presses, Math.max(0, s.sinceFirstBuzz), now);
        List<GlobalPos> lit = new ArrayList<>();
        for (RepriseState.Place p : s.litBuzzers) {
            GlobalPos g = pos(p);
            if (g != null) lit.add(g);
        }
        Buzzers.restoreLit(lit);
        restoreTablets(s);
    }

    private static void restoreTablets(RepriseState s) {
        away.clear();
        long now = System.currentTimeMillis();
        for (RepriseState.Tablet t : s.tablets) {
            GlobalPos g = pos(t);
            if (g != null) away.put(g, new Away(t, now));
        }
    }

    // ---------------------------------------------------------------- freeze and go

    /** Freezes the show (players, entities, every clock). @return false if it already was */
    public static boolean freeze(MinecraftServer server, String why) {
        if (frozen) return false;
        long now = System.currentTimeMillis();
        // Tablets away stop counting too
        for (Away a : away.values()) {
            a.t.elapsedMs = a.elapsed(now);
            a.at = now;
        }
        frozen = true;
        reason = why;
        frozenAt = now;
        if (!server.tickRateManager().isFrozen()) {
            server.tickRateManager().setFrozen(true);
            weFroze = true;
        }
        Show.pauseTimer(server);
        Stopwatch.SHARED.freeze(frozenAt);
        Buzzers.LOCK.freeze(frozenAt);
        pauseTablets();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) hold(p);
        Director.sync(server);
        save(server);
        return true;
    }

    /**
     * The go: every clock starts again from where it stood, players are free. Refused while a contestant is missing
     * unless {@code force}. @return the missing contestants when refused, else an empty list
     */
    public static List<String> resume(MinecraftServer server, boolean force) {
        List<String> missing = missing(server);
        if (!frozen || (!missing.isEmpty() && !force)) return missing;
        long now = System.currentTimeMillis();
        frozen = false;
        reason = "";
        if (weFroze) server.tickRateManager().setFrozen(false);
        weFroze = false;
        Show.resumeTimer(server);
        Stopwatch.SHARED.thaw(now);
        Buzzers.LOCK.thaw(now);
        for (ClockBlockEntity t : serverTablets()) {
            GlobalPos g = key(t);
            if (tabletsToResume.remove(g)) t.control("start");
        }
        // Tablets not loaded count on from now (they start when they load)
        for (GlobalPos g : tabletsToResume) {
            Away a = away.get(g);
            if (a != null) a.t.running = true;
        }
        for (Away a : away.values()) a.at = now;
        tabletsToResume.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) release(p);
        anchors.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendOverlayMessage(Component.literal("▶ Reprise").withStyle(ChatFormatting.GRAY));
            Show.sound(p, SoundEvents.NOTE_BLOCK_CHIME, 0.6f, 1.3f);
        }
        Director.sync(server);
        save(server);
        return List.of();
    }

    /** Running tablets stand still (remembered to start again at the go). */
    private static void pauseTablets() {
        for (ClockBlockEntity t : serverTablets()) {
            if (!t.clock().running()) continue;
            tabletsToResume.add(key(t));
            t.control("pause");
        }
    }

    private static List<ClockBlockEntity> serverTablets() {
        List<ClockBlockEntity> out = new ArrayList<>();
        for (ClockBlockEntity t : ClockBlockEntity.loaded())
            if (t.getLevel() instanceof ServerLevel && !t.isRemoved()) out.add(t);
        return out;
    }

    // ---------------------------------------------------------------- players

    /** Contestants are held, and anyone who is not an organiser; spectators (cameras) never. */
    private static boolean holds(ServerPlayer p) {
        if (p.isSpectator()) return false;
        return !Permissions.isAdmin(p) || isContestant(p.getGameProfile().name());
    }

    private static boolean held(Player player) {
        return frozen && player instanceof ServerPlayer sp && anchors.containsKey(sp.getUUID());
    }

    private static void hold(ServerPlayer p) {
        if (!holds(p)) return;
        for (Holder<Attribute> a : HELD_ATTRIBUTES) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst != null && !inst.hasModifier(HOLD))
                inst.addTransientModifier(new AttributeModifier(HOLD, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        anchors.put(p.getUUID(), p.position());
    }

    private static void release(ServerPlayer p) {
        for (Holder<Attribute> a : HELD_ATTRIBUTES) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst != null) inst.removeModifier(HOLD);
        }
    }

    private static void onJoin(MinecraftServer server, ServerPlayer p) {
        if (!frozen) return;
        hold(p);
        if (anchors.containsKey(p.getUUID())) p.sendOverlayMessage(Component.literal("⏸ Pause").withStyle(ChatFormatting.GRAY));
        String name = p.getGameProfile().name();
        if (Permissions.isAdmin(p)) {
            for (Component line : status(server, name)) p.sendSystemMessage(line);
        }
        if (!isContestant(name)) return;
        List<String> missing = missing(server, name);
        Component msg = missing.isEmpty()
                ? Component.literal("⏸ " + name + " est revenu — tout le monde est là. ").withStyle(ChatFormatting.GREEN).append(resumeButton(false))
                : Component.literal("⏸ " + name + " est revenu — manque encore : " + String.join(", ", missing)).withStyle(ChatFormatting.GOLD);
        tellOrganisers(server, msg, p);
    }

    private static void onLeave(MinecraftServer server, ServerPlayer p) {
        anchors.remove(p.getUUID());
        if (frozen && isContestant(p.getGameProfile().name()))
            tellOrganisers(server, Component.literal("⏸ " + p.getGameProfile().name() + " s'est déconnecté.").withStyle(ChatFormatting.GOLD), p);
    }

    private static void tellOrganisers(MinecraftServer server, Component msg, ServerPlayer except) {
        for (ServerPlayer o : server.getPlayerList().getPlayers())
            if (o != except && Permissions.isAdmin(o)) o.sendSystemMessage(msg);
        CraftMaster.LOG.info("[Reprise] {}", msg.getString());
    }

    // ---------------------------------------------------------------- every tick

    private static void tick(MinecraftServer server) {
        if (!away.isEmpty()) applyAwayTablets();
        if (frozen) {
            pauseTablets();
            holdInPlace(server);
        }
        if (server.getTickCount() % SAVE_TICKS == 0) save(server);
    }

    /** Tablets away that have loaded get their time back (paused while frozen). */
    private static void applyAwayTablets() {
        long now = System.currentTimeMillis();
        for (ClockBlockEntity t : serverTablets()) {
            GlobalPos g = key(t);
            Away a = away.remove(g);
            if (a == null) continue;
            long elapsed = a.elapsed(now);
            if (frozen) {
                t.clock().set(false, elapsed, now);
                t.control("pause");
                if (a.t.running) tabletsToResume.add(g);
            } else if (a.t.running) {
                t.clock().set(true, elapsed, now);
                t.control("start");
            } else {
                t.clock().set(false, elapsed, now);
                t.control("pause");
            }
        }
    }

    /** Held players stay where they are (falling is allowed); a reminder in their action bar now and then. */
    private static void holdInPlace(MinecraftServer server) {
        boolean remind = server.getTickCount() % 40 == 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Vec3 a = anchors.get(p.getUUID());
            if (a == null) {
                // Became a contestant, or no longer a spectator
                if (holds(p)) hold(p);
                continue;
            }
            if (!holds(p)) {
                release(p);
                anchors.remove(p.getUUID());
                continue;
            }
            Vec3 at = p.position();
            if (at.y < a.y) anchors.put(p.getUUID(), a = new Vec3(a.x, at.y, a.z));
            double dx = at.x - a.x, dz = at.z - a.z;
            if (dx * dx + dz * dz > 0.25 || at.y > a.y + 1.3)
                p.teleportTo(p.level(), a.x, Math.min(at.y, a.y), a.z, Set.of(), p.getYRot(), p.getXRot(), false);
            if (remind) p.sendOverlayMessage(Component.literal("⏸ Pause").withStyle(ChatFormatting.GRAY));
        }
    }

    // ---------------------------------------------------------------- saving

    /** The clocks as they stand now. */
    static RepriseState snapshot(MinecraftServer server) {
        long now = System.currentTimeMillis();
        RepriseState s = new RepriseState();
        s.frozen = frozen;
        CraftMasterData data = CraftMaster.data();
        Live.Current cur = data == null ? null : data.live().current;
        if (cur != null) {
            s.season = cur.season();
            s.partie = cur.partie();
            s.manche = cur.manche();
        }
        if (Show.timerRunning())
            s.timer = new RepriseState.Timer(Show.timerName(), Show.timerTotalTicks(), Show.timerLeftTicks(server));
        s.stopwatches.putAll(Stopwatch.SHARED.runningTimes(now));
        s.lastTimes.putAll(Stopwatch.SHARED.lastTimes());
        for (BuzzerLock.Press p : Buzzers.LOCK.presses()) s.buzzes.add(new RepriseState.Buzz(p.order(), p.player(), p.delayMs()));
        s.sinceFirstBuzz = Buzzers.LOCK.sinceFirst(now);
        for (GlobalPos g : Buzzers.lit()) s.litBuzzers.add(place(g));
        Map<GlobalPos, RepriseState.Tablet> loaded = new HashMap<>();
        for (ClockBlockEntity t : serverTablets()) {
            GlobalPos g = key(t);
            RepriseState.Place p = place(g);
            RepriseState.Tablet saved = new RepriseState.Tablet(p.dim, p.x, p.y, p.z,
                    t.clock().running() || tabletsToResume.contains(g), t.clock().elapsed(now));
            loaded.put(g, saved);
            s.tablets.add(saved);
        }
        // A tablet gone since the last save: unloaded (remembered) or broken (its chunk is there without it)
        for (Map.Entry<GlobalPos, RepriseState.Tablet> e : seen.entrySet())
            if (!loaded.containsKey(e.getKey()) && !away.containsKey(e.getKey()))
                away.put(e.getKey(), new Away(e.getValue(), frozen ? now : seenAt));
        away.entrySet().removeIf(e -> {
            ServerLevel level = server.getLevel(e.getKey().dimension());
            BlockPos pos = e.getKey().pos();
            return level == null || level.isLoaded(pos) && !(level.getBlockEntity(pos) instanceof ClockBlockEntity);
        });
        seen = loaded;
        seenAt = now;
        // Tablets away are kept for the next restart
        for (Away a : away.values())
            s.tablets.add(new RepriseState.Tablet(a.t.dim, a.t.x, a.t.y, a.t.z, a.t.running, a.elapsed(now)));
        return s;
    }

    /** Writes the state if it changed (off the server thread). */
    private static void save(MinecraftServer server) {
        if (file == null) return;
        RepriseState s = snapshot(server);
        String compare = s.toJson();
        if (compare.equals(lastWritten)) return;
        lastWritten = compare;
        s.savedAt = System.currentTimeMillis();
        String json = s.toJson();
        IO.submit(() -> write(json));
    }

    private static void write(String json) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            CraftMaster.LOG.warn("[Reprise] Impossible d'écrire {} : {}", file, e.toString());
        }
    }

    private static RepriseState read() {
        try {
            return Files.exists(file) ? RepriseState.fromJson(Files.readString(file, StandardCharsets.UTF_8)) : null;
        } catch (IOException e) {
            CraftMaster.LOG.warn("[Reprise] Impossible de lire {} : {}", file, e.toString());
            return null;
        }
    }

    // ---------------------------------------------------------------- status

    /** The season of the round being played (else the active one). */
    private static Season season() {
        CraftMasterData data = CraftMaster.data();
        if (data == null) return null;
        Live.Current cur = data.live().current;
        Season s = cur == null ? null : data.season(cur.season());
        return s != null ? s : data.activeSeason();
    }

    private static boolean isContestant(String name) {
        Season s = season();
        if (s == null) return false;
        for (SeasonPlayer p : s.players.values()) if (!p.archived && p.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** Contestants of the season (not archived) who are not connected. */
    public static List<String> missing(MinecraftServer server) {
        return missing(server, null);
    }

    /** {@code joining}: a player being connected (not in the player list yet), counted as there. */
    private static List<String> missing(MinecraftServer server, String joining) {
        List<String> out = new ArrayList<>();
        Season s = season();
        if (s == null) return out;
        for (SeasonPlayer p : s.players.values())
            if (!p.archived && server.getPlayerList().getPlayerByName(p.name) == null && !p.name.equalsIgnoreCase(joining)) out.add(p.name);
        return out;
    }

    /** What the organisers need to know: frozen or not, the clocks, who is missing. */
    public static List<Component> status(MinecraftServer server) {
        return status(server, null);
    }

    private static List<Component> status(MinecraftServer server, String joining) {
        List<Component> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (frozen) {
            long min = (now - frozenAt) / 60_000;
            out.add(Component.literal("⏸ Show gelé (" + reason + ", depuis " + (min < 1 ? "moins d'une minute" : min + " min") + ")")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else {
            out.add(Component.literal("▶ Le show n'est pas gelé.").withStyle(ChatFormatting.GREEN));
        }
        CraftMasterData data = CraftMaster.data();
        Manche m = data == null ? null : data.currentManche();
        if (m != null) out.add(line("Manche : ", m.name));
        if (Show.timerRunning())
            out.add(line("Chrono de manche : ", Actions.clock(Show.timerRemaining(server)) + (Show.timerPaused() ? " (en pause)" : "")));
        List<String> tablets = new ArrayList<>();
        for (ClockBlockEntity t : serverTablets()) {
            TabletClock.Settings st = t.settings();
            if (st.mode != TabletClock.Mode.TIMER && st.mode != TabletClock.Mode.STOPWATCH) continue;
            boolean running = t.clock().running() || tabletsToResume.contains(key(t));
            if (!running && t.clock().elapsed(now) == 0) continue;
            String shown = TabletClock.format(t.clock().shown(st, now), TabletClock.Format.MINUTES, st.mode == TabletClock.Mode.TIMER);
            String name = !st.label.isEmpty() ? st.label : !st.group.isEmpty() ? st.group : t.getBlockPos().toShortString();
            tablets.add(name + " " + shown + (running ? "" : " (arrêtée)"));
        }
        if (!away.isEmpty()) tablets.add(away.size() + " hors des zones chargées");
        if (!tablets.isEmpty()) out.add(line("Tablettes : ", String.join(", ", tablets)));
        Map<String, Long> watches = Stopwatch.SHARED.runningTimes(now);
        if (!watches.isEmpty()) {
            List<String> w = new ArrayList<>();
            watches.forEach((p, ms) -> w.add(p + " " + Stopwatch.format(ms)));
            out.add(line("Chronos candidats : ", String.join(", ", w)));
        }
        String winner = Buzzers.LOCK.winner();
        if (winner != null) out.add(line("Buzzers : ", winner + " en premier (" + Buzzers.LOCK.presses().size() + " buzz)"));
        List<String> missing = missing(server, joining);
        if (missing.isEmpty()) {
            MutableComponent l = Component.literal("Tous les candidats sont connectés.").withStyle(ChatFormatting.GREEN);
            if (frozen) l.append(" ").append(resumeButton(false));
            out.add(l);
        } else {
            out.add(Component.literal("Manquent : ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.join(", ", missing)).withStyle(ChatFormatting.RED)));
            if (frozen) out.add(Component.literal("Reprendre quand même : ").withStyle(ChatFormatting.DARK_GRAY).append(resumeButton(true)));
        }
        return out;
    }

    private static Component line(String label, String value) {
        return Component.literal(label).withStyle(ChatFormatting.GRAY).append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    private static Component resumeButton(boolean force) {
        String cmd = "/cm reprise reprendre" + (force ? " force" : "");
        return Component.literal(force ? "[Forcer la reprise]" : "[Reprendre]").withStyle(s -> s
                .withColor(force ? ChatFormatting.RED : ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent.RunCommand(cmd))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(cmd))));
    }

    // ---------------------------------------------------------------- positions

    private static GlobalPos key(ClockBlockEntity t) {
        return GlobalPos.of(t.getLevel().dimension(), t.getBlockPos().immutable());
    }

    private static RepriseState.Place place(GlobalPos g) {
        return new RepriseState.Place(g.dimension().identifier().toString(), g.pos().getX(), g.pos().getY(), g.pos().getZ());
    }

    private static GlobalPos pos(RepriseState.Place p) {
        Identifier id = Identifier.tryParse(p.dim);
        if (id == null) return null;
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, id);
        return GlobalPos.of(dim, new BlockPos(p.x, p.y, p.z));
    }
}
