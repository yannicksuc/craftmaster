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
 * /tablet/start   timer tablets: start, pause, toggle, reset, restart (?group=name: only that group)
 * /points?player=Ensuri&amp;delta=1   points for the round being played, at once (quick presses on one player are
 *                grouped for 0.8 s into one "+3" announced)
 * /round/next    starts the next round · /round/start?manche=pont · /round/end · /timer/stop
 * /publish       publishes the scores; press twice within 3 s (the first press only arms it) · /reveal/start too
 * /camera/cut?camera=2 · /camera/auto (?enabled=false)
 * /status       JSON: connected, organiser, open screen, season, undo / redo available
 * </pre>
 * Every answer is JSON {@code {"ok": true|false, ...}}; the game does the work on its own thread. Requests from a
 * web page (an {@code Origin} header) are refused: only local tools (StreamDeck, Companion, curl) get through.
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
            // Its dispatcher thread takes the daemon status of the thread that starts it: started from a daemon
            // thread, it never keeps the game from closing (it did: the shutdown watchdog crashed the client)
            HttpServer started = server;
            Thread t = Thread.ofPlatform().daemon().name("CraftMaster-streamdeck-start").unstarted(started::start);
            t.start();
            try {
                t.join(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
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
            if (e.getRequestHeaders().getFirst("Origin") != null) {
                // A web page open on this PC must not drive the show
                code = 403;
                out = error("requests from web pages are refused");
            } else if (!s.token.isEmpty() && !s.token.equals(token(e, uri))) {
                code = 403;
                out = error("bad token");
            } else {
                out = route(uri, uri.getPath().replaceAll("/+$", ""), query(uri, "group"));
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

    /** A query parameter (decoded), "" if absent. */
    private static String query(URI uri, String name) {
        String q = uri.getRawQuery();
        if (q == null) return "";
        for (String part : q.split("&"))
            if (part.startsWith(name + "=")) return java.net.URLDecoder.decode(part.substring(name.length() + 1), StandardCharsets.UTF_8);
        return "";
    }

    // ---------------------------------------------------------------- live actions

    private static final java.util.concurrent.ScheduledExecutorService LATER =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "CraftMaster StreamDeck");
                t.setDaemon(true);
                return t;
            });
    /** Points pressed but not sent yet, per player, and the pending send of each. */
    private static final java.util.Map<String, Integer> POINTS = new java.util.HashMap<>();
    private static final java.util.Map<String, java.util.concurrent.ScheduledFuture<?>> SENDS = new java.util.HashMap<>();
    /** Armed double-press keys (publish, reveal) and when they were armed. */
    private static final java.util.Map<String, Long> ARMED = new java.util.HashMap<>();

    private static JsonObject action(String type, Object... fields) {
        JsonObject a = new JsonObject();
        a.addProperty("type", type);
        for (int i = 0; i + 1 < fields.length; i += 2) {
            Object v = fields[i + 1];
            if (v instanceof Number n) a.addProperty((String) fields[i], n);
            else if (v instanceof Boolean b) a.addProperty((String) fields[i], b);
            else a.addProperty((String) fields[i], String.valueOf(v));
        }
        return a;
    }

    /** Sends actions to the server (as the organiser; the server checks). */
    private static JsonObject send(JsonObject... actions) {
        return onGame(() -> {
            if (!connected()) return error("not connected");
            com.google.gson.JsonArray list = new com.google.gson.JsonArray();
            for (JsonObject a : actions) list.add(a);
            ClientPlayNetworking.send(new fr.lordfinn.craftmaster.trial.TrialNetwork.RunActions(list.toString()));
            return ok();
        });
    }

    /** +n for a player, grouped: presses within 0.8 s of each other make one score.add. */
    private static synchronized JsonObject points(String player, int delta) {
        if (player.isBlank()) return error("player=… needed");
        int total = POINTS.merge(player, delta, Integer::sum);
        java.util.concurrent.ScheduledFuture<?> f = SENDS.remove(player);
        if (f != null) f.cancel(false);
        SENDS.put(player, LATER.schedule(() -> flush(player), 800, java.util.concurrent.TimeUnit.MILLISECONDS));
        JsonObject o = ok();
        o.addProperty("player", player);
        o.addProperty("pending", total);
        return o;
    }

    private static void flush(String player) {
        int pts;
        synchronized (StreamDeckServer.class) {
            Integer v = POINTS.remove(player);
            SENDS.remove(player);
            pts = v == null ? 0 : v;
        }
        if (pts != 0) send(action("score.add", "player", player, "points", pts));
    }

    /** A key that needs two presses within 3 s: the first arms it (and says so), the second runs it. */
    private static synchronized boolean armed(String key) {
        long now = System.currentTimeMillis();
        Long at = ARMED.remove(key);
        if (at != null && now - at <= 3000) return true;
        ARMED.put(key, now);
        return false;
    }

    private static JsonObject armedAnswer(String what) {
        JsonObject o = ok();
        o.addProperty("armed", true);
        o.addProperty("message", "press again within 3 s to " + what);
        return o;
    }

    private static JsonObject route(URI uri, String path, String group) {
        switch (path) {
            case "/points" -> {
                int delta;
                try {
                    delta = Integer.parseInt(query(uri, "delta").isEmpty() ? "1" : query(uri, "delta"));
                } catch (NumberFormatException ex) {
                    return error("delta must be a number");
                }
                return points(query(uri, "player"), delta);
            }
            case "/round/next" -> {
                return send(action("manche.start", "manche", "next"));
            }
            case "/round/start" -> {
                String m = query(uri, "manche");
                return send(action("manche.start", "manche", m.isEmpty() ? "next" : m));
            }
            case "/round/end" -> {
                return send(action("manche.end"));
            }
            case "/timer/stop" -> {
                return send(action("timer.stop"));
            }
            case "/publish" -> {
                return armed("publish") ? send(action("scores.publish")) : armedAnswer("publish the scores");
            }
            case "/reveal/start" -> {
                return armed("reveal") ? send(action("reveal.start")) : armedAnswer("start the reveal");
            }
            case "/camera/cut" -> {
                String cam = query(uri, "camera");
                if (cam.isEmpty()) return error("camera=… needed");
                return send(action("camera.cut", "camera", cam.matches("\\d+") ? (Object) Integer.parseInt(cam) : cam));
            }
            case "/camera/auto" -> {
                return send(action("camera.auto", "enabled", !"false".equals(query(uri, "enabled"))));
            }
            default -> {
                return route(path, group);
            }
        }
    }

    private static JsonObject route(String path, String group) {
        if (path.startsWith("/tablet/")) {
            String what = path.substring("/tablet/".length());
            if (!java.util.List.of("start", "pause", "toggle", "reset", "restart").contains(what)) return error("tablet: start, pause, toggle, reset, restart");
            String g = group.replaceAll("[^A-Za-z0-9_-]", "");
            return onGame(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (!connected() || mc.player == null) return error("not connected");
                // The server checks the permission (organisers only)
                mc.player.connection.sendCommand("craftMasterTablet " + what + (g.isEmpty() ? "" : " " + g));
                return ok();
            });
        }
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
                JsonObject o = ok();
                JsonObject last = ScoreEntryScreen.lastBoard();
                String key = path.equals("/undo") ? "undoText" : "redoText";
                if (last != null && last.has(key)) o.addProperty("did", last.get(key).getAsString());
                return o;
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
                JsonObject o = error("unknown: " + path + " (menu, ranking, points, round/next, round/start, round/end, timer/stop, publish, reveal/start, "
                        + "camera/cut, camera/auto, undo, redo, season/next, season/prev, tablet/<start|pause|toggle|reset|restart>, status)");
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
