package fr.lordfinn.craftmaster.portrait;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * click on it.
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
        Portraits.refresh(p);
        level.addFreshEntity(p);
        p.playSound(SoundEvents.PAINTING_PLACE, 1f, 1f);
        ItemStack stack = ctx.getItemInHand();
        if (player == null || !player.isCreative()) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.craftmaster.portrait.hint").withStyle(ChatFormatting.GRAY));
    }
}
