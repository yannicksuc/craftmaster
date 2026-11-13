package fr.lordfinn.craftmaster.client.wardrobe;

import fr.lordfinn.craftmaster.wardrobe.WardrobeNetwork;
import fr.lordfinn.craftmaster.wardrobe.Wardrobes;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client side of the wardrobe: its screen, and the line telling a costume copy goes back to the wardrobe. */
public final class WardrobeClient {

    private WardrobeClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(WardrobeNetwork.Open.TYPE, (p, ctx) -> {
            Minecraft mc = ctx.client();
            if (mc.gui.screen() instanceof WardrobeScreen s && s.pos().equals(p.pos())) s.update(p);
            else if (!p.refresh()) mc.gui.setScreen(new WardrobeScreen(p));
        });
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (Wardrobes.isCostume(stack))
                lines.add(Component.translatable("item.craftmaster.wardrobe.costume_copy").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        });
    }
}
