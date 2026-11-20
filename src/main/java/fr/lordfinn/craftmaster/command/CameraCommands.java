package fr.lordfinn.craftmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork;
import fr.lordfinn.craftmaster.broadcast.Credits;
import fr.lordfinn.craftmaster.broadcast.Director;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.camera.CameraList;
import fr.lordfinn.craftmaster.camera.CameraPath;
import fr.lordfinn.craftmaster.camera.CameraPaths;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.camera.Cameras;
import fr.lordfinn.craftmaster.camera.Lieux;
import fr.lordfinn.craftmaster.camera.PathMarkerItem;
import fr.lordfinn.craftmaster.camera.PathRecording;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.MancheIntro;
import fr.lordfinn.craftmaster.data.Partie;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.registry.ModContent;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.trial.TrialZones;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import java.util.function.UnaryOperator;

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
 * TV direction commands (chantier E): {@code /cm cam} (alias {@code /cm cam}) places and drives the cameras
 * (of the studio and of the rounds, see {@link CameraSpot}) and records the travellings, {@code /cm tv}
 * (alias {@code /cm tv}) runs the overlay, the credits and the rounds' intros. They need {@link Permissions#CAMERA}
 * (organisers, or the craftmaster:camera permission). The on-air actions and the intros go through
 * {@link ActionExecutor}, like the voice control. Reference: docs/wiki/Realisation.md.
 *
 * <p>A camera argument is a reference: "2" (camera 2 where the keys point, {@link Director#focus()}), "studio:2",
 * "pont:2", "pont:ensuri:1", "ensuri:1", a name or a role.
 */
public final class CameraCommands {

    private CameraCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        CraftMasterCommands.add(d, CameraCommands::camera, "craftMasterCamera", "cam");
        CraftMasterCommands.add(d, CameraCommands::tv, "craftMasterTv", "tv");
    }

    /** Effects of the screen effects system (fx.ScreenFx): "aucun", every id and the ready-made combinations. */
    public static final List<String> EFFECTS = fr.lordfinn.craftmaster.fx.ScreenFx.suggestions();

    /** Numbers of the focus, keys of every camera, names and roles: what a camera reference understands. */
    /**
     * The cameras a bare number means here (the round of the arena the player stands in, else the focus): their
     * numbers with their names in the tooltip, and in an arena the next free number (« nouvelle »); then the other
     * lieux' keys and the roles.
     */
    private static final SuggestionProvider<CommandSourceStack> CAMERAS = (c, b) -> {
        String arena = arenaGroup(c);
        String group = arena != null ? arena : Director.focus();
        String where = whereName(group);
        CameraList here = Cameras.list().group(group);
        for (CameraSpot s : here.all())
            b.suggest(String.valueOf(s.number()), Component.literal(s.number() + " · " + s.name() + " — " + where));
        // In an arena: its round's cameras only, and the number a new one gets
        if (arena != null) b.suggest(String.valueOf(Cameras.list().nextFree(group)), Component.literal("nouvelle — " + where));
        Set<String> more = new LinkedHashSet<>();
        for (CameraSpot s : here.all()) more.add(s.name());
        if (arena == null) for (CameraSpot s : Cameras.list().all()) more.add(s.key());
        more.addAll(CameraSpot.ROLES);
        for (String m : more) if (m != null && !m.isBlank() && m.toLowerCase(java.util.Locale.ROOT).startsWith(b.getRemainingLowerCase())) b.suggest(m.contains(" ") ? "\"" + m + "\"" : m);
        return b.buildFuture();
    };

    /** « caméras de la manche La pomme » / « caméras du studio ». */
    private static String whereName(String group) {
        String lieu = CameraSpot.lieuOf(group);
        return CameraSpot.STUDIO.equals(lieu) ? "caméras du studio" : "caméras de la manche " + Lieux.lieuName(lieu);
    }
    private static final SuggestionProvider<CommandSourceStack> ROLES = list(c -> {
        List<String> r = new ArrayList<>(CameraSpot.ROLES);
        r.add("aucun");
        return r;
    });
    /** "studio" and the rounds of the active season (ids). */
    private static final SuggestionProvider<CommandSourceStack> LIEUX = list(c -> {
        List<String> out = new ArrayList<>(List.of(CameraSpot.STUDIO));
        out.addAll(mancheIds());
        return out;
    });
    private static final SuggestionProvider<CommandSourceStack> FOCUS_LIEUX = list(c -> {
        List<String> out = new ArrayList<>(List.of("auto", CameraSpot.STUDIO));
        out.addAll(mancheIds());
        return out;
    });
    /** Trial zones, the contestants, and the zones that already have cameras. */
    private static final SuggestionProvider<CommandSourceStack> ZONES = list(c -> {
        Set<String> out = new LinkedHashSet<>(TrialZones.zones().keySet());
        for (CameraSpot s : Cameras.list().all()) if (!s.zone().isEmpty()) out.add(s.zone());
        out.addAll(fr.lordfinn.craftmaster.trial.Judging.contestants());
        return out;
    });
    private static final SuggestionProvider<CommandSourceStack> ZONE_CHOICES = list(c -> {
        Set<String> out = new LinkedHashSet<>(List.of("suivante", "precedente", "tout"));
        out.addAll(Cameras.list().zones(Director.focusLieu()));
        out.remove("");
        out.addAll(fr.lordfinn.craftmaster.trial.Judging.contestants());
        return out;
    });
    private static final SuggestionProvider<CommandSourceStack> PATHS = list(c -> CameraPaths.byName().keySet());
    private static final SuggestionProvider<CommandSourceStack> MANCHES = list(c -> mancheIds());
    private static final SuggestionProvider<CommandSourceStack> MOTIONS = list(c -> CameraSpot.MOTIONS);
    private static final SuggestionProvider<CommandSourceStack> CURVES = list(c -> CameraPath.CURVES);
    private static final SuggestionProvider<CommandSourceStack> TRANSITIONS = list(c -> fr.lordfinn.craftmaster.broadcast.BroadcastActions.TRANSITION_IDS);
    private static final SuggestionProvider<CommandSourceStack> EFFECT_IDS = list(c -> EFFECTS);
    private static final SuggestionProvider<CommandSourceStack> CARDS = list(c -> List.of("titre", "synthe", "aucune"));
    private static final SuggestionProvider<CommandSourceStack> MUSIC = list(c -> List.of("intro", "theme", "aucune", "minecraft:music_disc.otherside"));
    private static final SuggestionProvider<CommandSourceStack> ON_OFF = list(c -> List.of("on", "off"));
    private static final SuggestionProvider<CommandSourceStack> SCORE_MODES = list(c -> List.of("show", "auto", "on", "off"));
    private static final SuggestionProvider<CommandSourceStack> ONLINE = list(c -> c.getSource().getOnlinePlayerNames());

    private static List<String> mancheIds() {
        List<String> out = new ArrayList<>();
        Season s = CraftMaster.data() == null ? null : CraftMaster.data().activeSeason();
        if (s != null) for (Partie p : s.parties.values()) for (Manche m : p.manches) if (!m.archived) out.add(m.id);
        return out;
    }

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

    private static RequiredArgumentBuilder<CommandSourceStack, String> cam() {
        return argument("camera", word()).suggests(CAMERAS);
    }

    /** The camera of the "camera" argument, or null (said so). */
    private static CameraSpot spot(CommandContext<CommandSourceStack> c) {
        String ref = str(c, "camera");
        // In an arena, a bare number is a camera of its round
        String group = groupHere(c);
        CameraSpot s = Lieux.resolve(c.getSource().getServer(), ref, group);
        if (s == null) fail(c, "Aucune caméra « " + ref + " » dans les " + whereName(group) + " (/cm cam liste tout pour toutes les voir).");
        return s;
    }

    // ---------------------------------------------------------------- /cm cam

    private static LiteralArgumentBuilder<CommandSourceStack> camera(String name) {
        return literal(name).requires(Permissions.CAMERA)
                .executes(c -> listCameras(c, false))
                .then(literal("list").executes(c -> listCameras(c, false))
                        .then(literal("tout").executes(c -> listCameras(c, true)))
                        .then(literal("all").executes(c -> listCameras(c, true))))
                .then(literal("here")
                        .then(cam().executes(c -> here(c, null))
                                .then(argument("name", StringArgumentType.greedyString()).executes(c -> here(c, str(c, "name"))))))
                .then(literal("add")
                        .executes(c -> add(c, null))
                        .then(argument("name", StringArgumentType.greedyString()).executes(c -> add(c, str(c, "name")))))
                .then(literal("aim").then(cam().executes(CameraCommands::aim)))
                // Without moving: a camera at coordinates, aimed at a point (console, the régie agent)
                .then(literal("placer").then(cam().then(argument("pos", net.minecraft.commands.arguments.coordinates.Vec3Argument.vec3(false))
                        .then(literal("vers").then(argument("target", net.minecraft.commands.arguments.coordinates.Vec3Argument.vec3(false))
                                .executes(CameraCommands::placeAt))))))
                .then(literal("goto").then(cam().executes(CameraCommands::gotoCamera)))
                .then(literal("name").then(cam().then(argument("name", StringArgumentType.greedyString()).executes(c -> {
                    String newName = str(c, "name");
                    return set(c, s -> s.withName(newName), "renommée « " + newName + " »");
                }))))
                .then(literal("role").then(cam().then(argument("role", word()).suggests(ROLES).executes(c -> {
                    CameraSpot target = spot(c);
                    if (target == null) return 0;
                    String role = CameraSpot.normalizeRole(str(c, "role"));
                    if (!role.isEmpty()) {
                        // One camera per role in each group: the previous holder loses it
                        CameraSpot holder = Cameras.list().group(target.group()).withRole(role);
                        if (holder != null && !holder.key().equals(target.key()))
                            Cameras.update(c.getSource().getServer(), holder.key(), s -> s.withRole(""));
                    }
                    return set(c, s -> s.withRole(role), role.isEmpty() ? "aucun rôle" : "rôle « " + role + " »");
                }))))
                .then(literal("zoom").then(cam()
                        .then(literal("reset").executes(c -> set(c, s -> s.withFov(0), "champ de vision du joueur")))
                        .then(argument("fov", FloatArgumentType.floatArg(CameraSpot.MIN_FOV, CameraSpot.MAX_FOV)).executes(c -> {
                            float fov = FloatArgumentType.getFloat(c, "fov");
                            return set(c, s -> s.withFov(fov), "champ de " + Math.round(fov) + "°");
                        }))))
                .then(literal("motion").then(cam().then(argument("motion", word()).suggests(MOTIONS).executes(c -> {
                    String m = CameraSpot.normalizeMotion(str(c, "motion"));
                    return set(c, s -> s.withMotion(m), "mouvement « " + m + " »");
                }))))
                .then(literal("remove").then(cam().executes(c -> {
                    CameraSpot s = spot(c);
                    if (s == null) return 0;
                    Cameras.remove(c.getSource().getServer(), s.key());
                    ok(c, "Caméra " + s.label() + " retirée.");
                    return 1;
                })))
                // Lieux: which lieu a camera belongs to, which lieu / zone the keys address
                .then(literal("lieu").then(cam().then(argument("lieu", word()).suggests(LIEUX)
                        .executes(c -> run(c, action("camera.lieu", "camera", str(c, "camera"), "lieu", str(c, "lieu"))))
                        .then(argument("zone", word()).suggests(ZONES)
                                .executes(c -> run(c, action("camera.lieu", "camera", str(c, "camera"), "lieu", str(c, "lieu"), "zone", str(c, "zone"))))))))
                .then(literal("focus").executes(CameraCommands::showFocus)
                        .then(argument("lieu", word()).suggests(FOCUS_LIEUX)
                                .executes(c -> run(c, action("camera.focus", "lieu", str(c, "lieu"))))
                                .then(argument("zone", word()).suggests(ZONES)
                                        .executes(c -> run(c, action("camera.focus", "lieu", str(c, "lieu"), "zone", str(c, "zone")))))))
                .then(literal("zone").executes(CameraCommands::showFocus)
                        .then(argument("zone", word()).suggests(ZONE_CHOICES)
                                .executes(c -> run(c, action("camera.focus", "zone", str(c, "zone"))))))
                // On air
                .then(literal("cut").then(argument("camera", word()).suggests(CAMERAS)
                        .executes(c -> run(c, action("camera.cut", "camera", str(c, "camera"))))
                        .then(argument("transition", word()).suggests(TRANSITIONS)
                                .executes(c -> run(c, action("camera.cut", "camera", str(c, "camera"), "transition", str(c, "transition"))))
                                .then(argument("effect", word()).suggests(EFFECT_IDS)
                                        .executes(c -> run(c, action("camera.cut", "camera", str(c, "camera"), "transition", str(c, "transition"),
                                                "effect", str(c, "effect"))))))))
                .then(literal("wide").executes(c -> run(c, action("camera.cut", "camera", CameraSpot.WIDE))))
                .then(literal("next").executes(c -> step(c, 1)))
                .then(literal("prev").executes(c -> step(c, -1)))
                .then(literal("follow").then(argument("player", word()).suggests(ONLINE)
                        .executes(c -> run(c, action("camera.follow", "player", str(c, "player"))))
                        .then(argument("transition", word()).suggests(TRANSITIONS)
                                .executes(c -> run(c, action("camera.follow", "player", str(c, "player"), "transition", str(c, "transition")))))))
                .then(literal("pov").then(argument("player", word()).suggests(ONLINE)
                        .executes(c -> run(c, action("camera.pov", "player", str(c, "player"))))))
                .then(literal("mosaic").executes(c -> run(c, action("camera.mosaic")))
                        .then(literal("off").executes(c -> run(c, action("camera.mosaic", "enabled", false))))
                        .then(argument("lieu", word()).suggests(LIEUX).executes(c -> run(c, action("camera.mosaic", "lieu", str(c, "lieu"))))))
                .then(literal("auto")
                        // Cut on who speaks (voice chat) as well
                        .then(literal("voix").executes(c -> voice(c))
                                .then(argument("enabled", word()).suggests(ON_OFF).executes(c -> {
                                    Director.setVoiceCuts(onOff(c, "enabled"));
                                    return voice(c);
                                })))
                        .then(argument("enabled", word()).suggests(ON_OFF)
                        .executes(c -> run(c, action("camera.auto", "enabled", onOff(c, "enabled"))))))
                .then(literal("transition").then(argument("transition", word()).suggests(TRANSITIONS)
                        .executes(c -> run(c, action("camera.transition", "transition", str(c, "transition"))))))
                // Automatic framing: go and see a player, stay on them, wider / tighter, cameras to avoid
                .then(literal("voir").then(argument("player", word()).suggests(ONLINE)
                        .executes(c -> run(c, action("camera.goto", "player", str(c, "player"))))))
                .then(literal("rester").then(literal("off").executes(c -> run(c, action("camera.stay", "enabled", false))))
                        .then(argument("player", word()).suggests(ONLINE)
                                .executes(c -> run(c, action("camera.stay", "player", str(c, "player"))))))
                .then(literal("cadrage").then(argument("size", word()).suggests(list(c -> List.of("large", "serre", "auto")))
                        .executes(c -> run(c, action("camera.framing", "size", str(c, "size"))))))
                .then(literal("eviter").then(argument("camera", word()).suggests(CAMERAS)
                        .executes(c -> run(c, action("camera.avoid", "camera", str(c, "camera"))))
                        .then(literal("off").executes(c -> run(c, action("camera.avoid", "camera", str(c, "camera"), "enabled", false))))))
                // Travellings
                .then(literal("jouer").then(argument("path", word()).suggests(PATHS)
                        .executes(c -> run(c, action("camera.path", "path", str(c, "path"))))
                        .then(argument("transition", word()).suggests(TRANSITIONS)
                                .executes(c -> run(c, action("camera.path", "path", str(c, "path"), "transition", str(c, "transition"))))
                                .then(argument("effect", word()).suggests(EFFECT_IDS)
                                        .executes(c -> run(c, action("camera.path", "path", str(c, "path"), "transition", str(c, "transition"),
                                                "effect", str(c, "effect"))))))))
                .then(literal("trajets").executes(CameraCommands::listPaths))
                .then(literal("trajet").executes(CameraCommands::listPaths).then(pathCommands()));
    }

    private static int listCameras(CommandContext<CommandSourceStack> c, boolean everything) {
        String focus = Director.focus();
        List<CameraSpot> all = everything ? Cameras.list().all() : Cameras.list().group(focus).all();
        c.getSource().sendSystemMessage(Component.literal("— Caméras : " + (everything ? "tous les lieux" : Director.focusName()) + " —")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (all.isEmpty()) c.getSource().sendSystemMessage(Component.literal(Cameras.list().isEmpty()
                ? "Aucune caméra. Place-toi et regarde le plan voulu, puis /cm cam ici 1 Plan large (ou pose l'objet Caméra de studio)."
                : "Aucune caméra ici (/cm cam liste tout pour les autres lieux).").withStyle(ChatFormatting.GRAY));
        BroadcastNetwork.Shot shot = Director.shot();
        String group = "";
        for (CameraSpot s : all) {
            if (everything && !s.group().equals(group)) {
                group = s.group();
                c.getSource().sendSystemMessage(Component.literal(" " + CameraSpot.placeName(group) + " (" + Lieux.lieuName(s.lieu()) + ")"
                        + (group.equals(focus) ? " ← touches 1–9" : "")).withStyle(ChatFormatting.YELLOW));
            }
            boolean onAir = shot.kind() == BroadcastNetwork.SHOT_CAMERA && shot.camera().equals(s.key());
            c.getSource().sendSystemMessage(Component.literal((onAir ? "● " : "  ") + s.number() + " · " + s.name()
                            + (s.role().isEmpty() ? "" : " (" + s.role() + ")")).withStyle(onAir ? ChatFormatting.RED : ChatFormatting.WHITE)
                    .append(Component.literal("  " + s.key() + " · " + s.describe()).withStyle(ChatFormatting.GRAY)));
        }
        c.getSource().sendSystemMessage(Component.literal("À l'antenne : " + onAir(shot) + " · touches 1–9 : " + Director.focusName()
                + " · réalisation auto " + (Director.autoDirection() ? "on" : "off") + " · " + Director.broadcasters().size()
                + " jeu(x) en mode diffusion" + (Director.sequenceRunning() ? " · générique / intro en cours" : "")).withStyle(ChatFormatting.GRAY));
        return all.size();
    }

    static String onAir(BroadcastNetwork.Shot shot) {
        return switch (shot.kind()) {
            case BroadcastNetwork.SHOT_CAMERA -> {
                CameraSpot s = Cameras.list().get(shot.camera());
                yield "caméra " + (s == null ? shot.camera() : s.label());
            }
            case BroadcastNetwork.SHOT_FOLLOW -> "on suit " + shot.player();
            case BroadcastNetwork.SHOT_PATH -> "trajet « " + shot.camera() + " »";
            case BroadcastNetwork.SHOT_MOSAIC -> "écran partagé (" + shot.tiles().size() + ")";
            case BroadcastNetwork.SHOT_POV -> "vue de " + shot.player();
            case BroadcastNetwork.SHOT_FREE -> "caméra libre";
            default -> "rien (vue du joueur)";
        } + (shot.effect().isEmpty() ? "" : ", effet " + shot.effect());
    }

    private static int showFocus(CommandContext<CommandSourceStack> c) {
        List<String> zones = new ArrayList<>(Cameras.list().zones(Director.focusLieu()));
        zones.remove("");
        ok(c, "Touches 1–9 : " + Director.focusName() + " (" + Cameras.list().group(Director.focus()).size() + " caméra(s))"
                + (zones.isEmpty() ? "" : " · zones : " + String.join(", ", zones)) + ". /cm cam focus <studio|manche|auto> [zone], /cm cam zone <zone|joueur|suivante|tout>.");
        return 1;
    }

    /**
     * Camera {@code ref} put where the player's eyes are, looking where they look: moved if it exists, else created
     * ("2" in the focus, "pont:ensuri:2" in that group).
     */
    private static int here(CommandContext<CommandSourceStack> c, String name) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        String ref = str(c, "camera");
        // Standing in an arena, a bare number is a camera of its round (not of the studio in focus)
        String here = groupHere(c);
        if (ref.indexOf(':') < 0 && !here.equals(Director.focus())) ref = CameraSpot.lieuOf(here) + ":" + ref;
        CameraSpot old = Director.resolve(c.getSource().getServer(), ref);
        CameraSpot spot = old != null
                ? old.withPlace(Busts.worldId(p.level()), p.getX(), p.getEyeY(), p.getZ(), p.getYRot(), p.getXRot())
                : newSpot(c, ref, Busts.worldId(p.level()), p.getX(), p.getEyeY(), p.getZ(), p.getYRot(), p.getXRot());
        if (spot == null) return 0;
        if (name != null) spot = spot.withName(name);
        Cameras.place(c.getSource().getServer(), spot);
        ok(c, "Caméra " + spot.label() + (old == null ? " posée" : " déplacée") + " à ta place (" + spot.placeName() + "), elle regarde là où tu regardes.");
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> c, String name) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        String group = groupHere(c);
        CameraSpot spot = new CameraSpot(Cameras.list().nextFree(group), name, "", Busts.worldId(p.level()), p.getX(), p.getEyeY(), p.getZ(),
                p.getYRot(), p.getXRot(), 0, CameraSpot.BREATHING, CameraSpot.lieuOf(group), CameraSpot.zoneOf(group));
        Cameras.place(c.getSource().getServer(), spot);
        ok(c, "Caméra " + spot.label() + " posée à ta place (" + spot.placeName() + ").");
        return 1;
    }

    private static int aim(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        return set(c, s -> s.withRotation(p.getYRot(), p.getXRot()), "elle regarde maintenant là où tu regardes");
    }

    private static int gotoCamera(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        CameraSpot s = spot(c);
        ServerLevel level = s == null ? null : Busts.level(c.getSource().getServer(), s.world());
        if (level == null) return 0;
        // Eyes at the lens: what the player sees is the shot (to check or adjust it)
        p.teleportTo(level, s.x(), s.y() - p.getEyeHeight(), s.z(), Set.of(), s.yaw(), s.pitch(), false);
        ok(c, "Tu vois ce que voit la caméra " + s.label() + ". /cm cam ici " + s.key() + " pour la recadrer d'ici.");
        return 1;
    }

    /** A new camera for the ref {@code 3}, {@code pont:2}, {@code pont:ensuri:1}; null (said so) when the ref is not one. */
    /**
     * The lieu a new camera goes to without one in its name: the round of the arena the player stands in (its
     * reference room or a copy: where intro cameras are placed), else the focus.
     */
    private static String groupHere(CommandContext<CommandSourceStack> c) {
        String arena = arenaGroup(c);
        return arena != null ? arena : Director.focus();
    }

    /** The round (lieu) of the arena the player stands in (its reference room or a copy), or null. */
    private static String arenaGroup(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return null;
        var owner = fr.lordfinn.craftmaster.trial.kit.Arenas.ownerAt(p.level(), p.blockPosition());
        return owner != null && owner.arena().manche != null ? Lieux.lieu(owner.arena().manche) : null;
    }

    private static CameraSpot newSpot(CommandContext<CommandSourceStack> c, String ref, String world, double x, double y, double z, float yaw, float pitch) {
        String group = ref.lastIndexOf(':') > 0 ? Director.focus() : groupHere(c);
        String num = ref;
        int colon = ref.lastIndexOf(':');
        if (colon > 0) {
            group = Cameras.list().place(ref.substring(0, colon), group, Lieux.places(c.getSource().getServer(), group), true);
            num = ref.substring(colon + 1);
            if (group != null && !CameraSpot.STUDIO.equals(CameraSpot.lieuOf(group)) && Lieux.lieu(CameraSpot.lieuOf(group)) == null) group = null;
        }
        if (group == null || !num.matches("\\d{1,2}") || Integer.parseInt(num) < 1) {
            fail(c, "« " + ref + " » : donne un numéro (/cm cam ici 3) ou lieu:numéro (/cm cam ici pont:2, /cm cam ici pont:ensuri:1).");
            return null;
        }
        return new CameraSpot(Integer.parseInt(num), null, "", world, x, y, z, yaw, pitch, 0, CameraSpot.BREATHING,
                CameraSpot.lieuOf(group), CameraSpot.zoneOf(group));
    }

    /** {@code /cm cam placer <n> <x y z> vers <x y z>}: the lens there, looking at the target; a new camera is a bare viewpoint. */
    private static int placeAt(CommandContext<CommandSourceStack> c) {
        var pos = net.minecraft.commands.arguments.coordinates.Vec3Argument.getVec3(c, "pos");
        var target = net.minecraft.commands.arguments.coordinates.Vec3Argument.getVec3(c, "target");
        double dx = target.x - pos.x, dy = target.y - pos.y, dz = target.z - pos.z;
        if (dx * dx + dy * dy + dz * dz < 1e-4) {
            fail(c, "La cible est à la place de la caméra.");
            return 0;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        var server = c.getSource().getServer();
        String ref = str(c, "camera");
        CameraSpot old = Director.resolve(server, ref);
        String world = old != null ? old.world() : Busts.worldId(c.getSource().getLevel());
        CameraSpot spot = old != null ? old.withPlace(world, pos.x, pos.y, pos.z, yaw, pitch) : newSpot(c, ref, world, pos.x, pos.y, pos.z, yaw, pitch);
        if (spot == null) return 0;
        // A camera with a tripod keeps it (moved); otherwise nothing stands in the players' way
        if (old != null && Cameras.prop(server, old.key()) != null) Cameras.place(server, spot);
        else Cameras.placeViewpoint(server, spot);
        ok(c, "Caméra " + spot.label() + (old == null ? " posée" : " déplacée") + String.format(java.util.Locale.ROOT,
                " en %.1f %.1f %.1f, vers %.1f %.1f %.1f.", pos.x, pos.y, pos.z, target.x, target.y, target.z));
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> c, UnaryOperator<CameraSpot> change, String what) {
        CameraSpot s = spot(c);
        if (s == null) return 0;
        Cameras.update(c.getSource().getServer(), s.key(), change);
        ok(c, "Caméra " + s.label() + " : " + what + ".");
        return 1;
    }

    private static int step(CommandContext<CommandSourceStack> c, int delta) {
        BroadcastNetwork.Shot shot = Director.shot();
        CameraSpot s = Cameras.list().group(Director.focus()).step(shot.kind() == BroadcastNetwork.SHOT_CAMERA ? shot.camera() : "", delta);
        if (s == null) {
            fail(c, "Aucune caméra dans " + Director.focusName() + ".");
            return 0;
        }
        return run(c, action("camera.cut", "camera", s.key()));
    }

    /** All eyes on a player: what chantier D's envelopes fire ({@code ShowEvents.spotlight}), by hand. */
    private static int spotlight(CommandContext<CommandSourceStack> c, String reason) {
        String player = str(c, "player");
        fr.lordfinn.craftmaster.show.ShowEvents.spotlight(c.getSource().getServer(), player, reason == null ? "" : reason);
        ok(c, "Projecteur sur " + player + (Director.autoDirection() ? "." : " (la réalisation automatique est désactivée : /cm cam auto on)."));
        return 1;
    }

    // ---------------------------------------------------------------- travellings

    private static int listPaths(CommandContext<CommandSourceStack> c) {
        c.getSource().sendSystemMessage(Component.literal("— Trajets (travellings) —").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (CameraPaths.all().isEmpty()) c.getSource().sendSystemMessage(Component.literal(
                "Aucun trajet. Place-toi au départ et tape /cm cam trajet <nom> point, puis déplace-toi et recommence (ou /cm cam trajet <nom> objet).")
                .withStyle(ChatFormatting.GRAY));
        for (CameraPath p : CameraPaths.all())
            c.getSource().sendSystemMessage(Component.literal("  " + p.name()).withStyle(ChatFormatting.WHITE)
                    .append(Component.literal("  " + Lieux.lieuName(p.lieu()) + " · " + p.steps().size() + " étape(s) · "
                            + CameraPath.seconds(Math.round(p.duration() * 10) / 10.0)).withStyle(ChatFormatting.GRAY)));
        return CameraPaths.all().size();
    }

    private static CameraPath path(CommandContext<CommandSourceStack> c) {
        String n = str(c, "path");
        CameraPath p = CameraPaths.get(n);
        if (p == null) fail(c, "Aucun trajet « " + n + " » (/cm cam trajets).");
        return p;
    }

    /** Index (1-based argument "step", or the last step) of a path; -1 (said so) if out of range. */
    private static int stepIndex(CommandContext<CommandSourceStack> c, CameraPath p, boolean given) {
        if (p.isEmpty()) {
            fail(c, "Le trajet « " + p.name() + " » n'a aucune étape.");
            return -1;
        }
        int i = given ? getInteger(c, "step") - 1 : p.steps().size() - 1;
        if (i < 0 || i >= p.steps().size()) {
            fail(c, "Le trajet « " + p.name() + " » a " + p.steps().size() + " étape(s).");
            return -1;
        }
        return i;
    }

    private static int changeStep(CommandContext<CommandSourceStack> c, boolean given, UnaryOperator<CameraPath.Step> change, String what) {
        CameraPath p = path(c);
        if (p == null) return 0;
        int i = stepIndex(c, p, given);
        if (i < 0) return 0;
        List<CameraPath.Step> steps = new ArrayList<>(p.steps());
        steps.set(i, change.apply(steps.get(i)));
        CameraPaths.put(c.getSource().getServer(), p.withSteps(steps));
        PathRecording.show(c.getSource().getServer(), p.name(), 6);
        ok(c, "Trajet « " + p.name() + " », étape " + (i + 1) + " : " + what + " (total " + CameraPath.seconds(Math.round(p.withSteps(steps).duration() * 10) / 10.0) + ").");
        return 1;
    }

    /** A sub-command that changes one step: "… <value>" (last step) or "… <value> <step>". */
    private static RequiredArgumentBuilder<CommandSourceStack, ?> onStep(RequiredArgumentBuilder<CommandSourceStack, ?> value,
                                                                           java.util.function.BiFunction<CommandContext<CommandSourceStack>, Boolean, Integer> run) {
        return value.executes(c -> run.apply(c, false)).then(argument("step", integer(1, 99)).executes(c -> run.apply(c, true)));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> pathCommands() {
        return argument("path", word()).suggests(PATHS)
                .executes(CameraCommands::pathInfo)
                .then(literal("info").executes(CameraCommands::pathInfo))
                .then(literal("point")
                        .executes(c -> addPoint(c, -1))
                        .then(argument("seconds", DoubleArgumentType.doubleArg(0, CameraPath.MAX_SECONDS)).executes(c -> addPoint(c, DoubleArgumentType.getDouble(c, "seconds")))))
                .then(literal("remplacer").then(argument("step", integer(1, 99)).executes(c -> {
                    ServerPlayer p = player(c);
                    if (p == null) return 0;
                    return changeStep(c, true, s -> s.withPlace(p.getX(), p.getEyeY(), p.getZ(), p.getYRot(), p.getXRot()), "replacée à tes yeux");
                })))
                .then(literal("duree").then(onStep(argument("seconds", DoubleArgumentType.doubleArg(0, CameraPath.MAX_SECONDS)), (c, given) -> {
                    double s = DoubleArgumentType.getDouble(c, "seconds");
                    return changeStep(c, given, st -> st.withTravel(s), "atteinte en " + CameraPath.seconds(s));
                })))
                .then(literal("pause").then(onStep(argument("seconds", DoubleArgumentType.doubleArg(0, CameraPath.MAX_SECONDS)), (c, given) -> {
                    double s = DoubleArgumentType.getDouble(c, "seconds");
                    return changeStep(c, given, st -> st.withHold(s), s > 0 ? "pause de " + CameraPath.seconds(s) : "sans pause");
                })))
                .then(literal("courbe").then(onStep(argument("curve", word()).suggests(CURVES), (c, given) -> {
                    String k = CameraPath.normalizeCurve(str(c, "curve"));
                    return changeStep(c, given, st -> st.withCurve(k), "courbe « " + k + " »");
                })))
                .then(literal("zoom")
                        .then(onStep(argument("fov", FloatArgumentType.floatArg(CameraSpot.MIN_FOV, CameraSpot.MAX_FOV)), (c, given) -> {
                            float f = FloatArgumentType.getFloat(c, "fov");
                            return changeStep(c, given, st -> st.withFov(f), "champ de " + Math.round(f) + "°");
                        }))
                        .then(literal("reset").executes(c -> changeStep(c, false, st -> st.withFov(0), "champ du joueur"))
                                .then(argument("step", integer(1, 99)).executes(c -> changeStep(c, true, st -> st.withFov(0), "champ du joueur")))))
                .then(literal("suppr")
                        .executes(c -> removeStep(c, -1))
                        .then(argument("step", integer(1, 99)).executes(c -> removeStep(c, getInteger(c, "step") - 1))))
                .then(literal("aller").then(argument("step", integer(1, 99)).executes(c -> {
                    ServerPlayer p = player(c);
                    CameraPath path = path(c);
                    if (p == null || path == null) return 0;
                    int i = stepIndex(c, path, true);
                    ServerLevel level = Busts.level(c.getSource().getServer(), path.world());
                    if (i < 0 || level == null) return 0;
                    CameraPath.Step s = path.steps().get(i);
                    p.teleportTo(level, s.x(), s.y() - p.getEyeHeight(), s.z(), Set.of(), s.yaw(), s.pitch(), false);
                    ok(c, "Tu es à l'étape " + (i + 1) + " du trajet « " + path.name() + " » (/cm cam trajet " + path.name() + " remplacer " + (i + 1) + " pour la refaire d'ici).");
                    return 1;
                })))
                .then(literal("apercu").executes(CameraCommands::preview))
                .then(literal("montrer").executes(c -> {
                    CameraPath p = path(c);
                    if (p == null) return 0;
                    PathRecording.show(c.getSource().getServer(), p.name(), 20);
                    ok(c, "Trajet « " + p.name() + " » dessiné 20 s (poussière dorée, étapes en rouge).");
                    return 1;
                }))
                .then(literal("jouer").executes(c -> run(c, action("camera.path", "path", str(c, "path"))))
                        .then(argument("transition", word()).suggests(TRANSITIONS)
                                .executes(c -> run(c, action("camera.path", "path", str(c, "path"), "transition", str(c, "transition"))))))
                .then(literal("lieu").then(argument("lieu", word()).suggests(LIEUX).executes(c -> {
                    CameraPath p = path(c);
                    if (p == null) return 0;
                    String lieu = Lieux.lieu(str(c, "lieu"));
                    if (lieu == null) {
                        fail(c, "Lieu « " + str(c, "lieu") + " » inconnu (studio ou une manche).");
                        return 0;
                    }
                    CameraPaths.put(c.getSource().getServer(), p.withLieu(lieu));
                    ok(c, "Trajet « " + p.name() + " » : lieu " + Lieux.lieuName(lieu) + ".");
                    return 1;
                })))
                .then(literal("objet").executes(c -> {
                    ServerPlayer p = player(c);
                    if (p == null) return 0;
                    String n = CameraPath.normalizeName(str(c, "path"));
                    p.getInventory().add(PathMarkerItem.bound(ModContent.PATH_MARKER, n));
                    ok(c, "Repère de travelling pour « " + n + " » : clic droit = une étape à tes yeux, accroupi + clic droit = retirer la dernière.");
                    return 1;
                }))
                .then(literal("effacer").executes(c -> {
                    String n = str(c, "path");
                    if (!CameraPaths.remove(c.getSource().getServer(), n)) {
                        fail(c, "Aucun trajet « " + n + " ».");
                        return 0;
                    }
                    ok(c, "Trajet « " + CameraPath.normalizeName(n) + " » effacé.");
                    return 1;
                }));
    }

    private static int addPoint(CommandContext<CommandSourceStack> c, double seconds) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        String line = PathRecording.addStep(c.getSource().getServer(), p, str(c, "path"), seconds);
        if (line.startsWith("⚠")) {
            fail(c, line);
            return 0;
        }
        ok(c, line);
        return 1;
    }

    private static int removeStep(CommandContext<CommandSourceStack> c, int index) {
        String line = PathRecording.removeStep(c.getSource().getServer(), str(c, "path"), index);
        if (line.startsWith("⚠")) {
            fail(c, line);
            return 0;
        }
        ok(c, line);
        return 1;
    }

    private static int pathInfo(CommandContext<CommandSourceStack> c) {
        CameraPath p = path(c);
        if (p == null) return 0;
        c.getSource().sendSystemMessage(Component.literal("— Trajet « " + p.name() + " » — " + Lieux.lieuName(p.lieu()) + " · "
                + CameraPath.seconds(Math.round(p.duration() * 10) / 10.0)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (int i = 0; i < p.steps().size(); i++)
            c.getSource().sendSystemMessage(Component.literal("  " + p.steps().get(i).describe(i)).withStyle(ChatFormatting.GRAY));
        c.getSource().sendSystemMessage(Component.literal("point [s] · duree <s> [étape] · pause <s> [étape] · courbe · zoom · suppr [étape] · "
                + "remplacer <étape> · aller <étape> · apercu · montrer · jouer · objet · lieu · effacer").withStyle(ChatFormatting.DARK_GRAY));
        PathRecording.show(c.getSource().getServer(), p.name(), 10);
        return 1;
    }

    /** The travelling on the organiser's own screen (not on the broadcast). */
    private static int preview(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        CameraPath path = path(c);
        if (p == null || path == null) return 0;
        if (path.steps().size() < 2) {
            fail(c, "Il faut au moins deux étapes pour un aperçu.");
            return 0;
        }
        if (!ServerPlayNetworking.canSend(p, BroadcastNetwork.ShowCue.TYPE)) {
            fail(c, "Ton jeu n'a pas le mod : /cm cam trajet " + path.name() + " montrer dessine le trajet.");
            return 0;
        }
        ServerPlayNetworking.send(p, new BroadcastNetwork.ShowCue(BroadcastNetwork.Cues.PATH_PREVIEW, path.name(), "", "",
                (int) Math.ceil(path.duration() * 20)));
        // The dust line would be right in front of the lens all the way
        PathRecording.hide(path.name());
        ok(c, "Aperçu du trajet « " + path.name() + " » sur ton écran (" + CameraPath.seconds(Math.round(path.duration() * 10) / 10.0) + ").");
        return 1;
    }

    // ---------------------------------------------------------------- /cm tv

    private static int voice(CommandContext<CommandSourceStack> c) {
        ok(c, "Réalisation automatique sur qui parle : " + (Director.voiceCuts() ? "oui" : "non")
                + (fr.lordfinn.craftmaster.broadcast.Speaking.available() ? "" : " (chat vocal absent : rien à suivre)")
                + (Director.autoDirection() ? "." : " · la réalisation automatique est arrêtée (/cm cam auto on)."));
        return 1;
    }

    private static int feed(CommandContext<CommandSourceStack> c) {
        ok(c, fr.lordfinn.craftmaster.broadcast.ShowFeed.describe());
        return 1;
    }

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
                // The show's feed on the players' screens: forced on everyone or not, moving allowed while forced
                .then(literal("flux").executes(c -> feed(c))
                        .then(literal("forcer").executes(c -> {
                            fr.lordfinn.craftmaster.broadcast.ShowFeed.setManual(true);
                            return feed(c);
                        }))
                        .then(literal("libre").executes(c -> {
                            fr.lordfinn.craftmaster.broadcast.ShowFeed.setManual(false);
                            return feed(c);
                        }))
                        .then(literal("bouger").then(argument("enabled", word()).suggests(ON_OFF).executes(c -> {
                            fr.lordfinn.craftmaster.broadcast.ShowFeed.setAllowMove(onOff(c, "enabled"));
                            return feed(c);
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
                            if (target == null || !ServerPlayNetworking.canSend(target, BroadcastNetwork.ShowCue.TYPE)) {
                                fail(c, str(c, "player") + " n'est pas connecté avec le mod.");
                                return 0;
                            }
                            boolean on = onOff(c, "enabled");
                            ServerPlayNetworking.send(target, new BroadcastNetwork.ShowCue(BroadcastNetwork.Cues.BROADCAST, on ? "on" : "off", "", "", 0));
                            ok(c, "Mode diffusion " + (on ? "activé" : "désactivé") + " pour " + target.getGameProfile().name() + ".");
                            return 1;
                        }))))
                .then(literal("spotlight").then(argument("player", word()).suggests(ONLINE)
                        .executes(c -> spotlight(c, null))
                        .then(argument("reason", StringArgumentType.greedyString()).executes(c -> spotlight(c, str(c, "reason"))))))
                .then(literal("intro").executes(c -> run(c, action("show.intro"))).then(introCommands()))
                .then(literal("outro").executes(c -> run(c, action("show.outro"))))
                .then(literal("stop").executes(c -> run(c, action("show.stop"))));
    }

    // ---------------------------------------------------------------- round intros

    private static Map<String, Object> intro(CommandContext<CommandSourceStack> c, String type, Object... fields) {
        Map<String, Object> a = action(type, fields);
        a.put("manche", str(c, "manche"));
        return a;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ?> addShot(String literalName, String field, SuggestionProvider<CommandSourceStack> suggest) {
        return argument("ref", word()).suggests(suggest)
                .executes(c -> run(c, intro(c, "intro.shot.add", field, str(c, "ref"))))
                .then(argument("seconds", DoubleArgumentType.doubleArg(0, MancheIntro.MAX_SECONDS))
                        .executes(c -> run(c, intro(c, "intro.shot.add", field, str(c, "ref"), "seconds", DoubleArgumentType.getDouble(c, "seconds"))))
                        .then(argument("transition", word()).suggests(TRANSITIONS)
                                .executes(c -> run(c, intro(c, "intro.shot.add", field, str(c, "ref"), "seconds", DoubleArgumentType.getDouble(c, "seconds"),
                                        "transition", str(c, "transition"))))
                                .then(argument("effect", word()).suggests(EFFECT_IDS)
                                        .executes(c -> run(c, intro(c, "intro.shot.add", field, str(c, "ref"), "seconds", DoubleArgumentType.getDouble(c, "seconds"),
                                                "transition", str(c, "transition"), "effect", str(c, "effect")))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> introCommands() {
        return argument("manche", word()).suggests(MANCHES)
                .executes(c -> run(c, intro(c, "intro.play")))
                .then(literal("jouer").executes(c -> run(c, intro(c, "intro.play"))))
                .then(literal("voir").executes(CameraCommands::showIntro))
                .then(literal("ajouter")
                        .then(literal("camera").then(addShot("camera", "camera", CAMERAS)))
                        .then(literal("trajet").then(addShot("trajet", "path", PATHS)))
                        .then(literal("suivi").then(addShot("suivi", "follow", ONLINE)))
                        // The task location shot: arrival point and envelope (camera generated the first time)
                        .then(literal("tache").executes(c -> run(c, intro(c, "intro.shot.add", "task", true)))
                                .then(argument("seconds", DoubleArgumentType.doubleArg(0, MancheIntro.MAX_SECONDS))
                                        .executes(c -> run(c, intro(c, "intro.shot.add", "task", true, "seconds", DoubleArgumentType.getDouble(c, "seconds")))))))
                .then(literal("plan").then(argument("index", integer(1, MancheIntro.MAX_SHOTS))
                        .then(literal("retirer").executes(c -> run(c, intro(c, "intro.shot.remove", "index", getInteger(c, "index")))))
                        // The intro editor (clickable in /cm tv intro <m> voir): try from here, reorder, other camera
                        .then(literal("essayer").executes(c -> run(c, intro(c, "intro.play", "from", getInteger(c, "index")))))
                        .then(literal("monter").executes(c -> run(c, intro(c, "intro.shot.move", "index", getInteger(c, "index"), "to", getInteger(c, "index") - 1))))
                        .then(literal("descendre").executes(c -> run(c, intro(c, "intro.shot.move", "index", getInteger(c, "index"), "to", getInteger(c, "index") + 1))))
                        .then(literal("camera").then(argument("ref", word()).suggests(CAMERAS)
                                .executes(c -> run(c, intro(c, "intro.shot.set", "index", getInteger(c, "index"), "camera", str(c, "ref"))))))
                        .then(literal("tache").executes(c -> run(c, intro(c, "intro.shot.set", "index", getInteger(c, "index"), "task", true))))
                        .then(literal("duree").then(argument("seconds", DoubleArgumentType.doubleArg(0, MancheIntro.MAX_SECONDS))
                                .executes(c -> run(c, intro(c, "intro.shot.set", "index", getInteger(c, "index"), "seconds", DoubleArgumentType.getDouble(c, "seconds"))))))
                        .then(literal("transition").then(argument("transition", word()).suggests(TRANSITIONS)
                                .executes(c -> run(c, intro(c, "intro.shot.set", "index", getInteger(c, "index"), "transition", str(c, "transition"))))))
                        .then(literal("effet").then(argument("effect", word()).suggests(EFFECT_IDS)
                                .executes(c -> run(c, intro(c, "intro.shot.set", "index", getInteger(c, "index"), "effect", str(c, "effect"))))))
                        // The focus pull of its depth of field: targets, timing, blur (FocusCommands)
                        .then(FocusCommands.branch())
                        .then(literal("carte").then(argument("card", word()).suggests(CARDS)
                                .executes(c -> run(c, intro(c, "intro.shot.set", "index", getInteger(c, "index"), "card", str(c, "card"), "text", "")))
                                .then(argument("text", StringArgumentType.greedyString())
                                        .executes(c -> run(c, intro(c, "intro.shot.set", "index", getInteger(c, "index"), "card", str(c, "card"), "text", str(c, "text")))))))))
                .then(literal("auto").then(argument("enabled", word()).suggests(ON_OFF)
                        .executes(c -> run(c, intro(c, "intro.set", "auto", onOff(c, "enabled"))))))
                .then(literal("musique").then(argument("music", word()).suggests(MUSIC)
                        .executes(c -> run(c, intro(c, "intro.set", "music", str(c, "music"))))))
                .then(literal("titre").then(argument("enabled", word()).suggests(ON_OFF)
                        .executes(c -> run(c, intro(c, "intro.set", "titleCard", onOff(c, "enabled"))))))
                // Cut on the music: tempo ("theme" = the show's theme), shot length in beats, first beat (s)
                .then(literal("rythme").then(argument("bpm", word()).suggests(list(c -> List.of("intro", "theme", "off", "120")))
                        .executes(c -> run(c, intro(c, "intro.set", "bpm", str(c, "bpm"))))
                        .then(argument("beats", integer(1, 64))
                                .executes(c -> run(c, intro(c, "intro.set", "bpm", str(c, "bpm"), "beats", getInteger(c, "beats"))))
                                .then(argument("offset", DoubleArgumentType.doubleArg(0, 30))
                                        .executes(c -> run(c, intro(c, "intro.set", "bpm", str(c, "bpm"), "beats", getInteger(c, "beats"),
                                                "offset", DoubleArgumentType.getDouble(c, "offset"))))))))
                .then(literal("enchaine").then(argument("enabled", word()).suggests(ON_OFF)
                        .executes(c -> run(c, intro(c, "intro.set", "chained", onOff(c, "enabled"))))))
                // Everything made from the round's cameras, on the intro music
                .then(literal("construire").executes(c -> run(c, intro(c, "intro.auto"))))
                .then(literal("look").then(argument("effect", word()).suggests(EFFECT_IDS)
                        .executes(c -> run(c, intro(c, "intro.set", "look", str(c, "effect"))))))
                .then(literal("consigne").then(argument("enabled", word()).suggests(ON_OFF)
                        .executes(c -> run(c, intro(c, "intro.set", "typedTask", onOff(c, "enabled"))))))
                // Places the task location camera again (after moving the TP point or the envelope)
                .then(literal("lieu").executes(CameraCommands::taskCamera))
                .then(literal("effacer").executes(c -> run(c, intro(c, "intro.clear"))));
    }

    /** "[label]" that runs or (suggest) proposes {@code command}, with a hover text. */
    private static Component button(String label, String command, boolean suggest, String hover, ChatFormatting color) {
        net.minecraft.network.chat.ClickEvent click = suggest ? new net.minecraft.network.chat.ClickEvent.SuggestCommand(command)
                : new net.minecraft.network.chat.ClickEvent.RunCommand(command);
        return Component.literal("[" + label + "]").withStyle(st -> st.withColor(color).withClickEvent(click)
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(hover + "\n" + command)))).append(" ");
    }

    /**
     * The intro as a strip on the music: one block per beat (or per half second without a rhythm), each shot in its
     * colour, hover = the shot; the music's sting under its 40th beat when the intro music plays.
     */
    private static Component strip(Manche m, MancheIntro in, List<MancheIntro.Timed> timeline) {
        ChatFormatting[] colors = {ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.GREEN,
                ChatFormatting.BLUE, ChatFormatting.GOLD, ChatFormatting.RED, ChatFormatting.DARK_AQUA};
        double unit = in.beatSeconds() > 0 ? in.beatSeconds() : 0.5;
        net.minecraft.network.chat.MutableComponent out = Component.literal("  ");
        int cells = 0;
        for (MancheIntro.Timed t : timeline) {
            int n = (int) Math.max(1, Math.round(t.length() / unit));
            cells += n;
            if (cells > 80) n = Math.max(1, n - (cells - 80));
            ChatFormatting col = colors[t.index() % colors.length];
            String hover = (t.index() + 1) + " · " + t.shot().describe(t, in.beatSeconds());
            out.append(Component.literal("█".repeat(n)).withStyle(st -> st.withColor(col)
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(hover)))
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/cm tv intro " + m.id + " plan " + (t.index() + 1) + " essayer"))));
            if (cells >= 80) break;
        }
        if (in.music.equals(MancheIntro.INTRO_MUSIC) && in.bpm == MancheIntro.INTRO_BPM) {
            double total = timeline.isEmpty() ? 0 : timeline.getLast().start() + timeline.getLast().length();
            double diff = total - MancheIntro.INTRO_MUSIC_SECONDS;
            out.append(Component.literal(Math.abs(diff) < 0.01 ? "  ♪ finit sur l'accord" : diff < 0
                    ? "  ♪ " + MancheIntro.fmtNumber(-diff) + " s avant l'accord" : "  ♪ " + MancheIntro.fmtNumber(diff) + " s après la musique")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return out;
    }

    private static int taskCamera(CommandContext<CommandSourceStack> c) {
        Manche m = Lieux.manche(str(c, "manche"));
        if (m == null) {
            fail(c, "Manche « " + str(c, "manche") + " » introuvable.");
            return 0;
        }
        fr.lordfinn.craftmaster.broadcast.TaskFraming.Result r = fr.lordfinn.craftmaster.broadcast.TaskFraming.regenerate(c.getSource().getServer(), m);
        if (r.camera() == null) {
            fail(c, "Pas de plan « lieu de la tâche » pour « " + m.name + " » : " + r.problem() + ".");
            return 0;
        }
        CameraSpot s = r.camera();
        c.getSource().sendSystemMessage(Component.literal("Lieu de la tâche de « " + m.name + " » : caméra " + s.key()
                + String.format(java.util.Locale.ROOT, " (%.1f %.1f %.1f)", s.x(), s.y(), s.z())
                + (r.problem().isEmpty() ? ", l'enveloppe et l'arrivée dans le cadre." : " — " + r.problem() + ".")
                + " Sans trépied ; /cm cam pour la retoucher.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int showIntro(CommandContext<CommandSourceStack> c) {
        Manche m = Lieux.manche(str(c, "manche"));
        if (m == null) {
            fail(c, "Manche « " + str(c, "manche") + " » introuvable.");
            return 0;
        }
        MancheIntro in = m.intro;
        c.getSource().sendSystemMessage(Component.literal("— Intro de « " + m.name + " » — "
                + (in.auto ? in.chained ? "jouée au lancement, enchaînée (noir, TP, titre, révélation)" : "jouée au lancement" : "à la main (/cm tv intro " + m.id + ")")
                + " · " + (in.music.isEmpty() ? "sans musique" : "musique " + in.music)
                + (in.bpm > 0 ? " · " + in.rhythmText() : "")
                + (in.look.isEmpty() ? "" : " · look " + in.look)
                + (in.typedTask ? "" : " · sans consigne tapée") + " · "
                + CameraPath.seconds(Math.round(in.duration(n -> {
                    CameraPath p = CameraPaths.get(n);
                    return p == null ? 0 : p.duration();
                }) * 10) / 10.0)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (in.shots.isEmpty()) c.getSource().sendSystemMessage(Component.literal(
                "Aucun plan : /cm tv intro " + m.id + " ajouter camera <caméra> [s] · ajouter trajet <nom> [s] · ajouter suivi <joueur> [s]").withStyle(ChatFormatting.GRAY));
        int auto = in.autoCardShot();
        List<MancheIntro.Timed> timeline = in.timeline(n -> {
            CameraPath p = CameraPaths.get(n);
            return p == null ? 0 : p.duration();
        });
        if (!in.shots.isEmpty()) c.getSource().sendSystemMessage(strip(m, in, timeline));
        String base = "/cm tv intro " + m.id + " ";
        for (int i = 0; i < in.shots.size(); i++) {
            MancheIntro.Shot s = in.shots.get(i);
            String plan = base + "plan " + (i + 1) + " ";
            net.minecraft.network.chat.MutableComponent line = Component.literal("  " + (i + 1) + " ").withStyle(ChatFormatting.WHITE)
                    .append(button("▶", base + "plan " + (i + 1) + " essayer", false, "Jouer l'intro à partir de ce plan (sans la musique)", ChatFormatting.GREEN))
                    .append(Component.literal(" " + s.describe(timeline.get(i), in.beatSeconds()) + (i == auto ? ", titre (automatique)" : "") + " ")
                            .withStyle(ChatFormatting.GRAY));
            String away = fr.lordfinn.craftmaster.broadcast.IntroZone.check(m, s);
            if (!away.isEmpty()) line.append(Component.literal("⚠ hors zone, sauté ").withStyle(st -> st.withColor(ChatFormatting.RED)
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(
                            "Ce plan est " + away + " : une intro d'épreuve reste là où arrivent les joueurs (pas de caméra du studio,"
                                    + " pas de trajet ailleurs), il est sauté à la lecture.")))));
            if (i > 0) line.append(button("↑", plan + "monter", false, "Avancer ce plan d'une place", ChatFormatting.AQUA));
            if (i < in.shots.size() - 1) line.append(button("↓", plan + "descendre", false, "Reculer ce plan d'une place", ChatFormatting.AQUA));
            line.append(button("caméra", plan + "camera ", true, "Remplacer par une autre caméra du lieu", ChatFormatting.YELLOW))
                    .append(button("effet", plan + "effet ", true, "Effet d'image de ce plan (aucun pour l'enlever)", ChatFormatting.YELLOW))
                    .append(button("focus", plan + "focus", false, "Mise au point de ce plan : de quoi vers quoi, quand, combien de flou (vise dans le monde)", ChatFormatting.YELLOW))
                    .append(button("transition", plan + "transition ", true, "Transition vers ce plan", ChatFormatting.YELLOW))
                    .append(button("durée", plan + "duree ", true, "Durée en secondes (arrondie aux temps de la musique)", ChatFormatting.YELLOW))
                    .append(button("✖", plan + "retirer", false, "Retirer ce plan (annulable)", ChatFormatting.RED));
            c.getSource().sendSystemMessage(line);
        }
        c.getSource().sendSystemMessage(Component.literal("  ")
                .append(button("▶ tout jouer", base + "jouer", false, "Jouer l'intro entière, avec la musique", ChatFormatting.GREEN))
                .append(button("+ caméra", base + "ajouter camera ", true, "Ajouter un plan sur une caméra", ChatFormatting.YELLOW))
                .append(button("+ lieu de la tâche", base + "ajouter tache", false, "Ajouter le plan « lieu de la tâche »", ChatFormatting.YELLOW))
                .append(button("construire", base + "construire", true, "Refaire toute l'intro à partir des caméras de la manche (remplace les plans)", ChatFormatting.GOLD))
                .append(button("lieu", base + "lieu", false, "Replacer la caméra « lieu de la tâche » (TP ou enveloppe déplacés)", ChatFormatting.GOLD)));
        return in.shots.size();
    }
}
