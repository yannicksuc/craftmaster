package fr.lordfinn.craftmaster.studio;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A piece of LordFinn's furniture that is placed like on the server: turned in steps of 45° toward the player,
 * on the floor or (when {@code anyFace}) on a wall or the ceiling. {@code tilts} > 1: right-click changes the
 * variant (the studio lights' angle), sneak + right-click turns it by 45°. The block model is empty, the
 * furniture is drawn by {@link FurnitureBlockEntity}.
 */
public class FurnitureBlock extends BaseEntityBlock {

    // Minecraft 26.2 blocks must give a codec (26.3 dropped the requirement)
    //? if <26.3 {
    /*@Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }
    *///?}

    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    /** The wall it hangs on is behind it (FACE = WALL only). */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Where it faces on the floor or ceiling: yaw = ROTATION × 45° (0 = south, like the game's yaw). */
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 7);
    /** Variant (1 = first); blocks with a single look stay at 1. */
    public static final IntegerProperty TILT = IntegerProperty.create("tilt", 1, 5);

    private final int tilts;
    private final boolean anyFace;
    /** On a wall, sneak + right-click turns it by 45° in place (studio light) instead of to the next wall. */
    private boolean wallTurnsInPlace;
    private final VoxelShape floor, wall, ceiling;

    public FurnitureBlock(Properties props, int tilts, boolean anyFace, VoxelShape floor, VoxelShape wall, VoxelShape ceiling) {
        super(props);
        this.tilts = tilts;
        this.anyFace = anyFace;
        this.floor = floor;
        this.wall = wall;
        this.ceiling = ceiling;
        registerDefaultState(stateDefinition.any().setValue(FACE, AttachFace.FLOOR).setValue(FACING, Direction.NORTH)
                .setValue(ROTATION, 0).setValue(TILT, Math.min(tilts, (tilts + 1) / 2)));
    }

    public FurnitureBlock turnsInPlaceOnWalls() {
        wallTurnsInPlace = true;
        return this;
    }

    public int tilts() {
        return tilts;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACE, FACING, ROTATION, TILT);
    }

    /** The side of the block it is fixed to: below on the floor, above on the ceiling, behind it on a wall. */
    public static Direction support(BlockState state) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> Direction.DOWN;
            case CEILING -> Direction.UP;
            case WALL -> state.getValue(FACING).getOpposite();
        };
    }

    /** Fixed on the side of a rig rod: it stands on the rod's arm (see client FurnitureRenderer). */
    public static boolean onRodSide(BlockGetter level, BlockPos pos, BlockState state) {
        return state.getValue(FACE) == AttachFace.WALL
                && level.getBlockState(pos.relative(support(state))).getBlock() instanceof RigRodBlock;
    }

    /** The yaw a piece faces when it was placed by a player looking with {@code playerYaw}: toward that player. */
    public static int rotationFacing(float playerYaw) {
        return Math.floorMod(Math.round((playerYaw + 180) / 45f), 8);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        BlockState s = defaultBlockState().setValue(ROTATION, rotationFacing(ctx.getRotation()));
        if (!anyFace || face == Direction.UP) return s.setValue(FACE, AttachFace.FLOOR);
        if (face == Direction.DOWN) return s.setValue(FACE, AttachFace.CEILING);
        return s.setValue(FACE, AttachFace.WALL).setValue(FACING, face);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (tilts <= 1 || !player.mayBuild()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockState next;
        Component msg;
        if (player.isShiftKeyDown()) {
            // On a wall the softbox turns to the next wall (as before); on a rod's side, or a studio light on its
            // wall bracket, it turns by 45° where it is
            next = state.getValue(FACE) == AttachFace.WALL && !wallTurnsInPlace && !onRodSide(level, pos, state)
                    ? state.setValue(FACING, state.getValue(FACING).getClockWise())
                    : state.setValue(ROTATION, (state.getValue(ROTATION) + 1) % 8);
            msg = Component.translatable("message.craftmaster.furniture_turned");
        } else {
            int tilt = state.getValue(TILT) % tilts + 1;
            next = state.setValue(TILT, tilt);
            msg = Component.translatable("message.craftmaster.furniture_tilt", tilt, tilts);
        }
        level.setBlock(pos, next, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.LANTERN_PLACE, SoundSource.BLOCKS, 0.6f, 1.4f);
        player.sendOverlayMessage(msg.copy().withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> floor;
            case CEILING -> ceiling;
            case WALL -> wall;
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FurnitureBlockEntity(pos, state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        int steps = switch (rot) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 2;
            case CLOCKWISE_180 -> 4;
            case COUNTERCLOCKWISE_90 -> 6;
        };
        return state.setValue(FACING, rot.rotate(state.getValue(FACING))).setValue(ROTATION, (state.getValue(ROTATION) + steps) % 8);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
