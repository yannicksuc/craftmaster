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
 * downloaded from a URL by every player's game (see client/studio/ScreenImages). Replaces the Paper plugin's
 * {@code /image} (ImageFrame). The entity's position is the centre of the picture; its size is in blocks.
 * Without an image (or while it loads, or if it cannot be shown) the screen shows the CraftMaster test card.
 */
public class ImageScreenEntity extends Entity {

    private static final EntityDataAccessor<String> URL = SynchedEntityData.defineId(ImageScreenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> WIDTH = SynchedEntityData.defineId(ImageScreenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HEIGHT = SynchedEntityData.defineId(ImageScreenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FACING = SynchedEntityData.defineId(ImageScreenEntity.class, EntityDataSerializers.INT);
    /** True: the whole image, with bars if its shape differs; false: stretched to fill the screen. */
    private static final EntityDataAccessor<Boolean> FIT = SynchedEntityData.defineId(ImageScreenEntity.class, EntityDataSerializers.BOOLEAN);
    /** Name given by the organisers ("gauche", "plateau"...) to pick this screen from a command, the voice or the Stream Deck. */
    private static final EntityDataAccessor<String> NAME = SynchedEntityData.defineId(ImageScreenEntity.class, EntityDataSerializers.STRING);
    /** Game time the address was set: a video starts there for everyone, someone arriving later joins it where it is. */
    private static final EntityDataAccessor<Long> SHOWN_AT = SynchedEntityData.defineId(ImageScreenEntity.class, EntityDataSerializers.LONG);

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
        b.define(NAME, "");
        b.define(SHOWN_AT, -1L);
    }

    public String url() { return entityData.get(URL); }
    public int width() { return entityData.get(WIDTH); }
    public int height() { return entityData.get(HEIGHT); }
    public boolean fit() { return entityData.get(FIT); }
    public String screenName() { return entityData.get(NAME); }
    public long shownAt() { return entityData.get(SHOWN_AT); }

    public void setScreenName(String name) {
        entityData.set(NAME, name == null ? "" : name.strip());
    }

    public Direction getFacing() {
        return Direction.from2DDataValue(entityData.get(FACING));
    }

    /** New address; the same one again restarts its video from the beginning for everyone. */
    public void setUrl(String url) {
        entityData.set(URL, url == null ? "" : url.trim());
        entityData.set(SHOWN_AT, level().getGameTime());
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
        entityData.set(NAME, in.getStringOr("Name", ""));
        setFacing(Direction.byName(in.getStringOr("Facing", "south")) instanceof Direction d ? d : Direction.SOUTH);
        refreshBox();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putString("Url", url());
        out.putInt("Width", width());
        out.putInt("Height", height());
        out.putBoolean("Fit", fit());
        if (!screenName().isEmpty()) out.putString("Name", screenName());
        out.putString("Facing", getFacing().getSerializedName());
    }
}
