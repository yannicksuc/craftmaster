package fr.lordfinn.craftmaster.trial.item;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.trial.Judging;
import fr.lordfinn.craftmaster.trial.Stopwatch;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The judge's stopwatch (organisers): use it on a contestant to start their stopwatch, again to stop it (the time
 * is written on the results sheet of the round). Used in the air: every contestant with start / stop buttons.
 */
public class StopwatchItem extends Item {

    public StopwatchItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof ServerPlayer runner) || !(player instanceof ServerPlayer judge)) return InteractionResult.PASS;
        if (!Permissions.isAdmin(judge)) return InteractionResult.PASS;
        String name = Judging.contestant(runner) != null ? Judging.contestant(runner) : runner.getGameProfile().name();
        String known = Stopwatch.SHARED.find(name);
        boolean running = known != null && Stopwatch.SHARED.isRunning(known);
        ActionExecutor.Result r = ActionExecutor.execute(judge, List.of(Map.of("type", running ? "stopwatch.stop" : "stopwatch.start", "player", name)));
        for (String line : r.lines()) judge.sendSystemMessage(Component.literal(line).withStyle(r.ok() ? ChatFormatting.GREEN : ChatFormatting.RED));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer judge)) return InteractionResult.SUCCESS;
        if (!Permissions.isAdmin(judge)) return InteractionResult.PASS;
        List<String> cast = Judging.contestants();
        if (cast.isEmpty()) {
            judge.sendSystemMessage(Component.literal("Aucun candidat dans la saison active.").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS_SERVER;
        }
        judge.sendSystemMessage(Component.literal("⏱ Chronos des candidats").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        long now = System.currentTimeMillis();
        for (String p : cast) {
            long t = Stopwatch.SHARED.peek(p, now);
            long last = Stopwatch.SHARED.last(p);
            MutableComponent line = Component.literal("  " + p + "  ").withStyle(ChatFormatting.WHITE);
            line.append(button("[▶ départ]", "/craftMasterChrono start " + Judging.quote(p), "Lancer le chrono de " + p, ChatFormatting.GREEN));
            line.append(" ");
            line.append(button("[■ stop]", "/craftMasterChrono stop " + Judging.quote(p), "Arrêter et noter le temps de " + p, ChatFormatting.RED));
            if (t >= 0) line.append(Component.literal("  " + Stopwatch.format(t) + " …").withStyle(ChatFormatting.YELLOW));
            else if (last >= 0) line.append(Component.literal("  " + Stopwatch.format(last)).withStyle(ChatFormatting.GRAY));
            judge.sendSystemMessage(line);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static Component button(String text, String command, String hover, ChatFormatting color) {
        return Component.literal(text).withStyle(s -> s.withColor(color).withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.craftmaster.judge_stopwatch.hint").withStyle(ChatFormatting.GRAY));
    }
}
