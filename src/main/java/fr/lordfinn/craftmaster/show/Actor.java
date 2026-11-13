package fr.lordfinn.craftmaster.show;

import fr.lordfinn.craftmaster.Permissions;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;
import java.util.Set;

/**
 * Who sends a list of show actions, from where, and with which rights ({@link Rights}). Built from a connected player
 * (permissions, op level, the moderators' list) or from a moderator of the site (the list only).
 *
 * @param name   shown in the journal ("LordFinn", "ensuri")
 * @param origin voix | deck | jeu | site | commande
 */
public record Actor(String name, String origin, Set<Rights.Right> rights) {

    public Actor {
        rights = rights.isEmpty() ? EnumSet.noneOf(Rights.Right.class) : EnumSet.copyOf(rights);
    }

    public boolean isAdmin() {
        return rights.contains(Rights.Right.ADMIN);
    }

    public boolean any() {
        return !rights.isEmpty();
    }

    /** A connected player: organiser, permissions craftmaster:camera / tv / image / replay / jeu / modo, the list. */
    public static Actor of(ServerPlayer player, String origin) {
        Set<Rights.Right> r = EnumSet.noneOf(Rights.Right.class);
        if (Permissions.isAdmin(player)) r.add(Rights.Right.ADMIN);
        else {
            r.addAll(Permissions.rights(player));
            Moderators.Entry e = Moderators.byMinecraft(player.getPlainTextName());
            if (e != null && !Moderators.paused()) r.addAll(e.rightSet());
            if (Moderators.paused()) r.clear();
        }
        return new Actor(player.getPlainTextName(), origin, r);
    }

    /** A moderator signed in on the site with Twitch; no rights when unknown or paused. */
    public static Actor ofTwitch(String login) {
        Moderators.Entry e = Moderators.byTwitch(login);
        Set<Rights.Right> r = e == null || Moderators.paused() ? EnumSet.noneOf(Rights.Right.class) : e.rightSet();
        return new Actor(e == null ? login : e.label(), "site", r);
    }
}
