package fr.lordfinn.craftmaster.trial;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.show.Show;
import fr.lordfinn.craftmaster.trial.block.BuzzerBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The buzzers of the server, all in one game of "who is first": the first press lights its buzzer, tells
 * everyone (title + sound) and locks the others out until the judge resets ({@code buzzer.reset}, a command, the
 * voice, or an organiser sneaking + using a buzzer). Later presses are still noted (2nd, 3rd, with the delay).
 */
public final class Buzzers {

    private Buzzers() {}

    public static final BuzzerLock LOCK = new BuzzerLock();
    /** Buzzers lit since the last reset (to switch them off). */
    private static final Set<GlobalPos> LIT = new LinkedHashSet<>();

    /** A player presses the buzzer at {@code pos}. */
    public static void press(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        MinecraftServer server = level.getServer();
        String name = Judging.contestant(player) != null ? Judging.contestant(player) : player.getGameProfile().name();
        BuzzerLock.Press press = LOCK.press(name, System.currentTimeMillis());
        if (press == null) {
            player.sendOverlayMessage(Component.literal("Tu as déjà buzzé.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (press.first()) {
            level.setBlock(pos, state.setValue(BuzzerBlock.LIT, true), 3);
            LIT.add(GlobalPos.of(level.dimension(), pos.immutable()));
            Show.titleAll(server, Component.literal(name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.literal("a buzzé en premier !").withStyle(ChatFormatting.YELLOW), 2, 40, 10);
            Show.soundAll(server, SoundEvents.NOTE_BLOCK_DIDGERIDOO, 1f, 0.7f);
            Show.soundAll(server, SoundEvents.NOTE_BLOCK_BIT, 1f, 1.2f);
            TrialLog.add(server, "Buzzer : " + name + " en premier");
            // The automatic TV direction cuts to the winner
            fr.lordfinn.craftmaster.show.ShowEvents.spotlight(server, player.getGameProfile().name(), "buzzer");
        } else {
            String delay = String.format(java.util.Locale.ROOT, "%.2f", press.delayMs() / 1000.0).replace('.', ',');
            player.sendOverlayMessage(Component.literal("Trop tard ! " + press.order() + "e, +" + delay + " s").withStyle(ChatFormatting.RED));
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 0.5f);
            TrialLog.add(server, "Buzzer : " + name + " " + press.order() + "e (+" + delay + " s)");
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (Permissions.isAdmin(p))
                    p.sendSystemMessage(Component.literal("🔔 " + name + " — " + press.order() + "e (+" + delay + " s)").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Switches every buzzer off and opens the game again. @return the number of buzzers switched off */
    public static int reset(MinecraftServer server) {
        LOCK.reset();
        int n = 0;
        for (GlobalPos g : LIT) {
            ServerLevel level = server.getLevel(g.dimension());
            if (level == null || !level.isLoaded(g.pos())) continue;
            BlockState s = level.getBlockState(g.pos());
            if (s.getBlock() instanceof BuzzerBlock && s.getValue(BuzzerBlock.LIT)) {
                level.setBlock(g.pos(), s.setValue(BuzzerBlock.LIT, false), 3);
                n++;
            }
        }
        LIT.clear();
        Show.soundAll(server, SoundEvents.NOTE_BLOCK_CHIME, 0.6f, 1.5f);
        return n;
    }
}
