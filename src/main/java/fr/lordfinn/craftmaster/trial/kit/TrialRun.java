package fr.lordfinn.craftmaster.trial.kit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.data.SeasonPlayer;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.show.Actions;
import fr.lordfinn.craftmaster.show.ShowEvents;
import fr.lordfinn.craftmaster.task.EnvelopeItem;
import fr.lordfinn.craftmaster.task.TaskComponents;
import fr.lordfinn.craftmaster.task.Tasks;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A trial live in one action each ({@code /cm epreuve lancer|suite|terminer}, the menu, the Stream Deck):
 * <ul>
 * <li><b>lancer</b>: the previous round ends, the round starts; for each arena linked to it, the copies follow the
 * season's contestants and are rebuilt from the reference, every contestant goes to the start of their copy (respawn
 * there) in the arena's game mode with its kit; then the envelopes of the round go to everyone who must read it.</li>
 * <li><b>suite</b>: the next step of the arena ({@link Arena#etapes}: reveal the hidden part, common voice, timer…).</li>
 * <li><b>terminer</b>: the round ends, everyone goes back where they stood before (game mode too), the copies are
 * rebuilt for the next time.</li>
 * </ul>
 * Whatever starts a round ({@code /cm manche lancer}, the voice) does the same set-up: {@link #onRoundStarted}.
 * The arenas' copies always match the active season's contestants ({@link #syncCopies}); an arena without copies
 * is shared (everyone plays in the reference) and left alone.
 */
public final class TrialRun {

    private TrialRun() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Where a contestant stood before the trial (back there at its end). */
    static final class Back {
        String world;
        double x, y, z;
        float yaw;
        String gamemode;
    }

    /** What survives a restart: who got which envelope, where everyone goes back, the trial's step. */
    static final class State {
        /** "season:mancheId" → contestants (lower case) who got / opened the envelope. */
        Map<String, Set<String>> envelopes = new LinkedHashMap<>();
        /** Contestant (lower case) → where they stood before the trial. */
        Map<String, Back> back = new LinkedHashMap<>();
        /** The trial running (round id) and the next step of its « suite ». */
        String manche;
        int season;
        int step;
    }

    private static State state = new State();

    /** The trial running (round id) from its launch until « Revenir au plateau », or null. */
    public static String trialManche() {
        return state.manche;
    }
    private static final Set<java.util.UUID> JOINED = new java.util.HashSet<>();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("craftmaster/epreuve-en-cours.json");
    }

    private static void load() {
        try {
            if (Files.exists(file())) {
                State s = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), State.class);
                if (s != null) state = s;
            }
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Épreuve] {} illisible", file(), e);
        }
        if (state.envelopes == null) state.envelopes = new LinkedHashMap<>();
        if (state.back == null) state.back = new LinkedHashMap<>();
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(state), StandardCharsets.UTF_8);
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Épreuve] écriture impossible", e);
        }
    }

    public static void register() {
        load();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(TrialRun::choiceTick);
        ShowEvents.register(new ShowEvents.Listener() {
            @Override
            public void onMancheEnded(MinecraftServer server, String name) {
                roundEnded(server, name);
            }

            @Override
            public void onEnvelopeOpened(MinecraftServer server, ServerPlayer player, int season, String mancheId) {
                // Opened (and maybe torn): never sent again
                if (mancheId != null && !mancheId.isEmpty()) mark(season, mancheId, Arenas.playerName(player));
            }
        });
        // A contestant who arrives during the round gets their envelope
        // (next tick: at JOIN the player is not in the player list yet)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> JOINED.add(handler.getPlayer().getUUID()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (JOINED.isEmpty()) return;
            for (java.util.UUID id : List.copyOf(JOINED)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) lateEnvelope(server, p);
            }
            JOINED.clear();
        });
    }

    // ---------------------------------------------------------------- the season's contestants

    private static Season season() {
        CraftMasterData d = CraftMaster.data();
        return d == null ? null : d.activeSeason();
    }

    /** The active season's contestants (not put away), as named in the cast. */
    public static List<String> cast() {
        Season s = season();
        List<String> out = new ArrayList<>();
        if (s != null) for (SeasonPlayer p : s.players.values()) if (!p.archived) out.add(p.name);
        return out;
    }

    private static boolean in(String name, java.util.Collection<String> names) {
        for (String n : names) if (n.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** The arenas played in this round. */
    public static List<Arena> arenas(String mancheId) {
        List<Arena> out = new ArrayList<>();
        if (mancheId != null) for (Arena a : Arenas.all()) if (mancheId.equals(a.manche)) out.add(a);
        return out;
    }

    // ---------------------------------------------------------------- copies = contestants

    /**
     * The arena's copies become one per contestant of the active season: copies of people no longer in it are
     * removed (their ground put back), the new contestants get a free slot (the others keep theirs). An arena
     * without copies is shared and left alone.
     *
     * @return the lines to tell (empty when nothing changed)
     */
    public static List<String> syncCopies(MinecraftServer server, Arena a, boolean buildNew) {
        List<String> lines = new ArrayList<>();
        if (a.copies.isEmpty()) return lines;
        List<String> cast = cast();
        if (cast.isEmpty()) return lines;
        ServerLevel level = Arenas.level(server, a);
        List<String> gone = new ArrayList<>();
        for (Map.Entry<String, int[]> c : new ArrayList<>(a.copies.entrySet())) {
            if (in(c.getKey(), cast)) continue;
            clearCopy(level, a, c.getValue());
            a.copies.remove(c.getKey());
            gone.add(c.getKey());
        }
        if (!gone.isEmpty()) Arenas.save();
        List<String> added = Arenas.assign(a, cast);
        if (buildNew && !added.isEmpty()) Arenas.rebuild(server, a, added);
        if (!gone.isEmpty()) lines.add("« " + a.name + " » : copie(s) retirée(s) de " + String.join(", ", gone) + ".");
        if (!added.isEmpty()) lines.add("« " + a.name + " » : copie(s) pour " + String.join(", ", added) + ".");
        return lines;
    }

    /**
     * A removed copy's place gets its ground back: each block (and its barrier wall) becomes what stands at the same
     * height just beside it, in the gap between two copies (the world's own ground there, air above it).
     */
    static void clearCopy(ServerLevel level, Arena a, int[] off) {
        int x0 = a.x0 + off[0] - 1, x1 = a.x1 + off[0] + 1, z0 = a.z0 + off[2] - 1, z1 = a.z1 + off[2] + 1;
        int y0 = a.y0 + off[1], y1 = a.y1 + off[1] + 1;
        for (Entity e : level.getEntities((Entity) null, a.box(off).inflate(1), e -> !(e instanceof Player))) e.discard();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(), s = new BlockPos.MutableBlockPos();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;
        for (int z = z0; z <= z1; z++)
            for (int y = y0; y <= y1; y++) {
                BlockState ground = level.getBlockState(s.set(x0 - 1, y, z));
                if (ground.is(Blocks.BARRIER) || ground.hasBlockEntity()) ground = Blocks.AIR.defaultBlockState();
                for (int x = x0; x <= x1; x++) {
                    p.set(x, y, z);
                    level.removeBlockEntity(p);
                    level.setBlock(p, ground, flags);
                }
            }
    }

    // ---------------------------------------------------------------- lancer

    /** Longest wait for the server to load the trial's places (ticks). */
    private static final int LOAD_TIMEOUT = 300;
    /** "season:manche" whose copies are ready for its start (prepared by the launch, used by the start). */
    private static String prepared;
    /** What the set-up did, told to the organisers when the picture opens. */
    private static final List<String> REPORT = new ArrayList<>();

    /**
     * {@code /cm epreuve lancer <manche> [force]}: the previous round ends, the copies are prepared, the places the
     * trial will show are loaded on the server (copies, starts, teleport points, the intro's shots), the broadcast
     * draws the intro's shots behind a black, and only then the round starts (intro, black, teleports, reveal when
     * every game is ready). {@code force}: without waiting for the loads.
     */
    public static ActionExecutor.Result launch(MinecraftServer server, String manche, boolean force) {
        List<String> lines = new ArrayList<>();
        Tasks.Located found = Tasks.locateActive(manche);
        Manche target = found == null ? null : found.manche();
        if (target == null) return new ActionExecutor.Result(List.of("Manche « " + manche + " » introuvable dans la saison active."), false);
        // A frozen show holds the contestants where they are: the teleports would be undone at once
        if (fr.lordfinn.craftmaster.reprise.Reprise.frozen())
            return new ActionExecutor.Result(List.of("Le show est gelé (/cm reprise) : reprends-le d'abord (/cm reprise reprendre), puis relance."), false);
        if (LaunchGate.busy() && !force) return new ActionExecutor.Result(List.of("Un lancement est déjà en préparation (/cm epreuve lancer "
                + target.id + " force pour passer outre)."), false);
        LaunchGate.cancel();
        Manche now = fr.lordfinn.craftmaster.trial.Judging.currentManche();
        if (now != null && !now.id.equals(target.id))
            lines.addAll(ActionExecutor.execute(server, List.of(Map.of("type", "manche.end"))).lines());
        Season s = found.season();
        List<String> cast = cast();
        lines.addAll(prepare(server, s.id, target.id, cast));
        Map<ServerLevel, Set<net.minecraft.world.level.ChunkPos>> chunks = places(server, target);
        int count = chunks.values().stream().mapToInt(Set::size).sum();
        if (force) {
            LaunchGate.hold(chunks);
            lines.add("Lancement sans attendre le chargement (" + count + " chunks demandés).");
            afterLoad(server, target.id);
            return new ActionExecutor.Result(lines, true);
        }
        lines.add("Chargement de « " + target.name + " » (" + count + " chunks) : la manche part dès que tout est prêt.");
        LaunchGate.preload(server, chunks, LOAD_TIMEOUT, () -> afterLoad(server, target.id), () -> {
            Component force1 = Component.literal("[Lancer quand même]").withStyle(st -> st.withColor(ChatFormatting.RED)
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/cm epreuve lancer " + target.id + " force"))
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("/cm epreuve lancer " + target.id + " force"))));
            Finish.tellOrganisers(server, null, Component.literal("« " + target.name + " » n'est pas lancée. ").withStyle(ChatFormatting.GOLD)
                    .append(force1), "[Épreuve] « " + target.name + " » pas lancée : chargement trop long");
        });
        return new ActionExecutor.Result(lines, true);
    }

    /**
     * Everything is loaded: the games that will show the intro (the broadcast games, and every player with the mod,
     * taken under the lens for it: the intro's world may not be theirs) draw its shots behind a black, then the round
     * starts.
     */
    private static void afterLoad(MinecraftServer server, String mancheId) {
        List<fr.lordfinn.craftmaster.broadcast.Director.ShotRequest> shots =
                fr.lordfinn.craftmaster.broadcast.MancheIntros.shots(server, mancheId);
        if (!shots.isEmpty()) fr.lordfinn.craftmaster.show.Spectators.holdForIntro(server);
        List<ServerPlayer> broadcast = broadcasters(server);
        if (shots.isEmpty() || broadcast.isEmpty()) {
            start(server, mancheId);
            return;
        }
        // Behind a black on the broadcast games only: each shot once (the first one last: it stays on air)
        var black = new fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowCue(
                fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Cues.BLACKOUT, "", "", "12,6000,20", 6032);
        for (ServerPlayer p : broadcast) if (ServerPlayNetworking.canSend(p, black.type())) ServerPlayNetworking.send(p, black);
        List<fr.lordfinn.craftmaster.broadcast.Director.ShotRequest> order = new ArrayList<>(shots);
        java.util.Collections.reverse(order);
        LaunchGate.check(server, List.of(), 12, 12, "Noir", () -> prewarm(server, mancheId, order, 0));
    }

    private static void prewarm(MinecraftServer server, String mancheId, List<fr.lordfinn.craftmaster.broadcast.Director.ShotRequest> order, int i) {
        if (i >= order.size()) {
            // The intro's first shot is already on air under the black: it cuts in, and the black opens on it
            fr.lordfinn.craftmaster.broadcast.MancheIntros.fromBlack(true);
            start(server, mancheId);
            fr.lordfinn.craftmaster.broadcast.MancheIntros.fromBlack(false);
            // The intro's first shot goes on air at its next tick: the black opens once it is there
            LaunchGate.check(server, broadcasters(server), 4, 40, "Premier plan de l'intro", () -> {
                var open = new fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowCue(
                        fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Cues.BLACKOUT_OPEN, "", "", "", 20);
                for (ServerPlayer p : broadcasters(server)) if (ServerPlayNetworking.canSend(p, open.type())) ServerPlayNetworking.send(p, open);
            });
            return;
        }
        fr.lordfinn.craftmaster.broadcast.Director.take(server, order.get(i));
        fr.lordfinn.craftmaster.broadcast.Director.nudgeBroadcasters(server);
        LaunchGate.check(server, broadcasters(server), 4, 100, "Plan " + (order.size() - i) + " de l'intro sur la diffusion",
                () -> prewarm(server, mancheId, order, i + 1));
    }

    /** The games showing the intro: the broadcast games and the players held under the lens for it. */
    private static List<ServerPlayer> broadcasters(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        java.util.Set<java.util.UUID> ids = new java.util.LinkedHashSet<>(fr.lordfinn.craftmaster.broadcast.Director.broadcasters());
        ids.addAll(fr.lordfinn.craftmaster.show.Spectators.feedWatchers());
        for (java.util.UUID id : ids) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    private static void start(MinecraftServer server, String mancheId) {
        ActionExecutor.Result r = ActionExecutor.execute(server, List.of(Map.of("type", "manche.start", "manche", mancheId)));
        for (String l : r.lines())
            Finish.tellOrganisers(server, null, Component.literal("▶ " + l).withStyle(r.ok() ? ChatFormatting.GOLD : ChatFormatting.RED), "[Épreuve] " + l);
        if (!r.ok()) LaunchGate.release(server);
    }

    /**
     * Before a round starts (launch, or {@code /cm manche lancer}): its arenas' copies follow the cast and are
     * rebuilt from the reference, once per start.
     */
    public static List<String> prepare(MinecraftServer server, int season, String mancheId, List<String> cast) {
        List<String> lines = new ArrayList<>();
        List<Arena> arenas = arenas(mancheId);
        if (arenas.isEmpty() || key(season, mancheId).equals(prepared)) return lines;
        // The same trial launched again: it starts over (envelopes handed out again, stopwatches at zero)
        // (also after « Revenir au plateau », which forgets the round but not who got its envelope: a launch is
        // always a new start, the list only guards against a second envelope within one run)
        boolean relaunch = state.manche != null && state.manche.equals(mancheId) && state.season == season
                || state.envelopes.containsKey(key(season, mancheId));
        if (state.manche == null || !state.manche.equals(mancheId) || state.season != season) {
            state.back.clear();
            state.step = 0;
        }
        state.manche = mancheId;
        state.season = season;
        REPORT.clear();
        if (relaunch) {
            int taken = takeEnvelopes(server, season, mancheId);
            state.envelopes.remove(key(season, mancheId));
            fr.lordfinn.craftmaster.trial.Judging.reset();
            REPORT.add("▶ Relance : " + taken + " ancienne(s) enveloppe(s) reprise(s), de nouvelles au départ ; chronos remis à zéro.");
        }
        for (Arena a : arenas) {
            REPORT.addAll(syncCopies(server, a, false));
            if (!a.copies.isEmpty()) REPORT.add("« " + a.name + " » : " + Arenas.rebuild(server, a) + " copie(s) remise(s) à neuf.");
        }
        prepared = key(season, mancheId);
        save();
        lines.addAll(REPORT);
        return lines;
    }

    /** The places a round's trial shows, as chunks per level: its arenas (copies, starts, other zones), TP points, intro. */
    static Map<ServerLevel, Set<net.minecraft.world.level.ChunkPos>> places(MinecraftServer server, Manche m) {
        Map<ServerLevel, Set<net.minecraft.world.level.ChunkPos>> out = new LinkedHashMap<>();
        for (Arena a : arenas(m.id)) {
            ServerLevel level = Arenas.level(server, a);
            int g = Math.max(0, a.chargementMarge);
            area(out, level, a.x0 - g, a.z0 - g, a.x1 + g, a.z1 + g);
            for (int[] off : a.copies.values()) {
                area(out, level, a.x0 + off[0] - g, a.z0 + off[2] - g, a.x1 + off[0] + g, a.z1 + off[2] + g);
                double[] sp = Arenas.spawn(a, off);
                around(out, level, sp[0], sp[2]);
            }
            double[] ref = Arenas.spawn(a, null);
            around(out, level, ref[0], ref[2]);
            for (int[] z : a.chargementZones) if (z.length >= 4) area(out, level, z[0], z[1], z[2], z[3]);
        }
        for (fr.lordfinn.craftmaster.data.Spot sp : m.tp) {
            ServerLevel level = fr.lordfinn.craftmaster.bust.Busts.level(server, sp.world());
            if (level != null) around(out, level, sp.x(), sp.z());
        }
        for (Object[] sh : fr.lordfinn.craftmaster.broadcast.MancheIntros.shotPlaces(server, m.id)) {
            ServerLevel level = fr.lordfinn.craftmaster.bust.Busts.level(server, String.valueOf(sh[0]));
            if (level != null) around(out, level, (double) sh[1], (double) sh[2]);
        }
        fr.lordfinn.craftmaster.camera.CameraSpot wide = fr.lordfinn.craftmaster.camera.Lieux.resolve(server, "large", m.id);
        if (wide != null) {
            ServerLevel level = fr.lordfinn.craftmaster.bust.Busts.level(server, wide.world());
            if (level != null) around(out, level, wide.x(), wide.z());
        }
        return out;
    }

    private static void area(Map<ServerLevel, Set<net.minecraft.world.level.ChunkPos>> out, ServerLevel level, int x0, int z0, int x1, int z1) {
        Set<net.minecraft.world.level.ChunkPos> set = out.computeIfAbsent(level, k -> new LinkedHashSet<>());
        for (int cx = Math.min(x0, x1) >> 4; cx <= Math.max(x0, x1) >> 4; cx++)
            for (int cz = Math.min(z0, z1) >> 4; cz <= Math.max(z0, z1) >> 4; cz++) set.add(new net.minecraft.world.level.ChunkPos(cx, cz));
    }

    /** Two chunks around a point (what a player or a camera there sees first). */
    private static void around(Map<ServerLevel, Set<net.minecraft.world.level.ChunkPos>> out, ServerLevel level, double x, double z) {
        area(out, level, (int) Math.floor(x) - 32, (int) Math.floor(z) - 32, (int) Math.floor(x) + 32, (int) Math.floor(z) + 32);
    }

    /** At the round's teleports: where the cast stands now (back there at the end). */
    public static void rememberCast(MinecraftServer server, Actions.MancheStarted m) {
        if (arenas(m.mancheId()).isEmpty()) return;
        for (String name : m.cast()) {
            ServerPlayer p = server.getPlayerList().getPlayerByName(name);
            if (p != null) remember(p);
        }
        save();
    }

    /** At the round's teleports: each online contestant to the start of their copy, game mode, kit. */
    public static int placeCast(MinecraftServer server, Actions.MancheStarted m) {
        int n = 0;
        for (Arena a : arenas(m.mancheId())) {
            List<String> online = new ArrayList<>();
            for (String name : m.cast()) if (server.getPlayerList().getPlayerByName(name) != null) online.add(name);
            List<String> placed = Arenas.place(server, a, online);
            GameType mode = gameType(a);
            for (String name : placed) {
                ServerPlayer p = server.getPlayerList().getPlayerByName(name);
                if (p != null) {
                    p.setGameMode(mode);
                    p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    p.resetFallDistance();
                }
            }
            REPORT.add("« " + a.name + " » : " + (placed.isEmpty() ? "aucun candidat connecté" : String.join(", ", placed) + " en place")
                    + " (" + (mode == GameType.SURVIVAL ? "survie" : "aventure") + (a.kit.isEmpty() ? "" : ", kit donné") + ").");
            n += placed.size();
        }
        return n;
    }

    /**
     * The picture opened on the round (however it started): common voice if asked, envelopes, the loaded places let
     * go (the players are there). Told to the organisers.
     */
    public static void onRoundStarted(MinecraftServer server, Actions.MancheStarted m) {
        List<String> lines = new ArrayList<>(REPORT);
        REPORT.clear();
        prepared = null;
        try {
            for (Arena a : arenas(m.mancheId()))
                if (a.vocalLancement && voice()) {
                    int v = fr.lordfinn.craftmaster.compat.VoiceGroups.gather(server, pl -> fr.lordfinn.craftmaster.trial.Judging.contestant(pl) != null || Permissions.isAdmin(pl));
                    if (v >= 0) lines.add("Canal vocal commun ouvert (" + v + " joueur(s)).");
                }
            fr.lordfinn.craftmaster.show.Spectators.onTrialStarted(server);
            int given = autoEnvelopes(server, m.season(), m.mancheId(), null);
            if (given > 0) lines.add(given + " enveloppe(s) distribuée(s).");
            CraftMaster.LOG.info("[Épreuve] « {} » : {} enveloppe(s) distribuée(s) au départ{}", m.name(), given,
                    autoEnvelope(Tasks.locate(m.season(), m.mancheId()) == null ? null : Tasks.locate(m.season(), m.mancheId()).manche()) ? "" : " (enveloppes manuelles pour cette manche)");
        } catch (RuntimeException e) {
            CraftMaster.LOG.warn("[Épreuve] mise en place de « {} »", m.name(), e);
            lines.add("⚠ Mise en place incomplète : " + e);
        }
        LaunchGate.release(server);
        save();
        for (String l : lines)
            Finish.tellOrganisers(server, null, Component.literal("▶ " + l).withStyle(ChatFormatting.GOLD), "[Épreuve] " + l);
    }

    public static GameType gameType(Arena a) {
        return "survie".equals(a.mode) ? GameType.SURVIVAL : GameType.ADVENTURE;
    }

    private static boolean voice() {
        return FabricLoader.getInstance().isModLoaded("voicechat");
    }

    private static void remember(ServerPlayer p) {
        String k = Arenas.playerName(p).toLowerCase(Locale.ROOT);
        if (state.back.containsKey(k)) return;
        Back b = new Back();
        b.world = p.level().dimension().identifier().toString();
        b.x = p.getX();
        b.y = p.getY();
        b.z = p.getZ();
        b.yaw = p.getYRot();
        b.gamemode = p.gameMode().getName();
        state.back.put(k, b);
    }

    // ---------------------------------------------------------------- envelopes

    private static String key(int season, String mancheId) {
        return season + ":" + mancheId;
    }

    private static void mark(int season, String mancheId, String name) {
        state.envelopes.computeIfAbsent(key(season, mancheId), k -> new LinkedHashSet<>()).add(name.toLowerCase(Locale.ROOT));
        save();
    }

    private static boolean got(int season, String mancheId, String name) {
        Set<String> s = state.envelopes.get(key(season, mancheId));
        return s != null && s.contains(name.toLowerCase(Locale.ROOT));
    }

    /** The round's envelopes taken back from everyone online (a relaunch hands out new ones); returns how many. */
    private static int takeEnvelopes(MinecraftServer server, int season, String mancheId) {
        int n = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            var inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                TaskComponents.Envelope e = EnvelopeItem.envelope(inv.getItem(i));
                if (e != null && e.season() == season && mancheId.equals(e.manche())) {
                    inv.setItem(i, ItemStack.EMPTY);
                    n++;
                }
            }
        }
        return n;
    }

    /** Does the player carry (or wear in a bundle…) an envelope of this round? */
    private static boolean carries(ServerPlayer p, int season, String mancheId) {
        for (ItemStack st : p.getInventory().getNonEquipmentItems()) {
            TaskComponents.Envelope e = EnvelopeItem.envelope(st);
            if (e != null && e.season() == season && mancheId.equals(e.manche())) return true;
        }
        TaskComponents.Envelope off = EnvelopeItem.envelope(p.getOffhandItem());
        return off != null && off.season() == season && mancheId.equals(off.manche());
    }

    /** Is the round's envelope handed out by itself (it has a letter, the option is on)? */
    public static boolean autoEnvelope(Manche m) {
        return m != null && m.task != null && !m.task.isEmpty() && m.autoEnvelope;
    }

    /**
     * The round's envelope to every online contestant who must read it and has none yet (nor opened one): in a round
     * played in arenas with copies, only those who have a copy.
     *
     * @param only one player (a late arrival), or null for everyone
     * @return how many were given
     */
    static int autoEnvelopes(MinecraftServer server, int season, String mancheId, ServerPlayer only) {
        Tasks.Located l = Tasks.locate(season, mancheId);
        if (l == null || !autoEnvelope(l.manche())) {
            return 0;
        }
        List<Arena> arenas = arenas(mancheId);
        boolean withCopies = arenas.stream().anyMatch(a -> !a.copies.isEmpty());
        int n = 0;
        for (SeasonPlayer sp : l.season().players.values()) {
            if (sp.archived) continue;
            // A player who is joining is not in the player list yet
            ServerPlayer p = only != null ? (only.getGameProfile().name().equalsIgnoreCase(sp.name) ? only : null)
                    : server.getPlayerList().getPlayerByName(sp.name);
            if (p == null) continue;
            if (withCopies && arenas.stream().noneMatch(a -> a.copies.keySet().stream().anyMatch(k -> k.equalsIgnoreCase(sp.name)))) {
                continue;
            }
            if (got(season, mancheId, sp.name) || carries(p, season, mancheId)) {
                continue;
            }
            ItemStack env = Tasks.envelope(l, null, p.getGameProfile().name(), true);
            if (!p.getInventory().add(env)) p.spawnAtLocation(p.level(), env);
            p.sendOverlayMessage(Component.translatable("message.craftmaster.envelope_received").withStyle(ChatFormatting.GOLD));
            fr.lordfinn.craftmaster.show.Show.sound(p, fr.lordfinn.craftmaster.show.Show.holder(SoundEvents.BOOK_PAGE_TURN), 1f, 1.1f);
            mark(season, mancheId, sp.name);
            n++;
        }
        return n;
    }

    private static void lateEnvelope(MinecraftServer server, ServerPlayer p) {
        CraftMasterData d = CraftMaster.data();
        if (d == null || d.live().current == null) return;
        var cur = d.live().current;
        int n = autoEnvelopes(server, cur.season(), cur.manche(), p);
        if (n > 0) Finish.tellOrganisers(server, null, Component.literal("✉ Enveloppe donnée à " + p.getGameProfile().name() + " (arrivé pendant la manche).")
                .withStyle(ChatFormatting.GRAY), "[Épreuve] enveloppe donnée à " + p.getGameProfile().name() + " (arrivé pendant la manche)");
    }

    // ---------------------------------------------------------------- suite

    /** The steps of the round's « suite », in order. */
    static List<String> steps(String mancheId) {
        for (Arena a : arenas(mancheId)) if (a.etapes != null && !a.etapes.isEmpty()) return a.etapes;
        return List.of("revele");
    }

    /** {@code /cm epreuve suite}: the next step of the running trial. */
    public static ActionExecutor.Result next(MinecraftServer server) {
        Manche m = fr.lordfinn.craftmaster.trial.Judging.currentManche();
        Season s = season();
        if (m == null || s == null) return new ActionExecutor.Result(List.of("Aucune manche en cours."), false);
        if (!m.id.equals(state.manche)) {
            state.manche = m.id;
            state.season = s.id;
            state.step = 0;
        }
        List<String> steps = steps(m.id);
        if (state.step >= steps.size())
            return new ActionExecutor.Result(List.of("« " + m.name + " » : plus d'étape (" + String.join(" → ", steps) + "). /cm epreuve terminer pour finir."), false);
        String step = steps.get(state.step);
        List<String> lines = new ArrayList<>();
        boolean ok = runStep(server, s, m, step, lines);
        if (ok) state.step++;
        save();
        lines.add("Étape " + Math.min(state.step, steps.size()) + "/" + steps.size()
                + (state.step < steps.size() ? " · suivante : " + steps.get(state.step) : " · c'était la dernière"));
        return new ActionExecutor.Result(lines, ok);
    }

    /** One step: revele, vocal, vocalfin, chrono:<secondes>, panneaux. */
    private static boolean runStep(MinecraftServer server, Season s, Manche m, String step, List<String> lines) {
        String st = step.toLowerCase(Locale.ROOT);
        if (st.equals("revele")) {
            ActionExecutor.Result r = ActionExecutor.execute(server, List.of(Map.of("type", "task.reveal_more", "manche", m.id)));
            lines.addAll(r.lines());
            if (r.lines().isEmpty()) lines.add("Suite de la tâche révélée.");
            return r.ok();
        }
        if (st.equals("vocal")) {
            if (!voice()) {
                lines.add("Simple Voice Chat n'est pas sur ce serveur : étape vocal passée.");
                return true;
            }
            int v = fr.lordfinn.craftmaster.compat.VoiceGroups.gather(server, pl -> fr.lordfinn.craftmaster.trial.Judging.contestant(pl) != null || Permissions.isAdmin(pl));
            lines.add(v < 0 ? "Le chat vocal n'est pas prêt." : "Canal vocal commun : " + v + " joueur(s).");
            return v >= 0;
        }
        if (st.equals("vocalfin")) {
            if (voice()) fr.lordfinn.craftmaster.compat.VoiceGroups.disband(server);
            lines.add("Retour au vocal de proximité.");
            return true;
        }
        if (st.startsWith("chrono:")) {
            int sec;
            try {
                sec = Integer.parseInt(st.substring("chrono:".length()));
            } catch (NumberFormatException e) {
                lines.add("Étape « " + step + " » illisible.");
                return false;
            }
            Arenas.startTimer(server, m.name, sec);
            lines.add("Chrono " + Actions.clock(sec) + " lancé.");
            return true;
        }
        if (st.equals("panneaux")) {
            for (Arena a : arenas(m.id)) Arenas.tellSigns(server, a, Arenas.signs(server, a, true), true);
            lines.add("Panneaux verrouillés.");
            return true;
        }
        lines.add("Étape « " + step + " » inconnue (revele, vocal, vocalfin, chrono:<secondes>, panneaux).");
        return false;
    }

    // ---------------------------------------------------------------- terminer

    /**
     * {@code /cm epreuve terminer}: the round ends, nobody moves; the organisers choose then (chat, menu, Stream Deck):
     * see its replay ({@code /cm replay revoir}) or go back to the stage ({@code /cm epreuve plateau}).
     */
    public static ActionExecutor.Result finish(MinecraftServer server) {
        Manche m = fr.lordfinn.craftmaster.trial.Judging.currentManche();
        if (m == null) return new ActionExecutor.Result(List.of(state.back.isEmpty() ? "Aucune épreuve en cours."
                : "La manche est déjà finie : /cm epreuve plateau pour ramener tout le monde."), false);
        List<String> lines = new ArrayList<>(ActionExecutor.execute(server, List.of(Map.of("type", "manche.end"))).lines());
        // The stopwatches still running stop here (their bars stand still until « Revenir au plateau »)
        long now = System.currentTimeMillis();
        int stopped = 0;
        for (String p : List.copyOf(fr.lordfinn.craftmaster.trial.Stopwatch.SHARED.runningPlayers())) {
            fr.lordfinn.craftmaster.trial.Stopwatch.SHARED.stop(p, now);
            stopped++;
        }
        if (stopped > 0) lines.add(stopped + " chrono(s) encore en marche arrêté(s) (sans temps noté).");
        lines.add("Les candidats restent où ils sont : le choix (replay ou retour au plateau) arrive dans le chat.");
        return new ActionExecutor.Result(lines, true);
    }

    // ---------------------------------------------------------------- the choice at the end of a round

    /** A round just ended: its choice goes out when its replay is saved (or after a moment without one). */
    private static long choiceAt = -1;
    private static String choiceManche = "";

    private static void roundEnded(MinecraftServer server, String name) {
        choiceManche = name;
        choiceAt = server.getTickCount() + 40;
    }

    /** The round's recording just stopped ({@code Replays.stop}): the choice now, with the replay's views. */
    public static void replayFinished(MinecraftServer server) {
        if (choiceAt >= 0) sendChoice(server);
    }

    private static void choiceTick(MinecraftServer server) {
        if (choiceAt >= 0 && server.getTickCount() >= choiceAt) sendChoice(server);
    }

    private static Component button(String text, String command, String hover, ChatFormatting color) {
        return Component.literal(text).withStyle(st -> st.withColor(color)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(command))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(hover))));
    }

    /** « ▶ Revoir le replay : [zone] [Ensuri]… » and « ⌂ Revenir au plateau », to the organisers. */
    private static void sendChoice(MinecraftServer server) {
        choiceAt = -1;
        fr.lordfinn.craftmaster.replay.Replays.Finished replay = fr.lordfinn.craftmaster.replay.Replays.lastFinished();
        Component head = Component.literal("— Fin de « " + choiceManche + " » : à toi de choisir —").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        net.minecraft.network.chat.MutableComponent see = Component.literal("▶ Revoir le replay : ").withStyle(ChatFormatting.AQUA);
        boolean fresh = replay != null && replay.label().contains("« " + choiceManche + " »");
        if (fresh) {
            for (String v : replay.views())
                see.append(button("[" + v + "]", "/cm replay revoir " + v, "Rejouer " + (v.equals("zone") ? "la zone de l'épreuve" : "la vue de " + v)
                        + " (pour toi seul)", ChatFormatting.AQUA)).append(" ");
        } else see.append(Component.literal("pas d'enregistrement de cette manche").withStyle(ChatFormatting.GRAY));
        boolean back = !state.back.isEmpty();
        Component home = back ? Component.literal("⌂ ").withStyle(ChatFormatting.GREEN)
                .append(button("[Revenir au plateau]", "/cm epreuve plateau", "Tout le monde à sa place d'avant l'épreuve, mode de jeu remis, copies nettoyées",
                        ChatFormatting.GREEN)) : null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!Permissions.isAdmin(p)) continue;
            p.sendSystemMessage(head);
            p.sendSystemMessage(see);
            if (home != null) p.sendSystemMessage(home);
        }
        CraftMaster.LOG.info("[Épreuve] Fin de « {} » : choix proposé (replay {}, plateau {})", choiceManche, fresh ? replay.views() : "aucun", back);
    }


    /**
     * {@code /cm epreuve plateau}: back from the trial — everyone where they stood before it, their game mode back,
     * the common voice closed, the copies rebuilt for the next time.
     */
    public static ActionExecutor.Result backToStage(MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        Manche running = fr.lordfinn.craftmaster.trial.Judging.currentManche();
        if (running != null && running.id.equals(state.manche))
            return new ActionExecutor.Result(List.of("« " + running.name + " » est encore en cours : /cm epreuve terminer d'abord."), false);
        String mancheId = state.manche;
        if (mancheId == null && state.back.isEmpty()) return new ActionExecutor.Result(List.of("Personne à ramener : aucune épreuve terminée."), false);
        // The stopwatches' bars off the screens (the times stay in the results sheet)
        fr.lordfinn.craftmaster.trial.Judging.reset();
        // The spectators back to their place first (the contestants among them then go back to the set)
        int watching = fr.lordfinn.craftmaster.show.Spectators.stopAll(server);
        if (watching > 0) lines.add("👁 " + watching + " spectateur(s) revenu(s) à leur place.");
        List<Arena> arenas = arenas(mancheId);
        int moved = 0;
        for (Map.Entry<String, Back> e : new ArrayList<>(state.back.entrySet())) {
            ServerPlayer p = null;
            for (ServerPlayer sp : server.getPlayerList().getPlayers())
                if (Arenas.playerName(sp).equalsIgnoreCase(e.getKey())) p = sp;
            if (p == null) continue;
            Back b = e.getValue();
            ServerLevel level = fr.lordfinn.craftmaster.bust.Busts.level(server, b.world);
            if (level == null) level = server.overworld();
            p.teleportTo(level, b.x, b.y, b.z, Set.of(), b.yaw, 0f, true);
            Arenas.setRespawn(p, level, b.x, b.y, b.z, b.yaw);
            GameType g = GameType.byName(b.gamemode, GameType.ADVENTURE);
            p.setGameMode(g);
            for (Arena a : arenas) if (a.clearInventory) p.getInventory().clearContent();
            state.back.remove(e.getKey());
            moved++;
        }
        if (voice()) fr.lordfinn.craftmaster.compat.VoiceGroups.disband(server);
        LaunchGate.cancel();
        LaunchGate.release(server);
        prepared = null;
        lines.add("⌂ " + moved + " candidat(s) revenu(s) au plateau (leur place d'avant l'épreuve).");
        for (Arena a : arenas)
            if (!a.copies.isEmpty()) lines.add("« " + a.name + " » : " + Arenas.rebuildKeepingDrawings(server, a) + " copie(s) remise(s) à neuf"
                    + (a.field != null ? " (les dessins restent pour le jury)." : "."));
        state.manche = null;
        state.step = 0;
        save();
        return new ActionExecutor.Result(lines, true);
    }

    // ---------------------------------------------------------------- commands

    private static int say(CommandContext<CommandSourceStack> c, ActionExecutor.Result r) {
        for (String l : r.lines()) {
            Component line = Component.literal(l).withStyle(r.ok() ? ChatFormatting.GREEN : ChatFormatting.RED);
            if (r.ok()) c.getSource().sendSuccess(() -> line, true);
            else c.getSource().sendFailure(line);
        }
        return r.ok() ? 1 : 0;
    }

    private static List<String> mancheNames() {
        Season s = season();
        List<String> out = new ArrayList<>();
        if (s != null) for (var p : s.parties.values()) for (Manche m : p.activeManches()) out.add(m.id);
        return out;
    }

    /** {@code /cm epreuve lancer <manche> | suite | terminer}. */
    public static List<LiteralCommandNode<CommandSourceStack>> trialCommands() {
        LiteralArgumentBuilder<CommandSourceStack> launch = Commands.literal("lancer").requires(Permissions.ADMIN)
                .then(Commands.argument("manche", fr.lordfinn.craftmaster.command.WordArgument.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(mancheNames(), b))
                        .executes(c -> say(c, launch(c.getSource().getServer(), c.getArgument("manche", String.class), false)))
                        .then(Commands.literal("force").executes(c -> say(c, launch(c.getSource().getServer(), c.getArgument("manche", String.class), true)))));
        LiteralArgumentBuilder<CommandSourceStack> next = Commands.literal("suite").requires(Permissions.ADMIN)
                .executes(c -> say(c, next(c.getSource().getServer())));
        LiteralArgumentBuilder<CommandSourceStack> end = Commands.literal("terminer").requires(Permissions.ADMIN)
                .executes(c -> say(c, finish(c.getSource().getServer())));
        LiteralArgumentBuilder<CommandSourceStack> home = Commands.literal("plateau").requires(Permissions.ADMIN)
                .executes(c -> say(c, backToStage(c.getSource().getServer())));
        return List.of(launch.build(), next.build(), end.build(), home.build());
    }

    /** {@code /cm manche enveloppes <manche> auto|manuel}. */
    public static LiteralCommandNode<CommandSourceStack> mancheEnvelopeCommand() {
        var choice = Commands.argument("manche", fr.lordfinn.craftmaster.command.WordArgument.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(mancheNames(), b));
        for (String v : List.of("auto", "manuel")) choice.then(Commands.literal(v).executes(c -> ActionExecutor.execute(c.getSource(),
                List.of(Map.of("type", "manche.update", "manche", c.getArgument("manche", String.class), "autoEnvelope", v.equals("auto"))))));
        return Commands.literal("enveloppes").requires(Permissions.ADMIN).then(choice).build();
    }

    /** The organisers' summary of a round's trial (menu, /cm epreuve). */
    public static String describe(Manche m) {
        List<Arena> arenas = arenas(m.id);
        StringBuilder sb = new StringBuilder();
        for (Arena a : arenas) sb.append(sb.isEmpty() ? "" : " · ").append(a.name).append(a.copies.isEmpty() ? " (partagée)" : " (" + a.copies.size() + " copies)");
        return sb.toString();
    }

    public static String stringArg(CommandContext<CommandSourceStack> c, String name) {
        return StringArgumentType.getString(c, name);
    }
}
