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
        double mouldings = 2 * 26 * 2 * 3 + 2 * 2 * 32 * 3;
        double canvas = 22 * 32 * 1;
        double expectedZ = canvas * -1.0 / (mouldings + canvas);
        assertEquals(expectedZ, cog.z, EPS);
        // Hang point: top moulding, straight above
        Vector3d knot = PortraitGeometry.hangPoint(empty);
        assertEquals(0, knot.x, EPS);
        assertEquals(PortraitGeometry.FRAME_H / 2, knot.y, EPS);
    }

    @Test
    void volumesWeighTheAverage() {
        // Frame + head only: the head (512 px³) pulls the centre toward it in proportion to its volume
        Pose headOnly = only(Pose.NEUTRAL.withOffset(0, 0, 0), Part.HEAD);
        Vector3d cog = PortraitGeometry.centreOfGravity(headOnly);
        Vector3d frame = PortraitGeometry.centreOfGravity(only(Pose.NEUTRAL));
        double frameVolume = PortraitGeometry.FRAME_BOXES.stream().mapToDouble(Box::volume).sum();
        // Neutral pose, feet at the origin: the head's centre is 28 px above the feet, centred, at z = 0
        Vector3d head = new Vector3d(0, 28, 0);
        Vector3d expected = new Vector3d(frame).mul(frameVolume).add(new Vector3d(head).mul(512)).div(frameVolume + 512);
        assertEquals(expected.x, cog.x, EPS);
        assertEquals(expected.y, cog.y, EPS);
        assertEquals(expected.z, cog.z, EPS);
    }

    @Test
    void partMatricesMatchThePlayerModel() {
        // Standing straight with the feet at the origin: the head cube spans y 24..32, the face toward the viewer
        Pose standing = Pose.NEUTRAL.withOffset(0, 0, 0);
        Matrix4d head = PortraitGeometry.partMatrix(standing, Part.HEAD);
        Vector3d top = head.transformPosition(new Vector3d(0, -8, 0));
        assertEquals(32, top.y, EPS);
        Vector3d face = head.transformPosition(new Vector3d(0, -4, -4));
        assertEquals(4, face.z, EPS, "model -z (the face) points to the viewer");
        // The right arm is on the viewer's left
        Vector3d rightHand = PortraitGeometry.partMatrix(standing, Part.RIGHT_ARM).transformPosition(new Vector3d(-1, 10, 0));
        assertEquals(-6, rightHand.x, EPS);
        assertEquals(12, rightHand.y, EPS);
        // A negative X rotation brings the arm forward (toward the viewer)
        Pose forward = standing.withRotation(Part.RIGHT_ARM, new Rotation(-90, 0, 0));
        Vector3d hand = PortraitGeometry.partMatrix(forward, Part.RIGHT_ARM).transformPosition(new Vector3d(-1, 10, 0));
        assertEquals(22, hand.y, EPS);
        assertEquals(10, hand.z, EPS);
        // Leaning the upper body forward carries the head with it
        Pose lean = standing.withRotation(Part.BODY, new Rotation(30, 0, 0));
        Vector3d leanHead = PortraitGeometry.partMatrix(lean, Part.HEAD).transformPosition(new Vector3d());
        assertTrue(leanHead.z > 5, "neck moved toward the viewer: " + leanHead.z);
        // Scale grows around the feet
        Matrix4d big = PortraitGeometry.partMatrix(standing.withScale(2), Part.HEAD);
        assertEquals(64, big.transformPosition(new Vector3d(0, -8, 0)).y, EPS);
    }

    @Test
    void raisedArmPullsTheHangPointSideways() {
        Pose wave = Pose.NEUTRAL.withRotation(Part.RIGHT_ARM, new Rotation(0, 0, 90));
        Vector3d cog = PortraitGeometry.centreOfGravity(wave);
        assertTrue(cog.x < -0.1, "right arm on the viewer's left: " + cog.x);
        Vector3d knot = PortraitGeometry.hangPoint(wave);
        assertEquals(cog.x, knot.x, EPS, "the knot is right above the centre of gravity");
    }

    @Test
    void figureUnderTheFrameLowersTheCentre() {
        Pose carry = PoseLibrary.presets().get("porte le cadre");
        Vector3d cog = PortraitGeometry.centreOfGravity(carry);
        assertTrue(cog.y < -10, "the figure below weighs more than the frame: " + cog.y);
        double[] b = PortraitGeometry.bounds(carry, false);
        assertTrue(b[1] <= carry.offsetY() + EPS && b[1] > carry.offsetY() - 1, "feet at the bottom of the bounds: " + b[1]);
        assertEquals(PortraitGeometry.FRAME_H / 2, b[4], 1.5, "the frame (slightly tilted) is the top");
    }

    @Test
    void tiltedFrameKnotStaysOnTheMoulding() {
        Pose rolled = only(Pose.NEUTRAL).withFrame(0, 20);
        Vector3d knot = PortraitGeometry.hangPoint(rolled);
        Matrix4d f = PortraitGeometry.frameMatrix(rolled);
        Vector3d a = f.transformPosition(new Vector3d(-13, 18, 0));
        Vector3d b = f.transformPosition(new Vector3d(13, 18, 0));
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
        assertEquals(13, k.x, EPS);
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
            if (Set.of("porte le cadre").contains(e.getKey())) continue;
            // The torso's back must not sink into the canvas
            Matrix4d body = PortraitGeometry.partMatrix(p, Part.BODY);
            Box box = PortraitGeometry.partBox(Part.BODY, false);
            for (Vector3d c : box.corners()) {
                body.transformPosition(c);
                assertTrue(c.z >= PortraitGeometry.CANVAS_FRONT - 1.5, e.getKey() + ": body at z " + c.z);
            }
        }
    }
}
