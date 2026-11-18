package fr.lordfinn.craftmaster.task;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Live;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Partie;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.data.SeasonPlayer;
import fr.lordfinn.craftmaster.data.Task;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.show.Actions;
import fr.lordfinn.craftmaster.show.Show;
import fr.lordfinn.craftmaster.show.ShowEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Server side of the tasks and envelopes (chantier D): reveals (the secret rule), the task of the moment sent to
 * every player, the cards to read, the sealed envelopes, and the task actions of {@link ActionExecutor}.
 *
 * <p><b>Secret rule</b>: a task stays secret for every non-admin (players, spectators, the broadcast client) until
 * it is revealed — envelope opened, round started (unless {@link Task#revealOnStart} is off), or the organiser's
 * order. The text of a secret task never leaves the server for a non-admin.
 *
 * <p>Interfaces for the other chantiers: {@link #reveal(MinecraftServer, String, ServerPlayer)},
 * {@link #openPaper(ServerPlayer, String, String)}, {@link Notes#open(ServerPlayer, String)}, and
 * {@link ShowEvents.Listener#onTaskRevealed} / {@link ShowEvents.Listener#onEnvelopeOpened}.
 */
public final class Tasks {

    private Tasks() {}

    /** Length of the envelope's opening animation: the reveal is announced when the card comes out. */
    static final int ENVELOPE_ANIMATION_TICKS = 50;
    /** A letter left sealed this long (seconds × 20) is revealed anyway. */
    static final int SEAL_TIMEOUT_TICKS = 45 * 20;
    /** Per reader: the reveal waiting for their seal to pop. */
    private static final Map<UUID, Runnable> PENDING = new HashMap<>();

    private static final Gson GSON = new Gson();
    private static TaskStore store;
    /** Last task state sent to each player (only changes are sent). */
    private static final Map<UUID, TaskNetwork.State> SENT = new HashMap<>();
    private record Scheduled(long at, Runnable run) {}
    private static final List<Scheduled> SCHEDULED = new ArrayList<>();

    public static TaskStore store() {
        return store;
    }

    public static void register() {
        TaskNetwork.registerTypes();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            store = new TaskStore(FabricLoader.getInstance().getConfigDir().resolve("craftmaster/tasks.json"), CraftMaster.LOG);
            store.load();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (store != null) store.save();
            SENT.clear();
            SCHEDULED.clear();
            PENDING.clear();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SENT.remove(handler.getPlayer().getUUID());
            server.execute(() -> sync(handler.getPlayer()));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SENT.remove(handler.getPlayer().getUUID());
            Runnable r = PENDING.remove(handler.getPlayer().getUUID());
            if (r != null) server.execute(r);
        });
        ServerTickEvents.END_SERVER_TICK.register(Tasks::tick);
        UseBlockCallback.EVENT.register(Notes::onUseBlock);
        UseEntityCallback.EVENT.register(Notes::onUseEntity);
        AttackBlockCallback.EVENT.register(Notes::onAttackBlock);

        ServerPlayNetworking.registerGlobalReceiver(TaskNetwork.RequestTask.TYPE, (p, ctx) -> openCurrent(ctx.player()));
        ServerPlayNetworking.registerGlobalReceiver(TaskNetwork.SealBroken.TYPE, (p, ctx) -> sealBroken(ctx.player()));
        ServerPlayNetworking.registerGlobalReceiver(TaskNetwork.RunActions.TYPE, (p, ctx) -> runActions(ctx.player(), p.json()));
        ServerPlayNetworking.registerGlobalReceiver(TaskNetwork.Seal.TYPE, (p, ctx) -> seal(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(TaskNetwork.SaveNote.TYPE, (p, ctx) -> Notes.save(ctx.player(), p.note()));
        ServerPlayNetworking.registerGlobalReceiver(TaskNetwork.NoteOrder.TYPE, (p, ctx) -> Notes.order(ctx.player(), p.id(), p.order()));
        RoundStart.register();
    }

    // ---------------------------------------------------------------- rounds

    /** A round with its season and game. */
    public record Located(Season season, Partie partie, Manche manche) {
        public int number() {
            return partie.number(manche);
        }

        public boolean hasTask() {
            return manche.task != null && !manche.task.isEmpty();
        }
    }

    public static Located locate(int season, String mancheId) {
        CraftMasterData data = CraftMaster.data();
        Season s = data == null ? null : data.season(season);
        if (s == null || mancheId == null) return null;
        for (Partie p : s.parties.values()) {
            Manche m = p.manche(mancheId);
            if (m != null) return new Located(s, p, m);
        }
        return null;
    }

    /** A round of the active season by id, name or nickname (the active game first). */
    public static Located locateActive(String key) {
        CraftMasterData data = CraftMaster.data();
        Season s = data == null ? null : data.activeSeason();
        if (s == null || key == null) return null;
        Located byId = locate(s.id, key);
        if (byId != null) return byId;
        Partie active = s.activePartie();
        if (active != null) for (Manche m : active.manches) if (m.matches(key)) return new Located(s, active, m);
        for (Partie p : s.parties.values()) for (Manche m : p.manches) if (m.matches(key)) return new Located(s, p, m);
        return null;
    }

    /** The round being played, or null. */
    static Located current() {
        CraftMasterData data = CraftMaster.data();
        Live.Current c = data == null ? null : data.live().current;
        return c == null ? null : locate(c.season(), c.manche());
    }

    /**
     * The task of the moment for this player: the round being played if it has a task, else the task revealed
     * last; organisers, between two rounds, see the next round that has one (to prepare it).
     */
    static Located taskOfTheMoment(boolean admin) {
        Located cur = current();
        if (cur != null && cur.hasTask()) return cur;
        if (admin) {
            CraftMasterData data = CraftMaster.data();
            Season s = data == null ? null : data.activeSeason();
            Partie p = s == null ? null : s.activePartie();
            if (p != null) {
                List<Manche> list = p.activeManches();
                int from = cur == null || !list.contains(cur.manche()) ? 0 : list.indexOf(cur.manche()) + 1;
                for (int i = from; i < list.size(); i++) {
                    Located l = new Located(s, p, list.get(i));
                    if (l.hasTask() && !store.isRevealed(s.id, l.manche().id)) return l;
                }
            }
        }
        String[] last = store == null ? null : store.lastRevealed();
        if (last != null) {
            try {
                Located l = locate(Integer.parseInt(last[0]), last[1]);
                if (l != null && l.hasTask()) return l;
            } catch (NumberFormatException ignored) {
                // a hand-edited file
            }
        }
        return null;
    }

    public static boolean isRevealed(Located l) {
        return store != null && store.isRevealed(l.season().id, l.manche().id);
    }

    /**
     * The card as a reader may receive it: {@link #card} with the task's [secret] parts redacted
     * ({@link TaskMarkup#redact}); {@code readable}: an organiser, who still reads the hidden parts (tagged).
     */
    static TaskNetwork.Card shown(Located l, boolean full, boolean readable) {
        TaskNetwork.Card c = card(l, full);
        if (c.text().isEmpty() && c.rules().isEmpty()) return c;
        int n = store == null ? 0 : store.secretsShown(l.season().id, l.manche().id);
        List<String> rules = new ArrayList<>();
        for (String r : c.rules()) rules.add(TaskMarkup.redact(r, Integer.MAX_VALUE, readable));
        return new TaskNetwork.Card(c.season(), c.mancheId(), c.mancheName(), c.number(), c.title(), TaskMarkup.redact(c.text(), n, readable),
                rules, c.seconds(), c.wax(), c.revealed());
    }

    /** The card of a round; {@code full}: with its text even if secret (organisers, the envelope's opener). */
    static TaskNetwork.Card card(Located l, boolean full) {
        boolean revealed = isRevealed(l);
        Task t = l.manche().task == null ? new Task() : l.manche().task;
        if (!full && !revealed)
            return new TaskNetwork.Card(l.season().id, l.manche().id, l.manche().name, l.number(), "", "", List.of(), t.shownSeconds(l.manche()), t.wax, false);
        return new TaskNetwork.Card(l.season().id, l.manche().id, l.manche().name, l.number(), t.title, t.text, List.copyOf(t.rules),
                t.shownSeconds(l.manche()), t.wax, revealed);
    }

    // ---------------------------------------------------------------- reveal

    /**
     * Reveals a round's task of the active season to everyone (contract for the other chantiers).
     *
     * @param mancheId the round's id (or name / nickname)
     * @param by       who revealed it (the envelope's opener, the organiser), or null
     * @return false if there is no such round with a task, or it was already revealed
     */
    public static boolean reveal(MinecraftServer server, String mancheId, ServerPlayer by) {
        Located l = locateActive(mancheId);
        return l != null && l.hasTask() && reveal(server, l, by, false);
    }

    /** @param announce tell everyone (organiser's order) */
    static boolean reveal(MinecraftServer server, Located l, ServerPlayer by, boolean announce) {
        if (store == null) return false;
        boolean first = store.setRevealed(l.season().id, l.manche().id, System.currentTimeMillis(), by == null ? null : by.getGameProfile().name());
        if (first) {
            if (announce) {
                Component msg = Component.translatable("message.craftmaster.task_revealed", l.number(), l.manche().name).withStyle(ChatFormatting.GOLD);
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.sendOverlayMessage(msg);
                    Show.sound(p, SoundEvents.NOTE_BLOCK_CHIME, 0.7f, 1.2f);
                }
            }
            ShowEvents.onTaskRevealed(server, l.season().id, l.manche().id, by);
        }
        sync(server);
        return first;
    }

    // ---------------------------------------------------------------- reading

    /** Opens a free card (title + text) on the player's screen: contract for the task props of chantier H. */
    public static void openPaper(ServerPlayer player, String title, String text) {
        send(player, new TaskNetwork.Paper("text", Component.empty(), title == null ? "" : title, Task.cleanText(text), List.of(), 0, "red", "paper",
                0, List.of(), "", 0, ""));
    }

    static void send(ServerPlayer player, TaskNetwork.Paper paper) {
        if (!paper.mancheId().isEmpty() && !paper.has(TaskNetwork.PREVIEW)) {
            // A round held for its start button: its letter carries the button for those still frozen
            if (RoundStart.awaitsButton(player, paper.season(), paper.mancheId()))
                paper = new TaskNetwork.Paper(paper.kind(), paper.header(), paper.title(), paper.text(), paper.rules(), paper.seconds(), paper.wax(),
                        paper.look(), paper.flags() | TaskNetwork.START, paper.pieces(), paper.mancheId(), paper.season(), paper.from());
            // Read without a seal to tear (J, a letter read again): it counts as read now
            if (!paper.has(TaskNetwork.SECRET) && !paper.has(TaskNetwork.ENVELOPE)) RoundStart.letterRead(player, paper.season(), paper.mancheId());
        }
        if (ServerPlayNetworking.canSend(player, TaskNetwork.Paper.TYPE)) {
            ServerPlayNetworking.send(player, paper);
            return;
        }
        // A vanilla game: the card in the chat
        player.sendSystemMessage(Component.literal("— ").append(paper.title().isEmpty() ? paper.header() : Component.literal(paper.title()))
                .append(" —").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (!paper.text().isEmpty()) player.sendSystemMessage(Component.literal(TaskMarkup.plain(paper.text())).withStyle(ChatFormatting.WHITE));
        for (String r : paper.rules()) player.sendSystemMessage(Component.literal(" • " + TaskMarkup.plain(r)).withStyle(ChatFormatting.GRAY));
    }

    /** The paper of a round's task; {@code flags} adds ENVELOPE, CAN_REVEAL, CAN_EDIT... */
    static TaskNetwork.Paper paper(Located l, boolean full, boolean readable, int flags) {
        TaskNetwork.Card c = shown(l, full, readable);
        Component header = Component.translatable("paper.craftmaster.round", l.number(), l.manche().name);
        boolean secret = !full && !c.revealed();
        return new TaskNetwork.Paper("task", header, c.title(), c.text(), c.rules(), c.seconds(), c.wax(), "paper",
                flags | (secret ? TaskNetwork.SECRET : 0), List.of(), l.manche().id, l.season().id, "");
    }

    /** J key or /cm tache: the task of the moment (secret = the sealed card; organisers read it anyway). */
    public static void openCurrent(ServerPlayer player) {
        boolean admin = Permissions.isAdmin(player);
        Located l = taskOfTheMoment(admin);
        if (l == null) {
            player.sendOverlayMessage(Component.translatable("message.craftmaster.no_task").withStyle(ChatFormatting.GRAY));
            return;
        }
        int flags = admin ? TaskNetwork.CAN_EDIT | (isRevealed(l) ? 0 : TaskNetwork.CAN_REVEAL) : 0;
        send(player, paper(l, admin, admin, flags));
    }

    /** An organiser looks at a given round's task (secret or not). */
    public static void openFor(ServerPlayer player, Located l) {
        send(player, paper(l, true, true, TaskNetwork.CAN_EDIT | (isRevealed(l) ? 0 : TaskNetwork.CAN_REVEAL)));
    }

    /**
     * An organiser plays a round's letter on their own screen (seal, opening, the final layout), to check how it
     * looks before the show: nothing is revealed, nobody else sees it.
     */
    public static void preview(ServerPlayer player, Located l) {
        send(player, paper(l, true, true, TaskNetwork.ENVELOPE | TaskNetwork.PREVIEW));
    }

    // ---------------------------------------------------------------- envelopes

    /**
     * Opens an envelope for the player. A sealed one plays the opening animation, becomes "opened" ({@code update}
     * stores it on the item or the block), is announced to everyone if it says so, and reveals its round's task
     * when the card comes out. An opened one is simply read again.
     */
    public static void openEnvelope(ServerPlayer player, TaskComponents.Envelope env, Consumer<TaskComponents.Envelope> update) {
        MinecraftServer server = player.level().getServer();
        boolean first = !env.opened();
        int flags = first ? TaskNetwork.ENVELOPE : 0;
        TaskNetwork.Paper paper;
        Located l = null;
        if (env.linked()) {
            l = locate(env.season(), env.manche());
            if (l == null || !l.hasTask()) {
                player.sendOverlayMessage(Component.translatable("message.craftmaster.envelope_empty").withStyle(ChatFormatting.GRAY));
                return;
            }
            paper = paper(l, true, Permissions.isAdmin(player), flags);
        } else {
            Component header = env.to().isBlank() ? Component.empty() : Component.translatable("paper.craftmaster.to", env.to());
            paper = new TaskNetwork.Paper("text", header, env.title(), env.text(), List.of(), 0, env.wax(), "paper", flags, List.of(), "", 0, "");
        }
        // The card carries the envelope's own wax
        paper = new TaskNetwork.Paper(paper.kind(), paper.header(), paper.title(), paper.text(), paper.rules(), paper.seconds(), env.wax(),
                paper.look(), paper.flags(), paper.pieces(), paper.mancheId(), paper.season(), env.to());
        send(player, paper);
        if (!first) return;
        update.accept(env.withOpened(true));
        player.level().playSound(player, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7f, 1f);
        ShowEvents.onEnvelopeOpened(server, player, env.season(), env.manche());
        final Located located = l;
        Runnable torn = () -> {
            if (env.announce()) {
                Component msg = Component.translatable("message.craftmaster.envelope_opened", player.getGameProfile().name()).withStyle(ChatFormatting.GOLD);
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (p == player) continue;
                    p.sendOverlayMessage(msg);
                    Show.sound(p, Show.holder(SoundEvents.BOOK_PAGE_TURN), 0.8f, 0.9f);
                }
            }
            if (located != null) reveal(server, located, player, false);
            if (env.linked()) RoundStart.letterRead(player, env.season(), env.manche());
        };
        if (ServerPlayNetworking.canSend(player, TaskNetwork.Paper.TYPE)) {
            // The reader tears the seal off: the task is revealed (and announced) when it pops (SealBroken), or
            // after a while if they leave the letter closed
            UUID id = player.getUUID();
            Runnable old = PENDING.put(id, torn);
            if (old != null) old.run();
            later(server, SEAL_TIMEOUT_TICKS, () -> {
                if (PENDING.remove(id, torn)) torn.run();
            });
        } else {
            later(server, ENVELOPE_ANIMATION_TICKS, torn);
        }
    }

    /**
     * The letter put in front of a player of a round held for its start button (RoundStart): the round's sealed
     * envelope, opened on their screen (tearing it reveals the task, as any envelope); without a task written, a
     * card with the round's name. Either carries « Commencer l'épreuve ».
     */
    static void openStartLetter(ServerPlayer player, int season, String mancheId, String name) {
        Located l = locate(season, mancheId);
        if (l != null && l.hasTask()) {
            Task t = l.manche().task;
            openEnvelope(player, new TaskComponents.Envelope(season, mancheId, l.manche().name, "", "", WaxColor.of(t.wax).id(), "", false, false), e -> {});
            return;
        }
        send(player, new TaskNetwork.Paper("text", Component.translatable("paper.craftmaster.round", l == null ? 0 : l.number(), name), name,
                "Prêt ? Clique sur « Commencer l'épreuve ».", List.of(), 0, Task.DEFAULT_WAX, "paper", 0, List.of(), mancheId, season, ""));
    }

    /** The reader's seal popped: what waited for it (reveal, announce) happens now. */
    static void sealBroken(ServerPlayer player) {
        Runnable r = PENDING.remove(player.getUUID());
        if (r != null) r.run();
    }

    /**
     * Reveals the next [secret] part of a round's text: every organiser and, if the task is revealed, every
     * reader gets the new text (an open letter updates in place) and the cards follow.
     */
    static int showNextSecret(MinecraftServer server, Located l) {
        int count = TaskMarkup.secretCount(l.manche().task == null ? "" : l.manche().task.text);
        int n = store.showNextSecret(l.season().id, l.manche().id, count);
        if (n < 0) return -1;
        boolean revealed = isRevealed(l);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean admin = Permissions.isAdmin(p);
            if (!(admin || revealed) || !ServerPlayNetworking.canSend(p, TaskNetwork.PaperUpdate.TYPE)) continue;
            TaskNetwork.Card c = shown(l, true, admin);
            ServerPlayNetworking.send(p, new TaskNetwork.PaperUpdate(l.season().id, l.manche().id, c.text(), c.rules()));
            Show.sound(p, Show.holder(SoundEvents.BOOK_PAGE_TURN), 0.8f, 1.3f);
        }
        sync(server);
        fr.lordfinn.craftmaster.trial.kit.Arenas.onSecretShown(server, l.season().id, l.manche().id);
        return n;
    }

    /** A sealed envelope of a round, addressed to {@code to} (may be empty). */
    public static ItemStack envelope(Located l, String wax, String to, boolean announce) {
        String w = wax != null ? wax : l.manche().task != null ? l.manche().task.wax : Task.DEFAULT_WAX;
        return TaskComponents.setEnvelope(new ItemStack(TaskContent.ENVELOPE),
                new TaskComponents.Envelope(l.season().id, l.manche().id, l.manche().name, "", "", WaxColor.of(w).id(), to == null ? "" : to, announce, false));
    }

    static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) p.spawnAtLocation(p.level(), stack);
    }

    /** Envelopes for the online cast (or the named players). @return the names of who got one */
    static List<String> giveEnvelopes(MinecraftServer server, Located l, List<String> players, String wax) {
        List<String> names = new ArrayList<>(players);
        if (names.isEmpty())
            for (SeasonPlayer sp : l.season().players.values()) if (!sp.archived) names.add(sp.name);
        List<String> given = new ArrayList<>();
        for (String n : names) {
            ServerPlayer p = server.getPlayerList().getPlayerByName(n);
            if (p == null) continue;
            give(p, envelope(l, wax, p.getGameProfile().name(), true));
            p.sendOverlayMessage(Component.translatable("message.craftmaster.envelope_received").withStyle(ChatFormatting.GOLD));
            Show.sound(p, Show.holder(SoundEvents.BOOK_PAGE_TURN), 1f, 1.1f);
            given.add(p.getGameProfile().name());
        }
        return given;
    }

    /** Organisers' wax seal: seals the envelopes and gives them. */
    private static void seal(ServerPlayer player, TaskNetwork.Seal s) {
        if (!Permissions.isAdmin(player)) return;
        WaxColor wax = WaxColor.of(s.wax());
        int count = Math.max(1, Math.min(16, s.count()));
        ItemStack proto;
        String what;
        if (!s.mancheId().isEmpty()) {
            Located l = locateActive(s.mancheId());
            if (l == null) {
                player.sendSystemMessage(Component.translatable("message.craftmaster.round_not_found", s.mancheId()).withStyle(ChatFormatting.RED));
                return;
            }
            proto = envelope(l, wax.id(), s.to().strip(), s.announce());
            what = l.manche().name;
        } else {
            String text = Task.cleanText(s.text());
            if (text.isEmpty() && s.title().isBlank()) {
                player.sendSystemMessage(Component.translatable("message.craftmaster.seal_empty").withStyle(ChatFormatting.RED));
                return;
            }
            proto = TaskComponents.setEnvelope(new ItemStack(TaskContent.ENVELOPE), new TaskComponents.Envelope(0, "", "",
                    s.title().strip(), text, wax.id(), s.to().strip(), s.announce(), false));
            what = s.title().isBlank() ? "texte libre" : s.title().strip();
        }
        for (int i = 0; i < count; i++) give(player, proto.copy());
        for (InteractionHand hand : InteractionHand.values())
            if (player.getItemInHand(hand).getItem() instanceof WaxSealItem) TaskComponents.setSealColor(player.getItemInHand(hand), wax);
        player.level().playSound(null, player.blockPosition(), SoundEvents.HONEYCOMB_WAX_ON, SoundSource.PLAYERS, 1f, 0.8f);
        player.sendSystemMessage(Component.translatable("message.craftmaster.sealed", count, what, Component.translatable("wax.craftmaster." + wax.id()))
                .withStyle(ChatFormatting.GREEN));
    }

    // ---------------------------------------------------------------- organisers' screens

    /** Task editor (mode 0) or wax seal (mode 1), on {@code selected} (round id) or the current / next round. */
    public static void openEditor(ServerPlayer player, int mode, String selected) {
        if (!ServerPlayNetworking.canSend(player, TaskNetwork.OpenEditor.TYPE)) {
            player.sendSystemMessage(Component.translatable("message.craftmaster.no_client_mod").withStyle(ChatFormatting.RED));
            return;
        }
        CraftMasterData data = CraftMaster.data();
        Season s = data.activeSeason();
        Partie p = s == null ? null : s.activePartie();
        Located cur = current();
        List<TaskNetwork.Round> rounds = new ArrayList<>();
        if (p != null) {
            for (Manche m : p.activeManches()) {
                Located l = new Located(s, p, m);
                rounds.add(new TaskNetwork.Round(card(l, true), l.hasTask(), m.task == null || m.task.revealOnStart,
                        cur != null && cur.manche() == m, m.task == null ? 0 : m.task.seconds));
            }
            // The round being played may be in another game: listed too
            if (cur != null && cur.partie() != p) rounds.add(new TaskNetwork.Round(card(cur, true), cur.hasTask(),
                    cur.manche().task == null || cur.manche().task.revealOnStart, true, cur.manche().task == null ? 0 : cur.manche().task.seconds));
        }
        if (selected == null) {
            Located l = taskOfTheMoment(true);
            selected = cur != null ? cur.manche().id : l != null ? l.manche().id : "";
        }
        List<String> cast = s == null ? List.of() : s.players.values().stream().filter(x -> !x.archived).map(x -> x.name).toList();
        ServerPlayNetworking.send(player, new TaskNetwork.OpenEditor(mode, rounds, selected, cast));
    }

    /** Actions sent by an organiser's screen: run like the commands, answered in the chat. */
    private static void runActions(ServerPlayer player, String json) {
        if (!Permissions.isAdmin(player)) return;
        List<Map<String, Object>> actions;
        try {
            actions = GSON.fromJson(json, new TypeToken<List<Map<String, Object>>>() {}.getType());
        } catch (RuntimeException e) {
            player.sendSystemMessage(Component.literal("Actions illisibles : " + e.getMessage()).withStyle(ChatFormatting.RED));
            return;
        }
        if (actions == null) return;
        ActionExecutor.Result r = ActionExecutor.execute(player, actions);
        for (String line : r.lines())
            player.sendSystemMessage(Component.literal(line).withStyle(r.ok() ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    // ---------------------------------------------------------------- task actions (ActionExecutor)

    public static void play(MinecraftServer server, Actions.TaskEffect effect, List<String> lines) {
        switch (effect) {
            case Actions.TaskRevealed r -> {
                Located l = locate(r.season(), r.manche());
                if (l == null) return;
                boolean first = reveal(server, l, null, r.announce());
                lines.add(first ? "Tâche de « " + l.manche().name + " » révélée à tous."
                        : "La tâche de « " + l.manche().name + " » était déjà révélée.");
            }
            case Actions.TaskHidden h -> {
                Located l = locate(h.season(), h.manche());
                if (l == null) return;
                boolean was = store.hide(h.season(), h.manche());
                sync(server);
                lines.add(was ? "Tâche de « " + l.manche().name + " » de nouveau secrète." : "La tâche de « " + l.manche().name + " » n'était pas révélée.");
            }
            case Actions.TaskSecretShown t -> {
                Located l = locate(t.season(), t.manche());
                if (l == null) return;
                int n = showNextSecret(server, l);
                int count = TaskMarkup.secretCount(l.manche().task.text);
                lines.add(n < 0 ? "Toutes les parties cachées de « " + l.manche().name + " » sont déjà révélées."
                        : "Partie cachée " + n + "/" + count + " de « " + l.manche().name + " » révélée"
                        + (isRevealed(l) ? "." : " (les joueurs la verront quand la tâche sera révélée)."));
            }
            case Actions.TaskShown s -> {
                Located l = locate(s.season(), s.manche());
                if (l == null) return;
                if (!isRevealed(l)) {
                    reveal(server, l, null, false);
                    lines.add("Tâche de « " + l.manche().name + " » révélée.");
                }
                showCard(server, l, s.seconds() * 20);
            }
            case Actions.EnvelopesGiven g -> {
                Located l = locate(g.season(), g.manche());
                if (l == null) return;
                List<String> given = giveEnvelopes(server, l, g.players(), g.wax());
                lines.add(given.isEmpty() ? "⚠ Aucun destinataire connecté pour les enveloppes de « " + l.manche().name + " »."
                        : "Enveloppe(s) de « " + l.manche().name + " » donnée(s) à : " + String.join(", ", given) + ".");
            }
        }
    }

    /** Everyone's screen shows the round's task card for a while (the TV overlay and !tache use the same card). */
    static void showCard(MinecraftServer server, Located l, int ticks) {
        sync(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (ServerPlayNetworking.canSend(p, TaskNetwork.ShowCard.TYPE)) ServerPlayNetworking.send(p, new TaskNetwork.ShowCard(ticks));
    }

    // ---------------------------------------------------------------- sync and tick

    /** Sends the task of the moment to every player whose view changed. */
    public static void sync(MinecraftServer server) {
        if (store == null || CraftMaster.data() == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sync(p);
    }

    static void sync(ServerPlayer p) {
        if (store == null || CraftMaster.data() == null || !ServerPlayNetworking.canSend(p, TaskNetwork.State.TYPE)) return;
        boolean admin = Permissions.isAdmin(p);
        Located l = taskOfTheMoment(admin);
        TaskNetwork.State state = new TaskNetwork.State(l == null ? TaskNetwork.Card.NONE : shown(l, admin, admin), admin);
        if (state.equals(SENT.get(p.getUUID()))) return;
        SENT.put(p.getUUID(), state);
        ServerPlayNetworking.send(p, state);
    }

    /** Runs {@code r} on the server thread in {@code ticks} ticks. */
    public static void later(MinecraftServer server, int ticks, Runnable r) {
        SCHEDULED.add(new Scheduled(server.getTickCount() + ticks, r));
    }

    private static void tick(MinecraftServer server) {
        if (!SCHEDULED.isEmpty()) {
            long now = server.getTickCount();
            List<Scheduled> due = SCHEDULED.stream().filter(s -> s.at() <= now).toList();
            SCHEDULED.removeAll(due);
            for (Scheduled s : due) {
                try {
                    s.run().run();
                } catch (RuntimeException e) {
                    CraftMaster.LOG.error("[Tâches] Erreur dans une action différée", e);
                }
            }
        }
        // Rights may change (op / deop) and rounds may start from anywhere: a cheap check once a second
        if (server.getTickCount() % 20 == 0) sync(server);
        Notes.tick(server);
    }
}
