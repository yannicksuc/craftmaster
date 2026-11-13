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
 *   <li>{@code @moment}: whoever the show just put in the spotlight (envelope opened, buzzer, reveal…), for a minute.</li>
 * </ul>
 * The beam stays on its player while they still match (no jumping between two people standing side by side).
 */
public final class SpotlightFollow {

    private SpotlightFollow() {}

    public static final String NEAREST = "@proche", CONTESTANT = "@candidat", ZONE = "@zone:", STAGE = "@scene", MOMENT = "@moment";
    /** The rules offered to commands and the voice control. */
    public static final List<String> RULES = List.of(NEAREST, CONTESTANT, STAGE, MOMENT, ZONE);
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
        return switch (k) {
            case "proche", "plus proche", "nearest", "auto" -> NEAREST;
            case "candidat", "candidats", "contestant" -> CONTESTANT;
            case "scene", "plateau", "stage", "sur scene" -> STAGE;
            case "moment", "action", "evenement" -> MOMENT;
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
        return switch (follow) {
            case NEAREST -> "le joueur le plus proche";
            case CONTESTANT -> "le candidat le plus proche";
            case STAGE -> "qui est sur scène (zone « scene »)";
            case MOMENT -> "le joueur du moment (enveloppe, buzzer, révélation…)";
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
        List<ServerPlayer> candidates = new ArrayList<>();
        Set<String> cast = rule.equals(CONTESTANT) ? contestants() : null;
        Zone zone = null;
        if (rule.startsWith(ZONE) || rule.equals(STAGE)) {
            zone = TrialZones.zones().get(rule.equals(STAGE) ? "scene" : rule.substring(ZONE.length()));
            if (zone == null) return "";
        }
        String world = level.dimension().identifier().toString();
        for (ServerPlayer p : level.players()) {
            if (p.gameMode() == GameType.SPECTATOR) continue;
            if (p.position().distanceTo(pivot) > SpotlightBlockEntity.FOLLOW_RANGE) continue;
            if (cast != null && !cast.contains(p.getGameProfile().name().toLowerCase(Locale.ROOT))) continue;
            if (zone != null && !zone.contains(world, p.getX(), p.getY(), p.getZ())) continue;
            candidates.add(p);
        }
        for (ServerPlayer p : candidates) if (p.getGameProfile().name().equalsIgnoreCase(current)) return p.getGameProfile().name();
        ServerPlayer best = null;
        for (ServerPlayer p : candidates)
            if (best == null || p.position().distanceToSqr(pivot) < best.position().distanceToSqr(pivot)) best = p;
        return best == null ? "" : best.getGameProfile().name();
    }

    private static Set<String> contestants() {
        var data = CraftMaster.data();
        Season s = data == null ? null : data.activeSeason();
        if (s == null) return Set.of();
        return s.players.values().stream().filter(x -> !x.archived).map(x -> x.name.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }
}
