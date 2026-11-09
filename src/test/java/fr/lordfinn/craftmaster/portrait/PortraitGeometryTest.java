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
        // Frame + head only: the baby head (8³ × 0.75³ = 216 px³) pulls the centre toward it in proportion to its volume
        Pose headOnly = only(Pose.NEUTRAL.withOffset(0, 0, 5), Part.HEAD);
        Vector3d cog = PortraitGeometry.centreOfGravity(headOnly);
        Vector3d frame = PortraitGeometry.centreOfGravity(only(Pose.NEUTRAL));
        double frameVolume = PortraitGeometry.FRAME_BOXES.stream().mapToDouble(Box::volume).sum();
        // Feet at the origin: the baby head (pivot at model y 12, cube 6 px) has its centre 15 px above the feet
        Vector3d head = new Vector3d(0, 15, 5);
        Vector3d expected = new Vector3d(frame).mul(frameVolume).add(new Vector3d(head).mul(216)).div(frameVolume + 216);
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
        assertEquals(0.75, PortraitGeometry.partScale(Part.HEAD));
        assertEquals(0.5, PortraitGeometry.partScale(Part.BODY));

        // Standing straight with the feet at (0, 0, 5): an 18 px figure, the head cube spans y 12..18
        Pose standing = Pose.NEUTRAL.withOffset(0, 0, 5);
        Matrix4d head = PortraitGeometry.partMatrix(standing, Part.HEAD);
        assertEquals(18, head.transformPosition(new Vector3d(0, -8, 0)).y, EPS);
        assertEquals(12, head.transformPosition(new Vector3d(0, 0, 0)).y, EPS);
        assertEquals(8, head.transformPosition(new Vector3d(0, -4, -4)).z, EPS, "model -z (the face) points to the viewer");
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
        assertEquals(36, big.transformPosition(new Vector3d(0, -8, 0)).y, EPS);
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
    void presetsKeepTheFigureInFrontOfTheCanvas() {
        for (Map.Entry<String, Pose> e : PoseLibrary.presets().entrySet()) {
            Pose p = e.getValue();
            if (Set.of("porte le cadre").contains(e.getKey()) || PortraitGeometry.behindCanvas(p)) continue;
            // The torso's back must not sink into the canvas
            Matrix4d body = PortraitGeometry.partMatrix(p, Part.BODY);
            Box box = PortraitGeometry.partBox(Part.BODY, false);
            for (Vector3d c : box.corners()) {
                body.transformPosition(c);
                assertTrue(c.z >= PortraitGeometry.CANVAS_FRONT - 1.5, e.getKey() + ": body at z " + c.z);
            }
        }
    }

    @Test
    void figureStaysOnItsSideOfTheCanvas() {
        // A front pose pushed too far back is moved forward: nothing pokes out of the back
        Pose sunk = Pose.NEUTRAL.withOffset(0, -10, -0.4);
        assertTrue(PortraitGeometry.figureShift(sunk, false) > 0);
        assertTrue(minZ(sunk, false) >= PortraitGeometry.CANVAS_BACK - EPS);
        // A pose behind the canvas (coming out of the back) is moved back: nothing pokes through the painting
        Pose back = Pose.NEUTRAL.withOffset(0, -10, -2).withRotation(Part.FIGURE, new Rotation(0, 180, 0));
        assertTrue(PortraitGeometry.behindCanvas(back));
        assertTrue(maxZ(back, false) <= PortraitGeometry.CANVAS_FRONT + EPS);
        assertTrue(minZ(back, false) < PortraitGeometry.CANVAS_BACK - 2, "it does come out of the back");
        // A pose well in front is left alone
        assertEquals(0, PortraitGeometry.figureShift(Pose.NEUTRAL, false));
    }

    @Test
    void windowLookKeepsTheFigureBehindTheOpeningInsideItsBox() {
        for (Map.Entry<String, Pose> e : PoseLibrary.presets().entrySet()) {
            Pose p = e.getValue();
            assertTrue(maxZ(p, true) <= PortraitGeometry.HD + EPS, e.getKey() + " pokes out of the window");
            Box box = PortraitGeometry.windowBox(p);
            Matrix4d toFrame = PortraitGeometry.frameMatrix(p).invert();
            for (Part part : PortraitGeometry.BODY_PARTS) {
                if (p.isHidden(part)) continue;
                Matrix4d m = new Matrix4d(toFrame).mul(PortraitGeometry.partMatrix(p, part, true));
                for (Vector3d c : PortraitGeometry.partBox(part, false).corners()) {
                    m.transformPosition(c);
                    assertTrue(c.x >= box.x0() && c.x <= box.x1() && c.y >= box.y0() && c.y <= box.y1() && c.z >= box.z0(),
                            e.getKey() + ": " + part + " outside the box at " + c);
                }
            }
        }
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

    private static double minZ(Pose p, boolean window) {
        return PortraitGeometry.bounds(only(p, PortraitGeometry.BODY_PARTS.toArray(Part[]::new)), window)[2];
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
