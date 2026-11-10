package fr.lordfinn.craftmaster.trial.block;

import fr.lordfinn.craftmaster.Permissions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Prop and costume trunk: a red velvet trunk with gold corners. Opening it shows its stock, and every item taken
 * is a copy (the trunk never empties); putting an item back in it gives it back to the trunk (it disappears).
 * An organiser sneaking + opening it edits the stock itself (a normal chest). A new trunk comes with costumes.
 */
public class CostumeChestBlock extends Block implements EntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public CostumeChestBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CostumeChestBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof CostumeChestBlockEntity chest)) return InteractionResult.PASS;
        boolean edit = sp.isShiftKeyDown() && Permissions.isAdmin(sp);
        level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.8f, 0.9f);
        if (edit) {
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, chest.stock()),
                    Component.translatable("container.craftmaster.costume_chest.edit")));
            sp.sendOverlayMessage(Component.literal("Stock de la malle : ce que tu y ranges sera proposé (copié) aux joueurs").withStyle(ChatFormatting.GOLD));
        } else {
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, chest.copies()),
                    Component.translatable("container.craftmaster.costume_chest")));
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
