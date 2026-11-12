package fr.lordfinn.craftmaster.wardrobe;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Packets of the outfit screen. Every action is checked on the server (reach, organiser rights). */
public final class WardrobeNetwork {

    private WardrobeNetwork() {}

    /**
     * Server → client: the outfit screen of the wardrobe at {@code pos} ({@code refresh}: update it if it is open,
     * do not open it otherwise). {@code custom}: its outfits were chosen (otherwise every outfit of the game).
     */
    public record Open(BlockPos pos, boolean show, boolean custom, boolean admin, boolean inCostume, String wearing,
                       List<String> favourites, List<Outfit> outfits, boolean refresh) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(CraftMaster.id("wardrobe_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeBoolean(p.show);
                    buf.writeBoolean(p.custom);
                    buf.writeBoolean(p.admin);
                    buf.writeBoolean(p.inCostume);
                    buf.writeUtf(p.wearing, 2048);
                    buf.writeVarInt(p.favourites.size());
                    p.favourites.forEach(f -> buf.writeUtf(f, 2048));
                    buf.writeVarInt(p.outfits.size());
                    for (Outfit o : p.outfits) Outfit.STREAM_CODEC.encode(buf, o);
                    buf.writeBoolean(p.refresh);
                }, buf -> {
                    BlockPos pos = buf.readBlockPos();
                    boolean show = buf.readBoolean(), custom = buf.readBoolean(), admin = buf.readBoolean(), costume = buf.readBoolean();
                    String wearing = buf.readUtf(2048);
                    List<String> fav = new ArrayList<>();
                    for (int i = Math.min(buf.readVarInt(), 4096); i > 0; i--) fav.add(buf.readUtf(2048));
                    List<Outfit> list = new ArrayList<>();
                    for (int i = Math.min(buf.readVarInt(), 4096); i > 0; i--) list.add(Outfit.STREAM_CODEC.decode(buf));
                    return new Open(pos, show, custom, admin, costume, wearing, fav, list, buf.readBoolean());
                });

        @Override
        public Type<Open> type() { return TYPE; }
    }

    /**
     * Client → server: a button. {@code action}: wear, wear_piece ({@code text} = 0 head … 3 feet), back, favourite,
     * add, replace, rename, delete, up, down, mode, auto, catalogue_add, catalogue_remove, close.
     */
    public record Act(BlockPos pos, String action, int index, String text) implements CustomPacketPayload {
        public static final Type<Act> TYPE = new Type<>(CraftMaster.id("wardrobe_act"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Act::pos,
                ByteBufCodecs.stringUtf8(32), Act::action,
                ByteBufCodecs.VAR_INT, Act::index,
                ByteBufCodecs.stringUtf8(Outfit.MAX_NAME * 4), Act::text,
                Act::new);

        @Override
        public Type<Act> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Act.TYPE, Act.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Act.TYPE, (p, ctx) -> {
            ServerPlayer player = ctx.player();
            if (!(player.level().getBlockEntity(p.pos()) instanceof WardrobeBlockEntity w)) return;
            if (p.action().equals("close")) {
                w.closedBy(player);
                return;
            }
            if (!w.reachable(player)) return;
            Wardrobes.act(player, w, p.action(), p.index(), p.text());
        });
    }

    private static Open payload(ServerPlayer player, WardrobeBlockEntity w, boolean refresh) {
        WardrobeState st = Wardrobes.state(player);
        return new Open(w.getBlockPos(), w.show(), w.custom(), Permissions.isAdmin(player), st.inCostume(), st.wearing(),
                st.favourites(), List.copyOf(w.outfits()), refresh);
    }

    public static void sendOpen(ServerPlayer player, WardrobeBlockEntity w) {
        if (ServerPlayNetworking.canSend(player, Open.TYPE)) ServerPlayNetworking.send(player, payload(player, w, false));
    }

    public static void sendRefresh(ServerPlayer player, WardrobeBlockEntity w) {
        if (ServerPlayNetworking.canSend(player, Open.TYPE)) ServerPlayNetworking.send(player, payload(player, w, true));
    }
}
