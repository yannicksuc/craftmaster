package fr.lordfinn.craftmaster.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WordArgumentTest {

    private static String parse(String input, StringReader[] out) throws CommandSyntaxException {
        StringReader r = new StringReader(input);
        String v = WordArgument.word().parse(r);
        out[0] = r;
        return v;
    }

    @Test
    void readsAccentsUpToTheNextSpace() throws CommandSyntaxException {
        StringReader[] r = new StringReader[1];
        assertEquals("Défi", parse("Défi LordFinn 3", r));
        assertEquals(" LordFinn 3", r[0].getRemaining());
    }

    @Test
    void readsQuotedNamesWithSpaces() throws CommandSyntaxException {
        StringReader[] r = new StringReader[1];
        assertEquals("Le Pont", parse("\"Le Pont\" 4", r));
        assertEquals(" 4", r[0].getRemaining());
    }

    @Test
    void escapeQuotesOnlyWhenNeeded() throws CommandSyntaxException {
        assertEquals("Défi", WordArgument.escape("Défi"));
        assertEquals("\"Le Pont\"", WordArgument.escape("Le Pont"));
        StringReader[] r = new StringReader[1];
        // What the suggestions type must parse back to the same name
        for (String name : new String[]{"Défi", "Le Pont", "a\"b c"})
            assertEquals(name, parse(WordArgument.escape(name), r));
    }
}
