package fr.lordfinn.craftmaster.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.show.ShowContext;
import fr.lordfinn.craftmaster.trial.Debug;
import fr.lordfinn.craftmaster.trial.kit.Arena;
import fr.lordfinn.craftmaster.trial.kit.Arenas;
import fr.lordfinn.craftmaster.trial.kit.InventoryCam;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * The CraftMaster menu (key N, {@code /cm menu}; client: MainMenuScreen): the server opens it ({@link Open}), the
 * open menu asks for the show's state once a second ({@link Request}) and gets it as JSON ({@link State}). Its
 * buttons send ordinary {@code /cm} commands: same rights, same feedback, undoable like the rest.
 */
public final class MenuNetwork {

    private MenuNetwork() {}

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    /** Server → player: open the menu on this page ("" = the main page). */
    public record Open(String page) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(CraftMaster.id("menu_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeUtf(p.page, 64), buf -> new Open(buf.readUtf(64)));

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    /** Player → server: "send me the state". */
    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(CraftMaster.id("menu_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = CustomPacketPayload.codec((p, buf) -> {}, buf -> new Request());

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    /** Server → player: the show's state for the menu. */
    public record State(String json) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(CraftMaster.id("menu_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeUtf(p.json, 1 << 20), buf -> new State(buf.readUtf(1 << 20)));

        @Override
        public Type<State> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (payload, ctx) ->
                ServerPlayNetworking.send(ctx.player(), new State(GSON.toJson(state(ctx.player())))));
    }

    public static boolean canOpen(ServerPlayer p) {
        return ServerPlayNetworking.canSend(p, Open.TYPE);
    }

    public static void open(ServerPlayer p, String page) {
        ServerPlayNetworking.send(p, new Open(page == null ? "" : page));
    }

    static JsonObject state(ServerPlayer p) {
        JsonObject out = new JsonObject();
        boolean admin = Permissions.isAdmin(p);
        out.addProperty("admin", admin);
        out.addProperty("camera", Permissions.canDriveCameras(p));
        out.add("spectators", fr.lordfinn.craftmaster.show.Spectators.state(p));
        out.add("chronos", fr.lordfinn.craftmaster.trial.ChronoBars.state(p));
        out.add("apercu", fr.lordfinn.craftmaster.trial.kit.DrawingPreview.state(p));
        if (!admin) return out;
        JsonObject ctx = GSON.toJsonTree(ShowContext.build(p.level().getServer(), p)).getAsJsonObject();
        // Only the active season, without its score tables (the score entry screen has them)
        JsonArray seasons = ctx.has("seasons") && ctx.get("seasons").isJsonArray() ? ctx.getAsJsonArray("seasons") : new JsonArray();
        for (JsonElement e : seasons) {
            JsonObject s = e.getAsJsonObject();
            if (s.has("active") && s.get("active").getAsBoolean()) {
                s.remove("scores");
                out.add("season", s);
            }
        }
        for (String k : new String[]{"currentManche", "currentMancheInfo", "timer", "revealRunning", "players", "onlinePlayers", "looks",
                "cues", "spotlights", "cameras", "onAir", "autoDirection", "curtains", "garages", "recentActions"})
            if (ctx.has(k)) out.add(k, ctx.get(k));
        JsonArray arenas = new JsonArray();
        for (Arena a : Arenas.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", a.name);
            o.addProperty("manche", a.manche);
            o.addProperty("mode", a.mode == null ? "aventure" : a.mode);
            o.addProperty("vocalLancement", a.vocalLancement);
            o.addProperty("etapes", String.join(" → ", a.etapes.isEmpty() ? java.util.List.of("revele") : a.etapes));
            JsonArray copies = new JsonArray();
            a.copies.keySet().forEach(copies::add);
            o.add("copies", copies);
            o.addProperty("chronoLettre", a.chronoLettre);
            o.addProperty("chronoSuite", a.chronoSuite);
            o.addProperty("panneauxSuite", a.panneauxSuite);
            o.addProperty("murs", a.walls);
            o.addProperty("inventaireVide", a.clearInventory);
            o.addProperty("solFin", a.solFin);
            o.addProperty("alphabet", a.alphabet);
            o.addProperty("champ", a.field != null);
            o.addProperty("aveugle", a.field != null && a.field.blind);
            o.addProperty("kit", a.kit.size());
            if (a.inventoryCam != null) o.addProperty("cameraInventaire", a.inventoryCam);
            o.addProperty("resume", Debug.describe(a));
            arenas.add(o);
        }
        out.add("arenas", arenas);
        JsonObject inv = new JsonObject();
        inv.addProperty("mode", InventoryCam.settings().mode);
        JsonObject perRound = new JsonObject();
        for (Map.Entry<String, Boolean> e : InventoryCam.settings().manches.entrySet()) perRound.addProperty(e.getKey(), e.getValue());
        inv.add("manches", perRound);
        inv.addProperty("resume", InventoryCam.describe());
        out.add("inventoryCam", inv);
        out.addProperty("debug", Debug.on(p));
        out.addProperty("frozen", fr.lordfinn.craftmaster.reprise.Reprise.frozen());
        out.addProperty("waiting", fr.lordfinn.craftmaster.task.RoundStart.waiting());
        return out;
    }
}
