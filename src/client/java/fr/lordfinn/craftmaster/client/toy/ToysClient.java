package fr.lordfinn.craftmaster.client.toy;

import fr.lordfinn.craftmaster.toy.Toys;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Client side of the task toys: the toy and balloon renderers (the item models do the rest, colours included). */
public final class ToysClient {

    private ToysClient() {}

    public static void init() {
        EntityRendererRegistry.register(Toys.TOY, ToyRenderer::new);
        EntityRendererRegistry.register(Toys.BALLOON_ENTITY, BalloonRenderer::new);
    }
}
