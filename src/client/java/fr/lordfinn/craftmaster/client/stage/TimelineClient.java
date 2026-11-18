package fr.lordfinn.craftmaster.client.stage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.stage.TimelineNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Opens the stage timeline editor when the server sends one (/cm sequence editer), and feeds it while it is open. */
public final class TimelineClient {

    private TimelineClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(TimelineNetwork.State.TYPE, (p, ctx) -> {
            JsonObject o = JsonParser.parseString(p.json()).getAsJsonObject();
            Minecraft mc = ctx.client();
            String kind = o.has("kind") ? o.get("kind").getAsString() : "";
            TimelineScreen open = mc.gui.screen() instanceof TimelineScreen s ? s : null;
            switch (kind) {
                case "open" -> {
                    if (open != null) open.open(o);
                    else mc.gui.setScreen(new TimelineScreen(o));
                }
                case "playhead" -> {
                    if (open != null) open.playhead(o);
                }
                case "captured" -> {
                    if (open != null) open.captured(o);
                }
                default -> {
                }
            }
        });
    }
}
