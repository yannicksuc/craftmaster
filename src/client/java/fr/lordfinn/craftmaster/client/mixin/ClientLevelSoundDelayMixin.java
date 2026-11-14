package fr.lordfinn.craftmaster.client.mixin;

import fr.lordfinn.craftmaster.client.broadcast.PlayerEars;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Far sounds (thunder...) are delayed by their distance to where they are heard from: the player in a camera view ({@link PlayerEars}). */
@Mixin(ClientLevel.class)
public abstract class ClientLevelSoundDelayMixin {

    @Redirect(method = "playSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZJ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 craftmaster$heardFrom(Camera camera) {
        return PlayerEars.position(camera.position());
    }
}
