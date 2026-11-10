package fr.lordfinn.craftmaster.task;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Reading a Twitch chat anonymously over IRC (no account, no key): log in as {@code justinfanNNNNN}, join the
 * channel, read the PRIVMSG lines, answer the PINGs. Pure logic, used by the broadcast client
 * (fr.lordfinn.craftmaster.client.task.TwitchChat) and tested on its own.
 */
public final class TwitchIrc {

    private TwitchIrc() {}

    public static final String HOST = "irc.chat.twitch.tv";
    public static final int TLS_PORT = 6697;

    /** A chat message: who wrote what in which channel. */
    public record Message(String channel, String user, String text) {}

    /** Anonymous read-only login name accepted by Twitch. */
    public static String anonymousNick(Random r) {
        return "justinfan" + (10000 + r.nextInt(89999));
    }

    /** "LordFinn", "#lordfinn", " https://twitch.tv/LordFinn " → "lordfinn" (empty if invalid). */
    public static String channel(String s) {
        if (s == null) return "";
        String c = s.strip().toLowerCase(Locale.ROOT);
        int slash = c.lastIndexOf('/');
        if (slash >= 0) c = c.substring(slash + 1);
        if (c.startsWith("#")) c = c.substring(1);
        return c.matches("[a-z0-9_]{2,25}") ? c : "";
    }

    /** The lines to send after connecting. */
    public static List<String> login(String nick, String channel) {
        return List.of("NICK " + nick, "JOIN #" + channel);
    }

    /** The answer to a server line, or null ("PING :tmi.twitch.tv" → "PONG :tmi.twitch.tv"). */
    public static String reply(String line) {
        return line != null && line.startsWith("PING") ? "PONG" + line.substring(4) : null;
    }

    /**
     * A chat message from a raw IRC line ({@code :user!user@user.tmi.twitch.tv PRIVMSG #channel :text}, with or
     * without a leading {@code @tags} part), or null for any other line.
     */
    public static Message parse(String line) {
        if (line == null) return null;
        String l = line;
        if (l.startsWith("@")) {
            int sp = l.indexOf(' ');
            if (sp < 0) return null;
            l = l.substring(sp + 1);
        }
        if (!l.startsWith(":")) return null;
        int sp = l.indexOf(' ');
        if (sp < 0) return null;
        String prefix = l.substring(1, sp);
        String rest = l.substring(sp + 1);
        if (!rest.startsWith("PRIVMSG ")) return null;
        rest = rest.substring(8);
        int colon = rest.indexOf(" :");
        if (colon < 0) return null;
        String channel = rest.substring(0, colon).strip();
        if (channel.startsWith("#")) channel = channel.substring(1);
        int bang = prefix.indexOf('!');
        String user = bang < 0 ? prefix : prefix.substring(0, bang);
        return new Message(channel, user, rest.substring(colon + 2));
    }

    /** "!tache", "!Tâche please" → true for the command "!tache" (first word, case and accents ignored). */
    public static boolean isCommand(String text, List<String> commands) {
        if (text == null) return false;
        String first = text.strip().split("\\s+", 2)[0];
        String norm = java.text.Normalizer.normalize(first, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        for (String c : commands) {
            String cn = java.text.Normalizer.normalize(c.strip(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
            if (!cn.isEmpty() && norm.equals(cn)) return true;
        }
        return false;
    }

    /** Anti-spam: may the card be shown now, {@code cooldownMs} after the last time? */
    public static boolean cooledDown(long lastShownMs, long nowMs, long cooldownMs) {
        return lastShownMs <= 0 || nowMs - lastShownMs >= cooldownMs;
    }
}
