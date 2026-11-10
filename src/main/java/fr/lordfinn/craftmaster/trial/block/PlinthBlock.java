package fr.lordfinn.craftmaster.trial.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Display plinth: a museum pedestal (cream stone, gold trim, red velvet top). Use it holding anything to put that
 * object on show (it floats and turns slowly above the velvet, its name shown above it); empty-handed to take it
 * back. Anyone can use it: contestants present their creations on it.
 */
public class PlinthBlock extends Block implements EntityBlock {

    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 2, 15), Block.box(3, 2, 3, 13, 12, 13), Block.box(1, 12, 1, 15, 14, 15));

    public PlinthBlock(Properties props) {
        super(props);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlinthBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof PlinthBlockEntity plinth)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!plinth.item().isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level instanceof ServerLevel) {
            plinth.setItem(player.isCreative() ? stack.copyWithCount(1) : stack.split(1));
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PlinthBlockEntity plinth) || plinth.item().isEmpty()) return InteractionResult.PASS;
        if (level instanceof ServerLevel) {
            ItemStack taken = plinth.item();
            plinth.setItem(ItemStack.EMPTY);
            if (!player.getInventory().add(taken) && level instanceof ServerLevel sl) player.spawnAtLocation(sl, taken);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    /** The object on show drops when the plinth is broken. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PlinthBlockEntity plinth && !plinth.item().isEmpty())
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, plinth.item());
        return super.playerWillDestroy(level, pos, state, player);
    }
}
