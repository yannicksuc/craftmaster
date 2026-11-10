package fr.lordfinn.craftmaster.client.studio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.studio.PrizeTableBlock;
import fr.lordfinn.craftmaster.studio.PrizeTableBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** The prizes lying on the prize table, one per quarter of the top, turned toward the audience. */
public class PrizeTableRenderer implements BlockEntityRenderer<PrizeTableBlockEntity, PrizeTableRenderer.State> {

    public static class State extends BlockEntityRenderState {
        float yaw;
        final List<ItemStackRenderState> items = new ArrayList<>();
        final List<Integer> slots = new ArrayList<>();
    }

    private final ItemModelResolver items;

    public PrizeTableRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PrizeTableBlockEntity be, State s, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, camera, crumbling);
        s.yaw = be.getBlockState().getValue(PrizeTableBlock.FACING).toYRot();
        s.items.clear();
        s.slots.clear();
        int seed = (int) be.getBlockPos().asLong();
        for (int i = 0; i < PrizeTableBlockEntity.SLOTS; i++) {
            ItemStack stack = be.items().get(i);
            if (stack.isEmpty()) continue;
            ItemStackRenderState r = new ItemStackRenderState();
            items.updateForTopItem(r, stack, ItemDisplayContext.FIXED, be.getLevel(), null, seed + i);
            s.items.add(r);
            s.slots.add(i);
        }
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        for (int i = 0; i < s.items.size(); i++) {
            double[] at = PrizeTableBlockEntity.slotOffset(s.slots.get(i));
            poseStack.pushPose();
            poseStack.translate(at[0], 1.0 + 0.012, at[1]);
            // Lying flat, its top toward the audience (the table's front)
            poseStack.rotate(Axis.YP.rotationDegrees(-s.yaw + 180));
            poseStack.rotate(Axis.XP.rotationDegrees(-90));
            poseStack.scale(0.42f, 0.42f, 0.42f);
            s.items.get(i).submit(poseStack, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }
}
