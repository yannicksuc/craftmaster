package fr.lordfinn.craftmaster.camera;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Partie;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.data.SeasonPlayer;
import fr.lordfinn.craftmaster.trial.TrialZones;
import fr.lordfinn.craftmaster.trial.Zone;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the cameras are, in show terms (server side): the lieux (the studio and the rounds of the active season),
 * the zones of a round (the trial zones of {@link TrialZones} are the source of truth: a camera's zone is a trial
 * zone's name; a name without such a zone still works as a free label), and which zone a contestant is in.
 *
 * <p>Also the queries a smarter automatic director can build on: {@link #zoneOf}, {@link #camerasFor}.
 */
public final class Lieux {

    private Lieux() {}

    /** The round of the active season with this id, name or nickname (archived ones too), or null. */
    public static Manche manche(String ref) {
        CraftMasterData data = CraftMaster.data();
        Season s = data == null ? null : data.activeSeason();
        if (s == null || ref == null) return null;
        String key = CameraList.fold(ref).replaceFirst("^(la |le |l'|manche |l'epreuve |epreuve )", "");
        for (Partie p : s.parties.values())
            for (Manche m : p.manches)
                if (m.matches(ref) || m.matches(key) || CameraList.fold(m.name).equals(key) || CameraList.fold(m.id).equals(key)) return m;
        return null;
    }

    /**
     * "studio", a round id ({@link #manche}), or null when unknown. A lieu that already has cameras (a round of an
     * older season) is accepted as it is.
     */
    public static String lieu(String ref) {
        if (ref == null || ref.isBlank()) return null;
        String l = CameraSpot.normalizeLieu(ref);
        if (CameraSpot.STUDIO.equals(l)) return CameraSpot.STUDIO;
        Manche m = manche(ref);
        if (m != null) return m.id;
        for (CameraSpot s : Cameras.list().all()) if (s.lieu().equals(l)) return l;
        return null;
    }

    /** Display name of a lieu: "studio" or the round's name. */
    public static String lieuName(String lieu) {
        if (CameraSpot.STUDIO.equals(lieu)) return "studio";
        Manche m = manche(lieu);
        return m == null ? lieu : m.name;
    }

    /** The trial zone named like that (normalized), or null. */
    public static Zone trialZone(String name) {
        if (name == null || name.isBlank()) return null;
        return TrialZones.zones().get(Zone.normalizeName(name));
    }

    /**
     * A zone name from what the organiser typed or said: a trial zone ("equipe-bleue", "Équipe bleue"), or a
     * contestant ("Ensuri" → the trial zone that watches them, else the label "ensuri"). "" for none / "tout".
     */
    public static String zone(String ref) {
        String z = CameraSpot.normalizeZone(ref);
        if (z.isEmpty()) return "";
        if (trialZone(z) != null) return z;
        String player = contestant(ref);
        if (player != null) {
            for (Zone t : TrialZones.zones().values())
                if (t.players().stream().anyMatch(player::equalsIgnoreCase)) return t.name();
            return CameraSpot.normalizeZone(player);
        }
        return z;
    }

    /** The contestant of the active season with this name or nickname, or null. */
    static String contestant(String ref) {
        CraftMasterData data = CraftMaster.data();
        Season s = data == null ? null : data.activeSeason();
        SeasonPlayer p = s == null || ref == null ? null : s.player(ref.trim());
        return p == null ? null : p.name;
    }

    /**
     * The group of {@code lieu}'s cameras that films this player ("pont:ensuri"), or null: the zone they stand in
     * (a trial zone that has cameras in that lieu), else the trial zone that watches them, else the zone labelled
     * with their name.
     */
    public static String zoneOf(MinecraftServer server, String player, String lieu) {
        String l = CameraSpot.normalizeLieu(lieu);
        List<String> zones = Cameras.list().zones(l);
        if (zones.isEmpty() || player == null) return null;
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayerByName(player);
        if (p != null) {
            String world = p.level().dimension().identifier().toString();
            for (String z : zones) {
                Zone t = trialZone(z);
                if (t != null && t.contains(world, p.getX(), p.getY(), p.getZ())) return CameraSpot.group(l, z);
            }
        }
        for (String z : zones) {
            Zone t = trialZone(z);
            if (t != null && t.players().stream().anyMatch(player::equalsIgnoreCase)) return CameraSpot.group(l, z);
        }
        String own = CameraSpot.normalizeZone(player);
        return zones.contains(own) && !own.isEmpty() ? CameraSpot.group(l, own) : null;
    }

    /** The players of a zone (its trial zone's list, else the contestant it is named after). */
    public static List<String> playersOf(String zone) {
        Zone t = trialZone(zone);
        if (t != null && !t.players().isEmpty()) return t.players();
        String c = contestant(zone);
        return c == null ? List.of() : List.of(c);
    }

    /**
     * The cameras of {@code lieu} that film this player: those of their zone, nearest first (a player not seen in
     * the world keeps the number order). Empty when they have no zone there.
     */
    public static List<CameraSpot> camerasFor(MinecraftServer server, String player, String lieu) {
        String g = zoneOf(server, player, lieu);
        if (g == null) return List.of();
        List<CameraSpot> out = new ArrayList<>(Cameras.list().group(g).all());
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayerByName(player);
        if (p != null) out.sort(Comparator.comparingDouble(s -> p.distanceToSqr(s.x(), s.y(), s.z())));
        return out;
    }

    /**
     * The phrases that name a group, for {@link CameraList#resolve(String, String, Map)}: "studio", every round
     * (id, name, nicknames) with cameras, its zones (name, trial zone players → their zone). Zones of the focused
     * lieu win when two lieux have a zone of the same name.
     */
    public static Map<String, String> places(MinecraftServer server, String focus) {
        Map<String, String> out = new HashMap<>();
        out.put("studio", CameraSpot.STUDIO);
        out.put("plateau", CameraSpot.STUDIO);
        String focusLieu = CameraSpot.lieuOf(focus == null ? CameraSpot.STUDIO : focus);
        List<String> groups = Cameras.list().groups();
        // Other lieux first, the focused one last so that its zones overwrite
        List<String> ordered = new ArrayList<>();
        for (String g : groups) if (!CameraSpot.lieuOf(g).equals(focusLieu)) ordered.add(g);
        for (String g : groups) if (CameraSpot.lieuOf(g).equals(focusLieu)) ordered.add(g);
        for (String g : ordered) {
            String l = CameraSpot.lieuOf(g), z = CameraSpot.zoneOf(g);
            if (CameraSpot.STUDIO.equals(l)) continue;
            if (z.isEmpty()) {
                out.put(CameraList.fold(l), l);
                Manche m = manche(l);
                if (m != null) {
                    out.put(CameraList.fold(m.name), l);
                    out.put(CameraList.fold(m.name).replaceFirst("^(le |la |l')", ""), l);
                    for (String a : m.aliases) out.put(CameraList.fold(a), l);
                }
                continue;
            }
            out.put(CameraList.fold(z), g);
            out.put(CameraList.fold(z.replace('-', ' ')), g);
            for (String p : playersOf(z)) out.put(CameraList.fold(p), g);
            // "l'équipe bleue" → "equipe bleue"
            if (z.startsWith("equipe-")) out.put(CameraList.fold(z.substring(7).replace('-', ' ')), g);
        }
        // Nicknames of contestants who have a zone
        CraftMasterData data = CraftMaster.data();
        Season s = data == null ? null : data.activeSeason();
        if (s != null) for (SeasonPlayer sp : s.players.values()) {
            String g = out.get(CameraList.fold(sp.name));
            if (g != null) for (String a : sp.aliases) out.putIfAbsent(CameraList.fold(a), g);
        }
        return out;
    }

    /** Finds a camera from any reference, relative to the director's focus (see {@link CameraList#resolve(String, String, Map)}). */
    public static CameraSpot resolve(MinecraftServer server, String ref, String focus) {
        return Cameras.list().resolve(ref, focus, places(server, focus));
    }
}
