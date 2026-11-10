package fr.lordfinn.craftmaster.trial;

import fr.lordfinn.craftmaster.Permissions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A task prop ({@code craftmaster:trial_prop}): any block or item, at any size and angle, that does something
 * when a contestant uses it (right click or hit): message, sound, item, note, teleport, redstone pulse, start /
 * end of their timed attempt... It counts the uses and remembers who touched it first and when.
 * <p>
 * Organisers open its settings by sneaking + using it (or with the task prop wand). The look (item, settings) is
 * synced; the memory ({@link PropStats}) and the item given stay on the server.
 */
public class TrialPropEntity extends Entity {

    private static final EntityDataAccessor<ItemStack> LOOK = SynchedEntityData.defineId(TrialPropEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<String> CONFIG = SynchedEntityData.defineId(TrialPropEntity.class, EntityDataSerializers.STRING);

    private PropConfig config = new PropConfig();
    private PropStats stats = new PropStats();
    private ItemStack give = ItemStack.EMPTY;

    public TrialPropEntity(EntityType<? extends TrialPropEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(LOOK, new ItemStack(Items.BELL));
        b.define(CONFIG, new PropConfig().toJson());
    }

    // ---------------------------------------------------------------- settings

    public ItemStack look() {
        return entityData.get(LOOK);
    }

    public void setLook(ItemStack stack) {
        entityData.set(LOOK, stack == null || stack.isEmpty() ? new ItemStack(Items.BELL) : stack.copyWithCount(1));
    }

    /** The settings (a copy on the client is parsed from the synced JSON). */
    public PropConfig config() {
        return config;
    }

    public void setConfig(PropConfig c) {
        config = c.copy().sanitize();
        entityData.set(CONFIG, config.toJson());
        setGlowingTag(config.glow);
        refreshBox();
    }

    public PropStats stats() {
        return stats;
    }

    public ItemStack giveStack() {
        return give;
    }

    public void setGiveStack(ItemStack stack) {
        give = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    /** The name shown and used in the messages: its own, or the shown item's. */
    public String displayName() {
        return config.name.isEmpty() ? look().getHoverName().getString() : config.name;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor == CONFIG && level().isClientSide()) {
            config = PropConfig.fromJson(entityData.get(CONFIG));
            refreshBox();
        }
    }

    // ---------------------------------------------------------------- box

    private void refreshBox() {
        if (entityData == null) return;
        setBoundingBox(makeBoundingBox(position()));
    }

    /** A cube of the prop's size standing on its position (the item is drawn centred in it). */
    @Override
    protected AABB makeBoundingBox(Vec3 pos) {
        double s = config == null ? 1 : Math.max(0.25, config.scale);
        return new AABB(pos.x - s / 2, pos.y, pos.z - s / 2, pos.x + s / 2, pos.y + s, pos.z + s / 2);
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
        return look().copy();
    }

    // ---------------------------------------------------------------- use

    /** Right click: organisers sneaking open the settings; everyone else (and organisers standing) use it. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            boolean wand = sp.getMainHandItem().is(TrialContent.PROP_WAND);
            if ((wand || sp.isShiftKeyDown()) && Permissions.isAdmin(sp)) TrialNetwork.openProp(sp, this);
            else TrialProps.use(this, sp);
        }
        return InteractionResult.SUCCESS;
    }

    /** A hit uses it too (contestants punch things); it never breaks (organisers remove it from its settings). */
    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (attacker instanceof ServerPlayer sp && !level().isClientSide()) {
            if (sp.isShiftKeyDown() && Permissions.isAdmin(sp)) TrialNetwork.openProp(sp, this);
            else TrialProps.use(this, sp);
        }
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        setLook(in.read("Look", ItemStack.CODEC).orElse(new ItemStack(Items.BELL)));
        setConfig(PropConfig.fromJson(in.getStringOr("Config", "{}")));
        stats = PropStats.fromJson(in.getStringOr("Stats", "{}"));
        give = in.read("Give", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.store("Look", ItemStack.CODEC, look());
        out.putString("Config", config.toJson());
        out.putString("Stats", stats.toJson());
        if (!give.isEmpty()) out.store("Give", ItemStack.CODEC, give);
    }
}
