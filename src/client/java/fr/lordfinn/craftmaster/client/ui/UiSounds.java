package fr.lordfinn.craftmaster.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** The UI kit's sounds (vanilla ones, played on the interface channel). */
public final class UiSounds {

    private UiSounds() {}

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    public static void click() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.5f);
    }

    /** A head picked up / put down. */
    public static void pick() {
        play(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.2f, 0.8f);
    }

    public static void drop() {
        play(SoundEvents.ITEM_FRAME_ADD_ITEM, 1f, 0.8f);
    }

    /** +1 rises, -1 falls; the pitch follows the new score. */
    public static void score(int newScore, boolean up) {
        float pitch = (float) Math.pow(2, Math.max(-10, Math.min(10, newScore)) / 12.0);
        play(up ? SoundEvents.NOTE_BLOCK_CHIME.value() : SoundEvents.NOTE_BLOCK_BASS.value(), pitch, 0.6f);
    }

    /** Scores sent. */
    public static void confirm() {
        play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5f, 0.6f);
    }

    public static void error() {
        play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.7f);
    }

    public static void page() {
        play(SoundEvents.BOOK_PAGE_TURN, 1f, 0.8f);
    }
}
