package fr.lordfinn.craftmaster.stage;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.show.Show;
import fr.lordfinn.craftmaster.show.ShowEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cue lists ("conduites"), like a light board's sequences: lines played one after the other, each one right away
 * unless it waits. A line is
 * <ul>
 *   <li>{@code attendre <secondes>}: wait;</li>
 *   <li>{@code ambiance <nom> [secondes]}: play a stage look (with that fade);</li>
 *   <li>{@code noir [secondes]}: every spotlight goes out;</li>
 *   <li>{@code conduite <nom>}: go on with another cue list;</li>
 *   <li>anything else: a server command without its slash ({@code cfsp groupe face* couleur rouge 2},
 *       {@code cfpt hoist 1 6 4}, {@code cmtele gauche clip dernier}, {@code tv intro}…).</li>
 * </ul>
 * One cue list plays at a time (starting another one stops it). Show events can start one by themselves
 * ({@link #EVENTS}), and each round can have its own for its launch.
 */
public final class Cues {

    private Cues() {}

    /** Show events that can play a cue list: launch of a round, its end, timer at zero, start of the reveal, podium. */
    public static final String LAUNCH = "lancement", END = "fin", TIMER_END = "chrono", REVEAL = "revelation", PODIUM = "podium";
    public static final List<String> EVENTS = List.of(LAUNCH, END, TIMER_END, REVEAL, PODIUM);

    /** The cue list playing: its name, its lines, the next line, the game tick to wait for. */
    private record Playing(String name, List<String> lines, int next, long waitUntil) {}

    private static Playing playing;
    private static int lastTimerLeft = -1;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Cues::tick);
        ShowEvents.register(new ShowEvents.Listener() {
            @Override
            public void onMancheStarted(MinecraftServer server, int season, int number, String name) {
                String cue = roundCue(name);
                fire(server, cue != null ? cue : Scenography.book().auto.get(LAUNCH), "lancement de « " + name + " »");
            }

            @Override
            public void onMancheEnded(MinecraftServer server, String name) {
                fire(server, Scenography.book().auto.get(END), "fin de « " + name + " »");
            }

            @Override
            public void onRevealStarted(MinecraftServer server, int season) {
                fire(server, Scenography.book().auto.get(REVEAL), "révélation");
            }

            @Override
            public void onRevealStep(MinecraftServer server, int season, int rank) {
                if (rank == 1) fire(server, Scenography.book().auto.get(PODIUM), "vainqueur révélé");
            }
        });
    }

    /** The cue list of the round named {@code name} (by id or name), or null. */
    private static String roundCue(String name) {
        var data = CraftMaster.data();
        var s = data == null ? null : data.activeSeason();
        if (s == null) return null;
        for (var p : s.parties.values())
            for (var m : p.manches)
                if (m.name.equalsIgnoreCase(name)) return Scenography.book().rounds.get(m.id);
        return null;
    }

    private static void fire(MinecraftServer server, String cue, String why) {
        if (cue == null || cue.isEmpty()) return;
        CraftMaster.LOG.info("[Scénographie] Conduite « {} » lancée par : {}", cue, why);
        play(server, cue);
    }

    // ---------------------------------------------------------------- playing

    public static String play(MinecraftServer server, String name) {
        List<String> lines = Scenography.book().cues.get(Scenography.key(name));
        // A timeline by that name (and no cue list): the automatic moments and voice orders play it too
        if (lines == null && Sequencer.exists(name)) return Sequencer.play(server, name, 0);
        if (lines == null) {
            // A look played as a one-line cue list: "lance l'ambiance plateau" and "lance la conduite plateau" both work
            if (Scenography.book().looks.containsKey(Scenography.key(name))) return Scenography.playLook(server, name, null);
            return "Conduite « " + Scenography.key(name) + " » inconnue (" + (Scenography.book().cues.isEmpty() ? "aucune" : String.join(", ", Scenography.book().cues.keySet())) + ").";
        }
        playing = new Playing(Scenography.key(name), List.copyOf(lines), 0, 0);
        step(server);
        return "Conduite « " + Scenography.key(name) + " » lancée (" + lines.size() + " ligne(s)).";
    }

    public static String stop() {
        if (playing == null) return "Aucune conduite en cours.";
        String n = playing.name;
        playing = null;
        return "Conduite « " + n + " » arrêtée.";
    }

    public static String status() {
        if (playing == null) return "Aucune conduite en cours.";
        return "Conduite « " + playing.name + " » : ligne " + Math.min(playing.next + 1, playing.lines.size()) + "/" + playing.lines.size() + ".";
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 == 0) {
            int left = Show.timerRemaining(server);
            if (lastTimerLeft > 0 && (left == 0 || left < 0 && lastTimerLeft <= 2))
                fire(server, Scenography.book().auto.get(TIMER_END), "fin du chrono");
            lastTimerLeft = left;
        }
        if (playing != null && server.getTickCount() >= playing.waitUntil) step(server);
    }

    /** Plays lines until one waits (at most 64 at once, so a cue list that calls itself cannot freeze the server). */
    private static void step(MinecraftServer server) {
        for (int guard = 0; guard < 64 && playing != null; guard++) {
            if (playing.next >= playing.lines.size()) {
                CraftMaster.LOG.info("[Scénographie] Conduite « {} » terminée", playing.name);
                playing = null;
                return;
            }
            Playing p = playing;
            String line = p.lines.get(p.next).strip();
            playing = new Playing(p.name, p.lines, p.next + 1, 0);
            double wait = run(server, line);
            if (playing == null || !playing.name.equals(p.name)) return; // the line started another cue list or stopped it
            if (wait > 0) {
                playing = new Playing(playing.name, playing.lines, playing.next, server.getTickCount() + Math.round(wait * 20));
                return;
            }
        }
    }

    /** Runs one line; returns how long to wait after it (seconds). */
    static double run(MinecraftServer server, String line) {
        if (line.isEmpty() || line.startsWith("#")) return 0;
        String[] w = line.split("\\s+", 2);
        String verb = fr.lordfinn.craftmaster.camera.CameraList.fold(w[0]).toLowerCase(Locale.ROOT);
        String rest = w.length > 1 ? w[1].strip() : "";
        switch (verb) {
            case "attendre", "attends", "wait", "pause" -> {
                return Math.max(0, number(rest, 0));
            }
            case "ambiance", "look" -> {
                // "ambiance plateau ouvert 3": the last word is the fade when it is a number
                String name = rest;
                Double fade = null;
                int sp = rest.lastIndexOf(' ');
                if (sp > 0) {
                    double f = number(rest.substring(sp + 1), Double.NaN);
                    if (!Double.isNaN(f)) {
                        fade = f;
                        name = rest.substring(0, sp);
                    }
                }
                CraftMaster.LOG.info("[Scénographie] {}", Scenography.playLook(server, name, fade));
                return 0;
            }
            case "noir", "blackout" -> {
                Scenography.blackout(server, number(rest, 1));
                return 0;
            }
            case "conduite" -> {
                play(server, rest);
                return 0;
            }
            case "sequence", "timeline" -> {
                Sequencer.play(server, rest, 0);
                return 0;
            }
            default -> {
                String cmd = line.startsWith("/") ? line.substring(1) : line;
                CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
                try {
                    server.getCommands().performPrefixedCommand(source, cmd);
                } catch (RuntimeException e) {
                    CraftMaster.LOG.warn("[Scénographie] Ligne « {} » : {}", line, e.toString());
                }
                return 0;
            }
        }
    }

    static double number(String s, double fallback) {
        try {
            return Double.parseDouble(s.strip().replace(',', '.').replaceAll("s$", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ---------------------------------------------------------------- editing

    public static List<String> lines(String name) {
        return Scenography.book().cues.get(Scenography.key(name));
    }

    public static String create(String name) {
        String k = Scenography.key(name);
        if (k.isEmpty()) return "Donne un nom à la conduite.";
        if (Scenography.book().cues.containsKey(k)) return "La conduite « " + k + " » existe déjà.";
        Scenography.book().cues.put(k, new ArrayList<>());
        Scenography.save();
        return "Conduite « " + k + " » créée : ajoute ses lignes avec /cm conduite ajouter " + k + " <ligne>.";
    }

    /** Adds a line at the end (or at position {@code at}, 1-based); creates the cue list if needed. */
    public static String add(String name, String line, int at) {
        String k = Scenography.key(name);
        if (k.isEmpty()) return "Donne un nom à la conduite.";
        List<String> l = Scenography.book().cues.computeIfAbsent(k, x -> new ArrayList<>());
        String clean = line.strip();
        if (clean.startsWith("/")) clean = clean.substring(1);
        int i = at <= 0 || at > l.size() ? l.size() : at - 1;
        l.add(i, clean);
        Scenography.save();
        return "Conduite « " + k + " », ligne " + (i + 1) + " : " + clean;
    }

    public static String remove(String name, int at) {
        List<String> l = lines(name);
        if (l == null) return "Conduite « " + Scenography.key(name) + " » inconnue.";
        if (at < 1 || at > l.size()) return "La conduite « " + Scenography.key(name) + " » a " + l.size() + " ligne(s).";
        String gone = l.remove(at - 1);
        Scenography.save();
        return "Ligne " + at + " retirée : " + gone;
    }

    public static String delete(String name) {
        String k = Scenography.key(name);
        if (Scenography.book().cues.remove(k) == null) return "Conduite « " + k + " » inconnue.";
        Scenography.book().auto.values().removeIf(k::equals);
        Scenography.book().rounds.values().removeIf(k::equals);
        Scenography.save();
        return "Conduite « " + k + " » supprimée.";
    }

    /** {@code event} plays {@code cue} by itself from now on ({@code cue} empty or "aucune": nothing). */
    public static String setAuto(String event, String cue) {
        String e = fr.lordfinn.craftmaster.camera.CameraList.fold(event).toLowerCase(Locale.ROOT);
        if (!EVENTS.contains(e)) return "Moment inconnu : " + String.join(", ", EVENTS) + ".";
        String k = Scenography.key(cue);
        if (k.isEmpty() || k.equals("aucune") || k.equals("rien")) {
            Scenography.book().auto.remove(e);
            Scenography.save();
            return "Plus de conduite automatique au moment « " + e + " ».";
        }
        if (!Scenography.book().cues.containsKey(k) && !Scenography.book().looks.containsKey(k) && !Sequencer.exists(k))
            return "Ni conduite, ni ambiance, ni séquence « " + k + " ».";
        Scenography.book().auto.put(e, k);
        Scenography.save();
        return "Au moment « " + e + " », « " + k + " » se lancera toute seule.";
    }

    /** The round {@code mancheId} plays {@code cue} at its launch (instead of the "lancement" one). */
    public static String setRound(String mancheId, String mancheName, String cue) {
        String k = Scenography.key(cue);
        if (k.isEmpty() || k.equals("aucune") || k.equals("rien")) {
            Scenography.book().rounds.remove(mancheId);
            Scenography.save();
            return "« " + mancheName + " » n'a plus de conduite à elle.";
        }
        if (!Scenography.book().cues.containsKey(k) && !Scenography.book().looks.containsKey(k) && !Sequencer.exists(k))
            return "Ni conduite, ni ambiance, ni séquence « " + k + " ».";
        Scenography.book().rounds.put(mancheId, k);
        Scenography.save();
        return "Au lancement de « " + mancheName + " », « " + k + " » se lancera toute seule.";
    }

    public static Map<String, String> autos() {
        return Scenography.book().auto;
    }
}
