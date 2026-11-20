package fr.lordfinn.craftmaster.broadcast;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Cues;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowCue;
import fr.lordfinn.craftmaster.camera.CameraPath;
import fr.lordfinn.craftmaster.camera.CameraPaths;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.camera.Lieux;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.MancheIntro;
import fr.lordfinn.craftmaster.data.Partie;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.show.Actions;
import fr.lordfinn.craftmaster.show.Show;
import fr.lordfinn.craftmaster.show.ShowEvents;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Plays a round's intro ({@link MancheIntro}) on the server, so every broadcast game sees the same shots at the
 * same time: each shot goes on air with its transition and effect, the cards and the music cue come with it, and
 * at the end the round's wide shot takes over. While it runs the automatic direction waits and the overlay hides
 * its usual graphics (sequence "manche" in the show state).
 */
public final class MancheIntros {

    private record Step(long due, Runnable run) {}

    private static final List<Step> STEPS = new ArrayList<>();
    /** Round whose intro is playing, or null. */
    private static String running;
    private static boolean music;
    /** The round waiting for the end of its intro to start ({@link #playThenStart}), or null. */
    private static Actions.MancheStarted pending;
    /** Between the black and the reveal of a launched round. */
    private static boolean launching;
    /** Id of the round waiting ({@link #pending}) and what runs when the picture opens on it (task reveal…). */
    private static String pendingId;
    private static final List<Runnable> AT_REVEAL = new ArrayList<>();

    /**
     * Round {@code mancheId} is waiting for its intro to end: {@code run} is kept for the moment the picture opens
     * on the players (true); false when that round is not waiting (run it now).
     */
    public static boolean atReveal(String mancheId, Runnable run) {
        if ((pending == null && !launching) || !mancheId.equals(pendingId)) return false;
        AT_REVEAL.add(run);
        return true;
    }

    private static void dropPending() {
        pending = null;
        pendingId = null;
        AT_REVEAL.clear();
    }

    /**
     * The next intro starts behind a black already on screen (« lancer l'épreuve » drew its shots first): its first
     * shot cuts in under the black, which then opens (no second dip to black).
     */
    private static boolean fromBlack;

    public static void fromBlack(boolean on) {
        fromBlack = on;
    }

    private MancheIntros() {}

    /** Id of the round whose intro is playing, or null. */
    public static String running() {
        return running;
    }

    private static void at(MinecraftServer server, double seconds, Runnable run) {
        STEPS.add(new Step(server.getTickCount() + Math.round(seconds * 20), run));
    }

    /** Length of a travelling for the timeline (0 if unknown: the default length is used). */
    static double pathSeconds(String name) {
        CameraPath p = CameraPaths.get(name);
        return p == null ? 0 : p.duration();
    }

    /**
     * A round started whose intro is set to play first and lead into it ({@link MancheIntro#chained}): plays the
     * intro, and the round itself (teleports, title, timer) waits for its end ({@link #launch}). Returns the line for
     * the organiser, or null when the round should start at once (no such intro, or it could not play).
     */
    public static String playThenStart(MinecraftServer server, Actions.MancheStarted start) {
        CraftMasterData data = CraftMaster.data();
        Manche m = data == null ? null : data.currentManche();
        if (m == null || !m.intro.auto || !m.intro.chained || m.intro.shots.isEmpty()) return null;
        String line = play(server, m.id);
        if (running == null) return null;
        pending = start;
        pendingId = m.id;
        return line + " La manche démarre à la fin : noir, téléportation, titre, révélation.";
    }

    /** Starts the intro of round {@code mancheId}; returns the line for the organiser. */
    public static String play(MinecraftServer server, String mancheId) {
        return play(server, mancheId, 1);
    }

    /**
     * Starts the intro of round {@code mancheId} from shot {@code from} (1 = the whole intro; later = a try-out of the
     * end, without the music, which would not be in time); returns the line for the organiser.
     */
    public static String play(MinecraftServer server, String mancheId, int from) {
        Manche m = Lieux.manche(mancheId);
        if (m == null) return "⚠ Manche « " + mancheId + " » introuvable.";
        MancheIntro intro = m.intro;
        if (intro.shots.isEmpty())
            return "⚠ La manche « " + m.name + " » n'a pas encore d'intro (/cm tv intro " + m.id + " ajouter camera <caméra>).";
        // A round still waiting for an earlier intro is dropped: another one is being introduced
        dropPending();
        stop(server);
        Credits.stop(server);
        running = m.id;
        int first = Math.max(0, Math.min(intro.shots.size() - 1, from - 1));
        music = !intro.music.isEmpty() && first == 0;
        Director.cue(server, new ShowCue(Cues.CLEAR, "", "", "", 0));
        if (music) Director.cue(server, new ShowCue(Cues.THEME, "play", soundOf(intro.music), "", 0));
        String title = m.name, subtitle = subtitle(m);
        List<String> problems = new ArrayList<>();
        int autoCard = intro.autoCardShot();
        List<MancheIntro.Timed> timeline = intro.timeline(MancheIntros::pathSeconds);
        double shift = timeline.get(first).start();
        for (MancheIntro.Timed t : timeline) {
            if (t.index() < first) continue;
            MancheIntro.Shot s = t.shot();
            Director.ShotRequest request = request(server, m, s, intro.effectOf(s), problems, t.index());
            int ticks = (int) Math.round(t.length() * 20);
            String card = t.index() == autoCard ? MancheIntro.TITLE : s.card();
            String[] text = s.text().isEmpty() ? new String[]{title, subtitle} : s.text().split("\\|", 2);
            String cardTitle = text[0].trim(), cardSub = text.length > 1 ? text[1].trim() : s.text().isEmpty() ? subtitle : "";
            at(server, t.start() - shift, () -> {
                if (request != null) Director.take(server, request);
                if (card.equals(MancheIntro.TITLE))
                    Director.cue(server, new ShowCue(Cues.TITLE_CARD, cardTitle, cardSub, "", Math.max(40, ticks - 10)));
                else if (card.equals(MancheIntro.LOWER_THIRD))
                    Director.cue(server, new ShowCue(Cues.LOWER_THIRD, cardTitle, cardSub, "", Math.max(40, ticks - 10)));
            });
        }
        double end = intro.duration(MancheIntros::pathSeconds);
        // The task typed at the bottom of the picture over the last shots (the task location shot, else the last one)
        String typed = typedTask(m);
        if (!typed.isEmpty() && !timeline.isEmpty()) {
            double typeAt = timeline.getLast().start();
            for (MancheIntro.Timed t : timeline) if (t.shot().kind().equals(MancheIntro.TASK)) typeAt = t.start();
            typeAt = Math.max(shift, Math.min(typeAt + 0.5, Math.max(0, end - 3)));
            int ticks = (int) Math.round((end - typeAt) * 20);
            at(server, typeAt - shift, () -> Director.cue(server, new ShowCue(Cues.TASK_TYPE, typed, "", "", ticks)));
            CraftMaster.LOG.info("[Réalisation] Consigne tapée à {} s : {}", Math.round((typeAt - shift) * 10) / 10.0, typed);
        }
        at(server, end - shift, () -> finish(server, true));
        fromBlack = false;
        Director.sync(server);
        CraftMaster.LOG.info("[Réalisation] Intro de la manche {} lancée", m.id);
        int count = timeline.size() - first;
        return "Intro de « " + m.name + " » lancée" + (first > 0 ? " depuis le plan " + (first + 1) + " (sans musique)" : "")
                + " (" + count + " plan" + (count > 1 ? "s" : "") + ", " + CameraPath.seconds(Math.round((end - shift) * 10) / 10.0) + ")" + (problems.isEmpty() ? "." : " — ⚠ " + String.join(" ; ", problems));
    }

    /** The intro's music cue as a sound id for the clients ("" = the show's theme). */
    static String soundOf(String music) {
        return switch (music) {
            case "theme" -> "";
            case MancheIntro.INTRO_MUSIC -> CraftMaster.id("show.intro").toString();
            default -> music;
        };
    }

    /** What the intro types at the bottom: the round's task, when it is revealed with the round; "" otherwise. */
    static String typedTask(Manche m) {
        if (!m.intro.typedTask || m.task == null || m.task.isEmpty() || !m.task.revealOnStart) return "";
        String line = fr.lordfinn.craftmaster.task.TaskMarkup.typedLine(m.task.text, 110);
        return line.isEmpty() ? m.task.title.strip() : line;
    }

    /** "Manche 2 · Partie 1" */
    private static String subtitle(Manche m) {
        CraftMasterData data = CraftMaster.data();
        Season s = data == null ? null : data.activeSeason();
        if (s != null) for (Partie p : s.parties.values()) if (p.manches.contains(m)) return "Manche " + p.number(m);
        return "";
    }

    /** What goes on air for one shot, or null (written in {@code problems}). */
    private static Director.ShotRequest request(MinecraftServer server, Manche m, MancheIntro.Shot s, String shotEffect, List<String> problems, int index) {
        int transition;
        try {
            transition = s.transition().isEmpty() ? BroadcastNetwork.CROSSFADE : BroadcastActions.parseTransition(s.transition());
        } catch (Actions.Invalid e) {
            transition = BroadcastNetwork.CROSSFADE;
        }
        // The first shot comes out of whatever was on air: a dip to black reads better than a dissolve
        if (index == 0 && s.transition().isEmpty()) transition = fromBlack ? BroadcastNetwork.CUT : BroadcastNetwork.FADE;
        String effect = BroadcastActions.effectId(shotEffect);
        // A trial's intro stays where its players arrive: a shot elsewhere is skipped (the one before stays on air),
        // nothing far is loaded then unloaded while the round starts
        String away = IntroZone.check(m, s);
        if (!away.isEmpty()) {
            problems.add("plan " + (index + 1) + " (" + s.ref() + ") sauté : " + away);
            return null;
        }
        switch (s.kind()) {
            case MancheIntro.PATH -> {
                CameraPath p = CameraPaths.get(s.ref());
                if (p == null || p.isEmpty()) {
                    problems.add("plan " + (index + 1) + " : trajet « " + s.ref() + " » introuvable");
                    return null;
                }
                return new Director.ShotRequest(BroadcastNetwork.SHOT_PATH, p.name(), "", transition, effect, List.of(), Director.Source.SEQUENCE);
            }
            case MancheIntro.TASK -> {
                // The arrival point and the envelope waiting there (camera generated the first time)
                TaskFraming.Result r = TaskFraming.camera(server, m);
                if (!r.problem().isEmpty()) problems.add("plan " + (index + 1) + " : " + r.problem());
                CameraSpot c = r.camera() != null ? r.camera() : Lieux.resolve(server, "large", m.id);
                return c == null ? null : Director.ShotRequest.camera(c, transition, effect, Director.Source.SEQUENCE);
            }
            case MancheIntro.FOLLOW -> {
                if (server.getPlayerList().getPlayerByName(s.ref()) == null) {
                    CameraSpot w = Lieux.resolve(server, "large", m.id);
                    if (w == null) {
                        problems.add("plan " + (index + 1) + " : " + s.ref() + " n'est pas connecté");
                        return null;
                    }
                    return Director.ShotRequest.camera(w, transition, effect, Director.Source.SEQUENCE);
                }
                return Director.ShotRequest.follow(s.ref(), transition, effect, Director.Source.SEQUENCE);
            }
            default -> {
                // Relative to the round's lieu: "2", "large", "ensuri:1", "studio:3"
                CameraSpot c = Lieux.resolve(server, s.ref(), m.id);
                if (c == null) {
                    problems.add("plan " + (index + 1) + " : caméra « " + s.ref() + " » introuvable");
                    return null;
                }
                return Director.ShotRequest.camera(c, transition, effect, Director.Source.SEQUENCE);
            }
        }
    }

    /** End of the intro: the round's wide shot, music stopped; or the round waiting for it is launched. */
    private static void finish(MinecraftServer server, boolean wide) {
        running = null;
        STEPS.clear();
        Actions.MancheStarted start = pending;
        pending = null;
        if (start != null) {
            launch(server, start);
            return;
        }
        if (music) Director.cue(server, new ShowCue(Cues.THEME, "stop", "", "", 0));
        music = false;
        if (wide) Director.wide(server, BroadcastNetwork.CROSSFADE);
        Director.sync(server);
    }

    /** Fade to black, in the dark, the round's title on black (ticks). */
    public static final int BLACK_IN = 12, BLACK_HOLD = 64, BLACK_OUT = 26;
    /** Longest the black waits for the games to be ready after the teleport (ticks), on top of {@link #BLACK_HOLD}. */
    public static final int READY_WAIT = 200;

    /**
     * The round's start after its intro (or at once for a trial: {@code /cm epreuve lancer}): everyone's picture fades
     * to black (players and broadcast), the players are teleported in the dark (into their arena copies) and the
     * broadcast cuts to the round's wide shot, the round's name is typed on black; the picture opens on them with
     * the horn and the timer once every game with the mod has received and drawn what it will show
     * ({@link fr.lordfinn.craftmaster.trial.kit.LaunchGate#check}), at most {@link #READY_WAIT} ticks later.
     */
    public static void launch(MinecraftServer server, Actions.MancheStarted start) {
        boolean withMusic = music;
        music = false;
        Director.cue(server, new ShowCue(Cues.CLEAR, "", "", "", 0));
        // The hold sent is the longest wait: the picture opens earlier, when everyone is ready (BLACKOUT_OPEN)
        int maxHold = BLACK_HOLD + READY_WAIT;
        Director.cue(server, new ShowCue(Cues.BLACKOUT, start.name(), "Manche " + start.number(),
                BLACK_IN + "," + maxHold + "," + BLACK_OUT, BLACK_IN + maxHold + BLACK_OUT));
        // Games without the mod: no black screen, so blindness over the teleport and the usual title
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers())
            if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, ShowCue.TYPE))
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, BLACK_IN + maxHold + 10, 0, false, false));
        launching = true;
        at(server, BLACK_IN / 20.0, () -> {
            if (withMusic) Director.cue(server, new ShowCue(Cues.THEME, "stop", "", "", 0));
            Show.teleport(server, start);
            Director.wide(server, BroadcastNetwork.CUT);
            Director.nudgeBroadcasters(server);
            Director.sync(server);
            // The title is typed meanwhile (at least BLACK_HOLD), then the picture opens once every game is ready
            fr.lordfinn.craftmaster.trial.kit.LaunchGate.check(server, fr.lordfinn.craftmaster.trial.kit.LaunchGate.everyone(server),
                    BLACK_HOLD, BLACK_HOLD + READY_WAIT, "Ouverture de « " + start.name() + " »", () -> reveal(server, start));
        });
    }

    /** The picture opens on the players: horn, timer, the task revealed with the start. */
    private static void reveal(MinecraftServer server, Actions.MancheStarted start) {
        {
            Director.cue(server, new ShowCue(Cues.BLACKOUT_OPEN, "", "", "", BLACK_OUT));
            for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers())
                if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, ShowCue.TYPE))
                    p.removeEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
            launching = false;
            for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers())
                if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, ShowCue.TYPE)) Show.roundTitle(p, start);
            Show.announce(server, start, false);
            Show.roundStarted(server, start);
            // The task revealed with the start (and anything else kept for this moment)
            List<Runnable> kept = new ArrayList<>(AT_REVEAL);
            AT_REVEAL.clear();
            pendingId = null;
            for (Runnable r : kept) r.run();
            // The round's banner comes back with the picture
            Director.sync(server);
            CraftMaster.LOG.info("[Réalisation] Manche « {} » lancée après son intro", start.name());
        }
    }

    /**
     * The shots of a round's intro, in order, cut (no transition): what a broadcast game must have drawn before the
     * intro plays (« lancer l'épreuve » shows each of them behind a black first).
     */
    public static List<Director.ShotRequest> shots(MinecraftServer server, String mancheId) {
        Manche m = Lieux.manche(mancheId);
        List<Director.ShotRequest> out = new ArrayList<>();
        if (m == null || !m.intro.auto || !m.intro.chained) return out;
        List<String> problems = new ArrayList<>();
        int i = 0;
        for (MancheIntro.Shot s : m.intro.shots) {
            Director.ShotRequest r = request(server, m, s, "", problems, i++);
            if (r != null) out.add(new Director.ShotRequest(r.kind(), r.camera(), r.player(), BroadcastNetwork.CUT, "", r.tiles(), r.source()));
        }
        return out;
    }

    /** Where the intro's shots stand ({world, x, z} as strings), for the chunks to load before it. */
    public static List<Object[]> shotPlaces(MinecraftServer server, String mancheId) {
        List<Object[]> out = new ArrayList<>();
        for (Director.ShotRequest r : shots(server, mancheId)) {
            if (r.kind() == BroadcastNetwork.SHOT_CAMERA) {
                fr.lordfinn.craftmaster.camera.CameraSpot c = fr.lordfinn.craftmaster.camera.Cameras.list().get(r.camera());
                if (c != null) out.add(new Object[]{c.world(), c.x(), c.z()});
            } else if (r.kind() == BroadcastNetwork.SHOT_PATH) {
                CameraPath p = CameraPaths.get(r.camera());
                if (p == null || p.isEmpty()) continue;
                double d = Math.max(0.1, p.duration());
                for (double t = 0; t <= d + 1e-6; t += Math.max(1, d / 8)) {
                    CameraPath.Pose pose = p.sample(t);
                    out.add(new Object[]{p.world(), pose.x(), pose.z()});
                }
            }
        }
        return out;
    }

    /** Is a round's intro playing? */
    public static boolean playing() {
        return running != null;
    }

    /** Is a round's launch (black, teleports, title) under way? */
    public static boolean launching() {
        return launching;
    }

    /**
     * Stops the intro (cards cleared, music stopped); true if one was playing. A round waiting for it starts at
     * once (black, teleports, title): stopping an intro skips it, it does not cancel the round.
     */
    static boolean stop(MinecraftServer server) {
        boolean was = running != null;
        if (server == null) {
            STEPS.clear();
            running = null;
            dropPending();
            launching = false;
            return was;
        }
        if (launching) return was;   // the launch's own steps run to the end
        STEPS.clear();
        if (was) {
            Director.cue(server, new ShowCue(Cues.CLEAR, "", "", "", 0));
            finish(server, false);
        }
        running = null;
        return was;
    }

    static void tick(MinecraftServer server) {
        if (STEPS.isEmpty()) return;
        long now = server.getTickCount();
        List<Step> due = new ArrayList<>();
        for (Iterator<Step> it = STEPS.iterator(); it.hasNext(); ) {
            Step s = it.next();
            if (s.due() <= now) {
                due.add(s);
                it.remove();
            }
        }
        due.sort((a, b) -> Long.compare(a.due(), b.due()));
        for (Step s : due) s.run().run();
    }

    /** Starting a round plays its intro by itself when it is set to ({@code intro.auto}). */
    static final class AutoPlay implements ShowEvents.Listener {
        @Override
        public void onMancheStarted(MinecraftServer server, int season, int number, String name) {
            CraftMasterData data = CraftMaster.data();
            Manche m = data == null ? null : data.currentManche();
            // A chained intro already played before the round (playThenStart): not twice
            if (m != null && m.intro.auto && !m.intro.chained && !m.intro.shots.isEmpty()) {
                String line = play(server, m.id);
                CraftMaster.LOG.info("[Réalisation] {}", line);
            }
        }
    }
}
