package fr.lordfinn.craftmaster.studio;

import fr.lordfinn.craftmaster.block.Shapes4;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A contestant's desk: the front faces the audience (the player who places it), with a brass plate bearing the
 * contestant's name. Name it with a renamed name tag (the tag is kept), or {@code /craftMasterDesk name}.
 */
public class DeskBlock extends FacingEntityBlock {

    public DeskBlock(Properties props) {
        // Facing north (front toward the audience at north): top, front panel, side panels
        super(props, Shapes4.of(new double[]{0, 13, 0, 16, 16, 16}, new double[]{0, 0, 1, 16, 13, 4},
                new double[]{0, 0, 4, 2, 13, 15}, new double[]{14, 0, 4, 16, 13, 15}), false);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.NAME_TAG) || !stack.has(DataComponents.CUSTOM_NAME)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!player.mayBuild() || !(level.getBlockEntity(pos) instanceof DeskBlockEntity desk)) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            String name = stack.getHoverName().getString();
            desk.setName(name);
            player.sendOverlayMessage(Component.translatable("message.craftmaster.desk_named", name).withStyle(ChatFormatting.GOLD));
            level.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 0.8f, 1.2f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DeskBlockEntity(pos, state);
    }
}
