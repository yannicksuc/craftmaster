package fr.lordfinn.craftmaster.broadcast;

import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Cues;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowCue;
import fr.lordfinn.craftmaster.show.Reveal;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The show's feed on the players' screens. Each player can show it in place of their own view (a key, client side)
 * and still play; during the big moments it is forced on everyone: the opening and closing credits, a round's
 * intro, the scores reveal, or by hand ({@code /cm tv flux forcer}). While forced, players stay still unless the
 * organisers allow moving ({@code /cm tv flux bouger on}).
 * <p>
 * Sent to the clients as a {@link Cues#FEED} cue whose title is {@code off}, {@code freeze} or {@code move}.
 */
public final class ShowFeed {

    private ShowFeed() {}

    /** Forced by hand ({@code /cm tv flux forcer}), until {@code /cm tv flux libre}. */
    private static boolean manual;
    /** Players may move and interact while the feed is forced. */
    private static boolean allowMove;
    private static String sent = "off";

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 5 != 0) return;
            String now = state();
            if (now.equals(sent)) return;
            sent = now;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p, now);
        });
        // A player joining during a forced moment gets it too
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> send(handler.getPlayer(), state())));
    }

    /** off, freeze (forced, standing still) or move (forced, free to move). */
    public static String state() {
        boolean forced = manual || !Credits.running().isEmpty() || MancheIntros.playing() || MancheIntros.launching() || Reveal.running();
        return !forced ? "off" : allowMove ? "move" : "freeze";
    }

    private static void send(ServerPlayer p, String state) {
        if (ServerPlayNetworking.canSend(p, ShowCue.TYPE)) ServerPlayNetworking.send(p, new ShowCue(Cues.FEED, state, "", "", 0));
    }

    public static void setManual(boolean on) {
        manual = on;
    }

    public static boolean manual() {
        return manual;
    }

    public static void setAllowMove(boolean on) {
        allowMove = on;
    }

    public static boolean allowMove() {
        return allowMove;
    }

    /** For /cm tv flux: where it stands. */
    public static String describe() {
        String s = state();
        return "Flux de l'émission : " + (s.equals("off") ? "libre (chacun l'affiche avec sa touche)" : "imposé à tous"
                + (manual ? " (à la main : /cm tv flux libre)" : " (générique, intro ou révélation en cours)"))
                + " · pendant les moments imposés, les joueurs " + (allowMove ? "peuvent bouger" : "restent immobiles") + ".";
    }

    static void reset(MinecraftServer server) {
        manual = false;
        sent = "off";
    }
}
