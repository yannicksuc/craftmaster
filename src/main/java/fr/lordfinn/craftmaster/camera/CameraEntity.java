package fr.lordfinn.craftmaster.camera;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.registry.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
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
 * A studio camera ({@code craftmaster:camera}): the physical prop of a {@link CameraSpot}. It is mounted like the
 * studio lights ({@link CameraMount}): on its own floor tripod, on a tripod stand or a rig rod, hung under a ceiling
 * or a rod, on a wall arm or a rod's side. Its position is the mount's anchor (the tripod's feet on the floor); the
 * lens is {@link CameraMount#lensDy()} above (or below) it, turned by the synced yaw and pitch. The
 * broadcast clients never draw it (they see through it). Organisers get its number and the useful commands with a
 * right click, and remove it (with its camera) with a hit.
 */
public class CameraEntity extends Entity {

    /** Floor tripod: from the feet to the lens (blocks), roughly a standing player's eyes. */
    public static final double LENS_HEIGHT = CameraMount.LEGS_LENS;

    private static final EntityDataAccessor<Integer> NUMBER = SynchedEntityData.defineId(CameraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> NAME = SynchedEntityData.defineId(CameraEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(CameraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(CameraEntity.class, EntityDataSerializers.FLOAT);
    /** The camera's group ("studio", "pont", "pont:ensuri"): with the number, its key. */
    private static final EntityDataAccessor<String> GROUP = SynchedEntityData.defineId(CameraEntity.class, EntityDataSerializers.STRING);
    /** {@link CameraMount#id()}: how it is mounted, which the client draws (legs, stem, arm, upside-down). */
    private static final EntityDataAccessor<String> MOUNT = SynchedEntityData.defineId(CameraEntity.class, EntityDataSerializers.STRING);

    /** Parsed {@link #MOUNT}, cached. */
    private CameraMount mount = CameraMount.FLOOR;

    public CameraEntity(EntityType<? extends CameraEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** A camera prop standing so that its lens is at the spot's position, looking where the spot looks. */
    public static CameraEntity create(ServerLevel level, CameraSpot spot) {
        CameraEntity e = new CameraEntity(ModContent.CAMERA, level);
        e.apply(spot);
        return e;
    }

    /** Moves and renames the prop to match the spot. */
    public void apply(CameraSpot spot) {
        entityData.set(NUMBER, spot.number());
        entityData.set(NAME, spot.name());
        entityData.set(YAW, spot.yaw());
        entityData.set(PITCH, spot.pitch());
        entityData.set(GROUP, spot.group());
        setMount(spot.mount());
        snapTo(spot.x(), spot.y() - mount.lensDy(), spot.z(), spot.yaw(), 0);
    }

    private void setMount(CameraMount m) {
        mount = m;
        entityData.set(MOUNT, m.id());
        refreshBox();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(NUMBER, 0);
        b.define(NAME, "");
        b.define(YAW, 0f);
        b.define(PITCH, 0f);
        b.define(GROUP, CameraSpot.STUDIO);
        b.define(MOUNT, CameraMount.FLOOR.id());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor == MOUNT) {
            mount = CameraMount.parse(entityData.get(MOUNT));
            refreshBox();
        }
    }

    private void refreshBox() {
        if (entityData != null) setBoundingBox(makeBoundingBox(position()));
    }

    /** On its floor tripod: the whole tripod (the type's size); mounted: around the camera's body only. */
    @Override
    protected AABB makeBoundingBox(Vec3 pos) {
        if (mount == null || mount.legs()) return super.makeBoundingBox(pos);
        Vec3 lens = pos.add(0, mount.lensDy(), 0);
        return new AABB(lens.x - 0.35, lens.y - 0.35, lens.z - 0.35, lens.x + 0.35, lens.y + 0.35, lens.z + 0.35);
    }

    public int number() { return entityData.get(NUMBER); }
    public String cameraName() { return entityData.get(NAME); }
    public float lensYaw() { return entityData.get(YAW); }
    public float lensPitch() { return entityData.get(PITCH); }
    public String group() { return entityData.get(GROUP); }
    public CameraMount mount() { return mount; }
    /** "studio:2", "pont:ensuri:1" (see {@link CameraSpot#key()}). */
    public String cameraKey() { return group() + ":" + number(); }

    public Vec3 lens() {
        return position().add(0, mount.lensDy(), 0);
    }

    // ---------------------------------------------------------------- behaviour

    /** Organisers: number, name and clickable commands in the chat. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            if (!Permissions.canDriveCameras(sp)) return InteractionResult.PASS;
            String key = cameraKey();
            CameraSpot spot = Cameras.get(key);
            sp.sendSystemMessage(Component.literal("" + (spot != null ? spot.label() : number() + " · " + cameraName()))
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            if (spot != null) sp.sendSystemMessage(Component.literal("   " + spot.describe()).withStyle(ChatFormatting.GRAY));
            sp.sendSystemMessage(Component.literal("   ")
                    .append(link("[À l'antenne]", "/cam cut " + key, "Passer à l'antenne sur cette caméra"))
                    .append(Component.literal(" "))
                    .append(link("[Viser comme moi]", "/cam aim " + key, "Elle regarde là où tu regardes (elle reste en place)"))
                    .append(Component.literal(" "))
                    .append(suggest("[Renommer]", "/cam name " + key + " ", "Donner un nom à la caméra"))
                    .append(Component.literal(" "))
                    .append(suggest("[Rôle]", "/cam role " + key + " ", "large, trone, podium ou aucun"))
                    .append(Component.literal(" "))
                    .append(suggest("[Lieu]", "/cam lieu " + key + " ", "studio, ou une manche et sa zone (ex. : pont ensuri)")));
        }
        return InteractionResult.SUCCESS;
    }

    private static Component link(String text, String command, String hover) {
        return Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.YELLOW)
                .withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    private static Component suggest(String text, String command, String hover) {
        return Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent.SuggestCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    /** A hit from someone allowed to drive the cameras removes the camera (prop and shot). */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isRemoved() || !(source.getEntity() instanceof ServerPlayer player) || !Permissions.canDriveCameras(player)) return false;
        playSound(SoundEvents.ARMOR_STAND_BREAK, 1f, 1f);
        if (!player.isCreative()) spawnAtLocation(level, new ItemStack(ModContent.CAMERA_ITEM), 0.5f);
        Cameras.removedByHand(level.getServer(), this);
        discard();
        player.sendSystemMessage(Component.literal("Caméra " + cameraKey() + " retirée.").withStyle(ChatFormatting.YELLOW));
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
        return new ItemStack(ModContent.CAMERA_ITEM);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        entityData.set(NUMBER, in.getIntOr("Number", 0));
        entityData.set(NAME, in.getStringOr("Name", ""));
        entityData.set(YAW, in.getFloatOr("LensYaw", 0f));
        entityData.set(PITCH, in.getFloatOr("LensPitch", 0f));
        // Tripods saved before the lieux belong to the studio
        entityData.set(GROUP, in.getStringOr("Group", CameraSpot.STUDIO));
        // Cameras saved before the mounts stand on their floor tripod
        setMount(CameraMount.parse(in.getStringOr("Mount", "")));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putInt("Number", number());
        out.putString("Name", cameraName());
        out.putFloat("LensYaw", lensYaw());
        out.putFloat("LensPitch", lensPitch());
        out.putString("Group", group());
        out.putString("Mount", mount.id());
    }
}
