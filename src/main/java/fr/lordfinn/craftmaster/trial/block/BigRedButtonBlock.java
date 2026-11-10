package fr.lordfinn.craftmaster.trial.block;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.trial.Judging;
import fr.lordfinn.craftmaster.trial.TrialLog;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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

import java.util.HashMap;
import java.util.Map;

/**
 * The big red button: a gold pedestal with a red dome. Pressed (used or punched), it goes down for a second,
 * gives a redstone signal (like a button, strongly through the block under it), clicks loudly, and the organisers
 * are told who pressed it (the first one since the last reset is marked as such, see the trial log).
 */
public class BigRedButtonBlock extends Block {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final int PRESS_TICKS = 20;

    private static final VoxelShape UP = Shapes.or(Block.box(2, 0, 2, 14, 9, 14), Block.box(4, 9, 4, 12, 13, 12));
    private static final VoxelShape DOWN = Shapes.or(Block.box(2, 0, 2, 14, 9, 14), Block.box(4, 9, 4, 12, 10, 12));

    /** First presser of each button since the last reset (block position → player). */
    private static final Map<BlockPos, String> FIRST = new HashMap<>();

    public BigRedButtonBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(POWERED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(POWERED) ? DOWN : UP;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) press(server, pos, state, sp);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && !sp.isCreative()) press(server, pos, state, sp);
    }

    private void press(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (state.getValue(POWERED)) return;
        level.setBlock(pos, state.setValue(POWERED, true), 3);
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.below(), this);
        level.scheduleTick(pos, this, PRESS_TICKS);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1f, 0.6f);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1f, 0.5f);
        String name = Judging.contestant(player) != null ? Judging.contestant(player) : player.getGameProfile().name();
        boolean first = !FIRST.containsKey(pos);
        if (first) FIRST.put(pos.immutable(), name);
        String where = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        TrialLog.add(level.getServer(), "Gros bouton rouge (" + where + ") : " + name + (first ? " — premier" : ""));
        player.sendOverlayMessage(Component.literal("Clac !").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers())
            if (Permissions.isAdmin(p) && p != player)
                p.sendSystemMessage(Component.literal("🔴 " + name + " a appuyé sur le gros bouton rouge" + (first ? " (premier)" : ""))
                        .withStyle(first ? ChatFormatting.GOLD : ChatFormatting.GRAY));
    }

    /** Forgets who pressed first (the trial reset). */
    public static void resetFirsts() {
        FIRST.clear();
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(POWERED)) return;
        level.setBlock(pos, state.setValue(POWERED, false), 3);
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.below(), this);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.6f, 0.6f);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) && direction == Direction.UP ? 15 : 0;
    }
}
