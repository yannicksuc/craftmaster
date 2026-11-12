package fr.lordfinn.craftmaster.toy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A minigolf hole: a block of green with a cup in the middle, set into the ground. A toy that rolls in slowly
 * enough (a golf ball, but an egg or a ball too) drops into it and the strokes are counted for whoever played it.
 */
public class GolfHoleBlock extends Block {

    /** The cup: 6 px wide (radius in blocks around the centre), 10 px deep. */
    public static final double CUP_RADIUS = 3.5 / 16, CUP_BOTTOM = 6 / 16.0;
    private static final VoxelShape SHAPE = Shapes.join(Shapes.block(), Block.box(5, 6, 5, 11, 16, 11), BooleanOp.ONLY_FIRST);

    public GolfHoleBlock(Properties props) {
        super(props);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp)
            sp.sendOverlayMessage(Component.literal("⛳ Frappe une balle de golf vers ce trou avec le club (accroupi : putt)").withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS;
    }
}
