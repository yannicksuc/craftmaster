package fr.lordfinn.craftmaster.show;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.data.Stage;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The staged reveal of a season's ranking: the stage curtain closes, drum roll, the scores are published (and the
 * season shown to the players), then the players are revealed from last to first with a title and a sound, their
 * bust lighting up; fireworks over the podium, and the curtain opens. Durations come from the stage settings.
 * {@link #stop} interrupts it (what was already published stays published).
 */
public final class Reveal {

    private record Step(long due, Runnable run) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int season;

    private Reveal() {}

    public static boolean running() {
        return !STEPS.isEmpty();
    }

    private static int ticks(double seconds) {
        return (int) Math.round(Math.max(0, seconds) * 20);
    }

    private static void at(MinecraftServer server, long delay, Runnable run) {
        STEPS.add(new Step(server.getTickCount() + delay, run));
    }

    public static void start(MinecraftServer server, int seasonId) {
        stop(server, false);
        season = seasonId;
        Stage.RevealTiming timing = CraftMaster.data().stage().reveal;
        boolean curtain = CraftMaster.data().stage().curtains.containsKey(Stage.MAIN_CURTAIN);

        Show.titleAll(server, Component.translatable("show.craftmaster.reveal_title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal(seasonName()).withStyle(ChatFormatting.YELLOW), 10, 50, 15);
        if (curtain) moveCurtain(server, false);
        long t = curtain ? ticks(timing.curtain()) : 20;

        // Drum roll: snare hits closer and closer together
        int drum = ticks(timing.drum());
        for (int d = 0; d < drum; ) {
            float progress = d / (float) Math.max(1, drum);
            at(server, t + d, () -> Show.soundAll(server, SoundEvents.NOTE_BLOCK_SNARE, 1f, 1f));
            d += Math.max(1, Math.round(5 - 4 * progress));
        }
        t += drum;
        at(server, t, () -> {
            Show.soundAll(server, SoundEvents.NOTE_BLOCK_BASEDRUM, 1f, 0.6f);
            publishAndReveal(server, timing, curtain);
        });
    }

    private static String seasonName() {
        Season s = CraftMaster.data().season(season);
        return s == null ? "Saison " + season : s.name;
    }

    private static void publishAndReveal(MinecraftServer server, Stage.RevealTiming timing, boolean curtain) {
        CraftMasterData data = CraftMaster.data();
        Season s = data.season(season);
        if (s == null) {
            stop(server, false);
            return;
        }
        data.change("Révélation : " + s.name + " publiée et montrée aux joueurs", () -> {
            s.publish(System.currentTimeMillis());
            s.hidden = false;
        });
        ActionExecutor.refresh(server);
        ShowEvents.onPublished(server, season);

        List<CraftMasterData.Entry> ranking = data.ranking(season, true);
        long t = 0;
        for (int i = ranking.size() - 1; i >= 0; i--) {
            int rank = i + 1;
            CraftMasterData.Entry e = ranking.get(i);
            at(server, t, () -> revealRank(server, rank, e));
            t += ticks(rank <= 3 ? timing.podium() : timing.step());
        }
        at(server, t, () -> fireworks(server, Math.min(3, ranking.size())));
        t += 40;
        if (curtain) at(server, t, () -> moveCurtain(server, true));
    }

    /** "1er", "2e"... in the player's language. */
    private static MutableComponent ordinal(int rank) {
        return rank <= 3 ? Component.translatable("show.craftmaster.rank." + rank) : Component.translatable("show.craftmaster.rank.n", rank);
    }

    private static void revealRank(MinecraftServer server, int rank, CraftMasterData.Entry e) {
        // Big text stays short (long pseudos ran off the screen): the rank as title, name and points as subtitle
        MutableComponent title = ordinal(rank).withStyle(Busts.rankColor(rank), ChatFormatting.BOLD);
        MutableComponent subtitle = Component.literal(e.player()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                .append(Component.literal(" — ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("show.craftmaster.points", e.total()).withStyle(ChatFormatting.YELLOW));
        Show.titleAll(server, title, subtitle, 5, 50, 10);
        switch (rank) {
            case 1 -> Show.soundAll(server, Show.holder(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE), 1f, 1f);
            case 2, 3 -> Show.soundAll(server, Show.holder(SoundEvents.PLAYER_LEVELUP), 1f, 1f);
            default -> Show.soundAll(server, SoundEvents.NOTE_BLOCK_PLING, 1f, 1f);
        }
        CraftMasterData.BustPos b = CraftMaster.data().bust(season, rank);
        ServerLevel level = b == null ? null : Busts.level(server, b.world());
        if (level != null) {
            level.sendParticles(rank == 1 ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.END_ROD,
                    b.x() + 0.5, b.y() + 0.8, b.z() + 0.5, rank == 1 ? 60 : 30, 0.4, 0.5, 0.4, 0.15);
        }
        ShowEvents.onRevealStep(server, season, rank);
    }

    /** Rockets over the podium busts (ranks 1 to {@code podium}), gold for the winner. */
    private static void fireworks(MinecraftServer server, int podium) {
        for (int rank = 1; rank <= podium; rank++) {
            CraftMasterData.BustPos b = CraftMaster.data().bust(season, rank);
            ServerLevel level = b == null ? null : Busts.level(server, b.world());
            if (level == null) continue;
            int color = switch (rank) {
                case 1 -> 0xFFC23D;
                case 2 -> 0xD5DCE4;
                default -> 0xD98A4E;
            };
            int volleys = rank == 1 ? 3 : 2;
            for (int v = 0; v < volleys; v++) {
                int delay = v * 12 + rank * 4;
                at(server, delay, () -> {
                    ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
                    rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(new FireworkExplosion(
                            FireworkExplosion.Shape.LARGE_BALL, IntList.of(color, 0xFFFFFF), IntList.of(color), true, true))));
                    double dx = (level.getRandom().nextDouble() - 0.5) * 2, dz = (level.getRandom().nextDouble() - 0.5) * 2;
                    level.addFreshEntity(new FireworkRocketEntity(level, b.x() + 0.5 + dx, b.y() + 1.0, b.z() + 0.5 + dz, rocket));
                });
            }
        }
    }

    private static void moveCurtain(MinecraftServer server, boolean open) {
        String error = StageControl.moveCurtain(server, Stage.MAIN_CURTAIN, open);
        if (error != null) CraftMaster.LOG.warn("[Révélation] {}", error);
    }

    /**
     * Interrupts the reveal. Titles are cleared; with {@code openCurtain}, the stage curtain opens again.
     *
     * @return true if a reveal was running
     */
    public static boolean stop(MinecraftServer server, boolean openCurtain) {
        if (STEPS.isEmpty()) return false;
        STEPS.clear();
        if (server != null) {
            Show.clearTitles(server);
            if (openCurtain && CraftMaster.data().stage().curtains.containsKey(Stage.MAIN_CURTAIN)) moveCurtain(server, true);
        }
        return true;
    }

    static void tick(MinecraftServer server) {
        if (STEPS.isEmpty()) return;
        long now = server.getTickCount();
        // Steps may schedule more steps: run the due ones from a copy
        List<Step> due = new ArrayList<>();
        for (Iterator<Step> it = STEPS.iterator(); it.hasNext(); ) {
            Step s = it.next();
            if (s.due() <= now) {
                due.add(s);
                it.remove();
            }
        }
        due.sort((a, b) -> Long.compare(a.due(), b.due()));
        for (Step s : due) s.run().run();
    }
}
