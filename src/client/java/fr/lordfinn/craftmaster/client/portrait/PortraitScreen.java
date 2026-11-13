package fr.lordfinn.craftmaster.client.portrait;

import fr.lordfinn.craftmaster.portrait.PoseLibraries;
import fr.lordfinn.craftmaster.portrait.PortraitEntity;
import fr.lordfinn.craftmaster.portrait.PortraitNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Settings of one portrait (organisers, right click): gallery season, followed rank or fixed player, displayed
 * name, skin URL, pose (shown live on the portrait while choosing) and string length. The world stays visible
 * on the right so the portrait can be watched while it changes.
 */
public class PortraitScreen extends Screen {

    private static final int PANEL = 0xD0101018, BORDER = 0xFFFFC23D, LABEL = 0xFFBFBFCF;

    private final PortraitEntity portrait;
    private EditBox season, rankOrPlayer, displayName, skinUrl, hang, turn, windowDepth, windowSize;
    private CycleButton<Boolean> followsRank, window;
    private CycleButton<String> pose;
    private Component error;
    private int left, top;

    // Values kept across re-inits (resize, coming back from the pose editor)
    private String seasonV, rankV, playerV, nameV, urlV, hangV, poseV, depthV, sizeV, turnV;
    private boolean rankMode, windowV;

    public PortraitScreen(PortraitEntity portrait) {
        super(Component.translatable("screen.craftmaster.portrait"));
        this.portrait = portrait;
        seasonV = String.valueOf(portrait.season());
        rankMode = portrait.followsRank();
        rankV = String.valueOf(Math.max(1, portrait.rank()));
        playerV = portrait.fixedPlayer();
        nameV = portrait.displayName();
        urlV = portrait.skinUrl();
        hangV = portrait.hangSetting() < 0 ? "auto" : String.valueOf(portrait.hangSetting());
        turnV = String.valueOf(Math.round(portrait.turn()));
        poseV = portrait.poseName();
        windowV = portrait.window();
        depthV = String.valueOf(portrait.windowDepth());
        sizeV = String.valueOf(portrait.windowSize());
    }

    public PortraitEntity portrait() {
        return portrait;
    }

    /** The pose editor saved or deleted a pose: offer the fresh list. */
    void libraryChanged() {
        rebuildWidgets();
    }

    /** Pose chosen in the editor ("use this pose"). */
    void selectPose(String name) {
        poseV = name;
        poseChosen = true;
    }

    private boolean poseChosen;

    private void keep() {
        if (season == null) return;
        seasonV = season.getValue();
        if (followsRank.getValue()) rankV = rankOrPlayer.getValue();
        else playerV = rankOrPlayer.getValue();
        rankMode = followsRank.getValue();
        nameV = displayName.getValue();
        urlV = skinUrl.getValue();
        hangV = hang.getValue();
        turnV = turn.getValue();
        windowV = window.getValue();
        depthV = windowDepth.getValue();
        sizeV = windowSize.getValue();
        if (!poseChosen) poseV = pose.getValue();
        poseChosen = false;
    }

    @Override
    protected void rebuildWidgets() {
        keep();
        super.rebuildWidgets();
    }

    @Override
    protected void init() {
        left = 10;
        top = Math.max(2, height / 2 - 116);
        int x = left + 92, w = 130, y = top + 22, step = 22;

        season = box(x, y, 40, seasonV, "season");
        tip(season, "season_tip");
        y += step;
        followsRank = addRenderableWidget(CycleButton.booleanBuilder(Component.translatable("screen.craftmaster.portrait.by_rank"),
                Component.translatable("screen.craftmaster.portrait.by_player"), rankMode).displayOnlyValue()
                .create(left + 8, y, 80, 20, Component.empty(), (b, v) -> {
                    if (v) {
                        playerV = rankOrPlayer.getValue();
                        rankOrPlayer.setValue(rankV);
                    } else {
                        rankV = rankOrPlayer.getValue();
                        rankOrPlayer.setValue(playerV);
                    }
                }));
        followsRank.setTooltip(Tooltip.create(Component.translatable("screen.craftmaster.portrait.follow_tip")));
        rankOrPlayer = box(x, y, w, rankMode ? rankV : playerV, "follow");
        tip(rankOrPlayer, "follow_tip");
        y += step;
        displayName = box(x, y, w, nameV, "name");
        tip(displayName, "name_tip");
        y += step;
        skinUrl = box(x, y, w, urlV, "skin", 1000);
        tip(skinUrl, "skin_tip");
        y += step;
        List<String> names = new ArrayList<>(PoseLibraries.client().names());
        if (!names.contains(poseV)) names.add(0, poseV);
        pose = addRenderableWidget(CycleButton.<String>builder(Component::literal, poseV).withValues(names).displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.translatable("screen.craftmaster.portrait.pose_tip")))
                .create(x, y, w - 46, 20, Component.empty(), (b, v) -> PortraitClient.setPreview(portrait.getId(), PoseLibraries.client().get(v))));
        addRenderableWidget(Button.builder(Component.translatable("screen.craftmaster.portrait.edit_pose"), b -> {
            keep();
            minecraft.gui.setScreen(new PoseEditorScreen(portrait, this));
        }).bounds(x + w - 44, y, 44, 20).tooltip(Tooltip.create(Component.translatable("screen.craftmaster.portrait.edit_pose_tip"))).build());
        y += step;
        hang = box(x, y, 50, hangV, "hang");
        tip(hang, "hang_tip");
        // Exact angle from the wall direction (degrees), and "face me" from the look of the player editing
        turn = box(x + 80, y, 30, turnV, "turn");
        tip(turn, "turn_tip");
        addRenderableWidget(Button.builder(Component.translatable("screen.craftmaster.portrait.face_me"), b -> {
            var me = minecraft.player;
            if (me == null) return;
            float t = net.minecraft.util.Mth.wrapDegrees(me.getYRot() + 180 - portrait.getFacing().toYRot());
            // Beyond ±90° the wall direction would be the wrong one: the server picks the nearest facing
            turn.setValue(String.valueOf(Math.round(t)));
        }).bounds(x + 112, y, 18, 20).tooltip(Tooltip.create(Component.translatable("screen.craftmaster.portrait.face_me_tip"))).build());
        y += step;
        // Window look: on / off, depth of the room behind the opening (px), its size (% of the opening)
        window = addRenderableWidget(CycleButton.onOffBuilder(windowV).displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.translatable("screen.craftmaster.portrait.window_tip")))
                .create(x, y, 42, 20, Component.translatable("screen.craftmaster.portrait.window")));
        windowDepth = box(x + 46, y, 40, depthV, "window_depth");
        tip(windowDepth, "window_depth");
        windowSize = box(x + 90, y, 40, sizeV, "window_size");
        tip(windowSize, "window_size");
        y += step + 8;
        addRenderableWidget(Button.builder(Component.translatable("screen.craftmaster.portrait.save"), b -> save())
                .bounds(left + 8, y, 105, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(left + 117, y, 105, 20).build());
        PortraitClient.setPreview(portrait.getId(), PoseLibraries.client().get(poseV));
    }

    private static void tip(EditBox box, String key) {
        box.setTooltip(Tooltip.create(Component.translatable("screen.craftmaster.portrait." + key)));
    }

    /** Enter saves (outside the text boxes' own use of it), like the other forms. */
    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.isConfirmation() && !(getFocused() instanceof CycleButton<?>) && !(getFocused() instanceof Button)) {
            save();
            return true;
        }
        return super.keyPressed(e);
    }

    private EditBox box(int x, int y, int w, String value, String key) {
        return box(x, y, w, value, key, 128);
    }

    /** The max length is set before the value, else a longer value (a skin URL) is cut. */
    private EditBox box(int x, int y, int w, String value, String key, int maxLength) {
        EditBox b = new EditBox(font, x, y, w, 20, Component.translatable("screen.craftmaster.portrait." + key));
        b.setMaxLength(maxLength);
        b.setValue(value == null ? "" : value);
        return addRenderableWidget(b);
    }

    private void save() {
        error = null;
        int s;
        try {
            s = Integer.parseInt(season.getValue().trim());
        } catch (NumberFormatException e) {
            error = Component.translatable("screen.craftmaster.portrait.bad_season");
            return;
        }
        int rank = 0;
        String player = "";
        if (followsRank.getValue()) {
            try {
                rank = Integer.parseInt(rankOrPlayer.getValue().trim());
            } catch (NumberFormatException e) {
                rank = -1;
            }
            if (rank < 1) {
                error = Component.translatable("screen.craftmaster.portrait.bad_rank");
                return;
            }
        } else {
            player = rankOrPlayer.getValue().trim();
            if (player.isEmpty() || player.contains(" ")) {
                error = Component.translatable("screen.craftmaster.portrait.bad_player");
                return;
            }
        }
        float h;
        String hv = hang.getValue().trim().toLowerCase(Locale.ROOT);
        if (hv.isEmpty() || hv.equals("auto")) h = -1;
        else {
            try {
                h = Float.parseFloat(hv.replace(',', '.'));
            } catch (NumberFormatException e) {
                h = Float.NaN;
            }
            if (!(h >= 0 && h <= PortraitEntity.MAX_HANG)) {
                error = Component.translatable("screen.craftmaster.portrait.bad_hang");
                return;
            }
        }
        String url = skinUrl.getValue().trim();
        if (!PortraitNetwork.validSkinUrl(url)) {
            error = Component.translatable("message.craftmaster.portrait_bad_url");
            return;
        }
        int depth, size;
        try {
            depth = Integer.parseInt(windowDepth.getValue().trim());
            size = Integer.parseInt(windowSize.getValue().replace("%", "").trim());
        } catch (NumberFormatException e) {
            depth = size = -1;
        }
        if (depth < PortraitEntity.MIN_WINDOW_DEPTH || depth > PortraitEntity.MAX_WINDOW_DEPTH
                || size < PortraitEntity.MIN_WINDOW_SIZE || size > PortraitEntity.MAX_WINDOW_SIZE) {
            error = Component.translatable("screen.craftmaster.portrait.bad_window");
            return;
        }
        float tr;
        try {
            tr = Float.parseFloat(turn.getValue().replace(',', '.').replace("°", "").trim());
        } catch (NumberFormatException e) {
            error = Component.translatable("screen.craftmaster.portrait.bad_turn");
            return;
        }
        ClientPlayNetworking.send(new PortraitNetwork.Update(portrait.getId(), s, rank, player, displayName.getValue().trim(), url,
                pose.getValue(), h, window.getValue(), depth, size, tr));
        onClose();
    }

    /** The preview ends with the screen (the pose editor and coming back here set it again). */
    @Override
    public void removed() {
        PortraitClient.setPreview(portrait.getId(), null);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** No blur nor darkening: the portrait stays visible behind. */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int w = 232, h = 232;
        g.fill(left, top, left + w, top + h, PANEL);
        g.outline(left, top, w, h, BORDER);
        g.text(font, Component.translatable("screen.craftmaster.portrait").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), left + 8, top + 8, 0xFFFFFFFF);
        int y = top + 28, step = 22;
        g.text(font, Component.translatable("screen.craftmaster.portrait.season"), left + 8, y, LABEL);
        y += step * 2;
        g.text(font, Component.translatable("screen.craftmaster.portrait.name"), left + 8, y, LABEL);
        y += step;
        g.text(font, Component.translatable("screen.craftmaster.portrait.skin"), left + 8, y, LABEL);
        y += step;
        g.text(font, Component.translatable("screen.craftmaster.portrait.pose"), left + 8, y, LABEL);
        y += step;
        g.text(font, Component.translatable("screen.craftmaster.portrait.hang"), left + 8, y, LABEL);
        g.text(font, Component.translatable("screen.craftmaster.portrait.turn").withStyle(ChatFormatting.GRAY), left + 92 + 54, y, LABEL);
        y += step;
        g.text(font, Component.translatable("screen.craftmaster.portrait.window_label"), left + 8, y, LABEL);
        if (error != null) g.text(font, error.copy().withStyle(ChatFormatting.RED), left + 8, top + h - 14, 0xFFFF5555);
        else g.text(font, Component.translatable("screen.craftmaster.portrait.hint").withStyle(ChatFormatting.DARK_GRAY), left + 8, top + h - 14, LABEL);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }
}
