package fr.lordfinn.craftmaster.client.media;

import fr.lordfinn.craftmaster.CraftMaster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.watermedia.WaterMedia;
import org.watermedia.api.codecs.CodecsAPI;
import org.watermedia.api.player.MRL;
import org.watermedia.api.player.MediaPlayer;
import org.watermedia.api.player.PlayerAPI;
import org.watermedia.api.player.SFXEngine;

import java.net.URI;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/**
 * One video on one studio screen, played by WaterMedia: the link resolves (a Twitch clip or channel becomes its
 * stream), FFmpeg decodes, WaterMedia uploads each frame into its own OpenGL texture, and at the start of every frame
 * that picture is copied on the GPU into a Minecraft texture the screen draws like an image. The sound comes from the
 * screen in the world (positional, the jukebox volume). Render thread only, except {@link #ffmpegReady()}.
 */
final class WaterMediaVideo {

    /** Upload size cap: a studio screen never needs more (16 × 9 blocks seen from the set). */
    private static final int MAX_W = 1280, MAX_H = 720;

    /** WaterMedia's OpenGL work, run on the render thread at the start of each frame. */
    static final Queue QUEUE = new Queue();

    static final class Queue implements Executor {
        private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();

        @Override
        public void execute(Runnable r) {
            tasks.add(r);
        }

        void pump() {
            Runnable r;
            int n = 0;
            while (n++ < 256 && (r = tasks.poll()) != null) {
                try {
                    r.run();
                } catch (RuntimeException e) {
                    CraftMaster.LOG.warn("[Vidéo] Tâche WaterMedia en échec", e);
                }
            }
        }
    }

    static boolean ffmpegReady() {
        return WaterMedia.loaded() && CodecsAPI.ffmpegLoaded();
    }

    enum State { RESOLVING, LOADING, PLAYING, ENDED, FAILED }

    final String url;
    private final URI uri;
    private final double x, y, z;
    private final long startTick;
    private MRL mrl;
    private MediaPlayer player;
    private DynamicTexture target;
    private final Identifier textureId;
    private int texW, texH;
    private State state = State.RESOLVING;
    private String error = "";
    private boolean caughtUp;
    private long createdAt = System.currentTimeMillis();

    /**
     * @param playUri   what WaterMedia opens (the web link, or {@code water://local/...} for a downloaded clip)
     * @param startTick game time the screen got this video (-1: none): someone arriving later joins it where it is
     */
    WaterMediaVideo(String url, String playUri, int serial, double x, double y, double z, long startTick) {
        this.url = url;
        this.x = x;
        this.y = y;
        this.z = z;
        this.startTick = startTick;
        this.textureId = CraftMaster.id("studio_video/" + serial);
        URI u;
        try {
            u = URI.create(playUri);
        } catch (IllegalArgumentException e) {
            u = null;
            fail("adresse illisible");
        }
        this.uri = u;
        if (u != null) {
            try {
                mrl = PlayerAPI.mrl(u);
            } catch (RuntimeException e) {
                fail(message(e));
            }
        }
    }

    State state() { return state; }
    String error() { return error; }
    Identifier texture() { return texW > 0 ? textureId : null; }
    int width() { return texW; }
    int height() { return texH; }
    boolean live() { return player != null && player.live(); }

    /** Each frame on the render thread: advance the resolution, start the player, copy the newest picture. */
    void frame() {
        if (state == State.FAILED) return;
        if (player == null) {
            if (mrl == null) return;
            switch (mrl.status()) {
                case FETCHING -> { return; }
                case LOADED -> start();
                case BLOCKED -> { fail("contenu réservé aux adultes"); return; }
                case ERROR -> { fail(mrl.exception() == null ? "lien illisible" : message(mrl.exception())); return; }
                case EXPIRED, FORGOTTEN -> { mrl = mrl.reload(); return; }
            }
            if (player == null) return;
        }
        if (player.error()) {
            fail(player.exception() == null ? "lecture impossible" : message(player.exception()));
            return;
        }
        state = player.ended() ? State.ENDED : player.playing() || player.paused() ? State.PLAYING : State.LOADING;
        if (!caughtUp && player.playing()) {
            caughtUp = true;
            // Arriving after the start: join the video where the others are
            if (startTick >= 0 && player.canSeek() && Minecraft.getInstance().level != null) {
                long elapsedMs = (Minecraft.getInstance().level.getGameTime() - startTick) * 50L;
                long d = player.duration();
                if (elapsedMs > 1500 && d > 0) player.seek(elapsedMs % d);
            }
        }
        volume();
        copyPicture();
    }

    private void start() {
        state = State.LOADING;
        Thread render = Thread.currentThread();
        try {
            player = PlayerAPI.player(mrl, () -> PlayerAPI.glEngine(render, QUEUE), () -> PlayerAPI.alEngine(true));
        } catch (RuntimeException e) {
            fail(message(e));
            return;
        }
        if (player == null) {
            fail("aucune vidéo dans ce lien");
            return;
        }
        player.scale(MAX_W, MAX_H);
        player.repeat(true);
        try {
            player.spatialAudio(new SFXEngine.SpatialAudio(x, y, z, 6f, 48f, 1f, false, null));
        } catch (RuntimeException e) {
            CraftMaster.LOG.debug("[Vidéo] Son positionnel indisponible : {}", e.toString());
        }
        volume();
        if (!player.start()) fail("le lecteur refuse de démarrer");
    }

    private int lastVolume = -1;

    /** The jukebox volume (the master volume is the listener's, applied by Minecraft). */
    private void volume() {
        if (player == null) return;
        int v = Math.round(Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.RECORDS) * 100);
        if (v != lastVolume) {
            lastVolume = v;
            player.volume(v);
        }
    }

    /** GPU copy of WaterMedia's texture into ours (same RGBA8 layout, top row first like a NativeImage). */
    private void copyPicture() {
        long src = player.texture();
        int w = player.width(), h = player.height();
        if (src == 0 || w <= 0 || h <= 0) return;
        if (target == null || w != texW || h != texH) {
            DynamicTexture t = new DynamicTexture(() -> "craftmaster studio video " + url, w, h, false);
            Minecraft.getInstance().getTextureManager().register(textureId, t);
            target = t;
            texW = w;
            texH = h;
        }
        GlBridge.copyInto(src, target, w, h);
    }

    private void fail(String why) {
        state = State.FAILED;
        error = why == null || why.isBlank() ? "?" : why;
        CraftMaster.LOG.warn("[Vidéo] {} : {}", url, error);
    }

    /** Stops the player, frees its engines and our texture. Render thread. */
    void release() {
        if (player != null) {
            try {
                player.release();
            } catch (RuntimeException e) {
                CraftMaster.LOG.debug("[Vidéo] Libération : {}", e.toString());
            }
            player = null;
        }
        if (target != null) {
            Minecraft.getInstance().getTextureManager().release(textureId);
            target = null;
            texW = texH = 0;
        }
    }

    long age() {
        return System.currentTimeMillis() - createdAt;
    }

    static String message(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        String m = t.getMessage();
        if (m == null || m.isBlank()) return t.getClass().getSimpleName();
        if (m.contains("offline") || m.toLowerCase().contains("is not live") || m.contains("not streaming")) return "chaîne hors ligne";
        return m.length() > 90 ? m.substring(0, 90) + "…" : m;
    }
}
