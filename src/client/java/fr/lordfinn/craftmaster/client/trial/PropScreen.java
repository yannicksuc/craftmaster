package fr.lordfinn.craftmaster.client.trial;

import fr.lordfinn.craftmaster.trial.PropConfig;
import fr.lordfinn.craftmaster.trial.TrialNetwork;
import fr.lordfinn.craftmaster.trial.TrialPropEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Settings of a task prop (organisers): three tabs — its look, what it does when used, and who may use it with
 * what it remembers (first to touch it, uses per player). The world stays visible on the right, the prop
 * changes as soon as it is saved; "Test" uses it as a contestant would.
 */
public class PropScreen extends Screen {

    private static final int PANEL = 0xE0161012, BORDER = 0xFFD4A53A, VELVET = 0xFF6B1420, LABEL = 0xFFE8DCC0, MUTED = 0xFF9C8F78;
    private static final int W = 286, ROW = 22;

    /** Sounds offered (id, label key); any other id typed in the command stays as is. */
    private static final String[][] SOUNDS = {
            {"", "none"}, {"minecraft:block.note_block.bell", "bell"}, {"minecraft:block.note_block.chime", "chime"},
            {"minecraft:block.note_block.pling", "pling"}, {"minecraft:entity.player.levelup", "levelup"},
            {"minecraft:block.bell.use", "gong"}, {"minecraft:block.note_block.bass", "buzz"},
            {"minecraft:entity.villager.yes", "yes"}, {"minecraft:entity.villager.no", "no"},
            {"minecraft:block.anvil.land", "anvil"}, {"minecraft:entity.firework_rocket.blast", "firework"},
            {"minecraft:block.chest.open", "chest"}, {"minecraft:entity.cat.ambient", "cat"},
            {"minecraft:entity.experience_orb.pickup", "orb"}, {"minecraft:block.wooden_door.open", "door"}};

    private enum Tab { LOOK, EFFECTS, ACTIONS, RULES }

    private final TrialPropEntity prop;
    private PropConfig c;
    private List<String> stats;
    private String give;
    private Tab tab = Tab.LOOK;
    private int left, top, h;
    private Component error;
    /** Reads the widgets of the current tab back into {@link #c}. */
    private final List<Runnable> readers = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();

    private record Label(Component text, int x, int y) {}

    public PropScreen(TrialPropEntity prop, PropConfig config, List<String> stats, String give) {
        super(Component.translatable("screen.craftmaster.prop"));
        this.prop = prop;
        this.c = config;
        this.stats = stats;
        this.give = give;
    }

    public TrialPropEntity prop() {
        return prop;
    }

    /** The server answered a button (look from hand...): new settings and memory, same tab. */
    public void update(PropConfig config, List<String> stats, String give) {
        this.c = config;
        this.stats = stats;
        this.give = give;
        readers.clear();
        rebuildWidgets();
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("screen.craftmaster.prop." + key, args);
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void rebuildWidgets() {
        read();
        super.rebuildWidgets();
    }

    private void read() {
        error = null;
        for (Runnable r : readers) {
            try {
                r.run();
            } catch (NumberFormatException e) {
                error = t("bad_number");
            }
        }
        c.sanitize();
    }

    @Override
    protected void init() {
        readers.clear();
        labels.clear();
        h = Math.min(height - 8, 272);
        left = 8;
        top = Math.max(4, (height - h) / 2);
        int tx = left + 8;
        for (Tab tb : Tab.values()) {
            Button b = addRenderableWidget(Button.builder(t("tab." + tb.name().toLowerCase(Locale.ROOT)), x -> {
                read();
                tab = tb;
                readers.clear();
                super.rebuildWidgets();
            }).bounds(tx, top + 20, 66, 18).build());
            b.active = tb != tab;
            tx += 68;
        }
        int y = top + 44;
        switch (tab) {
            case LOOK -> look(y);
            case EFFECTS -> effects(y);
            case ACTIONS -> actions(y);
            case RULES -> rules(y);
        }
        int by = top + h - 26;
        addRenderableWidget(Button.builder(t("save"), b -> save()).bounds(left + 8, by, 88, 20).build());
        addRenderableWidget(Button.builder(t("test"), b -> action("test")).tooltip(Tooltip.create(t("test.tip")))
                .bounds(left + 99, by, 88, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(left + 190, by, 88, 20).build());
    }

    private void label(String key, int y) {
        labels.add(new Label(t(key), left + 10, y + 6));
    }

    private EditBox box(int x, int y, int w, String value, int max, String tipKey, Consumer<String> reader) {
        EditBox b = new EditBox(font, x, y, w, 20, Component.empty());
        b.setMaxLength(max);
        b.setValue(value);
        if (tipKey != null) b.setTooltip(Tooltip.create(t(tipKey)));
        addRenderableWidget(b);
        readers.add(() -> reader.accept(b.getValue().trim()));
        return b;
    }

    private CycleButton<Boolean> toggle(int x, int y, int w, String key, boolean value, Consumer<Boolean> reader) {
        CycleButton<Boolean> b = CycleButton.onOffBuilder(value).create(x, y, w, 20, t(key));
        b.setTooltip(Tooltip.create(t(key + ".tip")));
        addRenderableWidget(b);
        readers.add(() -> reader.accept(b.getValue()));
        return b;
    }

    private static float f(String s) {
        return Float.parseFloat(s.replace(',', '.'));
    }

    private static double d(String s) {
        return Double.parseDouble(s.replace(',', '.'));
    }

    private static String num(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void look(int y) {
        int x = left + 96;
        label("name", y);
        box(x, y, 182, c.name, 64, "name.tip", v -> c.name = v);
        y += ROW;
        toggle(left + 8, y, 132, "show_name", c.showName, v -> c.showName = v);
        toggle(left + 146, y, 132, "glow", c.glow, v -> c.glow = v);
        y += ROW;
        label("scale", y);
        box(x, y, 50, num(c.scale), 8, "scale.tip", v -> c.scale = f(v));
        y += ROW;
        label("rotation", y);
        box(x, y, 50, num(c.yaw), 8, "rotation.tip", v -> c.yaw = f(v));
        addRenderableWidget(Button.builder(Component.literal("⟲ 45°"), b -> {
            read();
            c.yaw = (c.yaw + 45) % 360;
            readers.clear();
            super.rebuildWidgets();
        }).bounds(x + 54, y, 48, 20).build());
        y += ROW;
        label("pitch", y);
        box(x, y, 50, num(c.pitch), 8, "pitch.tip", v -> c.pitch = f(v));
        y += ROW;
        toggle(left + 8, y, 132, "spin", c.spin, v -> c.spin = v);
        toggle(left + 146, y, 132, "bob", c.bob, v -> c.bob = v);
        y += ROW + 6;
        addRenderableWidget(Button.builder(t("look_hand"), b -> action("look_hand")).tooltip(Tooltip.create(t("look_hand.tip")))
                .bounds(left + 8, y, 270, 20).build());
        y += ROW;
        labels.add(new Label(t("look_now", prop.look().getHoverName()).copy().withStyle(ChatFormatting.GRAY), left + 10, y + 6));
    }

    private void effects(int y) {
        int x = left + 96;
        label("message", y);
        box(x, y, 182, c.message, 256, "message.tip", v -> c.message = v);
        y += ROW;
        toggle(x, y, 182, "message_all", c.messageToAll, v -> c.messageToAll = v);
        y += ROW;
        label("sound", y);
        List<String> ids = new ArrayList<>();
        for (String[] s : SOUNDS) ids.add(s[0]);
        if (!ids.contains(c.sound)) ids.add(c.sound);
        CycleButton<String> sound = CycleButton.<String>builder(id -> {
            for (String[] s : SOUNDS) if (s[0].equals(id)) return t("sound." + s[1]);
            return Component.literal(id);
        }, c.sound).withValues(ids).displayOnlyValue().create(x, y, 128, 20, t("sound"));
        addRenderableWidget(sound);
        readers.add(() -> c.sound = sound.getValue());
        box(x + 132, y, 50, num(c.soundPitch), 6, "pitch_sound.tip", v -> c.soundPitch = f(v));
        y += ROW;
        label("give", y);
        box(x, y, 104, c.giveItem, 128, "give.tip", v -> c.giveItem = v);
        box(x + 108, y, 28, String.valueOf(c.giveCount), 4, "give_count.tip", v -> c.giveCount = Integer.parseInt(v.isEmpty() ? "1" : v));
        addRenderableWidget(Button.builder(t("hand"), b -> action("give_hand")).tooltip(Tooltip.create(t("give_hand.tip")))
                .bounds(x + 140, y, 42, 20).build());
        y += ROW;
        label("note", y);
        box(x, y, 182, c.noteTitle, 64, "note_title.tip", v -> c.noteTitle = v);
        y += ROW;
        MultiLineEditBox note = MultiLineEditBox.builder().setX(x).setY(y).setPlaceholder(t("note_text.placeholder"))
                .build(font, 182, 38, t("note"));
        note.setCharacterLimit(2000);
        note.setValue(c.noteText);
        addRenderableWidget(note);
        readers.add(() -> c.noteText = note.getValue());
    }

    private void actions(int y) {
        int x = left + 96;
        toggle(left + 8, y, 84, "teleport", c.teleport, v -> c.teleport = v);
        box(x, y, 42, num(c.tpX), 12, "tp.tip", v -> c.tpX = d(v));
        box(x + 45, y, 42, num(c.tpY), 12, "tp.tip", v -> c.tpY = d(v));
        box(x + 90, y, 42, num(c.tpZ), 12, "tp.tip", v -> c.tpZ = d(v));
        addRenderableWidget(Button.builder(t("here"), b -> action("tp_here")).tooltip(Tooltip.create(t("here.tip")))
                .bounds(x + 136, y, 46, 20).build());
        y += ROW;
        toggle(left + 8, y, 84, "pulse", c.pulse, v -> c.pulse = v);
        box(x, y, 30, String.valueOf(c.pulseDx), 3, "pulse_offset.tip", v -> c.pulseDx = Integer.parseInt(v));
        box(x + 33, y, 30, String.valueOf(c.pulseDy), 3, "pulse_offset.tip", v -> c.pulseDy = Integer.parseInt(v));
        box(x + 66, y, 30, String.valueOf(c.pulseDz), 3, "pulse_offset.tip", v -> c.pulseDz = Integer.parseInt(v));
        box(x + 100, y, 34, String.valueOf(c.pulseTicks), 3, "pulse_ticks.tip", v -> c.pulseTicks = Integer.parseInt(v));
        y += ROW;
        toggle(left + 8, y, 132, "start_attempt", c.startAttempt, v -> c.startAttempt = v);
        toggle(left + 146, y, 132, "end_attempt", c.endAttempt, v -> c.endAttempt = v);
    }

    private void rules(int y) {
        toggle(left + 8, y, 132, "once", c.oncePerPlayer, v -> c.oncePerPlayer = v);
        toggle(left + 146, y, 132, "contestants_only", c.contestantsOnly, v -> c.contestantsOnly = v);
        y += ROW;
        label("cooldown", y);
        box(left + 96, y, 50, num(c.cooldown), 8, "cooldown.tip", v -> c.cooldown = f(v));
        toggle(left + 150, y, 128, "announce_first", c.announceFirst, v -> c.announceFirst = v);
        y += ROW + 4;
        labels.add(new Label(t("memory").copy().withStyle(ChatFormatting.GOLD), left + 10, y + 2));
        y += 14;
        int shown = 0;
        for (String s : stats) {
            if (y + shown * 11 > top + h - 74) break;
            labels.add(new Label(Component.literal(s).withStyle(shown == 0 ? ChatFormatting.WHITE : ChatFormatting.GRAY), left + 12, y + shown * 11));
            shown++;
        }
        int by = top + h - 62;
        addRenderableWidget(Button.builder(t("reset_stats"), b -> action("reset_stats")).bounds(left + 8, by, 132, 20).build());
        Button remove = addRenderableWidget(Button.builder(t("remove").copy().withStyle(ChatFormatting.RED), b -> action("remove"))
                .tooltip(Tooltip.create(t("remove.tip"))).bounds(left + 146, by, 132, 20).build());
        remove.active = true;
    }

    // ---------------------------------------------------------------- actions

    private void save() {
        read();
        if (error != null) return;
        ClientPlayNetworking.send(new TrialNetwork.SaveProp(prop.getId(), c.toJson()));
        onClose();
    }

    private void action(String what) {
        read();
        if (error != null) return;
        ClientPlayNetworking.send(new TrialNetwork.PropAction(prop.getId(), what, c.toJson()));
        if (what.equals("remove") || what.equals("test")) onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** No blur: the prop stays visible on the right. */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(left, top, left + W, top + h, PANEL);
        g.fill(left, top, left + W, top + 16, VELVET);
        g.outline(left, top, W, h, BORDER);
        g.horizontalLine(left + 1, left + W - 2, top + 16, BORDER);
        g.text(font, Component.translatable("screen.craftmaster.prop").withStyle(ChatFormatting.BOLD), left + 8, top + 4, 0xFFFFE9A8);
        String name = prop.displayName();
        g.text(font, Component.literal(name).withStyle(ChatFormatting.ITALIC), left + W - 8 - font.width(name), top + 4, 0xFFE8D3A0);
        for (Label l : labels) g.text(font, l.text(), l.x(), l.y(), LABEL);
        Component foot = error != null ? error.copy().withStyle(ChatFormatting.RED)
                : tab == Tab.EFFECTS && !give.isEmpty() ? t("give_kept", give).copy().withStyle(ChatFormatting.DARK_GRAY)
                : t("hint").copy().withStyle(ChatFormatting.DARK_GRAY);
        g.text(font, foot, left + 8, top + h - 38, MUTED);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }
}
