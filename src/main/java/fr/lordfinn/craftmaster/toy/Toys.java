package fr.lordfinn.craftmaster.toy;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.trial.Judging;
import fr.lordfinn.craftmaster.trial.ResultSheet;
import fr.lordfinn.craftmaster.trial.TrialLog;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Task toys: small objects that live in the world (rubber ducks, balls, bean bags, fragile eggs, golf balls,
 * balloons) and the things that judge them (target containers that count what lands in them, a golf hole, a
 * graduated glass and tank). Everything is in this package so it stays apart from the rest of the mod; one call
 * from {@code CraftMaster#onInitialize} wires it.
 */
public final class Toys {

    private Toys() {}

    public static final Map<ToyKind, ToyItem> TOY_ITEMS = new EnumMap<>(ToyKind.class);
    public static Item GOLF_CLUB, BALLOON;
    public static Block BUCKET_TARGET, BIN_TARGET, MUG_TARGET, GOLF_HOLE, MEASURING_GLASS, MEASURING_TANK;
    public static BlockEntityType<CatchTargetBlockEntity> TARGET_ENTITY;
    public static BlockEntityType<MeasuringGlassBlockEntity> GLASS_ENTITY;
    public static EntityType<ToyEntity> TOY;
    public static EntityType<BalloonEntity> BALLOON_ENTITY;
    public static SoundEvent SQUEAK, POP, PUFF;

    /** What the players did with the toys since the last {@code /craftMasterToys reset} (memory only). */
    public static final ToyTally TALLY = new ToyTally();

    /** The creative tab entries (stacks can only be made once the game is loaded). */
    private static final List<Supplier<ItemStack>> TAB = new ArrayList<>();

    public static void register() {
        SQUEAK = sound("toy.squeak");
        POP = sound("toy.pop");
        PUFF = sound("toy.puff");

        for (ToyKind k : ToyKind.values()) {
            ToyItem item = item(k.id, p -> new ToyItem(k, p), new Item.Properties().stacksTo(k == ToyKind.EGG ? 16 : 64));
            TOY_ITEMS.put(k, item);
            TAB.add(() -> new ItemStack(item));
        }
        // A few ready-made colours of ducks and balls (any colour: craft it with a dye)
        for (int dye : new int[]{14, 11, 13, 6, 0, 15}) TAB.add(() -> dyed(TOY_ITEMS.get(ToyKind.DUCK), dye));
        for (int dye : new int[]{4, 11, 13}) TAB.add(() -> dyed(TOY_ITEMS.get(ToyKind.BALL), dye));
        TAB.add(() -> {
            ItemStack shell = new ItemStack(TOY_ITEMS.get(ToyKind.EGG));
            setFragility(shell, 0);
            return shell;
        });

        BALLOON = item("balloon", BalloonItem::new, new Item.Properties().stacksTo(16));
        TAB.add(() -> new ItemStack(BALLOON));
        for (int dye : new int[]{11, 4, 13, 6}) TAB.add(() -> dyed(BALLOON, dye));
        GOLF_CLUB = item("golf_club", GolfClubItem::new, GolfClubItem.properties());
        TAB.add(() -> new ItemStack(GOLF_CLUB));

        GOLF_HOLE = block("golf_hole", GolfHoleBlock::new,
                BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).strength(0.8f).sound(SoundType.GRASS));
        BUCKET_TARGET = block("bucket_target", p -> new CatchTargetBlock(CatchTargetBlock.Shape.BUCKET, p),
                BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1f).sound(SoundType.METAL).noOcclusion().forceSolidOn());
        BIN_TARGET = block("bin_target", p -> new CatchTargetBlock(CatchTargetBlock.Shape.BIN, p),
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(1.5f).sound(SoundType.METAL).noOcclusion().forceSolidOn());
        MUG_TARGET = block("mug_target", p -> new CatchTargetBlock(CatchTargetBlock.Shape.MUG, p),
                BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.6f).sound(SoundType.DECORATED_POT).noOcclusion().forceSolidOn());
        MEASURING_GLASS = block("measuring_glass", p -> new MeasuringGlassBlock(MeasuringGlassBlock.Size.GLASS, p),
                BlockBehaviour.Properties.of().strength(0.4f).sound(SoundType.GLASS).noOcclusion().forceSolidOn());
        MEASURING_TANK = block("measuring_tank", p -> new MeasuringGlassBlock(MeasuringGlassBlock.Size.TANK, p),
                BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.GLASS).noOcclusion().forceSolidOn());

        TARGET_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, CraftMaster.id("catch_target"),
                new BlockEntityType<>(CatchTargetBlockEntity::new, Set.of(BUCKET_TARGET, BIN_TARGET, MUG_TARGET)));
        GLASS_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, CraftMaster.id("measuring_glass"),
                new BlockEntityType<>(MeasuringGlassBlockEntity::new, Set.of(MEASURING_GLASS, MEASURING_TANK)));

        ResourceKey<EntityType<?>> toyKey = ResourceKey.create(Registries.ENTITY_TYPE, CraftMaster.id("toy"));
        TOY = Registry.register(BuiltInRegistries.ENTITY_TYPE, toyKey,
                EntityType.Builder.<ToyEntity>of(ToyEntity::new, MobCategory.MISC)
                        .sized(0.4f, 0.4f).noLootTable().clientTrackingRange(8).updateInterval(1).build(toyKey));
        ResourceKey<EntityType<?>> balloonKey = ResourceKey.create(Registries.ENTITY_TYPE, CraftMaster.id("balloon"));
        BALLOON_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, balloonKey,
                EntityType.Builder.<BalloonEntity>of(BalloonEntity::new, MobCategory.MISC)
                        .sized(1f, 1f).noLootTable().clientTrackingRange(8).updateInterval(2).build(balloonKey));

        ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, CraftMaster.id("craftmaster"));
        CreativeModeTabEvents.modifyOutputEvent(tab).register(out -> TAB.forEach(s -> out.accept(s.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS)));

        // A dispenser throws the toys (machines that launch balls at the contestants)
        for (ToyItem item : TOY_ITEMS.values()) DispenserBlock.registerBehavior(item, new DefaultDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack stack) {
                Direction facing = source.state().getValue(DispenserBlock.FACING);
                Position at = DispenserBlock.getDispensePosition(source);
                ToyEntity toy = ToyEntity.create(source.level(), stack, new Vec3(at.x(), at.y() - item.kind().height / 2, at.z()), facing.toYRot());
                double speed = item.kind().maxThrowSpeed * 0.7;
                toy.setDeltaMovement(facing.getStepX() * speed, facing.getStepY() * speed + 0.12, facing.getStepZ() * speed);
                source.level().addFreshEntity(toy);
                stack.shrink(1);
                return stack;
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> ToyCommands.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(Toys::tickPulses);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            endPulses(server);
            TALLY.reset();
        });
    }

    private static SoundEvent sound(String name) {
        return Registry.register(BuiltInRegistries.SOUND_EVENT, CraftMaster.id(name), SoundEvent.createVariableRangeEvent(CraftMaster.id(name)));
    }

    private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, CraftMaster.id(name));
        Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, CraftMaster.id(name));
        Item item = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        TAB.add(() -> new ItemStack(item));
        return block;
    }

    private static <I extends Item> I item(String name, Function<Item.Properties, I> factory, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CraftMaster.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
    }

    // ---------------------------------------------------------------- item data

    private static ItemStack dyed(Item item, int dye) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.DYED_COLOR, new DyedItemColor(ToyPhysics.DYE_RGB[dye]));
        return s;
    }

    /** The colour of a dyeable toy or balloon (its kind's default if not dyed). */
    public static int color(ItemStack stack, int fallback) {
        DyedItemColor c = stack.get(DataComponents.DYED_COLOR);
        return c == null ? fallback : c.rgb();
    }

    /**
     * The dyeable toys and the balloon have one painted texture per dye colour: one dyed with several dyes (an
     * in-between colour) is given the nearest of the 16. Returns true if it changed.
     */
    public static boolean snapDyeColour(ItemStack stack) {
        boolean dyeable = stack.getItem() instanceof ToyItem t ? t.kind().dyeable : stack.is(BALLOON);
        if (!dyeable) return false;
        DyedItemColor c = stack.get(DataComponents.DYED_COLOR);
        if (c == null) return false;
        int rgb = ToyPhysics.DYE_RGB[ToyPhysics.nearestDye(c.rgb())];
        if ((c.rgb() & 0xFFFFFF) == rgb) return false;
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
        return true;
    }

    public static CompoundTag data(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d == null ? new CompoundTag() : d.copyTag();
    }

    /** Egg: the highest fall it survives, in blocks (0 = breaks at the least shock). */
    public static double fragility(ItemStack stack) {
        return data(stack).getDoubleOr("Fragility", 2.0);
    }

    public static void setFragility(ItemStack stack, double blocks) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putDouble("Fragility", Math.max(0, blocks)));
    }

    /**
     * The French name of a toy for the server's messages: its custom name if renamed, else the kind and colour
     * ("canard en plastique rouge"). The server has no French translations, so the names are built here.
     */
    public static String frName(ItemStack stack) {
        if (stack.has(DataComponents.CUSTOM_NAME)) return stack.getHoverName().getString();
        if (stack.getItem() instanceof ToyItem t) {
            ToyKind k = t.kind();
            if (k == ToyKind.EGG && fragility(stack) <= 0) return "œuf à coquille fine";
            if (!k.dyeable || !stack.has(DataComponents.DYED_COLOR)) return k.fr;
            return k.fr + " " + ToyPhysics.colourFr(ToyPhysics.nearestDye(color(stack, k.defaultColor)), k.female);
        }
        if (stack.is(BALLOON)) {
            return stack.has(DataComponents.DYED_COLOR) ? "ballon " + ToyPhysics.colourFr(ToyPhysics.nearestDye(color(stack, 0)), false) : "ballon";
        }
        // The game's own objects that land in the targets most often (the server only knows English names)
        if (stack.is(net.minecraft.world.item.Items.SNOWBALL)) return "boule de neige";
        if (stack.is(net.minecraft.world.item.Items.EGG)) return "œuf";
        if (stack.is(net.minecraft.world.item.Items.SLIME_BALL)) return "boule de slime";
        return stack.getHoverName().getString();
    }

    // ---------------------------------------------------------------- messages

    /** Organisers online (and in the same level if {@code near} is given, within 96 blocks). */
    public static List<ServerPlayer> organisers(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (Permissions.isAdmin(p)) out.add(p);
        return out;
    }

    /** A line in every organiser's chat (and in the trial log). */
    public static void tellOrganisers(MinecraftServer server, Component line) {
        TrialLog.add(server, line.getString());
        for (ServerPlayer p : organisers(server)) p.sendSystemMessage(line);
    }

    /**
     * A result for one player, as a button in the organisers' chat that writes it on the current round's sheet
     * ("[noter 7,4 blocs]"). Nothing if no round is running or the player is not a contestant.
     */
    public static MutableComponent noteButton(String player, double value, ResultSheet.Kind kind, String label) {
        String contestant = null;
        for (String c : Judging.contestants()) if (c.equalsIgnoreCase(player)) contestant = c;
        if (contestant == null || Judging.currentManche() == null) return Component.empty();
        String cmd = "/craftMasterResult set " + Judging.quote(contestant) + " " + String.format(Locale.ROOT, "%.2f", value) + " " + kind.id;
        String who = contestant;
        return Component.literal(" [noter " + label + "]").withStyle(s -> s.withColor(ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent.RunCommand(cmd))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Écrire " + ResultSheet.format(kind, value) + " pour " + who
                        + " sur la feuille de « " + Judging.currentManche().name + " »"))));
    }

    /** The name a player is known by in the show (the contestant's spelling if they are one). */
    public static String who(ServerPlayer p) {
        String c = Judging.contestant(p);
        return c != null ? c : p.getGameProfile().name();
    }

    /** Contestants count; organisers testing their set-up do not (unless they are a contestant). */
    public static boolean counts(ServerPlayer p) {
        return Judging.contestant(p) != null || !Permissions.isAdmin(p);
    }

    // ---------------------------------------------------------------- redstone pulses (balloons)

    private record Pulse(ServerLevel level, BlockPos pos) {}

    private static final Map<Pulse, Integer> PULSES = new LinkedHashMap<>();

    /** A redstone block for a moment, only where there is air (it never replaces a block of the set). */
    public static boolean pulse(ServerLevel level, BlockPos pos, int ticks) {
        if (!level.getBlockState(pos).isAir()) return false;
        level.setBlock(pos, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        PULSES.put(new Pulse(level, pos.immutable()), level.getServer().getTickCount() + ticks);
        return true;
    }

    private static void tickPulses(MinecraftServer server) {
        if (PULSES.isEmpty()) return;
        int now = server.getTickCount();
        for (Iterator<Map.Entry<Pulse, Integer>> it = PULSES.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (now < e.getValue()) continue;
            if (e.getKey().level().getBlockState(e.getKey().pos()).is(Blocks.REDSTONE_BLOCK))
                e.getKey().level().setBlock(e.getKey().pos(), Blocks.AIR.defaultBlockState(), 3);
            it.remove();
        }
    }

    private static void endPulses(MinecraftServer server) {
        PULSES.forEach((p, t) -> {
            if (p.level().getBlockState(p.pos()).is(Blocks.REDSTONE_BLOCK)) p.level().setBlock(p.pos(), Blocks.AIR.defaultBlockState(), 3);
        });
        PULSES.clear();
    }
}
