package fr.lordfinn.craftmaster.toy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * An inflated balloon tied where it was put down ({@code craftmaster:balloon}): it sways a little in place. A hit,
 * an arrow, a snowball, anything that hurts pops it: a bang, coloured bits, a redstone pulse two blocks under it
 * (where there is air, like the task props) and the organisers know who popped it. Right click takes it back.
 */
public class BalloonEntity extends Entity {

    private static final EntityDataAccessor<ItemStack> STACK = SynchedEntityData.defineId(BalloonEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(BalloonEntity.class, EntityDataSerializers.FLOAT);

    private String placedBy = "";

    public BalloonEntity(EntityType<? extends BalloonEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static BalloonEntity create(ServerLevel level, ItemStack stack, float size, Vec3 pos, ServerPlayer by) {
        BalloonEntity b = new BalloonEntity(Toys.BALLOON_ENTITY, level);
        ItemStack one = stack.copyWithCount(1);
        Toys.snapDyeColour(one);
        b.entityData.set(STACK, one);
        b.entityData.set(SIZE, size);
        b.placedBy = Toys.who(by);
        b.setPos(pos);
        b.setYRot(by.getYRot() + 180);
        b.refreshDimensions();
        return b;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(STACK, ItemStack.EMPTY);
        b.define(SIZE, 1f);
    }

    public ItemStack stack() {
        return entityData.get(STACK);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    public int color() {
        return Toys.color(stack(), BalloonItem.DEFAULT_COLOR);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor == SIZE) refreshDimensions();
    }

    /** The body is about as tall as wide, plus the knot. */
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float s = size();
        return EntityDimensions.fixed(s, s * 1.2f);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeHitByProjectile() {
        return !isRemoved();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }

    @Override
    public ItemStack getPickResult() {
        return stack().copy();
    }

    /** Right click: take it back (inflated). */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || isRemoved()) return InteractionResult.SUCCESS;
        ItemStack stack = stack().copy();
        BalloonItem.setSize(stack, size(), Toys.data(stack).getFloatOr("BurstAt", Balloons.MAX));
        if (!sp.getInventory().add(stack)) sp.spawnAtLocation((ServerLevel) level(), stack);
        level().playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.LEAD_UNTIED, SoundSource.PLAYERS, 0.6f, 1.4f);
        discard();
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (level() instanceof ServerLevel level) pop(level, attacker);
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        pop(level, source.getEntity() != null ? source.getEntity() : source.getDirectEntity());
        return true;
    }

    public void pop(ServerLevel level, Entity by) {
        if (isRemoved()) return;
        float s = size();
        Vec3 c = position().add(0, s * 0.6, 0);
        level.playSound(null, c.x, c.y, c.z, Toys.POP, SoundSource.NEUTRAL, 1.5f, 1.15f - Balloons.fullness(s) * 0.3f);
        level.sendParticles(new DustParticleOptions(color(), 1.4f), c.x, c.y, c.z, (int) (18 + s * 14), s * 0.35, s * 0.35, s * 0.35, 0.15);
        boolean signal = Toys.pulse(level, blockPosition().below(2), 10);
        String name = Toys.frName(stack());
        String who = by instanceof ServerPlayer p ? Toys.who(p) : by != null ? by.getName().getString() : "?";
        if (by instanceof ServerPlayer p) {
            int n = Toys.TALLY.popped(who);
            p.sendOverlayMessage(Component.literal("✸ Pop ! (" + n + ")").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        Toys.tellOrganisers(level.getServer(), Component.literal("✸ " + who + " a crevé " + (stack().has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                        ? "« " + name + " »" : "un " + name))
                .withStyle(ChatFormatting.RED)
                .append(Component.literal(signal ? "" : " (pas de signal redstone : pas d'air 2 blocs dessous)").withStyle(ChatFormatting.DARK_GRAY)));
        discard();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        entityData.set(STACK, in.read("Item", ItemStack.CODEC).orElse(new ItemStack(Toys.BALLOON)));
        entityData.set(SIZE, in.getFloatOr("Size", 1f));
        placedBy = in.getStringOr("PlacedBy", "");
        refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.store("Item", ItemStack.CODEC, stack());
        out.putFloat("Size", size());
        out.putString("PlacedBy", placedBy);
    }
}
