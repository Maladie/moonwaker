package com.limelight.binding.input;

import com.limelight.nvstream.StreamInput;
import com.limelight.nvstream.input.ControllerPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * MoonWaker: client-side gyro aiming. Sole owner of per-stream gyro settings, per-pad gyro
 * state (bias, gravity, smoothing, accumulators, stick vector, activation) and of the answer
 * "does a motion sample go to the host or to aiming". Main thread only; no Android classes.
 *
 * Pad frame: X = right (pitch axis), Y = up (yaw axis), Z = toward the player (roll axis);
 * rates follow the right-hand rule. Output frame: h > 0 turns the camera right, v > 0 up.
 */
public final class GyroAim {
    public enum Output { MOUSE, STICK }
    public enum Activation { ALWAYS, HOLD_ON, HOLD_OFF, TOGGLE }
    public enum Axis { PLAYER, YAW, ROLL }
    public enum Button {
        L1(ControllerPacket.LB_FLAG), R1(ControllerPacket.RB_FLAG), L2(0), R2(0),
        L3(ControllerPacket.LS_CLK_FLAG), R3(ControllerPacket.RS_CLK_FLAG),
        TOUCHPAD(ControllerPacket.TOUCHPAD_FLAG);

        public final int flag;
        Button(int flag) { this.flag = flag; }
    }

    /** Speed-dependent gain (GyroWiki): x1 when slow, up to {@code fast} at ACCEL_FAST_DEG_S. */
    public enum Acceleration {
        NONE(1f), LOW(1.5f), STANDARD(2f), HIGH(3f);

        public final float fast;
        Acceleration(float fast) { this.fast = fast; }
    }

    /** Immutable per-game settings; DEFAULT is what a game without a "gyro" override gets. */
    public static final class Settings {
        public static final Settings DEFAULT = new Builder().build();

        public final boolean aim;          // false = motion goes to the host (upstream behavior)
        public final Output output;
        public final Activation activation;
        public final Button button;
        public final boolean buttonGyroOnly; // HOLD_OFF/TOGGLE: hide the button from the game
        public final boolean stickPausesGyro; // moving the right stick pauses the gyro (GyroWiki)
        public final float sensX, sensY;
        public final boolean invertX, invertY;
        public final Axis axis;
        public final Acceleration acceleration;
        public final float stickDeadzone;  // the game's own stick deadzone, 0..0.4
        public final float stickCurve;     // the game's stick response exponent, 1..3
        /** STICK gearing: the game's turn speed at full right-stick deflection, deg/s. */
        public final int stickTurnSpeed;
        /** MOUSE gearing: mouse counts that turn the game's camera one full circle. */
        public final int mouseCountsPer360;

        private Settings(Builder b) {
            aim = b.aim;
            output = b.output;
            activation = b.activation;
            button = b.button;
            buttonGyroOnly = b.buttonGyroOnly;
            stickPausesGyro = b.stickPausesGyro;
            sensX = b.sensX;
            sensY = b.sensY;
            invertX = b.invertX;
            invertY = b.invertY;
            axis = b.axis;
            acceleration = b.acceleration;
            stickDeadzone = b.stickDeadzone;
            stickCurve = b.stickCurve;
            stickTurnSpeed = b.stickTurnSpeed;
            mouseCountsPer360 = b.mouseCountsPer360;
        }

        public Builder toBuilder() {
            return new Builder(this);
        }
    }

    /** Field defaults are the built-in defaults. */
    public static final class Builder {
        private boolean aim;
        private Output output = Output.MOUSE;
        private Activation activation = Activation.HOLD_ON;
        private Button button = Button.L2;
        private boolean buttonGyroOnly;
        private boolean stickPausesGyro;
        private float sensX = 1f, sensY = 1f;
        private boolean invertX, invertY;
        private Axis axis = Axis.PLAYER;
        private Acceleration acceleration = Acceleration.NONE;
        private float stickDeadzone = 0.15f;
        private float stickCurve = 1f;
        private int stickTurnSpeed = 180;
        private int mouseCountsPer360 = 5040;

        public Builder() {}

        private Builder(Settings s) {
            aim = s.aim;
            output = s.output;
            activation = s.activation;
            button = s.button;
            buttonGyroOnly = s.buttonGyroOnly;
            stickPausesGyro = s.stickPausesGyro;
            sensX = s.sensX;
            sensY = s.sensY;
            invertX = s.invertX;
            invertY = s.invertY;
            axis = s.axis;
            acceleration = s.acceleration;
            stickDeadzone = s.stickDeadzone;
            stickCurve = s.stickCurve;
            stickTurnSpeed = s.stickTurnSpeed;
            mouseCountsPer360 = s.mouseCountsPer360;
        }

        public Builder aim(boolean v) { aim = v; return this; }
        public Builder output(Output v) { output = v; return this; }
        public Builder activation(Activation v) { activation = v; return this; }
        public Builder button(Button v) { button = v; return this; }
        public Builder buttonGyroOnly(boolean v) { buttonGyroOnly = v; return this; }
        public Builder stickPausesGyro(boolean v) { stickPausesGyro = v; return this; }
        public Builder sensX(float v) { sensX = v; return this; }
        public Builder sensY(float v) { sensY = v; return this; }
        public Builder invertX(boolean v) { invertX = v; return this; }
        public Builder invertY(boolean v) { invertY = v; return this; }
        public Builder axis(Axis v) { axis = v; return this; }
        public Builder acceleration(Acceleration v) { acceleration = v; return this; }
        public Builder stickDeadzone(float v) { stickDeadzone = v; return this; }
        public Builder stickCurve(float v) { stickCurve = v; return this; }
        public Builder stickTurnSpeed(int v) {
            stickTurnSpeed = Math.max(STICK_TURN_MIN, Math.min(STICK_TURN_MAX, v));
            return this;
        }
        public Builder mouseCountsPer360(int v) {
            mouseCountsPer360 = Math.max(MOUSE_360_MIN, Math.min(MOUSE_360_MAX, v));
            return this;
        }

        public Settings build() {
            return new Settings(this);
        }
    }

    /**
     * What Streaming Autopilot proposes from {@code base}: {@code output} only while L2 is held
     * (aiming down sights), never always on (owner, TV test 2026-10-23).
     */
    public static Settings aimProposal(Settings base, Output output) {
        return base.toBuilder().aim(true).output(output).activation(Activation.HOLD_ON)
                .button(Button.L2).build();
    }

    /** Activation buttons the pad can spare: L1/R1 drop out while the menu trigger uses L1+R1. */
    public static List<Button> allowedButtons(String overlayTrigger) {
        boolean bumpersTaken = "lb_rb".equals(overlayTrigger);
        List<Button> buttons = new ArrayList<>();
        for (Button button : Button.values()) {
            if (bumpersTaken && (button == Button.L1 || button == Button.R1)) continue;
            buttons.add(button);
        }
        return buttons;
    }

    // Gearing ranges and steps the settings screens offer; the Builder clamps to them.
    public static final int STICK_TURN_MIN = 60, STICK_TURN_MAX = 720, STICK_TURN_STEP = 10;
    public static final int MOUSE_360_MIN = 720, MOUSE_360_MAX = 36000, MOUSE_360_STEP = 120;

    // --- Tuning constants (stage 4 on the TV) ---
    static final float STICK_EPSILON = 0.002f;
    static final int TRIGGER_THRESHOLD = 51;            // 20% of 0..255
    // Soft tiered smoothing T and tightening Tt; 0 = off as in JSM. GyroWiki start value: 5.
    static final float SMOOTH_THRESHOLD_DEG_S = 0f;
    static final float TIGHTEN_THRESHOLD_DEG_S = 0f;
    static final int SMOOTH_SAMPLES = 25;               // 0.125 s at 200 Hz (JSM GYRO_SMOOTH_TIME)
    // BRAVIA + DualSense over BT delivers 11–23 ms apart (measured 2026-10-18), not 5 ms.
    static final float MAX_DT_S = 0.05f;
    static final float GAP_S = 0.1f;                    // longer gap = break: dt 0, fresh state
    static final float PLAYER_YAW_RELAX = 1.41f;
    static final float GRAVITY_CORRECTION = 0.02f;      // per accel sample, toward the accelerometer
    static final float ACCEL_MEAN_TAU_S = 0.25f;        // stillness reference only
    static final float STILL_TIME_S = 1f;
    static final float STILL_GYRO_MAX_DEG_S = 3f;       // raw rate bound while "lying still"
    static final float STILL_GYRO_JITTER_DEG_S = 0.5f;  // deviation from the short-term mean
    static final float STILL_ACCEL_JITTER = 0.05f;      // m/s^2 deviation from the accel mean
    static final float STILL_ACCEL_G_TOLERANCE = 0.5f;  // m/s^2 around 9.81
    static final float STILL_MEAN_TAU_S = 0.1f;
    static final float BIAS_TAU_S = 2f;
    static final float STANDARD_GRAVITY = 9.80665f;
    static final float ACCEL_FAST_DEG_S = 75f;          // GyroWiki: full fast gain from 75 deg/s
    static final float STICK_PAUSE_THRESHOLD = 0.2f;    // right-stick travel that pauses the gyro
    // Manual calibration: the pad must lie still for CAL_STILL_S within CAL_TIMEOUT_S.
    static final float CAL_STILL_S = 2f;
    static final float CAL_TIMEOUT_S = 10f;
    static final float CAL_MOVE_DEG_S = 5f;             // raw rate above this = still moving
    static final float CAL_RANGE_DEG_S = 1.5f;          // per-axis spread allowed in the window
    // Android sensor values[] index and sign for each pad-frame axis (confirm in stage 0).
    static final int IDX_X = 0, IDX_Y = 1, IDX_Z = 2;
    static final float SIGN_X = 1f, SIGN_Y = 1f, SIGN_Z = 1f;

    private static final float RAD_TO_DEG = 57.2957795f;
    private static final float DEG_TO_RAD = 1 / RAD_TO_DEG;
    private static final int MAX_PADS = 16;

    // Written on the main thread, read by the sensor registration on the background handler.
    private volatile Settings settings;
    private final StreamInput conn;
    private final Pad[] pads = new Pad[MAX_PADS];
    private final float smoothThreshold;
    private final float tightenThreshold;

    public GyroAim(Settings settings, StreamInput conn) {
        this(settings, conn, SMOOTH_THRESHOLD_DEG_S, TIGHTEN_THRESHOLD_DEG_S);
    }

    GyroAim(Settings settings, StreamInput conn, float smoothThreshold, float tightenThreshold) {
        this.settings = settings;
        this.conn = conn;
        this.smoothThreshold = smoothThreshold;
        this.tightenThreshold = tightenThreshold;
    }

    public boolean forwardsMotionToHost() {
        return !settings.aim;
    }

    public Settings settings() {
        return settings;
    }

    /**
     * Live change from the stream menu. Pads keep their bias and gravity; output and activation
     * start over. The caller pauses every pad first, so no stick deflection is left behind.
     */
    public void setSettings(Settings next) {
        settings = next;
        for (Pad p : pads) {
            if (p == null) continue;
            p.resetMotion();
            p.setStick(0, 0);
            p.pressed = false;
            p.toggled = true;
            p.active = next.activation != Activation.HOLD_ON;
        }
    }

    /**
     * Manual recalibration: the pad's output stops until it has lain still for CAL_STILL_S, then
     * the bias is the mean of that window. {@code done} gets false when it kept moving for
     * CAL_TIMEOUT_S. Returns false (and never calls {@code done}) outside aim mode.
     */
    public boolean startCalibration(short controller, Consumer<Boolean> done) {
        Pad p = pad(controller);
        if (p == null) return false;
        p.calibration = done;
        p.calStart = -1;
        p.resetCalibrationWindow();
        p.resetOutput();
        p.setStick(0, 0);
        return true;
    }

    /** Gyro sample in rad/s (Android units). Returns true when the stick vector changed. */
    public boolean onGyro(short controller, long tNanos, float v0, float v1, float v2) {
        Pad p = pad(controller);
        if (p == null) return false;
        float wx = pick(IDX_X, v0, v1, v2) * SIGN_X * RAD_TO_DEG;
        float wy = pick(IDX_Y, v0, v1, v2) * SIGN_Y * RAD_TO_DEG;
        float wz = pick(IDX_Z, v0, v1, v2) * SIGN_Z * RAD_TO_DEG;

        float dt = 0;
        if (p.hasT) {
            float gap = (tNanos - p.tNanos) / 1e9f;
            if (gap > GAP_S) p.resetMotion();
            else dt = Math.max(0, Math.min(MAX_DT_S, gap));
        }
        if (!p.hasT || tNanos > p.tNanos) p.tNanos = tNanos;
        p.hasT = true;

        if (p.calibration != null) {
            calibrateManually(p, tNanos, dt, wx, wy, wz);
            return p.setStick(0, 0);
        }
        calibrate(p, dt, wx, wy, wz);
        wx -= p.biasX;
        wy -= p.biasY;
        wz -= p.biasZ;

        // Complementary gravity filter, gyro part: a world-fixed vector turns by -omega x g.
        float rx = wx * DEG_TO_RAD * dt, ry = wy * DEG_TO_RAD * dt, rz = wz * DEG_TO_RAD * dt;
        p.setGravity(p.gx - (ry * p.gz - rz * p.gy),
                p.gy - (rz * p.gx - rx * p.gz),
                p.gz - (rx * p.gy - ry * p.gx));

        // Axis selection -> (h, v) in deg/s.
        float h;
        switch (settings.axis) {
            case YAW: h = -wy; break;
            case ROLL: h = -wz; break;
            default: {
                float worldYaw = p.gy * wy + p.gz * wz;
                float limit = (float) Math.sqrt(wy * wy + wz * wz);
                h = -Math.signum(worldYaw) * Math.min(Math.abs(worldYaw) * PLAYER_YAW_RELAX, limit);
            }
        }
        float vv = wx;

        if (!p.active) return p.setStick(0, 0);

        // Soft tiered smoothing, then tightening.
        if (smoothThreshold > 0) {
            p.smoothH[p.smoothIdx] = h;
            p.smoothV[p.smoothIdx] = vv;
            p.smoothIdx = (p.smoothIdx + 1) % SMOOTH_SAMPLES;
            if (p.smoothCount < SMOOTH_SAMPLES) p.smoothCount++;
            float avgH = 0, avgV = 0;
            for (int i = 0; i < p.smoothCount; i++) {
                avgH += p.smoothH[i];
                avgV += p.smoothV[i];
            }
            avgH /= p.smoothCount;
            avgV /= p.smoothCount;
            float speed = (float) Math.sqrt(h * h + vv * vv);
            float half = smoothThreshold / 2;
            float w = clamp((speed - half) / half, 0, 1);
            h = w * h + (1 - w) * avgH;
            vv = w * vv + (1 - w) * avgV;
        }
        if (tightenThreshold > 0) {
            float s = (float) Math.sqrt(h * h + vv * vv);
            if (s < tightenThreshold) {
                h *= s / tightenThreshold;
                vv *= s / tightenThreshold;
            }
        }

        float fast = settings.acceleration.fast;
        if (fast != 1f) {
            float f = clamp((float) Math.sqrt(h * h + vv * vv) / ACCEL_FAST_DEG_S, 0, 1);
            float gain = 1 + (fast - 1) * f;
            h *= gain;
            vv *= gain;
        }

        h *= settings.sensX * (settings.invertX ? -1 : 1);
        vv *= settings.sensY * (settings.invertY ? -1 : 1);

        // Gearing: at sens 1.0 the camera turns as far as the pad.
        if (usesMouse(controller)) {
            float countsPerDeg = settings.mouseCountsPer360 / 360f;
            p.accX += h * dt * countsPerDeg;
            p.accY += vv * dt * countsPerDeg;
            int ix = (int) p.accX;
            int iy = (int) p.accY;
            p.accX -= ix;
            p.accY -= iy;
            if ((ix | iy) != 0) conn.sendMouseMove((short) ix, (short) -iy);
            return false;
        }
        float gx = h / settings.stickTurnSpeed;
        float gy = vv / settings.stickTurnSpeed;
        float mag = (float) Math.sqrt(gx * gx + gy * gy);
        if (mag > 1) {
            gx /= mag;
            gy /= mag;
        }
        return p.setStick(gx, gy);
    }

    /** Accelerometer sample in m/s^2 (Android units). */
    public void onAccel(short controller, long tNanos, float v0, float v1, float v2) {
        Pad p = pad(controller);
        if (p == null) return;
        float ax = pick(IDX_X, v0, v1, v2) * SIGN_X;
        float ay = pick(IDX_Y, v0, v1, v2) * SIGN_Y;
        float az = pick(IDX_Z, v0, v1, v2) * SIGN_Z;
        float norm = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (norm == 0) return;
        if (!p.hasAccel || tNanos - p.accelT > GAP_S * 1e9f) {
            p.accMeanX = ax;
            p.accMeanY = ay;
            p.accMeanZ = az;
            p.accelStill = false;
            p.setGravity(ax, ay, az);
        } else {
            float dt = Math.max(0, Math.min(MAX_DT_S * 5, (tNanos - p.accelT) / 1e9f));
            float k = dt / (ACCEL_MEAN_TAU_S + dt);
            float jitter = (float) Math.sqrt(sq(ax - p.accMeanX) + sq(ay - p.accMeanY) + sq(az - p.accMeanZ));
            p.accelStill = jitter < STILL_ACCEL_JITTER
                    && Math.abs(norm - STANDARD_GRAVITY) < STILL_ACCEL_G_TOLERANCE;
            p.accMeanX += (ax - p.accMeanX) * k;
            p.accMeanY += (ay - p.accMeanY) * k;
            p.accMeanZ += (az - p.accMeanZ) * k;
            // Complementary gravity filter, accelerometer part: a small pull, so shocks barely move it.
            p.setGravity(p.gx + (ax / norm - p.gx) * GRAVITY_CORRECTION,
                    p.gy + (ay / norm - p.gy) * GRAVITY_CORRECTION,
                    p.gz + (az / norm - p.gz) * GRAVITY_CORRECTION);
        }
        p.hasAccel = true;
        p.accelT = tNanos;
    }

    /**
     * Updates activation from the merged pad state that goes to the game (right stick before
     * the gyro is mixed in). Returns the button map with a gyro-only activation button removed.
     */
    public int onPadState(short controller, int inputMap, byte leftTrigger, byte rightTrigger,
                          short rightStickX, short rightStickY) {
        Pad p = pad(controller);
        if (p == null) return inputMap;
        boolean pressed;
        switch (settings.button) {
            case L2: pressed = (leftTrigger & 0xFF) >= TRIGGER_THRESHOLD; break;
            case R2: pressed = (rightTrigger & 0xFF) >= TRIGGER_THRESHOLD; break;
            default: pressed = (inputMap & settings.button.flag) != 0;
        }
        if (pressed && !p.pressed) p.toggled = !p.toggled;
        p.pressed = pressed;
        boolean active;
        switch (settings.activation) {
            case ALWAYS: active = true; break;
            case HOLD_ON: active = pressed; break;
            case HOLD_OFF: active = !pressed; break;
            default: active = p.toggled;
        }
        if (settings.stickPausesGyro && Math.hypot(rightStickX, rightStickY) / 0x7FFE
                > STICK_PAUSE_THRESHOLD) {
            active = false;
        }
        if (active != p.active) {
            p.active = active;
            p.resetOutput();
            p.setStick(0, 0);
        }
        return hidesButton() ? inputMap & ~settings.button.flag : inputMap;
    }

    /** Trigger value the game sees; a gyro-only L2/R2 activation button is hidden. */
    public byte filterTrigger(boolean left, byte value) {
        if (!hidesButton()) return value;
        return settings.button == (left ? Button.L2 : Button.R2) ? 0 : value;
    }

    /** Adds the gyro stick vector to the physical right stick (packet units, Y up). */
    public void mixRightStick(short controller, short[] rs) {
        Pad p = pad(controller);
        if (p == null || (p.stickX == 0 && p.stickY == 0)) return;
        float sx = rs[0] / (float) 0x7FFE;
        float sy = rs[1] / (float) 0x7FFE;
        float gx = p.stickX, gy = p.stickY;
        float dz = settings.stickDeadzone;
        float gMag = (float) Math.sqrt(gx * gx + gy * gy);
        // While the physical stick rests inside the game's deadzone, undo the game's deadzone
        // and response curve; once it is past, a plain sum avoids a jump.
        if (Math.sqrt(sx * sx + sy * sy) <= Math.max(dz, STICK_EPSILON)) {
            if (gMag <= STICK_EPSILON) {
                gx = 0;
                gy = 0;
            } else {
                float curved = (float) Math.pow(gMag, 1 / settings.stickCurve);
                float scale = (dz + (1 - dz) * curved) / gMag;
                gx *= scale;
                gy *= scale;
            }
        }
        float ox = sx + gx, oy = sy + gy;
        float mag = (float) Math.sqrt(ox * ox + oy * oy);
        if (mag > 1) {
            ox /= mag;
            oy /= mag;
        }
        rs[0] = (short) Math.round(ox * 0x7FFE);
        rs[1] = (short) Math.round(oy * 0x7FFE);
    }

    /**
     * Local UI gate, mouse emulation, sensors off: zeroes output and clears accumulators; the
     * next sample integrates from dt = 0. Returns true when the stick vector was non-zero, so
     * the caller must send a packet.
     */
    public boolean pause(short controller) {
        Pad p = pad(controller);
        if (p == null) return false;
        p.resetMotion();
        return p.setStick(0, 0);
    }

    /** Pad gone: forget its bias and activation. */
    public void remove(short controller) {
        if (controller >= 0 && controller < MAX_PADS) pads[controller] = null;
    }

    private boolean usesMouse(short controller) {
        return settings.output == Output.MOUSE && controller == 0;
    }

    private boolean hidesButton() {
        return settings.buttonGyroOnly && (settings.activation == Activation.HOLD_OFF
                || settings.activation == Activation.TOGGLE);
    }

    private static void calibrateManually(Pad p, long tNanos, float dt, float wx, float wy,
                                          float wz) {
        if (p.calStart < 0) p.calStart = tNanos;
        boolean moving = Math.sqrt(wx * wx + wy * wy + wz * wz) > CAL_MOVE_DEG_S;
        if (!moving) {
            p.calMin[0] = Math.min(p.calMin[0], wx);
            p.calMin[1] = Math.min(p.calMin[1], wy);
            p.calMin[2] = Math.min(p.calMin[2], wz);
            p.calMax[0] = Math.max(p.calMax[0], wx);
            p.calMax[1] = Math.max(p.calMax[1], wy);
            p.calMax[2] = Math.max(p.calMax[2], wz);
            for (int i = 0; i < 3; i++) {
                if (p.calMax[i] - p.calMin[i] > CAL_RANGE_DEG_S) moving = true;
            }
        }
        if (moving) {
            p.resetCalibrationWindow();
        } else {
            p.calTime += dt;
            p.calSumX += wx;
            p.calSumY += wy;
            p.calSumZ += wz;
            p.calCount++;
            if (p.calTime >= CAL_STILL_S) {
                p.biasX = p.calSumX / p.calCount;
                p.biasY = p.calSumY / p.calCount;
                p.biasZ = p.calSumZ / p.calCount;
                finishCalibration(p, true);
                return;
            }
        }
        if ((tNanos - p.calStart) / 1e9f > CAL_TIMEOUT_S) finishCalibration(p, false);
    }

    private static void finishCalibration(Pad p, boolean ok) {
        Consumer<Boolean> done = p.calibration;
        p.calibration = null;
        p.resetMotion();
        done.accept(ok);
    }

    private static void calibrate(Pad p, float dt, float wx, float wy, float wz) {
        if (dt <= 0) return;
        float k = dt / (STILL_MEAN_TAU_S + dt);
        p.meanX += (wx - p.meanX) * k;
        p.meanY += (wy - p.meanY) * k;
        p.meanZ += (wz - p.meanZ) * k;
        float jitter = (float) Math.sqrt(sq(wx - p.meanX) + sq(wy - p.meanY) + sq(wz - p.meanZ));
        float rate = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
        boolean still = p.accelStill && jitter < STILL_GYRO_JITTER_DEG_S
                && rate < STILL_GYRO_MAX_DEG_S;
        p.stillTime = still ? p.stillTime + dt : 0;
        if (p.stillTime < STILL_TIME_S) return;
        float b = dt / BIAS_TAU_S;
        p.biasX += (wx - p.biasX) * b;
        p.biasY += (wy - p.biasY) * b;
        p.biasZ += (wz - p.biasZ) * b;
    }

    private Pad pad(short controller) {
        if (!settings.aim || controller < 0 || controller >= MAX_PADS) return null;
        if (pads[controller] == null) pads[controller] = new Pad(settings.activation != Activation.HOLD_ON);
        return pads[controller];
    }

    private static float pick(int index, float v0, float v1, float v2) {
        return index == 0 ? v0 : index == 1 ? v1 : v2;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float sq(float v) {
        return v * v;
    }

    private static final class Pad {
        long tNanos;
        boolean hasT;
        float biasX, biasY, biasZ;
        float meanX, meanY, meanZ;
        float stillTime;
        boolean accelStill;
        boolean hasAccel;
        long accelT;
        float accMeanX, accMeanY = STANDARD_GRAVITY, accMeanZ;
        float gx, gy = 1, gz;          // gravity estimate: unit "up" vector in the pad frame
        final float[] smoothH = new float[SMOOTH_SAMPLES];
        final float[] smoothV = new float[SMOOTH_SAMPLES];
        int smoothIdx, smoothCount;
        float accX, accY;
        float stickX, stickY;
        boolean pressed, toggled = true, active;
        Consumer<Boolean> calibration;  // non-null while a manual calibration runs
        long calStart;
        float calTime, calSumX, calSumY, calSumZ;
        int calCount;
        final float[] calMin = new float[3];
        final float[] calMax = new float[3];

        Pad(boolean initiallyActive) {
            active = initiallyActive;
        }

        void resetMotion() {
            hasT = false;
            resetOutput();
        }

        void resetCalibrationWindow() {
            calTime = 0;
            calSumX = 0;
            calSumY = 0;
            calSumZ = 0;
            calCount = 0;
            for (int i = 0; i < 3; i++) {
                calMin[i] = Float.MAX_VALUE;
                calMax[i] = -Float.MAX_VALUE;
            }
        }

        void resetOutput() {
            accX = 0;
            accY = 0;
            smoothIdx = 0;
            smoothCount = 0;
        }

        void setGravity(float x, float y, float z) {
            float n = (float) Math.sqrt(x * x + y * y + z * z);
            if (n == 0) return;
            gx = x / n;
            gy = y / n;
            gz = z / n;
        }

        boolean setStick(float x, float y) {
            boolean changed = x != stickX || y != stickY;
            stickX = x;
            stickY = y;
            return changed;
        }
    }
}
