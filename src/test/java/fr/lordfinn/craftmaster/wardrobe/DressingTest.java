package fr.lordfinn.craftmaster.wardrobe;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DressingTest {

    /** "" = nothing, "*x" = costume copy of x. */
    private static final Dressing.Kind<String> K = new Dressing.Kind<>() {
        public String none() { return ""; }
        public boolean empty(String s) { return s.isEmpty(); }
        public boolean costume(String s) { return s.startsWith("*"); }
        public String costumeCopy(String s) { return "*" + s; }
    };

    private static final List<String> OWN = List.of("cap", "shirt", "jeans", "");
    private static final List<String> DOCTOR = List.of("mask", "robe", "trousers", "boots");
    private static final List<String> POLICE = List.of("", "uniform", "trousers", "shoes");

    @Test
    void putOnKeepsOwnClothesAsideAndWearsCopies() {
        var r = Dressing.putOnCopy(K, OWN, null, DOCTOR);
        assertEquals(List.of("*mask", "*robe", "*trousers", "*boots"), r.worn());
        assertEquals(OWN, r.saved());
        assertTrue(r.toInventory().isEmpty());
    }

    @Test
    void secondOutfitReplacesCopiesAndKeepsTheSameSavedClothes() {
        var first = Dressing.putOnCopy(K, OWN, null, DOCTOR);
        var second = Dressing.putOnCopy(K, first.worn(), first.saved(), POLICE);
        assertEquals(List.of("", "*uniform", "*trousers", "*shoes"), second.worn());
        assertEquals(OWN, second.saved());
        assertTrue(second.toInventory().isEmpty());
    }

    @Test
    void ownPiecePutOnDuringACostumeGoesToTheInventory() {
        var first = Dressing.putOnCopy(K, OWN, null, POLICE);
        List<String> worn = List.of("helmet", "*uniform", "*trousers", "*shoes");
        var second = Dressing.putOnCopy(K, worn, first.saved(), DOCTOR);
        assertEquals(List.of("helmet"), second.toInventory());
        var back = Dressing.takeBack(K, List.of("hat", "*robe", "", "*boots"), second.saved());
        assertEquals(OWN, back.worn());
        assertNull(back.saved());
        assertEquals(List.of("hat"), back.toInventory());
    }

    @Test
    void takeBackGivesOwnClothesBack() {
        var on = Dressing.putOnCopy(K, OWN, null, DOCTOR);
        var back = Dressing.takeBack(K, on.worn(), on.saved());
        assertEquals(OWN, back.worn());
        assertNull(back.saved());
        assertTrue(back.toInventory().isEmpty());
        assertNull(Dressing.takeBack(K, OWN, null), "nothing to take back without a costume");
    }

    @Test
    void costumeCopiesNeverGetSavedAsOwnClothes() {
        var r = Dressing.putOnCopy(K, List.of("*mask", "shirt", "", ""), null, POLICE);
        assertEquals(List.of("", "shirt", "", ""), r.saved());
    }

    @Test
    void storageSwapsAndSwapsBack() {
        var r = Dressing.swap(K, OWN, DOCTOR);
        assertEquals(DOCTOR, r.worn());
        assertEquals(OWN, r.outfit());
        var back = Dressing.swap(K, r.worn(), r.outfit());
        assertEquals(OWN, back.worn());
        assertEquals(DOCTOR, back.outfit());
    }

    @Test
    void storageDoesNotStoreShowCopies() {
        var r = Dressing.swap(K, List.of("*mask", "shirt", "", ""), DOCTOR);
        assertEquals(List.of("", "shirt", "", ""), r.outfit());
    }

    @Test
    void storeLeavesThePlayerWithoutThosePieces() {
        var r = Dressing.store(K, OWN);
        assertTrue(Dressing.allEmpty(K, r.worn()));
        assertEquals(OWN, r.outfit());
    }

    @Test
    void templateUnmarksCopies() {
        var t = Dressing.template(K, List.of("*mask", "robe", "", "*boots"), s -> s.replace("*", ""));
        assertEquals(List.of("mask", "robe", "", "boots"), t);
    }

    @Test
    void mixingPiecesOfTwoOutfits() {
        // The doctor's robe over your own cap and jeans, then the mafia hat
        var robe = Dressing.putOnCopy(K, OWN, null, DOCTOR, Dressing.only(1));
        assertEquals(List.of("cap", "*robe", "jeans", ""), robe.worn());
        assertEquals(List.of("", "shirt", "", ""), robe.saved());
        var hat = Dressing.putOnCopy(K, robe.worn(), robe.saved(), List.of("hat", "jacket", "", ""), Dressing.only(0));
        assertEquals(List.of("*hat", "*robe", "jeans", ""), hat.worn());
        assertEquals(List.of("cap", "shirt", "", ""), hat.saved());
        var back = Dressing.takeBack(K, hat.worn(), hat.saved());
        assertEquals(OWN, back.worn());
        assertTrue(back.toInventory().isEmpty());
    }

    @Test
    void wholeOutfitAfterAPieceKeepsWhatWasSaved() {
        var robe = Dressing.putOnCopy(K, OWN, null, DOCTOR, Dressing.only(1));
        var police = Dressing.putOnCopy(K, robe.worn(), robe.saved(), POLICE);
        assertEquals(List.of("", "*uniform", "*trousers", "*shoes"), police.worn());
        assertEquals(OWN, police.saved());
        assertEquals(OWN, Dressing.takeBack(K, police.worn(), police.saved()).worn());
    }

    @Test
    void storageSwapsOnePiece() {
        var r = Dressing.swap(K, OWN, DOCTOR, Dressing.only(0));
        assertEquals(List.of("mask", "shirt", "jeans", ""), r.worn());
        assertEquals(List.of("cap", "robe", "trousers", "boots"), r.outfit());
    }

    @Test
    void takingOffACopyGivesBackWhatWasKeptAside() {
        var on = Dressing.putOnCopy(K, OWN, null, DOCTOR);
        var hat = Dressing.takeOff(K, on.worn(), on.saved(), 0);
        assertEquals(List.of("cap", "*robe", "*trousers", "*boots"), hat.worn());
        assertEquals(List.of("", "shirt", "jeans", ""), hat.saved());
        assertTrue(hat.toInventory().isEmpty());
        // Feet: nothing was kept there, nothing comes back
        var feet = Dressing.takeOff(K, hat.worn(), hat.saved(), 3);
        assertEquals(List.of("cap", "*robe", "*trousers", ""), feet.worn());
    }

    @Test
    void takingOffTheLastCopyEndsTheCostume() {
        var robe = Dressing.putOnCopy(K, OWN, null, DOCTOR, Dressing.only(1));
        var off = Dressing.takeOff(K, robe.worn(), robe.saved(), 1);
        assertEquals(OWN, off.worn());
        assertNull(off.saved());
    }

    @Test
    void takingOffOwnPieceGoesToTheInventory() {
        var off = Dressing.takeOff(K, OWN, null, 1);
        assertEquals(List.of("cap", "", "jeans", ""), off.worn());
        assertEquals(List.of("shirt"), off.toInventory());
        assertNull(off.saved());
    }

    @Test
    void strippingKeepsEverythingAside() {
        var mixed = Dressing.putOnCopy(K, OWN, null, DOCTOR, Dressing.only(1));   // cap, *robe, jeans, ""
        var bare = Dressing.stripAll(K, mixed.worn(), mixed.saved());
        assertTrue(Dressing.allEmpty(K, bare.worn()));
        assertEquals(OWN, bare.saved());
        assertTrue(bare.toInventory().isEmpty());
        assertEquals(OWN, Dressing.takeBack(K, bare.worn(), bare.saved()).worn());
        assertNull(Dressing.stripAll(K, Dressing.nothing(K), null).saved());
    }

    @Test
    void fourPiecesRequired() {
        assertThrows(IllegalArgumentException.class, () -> Dressing.swap(K, List.of("a"), DOCTOR));
    }
}
