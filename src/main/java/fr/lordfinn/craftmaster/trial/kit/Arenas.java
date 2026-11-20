package fr.lordfinn.craftmaster.trial.kit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.JsonOps;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.show.Show;
import fr.lordfinn.craftmaster.show.ShowEvents;
import fr.lordfinn.craftmaster.trial.Judging;
import fr.lordfinn.craftmaster.trial.Stopwatch;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.BlockHitResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The trial sets ({@link Arena}): reference box copied once per contestant, reset, spawn and respawn, kit of items,
 * drawing field, signs, block-by-block comparison with the reference, and the links with the round (stopwatch at
 * the letter, timer and locked signs at the hidden part's reveal). Commands: {@code /cm arene}.
 */
public final class Arenas {

    private Arenas() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Arena> ARENAS = new LinkedHashMap<>();

    /** Who stands in a copy: the arena and the copy's contestant (null = the reference itself). */
    public record Owner(Arena arena, String player) {
        public int[] offset() {
            return player == null ? null : arena.copies.get(player);
        }
    }

    // ---------------------------------------------------------------- storage

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("craftmaster/arenas.json");
    }

    public static void load() {
        ARENAS.clear();
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            List<Arena> list = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), new TypeToken<List<Arena>>() {}.getType());
            if (list != null) for (Arena a : list) if (a != null && a.name != null) ARENAS.put(key(a.name), a);
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Arènes] {} illisible", f, e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(new ArrayList<>(ARENAS.values())), StandardCharsets.UTF_8);
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Arènes] écriture impossible", e);
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public static Arena get(String name) {
        return name == null ? null : ARENAS.get(key(name));
    }

    public static java.util.Collection<Arena> all() {
        return ARENAS.values();
    }

    public static Arena create(String name, ServerLevel level, BlockPos a, BlockPos b) {
        Arena ar = get(name);
        if (ar == null) {
            ar = new Arena();
            ar.name = name;
        }
        ar.world = level.dimension().identifier().toString();
        ar.x0 = Math.min(a.getX(), b.getX());
        ar.y0 = Math.min(a.getY(), b.getY());
        ar.z0 = Math.min(a.getZ(), b.getZ());
        ar.x1 = Math.max(a.getX(), b.getX());
        ar.y1 = Math.max(a.getY(), b.getY());
        ar.z1 = Math.max(a.getZ(), b.getZ());
        ARENAS.put(key(name), ar);
        save();
        return ar;
    }

    public static boolean remove(String name) {
        boolean was = ARENAS.remove(key(name)) != null;
        if (was) save();
        return was;
    }

    public static ServerLevel level(MinecraftServer server, Arena a) {
        Identifier id = Identifier.tryParse(a.world);
        ServerLevel l = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        return l != null ? l : server.overworld();
    }

    // ---------------------------------------------------------------- who is where

    /** The arena (and copy) at this position of this level, or null. */
    public static Owner ownerAt(Level level, BlockPos pos) {
        String dim = level.dimension().identifier().toString();
        for (Arena a : ARENAS.values()) {
            if (!a.world.equals(dim)) continue;
            if (a.contains(pos, null)) return new Owner(a, null);
            for (Map.Entry<String, int[]> c : a.copies.entrySet()) if (a.contains(pos, c.getValue())) return new Owner(a, c.getKey());
        }
        return null;
    }

    /**
     * May this player play here: their own copy, or the reference (an organiser trying the set, or a trial
     * played without copies)?
     */
    public static boolean mayUse(ServerPlayer p, Owner o) {
        if (o == null || o.player() == null) return true;
        String name = playerName(p);
        return o.player().equalsIgnoreCase(name);
    }

    /** The contestant's name (as in the cast), else the account's. */
    public static String playerName(ServerPlayer p) {
        String c = Judging.contestant(p);
        return c != null ? c : p.getGameProfile().name();
    }

    // ---------------------------------------------------------------- copies

    /** Gives each player a copy (side by side, after the existing ones); returns the newly assigned. */
    public static List<String> assign(Arena a, List<String> players) {
        List<String> added = new ArrayList<>();
        for (String p : players) {
            boolean known = a.copies.keySet().stream().anyMatch(k -> k.equalsIgnoreCase(p));
            if (known) continue;
            int i = 1;
            while (true) {
                int[] s = a.slot(i);
                boolean used = a.copies.values().stream().anyMatch(o -> o[0] == s[0] && o[1] == s[1] && o[2] == s[2]);
                if (!used) break;
                i++;
            }
            a.copies.put(p, a.slot(i));
            added.add(p);
        }
        save();
        return added;
    }

    /** Block flags of a copy: sent to the players, no shape or neighbour updates, nothing placed reacts. */
    private static final int NO_UPDATE = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;

    /**
     * Builds (or rebuilds) every copy from the reference: blocks with their contents (chests, barrels, shelves...),
     * entities (item frames, golem, flowers of the path, vase...), nothing else left inside, walls around.
     *
     * @return number of copies built
     */
    public static int rebuild(MinecraftServer server, Arena a) {
        return rebuild(server, a, null);
    }

    /** A rebuild waiting for the entities of its chunks (the copies far out are not always loaded). */
    private record Waiting(String arena, java.util.Collection<String> only, List<net.minecraft.world.level.ChunkPos> chunks, int since) {}

    private static final List<Waiting> WAITING = new ArrayList<>();
    private static boolean ticking;

    /**
     * The same for some contestants' copies only ({@code only} null = all). The chunks of the reference and of the
     * copies are loaded first, their entities with them: an old item frame still on disk would come back after the
     * new one is placed, fall off its block and drop its item on the floor, or the new one be lost (rooms beyond what
     * is kept loaded). When they are not all loaded yet, the rebuild waits for them (10 s at most) and the organisers
     * are told when it is done; it then returns the number of copies it will rebuild.
     */
    public static int rebuild(MinecraftServer server, Arena a, java.util.Collection<String> only) {
        ServerLevel level = level(server, a);
        List<net.minecraft.world.level.ChunkPos> chunks = chunks(a, only);
        boolean ready = true;
        for (net.minecraft.world.level.ChunkPos c : chunks) if (!level.areEntitiesLoaded(c.pack())) ready = false;
        if (ready) return rebuildNow(server, a, only);
        for (net.minecraft.world.level.ChunkPos c : chunks) level.getChunkSource().addTicketWithRadius(LaunchGate.TICKET, c, 0);
        WAITING.add(new Waiting(a.name, only, chunks, server.getTickCount()));
        if (!ticking) {
            ticking = true;
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(Arenas::waiting);
        }
        CraftMaster.LOG.info("[Arène] « {} » : remise à neuf dès que ses {} chunks sont chargés", a.name, chunks.size());
        int n = 0;
        for (String k : a.copies.keySet()) if (only == null || only.stream().anyMatch(w -> w.equalsIgnoreCase(k))) n++;
        return n;
    }

    private static void waiting(MinecraftServer server) {
        if (WAITING.isEmpty()) return;
        for (Waiting w : List.copyOf(WAITING)) {
            Arena a = get(w.arena());
            ServerLevel level = a == null ? null : level(server, a);
            boolean ready = level != null;
            if (level != null) for (net.minecraft.world.level.ChunkPos c : w.chunks()) if (!level.areEntitiesLoaded(c.pack())) ready = false;
            boolean late = server.getTickCount() - w.since() > 200;
            if (!ready && !late) continue;
            WAITING.remove(w);
            if (a == null || level == null) continue;
            int n = rebuildNow(server, a, w.only());
            for (net.minecraft.world.level.ChunkPos c : w.chunks()) level.getChunkSource().removeTicketWithRadius(LaunchGate.TICKET, c, 0);
            Finish.tellOrganisers(server, null, net.minecraft.network.chat.Component.literal("« " + a.name + " » : " + n + " copie(s) remise(s) à neuf"
                    + (late ? " (chargement lent : faite sans attendre plus)" : "") + ".").withStyle(net.minecraft.ChatFormatting.GRAY), null);
        }
    }

    /** The chunks of the reference and of the copies concerned. */
    private static List<net.minecraft.world.level.ChunkPos> chunks(Arena a, java.util.Collection<String> only) {
        List<int[]> offs = new ArrayList<>();
        offs.add(new int[]{0, 0, 0});
        for (Map.Entry<String, int[]> c : a.copies.entrySet())
            if (only == null || only.stream().anyMatch(w -> w.equalsIgnoreCase(c.getKey()))) offs.add(c.getValue());
        java.util.Set<net.minecraft.world.level.ChunkPos> out = new java.util.LinkedHashSet<>();
        int x0 = Math.min(a.x0, a.x1), x1 = Math.max(a.x0, a.x1), z0 = Math.min(a.z0, a.z1), z1 = Math.max(a.z0, a.z1);
        for (int[] o : offs)
            for (int cx = (x0 + o[0]) >> 4; cx <= (x1 + o[0]) >> 4; cx++)
                for (int cz = (z0 + o[2]) >> 4; cz <= (z1 + o[2]) >> 4; cz++) out.add(new net.minecraft.world.level.ChunkPos(cx, cz));
        return new ArrayList<>(out);
    }

    /** The copies built now (their entities known to be loaded, or waited for long enough). */
    private static int rebuildNow(MinecraftServer server, Arena a, java.util.Collection<String> only) {
        ServerLevel level = level(server, a);
        StructureTemplate t = new StructureTemplate();
        t.fillFromWorld(level, a.min(), new net.minecraft.core.Vec3i(a.sizeX(), a.sizeY(), a.sizeZ()), true, List.of(Blocks.STRUCTURE_VOID));
        int n = 0;
        for (Map.Entry<String, int[]> c : a.copies.entrySet()) {
            if (only != null && only.stream().noneMatch(w -> w.equalsIgnoreCase(c.getKey()))) continue;
            int[] off = c.getValue();
            for (Entity e : level.getEntities((Entity) null, a.box(off), e -> !(e instanceof Player))) e.discard();
            // Markers on the floor (no size) escape the box test: with their progress tags, a reset room would stay done
            net.minecraft.world.phys.AABB box = a.box(off);
            for (Entity e : level.getEntities(net.minecraft.world.entity.EntityTypes.MARKER, box.inflate(0.5), e -> e.getX() >= box.minX && e.getX() <= box.maxX
                    && e.getY() >= box.minY && e.getY() <= box.maxY && e.getZ() >= box.minZ && e.getZ() <= box.maxZ)) e.discard();
            BlockPos at = a.min().offset(off[0], off[1], off[2]);
            // No updates at all, like a WorldEdit paste: crops on stone, floating plants, unconnected panes stay as built
            t.placeInWorld(level, at, at, new StructurePlaceSettings().setKnownShape(true), level.getRandom(), NO_UPDATE);
            copyPassengers(level, a, off);
            copyMarkers(level, a, off);
            if (a.walls) walls(level, a, off);
            // Nothing lying on the floor after the copy (an old frame that fell off would have dropped its item)
            for (Entity e : level.getEntities(net.minecraft.world.entity.EntityTypes.ITEM, a.box(off), e -> true)) e.discard();
            check(server, level, a, c.getKey(), off);
            n++;
        }
        if (only != null) return n;
        Pickups.forget(a.name);
        Drawing.forget(a.name);
        DrawingImages.clear(a.name);
        GroundEnd.forget();
        Finish.reset();
        return n;
    }

    /** The kinds of entities a copy must have as many of as the reference (read when needed: the game's registries). */
    private static List<net.minecraft.world.entity.EntityType<?>> counted() {
        return List.of(net.minecraft.world.entity.EntityTypes.ITEM_FRAME, net.minecraft.world.entity.EntityTypes.GLOW_ITEM_FRAME,
                net.minecraft.world.entity.EntityTypes.PAINTING, net.minecraft.world.entity.EntityTypes.MANNEQUIN, net.minecraft.world.entity.EntityTypes.VILLAGER);
    }

    /** The copy's frames, paintings, mannequins and villagers counted against the reference; the organisers warned if they differ. */
    private static void check(MinecraftServer server, ServerLevel level, Arena a, String who, int[] off) {
        List<String> wrong = new ArrayList<>();
        for (net.minecraft.world.entity.EntityType<?> type : counted()) {
            int ref = level.getEntities(type, a.box(null), e -> true).size(), here = level.getEntities(type, a.box(off), e -> true).size();
            if (ref != here) wrong.add(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath() + " " + here + "/" + ref);
        }
        if (wrong.isEmpty()) return;
        String line = "⚠ « " + a.name + " », copie de " + who + " : " + String.join(", ", wrong) + " (copie/référence). /cm arene reset " + a.name + " pour la refaire.";
        CraftMaster.LOG.warn("[Arène] {}", line);
        Finish.tellOrganisers(server, null, net.minecraft.network.chat.Component.literal(line).withStyle(net.minecraft.ChatFormatting.RED), null);
    }

    /**
     * The riders too: a structure places each entity alone, so a seat ({@code item_display}, minecart, boat…) arrives
     * empty and whoever sat on it is lost (La pomme: the seated mannequins Remy, Lucie, Mireille, Gégé). Each mount of
     * the reference that carries someone gets, in the copy, the same riders (and theirs), saved and loaded again with
     * new UUIDs; a mount that already has its riders is left as it is.
     */
    private static void copyPassengers(ServerLevel level, Arena a, int[] off) {
        net.minecraft.world.phys.AABB ref = a.box(null);
        for (Entity mount : level.getEntities((Entity) null, ref, e -> !(e instanceof Player) && !e.isPassenger() && !e.getPassengers().isEmpty())) {
            double x = mount.getX() + off[0], y = mount.getY() + off[1], z = mount.getZ() + off[2];
            net.minecraft.world.phys.AABB near = new net.minecraft.world.phys.AABB(x - 0.3, y - 0.3, z - 0.3, x + 0.3, y + 0.3, z + 0.3);
            Entity copy = level.getEntities((Entity) null, near, e -> e.getType() == mount.getType() && !e.isPassenger()).stream()
                    .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(x, y, z))).orElse(null);
            if (copy == null || !copy.getPassengers().isEmpty()) continue;
            for (Entity rider : mount.getPassengers()) {
                net.minecraft.world.level.storage.TagValueOutput out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                        net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
                if (!rider.saveAsPassenger(out)) continue;
                Entity again = net.minecraft.world.entity.EntityType.loadEntityRecursive(
                        net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()),
                        level, net.minecraft.world.entity.EntitySpawnReason.STRUCTURE, e -> {
                            e.snapTo(e.getX() + off[0], e.getY() + off[1], e.getZ() + off[2], e.getYRot(), e.getXRot());
                            e.setUUID(java.util.UUID.randomUUID());
                            return e;
                        });
                if (again == null) continue;
                level.addFreshEntityWithPassengers(again);
                again.startRiding(copy, true, false);
            }
        }
    }

    /**
     * The reference's markers in the copy too (the structure copy leaves them out): a room's datapack runs its
     * mechanics from them (La pomme: {@code pomme_salle}). A tag ending in {@code _ref} marks the reference only.
     */
    private static void copyMarkers(ServerLevel level, Arena a, int[] off) {
        // A marker has no size: one lying on the room's floor (y = y0) is outside a box test, hence the margin
        net.minecraft.world.phys.AABB ref = a.box(null);
        for (Entity m : level.getEntities(net.minecraft.world.entity.EntityTypes.MARKER, ref.inflate(0.5), e -> e.getX() >= ref.minX && e.getX() <= ref.maxX
                && e.getY() >= ref.minY && e.getY() <= ref.maxY && e.getZ() >= ref.minZ && e.getZ() <= ref.maxZ)) {
            double x = m.getX() + off[0], y = m.getY() + off[1], z = m.getZ() + off[2];
            net.minecraft.world.phys.AABB here = new net.minecraft.world.phys.AABB(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5);
            if (!level.getEntities(net.minecraft.world.entity.EntityTypes.MARKER, here, e -> e.entityTags().equals(m.entityTags())).isEmpty()) continue;
            Entity copy = net.minecraft.world.entity.EntityTypes.MARKER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (copy == null) continue;
            copy.snapTo(x, y, z, m.getYRot(), m.getXRot());
            for (String tag : m.entityTags()) if (!tag.endsWith("_ref")) copy.addTag(tag);
            level.addFreshEntity(copy);
        }
    }

    /** Barrier blocks one block outside the copy (four sides and the ceiling), only where there is air. */
    private static void walls(ServerLevel level, Arena a, int[] off) {
        int x0 = a.x0 + off[0] - 1, x1 = a.x1 + off[0] + 1, z0 = a.z0 + off[2] - 1, z1 = a.z1 + off[2] + 1;
        int y0 = a.y0 + off[1], y1 = a.y1 + off[1] + 1;
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = y0; y <= y1; y++) {
                    boolean side = x == x0 || x == x1 || z == z0 || z == z1;
                    if (!side && y != y1) continue;
                    p.set(x, y, z);
                    if (level.getBlockState(p).isAir()) level.setBlock(p, barrier, NO_UPDATE);
                }
    }

    /** Removes a player's copy from the list (its blocks stay where they are). */
    public static boolean unassign(Arena a, String player) {
        boolean was = a.copies.keySet().removeIf(k -> k.equalsIgnoreCase(player));
        if (was) save();
        return was;
    }

    // ---------------------------------------------------------------- spawn, respawn, kit

    public static double[] spawn(Arena a, int[] off) {
        int dx = off == null ? 0 : off[0], dy = off == null ? 0 : off[1], dz = off == null ? 0 : off[2];
        if (a.hasSpawn) return new double[]{a.sx + dx, a.sy + dy, a.sz + dz};
        return new double[]{(a.x0 + a.x1) / 2.0 + 0.5 + dx, a.y1 + 1 + dy, (a.z0 + a.z1) / 2.0 + 0.5 + dz};
    }

    /** Sets the player's respawn point (forced: no bed needed) where they stand in a set. */
    public static void setRespawn(ServerPlayer p, ServerLevel level, double x, double y, double z, float yaw) {
        p.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), BlockPos.containing(x, y, z), yaw, 0f), true), false);
    }

    /**
     * Each player to their copy's spawn (respawn set there), with the kit. A player without copy goes to the
     * reference's spawn.
     *
     * @return the players placed
     */
    public static List<String> place(MinecraftServer server, Arena a, List<String> players) {
        ServerLevel level = level(server, a);
        List<String> done = new ArrayList<>();
        for (String name : players) {
            ServerPlayer p = server.getPlayerList().getPlayerByName(name);
            if (p == null) continue;
            int[] off = null;
            for (Map.Entry<String, int[]> c : a.copies.entrySet()) if (c.getKey().equalsIgnoreCase(name)) off = c.getValue();
            double[] s = spawn(a, off);
            p.teleportTo(level, s[0], s[1], s[2], Set.of(), a.syaw, 0f, true);
            setRespawn(p, level, s[0], s[1], s[2], a.syaw);
            if (a.clearInventory) p.getInventory().clearContent();
            for (ItemStack st : kit(server, a)) if (!p.getInventory().add(st)) p.spawnAtLocation(level, st);
            done.add(name);
        }
        return done;
    }

    public static List<ItemStack> kit(MinecraftServer server, Arena a) {
        List<ItemStack> out = new ArrayList<>();
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (JsonElement e : a.kit) ItemStack.CODEC.parse(ops, e).result().ifPresent(out::add);
        return out;
    }

    /** The kit becomes the items the organiser carries (inventory and off hand). */
    public static int setKit(ServerPlayer from, Arena a) {
        var ops = from.level().getServer().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        a.kit.clear();
        for (ItemStack st : from.getInventory().getNonEquipmentItems())
            if (!st.isEmpty()) ItemStack.CODEC.encodeStart(ops, st).result().ifPresent(a.kit::add);
        save();
        return a.kit.size();
    }

    // ---------------------------------------------------------------- signs

    /** Every sign of every copy: contestant → text (front side, lines joined); locks them if asked. */
    public static Map<String, String> signs(MinecraftServer server, Arena a, boolean lock) {
        ServerLevel level = level(server, a);
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> c : a.copies.entrySet()) {
            List<String> texts = new ArrayList<>();
            for (BlockEntity be : blockEntities(level, a, c.getValue())) {
                if (!(be instanceof SignBlockEntity sign)) continue;
                StringBuilder sb = new StringBuilder();
                for (Component line : sign.getFrontText().getMessages(false)) {
                    String s = line.getString().trim();
                    if (!s.isEmpty()) sb.append(sb.isEmpty() ? "" : " ").append(s);
                }
                if (!sb.isEmpty()) texts.add(sb.toString());
                if (lock && !sign.isWaxed()) {
                    sign.setWaxed(true);
                    BlockState st = level.getBlockState(sign.getBlockPos());
                    level.sendBlockUpdated(sign.getBlockPos(), st, st, Block.UPDATE_CLIENTS);
                }
            }
            out.put(c.getKey(), String.join(" / ", texts));
        }
        return out;
    }

    private static List<BlockEntity> blockEntities(ServerLevel level, Arena a, int[] off) {
        List<BlockEntity> out = new ArrayList<>();
        int x0 = a.x0 + off[0], x1 = a.x1 + off[0], z0 = a.z0 + off[2], z1 = a.z1 + off[2];
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                LevelChunk ch = level.getChunk(cx, cz);
                for (BlockEntity be : ch.getBlockEntities().values()) if (a.contains(be.getBlockPos(), off)) out.add(be);
            }
        return out;
    }

    /** Sends the signs' texts to the organisers ("Ensuri : girafe"). */
    public static void tellSigns(MinecraftServer server, Arena a, Map<String, String> texts, boolean locked) {
        Finish.tellOrganisers(server, null, Component.literal((locked ? "🔒 Panneaux verrouillés" : "Panneaux") + " de « " + a.name + " » :")
                .withStyle(ChatFormatting.GOLD), null);
        for (Map.Entry<String, String> e : texts.entrySet())
            Finish.tellOrganisers(server, null, Component.literal(" ").append(Finish.name(e.getKey()))
                    .append(Component.literal(" : " + (e.getValue().isEmpty() ? "(rien d'écrit)" : e.getValue())).withStyle(ChatFormatting.WHITE)),
                    a.name + " — " + e.getKey() + " : " + e.getValue());
    }

    // ---------------------------------------------------------------- comparison with the reference

    /**
     * Each copy against the reference, block by block (states only: the contents of chests, barrels, shelves and
     * the item frames don't count): share of the positions that match, among those where either is not air.
     */
    public static Map<String, Double> compare(MinecraftServer server, Arena a) {
        ServerLevel level = level(server, a);
        Map<String, Double> out = new LinkedHashMap<>();
        BlockPos.MutableBlockPos r = new BlockPos.MutableBlockPos(), c = new BlockPos.MutableBlockPos();
        for (Map.Entry<String, int[]> e : a.copies.entrySet()) {
            int[] o = e.getValue();
            long same = 0, total = 0;
            for (int x = a.x0; x <= a.x1; x++)
                for (int y = a.y0; y <= a.y1; y++)
                    for (int z = a.z0; z <= a.z1; z++) {
                        BlockState ref = level.getBlockState(r.set(x, y, z));
                        BlockState cop = level.getBlockState(c.set(x + o[0], y + o[1], z + o[2]));
                        if (ref.isAir() && cop.isAir()) continue;
                        // The bedrock floor and the barriers never change: they would only pad everyone's score
                        if (ref == cop && (ref.is(Blocks.BEDROCK) || ref.is(Blocks.BARRIER))) continue;
                        total++;
                        if (ref == cop) same++;
                    }
            out.put(e.getKey(), total == 0 ? 100.0 : Math.round(same * 1000.0 / total) / 10.0);
        }
        return out;
    }

    // ---------------------------------------------------------------- links with the round

    public static void register() {
        ShowEvents.register(new ShowEvents.Listener() {
            @Override
            public void onEnvelopeOpened(MinecraftServer server, ServerPlayer player, int season, String mancheId) {
                for (Arena a : ARENAS.values()) {
                    if (!a.chronoLettre || a.manche == null || !a.manche.equals(mancheId)) continue;
                    String c = Judging.contestant(player);
                    if (c == null || Finish.finished(c)) continue;
                    String known = Stopwatch.SHARED.find(c);
                    if (known != null && Stopwatch.SHARED.isRunning(known)) continue;
                    ActionExecutor.execute(server, List.of(Map.of("type", "stopwatch.start", "player", c)));
                }
            }
        });
    }

    /** A hidden part of a round's task was revealed (from the tasks): its arenas' timer and signs follow. */
    public static void onSecretShown(MinecraftServer server, int season, String mancheId) {
        for (Arena a : ARENAS.values()) {
            if (a.manche == null || !a.manche.equals(mancheId)) continue;
            if (a.panneauxSuite) tellSigns(server, a, signs(server, a, true), true);
            if (a.chronoSuite > 0) {
                fr.lordfinn.craftmaster.data.Manche m = Judging.currentManche();
                startTimer(server, m != null && m.id.equals(mancheId) ? m.name : a.name, a.chronoSuite);
            }
        }
    }

    /** The round's timer, started now (the shared voice channel closes with it). */
    public static void startTimer(MinecraftServer server, String name, int seconds) {
        Show.startTimer(server, name, seconds);
        Show.soundAll(server, SoundEvents.NOTE_BLOCK_BELL, 1f, 1.2f);
        if (FabricLoader.getInstance().isModLoaded("voicechat")) fr.lordfinn.craftmaster.compat.VoiceGroups.disband(server);
    }

    // ---------------------------------------------------------------- the drawing field

    /** Is this block (one of the copy's own field floor blocks) in the field of {@code o}'s copy? */
    public static boolean inField(Owner o, BlockPos p, boolean floorOnly) {
        Arena.Field f = o.arena().field;
        if (f == null) return false;
        int[] off = o.offset();
        int dx = off == null ? 0 : off[0], dy = off == null ? 0 : off[1], dz = off == null ? 0 : off[2];
        if (p.getX() < f.x0 + dx || p.getX() > f.x1 + dx || p.getZ() < f.z0 + dz || p.getZ() > f.z1 + dz) return false;
        return floorOnly ? p.getY() == f.y + dy : p.getY() > f.y + dy && p.getY() <= f.y + dy + 3;
    }

    public static BlockState block(String id, BlockState fallback) {
        Identifier i = Identifier.tryParse(id);
        if (i == null) return fallback;
        Block b = BuiltInRegistries.BLOCK.getValue(i);
        return b == Blocks.AIR ? fallback : b.defaultBlockState();
    }

    /** Banners planted in a drawing field: red starts the player's drawing, green ends it (from UseBlockCallback). */
    public static InteractionResult onUseBlock(Player player, Level level, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer sp) || player.isSpectator()) return InteractionResult.PASS;
        ItemStack st = player.getItemInHand(hand);
        boolean red = st.is(Items.BANNER.red()), green = st.is(Items.BANNER.green()) || st.is(Items.BANNER.lime());
        if (!red && !green) return InteractionResult.PASS;
        BlockPos at = level.getBlockState(hit.getBlockPos()).canBeReplaced() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
        if (hit.getDirection() != Direction.UP && !level.getBlockState(hit.getBlockPos()).canBeReplaced()) return InteractionResult.PASS;
        Owner o = ownerAt(level, at);
        if (o == null || o.arena().field == null || !inField(o, at, false) || !mayUse(sp, o)) return InteractionResult.PASS;
        if (red) Drawing.start(sp, o);
        else Drawing.stop(sp, o, false);
        return InteractionResult.PASS;
    }
}
