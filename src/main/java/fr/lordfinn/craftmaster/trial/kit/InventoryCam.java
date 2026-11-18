package fr.lordfinn.craftmaster.trial.kit;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.broadcast.Director;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The inventory camera of the show: while a player has their inventory open (or any screen showing it), the
 * broadcast games see it live — the items, the slot under their mouse, the item they carry on the cursor and where
 * the cursor is — and draw it in a corner of the picture when that player is on air.
 * <p>
 * The player's own game says when the screen opens and closes, the slot hovered and the cursor's place relative to
 * the inventory's grid ({@link Screen}); the server adds what it knows for sure (the items, the carried stack) and
 * sends {@link View} to the broadcast games and the organisers, only when something changed.
 */
public final class InventoryCam {

    private InventoryCam() {}

    /** Player → game: the inventory screen. {@code x, y}: the mouse from the top-left slot of the backpack, GUI pixels. */
    public record Screen(boolean open, int hovered, int x, int y) implements CustomPacketPayload {
        public static final Type<Screen> TYPE = new Type<>(CraftMaster.id("inventory_screen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Screen> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeBoolean(p.open);
                    buf.writeVarInt(p.hovered + 1);
                    buf.writeShort(p.x);
                    buf.writeShort(p.y);
                },
                buf -> new Screen(buf.readBoolean(), buf.readVarInt() - 1, buf.readShort(), buf.readShort()));

        @Override
        public Type<Screen> type() {
            return TYPE;
        }
    }

    /** Server → broadcast: a player's inventory as they see it now (36 slots: hot bar 0-8, backpack 9-35). */
    public record View(String player, boolean open, int hovered, int x, int y, List<ItemStack> slots, ItemStack carried)
            implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(CraftMaster.id("inventory_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeUtf(p.player);
                    buf.writeBoolean(p.open);
                    buf.writeVarInt(p.hovered + 1);
                    buf.writeShort(p.x);
                    buf.writeShort(p.y);
                    buf.writeVarInt(p.slots.size());
                    for (ItemStack s : p.slots) ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s);
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.carried);
                },
                buf -> {
                    String player = buf.readUtf();
                    boolean open = buf.readBoolean();
                    int hovered = buf.readVarInt() - 1;
                    int x = buf.readShort(), y = buf.readShort();
                    int n = Math.min(64, buf.readVarInt());
                    List<ItemStack> slots = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) slots.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                    return new View(player, open, hovered, x, y, slots, ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                });

        @Override
        public Type<View> type() {
            return TYPE;
        }
    }

    private static final Map<UUID, Screen> SCREENS = new HashMap<>();
    private static final Map<UUID, View> SENT = new HashMap<>();

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Screen.TYPE, Screen.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(View.TYPE, View.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Screen.TYPE, (payload, ctx) -> {
            int hovered = payload.hovered() < -1 || payload.hovered() > 35 ? -1 : payload.hovered();
            int x = Math.max(-200, Math.min(400, payload.x())), y = Math.max(-200, Math.min(400, payload.y()));
            SCREENS.put(ctx.player().getUUID(), new Screen(payload.open(), hovered, x, y));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            if (SCREENS.remove(id) != null && SENT.containsKey(id)) {
                View last = SENT.remove(id);
                send(server, new View(last.player(), false, -1, 0, 0, last.slots(), ItemStack.EMPTY));
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(InventoryCam::tick);
    }

    private static void tick(MinecraftServer server) {
        if (SCREENS.isEmpty()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Screen s = SCREENS.get(p.getUUID());
            if (s == null) continue;
            View last = SENT.get(p.getUUID());
            if (!s.open() && (last == null || !last.open())) continue;
            List<ItemStack> slots = new ArrayList<>(36);
            for (int i = 0; i < 36; i++) slots.add(p.getInventory().getItem(i).copy());
            ItemStack carried = s.open() ? p.containerMenu.getCarried().copy() : ItemStack.EMPTY;
            View v = new View(Arenas.playerName(p), s.open(), s.hovered(), s.x(), s.y(), slots, carried);
            if (last != null && same(last, v)) continue;
            SENT.put(p.getUUID(), v);
            send(server, v);
            if (!s.open()) SCREENS.remove(p.getUUID());
        }
    }

    private static boolean same(View a, View b) {
        if (a.open() != b.open() || a.hovered() != b.hovered() || a.x() != b.x() || a.y() != b.y()) return false;
        if (!ItemStack.matches(a.carried(), b.carried())) return false;
        for (int i = 0; i < a.slots().size(); i++) if (!ItemStack.matches(a.slots().get(i), b.slots().get(i))) return false;
        return true;
    }

    /** To the broadcast games and the organisers (their show feed), never to the other contestants. */
    private static void send(MinecraftServer server, View v) {
        Set<UUID> to = new HashSet<>(Director.broadcasters());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (Permissions.isAdmin(p)) to.add(p.getUUID());
        for (UUID id : to) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && ServerPlayNetworking.canSend(p, View.TYPE)) ServerPlayNetworking.send(p, v);
        }
    }
}
