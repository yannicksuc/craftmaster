package fr.lordfinn.craftmaster.task;

import fr.lordfinn.craftmaster.Permissions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * The organisers' wax seal: right-click opens the sealing screen (a round's task or a free text, the wax colour,
 * the name written on the envelope, how many), which gives the sealed envelopes. Its head shows the last colour.
 */
public class WaxSealItem extends Item {

    public WaxSealItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (!Permissions.isAdmin(sp)) {
            sp.sendOverlayMessage(Component.translatable("message.craftmaster.admin_only").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        Tasks.openEditor(sp, 1, null);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.craftmaster.wax_seal.wax", Component.translatable("wax.craftmaster." + TaskComponents.sealColor(stack).id()))
                .withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("item.craftmaster.wax_seal.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
