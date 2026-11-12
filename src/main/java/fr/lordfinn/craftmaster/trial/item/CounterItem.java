package fr.lordfinn.craftmaster.trial.item;

import fr.lordfinn.craftmaster.trial.Judging;
import fr.lordfinn.craftmaster.trial.ResultSheet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.level.Level;
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
 * Tally counter: use it on anything that holds items to count them, with the three most common kinds. Blocks: any
 * container (chest, double chest, barrel, shulker box, hopper, dispenser, furnace...), other mods' storages through
 * Fabric's transfer API, the studio's desks and prize tables. Entities: players, chest minecarts and boats,
 * donkeys and llamas with a chest, villagers and allays, armour stands and mobs (what they wear and hold), item
 * frames, items on the ground. If the other hand holds an item, only that kind is counted ("how many eggs in the
 * chest?"). Organisers get buttons to write the count down for a contestant. Entities go through
 * {@link #useOnEntity} (Trials' UseEntityCallback: an item only gets living entities by itself).
 */
public class CounterItem extends Item {

    public CounterItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        List<ItemStack> items = blockItems(level, ctx.getClickedPos());
        if (items == null) {
            player.sendOverlayMessage(Component.literal("🔢 Utilise le compteur sur ce qui contient des objets (coffre, tonneau, entonnoir, wagonnet, âne, porte-armure, joueur…)")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS_SERVER;
        }
        report(player, items, level.getBlockState(ctx.getClickedPos()).getBlock().getName().getString(), ctx.getHand());
        return InteractionResult.SUCCESS_SERVER;
    }

    /** The items of the block there, or null if it holds none. */
    static List<ItemStack> blockItems(Level level, BlockPos pos) {
        List<ItemStack> items = new ArrayList<>();
        // Fabric's transfer API first: double chests whole, other mods' storages, the studio's desks and tables
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, null);
        if (storage != null) {
            for (StorageView<ItemVariant> v : storage) {
                if (v.isResourceBlank() || v.getAmount() <= 0) continue;
                items.add(v.getResource().toStack((int) Math.min(Integer.MAX_VALUE, v.getAmount())));
            }
        } else if (level.getBlockEntity(pos) instanceof Container c) {
            for (int i = 0; i < c.getContainerSize(); i++) items.add(c.getItem(i));
        } else {
            return null;
        }
        // What lies on show on a prize table counts too
        if (level.getBlockEntity(pos) instanceof fr.lordfinn.craftmaster.studio.PrizeTableBlockEntity table) items.addAll(table.items());
        return items;
    }

    /** The items an entity holds, or null if it holds none. */
    static List<ItemStack> entityItems(Entity e) {
        List<ItemStack> items = new ArrayList<>();
        if (e instanceof Player p) {
            items.addAll(p.getInventory().getNonEquipmentItems());
            for (EquipmentSlot slot : EquipmentSlot.values()) items.add(p.getItemBySlot(slot));
            return items;
        }
        boolean any = false;
        if (e instanceof Container c) {                       // chest minecarts and boats, hopper minecarts
            for (int i = 0; i < c.getContainerSize(); i++) items.add(c.getItem(i));
            any = true;
        }
        if (e instanceof InventoryCarrier carrier) {          // villagers, allays, piglins
            Container c = carrier.getInventory();
            for (int i = 0; i < c.getContainerSize(); i++) items.add(c.getItem(i));
            any = true;
        }
        if (e instanceof AbstractHorse horse) {                // saddle, armour, a donkey's or llama's chest
            for (int i = 0; i < horse.getInventorySize(); i++) items.add(horse.getSlot(AbstractHorse.INVENTORY_SLOT_OFFSET + i).get());
            any = true;
        }
        if (e instanceof LivingEntity living) {               // armour stands and mobs: what they wear and hold
            for (EquipmentSlot slot : EquipmentSlot.values()) items.add(living.getItemBySlot(slot));
            any = true;
        }
        if (e instanceof ItemFrame frame) {
            items.add(frame.getItem());
            any = true;
        }
        if (e instanceof ItemEntity item) {
            items.add(item.getItem());
            any = true;
        }
        return any ? items : null;
    }

    /** Use on an entity (from Trials' UseEntityCallback). */
    public static InteractionResult useOnEntity(ServerPlayer player, InteractionHand hand, Entity target) {
        List<ItemStack> items = entityItems(target);
        if (items == null) return InteractionResult.PASS;
        String name = target instanceof ServerPlayer other ? other.getGameProfile().name() : target.getName().getString();
        report(player, items, name, hand);
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
