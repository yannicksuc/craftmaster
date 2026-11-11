package fr.lordfinn.craftmaster.client.task;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.task.TwitchIrc;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Optional: the broadcast client reads its Twitch chat anonymously, and {@code !tache} shows the task card on
 * screen for a few seconds (at most once every N seconds). Off by default; set in
 * {@code config/craftmaster-client.json} on the broadcast game only, reloaded with {@code /craftMasterTwitch reload}.
 * Read-only: no account, no key, nothing is ever written in the chat.
 */
public final class TwitchChat {

    private TwitchChat() {}

    /** The client's settings file. */
    public static final class Config {
        public Twitch twitch = new Twitch();
        /** Local HTTP API for a StreamDeck (score entry screen, undo / redo, seasons). */
        public fr.lordfinn.craftmaster.client.score.StreamDeckServer.Settings streamDeck = new fr.lordfinn.craftmaster.client.score.StreamDeckServer.Settings();
    }

    public static final class Twitch {
        public boolean enabled = false;
        /** Channel name (or its address). */
        public String channel = "";
        public List<String> commands = new ArrayList<>(List.of("!tache", "!task"));
        /** At most one card every N seconds. */
        public int cooldownSeconds = 30;
        /** How long the card stays. */
        public int showSeconds = 12;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config config = new Config();
    private static Thread thread;
    private static volatile boolean running;
    private static volatile Socket socket;
    private static volatile String status = "désactivé";
    private static long lastShown;

    static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("craftmaster-client.json");
    }

    public static Config config() {
        return config;
    }

    public static String status() {
        return status;
    }

    /** Reads the settings (writing the defaults the first time) and (re)starts the reader. */
    public static synchronized void reload() {
        stop();
        Path f = file();
        try {
            if (Files.exists(f)) {
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    Config c = GSON.fromJson(r, Config.class);
                    config = c == null ? new Config() : c;
                }
            } else {
                config = new Config();
            }
            if (config.twitch == null) config.twitch = new Twitch();
            if (config.streamDeck == null) config.streamDeck = new fr.lordfinn.craftmaster.client.score.StreamDeckServer.Settings();
            // Written back so new settings show up in the file
            Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                GSON.toJson(config, w);
            }
        } catch (IOException | RuntimeException e) {
            CraftMaster.LOG.warn("[Twitch] Réglages illisibles ({}) : chat Twitch désactivé", f, e);
            config = new Config();
        }
        Twitch t = config.twitch;
        String channel = TwitchIrc.channel(t.channel);
        if (!t.enabled) {
            status = "désactivé (config/craftmaster-client.json)";
            return;
        }
        if (channel.isEmpty()) {
            status = "aucune chaîne valide dans config/craftmaster-client.json";
            return;
        }
        running = true;
        thread = new Thread(() -> loop(channel), "CraftMaster Twitch chat");
        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized void stop() {
        running = false;
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // closing anyway
            }
        }
        if (thread != null) thread.interrupt();
        thread = null;
    }

    private static void loop(String channel) {
        Random random = new Random();
        int backoff = 5;
        while (running) {
            try (Socket s = SSLSocketFactory.getDefault().createSocket()) {
                socket = s;
                s.connect(new InetSocketAddress(TwitchIrc.HOST, TwitchIrc.TLS_PORT), 10_000);
                s.setSoTimeout(6 * 60_000);
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                for (String l : TwitchIrc.login(TwitchIrc.anonymousNick(random), channel)) out.write(l + "\r\n");
                out.flush();
                status = "connecté à #" + channel + " (lecture seule)";
                CraftMaster.LOG.info("[Twitch] Lecture du chat de #{}", channel);
                backoff = 5;
                String line;
                while (running && (line = in.readLine()) != null) {
                    String reply = TwitchIrc.reply(line);
                    if (reply != null) {
                        out.write(reply + "\r\n");
                        out.flush();
                        continue;
                    }
                    TwitchIrc.Message m = TwitchIrc.parse(line);
                    if (m != null && TwitchIrc.isCommand(m.text(), config.twitch.commands)) trigger(m.user());
                }
            } catch (IOException e) {
                if (!running) break;
                status = "déconnecté (" + e.getMessage() + "), nouvel essai dans " + backoff + " s";
                CraftMaster.LOG.warn("[Twitch] {}", status);
            } finally {
                socket = null;
            }
            try {
                Thread.sleep(backoff * 1000L);
            } catch (InterruptedException e) {
                break;
            }
            backoff = Math.min(60, backoff * 2);
        }
        if (!running) status = "arrêté";
    }

    /** Someone typed !tache: the card, unless it was shown less than cooldownSeconds ago. */
    static void trigger(String user) {
        long now = System.currentTimeMillis();
        synchronized (TwitchChat.class) {
            if (!TwitchIrc.cooledDown(lastShown, now, Math.max(0, config.twitch.cooldownSeconds) * 1000L)) return;
            lastShown = now;
        }
        int ticks = Math.max(3, Math.min(60, config.twitch.showSeconds)) * 20;
        Minecraft.getInstance().execute(() -> CurrentTask.showCard(ticks));
    }
}
