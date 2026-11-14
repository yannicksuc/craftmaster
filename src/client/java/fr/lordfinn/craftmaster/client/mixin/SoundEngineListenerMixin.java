package fr.lordfinn.craftmaster.client.mixin;

import com.mojang.blaze3d.audio.Listener;
import com.mojang.blaze3d.audio.ListenerTransform;
import fr.lordfinn.craftmaster.client.broadcast.PlayerEars;
import net.minecraft.client.Camera;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game's sound listener (set once a frame from the camera) stays on the player's head while a studio camera is on
 * their screen (F7 feed, travelling preview, looking through a camera / spotlight): see {@link PlayerEars}.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineListenerMixin {

    @Shadow private boolean loaded;
    @Shadow @Final private Listener listener;
    @Shadow @Final private SoundEngineExecutor executor;

    @Inject(method = "updateSource", at = @At("HEAD"), cancellable = true)
    private void craftmaster$playerEars(Camera camera, CallbackInfo ci) {
        switch (PlayerEars.mode()) {
            case CAMERA -> {}
            case HOLD -> ci.cancel();
            case PLAYER -> {
                PlayerEars.Ears ears = PlayerEars.player();
                if (ears == null) return;
                ci.cancel();
                if (!loaded) return;
                ListenerTransform transform = new ListenerTransform(ears.position(), new Vec3(ears.forward()), new Vec3(ears.up()));
                executor.execute(() -> listener.setTransform(transform));
            }
        }
    }
}
