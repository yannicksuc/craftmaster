package fr.lordfinn.craftmaster.toy;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.trial.ResultSheet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * A toy lying in the world ({@code craftmaster:toy}): a rubber duck, a ball, a bean bag, a fragile egg, a golf
 * ball. Not a mob: no AI, no health. It falls, bounces, rolls, floats (and drifts with the current), sinks into a
 * golf hole or a target container. Right click picks it up, a hit gives it a little kick (or a swing with the
 * golf club). Put down by an organiser it counts as hidden: the contestant who picks it up "found" it.
 */
public class ToyEntity extends Entity {

    private static final EntityDataAccessor<ItemStack> STACK = SynchedEntityData.defineId(ToyEntity.class, EntityDataSerializers.ITEM_STACK);

    private ToyKind kind = ToyKind.DUCK;
    /** Put down by an organiser for the contestants to find. */
    private boolean hidden;
    /** The last player who threw or hit it, where from, and (golf) the strokes since it was put down. */
    private String thrower = "";
    private Vec3 throwFrom;
    private boolean flying;
    /** The last move was a golf stroke (not a throw: no throw distance). */
    private boolean club;
    private int strokes;
    private int restTicks;
    /** Ticks without a squeak (it squeaks when it lands, not at each little bounce). */
    private int squeakCooldown;
    /** Sitting in a golf hole. */
    private BlockPos holedIn;
    /** Shown glowing to find it (organisers' {@code /craftMasterToys glow}) until this tick count. */
    private int glowTicks;

    public ToyEntity(EntityType<? extends ToyEntity> type, Level level) {
        super(type, level);
    }

    public static ToyEntity create(ServerLevel level, ItemStack stack, Vec3 pos, float yaw) {
        ToyEntity e = new ToyEntity(Toys.TOY, level);
        e.setStack(stack);
        e.setPos(pos);
        e.setYRot(yaw);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(STACK, new ItemStack(Items.AIR));
    }

    public ItemStack stack() {
        return entityData.get(STACK);
    }

    public ToyKind kind() {
        return kind;
    }

    public void setStack(ItemStack stack) {
        entityData.set(STACK, stack.copyWithCount(1));
        if (stack.getItem() instanceof ToyItem t) kind = t.kind();
        refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor == STACK) {
            if (stack().getItem() instanceof ToyItem t) kind = t.kind();
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed((float) kind.width, (float) kind.height);
    }

    public boolean hidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public void glowFor(int ticks) {
        glowTicks = ticks;
        setGlowingTag(true);
    }

    /** Called when it leaves a hand: who threw it, from where. */
    public void thrownBy(ServerPlayer p) {
        thrower = Toys.who(p);
        throwFrom = p.position();
        flying = true;
        club = false;
        restTicks = 0;
        hidden = false;
        holedIn = null;
    }

    // ---------------------------------------------------------------- physics

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (squeakCooldown > 0) squeakCooldown--;
        if (glowTicks > 0 && --glowTicks == 0) setGlowingTag(false);
        if (holedIn != null) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        Vec3 v = getDeltaMovement();
        if (isInWater()) {
            double h = getFluidHeight(FluidTags.WATER);
            if (kind.floats) {
                // Spring towards its waterline, well damped: it bobs once and rides the current
                double target = kind.height * kind.floatDepth;
                v = new Vec3(v.x * 0.9, (v.y + (h - target) * 0.06) * 0.75, v.z * 0.9);
            } else {
                v = new Vec3(v.x * 0.8, v.y * 0.8 - 0.008, v.z * 0.8);
            }
        } else if (isInLava()) {
            level.playSound(null, blockPosition(), SoundEvents.LAVA_EXTINGUISH, SoundSource.NEUTRAL, 0.5f, 1.6f);
            discard();
            return;
        } else {
            v = v.add(0, -ToyPhysics.GRAVITY, 0).scale(0.99);
        }

        Vec3 before = position();
        move(MoverType.SELF, v);
        Vec3 moved = position().subtract(before);
        boolean cx = horizontalCollision && Math.abs(moved.x - v.x) > 1e-5;
        boolean cz = horizontalCollision && Math.abs(moved.z - v.z) > 1e-5;
        boolean cy = verticalCollision && Math.abs(moved.y - v.y) > 1e-5;
        double impact = Math.max(cy ? Math.abs(v.y) : 0, Math.max(cx ? Math.abs(v.x) : 0, cz ? Math.abs(v.z) : 0));

        if (kind == ToyKind.EGG && impact > 0 && ToyPhysics.eggBreaks(impact, Toys.fragility(stack()))) {
            breakEgg(level, "s'est cassé (choc d'une chute de " + ToyPhysics.blocks(ToyPhysics.fallHeightFor(impact)) + " blocs)");
            return;
        }
        if (impact > 0.15) landSound(level, impact);

        Vec3 nv = new Vec3(cx ? ToyPhysics.bounce(v.x, kind.restitution) : v.x,
                cy ? ToyPhysics.bounce(v.y, kind.restitution) : v.y,
                cz ? ToyPhysics.bounce(v.z, kind.restitution) : v.z);
        if (onGround()) nv = new Vec3(nv.x * kind.groundFriction, nv.y, nv.z * kind.groundFriction);
        if (nv.horizontalDistanceSqr() < 1e-5 && onGround()) nv = new Vec3(0, nv.y, 0);
        setDeltaMovement(nv);
        if (moved.horizontalDistanceSqr() > 1e-4) {
            // Faces the way it goes (a duck turns with the current)
            float yaw = (float) (Mth.atan2(moved.x, moved.z) * -180 / Math.PI);
            setYRot(Mth.approachDegrees(getYRot(), yaw, isInWater() ? 3 : 20));
        }

        pushedByPlayers(level);
        if (checkHole(level, before)) return;
        if (CatchTargetBlock.tryCatch(level, this)) return;

        // A throw that came to rest: its distance
        if (flying) {
            boolean still = (onGround() || isInWater()) && getDeltaMovement().lengthSqr() < 0.003;
            restTicks = still ? restTicks + 1 : 0;
            if (restTicks >= 4) {
                flying = false;
                landed(level);
            }
        }
    }

    private void landSound(ServerLevel level, double impact) {
        if (kind == ToyKind.DUCK) {
            if (squeakCooldown == 0 && impact > 0.3) squeak(level, 0.6f);
            return;
        }
        var sound = switch (kind) {
            case BALL -> SoundEvents.SLIME_JUMP_SMALL;
            case GOLF_BALL -> SoundEvents.BAMBOO_WOOD_HIT;
            case BEANBAG -> SoundEvents.WOOL_FALL;
            default -> SoundEvents.STONE_HIT;
        };
        level.playSound(null, blockPosition(), sound, SoundSource.NEUTRAL, (float) Math.min(0.8, impact), kind == ToyKind.GOLF_BALL ? 1.8f : 1.3f);
    }

    public void squeak(ServerLevel level, float volume) {
        squeakCooldown = 6;
        level.playSound(null, getX(), getY(), getZ(), Toys.SQUEAK, SoundSource.NEUTRAL, volume, 0.9f + random.nextFloat() * 0.25f);
    }

    /** Players walking into it push it along (football with a ball, a nudge for the rest). */
    private void pushedByPlayers(ServerLevel level) {
        AABB box = getBoundingBox().inflate(0.15, 0, 0.15);
        for (Player p : level.getEntitiesOfClass(Player.class, box, pl -> !pl.isSpectator())) {
            Vec3 away = position().subtract(p.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 1e-6) continue;
            double strength = kind == ToyKind.BALL ? 0.28 : kind == ToyKind.GOLF_BALL ? 0.12 : 0.08;
            Vec3 run = p.getDeltaMovement().multiply(1, 0, 1);
            Vec3 push = away.normalize().scale(strength + Math.max(0, run.dot(away.normalize())) * 0.8);
            setDeltaMovement(getDeltaMovement().add(push.x, kind == ToyKind.BALL ? 0.05 : 0, push.z));
        }
    }

    // ---------------------------------------------------------------- golf

    private boolean checkHole(ServerLevel level, Vec3 before) {
        for (BlockPos pos : new BlockPos[]{blockPosition(), blockPosition().below()}) {
            BlockState st = level.getBlockState(pos);
            if (!(st.getBlock() instanceof GolfHoleBlock)) continue;
            double rim = pos.getY() + 1;
            if (getY() > rim + 0.05) continue;
            double speed = getDeltaMovement().horizontalDistance();
            if (!ToyPhysics.dropsInCup(before.x, before.z, getX(), getZ(), pos.getX() + 0.5, pos.getZ() + 0.5, GolfHoleBlock.CUP_RADIUS, speed)
                    && !(getY() < rim - 0.2)) continue;
            holedIn = pos.immutable();
            setPos(pos.getX() + 0.5, pos.getY() + GolfHoleBlock.CUP_BOTTOM, pos.getZ() + 0.5);
            setDeltaMovement(Vec3.ZERO);
            flying = false;
            level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 1f, 1.4f);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0);
            String p = thrower.isEmpty() ? "?" : thrower;
            int n = Math.max(1, strokes);
            int total = Toys.TALLY.holed(p, n);
            Component line = Component.literal("⛳ " + p + " : dans le trou en " + n + " coup" + (n > 1 ? "s" : "")).withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(" (" + Toys.frName(stack()) + ", total " + total + ")").withStyle(ChatFormatting.GRAY))
                    .append(Toys.noteButton(p, n, ResultSheet.Kind.COUNT, n + " coups"))
                    .append(Toys.noteButton(p, total, ResultSheet.Kind.COUNT, "total " + total));
            Toys.tellOrganisers(level.getServer(), line);
            ServerPlayer sp = level.getServer().getPlayerList().getPlayerByName(p);
            if (sp != null) sp.sendOverlayMessage(Component.literal("⛳ Dans le trou en " + n + " coup" + (n > 1 ? "s" : "") + " !")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            return true;
        }
        return false;
    }

    /** A swing of the golf club (power 0 to 1); a putt (sneaking) stays on the ground. */
    public void struck(ServerPlayer p, double power, boolean putt) {
        thrownBy(p);
        club = true;
        strokes++;
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-6) look = new Vec3(0, 0, 1);
        look = look.normalize();
        // Minigolf strengths: a full putt rolls about ten blocks, a full swing about twenty-five
        double speed = putt ? 0.05 + power * 0.40 : 0.15 + power * 0.75;
        double lift = putt ? 0 : 0.08 + power * 0.22;
        setDeltaMovement(look.x * speed, lift, look.z * speed);
        ServerLevel level = (ServerLevel) level();
        level.playSound(null, blockPosition(), SoundEvents.BAMBOO_WOOD_HIT, SoundSource.PLAYERS, 1f, putt ? 1.9f : 1.5f);
        p.sendOverlayMessage(Component.literal("⛳ Coup " + strokes + (putt ? " (putt)" : "") + " · force " + Math.round(power * 100) + " %")
                .withStyle(ChatFormatting.GREEN));
        if (kind == ToyKind.EGG && ToyPhysics.eggBreaks(speed * 0.6, Toys.fragility(stack())))
            breakEgg(level, "s'est cassé sous le club");
    }

    // ---------------------------------------------------------------- landing and the egg

    private void landed(ServerLevel level) {
        if (throwFrom == null || thrower.isEmpty() || club) return;
        double d = ToyPhysics.flatDistance(throwFrom.x, throwFrom.z, getX(), getZ());
        if (d < 1.5) return;
        boolean best = Toys.TALLY.thrown(thrower, d);
        ServerPlayer sp = level.getServer().getPlayerList().getPlayerByName(thrower);
        Component m = Component.literal("↔ " + ToyPhysics.blocksText(d) + (best ? " (record)" : "")).withStyle(ChatFormatting.GOLD);
        if (sp != null) sp.sendOverlayMessage(m);
        // Organisers near the throw see it with the button to write it down
        for (ServerPlayer o : Toys.organisers(level.getServer())) {
            if (o.level() != level || o.distanceToSqr(this) > 64 * 64 || o == sp && !Permissions.isAdmin(sp)) continue;
            o.sendSystemMessage(Component.literal("↔ " + thrower + " : lancer de " + ToyPhysics.blocksText(d)).withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(" (" + Toys.frName(stack()) + ")").withStyle(ChatFormatting.GRAY))
                    .append(Toys.noteButton(thrower, d, ResultSheet.Kind.DISTANCE, ToyPhysics.blocksText(d))));
        }
    }

    public void breakEgg(ServerLevel level, String how) {
        level.playSound(null, blockPosition(), SoundEvents.TURTLE_EGG_BREAK, SoundSource.NEUTRAL, 1f, 1.1f);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.EGG), getX(), getY() + 0.1, getZ(), 14, 0.1, 0.05, 0.1, 0.08);
        level.sendParticles(ParticleTypes.FALLING_HONEY, getX(), getY() + 0.05, getZ(), 4, 0.15, 0, 0.15, 0);
        String p = thrower.isEmpty() ? null : thrower;
        if (p != null) Toys.TALLY.brokenEgg(p);
        Toys.tellOrganisers(level.getServer(), Component.literal("◇ L'" + Toys.frName(stack()) + (p != null ? " de " + p : "") + " " + how)
                .withStyle(ChatFormatting.YELLOW));
        ServerPlayer sp = p == null ? null : level.getServer().getPlayerList().getPlayerByName(p);
        if (sp != null) sp.sendOverlayMessage(Component.literal("◇ Crac ! L'œuf est cassé").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        discard();
    }

    // ---------------------------------------------------------------- players

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return stack().copy();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64 * 64;
    }

    /** Right click: pick it up. An organiser sneaking toggles "hidden" instead (and sees what it remembers). */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp) || !(level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        if (sp.isShiftKeyDown() && Permissions.isAdmin(sp)) {
            hidden = !hidden;
            sp.sendOverlayMessage(Component.literal((hidden ? "✦ Caché" : "◉ Plus caché") + " : " + Toys.frName(stack())
                    + (thrower.isEmpty() ? "" : " · dernier joueur " + thrower) + (strokes > 0 ? " · " + strokes + " coup(s)" : "")).withStyle(ChatFormatting.GOLD));
            return InteractionResult.SUCCESS;
        }
        pickUp(level, sp);
        return InteractionResult.SUCCESS;
    }

    private void pickUp(ServerLevel level, ServerPlayer sp) {
        ItemStack stack = stack().copy();
        if (kind == ToyKind.DUCK) squeak(level, 0.8f);
        else level.playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1.4f);
        if (hidden && Toys.counts(sp)) {
            String who = Toys.who(sp);
            String name = Toys.frName(stack);
            int total = Toys.TALLY.found(who, name);
            int left = ToyCommands.hiddenLeft(level.getServer()) - 1;
            sp.sendOverlayMessage(Component.literal("✔ Trouvé : " + name + " (" + total + ")").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            Toys.tellOrganisers(level.getServer(), Component.literal("✔ " + who + " a trouvé " + (kind.female ? "une " : "un ") + name).withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(" (" + total + " en tout, encore " + left + " caché" + (left > 1 ? "s" : "") + ")").withStyle(ChatFormatting.GRAY))
                    .append(Toys.noteButton(who, total, ResultSheet.Kind.COUNT, String.valueOf(total))));
        }
        if (!sp.getInventory().add(stack)) sp.spawnAtLocation(level, stack);
        discard();
    }

    /** A hit: a squeak and a hop, a kick, or a swing with the golf club. */
    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (!(attacker instanceof ServerPlayer sp) || !(level() instanceof ServerLevel level)) return true;
        if (sp.getMainHandItem().is(Toys.GOLF_CLUB)) {
            double power = sp.getAttackStrengthScale(0.5f);
            sp.resetAttackStrengthTicker();
            holedIn = null;
            struck(sp, power, sp.isShiftKeyDown());
            return true;
        }
        if (kind == ToyKind.EGG) {
            thrower = Toys.who(sp);
            breakEgg(level, "a été écrasé");
            return true;
        }
        if (kind == ToyKind.DUCK) squeak(level, 1f);
        Vec3 look = sp.getLookAngle().multiply(1, 0, 1).normalize();
        double k = kind == ToyKind.BALL ? 0.7 : 0.25;
        thrownBy(sp);
        setDeltaMovement(getDeltaMovement().add(look.x * k, kind == ToyKind.DUCK ? 0.25 : 0.2 + k * 0.3, look.z * k));
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (kind == ToyKind.EGG && !isRemoved()) breakEgg(level, "a été touché");
        else if (kind == ToyKind.DUCK && squeakCooldown == 0) squeak(level, 1f);
        return false;
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        setStack(in.read("Item", ItemStack.CODEC).orElse(new ItemStack(Toys.TOY_ITEMS.get(ToyKind.DUCK))));
        hidden = in.getBooleanOr("Hidden", false);
        thrower = in.getStringOr("Thrower", "");
        strokes = in.getIntOr("Strokes", 0);
        in.read("HoledIn", BlockPos.CODEC).ifPresent(p -> holedIn = p);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.store("Item", ItemStack.CODEC, stack());
        out.putBoolean("Hidden", hidden);
        out.putString("Thrower", thrower);
        out.putInt("Strokes", strokes);
        if (holedIn != null) out.store("HoledIn", BlockPos.CODEC, holedIn);
    }

    /** For the targets: who threw it and how far from the target. */
    public String thrower() {
        return thrower;
    }

    public double throwDistanceTo(double x, double z) {
        return throwFrom == null ? 0 : ToyPhysics.flatDistance(throwFrom.x, throwFrom.z, x, z);
    }
}
