package fr.lordfinn.craftmaster.client.mixin;

import fr.lordfinn.craftmaster.client.portrait.PortraitClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Portraits hang far below their entity's position (the hook, under the ceiling): the game only looks for entities
 * in the sections around the line of sight, so a portrait on a long string was only found when looking near its
 * hook. After the game's own aim, the portraits are tried along the line of sight.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftPickMixin {

    @Inject(method = "pick", at = @At("TAIL"))
    private void craftmaster$pickPortraits(float partialTick, CallbackInfo ci) {
        PortraitClient.pickInReach((Minecraft) (Object) this, partialTick);
    }
}
