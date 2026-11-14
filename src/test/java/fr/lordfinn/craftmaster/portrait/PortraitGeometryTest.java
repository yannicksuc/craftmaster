package fr.lordfinn.craftmaster.portrait;

import fr.lordfinn.craftmaster.portrait.Pose.Part;
import fr.lordfinn.craftmaster.portrait.Pose.Rotation;
import fr.lordfinn.craftmaster.portrait.PortraitGeometry.Box;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PortraitGeometryTest {

    private static final double EPS = 1e-6;

    /** A pose with every figure part hidden but the given ones. */
    private static Pose only(Pose base, Part... visible) {
        Pose p = base;
        for (Part part : PortraitGeometry.BODY_PARTS) p = p.withHidden(part, true);
        for (Part part : visible) p = p.withHidden(part, false);
        return p;
    }

    @Test
    void frameAloneBalancesOnItsCentreLine() {
        Pose empty = only(Pose.NEUTRAL);
        Vector3d cog = PortraitGeometry.centreOfGravity(empty);
        assertEquals(0, cog.x, EPS);
        assertEquals(0, cog.y, EPS, "symmetric top/bottom mouldings");
        // The canvas sits at the back: the centre is a bit behind the frame's middle plane
        double W = PortraitGeometry.FRAME_W, H = PortraitGeometry.FRAME_H, B = PortraitGeometry.BORDER, D = PortraitGeometry.DEPTH;
        double mouldings = 2 * W * B * D + 2 * B * (H - 2 * B) * D;
        double canvas = (W - 2 * B) * (H - 2 * B) * PortraitGeometry.CANVAS;
        double expectedZ = canvas * -1.0 / (mouldings + canvas);
        assertEquals(expectedZ, cog.z, EPS);
        // Hang point: top moulding, straight above
        Vector3d knot = PortraitGeometry.hangPoint(empty);
        assertEquals(0, knot.x, EPS);
        assertEquals(PortraitGeometry.FRAME_H / 2, knot.y, EPS);
    }

    @Test
    void volumesWeighTheAverage() {
        // Frame + head only: the head (8³ × scale³ px³) pulls the centre toward it in proportion to its volume
        Pose headOnly = only(Pose.NEUTRAL.withOffset(0, 0, 5), Part.HEAD);
        Vector3d cog = PortraitGeometry.centreOfGravity(headOnly);
        Vector3d frame = PortraitGeometry.centreOfGravity(only(Pose.NEUTRAL));
        double frameVolume = PortraitGeometry.FRAME_BOXES.stream().mapToDouble(Box::volume).sum();
        // Feet at the origin: the head (pivot at model y 12, cube 8 × scale px) has its centre 12 + 4 × scale above the feet
        double sc = PortraitGeometry.BABY_HEAD_SCALE, vol = Math.pow(8 * sc, 3);
        Vector3d head = new Vector3d(0, 12 + 4 * sc, 5);
        Vector3d expected = new Vector3d(frame).mul(frameVolume).add(new Vector3d(head).mul(vol)).div(frameVolume + vol);
        assertEquals(expected.x, cog.x, EPS);
        assertEquals(expected.y, cog.y, EPS);
        assertEquals(expected.z, cog.z, EPS);
    }

    @Test
    void partMatricesMatchTheBabyPlayerModel() {
        // Vanilla baby transform: head pivot (0, 12), arms (±2.5, 13), legs (±0.95, 18), body/limbs halved
        assertEquals(new Vector3d(0, 12, 0), PortraitGeometry.pivot(Part.HEAD));
        assertEquals(new Vector3d(-2.5, 13, 0), PortraitGeometry.pivot(Part.RIGHT_ARM));
        assertEquals(new Vector3d(0.95, 18, 0), PortraitGeometry.pivot(Part.LEFT_LEG));
        assertEquals(PortraitGeometry.BABY_HEAD_SCALE, PortraitGeometry.partScale(Part.HEAD));
        assertEquals(0.5, PortraitGeometry.partScale(Part.BODY));

        // Standing straight with the feet at (0, 0, 5): an 18 px figure, the head cube spans y 12..18
        Pose standing = Pose.NEUTRAL.withOffset(0, 0, 5);
        Matrix4d head = PortraitGeometry.partMatrix(standing, Part.HEAD);
        assertEquals(12 + 8 * PortraitGeometry.BABY_HEAD_SCALE, head.transformPosition(new Vector3d(0, -8, 0)).y, EPS);
        assertEquals(12, head.transformPosition(new Vector3d(0, 0, 0)).y, EPS);
        assertEquals(5 + 4 * PortraitGeometry.BABY_HEAD_SCALE, head.transformPosition(new Vector3d(0, -4, -4)).z, EPS, "model -z (the face) points to the viewer");
        // Body from the hips (6 px above the feet) to the neck (12 px); feet on the ground
        Matrix4d body = PortraitGeometry.partMatrix(standing, Part.BODY);
        assertEquals(12, body.transformPosition(new Vector3d(0, 0, 0)).y, EPS);
        assertEquals(6, body.transformPosition(new Vector3d(0, 12, 0)).y, EPS);
        assertEquals(0, PortraitGeometry.partMatrix(standing, Part.RIGHT_LEG).transformPosition(new Vector3d(0, 12, 0)).y, EPS);
        // The right arm is on the viewer's left
        Vector3d rightHand = PortraitGeometry.partMatrix(standing, Part.RIGHT_ARM).transformPosition(new Vector3d(-1, 10, 0));
        assertEquals(-3, rightHand.x, EPS);
        assertEquals(6, rightHand.y, EPS);
        // A negative X rotation brings the arm forward (toward the viewer)
        Pose forward = standing.withRotation(Part.RIGHT_ARM, new Rotation(-90, 0, 0));
        Vector3d hand = PortraitGeometry.partMatrix(forward, Part.RIGHT_ARM).transformPosition(new Vector3d(-1, 10, 0));
        assertEquals(11, hand.y, EPS);
        assertEquals(10, hand.z, EPS);
        // Leaning the upper body forward carries the head with it
        Pose lean = standing.withRotation(Part.BODY, new Rotation(30, 0, 0));
        Vector3d leanHead = PortraitGeometry.partMatrix(lean, Part.HEAD).transformPosition(new Vector3d());
        assertTrue(leanHead.z > 7.5, "neck moved toward the viewer: " + leanHead.z);
        // Scale grows around the feet
        Matrix4d big = PortraitGeometry.partMatrix(standing.withScale(2), Part.HEAD);
        assertEquals(2 * (12 + 8 * PortraitGeometry.BABY_HEAD_SCALE), big.transformPosition(new Vector3d(0, -8, 0)).y, EPS);
    }

    @Test
    void raisedArmPullsTheHangPointSideways() {
        Pose wave = Pose.NEUTRAL.withRotation(Part.RIGHT_ARM, new Rotation(0, 0, 90));
        Vector3d cog = PortraitGeometry.centreOfGravity(wave);
        assertTrue(cog.x < -0.01, "right arm on the viewer's left (a baby arm is light): " + cog.x);
        Vector3d knot = PortraitGeometry.hangPoint(wave);
        assertEquals(cog.x, knot.x, EPS, "the knot is right above the centre of gravity");
    }

    @Test
    void figureUnderTheFrameLowersTheCentre() {
        Pose carry = PoseLibrary.presets().get("porte le cadre");
        Vector3d cog = PortraitGeometry.centreOfGravity(carry);
        assertTrue(cog.y < -3, "the figure below pulls the centre down: " + cog.y);
        double[] b = PortraitGeometry.bounds(carry, false);
        assertTrue(b[1] <= carry.offsetY() + EPS && b[1] > carry.offsetY() - 1, "feet at the bottom of the bounds: " + b[1]);
        assertEquals(PortraitGeometry.FRAME_H / 2, b[4], 1.5, "the frame (slightly tilted) is the top");
    }

    @Test
    void tiltedFrameKnotStaysOnTheMoulding() {
        Pose rolled = only(Pose.NEUTRAL).withFrame(0, 20);
        Vector3d knot = PortraitGeometry.hangPoint(rolled);
        Matrix4d f = PortraitGeometry.frameMatrix(rolled);
        double hw = PortraitGeometry.FRAME_W / 2, hh = PortraitGeometry.FRAME_H / 2;
        Vector3d a = f.transformPosition(new Vector3d(-hw, hh, 0));
        Vector3d b = f.transformPosition(new Vector3d(hw, hh, 0));
        // On segment a-b
        double t = (knot.x - a.x) / (b.x - a.x);
        assertTrue(t >= 0 && t <= 1);
        assertEquals(a.y + (b.y - a.y) * t, knot.y, EPS);
        // A frame alone is symmetric around its centre, whatever its roll: knot at x = 0
        assertEquals(0, knot.x, EPS);

        // Far out centre of gravity: the knot is clamped to the end of the moulding
        Pose heavy = new Pose(Map.of(), EnumSet.of(Part.RIGHT_ARM, Part.LEFT_ARM, Part.RIGHT_LEG, Part.LEFT_LEG, Part.BODY),
                60, 0, 0, 3, 0, 0);
        Vector3d k = PortraitGeometry.hangPoint(heavy);
        assertEquals(PortraitGeometry.FRAME_W / 2, k.x, EPS);
    }

    @Test
    void portraitOriginPutsTheCentreUnderTheHook() {
        Pose pose = PoseLibrary.presets().get("salut");
        Vector3d origin = PortraitGeometry.portraitOrigin(pose, false, 1.25);
        Vector3d cog = PortraitGeometry.centreOfGravity(pose);
        Vector3d knot = PortraitGeometry.hangPoint(pose);
        // Portrait point p is drawn at origin + p/16 (blocks, relative to the hook)
        assertEquals(0, origin.x + cog.x / 16, EPS);
        assertEquals(0, origin.z + cog.z / 16, EPS);
        assertEquals(-1.25, origin.y + knot.y / 16, EPS);
    }

    @Test
    void canvasCutsOnTheRightSide() {
        // A front pose keeps what is in front of the painted face; a pose behind the canvas, what is behind its back
        assertEquals(PortraitGeometry.CANVAS_FRONT, PortraitGeometry.cutPlane(Pose.NEUTRAL));
        Pose back = PoseLibrary.presets().get("sort par derrière");
        assertTrue(PortraitGeometry.behindCanvas(back));
        assertEquals(PortraitGeometry.CANVAS_BACK, PortraitGeometry.cutPlane(back));
        // Normally the figure is not moved: the cut does the work (legs stay "in the painting")
        for (Pose p : PoseLibrary.presets().values()) assertEquals(0, PortraitGeometry.figureShift(p, false));
        // Leaning out: the head is out of the painting, the feet still inside it
        Pose lean = PoseLibrary.presets().get("se penche dehors");
        assertTrue(PortraitGeometry.partMatrix(lean, Part.HEAD).transformPosition(new Vector3d(0, -4, 0)).z > PortraitGeometry.CANVAS_FRONT);
        assertTrue(PortraitGeometry.partMatrix(lean, Part.RIGHT_LEG).transformPosition(new Vector3d(0, 12, 0)).z < PortraitGeometry.CANVAS_FRONT);
    }

    @Test
    void windowLookKeepsTheFigureAndThePortraitInPlace() {
        for (Map.Entry<String, Pose> e : PoseLibrary.presets().entrySet()) {
            Pose p = e.getValue();
            assertEquals(maxZ(p, false), maxZ(p, true), EPS, e.getKey());
            Vector3d a = PortraitGeometry.portraitOrigin(p, false, 3), b = PortraitGeometry.portraitOrigin(p, true, 3);
            assertEquals(0, a.distance(b), EPS, e.getKey() + ": the portrait does not move when the look changes");
        }
        // Nothing of the window look exists outside the frame: same bounds as the frame + figure
        double[] b = PortraitGeometry.bounds(Pose.NEUTRAL, true);
        assertTrue(b[2] > -11, "no deep box behind the frame: " + b[2]);
    }

    @Test
    void chosenKnotHangsThePortraitFromIt() {
        Pose p = PoseLibrary.presets().get("salut").withAttach(new Pose.Attach(Part.RIGHT_ARM, -1, 10, 0));
        // Saved and read back
        assertEquals(p, Pose.fromJson(p.toJson()));
        // The knot is that point of the arm, as posed, and it is right under the hook
        Vector3d knot = PortraitGeometry.hangPoint(p, false);
        Vector3d expected = PortraitGeometry.partMatrix(p, Part.RIGHT_ARM).transformPosition(new Vector3d(-1, 10, 0));
        assertEquals(0, knot.distance(expected), EPS);
        Vector3d origin = PortraitGeometry.portraitOrigin(p, false, 3);
        assertEquals(0, origin.x + knot.x / 16, EPS);
        assertEquals(-3, origin.y + knot.y / 16, EPS);
        // On the frame, and mirrored onto the other arm
        Pose f = Pose.NEUTRAL.withAttach(new Pose.Attach(null, 4, 12, 0));
        assertEquals(4, PortraitGeometry.hangPoint(f, false).x, EPS);
        assertEquals(Part.LEFT_ARM, PoseEditing.mirror(p).attach().part());
        // No knot chosen: computed as before
        assertNull(Pose.fromJson(Pose.NEUTRAL.toJson()).attach());
    }

    @Test
    void stringIsVerticalAboveTheCentreOfGravity() {
        for (Pose p : PoseLibrary.presets().values()) {
            for (boolean window : new boolean[]{false, true}) {
                Vector3d cog = PortraitGeometry.centreOfGravity(p, window);
                Vector3d knot = PortraitGeometry.hangPoint(p, window);
                if (Math.abs(cog.x) < PortraitGeometry.HW && Math.abs(cog.z) < PortraitGeometry.HD) {
                    assertEquals(cog.x, knot.x, EPS);
                    assertEquals(cog.z, knot.z, EPS);
                }
                Vector3d origin = PortraitGeometry.portraitOrigin(p, window, 3);
                assertEquals(0, origin.x + knot.x / 16, EPS, "knot straight under the hook");
                assertEquals(0, origin.z + knot.z / 16, EPS);
            }
        }
    }

    private static double maxZ(Pose p, boolean window) {
        double max = -Double.MAX_VALUE;
        for (Part part : PortraitGeometry.BODY_PARTS) {
            if (p.isHidden(part)) continue;
            Matrix4d m = PortraitGeometry.partMatrix(p, part, window);
            for (Vector3d c : PortraitGeometry.partBox(part, false).corners()) max = Math.max(max, m.transformPosition(c).z);
        }
        return max;
    }
}
