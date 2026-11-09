package fr.lordfinn.craftmaster.show;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.curtain.Curtains;
import fr.lordfinn.craftmaster.data.BlockSpot;
import fr.lordfinn.craftmaster.data.Partie;
import fr.lordfinn.craftmaster.data.Stage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;
import java.util.Optional;

/**
 * The stage in the world: named curtains (one of their blocks is registered) and sceneries, saved as vanilla
 * structures ({@code craftmaster:scene/<name>} in the world's generated structures) and placed at the stage anchor.
 */
public final class StageControl {

    private StageControl() {}

    /** Biggest scenery saved at once, per axis (vanilla structure blocks allow 48). */
    public static final int MAX_SCENE_SIZE = 64;

    /**
     * Opens or closes a registered curtain.
     *
     * @return null if it worked, else why not (French)
     */
    public static String moveCurtain(MinecraftServer server, String name, boolean open) {
        BlockSpot b = CraftMaster.data().stage().curtains.get(name);
        if (b == null) return "Rideau « " + name + " » inconnu.";
        ServerLevel level = Busts.level(server, b.world());
        if (level == null) return "Monde introuvable pour le rideau « " + name + " » : " + b.world();
        BlockPos pos = new BlockPos(b.x(), b.y(), b.z());
        if (!level.isLoaded(pos)) return "Le rideau « " + name + " » n'est pas chargé (personne à côté).";
        if (Curtains.animate(level, pos, open) == null) return "Plus de rideau en " + b.describe() + " (rideau « " + name + " »).";
        return null;
    }

    public static Identifier sceneId(String name) {
        return CraftMaster.id("scene/" + Partie.slug(name));
    }

    /**
     * Saves the blocks between the two corners as the scenery {@code name} (structure voids are skipped, so they
     * leave the stage as it is when placed).
     *
     * @return the size saved
     */
    public static Vec3i saveScene(ServerLevel level, String name, BlockPos a, BlockPos b) {
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        Vec3i size = new Vec3i(Math.abs(a.getX() - b.getX()) + 1, Math.abs(a.getY() - b.getY()) + 1, Math.abs(a.getZ() - b.getZ()) + 1);
        StructureTemplateManager manager = level.getServer().getStructureTemplateManager();
        StructureTemplate template = manager.getOrCreate(sceneId(name));
        template.fillFromWorld(level, min, size, false, List.of(Blocks.STRUCTURE_VOID));
        manager.save(sceneId(name));
        return size;
    }

    /**
     * Places a saved scenery at the stage anchor. The box of the scenery placed before is emptied first when it was
     * bigger, so nothing of it is left behind.
     *
     * @param previous the scenery placed before (to clear), or null
     * @return null if it worked, else why not (French)
     */
    public static String placeScene(MinecraftServer server, String name, String previous) {
        Stage stage = CraftMaster.data().stage();
        if (stage.anchor == null) return "Pas d'ancre de scène.";
        ServerLevel level = Busts.level(server, stage.anchor.world());
        if (level == null) return "Monde introuvable pour l'ancre : " + stage.anchor.world();
        Optional<StructureTemplate> template = server.getStructureTemplateManager().get(sceneId(name));
        if (template.isEmpty()) return "Fichier du décor « " + name + " » introuvable.";
        BlockPos anchor = new BlockPos(stage.anchor.x(), stage.anchor.y(), stage.anchor.z());
        Stage.Scene old = previous == null ? null : stage.scenes.get(previous);
        if (old != null && !previous.equals(name)) {
            for (BlockPos p : BlockPos.betweenClosed(anchor, anchor.offset(old.sizeX() - 1, old.sizeY() - 1, old.sizeZ() - 1)))
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        template.get().placeInWorld(level, anchor, anchor, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_CLIENTS);
        return null;
    }
}
