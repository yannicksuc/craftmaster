package fr.lordfinn.craftmaster.task;

import fr.lordfinn.craftmaster.CraftMaster;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** Blocks, items and data components of the tasks, envelopes and hidden notes (chantier D). */
public final class TaskContent {

    private TaskContent() {}

    public static Item ENVELOPE, WAX_SEAL, NOTE, NOTE_BOTTLE, UV_LAMP;
    /** Envelope lying on a table; a sheet pinned on a face of a block. */
    public static EnvelopeBlock ENVELOPE_BLOCK;
    public static PinnedNoteBlock PINNED_NOTE;
    public static BlockEntityType<EnvelopeBlockEntity> ENVELOPE_BLOCK_ENTITY;

    /** @param tab adds an item to the CraftMaster creative tab */
    public static void register(Consumer<ItemStack> tab) {
        TaskComponents.register();
        ENVELOPE_BLOCK = block("envelope_block", EnvelopeBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                .strength(0.1f).sound(SoundType.WOOL).noCollision().noOcclusion().pushReaction(PushReaction.POPPED));
        PINNED_NOTE = block("pinned_note", PinnedNoteBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                .strength(0.1f).sound(SoundType.WOOL).noCollision().noOcclusion().pushReaction(PushReaction.POPPED).replaceable());
        ENVELOPE_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, CraftMaster.id("envelope_block"),
                new BlockEntityType<>(EnvelopeBlockEntity::new, Set.of(ENVELOPE_BLOCK)));

        ENVELOPE = item("envelope", EnvelopeItem::new, new Item.Properties().stacksTo(1));
        WAX_SEAL = item("wax_seal", WaxSealItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
        NOTE = item("note", NoteItem::new, new Item.Properties().stacksTo(1));
        NOTE_BOTTLE = item("note_bottle", NoteBottleItem::new, new Item.Properties().stacksTo(1));
        UV_LAMP = item("uv_lamp", UvLampItem::new, new Item.Properties().stacksTo(1));

        // A sealed free-text envelope and a red seal in the tab; notes come from /craftMasterNote (they need a text)
        tab.accept(TaskComponents.setEnvelope(new ItemStack(ENVELOPE), TaskComponents.Envelope.BLANK));
        ItemStack seal = new ItemStack(WAX_SEAL);
        TaskComponents.setSealColor(seal, WaxColor.RED);
        tab.accept(seal);
        tab.accept(new ItemStack(UV_LAMP));
    }

    private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties props) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, CraftMaster.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
    }

    private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CraftMaster.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
    }
}
