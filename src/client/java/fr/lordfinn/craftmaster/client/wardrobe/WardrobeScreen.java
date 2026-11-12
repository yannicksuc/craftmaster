package fr.lordfinn.craftmaster.client.wardrobe;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.client.ui.Theme;
import fr.lordfinn.craftmaster.client.ui.Ui;
import fr.lordfinn.craftmaster.client.ui.UiButton;
import fr.lordfinn.craftmaster.wardrobe.Outfit;
import fr.lordfinn.craftmaster.wardrobe.OutfitSearch;
import fr.lordfinn.craftmaster.wardrobe.OutfitTags;
import fr.lordfinn.craftmaster.wardrobe.WardrobeNetwork;
import fr.lordfinn.craftmaster.wardrobe.WardrobeState;
import fr.lordfinn.craftmaster.wardrobe.Wardrobes;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The outfit screen of a wardrobe. Left, the whole height: the player dressed in what is under the mouse (or what
 * they wear; drag to turn), its name, the four pieces worn on the floor (click: its outfit, right-click: take it off),
 * and two square buttons in the corner: ⇄ (your own clothes ↔ the last costume) and the bin (the costume goes, your
 * clothes come back; Shift: everything worn is deleted, after a confirmation). Right: a search bar (French words
 * work), the tags, the favourites, and the outfits as cards (click: put it on, click a piece: only it, ☆: favourite).
 * The panel grows with the window; the cards fill its width and height.
 */
public class WardrobeScreen extends Screen {

    /** Margin around and between the blocks of the panel; title bar height; gap between cards; icon squares. */
    private static final int M = 6, TITLE = 18, GAP = 4, MIN_CARD_W = 92, MIN_CARD_H = 36, ICON = 18;

    private BlockPos pos;
    private boolean show, custom, admin, inCostume;
    private String wearing, lastWearing;
    private Set<String> favourites = Set.of();
    private List<Outfit> outfits, others = List.of();
    private List<ItemStack> saved = List.of(), last = List.of();

    private String query = "";
    private boolean favouritesOnly, editing, tagsOpen, confirming;
    private int scroll, tagScroll, selected = -1;
    /** Tags ticked in the tag panel: an outfit is shown if it has all of them. */
    private final Set<OutfitTags.Tag> activeTags = new LinkedHashSet<>();
    /** Turn of the preview, in degrees (drag it). */
    private float spin;

    // Layout (computed in init, from the window size)
    private int x0, y0, w, h, lx, ly, lw, lh, rx, rw, gridX, gridY, gridW, gridH, cols, rows, cardW, cardH;
    private EditBox search, nameBox, tagsBox;
    private UiButton switchButton, trashButton;

    private final List<Card> cards = new ArrayList<>();
    private Card hovered;
    private int hoveredPiece = -1, hoveredWorn = -1;
    private boolean hoveredStar;
    /** The outfit shown by a click on a worn piece, outlined until {@link #flashUntil}. */
    private int flash = -1;
    private long flashUntil;
    private RemotePlayer mannequin;
    private List<ItemStack> dressed = List.of();
    private final List<Chip> chips = new ArrayList<>();
    private Map<OutfitTags.Tag, Integer> tagCount;
    private Map<String, LinkedHashSet<OutfitTags.Tag>> tagsByCategory;

    /** A card: an outfit of the wardrobe ({@code index} ≥ 0), or a grey one its list leaves out (edit mode). */
    private record Card(int index, Outfit outfit, String key, Component name, String search, List<OutfitTags.Tag> tags, boolean grey) {}

    private record Chip(int x, int y, int w, int h, OutfitTags.Tag tag) {}

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
        lastWearing = p.lastWearing();
        favourites = new HashSet<>(p.favourites());
        outfits = p.outfits();
        others = p.others();
        saved = p.saved();
        last = p.last();
        tagCount = null;
        if (selected >= outfits.size()) selected = -1;
        if (!manage()) editing = false;
        if (minecraft != null) rebuildKeepingText();
    }

    private void rebuildKeepingText() {
        String name = nameBox == null ? null : nameBox.getValue();
        rebuildWidgets();
        if (nameBox != null && name != null) nameBox.setValue(name);
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("screen.craftmaster.wardrobe." + key, args);
    }

    /** Can change the outfits: organisers, or anyone in a storage wardrobe. */
    private boolean manage() {
        return admin || !show;
    }

    private int bw(Component label) {
        return Math.max(ICON, font.width(label) + 12);
    }

    private UiButton cell(int x, int y, int w, Component label, String tip, boolean active, Runnable onPress) {
        UiButton b = UiButton.of(x, y, w, 20, label, onPress);
        if (tip != null) b.setTooltip(Tooltip.create(t(tip)));
        b.active = active;
        return addRenderableWidget(b);
    }

    /** A square button drawing its own icon. */
    private UiButton iconButton(int x, int y, Consumer<GuiGraphicsExtractor> icon, Consumer<InputWithModifiers> onPress) {
        return addRenderableWidget(new UiButton(x, y, ICON, ICON, Component.empty(), onPress) {
            @Override
            protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
                super.extractContents(g, mouseX, mouseY, partial);
                g.pose().pushMatrix();
                g.pose().translate(getX(), getY());
                icon.accept(g);
                g.pose().popMatrix();
            }
        });
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        // The panel grows with the window (more cards, a bigger preview), with a margin around it
        w = Math.min(width - 2 * M, Math.max(420, Math.min(720, width * 9 / 10)));
        h = Math.min(height - 2 * M, Math.max(230, Math.min(420, height * 9 / 10)));
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        lw = Mth.clamp(w * 26 / 100, 104, 180);
        lx = x0 + M;
        ly = y0 + TITLE + M;
        lh = y0 + h - M - ly;
        rx = lx + lw + M;
        rw = x0 + w - M - rx;

        // Title bar: the edit mode (lit while on; click again to leave it)
        if (manage()) {
            Component edit = t("edit");
            UiButton e = UiButton.of(x0 + w - M - bw(edit), y0 + 2, bw(edit), 14, edit, () -> {
                editing = !editing;
                selected = -1;
                rebuildKeepingText();
            }).on(() -> editing);
            e.setTooltip(Tooltip.create(t("edit.tip")));
            addRenderableWidget(e);
        }

        // Preview corner: ⇄ (own clothes ↔ last costume) and the bin
        switchButton = iconButton(lx + lw - 3 - ICON, ly + 3, this::switchIcon, i -> act("switch", -1, ""));
        trashButton = iconButton(lx + lw - 3 - 2 * ICON - 2, ly + 3, this::trashIcon, i -> {
            if (i.hasShiftDown()) confirmDeleteAll();
            else act("trash", -1, "");
        });

        // Search row: the search, the tags, the favourites (18 high, like the cards' icons)
        Component fav = t("favourites");
        Component tagsLabel = activeTags.isEmpty() ? t("tags") : t("tags.n", activeTags.size());
        int favW = bw(fav), tagsW = bw(tagsLabel);
        search = new EditBox(font, rx, ly, rw - favW - tagsW - 2 * GAP, 18, t("search"));
        search.setHint(t("search.hint").copy().withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(s -> {
            query = s;
            scroll = 0;
            filter();
        });
        addRenderableWidget(search);
        UiButton tagsButton = UiButton.of(rx + rw - favW - tagsW - GAP, ly, tagsW, 18, tagsLabel, () -> {
            tagsOpen = !tagsOpen;
            tagScroll = 0;
        }).on(() -> tagsOpen || !activeTags.isEmpty());
        tagsButton.setTooltip(Tooltip.create(t("tags.tip")));
        addRenderableWidget(tagsButton);
        UiButton favButton = UiButton.of(rx + rw - favW, ly, favW, 18, fav, () -> {
            favouritesOnly = !favouritesOnly;
            scroll = 0;
            filter();
        }).on(() -> favouritesOnly);
        favButton.setTooltip(Tooltip.create(t("favourites.tip")));
        addRenderableWidget(favButton);

        // Edit rows at the bottom of the right side: a grid of three equal columns, rows of 20
        int bottom = y0 + h - M;
        int gridBottom = bottom;
        nameBox = null;
        tagsBox = null;
        if (editing) {
            int rowB = bottom - 20, rowA = rowB - 20 - GAP;
            int col = (rw - 2 * GAP) / 3;
            int c1 = rx, c2 = rx + col + GAP, c3 = rx + 2 * (col + GAP), last = rx + rw - c3;
            Outfit chosen = selected >= 0 && selected < outfits.size() ? outfits.get(selected) : null;
            nameBox = new EditBox(font, c1, rowA, col, 20, t("name"));
            nameBox.setMaxLength(Outfit.MAX_NAME);
            nameBox.setHint(t("name.hint").copy().withStyle(ChatFormatting.DARK_GRAY));
            addRenderableWidget(nameBox);
            cell(c2, rowA, col, show ? t("add") : t("store"), show ? "add.tip" : "store.tip", true, () -> act("add", -1, nameBox.getValue()));
            cell(c3, rowA, last, t("rename"), "rename.tip", chosen != null, () -> act("rename", selected, nameBox.getValue()));
            if (chosen != null) {
                if (show) cell(c1, rowB, col, t("replace"), "replace.tip", true, () -> act("replace", selected, nameBox.getValue()));
                cell(show ? c2 : c1, rowB, col, show ? t("delete") : t("take_out"), show ? "delete.tip" : "take_out.tip", true, () -> {
                    act("delete", selected, "");
                    selected = -1;
                });
                int half = (last - GAP) / 2;
                cell(c3, rowB, half, t("move_up"), "move.tip", selected > 0, () -> {
                    act("up", selected, "");
                    selected--;
                });
                cell(c3 + half + GAP, rowB, last - half - GAP, t("move_down"), "move.tip", selected < outfits.size() - 1, () -> {
                    act("down", selected, "");
                    selected++;
                });
                int rowT = rowA - 20 - GAP;
                tagsBox = new EditBox(font, c1, rowT, c3 - GAP - c1, 20, t("tags.edit"));
                tagsBox.setMaxLength(256);
                tagsBox.setHint(t("tags.edit.hint").copy().withStyle(ChatFormatting.DARK_GRAY));
                tagsBox.setValue(String.join(", ", chosen.tags()));
                addRenderableWidget(tagsBox);
                cell(c3, rowT, last, t("tags.save"), "tags.save.tip", true, () -> act("tags", selected, tagsBox.getValue()));
                gridBottom = rowT - M;
            } else {
                cell(c1, rowB, col, show ? t("type.show") : t("type.storage"), show ? "type.show.tip" : "type.storage.tip", admin,
                        () -> act("mode", -1, ""));
                if (show) cell(c2, rowB, rx + rw - c2, t("auto"), custom ? "auto.tip" : "auto.already", admin && custom, () -> act("auto", -1, ""));
                gridBottom = rowA - M;
            }
        }

        // The cards fill the grid: as many columns and rows as fit, stretched to its width and height
        gridX = rx;
        gridY = ly + 18 + M;
        gridW = rw - 6;                           // room for the scroll bar
        gridH = gridBottom - gridY;
        cols = Math.max(1, (gridW + GAP) / (MIN_CARD_W + GAP));
        rows = Math.max(1, (gridH + GAP) / (MIN_CARD_H + GAP));
        cardW = (gridW - (cols - 1) * GAP) / cols;
        cardH = (gridH - (rows - 1) * GAP) / rows;
        filter();
    }

    private void filter() {
        cards.clear();
        List<Card> favs = new ArrayList<>(), rest = new ArrayList<>();
        for (int i = 0; i < outfits.size(); i++) {
            Card c = card(i, outfits.get(i), false);
            boolean fav = favourites.contains(c.key());
            if ((favouritesOnly && !fav) || !passes(c)) continue;
            // In the edit mode the order is the wardrobe's (moving outfits); otherwise favourites first
            (fav && !editing ? favs : rest).add(c);
        }
        cards.addAll(favs);
        cards.addAll(rest);
        if (editing && !favouritesOnly) {
            for (Outfit o : others) {
                Card c = card(-1, o, true);
                if (passes(c)) cards.add(c);
            }
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private Card card(int index, Outfit o, boolean grey) {
        Component name = o.displayName();
        StringBuilder names = new StringBuilder(name.getString());
        StringBuilder words = new StringBuilder(name.getString());
        for (ItemStack s : o.pieces()) {
            if (s.isEmpty()) continue;
            names.append(' ').append(s.getHoverName().getString());
            words.append(' ').append(s.getHoverName().getString()).append(' ').append(BuiltInRegistries.ITEM.getKey(s.getItem()));
        }
        // Tags from the names only: every Selfexpression id ends in _chestplate, _helmet... (not armour, not a helmet)
        List<OutfitTags.Tag> tags = OutfitTags.of(names.toString(), o.present(), o.tags());
        for (OutfitTags.Tag tag : tags) words.append(' ').append(tag.name());
        return new Card(index, o, o.key(), name, words.toString(), tags, grey);
    }

    private boolean passes(Card c) {
        return c.tags().containsAll(activeTags) && OutfitSearch.matches(query, c.search());
    }

    private int totalRows() {
        return (cards.size() + cols - 1) / cols;
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - rows);
    }

    // ---------------------------------------------------------------- actions

    private void act(String action, int index, String text) {
        if (ClientPlayNetworking.canSend(WardrobeNetwork.Act.TYPE)) ClientPlayNetworking.send(new WardrobeNetwork.Act(pos, action, index, text));
    }

    /** Shift + bin: everything worn is deleted, your own clothes too: asked first. */
    private void confirmDeleteAll() {
        if (minecraft == null) return;
        Minecraft mc = minecraft;
        confirming = true;
        mc.gui.setScreen(new ConfirmScreen(yes -> {
            if (yes) act("trash_all", -1, "");
            mc.gui.setScreen(this);
            confirming = false;
        }, t("trash_all.title"), t("trash_all.text")));
    }

    private int cardX(int col) {
        return gridX + col * (cardW + GAP);
    }

    private int cardY(int row) {
        return gridY + row * (cardH + GAP);
    }

    private Card cardAt(double mx, double my) {
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                int i = (r + scroll) * cols + c;
                if (i < cards.size() && Ui.inside(mx, my, cardX(c), cardY(r), cardW, cardH)) return cards.get(i);
            }
        return null;
    }

    /** Where the four piece icons of a card go: spread over its width, at its bottom. */
    private int pieceX(int cardX, int p) {
        int space = (cardW - 8 - 4 * ICON) / 3;
        return cardX + 4 + p * (ICON + space);
    }

    private int pieceY(int cardY) {
        return cardY + cardH - ICON - 4;
    }

    private int[] cardPos(Card card) {
        int i = cards.indexOf(card) - scroll * cols;
        return new int[]{cardX(i % cols), cardY(i / cols)};
    }

    /** The piece (0 head … 3 feet) under the mouse on a card, -1 elsewhere on it (or an empty piece). */
    private int pieceAt(Card card, double mx, double my) {
        int[] xy = cardPos(card);
        for (int p = 0; p < 4; p++)
            if (Ui.inside(mx, my, pieceX(xy[0], p), pieceY(xy[1]), ICON, ICON)) return card.outfit().pieces().get(p).isEmpty() ? -1 : p;
        return -1;
    }

    private boolean starAt(Card card, double mx, double my) {
        int[] xy = cardPos(card);
        return !card.grey() && Ui.inside(mx, my, xy[0] + cardW - 14, xy[1] + 1, 13, 13);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (super.mouseClicked(e, doubleClick)) return true;
        if (tagsOpen && Ui.inside(e.x(), e.y(), gridX, gridY, rw, gridH)) {
            for (Chip chip : chips) {
                if (Ui.inside(e.x(), e.y(), chip.x(), chip.y(), chip.w(), chip.h())) {
                    if (chip.tag() == null) activeTags.clear();
                    else if (!activeTags.remove(chip.tag())) activeTags.add(chip.tag());
                    scroll = 0;
                    rebuildKeepingText();
                    return true;
                }
            }
            return true;
        }
        int worn = wornAt(e.x(), e.y());
        if (worn >= 0) {
            if (e.button() == InputConstants.MOUSE_BUTTON_RIGHT) act("take_off", worn, "");
            else if (e.button() == InputConstants.MOUSE_BUTTON_LEFT) showOutfitOf(worn);
            return true;
        }
        Card c = cardAt(e.x(), e.y());
        if (c == null || e.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        if (c.grey()) {
            act("restore", -1, c.key());
            return true;
        }
        if (starAt(c, e.x(), e.y())) {
            act("favourite", c.index(), "");
            return true;
        }
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
        if (Ui.inside(e.x(), e.y(), lx, ly + ICON + 6, lw, lh - 2 * ICON - 20)) {
            spin = (spin + (float) dx * 2.5f) % 360f;
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (Ui.inside(mx, my, gridX, gridY, rw, gridH)) {
            if (tagsOpen) tagScroll = Math.max(0, tagScroll - (int) Math.signum(sy) * 12);
            else scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(sy)));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void removed() {
        // The confirmation screen comes back here: the wardrobe stays open for the server (its doors too)
        if (!confirming) act("close", -1, "");
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- worn pieces

    private int wornX(int i) {
        return lx + (lw - (4 * ICON + 3 * 3)) / 2 + i * (ICON + 3);
    }

    private int wornY() {
        return ly + lh - ICON - 4;
    }

    private int wornAt(double mx, double my) {
        for (int i = 0; i < 4; i++) if (Ui.inside(mx, my, wornX(i), wornY(), ICON, ICON)) return i;
        return -1;
    }

    /** Scrolls to the outfit a worn piece comes from (the one worn whole first) and makes it flash. */
    private void showOutfitOf(int piece) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack worn = Wardrobes.unmark(mc.player.getItemBySlot(Outfit.SLOTS[piece]));
        if (worn.isEmpty()) return;
        int found = -1;
        for (int i = 0; i < outfits.size() && found < 0; i++)
            if (outfits.get(i).key().equals(wearing) && ItemStack.isSameItemSameComponents(outfits.get(i).pieces().get(piece), worn)) found = i;
        for (int i = 0; i < outfits.size() && found < 0; i++)
            if (ItemStack.isSameItemSameComponents(Wardrobes.unmark(outfits.get(i).pieces().get(piece)), worn)) found = i;
        if (found < 0) {
            mc.player.sendOverlayMessage(t("not_here", worn.getHoverName()).copy().withStyle(ChatFormatting.GOLD));
            return;
        }
        int target = found;
        if (cards.stream().noneMatch(c -> c.index() == target)) {
            // Hidden by the search, the tags or the favourites: show everything again
            query = "";
            favouritesOnly = false;
            activeTags.clear();
            rebuildKeepingText();
        }
        tagsOpen = false;
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).index() == target) {
                scroll = Math.max(0, Math.min(maxScroll(), i / cols - rows / 2));
                break;
            }
        }
        flash = target;
        flashUntil = System.currentTimeMillis() + 2000;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0x60000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        Ui.panel(g, font, x0, y0, w, h, null);
        g.fill(x0, y0, x0 + w, y0 + TITLE, Theme.VELVET);
        g.fill(x0, y0 + TITLE - 3, x0 + w, y0 + TITLE, Theme.VELVET_DARK);
        g.horizontalLine(x0 + 1, x0 + w - 2, y0 + TITLE, Theme.BORDER);
        g.outline(x0, y0, w, h, Theme.BORDER);
        Component title = this.title.copy().withStyle(ChatFormatting.BOLD);
        g.text(font, title, x0 + M + 2, y0 + 5, Theme.TITLE);
        Component count = cards.size() == 1 ? t("count.one") : t("count", cards.size());
        g.text(font, count, x0 + M + 6 + font.width(title), y0 + 5, Theme.TEXT_DIM, false);

        hovered = tagsOpen ? null : cardAt(mouseX, mouseY);
        hoveredPiece = hovered == null || editing ? -1 : pieceAt(hovered, mouseX, mouseY);
        hoveredStar = hovered != null && starAt(hovered, mouseX, mouseY);
        hoveredWorn = wornAt(mouseX, mouseY);
        extractPreview(g, mouseX, mouseY);
        if (tagsOpen) extractTags(g, mouseX, mouseY);
        else extractGrid(g);
        if (cards.isEmpty() && !tagsOpen) {
            Component empty = favouritesOnly && query.isBlank() ? t("empty.favourites")
                    : query.isBlank() ? t(show ? "empty.show" : "empty.storage") : t("no_match", query);
            g.centeredText(font, empty, gridX + gridW / 2, gridY + gridH / 2 - 4, Theme.TEXT_DIM);
        }
        super.extractRenderState(g, mouseX, mouseY, partial);

        if (hovered != null) g.setTooltipForNextFrame(tooltip(hovered), mouseX, mouseY);
        else if (hoveredWorn >= 0 && minecraft != null && minecraft.player != null) {
            ItemStack s = minecraft.player.getItemBySlot(Outfit.SLOTS[hoveredWorn]);
            if (!s.isEmpty()) g.setTooltipForNextFrame(List.of(s.getHoverName().copy().withStyle(ChatFormatting.GOLD).getVisualOrderText(),
                    t("tip.worn").copy().withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText()), mouseX, mouseY);
        } else if (switchButton.isHovered()) {
            g.setTooltipForNextFrame(switchTooltip(), mouseX, mouseY);
        } else if (trashButton.isHovered()) {
            g.setTooltipForNextFrame(List.of(t("trash").copy().withStyle(ChatFormatting.GOLD).getVisualOrderText(),
                    t("trash.tip").copy().withStyle(ChatFormatting.GRAY).getVisualOrderText(),
                    t("trash.shift").copy().withStyle(ChatFormatting.RED).getVisualOrderText()), mouseX, mouseY);
        }
    }

    /** What the switch button will put on: your own clothes (in a costume) or the last costume. */
    private List<FormattedCharSequence> switchTooltip() {
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<ItemStack> pieces;
        if (inCostume) {
            lines.add(t("switch.own").copy().withStyle(ChatFormatting.GOLD).getVisualOrderText());
            pieces = saved;
        } else if (last.stream().anyMatch(s -> !s.isEmpty())) {
            Component name = lastWearing.equals(WardrobeState.MIX) || lastWearing.isEmpty() ? t("mix") : nameOf(lastWearing);
            lines.add(t("switch.last", name).copy().withStyle(ChatFormatting.GOLD).getVisualOrderText());
            pieces = last;
        } else {
            lines.add(t("switch.none").copy().withStyle(ChatFormatting.GRAY).getVisualOrderText());
            return lines;
        }
        String[] parts = {"head", "body", "legs", "feet"};
        for (int i = 0; i < 4; i++) {
            ItemStack s = i < pieces.size() ? pieces.get(i) : ItemStack.EMPTY;
            if (!s.isEmpty()) lines.add(t("piece." + parts[i], s.getHoverName()).copy().withStyle(ChatFormatting.GRAY).getVisualOrderText());
        }
        if (pieces.stream().allMatch(ItemStack::isEmpty)) lines.add(t("switch.nothing").copy().withStyle(ChatFormatting.GRAY).getVisualOrderText());
        lines.add(t("switch.tip").copy().withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        return lines;
    }

    private Component nameOf(String key) {
        for (Outfit o : outfits) if (o.key().equals(key)) return o.displayName();
        return t("mix");
    }

    private List<FormattedCharSequence> tooltip(Card c) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        boolean fav = favourites.contains(c.key());
        lines.add(c.name().copy().withStyle(ChatFormatting.GOLD).getVisualOrderText());
        String[] parts = {"head", "body", "legs", "feet"};
        for (int i = 0; i < 4; i++) {
            ItemStack s = c.outfit().pieces().get(i);
            ChatFormatting style = i == hoveredPiece ? ChatFormatting.WHITE : ChatFormatting.GRAY;
            lines.add(t("piece." + parts[i], s.isEmpty() ? t("nothing") : s.getHoverName()).copy().withStyle(style).getVisualOrderText());
        }
        if (!c.tags().isEmpty()) {
            String tags = String.join(", ", c.tags().stream().map(OutfitTags.Tag::name).toList());
            lines.addAll(font.split(t("tip.tags", tags).copy().withStyle(ChatFormatting.DARK_AQUA), 220));
        }
        Component action = c.grey() ? t("tip.restore")
                : hoveredStar ? (fav ? t("tip.unfavourite") : t("tip.favourite"))
                : editing ? t("tip.choose")
                : hoveredPiece >= 0 ? t("tip.wear_piece", c.outfit().pieces().get(hoveredPiece).getHoverName()) : t("tip.wear");
        lines.add(action.copy().withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        return lines;
    }

    private void extractGrid(GuiGraphicsExtractor g) {
        g.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i = (r + scroll) * cols + c;
                if (i >= cards.size()) break;
                extractCard(g, cards.get(i), cardX(c), cardY(r));
            }
        }
        g.disableScissor();
        if (maxScroll() > 0) {
            int sx = gridX + gridW + 3;
            int bh = Math.max(12, gridH * rows / totalRows());
            int by = gridY + (gridH - bh) * scroll / maxScroll();
            g.fill(sx, gridY, sx + 3, gridY + gridH, 0x40000000);
            g.fill(sx, by, sx + 3, by + bh, Theme.BORDER);
        }
    }

    private void extractCard(GuiGraphicsExtractor g, Card card, int x, int y) {
        boolean worn = !card.grey() && !wearing.isEmpty() && card.key().equals(wearing);
        boolean fav = favourites.contains(card.key());
        g.fill(x, y, x + cardW, y + cardH, Theme.shade(Theme.VELVET, -0.35f));
        if (card == hovered && hoveredPiece < 0 && !hoveredStar) g.fill(x, y, x + cardW, y + cardH, Theme.HOVER);
        if (card.index() == flash && card.index() >= 0 && System.currentTimeMillis() < flashUntil) {
            boolean blink = (System.currentTimeMillis() / 250) % 2 == 0;
            g.fill(x, y, x + cardW, y + cardH, blink ? 0x50FFC23D : 0x20FFC23D);
            g.outline(x, y, cardW, cardH, Theme.GOLD);
        } else if (editing && card.index() >= 0 && card.index() == selected) {
            g.fill(x, y, x + cardW, y + cardH, Theme.SELECTED);
            g.outline(x, y, cardW, cardH, Theme.GOLD);
        } else {
            g.outline(x, y, cardW, cardH, worn ? Theme.OK : Theme.BORDER_DARK);
        }
        String name = font.plainSubstrByWidth(card.name().getString(), cardW - 8 - (card.grey() ? 0 : 12));
        g.text(font, name, x + 4, y + 4, card.grey() ? Theme.TEXT_DIM : worn ? Theme.OK : Theme.TEXT, false);
        if (!card.grey()) {
            // The star: empty (☆) or full (★), click to switch
            boolean on = card == hovered && hoveredStar;
            g.text(font, fav ? "★" : "☆", x + cardW - 11, y + 4, fav ? Theme.GOLD : on ? Theme.TITLE : Theme.TEXT_DIM, false);
        }
        for (int p = 0; p < 4; p++) {
            ItemStack s = card.outfit().pieces().get(p);
            int ix = pieceX(x, p), iy = pieceY(y);
            boolean on = card == hovered && p == hoveredPiece;
            g.fill(ix, iy, ix + ICON, iy + ICON, on ? 0x60FFC23D : 0x30000000);
            if (on) g.outline(ix, iy, ICON, ICON, Theme.GOLD);
            if (!s.isEmpty()) g.fakeItem(s, ix + 1, iy + 1);
        }
        if (card.grey()) g.fill(x + 1, y + 1, x + cardW - 1, y + cardH - 1, 0x99201A1C);
    }

    /** The tag panel, in place of the cards: each category and its tags (how many outfits), click to tick. */
    private void extractTags(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        chips.clear();
        if (tagCount == null) {
            tagCount = new HashMap<>();
            tagsByCategory = new LinkedHashMap<>();
            for (String c : OutfitTags.CATEGORIES.keySet()) tagsByCategory.put(c, new LinkedHashSet<>());
            tagsByCategory.put(OutfitTags.BODY, new LinkedHashSet<>());
            tagsByCategory.put(OutfitTags.CUSTOM, new LinkedHashSet<>());
            for (int i = 0; i < outfits.size(); i++) {
                for (OutfitTags.Tag tag : card(i, outfits.get(i), false).tags()) {
                    tagCount.merge(tag, 1, Integer::sum);
                    tagsByCategory.computeIfAbsent(tag.category(), k -> new LinkedHashSet<>()).add(tag);
                }
            }
        }
        int right = gridX + rw;
        g.fill(gridX, gridY, right, gridY + gridH, 0x40000000);
        g.enableScissor(gridX, gridY, right, gridY + gridH);
        int x, y = gridY + 4 - tagScroll;
        if (!activeTags.isEmpty()) {
            Component clear = t("tags.clear");
            chip(g, gridX + 4, y, font.width(clear) + 8, clear, null, false, mouseX, mouseY);
            y += 16;
        }
        for (var e : tagsByCategory.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            List<OutfitTags.Tag> tags = new ArrayList<>(e.getValue());
            if (OutfitTags.CATEGORIES.containsKey(e.getKey())) {
                List<String> order = new ArrayList<>(OutfitTags.CATEGORIES.get(e.getKey()).keySet());
                tags.sort(Comparator.comparingInt(tag -> order.indexOf(tag.name())));
            }
            g.text(font, e.getKey(), gridX + 4, y + 2, Theme.GOLD, false);
            y += 12;
            x = gridX + 4;
            for (OutfitTags.Tag tag : tags) {
                Component label = Component.literal(tag.name() + " " + tagCount.getOrDefault(tag, 0));
                int cw = font.width(label) + 8;
                if (x + cw > right - 4) {
                    x = gridX + 4;
                    y += 14;
                }
                chip(g, x, y, cw, label, tag, activeTags.contains(tag), mouseX, mouseY);
                x += cw + 3;
            }
            y += 18;
        }
        g.disableScissor();
        tagScroll = Math.max(0, Math.min(tagScroll, y + tagScroll - gridY - gridH));
    }

    private void chip(GuiGraphicsExtractor g, int x, int y, int w, Component label, OutfitTags.Tag tag, boolean on, int mouseX, int mouseY) {
        boolean hover = Ui.inside(mouseX, mouseY, x, y, w, 12) && Ui.inside(mouseX, mouseY, gridX, gridY, rw, gridH);
        int face = on ? Theme.BUTTON_ON : hover ? Theme.BUTTON_HOVER : Theme.BUTTON;
        g.fill(x, y, x + w, y + 12, face);
        g.outline(x, y, w, 12, on ? Theme.GOLD : Theme.BORDER_DARK);
        g.text(font, label, x + 4, y + 2, on ? Theme.TITLE : Theme.TEXT, false);
        if (y + 12 > gridY && y < gridY + gridH) chips.add(new Chip(x, Math.max(y, gridY), w, Math.min(y + 12, gridY + gridH) - Math.max(y, gridY), tag));
    }

    private void extractPreview(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Ui.paper(g, lx, ly, lw, lh);
        int floor = ly + lh - ICON - 8;                     // the floor strip: the pieces worn
        g.fill(lx + 1, floor, lx + lw - 1, ly + lh - 1, Theme.shade(Theme.PAPER, -0.12f));
        g.horizontalLine(lx + 1, lx + lw - 2, floor, Theme.shade(Theme.PAPER, -0.22f));
        if (mc.player == null || mc.level == null) return;
        // What the player wears now, with the outfit (or the single piece) under the mouse
        List<ItemStack> look = new ArrayList<>();
        for (EquipmentSlot s : Outfit.SLOTS) look.add(mc.player.getItemBySlot(s));
        Component label;
        if (hovered != null && !editing && hoveredPiece >= 0) {
            look.set(hoveredPiece, hovered.outfit().pieces().get(hoveredPiece));
            label = hovered.outfit().pieces().get(hoveredPiece).getHoverName();
        } else if (hovered != null && !hoveredStar) {
            look = new ArrayList<>(hovered.outfit().pieces());
            label = hovered.name();
        } else {
            label = wearing.isEmpty() ? t("own_clothes") : wearing.equals(WardrobeState.MIX) ? t("mix") : nameOf(wearing);
        }
        RemotePlayer m = mannequin(mc);
        if (!same(look, dressed)) {
            for (int i = 0; i < 4; i++) m.setItemSlot(Outfit.SLOTS[i], look.get(i).copy());
            dressed = look;
        }
        // The player as big as the frame allows (on screen a player is about 2 blocks high and 1 wide)
        int top = ly + ICON + 8, bottom = floor - 14;
        int scale = Math.max(20, Math.min((bottom - top) * 45 / 100, (lw - 12) * 75 / 100));
        extractMannequin(g, lx + 2, top, lx + lw - 2, bottom, scale, 0.0625f, mouseX, mouseY, m);
        // Its name, dark ink on the paper, no shadow
        String s = font.plainSubstrByWidth(label.getString(), lw - 8);
        g.text(font, s, lx + (lw - font.width(s)) / 2, floor - 11, Theme.INK, false);
        for (int i = 0; i < 4; i++) {
            int sx = wornX(i), sy = wornY();
            ItemStack piece = mc.player.getItemBySlot(Outfit.SLOTS[i]);
            boolean on = hoveredWorn == i;
            g.fill(sx, sy, sx + ICON, sy + ICON, on ? 0x60FFC23D : Theme.shade(Theme.PAPER, -0.28f));
            g.outline(sx, sy, ICON, ICON, on ? Theme.GOLD : Theme.PAPER_EDGE);
            if (!piece.isEmpty()) g.fakeItem(piece, sx + 1, sy + 1);
        }
    }

    /** ⇄ drawn in pixels: an arrow to the right above an arrow to the left. */
    private void switchIcon(GuiGraphicsExtractor g) {
        int c = Theme.TEXT;
        g.fill(4, 6, 13, 7, c);
        g.fill(11, 4, 12, 9, c);
        g.fill(12, 5, 13, 8, c);
        g.fill(5, 11, 14, 12, c);
        g.fill(6, 9, 7, 14, c);
        g.fill(5, 10, 6, 13, c);
    }

    /** A bin drawn in pixels: lid, handle, body with ribs. */
    private void trashIcon(GuiGraphicsExtractor g) {
        int c = Theme.TEXT, d = Theme.shade(Theme.TEXT, -0.35f);
        g.fill(7, 3, 11, 4, c);
        g.fill(4, 4, 14, 6, c);
        g.fill(5, 6, 13, 15, c);
        g.fill(7, 7, 8, 14, d);
        g.fill(10, 7, 11, 14, d);
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
