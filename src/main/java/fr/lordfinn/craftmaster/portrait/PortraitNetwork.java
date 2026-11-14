package fr.lordfinn.craftmaster.portrait;

import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.util.Locale;

/**
 * Packets of the portraits: the pose library (server → clients), opening the settings / pose editor screens,
 * and the organisers' changes (client → server, checked against {@link Permissions}).
 */
public final class PortraitNetwork {

    private PortraitNetwork() {}

    private static final int MAX_JSON = 1 << 18;

    /** Server → client: the whole pose library (poses.json). */
    public record Poses(String json) implements CustomPacketPayload {
        public static final Type<Poses> TYPE = new Type<>(CraftMaster.id("portrait_poses"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Poses> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeUtf(p.json, MAX_JSON), buf -> new Poses(buf.readUtf(MAX_JSON)));

        @Override
        public Type<Poses> type() { return TYPE; }
    }

    /** Server → client: open the settings screen (or the pose editor) of a portrait. */
    public record Open(int entityId, boolean poseEditor) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(CraftMaster.id("portrait_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.entityId);
                    buf.writeBoolean(p.poseEditor);
                }, buf -> new Open(buf.readVarInt(), buf.readBoolean()));

        @Override
        public Type<Open> type() { return TYPE; }
    }

    /**
     * Client → server: new settings of a portrait. {@code rank} 0 with a {@code fixedPlayer} = follows that
     * player; {@code hang} negative = automatic length; empty strings = default; {@code window}: Antichamber look, with the depth (px) and size (% of the opening) of its room.
     */
    public record Update(int entityId, int season, int rank, String fixedPlayer, String displayName, String skinUrl,
                         String pose, float hang, boolean window, int windowDepth, int windowSize, float turn) implements CustomPacketPayload {
        public static final Type<Update> TYPE = new Type<>(CraftMaster.id("portrait_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Update> CODEC = CustomPacketPayload.codec(Update::write, Update::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeVarInt(season);
            buf.writeVarInt(rank);
            buf.writeUtf(fixedPlayer, 64);
            buf.writeUtf(displayName, 128);
            buf.writeUtf(skinUrl, 1024);
            buf.writeUtf(pose, 128);
            buf.writeFloat(hang);
            buf.writeBoolean(window);
            buf.writeVarInt(windowDepth);
            buf.writeVarInt(windowSize);
            buf.writeFloat(turn);
        }

        private static Update read(RegistryFriendlyByteBuf buf) {
            return new Update(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64), buf.readUtf(128),
                    buf.readUtf(1024), buf.readUtf(128), buf.readFloat(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readFloat());
        }

        @Override
        public Type<Update> type() { return TYPE; }
    }

    /** Client → server: save a pose (new name or overwrite). */
    public record SavePose(String name, String json) implements CustomPacketPayload {
        public static final Type<SavePose> TYPE = new Type<>(CraftMaster.id("portrait_save_pose"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SavePose> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeUtf(p.name, 128);
                    buf.writeUtf(p.json, 8192);
                }, buf -> new SavePose(buf.readUtf(128), buf.readUtf(8192)));

        @Override
        public Type<SavePose> type() { return TYPE; }
    }

    /** Client → server: delete a pose. */
    public record DeletePose(String name) implements CustomPacketPayload {
        public static final Type<DeletePose> TYPE = new Type<>(CraftMaster.id("portrait_delete_pose"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DeletePose> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeUtf(p.name, 128), buf -> new DeletePose(buf.readUtf(128)));

        @Override
        public Type<DeletePose> type() { return TYPE; }
    }

    /** Client → server: rename a pose (the portraits wearing it follow). */
    public record RenamePose(String from, String to) implements CustomPacketPayload {
        public static final Type<RenamePose> TYPE = new Type<>(CraftMaster.id("portrait_rename_pose"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RenamePose> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeUtf(p.from, 128);
                    buf.writeUtf(p.to, 128);
                }, buf -> new RenamePose(buf.readUtf(128), buf.readUtf(128)));

        @Override
        public Type<RenamePose> type() { return TYPE; }
    }

    /** Client → server: an organiser clicked a portrait out of the game's reach (they hang high above the stage). */
    public record OpenFar(int entityId) implements CustomPacketPayload {
        public static final Type<OpenFar> TYPE = new Type<>(CraftMaster.id("portrait_open_far"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenFar> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeVarInt(p.entityId), buf -> new OpenFar(buf.readVarInt()));

        @Override
        public Type<OpenFar> type() { return TYPE; }
    }

    /** How far an organiser can open a portrait from (blocks). */
    public static final double FAR_REACH = 48;

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(OpenFar.TYPE, OpenFar.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(OpenFar.TYPE, (p, ctx) -> {
            ServerPlayer player = ctx.player();
            if (!Permissions.isAdmin(player)) return;
            if (player.level().getEntity(p.entityId()) instanceof PortraitEntity portrait
                    && portrait.getBoundingBox().getCenter().distanceTo(player.getEyePosition()) <= FAR_REACH + 4)
                openScreen(player, portrait, false);
        });
        PayloadTypeRegistry.clientboundPlay().register(Poses.TYPE, Poses.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Update.TYPE, Update.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SavePose.TYPE, SavePose.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DeletePose.TYPE, DeletePose.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RenamePose.TYPE, RenamePose.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RenamePose.TYPE, (p, ctx) -> renamePose(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(Update.TYPE, (p, ctx) -> update(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(SavePose.TYPE, (p, ctx) -> savePose(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(DeletePose.TYPE, (p, ctx) -> deletePose(ctx.player(), p));
    }

    public static void sendPoses(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, Poses.TYPE)) ServerPlayNetworking.send(player, new Poses(PoseLibraries.server().toJson()));
    }

    /** Opens the settings screen (or the pose editor) on the organiser's game. */
    public static boolean openScreen(ServerPlayer player, PortraitEntity portrait, boolean poseEditor) {
        if (!ServerPlayNetworking.canSend(player, Open.TYPE)) {
            player.sendSystemMessage(Component.translatable("message.craftmaster.no_client_mod").withStyle(ChatFormatting.RED));
            return false;
        }
        sendPoses(player);
        ServerPlayNetworking.send(player, new Open(portrait.getId(), poseEditor));
        return true;
    }

    /** Only http(s) image links are accepted as skins (they are downloaded by every player's game). */
    public static boolean validSkinUrl(String url) {
        if (url == null || url.isEmpty()) return true;
        if (url.length() > 1000) return false;
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void update(ServerPlayer player, Update u) {
        if (!Permissions.isAdmin(player)) return;
        if (!(player.level().getEntity(u.entityId()) instanceof PortraitEntity p) || p.distanceToSqr(player) > 64 * 64) return;
        if (!validSkinUrl(u.skinUrl())) {
            player.sendSystemMessage(Component.translatable("message.craftmaster.portrait_bad_url").withStyle(ChatFormatting.RED));
            return;
        }
        p.setSeason(u.season());
        if (u.rank() > 0 || u.fixedPlayer().isBlank()) p.setRank(Math.max(1, u.rank()));
        else p.setFixedPlayer(u.fixedPlayer());
        p.setDisplayName(u.displayName());
        p.setSkinUrl(u.skinUrl());
        p.setPoseName(u.pose());
        p.setHang(u.hang());
        if (Float.isFinite(u.turn())) p.setTurn(u.turn());
        p.setWindow(u.window());
        p.setWindowDepth(u.windowDepth());
        p.setWindowSize(u.windowSize());
        Portraits.refresh(p);
        player.sendOverlayMessage(Component.translatable("message.craftmaster.portrait_saved").withStyle(ChatFormatting.GREEN));
    }

    private static void savePose(ServerPlayer player, SavePose s) {
        if (!Permissions.isAdmin(player)) return;
        Pose pose;
        try {
            pose = Pose.fromJson(JsonParser.parseString(s.json()).getAsJsonObject());
        } catch (Exception e) {
            return;
        }
        String name = PoseLibraries.server().put(s.name(), pose);
        if (name == null) return;
        Portraits.posesChanged(player.level().getServer());
        player.sendOverlayMessage(Component.translatable("message.craftmaster.pose_saved", name).withStyle(ChatFormatting.GREEN));
    }

    private static void renamePose(ServerPlayer player, RenamePose r) {
        if (!Permissions.isAdmin(player)) return;
        String from = Pose.normalizeName(r.from());
        String to = PoseLibraries.server().rename(from, r.to());
        if (to == null) {
            player.sendOverlayMessage(Component.translatable("message.craftmaster.pose_rename_taken", Pose.normalizeName(r.to())).withStyle(ChatFormatting.RED));
            return;
        }
        // The portraits loaded now wear the new name; the others take it when they load (see Portraits)
        for (net.minecraft.server.level.ServerLevel level : player.level().getServer().getAllLevels())
            for (net.minecraft.world.entity.Entity e : level.getAllEntities())
                if (e instanceof PortraitEntity p && p.poseName().equals(from)) p.setPoseName(to);
        Portraits.posesChanged(player.level().getServer());
        player.sendOverlayMessage(Component.translatable("message.craftmaster.pose_renamed", from, to).withStyle(ChatFormatting.GREEN));
    }

    private static void deletePose(ServerPlayer player, DeletePose d) {
        if (!Permissions.isAdmin(player)) return;
        if (!PoseLibraries.server().remove(d.name())) return;
        Portraits.posesChanged(player.level().getServer());
        player.sendOverlayMessage(Component.translatable("message.craftmaster.pose_deleted", Pose.normalizeName(d.name())).withStyle(ChatFormatting.YELLOW));
    }
}
