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
import net.minecraft.world.phys.Vec3;

/**
 * A studio camera on its tripod ({@code craftmaster:camera}): the physical prop of a {@link CameraSpot}. Its
 * position is the tripod's feet; the lens is {@link #LENS_HEIGHT} above, turned by the synced yaw and pitch. The
 * broadcast clients never draw it (they see through it). Organisers get its number and the useful commands with a
 * right click, and remove it (with its camera) with a hit.
 */
public class CameraEntity extends Entity {

    /** From the feet to the lens (blocks): roughly a standing player's eyes. */
    public static final double LENS_HEIGHT = 1.5;

    private static final EntityDataAccessor<Integer> NUMBER = SynchedEntityData.defineId(CameraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> NAME = SynchedEntityData.defineId(CameraEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(CameraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(CameraEntity.class, EntityDataSerializers.FLOAT);

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
        snapTo(spot.x(), spot.y() - LENS_HEIGHT, spot.z(), spot.yaw(), 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(NUMBER, 0);
        b.define(NAME, "");
        b.define(YAW, 0f);
        b.define(PITCH, 0f);
    }

    public int number() { return entityData.get(NUMBER); }
    public String cameraName() { return entityData.get(NAME); }
    public float lensYaw() { return entityData.get(YAW); }
    public float lensPitch() { return entityData.get(PITCH); }

    public Vec3 lens() {
        return position().add(0, LENS_HEIGHT, 0);
    }

    // ---------------------------------------------------------------- behaviour

    /** Organisers: number, name and clickable commands in the chat. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            if (!Permissions.canDriveCameras(sp)) return InteractionResult.PASS;
            CameraSpot spot = Cameras.get(number());
            sp.sendSystemMessage(Component.literal("" + (spot != null ? spot.label() : number() + " · " + cameraName()))
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            if (spot != null) sp.sendSystemMessage(Component.literal("   " + spot.describe()).withStyle(ChatFormatting.GRAY));
            sp.sendSystemMessage(Component.literal("   ")
                    .append(link("[À l'antenne]", "/cam cut " + number(), "Passer à l'antenne sur cette caméra"))
                    .append(Component.literal(" "))
                    .append(link("[Viser comme moi]", "/cam aim " + number(), "Elle regarde là où tu regardes (elle reste en place)"))
                    .append(Component.literal(" "))
                    .append(suggest("[Renommer]", "/cam name " + number() + " ", "Donner un nom à la caméra"))
                    .append(Component.literal(" "))
                    .append(suggest("[Rôle]", "/cam role " + number() + " ", "large, trone, podium ou aucun")));
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
        player.sendSystemMessage(Component.literal("Caméra " + number() + " retirée.").withStyle(ChatFormatting.YELLOW));
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
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putInt("Number", number());
        out.putString("Name", cameraName());
        out.putFloat("LensYaw", lensYaw());
        out.putFloat("LensPitch", lensPitch());
    }
}
