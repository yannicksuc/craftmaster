package fr.lordfinn.craftmaster.studio;

import fr.lordfinn.craftmaster.block.Shapes4;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The prize table: a table draped in red velvet where up to four prizes lie on show (one per quarter of the top).
 * On the top, right click with an item: puts one down where you click; empty hand: takes back the one under the
 * cursor. On the drape (the sides): the shelf hidden under it, 27 slots. Tables side by side join into one long
 * table ({@link SideJoins}). Breaking the table drops everything.
 */
public class PrizeTableBlock extends JoiningBlock {

    public PrizeTableBlock(Properties props) {
        super(props, Shapes4.of(new double[]{0, 0, 0, 16, 16, 16}));
    }

    /** A click on a side of the drape (not the top): the hidden shelf. */
    private static InteractionResult openShelf(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof PrizeTableBlockEntity table)) return InteractionResult.PASS;
        table.storage().open(player, Component.translatable("container.craftmaster.prize_table"));
        return InteractionResult.SUCCESS;
    }

    /** Which quarter of the top a click is on (0..3, see {@link PrizeTableBlockEntity#slotOffset}). */
    static int slotAt(BlockPos pos, Vec3 hit) {
        double x = hit.x - pos.getX(), z = hit.z - pos.getZ();
        return (x >= 0.5 ? 1 : 0) + (z >= 0.5 ? 2 : 0);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hit.getDirection() != Direction.UP) return openShelf(level, pos, player);
        if (stack.isEmpty() || !player.mayBuild() || !(level.getBlockEntity(pos) instanceof PrizeTableBlockEntity table))
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        int slot = table.freeSlot(slotAt(pos, hit.getLocation()));
        if (slot < 0) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) {
            table.put(slot, stack.copyWithCount(1));
            if (!player.isCreative()) stack.shrink(1);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (hit.getDirection() != Direction.UP) return openShelf(level, pos, player);
        if (!player.mayBuild() || !(level.getBlockEntity(pos) instanceof PrizeTableBlockEntity table)) return InteractionResult.PASS;
        int slot = table.takenSlot(slotAt(pos, hit.getLocation()));
        if (slot < 0) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            ItemStack taken = table.take(slot);
            if (!player.getInventory().add(taken))
                net.minecraft.world.Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), taken);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PrizeTableBlockEntity(pos, state);
    }
}
