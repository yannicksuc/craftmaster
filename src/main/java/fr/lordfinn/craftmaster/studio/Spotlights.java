package fr.lordfinn.craftmaster.studio;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;

import java.util.List;

/**
 * Orders given to every loaded spotlight at once (commands with {@code all}, the voice control's
 * {@code spotlight.*} actions). Each returns a French line saying what was done.
 */
public final class Spotlights {

    private Spotlights() {}

    /** All spotlights follow a player (by exact name, case ignored). */
    public static String follow(MinecraftServer server, String player) {
        ServerPlayer p = server.getPlayerList().getPlayerByName(player);
        String name = p != null ? p.getGameProfile().name() : player;
        List<SpotlightBlockEntity> all = SpotlightBlockEntity.loaded();
        for (SpotlightBlockEntity s : all) {
            s.follow(name);
            SpotlightBlock.setLit(s.getLevel(), s.getBlockPos(), s.getBlockState(), true);
        }
        if (all.isEmpty()) return "Aucun projecteur chargé.";
        return all.size() + " projecteur(s) sur " + name + (p == null ? " (pas connecté pour l'instant)" : "") + ".";
    }

    /** All spotlights stop following (they keep their last direction). */
    public static String stop(MinecraftServer server) {
        List<SpotlightBlockEntity> all = SpotlightBlockEntity.loaded();
        for (SpotlightBlockEntity s : all) s.follow("");
        return all.isEmpty() ? "Aucun projecteur chargé." : all.size() + " projecteur(s) fixe(s).";
    }

    public static String setLit(MinecraftServer server, boolean lit) {
        List<SpotlightBlockEntity> all = SpotlightBlockEntity.loaded();
        for (SpotlightBlockEntity s : all) SpotlightBlock.setLit(s.getLevel(), s.getBlockPos(), s.getBlockState(), lit);
        return all.isEmpty() ? "Aucun projecteur chargé." : all.size() + " projecteur(s) " + (lit ? "allumé(s)." : "éteint(s).");
    }

    public static String color(MinecraftServer server, DyeColor dye) {
        List<SpotlightBlockEntity> all = SpotlightBlockEntity.loaded();
        for (SpotlightBlockEntity s : all) s.setColor(SpotlightBlock.beamColor(dye));
        return all.isEmpty() ? "Aucun projecteur chargé." : all.size() + " projecteur(s) en " + dye.getName() + ".";
    }
}
