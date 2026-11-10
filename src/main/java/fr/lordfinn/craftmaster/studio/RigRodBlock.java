package fr.lordfinn.craftmaster.studio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * LordFinn's tripod rod, grown into a rig: rods link like fences, but on all 6 sides, to build towers, beams,
 * corners, T and X junctions. A rod links to another rod (both ways), to the top of a tripod below it, to a light
 * hung on it (studio light, softbox, spotlight) and, when placed against it, to a wall or a ceiling (a plate).
 * Sneak while placing it: it only links to the block it was placed against. The logic is in {@link RigLinks}; the
 * look (a multipart of the rod's own pieces, scripts/gen_furniture.py) draws {@link RigLinks#arms} and a clamp at
 * the junctions.
 */
public class RigRodBlock extends Block {

    private static final Direction[] SIDES = Direction.values(); // DOWN, UP, NORTH, SOUTH, WEST, EAST: RigLinks' order
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        // 4 px thick (the caps' size): easier to aim at than the 2 px tube
        VoxelShape[] arm = {
                Block.box(6, 0, 6, 10, 10, 10), Block.box(6, 6, 6, 10, 16, 10),
                Block.box(6, 6, 0, 10, 10, 10), Block.box(6, 6, 6, 10, 10, 16),
                Block.box(0, 6, 6, 10, 10, 10), Block.box(6, 6, 6, 16, 10, 10)};
        for (int links = 0; links < 64; links++) {
            VoxelShape s = Shapes.empty();
            int arms = RigLinks.arms(links);
            for (int i = 0; i < 6; i++) if (RigLinks.has(arms, i)) s = Shapes.or(s, arm[i]);
            SHAPES[links] = s.optimize();
        }
    }

    public RigRodBlock(Properties props) {
        super(props);
        BlockState s = stateDefinition.any();
        for (Direction d : SIDES) s = s.setValue(prop(d), false);
        registerDefaultState(s);
    }

    public static BooleanProperty prop(Direction d) {
        return PipeBlock.PROPERTY_BY_DIRECTION.get(d);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        for (Direction d : SIDES) b.add(prop(d));
    }

    public static int links(BlockState state) {
        int m = 0;
        for (Direction d : SIDES) if (state.getValue(prop(d))) m |= RigLinks.bit(d.ordinal());
        return m;
    }

    private BlockState withLinks(int links) {
        BlockState s = defaultBlockState();
        for (Direction d : SIDES) s = s.setValue(prop(d), RigLinks.has(links, d.ordinal()));
        return s;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        LevelReader level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        int rods = 0, solids = 0;
        for (Direction d : SIDES) {
            BlockPos n = pos.relative(d);
            BlockState ns = level.getBlockState(n);
            if (ns.getBlock() instanceof RigRodBlock || holds(ns, d)) rods |= RigLinks.bit(d.ordinal());
            else if (ns.isFaceSturdy(level, n, d.getOpposite())) solids |= RigLinks.bit(d.ordinal());
        }
        // Against a block (not replacing grass or snow in place): the side of that block
        int clicked = ctx.replacingClickedOnBlock() ? -1 : ctx.getClickedFace().getOpposite().ordinal();
        return withLinks(RigLinks.placement(rods, solids, clicked, ctx.isSecondaryUseActive()));
    }

    /** Something on side {@code d} of a rod that the rod always links to: a tripod's top, a light hung on the rod. */
    static boolean holds(BlockState n, Direction d) {
        if (d == Direction.DOWN && n.is(Furniture.TRIPOD)) return true;
        if (n.getBlock() instanceof FurnitureBlock) return FurnitureBlock.support(n) == d.getOpposite();
        if (n.getBlock() instanceof SpotlightBlock)
            return d == (n.getValue(SpotlightBlock.HANGING) ? Direction.DOWN : Direction.UP);
        return false;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction d,
                                     BlockPos neighborPos, BlockState n, RandomSource random) {
        boolean link;
        if (n.getBlock() instanceof RigRodBlock) link = n.getValue(prop(d.getOpposite())); // links are mutual
        else if (holds(n, d)) link = true;
        else link = state.getValue(prop(d)) && n.isFaceSturdy(level, neighborPos, d.getOpposite()); // a plate stays while its wall does
        return state.setValue(prop(d), link);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[links(state)];
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        int times = switch (rot) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
        return withLinks(RigLinks.rotateY(links(state), times));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return switch (mirror) {
            case NONE -> state;
            case LEFT_RIGHT -> withLinks(RigLinks.mirror(links(state), true));
            case FRONT_BACK -> withLinks(RigLinks.mirror(links(state), false));
        };
    }
}
