package fr.lordfinn.craftmaster.client.studio;

import fr.lordfinn.craftmaster.media.ServerImages;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** The game's side of {@link ServerImages}: asks the server for a {@code cmimg://} picture and puts its pieces together. */
public final class ServerImagesClient {

    private ServerImagesClient() {}

    private static final class Pending {
        final CompletableFuture<byte[]> done = new CompletableFuture<>();
        byte[][] parts;
        int received;
    }

    private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ServerImages.Chunk.TYPE, (c, ctx) -> {
            Pending p = PENDING.get(c.id());
            if (p == null) return;
            if (c.total() <= 0) {
                PENDING.remove(c.id());
                p.done.completeExceptionally(new IOException("image inconnue du serveur"));
                return;
            }
            if (p.parts == null) p.parts = new byte[c.total()][];
            if (c.index() < 0 || c.index() >= p.parts.length || p.parts[c.index()] != null) return;
            p.parts[c.index()] = c.data();
            if (++p.received < p.parts.length) return;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] b : p.parts) out.writeBytes(b);
            PENDING.remove(c.id());
            p.done.complete(out.toByteArray());
        });
    }

    /** The picture's bytes (blocks, off the render thread), or an IOException. */
    static byte[] fetch(String url) throws IOException {
        String id = url.substring(ServerImages.SCHEME.length()).replaceAll("[^0-9a-f]", "");
        Pending p = PENDING.computeIfAbsent(id, k -> {
            Pending n = new Pending();
            Minecraft.getInstance().execute(() -> {
                if (ClientPlayNetworking.canSend(ServerImages.Request.TYPE)) ClientPlayNetworking.send(new ServerImages.Request(k));
                else n.done.completeExceptionally(new IOException("serveur sans images CraftMaster"));
            });
            return n;
        });
        try {
            return p.done.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            PENDING.remove(id);
            throw new IOException("image du serveur indisponible (" + e.getClass().getSimpleName() + ")");
        }
    }
}
