package fr.lordfinn.craftmaster.trial.kit;

import com.mojang.math.Transformation;
import fr.lordfinn.craftmaster.mixin.DisplayAccessor;
import fr.lordfinn.craftmaster.mixin.InteractionAccessor;
import fr.lordfinn.craftmaster.mixin.ItemDisplayAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Two pieces of the trial kit, made of vanilla entities only (nothing to install on the players' side, copied with
 * their arena, state kept in the entities' tags so a rebuilt copy starts over):
 * <ul>
 *   <li><b>Password-locked pickup</b>: an item lying on the ground (an item display) with an interaction spot.
 *       Clicking it opens the game's dialog window: the right password gives the item (it disappears from the
 *       ground). Rules: {@code initiale:C} = any word starting with C (each word once per player), {@code mot:xyz}
 *       = exactly that word, {@code libre} = anything. A wrong word just asks again.</li>
 *   <li><b>Vase that shows its fill</b>: an interaction spot over a pot; each picked item put in it appears in its
 *       bouquet, up to its target; full = the player's attempt ends ({@link Finish}) and a golem nearby offers a
 *       flower.</li>
 * </ul>
 */
public final class Pickups {

    private Pickups() {}

    public static final String PICKUP = "cm_pickup", PICKUP_SHOW = "cm_pickup_show", VASE = "cm_vase", BOUQUET = "cm_bouquet";
    private static final String RULE = "cm_pw:", LABEL = "cm_label:", TARGET = "cm_target:", COUNT = "cm_count:";
    /** Custom data key of a picked item: the arena it comes from ("" = none). */
    public static final String PICKED_KEY = "craftmaster_pickup";

    /** Words already used, per arena and player. */
    private static final Map<String, Set<String>> USED = new HashMap<>();

    // ---------------------------------------------------------------- spawning

    static Interaction spot(ServerLevel level, Vec3 pos, float width, float height, String... tags) {
        Interaction e = net.minecraft.world.entity.EntityTypes.INTERACTION.create(level, EntitySpawnReason.COMMAND);
        e.snapTo(pos.x, pos.y, pos.z, 0, 0);
        InteractionAccessor acc = (InteractionAccessor) e;
        acc.craftmaster$setWidth(width);
        acc.craftmaster$setHeight(height);
        acc.craftmaster$setResponse(true);
        for (String t : tags) e.addTag(t);
        level.addFreshEntity(e);
        return e;
    }

    static Display.ItemDisplay show(ServerLevel level, Vec3 pos, ItemStack stack, Quaternionf rotation, float scale, String tag) {
        Display.ItemDisplay d = net.minecraft.world.entity.EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.COMMAND);
        d.snapTo(pos.x, pos.y, pos.z, 0, 0);
        ((ItemDisplayAccessor) d).craftmaster$setItemStack(stack.copyWithCount(1));
        ((ItemDisplayAccessor) d).craftmaster$setItemTransform(ItemDisplayContext.FIXED);
        ((DisplayAccessor) d).craftmaster$setTransformation(new Transformation(new Vector3f(), rotation, new Vector3f(scale, scale, scale), new Quaternionf()));
        d.addTag(tag);
        level.addFreshEntity(d);
        return d;
    }

    /**
     * An item lying flat at {@code pos} (on top of a block), locked by {@code rule}, named {@code label}
     * ("Fleur n°3").
     */
    public static Interaction placePickup(ServerLevel level, Vec3 pos, ItemStack look, String rule, String label, float yawDegrees, String number) {
        Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(-yawDegrees)).rotateX((float) Math.toRadians(-90));
        show(level, pos.add(0, 0.03, 0), look, rot, 0.75f, PICKUP_SHOW);
        if (number != null && !number.isEmpty()) {
            Display.TextDisplay t = net.minecraft.world.entity.EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.COMMAND);
            t.snapTo(pos.x, pos.y + 0.45, pos.z, 0, 0);
            ((fr.lordfinn.craftmaster.mixin.TextDisplayAccessor) t).craftmaster$setText(Component.literal(number).withStyle(ChatFormatting.BOLD));
            ((DisplayAccessor) t).craftmaster$setBillboardConstraints(Display.BillboardConstraints.CENTER);
            ((DisplayAccessor) t).craftmaster$setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.8f, 0.8f, 0.8f), new Quaternionf()));
            t.addTag(PICKUP_SHOW);
            level.addFreshEntity(t);
        }
        Interaction spot = spot(level, pos, 0.8f, 0.3f, PICKUP, RULE + rule);
        spot.addTag(LABEL + label);
        return spot;
    }

    /** A vase spot over a pot standing at {@code potCenterBottom}, filled up to {@code target}. */
    public static Interaction placeVase(ServerLevel level, Vec3 potCenterBottom, int target) {
        return spot(level, potCenterBottom.add(0, -0.01, 0), 1.02f, 1.35f, VASE, TARGET + target, COUNT + 0);
    }

    // ---------------------------------------------------------------- tags

    private static String tag(Entity e, String prefix) {
        for (String t : e.entityTags()) if (t.startsWith(prefix)) return t.substring(prefix.length());
        return null;
    }

    private static int intTag(Entity e, String prefix, int def) {
        try {
            String s = tag(e, prefix);
            return s == null ? def : Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private static void setTag(Entity e, String prefix, String value) {
        for (String t : List.copyOf(e.entityTags())) if (t.startsWith(prefix)) e.removeTag(t);
        e.addTag(prefix + value);
    }

    private static String labelOf(Entity spot) {
        String l = tag(spot, LABEL);
        return l == null || l.isEmpty() ? "Objet" : l;
    }

    public static String norm(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }

    // ---------------------------------------------------------------- use

    /** Right or left click on an interaction entity (from the entity callbacks). */
    public static InteractionResult onUse(Player player, Level level, InteractionHand hand, Entity entity) {
        if (!(entity instanceof Interaction spot)) return InteractionResult.PASS;
        boolean pickup = spot.entityTags().contains(PICKUP), vase = spot.entityTags().contains(VASE);
        if (!pickup && !vase) return InteractionResult.PASS;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || player.isSpectator()) return InteractionResult.SUCCESS;
        Arenas.Owner o = Arenas.ownerAt(level, spot.blockPosition());
        if (!Arenas.mayUse(sp, o)) {
            sp.sendOverlayMessage(Component.literal("Ce n'est pas ton parcours").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        if (pickup) ask(sp, spot, null);
        else fill(sp, spot, o);
        return InteractionResult.SUCCESS;
    }

    private static void ask(ServerPlayer p, Interaction spot, String error) {
        String label = labelOf(spot);
        List<Component> lines = error == null ? List.of() : List.of(Component.literal(error).withStyle(ChatFormatting.RED));
        Dialogs.ask(p, Component.literal(label), lines, Component.literal("Mot de passe"), 40, (player, typed) -> answer(player, spot, typed));
    }

    private static void answer(ServerPlayer p, Interaction spot, String typed) {
        if (spot.isRemoved() || typed.isEmpty()) return;
        String rule = tag(spot, RULE);
        String label = labelOf(spot);
        Arenas.Owner o = Arenas.ownerAt(p.level(), spot.blockPosition());
        String arena = o == null ? "" : o.arena().name;
        String name = Arenas.playerName(p);
        String word = norm(typed);
        Set<String> used = USED.computeIfAbsent(arena.toLowerCase(Locale.ROOT) + "|" + name.toLowerCase(Locale.ROOT), k -> new HashSet<>());
        String why = check(rule, word, used);
        if (why != null) {
            fr.lordfinn.craftmaster.trial.TrialLog.add(p.level().getServer(), name + " — " + label + " : « " + typed + " » refusé (" + why + ")");
            ask(p, spot, "✗ « " + typed + " » : mauvais mot de passe");
            return;
        }
        if (rule != null && rule.startsWith("initiale:")) used.add(word);
        take(p, spot, arena, label, typed);
    }

    /** Why the word is refused (for the organisers' log), or null if it opens. */
    static String check(String rule, String word, Set<String> used) {
        if (rule == null || rule.equals("libre")) return word.isEmpty() ? "vide" : null;
        if (rule.startsWith("mot:")) return word.equals(norm(rule.substring(4))) ? null : "pas le mot";
        if (rule.startsWith("initiale:")) {
            String letter = norm(rule.substring(9));
            if (!word.matches("[a-z][a-z'\\-]*")) return "pas un mot";
            if (word.replaceAll("[^a-z]", "").length() < 3) return "moins de 3 lettres";
            if (!word.startsWith(letter)) return "ne commence pas par " + letter.toUpperCase(Locale.ROOT);
            if (used.contains(word)) return "déjà utilisé";
            return null;
        }
        return "règle inconnue";
    }

    private static void take(ServerPlayer p, Interaction spot, String arena, String label, String typed) {
        ServerLevel level = (ServerLevel) p.level();
        ItemStack look = ItemStack.EMPTY;
        for (Display d : level.getEntitiesOfClass(Display.class, spot.getBoundingBox().inflate(0.4, 0.6, 0.4),
                d -> d.entityTags().contains(PICKUP_SHOW))) {
            if (look.isEmpty() && d instanceof Display.ItemDisplay item) look = ((ItemDisplayAccessor) item).craftmaster$getItemStack().copy();
            d.discard();
        }
        spot.discard();
        if (look.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, look, t -> t.putString(PICKED_KEY, arena));
        look.set(DataComponents.CUSTOM_NAME, Component.literal(label).withStyle(st -> st.withItalic(false)));
        if (!p.getInventory().add(look)) p.spawnAtLocation(level, look);
        level.playSound(null, spot.getX(), spot.getY(), spot.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 1.3f);
        p.sendSystemMessage(Component.literal("✿ " + label + " prise avec « " + typed + " »").withStyle(ChatFormatting.GREEN));
        String name = Arenas.playerName(p);
        Finish.tellOrganisers(level.getServer(), p, Component.literal("✿ ").withStyle(Finish.colour(name)).append(Finish.name(name))
                        .append(Component.literal(" : " + label + " avec « " + typed + " »").withStyle(Finish.colour(name))),
                name + " — " + label + " avec « " + typed + " »");
    }

    private static boolean picked(ItemStack st) {
        CustomData d = st.get(DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().contains(PICKED_KEY);
    }

    private static String pickedArena(ItemStack st) {
        CompoundTag t = st.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return t.getStringOr(PICKED_KEY, "");
    }

    private static void fill(ServerPlayer p, Interaction vase, Arenas.Owner o) {
        int target = intTag(vase, TARGET, 26), count = intTag(vase, COUNT, 0);
        ItemStack held = p.getMainHandItem();
        String arena = o == null ? "" : o.arena().name;
        if (!picked(held) || !pickedArena(held).equalsIgnoreCase(arena)) {
            p.sendOverlayMessage(Component.literal("Vase : " + count + " / " + target).withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (count >= target) {
            p.sendOverlayMessage(Component.literal("Le vase est plein").withStyle(ChatFormatting.GRAY));
            return;
        }
        ServerLevel level = (ServerLevel) p.level();
        ItemStack flower = held.copyWithCount(1);
        held.shrink(1);
        bouquet(level, vase, count, flower);
        count++;
        setTag(vase, COUNT, String.valueOf(count));
        level.playSound(null, vase.getX(), vase.getY() + 1, vase.getZ(), SoundEvents.FLOWERING_AZALEA_PLACE, SoundSource.BLOCKS, 1f, 1.1f);
        p.sendOverlayMessage(Component.literal("✿ " + count + " / " + target).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        if (count < target) return;
        level.playSound(null, vase.getX(), vase.getY() + 1, vase.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1f, 1.5f);
        for (IronGolem g : level.getEntitiesOfClass(IronGolem.class, new AABB(vase.blockPosition()).inflate(8))) g.offerFlower(true);
        Finish.done(p, "a rempli le vase (" + target + " fleurs)");
    }

    /**
     * Flower number {@code i} of the bouquet, as the game's own flower block (crossed planes: seen from every side),
     * its stem in the pot's mouth: on a golden-angle spiral, each new one a little further out, lower and leaning
     * outwards, each turned its own way, so the bunch grows round.
     */
    private static void bouquet(ServerLevel level, Interaction vase, int i, ItemStack flower) {
        double angle = i * 2.39996;
        double k = Math.sqrt(i / 26.0);
        double r = 0.03 + 0.17 * k;
        double h = 0.92 - 0.06 * k + (i % 3) * 0.025;
        float scale = 0.62f;
        float tilt = (float) (0.15 + 0.55 * k);
        Quaternionf rot = new Quaternionf().rotateAxis(tilt, (float) Math.sin(angle), 0f, (float) -Math.cos(angle)).rotateY((float) (i * 1.7));
        Vector3f centre = new Vector3f(0.5f * scale, 0f, 0.5f * scale);
        rot.transform(centre);
        Vec3 at = vase.position().add(Math.cos(angle) * r, h, Math.sin(angle) * r);
        if (!(flower.getItem() instanceof net.minecraft.world.item.BlockItem bi)) {
            show(level, at, flower, rot, 0.5f, BOUQUET);
            return;
        }
        Display.BlockDisplay d = net.minecraft.world.entity.EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.COMMAND);
        d.snapTo(at.x, at.y, at.z, 0, 0);
        ((fr.lordfinn.craftmaster.mixin.BlockDisplayAccessor) d).craftmaster$setBlockState(bi.getBlock().defaultBlockState());
        ((DisplayAccessor) d).craftmaster$setTransformation(new Transformation(centre.negate(), rot, new Vector3f(scale, scale, scale), new Quaternionf()));
        d.addTag(BOUQUET);
        level.addFreshEntity(d);
    }

    /** The arena was rebuilt: its words can be used again. */
    static void forget(String arena) {
        USED.keySet().removeIf(k -> k.startsWith(arena.toLowerCase(Locale.ROOT) + "|"));
    }
}
