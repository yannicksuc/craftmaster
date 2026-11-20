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
 * The walked drawings as pictures: the path seen from above (north up), framed on the drawing, a fine ink line that
 * keeps its corners, one PNG (and its raw path, .json) per contestant in {@code config/craftmaster/dessins/<arena>/<player>.png}, kept for the jury's screen
 * ({@link JuryScreen}).
 */
public final class DrawingImages {

    private DrawingImages() {}

    private static final Color PAPER = new Color(0xF3, 0xEC, 0xDC), INK = new Color(0x1E, 0x18, 0x14), EDGE = new Color(0xC9, 0xBC, 0xA0);

    static Path dir(String arena) {
        return FabricLoader.getInstance().getConfigDir().resolve("craftmaster/dessins").resolve(safe(arena));
    }

    static String safe(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    /** Draws and keeps the picture of a finished drawing, and its raw path (to draw it again later). */
    static void save(MinecraftServer server, Arena arena, String player, Drawing.Trace trace) {
        if (arena.field == null) return;
        try {
            int w = arena.field.x1 - arena.field.x0 + 1, d = arena.field.z1 - arena.field.z0 + 1;
            Path dir = dir(arena.name);
            Files.createDirectories(dir);
            String file = player.replaceAll("[^A-Za-z0-9_.-]", "_");
            Files.writeString(dir.resolve(file + ".json"), rawJson(w, d, trace.points));
            Files.write(dir.resolve(file + ".png"), render(w, d, trace.points));
            JuryScreen.drawingSaved(server, arena, player);
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Dessins] image de {} impossible", player, e);
        }
    }

    /** The path as walked: {"w", "d", "points": [[x, z], null = pen lifted, ...]} in blocks from the field's corner. */
    static String rawJson(int w, int d, List<float[]> points) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("w", w);
        o.addProperty("d", d);
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        for (float[] p : points) {
            if (Float.isNaN(p[0])) {
                a.add(com.google.gson.JsonNull.INSTANCE);
                continue;
            }
            com.google.gson.JsonArray q = new com.google.gson.JsonArray();
            q.add(Math.round(p[0] * 1000) / 1000f);
            q.add(Math.round(p[1] * 1000) / 1000f);
            a.add(q);
        }
        o.add("points", a);
        return o.toString();
    }

    /**
     * The pictures of an arena drawn again from their raw paths (the drawing algorithm changed). Returns the players
     * redrawn; those without a raw path (drawn before it was kept) are left as they are.
     */
    public static List<String> redraw(String arena) throws Exception {
        List<String> done = new ArrayList<>();
        Path d = dir(arena);
        if (!Files.isDirectory(d)) return done;
        try (var files = Files.list(d)) {
            for (Path f : files.sorted().toList()) {
                String n = f.getFileName().toString();
                if (!n.endsWith(".json")) continue;
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                List<float[]> pts = new ArrayList<>();
                for (com.google.gson.JsonElement e : o.getAsJsonArray("points")) {
                    if (e.isJsonNull()) pts.add(new float[]{Float.NaN, Float.NaN});
                    else pts.add(new float[]{e.getAsJsonArray().get(0).getAsFloat(), e.getAsJsonArray().get(1).getAsFloat()});
                }
                String base = n.substring(0, n.length() - 5);
                Files.write(d.resolve(base + ".png"), render(o.get("w").getAsInt(), o.get("d").getAsInt(), pts));
                done.add(base);
            }
        }
        return done;
    }

    /** Longest side of the picture, in pixels. */
    static final int SIDE = 1024;

    /**
     * A field of {@code w x d} blocks and the path (blocks from its corner; NaN = pen lifted) to PNG: seen from above,
     * north up, framed on the drawing itself (its box, a margin, centred), a fine line that keeps its corners.
     */
    static byte[] render(int w, int d, List<float[]> points) throws Exception {
        List<List<float[]>> strokes = new ArrayList<>();
        for (List<float[]> st : strokes(points)) strokes.add(smooth(simplify(st, 0.2f)));
        // The drawing's box (the whole field when there is nothing)
        float minX = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (List<float[]> st : strokes)
            for (float[] p : st) {
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[1]);
                maxZ = Math.max(maxZ, p[1]);
            }
        if (minX > maxX) {
            minX = 0;
            minZ = 0;
            maxX = w;
            maxZ = d;
        }
        float bw = Math.max(maxX - minX, 2f), bd = Math.max(maxZ - minZ, 2f);
        float margin = Math.max(bw, bd) * 0.12f + 0.5f;
        float spanX = bw + 2 * margin, spanZ = bd + 2 * margin;
        float scale = SIDE / Math.max(spanX, spanZ);
        int iw = Math.round(spanX * scale), ih = Math.round(spanZ * scale);
        float ox = (minX + maxX) / 2 - spanX / 2, oz = (minZ + maxZ) / 2 - spanZ / 2;
        BufferedImage img = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(PAPER);
        g.fillRect(0, 0, iw, ih);
        g.setColor(EDGE);
        g.setStroke(new BasicStroke(3f));
        g.drawRect(8, 8, iw - 17, ih - 17);
        // The line: about 1/90 of the picture, never thinner than 4 px nor wider than 0.45 block
        float line = Math.max(4f, Math.min(SIDE / 90f, scale * 0.45f));
        g.setColor(INK);
        g.setStroke(new BasicStroke(line, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (List<float[]> st : strokes) {
            if (st.size() == 1) {
                float[] p = st.get(0);
                float r = line * 0.6f;
                g.fill(new java.awt.geom.Ellipse2D.Float((p[0] - ox) * scale - r, (p[1] - oz) * scale - r, 2 * r, 2 * r));
                continue;
            }
            Path2D.Float path = new Path2D.Float();
            path.moveTo((st.get(0)[0] - ox) * scale, (st.get(0)[1] - oz) * scale);
            for (int i = 1; i < st.size(); i++) path.lineTo((st.get(i)[0] - ox) * scale, (st.get(i)[1] - oz) * scale);
            g.draw(path);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** Ramer-Douglas-Peucker: the walking jitter (under {@code eps} block) out, the corners kept. */
    static List<float[]> simplify(List<float[]> pts, float eps) {
        if (pts.size() < 3) return pts;
        boolean[] keep = new boolean[pts.size()];
        keep[0] = true;
        keep[pts.size() - 1] = true;
        java.util.ArrayDeque<int[]> todo = new java.util.ArrayDeque<>();
        todo.push(new int[]{0, pts.size() - 1});
        while (!todo.isEmpty()) {
            int[] r = todo.pop();
            float[] a = pts.get(r[0]), b = pts.get(r[1]);
            float dx = b[0] - a[0], dz = b[1] - a[1], len = (float) Math.hypot(dx, dz);
            int best = -1;
            float far = eps;
            for (int i = r[0] + 1; i < r[1]; i++) {
                float[] p = pts.get(i);
                float dist = len < 1e-4f ? (float) Math.hypot(p[0] - a[0], p[1] - a[1])
                        : Math.abs(dx * (a[1] - p[1]) - dz * (a[0] - p[0])) / len;
                if (dist > far) {
                    far = dist;
                    best = i;
                }
            }
            if (best < 0) continue;
            keep[best] = true;
            todo.push(new int[]{r[0], best});
            todo.push(new int[]{best, r[1]});
        }
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) if (keep[i]) out.add(pts.get(i));
        return out;
    }

    /**
     * Two light Chaikin passes, only on the gentle bends: a turn sharper than 50 degrees stays a corner (ears, legs,
     * horns keep their points).
     */
    static List<float[]> smooth(List<float[]> pts) {
        List<float[]> cur = pts;
        for (int pass = 0; pass < 2 && cur.size() >= 3; pass++) {
            List<float[]> out = new ArrayList<>();
            out.add(cur.get(0));
            for (int i = 1; i < cur.size() - 1; i++) {
                float[] a = cur.get(i - 1), b = cur.get(i), c = cur.get(i + 1);
                double t1 = Math.atan2(b[1] - a[1], b[0] - a[0]), t2 = Math.atan2(c[1] - b[1], c[0] - b[0]);
                double turn = Math.abs(Math.atan2(Math.sin(t2 - t1), Math.cos(t2 - t1)));
                if (turn > Math.toRadians(50)) {
                    out.add(b);
                    continue;
                }
                out.add(new float[]{b[0] * 0.75f + a[0] * 0.25f, b[1] * 0.75f + a[1] * 0.25f});
                out.add(new float[]{b[0] * 0.75f + c[0] * 0.25f, b[1] * 0.75f + c[1] * 0.25f});
            }
            out.add(cur.get(cur.size() - 1));
            cur = out;
        }
        return cur;
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

    /**
     * The arena's pictures put aside for a new game: moved to {@code dessins/archives/<arena>/<date_heure>/} (to see
     * them again later), not deleted.
     */
    static void clear(String arena) {
        Path d = dir(arena);
        if (!Files.isDirectory(d)) return;
        try (var files = Files.list(d)) {
            List<Path> pngs = files.filter(f -> f.getFileName().toString().endsWith(".png") || f.getFileName().toString().endsWith(".json")).toList();
            if (pngs.isEmpty()) return;
            String when = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path to = dir(arena).getParent().resolve("archives").resolve(safe(arena)).resolve(when);
            Files.createDirectories(to);
            for (Path f : pngs) Files.move(f, to.resolve(f.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            CraftMaster.LOG.info("[Dessins] {} dessin(s) de « {} » archivé(s) dans {}", pngs.size(), arena, to);
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Dessins] {} : archivage impossible", d, e);
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
