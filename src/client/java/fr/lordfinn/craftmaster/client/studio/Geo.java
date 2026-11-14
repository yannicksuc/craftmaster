package fr.lordfinn.craftmaster.client.studio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Small geometry helpers of the studio renderers: textured quads and boxes in the current pose, UVs in pixels of
 * a texture of {@code texW × texH}.
 */
final class Geo {

    private Geo() {}

    /** A texture and its size, for pixel UVs. */
    record Tex(float w, float h) {}

    static void vertex(VertexConsumer c, PoseStack.Pose p, float x, float y, float z, float u, float v, Tex t, int color, int light,
                       float nx, float ny, float nz) {
        c.addVertex(p, x, y, z).setColor(color).setUv(u / t.w(), v / t.h()).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(p, nx, ny, nz);
    }

    /** Quad a-b-c-d (counter-clockwise seen from the front), UV rectangle (u0, v0) at a to (u1, v1) at c. */
    static void quad(VertexConsumer c, PoseStack.Pose p, Tex t,
                     float ax, float ay, float az, float bx, float by, float bz,
                     float cx, float cy, float cz, float dx, float dy, float dz,
                     float u0, float v0, float u1, float v1, int color, int light, float nx, float ny, float nz) {
        vertex(c, p, ax, ay, az, u0, v0, t, color, light, nx, ny, nz);
        vertex(c, p, bx, by, bz, u0, v1, t, color, light, nx, ny, nz);
        vertex(c, p, cx, cy, cz, u1, v1, t, color, light, nx, ny, nz);
        vertex(c, p, dx, dy, dz, u1, v0, t, color, light, nx, ny, nz);
    }

    /** Front-facing rectangle in the z = {@code z} plane, facing +z: x0..x1 left to right, y0..y1 bottom to top. */
    static void front(VertexConsumer c, PoseStack.Pose p, Tex t, float x0, float y0, float x1, float y1, float z,
                      float u0, float v0, float u1, float v1, int color, int light) {
        quad(c, p, t, x0, y1, z, x0, y0, z, x1, y0, z, x1, y1, z, u0, v0, u1, v1, color, light, 0, 0, 1);
    }

    /**
     * Axis-aligned box; {@code uv} gives each face's rectangle in pixels {u0, v0, u1, v1}, in the order
     * north (-z), south (+z), west (-x), east (+x), up, down. A null face is skipped.
     */
    static void box(VertexConsumer c, PoseStack.Pose p, Tex t, float x0, float y0, float z0, float x1, float y1, float z1,
                    float[][] uv, int color, int light) {
        float[] f;
        if ((f = uv[0]) != null) // north: seen from -z, x goes right to left
            quad(c, p, t, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, f[0], f[1], f[2], f[3], color, light, 0, 0, -1);
        if ((f = uv[1]) != null)
            quad(c, p, t, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, f[0], f[1], f[2], f[3], color, light, 0, 0, 1);
        if ((f = uv[2]) != null)
            quad(c, p, t, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, f[0], f[1], f[2], f[3], color, light, -1, 0, 0);
        if ((f = uv[3]) != null)
            quad(c, p, t, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, f[0], f[1], f[2], f[3], color, light, 1, 0, 0);
        if ((f = uv[4]) != null)
            quad(c, p, t, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, f[0], f[1], f[2], f[3], color, light, 0, 1, 0);
        if ((f = uv[5]) != null)
            quad(c, p, t, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, f[0], f[1], f[2], f[3], color, light, 0, -1, 0);
    }

    /** The same rectangle on all six faces. */
    static float[][] all(float u0, float v0, float u1, float v1) {
        float[] f = {u0, v0, u1, v1};
        return new float[][]{f, f, f, f, f, f};
    }

    /** Position + colour vertex (lightning-like render types: additive light, no texture). */
    static void glow(VertexConsumer c, PoseStack.Pose p, float x, float y, float z, int argb) {
        c.addVertex(p, x, y, z).setColor(argb);
    }

    /**
     * A vertex of the spotlights' light: position + colour, plus a white texel, full light and a normal when it is drawn
     * with the textured type used under a shader pack ({@link SpotLightRenderType#textured}).
     */
    static void light(VertexConsumer c, PoseStack.Pose p, float x, float y, float z, int argb) {
        if (SpotLightRenderType.textured) {
            c.addVertex(p, x, y, z).setColor(argb).setUv(0.5f, 0.5f).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .setLight(net.minecraft.util.LightCoordsUtil.FULL_BRIGHT).setNormal(p, 0, 1, 0);
        } else {
            c.addVertex(p, x, y, z).setColor(argb);
        }
    }

    static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }
}
