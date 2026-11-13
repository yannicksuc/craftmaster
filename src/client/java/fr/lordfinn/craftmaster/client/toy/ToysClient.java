package fr.lordfinn.craftmaster.client.toy;

import fr.lordfinn.craftmaster.toy.Toys;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.color.block.BlockTintSources;

import java.util.List;

/** Client side of the task toys: the toy and balloon renderers, the golf hole's grass colour (the item models do the rest, colours included). */
public final class ToysClient {

    private ToysClient() {}

    public static void init() {
        EntityRendererRegistry.register(Toys.TOY, ToyRenderer::new);
        EntityRendererRegistry.register(Toys.BALLOON_ENTITY, BalloonRenderer::new);
        // The golf hole is the game's grass block with a cup: its grass takes the biome's colour, like the lawn
        BlockColorRegistry.register(List.of(BlockTintSources.grass()), Toys.GOLF_HOLE);
    }
}
