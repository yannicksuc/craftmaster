package fr.lordfinn.craftmaster.task;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** The UV lamp: held in either hand while reading, invisible ink shows up on the paper. */
public class UvLampItem extends Item {

    public UvLampItem(Properties properties) {
        super(properties);
    }

    /** Holds the lamp in one of the hands. */
    public static boolean holds(Player player) {
        return player.getMainHandItem().getItem() instanceof UvLampItem || player.getOffhandItem().getItem() instanceof UvLampItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.craftmaster.uv_lamp.hint").withStyle(ChatFormatting.GRAY));
    }
}
