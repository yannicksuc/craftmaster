package fr.lordfinn.craftmaster.client.replay;

import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.client.broadcast.ShowStateClient;
import fr.lordfinn.craftmaster.replay.ReplayNetwork.ReplayStatus;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * The replay bar for the mouse (key M): the picture stays, the bar at the bottom answers clicks (the timeline seeks,
 * a marker shows its name under the mouse) and rows of buttons above it: playback (pause, speeds, markers), who
 * directs (Direct, Auto, Manuel, Libre), which file (the zone, each player's own view), the cameras of the lieu,
 * the wide shot and a shot on each contestant (click: follow shot; Shift-click: through their eyes).
 */
final class ReplayScreen extends Screen {

    private static final int BH = 16, GAP = 3;
    private String built = "";

    ReplayScreen() {
        super(Component.literal("Replay"));
    }

    @Override
    protected void init() {
        built = signature();
        int y = ReplayBar.top(height) - BH - 4;
        int left = ReplayBar.MARGIN;
        Row r = new Row(left, y);

        r.add(ReplayClient.paused() ? "▶ Lecture" : "❚❚ Pause", "B", b -> ReplayClient.togglePause());
        for (float s : ReplayClient.SPEEDS) r.add("×" + ReplayBar.speed(s), "↑ ↓", b -> ReplayClient.speed(s));
        r.add("◀ Repère", "Page préc.", b -> ReplayClient.marker(-1));
        r.add("Repère ▶", "Page suiv.", b -> ReplayClient.marker(1));
        r.gap();
        // The camera modes stay together on one line
        int modes = 0;
        for (ReplayDirector.Mode m : ReplayDirector.Mode.values()) modes += font.width("● " + m.label) + 10 + GAP;
        r.fit(modes);
        for (ReplayDirector.Mode m : ReplayDirector.Mode.values())
            r.add((ReplayDirector.mode() == m ? "● " : "") + m.label, tip(m), b -> {
                ReplayClient.setMode(m);
                rebuildWidgets();
            });
        r.gap();
        r.add("Fermer le replay", null, b -> {
            ReplayClient.close();
            ReplayClient.panelWanted = false;
            onClose();
        });

        r.newLine();
        ReplayStatus s = ReplayClient.status();
        if (s != null && s.views().size() > 1) {
            r.label("Fichier :");
            for (String v : s.views())
                r.add((v.equals(s.view()) ? "● " : "") + (v.equals("zone") ? "Zone" : v),
                        v.equals("zone") ? "Toute la zone de l'épreuve" : "Ce que le jeu de " + v + " a reçu (sa vue, son interface)",
                        b -> ReplayClient.view(v));
            r.gap();
        }
        r.label("Plan :");
        r.add("Large", "0", b -> pick(ReplayDirector.wide()));
        for (CameraSpot c : ShowStateClient.focused().all())
            if (c.number() >= 1 && c.number() <= 9) r.add(String.valueOf(c.number()), c.label(), b -> pick(ReplayDirector.camera(String.valueOf(c.number()))));
        r.gap();
        for (String p : ReplayDirector.contestants())
            r.add(p, "Clic : le suivre · Maj+clic : par ses yeux", b -> pick(ReplayDirector.player(p, 0, minecraft.hasShiftDown()) != null));
    }

    private static String tip(ReplayDirector.Mode m) {
        return switch (m) {
            case DIRECT -> "Les plans choisis en direct (sinon la réalisation auto)";
            case AUTO -> "Recalculée : qui marque ou est sous le projecteur, sinon le plan large";
            case MANUAL -> "Touches 1–9, molette, 0 plan large, Tab joueurs";
            case FREE -> "Ta propre caméra (mode diffusion coupé)";
        };
    }

    private void pick(boolean ok) {
        if (ok && !fr.lordfinn.craftmaster.client.broadcast.Broadcast.active()) fr.lordfinn.craftmaster.client.broadcast.Broadcast.set(true);
        ReplayClient.lastInput = System.currentTimeMillis();
        rebuildWidgets();
    }

    /** What the buttons depend on: rebuilt when it changes (pause, mode, file, cameras, contestants). */
    private static String signature() {
        ReplayStatus s = ReplayClient.status();
        return ReplayClient.paused() + "|" + ReplayDirector.mode() + "|" + (s == null ? "" : s.view() + s.views())
                + "|" + ShowStateClient.focused().size() + "|" + ReplayDirector.contestants();
    }

    @Override
    public void tick() {
        if (!ReplayClient.active()) {
            onClose();
            return;
        }
        if (!signature().equals(built)) rebuildWidgets();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (super.mouseClicked(e, doubleClick)) return true;
        long t = ReplayBar.timeAt(width, height, e.x(), e.y());
        if (t >= 0) {
            ReplayClient.seek(t);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (ReplayClient.PANEL_KEY.matches(event)) {
            onClose();
            return true;
        }
        if (ReplayClient.PLAY_KEY.matches(event)) {
            ReplayClient.togglePause();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        // Closed by the user (Esc, M): not reopened; closed by a loading screen (a seek back, a file switch): it is
        ReplayClient.panelWanted = false;
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {}

    /** Buttons laid out left to right, a new line above when full. */
    private final class Row {
        private final int left;
        private int x, y;

        Row(int left, int y) {
            this.left = left;
            this.x = left;
            this.y = y;
        }

        void add(String text, String tooltip, Consumer<Button> action) {
            int w = font.width(text) + 10;
            if (x + w > width - ReplayBar.MARGIN) newLine();
            Button.Builder b = Button.builder(Component.literal(text), action::accept).bounds(x, y, w, BH);
            if (tooltip != null) b.tooltip(Tooltip.create(Component.literal(tooltip)));
            addRenderableWidget(b.build());
            x += w + GAP;
        }

        void label(String text) {
            int w = font.width(text) + 4;
            if (x + w + 30 > width - ReplayBar.MARGIN) newLine();
            int lx = x, ly = y;
            addRenderableOnly((g, mx, my, pt) -> g.text(font, Component.literal(text).getVisualOrderText(), lx, ly + 4, 0xFFFFFFFF, true));
            x += w + GAP;
        }

        /** A new line first if {@code w} more pixels do not fit on this one. */
        void fit(int w) {
            if (x + w > width - ReplayBar.MARGIN) newLine();
        }

        void gap() {
            x += 8;
        }

        void newLine() {
            if (x == left) return;
            x = left;
            y -= BH + GAP;
        }
    }
}
