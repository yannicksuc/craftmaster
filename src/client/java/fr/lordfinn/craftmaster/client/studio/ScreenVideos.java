package fr.lordfinn.craftmaster.client.studio;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.studio.ImageScreenEntity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Videos on the studio screens, played by WaterMedia (an optional library mod: https://modrinth.com/mod/watermedia,
 * with its binaries). A screen whose address is not a plain image (.png, .jpg...) plays it as a video when the
 * player's game has WaterMedia: YouTube, Twitch, Kick, a direct MP4 / WebM link... Without WaterMedia (or on
 * Minecraft 26.3, see {@link VideoTexture}) the screens show images only, as before.
 *
 * <p>This class never touches a WaterMedia class itself ({@link WaterMediaScreens} does, once it is known to be
 * there), so the mod loads fine without it.
 */
public final class ScreenVideos {

    private ScreenVideos() {}

    public enum Status { LOADING, READY, FAILED }

    /** What a screen shows now: the frame's texture and size when READY, why not when FAILED. */
    public record Frame(Status status, Identifier texture, int width, int height, String error) {
        static Frame loading() { return new Frame(Status.LOADING, null, 0, 0, ""); }
        static Frame failed(String error) { return new Frame(Status.FAILED, null, 0, 0, error); }
    }

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");
    private static Boolean available;

    /** WaterMedia is there and started, on a Minecraft version that can draw its frames. */
    public static boolean available() {
        if (available == null) {
            boolean ok = VideoTexture.supported() && FabricLoader.getInstance().isModLoaded("watermedia");
            if (ok) {
                try {
                    ok = WaterMediaScreens.ready();
                } catch (Throwable t) {
                    CraftMaster.LOG.warn("[Écrans] WaterMedia présent mais inutilisable : {}", t.toString());
                    ok = false;
                }
            }
            available = ok;
            if (ok) CraftMaster.LOG.info("[Écrans] WaterMedia trouvé : les écrans du studio lisent aussi les vidéos");
        }
        return available;
    }

    /** An address of a plain image (by its extension): shown by {@link ScreenImages}, never played as a video. */
    public static boolean isImageUrl(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path == null) return false;
            int dot = path.lastIndexOf('.');
            return dot >= 0 && IMAGE_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** This screen's address is played as a video here. */
    public static boolean plays(String url) {
        return !url.isEmpty() && !isImageUrl(url) && available();
    }

    /** The frame to show on a screen (render thread); starts or follows its playback. */
    public static Frame frame(ImageScreenEntity screen, float partialTick) {
        return WaterMediaScreens.frame(screen, partialTick);
    }

    /** Every client tick: volume by distance, players of screens gone or far away released. */
    public static void tick(Minecraft mc) {
        if (available != null && available) WaterMediaScreens.tick(mc);
    }

    /** Leaving a world: every video stops. */
    public static void clear() {
        if (available != null && available) WaterMediaScreens.clear();
    }
}
