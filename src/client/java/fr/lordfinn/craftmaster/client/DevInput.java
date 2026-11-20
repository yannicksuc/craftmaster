package fr.lordfinn.craftmaster.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.CraftMaster;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Development only (never active outside the dev environment): mouse and keyboard input for the open screen,
 * read from {@code <run dir>/dev-input.txt}, so screens can be tested in game without touching the real mouse
 * (the test tooling has no GUI click). The file is consumed once per tick, one command per line, GUI coordinates:
 * <pre>
 * click x y [button] [mods]     press (button 0 left, 1 right), mods: 1 Shift, 2 Ctrl, 4 Alt (added)
 * dclick x y [button] [mods]    the same, as the second press of a double-click
 * drag x y dx dy [button] [mods]
 * release x y [button]
 * scroll x y amount
 * type TEXT                      types TEXT (spaces as _) in the focused field
 * key NAME [mods]                NAME: a letter, a digit, LEFT, RIGHT, UP, DOWN, HOME, END, PAGEUP, PAGEDOWN, ESCAPE, ENTER, SPACE
 * hold NAME seconds              the key reads as held down ({@link #held}) for that long (pressed once too)
 * quit                           closes the game (stops a test client cleanly)
 * screenshot                     saves the picture to screenshots/ like F2 (any screen, no world needed)
 * rec SECONDS [TICKS|Nms]        the finished frame every TICKS ticks (2) or N ms for SECONDS, in rec/<ms>.png
 * hover x y                      screens that follow the mouse read this position instead ("hover" alone: the real one)
 * slowmo factor                  animations that ask {@link #timeScale} run this many times slower (1 = normal)
 * chat TEXT                     opens the chat with TEXT typed (spaces as _): the command suggestions show
 * open CLASS                    opens that screen (no-argument constructor), e.g. another mod's menu
 * use seconds                    holds the "use" key in the world that long, then lets go (charge a throw, a bow…)
 * camera first|back|front        the point of view, as F5 cycles it (see the player's own model)
 * </pre>
 */
public final class DevInput {

    private DevInput() {}

    private static final java.util.Map<Integer, Long> HELD = new java.util.HashMap<>();
    private static float slow = 1;
    /** A "use" line holds the use key until then (nanoTime), 0 when not holding. */
    private static long useUntil;

    /** Is this key (InputConstants.KEY_*) held by a {@code hold} line? Always false outside development. */
    public static boolean held(int key) {
        Long until = HELD.get(key);
        return until != null && System.nanoTime() < until;
    }

    /** A mouse position (GUI coordinates) set by a {@code hover} line, null when none; always null outside development. */
    private static double[] hover;

    public static double[] hover() {
        return hover;
    }

    /** Speed factor of the animations (1 normal, 0.1 = ten times slower with "slowmo 10"); 1 outside development. */
    public static float timeScale() {
        return 1 / slow;
    }

    private static int recTicks, recEvery = 2;
    private static long lastGrab, recEveryMs = 100;

    /** Writers of the "rec" frames (PNG encoding off the render thread). */
    private static java.util.concurrent.ExecutorService recWriters;

    /**
     * "rec": the finished frame (world, effects and interface: what the window shows) every {@code recEvery} ticks'
     * worth of time (or every N ms with "rec SECONDS Nms"), from the end of {@code GameRenderer.render}, written as
     * {@code <run dir>/rec/<milliseconds>.png} (silently: the time of each frame is in its name, for a video).
     */
    public static void afterFrame() {
        if (recTicks <= 0) return;
        long now = System.currentTimeMillis();
        if (now - lastGrab < recEveryMs) return;
        lastGrab = now;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        java.nio.file.Path dir = FabricLoader.getInstance().getGameDir().resolve("rec");
        if (recWriters == null) recWriters = java.util.concurrent.Executors.newFixedThreadPool(6);
        net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), img -> recWriters.execute(() -> {
            try (img) {
                Files.createDirectories(dir);
                img.writeToFile(dir.resolve(now + ".png"));
            } catch (Exception e) {
                CraftMaster.LOG.warn("[DevInput] rec : {}", e.toString());
            }
        }));
    }

    public static void init() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
        Path file = FabricLoader.getInstance().getGameDir().resolve("dev-input.txt");
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (useUntil != 0 && System.nanoTime() > useUntil) {
                mc.options.keyUse.setDown(false);
                useUntil = 0;
            }
            if (recTicks > 0) recTicks--;
            if (!Files.exists(file)) return;
            List<String> lines;
            try {
                lines = Files.readAllLines(file);
                Files.delete(file);
            } catch (Exception e) {
                return;
            }
            Screen screen = mc.gui.screen();
            for (String line : lines) {
                if (line.strip().equalsIgnoreCase("quit")) {
                    mc.stop();
                    return;
                }
                if (line.strip().toLowerCase(Locale.ROOT).startsWith("slowmo")) {
                    slow = Math.max(0.01f, Float.parseFloat(line.strip().split("\\s+")[1]));
                    continue;
                }
                if (line.strip().toLowerCase(Locale.ROOT).startsWith("rec ")) {
                    String[] r = line.strip().split("\s+");
                    recEvery = r.length > 2 && !r[2].endsWith("ms") ? Math.max(1, Integer.parseInt(r[2])) : 2;
                    recEveryMs = r.length > 2 && r[2].endsWith("ms") ? Math.max(1, Integer.parseInt(r[2].replace("ms", ""))) : recEvery * 50L;
                    recTicks = (int) Math.round(Double.parseDouble(r[1]) * 20);
                    continue;
                }
                if (line.strip().toLowerCase(Locale.ROOT).startsWith("screenshot")) {
                    // Like F2, into <run dir>/screenshots (works on every screen, without a world)
                    net.minecraft.client.Screenshot.grab(mc, false);
                    continue;
                }
                if (line.strip().equalsIgnoreCase("inventory") && mc.player != null) {
                    // Opens the player's inventory, as with E (the show's inventory camera)
                    mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                    screen = mc.gui.screen();
                    continue;
                }
                if (line.strip().toLowerCase(Locale.ROOT).startsWith("hover")) {
                    String[] a = line.strip().split("\\s+");
                    hover = a.length > 2 ? new double[]{Double.parseDouble(a[1]), Double.parseDouble(a[2])} : null;
                    continue;
                }
                // Axiom (travelling gizmos): "axiom LEFT_MOUSE" runs one of its actions, "axiomhold 1/0" holds the
                // left button for its tools; "look <yaw> <pitch>" turns the player
                if (line.strip().startsWith("axiom") && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("axiom")) {
                    String[] a = line.strip().split("\s+");
                    if (a[0].equals("axiomhold")) fr.lordfinn.craftmaster.client.axiom.TravellingGizmos.devLeft = a.length > 1 && a[1].equals("1");
                    else if (a.length > 1) fr.lordfinn.craftmaster.client.axiom.TravellingGizmos.devAction(a[1]);
                    continue;
                }
                // "regie": the moderators' director, as with its key
                if (line.strip().equals("regie")) {
                    fr.lordfinn.craftmaster.client.regie.ModoDeskClient.open(mc);
                    continue;
                }
                // "chat TEXT": the chat opened with TEXT typed (the command suggestions show, Tab completes)
                if (line.strip().startsWith("chat ")) {
                    mc.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen(line.strip().substring(5).replace('_', ' '), false));
                    continue;
                }
                // "open <class>": opens a screen built with its no-argument constructor (another mod's menu, e.g.
                // de.maxhenkel.voicechat.gui.VoiceChatScreen)
                if (line.strip().startsWith("open ")) {
                    try {
                        mc.gui.setScreen((net.minecraft.client.gui.screens.Screen) Class.forName(line.strip().substring(5).strip()).getConstructor().newInstance());
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        CraftMaster.LOG.warn("[DevInput] {} : {}", line, e.toString());
                    }
                    continue;
                }
                // "ears": where the game and Simple Voice Chat listen from right now, and the camera (logged)
                if (line.strip().equals("ears") && mc.player != null) {
                    Object voice = null;
                    try {
                        voice = Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager").getMethod("getCameraState").invoke(null);
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                    }
                    CraftMaster.LOG.info("[DevInput] ears: sound={} voice={} camera={} eyes={} mode={}",
                            mc.getSoundManager().getListenerTransform().position(), voice,
                            mc.gameRenderer.mainCamera().position(), mc.player.getEyePosition(),
                            fr.lordfinn.craftmaster.client.broadcast.PlayerEars.mode());
                    continue;
                }
                // "control <x y z> <yaw> <pitch>": look through a camera standing there (as its menu's button; any screen ends it)
                if (line.strip().startsWith("control ")) {
                    String[] a = line.strip().split("\s+");
                    if (a.length >= 6) fr.lordfinn.craftmaster.client.studio.ControlView.startCamera("dev", "dev", new net.minecraft.world.phys.Vec3(
                            Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3])), Float.parseFloat(a[4]), Float.parseFloat(a[5]), 70);
                    continue;
                }
                // "attack": a hit on what the crosshair aims at (logged)
                if (line.strip().equals("attack") && mc.player != null && mc.gameMode != null) {
                    CraftMaster.LOG.info("[DevInput] aim: {}", mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult e ? e.getEntity() : mc.hitResult == null ? null : mc.hitResult.getType());
                    if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult e) mc.gameMode.attack(mc.player, e.getEntity());
                    continue;
                }
                // "pickdata": Ctrl + middle click on the entity aimed at
                if (line.strip().equals("pickdata") && mc.crosshairPickEntity != null && mc.getConnection() != null) {
                    mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket(mc.crosshairPickEntity.getId(), true));
                    continue;
                }
                // "camturn <dx> <dy>" / "camwheel <notches>": the mouse and the wheel of the broadcast game (follow shot)
                if (line.strip().startsWith("camturn ")) {
                    String[] a = line.strip().split("\s+");
                    fr.lordfinn.craftmaster.client.broadcast.FollowCamera.turn(Double.parseDouble(a[1]), Double.parseDouble(a[2]));
                    continue;
                }
                // "camaction <json>": a direction action sent as the broadcast keys do (e.g. {"type":"camera.split",...})
                if (line.strip().startsWith("camaction ")) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> act = new com.google.gson.Gson().fromJson(line.strip().substring(10), java.util.Map.class);
                    fr.lordfinn.craftmaster.client.broadcast.Broadcast.request(act);
                    continue;
                }
                if (line.strip().startsWith("camwheel ")) {
                    fr.lordfinn.craftmaster.client.broadcast.Broadcast.onScroll(Double.parseDouble(line.strip().split("\s+")[1]));
                    continue;
                }
                // "camera first|back|front": the point of view, as F5 cycles it (the player's own model seen from behind)
                if (line.strip().startsWith("camera ")) {
                    String v = line.strip().substring(7).strip();
                    mc.options.setCameraType(v.equals("back") ? net.minecraft.client.CameraType.THIRD_PERSON_BACK
                            : v.equals("front") ? net.minecraft.client.CameraType.THIRD_PERSON_FRONT : net.minecraft.client.CameraType.FIRST_PERSON);
                    continue;
                }
                if (line.strip().startsWith("look ") && mc.player != null) {
                    String[] a = line.strip().split("\s+");
                    mc.player.setYRot(Float.parseFloat(a[1]));
                    mc.player.setXRot(Float.parseFloat(a[2]));
                    continue;
                }
                if (line.strip().toLowerCase(Locale.ROOT).startsWith("use")) {
                    String[] a = line.strip().split("\\s+");
                    useUntil = System.nanoTime() + (long) (Double.parseDouble(a.length > 1 ? a[1] : "0.2") * 1e9);
                    mc.options.keyUse.setDown(true);
                    continue;
                }
                if (screen == null) continue;
                try {
                    run(screen, line.trim().split("\\s+"));
                } catch (Exception e) {
                    CraftMaster.LOG.warn("[DevInput] {} : {}", line, e.toString());
                }
            }
        });
    }

    /** 0 left, 1 right, 2 middle → the game's (SDL) button numbers. */
    private static int button(String[] a, int i) {
        return switch (arg(a, i, 0)) {
            case 1 -> InputConstants.MOUSE_BUTTON_RIGHT;
            case 2 -> InputConstants.MOUSE_BUTTON_MIDDLE;
            default -> InputConstants.MOUSE_BUTTON_LEFT;
        };
    }

    private static int arg(String[] a, int i, int fallback) {
        return a.length > i ? Integer.parseInt(a[i]) : fallback;
    }

    /** 1 Shift, 2 Ctrl, 4 Alt → the game's (SDL) modifier bits. */
    private static int mods(String[] a, int i) {
        int m = arg(a, i, 0), out = 0;
        //? if >=26.3 {
        if ((m & 1) != 0) out |= 0x0001;
        if ((m & 2) != 0) out |= 0x0040;
        if ((m & 4) != 0) out |= 0x0100;
        //?} else {
        /*out = m & 7; // 26.2: Shift 1, Ctrl 2, Alt 4 (InputWithModifiers), the same bits as the file
        *///?}
        return out;
    }

    private static void run(Screen s, String[] a) {
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "click" -> s.mouseClicked(new MouseButtonEvent(Double.parseDouble(a[1]), Double.parseDouble(a[2]),
                    new MouseButtonInfo(button(a, 3), mods(a, 4))), false);
            case "dclick" -> s.mouseClicked(new MouseButtonEvent(Double.parseDouble(a[1]), Double.parseDouble(a[2]),
                    new MouseButtonInfo(button(a, 3), mods(a, 4))), true);
            case "drag" -> s.mouseDragged(new MouseButtonEvent(Double.parseDouble(a[1]), Double.parseDouble(a[2]),
                    new MouseButtonInfo(button(a, 5), mods(a, 6))), Double.parseDouble(a[3]), Double.parseDouble(a[4]));
            case "release" -> s.mouseReleased(new MouseButtonEvent(Double.parseDouble(a[1]), Double.parseDouble(a[2]),
                    new MouseButtonInfo(button(a, 3), 0)));
            case "scroll" -> s.mouseScrolled(Double.parseDouble(a[1]), Double.parseDouble(a[2]), 0, Double.parseDouble(a[3]));
            case "hold" -> {
                int[] k = keyCode(a[1]);
                HELD.put(k[0], System.nanoTime() + (long) (Double.parseDouble(a.length > 2 ? a[2] : "1") * 1e9));
                s.keyPressed(new KeyEvent(k[0], k[1], 0));
            }
            case "type" -> {
                String text = String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)).replace('_', ' ');
                text.codePoints().forEach(cp -> s.charTyped(new net.minecraft.client.input.CharacterEvent(cp)));
            }
            case "slowmo" -> slow = Math.max(0.01f, Float.parseFloat(a[1]));
            case "key" -> {
                int[] k = keyCode(a[1]);
                s.keyPressed(new KeyEvent(k[0], k[1], mods(a, 2)));
            }
            default -> CraftMaster.LOG.warn("[DevInput] unknown: {}", String.join(" ", a));
        }
    }

    /** {scancode (the key's place, {@link InputConstants}), keycode (what it types: 'a'... for letters)}. */
    private static int[] keyCode(String name) {
        String n = name.toUpperCase(Locale.ROOT);
        char c = n.charAt(0);
        if (n.length() == 1 && c >= 'A' && c <= 'Z') return new int[]{InputConstants.KEY_A + (c - 'A'), 'a' + (c - 'A')};
        if (n.length() == 1 && c >= '1' && c <= '9') return new int[]{InputConstants.KEY_1 + (c - '1'), c};
        if (n.equals("0")) return new int[]{InputConstants.KEY_0, '0'};
        int code = switch (n) {
            case "LEFT" -> InputConstants.KEY_LEFT;
            case "RIGHT" -> InputConstants.KEY_RIGHT;
            case "UP" -> InputConstants.KEY_UP;
            case "DOWN" -> InputConstants.KEY_DOWN;
            case "HOME" -> InputConstants.KEY_HOME;
            case "ESCAPE" -> InputConstants.KEY_ESCAPE;
            case "ENTER" -> InputConstants.KEY_RETURN;
            case "SPACE" -> InputConstants.KEY_SPACE;
            case "PAGEDOWN" -> InputConstants.KEY_PAGEDOWN;
            case "PAGEUP" -> InputConstants.KEY_PAGEUP;
            case "END" -> InputConstants.KEY_END;
            default -> throw new IllegalArgumentException("key " + name);
        };
        return new int[]{code, code};
    }
}
