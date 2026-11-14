package fr.lordfinn.craftmaster.client.studio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.camera.CameraKit;
import fr.lordfinn.craftmaster.client.ui.Theme;
import fr.lordfinn.craftmaster.client.ui.Ui;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A picked-up camera placed again with its travellings: do they follow the camera (moved and turned with it) or
 * stay where they were? Closed without choosing, they follow it.
 */
public class KitPlacementScreen extends Screen {

    private final String camera;
    private final String label;
    private final int count;
    private int x0, y0, w, h;
    private boolean answered;

    private KitPlacementScreen(JsonObject o) {
        super(Component.translatable("screen.craftmaster.kit.title", o.get("label").getAsString()));
        camera = o.get("camera").getAsString();
        label = o.get("label").getAsString();
        count = o.get("count").getAsInt();
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CameraKit.Ask.TYPE, (p, ctx) ->
                ctx.client().gui.setScreen(new KitPlacementScreen(JsonParser.parseString(p.json()).getAsJsonObject())));
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("screen.craftmaster.kit." + key, args);
    }

    @Override
    protected void init() {
        w = 230;
        h = 92;
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        Ui.panel(g, font, x0, y0, w, h, t("title", label));
        int y = y0 + 24;
        for (var line : font.split(t("question", count), w - 16)) {
            g.text(font, line, x0 + 8, y, Theme.TEXT, false);
            y += 10;
        }
        int bw = (w - 20) / 2, by = y0 + h - 30;
        boolean h1 = Ui.inside(mx, my, x0 + 6, by, bw, 20), h2 = Ui.inside(mx, my, x0 + 14 + bw, by, bw, 20);
        Ui.button(g, font, x0 + 6, by, bw, 20, t("relative"), true, h1, true);
        Ui.button(g, font, x0 + 14 + bw, by, bw, 20, t("absolute"), false, h2, true);
        super.extractRenderState(g, mx, my, partial);
        if (h1) Ui.tooltip(g, font, t("relative_tip"), mx, my);
        else if (h2) Ui.tooltip(g, font, t("absolute_tip"), mx, my);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        int bw = (w - 20) / 2, by = y0 + h - 30;
        if (Ui.inside((int) e.x(), (int) e.y(), x0 + 6, by, bw, 20)) return choose(true);
        if (Ui.inside((int) e.x(), (int) e.y(), x0 + 14 + bw, by, bw, 20)) return choose(false);
        return super.mouseClicked(e, doubleClick);
    }

    private boolean choose(boolean relative) {
        answered = true;
        TravellingEdit.send(camera, null, "kit", valueOf(relative));
        onClose();
        return true;
    }

    private static JsonObject valueOf(boolean relative) {
        JsonObject o = new JsonObject();
        o.addProperty("value", relative ? "relative" : "absolute");
        return o;
    }

    /** Closed without choosing: they follow the camera. */
    @Override
    public void removed() {
        super.removed();
        if (!answered) {
            answered = true;
            TravellingEdit.send(camera, null, "kit", valueOf(true));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
