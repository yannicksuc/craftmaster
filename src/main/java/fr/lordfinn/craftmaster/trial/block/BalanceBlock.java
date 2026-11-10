package fr.lordfinn.craftmaster.trial.block;

import fr.lordfinn.craftmaster.trial.Judging;
import fr.lordfinn.craftmaster.trial.ResultSheet;
import fr.lordfinn.craftmaster.trial.Weights;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * The scales: use it holding something to weigh what you hold, or empty-handed to weigh what lies on its plate
 * (items thrown on it). The weight comes from {@link Weights}; organisers get buttons to write it down for a
 * contestant on the results sheet.
 */
public class BalanceBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 10, 15);

    public BalanceBlock(Properties props) {
        super(props);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || player.isShiftKeyDown()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level instanceof ServerLevel && player instanceof ServerPlayer sp) {
            double kg = weigh(stack);
            report(sp, pos, kg, stack.getCount() + " × " + stack.getHoverName().getString());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        List<ItemEntity> items = server.getEntitiesOfClass(ItemEntity.class, new AABB(pos).expandTowards(0, 0.75, 0).inflate(0.1));
        if (items.isEmpty()) {
            sp.sendOverlayMessage(Component.literal("⚖ Le plateau est vide : jette des objets dessus, ou utilise-la en tenant un objet")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS_SERVER;
        }
        double kg = 0;
        int count = 0;
        for (ItemEntity e : items) {
            kg += weigh(e.getItem());
            count += e.getItem().getCount();
        }
        report(sp, pos, kg, count + " objet(s) sur le plateau");
        return InteractionResult.SUCCESS_SERVER;
    }

    public static double weigh(ItemStack stack) {
        return Weights.of(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), stack.getItem() instanceof BlockItem);
    }

    private static void report(ServerPlayer player, BlockPos pos, double kg, String what) {
        player.level().playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.4f);
        String text = ResultSheet.format(ResultSheet.Kind.WEIGHT, kg);
        player.sendOverlayMessage(Component.literal("⚖ " + text).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        Judging.offerMeasure(player, Component.literal("⚖ Balance : ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(" (" + what + ")").withStyle(ChatFormatting.GRAY)), kg, ResultSheet.Kind.WEIGHT);
    }
}
