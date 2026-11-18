package fr.lordfinn.craftmaster.trial.kit;

import fr.lordfinn.craftmaster.CraftMaster;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The walked drawings as pictures: the path seen from above (north up), a thick smooth ink line on paper, one PNG
 * per contestant in {@code config/craftmaster/dessins/<arena>/<player>.png}, kept for the jury's screen
 * ({@link JuryScreen}).
 */
public final class DrawingImages {

    private DrawingImages() {}

    /** Pixels per block of field. */
    static final int PX = 32;
    private static final Color PAPER = new Color(0xF3, 0xEC, 0xDC), INK = new Color(0x1E, 0x18, 0x14), EDGE = new Color(0xC9, 0xBC, 0xA0);

    static Path dir(String arena) {
        return FabricLoader.getInstance().getConfigDir().resolve("craftmaster/dessins").resolve(safe(arena));
    }

    static String safe(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    /** Draws and keeps the picture of a finished drawing. */
    static void save(MinecraftServer server, Arena arena, String player, Drawing.Trace trace) {
        if (arena.field == null) return;
        try {
            byte[] png = render(arena.field.x1 - arena.field.x0 + 1, arena.field.z1 - arena.field.z0 + 1, trace.points);
            Path d = dir(arena.name);
            Files.createDirectories(d);
            Files.write(d.resolve(player.replaceAll("[^A-Za-z0-9_.-]", "_") + ".png"), png);
            JuryScreen.drawingSaved(server, arena, player);
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Dessins] image de {} impossible", player, e);
        }
    }

    /** A field of {@code w × d} blocks and the path (blocks from its corner; NaN = pen lifted) → PNG. */
    static byte[] render(int w, int d, List<float[]> points) throws Exception {
        int m = PX / 2;
        BufferedImage img = new BufferedImage(w * PX + 2 * m, d * PX + 2 * m, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(PAPER);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(EDGE);
        g.setStroke(new BasicStroke(3f));
        g.drawRect(m / 2, m / 2, img.getWidth() - m, img.getHeight() - m);
        g.setColor(INK);
        g.setStroke(new BasicStroke(PX * 0.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (List<float[]> stroke : strokes(points)) {
            if (stroke.size() == 1) {
                float[] p = stroke.get(0);
                float r = PX * 0.35f;
                g.fill(new java.awt.geom.Ellipse2D.Float(m + p[0] * PX - r, m + p[1] * PX - r, 2 * r, 2 * r));
                continue;
            }
            Path2D.Float path = new Path2D.Float();
            // Smoothed: a quadratic curve through the middles of the steps (no zigzag of the ticks)
            float[] a = stroke.get(0);
            path.moveTo(m + a[0] * PX, m + a[1] * PX);
            for (int i = 1; i < stroke.size() - 1; i++) {
                float[] c = stroke.get(i), n = stroke.get(i + 1);
                path.quadTo(m + c[0] * PX, m + c[1] * PX, m + (c[0] + n[0]) / 2 * PX, m + (c[1] + n[1]) / 2 * PX);
            }
            float[] z = stroke.get(stroke.size() - 1);
            path.lineTo(m + z[0] * PX, m + z[1] * PX);
            g.draw(path);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static List<List<float[]>> strokes(List<float[]> points) {
        List<List<float[]>> out = new ArrayList<>();
        List<float[]> cur = new ArrayList<>();
        for (float[] p : points) {
            if (Float.isNaN(p[0])) {
                if (!cur.isEmpty()) out.add(cur);
                cur = new ArrayList<>();
            } else cur.add(p);
        }
        if (!cur.isEmpty()) out.add(cur);
        return out;
    }

    /** The arena is played again: its pictures go away. */
    static void clear(String arena) {
        Path d = dir(arena);
        if (!Files.isDirectory(d)) return;
        try (var files = Files.list(d)) {
            for (Path f : files.toList()) if (f.getFileName().toString().endsWith(".png")) Files.deleteIfExists(f);
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Dessins] {} : effacement impossible", d, e);
        }
    }

    /** The players who have a picture in this arena. */
    public static java.util.List<String> names(String arena) {
        return new java.util.ArrayList<>(all(arena).keySet());
    }

    /** The pictures of an arena, by player (in file order). */
    static Map<String, byte[]> all(String arena) {
        Map<String, byte[]> out = new LinkedHashMap<>();
        Path d = dir(arena);
        if (!Files.isDirectory(d)) return out;
        try (var files = Files.list(d)) {
            for (Path f : files.sorted().toList()) {
                String n = f.getFileName().toString();
                if (n.endsWith(".png")) out.put(n.substring(0, n.length() - 4), Files.readAllBytes(f));
            }
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Dessins] {} illisible", d, e);
        }
        return out;
    }
}
