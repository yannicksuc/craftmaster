package fr.lordfinn.craftmaster.client.broadcast;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * This player's rights on the show's picture, as the server tells them ({@code Cues.RIGHTS}): an organiser switches
 * his own broadcast mode (F8); the show's feed (F7) is for the organisers and for those who may watch the trial
 * (done, or not contestants, when it is allowed). Until the server has said, an organiser key does nothing.
 */
public final class Rights {

    private Rights() {}

    private static boolean known, admin, feed;

    static {
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> known = admin = feed = false);
    }

    static void set(boolean isAdmin, boolean mayFeed) {
        known = true;
        admin = isAdmin;
        feed = mayFeed;
    }

    public static boolean known() {
        return known;
    }

    public static boolean admin() {
        return admin;
    }

    public static boolean feed() {
        return feed;
    }
}
