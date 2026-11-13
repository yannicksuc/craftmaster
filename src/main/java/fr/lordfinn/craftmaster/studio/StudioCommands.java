package fr.lordfinn.craftmaster.studio;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.craftmaster.Permissions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Commands of the studio set (reference: docs/wiki/Plateau.md):
 * <ul>
 *   <li>{@code /craftMasterImage <URL> [largeur] [hauteur]} (aliases {@code /image}, {@code /cfim}, permission
 *   {@code craftmaster:image} like the plugin's craftmaster.image): the screen you look at shows the image, or a new
 *   screen is hung on the wall you look at; {@code clear}, {@code remove}, {@code fit}, {@code size}. The address
 *   {@code chat:twitch}, {@code chat:jeu} or {@code chat:tout} makes the screen a chat wall (media/ChatWall).</li>
 *   <li>{@code /craftMasterSpotlight} ({@code /cfsp}, admin): follow a player, stop, on / off, colour, aim at me;
 *   on the spotlight you look at, or on all of them with {@code all}.</li>
 *   <li>{@code /craftMasterDesk name <nom>} ({@code /cfdk}, admin): the name on the desk you look at.</li>
 *   <li>{@code /craftMasterGong} ({@code /cfgo}, admin): strikes the nearest gong.</li>
 * </ul>
 */
public final class StudioCommands {

    private StudioCommands() {}

    /** How far "the one you look at" goes (blocks). */
    private static final double REACH = 48;

    private static final SuggestionProvider<CommandSourceStack> PLAYERS = (c, b) ->
            SharedSuggestionProvider.suggest(c.getSource().getServer().getPlayerList().getPlayers().stream().map(p -> p.getGameProfile().name()), b);
    private static final SuggestionProvider<CommandSourceStack> DYES = (c, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(DyeColor.values()).map(DyeColor::getName), b);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String name : List.of("craftMasterImage", "craftmasterimage", "image", "cfim")) d.register(image(name));
        for (String name : List.of("craftMasterSpotlight", "craftmasterspotlight", "cfsp")) d.register(spotlight(name));
        for (String name : List.of("craftMasterDesk", "craftmasterdesk", "cfdk")) d.register(desk(name));
        for (String name : List.of("craftMasterGong", "craftmastergong", "cfgo")) d.register(gong(name));
        for (String name : List.of("craftMasterTablet", "craftmastertablet", "cftb")) d.register(tablet(name));
    }

    // ---------------------------------------------------------------- timer tablets

    /** /craftMasterTablet start|pause|toggle|reset|restart [group]: every loaded tablet (of the group). */
    private static LiteralArgumentBuilder<CommandSourceStack> tablet(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal(name).requires(Permissions.ADMIN);
        for (String what : List.of("start", "pause", "toggle", "reset", "restart")) {
            root.then(literal(what)
                    .executes(c -> tabletDone(c, what, TimerTabletBlockEntity.controlAll("", what)))
                    .then(argument("group", StringArgumentType.word())
                            .executes(c -> tabletDone(c, what, TimerTabletBlockEntity.controlAll(StringArgumentType.getString(c, "group"), what)))));
        }
        return root;
    }

    private static int tabletDone(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, String what, int n) {
        c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                n == 0 ? "Aucune tablette chargée (dans ce groupe)." : "Tablettes : " + what + " (" + n + ")"), false);
        return n;
    }

    // ---------------------------------------------------------------- image

    private static LiteralArgumentBuilder<CommandSourceStack> image(String name) {
        return literal(name).requires(Permissions.IMAGE)
                .then(literal("clear").executes(c -> onScreen(c, s -> {
                    s.setUrl("");
                    return "Écran remis sur la mire.";
                })))
                .then(literal("remove").executes(c -> onScreen(c, s -> {
                    s.discard();
                    return "Écran retiré.";
                })))
                // Videos (WaterMedia): the same for every player
                .then(literal("play").executes(c -> onScreen(c, s -> {
                    s.playVideo();
                    return "▶ Lecture.";
                })))
                .then(literal("pause").executes(c -> onScreen(c, s -> {
                    s.pauseVideo();
                    return "⏸ Pause à " + clock(s.pausedAt()) + ".";
                })))
                .then(literal("restart").executes(c -> onScreen(c, s -> {
                    s.restartVideo();
                    return "⏮ Vidéo relancée depuis le début.";
                })))
                .then(literal("seek").then(argument("seconds", integer(0)).executes(c -> {
                    int sec = getInteger(c, "seconds");
                    return onScreen(c, s -> {
                        boolean paused = s.paused();
                        s.restartVideo();
                        s.playVideoAt(sec * 1000L, paused);
                        return "Vidéo à " + clock(sec * 1000L) + ".";
                    });
                })))
                .then(literal("volume").then(argument("percent", integer(0, 100)).executes(c -> {
                    int v = getInteger(c, "percent");
                    return onScreen(c, s -> {
                        s.setVolume(v);
                        return "Volume de l'écran : " + v + " %.";
                    });
                })))
                .then(literal("loop").then(argument("value", BoolArgumentType.bool()).executes(c -> {
                    boolean loop = BoolArgumentType.getBool(c, "value");
                    return onScreen(c, s -> {
                        s.setLoop(loop);
                        return loop ? "La vidéo tourne en boucle." : "La vidéo s'arrête à la fin.";
                    });
                })))
                .then(literal("fit").then(argument("value", BoolArgumentType.bool()).executes(c -> {
                    boolean fit = BoolArgumentType.getBool(c, "value");
                    return onScreen(c, s -> {
                        s.setFit(fit);
                        return fit ? "Image entière (bandes si besoin)." : "Image étirée sur tout l'écran.";
                    });
                })))
                .then(literal("size").then(argument("width", integer(1, ImageRequest.MAX_WIDTH))
                        .then(argument("height", integer(1, ImageRequest.MAX_HEIGHT)).executes(c -> {
                            int w = getInteger(c, "width"), h = getInteger(c, "height");
                            return onScreen(c, s -> {
                                s.resize(w, h);
                                return "Écran de " + w + " × " + h + " blocs.";
                            });
                        }))))
                .then(argument("url", StringArgumentType.greedyString())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("https://", "chat:twitch", "chat:jeu", "chat:tout"), b))
                        .executes(StudioCommands::showImage));
    }

    /** /image <URL> [w] [h]: on the screen looked at, else a new screen on the wall looked at. */
    private static int showImage(CommandContext<CommandSourceStack> c) {
        ImageRequest req;
        try {
            req = ImageRequest.parse(StringArgumentType.getString(c, "url"));
        } catch (ImageRequest.Invalid e) {
            fail(c, e.getMessage());
            return 0;
        }
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) {
            fail(c, "À lancer en jeu, en regardant un écran ou un mur.");
            return 0;
        }
        Optional<ImageScreenEntity> looked = lookedScreen(player);
        String[] given = StringArgumentType.getString(c, "url").trim().split("\\s+");
        if (looked.isPresent()) {
            ImageScreenEntity s = looked.get();
            s.setUrl(req.url());
            if (given.length > 1) s.resize(req.width(), req.height());
            ok(c, (chatWall(req) ? "Mur de chat affiché" : "Image affichée") + " sur l'écran (" + s.width() + " × " + s.height() + " blocs).");
            return 1;
        }
        BlockHitResult hit = lookedBlock(player);
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getDirection().getAxis().isHorizontal()) {
            fail(c, "Regarde un mur (ou un écran du studio) pour y afficher l'image.");
            return 0;
        }
        ServerLevel level = player.level();
        ImageScreenEntity screen = ImageScreenEntity.create(level, hit.getBlockPos(), hit.getDirection(), req.width(), req.height());
        screen.setUrl(req.url());
        level.addFreshEntity(screen);
        screen.playSound(SoundEvents.PAINTING_PLACE, 1f, 0.8f);
        ok(c, "✅ Écran de " + req.width() + " × " + req.height() + " blocs posé : "
                + (chatWall(req) ? "il affiche les derniers messages du chat." : "l'image se charge chez chaque joueur."));
        return 1;
    }

    /** The address is a chat wall (chat:twitch, chat:jeu, chat:tout), not an image. */
    private static boolean chatWall(ImageRequest req) {
        return fr.lordfinn.craftmaster.media.ChatWall.mode(req.url()) != null;
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    private static int onScreen(CommandContext<CommandSourceStack> c, Function<ImageScreenEntity, String> action) {
        ServerPlayer player = c.getSource().getPlayer();
        Optional<ImageScreenEntity> s = player == null ? Optional.empty() : lookedScreen(player);
        if (s.isEmpty()) {
            fail(c, "Regarde un écran du studio.");
            return 0;
        }
        ok(c, action.apply(s.get()));
        return 1;
    }

    /** The studio screen the player looks at (nearest one hit by the view ray, through nothing solid). */
    public static Optional<ImageScreenEntity> lookedScreen(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1f).scale(REACH));
        double wall = lookedBlock(player).getLocation().distanceTo(eye);
        AABB area = new AABB(eye, end).inflate(1);
        return player.level().getEntitiesOfClass(ImageScreenEntity.class, area).stream()
                .filter(s -> s.getBoundingBox().inflate(0.05).clip(eye, end).map(p -> p.distanceTo(eye) <= wall + 0.2).orElse(false))
                .min(Comparator.comparingDouble(s -> s.distanceTo(player)));
    }

    static BlockHitResult lookedBlock(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        return player.level().clip(new ClipContext(eye, eye.add(player.getViewVector(1f).scale(REACH)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }

    // ---------------------------------------------------------------- spotlights

    private static final SuggestionProvider<CommandSourceStack> SPOT_TARGETS = (c, b) -> {
        List<String> l = new java.util.ArrayList<>(List.of("tout"));
        for (String n : Spotlights.names()) {
            l.add(n);
            // A group: the name without its number ("face" = face1, face2…)
            String g = n.replaceAll("[0-9]+$", "");
            if (!g.equals(n) && !g.isEmpty() && !l.contains(g)) l.add(g);
        }
        return SharedSuggestionProvider.suggest(l, b);
    };
    private static final SuggestionProvider<CommandSourceStack> FOLLOWS = (c, b) -> {
        List<String> l = new java.util.ArrayList<>(SpotlightFollow.RULES.stream().map(r -> r.equals(SpotlightFollow.ZONE) ? "@zone:scene" : r).toList());
        for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) l.add(p.getGameProfile().name());
        return SharedSuggestionProvider.suggest(l, b);
    };
    private static final SuggestionProvider<CommandSourceStack> COLORS = (c, b) -> SharedSuggestionProvider.suggest(Spotlights.COLOR_WORDS, b);

    /** Which spotlights a /cfsp order acts on: the one looked at (null), all, or a name / group. */
    private interface SpotTarget {
        String of(CommandContext<CommandSourceStack> c);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> spotlight(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal(name).requires(Permissions.ADMIN);
        spotlightOrders(root, c -> null);
        for (String word : List.of("all", "tout")) {
            LiteralArgumentBuilder<CommandSourceStack> all = literal(word);
            spotlightOrders(all, c -> Spotlights.ALL);
            root.then(all);
        }
        var target = argument("target", StringArgumentType.string()).suggests(SPOT_TARGETS);
        spotlightOrders(target, c -> StringArgumentType.getString(c, "target"));
        root.then(literal("groupe").then(target));
        // Names the spotlight looked at: "face1", "contre", "poursuite"... (groups: same start, /cfsp groupe face* …)
        root.then(literal("nom").then(argument("name", StringArgumentType.word()).executes(c -> {
            String n = StringArgumentType.getString(c, "name");
            return onSpotlight(c, s -> {
                s.setName(n);
                return "Projecteur nommé « " + s.name() + " ».";
            });
        })));
        root.then(literal("list").executes(StudioCommands::listSpotlights));
        root.then(literal("liste").executes(StudioCommands::listSpotlights));
        return root;
    }

    private static int listSpotlights(CommandContext<CommandSourceStack> c) {
        List<SpotlightBlockEntity> list = SpotlightBlockEntity.loaded();
        if (list.isEmpty()) ok(c, "Aucun projecteur chargé.");
        for (SpotlightBlockEntity s : list) {
            BlockPos p = s.getBlockPos();
            ok(c, "• " + (s.name().isEmpty() ? "(sans nom)" : s.name()) + " — " + p.getX() + " " + p.getY() + " " + p.getZ()
                    + (s.lit() ? ", allumé " + s.intensity() + " %" : ", éteint") + ", cône " + Math.round(s.cone()) + "°"
                    + (s.follow().isEmpty() ? "" : ", suit " + SpotlightFollow.describe(s.follow())
                    + (SpotlightFollow.isRule(s.follow()) ? " (" + (s.target().isEmpty() ? "personne" : s.target()) + ")" : "")));
        }
        return list.size();
    }

    /** Runs {@code set} on the target's spotlights (or the one looked at), reaching it in {@code seconds}. */
    private static int spot(CommandContext<CommandSourceStack> c, SpotTarget t, Spotlights.Settings set, double seconds) {
        String target = t.of(c);
        if (target != null) return done(c, Spotlights.apply(c.getSource().getServer(), target, set, seconds));
        return onSpotlight(c, s -> {
            Spotlights.apply(s, set, seconds);
            return "Projecteur" + (s.name().isEmpty() ? "" : " « " + s.name() + " »") + " : " + Spotlights.describe(set)
                    + (seconds > 0 ? " en " + Spotlights.seconds(seconds) : "") + ".";
        });
    }

    /** {@code then} + the same with a trailing fade in seconds. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withFade(T node, SpotTarget t,
            Function<CommandContext<CommandSourceStack>, Spotlights.Settings> set) {
        node.executes(c -> {
            Spotlights.Settings st = set.apply(c);
            return st == null ? 0 : spot(c, t, st, 0);
        });
        node.then(argument("seconds", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 60)).executes(c -> {
            Spotlights.Settings st = set.apply(c);
            return st == null ? 0 : spot(c, t, st, com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "seconds"));
        }));
        return node;
    }

    private static Spotlights.Settings settings(Boolean lit, Integer color, Integer intensity, Float cone, String follow, Vec3 at) {
        return new Spotlights.Settings(lit, color, intensity, cone, follow, at, null, null);
    }

    private static void spotlightOrders(ArgumentBuilder<CommandSourceStack, ?> node, SpotTarget t) {
        for (String word : List.of("follow", "suivre")) {
            node.then(literal(word).then(argument("player", StringArgumentType.greedyString()).suggests(FOLLOWS).executes(c -> {
                String who = StringArgumentType.getString(c, "player").strip();
                if (SpotlightFollow.isNobody(who)) who = "";
                String rule = who.isEmpty() ? null : SpotlightFollow.parse(who);
                if (rule == null && who.startsWith("@")) {
                    fail(c, "Suivi automatique inconnu : @proche, @candidat, @scene, @moment ou @zone:<nom>.");
                    return 0;
                }
                return spot(c, t, settings(null, null, null, null, rule != null ? rule : who, null), 0);
            })));
        }
        node.then(literal("stop").executes(c -> spot(c, t, settings(null, null, null, null, "", null), 0)));
        for (boolean lit : new boolean[]{true, false})
            for (String word : lit ? List.of("on", "allume") : List.of("off", "eteint"))
                node.then(withFade(literal(word), t, c -> settings(lit, null, null, null, null, null)));
        for (String word : List.of("color", "couleur"))
            node.then(literal(word).then(withFade(argument("color", StringArgumentType.word()).suggests(COLORS), t, c -> {
                Integer rgb = Spotlights.parseColor(StringArgumentType.getString(c, "color"));
                if (rgb == null) fail(c, "Couleur inconnue : " + String.join(", ", Spotlights.COLOR_WORDS) + ", ou #RRVVBB.");
                return rgb == null ? null : settings(null, rgb, null, null, null, null);
            })));
        for (String word : List.of("intensity", "intensite"))
            node.then(literal(word).then(withFade(argument("percent", integer(0, SpotlightBlockEntity.MAX_INTENSITY)), t,
                    c -> settings(null, null, getInteger(c, "percent"), null, null, null))));
        node.then(literal("cone").then(withFade(argument("degrees", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(
                        SpotlightBlockEntity.MIN_CONE, SpotlightBlockEntity.MAX_CONE)), t,
                c -> settings(null, null, null, com.mojang.brigadier.arguments.FloatArgumentType.getFloat(c, "degrees"), null, null))));
        // Aims at the player who types it (handy: stand where the light must go)
        for (String word : List.of("here", "ici"))
            node.then(withFade(literal(word), t, c -> settings(null, null, null, null, "", c.getSource().getPosition().add(0, 1, 0))));
    }

    private static int onSpotlight(CommandContext<CommandSourceStack> c, Function<SpotlightBlockEntity, String> action) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player != null) {
            BlockHitResult hit = lookedBlock(player);
            if (hit.getType() == HitResult.Type.BLOCK && player.level().getBlockEntity(hit.getBlockPos()) instanceof SpotlightBlockEntity s) {
                ok(c, action.apply(s));
                return 1;
            }
        }
        fail(c, "Regarde un projecteur (ou /cfsp tout …, /cfsp groupe <nom ou début de nom> …).");
        return 0;
    }

    // ---------------------------------------------------------------- desks and gong

    private static LiteralArgumentBuilder<CommandSourceStack> desk(String name) {
        return literal(name).requires(Permissions.ADMIN).then(literal("name").then(argument("name", StringArgumentType.greedyString())
                .suggests(PLAYERS).executes(c -> {
                    String text = StringArgumentType.getString(c, "name").trim();
                    boolean clear = text.equals("-");
                    ServerPlayer player = c.getSource().getPlayer();
                    if (player != null) {
                        BlockHitResult hit = lookedBlock(player);
                        if (hit.getType() == HitResult.Type.BLOCK && player.level().getBlockEntity(hit.getBlockPos()) instanceof DeskBlockEntity desk) {
                            desk.setName(clear ? "" : text);
                            ok(c, clear ? "Plaque effacée." : "Plaque : « " + desk.name() + " ».");
                            return 1;
                        }
                    }
                    fail(c, "Regarde un pupitre de candidat.");
                    return 0;
                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> gong(String name) {
        return literal(name).requires(Permissions.ADMIN).executes(c -> {
            ServerLevel level = c.getSource().getLevel();
            BlockPos at = BlockPos.containing(c.getSource().getPosition());
            Optional<BlockPos> gong = Optional.empty();
            // Nearest first
            for (BlockPos p : BlockPos./*$ within_clipped_manhattan {*/withinClippedManhattan/*$}*/(at, 32, 16, 32)) {
                if (level.getBlockState(p).getBlock() instanceof GongBlock) {
                    gong = Optional.of(p.immutable());
                    break;
                }
            }
            if (gong.isEmpty()) {
                fail(c, "Aucun gong à moins de 32 blocs.");
                return 0;
            }
            GongBlock.strike(level, gong.get(), c.getSource().getPlayer());
            ok(c, "Gong !");
            return 1;
        });
    }

    // ---------------------------------------------------------------- helpers

    private static int done(CommandContext<CommandSourceStack> c, String msg) {
        ok(c, msg);
        return 1;
    }

    private static void ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GREEN), true);
    }

    private static void fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
    }
}
