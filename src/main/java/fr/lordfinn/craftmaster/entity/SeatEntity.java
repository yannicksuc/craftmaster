package fr.lordfinn.craftmaster.entity;

import fr.lordfinn.craftmaster.block.ChairBlock;
import fr.lordfinn.craftmaster.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Invisible entity the player rides while sitting on a chair. It removes itself as soon as nobody sits on it or the
 * chair is broken. A player who logs out seated comes back seated (the seat is saved with them).
 */
public class SeatEntity extends Entity {

    /** Where the rider's attachment point sits below the seat top: players hang 0.6 below it, which puts the hips on the seat. */
    private static final double RIDE_OFFSET = 0.15;

    /** How far the seated player can turn away from the chair's direction (degrees). */
    private static final float MAX_TURN = 100f;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static boolean occupied(ServerLevel level, BlockPos pos) {
        return !level.getEntitiesOfClass(SeatEntity.class, new AABB(pos).expandTowards(0, 1, 0), Entity::isVehicle).isEmpty();
    }

    public static void sit(ServerLevel level, BlockPos pos, Direction facing, Player player) {
        SeatEntity seat = ModContent.SEAT.create(level, EntitySpawnReason.TRIGGERED);
        if (seat == null) return;
        seat.snapTo(pos.getX() + 0.5, pos.getY() + seatTop(level, pos) - RIDE_OFFSET, pos.getZ() + 0.5, facing.toYRot(), 0);
        level.addFreshEntity(seat);
        if (!player.startRiding(seat)) seat.discard();
    }

    @Override
    public void tick() {
        super.tick();
        // A high seat (the throne's cushion is above its block) sits in the block over its chair
        if (level() instanceof ServerLevel && (!isVehicle() || !(level().getBlockState(blockPosition()).getBlock() instanceof ChairBlock
                || level().getBlockState(blockPosition().below()).getBlock() instanceof ChairBlock)))
            discard();
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) {
        return Vec3.ZERO;
    }

    /** Getting up: in front of the chair if there is room, else standing on it. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos pos = chairPos();
        Direction facing = Direction.fromYRot(getYRot());
        Vec3 front = Vec3.atBottomCenterOf(pos.relative(facing));
        if (level().noCollision(passenger, passenger.getDimensions(Pose.STANDING).makeBoundingBox(front))
                && !level().getBlockState(pos.relative(facing).below()).isAir())
            return front;
        return new Vec3(pos.getX() + 0.5, pos.getY() + seatTop(level(), pos), pos.getZ() + 0.5);
    }

    /** The chair's block: the seat's own block, or the one under it for a high seat (the throne). */
    private BlockPos chairPos() {
        BlockPos pos = blockPosition();
        return level().getBlockState(pos).getBlock() instanceof ChairBlock ? pos : pos.below();
    }

    /** Seat height of the chair at {@code pos} (each kind of seat has its own: chairs, throne...). */
    private static double seatTop(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof ChairBlock chair ? chair.seatTop() : ChairBlock.SEAT_TOP;
    }

    // Same idea as boats: the body follows the chair, the head can look around within MAX_TURN
    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        super.positionRider(passenger, move);
        clampRotation(passenger);
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        clampRotation(passenger);
    }

    private void clampRotation(Entity passenger) {
        passenger.setYBodyRot(getYRot());
        float delta = Mth.wrapDegrees(passenger.getYRot() - getYRot());
        float clamped = Mth.clamp(delta, -MAX_TURN, MAX_TURN);
        passenger.yRotO += clamped - delta;
        passenger.setYRot(passenger.getYRot() + clamped - delta);
        passenger.setYHeadRot(passenger.getYRot());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {}
}
