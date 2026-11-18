package fr.lordfinn.craftmaster.compat;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import fr.lordfinn.craftmaster.CraftMaster;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A shared voice channel of Simple Voice Chat for a moment of a trial (the organiser announcing a hidden twist to
 * everyone at once): every player chosen joins one group; the group is closed (each back to proximity chat) when
 * the timer starts or on demand. Only loaded when Simple Voice Chat is there (callers check).
 */
public final class VoiceGroups {

    private VoiceGroups() {}

    private static VoicechatServerApi api;
    private static UUID group;
    private static final List<UUID> MEMBERS = new ArrayList<>();

    static void init(VoicechatApi a) {
        if (a instanceof VoicechatServerApi s) api = s;
    }

    public static boolean available() {
        return api != null;
    }

    /** Puts every online player matching {@code who} in one open group; returns how many joined (-1: no voice chat). */
    public static int gather(MinecraftServer server, Predicate<ServerPlayer> who) {
        if (api == null) return -1;
        disband(server);
        Group g = api.groupBuilder().setName("CraftMaster").setPersistent(false).setHidden(true).setType(Group.Type.OPEN).build();
        group = g.getId();
        int n = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!who.test(p)) continue;
            VoicechatConnection c = api.getConnectionOf(p.getUUID());
            if (c == null || !c.isInstalled()) continue;
            c.setGroup(g);
            MEMBERS.add(p.getUUID());
            n++;
        }
        CraftMaster.LOG.info("[Vocal] Canal commun : {} joueur(s)", n);
        return n;
    }

    /** Everyone back to proximity chat; true if a group was open. */
    public static boolean disband(MinecraftServer server) {
        if (api == null || group == null) return false;
        for (UUID id : MEMBERS) {
            VoicechatConnection c = api.getConnectionOf(id);
            if (c != null && c.getGroup() != null && c.getGroup().getId().equals(group)) c.setGroup(null);
        }
        MEMBERS.clear();
        api.removeGroup(group);
        group = null;
        CraftMaster.LOG.info("[Vocal] Canal commun fermé");
        return true;
    }

    public static boolean open() {
        return group != null;
    }
}
