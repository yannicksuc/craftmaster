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
 * drag x y dx dy [button] [mods]
 * release x y [button]
 * scroll x y amount
 * type TEXT                      types TEXT (spaces as _) in the focused field
 * key NAME [mods]                NAME: a letter, a digit, LEFT, RIGHT, UP, DOWN, HOME, END, PAGEUP, PAGEDOWN, ESCAPE, ENTER, SPACE
 * hold NAME seconds              the key reads as held down ({@link #held}) for that long (pressed once too)
 * quit                           closes the game (stops a test client cleanly)
 * screenshot                     saves the picture to screenshots/ like F2 (any screen, no world needed)
 * hover x y                      screens that follow the mouse read this position instead ("hover" alone: the real one)
 * slowmo factor                  animations that ask {@link #timeScale} run this many times slower (1 = normal)
 * use seconds                    holds the "use" key in the world that long, then lets go (charge a throw, a bow…)
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

    public static void init() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
        Path file = FabricLoader.getInstance().getGameDir().resolve("dev-input.txt");
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (useUntil != 0 && System.nanoTime() > useUntil) {
                mc.options.keyUse.setDown(false);
                useUntil = 0;
            }
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
                if (line.strip().toLowerCase(Locale.ROOT).startsWith("screenshot")) {
                    // Like F2, into <run dir>/screenshots (works on every screen, without a world)
                    net.minecraft.client.Screenshot.grab(mc, false);
                    continue;
                }
                if (line.strip().toLowerCase(Locale.ROOT).startsWith("hover")) {
                    String[] a = line.strip().split("\\s+");
                    hover = a.length > 2 ? new double[]{Double.parseDouble(a[1]), Double.parseDouble(a[2])} : null;
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
        if ((m & 1) != 0) out |= 0x0001;
        if ((m & 2) != 0) out |= 0x0040;
        if ((m & 4) != 0) out |= 0x0100;
        return out;
    }

    private static void run(Screen s, String[] a) {
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "click" -> s.mouseClicked(new MouseButtonEvent(Double.parseDouble(a[1]), Double.parseDouble(a[2]),
                    new MouseButtonInfo(button(a, 3), mods(a, 4))), false);
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
