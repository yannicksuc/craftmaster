package fr.lordfinn.craftmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork;
import fr.lordfinn.craftmaster.broadcast.Credits;
import fr.lordfinn.craftmaster.broadcast.Director;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.camera.Cameras;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static fr.lordfinn.craftmaster.command.CraftMasterCommands.action;
import static fr.lordfinn.craftmaster.command.CraftMasterCommands.fail;
import static fr.lordfinn.craftmaster.command.CraftMasterCommands.list;
import static fr.lordfinn.craftmaster.command.CraftMasterCommands.ok;
import static fr.lordfinn.craftmaster.command.WordArgument.word;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * TV direction commands (chantier E): {@code /craftMasterCamera} (alias {@code /cam}) places and drives the studio
 * cameras, {@code /craftMasterTv} (alias {@code /tv}) runs the overlay and the credits. They need
 * {@link Permissions#CAMERA} (organisers, or the craftmaster:camera permission). The on-air actions go through
 * {@link ActionExecutor}, like the voice control. Reference: docs/wiki/Realisation.md.
 */
public final class CameraCommands {

    private CameraCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        CraftMasterCommands.add(d, CameraCommands::camera, "craftMasterCamera", "cam");
        CraftMasterCommands.add(d, CameraCommands::tv, "craftMasterTv", "tv");
    }

    private static final SuggestionProvider<CommandSourceStack> NUMBERS = list(c ->
            Cameras.list().all().stream().map(s -> String.valueOf(s.number())).toList());
    /** Numbers, names and roles: what camera.cut understands. */
    private static final SuggestionProvider<CommandSourceStack> CAMERAS = list(c -> {
        Set<String> out = new LinkedHashSet<>();
        for (CameraSpot s : Cameras.list().all()) out.add(String.valueOf(s.number()));
        for (CameraSpot s : Cameras.list().all()) out.add(s.name());
        out.addAll(CameraSpot.ROLES);
        return out;
    });
    private static final SuggestionProvider<CommandSourceStack> ROLES = list(c -> {
        List<String> r = new ArrayList<>(CameraSpot.ROLES);
        r.add("aucun");
        return r;
    });
    private static final SuggestionProvider<CommandSourceStack> MOTIONS = list(c -> CameraSpot.MOTIONS);
    private static final SuggestionProvider<CommandSourceStack> TRANSITIONS = list(c -> List.of("coupe", "fondu", "enchaine"));
    private static final SuggestionProvider<CommandSourceStack> ON_OFF = list(c -> List.of("on", "off"));
    private static final SuggestionProvider<CommandSourceStack> SCORE_MODES = list(c -> List.of("show", "auto", "on", "off"));
    private static final SuggestionProvider<CommandSourceStack> ONLINE = list(c -> c.getSource().getOnlinePlayerNames());

    @SafeVarargs
    private static int run(CommandContext<CommandSourceStack> c, Map<String, Object>... actions) {
        return ActionExecutor.execute(c.getSource(), List.of(actions));
    }

    private static String str(CommandContext<CommandSourceStack> c, String name) {
        return c.getArgument(name, String.class);
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) fail(c, "Cette commande est réservée aux joueurs (elle utilise ta position).");
        return p;
    }

    private static boolean onOff(CommandContext<CommandSourceStack> c, String arg) {
        String v = str(c, arg).toLowerCase(java.util.Locale.ROOT);
        return v.equals("on") || v.equals("oui") || v.equals("true");
    }

    // ---------------------------------------------------------------- /craftMasterCamera

    private static LiteralArgumentBuilder<CommandSourceStack> camera(String name) {
        return literal(name).requires(Permissions.CAMERA)
                .executes(CameraCommands::listCameras)
                .then(literal("list").executes(CameraCommands::listCameras))
                .then(literal("here")
                        .then(argument("number", integer(1, 99)).suggests(NUMBERS)
                                .executes(c -> here(c, getInteger(c, "number"), null))
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(c -> here(c, getInteger(c, "number"), str(c, "name"))))))
                .then(literal("add")
                        .executes(c -> here(c, Cameras.list().nextFree(), null))
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(c -> here(c, Cameras.list().nextFree(), str(c, "name")))))
                .then(literal("aim").then(argument("number", integer(1, 99)).suggests(NUMBERS).executes(CameraCommands::aim)))
                .then(literal("goto").then(argument("number", integer(1, 99)).suggests(NUMBERS).executes(CameraCommands::gotoCamera)))
                .then(literal("name").then(argument("number", integer(1, 99)).suggests(NUMBERS)
                        .then(argument("name", StringArgumentType.greedyString()).executes(c -> {
                            int n = getInteger(c, "number");
                            String newName = str(c, "name");
                            return set(c, n, s -> s.withName(newName), "Caméra " + n + " renommée « " + newName + " ».");
                        }))))
                .then(literal("role").then(argument("number", integer(1, 99)).suggests(NUMBERS)
                        .then(argument("role", word()).suggests(ROLES).executes(c -> {
                            int n = getInteger(c, "number");
                            String role = CameraSpot.normalizeRole(str(c, "role"));
                            if (!role.isEmpty()) {
                                // One camera per role: the previous holder loses it
                                CameraSpot holder = Cameras.list().withRole(role);
                                if (holder != null && holder.number() != n) Cameras.update(c.getSource().getServer(), holder.number(), s -> s.withRole(""));
                            }
                            return set(c, n, s -> s.withRole(role), "Caméra " + n + " : " + (role.isEmpty() ? "aucun rôle." : "rôle « " + role + " »."));
                        }))))
                .then(literal("zoom").then(argument("number", integer(1, 99)).suggests(NUMBERS)
                        .then(literal("reset").executes(c -> set(c, getInteger(c, "number"), s -> s.withFov(0),
                                "Caméra " + getInteger(c, "number") + " : champ de vision du joueur.")))
                        .then(argument("fov", FloatArgumentType.floatArg(CameraSpot.MIN_FOV, CameraSpot.MAX_FOV)).executes(c -> {
                            float fov = FloatArgumentType.getFloat(c, "fov");
                            return set(c, getInteger(c, "number"), s -> s.withFov(fov), "Caméra " + getInteger(c, "number") + " : champ de " + Math.round(fov) + "°.");
                        }))))
                .then(literal("motion").then(argument("number", integer(1, 99)).suggests(NUMBERS)
                        .then(argument("motion", word()).suggests(MOTIONS).executes(c -> {
                            String m = CameraSpot.normalizeMotion(str(c, "motion"));
                            return set(c, getInteger(c, "number"), s -> s.withMotion(m), "Caméra " + getInteger(c, "number") + " : mouvement « " + m + " ».");
                        }))))
                .then(literal("remove").then(argument("number", integer(1, 99)).suggests(NUMBERS).executes(c -> {
                    int n = getInteger(c, "number");
                    if (!Cameras.remove(c.getSource().getServer(), n)) {
                        fail(c, "Aucune caméra " + n + ".");
                        return 0;
                    }
                    ok(c, "Caméra " + n + " retirée.");
                    return 1;
                })))
                .then(literal("cut").then(argument("camera", word()).suggests(CAMERAS)
                        .executes(c -> run(c, action("camera.cut", "camera", str(c, "camera"))))
                        .then(argument("transition", word()).suggests(TRANSITIONS)
                                .executes(c -> run(c, action("camera.cut", "camera", str(c, "camera"), "transition", str(c, "transition")))))))
                .then(literal("wide").executes(c -> run(c, action("camera.cut", "camera", CameraSpot.WIDE))))
                .then(literal("next").executes(c -> step(c, 1)))
                .then(literal("prev").executes(c -> step(c, -1)))
                .then(literal("follow").then(argument("player", word()).suggests(ONLINE)
                        .executes(c -> run(c, action("camera.follow", "player", str(c, "player"))))
                        .then(argument("transition", word()).suggests(TRANSITIONS)
                                .executes(c -> run(c, action("camera.follow", "player", str(c, "player"), "transition", str(c, "transition")))))))
                .then(literal("auto").then(argument("enabled", word()).suggests(ON_OFF)
                        .executes(c -> run(c, action("camera.auto", "enabled", onOff(c, "enabled"))))))
                .then(literal("transition").then(argument("transition", word()).suggests(TRANSITIONS)
                        .executes(c -> run(c, action("camera.transition", "transition", str(c, "transition"))))));
    }

    private static int listCameras(CommandContext<CommandSourceStack> c) {
        List<CameraSpot> all = Cameras.list().all();
        c.getSource().sendSystemMessage(Component.literal("— Caméras du studio —").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (all.isEmpty()) c.getSource().sendSystemMessage(Component.literal(
                "Aucune caméra. Place-toi et regarde le plan voulu, puis /cam here 1 Plan large (ou pose l'objet Caméra de studio).")
                .withStyle(ChatFormatting.GRAY));
        BroadcastNetwork.Shot shot = Director.shot();
        for (CameraSpot s : all) {
            boolean onAir = shot.kind() == BroadcastNetwork.SHOT_CAMERA && shot.camera() == s.number();
            c.getSource().sendSystemMessage(Component.literal((onAir ? "● " : "  ") + s.label()).withStyle(onAir ? ChatFormatting.RED : ChatFormatting.WHITE)
                    .append(Component.literal("  " + s.describe()).withStyle(ChatFormatting.GRAY)));
        }
        String onAirText = switch (shot.kind()) {
            case BroadcastNetwork.SHOT_CAMERA -> "caméra " + shot.camera();
            case BroadcastNetwork.SHOT_FOLLOW -> "on suit " + shot.player();
            default -> "rien (vue du joueur)";
        };
        c.getSource().sendSystemMessage(Component.literal("À l'antenne : " + onAirText + " · réalisation auto "
                + (Director.autoDirection() ? "on" : "off") + " · " + Director.broadcasters().size() + " jeu(x) en mode diffusion"
                + (Credits.running().isEmpty() ? "" : " · générique en cours")).withStyle(ChatFormatting.GRAY));
        return all.size();
    }

    /** Camera {@code n} put where the player's eyes are, looking where they look. */
    private static int here(CommandContext<CommandSourceStack> c, int n, String name) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        CameraSpot old = Cameras.get(n);
        String newName = name != null ? name : old != null ? old.name() : null;
        CameraSpot spot = old != null
                ? old.withPlace(Busts.worldId(p.level()), p.getX(), p.getEyeY(), p.getZ(), p.getYRot(), p.getXRot())
                : new CameraSpot(n, null, "", Busts.worldId(p.level()), p.getX(), p.getEyeY(), p.getZ(), p.getYRot(), p.getXRot(), 0, CameraSpot.BREATHING);
        if (newName != null) spot = spot.withName(newName);
        Cameras.place(c.getSource().getServer(), spot);
        ok(c, "Caméra " + spot.label() + (old == null ? " posée" : " déplacée") + " à ta place, elle regarde là où tu regardes.");
        return 1;
    }

    private static int aim(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        int n = getInteger(c, "number");
        return set(c, n, s -> s.withRotation(p.getYRot(), p.getXRot()), "Caméra " + n + " : elle regarde maintenant là où tu regardes.");
    }

    private static int gotoCamera(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        int n = getInteger(c, "number");
        CameraSpot s = Cameras.get(n);
        ServerLevel level = s == null ? null : Busts.level(c.getSource().getServer(), s.world());
        if (level == null) {
            fail(c, "Aucune caméra " + n + ".");
            return 0;
        }
        // Eyes at the lens: what the player sees is the shot (to check or adjust it)
        p.teleportTo(level, s.x(), s.y() - p.getEyeHeight(), s.z(), Set.of(), s.yaw(), s.pitch(), false);
        ok(c, "Tu vois ce que voit la caméra " + s.label() + ". /cam here " + n + " pour la recadrer d'ici.");
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> c, int n, java.util.function.UnaryOperator<CameraSpot> change, String message) {
        if (!Cameras.update(c.getSource().getServer(), n, change)) {
            fail(c, "Aucune caméra " + n + ".");
            return 0;
        }
        ok(c, message);
        return 1;
    }

    private static int step(CommandContext<CommandSourceStack> c, int delta) {
        BroadcastNetwork.Shot shot = Director.shot();
        CameraSpot s = Cameras.list().step(shot.kind() == BroadcastNetwork.SHOT_CAMERA ? shot.camera() : -1, delta);
        if (s == null) {
            fail(c, "Aucune caméra posée.");
            return 0;
        }
        return run(c, action("camera.cut", "camera", String.valueOf(s.number())));
    }

    /** All eyes on a player: what chantier D's envelopes fire ({@code ShowEvents.spotlight}), by hand. */
    private static int spotlight(CommandContext<CommandSourceStack> c, String reason) {
        String player = str(c, "player");
        fr.lordfinn.craftmaster.show.ShowEvents.spotlight(c.getSource().getServer(), player, reason == null ? "" : reason);
        ok(c, "Projecteur sur " + player + (Director.autoDirection() ? "." : " (la réalisation automatique est désactivée : /cam auto on)."));
        return 1;
    }

    // ---------------------------------------------------------------- /craftMasterTv

    private static LiteralArgumentBuilder<CommandSourceStack> tv(String name) {
        return literal(name).requires(Permissions.CAMERA)
                .then(literal("lowerthird")
                        .then(literal("player").then(argument("player", word()).suggests(ONLINE)
                                .executes(c -> run(c, action("broadcast.lowerthird", "player", str(c, "player"))))))
                        .then(literal("text").then(argument("text", StringArgumentType.greedyString())
                                .executes(c -> {
                                    // "Title | subtitle"
                                    String[] parts = str(c, "text").split("\\|", 2);
                                    return run(c, parts.length > 1
                                            ? action("broadcast.lowerthird", "text", parts[0].trim(), "subtitle", parts[1].trim())
                                            : action("broadcast.lowerthird", "text", parts[0].trim()));
                                }))))
                .then(literal("task").executes(c -> run(c, action("broadcast.task")))
                        .then(argument("seconds", integer(1, 600)).executes(c -> run(c, action("broadcast.task", "seconds", getInteger(c, "seconds"))))))
                .then(literal("scores").executes(c -> run(c, action("broadcast.scores", "mode", "show")))
                        .then(argument("mode", word()).suggests(SCORE_MODES).executes(c -> run(c, action("broadcast.scores", "mode", str(c, "mode"))))))
                .then(literal("letterbox").then(argument("enabled", word()).suggests(ON_OFF)
                        .executes(c -> run(c, action("broadcast.letterbox", "enabled", onOff(c, "enabled"))))))
                .then(literal("broadcast").then(argument("player", word()).suggests(ONLINE)
                        .then(argument("enabled", word()).suggests(ON_OFF).executes(c -> {
                            // Switches a game's broadcast mode from here (the camera account's PC may be out of reach)
                            ServerPlayer target = c.getSource().getServer().getPlayerList().getPlayerByName(str(c, "player"));
                            if (target == null || !net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(target, BroadcastNetwork.ShowCue.TYPE)) {
                                fail(c, str(c, "player") + " n'est pas connecté avec le mod.");
                                return 0;
                            }
                            boolean on = onOff(c, "enabled");
                            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(target,
                                    new BroadcastNetwork.ShowCue(BroadcastNetwork.Cues.BROADCAST, on ? "on" : "off", "", "", 0));
                            ok(c, "Mode diffusion " + (on ? "activé" : "désactivé") + " pour " + target.getGameProfile().name() + ".");
                            return 1;
                        }))))
                .then(literal("spotlight").then(argument("player", word()).suggests(ONLINE)
                        .executes(c -> spotlight(c, null))
                        .then(argument("reason", StringArgumentType.greedyString()).executes(c -> spotlight(c, str(c, "reason"))))))
                .then(literal("intro").executes(c -> run(c, action("show.intro"))))
                .then(literal("outro").executes(c -> run(c, action("show.outro"))))
                .then(literal("stop").executes(c -> run(c, action("show.stop"))));
    }
}
