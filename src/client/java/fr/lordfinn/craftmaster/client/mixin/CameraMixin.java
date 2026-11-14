package fr.lordfinn.craftmaster.client.mixin;

import fr.lordfinn.craftmaster.client.broadcast.Broadcast;
import fr.lordfinn.craftmaster.client.broadcast.DirectorCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Broadcast mode: the view comes from the director's shot (studio camera or follow shot) instead of the player's
 * eyes. The camera stays attached to the player for everything else (chunks, culling); it is not "detached", so the
 * local player's body is not drawn. Field of view: the camera's own, or the player's setting without the dynamic
 * effects (sprint, speed), which would make the picture breathe for no reason.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private boolean detached;

    @Shadow protected abstract void setPosition(Vec3 position);

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Unique private DirectorCamera.Pose craftmaster$pose;

    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void craftmaster$directorShot(DeltaTracker delta, CallbackInfo ci) {
        craftmaster$pose = null;
        // A preview for the directors being taken this frame (put back before the interface is drawn)
        DirectorCamera.Pose still = fr.lordfinn.craftmaster.client.broadcast.ThumbCapture.framePose();
        if (still != null) {
            craftmaster$pose = still;
            detached = true;
            setPosition(still.position());
            setRotation(still.yaw(), still.pitch());
            return;
        }
        // An organiser looking through a camera or a spotlight to frame it (studio/ControlView)
        DirectorCamera.Pose control = fr.lordfinn.craftmaster.client.studio.ControlView.pose(delta.getGameTimeDeltaPartialTick(true));
        if (control != null) {
            craftmaster$pose = control;
            detached = false;
            setPosition(control.position());
            setRotation(control.yaw(), control.pitch());
            return;
        }
        // Broadcast mode, the show's feed on a player's screen, or a travelling preview on an organiser's own screen
        boolean feed = fr.lordfinn.craftmaster.client.broadcast.ShowFeedClient.active();
        if (!Broadcast.active() && !feed && !DirectorCamera.previewing()) return;
        DirectorCamera.Pose pose = DirectorCamera.pose(delta.getGameTimeDeltaPartialTick(true));
        if (pose == null) return;
        craftmaster$pose = pose;
        // With the feed, the player is in the picture: their body is drawn (the broadcast game's never is)
        detached = feed;
        setPosition(pose.position());
        setRotation(pose.yaw(), pose.pitch());
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void craftmaster$directorFov(float partialTick, CallbackInfoReturnable<Float> cir) {
        DirectorCamera.Pose pose = craftmaster$pose;
        if (pose == null) return;
        cir.setReturnValue(pose.fov() > 0 ? pose.fov() : (float) Minecraft.getInstance().options.fov().get().intValue());
    }
}
