package fr.lordfinn.craftmaster.client.fx;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.client.broadcast.Broadcast;
import fr.lordfinn.craftmaster.fx.FxContent;
import fr.lordfinn.craftmaster.fx.FxMixer;
import fr.lordfinn.craftmaster.fx.FxNetwork;
import fr.lordfinn.craftmaster.fx.ScreenFx;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Screen effects of this game: post-processing of the world view only (the HUD, the broadcast overlay and the
 * screens are drawn afterwards, so they stay sharp). Each effect is the post effect {@code craftmaster:fx/<id>}
 * (assets/craftmaster/post_effect/fx/), with an intensity uniform faded smoothly by {@link FxMixer} — except
 * "citrouille", which is vanilla's own carved pumpkin overlay ({@link FxOverlay}).
 *
 * <p>What is shown:
 * <ul>
 *   <li>in broadcast mode: the <b>broadcast effect</b> ({@link #setBroadcastEffect}, or {@code /fx diffusion …}
 *       from the server), the current shot's own effect (intros) added to it, plus the local try-out ({@code /essaifx});</li>
 *   <li>otherwise: the effect forced on this player by an organiser ({@code /fx <joueur> …}), the effect of what
 *       they wear on their head (glasses, masks: data component {@code craftmaster:screen_fx}) and the try-out,
 *       combined.</li>
 * </ul>
 */
public final class ScreenEffects {

    private ScreenEffects() {}

    /** One layer: an effect list and when it stops (System.nanoTime, Long.MAX_VALUE = until changed). */
    private record Slot(List<String> effects, long until) {
        static final Slot NONE = new Slot(List.of(), Long.MAX_VALUE);

        List<String> now(long t) {
            return t < until ? effects : List.of();
        }
    }

    private static final FxMixer MIXER = new FxMixer();
    private static Slot broadcast = Slot.NONE, shot = Slot.NONE, forced = Slot.NONE, tryout = Slot.NONE;
    private static float fade = FxMixer.DEFAULT_FADE;
    private static long lastFrame = -1;
    private static final long START = System.nanoTime();
    private static List<String> visible = List.of();

    // ---------------------------------------------------------------- API

    /**
     * The effect of the broadcast view (games in broadcast mode only): an id ("sepia") or a "+"-joined combination
     * ("fisheye+masque_rond"), French names and presets accepted ("noir et blanc", "judas"); null, "" or "aucun" =
     * none. Fades in / out over 0.6 s. Unknown names are ignored. Used by the intros and camera moves.
     */
    public static void setBroadcastEffect(String idOrNull) {
        setBroadcastEffect(idOrNull, FxMixer.DEFAULT_FADE);
    }

    /** Same, with the fade duration in seconds (0 = at once). */
    public static void setBroadcastEffect(String idOrNull, float fadeSeconds) {
        // The shot's own effect (intros, camera moves): added to the operator's /fx diffusion, never replacing it
        shot = new Slot(ScreenFx.parseLenient(idOrNull), Long.MAX_VALUE);
        fade = Math.max(0f, fadeSeconds);
    }

    /** The broadcast effect currently asked ("" = none), as a canonical combination. */
    public static String broadcastEffect() {
        long t = System.nanoTime();
        Set<String> all = new LinkedHashSet<>(broadcast.now(t));
        all.addAll(shot.now(t));
        return ScreenFx.join(new ArrayList<>(all));
    }

    /** Local try-out on this game's own view ({@code /essaifx}), 0 seconds = until changed. */
    public static void tryOut(String spec, int seconds) {
        tryout = new Slot(ScreenFx.parseLenient(spec), seconds > 0 ? System.nanoTime() + seconds * 1_000_000_000L : Long.MAX_VALUE);
        fade = FxMixer.DEFAULT_FADE;
    }

    /** An order from the server. */
    static void receive(FxNetwork.Order o) {
        Slot s = new Slot(ScreenFx.parseLenient(o.effect()), o.ticks() > 0 ? System.nanoTime() + o.ticks() * 50_000_000L : Long.MAX_VALUE);
        if (o.layer() == FxNetwork.LAYER_BROADCAST) broadcast = s;
        else forced = s;
        fade = o.fadeTicks() / 20f;
    }

    static void reset() {
        broadcast = shot = forced = tryout = Slot.NONE;
        MIXER.clear();
        visible = List.of();
    }

    /** Effects drawn this frame, in drawing order. */
    public static List<String> visible() {
        return visible;
    }

    /** Current (eased) intensity of an effect, 0 when off. */
    public static float intensity(String id) {
        return FxMixer.eased(MIXER.intensity(id));
    }

    /** Seconds since the game started (for the animated effects), wrapped to keep float precision. */
    public static float time() {
        return (float) (((System.nanoTime() - START) / 1e9) % 3600.0);
    }

    // ---------------------------------------------------------------- every frame (GameRenderer.update)

    /** Advances the fades and returns the post effects to draw this frame. */
    public static List<Identifier> frame() {
        long t = System.nanoTime();
        float dt = lastFrame < 0 ? 0f : Math.min(0.25f, (t - lastFrame) / 1e9f);
        lastFrame = t;
        Set<String> wanted = new LinkedHashSet<>(tryout.now(t));
        Minecraft mc = Minecraft.getInstance();
        if (Broadcast.active()) {
            wanted.addAll(broadcast.now(t));
            wanted.addAll(shot.now(t));
        } else if (mc.player != null) {
            wanted.addAll(forced.now(t));
            ItemStack head = mc.player.getItemBySlot(EquipmentSlot.HEAD);
            // An item with a vanilla camera overlay (the pumpkin mask) already gets it from vanilla: not twice
            Equippable eq = head.get(DataComponents.EQUIPPABLE);
            boolean vanillaOverlay = eq != null && eq.cameraOverlay().isPresent();
            for (String id : ScreenFx.parseLenient(FxContent.effectOf(head)))
                if (!(vanillaOverlay && id.equals(FxOverlay.PUMPKIN))) wanted.add(id);
        }
        MIXER.want(wanted, fade);
        MIXER.advance(dt);
        visible = MIXER.visible();
        if (visible.isEmpty()) return List.of();
        List<Identifier> out = new ArrayList<>(visible.size());
        // "citrouille" is vanilla's pumpkin overlay, drawn on the HUD by FxOverlay, not a post effect
        for (String id : visible) if (!id.equals(FxOverlay.PUMPKIN)) out.add(CraftMaster.id("fx/" + id));
        return out;
    }

    /** Field of view with the effects: a fisheye sees wider, binoculars zoom in. */
    public static float fov(float fov) {
        float fish = intensity("fisheye"), bino = intensity("jumelles");
        if (fish > 0) fov += (Math.min(fov * 1.8f, 135f) - fov) * fish;
        if (bino > 0) fov *= 1f - 0.55f * bino;
        return fov;
    }
}
