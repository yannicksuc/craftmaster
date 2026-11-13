package fr.lordfinn.craftmaster.client.portrait;

import com.mojang.blaze3d.platform.NativeImage;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.portrait.SkinFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The skin a portrait wears: the shown player's own (looked up from the name by the game, like a player head),
 * or an image downloaded from the portrait's URL (or read from a PNG file, for the title screen). Downloads are asynchronous and cached per URL; the image must
 * be a skin PNG, 64×64 or legacy 64×32, or an HD one (128×128, 256×256... up to 1024, see {@link SkinFormat});
 * anything else (or an unreachable link) falls back to the default skin. A player's own skin brings their cape.
 */
public final class PortraitSkins {

    private PortraitSkins() {}

    /** @param cape the cape's texture, or null */
    public record Skin(Identifier texture, boolean slim, Identifier cape) {}
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private enum State { LOADING, READY, FAILED }

    private static final class Entry {
        volatile State state = State.LOADING;
        volatile Skin skin;
    }

    private static final Map<String, Entry> URL_CACHE = new HashMap<>();
    private static int nextId;

    private static Skin of(PlayerSkin s) {
        return new Skin(s.body().texturePath(), s.model() == PlayerModelType.SLIM, s.cape() == null ? null : s.cape().texturePath());
    }

    public static Skin defaultSkin() {
        return of(DefaultPlayerSkin.getDefaultSkin());
    }

    /**
     * @param player shown player (empty: nobody)
     * @param url    skin URL set on the portrait (empty: the player's own skin)
     */
    public static Skin resolve(String player, String url) {
        if (url != null && !url.isEmpty()) {
            Skin s = fromUrl(url);
            return s != null ? s : defaultSkin();
        }
        if (player == null || player.isEmpty()) return defaultSkin();
        return of(Minecraft.getInstance().playerSkinRenderCache().getOrDefault(ResolvableProfile.createUnresolved(player)).playerSkin());
    }

    /** The downloaded skin, or null while it loads or if it could not be used. Called on the render thread. */
    private static Skin fromUrl(String url) {
        Entry e = URL_CACHE.get(url);
        if (e == null) {
            e = new Entry();
            URL_CACHE.put(url, e);
            download(url, e);
        }
        return e.state == State.READY ? e.skin : null;
    }

    private static void download(String url, Entry entry) {
        CompletableFuture.supplyAsync(() -> {
            try {
                byte[] bytes;
                if (isWeb(url)) {
                    HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                            .header("User-Agent", "CraftMaster-Portraits").GET().build();
                    HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
                    if (resp.statusCode() / 100 != 2) throw new IOException("HTTP " + resp.statusCode());
                    try (InputStream in = resp.body()) {
                        bytes = in.readNBytes(SkinFormat.MAX_BYTES + 1);
                    }
                } else {
                    // A PNG file on this computer (the title screen's skins, see client.home)
                    try (InputStream in = java.nio.file.Files.newInputStream(url.startsWith("file:") ? java.nio.file.Path.of(URI.create(url)) : java.nio.file.Path.of(url))) {
                        bytes = in.readNBytes(SkinFormat.MAX_BYTES + 1);
                    }
                }
                if (bytes.length > SkinFormat.MAX_BYTES) throw new IOException("image too large");
                NativeImage image = NativeImage.read(bytes);
                int w = image.getWidth(), h = image.getHeight();
                if (SkinFormat.scale(w, h) == 0) {
                    image.close();
                    throw new IOException("not a skin (" + w + "x" + h + ", 64x64, 64x32 or HD 128x128, 256x256... expected)");
                }
                return SkinFormat.legacy(w, h) ? toModern(image) : image;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }).whenComplete((image, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null || image == null) {
                entry.state = State.FAILED;
                CraftMaster.LOG.warn("[Portraits] Skin inutilisable {} : {}", url, error == null ? "?" : rootMessage(error));
                return;
            }
            boolean slim = isSlim(image);
            Identifier id = CraftMaster.id("portrait_skin/" + (nextId++) + "_" + Integer.toHexString(url.hashCode()).toLowerCase(Locale.ROOT));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "craftmaster portrait skin " + url, image));
            entry.skin = new Skin(id, slim, null);
            entry.state = State.READY;
        }));
    }

    private static boolean isWeb(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        return u.startsWith("http://") || u.startsWith("https://");
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    /** Slim (3 px) arms leave the 4th column of the right arm's front transparent (at any HD scale). */
    static boolean isSlim(NativeImage image) {
        int k = SkinFormat.scale(image.getWidth(), image.getHeight());
        for (int[] p : SkinFormat.slimProbes(k)) if ((image.getPixel(p[0], p[1]) >>> 24) != 0) return false;
        return true;
    }

    /** Legacy (2:1) skin: the left arm and leg are copies (mirrored) of the right ones, like vanilla does. */
    private static NativeImage toModern(NativeImage old) {
        int w = old.getWidth(), half = old.getHeight(), k = SkinFormat.scale(w, half);
        NativeImage img = new NativeImage(w, w, true);
        for (int y = 0; y < half; y++)
            for (int x = 0; x < w; x++) img.setPixel(x, y, old.getPixel(x, y));
        old.close();
        img.fillRect(0, half, w, half, 0);
        for (int[] c : SkinFormat.legacyCopies(k)) img.copyRect(c[0], c[1], c[2], c[3], c[4], c[5], true, false);
        return img;
    }

    /**
     * Forgets one skin once it is loaded or has failed (not while it loads): the next {@link #resolve} reads it
     * again. The title screen calls it for its own skins each time it opens, so an edited file or a link that was
     * down shows up without restarting.
     */
    public static void forget(String url) {
        Entry e = URL_CACHE.get(url);
        if (e == null || e.state == State.LOADING) return;
        URL_CACHE.remove(url);
        if (e.skin != null) Minecraft.getInstance().getTextureManager().release(e.skin.texture());
    }

    /** Forgets downloaded skins (leaving a world): a new visit downloads them again. */
    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (Entry e : URL_CACHE.values()) if (e.skin != null) mc.getTextureManager().release(e.skin.texture());
        URL_CACHE.clear();
    }
}
