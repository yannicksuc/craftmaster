package fr.lordfinn.craftmaster.show;

import fr.lordfinn.craftmaster.data.BlockSpot;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Partie;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.data.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The action schema of the voice control (docs/design/emission.md §4), applied without a server. */
class ActionsTest {

    @TempDir
    Path dir;

    private CraftMasterData data;
    /** Number of the first season created ("Saison 4" is only its name). */
    private static final int S = 1;

    @BeforeEach
    void setUp() {
        data = new CraftMasterData(dir.resolve("config.yml"), LoggerFactory.getLogger("test"));
        data.load(null);
    }

    private static Map<String, Object> a(String type, Object... fields) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        for (int i = 0; i < fields.length; i += 2) m.put((String) fields[i], fields[i + 1]);
        return m;
    }

    @SafeVarargs
    private Actions.Outcome run(Map<String, Object>... actions) {
        return Actions.apply(data, List.of(actions));
    }

    /** A season with its cast and three rounds, as prepared before the show. */
    private void prepare() {
        Actions.Outcome o = run(
                a("season.create", "name", "Saison 4"),
                a("player.add", "pseudo", "LordFinn", "aliases", List.of("Finn")),
                a("player.add", "pseudo", "Ensuri", "aliases", "Ensu, Enzo"),
                a("player.add", "pseudo", "Cyllu"),
                a("manche.create", "name", "Le Pont", "aliases", List.of("pont")),
                a("manche.create", "name", "La Tour", "timer", 180.0),
                a("manche.create", "name", "Finale"));
        assertTrue(o.ok(), o.lines().toString());
    }

    private Season season() {
        return data.activeSeason();
    }

    private Partie partie() {
        return season().activePartie();
    }

    @Test
    void preparationCreatesAnInvisibleGame() {
        prepare();
        assertEquals("Saison 4", season().name);
        assertEquals(1, season().parties.size(), "« Partie 1 » created on its own");
        assertEquals(List.of("Le Pont", "La Tour", "Finale"), partie().manches.stream().map(r -> r.name).toList());
        assertEquals(180, partie().manches.get(1).timer, "JSON numbers come as doubles");
        assertEquals(List.of("Ensu", "Enzo"), season().players.get("Ensuri").aliases, "a comma list works too");
        assertEquals(1, data.history().size(), "the whole list is one undoable change");
    }

    @Test
    void aRoundIsStartedThenScoredWithoutNamingIt() {
        prepare();
        Actions.Outcome o = run(a("manche.start", "manche", "next"));
        assertTrue(o.ok(), o.lines().toString());
        assertEquals("Le Pont", data.currentManche().name);
        assertEquals(Partie.EN_COURS, partie().status);
        Actions.MancheStarted started = (Actions.MancheStarted) o.effects().getFirst();
        assertEquals(1, started.number());
        assertEquals(List.of("LordFinn", "Ensuri", "Cyllu"), started.cast());

        o = run(a("score.add", "player", "Finn", "points", 3), a("score.add", "player", "Ensuri", "points", 2));
        assertTrue(o.ok(), o.lines().toString());
        assertEquals(List.of(new Actions.ScoreGiven(S, "LordFinn", 3, "Le Pont"), new Actions.ScoreGiven(S, "Ensuri", 2, "Le Pont")), o.effects());
        assertEquals(3, data.total(S, "LordFinn"), "a nickname reaches the player");
        assertTrue(data.live().lastScoreAt > 0);

        o = run(a("manche.start", "manche", "suivante"), a("score.set", "manche", "current", "player", "Cyllu", "value", 7));
        assertTrue(o.ok(), o.lines().toString());
        assertEquals("La Tour", data.currentManche().name);
        assertEquals(180, ((Actions.MancheStarted) o.effects().getFirst()).timer());
        assertEquals(7, partie().score("la-tour", "Cyllu"));
    }

    @Test
    void roundsAreFoundByIdNameOrNickname() {
        prepare();
        assertTrue(run(a("score.set", "manche", "pont", "player", "Cyllu", "value", 1)).ok());
        assertTrue(run(a("score.set", "manche", "LA TOUR", "player", "Cyllu", "value", 2)).ok());
        assertTrue(run(a("score.set", "manche", "finale", "player", "Cyllu", "value", 3)).ok());
        assertEquals(6, data.total(S, "Cyllu"));
    }

    @Test
    void anInvalidActionCancelsTheWholeList() {
        prepare();
        int before = data.history().size();
        Actions.Outcome o = run(a("score.add", "manche", "pont", "player", "LordFinn", "points", 3),
                a("score.add", "manche", "Inconnue", "player", "LordFinn", "points", 3));
        assertFalse(o.ok());
        assertTrue(o.lines().getFirst().startsWith("Action 2 (score.add) : manche « Inconnue » introuvable"), o.lines().toString());
        assertEquals("Rien n'a été modifié.", o.lines().getLast());
        assertTrue(o.effects().isEmpty());
        assertEquals(0, data.total(S, "LordFinn"));
        assertEquals(before, data.history().size());
    }

    @Test
    void clearFrenchMessages() {
        prepare();
        assertEquals(List.of("aucune manche en cours dans Saison 4."), run(a("score.add", "player", "LordFinn", "points", 1)).lines());
        assertEquals(List.of("champ « player » manquant."), run(a("score.add", "manche", "pont", "points", 1)).lines());
        assertEquals(List.of("« points » doit être un nombre entier (reçu : trois)."),
                run(a("score.add", "manche", "pont", "player", "Cyllu", "points", "trois")).lines());
        assertEquals(List.of("action inconnue « dance »."), run(a("dance")).lines());
        assertEquals(List.of("rideau « stage » inconnu (rideaux : aucun)."), run(a("curtain.open")).lines());
        assertEquals(List.of("précisez la saison à supprimer (« season »)."), run(a("season.delete")).lines());
        assertEquals(List.of("Cyllu n'a pas de score pour « Le Pont »."), run(a("score.remove", "manche", "pont", "player", "Cyllu")).lines());
    }

    @Test
    void undoTakesBackAWholeListAndCanBeFollowedByAFix() {
        prepare();
        run(a("manche.start", "manche", "pont"));
        run(a("score.add", "player", "LordFinn", "points", 3), a("score.add", "player", "Cyllu", "points", 1));
        Actions.Outcome o = run(a("undo", "count", 1), a("score.add", "player", "LordFinn", "points", 4));
        assertTrue(o.ok(), o.lines().toString());
        assertTrue(o.lines().getFirst().startsWith("Annulé : +3 pour LordFinn"), o.lines().toString());
        assertEquals(4, data.total(S, "LordFinn"));
        assertEquals(0, data.total(S, "Cyllu"));

        assertFalse(run(a("say", "text", "ok"), a("undo")).ok(), "undo only first");
        o = run(a("undo"), a("undo"));
        assertFalse(o.ok());
        assertEquals(0, data.total(S, "LordFinn"), "the first undo was done before the error");
    }

    @Test
    void redoGivesBackWhatWasUndone() {
        prepare();
        run(a("manche.start", "manche", "pont"));
        run(a("score.add", "player", "LordFinn", "points", 3));
        assertTrue(run(a("undo")).ok());
        assertEquals(0, data.total(S, "LordFinn"));
        Actions.Outcome o = run(a("redo"));
        assertTrue(o.ok(), o.lines().toString());
        assertTrue(o.lines().getFirst().startsWith("Refait : +3 pour LordFinn"), o.lines().toString());
        assertEquals(3, data.total(S, "LordFinn"));
        assertEquals(List.of("Rien à refaire."), run(a("redo")).lines());
        assertFalse(run(a("say", "text", "ok"), a("redo")).ok(), "redo only first");
    }

    @Test
    void sayChangesNothing() {
        Actions.Outcome o = run(a("say", "text", "Qui ça ? Ensuri ou Cyllu ?"));
        assertTrue(o.ok());
        assertFalse(o.changed());
        assertEquals(List.of("Qui ça ? Ensuri ou Cyllu ?"), o.lines());
        assertTrue(data.history().isEmpty());
    }

    @Test
    void publishAndRevealProduceEffects() {
        prepare();
        assertFalse(run(a("reveal.start")).ok(), "nothing to reveal yet");
        run(a("score.set", "manche", "pont", "player", "Cyllu", "value", 5));
        Actions.Outcome o = run(a("scores.publish"));
        assertEquals(List.of(new Actions.Published(S)), o.effects());
        assertEquals(5, data.publishedTotal(S, "Cyllu"));
        assertEquals(List.of(new Actions.RevealStarted(S)), run(a("reveal.start", "season", S)).effects());
    }

    @Test
    void castManagement() {
        prepare();
        assertFalse(run(a("player.add", "pseudo", "Arreaubaz", "aliases", List.of("Finn"))).ok(), "a nickname already taken");
        assertTrue(run(a("player.update", "pseudo", "Ensu", "newPseudo", "Ensuri_", "aliases", List.of("Enzo"))).ok());
        assertEquals(List.of("LordFinn", "Ensuri_", "Cyllu"), List.copyOf(season().players.keySet()), "renamed in place");
        run(a("score.set", "manche", "pont", "player", "Cyllu", "value", 5), a("scores.publish"));
        assertTrue(run(a("player.update", "pseudo", "Cyllu", "newPseudo", "Cyllu2")).ok());
        assertEquals(5, data.total(S, "Cyllu2"), "scores follow a rename");
        assertEquals(5, data.publishedTotal(S, "Cyllu2"), "published ones too");
        assertTrue(run(a("player.remove", "pseudo", "Cyllu2")).ok());
        assertTrue(season().players.get("Cyllu2").archived, "kept for the ranking while they have points");
        assertTrue(run(a("player.remove", "pseudo", "LordFinn")).ok());
        assertFalse(season().players.containsKey("LordFinn"));
        assertTrue(run(a("score.add", "manche", "pont", "player", "Notch", "points", 1)).ok());
        assertTrue(season().players.containsKey("Notch"), "someone new scored joins the cast");
    }

    @Test
    void roundOrderAndInsertion() {
        prepare();
        run(a("manche.create", "name", "Bonus", "after", "pont"));
        assertEquals(List.of("Le Pont", "Bonus", "La Tour", "Finale"), partie().manches.stream().map(r -> r.name).toList());
        run(a("manche.archive", "manche", "Bonus", "archived", true));
        run(a("manche.update", "manche", "Finale", "order", 1));
        assertEquals(List.of("Finale", "Bonus", "Le Pont", "La Tour"), partie().manches.stream().map(r -> r.name).toList(),
                "archived rounds keep their slot");
        assertEquals(List.of("Finale", "Le Pont", "La Tour"), partie().activeManches().stream().map(r -> r.name).toList());
    }

    @Test
    void nextSkipsArchivedRoundsAndStopsAtTheEnd() {
        prepare();
        run(a("manche.archive", "manche", "La Tour", "archived", true));
        run(a("manche.start", "manche", "next"));
        run(a("manche.start", "manche", "next"));
        assertEquals("Finale", data.currentManche().name);
        assertEquals(List.of("plus de manche après « Finale » dans Partie 1."), run(a("manche.start", "manche", "next")).lines());
        assertTrue(run(a("manche.end")).ok());
        assertNull(data.currentManche());
    }

    @Test
    void renameOntoAnotherRoundNeedsMerge() {
        prepare();
        run(a("score.set", "manche", "pont", "player", "Cyllu", "value", 2), a("score.set", "manche", "Finale", "player", "Cyllu", "value", 3));
        assertFalse(run(a("manche.update", "manche", "pont", "name", "Finale")).ok());
        assertTrue(run(a("manche.update", "manche", "pont", "name", "Finale", "merge", true)).ok());
        assertEquals(5, partie().score("finale", "Cyllu"));
        assertEquals(2, partie().manches.size());
    }

    @Test
    void gamesCanBeCreatedActivatedAndDeleted() {
        prepare();
        assertTrue(run(a("partie.create", "name", "Finale du soir")).ok());
        assertEquals("Finale du soir", partie().name, "the new game is active");
        assertTrue(run(a("manche.create", "name", "Le Pont")).ok(), "names are per game");
        assertNotEquals("le-pont", partie().manches.getFirst().id, "ids stay unique in the season");
        assertTrue(run(a("partie.update", "partie", "Partie 1", "status", "terminée")).ok());
        assertEquals(Partie.TERMINEE, season().parties.get(1).status);
        assertTrue(run(a("partie.activate", "partie", 1)).ok());
        assertEquals(1, partie().id);
        assertTrue(run(a("partie.delete", "partie", "Finale du soir")).ok());
        assertEquals(1, season().parties.size());
        assertTrue(run(a("undo")).ok());
        assertEquals(2, season().parties.size(), "deleting is undoable");
    }

    @Test
    void archivedSeasonsAreReadOnly() {
        prepare();
        run(a("season.archive", "archived", true));
        assertEquals(List.of("Saison 4 est archivée (lecture seule) : désarchivez-la d'abord."),
                run(a("score.add", "manche", "pont", "player", "Cyllu", "points", 1)).lines());
        assertTrue(run(a("season.archive", "archived", false)).ok());
    }

    @Test
    void seasonsByNumberOrName() {
        prepare();
        run(a("season.create", "name", "Hiver"));
        assertEquals("Hiver", season().name);
        assertEquals(2, season().id, "next free number");
        assertTrue(run(a("season.activate", "season", "saison 4")).ok());
        assertEquals(S, season().id);
        assertTrue(run(a("season.update", "season", 2, "hidden", true, "name", "Hiver 2026")).ok());
        assertTrue(data.isHidden(2));
        assertTrue(run(a("season.delete", "season", "Hiver 2026")).ok());
        assertNull(data.season(2));
    }

    @Test
    void stageActionsCheckTheSetUp() {
        prepare();
        data.change("Plateau", () -> {
            data.stage().curtains.put(Stage.MAIN_CURTAIN, new BlockSpot("minecraft:overworld", 0, 64, 0));
            data.stage().curtains.put("fond", new BlockSpot("minecraft:overworld", 0, 64, 9));
            data.stage().scenes.put("Forêt", new Stage.Scene(5, 4, 5));
        });
        assertEquals(List.of(new Actions.CurtainMoved("stage", false)), run(a("curtain.close")).effects());
        assertEquals(List.of(new Actions.CurtainMoved("fond", true)), run(a("curtain.open", "name", "FOND")).effects());
        assertEquals(List.of("pas d'ancre de scène : définissez-la d'abord."), run(a("scene.set", "name", "forêt")).lines());
        data.change("Ancre", () -> { data.stage().anchor = new BlockSpot("minecraft:overworld", 0, 64, 3); });
        Actions.Outcome o = run(a("scene.set", "name", "forêt"));
        assertEquals(List.of(new Actions.ScenePlaced("Forêt")), o.effects());
        assertEquals("Forêt", data.live().scene);

        assertTrue(run(a("manche.update", "manche", "pont", "onStage", true, "scene", "Forêt")).ok());
        o = run(a("manche.start", "manche", "pont"));
        assertEquals(new Actions.ScenePlaced("Forêt"), o.effects().getFirst(), "a stage round places its scenery");
        assertFalse(run(a("manche.update", "manche", "pont", "scene", "Plage")).ok(), "unknown scenery");
    }

    @Test
    void tpPointsAreAddedAndCleared() {
        prepare();
        assertTrue(run(a("manche.tp.add", "manche", "pont", "world", "minecraft:overworld", "x", 1.5, "y", 64, "z", -2.5, "yaw", 90)).ok());
        Manche pont = partie().manche("le-pont");
        assertEquals(1, pont.tp.size());
        assertEquals(90f, pont.tp.getFirst().yaw());
        assertTrue(run(a("manche.tp.clear", "manche", "pont")).ok());
        assertTrue(pont.tp.isEmpty());
    }

    @Test
    void theActionKeyIsAcceptedForTheType() {
        Actions.Outcome o = Actions.apply(data, List.of(Map.of("action", "season.create", "name", "S")));
        assertTrue(o.ok(), o.lines().toString());
    }
}
