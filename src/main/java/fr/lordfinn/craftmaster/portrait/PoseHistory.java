package fr.lordfinn.craftmaster.portrait;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo / redo of the pose editor. Each change pushes the previous pose, except changes continuing the same gesture
 * (same {@code key}: one mouse drag, one slider, a burst of wheel steps on the same axis), which replace the last
 * step: dragging an arm for two seconds is one undo, not two hundred.
 */
public final class PoseHistory {

    /** Changes with the same key closer than this (ms) are one step, until {@link #seal()}. */
    public static final long MERGE_MS = 900;
    /** Steps kept. */
    public static final int LIMIT = 200;

    private final Deque<Pose> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private Pose current;
    private String lastKey;
    private long lastTime;

    public PoseHistory(Pose initial) {
        current = initial;
    }

    public Pose current() {
        return current;
    }

    /**
     * The pose becomes {@code next}.
     *
     * @param key  what made the change (merged with the previous change of the same key), null: always a new step
     * @param time now, in ms
     * @return false if nothing changed
     */
    public boolean change(Pose next, String key, long time) {
        if (next == null || next.equals(current)) return false;
        boolean merge = key != null && key.equals(lastKey) && time - lastTime <= MERGE_MS && !undo.isEmpty();
        if (!merge) {
            undo.push(current);
            while (undo.size() > LIMIT) undo.removeLast();
        }
        redo.clear();
        current = next;
        lastKey = key;
        lastTime = time;
        return true;
    }

    /** The current gesture is over (mouse released): the next change is a new step even with the same key. */
    public void seal() {
        lastKey = null;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /** Goes one step back; returns the pose now current. */
    public Pose undo() {
        if (undo.isEmpty()) return current;
        redo.push(current);
        current = undo.pop();
        seal();
        return current;
    }

    /** Goes one step forward again; returns the pose now current. */
    public Pose redo() {
        if (redo.isEmpty()) return current;
        undo.push(current);
        current = redo.pop();
        seal();
        return current;
    }

    /** Starts over from {@code pose} (another pose loaded): its own history, nothing to undo. */
    public void reset(Pose pose) {
        undo.clear();
        redo.clear();
        current = pose;
        seal();
    }
}
