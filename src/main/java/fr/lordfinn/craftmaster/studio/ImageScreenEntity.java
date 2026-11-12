package fr.lordfinn.craftmaster.studio;

import fr.lordfinn.craftmaster.Permissions;
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

/**
 * Studio screen ({@code craftmaster:studio_screen}): a flat screen on a wall, several blocks wide, showing an image
 * downloaded from a URL by every player's game (see client/studio/ScreenImages), or a video (YouTube, Twitch, MP4...)
 * played by WaterMedia when the players have it (client/studio/ScreenVideos). Replaces the Paper plugin's
 * {@code /image} (ImageFrame). The entity's position is the centre of the picture; its size is in blocks.
 * Without an image (or while it loads, or if it cannot be shown) the screen shows the CraftMaster test card.
 *
 * <p>Video playback is the same for everyone: the server keeps the game tick the video started at ({@code START}),
 * or the position it was paused at ({@code PAUSED_AT}, -1 while playing); each game plays (and seeks) to match.
 */
public class ImageScreenEntity extends Entity {

    private static final EntityDataAccessor<String> URL = SynchedEntityData.defineId(ImageScreenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> WIDTH = SynchedEntityData.defineId(ImageScreenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HEIGHT = SynchedEntityData.defineId(ImageScreenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FACING = SynchedEntityData.defineId(ImageScreenEntity.class, EntityDataSerializers.INT);
    /** True: the whole image, with bars if its shape differs; false: stretched to fill the screen. */
    private static final EntityDataAccessor<Boolean> FIT = SynchedEntityData.defineId(ImageScreenEntity.class, EntityDataSerializers.BOOLEAN);
    /** Video: game tick at which the video was at 0:00, position (ms) it is paused at or -1, loop, volume 0..100. */
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(ImageScreenEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> PAUSED_AT = SynchedEntityData.defineId(ImageScreenEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> LOOP = SynchedEntityData.defineId(ImageScreenEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> VOLUME = SynchedEntityData.defineId(ImageScreenEntity.class, EntityDataSerializers.INT);

    /** Thickness of the screen (blocks): it stands out of the wall like a flat TV. */
    public static final double DEPTH = 2 / 16.0;

    public ImageScreenEntity(EntityType<? extends ImageScreenEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /**
     * A screen on the {@code face} side of the wall block {@code wall}, centred on it (an even size leans to the
     * viewer's right / up).
     */
    public static ImageScreenEntity create(ServerLevel level, BlockPos wall, Direction face, int width, int height) {
        ImageScreenEntity e = new ImageScreenEntity(Studio.STUDIO_SCREEN, level);
        e.setFacing(face);
        e.entityData.set(WIDTH, ImageRequest.clamp(width, ImageRequest.MAX_WIDTH));
        e.entityData.set(HEIGHT, ImageRequest.clamp(height, ImageRequest.MAX_HEIGHT));
        e.placeOn(wall, face);
        return e;
    }

    /** Puts the screen against the face of a wall block, centred on it. */
    public void placeOn(BlockPos wall, Direction face) {
        Direction right = face.getCounterClockWise();
        double offRight = (width() % 2 == 0) ? 0.5 : 0, offUp = (height() % 2 == 0) ? 0.5 : 0;
        Vec3 c = Vec3.atCenterOf(wall)
                .add(face.getStepX() * (0.5 + DEPTH / 2), 0, face.getStepZ() * (0.5 + DEPTH / 2))
                .add(right.getStepX() * offRight, offUp, right.getStepZ() * offRight);
        setPos(c);
        refreshBox();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(URL, "");
        b.define(WIDTH, ImageRequest.DEFAULT_WIDTH);
        b.define(HEIGHT, ImageRequest.DEFAULT_HEIGHT);
        b.define(FACING, Direction.SOUTH.get2DDataValue());
        b.define(FIT, true);
        b.define(START, 0L);
        b.define(PAUSED_AT, -1L);
        b.define(LOOP, true);
        b.define(VOLUME, 100);
    }

    public String url() { return entityData.get(URL); }
    public int width() { return entityData.get(WIDTH); }
    public int height() { return entityData.get(HEIGHT); }
    public boolean fit() { return entityData.get(FIT); }
    public long videoStart() { return entityData.get(START); }
    public long pausedAt() { return entityData.get(PAUSED_AT); }
    public boolean paused() { return entityData.get(PAUSED_AT) >= 0; }
    public boolean loop() { return entityData.get(LOOP); }
    public int volume() { return entityData.get(VOLUME); }

    /** Where the video should be now (ms from its start), on this side's clock; partialTick smooths it on clients. */
    public long videoPosition(float partialTick) {
        if (paused()) return pausedAt();
        return Math.max(0, (long) ((level().getGameTime() - videoStart() + partialTick) * 50));
    }

    /** The video starts again from 0:00 (also when the address changes). */
    public void restartVideo() {
        entityData.set(START, level().getGameTime());
        entityData.set(PAUSED_AT, -1L);
    }

    public void pauseVideo() {
        if (!paused()) entityData.set(PAUSED_AT, videoPosition(0));
    }

    public void playVideo() {
        if (!paused()) return;
        entityData.set(START, level().getGameTime() - pausedAt() / 50);
        entityData.set(PAUSED_AT, -1L);
    }

    /** Jumps to {@code position} ms, playing or paused. */
    public void playVideoAt(long position, boolean paused) {
        long p = Math.max(0, position);
        entityData.set(START, level().getGameTime() - p / 50);
        entityData.set(PAUSED_AT, paused ? p : -1L);
    }

    public void setLoop(boolean loop) {
        entityData.set(LOOP, loop);
    }

    public void setVolume(int volume) {
        entityData.set(VOLUME, Math.max(0, Math.min(100, volume)));
    }

    public Direction getFacing() {
        return Direction.from2DDataValue(entityData.get(FACING));
    }

    public void setUrl(String url) {
        String u = url == null ? "" : url.trim();
        if (!u.equals(url())) restartVideo();
        entityData.set(URL, u);
    }

    public void setFit(boolean fit) {
        entityData.set(FIT, fit);
    }

    /** Resizes around the same wall block (the one behind the screen's centre block). */
    public void resize(int width, int height) {
        BlockPos wall = wallBlock();
        entityData.set(WIDTH, ImageRequest.clamp(width, ImageRequest.MAX_WIDTH));
        entityData.set(HEIGHT, ImageRequest.clamp(height, ImageRequest.MAX_HEIGHT));
        placeOn(wall, getFacing());
    }

    /** The wall block the screen was centred on. */
    public BlockPos wallBlock() {
        Direction face = getFacing(), right = face.getCounterClockWise();
        double offRight = (width() % 2 == 0) ? 0.5 : 0, offUp = (height() % 2 == 0) ? 0.5 : 0;
        Vec3 p = position().subtract(face.getStepX() * (0.5 + DEPTH / 2), 0, face.getStepZ() * (0.5 + DEPTH / 2))
                .subtract(right.getStepX() * offRight, offUp, right.getStepZ() * offRight);
        return BlockPos.containing(p);
    }

    private void setFacing(Direction facing) {
        if (facing.getAxis().isVertical()) facing = Direction.SOUTH;
        entityData.set(FACING, facing.get2DDataValue());
        setYRot(facing.toYRot());
    }

    // ---------------------------------------------------------------- behaviour

    /** Organisers with the image permission open the settings with a right click. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            if (!Permissions.canShowImages(sp)) return InteractionResult.PASS;
            StudioNetwork.openScreenSettings(sp, this);
        }
        return InteractionResult.SUCCESS;
    }

    /** Broken by a hit from a player allowed to build, like a painting. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isRemoved() || !(source.getEntity() instanceof Player player) || !player.mayBuild()) return false;
        playSound(SoundEvents.PAINTING_BREAK, 1f, 0.8f);
        if (!player.isCreative()) spawnAtLocation(level, new ItemStack(Studio.STUDIO_SCREEN_ITEM));
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
        return new ItemStack(Studio.STUDIO_SCREEN_ITEM);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    /** A screen lights itself: lit as if in front of it, never dark inside the wall. */
    @Override
    public Vec3 getLightProbePosition(float partialTick) {
        Direction f = getFacing();
        return getPosition(partialTick).add(f.getStepX() * 0.5, 0, f.getStepZ() * 0.5);
    }

    // ---------------------------------------------------------------- box

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor == WIDTH || accessor == HEIGHT || accessor == FACING) refreshBox();
    }

    private void refreshBox() {
        if (entityData == null) return;
        setBoundingBox(makeBoundingBox(position()));
    }

    @Override
    protected AABB makeBoundingBox(Vec3 pos) {
        if (entityData == null) return super.makeBoundingBox(pos);
        Direction f = getFacing();
        double hw = width() / 2.0, hh = height() / 2.0, hd = DEPTH / 2;
        double hx = f.getAxis() == Direction.Axis.X ? hd : hw;
        double hz = f.getAxis() == Direction.Axis.Z ? hd : hw;
        return new AABB(pos.x - hx, pos.y - hh, pos.z - hz, pos.x + hx, pos.y + hh, pos.z + hz);
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        entityData.set(URL, in.getStringOr("Url", ""));
        entityData.set(WIDTH, ImageRequest.clamp(in.getIntOr("Width", ImageRequest.DEFAULT_WIDTH), ImageRequest.MAX_WIDTH));
        entityData.set(HEIGHT, ImageRequest.clamp(in.getIntOr("Height", ImageRequest.DEFAULT_HEIGHT), ImageRequest.MAX_HEIGHT));
        entityData.set(FIT, in.getBooleanOr("Fit", true));
        // Saved as a position: a paused video stays where it was, a playing one goes on from there
        long position = Math.max(0, in.getLongOr("VideoPosition", 0L));
        entityData.set(PAUSED_AT, in.getBooleanOr("VideoPaused", false) ? position : -1L);
        entityData.set(START, level().getGameTime() - position / 50);
        entityData.set(LOOP, in.getBooleanOr("VideoLoop", true));
        entityData.set(VOLUME, Math.max(0, Math.min(100, in.getIntOr("VideoVolume", 100))));
        setFacing(Direction.byName(in.getStringOr("Facing", "south")) instanceof Direction d ? d : Direction.SOUTH);
        refreshBox();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putString("Url", url());
        out.putInt("Width", width());
        out.putInt("Height", height());
        out.putBoolean("Fit", fit());
        out.putLong("VideoPosition", videoPosition(0));
        out.putBoolean("VideoPaused", paused());
        out.putBoolean("VideoLoop", loop());
        out.putInt("VideoVolume", volume());
        out.putString("Facing", getFacing().getSerializedName());
    }
}
