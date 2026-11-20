package fr.lordfinn.craftmaster.client.regie;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.client.broadcast.DirectorCamera;
import fr.lordfinn.craftmaster.client.broadcast.ThumbCapture;
import fr.lordfinn.craftmaster.client.fx.ScreenEffects;
import fr.lordfinn.craftmaster.fx.FocusPull;
import fr.lordfinn.craftmaster.fx.ScreenFx;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * What the régie agent sees (docs/wiki/RegieIA.md, tool « voir »): the server turns the request into pictures to take
 * (a camera at some seconds of its motion, a travelling at some seconds, each shot of an intro, with their effects
 * and focus pulls); this game draws each one from its viewpoint for a single frame, the way the show's feed draws it
 * ({@link ThumbCapture}: the player's own screen keeps its picture), writes them as PNG files with a contact sheet
 * (and a short video when asked, with ffmpeg if it is installed), shows them to the organiser in his chat
 * ([aperçu] → {@link AgentViewScreen}) and hands the files' paths to the voice control service, which gives the
 * pictures to the agent.
 */
public final class AgentVision {

    private AgentVision() {}

    /** The last views (for /iavue n): folder, title, files. */
    public record View(String title, Path folder, List<Path> images, List<String> captions) {}

    private static final List<View> VIEWS = new CopyOnWriteArrayList<>();
    private static final AtomicInteger COUNTER = new AtomicInteger();

    public static List<View> views() {
        return VIEWS;
    }

    /**
     * Takes the pictures of a plan sent by the server ({@code AgentBridge.renderPlan}), then calls {@code done} with
     * the JSON summary for the agent (client thread).
     */
    public static void take(JsonObject plan, Consumer<String> done) {
        Minecraft mc = Minecraft.getInstance();
        String title = plan.has("titre") ? plan.get("titre").getAsString() : "vue";
        JsonArray frames = plan.has("images") ? plan.getAsJsonArray("images") : new JsonArray();
        boolean video = plan.has("video") && plan.get("video").getAsBoolean();
        Path folder;
        try {
            folder = FabricLoader.getInstance().getGameDir().resolve("craftmaster-regie").resolve("vues")
                    .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + COUNTER.incrementAndGet());
            Files.createDirectories(folder);
        } catch (Exception e) {
            done.accept("Erreur : dossier des vues impossible (" + e.getMessage() + ").");
            return;
        }
        int n = frames.size();
        if (n == 0) {
            done.accept(plan.has("erreur") ? plan.get("erreur").getAsString() : "Rien à regarder.");
            return;
        }
        Path[] files = new Path[n];
        String[] captions = new String[n];
        String[] missed = new String[n];
        AtomicInteger left = new AtomicInteger(n);
        Runnable one = () -> {
            if (left.decrementAndGet() == 0) mc.execute(() -> finish(plan, title, folder, files, captions, missed, video, done));
        };
        for (int i = 0; i < n; i++) {
            JsonObject f = frames.get(i).getAsJsonObject();
            final int k = i;
            captions[i] = f.has("legende") ? f.get("legende").getAsString() : "image " + (i + 1);
            double t = f.has("t") ? f.get("t").getAsDouble() : 0;
            DirectorCamera.Pose pose = null;
            String world = "";
            if (f.has("camera") && !f.get("camera").getAsString().isEmpty()) {
                pose = DirectorCamera.cameraAt(f.get("camera").getAsString(), t);
                world = DirectorCamera.cameraWorld(f.get("camera").getAsString());
            } else if (f.has("trajet") && !f.get("trajet").getAsString().isEmpty()) {
                pose = DirectorCamera.pathAt(f.get("trajet").getAsString(), t);
                world = DirectorCamera.pathWorld(f.get("trajet").getAsString());
            }
            if (pose == null) {
                missed[i] = "caméra ou trajet inconnu de ce jeu";
                one.run();
                continue;
            }
            String spec = f.has("effet") ? f.get("effet").getAsString() : "";
            float since = f.has("depuis") ? f.get("depuis").getAsFloat() : (float) t;
            ScreenEffects.Override fx = new ScreenEffects.Override(ScreenFx.parseLenient(spec), FocusPull.of(spec), since);
            Path file = folder.resolve(String.format("%03d.png", i + 1));
            ThumbCapture.agent(new ThumbCapture.AgentFrame(pose, world, fx, image -> {
                try (image) {
                    image.writeToFile(file);
                    files[k] = file;
                } catch (Exception e) {
                    missed[k] = "écriture impossible (" + e.getMessage() + ")";
                }
                one.run();
            }, why -> {
                missed[k] = why;
                one.run();
            }));
        }
    }

    private static void finish(JsonObject plan, String title, Path folder, Path[] files, String[] captions, String[] missed,
                               boolean video, Consumer<String> done) {
        List<Path> ok = new ArrayList<>();
        List<String> okCaptions = new ArrayList<>();
        JsonArray images = new JsonArray();
        JsonArray failed = new JsonArray();
        for (int i = 0; i < files.length; i++) {
            if (files[i] != null) {
                ok.add(files[i]);
                okCaptions.add(captions[i]);
                JsonObject o = new JsonObject();
                o.addProperty("fichier", files[i].toAbsolutePath().toString());
                o.addProperty("legende", captions[i]);
                images.add(o);
            } else {
                JsonObject o = new JsonObject();
                o.addProperty("legende", captions[i]);
                o.addProperty("pourquoi", missed[i] == null ? "?" : missed[i]);
                failed.add(o);
            }
        }
        JsonObject out = new JsonObject();
        out.addProperty("vision", true);
        out.addProperty("titre", title);
        out.addProperty("dossier", folder.toAbsolutePath().toString());
        out.add("images", images);
        if (!failed.isEmpty()) out.add("ratees", failed);
        if (plan.has("notes")) out.add("notes", plan.get("notes"));
        Path sheet = null;
        if (ok.size() > 1 && !video) {
            sheet = contactSheet(folder.resolve("planche.png"), ok, okCaptions, title);
            if (sheet != null) out.addProperty("planche", sheet.toAbsolutePath().toString());
        }
        if (video && ok.size() > 1) {
            sheet = contactSheet(folder.resolve("planche.png"), pickEvenly(ok, 12), pickEvenly(okCaptions, 12), title);
            if (sheet != null) out.addProperty("planche", sheet.toAbsolutePath().toString());
            double fps = plan.has("ips") ? plan.get("ips").getAsDouble() : 8;
            Path mp4 = encode(folder, fps);
            if (mp4 != null) out.addProperty("video", mp4.toAbsolutePath().toString());
        }
        View v = new View(title, folder, sheet != null ? withFirst(sheet, ok) : ok, sheet != null ? withFirst("planche", okCaptions) : okCaptions);
        if (!ok.isEmpty()) {
            VIEWS.add(v);
            while (VIEWS.size() > 20) VIEWS.removeFirst();
            showInChat(VIEWS.size(), v, out.has("video") ? Path.of(out.get("video").getAsString()) : null, failed.size());
        }
        CraftMaster.LOG.info("[Régie IA] Vue « {} » : {} image(s), {} ratée(s), dans {}", title, ok.size(), failed.size(), folder);
        done.accept(out.toString());
    }

    private static <T> List<T> pickEvenly(List<T> all, int max) {
        if (all.size() <= max) return all;
        List<T> out = new ArrayList<>();
        for (int i = 0; i < max; i++) out.add(all.get((int) Math.round(i * (all.size() - 1) / (double) (max - 1))));
        return out;
    }

    private static <T> List<T> withFirst(T first, List<T> rest) {
        List<T> out = new ArrayList<>();
        out.add(first);
        out.addAll(rest);
        return out;
    }

    /** One picture per cell (4 per row, 480 px wide), its caption on a dark band under it, the title on top. */
    static Path contactSheet(Path out, List<Path> files, List<String> captions, String title) {
        try {
            int cols = Math.min(4, files.size()), rows = (files.size() + cols - 1) / cols;
            int cw = 480, ch = 270, band = 24, head = 30;
            BufferedImage sheet = new BufferedImage(cols * cw, head + rows * (ch + band), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(20, 14, 16));
            g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            g.setColor(new Color(255, 233, 168));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g.drawString(title, 8, 21);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            for (int i = 0; i < files.size(); i++) {
                BufferedImage img = ImageIO.read(files.get(i).toFile());
                int x = (i % cols) * cw, y = head + (i / cols) * (ch + band);
                if (img != null) g.drawImage(img, x, y, cw, ch, null);
                g.setColor(new Color(40, 28, 30));
                g.fillRect(x, y + ch, cw, band);
                g.setColor(Color.WHITE);
                String c = (i + 1) + ". " + captions.get(i);
                while (g.getFontMetrics().stringWidth(c) > cw - 10 && c.length() > 4) c = c.substring(0, c.length() - 2);
                g.drawString(c, x + 5, y + ch + 17);
            }
            g.dispose();
            ImageIO.write(sheet, "png", out.toFile());
            return out;
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Régie IA] Planche contact impossible", e);
            return null;
        }
    }

    /** The pictures as a short MP4 (ffmpeg on the PATH), or null. */
    private static Path encode(Path folder, double fps) {
        Path mp4 = folder.resolve("video.mp4");
        try {
            Process p = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-framerate", String.valueOf(fps), "-i", "%03d.png",
                    "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2", "-c:v", "libx264", "-pix_fmt", "yuv420p", "video.mp4")
                    .directory(folder.toFile()).redirectErrorStream(true).redirectOutput(folder.resolve("ffmpeg.log").toFile()).start();
            if (!p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            return p.exitValue() == 0 && Files.exists(mp4) ? mp4 : null;
        } catch (Exception e) {
            CraftMaster.LOG.info("[Régie IA] Pas de vidéo (ffmpeg absent ?) : {}", e.toString());
            return null;
        }
    }

    // ---------------------------------------------------------------- the organiser's chat

    private static void showInChat(int index, View v, Path video, int failed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || fr.lordfinn.craftmaster.client.broadcast.Broadcast.active()) return;
        MutableComponent line = Component.literal("[Régie IA] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("👁 " + v.title() + " (" + (v.images().size() - (v.captions().getFirst().equals("planche") ? 1 : 0))
                        + " image" + (v.images().size() > 2 ? "s" : "") + (failed > 0 ? ", " + failed + " hors de portée" : "") + ") ").withStyle(ChatFormatting.GRAY))
                .append(link("[aperçu]", new ClickEvent.RunCommand("/iavue " + index), "Voir les images ici, en jeu (◀ ▶ pour passer de l'une à l'autre)", ChatFormatting.GREEN))
                .append(Component.literal(" "))
                .append(link("[dossier]", new ClickEvent.OpenFile(v.folder().toAbsolutePath().toString()), "Ouvrir le dossier des images", ChatFormatting.YELLOW));
        if (video != null)
            line.append(Component.literal(" ")).append(link("[▶ vidéo]", new ClickEvent.OpenFile(video.toAbsolutePath().toString()), "La vidéo (MP4)", ChatFormatting.LIGHT_PURPLE));
        mc.player.sendSystemMessage(line);
    }

    private static Component link(String text, ClickEvent click, String hover, ChatFormatting color) {
        return Component.literal(text).withStyle(st -> st.withColor(color).withClickEvent(click)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    /** {@code /iavue n}: the n-th view (1 = oldest kept), on screen. */
    static void open(int index) {
        Minecraft mc = Minecraft.getInstance();
        if (index < 1 || index > VIEWS.size()) {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("Cette vue n'est plus là.").withStyle(ChatFormatting.GRAY));
            return;
        }
        View v = VIEWS.get(index - 1);
        mc.execute(() -> mc.gui.setScreen(new AgentViewScreen(v)));
    }

    static File file(Path p) {
        return p.toFile();
    }

    static JsonElement json(String s) {
        return com.google.gson.JsonParser.parseString(s);
    }
}
