package fr.lordfinn.craftmaster.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import fr.lordfinn.craftmaster.camera.Lieux;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.MancheIntro;
import fr.lordfinn.craftmaster.fx.FocusPull;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /cm tv intro <m> plan <n> focus …}: the focus pull of an intro shot ({@link FocusPull}). Without words, the
 * focus panel (the editor's « [focus] »): aim at the start and click, aim at the end and click, presets, duration,
 * blur, easing, keep, try the shot.
 */
final class FocusCommands {

    private FocusCommands() {}

    private static final double REACH = 64;

    /** The {@code focus} branch of {@code plan <index>}. */
    static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return literal("focus").executes(FocusCommands::panel)
                .then(argument("focus", StringArgumentType.greedyString()).suggests(SUGGEST).executes(FocusCommands::set));
    }

    // ---------------------------------------------------------------- set

    private static int set(CommandContext<CommandSourceStack> c) {
        Manche m = Lieux.manche(c.getArgument("manche", String.class));
        int index = c.getArgument("index", Integer.class);
        if (m == null || index < 1 || index > m.intro.shots.size()) {
            c.getSource().sendFailure(Component.literal(m == null ? "Manche introuvable." : "Plan " + index + " inexistant."));
            return 0;
        }
        String words = c.getArgument("focus", String.class).trim();
        String canonical;
        if (words.equalsIgnoreCase("aucun") || words.equalsIgnoreCase("preset") || words.equalsIgnoreCase("defaut")) canonical = "";
        else {
            MancheIntro.Shot shot = m.intro.shots.get(index - 1);
            FocusPull base = shot.focusPull();
            if (base == null) base = FocusPull.preset(presetOf(shot.effect()));
            try {
                canonical = FocusPull.parseWords(words, base, m.intro.beatSeconds(), picker(c.getSource().getPlayer())).canonical();
            } catch (IllegalArgumentException e) {
                c.getSource().sendFailure(Component.literal("✗ Focus : " + e.getMessage()));
                return 0;
            }
        }
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("type", "intro.shot.set");
        a.put("manche", m.id);
        a.put("index", index);
        a.put("focus", canonical);
        int r = ActionExecutor.execute(c.getSource(), List.of(a));
        if (r > 0) panel(c);
        return r;
    }

    /** The depth of field preset of a shot's effect (the base a new focus starts from). */
    private static String presetOf(String effect) {
        for (String id : fr.lordfinn.craftmaster.fx.ScreenFx.parseLenient(effect))
            if (FocusPull.DOF_EFFECTS.contains(id)) return id;
        return "mise_au_point";
    }

    /** « ici », « entite » and players' names, from the organiser's eyes. */
    private static FocusPull.Picker picker(ServerPlayer p) {
        return new FocusPull.Picker() {
            @Override
            public String point() {
                if (p == null) return null;
                HitResult hit = p.pick(REACH, 1f, false);
                if (hit.getType() == HitResult.Type.MISS) return null;
                Vec3 v = hit.getLocation();
                return String.format(Locale.ROOT, "p:%.1f,%.1f,%.1f", v.x, v.y, v.z);
            }

            @Override
            public String entity() {
                if (p == null) return null;
                Vec3 eye = p.getEyePosition(), end = eye.add(p.getViewVector(1f).scale(REACH));
                Entity best = null;
                double bestD = Double.MAX_VALUE;
                for (Entity e : p.level().getEntities(p, new AABB(eye, end).inflate(1), e -> !e.isSpectator())) {
                    Optional<Vec3> at = e.getBoundingBox().inflate(0.3).clip(eye, end);
                    if (at.isPresent() && at.get().distanceToSqr(eye) < bestD) {
                        bestD = at.get().distanceToSqr(eye);
                        best = e;
                    }
                }
                if (best == null) return null;
                return best instanceof ServerPlayer sp ? "j:" + sp.getGameProfile().name() : "e:" + best.getUUID();
            }

            @Override
            public String player(String name) {
                if (p == null) return null;
                ServerPlayer o = p.level().getServer().getPlayerList().getPlayerByName(name);
                return o == null ? null : "j:" + o.getGameProfile().name();
            }
        };
    }

    // ---------------------------------------------------------------- Tab

    private static final List<String> KEYS = List.of("de", "vers", "duree", "delai", "flou", "courbe", "garder");
    private static final List<String> TARGETS = List.of("centre", "fond", "ici", "entite", "4", "8", "16", "32");

    private static final SuggestionProvider<CommandSourceStack> SUGGEST = (c, b) -> {
        String typed = b.getRemaining();
        int cut = typed.lastIndexOf(' ') + 1;
        String last = typed.substring(cut).toLowerCase(Locale.ROOT);
        String[] words = typed.substring(0, cut).trim().toLowerCase(Locale.ROOT).split("\\s+");
        String prev = words.length == 0 ? "" : words[words.length - 1];
        SuggestionsBuilder at = b.createOffset(b.getStart() + cut);
        List<String> options;
        Map<String, String> tips = new LinkedHashMap<>();
        switch (prev) {
            case "de", "vers" -> {
                options = new java.util.ArrayList<>(TARGETS);
                tips.put("centre", "Ce qui est au centre de l'image");
                tips.put("fond", "L'arrière-plan, derrière le sujet");
                tips.put("ici", "Le point que tu regardes maintenant");
                tips.put("entite", "L'entité que tu regardes (suivie pendant le plan)");
                for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) options.add(p.getGameProfile().name());
            }
            case "duree", "delai" -> {
                options = List.of("0.4", "0.8", "1.2", "2", "4", "1t", "2t", "4t");
                tips.put("2t", "2 temps de la musique de l'intro");
            }
            case "flou" -> {
                options = List.of("50", "100", "150", "200", "300");
                tips.put("100", "Normal (celui des préréglages)");
            }
            case "courbe" -> {
                options = FocusPull.EASINGS;
                tips.put("lisse", "Démarre et arrive doucement");
                tips.put("entree", "Démarre doucement, arrive franc");
                tips.put("sortie", "Démarre franc, arrive doucement");
            }
            case "garder" -> {
                options = List.of("oui", "non");
                tips.put("non", "Une fois arrivée, le flou s'efface : tout devient net");
            }
            default -> {
                options = new java.util.ArrayList<>(KEYS);
                if (typed.isBlank()) options.add("aucun");
                tips.put("de", "Point de départ de la mise au point");
                tips.put("vers", "Point d'arrivée");
                tips.put("duree", "Durée du glissement (s, ou 2t en temps)");
                tips.put("delai", "Attente avant le glissement");
                tips.put("flou", "Force du flou hors de la mise au point (%)");
                tips.put("aucun", "Revenir au réglage de l'effet");
            }
        }
        for (String o : options)
            if (o.toLowerCase(Locale.ROOT).startsWith(last)) {
                String tip = tips.get(o);
                if (tip == null) at.suggest(o);
                else at.suggest(o, Component.literal(tip));
            }
        return at.buildFuture();
    };

    // ---------------------------------------------------------------- the panel

    private static Component button(String label, String command, boolean suggest, String hover, ChatFormatting color) {
        ClickEvent click = suggest ? new ClickEvent.SuggestCommand(command) : new ClickEvent.RunCommand(command);
        return Component.literal("[" + label + "]").withStyle(st -> st.withColor(color).withClickEvent(click)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover + "\n" + command)))).append(" ");
    }

    static int panel(CommandContext<CommandSourceStack> c) {
        Manche m = Lieux.manche(c.getArgument("manche", String.class));
        int index = c.getArgument("index", Integer.class);
        if (m == null || index < 1 || index > m.intro.shots.size()) return 0;
        MancheIntro.Shot shot = m.intro.shots.get(index - 1);
        FocusPull f = shot.focusPull();
        String base = "/cm tv intro " + m.id + " plan " + index + " focus ";
        c.getSource().sendSystemMessage(Component.literal("— Focus du plan " + index + " — ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(f == null ? "réglage de l'effet (" + presetOf(shot.effect()) + ")" : f.describe()).withStyle(ChatFormatting.GRAY)));
        c.getSource().sendSystemMessage(Component.literal("  ")
                .append(button("① vise le départ, clique", base + "de ici", false,
                        "Regarde le point où la mise au point commence (la fleur à gauche…), puis clique", ChatFormatting.AQUA))
                .append(button("② vise l'arrivée, clique", base + "vers ici", false,
                        "Regarde le point où elle arrive (la salle…), puis clique", ChatFormatting.AQUA))
                .append(button("entité visée", base + "vers entite", false, "La mise au point arrive sur l'entité que tu regardes (suivie)", ChatFormatting.AQUA))
                .append(button("▶ essayer", "/cm tv intro " + m.id + " plan " + index + " essayer", false,
                        "Jouer l'intro à partir de ce plan (à l'antenne, sans la musique)", ChatFormatting.GREEN)));
        MutableComponent presets = Component.literal("  ")
                .append(button("centre → fond", base + "de centre vers fond", false, "Net sur le sujet, puis la salle (mise_au_point_inverse)", ChatFormatting.YELLOW))
                .append(button("fond → centre", base + "de fond vers centre", false, "La salle, puis le sujet (mise_au_point)", ChatFormatting.YELLOW))
                .append(button("centre fixe", base + "de centre vers centre", false, "Net sur le sujet tout du long (macro)", ChatFormatting.YELLOW))
                .append(button("✖ aucun", base + "aucun", false, "Revenir au réglage de l'effet", ChatFormatting.RED));
        c.getSource().sendSystemMessage(presets);
        MutableComponent values = Component.literal("  durée ").withStyle(ChatFormatting.GRAY);
        for (String d : List.of("0.6", "1.2", "2", "3")) values.append(button(d.replace('.', ',') + " s", base + "duree " + d, false, "Durée du glissement", ChatFormatting.WHITE));
        values.append(Component.literal(" délai ").withStyle(ChatFormatting.GRAY));
        for (String d : List.of("0", "0.4", "1")) values.append(button(d.replace('.', ',') + " s", base + "delai " + d, false, "Attente avant le glissement", ChatFormatting.WHITE));
        values.append(Component.literal(" flou ").withStyle(ChatFormatting.GRAY));
        for (String d : List.of("50", "100", "200")) values.append(button(d + " %", base + "flou " + d, false, "Force du flou hors de la mise au point", ChatFormatting.WHITE));
        c.getSource().sendSystemMessage(values);
        MutableComponent more = Component.literal("  courbe ").withStyle(ChatFormatting.GRAY);
        for (String e : FocusPull.EASINGS) more.append(button(e, base + "courbe " + e, false, "Comment la mise au point glisse", ChatFormatting.WHITE));
        boolean keep = f == null || f.keep();
        more.append(button(keep ? "garder : oui" : "garder : non", base + "garder " + (keep ? "non" : "oui"), false,
                keep ? "Clique : une fois arrivée, le flou s'efface (tout net)" : "Clique : la mise au point reste à l'arrivée", ChatFormatting.WHITE));
        more.append(button("✎ taper", base, true, "Tout régler d'une ligne : de <cible> vers <cible> duree <s|2t> delai <s> flou <%> courbe <c> garder oui|non (Tab)", ChatFormatting.LIGHT_PURPLE));
        c.getSource().sendSystemMessage(more);
        return 1;
    }
}
