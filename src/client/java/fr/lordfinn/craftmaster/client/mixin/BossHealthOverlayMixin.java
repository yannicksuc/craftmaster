package fr.lordfinn.craftmaster.client.mixin;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

/**
 * An update for a boss bar this game never saw added is dropped instead of crashing (the vanilla game throws and
 * disconnects). It happens when watching a replay: a player's file that starts while the round's timer bar is
 * already shown has its updates but not its creation, and a seek in Server Replay's viewer may skip the creation.
 */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {

    @Shadow
    @Final
    private Map<UUID, LerpingBossEvent> events;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void craftmaster$unknownBar(ClientboundBossEventPacket packet, CallbackInfo ci) {
        UUID[] id = new UUID[1];
        boolean[] add = new boolean[1];
        packet.dispatch(new ClientboundBossEventPacket.Handler() {
            @Override
            public void add(UUID u, net.minecraft.network.chat.Component name, float progress, net.minecraft.world.BossEvent.BossBarColor color,
                            net.minecraft.world.BossEvent.BossBarOverlay overlay, boolean darken, boolean music, boolean fog) {
                add[0] = true;
            }

            @Override
            public void remove(UUID u) {
                id[0] = u;
            }

            @Override
            public void updateProgress(UUID u, float progress) {
                id[0] = u;
            }

            @Override
            public void updateName(UUID u, net.minecraft.network.chat.Component name) {
                id[0] = u;
            }

            @Override
            public void updateStyle(UUID u, net.minecraft.world.BossEvent.BossBarColor color, net.minecraft.world.BossEvent.BossBarOverlay overlay) {
                id[0] = u;
            }

            @Override
            public void updateProperties(UUID u, boolean darken, boolean music, boolean fog) {
                id[0] = u;
            }
        });
        if (!add[0] && id[0] != null && !events.containsKey(id[0])) ci.cancel();
    }
}
