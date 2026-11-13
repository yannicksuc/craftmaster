package fr.lordfinn.craftmaster.client.home;

import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.client.portrait.PortraitRenderer;
import fr.lordfinn.craftmaster.client.portrait.PortraitSkins;
import fr.lordfinn.craftmaster.client.ui.Theme;
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
import net.minecraft.resources.Identifier;
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
 *   <li>at the bottom right, a fixed panel as wide as the row above it, with the CraftMaster logo and the buttons
 *   (Jouer connects to the show's server).</li>
 * </ul>
 * The mouse moves three layers at different speeds (background, row, the master), eased; the panel stays.
 */
public class HomeScreen extends Screen {

    private static final double FRAME_W = PortraitGeometry.FRAME_W / 16, FRAME_H = PortraitGeometry.FRAME_H / 16;
    private static final int BUTTON_H = 20, GAP = 4, MIN_PANEL_W = 150;
    /** The CraftMaster logo of the old server's resource pack (white pixel art with its grey depth), 256 × 30. */
    private static final Identifier LOGO = fr.lordfinn.craftmaster.CraftMaster.id("textures/gui/title_logo.png");
    private static final int LOGO_W = 256, LOGO_H = 30;

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
    /** The panel (GUI pixels), its padding, the logo's scale (GUI pixels per texel) and bottom. */
    private int panelLeft, panelRight, panelTop, panelBottom, logoBottom, PAD = 6;
    private double logoScale = 1;
    /** The background, loaded at each init (opening, coming back, resize): an edited file shows then. */
    private HomeBackground.Picture picture;

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
        picture = null;
        layoutPanel();
        int left = panelLeft + PAD, right = panelRight - PAD, w = right - left, half = (w - GAP) / 2;
        int y = logoBottom + GAP + 2;
        UiButton play = addRenderableWidget(UiButton.of(left, y, w, BUTTON_H,
                Component.translatable("screen.craftmaster.home.play").withStyle(ChatFormatting.BOLD), this::play));
        // The server's name only: its address never shows (the menu is seen on stream)
        play.setTooltip(Tooltip.create(Component.translatable("screen.craftmaster.home.play.tip", config.serverName)));
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

    /**
     * The panel at the bottom right (fixed, no parallax): as wide as the row of frames above it unless accueil.json
     * sets its edges; inside, the logo fills the width (pixel perfect when it can) and the buttons under it.
     */
    private void layoutPanel() {
        HomeConfig.Panel cfg = config.panel;
        PAD = (int) cfg.padding();
        double f = HomeLayout.focal(config.row.fov(), height);
        double[] span = HomeLayout.rowSpan(rowCentres(f), FRAME_W, width, height, f);
        // Automatic left edge: the row's, but never over the master's group (throne, table and frog)
        span[0] = Math.max(span[0], groupRight(f) + 8);
        double[] edges = HomeLayout.panelEdges(span, cfg.left(), cfg.right(), width, 4, MIN_PANEL_W);
        panelLeft = (int) Math.round(edges[0]);
        panelRight = (int) Math.round(edges[1]);
        int inner = panelRight - panelLeft - 2 * PAD;
        logoScale = HomeLayout.fitScale(LOGO_W, inner, minecraft.getWindow().getGuiScale());
        int logoH = (int) Math.ceil(LOGO_H * logoScale);
        int buttons = 3 * BUTTON_H + 2 * GAP;
        panelBottom = height - (int) cfg.bottom();
        panelTop = panelBottom - PAD - buttons - GAP - 2 - logoH - PAD;
        logoBottom = panelTop + PAD + logoH;
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
            fr.lordfinn.craftmaster.CraftMaster.LOG.warn("[Accueil] Adresse du serveur invalide dans accueil.json (champ « serveur »)");
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
            panel(g);
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
        if (picture == null) {
            // accueil.json's file, else config/craftmaster/accueil-fond.png when there is one, else the mod's picture
            java.nio.file.Path file = config.background.isEmpty() ? HomeScreens.resolve(HomeScreens.DEFAULT_BACKGROUND)
                    : HomeScreens.resolve(config.background);
            if (config.background.isEmpty() && !java.nio.file.Files.isRegularFile(file)) file = null;
            picture = HomeBackground.get(file);
        }
        HomeBackground.Picture p = picture;
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
        List<Vector3d> centres = rowCentres(f);
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
            // No pose given: each frame its own, in turn (a varied wall rather than five times the same)
            String poseName = !player.pose().isEmpty() ? player.pose() : !config.defaultPose.isEmpty() ? config.defaultPose
                    : HomeScreens.VARIED_POSES.get(i % HomeScreens.VARIED_POSES.size());
            Pose pose = poses.get(poseName);
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
        boolean throne = ch.throne();
        // The group's size: the throne's height (or the seated figure's) as a share of the screen's
        double blocks = throne ? HomeCharacter.THRONE_HEIGHT : HomeCharacter.FIGURE_HEIGHT * ch.scale();
        double depth = f * blocks / (ch.size() * height);
        double cx = ch.x() * width - mouseX * config.parallax.front(), cy = ch.y() * height - mouseY * config.parallax.front();
        Vector3d seat = HomeLayout.unproject(cx, cy, depth, width, height, f);
        // LordFinn's CraftMaster skin ships with the mod: used when no other skin is given
        PortraitSkins.Skin skin = ch.skin().isBlank() && ch.pseudo().equalsIgnoreCase("LordFinn") ? HomeScreens.LORDFINN_SKIN
                : PortraitSkins.resolve(ch.pseudo(), HomeScreens.skinSource(ch.skin()));
        float x = (float) seat.x, y = (float) seat.y, z = (float) seat.z, scale = (float) ch.scale();
        // Turned by the angle asked, from facing the eye
        float yaw = (float) (HomeLayout.facingYaw(seat) + ch.angle());
        double lookX = mouseX, lookY = mouseY;
        HomeConfig.Table table = config.table;
        return (ps, collector, camera) -> {
            ps.translate(x, y, z);
            ps./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(yaw));
            HomeCharacter.submit(ps, collector, skin, throne, scale, seconds, lookX, lookY);
            if (table.enabled()) {
                // On the throne's floor, true proportions times its own size
                ps.pushPose();
                ps.translate((float) table.x(), -HomeCharacter.SEAT_TOP, (float) table.z());
                ps./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees((float) table.angle()));
                ps.scale((float) table.scale(), (float) table.scale(), (float) table.scale());
                HomeTable.submit(ps, collector, table.block(), (float) table.frogAngle());
                ps.popPose();
            }
        };
    }

    /**
     * The right edge on screen (GUI pixels) of the master's group at rest (no parallax): the throne's right side, and
     * the table's when it is shown.
     */
    private double groupRight(double f) {
        HomeConfig.Character ch = config.character;
        double blocks = ch.throne() ? HomeCharacter.THRONE_HEIGHT : HomeCharacter.FIGURE_HEIGHT * ch.scale();
        double depth = f * blocks / (ch.size() * height);
        Vector3d seat = HomeLayout.unproject(ch.x() * width, ch.y() * height, depth, width, height, f);
        double yaw = Math.toRadians(HomeLayout.facingYaw(seat) + ch.angle());
        List<double[]> points = new ArrayList<>();
        // Throne space corners (x right, z toward the player) at the cushion's height
        double half = ch.throne() ? 0.85 : 0.5 * ch.scale();
        points.add(new double[]{half, half});
        points.add(new double[]{half, -half});
        HomeConfig.Table t = config.table;
        if (t.enabled()) {
            double r = 0.5 * t.scale();
            points.add(new double[]{t.x() + r, t.z() + r});
            points.add(new double[]{t.x() + r, t.z() - r});
        }
        double right = -Double.MAX_VALUE;
        for (double[] p : points) {
            // Turned around the vertical axis like the pose stack (rotateY: x' = x cos + z sin, z' = -x sin + z cos)
            Vector3d q = new Vector3d(seat.x + p[0] * Math.cos(yaw) + p[1] * Math.sin(yaw), seat.y,
                    seat.z - p[0] * Math.sin(yaw) + p[1] * Math.cos(yaw));
            right = Math.max(right, HomeLayout.project(q, width, height, f)[0]);
        }
        return right;
    }

    /** Where the frames hang (camera space), before the parallax. */
    private List<Vector3d> rowCentres(double f) {
        HomeConfig.Row r = config.row;
        return HomeLayout.row(config.shownPlayers().size(), r.first()[0] * width, r.first()[1] * height,
                r.vanish()[0] * width, r.vanish()[1] * height, r.size() * height, FRAME_W, FRAME_H, r.spacing(), r.farthest(),
                width, height, f);
    }

    /** The panel in the show's colours (velvet, golden edge) and the logo in it; the buttons are its widgets. */
    private void panel(GuiGraphicsExtractor g) {
        g.fill(panelLeft + 2, panelTop + 2, panelRight + 2, panelBottom + 2, 0x80000000);
        g.fill(panelLeft, panelTop, panelRight, panelBottom, (Theme.VELVET_DARK & 0xFFFFFF) | 0xE8000000);
        g.outline(panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop, Theme.BORDER);
        g.outline(panelLeft + 1, panelTop + 1, panelRight - panelLeft - 2, panelBottom - panelTop - 2, Theme.BORDER_DARK);
        double w = LOGO_W * logoScale;
        float x = (float) ((panelLeft + panelRight - w) / 2);
        // On whole screen pixels, so the pixel art stays crisp
        int gs = minecraft.getWindow().getGuiScale();
        x = Math.round(x * gs) / (float) gs;
        g.pose().pushMatrix();
        g.pose().translate(x, panelTop + PAD);
        g.pose().scale((float) logoScale, (float) logoScale);
        g.blit(LOGO, 0, 0, LOGO_W, LOGO_H, 0, 1, 0, 1);
        g.pose().popMatrix();
    }
}
