package fr.lordfinn.craftmaster.client.media;

import fr.lordfinn.craftmaster.media.ChatWall;
import fr.lordfinn.craftmaster.media.MediaNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The game's side of the chat wall ({@link ChatWall}): forwards the Twitch chat read by the broadcast game
 * (client/task/TwitchChat) to the server, keeps the messages the server sends, and lays them out on a studio screen
 * (ImageScreenRenderer draws what {@link #layout} returns). Nothing is decided here: the server keeps the history.
 */
public final class ChatWallClient {

    private ChatWallClient() {}

    /** Scroll animation of a new message (ms). */
    private static final long SCROLL_MS = 350;

    /** A message received, with when it arrived (0: from a history, no animation) and its last wrap. */
    private static final class Entry {
        final ChatWall.Line line;
        final long arrivedAt;
        int wrapWidth = -1;
        List<FormattedCharSequence> wrapped = List.of();

        Entry(ChatWall.Line line, long arrivedAt) {
            this.line = line;
            this.arrivedAt = arrivedAt;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(MediaNetwork.ChatLines.TYPE, (p, ctx) -> receive(p));
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> ENTRIES.clear());
    }

    private static void receive(MediaNetwork.ChatLines p) {
        long now = System.currentTimeMillis();
        if (p.reset()) {
            // A history: what was already there does not scroll in again
            List<String> had = ENTRIES.stream().map(e -> e.line.id()).toList();
            ENTRIES.clear();
            for (ChatWall.Line l : p.lines()) ENTRIES.add(new Entry(l, had.contains(l.id()) || had.isEmpty() ? 0 : now));
        } else {
            for (ChatWall.Line l : p.lines()) {
                if (ENTRIES.stream().anyMatch(e -> e.line.id().equals(l.id()))) continue;
                ENTRIES.add(new Entry(l, now));
            }
        }
        // Twice the server's history at most (two sources)
        while (ENTRIES.size() > 2 * ChatWall.HISTORY) ENTRIES.removeFirst();
    }

    // ---------------------------------------------------------------- forwarding the Twitch chat (broadcast game)

    /** A Twitch chat message read by TwitchChat (reader thread): sent to the server, which keeps it if we organise. */
    public static void forwardTwitch(String rawLine, String user, String text) {
        if (text == null || text.strip().startsWith("!")) return;
        Map<String, String> tags = ChatWall.tags(rawLine);
        String name = tags.getOrDefault("display-name", "");
        if (name.isBlank()) name = user;
        String id = tags.getOrDefault("id", "");
        if (id.isBlank()) id = Integer.toHexString((user + "|" + text + "|" + System.currentTimeMillis()).hashCode());
        send(new MediaNetwork.TwitchChatIn(MediaNetwork.TwitchChatIn.MESSAGE, cut(id, 64), cut(name, 64),
                ChatWall.parseColor(tags.get("color")), cut(text, 600)));
    }

    /** A line that is not a message: a deleted message, a ban or a cleared chat removes lines from the wall. */
    public static void forwardModeration(String rawLine) {
        ChatWall.Moderation m = ChatWall.moderation(rawLine);
        if (m == null) return;
        if (m.clearAll()) send(new MediaNetwork.TwitchChatIn(MediaNetwork.TwitchChatIn.CLEAR, "", "", -1, ""));
        else if (!m.id().isEmpty()) send(new MediaNetwork.TwitchChatIn(MediaNetwork.TwitchChatIn.DELETE, cut(m.id(), 64), "", -1, ""));
        else send(new MediaNetwork.TwitchChatIn(MediaNetwork.TwitchChatIn.BAN, "", cut(m.user(), 64), -1, ""));
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static void send(MediaNetwork.TwitchChatIn p) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(MediaNetwork.TwitchChatIn.TYPE))
                ClientPlayNetworking.send(p);
        });
    }

    // ---------------------------------------------------------------- layout

    /** A coloured rectangle, in the wall's pixels (y down). */
    public record Rect(float x0, float y0, float x1, float y1, int argb) {}

    /** A line of text at (x, y), in the wall's pixels, with its alpha (0..255). */
    public record Text(float x, float y, FormattedCharSequence text, int alpha) {}

    /**
     * What to draw on a chat wall of the screen's inner size (blocks): {@code scale} blocks per pixel, the panel's
     * size in pixels, rectangles (background, title band, source marks) and text lines.
     */
    public record Layout(float scale, float width, float height, List<Rect> rects, List<Text> texts) {}

    private static final int LINE_H = 10, HEADER_H = 14, PAD = 4, MIN_TEXT_W = 170;
    private static final int TWITCH = 0x9146FF, JEU = 0x55C24A, GOLD = 0xF2B33D;

    /** Lines that fit: about 8 on a small screen, 14 on a tall one. */
    static int targetLines(int heightBlocks) {
        return Math.max(8, Math.min(14, Math.round(6.5f + 0.9f * heightBlocks)));
    }

    public static Layout layout(ChatWall.Mode mode, float innerW, float innerH, int heightBlocks, Font font, long now) {
        int lines = targetLines(heightBlocks);
        float scale = innerH / (HEADER_H + 2 * PAD + lines * LINE_H);
        scale = Math.min(scale, innerW / (MIN_TEXT_W + 2 * PAD));
        float w = innerW / scale, h = innerH / scale;
        List<Rect> rects = new ArrayList<>();
        List<Text> texts = new ArrayList<>();
        int accent = switch (mode) {
            case TWITCH -> TWITCH;
            case JEU -> JEU;
            case TOUT -> GOLD;
        };
        // Panel, title band, a thin accent line under it
        rects.add(new Rect(0, 0, w, h, 0xF00E0F16));
        rects.add(new Rect(0, 0, w, HEADER_H, 0xFF1A1B26));
        rects.add(new Rect(0, HEADER_H - 1, w, HEADER_H, 0xFF000000 | accent));
        rects.add(new Rect(PAD, 3, PAD + 3, HEADER_H - 4, 0xFF000000 | accent));
        String title = switch (mode) {
            case TWITCH -> "CHAT TWITCH";
            case JEU -> "CHAT DU JEU";
            case TOUT -> "LE CHAT";
        };
        texts.add(new Text(PAD + 6, 3, Component.literal(title).withStyle(ChatFormatting.BOLD).withColor(0xFFFFFF).getVisualOrderText(), 255));

        boolean marks = mode == ChatWall.Mode.TOUT;
        float textX = PAD + (marks ? 5 : 0);
        int textW = (int) Math.max(20, w - textX - PAD);
        float top = HEADER_H + PAD, bottom = h - PAD;

        // Lines from the newest up, as long as they may show; new messages slide in from below
        List<Entry> shown = ENTRIES.stream().filter(e -> mode.shows(e.line.source())).toList();
        if (shown.isEmpty()) {
            String wait = mode == ChatWall.Mode.JEU ? "En attente des messages du jeu…" : "En attente des messages du chat…";
            int tw = font.width(wait);
            texts.add(new Text((w - tw) / 2f, (top + bottom) / 2f - LINE_H / 2f,
                    Component.literal(wait).withColor(0x8A8FA3).getVisualOrderText(), 255));
            return new Layout(scale, w, h, rects, texts);
        }
        float offset = 0;
        for (Entry e : shown) {
            if (e.arrivedAt <= 0) continue;
            float t = (now - e.arrivedAt) / (float) SCROLL_MS;
            if (t >= 1) continue;
            float eased = 1 - (1 - Math.max(0, t)) * (1 - Math.max(0, t)) * (1 - Math.max(0, t));
            offset += wrap(e, font, textW).size() * LINE_H * (1 - eased);
        }
        float y = bottom + offset;
        for (int i = shown.size() - 1; i >= 0 && y > top - LINE_H; i--) {
            Entry e = shown.get(i);
            List<FormattedCharSequence> wl = wrap(e, font, textW);
            float msgBottom = y;
            for (int k = wl.size() - 1; k >= 0; k--) {
                y -= LINE_H;
                float alpha = visible(y, top, bottom);
                if (alpha > 0.03f) texts.add(new Text(textX, y + 1, wl.get(k), Math.round(alpha * 255)));
            }
            if (marks) {
                float y0 = Math.max(top, y), y1 = Math.min(bottom, msgBottom - 1);
                if (y1 > y0) rects.add(new Rect(PAD, y0, PAD + 2, y1,
                        0xFF000000 | (e.line.source() == ChatWall.Source.TWITCH ? TWITCH : JEU)));
            }
        }
        return new Layout(scale, w, h, rects, texts);
    }

    /** How much of a line [y, y + LINE_H) is inside [top, bottom]: 1 inside, fading as it leaves. */
    private static float visible(float y, float top, float bottom) {
        float in = Math.min(y + LINE_H, bottom) - Math.max(y, top);
        return Math.max(0, Math.min(1, in / LINE_H));
    }

    /** The message's lines at this width: "Author: text", the author in its colour (cached per width). */
    private static List<FormattedCharSequence> wrap(Entry e, Font font, int width) {
        if (e.wrapWidth == width) return e.wrapped;
        ChatWall.Line l = e.line;
        String full = l.author() + ": " + l.text();
        int authorEnd = l.author().length();
        List<FormattedCharSequence> out = new ArrayList<>();
        for (int[] r : ChatWall.wrap(full, width, font::width)) {
            MutableComponent c = Component.empty();
            if (r[0] < authorEnd) {
                c.append(Component.literal(full.substring(r[0], Math.min(r[1], authorEnd))).withColor(l.color() & 0xFFFFFF));
                if (r[1] > authorEnd) c.append(Component.literal(full.substring(authorEnd, r[1])).withColor(0xFFFFFF));
            } else {
                c.append(Component.literal(full.substring(r[0], r[1])).withColor(0xFFFFFF));
            }
            out.add(c.getVisualOrderText());
        }
        e.wrapWidth = width;
        e.wrapped = out;
        return out;
    }
}
