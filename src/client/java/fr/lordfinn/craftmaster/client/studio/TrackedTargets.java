package fr.lordfinn.craftmaster.client.studio;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import fr.lordfinn.craftmaster.camera.TrackTargets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The entities the travelling steps track (selectors like {@code @n[type=sheep]}), as the server found them
 * (TrackTargets): followed here from the client's own copy of the entity, so the camera moves smoothly. Also the
 * selector's autocompletion in the travelling editor, the same as in the chat's commands.
 */
public final class TrackedTargets {

    private TrackedTargets() {}

    private static Map<String, Integer> ids = new HashMap<>();

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(TrackTargets.Targets.TYPE, (p, ctx) -> {
            Map<String, Integer> m = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : JsonParser.parseString(p.json()).getAsJsonObject().entrySet()) m.put(e.getKey(), e.getValue().getAsInt());
            ids = m;
        });
    }

    /** The entity a track key (CameraPath.trackKey) gives now (null: none, or not loaded here). */
    public static Entity entity(String key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || key == null) return null;
        Integer id = ids.get(key);
        if (id != null && id >= 0) {
            Entity e = mc.level.getEntity(id);
            if (e != null) return e;
        }
        String name = fr.lordfinn.craftmaster.camera.CameraPath.trackTarget(key).strip();
        for (var pl : mc.level.players()) if (pl.getGameProfile().name().equalsIgnoreCase(name)) return pl;
        return null;
    }

    /** Suggestions for a selector being typed (text up to the cursor), as in the chat: what to insert, and where. */
    public static List<Suggestion> suggest(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return List.of();
        SharedSuggestionProvider source = mc.getConnection().getSuggestionsProvider();
        StringReader reader = new StringReader(text);
        EntitySelectorParser parser = new EntitySelectorParser(reader, true);
        try {
            parser.parse();
        } catch (Exception ignored) {
            // Incomplete: the parser still knows what may come next
        }
        try {
            var future = parser.fillSuggestions(new SuggestionsBuilder(text, 0), b -> SharedSuggestionProvider.suggest(source.getOnlinePlayerNames(), b));
            return new ArrayList<>(future.join().getList());
        } catch (Exception e) {
            return List.of();
        }
    }
}
