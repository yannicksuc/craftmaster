package fr.lordfinn.craftmaster.studio;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The spotlight's menu (organisers: sneak + right-click on a spotlight). The client reads the spotlight's settings
 * from its own (synced) block entity every frame; the server only opens the menu (with the names to pick from) and
 * applies the changes.
 * <pre>
 * client ← Open(json)   {x, y, z, follow: [...rules, online players], names: [...spotlight names]}
 * client → Edit(json)   {x, y, z, what: "lit"|"color"|"intensity"|"cone"|"name"|"follow"|"effect"|"speed"|"turn"|"tilt"|"flip"|"look"|"me", value}
 * </pre>
 */
public final class SpotlightNetwork {

    private SpotlightNetwork() {}

    private static final int MAX = 16 * 1024;

    public record Open(String json) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(CraftMaster.id("spotlight_open"));
        public static final StreamCodec<FriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeByteArray(p.json.getBytes(StandardCharsets.UTF_8)),
                buf -> new Open(new String(buf.readByteArray(MAX), StandardCharsets.UTF_8)));

        @Override
        public Type<Open> type() { return TYPE; }
    }

    public record Edit(String json) implements CustomPacketPayload {
        public static final Type<Edit> TYPE = new Type<>(CraftMaster.id("spotlight_edit"));
        public static final StreamCodec<FriendlyByteBuf, Edit> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeByteArray(p.json.getBytes(StandardCharsets.UTF_8)),
                buf -> new Edit(new String(buf.readByteArray(MAX), StandardCharsets.UTF_8)));

        @Override
        public Type<Edit> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Edit.TYPE, Edit.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Edit.TYPE, (p, ctx) -> {
            ServerPlayer player = ctx.player();
            if (!Permissions.isAdmin(player)) return;
            try {
                edit(player, JsonParser.parseString(p.json()).getAsJsonObject());
            } catch (RuntimeException e) {
                CraftMaster.LOG.warn("[Projecteur] Menu : {}", e.toString());
            }
        });
    }

    /** Opens the menu of the spotlight at {@code pos}; false if the player's game lacks the mod. */
    public static boolean open(ServerPlayer player, BlockPos pos) {
        if (!ServerPlayNetworking.canSend(player, Open.TYPE)) return false;
        JsonObject o = new JsonObject();
        o.addProperty("x", pos.getX());
        o.addProperty("y", pos.getY());
        o.addProperty("z", pos.getZ());
        JsonArray follow = new JsonArray();
        for (String r : List.of("", "@scene", "@maitre", "@rang:1", "@rang:2", "@rang:3", "@dernier", "@hasard", "@public", "@moment", "@candidat", "@proche"))
            follow.add(r);
        for (ServerPlayer p : player.level().getServer().getPlayerList().getPlayers()) follow.add(p.getGameProfile().name());
        o.add("follow", follow);
        JsonArray names = new JsonArray();
        Spotlights.names().forEach(names::add);
        o.add("names", names);
        ServerPlayNetworking.send(player, new Open(o.toString()));
        return true;
    }

    private static void edit(ServerPlayer player, JsonObject o) {
        BlockPos pos = new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
        if (player.position().distanceTo(Vec3.atCenterOf(pos)) > 128) return;
        if (!(player.level().getBlockEntity(pos) instanceof SpotlightBlockEntity s)) return;
        String what = o.get("what").getAsString();
        var v = o.get("value");
        double fade = 0.25;
        switch (what) {
            case "lit" -> SpotlightBlock.setLit(s.getLevel(), pos, s.getBlockState(), v.getAsBoolean());
            case "color" -> {
                Integer c = Spotlights.parseColor(v.getAsString());
                if (c != null) Spotlights.apply(s, new Spotlights.Settings(null, c, null, null, null, null, null, null), fade);
            }
            case "intensity" -> Spotlights.apply(s, new Spotlights.Settings(null, null, Math.max(0, Math.min(100, v.getAsInt())), null, null, null, null, null), fade);
            case "cone" -> Spotlights.apply(s, new Spotlights.Settings(null, null, null, v.getAsFloat(), null, null, null, null), fade);
            case "name" -> s.setName(v.getAsString());
            case "follow" -> {
                String f = v.getAsString();
                String rule = SpotlightFollow.isNobody(f) ? "" : SpotlightFollow.parse(f) != null ? SpotlightFollow.parse(f) : f.strip();
                Spotlights.apply(s, new Spotlights.Settings(null, null, null, null, rule, null, null, null), 0.5);
            }
            case "effect" -> s.setEffect(v.getAsString(), s.effectSpeed(), s.effectPhase());
            case "speed" -> s.setEffect(s.effect(), v.getAsFloat(), s.effectPhase());
            case "turn" -> Spotlights.turn(Spotlights.at(s), v.getAsFloat(), 0, false, 0.4);
            case "tilt" -> Spotlights.turn(Spotlights.at(s), 0, v.getAsFloat(), false, 0.4);
            case "flip" -> Spotlights.turn(Spotlights.at(s), 0, 0, true, 0.8);
            case "angle" -> {
                JsonObject a = v.getAsJsonObject();
                Spotlights.apply(s, new Spotlights.Settings(null, null, null, null, "", null, a.get("yaw").getAsFloat(), a.get("pitch").getAsFloat()), 0.4);
            }
            // Onto what the player looks at (the crosshair's block), or onto the player
            case "look" -> {
                net.minecraft.world.phys.HitResult hit = player.pick(96, 0, false);
                Vec3 at = hit.getLocation();
                Spotlights.apply(s, new Spotlights.Settings(null, null, null, null, "", at, null, null), 0.5);
            }
            case "me" -> Spotlights.apply(s, new Spotlights.Settings(null, null, null, null, "", SpotlightBlockEntity.followPoint(player.position(), player.getBbHeight()), null, null), 0.6);
            default -> {
            }
        }
    }
}
