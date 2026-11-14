package fr.lordfinn.craftmaster.client.broadcast;

import fr.lordfinn.craftmaster.client.studio.ControlView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Where this game listens from. The views that put a studio camera on a player's screen while they keep playing — the
 * show's feed (F7, {@link ShowFeedClient}), a travelling preview ({@link DirectorCamera#previewing()}) and looking
 * through a camera or a spotlight ({@link ControlView}) — move the picture, not the player: the game's sounds and the
 * Simple Voice Chat voices are still heard from the player's head, with the player's own facing (left / right follows
 * where the player looks, not where the camera looks).
 * <p>
 * Broadcast mode (F8, the game OBS captures) keeps listening from the director's camera: that game's player is parked
 * out of the show, the stream hears the place it shows.
 * <p>
 * Read at the start of a frame (vanilla's sound listener, Voice Chat's camera tracker): the camera then still holds the
 * last frame's view. After a frame drawn for a shot preview ({@link ThumbCapture}), that view is the previewed camera's,
 * so the listener is left where it was for that one frame instead of jumping there and back.
 */
public final class PlayerEars {

    private PlayerEars() {}

    public enum Mode {
        /** Vanilla: the listener is the camera. */
        CAMERA,
        /** The camera is elsewhere, the listener stays on the player ({@link #player()}). */
        PLAYER,
        /** Keep the last listener (the camera still holds a shot preview's view). */
        HOLD
    }

    /** Position, facing and up of the listener, and its yaw (Voice Chat's stereo without OpenAL). */
    public record Ears(Vec3 position, Vector3f forward, Vector3f up, float yRot) {}

    public static Mode mode() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return Mode.CAMERA;
        if (ControlView.active()) return Mode.PLAYER;
        if (!Broadcast.active() && (ShowFeedClient.active() || DirectorCamera.previewing())) return Mode.PLAYER;
        if (ThumbCapture.cameraOnThumb()) return Mode.HOLD;
        return Mode.CAMERA;
    }

    /** The player's head as the first-person camera would be (same rotation as Camera.setRotation), or null. */
    public static @Nullable Ears player() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return null;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float yRot = p.getViewYRot(pt), xRot = p.getViewXRot(pt);
        float[] facing = ControlView.playerFacing();   // looking through: the player's rotation aims the camera
        if (facing != null) {
            yRot = facing[0];
            xRot = facing[1];
        }
        Quaternionf q = new Quaternionf().rotationYXZ((float) Math.PI - yRot * (float) (Math.PI / 180.0),
                -xRot * (float) (Math.PI / 180.0), 0.0F);
        return new Ears(p.getEyePosition(pt), q.transform(new Vector3f(0, 0, -1)), q.transform(new Vector3f(0, 1, 0)), yRot);
    }

    /** Where sounds are heard from right now (the delay of far sounds, like thunder, is counted from there). */
    public static Vec3 position(Vec3 camera) {
        if (mode() != Mode.PLAYER) return camera;
        Ears e = player();
        return e == null ? camera : e.position();
    }
}
