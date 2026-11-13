package fr.lordfinn.craftmaster.client.media;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.media.ClipLinks;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The videos of the studio screens in sight: one WaterMedia player per screen, started when the screen is first
 * drawn with a video address, stopped a few seconds after it is no longer drawn (out of sight, too far, address
 * changed), so a live stream only downloads while someone can see it. Render thread only.
 */
public final class VideoScreens {

    private VideoScreens() {}

    /** A screen not drawn for this long stops its video (and its download). */
    private static final long UNSEEN_MS = 4000;

    private static final class Slot {
        WaterMediaVideo video;
        long seenAt;
        String url;
        long startTick;
    }

    private static final Map<Integer, Slot> SLOTS = new HashMap<>();
    private static int serial;

    /** What a screen shows for its video address right now. */
    public record View(Identifier texture, int width, int height, String message, boolean error) {}

    /**
     * Called by the screen renderer every frame the screen is drawn.
     * @param startTick game time the address was set on the screen (-1: unknown)
     */
    public static View view(int entityId, String url, long startTick, double x, double y, double z) {
        if (!Media.available()) return new View(null, 0, 0, Media.unavailableReason(), true);
        Slot s = SLOTS.computeIfAbsent(entityId, k -> new Slot());
        s.seenAt = System.currentTimeMillis();
        if (!url.equals(s.url) || startTick != s.startTick) {
            if (s.video != null) s.video.release();
            s.video = null;
            s.url = url;
            s.startTick = startTick;
        }
        if (s.video == null) {
            String play = url;
            String clip = ClipLinks.id(url);
            if (clip != null) {
                ClipCache.Status c = ClipCache.get(clip);
                if (c.error() != null) return new View(null, 0, 0, c.error(), true);
                if (c.localUri() == null) return new View(null, 0, 0, "Clip : " + c.percent() + " %", false);
                play = c.localUri();
            }
            s.video = new WaterMediaVideo(url, play, serial++, x, y, z, startTick);
        }
        WaterMediaVideo v = s.video;
        return switch (v.state()) {
            case FAILED -> new View(null, 0, 0, v.error(), true);
            case RESOLVING, LOADING -> v.texture() != null ? new View(v.texture(), v.width(), v.height(), null, false)
                    : new View(null, 0, 0, null, false);
            default -> new View(v.texture(), v.width(), v.height(), null, false);
        };
    }

    /** Start of each frame: WaterMedia's GL work, then every video moves on; unseen ones stop. */
    static void frameStart() {
        WaterMediaVideo.QUEUE.pump();
        if (SLOTS.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<Slot> it = SLOTS.values().iterator();
        while (it.hasNext()) {
            Slot s = it.next();
            if (now - s.seenAt > UNSEEN_MS) {
                if (s.video != null) s.video.release();
                it.remove();
                continue;
            }
            if (s.video != null) {
                try {
                    s.video.frame();
                } catch (RuntimeException e) {
                    CraftMaster.LOG.warn("[Vidéo] {}", s.url, e);
                    s.video.release();
                    s.video = null;
                    s.url = null;
                }
            }
        }
    }

    /** Leaving a world: every video stops. */
    public static void clear() {
        for (Slot s : SLOTS.values()) if (s.video != null) s.video.release();
        SLOTS.clear();
        WaterMediaVideo.QUEUE.pump();
    }
}
