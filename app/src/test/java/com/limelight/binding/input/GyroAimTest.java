package com.limelight.binding.input;

import com.limelight.binding.input.GyroAim.Activation;
import com.limelight.binding.input.GyroAim.Axis;
import com.limelight.binding.input.GyroAim.Button;
import com.limelight.binding.input.GyroAim.Output;
import com.limelight.binding.input.GyroAim.Settings;
import com.limelight.nvstream.StreamInput;
import com.limelight.nvstream.input.ControllerPacket;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GyroAimTest {
    private static final short P1 = 0;
    private static final long STEP = 5_000_000L; // 200 Hz
    private static final float G = 9.80665f;
    private static final float COUNTS_PER_DEG = Settings.DEFAULT.mouseCountsPer360 / 360f;
    private static final float STICK_FULL = Settings.DEFAULT.stickTurnSpeed;

    private final List<int[]> moves = new ArrayList<>();
    private final StreamInput conn = (StreamInput) Proxy.newProxyInstance(
            StreamInput.class.getClassLoader(), new Class<?>[]{StreamInput.class},
            (proxy, method, args) -> {
                if (method.getName().equals("sendMouseMove")) {
                    moves.add(new int[]{(short) args[0], (short) args[1]});
                }
                return method.getReturnType() == int.class ? 0 : null;
            });

    private static Settings settings(Output output, Activation activation, Axis axis,
                                     float sensX, boolean invertX, boolean gyroOnly) {
        return settings(true, output, activation, Button.L2, gyroOnly,
                sensX, 1f, invertX, false, axis, 0f);
    }

    private static Settings settings(boolean aim, Output output, Activation activation,
                                     Button button, boolean gyroOnly, float sensX, float sensY,
                                     boolean invertX, boolean invertY, Axis axis, float deadzone) {
        return new GyroAim.Builder().aim(aim).output(output).activation(activation).button(button)
                .buttonGyroOnly(gyroOnly).sensX(sensX).sensY(sensY).invertX(invertX)
                .invertY(invertY).axis(axis).stickDeadzone(deadzone).build();
    }

    private static Settings mouse() {
        return settings(Output.MOUSE, Activation.ALWAYS, Axis.YAW, 1f, false, false);
    }

    private static Settings stick() {
        return settings(Output.STICK, Activation.ALWAYS, Axis.YAW, 1f, false, false);
    }

    private static float rad(double degPerSecond) {
        return (float) Math.toRadians(degPerSecond);
    }

    /** Yaw about the pad's up axis; positive = counterclockwise = camera left. */
    private static void yaw(GyroAim aim, short pad, long t, double degPerSecond) {
        aim.onGyro(pad, t, 0, rad(degPerSecond), 0);
    }

    private int sumX() {
        int sum = 0;
        for (int[] m : moves) sum += m[0];
        return sum;
    }

    private static float stickX(GyroAim aim, short pad, short physical) {
        short[] rs = {physical, 0};
        aim.mixRightStick(pad, rs);
        return rs[0] / (float) 0x7FFE;
    }

    @Test public void mouseIntegratesConstantYaw() {
        GyroAim aim = new GyroAim(mouse(), conn);
        for (int i = 0; i < 200; i++) yaw(aim, P1, i * STEP, 10);
        // 199 intervals of 5 ms at 10 deg/s, camera turns right for clockwise yaw.
        assertEquals(-10 * COUNTS_PER_DEG, sumX(), 1.5);
        for (int[] m : moves) assertEquals(0, m[1]);
    }

    @Test public void mouseAccumulatesFractionsAndTruncatesSymmetrically() {
        // 0.31 counts per 5 ms sample.
        double rate = 0.31 / (0.005 * COUNTS_PER_DEG);
        for (int sign : new int[]{1, -1}) {
            moves.clear();
            GyroAim aim = new GyroAim(mouse(), conn);
            List<Integer> sentAt = new ArrayList<>();
            yaw(aim, P1, 0, -sign * rate);
            for (int i = 1; i <= 10; i++) {
                int before = moves.size();
                yaw(aim, P1, i * STEP, -sign * rate);
                if (moves.size() > before) {
                    sentAt.add(i);
                    assertEquals(sign, moves.get(moves.size() - 1)[0]);
                }
            }
            assertEquals(List.of(4, 7, 10), sentAt);
        }
    }

    @Test public void invertAndSensitivityScaleX() {
        GyroAim aim = new GyroAim(settings(Output.MOUSE, Activation.ALWAYS, Axis.YAW,
                2f, true, false), conn);
        for (int i = 0; i < 201; i++) yaw(aim, P1, i * STEP, 10);
        assertEquals(2 * 10 * COUNTS_PER_DEG, sumX(), 1.5);
    }

    @Test public void pitchUpMovesMouseUp() {
        GyroAim aim = new GyroAim(mouse(), conn);
        for (int i = 0; i < 201; i++) aim.onGyro(P1, i * STEP, rad(10), 0, 0);
        int sumY = 0;
        for (int[] m : moves) sumY += m[1];
        assertEquals(-10 * COUNTS_PER_DEG, sumY, 1.5);
    }

    @Test public void mouseIsPlayerOneOnlyOthersGetStick() {
        GyroAim aim = new GyroAim(mouse(), conn);
        assertTrue(aim.onGyro((short) 1, 0, 0, rad(-90), 0));
        assertEquals(0.5f, stickX(aim, (short) 1, (short) 0), 0.01f);
        assertTrue(moves.isEmpty());
    }

    @Test public void mouseGearingTurnsTheCameraLikeThePad() {
        // 3600 counts per 360 deg: a 90 deg turn of the pad sends 900 counts at sens 1.0.
        GyroAim aim = new GyroAim(mouse().toBuilder().mouseCountsPer360(3600).build(), conn);
        for (int i = 0; i <= 200; i++) yaw(aim, P1, i * STEP, -90);
        assertEquals(90 * 3600 / 360.0, sumX(), 1.5);
    }

    @Test public void stickGearingDeflectsByPadRateOverGameTurnSpeed() {
        // The game turns 400 deg/s at full stick: a 100 deg/s pad turn asks for a quarter.
        GyroAim aim = new GyroAim(stick().toBuilder().stickTurnSpeed(400).build(), conn);
        yaw(aim, P1, 0, -100);
        assertEquals(100 / 400f, stickX(aim, P1, (short) 0), 1e-4f);
        aim.onGyro(P1, STEP, rad(-40), 0, 0);
        short[] rs = {0, 0};
        aim.mixRightStick(P1, rs);
        assertEquals(-40 / 400f, rs[1] / (float) 0x7FFE, 1e-4f);
    }

    @Test public void gearingIsClampedToTheOfferedRange() {
        Settings s = new GyroAim.Builder().stickTurnSpeed(1).mouseCountsPer360(1_000_000).build();
        assertEquals(GyroAim.STICK_TURN_MIN, s.stickTurnSpeed);
        assertEquals(GyroAim.MOUSE_360_MAX, s.mouseCountsPer360);
    }

    @Test public void stickFullDeflectionAndClip() {
        GyroAim aim = new GyroAim(stick(), conn);
        yaw(aim, P1, 0, -STICK_FULL);
        assertEquals(1f, stickX(aim, P1, (short) 0), 1e-4f);
        yaw(aim, P1, STEP, -2 * STICK_FULL);
        assertEquals(1f, stickX(aim, P1, (short) 0), 1e-4f);
    }

    @Test public void stickDeadzoneCompensation() {
        GyroAim aim = new GyroAim(settings(true, Output.STICK, Activation.ALWAYS, Button.L2,
                false, 1f, 1f, false, false, Axis.YAW, 0.15f), conn);
        // |g| = 0.01 -> 0.15 + 0.85 * 0.01
        yaw(aim, P1, 0, -0.01 * STICK_FULL);
        assertEquals(0.1585f, stickX(aim, P1, (short) 0), 1e-3f);
        // Physical stick already past the game's deadzone: plain sum.
        float s = 0.5f;
        assertEquals(s + 0.01f, stickX(aim, P1, (short) (s * 0x7FFE)), 1e-3f);
        // Sum is clipped to the unit circle.
        assertEquals(1f, stickX(aim, P1, (short) 0x7FFE), 1e-4f);

        GyroAim tiny = new GyroAim(settings(true, Output.STICK, Activation.ALWAYS, Button.L2,
                false, 1f, 1f, false, false, Axis.YAW, 0.15f), conn);
        yaw(tiny, P1, 0, -0.001 * STICK_FULL);
        assertEquals(0f, stickX(tiny, P1, (short) 0), 0f);
    }

    @Test public void smoothingAndTighteningAreOffByDefault() {
        GyroAim aim = new GyroAim(stick(), conn);
        for (int i = 0; i < 7; i++) yaw(aim, P1, i * STEP, -20);
        yaw(aim, P1, 7 * STEP, -1);
        assertEquals(1.0 / STICK_FULL, stickX(aim, P1, (short) 0), 1e-4);
    }

    @Test public void tighteningScalesSlowMotion() {
        GyroAim aim = new GyroAim(stick(), conn, 0, 5);
        yaw(aim, P1, 0, -2.5);
        assertEquals(1.25 / STICK_FULL, stickX(aim, P1, (short) 0), 1e-4);
        yaw(aim, P1, STEP, -10);
        assertEquals(10 / STICK_FULL, stickX(aim, P1, (short) 0), 1e-4);
    }

    @Test public void softTieredSmoothing() {
        GyroAim aim = new GyroAim(stick(), conn, 5, 0);
        double fast = 20;
        for (int i = 0; i < 7; i++) yaw(aim, P1, i * STEP, -fast);
        assertEquals(fast / STICK_FULL, stickX(aim, P1, (short) 0), 1e-4);
        // Below T/2 the output is the window mean (7 x 20 and one 0).
        yaw(aim, P1, 7 * STEP, 0);
        assertEquals(17.5 / STICK_FULL, stickX(aim, P1, (short) 0), 1e-4);
    }

    @Test public void autoCalibrationRemovesBiasOnlyWhenLyingStill() {
        GyroAim still = new GyroAim(stick(), conn);
        GyroAim held = new GyroAim(stick(), conn);
        for (int i = 0; i < 2000; i++) {
            long t = i * STEP;
            if (i % 4 == 0) {
                still.onAccel(P1, t, 0, G, 0);
                held.onAccel(P1, t, (i / 4) % 2 == 0 ? 0.3f : -0.3f, G, 0);
            }
            yaw(still, P1, t, 0.5);
            yaw(held, P1, t, 0.5);
        }
        assertEquals(0, stickX(still, P1, (short) 0) * STICK_FULL, 0.02);
        assertEquals(-0.5, stickX(held, P1, (short) 0) * STICK_FULL, 0.02);
    }

    @Test public void playerSpaceIgnoresForwardTilt() {
        GyroAim aim = new GyroAim(settings(Output.STICK, Activation.ALWAYS, Axis.PLAYER,
                1f, false, false), conn);
        float c = (float) Math.cos(Math.PI / 4), s = (float) Math.sin(Math.PI / 4);
        // Pad tilted 45 deg toward the player; world up in the pad frame is (0, c, -s).
        aim.onAccel(P1, 0, 0, G * c, -G * s);
        aim.onGyro(P1, 0, 0, rad(30) * c, -rad(30) * s);
        assertEquals(-30 / STICK_FULL, stickX(aim, P1, (short) 0), 1e-3);

        GyroAim yawOnly = new GyroAim(stick(), conn);
        yawOnly.onGyro(P1, 0, 0, rad(30) * c, -rad(30) * s);
        assertEquals(-30 * c / STICK_FULL, stickX(yawOnly, P1, (short) 0), 1e-3);
    }

    @Test public void gravityFilterIgnoresShocksAndFollowsRotation() {
        Settings player = settings(Output.STICK, Activation.ALWAYS, Axis.PLAYER, 1f, false, false);
        // Level pad, one 3 g shock along the roll axis: roll reads as world yaw only through
        // the gravity tilt, which must stay under 2 degrees.
        GyroAim shocked = new GyroAim(player, conn);
        shocked.onAccel(P1, 0, 0, G, 0);
        shocked.onAccel(P1, 20_000_000L, 0, 0, 3 * G);
        shocked.onGyro(P1, 20_000_000L, 0, 0, rad(30));
        double turn = Math.abs(stickX(shocked, P1, (short) 0) * STICK_FULL);
        assertTrue(turn > 0);
        assertTrue(turn < 30 * GyroAim.PLAYER_YAW_RELAX * Math.sin(Math.toRadians(2)));

        // Roll the pad 90 degrees with no accelerometer samples: up follows the gyro to the
        // pad's X axis, so local yaw no longer turns the camera.
        GyroAim rolled = new GyroAim(player, conn);
        rolled.onAccel(P1, 0, 0, G, 0);
        for (int i = 0; i <= 100; i++) rolled.onGyro(P1, i * STEP, 0, 0, rad(180));
        rolled.onGyro(P1, 101 * STEP, 0, rad(30), 0);
        assertEquals(0, stickX(rolled, P1, (short) 0), 0.01);
    }

    @Test public void holdActivationUsesTriggerThreshold() {
        GyroAim aim = new GyroAim(settings(true, Output.STICK, Activation.HOLD_ON, Button.L2,
                false, 1f, 1f, false, false, Axis.YAW, 0.15f), conn);
        aim.onPadState(P1, 0, (byte) 50, (byte) 0, (short) 0, (short) 0);
        assertFalse(aim.onGyro(P1, 0, 0, rad(-90), 0));
        aim.onPadState(P1, 0, (byte) 51, (byte) 0, (short) 0, (short) 0);
        assertTrue(aim.onGyro(P1, STEP, 0, rad(-90), 0));
        // Release: stick returns to zero in the same packet.
        aim.onPadState(P1, 0, (byte) 0, (byte) 0, (short) 0, (short) 0);
        assertEquals(0f, stickX(aim, P1, (short) 0), 0f);
        // L2 still reaches the game in hold mode.
        assertEquals((byte) 200, aim.filterTrigger(true, (byte) 200));
    }

    @Test public void ratchetAndToggle() {
        GyroAim ratchet = new GyroAim(settings(true, Output.STICK, Activation.HOLD_OFF,
                Button.R1, false, 1f, 1f, false, false, Axis.YAW, 0.15f), conn);
        assertTrue(ratchet.onGyro(P1, 0, 0, rad(-90), 0));
        ratchet.onPadState(P1, ControllerPacket.RB_FLAG, (byte) 0, (byte) 0, (short) 0, (short) 0);
        assertEquals(0f, stickX(ratchet, P1, (short) 0), 0f);

        GyroAim toggle = new GyroAim(settings(true, Output.STICK, Activation.TOGGLE,
                Button.R1, false, 1f, 1f, false, false, Axis.YAW, 0.15f), conn);
        assertTrue(toggle.onGyro(P1, 0, 0, rad(-90), 0)); // on at stream start
        toggle.onPadState(P1, ControllerPacket.RB_FLAG, (byte) 0, (byte) 0, (short) 0, (short) 0);
        toggle.onPadState(P1, ControllerPacket.RB_FLAG, (byte) 0, (byte) 0, (short) 0, (short) 0); // level, not edge
        toggle.onPadState(P1, 0, (byte) 0, (byte) 0, (short) 0, (short) 0);
        assertFalse(toggle.onGyro(P1, STEP, 0, rad(-90), 0));
        toggle.onPadState(P1, ControllerPacket.RB_FLAG, (byte) 0, (byte) 0, (short) 0, (short) 0);
        assertTrue(toggle.onGyro(P1, 2 * STEP, 0, rad(-90), 0));
    }

    @Test public void gyroOnlyButtonIsMaskedOnlyWhenRequested() {
        int map = ControllerPacket.RB_FLAG | ControllerPacket.A_FLAG;
        GyroAim masked = new GyroAim(settings(true, Output.MOUSE, Activation.TOGGLE,
                Button.R1, true, 1f, 1f, false, false, Axis.YAW, 0.15f), conn);
        assertEquals(ControllerPacket.A_FLAG, masked.onPadState(P1, map, (byte) 0, (byte) 0, (short) 0, (short) 0));
        GyroAim open = new GyroAim(settings(true, Output.MOUSE, Activation.TOGGLE,
                Button.R1, false, 1f, 1f, false, false, Axis.YAW, 0.15f), conn);
        assertEquals(map, open.onPadState(P1, map, (byte) 0, (byte) 0, (short) 0, (short) 0));
        GyroAim maskedL2 = new GyroAim(settings(true, Output.MOUSE, Activation.HOLD_OFF,
                Button.L2, true, 1f, 1f, false, false, Axis.YAW, 0.15f), conn);
        assertEquals((byte) 0, maskedL2.filterTrigger(true, (byte) 200));
        assertEquals((byte) 200, maskedL2.filterTrigger(false, (byte) 200));
    }

    @Test public void pauseZeroesStickAndRestartsFromZeroDt() {
        GyroAim aim = new GyroAim(stick(), conn);
        yaw(aim, P1, 0, -90);
        assertTrue(aim.pause(P1));
        assertEquals(0f, stickX(aim, P1, (short) 0), 0f);
        assertFalse(aim.pause(P1));

        GyroAim m = new GyroAim(mouse(), conn);
        yaw(m, P1, 0, -90);
        m.pause(P1);
        yaw(m, P1, STEP, -90); // first sample after pause integrates nothing
        assertTrue(moves.isEmpty());
    }

    @Test public void gapsAndNonMonotonicTimestampsDoNotJump() {
        GyroAim aim = new GyroAim(mouse(), conn);
        yaw(aim, P1, 0, -90);
        yaw(aim, P1, 500_000_000L, -90);
        assertTrue(moves.isEmpty());
        yaw(aim, P1, 400_000_000L, -90);
        assertTrue(moves.isEmpty());
        yaw(aim, P1, 500_000_000L + STEP, -90);
        assertEquals((int) (90 * 0.005 * COUNTS_PER_DEG), sumX());
    }

    @Test public void liveSettingsSwitchOutputAndModeKeepingBias() {
        GyroAim aim = new GyroAim(stick(), conn);
        for (int i = 0; i < 2000; i++) {
            if (i % 4 == 0) aim.onAccel(P1, i * STEP, 0, G, 0);
            yaw(aim, P1, i * STEP, 0.5);
        }
        aim.setSettings(mouse());
        assertEquals(0f, stickX(aim, P1, (short) 0), 0f);
        // The learned 0.5 deg/s bias survives: a still pad moves no mouse counts.
        for (int i = 0; i < 200; i++) yaw(aim, P1, (2000 + i) * STEP, 0.5);
        assertTrue(Math.abs(sumX()) <= 1);

        aim.setSettings(Settings.DEFAULT);
        assertTrue(aim.forwardsMotionToHost());
        assertFalse(aim.onGyro(P1, 3000 * STEP, 0, rad(-90), 0));
    }

    @Test public void mouseKeepsTheWholeTurnAtTheMeasuredBluetoothRate() {
        GyroAim aim = new GyroAim(mouse(), conn);
        long step = 22_400_000L; // the longest common interval on the TV
        for (int i = 0; i <= 50; i++) yaw(aim, P1, i * step, -10);
        assertEquals(10 * 50 * 0.0224 * COUNTS_PER_DEG, sumX(), 1.5);
    }

    @Test public void accelerationDoublesFastTurnsOnly() {
        Settings standard = stick().toBuilder().acceleration(GyroAim.Acceleration.STANDARD).build();
        GyroAim aim = new GyroAim(standard, conn);
        yaw(aim, P1, 0, -GyroAim.ACCEL_FAST_DEG_S / 2);
        // Half way to the fast speed: gain 1.5.
        assertEquals(1.5 * GyroAim.ACCEL_FAST_DEG_S / 2 / STICK_FULL,
                stickX(aim, P1, (short) 0), 1e-4);
        yaw(aim, P1, STEP, -1);
        assertEquals((1 + 1 / GyroAim.ACCEL_FAST_DEG_S) / STICK_FULL,
                stickX(aim, P1, (short) 0), 1e-4);
    }

    @Test public void stickCurveUndoesTheGamesResponse() {
        GyroAim aim = new GyroAim(stick().toBuilder().stickCurve(2f).build(), conn);
        yaw(aim, P1, 0, -0.25 * STICK_FULL);
        assertEquals(0.5f, stickX(aim, P1, (short) 0), 1e-3f);
        // Physical stick already moving: plain sum, no curve.
        assertEquals(0.75f, stickX(aim, P1, (short) (0.5f * 0x7FFE)), 1e-3f);
    }

    @Test public void rightStickPausesTheGyroWhenAsked() {
        GyroAim aim = new GyroAim(stick().toBuilder().stickPausesGyro(true).build(), conn);
        assertTrue(aim.onGyro(P1, 0, 0, rad(-90), 0));
        aim.onPadState(P1, 0, (byte) 0, (byte) 0, (short) (0.3f * 0x7FFE), (short) 0);
        assertEquals(0f, stickX(aim, P1, (short) 0), 0f);
        assertFalse(aim.onGyro(P1, STEP, 0, rad(-90), 0));
        aim.onPadState(P1, 0, (byte) 0, (byte) 0, (short) (0.1f * 0x7FFE), (short) 0);
        assertTrue(aim.onGyro(P1, 2 * STEP, 0, rad(-90), 0));
    }

    @Test public void manualCalibrationWaitsForStillnessAndReportsMovement() {
        List<Boolean> results = new ArrayList<>();
        GyroAim aim = new GyroAim(stick(), conn);
        assertTrue(aim.startCalibration(P1, results::add));
        long t = 0;
        // Being put down: moving, output stays at zero.
        for (int i = 0; i < 100; i++, t += STEP) {
            assertFalse(aim.onGyro(P1, t, 0, rad(40), 0));
        }
        // Lying still with a 0.5 deg/s bias for just over 2 s.
        for (int i = 0; i < 420 && results.isEmpty(); i++, t += STEP) yaw(aim, P1, t, 0.5);
        assertEquals(List.of(true), results);
        yaw(aim, P1, t, 0.5);
        yaw(aim, P1, t + STEP, 0.5);
        assertEquals(0f, stickX(aim, P1, (short) 0), 0.001f);

        List<Boolean> moved = new ArrayList<>();
        GyroAim restless = new GyroAim(stick(), conn);
        restless.startCalibration(P1, moved::add);
        for (int i = 0; i < 2100 && moved.isEmpty(); i++) restless.onGyro(P1, i * STEP, 0, rad(30), 0);
        assertEquals(List.of(false), moved);
        assertFalse(new GyroAim(Settings.DEFAULT, conn).startCalibration(P1, moved::add));
    }

    @Test public void hostModeForwardsMotion() {
        assertTrue(new GyroAim(Settings.DEFAULT, conn).forwardsMotionToHost());
        assertFalse(new GyroAim(mouse(), conn).forwardsMotionToHost());
    }

    /** Autopilot's proposal aims only while L2 is held, whatever the output or the base. */
    @Test public void autopilotProposalAimsOnlyWhileL2IsHeld() {
        Settings always = Settings.DEFAULT.toBuilder().activation(Activation.ALWAYS)
                .button(Button.R1).build();
        for (Output output : Output.values()) {
            Settings proposal = GyroAim.aimProposal(always, output);
            assertTrue(proposal.aim);
            assertEquals(output, proposal.output);
            assertEquals(Activation.HOLD_ON, proposal.activation);
            assertEquals(Button.L2, proposal.button);
        }
    }
}
