package fr.lordfinn.craftmaster.replay;

import fr.lordfinn.craftmaster.CraftMaster;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Replay viewer ↔ CraftMaster client. Server → the game watching a replay (Server Replay's viewer): where the
 * playback is ({@link ReplayStatus}), several times a second. The other way, the client's playback bar sends
 * commands the viewer lets through while a replay plays: {@code /replay view cm …} ({@link ReplayViewing}).
 */
public final class ReplayNetwork {

    private ReplayNetwork() {}

    /** A marker on the replay's timeline. */
    public record Marker(long ms, String name, int color) {}

    /**
     * Where the replay is.
     *
     * @param title    "saison 1, Partie 1, manche 1 « Le Pont »" (or the file's folder)
     * @param view     the file being played: "zone" or a player's name
     * @param views    the other files of the same recording that can be switched to ("zone", players)
     * @param progress position in the file (ms)
     * @param duration length of the file (ms)
     * @param tick0    the server's tick at the file's time 0 (to replay the timer exactly), -1 if unknown
     * @param markers  the file's markers, or null when unchanged since the last status (sent now and then)
     * @param reopening the file closes and opens again now (a switch, a seek back): a few seconds with no status
     */
    public record ReplayStatus(String title, String view, List<String> views, long progress, long duration, boolean paused,
                               float speed, double tick0, List<Marker> markers, boolean reopening) implements CustomPacketPayload {

        public static final Type<ReplayStatus> TYPE = new Type<>(CraftMaster.id("replay_status"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReplayStatus> CODEC = CustomPacketPayload.codec(ReplayStatus::write, ReplayStatus::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(title);
            buf.writeUtf(view);
            buf.writeVarInt(views.size());
            for (String v : views) buf.writeUtf(v);
            buf.writeVarLong(progress);
            buf.writeVarLong(duration);
            buf.writeBoolean(paused);
            buf.writeFloat(speed);
            buf.writeDouble(tick0);
            buf.writeBoolean(reopening);
            buf.writeBoolean(markers != null);
            if (markers != null) {
                buf.writeVarInt(markers.size());
                for (Marker m : markers) {
                    buf.writeVarLong(m.ms());
                    buf.writeUtf(m.name());
                    buf.writeInt(m.color());
                }
            }
        }

        private static ReplayStatus read(RegistryFriendlyByteBuf buf) {
            String title = buf.readUtf(), view = buf.readUtf();
            List<String> views = new ArrayList<>();
            for (int i = buf.readVarInt(); i > 0; i--) views.add(buf.readUtf());
            long progress = buf.readVarLong(), duration = buf.readVarLong();
            boolean paused = buf.readBoolean();
            float speed = buf.readFloat();
            double tick0 = buf.readDouble();
            boolean reopening = buf.readBoolean();
            List<Marker> markers = null;
            if (buf.readBoolean()) {
                markers = new ArrayList<>();
                for (int i = buf.readVarInt(); i > 0; i--) markers.add(new Marker(buf.readVarLong(), buf.readUtf(), buf.readInt()));
            }
            return new ReplayStatus(title, view, views, progress, duration, paused, speed, tick0, markers, reopening);
        }

        @Override
        public Type<ReplayStatus> type() { return TYPE; }
    }

    public static void registerTypes() {
        PayloadTypeRegistry.clientboundPlay().register(ReplayStatus.TYPE, ReplayStatus.CODEC);
    }
}
