package fr.lordfinn.craftmaster.client.broadcast;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.client.compat.ClientCompat;
import fr.lordfinn.craftmaster.regie.Thumbnails;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Takes the previews the server asks for ({@link Thumbnails.Request}): one camera per frame, every other frame. The
 * frame before, the world picture is kept; on the capture frame the world is drawn from the camera, copied aside,
 * and the kept picture is put back before the interface is drawn — the player (or the broadcast) never sees it. The
 * copy is read back, made small and sent as a PNG.
 */
public final class ThumbCapture {

    private ThumbCapture() {}

    private static final Deque<String> QUEUE = new ArrayDeque<>();
    /** The camera whose view this frame's world render is (null: the normal view). */
    private static String capturing;
    /** The last normal world picture, and whether it is from the frame just before. */
    private static TextureTarget kept, shot;
    private static boolean keptFresh;
    /** The camera holds this frame's capture view until the next frame's view is set (PlayerEars). */
    private static boolean cameraOnThumb;

    /**
     * A picture for the régie agent ({@link fr.lordfinn.craftmaster.client.regie.AgentVision}): drawn from
     * {@code pose} in {@code world} with exactly the effects of {@code fx}, at full size; {@code done} gets it (and
     * closes it), {@code missed} runs when it cannot be taken (another world, chunks not loaded here).
     */
    public record AgentFrame(DirectorCamera.Pose pose, String world, fr.lordfinn.craftmaster.client.fx.ScreenEffects.Override fx,
                             java.util.function.Consumer<NativeImage> done, java.util.function.Consumer<String> missed) {}

    private static final Deque<AgentFrame> AGENT = new ArrayDeque<>();
    private static AgentFrame agentFrame;
    /** Largest width of an agent picture (the read-back shrinks the screen's picture to fit). */
    public static final int AGENT_WIDTH = 960;

    public static void agent(AgentFrame f) {
        AGENT.add(f);
    }

    public static int agentPending() {
        return AGENT.size() + (agentFrame != null ? 1 : 0);
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Thumbnails.Request.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            for (String k : p.keys()) if (!QUEUE.contains(k)) QUEUE.add(k);
        }));
    }

    /** The view to render this frame instead of the normal one, or null. Decided before the world is drawn. */
    public static DirectorCamera.Pose framePose() {
        Minecraft mc = Minecraft.getInstance();
        capturing = null;
        cameraOnThumb = false;
        if (agentFrame != null) {
            // Never drawn (the frame was skipped): put it back first in line
            AGENT.addFirst(agentFrame);
            agentFrame = null;
            fr.lordfinn.craftmaster.client.fx.ScreenEffects.override(null);
        }
        if (!AGENT.isEmpty() && mc.level != null && keptFresh && Mosaic.rendering < 0) {
            AgentFrame f = AGENT.poll();
            String here = mc.level.dimension().identifier().toString();
            DirectorCamera.Pose p = f.pose();
            if (!f.world().isEmpty() && !f.world().equals(here)) {
                f.missed().accept("dans un autre monde (" + f.world() + ")");
                return null;
            }
            if (!mc.level.getChunkSource().hasChunk(((int) Math.floor(p.position().x)) >> 4, ((int) Math.floor(p.position().z)) >> 4)) {
                f.missed().accept("trop loin de ton jeu (monde pas chargé là)");
                return null;
            }
            agentFrame = f;
            cameraOnThumb = true;
            fr.lordfinn.craftmaster.client.fx.ScreenEffects.override(f.fx());
            return p;
        }
        if (QUEUE.isEmpty() || mc.level == null || !keptFresh || Mosaic.rendering >= 0) return null;
        String key = QUEUE.poll();
        CameraSpot s = ShowStateClient.cameras().get(key);
        if (s == null || !s.world().equals(mc.level.dimension().identifier().toString())) return null;
        capturing = key;
        cameraOnThumb = true;
        return DirectorCamera.still(s);
    }

    /** Between a capture frame and the next frame's view: the camera still shows the previewed shot. */
    public static boolean cameraOnThumb() {
        return cameraOnThumb;
    }

    public static boolean capturing() {
        return capturing != null || agentFrame != null;
    }

    /** Right after the world was drawn, before the interface. */
    public static void afterLevel() {
        Minecraft mc = Minecraft.getInstance();
        if (QUEUE.isEmpty() && capturing == null && AGENT.isEmpty() && agentFrame == null) {
            keptFresh = false;
            return;
        }
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        try {
            if (kept == null || kept.width != main.width || kept.height != main.height) {
                if (kept != null) kept.destroyBuffers();
                if (shot != null) shot.destroyBuffers();
                kept = ClientCompat.colorTarget("craftmaster_thumb_kept", main);
                shot = ClientCompat.colorTarget("craftmaster_thumb_shot", main);
                keptFresh = false;
                capturing = null;
                return;
            }
            if (agentFrame != null) {
                // A picture for the régie agent: aside, the normal one back, read back at up to AGENT_WIDTH
                AgentFrame f = agentFrame;
                agentFrame = null;
                keptFresh = false;
                fr.lordfinn.craftmaster.client.fx.ScreenEffects.override(null);
                ClientCompat.copyColor(main, shot);
                ClientCompat.copyColor(kept, main);
                int down = Math.max(1, (main.width + AGENT_WIDTH - 1) / AGENT_WIDTH);
                while (main.width % down != 0 || main.height % down != 0) down++;
                Screenshot.takeScreenshot(shot, down, f.done());
                return;
            }
            if (capturing == null) {
                // A normal frame: keep its picture for the capture frame that follows
                ClientCompat.copyColor(main, kept);
                keptFresh = true;
                return;
            }
            // The capture frame: the camera's picture aside, the normal one back on screen
            String key = capturing;
            capturing = null;
            keptFresh = false;
            ClientCompat.copyColor(main, shot);
            ClientCompat.copyColor(kept, main);
            // The read-back shrinks by a factor that must divide the picture's size: the largest that fits
            int down = Math.max(1, Math.min(main.width / Thumbnails.WIDTH, main.height / Thumbnails.HEIGHT));
            while (down > 1 && (main.width % down != 0 || main.height % down != 0)) down--;
            Screenshot.takeScreenshot(shot, down, image -> send(key, image));
        } catch (RuntimeException e) {
            CraftMaster.LOG.warn("[Réalisation] Vignette impossible", e);
            QUEUE.clear();
            capturing = null;
            if (agentFrame != null) {
                agentFrame.missed().accept("rendu impossible (" + e.getMessage() + ")");
                agentFrame = null;
                fr.lordfinn.craftmaster.client.fx.ScreenEffects.override(null);
            }
        }
    }

    /** Scaled to the preview size (cropped to 16:9 around the centre), PNG, to the server. */
    private static void send(String key, NativeImage image) {
        try (image) {
            int w = image.getWidth(), h = image.getHeight();
            int cw = Math.min(w, h * 16 / 9), ch = Math.min(h, w * 9 / 16);
            try (NativeImage small = new NativeImage(Thumbnails.WIDTH, Thumbnails.HEIGHT, false)) {
                image.resizeSubRectTo((w - cw) / 2, (h - ch) / 2, cw, ch, small);
                Path tmp = Files.createTempFile("craftmaster-thumb", ".png");
                try {
                    small.writeToFile(tmp);
                    byte[] png = Files.readAllBytes(tmp);
                    if (png.length <= Thumbnails.MAX_BYTES && ClientPlayNetworking.canSend(Thumbnails.Image.TYPE))
                        ClientPlayNetworking.send(new Thumbnails.Image(key, png));
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Réalisation] Vignette de {} non envoyée", key, e);
        }
    }

    static List<String> pending() {
        return List.copyOf(QUEUE);
    }
}
