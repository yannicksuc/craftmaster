package fr.lordfinn.craftmaster.media;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.lordfinn.craftmaster.CraftMaster;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Each contestant's Twitch channel ({@code config/craftmaster/twitch.json}: Minecraft name → channel login), so a
 * screen can show "Ensuri's live" by name. Unit tested.
 */
public final class TwitchChannels {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    /** Lower-case player name → [player name as written, channel]. */
    private final Map<String, String[]> links = new TreeMap<>();

    public TwitchChannels(Path file) {
        this.file = file;
        load();
    }

    /** Links a player to a channel (a name or a twitch.tv address); false if the channel is not valid. */
    public synchronized boolean link(String player, String channel) {
        String c = ClipLinks.twitchChannel(channel);
        if (player == null || player.isBlank() || c.isEmpty()) return false;
        links.put(player.toLowerCase(Locale.ROOT), new String[]{player, c});
        save();
        return true;
    }

    public synchronized boolean unlink(String player) {
        boolean removed = player != null && links.remove(player.toLowerCase(Locale.ROOT)) != null;
        if (removed) save();
        return removed;
    }

    /** Player name → channel, sorted by name. */
    public synchronized Map<String, String> all() {
        Map<String, String> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String[] l : links.values()) out.put(l[0], l[1]);
        return out;
    }

    /** The channel of a player named {@code who}, or of the player whose channel is {@code who}; null if none. */
    public synchronized String channelOf(String who) {
        String[] l = find(who);
        return l == null ? null : l[1];
    }

    /** The player linked to {@code who} (their name or their channel); null if none. */
    public synchronized String playerOf(String who) {
        String[] l = find(who);
        return l == null ? null : l[0];
    }

    private String[] find(String who) {
        if (who == null) return null;
        String w = who.strip().toLowerCase(Locale.ROOT);
        String[] l = links.get(w);
        if (l != null) return l;
        String ch = ClipLinks.twitchChannel(who);
        for (String[] x : links.values()) if (x[1].equals(ch)) return x;
        return null;
    }

    private void load() {
        links.clear();
        if (!Files.isRegularFile(file)) return;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, String> m = GSON.fromJson(r, new TypeToken<Map<String, String>>() {}.getType());
            if (m != null) m.forEach((p, c) -> {
                String ch = ClipLinks.twitchChannel(c);
                if (p != null && !p.isBlank() && !ch.isEmpty()) links.put(p.toLowerCase(Locale.ROOT), new String[]{p, ch});
            });
        } catch (IOException | RuntimeException e) {
            CraftMaster.LOG.warn("[Twitch] {} illisible : {}", file, e.toString());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(all(), w);
            }
        } catch (IOException e) {
            CraftMaster.LOG.warn("[Twitch] {} non enregistré : {}", file, e.toString());
        }
    }
}
