package fr.lordfinn.craftmaster.broadcast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cutting on who speaks (pure logic, times in milliseconds), like a talk show's director: the one who talks gets
 * their shot, but never every second.
 * <ul>
 *   <li>a voice counts once it has lasted {@link #MIN_TALK} (a cough, a "yes" do not cut);</li>
 *   <li>a shot stays at least {@link #MIN_HOLD} ({@link #GROUP_HOLD} for a group shot);</li>
 *   <li>a back-and-forth (the shot would go back to someone seen less than {@link #GROUP_WINDOW} ago, or two
 *       talk at once) goes to a group shot of those speaking rather than ping-pong;</li>
 *   <li>after {@link #SILENCE} without a voice, back to the wide shot.</li>
 * </ul>
 * The caller tells it when it put a shot on air ({@link #aired}) or when something else took the air
 * ({@link #otherShot}).
 */
public final class SpeakerCuts {

    public static final long GAP = 450, MIN_TALK = 500, MIN_HOLD = 2000, GROUP_HOLD = 3000, GROUP_WINDOW = 6000, SILENCE = 5000;

    public enum Kind { NONE, SOLO, GROUP, WIDE }

    public record Decision(Kind kind, List<String> who) {
        static final Decision NONE = new Decision(Kind.NONE, List.of());
    }

    /** Start of each one's current stretch of speech, and their last packet. */
    private final Map<String, Long> talkStart = new HashMap<>(), lastTalk = new HashMap<>();
    /** When each one last spoke long enough to count. */
    private final Map<String, Long> lastCounted = new HashMap<>();
    /** What is on air from us: SOLO / GROUP / WIDE (null = another shot), whom, since when. */
    private Kind onAir;
    private List<String> onAirWho = List.of();
    private long since = Long.MIN_VALUE / 2;
    /** The last solo shot: whom, when. */
    private String lastSolo;
    private long lastSoloAt = Long.MIN_VALUE / 2;

    /**
     * One step: {@code talking} = who is heard right now (a voice packet within the last moment), among those a
     * camera can show. Returns the shot to take, or {@link Decision#NONE}.
     */
    public Decision update(long now, Collection<String> talking) {
        for (String p : talking) {
            Long last = lastTalk.get(p);
            if (last == null || now - last > GAP) talkStart.put(p, now);
            lastTalk.put(p, now);
        }
        List<String> counted = new ArrayList<>();
        for (Map.Entry<String, Long> e : lastTalk.entrySet()) {
            String p = e.getKey();
            if (now - e.getValue() <= GAP && e.getValue() - talkStart.getOrDefault(p, now) >= MIN_TALK) {
                counted.add(p);
                lastCounted.put(p, now);
            }
        }
        counted.sort(String::compareToIgnoreCase);
        long held = now - since;
        boolean holding = onAir != null && held < (onAir == Kind.GROUP ? GROUP_HOLD : MIN_HOLD);

        if (!counted.isEmpty()) {
            if (holding) return Decision.NONE;
            // Several at once: the group
            if (counted.size() > 1) return group(now, counted);
            String s = counted.getFirst();
            if (onAir == Kind.SOLO && onAirWho.contains(s)) return Decision.NONE;
            if (onAir == Kind.GROUP && onAirWho.contains(s)) {
                // In the group shot: a long solo stretch earns its own shot again
                return now - talkStart.get(s) >= MIN_HOLD ? new Decision(Kind.SOLO, List.of(s)) : Decision.NONE;
            }
            // Ping-pong: back to someone else seen a moment ago → both of them
            if (lastSolo != null && !lastSolo.equalsIgnoreCase(s) && now - lastSoloAt < GROUP_WINDOW
                    && now - lastCounted.getOrDefault(lastSolo, Long.MIN_VALUE / 2) < GROUP_WINDOW)
                return group(now, List.of(lastSolo, s));
            return new Decision(Kind.SOLO, List.of(s));
        }
        // Silence: back to the wide shot (only from our own speaker shots)
        if ((onAir == Kind.SOLO || onAir == Kind.GROUP) && held >= MIN_HOLD) {
            long lastVoice = Long.MIN_VALUE / 2;
            for (long t : lastTalk.values()) lastVoice = Math.max(lastVoice, t);
            if (now - lastVoice >= SILENCE) return new Decision(Kind.WIDE, List.of());
        }
        return Decision.NONE;
    }

    private Decision group(long now, Collection<String> core) {
        // Everyone heard lately, so the shot keeps them all
        Set<String> who = new LinkedHashSet<>(core);
        for (Map.Entry<String, Long> e : lastCounted.entrySet()) if (now - e.getValue() < GROUP_WINDOW) who.add(e.getKey());
        List<String> list = new ArrayList<>(who);
        list.sort(String::compareToIgnoreCase);
        if (onAir == Kind.GROUP && onAirWho.containsAll(list)) return Decision.NONE;
        return new Decision(Kind.GROUP, list);
    }

    /** The decision {@code d} went on air (the caller may have widened a group it could not frame). */
    public void aired(long now, Decision d) {
        onAir = d.kind() == Kind.NONE ? onAir : d.kind();
        onAirWho = d.who();
        since = now;
        if (d.kind() == Kind.SOLO) {
            lastSolo = d.who().getFirst();
            lastSoloAt = now;
        }
    }

    /** Another shot took the air (operator, score, spotlight…): ours are over. */
    public void otherShot(long now) {
        onAir = null;
        onAirWho = List.of();
        since = now;
    }

    /** Forget the voices of those gone. */
    public void forget(String player) {
        talkStart.remove(player);
        lastTalk.remove(player);
        lastCounted.remove(player);
    }
}
