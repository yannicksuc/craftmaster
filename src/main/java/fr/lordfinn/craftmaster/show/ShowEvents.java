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

        // ---- added by chantier E (TV direction); see docs/design/diffusion.md "Interfaces livrées"

        /** Live points given (after the data changed): {@code delta} may be negative; not published yet. */
        default void onScoreGiven(MinecraftServer server, int season, String player, int delta, String manche) {}

        /** A round was just started (titles, teleports and timer already done). */
        default void onMancheStarted(MinecraftServer server, int season, int number, String name) {}

        /** The staged reveal of the season's ranking starts. */
        default void onRevealStarted(MinecraftServer server, int season) {}

        /**
         * Generic "all eyes on this player" moment (the automatic direction cuts to them): fired with
         * {@link #spotlight}, e.g. by chantier D when a contestant opens their envelope ({@code reason = "envelope"}).
         */
        default void onSpotlight(MinecraftServer server, String player, String reason) {}
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

    public static void onScoreGiven(MinecraftServer server, int season, String player, int delta, String manche) {
        fire("de points", l -> l.onScoreGiven(server, season, player, delta, manche));
    }

    public static void onMancheStarted(MinecraftServer server, int season, int number, String name) {
        fire("de manche", l -> l.onMancheStarted(server, season, number, name));
    }

    public static void onRevealStarted(MinecraftServer server, int season) {
        fire("de révélation", l -> l.onRevealStarted(server, season));
    }

    /**
     * Puts a player in the spotlight: the automatic direction (if on) cuts to them with their name on screen. Any
     * part of the mod may call it (server thread); {@code reason} is free text ("envelope", "buzzer"…).
     */
    public static void spotlight(MinecraftServer server, String player, String reason) {
        fire("de projecteur", l -> l.onSpotlight(server, player, reason));
    }

    private static void fire(String what, java.util.function.Consumer<Listener> call) {
        for (Listener l : LISTENERS) {
            try {
                call.accept(l);
            } catch (RuntimeException e) {
                CraftMaster.LOG.error("[Show] Erreur dans un écouteur {}", what, e);
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
