package fr.lordfinn.craftmaster.regie;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork;
import fr.lordfinn.craftmaster.broadcast.Director;
import fr.lordfinn.craftmaster.show.Actor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the moderators' director in game (docs/wiki/RegieModos.md). A moderator opens it (key ;), the server
 * answers with a {@link ModoState} for their rights and sends it again, once a second at most, while it changes; it
 * closes when they close the screen or leave. The orders themselves go through the usual RunActions packet, checked
 * per action by {@code ActionExecutor}. Nothing here is shown to the viewers: only the moderator's own screen.
 * <pre>
 * client → DeskOpen(open)       open / close the director
 * client ← DeskState(json)      "" rights = refused (not a moderator, or paused)
 * </pre>
 */
public final class ModoDesk {

    private ModoDesk() {}

    public record DeskOpen(boolean open) implements CustomPacketPayload {
        public static final Type<DeskOpen> TYPE = new Type<>(CraftMaster.id("modo_desk_open"));
        public static final StreamCodec<FriendlyByteBuf, DeskOpen> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeBoolean(p.open), buf -> new DeskOpen(buf.readBoolean()));

        @Override
        public Type<DeskOpen> type() { return TYPE; }
    }

    public record DeskState(String json) implements CustomPacketPayload {
        public static final Type<DeskState> TYPE = new Type<>(CraftMaster.id("modo_desk_state"));
        public static final StreamCodec<FriendlyByteBuf, DeskState> CODEC = CustomPacketPayload.codec(
                (p, buf) -> RegiePayloads.writeJson(buf, p.json), buf -> new DeskState(RegiePayloads.readJson(buf, RegiePayloads.MAX_CONTEXT_JSON)));

        @Override
        public Type<DeskState> type() { return TYPE; }
    }

    /** Open directors and the last state sent to each. */
    private static final Map<UUID, String> OPEN = new HashMap<>();

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(DeskOpen.TYPE, DeskOpen.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DeskState.TYPE, DeskState.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(DeskOpen.TYPE, (p, ctx) -> {
            ServerPlayer player = ctx.player();
            if (!p.open()) {
                OPEN.remove(player.getUUID());
                return;
            }
            OPEN.put(player.getUUID(), "");
            push(player.level().getServer(), player, Director.build(player.level().getServer()), true);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> OPEN.remove(handler.getPlayer().getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (OPEN.isEmpty() || server.getTickCount() % 20 != 0) return;
            BroadcastNetwork.ShowState show = Director.build(server);
            for (UUID id : java.util.List.copyOf(OPEN.keySet())) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) OPEN.remove(id);
                else push(server, p, show, false);
            }
        });
    }

    private static void push(MinecraftServer server, ServerPlayer player, BroadcastNetwork.ShowState show, boolean force) {
        if (!ServerPlayNetworking.canSend(player, DeskState.TYPE)) return;
        Actor actor = Actor.of(player, "jeu");
        String json = ModoState.build(CraftMaster.data(), show, actor).toString();
        String previous = OPEN.put(player.getUUID(), json);
        if (force || !json.equals(previous)) ServerPlayNetworking.send(player, new DeskState(json));
        // No right any more (removed, paused): the screen closes on the client and stops asking
        if (!actor.any()) OPEN.remove(player.getUUID());
    }
}
