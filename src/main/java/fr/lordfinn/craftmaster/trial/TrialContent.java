package fr.lordfinn.craftmaster.trial;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.trial.block.BalanceBlock;
import fr.lordfinn.craftmaster.trial.block.BigRedButtonBlock;
import fr.lordfinn.craftmaster.trial.block.BuzzerBlock;
import fr.lordfinn.craftmaster.trial.block.PlinthBlock;
import fr.lordfinn.craftmaster.trial.block.PlinthBlockEntity;
import fr.lordfinn.craftmaster.trial.item.BoardItem;
import fr.lordfinn.craftmaster.trial.item.CounterItem;
import fr.lordfinn.craftmaster.trial.item.MeasuringTapeItem;
import fr.lordfinn.craftmaster.trial.item.PropWandItem;
import fr.lordfinn.craftmaster.trial.item.StopwatchItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Blocks, items and entities of the tasks (chantier H): task props, buzzers and big red button, judging tools,
 * drawing boards, display plinth. Added to the CraftMaster creative tab. The costume trunk and the red card were
 * taken out (LordFinn's feedback): old worlds load them as a plain chest / nothing ({@link #removed()}).
 */
public final class TrialContent {

    private TrialContent() {}

    public static Block BIG_RED_BUTTON, BALANCE, PLINTH;
    public static final Map<DyeColor, BuzzerBlock> BUZZERS = new LinkedHashMap<>();
    public static BlockEntityType<PlinthBlockEntity> PLINTH_ENTITY;

    public static Item PROP_WAND, JUDGE_STOPWATCH, MEASURING_TAPE, TALLY_COUNTER;
    public static final Map<BoardEntity.Variant, Item> BOARD_ITEMS = new EnumMap<>(BoardEntity.Variant.class);

    public static EntityType<TrialPropEntity> TRIAL_PROP;
    public static EntityType<BoardEntity> BOARD;

    private static final List<Item> TAB = new ArrayList<>();

    public static void register() {
        PROP_WAND = item("prop_wand", PropWandItem::new, new Item.Properties().stacksTo(1));
        BIG_RED_BUTTON = block("big_red_button", BigRedButtonBlock::new,
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.5f).sound(SoundType.METAL).noOcclusion());
        for (DyeColor color : DyeColor.values()) {
            BUZZERS.put(color, block(color.getName() + "_buzzer", BuzzerBlock::new,
                    BlockBehaviour.Properties.of().mapColor(color.getMapColor()).strength(1f).sound(SoundType.METAL).noOcclusion()
                            .lightLevel(s -> s.getValue(BuzzerBlock.LIT) ? 12 : 0)));
        }
        JUDGE_STOPWATCH = item("judge_stopwatch", StopwatchItem::new, new Item.Properties().stacksTo(1));
        MEASURING_TAPE = item("measuring_tape", MeasuringTapeItem::new, new Item.Properties().stacksTo(1));
        TALLY_COUNTER = item("tally_counter", CounterItem::new, new Item.Properties().stacksTo(1));
        BALANCE = block("balance", BalanceBlock::new,
                BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(2f).sound(SoundType.METAL).noOcclusion());
        for (BoardEntity.Variant v : BoardEntity.Variant.values())
            BOARD_ITEMS.put(v, item(v.id, p -> new BoardItem(v, p), new Item.Properties().stacksTo(16)));
        PLINTH = block("display_plinth", PlinthBlock::new,
                BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(2f).sound(SoundType.STONE).noOcclusion());

        PLINTH_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, CraftMaster.id("display_plinth"),
                new BlockEntityType<>(PlinthBlockEntity::new, Set.of(PLINTH)));
        removed();

        ResourceKey<EntityType<?>> propKey = ResourceKey.create(Registries.ENTITY_TYPE, CraftMaster.id("trial_prop"));
        TRIAL_PROP = Registry.register(BuiltInRegistries.ENTITY_TYPE, propKey,
                EntityType.Builder.<TrialPropEntity>of(TrialPropEntity::new, MobCategory.MISC)
                        .sized(1f, 1f).noLootTable().clientTrackingRange(10).updateInterval(20).build(propKey));
        ResourceKey<EntityType<?>> boardKey = ResourceKey.create(Registries.ENTITY_TYPE, CraftMaster.id("board"));
        BOARD = Registry.register(BuiltInRegistries.ENTITY_TYPE, boardKey,
                EntityType.Builder.<BoardEntity>of(BoardEntity::new, MobCategory.MISC)
                        .sized(1f, 1f).noLootTable().clientTrackingRange(10).updateInterval(Integer.MAX_VALUE).build(boardKey));

        ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, CraftMaster.id("craftmaster"));
        CreativeModeTabEvents.modifyOutputEvent(tab).register(out -> TAB.forEach(i -> out.accept(new ItemStack(i), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS)));
    }

    /** Old worlds: the costume trunk becomes a plain (empty) chest, the red card disappears. */
    private static void removed() {
        net.minecraft.resources.Identifier trunk = CraftMaster.id("costume_chest"), chest = net.minecraft.resources.Identifier.parse("minecraft:chest");
        BuiltInRegistries.BLOCK.addAlias(trunk, chest);
        BuiltInRegistries.ITEM.addAlias(trunk, chest);
        BuiltInRegistries.BLOCK_ENTITY_TYPE.addAlias(trunk, chest);
        BuiltInRegistries.ITEM.addAlias(CraftMaster.id("red_card"), net.minecraft.resources.Identifier.parse("minecraft:air"));
    }

    private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties props) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, CraftMaster.id(name));
        B block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, CraftMaster.id(name));
        TAB.add(Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())));
        return block;
    }

    private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CraftMaster.id(name));
        Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
        TAB.add(item);
        return item;
    }
}
