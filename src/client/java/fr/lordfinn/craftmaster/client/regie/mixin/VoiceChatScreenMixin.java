package fr.lordfinn.craftmaster.client.regie.mixin;

import fr.lordfinn.craftmaster.client.regie.AgentToggleButton;
import fr.lordfinn.craftmaster.client.regie.VoiceRegie;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Simple Voice Chat's menu (V) gets the régie agent's on / off button, for organisers: in its bottom row, right after
 * its "adjust volumes" button (x + 72), the row's free place. Voice Chat has no API for its menus.
 */
@Mixin(targets = "de.maxhenkel.voicechat.gui.VoiceChatScreen", remap = false)
public abstract class VoiceChatScreenMixin extends Screen {

    private VoiceChatScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void craftmaster$agentButton(CallbackInfo ci) {
        if (!VoiceRegie.isAdmin()) return;
        int left = (width - 195) / 2, top = (height - 76) / 2;
        try {   // VoiceChatScreenBase's public getters (its size is fixed: 195 x 76)
            left = (int) getClass().getMethod("getGuiLeft").invoke(this);
            top = (int) getClass().getMethod("getGuiTop").invoke(this);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        addRenderableWidget(new AgentToggleButton(left + 72, top + 76 - 6 - 20));
    }
}
