package fr.lordfinn.craftmaster.studio;

import net.minecraft.ChatFormatting;
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
 * "Écran du studio": used on a wall, hangs a 3 × 2 screen showing the test card. Organisers then give it an image
 * (right click on the screen, or {@code /cm media image <URL>} while looking at it).
 */
public class ImageScreenItem extends Item {

    public static final int WIDTH = 3, HEIGHT = 2;

    public ImageScreenItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (!ctx.getClickedFace().getAxis().isHorizontal()) {
            if (player != null && ctx.getLevel().isClientSide())
                player.sendOverlayMessage(Component.translatable("message.craftmaster.screen_wall").withStyle(ChatFormatting.YELLOW));
            return InteractionResult.FAIL;
        }
        if (player != null && !player.mayBuild()) return InteractionResult.FAIL;
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        ImageScreenEntity screen = ImageScreenEntity.create(level, ctx.getClickedPos(), ctx.getClickedFace(), WIDTH, HEIGHT);
        level.addFreshEntity(screen);
        screen.playSound(SoundEvents.PAINTING_PLACE, 1f, 0.8f);
        if (player == null || !player.isCreative()) ctx.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.craftmaster.studio_screen.hint").withStyle(ChatFormatting.GRAY));
    }
}
