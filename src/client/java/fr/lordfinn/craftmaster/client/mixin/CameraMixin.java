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
        if (!Broadcast.active()) return;
        DirectorCamera.Pose pose = DirectorCamera.pose(delta.getGameTimeDeltaPartialTick(true));
        if (pose == null) return;
        craftmaster$pose = pose;
        detached = false;
        setPosition(pose.position());
        setRotation(pose.yaw(), pose.pitch());
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void craftmaster$directorFov(float partialTick, CallbackInfoReturnable<Float> cir) {
        DirectorCamera.Pose pose = craftmaster$pose;
        if (pose == null || !Broadcast.active()) return;
        cir.setReturnValue(pose.fov() > 0 ? pose.fov() : (float) Minecraft.getInstance().options.fov().get().intValue());
    }
}
