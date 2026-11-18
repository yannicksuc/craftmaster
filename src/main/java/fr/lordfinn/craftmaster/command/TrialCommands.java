package fr.lordfinn.craftmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.data.SeasonPlayer;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.trial.BoardEntity;
import fr.lordfinn.craftmaster.trial.Buzzers;
import fr.lordfinn.craftmaster.trial.BuzzerLock;
import fr.lordfinn.craftmaster.trial.Judging;
import fr.lordfinn.craftmaster.trial.ResultSheet;
import fr.lordfinn.craftmaster.trial.Stopwatch;
import fr.lordfinn.craftmaster.trial.TrialLog;
import fr.lordfinn.craftmaster.trial.TrialNetwork;
import fr.lordfinn.craftmaster.trial.TrialPropEntity;
import fr.lordfinn.craftmaster.trial.TrialZones;
import fr.lordfinn.craftmaster.trial.Zone;
import fr.lordfinn.craftmaster.trial.block.BigRedButtonBlock;
import fr.lordfinn.craftmaster.trial.item.MeasuringTapeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * Commands of the tasks (chantier H): results sheet, stopwatches, buzzers, task zones, trial log, drawing boards
 * and free naming of items. What changes the show's data runs through {@link ActionExecutor} (undoable, same as
 * the voice). Reference: docs/wiki/Epreuves.md and Commandes.md.
 */
public final class TrialCommands {

    private TrialCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        CraftMasterCommands.add(d, TrialCommands::result, "craftMasterResult", "cfre");
        CraftMasterCommands.add(d, TrialCommands::chrono, "craftMasterChrono", "cfch");
        CraftMasterCommands.add(d, TrialCommands::buzzer, "craftMasterBuzzer", "cfbz");
        CraftMasterCommands.add(d, TrialCommands::zone, "craftMasterZone", "cfzo");
        CraftMasterCommands.add(d, TrialCommands::trial, "craftMasterTrial", "cftr");
        CraftMasterCommands.add(d, TrialCommands::board, "craftMasterBoard", "cfbd");
        CraftMasterCommands.add(d, TrialCommands::name, "craftMasterName", "cfnm");
    }

    // ---------------------------------------------------------------- helpers

    @SafeVarargs
    private static int run(CommandContext<CommandSourceStack> c, Map<String, Object>... actions) {
        return ActionExecutor.execute(c.getSource(), List.of(actions));
    }

    private static String str(CommandContext<CommandSourceStack> c, String name) {
        return c.getArgument(name, String.class);
    }

    private static void info(CommandContext<CommandSourceStack> c, Component line) {
        c.getSource().sendSystemMessage(line);
    }

    private static void header(CommandContext<CommandSourceStack> c, String title) {
        info(c, Component.literal("— " + title + " —").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) fail(c, "Cette commande est réservée aux joueurs.");
        return p;
    }

    /** The entity of that class the player looks at (within 8 blocks), if any. */
    private static <T extends Entity> T lookedAt(ServerPlayer p, Class<T> type) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getViewVector(1f).scale(8));
        T best = null;
        double bestD = Double.MAX_VALUE;
        for (T e : p.level().getEntitiesOfClass(type, new AABB(eye, end).inflate(1))) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.05).clip(eye, end);
            if (hit.isPresent() && hit.get().distanceToSqr(eye) < bestD) {
                best = e;
                bestD = hit.get().distanceToSqr(eye);
            }
        }
        return best;
    }

    private static final SuggestionProvider<CommandSourceStack> CAST = list(c -> {
        List<String> out = new ArrayList<>(Judging.contestants());
        for (String n : c.getSource().getOnlinePlayerNames()) if (!out.contains(n)) out.add(n);
        return out;
    });
    private static final SuggestionProvider<CommandSourceStack> KINDS = list(c -> List.of("temps", "nombre", "distance", "note", "poids"));
    private static final SuggestionProvider<CommandSourceStack> ORDERS = list(c -> List.of("lower", "higher", "auto"));
    private static final SuggestionProvider<CommandSourceStack> SCALES = list(c -> List.of("5,4,3,2,1", "3,2,1", "10,8,6,4,2", "5,3,1"));
    private static final SuggestionProvider<CommandSourceStack> MANCHES = list(c -> {
        Season s = CraftMaster.data().activeSeason();
        return s == null || s.activePartie() == null ? List.of() : s.activePartie().activeManches().stream().map(m -> m.name).toList();
    });
    private static final SuggestionProvider<CommandSourceStack> ZONES = list(c -> TrialZones.zones().keySet());

    // ---------------------------------------------------------------- results sheet

    private static LiteralArgumentBuilder<CommandSourceStack> result(String name) {
        return literal(name).requires(Permissions.ADMIN)
                .executes(c -> openSheet(c, ""))
                .then(literal("open").executes(c -> openSheet(c, ""))
                        .then(argument("manche", word()).suggests(MANCHES).executes(c -> openSheet(c, str(c, "manche")))))
                .then(literal("set").then(argument("player", word()).suggests(CAST)
                        .then(argument("value", word())
                                .executes(c -> run(c, action("result.set", "player", str(c, "player"), "value", str(c, "value"))))
                                .then(argument("kind", word()).suggests(KINDS)
                                        .executes(c -> run(c, action("result.set", "player", str(c, "player"), "value", str(c, "value"),
                                                "kind", str(c, "kind"))))))))
                .then(literal("dq").then(argument("player", word()).suggests(CAST)
                        .executes(c -> run(c, action("result.dq", "player", str(c, "player"), "dq", true)))))
                .then(literal("undq").then(argument("player", word()).suggests(CAST)
                        .executes(c -> run(c, action("result.dq", "player", str(c, "player"), "dq", false)))))
                .then(literal("remove").then(argument("player", word()).suggests(CAST)
                        .executes(c -> run(c, action("result.remove", "player", str(c, "player"))))))
                .then(literal("clear").executes(c -> run(c, action("results.clear"))))
                .then(literal("kind").then(argument("kind", word()).suggests(KINDS)
                        .executes(c -> run(c, action("results.config", "kind", str(c, "kind"))))))
                .then(literal("order").then(argument("order", word()).suggests(ORDERS)
                        .executes(c -> run(c, action("results.config", "order", str(c, "order"))))))
                .then(literal("scale").then(argument("scale", StringArgumentType.greedyString()).suggests(SCALES)
                        .executes(c -> run(c, action("results.config", "scale", str(c, "scale"))))))
                .then(literal("apply").executes(c -> run(c, action("results.apply")))
                        .then(argument("scale", StringArgumentType.greedyString()).suggests(SCALES)
                                .executes(c -> run(c, action("results.apply", "scale", str(c, "scale"))))))
                .then(literal("show").executes(TrialCommands::showSheet));
    }

    private static int openSheet(CommandContext<CommandSourceStack> c, String manche) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        String id = manche;
        if (!manche.isEmpty()) {
            Season s = CraftMaster.data().activeSeason();
            Manche m = null;
            if (s != null && s.activePartie() != null)
                for (Manche x : s.activePartie().manches) if (x.matches(manche)) m = x;
            if (m == null) {
                fail(c, "Manche « " + manche + " » introuvable dans la partie active.");
                return 0;
            }
            id = m.id;
        }
        TrialNetwork.sendSheet(p, id, true);
        return 1;
    }

    /** The sheet of the current round in the chat: value, place, proposed points. */
    private static int showSheet(CommandContext<CommandSourceStack> c) {
        Manche m = CraftMaster.data().currentManche();
        Season s = CraftMaster.data().activeSeason();
        if (m == null || s == null) {
            fail(c, "Aucune manche en cours.");
            return 0;
        }
        ResultSheet r = m.results;
        header(c, "Résultats — " + m.name + " (" + r.kind.fr.toLowerCase() + ", " + (r.lowerWins() ? "le plus petit gagne" : "le plus grand gagne")
                + ", barème " + r.scaleOrDefault().replace(",", "-") + ")");
        List<String> cast = new ArrayList<>();
        for (SeasonPlayer p : s.players.values()) if (!p.archived) cast.add(p.name);
        for (ResultSheet.Placed p : r.rank(cast)) {
            String place = p.dq() ? "DQ" : p.place() == 0 ? "—" : "#" + p.place();
            String value = p.dq() ? "disqualifié" : p.value() == null ? "pas de résultat" : r.format(p.value());
            String pts = p.points() == null ? "" : "  → " + p.points() + " pt" + (Math.abs(p.points()) > 1 ? "s" : "");
            info(c, Component.literal(" " + place + "  ").withStyle(p.dq() ? ChatFormatting.RED : ChatFormatting.GOLD)
                    .append(Component.literal(p.player() + " : " + value).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(pts).withStyle(ChatFormatting.GREEN)));
        }
        return 1;
    }

    // ---------------------------------------------------------------- stopwatches

    private static LiteralArgumentBuilder<CommandSourceStack> chrono(String name) {
        return literal(name).requires(Permissions.ADMIN)
                .then(literal("start").then(argument("player", word()).suggests(CAST)
                        .executes(c -> run(c, action("stopwatch.start", "player", str(c, "player"))))))
                .then(literal("stop").then(argument("player", word()).suggests(CAST)
                        .executes(c -> run(c, action("stopwatch.stop", "player", str(c, "player"))))
                        .then(argument("record", BoolArgumentType.bool())
                                .executes(c -> run(c, action("stopwatch.stop", "player", str(c, "player"),
                                        "record", BoolArgumentType.getBool(c, "record")))))))
                .then(literal("reset").executes(c -> run(c, action("stopwatch.reset")))
                        .then(argument("player", word()).suggests(CAST)
                                .executes(c -> run(c, action("stopwatch.reset", "player", str(c, "player"))))))
                .then(literal("list").executes(c -> {
                    header(c, "Chronos");
                    long now = System.currentTimeMillis();
                    boolean any = false;
                    List<String> names = new ArrayList<>(Judging.contestants());
                    for (String p : Stopwatch.SHARED.runningPlayers()) if (!names.contains(p)) names.add(p);
                    for (String p : names) {
                        long t = Stopwatch.SHARED.peek(p, now), last = Stopwatch.SHARED.last(p);
                        if (t < 0 && last < 0) continue;
                        any = true;
                        info(c, Component.literal(" " + p + " : ").withStyle(ChatFormatting.WHITE)
                                .append(Component.literal(t >= 0 ? Stopwatch.format(t) + " (en cours)" : Stopwatch.format(last))
                                        .withStyle(t >= 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY)));
                    }
                    if (!any) info(c, Component.literal(" Aucun chrono.").withStyle(ChatFormatting.GRAY));
                    return 1;
                }));
    }

    // ---------------------------------------------------------------- buzzers

    private static LiteralArgumentBuilder<CommandSourceStack> buzzer(String name) {
        return literal(name).requires(Permissions.ADMIN)
                .then(literal("reset").executes(c -> run(c, action("buzzer.reset"))))
                .then(literal("mode").executes(TrialCommands::buzzerModes)
                        .then(argument("mode", word()).suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(Buzzers.Mode.values()).map(m -> m.id), b)).executes(c -> {
                            Buzzers.Mode m = Buzzers.Mode.byId(str(c, "mode"));
                            fr.lordfinn.craftmaster.CraftMaster.data().change("Mode des buzzers : " + m.label,
                                    () -> fr.lordfinn.craftmaster.CraftMaster.data().stage().buzzerMode = m.id);
                            Buzzers.reset(c.getSource().getServer());
                            return buzzerModes(c);
                        })))
                .then(literal("rearm").then(argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 300)).executes(c -> {
                    int sec = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "seconds");
                    fr.lordfinn.craftmaster.CraftMaster.data().change("Réarmement des buzzers : " + sec + " s",
                            () -> fr.lordfinn.craftmaster.CraftMaster.data().stage().buzzerRearm = sec);
                    return buzzerModes(c);
                })))
                .then(literal("status").executes(c -> {
                    header(c, "Buzzers");
                    List<BuzzerLock.Press> presses = Buzzers.LOCK.presses();
                    if (presses.isEmpty()) info(c, Component.literal(" Personne n'a buzzé.").withStyle(ChatFormatting.GRAY));
                    for (BuzzerLock.Press p : presses)
                        info(c, Component.literal(" " + p.order() + ". " + p.player() + (p.first() ? "" : "  +" + p.delayMs() + " ms"))
                                .withStyle(p.first() ? ChatFormatting.GOLD : ChatFormatting.WHITE));
                    return 1;
                }));
    }

    /** The organisers' buzzer menu in the chat: the current mode, each mode as a button, the re-arm delay. */
    private static int buzzerModes(CommandContext<CommandSourceStack> c) {
        header(c, "Buzzers");
        Buzzers.Mode current = Buzzers.mode();
        for (Buzzers.Mode m : Buzzers.Mode.values()) {
            boolean on = m == current;
            info(c, Component.literal(on ? " ● " : " ○ ").withStyle(on ? ChatFormatting.GOLD : ChatFormatting.GRAY)
                    .append(Component.literal("[" + m.label + "]").withStyle(st -> st.withColor(on ? ChatFormatting.GOLD : ChatFormatting.YELLOW)
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/cm epreuve buzzer mode " + m.id))
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(m.help)))))
                    .append(Component.literal("  " + m.help).withStyle(ChatFormatting.GRAY)));
        }
        info(c, Component.literal(" Réarmement automatique : " + Buzzers.rearmSeconds() + " s ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("[changer]").withStyle(st -> st.withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent.SuggestCommand("/cm epreuve buzzer rearmer ")))));
        info(c, Component.literal(" Remise à zéro : ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("[maintenant]").withStyle(st -> st.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/cm epreuve buzzer reset"))))
                .append(Component.literal(", la voix, accroupi + clic sur un buzzer, ou un signal redstone dans un buzzer").withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    // ---------------------------------------------------------------- zones

    private static LiteralArgumentBuilder<CommandSourceStack> zone(String name) {
        return literal(name).requires(Permissions.ADMIN)
                .then(literal("create").then(argument("name", word())
                        .executes(c -> createZoneFromTape(c, str(c, "name")))
                        .then(argument("from", BlockPosArgument.blockPos()).then(argument("to", BlockPosArgument.blockPos())
                                .executes(c -> createZone(c, str(c, "name"), BlockPosArgument.getBlockPos(c, "from"),
                                        BlockPosArgument.getBlockPos(c, "to")))))))
                .then(literal("remove").then(argument("name", word()).suggests(ZONES).executes(c -> {
                    String n = Zone.normalizeName(str(c, "name"));
                    if (!TrialZones.remove(n)) {
                        fail(c, "Zone « " + n + " » inconnue.");
                        return 0;
                    }
                    ok(c, "Zone « " + n + " » supprimée.");
                    return 1;
                })))
                .then(literal("list").executes(c -> {
                    header(c, "Zones d'épreuve");
                    if (TrialZones.zones().isEmpty()) info(c, Component.literal(" Aucune zone.").withStyle(ChatFormatting.GRAY));
                    for (Zone z : TrialZones.zones().values())
                        info(c, Component.literal(" " + z.name() + " : ").withStyle(ChatFormatting.GOLD)
                                .append(Component.literal(z.describe()).withStyle(ChatFormatting.WHITE)));
                    return 1;
                }))
                .then(literal("show").then(argument("name", word()).suggests(ZONES).executes(c -> {
                    String n = Zone.normalizeName(str(c, "name"));
                    if (!TrialZones.zones().containsKey(n)) {
                        fail(c, "Zone « " + n + " » inconnue.");
                        return 0;
                    }
                    TrialZones.show(c.getSource().getServer(), n, 15);
                    ok(c, "Contour de « " + n + " » affiché 15 secondes.");
                    return 1;
                })))
                .then(literal("players").then(argument("name", word()).suggests(ZONES)
                        .then(argument("players", StringArgumentType.greedyString()).suggests(CAST).executes(c -> {
                            String n = Zone.normalizeName(str(c, "name"));
                            Zone z = TrialZones.zones().get(n);
                            if (z == null) {
                                fail(c, "Zone « " + n + " » inconnue.");
                                return 0;
                            }
                            String raw = str(c, "players").trim();
                            List<String> players = raw.equalsIgnoreCase("all") || raw.equalsIgnoreCase("tous") ? List.of()
                                    : Arrays.stream(raw.split("[,\\s]+")).filter(s -> !s.isBlank()).toList();
                            TrialZones.put(z.withPlayers(players));
                            ok(c, "Zone « " + n + " » : " + (players.isEmpty() ? "tous les candidats" : String.join(", ", players)) + ".");
                            return 1;
                        }))))
                .then(literal("enable").then(argument("name", word()).suggests(ZONES)
                        .then(argument("enabled", BoolArgumentType.bool()).executes(c -> {
                            String n = Zone.normalizeName(str(c, "name"));
                            Zone z = TrialZones.zones().get(n);
                            if (z == null) {
                                fail(c, "Zone « " + n + " » inconnue.");
                                return 0;
                            }
                            boolean on = BoolArgumentType.getBool(c, "enabled");
                            TrialZones.put(z.withEnabled(on));
                            ok(c, "Zone « " + n + " » " + (on ? "activée" : "désactivée") + ".");
                            return 1;
                        }))));
    }

    private static int createZoneFromTape(CommandContext<CommandSourceStack> c, String name) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        BlockPos[] pts = MeasuringTapeItem.lastPoints(p.getUUID());
        if (pts == null) {
            fail(c, "Mesure d'abord les deux coins opposés avec le mètre ruban, ou donne-les : /cm epreuve zone creer <nom> <de> <à>.");
            return 0;
        }
        return createZone(c, name, pts[0], pts[1]);
    }

    private static int createZone(CommandContext<CommandSourceStack> c, String rawName, BlockPos from, BlockPos to) {
        String n = Zone.normalizeName(rawName);
        String world = c.getSource().getLevel().dimension().identifier().toString();
        Zone old = TrialZones.zones().get(n);
        Zone z = new Zone(n, world, from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(),
                old == null ? List.of() : old.players(), true);
        TrialZones.put(z);
        TrialZones.show(c.getSource().getServer(), n, 10);
        ok(c, "Zone « " + n + " » " + (old == null ? "créée" : "redéfinie") + " : " + z.describe() + ".");
        return 1;
    }

    // ---------------------------------------------------------------- trial log, props

    private static LiteralArgumentBuilder<CommandSourceStack> trial(String name) {
        return literal(name).requires(Permissions.ADMIN)
                .then(literal("log").executes(c -> {
                    header(c, "Journal des épreuves");
                    List<String> lines = TrialLog.latest(15);
                    if (lines.isEmpty()) info(c, Component.literal(" Rien pour l'instant.").withStyle(ChatFormatting.GRAY));
                    for (int i = lines.size() - 1; i >= 0; i--) info(c, Component.literal(" " + lines.get(i)).withStyle(ChatFormatting.WHITE));
                    return 1;
                }))
                .then(literal("reset").executes(c -> {
                    BigRedButtonBlock.resetFirsts();
                    TrialLog.clear();
                    run(c, action("buzzer.reset"));
                    ok(c, "Épreuve remise à zéro : buzzers, « premier » des gros boutons, journal.");
                    return 1;
                }))
                .then(literal("prop").executes(c -> {
                    ServerPlayer p = player(c);
                    if (p == null) return 0;
                    TrialPropEntity prop = lookedAt(p, TrialPropEntity.class);
                    if (prop == null) {
                        fail(c, "Regarde un objet d'épreuve (à moins de 8 blocs).");
                        return 0;
                    }
                    TrialNetwork.openProp(p, prop);
                    return 1;
                }))
                // ---- trial kit (docs/wiki/Arenes.md): generic pieces of the kit's trials
                .then(literal("motdepasse")
                        .then(literal("initiale").then(argument("valeur", word()).executes(c -> lockedPickup(c, "initiale:" + str(c, "valeur"), null))
                                .then(argument("nom", StringArgumentType.greedyString()).executes(c -> lockedPickup(c, "initiale:" + str(c, "valeur"), str(c, "nom"))))))
                        .then(literal("mot").then(argument("valeur", word()).executes(c -> lockedPickup(c, "mot:" + str(c, "valeur"), null))
                                .then(argument("nom", StringArgumentType.greedyString()).executes(c -> lockedPickup(c, "mot:" + str(c, "valeur"), str(c, "nom"))))))
                        .then(literal("libre").executes(c -> lockedPickup(c, "libre", null))))
                .then(literal("vase").then(argument("objectif", integer(1, 200)).executes(c -> {
                    ServerPlayer p = player(c);
                    if (p == null) return 0;
                    if (!(p.pick(8, 1f, false) instanceof net.minecraft.world.phys.BlockHitResult bh)
                            || p.level().getBlockState(bh.getBlockPos()).isAir()) {
                        fail(c, "Regarde le pot (ou le bloc) qui sert de vase.");
                        return 0;
                    }
                    BlockPos at = bh.getBlockPos();
                    fr.lordfinn.craftmaster.trial.kit.Pickups.placeVase((net.minecraft.server.level.ServerLevel) p.level(),
                            new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5), getInteger(c, "objectif"));
                    ok(c, "Vase posé : il montre chaque objet pris avec un mot de passe, jusqu'à " + getInteger(c, "objectif") + " (plein = fin de l'essai).");
                    return 1;
                })))
                .then(literal("marquer").then(argument("but", word()).executes(c -> {
                    ServerPlayer p = player(c);
                    if (p == null) return 0;
                    ItemStack st = p.getMainHandItem();
                    if (st.isEmpty()) {
                        fail(c, "Prends en main l'objet à marquer (la pomme).");
                        return 0;
                    }
                    fr.lordfinn.craftmaster.trial.kit.Finish.mark(st, str(c, "but"));
                    ok(c, "« " + st.getHoverName().getString() + " » marqué « " + str(c, "but") + " » : le manger termine l'essai du joueur (chrono noté).");
                    return 1;
                })))
                .then(literal("panneau").then(argument("texte", StringArgumentType.greedyString())
                        .executes(c -> ArenaCommands.writeSign(c, str(c, "texte")))))
                .then(literal("vocal")
                        .then(literal("commun").executes(c -> {
                            if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voicechat")) {
                                fail(c, "Simple Voice Chat n'est pas sur ce serveur.");
                                return 0;
                            }
                            int n = fr.lordfinn.craftmaster.compat.VoiceGroups.gather(c.getSource().getServer(),
                                    pl -> Judging.contestant(pl) != null || Permissions.isAdmin(pl));
                            if (n < 0) fail(c, "Le chat vocal n'est pas prêt.");
                            else ok(c, "Canal vocal commun : " + n + " joueur(s) (candidats et organisateurs). Fermé par /cm epreuve chrono ou /cm epreuve vocal fin.");
                            return n < 0 ? 0 : 1;
                        }))
                        .then(literal("fin").executes(c -> {
                            boolean was = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voicechat")
                                    && fr.lordfinn.craftmaster.compat.VoiceGroups.disband(c.getSource().getServer());
                            ok(c, was ? "Canal vocal commun fermé : chacun revient au vocal de proximité." : "Pas de canal vocal commun ouvert.");
                            return 1;
                        })))
                .then(literal("chrono").then(argument("secondes", integer(1, 7200)).executes(c -> {
                    Manche m = Judging.currentManche();
                    String label = m != null ? m.name : "Chrono";
                    fr.lordfinn.craftmaster.trial.kit.Arenas.startTimer(c.getSource().getServer(), label, getInteger(c, "secondes"));
                    ok(c, "Chrono de « " + label + " » lancé : " + getInteger(c, "secondes") + " s (canal vocal commun fermé s'il était ouvert).");
                    return 1;
                })));
    }

    /** A password-locked pickup lying on the block looked at, showing the item held. */
    private static int lockedPickup(CommandContext<CommandSourceStack> c, String rule, String label) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        ItemStack held = p.getMainHandItem();
        if (held.isEmpty()) {
            fail(c, "Prends en main l'objet à poser (une fleur...).");
            return 0;
        }
        if (!(p.pick(8, 1f, false) instanceof net.minecraft.world.phys.BlockHitResult bh) || p.level().getBlockState(bh.getBlockPos()).isAir()) {
            fail(c, "Regarde le sol où le poser.");
            return 0;
        }
        BlockPos top = bh.getBlockPos();
        String name = label != null ? label : held.getHoverName().getString();
        fr.lordfinn.craftmaster.trial.kit.Pickups.placePickup((net.minecraft.server.level.ServerLevel) p.level(),
                new Vec3(bh.getLocation().x, top.getY() + 1, bh.getLocation().z), held, rule, name, p.getYRot(), null);
        ok(c, "« " + name + " » posé, verrouillé (" + rule + ").");
        return 1;
    }

    // ---------------------------------------------------------------- boards

    private static BoardEntity board(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return null;
        BoardEntity b = lookedAt(p, BoardEntity.class);
        if (b == null) fail(c, "Regarde un tableau (toile, ardoise, tableau blanc) à moins de 8 blocs.");
        return b;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> board(String name) {
        return literal(name).requires(Permissions.ADMIN)
                .then(literal("size").then(argument("width", integer(1, BoardEntity.MAX_WIDTH))
                        .then(argument("height", integer(1, BoardEntity.MAX_HEIGHT)).executes(c -> {
                            BoardEntity b = board(c);
                            if (b == null) return 0;
                            int w = getInteger(c, "width"), h = getInteger(c, "height");
                            // Grows to the right and up from the bottom-left corner, like when it was hung
                            Vec3 corner = b.position().add(-b.right().getStepX() * b.boardWidth() / 2.0, -b.boardHeight() / 2.0,
                                    -b.right().getStepZ() * b.boardWidth() / 2.0);
                            b.resize(w, h);
                            Vec3 center = corner.add(b.right().getStepX() * w / 2.0, h / 2.0, b.right().getStepZ() * w / 2.0);
                            b.snapTo(center.x, center.y, center.z, b.getYRot(), 0);
                            ok(c, "Tableau : " + w + "×" + h + " blocs.");
                            return 1;
                        }))))
                .then(literal("clear").executes(c -> {
                    BoardEntity b = board(c);
                    if (b == null) return 0;
                    b.clearDrawing();
                    b.setText("");
                    ok(c, "Tableau effacé.");
                    return 1;
                }))
                .then(literal("lock").then(argument("locked", BoolArgumentType.bool()).executes(c -> {
                    BoardEntity b = board(c);
                    if (b == null) return 0;
                    b.setLocked(BoolArgumentType.getBool(c, "locked"));
                    ok(c, b.locked() ? "Tableau verrouillé : seuls les organisateurs peuvent y dessiner ou écrire." : "Tableau déverrouillé.");
                    return 1;
                })))
                .then(literal("text").then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                    BoardEntity b = board(c);
                    if (b == null) return 0;
                    b.setText(str(c, "text").replace("\\n", "\n"));
                    ok(c, "Texte écrit sur le tableau.");
                    return 1;
                })));
    }

    // ---------------------------------------------------------------- free naming

    /** Anyone: names the item held, "&" colour codes allowed (&6 gold, &l bold...), no anvil and no XP. */
    private static LiteralArgumentBuilder<CommandSourceStack> name(String name) {
        return literal(name)
                .then(argument("name", StringArgumentType.greedyString()).executes(c -> {
                    ServerPlayer p = player(c);
                    if (p == null) return 0;
                    ItemStack held = p.getMainHandItem();
                    if (held.isEmpty()) {
                        fail(c, "Prends en main l'objet à nommer.");
                        return 0;
                    }
                    String text = str(c, "name");
                    if (text.length() > 64) text = text.substring(0, 64);
                    held.set(DataComponents.CUSTOM_NAME, colored(text));
                    ok(c, "Objet nommé.");
                    return 1;
                }))
                // Without a name: back to the item's own name
                .executes(c -> {
                    ServerPlayer p = player(c);
                    if (p == null) return 0;
                    p.getMainHandItem().remove(DataComponents.CUSTOM_NAME);
                    ok(c, "Nom retiré.");
                    return 1;
                });
    }

    /** "&6Le &lTrésor" → gold "Le " + gold bold "Trésor", not italic. */
    static Component colored(String text) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        net.minecraft.network.chat.Style style = net.minecraft.network.chat.Style.EMPTY.withItalic(false);
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            ChatFormatting f = ch == '&' && i + 1 < text.length() ? ChatFormatting.getByCode(text.charAt(i + 1)) : null;
            if (f == null) {
                run.append(ch);
                continue;
            }
            if (!run.isEmpty()) out.append(Component.literal(run.toString()).withStyle(style));
            run.setLength(0);
            style = f == ChatFormatting.RESET ? net.minecraft.network.chat.Style.EMPTY.withItalic(false) : style.applyFormat(f);
            i++;
        }
        if (!run.isEmpty()) out.append(Component.literal(run.toString()).withStyle(style));
        return out;
    }
}
