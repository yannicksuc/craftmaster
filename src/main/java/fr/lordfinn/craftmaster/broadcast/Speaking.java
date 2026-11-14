package fr.lordfinn.craftmaster.broadcast;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is speaking in the voice chat, fed by {@code compat.VoiceActivity} (Simple Voice Chat, optional): the time of
 * each player's last voice packet. Written from the voice chat's thread, read by the server tick.
 */
public final class Speaking {

    private Speaking() {}

    /** Heard within this long counts as speaking now (voice packets come every 20 ms while one talks). */
    public static final long NOW_MS = 220;

    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();
    private static volatile boolean available;

    /** The voice chat is there (its plugin started). */
    public static boolean available() {
        return available;
    }

    public static void setAvailable() {
        available = true;
    }

    public static void heard(UUID player) {
        LAST.put(player, System.currentTimeMillis());
    }

    public static boolean talking(UUID player, long nowMs) {
        Long t = LAST.get(player);
        return t != null && nowMs - t <= NOW_MS;
    }

    public static void forget(UUID player) {
        LAST.remove(player);
    }
}
