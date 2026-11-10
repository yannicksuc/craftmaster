package fr.lordfinn.craftmaster.camera;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CameraListTest {

    private static CameraSpot cam(int n, String name, String role) {
        return new CameraSpot(n, name, role, "minecraft:overworld", n, 64, 0, 0, 0, 0, null);
    }

    private static CameraList studio() {
        return new CameraList(List.of(cam(1, "Plateau", ""), cam(2, "Plan large", "large"), cam(3, "Trône de LordFinn", "trône"),
                cam(5, "Podium", "podium")));
    }

    @Test
    void resolvesNumbersInEveryForm() {
        CameraList l = studio();
        assertEquals(2, l.resolve("2").number());
        assertEquals(2, l.resolve("caméra 2").number());
        assertEquals(2, l.resolve("Camera deux").number());
        assertEquals(3, l.resolve("cam 3").number());
        assertEquals(1, l.resolve("la caméra un").number());
        assertNull(l.resolve("4"));
    }

    @Test
    void resolvesNamesAndRoles() {
        CameraList l = studio();
        assertEquals(2, l.resolve("plan large").number());
        assertEquals(2, l.resolve("large").number());
        assertEquals(3, l.resolve("trone").number());
        assertEquals(3, l.resolve("plan sur le trône").number());
        assertEquals(5, l.resolve("podium").number());
        assertEquals(1, l.resolve("plateau").number());
        assertNull(l.resolve("cuisine"));
        assertNull(l.resolve(""));
    }

    @Test
    void wideFallsBackOnTheFirstCamera() {
        CameraList l = new CameraList(List.of(cam(4, "A", ""), cam(2, "B", "")));
        assertEquals(2, l.wide().number());
        assertEquals(2, l.resolve("plan large").number());
        assertNull(new CameraList().wide());
    }

    @Test
    void stepsAroundInNumberOrder() {
        CameraList l = studio();
        assertEquals(2, l.step(1, 1).number());
        assertEquals(1, l.step(5, 1).number());
        assertEquals(5, l.step(1, -1).number());
        assertEquals(1, l.step(-1, 1).number());
        assertEquals(5, l.step(-1, -1).number());
    }

    @Test
    void nextFreeFillsTheGaps() {
        assertEquals(4, studio().nextFree());
        assertEquals(1, new CameraList().nextFree());
    }

    @Test
    void spotNormalizesItsFields() {
        CameraSpot s = new CameraSpot(7, " ", "Throne", "w", 0, 0, 0, 10, 120, 300, "pan");
        assertEquals("Caméra 7", s.name());
        assertEquals(CameraSpot.THRONE, s.role());
        assertEquals(CameraSpot.PAN, s.motion());
        assertEquals(CameraSpot.MAX_FOV, s.fov());
        assertEquals(90, s.pitch());
        assertEquals("", CameraSpot.normalizeRole("aucun"));
        assertEquals(CameraSpot.BREATHING, CameraSpot.normalizeMotion(null));
    }
}
