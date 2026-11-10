package fr.lordfinn.craftmaster.trial.item;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.trial.TrialNetwork;
import fr.lordfinn.craftmaster.trial.TrialPropEntity;
import fr.lordfinn.craftmaster.trial.TrialProps;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * Task prop wand (organisers): use it on a block to put a new task prop on it, looking like the item held in the
 * other hand (a bell otherwise), and open its settings. Using it on an existing prop opens its settings.
 */
public class PropWandItem extends Item {

    public PropWandItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        if (!Permissions.isAdmin(player)) {
            player.sendOverlayMessage(Component.literal("Réservé aux organisateurs").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        BlockPos pos = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();
        // On top of the block, or against its side (centred in the next block, standing on its floor)
        Vec3 at = face == Direction.UP ? new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5)
                : Vec3.atBottomCenterOf(pos.relative(face));
        ItemStack look = player.getItemInHand(ctx.getHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        float yaw = Math.round((player.getYRot() + 180) / 45f) * 45f;
        TrialPropEntity prop = TrialProps.spawn(level, at, yaw, look);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 1.2f);
        TrialNetwork.openProp(player, prop);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.craftmaster.prop_wand.hint").withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("item.craftmaster.prop_wand.hint2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
