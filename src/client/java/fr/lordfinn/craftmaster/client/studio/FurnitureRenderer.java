package fr.lordfinn.craftmaster.client.studio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.studio.Furniture;
import fr.lordfinn.craftmaster.studio.FurnitureBlock;
import fr.lordfinn.craftmaster.studio.FurnitureBlockEntity;
import fr.lordfinn.craftmaster.studio.StudioSeatBlock;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws LordFinn's furniture ({@link Furniture}) exactly like his server did: the same item model, shown the way
 * an invisible item frame lying on the floor (or hanging on a wall / the ceiling) shows it, or for the softboxes
 * the way an item display does. The transforms below are the vanilla item frame / item display ones (a softbox
 * on a wall stands upright, its back to the wall).
 */
public class FurnitureRenderer implements BlockEntityRenderer<FurnitureBlockEntity, FurnitureRenderer.State> {

    public static class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        Furniture.Mount mount = Furniture.Mount.FRAME;
        AttachFace face = AttachFace.FLOOR;
        Direction wall = Direction.NORTH;
        float yaw;
    }

    /** One stack per model: a plain item carrying the furniture's item model, like on the server. */
    private static final Map<Identifier, ItemStack> STACKS = new HashMap<>();

    private final ItemModelResolver items;

    public FurnitureRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.itemModelResolver();
    }

    public static ItemStack stack(Identifier model) {
        return STACKS.computeIfAbsent(model, m -> {
            ItemStack s = new ItemStack(Items.PAPER);
            s.set(DataComponents.ITEM_MODEL, m);
            return s;
        });
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FurnitureBlockEntity be, State s, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, camera, crumbling);
        BlockState state = be.getBlockState();
        Furniture.Look look = Furniture.look(state);
        if (look == null) {
            s.item.clear();
            return;
        }
        s.mount = look.mount();
        if (state.getBlock() instanceof FurnitureBlock) {
            s.face = state.getValue(FurnitureBlock.FACE);
            s.wall = state.getValue(FurnitureBlock.FACING);
            s.yaw = state.getValue(FurnitureBlock.ROTATION) * 45f;
        } else {
            // Seats: FACING is where the seated player looks
            s.face = AttachFace.FLOOR;
            s.yaw = state.getValue(StudioSeatBlock.FACING).toYRot();
        }
        ItemDisplayContext ctx = s.mount == Furniture.Mount.FRAME ? ItemDisplayContext.FIXED : ItemDisplayContext.HEAD;
        items.updateForTopItem(s.item, stack(look.model()), ctx, be.getLevel(), null, 0);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (s.item.isEmpty()) return;
        pose.pushPose();
        if (s.mount == Furniture.Mount.FRAME) frame(s, pose);
        else display(s, pose);
        s.item.submit(pose, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    /** ItemFrameRenderer: an invisible frame in this block, against the face it was placed on. */
    private static void frame(State s, PoseStack pose) {
        pose.translate(0.5, 0.5, 0.5);
        switch (s.face) {
            case FLOOR -> {
                pose.rotate(Axis.XP.rotationDegrees(-90));
                pose.rotate(Axis.YP.rotationDegrees(180));
            }
            case CEILING -> {
                pose.rotate(Axis.XP.rotationDegrees(90));
                pose.rotate(Axis.YP.rotationDegrees(180));
            }
            case WALL -> pose.rotate(Axis.YP.rotationDegrees(180 - s.wall.toYRot()));
        }
        pose.translate(0, 0, 0.5);
        // The frame's own rotation turns the piece (8 steps of 45°); a ceiling frame sees it from below
        if (s.face != AttachFace.WALL) pose.rotate(Axis.ZP.rotationDegrees(s.face == AttachFace.CEILING ? -s.yaw : s.yaw));
        pose.scale(0.5f, 0.5f, 0.5f);
    }

    /** ItemDisplayRenderer: an item display standing on the face it was placed on (the softboxes). */
    private static void display(State s, PoseStack pose) {
        switch (s.face) {
            case FLOOR -> {
                pose.translate(0.5, 0, 0.5);
                pose.rotate(Axis.YP.rotationDegrees(-s.yaw));
            }
            case CEILING -> {
                pose.translate(0.5, 1, 0.5);
                pose.rotate(Axis.YP.rotationDegrees(-s.yaw));
                pose.rotate(Axis.XP.rotationDegrees(180));
            }
            case WALL -> {
                // Upright, its back to the wall, shining out of it (its stand reads as the wall bracket)
                pose.translate(0.5, 0, 0.5);
                pose.rotate(Axis.YP.rotationDegrees(-s.wall.toYRot()));
            }
        }
        pose.translate(0, 0.015, 0);
        pose.rotate(Axis.YP.rotationDegrees(180));
    }

    @Override
    public boolean shouldRenderOffScreen() {
        // The throne is 4.5 blocks tall: keep drawing it when its base block is off screen
        return true;
    }
}
