package fr.lordfinn.craftmaster.trial.item;

import fr.lordfinn.craftmaster.trial.BoardEntity;
import fr.lordfinn.craftmaster.trial.TrialContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * Canvas, slate or whiteboard to hang: used on the side of a block, the board goes on that wall, its bottom-left
 * corner (as seen from the front) on the block clicked, its default size up and to the right. The size changes
 * with {@code /cm epreuve tableau taille}.
 */
public class BoardItem extends Item {

    private final BoardEntity.Variant variant;

    public BoardItem(BoardEntity.Variant variant, Properties props) {
        super(props);
        this.variant = variant;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Direction face = ctx.getClickedFace();
        if (face.getAxis().isVertical()) return InteractionResult.PASS;
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        int w = variant.defaultWidth, h = variant.defaultHeight;
        Direction right = face.getCounterClockWise();
        BlockPos front = ctx.getClickedPos().relative(face);
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++) {
                BlockPos p = front.relative(right, x).above(y);
                if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                    player.sendOverlayMessage(Component.literal("Pas la place (" + w + "×" + h + " blocs) devant ce mur").withStyle(ChatFormatting.RED));
                    return InteractionResult.FAIL;
                }
            }
        Vec3 center = Vec3.atCenterOf(front)
                .add(right.getStepX() * (w - 1) / 2.0, (h - 1) / 2.0, right.getStepZ() * (w - 1) / 2.0)
                .subtract(face.getStepX() * (0.5 - BoardEntity.DEPTH), 0, face.getStepZ() * (0.5 - BoardEntity.DEPTH));
        BoardEntity board = new BoardEntity(TrialContent.BOARD, level);
        board.setup(variant, w, h, face);
        board.snapTo(center.x, center.y, center.z, face.toYRot(), 0);
        board.setup(variant, w, h, face);
        level.addFreshEntity(board);
        level.playSound(null, front, SoundEvents.PAINTING_PLACE, SoundSource.BLOCKS, 1f, 1f);
        if (!player.isCreative()) ctx.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.craftmaster.board.hint").withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("item.craftmaster.board.hint2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
