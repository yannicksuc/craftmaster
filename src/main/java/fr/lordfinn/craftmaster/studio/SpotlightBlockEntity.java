package fr.lordfinn.craftmaster.studio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A spotlight's settings: its name, where it points (yaw / pitch, or a followed player), its colour, intensity and
 * cone angle, and how long a change takes (fade). The server puts a real light (an invisible {@code minecraft:light}
 * block, as bright as the intensity) where the beam lands; the clients draw the head, the beam and the pool of
 * light, turning and fading smoothly toward the settings (and following the player themselves, every frame).
 * <p>A follow can be a player's name or an automatic rule ({@link SpotlightFollow}): the server picks the player
 * (sent to the clients as {@link #target()}).
 */
public class SpotlightBlockEntity extends BlockEntity {

    /** How far the beam goes (blocks). */
    public static final double RANGE = 48;
    /** A followed player farther than this is not followed (blocks). */
    public static final double FOLLOW_RANGE = 96;
    public static final int DEFAULT_COLOR = 0xFFF1D6;
    /** Half-angle of the cone (degrees): a narrow stage beam by default, up to a wide wash. */
    public static final float DEFAULT_CONE = 3.2f, MIN_CONE = 1, MAX_CONE = 30;
    public static final int MAX_INTENSITY = 100;
    /** Longest fade (ticks). */
    public static final int MAX_FADE = 20 * 60;
    /** Light level of the spot where the beam lands. */
    private static final int SPOT_LIGHT = 15;

    /** Server-side spotlights currently loaded, for the commands acting on all of them. */
    private static final Set<SpotlightBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    private float yaw, pitch = 30;
    private int color = DEFAULT_COLOR;
    private String follow = "";
    /** The player followed right now: {@link #follow} itself, or the one an automatic rule picked (server). */
    private String target = "";
    private String name = "";
    private int intensity = MAX_INTENSITY;
    private float cone = DEFAULT_CONE;
    /** How long the clients take to reach new colour / intensity / cone / aim settings (ticks; 0 = their usual pace). */
    private int fade;
    /** A light effect drawn by the clients on the intensity ("respiration", "strobe"…; "" none), its speed and phase. */
    private String effect = "";
    private float effectSpeed = 1, effectPhase;
    /** The light block this spotlight placed (server), or null. */
    private BlockPos light;

    // Client only: what is drawn this frame, easing toward the settings
    public float renderYaw, renderPitch, renderIntensity, renderCone;
    /**
     * Client only: the followed player's feet as the beam follows them (the follow camera's {@link
     * fr.lordfinn.craftmaster.camera.TargetTracker}), and whom it tracks ("" = none).
     */
    public final fr.lordfinn.craftmaster.camera.TargetTracker renderTracker = new fr.lordfinn.craftmaster.camera.TargetTracker(0.12, 0.25, 0.25, 1.2);
    public String renderTracked = "";
    public float[] renderColor;
    public long renderNanos;
    public boolean renderReady;

    public SpotlightBlockEntity(BlockPos pos, BlockState state) {
        super(Studio.SPOTLIGHT_BE, pos, state);
    }

    public static List<SpotlightBlockEntity> loaded() {
        synchronized (LOADED) {
            return LOADED.stream().filter(b -> !b.isRemoved() && b.level != null).toList();
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (!level.isClientSide()) synchronized (LOADED) { LOADED.add(this); }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        synchronized (LOADED) { LOADED.remove(this); }
    }

    /** The block is broken or replaced: the spot of light goes too. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        clearLight();
    }

    // ---------------------------------------------------------------- settings

    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public int color() { return color; }
    public String follow() { return follow; }
    /** The player the beam is on now (empty: none). */
    public String target() { return target; }
    public String name() { return name; }
    public int intensity() { return intensity; }
    public float cone() { return cone; }
    public int fade() { return fade; }
    public String effect() { return effect; }
    public float effectSpeed() { return effectSpeed; }
    public float effectPhase() { return effectPhase; }

    /** A light effect ("" or "aucun": none), its speed (1 = normal) and its phase (0..1: a chase across a group). */
    public void setEffect(String effect, float speed, float phase) {
        String e = effect == null || effect.equals("aucun") ? "" : effect;
        this.effect = e;
        this.effectSpeed = Math.max(0.05f, Math.min(10, speed));
        this.effectPhase = phase - (float) Math.floor(phase);
        changed();
    }

    public boolean hanging() {
        return getBlockState().getValue(SpotlightBlock.HANGING);
    }

    public boolean lit() {
        return getBlockState().getValue(SpotlightBlock.LIT);
    }

    /** Centre of the head, around which it turns. */
    public Vec3 pivot() {
        return Vec3.atLowerCornerOf(worldPosition).add(0.5, hanging() ? 7 / 16.0 : 9 / 16.0, 0.5);
    }

    /** Points the spotlight at a fixed direction (stops following). */
    public void aim(float yaw, float pitch) {
        this.yaw = SpotlightAim.wrap(yaw);
        this.pitch = SpotlightAim.clampPitch(pitch);
        this.follow = "";
        changed();
    }

    /** Points the spotlight at a place (stops following). */
    public void aimAt(Vec3 target) {
        Vec3 d = target.subtract(pivot());
        float[] a = SpotlightAim.angles(d.x, d.y, d.z);
        aim(a[0], a[1]);
    }

    /** Follows a player by name, or an automatic rule ({@link SpotlightFollow}); empty: stop, keep the direction. */
    public void follow(String player) {
        this.follow = player == null ? "" : player.strip();
        this.target = SpotlightFollow.isRule(follow) ? "" : follow;
        changed();
    }

    public void setColor(int rgb) {
        this.color = rgb & 0xFFFFFF;
        changed();
    }

    public void setName(String name) {
        this.name = name == null ? "" : name.strip().toLowerCase(java.util.Locale.ROOT);
        changed();
    }

    /** 0 (dark, though switched on) to 100. */
    public void setIntensity(int percent) {
        this.intensity = Math.max(0, Math.min(MAX_INTENSITY, percent));
        changed();
    }

    public void setCone(float degrees) {
        this.cone = Math.max(MIN_CONE, Math.min(MAX_CONE, degrees));
        changed();
    }

    /** Duration of the next changes, seen by the clients (ticks). */
    public void setFade(int ticks) {
        this.fade = Math.max(0, Math.min(MAX_FADE, ticks));
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            updateLight();
        }
    }

    // ---------------------------------------------------------------- server tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpotlightBlockEntity be) {
        if (level.getGameTime() % 4 != 0) return;
        if (!be.follow.isEmpty() && level instanceof ServerLevel server) {
            // An automatic follow picks its player now and then (not every tick: the beam would jump between two)
            if (SpotlightFollow.isRule(be.follow) && level.getGameTime() % 20 == 0) {
                String picked = SpotlightFollow.pick(server, be.follow, be.pivot(), be.target);
                if (!picked.equals(be.target)) {
                    be.target = picked;
                    be.setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                }
            }
            ServerPlayer target = be.target.isEmpty() ? null : server.getServer().getPlayerList().getPlayerByName(be.target);
            if (target != null && target.level() == level && target.position().distanceTo(be.pivot()) < FOLLOW_RANGE) {
                Vec3 d = followPoint(target.position(), target.getBbHeight()).subtract(be.pivot());
                float[] a = SpotlightAim.angles(d.x, d.y, d.z);
                be.yaw = a[0];
                be.pitch = a[1];
            }
        }
        be.updateLight();
    }

    /** Where a followed player is aimed at: their feet. */
    public static Vec3 followPoint(Vec3 feet, double height) {
        // The feet, like a follow spot on stage: the pool lands under the player and the cone lights them from head to toe
        // (aimed at the chest, the pool fell on the floor behind them)
        return feet.add(0, 0.05, 0);
    }

    /** Puts the light block where the beam lands (in the air in front of the block it hits), or removes it. */
    void updateLight() {
        if (!(level instanceof ServerLevel server)) return;
        BlockPos target = null;
        if (lit() && intensity > 0) {
            BlockHitResult hit = trace(server, pivot(), yaw, pitch);
            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos p = hit.getBlockPos().relative(hit.getDirection());
                BlockState there = server.getBlockState(p);
                if (there.isAir() || there.is(Blocks.LIGHT)) target = p;
            }
        }
        int level = Math.max(1, Math.round(SPOT_LIGHT * intensity / (float) MAX_INTENSITY));
        if (target != null && target.equals(light) && server.getBlockState(target).is(Blocks.LIGHT)
                && server.getBlockState(target).getValue(LightBlock.LEVEL) == level) return;
        clearLight();
        if (target != null) {
            server.setBlock(target, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level), 3);
            light = target;
            setChanged();
        }
    }

    void clearLight() {
        if (light == null || level == null || level.isClientSide()) return;
        // Only our own light block (another spotlight may light the same place: it puts its own back next tick)
        if (level.getBlockState(light).is(Blocks.LIGHT)) level.setBlock(light, Blocks.AIR.defaultBlockState(), 3);
        light = null;
        setChanged();
    }

    /** The first block along the beam, up to {@link #RANGE}. Used by both sides (the client draws the beam to it). */
    public static BlockHitResult trace(Level level, Vec3 from, float yaw, float pitch) {
        double[] d = SpotlightAim.direction(yaw, pitch);
        // Start a little in front of the lens: the spotlight's own block is not in the way
        Vec3 start = from.add(d[0] * 0.45, d[1] * 0.45, d[2] * 0.45);
        Vec3 end = from.add(d[0] * RANGE, d[1] * RANGE, d[2] * RANGE);
        return level.clip(new ClipContext(start, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
    }

    // ---------------------------------------------------------------- save and sync

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        yaw = in.getFloatOr("Yaw", 0);
        pitch = in.getFloatOr("Pitch", 30);
        color = in.getIntOr("Color", DEFAULT_COLOR);
        follow = in.getStringOr("Follow", "");
        target = in.getStringOr("Target", SpotlightFollow.isRule(follow) ? "" : follow);
        name = in.getStringOr("Name", "");
        intensity = Math.max(0, Math.min(MAX_INTENSITY, in.getIntOr("Intensity", MAX_INTENSITY)));
        cone = Math.max(MIN_CONE, Math.min(MAX_CONE, in.getFloatOr("Cone", DEFAULT_CONE)));
        fade = Math.max(0, Math.min(MAX_FADE, in.getIntOr("Fade", 0)));
        light = in.read("Light", BlockPos.CODEC).orElse(null);
        effect = in.getStringOr("Effect", "");
        effectSpeed = in.getFloatOr("EffectSpeed", 1);
        effectPhase = in.getFloatOr("EffectPhase", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putFloat("Yaw", yaw);
        out.putFloat("Pitch", pitch);
        out.putInt("Color", color);
        out.putString("Follow", follow);
        out.putString("Target", target);
        if (!name.isEmpty()) out.putString("Name", name);
        out.putInt("Intensity", intensity);
        out.putFloat("Cone", cone);
        out.putInt("Fade", fade);
        if (light != null) out.store("Light", BlockPos.CODEC, light);
        if (!effect.isEmpty()) {
            out.putString("Effect", effect);
            out.putFloat("EffectSpeed", effectSpeed);
            out.putFloat("EffectPhase", effectPhase);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
