package fr.lordfinn.craftmaster.client.media;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.media.ClipLinks;
import fr.lordfinn.craftmaster.media.MediaNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * The clips this game downloaded from the server ({@code craftmaster/clip-cache/<id>.mp4} in the game folder), so a
 * screen showing a clip plays a local file. A clip is fetched the first time a screen shows it; the files stay
 * between sessions (a clip never changes once recorded). Client thread only.
 */
public final class ClipCache {

    private ClipCache() {}

    /** {@code localUri} once the file is here; else {@code percent} downloaded, or {@code error}. */
    public record Status(String localUri, int percent, String error) {}

    private static final class Download {
        long total = -1, received;
        RandomAccessFile out;
        String error;
        long requestedAt = System.currentTimeMillis();
    }

    private static final Map<String, Download> DOWNLOADS = new HashMap<>();

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(MediaNetwork.Piece.TYPE, (p, ctx) -> piece(p));
    }

    static Path dir() {
        return FabricLoader.getInstance().getGameDir().resolve("craftmaster/clip-cache");
    }

    static Path file(String id) {
        return dir().resolve(id + ".mp4");
    }

    /** WaterMedia's address of a local clip ({@code water://local/} is relative to the game folder). */
    static String uri(String id) {
        return "water://local/craftmaster/clip-cache/" + id + ".mp4";
    }

    public static Status get(String id) {
        if (!ClipLinks.validId(id)) return new Status(null, 0, "clip invalide");
        if (Files.isRegularFile(file(id))) return new Status(uri(id), 100, null);
        Download d = DOWNLOADS.get(id);
        if (d == null) {
            if (!ClientPlayNetworking.canSend(MediaNetwork.Fetch.TYPE)) return new Status(null, 0, "serveur sans CraftMaster");
            d = new Download();
            DOWNLOADS.put(id, d);
            ClientPlayNetworking.send(new MediaNetwork.Fetch(id));
        }
        if (d.error != null) return new Status(null, 0, d.error);
        // No answer (the server was busy, a replay was playing): ask again
        if (d.total < 0 && System.currentTimeMillis() - d.requestedAt > 15_000) {
            DOWNLOADS.remove(id);
        }
        return new Status(null, d.total <= 0 ? 0 : (int) (d.received * 100 / d.total), null);
    }

    /** A clip this game just recorded and the server accepted: it is already here, no download. */
    static void adopt(Path recorded, String id) {
        try {
            Files.createDirectories(dir());
            Files.copy(recorded, file(id), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            CraftMaster.LOG.debug("[Clips] Copie locale de {} impossible : {}", id, e.toString());
        }
    }

    private static void piece(MediaNetwork.Piece p) {
        Download d = DOWNLOADS.get(p.clip());
        if (d == null) return;
        if (!p.error().isEmpty()) {
            d.error = p.error();
            close(d);
            return;
        }
        try {
            if (d.out == null) {
                Files.createDirectories(dir());
                d.out = new RandomAccessFile(dir().resolve(p.clip() + ".part").toFile(), "rw");
                d.out.setLength(0);
                d.total = p.total();
            }
            if (p.offset() != d.received) return;
            d.out.write(p.data());
            d.received += p.data().length;
            if (d.received >= d.total) {
                close(d);
                Files.move(dir().resolve(p.clip() + ".part"), file(p.clip()), StandardCopyOption.REPLACE_EXISTING);
                DOWNLOADS.remove(p.clip());
                CraftMaster.LOG.info("[Clips] {} téléchargé ({} Ko)", p.clip(), d.total / 1024);
            }
        } catch (IOException e) {
            d.error = "écriture impossible : " + e.getMessage();
            close(d);
        }
    }

    private static void close(Download d) {
        if (d.out == null) return;
        try {
            d.out.close();
        } catch (IOException ignored) {
            // closing anyway
        }
        d.out = null;
    }

    /** Leaving a server: unfinished downloads are dropped (they start again next time). */
    public static void clear() {
        for (Download d : DOWNLOADS.values()) close(d);
        DOWNLOADS.clear();
    }
}
