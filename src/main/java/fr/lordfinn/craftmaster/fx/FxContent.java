package fr.lordfinn.craftmaster.fx;

import com.mojang.serialization.Codec;
import fr.lordfinn.craftmaster.CraftMaster;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The wearable effect items (glasses, masks, headsets): worn on the head like a carved pumpkin, they put a screen
 * effect on the wearer's own view (client: {@code client.fx.ScreenEffects}). The effect is the data component
 * {@link #SCREEN_FX} (a combination like "fisheye+masque_rond"), so any item can carry one:
 * {@code /give @s carved_pumpkin[craftmaster:screen_fx="sepia"]} works as well.
 */
public final class FxContent {

    private FxContent() {}

    /** Screen effect of an item worn on the head ("" = none). */
    public static DataComponentType<String> SCREEN_FX;

    /** Item id → effect, in creative tab order. */
    public static final Map<String, String> WEARABLES = new LinkedHashMap<>();
    /** Item id → French name (server messages; the item names themselves are in the lang files). */
    public static final Map<String, String> FRENCH = new LinkedHashMap<>();
    /** Item id → item. */
    public static final Map<String, Item> ITEMS = new LinkedHashMap<>();

    static {
        WEARABLES.put("blurry_glasses", "flou");
        WEARABLES.put("sepia_glasses", "sepia");
        WEARABLES.put("noir_blanc_glasses", "noir_blanc");
        WEARABLES.put("pixel_glasses", "pixel");
        WEARABLES.put("peephole_glasses", "fisheye+masque_rond");
        WEARABLES.put("vintage_glasses", "vieux_film+vignette");
        WEARABLES.put("vhs_headset", "vhs");
        WEARABLES.put("thermal_goggles", "infrarouge");
        WEARABLES.put("heart_glasses", "reve");
        WEARABLES.put("wobbly_glasses", "ondule");
        WEARABLES.put("binocular_goggles", "jumelles");
        FRENCH.put("blurry_glasses", "lunettes floues");
        FRENCH.put("sepia_glasses", "lunettes sépia");
        FRENCH.put("noir_blanc_glasses", "lunettes noir et blanc");
        FRENCH.put("pixel_glasses", "lunettes pixel");
        FRENCH.put("peephole_glasses", "lunettes judas");
        FRENCH.put("vintage_glasses", "lunettes vintage");
        FRENCH.put("vhs_headset", "casque VHS");
        FRENCH.put("thermal_goggles", "lunettes thermiques");
        FRENCH.put("heart_glasses", "lunettes cœur");
        FRENCH.put("wobbly_glasses", "lunettes tourbillon");
        FRENCH.put("binocular_goggles", "jumelles frontales");
    }

    public static void register() {
        SCREEN_FX = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CraftMaster.id("screen_fx"),
                DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
        for (Map.Entry<String, String> e : WEARABLES.entrySet()) {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CraftMaster.id(e.getKey()));
            // (No pumpkin mask: a vanilla carved pumpkin already gives that view)
            Equippable head = Equippable.builder(EquipmentSlot.HEAD).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
                    .setSwappable(true).setDispensable(true).setDamageOnHurt(false).build();
            Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)
                    .component(DataComponents.EQUIPPABLE, head).component(SCREEN_FX, e.getValue())));
            ITEMS.put(e.getKey(), item);
        }
        ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, CraftMaster.id("craftmaster"));
        CreativeModeTabEvents.modifyOutputEvent(tab).register(out ->
                ITEMS.values().forEach(i -> out.accept(new ItemStack(i), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS)));
    }

    /** The effect a stack puts on its wearer's view ("" = none). */
    public static String effectOf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || SCREEN_FX == null) return "";
        String fx = stack.get(SCREEN_FX);
        return fx == null ? "" : fx;
    }

    /** French name of a wearable stack (its item id if not one of ours). */
    public static String frenchName(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return FRENCH.getOrDefault(id, id);
    }

    /** Wearable by item id or French-ish name ("lunettes floues" is not needed: the ids are suggested). */
    public static Item wearable(String id) {
        String n = id.trim().toLowerCase(java.util.Locale.ROOT).replace("craftmaster:", "");
        return ITEMS.get(n);
    }
}
