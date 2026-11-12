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
 * The effect glasses: one item, {@code craftmaster:glasses}, worn on the head like a carved pumpkin; it puts a screen
 * effect on the wearer's own view (client: {@code client.fx.ScreenEffects}). The effect is the data component
 * {@link #SCREEN_FX} (a combination like "fisheye+masque_rond"), the lenses' colour ({@code minecraft:dyed_color})
 * tells which one it is, and the name says it ({@code minecraft:item_name}). The creative tab has one pair per effect
 * ({@link #stack}). Any item can carry an effect: {@code /give @s carved_pumpkin[craftmaster:screen_fx="sepia"]}.
 *
 * <p>The eleven glasses, masks and headsets of before (blurry_glasses...) are still registered, out of the tab, with
 * their effect, colour and name, drawn like the new glasses: in a player's inventory they turn into the new item.
 */
public final class FxContent {

    private FxContent() {}

    /** Screen effect of an item worn on the head ("" = none). */
    public static DataComponentType<String> SCREEN_FX;

    /** Kind (the old item id, used by /fx porter) → effect, in creative tab order. */
    public static final Map<String, String> WEARABLES = new LinkedHashMap<>();
    /** Kind → French name (server messages; the item names themselves are in the lang files). */
    public static final Map<String, String> FRENCH = new LinkedHashMap<>();
    /** Kind → lens colour (RGB), the colour code of the effect (docs/wiki/Effets.md). */
    public static final Map<String, Integer> COLORS = new LinkedHashMap<>();
    /** Kind → the old item, kept for old worlds. */
    public static final Map<String, Item> ITEMS = new LinkedHashMap<>();
    /** The one glasses item. */
    public static Item GLASSES;

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
        COLORS.put("blurry_glasses", 0x9CC8EC);       // light blue: blurred
        COLORS.put("sepia_glasses", 0xA8743C);        // brown: sepia
        COLORS.put("noir_blanc_glasses", 0x8E8E8E);   // grey: black and white
        COLORS.put("pixel_glasses", 0x5DBB3F);        // green: pixels
        COLORS.put("peephole_glasses", 0x26B4BE);     // cyan: peephole
        COLORS.put("vintage_glasses", 0xE38A2C);      // orange: old film
        COLORS.put("vhs_headset", 0x8B4FD6);          // purple: VHS
        COLORS.put("thermal_goggles", 0xD9362B);      // red: thermal
        COLORS.put("heart_glasses", 0xF08CBF);        // pink: dream
        COLORS.put("wobbly_glasses", 0xC23CB4);       // magenta: wobbly
        COLORS.put("binocular_goggles", 0xF2C93A);    // yellow: binoculars
    }

    private static Equippable head() {
        return Equippable.builder(EquipmentSlot.HEAD).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
                .setSwappable(true).setDispensable(true).setDamageOnHurt(false).build();
    }

    /** A pair of glasses of this kind (old item id), or null. */
    public static ItemStack stack(String kind) {
        String fx = WEARABLES.get(kind);
        if (fx == null || GLASSES == null) return null;
        ItemStack s = new ItemStack(GLASSES);
        s.set(SCREEN_FX, fx);
        s.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(COLORS.get(kind)));
        s.set(DataComponents.ITEM_NAME, net.minecraft.network.chat.Component.translatable("item.craftmaster." + kind));
        return s;
    }

    /** An old effect item: same look and effect; turns into the new glasses once in a player's inventory. */
    private static final class OldGlasses extends Item {
        private final String kind;

        OldGlasses(Properties props, String kind) {
            super(props);
            this.kind = kind;
        }

        @Override
        public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity owner, EquipmentSlot slot) {
            if (!(owner instanceof net.minecraft.world.entity.player.Player player)) return;
            ItemStack now = stack(kind);
            if (now == null) return;
            // Keep what the old stack carried on top (a binding curse put by /fx porter ... verrouille, a name)
            now.applyComponents(stack.getComponentsPatch());
            if (slot != null) player.setItemSlot(slot, now);
            else {
                var inv = player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i) == stack) inv.setItem(i, now);
            }
        }
    }

    public static void register() {
        SCREEN_FX = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CraftMaster.id("screen_fx"),
                DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
        ResourceKey<Item> glassesKey = ResourceKey.create(Registries.ITEM, CraftMaster.id("glasses"));
        GLASSES = Registry.register(BuiltInRegistries.ITEM, glassesKey, new Item(new Item.Properties().setId(glassesKey).stacksTo(1)
                .component(DataComponents.EQUIPPABLE, head())));
        for (Map.Entry<String, String> e : WEARABLES.entrySet()) {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CraftMaster.id(e.getKey()));
            // (No pumpkin mask: a vanilla carved pumpkin already gives that view)
            Item item = Registry.register(BuiltInRegistries.ITEM, key, new OldGlasses(new Item.Properties().setId(key).stacksTo(1)
                    .component(DataComponents.EQUIPPABLE, head()).component(SCREEN_FX, e.getValue())
                    .component(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(COLORS.get(e.getKey()))), e.getKey()));
            ITEMS.put(e.getKey(), item);
        }
        ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, CraftMaster.id("craftmaster"));
        CreativeModeTabEvents.modifyOutputEvent(tab).register(out ->
                WEARABLES.keySet().forEach(k -> out.accept(stack(k), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS)));
    }

    /** The effect a stack puts on its wearer's view ("" = none). */
    public static String effectOf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || SCREEN_FX == null) return "";
        String fx = stack.get(SCREEN_FX);
        return fx == null ? "" : fx;
    }

    /** French name of a wearable stack (by its effect; its item id if not one of ours). */
    public static String frenchName(ItemStack stack) {
        String fx = effectOf(stack);
        for (Map.Entry<String, String> e : WEARABLES.entrySet()) if (e.getValue().equals(fx)) return FRENCH.get(e.getKey());
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return FRENCH.getOrDefault(id, id);
    }

    /** A new pair of glasses by kind (the old item ids are suggested: blurry_glasses...), or null. */
    public static ItemStack wearable(String id) {
        String n = id.trim().toLowerCase(java.util.Locale.ROOT).replace("craftmaster:", "");
        return stack(n);
    }
}
