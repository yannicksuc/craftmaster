package fr.lordfinn.craftmaster.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.lordfinn.craftmaster.CraftMaster;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The replays' disk: how much CraftMaster's recordings take, how much is free, a warning when it runs low, and the
 * purge of old replays ({@code keepDays}, {@code maxGb} in {@code replays.json}, or {@code /cm replay purge}). Only the
 * recordings of CraftMaster's index are touched (their files, then their entry); recordings in progress never.
 */
public final class ReplayDisk {

    private ReplayDisk() {}

    private static final double GB = 1024.0 * 1024 * 1024;
    private static long lastWarning;

    /** The recordings' folder (Server Replay's folders' parent). */
    private static Path root() {
        return Replays.indexFile().getParent();
    }

    /** Free space on the recordings' disk (bytes), -1 if unknown. */
    public static long free() {
        try {
            Path r = root();
            Files.createDirectories(r);
            return Files.getFileStore(r).getUsableSpace();
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    /** "Disque presque plein…" when below {@code minFreeGb}, at most every 5 min; else null. */
    static String lowSpaceWarning() {
        long f = free();
        double min = Replays.settings().minFreeGb;
        if (f < 0 || min <= 0 || f >= min * GB) return null;
        long now = System.currentTimeMillis();
        if (now - lastWarning < 5 * 60_000) return null;
        lastWarning = now;
        return String.format(Locale.FRENCH, "⚠ Replays : plus que %.1f Go libres sur le disque des enregistrements "
                + "(Server Replay met en pause sous 1 Go). /cm replay disque, /cm replay purge <jours>.", f / GB);
    }

    /** One recording of the index with its files on the disk. */
    record Stored(JsonObject entry, Instant started, List<Path> files, long bytes) {}

    /** The finished recordings of the index, oldest first, with their files. */
    static List<Stored> stored() {
        List<Stored> out = new ArrayList<>();
        try {
            for (JsonElement e : Replays.readIndex(Replays.indexFile())) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("inProgress")) continue;
                ReplayViewing.Entry entry = ReplayViewing.parse(o);
                List<Path> files = new ArrayList<>();
                long bytes = 0;
                for (ReplayViewing.View v : entry.views()) {
                    // Only the file named after this recording's start: never another recording's
                    Path f = ReplayViewing.file(entry, v, true);
                    if (f == null) continue;
                    files.add(f);
                    bytes += size(f);
                }
                out.add(new Stored(o, entry.started(), files, bytes));
            }
        } catch (IOException | RuntimeException e) {
            CraftMaster.LOG.warn("[Replay] Index des replays illisible : {}", e.toString());
        }
        out.sort(java.util.Comparator.comparing(Stored::started));
        return out;
    }

    private static long size(Path p) {
        if (!Files.isDirectory(p)) {
            try {
                return Files.size(p);
            } catch (IOException e) {
                return 0;
            }
        }
        try (Stream<Path> s = Files.walk(p)) {
            return s.filter(Files::isRegularFile).mapToLong(ReplayDisk::size).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Lines for {@code /cm replay disque}. */
    public static List<String> report() {
        List<Stored> all = stored();
        long used = all.stream().mapToLong(Stored::bytes).sum();
        long f = free();
        Replays.Settings s = Replays.settings();
        List<String> out = new ArrayList<>();
        out.add(String.format(Locale.FRENCH, "Replays CraftMaster : %d enregistrement(s), %s", all.size(), size(used / GB))
                + (f < 0 ? "" : String.format(Locale.FRENCH, " · %.1f Go libres sur le disque", f / GB)));
        out.add("Purge automatique : " + (s.keepDays > 0 ? "plus vieux que " + s.keepDays + " jour(s)" : "pas par l'âge")
                + " · " + (s.maxGb > 0 ? String.format(Locale.FRENCH, "%.1f Go au plus", s.maxGb) : "pas de plafond")
                + String.format(Locale.FRENCH, " · alerte sous %.1f Go libres", s.minFreeGb));
        if (!all.isEmpty())
            out.add("Le plus ancien : " + all.getFirst().entry().get("label").getAsString() + " (" + all.getFirst().started().toString().substring(0, 10) + ")");
        return out;
    }

    private static String size(double gb) {
        return gb >= 1 ? String.format(Locale.FRENCH, "%.2f Go", gb) : String.format(Locale.FRENCH, "%.1f Mo", gb * 1024);
    }

    /** The automatic purge (settings), after a recording and at the server's start. */
    static void autoPurge() {
        Replays.Settings s = Replays.settings();
        if (s.keepDays <= 0 && s.maxGb <= 0) return;
        int n = purge(s.keepDays, s.maxGb);
        if (n > 0) CraftMaster.LOG.info("[Replay] Purge automatique : {} vieux replay(s) supprimé(s)", n);
    }

    /**
     * Deletes the recordings older than {@code days} (0 = none by age), then the oldest while they take more than
     * {@code maxGb} (0 = no limit). Returns how many were deleted.
     */
    public static synchronized int purge(int days, double maxGb) {
        List<Stored> all = stored();
        Instant limit = days > 0 ? Instant.now().minus(Duration.ofDays(days)) : null;
        long total = all.stream().mapToLong(Stored::bytes).sum();
        List<Stored> doomed = new ArrayList<>();
        for (Stored st : all) {
            boolean old = limit != null && st.started().isBefore(limit);
            boolean over = maxGb > 0 && total > maxGb * GB;
            if (!old && !over) continue;
            doomed.add(st);
            total -= st.bytes();
        }
        if (doomed.isEmpty()) return 0;
        for (Stored st : doomed) {
            for (Path f : st.files()) delete(f);
            CraftMaster.LOG.info("[Replay] Supprimé : {} ({} Mo)", st.entry().get("label").getAsString(), st.bytes() / 1_000_000);
        }
        try {
            JsonArray kept = new JsonArray();
            for (JsonElement e : Replays.readIndex(Replays.indexFile())) {
                boolean gone = false;
                for (Stored st : doomed) if (e.equals(st.entry())) gone = true;
                if (!gone) kept.add(e);
            }
            Replays.writeIndexFile(Replays.indexFile(), kept);
        } catch (IOException | RuntimeException e) {
            CraftMaster.LOG.warn("[Replay] Index non mis à jour après la purge : {}", e.toString());
        }
        return doomed.size();
    }

    private static void delete(Path p) {
        try {
            if (Files.isDirectory(p)) {
                try (Stream<Path> s = Files.walk(p)) {
                    for (Path q : s.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(q);
                }
            } else Files.deleteIfExists(p);
            // Its folder, once empty
            Path dir = p.getParent();
            try (Stream<Path> s = Files.list(dir)) {
                if (s.findAny().isEmpty()) Files.deleteIfExists(dir);
            }
        } catch (IOException e) {
            CraftMaster.LOG.warn("[Replay] {} non supprimé : {}", p, e.toString());
        }
    }
}
