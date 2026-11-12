package fr.lordfinn.craftmaster.client.trial;

import com.mojang.blaze3d.platform.NativeImage;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.trial.BoardEntity;
import fr.lordfinn.craftmaster.trial.CanvasPixels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * One dynamic texture per drawing board: its surface (paper weave, matte slate, white enamel) with the painted
 * pixels on it. Rebuilt only when the synced drawing changes; released when the board is gone.
 */
public final class BoardTextures {

    private BoardTextures() {}

    private static final class Tex {
        final Identifier id;
        final DynamicTexture texture;
        String pixels;
        int width, height, variant;
        long lastUsed;

        Tex(Identifier id, DynamicTexture texture) {
            this.id = id;
            this.texture = texture;
        }
    }

    private static final Map<Integer, Tex> CACHE = new HashMap<>();
    private static long frame;

    /** The texture of a board, up to date. */
    public static Identifier get(BoardEntity board) {
        int w = board.boardWidth() * 16, h = board.boardHeight() * 16;
        String px = board.pixelText();
        int variant = board.variant().ordinal();
        Tex t = CACHE.get(board.getId());
        if (t != null && (t.width != w || t.height != h)) {
            Minecraft.getInstance().getTextureManager().release(t.id);
            CACHE.remove(board.getId());
            t = null;
        }
        if (t == null) {
            Identifier id = CraftMaster.id("board/" + board.getId() + "_" + System.nanoTime() % 100000);
            NativeImage img = new NativeImage(w, h, true);
            DynamicTexture tex = new DynamicTexture(() -> "craftmaster board " + board.getId(), img);
            Minecraft.getInstance().getTextureManager().register(id, tex);
            t = new Tex(id, tex);
            t.width = w;
            t.height = h;
            t.variant = -1;
            CACHE.put(board.getId(), t);
        }
        if (!px.equals(t.pixels) || t.variant != variant) {
            paint(t.texture.getPixels(), board.variant(), CanvasPixels.decode(w, h, px));
            t.texture.upload();
            t.pixels = px;
            t.variant = variant;
        }
        t.lastUsed = frame;
        return t.id;
    }

    /** Surface plus pixels into the image (ARGB). */
    private static void paint(NativeImage img, BoardEntity.Variant v, CanvasPixels p) {
        for (int y = 0; y < p.height(); y++)
            for (int x = 0; x < p.width(); x++) {
                int c = p.get(x, y);
                img.setPixel(x, y, c == 0 ? surface(v, x, y, p.width(), p.height()) : ink(v, c, x, y));
            }
    }

    /** A stable noise in [0, 1) per pixel. */
    private static double noise(int x, int y, int salt) {
        long h = x * 73856093L ^ y * 19349663L ^ salt * 83492791L;
        h ^= h >>> 13;
        h *= 0x5DEECE66DL;
        h ^= h >>> 17;
        return (h & 0xFFFF) / 65536.0;
    }

    private static int shade(int argb, double f) {
        int r = (int) Math.max(0, Math.min(255, ((argb >> 16) & 255) * f));
        int g = (int) Math.max(0, Math.min(255, ((argb >> 8) & 255) * f));
        int b = (int) Math.max(0, Math.min(255, (argb & 255) * f));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Slate tones, light to dark: matte, a little lighter at the top. */
    private static final int[] SLATE_TONES = {0xFF35423B, 0xFF303C35, 0xFF2B3630, 0xFF27312B};
    /** Whiteboard enamel, light to dark, and the sheen near the top-left corner. */
    private static final int[] ENAMEL_TONES = {0xFFFAFBFC, 0xFFF3F5F7, 0xFFECEFF2};

    private static int surface(BoardEntity.Variant v, int x, int y, int w, int h) {
        return switch (v) {
            // Canvas weave: a faint grid and grain
            case CANVAS -> shade(v.surface, 0.95 + 0.05 * noise(x, y, 0) - ((x % 2 == 0) ^ (y % 2 == 0) ? 0.02 : 0));
            // Slate: matte, a few tones from top to bottom, blended by short horizontal strokes (no dust, no dots)
            case SLATE -> SLATE_TONES[tone(x, y, h, SLATE_TONES.length)];
            // Whiteboard: clean enamel, a soft sheen toward the top left
            case WHITEBOARD -> ENAMEL_TONES[tone(x + y, y, h, ENAMEL_TONES.length)];
        };
    }

    /** Index of a top-to-bottom gradient of {@code n} tones, its steps broken by strokes of 2 to 4 pixels. */
    private static int tone(int x, int y, int h, int n) {
        double t = (y + 0.5) / h * n;
        // Each run of 3 pixels on a row moves the step up or down by up to half a tone
        t += (noise(x / 3, y, 11) - 0.5) * 0.9;
        return Math.max(0, Math.min(n - 1, (int) t));
    }

    private static int ink(BoardEntity.Variant v, int c, int x, int y) {
        int rgb = DyeColor.byId(c - 1).getTextureDiffuseColor() | 0xFF000000;
        double n = noise(x, y, 7 + c);
        if (v == BoardEntity.Variant.SLATE) {
            // Chalk: paler and grainy
            int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
            r = (r + 255) / 2; g = (g + 255) / 2; b = (b + 255) / 2;
            return shade(0xFF000000 | r << 16 | g << 8 | b, 0.85 + 0.2 * n);
        }
        // Paint / marker: a little texture so it does not look flat
        return shade(rgb, 0.92 + 0.1 * n);
    }

    /** Called once a frame: boards not drawn for a while (gone, far away) give their texture back. */
    public static void endFrame() {
        frame++;
        if (frame % 200 != 0) return;
        for (Iterator<Tex> it = CACHE.values().iterator(); it.hasNext(); ) {
            Tex t = it.next();
            if (frame - t.lastUsed > 600) {
                Minecraft.getInstance().getTextureManager().release(t.id);
                it.remove();
            }
        }
    }

    public static void clear() {
        for (Tex t : CACHE.values()) Minecraft.getInstance().getTextureManager().release(t.id);
        CACHE.clear();
    }
}
