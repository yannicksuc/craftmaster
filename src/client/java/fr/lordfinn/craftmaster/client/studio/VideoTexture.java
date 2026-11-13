package fr.lordfinn.craftmaster.client.studio;

import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * A video frame of WaterMedia as a Minecraft texture: WaterMedia's own OpenGL texture (it keeps it and frees it),
 * wrapped so that the game draws it like any other texture, without copying a pixel. Minecraft 26.2 only (its
 * OpenGL backend, the version WaterMedia supports); on 26.3 {@link #supported()} is false and the screens show
 * images only. The {@code //?} comments are Stonecutter conditions (the 26.2 build uses the commented-out code).
 */
public final class VideoTexture extends AbstractTexture {

    private final int glId, width, height;

    /** Whether this Minecraft version can show WaterMedia's textures. */
    public static boolean supported() {
        //? if >=26.3 {
        return false;
        //?} else {
        /*return true;
        *///?}
    }

    public VideoTexture(int glId, int width, int height) {
        this.glId = glId;
        this.width = width;
        this.height = height;
        //? if <26.3 {
        /*com.mojang.blaze3d.opengl.GlTexture t = new Borrowed(glId, width, height);
        this.texture = t;
        this.textureView = com.mojang.blaze3d.systems.RenderSystem.getDevice().createTextureView(t);
        this.sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
        *///?}
    }

    /** Still the same picture: WaterMedia reallocates its texture when the video's size changes. */
    public boolean is(int glId, int width, int height) {
        return this.glId == glId && this.width == width && this.height == height;
    }

    //? if <26.3 {
    /*// WaterMedia's texture name, never deleted here: closing only forgets it
    private static final class Borrowed extends com.mojang.blaze3d.opengl.GlTexture {
        Borrowed(int id, int width, int height) {
            super(com.mojang.blaze3d.textures.GpuTexture.USAGE_TEXTURE_BINDING, "craftmaster studio screen video",
                    com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, width, height, 1, 1, id, new com.mojang.blaze3d.opengl.FrameBufferCache());
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public void removeViews() {
            // The views of a borrowed texture never free it
        }
    }
    *///?}
}
