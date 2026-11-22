package fr.lordfinn.craftmaster.trial.kit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.trial.Judging;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The book each contestant signs with the name of their animal ({@link Arena#livreNom}, on a common ground). Signing
 * is answering: the text is kept with the drawing ({@code <player>.nom.json}) and no longer changes. Nobody reads it
 * before the jury reveals it, the organisers and the AI control included: they are only told that the book is signed. A book not signed when the time is up is taken as it stands, marked unsigned. The book stays with
 * its owner: thrown or dropped it comes back, it goes in no container, lectern or frame.
 */
public final class NameBooks {

    private NameBooks() {}

    private static final String TAG = "craftmaster_livre";

    /** Contestants (lower case) who were given a book, and a copy of it as they last had it. */
    private static final Map<String, ItemStack> HELD = new HashMap<>();
    /** Answers fixed: signed, or read when the time was up. */
    private static final Set<String> FIXED = new HashSet<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(NameBooks::tick);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                !level.isClientSide() && isBook(player.getItemInHand(hand)) ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    static boolean isBook(ItemStack st) {
        if (st.isEmpty() || !(st.is(Items.WRITABLE_BOOK) || st.is(Items.WRITTEN_BOOK))) return false;
        CustomData d = st.get(DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().contains(TAG);
    }

    /** On a common ground with a book: no chest, lectern or anything holding items may be used. */
    static boolean blocksUse(Player p, Level level, BlockPos pos) {
        Arena a = CommonGround.at(level, pos);
        return a != null && a.livreNom && level.getBlockEntity(pos) != null;
    }

    /** The contestant's blank book (nothing if they already have theirs). */
    static void give(ServerPlayer p, Arena a) {
        String name = Arenas.playerName(p);
        String key = name.toLowerCase(Locale.ROOT);
        if (find(p) != null) return;
        ItemStack st = new ItemStack(Items.WRITABLE_BOOK);
        st.set(DataComponents.CUSTOM_NAME, Component.literal("Le nom de ton animal").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
        st.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Écris son nom, puis signe").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)))));
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG, a.name);
        tag.putString("owner", name);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        HELD.put(key, st.copy());
        if (!p.getInventory().add(st)) p.getInventory().setItem(p.getInventory().getSelectedSlot(), st);
    }

    /** The player's book, wherever it is in their inventory or the screen they have open. */
    private static ItemStack find(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (isBook(inv.getItem(i))) return inv.getItem(i);
        if (isBook(p.containerMenu.getCarried())) return p.containerMenu.getCarried();
        for (Slot s : p.containerMenu.slots) if (isBook(s.getItem())) return s.getItem();
        for (Slot s : p.inventoryMenu.slots) if (isBook(s.getItem())) return s.getItem();
        return null;
    }

    private static void tick(MinecraftServer server) {
        if (HELD.isEmpty()) return;
        for (Arena a : Arenas.all()) {
            if (!a.livreNom || !CommonGround.running(a)) continue;
            ServerLevel level = Arenas.level(server, a);
            // A book on the floor (thrown, or at a death) is nobody's to read
            for (ItemEntity e : level.getEntities(EntityTypes.ITEM, e -> isBook(e.getItem()))) e.discard();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                String name = Judging.contestant(p);
                if (name == null) continue;
                String key = name.toLowerCase(Locale.ROOT);
                ItemStack known = HELD.get(key);
                if (known == null) continue;
                ItemStack st = find(p);
                if (st == null) {
                    if (p.isAlive() && !p.isSpectator() && !p.getInventory().add(known.copy()))
                        p.getInventory().setItem(p.getInventory().getSelectedSlot(), known.copy());
                    continue;
                }
                if (!ItemStack.isSameItemSameComponents(st, known)) HELD.put(key, st.copy());
                if (st.is(Items.WRITTEN_BOOK) && !FIXED.contains(key)) fix(server, a, name, text(st), true);
            }
        }
    }

    /** What is written in a book. */
    static String text(ItemStack st) {
        List<String> pages = new ArrayList<>();
        WritableBookContent w = st.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (w != null) w.pages().forEach(f -> pages.add(f.raw()));
        WrittenBookContent s = st.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (s != null) s.pages().forEach(f -> pages.add(f.raw().getString()));
        return String.join(" ", pages).replaceAll("\\s+", " ").strip();
    }

    private static void fix(MinecraftServer server, Arena a, String name, String text, boolean signed) {
        FIXED.add(name.toLowerCase(Locale.ROOT));
        try {
            JsonObject o = new JsonObject();
            o.addProperty("joueur", name);
            o.addProperty("nom", text);
            o.addProperty("signe", signed);
            Path d = DrawingImages.dir(a.name);
            Files.createDirectories(d);
            Files.writeString(file(a.name, name), o.toString());
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Livres] réponse de {} non enregistrée", name, e);
        }
        Component line = Component.literal("✍ ").withStyle(ChatFormatting.GOLD).append(Finish.name(name))
                .append(Component.literal(signed ? " a signé" : " n'a pas signé : son livre est relevé tel quel").withStyle(ChatFormatting.GOLD));
        // Who signed, never what: the master guesses the drawing before the name is revealed
        Finish.tellOrganisers(server, null, line, name + (signed ? " a signé son livre" : " n'a pas signé son livre"));
    }

    private static Path file(String arena, String player) {
        return DrawingImages.dir(arena).resolve(player.replaceAll("[^A-Za-z0-9_.-]", "_") + ".nom.json");
    }

    /** A book as others may see it (the inventory camera): the object, nothing of what is written in it. */
    public static ItemStack forViewers(ItemStack st) {
        if (!isBook(st)) return st;
        ItemStack out = new ItemStack(Items.WRITABLE_BOOK);
        out.set(DataComponents.CUSTOM_NAME, Component.literal("Le nom de ton animal").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
        return out;
    }

    /** Would this command line of the AI control read a player's data while books are in play? */
    public static boolean readsBooks(List<String> words) {
        if (HELD.isEmpty()) return false;
        List<String> w = words.stream().map(x -> x.toLowerCase(Locale.ROOT)).toList();
        return w.contains("data") && w.contains("get") && w.contains("entity");
    }

    /** What the AI control is told, without any name not revealed yet. */
    public static String redact(String s) {
        if (s == null || s.isEmpty()) return s;
        Set<String> hidden = new HashSet<>();
        for (ItemStack st : HELD.values()) {
            String owner = st.get(DataComponents.CUSTOM_DATA) == null ? "" : st.get(DataComponents.CUSTOM_DATA).copyTag().getStringOr("owner", "");
            if (!JuryScreen.revealedAny(owner)) hidden.add(text(st));
        }
        for (Arena a : Arenas.all()) {
            if (!a.livreNom) continue;
            for (String p : DrawingImages.names(a.name)) {
                String[] answer = answer(a.name, p);
                if (answer != null && !JuryScreen.revealed(a.name, p)) hidden.add(answer[0]);
            }
        }
        for (String h : hidden)
            if (h.length() >= 3) s = s.replaceAll("(?i)" + java.util.regex.Pattern.quote(h), "‹caché›");
        return s;
    }

    /** The answer kept for this drawing: {text, "signé" | "non signé"}, or null. */
    public static String[] answer(String arena, String player) {
        try {
            Path f = file(arena, player);
            if (!Files.exists(f)) return null;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            return new String[]{o.get("nom").getAsString(), o.get("signe").getAsBoolean() ? "signé" : "non signé"};
        } catch (Exception e) {
            return null;
        }
    }

    /** The time is up (or the trial ends): the books not signed are read as they stand, and nothing changes after. */
    public static void collect(MinecraftServer server) {
        if (HELD.isEmpty()) return;
        for (Arena a : Arenas.all()) {
            if (!a.livreNom || !CommonGround.running(a)) continue;
            for (Map.Entry<String, ItemStack> e : HELD.entrySet()) {
                if (FIXED.contains(e.getKey())) continue;
                String shown = e.getKey();
                ItemStack st = e.getValue();
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    String c = Judging.contestant(p);
                    if (c == null || !c.equalsIgnoreCase(e.getKey())) continue;
                    shown = c;
                    ItemStack now = find(p);
                    if (now != null) st = now;
                }
                CompoundTag t = st.get(DataComponents.CUSTOM_DATA) == null ? null : st.get(DataComponents.CUSTOM_DATA).copyTag();
                if (t != null) shown = t.getStringOr("owner", shown);
                fix(server, a, shown, text(st), st.is(Items.WRITTEN_BOOK));
            }
        }
    }

    /** The books leave every inventory; what was answered stays on disk. */
    private static void takeAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            var inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) if (isBook(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
            if (isBook(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    /** « Revenir au plateau »: the answers are read, then the books are taken back. */
    public static void backToStage(MinecraftServer server) {
        if (HELD.isEmpty()) return;
        collect(server);
        HELD.clear();
        FIXED.clear();
        takeAll(server);
    }

    /** A new launch: old books taken back, blank ones given at the start. */
    public static void restart(MinecraftServer server) {
        HELD.clear();
        FIXED.clear();
        takeAll(server);
    }
}
