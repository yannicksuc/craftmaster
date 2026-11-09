package fr.lordfinn.craftmaster.registry;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.block.ChairBlock;
import fr.lordfinn.craftmaster.block.CurtainBlock;
import fr.lordfinn.craftmaster.entity.SeatEntity;
import fr.lordfinn.craftmaster.item.CurtainRemoteItem;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Blocks, items and entities of the mod. Block/item/model names are listed in the README. */
public final class ModContent {

    /** Wood types of the chairs; the models reuse the vanilla planks and logs. */
    public static final List<String> CHAIR_WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "bamboo", "crimson", "warped");

    public static final Map<String, ChairBlock> CHAIRS = new LinkedHashMap<>();
    public static final Map<DyeColor, CurtainBlock> CURTAINS = new LinkedHashMap<>();
    public static Item CURTAIN_REMOTE;
    public static EntityType<SeatEntity> SEAT;

    private static final List<Item> TAB_ITEMS = new ArrayList<>();

    private ModContent() {}

    public static void register() {
        for (String wood : CHAIR_WOODS) {
            SoundType sound = switch (wood) {
                case "cherry" -> SoundType.CHERRY_WOOD;
                case "bamboo" -> SoundType.BAMBOO_WOOD;
                case "crimson", "warped" -> SoundType.NETHER_WOOD;
                default -> SoundType.WOOD;
            };
            CHAIRS.put(wood, block(wood + "_chair", ChairBlock::new,
                    BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 3.0f).sound(sound).noOcclusion().ignitedByLava()));
        }
        for (DyeColor color : DyeColor.values()) {
            CURTAINS.put(color, block(color.getName() + "_curtain", CurtainBlock::new,
                    BlockBehaviour.Properties.of().mapColor(color.getMapColor()).strength(0.8f).sound(SoundType.WOOL)
                            .noCollision().noOcclusion().ignitedByLava().pushReaction(PushReaction.POPPED)));
        }
        CURTAIN_REMOTE = item("curtain_remote", CurtainRemoteItem::new, new Item.Properties().stacksTo(1));

        ResourceKey<EntityType<?>> seatKey = ResourceKey.create(Registries.ENTITY_TYPE, CraftMaster.id("seat"));
        SEAT = Registry.register(BuiltInRegistries.ENTITY_TYPE, seatKey,
                EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
                        .sized(0.25f, 0.25f).noSummon().noSave().clientTrackingRange(10).build(seatKey));

        ResourceKey<CreativeModeTab> tabKey = ResourceKey.create(Registries.CREATIVE_MODE_TAB, CraftMaster.id("craftmaster"));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.craftmaster"))
                .icon(() -> new ItemStack(CURTAINS.get(DyeColor.RED)))
                .displayItems((params, out) -> TAB_ITEMS.forEach(out::accept))
                .build());
    }

    private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties props) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, CraftMaster.id(name));
        B block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, CraftMaster.id(name));
        TAB_ITEMS.add(Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())));
        return block;
    }

    private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CraftMaster.id(name));
        Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
        TAB_ITEMS.add(item);
        return item;
    }
}
