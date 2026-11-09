package fr.lordfinn.craftmaster.portrait;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * How the figure stands in (or out of) a portrait's frame.
 * <p>
 * Rotations are in degrees and follow the vanilla player model conventions ({@code ModelPart} xRot/yRot/zRot,
 * applied Z then Y then X, model space: y down, the face looking at -z): a negative X rotation brings an arm
 * forward, a positive Z rotation raises the right arm sideways. {@link Part#FIGURE} turns the whole figure around
 * its pelvis, {@link Part#BODY} turns the upper body (head and arms follow) around the hips.
 * <p>
 * {@code offset} is where the figure's feet are, in pixels, relative to the centre of the frame (x to the
 * viewer's right, y up, z toward the viewer). {@code scale} enlarges the figure around its feet (a bust fills the
 * frame better at 1.4). {@code framePitch} leans the frame forward (positive: its top comes toward the viewer),
 * {@code frameRoll} turns it in its own plane (positive: counter-clockwise as seen from the front). Hidden parts
 * are not drawn and do not weigh in the centre of gravity.
 */
public record Pose(Map<Part, Rotation> rotations, Set<Part> hidden,
                   double offsetX, double offsetY, double offsetZ, double scale, double framePitch, double frameRoll) {

    /** The figure's parts, with their key in poses.json. */
    public enum Part {
        FIGURE("figure"), HEAD("head"), BODY("body"), RIGHT_ARM("rightArm"), LEFT_ARM("leftArm"),
        RIGHT_LEG("rightLeg"), LEFT_LEG("leftLeg");

        public final String key;

        Part(String key) {
            this.key = key;
        }

        public static Part byKey(String key) {
            for (Part p : values()) if (p.key.equalsIgnoreCase(key)) return p;
            return null;
        }

        /** Parts that can be hidden (the whole figure cannot). */
        public boolean hideable() {
            return this != FIGURE;
        }
    }

    /** Rotation of a part around X, Y and Z, in degrees. */
    public record Rotation(double x, double y, double z) {
        public static final Rotation ZERO = new Rotation(0, 0, 0);

        public boolean isZero() {
            return x == 0 && y == 0 && z == 0;
        }
    }

    public Pose {
        EnumMap<Part, Rotation> r = new EnumMap<>(Part.class);
        for (Part p : Part.values()) r.put(p, rotations == null ? Rotation.ZERO : rotations.getOrDefault(p, Rotation.ZERO));
        rotations = Map.copyOf(r);
        EnumSet<Part> h = EnumSet.noneOf(Part.class);
        if (hidden != null) for (Part p : hidden) if (p.hideable()) h.add(p);
        hidden = Set.copyOf(h);
        if (!(scale >= 0.1 && scale <= 10)) scale = 1;
    }

    /** A figure standing straight in the frame, feet on its bottom edge. */
    public static final Pose NEUTRAL = new Pose(Map.of(), Set.of(), 0, -16, 3, 1, 0, 0);

    public Rotation rotation(Part part) {
        return rotations.get(part);
    }

    public boolean isHidden(Part part) {
        return hidden.contains(part);
    }

    public Pose withRotation(Part part, Rotation rotation) {
        EnumMap<Part, Rotation> r = new EnumMap<>(rotations);
        r.put(part, rotation);
        return new Pose(r, hidden, offsetX, offsetY, offsetZ, scale, framePitch, frameRoll);
    }

    public Pose withHidden(Part part, boolean hide) {
        EnumSet<Part> h = hidden.isEmpty() ? EnumSet.noneOf(Part.class) : EnumSet.copyOf(hidden);
        if (hide) h.add(part);
        else h.remove(part);
        return new Pose(rotations, h, offsetX, offsetY, offsetZ, scale, framePitch, frameRoll);
    }

    public Pose withOffset(double x, double y, double z) {
        return new Pose(rotations, hidden, x, y, z, scale, framePitch, frameRoll);
    }

    public Pose withScale(double s) {
        return new Pose(rotations, hidden, offsetX, offsetY, offsetZ, s, framePitch, frameRoll);
    }

    public Pose withFrame(double pitch, double roll) {
        return new Pose(rotations, hidden, offsetX, offsetY, offsetZ, scale, pitch, roll);
    }

    // ---------------------------------------------------------------- JSON

    /**
     * <pre>{"offset": [0, -16, 3], "scale": 1.4, "frame": [pitch, roll], "head": [x, y, z], ..., "hidden": ["rightLeg"]}</pre>
     * Parts without rotation and default values are left out.
     */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.add("offset", array(offsetX, offsetY, offsetZ));
        if (scale != 1) o.add("scale", number(scale));
        if (framePitch != 0 || frameRoll != 0) o.add("frame", array(framePitch, frameRoll));
        for (Part p : Part.values()) {
            Rotation r = rotations.get(p);
            if (!r.isZero()) o.add(p.key, array(r.x(), r.y(), r.z()));
        }
        if (!hidden.isEmpty()) {
            JsonArray h = new JsonArray();
            for (Part p : Part.values()) if (hidden.contains(p)) h.add(p.key);
            o.add("hidden", h);
        }
        return o;
    }

    /** Lenient: unknown keys are ignored, missing or malformed values take their default. */
    public static Pose fromJson(JsonObject o) {
        double[] offset = numbers(o.get("offset"), 3, new double[]{NEUTRAL.offsetX, NEUTRAL.offsetY, NEUTRAL.offsetZ});
        double[] frame = numbers(o.get("frame"), 2, new double[]{0, 0});
        double scale = 1;
        if (o.get("scale") instanceof JsonPrimitive p && p.isNumber()) scale = p.getAsDouble();
        EnumMap<Part, Rotation> rot = new EnumMap<>(Part.class);
        for (Part p : Part.values()) {
            double[] r = numbers(o.get(p.key), 3, new double[]{0, 0, 0});
            rot.put(p, new Rotation(r[0], r[1], r[2]));
        }
        EnumSet<Part> hidden = EnumSet.noneOf(Part.class);
        if (o.get("hidden") instanceof JsonArray a) {
            for (JsonElement e : a) {
                if (!e.isJsonPrimitive()) continue;
                Part p = Part.byKey(e.getAsString());
                if (p != null && p.hideable()) hidden.add(p);
            }
        }
        return new Pose(rot, hidden, offset[0], offset[1], offset[2], scale, frame[0], frame[1]);
    }

    private static JsonPrimitive number(double v) {
        // Whole numbers are written without ".0" to keep poses.json readable
        if (v == Math.rint(v) && Math.abs(v) < 1e9) return new JsonPrimitive((long) v);
        return new JsonPrimitive(Math.round(v * 1000) / 1000.0);
    }

    private static JsonArray array(double... values) {
        JsonArray a = new JsonArray();
        for (double v : values) a.add(number(v));
        return a;
    }

    private static double[] numbers(JsonElement e, int n, double[] fallback) {
        if (!(e instanceof JsonArray a) || a.size() != n) return fallback;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            JsonElement v = a.get(i);
            if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) return fallback;
            double d = v.getAsDouble();
            if (!Double.isFinite(d)) return fallback;
            out[i] = d;
        }
        return out;
    }

    /** Pose names are free text (accents, spaces) but trimmed, single-spaced and lower case. */
    public static String normalizeName(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
