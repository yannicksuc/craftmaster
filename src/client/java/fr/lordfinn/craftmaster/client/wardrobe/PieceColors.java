package fr.lordfinn.craftmaster.client.wardrobe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.wardrobe.OutfitColors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The colours of a piece, for the colour tags: its dye (leather) or the pixels of its item icon, found through the
 * resource packs like the game does (items/&lt;id&gt;.json → its model → the model's layer0 texture). Cached by item.
 */
final class PieceColors {

    private PieceColors() {}

    private static final Map<String, List<String>> CACHE = new HashMap<>();

    static List<String> of(ItemStack s) {
        if (s.isEmpty()) return List.of();
        DyedItemColor dye = s.get(DataComponents.DYED_COLOR);
        if (dye != null) return List.of(OutfitColors.classify(dye.rgb()));
        Identifier id = BuiltInRegistries.ITEM.getKey(s.getItem());
        return CACHE.computeIfAbsent(id.toString(), k -> read(id));
    }

    /** Forget everything (resource packs reloaded). */
    static void clear() {
        CACHE.clear();
    }

    private static List<String> read(Identifier item) {
        try {
            ResourceManager rm = Minecraft.getInstance().getResourceManager();
            String model = itemModel(rm, item);
            if (model == null) return List.of();
            String texture = layer0(rm, Identifier.tryParse(model), 0);
            if (texture == null) return List.of();
            Identifier tex = Identifier.tryParse(texture);
            if (tex == null) return List.of();
            Optional<Resource> png = rm.getResource(Identifier.fromNamespaceAndPath(tex.getNamespace(), "textures/" + tex.getPath() + ".png"));
            if (png.isEmpty()) return List.of();
            BufferedImage img;
            try (InputStream in = png.get().open()) {
                img = ImageIO.read(in);
            }
            if (img == null) return List.of();
            // Animated or tall textures: the first square frame
            int w = img.getWidth(), h = Math.min(img.getHeight(), w);
            int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
            return OutfitColors.ofImage(argb, w, h);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** The model of an item definition: the first "model" of a minecraft:model entry. */
    private static String itemModel(ResourceManager rm, Identifier item) throws Exception {
        JsonElement def = json(rm, Identifier.fromNamespaceAndPath(item.getNamespace(), "items/" + item.getPath() + ".json"));
        if (def == null) return null;
        return findModel(def);
    }

    private static String findModel(JsonElement e) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("type") && o.get("type").getAsString().endsWith("model") && o.has("model") && o.get("model").isJsonPrimitive())
                return o.get("model").getAsString();
            for (var entry : o.entrySet()) {
                String m = findModel(entry.getValue());
                if (m != null) return m;
            }
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                String m = findModel(x);
                if (m != null) return m;
            }
        }
        return null;
    }

    /** The layer0 texture of a model, following its parents. */
    private static String layer0(ResourceManager rm, Identifier model, int depth) throws Exception {
        if (model == null || depth > 6) return null;
        JsonElement m = json(rm, Identifier.fromNamespaceAndPath(model.getNamespace(), "models/" + model.getPath() + ".json"));
        if (m == null || !m.isJsonObject()) return null;
        JsonObject o = m.getAsJsonObject();
        if (o.has("textures") && o.getAsJsonObject("textures").has("layer0")) {
            String t = o.getAsJsonObject("textures").get("layer0").getAsString();
            return t.contains(":") ? t : "minecraft:" + t;
        }
        if (o.has("parent")) {
            String p = o.get("parent").getAsString();
            return layer0(rm, Identifier.tryParse(p.contains(":") ? p : "minecraft:" + p), depth + 1);
        }
        return null;
    }

    private static JsonElement json(ResourceManager rm, Identifier file) throws Exception {
        Optional<Resource> r = rm.getResource(file);
        if (r.isEmpty()) return null;
        try (InputStream in = r.get().open()) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }
}
