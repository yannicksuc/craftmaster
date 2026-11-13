package fr.lordfinn.craftmaster.client.media;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.client.CraftMasterClient;
import fr.lordfinn.craftmaster.client.broadcast.Broadcast;
import fr.lordfinn.craftmaster.client.task.TwitchChat;
import fr.lordfinn.craftmaster.media.MediaNetwork;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;

/**
 * In-game clips: F9 starts recording the current view, F10 stops it; the MP4 stays in {@code craftmaster/clips/} and
 * is sent to the server, which then lists it ({@code /cmclip liste}) and lets any studio screen show it ({@code
 * /cmtele <tele> clip dernier}). The Stream Deck ({@code /clip/start}, {@code /clip/stop}) and the voice do the same.
 * On screen: nothing in broadcast mode (the viewers see the picture), else a small red dot and the time in a corner.
 */
public final class Clips {

    private Clips() {}

    /** Settings: section {@code clips} of {@code config/craftmaster-client.json}. */
    public static final class Settings {
        /** Pictures per second (10 to 60). */
        public int fps = 30;
        /** 10 (smallest file) to 100 (best picture); 40 ≈ 9 Mbit/s in 1080p30. */
        public int quality = 40;
        /** Send every clip to the server (so the screens can show it). */
        public boolean upload = true;
        /** A clip stops by itself after this many seconds (0: never). */
        public int maxSeconds = 180;
    }

    public static final KeyMapping START_KEY = new KeyMapping("key.craftmaster.clip_start", InputConstants.Type./*$ keyboard_type {*/KEYBOARD/*$}*/,
            InputConstants.KEY_F9, CraftMasterClient.CATEGORY);
    public static final KeyMapping STOP_KEY = new KeyMapping("key.craftmaster.clip_stop", InputConstants.Type./*$ keyboard_type {*/KEYBOARD/*$}*/,
            InputConstants.KEY_F10, CraftMasterClient.CATEGORY);

    private static ClipRecorder recorder;
    /** Last thing that happened, for the Stream Deck's /status and the voice. */
    private static String lastEvent = "";

    public static void init() {
        KeyMappingHelper.registerKeyMapping(START_KEY);
        KeyMappingHelper.registerKeyMapping(STOP_KEY);
        ClipCache.init();
        ChatWallClient.init();
        ClientPlayNetworking.registerGlobalReceiver(MediaNetwork.UploadReply.TYPE, (p, ctx) -> Uploads.reply(p));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (START_KEY.consumeClick()) start();
            while (STOP_KEY.consumeClick()) stop();
            if (recorder != null && !recorder.stopping()) {
                int max = settings().maxSeconds;
                if (max > 0 && recorder.elapsedMs() > max * 1000L) stop();
            }
            Uploads.tick();
        });
        HudElementRegistry.addLast(CraftMaster.id("clip_recording"), Clips::hud);
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> {
            ClipCache.clear();
            if (Media.installed()) VideoScreens.clear();
        });
    }

    static Settings settings() {
        Settings s = TwitchChat.config().clips;
        return s == null ? new Settings() : s;
    }

    public static boolean recording() {
        return recorder != null && !recorder.stopping();
    }

    public static String lastEvent() {
        return lastEvent;
    }

    /** The recording's length so far (ms), 0 when none. */
    public static long elapsedMs() {
        return recording() ? recorder.elapsedMs() : 0;
    }

    /** Starts a clip; false (and why, in French) when it cannot. */
    public static Result start() {
        if (recording()) return say(false, "Clip déjà en cours (F10 pour l'arrêter).");
        if (recorder != null) return say(false, "Le clip précédent se termine, un instant.");
        if (!Media.available()) return say(false, "Clips indisponibles : " + Media.unavailableReason() + ".");
        try {
            recorder = ClipRecorder.start(settings().fps, settings().quality, replayPlaying() ? "replay" : "direct");
        } catch (IOException | RuntimeException | LinkageError e) {
            recorder = null;
            return say(false, "Clip impossible : " + WaterMediaVideo.message(e));
        }
        CraftMaster.LOG.info("[Clips] Enregistrement de {} ({} i/s)", recorder.file().getFileName(), settings().fps);
        return say(true, "● Clip démarré (F10 pour l'arrêter).");
    }

    /** Stops the clip; the file is finished, then sent to the server. */
    public static Result stop() {
        if (!recording()) return say(false, "Aucun clip en cours (F9 pour en démarrer un).");
        ClipRecorder r = recorder;
        r.stop(Clips::finished);
        return say(true, "Clip arrêté : finition du fichier…");
    }

    public static Result toggle() {
        return recording() ? stop() : start();
    }

    private static void finished(ClipRecorder r) {
        recorder = null;
        if (r.error() != null) {
            say(false, "Clip perdu : " + r.error());
            try {
                Files.deleteIfExists(r.file());
            } catch (IOException ignored) {
                // nothing to clean
            }
            return;
        }
        CraftMaster.LOG.info("[Clips] {} : {} s, {} images, {} sautées", r.file().getFileName(), r.durationMs() / 1000, r.frames(), r.dropped());
        if (settings().upload) {
            Uploads.add(r.file(), r.durationMs(), r.source());
            say(true, "Clip de " + r.durationMs() / 1000 + " s enregistré, envoi au serveur…");
        } else {
            say(true, "Clip de " + r.durationMs() / 1000 + " s enregistré : craftmaster/clips/" + r.file().getFileName());
        }
    }

    /** After the world is drawn, before the interface (GameRendererMixin). */
    public static void afterLevel() {
        ClipRecorder r = recorder;
        if (r == null) return;
        if (r.stopping()) r.poll();
        else r.afterLevel();
    }

    /** A replay of the replay director plays (read only, through its public state, when that code is there). */
    private static boolean replayPlaying() {
        try {
            Class<?> c = Class.forName("fr.lordfinn.craftmaster.client.replay.ReplayClient");
            return (boolean) c.getMethod("active").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    public record Result(boolean ok, String message) {}

    /** Tells the organiser what happened, without anything on the broadcast picture. */
    static Result say(boolean ok, String message) {
        lastEvent = message;
        CraftMaster.LOG.info("[Clips] {}", message);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !Broadcast.active())
            mc.player.sendOverlayMessage(Component.literal(message).withStyle(ok ? ChatFormatting.GOLD : ChatFormatting.RED));
        return new Result(ok, message);
    }

    /** Small red dot and time in the top-right corner while recording (not in broadcast mode). */
    private static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (!recording() || Broadcast.active()) return;
        Minecraft mc = Minecraft.getInstance();
        long s = recorder.elapsedMs() / 1000;
        String t = String.format("%d:%02d", s / 60, s % 60);
        int w = mc.font.width(t);
        int x = g.guiWidth() - w - 16, y = 6;
        boolean on = (System.currentTimeMillis() / 500) % 2 == 0;
        if (on) g.fill(x, y + 1, x + 6, y + 7, 0xFFE03030);
        g.text(mc.font, t, x + 9, y, 0xFFFFFFFF);
    }

    // ---------------------------------------------------------------- sending to the server

    /** Clips waiting to be sent, one at a time, in pieces paced per tick (retried while a replay blocks the way). */
    static final class Uploads {

        private static final int PIECES_PER_TICK = 12;

        private static final class Item {
            final Path file;
            final int durationMs;
            final String source;
            final int id;
            long size, offset;
            int state; // 0 = asked, 1 = sending, 2 = sent, waiting for the server
            long askedAt;

            Item(Path file, int durationMs, String source, int id) {
                this.file = file;
                this.durationMs = durationMs;
                this.source = source;
                this.id = id;
            }
        }

        private static final ArrayDeque<Item> QUEUE = new ArrayDeque<>();
        private static int nextId = (int) (System.currentTimeMillis() & 0xFFFF);

        static void add(Path file, int durationMs, String source) {
            QUEUE.add(new Item(file, durationMs, source, nextId++));
        }

        static int waiting() {
            return QUEUE.size();
        }

        static void tick() {
            Item it = QUEUE.peek();
            if (it == null) return;
            if (!ClientPlayNetworking.canSend(MediaNetwork.UploadBegin.TYPE)) return;
            long now = System.currentTimeMillis();
            if (it.state == 0 && now - it.askedAt > 10_000) {
                // First ask, or no answer (the server is busy, or a replay is playing and lets nothing through)
                try {
                    it.size = Files.size(it.file);
                } catch (IOException e) {
                    QUEUE.poll();
                    say(false, "Clip introuvable : " + it.file.getFileName());
                    return;
                }
                it.askedAt = now;
                ClientPlayNetworking.send(new MediaNetwork.UploadBegin(it.id, "", it.size, it.durationMs, it.source));
            } else if (it.state == 1) {
                try (RandomAccessFile in = new RandomAccessFile(it.file.toFile(), "r")) {
                    for (int i = 0; i < PIECES_PER_TICK && it.offset < it.size; i++) {
                        int n = (int) Math.min(MediaNetwork.UPLOAD_PIECE, it.size - it.offset);
                        byte[] data = new byte[n];
                        in.seek(it.offset);
                        in.readFully(data);
                        ClientPlayNetworking.send(new MediaNetwork.UploadPiece(it.id, it.offset, data));
                        it.offset += n;
                    }
                } catch (IOException e) {
                    QUEUE.poll();
                    say(false, "Lecture du clip impossible : " + e.getMessage());
                    return;
                }
                if (it.offset >= it.size) {
                    ClientPlayNetworking.send(new MediaNetwork.UploadEnd(it.id));
                    it.state = 2;
                    it.askedAt = now;
                }
            } else if (it.state == 2 && now - it.askedAt > 30_000) {
                // Lost on the way: everything again
                it.state = 0;
                it.offset = 0;
                it.askedAt = 0;
            }
        }

        static void reply(MediaNetwork.UploadReply p) {
            Item it = QUEUE.peek();
            if (it == null || it.id != p.upload()) return;
            switch (p.state()) {
                case "ready" -> {
                    it.state = 1;
                    it.offset = 0;
                }
                case "done" -> {
                    QUEUE.poll();
                    ClipCache.adopt(it.file, p.clip());
                    say(true, "Clip « " + p.message() + " » sur le serveur : /cmtele <tele> clip dernier.");
                }
                default -> {
                    QUEUE.poll();
                    say(false, "Clip refusé par le serveur : " + p.message());
                }
            }
        }
    }
}
