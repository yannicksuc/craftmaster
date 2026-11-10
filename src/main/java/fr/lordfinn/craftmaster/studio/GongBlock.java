package fr.lordfinn.craftmaster.studio;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.block.Shapes4;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Map;

/**
 * The end-of-task gong: a golden disc hanging in a wooden frame. Right click (or a redstone pulse) strikes it: a
 * deep gong heard across the studio and the disc swings. An organiser who strikes it sneaking also ends the round
 * in progress (same as {@code /craftMasterManche end}, undoable).
 */
public class GongBlock extends FacingEntityBlock {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** Block event id of a strike (the clients start the swing). */
    static final int STRIKE = 1;

    public GongBlock(Properties props) {
        // Facing north (the disc's face toward north): two posts, the top beam, the disc
        super(props, Shapes4.of(new double[]{0, 0, 6, 16, 16, 10}), false);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return super.getStateForPlacement(ctx).setValue(POWERED, ctx.getLevel().hasNeighborSignal(ctx.getClickedPos()));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        strike(server, pos, player);
        if (player.isShiftKeyDown() && player instanceof ServerPlayer sp && Permissions.isAdmin(sp)) endRound(sp);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean moving) {
        if (!(level instanceof ServerLevel server)) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) return;
        level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
        if (powered) strike(server, pos, null);
    }

    public static void strike(ServerLevel level, BlockPos pos, Player by) {
        level.playSound(null, pos, Studio.GONG_SOUND.value(), SoundSource.BLOCKS, 4f, 1f);
        level.blockEvent(pos, level.getBlockState(pos).getBlock(), STRIKE, 0);
        level.gameEvent(by, GameEvent.BLOCK_CHANGE, pos);
    }

    /** Ends the round in progress through the action executor (so it is undoable and logged). */
    private static void endRound(ServerPlayer admin) {
        if (CraftMaster.data() == null || CraftMaster.data().currentManche() == null) {
            admin.sendOverlayMessage(Component.translatable("message.craftmaster.gong_no_round").withStyle(ChatFormatting.GRAY));
            return;
        }
        ActionExecutor.Result r = ActionExecutor.execute(admin, List.of(Map.of("type", "manche.end")));
        for (String line : r.lines())
            admin.sendSystemMessage(Component.literal(line).withStyle(r.ok() ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        if (id == STRIKE && level.getBlockEntity(pos) instanceof GongBlockEntity gong) {
            gong.struck(level.getGameTime());
            return true;
        }
        return super.triggerEvent(state, level, pos, id, param);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GongBlockEntity(pos, state);
    }
}
