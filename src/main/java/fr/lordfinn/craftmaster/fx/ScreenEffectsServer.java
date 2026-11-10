package fr.lordfinn.craftmaster.fx;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.data.SeasonPlayer;
import fr.lordfinn.craftmaster.fx.FxNetwork.Order;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of the screen effects: who has which effect forced on their view, the broadcast view's effect, and
 * the wearables put on a contestant by an organiser. The clients draw the effects (client.fx.ScreenEffects); the
 * server only sends orders ({@link FxNetwork.Order}) and remembers them, so a game that reconnects (or switches to
 * broadcast mode later) gets the effect still running. Nothing here is in the undo history: it is the live show's
 * picture, like the camera shots.
 *
 * <p>Targets: {@code diffusion} (the games in broadcast mode), {@code tous} (every player online), {@code candidats}
 * (the active season's cast online), {@code tout} (everything: players and broadcast, for "aucun"), or a player.
 */
public final class ScreenEffectsServer {

    private ScreenEffectsServer() {}

    public static final int DEFAULT_FADE_TICKS = 12;

    /** A remembered order: effect, fade, server tick when it ends (-1 = until changed). */
    private record Active(String effect, int fadeTicks, long endTick) {}

    private static final Map<UUID, Active> forced = new HashMap<>();
    private static Active broadcast;

    public static final Set<String> BROADCAST_WORDS = Set.of("diffusion", "broadcast", "stream", "live", "antenne", "tv", "camera");
    public static final Set<String> ALL_WORDS = Set.of("tous", "all", "@a", "joueurs", "tout_le_monde", "everyone", "everybody");
    public static final Set<String> CAST_WORDS = Set.of("candidats", "candidat", "contestants", "cast");
    public static final Set<String> EVERYTHING_WORDS = Set.of("tout", "partout", "everything");

    public static void register() {
        FxContent.register();
        FxNetwork.registerTypes();
        ServerTickEvents.END_SERVER_TICK.register(ScreenEffectsServer::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> onJoin(server, handler.getPlayer())));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            forced.clear();
            broadcast = null;
        });
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        forced.values().removeIf(a -> a.endTick >= 0 && now >= a.endTick);
        if (broadcast != null && broadcast.endTick >= 0 && now >= broadcast.endTick) broadcast = null;
    }

    private static void onJoin(MinecraftServer server, ServerPlayer p) {
        long now = server.getTickCount();
        if (broadcast != null) send(p, FxNetwork.LAYER_BROADCAST, broadcast, now);
        Active a = forced.get(p.getUUID());
        if (a != null) send(p, FxNetwork.LAYER_PLAYER, a, now);
    }

    private static void send(ServerPlayer p, int layer, Active a, long now) {
        int ticks = a.endTick < 0 ? 0 : (int) Math.max(1, a.endTick - now);
        if (ServerPlayNetworking.canSend(p, Order.TYPE)) ServerPlayNetworking.send(p, new Order(layer, a.effect, a.fadeTicks, ticks));
    }

    private static String norm(String target) {
        return target == null ? "" : target.trim().toLowerCase(Locale.ROOT).replace("é", "e").replaceAll("\\s+", "_");
    }

    /** True if the target names the broadcast view. */
    public static boolean isBroadcast(String target) {
        return BROADCAST_WORDS.contains(norm(target));
    }

    /**
     * Puts an effect (a parsed combination, empty = none) on a target for {@code seconds} (0 = until changed).
     *
     * @return the French line for the organiser
     * @throws IllegalArgumentException if the target is unknown (no such player online)
     */
    public static String set(MinecraftServer server, String target, List<String> effect, int seconds, int fadeTicks) {
        String t = norm(target);
        String spec = ScreenFx.join(effect);
        long now = server.getTickCount();
        Active a = new Active(spec, Math.max(0, fadeTicks), seconds > 0 ? now + seconds * 20L : -1);
        String what = ScreenFx.describe(effect);
        String time = seconds > 0 ? " pendant " + seconds + " s" : "";
        boolean everything = EVERYTHING_WORDS.contains(t);
        if (BROADCAST_WORDS.contains(t) || (everything && effect.isEmpty())) {
            broadcast = effect.isEmpty() ? null : a;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p, FxNetwork.LAYER_BROADCAST, a, now);
            if (!everything) return effect.isEmpty() ? "Diffusion : effets retirés." : "Diffusion : " + what + time + ".";
        }
        List<ServerPlayer> players = players(server, t);
        for (ServerPlayer p : players) {
            if (effect.isEmpty()) forced.remove(p.getUUID());
            else forced.put(p.getUUID(), a);
            send(p, FxNetwork.LAYER_PLAYER, a, now);
            if (!effect.isEmpty())
                p.sendOverlayMessage(Component.literal("Effet : " + what + time).withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        if (everything) return "Tous les effets retirés (diffusion et joueurs).";
        String who = ALL_WORDS.contains(t) ? "tous les joueurs (" + players.size() + ")"
                : CAST_WORDS.contains(t) ? "les candidats connectés (" + players.size() + ")" : players.getFirst().getName().getString();
        return effect.isEmpty() ? "Effets retirés pour " + who + "." : who + " : " + what + time + ".";
    }

    /** "+a+b-c": changes what is on instead of replacing it. */
    public static boolean isDelta(String spec) {
        String s = spec == null ? "" : spec.trim();
        return s.startsWith("+") || s.startsWith("-");
    }

    /** A delta with canonical ids, e.g. "+ Sépia -flou" -> "+sepia-flou" (throws on an unknown effect). */
    public static String canonicalDelta(String spec) {
        StringBuilder out = new StringBuilder();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([+-])([^+-]*)").matcher(spec.trim());
        while (m.find()) {
            if (m.group(2).isBlank()) continue;
            for (String id : ScreenFx.parse(m.group(2))) out.append(m.group(1)).append(id);
        }
        return out.toString();
    }

    /** The current effects with a delta applied (see {@link #isDelta}). */
    static List<String> merge(String current, String delta) {
        LinkedHashSet<String> cur = new LinkedHashSet<>(ScreenFx.parseLenient(current));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([+-])([^+-]*)").matcher(delta.trim());
        while (m.find()) {
            if (m.group(2).isBlank()) continue;
            List<String> ids = ScreenFx.parse(m.group(2));
            if (m.group(1).equals("+")) cur.addAll(ids);
            else ids.forEach(cur::remove);
        }
        return ScreenFx.parse(String.join("+", cur));
    }

    /** Like {@link #set}, but a delta ("+sepia", "-flou") is merged with what each target already has. */
    public static String apply(MinecraftServer server, String target, String spec, int seconds, int fadeTicks) {
        if (!isDelta(spec)) return set(server, target, ScreenFx.parseLenient(spec), seconds, fadeTicks);
        if (BROADCAST_WORDS.contains(norm(target))) return set(server, target, merge(broadcastEffect(), spec), seconds, fadeTicks);
        List<String> lines = new ArrayList<>();
        for (ServerPlayer p : players(server, target))
            lines.add(set(server, p.getName().getString(), merge(forcedOn(p), spec), seconds, fadeTicks));
        return String.join(" ", lines);
    }

    /** Players named by a target (not the broadcast). */
    public static List<ServerPlayer> players(MinecraftServer server, String target) {
        String t = norm(target);
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        if (ALL_WORDS.contains(t) || EVERYTHING_WORDS.contains(t)) return new ArrayList<>(online);
        if (CAST_WORDS.contains(t)) {
            Set<String> cast = new LinkedHashSet<>();
            CraftMasterData data = CraftMaster.data();
            Season s = data == null ? null : data.activeSeason();
            if (s != null) for (SeasonPlayer sp : s.players.values()) cast.add(sp.name.toLowerCase(Locale.ROOT));
            return online.stream().filter(p -> cast.contains(p.getName().getString().toLowerCase(Locale.ROOT))).toList();
        }
        for (ServerPlayer p : online) if (p.getName().getString().equalsIgnoreCase(target.trim())) return List.of(p);
        throw new IllegalArgumentException("« " + target + " » n'est pas connecté (cibles : diffusion, tous, candidats, ou un joueur).");
    }

    /** Effect forced on a player now ("" = none). */
    public static String forcedOn(ServerPlayer p) {
        Active a = forced.get(p.getUUID());
        return a == null ? "" : a.effect;
    }

    /** Effect of the broadcast view now ("" = none). */
    public static String broadcastEffect() {
        return broadcast == null ? "" : broadcast.effect;
    }

    // ---------------------------------------------------------------- wearables put on by an organiser

    /**
     * Puts a wearable on a player's head (what they wore goes back to their inventory, or drops). With
     * {@code lock}, the item gets the Curse of Binding: the player cannot take it off (survival / adventure) until
     * {@link #takeOff}.
     */
    public static String wear(ServerPlayer p, Item item, boolean lock) {
        ItemStack old = p.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack stack = new ItemStack(item);
        if (lock) {
            stack.enchant(binding(p.level().getServer()), 1);
            // Locked, but it keeps its look on camera (no enchantment shine)
            stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        }
        p.setItemSlot(EquipmentSlot.HEAD, stack);
        if (!old.isEmpty() && !p.getInventory().add(old)) p.drop(old, false, net.minecraft.util.Prediction.SERVER_ONLY);
        String name = FxContent.frenchName(stack);
        p.sendOverlayMessage(Component.literal("Tu portes : ").append(stack.getHoverName()).append(lock ? " (verrouillé)" : "")
                .withStyle(net.minecraft.ChatFormatting.GOLD));
        return p.getName().getString() + " porte " + name + (lock ? ", verrouillé" : "") + ".";
    }

    /** Takes off what a player wears on the head if it carries a screen effect (locked or not). */
    public static String takeOff(ServerPlayer p) {
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        if (FxContent.effectOf(head).isEmpty()) return p.getName().getString() + " ne porte pas d'objet à effet.";
        String name = FxContent.frenchName(head);
        p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        // Given back without the curse (it was only there for the trial)
        EnchantmentHelper.updateEnchantments(head, m -> m.removeIf(h -> h.is(Enchantments.BINDING_CURSE)));
        head.remove(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        if (!p.getInventory().add(head)) p.drop(head, false, net.minecraft.util.Prediction.SERVER_ONLY);
        return p.getName().getString() + " a retiré " + name + ".";
    }

    private static Holder<Enchantment> binding(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BINDING_CURSE);
    }
}
