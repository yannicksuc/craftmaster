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
        /** Fixed on a side, it stands upright on an arm (see {@link #ROD_REACH}), turned by its yaw like on a floor. */
        boolean standing;
        /** Moved toward its support / up so that its foot rests on the arm (blocks). */
        float reach, lift;
        /** On an ordinary wall the studio light brings its own arm: a short rod with its plate on the wall. */
        final ItemStackRenderState bracket = new ItemStackRenderState();
    }

    /**
     * On a rig rod's side a light stands on the rod's arm, which reaches the face between both blocks: drawn
     * {@code ROD_REACH} toward the rod and {@code ROD_LIFT} up, its foot on the arm's end cap (top at 10 px). On a
     * wall, the studio light stands on its bracket's tube (top at 9 px), in the middle of its block.
     */
    private static final float ROD_REACH = 6 / 16f, ROD_LIFT = 10 / 16f, BRACKET_LIFT = 9 / 16f;
    private static final Identifier BRACKET = fr.lordfinn.craftmaster.CraftMaster.id("furniture/rig_arm");

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
        s.standing = false;
        s.bracket.clear();
        if (state.getBlock() instanceof FurnitureBlock) {
            s.face = state.getValue(FurnitureBlock.FACE);
            s.wall = state.getValue(FurnitureBlock.FACING);
            s.yaw = state.getValue(FurnitureBlock.ROTATION) * 45f;
            if (s.face == AttachFace.WALL) {
                boolean onRod = be.getLevel() != null && FurnitureBlock.onRodSide(be.getLevel(), be.getBlockPos(), state);
                if (onRod) {
                    s.standing = true;
                    s.reach = ROD_REACH;
                    s.lift = ROD_LIFT;
                } else if (state.getBlock() == Furniture.STUDIO_LIGHT) {
                    s.standing = true;
                    s.reach = 0;
                    s.lift = BRACKET_LIFT;
                    items.updateForTopItem(s.bracket, stack(BRACKET), ItemDisplayContext.NONE, be.getLevel(), null, 0);
                }
                // (a softbox on an ordinary wall stands with its back to it, as before)
            }
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
        AttachFace face = s.face;
        if (s.standing) {
            Direction toSupport = s.wall.getOpposite();
            if (!s.bracket.isEmpty()) {
                // The bracket: the rod's arm turned from the block's centre to the wall, its cap flat on it
                pose.pushPose();
                pose.translate(0.5, 0.5, 0.5);
                pose./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(-toSupport.toYRot()));
                pose./*$ pose_rotate {*/rotate/*$}*/(Axis.XP.rotationDegrees(90));
                s.bracket.submit(pose, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
            // Stands on the arm, as on a floor
            pose.translate(toSupport.getStepX() * s.reach, s.lift, toSupport.getStepZ() * s.reach);
            s.face = AttachFace.FLOOR;
        }
        if (s.mount == Furniture.Mount.FRAME) frame(s, pose);
        else display(s, pose);
        s.item.submit(pose, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        s.face = face;
        pose.popPose();
    }

    /** ItemFrameRenderer: an invisible frame in this block, against the face it was placed on. */
    private static void frame(State s, PoseStack pose) {
        pose.translate(0.5, 0.5, 0.5);
        switch (s.face) {
            case FLOOR -> {
                pose./*$ pose_rotate {*/rotate/*$}*/(Axis.XP.rotationDegrees(-90));
                pose./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(180));
            }
            case CEILING -> {
                pose./*$ pose_rotate {*/rotate/*$}*/(Axis.XP.rotationDegrees(90));
                pose./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(180));
            }
            case WALL -> pose./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(180 - s.wall.toYRot()));
        }
        pose.translate(0, 0, 0.5);
        // The frame's own rotation turns the piece (8 steps of 45°); a ceiling frame sees it from below
        if (s.face != AttachFace.WALL) pose./*$ pose_rotate {*/rotate/*$}*/(Axis.ZP.rotationDegrees(s.face == AttachFace.CEILING ? -s.yaw : s.yaw));
        pose.scale(0.5f, 0.5f, 0.5f);
    }

    /** ItemDisplayRenderer: an item display standing on the face it was placed on (the softboxes). */
    private static void display(State s, PoseStack pose) {
        switch (s.face) {
            case FLOOR -> {
                pose.translate(0.5, 0, 0.5);
                pose./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(-s.yaw));
            }
            case CEILING -> {
                pose.translate(0.5, 1, 0.5);
                pose./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(-s.yaw));
                pose./*$ pose_rotate {*/rotate/*$}*/(Axis.XP.rotationDegrees(180));
            }
            case WALL -> {
                // Upright, its back to the wall, shining out of it (its stand reads as the wall bracket)
                pose.translate(0.5, 0, 0.5);
                pose./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(-s.wall.toYRot()));
            }
        }
        pose.translate(0, 0.015, 0);
        pose./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(180));
    }

    @Override
    public boolean shouldRenderOffScreen() {
        // The throne is 4.5 blocks tall: keep drawing it when its base block is off screen
        return true;
    }
}
