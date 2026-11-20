package fr.lordfinn.craftmaster.client.broadcast;

import fr.lordfinn.craftmaster.CraftMaster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The show's theme ({@code craftmaster:show.theme}, sound file {@code assets/craftmaster/sounds/show/theme.ogg}, which
 * a resource pack can replace) played during the credits: heard the same wherever the player is (no attenuation,
 * not positional), on the "Jukebox / Note blocks" volume slider.
 */
public final class ThemeSound {

    private static SoundInstance playing;

    private ThemeSound() {}

    /** Plays the theme, or the sound {@code soundId} ("minecraft:music_disc.otherside"; "" = the theme). */
    static void play(String soundId) {
        stop();
        net.minecraft.resources.Identifier id = soundId == null || soundId.isBlank() ? null : net.minecraft.resources.Identifier.tryParse(soundId);
        playing = new SimpleSoundInstance(id != null ? id : CraftMaster.id("show.theme"), SoundSource.RECORDS, 1f, 1f, RandomSource.create(),
                false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        Minecraft.getInstance().getSoundManager().play(playing);
        // Which music, and whether the game knows it (a missing sound plays nothing, silently)
        CraftMaster.LOG.info("[Réalisation] Musique {}{}", playing.getIdentifier(),
                Minecraft.getInstance().getSoundManager().getSoundEvent(playing.getIdentifier()) == null ? " : son inconnu du jeu" : "");
    }

    static void stop() {
        if (playing != null) Minecraft.getInstance().getSoundManager().stop(playing);
        playing = null;
    }
}
