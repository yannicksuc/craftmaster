package fr.lordfinn.craftmaster.camera;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.bust.Busts;
import net.minecraft.ChatFormatting;
import fr.lordfinn.craftmaster.studio.Furniture;
import fr.lordfinn.craftmaster.studio.RigRodBlock;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * "Caméra de studio": used on a block, mounts a camera there (next free number, {@link CameraMount}), its lens turned
 * the way the player looks: on the floor (its own tripod), on a tripod stand or a rig rod, under a ceiling or a rod
 * (hung upside-down), on a wall (a small arm) or a rod's side. Only for those who may drive the cameras; frame it
 * afterwards with {@code /cm cam viser}, or place it exactly with {@code /cm cam ici}.
 */
public class CameraItem extends Item {

    public CameraItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level world = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();
        CameraMount mount = CameraMount.choose(face.ordinal(), support(world, pos, face));
        if (mount == null) return InteractionResult.PASS;
        if (!(world instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer player))
            return InteractionResult.SUCCESS;
        if (!Permissions.canDriveCameras(player)) {
            player.sendOverlayMessage(Component.literal("Réservé à la réalisation (organisateurs)").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        Vec3 at = ctx.getClickLocation();
        double[] anchor = mount.anchor(pos.getX(), pos.getY(), pos.getZ(), at.x, at.y, at.z);
        // In the lieu / zone the operator works in (/cm cam focus), like the keys 1–9
        String group = fr.lordfinn.craftmaster.broadcast.Director.focus();
        int number = Cameras.list().nextFree(group);
        CameraSpot spot = new CameraSpot(number, null, "", Busts.worldId(level), anchor[0], anchor[1] + mount.lensDy(), anchor[2],
                player.getYRot(), player.getXRot(), 0, CameraSpot.BREATHING, CameraSpot.lieuOf(group), CameraSpot.zoneOf(group), mount);
        // A camera picked up (Shift + hit) comes back with its settings, and its travellings once the player says how
        com.google.gson.JsonObject kit = CameraKit.kit(ctx.getItemInHand());
        spot = CameraKit.withSettings(spot, kit);
        Cameras.place(level.getServer(), spot);
        CameraKit.placed(player, spot, kit);
        level.playSound(null, anchor[0], anchor[1], anchor[2], SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 1f, 1.2f);
        player.sendSystemMessage(Component.literal("Caméra " + number + " posée " + mount.label() + " (" + spot.placeName() + "). ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Clic droit dessus pour la régler, /cm cam viser " + spot.key() + " pour la viser, /cm cam lieu "
                        + spot.key() + " <lieu> [zone] pour changer de lieu.").withStyle(ChatFormatting.GRAY)));
        ItemStack stack = ctx.getItemInHand();
        if (!player.isCreative()) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    /**
     * What the camera is used on, like the studio lights: a tripod stand, a rig rod, or an ordinary block whose face
     * can hold it (any top face, as before; a sturdy wall or ceiling); null when it cannot hold it.
     */
    private static CameraMount.Support support(Level level, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Furniture.TRIPOD)) return CameraMount.Support.TRIPOD;
        if (state.getBlock() instanceof RigRodBlock) return CameraMount.Support.ROD;
        if (face == Direction.UP || state.isFaceSturdy(level, pos, face)) return CameraMount.Support.BLOCK;
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.craftmaster.camera.hint").withStyle(ChatFormatting.GRAY));
    }
}
