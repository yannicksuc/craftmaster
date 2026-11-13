package fr.lordfinn.craftmaster.toy;

import fr.lordfinn.craftmaster.trial.ResultSheet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

/**
 * A balloon. Hold the use button to blow it up, a puff at a time: it grows in the hand, and bursts if you keep
 * going (each balloon has its own limit, unknown). Inflated, use it on a block to tie it there (a
 * {@link BalloonEntity}): a hit or an arrow pops it.
 */
public class BalloonItem extends Item {

    public static final int DEFAULT_COLOR = 0xB02E26;

    public BalloonItem(Properties props) {
        super(props);
    }

    public static float size(ItemStack stack) {
        return Toys.data(stack).getFloatOr("Size", Balloons.MIN);
    }

    static void setSize(ItemStack stack, float size, float burstAt) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
            t.putFloat("Size", size);
            t.putFloat("BurstAt", burstAt);
        });
        // The hand model picks its size from this
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(Balloons.fullness(size)), List.of(), List.of(), List.of()));
    }

    /** A balloon dyed an in-between colour takes the nearest dye's (it has one texture per dye). */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, net.minecraft.world.entity.Entity owner, net.minecraft.world.entity.EquipmentSlot slot) {
        Toys.snapDyeColour(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        ItemStack stack = ctx.getItemInHand();
        float size = size(stack);
        if (size < Balloons.PLACEABLE) return InteractionResult.PASS;
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
        Vec3 hit = ctx.getClickLocation();
        BalloonEntity b = BalloonEntity.create(level, stack, size, new Vec3(hit.x, ctx.getClickedPos().getY() + 1.0 + 0.05, hit.z), p);
        if (ctx.getClickedFace() != net.minecraft.core.Direction.UP)
            b.setPos(hit.x + ctx.getClickedFace().getStepX() * (size / 2 + 0.02), hit.y - size / 2, hit.z + ctx.getClickedFace().getStepZ() * (size / 2 + 0.02));
        if (!level.noCollision(b, b.getBoundingBox().deflate(0.05))) {
            p.sendOverlayMessage(Component.literal("Pas la place pour ce ballon").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        level.addFreshEntity(b);
        level.playSound(null, b.blockPosition(), net.minecraft.sounds.SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.6f, 1.4f);
        stack.consume(1, p);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            // Its own burst limit first (so it no longer stacks with the flat ones), then the others of the stack
            // go back to the inventory: only one balloon is blown up
            ItemStack rest = stack.getCount() > 1 ? stack.copyWithCount(stack.getCount() - 1) : ItemStack.EMPTY;
            stack.setCount(1);
            if (!Toys.data(stack).contains("BurstAt")) setSize(stack, size(stack), Balloons.burstAt(player.getRandom().nextDouble()));
            if (!rest.isEmpty() && !player.getInventory().add(rest)) player.drop(rest, false/*$ drop_prediction {*/, net.minecraft.util.Prediction.SERVER_ONLY/*$}*/);
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel sl) || !(entity instanceof ServerPlayer p)) return;
        int used = getUseDuration(stack, entity) - remaining;
        if (used == 0 || used % Balloons.PUFF_TICKS != 0) return;
        float burstAt = Toys.data(stack).getFloatOr("BurstAt", Balloons.MAX);
        float size = Balloons.puff(size(stack));
        if (Balloons.bursts(size, burstAt)) {
            burst(sl, p, stack, size);
            return;
        }
        setSize(stack, size, burstAt);
        sl.playSound(null, p.getX(), p.getEyeY(), p.getZ(), Toys.PUFF, SoundSource.PLAYERS, 0.7f, Balloons.puffPitch(size));
        p.sendOverlayMessage(Component.literal("○ " + ToyPhysics.blocksText(size)).withStyle(ChatFormatting.RED));
    }

    private static void burst(ServerLevel level, ServerPlayer p, ItemStack stack, float size) {
        p.stopUsingItem();
        int rgb = Toys.color(stack, DEFAULT_COLOR);
        String name = Toys.frName(stack);
        stack.shrink(1);
        Vec3 at = p.getEyePosition().add(p.getLookAngle().scale(0.6));
        level.playSound(null, at.x, at.y, at.z, Toys.POP, SoundSource.PLAYERS, 1.2f, 1f);
        level.sendParticles(new DustParticleOptions(rgb, 1.2f), at.x, at.y, at.z, 24, size * 0.3, size * 0.3, size * 0.3, 0.1);
        p.sendOverlayMessage(Component.literal("✸ Le ballon a éclaté !").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        String who = Toys.who(p);
        Toys.TALLY.popped(who);
        Toys.tellOrganisers(level.getServer(), Component.literal("✸ Le " + name + " de " + who + " a éclaté en le gonflant (" + ToyPhysics.blocks(size) + " blocs)")
                .withStyle(ChatFormatting.RED));
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (!(level instanceof ServerLevel sl) || !(entity instanceof ServerPlayer p) || stack.isEmpty()) return true;
        float size = size(stack);
        String who = Toys.who(p);
        Toys.tellOrganisers(sl.getServer(), Component.literal("○ " + who + " : ballon de " + ToyPhysics.blocksText(size)).withStyle(ChatFormatting.RED)
                .append(Toys.noteButton(who, size, ResultSheet.Kind.DISTANCE, ToyPhysics.blocksText(size))));
        return true;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.TOOT_HORN;
    }

    @Override
    public Component getName(ItemStack stack) {
        Component base = stack.has(DataComponents.DYED_COLOR)
                ? Component.translatable(getDescriptionId() + ".colored",
                Component.translatable("color.craftmaster." + ToyPhysics.DYES[ToyPhysics.nearestDye(Toys.color(stack, DEFAULT_COLOR))] + ".m"))
                : super.getName(stack);
        return base;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        float size = size(stack);
        if (size > Balloons.MIN + 0.01f) out.accept(Component.translatable("item.craftmaster.balloon.size", ToyPhysics.blocks(size)).withStyle(ChatFormatting.RED));
        out.accept(Component.translatable("item.craftmaster.balloon.hint").withStyle(ChatFormatting.GRAY));
    }
}
