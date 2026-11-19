package fr.lordfinn.craftmaster.trial.kit;

import fr.lordfinn.craftmaster.CraftMaster;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Nothing shows before it is ready (« lancer l'épreuve », the round's launch after its intro):
 * <ul>
 * <li><b>server</b>: the places the trial will show (copies, starts, teleport points, the intro's shots) are held
 * loaded by tickets of our own (not saved with the world) until the players are there; {@link #preload} waits until
 * their blocks and entities are loaded and ticking;</li>
 * <li><b>clients</b>: {@link #check} asks each game with the mod whether it has received the chunks around its view
 * and built their render sections ({@code ReadyCheck}); the picture opens when every game answered ({@code
 * ReadyReply}), or after a timeout, the organisers being told who was not ready. Games without the mod get the
 * minimum wait.</li>
 * </ul>
 */
public final class LaunchGate {

    private LaunchGate() {}

    /** Loads and simulates (entities ticking), no timeout, not saved with the world. */
    public static final TicketType TICKET = new TicketType(TicketType.NO_TIMEOUT,
            TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
    /** Ticket radius: 2 makes the chunk itself entity-ticking. */
    private static final int RADIUS = 2;

    // ---------------------------------------------------------------- packets

    /** Server → client: tell me when the chunks around your view are received and drawn. */
    public record ReadyCheck(int id) implements CustomPacketPayload {
        public static final Type<ReadyCheck> TYPE = new Type<>(CraftMaster.id("ready_check"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReadyCheck> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeVarInt(p.id), buf -> new ReadyCheck(buf.readVarInt()));

        @Override
        public Type<ReadyCheck> type() { return TYPE; }
    }

    /** Client → server: ready ({@code id} of the check). */
    public record ReadyReply(int id) implements CustomPacketPayload {
        public static final Type<ReadyReply> TYPE = new Type<>(CraftMaster.id("ready_reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReadyReply> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeVarInt(p.id), buf -> new ReadyReply(buf.readVarInt()));

        @Override
        public Type<ReadyReply> type() { return TYPE; }
    }

    // ---------------------------------------------------------------- state

    /** Chunks held by our tickets, per dimension. */
    private static final Map<ResourceKey<Level>, Set<ChunkPos>> HELD = new HashMap<>();

    private record Preload(Map<ServerLevel, Set<ChunkPos>> chunks, long deadline, Runnable ready, Runnable late) {}

    private static Preload preload;

    private record Wait(int id, Set<UUID> waiting, Map<UUID, String> names, long minAt, long maxAt, String what, Runnable then) {}

    private static Wait wait;
    private static int nextId = 1;

    public static void register() {
        Registry.register(BuiltInRegistries.TICKET_TYPE, CraftMaster.id("epreuve"), TICKET);
        PayloadTypeRegistry.clientboundPlay().register(ReadyCheck.TYPE, ReadyCheck.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ReadyReply.TYPE, ReadyReply.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ReadyReply.TYPE, (p, ctx) -> {
            Wait w = wait;
            if (w != null && w.id == p.id()) w.waiting.remove(ctx.player().getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(LaunchGate::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            HELD.clear();
            preload = null;
            wait = null;
        });
    }

    // ---------------------------------------------------------------- server: tickets

    /** Holds these chunks loaded (on top of what is already held). */
    public static void hold(Map<ServerLevel, Set<ChunkPos>> chunks) {
        for (Map.Entry<ServerLevel, Set<ChunkPos>> e : chunks.entrySet()) {
            Set<ChunkPos> held = HELD.computeIfAbsent(e.getKey().dimension(), k -> new HashSet<>());
            for (ChunkPos c : e.getValue())
                if (held.add(c)) e.getKey().getChunkSource().addTicketWithRadius(TICKET, c, RADIUS);
        }
    }

    /** Lets every held chunk go (the players are there, or the trial is over). */
    public static void release(MinecraftServer server) {
        for (Map.Entry<ResourceKey<Level>, Set<ChunkPos>> e : HELD.entrySet()) {
            ServerLevel level = server.getLevel(e.getKey());
            if (level != null) for (ChunkPos c : e.getValue()) level.getChunkSource().removeTicketWithRadius(TICKET, c, RADIUS);
        }
        HELD.clear();
    }

    public static int held() {
        int n = 0;
        for (Set<ChunkPos> s : HELD.values()) n += s.size();
        return n;
    }

    /**
     * Holds the chunks and runs {@code ready} once their blocks and entities are all loaded and ticking, or
     * {@code late} after {@code timeoutTicks}.
     */
    public static void preload(MinecraftServer server, Map<ServerLevel, Set<ChunkPos>> chunks, int timeoutTicks, Runnable ready, Runnable late) {
        hold(chunks);
        preload = new Preload(chunks, server.getTickCount() + timeoutTicks, ready, late);
    }

    /** The chunks not loaded yet (for the organisers), "" when all are. */
    private static String missing(Map<ServerLevel, Set<ChunkPos>> chunks) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<ServerLevel, Set<ChunkPos>> e : chunks.entrySet()) {
            int n = 0;
            for (ChunkPos c : e.getValue()) if (!e.getKey().areEntitiesActuallyLoadedAndTicking(c)) n++;
            if (n > 0) sb.append(sb.isEmpty() ? "" : ", ").append(n).append(" chunk(s) de ").append(e.getKey().dimension().identifier().getPath());
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- clients

    /**
     * Asks the games of {@code who} (those with the mod) whether their view is loaded and drawn; runs {@code then}
     * when all said so (not before {@code minTicks}) or after {@code maxTicks}, telling the organisers who was not
     * ready ({@code what}: what was waited for).
     */
    public static void check(MinecraftServer server, Collection<ServerPlayer> who, int minTicks, int maxTicks, String what, Runnable then) {
        int id = nextId++;
        Set<UUID> waiting = new LinkedHashSet<>();
        Map<UUID, String> names = new HashMap<>();
        for (ServerPlayer p : who) {
            if (!ServerPlayNetworking.canSend(p, ReadyCheck.TYPE)) continue;
            ServerPlayNetworking.send(p, new ReadyCheck(id));
            waiting.add(p.getUUID());
            names.put(p.getUUID(), p.getGameProfile().name());
        }
        long now = server.getTickCount();
        wait = new Wait(id, waiting, names, now + minTicks, now + Math.max(minTicks, maxTicks), what, then);
    }

    /** Everyone online: candidates, organisers, broadcast games. */
    public static List<ServerPlayer> everyone(MinecraftServer server) {
        return new ArrayList<>(server.getPlayerList().getPlayers());
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        Preload p = preload;
        if (p != null && now % 5 == 0) {
            String missing = missing(p.chunks);
            if (missing.isEmpty()) {
                preload = null;
                p.ready.run();
            } else if (now >= p.deadline) {
                preload = null;
                CraftMaster.LOG.warn("[Épreuve] zones pas chargées à temps : {}", missing);
                Finish.tellOrganisers(server, null, Component.literal("⚠ Zones pas chargées à temps : " + missing + ".")
                        .withStyle(ChatFormatting.RED), null);
                p.late.run();
            }
        }
        Wait w = wait;
        if (w == null) return;
        // Someone who left is not waited for
        w.waiting.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        if (now < w.minAt || (!w.waiting.isEmpty() && now < w.maxAt)) return;
        wait = null;
        if (!w.waiting.isEmpty()) {
            List<String> late = new ArrayList<>();
            for (UUID id : w.waiting) late.add(w.names.getOrDefault(id, id.toString()));
            CraftMaster.LOG.warn("[Épreuve] {} : pas prêts à temps : {}", w.what, late);
            Finish.tellOrganisers(server, null, Component.literal("⚠ " + w.what + " : pas prêt(s) à temps : " + String.join(", ", late) + ".")
                    .withStyle(ChatFormatting.GOLD), null);
        }
        w.then.run();
    }

    /** Is a launch waiting for loads or games? */
    public static boolean busy() {
        return preload != null || wait != null;
    }

    /** Drops what is waiting (a new launch, a stop). */
    public static void cancel() {
        preload = null;
        wait = null;
    }
}
