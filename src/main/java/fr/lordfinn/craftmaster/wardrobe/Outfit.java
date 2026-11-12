package fr.lordfinn.craftmaster.wardrobe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A complete outfit of a wardrobe: a name and four pieces (head, body, legs, feet), any item, often empty. With the
 * clothes of Selfexpression each piece is an armour item; any armour, a head or a carved pumpkin works too.
 * An outfit without a name (the sets found in the game's items) is called after its body piece, in the player's
 * language.
 */
public record Outfit(String name, List<ItemStack> pieces) {

    /** Head, body, legs, feet: the order of {@link #pieces}. */
    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    public static final int MAX_NAME = 48;

    public static final Codec<Outfit> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(Outfit::name),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("pieces", List.of()).forGetter(Outfit::pieces)
    ).apply(i, Outfit::of));

    public static final StreamCodec<RegistryFriendlyByteBuf, Outfit> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_NAME * 4), Outfit::name,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC, Outfit::pieces,
            Outfit::of);

    /** Always four pieces, copies, the name trimmed to {@link #MAX_NAME}. */
    public static Outfit of(String name, List<ItemStack> pieces) {
        List<ItemStack> p = new ArrayList<>();
        for (int i = 0; i < Dressing.PIECES; i++) p.add(i < pieces.size() && pieces.get(i) != null ? pieces.get(i).copy() : ItemStack.EMPTY);
        String n = name == null ? "" : name.strip();
        return new Outfit(n.length() > MAX_NAME ? n.substring(0, MAX_NAME) : n, List.copyOf(p));
    }

    public boolean isEmpty() {
        return pieces.stream().allMatch(ItemStack::isEmpty);
    }

    public Outfit withName(String name) {
        return of(name, pieces);
    }

    /** Its name, or (no name) its body piece's name, translated by the client. */
    public Component displayName() {
        if (!name.isEmpty()) return Component.literal(name);
        ItemStack named = !pieces.get(1).isEmpty() ? pieces.get(1) : pieces.stream().filter(s -> !s.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
        return named.isEmpty() ? Component.literal("?") : named.getHoverName();
    }

    /** What the outfit is, whatever its name: its pieces (favourites and "worn" are kept by this). */
    public String key() {
        return key(pieces);
    }

    public static String key(List<ItemStack> pieces) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack s : pieces) {
            if (sb.length() > 0) sb.append(',');
            if (s.isEmpty()) continue;
            ItemStack c = Wardrobes.unmark(s);
            sb.append(BuiltInRegistries.ITEM.getKey(c.getItem()));
            if (!c.getComponentsPatch().isEmpty()) sb.append('#').append(Integer.toHexString(c.getComponentsPatch().toString().hashCode()));
        }
        return sb.toString();
    }
}
