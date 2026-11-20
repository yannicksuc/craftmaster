package fr.lordfinn.craftmaster.compat;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.show.MasterVoice;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The one place where CraftMaster puts players in Simple Voice Chat groups. Three groups, made when someone needs
 * them and removed when empty:
 * <ul>
 *   <li>« CraftMaster » (open: they also hear the players around them): the trial's common channel
 *       ({@code /cm epreuve vocal commun|fin}, the voice at the launch);</li>
 *   <li>« Spectateurs » (isolated: nobody outside hears them, they hear nobody outside): the players watching a
 *       contestant's camera or the broadcast ({@link fr.lordfinn.craftmaster.show.Spectators}), and the master when
 *       his commentary is open;</li>
 *   <li>« Maître » (isolated): the master and those he talks to ({@link MasterVoice}: announcement, one player, a team).</li>
 * </ul>
 * Each player's group is worked out again at every change ({@link #reconcile}, {@link MasterVoice#layer}): the
 * master's group first, then the spectators', then the common channel, else none — or the group of their own they
 * were in before CraftMaster moved them, given back. Nobody stays stuck. Only loaded when Simple Voice Chat is
 * there (callers check).
 */
public final class VoiceGroups {

    private VoiceGroups() {}

    private static VoicechatServerApi api;
    /** Layer → its group. */
    private static final Map<String, UUID> GROUPS = new HashMap<>();
    /** The trial's common channel: who is in it. */
    private static final Set<UUID> COMMON = new LinkedHashSet<>();
    /** A group of their own a player was in before being moved into one of ours: given back afterwards. */
    private static final Map<UUID, UUID> BEFORE = new HashMap<>();

    static void init(VoicechatApi a) {
        if (a instanceof VoicechatServerApi s) api = s;
    }

    public static boolean available() {
        return api != null;
    }

    /** Puts every online player matching {@code who} in the common channel; returns how many (-1: no voice chat). */
    public static int gather(MinecraftServer server, Predicate<ServerPlayer> who) {
        if (api == null) return -1;
        COMMON.clear();
        int n = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!who.test(p)) continue;
            VoicechatConnection c = api.getConnectionOf(p.getUUID());
            if (c == null || !c.isInstalled()) continue;
            COMMON.add(p.getUUID());
            n++;
        }
        reconcile(server);
        CraftMaster.LOG.info("[Vocal] Canal commun : {} joueur(s)", n);
        return n;
    }

    /** The common channel closed (its players back to their usual voice); true if it was open. */
    public static boolean disband(MinecraftServer server) {
        if (api == null || COMMON.isEmpty()) return false;
        COMMON.clear();
        reconcile(server);
        CraftMaster.LOG.info("[Vocal] Canal commun fermé");
        return true;
    }

    public static boolean open() {
        return !COMMON.isEmpty();
    }

    /** Everyone in the group their situation asks for now. */
    public static synchronized void reconcile(MinecraftServer server) {
        if (api == null) return;
        Map<String, Integer> wanted = new HashMap<>();
        Map<ServerPlayer, String> where = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            VoicechatConnection c = api.getConnectionOf(p.getUUID());
            if (c == null || !c.isInstalled()) continue;
            String layer = MasterVoice.layer(p, COMMON.contains(p.getUUID()));
            where.put(p, layer);
            if (layer != null) wanted.merge(layer, 1, Integer::sum);
        }
        for (Map.Entry<ServerPlayer, String> e : where.entrySet()) {
            ServerPlayer p = e.getKey();
            VoicechatConnection c = api.getConnectionOf(p.getUUID());
            Group now = c.getGroup();
            UUID nowId = now == null ? null : now.getId();
            boolean ours = nowId != null && GROUPS.containsValue(nowId);
            if (e.getValue() == null) {
                if (ours) {
                    UUID back = BEFORE.remove(p.getUUID());
                    Group g = back == null ? null : api.getGroup(back);
                    c.setGroup(g);
                }
                continue;
            }
            Group target = group(e.getValue());
            if (target.getId().equals(nowId)) continue;
            if (nowId != null && !ours) BEFORE.put(p.getUUID(), nowId);
            c.setGroup(target);
        }
        // Groups nobody needs any more
        for (String layer : java.util.List.copyOf(GROUPS.keySet()))
            if (!wanted.containsKey(layer)) {
                UUID id = GROUPS.remove(layer);
                if (api.getGroup(id) != null) api.removeGroup(id);
            }
    }

    private static Group group(String layer) {
        UUID id = GROUPS.get(layer);
        Group g = id == null ? null : api.getGroup(id);
        if (g != null) return g;
        g = switch (layer) {
            case "common" -> api.groupBuilder().setName("CraftMaster").setPersistent(false).setHidden(true).setType(Group.Type.OPEN).build();
            case "spectators" -> api.groupBuilder().setName("Spectateurs").setPersistent(false).setHidden(true).setType(Group.Type.ISOLATED).build();
            default -> api.groupBuilder().setName("Maître").setPersistent(false).setHidden(true).setType(Group.Type.ISOLATED).build();
        };
        GROUPS.put(layer, g.getId());
        return g;
    }

    /** "Spectateurs: A, B · Maître: …" for the tests and the AI control. */
    public static String describe(MinecraftServer server) {
        if (api == null) return "Simple Voice Chat absent";
        StringBuilder b = new StringBuilder();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            VoicechatConnection c = api.getConnectionOf(p.getUUID());
            Group g = c == null ? null : c.getGroup();
            b.append(p.getGameProfile().name()).append(" → ").append(c == null || !c.isInstalled() ? "sans le mod vocal" : g == null ? "proximité" : g.getName()).append("; ");
        }
        return b.toString();
    }
}
