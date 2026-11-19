package fr.lordfinn.craftmaster.broadcast;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Contestant;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Cues;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowCue;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowState;
import fr.lordfinn.craftmaster.camera.CameraList;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.camera.Cameras;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Opening and closing credits, played on the server so every broadcast game sees the same thing at the same time
 * with the opening fanfare ({@code craftmaster:show.ouverture}) or the closing theme ({@code craftmaster:show.theme}).
 * <p>Opening: the studio cameras one after the other in cross-fades under the CraftMaster title and the season,
 * then each contestant (followed if connected, wide shot otherwise) with their name card, then the game's name.
 * Closing: the credits roll (host, cast, published ranking) over slow cross-fades between the cameras.
 */
public final class Credits {

    private record Step(long due, Runnable run) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static String running = "";

    /** Lengths (ticks): one studio shot, one contestant, the closing roll. */
    static final int STUDIO_SHOT = 70, CONTESTANT = 80, MAX_STUDIO_SHOTS = 5, ROLL = 30 * 20;

    private Credits() {}

    /** "" when no credits run, else "intro" or "outro". */
    public static String running() {
        return running;
    }

    private static void at(MinecraftServer server, long delay, Runnable run) {
        STEPS.add(new Step(server.getTickCount() + delay, run));
    }

    /** Starts the opening ({@code intro}) or closing credits; returns the line for the organiser. */
    static String start(MinecraftServer server, boolean intro) {
        stop(server);
        MancheIntros.stop(server);
        running = intro ? "intro" : "outro";
        ShowState state = Director.build(server);
        Director.cue(server, new ShowCue(Cues.CLEAR, "", "", "", 0));
        // The opening on its own fanfare (note blocks, 17 s, 112 BPM like the theme), the closing on the theme
        Director.cue(server, new ShowCue(Cues.THEME, "play", intro ? CraftMaster.id("show.ouverture").toString() : "", "", 0));
        List<CameraSpot> cams = studioShots();
        long t;
        if (intro) {
            t = 0;
            String season = state.seasonName().isEmpty() ? "" : state.seasonName();
            String sub = state.partieName().isEmpty() ? season : season + " — " + state.partieName();
            int studio = Math.max(2, Math.min(cams.size(), MAX_STUDIO_SHOTS));
            at(server, 15, () -> Director.cue(server, new ShowCue(Cues.TITLE_CARD, "CraftMaster", sub, "", studio * STUDIO_SHOT - 25)));
            for (int i = 0; i < studio; i++) {
                CameraSpot cam = cams.isEmpty() ? null : cams.get(i % cams.size());
                if (cam != null) at(server, t, () -> Director.cut(server, cam, BroadcastNetwork.CROSSFADE));
                t += STUDIO_SHOT;
            }
            List<Contestant> cast = state.contestants();
            for (int i = 0; i < cast.size(); i++) {
                Contestant c = cast.get(i);
                String index = (i + 1) + "/" + cast.size();
                at(server, t, () -> {
                    if (server.getPlayerList().getPlayerByName(c.name()) != null) Director.follow(server, c.name(), BroadcastNetwork.CROSSFADE);
                    else Director.wide(server, BroadcastNetwork.CROSSFADE);
                    Director.cue(server, new ShowCue(Cues.CONTESTANT, index, "", c.name(), CONTESTANT - 6));
                });
                t += CONTESTANT;
            }
            String closing = state.mancheName().isEmpty() ? "Que le jeu commence !" : "Manche " + state.mancheNumber() + " — " + state.mancheName();
            at(server, t, () -> {
                Director.wide(server, BroadcastNetwork.CROSSFADE);
                Director.cue(server, new ShowCue(Cues.TITLE_CARD, closing, state.partieName(), "", 70));
            });
            t += 80;
        } else {
            at(server, 0, () -> {
                Director.wide(server, BroadcastNetwork.CROSSFADE);
                Director.cue(server, new ShowCue(Cues.CREDITS, "", "", "", ROLL));
            });
            int shots = Math.max(1, ROLL / 120);
            for (int i = 1; i < shots && !cams.isEmpty(); i++) {
                CameraSpot cam = cams.get(i % cams.size());
                at(server, i * 120L, () -> Director.cut(server, cam, BroadcastNetwork.CROSSFADE));
            }
            t = ROLL + 20;
        }
        at(server, t, () -> {
            running = "";
            Director.sync(server);
        });
        Director.sync(server);
        CraftMaster.LOG.info("[Réalisation] Générique {} lancé", intro ? "d'ouverture" : "de fin");
        return intro ? "Générique d'ouverture lancé (" + Math.round(t / 20.0) + " s)." : "Générique de fin lancé (" + Math.round(t / 20.0) + " s).";
    }

    /** The cameras for the studio shots: the wide shot first, then the others in number order. */
    private static List<CameraSpot> studioShots() {
        CameraList studio = Cameras.list().group(CameraSpot.STUDIO);
        List<CameraSpot> all = CameraList.sorted(studio.all());
        CameraSpot wide = studio.wide();
        if (wide != null) {
            all.remove(wide);
            all.addFirst(wide);
        }
        return all;
    }

    /** Stops the credits (cards cleared, theme stopped); true if they were running. */
    static boolean stop(MinecraftServer server) {
        boolean was = !running.isEmpty();
        STEPS.clear();
        running = "";
        if (was && server != null) {
            Director.cue(server, new ShowCue(Cues.CLEAR, "", "", "", 0));
            Director.cue(server, new ShowCue(Cues.THEME, "stop", "", "", 0));
            Director.sync(server);
        }
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
}
