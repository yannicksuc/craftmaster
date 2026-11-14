package fr.lordfinn.craftmaster.stage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.block.CurtainBlock;
import fr.lordfinn.craftmaster.curtain.Curtains;
import fr.lordfinn.craftmaster.data.BlockSpot;
import fr.lordfinn.craftmaster.media.MediaServer;
import fr.lordfinn.craftmaster.portrait.PortraitCommands;
import fr.lordfinn.craftmaster.portrait.PortraitEntity;
import fr.lordfinn.craftmaster.show.StageControl;
import fr.lordfinn.craftmaster.studio.ImageScreenEntity;
import fr.lordfinn.craftmaster.studio.SpotlightBlock;
import fr.lordfinn.craftmaster.studio.SpotlightBlockEntity;
import fr.lordfinn.craftmaster.studio.Spotlights;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The stage looks: saved from what the stage shows now, played back with a fade (spotlights fade and turn,
 * portraits are hoisted, curtains open or close, screens change). Their file also holds the cue lists ({@link Cues}).
 */
public final class Scenography {

    private Scenography() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Look.Book book = new Look.Book();

    static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("craftmaster/scenographie.json");
    }

    public static Look.Book book() {
        return book;
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) {
            book = new Look.Book();
            return;
        }
        try {
            Look.Book b = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Look.Book.class);
            book = b == null ? new Look.Book() : b;
            if (book.looks == null) book.looks = new LinkedHashMap<>();
            if (book.cues == null) book.cues = new LinkedHashMap<>();
            if (book.auto == null) book.auto = new LinkedHashMap<>();
            if (book.rounds == null) book.rounds = new LinkedHashMap<>();
            if (book.sequences == null) book.sequences = new LinkedHashMap<>();
            book.sequences.values().removeIf(java.util.Objects::isNull);
            book.sequences.values().forEach(Sequence::clean);
            if (book.plateau == null) book.plateau = new Look.Plateau();
            if (book.plateau.points == null) book.plateau.points = new LinkedHashMap<>();
            if (book.plateau.scene == null || book.plateau.scene.isBlank()) book.plateau.scene = "scene";
            if (book.plateau.publicZone == null || book.plateau.publicZone.isBlank()) book.plateau.publicZone = "public";
            if (book.plateau.maitre == null) book.plateau.maitre = "";
        } catch (Exception e) {
            CraftMaster.LOG.error("[Scénographie] {} illisible, mis de côté", f, e);
            try {
                Files.move(f, f.resolveSibling("scenographie.json.broken"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
            book = new Look.Book();
        }
    }

    public static void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("scenographie.json.tmp");
            Files.writeString(tmp, GSON.toJson(book), StandardCharsets.UTF_8);
            Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            CraftMaster.LOG.error("[Scénographie] Enregistrement impossible", e);
        }
    }

    /** "Plateau ouvert" → "plateau ouvert": names are kept in lower case, spaces allowed. */
    public static String key(String name) {
        return name == null ? "" : name.strip().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    // ---------------------------------------------------------------- saving a look

    /** The stage as it is now; {@code parts} = which parts (empty: all). */
    public static Look capture(MinecraftServer server, List<String> parts) {
        boolean all = parts == null || parts.isEmpty();
        Look look = new Look();
        if (all || parts.contains(Look.LIGHTS)) {
            look.spots = new ArrayList<>();
            for (SpotlightBlockEntity s : SpotlightBlockEntity.loaded()) {
                Look.Spot p = new Look.Spot();
                p.world = Busts.worldId(s.getLevel());
                p.x = s.getBlockPos().getX();
                p.y = s.getBlockPos().getY();
                p.z = s.getBlockPos().getZ();
                p.name = s.name();
                p.lit = s.lit();
                p.color = s.color();
                p.intensity = s.intensity();
                p.cone = s.cone();
                p.yaw = s.yaw();
                p.pitch = s.pitch();
                p.follow = s.follow();
                look.spots.add(p);
            }
        }
        if (all || parts.contains(Look.CURTAINS)) {
            look.curtains = new LinkedHashMap<>();
            for (Map.Entry<String, BlockSpot> e : CraftMaster.data().stage().curtains.entrySet()) {
                Boolean open = curtainOpen(server, e.getValue());
                if (open != null) look.curtains.put(e.getKey(), open);
            }
        }
        if (all || parts.contains(Look.PORTRAITS)) {
            look.portraits = new LinkedHashMap<>();
            for (ServerLevel level : server.getAllLevels())
                for (PortraitEntity p : level.getEntities(EntityTypeTest.forClass(PortraitEntity.class), e -> !e.isRemoved()))
                    look.portraits.put(p.getUUID().toString(), p.hangSetting());
        }
        if (all || parts.contains(Look.SCREENS)) {
            look.screens = new LinkedHashMap<>();
            for (ImageScreenEntity s : MediaServer.screens(server))
                if (!s.screenName().isEmpty()) look.screens.put(key(s.screenName()), s.url());
        }
        return look;
    }

    private static Boolean curtainOpen(MinecraftServer server, BlockSpot b) {
        ServerLevel level = Busts.level(server, b.world());
        BlockPos pos = new BlockPos(b.x(), b.y(), b.z());
        if (level == null || !level.isLoaded(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CurtainBlock)) return null;
        return Curtains.isHeadingOpen(level, pos, state);
    }

    /** Saves the stage as it is now under {@code name}. */
    public static String saveLook(MinecraftServer server, String name, List<String> parts, Double fade) {
        String k = key(name);
        if (k.isEmpty()) return "Donne un nom à l'ambiance.";
        Look look = capture(server, parts);
        Look old = book.looks.get(k);
        look.fade = fade != null ? fade : old != null ? old.fade : 2;
        book.looks.put(k, look);
        save();
        return "Ambiance « " + k + " » " + (old == null ? "enregistrée" : "remplacée") + " : " + look.describe() + ".";
    }

    // ---------------------------------------------------------------- playing a look

    /** Plays the look {@code name} (fade: its own when null). */
    public static String playLook(MinecraftServer server, String name, Double fade) {
        Look look = book.looks.get(key(name));
        if (look == null) return "Ambiance « " + key(name) + " » inconnue (" + (book.looks.isEmpty() ? "aucune enregistrée" : String.join(", ", book.looks.keySet())) + ").";
        double f = fade != null ? fade : look.fade;
        List<String> done = new ArrayList<>();
        if (look.spots != null) {
            int n = 0;
            for (Look.Spot p : look.spots) {
                ServerLevel level = Busts.level(server, p.world);
                BlockPos pos = new BlockPos(p.x, p.y, p.z);
                if (level == null || !level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof SpotlightBlockEntity s)) continue;
                Spotlights.apply(s, new Spotlights.Settings(p.lit, p.color, p.intensity, p.cone, p.follow == null ? "" : p.follow,
                        null, p.follow == null || p.follow.isEmpty() ? p.yaw : null, p.follow == null || p.follow.isEmpty() ? p.pitch : null), f);
                n++;
            }
            done.add(n + " projecteur(s)");
        }
        if (look.curtains != null) {
            int n = 0;
            for (Map.Entry<String, Boolean> e : look.curtains.entrySet()) {
                BlockSpot b = CraftMaster.data().stage().curtains.get(e.getKey());
                if (b == null) continue;
                Boolean now = curtainOpen(server, b);
                if (now == null || now.equals(e.getValue())) continue;
                if (StageControl.moveCurtain(server, e.getKey(), e.getValue()) == null) n++;
            }
            if (n > 0) done.add(n + " rideau(x)");
        }
        if (look.portraits != null) {
            int n = 0;
            for (ServerLevel level : server.getAllLevels())
                for (PortraitEntity p : level.getEntities(EntityTypeTest.forClass(PortraitEntity.class), e -> !e.isRemoved())) {
                    Float h = look.portraits.get(p.getUUID().toString());
                    if (h == null || h == p.hangSetting()) continue;
                    PortraitCommands.hoist(p, h, Math.max(0.5, f));
                    n++;
                }
            if (n > 0) done.add(n + " tableau(x)");
        }
        if (look.screens != null) {
            int n = 0;
            for (ImageScreenEntity s : MediaServer.screens(server)) {
                String url = look.screens.get(key(s.screenName()));
                if (url == null || url.equals(s.url())) continue;
                s.setUrl(url);
                n++;
            }
            if (n > 0) done.add(n + " écran(s)");
        }
        CraftMaster.LOG.info("[Scénographie] Ambiance « {} » ({})", key(name), String.join(", ", done));
        return "Ambiance « " + key(name) + " »" + (f > 0 ? " en " + Look.secondsText(f) : "") + " : " + (done.isEmpty() ? "rien à changer" : String.join(", ", done)) + ".";
    }

    public static String deleteLook(String name) {
        if (book.looks.remove(key(name)) == null) return "Ambiance « " + key(name) + " » inconnue.";
        save();
        return "Ambiance « " + key(name) + " » supprimée.";
    }

    static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Switches every spotlight off and closes nothing: the "noir" a cue list can end on. */
    public static String blackout(MinecraftServer server, double fade) {
        return Spotlights.apply(server, Spotlights.ALL, new Spotlights.Settings(false, null, null, null, null, null, null, null), fade);
    }

    /** True if a spotlight block is still there (for tests of a saved look). */
    static boolean isSpotlight(BlockState state) {
        return state.getBlock() instanceof SpotlightBlock;
    }
}
