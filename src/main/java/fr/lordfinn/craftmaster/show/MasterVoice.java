package fr.lordfinn.craftmaster.show;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork;
import fr.lordfinn.craftmaster.trial.Judging;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The game master's voice (Simple Voice Chat), in exclusive modes switched in one gesture (command, N menu, key,
 * Stream Deck, AI control):
 * <ul>
 *   <li>{@link Mode#OFF}: his voice is everyone's usual one (proximity, the trial's common channel…);</li>
 *   <li>{@link Mode#COMMENT}: with the spectators ({@link Spectators}), open (they hear him and each other) or closed;
 *       the contestants still playing never hear it;</li>
 *   <li>{@link Mode#ALL}: an announcement — every player (or only the contestants, or only the spectators) with him;</li>
 *   <li>{@link Mode#TARGET}: one contestant, or a team (scoreboard team), alone with him.</li>
 * </ul>
 * Who is in which group is worked out again from scratch at every change ({@code VoiceGroups.reconcile}): nobody
 * stays stuck in a group. The master sees his mode at the top of his screen, a player he talks to sees « LordFinn te
 * parle ». This class holds no Simple Voice Chat type (the mod is optional).
 */
public final class MasterVoice {

    private MasterVoice() {}

    public enum Mode { OFF, COMMENT, ALL, TARGET }

    /** {@link Mode#ALL} for: everyone, the contestants only, the spectators only. */
    public enum Audience { ALL, CONTESTANTS, SPECTATORS }

    private static Mode mode = Mode.OFF;
    private static boolean commentOpen = true;
    private static UUID host;
    private static String hostName = "";
    private static Audience audience = Audience.ALL;
    /** {@link Mode#TARGET}: players (lower case) and the label ("Ensuri", "équipe Rouge"). */
    private static final Set<String> targets = new LinkedHashSet<>();
    private static String targetLabel = "";

    public static Mode mode() {
        return mode;
    }

    public static boolean commentOpen() {
        return commentOpen;
    }

    public static UUID host() {
        return host;
    }

    /** "commentaire", "tous", "joueur", "off" → the mode; null if not understood. */
    public static Mode parse(String word) {
        if (word == null) return null;
        return switch (word.trim().toLowerCase(Locale.ROOT)) {
            case "off", "aucun", "coupe", "proximite", "normal" -> Mode.OFF;
            case "commentaire", "comment", "spectateurs", "commentary" -> Mode.COMMENT;
            case "tous", "tout", "annonce", "all", "everyone" -> Mode.ALL;
            case "joueur", "equipe", "équipe", "cible", "player", "team" -> Mode.TARGET;
            default -> null;
        };
    }

    public static String label(Mode m) {
        return switch (m) {
            case OFF -> "off";
            case COMMENT -> "commentaire";
            case ALL -> "tous";
            case TARGET -> "joueur";
        };
    }

    /**
     * Switches the master's voice. {@code target}: for {@link Mode#TARGET} a player or a team ("equipe:Rouge" or a
     * team's name), absent = the contestant he is watching; for {@link Mode#ALL} "candidats" / "spectateurs" / "tous";
     * for {@link Mode#COMMENT} "ouvert" / "ferme" (absent: open). Returns the line for him, or throws.
     */
    public static String set(MinecraftServer server, ServerPlayer by, Mode m, String target) {
        if (by != null) {
            host = by.getUUID();
            hostName = by.getGameProfile().name();
        }
        if (host == null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (Permissions.isAdmin(p)) {
                    host = p.getUUID();
                    hostName = p.getGameProfile().name();
                    break;
                }
        }
        if (host == null) throw new IllegalArgumentException("aucun organisateur en ligne pour parler.");
        String t = target == null ? "" : target.trim();
        switch (m) {
            case OFF -> {
                targets.clear();
                targetLabel = "";
            }
            case COMMENT -> {
                String k = t.toLowerCase(Locale.ROOT);
                if (k.startsWith("ferm") || k.equals("off") || k.equals("non")) commentOpen = false;
                else if (k.startsWith("bascul")) commentOpen = mode != Mode.COMMENT || !commentOpen;
                else commentOpen = true;
                targets.clear();
            }
            case ALL -> {
                String k = t.toLowerCase(Locale.ROOT);
                audience = k.startsWith("candidat") ? Audience.CONTESTANTS : k.startsWith("spectat") ? Audience.SPECTATORS : Audience.ALL;
                targets.clear();
            }
            case TARGET -> {
                targets.clear();
                if (t.isEmpty()) {
                    ServerPlayer me = server.getPlayerList().getPlayer(host);
                    String watched = me == null ? null : Spectators.watchedBy(me);
                    if (watched == null) throw new IllegalArgumentException("donne le joueur ou l'équipe (/cm vocal mode joueur <nom>).");
                    t = watched;
                }
                String team = t.toLowerCase(Locale.ROOT).startsWith("equipe:") ? t.substring(7) : t;
                PlayerTeam pt = server.getScoreboard().getPlayerTeam(team);
                ServerPlayer one = server.getPlayerList().getPlayerByName(t);
                if (one != null) {
                    targets.add(one.getGameProfile().name().toLowerCase(Locale.ROOT));
                    targetLabel = one.getGameProfile().name();
                } else if (pt != null) {
                    for (String n : pt.getPlayers()) targets.add(n.toLowerCase(Locale.ROOT));
                    targetLabel = "équipe " + pt.getName();
                } else throw new IllegalArgumentException("« " + t + " » : ni un joueur en ligne ni une équipe.");
            }
        }
        mode = m;
        refresh(server);
        return status(server);
    }

    /** The next mode (off → commentaire → tous → joueur → off), for the key and the Stream Deck. */
    public static String next(MinecraftServer server, ServerPlayer by) {
        Mode n = switch (mode) {
            case OFF -> Mode.COMMENT;
            case COMMENT -> Mode.ALL;
            case ALL -> Mode.TARGET;
            case TARGET -> Mode.OFF;
        };
        try {
            return set(server, by, n, null);
        } catch (IllegalArgumentException e) {
            // Nobody to talk to alone: straight to off
            return set(server, by, Mode.OFF, null);
        }
    }

    /** Where {@code p} should be: "master" (the master's group), "spectators", "common" (the trial's channel) or null. */
    public static String layer(ServerPlayer p, boolean inCommon) {
        boolean isHost = host != null && host.equals(p.getUUID());
        boolean watcher = Spectators.watching(p);
        switch (mode) {
            case ALL -> {
                boolean contestant = Judging.contestant(p) != null;
                if (isHost || audience == Audience.ALL || (audience == Audience.CONTESTANTS && contestant && !watcher)
                        || (audience == Audience.SPECTATORS && (watcher || !contestant))) return "master";
            }
            case TARGET -> {
                if (isHost || targets.contains(p.getGameProfile().name().toLowerCase(Locale.ROOT))) return "master";
            }
            case COMMENT -> {
                if (isHost && commentOpen) return "spectators";
            }
            default -> {}
        }
        if (watcher) return "spectators";
        return inCommon ? "common" : null;
    }

    /** Groups worked out again, and everyone's indicator. */
    public static void refresh(MinecraftServer server) {
        if (FabricLoader.getInstance().isModLoaded("voicechat")) fr.lordfinn.craftmaster.compat.VoiceGroups.reconcile(server);
        hud(server);
    }

    /** "Vocal : commentaire (ouvert) — 3 spectateurs" */
    public static String status(MinecraftServer server) {
        List<String> who = listeners(server);
        String what = switch (mode) {
            case OFF -> "off (chacun son vocal habituel)";
            case COMMENT -> "commentaire " + (commentOpen ? "ouvert" : "fermé");
            case ALL -> "annonce à " + switch (audience) {
                case ALL -> "tout le monde";
                case CONTESTANTS -> "tous les candidats";
                case SPECTATORS -> "tous les spectateurs";
            };
            case TARGET -> "seul avec " + targetLabel;
        };
        return "Vocal : " + what + (mode == Mode.OFF || (mode == Mode.COMMENT && !commentOpen) ? ""
                : " — t'entendent : " + (who.isEmpty() ? "personne" : String.join(", ", who)));
    }

    /** Who hears the master now (names). */
    public static List<String> listeners(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        if (mode == Mode.OFF || (mode == Mode.COMMENT && !commentOpen)) return out;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (host != null && host.equals(p.getUUID())) continue;
            String l = layer(p, false);
            if ("master".equals(l) || (mode == Mode.COMMENT && "spectators".equals(l))) out.add(p.getGameProfile().name());
        }
        return out;
    }

    /** The master's line at the top of his screen; « LordFinn te parle » on the screens of those who hear him. */
    private static void hud(MinecraftServer server) {
        List<String> who = listeners(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(p, BroadcastNetwork.ShowCue.TYPE)) continue;
            String line;
            String role;
            if (host != null && host.equals(p.getUUID())) {
                line = mode == Mode.OFF ? "" : status(server);
                role = "host";
            } else {
                line = who.contains(p.getGameProfile().name()) ? (mode == Mode.COMMENT ? hostName + " commente avec vous" : hostName + " te parle") : "";
                role = "player";
            }
            ServerPlayNetworking.send(p, new BroadcastNetwork.ShowCue(BroadcastNetwork.Cues.VOICE, line, role, "", 0));
        }
    }

    /** A player joining or leaving: groups and indicators again. */
    public static void playersChanged(MinecraftServer server) {
        refresh(server);
    }
}
