package fr.lordfinn.craftmaster.task;

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
 * A message in a bottle: hidden in a chest, floating in a pond, given by an organiser. Right-click uncorks it and
 * the note is read (and logged as found); a note "taken once" comes out of the bottle into the inventory.
 */
public class NoteBottleItem extends Item {

    public NoteBottleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        TaskComponents.NoteRef r = stack.get(TaskComponents.NOTE);
        if (r == null) return InteractionResult.PASS;
        Notes.uncork(sp, hand, stack, r);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.craftmaster.note_bottle.hint").withStyle(ChatFormatting.GRAY));
    }
}
