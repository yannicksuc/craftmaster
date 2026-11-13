package fr.lordfinn.craftmaster.client.mixin;

import fr.lordfinn.craftmaster.client.fx.ScreenEffects;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Screen effect "timelapse": the clouds drift with the accelerated time (they follow the game time, not the clocks). */
@Mixin(CloudRenderer.class)
public abstract class FxTimelapseCloudsMixin {

    //? if >=26.3 {
    @ModifyVariable(method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V", at = @At("HEAD"), argsOnly = true)
    //?} else {
    /*@ModifyVariable(method = "render(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V", at = @At("HEAD"), argsOnly = true)
    *///?}
    private long craftmaster$timelapse(long gameTime) {
        return gameTime + ScreenEffects.timelapseTicks();
    }
}
