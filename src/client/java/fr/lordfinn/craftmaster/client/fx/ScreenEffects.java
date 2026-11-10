package fr.lordfinn.craftmaster.client.fx;

/**
 * STUB (chantier E): the screen effects system is built by another chantier, which replaces this class. The TV
 * direction calls {@link #setBroadcastEffect} whenever the shot on screen changes, with the shot's effect id
 * ("noir_blanc", "sepia", "pixel", "fisheye", "vignette", "masque_rond", "citrouille"…) or null for none.
 */
public final class ScreenEffects {

    private ScreenEffects() {}

    /** The effect to apply to the broadcast picture from now on; null = none. Unknown ids must be ignored. */
    public static void setBroadcastEffect(String idOrNull) {
        // Nothing here: the real effects come with the effects chantier
    }
}
