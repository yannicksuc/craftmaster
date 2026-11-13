package fr.lordfinn.craftmaster.toy;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.trial.ResultSheet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A target container that counts what lands in it: a bucket, a bin, a mug. Thrown toys, dropped items and the
 * game's snowballs and eggs that fall in are caught (kept inside: the tally counter counts them, a hopper under
 * it takes them out), with who threw them and from how far. Each catch: a sound, a redstone pulse, and for the
 * organisers a line with buttons to write the distance or the count on the results sheet. Comparator: the number
 * of objects inside (up to 15). Empty hand: what is inside; sneaking: empty it.
 */
public class CatchTargetBlock extends Block implements EntityBlock {

    public enum Shape {
        // inner box (px) and wall thickness: bucket 10×10×9, bin 12×12×15, mug 6×6×6 (+ handle)
        BUCKET("seau", false, 3, 13, 9, 1),
        BIN("poubelle", true, 2, 14, 15, 1),
        MUG("tasse", true, 5, 11, 6, 1);

        public final String fr;
        public final boolean female;
        final int from, to, height, wall;

        Shape(String fr, boolean female, int from, int to, int height, int wall) {
            this.fr = fr;
            this.female = female;
            this.from = from;
            this.to = to;
            this.height = height;
            this.wall = wall;
        }
    }

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Shape shape;
    private final VoxelShape outline, collision;

    public CatchTargetBlock(Shape shape, Properties props) {
        super(props);
        this.shape = shape;
        int a = shape.from, b = shape.to, h = shape.height, w = shape.wall;
        this.outline = Block.box(a, 0, a, b, h, b);
        this.collision = Shapes.join(outline, Block.box(a + w, w, a + w, b - w, 16, b - w), BooleanOp.ONLY_FIRST);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false).setValue(FACING, Direction.NORTH));
    }

    public Shape shape() {
        return shape;
    }


    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(POWERED, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CatchTargetBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return outline;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return collision;
    }

    /** The inside of the container, in world coordinates (what falls in there is caught). */
    public AABB inside(BlockPos pos) {
        double a = (shape.from + shape.wall) / 16.0, b = (shape.to - shape.wall) / 16.0;
        return new AABB(pos.getX() + a, pos.getY() + shape.wall / 16.0, pos.getZ() + a,
                pos.getX() + b, pos.getY() + shape.height / 16.0 + 0.05, pos.getZ() + b);
    }

    // ---------------------------------------------------------------- catching

    /** A toy whose centre is inside a target (here or just below its feet) is caught. */
    public static boolean tryCatch(ServerLevel level, ToyEntity toy) {
        BlockPos pos = toy.blockPosition();
        if (!(level.getBlockState(pos).getBlock() instanceof CatchTargetBlock t)) return false;
        if (!t.inside(pos).contains(toy.getX(), toy.getY() + 0.02, toy.getZ())) return false;
        String who = toy.thrower();
        double d = toy.throwDistanceTo(pos.getX() + 0.5, pos.getZ() + 0.5);
        if (!t.caught(level, pos, toy.stack(), who, d)) return false;
        toy.discard();
        return true;
    }

    /** Dropped items (Q) falling in. */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
        if (!(level instanceof ServerLevel sl) || !(entity instanceof ItemEntity item) || item.isRemoved()) return;
        if (!inside(pos).contains(item.position().add(0, 0.05, 0))) return;
        String who = "";
        double d = 0;
        if (item.getOwner() instanceof ServerPlayer p) {
            who = Toys.who(p);
            d = Math.sqrt(p.distanceToSqr(pos.getX() + 0.5, p.getY(), pos.getZ() + 0.5));
        }
        if (caught(sl, pos, item.getItem(), who, d)) item.discard();
    }

    /** The game's snowballs and eggs that hit the container. */
    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!(level instanceof ServerLevel sl) || !(projectile instanceof ItemSupplier supplier)) return;
        BlockPos pos = hit.getBlockPos();
        // Only from above, into the opening
        if (hit.getDirection() != Direction.UP && !inside(pos).inflate(0.05).contains(hit.getLocation())) return;
        String who = "";
        double d = 0;
        if (projectile.getOwner() instanceof ServerPlayer p) {
            who = Toys.who(p);
            d = Math.sqrt(p.distanceToSqr(pos.getX() + 0.5, p.getY(), pos.getZ() + 0.5));
        }
        if (caught(sl, pos, supplier.getItem().copyWithCount(1), who, d)) projectile.discard();
    }

    /** Puts a caught object inside and tells everyone; false if it is full. */
    boolean caught(ServerLevel level, BlockPos pos, ItemStack stack, String who, double distance) {
        if (!(level.getBlockEntity(pos) instanceof CatchTargetBlockEntity be)) return false;
        if (!be.add(stack.copy(), who)) return false;
        String name = Toys.frName(stack);
        level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 1f, shape == Shape.MUG ? 1.6f : 1.1f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + shape.height / 16.0 + 0.1, pos.getZ() + 0.5, 6, 0.15, 0.05, 0.15, 0);
        level.setBlock(pos, level.getBlockState(pos).setValue(POWERED, true), 3);
        level.scheduleTick(pos, this, 6);
        level.updateNeighbourForOutputSignal(pos, this);
        String target = shape.fr;
        if (who.isEmpty()) {
            Toys.tellOrganisers(level.getServer(), Component.literal("◎ " + name + " dans " + (shape.female ? "la " : "le ") + target + " (" + be.count() + " dedans)").withStyle(ChatFormatting.GOLD));
            return true;
        }
        int n = Toys.TALLY.caught(who, target, distance);
        String far = distance >= 1 ? ", lancé de " + ToyPhysics.blocksText(distance) : "";
        ServerPlayer sp = level.getServer().getPlayerList().getPlayerByName(who);
        if (sp != null) sp.sendOverlayMessage(Component.literal("◎ Dans " + (shape.female ? "la " : "le ") + target + " ! (" + (distance >= 1 ? ToyPhysics.blocks(distance) + " blocs · " : "") + n + ")").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        MutableComponent line = Component.literal("◎ " + who + " : " + name + " dans " + (shape.female ? "la " : "le ") + target + far).withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" (" + n + ")").withStyle(ChatFormatting.GRAY));
        if (distance >= 1) line.append(Toys.noteButton(who, distance, ResultSheet.Kind.DISTANCE, ToyPhysics.blocksText(distance)));
        line.append(Toys.noteButton(who, n, ResultSheet.Kind.COUNT, String.valueOf(n)));
        Toys.tellOrganisers(level.getServer(), line);
        return true;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) level.setBlock(pos, state.setValue(POWERED, false), 3);
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
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof CatchTargetBlockEntity be ? Math.min(15, be.count()) : 0;
    }

    // ---------------------------------------------------------------- hands

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp) || !(level.getBlockEntity(pos) instanceof CatchTargetBlockEntity be))
            return InteractionResult.SUCCESS;
        if (sp.isShiftKeyDown()) {
            if (!Permissions.isAdmin(sp) && be.count() > 0 && !sp.isCreative()) {
                sp.sendOverlayMessage(Component.literal("Seuls les organisateurs vident " + (shape.female ? "la " : "le ") + shape.fr).withStyle(ChatFormatting.GRAY));
                return InteractionResult.SUCCESS;
            }
            int n = be.count();
            for (ItemStack s : be.takeAll()) if (!sp.getInventory().add(s)) sp.spawnAtLocation(sl, s);
            sl.updateNeighbourForOutputSignal(pos, this);
            sl.playSound(null, pos, SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.BLOCKS, 1f, 1f);
            sp.sendOverlayMessage(Component.literal("◎ " + capital(shape.fr) + (shape.female ? " vidée (" : " vidé (") + n + " objet" + (n > 1 ? "s" : "") + ")").withStyle(ChatFormatting.GOLD));
            return InteractionResult.SUCCESS;
        }
        sp.sendSystemMessage(Component.literal("◎ " + capital(shape.fr) + " : " + be.count() + " objet(s)").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(be.summary().isEmpty() ? "" : " — " + be.summary()).withStyle(ChatFormatting.GRAY)));
        return InteractionResult.SUCCESS;
    }

    private static String capital(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        level.updateNeighbourForOutputSignal(pos, this);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CatchTargetBlockEntity be)
            for (ItemStack s : be.takeAll()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
        return super.playerWillDestroy(level, pos, state, player);
    }
}
