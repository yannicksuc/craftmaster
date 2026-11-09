package fr.lordfinn.craftmaster.show;

import fr.lordfinn.craftmaster.CraftMaster;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hooks for other parts of the mod (the hanging portraits) to follow the show. Listeners run on the server thread,
 * after the data changed; an exception in one of them is logged and does not stop the others.
 */
public final class ShowEvents {

    public interface Listener {
        /** The season's scores were just published (the published ranking may have changed). */
        default void onPublished(MinecraftServer server, int season) {}

        /** During the staged reveal: the player at {@code rank} (1-based, published ranking) is being revealed. */
        default void onRevealStep(MinecraftServer server, int season, int rank) {}
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private ShowEvents() {}

    public static void register(Listener listener) {
        LISTENERS.add(listener);
    }

    public static void unregister(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static void onPublished(MinecraftServer server, int season) {
        for (Listener l : LISTENERS) {
            try {
                l.onPublished(server, season);
            } catch (RuntimeException e) {
                CraftMaster.LOG.error("[Show] Erreur dans un écouteur de publication", e);
            }
        }
    }

    public static void onRevealStep(MinecraftServer server, int season, int rank) {
        for (Listener l : LISTENERS) {
            try {
                l.onRevealStep(server, season, rank);
            } catch (RuntimeException e) {
                CraftMaster.LOG.error("[Show] Erreur dans un écouteur de révélation", e);
            }
        }
    }
}
