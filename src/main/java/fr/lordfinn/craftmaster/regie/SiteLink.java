package fr.lordfinn.craftmaster.regie;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork;
import fr.lordfinn.craftmaster.broadcast.Director;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.show.Actor;
import fr.lordfinn.craftmaster.show.Moderators;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Link between this server and the moderators' page of lordfinn.fr (docs/wiki/RegieModos.md). The server always calls
 * the site, never the reverse: no port to open, and the Stream Deck's local API (127.0.0.1, on LordFinn's game) has
 * nothing to do with it. Two HTTPS loops, with the shared secret ({@code /cm modo site lier}) as a bearer token:
 * <pre>
 * GET  {url}/orders   held up to 25 s by the site → {orders [{id, twitch, actions[]}], watching [logins]}
 * POST {url}/push     every second while something changed (15 s otherwise) →
 *                     {moderators [{twitch, label, rights[]}], paused, states {login: ModoState}, results [{id, ok, lines[]}]}
 * </pre>
 * The site only says who is signed in (a Twitch login); the rights come from {@link Moderators} here, checked again for
 * every order by {@link ActionExecutor} ({@link Actor#ofTwitch}). An unknown login gets nothing done.
 */
public final class SiteLink {

    private SiteLink() {}

    static final class Settings {
        boolean enabled = false;
        /** Base address of the link on the site, e.g. https://lordfinn.fr/api/craftmaster/link */
        String url = "https://lordfinn.fr/api/craftmaster/link";
        String secret = "";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Path path;
    private static volatile Settings settings = new Settings();
    private static volatile MinecraftServer server;
    private static volatile Thread poller, pusher;
    private static volatile boolean running;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** Logins whose page is open (from the site), results to send back, last state sent per login. */
    private static volatile Set<String> watching = Set.of();
    private static final ConcurrentLinkedQueue<JsonObject> RESULTS = new ConcurrentLinkedQueue<>();
    private static final Map<String, String> SENT = new LinkedHashMap<>();
    /** The version of each camera preview the site has (Thumbnails). */
    private static final Map<String, Integer> SENT_THUMBS = new LinkedHashMap<>();
    /** At most this many previews per push (each ~30 KB): the others go with the next ones, a second later. */
    private static final int THUMBS_PER_PUSH = 4;
    private static volatile String lastModerators = "";
    private static volatile long lastPush;
    private static volatile String status = "arrêté";

    // ---------------------------------------------------------------- settings

    public static synchronized void load(Path p) {
        path = p;
        Settings s = new Settings();
        if (Files.isRegularFile(p)) {
            try {
                Settings read = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), Settings.class);
                if (read != null) s = read;
            } catch (IOException | RuntimeException e) {
                CraftMaster.LOG.warn("[Site] {} illisible : {}", p, e.toString());
            }
        }
        settings = s;
    }

    private static synchronized void save() {
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(settings), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CraftMaster.LOG.warn("[Site] écriture de {} impossible : {}", path, e.toString());
        }
    }

    /** A new secret (shown once to the organiser, to put in the site's .env); the link starts with it. */
    public static String pair(String url) {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        Settings s = new Settings();
        s.url = url == null || url.isBlank() ? settings.url : url.trim().replaceAll("/+$", "");
        s.secret = HexFormat.of().formatHex(b);
        s.enabled = true;
        settings = s;
        save();
        restart();
        return s.secret;
    }

    public static void setEnabled(boolean on) {
        settings.enabled = on;
        save();
        restart();
    }

    public static String describe() {
        Settings s = settings;
        if (s.secret.isEmpty()) return "Site : pas lié (/cm modo site lier [adresse]).";
        return "Site : " + (s.enabled ? "activé" : "coupé") + " · " + s.url + " · " + status
                + (watching.isEmpty() ? "" : " · page ouverte : " + String.join(", ", watching));
    }

    // ---------------------------------------------------------------- life

    public static void start(MinecraftServer s) {
        server = s;
        restart();
    }

    public static void stop() {
        running = false;
        if (poller != null) poller.interrupt();
        if (pusher != null) pusher.interrupt();
        poller = pusher = null;
        status = "arrêté";
    }

    private static synchronized void restart() {
        stop();
        Settings s = settings;
        if (server == null || !s.enabled || s.secret.isEmpty() || !s.url.startsWith("https://") && !s.url.startsWith("http://127.0.0.1")
                && !s.url.startsWith("http://localhost")) {
            if (s.enabled && !s.url.startsWith("https://")) status = "adresse refusée (https:// seulement)";
            return;
        }
        running = true;
        SENT.clear();
        SENT_THUMBS.clear();
        lastModerators = "";
        status = "connexion…";
        poller = Thread.ofPlatform().daemon().name("CraftMaster-site-orders").start(SiteLink::pollLoop);
        pusher = Thread.ofPlatform().daemon().name("CraftMaster-site-push").start(SiteLink::pushLoop);
    }

    private static HttpRequest.Builder request(String end) {
        Settings s = settings;
        return HttpRequest.newBuilder(URI.create(s.url + end)).header("Authorization", "Bearer " + s.secret)
                .header("Content-Type", "application/json").header("User-Agent", "CraftMaster");
    }

    private static void pollLoop() {
        long backoff = 1000;
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                HttpResponse<String> r = HTTP.send(request("/orders").timeout(Duration.ofSeconds(40)).GET().build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (r.statusCode() == 401 || r.statusCode() == 403) {
                    status = "secret refusé par le site";
                    sleep(30_000);
                    continue;
                }
                if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode());
                status = "connecté";
                backoff = 1000;
                onOrders(JsonParser.parseString(r.body()).getAsJsonObject());
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                if (!running) return;
                status = "site injoignable (" + e.getClass().getSimpleName() + ")";
                sleep(backoff);
                backoff = Math.min(backoff * 2, 30_000);
            }
        }
    }

    private static void onOrders(JsonObject o) {
        if (o.has("watching") && o.get("watching").isJsonArray()) {
            List<String> w = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("watching")) w.add(e.getAsString().toLowerCase(java.util.Locale.ROOT));
            watching = Set.copyOf(w);
        }
        if (!o.has("orders")) return;
        MinecraftServer s = server;
        if (s == null) return;
        for (JsonElement e : o.getAsJsonArray("orders")) {
            JsonObject order = e.getAsJsonObject();
            String id = order.get("id").getAsString();
            String login = order.get("twitch").getAsString();
            List<Map<String, Object>> actions;
            try {
                JsonArray list = order.getAsJsonArray("actions");
                if (list.size() == 0 || list.size() > 20) throw new IllegalArgumentException("1 à 20 actions");
                actions = RegieJson.readActions(list.toString());
            } catch (RuntimeException ex) {
                result(id, false, List.of("✗ Ordre illisible : " + ex.getMessage()));
                continue;
            }
            // On the game's thread, as the moderator: the executor checks each action against the list's rights
            s.execute(() -> {
                ActionExecutor.Result r;
                try {
                    r = ActionExecutor.execute(s, Actor.ofTwitch(login), actions);
                } catch (RuntimeException ex) {
                    CraftMaster.LOG.error("[Site] ordre {} de {} : {}", id, login, ex.toString());
                    r = new ActionExecutor.Result(List.of("✗ Erreur interne."), false);
                }
                result(id, r.ok(), r.lines());
            });
        }
    }

    private static void result(String id, boolean ok, List<String> lines) {
        JsonObject j = new JsonObject();
        j.addProperty("id", id);
        j.addProperty("ok", ok);
        JsonArray l = new JsonArray();
        lines.forEach(l::add);
        j.add("lines", l);
        RESULTS.add(j);
        WAKE.release();   // wakes the push loop: the moderator waits for this answer
    }

    private static final java.util.concurrent.Semaphore WAKE = new java.util.concurrent.Semaphore(0);

    private static void pushLoop() {
        while (running) {
            try {
                if (WAKE.tryAcquire(1, java.util.concurrent.TimeUnit.SECONDS)) WAKE.drainPermits();
            } catch (InterruptedException e) {
                return;
            }
            try {
                JsonObject body = snapshot();
                if (body == null) continue;
                HttpResponse<String> r = HTTP.send(request("/push").timeout(Duration.ofSeconds(15))
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build(),
                        HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() != 200) {
                    // Results not delivered: put them back for the next push
                    if (body.has("results")) for (JsonElement e : body.getAsJsonArray("results")) RESULTS.add(e.getAsJsonObject());
                    SENT.clear();
                    SENT_THUMBS.clear();
                    lastModerators = "";
                }
            } catch (InterruptedException e) {
                if (!running) return;
            } catch (Exception e) {
                SENT.clear();
                SENT_THUMBS.clear();
                lastModerators = "";
            }
        }
    }

    /** What to push now, built on the game's thread; null when nothing changed and the heartbeat is not due. */
    private static JsonObject snapshot() throws Exception {
        MinecraftServer s = server;
        if (s == null) return null;
        Set<String> w = watching;
        java.util.concurrent.CompletableFuture<JsonObject> f = new java.util.concurrent.CompletableFuture<>();
        s.execute(() -> {
            try {
                JsonObject states = new JsonObject();
                BroadcastNetwork.ShowState show = w.isEmpty() ? null : Director.build(s);
                for (String login : w) {
                    Actor a = Actor.ofTwitch(login);
                    states.add(login, a.any() ? ModoState.build(CraftMaster.data(), show, a) : new JsonObject());
                }
                f.complete(states);
            } catch (RuntimeException e) {
                f.completeExceptionally(e);
            }
        });
        JsonObject states = f.get(5, java.util.concurrent.TimeUnit.SECONDS);

        JsonObject body = new JsonObject();
        boolean changed = false;
        JsonArray mods = new JsonArray();
        for (Moderators.Entry e : Moderators.list()) {
            if (e.twitch.isEmpty()) continue;
            JsonObject m = new JsonObject();
            m.addProperty("twitch", e.twitch);
            m.addProperty("label", e.label());
            JsonArray r = new JsonArray();
            e.rights.forEach(r::add);
            m.add("rights", r);
            mods.add(m);
        }
        String modsText = mods + "|" + Moderators.paused();
        if (!modsText.equals(lastModerators)) {
            lastModerators = modsText;
            changed = true;
        }
        body.add("moderators", mods);
        body.addProperty("paused", Moderators.paused());
        JsonObject changedStates = new JsonObject();
        for (Map.Entry<String, JsonElement> e : states.entrySet()) {
            String text = e.getValue().toString();
            if (!text.equals(SENT.get(e.getKey()))) {
                SENT.put(e.getKey(), text);
                changedStates.add(e.getKey(), e.getValue());
                changed = true;
            }
        }
        SENT.keySet().retainAll(states.keySet());
        body.add("states", changedStates);
        // The shots' previews: the new ones (a few per push), and which cameras still exist
        JsonObject thumbs = new JsonObject();
        JsonArray keep = new JsonArray();
        for (Map.Entry<String, byte[]> e : Thumbnails.images().entrySet()) {
            keep.add(e.getKey());
            int v = Thumbnails.versionOf(e.getKey());
            if (thumbs.size() < THUMBS_PER_PUSH && SENT_THUMBS.getOrDefault(e.getKey(), -1) != v) {
                thumbs.addProperty(e.getKey(), java.util.Base64.getEncoder().encodeToString(e.getValue()));
                SENT_THUMBS.put(e.getKey(), v);
            }
        }
        if (!thumbs.isEmpty()) {
            body.add("thumbs", thumbs);
            body.add("thumbKeys", keep);
            changed = true;
        }
        JsonArray results = new JsonArray();
        JsonObject r;
        while ((r = RESULTS.poll()) != null) results.add(r);
        if (!results.isEmpty()) {
            body.add("results", results);
            changed = true;
        }
        long now = System.currentTimeMillis();
        if (!changed && now - lastPush < 15_000) return null;
        lastPush = now;
        return body;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
