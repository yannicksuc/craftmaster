package fr.lordfinn.craftmaster.client.home;

import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.client.portrait.PortraitRenderer;
import fr.lordfinn.craftmaster.client.portrait.PortraitSkins;
import fr.lordfinn.craftmaster.client.ui.UiButton;
import fr.lordfinn.craftmaster.home.HomeConfig;
import fr.lordfinn.craftmaster.home.HomeLayout;
import fr.lordfinn.craftmaster.portrait.Pose;
import fr.lordfinn.craftmaster.portrait.PoseLibrary;
import fr.lordfinn.craftmaster.portrait.PortraitGeometry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * CraftMaster's title screen (docs/wiki/Accueil.md), built from accueil.json ({@link HomeConfig}):
 * <ul>
 *   <li>the picture of the stage behind everything, cover fit and zoomed so the parallax never shows its edges;</li>
 *   <li>on the left, the master in 3D, sitting on the throne of the picture, with a slow idle motion ({@link HomeCharacter});</li>
 *   <li>at the top, the players' portraits drawn exactly like the hanging ones ({@link PortraitRenderer}), one row
 *   seen in real (weak) perspective, the nearest (player 1) at the top right, the row receding toward the throne,
 *   each frame turned three quarters toward it and swaying gently, the far ones fading into the background;</li>
 *   <li>at the bottom right, the CraftMaster title and the buttons (Jouer connects to the show's server).</li>
 * </ul>
 * The mouse moves three layers at different speeds (background, row, front), eased.
 */
public class HomeScreen extends Screen {

    private static final double FRAME_W = PortraitGeometry.FRAME_W / 16, FRAME_H = PortraitGeometry.FRAME_H / 16;
    private static final int BUTTON_W = 150, BUTTON_H = 20, GAP = 4;
    private static final Component TITLE = Component.literal("CRAFTMASTER").withStyle(ChatFormatting.BOLD);

    private HomeConfig config;
    /** accueil.json's date when it was read, to read it again when coming back from another screen. */
    private long configDate = HomeScreens.configDate();
    private final Screen vanilla;
    private final long start = System.nanoTime();
    private long lastFrame;
    /** Eased mouse, -1..1 from the screen's centre. */
    private double mouseX, mouseY;
    /** The portraits' preset poses ("classique", "salut", "trône"...). */
    private final PoseLibrary poses = PoseLibrary.withPresets();
    private int buttonsTop;

    HomeScreen(HomeConfig config, Screen vanilla) {
        super(Component.translatable("screen.craftmaster.home"));
        this.config = config;
        this.vanilla = vanilla;
        // Skins given as files or links are read again at each opening (an edited file, a link that was down)
        PortraitSkins.forget(HomeScreens.skinSource(config.character.skin()));
        for (HomeConfig.Player p : config.players) PortraitSkins.forget(HomeScreens.skinSource(p.skin()));
    }

    // ---------------------------------------------------------------- buttons

    @Override
    protected void init() {
        // Back from the options, a world list...: accueil.json is read again if it changed (a broken file keeps the last)
        long date = HomeScreens.configDate();
        if (date != configDate) {
            configDate = date;
            try {
                config = HomeScreens.load(HomeScreens.configFile());
            } catch (Exception e) {
                fr.lordfinn.craftmaster.CraftMaster.LOG.warn("[Accueil] accueil.json illisible, réglages précédents gardés : {}", e.toString());
            }
        }
        int right = width - 14, left = right - BUTTON_W, half = (BUTTON_W - GAP) / 2;
        int y = height - 14 - 3 * BUTTON_H - 2 * GAP;
        buttonsTop = y;
        String address = config.server;
        UiButton play = addRenderableWidget(UiButton.of(left, y, BUTTON_W, BUTTON_H,
                Component.translatable("screen.craftmaster.home.play").withStyle(ChatFormatting.BOLD), this::play));
        play.setTooltip(Tooltip.create(Component.translatable("screen.craftmaster.home.play.tip", config.serverName, address)));
        y += BUTTON_H + GAP;
        addRenderableWidget(UiButton.of(left, y, half, BUTTON_H, Component.translatable("screen.craftmaster.home.solo"),
                () -> minecraft.gui.setScreen(new SelectWorldScreen(this))));
        addRenderableWidget(UiButton.of(right - half, y, half, BUTTON_H, Component.translatable("screen.craftmaster.home.multiplayer"),
                () -> minecraft.gui.setScreen(minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this))));
        y += BUTTON_H + GAP;
        addRenderableWidget(UiButton.of(left, y, half, BUTTON_H, Component.translatable("screen.craftmaster.home.options"),
                this::options));
        addRenderableWidget(UiButton.of(right - half, y, half, BUTTON_H, Component.translatable("screen.craftmaster.home.quit"),
                () -> minecraft.stop()));
    }

    private void options() {
        //? if >=26.3 {
        minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options));
        //?} else {
        /*minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options, false));
        *///?}
    }

    /** Straight to the show's server. */
    private void play() {
        String address = config.server;
        if (!ServerAddress.isValidAddress(address)) {
            fr.lordfinn.craftmaster.CraftMaster.LOG.warn("[Accueil] Adresse du serveur invalide dans accueil.json : {}", address);
            return;
        }
        ServerData data = new ServerData(config.serverName, address, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(address), data, false, null);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        // Drawn in extractRenderState, under the 3D layers
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        if (HomeScreens.broken()) {
            minecraft.gui.setScreen(vanilla);
            return;
        }
        try {
            long now = System.nanoTime();
            double dt = lastFrame == 0 ? 0 : (now - lastFrame) / 1e9;
            lastFrame = now;
            double seconds = (now - start) / 1e9;
            double[] hover = fr.lordfinn.craftmaster.client.DevInput.hover();
            double[] target = hover != null ? HomeLayout.mouse(hover[0], hover[1], width, height) : HomeLayout.mouse(mx, my, width, height);
            double rate = config.parallax.smoothing();
            mouseX = HomeLayout.ease(mouseX, target[0], dt, rate);
            mouseY = HomeLayout.ease(mouseY, target[1], dt, rate);

            HomeBackground.Picture picture = background(g);
            g.nextStratum();
            double f = HomeLayout.focal(config.row.fov(), height);
            List<HomeSceneState.Piece> row = row(f, seconds, picture.average());
            if (!row.isEmpty()) {
                addScene(g, row);
                g.nextStratum();
            }
            addScene(g, List.of(character(f, seconds)));
            g.nextStratum();
            logo(g);
        } catch (Throwable t) {
            HomeScreens.failed(t);
            return;
        }
        super.extractRenderState(g, mx, my, partial);
        String version = "CraftMaster " + FabricLoader.getInstance().getModContainer("craftmaster")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?")
                + " — Minecraft " + SharedConstants.getCurrentVersion().name();
        g.text(font, version, 2, height - 10, 0xB0FFFFFF, true);
    }

    private void addScene(GuiGraphicsExtractor g, List<HomeSceneState.Piece> pieces) {
        ((fr.lordfinn.craftmaster.client.mixin.GuiGraphicsExtractorAccessor) g).craftmaster$guiRenderState()
                .addPicturesInPictureState(HomeSceneState.fullScreen((float) config.row.fov(), pieces, width, height));
    }

    /** The stage picture, cover fit, zoomed, moved by the slowest layer. */
    private HomeBackground.Picture background(GuiGraphicsExtractor g) {
        java.nio.file.Path file = config.background.isEmpty() ? null : HomeScreens.resolve(config.background);
        HomeBackground.Picture p = HomeBackground.get(file);
        double[] c = HomeLayout.cover(p.width(), p.height(), width, height, config.zoom);
        double ox = clamp(-mouseX * config.parallax.background(), c[2]), oy = clamp(-mouseY * config.parallax.background(), c[3]);
        g.fill(0, 0, width, height, 0xFF000000);
        g.pose().pushMatrix();
        g.pose().translate((float) ((width - c[0]) / 2 + ox), (float) ((height - c[1]) / 2 + oy));
        g.pose().scale((float) (c[0] / p.width()), (float) (c[1] / p.height()));
        g.blit(p.texture(), 0, 0, p.width(), p.height(), 0, 1, 0, 1);
        g.pose().popMatrix();
        return p;
    }

    private static double clamp(double v, double margin) {
        return Math.max(-margin, Math.min(margin, v));
    }

    /** The players' frames, nearest first (drawn in any order: the depth buffer sorts them). */
    private List<HomeSceneState.Piece> row(double f, double seconds, int haze) {
        List<HomeConfig.Player> players = config.shownPlayers();
        HomeConfig.Row r = config.row;
        List<Vector3d> centres = HomeLayout.row(players.size(), r.first()[0] * width, r.first()[1] * height,
                r.vanish()[0] * width, r.vanish()[1] * height, r.size() * height, FRAME_W, FRAME_H, r.spacing(), r.farthest(),
                width, height, f);
        List<HomeSceneState.Piece> pieces = new ArrayList<>();
        if (centres.isEmpty()) return pieces;
        // Middle layer: the row moves, and pivots a little around its middle so the depth reads
        Vector3d middle = new Vector3d();
        for (Vector3d c : centres) middle.add(c);
        middle.div(centres.size());
        double pivot = config.parallax.pivot();
        Matrix3d turn = new Matrix3d().rotateY(Math.toRadians(mouseX * pivot)).rotateX(Math.toRadians(-mouseY * pivot * 0.5));
        double offX = -mouseX * config.parallax.row(), offY = -mouseY * config.parallax.row();
        for (int i = 0; i < centres.size(); i++) {
            Vector3d p = turn.transform(new Vector3d(centres.get(i)).sub(middle)).add(middle);
            p.add(HomeLayout.screenShift(offX, offY, -p.z, f));
            // Three quarters as seen from the eye: turned from facing the camera (not from facing straight ahead)
            double yaw = HomeLayout.facingYaw(centres.get(i)) - HomeLayout.swayAngle(r.angle(), r.sway(), seconds, i) + mouseX * pivot;
            double fade = centres.size() == 1 ? 0 : r.haze() * i / (centres.size() - 1);
            HomeConfig.Player player = players.get(i);
            Pose pose = poses.get(player.pose().isEmpty() ? config.defaultPose : player.pose());
            PortraitSkins.Skin skin = PortraitSkins.resolve(player.pseudo(), HomeScreens.skinSource(player.skin()));
            PortraitRenderer.State state = PortraitRenderer.menuState(pose, skin, HomeLayout.hazeTint(haze, fade));
            float x = (float) p.x, y = (float) p.y, z = (float) p.z, turnDeg = (float) yaw;
            pieces.add((ps, collector, camera) -> {
                ps.translate(x, y, z);
                ps./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(turnDeg));
                Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(state).submit(state, ps, collector, camera);
            });
        }
        return pieces;
    }

    /**
     * The master, front layer: the middle of the figure on the screen point asked, its visible height a share of
     * the screen's.
     */
    private HomeSceneState.Piece character(double f, double seconds) {
        HomeConfig.Character ch = config.character;
        boolean seated = ch.seated();
        double depth = f * HomeCharacter.height(seated) / (ch.size() * height);
        double cx = ch.x() * width - mouseX * config.parallax.front(), cy = ch.y() * height - mouseY * config.parallax.front();
        Vector3d middle = HomeLayout.unproject(cx, cy, depth, width, height, f);
        PortraitSkins.Skin skin = PortraitSkins.resolve(ch.pseudo(), HomeScreens.skinSource(ch.skin()));
        float x = (float) middle.x, y = (float) middle.y - HomeCharacter.middle(seated), z = (float) middle.z;
        // Turned by the angle asked, from facing the eye
        float yaw = (float) (HomeLayout.facingYaw(middle) + ch.angle());
        double lookX = mouseX, lookY = mouseY;
        return (ps, collector, camera) -> {
            ps.translate(x, y, z);
            ps./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(yaw));
            HomeCharacter.submit(ps, collector, skin, seated, seconds, lookX, lookY);
        };
    }

    /** The title above the buttons, in the show's gold, moved with the front layer. */
    private void logo(GuiGraphicsExtractor g) {
        int textW = font.width(TITLE);
        float scale = Math.max(1.5f, Math.min(4f, (BUTTON_W + 30f) / textW));
        float cx = width - 14 - BUTTON_W / 2f - (float) (mouseX * config.parallax.front() * 0.6);
        float top = buttonsTop - 10 - 9 * scale - (float) (mouseY * config.parallax.front() * 0.6);
        g.pose().pushMatrix();
        g.pose().translate(cx, top);
        g.pose().scale(scale, scale);
        int x = -textW / 2;
        // Dark outline and drop shadow, then the gold in three bands (light at the top, deeper at the bottom)
        g.text(font, TITLE, x, 2, 0xFF14050A, false);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                if (dx != 0 || dy != 0) g.text(font, TITLE, x + dx, dy, 0xFF3E0A12, false);
        int[][] bands = {{-1, 3, 0xFFFFF0B4}, {3, 5, 0xFFFFC83F}, {5, 9, 0xFFE0921E}};
        for (int[] b : bands) {
            g.enableScissor(x - 2, b[0], x + textW + 2, b[1]);
            g.text(font, TITLE, x, 0, b[2], false);
            g.disableScissor();
        }
        g.pose().popMatrix();
        // A thin golden rule under the title
        int ruleY = (int) (top + 9 * scale + 3);
        int half = (int) (textW * scale / 2);
        g.fill((int) cx - half, ruleY, (int) cx + half, ruleY + 1, 0xFFD4A53A);
        g.fill((int) cx - half / 3, ruleY + 2, (int) cx + half / 3, ruleY + 3, 0x90D4A53A);
    }
}
