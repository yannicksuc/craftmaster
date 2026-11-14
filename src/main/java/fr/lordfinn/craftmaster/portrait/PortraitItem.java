package fr.lordfinn.craftmaster.portrait;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import java.util.function.Consumer;

/**
 * "Tableau": used on the underside of a block, hangs a portrait from it, its picture turned toward the player.
 * The new portrait joins the latest season's gallery at the next free rank; organisers change that with a right
 * click on it. Picked with its settings (Ctrl + middle click on a portrait), it hangs a copy of that portrait.
 */
public class PortraitItem extends Item {

    public PortraitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (ctx.getClickedFace() != Direction.DOWN) {
            if (player != null && ctx.getLevel().isClientSide())
                player.sendOverlayMessage(Component.translatable("message.craftmaster.portrait_ceiling").withStyle(ChatFormatting.YELLOW));
            return InteractionResult.FAIL;
        }
        if (player != null && !player.mayBuild()) return InteractionResult.FAIL;
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;

        BlockPos ceiling = ctx.getClickedPos();
        int season = Portraits.defaultSeason();
        PortraitEntity p = PortraitEntity.create(level, ceiling, ctx.getHorizontalDirection().getOpposite(), season, Portraits.nextRank(level, season));
        // Facing the one who hangs it, exactly (not only the nearest of four directions)
        if (ctx.getPlayer() != null) p.faceLook(ctx.getPlayer().getYRot());
        applySettings(p, ctx.getItemInHand());
        Portraits.refresh(p);
        level.addFreshEntity(p);
        p.playSound(SoundEvents.PAINTING_PLACE, 1f, 1f);
        ItemStack stack = ctx.getItemInHand();
        if (player == null || !player.isCreative()) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    /** A portrait item carrying {@code p}'s settings (its pose by name, its string, its window look...). */
    public static ItemStack withSettings(PortraitEntity p) {
        ItemStack stack = new ItemStack(fr.lordfinn.craftmaster.registry.ModContent.PORTRAIT_ITEM);
        CompoundTag t = new CompoundTag();
        t.putInt("Season", p.season());
        if (p.followsRank()) t.putInt("Rank", p.rank());
        else t.putString("FixedPlayer", p.fixedPlayer());
        t.putString("DisplayName", p.displayName());
        t.putString("SkinUrl", p.skinUrl());
        t.putString("Pose", p.poseName());
        t.putFloat("Hang", p.hangSetting());
        t.putBoolean("Window", p.window());
        t.putInt("WindowDepth", p.windowDepth());
        t.putInt("WindowSize", p.windowSize());
        CompoundTag root = new CompoundTag();
        root.put(KEY, t);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return stack;
    }

    private static final String KEY = "portrait";

    /** The settings an item carries (picked with Ctrl), or null. */
    private static CompoundTag settings(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        return data.copyTag().getCompound(KEY).orElse(null);
    }

    private static void applySettings(PortraitEntity p, ItemStack stack) {
        CompoundTag t = settings(stack);
        if (t == null) return;
        p.setSeason(t.getIntOr("Season", p.season()));
        String fixed = t.getStringOr("FixedPlayer", "");
        if (!fixed.isBlank()) p.setFixedPlayer(fixed);
        else p.setRank(Math.max(1, t.getIntOr("Rank", p.rank())));
        p.setDisplayName(t.getStringOr("DisplayName", ""));
        p.setSkinUrl(t.getStringOr("SkinUrl", ""));
        p.setPoseName(t.getStringOr("Pose", p.poseName()));
        p.setHang(t.getFloatOr("Hang", -1f));
        p.setWindow(t.getBooleanOr("Window", false));
        p.setWindowDepth(t.getIntOr("WindowDepth", PortraitEntity.DEFAULT_WINDOW_DEPTH));
        p.setWindowSize(t.getIntOr("WindowSize", PortraitEntity.DEFAULT_WINDOW_SIZE));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        CompoundTag t = settings(stack);
        if (t != null) {
            // A copy: what it will show
            String who = t.getStringOr("FixedPlayer", "");
            Component follow = who.isBlank() ? Component.translatable("item.craftmaster.portrait.copy_rank", t.getIntOr("Rank", 1))
                    : Component.literal(who);
            out.accept(Component.translatable("item.craftmaster.portrait.copy", t.getIntOr("Season", 1), follow,
                    t.getStringOr("Pose", "")).withStyle(ChatFormatting.GOLD));
        }
        out.accept(Component.translatable("item.craftmaster.portrait.hint").withStyle(ChatFormatting.GRAY));
    }
}
