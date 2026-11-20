package fr.lordfinn.craftmaster.fx;

import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.show.Actions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenFxTest {

    @TempDir
    Path dir;

    @Test
    void combinationsAreDrawnInTheFixedOrder() {
        assertEquals(List.of("fisheye", "sepia", "masque_rond"), ScreenFx.parse("masque_rond+sepia+fisheye"));
        assertEquals("fisheye+masque_rond", ScreenFx.join(ScreenFx.parse("judas")));
        assertEquals(List.of("fisheye", "sepia", "masque_rond"), ScreenFx.parse("judas + sépia"));
        assertEquals(List.of("flou"), ScreenFx.parse("flou+flou"));
    }

    @Test
    void frenchNamesAndAliasesAreUnderstood() {
        assertEquals(List.of("noir_blanc"), ScreenFx.parse("noir et blanc"));
        assertEquals(List.of("noir_blanc"), ScreenFx.parse("Noir-et-Blanc"));
        assertEquals(List.of("sepia"), ScreenFx.parse("Sépia"));
        assertEquals(List.of("fisheye"), ScreenFx.parse("œil de poisson"));
        assertEquals(List.of("fisheye"), ScreenFx.parse("fish-eye"));
        assertEquals(List.of("reve"), ScreenFx.parse("rêve"));
        assertEquals(List.of("vieux_film"), ScreenFx.parse("le vieux film"));
        assertEquals(List.of("infrarouge"), ScreenFx.parse("thermique"));
        assertEquals(List.of("pixel"), ScreenFx.parse("pixelisé"));
    }

    @Test
    void introEffectsAreUnderstoodAndOrdered() {
        assertEquals(List.of("look_intro"), ScreenFx.parse("look intro"));
        assertEquals(List.of("look_intro"), ScreenFx.parse("Taskmaster"));
        assertEquals(List.of("look_intro"), ScreenFx.parse("vieux reportage"));
        assertEquals(List.of("look_intro"), ScreenFx.parse("le look d'intro"));
        assertEquals(List.of("mise_au_point"), ScreenFx.parse("la bascule de mise au point"));
        assertEquals(List.of("mise_au_point"), ScreenFx.parse("mise au point"));
        assertEquals(List.of("mise_au_point_inverse"), ScreenFx.parse("mise au point inverse"));
        assertEquals(List.of("mise_au_point_inverse"), ScreenFx.parse("bascule inverse"));
        assertEquals(List.of("macro"), ScreenFx.parse("profondeur de champ"));
        assertEquals(List.of("stop_motion"), ScreenFx.parse("stop-motion"));
        assertEquals(List.of("timelapse"), ScreenFx.parse("temps accéléré"));
        // Depth of field first (it reads the depth buffer), then the held picture, then the rest
        assertEquals(List.of("timelapse", "macro", "stop_motion", "fisheye", "look_intro", "vignette"),
                ScreenFx.parse("vignette+look_intro+fisheye+stop_motion+macro+timelapse"));
        assertEquals("bascule de mise au point + look intro", ScreenFx.describe(ScreenFx.parse("look intro+bascule")));
    }

    @Test
    void mixerCountsTheSecondsSinceAnEffectWasSwitchedOn() {
        FxMixer m = new FxMixer();
        m.want(List.of("mise_au_point"), 0.5f);
        m.advance(0.3f);
        m.advance(0.4f);
        assertEquals(0.7f, m.sinceOn("mise_au_point"), 1e-4);
        // Still wanted: the clock goes on; a new shot replays the move
        m.want(List.of("mise_au_point"), 0.5f);
        m.advance(0.1f);
        assertEquals(0.8f, m.sinceOn("mise_au_point"), 1e-4);
        m.restart(List.of("mise_au_point", "sepia"));
        assertEquals(0f, m.sinceOn("mise_au_point"));
        assertEquals(0f, m.sinceOn("sepia"));
        // Switched off: no clock
        m.want(List.of(), 0.5f);
        m.advance(0.1f);
        assertEquals(0f, m.sinceOn("mise_au_point"));
    }

    @Test
    void stopMotionTakesAboutSevenPicturesPerSecond() {
        StopMotionClock clock = new StopMotionClock();
        // The first frame always takes a picture
        assertTrue(clock.advance(1 / 60f, 1f));
        int taken = 0;
        for (int i = 0; i < 600; i++) if (clock.advance(1 / 60f, 1f)) taken++;
        assertEquals(70, taken, 1);
        assertEquals(taken + 1, clock.image());
        // Fading in at half intensity: twice as often
        StopMotionClock half = new StopMotionClock();
        half.advance(0, 0.5f);
        taken = 0;
        for (int i = 0; i < 600; i++) if (half.advance(1 / 60f, 0.5f)) taken++;
        assertEquals(140, taken, 1);
        // Reset: the next frame takes a picture again
        clock.reset();
        assertTrue(clock.advance(0.001f, 1f));
    }

    @Test
    void noneWordsGiveNothing() {
        assertTrue(ScreenFx.parse("aucun").isEmpty());
        assertTrue(ScreenFx.parse(null).isEmpty());
        assertTrue(ScreenFx.parse("  ").isEmpty());
        assertEquals("aucun effet", ScreenFx.describe(List.of()));
    }

    @Test
    void unknownOrTooManyEffectsAreRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ScreenFx.parse("sepia+arc-en-ciel"));
        assertTrue(e.getMessage().contains("arc-en-ciel"));
        assertThrows(IllegalArgumentException.class, () -> ScreenFx.parse("flou+sepia+pixel+vhs+vignette+bd+negatif"));
        // The client never throws: it keeps what it knows
        assertEquals(List.of("sepia"), ScreenFx.parseLenient("sepia+arc-en-ciel"));
    }

    @Test
    void everyEffectHasItsName() {
        assertEquals(22, ScreenFx.ORDER.size());
        assertEquals("fish-eye + masque rond", ScreenFx.describe(ScreenFx.parse("judas")));
        for (String id : ScreenFx.ids()) assertEquals(List.of(id), ScreenFx.parse(id));
        for (String w : FxContent.WEARABLES.values()) assertFalse(ScreenFx.parse(w).isEmpty(), w);
    }

    @Test
    void mixerFadesInAndOut() {
        FxMixer m = new FxMixer();
        m.want(List.of("sepia"), 0.5f);
        assertEquals(0f, m.intensity("sepia"));
        m.advance(0.25f);
        assertEquals(0.5f, m.intensity("sepia"), 1e-4);
        assertEquals(List.of("sepia"), m.visible());
        m.advance(1f);
        assertEquals(1f, m.intensity("sepia"));
        // Switched off: fades out, still drawn until it reaches 0
        m.want(List.of("fisheye"), 1f);
        m.advance(0.5f);
        assertEquals(0.5f, m.intensity("sepia"), 1e-4);
        assertEquals(List.of("fisheye", "sepia"), m.visible());
        m.advance(0.6f);
        assertEquals(List.of("fisheye"), m.visible());
        // No fade: at once
        m.want(List.of(), 0f);
        m.advance(0.01f);
        assertTrue(m.visible().isEmpty());
        assertEquals(0.5f, FxMixer.eased(0.5f), 1e-6);
        assertEquals(1f, FxMixer.eased(2f));
    }

    @Test
    void showActionsCheckTheEffectAndChangeNoData() {
        CraftMasterData data = new CraftMasterData(dir.resolve("config.yml"), LoggerFactory.getLogger("test"));
        Actions.Outcome o = Actions.apply(data, List.of(Map.of("type", "fx.set", "effect", "noir et blanc", "seconds", 30)));
        assertTrue(o.ok());
        assertFalse(o.changed());
        Actions.ScreenEffect e = assertInstanceOf(Actions.ScreenEffect.class, o.effects().getFirst());
        assertEquals("diffusion", e.target());
        assertEquals("noir_blanc", e.effect());
        assertEquals(30, e.seconds());

        Actions.ScreenEffect clear = assertInstanceOf(Actions.ScreenEffect.class,
                Actions.apply(data, List.of(Map.of("type", "fx.clear"))).effects().getFirst());
        assertEquals("tout", clear.target());
        assertEquals("", clear.effect());

        assertFalse(Actions.apply(data, List.of(Map.of("type", "fx.set", "effect", "arc-en-ciel"))).ok());
        assertFalse(Actions.apply(data, List.of(Map.of("type", "fx.set"))).ok());
        assertFalse(Actions.apply(data, List.of(Map.of("type", "fx.set", "target", "tout", "effect", "sepia"))).ok());
        assertFalse(Actions.apply(data, List.of(Map.of("type", "fx.set", "effect", "sepia", "seconds", -3))).ok());
    }
}
