package fr.lordfinn.craftmaster.task;

import fr.lordfinn.craftmaster.CraftMaster;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Packets of the tasks, envelopes and notes (chantier D).
 * <pre>
 * server → client  State(card, admin)         the task of the moment, for the J key, the HUD card and the TV
 *                                             (a secret task reaches non-admins without its text)
 *                  OpenPaper(paper)           read a card: task (with the envelope animation), note, free text
 *                  ShowCard(ticks)            the task card on screen for a while (task.show, !tache)
 *                  OpenEditor(mode, rounds)   organisers: task editor (mode 0) or wax seal (mode 1)
 *                  OpenNote(note)             organisers: note editor
 * client → server  RequestTask()              J pressed: open the task of the moment
 *                  RunActions(json)           organisers' screens: actions for ActionExecutor
 *                  SaveNote(note)             organisers: create / change a note
 *                  NoteOrder(id, order)       organisers: give the tool, bottle, reset, unhide, delete
 *                  Seal(...)                  organisers: seal envelopes with the wax seal
 * </pre>
 */
public final class TaskNetwork {

    private TaskNetwork() {}

    static final int MAX_TEXT = 8000;

    static void writeList(FriendlyByteBuf buf, List<String> list) {
        buf.writeVarInt(list.size());
        for (String s : list) buf.writeUtf(s, MAX_TEXT);
    }

    static void writeComponents(RegistryFriendlyByteBuf buf, List<Component> list) {
        buf.writeVarInt(list.size());
        for (Component c : list) ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, c);
    }

    static List<Component> readComponents(RegistryFriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 512);
        List<Component> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf));
        return out;
    }

    static List<String> readList(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 256);
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(buf.readUtf(MAX_TEXT));
        return out;
    }

    /**
     * A round's task as a player may see it. {@code revealed} false for a non-admin: title, text and rules are
     * empty (the secret never leaves the server). {@code seconds}: the duration to show (0 = none).
     */
    public record Card(int season, String mancheId, String mancheName, int number, String title, String text,
                       List<String> rules, int seconds, String wax, boolean revealed) {
        public static final Card NONE = new Card(0, "", "", 0, "", "", List.of(), 0, "red", false);

        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(season);
            buf.writeUtf(mancheId);
            buf.writeUtf(mancheName);
            buf.writeVarInt(number);
            buf.writeUtf(title);
            buf.writeUtf(text, MAX_TEXT);
            writeList(buf, rules);
            buf.writeVarInt(seconds);
            buf.writeUtf(wax);
            buf.writeBoolean(revealed);
        }

        static Card read(FriendlyByteBuf buf) {
            return new Card(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readUtf(), buf.readUtf(MAX_TEXT),
                    readList(buf), buf.readVarInt(), buf.readUtf(), buf.readBoolean());
        }

        public boolean present() {
            return !mancheId.isEmpty();
        }
    }

    /** Server → client: the task of the moment ({@link Card#NONE} = none); {@code admin}: this player organises. */
    public record State(Card card, boolean admin) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(CraftMaster.id("task_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    p.card.write(buf);
                    buf.writeBoolean(p.admin);
                }, buf -> new State(Card.read(buf), buf.readBoolean()));

        @Override
        public Type<State> type() { return TYPE; }
    }

    /** Flags of a {@link Paper}. */
    public static final int ENVELOPE = 1, INK = 2, LIT = 4, SECRET = 8, CAN_REVEAL = 16, FOUND = 32, CAN_EDIT = 64;

    /**
     * Something to read on the paper screen. {@code kind}: "task", "note" or "text". {@code header}: small line
     * above the title ("Manche 3 · Le Pont", "Trouvée sous le tapis"). {@code pieces}: for a torn note, the text of
     * each piece, empty for the ones the reader has not found ({@code text} is then empty unless all are found).
     * {@code look}: paper look (Note.LOOKS). {@code mancheId} / {@code season}: the task, for the admin buttons.
     */
    public record Paper(String kind, Component header, String title, String text, List<String> rules, int seconds,
                        String wax, String look, int flags, List<String> pieces, String mancheId, int season, String from)
            implements CustomPacketPayload {
        public static final Type<Paper> TYPE = new Type<>(CraftMaster.id("task_paper"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Paper> CODEC = CustomPacketPayload.codec(Paper::write, Paper::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(kind);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, header);
            buf.writeUtf(title);
            buf.writeUtf(text, MAX_TEXT);
            writeList(buf, rules);
            buf.writeVarInt(seconds);
            buf.writeUtf(wax);
            buf.writeUtf(look);
            buf.writeVarInt(flags);
            writeList(buf, pieces);
            buf.writeUtf(mancheId);
            buf.writeVarInt(season);
            buf.writeUtf(from);
        }

        private static Paper read(RegistryFriendlyByteBuf buf) {
            return new Paper(buf.readUtf(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readUtf(), buf.readUtf(MAX_TEXT), readList(buf), buf.readVarInt(),
                    buf.readUtf(), buf.readUtf(), buf.readVarInt(), readList(buf), buf.readUtf(), buf.readVarInt(), buf.readUtf());
        }

        public boolean has(int flag) {
            return (flags & flag) != 0;
        }

        @Override
        public Type<Paper> type() { return TYPE; }
    }

    /** Server → client: show the task card over the game for {@code ticks}. */
    public record ShowCard(int ticks) implements CustomPacketPayload {
        public static final Type<ShowCard> TYPE = new Type<>(CraftMaster.id("task_show_card"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShowCard> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeVarInt(p.ticks), buf -> new ShowCard(buf.readVarInt()));

        @Override
        public Type<ShowCard> type() { return TYPE; }
    }

    /** Server → client: close the card being read (and the task screens), e.g. when the round starts. */
    public record CloseCard() implements CustomPacketPayload {
        public static final Type<CloseCard> TYPE = new Type<>(CraftMaster.id("task_close_card"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CloseCard> CODEC = StreamCodec.unit(new CloseCard());

        @Override
        public Type<CloseCard> type() { return TYPE; }
    }

    /**
     * A round in the organisers' screens, with its task (full text) and settings. {@code ownSeconds}: the task's own
     * duration (0 = the card shows the round's timer, which is {@code card.seconds}).
     */
    public record Round(Card card, boolean hasTask, boolean revealOnStart, boolean current, int ownSeconds) {
        void write(FriendlyByteBuf buf) {
            card.write(buf);
            buf.writeBoolean(hasTask);
            buf.writeBoolean(revealOnStart);
            buf.writeBoolean(current);
            buf.writeVarInt(ownSeconds);
        }

        static Round read(FriendlyByteBuf buf) {
            return new Round(Card.read(buf), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
        }
    }

    /** Server → client (organisers): the task editor (mode 0) or the wax seal (mode 1), on {@code selected}. */
    public record OpenEditor(int mode, List<Round> rounds, String selected, List<String> cast) implements CustomPacketPayload {
        public static final Type<OpenEditor> TYPE = new Type<>(CraftMaster.id("task_open_editor"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenEditor> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.mode);
                    buf.writeVarInt(p.rounds.size());
                    for (Round r : p.rounds) r.write(buf);
                    buf.writeUtf(p.selected);
                    writeList(buf, p.cast);
                }, buf -> {
                    int mode = buf.readVarInt();
                    int n = Math.min(buf.readVarInt(), 512);
                    List<Round> rounds = new ArrayList<>();
                    for (int i = 0; i < n; i++) rounds.add(Round.read(buf));
                    return new OpenEditor(mode, rounds, buf.readUtf(), readList(buf));
                });

        @Override
        public Type<OpenEditor> type() { return TYPE; }
    }

    /**
     * A note in its editor: settings, then (server → client only) its hiding places and finds, translated by the client.
     * {@code id} empty = a new note.
     */
    public record NoteView(String id, String title, String text, String look, String readers, List<String> players,
                           boolean once, boolean ink, int pieces, List<Component> spots, List<Component> finds) {
        void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeUtf(title);
            buf.writeUtf(text, MAX_TEXT);
            buf.writeUtf(look);
            buf.writeUtf(readers);
            writeList(buf, players);
            buf.writeBoolean(once);
            buf.writeBoolean(ink);
            buf.writeVarInt(pieces);
            writeComponents(buf, spots);
            writeComponents(buf, finds);
        }

        static NoteView read(RegistryFriendlyByteBuf buf) {
            return new NoteView(buf.readUtf(), buf.readUtf(), buf.readUtf(MAX_TEXT), buf.readUtf(), buf.readUtf(), readList(buf),
                    buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), readComponents(buf), readComponents(buf));
        }
    }

    public record OpenNote(NoteView note) implements CustomPacketPayload {
        public static final Type<OpenNote> TYPE = new Type<>(CraftMaster.id("task_open_note"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenNote> CODEC = CustomPacketPayload.codec(
                (p, buf) -> p.note.write(buf), buf -> new OpenNote(NoteView.read(buf)));

        @Override
        public Type<OpenNote> type() { return TYPE; }
    }

    public record RequestTask() implements CustomPacketPayload {
        public static final Type<RequestTask> TYPE = new Type<>(CraftMaster.id("task_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestTask> CODEC = StreamCodec.unit(new RequestTask());

        @Override
        public Type<RequestTask> type() { return TYPE; }
    }

    /** Client → server (organisers): a JSON list of actions, run like the commands and the voice control. */
    public record RunActions(String json) implements CustomPacketPayload {
        public static final Type<RunActions> TYPE = new Type<>(CraftMaster.id("task_run_actions"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RunActions> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeUtf(p.json, 32000), buf -> new RunActions(buf.readUtf(32000)));

        @Override
        public Type<RunActions> type() { return TYPE; }
    }

    public record SaveNote(NoteView note) implements CustomPacketPayload {
        public static final Type<SaveNote> TYPE = new Type<>(CraftMaster.id("task_save_note"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SaveNote> CODEC = CustomPacketPayload.codec(
                (p, buf) -> p.note.write(buf), buf -> new SaveNote(NoteView.read(buf)));

        @Override
        public Type<SaveNote> type() { return TYPE; }
    }

    /** {@code order}: give | bottle | reset | unhide | delete. */
    public record NoteOrder(String id, String order) implements CustomPacketPayload {
        public static final Type<NoteOrder> TYPE = new Type<>(CraftMaster.id("task_note_order"));
        public static final StreamCodec<RegistryFriendlyByteBuf, NoteOrder> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeUtf(p.id);
                    buf.writeUtf(p.order);
                }, buf -> new NoteOrder(buf.readUtf(), buf.readUtf()));

        @Override
        public Type<NoteOrder> type() { return TYPE; }
    }

    /**
     * Client → server (organisers): seal {@code count} envelopes. {@code mancheId} empty = free text
     * ({@code title}, {@code text}); {@code to}: name written on it (may be empty).
     */
    public record Seal(String mancheId, String title, String text, String wax, String to, int count, boolean announce)
            implements CustomPacketPayload {
        public static final Type<Seal> TYPE = new Type<>(CraftMaster.id("task_seal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Seal> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeUtf(p.mancheId);
                    buf.writeUtf(p.title);
                    buf.writeUtf(p.text, MAX_TEXT);
                    buf.writeUtf(p.wax);
                    buf.writeUtf(p.to);
                    buf.writeVarInt(p.count);
                    buf.writeBoolean(p.announce);
                }, buf -> new Seal(buf.readUtf(), buf.readUtf(), buf.readUtf(MAX_TEXT), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readBoolean()));

        @Override
        public Type<Seal> type() { return TYPE; }
    }

    static void registerTypes() {
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Paper.TYPE, Paper.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ShowCard.TYPE, ShowCard.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenEditor.TYPE, OpenEditor.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenNote.TYPE, OpenNote.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CloseCard.TYPE, CloseCard.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestTask.TYPE, RequestTask.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RunActions.TYPE, RunActions.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SaveNote.TYPE, SaveNote.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(NoteOrder.TYPE, NoteOrder.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Seal.TYPE, Seal.CODEC);
    }
}
