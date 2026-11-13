package fr.lordfinn.craftmaster.wardrobe;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The wardrobe: change into a complete outfit in one click (or a single piece of one, to mix outfits) and back
 * into your own clothes in one click (docs/wiki/GardeRobe.md). Works with any armour; made for the clothes of
 * Selfexpression (optional).
 */
public final class Wardrobes {

    private Wardrobes() {}

    public static WardrobeBlock WARDROBE;
    public static Item WARDROBE_ITEM;
    public static BlockEntityType<WardrobeBlockEntity> WARDROBE_ENTITY;
    /** Clothes kept aside, outfit worn, favourites, on the player (persistent, kept through death). */
    public static AttachmentType<WardrobeState> STATE;

    /** Marks a copy given by a show wardrobe (custom data key). */
    public static final String COSTUME_TAG = "craftmaster_costume";

    @SuppressWarnings("deprecation")
    public static void register() {
        ResourceKey<net.minecraft.world.level.block.Block> key = ResourceKey.create(Registries.BLOCK, CraftMaster.id("wardrobe"));
        WARDROBE = Registry.register(BuiltInRegistries.BLOCK, key, new WardrobeBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BROWN).strength(2.5f).sound(SoundType.WOOD).noOcclusion().ignitedByLava().setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, CraftMaster.id("wardrobe"));
        WARDROBE_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(WARDROBE, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        WARDROBE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, CraftMaster.id("wardrobe"),
                new BlockEntityType<>(WardrobeBlockEntity::new, Set.of(WARDROBE)));
        STATE = AttachmentRegistry.create(CraftMaster.id("wardrobe"), b -> b.persistent(WardrobeState.CODEC).copyOnDeath());

        ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, CraftMaster.id("craftmaster"));
        CreativeModeTabEvents.modifyOutputEvent(tab).register(out ->
                out.accept(new ItemStack(WARDROBE_ITEM), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));

        WardrobeNetwork.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> Catalogue.load(server.registryAccess()));
    }

    // ---------------------------------------------------------------- costume copies

    public static boolean isCostume(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().contains(COSTUME_TAG);
    }

    public static ItemStack mark(ItemStack s) {
        ItemStack c = s.copy();
        CustomData.update(DataComponents.CUSTOM_DATA, c, t -> t.putBoolean(COSTUME_TAG, true));
        return c;
    }

    public static ItemStack unmark(ItemStack s) {
        ItemStack c = s.copy();
        if (isCostume(c)) {
            CustomData.update(DataComponents.CUSTOM_DATA, c, t -> t.remove(COSTUME_TAG));
            CustomData d = c.get(DataComponents.CUSTOM_DATA);
            if (d != null && d.copyTag().isEmpty()) c.remove(DataComponents.CUSTOM_DATA);
        }
        return c;
    }

    public static final Dressing.Kind<ItemStack> KIND = new Dressing.Kind<>() {
        @Override public ItemStack none() { return ItemStack.EMPTY; }
        @Override public boolean empty(ItemStack s) { return s.isEmpty(); }
        @Override public boolean costume(ItemStack s) { return isCostume(s); }
        @Override public ItemStack costumeCopy(ItemStack s) { return mark(s); }
    };

    /** The outfit of {@code w} with the same pieces (name and costume marks ignored), or null. */
    public static Outfit find(WardrobeBlockEntity w, List<ItemStack> pieces) {
        String key = Outfit.key(pieces);
        for (Outfit o : w.outfits()) if (o.key().equals(key)) return o;
        return null;
    }

    // ---------------------------------------------------------------- the player

    public static WardrobeState state(ServerPlayer p) {
        WardrobeState s = p.getAttached(STATE);
        return s == null ? WardrobeState.NONE : s;
    }

    private static void setState(ServerPlayer p, WardrobeState s) {
        if (s.equals(WardrobeState.NONE)) p.removeAttached(STATE);
        else p.setAttached(STATE, s);
    }

    public static List<ItemStack> worn(ServerPlayer p) {
        List<ItemStack> out = new ArrayList<>();
        for (EquipmentSlot slot : Outfit.SLOTS) out.add(p.getItemBySlot(slot).copy());
        return out;
    }

    private static void apply(ServerPlayer p, Dressing.Result<ItemStack> r) {
        for (int i = 0; i < Outfit.SLOTS.length; i++) p.setItemSlot(Outfit.SLOTS[i], r.worn().get(i).copy());
        for (ItemStack s : r.toInventory()) give(p, s);
        p.inventoryMenu.broadcastChanges();
    }

    private static void give(ServerPlayer p, ItemStack s) {
        if (!s.isEmpty() && !p.getInventory().add(s)) p.drop(s, false/*$ drop_prediction {*/, net.minecraft.util.Prediction.SERVER_ONLY/*$}*/);
    }

    /** Costume copies left in the inventory (taken off by hand) go back to the wardrobe. */
    private static void purgeCopies(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (isCostume(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
    }

    private static String own(ServerPlayer p) {
        return "Affaires de " + p.getGameProfile().name();
    }

    private static void sound(ServerPlayer p) {
        p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.9f, 1f);
    }

    static void say(ServerPlayer p, String key, Object... args) {
        p.sendOverlayMessage(Component.translatable("message.craftmaster.wardrobe." + key, args).withStyle(ChatFormatting.GOLD));
    }

    // ---------------------------------------------------------------- actions

    /** Puts on outfit {@code index} of {@code w}: whole ({@code piece} < 0) or only that piece (0 head … 3 feet). */
    public static void wear(ServerPlayer p, WardrobeBlockEntity w, int index, int piece) {
        Outfit o = w.get(index);
        if (o == null) return;
        boolean whole = piece < 0 || piece > 3;
        if (!whole && o.pieces().get(piece).isEmpty()) return;
        boolean[] mask = whole ? Dressing.ALL : Dressing.only(piece);
        WardrobeState st = state(p);
        boolean ownThings = o.name().equals(own(p));
        if (w.show()) {
            var r = Dressing.putOnCopy(KIND, worn(p), st.saved().orElse(null), o.pieces(), mask);
            purgeCopies(p);
            apply(p, r);
            setState(p, st.withDressing(Optional.of(r.saved()), whole ? o.key() : WardrobeState.MIX));
        } else {
            var r = Dressing.swap(KIND, worn(p), o.pieces(), mask);
            apply(p, r);
            if (whole) {
                String stored = st.wearing().isEmpty() || st.inCostume() || st.wearing().equals(WardrobeState.MIX) ? own(p)
                        : w.outfits().stream().filter(x -> x.key().equals(st.wearing())).map(Outfit::name).findFirst().orElse(own(p));
                if (Dressing.allEmpty(KIND, r.outfit())) w.remove(index);
                else w.set(index, Outfit.of(stored, r.outfit()));
                setState(p, st.withDressing(st.saved(), ownThings ? "" : o.key()));
            } else {
                if (Dressing.allEmpty(KIND, r.outfit())) w.remove(index);
                else w.set(index, Outfit.of(o.name(), r.outfit(), o.tags()));
                setState(p, st.withDressing(st.saved(), WardrobeState.MIX));
            }
        }
        sound(p);
        if (ownThings && whole) say(p, "own");
        else if (whole) say(p, "wearing", o.displayName());
        else say(p, "wearing_piece", o.pieces().get(piece).getHoverName());
    }

    /**
     * Back into your own clothes: a show costume ends (it is remembered: {@link #switchOutfit} puts it back on);
     * in a storage wardrobe, swaps with "Affaires de <you>".
     */
    public static void takeBack(ServerPlayer p, WardrobeBlockEntity w) {
        WardrobeState st = state(p);
        if (st.inCostume()) {
            List<ItemStack> worn = worn(p);
            // The costume pieces worn now, to put them back on with the switch button
            List<ItemStack> costume = new ArrayList<>();
            List<Boolean> ownPieces = new ArrayList<>();
            for (ItemStack s : worn) {
                costume.add(isCostume(s) ? unmark(s) : ItemStack.EMPTY);
                ownPieces.add(!s.isEmpty() && !isCostume(s));
            }
            var r = Dressing.takeBack(KIND, worn, st.saved().get());
            purgeCopies(p);
            apply(p, r);
            WardrobeState next = st.withDressing(Optional.empty(), "");
            if (!Dressing.allEmpty(KIND, costume)) next = next.withLast(Optional.of(costume), st.wearing(), ownPieces);
            setState(p, next);
            sound(p);
            say(p, "back");
            return;
        }
        if (w != null && !w.show()) {
            for (int i = 0; i < w.outfits().size(); i++) {
                if (w.outfits().get(i).name().equals(own(p))) {
                    wear(p, w, i, -1);
                    return;
                }
            }
        }
        say(p, "nothing_back");
    }

    /**
     * The switch button: in a costume, back into your own clothes; in your own clothes, the last costume again
     * (copies, the pieces it had only: the others stay yours).
     */
    public static void switchOutfit(ServerPlayer p, WardrobeBlockEntity w) {
        WardrobeState st = state(p);
        if (st.inCostume() || w == null || !w.show() || st.last().isEmpty()) {
            takeBack(p, w);
            return;
        }
        List<ItemStack> last = st.last().get();
        // The same look as then: its pieces, nothing where it had nothing, your own pieces where you wore them
        boolean[] mask = new boolean[Dressing.PIECES];
        for (int i = 0; i < mask.length; i++) mask[i] = !st.lastOwn(i);
        var r = Dressing.putOnCopy(KIND, worn(p), null, last, mask);
        purgeCopies(p);
        apply(p, r);
        setState(p, st.withDressing(Optional.of(r.saved()), st.lastWearing().isEmpty() ? WardrobeState.MIX : st.lastWearing()));
        sound(p);
        say(p, "switched");
    }

    /**
     * A button of the outfit screen. Organisers only, in a show wardrobe: add / replace / delete / rename / move,
     * the ready outfits, the mode, every outfit again. In a storage wardrobe anyone stores, renames and takes out.
     * Anyone: wear, wear a piece, take back, favourite.
     */
    public static void act(ServerPlayer p, WardrobeBlockEntity w, String action, int index, String text) {
        boolean admin = Permissions.isAdmin(p);
        boolean manage = admin || !w.show();
        String name = text == null ? "" : text.strip();
        switch (action) {
            case "wear" -> wear(p, w, index, -1);
            case "wear_piece" -> {
                try {
                    wear(p, w, index, Integer.parseInt(name));
                } catch (NumberFormatException e) {
                    return;
                }
            }
            case "back" -> takeBack(p, w);
            case "switch" -> switchOutfit(p, w);
            case "trash" -> {
                // The costume goes (and is not remembered); your own clothes come back
                WardrobeState st = state(p);
                if (st.inCostume()) takeBack(p, w);
                setState(p, state(p).withLast(Optional.empty(), "", List.of()));
                say(p, "trashed");
            }
            case "trash_all" -> {
                // Shift + trash, confirmed: everything worn is deleted, your own clothes too
                purgeCopies(p);
                for (EquipmentSlot slot : Outfit.SLOTS) p.setItemSlot(slot, ItemStack.EMPTY);
                p.inventoryMenu.broadcastChanges();
                setState(p, state(p).withDressing(Optional.empty(), "").withLast(Optional.empty(), "", List.of()));
                sound(p);
                say(p, "trashed_all");
            }
            case "restore" -> {
                // An outfit of the game that this wardrobe's own list left out (grey in the edit mode): back in
                if (!admin || !w.show() || name.isEmpty()) return;
                for (Outfit o : Catalogue.all()) {
                    if (o.key().equals(name) && w.outfits().stream().noneMatch(x -> x.key().equals(name))) {
                        w.addAll(List.of(o));
                        say(p, "restored", o.displayName());
                        break;
                    }
                }
            }
            case "tags" -> {
                Outfit o = w.get(index);
                if (!manage || o == null) return;
                w.set(index, o.withTags(OutfitTags.parse(name)));
                say(p, "tagged", o.displayName());
            }
            case "take_off" -> {
                if (index < 0 || index > 3) return;
                WardrobeState st = state(p);
                List<ItemStack> worn = worn(p);
                if (worn.get(index).isEmpty()) return;
                Component piece = worn.get(index).getHoverName();
                var r = Dressing.takeOff(KIND, worn, st.saved().orElse(null), index);
                apply(p, r);
                boolean still = r.saved() != null || r.worn().stream().anyMatch(Wardrobes::isCostume);
                setState(p, st.withDressing(Optional.ofNullable(r.saved()), still ? WardrobeState.MIX : ""));
                sound(p);
                say(p, "taken_off", piece);
            }
            case "favourite" -> {
                Outfit o = w.get(index);
                if (o == null) return;
                setState(p, state(p).toggleFavourite(o.key()));
            }
            case "add" -> {
                if (!manage) return;
                if (w.outfits().size() >= WardrobeBlockEntity.MAX_OUTFITS) {
                    say(p, "full");
                    break;
                }
                List<ItemStack> worn = worn(p);
                if (Dressing.allEmpty(KIND, worn)) {
                    say(p, "wear_something");
                    break;
                }
                if (w.show()) {
                    List<ItemStack> pieces = Dressing.template(KIND, worn, Wardrobes::unmark);
                    Outfit same = find(w, pieces);
                    if (same != null && (name.isEmpty() || name.equals(same.name()))) {
                        say(p, "already", same.displayName());
                        break;
                    }
                    w.addAll(List.of(Outfit.of(name.isEmpty() ? "Tenue " + (w.outfits().size() + 1) : name, pieces)));
                } else {
                    // Storage: what you wear goes in (you are left without it)
                    var r = Dressing.store(KIND, worn);
                    apply(p, r);
                    WardrobeState st = state(p);
                    w.addAll(List.of(Outfit.of(!name.isEmpty() ? name : own(p), r.outfit())));
                    setState(p, st.withDressing(st.saved(), ""));
                    sound(p);
                }
                say(p, "added", w.outfits().getLast().displayName());
            }
            case "replace" -> {
                Outfit o = w.get(index);
                if (!admin || !w.show() || o == null) return;
                List<ItemStack> worn = worn(p);
                if (Dressing.allEmpty(KIND, worn)) {
                    say(p, "wear_something");
                    break;
                }
                w.set(index, Outfit.of(name.isEmpty() ? o.name() : name, Dressing.template(KIND, worn, Wardrobes::unmark), o.tags()));
                say(p, "replaced", w.get(index).displayName());
            }
            case "rename" -> {
                Outfit o = w.get(index);
                if (!manage || o == null || name.isEmpty()) return;
                w.set(index, o.withName(name));
            }
            case "delete" -> {
                Outfit o = w.get(index);
                if (!manage || o == null) return;
                // Storage: the clothes come out into the inventory
                if (!w.show()) for (ItemStack s : o.pieces()) give(p, s.copy());
                w.remove(index);
                say(p, w.show() ? "deleted" : "taken_out", o.displayName());
            }
            case "up", "down" -> {
                if (manage) w.move(index, action.equals("up") ? -1 : 1);
            }
            case "mode" -> {
                if (admin) w.setShow(!w.show());
            }
            case "auto" -> {
                if (!admin || !w.show()) return;
                w.setAuto();
                say(p, "auto", w.outfits().size());
            }
            case "catalogue_add" -> {
                Outfit o = w.get(index);
                if (!admin || o == null) return;
                String n = o.name().isEmpty() ? o.displayName().getString() : o.name();
                Catalogue.put(Outfit.of(n, Dressing.template(KIND, o.pieces(), Wardrobes::unmark)));
                say(p, "catalogue_added", o.displayName());
            }
            case "catalogue_remove" -> {
                Outfit o = w.get(index);
                if (!admin || o == null) return;
                say(p, Catalogue.remove(o.name()) ? "catalogue_removed" : "catalogue_missing", o.displayName());
            }
            default -> {
                return;
            }
        }
        WardrobeNetwork.sendRefresh(p, w);
    }
}
