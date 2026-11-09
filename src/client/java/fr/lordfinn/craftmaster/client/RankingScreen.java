package fr.lordfinn.craftmaster.client;

import fr.lordfinn.craftmaster.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Season ranking: podium colours, each player's head, total with a bar, and the detail per round when hovering a
 * line. Arrows (or ← →) change season; it refreshes live. Players see the published scores; organisers see the
 * live ones, with the points not published yet marked beside the total.
 */
public class RankingScreen extends Screen {

    private static final int ROW_H = 22;
    private static final int GOLD = 0xFFFFC23D, SILVER = 0xFFD5DCE4, BRONZE = 0xFFD98A4E;
    private static final int PANEL = 0xE0121018, BORDER = 0xFFFFC23D, ROW_ALT = 0x30FFFFFF, ROW_ME = 0x40FFC23D, UNPUBLISHED = 0xFFFF9F43;

    private static final ChatFormatting UNPUBLISHED_STYLE = ChatFormatting.GOLD;

    private Network.Ranking data;
    private final Map<String, ItemStack> heads = new HashMap<>();
    private double scroll;
    private Button prev, next;

    // Panel layout, computed in init()
    private int left, top, panelW, panelH, listTop, listH;

    public RankingScreen(Network.Ranking data) {
        super(Component.translatable("screen.craftmaster.ranking"));
        this.data = data;
    }

    public int season() {
        return data.season();
    }

    public void update(Network.Ranking fresh) {
        data = fresh;
        updateButtons();
    }

    @Override
    protected void init() {
        panelW = Math.min(360, width - 20);
        panelH = Math.min(300, height - 20);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        listTop = top + 46;
        listH = panelH - 46 - 22;
        prev = addRenderableWidget(Button.builder(Component.literal("◀"), b -> changeSeason(-1)).bounds(left + 8, top + 8, 20, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal("▶"), b -> changeSeason(1)).bounds(left + panelW - 28, top + 8, 20, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        if (prev == null) return;
        int i = data.seasons().indexOf(data.season());
        prev.active = i > 0 || (i < 0 && !data.seasons().isEmpty());
        next.active = i >= 0 && i < data.seasons().size() - 1;
    }

    private void changeSeason(int delta) {
        List<Integer> seasons = data.seasons();
        if (seasons.isEmpty()) return;
        int i = seasons.indexOf(data.season());
        int target = i < 0 ? seasons.getLast() : seasons.get(Math.max(0, Math.min(seasons.size() - 1, i + delta)));
        if (target == data.season()) return;
        scroll = 0;
        // Shown right away (empty) while the server answers
        data = Network.Ranking.empty(target, seasons);
        updateButtons();
        CraftMasterClient.openRanking(minecraft, target);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (CraftMasterClient.RANKING_KEY.matches(event)) {
            onClose();
            return true;
        }
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT) { changeSeason(-1); return true; }
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT) { changeSeason(1); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - sy * ROW_H));
        return true;
    }

    private int maxScroll() {
        return Math.max(0, data.rows().size() * ROW_H - listH);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ItemStack head(String player) {
        return heads.computeIfAbsent(player, p -> {
            ItemStack s = new ItemStack(Items.PLAYER_HEAD);
            s.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(p));
            return s;
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        // Panel
        g.fill(left, top, left + panelW, top + panelH, PANEL);
        g.outline(left, top, panelW, panelH, BORDER);
        g.centeredText(font, Component.literal("CraftMaster").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), width / 2, top + 8, 0xFFFFFFFF);
        Component season = data.name().isEmpty() ? Component.translatable("screen.craftmaster.season", data.season()) : Component.literal(data.name());
        // Only admins receive a hidden season: remind them the players cannot see it yet
        if (data.hidden()) season = season.copy().append(Component.translatable("screen.craftmaster.hidden").withStyle(ChatFormatting.LIGHT_PURPLE));
        // Organisers see the live scores; players see the published ones
        if (data.live()) season = season.copy().append(Component.translatable("screen.craftmaster.live").withStyle(ChatFormatting.AQUA));
        g.centeredText(font, season, width / 2, top + 20, 0xFFE0E0E0);
        g.text(font, Component.translatable("screen.craftmaster.player"), left + 38, top + 34, 0xFF9A9AA8);
        Component pts = Component.translatable("screen.craftmaster.points");
        g.text(font, pts, left + panelW - 12 - font.width(pts), top + 34, 0xFF9A9AA8);
        g.horizontalLine(left + 6, left + panelW - 7, listTop - 2, 0x60FFFFFF);

        List<Network.Row> rows = data.rows();
        if (rows.isEmpty()) {
            Component empty = Component.translatable(data.hidden() ? "screen.craftmaster.hidden_season" : "screen.craftmaster.empty");
            g.centeredText(font, empty, width / 2, listTop + listH / 2 - 4, 0xFF9A9AA8);
        }

        int best = rows.isEmpty() ? 1 : Math.max(1, rows.getFirst().total());
        String me = minecraft != null && minecraft.player != null ? minecraft.player.getGameProfile().name() : "";
        Network.Row hovered = null;

        g.enableScissor(left + 1, listTop, left + panelW - 1, listTop + listH);
        for (int i = 0; i < rows.size(); i++) {
            int y = listTop + i * ROW_H - (int) scroll;
            if (y + ROW_H < listTop || y > listTop + listH) continue;
            Network.Row r = rows.get(i);
            int x0 = left + 6, x1 = left + panelW - 6;
            if (r.player().equalsIgnoreCase(me)) g.fill(x0, y, x1, y + ROW_H - 2, ROW_ME);
            else if (i % 2 == 1) g.fill(x0, y, x1, y + ROW_H - 2, ROW_ALT);

            int rank = i + 1;
            int color = rank == 1 ? GOLD : rank == 2 ? SILVER : rank == 3 ? BRONZE : 0xFFFFFFFF;
            String rankText = "#" + rank;
            g.text(font, rankText, x0 + 14 - font.width(rankText) / 2, y + 6, color);
            g.item(head(r.player()), x0 + 26, y + 2);
            g.text(font, r.player(), x0 + 46, y + 3, 0xFFFFFFFF);

            // Bar: share of the leader's points
            int barX = x0 + 46, barW = Math.max(10, x1 - 56 - barX);
            g.fill(barX, y + 14, barX + barW, y + 17, 0x40FFFFFF);
            g.fill(barX, y + 14, barX + Math.max(1, barW * Math.max(0, r.total()) / best), y + 17, color);

            String total = String.valueOf(r.total());
            g.text(font, total, x1 - 6 - font.width(total), y + 6, 0xFFFFE082);
            // Points not published yet: the players do not see them
            int unpublished = r.total() - r.publishedTotal();
            if (data.live() && unpublished != 0) {
                Component note = Component.translatable("screen.craftmaster.unpublished", (unpublished > 0 ? "+" : "") + unpublished);
                g.text(font, note, x1 - 10 - font.width(total) - font.width(note), y + 3, UNPUBLISHED);
            }

            if (mouseX >= x0 && mouseX < x1 && mouseY >= Math.max(y, listTop) && mouseY < Math.min(y + ROW_H, listTop + listH)) hovered = r;
        }
        g.disableScissor();

        // Scroll bar
        if (maxScroll() > 0) {
            int trackX = left + panelW - 4;
            int thumbH = Math.max(12, listH * listH / (rows.size() * ROW_H));
            int thumbY = listTop + (int) ((listH - thumbH) * (scroll / maxScroll()));
            g.fill(trackX, listTop, trackX + 2, listTop + listH, 0x30FFFFFF);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, BORDER);
        }

        g.centeredText(font, Component.translatable("screen.craftmaster.hint"), width / 2, top + panelH - 14, 0xFF6E6E7A);

        super.extractRenderState(g, mouseX, mouseY, partial);

        if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(hovered.player()).withStyle(ChatFormatting.GOLD));
            int listed = 0;
            for (int t = 0; t < data.tasks().size(); t++) {
                listed += hovered.perTask().get(t);
                lines.add(Component.literal(data.tasks().get(t) + " : ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.valueOf(hovered.perTask().get(t))).withStyle(ChatFormatting.WHITE)));
            }
            // Points of archived or removed rounds still count in the total: show them so the sum adds up
            int other = hovered.total() - listed;
            if (other != 0)
                lines.add(Component.translatable("screen.craftmaster.other_tasks").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal(" : " + other).withStyle(ChatFormatting.WHITE)));
            if (data.live() && hovered.total() != hovered.publishedTotal())
                lines.add(Component.translatable("screen.craftmaster.published_total", hovered.publishedTotal()).withStyle(UNPUBLISHED_STYLE));
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }
}
