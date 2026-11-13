package fr.lordfinn.craftmaster.client.media;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.media.ClipLinks;
import net.fabricmc.loader.api.FabricLoader;

import java.util.Locale;

/**
 * Videos on the studio screens and in-game clips, through WaterMedia when it is installed (the modpack ships it with
 * its Binaries, which bring FFmpeg; Minecraft 26.2 only). Without it the screens keep showing pictures and the clip
 * keys say why they do nothing. Nothing in this class touches WaterMedia's classes: {@link WaterMediaVideo},
 * {@link VideoScreens} and {@link ClipRecorder} only load once {@link #available()} said yes.
 */
public final class Media {

    private Media() {}

    private static Boolean installed;

    /** WaterMedia is installed (its classes can load). */
    public static boolean installed() {
        if (installed == null) installed = FabricLoader.getInstance().isModLoaded("watermedia");
        return installed;
    }

    /** WaterMedia is installed and its FFmpeg started: videos play, clips record. */
    public static boolean available() {
        if (!installed() || !GlBridge.supported()) return false;
        try {
            return WaterMediaVideo.ffmpegReady();
        } catch (LinkageError e) {
            CraftMaster.LOG.warn("[Vidéo] WaterMedia inutilisable : {}", e.toString());
            installed = false;
            return false;
        }
    }

    /** Why videos do not play, in French (null when they do). */
    public static String unavailableReason() {
        if (!GlBridge.supported()) return "vidéos et clips : Minecraft 26.2 seulement (WaterMedia n'existe pas en 26.3)";
        if (!installed()) return "WaterMedia n'est pas installé (modpack, Minecraft 26.2)";
        try {
            return WaterMediaVideo.ffmpegReady() ? null : "FFmpeg de WaterMedia indisponible (WaterMedia Binaries manquant, ou Flashback installé)";
        } catch (LinkageError e) {
            return "WaterMedia inutilisable (" + e.getClass().getSimpleName() + ")";
        }
    }

    /** Called at the start of every frame on the render thread (GameRendererMixin). */
    public static void frameStart() {
        if (installed != null && installed) VideoScreens.frameStart();
    }

    /**
     * The address is a video for WaterMedia rather than a picture for ScreenImages: a CraftMaster clip, a video file,
     * an HLS stream, or a page of a video site (Twitch, Kick, YouTube, Medal, Streamable...).
     */
    public static boolean isVideo(String url) {
        if (url == null || url.isEmpty()) return false;
        String u = url.toLowerCase(Locale.ROOT);
        if (u.startsWith(ClipLinks.SCHEME)) return true;
        if (u.startsWith("water://")) return true;
        String path = u.replaceAll("[?#].*$", "");
        for (String ext : new String[]{".mp4", ".webm", ".mkv", ".mov", ".m3u8", ".m4v", ".ogv", ".avi", ".mp3", ".ogg", ".wav", ".flac"})
            if (path.endsWith(ext)) return true;
        String host = host(u);
        return host.endsWith("twitch.tv") || host.endsWith("kick.com") || host.endsWith("youtube.com") || host.equals("youtu.be")
                || host.endsWith("medal.tv") || host.endsWith("streamable.com") || host.endsWith("tiktok.com")
                || host.endsWith("x.com") || host.endsWith("twitter.com") || host.endsWith("bsky.app") || host.endsWith("dailymotion.com");
    }

    /**
     * A clip of the server, a Twitch / Kick page or an HLS stream: played by the clips' player ({@link VideoScreens}),
     * which knows the clip cache and live streams. Other videos go to the studio screens' player (pause, seek, loop).
     */
    public static boolean isClipOrStream(String url) {
        if (url == null || url.isEmpty()) return false;
        String u = url.toLowerCase(Locale.ROOT);
        if (u.startsWith(ClipLinks.SCHEME)) return true;
        String host = host(u);
        return host.endsWith("twitch.tv") || host.endsWith("kick.com") || u.replaceAll("[?#].*$", "").endsWith(".m3u8");
    }

    static String host(String lowerUrl) {
        int s = lowerUrl.indexOf("://");
        if (s < 0) return "";
        String rest = lowerUrl.substring(s + 3);
        int end = rest.length();
        for (char c : new char[]{'/', '?', '#', ':'}) {
            int i = rest.indexOf(c);
            if (i >= 0 && i < end) end = i;
        }
        String h = rest.substring(0, end);
        return h.startsWith("www.") ? h.substring(4) : h;
    }
}
