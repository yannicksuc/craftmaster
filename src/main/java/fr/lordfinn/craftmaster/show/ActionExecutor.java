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
        fr.lordfinn.craftmaster.portrait.Portraits.refreshAll(server);
        for (int s : CraftMaster.data().allSeasons()) Network.broadcast(server, s);
    }

    private static void play(MinecraftServer server, Actions.Effect effect, List<String> lines) {
        switch (effect) {
            case Actions.ScoreGiven s -> Show.announceScore(server, s);
            case Actions.MancheStarted m -> {
                int moved = Show.startManche(server, m);
                if (!m.tp().isEmpty()) lines.add(moved + " joueur(s) téléporté(s) sur " + m.tp().size() + " point(s).");
            }
            case Actions.MancheEnded m -> Show.endManche(server, m.name());
            case Actions.TimerStopped t -> lines.add(Show.stopTimer(server) ? "Chrono arrêté." : "Aucun chrono ne tournait.");
            case Actions.Published p -> {
                Show.announcePublished(server);
                ShowEvents.onPublished(server, p.season());
            }
            case Actions.RevealStarted r -> Reveal.start(server, r.season());
            case Actions.RevealStopped r -> lines.add(Reveal.stop(server, true) ? "Révélation interrompue." : "Aucune révélation en cours.");
            case Actions.CurtainMoved c -> {
                String error = StageControl.moveCurtain(server, c.name(), c.open());
                if (error != null) lines.add("⚠ " + error);
            }
            case Actions.ScenePlaced s -> {
                String error = StageControl.placeScene(server, s.name(), previousScene);
                if (error != null) lines.add("⚠ " + error);
                else previousScene = s.name();
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
