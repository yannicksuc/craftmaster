package fr.lordfinn.craftmaster.media;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Addresses a studio screen can show besides plain web links: {@code cmclip:<id>}, a clip recorded in the game and
 * kept by the server (each game downloads it from the server, no web host needed). Pure, unit tested.
 */
public final class ClipLinks {

    private ClipLinks() {}

    public static final String SCHEME = "cmclip:";
    /** Clip ids: the server makes them from the date and a counter (clip-20261113-021530-1). */
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    public static String of(String id) {
        return SCHEME + id;
    }

    /** The clip id of a {@code cmclip:} address, else null. */
    public static String id(String url) {
        if (url == null || !url.toLowerCase(Locale.ROOT).startsWith(SCHEME)) return null;
        String id = url.substring(SCHEME.length()).trim().toLowerCase(Locale.ROOT);
        return validId(id) ? id : null;
    }

    /** A Twitch clip page: clips.twitch.tv/&lt;slug&gt; or twitch.tv/&lt;channel&gt;/clip/&lt;slug&gt; (with or without www., m.). */
    private static final Pattern TWITCH_CLIP = Pattern.compile(
            "https?://(?:(?:www\\.|m\\.)?twitch\\.tv/[A-Za-z0-9_]{2,25}/clip/|clips\\.twitch\\.tv/(?:embed\\?clip=)?)([A-Za-z0-9_-]{4,100})[^\\s]*");

    /** The Twitch clip links found in a chat message, as clean addresses (https://clips.twitch.tv/&lt;slug&gt;), in order. */
    public static java.util.List<String> twitchClips(String text) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null) return out;
        java.util.regex.Matcher m = TWITCH_CLIP.matcher(text);
        while (m.find()) {
            String link = "https://clips.twitch.tv/" + m.group(1);
            if (!out.contains(link)) out.add(link);
        }
        return out;
    }

    /** A Twitch channel login from a name or an address (twitch.tv/name), lower case; "" when it is not one. */
    public static String twitchChannel(String text) {
        if (text == null) return "";
        String t = text.trim();
        t = t.replaceFirst("(?i)^https?://", "").replaceFirst("(?i)^(www\\.|m\\.)", "").replaceFirst("(?i)^twitch\\.tv/", "");
        t = t.replaceFirst("^@", "").replaceAll("[/?#].*$", "").toLowerCase(Locale.ROOT);
        return t.matches("[a-z0-9_]{3,25}") ? t : "";
    }
}
