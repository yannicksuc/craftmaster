package fr.lordfinn.craftmaster.fx;

import fr.lordfinn.craftmaster.CraftMaster;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Payload of the screen effects: {@link Order} {@code craftmaster:screen_fx} (server → client).
 * Reference: docs/design/diffusion.md, "Interfaces livrées", screen effects.
 */
public final class FxNetwork {

    private FxNetwork() {}

    /** Layers: an effect forced on this player's own view; the effect of the broadcast view (games in broadcast mode). */
    public static final int LAYER_PLAYER = 0, LAYER_BROADCAST = 1;

    /**
     * An effect order.
     *
     * @param layer      {@link #LAYER_PLAYER} or {@link #LAYER_BROADCAST}
     * @param effect     combination ("fisheye+masque_rond"), "" = none
     * @param fadeTicks  fade in / out duration (0 = at once)
     * @param ticks      how long it lasts, 0 = until the next order
     */
    public record Order(int layer, String effect, int fadeTicks, int ticks) implements CustomPacketPayload {
        public static final Type<Order> TYPE = new Type<>(CraftMaster.id("screen_fx"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Order> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.layer);
                    buf.writeUtf(p.effect);
                    buf.writeVarInt(p.fadeTicks);
                    buf.writeVarInt(p.ticks);
                },
                buf -> new Order(buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<Order> type() { return TYPE; }
    }

    static void registerTypes() {
        PayloadTypeRegistry.clientboundPlay().register(Order.TYPE, Order.CODEC);
    }
}
