package fr.lordfinn.craftmaster.client.score;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.client.broadcast.Broadcast;
import fr.lordfinn.craftmaster.client.task.TwitchChat;
import fr.lordfinn.craftmaster.client.ui.PlayerFrame;
import fr.lordfinn.craftmaster.client.ui.Ui;
import fr.lordfinn.craftmaster.network.Network;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The player frame in the HUD: the local player's face with his season total under it, in a corner of the screen.
 * It follows the rankings the server sends (at join, then after every change), so it updates by itself:
 * players see their published total, organisers the live one. Hidden when the player has no score, in broadcast
 * mode, with the HUD hidden (F1) or while a screen is open. Settings: {@code playerFrame} in
 * {@code config/craftmaster-client.json} ({@code enabled}, {@code corner}: top_left, top_right, bottom_left,
 * bottom_right).
 */
public final class PlayerFrameHud {

    private PlayerFrameHud() {}

    public static final class Settings {
        public boolean enabled = true;
        public String corner = "top_right";
    }

    private static final int FACE = 24;
    /** Latest season seen in a ranking, and the local player's total in it (null: no score). */
    private static int season = -1;
    private static Integer total;
    private static Integer previous;
    private static long changedAt;

    public static void init() {
        HudElementRegistry.addLast(CraftMaster.id("player_frame"), PlayerFrameHud::extract);
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(() -> {
            season = -1;
            total = null;
        }));
    }

    /** Every ranking the client gets (the receiver of the ranking screen calls this first). */
    public static void accept(Network.Ranking r) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || r.season() < season) return;
        season = r.season();
        String me = mc.player.getGameProfile().name();
        Integer t = null;
        for (Network.Row row : r.rows()) if (row.player().equalsIgnoreCase(me)) t = row.total();
        if (t != null && total != null && !t.equals(total)) {
            previous = total;
            changedAt = System.currentTimeMillis();
        }
        total = t;
    }

    private static void extract(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        TwitchChat.Config config = TwitchChat.config();
        if (config.playerFrame == null || !config.playerFrame.enabled || total == null || mc.player == null) return;
        if (mc.gui.screen() != null || Broadcast.active()) return;
        int w = FACE + 4, h = PlayerFrame.height(FACE, false);
        String c = config.playerFrame.corner == null ? "" : config.playerFrame.corner;
        int x = c.endsWith("left") ? 6 : g.guiWidth() - w - 6;
        int y = c.startsWith("bottom") ? g.guiHeight() - h - 40 : 6;
        String me = mc.player.getGameProfile().name();
        // The score is a digit head from -10 to +10: a season total above that is written as a number instead
        boolean small = total >= -10 && total <= 10;
        PlayerFrame.draw(g, mc.font, me, small ? total : null, x, y, FACE, false, System.currentTimeMillis() - changedAt < 1500);
        if (!small) {
            Ui.text(g, mc.font, Component.literal(String.valueOf(total)), x + w / 2f, y + FACE + 6 + PlayerFrame.scoreSize(FACE) / 2f,
                    1.5f, 0xFFFFFFFF, true);
        }
        long since = System.currentTimeMillis() - changedAt;
        if (previous != null && since < 2500) {
            int d = total - previous;
            String s = d > 0 ? "+" + d : String.valueOf(d);
            g.text(mc.font, s, c.endsWith("left") ? x + w + 4 : x - mc.font.width(s) - 4, y + 8, d > 0 ? 0xFF7FD8E0 : 0xFFF1BC48, true);
        }
    }
}
