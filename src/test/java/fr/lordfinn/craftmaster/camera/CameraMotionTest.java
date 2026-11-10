package fr.lordfinn.craftmaster.camera;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraMotionTest {

    private static CameraSpot cam(String motion, float yaw, float pitch) {
        return new CameraSpot(1, "A", "", "w", 10, 70, -5, yaw, pitch, 0, motion);
    }

    @Test
    void fixedCameraNeverMoves() {
        for (double t : new double[]{0, 1.3, 17, 600}) {
            CameraMotion.View v = CameraMotion.view(cam(CameraSpot.FIXED, 30, 10), t);
            assertEquals(10, v.x(), 1e-9);
            assertEquals(70, v.y(), 1e-9);
            assertEquals(30, v.yaw(), 1e-6);
            assertEquals(10, v.pitch(), 1e-6);
        }
    }

    @Test
    void breathingStaysSubtle() {
        double maxPos = 0, maxAngle = 0;
        for (double t = 0; t < 120; t += 0.05) {
            CameraMotion.View v = CameraMotion.view(cam(CameraSpot.BREATHING, 0, 0), t);
            maxPos = Math.max(maxPos, Math.max(Math.abs(v.x() - 10), Math.max(Math.abs(v.y() - 70), Math.abs(v.z() + 5))));
            maxAngle = Math.max(maxAngle, Math.max(Math.abs(v.yaw()), Math.abs(v.pitch())));
        }
        assertTrue(maxPos > 0.01 && maxPos <= 0.05, "breathing amplitude " + maxPos);
        assertTrue(maxAngle > 0.1 && maxAngle <= 0.5, "breathing angle " + maxAngle);
    }

    @Test
    void breathingIsSmoothFromFrameToFrame() {
        CameraMotion.View a = CameraMotion.view(cam(CameraSpot.BREATHING, 0, 0), 10);
        CameraMotion.View b = CameraMotion.view(cam(CameraSpot.BREATHING, 0, 0), 10 + 1 / 60.0);
        assertTrue(Math.abs(a.x() - b.x()) < 0.001);
        assertTrue(Math.abs(a.yaw() - b.yaw()) < 0.01);
    }

    @Test
    void dollyPushesForwardAndStops() {
        // Yaw 0 looks toward +z
        CameraMotion.View early = CameraMotion.view(cam(CameraSpot.DOLLY, 0, 0), 2);
        CameraMotion.View late = CameraMotion.view(cam(CameraSpot.DOLLY, 0, 0), 600);
        assertTrue(early.z() > -5 + 0.1 && early.z() < -5 + 0.3, "early " + early.z());
        assertEquals(-5 + CameraMotion.DOLLY_MAX, late.z(), 0.05);
        assertEquals(CameraMotion.DOLLY_SPEED, CameraMotion.dolly(0.001) / 0.001, 1e-3);
    }

    @Test
    void panSweepsBothWays() {
        double min = 0, max = 0;
        for (double t = 0; t < CameraMotion.PAN_PERIOD; t += 0.1) {
            float yaw = CameraMotion.view(cam(CameraSpot.PAN, 90, 0), t).yaw() - 90;
            min = Math.min(min, yaw);
            max = Math.max(max, yaw);
        }
        assertTrue(max > CameraMotion.PAN_DEGREES * 0.9 && min < -CameraMotion.PAN_DEGREES * 0.9);
    }

    @Test
    void forwardAndLookAtAgree() {
        double[] f = CameraMotion.forward(90, 0);   // yaw 90 = toward -x
        assertEquals(-1, f[0], 1e-9);
        assertEquals(0, f[2], 1e-9);
        float[] look = CameraMotion.lookAt(0, 0, 0, 0, -1, 1);   // down 45° toward +z
        assertEquals(0, look[0], 1e-4);
        assertEquals(45, look[1], 1e-4);
    }

    @Test
    void followShotIsInFrontOfThePlayer() {
        // Facing +z (yaw 0): the camera stands at +z, a bit to the side, above
        double[] p = CameraMotion.followPosition(0, 64, 0, 0, 3, 1, 1.8);
        assertEquals(3, p[2], 1e-9);
        assertEquals(65.8, p[1], 1e-9);
        assertEquals(1, Math.abs(p[0]), 1e-9);
    }

    @Test
    void anglesInterpolateTheShortWay() {
        assertEquals(180, CameraMotion.lerpAngle(170, -170, 0.5), 1e-4);
        assertEquals(-175, CameraMotion.lerpAngle(-170, 180, 0.5) , 1e-4);
        assertEquals(1, CameraMotion.smoothing(10, 0.1), 1e-6);
        assertEquals(0, CameraMotion.smoothing(0, 0.1), 1e-9);
    }
}
