package fr.lordfinn.craftmaster.client.score;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.client.CraftMasterClient;
import fr.lordfinn.craftmaster.client.RankingScreen;
import fr.lordfinn.craftmaster.client.task.TwitchChat;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A small HTTP API on 127.0.0.1 for a StreamDeck (or any local tool): one address per button. Off by default; set
 * in {@code config/craftmaster-client.json}, section {@code streamDeck} ({@code enabled}, {@code port}, and an
 * optional {@code token} to give as {@code ?token=} or header {@code X-Token}). Only this PC can reach it.
 *
 * <pre>
 * /menu         opens the score entry screen (closes it if open)
 * /ranking      opens the ranking screen (closes it if open)
 * /undo /redo   undoes / redoes the last change (organisers; the server checks)
 * /season/next  next / previous season in the open score entry or ranking screen
 * /season/prev
 * /status       JSON: connected, organiser, open screen, season, undo / redo available
 * </pre>
 * Every answer is JSON {@code {"ok": true|false, ...}}; the game does the work on its own thread.
 */
public final class StreamDeckServer {

    private StreamDeckServer() {}

    public static final class Settings {
        public boolean enabled = false;
        public int port = 25710;
        /** Empty = no token asked (the API only listens on this PC anyway). */
        public String token = "";
    }

    private static HttpServer server;

    public static synchronized void start() {
        stop();
        Settings s = TwitchChat.config().streamDeck;
        if (s == null || !s.enabled) return;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), s.port), 8);
            server.createContext("/", e -> handle(e, s));
            server.start();
            CraftMaster.LOG.info("[StreamDeck] API sur http://127.0.0.1:{}/", s.port);
        } catch (IOException e) {
            CraftMaster.LOG.warn("[StreamDeck] Impossible d'ouvrir le port {} : {}", s.port, e.toString());
            server = null;
        }
    }

    public static synchronized void stop() {
        if (server != null) server.stop(0);
        server = null;
    }

    private static void handle(HttpExchange e, Settings s) throws IOException {
        JsonObject out;
        int code = 200;
        try {
            URI uri = e.getRequestURI();
            if (!s.token.isEmpty() && !s.token.equals(token(e, uri))) {
                code = 403;
                out = error("bad token");
            } else {
                out = route(uri.getPath().replaceAll("/+$", ""));
                if (!out.get("ok").getAsBoolean()) code = out.has("code") ? out.get("code").getAsInt() : 409;
                out.remove("code");
            }
        } catch (RuntimeException ex) {
            code = 500;
            out = error(ex.toString());
        }
        byte[] body = out.toString().getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        e.sendResponseHeaders(code, body.length);
        try (OutputStream o = e.getResponseBody()) {
            o.write(body);
        }
    }

    private static String token(HttpExchange e, URI uri) {
        String h = e.getRequestHeaders().getFirst("X-Token");
        if (h != null) return h;
        String q = uri.getRawQuery();
        if (q == null) return "";
        for (String part : q.split("&")) if (part.startsWith("token=")) return java.net.URLDecoder.decode(part.substring(6), StandardCharsets.UTF_8);
        return "";
    }

    static JsonObject error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("error", message);
        return o;
    }

    static JsonObject ok() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        return o;
    }

    private static JsonObject route(String path) {
        return switch (path) {
            case "", "/status" -> onGame(StreamDeckServer::status);
            case "/menu" -> onGame(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (!connected()) return error("not connected");
                if (mc.gui.screen() instanceof ScoreEntryScreen s) s.onClose();
                else ScoreEntryClient.open(mc);
                return ok();
            });
            case "/ranking" -> onGame(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (!connected()) return error("not connected");
                if (mc.gui.screen() instanceof RankingScreen s) s.onClose();
                else CraftMasterClient.openRanking(mc, -1);
                return ok();
            });
            case "/undo", "/redo" -> onGame(() -> {
                if (!connected()) return error("not connected");
                ScoreEntryScreen.simple(path.substring(1));
                return ok();
            });
            case "/season/next", "/season/prev" -> onGame(() -> {
                int delta = path.endsWith("next") ? 1 : -1;
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() instanceof ScoreEntryScreen s) s.seasonStep(delta);
                else if (mc.gui.screen() instanceof RankingScreen r) r.changeSeason(delta);
                else return error("no score entry or ranking screen open");
                return ok();
            });
            default -> {
                JsonObject o = error("unknown: " + path + " (menu, ranking, undo, redo, season/next, season/prev, status)");
                o.addProperty("code", 404);
                yield o;
            }
        };
    }

    private static boolean connected() {
        return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(ScoreEntryNetworkTypes.REQUEST);
    }

    private static JsonObject status() {
        Minecraft mc = Minecraft.getInstance();
        JsonObject o = ok();
        o.addProperty("connected", connected());
        o.addProperty("organiser", fr.lordfinn.craftmaster.client.regie.VoiceRegie.isAdmin());
        String screen = mc.gui.screen() instanceof ScoreEntryScreen ? "scores" : mc.gui.screen() instanceof RankingScreen ? "ranking"
                : mc.gui.screen() == null ? "none" : "other";
        o.addProperty("screen", screen);
        JsonObject last = ScoreEntryScreen.lastBoard();
        if (mc.gui.screen() instanceof ScoreEntryScreen s) o.addProperty("season", s.season());
        else if (mc.gui.screen() instanceof RankingScreen r) o.addProperty("season", r.season());
        if (last != null) {
            o.addProperty("undo", last.get("undo").getAsInt());
            o.addProperty("redo", last.get("redo").getAsInt());
        }
        return o;
    }

    /** Runs on the game's thread and waits for it (2 s at most). */
    private static JsonObject onGame(Supplier<JsonObject> task) {
        CompletableFuture<JsonObject> f = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                f.complete(task.get());
            } catch (RuntimeException ex) {
                f.completeExceptionally(ex);
            }
        });
        try {
            return f.get(2, TimeUnit.SECONDS);
        } catch (Exception ex) {
            return error("game busy: " + ex);
        }
    }

    /** Kept apart so this class does not load network classes before the game is ready. */
    private static final class ScoreEntryNetworkTypes {
        static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?> REQUEST =
                fr.lordfinn.craftmaster.network.ScoreEntryNetwork.RequestBoard.TYPE;
    }
}
