package fr.lordfinn.craftmaster.trial;

import fr.lordfinn.craftmaster.command.TrialCommands;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Chantier H, "épreuves": task props, buzzers, judging tools (results sheet, stopwatches, tape, counter, scales,
 * zones, red card) and creative objects (drawing boards, plinth, costume trunk, free naming). One call from
 * {@code CraftMaster#onInitialize} wires it all.
 */
public final class Trials {

    private Trials() {}

    public static void register() {
        TrialContent.register();
        TrialNetwork.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> TrialCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> TrialZones.load());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TrialProps.clear(server);
            Judging.reset();
            Buzzers.LOCK.reset();
            TrialLog.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Judging.tick(server);
            TrialProps.tick(server);
            TrialZones.tick(server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Judging.onJoin(handler.getPlayer()));
    }
}
