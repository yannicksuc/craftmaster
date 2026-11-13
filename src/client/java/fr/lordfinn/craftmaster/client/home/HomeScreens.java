package fr.lordfinn.craftmaster.client.home;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.home.HomeConfig;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Replaces Minecraft's title screen with CraftMaster's ({@link HomeScreen}) each time the game opens it
 * (client.mixin.GuiSetScreenMixin), reading {@code config/craftmaster/accueil.json} again every time (created with
 * the defaults on first launch). The vanilla title screen stays when the file says so ({@code "actif": false}),
 * when it is not valid JSON, or when the home screen failed to draw (until the file changes).
 */
public final class HomeScreens {

    private HomeScreens() {}

    /** The file's date when the home screen failed (0 = it did not): no new try before the file changes. */
    private static long failedAt;

    public static void init() {
        PictureInPictureRendererRegistry.register(ctx -> new HomeSceneRenderer());
    }

    /** Background picked up when accueil.json names none (in config/craftmaster/). */
    static final String DEFAULT_BACKGROUND = "accueil-fond.png";

    public static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("craftmaster/accueil.json");
    }

    /** Called with every screen the game opens: the vanilla title screen becomes CraftMaster's. */
    public static Screen replace(Screen screen) {
        if (screen == null || screen.getClass() != TitleScreen.class) return screen;
        try {
            Path file = configFile();
            if (failedAt != 0 && failedAt == modified(file)) return screen;
            failedAt = 0;
            HomeConfig config = load(file);
            return config.enabled ? new HomeScreen(config, screen) : screen;
        } catch (Throwable t) {
            CraftMaster.LOG.warn("[Accueil] Écran titre de Minecraft à la place de celui de CraftMaster : {}", t.toString());
            return screen;
        }
    }

    /** The home screen could not be drawn: the vanilla one until accueil.json changes. */
    static void failed(Throwable t) {
        if (failedAt != 0) return;
        CraftMaster.LOG.error("[Accueil] L'écran titre de CraftMaster n'a pas pu s'afficher, retour à celui de Minecraft", t);
        failedAt = Math.max(1, modified(configFile()));
    }

    /** True once the home screen failed (until accueil.json changes): the open one gives way to the vanilla one. */
    static boolean broken() {
        return failedAt != 0;
    }

    /** accueil.json's last change (0 when missing). */
    static long configDate() {
        try {
            return Files.getLastModifiedTime(configFile()).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 1;
        }
    }

    /** Reads the file, writing the defaults first if there is none. Throws if it is not valid JSON. */
    static HomeConfig load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            HomeConfig d = HomeConfig.defaults();
            Files.createDirectories(file.getParent());
            Files.writeString(file, d.toJson(), StandardCharsets.UTF_8);
            CraftMaster.LOG.info("[Accueil] {} créé avec les réglages par défaut", file);
            return d;
        }
        return HomeConfig.parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** A file named in accueil.json: absolute, or relative to config/craftmaster/. */
    static Path resolve(String name) {
        Path p = Path.of(name);
        return p.isAbsolute() ? p : configFile().getParent().resolve(p);
    }

    /** A skin of accueil.json as PortraitSkins takes it: a link stays, a file becomes its absolute path. */
    /** Poses given in turn to the frames whose player has none. */
    public static final java.util.List<String> VARIED_POSES = fr.lordfinn.craftmaster.portrait.PoseLibrary.VARIED;

    /** LordFinn's CraftMaster skin, shipped with the mod. */
    public static final fr.lordfinn.craftmaster.client.portrait.PortraitSkins.Skin LORDFINN_SKIN = new fr.lordfinn.craftmaster.client.portrait.PortraitSkins.Skin(
            fr.lordfinn.craftmaster.CraftMaster.id("textures/entity/lordfinn_craftmaster.png"), false, null);

    static String skinSource(String skin) {
        if (skin == null || skin.isBlank()) return "";
        String s = skin.trim();
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file:")) return s;
        return resolve(s).toAbsolutePath().toString();
    }
}
