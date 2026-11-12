package fr.lordfinn.craftmaster.wardrobe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the wardrobes remember about a player (kept on the player, through deaths and reconnections): the clothes
 * kept aside while they wear show copies (slot by slot), what they wear, their favourite outfits, and the last
 * costume they took off (the switch button puts it back on).
 *
 * @param saved       their own four pieces kept aside, empty = not in a show costume
 * @param wearing     {@link Outfit#key()} of the outfit worn whole, {@link #MIX} for pieces of several, "" = their own
 * @param favourites  {@link Outfit#key()} of their favourite outfits (in every wardrobe)
 * @param last        the costume pieces they wore before going back to their own clothes (empty = none)
 * @param lastWearing what {@link #wearing} was then
 * @param lastOwn     for each piece of {@link #last}: it was the player's own (it stays when the costume comes back)
 */
public record WardrobeState(Optional<List<ItemStack>> saved, String wearing, List<String> favourites,
                            Optional<List<ItemStack>> last, String lastWearing, List<Boolean> lastOwn) {

    public static final String MIX = "~mix";
    public static final WardrobeState NONE = new WardrobeState(Optional.empty(), "", List.of(), Optional.empty(), "", List.of());

    public static final Codec<WardrobeState> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("saved").forGetter(WardrobeState::saved),
            Codec.STRING.optionalFieldOf("wearing", "").forGetter(WardrobeState::wearing),
            Codec.STRING.listOf().optionalFieldOf("favourites", List.of()).forGetter(WardrobeState::favourites),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("last").forGetter(WardrobeState::last),
            Codec.STRING.optionalFieldOf("last_wearing", "").forGetter(WardrobeState::lastWearing),
            Codec.BOOL.listOf().optionalFieldOf("last_own", List.of()).forGetter(WardrobeState::lastOwn)
    ).apply(i, WardrobeState::new));

    public boolean inCostume() {
        return saved.isPresent();
    }

    public WardrobeState withDressing(Optional<List<ItemStack>> saved, String wearing) {
        return new WardrobeState(saved, wearing, favourites, last, lastWearing, lastOwn);
    }

    public WardrobeState withLast(Optional<List<ItemStack>> last, String lastWearing, List<Boolean> lastOwn) {
        return new WardrobeState(saved, wearing, favourites, last, lastWearing, List.copyOf(lastOwn));
    }

    /** Was piece {@code i} of the last costume the player's own? */
    public boolean lastOwn(int i) {
        return i < lastOwn.size() && lastOwn.get(i);
    }

    /** Favourite on / off. */
    public WardrobeState toggleFavourite(String key) {
        List<String> f = new ArrayList<>(favourites);
        if (!f.remove(key)) f.add(key);
        return new WardrobeState(saved, wearing, List.copyOf(f), last, lastWearing, lastOwn);
    }
}
