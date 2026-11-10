package fr.lordfinn.craftmaster.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.craftmaster.CraftMaster;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

/** Item data of the envelopes and notes (chantier D). */
public final class TaskComponents {

    private TaskComponents() {}

    /**
     * A sealed envelope. Linked to a round ({@code manche} = its id in {@code season}; the text is read from the
     * data when it is opened, so it is always the latest), or carrying a free text ({@code manche} empty).
     * {@code label}: the round's name when it was sealed, for the tooltip; {@code to}: name written on it;
     * {@code announce}: opening it is announced to everyone.
     */
    public record Envelope(int season, String manche, String label, String title, String text, String wax, String to,
                           boolean announce, boolean opened) {
        public static final Codec<Envelope> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("season", 0).forGetter(Envelope::season),
                Codec.STRING.optionalFieldOf("manche", "").forGetter(Envelope::manche),
                Codec.STRING.optionalFieldOf("label", "").forGetter(Envelope::label),
                Codec.STRING.optionalFieldOf("title", "").forGetter(Envelope::title),
                Codec.STRING.optionalFieldOf("text", "").forGetter(Envelope::text),
                Codec.STRING.optionalFieldOf("wax", "red").forGetter(Envelope::wax),
                Codec.STRING.optionalFieldOf("to", "").forGetter(Envelope::to),
                Codec.BOOL.optionalFieldOf("announce", true).forGetter(Envelope::announce),
                Codec.BOOL.optionalFieldOf("opened", false).forGetter(Envelope::opened)
        ).apply(i, Envelope::new));

        public static final Envelope BLANK = new Envelope(0, "", "", "", "", "red", "", true, false);

        public Envelope withOpened(boolean o) {
            return new Envelope(season, manche, label, title, text, wax, to, announce, o);
        }

        public boolean linked() {
            return !manche.isEmpty();
        }
    }

    /** A note item: the hiding tool (organisers), a taken note ({@code taken}) or a bottle; {@code piece} 0-based. */
    public record NoteRef(String id, int piece, boolean taken) {
        public static final Codec<NoteRef> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(NoteRef::id),
                Codec.INT.optionalFieldOf("piece", 0).forGetter(NoteRef::piece),
                Codec.BOOL.optionalFieldOf("taken", false).forGetter(NoteRef::taken)
        ).apply(i, NoteRef::new));
    }

    public static DataComponentType<Envelope> ENVELOPE;
    public static DataComponentType<NoteRef> NOTE;

    static void register() {
        ENVELOPE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CraftMaster.id("envelope"),
                DataComponentType.<Envelope>builder().persistent(Envelope.CODEC)
                        .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(Envelope.CODEC)).build());
        NOTE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CraftMaster.id("note"),
                DataComponentType.<NoteRef>builder().persistent(NoteRef.CODEC)
                        .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(NoteRef.CODEC)).build());
    }

    /** Puts the envelope on the stack, with the look of the item model (flag 0 = opened, colour 0 = wax). */
    public static ItemStack setEnvelope(ItemStack stack, Envelope e) {
        stack.set(ENVELOPE, e);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(e.opened()), List.of(), List.of(WaxColor.of(e.wax()).rgb)));
        return stack;
    }

    /** The wax seal's colour, shown by its item model (colour 0). */
    public static void setSealColor(ItemStack stack, WaxColor c) {
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(c.id()), List.of(c.rgb)));
    }

    public static WaxColor sealColor(ItemStack stack) {
        CustomModelData d = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return d == null || d.getString(0) == null ? WaxColor.RED : WaxColor.of(d.getString(0));
    }
}
