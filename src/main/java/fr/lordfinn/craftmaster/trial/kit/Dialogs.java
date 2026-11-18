package fr.lordfinn.craftmaster.trial.kit;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * A typed answer through the game's own dialog window (vanilla since 1.21.6): the server opens it, nothing to
 * install on the players' side. Used by the password-locked pickups; any trial that needs a word typed by a
 * contestant (an answer, a code) can use {@link #ask}.
 */
public final class Dialogs {

    private Dialogs() {}

    private static final String NS = "craftmaster";
    private static final String KEY = "reponse";
    /** Per player: the answer awaited, by its button's id (one at a time: a new question replaces the old). */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static int counter;

    private record Pending(String path, BiConsumer<ServerPlayer, String> onAnswer) {}

    /**
     * Opens a window with a title, some lines of text, one text field and a « Valider » button.
     *
     * @param lines    text above the field (each a paragraph)
     * @param label    the field's label
     * @param onAnswer called on the server thread with what was typed (trimmed)
     */
    public static void ask(ServerPlayer player, Component title, List<Component> lines, Component label, int maxLength,
                           BiConsumer<ServerPlayer, String> onAnswer) {
        String path = "kit/" + (++counter);
        PENDING.put(player.getUUID(), new Pending(path, onAnswer));
        List<DialogBody> body = new ArrayList<>();
        for (Component l : lines) body.add(new PlainMessage(l, 260));
        TextInput field = new TextInput(220, label, true, "", Math.max(1, maxLength), Optional.empty());
        CommonDialogData common = new CommonDialogData(title, Optional.empty(), true, false, DialogAction.CLOSE, body,
                List.of(new Input(KEY, field)));
        ActionButton ok = new ActionButton(new CommonButtonData(Component.literal("Valider"), 120),
                Optional.of(new CustomAll(Identifier.fromNamespaceAndPath(NS, path), Optional.empty())));
        player.openDialog(Holder.direct(new NoticeDialog(common, ok)));
    }

    /** A dialog button came back (from the mixin): true if it was one of ours. */
    public static boolean handle(ServerPlayer player, Identifier id, Optional<Tag> payload) {
        if (!NS.equals(id.getNamespace()) || !id.getPath().startsWith("kit/")) return false;
        Pending p = PENDING.get(player.getUUID());
        if (p == null || !p.path.equals(id.getPath())) return true; // an old window: ignored
        PENDING.remove(player.getUUID());
        String typed = payload.filter(t -> t instanceof CompoundTag).map(t -> ((CompoundTag) t).getStringOr(KEY, "")).orElse("").trim();
        p.onAnswer.accept(player, typed);
        return true;
    }

    public static void forget(UUID player) {
        PENDING.remove(player);
    }
}
