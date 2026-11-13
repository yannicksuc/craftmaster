package fr.lordfinn.craftmaster.client.media;

import fr.lordfinn.craftmaster.CraftMaster;
import net.fabricmc.loader.api.FabricLoader;
import org.watermedia.api.codecs.CodecsAPI;
import org.watermedia.api.codecs.Container;
import org.watermedia.api.codecs.Encoder;
import org.watermedia.api.codecs.VideoCodec;
import org.watermedia.api.util.PixelFormat;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Records the current view into an MP4, like Flashback's video export but while playing: the world picture (before
 * the interface is drawn: no HUD, no chat) is read back at a fixed frame rate without stalling the frame, and a
 * separate thread encodes it in H.264 with WaterMedia's FFmpeg. Works the same live and during a replay (it records
 * what this game shows). No sound yet. Render thread for {@link #start}, {@link #stop}, {@link #afterLevel}.
 */
final class ClipRecorder {

    /** Frames waiting for the encoder; beyond this the newest is dropped (the encoder is behind). */
    private static final int QUEUE = 6;

    private final Path file;
    private final int fps;
    private final int quality;
    private final long frameUs;
    private final String source;
    private final long startNanos = System.nanoTime();
    private long nextFrameUs;
    private long lastPtsUs = -1;
    private int width, height;
    private volatile boolean stopping;
    private boolean finished;
    private final AtomicInteger dropped = new AtomicInteger();
    private final AtomicInteger encoded = new AtomicInteger();
    private volatile String error;

    private record Frame(ByteBuffer data, int width, int height, long ptsUs) {}

    private static final Frame END = new Frame(null, 0, 0, -1);
    private final BlockingQueue<Frame> frames = new ArrayBlockingQueue<>(QUEUE + 1);
    private final BlockingQueue<ByteBuffer> spare = new ArrayBlockingQueue<>(QUEUE + 4);
    private Thread encoder;
    private Consumer<ClipRecorder> onFinished;

    private ClipRecorder(Path file, int fps, int quality, String source) {
        this.file = file;
        this.fps = fps;
        this.quality = quality;
        this.frameUs = 1_000_000L / fps;
        this.source = source;
    }

    Path file() { return file; }
    String source() { return source; }
    String error() { return error; }
    int durationMs() { return (int) Math.max(0, lastPtsUs / 1000); }
    int frames() { return encoded.get(); }
    int dropped() { return dropped.get(); }
    long elapsedMs() { return (System.nanoTime() - startNanos) / 1_000_000; }
    boolean stopping() { return stopping; }

    static Path folder() {
        return FabricLoader.getInstance().getGameDir().resolve("craftmaster/clips");
    }

    /** Starts a recording into {@code craftmaster/clips/clip-<date>.mp4}. */
    static ClipRecorder start(int fps, int quality, String source) throws java.io.IOException {
        Files.createDirectories(folder());
        String name = "clip-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + ".mp4";
        return new ClipRecorder(folder().resolve(name), Math.max(10, Math.min(60, fps)), Math.max(10, Math.min(100, quality)), source);
    }

    /** After the world is drawn, before the interface: one picture when its time has come. */
    void afterLevel() {
        if (stopping || error != null) return;
        long now = (System.nanoTime() - startNanos) / 1000;
        if (now < nextFrameUs) return;
        // Catch up without bursts: the next picture is one frame later than this one's slot
        nextFrameUs = Math.max(nextFrameUs + frameUs, now - frameUs);
        if (!GlBridge.grab(now, this::received)) dropped.incrementAndGet();
    }

    /** A picture read back from the GPU (render thread): copied, then encoded on the encoder thread. */
    private void received(ByteBuffer bottomUp, int w, int h, long ptsUs) {
        if (error != null) return;
        if (encoder == null) {
            // The size is fixed by the first picture (even sides, as H.264 wants)
            width = w & ~1;
            height = h & ~1;
            encoder = Thread.ofPlatform().daemon().name("CraftMaster clip encoder").start(this::encodeLoop);
        }
        if (stopping && ptsUs > lastQueuedUs) return;
        if (w < width || h < height) {
            dropped.incrementAndGet();
            return;
        }
        ByteBuffer copy = spare.poll();
        int bytes = w * h * 4;
        if (copy == null || copy.capacity() < bytes) copy = ByteBuffer.allocateDirect(bytes);
        copy.clear();
        ByteBuffer src = bottomUp.duplicate();
        src.limit(Math.min(src.capacity(), bytes));
        copy.put(src).flip();
        if (!frames.offer(new Frame(copy, w, h, ptsUs))) {
            dropped.incrementAndGet();
            spare.offer(copy);
        }
    }

    private volatile long lastQueuedUs = Long.MAX_VALUE;

    private void encodeLoop() {
        Encoder.Video video = new Encoder.Video(VideoCodec.H264, width, height, PixelFormat.RGBA, fps, quality, 0);
        ByteBuffer topDown = ByteBuffer.allocateDirect((int) video.frameBytes());
        try (Encoder enc = CodecsAPI.encoder(Container.MP4, file, video, null)) {
            while (true) {
                Frame f = frames.take();
                if (f == END) break;
                flip(f, topDown);
                long pts = Math.max(f.ptsUs, lastPtsUs + 1);
                enc.video(topDown, pts);
                lastPtsUs = pts;
                encoded.incrementAndGet();
                spare.offer(f.data);
            }
        } catch (Exception | LinkageError e) {
            error = WaterMediaVideo.message(e);
            CraftMaster.LOG.warn("[Clips] Encodage impossible", e);
        }
        Runnable done = () -> {
            finished = true;
            if (onFinished != null) onFinished.accept(this);
        };
        net.minecraft.client.Minecraft.getInstance().execute(done);
    }

    /** Bottom-up rows of {@code f} (any size ≥ ours) into our top-down picture, cropped to the even size. */
    private void flip(Frame f, ByteBuffer out) {
        int row = width * 4, srcRow = f.width * 4;
        out.clear();
        ByteBuffer src = f.data.duplicate();
        for (int y = 0; y < height; y++) {
            int from = (f.height - 1 - y) * srcRow;
            src.limit(from + row).position(from);
            out.put(src);
        }
        out.flip();
    }

    /**
     * Stops: pictures already asked from the GPU still arrive, then the file is finished on the encoder thread and
     * {@code done} runs on the client thread (with {@link #error()} set if it failed). {@link #poll()} moves it on.
     */
    void stop(Consumer<ClipRecorder> done) {
        if (stopping) return;
        stopping = true;
        onFinished = done;
        lastQueuedUs = (System.nanoTime() - startNanos) / 1000;
        poll();
    }

    private boolean endQueued;

    /** Each frame while stopping: once the GPU gave back every picture asked, the end mark goes to the encoder. */
    void poll() {
        if (!stopping || endQueued || GlBridge.pending() > 0) return;
        endQueued = true;
        GlBridge.releaseGrab();
        if (encoder == null) {
            // Not one picture arrived
            if (error == null) error = "aucune image enregistrée";
            finished = true;
            if (onFinished != null) onFinished.accept(this);
            return;
        }
        if (!frames.offer(END)) {
            frames.poll();
            dropped.incrementAndGet();
            frames.offer(END);
        }
    }

    boolean finished() { return finished; }
}
