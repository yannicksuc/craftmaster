package fr.lordfinn.craftmaster.item;

import fr.lordfinn.craftmaster.block.CurtainBlock;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.curtain.Curtains;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Curtain remote: right-click a curtain to link it (and open/close it), then right-click anywhere to open/close it
 * from the audience or backstage.
 */
public class CurtainRemoteItem extends Item {

    public CurtainRemoteItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (!(level.getBlockState(pos).getBlock() instanceof CurtainBlock)) return InteractionResult.PASS;
        if (level instanceof ServerLevel server) {
            CompoundTag link = new CompoundTag();
            link.putString("world", Busts.worldId(server));
            link.putInt("x", pos.getX());
            link.putInt("y", pos.getY());
            link.putInt("z", pos.getZ());
            CustomData.set(DataComponents.CUSTOM_DATA, ctx.getItemInHand(), link);
            toggle(server, pos, ctx.getPlayer());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        CompoundTag link = link(player.getItemInHand(hand));
        ServerLevel target = link == null ? null : Busts.level(server.getServer(), link.getStringOr("world", ""));
        if (target == null) {
            say(player, Component.translatable("message.craftmaster.remote_unlinked").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        toggle(target, new BlockPos(link.getIntOr("x", 0), link.getIntOr("y", 0), link.getIntOr("z", 0)), player);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void toggle(ServerLevel level, BlockPos pos, Player player) {
        Boolean open = Curtains.animate(level, pos, null);
        if (open == null) say(player, Component.translatable("message.craftmaster.remote_lost").withStyle(ChatFormatting.RED));
        else say(player, Component.translatable(open ? "message.craftmaster.curtain_opening" : "message.craftmaster.curtain_closing")
                .withStyle(ChatFormatting.GOLD));
    }

    private static void say(Player player, Component msg) {
        if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(msg);
    }

    private static CompoundTag link(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return null;
        CompoundTag t = d.copyTag();
        return t.contains("x") ? t : null;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return link(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        CompoundTag link = link(stack);
        if (link == null) {
            out.accept(Component.translatable("item.craftmaster.curtain_remote.unlinked").withStyle(ChatFormatting.GRAY));
        } else {
            out.accept(Component.translatable("item.craftmaster.curtain_remote.linked",
                    link.getIntOr("x", 0), link.getIntOr("y", 0), link.getIntOr("z", 0)).withStyle(ChatFormatting.GRAY));
        }
    }
}
