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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * A note. In an organiser's hand (not taken) it is the hiding tool: right-click a block hides the note there (the
 * way depends on the block: under a carpet, in a bookshelf, false bottom of a chest...), crouching pins it on the
 * clicked face, a left click on a hiding place removes it, right-click in the air opens the note's editor.
 * Anyone else, or once taken by a finder, reads it with a right-click.
 */
public class NoteItem extends Item {

    public NoteItem(Properties properties) {
        super(properties);
    }

    public static TaskComponents.NoteRef ref(ItemStack stack) {
        return stack.get(TaskComponents.NOTE);
    }

    /** In this player's hand, the item is the organisers' hiding tool. */
    public static boolean isTool(ServerPlayer player, ItemStack stack) {
        TaskComponents.NoteRef r = ref(stack);
        return stack.getItem() instanceof NoteItem && r != null && !r.taken() && Permissions.isAdmin(player);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        TaskComponents.NoteRef r = ref(stack);
        if (r == null) return InteractionResult.PASS;
        if (isTool(sp, stack)) Notes.openEditor(sp, r.id());
        else Notes.read(sp, r);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getPlayer() instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        ItemStack stack = ctx.getItemInHand();
        TaskComponents.NoteRef r = ref(stack);
        if (r == null) return InteractionResult.PASS;
        if (!isTool(sp, stack)) {
            Notes.read(sp, r);
            return InteractionResult.SUCCESS_SERVER;
        }
        // Hiding is done by Notes.onUseBlock (before the block itself reacts)
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        TaskComponents.NoteRef r = ref(stack);
        if (r == null) return;
        if (r.taken()) {
            out.accept(Component.translatable("item.craftmaster.note.hint_read").withStyle(ChatFormatting.GRAY));
            return;
        }
        out.accept(Component.translatable("item.craftmaster.note.tool").withStyle(ChatFormatting.GOLD));
        for (String k : new String[]{"hide", "pin", "remove", "edit"})
            out.accept(Component.translatable("item.craftmaster.note.hint_" + k).withStyle(ChatFormatting.DARK_GRAY));
    }
}
