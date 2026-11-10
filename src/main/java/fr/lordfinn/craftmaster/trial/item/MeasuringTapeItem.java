package fr.lordfinn.craftmaster.trial.item;

import fr.lordfinn.craftmaster.trial.Judging;
import fr.lordfinn.craftmaster.trial.ResultSheet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Measuring tape: use it on a first block, then on a second one: the distance between their centres (straight
 * line, plus the horizontal distance and the height), shown along a line of gold dust. Sneak + use starts over.
 * Organisers get buttons to write the distance down for a contestant. The two last points also define a task
 * zone ({@code /craftMasterZone create <name>}).
 */
public class MeasuringTapeItem extends Item {

    /** Last two points measured by each player (for the zones). */
    private static final Map<UUID, BlockPos[]> LAST = new HashMap<>();

    public MeasuringTapeItem(Properties props) {
        super(props);
    }

    public static BlockPos[] lastPoints(UUID player) {
        return LAST.get(player);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        ItemStack stack = ctx.getItemInHand();
        BlockPos pos = ctx.getClickedPos().immutable();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (player.isShiftKeyDown() || !tag.contains("ax")) {
            tag.putInt("ax", pos.getX());
            tag.putInt("ay", pos.getY());
            tag.putInt("az", pos.getZ());
            CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
            player.sendOverlayMessage(Component.literal("📏 Point A : " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " — utilise le mètre sur le point B")
                    .withStyle(ChatFormatting.GOLD));
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.6f, 1.6f);
            return InteractionResult.SUCCESS_SERVER;
        }
        BlockPos a = new BlockPos(tag.getIntOr("ax", 0), tag.getIntOr("ay", 0), tag.getIntOr("az", 0));
        stack.remove(DataComponents.CUSTOM_DATA);
        LAST.put(player.getUUID(), new BlockPos[]{a, pos});
        Vec3 va = Vec3.atCenterOf(a), vb = Vec3.atCenterOf(pos);
        double d = va.distanceTo(vb);
        double flat = Math.sqrt(Math.pow(vb.x - va.x, 2) + Math.pow(vb.z - va.z, 2));
        int height = pos.getY() - a.getY();
        // A line of gold dust between the two points
        DustParticleOptions dust = new DustParticleOptions(0xFFC23D, 1.2f);
        int steps = Math.max(2, (int) (d * 4));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = va.lerp(vb, i / (double) steps);
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.8f, 1.1f);
        String text = ResultSheet.format(ResultSheet.Kind.DISTANCE, d);
        player.sendOverlayMessage(Component.literal("📏 " + text).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        Judging.offerMeasure(player, Component.literal("📏 Distance : ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(" (à plat " + ResultSheet.format(ResultSheet.Kind.DISTANCE, flat) + ", hauteur "
                        + (height >= 0 ? "+" : "") + height + " m)").withStyle(ChatFormatting.GRAY)), d, ResultSheet.Kind.DISTANCE);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(DataComponents.CUSTOM_DATA);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.craftmaster.measuring_tape.hint").withStyle(ChatFormatting.GRAY));
    }
}
