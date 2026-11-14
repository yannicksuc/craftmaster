package fr.lordfinn.craftmaster.studio;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.show.ShowEvents;
import fr.lordfinn.craftmaster.trial.TrialZones;
import fr.lordfinn.craftmaster.trial.Zone;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Automatic follow-spots: instead of a player's name, a spotlight follows a rule and the server picks the player.
 * <ul>
 *   <li>{@code @proche}: the nearest player (not a spectator);</li>
 *   <li>{@code @candidat}: the nearest contestant of the active season;</li>
 *   <li>{@code @zone:<name>}: a player standing in that trial zone ({@code @scene} = the zone named "scene");</li>
 *   <li>{@code @moment}: whoever the show just put in the spotlight (envelope opened, buzzer, reveal…), for a minute;</li>
 *   <li>{@code @antenne}: the player the TV direction shows (followed, cut to because they speak, « on va voir »).</li>
 * </ul>
 * The same rules serve the follow camera ({@code /cam follow @moment}): one follow system for both.
 * The beam stays on its player while they still match (no jumping between two people standing side by side).
 */
public final class SpotlightFollow {

    private SpotlightFollow() {}

    public static final String NEAREST = "@proche", CONTESTANT = "@candidat", ZONE = "@zone:", STAGE = "@scene", MOMENT = "@moment";
    /** The show's master, a rank of the season's ranking, the last one, a contestant at random, someone of the audience. */
    public static final String MASTER = "@maitre", RANK = "@rang:", LAST = "@dernier", RANDOM = "@hasard", AUDIENCE = "@public";
    /** The player the TV direction shows. */
    public static final String ON_AIR = "@antenne";
    /** The rules offered to commands and the voice control. */
    public static final List<String> RULES = List.of(NEAREST, CONTESTANT, STAGE, MOMENT, ON_AIR, MASTER, RANK + "1", LAST, RANDOM, AUDIENCE, ZONE);
    /** How long "the moment" lasts (ms). */
    private static final long MOMENT_MS = 60_000;

    private static volatile String momentPlayer = "";
    private static volatile long momentAt;

    public static void register() {
        ShowEvents.register(new ShowEvents.Listener() {
            @Override
            public void onSpotlight(MinecraftServer server, String player, String reason) {
                momentPlayer = player == null ? "" : player;
                momentAt = System.currentTimeMillis();
            }
        });
    }

    public static boolean isRule(String follow) {
        return follow != null && follow.startsWith("@");
    }

    /** "@scène", "scene", "zone scène"… → the stored rule, or null if it is not a rule (then it is a player name). */
    public static String parse(String s) {
        if (s == null) return null;
        String k = fr.lordfinn.craftmaster.camera.CameraList.fold(s.strip()).replaceAll("^@", "");
        if (k.startsWith("zone:") || k.startsWith("zone ")) return k.length() > 5 ? ZONE + Zone.normalizeName(k.substring(5).strip()) : null;
        // "rang:2", "rang 2", "2e", "premier", "deuxième"…
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(?:rang[: ]?|n°?|numero )?([0-9]+)(?:er|e|eme|ieme)?$").matcher(k);
        if (m.matches()) return RANK + Integer.parseInt(m.group(1));
        return switch (k) {
            case "maitre", "craftmaster", "master", "animateur", "presentateur" -> MASTER;
            case "premier", "1er", "vainqueur", "gagnant", "leader" -> RANK + 1;
            case "deuxieme", "second" -> RANK + 2;
            case "troisieme" -> RANK + 3;
            case "dernier", "last" -> LAST;
            case "hasard", "aleatoire", "random", "candidat au hasard" -> RANDOM;
            case "public", "spectateur", "spectateurs", "audience", "quelqu'un du public" -> AUDIENCE;
            case "proche", "plus proche", "nearest", "auto" -> NEAREST;
            case "candidat", "candidats", "contestant" -> CONTESTANT;
            case "scene", "plateau", "stage", "sur scene" -> STAGE;
            case "moment", "action", "evenement" -> MOMENT;
            case "antenne", "a l'antenne", "a l antenne", "camera", "celui qu'on voit", "qui passe a l'antenne", "direct" -> ON_AIR;
            default -> null;
        };
    }

    /** "stop", "personne", "aucun"… : nobody (the spotlight stops following). */
    public static boolean isNobody(String s) {
        String k = s == null ? "" : fr.lordfinn.craftmaster.camera.CameraList.fold(s.strip());
        return k.isEmpty() || k.equals("stop") || k.equals("personne") || k.equals("aucun") || k.equals("rien") || k.equals("none") || k.equals("fixe");
    }

    /** French words for messages. */
    public static String describe(String follow) {
        if (follow == null || follow.isEmpty()) return "personne";
        if (!isRule(follow)) return follow;
        if (follow.startsWith(ZONE)) return "qui est dans la zone « " + follow.substring(ZONE.length()) + " »";
        if (follow.startsWith(RANK)) return "le " + follow.substring(RANK.length()) + (follow.endsWith(":1") ? "er" : "e") + " du classement";
        return switch (follow) {
            case MASTER -> "le CraftMaster";
            case LAST -> "le dernier du classement";
            case RANDOM -> "un candidat au hasard";
            case AUDIENCE -> "quelqu'un du public au hasard";
            case NEAREST -> "le joueur le plus proche";
            case CONTESTANT -> "le candidat le plus proche";
            case STAGE -> "qui est sur scène (zone « scene »)";
            case MOMENT -> "le joueur du moment (enveloppe, buzzer, révélation…)";
            case ON_AIR -> "le joueur à l'antenne";
            default -> follow;
        };
    }

    /**
     * The player a spotlight at {@code pivot} follows by {@code rule} now: {@code current} while they still match,
     * else the nearest who does, else nobody ("").
     */
    public static String pick(ServerLevel level, String rule, Vec3 pivot, String current) {
        MinecraftServer server = level.getServer();
        if (rule.equals(MOMENT)) {
            if (System.currentTimeMillis() - momentAt > MOMENT_MS) return "";
            ServerPlayer p = server.getPlayerList().getPlayerByName(momentPlayer);
            return p != null && p.level() == level ? p.getGameProfile().name() : "";
        }
        // One named player: the master, a rank of the live ranking, the one on air
        String named = null;
        if (rule.equals(MASTER)) named = master();
        else if (rule.equals(ON_AIR)) named = fr.lordfinn.craftmaster.broadcast.Director.shownPlayer();
        else if (rule.startsWith(RANK) || rule.equals(LAST)) named = ranked(rule);
        if (named != null) {
            ServerPlayer p = named.isEmpty() ? null : server.getPlayerList().getPlayerByName(named);
            return p != null && p.level() == level && p.position().distanceTo(pivot) <= SpotlightBlockEntity.FOLLOW_RANGE ? p.getGameProfile().name() : "";
        }
        List<ServerPlayer> candidates = new ArrayList<>();
        Set<String> cast = rule.equals(CONTESTANT) || rule.equals(RANDOM) ? contestants() : null;
        Set<String> notCast = rule.equals(AUDIENCE) ? contestants() : null;
        var plateau = fr.lordfinn.craftmaster.stage.Scenography.book().plateau;
        Zone zone = null;
        if (rule.startsWith(ZONE) || rule.equals(STAGE)) {
            zone = TrialZones.zones().get(rule.equals(STAGE) ? plateau.scene : rule.substring(ZONE.length()));
            if (zone == null) return "";
        }
        // The audience: its zone when there is one, else anyone who is not a contestant nor the master
        if (rule.equals(AUDIENCE)) zone = TrialZones.zones().get(plateau.publicZone);
        String world = level.dimension().identifier().toString();
        for (ServerPlayer p : level.players()) {
            if (p.gameMode() == GameType.SPECTATOR) continue;
            if (p.position().distanceTo(pivot) > SpotlightBlockEntity.FOLLOW_RANGE) continue;
            if (cast != null && !cast.contains(p.getGameProfile().name().toLowerCase(Locale.ROOT))) continue;
            if (notCast != null && (notCast.contains(p.getGameProfile().name().toLowerCase(Locale.ROOT))
                    || p.getGameProfile().name().equalsIgnoreCase(master()))) continue;
            if (zone != null && !zone.contains(world, p.getX(), p.getY(), p.getZ())) continue;
            candidates.add(p);
        }
        for (ServerPlayer p : candidates) if (p.getGameProfile().name().equalsIgnoreCase(current)) return p.getGameProfile().name();
        // At random: picked once, then kept while they still match (above)
        if (rule.equals(RANDOM) || rule.equals(AUDIENCE))
            return candidates.isEmpty() ? "" : candidates.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(candidates.size())).getGameProfile().name();
        ServerPlayer best = null;
        for (ServerPlayer p : candidates)
            if (best == null || p.position().distanceToSqr(pivot) < best.position().distanceToSqr(pivot)) best = p;
        return best == null ? "" : best.getGameProfile().name();
    }

    /** The show's master: the plateau's setting, LordFinn by default. */
    public static String master() {
        String m = fr.lordfinn.craftmaster.stage.Scenography.book().plateau.maitre;
        return m == null || m.isBlank() ? "LordFinn" : m.strip();
    }

    /** The player at a rank ("@rang:2") or the last one of the active season's live ranking; "" if none. */
    static String ranked(String rule) {
        var data = CraftMaster.data();
        Season s = data == null ? null : data.activeSeason();
        if (s == null) return "";
        List<fr.lordfinn.craftmaster.data.CraftMasterData.Entry> r = data.ranking(s.id, false);
        if (r.isEmpty()) return "";
        if (rule.equals(LAST)) return r.getLast().player();
        try {
            int n = Integer.parseInt(rule.substring(RANK.length()));
            return n >= 1 && n <= r.size() ? r.get(n - 1).player() : "";
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private static Set<String> contestants() {
        var data = CraftMaster.data();
        Season s = data == null ? null : data.activeSeason();
        if (s == null) return Set.of();
        return s.players.values().stream().filter(x -> !x.archived).map(x -> x.name.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }
}
