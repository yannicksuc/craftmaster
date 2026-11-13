package fr.lordfinn.craftmaster.client.media;

import java.nio.ByteBuffer;

/**
 * The OpenGL work of the videos and clips, Minecraft 26.2 only (26.3 draws without OpenGL, and WaterMedia does not
 * load there): copying WaterMedia's picture into a Minecraft texture, and reading the world picture back for a clip
 * without stalling the frame (three buffers in turn, each read once the GPU has filled it).
 */
final class GlBridge {

    private GlBridge() {}

    /** Receives one picture of the world: RGBA, bottom row first (OpenGL order), valid only during the call. */
    interface Sink {
        void accept(ByteBuffer rgbaBottomUp, int width, int height, long ptsUs);
    }

    //? if >=26.3 {
    static boolean supported() { return false; }
    static void copyInto(long src, net.minecraft.client.renderer.texture.AbstractTexture dst, int w, int h) {}
    static boolean grab(long ptsUs, Sink sink) { return false; }
    static int pending() { return 0; }
    static void releaseGrab() {}
    //?} else {
    /*static boolean supported() { return true; }

    private static int readFbo, drawFbo;

    // GPU copy of WaterMedia's texture into ours (same RGBA8 layout, top row first like a NativeImage)
    static void copyInto(long src, net.minecraft.client.renderer.texture.AbstractTexture dstTexture, int w, int h) {
        int dst = ((com.mojang.blaze3d.opengl.GlTexture) dstTexture.getTexture()).glId();
        if (org.lwjgl.opengl.GL.getCapabilities().glCopyImageSubData != 0L) {
            org.lwjgl.opengl.GL43.glCopyImageSubData((int) src, org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                    dst, org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, 0, 0, 0, w, h, 1);
            return;
        }
        // OpenGL 3 fallback: framebuffer blit, bindings put back as they were
        int prevRead = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (readFbo == 0) {
            readFbo = org.lwjgl.opengl.GL30.glGenFramebuffers();
            drawFbo = org.lwjgl.opengl.GL30.glGenFramebuffers();
        }
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, readFbo);
        org.lwjgl.opengl.GL30.glFramebufferTexture2D(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0, org.lwjgl.opengl.GL11.GL_TEXTURE_2D, (int) src, 0);
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
        org.lwjgl.opengl.GL30.glFramebufferTexture2D(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0, org.lwjgl.opengl.GL11.GL_TEXTURE_2D, dst, 0);
        org.lwjgl.opengl.GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, org.lwjgl.opengl.GL11.GL_NEAREST);
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, prevRead);
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
    }

    private static final int RING = 3;
    private static com.mojang.blaze3d.buffers.GpuBuffer[] ring;
    private static final boolean[] busy = new boolean[RING];
    private static int ringW, ringH, pending;

    // Asks the GPU for a copy of the world picture (main target) now; the sink gets it a frame or two later.
    // False when every buffer is still waiting (the frame is skipped).
    static boolean grab(long ptsUs, Sink sink) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        com.mojang.blaze3d.textures.GpuTexture tex = mc.gameRenderer.mainRenderTarget().getColorTexture();
        if (tex == null) return false;
        int w = tex.getWidth(0), h = tex.getHeight(0);
        int bytes = tex.getFormat().blockSize();
        if (ring == null || w != ringW || h != ringH) {
            if (pending > 0) return false;
            releaseGrab();
            ring = new com.mojang.blaze3d.buffers.GpuBuffer[RING];
            for (int i = 0; i < RING; i++)
                ring[i] = com.mojang.blaze3d.systems.RenderSystem.getDevice().createBuffer(() -> "craftmaster clip frame",
                        com.mojang.blaze3d.buffers.GpuBuffer.USAGE_MAP_READ | com.mojang.blaze3d.buffers.GpuBuffer.USAGE_COPY_DST, (long) w * h * bytes);
            ringW = w;
            ringH = h;
        }
        int slot = -1;
        for (int i = 0; i < RING; i++) if (!busy[i]) { slot = i; break; }
        if (slot < 0) return false;
        final int s = slot;
        final com.mojang.blaze3d.buffers.GpuBuffer buf = ring[s];
        busy[s] = true;
        pending++;
        com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(tex, buf, 0, () -> {
            try (com.mojang.blaze3d.buffers.GpuBufferSlice.MappedView view = buf.map(true, false)) {
                sink.accept(view.data(), w, h, ptsUs);
            } catch (RuntimeException e) {
                fr.lordfinn.craftmaster.CraftMaster.LOG.warn("[Clips] Image perdue : {}", e.toString());
            } finally {
                busy[s] = false;
                pending--;
            }
        }, 0);
        return true;
    }

    static int pending() { return pending; }

    static void releaseGrab() {
        if (ring == null || pending > 0) return;
        for (com.mojang.blaze3d.buffers.GpuBuffer b : ring) b.close();
        ring = null;
        ringW = ringH = 0;
    }
    *///?}
}
