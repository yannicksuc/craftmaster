package fr.lordfinn.craftmaster.trial.kit;

import fr.lordfinn.craftmaster.media.MediaServer;
import fr.lordfinn.craftmaster.media.ServerImages;
import fr.lordfinn.craftmaster.studio.ImageScreenEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The jury's screen of the walked drawings: the studio screens named « jury » (or any name) show either all the
 * drawings side by side with their author's name, or one drawing in big with its author's name, switched live from
 * the organisers' chat ([Tous] [Ensuri] [Cyllu]… [◀] [▶]) or by command. The pictures are made by the server
 * (16:9, paper and ink, a pixel font of its own: a server has no fonts) and reach the games through
 * {@link ServerImages}.
 */
public final class JuryScreen {

    private JuryScreen() {}

    public static final String DEFAULT_SCREEN = "jury";
    private static final int W = 1920, H = 1080;
    private static final Color BACK = new Color(0x2A, 0x1A, 0x14), PAPER = new Color(0xF3, 0xEC, 0xDC), TITLE = new Color(0xFF, 0xE9, 0xA8),
            GOLD = new Color(0xD4, 0xA5, 0x3A), INK = new Color(0x1E, 0x18, 0x14);

    /** What each arena's screen shows now: "" = all, else the player. */
    private static final Map<String, String> SHOWN = new HashMap<>();
    /** "arena:player" whose written name is on the screen: hidden until the organiser reveals it. */
    private static final java.util.Set<String> REVEALED = new java.util.HashSet<>();

    static void forgetRevealed(String arena) {
        String k = arena.toLowerCase(Locale.ROOT) + ":";
        REVEALED.removeIf(r -> r.startsWith(k));
    }

    /** The drawing's author, with the name of the animal once revealed. */
    private static String caption(String arena, String player) {
        if (!REVEALED.contains(arena.toLowerCase(Locale.ROOT) + ":" + player.toLowerCase(Locale.ROOT))) return player;
        String[] a = NameBooks.answer(arena, player);
        return a == null || a[0].isEmpty() ? player : player + " - " + (a[0].length() > 22 ? a[0].substring(0, 22) : a[0]);
    }

    /** Puts the name written by {@code player} on the screen, with their drawing in big. */
    public static String reveal(MinecraftServer server, Arena arena, String player, String screen) throws Exception {
        String known = DrawingImages.names(arena.name).stream().filter(n -> n.equalsIgnoreCase(player)).findFirst()
                .orElseThrow(() -> new IllegalStateException(player + " n'a pas de dessin."));
        String[] a = NameBooks.answer(arena.name, known);
        if (a == null) throw new IllegalStateException(known + " n'a pas de nom enregistré.");
        REVEALED.add(arena.name.toLowerCase(Locale.ROOT) + ":" + known.toLowerCase(Locale.ROOT));
        show(server, arena, known, screen);
        return known + " : « " + a[0] + " » (" + a[1] + "), affiché sur l'écran « " + screen + " ».";
    }

    /** A drawing was finished: the organisers get the buttons to show it. */
    static void drawingSaved(MinecraftServer server, Arena arena, String player) {
        Finish.tellOrganisers(server, null, Component.literal("✎ Dessin de ").withStyle(ChatFormatting.GOLD).append(Finish.name(player))
                .append(Component.literal(" prêt ").withStyle(ChatFormatting.GOLD)).append(buttons(arena.name)), null);
    }

    /** The chat buttons to switch the screen: all, each player, previous / next. */
    public static MutableComponent buttons(String arena) {
        MutableComponent line = Component.empty();
        line.append(button("[Tous]", "/cm arene jury " + arena + " tous", "Tous les dessins côte à côte", ChatFormatting.GREEN));
        for (String p : DrawingImages.all(arena).keySet())
            line.append(Component.literal(" ")).append(button("[" + p + "]", "/cm arene jury " + arena + " " + p, "Le dessin de " + p + " en grand",
                    Finish.colour(p)));
        line.append(Component.literal(" ")).append(button("[◀]", "/cm arene jury " + arena + " precedent", "Le dessin d'avant", ChatFormatting.GRAY));
        line.append(Component.literal(" ")).append(button("[▶]", "/cm arene jury " + arena + " suivant", "Le dessin d'après", ChatFormatting.GRAY));
        // The names stay hidden on the screen until revealed, one by one
        for (String p : DrawingImages.all(arena).keySet()) {
            if (NameBooks.answer(arena, p) == null || REVEALED.contains(arena.toLowerCase(Locale.ROOT) + ":" + p.toLowerCase(Locale.ROOT))) continue;
            line.append(Component.literal("\n")).append(button("[Révéler le nom] " + p, "/cm arene jury " + arena + " reveler " + p,
                    "Affiche à l'écran le nom écrit par " + p, ChatFormatting.GOLD));
        }
        return line;
    }

    private static MutableComponent button(String label, String command, String tip, ChatFormatting colour) {
        return Component.literal(label).withStyle(st -> st.withColor(colour).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal(tip))));
    }

    /**
     * Shows {@code what} ("tous", a player, "suivant", "precedent") on the screens named {@code screen}.
     *
     * @return a line for the organiser
     */
    public static String show(MinecraftServer server, Arena arena, String what, String screen) throws Exception {
        Map<String, byte[]> all = DrawingImages.all(arena.name);
        if (all.isEmpty()) throw new IllegalStateException("aucun dessin terminé dans « " + arena.name + " »");
        List<ImageScreenEntity> screens = MediaServer.screens(server, screen);
        if (screens.isEmpty()) throw new IllegalStateException("aucun écran nommé « " + screen + " » : regarde un écran du studio et /cm media tele nommer " + screen);
        List<String> names = new ArrayList<>(all.keySet());
        String key = arena.name.toLowerCase(Locale.ROOT);
        String w = what.toLowerCase(Locale.ROOT);
        String player;
        if (w.equals("tous") || w.equals("all")) player = "";
        else if (w.equals("suivant") || w.equals("precedent") || w.equals("précédent")) {
            int i = names.indexOf(SHOWN.getOrDefault(key, ""));
            int n = names.size();
            i = w.equals("suivant") ? (i + 1) % n : (i <= 0 ? n - 1 : i - 1);
            player = names.get(i);
        } else {
            player = names.stream().filter(n -> n.equalsIgnoreCase(what)).findFirst()
                    .orElseThrow(() -> new IllegalStateException(what + " n'a pas de dessin (dessins : " + String.join(", ", names) + ")"));
        }
        String url = ServerImages.publish(player.isEmpty() ? board(arena.name, all) : single(caption(arena.name, player), all.get(player)));
        for (ImageScreenEntity s : screens) {
            s.setFit(true);
            s.setUrl(url);
        }
        SHOWN.put(key, player);
        return player.isEmpty() ? "Écran « " + screen + " » : tous les dessins (" + names.size() + ")." : "Écran « " + screen + " » : le dessin de " + player + ".";
    }

    // ---------------------------------------------------------------- pictures

    private static BufferedImage canvas(Graphics2D[] out) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(BACK);
        g.fillRect(0, 0, W, H);
        out[0] = g;
        return img;
    }

    private static byte[] png(BufferedImage img) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(img, "png", o);
        return o.toByteArray();
    }

    /** One drawing in big, its author's name above. */
    static byte[] single(String player, byte[] drawing) throws Exception {
        Graphics2D[] g = new Graphics2D[1];
        BufferedImage img = canvas(g);
        int titleH = 150;
        PixelFont.draw(g[0], player, W / 2, 40, Math.max(5, Math.min(12, (W - 120) / (6 * Math.max(1, player.length())))), TITLE, true);
        BufferedImage d = ImageIO.read(new ByteArrayInputStream(drawing));
        fit(g[0], d, 60, titleH + 10, W - 120, H - titleH - 50);
        g[0].dispose();
        return png(img);
    }

    /** Every drawing side by side, each with its author's name below, in the most even grid that fits. */
    static byte[] board(String arena, Map<String, byte[]> all) throws Exception {
        Graphics2D[] g = new Graphics2D[1];
        BufferedImage img = canvas(g);
        int n = all.size(), cols = 1;
        double best = 0;
        for (int c = 1; c <= n; c++) {
            int rows = (n + c - 1) / c;
            double cell = Math.min((W - 80.0) / c, (H - 80.0) / rows / 1.12);
            if (cell > best) {
                best = cell;
                cols = c;
            }
        }
        int rows = (n + cols - 1) / cols;
        int cw = (W - 80) / cols, ch = (H - 80) / rows;
        int i = 0;
        for (Map.Entry<String, byte[]> e : all.entrySet()) {
            int r = i / cols, c = i % cols;
            // The last row centred
            int inRow = r == rows - 1 ? n - r * cols : cols;
            int x0 = 40 + c * cw + (cols - inRow) * cw / 2, y0 = 40 + r * ch;
            String caption = caption(arena, e.getKey());
            int label = Math.max(2, Math.min(Math.min(9, ch / 50), (cw - 20) / (6 * Math.max(1, caption.length()))));
            int labelH = label * 7 + 16;
            BufferedImage d = ImageIO.read(new ByteArrayInputStream(e.getValue()));
            fit(g[0], d, x0 + 10, y0 + 6, cw - 20, ch - labelH - 12);
            PixelFont.draw(g[0], caption, x0 + cw / 2, y0 + ch - labelH + 2, label, TITLE, true);
            i++;
        }
        g[0].dispose();
        return png(img);
    }

    /** The drawing as big as it fits in the box, centred, with a gold frame. */
    private static void fit(Graphics2D g, BufferedImage d, int x, int y, int w, int h) {
        double k = Math.min(w / (double) d.getWidth(), h / (double) d.getHeight());
        int dw = (int) (d.getWidth() * k), dh = (int) (d.getHeight() * k);
        int dx = x + (w - dw) / 2, dy = y + (h - dh) / 2;
        g.setColor(GOLD);
        g.fillRect(dx - 6, dy - 6, dw + 12, dh + 12);
        g.setColor(PAPER);
        g.fillRect(dx, dy, dw, dh);
        g.drawImage(d, dx, dy, dw, dh, null);
    }

    /** A 5 × 7 pixel font (capitals, digits, « _ » and « - »; accents dropped): the server has no fonts. */
    static final class PixelFont {
        private static final Map<Character, String[]> G = new HashMap<>();

        static {
            String[] rows = {
                    "A01110100011000111111100011000110001", "B11110100011000111110100011000111110", "C01110100011000010000100001000101110",
                    "D11110100011000110001100011000111110", "E11111100001000011110100001000011111", "F11111100001000011110100001000010000",
                    "G01110100011000010111100011000101111", "H10001100011000111111100011000110001", "I01110001000010000100001000010001110",
                    "J00111000100001000010000101001001100", "K10001100101010011000101001001010001", "L10000100001000010000100001000011111",
                    "M10001110111010110101100011000110001", "N10001100011100110101100111000110001", "O01110100011000110001100011000101110",
                    "P11110100011000111110100001000010000", "Q01110100011000110001101011001001101", "R11110100011000111110101001001010001",
                    "S01111100001000001110000010000111110", "T11111001000010000100001000010000100", "U10001100011000110001100011000101110",
                    "V10001100011000110001100010101000100", "W10001100011000110101101011010101010", "X10001100010101000100010101000110001",
                    "Y10001100010101000100001000010000100", "Z11111000010001000100010001000011111", "001110100011001110101110011000101110",
                    "100100011000010000100001000010001110", "201110100010000100010001000100011111", "311111000100010000010000011000101110",
                    "400010001100101010010111110001000010", "511111100001111000001000011000101110", "600110010001000011110100011000101110",
                    "711111000010001000100010000100001000", "801110100011000101110100011000101110", "901110100011000101111000010001001100",
                    "_00000000000000000000000000000011111", "-00000000000000011111000000000000000"};
            for (String r : rows) {
                String[] g = new String[7];
                for (int i = 0; i < 7; i++) g[i] = r.substring(1 + i * 5, 6 + i * 5);
                G.put(r.charAt(0), g);
            }
        }

        static String clean(String s) {
            return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
        }

        static int width(String s, int px) {
            return Math.max(0, clean(s).length() * 6 - 1) * px;
        }

        /** Draws {@code s} with pixels of {@code px}, centred on {@code cx} if asked, a dark shadow under it. */
        static void draw(Graphics2D g, String s, int cx, int y, int px, Color colour, boolean centre) {
            String t = clean(s);
            int x = centre ? cx - width(t, px) / 2 : cx;
            for (int pass = 0; pass < 2; pass++) {
                g.setColor(pass == 0 ? new Color(0, 0, 0, 150) : colour);
                int off = pass == 0 ? Math.max(1, px / 2) : 0;
                int xi = x;
                for (char ch : t.toCharArray()) {
                    String[] gl = G.get(ch);
                    if (gl != null)
                        for (int r = 0; r < 7; r++)
                            for (int c = 0; c < 5; c++)
                                if (gl[r].charAt(c) == '1') g.fillRect(xi + c * px + off, y + r * px + off, px, px);
                    xi += 6 * px;
                }
            }
        }
    }
}
