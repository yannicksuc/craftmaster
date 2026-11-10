package fr.lordfinn.craftmaster.camera;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Cameras of several lieux and zones: groups, numbering per group, references relative to the focus. */
class CameraLieuxTest {

    private static CameraSpot cam(int n, String name, String role, String lieu, String zone) {
        return new CameraSpot(n, name, role, "minecraft:overworld", n, 64, 0, 0, 0, 0, null, lieu, zone);
    }

    /** Studio 1–3 (throne, podium), round "pont": its own 1 (wide), Ensuri's room 1–2, Cyllu's room 1. */
    private static CameraList list() {
        return new CameraList(List.of(cam(1, "Plan large", "large", "", ""), cam(2, "Trône", "trone", "studio", ""),
                cam(3, "Podium", "podium", "plateau", ""),
                cam(1, "Pont large", "large", "pont", ""),
                cam(1, null, "", "pont", "ensuri"), cam(2, "Établi d'Ensuri", "", "pont", "Ensuri"),
                cam(1, null, "", "pont", "equipe-bleue")));
    }

    private static final Map<String, String> PLACES = Map.of("le pont", "pont", "ensuri", "pont:ensuri", "equipe bleue", "pont:equipe-bleue");

    @Test
    void groupsAndKeys() {
        CameraSpot s = cam(2, null, "", "Pont", "Équipe Bleue");
        assertEquals("pont", s.lieu());
        assertEquals("equipe-bleue", s.zone());
        assertEquals("pont:equipe-bleue", s.group());
        assertEquals("pont:equipe-bleue:2", s.key());
        assertEquals("studio", cam(1, null, "", null, "ignored").group());
        assertEquals("", cam(1, null, "", "studio", "x").zone());
        assertEquals(List.of("studio", "pont", "pont:ensuri", "pont:equipe-bleue"), list().groups());
        assertEquals(List.of("", "ensuri", "equipe-bleue"), list().zones("pont"));
        assertEquals(2, list().group("pont:ensuri").size());
        assertEquals(4, list().lieu("pont").size());
        assertEquals("manche pont, zone ensuri", CameraSpot.placeName("pont:ensuri"));
        assertEquals("pont:ensuri 1 · Caméra 1", list().get("pont:ensuri:1").label());
    }

    @Test
    void numbersArePerGroup() {
        CameraList l = list();
        assertEquals(3, l.nextFree("pont:ensuri"));
        assertEquals(2, l.nextFree("pont"));
        assertEquals(4, l.nextFree("studio"));
        assertEquals(1, l.nextFree("tour"));
    }

    @Test
    void plainReferencesStayInTheFocus() {
        CameraList l = list();
        assertEquals("studio:2", l.resolve("2", "studio", PLACES).key());
        assertEquals("pont:ensuri:2", l.resolve("caméra 2", "pont:ensuri", PLACES).key());
        // No camera 3 in Ensuri's room: never another lieu's camera 3
        assertNull(l.resolve("3", "pont:ensuri", PLACES));
        assertEquals("pont:1", l.resolve("plan large", "pont", PLACES).key());
        // A role the zone has not: the round's, then the studio's (throne reaction during a round)
        assertEquals("pont:1", l.resolve("large", "pont:ensuri", PLACES).key());
        assertEquals("studio:2", l.resolve("plan sur le trône", "pont:ensuri", PLACES).key());
        assertEquals("studio:3", l.resolve("podium", "pont", PLACES).key());
        // A unique name anywhere
        assertEquals("pont:ensuri:2", l.resolve("Établi d'Ensuri", "studio", PLACES).key());
    }

    @Test
    void explicitReferencesReachAnyGroup() {
        CameraList l = list();
        assertEquals("studio:2", l.resolve("studio:2", "pont:ensuri", PLACES).key());
        assertEquals("pont:1", l.resolve("pont:1", "studio", PLACES).key());
        assertEquals("pont:ensuri:1", l.resolve("pont:ensuri:1", "studio", PLACES).key());
        // A zone of the focused round
        assertEquals("pont:equipe-bleue:1", l.resolve("equipe-bleue:1", "pont", PLACES).key());
        assertEquals("pont:ensuri:2", l.resolve("ensuri:2", "pont:equipe-bleue", PLACES).key());
        assertNull(l.resolve("pont:9", "studio", PLACES));
    }

    @Test
    void placesInWords() {
        CameraList l = list();
        assertEquals("studio:2", l.resolve("caméra 2 du studio", "pont:ensuri", PLACES).key());
        assertEquals("studio:1", l.resolve("caméra studio 1", "pont", PLACES).key());
        assertEquals("studio:3", l.resolve("studio 3", "pont", PLACES).key());
        assertEquals("pont:ensuri:2", l.resolve("caméra 2 d'Ensuri", "studio", PLACES).key());
        assertEquals("pont:equipe-bleue:1", l.resolve("la caméra 1 de l'équipe bleue", "studio", PLACES).key());
        assertEquals("pont:1", l.resolve("caméra 1 du pont", "studio", PLACES).key());
        assertEquals("pont:1", l.resolve("plan large du pont", "studio", PLACES).key());
    }

    @Test
    void placeFindsGroups() {
        CameraList l = list();
        assertEquals("studio", l.place("studio", "pont", Map.of(), true));
        assertEquals("pont", l.place("pont", "studio", Map.of(), false));
        assertEquals("pont:ensuri", l.place("ensuri", "pont", Map.of(), false));
        // A zone found in only one lieu
        assertEquals("pont:ensuri", l.place("ensuri", "studio", Map.of(), false));
        assertNull(l.place("cuisine", "studio", Map.of(), false));
        // Before a colon, an unknown name is a (still empty) zone of the focused round
        assertEquals("pont:cuisine", l.place("cuisine", "pont", Map.of(), true));
    }

    @Test
    void stepStaysInTheGroupView() {
        CameraList ensuri = list().group("pont:ensuri");
        assertEquals("pont:ensuri:2", ensuri.step("pont:ensuri:1", 1).key());
        assertEquals("pont:ensuri:1", ensuri.step("pont:ensuri:2", 1).key());
        assertEquals("pont:ensuri:1", ensuri.step("studio:1", 1).key());
    }

    @Test
    void movingKeepsAGivenNameAndRenumbersADefaultOne() {
        CameraSpot a = cam(3, null, "", "studio", "");
        CameraSpot moved = a.withLieu("pont", "ensuri", 4);
        assertEquals("Caméra 4", moved.name());
        assertEquals("pont:ensuri:4", moved.key());
        assertEquals("Grue", cam(3, "Grue", "", "studio", "").withLieu("pont", "", 1).name());
    }
}
