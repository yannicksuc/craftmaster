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
        ShowEvents.register(new ShowEvents.Listener() {
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

    /** {@code /cm epreuve lancer <manche>}: the previous round ends, this one starts (and its set-up follows). */
    public static ActionExecutor.Result launch(MinecraftServer server, String manche) {
        List<String> lines = new ArrayList<>();
        Manche now = fr.lordfinn.craftmaster.trial.Judging.currentManche();
        Tasks.Located found = Tasks.locateActive(manche);
        Manche target = found == null ? null : found.manche();
        if (target == null) return new ActionExecutor.Result(List.of("Manche « " + manche + " » introuvable dans la saison active."), false);
        if (now != null && !now.id.equals(target.id)) {
            ActionExecutor.Result end = ActionExecutor.execute(server, List.of(Map.of("type", "manche.end")));
            lines.addAll(end.lines());
        }
        ActionExecutor.Result r = ActionExecutor.execute(server, List.of(Map.of("type", "manche.start", "manche", target.id)));
        lines.addAll(r.lines());
        return new ActionExecutor.Result(lines, r.ok());
    }

    /**
     * A round started (however: command, menu, Stream Deck, voice, intro): its arenas are set up and its envelopes
     * given. Told to the organisers.
     */
    public static void onRoundStarted(MinecraftServer server, Actions.MancheStarted m) {
        List<String> lines = new ArrayList<>();
        try {
            List<Arena> arenas = arenas(m.mancheId());
            if (!arenas.isEmpty()) {
                if (state.manche == null || !state.manche.equals(m.mancheId()) || state.season != m.season()) {
                    state.back.clear();
                    state.step = 0;
                }
                state.manche = m.mancheId();
                state.season = m.season();
                for (Arena a : arenas) lines.addAll(setUp(server, a, m.cast()));
            }
            int given = autoEnvelopes(server, m.season(), m.mancheId(), null);
            if (given > 0) lines.add(given + " enveloppe(s) distribuée(s).");
        } catch (RuntimeException e) {
            CraftMaster.LOG.warn("[Épreuve] mise en place de « {} »", m.name(), e);
            lines.add("⚠ Mise en place incomplète : " + e);
        }
        save();
        for (String l : lines)
            Finish.tellOrganisers(server, null, Component.literal("▶ " + l).withStyle(ChatFormatting.GOLD), "[Épreuve] " + l);
    }

    /** One arena of the round: copies, reset, everyone at their start in its game mode with the kit. */
    private static List<String> setUp(MinecraftServer server, Arena a, List<String> cast) {
        List<String> lines = new ArrayList<>(syncCopies(server, a, false));
        if (!a.copies.isEmpty()) {
            int n = Arenas.rebuild(server, a);
            lines.add("« " + a.name + " » : " + n + " copie(s) remise(s) à neuf.");
        }
        List<String> online = new ArrayList<>();
        for (String name : cast) {
            ServerPlayer p = server.getPlayerList().getPlayerByName(name);
            if (p == null) continue;
            remember(p);
            online.add(name);
        }
        List<String> placed = Arenas.place(server, a, online);
        GameType mode = gameType(a);
        for (String name : placed) {
            ServerPlayer p = server.getPlayerList().getPlayerByName(name);
            if (p != null) p.setGameMode(mode);
        }
        lines.add("« " + a.name + " » : " + (placed.isEmpty() ? "aucun candidat connecté" : String.join(", ", placed) + " en place")
                + " (" + (mode == GameType.SURVIVAL ? "survie" : "aventure") + (a.kit.isEmpty() ? "" : ", kit donné") + ").");
        if (a.vocalLancement && voice()) {
            int v = fr.lordfinn.craftmaster.compat.VoiceGroups.gather(server, pl -> fr.lordfinn.craftmaster.trial.Judging.contestant(pl) != null || Permissions.isAdmin(pl));
            if (v >= 0) lines.add("Canal vocal commun ouvert (" + v + " joueur(s)).");
        }
        return lines;
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

    /** {@code /cm epreuve terminer}: the round ends, everyone back where they stood, copies rebuilt. */
    public static ActionExecutor.Result finish(MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        Manche m = fr.lordfinn.craftmaster.trial.Judging.currentManche();
        String mancheId = m != null ? m.id : state.manche;
        if (mancheId == null) return new ActionExecutor.Result(List.of("Aucune épreuve en cours."), false);
        if (m != null) lines.addAll(ActionExecutor.execute(server, List.of(Map.of("type", "manche.end"))).lines());
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
        lines.add(moved + " candidat(s) revenu(s) à leur place d'avant l'épreuve.");
        for (Arena a : arenas)
            if (!a.copies.isEmpty()) lines.add("« " + a.name + " » : " + Arenas.rebuild(server, a) + " copie(s) remise(s) à neuf.");
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
                        .executes(c -> say(c, launch(c.getSource().getServer(), c.getArgument("manche", String.class)))));
        LiteralArgumentBuilder<CommandSourceStack> next = Commands.literal("suite").requires(Permissions.ADMIN)
                .executes(c -> say(c, next(c.getSource().getServer())));
        LiteralArgumentBuilder<CommandSourceStack> end = Commands.literal("terminer").requires(Permissions.ADMIN)
                .executes(c -> say(c, finish(c.getSource().getServer())));
        return List.of(launch.build(), next.build(), end.build());
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
