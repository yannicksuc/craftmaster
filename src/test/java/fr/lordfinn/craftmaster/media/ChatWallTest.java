package fr.lordfinn.craftmaster.media;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatWallTest {

    @Test
    void addressGivesTheMode() {
        assertEquals(ChatWall.Mode.TWITCH, ChatWall.mode("chat:twitch"));
        assertEquals(ChatWall.Mode.JEU, ChatWall.mode(" CHAT:Jeu "));
        assertEquals(ChatWall.Mode.JEU, ChatWall.mode("chat:game"));
        assertEquals(ChatWall.Mode.TOUT, ChatWall.mode("chat:tout"));
        assertEquals(ChatWall.Mode.TOUT, ChatWall.mode("chat"));
        assertEquals(ChatWall.Mode.TOUT, ChatWall.mode("chat:"));
        assertNull(ChatWall.mode("chat:youtube"));
        assertNull(ChatWall.mode("https://example.org/chat.png"));
        assertNull(ChatWall.mode(""));
        assertNull(ChatWall.mode(null));
        assertEquals("chat:twitch", ChatWall.Mode.TWITCH.address());
        assertTrue(ChatWall.Mode.TOUT.shows(ChatWall.Source.JEU) && ChatWall.Mode.TOUT.shows(ChatWall.Source.TWITCH));
        assertFalse(ChatWall.Mode.TWITCH.shows(ChatWall.Source.JEU));
        assertFalse(ChatWall.Mode.JEU.shows(ChatWall.Source.TWITCH));
    }

    @Test
    void imageAddressAcceptsChatWalls() {
        assertNull(fr.lordfinn.craftmaster.studio.ImageRequest.urlError("chat:twitch"));
        assertNull(fr.lordfinn.craftmaster.studio.ImageRequest.urlError("chat"));
        assertNotNull(fr.lordfinn.craftmaster.studio.ImageRequest.urlError("chat:facebook"));
    }

    @Test
    void textIsCleaned() {
        assertEquals("salut tout le monde", ChatWall.cleanText("  salut\u0000 tout\t\tle\nmonde "));
        assertEquals("rouge", ChatWall.cleanText("§crouge"));
        assertEquals("abc", ChatWall.cleanText("a‮b​c"));
        assertNull(ChatWall.cleanText("!tache"));
        assertNull(ChatWall.cleanText("  !so lordfinn"));
        assertNull(ChatWall.cleanText(" \u0001 "));
        assertNull(ChatWall.cleanText(null));
        String long_ = "x".repeat(500);
        String cut = ChatWall.cleanText(long_);
        assertEquals(ChatWall.MAX_TEXT, cut.codePointCount(0, cut.length()));
        assertTrue(cut.endsWith("…"));
        // Emoji (surrogate pairs) are kept whole
        assertEquals("gg 🎉", ChatWall.cleanText("gg 🎉"));
    }

    @Test
    void authorIsCleaned() {
        assertEquals("Bob", ChatWall.cleanAuthor(" B\u0007ob "));
        assertEquals("?", ChatWall.cleanAuthor(""));
        assertEquals(ChatWall.MAX_AUTHOR, ChatWall.cleanAuthor("a".repeat(60)).length());
    }

    @Test
    void colours() {
        assertEquals(ChatWall.nameColor("LordFinn"), ChatWall.nameColor("lordfinn"));
        assertTrue(ChatWall.nameColor("Ensuri") != ChatWall.nameColor("Cyllu") || ChatWall.nameColor("Ensuri") != ChatWall.nameColor("JustEdible"));
        assertEquals(0x1E90FF, ChatWall.parseColor("#1E90FF"));
        assertEquals(ChatWall.NO_COLOR, ChatWall.parseColor(""));
        assertEquals(ChatWall.NO_COLOR, ChatWall.parseColor("blue"));
        // Dark blue becomes readable on the dark panel, a bright colour stays
        int fixed = ChatWall.readable(0x0000FF);
        assertTrue(((fixed >> 16) & 0xFF) > 0x40 && (fixed & 0xFF) == 0xFF);
        assertEquals(0xFFD700, ChatWall.readable(0xFFD700));
        assertEquals(ChatWall.nameColor("x"), ChatWall.authorColor("x", ChatWall.NO_COLOR));
    }

    private static ChatWall.Line line(ChatWall.Source s, String id, String author) {
        return new ChatWall.Line(s, id, author, 0xFFFFFF, "msg " + id);
    }

    @Test
    void historyKeepsTheLastPerSourceAndEachIdOnce() {
        ChatWall.History h = new ChatWall.History(3);
        assertTrue(h.add(line(ChatWall.Source.JEU, "j1", "a")));
        for (int i = 1; i <= 5; i++) assertTrue(h.add(line(ChatWall.Source.TWITCH, "t" + i, "u" + i)));
        assertFalse(h.add(line(ChatWall.Source.TWITCH, "t5", "u5")));
        List<String> ids = h.all().stream().map(ChatWall.Line::id).toList();
        // The busy Twitch chat does not push the game's message away; arrival order is kept
        assertEquals(List.of("j1", "t3", "t4", "t5"), ids);
        assertTrue(h.removeId("t4"));
        assertFalse(h.removeId("t4"));
        h.add(line(ChatWall.Source.TWITCH, "t6", "u3"));
        assertTrue(h.removeAuthor(ChatWall.Source.TWITCH, "U3"));
        assertEquals(List.of("j1", "t5"), h.all().stream().map(ChatWall.Line::id).toList());
        assertTrue(h.clear(ChatWall.Source.TWITCH));
        assertEquals(List.of("j1"), h.all().stream().map(ChatWall.Line::id).toList());
    }

    @Test
    void twitchTagsAndModeration() {
        String msg = "@badge-info=;color=#FF4500;display-name=Le\\sBob;id=abc-123;mod=0 :lebob!lebob@lebob.tmi.twitch.tv PRIVMSG #lordfinn :salut";
        var t = ChatWall.tags(msg);
        assertEquals("#FF4500", t.get("color"));
        assertEquals("Le Bob", t.get("display-name"));
        assertEquals("abc-123", t.get("id"));
        assertTrue(ChatWall.tags(":a!a@a PRIVMSG #b :c").isEmpty());
        assertNull(ChatWall.moderation(msg));

        var del = ChatWall.moderation("@login=bob;target-msg-id=abc-123 :tmi.twitch.tv CLEARMSG #lordfinn :bad words");
        assertEquals(new ChatWall.Moderation("abc-123", ""), del);
        var ban = ChatWall.moderation("@ban-duration=600 :tmi.twitch.tv CLEARCHAT #lordfinn :bob");
        assertEquals("bob", ban.user());
        assertFalse(ban.clearAll());
        assertTrue(ChatWall.moderation(":tmi.twitch.tv CLEARCHAT #lordfinn").clearAll());
        assertNull(ChatWall.moderation("PING :tmi.twitch.tv"));
    }

    /** One "pixel" per character: easy to reason about. */
    private static List<String> wrap(String text, int width) {
        return ChatWall.wrap(text, width, String::length).stream().map(r -> text.substring(r[0], r[1])).toList();
    }

    @Test
    void wrapsOnWordsAndCutsLongWords() {
        assertEquals(List.of("Bob: salut", "tout le", "monde"), wrap("Bob: salut tout le monde", 10));
        assertEquals(List.of("abcde", "fghij", "k"), wrap("abcdefghijk", 5));
        assertEquals(List.of("ab", "cdefg", "hij"), wrap("ab cdefghij", 5));
        assertEquals(List.of("court"), wrap("court", 40));
        assertEquals(List.of(), wrap("", 10));
        // Not even one character fits: still one per line, never an endless loop
        assertEquals(List.of("a", "b"), wrap("ab", 0));
        // Every character is in a line, in order (spaces at breaks aside)
        String t = "un deux trois quatre cinq six sept huit neuf dix";
        assertEquals(t.replace(" ", ""), String.join("", wrap(t, 7)).replace(" ", ""));
    }
}
