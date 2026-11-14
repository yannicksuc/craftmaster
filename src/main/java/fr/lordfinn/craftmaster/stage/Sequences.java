package fr.lordfinn.craftmaster.stage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.lordfinn.craftmaster.CraftMaster;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The saved timelines: create, replace (from the editor or a JSON file), duplicate, rename, delete, and the whole
 * timeline tools (mirror, reverse, stretch). A file in config/craftmaster/sequences/ can be imported and exported:
 * the way for an AI (or anyone) to write a timeline by hand.
 */
public final class Sequences {

    private Sequences() {}

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static Map<String, Sequence> all() {
        return Scenography.book().sequences;
    }

    public static Sequence get(String name) {
        return all().get(Scenography.key(name));
    }

    public static Sequence parse(String json) {
        Sequence s = GSON.fromJson(json, Sequence.class);
        if (s == null) throw new IllegalArgumentException("séquence vide");
        return s.clean();
    }

    public static String json(Sequence s) {
        return GSON.toJson(s);
    }

    /** Saves (creates or replaces) a timeline; one playing it goes on with the new version. */
    public static void put(String name, Sequence seq) {
        String k = Scenography.key(name);
        seq.clean();
        all().put(k, seq);
        Scenography.save();
        Sequencer.edited(k, seq);
    }

    public static String create(String name, double seconds) {
        String k = Scenography.key(name);
        if (k.isEmpty()) return "Donne un nom à la séquence.";
        if (all().containsKey(k)) return "La séquence « " + k + " » existe déjà.";
        Sequence s = new Sequence();
        s.duree = seconds > 0 ? seconds : 10;
        // A ready-to-fill start: all the lights, the curtains
        s.pistes.add(new Sequence.Track(Sequence.LIGHT, "tout"));
        put(k, s);
        return "Séquence « " + k + " » créée (" + Sequence.num(s.duree) + " s).";
    }

    public static String delete(String name) {
        String k = Scenography.key(name);
        if (all().remove(k) == null) return "Séquence « " + k + " » inconnue.";
        Sequencer.stop(k);
        Scenography.book().auto.values().removeIf(k::equals);
        Scenography.book().rounds.values().removeIf(k::equals);
        Scenography.save();
        return "Séquence « " + k + " » supprimée.";
    }

    public static String duplicate(String name, String copy) {
        Sequence s = get(name);
        if (s == null) return "Séquence « " + Scenography.key(name) + " » inconnue.";
        String k = Scenography.key(copy);
        if (k.isEmpty() || all().containsKey(k)) return "Choisis un autre nom que « " + k + " ».";
        put(k, SequenceTools.copy(s));
        return "Séquence « " + Scenography.key(name) + " » copiée en « " + k + " ».";
    }

    public static String rename(String name, String to) {
        String from = Scenography.key(name), k = Scenography.key(to);
        Sequence s = all().get(from);
        if (s == null) return "Séquence « " + from + " » inconnue.";
        if (k.isEmpty() || all().containsKey(k)) return "Choisis un autre nom que « " + k + " ».";
        all().remove(from);
        Sequencer.stop(from);
        all().put(k, s);
        Scenography.book().auto.replaceAll((e, v) -> v.equals(from) ? k : v);
        Scenography.book().rounds.replaceAll((e, v) -> v.equals(from) ? k : v);
        Scenography.save();
        return "Séquence « " + from + " » renommée en « " + k + " ».";
    }

    // ---------------------------------------------------------------- whole-timeline tools

    /** Adds, for every track with a side in its name (or every light track: all), the other side's mirror. */
    public static String mirror(net.minecraft.server.MinecraftServer server, String name) {
        Sequence s = get(name);
        if (s == null) return "Séquence « " + Scenography.key(name) + " » inconnue.";
        Sequencer.Plan plan = Sequencer.Plan.of(server);
        List<Sequence.Track> added = new ArrayList<>();
        for (Sequence.Track tr : s.pistes) {
            String other = SequenceTools.mirrorName(tr.cible);
            if (other.equals(tr.cible)) continue;
            boolean exists = s.pistes.stream().anyMatch(t -> t.type.equals(tr.type) && t.cible.equalsIgnoreCase(other));
            if (!exists) added.add(SequenceTools.mirror(tr, plan.front(), plan::mirrorPlace));
        }
        if (added.isEmpty()) return "Rien à mettre en miroir : aucune piste n'a de côté dans son nom (gauche, droite, cour, jardin, g, d).";
        s.pistes.addAll(added);
        put(name, s);
        return added.size() + " piste(s) en miroir ajoutée(s) à « " + Scenography.key(name) + " ».";
    }

    public static String reverse(String name) {
        Sequence s = get(name);
        if (s == null) return "Séquence « " + Scenography.key(name) + " » inconnue.";
        SequenceTools.reverse(s);
        put(name, s);
        return "Séquence « " + Scenography.key(name) + " » inversée.";
    }

    public static String stretch(String name, double factor) {
        Sequence s = get(name);
        if (s == null) return "Séquence « " + Scenography.key(name) + " » inconnue.";
        SequenceTools.stretch(s, factor);
        put(name, s);
        return "Séquence « " + Scenography.key(name) + " » : " + Sequence.num(s.duree) + " s.";
    }

    public static String loop(String name, boolean on) {
        Sequence s = get(name);
        if (s == null) return "Séquence « " + Scenography.key(name) + " » inconnue.";
        s.boucle = on;
        put(name, s);
        return "Séquence « " + Scenography.key(name) + " » : " + (on ? "en boucle" : "jouée une fois") + ".";
    }

    // ---------------------------------------------------------------- files

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("craftmaster/sequences");
    }

    private static Path file(String name) {
        String safe = Scenography.key(name).replaceAll("[^a-z0-9 _-]", "_").replace(' ', '_');
        return folder().resolve(safe + ".json");
    }

    /** Reads config/craftmaster/sequences/<name>.json into the timeline {@code name} (replacing it). */
    public static String importFile(String name) {
        Path f = file(name);
        if (!Files.exists(f)) return "Pas de fichier " + folder().getFileName() + "/" + f.getFileName() + ".";
        try {
            Sequence s = parse(Files.readString(f, StandardCharsets.UTF_8));
            put(name, s);
            return "Séquence « " + Scenography.key(name) + " » importée : " + s.pistes.size() + " piste(s), " + s.keyCount() + " keypoint(s), " + Sequence.num(s.duree) + " s.";
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Scénographie] {} illisible", f, e);
            return "Fichier illisible : " + e.getMessage();
        }
    }

    /** Writes the timeline to config/craftmaster/sequences/<name>.json. */
    public static String exportFile(String name) {
        Sequence s = get(name);
        if (s == null) return "Séquence « " + Scenography.key(name) + " » inconnue.";
        try {
            Files.createDirectories(folder());
            Files.writeString(file(name), json(s), StandardCharsets.UTF_8);
            return "Séquence « " + Scenography.key(name) + " » écrite dans config/craftmaster/sequences/" + file(name).getFileName() + ".";
        } catch (IOException e) {
            return "Écriture impossible : " + e.getMessage();
        }
    }

    /** Imports every file of the folder (new and changed timelines). */
    public static String importAll() {
        if (!Files.isDirectory(folder())) return "Pas de dossier config/craftmaster/sequences.";
        int n = 0;
        List<String> bad = new ArrayList<>();
        try (var files = Files.list(folder())) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = f.getFileName().toString().replaceAll("\\.json$", "").replace('_', ' ');
                try {
                    put(name, parse(Files.readString(f, StandardCharsets.UTF_8)));
                    n++;
                } catch (Exception e) {
                    bad.add(f.getFileName() + " (" + e.getMessage() + ")");
                }
            }
        } catch (IOException e) {
            return "Lecture impossible : " + e.getMessage();
        }
        return n + " séquence(s) importée(s)" + (bad.isEmpty() ? "." : ", illisibles : " + String.join(", ", bad) + ".");
    }
}
