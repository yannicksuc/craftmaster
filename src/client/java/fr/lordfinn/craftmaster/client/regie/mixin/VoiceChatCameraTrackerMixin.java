package fr.lordfinn.craftmaster.client.regie.mixin;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.client.broadcast.PlayerEars;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Simple Voice Chat hears the other players from its camera tracker, refreshed at the start of every frame from the
 * game's camera (volume by distance, OpenAL direction, stereo, voices changed by a voice changer like Voicemod
 * included: they are mixed like any voice). While a studio camera is on this player's screen the tracker is set to
 * the player's head instead, and kept as it was for the frame after a shot preview ({@link PlayerEars}).
 * <p>
 * Voice Chat has no public API for it (its camera event is internal) and is optional, so its tracker state, a record
 * {@code CameraState(position, forward, up, yRot, player, playerEyePosition, cameraEntity, spectator)}, is built by
 * reflection; the camera entity is the player, so its "freecam" setting does not move the voices again.
 */
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.camera.CameraTracker", remap = false)
public abstract class VoiceChatCameraTrackerMixin {

    @Unique private static Field craftmaster$state;
    @Unique private static Constructor<?> craftmaster$newState;
    @Unique private static boolean craftmaster$broken;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void craftmaster$playerEars(CallbackInfo ci) {
        if (craftmaster$broken) return;
        PlayerEars.Mode mode = PlayerEars.mode();
        if (mode == PlayerEars.Mode.CAMERA) return;
        if (mode == PlayerEars.Mode.HOLD) {
            ci.cancel();
            return;
        }
        PlayerEars.Ears ears = PlayerEars.player();
        LocalPlayer player = Minecraft.getInstance().player;
        if (ears == null || player == null) return;
        try {
            if (craftmaster$state == null) {
                Field f = getClass().getDeclaredField("state");
                f.setAccessible(true);
                Constructor<?> c = f.getType().getDeclaredConstructor(Vec3.class, Vector3fc.class, Vector3fc.class, float.class,
                        UUID.class, Vec3.class, UUID.class, boolean.class);
                c.setAccessible(true);
                craftmaster$newState = c;
                craftmaster$state = f;
            }
            UUID id = player.getUUID();
            craftmaster$state.set(this, craftmaster$newState.newInstance(ears.position(), ears.forward(), ears.up(), ears.yRot(),
                    id, ears.position(), id, player.isSpectator()));
            ci.cancel();
        } catch (ReflectiveOperationException | RuntimeException e) {
            craftmaster$broken = true;   // another Voice Chat version: voices stay on the camera, nothing else breaks
            CraftMaster.LOG.warn("Simple Voice Chat: cannot keep the voices on the player in camera views", e);
        }
    }
}
