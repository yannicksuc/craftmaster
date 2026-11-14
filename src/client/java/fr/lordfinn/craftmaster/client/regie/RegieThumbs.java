package fr.lordfinn.craftmaster.client.regie;

import com.mojang.blaze3d.platform.NativeImage;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.regie.Thumbnails;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

/** The previews of the shots received from the server (camera key → texture), for the director's camera buttons. */
public final class RegieThumbs {

    private RegieThumbs() {}

    private static final Map<String, Identifier> TEXTURES = new HashMap<>();

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Thumbnails.Image.TYPE, (p, ctx) -> ctx.client().execute(() -> load(p.key(), p.png())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(RegieThumbs::clear));
    }

    /** The texture of camera {@code key}'s preview, or null if none came yet. */
    public static Identifier get(String key) {
        return TEXTURES.get(key);
    }

    private static void load(String key, byte[] png) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            Identifier id = CraftMaster.id("regie_thumb/" + key.replaceAll("[^a-z0-9_.-]", "_"));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "craftmaster preview " + key, image));
            TEXTURES.put(key, id);
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Régie] Vignette de {} illisible", key, e);
        }
    }

    private static void clear() {
        for (Identifier id : TEXTURES.values()) Minecraft.getInstance().getTextureManager().release(id);
        TEXTURES.clear();
    }
}
