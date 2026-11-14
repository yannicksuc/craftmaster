package fr.lordfinn.craftmaster.camera;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.registry.ModContent;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.show.Actor;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The travelling editor of a camera (its menu → a travelling): the steps, each with its travel time, hold,
 * transition, zoom and aim (its own angle, a tracked player, a place), edited from the client; steps added where the
 * player stands and looks. A camera can have several travellings; one can start anywhere (not where the camera
 * stands) and a single step is a fixed shot.
 * <pre>
 * client ← Open(json)   {camera, cameraName, path, paths: [...], steps: [...], duration, ghost, players: [...], points: [...]}
 * client → Edit(json)   {camera, path, action, index?, field?, value?}
 * </pre>
 */
public final class TravellingNetwork {

    private TravellingNetwork() {}

    private static final int MAX = 128 * 1024;

    public record Open(String json) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(CraftMaster.id("travelling_open"));
        public static final StreamCodec<FriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeByteArray(p.json.getBytes(StandardCharsets.UTF_8)),
                buf -> new Open(new String(buf.readByteArray(MAX), StandardCharsets.UTF_8)));

        @Override
        public Type<Open> type() { return TYPE; }
    }

    public record Edit(String json) implements CustomPacketPayload {
        public static final Type<Edit> TYPE = new Type<>(CraftMaster.id("travelling_edit"));
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
            if (!Permissions.canDriveCameras(player)) return;
            try {
                edit(player, JsonParser.parseString(p.json()).getAsJsonObject());
            } catch (RuntimeException e) {
                CraftMaster.LOG.warn("[Travellings] Éditeur : {}", e.toString());
            }
        });
    }

    /** The travellings of camera {@code key}, in name order. */
    public static List<CameraPath> of(String key) {
        return CameraPath.ofCamera(CameraPaths.all(), key);
    }

    /** Opens the editor on travelling {@code path} of camera {@code key} (null: its first, or none yet). */
    public static boolean open(ServerPlayer player, String key, String path) {
        return open(player, key, path, -1);
    }

    /** The same, with step {@code select} selected (a step clicked in the world). */
    public static boolean open(ServerPlayer player, String key, String path, int select) {
        CameraSpot cam = Cameras.get(key);
        if (cam == null || !ServerPlayNetworking.canSend(player, Open.TYPE)) return false;
        List<CameraPath> mine = of(key);
        CameraPath p = path == null ? null : CameraPaths.get(path);
        if (p == null && !mine.isEmpty()) p = mine.getFirst();
        JsonObject o = new JsonObject();
        o.addProperty("camera", key);
        o.addProperty("cameraName", cam.name());
        o.addProperty("number", cam.number());
        o.addProperty("ghost", CameraOptions.ghost(key));
        if (select >= 0) o.addProperty("select", select);
        JsonArray names = new JsonArray();
        mine.forEach(m -> names.add(m.name()));
        o.add("paths", names);
        if (p != null) {
            o.addProperty("path", p.name());
            o.addProperty("duration", p.duration());
            JsonArray steps = new JsonArray();
            for (CameraPath.Step s : p.steps()) {
                JsonObject so = new JsonObject();
                so.addProperty("x", s.x());
                so.addProperty("y", s.y());
                so.addProperty("z", s.z());
                so.addProperty("yaw", s.yaw());
                so.addProperty("pitch", s.pitch());
                so.addProperty("fov", s.fov());
                so.addProperty("travel", s.travel());
                so.addProperty("hold", s.hold());
                so.addProperty("curve", s.curve());
                so.addProperty("target", s.target());
                steps.add(so);
            }
            o.add("steps", steps);
        }
        JsonArray players = new JsonArray();
        for (ServerPlayer pl : player.level().getServer().getPlayerList().getPlayers()) players.add(pl.getGameProfile().name());
        o.add("players", players);
        JsonArray points = new JsonArray();
        var plan = fr.lordfinn.craftmaster.stage.Sequencer.Plan.of(player.level().getServer());
        points.add(place("@centre", plan.place("@centre")));
        for (String n : fr.lordfinn.craftmaster.stage.Scenography.book().plateau.points.keySet()) points.add(place("@point:" + n, plan.place("@point:" + n)));
        o.add("points", points);
        ServerPlayNetworking.send(player, new Open(o.toString()));
        return true;
    }

    /** A named place for the aim picker: {name, at: "x y z"}. */
    private static JsonObject place(String name, net.minecraft.world.phys.Vec3 v) {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("at", v == null ? "" : String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f", v.x, v.y, v.z));
        return o;
    }

    private static void edit(ServerPlayer player, JsonObject o) {
        MinecraftServer server = player.level().getServer();
        String key = o.get("camera").getAsString();
        String name = o.has("path") && !o.get("path").isJsonNull() ? o.get("path").getAsString() : null;
        String action = o.get("action").getAsString();
        CameraSpot cam = Cameras.get(key);
        if (cam == null) return;
        int index = o.has("index") ? o.get("index").getAsInt() : -1;
        switch (action) {
            case "open" -> {
                if (o.has("select")) {
                    open(player, key, name, o.get("select").getAsInt());
                    return;
                }
            }
            case "create" -> {
                // A new travelling of this camera: "cam3-1", "cam3-2"…, starting where the camera stands
                String base = CameraPath.normalizeName("cam" + cam.number() + (cam.lieu().equals(CameraSpot.STUDIO) ? "" : "-" + cam.lieu()));
                String n = base + "-1";
                for (int i = 2; CameraPaths.get(n) != null; i++) n = base + "-" + i;
                CameraPath.Step first = new CameraPath.Step(cam.x(), cam.y(), cam.z(), cam.yaw(), cam.pitch(), cam.fov(), 0, 0, null);
                List<CameraPath.Step> steps = new ArrayList<>(List.of(first));
                // Started from a dot beside the camera (Axiom's tools): a second step there, before or after the camera
                if (o.has("x")) {
                    CameraPath.Step there = new CameraPath.Step(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(),
                            o.get("yaw").getAsFloat(), Math.max(-90, Math.min(90, o.get("pitch").getAsFloat())), cam.fov(), CameraPath.DEFAULT_TRAVEL, 0, null);
                    if (index == 0) {
                        steps.set(0, first.withTravel(CameraPath.DEFAULT_TRAVEL));
                        steps.add(0, there.withTravel(0));
                    } else steps.add(there);
                }
                CameraPaths.put(server, new CameraPath(n, cam.lieu(), cam.world(), steps, key));
                name = n;
                if (o.has("quiet") && o.get("quiet").getAsBoolean()) return;
            }
            case "rename" -> {
                CameraPath p = CameraPaths.get(name);
                String to = CameraPath.normalizeName(o.get("value").getAsString());
                if (p != null && CameraPaths.get(to) == null) {
                    CameraPaths.remove(server, p.name());
                    CameraPaths.put(server, new CameraPath(to, p.lieu(), p.world(), p.steps(), p.camera()));
                    name = to;
                }
            }
            case "delete" -> {
                if (name != null) CameraPaths.remove(server, name);
                name = null;
            }
            case "ghost" -> {
                CameraOptions.setGhost(key, o.get("value").getAsBoolean());
                Cameras.update(server, key, s -> s);
            }
            // Steps where the player stands and looks: at the end, before step index, or replacing it
            case "addHere", "insertHere", "replaceHere" -> {
                CameraPath p = CameraPaths.get(name);
                if (p == null || !p.world().equals(Busts.worldId(player.level()))) return;
                List<CameraPath.Step> steps = new ArrayList<>(p.steps());
                CameraPath.Step here = new CameraPath.Step(player.getX(), player.getEyeY(), player.getZ(), player.getYRot(), player.getXRot(),
                        0, steps.size() >= 2 ? steps.getLast().travel() : CameraPath.DEFAULT_TRAVEL, 0, null);
                if (action.equals("replaceHere") && index >= 0 && index < steps.size()) {
                    CameraPath.Step old = steps.get(index);
                    steps.set(index, old.withPlace(here.x(), here.y(), here.z(), here.yaw(), here.pitch()));
                } else if (action.equals("insertHere") && index >= 0 && index <= steps.size()) steps.add(index, here);
                else steps.add(here);
                CameraPaths.put(server, p.withSteps(steps));
            }
            // A step placed in the world (Axiom's gizmos): moved / turned, or inserted at index with its pose
            case "pose", "insertAt" -> {
                CameraPath p = CameraPaths.get(name);
                if (p == null || index < 0) return;
                List<CameraPath.Step> steps = new ArrayList<>(p.steps());
                double x = o.get("x").getAsDouble(), y = o.get("y").getAsDouble(), z = o.get("z").getAsDouble();
                float yaw = o.get("yaw").getAsFloat(), pitch = Math.max(-90, Math.min(90, o.get("pitch").getAsFloat()));
                if (action.equals("pose")) {
                    if (index >= steps.size()) return;
                    CameraPath.Step s = steps.get(index);
                    // Turned by hand: it stops tracking (its own angle again)
                    boolean turned = Math.abs(s.yaw() - yaw) > 0.01 || Math.abs(s.pitch() - pitch) > 0.01;
                    CameraPath.Step moved = s.withPlace(x, y, z, yaw, pitch);
                    steps.set(index, turned ? moved.withTarget("") : moved);
                } else {
                    index = Math.min(index, steps.size());
                    // Inside the path (split = how far along the next step's travel): the travel time is shared, the
                    // timing of the rest stays the same; at an end, the neighbour's travel
                    double split = o.has("split") ? o.get("split").getAsDouble() : -1;
                    double travel = steps.isEmpty() ? 0 : steps.size() >= 2 ? steps.get(Math.max(1, Math.min(steps.size() - 1, index))).travel() : CameraPath.DEFAULT_TRAVEL;
                    float fov = o.has("fov") ? o.get("fov").getAsFloat() : index > 0 ? steps.get(index - 1).fov() : steps.isEmpty() ? 0 : steps.getFirst().fov();
                    if (split > 0 && split < 1 && index > 0 && index < steps.size()) {
                        CameraPath.Step next = steps.get(index);
                        travel = next.travel() * split;
                        steps.set(index, next.withTravel(next.travel() - travel));
                    }
                    steps.add(index, new CameraPath.Step(x, y, z, yaw, pitch, fov, index == 0 ? 0 : travel, 0, null));
                    // A new first step: the old first one now needs a travel
                    if (index == 0 && steps.size() > 1 && steps.get(1).travel() <= 0) steps.set(1, steps.get(1).withTravel(CameraPath.DEFAULT_TRAVEL));
                }
                CameraPaths.put(server, p.withSteps(steps));
                // From the world: no menu opens
                if (o.has("quiet") && o.get("quiet").getAsBoolean()) return;
            }
            case "fromCamera" -> {
                // The camera's own place as a step (the move then starts or ends where it stands)
                CameraPath p = CameraPaths.get(name);
                if (p == null) return;
                List<CameraPath.Step> steps = new ArrayList<>(p.steps());
                steps.add(Math.max(0, Math.min(steps.size(), index < 0 ? 0 : index)),
                        new CameraPath.Step(cam.x(), cam.y(), cam.z(), cam.yaw(), cam.pitch(), cam.fov(), CameraPath.DEFAULT_TRAVEL, 0, null));
                CameraPaths.put(server, p.withSteps(steps));
            }
            case "set" -> {
                CameraPath p = CameraPaths.get(name);
                if (p == null || index < 0 || index >= p.steps().size()) return;
                List<CameraPath.Step> steps = new ArrayList<>(p.steps());
                CameraPath.Step s = steps.get(index);
                JsonElement v = o.get("value");
                steps.set(index, switch (o.get("field").getAsString()) {
                    case "travel" -> s.withTravel(v.getAsDouble());
                    case "hold" -> s.withHold(v.getAsDouble());
                    case "curve" -> s.withCurve(v.getAsString());
                    case "fov" -> s.withFov(v.getAsFloat());
                    case "target" -> s.withTarget(v.getAsString());
                    case "angle" -> s.withPlace(s.x(), s.y(), s.z(), v.getAsJsonObject().get("yaw").getAsFloat(), v.getAsJsonObject().get("pitch").getAsFloat());
                    default -> s;
                });
                CameraPaths.put(server, p.withSteps(steps));
            }
            // The step looks where the player looks (it stays in place), like a camera's "where I look"
            case "lookHere" -> {
                CameraPath p = CameraPaths.get(name);
                if (p == null || index < 0 || index >= p.steps().size()) return;
                List<CameraPath.Step> steps = new ArrayList<>(p.steps());
                CameraPath.Step s = steps.get(index);
                steps.set(index, s.withPlace(s.x(), s.y(), s.z(), player.getYRot(), player.getXRot()).withTarget(""));
                CameraPaths.put(server, p.withSteps(steps));
            }
            case "move" -> {
                CameraPath p = CameraPaths.get(name);
                int to = index + o.get("value").getAsInt();
                if (p == null || index < 0 || index >= p.steps().size() || to < 0 || to >= p.steps().size()) return;
                List<CameraPath.Step> steps = new ArrayList<>(p.steps());
                steps.add(to, steps.remove(index));
                CameraPaths.put(server, p.withSteps(steps));
            }
            case "remove" -> {
                CameraPath p = CameraPaths.get(name);
                if (p == null || index < 0 || index >= p.steps().size() || p.steps().size() <= 1) return;
                List<CameraPath.Step> steps = new ArrayList<>(p.steps());
                CameraPath.Step gone = steps.remove(index);
                // From the world: the next step takes its travel time (the inverse of a step added on the path)
                boolean quiet = o.has("quiet") && o.get("quiet").getAsBoolean();
                if (quiet && index > 0 && index < steps.size()) steps.set(index, steps.get(index).withTravel(steps.get(index).travel() + gone.travel()));
                CameraPaths.put(server, p.withSteps(steps));
                if (quiet) return;
            }
            case "goTo" -> {
                CameraPath p = CameraPaths.get(name);
                ServerLevel level = p == null ? null : Busts.level(server, p.world());
                if (level == null || index < 0 || index >= p.steps().size()) return;
                CameraPath.Step s = p.steps().get(index);
                player.teleportTo(level, s.x(), s.y() - player.getEyeHeight(), s.z(), Set.of(), s.yaw(), s.pitch(), false);
                return;
            }
            case "preview" -> {
                CameraPath p = CameraPaths.get(name);
                if (p == null || !ServerPlayNetworking.canSend(player, BroadcastNetwork.ShowCue.TYPE)) return;
                // A fixed shot (one step) is shown for its hold, at least 3 seconds
                double seconds = p.steps().size() < 2 ? Math.max(3, p.duration()) : p.duration();
                ServerPlayNetworking.send(player, new BroadcastNetwork.ShowCue(BroadcastNetwork.Cues.PATH_PREVIEW, p.name(), "", "", (int) Math.ceil(seconds * 20)));
                PathRecording.hide(p.name());
                return;
            }
            case "air" -> {
                java.util.Map<String, Object> act = new java.util.HashMap<>();
                act.put("type", "camera.path");
                act.put("path", name);
                ActionExecutor.execute(server, Actor.of(player, "jeu"), List.of(act));
                return;
            }
            case "marker" -> {
                if (name != null) player.getInventory().add(PathMarkerItem.bound(ModContent.PATH_MARKER, name));
                return;
            }
            default -> {
                return;
            }
        }
        open(player, key, name, o.has("select") ? o.get("select").getAsInt() : -1);
    }
}
