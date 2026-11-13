package fr.lordfinn.craftmaster.client.home;

import com.mojang.blaze3d.platform.NativeImage;
import fr.lordfinn.craftmaster.CraftMaster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The title screen's background picture: a PNG file named in accueil.json, or the mod's picture of the stage.
 * Loaded once into a texture (again when the file changes), with its size (for the cover fit) and its average
 * colour (the haze of the far frames).
 */
final class HomeBackground {

    private HomeBackground() {}

    /** The texture, its size in pixels and its average colour (RGB). */
    record Picture(Identifier texture, int width, int height, int average) {}

    static final Identifier DEFAULT = CraftMaster.id("textures/gui/home/background.png");
    private static final Identifier TEXTURE = CraftMaster.id("home_background");

    private static Picture current;
    private static String loadedKey;

    /**
     * The picture for that file ({@code null}: the mod's own); the mod's own when the file cannot be read.
     * Render thread.
     */
    static Picture get(Path file) {
        String key = "default";
        if (file != null) {
            try {
                key = file.toAbsolutePath() + "@" + Files.getLastModifiedTime(file).toMillis() + "/" + Files.size(file);
            } catch (IOException e) {
                CraftMaster.LOG.warn("[Accueil] Fond introuvable {} : image du mod à la place", file);
                file = null;
            }
        }
        if (current != null && key.equals(loadedKey)) return current;
        NativeImage image = null;
        if (file != null) {
            try (InputStream in = Files.newInputStream(file)) {
                image = NativeImage.read(in);
            } catch (IOException | RuntimeException e) {
                CraftMaster.LOG.warn("[Accueil] Fond illisible {} ({}) : image du mod à la place", file, e.getMessage());
                key = "default";
                if (current != null && key.equals(loadedKey)) return current;
            }
        }
        if (image == null) {
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(DEFAULT)) {
                image = NativeImage.read(in);
            } catch (IOException e) {
                throw new IllegalStateException("background of the title screen missing: " + DEFAULT, e);
            }
        }
        int average = average(image);
        Picture p = new Picture(TEXTURE, image.getWidth(), image.getHeight(), average);
        // Registering again under the same id closes the previous texture
        Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(() -> "craftmaster title screen background", image));
        current = p;
        loadedKey = key;
        return p;
    }

    /** Average colour (RGB) of a grid of samples. */
    private static int average(NativeImage image) {
        long r = 0, g = 0, b = 0, n = 0;
        int w = image.getWidth(), h = image.getHeight();
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                int c = image.getPixel(x * w / 24 + w / 48, y * h / 24 + h / 48);
                r += c >> 16 & 255;
                g += c >> 8 & 255;
                b += c & 255;
                n++;
            }
        }
        return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }
}
