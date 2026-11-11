package fr.lordfinn.craftmaster.data;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/** Undo/redo stack of snapshots, bounded (50 steps by default). A new change empties the redo stack. */
public final class BoundedHistory<T> {

    public static final int DEFAULT_LIMIT = 50;

    private final int limit;
    private final Deque<T> undo = new ArrayDeque<>();
    private final Deque<T> redo = new ArrayDeque<>();

    public BoundedHistory(int limit) {
        this.limit = limit;
    }

    public BoundedHistory() {
        this(DEFAULT_LIMIT);
    }

    /** Record the state as it was BEFORE a change. */
    public void record(T before) {
        undo.addLast(before);
        if (undo.size() > limit) undo.removeFirst();
        redo.clear();
    }

    /** Given the current state, returns the previous one (and keeps current for redo). */
    public Optional<T> undo(T current) {
        if (undo.isEmpty()) return Optional.empty();
        redo.addLast(current);
        return Optional.of(undo.removeLast());
    }

    public Optional<T> redo(T current) {
        if (redo.isEmpty()) return Optional.empty();
        undo.addLast(current);
        return Optional.of(redo.removeLast());
    }

    public int undoSize() { return undo.size(); }
    public int redoSize() { return redo.size(); }
}
