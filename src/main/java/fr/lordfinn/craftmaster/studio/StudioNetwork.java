package fr.lordfinn.craftmaster.studio;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Packets of the studio set: the giant timer's state, the studio screen's settings screen. */
public final class StudioNetwork {

    private StudioNetwork() {}

    /**
     * Server → client: what the giant timers show. {@code remaining}: seconds left, -1 when no timer runs;
     * {@code duration}: the current round's set duration (0 = none); {@code manche} / {@code number}: the current
     * round ("" / 0 when none).
     */
    public record TimerState(boolean running, int remaining, int duration, String manche, int number) implements CustomPacketPayload {
        public static final Type<TimerState> TYPE = new Type<>(CraftMaster.id("studio_timer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TimerState> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeBoolean(p.running);
                    buf.writeVarInt(p.remaining + 1);
                    buf.writeVarInt(p.duration);
                    buf.writeUtf(p.manche);
                    buf.writeVarInt(p.number);
                },
                buf -> new TimerState(buf.readBoolean(), buf.readVarInt() - 1, buf.readVarInt(), buf.readUtf(), buf.readVarInt()));

        @Override
        public Type<TimerState> type() { return TYPE; }
    }

    /** Server → client: open the settings of a studio screen. */
    public record OpenScreen(int entityId) implements CustomPacketPayload {
        public static final Type<OpenScreen> TYPE = new Type<>(CraftMaster.id("studio_screen_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenScreen> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeVarInt(p.entityId), buf -> new OpenScreen(buf.readVarInt()));

        @Override
        public Type<OpenScreen> type() { return TYPE; }
    }

    /** Client → server: new settings of a studio screen (empty URL: back to the test card). */
    public record SaveScreen(int entityId, String url, int width, int height, boolean fit) implements CustomPacketPayload {
        public static final Type<SaveScreen> TYPE = new Type<>(CraftMaster.id("studio_screen_save"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SaveScreen> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.entityId);
                    buf.writeUtf(p.url, ImageRequest.MAX_URL_LENGTH);
                    buf.writeVarInt(p.width);
                    buf.writeVarInt(p.height);
                    buf.writeBoolean(p.fit);
                },
                buf -> new SaveScreen(buf.readVarInt(), buf.readUtf(ImageRequest.MAX_URL_LENGTH), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

        @Override
        public Type<SaveScreen> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TimerState.TYPE, TimerState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenScreen.TYPE, OpenScreen.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SaveScreen.TYPE, SaveScreen.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SaveScreen.TYPE, (p, ctx) -> save(ctx.player(), p));
    }

    public static void openScreenSettings(ServerPlayer player, ImageScreenEntity screen) {
        if (!ServerPlayNetworking.canSend(player, OpenScreen.TYPE)) {
            player.sendSystemMessage(Component.translatable("message.craftmaster.no_client_mod").withStyle(ChatFormatting.RED));
            return;
        }
        ServerPlayNetworking.send(player, new OpenScreen(screen.getId()));
    }

    private static void save(ServerPlayer player, SaveScreen p) {
        if (!Permissions.canShowImages(player)) return;
        if (!(player.level().getEntity(p.entityId()) instanceof ImageScreenEntity screen) || screen.distanceTo(player) > 64) return;
        String url = p.url().trim();
        if (!url.isEmpty()) {
            String error = ImageRequest.urlError(url);
            if (error != null) {
                player.sendSystemMessage(Component.literal(error).withStyle(ChatFormatting.RED));
                return;
            }
        }
        screen.setUrl(url);
        screen.setFit(p.fit());
        if (p.width() != screen.width() || p.height() != screen.height()) screen.resize(p.width(), p.height());
        player.sendOverlayMessage(Component.translatable("message.craftmaster.screen_saved").withStyle(ChatFormatting.GOLD));
    }
}
