package fr.lordfinn.craftmaster.portrait;

import fr.lordfinn.craftmaster.portrait.Pose.Part;
import fr.lordfinn.craftmaster.portrait.Pose.Rotation;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pose editor logic: undo stack, mirror, reset, drags, and clicking parts on the preview. */
class PoseEditingTest {

    private static final double EPS = 1e-6;

    private static Pose raisedRightArm() {
        return Pose.NEUTRAL.withRotation(Part.RIGHT_ARM, new Rotation(-20, 10, 120))
                .withRotation(Part.HEAD, new Rotation(5, 30, -8))
                .withOffset(3, -10, 2).withFrame(4, 12).withHidden(Part.LEFT_LEG, true);
    }

    // ---------------------------------------------------------------- history

    @Test
    void undoRedoWalkTheSteps() {
        PoseHistory h = new PoseHistory(Pose.NEUTRAL);
        Pose a = Pose.NEUTRAL.withScale(1.5), b = a.withOffset(1, 2, 3);
        assertFalse(h.canUndo());
        assertTrue(h.change(a, null, 0));
        assertTrue(h.change(b, null, 10));
        assertEquals(a, h.undo());
        assertEquals(Pose.NEUTRAL, h.undo());
        assertEquals(Pose.NEUTRAL, h.undo(), "nothing more to undo");
        assertEquals(a, h.redo());
        assertEquals(b, h.redo());
        assertFalse(h.canRedo());
    }

    @Test
    void aNewChangeDropsTheRedos() {
        PoseHistory h = new PoseHistory(Pose.NEUTRAL);
        h.change(Pose.NEUTRAL.withScale(2), null, 0);
        h.undo();
        assertTrue(h.canRedo());
        h.change(Pose.NEUTRAL.withScale(1.2), null, 5);
        assertFalse(h.canRedo());
        assertEquals(Pose.NEUTRAL, h.undo());
    }

    @Test
    void oneGestureIsOneStep() {
        PoseHistory h = new PoseHistory(Pose.NEUTRAL);
        Pose p = Pose.NEUTRAL;
        for (int i = 1; i <= 50; i++) {
            p = PoseEditing.drag(p, Part.RIGHT_ARM, 2, 0, false);
            h.change(p, "drag", i * 16L);
        }
        h.seal();
        assertEquals(p, h.current());
        assertEquals(Pose.NEUTRAL, h.undo(), "a whole drag undoes at once");
        // The same key after a pause, or after seal(), is a new step
        PoseHistory h2 = new PoseHistory(Pose.NEUTRAL);
        Pose x = Pose.NEUTRAL.withScale(1.1), y = x.withScale(1.2), z = y.withScale(1.3);
        h2.change(x, "wheel", 0);
        h2.change(y, "wheel", 100);
        h2.change(z, "wheel", 100 + PoseHistory.MERGE_MS + 1);
        assertEquals(y, h2.undo());
        assertEquals(Pose.NEUTRAL, h2.undo());
    }

    @Test
    void unchangedPoseIsNoStep() {
        PoseHistory h = new PoseHistory(Pose.NEUTRAL);
        assertFalse(h.change(Pose.NEUTRAL.withScale(1), null, 0));
        assertFalse(h.canUndo());
    }

    @Test
    void historyIsBounded() {
        PoseHistory h = new PoseHistory(Pose.NEUTRAL);
        for (int i = 1; i <= PoseHistory.LIMIT + 50; i++) h.change(Pose.NEUTRAL.withOffset(i, 0, 0), null, i * 10_000L);
        int undos = 0;
        while (h.canUndo()) {
            h.undo();
            undos++;
        }
        assertEquals(PoseHistory.LIMIT, undos);
    }

    // ---------------------------------------------------------------- mirror, copy, reset

    @Test
    void mirrorSwapsSidesAndFlipsTurns() {
        Pose p = raisedRightArm();
        Pose m = PoseEditing.mirror(p);
        assertEquals(new Rotation(-20, -10, -120), m.rotation(Part.LEFT_ARM));
        assertEquals(Rotation.ZERO, m.rotation(Part.RIGHT_ARM));
        assertEquals(new Rotation(5, -30, 8), m.rotation(Part.HEAD));
        assertTrue(m.isHidden(Part.RIGHT_LEG));
        assertFalse(m.isHidden(Part.LEFT_LEG));
        assertEquals(-3, m.offsetX(), EPS);
        assertEquals(-12, m.frameRoll(), EPS);
        assertEquals(4, m.framePitch(), EPS);
        assertEquals(p, PoseEditing.mirror(m), "mirroring twice gives the pose back");
    }

    @Test
    void mirroredPoseIsTheReflectionOfTheFigure() {
        // Every corner of every part lands on the reflection (x -> -x) of the matching corner
        Pose p = raisedRightArm().withHidden(Part.LEFT_LEG, false).withFrame(0, 0)
                .withRotation(Part.BODY, new Rotation(10, 20, 5)).withRotation(Part.LEFT_LEG, new Rotation(30, -15, 10));
        Pose m = PoseEditing.mirror(p);
        for (Part part : PortraitGeometry.BODY_PARTS) {
            Part other = PoseEditing.opposite(part);
            var a = PortraitGeometry.partMatrix(p, part);
            var b = PortraitGeometry.partMatrix(m, other);
            PortraitGeometry.Box box = PortraitGeometry.partBox(part, false);
            for (Vector3d c : box.corners()) {
                Vector3d pa = a.transformPosition(new Vector3d(c));
                // The matching corner of the mirrored part: mirrored in the part's own space
                Vector3d pb = b.transformPosition(new Vector3d(-c.x, c.y, c.z));
                assertEquals(-pa.x, pb.x, 1e-6, part + " x");
                assertEquals(pa.y, pb.y, 1e-6, part + " y");
                assertEquals(pa.z, pb.z, 1e-6, part + " z");
            }
        }
    }

    @Test
    void copyToOtherSideMakesASymmetricPose() {
        Pose p = PoseEditing.copyToOtherSide(raisedRightArm(), Part.RIGHT_ARM);
        assertEquals(new Rotation(-20, -10, -120), p.rotation(Part.LEFT_ARM));
        assertEquals(new Rotation(-20, 10, 120), p.rotation(Part.RIGHT_ARM));
        Pose legs = PoseEditing.copyToOtherSide(raisedRightArm(), Part.RIGHT_LEG);
        assertFalse(legs.isHidden(Part.LEFT_LEG), "visibility copied too");
        assertEquals(raisedRightArm(), PoseEditing.copyToOtherSide(raisedRightArm(), Part.HEAD), "the head has no other side");
    }

    @Test
    void resetStraightensOnePart() {
        Pose p = PoseEditing.reset(raisedRightArm(), Part.RIGHT_ARM);
        assertEquals(Rotation.ZERO, p.rotation(Part.RIGHT_ARM));
        assertEquals(new Rotation(5, 30, -8), p.rotation(Part.HEAD), "the others keep theirs");
        Pose f = PoseEditing.reset(raisedRightArm().withScale(1.6).withRotation(Part.FIGURE, new Rotation(0, 45, 0)), Part.FIGURE);
        assertEquals(Rotation.ZERO, f.rotation(Part.FIGURE));
        assertEquals(Pose.NEUTRAL.offsetY(), f.offsetY(), EPS);
        assertEquals(1, f.scale(), EPS);
        assertEquals(0, PoseEditing.resetFrame(raisedRightArm()).frameRoll(), EPS);
    }

    // ---------------------------------------------------------------- drags

    @Test
    void dragMovesWhatIsUnderTheMouseTheSameWay() {
        // Dragging right moves the end of the right arm (and the face) toward the viewer's right (+x)
        for (Part part : new Part[]{Part.RIGHT_ARM, Part.LEFT_ARM, Part.RIGHT_LEG, Part.HEAD}) {
            Vector3d before = tip(Pose.NEUTRAL, part);
            Vector3d after = tip(PoseEditing.drag(Pose.NEUTRAL, part, 20, 0, false), part);
            assertTrue(after.x > before.x + 0.5, part + " follows a drag to the right: " + before.x + " -> " + after.x);
        }
        // Dragging up brings a hanging arm's end up; dragging down lowers the face (nod)
        Vector3d arm = tip(Pose.NEUTRAL, Part.RIGHT_ARM);
        assertTrue(tip(PoseEditing.drag(Pose.NEUTRAL, Part.RIGHT_ARM, 0, -90, false), Part.RIGHT_ARM).y > arm.y + 0.5);
        Vector3d face = tip(Pose.NEUTRAL, Part.HEAD);
        assertTrue(tip(PoseEditing.drag(Pose.NEUTRAL, Part.HEAD, 0, 30, false), Part.HEAD).y < face.y - 0.5);
    }

    /** The far end of a part: the hand / foot of a limb, the nose of the head (portrait space). */
    private static Vector3d tip(Pose pose, Part part) {
        Vector3d local = part == Part.HEAD ? new Vector3d(0, -4, -4) : new Vector3d(0, part.name().endsWith("LEG") ? 12 : 10, 0);
        return PortraitGeometry.partMatrix(pose, part).transformPosition(local);
    }

    @Test
    void anglesWrapAndRound() {
        assertEquals(-170, PoseEditing.wrap(190), EPS);
        assertEquals(180, PoseEditing.wrap(-180), EPS);
        assertEquals(12.3, PoseEditing.wrap(12.34), EPS);
        Pose p = PoseEditing.turn(Pose.NEUTRAL.withRotation(Part.HEAD, new Rotation(0, 175, 0)), Part.HEAD, PoseEditing.Axis.Y, 10);
        assertEquals(-175, p.rotation(Part.HEAD).y(), EPS);
    }

    @Test
    void moveAndFrameDragsStayInRange() {
        Pose p = PoseEditing.move(Pose.NEUTRAL, 1000, -1000);
        assertEquals(24, p.offsetX(), EPS);
        assertEquals(-48, p.offsetY(), EPS);
        Pose f = PoseEditing.dragFrame(Pose.NEUTRAL, -1000, 1000);
        assertEquals(60, f.framePitch(), EPS);
        assertEquals(60, f.frameRoll(), EPS);
    }

    // ---------------------------------------------------------------- clicking the preview

    private static final PoseHitTest.View FRONT = new PoseHitTest.View(0, 0, 4, 200, 120);

    /** Screen point where a part's centre is drawn. */
    private static double[] screenOf(Pose pose, PoseHitTest.View view, Part part) {
        Vector3d c = PortraitGeometry.partMatrix(pose, part).transformPosition(PortraitGeometry.partBox(part, false).center());
        return view.project(c);
    }

    @Test
    void clickingAPartPicksIt() {
        for (PoseHitTest.View view : new PoseHitTest.View[]{FRONT, new PoseHitTest.View(35, 15, 3, 150, 100), new PoseHitTest.View(-60, -20, 5, 0, 0)}) {
            for (Part part : PortraitGeometry.BODY_PARTS) {
                if (part == Part.BODY && view.yaw() != 0) continue;     // seen from the side the arms cover it
                double[] s = screenOf(Pose.NEUTRAL, view, part);
                PoseHitTest.Hit hit = PoseHitTest.pick(Pose.NEUTRAL, view, s[0], s[1], false);
                assertNotNull(hit, part + " at " + s[0] + "," + s[1]);
                if (view == FRONT) assertEquals(part, hit.part(), "front view: " + part);
            }
        }
    }

    @Test
    void clickingWorksInTheEditorsOwnView() {
        // The editor's default view (turned 25°, seen a little from above) on a preset with a raised arm
        Pose salut = PoseLibrary.presets().get("salut");
        PoseHitTest.View view = new PoseHitTest.View(25, 12, 5.3, 332.5, 108);
        for (Part part : new Part[]{Part.HEAD, Part.RIGHT_ARM, Part.LEFT_ARM, Part.RIGHT_LEG}) {
            // The middle of the head; near the hand / foot of a limb (its middle may be behind the head)
            Vector3d local = part == Part.HEAD ? new Vector3d(0, -4, 0) : new Vector3d(0, 8, 0);
            double[] s = view.project(PortraitGeometry.partMatrix(salut, part).transformPosition(local));
            PoseHitTest.Hit hit = PoseHitTest.pick(salut, view, s[0], s[1], false);
            assertNotNull(hit, part + " at " + s[0] + "," + s[1]);
            assertEquals(part, hit.part(), part + " at " + s[0] + "," + s[1]);
        }
    }

    @Test
    void frontViewPutsTheRightArmOnTheViewersLeft() {
        double[] right = screenOf(Pose.NEUTRAL, FRONT, Part.RIGHT_ARM), left = screenOf(Pose.NEUTRAL, FRONT, Part.LEFT_ARM);
        assertTrue(right[0] < left[0]);
        double[] head = screenOf(Pose.NEUTRAL, FRONT, Part.HEAD), leg = screenOf(Pose.NEUTRAL, FRONT, Part.RIGHT_LEG);
        assertTrue(head[1] < leg[1], "head above the legs (screen y down)");
    }

    @Test
    void theNearestPartWins() {
        // An arm swung forward in front of the body: clicking where both are drawn picks the arm
        Pose p = Pose.NEUTRAL.withRotation(Part.RIGHT_ARM, new Rotation(-90, 0, -60));
        double[] arm = screenOf(p, FRONT, Part.RIGHT_ARM);
        PoseHitTest.Hit hit = PoseHitTest.pick(p, FRONT, arm[0], arm[1], false);
        assertEquals(Part.RIGHT_ARM, hit.part());
        // Hidden parts cannot be clicked: the body behind is picked instead (or nothing)
        PoseHitTest.Hit behind = PoseHitTest.pick(p.withHidden(Part.RIGHT_ARM, true), FRONT, arm[0], arm[1], false);
        assertTrue(behind == null || behind.part() != Part.RIGHT_ARM);
    }

    @Test
    void aPartBehindTheCanvasIsNotPickedThroughIt() {
        // Seen from behind, the canvas hides the figure: the frame is what is under the mouse
        PoseHitTest.View back = new PoseHitTest.View(180, 0, 4, 0, 0);
        double[] centre = back.project(new Vector3d(0, -6, 0));
        PoseHitTest.Hit hit = PoseHitTest.pick(Pose.NEUTRAL, back, centre[0], centre[1], false);
        assertNotNull(hit);
        assertTrue(hit.frame());
        // Window look from behind: its back plate hides the figure too
        PoseHitTest.Hit plate = PoseHitTest.pick(Pose.NEUTRAL, back, centre[0], centre[1], false, true);
        assertNotNull(plate);
        assertTrue(plate.frame());
        // Window look from the front: the figure deep in the room is seen through the opening, and picked
        Pose deep = Pose.NEUTRAL.withOffset(0, -10, -8);
        double[] c = FRONT.project(PortraitGeometry.partMatrix(deep, Part.BODY).transformPosition(PortraitGeometry.partBox(Part.BODY, false).center()));
        PoseHitTest.Hit inRoom = PoseHitTest.pick(deep, FRONT, c[0], c[1], false, true);
        assertNotNull(inRoom);
        assertEquals(Part.BODY, inRoom.part());
        // The string cannot be tied to it: it is an illusion behind the canvas plane (nothing, or the frame)
        Pose.Attach knot = PoseHitTest.pickAttach(deep, FRONT, c[0], c[1], false, true);
        assertTrue(knot == null || knot.onFrame());
        // Without the window look the canvas is in front of it
        assertTrue(PoseHitTest.pick(deep, FRONT, c[0], c[1], false, false).frame());
    }

    @Test
    void frameOnlyWhereThereIsNoFigure() {
        // A corner of the frame
        double[] corner = FRONT.project(new Vector3d(-PortraitGeometry.HW + 1, PortraitGeometry.HH - 1, 0));
        PoseHitTest.Hit hit = PoseHitTest.pick(Pose.NEUTRAL, FRONT, corner[0], corner[1], false);
        assertNotNull(hit);
        assertTrue(hit.frame());
        // Far away: nothing
        assertNull(PoseHitTest.pick(Pose.NEUTRAL, FRONT, FRONT.cx() + 500, FRONT.cy(), false));
    }

    @Test
    void viewTurnsTheRightWay() {
        // Turned by a positive yaw, the portrait's right side (+x) comes toward the viewer
        PoseHitTest.View v = new PoseHitTest.View(30, 0, 1, 0, 0);
        assertTrue(v.project(new Vector3d(10, 0, 0))[2] > 0);
        PoseHitTest.View up = new PoseHitTest.View(0, 30, 1, 0, 0);
        assertTrue(up.project(new Vector3d(0, 10, 0))[2] > 0, "seen from above, the top comes nearer");
        assertEquals(80, new PoseHitTest.View(0, 70, 1, 0, 0).turned(0, 50).pitch(), EPS);
    }
}
