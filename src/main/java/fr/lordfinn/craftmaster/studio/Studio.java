package fr.lordfinn.craftmaster.studio;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.block.Shapes4;
import fr.lordfinn.craftmaster.registry.ModContent;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.Set;

/**
 * The studio set (chantier F, docs/wiki/Plateau.md): throne, lights and spotlights, giant timer, studio screens
 * (/image), contestants' desks, prize table, gong and bells, "EN DIRECT" sign and stanchions. Registered
 * after {@link ModContent} (same creative tab).
 */
public final class Studio {

    private Studio() {}

    // Seats
    public static FurnitureSeatBlock THRONE;
    // Lights
    public static LampBlock FLOOR_LAMP, DESK_LAMP, CEILING_PENDANT;
    public static SpotlightBlock SPOTLIGHT;
    public static OnAirSignBlock ON_AIR_SIGN;
    // Show
    public static TimerDisplayBlock TIMER_DISPLAY;
    public static TimerTabletBlock TIMER_TABLET;
    public static DeskBlock CONTESTANT_DESK;
    public static PrizeTableBlock PRIZE_TABLE;
    public static GongBlock GONG;
    public static DeskBellBlock DESK_BELL;
    public static StanchionBlock STANCHION;
    public static Item STUDIO_SCREEN_ITEM;

    public static EntityType<ImageScreenEntity> STUDIO_SCREEN;
    public static BlockEntityType<SpotlightBlockEntity> SPOTLIGHT_BE;
    public static BlockEntityType<TimerDisplayBlockEntity> TIMER_DISPLAY_BE;
    public static BlockEntityType<TimerTabletBlockEntity> TIMER_TABLET_BE;
    public static BlockEntityType<DeskBlockEntity> DESK_BE;
    public static BlockEntityType<PrizeTableBlockEntity> PRIZE_TABLE_BE;
    public static BlockEntityType<GongBlockEntity> GONG_BE;
    public static BlockEntityType<StanchionBlockEntity> STANCHION_BE;

    /** Sounds a resource pack can replace (assets/craftmaster/sounds.json). */
    public static Holder<SoundEvent> GONG_SOUND, DESK_BELL_SOUND;

    public static void register() {
        GONG_SOUND = sound("studio.gong");
        DESK_BELL_SOUND = sound("studio.desk_bell");

        // ------------------------------------------------------------ seats (facing north, backrest at south)
        // LordFinn's own throne model (Furniture): 1.7 blocks wide, 4.5 tall; the sitter sits at 20 px (on the cushion)
        THRONE = ModContent.block("throne", p -> new FurnitureSeatBlock(p, 20, Shapes4.of(
                        new double[]{0, 0, 0, 16, 15, 16}, new double[]{0, 15, 14, 16, 16, 16})),
                props(MapColor.GOLD, 3f).sound(SoundType.WOOD).noOcclusion());

        // ------------------------------------------------------------ lights
        FLOOR_LAMP = ModContent.block("floor_lamp", p -> new LampBlock(p, Shapes4.of(
                        new double[]{4, 0, 4, 12, 2, 12}, new double[]{7, 2, 7, 9, 22, 9}, new double[]{3, 22, 3, 13, 31, 13}), false),
                lamp(MapColor.GOLD, 15));
        DESK_LAMP = ModContent.block("desk_lamp", p -> new LampBlock(p, Shapes4.of(new double[]{3, 0, 4, 13, 10, 12}), false),
                lamp(MapColor.COLOR_GREEN, 12));
        CEILING_PENDANT = ModContent.block("ceiling_pendant", p -> new LampBlock(p, Shapes4.of(new double[]{3, 1, 3, 13, 16, 13}), true),
                lamp(MapColor.GOLD, 15));
        SPOTLIGHT = ModContent.block("spotlight", SpotlightBlock::new,
                props(MapColor.COLOR_BLACK, 2f).sound(SoundType.METAL).noOcclusion().lightLevel(s -> s.getValue(SpotlightBlock.LIT) ? 6 : 0));
        ON_AIR_SIGN = ModContent.block("on_air_sign", OnAirSignBlock::new,
                props(MapColor.COLOR_RED, 1f).sound(SoundType.GLASS).noOcclusion().lightLevel(s -> s.getValue(OnAirSignBlock.LIT) ? 10 : 0));

        // ------------------------------------------------------------ show
        TIMER_DISPLAY = ModContent.block("timer_display", TimerDisplayBlock::new,
                props(MapColor.COLOR_BLACK, 2f).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 8));
        // Timer tablet: a framed digital timer, placed like a sign, held up like in Taskmaster
        TIMER_TABLET = ModContent.block("timer_tablet", TimerTabletBlock::new,
                props(MapColor.COLOR_BLACK, 1f).sound(SoundType.WOOD).noOcclusion().noCollision());
        STUDIO_SCREEN_ITEM = ModContent.item("studio_screen", ImageScreenItem::new, new Item.Properties().stacksTo(16));
        CONTESTANT_DESK = ModContent.block("contestant_desk", DeskBlock::new, props(MapColor.COLOR_RED, 2.5f).sound(SoundType.WOOD).noOcclusion());
        DESK_BELL = ModContent.block("desk_bell", DeskBellBlock::new, props(MapColor.GOLD, 1f).sound(SoundType.METAL).noOcclusion());
        GONG = ModContent.block("gong", GongBlock::new, props(MapColor.GOLD, 3f).sound(SoundType.WOOD).noOcclusion());
        PRIZE_TABLE = ModContent.block("prize_table", PrizeTableBlock::new, props(MapColor.COLOR_RED, 2f).sound(SoundType.WOOL).noOcclusion());
        STANCHION = ModContent.block("stanchion", StanchionBlock::new, props(MapColor.GOLD, 2f).sound(SoundType.METAL).noOcclusion());

        SPOTLIGHT_BE = blockEntity("spotlight", SpotlightBlockEntity::new, SPOTLIGHT);
        TIMER_DISPLAY_BE = blockEntity("timer_display", TimerDisplayBlockEntity::new, TIMER_DISPLAY);
        TIMER_TABLET_BE = blockEntity("timer_tablet", TimerTabletBlockEntity::new, TIMER_TABLET);
        DESK_BE = blockEntity("contestant_desk", DeskBlockEntity::new, CONTESTANT_DESK);
        PRIZE_TABLE_BE = blockEntity("prize_table", PrizeTableBlockEntity::new, PRIZE_TABLE);
        GONG_BE = blockEntity("gong", GongBlockEntity::new, GONG);
        STANCHION_BE = blockEntity("stanchion", StanchionBlockEntity::new, STANCHION);

        ResourceKey<EntityType<?>> screenKey = ResourceKey.create(Registries.ENTITY_TYPE, CraftMaster.id("studio_screen"));
        // LordFinn's furniture from the server (after the throne: they share its block entity type)
        Furniture.register();

        STUDIO_SCREEN = Registry.register(BuiltInRegistries.ENTITY_TYPE, screenKey,
                EntityType.Builder.<ImageScreenEntity>of(ImageScreenEntity::new, MobCategory.MISC)
                        .sized(1f, 1f).noLootTable().clientTrackingRange(10).updateInterval(10).build(screenKey));

        removed();
        StudioNetwork.register();
        CommandRegistrationCallback.EVENT.register((d, ctx, sel) -> StudioCommands.register(d));
        ServerTickEvents.END_SERVER_TICK.register(StudioTimer::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> StudioTimer.onJoin(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> StudioTimer.reset());
    }

    /**
     * Pieces taken out of the set (LordFinn's feedback): worlds that still hold them load without errors. The trophy
     * becomes a player head (LordFinn's trophy is a golden frog head, a skinned player head), the assistant's
     * armchair and the sofa disappear; the podium steps become quartz (1st and 2nd: a block, 3rd: a slab). Blocks and
     * items alike (in chests, inventories, item frames).
     */
    private static void removed() {
        alias("podium_first", "minecraft:quartz_block");
        alias("podium_second", "minecraft:quartz_block");
        alias("podium_third", "minecraft:quartz_slab");
        alias("trophy", "minecraft:player_head");
        alias("assistant_chair", "minecraft:air");
        alias("sofa", "minecraft:air");
    }

    private static void alias(String old, String now) {
        net.minecraft.resources.Identifier from = CraftMaster.id(old), to = net.minecraft.resources.Identifier.parse(now);
        BuiltInRegistries.BLOCK.addAlias(from, to);
        BuiltInRegistries.ITEM.addAlias(from, to);
    }

    private static BlockBehaviour.Properties props(MapColor color, float strength) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(strength, strength * 2);
    }

    private static BlockBehaviour.Properties lamp(MapColor color, int light) {
        return props(color, 1f).sound(SoundType.LANTERN).noOcclusion().lightLevel(s -> s.getValue(LampBlock.LIT) ? light : 0);
    }

    private static <T extends BlockEntity> BlockEntityType<T> blockEntity(String name, BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, CraftMaster.id(name), new BlockEntityType<>(factory, Set.of(blocks)));
    }

    private static Holder<SoundEvent> sound(String name) {
        return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, CraftMaster.id(name), SoundEvent.createVariableRangeEvent(CraftMaster.id(name)));
    }
}
