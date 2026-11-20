package fr.lordfinn.craftmaster.trial;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Who sees the stopwatches' bars at the top of the screen ({@link Judging} draws them):
 * <ul>
 *   <li>a contestant: « auto » (their own bar, none when a tablet in their room shows their time), « soi » (their own)
 *   or « aucun »;</li>
 *   <li>the others (organisers, spectators): « compact » (every time on one line), « tous » (one bar each) or « aucun »;</li>
 *   <li>each player may choose for themself (« moi »), over the rule of their group.</li>
 * </ul>
 * The broadcast games keep every bar (their picture has its own dressing). {@code /cm chronos barres}, the N menu.
 */
public final class ChronoBars {

    private ChronoBars() {}

    public static final List<String> CONTESTANT_MODES = List.of("auto", "soi", "aucun");
    public static final List<String> OTHER_MODES = List.of("compact", "tous", "aucun");
    public static final List<String> OWN_MODES = List.of("soi", "compact", "tous", "aucun", "defaut");

    static final class Settings {
        String candidats = "auto";
        String autres = "compact";
        /** Player name (lower case) → their own choice. */
        Map<String, String> joueurs = new LinkedHashMap<>();
    }

    private static Settings settings = new Settings();
    private static boolean loaded;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("craftmaster/chronos.json");
    }

    private static Settings s() {
        if (!loaded) {
            loaded = true;
            try {
                if (Files.exists(file())) settings = GSON.fromJson(Files.readString(file()), Settings.class);
            } catch (Exception e) {
                CraftMaster.LOG.warn("[Chronos] Réglages illisibles : {}", e.getMessage());
            }
            if (settings == null) settings = new Settings();
            if (settings.joueurs == null) settings.joueurs = new LinkedHashMap<>();
            if (!CONTESTANT_MODES.contains(settings.candidats)) settings.candidats = "auto";
            if (!OTHER_MODES.contains(settings.autres)) settings.autres = "compact";
        }
        return settings;
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(settings));
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Chronos] Réglages non enregistrés : {}", e.getMessage());
        }
    }

    /**
     * What this player sees: "soi" (their own bar), "compact", "tous" or "aucun".
     *
     * @param withTablet the contestants whose room has a tablet showing their time (lower case)
     */
    static String mode(ServerPlayer p, Set<String> withTablet) {
        if (fr.lordfinn.craftmaster.broadcast.Director.broadcasters().contains(p.getUUID())) return "tous";
        String own = s().joueurs.get(p.getGameProfile().name().toLowerCase(Locale.ROOT));
        if (own != null) return own;
        String c = Judging.contestant(p);
        if (c != null) {
            return switch (s().candidats) {
                case "soi" -> "soi";
                case "aucun" -> "aucun";
                default -> withTablet.contains(c.toLowerCase(Locale.ROOT)) ? "aucun" : "soi";
            };
        }
        return s().autres;
    }

    /** The contestants whose arena copy holds a tablet following their stopwatch (lower case). */
    static Set<String> withTablet() {
        Set<String> out = new LinkedHashSet<>();
        for (fr.lordfinn.craftmaster.studio.ClockBlockEntity t : fr.lordfinn.craftmaster.studio.ClockBlockEntity.loaded()) {
            if (t.getLevel() == null || t.getLevel().isClientSide() || !t.settings().followContestant) continue;
            fr.lordfinn.craftmaster.trial.kit.Arenas.Owner o = fr.lordfinn.craftmaster.trial.kit.Arenas.ownerAt(t.getLevel(), t.getBlockPos());
            if (o != null && o.player() != null) out.add(o.player().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    public static String describe() {
        return "Barres de chrono — candidats : " + s().candidats + " · les autres : " + s().autres
                + (s().joueurs.isEmpty() ? "" : " · choix perso : " + s().joueurs);
    }

    /** For the N menu. */
    public static com.google.gson.JsonObject state(ServerPlayer p) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("candidats", s().candidats);
        o.addProperty("autres", s().autres);
        String own = s().joueurs.get(p.getGameProfile().name().toLowerCase(Locale.ROOT));
        o.addProperty("moi", own == null ? "defaut" : own);
        return o;
    }

    // ---------------------------------------------------------------- command

    /** {@code /cm chronos barres [candidats <auto|soi|aucun> | autres <compact|tous|aucun> | moi <…>]}. */
    public static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        return literal(name).then(literal("barres")
                .executes(c -> say(c, describe()))
                .then(literal("candidats").requires(Permissions.ADMIN).then(argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> {
                            b.suggest("auto", Component.literal("Son chrono, rien s'il a la tablette dans sa salle"));
                            b.suggest("soi", Component.literal("Son propre chrono seulement"));
                            b.suggest("aucun", Component.literal("Aucune barre"));
                            return b.buildFuture();
                        })
                        .executes(c -> set(c, "candidats", StringArgumentType.getString(c, "mode")))))
                .then(literal("autres").requires(Permissions.ADMIN).then(argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> {
                            b.suggest("compact", Component.literal("Tous les chronos sur une seule ligne"));
                            b.suggest("tous", Component.literal("Une barre par candidat"));
                            b.suggest("aucun", Component.literal("Masqués"));
                            return b.buildFuture();
                        })
                        .executes(c -> set(c, "autres", StringArgumentType.getString(c, "mode")))))
                .then(literal("moi").then(argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> {
                            b.suggest("soi", Component.literal("Ton propre chrono"));
                            b.suggest("compact", Component.literal("Tous sur une ligne"));
                            b.suggest("tous", Component.literal("Une barre par candidat"));
                            b.suggest("aucun", Component.literal("Rien en haut de l'écran"));
                            b.suggest("defaut", Component.literal("La règle de ton groupe (candidats ou les autres)"));
                            return b.buildFuture();
                        })
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            String m = StringArgumentType.getString(c, "mode").toLowerCase(Locale.ROOT);
                            if (!OWN_MODES.contains(m)) return fail(c, "« " + m + " » : " + String.join(", ", OWN_MODES) + ".");
                            String k = p.getGameProfile().name().toLowerCase(Locale.ROOT);
                            if (m.equals("defaut")) s().joueurs.remove(k);
                            else s().joueurs.put(k, m);
                            save();
                            Judging.refreshViewers(c.getSource().getServer());
                            return say(c, "Tes barres de chrono : " + (m.equals("defaut") ? "la règle de ton groupe" : m) + ".");
                        }))));
    }

    private static int set(CommandContext<CommandSourceStack> c, String who, String mode) {
        String m = mode.toLowerCase(Locale.ROOT);
        List<String> ok = who.equals("candidats") ? CONTESTANT_MODES : OTHER_MODES;
        if (!ok.contains(m)) return fail(c, "« " + m + " » : " + String.join(", ", ok) + ".");
        if (who.equals("candidats")) s().candidats = m;
        else s().autres = m;
        save();
        Judging.refreshViewers(c.getSource().getServer());
        return say(c, describe());
    }

    private static int say(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendFailure(Component.literal(text));
        return 0;
    }
}
