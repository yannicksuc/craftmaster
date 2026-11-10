package fr.lordfinn.craftmaster.camera;

import fr.lordfinn.craftmaster.Permissions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * "Repère de travelling" (a clapperboard): bound to a path ({@code /cam trajet <nom> objet}), a right click adds a
 * step at the player's eyes, looking where they look; sneaking + right click removes the last step. The path's name
 * is kept in the item's custom data ({@code {path: "pont"}}). For those who may drive the cameras.
 */
public class PathMarkerItem extends Item {

    public PathMarkerItem(Properties properties) {
        super(properties);
    }

    /** The path this marker records, or "" if not bound yet. */
    public static String path(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr("path", "");
    }

    public static ItemStack bound(Item item, String path) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putString("path", path);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Repère de travelling : " + path).withStyle(ChatFormatting.GOLD));
        return stack;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (!Permissions.canDriveCameras(sp)) {
            sp.sendOverlayMessage(Component.literal("Réservé à la réalisation (organisateurs)").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        String path = path(player.getItemInHand(hand));
        if (path.isEmpty()) {
            sp.sendSystemMessage(Component.literal("Ce repère n'est lié à aucun trajet : /cam trajet <nom> objet").withStyle(ChatFormatting.YELLOW));
            return InteractionResult.FAIL;
        }
        String line = player.isShiftKeyDown()
                ? PathRecording.removeStep(sp.level().getServer(), path, -1)
                : PathRecording.addStep(sp.level().getServer(), sp, path, -1);
        boolean ok = !line.startsWith("⚠");
        sp.sendSystemMessage(Component.literal(line).withStyle(ok ? ChatFormatting.GOLD : ChatFormatting.RED));
        if (ok) level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.PLAYERS,
                0.8f, player.isShiftKeyDown() ? 0.7f : 1.4f);
        return ok ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        String path = path(stack);
        out.accept(Component.translatable(path.isEmpty() ? "item.craftmaster.path_marker.unbound" : "item.craftmaster.path_marker.hint", path)
                .withStyle(ChatFormatting.GRAY));
    }
}
