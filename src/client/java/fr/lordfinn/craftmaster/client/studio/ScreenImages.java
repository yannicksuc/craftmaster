package fr.lordfinn.craftmaster.client.studio;

import com.mojang.blaze3d.platform.NativeImage;
import fr.lordfinn.craftmaster.CraftMaster;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Images of the studio screens, downloaded by each player's game from the screen's URL: asynchronous, one download
 * per address, kept in memory for the session and on disk ({@code craftmaster/image-cache/} in the game folder) so
 * that coming back does not download again. PNG, JPEG, GIF (first frame) and BMP up to {@link #MAX_BYTES}; bigger
 * pictures are scaled down to {@link #MAX_SIDE} pixels. Anything else falls back to the test card with a message.
 */
public final class ScreenImages {

    private ScreenImages() {}

    public static final int MAX_BYTES = 12 * 1024 * 1024;
    public static final int MAX_SIDE = 2048;
    /** A file in the disk cache older than this is downloaded again. */
    private static final Duration CACHE_AGE = Duration.ofDays(7);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();

    public enum Status { LOADING, READY, FAILED }

    /** One address: its state, the texture once ready (and its size, for the "fit" look), or why it failed. */
    public static final class Entry {
        volatile Status status = Status.LOADING;
        Identifier texture;
        int width, height;
        String error = "";

        public Status status() { return status; }
        public Identifier texture() { return texture; }
        public int width() { return width; }
        public int height() { return height; }
        public String error() { return error; }
    }

    private static final Map<String, Entry> CACHE = new HashMap<>();
    private static int nextId;

    /** The entry of an address, starting its download the first time. Render thread only. */
    public static Entry get(String url) {
        Entry e = CACHE.get(url);
        if (e == null) {
            e = new Entry();
            CACHE.put(url, e);
            load(url, e);
        }
        return e;
    }

    private record Decoded(NativeImage image, int width, int height) {}

    private static void load(String url, Entry entry) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return decode(bytes(url));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }).whenComplete((img, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null || img == null) {
                entry.error = rootMessage(error);
                entry.status = Status.FAILED;
                CraftMaster.LOG.warn("[Écrans] Image inutilisable {} : {}", url, entry.error);
                return;
            }
            Identifier id = CraftMaster.id("studio_screen/" + (nextId++));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "craftmaster studio screen " + url, img.image()));
            entry.texture = id;
            entry.width = img.width();
            entry.height = img.height();
            entry.status = Status.READY;
        }));
    }

    /** The file's bytes: from the disk cache if fresh, else downloaded (and cached). */
    private static byte[] bytes(String url) throws IOException, InterruptedException {
        Path file = cacheFile(url);
        try {
            if (Files.isRegularFile(file) && Files.getLastModifiedTime(file).toInstant().isAfter(java.time.Instant.now().minus(CACHE_AGE)))
                return Files.readAllBytes(file);
        } catch (IOException ignored) {
            // Unreadable cache: download again
        }
        if (url.startsWith(fr.lordfinn.craftmaster.media.ServerImages.SCHEME)) {
            // A picture made by the server (walked drawings, jury board): through the game's connection
            byte[] data = ServerImagesClient.fetch(url);
            try {
                Files.createDirectories(file.getParent());
                Files.write(file, data);
            } catch (IOException ignored) {
            }
            return data;
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "CraftMaster-StudioScreen/1.0 (Minecraft mod; image screens)").header("Accept", "image/*").GET().build();
        HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() / 100 != 2) throw new IOException("HTTP " + resp.statusCode());
        byte[] data;
        try (InputStream in = resp.body()) {
            data = in.readNBytes(MAX_BYTES + 1);
        }
        if (data.length > MAX_BYTES) throw new IOException("image trop lourde (" + MAX_BYTES / 1024 / 1024 + " Mo au plus)");
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, data);
        } catch (IOException e) {
            CraftMaster.LOG.debug("[Écrans] Cache disque indisponible : {}", e.getMessage());
        }
        return data;
    }

    private static Path cacheFile(String url) {
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(url.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            hash = Integer.toHexString(url.hashCode());
        }
        return FabricLoader.getInstance().getGameDir().resolve("craftmaster/image-cache/" + hash + ".img");
    }

    /** Any format Java reads (PNG, JPEG, GIF, BMP), else Minecraft's own PNG reader; scaled down if too big. */
    private static Decoded decode(byte[] data) throws IOException {
        BufferedImage img = null;
        try {
            img = ImageIO.read(new ByteArrayInputStream(data));
        } catch (Exception | LinkageError ignored) {
            // No java.desktop or an unknown format: try the game's reader
        }
        if (img == null) {
            NativeImage ni;
            try {
                ni = NativeImage.read(data);
            } catch (IOException e) {
                throw new IOException("format non pris en charge (PNG, JPEG, GIF ou BMP)");
            }
            if (ni.getWidth() > MAX_SIDE || ni.getHeight() > MAX_SIDE) {
                int w = ni.getWidth(), h = ni.getHeight();
                ni.close();
                throw new IOException("image trop grande (" + w + "×" + h + ", " + MAX_SIDE + " px au plus pour ce format)");
            }
            return new Decoded(ni, ni.getWidth(), ni.getHeight());
        }
        int w = img.getWidth(), h = img.getHeight();
        if (w <= 0 || h <= 0) throw new IOException("image vide");
        double scale = Math.min(1.0, MAX_SIDE / (double) Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * scale)), th = Math.max(1, (int) Math.round(h * scale));
        BufferedImage argb = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = argb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, 0, 0, tw, th, null);
        g.dispose();
        NativeImage out = new NativeImage(tw, th, false);
        for (int y = 0; y < th; y++)
            for (int x = 0; x < tw; x++) out.setPixel(x, y, argb.getRGB(x, y));
        return new Decoded(out, w, h);
    }

    private static String rootMessage(Throwable t) {
        if (t == null) return "?";
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    /** Forgets the images (leaving a world); the disk cache stays. */
    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (Entry e : CACHE.values()) if (e.texture != null) mc.getTextureManager().release(e.texture);
        CACHE.clear();
    }
}
