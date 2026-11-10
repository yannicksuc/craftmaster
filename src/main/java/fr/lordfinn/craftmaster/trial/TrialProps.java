package fr.lordfinn.craftmaster.trial;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.show.Show;
import fr.lordfinn.craftmaster.task.Tasks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What a task prop does when used, and the redstone pulses it leaves behind. */
public final class TrialProps {

    private TrialProps() {}

    /** Redstone blocks put by props, with the server tick at which they go away. */
    private static final Map<GlobalPos, Long> PULSES = new LinkedHashMap<>();

    /** A new prop standing at {@code pos}, turned to {@code yaw}, showing {@code look}. */
    public static TrialPropEntity spawn(ServerLevel level, Vec3 pos, float yaw, ItemStack look) {
        TrialPropEntity e = new TrialPropEntity(TrialContent.TRIAL_PROP, level);
        e.snapTo(pos.x, pos.y, pos.z, 0, 0);
        e.setLook(look);
        PropConfig c = new PropConfig();
        c.yaw = yaw;
        e.setConfig(c);
        level.addFreshEntity(e);
        return e;
    }

    /** A player uses the prop. */
    public static void use(TrialPropEntity prop, ServerPlayer player) {
        if (!(prop.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        PropConfig c = prop.config();
        String contestant = Judging.contestant(player);
        if (c.contestantsOnly && contestant == null) {
            player.sendOverlayMessage(Component.literal("Réservé aux candidats").withStyle(ChatFormatting.GRAY));
            return;
        }
        String name = contestant != null ? contestant : player.getGameProfile().name();
        PropStats.Use u = prop.stats().use(name, System.currentTimeMillis(), c.oncePerPlayer, (long) (c.cooldown * 1000));
        if (u.refused()) {
            if (c.oncePerPlayer && u.playerCount() > 0)
                player.sendOverlayMessage(Component.literal("Déjà utilisé").withStyle(ChatFormatting.GRAY));
            return;
        }
        String propName = prop.displayName();

        if (!c.message.isEmpty()) {
            Component msg = Component.literal(c.message.replace("{player}", name).replace("{count}", String.valueOf(u.playerCount()))
                    .replace("{total}", String.valueOf(u.total()))).withStyle(ChatFormatting.GOLD);
            if (c.messageToAll) for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(msg);
            else player.sendSystemMessage(msg);
        }
        if (!c.sound.isEmpty()) {
            Identifier id = Identifier.tryParse(c.sound);
            SoundEvent sound = id == null ? null : BuiltInRegistries.SOUND_EVENT.getValue(id);
            if (sound != null) level.playSound(null, prop.getX(), prop.getY() + 0.5, prop.getZ(), sound, SoundSource.BLOCKS, 1f, c.soundPitch);
        }
        ItemStack gift = gift(prop);
        if (!gift.isEmpty() && !player.getInventory().add(gift)) player.spawnAtLocation(level, gift);
        if (!c.noteText.isEmpty()) Tasks.openPaper(player, c.noteTitle.isEmpty() ? propName : c.noteTitle, c.noteText);
        if (c.pulse) pulse(level, prop.blockPosition().offset(c.pulseDx, c.pulseDy, c.pulseDz), c.pulseTicks, server);
        if (c.teleport) player.teleportTo(level, c.tpX, c.tpY, c.tpZ, Set.of(), c.tpYaw, c.tpPitch, true);
        if (c.startAttempt) ActionExecutor.execute(server, List.of(Map.of("type", "stopwatch.start", "player", name)));
        if (c.endAttempt && Stopwatch.SHARED.find(name) != null && Stopwatch.SHARED.isRunning(Stopwatch.SHARED.find(name)))
            report(server, ActionExecutor.execute(server, List.of(Map.of("type", "stopwatch.stop", "player", name))));

        if (u.firstEver()) {
            TrialLog.add(server, "« " + propName + " » : " + name + " en premier");
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (Permissions.isAdmin(p) && p != player)
                    p.sendSystemMessage(Component.literal("★ " + name + " a touché « " + propName + " » en premier").withStyle(ChatFormatting.GOLD));
            if (c.announceFirst)
                Show.titleAll(server, Component.literal(name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                        Component.literal("a trouvé « " + propName + " » en premier !").withStyle(ChatFormatting.YELLOW), 3, 50, 12);
        } else if (u.firstForPlayer()) {
            TrialLog.add(server, "« " + propName + " » : " + name + " (" + prop.stats().order().size() + "e)");
        }
    }

    /** Errors of an attempt ended by a prop (sheet of another kind...) go to the organisers. */
    private static void report(MinecraftServer server, ActionExecutor.Result r) {
        if (r.ok()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (Permissions.isAdmin(p)) for (String l : r.lines()) p.sendSystemMessage(Component.literal("⚠ " + l).withStyle(ChatFormatting.RED));
    }

    /** The item given: the exact stack chosen in the settings, or a plain one from its id. */
    private static ItemStack gift(TrialPropEntity prop) {
        PropConfig c = prop.config();
        if (c.giveItem.isEmpty()) return ItemStack.EMPTY;
        ItemStack kept = prop.giveStack();
        if (!kept.isEmpty() && BuiltInRegistries.ITEM.getKey(kept.getItem()).toString().equals(c.giveItem))
            return kept.copyWithCount(c.giveCount);
        Identifier id = Identifier.tryParse(c.giveItem.contains(":") ? c.giveItem : "minecraft:" + c.giveItem);
        Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
        return item == null || item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item, c.giveCount);
    }

    /** A redstone block for a moment, only where there is air (it never replaces a block of the set). */
    private static void pulse(ServerLevel level, BlockPos pos, int ticks, MinecraftServer server) {
        GlobalPos g = GlobalPos.of(level.dimension(), pos.immutable());
        if (PULSES.containsKey(g)) {
            PULSES.put(g, server.getTickCount() + (long) ticks);
            return;
        }
        if (!level.getBlockState(pos).isAir()) {
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (Permissions.isAdmin(p))
                    p.sendOverlayMessage(Component.literal("Signal redstone impossible : " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                            + " n'est pas vide").withStyle(ChatFormatting.RED));
            return;
        }
        level.setBlock(pos, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        PULSES.put(g, server.getTickCount() + (long) ticks);
    }

    public static void tick(MinecraftServer server) {
        if (PULSES.isEmpty()) return;
        for (Iterator<Map.Entry<GlobalPos, Long>> it = PULSES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<GlobalPos, Long> e = it.next();
            if (server.getTickCount() < e.getValue()) continue;
            ServerLevel level = server.getLevel(e.getKey().dimension());
            if (level != null && level.getBlockState(e.getKey().pos()).is(Blocks.REDSTONE_BLOCK))
                level.setBlock(e.getKey().pos(), Blocks.AIR.defaultBlockState(), 3);
            it.remove();
        }
    }

    /** Server stopping: the pulses end now (no redstone block left in the world). */
    public static void clear(MinecraftServer server) {
        for (Map.Entry<GlobalPos, Long> e : PULSES.entrySet()) e.setValue(0L);
        tick(server);
    }
}
