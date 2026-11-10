package fr.lordfinn.craftmaster.trial;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;

/**
 * Settings of a task prop ({@link TrialPropEntity}): how it looks (the item it shows is kept by the entity) and
 * what happens when a contestant uses it. Sent as JSON to the organiser's settings screen and back, saved in the
 * entity as the same JSON. Every value is clamped by {@link #sanitize()}, so a hand-made packet cannot break it.
 */
public final class PropConfig {

    // ------------------------------------------------------------ look
    /** Name shown above it (and used in the messages); empty = the item's name. */
    public String name = "";
    public boolean showName = true;
    /** Size: 1 = one block. */
    public float scale = 1f;
    /** Turn around the vertical axis and tilt forward, degrees. */
    public float yaw = 0f, pitch = 0f;
    /** Turns slowly on itself / floats up and down. */
    public boolean spin = false, bob = false;
    public boolean glow = false;

    // ------------------------------------------------------------ on use
    /** Message to the player who uses it ({@code {player}} = their name); empty = none. */
    public String message = "";
    /** The message goes to everyone instead. */
    public boolean messageToAll = false;
    /** Sound id ("minecraft:block.note_block.bell"); empty = none. */
    public String sound = "minecraft:block.note_block.bell";
    public float soundPitch = 1f;
    /** Item given ("minecraft:diamond"); empty = none. The exact stack (with its name...) is kept by the entity. */
    public String giveItem = "";
    public int giveCount = 1;
    /** A note opened on the player's screen (paper card); empty text = none. */
    public String noteTitle = "", noteText = "";
    /** Teleports the player there. */
    public boolean teleport = false;
    public double tpX, tpY, tpZ;
    public float tpYaw, tpPitch;
    /** A redstone pulse: a redstone block appears for {@link #pulseTicks} at this offset from the prop. */
    public boolean pulse = false;
    public int pulseDx = 0, pulseDy = -2, pulseDz = 0, pulseTicks = 10;
    /** Starts / stops the player's stopwatch (stopping records the time on the results sheet). */
    public boolean startAttempt = false, endAttempt = false;
    /** Tells everyone who touched it first. */
    public boolean announceFirst = false;

    // ------------------------------------------------------------ who / how often
    /** Each player can use it once. */
    public boolean oncePerPlayer = false;
    /** Seconds before the same player can use it again. */
    public float cooldown = 0.5f;
    /** Only the contestants of the season (not the audience) trigger it. */
    public boolean contestantsOnly = false;

    public PropConfig copy() {
        return fromJson(toJson());
    }

    /** Keeps every value in its range. */
    public PropConfig sanitize() {
        name = cut(name, 64);
        scale = clamp(scale, 0.1f, 8f);
        yaw = ((yaw % 360) + 360) % 360;
        pitch = clamp(pitch, -90f, 90f);
        message = cut(message, 256);
        sound = cut(sound, 128).toLowerCase(Locale.ROOT);
        soundPitch = clamp(soundPitch, 0.5f, 2f);
        giveItem = cut(giveItem, 128).toLowerCase(Locale.ROOT);
        giveCount = Math.max(1, Math.min(64 * 9, giveCount));
        noteTitle = cut(noteTitle, 64);
        noteText = cut(noteText, 2000);
        if (!Double.isFinite(tpX)) tpX = 0;
        if (!Double.isFinite(tpY)) tpY = 0;
        if (!Double.isFinite(tpZ)) tpZ = 0;
        tpY = Math.max(-2048, Math.min(4096, tpY));
        tpYaw = Float.isFinite(tpYaw) ? tpYaw : 0;
        tpPitch = clamp(tpPitch, -90f, 90f);
        pulseDx = Math.max(-16, Math.min(16, pulseDx));
        pulseDy = Math.max(-16, Math.min(16, pulseDy));
        pulseDz = Math.max(-16, Math.min(16, pulseDz));
        pulseTicks = Math.max(2, Math.min(200, pulseTicks));
        cooldown = clamp(cooldown, 0f, 3600f);
        return this;
    }

    private static float clamp(float v, float lo, float hi) {
        return Float.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : lo;
    }

    private static String cut(String s, int max) {
        if (s == null) return "";
        s = s.strip();
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ------------------------------------------------------------ JSON

    public String toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("showName", showName);
        o.addProperty("scale", scale);
        o.addProperty("yaw", yaw);
        o.addProperty("pitch", pitch);
        o.addProperty("spin", spin);
        o.addProperty("bob", bob);
        o.addProperty("glow", glow);
        o.addProperty("message", message);
        o.addProperty("messageToAll", messageToAll);
        o.addProperty("sound", sound);
        o.addProperty("soundPitch", soundPitch);
        o.addProperty("giveItem", giveItem);
        o.addProperty("giveCount", giveCount);
        o.addProperty("noteTitle", noteTitle);
        o.addProperty("noteText", noteText);
        o.addProperty("teleport", teleport);
        o.addProperty("tpX", tpX);
        o.addProperty("tpY", tpY);
        o.addProperty("tpZ", tpZ);
        o.addProperty("tpYaw", tpYaw);
        o.addProperty("tpPitch", tpPitch);
        o.addProperty("pulse", pulse);
        o.addProperty("pulseDx", pulseDx);
        o.addProperty("pulseDy", pulseDy);
        o.addProperty("pulseDz", pulseDz);
        o.addProperty("pulseTicks", pulseTicks);
        o.addProperty("startAttempt", startAttempt);
        o.addProperty("endAttempt", endAttempt);
        o.addProperty("announceFirst", announceFirst);
        o.addProperty("oncePerPlayer", oncePerPlayer);
        o.addProperty("cooldown", cooldown);
        o.addProperty("contestantsOnly", contestantsOnly);
        return o.toString();
    }

    /** Reads the JSON; missing or unreadable values keep their default. */
    public static PropConfig fromJson(String json) {
        PropConfig c = new PropConfig();
        JsonObject o;
        try {
            JsonElement e = JsonParser.parseString(json == null || json.isBlank() ? "{}" : json);
            o = e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            o = new JsonObject();
        }
        c.name = s(o, "name", c.name);
        c.showName = b(o, "showName", c.showName);
        c.scale = (float) d(o, "scale", c.scale);
        c.yaw = (float) d(o, "yaw", c.yaw);
        c.pitch = (float) d(o, "pitch", c.pitch);
        c.spin = b(o, "spin", c.spin);
        c.bob = b(o, "bob", c.bob);
        c.glow = b(o, "glow", c.glow);
        c.message = s(o, "message", c.message);
        c.messageToAll = b(o, "messageToAll", c.messageToAll);
        c.sound = s(o, "sound", c.sound);
        c.soundPitch = (float) d(o, "soundPitch", c.soundPitch);
        c.giveItem = s(o, "giveItem", c.giveItem);
        c.giveCount = (int) d(o, "giveCount", c.giveCount);
        c.noteTitle = s(o, "noteTitle", c.noteTitle);
        c.noteText = s(o, "noteText", c.noteText);
        c.teleport = b(o, "teleport", c.teleport);
        c.tpX = d(o, "tpX", c.tpX);
        c.tpY = d(o, "tpY", c.tpY);
        c.tpZ = d(o, "tpZ", c.tpZ);
        c.tpYaw = (float) d(o, "tpYaw", c.tpYaw);
        c.tpPitch = (float) d(o, "tpPitch", c.tpPitch);
        c.pulse = b(o, "pulse", c.pulse);
        c.pulseDx = (int) d(o, "pulseDx", c.pulseDx);
        c.pulseDy = (int) d(o, "pulseDy", c.pulseDy);
        c.pulseDz = (int) d(o, "pulseDz", c.pulseDz);
        c.pulseTicks = (int) d(o, "pulseTicks", c.pulseTicks);
        c.startAttempt = b(o, "startAttempt", c.startAttempt);
        c.endAttempt = b(o, "endAttempt", c.endAttempt);
        c.announceFirst = b(o, "announceFirst", c.announceFirst);
        c.oncePerPlayer = b(o, "oncePerPlayer", c.oncePerPlayer);
        c.cooldown = (float) d(o, "cooldown", c.cooldown);
        c.contestantsOnly = b(o, "contestantsOnly", c.contestantsOnly);
        return c.sanitize();
    }

    private static String s(JsonObject o, String k, String def) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static boolean b(JsonObject o, String k, boolean def) {
        try {
            return o.has(k) ? o.get(k).getAsBoolean() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static double d(JsonObject o, String k, double def) {
        try {
            return o.has(k) ? o.get(k).getAsDouble() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }
}
