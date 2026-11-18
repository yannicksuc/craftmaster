package fr.lordfinn.craftmaster.client.studio;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.studio.ImageScreenEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.watermedia.WaterMedia;
import org.watermedia.api.player.MRL;
import org.watermedia.api.player.MediaPlayer;
import org.watermedia.api.player.PlayerAPI;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/**
 * The WaterMedia side of {@link ScreenVideos} (loaded only when WaterMedia is there): one media player per studio
 * screen showing a video, its OpenGL frames drawn through {@link VideoTexture}, its sound through OpenAL.
 *
 * <ul>
 *   <li><b>Same picture for everyone</b>: each game plays at the position the server gives
 *   ({@link ImageScreenEntity#videoPosition}), looping on the video's length; a game more than 2 s off seeks.</li>
 *   <li><b>Sound</b>: the screen's volume (/cm media image volume), the "Jukebox / Note Blocks" slider, full up to 4 blocks
 *   away, fading out at {@link #HEARING} blocks.</li>
 *   <li>A screen out of sight for {@link #FORGET_MS} ms, gone, or farther than {@link #KEEP} blocks stops its
 *   player (it starts again, at the right position, when seen again).</li>
 * </ul>
 */
final class WaterMediaScreens {

    private WaterMediaScreens() {}

    private static final double HEARING = 40, KEEP = 128;
    private static final long FORGET_MS = 5000, SEEK_TOLERANCE_MS = 2000, SEEK_COOLDOWN_MS = 3000;

    /** GL work of the engines, run on the render thread by {@link #pump()} (every frame a screen is drawn, every tick). */
    private static final Queue<Runnable> GL_TASKS = new ConcurrentLinkedQueue<>();
    private static final Executor RENDER_EXECUTOR = GL_TASKS::add;

    private static final Map<Integer, Playback> PLAYBACKS = new HashMap<>();
    private static int nextTexture;

    private static final class Playback {
        final String url;
        MRL mrl;
        MediaPlayer player;
        VideoTexture texture;
        Identifier textureId;
        String error;
        long seen, lastSeek;

        Playback(String url) {
            this.url = url;
        }
    }

    static boolean ready() {
        return WaterMedia.loaded();
    }

    private static void pump() {
        for (Runnable r; (r = GL_TASKS.poll()) != null; ) {
            try {
                r.run();
            } catch (Throwable t) {
                CraftMaster.LOG.warn("[Écrans] Tâche WaterMedia en échec : {}", t.toString());
            }
        }
    }

    static ScreenVideos.Frame frame(ImageScreenEntity screen, float partialTick) {
        pump();
        long now = System.currentTimeMillis();
        Playback p = PLAYBACKS.get(screen.getId());
        if (p == null || !p.url.equals(screen.url())) {
            if (p != null) release(p);
            p = new Playback(screen.url());
            PLAYBACKS.put(screen.getId(), p);
        }
        p.seen = now;
        if (p.error != null) return ScreenVideos.Frame.failed(p.error);

        if (p.player == null) {
            if (p.mrl == null) p.mrl = PlayerAPI.mrl(p.url);
            MRL.Status status = p.mrl.status();
            if (status.failed()) return fail(p, p.mrl.exception());
            if (!status.loaded()) return ScreenVideos.Frame.loading();
            Thread renderThread = Thread.currentThread();
            p.player = PlayerAPI.player(p.mrl, () -> PlayerAPI.glEngine(renderThread, RENDER_EXECUTOR), PlayerAPI::alEngine);
            if (p.player == null) return fail(p, p.mrl.exception());
            p.player.repeat(screen.loop());
            p.player.volume(0);
            if (screen.paused()) p.player.startPaused();
            else p.player.start();
            p.lastSeek = now;
            return ScreenVideos.Frame.loading();
        }

        MediaPlayer mp = p.player;
        if (mp.error()) return fail(p, null);
        follow(screen, p, mp, partialTick, now);

        long tex = mp.texture();
        int w = mp.width(), h = mp.height();
        if (tex == MediaPlayer.NO_TEXTURE || w <= 0 || h <= 0) return ScreenVideos.Frame.loading();
        if (p.texture == null || !p.texture.is((int) tex, w, h)) {
            Minecraft mc = Minecraft.getInstance();
            if (p.textureId != null) mc.getTextureManager().release(p.textureId);
            p.texture = new VideoTexture((int) tex, w, h);
            p.textureId = CraftMaster.id("studio_screen/video_" + (nextTexture++));
            mc.getTextureManager().register(p.textureId, p.texture);
        }
        return new ScreenVideos.Frame(ScreenVideos.Status.READY, p.textureId, w, h, "");
    }

    /** Plays, pauses and seeks to stay where the server says the video is. */
    private static void follow(ImageScreenEntity screen, Playback p, MediaPlayer mp, float partialTick, long now) {
        mp.repeat(screen.loop());
        if (screen.paused()) {
            if (mp.playing()) mp.pause();
        } else if (mp.paused()) {
            mp.resume();
        }
        if (mp.live() || !(mp.playing() || mp.paused())) return;
        long duration = mp.duration();
        long want = screen.videoPosition(partialTick);
        if (duration > 0) want = screen.loop() ? want % duration : Math.min(want, duration);
        long off = Math.abs(mp.time() - want);
        // Near the loop point the two ends are close too
        if (duration > 0 && screen.loop()) off = Math.min(off, duration - off);
        if (off > SEEK_TOLERANCE_MS && now - p.lastSeek > SEEK_COOLDOWN_MS) {
            mp.seek(want);
            p.lastSeek = now;
        }
    }

    private static ScreenVideos.Frame fail(Playback p, Throwable cause) {
        Throwable t = cause;
        while (t != null && t.getCause() != null) t = t.getCause();
        p.error = t == null || t.getMessage() == null ? (t == null ? "lecture impossible" : t.getClass().getSimpleName()) : t.getMessage();
        CraftMaster.LOG.warn("[Écrans] Vidéo illisible {} : {}", p.url, p.error);
        if (p.player != null) {
            p.player.release();
            p.player = null;
        }
        return ScreenVideos.Frame.failed(p.error);
    }

    static void tick(Minecraft mc) {
        pump();
        if (PLAYBACKS.isEmpty()) return;
        long now = System.currentTimeMillis();
        Vec3 ear = mc.gameRenderer.mainCamera().position();
        float slider = mc.options.getFinalSoundSourceVolume(SoundSource.RECORDS);
        for (Iterator<Map.Entry<Integer, Playback>> it = PLAYBACKS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Playback> e = it.next();
            Playback p = e.getValue();
            ImageScreenEntity screen = mc.level != null && mc.level.getEntity(e.getKey()) instanceof ImageScreenEntity s && !s.isRemoved() ? s : null;
            double distance = screen == null ? Double.MAX_VALUE : Math.sqrt(screen.getBoundingBox().distanceToSqr(ear));
            if (screen == null || distance > KEEP || now - p.seen > FORGET_MS) {
                release(p);
                it.remove();
                continue;
            }
            if (p.player != null) {
                double fade = Math.max(0, Math.min(1, 1 - (distance - 4) / (HEARING - 4)));
                p.player.volume((int) Math.round(screen.volume() * slider * fade * fade));
            }
        }
        pump();
    }

    static void clear() {
        PLAYBACKS.values().forEach(WaterMediaScreens::release);
        PLAYBACKS.clear();
        pump();
    }

    private static void release(Playback p) {
        if (p.textureId != null) Minecraft.getInstance().getTextureManager().release(p.textureId);
        p.textureId = null;
        p.texture = null;
        if (p.player != null) p.player.release();
        p.player = null;
    }
}
