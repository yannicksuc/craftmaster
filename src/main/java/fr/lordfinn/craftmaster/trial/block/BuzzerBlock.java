package fr.lordfinn.craftmaster.trial.block;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.trial.Buzzers;
import fr.lordfinn.craftmaster.trial.TrialLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;

/**
 * A quiz buzzer (one per dye colour, one colour per contestant): pressed (used or punched), see {@link Buzzers}.
 * The winning buzzer lights up and gives a redstone signal until the reset. An organiser sneaking + using it, or a
 * redstone pulse coming into it (a button, a lever, a judge's pedal), resets all the buzzers.
 */
public class BuzzerBlock extends Block {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Powered from outside (the reset is on the rising edge). */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 4, 13), Block.box(5, 4, 5, 11, 7, 11));

    public BuzzerBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LIT, POWERED);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                   net.minecraft.world.level.redstone.Orientation orientation, boolean moved) {
        if (!(level instanceof ServerLevel server)) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) return;
        level.setBlock(pos, state.setValue(POWERED, powered), 2);
        if (powered) {
            int n = Buzzers.reset(server.getServer());
            TrialLog.add(server.getServer(), "Buzzers remis à zéro (redstone, " + n + ")");
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (sp.isShiftKeyDown() && Permissions.isAdmin(sp)) {
            ActionExecutor.execute(sp, List.of(Map.of("type", "buzzer.reset")));
            sp.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Buzzers remis à zéro").withStyle(net.minecraft.ChatFormatting.GREEN));
            return InteractionResult.SUCCESS_SERVER;
        }
        Buzzers.press(server, pos, state, sp);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && !sp.isCreative()) Buzzers.press(server, pos, state, sp);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(LIT) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(LIT) && direction == Direction.UP ? 15 : 0;
    }
}
