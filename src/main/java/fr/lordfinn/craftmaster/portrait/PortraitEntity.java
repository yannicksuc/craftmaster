package fr.lordfinn.craftmaster.portrait;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * A hanging portrait ({@code craftmaster:portrait}): a golden frame with a posed player in it, hanging by a
 * string from a ceiling block. The entity's position is the hook, under the ceiling block; everything else
 * (frame, figure, sway, column changes) is drawn by the client from the synced data below.
 * <p>
 * Settings (season = gallery, followed rank or fixed player, displayed name, skin URL, pose, string length) are
 * chosen by organisers; what it shows (player, rank, published points) is set by {@link Portraits}.
 */
public class PortraitEntity extends Entity {

    // Settings
    private static final EntityDataAccessor<Integer> SEASON = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.INT);
    /** Followed rank, 0 when the portrait follows a fixed player. */
    private static final EntityDataAccessor<Integer> RANK = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> FIXED_PLAYER = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DISPLAY_NAME = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN_URL = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> POSE = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.STRING);
    /** String length in blocks; negative = automatic (a gentle variation of its own). */
    private static final EntityDataAccessor<Float> HANG = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> FACING = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.INT);
    /** Exact angle (degrees) the picture is turned by from its facing: set at placement from the look, editable. */
    private static final EntityDataAccessor<Float> TURN = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.FLOAT);
    /** "Antichamber" window look: the figure is only seen through the opening, in a box behind the frame. */
    private static final EntityDataAccessor<Boolean> WINDOW = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.BOOLEAN);
    /** Window look: depth of the room behind the opening (px) and its size in percent of the opening. */
    private static final EntityDataAccessor<Integer> WINDOW_DEPTH = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WINDOW_SIZE = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.INT);
    public static final int DEFAULT_WINDOW_DEPTH = 16, MIN_WINDOW_DEPTH = 4, MAX_WINDOW_DEPTH = 96;
    public static final int DEFAULT_WINDOW_SIZE = 140, MIN_WINDOW_SIZE = 100, MAX_WINDOW_SIZE = 200;
    // What it shows
    private static final EntityDataAccessor<String> SHOWN_PLAYER = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SHOWN_RANK = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POINTS = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.INT);
    /** The season is hidden: no name, no skin, no points until the reveal. */
    private static final EntityDataAccessor<Boolean> ANONYMOUS = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.BOOLEAN);
    // Column change (see PortraitAnimation) and reveal highlight, both in game ticks
    private static final EntityDataAccessor<BlockPos> ANIM_FROM = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<BlockPos> ANIM_TO = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> ANIM_PLANE = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> ANIM_START = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> ANIM_SLIDE = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.INT);
    /** Raised or lowered (hoist): the string length it starts from, the game tick it starts and how long it takes. */
    private static final EntityDataAccessor<Float> HOIST_FROM = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> HOIST_START = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> HOIST_TICKS = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> HIGHLIGHT_UNTIL = SynchedEntityData.defineId(PortraitEntity.class, EntityDataSerializers.LONG);

    /** Default string length and how much the automatic one varies from a portrait to the next (blocks). */
    public static final double AUTO_HANG = 0.8, AUTO_HANG_VARIATION = 0.25;
    public static final double MAX_HANG = 16;

    /** The block the portrait hangs from (the hook is under its bottom face). */
    private BlockPos ceiling = BlockPos.ZERO;

    public PortraitEntity(EntityType<? extends PortraitEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** A new portrait hanging under {@code ceiling}, its picture facing {@code facing}. */
    public static PortraitEntity create(ServerLevel level, BlockPos ceiling, Direction facing, int season, int rank) {
        PortraitEntity p = new PortraitEntity(ModContent.PORTRAIT, level);
        p.setFacing(facing);
        p.entityData.set(SEASON, season);
        p.entityData.set(RANK, Math.max(0, rank));
        p.entityData.set(SHOWN_RANK, Math.max(0, rank));
        p.hangAt(ceiling);
        // Never the basic pose: one of the lively ones, at random
        p.entityData.set(POSE, PoseLibrary.varied(java.util.concurrent.ThreadLocalRandom.current().nextLong()));
        return p;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(SEASON, 1);
        b.define(RANK, 1);
        b.define(FIXED_PLAYER, "");
        b.define(DISPLAY_NAME, "");
        b.define(SKIN_URL, "");
        b.define(POSE, PoseLibrary.DEFAULT);
        b.define(HANG, -1f);
        b.define(FACING, Direction.SOUTH.get2DDataValue());
        b.define(TURN, 0f);
        b.define(WINDOW, false);
        b.define(WINDOW_DEPTH, DEFAULT_WINDOW_DEPTH);
        b.define(WINDOW_SIZE, DEFAULT_WINDOW_SIZE);
        b.define(SHOWN_PLAYER, "");
        b.define(SHOWN_RANK, 0);
        b.define(POINTS, 0);
        b.define(ANONYMOUS, false);
        b.define(ANIM_FROM, BlockPos.ZERO);
        b.define(ANIM_TO, BlockPos.ZERO);
        b.define(ANIM_PLANE, 0);
        b.define(ANIM_START, Long.MIN_VALUE / 2);
        b.define(ANIM_SLIDE, 0);
        b.define(HOIST_FROM, 0f);
        b.define(HOIST_START, Long.MIN_VALUE / 2);
        b.define(HOIST_TICKS, 0);
        b.define(HIGHLIGHT_UNTIL, Long.MIN_VALUE / 2);
    }

    // ---------------------------------------------------------------- position

    /** The hook's position for a ceiling block: the middle of its bottom face. */
    public static Vec3 hook(BlockPos ceiling) {
        return new Vec3(ceiling.getX() + 0.5, ceiling.getY(), ceiling.getZ() + 0.5);
    }

    public BlockPos ceiling() {
        return ceiling;
    }

    /** Moves the hook under another ceiling block (no animation). */
    public void hangAt(BlockPos ceiling) {
        this.ceiling = ceiling.immutable();
        snapTo(hook(ceiling), getFacing().toYRot(), 0);
        entityData.set(ANIM_TO, this.ceiling);
    }

    /**
     * Moves to another ceiling block with the rearrangement animation: the clients draw the move from
     * {@code from} (the current column) starting at game tick {@code start}.
     */
    public void moveTo(BlockPos to, int plane, long start, int slideTicks) {
        entityData.set(ANIM_FROM, ceiling);
        entityData.set(ANIM_PLANE, plane);
        entityData.set(ANIM_SLIDE, slideTicks);
        entityData.set(ANIM_START, start);
        hangAt(to);
    }

    public Direction getFacing() {
        return Direction.from2DDataValue(entityData.get(FACING));
    }

    /** Degrees the picture is turned by from its facing (-180 to 180, positive = like the player turning right). */
    public float turn() {
        return entityData.get(TURN);
    }

    public void setTurn(float degrees) {
        entityData.set(TURN, net.minecraft.util.Mth.wrapDegrees(degrees));
        setYRot(yaw());
        refreshBox();
    }

    /** The exact direction the picture faces (degrees, Minecraft yaw): facing + turn. */
    public float yaw() {
        return getFacing().toYRot() + turn();
    }

    /** Turns the picture to face someone looking with {@code lookYaw}, keeping the facing closest to it. */
    public void faceLook(float lookYaw) {
        setFacing(Direction.fromYRot(lookYaw).getOpposite());
        setTurn(net.minecraft.util.Mth.wrapDegrees(lookYaw + 180 - getFacing().toYRot()));
    }

    public void setFacing(Direction facing) {
        if (facing.getAxis().isVertical()) facing = Direction.SOUTH;
        entityData.set(FACING, facing.get2DDataValue());
        setYRot(facing.toYRot());
        refreshBox();
    }

    // ---------------------------------------------------------------- settings

    public int season() { return entityData.get(SEASON); }
    public int rank() { return entityData.get(RANK); }
    public String fixedPlayer() { return entityData.get(FIXED_PLAYER); }
    public boolean followsRank() { return fixedPlayer().isEmpty(); }
    public String displayName() { return entityData.get(DISPLAY_NAME); }
    public String skinUrl() { return entityData.get(SKIN_URL); }
    public String poseName() { return entityData.get(POSE); }
    /** The string length as set (negative: automatic). */
    public float hangSetting() { return entityData.get(HANG); }
    public boolean window() { return entityData.get(WINDOW); }
    public int windowDepth() { return entityData.get(WINDOW_DEPTH); }
    public int windowSize() { return entityData.get(WINDOW_SIZE); }

    public void setWindowDepth(int px) {
        entityData.set(WINDOW_DEPTH, Math.max(MIN_WINDOW_DEPTH, Math.min(MAX_WINDOW_DEPTH, px)));
    }

    public void setWindowSize(int percent) {
        entityData.set(WINDOW_SIZE, Math.max(MIN_WINDOW_SIZE, Math.min(MAX_WINDOW_SIZE, percent)));
    }

    public void setWindow(boolean window) {
        entityData.set(WINDOW, window);
        refreshBox();
    }

    public void setSeason(int season) { entityData.set(SEASON, season); }

    /** Follows rank N (N >= 1). */
    public void setRank(int rank) {
        entityData.set(RANK, Math.max(1, rank));
        entityData.set(FIXED_PLAYER, "");
    }

    /** Follows a fixed player (empty: back to following a rank). */
    public void setFixedPlayer(String player) {
        entityData.set(FIXED_PLAYER, player == null ? "" : player.trim());
        if (followsRank() && rank() < 1) entityData.set(RANK, 1);
    }

    public void setDisplayName(String name) { entityData.set(DISPLAY_NAME, name == null ? "" : name.trim()); }
    public void setSkinUrl(String url) { entityData.set(SKIN_URL, url == null ? "" : url.trim()); }

    public void setPoseName(String pose) {
        entityData.set(POSE, Pose.normalizeName(pose).isEmpty() ? PoseLibrary.DEFAULT : Pose.normalizeName(pose));
        refreshBox();
    }

    public void setHang(float blocks) {
        entityData.set(HANG, blocks < 0 ? -1f : (float) Math.min(MAX_HANG, blocks));
        refreshBox();
    }

    /** String length in blocks: the setting, or the automatic one (each portrait differs a little). */
    public double hangLength() {
        return hangFor(hangSetting());
    }

    private double hangFor(float set) {
        if (set >= 0) return set;
        return AUTO_HANG + AUTO_HANG_VARIATION * variation(getUUID().getLeastSignificantBits());
    }

    /** Default hoist speed (blocks per second) when no duration is given. */
    public static final double HOIST_SPEED = 1.0;

    /**
     * Raises or lowers the portrait to a string of {@code blocks} (negative: automatic) in {@code ticks} game ticks
     * (0: at {@link #HOIST_SPEED}), from wherever it is now, even halfway through another hoist.
     *
     * @return the duration in ticks
     */
    public int hoist(float blocks, int ticks) {
        long now = level().getGameTime();
        double from = hangLengthAt(now);
        float target = blocks < 0 ? -1f : (float) Math.min(MAX_HANG, blocks);
        double to = hangFor(target);
        if (ticks <= 0) ticks = (int) Math.max(10, Math.round(Math.abs(to - from) / HOIST_SPEED * 20));
        entityData.set(HOIST_FROM, (float) from);
        entityData.set(HOIST_TICKS, ticks);
        entityData.set(HOIST_START, now);
        setHang(target);
        return ticks;
    }

    /** The string length shown at game tick {@code ticks} (fractional for smooth frames): eased while hoisting. */
    public double hangLengthAt(double ticks) {
        return PortraitAnimation.hoisted(entityData.get(HOIST_FROM), hangLength(), entityData.get(HOIST_START), entityData.get(HOIST_TICKS), ticks);
    }

    /** A stable value in [-1, 1] for each portrait. */
    public static double variation(long seed) {
        long h = seed * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return ((h & 0xFFFF) / 65535.0) * 2 - 1;
    }

    // ---------------------------------------------------------------- shown

    public String shownPlayer() { return entityData.get(SHOWN_PLAYER); }
    public int shownRank() { return entityData.get(SHOWN_RANK); }
    public int points() { return entityData.get(POINTS); }
    public boolean anonymous() { return entityData.get(ANONYMOUS); }

    public void show(String player, int rank, int points, boolean anonymous) {
        entityData.set(SHOWN_PLAYER, player == null || anonymous ? "" : player);
        entityData.set(SHOWN_RANK, Math.max(0, rank));
        entityData.set(POINTS, anonymous ? 0 : points);
        entityData.set(ANONYMOUS, anonymous);
    }

    /** After a rearrangement, a portrait following a rank follows the rank its player now has. */
    void followRankSilently(int rank) {
        if (followsRank() && rank > 0) entityData.set(RANK, rank);
    }

    public BlockPos animFrom() { return entityData.get(ANIM_FROM); }
    public BlockPos animTo() { return entityData.get(ANIM_TO); }
    public int animPlane() { return entityData.get(ANIM_PLANE); }
    public long animStart() { return entityData.get(ANIM_START); }
    public int animSlide() { return entityData.get(ANIM_SLIDE); }
    public long highlightUntil() { return entityData.get(HIGHLIGHT_UNTIL); }

    public void highlight(int ticks) {
        entityData.set(HIGHLIGHT_UNTIL, level().getGameTime() + ticks);
        setGlowingTag(true);
    }

    /** The pose this portrait takes, from the server's or the client's library. */
    public Pose pose() {
        return PoseLibraries.get(level().isClientSide()).get(poseName());
    }

    // ---------------------------------------------------------------- behaviour

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel && isCurrentlyGlowing() && level().getGameTime() > highlightUntil()) setGlowingTag(false);
    }

    /** Organisers open the settings with a right click. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            if (!Permissions.isAdmin(sp)) return InteractionResult.PASS;
            PortraitNetwork.openScreen(sp, this, false);
        }
        return InteractionResult.SUCCESS;
    }

    /** Broken by a hit from a player allowed to build, like a painting; nothing else harms it. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isRemoved() || !(source.getEntity() instanceof Player player) || !player.mayBuild()) return false;
        playSound(SoundEvents.PAINTING_BREAK, 1f, 1f);
        if (!player.isCreative()) spawnAtLocation(level, new ItemStack(ModContent.PORTRAIT_ITEM), (float) -hangLength());
        discard();
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModContent.PORTRAIT_ITEM);
    }

    /** Lit like the frame, not like the ceiling block the hook is in. */
    @Override
    public Vec3 getLightProbePosition(float partialTick) {
        return getPosition(partialTick).add(0, -hangLength() - 1, 0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    // ---------------------------------------------------------------- box

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor == POSE || accessor == HANG || accessor == FACING || accessor == WINDOW || accessor == TURN) refreshBox();
    }

    private void refreshBox() {
        if (entityData == null) return;
        setBoundingBox(makeBoundingBox(position()));
    }

    /** Portrait-space offset (pixels) → world offset from the hook (blocks), for a portrait facing {@code facing}. */
    public static Vec3 toWorld(Vector3d blocks, Direction facing) {
        return toWorld(blocks, facing.toYRot());
    }

    /** The same for an exact yaw (degrees). */
    public static Vec3 toWorld(Vector3d blocks, float yaw) {
        double a = Math.toRadians(-yaw);
        double cos = Math.cos(a), sin = Math.sin(a);
        return new Vec3(blocks.x * cos + blocks.z * sin, blocks.y, -blocks.x * sin + blocks.z * cos);
    }

    /** Around the frame and the figure (not the string), so clicking near the ceiling does not hit it. */
    @Override
    protected AABB makeBoundingBox(Vec3 pos) {
        if (entityData == null) return super.makeBoundingBox(pos);
        Pose pose = pose();
        double[] b = PortraitGeometry.bounds(pose, window());
        Vector3d origin = PortraitGeometry.portraitOrigin(pose, window(), hangLength());
        float yaw = yaw();
        // A turned picture: the box around the rotated corners (all eight, so it covers any angle)
        AABB box = null;
        for (int i = 0; i < 8; i++) {
            Vec3 c = toWorld(new Vector3d(origin).add(b[(i & 1) == 0 ? 0 : 3] / 16, b[(i & 2) == 0 ? 1 : 4] / 16, b[(i & 4) == 0 ? 2 : 5] / 16), yaw);
            AABB one = new AABB(pos.add(c), pos.add(c));
            box = box == null ? one : box.minmax(one);
        }
        return box.inflate(0.02);
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        entityData.set(SEASON, in.getIntOr("Season", 1));
        entityData.set(RANK, in.getIntOr("Rank", 1));
        entityData.set(FIXED_PLAYER, in.getStringOr("FixedPlayer", ""));
        entityData.set(DISPLAY_NAME, in.getStringOr("DisplayName", ""));
        entityData.set(SKIN_URL, in.getStringOr("SkinUrl", ""));
        String pose = in.getStringOr("Pose", PoseLibrary.DEFAULT);
        // Portraits saved in the basic pose get a lively one (stable: from their UUID)
        entityData.set(POSE, pose.equals(PoseLibrary.DEFAULT) ? PoseLibrary.varied(getUUID().getLeastSignificantBits()) : pose);
        entityData.set(HANG, in.getFloatOr("Hang", -1f));
        entityData.set(WINDOW, in.getBooleanOr("Window", false));
        setWindowDepth(in.getIntOr("WindowDepth", DEFAULT_WINDOW_DEPTH));
        setWindowSize(in.getIntOr("WindowSize", DEFAULT_WINDOW_SIZE));
        entityData.set(FACING, Direction.byName(in.getStringOr("Facing", "south")) instanceof Direction d && d.getAxis().isHorizontal()
                ? d.get2DDataValue() : Direction.SOUTH.get2DDataValue());
        entityData.set(TURN, net.minecraft.util.Mth.wrapDegrees(in.getFloatOr("Turn", 0f)));
        entityData.set(SHOWN_PLAYER, in.getStringOr("ShownPlayer", ""));
        entityData.set(SHOWN_RANK, in.getIntOr("ShownRank", 0));
        entityData.set(POINTS, in.getIntOr("Points", 0));
        entityData.set(ANONYMOUS, in.getBooleanOr("Anonymous", false));
        BlockPos c = in.read("Ceiling", BlockPos.CODEC).orElse(BlockPos.containing(getX(), getY(), getZ()));
        hangAt(c);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putInt("Season", season());
        out.putInt("Rank", rank());
        out.putString("FixedPlayer", fixedPlayer());
        out.putString("DisplayName", displayName());
        out.putString("SkinUrl", skinUrl());
        out.putString("Pose", poseName());
        out.putFloat("Hang", hangSetting());
        out.putBoolean("Window", window());
        out.putInt("WindowDepth", windowDepth());
        out.putInt("WindowSize", windowSize());
        out.putString("Facing", getFacing().getSerializedName());
        if (turn() != 0) out.putFloat("Turn", turn());
        out.putString("ShownPlayer", shownPlayer());
        out.putInt("ShownRank", shownRank());
        out.putInt("Points", points());
        out.putBoolean("Anonymous", anonymous());
        out.store("Ceiling", BlockPos.CODEC, ceiling);
    }
}
