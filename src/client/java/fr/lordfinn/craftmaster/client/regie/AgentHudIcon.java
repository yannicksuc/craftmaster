package fr.lordfinn.craftmaster.client.regie;

import fr.lordfinn.craftmaster.CraftMaster;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * The régie agent's HUD icon, next to Simple Voice Chat's microphone and drawn like it: a 16 × 16 sprite at the
 * place its settings give ({@code hud_icon_pos_x / _y}, negative = from the right / bottom, {@code hud_icon_scale}),
 * on the side away from the screen's edge. Listening (N held): the sparkle with sound bars; working: the sparkle
 * with a blinking small one. Hidden with Voice Chat's own icons (its "hide icons" key), with F1, and never in a
 * broadcasting game. Voice Chat has no API for its HUD: its client settings are read by reflection, and without it
 * the icon takes its default place (16, -16).
 */
final class AgentHudIcon {

    private AgentHudIcon() {}

    private static final Identifier LISTENING = CraftMaster.id("icons/regie_agent_listening");
    private static final Identifier WORKING = CraftMaster.id("icons/regie_agent_working");
    private static final Identifier WORKING_2 = CraftMaster.id("icons/regie_agent_working2");

    /** Simple Voice Chat's client settings, if it is there: x, y, scale, hideIcons, showHudIcons. */
    private static Object config;
    private static Field posX, posY, scale, hide, show;
    private static Method get;
    private static boolean looked;

    static void init() {
        HudElementRegistry.addLast(CraftMaster.id("regie_agent"), (g, delta) -> extract(g));
    }

    private static void extract(GuiGraphicsExtractor g) {
        RegieAgent.State state = RegieAgent.state();
        if (state == RegieAgent.State.NONE || fr.lordfinn.craftmaster.client.broadcast.Broadcast.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        lookUp();
        if (bool(hide, false) || !bool(show, true)) return;
        int x = integer(posX, 16), y = integer(posY, -16);
        float s = (float) dbl(scale, 1.0);
        Identifier icon = state == RegieAgent.State.LISTENING ? LISTENING
                : (System.currentTimeMillis() / 400) % 2 == 0 ? WORKING : WORKING_2;
        var pose = g.pose();
        pose.pushMatrix();
        if (x < 0) pose.translate(mc.getWindow().getGuiScaledWidth(), 0);
        if (y < 0) pose.translate(0, mc.getWindow().getGuiScaledHeight());
        pose.translate(x, y);
        pose.scale(s, s);
        // Voice Chat's icon is at (x < 0 ? -16 : 0, y < 0 ? -16 : 0): this one sits beside it, towards the middle
        g.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x < 0 ? -34 : 18, y < 0 ? -16 : 0, 16, 16);
        pose.popMatrix();
    }

    private static void lookUp() {
        if (looked) return;
        looked = true;
        if (!FabricLoader.getInstance().isModLoaded("voicechat")) return;
        try {
            Class<?> client = Class.forName("de.maxhenkel.voicechat.VoicechatClient");
            config = client.getField("CLIENT_CONFIG").get(null);
            if (config == null) {
                looked = false;   // not loaded yet: try again later
                return;
            }
            Class<?> c = config.getClass();
            posX = c.getField("hudIconPosX");
            posY = c.getField("hudIconPosY");
            scale = c.getField("hudIconScale");
            hide = c.getField("hideIcons");
            show = c.getField("showHudIcons");
            get = Class.forName("de.maxhenkel.voicechat.configbuilder.entry.ConfigEntry").getMethod("get");
        } catch (ReflectiveOperationException | LinkageError e) {
            CraftMaster.LOG.warn("Régie IA : réglages de l'icône de Simple Voice Chat illisibles, place par défaut ({})", e.toString());
            config = null;
        }
    }

    private static Object value(Field f) {
        if (config == null || f == null || get == null) return null;
        try {
            return get.invoke(f.get(config));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static int integer(Field f, int def) {
        return value(f) instanceof Number n ? n.intValue() : def;
    }

    private static double dbl(Field f, double def) {
        return value(f) instanceof Number n ? n.doubleValue() : def;
    }

    private static boolean bool(Field f, boolean def) {
        return value(f) instanceof Boolean b ? b : def;
    }
}
