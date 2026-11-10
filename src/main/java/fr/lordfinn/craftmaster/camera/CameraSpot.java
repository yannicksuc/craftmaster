package fr.lordfinn.craftmaster.camera;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Locale;

/**
 * One studio camera as the director knows it: its number (1, 2, 3…, what the keys 1–9 pick), its name, an
 * optional role ({@link #WIDE}, {@link #THRONE}, {@link #PODIUM}: what "plan large", "plan sur le trône" and the
 * automatic direction look for), where its lens is and where it looks, its zoom and how it moves on air.
 * Pure data, shared by the server (registry, file) and the clients (show state).
 *
 * @param world  dimension id ("minecraft:overworld")
 * @param x      lens position (the point the broadcast sees from), not the tripod's feet
 * @param fov    field of view in degrees, 0 = the viewer's own setting
 * @param motion one of {@link #MOTIONS}
 */
public record CameraSpot(int number, String name, String role, String world, double x, double y, double z,
                         float yaw, float pitch, float fov, String motion) {

    public static final String WIDE = "large", THRONE = "trone", PODIUM = "podium";
    public static final java.util.List<String> ROLES = java.util.List.of(WIDE, THRONE, PODIUM);

    /** Locked off (no movement), slow breathing (default), slow push-in, slow pan back and forth. */
    public static final String FIXED = "fixe", BREATHING = "respiration", DOLLY = "travelling", PAN = "panoramique";
    public static final java.util.List<String> MOTIONS = java.util.List.of(FIXED, BREATHING, DOLLY, PAN);

    public static final float MIN_FOV = 15, MAX_FOV = 110;

    public CameraSpot {
        name = name == null || name.isBlank() ? defaultName(number) : name.trim();
        role = normalizeRole(role);
        motion = normalizeMotion(motion);
        fov = fov <= 0 ? 0 : Math.max(MIN_FOV, Math.min(MAX_FOV, fov));
        pitch = Math.max(-90, Math.min(90, pitch));
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

    public CameraSpot withName(String n) {
        return new CameraSpot(number, n, role, world, x, y, z, yaw, pitch, fov, motion);
    }

    public CameraSpot withRole(String r) {
        return new CameraSpot(number, name, r, world, x, y, z, yaw, pitch, fov, motion);
    }

    public CameraSpot withFov(float f) {
        return new CameraSpot(number, name, role, world, x, y, z, yaw, pitch, f, motion);
    }

    public CameraSpot withMotion(String m) {
        return new CameraSpot(number, name, role, world, x, y, z, yaw, pitch, fov, m);
    }

    public CameraSpot withPlace(String w, double nx, double ny, double nz, float nyaw, float npitch) {
        return new CameraSpot(number, name, role, w, nx, ny, nz, nyaw, npitch, fov, motion);
    }

    public CameraSpot withRotation(float nyaw, float npitch) {
        return new CameraSpot(number, name, role, world, x, y, z, nyaw, npitch, fov, motion);
    }

    /** "2 · Plan large (large)" */
    public String label() {
        return number + " · " + name + (role.isEmpty() ? "" : " (" + role + ")");
    }

    public String describe() {
        return String.format(Locale.ROOT, "%.1f %.1f %.1f, regard %.0f° / %.0f°%s, %s", x, y, z, yaw, pitch,
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
    }

    public static CameraSpot read(FriendlyByteBuf buf) {
        return new CameraSpot(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readUtf());
    }
}
