package fr.lordfinn.craftmaster.bust;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.mixin.DisplayAccessor;
import fr.lordfinn.craftmaster.mixin.TextDisplayAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Busts: player heads placed in the world that wear the skin of the player at a given rank of a season.
 * The mod adds a floating label (rank, name, points) above each one.
 */
public final class Busts {

    /** Every label carries this tag, plus "craftmaster.bust.SEASON.RANK". */
    public static final String TAG = "craftmaster.bust";

    private Busts() {}

    public static boolean isHead(BlockState s) {
        return s.is(Blocks.PLAYER_HEAD) || s.is(Blocks.PLAYER_WALL_HEAD);
    }

    /** Dimension id written for new busts. */
    public static String worldId(Level level) {
        return level.dimension().identifier().toString();
    }

    /** Accepts dimension ids and the plugin's Bukkit world names (world, world_nether, world_the_end). */
    public static ServerLevel level(MinecraftServer server, String world) {
        if (world == null) return null;
        Identifier id;
        if (world.contains(":")) id = Identifier.tryParse(world);
        else if (world.endsWith("_nether")) id = Level.NETHER.identifier();
        else if (world.endsWith("_the_end")) id = Level.END.identifier();
        else if (world.equals("world")) id = Level.OVERWORLD.identifier();
        else id = Identifier.tryParse("minecraft:" + world);
        if (id == null) return null;
        ServerLevel l = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        // Bukkit's overworld name is the server's level-name, whatever it is
        return l != null || world.contains(":") ? l : server.getLevel(Level.OVERWORLD);
    }

    public static void updateAll(MinecraftServer server) {
        CraftMasterData data = CraftMaster.data();
        if (data == null) return;
        for (int season : data.bustSeasons())
            for (int rank : data.bustRanks(season)) update(server, season, rank);
    }

    public static void update(MinecraftServer server, int season, int rank) {
        CraftMasterData data = CraftMaster.data();
        CraftMasterData.BustPos bp = data.bust(season, rank);
        if (bp == null) return;
        ServerLevel level = level(server, bp.world());
        if (level == null) {
            CraftMaster.LOG.warn("[Busts] Monde introuvable pour saison {} rang {} : {}", season, rank, bp.world());
            return;
        }
        BlockPos pos = new BlockPos(bp.x(), bp.y(), bp.z());
        String player = data.playerAtRank(season, rank);

        BlockState state = level.getBlockState(pos);
        if (isHead(state) && level.getBlockEntity(pos) instanceof SkullBlockEntity skull) {
            setHeadOwner(level, pos, state, skull, player);
        } else {
            CraftMaster.LOG.info("[Busts] Pas de tête en {} pour saison {} rang {}", pos.toShortString(), season, rank);
        }
        updateLabel(level, pos, state, season, rank, player, player == null ? 0 : data.total(season, player));
    }

    /** The skin is looked up by the players' own game from the name, like a head given with /give. */
    private static void setHeadOwner(ServerLevel level, BlockPos pos, BlockState state, SkullBlockEntity skull, String player) {
        DataComponentMap.Builder b = DataComponentMap.builder();
        for (TypedDataComponent<?> c : skull.collectComponents())
            if (c.type() != DataComponents.PROFILE) copy(b, c);
        if (player != null) b.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(player));
        skull.applyComponents(b.build(), DataComponentPatch.EMPTY);
        skull.setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
    }

    private static <T> void copy(DataComponentMap.Builder b, TypedDataComponent<T> c) {
        b.set(c.type(), c.value());
    }

    // ---------------------------------------------------------------- labels

    private static String tag(int season, int rank) {
        return TAG + "." + season + "." + rank;
    }

    private static double labelY(BlockPos pos, BlockState state) {
        // Floor heads are 8 px tall, wall heads sit 4 px higher
        return pos.getY() + (state.is(Blocks.PLAYER_WALL_HEAD) ? 1.0 : 0.75);
    }

    public static Component labelText(int rank, String player, int total) {
        ChatFormatting color = switch (rank) {
            case 1 -> ChatFormatting.GOLD;
            case 2 -> ChatFormatting.GRAY;
            case 3 -> ChatFormatting.RED;
            default -> ChatFormatting.WHITE;
        };
        MutableComponent c = Component.literal("#" + rank + " ").withStyle(color, ChatFormatting.BOLD);
        if (player == null) return c.append(Component.literal("—").withStyle(ChatFormatting.DARK_GRAY));
        return c.append(Component.literal(player).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\n" + total + " pts").withStyle(ChatFormatting.YELLOW));
    }

    private static void updateLabel(ServerLevel level, BlockPos pos, BlockState state, int season, int rank, String player, int total) {
        // Entities load after blocks: when they are not there yet, onEntityLoad refreshes the label instead
        if (!level.areEntitiesLoaded(ChunkPos.pack(pos))) return;
        String tag = tag(season, rank);
        List<Display.TextDisplay> found = level.getEntitiesOfClass(Display.TextDisplay.class,
                new AABB(pos).inflate(2), e -> e.entityTags().contains(tag));
        Display.TextDisplay label = found.isEmpty() ? null : found.getFirst();
        for (int i = 1; i < found.size(); i++) found.get(i).discard();
        if (label == null) {
            label = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.COMMAND);
            if (label == null) return;
            label.addTag(TAG);
            label.addTag(tag);
            ((DisplayAccessor) label).craftmaster$setBillboardConstraints(Display.BillboardConstraints.CENTER);
            ((TextDisplayAccessor) label).craftmaster$setBackgroundColor(0x60000000);
            label.snapTo(pos.getX() + 0.5, labelY(pos, state), pos.getZ() + 0.5);
            level.addFreshEntity(label);
        }
        ((TextDisplayAccessor) label).craftmaster$setText(labelText(rank, player, total));
    }

    /** Removes the label of a bust that is no longer registered. */
    public static void removeLabel(MinecraftServer server, CraftMasterData.BustPos bp, int season, int rank) {
        ServerLevel level = level(server, bp.world());
        if (level == null) return;
        BlockPos pos = new BlockPos(bp.x(), bp.y(), bp.z());
        if (!level.areEntitiesLoaded(ChunkPos.pack(pos))) return;
        String tag = tag(season, rank);
        level.getEntitiesOfClass(Display.TextDisplay.class, new AABB(pos).inflate(2), e -> e.entityTags().contains(tag))
                .forEach(Entity::discard);
    }

    /** A label coming back with its chunk: refresh its text, or remove it if its bust was deleted meanwhile. */
    public static void onEntityLoad(Entity entity, ServerLevel level) {
        if (!(entity instanceof Display.TextDisplay label) || !label.entityTags().contains(TAG)) return;
        CraftMasterData data = CraftMaster.data();
        if (data == null) return;
        for (String t : label.entityTags()) {
            if (!t.startsWith(TAG + ".")) continue;
            String[] p = t.substring(TAG.length() + 1).split("\\.");
            if (p.length != 2) continue;
            try {
                int season = Integer.parseInt(p[0]);
                int rank = Integer.parseInt(p[1]);
                CraftMasterData.BustPos bp = data.bust(season, rank);
                if (bp == null || level(level.getServer(), bp.world()) != level) {
                    level.getServer().execute(label::discard);
                    return;
                }
                String player = data.playerAtRank(season, rank);
                ((TextDisplayAccessor) label).craftmaster$setText(labelText(rank, player, player == null ? 0 : data.total(season, player)));
            } catch (NumberFormatException ignored) {}
            return;
        }
    }
}
