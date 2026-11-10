package fr.lordfinn.craftmaster.trial.item;

import fr.lordfinn.craftmaster.trial.Judging;
import fr.lordfinn.craftmaster.trial.ResultSheet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Tally counter: use it on a chest (any container) or on a player to count the items in it, with the three most
 * common kinds. If the other hand holds an item, only that kind is counted ("how many eggs in the chest?").
 * Organisers get buttons to write the count down for a contestant.
 */
public class CounterItem extends Item {

    public CounterItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(ctx.getClickedPos()) instanceof Container c)) {
            player.sendOverlayMessage(Component.literal("🔢 Utilise le compteur sur un coffre (ou un joueur)").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS_SERVER;
        }
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize(); i++) items.add(c.getItem(i));
        report(player, items, level.getBlockState(ctx.getClickedPos()).getBlock().getName().getString(), ctx.getHand());
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof ServerPlayer other) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        report(sp, other.getInventory().getNonEquipmentItems(), other.getGameProfile().name(), hand);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void report(ServerPlayer player, List<ItemStack> items, String where, InteractionHand hand) {
        ItemStack only = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        Map<String, Integer> byKind = new LinkedHashMap<>();
        int total = 0;
        for (ItemStack s : items) {
            if (s.isEmpty() || (!only.isEmpty() && !ItemStack.isSameItem(s, only))) continue;
            total += s.getCount();
            byKind.merge(s.getHoverName().getString(), s.getCount(), Integer::sum);
        }
        List<String> top = byKind.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(3)
                .map(e -> e.getValue() + " " + e.getKey()).toList();
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.8f);
        player.sendOverlayMessage(Component.literal("🔢 " + total).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        Judging.offerMeasure(player, Component.literal("🔢 " + where + " : ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(total + (only.isEmpty() ? " objet(s)" : " × " + only.getHoverName().getString())).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(top.isEmpty() || !only.isEmpty() ? "" : " (" + String.join(", ", top) + (byKind.size() > 3 ? ", …" : "") + ")")
                        .withStyle(ChatFormatting.GRAY)), total, ResultSheet.Kind.COUNT);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.craftmaster.tally_counter.hint").withStyle(ChatFormatting.GRAY));
    }
}
