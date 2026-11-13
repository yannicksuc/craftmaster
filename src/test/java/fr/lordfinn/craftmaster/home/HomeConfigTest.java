package fr.lordfinn.craftmaster.home;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HomeConfigTest {

    @Test
    void defaultsSurviveARoundTrip() {
        HomeConfig d = HomeConfig.defaults();
        HomeConfig c = HomeConfig.parse(d.toJson());
        assertTrue(c.enabled);
        assertEquals("109.239.154.4:25600", c.server);
        assertEquals("LordFinn", c.character.pseudo());
        assertTrue(c.character.seated());
        assertFalse(HomeConfig.parse("{\"personnage\": {\"assis\": false}}").character.seated());
        assertEquals(5, c.players.size());
        assertEquals("Ensuri", c.players.getFirst().pseudo());
        assertEquals(35, c.row.angle());
        assertEquals(6, c.row.sway());
        assertEquals(d.row.spacing(), c.row.spacing());
        assertArrayEquals(d.row.first(), c.row.first());
        assertArrayEquals(d.row.vanish(), c.row.vanish());
        assertEquals(d.parallax, c.parallax);
        assertEquals(d.zoom, c.zoom);
    }

    @Test
    void emptyObjectGivesTheDefaults() {
        HomeConfig c = HomeConfig.parse("{}");
        assertEquals(HomeConfig.defaults().toJson(), c.toJson());
    }

    @Test
    void playersAsNamesOrObjects() {
        HomeConfig c = HomeConfig.parse("""
                {"joueurs": ["Ensuri", "  ", {"pseudo": "Cyllu", "skin": "https://example.org/s.png", "pose": "salut"},
                             {"skin": "skins/juste.png"}, {"pseudo": ""}, 12]}""");
        assertEquals(4, c.players.size());
        assertEquals(new HomeConfig.Player("Ensuri", "", ""), c.players.get(0));
        assertEquals(new HomeConfig.Player("Cyllu", "https://example.org/s.png", "salut"), c.players.get(1));
        assertEquals(new HomeConfig.Player("", "skins/juste.png", ""), c.players.get(2));
        assertEquals("12", c.players.get(3).pseudo());
    }

    @Test
    void valuesAreClampedAndWrongTypesIgnored() {
        HomeConfig c = HomeConfig.parse("""
                {"actif": "oui?", "zoomFond": 40, "serveur": "  ",
                 "cadres": {"max": 99, "angle": "trente", "balancement": -3, "premier": [2, -1], "fuite": [0.5], "plusLoin": 0},
                 "parallaxe": {"fond": 1e9, "douceur": 0}}""");
        assertTrue(c.enabled, "not a boolean: default");
        assertEquals(2, c.zoom);
        assertEquals("109.239.154.4:25600", c.server, "blank address: default");
        assertEquals(12, c.row.max());
        assertEquals(35, c.row.angle());
        assertEquals(0, c.row.sway());
        assertArrayEquals(new double[]{1, 0}, c.row.first());
        assertArrayEquals(HomeConfig.defaults().row.vanish(), c.row.vanish());
        assertEquals(0.2, c.row.farthest());
        assertEquals(100, c.parallax.background());
        assertEquals(0.2, c.parallax.smoothing());
    }

    @Test
    void shownPlayersFollowTheMaximum() {
        HomeConfig c = HomeConfig.parse("{\"cadres\": {\"max\": 2}}");
        assertEquals(2, c.shownPlayers().size());
        assertEquals("Ensuri", c.shownPlayers().getFirst().pseudo());
        assertEquals(0, HomeConfig.parse("{\"joueurs\": []}").shownPlayers().size());
    }

    @Test
    void disabledAndNotJson() {
        assertFalse(HomeConfig.parse("{\"actif\": false}").enabled);
        assertThrows(RuntimeException.class, () -> HomeConfig.parse("pas du json"));
        assertThrows(RuntimeException.class, () -> HomeConfig.parse("[1, 2]"));
    }
}
