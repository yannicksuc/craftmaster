package fr.lordfinn.craftmaster.toy;

import fr.lordfinn.craftmaster.Permissions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * The item of a toy. On a block: put it down right there (on a slab, in a pot, behind a block); in the air: hold
 * to wind up and let go to throw (like a bow, the longer the farther). Put down by an organiser it is hidden.
 */
public class ToyItem extends Item {

    private final ToyKind kind;

    public ToyItem(ToyKind kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    public ToyKind kind() {
        return kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
        Vec3 hit = ctx.getClickLocation();
        Direction face = ctx.getClickedFace();
        double hw = kind.width / 2 + 0.01;
        Vec3 pos = switch (face) {
            case UP -> hit;
            case DOWN -> hit.add(0, -kind.height - 0.01, 0);
            default -> hit.add(face.getStepX() * hw, -kind.height / 2, face.getStepZ() * hw);
        };
        ToyEntity toy = ToyEntity.create(level, ctx.getItemInHand(), pos, p.getYRot() + 180);
        if (!level.noCollision(toy, toy.getBoundingBox().deflate(0.02))) {
            p.sendOverlayMessage(Component.literal("Pas la place ici").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        boolean organiser = Permissions.isAdmin(p);
        toy.setHidden(organiser);
        level.addFreshEntity(toy);
        if (kind == ToyKind.DUCK) toy.squeak(level, 0.5f);
        else level.playSound(null, toy.blockPosition(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.PLAYERS, 0.5f, 1.3f);
        ctx.getItemInHand().consume(1, p);
        if (organiser) p.sendOverlayMessage(Component.literal("✦ Caché : " + Toys.frName(toy.stack())
                + " (" + ToyCommands.hiddenLeft(level.getServer()) + " en tout)").withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.TRIDENT;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (!(level instanceof ServerLevel sl) || !(entity instanceof ServerPlayer p)) return true;
        int used = getUseDuration(stack, entity) - remaining;
        double speed = ToyPhysics.throwSpeed(kind, used);
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        ToyEntity toy = ToyEntity.create(sl, stack, eye.add(look.scale(0.3)).subtract(0, kind.height / 2, 0), p.getYRot());
        toy.setDeltaMovement(look.scale(speed).add(p.getDeltaMovement().multiply(1, 0, 1)));
        toy.thrownBy(p);
        sl.addFreshEntity(toy);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5f, 0.6f + (float) ToyPhysics.charge(used) * 0.4f);
        if (kind == ToyKind.DUCK) toy.squeak(sl, 0.5f);
        stack.consume(1, p);
        return true;
    }

    /** A duck dyed an in-between colour takes the nearest dye's (it has one texture per dye). */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, net.minecraft.world.entity.Entity owner, net.minecraft.world.entity.EquipmentSlot slot) {
        if (kind == ToyKind.DUCK) Toys.snapDuckColour(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        if (kind == ToyKind.EGG && Toys.fragility(stack) <= 0) return Component.translatable("item.craftmaster.fragile_egg.thin");
        if (!kind.dyeable || !stack.has(DataComponents.DYED_COLOR)) return super.getName(stack);
        int dye = ToyPhysics.nearestDye(Toys.color(stack, kind.defaultColor));
        return Component.translatable(getDescriptionId() + ".colored",
                Component.translatable("color.craftmaster." + ToyPhysics.DYES[dye] + (kind.female ? ".f" : ".m")));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.craftmaster.toy.hint").withStyle(ChatFormatting.GRAY));
        if (kind == ToyKind.EGG) {
            double f = Toys.fragility(stack);
            out.accept((f <= 0 ? Component.translatable("item.craftmaster.fragile_egg.any")
                    : Component.translatable("item.craftmaster.fragile_egg.blocks", ToyPhysics.blocks(f))).withStyle(ChatFormatting.YELLOW));
        }
        if (kind == ToyKind.GOLF_BALL) out.accept(Component.translatable("item.craftmaster.golf_ball.hint").withStyle(ChatFormatting.GRAY));
    }
}
