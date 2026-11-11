package fr.lordfinn.craftmaster.client.regie;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.client.CraftMasterClient;
import fr.lordfinn.craftmaster.regie.RegieJson;
import fr.lordfinn.craftmaster.regie.RegiePayloads;
import fr.lordfinn.craftmaster.regie.RegieService;
import fr.lordfinn.craftmaster.regie.Wav;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * The voice control on the admin's side ("régie vocale", docs/wiki/RegieVocale.md): hold the key, speak, release.
 * <ol>
 *   <li>key down: asks the server for the show context and starts recording;</li>
 *   <li>key up: sends WAV + context to the local service (worker thread), which answers transcript + actions;</li>
 *   <li>sends the actions to the server, shows what it did (HUD, {@link RegieHud}).</li>
 * </ol>
 * Dev-only system properties: {@code craftmaster.regie.url} (service URL), {@code craftmaster.regie.wav} (a WAV
 * file used instead of the microphone), {@code craftmaster.regie.triggerDir} (a folder watched every second: a
 * {@code .wav} dropped there plays the whole cycle as if the key were held for its duration — lets a test drive
 * the feature without a keyboard; the file is renamed {@code .done}).
 */
public final class VoiceRegie {

    private VoiceRegie() {}

    /** Hold to speak to the voice control (default N). */
    public static final KeyMapping KEY = new KeyMapping("key.craftmaster.regie", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_N, CraftMasterClient.CATEGORY);

    enum Phase { IDLE, LISTENING, THINKING, EXECUTING, SHOWING }

    /** What the HUD draws; replaced as a whole, read on the render thread. */
    record View(Phase phase, String transcript, String reply, List<String> lines, boolean ok, boolean undoHint, long until) {
        static final View IDLE = new View(Phase.IDLE, "", "", List.of(), true, false, 0);
    }

    static volatile View view = View.IDLE;
    static volatile boolean admin;

    /** Is this player an organiser (as the server last said)? */
    public static boolean isAdmin() {
        return admin;
    }
    private static Recorder recorder;
    private static int requestId;
    private static CompletableFuture<RegiePayloads.Context> pendingContext;
    private static long executingSince;
    private static boolean wasDown;
    private static Path simulated;      // trigger-dir file being "spoken"
    private static long simulatedUntil;
    private static int triggerTicks;

    private static final String URL = System.getProperty("craftmaster.regie.url", RegieService.DEFAULT_URL);
    private static final String DEV_WAV = System.getProperty("craftmaster.regie.wav");
    private static final String TRIGGER_DIR = System.getProperty("craftmaster.regie.triggerDir");
    private static final long SHOW_MS = 9000;

    public static void init() {
        KeyMappingHelper.registerKeyMapping(KEY);
        RegieHud hud = new RegieHud();
        // Not on the broadcast picture (chantier E): the voice control's feedback is for the organiser only
        HudElementRegistry.addLast(CraftMaster.id("regie"), (g, delta) -> {
            if (!fr.lordfinn.craftmaster.client.broadcast.Broadcast.active()) hud.extractRenderState(g, delta);
        });
        ClientTickEvents.END_CLIENT_TICK.register(VoiceRegie::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> reset());

        ClientPlayNetworking.registerGlobalReceiver(RegiePayloads.Status.TYPE, (p, ctx) -> admin = p.admin());
        ClientPlayNetworking.registerGlobalReceiver(RegiePayloads.Context.TYPE, (p, ctx) -> {
            if (pendingContext != null && p.id() == requestId) pendingContext.complete(p);
        });
        ClientPlayNetworking.registerGlobalReceiver(RegiePayloads.Result.TYPE, (p, ctx) -> onResult(p));
    }

    private static void reset() {
        if (recorder != null) recorder.stop();
        recorder = null;
        admin = false;
        view = View.IDLE;
    }

    // ------------------------------------------------------------------ key / trigger

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        boolean down = KEY.isDown();
        if (simulated != null) down = System.currentTimeMillis() < simulatedUntil;
        if (down && !wasDown) press(mc);
        if (!down && wasDown) release(mc);
        wasDown = down;

        View v = view;
        if (v.phase == Phase.SHOWING && System.currentTimeMillis() > v.until) view = View.IDLE;
        if (v.phase == Phase.EXECUTING && System.currentTimeMillis() - executingSince > 6000)
            show(List.of(), "", false, Component.translatable("message.craftmaster.regie.no_result").getString(), false);
        if (TRIGGER_DIR != null && simulated == null && v.phase == Phase.IDLE && ++triggerTicks % 20 == 0) pollTrigger();
    }

    private static void pollTrigger() {
        try (Stream<Path> files = Files.list(Path.of(TRIGGER_DIR))) {
            Path wav = files.filter(f -> f.toString().endsWith(".wav")).sorted().findFirst().orElse(null);
            if (wav == null) return;
            Path playing = wav.resolveSibling(wav.getFileName() + ".playing");
            Files.move(wav, playing);
            simulated = playing;
            simulatedUntil = System.currentTimeMillis() + (long) (Recorder.durationOf(playing) * 1000) + 300;
        } catch (Exception e) {
            CraftMaster.LOG.warn("Voice control trigger dir: {}", e.toString());
        }
    }

    private static void press(Minecraft mc) {
        if (!ClientPlayNetworking.canSend(RegiePayloads.ContextRequest.TYPE)) {
            mc.player.sendOverlayMessage(Component.translatable("message.craftmaster.no_server_mod"));
            return;
        }
        if (!admin) {
            mc.player.sendOverlayMessage(Component.translatable("message.craftmaster.regie.not_admin"));
            return;
        }
        Phase phase = view.phase;
        if (phase == Phase.THINKING || phase == Phase.EXECUTING) {
            mc.player.sendOverlayMessage(Component.translatable("message.craftmaster.regie.busy"));
            return;
        }
        try {
            Path wav = simulated != null ? simulated : DEV_WAV != null ? Path.of(DEV_WAV) : null;
            recorder = wav != null ? Recorder.fromWav(wav) : Recorder.microphone();
        } catch (Exception e) {
            CraftMaster.LOG.warn("Voice control: no microphone", e);
            show(List.of(), "", false, Component.translatable("message.craftmaster.regie.mic_unavailable", e.getMessage()).getString(), false);
            return;
        }
        requestId++;
        pendingContext = new CompletableFuture<>();
        ClientPlayNetworking.send(new RegiePayloads.ContextRequest(requestId));
        view = new View(Phase.LISTENING, "", "", List.of(), true, false, 0);
    }

    private static void release(Minecraft mc) {
        if (simulated != null) {
            try { Files.move(simulated, simulated.resolveSibling(simulated.getFileName().toString().replace(".playing", ".done"))); }
            catch (IOException ignored) { }
            simulated = null;
        }
        if (recorder == null) return;
        byte[] pcm = recorder.stop();
        recorder = null;
        if (Wav.seconds(pcm.length) < 0.3) {
            show(List.of(), "", false, Component.translatable("message.craftmaster.regie.too_short").getString(), false);
            return;
        }
        view = new View(Phase.THINKING, "", "", List.of(), true, false, 0);
        int id = requestId;
        CompletableFuture<RegiePayloads.Context> context = pendingContext;
        Thread.ofPlatform().daemon().name("CraftMaster-regie").start(() -> understand(mc, id, context, pcm));
    }

    // ------------------------------------------------------------------ worker thread

    private static void understand(Minecraft mc, int id, CompletableFuture<RegiePayloads.Context> context, byte[] pcm) {
        RegiePayloads.Context ctx;
        try {
            ctx = context.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            mc.execute(() -> fail(id, "message.craftmaster.regie.no_context"));
            return;
        }
        if (!ctx.admin()) {
            mc.execute(() -> { admin = false; fail(id, "message.craftmaster.regie.not_admin"); });
            return;
        }
        long t = System.nanoTime();
        RegieService.Reply reply = new RegieService(URL).command(Wav.encode(pcm), ctx.json());
        CraftMaster.LOG.info("Voice control: \"{}\" -> {} {} ({} ms)", reply.transcript(), reply.actionsJson(),
                reply.error() == null ? "" : reply.error(), (System.nanoTime() - t) / 1_000_000);
        mc.execute(() -> onReply(id, reply));
    }

    // ------------------------------------------------------------------ results (client thread)

    private static void onReply(int id, RegieService.Reply r) {
        if (id != requestId) return;
        if (r.error() != null && r.actionCount() == 0) {
            String key = switch (r.error()) {
                case "unreachable", "timeout" -> "message.craftmaster.regie.unreachable";
                case "no_api_key" -> "message.craftmaster.regie.no_api_key";
                case "empty_transcript" -> "message.craftmaster.regie.empty";
                default -> null;
            };
            String msg = key != null ? Component.translatable(key).getString()
                    : Component.translatable("message.craftmaster.regie.service_error", r.reply().isBlank() ? r.error() : r.reply()).getString();
            show(List.of(), r.transcript(), false, msg, false);
            return;
        }
        // "say" only talks: it is shown from the reply, the server has nothing to run for it
        String actions = RegieJson.withoutSay(r.actionsJson());
        if (actions.equals("[]")) {
            show(r.warnings(), r.transcript(), true, r.reply().isBlank()
                    ? Component.translatable("hud.craftmaster.regie.nothing_done").getString() : r.reply(), false);
            return;
        }
        executingSince = System.currentTimeMillis();
        view = new View(Phase.EXECUTING, r.transcript(), r.reply(), r.warnings(), true, false, 0);
        ClientPlayNetworking.send(new RegiePayloads.Actions(id, r.transcript(), actions));
    }

    private static void onResult(RegiePayloads.Result p) {
        if (p.id() != requestId || view.phase != Phase.EXECUTING) return;
        if (p.code().equals("not_admin")) {
            admin = false;
            fail(p.id(), "message.craftmaster.regie.not_admin");
            return;
        }
        List<String> lines = new ArrayList<>(view.lines);   // service warnings first
        lines.addAll(p.lines());
        show(lines, view.transcript, p.ok(), view.reply, true);
    }

    private static void fail(int id, String key) {
        if (id == requestId) show(List.of(), "", false, Component.translatable(key).getString(), false);
    }

    private static void show(List<String> lines, String transcript, boolean ok, String reply, boolean undoHint) {
        view = new View(Phase.SHOWING, transcript, reply, List.copyOf(lines), ok, undoHint, System.currentTimeMillis() + SHOW_MS);
    }

    static Recorder recorder() { return recorder; }
}
