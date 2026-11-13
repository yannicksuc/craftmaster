package fr.lordfinn.craftmaster.client.home;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.google.common.collect.ImmutableMultimap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.CraftMaster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.skull.SkullModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The pedestal table next to the throne with LordFinn's Golden Frog trophy on it: a dark oak top on a single
 * stripped dark oak post and a small foot (vanilla block textures), and the frog, a player head (vanilla's skull
 * model) wearing the head texture of the old server's HeadDatabase. The texture comes from Mojang's skin servers
 * like any player head; a copy shipped with the mod is used while it is not there (offline).
 * <p>
 * Table space: blocks, the floor's centre under the post at the origin, y up.
 */
public final class HomeTable {

    private HomeTable() {}

    private static final Identifier PLANKS = Identifier.withDefaultNamespace("textures/block/dark_oak_planks.png");
    private static final Identifier POST = Identifier.withDefaultNamespace("textures/block/stripped_dark_oak_log.png");
    /** Shipped copy of the Golden Frog's head texture. */
    private static final Identifier FROG_FALLBACK = CraftMaster.id("textures/entity/golden_frog.png");
    private static final String FROG_TEXTURES = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmNlYzMzYTI2MTA2ZTQ1NjE2Njk0ZWJjNDYwMmMwZmUyMTZmYTk2MDE0YjIyYmJlMzE2N2RhYmJlODFiMGQ2NiJ9fX0=";
    private static final ResolvableProfile FROG = ResolvableProfile.createResolved(new GameProfile(
            UUID.fromString("5f2c6e0a-7b1d-4c39-9a4e-3d8b0f6a1c27"), "GoldenFrog",
            new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", FROG_TEXTURES)))));

    /** Top height and half size, post half width, foot half size and thickness (blocks). */
    public static final float TOP_Y = 0.9f, TOP_HALF = 0.4f, TOP_THICK = 0.08f, POST_HALF = 0.09f, FOOT_HALF = 0.26f, FOOT_THICK = 0.07f;

    private static ModelPart head;

    /** The frog's texture: Mojang's, else the shipped copy, else null (nothing drawn rather than a default head). */
    private static Identifier frogTexture() {
        Minecraft mc = Minecraft.getInstance();
        Identifier t = mc.playerSkinRenderCache().getOrDefault(FROG).playerSkin().body().texturePath();
        // Still a default skin: not downloaded (yet, or offline)
        boolean missing = t.equals(DefaultPlayerSkin.getDefaultSkin().body().texturePath()) || t.getPath().contains("entity/player/");
        if (!missing) return t;
        return mc.getResourceManager().getResource(FROG_FALLBACK).isPresent() ? FROG_FALLBACK : null;
    }

    /** Submits the table, and the frog on it turned by {@code frogYaw} degrees. */
    public static void submit(PoseStack pose, SubmitNodeCollector collector, String blockId, float frogYaw) {
        int light = LightCoordsUtil.FULL_BRIGHT;
        float top = submitBlock(pose, collector, blockId, light);
        if (top < 0) {
            // The block is not there (Macaw's Furniture not installed...): the mod's own pedestal table
            top = TOP_Y;
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(PLANKS), (p, c) -> {
                box(c, p, -TOP_HALF, TOP_Y - TOP_THICK, -TOP_HALF, TOP_HALF, TOP_Y, TOP_HALF, light);
                box(c, p, -FOOT_HALF, 0, -FOOT_HALF, FOOT_HALF, FOOT_THICK, FOOT_HALF, light);
            });
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(POST), (p, c) ->
                    box(c, p, -POST_HALF, FOOT_THICK, -POST_HALF, POST_HALF, TOP_Y - TOP_THICK, POST_HALF, light));
        }

        Identifier frog = frogTexture();
        if (frog == null) return;
        if (head == null) head = SkullModel.createHumanoidHeadLayer().bakeRoot();
        pose.pushPose();
        // A placed player head: 8 px cube standing on the top, like the skull renderer (y down model space)
        pose.translate(0, top, 0);
        // Its face toward +z (the player) before its own turn, like the figure on the throne
        pose./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(frogYaw + 180));
        pose.scale(-1, -1, 1);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(frog), (p, c) -> {
            PoseStack ps = new PoseStack();
            ps.last().set(p);
            head.render(ps, c, light, OverlayTexture.NO_OVERLAY);
        });
        pose.popPose();
    }

    /**
     * The block of that id (its default state, its block model, centred on the origin), if the game has it.
     * Returns the height of its top (from its outline), or -1 when there is no such block or model.
     */
    private static float submitBlock(PoseStack pose, SubmitNodeCollector collector, String id, int light) {
        Identifier rid = id == null || id.isEmpty() ? null : Identifier.tryParse(id);
        if (rid == null) return -1;
        Block block = BuiltInRegistries.BLOCK.getOptional(rid).orElse(null);
        if (block == null || block == Blocks.AIR) return -1;
        BlockState state = block.defaultBlockState();
        BlockStateModelSet models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        BlockStateModel model = models.get(state);
        if (model == null || model == models.missingModel()) return -1;
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42), parts);
        if (parts.isEmpty()) return -1;
        VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        float top = shape.isEmpty() ? 1 : (float) shape.max(Direction.Axis.Y);
        pose.pushPose();
        pose.translate(-0.5f, 0, -0.5f);
        collector.submitBlockModel(pose, Sheets.cutoutBlockItemSheet(), parts, BlockModelRenderState.EMPTY_TINTS, light,
                OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
        return top;
    }

    /** An axis-aligned box, each face showing the texture at one texel per pixel (16 per block). */
    private static void box(VertexConsumer c, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        float dx = Math.min(1, x1 - x0), dy = Math.min(1, y1 - y0), dz = Math.min(1, z1 - z0);
        quad(c, p, light, 0, 1, 0, dx, dz, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(c, p, light, 0, -1, 0, dx, dz, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        quad(c, p, light, 0, 0, 1, dx, dy, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1);
        quad(c, p, light, 0, 0, -1, dx, dy, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0);
        quad(c, p, light, 1, 0, 0, dz, dy, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0);
        quad(c, p, light, -1, 0, 0, dz, dy, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1);
    }

    /** Corners a (u0 v0), b (u0 v1), c (u1 v1), d (u1 v0). */
    private static void quad(VertexConsumer c, PoseStack.Pose p, int light, float nx, float ny, float nz, float u, float v,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(c, p, ax, ay, az, 0, 0, light, nx, ny, nz);
        vertex(c, p, bx, by, bz, 0, v, light, nx, ny, nz);
        vertex(c, p, cx, cy, cz, u, v, light, nx, ny, nz);
        vertex(c, p, dx, dy, dz, u, 0, light, nx, ny, nz);
    }

    private static void vertex(VertexConsumer c, PoseStack.Pose p, float x, float y, float z, float u, float v, int light,
                               float nx, float ny, float nz) {
        c.addVertex(p, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }
}
