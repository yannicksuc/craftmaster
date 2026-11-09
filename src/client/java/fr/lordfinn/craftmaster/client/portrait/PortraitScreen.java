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
    private EditBox season, rankOrPlayer, displayName, skinUrl, hang;
    private CycleButton<Boolean> followsRank, window;
    private CycleButton<String> pose;
    private Component error;
    private int left, top;

    // Values kept across re-inits (resize, coming back from the pose editor)
    private String seasonV, rankV, playerV, nameV, urlV, hangV, poseV;
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
        poseV = portrait.poseName();
        windowV = portrait.window();
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
        windowV = window.getValue();
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
        rankOrPlayer = box(x, y, w, rankMode ? rankV : playerV, "follow");
        y += step;
        displayName = box(x, y, w, nameV, "name");
        y += step;
        skinUrl = box(x, y, w, urlV, "skin");
        skinUrl.setMaxLength(1000);
        y += step;
        List<String> names = new ArrayList<>(PoseLibraries.client().names());
        if (!names.contains(poseV)) names.add(0, poseV);
        pose = addRenderableWidget(CycleButton.<String>builder(Component::literal, poseV).withValues(names).displayOnlyValue()
                .create(x, y, w - 46, 20, Component.empty(), (b, v) -> PortraitClient.setPreview(portrait.getId(), PoseLibraries.client().get(v))));
        addRenderableWidget(Button.builder(Component.translatable("screen.craftmaster.portrait.edit_pose"), b -> {
            keep();
            minecraft.gui.setScreen(new PoseEditorScreen(portrait, this));
        }).bounds(x + w - 44, y, 44, 20).build());
        y += step;
        hang = box(x, y, 50, hangV, "hang");
        y += step;
        window = addRenderableWidget(CycleButton.onOffBuilder(windowV)
                .create(x, y, w, 20, Component.translatable("screen.craftmaster.portrait.window")));
        y += step + 8;
        addRenderableWidget(Button.builder(Component.translatable("screen.craftmaster.portrait.save"), b -> save())
                .bounds(left + 8, y, 105, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(left + 117, y, 105, 20).build());
        PortraitClient.setPreview(portrait.getId(), PoseLibraries.client().get(poseV));
    }

    private EditBox box(int x, int y, int w, String value, String key) {
        EditBox b = new EditBox(font, x, y, w, 20, Component.translatable("screen.craftmaster.portrait." + key));
        b.setMaxLength(128);
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
        ClientPlayNetworking.send(new PortraitNetwork.Update(portrait.getId(), s, rank, player, displayName.getValue().trim(), url,
                pose.getValue(), h, window.getValue()));
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
        g.text(font, Component.translatable("screen.craftmaster.portrait.hang_hint").withStyle(ChatFormatting.DARK_GRAY), left + 150, y, LABEL);
        y += step;
        g.text(font, Component.translatable("screen.craftmaster.portrait.window_label"), left + 8, y, LABEL);
        if (error != null) g.text(font, error.copy().withStyle(ChatFormatting.RED), left + 8, top + h - 14, 0xFFFF5555);
        else g.text(font, Component.translatable("screen.craftmaster.portrait.hint").withStyle(ChatFormatting.DARK_GRAY), left + 8, top + h - 14, LABEL);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }
}
