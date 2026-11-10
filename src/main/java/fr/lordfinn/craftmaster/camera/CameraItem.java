package fr.lordfinn.craftmaster.camera;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.bust.Busts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * "Caméra de studio": used on the top of a block, stands a camera there (next free number), its lens turned the way
 * the player looks. Only for those who may drive the cameras; frame it afterwards with {@code /cam aim}, or place
 * it exactly with {@code /cam here}.
 */
public class CameraItem extends Item {

    public CameraItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getClickedFace() != Direction.UP) return InteractionResult.PASS;
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer player))
            return InteractionResult.SUCCESS;
        if (!Permissions.canDriveCameras(player)) {
            player.sendOverlayMessage(Component.literal("Réservé à la réalisation (organisateurs)").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        Vec3 at = ctx.getClickLocation();
        // In the lieu / zone the operator works in (/cam focus), like the keys 1–9
        String group = fr.lordfinn.craftmaster.broadcast.Director.focus();
        int number = Cameras.list().nextFree(group);
        CameraSpot spot = new CameraSpot(number, null, "", Busts.worldId(level), at.x, at.y + CameraEntity.LENS_HEIGHT, at.z,
                player.getYRot(), player.getXRot(), 0, CameraSpot.BREATHING, CameraSpot.lieuOf(group), CameraSpot.zoneOf(group));
        Cameras.place(level.getServer(), spot);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 1f, 1.2f);
        player.sendSystemMessage(Component.literal("Caméra " + number + " posée (" + spot.placeName() + "). ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Clic droit dessus pour la régler, /cam aim " + spot.key() + " pour la viser, /cam lieu "
                        + spot.key() + " <lieu> [zone] pour changer de lieu.").withStyle(ChatFormatting.GRAY)));
        ItemStack stack = ctx.getItemInHand();
        if (!player.isCreative()) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.craftmaster.camera.hint").withStyle(ChatFormatting.GRAY));
    }
}
