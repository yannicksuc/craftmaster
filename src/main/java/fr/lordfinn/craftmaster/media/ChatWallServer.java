package fr.lordfinn.craftmaster.media;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.studio.ImageScreenEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of the chat wall ({@link ChatWall}): keeps the last messages (Twitch, forwarded by the broadcast
 * game; the players' chat), and sends them to the players who see a chat wall screen: the whole history when they
 * start seeing one (or a screen turns into one), then each new message. The games only draw.
 */
public final class ChatWallServer {

    private ChatWallServer() {}

    private static final ChatWall.History HISTORY = new ChatWall.History();
    private static long nextId;
    /** Players already told that their Twitch forwarding is refused (logged once each). */
    private static final Set<UUID> REFUSED = new HashSet<>();

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(MediaNetwork.TwitchChatIn.TYPE, (p, ctx) -> twitch(ctx.player(), p));
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
                game(sender.level().getServer(), sender.getGameProfile().name(), message.signedContent()));
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof ImageScreenEntity s && ChatWall.mode(s.url()) != null) sendHistory(List.of(player));
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            synchronized (HISTORY) {
                for (ChatWall.Source src : ChatWall.Source.values()) HISTORY.clear(src);
            }
            REFUSED.clear();
        });
    }

    /** A player wrote in the game's chat. */
    static void game(MinecraftServer server, String author, String text) {
        String t = ChatWall.cleanText(text);
        if (t == null) return;
        String a = ChatWall.cleanAuthor(author);
        add(server, new ChatWall.Line(ChatWall.Source.JEU, "jeu-" + (nextId++), a, ChatWall.nameColor(a), t));
    }

    /** The broadcast game forwards a line of its Twitch chat: organisers only (craftmaster:image or admin). */
    private static void twitch(ServerPlayer player, MediaNetwork.TwitchChatIn p) {
        if (!Permissions.canShowImages(player)) {
            if (REFUSED.add(player.getUUID()))
                CraftMaster.LOG.info("[Chat] Chat Twitch de {} ignoré : il faut craftmaster:image ou être organisateur", player.getGameProfile().name());
            return;
        }
        MinecraftServer server = player.level().getServer();
        switch (p.kind()) {
            case MediaNetwork.TwitchChatIn.MESSAGE -> {
                String t = ChatWall.cleanText(p.text());
                if (t == null || p.id().isBlank()) return;
                String a = ChatWall.cleanAuthor(p.author());
                add(server, new ChatWall.Line(ChatWall.Source.TWITCH, "tw-" + p.id().strip(), a, ChatWall.authorColor(a, p.color()), t));
            }
            case MediaNetwork.TwitchChatIn.DELETE -> changed(server, sync(() -> HISTORY.removeId("tw-" + p.id().strip())));
            case MediaNetwork.TwitchChatIn.BAN -> changed(server, sync(() -> HISTORY.removeAuthor(ChatWall.Source.TWITCH, p.author().strip())));
            case MediaNetwork.TwitchChatIn.CLEAR -> changed(server, sync(() -> HISTORY.clear(ChatWall.Source.TWITCH)));
            default -> { }
        }
    }

    private static boolean sync(java.util.function.BooleanSupplier op) {
        synchronized (HISTORY) {
            return op.getAsBoolean();
        }
    }

    /** A new message: kept, then sent to whoever sees a chat wall showing its source. */
    private static void add(MinecraftServer server, ChatWall.Line line) {
        if (!sync(() -> HISTORY.add(line))) return;
        send(viewers(server, line.source()), new MediaNetwork.ChatLines(false, List.of(line)));
    }

    /** Messages were removed (moderation): everyone who sees a chat wall gets the history again. */
    private static void changed(MinecraftServer server, boolean removed) {
        if (removed) sendHistory(viewers(server, null));
    }

    /** A screen became a chat wall: the players who see it get the history. */
    public static void screenShown(ImageScreenEntity screen) {
        sendHistory(PlayerLookup.tracking(screen));
    }

    /** The kept messages, oldest first. */
    public static List<ChatWall.Line> history() {
        synchronized (HISTORY) {
            return HISTORY.all();
        }
    }

    private static void sendHistory(Collection<ServerPlayer> players) {
        if (players.isEmpty()) return;
        send(players, new MediaNetwork.ChatLines(true, history()));
    }

    /** The players who see a chat wall showing {@code source} (any chat wall when null). */
    private static Set<ServerPlayer> viewers(MinecraftServer server, ChatWall.Source source) {
        Set<ServerPlayer> out = new HashSet<>();
        if (server == null) return out;
        for (ImageScreenEntity s : MediaServer.screens(server)) {
            ChatWall.Mode m = ChatWall.mode(s.url());
            if (m != null && (source == null || m.shows(source))) out.addAll(PlayerLookup.tracking(s));
        }
        return out;
    }

    private static void send(Collection<ServerPlayer> players, MediaNetwork.ChatLines packet) {
        for (ServerPlayer p : players) if (ServerPlayNetworking.canSend(p, MediaNetwork.ChatLines.TYPE)) ServerPlayNetworking.send(p, packet);
    }
}
