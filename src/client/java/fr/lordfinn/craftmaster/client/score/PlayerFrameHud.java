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
 * bottom_right, {@code season}: "active" by default — the server's active season — or a season number).
 */
public final class PlayerFrameHud {

    private PlayerFrameHud() {}

    public static final class Settings {
        public boolean enabled = true;
        public String corner = "top_right";
        /** The season the frame shows: "active" (the server's active season) or a season number. */
        public String season = "active";
    }

    private static final int FACE = 24;
    /** The server's active season (-1: none / unknown). */
    private static int active = -1;
    /** The local player's total per season (absent: no score in it). */
    private static final java.util.Map<Integer, Integer> TOTALS = new java.util.HashMap<>();
    private static int shownSeason = -1;
    private static Integer total, previous;
    private static long changedAt;

    public static void init() {
        HudElementRegistry.addLast(CraftMaster.id("player_frame"), PlayerFrameHud::extract);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(Network.ActiveSeason.TYPE, (p, ctx) -> {
            active = p.season();
            follow();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(() -> {
            active = -1;
            TOTALS.clear();
            shownSeason = -1;
            total = previous = null;
        }));
    }

    /** The season chosen in the settings: "active" (default) or a number. */
    static int wantedSeason() {
        Settings s = TwitchChat.config().playerFrame;
        String v = s == null || s.season == null ? "active" : s.season.trim();
        try {
            return v.isEmpty() || v.equalsIgnoreCase("active") ? active : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return active;
        }
    }

    /** Every ranking the client gets (the receiver of the ranking screen calls this first). */
    public static void accept(Network.Ranking r) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String me = mc.player.getGameProfile().name();
        TOTALS.remove(r.season());
        for (Network.Row row : r.rows()) if (row.player().equalsIgnoreCase(me)) TOTALS.put(r.season(), row.total());
        follow();
    }

    /** Picks the total of the wanted season; a change of that same season's total flashes "+3". */
    private static void follow() {
        int season = wantedSeason();
        Integer t = TOTALS.get(season);
        if (season == shownSeason && t != null && total != null && !t.equals(total)) {
            previous = total;
            changedAt = System.currentTimeMillis();
        }
        if (season != shownSeason) previous = null;
        shownSeason = season;
        total = t;
    }

    private static void extract(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        TwitchChat.Config config = TwitchChat.config();
        if (config.playerFrame == null || !config.playerFrame.enabled || mc.player == null) return;
        // A round's launch: the black screen is not covered
        if (fr.lordfinn.craftmaster.client.broadcast.Blackout.active()) return;
        // The setting may have changed (reload): follow it
        if (wantedSeason() != shownSeason) follow();
        // Organisers: a reminder while a score entry draft is not validated
        String draft = fr.lordfinn.craftmaster.client.regie.VoiceRegie.isAdmin() ? ScoreEntryScreen.draftRoundName() : null;
        if (draft != null) {
            Component d = Component.translatable("hud.craftmaster.draft", draft);
            String c0 = config.playerFrame.corner == null ? "" : config.playerFrame.corner;
            int dx = c0.endsWith("left") ? 6 : g.guiWidth() - mc.font.width(d) - 6;
            int dy = c0.startsWith("bottom") ? g.guiHeight() - 52 - PlayerFrame.height(FACE, false) : 10 + PlayerFrame.height(FACE, false);
            g.text(mc.font, d, dx, dy, 0xFFFFC23D, true);
        }
        if (total == null) return;
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
