package fr.lordfinn.craftmaster.studio;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.block.Shapes4;
import fr.lordfinn.craftmaster.show.Show;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

/**
 * A counter bell on a contestant's desk: "ding!" when a contestant says they are done. The organisers see who rang
 * and how much time was left; it gives a short redstone pulse (like a button) and can be rung once a second.
 */
public class DeskBellBlock extends DecorBlock {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final int PRESS_TICKS = 10;

    public DeskBellBlock(Properties props) {
        super(props, Shapes4.of(new double[]{4, 0, 4, 12, 5, 12}));
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(POWERED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(POWERED)) return InteractionResult.CONSUME;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, PRESS_TICKS);
        level.playSound(null, pos, Studio.DESK_BELL_SOUND.value(), SoundSource.BLOCKS, 1.5f, 1f);
        level.updateNeighborsAt(pos.below(), this);
        String stopped = stopTablets(server, pos);
        // A note flying up from the bell
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 1, 0, 0, 0, 0.6);
        announce(server, player, stopped);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Stops the timer tablets and giant timers within 2 blocks (like Taskmaster's bell); what the first one shows, or null. */
    private static String stopTablets(ServerLevel level, BlockPos pos) {
        String shown = null;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 1, 2))) {
            if (!(level.getBlockEntity(p) instanceof ClockBlockEntity tablet)) continue;
            String s = tablet.bell();
            if (s != null && shown == null) {
                shown = s;
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.01);
            }
        }
        return shown;
    }

    /** "♪ Ensuri a sonné (1:42 restantes)", or "— tablette arrêtée à 25.7", in the organisers' chat. */
    private static void announce(ServerLevel level, Player player, String tablet) {
        int left = Show.timerRemaining(level.getServer());
        Component msg = tablet != null
                ? Component.translatable("message.craftmaster.bell_rung_tablet", player.getName().getString(), tablet).withStyle(ChatFormatting.GOLD)
                : Component.translatable(left >= 0 ? "message.craftmaster.bell_rung_timer" : "message.craftmaster.bell_rung",
                player.getName().getString(), SevenSegment.clock(left)).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers())
            if (Permissions.isAdmin(p) || p == player) p.sendSystemMessage(msg);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(POWERED)) return;
        level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_ALL);
        level.updateNeighborsAt(pos.below(), this);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return state.getValue(POWERED) && dir == Direction.UP ? 15 : 0;
    }
}
