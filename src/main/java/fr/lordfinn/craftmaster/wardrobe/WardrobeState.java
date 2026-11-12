package fr.lordfinn.craftmaster.wardrobe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the wardrobes remember about a player (kept on the player, through deaths and reconnections): the clothes
 * kept aside while they wear show copies (slot by slot), what they wear, and their favourite outfits.
 *
 * @param saved      their own four pieces kept aside, empty = not in a show costume
 * @param wearing    {@link Outfit#key()} of the outfit worn whole, {@link #MIX} for pieces of several, "" = their own
 * @param favourites {@link Outfit#key()} of their favourite outfits (in every wardrobe)
 */
public record WardrobeState(Optional<List<ItemStack>> saved, String wearing, List<String> favourites) {

    public static final String MIX = "~mix";
    public static final WardrobeState NONE = new WardrobeState(Optional.empty(), "", List.of());

    public static final Codec<WardrobeState> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("saved").forGetter(WardrobeState::saved),
            Codec.STRING.optionalFieldOf("wearing", "").forGetter(WardrobeState::wearing),
            Codec.STRING.listOf().optionalFieldOf("favourites", List.of()).forGetter(WardrobeState::favourites)
    ).apply(i, WardrobeState::new));

    public boolean inCostume() {
        return saved.isPresent();
    }

    public WardrobeState withDressing(Optional<List<ItemStack>> saved, String wearing) {
        return new WardrobeState(saved, wearing, favourites);
    }

    /** Favourite on / off. */
    public WardrobeState toggleFavourite(String key) {
        List<String> f = new ArrayList<>(favourites);
        if (!f.remove(key)) f.add(key);
        return new WardrobeState(saved, wearing, List.copyOf(f));
    }
}
