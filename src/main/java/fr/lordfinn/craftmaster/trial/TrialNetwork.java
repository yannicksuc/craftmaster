package fr.lordfinn.craftmaster.trial;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Live;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Partie;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.data.SeasonPlayer;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Packets of the task tools: the task prop settings screen, the text of a drawing board, and the results sheet
 * screen (whose changes are actions run by {@link ActionExecutor}, like the voice: undoable). Everything an
 * organiser sends is checked against {@link Permissions}.
 */
public final class TrialNetwork {

    private TrialNetwork() {}

    private static final int MAX_JSON = 1 << 16;

    /** Server → client: open the settings of a prop. {@code stats}: lines describing its memory. */
    public record OpenProp(int entityId, String config, List<String> stats, String give) implements CustomPacketPayload {
        public static final Type<OpenProp> TYPE = new Type<>(CraftMaster.id("prop_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenProp> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.entityId);
                    buf.writeUtf(p.config, MAX_JSON);
                    buf.writeVarInt(p.stats.size());
                    p.stats.forEach(s -> buf.writeUtf(s, 512));
                    buf.writeUtf(p.give, 512);
                }, buf -> {
                    int id = buf.readVarInt();
                    String config = buf.readUtf(MAX_JSON);
                    List<String> stats = new ArrayList<>();
                    for (int i = Math.min(buf.readVarInt(), 64); i > 0; i--) stats.add(buf.readUtf(512));
                    return new OpenProp(id, config, stats, buf.readUtf(512));
                });

        @Override
        public Type<OpenProp> type() { return TYPE; }
    }

    /** Client → server: new settings of a prop. */
    public record SaveProp(int entityId, String config) implements CustomPacketPayload {
        public static final Type<SaveProp> TYPE = new Type<>(CraftMaster.id("prop_save"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SaveProp> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.entityId);
                    buf.writeUtf(p.config, MAX_JSON);
                }, buf -> new SaveProp(buf.readVarInt(), buf.readUtf(MAX_JSON)));

        @Override
        public Type<SaveProp> type() { return TYPE; }
    }

    /**
     * Client → server: a button of the prop screen: "look_hand" (look = item in hand), "give_hand" (item given =
     * item in hand), "tp_here" (teleport target = where the organiser stands), "reset_stats", "remove", "test".
     */
    public record PropAction(int entityId, String action, String config) implements CustomPacketPayload {
        public static final Type<PropAction> TYPE = new Type<>(CraftMaster.id("prop_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PropAction> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.entityId);
                    buf.writeUtf(p.action, 32);
                    buf.writeUtf(p.config, MAX_JSON);
                }, buf -> new PropAction(buf.readVarInt(), buf.readUtf(32), buf.readUtf(MAX_JSON)));

        @Override
        public Type<PropAction> type() { return TYPE; }
    }

    /** Server → client: write on that board (the screen reads the current text from the entity). */
    public record OpenBoard(int entityId) implements CustomPacketPayload {
        public static final Type<OpenBoard> TYPE = new Type<>(CraftMaster.id("board_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenBoard> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeVarInt(p.entityId), buf -> new OpenBoard(buf.readVarInt()));

        @Override
        public Type<OpenBoard> type() { return TYPE; }
    }

    /** Client → server: the new text of a board. */
    public record BoardText(int entityId, String text) implements CustomPacketPayload {
        public static final Type<BoardText> TYPE = new Type<>(CraftMaster.id("board_text"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BoardText> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.entityId);
                    buf.writeUtf(p.text, 2048);
                }, buf -> new BoardText(buf.readVarInt(), buf.readUtf(2048)));

        @Override
        public Type<BoardText> type() { return TYPE; }
    }

    /** Client → server: send me the results sheet of that round ("" = the current one). */
    public record RequestSheet(String manche) implements CustomPacketPayload {
        public static final Type<RequestSheet> TYPE = new Type<>(CraftMaster.id("sheet_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestSheet> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeUtf(p.manche, 256), buf -> new RequestSheet(buf.readUtf(256)));

        @Override
        public Type<RequestSheet> type() { return TYPE; }
    }

    /**
     * Server → client: a results sheet (JSON, see {@link #sheetJson}). {@code open}: open the screen; otherwise
     * only refresh one already open.
     */
    public record Sheet(boolean open, String json) implements CustomPacketPayload {
        public static final Type<Sheet> TYPE = new Type<>(CraftMaster.id("sheet"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sheet> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeBoolean(p.open);
                    buf.writeUtf(p.json, MAX_JSON);
                }, buf -> new Sheet(buf.readBoolean(), buf.readUtf(MAX_JSON)));

        @Override
        public Type<Sheet> type() { return TYPE; }
    }

    /** Client → server: actions to run (the results screen's buttons), JSON list as for the voice. */
    public record RunActions(String json) implements CustomPacketPayload {
        public static final Type<RunActions> TYPE = new Type<>(CraftMaster.id("run_actions"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RunActions> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeUtf(p.json, MAX_JSON), buf -> new RunActions(buf.readUtf(MAX_JSON)));

        @Override
        public Type<RunActions> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(OpenProp.TYPE, OpenProp.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SaveProp.TYPE, SaveProp.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PropAction.TYPE, PropAction.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenBoard.TYPE, OpenBoard.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BoardText.TYPE, BoardText.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestSheet.TYPE, RequestSheet.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Sheet.TYPE, Sheet.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RunActions.TYPE, RunActions.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SaveProp.TYPE, (p, ctx) -> saveProp(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(PropAction.TYPE, (p, ctx) -> propAction(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(BoardText.TYPE, (p, ctx) -> boardText(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(RequestSheet.TYPE, (p, ctx) -> sendSheet(ctx.player(), p.manche(), true));
        ServerPlayNetworking.registerGlobalReceiver(RunActions.TYPE, (p, ctx) -> runActions(ctx.player(), p.json()));
    }

    private static boolean noMod(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        if (ServerPlayNetworking.canSend(player, type)) return false;
        player.sendSystemMessage(Component.translatable("message.craftmaster.no_client_mod").withStyle(ChatFormatting.RED));
        return true;
    }

    // ---------------------------------------------------------------- props

    public static void openProp(ServerPlayer player, TrialPropEntity prop) {
        if (!Permissions.isAdmin(player) || noMod(player, OpenProp.TYPE)) return;
        ServerPlayNetworking.send(player, new OpenProp(prop.getId(), prop.config().toJson(), statsLines(prop), giveText(prop)));
    }

    private static String giveText(TrialPropEntity prop) {
        ItemStack s = prop.giveStack();
        return s.isEmpty() ? "" : s.getHoverName().getString() + " (" + BuiltInRegistries.ITEM.getKey(s.getItem()) + ")";
    }

    /** "Utilisé 12 fois — premier : Ensuri à 14:03:07" then one line per player (first use, count). */
    private static List<String> statsLines(TrialPropEntity prop) {
        PropStats s = prop.stats();
        List<String> out = new ArrayList<>();
        if (s.total() == 0) {
            out.add("Personne ne l'a encore utilisé.");
            return out;
        }
        out.add("Utilisé " + s.total() + " fois — premier : " + s.first() + " à " + TrialLog.clock(s.firstTime()));
        int i = 1;
        for (String p : s.order()) {
            out.add(i++ + ". " + p + " — " + TrialLog.clock(s.firstTimes().get(p)) + ", " + s.clicks().getOrDefault(p, 0) + " fois");
            if (out.size() > 30) break;
        }
        return out;
    }

    private static TrialPropEntity prop(ServerPlayer player, int id) {
        if (!Permissions.isAdmin(player)) return null;
        return player.level().getEntity(id) instanceof TrialPropEntity p && p.distanceToSqr(player) < 96 * 96 ? p : null;
    }

    private static void saveProp(ServerPlayer player, SaveProp s) {
        TrialPropEntity p = prop(player, s.entityId());
        if (p == null) return;
        p.setConfig(PropConfig.fromJson(s.config()));
        player.sendOverlayMessage(Component.literal("Objet d'épreuve enregistré").withStyle(ChatFormatting.GREEN));
    }

    private static void propAction(ServerPlayer player, PropAction a) {
        TrialPropEntity p = prop(player, a.entityId());
        if (p == null) return;
        // The screen's current (unsaved) settings come along, so a button never loses what was typed
        PropConfig c = PropConfig.fromJson(a.config());
        ItemStack hand = player.getMainHandItem().is(TrialContent.PROP_WAND) ? player.getOffhandItem() : player.getMainHandItem();
        switch (a.action()) {
            case "look_hand" -> {
                if (hand.isEmpty()) {
                    player.sendOverlayMessage(Component.literal("Prends en main l'objet à montrer").withStyle(ChatFormatting.RED));
                    return;
                }
                p.setLook(hand);
            }
            case "give_hand" -> {
                p.setGiveStack(hand);
                c.giveItem = hand.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(hand.getItem()).toString();
                c.giveCount = Math.max(1, hand.getCount());
            }
            case "tp_here" -> {
                c.teleport = true;
                c.tpX = Math.round(player.getX() * 100) / 100.0;
                c.tpY = Math.round(player.getY() * 100) / 100.0;
                c.tpZ = Math.round(player.getZ() * 100) / 100.0;
                c.tpYaw = Math.round(player.getYRot());
                c.tpPitch = Math.round(player.getXRot());
            }
            case "reset_stats" -> {
                p.stats().reset();
                player.sendOverlayMessage(Component.literal("Compteurs remis à zéro").withStyle(ChatFormatting.YELLOW));
            }
            case "remove" -> {
                p.discard();
                player.sendOverlayMessage(Component.literal("Objet d'épreuve supprimé").withStyle(ChatFormatting.YELLOW));
                return;
            }
            case "test" -> {
                p.setConfig(c);
                TrialProps.use(p, player);
                return;
            }
            default -> {
                return;
            }
        }
        p.setConfig(c);
        openProp(player, p);
    }

    // ---------------------------------------------------------------- boards

    public static void openBoardText(ServerPlayer player, BoardEntity board) {
        if (noMod(player, OpenBoard.TYPE)) return;
        ServerPlayNetworking.send(player, new OpenBoard(board.getId()));
    }

    private static void boardText(ServerPlayer player, BoardText t) {
        if (!(player.level().getEntity(t.entityId()) instanceof BoardEntity b) || b.distanceToSqr(player) > 16 * 16) return;
        if (b.locked() && !Permissions.isAdmin(player)) return;
        b.setText(t.text());
    }

    // ---------------------------------------------------------------- results sheet

    private record Target(Season season, Partie partie, Manche manche) {}

    /** The round of the sheet: the one asked (by id, in the active game), else the current one, else the first one. */
    private static Target target(String mancheId) {
        CraftMasterData data = CraftMaster.data();
        Season s = data == null ? null : data.activeSeason();
        if (s == null) return null;
        Live.Current cur = data.live().current;
        Partie p = cur != null && cur.season() == s.id && s.parties.containsKey(cur.partie()) ? s.parties.get(cur.partie()) : s.activePartie();
        if (p == null) return null;
        Manche m = mancheId == null || mancheId.isEmpty() ? null : p.manche(mancheId);
        if (m == null) m = data.currentManche() != null && p.manches.contains(data.currentManche()) ? data.currentManche() : null;
        if (m == null && !p.activeManches().isEmpty()) m = p.activeManches().getFirst();
        return m == null ? null : new Target(s, p, m);
    }

    /**
     * The sheet as the screen shows it: {@code season, partie, manche {id, name, number, current}, manches [{id,
     * name}], kind, kinds [{id, name}], lowerWins, scale, rows [{player, online, value, text, dq, place, points,
     * score, running (ms or -1), last (ms or -1)}]}. Null if there is no round.
     */
    public static String sheetJson(MinecraftServer server, String mancheId) {
        Target t = target(mancheId);
        if (t == null) return null;
        ResultSheet r = t.manche().results;
        JsonObject o = new JsonObject();
        o.addProperty("season", t.season().name);
        o.addProperty("partie", t.partie().name);
        JsonObject m = new JsonObject();
        m.addProperty("id", t.manche().id);
        m.addProperty("name", t.manche().name);
        m.addProperty("number", t.partie().number(t.manche()));
        m.addProperty("current", t.manche() == CraftMaster.data().currentManche());
        o.add("manche", m);
        JsonArray list = new JsonArray();
        for (Manche x : t.partie().activeManches()) {
            JsonObject e = new JsonObject();
            e.addProperty("id", x.id);
            e.addProperty("name", x.name);
            list.add(e);
        }
        o.add("manches", list);
        o.addProperty("kind", r.kind.id);
        o.addProperty("lowerWins", r.lowerWins());
        o.addProperty("scale", r.scaleOrDefault());
        List<String> cast = new ArrayList<>();
        for (SeasonPlayer p : t.season().players.values()) if (!p.archived) cast.add(p.name);
        long now = System.currentTimeMillis();
        JsonArray rows = new JsonArray();
        for (ResultSheet.Placed pl : r.rank(cast)) {
            JsonObject row = new JsonObject();
            row.addProperty("player", pl.player());
            row.addProperty("online", server.getPlayerList().getPlayerByName(pl.player()) != null);
            if (pl.value() != null) {
                row.addProperty("value", pl.value());
                row.addProperty("text", r.format(pl.value()));
            }
            row.addProperty("dq", pl.dq());
            row.addProperty("place", pl.place());
            if (pl.points() != null) row.addProperty("points", pl.points());
            if (t.partie().hasScore(t.manche().id, pl.player())) row.addProperty("score", t.partie().score(t.manche().id, pl.player()));
            String w = Stopwatch.SHARED.find(pl.player());
            row.addProperty("running", w == null ? -1 : Stopwatch.SHARED.peek(w, now));
            row.addProperty("last", w == null ? -1 : Stopwatch.SHARED.last(w));
            rows.add(row);
        }
        o.add("rows", rows);
        return o.toString();
    }

    /** Opens (or refreshes) the results sheet screen of an organiser. */
    public static void sendSheet(ServerPlayer player, String mancheId, boolean open) {
        if (!Permissions.isAdmin(player) || CraftMaster.data() == null) return;
        if (open && noMod(player, Sheet.TYPE)) return;
        if (!ServerPlayNetworking.canSend(player, Sheet.TYPE)) return;
        String json = sheetJson(player.level().getServer(), mancheId);
        if (json == null) {
            if (open) player.sendSystemMessage(Component.literal("Aucune manche dans la partie active : créez-en une d'abord.").withStyle(ChatFormatting.RED));
            return;
        }
        ServerPlayNetworking.send(player, new Sheet(open, json));
    }

    /**
     * After any change: organisers whose sheet screen is open get it again (their game ignores it otherwise). The
     * screen asks again for its own round, so only a refresh of the current round is pushed here.
     */
    public static void refreshSheets(MinecraftServer server) {
        if (CraftMaster.data() == null) return;
        String json = null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(p, Sheet.TYPE) || !Permissions.isAdmin(p)) continue;
            if (json == null) json = sheetJson(server, "");
            if (json == null) return;
            ServerPlayNetworking.send(p, new Sheet(false, json));
        }
    }

    private static void runActions(ServerPlayer player, String json) {
        if (!Permissions.isAdmin(player)) return;
        List<Map<String, Object>> actions = new ArrayList<>();
        try {
            JsonElement e = JsonParser.parseString(json);
            for (JsonElement a : e.getAsJsonArray()) {
                Map<String, Object> m = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> f : a.getAsJsonObject().entrySet()) {
                    JsonElement v = f.getValue();
                    if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) m.put(f.getKey(), v.getAsBoolean());
                    else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) m.put(f.getKey(), v.getAsDouble());
                    else if (!v.isJsonNull()) m.put(f.getKey(), v.getAsString());
                }
                actions.add(m);
            }
        } catch (RuntimeException ex) {
            return;
        }
        if (actions.isEmpty() || actions.size() > 20) return;
        ActionExecutor.Result r = ActionExecutor.execute(player, actions);
        // Errors in red in the chat; what was done in the action bar (the screen shows the result)
        if (!r.ok()) for (String l : r.lines()) player.sendSystemMessage(Component.literal(l).withStyle(ChatFormatting.RED));
        else if (!r.lines().isEmpty()) player.sendOverlayMessage(Component.literal(r.lines().getLast()).withStyle(ChatFormatting.GREEN));
        // The screen shows another round than the current one: refresh it too
        String asked = actions.getFirst().get("manche") == null ? "" : String.valueOf(actions.getFirst().get("manche"));
        if (!asked.isEmpty()) sendSheet(player, asked, false);
    }
}
