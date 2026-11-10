package fr.lordfinn.craftmaster.studio;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Live;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.show.Show;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Keeps the giant timers of every client in step with the round timer ({@link Show#timerRemaining}): a small
 * packet when what they show changes (once a second while it runs). Reads the show's state, never changes it.
 */
public final class StudioTimer {

    private StudioTimer() {}

    private static StudioNetwork.TimerState last;

    public static void tick(MinecraftServer server) {
        StudioNetwork.TimerState now = state(server);
        if (now.equals(last)) return;
        last = now;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p, now);
    }

    public static void onJoin(ServerPlayer player) {
        send(player, last != null ? last : state(player.level().getServer()));
    }

    public static void reset() {
        last = null;
    }

    private static void send(ServerPlayer p, StudioNetwork.TimerState state) {
        if (ServerPlayNetworking.canSend(p, StudioNetwork.TimerState.TYPE)) ServerPlayNetworking.send(p, state);
    }

    /** The current round (name, number, its set duration) and the timer, if one runs. */
    static StudioNetwork.TimerState state(MinecraftServer server) {
        String name = "";
        int number = 0, duration = 0;
        CraftMasterData data = CraftMaster.data();
        if (data != null) {
            Manche m = data.currentManche();
            Live.Current c = data.live().current;
            if (m != null && c != null) {
                name = m.name;
                duration = m.timer;
                Season s = data.season(c.season());
                if (s != null && s.parties.get(c.partie()) != null) number = s.parties.get(c.partie()).number(m);
            }
        }
        boolean running = Show.timerRunning();
        return new StudioNetwork.TimerState(running, running ? Show.timerRemaining(server) : -1, duration, name, number);
    }
}
