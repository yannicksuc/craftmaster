package fr.lordfinn.craftmaster.client.wardrobe;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.client.ui.Theme;
import fr.lordfinn.craftmaster.client.ui.Ui;
import fr.lordfinn.craftmaster.client.ui.UiButton;
import fr.lordfinn.craftmaster.wardrobe.Outfit;
import fr.lordfinn.craftmaster.wardrobe.OutfitSearch;
import fr.lordfinn.craftmaster.wardrobe.WardrobeNetwork;
import fr.lordfinn.craftmaster.wardrobe.WardrobeState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The outfit screen of a wardrobe: on the left the player dressed in what is under the mouse (or what they wear),
 * drag to turn; on the right the outfits as cards, favourites first, with a search bar (French words work, see
 * {@link OutfitSearch}).
 * <ul>
 *   <li>click a card: put the whole outfit on; click one of its pieces: only that piece (to mix outfits);</li>
 *   <li>right-click: favourite on / off; "Mes affaires": your own clothes again;</li>
 *   <li>"✎ Modifier" (organisers, or anyone in a storage wardrobe): a click chooses an outfit, then plain buttons
 *   change it; "Ajouter ce que je porte" makes a new one.</li>
 * </ul>
 */
public class WardrobeScreen extends Screen {

    private static final int CARD_W = 94, CARD_H = 32, GAP = 4, LEFT_W = 108;

    private BlockPos pos;
    private boolean show, custom, admin, inCostume;
    private String wearing;
    private Set<String> favourites = Set.of();
    private List<Outfit> outfits;

    private String query = "";
    private boolean favouritesOnly, editing;
    private int scroll, selected = -1;
    /** Turn of the preview, in degrees (drag it). */
    private float spin;
    private int x0, y0, w, h, gridX, gridY, gridW, gridH, cols, hintY;
    private EditBox search, nameBox;
    /** What the grid shows now (filtered, favourites first). */
    private final List<Card> cards = new ArrayList<>();
    private Card hovered;
    private int hoveredPiece = -1;
    private RemotePlayer mannequin;
    private List<ItemStack> dressed = List.of();

    private record Card(int index, Outfit outfit, String key, Component name, String search) {}

    public WardrobeScreen(WardrobeNetwork.Open p) {
        super(Component.translatable("screen.craftmaster.wardrobe"));
        update(p);
    }

    public BlockPos pos() {
        return pos;
    }

    /** New contents from the server (after an action): same search, scroll and choice. */
    public void update(WardrobeNetwork.Open p) {
        pos = p.pos();
        show = p.show();
        custom = p.custom();
        admin = p.admin();
        inCostume = p.inCostume();
        wearing = p.wearing();
        favourites = new HashSet<>(p.favourites());
        outfits = p.outfits();
        if (selected >= outfits.size()) selected = -1;
        if (!manage()) editing = false;
        if (minecraft != null) rebuildKeepingName();
    }

    private void rebuildKeepingName() {
        String name = nameBox == null ? "" : nameBox.getValue();
        rebuildWidgets();
        if (nameBox != null) nameBox.setValue(name);
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("screen.craftmaster.wardrobe." + key, args);
    }

    /** Can change the outfits: organisers, or anyone in a storage wardrobe. */
    private boolean manage() {
        return admin || !show;
    }

    private int bw(Component label) {
        return Math.max(18, font.width(label) + 10);
    }

    /** A button of the bottom grid: {@code w} wide, 20 high. */
    private UiButton cell(int x, int y, int w, Component label, String tip, boolean active, Runnable onPress) {
        UiButton b = UiButton.of(x, y, w, 20, label, onPress);
        if (tip != null) b.setTooltip(Tooltip.create(t(tip)));
        b.active = active;
        return addRenderableWidget(b);
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        w = Math.min(width - 12, 440);
        h = Math.min(height - 12, 250);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        int rx = x0 + LEFT_W + 6, rw = w - LEFT_W - 12;

        // Title bar: the edit mode (lit while on; click again to leave it)
        if (manage()) {
            Component edit = t("edit");
            UiButton e = UiButton.of(x0 + w - 6 - bw(edit), y0 + 2, bw(edit), 14, edit, () -> {
                editing = !editing;
                selected = -1;
                rebuildKeepingName();
            }).on(() -> editing);
            e.setTooltip(Tooltip.create(t("edit.tip")));
            addRenderableWidget(e);
        }

        // Search and the favourites filter
        Component fav = t("favourites");
        int favW = bw(fav);
        search = new EditBox(font, rx, y0 + 22, rw - favW - 4, 16, t("search"));
        search.setHint(t("search.hint").copy().withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(s -> {
            query = s;
            scroll = 0;
            filter();
        });
        addRenderableWidget(search);
        UiButton favButton = UiButton.of(rx + rw - favW, y0 + 22, favW, 16, fav, () -> {
            favouritesOnly = !favouritesOnly;
            scroll = 0;
            filter();
        }).on(() -> favouritesOnly);
        favButton.setTooltip(Tooltip.create(t("favourites.tip")));
        addRenderableWidget(favButton);

        // Bottom: two rows of 20 px on a grid of three equal columns; the second row is level with "Mes affaires"
        int rowB = y0 + h - 26, rowA = rowB - 24;
        UiButton back = UiButton.of(x0 + 6, rowB, LEFT_W - 6, 20, t("back"), () -> act("back", -1, ""));
        back.setTooltip(Tooltip.create(t("back.tip")));
        addRenderableWidget(back);

        nameBox = null;
        if (editing) {
            int col = (rw - 4) / 3;
            int c1 = rx, c2 = rx + col + 2, c3 = rx + 2 * (col + 2), last = rx + rw - c3;
            Outfit chosen = selected >= 0 && selected < outfits.size() ? outfits.get(selected) : null;
            // Row A: a name, add what I wear, rename the chosen one
            nameBox = new EditBox(font, c1, rowA, col, 20, t("name"));
            nameBox.setMaxLength(Outfit.MAX_NAME);
            nameBox.setHint(t("name.hint").copy().withStyle(ChatFormatting.DARK_GRAY));
            addRenderableWidget(nameBox);
            cell(c2, rowA, col, show ? t("add") : t("store"), show ? "add.tip" : "store.tip", true, () -> act("add", -1, nameBox.getValue()));
            cell(c3, rowA, last, t("rename"), "rename.tip", chosen != null, () -> act("rename", selected, nameBox.getValue()));
            // Row B: the chosen outfit (replace, delete, move), or the wardrobe itself (its type, every outfit again)
            if (chosen != null) {
                if (show) cell(c1, rowB, col, t("replace"), "replace.tip", true, () -> act("replace", selected, nameBox.getValue()));
                cell(show ? c2 : c1, rowB, col, show ? t("delete") : t("take_out"), show ? "delete.tip" : "take_out.tip", true, () -> {
                    act("delete", selected, "");
                    selected = -1;
                });
                int half = (last - 2) / 2;
                cell(c3, rowB, half, t("move_up"), "move.tip", selected > 0, () -> {
                    act("up", selected, "");
                    selected--;
                });
                cell(c3 + half + 2, rowB, last - half - 2, t("move_down"), "move.tip", selected < outfits.size() - 1, () -> {
                    act("down", selected, "");
                    selected++;
                });
            } else {
                cell(c1, rowB, col, show ? t("type.show") : t("type.storage"), show ? "type.show.tip" : "type.storage.tip", admin,
                        () -> act("mode", -1, ""));
                if (show) cell(c2, rowB, rx + rw - c2, t("auto"), custom ? "auto.tip" : "auto.already", admin && custom, () -> act("auto", -1, ""));
            }
            hintY = rowA - 12;
        } else {
            hintY = rowB + 6;
        }
        gridX = rx;
        gridY = y0 + 42;
        gridW = rw;
        gridH = hintY - 4 - gridY;
        cols = Math.max(1, (gridW + GAP) / (CARD_W + GAP));
        filter();
    }

    private void filter() {
        cards.clear();
        List<Card> favs = new ArrayList<>(), rest = new ArrayList<>();
        for (int i = 0; i < outfits.size(); i++) {
            Outfit o = outfits.get(i);
            Component name = o.displayName();
            String key = o.key();
            Card c = new Card(i, o, key, name, describe(o, name));
            boolean fav = favourites.contains(key);
            if ((favouritesOnly && !fav) || !OutfitSearch.matches(query, c.search())) continue;
            // In the edit mode the order is the wardrobe's (moving outfits); otherwise favourites first
            (fav && !editing ? favs : rest).add(c);
        }
        cards.addAll(favs);
        cards.addAll(rest);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    /** The words of an outfit for the search: its name, its pieces' names and ids. */
    private static String describe(Outfit o, Component name) {
        StringBuilder sb = new StringBuilder(name.getString());
        for (ItemStack s : o.pieces()) {
            if (s.isEmpty()) continue;
            sb.append(' ').append(s.getHoverName().getString()).append(' ').append(BuiltInRegistries.ITEM.getKey(s.getItem()));
        }
        return sb.toString();
    }

    private int rows() {
        return (cards.size() + cols - 1) / cols;
    }

    private int visibleRows() {
        return Math.max(1, (gridH + GAP) / (CARD_H + GAP));
    }

    private int maxScroll() {
        return Math.max(0, rows() - visibleRows());
    }

    // ---------------------------------------------------------------- actions

    private void act(String action, int index, String text) {
        if (ClientPlayNetworking.canSend(WardrobeNetwork.Act.TYPE)) ClientPlayNetworking.send(new WardrobeNetwork.Act(pos, action, index, text));
    }

    private Card cardAt(double mx, double my) {
        if (!Ui.inside(mx, my, gridX, gridY, gridW, gridH)) return null;
        int c = (int) ((mx - gridX) / (CARD_W + GAP)), r = (int) ((my - gridY) / (CARD_H + GAP));
        if (c >= cols || mx - gridX - c * (CARD_W + GAP) > CARD_W || my - gridY - r * (CARD_H + GAP) > CARD_H) return null;
        int i = (r + scroll) * cols + c;
        return i < cards.size() && r < visibleRows() ? cards.get(i) : null;
    }

    /** The piece (0 head … 3 feet) under the mouse on a card, -1 elsewhere on it (or an empty piece). */
    private int pieceAt(Card card, double mx, double my) {
        int i = cards.indexOf(card) - scroll * cols;
        int x = gridX + (i % cols) * (CARD_W + GAP), y = gridY + (i / cols) * (CARD_H + GAP);
        for (int p = 0; p < 4; p++)
            if (Ui.inside(mx, my, x + 3 + p * 22, y + 14, 18, 16)) return card.outfit().pieces().get(p).isEmpty() ? -1 : p;
        return -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (super.mouseClicked(e, doubleClick)) return true;
        Card c = cardAt(e.x(), e.y());
        if (c == null) return false;
        if (e.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            act("favourite", c.index(), "");
            return true;
        }
        if (e.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        if (editing) {
            selected = selected == c.index() ? -1 : c.index();
            rebuildWidgets();
            if (nameBox != null && selected >= 0) nameBox.setValue(c.outfit().name().isEmpty() ? c.name().getString() : c.outfit().name());
            return true;
        }
        int piece = pieceAt(c, e.x(), e.y());
        if (piece >= 0) act("wear_piece", c.index(), Integer.toString(piece));
        else act("wear", c.index(), "");
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (Ui.inside(e.x(), e.y(), x0 + 6, y0 + 22, LEFT_W - 6, h - 74)) {
            spin = (spin + (float) dx * 2.5f) % 360f;
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (Ui.inside(mx, my, gridX, gridY, gridW, gridH)) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(sy)));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void removed() {
        act("close", -1, "");
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0x60000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        Ui.panel(g, font, x0, y0, w, h, null);
        g.fill(x0, y0, x0 + w, y0 + 18, Theme.VELVET);
        g.fill(x0, y0 + 15, x0 + w, y0 + 18, Theme.VELVET_DARK);
        g.horizontalLine(x0 + 1, x0 + w - 2, y0 + 18, Theme.BORDER);
        g.outline(x0, y0, w, h, Theme.BORDER);
        g.text(font, title.copy().withStyle(ChatFormatting.BOLD), x0 + 8, y0 + 5, Theme.TITLE);
        Component count = cards.size() == 1 ? t("count.one") : t("count", cards.size());
        g.text(font, count, x0 + 12 + font.width(title.copy().withStyle(ChatFormatting.BOLD)), y0 + 5, Theme.TEXT_DIM, false);

        hovered = cardAt(mouseX, mouseY);
        hoveredPiece = hovered == null || editing ? -1 : pieceAt(hovered, mouseX, mouseY);
        extractPreview(g, mouseX, mouseY);
        extractGrid(g);

        Component hint;
        if (editing) {
            Outfit chosen = selected >= 0 && selected < outfits.size() ? outfits.get(selected) : null;
            hint = chosen == null ? t("hint.edit") : t("hint.chosen", chosen.displayName());
        } else {
            hint = t("hint");
        }
        g.text(font, font.plainSubstrByWidth(hint.getString(), gridW), gridX, hintY, editing ? Theme.GOLD : Theme.TEXT_DIM, false);
        if (cards.isEmpty()) {
            Component empty = favouritesOnly && query.isBlank() ? t("empty.favourites")
                    : query.isBlank() ? t(show ? "empty.show" : "empty.storage") : t("no_match", query);
            g.centeredText(font, empty, gridX + gridW / 2, gridY + 20, Theme.TEXT_DIM);
        }
        super.extractRenderState(g, mouseX, mouseY, partial);
        if (hovered != null) g.setTooltipForNextFrame(tooltip(hovered), mouseX, mouseY);
    }

    private List<FormattedCharSequence> tooltip(Card c) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        boolean fav = favourites.contains(c.key());
        lines.add(c.name().copy().withStyle(ChatFormatting.GOLD).append(fav ? Component.literal(" ★").withStyle(ChatFormatting.YELLOW) : Component.empty())
                .getVisualOrderText());
        String[] parts = {"head", "body", "legs", "feet"};
        for (int i = 0; i < 4; i++) {
            ItemStack s = c.outfit().pieces().get(i);
            ChatFormatting style = i == hoveredPiece ? ChatFormatting.WHITE : ChatFormatting.GRAY;
            lines.add(t("piece." + parts[i], s.isEmpty() ? t("nothing") : s.getHoverName()).copy().withStyle(style).getVisualOrderText());
        }
        Component action = editing ? t("tip.choose")
                : hoveredPiece >= 0 ? t("tip.wear_piece", c.outfit().pieces().get(hoveredPiece).getHoverName()) : t("tip.wear");
        lines.add(action.copy().withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        lines.add((fav ? t("tip.unfavourite") : t("tip.favourite")).copy().withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        return lines;
    }

    private void extractGrid(GuiGraphicsExtractor g) {
        g.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);
        int vis = visibleRows();
        for (int r = 0; r < vis; r++) {
            for (int c = 0; c < cols; c++) {
                int i = (r + scroll) * cols + c;
                if (i >= cards.size()) break;
                Card card = cards.get(i);
                int x = gridX + c * (CARD_W + GAP), y = gridY + r * (CARD_H + GAP);
                boolean worn = !wearing.isEmpty() && card.key().equals(wearing);
                boolean fav = favourites.contains(card.key());
                g.fill(x, y, x + CARD_W, y + CARD_H, Theme.shade(Theme.VELVET, -0.35f));
                if (card == hovered && hoveredPiece < 0) g.fill(x, y, x + CARD_W, y + CARD_H, Theme.HOVER);
                if (editing && card.index() == selected) {
                    g.fill(x, y, x + CARD_W, y + CARD_H, Theme.SELECTED);
                    g.outline(x, y, CARD_W, CARD_H, Theme.GOLD);
                } else {
                    g.outline(x, y, CARD_W, CARD_H, worn ? Theme.OK : Theme.BORDER_DARK);
                }
                int nameW = CARD_W - 6 - (fav ? 9 : 0);
                g.text(font, font.plainSubstrByWidth(card.name().getString(), nameW), x + 3, y + 3, worn ? Theme.OK : Theme.TEXT, false);
                if (fav) g.text(font, "★", x + CARD_W - 9, y + 3, Theme.GOLD, false);
                for (int p = 0; p < 4; p++) {
                    ItemStack s = card.outfit().pieces().get(p);
                    int ix = x + 3 + p * 22, iy = y + 14;
                    boolean on = card == hovered && p == hoveredPiece;
                    g.fill(ix, iy, ix + 18, iy + 16, on ? 0x60FFC23D : 0x30000000);
                    if (on) g.outline(ix, iy, 18, 16, Theme.GOLD);
                    if (!s.isEmpty()) g.fakeItem(s, ix + 1, iy);
                }
            }
        }
        g.disableScissor();
        // Scroll bar
        if (maxScroll() > 0) {
            int bh = Math.max(12, gridH * vis / rows());
            int by = gridY + (gridH - bh) * scroll / maxScroll();
            g.fill(gridX + gridW + 2, gridY, gridX + gridW + 4, gridY + gridH, 0x40000000);
            g.fill(gridX + gridW + 2, by, gridX + gridW + 4, by + bh, Theme.BORDER);
        }
    }

    private void extractPreview(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int px = x0 + 6, py = y0 + 22, pw = LEFT_W - 6, ph = h - 22 - 52;
        Ui.paper(g, px, py, pw, ph);
        g.fill(px + 1, py + ph - 14, px + pw - 1, py + ph - 1, Theme.shade(Theme.PAPER, -0.12f));
        if (mc.player == null || mc.level == null) return;
        // What the player wears now, with the outfit (or the single piece) under the mouse
        List<ItemStack> look = new ArrayList<>();
        for (EquipmentSlot s : Outfit.SLOTS) look.add(mc.player.getItemBySlot(s));
        Component label;
        if (hovered != null && !editing && hoveredPiece >= 0) {
            look.set(hoveredPiece, hovered.outfit().pieces().get(hoveredPiece));
            label = hovered.outfit().pieces().get(hoveredPiece).getHoverName();
        } else if (hovered != null) {
            look = new ArrayList<>(hovered.outfit().pieces());
            label = hovered.name();
        } else {
            label = wearing.isEmpty() ? t("own_clothes") : wearing.equals(WardrobeState.MIX) ? t("mix") : wornName();
        }
        RemotePlayer m = mannequin(mc);
        if (!same(look, dressed)) {
            for (int i = 0; i < 4; i++) m.setItemSlot(Outfit.SLOTS[i], look.get(i).copy());
            dressed = look;
        }
        int scale = Math.min(pw, ph) * 30 / 100;
        extractMannequin(g, px + 2, py + 2, px + pw - 2, py + ph - 14, scale, 0.0625f, mouseX, mouseY, m);
        g.centeredText(font, Component.literal(font.plainSubstrByWidth(label.getString(), pw - 6)), px + pw / 2, py + ph - 11, Theme.INK);
        Component under = hovered != null ? t("preview") : inCostume ? t("in_costume") : t("you");
        g.centeredText(font, under, px + pw / 2, py + ph + 4, Theme.TEXT_DIM);
    }

    private Component wornName() {
        for (Outfit o : outfits) if (o.key().equals(wearing)) return o.displayName();
        return t("in_costume");
    }

    /**
     * Like the inventory's player (the head follows the mouse), without the name tag, turned by {@link #spin}
     * (drag the preview to turn it all the way round).
     */
    private void extractMannequin(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int scale, float yOffset, float mx, float my,
                                  RemotePlayer m) {
        float cx = (x0 + x1) / 2f, cy = (y0 + y1) / 2f;
        float ax = (float) Math.atan((cx - mx) / 40f), ay = (float) Math.atan((cy - my) / 40f);
        Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cam = new Quaternionf().rotateX(ay * 20f * Mth.DEG_TO_RAD);
        rot.mul(cam);
        EntityRenderState st = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(m).createRenderState(m, 1f);
        st.shadowPieces.clear();
        st.outlineColor = 0;
        st.nameTag = null;
        if (st instanceof LivingEntityRenderState l) {
            l.bodyRot = 180f + spin;
            l.yRot = Mth.clamp(ax * 20f, -40f, 40f);
            l.xRot = -ay * 20f;
            l.boundingBoxWidth /= l.scale;
            l.boundingBoxHeight /= l.scale;
            l.scale = 1f;
        }
        g.entity(st, scale, new Vector3f(0, st.boundingBoxHeight / 2f + yOffset, 0), rot, cam, x0, y0, x1, y1);
    }

    /** A copy of the player (same skin) that wears the outfit under the mouse: the real player is never touched. */
    private RemotePlayer mannequin(Minecraft mc) {
        if (mannequin == null || mannequin.level() != mc.level) {
            mannequin = new RemotePlayer(mc.level, mc.player.getGameProfile());
            mannequin.setSilent(true);
            // Never in the world: an id of its own (item models of a living entity are keyed by it)
            mannequin.setId(-0x5741D);
            dressed = List.of();
        }
        return mannequin;
    }

    private static boolean same(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        return true;
    }
}
