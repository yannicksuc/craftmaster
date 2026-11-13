package fr.lordfinn.craftmaster.trial;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The measuring tape's ribbon: for {@link #LIFETIME} ticks, a thin yellow tape is laid between the two points the
 * judge measured, with the distance written above its middle, for everyone to see. Vanilla display entities (a
 * stretched block display, a text display), tagged {@value #TAG}; they are removed when their time is up, and any
 * left over in a saved world is removed when it loads.
 */
public final class TapeRibbons {

    private TapeRibbons() {}

    public static final String TAG = "craftmaster_tape";
    private static final int LIFETIME = 200;
    /** Width and thickness of the tape (blocks); lift above the measured surfaces, against flicker. */
    private static final float WIDTH = 0.09f, THICK = 1 / 64f, LIFT = 0.012f;

    private record Shown(ServerLevel level, List<UUID> entities, long until) {}

    private static final List<Shown> SHOWN = new ArrayList<>();
    private static final Set<UUID> LIVE = new HashSet<>();
    private static int next;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TapeRibbons::tick);
        // A tape saved in the world (server stopped while it showed) goes away when it loads again
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG) && !LIVE.contains(entity.getUUID())) entity.discard();
        });
    }

    /** Lays the tape from {@code a} to {@code b} with {@code label} above its middle. */
    public static void show(ServerLevel level, Vec3 a, Vec3 b, String label) {
        Vec3 from = a.add(0, LIFT, 0), to = b.add(0, LIFT, 0);
        Vec3 d = to.subtract(from);
        float length = (float) d.length();
        if (length < 0.01f) return;
        Vector3f dir = new Vector3f((float) d.x, (float) d.y, (float) d.z).normalize();
        Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(1, 0, 0), dir);
        // The block's box is 0..1: centre its width and thickness on the line
        Vector3f shift = rot.transform(new Vector3f(0, -THICK / 2, -WIDTH / 2));
        String tag = TAG + "_" + (next++);
        String tape = String.format(Locale.ROOT,
                "summon minecraft:block_display %f %f %f {Tags:[\"%s\",\"%s\"],block_state:{Name:\"minecraft:yellow_concrete\"},"
                        + "brightness:{block:15,sky:15},transformation:{left_rotation:[%f,%f,%f,%f],right_rotation:[0f,0f,0f,1f],"
                        + "translation:[%f,%f,%f],scale:[%f,%f,%f]}}",
                from.x, from.y, from.z, TAG, tag, rot.x, rot.y, rot.z, rot.w, shift.x, shift.y, shift.z, length, THICK, WIDTH);
        Vec3 mid = from.lerp(to, 0.5).add(0, 0.3, 0);
        String text = String.format(Locale.ROOT,
                "summon minecraft:text_display %f %f %f {Tags:[\"%s\",\"%s\"],billboard:\"center\",brightness:{block:15,sky:15},"
                        + "background:-1442840576,text:{text:\"%s\",color:\"#FFD23D\",bold:true}}",
                mid.x, mid.y, mid.z, TAG, tag, label.replace("\\", "").replace("\"", ""));
        MinecraftServer server = level.getServer();
        CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, tape);
        server.getCommands().performPrefixedCommand(source, text);
        List<UUID> ids = new ArrayList<>();
        AABB box = new AABB(from, to).inflate(1);
        for (Entity e : level.getEntities((Entity) null, box, e -> e.entityTags().contains(tag))) ids.add(e.getUUID());
        LIVE.addAll(ids);
        SHOWN.add(new Shown(level, ids, server.getTickCount() + LIFETIME));
    }

    private static void tick(MinecraftServer server) {
        for (Iterator<Shown> it = SHOWN.iterator(); it.hasNext(); ) {
            Shown s = it.next();
            if (server.getTickCount() < s.until) continue;
            for (UUID id : s.entities) {
                Entity e = s.level.getEntity(id);
                if (e != null) e.discard();
                LIVE.remove(id);
            }
            it.remove();
        }
    }
}
