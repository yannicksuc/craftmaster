package fr.lordfinn.craftmaster.camera;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Locale;

/**
 * One camera as the director knows it: its number (1, 2, 3…, what the keys 1–9 pick), its name, an optional role
 * ({@link #WIDE}, {@link #THRONE}, {@link #PODIUM}: what "plan large", "plan sur le trône" and the automatic
 * direction look for), where its lens is and where it looks, its zoom and how it moves on air.
 * Pure data, shared by the server (registry, file) and the clients (show state).
 *
 * <p><b>Lieu and zone</b>: every camera belongs to a place, the {@link #STUDIO} (default) or a round ("manche", by
 * its id), and optionally to a zone inside that round (a trial zone of {@code trial.TrialZones}, typically the room
 * of one contestant or team). Numbers are counted <em>per group</em> (the studio, a round, a round's zone): Ensuri's
 * room has its own cameras 1, 2, 3, which are what the keys 1–9 pick once that zone is selected. {@link #key()}
 * ("studio:2", "pont:2", "pont:ensuri:1") names a camera uniquely.
 *
 * @param world  dimension id ("minecraft:overworld")
 * @param x      lens position (the point the broadcast sees from), not the tripod's feet
 * @param fov    field of view in degrees, 0 = the viewer's own setting
 * @param motion one of {@link #MOTIONS}
 * @param lieu   {@link #STUDIO} or a round id
 * @param zone   "" (the whole lieu) or a zone name; always "" in the studio
 */
public record CameraSpot(int number, String name, String role, String world, double x, double y, double z,
                         float yaw, float pitch, float fov, String motion, String lieu, String zone) {

    public static final String WIDE = "large", THRONE = "trone", PODIUM = "podium";
    public static final java.util.List<String> ROLES = java.util.List.of(WIDE, THRONE, PODIUM);

    /** Locked off (no movement), slow breathing (default), slow push-in, slow pan back and forth. */
    public static final String FIXED = "fixe", BREATHING = "respiration", DOLLY = "travelling", PAN = "panoramique";
    public static final java.util.List<String> MOTIONS = java.util.List.of(FIXED, BREATHING, DOLLY, PAN);

    /** The studio's lieu (the default one). */
    public static final String STUDIO = "studio";

    public static final float MIN_FOV = 15, MAX_FOV = 110;

    public CameraSpot {
        name = name == null || name.isBlank() ? defaultName(number) : name.trim();
        role = normalizeRole(role);
        motion = normalizeMotion(motion);
        fov = fov <= 0 ? 0 : Math.max(MIN_FOV, Math.min(MAX_FOV, fov));
        pitch = Math.max(-90, Math.min(90, pitch));
        lieu = normalizeLieu(lieu);
        zone = STUDIO.equals(lieu) ? "" : normalizeZone(zone);
    }

    /** A studio camera (as every camera was before the lieux). */
    public CameraSpot(int number, String name, String role, String world, double x, double y, double z,
                      float yaw, float pitch, float fov, String motion) {
        this(number, name, role, world, x, y, z, yaw, pitch, fov, motion, STUDIO, "");
    }

    public static String defaultName(int number) {
        return "Caméra " + number;
    }

    /** "trône", "throne", "réaction" → "trone"; "wide", "général" → "large"; unknown or "aucun" → "". */
    public static String normalizeRole(String role) {
        if (role == null) return "";
        String r = CameraList.fold(role);
        return switch (r) {
            case "large", "wide", "general", "plan large", "plan general", "ensemble" -> WIDE;
            case "trone", "throne", "reaction", "maitre", "master" -> THRONE;
            case "podium", "classement" -> PODIUM;
            default -> "";
        };
    }

    public static String normalizeMotion(String motion) {
        if (motion == null) return BREATHING;
        String m = CameraList.fold(motion);
        return switch (m) {
            case "fixe", "fixed", "locked", "aucun", "none" -> FIXED;
            case "travelling", "dolly", "push", "zoom" -> DOLLY;
            case "panoramique", "pan", "panorama" -> PAN;
            default -> BREATHING;
        };
    }

    /** "", "plateau", "Studio" → "studio"; a round id stays as it is (lower case, dashes). */
    public static String normalizeLieu(String lieu) {
        if (lieu == null) return STUDIO;
        String l = CameraList.fold(lieu).replaceFirst("^(le |la |l'|du |de la |de l')", "");
        if (l.isEmpty() || l.equals(STUDIO) || l.equals("plateau")) return STUDIO;
        return slug(l);
    }

    /** Zone names as the trial zones write them: letters, digits, dashes ("Équipe bleue" → "equipe-bleue"). */
    public static String normalizeZone(String zone) {
        if (zone == null) return "";
        String z = CameraList.fold(zone);
        if (z.isEmpty() || z.equals("tout") || z.equals("toutes") || z.equals("ensemble") || z.equals("aucune") || z.equals("-"))
            return "";
        return slug(z);
    }

    private static String slug(String folded) {
        return folded.replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
    }

    /** The group the camera is numbered in: "studio", "pont" or "pont:ensuri". */
    public String group() {
        return group(lieu, zone);
    }

    public static String group(String lieu, String zone) {
        String l = normalizeLieu(lieu);
        String z = STUDIO.equals(l) ? "" : normalizeZone(zone);
        return z.isEmpty() ? l : l + ":" + z;
    }

    /** The lieu of a group key ("pont:ensuri" → "pont"). */
    public static String lieuOf(String group) {
        int i = group.indexOf(':');
        return i < 0 ? group : group.substring(0, i);
    }

    /** The zone of a group key ("pont:ensuri" → "ensuri", "pont" → ""). */
    public static String zoneOf(String group) {
        int i = group.indexOf(':');
        return i < 0 ? "" : group.substring(i + 1);
    }

    /** Unique name of the camera: "studio:2", "pont:ensuri:1". */
    public String key() {
        return group() + ":" + number;
    }

    public boolean inStudio() {
        return STUDIO.equals(lieu);
    }

    /** "studio", "manche pont", "manche pont, zone ensuri" */
    public String placeName() {
        return placeName(group());
    }

    public static String placeName(String group) {
        String l = lieuOf(group), z = zoneOf(group);
        if (STUDIO.equals(l)) return "studio";
        return "manche " + l + (z.isEmpty() ? "" : ", zone " + z);
    }

    public CameraSpot withName(String n) {
        return new CameraSpot(number, n, role, world, x, y, z, yaw, pitch, fov, motion, lieu, zone);
    }

    public CameraSpot withRole(String r) {
        return new CameraSpot(number, name, r, world, x, y, z, yaw, pitch, fov, motion, lieu, zone);
    }

    public CameraSpot withFov(float f) {
        return new CameraSpot(number, name, role, world, x, y, z, yaw, pitch, f, motion, lieu, zone);
    }

    public CameraSpot withMotion(String m) {
        return new CameraSpot(number, name, role, world, x, y, z, yaw, pitch, fov, m, lieu, zone);
    }

    public CameraSpot withPlace(String w, double nx, double ny, double nz, float nyaw, float npitch) {
        return new CameraSpot(number, name, role, w, nx, ny, nz, nyaw, npitch, fov, motion, lieu, zone);
    }

    public CameraSpot withRotation(float nyaw, float npitch) {
        return new CameraSpot(number, name, role, world, x, y, z, nyaw, npitch, fov, motion, lieu, zone);
    }

    /**
     * The camera moved to another lieu / zone under that number. A default name ("Caméra 3") follows the new number;
     * a name given by hand stays.
     */
    public CameraSpot withLieu(String newLieu, String newZone, int newNumber) {
        String n = name.equals(defaultName(number)) ? null : name;
        return new CameraSpot(newNumber, n, role, world, x, y, z, yaw, pitch, fov, motion, newLieu, newZone);
    }

    /** "2 · Plan large (large)"; outside the studio the group comes first: "pont:ensuri 1 · Caméra 1". */
    public String label() {
        return (inStudio() ? "" : group() + " ") + number + " · " + name + (role.isEmpty() ? "" : " (" + role + ")");
    }

    public String describe() {
        return String.format(Locale.ROOT, "%s · %.1f %.1f %.1f, regard %.0f° / %.0f°%s, %s", placeName(), x, y, z, yaw, pitch,
                fov > 0 ? ", zoom " + Math.round(fov) + "°" : "", motion);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(number);
        buf.writeUtf(name);
        buf.writeUtf(role);
        buf.writeUtf(world);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeFloat(fov);
        buf.writeUtf(motion);
        buf.writeUtf(lieu);
        buf.writeUtf(zone);
    }

    public static CameraSpot read(FriendlyByteBuf buf) {
        return new CameraSpot(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readUtf(), buf.readUtf(), buf.readUtf());
    }
}
