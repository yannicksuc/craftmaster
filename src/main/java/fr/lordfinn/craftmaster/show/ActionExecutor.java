package fr.lordfinn.craftmaster.show;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs a list of show actions (schema: docs/design/emission.md §4) for an organiser: the voice control and the
 * commands both come here, so they behave the same and are undone the same way. The data part is one undoable
 * change ({@link Actions}); then the world follows (titles, teleports, timer, curtains, sceneries, reveal) and the
 * busts and ranking screens are refreshed.
 */
public final class ActionExecutor {

    /** One French line per action done (or the error), and whether the whole list was done. */
    public record Result(List<String> lines, boolean ok) {}

    private ActionExecutor() {}

    /** Entry point of the voice control: checks that {@code admin} is an organiser, then runs the actions. */
    public static Result execute(ServerPlayer admin, List<Map<String, Object>> actions) {
        if (!Permissions.isAdmin(admin)) return new Result(List.of("Réservé aux organisateurs."), false);
        return execute(admin.level().getServer(), actions);
    }

    /** Runs the actions (the caller has checked the permission). */
    public static Result execute(MinecraftServer server, List<Map<String, Object>> actions) {
        CraftMasterData data = CraftMaster.data();
        if (data == null) return new Result(List.of("Données CraftMaster non chargées."), false);
        Actions.Outcome o = Actions.apply(data, actions);
        List<String> lines = new ArrayList<>(o.lines());
        if (o.changed()) refresh(server);
        if (o.ok()) for (Actions.Effect e : o.effects()) play(server, e, lines);
        return new Result(lines, o.ok());
    }

    /** For commands: runs the actions and prints the lines to the source (green when done, red otherwise). */
    public static int execute(CommandSourceStack source, List<Map<String, Object>> actions) {
        Result r = execute(source.getServer(), actions);
        for (String line : r.lines()) {
            if (r.ok()) source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GREEN), true);
            else source.sendFailure(Component.literal(line));
        }
        return r.ok() ? 1 : 0;
    }

    /** Busts, labels and open ranking screens follow the data. */
    public static void refresh(MinecraftServer server) {
        Busts.updateAll(server);
        // Labels only: which player a portrait shows changes on publish, with its animation (ShowEvents)
        fr.lordfinn.craftmaster.portrait.Portraits.refreshLabels(server);
        for (int s : CraftMaster.data().allSeasons()) Network.broadcast(server, s);
        Network.broadcastActiveSeason(server);
        // chantier H: open results sheets follow
        fr.lordfinn.craftmaster.trial.TrialNetwork.refreshSheets(server);
        // Open score entry screens update in place
        fr.lordfinn.craftmaster.network.ScoreEntryNetwork.refresh(server);
        // Task cards and the J screen follow the task texts (chantier D)
        fr.lordfinn.craftmaster.task.Tasks.sync(server);
    }

    private static void play(MinecraftServer server, Actions.Effect effect, List<String> lines) {
        switch (effect) {
            case Actions.ScoreGiven s -> {
                Show.announceScore(server, s);
                ShowEvents.onScoreGiven(server, s.season(), s.player(), s.delta(), s.manche());
            }
            case Actions.MancheStarted m -> {
                // A chained intro plays first: the round itself starts at its end (black, teleports, title, reveal)
                String chained = fr.lordfinn.craftmaster.broadcast.MancheIntros.playThenStart(server, m);
                if (chained != null) {
                    lines.add(chained);
                    return;
                }
                int moved = Show.startManche(server, m);
                Show.roundStarted(server, m);
                if (!m.tp().isEmpty()) lines.add(moved + " joueur(s) téléporté(s) sur " + m.tp().size() + " point(s).");
            }
            case Actions.MancheEnded m -> {
                Show.endManche(server, m.name());
                fr.lordfinn.craftmaster.studio.TimerTabletBlockEntity.onRoundEnd(server);
            }
            case Actions.TimerGo g -> lines.add(fr.lordfinn.craftmaster.task.RoundStart.goNow(server)
                    ? "Chrono lancé, joueurs libérés." : "Aucune manche n'attend son départ.");
            case Actions.ReplayFlagged f -> lines.add(fr.lordfinn.craftmaster.replay.ReplayMoments.flag(null, f.text()));
            case Actions.TimerStopped t -> {
                boolean held = fr.lordfinn.craftmaster.task.RoundStart.waiting();
                fr.lordfinn.craftmaster.task.RoundStart.clear(server);
                lines.add(Show.stopTimer(server) ? "Chrono arrêté." : held ? "Départ annulé, joueurs libérés." : "Aucun chrono ne tournait.");
                fr.lordfinn.craftmaster.studio.TimerTabletBlockEntity.onTimerStopped(server);
            }
            case Actions.Published p -> {
                Show.announcePublished(server);
                ShowEvents.onPublished(server, p.season());
            }
            case Actions.RevealStarted r -> {
                Reveal.start(server, r.season());
                ShowEvents.onRevealStarted(server, r.season());
            }
            case Actions.RevealStopped r -> lines.add(Reveal.stop(server, true) ? "Révélation interrompue." : "Aucune révélation en cours.");
            case Actions.CurtainMoved c -> {
                String error = StageControl.moveCurtain(server, c.name(), c.open());
                if (error != null) lines.add("⚠ " + error);
            }
            // Studio set (chantier F)
            case Actions.SpotlightOrder o -> lines.add(switch (o.order()) {
                case "follow" -> fr.lordfinn.craftmaster.studio.Spotlights.follow(server, o.player());
                case "stop" -> fr.lordfinn.craftmaster.studio.Spotlights.stop(server);
                default -> fr.lordfinn.craftmaster.studio.Spotlights.setLit(server, o.order().equals("on"));
            });
            case Actions.ScenePlaced s -> {
                String error = StageControl.placeScene(server, s.name(), previousScene);
                if (error != null) lines.add("⚠ " + error);
                else previousScene = s.name();
            }
            // chantier H: judging (announces, stopwatches, buzzers)
            case Actions.ResultRecorded _, Actions.StopwatchStarted _, Actions.StopwatchStopped _, Actions.StopwatchReset _,
                 Actions.BuzzersReset _ -> {
                fr.lordfinn.craftmaster.trial.Judging.play(server, effect, lines);
                // The replay's key moments (results, disqualifications, times)
                fr.lordfinn.craftmaster.replay.ReplayMoments.onEffect(server, effect);
            }
            // TV direction (chantier E): cameras, overlay, credits
            case Actions.Broadcast b -> fr.lordfinn.craftmaster.broadcast.Director.play(server, b.action(), lines);
            // Screen effects (fx/): the broadcast view, forced on players
            case Actions.ScreenEffect f -> {
                try {
                    lines.add(fr.lordfinn.craftmaster.fx.ScreenEffectsServer.apply(server, f.target(), f.effect(), f.seconds(), f.fadeTicks()));
                } catch (IllegalArgumentException e) {
                    lines.add("⚠ " + e.getMessage());
                }
            }
            // Tasks, envelopes (chantier D)
            case Actions.TaskEffect t -> {
                // The task revealed with the round's start waits for its real start (after a chained intro)
                if (t instanceof Actions.TaskRevealed r && !r.announce()
                        && fr.lordfinn.craftmaster.broadcast.MancheIntros.atReveal(r.manche(), () -> fr.lordfinn.craftmaster.task.Tasks.play(server, t, new ArrayList<>()))) {
                    lines.add("La tâche sera révélée quand l'image s'ouvre sur les candidats.");
                } else {
                    fr.lordfinn.craftmaster.task.Tasks.play(server, t, lines);
                }
            }
        }
    }

    /** Scenery currently standing at the anchor, to clear before placing another one. */
    private static String previousScene;

    /** Called when the server starts: the scenery recorded in the file is the one standing. */
    public static void onServerStarted() {
        previousScene = CraftMaster.data().live().scene;
    }
}
