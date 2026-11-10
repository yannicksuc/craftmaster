package fr.lordfinn.craftmaster.trial.item;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.trial.Judging;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Red card (organisers): use it on a contestant to disqualify them from the current round (their line on the
 * results sheet says so and they get 0 when the scale is applied); sneak + use lifts it. Undoable like any action.
 */
public class RedCardItem extends Item {

    public RedCardItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof ServerPlayer culprit) || !(player instanceof ServerPlayer judge) || !Permissions.isAdmin(judge))
            return InteractionResult.PASS;
        String name = Judging.contestant(culprit) != null ? Judging.contestant(culprit) : culprit.getGameProfile().name();
        boolean lift = judge.isShiftKeyDown();
        ActionExecutor.Result r = ActionExecutor.execute(judge, List.of(Map.of("type", "result.dq", "player", name, "dq", !lift)));
        for (String line : r.lines()) judge.sendSystemMessage(Component.literal(line).withStyle(r.ok() ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (r.ok() && !lift && culprit.level() instanceof ServerLevel level)
            level.sendParticles(new DustParticleOptions(0xE02020, 1.5f), culprit.getX(), culprit.getY() + 2.2, culprit.getZ(), 20, 0.3, 0.2, 0.3, 0);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.craftmaster.red_card.hint").withStyle(ChatFormatting.GRAY));
    }
}
