package fr.lordfinn.craftmaster.show;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.trial.Judging;
import fr.lordfinn.craftmaster.trial.kit.Finish;
import fr.lordfinn.craftmaster.trial.kit.TrialRun;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spectators of a trial: the contestants who are done (the apple eaten, the timer stopped…) and the connected players
 * who are not contestants may, until the trial is over (« Revenir au plateau »), either watch one contestant still
 * playing (through his eyes, Minecraft's spectator camera) or the show's broadcast as it goes on air (the feed of
 * the automatic direction, the intros…), and comment it by voice with the game master ({@link MasterVoice}).
 * <p>
 * Allowed by default; the master can forbid it for everything or for one trial, before or during it (a forbidden
 * trial sends its spectators back at once). Safeguards: a spectator is in spectator mode (no interaction, invisible,
 * never on the broadcast), held on his camera; his voice is in an isolated group (no contestant playing hears it);
 * his chat only reaches those who are not playing; he gets back his place and game mode when he leaves, when the
 * trial is over and if he disconnects.
 */
public final class Spectators {

    private Spectators() {}

    /** Where a spectator was, to put him back. */
    record Back(String world, double x, double y, double z, float yaw, float pitch, String gamemode) {}

    /** A spectator: watching {@code target} (a contestant) or the broadcast ({@code target} null). */
    record Watch(String target, Back back) {}

    private static final Map<UUID, Watch> WATCHING = new LinkedHashMap<>();
    /** Already offered for this trial (no repeat). */
    private static final Set<UUID> OFFERED = new LinkedHashSet<>();

    /** Saved: allowed at all, and the trials (round ids) where it is forbidden. */
    static final class Settings {
        boolean enabled = true;
        Set<String> off = new LinkedHashSet<>();
    }

    private static Settings settings = new Settings();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("craftmaster/spectateurs.json");
    }

    public static void register() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, ctx, sel) -> fr.lordfinn.craftmaster.command.SpectatorCommands.register(d));
        try {
            if (Files.exists(file())) settings = GSON.fromJson(Files.readString(file()), Settings.class);
            if (settings == null) settings = new Settings();
            if (settings.off == null) settings.off = new LinkedHashSet<>();
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Spectateurs] Réglages illisibles : {}", e.getMessage());
            settings = new Settings();
        }
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 == 0) tick(server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayer p = handler.getPlayer();
            if (Judging.contestant(p) == null && !Permissions.isAdmin(p)) offer(p);
            MasterVoice.playersChanged(server);
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            RIGHTS.remove(handler.getPlayer().getUUID());
            // Held for an intro: back to his place before he is saved
            Back held = INTRO.remove(handler.getPlayer().getUUID());
            if (held != null) {
                ServerPlayer p = handler.getPlayer();
                ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(held.world())));
                p.teleportTo(level == null ? server.overworld() : level, held.x(), held.y(), held.z(), Set.of(), held.yaw(), held.pitch(), true);
                p.setGameMode(GameType.byName(held.gamemode(), GameType.ADVENTURE));
            }
            if (WATCHING.containsKey(handler.getPlayer().getUUID())) stop(handler.getPlayer(), false);
            server.execute(() -> MasterVoice.playersChanged(server));
        });
        // A spectator's chat only reaches those who are not playing (nothing to reveal to a contestant still in it)
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (!WATCHING.containsKey(sender.getUUID())) return true;
            MinecraftServer server = sender.level().getServer();
            Component line = Component.literal("[Spectateur] ").withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.literal(sender.getGameProfile().name() + " : ").withStyle(ChatFormatting.AQUA))
                    .append(message.decoratedContent().copy().withStyle(ChatFormatting.GRAY));
            for (ServerPlayer p : server.getPlayerList().getPlayers()) if (!playing(p)) p.sendSystemMessage(line);
            server.sendSystemMessage(line);
            return false;
        });
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(settings));
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Spectateurs] Réglages non enregistrés : {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------- rules

    /** The trial running (round id), or null: from its launch until « Revenir au plateau ». */
    public static String trial() {
        return TrialRun.trialManche();
    }

    public static boolean enabled() {
        return settings.enabled;
    }

    /** Allowed for this trial (round id). */
    public static boolean allowed(String mancheId) {
        return settings.enabled && (mancheId == null || !settings.off.contains(mancheId));
    }

    /** A contestant still playing (not done) of the trial running. */
    public static boolean playing(ServerPlayer p) {
        String name = Judging.contestant(p);
        return name != null && trial() != null && !Finish.finished(name) && !WATCHING.containsKey(p.getUUID());
    }

    /** May watch now: a trial running, allowed, and he is done or not a contestant. */
    public static boolean eligible(ServerPlayer p) {
        String t = trial();
        if (t == null || !allowed(t)) return false;
        String name = Judging.contestant(p);
        return name == null || Finish.finished(name);
    }

    /** The contestants still playing, online (names as in the cast). */
    public static List<String> playingNames(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (playing(p)) out.add(Judging.contestant(p));
        return out;
    }

    public static boolean watching(ServerPlayer p) {
        return WATCHING.containsKey(p.getUUID());
    }

    /** Players held near the camera during an intro (their place and mode to give back). */
    private static final Map<UUID, Back> INTRO = new LinkedHashMap<>();

    /**
     * An intro starts: its cameras may be in another world (the trial's) than the players (the set, the lobby), and a
     * game only draws its own world. Every player with the mod (not a broadcast game, not already a spectator) is
     * taken under the lens, invisible (spectator mode), for the time of the intro, like the broadcast games; given back
     * by {@link #releaseIntro} (in the black before the round's teleport, or when the intro ends or stops).
     */
    public static void holdForIntro(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (INTRO.containsKey(p.getUUID()) || WATCHING.containsKey(p.getUUID())) continue;
            if (fr.lordfinn.craftmaster.broadcast.Director.broadcasters().contains(p.getUUID())) continue;
            if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowCue.TYPE)) continue;
            INTRO.put(p.getUUID(), new Back(p.level().dimension().identifier().toString(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot(), p.gameMode().getName()));
            if (p.gameMode() != GameType.SPECTATOR) p.setGameMode(GameType.SPECTATOR);
        }
        if (!INTRO.isEmpty()) {
            fr.lordfinn.craftmaster.broadcast.Director.nudgeBroadcasters(server);
            CraftMaster.LOG.info("[Réalisation] {} joueur(s) sous l'objectif pendant l'intro", INTRO.size());
        }
    }

    /** The players held for the intro back to their place and game mode. */
    public static void releaseIntro(MinecraftServer server) {
        if (INTRO.isEmpty()) return;
        for (Map.Entry<UUID, Back> e : List.copyOf(INTRO.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            Back b = e.getValue();
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(b.world())));
            if (level == null) level = server.overworld();
            p.teleportTo(level, b.x(), b.y(), b.z(), Set.of(), b.yaw(), b.pitch(), true);
            p.setGameMode(GameType.byName(b.gamemode(), GameType.ADVENTURE));
        }
        INTRO.clear();
    }

    /** The broadcast's spectators (and the players held during an intro): kept near the shot on air, like the broadcast games. */
    public static Set<UUID> feedWatchers() {
        Set<UUID> out = new LinkedHashSet<>(INTRO.keySet());
        WATCHING.forEach((id, w) -> {
            if (w.target() == null) out.add(id);
        });
        return out;
    }

    public static boolean watchingFeed(ServerPlayer p) {
        Watch w = WATCHING.get(p.getUUID());
        return w != null && w.target() == null;
    }

    /** The contestant {@code p} watches (spectator, or an organiser on his camera), or null. */
    public static String watchedBy(ServerPlayer p) {
        Watch w = WATCHING.get(p.getUUID());
        if (w != null) return w.target();
        if (p.getCamera() instanceof ServerPlayer t && t != p) return t.getGameProfile().name();
        return null;
    }

    // ---------------------------------------------------------------- settings

    /** Allowed or forbidden, for everything ({@code mancheId} null) or one trial; those watching a forbidden trial go back. */
    public static String setAllowed(MinecraftServer server, boolean on, String mancheId) {
        if (mancheId == null) settings.enabled = on;
        else if (on) settings.off.remove(mancheId);
        else settings.off.add(mancheId);
        save();
        int sent = 0;
        if (!allowed(trial())) {
            for (UUID id : List.copyOf(WATCHING.keySet())) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) {
                    stop(p, true);
                    p.sendSystemMessage(Component.literal("Les spectateurs ne sont plus autorisés : retour à ta place.").withStyle(ChatFormatting.YELLOW));
                    sent++;
                }
            }
        } else if (on) offerAll(server);
        return "Spectateurs " + (on ? "autorisés" : "interdits") + (mancheId == null ? " (partout)" : " pour « " + mancheId + " »")
                + (sent > 0 ? " ; " + sent + " spectateur(s) renvoyé(s) à leur place" : "") + ".";
    }

    // ---------------------------------------------------------------- watching

    /** {@code target} a contestant still playing, or null = the broadcast. Returns the line for him, or throws. */
    public static String start(ServerPlayer p, String target) {
        MinecraftServer server = p.level().getServer();
        if (!eligible(p)) {
            String t = trial();
            throw new IllegalArgumentException(t == null ? "aucune épreuve en cours." : !allowed(t) ? "les spectateurs ne sont pas autorisés pour cette épreuve."
                    : "tu joues encore : tu pourras regarder quand tu auras fini.");
        }
        ServerPlayer t = null;
        if (target != null) {
            for (ServerPlayer o : server.getPlayerList().getPlayers())
                if (playing(o) && (o.getGameProfile().name().equalsIgnoreCase(target) || target.equalsIgnoreCase(Judging.contestant(o)))) t = o;
            if (t == null) throw new IllegalArgumentException("« " + target + " » n'est pas un candidat en train de jouer.");
            if (t == p) throw new IllegalArgumentException("c'est toi.");
        }
        Watch before = WATCHING.get(p.getUUID());
        Back back = before != null ? before.back() : new Back(p.level().dimension().identifier().toString(), p.getX(), p.getY(), p.getZ(),
                p.getYRot(), p.getXRot(), p.gameMode().getName());
        WATCHING.put(p.getUUID(), new Watch(t == null ? null : t.getGameProfile().name(), back));
        if (p.gameMode() != GameType.SPECTATOR) p.setGameMode(GameType.SPECTATOR);
        if (t != null) {
            p.setCamera(p);
            p.teleportTo((ServerLevel) t.level(), t.getX(), t.getY(), t.getZ(), Set.of(), t.getYRot(), t.getXRot(), false);
            p.setCamera(t);
        } else {
            p.setCamera(p);
            fr.lordfinn.craftmaster.broadcast.Director.nudgeBroadcasters(server);
        }
        MasterVoice.refresh(server);
        menu(p);
        CraftMaster.LOG.info("[Spectateurs] {} regarde {}", p.getGameProfile().name(), t == null ? "l'émission" : t.getGameProfile().name());
        return t == null ? "Tu regardes l'émission." : "Tu regardes " + t.getGameProfile().name() + ".";
    }

    /** Back to his place and game mode. {@code tell}: the menu of choices again if he still may watch. */
    public static boolean stop(ServerPlayer p, boolean tell) {
        Watch w = WATCHING.remove(p.getUUID());
        if (w == null) return false;
        MinecraftServer server = p.level().getServer();
        p.setCamera(p);
        Back b = w.back();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(b.world())));
        if (level == null) level = server.overworld();
        p.teleportTo(level, b.x(), b.y(), b.z(), Set.of(), b.yaw(), b.pitch(), true);
        p.setGameMode(GameType.byName(b.gamemode(), GameType.ADVENTURE));
        MasterVoice.refresh(server);
        if (tell) {
            p.sendSystemMessage(Component.literal("De retour à ta place.").withStyle(ChatFormatting.GRAY));
            if (eligible(p)) menu(p);
        }
        return true;
    }

    /** Everyone back (the trial is over). */
    public static int stopAll(MinecraftServer server) {
        int n = 0;
        for (UUID id : List.copyOf(WATCHING.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && stop(p, false)) n++;
            else WATCHING.remove(id);
        }
        OFFERED.clear();
        return n;
    }

    /** What each player was told of his rights (F8 his own, F7 allowed). */
    private static final Map<UUID, String> RIGHTS = new java.util.HashMap<>();

    /**
     * May turn the show's feed on himself (F7): the organisers always; out of a trial (the set, the studio) everyone,
     * unless the master turned the spectators off; during a trial those who may watch (done or not a contestant,
     * the trial allowing it), not the contestants still playing.
     */
    public static boolean feedAllowed(ServerPlayer p) {
        if (Permissions.isAdmin(p)) return true;
        if (trial() == null) return settings.enabled;
        return eligible(p);
    }

    private static void rights(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            // « admin » here: may switch his own broadcast mode (organisers, and the camera accounts like the OBS game)
            boolean own = Permissions.isAdmin(p) || Permissions.canDriveCameras(p);
            String r = (own ? "admin" : "") + "|" + (feedAllowed(p) ? "feed" : "");
            if (r.equals(RIGHTS.get(p.getUUID()))) continue;
            RIGHTS.put(p.getUUID(), r);
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowCue.TYPE))
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowCue(
                        fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Cues.RIGHTS, own ? "admin" : "", feedAllowed(p) ? "feed" : "", "", 0));
        }
    }

    /**
     * Broadcast mode (F8) switched by the master for {@code who}: "tous", "candidats", "spectateurs" (those not playing,
     * not organisers) or a player's name. Returns the line, or throws.
     */
    public static String broadcastFor(MinecraftServer server, String who, boolean on) {
        String k = who == null ? "tous" : who.trim().toLowerCase(Locale.ROOT);
        List<String> done = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean pick = switch (k) {
                case "tous", "tout", "all", "everyone" -> true;
                case "candidats", "contestants" -> Judging.contestant(p) != null;
                case "spectateurs", "spectators" -> !playing(p) && !Permissions.isAdmin(p);
                default -> p.getGameProfile().name().equalsIgnoreCase(who.trim());
            };
            if (!pick || !net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowCue.TYPE)) continue;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowCue(
                    fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Cues.BROADCAST, on ? "on" : "off", "", "", 0));
            done.add(p.getGameProfile().name());
        }
        if (done.isEmpty()) throw new IllegalArgumentException("personne avec le mod pour « " + who + " » (tous, candidats, spectateurs ou un pseudo).");
        return "Mode diffusion " + (on ? "lancé" : "coupé") + " pour " + done.size() + " joueur(s) : " + String.join(", ", done) + ".";
    }

    private static void tick(MinecraftServer server) {
        rights(server);
        // Held under the lens for an intro: spectators all along (a per-world game mode may change it on arrival)
        for (UUID id : INTRO.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && p.gameMode() != GameType.SPECTATOR) p.setGameMode(GameType.SPECTATOR);
        }
        String t = trial();
        if (t == null || !allowed(t)) {
            if (!WATCHING.isEmpty()) stopAll(server);
            if (t == null) OFFERED.clear();
            return;
        }
        for (Map.Entry<UUID, Watch> e : List.copyOf(WATCHING.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            if (p.gameMode() != GameType.SPECTATOR) p.setGameMode(GameType.SPECTATOR);
            String target = e.getValue().target();
            if (target == null) {
                if (p.getCamera() != p) p.setCamera(p);
                continue;
            }
            ServerPlayer tp = server.getPlayerList().getPlayerByName(target);
            if (tp == null) {
                // Gone: the broadcast instead
                WATCHING.put(e.getKey(), new Watch(null, e.getValue().back()));
                p.setCamera(p);
                p.sendSystemMessage(Component.literal(target + " n'est plus là : tu regardes l'émission.").withStyle(ChatFormatting.GRAY));
                menu(p);
                continue;
            }
            // Held on his camera (sneaking would let go of it)
            if (p.getCamera() != tp) {
                if (p.level() != tp.level() || p.distanceTo(tp) > 8)
                    p.teleportTo((ServerLevel) tp.level(), tp.getX(), tp.getY(), tp.getZ(), Set.of(), tp.getYRot(), tp.getXRot(), false);
                p.setCamera(tp);
            }
        }
    }

    // ---------------------------------------------------------------- offers

    /** A contestant just finished: he may watch the others. */
    public static void onFinished(ServerPlayer p) {
        offer(p);
    }

    /** The trial starts: the players who are not contestants are offered to watch. */
    public static void onTrialStarted(MinecraftServer server) {
        OFFERED.clear();
        offerAll(server);
    }

    private static void offerAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (!Permissions.isAdmin(p)) offer(p);
    }

    private static void offer(ServerPlayer p) {
        if (!eligible(p) || WATCHING.containsKey(p.getUUID()) || !OFFERED.add(p.getUUID())) return;
        menu(p);
    }

    /** The choices in the chat: the broadcast, each contestant still playing, leave. */
    public static void menu(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        MutableComponent line = Component.literal("👁 Regarder : ").withStyle(ChatFormatting.GOLD)
                .append(button("l'émission", "/cm spectateurs regarder emission", "La retransmission, comme elle passe à l'antenne, commentée en vocal avec l'animateur", ChatFormatting.AQUA));
        for (String n : playingNames(server))
            line.append(Component.literal(" ")).append(button(n, "/cm spectateurs regarder " + n, "Par les yeux de " + n, ChatFormatting.GREEN));
        if (WATCHING.containsKey(p.getUUID()))
            line.append(Component.literal(" ")).append(button("✖ Quitter", "/cm spectateurs quitter", "Revenir à ta place", ChatFormatting.RED));
        p.sendSystemMessage(line);
    }

    private static MutableComponent button(String label, String command, String hover, ChatFormatting colour) {
        return Component.literal("[" + label + "]").withStyle(st -> st.withColor(colour)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    /** "2 spectateurs : Ana (émission), Bob (→ Ensuri)" */
    public static String describe(MinecraftServer server) {
        List<String> parts = new ArrayList<>();
        WATCHING.forEach((id, w) -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            parts.add((p == null ? id.toString() : p.getGameProfile().name()) + (w.target() == null ? " (émission)" : " (→ " + w.target() + ")"));
        });
        String t = trial();
        return "Spectateurs " + (settings.enabled ? "autorisés" : "interdits partout")
                + (settings.off.isEmpty() ? "" : ", interdits pour : " + String.join(", ", settings.off))
                + (t == null ? " · aucune épreuve en cours" : " · épreuve « " + t + " » : " + (allowed(t) ? "autorisés" : "interdits"))
                + " · " + (parts.isEmpty() ? "personne ne regarde" : parts.size() + " : " + String.join(", ", parts));
    }

    /** For the N menu: may this player watch, and what. */
    public static com.google.gson.JsonObject state(ServerPlayer p) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("enabled", settings.enabled);
        String t = trial();
        o.addProperty("trial", t == null ? "" : t);
        o.addProperty("trialAllowed", t != null && allowed(t));
        o.addProperty("eligible", eligible(p));
        Watch w = WATCHING.get(p.getUUID());
        o.addProperty("watching", w == null ? "" : w.target() == null ? "emission" : w.target());
        com.google.gson.JsonArray names = new com.google.gson.JsonArray();
        for (String n : playingNames(p.level().getServer())) names.add(n);
        o.add("players", names);
        o.addProperty("voice", MasterVoice.label(MasterVoice.mode()));
        o.addProperty("commentOpen", MasterVoice.commentOpen());
        o.addProperty("voiceStatus", MasterVoice.status(p.level().getServer()));
        return o;
    }

    public static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
