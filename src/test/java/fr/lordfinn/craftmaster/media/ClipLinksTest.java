package fr.lordfinn.craftmaster.media;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClipLinksTest {

    @Test
    void clipAddresses() {
        assertEquals("clip-20261113-031602", ClipLinks.id("cmclip:clip-20261113-031602"));
        assertEquals("clip-1", ClipLinks.id("CMCLIP:Clip-1"));
        assertNull(ClipLinks.id("cmclip:../secret"));
        assertNull(ClipLinks.id("https://x.fr/a.mp4"));
        assertEquals("cmclip:abc", ClipLinks.of("abc"));
    }

    @Test
    void twitchClipLinksInChat() {
        assertEquals(List.of("https://clips.twitch.tv/ThoughtfulEndearingCoffeePipeHype-EtPf7yWwiWHpJCaj"),
                ClipLinks.twitchClips("regarde https://www.twitch.tv/jynxzi/clip/ThoughtfulEndearingCoffeePipeHype-EtPf7yWwiWHpJCaj?filter=clips lol"));
        assertEquals(List.of("https://clips.twitch.tv/AbcDef-12_x", "https://clips.twitch.tv/Other"),
                ClipLinks.twitchClips("https://clips.twitch.tv/AbcDef-12_x et https://m.twitch.tv/x_y/clip/Other https://clips.twitch.tv/AbcDef-12_x"));
        assertTrue(ClipLinks.twitchClips("https://www.twitch.tv/lordfinn_ en direct").isEmpty());
        assertTrue(ClipLinks.twitchClips(null).isEmpty());
    }

    @Test
    void twitchChannels() {
        assertEquals("lordfinn_", ClipLinks.twitchChannel("https://www.twitch.tv/LordFinn_"));
        assertEquals("ensuri", ClipLinks.twitchChannel("twitch.tv/ensuri/videos"));
        assertEquals("ensuri", ClipLinks.twitchChannel("@Ensuri"));
        assertEquals("", ClipLinks.twitchChannel("a b"));
        assertEquals("", ClipLinks.twitchChannel("xy"));
    }
}
