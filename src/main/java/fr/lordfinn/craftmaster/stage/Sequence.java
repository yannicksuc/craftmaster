package fr.lordfinn.craftmaster.stage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A timeline ("séquence") of the stage: tracks, each one driving one thing (a group of spotlights, a curtain, a
 * gallery of portraits, a screen, or plain commands), with keypoints in time. A keypoint only says what changes;
 * a field left out (null) stays as it is. Played by {@link Sequencer}, edited in game (TimelineScreen) or written by
 * hand / by an AI as JSON (French keys, see docs/wiki/Scenographie.md), saved in config/craftmaster/scenographie.json.
 * <pre>
 * {"duree": 12, "boucle": false, "pistes": [
 *   {"type": "lumiere", "cible": "tout", "cles": [{"t": 0, "allume": false, "fondu": 1}]},
 *   {"type": "lumiere", "cible": "poursuite", "cles": [{"t": 2, "suit": "@rang:1", "couleur": "or", "intensite": 100, "fondu": 1.5, "effet": "respiration"}]},
 *   {"type": "rideau", "cible": "fond", "cles": [{"t": 3, "ouvert": true}]},
 *   {"type": "commande", "cles": [{"t": 4, "commande": "title @a title \"Révélation\""}]}]}
 * </pre>
 */
public final class Sequence {

    public static final String LIGHT = "lumiere", CURTAIN = "rideau", PORTRAITS = "tableaux", SCREEN = "ecran", COMMAND = "commande";
    public static final List<String> TYPES = List.of(LIGHT, CURTAIN, PORTRAITS, SCREEN, COMMAND);

    /** Light effects, drawn by the clients on top of the intensity. */
    public static final String NO_EFFECT = "aucun";
    public static final List<String> EFFECTS = List.of(NO_EFFECT, "respiration", "pulse", "strobe", "scintille", "chenillard");

    /** Length (seconds): the end of the timeline, where it stops or loops. */
    public double duree = 10;
    /** Starts again at the end until stopped. */
    public boolean boucle;
    /** A word on what it is for (shown in the lists, read by an AI). */
    public String description = "";
    public List<Track> pistes = new ArrayList<>();

    public static final class Track {
        /** {@link #TYPES}. */
        public String type = LIGHT;
        /**
         * What it drives: spotlights (a name, a group prefix, "tout"), a curtain's name ("tout": all), a season's
         * gallery (its number, "tout"), a screen's name; nothing for commands.
         */
        public String cible = "";
        /** Muted: kept but not played. */
        public boolean muette;
        public List<Key> cles = new ArrayList<>();

        public Track() {}

        public Track(String type, String cible) {
            this.type = type;
            this.cible = cible;
        }

        public void sort() {
            cles.sort(Comparator.comparingDouble(k -> k.t));
        }
    }

    /** A keypoint: at {@code t}, what changes, reached in {@code fondu} seconds. */
    public static final class Key {
        /** Time from the start (seconds). */
        public double t;
        /** Transition: how long the change takes (0: a cut). */
        public double fondu;

        // ---- lumiere
        public Boolean allume;
        /** A colour name (blanc, chaud, ambre, or, rouge…) or #RRGGBB. */
        public String couleur;
        /** 0..100. */
        public Integer intensite;
        /** Half-angle of the cone, 1..30 degrees. */
        public Float cone;
        /**
         * Who the beam follows: a player's name or a rule ({@code @scene}, {@code @maitre}, {@code @rang:1},
         * {@code @dernier}, {@code @hasard}, {@code @public}, {@code @moment}, {@code @candidat}, {@code @proche},
         * {@code @zone:x}); "" stops following.
         */
        public String suit;
        /** Points at a place: {@code @centre}, {@code @point:<name>} (plateau points), or "x y z". */
        public String vise;
        /** Fixed direction (degrees), both or neither. */
        public Float horizontal, vertical;
        /** {@link #EFFECTS}. */
        public String effet;
        /** Effect speed (1 = normal). */
        public Double vitesse;
        /** Wave: the group's spotlights take the change one after the other over this many seconds, left to right. */
        public Double vague;

        // ---- rideau
        public Boolean ouvert;

        // ---- tableaux
        /** String length (blocks). */
        public Float hauteur;
        /** One portrait after the other, this many seconds apart (left to right). */
        public Double cascade;

        // ---- ecran: what /cmtele <screen> takes ("clip dernier", "lien https://…", "chat", "off")
        public String montre;

        // ---- commande: a cue list line (a server command without "/", or "ambiance x", "noir 2"…)
        public String commande;

        public Key() {}

        public Key(double t) {
            this.t = t;
        }

        public Key copy() {
            Key k = new Key(t);
            k.fondu = fondu;
            k.allume = allume;
            k.couleur = couleur;
            k.intensite = intensite;
            k.cone = cone;
            k.suit = suit;
            k.vise = vise;
            k.horizontal = horizontal;
            k.vertical = vertical;
            k.effet = effet;
            k.vitesse = vitesse;
            k.vague = vague;
            k.ouvert = ouvert;
            k.hauteur = hauteur;
            k.cascade = cascade;
            k.montre = montre;
            k.commande = commande;
            return k;
        }

        /** A few words for lists and the timeline's tooltips. */
        public String describe(String type) {
            List<String> p = new ArrayList<>();
            switch (type == null ? LIGHT : type) {
                case CURTAIN -> {
                    if (ouvert != null) p.add(ouvert ? "ouvre" : "ferme");
                }
                case PORTRAITS -> {
                    if (hauteur != null) p.add("ficelle " + num(hauteur) + " bloc(s)");
                    if (cascade != null && cascade > 0) p.add("en cascade " + num(cascade) + " s");
                }
                case SCREEN -> {
                    if (montre != null) p.add(montre.isEmpty() ? "mire" : montre);
                }
                case COMMAND -> {
                    if (commande != null) p.add(commande);
                }
                default -> {
                    if (allume != null) p.add(allume ? "allume" : "éteint");
                    if (couleur != null) p.add(couleur);
                    if (intensite != null) p.add(intensite + " %");
                    if (cone != null) p.add("cône " + num(cone) + "°");
                    if (suit != null) p.add(suit.isEmpty() ? "ne suit plus" : "suit " + suit);
                    if (vise != null) p.add("vise " + vise);
                    if (horizontal != null && vertical != null) p.add(Math.round(horizontal) + "° / " + Math.round(vertical) + "°");
                    if (effet != null) p.add(effet);
                    if (vague != null && vague > 0) p.add("vague " + num(vague) + " s");
                }
            }
            if (fondu > 0) p.add("fondu " + num(fondu) + " s");
            return p.isEmpty() ? "rien" : String.join(", ", p);
        }
    }

    static String num(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(Math.round(d * 100) / 100.0).replace('.', ',');
    }

    /** Fills what a hand-written file may leave out, sorts keys, clamps times. */
    public Sequence clean() {
        if (pistes == null) pistes = new ArrayList<>();
        if (description == null) description = "";
        if (!(duree > 0)) duree = 10;
        duree = Math.min(duree, 3600);
        pistes.removeIf(java.util.Objects::isNull);
        for (Track tr : pistes) {
            if (tr.type == null || !TYPES.contains(tr.type.toLowerCase(Locale.ROOT))) tr.type = LIGHT;
            tr.type = tr.type.toLowerCase(Locale.ROOT);
            if (tr.cible == null) tr.cible = "";
            if (tr.cles == null) tr.cles = new ArrayList<>();
            tr.cles.removeIf(java.util.Objects::isNull);
            for (Key k : tr.cles) {
                k.t = Math.max(0, Math.min(duree, k.t));
                k.fondu = Math.max(0, Math.min(60, k.fondu));
            }
            tr.sort();
        }
        return this;
    }

    /** The latest keypoint time (the timeline should be at least that long). */
    public double lastKey() {
        double last = 0;
        for (Track tr : pistes) for (Key k : tr.cles) last = Math.max(last, k.t + k.fondu);
        return last;
    }

    public int keyCount() {
        int n = 0;
        for (Track tr : pistes) n += tr.cles.size();
        return n;
    }
}
