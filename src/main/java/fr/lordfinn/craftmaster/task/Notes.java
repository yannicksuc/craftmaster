package fr.lordfinn.craftmaster.task;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.data.Task;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The hidden notes (chantier D). An organiser hides a note with its tool item; the players find it by the right
 * gesture at the right place (crouch to lift the carpet, pull the right book, look behind the painting, feel for
 * the false bottom, find the sheet pinned under the table, uncork the bottle). Who may read it, whether the first
 * finder takes it, invisible ink (UV lamp) and torn pieces are per note; every first find is logged and told to
 * the organisers.
 */
public final class Notes {

    private Notes() {}

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    static TaskStore store() {
        return Tasks.store();
    }

    // ---------------------------------------------------------------- reading rules

    /** In the active season's cast. */
    static boolean inCast(ServerPlayer p) {
        Season s = CraftMaster.data() == null ? null : CraftMaster.data().activeSeason();
        return s != null && s.player(p.getGameProfile().name()) != null;
    }

    static boolean mayRead(ServerPlayer p, Note n) {
        return Permissions.isAdmin(p) || n.readableBy(p.getGameProfile().name(), inCast(p));
    }

    /** Where it was found ("sous le tapis"), translated by each reader's game. */
    static Component where(String method) {
        Note.Method m = Note.Method.of(method);
        String key = m == null ? method : m.id();
        return Component.translatable("note.craftmaster.where." + key);
    }

    static Component piece(int piece, int pieces) {
        return Component.translatable("paper.craftmaster.piece", piece + 1, pieces);
    }

    /** Pieces the player has: found, or carried in the inventory (a piece given by a team mate). */
    static List<Integer> piecesOf(ServerPlayer p, Note n) {
        List<Integer> out = new ArrayList<>(n.piecesFoundBy(p.getGameProfile().name()));
        for (ItemStack s : p.getInventory()) {
            TaskComponents.NoteRef r = s.get(TaskComponents.NOTE);
            if (r != null && r.id().equals(n.id) && !out.contains(r.piece())) out.add(r.piece());
        }
        return out;
    }

    /** What the player sees on the paper: the whole text, its found pieces, or a blank sheet (ink, no lamp). */
    static TaskNetwork.Paper paper(ServerPlayer p, Note n, Component header, int flags) {
        boolean admin = Permissions.isAdmin(p);
        boolean lit = UvLampItem.holds(p);
        String text = n.text;
        List<String> pieces = List.of();
        if (n.pieces > 1) {
            List<String> parts = Note.split(n.text, n.pieces);
            List<Integer> have = admin ? java.util.stream.IntStream.range(0, n.pieces).boxed().toList() : piecesOf(p, n);
            List<String> shown = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) shown.add(have.contains(i) ? parts.get(i) : "");
            pieces = shown;
            if (have.size() < n.pieces) text = "";
        }
        if (n.ink) {
            flags |= TaskNetwork.INK;
            // Organisers always read it (with a hint that it is invisible ink); the others need the lamp
            if (lit || admin) flags |= TaskNetwork.LIT;
            else {
                text = "";
                pieces = pieces.stream().map(x -> "").toList();
            }
        }
        if (admin) flags |= TaskNetwork.CAN_EDIT;
        return new TaskNetwork.Paper("note", header, n.title, text, List.of(), 0, "red", n.look, flags, pieces, n.id, 0, "");
    }

    // ---------------------------------------------------------------- finding

    private static void found(ServerPlayer p, Note n, Note.Spot spot, String method, ServerLevel level, double x, double y, double z) {
        String name = p.getGameProfile().name();
        int piece = spot == null ? 0 : spot.piece;
        boolean first = store().recordFind(n, name, piece, method, System.currentTimeMillis());
        if (first) tellAdmins(p.level().getServer(), p, n, piece, method);
        level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 30, 0.4, 0.4, 0.4, 0.6);
        level.playSound(null, x, y, z, sound(method), SoundSource.PLAYERS, 0.9f, 1.1f);
        level.playSound(null, x, y, z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.2f);
        p.sendOverlayMessage(Component.translatable("message.craftmaster.note_found." + method).withStyle(ChatFormatting.GOLD));
        if (n.once && spot != null) {
            // Taken: it goes into the finder's inventory, the hiding place is empty for the others
            spot.takenBy = name;
            store().changed();
            Tasks.give(p, taken(n, piece));
            if (spot.method() == Note.Method.PINNED) {
                BlockPos pos = new BlockPos(spot.x, spot.y, spot.z);
                if (level.getBlockState(pos).getBlock() instanceof PinnedNoteBlock) level.removeBlock(pos, false);
            }
        }
        MutableComponent header = Component.translatable("paper.craftmaster.found", where(method));
        if (n.pieces > 1) header.append(" · ").append(piece(piece, n.pieces));
        Tasks.send(p, paper(p, n, header, TaskNetwork.FOUND));
    }

    private static SoundEvent sound(String method) {
        return switch (method) {
            case "under" -> SoundEvents.WOOL_BREAK;
            case "book" -> SoundEvents.CHISELED_BOOKSHELF_PICKUP;
            case "behind" -> SoundEvents.PAINTING_PLACE;
            case "false_bottom" -> SoundEvents.WOOD_HIT;
            case "bottle" -> SoundEvents.BOTTLE_EMPTY;
            default -> SoundEvents.ITEM_PICKUP;
        };
    }

    private static void tellAdmins(MinecraftServer server, ServerPlayer finder, Note n, int piece, String method) {
        MutableComponent what = Component.literal(n.title);
        if (n.pieces > 1) what.append(" (").append(piece(piece, n.pieces)).append(")");
        Component msg = Component.literal("📜 ").withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("message.craftmaster.note_found_admin", finder.getGameProfile().name(), what, where(method))
                        .withStyle(ChatFormatting.YELLOW));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (Permissions.isAdmin(p) && p != finder) p.sendSystemMessage(msg);
        CraftMaster.LOG.info("[Notes] {} a trouvé « {} » (morceau {}, {})", finder.getGameProfile().name(), n.title, piece + 1, method);
    }

    /** First hiding place of a note there that this player can find now with this gesture, or null. */
    private static Map.Entry<Note, Note.Spot> findable(ServerPlayer p, List<Map.Entry<Note, Note.Spot>> spots) {
        for (Map.Entry<Note, Note.Spot> e : spots) {
            Note.Spot s = e.getValue();
            if (s.takenBy != null) continue;
            if (s.method().sneak && !p.isShiftKeyDown()) continue;
            if (!mayRead(p, e.getKey())) continue;
            return e;
        }
        return null;
    }

    static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer p) || !(level instanceof ServerLevel server) || store() == null)
            return InteractionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        // The organiser holding a note hides it, before the block reacts (a chest would open, a lectern take it)
        if (NoteItem.isTool(p, p.getMainHandItem())) {
            hide(p, NoteItem.ref(p.getMainHandItem()), pos, hit.getDirection(), p.isShiftKeyDown());
            return InteractionResult.SUCCESS_SERVER;
        }
        Map.Entry<Note, Note.Spot> e = findable(p, store().spotsAt(Busts.worldId(level), pos.getX(), pos.getY(), pos.getZ()));
        if (e == null) return InteractionResult.PASS;
        found(p, e.getKey(), e.getValue(), e.getValue().method().id(), server, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5);
        return InteractionResult.SUCCESS_SERVER;
    }

    static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer p) || !(level instanceof ServerLevel server) || store() == null)
            return InteractionResult.PASS;
        if (NoteItem.isTool(p, p.getMainHandItem())) {
            if (entity instanceof HangingEntity) {
                hideBehind(p, NoteItem.ref(p.getMainHandItem()), entity);
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.PASS;
        }
        Map.Entry<Note, Note.Spot> e = findable(p, store().spotsOn(entity.getUUID().toString()));
        if (e == null) return InteractionResult.PASS;
        found(p, e.getKey(), e.getValue(), e.getValue().method().id(), server, entity.getX(), entity.getY(), entity.getZ());
        return InteractionResult.SUCCESS_SERVER;
    }

    /** An organiser's left click with the tool on a hiding place removes it (instead of breaking the block). */
    static InteractionResult onAttackBlock(Player player, Level level, InteractionHand hand, BlockPos pos, Direction dir) {
        ItemStack held = player.getMainHandItem();
        TaskComponents.NoteRef r = held.get(TaskComponents.NOTE);
        if (!(held.getItem() instanceof NoteItem) || r == null || r.taken()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer p) || !Permissions.isAdmin(p) || store() == null) return InteractionResult.PASS;
        String world = Busts.worldId(level);
        int removed = 0;
        for (Map.Entry<Note, Note.Spot> e : store().spotsAt(world, pos.getX(), pos.getY(), pos.getZ())) {
            e.getKey().spots.remove(e.getValue());
            removed++;
        }
        if (level.getBlockState(pos).getBlock() instanceof PinnedNoteBlock) level.removeBlock(pos, false);
        if (removed > 0) store().changed();
        p.sendOverlayMessage(Component.translatable(removed > 0 ? "message.craftmaster.note_unhidden" : "message.craftmaster.note_no_spot", removed)
                .withStyle(removed > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        return InteractionResult.SUCCESS;
    }

    /** A pinned sheet was broken or replaced: its hiding place goes too (unless a finder took it). */
    static void onPinnedRemoved(ServerLevel level, BlockPos pos) {
        if (store() == null) return;
        boolean any = false;
        for (Map.Entry<Note, Note.Spot> e : store().spotsAt(Busts.worldId(level), pos.getX(), pos.getY(), pos.getZ())) {
            if (e.getValue().method() == Note.Method.PINNED && e.getValue().takenBy == null) {
                e.getKey().spots.remove(e.getValue());
                any = true;
            }
        }
        if (any) store().changed();
    }

    /**
     * Opens a note for the player as if found (contract for the task props of chantier H): its readers are
     * respected, the find is logged ("prop"). @return false if there is no such note or the player may not read it
     */
    public static boolean open(ServerPlayer player, String noteId) {
        Note n = store() == null ? null : store().note(noteId);
        if (n == null || !mayRead(player, n)) return false;
        boolean first = store().recordFind(n, player.getGameProfile().name(), 0, "prop", System.currentTimeMillis());
        if (first) tellAdmins(player.level().getServer(), player, n, 0, "prop");
        Tasks.send(player, paper(player, n, Component.empty(), TaskNetwork.FOUND));
        return true;
    }

    /** Reading a note item (taken, or given by an organiser). */
    static void read(ServerPlayer p, TaskComponents.NoteRef r) {
        Note n = store().note(r.id());
        if (n == null) {
            p.sendOverlayMessage(Component.translatable("message.craftmaster.note_gone").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!mayRead(p, n)) {
            p.sendOverlayMessage(Component.translatable("message.craftmaster.note_unreadable").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (store().recordFind(n, p.getGameProfile().name(), r.piece(), "given", System.currentTimeMillis()))
            tellAdmins(p.level().getServer(), p, n, r.piece(), "given");
        Tasks.send(p, paper(p, n, n.pieces > 1 ? piece(r.piece(), n.pieces) : Component.empty(), 0));
    }

    /** Uncorking a message in a bottle. */
    static void uncork(ServerPlayer p, InteractionHand hand, ItemStack bottle, TaskComponents.NoteRef r) {
        Note n = store().note(r.id());
        if (n == null) {
            p.sendOverlayMessage(Component.translatable("message.craftmaster.note_gone").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!mayRead(p, n)) {
            p.sendOverlayMessage(Component.translatable("message.craftmaster.note_bottle_stuck").withStyle(ChatFormatting.GRAY));
            return;
        }
        Note.Spot pseudo = new Note.Spot(Busts.worldId(p.level()), p.getBlockX(), p.getBlockY(), p.getBlockZ(), null, Note.Method.INSIDE, r.piece());
        boolean once = n.once;
        // The note comes out of the bottle (taken once): the bottle becomes an empty glass bottle
        if (once) {
            bottle.shrink(1);
            Tasks.give(p, new ItemStack(Items.GLASS_BOTTLE));
            Tasks.give(p, taken(n, r.piece()));
        }
        n.once = false;
        try {
            found(p, n, pseudo, "bottle", (ServerLevel) p.level(), p.getX(), p.getEyeY(), p.getZ());
        } finally {
            n.once = once;
        }
    }

    // ---------------------------------------------------------------- hiding (organisers)

    /** The hiding way a block suggests: under a carpet, in a bookshelf, false bottom of a container, inside. */
    static Note.Method methodFor(Level level, BlockPos pos, BlockState state) {
        Block b = state.getBlock();
        if (b instanceof CarpetBlock || b instanceof SlabBlock || b instanceof BasePressurePlateBlock || b instanceof TrapDoorBlock)
            return Note.Method.UNDER;
        if (b == Blocks.BOOKSHELF || b instanceof ChiseledBookShelfBlock || b instanceof LecternBlock) return Note.Method.BOOK;
        if (level.getBlockEntity(pos) instanceof Container) return Note.Method.FALSE_BOTTOM;
        return Note.Method.INSIDE;
    }

    static void hide(ServerPlayer p, TaskComponents.NoteRef r, BlockPos pos, Direction face, boolean pin) {
        Note n = store().note(r.id());
        if (n == null) {
            p.sendSystemMessage(Component.translatable("message.craftmaster.note_gone").withStyle(ChatFormatting.RED));
            return;
        }
        ServerLevel level = (ServerLevel) p.level();
        String world = Busts.worldId(level);
        int piece = n.pieces > 1 ? n.nextPieceToHide() : 0;
        Note.Spot spot;
        if (pin) {
            BlockPos at = pos.relative(face);
            if (!level.getBlockState(at).canBeReplaced()) {
                p.sendOverlayMessage(Component.translatable("message.craftmaster.note_no_room").withStyle(ChatFormatting.RED));
                return;
            }
            level.setBlock(at, TaskContent.PINNED_NOTE.defaultBlockState().setValue(PinnedNoteBlock.FACING, face), Block.UPDATE_ALL);
            spot = new Note.Spot(world, at.getX(), at.getY(), at.getZ(), null, Note.Method.PINNED, piece);
            spot.face = face.getSerializedName();
        } else {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof PinnedNoteBlock) {
                p.sendOverlayMessage(Component.translatable("message.craftmaster.note_pinned_here").withStyle(ChatFormatting.GRAY));
                return;
            }
            for (Map.Entry<Note, Note.Spot> e : store().spotsAt(world, pos.getX(), pos.getY(), pos.getZ()))
                if (e.getKey() == n) {
                    p.sendOverlayMessage(Component.translatable("message.craftmaster.note_already_here").withStyle(ChatFormatting.GRAY));
                    return;
                }
            spot = new Note.Spot(world, pos.getX(), pos.getY(), pos.getZ(), null, methodFor(level, pos, state), piece);
        }
        n.spots.add(spot);
        store().changed();
        hidden(p, n, spot);
    }

    private static void hideBehind(ServerPlayer p, TaskComponents.NoteRef r, Entity entity) {
        Note n = store().note(r.id());
        if (n == null) return;
        if (!store().spotsOn(entity.getUUID().toString()).isEmpty()) {
            p.sendOverlayMessage(Component.translatable("message.craftmaster.note_already_here").withStyle(ChatFormatting.GRAY));
            return;
        }
        Note.Spot spot = new Note.Spot(Busts.worldId(p.level()), entity.getBlockX(), entity.getBlockY(), entity.getBlockZ(),
                entity.getUUID().toString(), Note.Method.BEHIND, n.pieces > 1 ? n.nextPieceToHide() : 0);
        n.spots.add(spot);
        store().changed();
        hidden(p, n, spot);
    }

    private static void hidden(ServerPlayer p, Note n, Note.Spot spot) {
        p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 0.8f);
        MutableComponent line = Component.translatable("message.craftmaster.note_hidden", n.title, where(spot.method), spot.x + " " + spot.y + " " + spot.z)
                .withStyle(ChatFormatting.GREEN);
        if (n.pieces > 1) line.append(Component.literal(" · ").append(piece(spot.piece, n.pieces)).withStyle(ChatFormatting.YELLOW));
        p.sendSystemMessage(line);
        p.sendSystemMessage(Component.translatable("message.craftmaster.note_how." + spot.method().id()).withStyle(ChatFormatting.GRAY));
    }

    // ---------------------------------------------------------------- items

    static ItemStack tool(Note n) {
        ItemStack s = new ItemStack(TaskContent.NOTE);
        s.set(TaskComponents.NOTE, new TaskComponents.NoteRef(n.id, 0, false));
        s.set(DataComponents.ITEM_NAME, Component.literal("✎ " + (n.title.isBlank() ? n.id : n.title)));
        return s;
    }

    static ItemStack taken(Note n, int piece) {
        ItemStack s = new ItemStack(TaskContent.NOTE);
        s.set(TaskComponents.NOTE, new TaskComponents.NoteRef(n.id, piece, true));
        MutableComponent name = n.title.isBlank() ? Component.translatable("item.craftmaster.note") : Component.literal(n.title);
        if (n.pieces > 1) name.append(" (" + (piece + 1) + "/" + n.pieces + ")");
        s.set(DataComponents.ITEM_NAME, name);
        return s;
    }

    static ItemStack bottle(Note n) {
        ItemStack s = new ItemStack(TaskContent.NOTE_BOTTLE);
        s.set(TaskComponents.NOTE, new TaskComponents.NoteRef(n.id, n.pieces > 1 ? n.nextPieceToHide() : 0, false));
        return s;
    }

    // ---------------------------------------------------------------- editor (organisers)

    public static TaskNetwork.NoteView view(Note n) {
        List<Component> spots = new ArrayList<>();
        for (Note.Spot s : n.spots) {
            MutableComponent line = where(s.method).copy().append(" — " + s.world.replace("minecraft:", "") + " " + s.x + " " + s.y + " " + s.z);
            if (n.pieces > 1) line.append(" · ").append(piece(s.piece, n.pieces));
            if (s.takenBy != null) line.append(" · ").append(Component.translatable("note.craftmaster.taken_by", s.takenBy));
            spots.add(line);
        }
        List<Component> finds = new ArrayList<>();
        for (Note.Find f : n.finds) {
            MutableComponent line = Component.literal(f.player() + " — " + TIME.format(Instant.ofEpochMilli(f.at())) + " — ").append(where(f.method()));
            if (n.pieces > 1) line.append(" · ").append(piece(f.piece(), n.pieces));
            finds.add(line);
        }
        return new TaskNetwork.NoteView(n.id, n.title, n.text, n.look, n.readers, List.copyOf(n.players), n.once, n.ink, n.pieces, spots, finds);
    }

    /** Opens the editor of a note (a new one if {@code id} is null). */
    public static void openEditor(ServerPlayer p, String id) {
        if (!ServerPlayNetworking.canSend(p, TaskNetwork.OpenNote.TYPE)) {
            p.sendSystemMessage(Component.translatable("message.craftmaster.no_client_mod").withStyle(ChatFormatting.RED));
            return;
        }
        Note n = id == null ? null : store().note(id);
        TaskNetwork.NoteView v = n != null ? view(n)
                : new TaskNetwork.NoteView("", "", "", "paper", Note.ALL, List.of(), false, false, 1, List.of(), List.of());
        ServerPlayNetworking.send(p, new TaskNetwork.OpenNote(v));
    }

    static void save(ServerPlayer p, TaskNetwork.NoteView v) {
        if (!Permissions.isAdmin(p)) return;
        boolean created = v.id().isEmpty() || store().note(v.id()) == null;
        String title = v.title().strip().isEmpty() ? "Note" : v.title().strip();
        Note n = created ? store().createNote(title, System.currentTimeMillis()) : store().note(v.id());
        n.title = title.length() > 64 ? title.substring(0, 64) : title;
        n.text = Task.cleanText(v.text());
        n.look = Note.LOOKS.contains(v.look()) ? v.look() : "paper";
        n.readers = Note.READERS.contains(v.readers()) ? v.readers() : Note.ALL;
        n.players = new ArrayList<>(v.players().stream().map(String::strip).filter(s -> !s.isEmpty()).toList());
        n.once = v.once();
        n.ink = v.ink();
        n.pieces = Math.max(1, Math.min(Note.MAX_PIECES, v.pieces()));
        store().changed();
        p.sendSystemMessage(Component.translatable(created ? "message.craftmaster.note_created" : "message.craftmaster.note_saved", n.title, n.id)
                .withStyle(ChatFormatting.GREEN));
        if (created) Tasks.give(p, tool(n));
    }

    /** Organisers' orders on a note: give | bottle | reset | unhide | delete | edit. */
    public static void order(ServerPlayer p, String id, String order) {
        if (!Permissions.isAdmin(p)) return;
        Note n = store().note(id);
        if (n == null) {
            p.sendSystemMessage(Component.translatable("message.craftmaster.note_unknown", id).withStyle(ChatFormatting.RED));
            return;
        }
        String line = switch (order) {
            case "give" -> {
                Tasks.give(p, tool(n));
                yield "message.craftmaster.note_tool_given";
            }
            case "bottle" -> {
                Tasks.give(p, bottle(n));
                yield "message.craftmaster.note_bottled";
            }
            case "reset" -> {
                reset(p.level().getServer(), n);
                yield "message.craftmaster.note_reset";
            }
            case "unhide" -> {
                unhide(p.level().getServer(), n);
                yield "message.craftmaster.note_unhidden_all";
            }
            case "delete" -> {
                unhide(p.level().getServer(), n);
                store().deleteNote(n.id);
                yield "message.craftmaster.note_deleted";
            }
            case "edit" -> {
                openEditor(p, n.id);
                yield null;
            }
            default -> null;
        };
        if (line != null) p.sendSystemMessage(Component.translatable(line, n.title).withStyle(ChatFormatting.GREEN));
    }

    /** Forgets the finds and puts the taken notes back (pinned sheets included). */
    static void reset(MinecraftServer server, Note n) {
        for (Note.Spot s : n.spots) {
            if (s.takenBy == null || s.method() != Note.Method.PINNED) continue;
            ServerLevel level = Busts.level(server, s.world);
            Direction face = s.face == null ? null : Direction.byName(s.face);
            BlockPos pos = new BlockPos(s.x, s.y, s.z);
            if (level != null && face != null && level.getBlockState(pos).canBeReplaced())
                level.setBlock(pos, TaskContent.PINNED_NOTE.defaultBlockState().setValue(PinnedNoteBlock.FACING, face), Block.UPDATE_ALL);
        }
        store().resetNote(n);
    }

    /** Removes every hiding place of the note (and its pinned sheets). */
    static void unhide(MinecraftServer server, Note n) {
        List<Note.Spot> spots = new ArrayList<>(n.spots);
        n.spots.clear();
        store().changed();
        for (Note.Spot s : spots) {
            if (s.method() != Note.Method.PINNED) continue;
            ServerLevel level = Busts.level(server, s.world);
            BlockPos pos = new BlockPos(s.x, s.y, s.z);
            if (level != null && level.getBlockState(pos).getBlock() instanceof PinnedNoteBlock) level.removeBlock(pos, false);
        }
    }

    // ---------------------------------------------------------------- organisers see their hiding places

    static void tick(MinecraftServer server) {
        if (store() == null || server.getTickCount() % 10 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!NoteItem.isTool(p, p.getMainHandItem())) continue;
            String mine = NoteItem.ref(p.getMainHandItem()).id();
            ServerLevel level = (ServerLevel) p.level();
            String world = Busts.worldId(level);
            for (Note n : store().notes().values()) {
                for (Note.Spot s : n.spots) {
                    if (!s.world.equals(world) || p.distanceToSqr(s.x + 0.5, s.y + 0.5, s.z + 0.5) > 48 * 48) continue;
                    ParticleOptions particle = s.takenBy != null ? ParticleTypes.SMOKE : n.id.equals(mine) ? ParticleTypes.WAX_ON : ParticleTypes.HAPPY_VILLAGER;
                    double x = s.x + 0.5, y = s.y + 0.5, z = s.z + 0.5;
                    if (s.entity != null) {
                        Entity e = level.getEntity(java.util.UUID.fromString(s.entity));
                        if (e == null) continue;
                        x = e.getX();
                        y = e.getY();
                        z = e.getZ();
                    }
                    level.sendParticles(p, particle, true, false, x, y, z, 3, 0.25, 0.25, 0.25, 0);
                }
            }
        }
    }
}
