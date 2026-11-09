package fr.lordfinn.craftmaster.regie;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.show.ShowContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the voice control: hands the show context to admins, runs the actions they send and answers
 * with what was done. The understanding itself happens on the admin's PC (local service), never here.
 * Every packet is checked against {@link Permissions#isAdmin}: a modified client gets nothing.
 */
public final class RegieServer {

    private RegieServer() {}

    /** Last admin flag sent to each player, to tell them again only when it changes (op / deop, LuckPerms). */
    private static final Map<UUID, Boolean> SENT = new HashMap<>();

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(RegiePayloads.Status.TYPE, RegiePayloads.Status.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RegiePayloads.Context.TYPE, RegiePayloads.Context.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RegiePayloads.Result.TYPE, RegiePayloads.Result.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RegiePayloads.ContextRequest.TYPE, RegiePayloads.ContextRequest.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RegiePayloads.Actions.TYPE, RegiePayloads.Actions.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RegiePayloads.ContextRequest.TYPE, (p, ctx) -> onContextRequest(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(RegiePayloads.Actions.TYPE, (p, ctx) -> onActions(ctx.player(), p));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendStatus(handler.getPlayer(), true));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.getPlayer().getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 40 == 0) server.getPlayerList().getPlayers().forEach(p -> sendStatus(p, false));
        });
    }

    private static void sendStatus(ServerPlayer player, boolean force) {
        if (!ServerPlayNetworking.canSend(player, RegiePayloads.Status.TYPE)) return;
        boolean admin = Permissions.isAdmin(player);
        Boolean previous = SENT.put(player.getUUID(), admin);
        if (force || previous == null || previous != admin) ServerPlayNetworking.send(player, new RegiePayloads.Status(admin));
    }

    private static void onContextRequest(ServerPlayer player, RegiePayloads.ContextRequest p) {
        if (!Permissions.isAdmin(player)) {
            sendStatus(player, true);
            ServerPlayNetworking.send(player, new RegiePayloads.Context(p.id(), false, "{}"));
            return;
        }
        String json;
        try {
            json = RegieJson.write(ShowContext.build(player.level().getServer(), player));
        } catch (RuntimeException e) {
            CraftMaster.LOG.error("Voice control: building the show context failed", e);
            json = "{}";
        }
        if (json.length() > RegiePayloads.MAX_CONTEXT_JSON / 2) CraftMaster.LOG.warn("Voice control: large show context ({} chars)", json.length());
        ServerPlayNetworking.send(player, new RegiePayloads.Context(p.id(), true, json));
    }

    private static void onActions(ServerPlayer player, RegiePayloads.Actions p) {
        if (!Permissions.isAdmin(player)) {
            ServerPlayNetworking.send(player, new RegiePayloads.Result(p.id(), false, List.of(), "not_admin"));
            return;
        }
        List<Map<String, Object>> actions;
        try {
            actions = RegieJson.readActions(p.json());
        } catch (IllegalArgumentException e) {
            ServerPlayNetworking.send(player, new RegiePayloads.Result(p.id(), false, List.of(e.getMessage()), "bad_actions"));
            return;
        }
        CraftMaster.LOG.info("Voice control: {} said \"{}\" -> {}", player.getPlainTextName(), p.transcript(), p.json());
        ActionExecutor.Result r;
        try {
            r = ActionExecutor.execute(player, actions);
        } catch (RuntimeException e) {
            CraftMaster.LOG.error("Voice control: executing {} failed", p.json(), e);
            r = new ActionExecutor.Result(List.of("Erreur interne : " + e), false);
        }
        ServerPlayNetworking.send(player, new RegiePayloads.Result(p.id(), r.ok(), r.lines(), ""));
    }
}
