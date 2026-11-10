package fr.lordfinn.craftmaster.trial;

import fr.lordfinn.craftmaster.Permissions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A drawing board on a wall ({@code craftmaster:board}): a painter's canvas, a school slate or a whiteboard, of
 * 1×1 to 8×6 blocks, 16 pixels per block. Players paint on it with dyes (hold right click to draw; sneak to fill
 * an area), rub out with a sponge, and write on it (right click empty-handed: a text screen). Organisers can lock
 * it. The drawing and the text are synced ({@link CanvasPixels}) and saved with the entity.
 */
public class BoardEntity extends Entity {

    /** Look of the board: surface colour and the colour of the text written on it. */
    public enum Variant {
        CANVAS("canvas", 0xFFF1E6C8, 0xFF3A2A1A, 2, 2),
        SLATE("slate", 0xFF2E3A33, 0xFFF2F2EA, 3, 2),
        WHITEBOARD("whiteboard", 0xFFF5F7F9, 0xFF1F3A8A, 3, 2);

        public final String id;
        public final int surface, ink, defaultWidth, defaultHeight;

        Variant(String id, int surface, int ink, int w, int h) {
            this.id = id;
            this.surface = surface;
            this.ink = ink;
            this.defaultWidth = w;
            this.defaultHeight = h;
        }

        public static Variant byIndex(int i) {
            Variant[] v = values();
            return v[Math.floorMod(i, v.length)];
        }
    }

    public static final int MAX_WIDTH = 8, MAX_HEIGHT = 6, MAX_TEXT = 600;
    /** Depth of the board on the wall, blocks. */
    public static final double DEPTH = 1 / 16.0;

    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(BoardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WIDTH = SynchedEntityData.defineId(BoardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HEIGHT = SynchedEntityData.defineId(BoardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FACING = SynchedEntityData.defineId(BoardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> PIXELS = SynchedEntityData.defineId(BoardEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> TEXT = SynchedEntityData.defineId(BoardEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> LOCKED = SynchedEntityData.defineId(BoardEntity.class, EntityDataSerializers.BOOLEAN);

    /** Server: the drawing being edited (the synced text is refreshed from it). */
    private CanvasPixels pixels;
    /** Last point painted by each player, to join the dots of a stroke. */
    private final Map<UUID, long[]> strokes = new HashMap<>();

    public BoardEntity(EntityType<? extends BoardEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(VARIANT, 0);
        b.define(WIDTH, 2);
        b.define(HEIGHT, 2);
        b.define(FACING, Direction.SOUTH.get2DDataValue());
        b.define(PIXELS, "");
        b.define(TEXT, "");
        b.define(LOCKED, false);
    }

    // ---------------------------------------------------------------- getters

    public Variant variant() { return Variant.byIndex(entityData.get(VARIANT)); }
    public int boardWidth() { return entityData.get(WIDTH); }
    public int boardHeight() { return entityData.get(HEIGHT); }
    public Direction facing() { return Direction.from2DDataValue(entityData.get(FACING)); }
    public String pixelText() { return entityData.get(PIXELS); }
    public String text() { return entityData.get(TEXT); }
    public boolean locked() { return entityData.get(LOCKED); }

    /** The board's "right" as seen by someone facing it. */
    public Direction right() {
        return facing().getCounterClockWise();
    }

    public CanvasPixels pixels() {
        if (pixels == null || pixels.width() != boardWidth() * 16 || pixels.height() != boardHeight() * 16)
            pixels = CanvasPixels.decode(boardWidth() * 16, boardHeight() * 16, pixelText());
        return pixels;
    }

    // ---------------------------------------------------------------- setters (server)

    public void setup(Variant variant, int width, int height, Direction facing) {
        entityData.set(VARIANT, variant.ordinal());
        entityData.set(FACING, facing.getAxis().isHorizontal() ? facing.get2DDataValue() : Direction.SOUTH.get2DDataValue());
        resize(width, height);
        setYRot(facing.toYRot());
    }

    /** New size; the drawing keeps its top-left part. */
    public void resize(int width, int height) {
        width = Math.max(1, Math.min(MAX_WIDTH, width));
        height = Math.max(1, Math.min(MAX_HEIGHT, height));
        CanvasPixels old = pixels();
        pixels = old.resized(width * 16, height * 16);
        entityData.set(WIDTH, width);
        entityData.set(HEIGHT, height);
        entityData.set(PIXELS, pixels.encode());
        refreshBox();
    }

    public void setText(String text) {
        String t = text == null ? "" : text.replace("\r", "");
        if (t.length() > MAX_TEXT) t = t.substring(0, MAX_TEXT);
        entityData.set(TEXT, t);
    }

    public void setLocked(boolean locked) {
        entityData.set(LOCKED, locked);
    }

    public void clearDrawing() {
        pixels().clear();
        entityData.set(PIXELS, pixels.encode());
    }

    private void publishPixels() {
        entityData.set(PIXELS, pixels().encode());
    }

    // ---------------------------------------------------------------- geometry

    /** Pixel under a hit point given relative to the entity (its centre), or null if outside. */
    public int[] pixelAt(Vec3 local) {
        Direction r = right();
        double u = local.x * r.getStepX() + local.z * r.getStepZ() + boardWidth() / 2.0;
        double v = boardHeight() / 2.0 - local.y;
        int px = (int) Math.floor(u * 16), py = (int) Math.floor(v * 16);
        if (px < 0 || py < 0 || px >= boardWidth() * 16 || py >= boardHeight() * 16) return null;
        return new int[]{px, py};
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor == WIDTH || accessor == HEIGHT || accessor == FACING) refreshBox();
        if (accessor == PIXELS && level().isClientSide()) pixels = null;
    }

    private void refreshBox() {
        if (entityData == null) return;
        setBoundingBox(makeBoundingBox(position()));
    }

    /** A thin plate on the wall: the entity is at the centre of the board's front face. */
    @Override
    protected AABB makeBoundingBox(Vec3 pos) {
        if (entityData == null) return super.makeBoundingBox(pos);
        Direction f = facing(), r = right();
        double hw = boardWidth() / 2.0, hh = boardHeight() / 2.0;
        Vec3 a = pos.add(r.getStepX() * hw, -hh, r.getStepZ() * hw).subtract(f.getStepX() * DEPTH, 0, f.getStepZ() * DEPTH);
        Vec3 b = pos.add(-r.getStepX() * hw, hh, -r.getStepZ() * hw);
        return new AABB(a, b);
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
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(TrialContent.BOARD_ITEMS.get(variant()));
    }

    // ---------------------------------------------------------------- use

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp) || !(level() instanceof ServerLevel)) return InteractionResult.SUCCESS;
        if (locked() && !Permissions.isAdmin(sp)) {
            sp.sendOverlayMessage(Component.literal("Ce tableau est verrouillé").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS_SERVER;
        }
        ItemStack held = sp.getMainHandItem();
        DyeColor dye = held.getItem() instanceof DyeItem ? held.get(DataComponents.DYE) : null;
        boolean eraser = held.is(Items.SPONGE) || held.is(Items.WET_SPONGE);
        if (dye == null && !eraser) {
            if (held.isEmpty()) TrialNetwork.openBoardText(sp, this);
            return InteractionResult.SUCCESS_SERVER;
        }
        int[] px = pixelAt(location);
        if (px == null) return InteractionResult.SUCCESS_SERVER;
        int color = eraser ? 0 : dye.getId() + 1;
        CanvasPixels p = pixels();
        boolean changed;
        long now = level().getGameTime();
        if (sp.isShiftKeyDown() && !eraser) {
            changed = p.fill(px[0], px[1], color);
            strokes.remove(sp.getUUID());
            if (changed) level().playSound(null, getX(), getY(), getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.5f, 1.4f);
        } else {
            int radius = eraser ? 1 : 0;
            long[] last = strokes.get(sp.getUUID());
            // Holding right click repeats every few ticks: join the points into a stroke
            if (last != null && now - last[2] <= 6 && last[3] == color) changed = p.line((int) last[0], (int) last[1], px[0], px[1], radius, color);
            else changed = p.dot(px[0], px[1], radius, color);
            strokes.put(sp.getUUID(), new long[]{px[0], px[1], now, color});
            if (changed && now % 3 == 0)
                level().playSound(null, getX(), getY(), getZ(), eraser ? SoundEvents.WOOL_HIT : SoundEvents.BRUSH_GENERIC, SoundSource.PLAYERS, 0.4f, 1.5f);
        }
        if (changed) publishPixels();
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Broken by a sneaking player allowed to build (a plain hit does nothing: painters miss-click). */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isRemoved() || !(source.getEntity() instanceof Player player) || !player.mayBuild()) return false;
        if (!player.isShiftKeyDown() || (locked() && !(player instanceof ServerPlayer sp && Permissions.isAdmin(sp)))) {
            if (player instanceof ServerPlayer sp)
                sp.sendOverlayMessage(Component.literal("Accroupis-toi pour décrocher le tableau").withStyle(ChatFormatting.GRAY));
            return false;
        }
        playSound(SoundEvents.PAINTING_BREAK, 1f, 1f);
        if (!player.isCreative()) spawnAtLocation(level, new ItemStack(TrialContent.BOARD_ITEMS.get(variant())));
        discard();
        return true;
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        Direction f = Direction.byName(in.getStringOr("Facing", "south"));
        entityData.set(VARIANT, in.getIntOr("Variant", 0));
        entityData.set(FACING, f != null && f.getAxis().isHorizontal() ? f.get2DDataValue() : Direction.SOUTH.get2DDataValue());
        entityData.set(WIDTH, Math.max(1, Math.min(MAX_WIDTH, in.getIntOr("Width", 2))));
        entityData.set(HEIGHT, Math.max(1, Math.min(MAX_HEIGHT, in.getIntOr("Height", 2))));
        entityData.set(PIXELS, in.getStringOr("Pixels", ""));
        pixels = null;
        setText(in.getStringOr("Text", ""));
        setLocked(in.getBooleanOr("Locked", false));
        setYRot(facing().toYRot());
        refreshBox();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putInt("Variant", variant().ordinal());
        out.putString("Facing", facing().getSerializedName());
        out.putInt("Width", boardWidth());
        out.putInt("Height", boardHeight());
        out.putString("Pixels", pixelText());
        out.putString("Text", text());
        out.putBoolean("Locked", locked());
    }
}
