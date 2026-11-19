package fr.lordfinn.craftmaster.client.broadcast;

import fr.lordfinn.craftmaster.trial.kit.LaunchGate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;

/**
 * Answers the server's {@link LaunchGate.ReadyCheck}: once the chunks around what this game shows (the camera: the
 * player's eyes, or the broadcast's shot) are all received and the level renderer has built every section it has to
 * draw, for a few ticks in a row, it replies {@link LaunchGate.ReadyReply}. The picture is hidden meanwhile (black of
 * the launch), so nothing pops in on screen.
 */
public final class ReadyClient {

    private ReadyClient() {}

    /** Chunks around the camera that must be there (capped by the render distance). */
    private static final int RADIUS = 5;
    /** Ticks to wait after the check (the teleport and the camera cut land first), then ticks ready in a row. */
    private static final int SETTLE = 6, STEADY = 5;

    private static int id = -1, age, steady;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(LaunchGate.ReadyCheck.TYPE, (payload, ctx) -> {
            id = payload.id();
            age = 0;
            steady = 0;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> id = -1);
        ClientTickEvents.END_CLIENT_TICK.register(ReadyClient::tick);
    }

    private static void tick(Minecraft mc) {
        if (id < 0) return;
        if (mc.level == null || mc.player == null) return;
        age++;
        if (age < SETTLE) return;
        steady = ready(mc) ? steady + 1 : 0;
        if (steady < STEADY) return;
        if (ClientPlayNetworking.canSend(LaunchGate.ReadyReply.TYPE)) ClientPlayNetworking.send(new LaunchGate.ReadyReply(id));
        id = -1;
    }

    /** Chunks around the camera received, every section to draw built. */
    static boolean ready(Minecraft mc) {
        Vec3 at = mc.gameRenderer.mainCamera().position();
        int r = Math.min(RADIUS, mc.options.getEffectiveRenderDistance());
        int cx = SectionPos.blockToSectionCoord(at.x), cz = SectionPos.blockToSectionCoord(at.z);
        for (int x = cx - r; x <= cx + r; x++)
            for (int z = cz - r; z <= cz + r; z++)
                if (!mc.level.getChunkSource().hasChunk(x, z)) return false;
        return mc.levelRenderer.hasRenderedAllSections();
    }
}
