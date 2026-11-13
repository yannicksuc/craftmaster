package fr.lordfinn.craftmaster.home;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings of CraftMaster's title screen, read from {@code config/craftmaster/accueil.json} (client side, see
 * docs/wiki/Accueil.md). The keys are French: LordFinn edits the file by hand. Every value is optional (a missing
 * or wrong one keeps its default, numbers are clamped to sensible ranges); only a file that is not JSON at all is
 * refused ({@link #parse} throws), and the game then shows the vanilla title screen.
 * <p>
 * Pure data: no Minecraft class, so it is unit tested.
 */
public final class HomeConfig {

    /** A player shown in a frame: pseudo, optional skin (URL or PNG file), pose of the portraits' presets. */
    public record Player(String pseudo, String skin, String pose) {}

    /**
     * The master on the left, sitting on the studio's throne (drawn with him, or not: {@code throne}). {@code size}:
     * height of the throne as a share of the screen's (above 1 it is cropped by the screen's edges; without the
     * throne, the seated figure's height); {@code scale}: the figure's size relative to the throne (1 = true
     * proportions); ({@code x}, {@code y}): where he sits (the cushion's centre, screen fractions); {@code angle}:
     * the group's turn toward the right (degrees, 0 = facing the player).
     */
    public record Character(String pseudo, String skin, boolean throne, double size, double scale, double angle, double x, double y) {}

    /**
     * The table with the Golden Frog, next to the throne: shown or not, the block drawn (any block id, e.g. Macaw's
     * Furniture's coffee table; a simple pedestal table of the mod when that block does not exist), where (blocks
     * from the throne's cushion, x to the right, z toward the player, in the throne's own turn), its turn and the
     * frog's (degrees), its size (1 = true proportions next to the throne).
     */
    public record Table(boolean enabled, String block, double x, double z, double angle, double frogAngle, double scale) {}

    /**
     * The row of frames. {@code first} and {@code vanish} are screen fractions (0..1, from the top left):
     * the centre of the nearest frame (player 1) and the zone the row recedes toward.
     */
    public record Row(int max, double angle, double sway, double spacing, double size, double[] first, double[] vanish,
                      double farthest, double fov, double haze) {}

    /**
     * The fixed panel at the bottom right holding the logo and the buttons: its left and right edges (screen
     * fractions, -1 = aligned with the row of frames above it), its margin to the bottom and its inner padding
     * (GUI pixels).
     */
    public record Panel(double left, double right, double bottom, double padding) {}

    /** Mouse parallax: how far each layer moves (GUI pixels at the screen's edges), the row's pivot, the easing. */
    public record Parallax(double background, double row, double front, double pivot, double smoothing) {}

    public boolean enabled = true;
    /** "host:port" the Jouer button connects to. */
    public String server = "109.239.154.4:25600";
    public String serverName = "CraftMaster";
    /**
     * Background PNG, relative to config/craftmaster/ or absolute; "" = config/craftmaster/accueil-fond.png when
     * that file exists, else the mod's picture of the stage.
     */
    public String background = "";
    /** Background zoom (cover fit times this), so the parallax never shows its edges. */
    public double zoom = 1.12;
    public Character character = new Character("LordFinn", "", true, 1.1, 1.35, 20, 0.17, 0.92);
    public List<Player> players = new ArrayList<>(List.of(
            new Player("Ensuri", "", ""), new Player("Cyllu", "", ""), new Player("Juste", "", ""),
            new Player("Arreaubaz", "", ""), new Player("Notch", "", "")));
    public Row row = new Row(5, 18, 4, 1.2, 0.32, new double[]{0.86, 0.33}, new double[]{0.4, 0.27}, 0.8, 24, 0.35);
    public Parallax parallax = new Parallax(7, 13, 20, 3, 5);
    public Panel panel = new Panel(-1, -1, 12, 6);
    public Table table = new Table(true, "mcwfurnitures:spruce_coffee_table", 1.7, 0.35, 0, 22, 1.3);
    /** Pose of a player who has none ("" in their entry). */
    public String defaultPose = "classique";

    /** The defaults, written to the file on first launch. */
    public static HomeConfig defaults() {
        return new HomeConfig();
    }

    /** Players actually shown: at most {@code row.max}, in the file's order (player 1 nearest). */
    public List<Player> shownPlayers() {
        return players.subList(0, Math.min(players.size(), row.max()));
    }

    // ---------------------------------------------------------------- JSON

    /** @throws com.google.gson.JsonParseException (or IllegalStateException) if the text is not a JSON object */
    public static HomeConfig parse(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        HomeConfig c = new HomeConfig();
        c.enabled = bool(o, "actif", c.enabled);
        c.server = str(o, "serveur", c.server).trim();
        if (c.server.isEmpty()) c.server = defaults().server;
        c.serverName = str(o, "nomServeur", c.serverName);
        c.background = str(o, "fond", c.background).trim();
        c.zoom = num(o, "zoomFond", c.zoom, 1, 2);
        c.defaultPose = str(o, "poseParDefaut", c.defaultPose);

        if (o.get("personnage") instanceof JsonObject p) {
            Character d = c.character;
            c.character = new Character(str(p, "pseudo", d.pseudo()), str(p, "skin", d.skin()), bool(p, "trone", d.throne()),
                    num(p, "taille", d.size(), 0.05, 3), num(p, "echelle", d.scale(), 0.3, 3), num(p, "angle", d.angle(), -90, 90),
                    num(p, "x", d.x(), -0.5, 1.5), num(p, "y", d.y(), -0.5, 1.5));
        }
        if (o.get("table") instanceof JsonObject t) {
            Table d = c.table;
            c.table = new Table(bool(t, "actif", d.enabled()), str(t, "bloc", d.block()).trim(), num(t, "x", d.x(), -10, 10), num(t, "z", d.z(), -10, 10),
                    num(t, "angle", d.angle(), -180, 180), num(t, "angleGrenouille", d.frogAngle(), -180, 180), num(t, "echelle", d.scale(), 0.2, 5));
        }
        if (o.get("joueurs") instanceof JsonArray a) {
            List<Player> list = new ArrayList<>();
            for (JsonElement e : a) {
                if (e.isJsonPrimitive()) {
                    String name = e.getAsString().trim();
                    if (!name.isEmpty()) list.add(new Player(name, "", ""));
                } else if (e instanceof JsonObject p) {
                    String name = str(p, "pseudo", "").trim();
                    String skin = str(p, "skin", "").trim();
                    if (!name.isEmpty() || !skin.isEmpty()) list.add(new Player(name, skin, str(p, "pose", "").trim()));
                }
            }
            c.players = list;
        }
        if (o.get("cadres") instanceof JsonObject r) {
            Row d = c.row;
            c.row = new Row((int) num(r, "max", d.max(), 0, 12), num(r, "angle", d.angle(), -80, 80),
                    num(r, "balancement", d.sway(), 0, 30), num(r, "ecart", d.spacing(), 0.3, 5),
                    num(r, "taille", d.size(), 0.05, 0.9), point(r, "premier", d.first()), point(r, "fuite", d.vanish()),
                    num(r, "plusLoin", d.farthest(), 0.2, 1), num(r, "champ", d.fov(), 2, 90), num(r, "brume", d.haze(), 0, 1));
        }
        if (o.get("panneau") instanceof JsonObject p) {
            Panel d = c.panel;
            c.panel = new Panel(edge(p, "gauche", d.left()), edge(p, "droite", d.right()),
                    num(p, "marge", d.bottom(), 0, 200), num(p, "interieur", d.padding(), 0, 50));
        }
        if (o.get("parallaxe") instanceof JsonObject p) {
            Parallax d = c.parallax;
            c.parallax = new Parallax(num(p, "fond", d.background(), 0, 100), num(p, "cadres", d.row(), 0, 100),
                    num(p, "devant", d.front(), 0, 100), num(p, "pivot", d.pivot(), 0, 30), num(p, "douceur", d.smoothing(), 0.2, 50));
        }
        return c;
    }

    public String toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("actif", enabled);
        o.addProperty("serveur", server);
        o.addProperty("nomServeur", serverName);
        o.addProperty("fond", background);
        o.addProperty("zoomFond", zoom);
        JsonObject p = new JsonObject();
        p.addProperty("pseudo", character.pseudo());
        p.addProperty("skin", character.skin());
        p.addProperty("trone", character.throne());
        p.addProperty("taille", character.size());
        p.addProperty("echelle", character.scale());
        p.addProperty("angle", character.angle());
        p.addProperty("x", character.x());
        p.addProperty("y", character.y());
        o.add("personnage", p);
        JsonObject t = new JsonObject();
        t.addProperty("actif", table.enabled());
        t.addProperty("bloc", table.block());
        t.addProperty("x", table.x());
        t.addProperty("z", table.z());
        t.addProperty("angle", table.angle());
        t.addProperty("angleGrenouille", table.frogAngle());
        t.addProperty("echelle", table.scale());
        o.add("table", t);
        JsonArray a = new JsonArray();
        for (Player pl : players) {
            JsonObject e = new JsonObject();
            e.addProperty("pseudo", pl.pseudo());
            e.addProperty("skin", pl.skin());
            e.addProperty("pose", pl.pose());
            a.add(e);
        }
        o.add("joueurs", a);
        o.addProperty("poseParDefaut", defaultPose);
        JsonObject r = new JsonObject();
        r.addProperty("max", row.max());
        r.addProperty("angle", row.angle());
        r.addProperty("balancement", row.sway());
        r.addProperty("ecart", row.spacing());
        r.addProperty("taille", row.size());
        r.add("premier", array(row.first()));
        r.add("fuite", array(row.vanish()));
        r.addProperty("plusLoin", row.farthest());
        r.addProperty("champ", row.fov());
        r.addProperty("brume", row.haze());
        o.add("cadres", r);
        JsonObject pa = new JsonObject();
        pa.addProperty("gauche", panel.left());
        pa.addProperty("droite", panel.right());
        pa.addProperty("marge", panel.bottom());
        pa.addProperty("interieur", panel.padding());
        o.add("panneau", pa);
        JsonObject x = new JsonObject();
        x.addProperty("fond", parallax.background());
        x.addProperty("cadres", parallax.row());
        x.addProperty("devant", parallax.front());
        x.addProperty("pivot", parallax.pivot());
        x.addProperty("douceur", parallax.smoothing());
        o.add("parallaxe", x);
        return GSON.toJson(o);
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static JsonArray array(double[] v) {
        JsonArray a = new JsonArray();
        for (double d : v) a.add(d);
        return a;
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        try {
            return o.get(key) instanceof com.google.gson.JsonPrimitive p && p.isBoolean() ? p.getAsBoolean() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static String str(JsonObject o, String key, String def) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static double num(JsonObject o, String key, double def, double min, double max) {
        try {
            if (!o.has(key)) return def;
            double v = o.get(key).getAsDouble();
            return Double.isFinite(v) ? Math.max(min, Math.min(max, v)) : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    /** A screen fraction 0..1, or -1 (any negative): automatic. */
    private static double edge(JsonObject o, String key, double def) {
        double v = num(o, key, def, -1, 1);
        return v < 0 ? -1 : v;
    }

    /** [x, y] screen fractions, each clamped to 0..1. */
    private static double[] point(JsonObject o, String key, double[] def) {
        try {
            if (!(o.get(key) instanceof JsonArray a) || a.size() != 2) return def;
            double x = a.get(0).getAsDouble(), y = a.get(1).getAsDouble();
            if (!Double.isFinite(x) || !Double.isFinite(y)) return def;
            return new double[]{Math.max(0, Math.min(1, x)), Math.max(0, Math.min(1, y))};
        } catch (RuntimeException e) {
            return def;
        }
    }
}
