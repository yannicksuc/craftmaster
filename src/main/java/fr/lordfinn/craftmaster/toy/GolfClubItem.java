package fr.lordfinn.craftmaster.toy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Golf club: hit a toy (a golf ball) with it. The swing's power is the attack charge (the bar under the
 * crosshair: wait a second for a full swing); sneaking is a putt that keeps the ball on the ground. Each swing
 * is a stroke on that ball.
 */
public class GolfClubItem extends Item {

    public GolfClubItem(Properties props) {
        super(props);
    }

    /** One swing a second at full power, no damage bonus. */
    public static Properties properties() {
        return new Properties().stacksTo(1).attributes(ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -3.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.craftmaster.golf_club.hint").withStyle(ChatFormatting.GRAY));
    }
}
