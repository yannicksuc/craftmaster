package fr.lordfinn.craftmaster.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.lordfinn.craftmaster.client.ui.Theme;
import fr.lordfinn.craftmaster.client.ui.Ui;
import fr.lordfinn.craftmaster.client.ui.UiButton;
import fr.lordfinn.craftmaster.client.ui.UiSounds;
import fr.lordfinn.craftmaster.client.ui.UiTabs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The CraftMaster menu (key N): one entry point to run the show, in the UI kit's colours. Six pages — season and
 * rounds, the trials live (each arena's options, changed at once), lights, cameras and broadcast, the contestants,
 * debug. Every button sends an ordinary {@code /cm} command (so it is undoable and answered like in the chat; the
 * last answers show at the bottom) and says what it does when hovered. The mouse wheel scrolls the page.
 * Dangerous buttons (publish, reveal) need Shift.
 */
public class MainMenuScreen extends Screen {

    static final List<String> PAGES = List.of("manches", "epreuves", "ambiances", "camera", "joueurs", "debug");
    private static final List<Component> TAB_NAMES = List.of(Component.literal("Manches"), Component.literal("Épreuves"),
            Component.literal("Ambiances"), Component.literal("Caméra"), Component.literal("Joueurs"), Component.literal("Debug"));
    private static final int ROW = 20, LABEL_W = 96;

    private static int lastPage;
    private int page;
    private int left, top, w, h, listTop, listBottom;
    private double scroll;
    private int contentHeight;
    private final List<Consumer<GuiGraphicsExtractor>> labels = new ArrayList<>();

    public MainMenuScreen(String page) {
        super(Component.literal("CraftMaster"));
        int i = PAGES.indexOf(page);
        this.page = i >= 0 ? i : lastPage;
    }

    void stateChanged() {
        rebuildWidgets();
    }

    private static JsonObject s() {
        return MenuClient.state == null ? new JsonObject() : MenuClient.state;
    }

    private static boolean admin() {
        return s().has("admin") && s().get("admin").getAsBoolean();
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        labels.clear();
        w = Math.min(width - 8, 460);
        h = Math.min(height - 8, 280);
        left = (width - w) / 2;
        top = (height - h) / 2;
        listTop = top + 44;
        listBottom = top + h - 34;
        if (MenuClient.state == null) return;
        if (!admin()) {
            playerPage();
            return;
        }
        UiTabs tabs = new UiTabs(font, left + 8, top + 22, 16, TAB_NAMES, () -> page, i -> {
            page = i;
            lastPage = i;
            scroll = 0;
            UiSounds.page();
            rebuildWidgets();
        });
        tabs.buttons().forEach(this::addRenderableWidget);
        Rows r = new Rows();
        switch (PAGES.get(page)) {
            case "manches" -> rounds(r);
            case "epreuves" -> trials(r);
            case "ambiances" -> lights(r);
            case "camera" -> cameras(r);
            case "joueurs" -> players(r);
            default -> debug(r);
        }
        contentHeight = r.y - listTop + (int) scroll;
        // Bottom bar: the same on every page
        int bx = left + 8, by = top + h - 26;
        addRenderableWidget(tip(UiButton.of(bx, by, 64, 18, Component.literal("Annuler"), () -> MenuClient.command("annuler")),
                "Annule la dernière modification (saisons, manches, scores, feuilles)"));
        addRenderableWidget(tip(UiButton.of(bx + 68, by, 64, 18, Component.literal("Refaire"), () -> MenuClient.command("refaire")),
                "Refait ce qui vient d'être annulé"));
        addRenderableWidget(UiButton.of(left + w - 72, by, 64, 18, Component.literal("Fermer"), this::onClose));
    }

    private void playerPage() {
        Rows r = new Rows();
        r.note("Le menu des organisateurs n'est pas pour toi : voici ce qui l'est.");
        r.row(Component.literal("Le show"), b -> {
            b.button("Classement", "Le classement de la saison (touche K)", () -> MenuClient.command("classement"));
            b.button("Ma tâche", "La tâche du moment (touche J)", () -> MenuClient.command("tache"));
        });
        addRenderableWidget(UiButton.of(left + w - 72, top + h - 26, 64, 18, Component.literal("Fermer"), this::onClose));
    }

    // ---------------------------------------------------------------- pages

    private void rounds(Rows r) {
        JsonObject season = obj(s(), "season");
        String current = str(obj(s(), "currentMancheInfo"), "name");
        int left = s().has("timer") && s().get("timer").isJsonObject() ? obj(s(), "timer").get("remaining").getAsInt() : 0;
        r.note("Saison : " + (season == null ? "aucune (/cm saison creer)" : str(season, "name"))
                + "  ·  en cours : " + (current == null ? "aucune" : current) + (left > 0 ? "  ·  chrono " + clock(left) : "")
                + (bool(s(), "waiting") ? "  ·  attend sa lettre" : ""));
        r.row(Component.literal("Le direct"), b -> {
            b.button("▶ Suivante", "Lance la manche suivante : titre, son, TP des candidats, chrono", () -> MenuClient.command("manche suivante"));
            b.button("■ Fin", "Fin de la manche en cours (titre et son)", () -> MenuClient.command("manche fin"));
            b.button("Chrono go", "Lance tout de suite le chrono qui attend la lettre ou le bouton, libère les joueurs figés",
                    () -> MenuClient.command("manche chrono go"));
            b.button("Chrono stop", "Arrête le chrono (et libère les joueurs figés)", () -> MenuClient.command("manche chrono stop"));
        });
        r.row(Component.literal("Scores"), b -> {
            b.shift("Publier", "Maj+clic : publie les scores (busts, étiquettes, écrans, classement des joueurs)", () -> MenuClient.command("scores publier"));
            b.shift("Révélation", "Maj+clic : lance la révélation mise en scène", () -> MenuClient.command("revelation lancer"));
            b.button("Saisie (U)", "L'écran de saisie des points de l'arbitre", () -> MenuClient.command("scores saisie"));
            b.button("Classement", "L'écran du classement (touche K)", () -> MenuClient.command("classement"));
        });
        r.row(Component.literal("Écrans"), b -> {
            b.button("Résultats (R)", "La feuille de résultats de la manche en cours", () -> MenuClient.command("epreuve feuille"));
            b.button("Tâches", "L'éditeur des lettres de tâche", () -> MenuClient.command("tache editer"));
            b.button("Statut", "L'état du direct dans le chat", () -> MenuClient.command("statut"));
        });
        JsonObject inv = obj(obj(s(), "inventoryCam"), "manches");
        JsonArray parties = season == null ? null : arr(season, "parties");
        if (parties != null) for (JsonElement pe : parties) {
            JsonObject p = pe.getAsJsonObject();
            if (!bool(p, "active")) continue;
            r.title("Manches de « " + str(p, "name") + " »");
            for (JsonElement me : arr(p, "manches")) {
                JsonObject m = me.getAsJsonObject();
                if (bool(m, "archived")) continue;
                String name = str(m, "name"), id = str(m, "id"), status = str(m, "status");
                ChatFormatting color = "current".equals(status) ? ChatFormatting.GOLD : "played".equals(status) ? ChatFormatting.GRAY : ChatFormatting.WHITE;
                int timer = m.has("timer") ? m.get("timer").getAsInt() : 0;
                MutableComponent label = Component.literal(("current".equals(status) ? "▶ " : "") + m.get("number").getAsString() + ". " + name)
                        .withStyle(color);
                if (timer > 0) label.append(Component.literal(" " + clock(timer)).withStyle(ChatFormatting.DARK_GRAY));
                Boolean cam = inv != null && inv.has(id) ? inv.get(id).getAsBoolean() : null;
                r.row(label, b -> {
                    b.button("Lancer", "Lance « " + name + " » maintenant", () -> MenuClient.command("manche lancer " + q(name)));
                    b.button("Tâche", "Lire la tâche de « " + name + " » (même secrète)", () -> MenuClient.command("tache lire " + q(name)));
                    String next = cam == null ? "oui" : cam ? "non" : "defaut";
                    b.button("Inv. : " + (cam == null ? "défaut" : cam ? "oui" : "non"),
                            "Caméra d'inventaire des viewers pendant cette manche (clic : " + next + ")",
                            () -> MenuClient.command("manche camerainventaire " + q(name) + " " + next));
                });
            }
        }
    }

    private void trials(Rows r) {
        r.row(Component.literal("Outils"), b -> {
            b.button("Journal", "Le journal de l'épreuve : boutons, buzzers, premiers, sorties de zone", () -> MenuClient.command("epreuve journal"));
            b.button("Buzzers", "Rallume la partie de buzzers", () -> MenuClient.command("epreuve buzzer reset"));
            b.button("Chronos", "Les chronos des candidats", () -> MenuClient.command("epreuve chrono liste"));
            b.button("Vocal commun", "Candidats et organisateurs dans un même canal vocal", () -> MenuClient.command("epreuve vocal commun"));
            b.button("Vocal fin", "Retour au vocal de proximité", () -> MenuClient.command("epreuve vocal fin"));
        });
        JsonArray arenas = arr(s(), "arenas");
        if (arenas == null || arenas.isEmpty()) {
            r.note("Aucune arène : /cm arene creer <nom> entre les deux points du mètre ruban.");
            return;
        }
        for (JsonElement e : arenas) {
            JsonObject a = e.getAsJsonObject();
            String name = str(a, "name"), n = q(name);
            JsonArray copies = arr(a, "copies");
            r.title(name + (str(a, "manche") == null ? "" : "  ·  manche " + str(a, "manche"))
                    + "  ·  " + (copies == null || copies.isEmpty() ? "pas de copie" : copies.size() + " copie(s)"));
            r.row(Component.literal("Le jeu").withStyle(ChatFormatting.GRAY), b -> {
                b.button("Copier", "Une copie par candidat de la saison, côte à côte (refait celles qui existent)", () -> MenuClient.command("arene copier " + n));
                b.button("Placer", "Chaque candidat au départ de sa copie, réapparition là, avec le kit", () -> MenuClient.command("arene placer " + n));
                b.shift("Reset", "Maj+clic : toutes les copies refaites comme la référence", () -> MenuClient.command("arene reset " + n));
                b.button("Comparer", "Chaque copie comparée bloc à bloc à la référence", () -> MenuClient.command("arene comparer " + n));
                b.button("Panneaux", "Le texte des panneaux de chaque copie", () -> MenuClient.command("arene panneaux " + n));
            });
            r.row(Component.literal("Options").withStyle(ChatFormatting.GRAY), b -> {
                b.toggle("Chrono lettre", "Le chrono de chacun démarre quand il ouvre la lettre", bool(a, "chronoLettre"),
                        v -> MenuClient.command("arene option " + n + " chronoLettre " + v));
                b.toggle("Panneaux suite", "Les panneaux se verrouillent quand la suite de la tâche est révélée", bool(a, "panneauxSuite"),
                        v -> MenuClient.command("arene option " + n + " panneauxSuite " + v));
                b.toggle("Murs", "Murs et plafond de barrières autour de chaque copie", bool(a, "murs"),
                        v -> MenuClient.command("arene option " + n + " murs " + v));
                b.toggle("Vider inv.", "L'inventaire des candidats est vidé avant le kit", bool(a, "inventaireVide"),
                        v -> MenuClient.command("arene option " + n + " inventaireVide " + v));
            });
            r.row(Component.empty(), b -> {
                b.toggle("Sol = fin", "Toucher le sol hors de la copie termine l'essai et verrouille l'inventaire", bool(a, "solFin"),
                        v -> MenuClient.command("arene option " + n + " solFin " + v));
                b.toggle("Alphabet", "Le score est la plus longue suite alphabétique de l'inventaire (avec Sol = fin)", bool(a, "alphabet"),
                        v -> MenuClient.command("arene option " + n + " alphabet " + v));
                Boolean cam = a.has("cameraInventaire") && !a.get("cameraInventaire").isJsonNull() ? a.get("cameraInventaire").getAsBoolean() : null;
                b.toggle("Cam. inventaire", "Les viewers voient l'inventaire des candidats de cette arène"
                        + (cam == null ? " (pas réglé : le réglage général décide)" : ""), cam != null && cam,
                        v -> MenuClient.command("arene option " + n + " cameraInventaire " + v));
                int suite = a.has("chronoSuite") ? a.get("chronoSuite").getAsInt() : 0;
                b.wheel("Suite : " + (suite == 0 ? "non" : clock(suite)),
                        "Chrono lancé à la révélation de la suite. Clic : +30 s, Maj+clic : −30 s, molette : ±10 s", suite,
                        v -> MenuClient.command("arene option " + n + " chronoSuite " + Math.max(0, Math.min(7200, v))), 30, 10);
            });
            if (copies != null && !copies.isEmpty()) r.row(Component.literal("Aller voir").withStyle(ChatFormatting.GRAY), b -> {
                for (JsonElement c : copies) {
                    String who = c.getAsString();
                    b.button(who, "Au-dessus de la copie de " + who + " (pour juger)", () -> MenuClient.command("arene tp " + n + " " + who + " dessus"));
                }
            });
        }
    }

    private void lights(Rows r) {
        r.row(Component.literal("Plateau"), b -> {
            b.button("Noir", "Toutes les lumières éteintes en fondu", () -> MenuClient.command("ambiance noir"));
            b.button("Stop conduite", "Arrête la conduite en cours", () -> MenuClient.command("conduite stop"));
            b.button("Séquences", "L'éditeur de séquences (timeline)", () -> MenuClient.command("sequence editer"));
            b.button("Spots allumés", "Tous les projecteurs allumés", () -> MenuClient.command("projecteur tout allume"));
            b.button("Spots éteints", "Tous les projecteurs éteints", () -> MenuClient.command("projecteur tout eteint"));
        });
        grid(r, "Ambiances", arr(s(), "looks"), name -> "Lancer l'ambiance « " + name + " »", name -> "ambiance lancer " + q(name));
        grid(r, "Conduites", arr(s(), "cues"), name -> "Lancer la conduite « " + name + " »", name -> "conduite lancer " + q(name));
        grid(r, "Rideaux", arr(s(), "curtains"), name -> "Clic : ouvrir « " + name + " », Maj+clic : fermer", null);
    }

    private void cameras(Rows r) {
        boolean auto = bool(s(), "autoDirection");
        r.note("À l'antenne : " + onAir(str(s(), "onAir")));
        r.row(Component.literal("Réalisation"), b -> {
            b.toggle("Auto", "Réalisation automatique (coupe d'elle-même sur ce qui se passe)", auto, v -> MenuClient.command("cam auto " + (v ? "on" : "off")));
            b.button("Plan large", "Le plan large", () -> MenuClient.command("cam large"));
            b.button("◀", "Caméra précédente", () -> MenuClient.command("cam precedente"));
            b.button("▶", "Caméra suivante", () -> MenuClient.command("cam suivante"));
            b.button("Régie (;)", "La régie des modos : plans, transitions, aperçus", () -> {
                onClose();
                fr.lordfinn.craftmaster.client.regie.ModoDeskClient.open(minecraft);
            });
        });
        String mode = str(obj(s(), "inventoryCam"), "mode");
        r.row(Component.literal("Habillage"), b -> {
            String next = "toujours".equals(mode) ? "epreuves" : "epreuves".equals(mode) ? "jamais" : "toujours";
            b.button("Inventaire : " + (mode == null ? "?" : mode), "Caméra d'inventaire des viewers : " + str(obj(s(), "inventoryCam"), "resume")
                    + ". Clic : " + next, () -> MenuClient.command("tv inventaire " + next));
            b.button("Scores", "Bandeau des scores à l'écran", () -> MenuClient.command("tv scores show"));
            b.button("Tâche", "La carte de la tâche à l'écran", () -> MenuClient.command("tv tache"));
            b.button("Stop", "Arrête l'habillage en cours (intro, générique)", () -> MenuClient.command("tv stop"));
        });
        JsonArray cams = arr(s(), "cameras");
        if (cams == null || cams.isEmpty()) {
            r.note("Aucune caméra : /cm cam ajouter [nom] là où tu regardes.");
            return;
        }
        r.title("Couper sur");
        List<String[]> list = new ArrayList<>();
        for (JsonElement e : cams) {
            JsonObject c = e.getAsJsonObject();
            String n = c.get("number").getAsString(), name = str(c, "name"), role = str(c, "role");
            list.add(new String[]{n, (name == null || name.isEmpty() ? "Caméra " + n : n + " · " + name) + (role == null || role.isEmpty() ? "" : " (" + role + ")")});
        }
        for (int i = 0; i < list.size(); i += 4) {
            List<String[]> part = list.subList(i, Math.min(list.size(), i + 4));
            r.row(Component.empty(), b -> {
                for (String[] c : part) b.button(c[1], "Couper sur la caméra " + c[0] + " (Maj+clic : en fondu)",
                        in -> MenuClient.command("cam couper " + c[0] + (in ? " fondu" : "")));
            });
        }
    }

    private void players(Rows r) {
        JsonArray cast = arr(s(), "players");
        if (cast == null || cast.isEmpty()) {
            r.note("Aucun candidat dans la saison : /cm joueur ajouter <pseudo>.");
            return;
        }
        r.note("Points : pour la manche en cours. Suivre : la caméra le suit et reste dessus.");
        for (JsonElement e : cast) {
            JsonObject p = e.getAsJsonObject();
            String name = str(p, "pseudo");
            boolean online = bool(p, "online");
            r.row(Component.literal((online ? "● " : "○ ") + name).withStyle(online ? ChatFormatting.GREEN : ChatFormatting.GRAY), b -> {
                b.button("+1", "Un point pour " + name + " dans la manche en cours", () -> MenuClient.command("points " + q(name) + " 1"));
                b.button("−1", "Un point de moins pour " + name, () -> MenuClient.command("points " + q(name) + " -1"));
                b.button("Suivre", "La caméra suit " + name + " et reste dessus", () -> MenuClient.command("cam suivre " + q(name)));
                b.button("Voir", "La meilleure caméra posée sur " + name + ", sinon le plan qui le suit", () -> MenuClient.command("cam voir " + q(name)));
                b.button("Synthé", "Synthé avec le nom (et le rang publié) de " + name, () -> MenuClient.command("tv synthe joueur " + q(name)));
            });
        }
    }

    private void debug(Rows r) {
        boolean on = bool(s(), "debug");
        r.row(Component.literal("Mode debug"), b -> {
            b.toggle(on ? "Activé" : "Coupé", "Zones des arènes, départs, champs, zones d'épreuve et état du show, pour toi seul (jamais sur la diffusion)",
                    on, v -> MenuClient.command("debug " + (v ? "on" : "off")));
            b.button("Statut", "L'état du direct dans le chat", () -> MenuClient.command("statut"));
            b.button("Historique", "Les 10 dernières modifications", () -> MenuClient.command("historique"));
            b.button("Commandes", "L'aide des commandes /cm dans le chat", () -> MenuClient.command("aide"));
        });
        if (bool(s(), "frozen")) r.row(Component.literal("Show gelé").withStyle(ChatFormatting.RED), b -> {
            b.button("Reprendre", "Feu vert : chaque chrono repart avec le temps qui lui restait", () -> MenuClient.command("reprise reprendre"));
            b.shift("Forcer", "Maj+clic : reprendre même s'il manque un candidat", () -> MenuClient.command("reprise reprendre force"));
        });
        else r.row(Component.literal("Crash"), b -> b.shift("Geler", "Maj+clic : gèle le show (joueurs, entités, chronos)", () -> MenuClient.command("reprise geler")));
        JsonArray recent = arr(s(), "recentActions");
        if (recent != null && !recent.isEmpty()) {
            r.title("Dernières modifications");
            int n = 0;
            for (JsonElement e : recent) if (n++ < 6) r.note("• " + e.getAsString());
        }
    }

    /** Names as buttons, four per row. {@code command} null: curtains (open / Shift: close). */
    private void grid(Rows r, String title, JsonArray names, java.util.function.Function<String, String> tooltip,
                      java.util.function.Function<String, String> command) {
        r.title(title);
        if (names == null || names.isEmpty()) {
            r.note("Aucune.");
            return;
        }
        List<String> all = new ArrayList<>();
        for (JsonElement e : names) all.add(e.getAsString());
        for (int i = 0; i < all.size(); i += 4) {
            List<String> part = all.subList(i, Math.min(all.size(), i + 4));
            r.row(Component.empty(), b -> {
                for (String n : part) b.button(n, tooltip.apply(n), shift -> MenuClient.command(command != null ? command.apply(n)
                        : "plateau rideau " + (shift ? "fermer " : "ouvrir ") + q(n)));
            });
        }
    }

    // ---------------------------------------------------------------- rows

    /** Builds the page top to bottom; rows out of the list's window get no widget. */
    private final class Rows {
        int y = listTop - (int) scroll;

        boolean visible(int rowY, int rowH) {
            return rowY >= listTop && rowY + rowH <= listBottom;
        }

        void note(String text) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(text), w - 24);
            for (var l : lines) {
                int ly = y;
                if (visible(ly, 10)) labels.add(g -> g.text(font, l, left + 12, ly + 1, Theme.TEXT_DIM, false));
                y += 11;
            }
            y += 3;
        }

        void title(String text) {
            int ly = y + 4;
            if (visible(ly, 10)) labels.add(g -> {
                g.text(font, Component.literal(text).withStyle(ChatFormatting.BOLD), left + 12, ly, Theme.TITLE, false);
                g.horizontalLine(left + 12, left + w - 12, ly + 10, Theme.BORDER_DARK);
            });
            y += 16;
        }

        void row(Component label, Consumer<Buttons> fill) {
            // A row without a label (grids) uses the whole width
            Buttons b = new Buttons(left + 12 + (label.getString().isEmpty() ? 0 : LABEL_W), y);
            fill.accept(b);
            int rows = b.lines();
            int ly = y;
            if (visible(ly, ROW)) labels.add(g -> g.text(font, label, left + 12, ly + 5, Theme.TEXT, false));
            b.add();
            y += rows * ROW;
        }
    }

    /** One row's buttons, laid out left to right (wrapping). */
    private final class Buttons {
        private final int x0;
        private int x, y;
        private final List<UiButton> out = new ArrayList<>();

        Buttons(int x, int y) {
            this.x0 = x;
            this.x = x;
            this.y = y;
        }

        private UiButton place(Component label, java.util.function.Consumer<Boolean> press) {
            int bw = Math.max(26, font.width(label) + 10);
            if (x + bw > left + w - 12 && x > x0) {
                x = x0;
                y += ROW;
            }
            UiButton b = new UiButton(x, y, bw, 18, label, in -> press.accept(in.hasShiftDown()));
            out.add(b);
            x += bw + 3;
            return b;
        }

        void button(String label, String tooltip, Runnable run) {
            tip(place(Component.literal(label), shift -> run.run()), tooltip);
        }

        void button(String label, String tooltip, java.util.function.Consumer<Boolean> run) {
            tip(place(Component.literal(label), run), tooltip);
        }

        /** Only with Shift held (publish, reveal, reset...): a plain click says so. */
        void shift(String label, String tooltip, Runnable run) {
            tip(place(Component.literal(label).withStyle(ChatFormatting.GOLD), shift -> {
                if (shift) run.run();
                else {
                    UiSounds.error();
                    MenuClient.ANSWERS.addFirst(Component.literal("« " + label + " » : Maj+clic pour confirmer.").withStyle(ChatFormatting.RED));
                }
            }), tooltip);
        }

        void toggle(String label, String tooltip, boolean on, java.util.function.Consumer<Boolean> set) {
            UiButton b = place(Component.literal((on ? "✔ " : "✘ ") + label).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY), shift -> set.accept(!on));
            b.on(() -> on);
            tip(b, tooltip + (on ? " — activé, clic : couper" : " — coupé, clic : activer"));
        }

        /** A number: click +step, Shift-click −step, the wheel ±fine. */
        void wheel(String label, String tooltip, int value, java.util.function.IntConsumer set, int step, int fine) {
            UiButton b = place(Component.literal(label), shift -> set.accept(value + (shift ? -step : step)));
            tip(b, tooltip);
            WHEELS.add(new Wheel(b, value, fine, set));
        }

        int lines() {
            return out.isEmpty() ? 1 : (out.getLast().getY() - out.getFirst().getY()) / ROW + 1;
        }

        void add() {
            for (UiButton b : out) if (b.getY() >= listTop && b.getY() + 18 <= listBottom) addRenderableWidget(b);
        }
    }

    private record Wheel(UiButton button, int value, int fine, java.util.function.IntConsumer set) {}

    private final List<Wheel> WHEELS = new ArrayList<>();

    @Override
    protected void rebuildWidgets() {
        labels.clear();
        WHEELS.clear();
        super.rebuildWidgets();
    }

    @Override
    protected void clearWidgets() {
        labels.clear();
        WHEELS.clear();
        super.clearWidgets();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        for (Wheel wl : WHEELS)
            if (wl.button.isMouseOver(mx, my)) {
                wl.set.accept(wl.value + (dy > 0 ? wl.fine : -wl.fine));
                return true;
            }
        if (my >= listTop && my <= listBottom) {
            double max = Math.max(0, contentHeight - (listBottom - listTop));
            double next = Math.max(0, Math.min(max, scroll - dy * 22));
            if (next != scroll) {
                scroll = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    private static UiButton tip(UiButton b, String text) {
        if (text != null && !text.isEmpty()) b.setTooltip(Tooltip.create(Component.literal(text)));
        return b;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        Ui.panel(g, font, left, top, w, h, Component.literal("CraftMaster").withStyle(ChatFormatting.BOLD));
        if (MenuClient.state == null) {
            g.centeredText(font, Component.literal("Chargement…"), left + w / 2, top + h / 2, Theme.TEXT_DIM);
        } else {
            for (Consumer<GuiGraphicsExtractor> l : labels) l.accept(g);
            double max = Math.max(0, contentHeight - (listBottom - listTop));
            if (max > 0) {
                // Scroll bar
                int bh = listBottom - listTop, th = Math.max(12, (int) (bh * (bh / (double) (contentHeight))));
                int ty = listTop + (int) ((bh - th) * (scroll / max));
                g.fill(left + w - 7, listTop, left + w - 5, listBottom, 0x40FFFFFF);
                g.fill(left + w - 7, ty, left + w - 5, ty + th, Theme.BORDER);
            }
        }
        // The last answers of the server (the chat is hidden behind the menu)
        int ay = top + h - 26;
        int ax = left + 150;
        int n = 0;
        for (Component a : MenuClient.ANSWERS) {
            if (n >= 2) break;
            g.text(font, font.plainSubstrByWidth(a.getString(), left + w - 80 - ax), ax, ay + (n == 0 ? 0 : 10), n == 0 ? Theme.TEXT : Theme.TEXT_DIM, false);
            n++;
        }
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- JSON

    static JsonObject obj(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null;
    }

    static JsonArray arr(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : null;
    }

    static String str(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
    }

    static boolean bool(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsBoolean();
    }

    /** A name as an argument: quoted when it has a space or an accent. */
    static String q(String name) {
        if (name == null) return "\"\"";
        return name.matches("[A-Za-z0-9_.+-]+") ? name : "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** "camera 3", "follow Ensuri"... in words. */
    static String onAir(String shot) {
        if (shot == null || shot.equals("none")) return "rien (vue du joueur)";
        String[] p = shot.split(" ", 2);
        String rest = p.length > 1 ? p[1] : "";
        return switch (p[0]) {
            case "camera" -> "caméra " + rest;
            case "follow" -> "suit " + rest;
            case "path" -> "travelling " + rest;
            case "mosaic" -> "écran partagé " + rest;
            case "pov" -> "vue de " + rest;
            case "free" -> "caméra libre";
            default -> shot;
        };
    }

    static String clock(int seconds) {
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
