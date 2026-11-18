package fr.lordfinn.craftmaster.trial.kit;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.show.Show;
import fr.lordfinn.craftmaster.trial.Judging;
import fr.lordfinn.craftmaster.trial.Stopwatch;
import fr.lordfinn.craftmaster.trial.TrialLog;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The end of a contestant's attempt, common to the kit's trials (the vase is full, the apple is eaten, the ground
 * is touched): their stopwatch stops and the time goes on the results sheet, and the news is <b>private</b>: the
 * player, the organisers and the spectators know, the other contestants don't. Also: one colour per contestant in
 * the organisers' chat, and the items marked as a trial's goal ({@code /craftMasterTrial marquer}).
 */
public final class Finish {

    private Finish() {}

    /** Custom data key of an item marked as a trial's goal ("pomme"...). */
    public static final String GOAL_KEY = "craftmaster_goal";

    /** Who finished, per round (round id + "|" + player), since the last reset: a second finish changes nothing. */
    private static final Set<String> DONE = new HashSet<>();

    private static final ChatFormatting[] COLOURS = {ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE, ChatFormatting.GREEN, ChatFormatting.YELLOW,
            ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.GOLD, ChatFormatting.DARK_AQUA, ChatFormatting.DARK_GREEN,
            ChatFormatting.DARK_PURPLE, ChatFormatting.DARK_RED, ChatFormatting.WHITE};

    /** The contestant's colour in the organisers' chat: their place in the cast (stable through a show). */
    public static ChatFormatting colour(String player) {
        List<String> cast = Judging.contestants();
        int i = -1;
        for (int k = 0; k < cast.size(); k++) if (cast.get(k).equalsIgnoreCase(player)) i = k;
        if (i < 0) i = Math.floorMod(player.toLowerCase().hashCode(), COLOURS.length);
        return COLOURS[i % COLOURS.length];
    }

    /** "Ensuri" in their colour, bold. */
    public static MutableComponent name(String player) {
        return Component.literal(player).withStyle(colour(player), ChatFormatting.BOLD);
    }

    /** A line for the organisers only (chat), and the trial log. */
    public static void tellOrganisers(MinecraftServer server, ServerPlayer except, Component line, String log) {
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (p != except && Permissions.isAdmin(p)) p.sendSystemMessage(line);
        if (log != null) TrialLog.add(server, log);
    }

    /**
     * The player's attempt is over: stopwatch stopped and written on the sheet (if it ran), « Terminé ! » for them,
     * a line for the organisers and the spectators.
     *
     * @param what what they did, for the messages ("a rempli le vase")
     * @return false if they had already finished
     */
    public static boolean done(ServerPlayer player, String what) {
        MinecraftServer server = player.level().getServer();
        String contestant = Judging.contestant(player);
        String name = contestant != null ? contestant : player.getGameProfile().name();
        if (!DONE.add(doneKey(name))) return false;
        String known = Stopwatch.SHARED.find(name);
        String time = "";
        if (known != null && Stopwatch.SHARED.isRunning(known)) {
            long ms = Stopwatch.SHARED.peek(known, System.currentTimeMillis());
            ActionExecutor.Result r = ActionExecutor.execute(server, List.of(Map.of("type", "stopwatch.stop", "player", known, "private", true)));
            if (!r.ok()) {
                // No round to write it in (a rehearsal): the stopwatch stops all the same, the time is in the log
                for (String l : r.lines()) tellOrganisers(server, null, Component.literal("⚠ " + l + " — temps non noté").withStyle(ChatFormatting.RED), null);
                ActionExecutor.execute(server, List.of(Map.of("type", "stopwatch.stop", "player", known, "private", true, "record", false)));
            }
            time = Stopwatch.format(ms);
        }
        Show.title(player, Component.literal("Terminé !").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                time.isEmpty() ? null : Component.literal(time).withStyle(ChatFormatting.YELLOW), 3, 50, 15);
        Show.sound(player, Show.holder(SoundEvents.PLAYER_LEVELUP), 1f, 1.2f);
        Component line = Component.literal("🏁 ").withStyle(ChatFormatting.GOLD).append(name(name))
                .append(Component.literal(" " + what + (time.isEmpty() ? "" : " en " + time)).withStyle(ChatFormatting.GOLD));
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (p != player && Judging.toldPrivately(p, name)) p.sendSystemMessage(line);
        TrialLog.add(server, name + " " + what + (time.isEmpty() ? "" : " en " + time));
        return true;
    }

    public static boolean finished(String player) {
        return DONE.contains(doneKey(player));
    }

    private static String doneKey(String player) {
        fr.lordfinn.craftmaster.data.Manche m = Judging.currentManche();
        return (m == null ? "" : m.id) + "|" + player.toLowerCase();
    }

    /** Everyone can finish again (a new attempt, the arena's reset). */
    public static void reset() {
        DONE.clear();
    }

    // ---------------------------------------------------------------- marked goal items

    /** Marks the stack as the goal {@code goal} (eating it ends the attempt). */
    public static void mark(ItemStack stack, String goal) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(GOAL_KEY, goal));
    }

    public static String goal(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        return tag.getString(GOAL_KEY).orElse(null);
    }

    /** From the mixin: the player finished eating or drinking {@code stack}. */
    public static void onConsumed(ServerPlayer player, ItemStack stack) {
        String goal = goal(stack);
        if (goal == null) return;
        done(player, "a mangé « " + stack.getHoverName().getString() + " »");
    }
}
