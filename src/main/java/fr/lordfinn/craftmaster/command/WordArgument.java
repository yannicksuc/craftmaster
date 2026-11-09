package fr.lordfinn.craftmaster.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.Collection;
import java.util.List;

/**
 * A task or player name: everything up to the next space, accents included ("Défi"), like the plugin's Bukkit
 * arguments. A name with spaces can be written in quotes. (Brigadier's own string argument refuses accents unquoted.)
 */
public final class WordArgument implements ArgumentType<String> {

    private static final List<String> EXAMPLES = List.of("Pont", "Défi", "\"Le Pont\"");

    public static WordArgument word() {
        return new WordArgument();
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && StringReader.isQuotedStringStart(reader.peek())) return reader.readQuotedString();
        int start = reader.getCursor();
        while (reader.canRead() && reader.peek() != ' ') reader.skip();
        return reader.getString().substring(start, reader.getCursor());
    }

    /** How to type a value back: quoted only when it contains a space or starts with a quote. */
    public static String escape(String value) {
        if (value.isEmpty() || value.indexOf(' ') >= 0 || StringReader.isQuotedStringStart(value.charAt(0)))
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        return value;
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
