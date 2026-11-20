package fr.lordfinn.craftmaster.studio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A clock of the set — the timer tablet and the giant timer: its settings and its own timer ({@link TabletClock}).
 * The server runs the timer (wall clock) and notices when it is over (sound, redstone signal); the clients draw it
 * from the elapsed time sent with each update (their own clock may differ from the server's, so only durations
 * travel). Tablets and giant timers of the same group start / pause together.
 */
public abstract class ClockBlockEntity extends SyncedBlockEntity {

    /** Loaded clocks (both sides), for groups and round triggers. */
    private static final Set<ClockBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    protected TabletClock.Settings settings = defaults();
    private final TabletClock clock = new TabletClock();
    /** The timer was seen over (server: sound played once; both: "time's up" blink start). */
    private boolean over;
    private long overAt = Long.MIN_VALUE / 2;
    /** When a bell stopped it (the frozen time flashes for a moment). */
    private long bellAt = Long.MIN_VALUE / 2;
    private boolean powered;
    private int followTicks;

    protected ClockBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The settings of a new one. */
    protected TabletClock.Settings defaults() {
        return new TabletClock.Settings();
    }

    /** Biggest size setting (tablet 4, giant timer 6). */
    public abstract int maxSize();

    /** The giant timer (a 2.5 × 1 panel, no pose, no frame) rather than a tablet. */
    public boolean giant() {
        return false;
    }

    /** Settings read from JSON (a packet, a save), clamped for this kind of clock. */
    public TabletClock.Settings read(String json) {
        return TabletClock.Settings.fromJson(json, maxSize());
    }

    /** Settings just applied (a tablet moves to its new pose). */
    protected void applied(TabletClock.Settings before) {}

    public static Set<ClockBlockEntity> loaded() {
        synchronized (LOADED) {
            return Set.copyOf(LOADED);
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        synchronized (LOADED) {
            LOADED.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        synchronized (LOADED) {
            LOADED.remove(this);
        }
    }

    public TabletClock.Settings settings() {
        return settings;
    }

    public TabletClock clock() {
        return clock;
    }

    public long bellAt() {
        return bellAt;
    }

    public long overAt() {
        return overAt;
    }

    public static long now() {
        return System.currentTimeMillis();
    }

    // ---------------------------------------------------------------- control (server)

    public void apply(TabletClock.Settings s) {
        TabletClock.Settings before = settings;
        settings = s;
        settings.size = Math.min(settings.size, maxSize());
        over = false;
        applied(before);
        sync();
    }

    /** start, pause, toggle, reset, restart. */
    public void control(String what) {
        if (settings.followContestant) return;   // follows the contestant's stopwatch only
        long now = now();
        switch (what) {
            case "start" -> {
                if (clock.done(settings, now)) clock.reset();
                clock.start(now);
            }
            case "pause" -> clock.pause(now);
            case "toggle" -> {
                if (clock.done(settings, now)) clock.restart(now);
                else clock.toggle(now);
            }
            case "reset" -> clock.reset();
            case "restart" -> clock.restart(now);
            default -> {
                return;
            }
        }
        over = false;
        overAt = Long.MIN_VALUE / 2;
        sync();
        updateSignal();
    }

    /**
     * A bell rang next to it: the tablet (and its group) stops, the frozen time flashes. Returns what it shows, or
     * null if it was not running.
     */
    public String bell() {
        boolean wasRunning = clock.running();
        long now = now();
        for (ClockBlockEntity t : settings.group.isEmpty() ? java.util.List.of(this) : loaded()) {
            if (t.level != level || !(t == this || t.settings.group.equals(settings.group))) continue;
            if (!t.clock.running()) continue;
            t.clock.pause(now);
            t.bellAt = now;
            t.sync();
            t.updateSignal();
        }
        if (!wasRunning) return null;
        boolean countdown = settings.mode == TabletClock.Mode.TIMER;
        return TabletClock.format(clock.shown(settings, now), settings.format, countdown);
    }

    /** Applies a control to this tablet and to every loaded one of its group. */
    public void controlGroup(String what) {
        if (settings.group.isEmpty()) {
            control(what);
            return;
        }
        for (ClockBlockEntity t : loaded())
            if (t.level == level && t.settings.group.equals(settings.group)) t.control(what);
    }

    /** Redstone on the block changed. */
    public void redstone(boolean nowPowered) {
        if (settings.followContestant) {
            powered = nowPowered;
            return;
        }
        boolean rising = nowPowered && !powered;
        boolean falling = !nowPowered && powered;
        powered = nowPowered;
        switch (settings.redstone) {
            case TOGGLE -> { if (rising) controlGroup("toggle"); }
            case RESTART -> { if (rising) controlGroup("restart"); }
            case RUN_WHILE_POWERED -> {
                if (rising) controlGroup("start");
                if (falling) controlGroup("pause");
            }
            case OFF -> {}
        }
    }

    /** The redstone signal given (comparator / adjacent): 15 while a timer is over. */
    public int signal() {
        return settings.signalWhenDone && settings.mode == TabletClock.Mode.TIMER && clock.done(settings, now()) && clock.elapsed(now()) > 0 ? 15 : 0;
    }

    private void updateSignal() {
        if (level != null && !level.isClientSide()) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ClockBlockEntity be) {
        long now = now();
        if (be.settings.followContestant) {
            // Every 5 ticks (own counter: the game time stands still in some worlds of the worlds mod)
            if (++be.followTicks % 5 == 0) be.followContestant(level, now);
            return;
        }
        if (be.settings.mode != TabletClock.Mode.TIMER || !be.clock.running() || be.over) return;
        if (!be.clock.done(be.settings, now)) return;
        // Over: freeze at 0, sound, redstone signal, blink
        be.over = true;
        be.overAt = now;
        be.clock.pause(now);
        SoundEvent sound = switch (be.settings.endSound) {
            case BEEP -> SoundEvents.NOTE_BLOCK_PLING.value();
            case BELL -> SoundEvents.BELL_BLOCK;
            case GONG -> Studio.GONG_SOUND.value();
            case BUZZER -> SoundEvents.NOTE_BLOCK_DIDGERIDOO.value();
            case NONE -> null;
        };
        if (sound != null && level instanceof ServerLevel sl) sl.playSound(null, pos, sound, SoundSource.BLOCKS, 1.5f, 1f);
        be.sync();
        be.updateSignal();
    }

    /**
     * The time of the contestant whose arena copy holds this clock: running from their letter, frozen at their
     * finish, 0 when they have no time (new round, reset stopwatch) or in a reference set.
     */
    private void followContestant(Level level, long now) {
        fr.lordfinn.craftmaster.trial.kit.Arenas.Owner o = fr.lordfinn.craftmaster.trial.kit.Arenas.ownerAt(level, worldPosition);
        String name = o == null ? null : o.player();
        fr.lordfinn.craftmaster.trial.Stopwatch sw = fr.lordfinn.craftmaster.trial.Stopwatch.SHARED;
        String known = name == null ? null : sw.find(name);
        boolean running = known != null && sw.isRunning(known);
        long elapsed = known == null ? 0 : running ? sw.peek(known, now) : Math.max(0, sw.last(known));
        // Sent again every 5 s while it runs: a game that missed the start (its room loaded, a teleport, a copy
        // placed while it was there) would keep showing 0:00 until the finish otherwise
        boolean resend = running && followTicks % 100 == 0;
        if (resend || running != clock.running() || Math.abs(elapsed - clock.elapsed(now)) > 250) {
            clock.set(running, elapsed, now);
            sync();
        }
    }

    // ---------------------------------------------------------------- saving and sync

    /** The saved settings (a giant timer saved before it had any reads its old size). */
    protected TabletClock.Settings loadSettings(ValueInput in) {
        return read(in.getStringOr("Settings", ""));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        settings = loadSettings(in);
        boolean running = in.getBooleanOr("Running", false);
        long now = now();
        // Sent to a client: the time counted when the packet left (no wall clock in common)
        long elapsed = in.getLongOr("ElapsedNow", -1);
        if (elapsed < 0) {
            // From the save: the run started at StartedAt (server clock)
            long banked = in.getLongOr("Banked", 0), started = in.getLongOr("StartedAt", now);
            elapsed = banked + (running ? Math.max(0, now - started) : 0);
        }
        clock.set(running, elapsed, now);
        long overAgo = in.getLongOr("OverAgo", -1);
        over = overAgo >= 0;
        overAt = over ? now - overAgo : Long.MIN_VALUE / 2;
        powered = in.getBooleanOr("Powered", false);
        long bellAgo = in.getLongOr("BellAgo", -1);
        bellAt = bellAgo >= 0 ? now - bellAgo : Long.MIN_VALUE / 2;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        long now = now();
        out.putString("Settings", settings.toJson());
        out.putBoolean("Running", clock.running());
        long elapsed = clock.elapsed(now);
        out.putLong("Banked", clock.running() ? 0 : elapsed);
        out.putLong("StartedAt", now - elapsed);
        if (over) out.putLong("OverAgo", Math.max(0, now - overAt));
        out.putBoolean("Powered", powered);
        if (now - bellAt < 5000) out.putLong("BellAgo", now - bellAt);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putLong("ElapsedNow", clock.elapsed(now()));
        return tag;
    }

    // ---------------------------------------------------------------- the show and commands

    /** A round started: clocks set to "start with the round" restart, those following the show refresh. */
    public static void onRoundStart(net.minecraft.server.MinecraftServer server) {
        for (ClockBlockEntity t : loaded())
            if (t.level != null && !t.level.isClientSide() && t.settings.startOnRound) t.control("restart");
    }

    /** The show's timer was stopped: the tablets that follow it pause. */
    public static void onTimerStopped(net.minecraft.server.MinecraftServer server) {
        for (ClockBlockEntity t : loaded())
            if (t.level != null && !t.level.isClientSide() && t.settings.stopWithTimer && t.clock.running()) t.control("pause");
    }

    public static void onRoundEnd(net.minecraft.server.MinecraftServer server) {
        for (ClockBlockEntity t : loaded())
            if (t.level != null && !t.level.isClientSide() && t.settings.stopOnRoundEnd) t.control("pause");
    }

    /** /cm plateau tablette: a control for the tablets and giant timers of a group ("" = all of them); how many were reached. */
    public static int controlAll(String group, String what) {
        int n = 0;
        String g = group == null ? "" : group.trim().toLowerCase(java.util.Locale.ROOT);
        for (ClockBlockEntity t : loaded()) {
            if (t.level == null || t.level.isClientSide() || (!g.isEmpty() && !t.settings.group.equals(g))) continue;
            t.control(what);
            n++;
        }
        return n;
    }
}
