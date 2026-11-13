package fr.lordfinn.craftmaster.task;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaskMarkupTest {

    @Test
    void blocks() {
        TaskMarkup.Doc d = TaskMarkup.parse("""
                # Tâche 4 : physique

                Construisez le pont.
                Vous avez {temps}.
                ---
                > Psst… sous la table
                [tampon: URGENT]
                !règle Le plus rapide gagne.
                [signature: La Régie]
                """, Map.of("time", "5:00"));
        List<TaskMarkup.Kind> kinds = d.blocks().stream().map(TaskMarkup.Block::kind).toList();
        assertEquals(List.of(TaskMarkup.Kind.HEADING, TaskMarkup.Kind.GAP, TaskMarkup.Kind.LINE, TaskMarkup.Kind.LINE,
                TaskMarkup.Kind.SEPARATOR, TaskMarkup.Kind.NOTE, TaskMarkup.Kind.STAMP, TaskMarkup.Kind.RULE), kinds);
        assertEquals("Tâche 4 : physique", d.blocks().get(0).plain());
        assertEquals("Vous avez 5:00.", d.blocks().get(3).plain());
        assertEquals("Psst… sous la table", d.blocks().get(5).plain());
        assertEquals("URGENT", d.blocks().get(6).plain());
        assertEquals("Le plus rapide gagne.", d.blocks().get(7).plain());
        assertEquals("La Régie", d.signature());
    }

    @Test
    void inlineStyles() {
        List<TaskMarkup.Span> s = TaskMarkup.parse("a **gras** __sou__ ~~barré~~ {rouge}rouge{/} fin {inconnu}", Map.of())
                .blocks().getFirst().spans();
        assertEquals("a ", s.get(0).text());
        assertEquals(new TaskMarkup.Span("gras", TaskMarkup.BOLD, "", 0), s.get(1));
        assertEquals(TaskMarkup.UNDERLINE, s.get(3).style());
        assertEquals(TaskMarkup.STRIKE, s.get(5).style());
        assertEquals(new TaskMarkup.Span("rouge", 0, "red", 0), s.get(7));
        assertEquals(" fin {inconnu}", s.get(8).text(), "an unknown {word} stays as typed");
    }

    @Test
    void variables() {
        assertEquals("Bravo Ensuri (Rouges), 2:30", TaskMarkup.substitute("Bravo {joueur} ({equipe}), {temps}",
                Map.of("player", "Ensuri", "team", "Rouges", "time", "2:30")));
    }

    @Test
    void secretsNeverLeak() {
        String t = "Début [secret]la suite cachée[/secret] milieu [secret]fin[/secret]";
        String viewer = TaskMarkup.redact(t, 0, false);
        assertFalse(viewer.contains("suite") || viewer.contains("fin"), viewer);
        assertEquals("Début [secret:15] milieu [secret:3]", viewer);
        assertEquals("Début la suite cachée milieu [secret:3]", TaskMarkup.redact(t, 1, false));
        assertEquals("Début la suite cachée milieu fin", TaskMarkup.redact(t, 2, false));
        assertEquals("Début la suite cachée milieu [secret]fin[/secret]", TaskMarkup.redact(t, 1, true));
        assertEquals(2, TaskMarkup.secretCount(t));
        // Unclosed: hidden to the end
        assertEquals("a [secret:6]", TaskMarkup.redact("a [secret]b c de", 0, false));
    }

    @Test
    void secretSpans() {
        List<TaskMarkup.Span> s = TaskMarkup.parse("a [secret:4] b", Map.of()).blocks().getFirst().spans();
        assertEquals(TaskMarkup.HIDDEN, s.get(1).secret());
        assertEquals(4, s.get(1).text().length());
        List<TaskMarkup.Block> admin = TaskMarkup.parse("x [secret]deux\nlignes[/secret] y", Map.of()).blocks();
        assertEquals(TaskMarkup.HIDDEN_READABLE, admin.get(0).spans().get(1).secret());
        assertEquals(TaskMarkup.HIDDEN_READABLE, admin.get(1).spans().get(0).secret(), "a secret goes on over lines");
        assertEquals(TaskMarkup.VISIBLE, admin.get(1).spans().get(1).secret());
    }

    @Test
    void plainText() {
        assertEquals("Titre\nLigne gras\n\nRègle", TaskMarkup.plain("# Titre\nLigne **gras**\n\n!règle Règle"));
    }

    @Test
    void typedLineIsTheFirstSentenceWithoutSecrets() {
        assertEquals("Mettez l'œuf le plus haut possible.",
                TaskMarkup.typedLine("# Tâche 4\n[tampon: URGENT]\nMettez l'œuf le plus **haut** possible.\n!règle Le plus haut gagne.", 110));
        assertEquals("Trouvez le", TaskMarkup.typedLine("Trouvez le [secret]trésor caché[/secret] avant tout le monde.", 110));
        assertEquals("Le plus haut gagne.", TaskMarkup.typedLine("# Titre seul\n!règle Le plus haut gagne.", 110));
        String cut = TaskMarkup.typedLine("Construisez la plus grande tour possible avec uniquement des blocs de terre en moins de dix minutes", 40);
        assertTrue(cut.endsWith("…") && cut.length() <= 40, cut);
        assertEquals("", TaskMarkup.typedLine("", 110));
    }
}
