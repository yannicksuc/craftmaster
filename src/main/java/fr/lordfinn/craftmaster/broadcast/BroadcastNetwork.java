package fr.lordfinn.craftmaster.broadcast;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.camera.CameraPath;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
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
 *   <li>{@link FreeCamera} (server → broadcast games): where the free camera is now ({@link #SHOT_FREE});</li>
 *   <li>{@link BroadcastMode} (client → server): "my game is (no longer) in broadcast mode";</li>
 *   <li>{@link CameraRequest} (client → server): an action of the show schema from the broadcast keys (1–9,
 *       wheel), run if the player may drive the cameras.</li>
 * </ul>
 */
public final class BroadcastNetwork {

    private BroadcastNetwork() {}

    /**
     * Shot kinds. NONE: no camera (the broadcast shows the player's own view); CAMERA: a placed camera; FOLLOW: the
     * smoothed shot that follows a player; PATH: a recorded travelling; MOSAIC: split screen of several cameras
     * (2–4 tiles); POV: through a player's eyes; FREE: a camera the server moves freely ({@link FreeCamera}).
     */
    public static final int SHOT_NONE = 0, SHOT_CAMERA = 1, SHOT_FOLLOW = 2, SHOT_PATH = 3, SHOT_MOSAIC = 4, SHOT_POV = 5, SHOT_FREE = 6;
    /**
     * Transitions: straight cut, dip through black, cross-fade from the last frame, kaleidoscope (the outgoing
     * picture folds into spinning mirrored segments, the cut happens at its peak), white flash cut, long double
     * exposure (the new picture fades in over the old one, held). Older clients play the last three as a cut.
     */
    public static final int CUT = 0, FADE = 1, CROSSFADE = 2, MIRROR = 3, FLASH = 4, DOUBLE_EXPOSURE = 5;
    /** Score strip: shown for a while after a publication, always, never. */
    public static final int SCORES_AUTO = 0, SCORES_ON = 1, SCORES_OFF = 2;

    /** A contestant of the active season (cast order) with the PUBLISHED total and rank (0 = not ranked yet). */
    public record Contestant(String name, int points, int rank, boolean online) {}

    /** One tile of a split screen: a camera key and the label written on it (the zone's players). */
    public record Tile(String camera, String label) {}

    /**
     * The shot on air. {@code serial} changes at every new shot (the clients play the transition then).
     *
     * @param camera camera key ("studio:2") for {@link #SHOT_CAMERA}, path name for {@link #SHOT_PATH}, lieu for
     *               {@link #SHOT_MOSAIC}
     * @param player the followed player ({@link #SHOT_FOLLOW}) or the one whose eyes we see ({@link #SHOT_POV})
     * @param effect screen effect id while this shot is on ("" = none), see {@code client.fx.ScreenEffects}
     * @param tiles  the tiles of a {@link #SHOT_MOSAIC}
     */
    public record Shot(int kind, String camera, String player, int transition, int serial, String effect, List<Tile> tiles) {
        public static final Shot NONE = new Shot(SHOT_NONE, "", "", CUT, 0, "", List.of());

        public Shot {
            camera = camera == null ? "" : camera;
            player = player == null ? "" : player;
            effect = effect == null ? "" : effect;
            tiles = tiles == null ? List.of() : List.copyOf(tiles);
        }

        /** Same picture (kind, camera, player, tiles), whatever the transition and serial. */
        public boolean sameContent(Shot o) {
            return kind == o.kind && camera.equals(o.camera) && player.equals(o.player) && tiles.equals(o.tiles);
        }

        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(kind);
            buf.writeUtf(camera);
            buf.writeUtf(player);
            buf.writeVarInt(transition);
            buf.writeVarInt(serial);
            buf.writeUtf(effect);
            buf.writeVarInt(tiles.size());
            for (Tile t : tiles) {
                buf.writeUtf(t.camera());
                buf.writeUtf(t.label());
            }
        }

        static Shot read(FriendlyByteBuf buf) {
            int kind = buf.readVarInt();
            String camera = buf.readUtf(), player = buf.readUtf();
            int transition = buf.readVarInt(), serial = buf.readVarInt();
            String effect = buf.readUtf();
            List<Tile> tiles = new ArrayList<>();
            for (int i = buf.readVarInt(); i > 0; i--) tiles.add(new Tile(buf.readUtf(), buf.readUtf()));
            return new Shot(kind, camera, player, transition, serial, effect, tiles);
        }
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
     * @param cameras      every camera, of every lieu
     * @param shot         the shot on air
     * @param auto         automatic direction on
     * @param letterbox    cinema bars on the broadcast
     * @param scores       score strip mode ({@link #SCORES_AUTO}…)
     * @param master       the show's host (name shown in the credits)
     * @param sequence     credits or intro running ("" = none, else "intro" / "outro" / "manche")
     * @param focus        the group the keys 1–9 and "caméra 2" address now ("studio", "pont", "pont:ensuri")
     * @param paths        every recorded travelling
     */
    public record ShowState(long serverTick, int season, String seasonName, boolean seasonHidden, int partie, String partieName,
                            String mancheId, int mancheNumber, String mancheName, String timerName, long timerEnd, int timerTotal,
                            List<Contestant> contestants, List<CameraSpot> cameras, Shot shot, boolean auto, boolean letterbox,
                            int scores, String master, String sequence, String focus, List<CameraPath> paths) implements CustomPacketPayload {

        public static final Type<ShowState> TYPE = new Type<>(CraftMaster.id("show_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShowState> CODEC = CustomPacketPayload.codec(ShowState::write, ShowState::read);

        public static final ShowState EMPTY = new ShowState(0, 0, "", false, 0, "", "", 0, "", "", -1, 0, List.of(), List.of(),
                Shot.NONE, false, false, SCORES_AUTO, "", "", CameraSpot.STUDIO, List.of());

        /** Same state with another shot on air (a replay directed on the watching game). */
        public ShowState withShot(Shot other) {
            return new ShowState(serverTick, season, seasonName, seasonHidden, partie, partieName, mancheId, mancheNumber, mancheName,
                    timerName, timerEnd, timerTotal, contestants, cameras, other, auto, letterbox, scores, master, sequence, focus, paths);
        }

        /** Same state apart from the send time (to know whether to send it again). */
        public ShowState unclocked() {
            return new ShowState(0, season, seasonName, seasonHidden, partie, partieName, mancheId, mancheNumber, mancheName,
                    timerName, timerEnd, timerTotal, contestants, cameras, shot, auto, letterbox, scores, master, sequence, focus, paths);
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
            shot.write(buf);
            buf.writeBoolean(auto);
            buf.writeBoolean(letterbox);
            buf.writeVarInt(scores);
            buf.writeUtf(master);
            buf.writeUtf(sequence);
            buf.writeUtf(focus);
            buf.writeVarInt(paths.size());
            for (CameraPath p : paths) p.write(buf);
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
            Shot shot = Shot.read(buf);
            boolean auto = buf.readBoolean(), letterbox = buf.readBoolean();
            int scores = buf.readVarInt();
            String master = buf.readUtf(), sequence = buf.readUtf(), focus = buf.readUtf();
            List<CameraPath> paths = new ArrayList<>();
            for (int i = buf.readVarInt(); i > 0; i--) paths.add(CameraPath.read(buf));
            return new ShowState(tick, season, seasonName, hidden, partie, partieName, mancheId, mancheNumber, mancheName,
                    timerName, timerEnd, timerTotal, contestants, cameras, shot, auto, letterbox, scores, master, sequence, focus, paths);
        }

        @Override
        public Type<ShowState> type() { return TYPE; }
    }

    /**
     * A one-off event for the overlay. Kinds ({@code Cues.*}): {@code points} (player, title "+3"), {@code lower_third}
     * (title, subtitle, player), {@code scores} (show the score strip), {@code task} (show the task card),
     * {@code title_card} (big centred card: title, subtitle), {@code contestant} (opening credits: player, title =
     * "1/4"), {@code credits} (closing credits roll), {@code theme} (play the theme, or the sound id in subtitle;
     * title "stop" stops it), {@code clear} (removes every card), {@code broadcast} (title "on" / "off": switches
     * that game's broadcast mode, sent to one player by {@code /cm tv diffusion}), {@code path_preview} (title = path
     * name: this game previews the travelling on its own screen, sent to one organiser).
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
                BROADCAST = "broadcast", PATH_PREVIEW = "path_preview",
                /** Everyone's picture to black and back (title = round name, subtitle = "Manche 2", player = "in,hold,out" ticks). */
                BLACKOUT = "blackout",
                /** The black opens now (its hold ends; ticks = fade-out): the launch waited until every game was ready. */
                BLACKOUT_OPEN = "blackout_open",
                /** The task typed at the bottom of the broadcast picture (title = text, ticks = how long it stays). */
                TASK_TYPE = "task_type",
                /** The show's feed forced on the players (title = off, freeze or move): see {@link ShowFeed}. */
                FEED = "feed",
                /** The game master's voice ({@code MasterVoice}): title = the line shown (empty = none), subtitle = host | player. */
                VOICE = "voice",
                /** A player's rights on the show's picture: title = "admin" or "", subtitle = "feed" when F7 is allowed. */
                RIGHTS = "rights",
                /** An organiser moved this player (/tp, /gamemode): out of F7 and F8, a normal player again. */
                RELEASE = "release",
                /** The live preview of a drawing: title = on | off, subtitle = corner, player = size in pixels. */
                PREVIEW = "preview";

        private Cues() {}
    }

    /**
     * Server → broadcast games: the free camera's target pose ({@link #SHOT_FREE}); the clients glide to it
     * smoothly, so a few updates per second are enough. {@code smoothing}: seconds to catch up (0 = jump).
     */
    public record FreeCamera(double x, double y, double z, float yaw, float pitch, float fov, float smoothing) implements CustomPacketPayload {
        public static final Type<FreeCamera> TYPE = new Type<>(CraftMaster.id("free_camera"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FreeCamera> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeDouble(p.x);
                    buf.writeDouble(p.y);
                    buf.writeDouble(p.z);
                    buf.writeFloat(p.yaw);
                    buf.writeFloat(p.pitch);
                    buf.writeFloat(p.fov);
                    buf.writeFloat(p.smoothing);
                },
                buf -> new FreeCamera(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
                        buf.readFloat(), buf.readFloat()));

        @Override
        public Type<FreeCamera> type() { return TYPE; }
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
        PayloadTypeRegistry.clientboundPlay().register(FreeCamera.TYPE, FreeCamera.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BroadcastMode.TYPE, BroadcastMode.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CameraRequest.TYPE, CameraRequest.CODEC);
    }
}
