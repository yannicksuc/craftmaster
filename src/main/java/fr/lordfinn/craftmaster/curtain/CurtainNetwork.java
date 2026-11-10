package fr.lordfinn.craftmaster.curtain;

import fr.lordfinn.craftmaster.CraftMaster;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Packets of the curtains. */
public final class CurtainNetwork {

    private CurtainNetwork() {}

    /**
     * Server → clients watching the curtain: the curtain containing {@code pos} starts sliding open (or closed)
     * and takes {@code ticks} to get there. The client finds the curtain itself (same blocks) and draws the fabric
     * moving; the blocks' own state follows column by column on the server.
     */
    public record Slide(BlockPos pos, boolean open, int ticks) implements CustomPacketPayload {
        public static final Type<Slide> TYPE = new Type<>(CraftMaster.id("curtain_slide"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Slide> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeBoolean(p.open);
                    buf.writeVarInt(p.ticks);
                }, buf -> new Slide(buf.readBlockPos(), buf.readBoolean(), buf.readVarInt()));

        @Override
        public Type<Slide> type() { return TYPE; }
    }
}
