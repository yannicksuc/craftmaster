package fr.lordfinn.craftmaster.task;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

/**
 * The sealed envelope: right-click opens it (animated opening, then the card), an opened one is read again.
 * Crouching and using it on the top of a block lays it there ({@link EnvelopeBlock}), to be picked up later.
 */
public class EnvelopeItem extends Item {

    public EnvelopeItem(Properties properties) {
        super(properties);
    }

    public static TaskComponents.Envelope envelope(ItemStack stack) {
        TaskComponents.Envelope e = stack.get(TaskComponents.ENVELOPE);
        return e == null ? TaskComponents.Envelope.BLANK : e;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer sp) {
            ItemStack stack = player.getItemInHand(hand);
            Tasks.openEnvelope(sp, envelope(stack), opened -> TaskComponents.setEnvelope(stack, opened));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null || !player.isShiftKeyDown() || ctx.getClickedFace() != Direction.UP)
            return use(ctx.getLevel(), player, ctx.getHand());
        // Laid on the table
        BlockPlaceContext place = new BlockPlaceContext(ctx);
        BlockPos pos = place.getClickedPos();
        Level level = ctx.getLevel();
        if (!place.canPlace() || !player.mayBuild()) return InteractionResult.FAIL;
        BlockState state = TaskContent.ENVELOPE_BLOCK.getStateForPlacement(place);
        if (state == null || !state.canSurvive(level, pos)) return InteractionResult.FAIL;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        ItemStack stack = ctx.getItemInHand();
        TaskComponents.Envelope env = envelope(stack);
        server.setBlock(pos, EnvelopeBlock.stateFor(state, env), Block.UPDATE_ALL);
        if (server.getBlockEntity(pos) instanceof EnvelopeBlockEntity be) be.setEnvelope(env);
        server.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8f, 0.9f);
        if (!player.isCreative()) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        TaskComponents.Envelope e = stack.get(TaskComponents.ENVELOPE);
        return Component.translatable(e != null && e.opened() ? "item.craftmaster.envelope.opened" : "item.craftmaster.envelope");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        TaskComponents.Envelope e = envelope(stack);
        if (!e.to().isBlank()) out.accept(Component.translatable("item.craftmaster.envelope.to", e.to()).withStyle(ChatFormatting.GOLD));
        if (e.linked() && !e.label().isBlank()) out.accept(Component.translatable("item.craftmaster.envelope.round", e.label()).withStyle(ChatFormatting.YELLOW));
        else if (!e.linked() && !e.title().isBlank() && e.opened()) out.accept(Component.literal(e.title()).withStyle(ChatFormatting.YELLOW));
        out.accept(Component.translatable("item.craftmaster.envelope.wax", Component.translatable("wax.craftmaster." + WaxColor.of(e.wax()).id()))
                .withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable(e.opened() ? "item.craftmaster.envelope.hint_open" : "item.craftmaster.envelope.hint").withStyle(ChatFormatting.DARK_GRAY));
        out.accept(Component.translatable("item.craftmaster.envelope.hint_place").withStyle(ChatFormatting.DARK_GRAY));
    }
}
