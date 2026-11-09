package fr.lordfinn.craftmaster.client.portrait;

import com.mojang.blaze3d.platform.NativeImage;
import fr.lordfinn.craftmaster.CraftMaster;
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
 * or an image downloaded from the portrait's URL. Downloads are asynchronous and cached per URL; the image must
 * be a 64×64 or legacy 64×32 PNG, anything else (or an unreachable link) falls back to the default skin.
 */
public final class PortraitSkins {

    private PortraitSkins() {}

    public record Skin(Identifier texture, boolean slim) {}

    private static final int MAX_BYTES = 512 * 1024;
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
        return new Skin(s.body().texturePath(), s.model() == PlayerModelType.SLIM);
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
                HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                        .header("User-Agent", "CraftMaster-Portraits").GET().build();
                HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() / 100 != 2) throw new IOException("HTTP " + resp.statusCode());
                byte[] bytes;
                try (InputStream in = resp.body()) {
                    bytes = in.readNBytes(MAX_BYTES + 1);
                }
                if (bytes.length > MAX_BYTES) throw new IOException("image too large");
                NativeImage image = NativeImage.read(bytes);
                if (image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
                    int w = image.getWidth(), h = image.getHeight();
                    image.close();
                    throw new IOException("not a skin (" + w + "x" + h + ", 64x64 or 64x32 expected)");
                }
                return image.getHeight() == 32 ? toModern(image) : image;
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
            entry.skin = new Skin(id, slim);
            entry.state = State.READY;
        }));
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    /** Slim (3 px) arms leave the 4th column of the right arm's front transparent. */
    static boolean isSlim(NativeImage image) {
        return (image.getPixel(54, 20) >>> 24) == 0 && (image.getPixel(55, 31) >>> 24) == 0;
    }

    /** Legacy 64×32 skin: the left arm and leg are copies (mirrored) of the right ones, like vanilla does. */
    private static NativeImage toModern(NativeImage old) {
        NativeImage img = new NativeImage(64, 64, true);
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 64; x++) img.setPixel(x, y, old.getPixel(x, y));
        old.close();
        img.fillRect(0, 32, 64, 32, 0);
        img.copyRect(4, 16, 16, 32, 4, 4, true, false);
        img.copyRect(8, 16, 16, 32, 4, 4, true, false);
        img.copyRect(0, 20, 24, 32, 4, 12, true, false);
        img.copyRect(4, 20, 16, 32, 4, 12, true, false);
        img.copyRect(8, 20, 8, 32, 4, 12, true, false);
        img.copyRect(12, 20, 16, 32, 4, 12, true, false);
        img.copyRect(44, 16, -8, 32, 4, 4, true, false);
        img.copyRect(48, 16, -8, 32, 4, 4, true, false);
        img.copyRect(40, 20, 0, 32, 4, 12, true, false);
        img.copyRect(44, 20, -8, 32, 4, 12, true, false);
        img.copyRect(48, 20, -16, 32, 4, 12, true, false);
        img.copyRect(52, 20, -8, 32, 4, 12, true, false);
        return img;
    }

    /** Forgets downloaded skins (leaving a world): a new visit downloads them again. */
    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (Entry e : URL_CACHE.values()) if (e.skin != null) mc.getTextureManager().release(e.skin.texture());
        URL_CACHE.clear();
    }
}
