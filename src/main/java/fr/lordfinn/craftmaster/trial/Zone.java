package fr.lordfinn.craftmaster.trial;

import java.util.List;
import java.util.Locale;

/**
 * A task zone: a box of blocks (both corners included) in one world. The contestants it watches get a warning when
 * they leave it (and the organisers are told). An empty player list watches every contestant of the season.
 *
 * @param world dimension id, "minecraft:overworld"
 */
public record Zone(String name, String world, int x0, int y0, int z0, int x1, int y1, int z1, List<String> players, boolean enabled) {

    public Zone {
        int ax = Math.min(x0, x1), bx = Math.max(x0, x1);
        int ay = Math.min(y0, y1), by = Math.max(y0, y1);
        int az = Math.min(z0, z1), bz = Math.max(z0, z1);
        x0 = ax; x1 = bx; y0 = ay; y1 = by; z0 = az; z1 = bz;
        players = List.copyOf(players);
    }

    /** Whether a position (feet of a player) is inside: block coordinates from x0 to x1 included, so up to x1 + 1. */
    public boolean contains(String world, double x, double y, double z) {
        return this.world.equals(world) && x >= x0 && x < x1 + 1 && y >= y0 && y < y1 + 1 && z >= z0 && z < z1 + 1;
    }

    /** Distance from a point to the box (0 inside). */
    public double distanceOutside(double x, double y, double z) {
        double dx = Math.max(Math.max(x0 - x, 0), x - (x1 + 1));
        double dy = Math.max(Math.max(y0 - y, 0), y - (y1 + 1));
        double dz = Math.max(Math.max(z0 - z, 0), z - (z1 + 1));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Whether it watches this player (an empty list = every contestant). */
    public boolean watches(String player, List<String> contestants) {
        if (!enabled) return false;
        if (players.isEmpty()) return contestants.stream().anyMatch(player::equalsIgnoreCase);
        return players.stream().anyMatch(player::equalsIgnoreCase);
    }

    public int sizeX() { return x1 - x0 + 1; }
    public int sizeY() { return y1 - y0 + 1; }
    public int sizeZ() { return z1 - z0 + 1; }

    public Zone withPlayers(List<String> players) {
        return new Zone(name, world, x0, y0, z0, x1, y1, z1, players, enabled);
    }

    public Zone withEnabled(boolean enabled) {
        return new Zone(name, world, x0, y0, z0, x1, y1, z1, players, enabled);
    }

    /** "12×4×9 blocs (…), tous les candidats" */
    public String describe() {
        return sizeX() + "×" + sizeY() + "×" + sizeZ() + " blocs (" + x0 + " " + y0 + " " + z0 + " → " + x1 + " " + y1 + " " + z1 + ")"
                + (players.isEmpty() ? ", tous les candidats" : ", " + String.join(", ", players)) + (enabled ? "" : ", désactivée");
    }

    /** Zone names: letters, digits, dashes, lower case. */
    public static String normalizeName(String name) {
        String n = name == null ? "" : java.text.Normalizer.normalize(name.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
        return n.isEmpty() ? "zone" : n;
    }
}
