package fr.lordfinn.craftmaster.broadcast;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Payloads of the TV direction (chantier E). Reference: docs/design/diffusion.md, "Interfaces livrées".
 * <ul>
 *   <li>{@link ShowState} (server → every client with the mod): the show's state, re-sent whenever it changes;</li>
 *   <li>{@link ShowCue} (server → clients): a one-off overlay event (points, lower third, title card, credits…);</li>
 *   <li>{@link BroadcastMode} (client → server): "my game is (no longer) in broadcast mode";</li>
 *   <li>{@link CameraRequest} (client → server): an action of the show schema from the broadcast keys (1–9,
 *       wheel), run if the player may drive the cameras.</li>
 * </ul>
 */
public final class BroadcastNetwork {

    private BroadcastNetwork() {}

    /** Shot kinds. NONE: no camera (the broadcast shows the player's own view). */
    public static final int SHOT_NONE = 0, SHOT_CAMERA = 1, SHOT_FOLLOW = 2;
    /** Transitions: straight cut, dip through black, cross-fade from the last frame. */
    public static final int CUT = 0, FADE = 1, CROSSFADE = 2;
    /** Score strip: shown for a while after a publication, always, never. */
    public static final int SCORES_AUTO = 0, SCORES_ON = 1, SCORES_OFF = 2;

    /** A contestant of the active season (cast order) with the PUBLISHED total and rank (0 = not ranked yet). */
    public record Contestant(String name, int points, int rank, boolean online) {}

    /**
     * The shot on air. {@code serial} changes at every new shot (the clients play the transition then);
     * {@code camera} is the camera number for {@link #SHOT_CAMERA}, {@code player} the followed player for
     * {@link #SHOT_FOLLOW}.
     */
    public record Shot(int kind, int camera, String player, int transition, int serial) {
        public static final Shot NONE = new Shot(SHOT_NONE, 0, "", CUT, 0);
    }

    /**
     * The show's state.
     *
     * @param serverTick   server tick when sent (the clients count the timer down from it)
     * @param season       active season (0 = none), its name and whether its ranking is still hidden
     * @param partie       active game number (0 = none) and name
     * @param mancheId     current round ("" = none), its number in the game and name
     * @param timerEnd     server tick when the round timer ends, -1 when none runs; {@code timerTotal} in ticks
     * @param contestants  cast of the active season (archived players left out), published scores only
     * @param cameras      every studio camera
     * @param shot         the shot on air
     * @param auto         automatic direction on
     * @param letterbox    cinema bars on the broadcast
     * @param scores       score strip mode ({@link #SCORES_AUTO}…)
     * @param master       the show's host (name shown in the credits)
     * @param sequence     opening / closing credits running ("" = none, else "intro" / "outro")
     */
    public record ShowState(long serverTick, int season, String seasonName, boolean seasonHidden, int partie, String partieName,
                            String mancheId, int mancheNumber, String mancheName, String timerName, long timerEnd, int timerTotal,
                            List<Contestant> contestants, List<CameraSpot> cameras, Shot shot, boolean auto, boolean letterbox,
                            int scores, String master, String sequence) implements CustomPacketPayload {

        public static final Type<ShowState> TYPE = new Type<>(CraftMaster.id("show_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShowState> CODEC = CustomPacketPayload.codec(ShowState::write, ShowState::read);

        public static final ShowState EMPTY = new ShowState(0, 0, "", false, 0, "", "", 0, "", "", -1, 0, List.of(), List.of(),
                Shot.NONE, false, false, SCORES_AUTO, "", "");

        /** Same state apart from the send time (to know whether to send it again). */
        public ShowState unclocked() {
            return new ShowState(0, season, seasonName, seasonHidden, partie, partieName, mancheId, mancheNumber, mancheName,
                    timerName, timerEnd, timerTotal, contestants, cameras, shot, auto, letterbox, scores, master, sequence);
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarLong(serverTick);
            buf.writeVarInt(season);
            buf.writeUtf(seasonName);
            buf.writeBoolean(seasonHidden);
            buf.writeVarInt(partie);
            buf.writeUtf(partieName);
            buf.writeUtf(mancheId);
            buf.writeVarInt(mancheNumber);
            buf.writeUtf(mancheName);
            buf.writeUtf(timerName);
            buf.writeLong(timerEnd);
            buf.writeVarInt(timerTotal);
            buf.writeVarInt(contestants.size());
            for (Contestant c : contestants) {
                buf.writeUtf(c.name());
                buf.writeVarInt(c.points());
                buf.writeVarInt(c.rank());
                buf.writeBoolean(c.online());
            }
            buf.writeVarInt(cameras.size());
            for (CameraSpot s : cameras) s.write(buf);
            buf.writeVarInt(shot.kind());
            buf.writeVarInt(shot.camera());
            buf.writeUtf(shot.player());
            buf.writeVarInt(shot.transition());
            buf.writeVarInt(shot.serial());
            buf.writeBoolean(auto);
            buf.writeBoolean(letterbox);
            buf.writeVarInt(scores);
            buf.writeUtf(master);
            buf.writeUtf(sequence);
        }

        private static ShowState read(RegistryFriendlyByteBuf buf) {
            long tick = buf.readVarLong();
            int season = buf.readVarInt();
            String seasonName = buf.readUtf();
            boolean hidden = buf.readBoolean();
            int partie = buf.readVarInt();
            String partieName = buf.readUtf();
            String mancheId = buf.readUtf();
            int mancheNumber = buf.readVarInt();
            String mancheName = buf.readUtf();
            String timerName = buf.readUtf();
            long timerEnd = buf.readLong();
            int timerTotal = buf.readVarInt();
            List<Contestant> contestants = new ArrayList<>();
            for (int i = buf.readVarInt(); i > 0; i--)
                contestants.add(new Contestant(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
            List<CameraSpot> cameras = new ArrayList<>();
            for (int i = buf.readVarInt(); i > 0; i--) cameras.add(CameraSpot.read(buf));
            Shot shot = new Shot(buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readVarInt());
            return new ShowState(tick, season, seasonName, hidden, partie, partieName, mancheId, mancheNumber, mancheName,
                    timerName, timerEnd, timerTotal, contestants, cameras, shot, buf.readBoolean(), buf.readBoolean(),
                    buf.readVarInt(), buf.readUtf(), buf.readUtf());
        }

        @Override
        public Type<ShowState> type() { return TYPE; }
    }

    /**
     * A one-off event for the overlay. Kinds ({@code Cues.*}): {@code points} (player, title "+3"), {@code lower_third}
     * (title, subtitle, player), {@code scores} (show the score strip), {@code task} (show the task card),
     * {@code title_card} (big centred card: title, subtitle), {@code contestant} (opening credits: player, title =
     * "1/4"), {@code credits} (closing credits roll), {@code theme} (play the theme; title "stop" stops it),
     * {@code clear} (removes every card), {@code broadcast} (title "on" / "off": switches that game's broadcast mode,
     * sent to one player by {@code /tv broadcast}).
     */
    public record ShowCue(String kind, String title, String subtitle, String player, int ticks) implements CustomPacketPayload {
        public static final Type<ShowCue> TYPE = new Type<>(CraftMaster.id("show_cue"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShowCue> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeUtf(p.kind);
                    buf.writeUtf(p.title);
                    buf.writeUtf(p.subtitle);
                    buf.writeUtf(p.player);
                    buf.writeVarInt(p.ticks);
                },
                buf -> new ShowCue(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt()));

        @Override
        public Type<ShowCue> type() { return TYPE; }
    }

    public static final class Cues {
        public static final String POINTS = "points", LOWER_THIRD = "lower_third", SCORES = "scores", TASK = "task",
                TITLE_CARD = "title_card", CONTESTANT = "contestant", CREDITS = "credits", THEME = "theme", CLEAR = "clear",
                BROADCAST = "broadcast";

        private Cues() {}
    }

    /** Client → server: the broadcast mode was switched on or off on this game. */
    public record BroadcastMode(boolean on) implements CustomPacketPayload {
        public static final Type<BroadcastMode> TYPE = new Type<>(CraftMaster.id("broadcast_mode"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BroadcastMode> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeBoolean(p.on), buf -> new BroadcastMode(buf.readBoolean()));

        @Override
        public Type<BroadcastMode> type() { return TYPE; }
    }

    /** Client → server: one show action as JSON (only {@code camera.*}, {@code broadcast.*} and {@code show.*}). */
    public record CameraRequest(String json) implements CustomPacketPayload {
        public static final Type<CameraRequest> TYPE = new Type<>(CraftMaster.id("camera_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CameraRequest> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeUtf(p.json), buf -> new CameraRequest(buf.readUtf()));

        @Override
        public Type<CameraRequest> type() { return TYPE; }
    }

    static void registerTypes() {
        PayloadTypeRegistry.clientboundPlay().register(ShowState.TYPE, ShowState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ShowCue.TYPE, ShowCue.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BroadcastMode.TYPE, BroadcastMode.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CameraRequest.TYPE, CameraRequest.CODEC);
    }
}
