package fr.lordfinn.craftmaster.trial.kit;

import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.trial.Judging;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.Blocks;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * « La tâche s'arrête quand vous touchez le sol » ({@link Arena#solFin}): once a contestant has left the arena's box
 * (the starting platform), standing on a solid block ends their attempt — not being in water, in a cobweb, on a
 * mount or gliding (the technicalities they may find). Their inventory is then locked (no click changes it) and,
 * with {@link Arena#alphabet}, scored: the longest run of items in alphabetical order in reading order, in the
 * player's own game language. The news is private ({@link Finish}).
 */
public final class GroundEnd {

    private GroundEnd() {}

    /** Round id + contestant seen on the starting platform: only they can end on the ground. */
    private static final java.util.Set<String> ON_PLATFORM = new java.util.HashSet<>();

    /** The arena whose round is running and whose attempt ends on the ground, or null. */
    private static Arena active() {
        Manche m = Judging.currentManche();
        if (m == null) return null;
        for (Arena a : Arenas.all()) if (a.solFin && m.id.equals(a.manche)) return a;
        return null;
    }

    /** The arena was reset: everyone must go back to the platform first. */
    static void forget() {
        ON_PLATFORM.clear();
    }

    /** Standing on a solid block (not floating, swimming, riding, gliding, in a cobweb). */
    public static boolean grounded(ServerPlayer p) {
        if (!p.onGround() || p.isPassenger() || p.isInWater() || p.isInLava() || p.isFallFlying() || p.isSpectator()) return false;
        return !p.level().getBlockState(p.blockPosition()).is(Blocks.COBWEB) && !p.level().getBlockState(p.blockPosition().above()).is(Blocks.COBWEB);
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 2 != 0) return;
        Arena a = active();
        if (a == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String c = Judging.contestant(p);
            if (c == null) continue;
            if (Finish.finished(c)) {
                if (p.containerMenu != p.inventoryMenu) p.closeContainer(); // no chest either
                continue;
            }
            if (a.alphabet) Lang.prefetch(p.clientInformation().language());
            Arenas.Owner o = Arenas.ownerAt(p.level(), p.blockPosition());
            String key = a.manche + "|" + c.toLowerCase(Locale.ROOT);
            if (o != null && o.arena() == a) {
                ON_PLATFORM.add(key); // still on the starting platform: from now on, the ground ends the attempt
                continue;
            }
            // Only once they have been on the platform in this round (not someone standing about before the start)
            if (!ON_PLATFORM.contains(key) || !grounded(p)) continue;
            end(p, c, a);
        }
    }

    private static void end(ServerPlayer p, String name, Arena a) {
        MinecraftServer server = p.level().getServer();
        p.closeContainer();
        if (!a.alphabet) {
            Finish.done(p, "a touché le sol");
            return;
        }
        String locale = Lang.norm(p.clientInformation().language());
        Map<String, String> table = Lang.table(locale);
        Score s = score(p, table, locale);
        Finish.done(p, "a touché le sol : suite de " + s.run.size());
        ActionExecutor.execute(server, List.of(Map.of("type", "result.set", "player", name, "value", s.run.size(), "kind", "count", "private", true)));
        String lang = locale + (table == null && !locale.equals("en_us") ? ", noms en anglais : langue pas encore téléchargée" : "");
        Finish.tellOrganisers(server, null, Component.literal("  ").append(Finish.name(name))
                .append(Component.literal(" (" + lang + ") : " + String.join(" ≤ ", s.run)).withStyle(ChatFormatting.GRAY)),
                name + " — suite de " + s.run.size() + " (" + locale + ") : " + String.join(" ≤ ", s.run));
        if (s.inBundle > 0)
            Finish.tellOrganisers(server, null, Component.literal("  ⚠ " + s.inBundle + " objet(s) encore dans un bundle ").withStyle(ChatFormatting.RED)
                    .append(Component.literal("[disqualifier " + name + "]").withStyle(st -> st.withColor(ChatFormatting.DARK_RED).withBold(true)
                            .withClickEvent(new ClickEvent.RunCommand("/craftMasterResult dq " + name))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("« Aucun objet ne doit être dans un bundle »"))))),
                    name + " — " + s.inBundle + " objet(s) dans un bundle");
    }

    /** The longest run, its names in order, and how many items were left inside bundles. */
    public record Score(List<String> run, int inBundle) {}

    /**
     * Reading order of the inventory screen: the three rows of the backpack (slots 9 to 35, left to right, top to
     * bottom), then the hot bar (0 to 8), the bottom row on screen. Empty slots are skipped; two equal names follow
     * each other fine. Armour and off hand don't count.
     */
    public static Score score(ServerPlayer p, Map<String, String> table, String locale) {
        List<ItemStack> order = new ArrayList<>();
        for (int i = 9; i < 36; i++) order.add(p.getInventory().getItem(i));
        for (int i = 0; i < 9; i++) order.add(p.getInventory().getItem(i));
        List<String> names = new ArrayList<>();
        int inBundle = 0;
        for (ItemStack st : order) {
            if (st.isEmpty()) continue;
            names.add(Lang.text(st.getHoverName(), table).trim());
            BundleContents b = st.get(DataComponents.BUNDLE_CONTENTS);
            if (b != null) inBundle += (int) b.itemCopyStream().mapToInt(ItemStack::getCount).sum();
        }
        return new Score(longestRun(names, locale), inBundle);
    }

    /** Longest run of consecutive names in non-decreasing alphabetical order (the language's own collation). */
    static List<String> longestRun(List<String> names, String locale) {
        String[] parts = locale.split("_");
        Collator col = Collator.getInstance(parts.length > 1 ? Locale.of(parts[0], parts[1].toUpperCase(Locale.ROOT)) : Locale.of(parts[0]));
        col.setStrength(Collator.PRIMARY);
        int bestStart = 0, bestLen = names.isEmpty() ? 0 : 1, start = 0;
        for (int i = 1; i < names.size(); i++) {
            if (col.compare(names.get(i - 1), names.get(i)) > 0) start = i;
            if (i - start + 1 > bestLen) {
                bestLen = i - start + 1;
                bestStart = start;
            }
        }
        return names.isEmpty() ? List.of() : new ArrayList<>(names.subList(bestStart, bestStart + bestLen));
    }

    /** No click may change this player's inventory (from the mixin): they finished an attempt that ends on the ground. */
    public static boolean locked(ServerPlayer p) {
        Arena a = active();
        if (a == null) return false;
        String c = Judging.contestant(p);
        return c != null && Finish.finished(c);
    }
}
