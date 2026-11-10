package fr.lordfinn.craftmaster.client.fx;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.client.mixin.HudInvoker;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * The "citrouille" effect is vanilla's carved pumpkin view: the same texture ({@code textures/misc/pumpkinblur.png},
 * so a resource pack that changes it changes ours) drawn by vanilla's own camera overlay code
 * ({@code Hud.extractTextureOverlay}), with the effect's intensity as alpha for the fade. It is a HUD element placed
 * right after vanilla's camera overlays: under the hotbar, the chat and the broadcast overlay (which is added last),
 * so the show's graphics stay on top. Drawn in broadcast mode too (vanilla's own overlays are hidden there).
 */
public final class FxOverlay {

    private FxOverlay() {}

    public static final String PUMPKIN = "citrouille";
    /** Vanilla's carved pumpkin overlay, as vanilla resolves the equippable's camera overlay "misc/pumpkinblur". */
    public static final Identifier PUMPKIN_BLUR = Identifier.withDefaultNamespace("textures/misc/pumpkinblur.png");

    static void init() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, CraftMaster.id("screen_fx_overlay"), (g, delta) -> {
            float alpha = ScreenEffects.intensity(PUMPKIN);
            if (alpha > 0f) ((HudInvoker) Minecraft.getInstance().gui.hud).craftmaster$extractTextureOverlay(g, PUMPKIN_BLUR, alpha);
        });
    }
}
