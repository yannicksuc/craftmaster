package fr.lordfinn.craftmaster.mixin;

import fr.lordfinn.craftmaster.portrait.PortraitEntity;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A portrait hangs far below its hook: hit or used at the hook (under the ceiling), the server must find it in
 * reach although its frame is blocks below. Its reach box is the frame's and the hook's together.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PortraitReachMixin {

    @Redirect(method = {"handleAttack", "handleInteract"}, require = 2,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private AABB craftmaster$portraitReach(Entity entity) {
        return entity instanceof PortraitEntity p ? p.reachBox() : entity.getBoundingBox();
    }
}
