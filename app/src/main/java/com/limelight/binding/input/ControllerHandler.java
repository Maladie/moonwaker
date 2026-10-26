package com.limelight.binding.input;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.hardware.BatteryState;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.input.InputManager;
import android.hardware.lights.Light;
import android.hardware.lights.LightState;
import android.hardware.lights.LightsManager;
import android.hardware.lights.LightsRequest;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.CombinedVibration;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.widget.Toast;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.audio.AudioHaptics;
import com.limelight.binding.input.StreamGamepadPolicy.DeviceKind;
import com.limelight.binding.input.driver.AbstractController;
import com.limelight.binding.input.driver.UsbDriverListener;
import com.limelight.binding.input.driver.UsbDriverService;
import com.limelight.diagnostics.MoonWakerDiagnostics;
import com.limelight.nvstream.StreamInput;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.GameGestures;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.Vector2d;

import org.cgutman.shieldcontrollerextensions.SceChargingState;
import org.cgutman.shieldcontrollerextensions.SceConnectionType;
import org.cgutman.shieldcontrollerextensions.SceManager;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class ControllerHandler implements InputManager.InputDeviceListener, UsbDriverListener {

    private final ControllerGuidePolicy guidePolicy = new ControllerGuidePolicy();

    public ControllerGuidePolicy getGuidePolicy() {
        return guidePolicy;
    }

    /**
     * Listener interface for physical controller button state changes.
     * Allows observers to react to gamepad input changes (e.g., update virtual controller visuals).
     */
    public interface ControllerInputListener {
        /**
         * Called when the aggregated controller button state changes.
         * @param buttonFlags The current button state flags from ControllerPacket
         */
        void onControllerInputChanged(int buttonFlags);
    }

    /**
     * Stream menu shortcuts decided by {@link StreamMenuButtons}.
     */
    public interface OverlayMenuListener {
        /** A held menu trigger or a held Back: open the stream menu. */
        void onOverlayMenuOpen();

        /** Back tapped (released before the hold time): stream navigation Back. */
        void onBackTap();

        /** Guide/PS tapped: Home, back to the console carousel. */
        void onHomeTap();

        /**
         * Xbox Share tapped or held, or PlayStation Create held ("Menu and screenshots"): a
         * screenshot, or ({@code clip}) the last 30 s, saved on the PC.
         */
        void onCaptureShortcut(boolean clip);
    }

    public static final class ControllerBatteryInfo {
        public final int controllerNumber;
        public final String controllerName;
        public final int percentage;
        public final int status;

        ControllerBatteryInfo(int controllerNumber, String controllerName, int percentage, int status) {
            this.controllerNumber = controllerNumber;
            this.controllerName = controllerName;
            this.percentage = percentage;
            this.status = status;
        }

        public boolean isCharging() { return status == BatteryState.STATUS_CHARGING; }
        public boolean isFull() { return status == BatteryState.STATUS_FULL; }
    }

    private static final int MAXIMUM_BUMPER_UP_DELAY_MS = 100;

    private static final int START_DOWN_TIME_MOUSE_MODE_MS = 750;

    private static final int MINIMUM_BUTTON_DOWN_TIME_MS = 25;

    private static final int OVERLAY_TRIGGER_TAP_RELEASE_DELAY_MS = 25;

    private static final int EMULATING_SPECIAL = 0x1;
    private static final int EMULATING_SELECT = 0x2;
    private static final int EMULATING_TOUCHPAD = 0x4;

    private static final short MAX_GAMEPADS = 16; // Limited by bits in activeGamepadMask

    private static final int BATTERY_RECHECK_INTERVAL_MS = 120 * 1000;
    private static final String GYRO_LOG_TAG = "GyroAim";
    private static final int GYRO_AIM_GYRO_PERIOD_US = 5_000;    // 200 Hz
    private static final int GYRO_AIM_ACCEL_PERIOD_US = 20_000;  // 50 Hz: gravity and stillness

    private static final Map<Integer, Integer> ANDROID_TO_LI_BUTTON_MAP = Map.ofEntries(
            Map.entry(KeyEvent.KEYCODE_BUTTON_A, ControllerPacket.A_FLAG),
            Map.entry(KeyEvent.KEYCODE_BUTTON_B, ControllerPacket.B_FLAG),
            Map.entry(KeyEvent.KEYCODE_BUTTON_X, ControllerPacket.X_FLAG),
            Map.entry(KeyEvent.KEYCODE_BUTTON_Y, ControllerPacket.Y_FLAG),
            Map.entry(KeyEvent.KEYCODE_DPAD_UP, ControllerPacket.UP_FLAG),
            Map.entry(KeyEvent.KEYCODE_DPAD_DOWN, ControllerPacket.DOWN_FLAG),
            Map.entry(KeyEvent.KEYCODE_DPAD_LEFT, ControllerPacket.LEFT_FLAG),
            Map.entry(KeyEvent.KEYCODE_DPAD_RIGHT, ControllerPacket.RIGHT_FLAG),
            Map.entry(KeyEvent.KEYCODE_DPAD_UP_LEFT, ControllerPacket.UP_FLAG | ControllerPacket.LEFT_FLAG),
            Map.entry(KeyEvent.KEYCODE_DPAD_UP_RIGHT, ControllerPacket.UP_FLAG | ControllerPacket.RIGHT_FLAG),
            Map.entry(KeyEvent.KEYCODE_DPAD_DOWN_LEFT, ControllerPacket.DOWN_FLAG | ControllerPacket.LEFT_FLAG),
            Map.entry(KeyEvent.KEYCODE_DPAD_DOWN_RIGHT, ControllerPacket.DOWN_FLAG | ControllerPacket.RIGHT_FLAG),
            Map.entry(KeyEvent.KEYCODE_BUTTON_L1, ControllerPacket.LB_FLAG),
            Map.entry(KeyEvent.KEYCODE_BUTTON_R1, ControllerPacket.RB_FLAG),
            Map.entry(KeyEvent.KEYCODE_BUTTON_THUMBL, ControllerPacket.LS_CLK_FLAG),
            Map.entry(KeyEvent.KEYCODE_BUTTON_THUMBR, ControllerPacket.RS_CLK_FLAG),
            Map.entry(KeyEvent.KEYCODE_BUTTON_START, ControllerPacket.PLAY_FLAG),
            Map.entry(KeyEvent.KEYCODE_MENU, ControllerPacket.PLAY_FLAG),
            Map.entry(KeyEvent.KEYCODE_BUTTON_SELECT, ControllerPacket.BACK_FLAG),
            Map.entry(KeyEvent.KEYCODE_BACK, ControllerPacket.BACK_FLAG),
            Map.entry(KeyEvent.KEYCODE_BUTTON_MODE, ControllerPacket.SPECIAL_BUTTON_FLAG),

            // This is the Xbox Series X Share button
            Map.entry(KeyEvent.KEYCODE_MEDIA_RECORD, ControllerPacket.MISC_FLAG),

            // This is a weird one, but it's what Android does prior to 4.10 kernels
            // where DualShock/DualSense touchpads weren't mapped as separate devices.
            // https://android.googlesource.com/platform/frameworks/base/+/master/data/keyboards/Vendor_054c_Product_0ce6_fallback.kl
            // https://android.googlesource.com/platform/frameworks/base/+/master/data/keyboards/Vendor_054c_Product_09cc.kl
            Map.entry(KeyEvent.KEYCODE_BUTTON_1, ControllerPacket.TOUCHPAD_FLAG)

            // FIXME: Paddles?
    );

    private final Vector2d inputVector = new Vector2d();

    private final SparseArray<InputDeviceContext> inputDeviceContexts = new SparseArray<>();
    private final SparseArray<UsbDeviceContext> usbDeviceContexts = new SparseArray<>();

    private final StreamInput conn;
    private final Activity activityContext;
    private double stickDeadzone;
    private final InputDeviceContext defaultContext = new InputDeviceContext();
    private final GameGestures gestures;
    private final InputManager inputManager;
    private final Vibrator deviceVibrator;
    private final VibratorManager deviceVibratorManager;
    private final SensorManager deviceSensorManager;
    private final SceManager sceManager;
    private final Handler mainThreadHandler;
    private final HandlerThread backgroundHandlerThread;
    private final Handler backgroundThreadHandler;
    private boolean hasGameController;
    private boolean stopped = false;
    private volatile boolean inputSuppressed;
    private volatile int audioLowFreqMotor;
    private final Runnable applyAudioRumble = this::reapplyRumble;
    // MoonWaker: client-side gyro aiming; GyroAim owns its settings and per-pad state.
    private final GyroAim gyroAim;
    private final short[] gyroRightStick = new short[2];
    private BooleanSupplier localUiOpen = () -> false;

    private final PreferenceConfiguration prefConfig;
    // MoonWaker: controller numbers and remote-vs-pad routing are owned by StreamGamepadPolicy.
    private final StreamGamepadPolicy gamepadPolicy = new StreamGamepadPolicy(MAX_GAMEPADS);
    // Written by the USB driver thread, read by the main-thread presence refresh.
    private volatile int usbPadCount;
    private long lastLocalOnlyRecordMs;
    private int localOnlyKeysSinceRecord;
    private String overlayHoldSource = "pad_shortcut";
    private static final long LOCAL_ONLY_RECORD_INTERVAL_MS = 10_000;

    public boolean pendingApplicationQuit = false;
    private ControllerInputListener controllerInputListener = null;
    private OverlayMenuListener overlayMenuListener = null;
    private final StreamMenuButtons menuButtons;
    private int overlayTriggerButtonFlag = ControllerPacket.BACK_FLAG;
    /** The pad holding the pending menu button, and the one holding Share. */
    private InputDeviceContext holdContext;
    private InputDeviceContext shareContext;
    /** Screenshots and clips work on this stream (#51); else Share and Select are the host's. */
    private boolean capturesAvailable;

    public ControllerHandler(Activity activityContext, StreamInput conn, GameGestures gestures, PreferenceConfiguration prefConfig) {
        this.activityContext = activityContext;
        this.conn = conn;
        this.gestures = gestures;
        this.prefConfig = prefConfig;
        this.gyroAim = new GyroAim(prefConfig.gyroAim, conn);

        overlayTriggerButtonFlag = triggerFlag(prefConfig.overlayTriggerButton);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            this.deviceVibratorManager = (VibratorManager) activityContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            this.deviceVibrator = this.deviceVibratorManager.getDefaultVibrator();
        }
        else {
            this.deviceVibratorManager = null;
            this.deviceVibrator = (Vibrator) activityContext.getSystemService(Context.VIBRATOR_SERVICE);
        }
        this.deviceSensorManager = (SensorManager) activityContext.getSystemService(Context.SENSOR_SERVICE);
        this.inputManager = (InputManager) activityContext.getSystemService(Context.INPUT_SERVICE);
        this.mainThreadHandler = new Handler(Looper.getMainLooper());
        this.menuButtons = new StreamMenuButtons(new StreamMenuButtons.Scheduler() {
            @Override public void postDelayed(Runnable task, long delayMs) {
                mainThreadHandler.postDelayed(task, delayMs);
            }

            @Override public void remove(Runnable task) {
                mainThreadHandler.removeCallbacks(task);
            }
        }, prefConfig.overlayHoldDurationMs, new StreamMenuButtons.Effects() {
            @Override public void openMenu() {
                openStreamMenu();
            }

            @Override public void navigateBack() {
                if (overlayMenuListener != null) overlayMenuListener.onBackTap();
            }

            @Override public void navigateHome() {
                if (overlayMenuListener != null) overlayMenuListener.onHomeTap();
            }

            @Override public void holdGuideOnHost() {
                holdContext.inputMap |= ControllerPacket.SPECIAL_BUTTON_FLAG;
                sendControllerInputPacket(holdContext);
            }

            @Override public void capture(boolean clip) {
                if (overlayMenuListener != null) overlayMenuListener.onCaptureShortcut(clip);
            }
        });

        // Create a HandlerThread to process battery state updates. These can be slow enough
        // that they lead to ANRs if we do them on the main thread.
        this.backgroundHandlerThread = new HandlerThread("ControllerHandler");
        this.backgroundHandlerThread.start();
        this.backgroundThreadHandler = new Handler(backgroundHandlerThread.getLooper());

        this.sceManager = new SceManager(activityContext);
        this.sceManager.start();

        int[] ids = InputDevice.getDeviceIds();
        for (int id : ids) {
            InputDevice dev = InputDevice.getDevice(id);
            if (dev == null) {
                // This device was removed during enumeration
                continue;
            }
            if ((dev.getSources() & InputDevice.SOURCE_JOYSTICK) != 0 ||
                    (dev.getSources() & InputDevice.SOURCE_GAMEPAD) != 0) {
                // This looks like a gamepad, but we'll check X and Y to be sure
                if (getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_X) != null &&
                    getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_Y) != null) {
                    // This is a gamepad
                    hasGameController = true;
                }
            }
        }

        this.stickDeadzone = stickDeadzone(prefConfig.deadzonePercentage);

        // Initialize the default context for events with no device
        defaultContext.leftStickXAxis = MotionEvent.AXIS_X;
        defaultContext.leftStickYAxis = MotionEvent.AXIS_Y;
        defaultContext.rightStickXAxis = MotionEvent.AXIS_Z;
        defaultContext.rightStickYAxis = MotionEvent.AXIS_RZ;
        defaultContext.leftTriggerAxis = MotionEvent.AXIS_BRAKE;
        defaultContext.rightTriggerAxis = MotionEvent.AXIS_GAS;
        defaultContext.hatXAxis = MotionEvent.AXIS_HAT_X;
        defaultContext.hatYAxis = MotionEvent.AXIS_HAT_Y;
        defaultContext.controllerNumber = (short) 0;
        defaultContext.assignedControllerNumber = true;
        defaultContext.external = false;

        // Get the initially attached set of gamepads. As each gamepad receives
        // its initial InputEvent, we will move these from this set onto the
        // reserved controller set which will allow them to properly unplug
        // if they are removed.
        gamepadPolicy.setInitialMask(getAttachedControllerMask(activityContext));
        refreshRealPadPresence();

        // Register ourselves for input device notifications
        inputManager.registerInputDeviceListener(this, null);
    }

    private static InputDevice.MotionRange getMotionRangeForJoystickAxis(InputDevice dev, int axis) {
        InputDevice.MotionRange range;

        // First get the axis for SOURCE_JOYSTICK
        range = dev.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK);
        if (range == null) {
            // Now try the axis for SOURCE_GAMEPAD
            range = dev.getMotionRange(axis, InputDevice.SOURCE_GAMEPAD);
        }

        return range;
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        trackInputDeviceIfGamepad(InputDevice.getDevice(deviceId));
        refreshRealPadPresence();
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        InputDeviceContext context = inputDeviceContexts.get(deviceId);
        if (context != null) {
            LimeLog.info("Removed controller: "+context.name+" ("+deviceId+")");
            if (context.gyroListener != null) gyroAim.remove(context.controllerNumber);
            releaseControllerNumber(context, "device_removed");
            // A hold of a pad that went away must not fire a capture or the menu later.
            if (context == holdContext) {
                menuButtons.cancel();
                holdContext = null;
            }
            if (context == shareContext) {
                menuButtons.cancelShare();
                shareContext = null;
            }
            context.destroy();
            inputDeviceContexts.remove(deviceId);
        }
        refreshRealPadPresence();
    }

    private static DeviceKind classifyDevice(InputDevice dev) {
        return StreamGamepadPolicy.classify(dev.getSources(), hasJoystickAxes(dev));
    }

    // MoonWaker: re-evaluates remote routing; a remote holding a number yields it to a new pad.
    // Main thread only: it owns inputDeviceContexts (USB callbacks post here).
    private void refreshRealPadPresence() {
        if (stopped) return;
        boolean present = usbPadCount > 0;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice dev = InputDevice.getDevice(id);
            if (dev != null && classifyDevice(dev) == DeviceKind.REAL_PAD) {
                present = true;
                break;
            }
        }
        java.util.List<InputDeviceContext> holders = new java.util.ArrayList<>();
        for (int i = 0; i < inputDeviceContexts.size(); i++) holders.add(inputDeviceContexts.valueAt(i));
        gamepadPolicy.updateRealPadPresence(present, holders);
    }

    private boolean isStreamGamepad(GenericControllerContext context) {
        return gamepadPolicy.isStreamGamepad(context.deviceKind);
    }

    // This can happen when gaining/losing input focus with some devices.
    // Input devices that have a trackpad may gain/lose AXIS_RELATIVE_X/Y.
    @Override
    public void onInputDeviceChanged(int deviceId) {
        InputDevice device = InputDevice.getDevice(deviceId);
        if (device == null) {
            return;
        }

        // If we don't have a context for this device, we don't need to update anything
        InputDeviceContext existingContext = inputDeviceContexts.get(deviceId);
        if (existingContext == null) {
            return;
        }

        LimeLog.info("Device changed: "+existingContext.name+" ("+deviceId+")");

        // Migrate the existing context into this new one by moving any stateful elements
        InputDeviceContext newContext = createInputDeviceContextForDevice(device);
        newContext.migrateContext(existingContext);
        inputDeviceContexts.put(deviceId, newContext);
        refreshRealPadPresence();
    }

    /**
     * Set a listener to receive controller input state changes.
     * @param listener The listener to notify of input changes, or null to remove
     */
    public void setControllerInputListener(ControllerInputListener listener) {
        this.controllerInputListener = listener;
    }

    public void setOverlayMenuListener(OverlayMenuListener listener) {
        this.overlayMenuListener = listener;
    }

    public List<ControllerBatteryInfo> getControllerBatteryInfo() {
        // Input contexts are normally created lazily on the first InputEvent. The
        // overlay must also show connected controllers that have not been used yet.
        for (int deviceId : InputDevice.getDeviceIds()) {
            trackInputDeviceIfGamepad(InputDevice.getDevice(deviceId));
        }

        List<ControllerBatteryInfo> result = new ArrayList<>();
        boolean[] displayedControllerNumbers = new boolean[MAX_GAMEPADS];

        // Preserve real player numbers for controllers that have already sent input.
        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            InputDeviceContext context = inputDeviceContexts.valueAt(i);
            if (!context.assignedControllerNumber) {
                continue;
            }
            displayedControllerNumbers[context.controllerNumber] = true;
            int percentage = Float.isNaN(context.lastReportedBatteryCapacity) ? -1 :
                    Math.max(0, Math.min(100, Math.round(context.lastReportedBatteryCapacity * 100.f)));
            result.add(new ControllerBatteryInfo(context.controllerNumber + 1,
                    context.name != null ? context.name : "Controller",
                    percentage, context.lastReportedBatteryStatus));
        }

        // Give unused controllers stable temporary labels without assigning protocol
        // player numbers or reporting arrivals to the host prematurely.
        int nextDisplayNumber = 0;
        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            InputDeviceContext context = inputDeviceContexts.valueAt(i);
            if (context.assignedControllerNumber || !context.hasJoystickAxes) {
                continue;
            }
            while (nextDisplayNumber < displayedControllerNumbers.length &&
                    displayedControllerNumbers[nextDisplayNumber]) {
                nextDisplayNumber++;
            }
            if (nextDisplayNumber >= displayedControllerNumbers.length) {
                break;
            }
            displayedControllerNumbers[nextDisplayNumber] = true;
            int percentage = Float.isNaN(context.lastReportedBatteryCapacity) ? -1 :
                    Math.max(0, Math.min(100, Math.round(context.lastReportedBatteryCapacity * 100.f)));
            result.add(new ControllerBatteryInfo(nextDisplayNumber + 1,
                    context.name != null ? context.name : "Controller",
                    percentage, context.lastReportedBatteryStatus));
        }

        result.sort((left, right) -> Integer.compare(left.controllerNumber, right.controllerNumber));
        return result;
    }

    private void trackInputDeviceIfGamepad(InputDevice device) {
        if (device != null && hasJoystickAxes(device) &&
                inputDeviceContexts.get(device.getId()) == null) {
            inputDeviceContexts.put(device.getId(), createInputDeviceContextForDevice(device));
        }
    }

    public void refreshControllerBatteryInfo(Runnable completion) {
        List<InputDeviceContext> contexts = new ArrayList<>();
        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            contexts.add(inputDeviceContexts.valueAt(i));
        }
        backgroundThreadHandler.post(() -> {
            for (InputDeviceContext context : contexts) {
                if (!stopped && context.inputDevice != null) {
                    sendControllerBatteryPacket(context);
                }
            }
            if (completion != null) { mainThreadHandler.post(completion); }
        });
    }

    /**
     * Toggle mouse emulation mode for controller 0 (the primary controller).
     * This is typically called from the overlay menu.
     */
    public void toggleMouseEmulationForController0() {
        // Try to find an input device context for controller 0
        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            InputDeviceContext context = inputDeviceContexts.valueAt(i);
            if (context.assignedControllerNumber && context.controllerNumber == 0) {
                context.toggleMouseEmulation();
                return;
            }
        }

        // Try USB device contexts
        for (int i = 0; i < usbDeviceContexts.size(); i++) {
            UsbDeviceContext context = usbDeviceContexts.valueAt(i);
            if (context.assignedControllerNumber && context.controllerNumber == 0) {
                context.toggleMouseEmulation();
                return;
            }
        }

        // Fallback to default context if no controller 0 was found
        defaultContext.toggleMouseEmulation();
    }

    public void stop() {
        if (stopped) {
            return;
        }

        // Stop new device contexts from being created or used
        stopped = true;

        // Unregister our input device callbacks
        inputManager.unregisterInputDeviceListener(this);

        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            InputDeviceContext deviceContext = inputDeviceContexts.valueAt(i);
            deviceContext.destroy();
        }

        for (int i = 0; i < usbDeviceContexts.size(); i++) {
            UsbDeviceContext deviceContext = usbDeviceContexts.valueAt(i);
            deviceContext.destroy();
        }

        deviceVibrator.cancel();
    }

    /**
     * Suppresses packets without stopping device discovery, sensors, or controller
     * ownership. This lets a privacy overlay gate input without reinitialization.
     */
    public void setInputSuppressed(boolean suppressed) {
        inputSuppressed = suppressed;
    }

    public void releaseAllControllerInputsAndSuppress() {
        inputSuppressed = false;
        releaseAllControllerInputs();
        inputSuppressed = true;
    }

    public GyroAim.Settings getGyroAimSettings() {
        return gyroAim.settings();
    }

    /**
     * Live gyro change from the stream menu. Switching between host motion and aiming
     * re-registers the pad sensors through the usual delayed path, with the routing of the new mode.
     */
    public void setGyroAimSettings(GyroAim.Settings next) {
        boolean modeChanged = next.aim == gyroAim.forwardsMotionToHost();
        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            InputDeviceContext context = inputDeviceContexts.valueAt(i);
            if (modeChanged) context.disableSensors();
            else if (gyroAim.pause(context.controllerNumber)) sendControllerInputPacket(context);
        }
        gyroAim.setSettings(next);
        if (modeChanged) enableSensors();
    }

    /**
     * Manual gyro recalibration of every pad whose gyro is registered; {@code done} runs once per
     * such pad, on the main thread. Returns how many pads started.
     */
    public int calibrateGyro(Consumer<Boolean> done) {
        int started = 0;
        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            InputDeviceContext context = inputDeviceContexts.valueAt(i);
            if (context.gyroListener != null
                    && gyroAim.startCalibration(context.controllerNumber, done)) started++;
        }
        return started;
    }

    /** Whether a connected pad has its own gyroscope (the device's fallback sensors never aim). */
    public boolean hasGyroPad() {
        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            SensorManager sm = inputDeviceContexts.valueAt(i).sensorManager;
            if (sm != null && sm != deviceSensorManager
                    && sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) return true;
        }
        return false;
    }

    /** Read-only fact from Game: the stream menu or the IME is showing; gyro aiming pauses. */
    public void setLocalUiGate(BooleanSupplier localUiOpen) {
        this.localUiOpen = localUiOpen;
    }

    public boolean isInputSuppressed() {
        return inputSuppressed;
    }

    /** Announces attached pads with neutral state even while the privacy gate is closed. */
    public void announceConnectedControllers() {
        if (stopped) return;
        for (int deviceId : InputDevice.getDeviceIds()) {
            trackInputDeviceIfGamepad(InputDevice.getDevice(deviceId));
        }
        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            InputDeviceContext context = inputDeviceContexts.valueAt(i);
            if (context.hasJoystickAxes) assignControllerNumberIfNeeded(context);
        }
        for (int i = 0; i < usbDeviceContexts.size(); i++) {
            assignControllerNumberIfNeeded(usbDeviceContexts.valueAt(i));
        }

        // Arrival packets describe capabilities, but some games enumerate the virtual pad
        // only after receiving its first input packet. Prime every assigned controller before
        // a non-Steam game starts, even though the privacy gate still suppresses user input.
        boolean[] primedControllers = new boolean[MAX_GAMEPADS];
        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            InputDeviceContext context = inputDeviceContexts.valueAt(i);
            if (context.hasJoystickAxes) primeControllerIfNeeded(context, primedControllers);
        }
        for (int i = 0; i < usbDeviceContexts.size(); i++) {
            primeControllerIfNeeded(usbDeviceContexts.valueAt(i), primedControllers);
        }
        if (prefConfig.onscreenController) sendNeutralControllerInput((short) 0);
    }

    private void primeControllerIfNeeded(GenericControllerContext context,
                                         boolean[] primedControllers) {
        if (!context.assignedControllerNumber
                || context.controllerNumber < 0
                || context.controllerNumber >= primedControllers.length
                || primedControllers[context.controllerNumber]) return;
        primedControllers[context.controllerNumber] = true;
        sendNeutralControllerInput(context.controllerNumber);
    }

    private void sendNeutralControllerInput(short controllerNumber) {
        conn.sendControllerInput(controllerNumber, getActiveControllerMask(),
                (short) 0, (byte) 0, (byte) 0,
                (short) 0, (short) 0, (short) 0, (short) 0);
    }

    private void releaseAllControllerInputs() {
        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            resetControllerInput(inputDeviceContexts.valueAt(i));
        }
        for (int i = 0; i < usbDeviceContexts.size(); i++) {
            resetControllerInput(usbDeviceContexts.valueAt(i));
        }
        resetControllerInput(defaultContext);
        for (short c = 0; c < MAX_GAMEPADS; c++) gyroAim.pause(c);

        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            sendControllerInputPacket(inputDeviceContexts.valueAt(i));
        }
        for (int i = 0; i < usbDeviceContexts.size(); i++) {
            sendControllerInputPacket(usbDeviceContexts.valueAt(i));
        }
        sendControllerInputPacket(defaultContext);
    }

    private static void resetControllerInput(GenericControllerContext context) {
        context.inputMap = 0;
        context.leftTrigger = 0;
        context.rightTrigger = 0;
        context.leftStickX = 0;
        context.leftStickY = 0;
        context.rightStickX = 0;
        context.rightStickY = 0;
    }

    public void destroy() {
        if (!stopped) {
            stop();
        }

        sceManager.stop();
        backgroundHandlerThread.quit();
    }

    public void disableSensors() {
        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            InputDeviceContext deviceContext = inputDeviceContexts.valueAt(i);
            deviceContext.disableSensors();
        }
    }

    public void enableSensors() {
        if (stopped) {
            return;
        }

        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            InputDeviceContext deviceContext = inputDeviceContexts.valueAt(i);
            deviceContext.enableSensors();
        }
    }

    private static boolean hasJoystickAxes(InputDevice device) {
        return (device.getSources() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
                getMotionRangeForJoystickAxis(device, MotionEvent.AXIS_X) != null &&
                getMotionRangeForJoystickAxis(device, MotionEvent.AXIS_Y) != null;
    }

    private static boolean hasGamepadButtons(InputDevice device) {
        return (device.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
    }

    public static boolean isGameControllerDevice(InputDevice device) {
        if (device == null) {
            return true;
        }

        if (hasJoystickAxes(device) || hasGamepadButtons(device)) {
            // Has real joystick axes or gamepad buttons
            return true;
        }

        // HACK for https://issuetracker.google.com/issues/163120692
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            if (device.getId() == -1) {
                // This "virtual" device could be input from any of the attached devices.
                // Look to see if any gamepads are connected.
                int[] ids = InputDevice.getDeviceIds();
                for (int id : ids) {
                    InputDevice dev = InputDevice.getDevice(id);
                    if (dev == null) {
                        // This device was removed during enumeration
                        continue;
                    }

                    // If there are any gamepad devices connected, we'll
                    // report that this virtual device is a gamepad.
                    if (hasJoystickAxes(dev) || hasGamepadButtons(dev)) {
                        return true;
                    }
                }
            }
        }

        // Otherwise, we'll try anything that claims to be a non-alphabetic keyboard
        return device.getKeyboardType() != InputDevice.KEYBOARD_TYPE_ALPHABETIC;
    }

    public static short getAttachedControllerMask(Context context) {
        int count = 0;
        short mask = 0;

        // Count all input devices that are gamepads
        InputManager im = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        for (int id : im.getInputDeviceIds()) {
            InputDevice dev = im.getInputDevice(id);
            if (dev == null) {
                continue;
            }

            if (hasJoystickAxes(dev)) {
                LimeLog.info("Counting InputDevice: "+dev.getName());
                mask |= 1 << count++;
            }
        }

        // Count all USB devices that match our drivers
        if (PreferenceConfiguration.readPreferences(context).usbDriver) {
            UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            if (usbManager != null) {
                for (UsbDevice dev : usbManager.getDeviceList().values()) {
                    // We explicitly check not to claim devices that appear as InputDevices
                    // otherwise we will double count them.
                    if (UsbDriverService.shouldClaimDevice(dev, false) &&
                            !UsbDriverService.isRecognizedInputDevice(dev)) {
                        LimeLog.info("Counting UsbDevice: "+dev.getDeviceName());
                        mask |= 1 << count++;
                    }
                }
            }
        }

        if (PreferenceConfiguration.readPreferences(context).onscreenController) {
            LimeLog.info("Counting OSC gamepad");
            mask |= 1;
        }

        LimeLog.info("Enumerated "+count+" gamepads");
        return mask;
    }

    private void releaseControllerNumber(GenericControllerContext context, String reason) {
        // If we reserved a controller number, remove that reservation
        if (context.reservedControllerNumber) {
            LimeLog.info("Controller number "+context.controllerNumber+" is now available");
            gamepadPolicy.release(context.controllerNumber);
        }
        if (context.assignedControllerNumber) {
            MoonWakerDiagnostics.record("INFO", "android.input", "controller.released",
                    "kind", context.deviceKind.name(), "controller_number", context.controllerNumber,
                    "reason", reason);
        }

        // If this device sent data as a gamepad, zero the values before removing.
        // We must do this after releasing the reserved number so this
        // causes the device to be removed on the server PC.
        if (context.assignedControllerNumber) {
            conn.sendControllerInput(context.controllerNumber, getActiveControllerMask(),
                    (short) 0,
                    (byte) 0, (byte) 0,
                    (short) 0, (short) 0,
                    (short) 0, (short) 0);
        }
    }

    private boolean isAssociatedJoystick(InputDevice originalDevice, InputDevice possibleAssociatedJoystick) {
        if (possibleAssociatedJoystick == null) {
            return false;
        }

        // This can't be an associated joystick if it's not a joystick
        if ((possibleAssociatedJoystick.getSources() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) {
            return false;
        }

        // Make sure the device names *don't* match in order to prevent us from accidentally matching
        // on another of the exact same device.
        if (possibleAssociatedJoystick.getName().equals(originalDevice.getName())) {
            return false;
        }

        // Make sure the descriptor matches. This can match in cases where two of the exact same
        // input device are connected, so we perform the name check to exclude that case.
        if (!possibleAssociatedJoystick.getDescriptor().equals(originalDevice.getDescriptor())) {
            return false;
        }

        return true;
    }

    private void reserveControllerNumber(GenericControllerContext context) {
        context.controllerNumber = 0;
        if (!prefConfig.multiController) {
            // Only player 1 exists; every stream pad intentionally shares it.
            LimeLog.info("Not reserving a controller number");
            return;
        }
        short number = gamepadPolicy.reserve();
        // ponytail: all 16 slots taken shares 0 like upstream; unreachable on a TV.
        if (number < 0) return;
        context.controllerNumber = number;
        context.reservedControllerNumber = true;
    }

    // Called before sending input but after we've determined that this
    // is definitely a controller (not a keyboard, mouse, or something else)
    private void assignControllerNumberIfNeeded(GenericControllerContext context) {
        // MoonWaker: a local-only remote never receives a protocol controller number.
        if (context.assignedControllerNumber || !isStreamGamepad(context)) {
            return;
        }

        if (context instanceof InputDeviceContext) {
            InputDeviceContext devContext = (InputDeviceContext) context;

            LimeLog.info(devContext.name+" ("+context.id+") needs a controller number assigned");
            if (!devContext.external || devContext.deviceKind == DeviceKind.REMOTE
                    || devContext.deviceKind == DeviceKind.KEYBOARD) {
                // MoonWaker: built-in buttons/remotes take a pool number, never a shared hardcoded 0.
                reserveControllerNumber(context);
            }
            else if (prefConfig.multiController && devContext.hasJoystickAxes) {
                reserveControllerNumber(context);
            }
            else if (!devContext.hasJoystickAxes) {
                // If this device doesn't have joystick axes, it may be an input device associated
                // with another joystick (like a PS4 touchpad). We'll propagate that joystick's
                // controller number to this associated device.

                context.controllerNumber = 0;

                // For the DS4 case, the associated joystick is the next device after the touchpad.
                // We'll try the opposite case too, just to be a little future-proof.
                InputDevice associatedDevice = InputDevice.getDevice(devContext.id + 1);
                if (!isAssociatedJoystick(devContext.inputDevice, associatedDevice)) {
                    associatedDevice = InputDevice.getDevice(devContext.id - 1);
                    if (!isAssociatedJoystick(devContext.inputDevice, associatedDevice)) {
                        LimeLog.info("No associated joystick device found");
                        associatedDevice = null;
                    }
                }

                if (associatedDevice != null) {
                    InputDeviceContext associatedDeviceContext = inputDeviceContexts.get(associatedDevice.getId());

                    // Create a new context for the associated device if one doesn't exist
                    if (associatedDeviceContext == null) {
                        associatedDeviceContext = createInputDeviceContextForDevice(associatedDevice);
                        inputDeviceContexts.put(associatedDevice.getId(), associatedDeviceContext);
                    }

                    // Assign a controller number for the associated device if one isn't assigned
                    if (!associatedDeviceContext.assignedControllerNumber) {
                        assignControllerNumberIfNeeded(associatedDeviceContext);
                    }

                    // Propagate the associated controller number
                    context.controllerNumber = associatedDeviceContext.controllerNumber;

                    LimeLog.info("Propagated controller number from "+associatedDeviceContext.name);
                }
            }
            else {
                LimeLog.info("Not reserving a controller number");
                context.controllerNumber = 0;
            }

            devContext.sensorManager = motionSensors(devContext.sensorManager, deviceSensorManager,
                    prefConfig.gamepadMotionSensorsFallbackToDevice, context.controllerNumber == 0);
        }
        else {
            reserveControllerNumber(context);
        }

        LimeLog.info("Assigned as controller "+context.controllerNumber);
        context.assignedControllerNumber = true;
        MoonWakerDiagnostics.record("INFO", "android.input", "controller.assigned",
                "kind", context.deviceKind.name(), "controller_number", context.controllerNumber);

        // Report attributes of this new controller to the host
        context.sendControllerArrival();
    }

    private UsbDeviceContext createUsbDeviceContextForDevice(AbstractController device) {
        UsbDeviceContext context = new UsbDeviceContext();

        context.id = device.getControllerId();
        context.device = device;
        context.external = true;

        context.vendorId = device.getVendorId();
        context.productId = device.getProductId();

        context.triggerDeadzone = 0.13f;

        return context;
    }

    private static boolean hasButtonUnderTouchpad(InputDevice dev, byte type) {
        // It has to have a touchpad to have a button under it
        if ((dev.getSources() & InputDevice.SOURCE_TOUCHPAD) != InputDevice.SOURCE_TOUCHPAD) {
            return false;
        }

        // Landroid/view/InputDevice;->hasButtonUnderPad()Z is blocked after O
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
            try {
                return (Boolean) dev.getClass().getMethod("hasButtonUnderPad").invoke(dev);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (ClassCastException e) {
                e.printStackTrace();
            }
        }

        // We can't use the platform API, so we'll have to just guess based on the gamepad type.
        // If this is a PlayStation controller with a touchpad, we know it has a clickpad.
        return type == MoonBridge.LI_CTYPE_PS;
    }

    private static boolean isExternal(InputDevice dev) {
        // The ASUS Tinker Board inaccurately reports Bluetooth gamepads as internal.
        if (Build.MODEL.equals("Tinker Board")) {
            return true;
        }

        String deviceName = dev.getName();
        if (deviceName.contains("gpio") || // This is the back button on Shield portable consoles
                deviceName.contains("joy_key") || // These are the gamepad buttons on the Archos Gamepad 2
                deviceName.contains("keypad") || // These are gamepad buttons on the XPERIA Play
                deviceName.equalsIgnoreCase("NVIDIA Corporation NVIDIA Controller v01.01") || // Gamepad on Shield Portable
                deviceName.equalsIgnoreCase("NVIDIA Corporation NVIDIA Controller v01.02") || // Gamepad on Shield Portable (?)
                deviceName.equalsIgnoreCase("GR0006") // Gamepad on Logitech G Cloud
        )
        {
            LimeLog.info(dev.getName()+" is internal by hardcoded mapping");
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Landroid/view/InputDevice;->isExternal()Z is officially public on Android Q
            return dev.isExternal();
        }
        else {
            try {
                // Landroid/view/InputDevice;->isExternal()Z is on the light graylist in Android P
                return (Boolean)dev.getClass().getMethod("isExternal").invoke(dev);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (ClassCastException e) {
                e.printStackTrace();
            }
        }

        // Answer true if we don't know
        return true;
    }

    /** A pad without sensors of its own uses the device's as player 1 when the fallback is on. */
    static <S> S motionSensors(S pad, S device, boolean fallbackToDevice, boolean playerOne) {
        return pad == null && fallbackToDevice && playerOne ? device : pad;
    }

    /** Sensors gained in host-motion mode reach no game while the host never heard of them. */
    static boolean hostMissesMotion(Object previous, Object next, boolean announced,
                                    boolean forwardsToHost) {
        return next != null && next != previous && !announced && forwardsToHost;
    }

    /** The stick deadzone as a fraction; 1% is the lowest one supported. */
    static double stickDeadzone(int percent) {
        return Math.max(1, percent) / 100.0;
    }

    /** {@code mask} without controller {@code number}: the host drops that pad. */
    static short withoutController(short mask, int number) {
        return (short) (mask & ~(1 << number));
    }

    /** The pad's own motion sensors when Settings allow them, else null. */
    private SensorManager padSensorManager(InputDevice dev) {
        // On Android 12, we can try to use the InputDevice's sensors. This may not work if the
        // Linux kernel version doesn't have motion sensor support, which is common for third-party
        // gamepads.
        //
        // Android 12 has a bug that causes InputDeviceSensorManager to cause a NPE on a background
        // thread due to bad error checking in InputListener callbacks. InputDeviceSensorManager is
        // created upon the first call to InputDevice.getSensorManager(), so we avoid calling this
        // on Android 12 unless we have a gamepad that could plausibly have motion sensors.
        // https://cs.android.com/android/_/android/platform/frameworks/base/+/8970010a5e9f3dc5c069f56b4147552accfcbbeb
        if (!prefConfig.gamepadMotionSensors || dev == null
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null;
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S
                && dev.getVendorId() != 0x054c && dev.getVendorId() != 0x057e) return null; // Sony or Nintendo
        SensorManager sm = dev.getSensorManager();
        return sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
                || sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null ? sm : null;
    }

    /**
     * The controller settings in {@code prefConfig} were refreshed (Settings opened from the Home
     * may have changed them): derived values follow, and every pad re-registers the sensors the
     * new settings give it. A pad that gains motion the host never heard of is announced again,
     * since the host learns motion only from an arrival; the game sees that pad reconnect.
     */
    public void applySettings() {
        stickDeadzone = stickDeadzone(prefConfig.deadzonePercentage);
        overlayTriggerButtonFlag = triggerFlag(prefConfig.overlayTriggerButton);
        menuButtons.setHoldMs(prefConfig.overlayHoldDurationMs);
        setGyroAimSettings(prefConfig.gyroAim);
        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            InputDeviceContext context = inputDeviceContexts.valueAt(i);
            SensorManager next = motionSensors(padSensorManager(context.inputDevice),
                    deviceSensorManager, prefConfig.gamepadMotionSensorsFallbackToDevice,
                    context.assignedControllerNumber && context.controllerNumber == 0);
            SensorManager previous = context.sensorManager;
            if (next == previous) continue;
            context.disableSensors();
            context.sensorManager = next;
            if (context.assignedControllerNumber && hostMissesMotion(previous, next,
                    context.motionAnnounced, gyroAim.forwardsMotionToHost())) {
                reannounceController(context);
            }
            if (next != null) context.enableSensors();
        }
    }

    /**
     * The host ignores the arrival of a pad it already has, so the pad is dropped first (its bit
     * cleared from the mask) and then arrives with its current capabilities.
     */
    private void reannounceController(InputDeviceContext context) {
        MoonWakerDiagnostics.record("INFO", "android.input", "controller.reannounced",
                "controller_number", context.controllerNumber, "reason", "motion_sensors");
        backgroundThreadHandler.removeCallbacks(context.batteryStateUpdateRunnable);
        context.batteryStateReportedToHost = false;
        conn.sendControllerRemoval(context.controllerNumber,
                withoutController(getActiveControllerMask(), context.controllerNumber));
        context.sendControllerArrival();
    }

    private InputDeviceContext createInputDeviceContextForDevice(InputDevice dev) {
        InputDeviceContext context = new InputDeviceContext();
        String devName = dev.getName();

        LimeLog.info("Creating controller context for device: "+devName);
        LimeLog.info("Vendor ID: " + dev.getVendorId());
        LimeLog.info("Product ID: "+dev.getProductId());
        LimeLog.info(dev.toString());

        context.inputDevice = dev;
        context.name = devName;
        context.id = dev.getId();
        context.external = isExternal(dev);
        context.deviceKind = classifyDevice(dev);
        MoonWakerDiagnostics.record("INFO", "android.input", "controller.context_created",
                "kind", context.deviceKind.name());

        context.vendorId = dev.getVendorId();
        context.productId = dev.getProductId();

        // These aren't always present in the Android key layout files, so they won't show up
        // in our normal InputDevice.hasKeys() probing.
        context.hasPaddles = MoonBridge.guessControllerHasPaddles(context.vendorId, context.productId);
        context.hasShare = MoonBridge.guessControllerHasShareButton(context.vendorId, context.productId);

        // Try to use the InputDevice's associated vibrators first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasQuadAmplitudeControlledRumbleVibrators(dev.getVibratorManager())) {
            context.vibratorManager = dev.getVibratorManager();
            context.quadVibrators = true;
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasDualAmplitudeControlledRumbleVibrators(dev.getVibratorManager())) {
            context.vibratorManager = dev.getVibratorManager();
            context.quadVibrators = false;
        }
        else if (dev.getVibrator().hasVibrator()) {
            context.vibrator = dev.getVibrator();
        }
        else if (!context.external) {
            // If this is an internal controller, try to use the device's vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasQuadAmplitudeControlledRumbleVibrators(deviceVibratorManager)) {
                context.vibratorManager = deviceVibratorManager;
                context.quadVibrators = true;
            }
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasDualAmplitudeControlledRumbleVibrators(deviceVibratorManager)) {
                context.vibratorManager = deviceVibratorManager;
                context.quadVibrators = false;
            }
            else if (deviceVibrator.hasVibrator()) {
                context.vibrator = deviceVibrator;
            }
        }

        context.sensorManager = padSensorManager(dev);

        // Check if this device has a usable RGB LED and cache that result
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (Light light : dev.getLightsManager().getLights()) {
                if (light.hasRgbControl()) {
                    context.hasRgbLed = true;
                    break;
                }
            }

            // Light.hasRgbControl() was totally broken prior to Android 14.
            // It always returned true because LIGHT_CAPABILITY_RGB was defined as 0,
            // so we will just guess RGB is supported if it's a PlayStation controller.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE && context.vendorId != 0x054c) {
                context.hasRgbLed = false;
            }
        }

        // Detect if the gamepad has Mode and Select buttons according to the Android key layouts.
        // We do this first because other codepaths below may override these defaults.
        boolean[] buttons = dev.hasKeys(KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK, 0);
        context.hasMode = buttons[0];
        context.hasSelect = buttons[1] || buttons[2];

        context.touchpadXRange = dev.getMotionRange(MotionEvent.AXIS_X, InputDevice.SOURCE_TOUCHPAD);
        context.touchpadYRange = dev.getMotionRange(MotionEvent.AXIS_Y, InputDevice.SOURCE_TOUCHPAD);
        context.touchpadPressureRange = dev.getMotionRange(MotionEvent.AXIS_PRESSURE, InputDevice.SOURCE_TOUCHPAD);

        context.leftStickXAxis = MotionEvent.AXIS_X;
        context.leftStickYAxis = MotionEvent.AXIS_Y;
        if (getMotionRangeForJoystickAxis(dev, context.leftStickXAxis) != null &&
                getMotionRangeForJoystickAxis(dev, context.leftStickYAxis) != null) {
            // This is a gamepad
            hasGameController = true;
            context.hasJoystickAxes = true;
        }

        // This is hack to deal with the Nvidia Shield's modifications that causes the DS4 clickpad
        // to work as a duplicate Select button instead of a unique button we can handle separately.
        context.isDualShockStandaloneTouchpad =
                context.vendorId == 0x054c && // Sony
                devName.endsWith(" Touchpad") &&
                dev.getSources() == (InputDevice.SOURCE_KEYBOARD | InputDevice.SOURCE_MOUSE);

        InputDevice.MotionRange leftTriggerRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_LTRIGGER);
        InputDevice.MotionRange rightTriggerRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RTRIGGER);
        InputDevice.MotionRange brakeRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_BRAKE);
        InputDevice.MotionRange gasRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_GAS);
        InputDevice.MotionRange throttleRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_THROTTLE);
        if (leftTriggerRange != null && rightTriggerRange != null)
        {
            // Some controllers use LTRIGGER and RTRIGGER (like Ouya)
            context.leftTriggerAxis = MotionEvent.AXIS_LTRIGGER;
            context.rightTriggerAxis = MotionEvent.AXIS_RTRIGGER;
        }
        else if (brakeRange != null && gasRange != null)
        {
            // Others use GAS and BRAKE (like Moga)
            context.leftTriggerAxis = MotionEvent.AXIS_BRAKE;
            context.rightTriggerAxis = MotionEvent.AXIS_GAS;
        }
        else if (brakeRange != null && throttleRange != null)
        {
            // Others use THROTTLE and BRAKE (like Xiaomi)
            context.leftTriggerAxis = MotionEvent.AXIS_BRAKE;
            context.rightTriggerAxis = MotionEvent.AXIS_THROTTLE;
        }
        else
        {
            InputDevice.MotionRange rxRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RX);
            InputDevice.MotionRange ryRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RY);
            if (rxRange != null && ryRange != null && devName != null) {
                if (dev.getVendorId() == 0x054c) { // Sony
                    if (dev.hasKeys(KeyEvent.KEYCODE_BUTTON_C)[0]) {
                        LimeLog.info("Detected non-standard DualShock 4 mapping");
                        context.isNonStandardDualShock4 = true;
                    } else {
                        LimeLog.info("Detected DualShock 4 (Linux standard mapping)");
                        context.usesLinuxGamepadStandardFaceButtons = true;
                    }
                }

                if (context.isNonStandardDualShock4) {
                    // The old DS4 driver uses RX and RY for triggers
                    context.leftTriggerAxis = MotionEvent.AXIS_RX;
                    context.rightTriggerAxis = MotionEvent.AXIS_RY;

                    // DS4 has Select and Mode buttons (possibly mapped non-standard)
                    context.hasSelect = true;
                    context.hasMode = true;
                }
                else {
                    // If it's not a non-standard DS4 controller, it's probably an Xbox controller or
                    // other sane controller that uses RX and RY for right stick and Z and RZ for triggers.
                    context.rightStickXAxis = MotionEvent.AXIS_RX;
                    context.rightStickYAxis = MotionEvent.AXIS_RY;

                    // While it's likely that Z and RZ are triggers, we may have digital trigger buttons
                    // instead. We must check that we actually have Z and RZ axes before assigning them.
                    if (getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_Z) != null &&
                            getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RZ) != null) {
                        context.leftTriggerAxis = MotionEvent.AXIS_Z;
                        context.rightTriggerAxis = MotionEvent.AXIS_RZ;
                    }
                }

                // Triggers always idle negative on axes that are centered at zero
                context.triggersIdleNegative = true;
            }
        }

        if (context.rightStickXAxis == -1 && context.rightStickYAxis == -1) {
            InputDevice.MotionRange zRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_Z);
            InputDevice.MotionRange rzRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RZ);

            // Most other controllers use Z and RZ for the right stick
            if (zRange != null && rzRange != null) {
                context.rightStickXAxis = MotionEvent.AXIS_Z;
                context.rightStickYAxis = MotionEvent.AXIS_RZ;
            }
            else {
                InputDevice.MotionRange rxRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RX);
                InputDevice.MotionRange ryRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RY);

                // Try RX and RY now
                if (rxRange != null && ryRange != null) {
                    context.rightStickXAxis = MotionEvent.AXIS_RX;
                    context.rightStickYAxis = MotionEvent.AXIS_RY;
                }
            }
        }

        // Some devices have "hats" for d-pads
        InputDevice.MotionRange hatXRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_HAT_X);
        InputDevice.MotionRange hatYRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_HAT_Y);
        if (hatXRange != null && hatYRange != null) {
            context.hatXAxis = MotionEvent.AXIS_HAT_X;
            context.hatYAxis = MotionEvent.AXIS_HAT_Y;
        }

        if (context.leftTriggerAxis != -1 && context.rightTriggerAxis != -1) {
            InputDevice.MotionRange ltRange = getMotionRangeForJoystickAxis(dev, context.leftTriggerAxis);
            InputDevice.MotionRange rtRange = getMotionRangeForJoystickAxis(dev, context.rightTriggerAxis);

            // It's important to have a valid deadzone so controller packet batching works properly
            context.triggerDeadzone = Math.max(Math.abs(ltRange.getFlat()), Math.abs(rtRange.getFlat()));

            // For triggers without (valid) deadzones, we'll use 13% (around XInput's default)
            if (context.triggerDeadzone < 0.13f ||
                context.triggerDeadzone > 0.30f)
            {
                context.triggerDeadzone = 0.13f;
            }
        }

        // The ADT-1 controller needs a similar fixup to the ASUS Gamepad
        if (dev.getVendorId() == 0x18d1 && dev.getProductId() == 0x2c40) {
            context.backIsStart = true;
            context.modeIsSelect = true;
            context.triggerDeadzone = 0.30f;
            context.hasSelect = true;
            context.hasMode = false;
        }

        if (devName != null) {
            // For the Nexus Player (and probably other ATV devices), we should
            // use the back button as start since it doesn't have a start/menu button
            // on the controller
            if (devName.contains("ASUS Gamepad")) {
                boolean[] hasStartKey = dev.hasKeys(KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU, 0);
                if (!hasStartKey[0] && !hasStartKey[1]) {
                    context.backIsStart = true;
                    context.modeIsSelect = true;
                    context.hasSelect = true;
                    context.hasMode = false;
                }

                // The ASUS Gamepad has triggers that sit far forward and are prone to false presses
                // so we increase the deadzone on them to minimize this
                context.triggerDeadzone = 0.30f;
            }
            // SHIELD controllers will use small stick deadzones
            else if (devName.contains("SHIELD") || devName.contains("NVIDIA Controller")) {
                // The big Nvidia button on the Shield controllers acts like a Search button. It
                // summons the Google Assistant on the Shield TV. On my Pixel 4, it seems to do
                // nothing, so we can hijack it to act like a mode button.
                if (devName.contains("NVIDIA Controller v01.03") || devName.contains("NVIDIA Controller v01.04")) {
                    context.searchIsMode = true;
                    context.hasMode = true;
                }
            }
            // The Serval has a couple of unknown buttons that are start and select. It also has
            // a back button which we want to ignore since there's already a select button.
            else if (devName.contains("Razer Serval")) {
                context.isServal = true;

                // Serval has Select and Mode buttons (possibly mapped non-standard)
                context.hasMode = true;
                context.hasSelect = true;
            }
            // The Xbox One S Bluetooth controller has some mappings that need fixing up.
            // However, Microsoft released a firmware update with no change to VID/PID
            // or device name that fixed the mappings for Android. Since there's
            // no good way to detect this, we'll use the presence of GAS/BRAKE axes
            // that were added in the latest firmware. If those are present, the only
            // required fixup is ignoring the select button.
            else if (devName.equals("Xbox Wireless Controller")) {
                if (gasRange == null) {
                    context.isNonStandardXboxBtController = true;

                    // Xbox One S has Select and Mode buttons (possibly mapped non-standard)
                    context.hasMode = true;
                    context.hasSelect = true;
                }
            }
        }

        // Thrustmaster Score A gamepad home button reports directly to android as
        // KEY_HOMEPAGE event on another event channel
        if (dev.getVendorId() == 0x044f && dev.getProductId() == 0xb328) {
            context.hasMode = false;
        }

        LimeLog.info("Trigger deadzone: "+context.triggerDeadzone);

        return context;
    }

    private InputDeviceContext getContextForEvent(InputEvent event) {
        // Don't return a context if we're stopped
        if (stopped) {
            return null;
        }
        else if (event.getDeviceId() == 0) {
            // Unknown devices use the default context
            return defaultContext;
        }
        else if (event.getDevice() == null) {
            // During device removal, sometimes we can get events after the
            // input device has been destroyed. In this case we'll see a
            // != 0 device ID but no device attached.
            return null;
        }

        // HACK for https://issuetracker.google.com/issues/163120692
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            if (event.getDeviceId() == -1) {
                return defaultContext;
            }
        }

        // Return the existing context if it exists
        InputDeviceContext context = inputDeviceContexts.get(event.getDeviceId());
        if (context != null) {
            return context;
        }

        // Otherwise create a new context
        context = createInputDeviceContextForDevice(event.getDevice());
        inputDeviceContexts.put(event.getDeviceId(), context);

        return context;
    }

    private byte maxByMagnitude(byte a, byte b) {
        int absA = Math.abs(a);
        int absB = Math.abs(b);
        if (absA > absB) {
            return a;
        }
        else {
            return b;
        }
    }

    private short maxByMagnitude(short a, short b) {
        int absA = Math.abs(a);
        int absB = Math.abs(b);
        if (absA > absB) {
            return a;
        }
        else {
            return b;
        }
    }

    private short getActiveControllerMask() {
        if (prefConfig.multiController) {
            return (short)(gamepadPolicy.activeMask() | (prefConfig.onscreenController ? 1 : 0));
        }
        else {
            // Only Player 1 is active with multi-controller disabled
            return 1;
        }
    }

    private static boolean areBatteryCapacitiesEqual(float first, float second) {
        // With no NaNs involved, it is a simple equality comparison.
        if (!Float.isNaN(first) && !Float.isNaN(second)) {
            return first == second;
        }
        else {
            // If we have a NaN in one or both positions, compare NaN-ness instead.
            // Equality comparisons will always return false for NaN.
            return Float.isNaN(first) == Float.isNaN(second);
        }
    }

    private int getBatteryWarningLevel(int percentage) {
        if (prefConfig.gamepadBatteryWarningThreshold <= 0 ||
                percentage > prefConfig.gamepadBatteryWarningThreshold) {
            return 0;
        }

        if (percentage <= 5) {
            return 3;
        }
        else if (percentage <= 10) {
            return 2;
        }
        else {
            return 1;
        }
    }

    private void updateControllerBatteryWarning(InputDeviceContext context,
                                                int batteryStatus,
                                                float batteryCapacity) {
        if (batteryStatus == BatteryState.STATUS_CHARGING ||
                batteryStatus == BatteryState.STATUS_FULL ||
                Float.isNaN(batteryCapacity)) {
            context.lastBatteryWarningLevel = 0;
            return;
        }

        int percentage = Math.max(0, Math.min(100, Math.round(batteryCapacity * 100.f)));
        int warningLevel = getBatteryWarningLevel(percentage);

        // A rising battery level rearms warnings that are no longer active.
        if (warningLevel < context.lastBatteryWarningLevel) {
            context.lastBatteryWarningLevel = warningLevel;
        }

        if (warningLevel > context.lastBatteryWarningLevel) {
            context.lastBatteryWarningLevel = warningLevel;
            final int controllerNumber = context.controllerNumber + 1;
            mainThreadHandler.post(() -> Toast.makeText(activityContext,
                    activityContext.getString(R.string.controller_battery_low,
                            controllerNumber, percentage),
                    Toast.LENGTH_LONG).show());
        }
    }

    private void recordLocalOnlyKey(GenericControllerContext context) {
        localOnlyKeysSinceRecord++;
        long now = android.os.SystemClock.uptimeMillis();
        if (lastLocalOnlyRecordMs != 0 && now - lastLocalOnlyRecordMs < LOCAL_ONLY_RECORD_INTERVAL_MS) {
            return;
        }
        MoonWakerDiagnostics.record("INFO", "android.input", "remote_key.local_only",
                "kind", context.deviceKind.name(), "local_key_count", localOnlyKeysSinceRecord);
        lastLocalOnlyRecordMs = now;
        localOnlyKeysSinceRecord = 0;
    }

    // This must not be called on the main thread due to risk of ANRs!
    private void sendControllerBatteryPacket(InputDeviceContext context) {
        int currentBatteryStatus;
        float currentBatteryCapacity;

        // Use the BatteryState object introduced in Android S, if it's available and present.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context.inputDevice.getBatteryState().isPresent()) {
            currentBatteryStatus = context.inputDevice.getBatteryState().getStatus();
            currentBatteryCapacity = context.inputDevice.getBatteryState().getCapacity();
        }
        else if (sceManager.isRecognizedDevice(context.inputDevice)) {
            // On the SHIELD Android TV, we can use a proprietary API to access battery/charge state.
            // We will convert it to the same form used by BatteryState to share code.
            int batteryPercentage = sceManager.getBatteryPercentage(context.inputDevice);
            if (batteryPercentage < 0) {
                currentBatteryCapacity = Float.NaN;
            }
            else {
                currentBatteryCapacity = batteryPercentage / 100.f;
            }

            SceConnectionType connectionType = sceManager.getConnectionType(context.inputDevice);
            SceChargingState chargingState = sceManager.getChargingState(context.inputDevice);

            // We can make some assumptions about charge state based on the connection type
            if (connectionType == SceConnectionType.WIRED || connectionType == SceConnectionType.BOTH) {
                if (batteryPercentage == 100) {
                    currentBatteryStatus = BatteryState.STATUS_FULL;
                }
                else if (chargingState == SceChargingState.NOT_CHARGING) {
                    currentBatteryStatus = BatteryState.STATUS_NOT_CHARGING;
                }
                else {
                    currentBatteryStatus = BatteryState.STATUS_CHARGING;
                }
            }
            else if (connectionType == SceConnectionType.WIRELESS) {
                if (chargingState == SceChargingState.CHARGING) {
                    currentBatteryStatus = BatteryState.STATUS_CHARGING;
                }
                else {
                    currentBatteryStatus = BatteryState.STATUS_DISCHARGING;
                }
            }
            else {
                // If connection type is unknown, just use the charge state
                if (batteryPercentage == 100) {
                    currentBatteryStatus = BatteryState.STATUS_FULL;
                }
                else if (chargingState == SceChargingState.NOT_CHARGING) {
                    currentBatteryStatus = BatteryState.STATUS_DISCHARGING;
                }
                else if (chargingState == SceChargingState.CHARGING) {
                    currentBatteryStatus = BatteryState.STATUS_CHARGING;
                }
                else {
                    currentBatteryStatus = BatteryState.STATUS_UNKNOWN;
                }
            }
        }
        else {
            return;
        }

        if (context.assignedControllerNumber) {
            updateControllerBatteryWarning(context, currentBatteryStatus, currentBatteryCapacity);
        }

        boolean batteryStateChanged = currentBatteryStatus != context.lastReportedBatteryStatus ||
                !areBatteryCapacitiesEqual(currentBatteryCapacity, context.lastReportedBatteryCapacity);
        if (!context.assignedControllerNumber) {
            context.lastReportedBatteryStatus = currentBatteryStatus;
            context.lastReportedBatteryCapacity = currentBatteryCapacity;
            return;
        }

        if (!context.batteryStateReportedToHost || batteryStateChanged) {
            byte state;
            byte percentage;

            switch (currentBatteryStatus) {
                case BatteryState.STATUS_UNKNOWN:
                    state = MoonBridge.LI_BATTERY_STATE_UNKNOWN;
                    break;

                case BatteryState.STATUS_CHARGING:
                    state = MoonBridge.LI_BATTERY_STATE_CHARGING;
                    break;

                case BatteryState.STATUS_DISCHARGING:
                    state = MoonBridge.LI_BATTERY_STATE_DISCHARGING;
                    break;

                case BatteryState.STATUS_NOT_CHARGING:
                    state = MoonBridge.LI_BATTERY_STATE_NOT_CHARGING;
                    break;

                case BatteryState.STATUS_FULL:
                    state = MoonBridge.LI_BATTERY_STATE_FULL;
                    break;

                default:
                    return;
            }

            if (Float.isNaN(currentBatteryCapacity)) {
                percentage = MoonBridge.LI_BATTERY_PERCENTAGE_UNKNOWN;
            }
            else {
                percentage = (byte)(currentBatteryCapacity * 100);
            }

            conn.sendControllerBatteryEvent((byte)context.controllerNumber, state, percentage);

            context.lastReportedBatteryStatus = currentBatteryStatus;
            context.lastReportedBatteryCapacity = currentBatteryCapacity;
            context.batteryStateReportedToHost = true;
        }
    }

    private void sendControllerInputPacket(GenericControllerContext originalContext) {
        if (inputSuppressed) return;
        if (!isStreamGamepad(originalContext)) {
            // MoonWaker: remote keys stay local (overlay/shortcuts) while a real pad is connected.
            if (originalContext.inputMap != 0) recordLocalOnlyKey(originalContext);
            return;
        }
        assignControllerNumberIfNeeded(originalContext);

        // Take the context's controller number and fuse all inputs with the same number
        short controllerNumber = originalContext.controllerNumber;
        int inputMap = 0;
        byte leftTrigger = 0;
        byte rightTrigger = 0;
        short leftStickX = 0;
        short leftStickY = 0;
        short rightStickX = 0;
        short rightStickY = 0;

        // In order to properly handle controllers that are split into multiple devices,
        // we must aggregate all controllers with the same controller number into a single
        // device before we send it.
        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            GenericControllerContext context = inputDeviceContexts.valueAt(i);
            if (context.assignedControllerNumber &&
                    context.controllerNumber == controllerNumber &&
                    context.mouseEmulationActive == originalContext.mouseEmulationActive) {
                inputMap |= context.inputMap;
                leftTrigger |= maxByMagnitude(leftTrigger, context.leftTrigger);
                rightTrigger |= maxByMagnitude(rightTrigger, context.rightTrigger);
                leftStickX |= maxByMagnitude(leftStickX, context.leftStickX);
                leftStickY |= maxByMagnitude(leftStickY, context.leftStickY);
                rightStickX |= maxByMagnitude(rightStickX, context.rightStickX);
                rightStickY |= maxByMagnitude(rightStickY, context.rightStickY);
            }
        }
        for (int i = 0; i < usbDeviceContexts.size(); i++) {
            GenericControllerContext context = usbDeviceContexts.valueAt(i);
            if (context.assignedControllerNumber &&
                    context.controllerNumber == controllerNumber &&
                    context.mouseEmulationActive == originalContext.mouseEmulationActive) {
                inputMap |= context.inputMap;
                leftTrigger |= maxByMagnitude(leftTrigger, context.leftTrigger);
                rightTrigger |= maxByMagnitude(rightTrigger, context.rightTrigger);
                leftStickX |= maxByMagnitude(leftStickX, context.leftStickX);
                leftStickY |= maxByMagnitude(leftStickY, context.leftStickY);
                rightStickX |= maxByMagnitude(rightStickX, context.rightStickX);
                rightStickY |= maxByMagnitude(rightStickY, context.rightStickY);
            }
        }
        if (defaultContext.controllerNumber == controllerNumber) {
            inputMap |= defaultContext.inputMap;
            leftTrigger |= maxByMagnitude(leftTrigger, defaultContext.leftTrigger);
            rightTrigger |= maxByMagnitude(rightTrigger, defaultContext.rightTrigger);
            leftStickX |= maxByMagnitude(leftStickX, defaultContext.leftStickX);
            leftStickY |= maxByMagnitude(leftStickY, defaultContext.leftStickY);
            rightStickX |= maxByMagnitude(rightStickX, defaultContext.rightStickX);
            rightStickY |= maxByMagnitude(rightStickY, defaultContext.rightStickY);
        }

        if (originalContext.mouseEmulationActive) {
            int changedMask = inputMap ^  originalContext.mouseEmulationLastInputMap;

            boolean aDown = (inputMap & ControllerPacket.A_FLAG) != 0;
            boolean bDown = (inputMap & ControllerPacket.B_FLAG) != 0;

            originalContext.mouseEmulationLastInputMap = inputMap;

            if ((changedMask & ControllerPacket.A_FLAG) != 0) {
                if (aDown) {
                    conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
                }
                else {
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                }
            }
            if ((changedMask & ControllerPacket.B_FLAG) != 0) {
                if (bDown) {
                    conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
                }
                else {
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
                }
            }
            if ((changedMask & ControllerPacket.UP_FLAG) != 0) {
                if ((inputMap & ControllerPacket.UP_FLAG) != 0) {
                    conn.sendMouseScroll((byte) 1);
                }
            }
            if ((changedMask & ControllerPacket.DOWN_FLAG) != 0) {
                if ((inputMap & ControllerPacket.DOWN_FLAG) != 0) {
                    conn.sendMouseScroll((byte) -1);
                }
            }
            if ((changedMask & ControllerPacket.RIGHT_FLAG) != 0) {
                if ((inputMap & ControllerPacket.RIGHT_FLAG) != 0) {
                    conn.sendMouseHScroll((byte) 1);
                }
            }
            if ((changedMask & ControllerPacket.LEFT_FLAG) != 0) {
                if ((inputMap & ControllerPacket.LEFT_FLAG) != 0) {
                    conn.sendMouseHScroll((byte) -1);
                }
            }

            conn.sendControllerInput(controllerNumber, getActiveControllerMask(),
                    (short)0, (byte)0, (byte)0, (short)0, (short)0, (short)0, (short)0);
        }
        else {
            inputMap = gyroAim.onPadState(controllerNumber, inputMap, leftTrigger, rightTrigger,
                    rightStickX, rightStickY);
            gyroRightStick[0] = rightStickX;
            gyroRightStick[1] = rightStickY;
            gyroAim.mixRightStick(controllerNumber, gyroRightStick);
            conn.sendControllerInput(controllerNumber, getActiveControllerMask(),
                    guidePolicy.filter(inputMap),
                    gyroAim.filterTrigger(true, leftTrigger),
                    gyroAim.filterTrigger(false, rightTrigger),
                    leftStickX, leftStickY,
                    gyroRightStick[0], gyroRightStick[1]);
        }

        // Notify listener of input state change (e.g., for virtual controller visual feedback)
        if (controllerInputListener != null) {
            controllerInputListener.onControllerInputChanged(inputMap);
        }
    }

    private final int REMAP_IGNORE = -1;
    private final int REMAP_CONSUME = -2;

    // Return a valid keycode, -2 to consume, or -1 to not consume the event
    // Device MAY BE NULL
    private int handleRemapping(InputDeviceContext context, KeyEvent event) {
        // If we know this gamepad has a share button and receive an unmapped
        // KEY_RECORD event, report that as a share button press.
        if (context.hasShare) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN &&
                    event.getScanCode() == 167) {
                return KeyEvent.KEYCODE_MEDIA_RECORD;
            }
        }

        // The Shield's key layout files map the DualShock 4 clickpad button to
        // BUTTON_SELECT instead of something sane like BUTTON_1 as the standard AOSP
        // mapping does. If we get a button from a Sony device reported as BUTTON_SELECT
        // that matches the keycode used by hid-sony for the clickpad or it's from the
        // separate touchpad input device, remap it to BUTTON_1 to match the current AOSP
        // layout and trigger our touchpad button logic.
        if (context.vendorId == 0x054c &&
                event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_SELECT &&
                (event.getScanCode() == 317 || context.isDualShockStandaloneTouchpad)) {
            return KeyEvent.KEYCODE_BUTTON_1;
        }

        // Override mode button for 8BitDo controllers
        if (context.vendorId == 0x2dc8 && event.getScanCode() == 306) {
            return KeyEvent.KEYCODE_BUTTON_MODE;
        }

        // This mapping was adding in Android 10, then changed based on
        // kernel changes (adding hid-nintendo) in Android 11. If we're
        // on anything newer than Pie, just use the built-in mapping.
        if ((context.vendorId == 0x057e && context.productId == 0x2009 && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) || // Switch Pro controller
                (context.vendorId == 0x0f0d && context.productId == 0x00c1)) { // HORIPAD for Switch
            switch (event.getScanCode()) {
                case 0x130:
                    return KeyEvent.KEYCODE_BUTTON_A;
                case 0x131:
                    return KeyEvent.KEYCODE_BUTTON_B;
                case 0x132:
                    return KeyEvent.KEYCODE_BUTTON_X;
                case 0x133:
                    return KeyEvent.KEYCODE_BUTTON_Y;
                case 0x134:
                    return KeyEvent.KEYCODE_BUTTON_L1;
                case 0x135:
                    return KeyEvent.KEYCODE_BUTTON_R1;
                case 0x136:
                    return KeyEvent.KEYCODE_BUTTON_L2;
                case 0x137:
                    return KeyEvent.KEYCODE_BUTTON_R2;
                case 0x138:
                    return KeyEvent.KEYCODE_BUTTON_SELECT;
                case 0x139:
                    return KeyEvent.KEYCODE_BUTTON_START;
                case 0x13A:
                    return KeyEvent.KEYCODE_BUTTON_THUMBL;
                case 0x13B:
                    return KeyEvent.KEYCODE_BUTTON_THUMBR;
                case 0x13D:
                    return KeyEvent.KEYCODE_BUTTON_MODE;
            }
        }

        if (context.usesLinuxGamepadStandardFaceButtons) {
            // Android's Generic.kl swaps BTN_NORTH and BTN_WEST
            switch (event.getScanCode()) {
                case 304:
                    return KeyEvent.KEYCODE_BUTTON_A;
                case 305:
                    return KeyEvent.KEYCODE_BUTTON_B;
                case 307:
                    return KeyEvent.KEYCODE_BUTTON_Y;
                case 308:
                    return KeyEvent.KEYCODE_BUTTON_X;
            }
        }

        if (context.isNonStandardDualShock4) {
            switch (event.getScanCode()) {
                case 304:
                    return KeyEvent.KEYCODE_BUTTON_X;
                case 305:
                    return KeyEvent.KEYCODE_BUTTON_A;
                case 306:
                    return KeyEvent.KEYCODE_BUTTON_B;
                case 307:
                    return KeyEvent.KEYCODE_BUTTON_Y;
                case 308:
                    return KeyEvent.KEYCODE_BUTTON_L1;
                case 309:
                    return KeyEvent.KEYCODE_BUTTON_R1;
                /*
                **** Using analog triggers instead ****
                case 310:
                    return KeyEvent.KEYCODE_BUTTON_L2;
                case 311:
                    return KeyEvent.KEYCODE_BUTTON_R2;
                */
                case 312:
                    return KeyEvent.KEYCODE_BUTTON_SELECT;
                case 313:
                    return KeyEvent.KEYCODE_BUTTON_START;
                case 314:
                    return KeyEvent.KEYCODE_BUTTON_THUMBL;
                case 315:
                    return KeyEvent.KEYCODE_BUTTON_THUMBR;
                case 316:
                    return KeyEvent.KEYCODE_BUTTON_MODE;
                default:
                    return REMAP_CONSUME;
            }
        }
        // If this is a Serval controller sending an unknown key code, it's probably
        // the start and select buttons
        else if (context.isServal && event.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN) {
            switch (event.getScanCode())  {
                case 314:
                    return KeyEvent.KEYCODE_BUTTON_SELECT;
                case 315:
                    return KeyEvent.KEYCODE_BUTTON_START;
            }
        }
        else if (context.isNonStandardXboxBtController) {
            switch (event.getScanCode()) {
                case 306:
                    return KeyEvent.KEYCODE_BUTTON_X;
                case 307:
                    return KeyEvent.KEYCODE_BUTTON_Y;
                case 308:
                    return KeyEvent.KEYCODE_BUTTON_L1;
                case 309:
                    return KeyEvent.KEYCODE_BUTTON_R1;
                case 310:
                    return KeyEvent.KEYCODE_BUTTON_SELECT;
                case 311:
                    return KeyEvent.KEYCODE_BUTTON_START;
                case 312:
                    return KeyEvent.KEYCODE_BUTTON_THUMBL;
                case 313:
                    return KeyEvent.KEYCODE_BUTTON_THUMBR;
                case 139:
                    return KeyEvent.KEYCODE_BUTTON_MODE;
                default:
                    // Other buttons are mapped correctly
            }

            // The Xbox button is sent as MENU
            if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
                return KeyEvent.KEYCODE_BUTTON_MODE;
            }
        }
        else if (context.vendorId == 0x0b05 && // ASUS
                     (context.productId == 0x7900 || // Kunai - USB
                      context.productId == 0x7902)) // Kunai - Bluetooth
        {
            // ROG Kunai has special M1-M4 buttons that are accessible via the
            // joycon-style detachable controllers that we should map to Start
            // and Select.
            switch (event.getScanCode()) {
                case 264:
                case 266:
                    return KeyEvent.KEYCODE_BUTTON_START;

                case 265:
                case 267:
                    return KeyEvent.KEYCODE_BUTTON_SELECT;
            }
        }

        if (context.hatXAxis == -1 &&
                 context.hatYAxis == -1 &&
                 /* FIXME: There's no good way to know for sure if xpad is bound
                    to this device, so we won't use the name to validate if these
                    scancodes should be mapped to DPAD

                    context.isXboxController &&
                  */
                 event.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN) {
            // If there's not a proper Xbox controller mapping, we'll translate the raw d-pad
            // scan codes into proper key codes
            switch (event.getScanCode())
            {
            case 704:
                return KeyEvent.KEYCODE_DPAD_LEFT;
            case 705:
                return KeyEvent.KEYCODE_DPAD_RIGHT;
            case 706:
                return KeyEvent.KEYCODE_DPAD_UP;
            case 707:
                return KeyEvent.KEYCODE_DPAD_DOWN;
            }
        }

        // Past here we can fixup the keycode and potentially trigger
        // another special case so we need to remember what keycode we're using
        int keyCode = event.getKeyCode();

        // This is a hack for (at least) the "Tablet Remote" app
        // which sends BACK with META_ALT_ON instead of KEYCODE_BUTTON_B
        if (keyCode == KeyEvent.KEYCODE_BACK &&
                !event.hasNoModifiers() &&
                (event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) != 0)
        {
            keyCode = KeyEvent.KEYCODE_BUTTON_B;
        }

        if (keyCode == KeyEvent.KEYCODE_BUTTON_START ||
                keyCode == KeyEvent.KEYCODE_MENU) {
            // Ensure that we never use back as start if we have a real start
            context.backIsStart = false;
        }
        else if (keyCode == KeyEvent.KEYCODE_BUTTON_SELECT) {
            // Don't use mode as select if we have a select
            context.modeIsSelect = false;
        }
        else if (context.backIsStart && keyCode == KeyEvent.KEYCODE_BACK) {
            // Emulate the start button with back
            return KeyEvent.KEYCODE_BUTTON_START;
        }
        else if (context.modeIsSelect && keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            // Emulate the select button with mode
            return KeyEvent.KEYCODE_BUTTON_SELECT;
        }
        else if (context.searchIsMode && keyCode == KeyEvent.KEYCODE_SEARCH) {
            // Emulate the mode button with search
            return KeyEvent.KEYCODE_BUTTON_MODE;
        }

        return keyCode;
    }

    private int handleFlipFaceButtons(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A:
                return KeyEvent.KEYCODE_BUTTON_B;
            case KeyEvent.KEYCODE_BUTTON_B:
                return KeyEvent.KEYCODE_BUTTON_A;
            case KeyEvent.KEYCODE_BUTTON_X:
                return KeyEvent.KEYCODE_BUTTON_Y;
            case KeyEvent.KEYCODE_BUTTON_Y:
                return KeyEvent.KEYCODE_BUTTON_X;
            default:
                return keyCode;
        }
    }

    private Vector2d populateCachedVector(float x, float y) {
        // Reinitialize our cached Vector2d object
        inputVector.initialize(x, y);
        return inputVector;
    }

    private void handleDeadZone(Vector2d stickVector) {
        if (stickVector.getMagnitude() <= stickDeadzone) {
            // Deadzone
            stickVector.initialize(0, 0);
        }

        // We're not normalizing here because we let the computer handle the deadzones.
        // Normalizing can make the deadzones larger than they should be after the computer also
        // evaluates the deadzone.
    }

    private void handleAxisSet(InputDeviceContext context, float lsX, float lsY, float rsX,
                               float rsY, float lt, float rt, float hatX, float hatY) {

        if (context.leftStickXAxis != -1 && context.leftStickYAxis != -1) {
            Vector2d leftStickVector = populateCachedVector(lsX, lsY);

            handleDeadZone(leftStickVector);

            context.leftStickX = (short) (leftStickVector.getX() * 0x7FFE);
            context.leftStickY = (short) (-leftStickVector.getY() * 0x7FFE);
        }

        if (context.rightStickXAxis != -1 && context.rightStickYAxis != -1) {
            Vector2d rightStickVector = populateCachedVector(rsX, rsY);

            handleDeadZone(rightStickVector);

            context.rightStickX = (short) (rightStickVector.getX() * 0x7FFE);
            context.rightStickY = (short) (-rightStickVector.getY() * 0x7FFE);
        }

        if (context.leftTriggerAxis != -1 && context.rightTriggerAxis != -1) {
            // Android sends an initial 0 value for trigger axes even if the trigger
            // should be negative when idle. After the first touch, the axes will go back
            // to normal behavior, so ignore triggersIdleNegative for each trigger until
            // first touch.
            if (lt != 0) {
                context.leftTriggerAxisUsed = true;
            }
            if (rt != 0) {
                context.rightTriggerAxisUsed = true;
            }
            if (context.triggersIdleNegative) {
                if (context.leftTriggerAxisUsed) {
                    lt = (lt + 1) / 2;
                }
                if (context.rightTriggerAxisUsed) {
                    rt = (rt + 1) / 2;
                }
            }

            if (lt <= context.triggerDeadzone) {
                lt = 0;
            }
            if (rt <= context.triggerDeadzone) {
                rt = 0;
            }

            context.leftTrigger = (byte)(lt * 0xFF);
            context.rightTrigger = (byte)(rt * 0xFF);
        }

        if (context.hatXAxis != -1 && context.hatYAxis != -1) {
            context.inputMap &= ~(ControllerPacket.LEFT_FLAG | ControllerPacket.RIGHT_FLAG);
            if (hatX < -0.5) {
                context.inputMap |= ControllerPacket.LEFT_FLAG;
                context.hatXAxisUsed = true;
            }
            else if (hatX > 0.5) {
                context.inputMap |= ControllerPacket.RIGHT_FLAG;
                context.hatXAxisUsed = true;
            }

            context.inputMap &= ~(ControllerPacket.UP_FLAG | ControllerPacket.DOWN_FLAG);
            if (hatY < -0.5) {
                context.inputMap |= ControllerPacket.UP_FLAG;
                context.hatYAxisUsed = true;
            }
            else if (hatY > 0.5) {
                context.inputMap |= ControllerPacket.DOWN_FLAG;
                context.hatYAxisUsed = true;
            }
        }

        sendControllerInputPacket(context);
    }

    // Normalize the given raw float value into a 0.0-1.0f range
    private float normalizeRawValueWithRange(float value, InputDevice.MotionRange range) {
        value = Math.max(value, range.getMin());
        value = Math.min(value, range.getMax());

        value -= range.getMin();

        return value / range.getRange();
    }

    private boolean sendTouchpadEventForPointer(InputDeviceContext context, MotionEvent event, byte touchType, int pointerIndex) {
        if (inputSuppressed) return true;
        float normalizedX = normalizeRawValueWithRange(event.getX(pointerIndex), context.touchpadXRange);
        float normalizedY = normalizeRawValueWithRange(event.getY(pointerIndex), context.touchpadYRange);
        float normalizedPressure = context.touchpadPressureRange != null ?
                normalizeRawValueWithRange(event.getPressure(pointerIndex), context.touchpadPressureRange)
                : 0;

        return conn.sendControllerTouchEvent((byte)context.controllerNumber, touchType,
                event.getPointerId(pointerIndex),
                normalizedX, normalizedY, normalizedPressure) != MoonBridge.LI_ERR_UNSUPPORTED;
    }

    public boolean tryHandleTouchpadEvent(MotionEvent event) {
        if (inputSuppressed) return true;
        // Bail if this is not a touchpad or mouse event
        if (event.getSource() != InputDevice.SOURCE_TOUCHPAD &&
                event.getSource() != InputDevice.SOURCE_MOUSE) {
            return false;
        }

        // Only get a context if one already exists. We want to ensure we don't report non-gamepads.
        InputDeviceContext context = inputDeviceContexts.get(event.getDeviceId());
        if (context == null) {
            return false;
        }

        // When we're working with a mouse source instead of a touchpad, we're quite limited in
        // what useful input we can provide via the controller API. The ABS_X/ABS_Y values are
        // screen coordinates rather than touchpad coordinates. For now, we will just support
        // the clickpad button and nothing else.
        if (event.getSource() == InputDevice.SOURCE_MOUSE) {
            // Unlike the touchpad where down and up refer to individual touches on the touchpad,
            // down and up on a mouse indicates the state of the left mouse button.
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    context.inputMap |= ControllerPacket.TOUCHPAD_FLAG;
                    sendControllerInputPacket(context);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    context.inputMap &= ~ControllerPacket.TOUCHPAD_FLAG;
                    sendControllerInputPacket(context);
                    break;
                default:
                    break;
            }

            return !prefConfig.gamepadTouchpadAsMouse;
        }

        byte touchType;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                touchType = MoonBridge.LI_TOUCH_EVENT_DOWN;
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if ((event.getFlags() & MotionEvent.FLAG_CANCELED) != 0) {
                    touchType = MoonBridge.LI_TOUCH_EVENT_CANCEL;
                }
                else {
                    touchType = MoonBridge.LI_TOUCH_EVENT_UP;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                touchType = MoonBridge.LI_TOUCH_EVENT_MOVE;
                break;

            case MotionEvent.ACTION_CANCEL:
                // ACTION_CANCEL applies to *all* pointers in the gesture, so it maps to CANCEL_ALL
                // rather than CANCEL. For a single pointer cancellation, that's indicated via
                // FLAG_CANCELED on a ACTION_POINTER_UP.
                // https://developer.android.com/develop/ui/views/touch-and-input/gestures/multi
                touchType = MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL;
                break;

            case MotionEvent.ACTION_BUTTON_PRESS:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && event.getActionButton() == MotionEvent.BUTTON_PRIMARY) {
                    context.inputMap |= ControllerPacket.TOUCHPAD_FLAG;
                    sendControllerInputPacket(context);
                    return !prefConfig.gamepadTouchpadAsMouse; // Report as unhandled event to trigger mouse handling
                }
                return false;

            case MotionEvent.ACTION_BUTTON_RELEASE:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && event.getActionButton() == MotionEvent.BUTTON_PRIMARY) {
                    context.inputMap &= ~ControllerPacket.TOUCHPAD_FLAG;
                    sendControllerInputPacket(context);
                    return !prefConfig.gamepadTouchpadAsMouse; // Report as unhandled event to trigger mouse handling
                }
                return false;

            default:
                return false;
        }

        // Bail if the user wants gamepad touchpads to control the mouse
        //
        // NB: We do this after processing ACTION_BUTTON_PRESS and ACTION_BUTTON_RELEASE
        // because we want to still send the touchpad button via the gamepad even when
        // configured to use the touchpad for mouse control.
        if (prefConfig.gamepadTouchpadAsMouse) {
            return false;
        }

        // If we don't have X and Y ranges, we can't process this event
        if (context.touchpadXRange == null || context.touchpadYRange == null) {
            return false;
        }

        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            // Move events may impact all active pointers
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (!sendTouchpadEventForPointer(context, event, touchType, i)) {
                    // Controller touch events are not supported by the host
                    return false;
                }
            }
            return true;
        }
        else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            // Cancel impacts all active pointers
            return conn.sendControllerTouchEvent((byte)context.controllerNumber, MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL,
                    0, 0, 0, 0) != MoonBridge.LI_ERR_UNSUPPORTED;
        }
        else {
            // Down and Up events impact the action index pointer
            return sendTouchpadEventForPointer(context, event, touchType, event.getActionIndex());
        }
    }

    public boolean handleMotionEvent(MotionEvent event) {
        if (inputSuppressed) return true;
        InputDeviceContext context = getContextForEvent(event);
        if (context == null) {
            return true;
        }

        float lsX = 0, lsY = 0, rsX = 0, rsY = 0, rt = 0, lt = 0, hatX = 0, hatY = 0;

        // We purposefully ignore the historical values in the motion event as it makes
        // the controller feel sluggish for some users.

        if (context.leftStickXAxis != -1 && context.leftStickYAxis != -1) {
            lsX = event.getAxisValue(context.leftStickXAxis);
            lsY = event.getAxisValue(context.leftStickYAxis);
        }

        if (context.rightStickXAxis != -1 && context.rightStickYAxis != -1) {
            rsX = event.getAxisValue(context.rightStickXAxis);
            rsY = event.getAxisValue(context.rightStickYAxis);
        }

        if (context.leftTriggerAxis != -1 && context.rightTriggerAxis != -1) {
            lt = event.getAxisValue(context.leftTriggerAxis);
            rt = event.getAxisValue(context.rightTriggerAxis);
        }

        if (context.hatXAxis != -1 && context.hatYAxis != -1) {
            hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
            hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        }

        handleAxisSet(context, lsX, lsY, rsX, rsY, lt, rt, hatX, hatY);

        return true;
    }

    private Vector2d convertRawStickAxisToPixelMovement(short stickX, short stickY) {
        Vector2d vector = new Vector2d();
        vector.initialize(stickX, stickY);
        vector.scalarMultiply(1 / 32766.0f);
        vector.scalarMultiply(4);
        if (vector.getMagnitude() > 0) {
            // Move faster as the stick is pressed further from center
            vector.scalarMultiply(Math.pow(vector.getMagnitude(), 2));
        }
        return vector;
    }

    private void sendEmulatedMouseMove(short x, short y) {
        Vector2d vector = convertRawStickAxisToPixelMovement(x, y);
        if (vector.getMagnitude() >= 1) {
            conn.sendMouseMove((short)vector.getX(), (short)-vector.getY());
        }
    }

    private void sendEmulatedMouseScroll(short x, short y) {
        Vector2d vector = convertRawStickAxisToPixelMovement(x, y);
        if (vector.getMagnitude() >= 1) {
            conn.sendMouseHighResScroll((short)vector.getY());
            conn.sendMouseHighResHScroll((short)vector.getX());
        }
    }

    /**
     * Whether the pad itself can rumble through Android: the same InputDevice vibrators
     * {@link #createInputDeviceContextForDevice} picks first. A pad without them (a DualSense on
     * the TV's Bluetooth stack has no force feedback) cannot play audio haptics either.
     */
    public static boolean canRumble(InputDevice dev) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = dev.getVibratorManager();
            if (hasQuadAmplitudeControlledRumbleVibrators(vm)
                    || hasDualAmplitudeControlledRumbleVibrators(vm)) {
                return true;
            }
        }
        return dev.getVibrator().hasVibrator();
    }

    @TargetApi(31)
    private static boolean hasDualAmplitudeControlledRumbleVibrators(VibratorManager vm) {
        int[] vibratorIds = vm.getVibratorIds();

        // There must be exactly 2 vibrators on this device
        if (vibratorIds.length != 2) {
            return false;
        }

        // Both vibrators must have amplitude control
        for (int vid : vibratorIds) {
            if (!vm.getVibrator(vid).hasAmplitudeControl()) {
                return false;
            }
        }

        return true;
    }

    // This must only be called if hasDualAmplitudeControlledRumbleVibrators() is true!
    @TargetApi(31)
    private void rumbleDualVibrators(VibratorManager vm, short lowFreqMotor, short highFreqMotor) {
        // Normalize motor values to 0-255 amplitudes for VibrationManager
        highFreqMotor = (short)((highFreqMotor >> 8) & 0xFF);
        lowFreqMotor = (short)((lowFreqMotor >> 8) & 0xFF);

        // If they're both zero, we can just call cancel().
        if (lowFreqMotor == 0 && highFreqMotor == 0) {
            vm.cancel();
            return;
        }

        // There's no documentation that states that vibrators for FF_RUMBLE input devices will
        // always be enumerated in this order, but it seems consistent between Xbox Series X (USB),
        // PS3 (USB), and PS4 (USB+BT) controllers on Android 12 Beta 3.
        int[] vibratorIds = vm.getVibratorIds();
        int[] vibratorAmplitudes = new int[] { highFreqMotor, lowFreqMotor };

        CombinedVibration.ParallelCombination combo = CombinedVibration.startParallel();

        for (int i = 0; i < vibratorIds.length; i++) {
            // It's illegal to create a VibrationEffect with an amplitude of 0.
            // Simply excluding that vibrator from our ParallelCombination will turn it off.
            if (vibratorAmplitudes[i] != 0) {
                combo.addVibrator(vibratorIds[i], VibrationEffect.createOneShot(60000, vibratorAmplitudes[i]));
            }
        }

        VibrationAttributes.Builder vibrationAttributes = new VibrationAttributes.Builder();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrationAttributes.setUsage(VibrationAttributes.USAGE_MEDIA);
        }

        vm.vibrate(combo.combine(), vibrationAttributes.build());
    }

    @TargetApi(31)
    private static boolean hasQuadAmplitudeControlledRumbleVibrators(VibratorManager vm) {
        int[] vibratorIds = vm.getVibratorIds();

        // There must be exactly 4 vibrators on this device
        if (vibratorIds.length != 4) {
            return false;
        }

        // All vibrators must have amplitude control
        for (int vid : vibratorIds) {
            if (!vm.getVibrator(vid).hasAmplitudeControl()) {
                return false;
            }
        }

        return true;
    }

    // This must only be called if hasQuadAmplitudeControlledRumbleVibrators() is true!
    @TargetApi(31)
    private void rumbleQuadVibrators(VibratorManager vm, short lowFreqMotor, short highFreqMotor, short leftTrigger, short rightTrigger) {
        // Normalize motor values to 0-255 amplitudes for VibrationManager
        highFreqMotor = (short)((highFreqMotor >> 8) & 0xFF);
        lowFreqMotor = (short)((lowFreqMotor >> 8) & 0xFF);
        leftTrigger = (short)((leftTrigger >> 8) & 0xFF);
        rightTrigger = (short)((rightTrigger >> 8) & 0xFF);

        // If they're all zero, we can just call cancel().
        if (lowFreqMotor == 0 && highFreqMotor == 0 && leftTrigger == 0 && rightTrigger == 0) {
            vm.cancel();
            return;
        }

        // This is a guess based upon the behavior of FF_RUMBLE, but untested due to lack of Linux
        // support for trigger rumble!
        int[] vibratorIds = vm.getVibratorIds();
        int[] vibratorAmplitudes = new int[] { highFreqMotor, lowFreqMotor, leftTrigger, rightTrigger };

        CombinedVibration.ParallelCombination combo = CombinedVibration.startParallel();

        for (int i = 0; i < vibratorIds.length; i++) {
            // It's illegal to create a VibrationEffect with an amplitude of 0.
            // Simply excluding that vibrator from our ParallelCombination will turn it off.
            if (vibratorAmplitudes[i] != 0) {
                combo.addVibrator(vibratorIds[i], VibrationEffect.createOneShot(60000, vibratorAmplitudes[i]));
            }
        }

        VibrationAttributes.Builder vibrationAttributes = new VibrationAttributes.Builder();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrationAttributes.setUsage(VibrationAttributes.USAGE_MEDIA);
        }

        vm.vibrate(combo.combine(), vibrationAttributes.build());
    }

    private void rumbleSingleVibrator(Vibrator vibrator, short lowFreqMotor, short highFreqMotor) {
        // Since we can only use a single amplitude value, compute the desired amplitude
        // by taking 80% of the big motor and 33% of the small motor, then capping to 255.
        // NB: This value is now 0-255 as required by VibrationEffect.
        short lowFreqMotorMSB = (short)((lowFreqMotor >> 8) & 0xFF);
        short highFreqMotorMSB = (short)((highFreqMotor >> 8) & 0xFF);
        int simulatedAmplitude = Math.min(255, (int)((lowFreqMotorMSB * 0.80) + (highFreqMotorMSB * 0.33)));

        if (simulatedAmplitude == 0) {
            // This case is easy - just cancel the current effect and get out.
            // NB: We cannot simply check lowFreqMotor == highFreqMotor == 0
            // because our simulatedAmplitude could be 0 even though our inputs
            // are not (ex: lowFreqMotor == 0 && highFreqMotor == 1).
            vibrator.cancel();
            return;
        }

        // Attempt to use amplitude-based control if we're on Oreo and the device
        // supports amplitude-based vibration control.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (vibrator.hasAmplitudeControl()) {
                VibrationEffect effect = VibrationEffect.createOneShot(60000, simulatedAmplitude);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    VibrationAttributes vibrationAttributes = new VibrationAttributes.Builder()
                            .setUsage(VibrationAttributes.USAGE_MEDIA)
                            .build();
                    vibrator.vibrate(effect, vibrationAttributes);
                }
                else {
                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .build();
                    vibrator.vibrate(effect, audioAttributes);
                }
                return;
            }
        }

        // If we reach this point, we don't have amplitude controls available, so
        // we must emulate it by PWMing the vibration. Ick.
        long pwmPeriod = 20;
        long onTime = (long)((simulatedAmplitude / 255.0) * pwmPeriod);
        long offTime = pwmPeriod - onTime;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            VibrationAttributes vibrationAttributes = new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_MEDIA)
                    .build();
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, onTime, offTime}, 0), vibrationAttributes);
        }
        else {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .build();
            vibrator.vibrate(new long[]{0, onTime, offTime}, 0, audioAttributes);
        }
    }

    /**
     * Audio haptics ({@link AudioHaptics}), from the audio thread: the pads re-rumble with the
     * game's last values on the controller thread, so the audio path never waits on a vibrator.
     */
    public void setAudioLowFreqMotor(int value) {
        audioLowFreqMotor = value;
        backgroundThreadHandler.removeCallbacks(applyAudioRumble);
        backgroundThreadHandler.post(applyAudioRumble);
    }

    private synchronized void reapplyRumble() {
        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            InputDeviceContext deviceContext = inputDeviceContexts.valueAt(i);
            handleRumble(deviceContext.controllerNumber, deviceContext.lowFreqMotor,
                    deviceContext.highFreqMotor);
        }
        for (int i = 0; i < usbDeviceContexts.size(); i++) {
            UsbDeviceContext deviceContext = usbDeviceContexts.valueAt(i);
            handleRumble(deviceContext.controllerNumber, deviceContext.lowFreqMotor,
                    deviceContext.highFreqMotor);
        }
    }

    // Stores the game's values and sends them merged with the audio haptics.
    public synchronized void handleRumble(short controllerNumber, short gameLowFreqMotor, short highFreqMotor) {
        boolean foundMatchingDevice = false;
        boolean vibrated = false;

        if (stopped) {
            return;
        }
        short lowFreqMotor = AudioHaptics.merge(gameLowFreqMotor, audioLowFreqMotor);

        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            InputDeviceContext deviceContext = inputDeviceContexts.valueAt(i);

            if (deviceContext.controllerNumber == controllerNumber) {
                foundMatchingDevice = true;

                deviceContext.lowFreqMotor = gameLowFreqMotor;
                deviceContext.highFreqMotor = highFreqMotor;

                // Prefer the documented Android 12 rumble API which can handle dual vibrators on PS/Xbox controllers
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && deviceContext.vibratorManager != null) {
                    vibrated = true;
                    if (deviceContext.quadVibrators) {
                        rumbleQuadVibrators(deviceContext.vibratorManager,
                                lowFreqMotor, highFreqMotor,
                                deviceContext.leftTriggerMotor, deviceContext.rightTriggerMotor);
                    }
                    else {
                        rumbleDualVibrators(deviceContext.vibratorManager,
                                lowFreqMotor, highFreqMotor);
                    }
                }
                // On Shield devices, we can use their special API to rumble Shield controllers
                else if (sceManager.rumble(deviceContext.inputDevice, lowFreqMotor, highFreqMotor)) {
                    vibrated = true;
                }
                // If all else fails, we have to try the old Vibrator API
                else if (deviceContext.vibrator != null) {
                    vibrated = true;
                    rumbleSingleVibrator(deviceContext.vibrator, lowFreqMotor, highFreqMotor);
                }
            }
        }

        for (int i = 0; i < usbDeviceContexts.size(); i++) {
            UsbDeviceContext deviceContext = usbDeviceContexts.valueAt(i);

            if (deviceContext.controllerNumber == controllerNumber) {
                foundMatchingDevice = vibrated = true;
                deviceContext.lowFreqMotor = gameLowFreqMotor;
                deviceContext.highFreqMotor = highFreqMotor;
                deviceContext.device.rumble(lowFreqMotor, highFreqMotor);
            }
        }

        // We may decide to rumble the device for player 1
        if (controllerNumber == 0) {
            // If we didn't find a matching device, it must be the on-screen
            // controls that triggered the rumble. Vibrate the device if
            // the user has requested that behavior.
            if (!foundMatchingDevice && prefConfig.onscreenController && !prefConfig.onlyL3R3 && prefConfig.vibrateOsc) {
                rumbleSingleVibrator(deviceVibrator, lowFreqMotor, highFreqMotor);
            }
            else if (foundMatchingDevice && !vibrated && prefConfig.vibrateFallbackToDevice) {
                // We found a device to vibrate but it didn't have rumble support. The user
                // has requested us to vibrate the device in this case.

                // We cast the unsigned short value to a signed int before multiplying by
                // the preferred strength. The resulting value is capped at 65534 before
                // we cast it back to a short so it doesn't go above 100%.
                short lowFreqMotorAdjusted = (short)(Math.min((((lowFreqMotor & 0xffff)
                        * prefConfig.vibrateFallbackToDeviceStrength) / 100), Short.MAX_VALUE*2));
                short highFreqMotorAdjusted = (short)(Math.min((((highFreqMotor & 0xffff)
                        * prefConfig.vibrateFallbackToDeviceStrength) / 100), Short.MAX_VALUE*2));

                rumbleSingleVibrator(deviceVibrator, lowFreqMotorAdjusted, highFreqMotorAdjusted);
            }
        }
    }

    public void handleRumbleTriggers(short controllerNumber, short leftTrigger, short rightTrigger) {
        if (stopped) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (int i = 0; i < inputDeviceContexts.size(); i++) {
                InputDeviceContext deviceContext = inputDeviceContexts.valueAt(i);

                if (deviceContext.controllerNumber == controllerNumber) {
                    deviceContext.leftTriggerMotor = leftTrigger;
                    deviceContext.rightTriggerMotor = rightTrigger;

                    if (deviceContext.quadVibrators) {
                        rumbleQuadVibrators(deviceContext.vibratorManager,
                                deviceContext.lowFreqMotor, deviceContext.highFreqMotor,
                                deviceContext.leftTriggerMotor, deviceContext.rightTriggerMotor);
                    }
                }
            }
        }

        for (int i = 0; i < usbDeviceContexts.size(); i++) {
            UsbDeviceContext deviceContext = usbDeviceContexts.valueAt(i);

            if (deviceContext.controllerNumber == controllerNumber) {
                deviceContext.device.rumbleTriggers(leftTrigger, rightTrigger);
            }
        }
    }

    private SensorEventListener createSensorListener(final short controllerNumber, final byte motionType, final boolean needsDeviceOrientationCorrection) {
        final boolean log = Log.isLoggable(GYRO_LOG_TAG, Log.VERBOSE);
        return new SensorEventListener() {
            private float[] lastValues = new float[3];

            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                if (inputSuppressed) return;
                if (log) logMotionSample(motionType == MoonBridge.LI_MOTION_TYPE_GYRO, controllerNumber, sensorEvent);
                // Android will invoke our callback any time we get a new reading,
                // even if the values are the same as last time. Don't report a
                // duplicate set of values to save bandwidth.
                if (sensorEvent.values[0] == lastValues[0] &&
                        sensorEvent.values[1] == lastValues[1] &&
                        sensorEvent.values[2] == lastValues[2]) {
                    return;
                }
                else {
                    lastValues[0] = sensorEvent.values[0];
                    lastValues[1] = sensorEvent.values[1];
                    lastValues[2] = sensorEvent.values[2];
                }

                int x = 0;
                int y = 1;
                int z = 2;
                int xFactor = 1;
                int yFactor = 1;
                int zFactor = 1;

                if (needsDeviceOrientationCorrection) {
                    int deviceRotation = activityContext.getWindowManager().getDefaultDisplay().getRotation();
                    switch (deviceRotation) {
                        case Surface.ROTATION_0:
                        case Surface.ROTATION_180:
                            x = 0;
                            y = 2;
                            z = 1;
                            break;

                        case Surface.ROTATION_90:
                        case Surface.ROTATION_270:
                            x = 1;
                            y = 2;
                            z = 0;
                            break;
                    }

                    switch (deviceRotation) {
                        case Surface.ROTATION_0:
                            zFactor = -1;
                            break;
                        case Surface.ROTATION_90:
                            xFactor = -1;
                            zFactor = -1;
                            break;
                        case Surface.ROTATION_180:
                            xFactor = -1;
                            break;
                        case Surface.ROTATION_270:
                            break;
                    }
                }

                if (motionType == MoonBridge.LI_MOTION_TYPE_GYRO) {
                    // Convert from rad/s to deg/s
                    conn.sendControllerMotionEvent((byte) controllerNumber,
                            motionType,
                            sensorEvent.values[x] * xFactor * 57.2957795f,
                            sensorEvent.values[y] * yFactor * 57.2957795f,
                            sensorEvent.values[z] * zFactor * 57.2957795f);
                }
                else {
                    // Pass m/s^2 directly without conversion
                    conn.sendControllerMotionEvent((byte) controllerNumber,
                            motionType,
                            sensorEvent.values[x] * xFactor,
                            sensorEvent.values[y] * yFactor,
                            sensorEvent.values[z] * zFactor);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };
    }

    // Raw motion samples for the axis/sign/rate measurement, in both host and aim mode. Enable
    // before the stream starts: adb shell setprop log.tag.GyroAim VERBOSE
    private static void logMotionSample(boolean gyro, short controller, SensorEvent e) {
        Log.v(GYRO_LOG_TAG, (gyro ? "gyro" : "accel") + " c=" + controller + " t=" + e.timestamp
                + " v=" + e.values[0] + "," + e.values[1] + "," + e.values[2]);
    }

    // Aim mode: the client is the only gyro consumer; nothing goes to the host.
    private SensorEventListener createGyroAimListener(final InputDeviceContext context, final boolean gyro) {
        final boolean log = Log.isLoggable(GYRO_LOG_TAG, Log.VERBOSE);
        return new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent e) {
                if (inputSuppressed) return;
                short c = context.controllerNumber;
                float[] v = e.values;
                if (log) logMotionSample(gyro, c, e);
                if (!gyro) {
                    gyroAim.onAccel(c, e.timestamp, v[0], v[1], v[2]);
                    return;
                }
                boolean stickChanged = localUiOpen.getAsBoolean() || context.mouseEmulationActive
                        ? gyroAim.pause(c)
                        : gyroAim.onGyro(c, e.timestamp, v[0], v[1], v[2]);
                if (stickChanged) sendControllerInputPacket(context);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };
    }

    public void handleSetMotionEventState(final short controllerNumber, final byte motionType, short reportRateHz) {
        if (stopped) {
            return;
        }

        // Report rate is restricted to <= 200 Hz without the HIGH_SAMPLING_RATE_SENSORS permission
        reportRateHz = (short) Math.min(200, reportRateHz);

        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            InputDeviceContext deviceContext = inputDeviceContexts.valueAt(i);

            if (deviceContext.controllerNumber == controllerNumber) {
                // Store the desired report rate even if we don't have sensors. In some cases,
                // input devices can be reconfigured at runtime which results in a change where
                // sensors disappear and reappear. By storing the desired report rate, we can
                // reapply the desired motion sensor configuration after they reappear.
                switch (motionType) {
                    case MoonBridge.LI_MOTION_TYPE_ACCEL:
                        deviceContext.accelReportRateHz = reportRateHz;
                        break;
                    case MoonBridge.LI_MOTION_TYPE_GYRO:
                        deviceContext.gyroReportRateHz = reportRateHz;
                        break;
                }
                // Aim mode keeps its own registration; the host request is only recorded.
                if (!gyroAim.forwardsMotionToHost()) break;

                backgroundThreadHandler.removeCallbacks(deviceContext.enableSensorRunnable);

                SensorManager sm = deviceContext.sensorManager;
                if (sm == null) {
                    continue;
                }

                switch (motionType) {
                    case MoonBridge.LI_MOTION_TYPE_ACCEL:
                        if (deviceContext.accelListener != null) {
                            sm.unregisterListener(deviceContext.accelListener);
                            deviceContext.accelListener = null;
                        }

                        // Enable the accelerometer if requested
                        Sensor accelSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                        if (reportRateHz != 0 && accelSensor != null) {
                            deviceContext.accelListener = createSensorListener(controllerNumber, motionType, sm == deviceSensorManager);
                            sm.registerListener(deviceContext.accelListener, accelSensor, 1000000 / reportRateHz);
                        }
                        break;
                    case MoonBridge.LI_MOTION_TYPE_GYRO:
                        if (deviceContext.gyroListener != null) {
                            sm.unregisterListener(deviceContext.gyroListener);
                            deviceContext.gyroListener = null;
                        }

                        // Enable the gyroscope if requested
                        Sensor gyroSensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                        if (reportRateHz != 0 && gyroSensor != null) {
                            deviceContext.gyroListener = createSensorListener(controllerNumber, motionType, sm == deviceSensorManager);
                            sm.registerListener(deviceContext.gyroListener, gyroSensor, 1000000 / reportRateHz);
                        }
                        break;
                }
                break;
            }
        }
    }

    public void handleSetControllerLED(short controllerNumber, byte r, byte g, byte b) {
        if (stopped) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (int i = 0; i < inputDeviceContexts.size(); i++) {
                InputDeviceContext deviceContext = inputDeviceContexts.valueAt(i);

                // Ignore input devices without an RGB LED
                if (deviceContext.controllerNumber == controllerNumber && deviceContext.hasRgbLed) {
                    // Convert the RGB components into the integer value that LightState uses
                    deviceContext.ledArgbValue = 0xFF000000 | ((r << 16) & 0xFF0000) | ((g << 8) & 0xFF00) | (b & 0xFF);

                    backgroundThreadHandler.removeCallbacks(deviceContext.setLedStateRunnable);
                    backgroundThreadHandler.post(deviceContext.setLedStateRunnable);
                }
            }
        }
    }

    private static int triggerFlag(String trigger) {
        if ("start".equals(trigger)) return ControllerPacket.PLAY_FLAG;
        if ("lb_rb".equals(trigger)) return ControllerPacket.LB_FLAG | ControllerPacket.RB_FLAG;
        if ("select".equals(trigger)) return ControllerPacket.BACK_FLAG;
        return 0;
    }

    private boolean isLocalTrigger(int flag) {
        return (overlayTriggerButtonFlag & flag) != 0;
    }

    private void openStreamMenu() {
        if (overlayMenuListener == null) return;
        releaseAllControllerInputs();
        MoonWakerDiagnostics.record("INFO", "android.input", "overlay.opened",
                "source", overlayHoldSource);
        overlayMenuListener.onOverlayMenuOpen();
    }

    /** Starts the hold when every button of the configured pad trigger is down. */
    private void checkOverlayTrigger(InputDeviceContext context, int buttonFlag) {
        if ((buttonFlag & overlayTriggerButtonFlag) != 0
                && (context.inputMap & overlayTriggerButtonFlag) == overlayTriggerButtonFlag) {
            overlayHoldSource = StreamGamepadPolicy.overlaySource(context.deviceKind);
            holdContext = context;
            menuButtons.press(StreamMenuButtons.Press.TRIGGER);
        }
    }

    /**
     * "Menu and screenshots" takes the Create button of a PlayStation pad (DualSense,
     * DualShock 4: Sony); every other pad's Select/View stays the game's and the hold trigger's.
     */
    private boolean localCreate(InputDeviceContext context) {
        return prefConfig.createButtonMenu && context.vendorId == 0x054c;
    }

    /**
     * A trigger still pending its hold (Start down for the menu) when Create goes down becomes
     * the host's held button, as with any chord: its release then reaches the host.
     */
    private void flushPendingTrigger() {
        InputDeviceContext held = holdContext;
        if (held == null || held.pendingOverlayTriggerPressFlag == 0 || !menuButtons.cancel()) {
            return;
        }
        held.inputMap |= held.pendingOverlayTriggerPressFlag;
        held.pendingOverlayTriggerPressFlag = 0;
        sendControllerInputPacket(held);
    }

    /** Releasing any button of the configured trigger ends its hold. */
    private boolean checkOverlayTriggerRelease(int buttonFlag) {
        return isLocalTrigger(buttonFlag) && menuButtons.release(StreamMenuButtons.Press.TRIGGER);
    }

    /**
     * Whether this stream can save screenshots and clips (a MoonWaker host with the capability,
     * not refused for want of Host Plus). Without, Xbox Share reaches the host as before.
     */
    public void setCapturesAvailable(boolean available) {
        capturesAvailable = available;
        if (!available) menuButtons.cancelShare();
    }

    /** Stream Back (remote or pad), never sent to the host: a tap is Back, a hold opens the menu. */
    public void handleBackDown(KeyEvent event) {
        if (event.getRepeatCount() != 0) return;
        overlayHoldSource = "remote_key";
        holdContext = null;
        menuButtons.press(StreamMenuButtons.Press.BACK);
    }

    public void handleBackUp() {
        menuButtons.release(StreamMenuButtons.Press.BACK);
    }

    /**
     * A trigger tapped before the hold time still reaches the host: the natural packet sends the
     * press and the release follows on the next loop iteration so the host sees both.
     */
    private void releaseTriggerButton(InputDeviceContext context, int flag) {
        boolean pendingHere = context.pendingOverlayTriggerPressFlag == flag;
        if (pendingHere) context.pendingOverlayTriggerPressFlag = 0;
        else context.inputMap &= ~flag;
        if (checkOverlayTriggerRelease(flag) && pendingHere) {
            context.inputMap |= flag;
            mainThreadHandler.postDelayed(() -> {
                context.inputMap &= ~flag;
                sendControllerInputPacket(context);
            }, OVERLAY_TRIGGER_TAP_RELEASE_DELAY_MS);
        }
    }

    public boolean handleButtonUp(KeyEvent event) {
        InputDeviceContext context = getContextForEvent(event);
        if (context == null) {
            return true;
        }

        int keyCode = handleRemapping(context, event);
        if (keyCode < 0) {
            return (keyCode == REMAP_CONSUME);
        }

        if (prefConfig.flipFaceButtons) {
            keyCode = handleFlipFaceButtons(keyCode);
        }

        // REMOVED: Thread.sleep() workaround for OUYA virtual menu button (OUYA discontinued 2015)
        // This was adding 0-25ms of latency to button releases on modern controllers.
        // If needed for specific legacy devices, this can be re-enabled with a device check.
        //
        // Original code:
        // int buttonDownTime = (int)(event.getEventTime() - event.getDownTime());
        // if (buttonDownTime < MINIMUM_BUTTON_DOWN_TIME_MS) {
        //     Thread.sleep(MINIMUM_BUTTON_DOWN_TIME_MS - buttonDownTime);
        // }

        switch (keyCode) {
        case KeyEvent.KEYCODE_BUTTON_MODE:
            context.pendingOverlayTriggerPressFlag = 0;
            // Lifts a held or chorded Guide on the host; a tap opens the stream menu instead.
            context.inputMap &= ~ControllerPacket.SPECIAL_BUTTON_FLAG;
            menuButtons.release(StreamMenuButtons.Press.GUIDE);
            break;
        case KeyEvent.KEYCODE_BUTTON_START:
        case KeyEvent.KEYCODE_MENU:
            releaseTriggerButton(context, ControllerPacket.PLAY_FLAG);
            break;
        case KeyEvent.KEYCODE_BUTTON_SELECT:
            if (localCreate(context)) menuButtons.release(StreamMenuButtons.Press.CREATE);
            else releaseTriggerButton(context, ControllerPacket.BACK_FLAG);
            break;
        case KeyEvent.KEYCODE_DPAD_LEFT:
            if (context.hatXAxisUsed) {
                // Suppress this duplicate event if we have a hat
                return true;
            }
            context.inputMap &= ~ControllerPacket.LEFT_FLAG;
            break;
        case KeyEvent.KEYCODE_DPAD_RIGHT:
            if (context.hatXAxisUsed) {
                // Suppress this duplicate event if we have a hat
                return true;
            }
            context.inputMap &= ~ControllerPacket.RIGHT_FLAG;
            break;
        case KeyEvent.KEYCODE_DPAD_UP:
            if (context.hatYAxisUsed) {
                // Suppress this duplicate event if we have a hat
                return true;
            }
            context.inputMap &= ~ControllerPacket.UP_FLAG;
            break;
        case KeyEvent.KEYCODE_DPAD_DOWN:
            if (context.hatYAxisUsed) {
                // Suppress this duplicate event if we have a hat
                return true;
            }
            context.inputMap &= ~ControllerPacket.DOWN_FLAG;
            break;
        case KeyEvent.KEYCODE_DPAD_UP_LEFT:
            if (context.hatXAxisUsed && context.hatYAxisUsed) {
                // Suppress this duplicate event if we have a hat
                return true;
            }
            context.inputMap &= ~(ControllerPacket.UP_FLAG | ControllerPacket.LEFT_FLAG);
            break;
        case KeyEvent.KEYCODE_DPAD_UP_RIGHT:
            if (context.hatXAxisUsed && context.hatYAxisUsed) {
                // Suppress this duplicate event if we have a hat
                return true;
            }
            context.inputMap &= ~(ControllerPacket.UP_FLAG | ControllerPacket.RIGHT_FLAG);
            break;
        case KeyEvent.KEYCODE_DPAD_DOWN_LEFT:
            if (context.hatXAxisUsed && context.hatYAxisUsed) {
                // Suppress this duplicate event if we have a hat
                return true;
            }
            context.inputMap &= ~(ControllerPacket.DOWN_FLAG | ControllerPacket.LEFT_FLAG);
            break;
        case KeyEvent.KEYCODE_DPAD_DOWN_RIGHT:
            if (context.hatXAxisUsed && context.hatYAxisUsed) {
                // Suppress this duplicate event if we have a hat
                return true;
            }
            context.inputMap &= ~(ControllerPacket.DOWN_FLAG | ControllerPacket.RIGHT_FLAG);
            break;
        case KeyEvent.KEYCODE_BUTTON_B:
            context.inputMap &= ~ControllerPacket.B_FLAG;
            break;
        case KeyEvent.KEYCODE_DPAD_CENTER:
        case KeyEvent.KEYCODE_BUTTON_A:
            context.inputMap &= ~ControllerPacket.A_FLAG;
            break;
        case KeyEvent.KEYCODE_BUTTON_X:
            context.inputMap &= ~ControllerPacket.X_FLAG;
            break;
        case KeyEvent.KEYCODE_BUTTON_Y:
            context.inputMap &= ~ControllerPacket.Y_FLAG;
            break;
        case KeyEvent.KEYCODE_BUTTON_L1:
            context.inputMap &= ~ControllerPacket.LB_FLAG;
            context.lastLbUpTime = event.getEventTime();
            checkOverlayTriggerRelease(ControllerPacket.LB_FLAG);
            break;
        case KeyEvent.KEYCODE_BUTTON_R1:
            context.inputMap &= ~ControllerPacket.RB_FLAG;
            context.lastRbUpTime = event.getEventTime();
            checkOverlayTriggerRelease(ControllerPacket.RB_FLAG);
            break;
        case KeyEvent.KEYCODE_BUTTON_THUMBL:
            context.inputMap &= ~ControllerPacket.LS_CLK_FLAG;
            break;
        case KeyEvent.KEYCODE_BUTTON_THUMBR:
            context.inputMap &= ~ControllerPacket.RS_CLK_FLAG;
            break;
        case KeyEvent.KEYCODE_MEDIA_RECORD: // Xbox Series X Share: captures when they work
            if (capturesAvailable) {
                menuButtons.releaseShare();
                return true;
            }
            context.inputMap &= ~ControllerPacket.MISC_FLAG;
            break;
        case KeyEvent.KEYCODE_BUTTON_1: // PS4/PS5 touchpad button (prior to 4.10)
            context.inputMap &= ~ControllerPacket.TOUCHPAD_FLAG;
            break;
        case KeyEvent.KEYCODE_BUTTON_L2:
            if (context.leftTriggerAxisUsed) {
                // Suppress this digital event if an analog trigger is active
                return true;
            }
            context.leftTrigger = 0;
            break;
        case KeyEvent.KEYCODE_BUTTON_R2:
            if (context.rightTriggerAxisUsed) {
                // Suppress this digital event if an analog trigger is active
                return true;
            }
            context.rightTrigger = 0;
            break;
        case KeyEvent.KEYCODE_UNKNOWN:
            // Paddles aren't mapped in any of the Android key layout files,
            // so we need to handle the evdev key codes directly.
            if (context.hasPaddles) {
                switch (event.getScanCode()) {
                    case 0x2c4: // BTN_TRIGGER_HAPPY5
                        context.inputMap &= ~ControllerPacket.PADDLE1_FLAG;
                        break;
                    case 0x2c5: // BTN_TRIGGER_HAPPY6
                        context.inputMap &= ~ControllerPacket.PADDLE2_FLAG;
                        break;
                    case 0x2c6: // BTN_TRIGGER_HAPPY7
                        context.inputMap &= ~ControllerPacket.PADDLE3_FLAG;
                        break;
                    case 0x2c7: // BTN_TRIGGER_HAPPY8
                        context.inputMap &= ~ControllerPacket.PADDLE4_FLAG;
                        break;
                    default:
                        return false;
                }
            }
            else {
                return false;
            }
            break;
        default:
            return false;
        }

        // Check if we're emulating the select button
        if ((context.emulatingButtonFlags & ControllerHandler.EMULATING_SELECT) != 0)
        {
            // If either start or LB is up, select comes up too
            if ((context.inputMap & ControllerPacket.PLAY_FLAG) == 0 ||
                (context.inputMap & ControllerPacket.LB_FLAG) == 0)
            {
                context.inputMap &= ~ControllerPacket.BACK_FLAG;

                context.emulatingButtonFlags &= ~ControllerHandler.EMULATING_SELECT;
            }
        }

        // Check if we're emulating the special button
        if ((context.emulatingButtonFlags & ControllerHandler.EMULATING_SPECIAL) != 0)
        {
            // If either start or select and RB is up, the special button comes up too
            if ((context.inputMap & ControllerPacket.PLAY_FLAG) == 0 ||
                ((context.inputMap & ControllerPacket.BACK_FLAG) == 0 &&
                 (context.inputMap & ControllerPacket.RB_FLAG) == 0))
            {
                context.inputMap &= ~ControllerPacket.SPECIAL_BUTTON_FLAG;

                context.emulatingButtonFlags &= ~ControllerHandler.EMULATING_SPECIAL;
            }
        }

        // Check if we're emulating the touchpad button
        if ((context.emulatingButtonFlags & ControllerHandler.EMULATING_TOUCHPAD) != 0)
        {
            // If either select or LB is up, touchpad comes up too
            if ((context.inputMap & ControllerPacket.BACK_FLAG) == 0 ||
                    (context.inputMap & ControllerPacket.LB_FLAG) == 0)
            {
                context.inputMap &= ~ControllerPacket.TOUCHPAD_FLAG;

                context.emulatingButtonFlags &= ~ControllerHandler.EMULATING_TOUCHPAD;
            }
        }

        sendControllerInputPacket(context);

        if (context.pendingExit && context.inputMap == 0) {
            // All buttons from the quit combo are lifted. Finish the activity now.
            activityContext.finish();
        }

        return true;
    }

    /** A configured trigger button is held back from the host until its tap or hold is known. */
    private boolean pressTriggerButton(InputDeviceContext context, int flag, KeyEvent event) {
        boolean first = event.getRepeatCount() == 0;
        if (!isLocalTrigger(flag)) {
            context.inputMap |= flag;
            if (first) checkOverlayTrigger(context, flag);
            return false;
        }
        if (first) {
            // Set the flag only for checkOverlayTrigger's all-buttons-down test.
            context.inputMap |= flag;
            checkOverlayTrigger(context, flag);
            context.inputMap &= ~flag;
            context.pendingOverlayTriggerPressFlag = flag;
        }
        return true;
    }

    public boolean handleButtonDown(KeyEvent event) {
        InputDeviceContext context = getContextForEvent(event);
        if (context == null) {
            return true;
        }

        int keyCode = handleRemapping(context, event);
        if (keyCode < 0) {
            return (keyCode == REMAP_CONSUME);
        }

        if (prefConfig.flipFaceButtons) {
            keyCode = handleFlipFaceButtons(keyCode);
        }

        boolean isPendingTriggerButton = false;

        switch (keyCode) {
        case KeyEvent.KEYCODE_BUTTON_MODE:
            context.hasMode = true;
            // Guide stays local until the outcome is known: a tap opens the stream menu.
            isPendingTriggerButton = true;
            if (event.getRepeatCount() == 0) {
                overlayHoldSource = StreamGamepadPolicy.overlaySource(context.deviceKind);
                holdContext = context;
                context.pendingOverlayTriggerPressFlag = ControllerPacket.SPECIAL_BUTTON_FLAG;
                menuButtons.press(StreamMenuButtons.Press.GUIDE);
            }
            break;
        case KeyEvent.KEYCODE_BUTTON_START:
        case KeyEvent.KEYCODE_MENU:
            if (event.getRepeatCount() == 0) context.startDownTime = event.getEventTime();
            isPendingTriggerButton = pressTriggerButton(context, ControllerPacket.PLAY_FLAG, event);
            break;
        case KeyEvent.KEYCODE_BUTTON_SELECT:
            context.hasSelect = true;
            if (!localCreate(context)) {
                isPendingTriggerButton = pressTriggerButton(context, ControllerPacket.BACK_FLAG,
                        event);
                break;
            }
            // PlayStation Create is the stream's own: tap the menu, hold a screenshot.
            isPendingTriggerButton = true;
            if (event.getRepeatCount() == 0) {
                flushPendingTrigger();
                overlayHoldSource = StreamGamepadPolicy.overlaySource(context.deviceKind);
                holdContext = context;
                menuButtons.press(StreamMenuButtons.Press.CREATE);
            }
            break;
        case KeyEvent.KEYCODE_DPAD_LEFT:
            if (context.hatXAxisUsed) {
                // Suppress this duplicate event if we have a hat
                return true;
            }
            context.inputMap |= ControllerPacket.LEFT_FLAG;
            break;
        case KeyEvent.KEYCODE_DPAD_RIGHT:
            if (context.hatXAxisUsed) {
                // Suppress this duplicate event if we have a hat
                return true;
            }
            context.inputMap |= ControllerPacket.RIGHT_FLAG;
            break;
        case KeyEvent.KEYCODE_DPAD_UP:
            if (context.hatYAxisUsed) {
                // Suppress this duplicate event if we have a hat
                return true;
            }
            context.inputMap |= ControllerPacket.UP_FLAG;
            break;
        case KeyEvent.KEYCODE_DPAD_DOWN:
            if (context.hatYAxisUsed) {
                // Suppress this duplicate event if we have a hat
                return true;
            }
            context.inputMap |= ControllerPacket.DOWN_FLAG;
            break;
        case KeyEvent.KEYCODE_DPAD_UP_LEFT:
            if (context.hatXAxisUsed && context.hatYAxisUsed) {
                // Suppress this duplicate event if we have a hat
                return true;
            }
            context.inputMap |= ControllerPacket.UP_FLAG | ControllerPacket.LEFT_FLAG;
            break;
        case KeyEvent.KEYCODE_DPAD_UP_RIGHT:
            if (context.hatXAxisUsed && context.hatYAxisUsed) {
                // Suppress this duplicate event if we have a hat
                return true;
            }
            context.inputMap |= ControllerPacket.UP_FLAG | ControllerPacket.RIGHT_FLAG;
            break;
        case KeyEvent.KEYCODE_DPAD_DOWN_LEFT:
            if (context.hatXAxisUsed && context.hatYAxisUsed) {
                // Suppress this duplicate event if we have a hat
                return true;
            }
            context.inputMap |= ControllerPacket.DOWN_FLAG | ControllerPacket.LEFT_FLAG;
            break;
        case KeyEvent.KEYCODE_DPAD_DOWN_RIGHT:
            if (context.hatXAxisUsed && context.hatYAxisUsed) {
                // Suppress this duplicate event if we have a hat
                return true;
            }
            context.inputMap |= ControllerPacket.DOWN_FLAG | ControllerPacket.RIGHT_FLAG;
            break;
        case KeyEvent.KEYCODE_BUTTON_B:
            context.inputMap |= ControllerPacket.B_FLAG;
            break;
        case KeyEvent.KEYCODE_DPAD_CENTER:
        case KeyEvent.KEYCODE_BUTTON_A:
            context.inputMap |= ControllerPacket.A_FLAG;
            break;
        case KeyEvent.KEYCODE_BUTTON_X:
            context.inputMap |= ControllerPacket.X_FLAG;
            break;
        case KeyEvent.KEYCODE_BUTTON_Y:
            context.inputMap |= ControllerPacket.Y_FLAG;
            break;
        case KeyEvent.KEYCODE_BUTTON_L1:
            context.inputMap |= ControllerPacket.LB_FLAG;
            if (event.getRepeatCount() == 0) {
                checkOverlayTrigger(context, ControllerPacket.LB_FLAG);
            }
            break;
        case KeyEvent.KEYCODE_BUTTON_R1:
            context.inputMap |= ControllerPacket.RB_FLAG;
            if (event.getRepeatCount() == 0) {
                checkOverlayTrigger(context, ControllerPacket.RB_FLAG);
            }
            break;
        case KeyEvent.KEYCODE_BUTTON_THUMBL:
            context.inputMap |= ControllerPacket.LS_CLK_FLAG;
            break;
        case KeyEvent.KEYCODE_BUTTON_THUMBR:
            context.inputMap |= ControllerPacket.RS_CLK_FLAG;
            break;
        case KeyEvent.KEYCODE_MEDIA_RECORD: // Xbox Series X Share: captures when they work
            if (!capturesAvailable) {
                context.inputMap |= ControllerPacket.MISC_FLAG;
            } else if (event.getRepeatCount() == 0) {
                // Never the host's; a trigger pending on this pad reaches it, as with any button.
                shareContext = context;
                menuButtons.pressShare();
            }
            break;
        case KeyEvent.KEYCODE_BUTTON_1: // PS4/PS5 touchpad button (prior to 4.10)
            context.inputMap |= ControllerPacket.TOUCHPAD_FLAG;
            break;
        case KeyEvent.KEYCODE_BUTTON_L2:
            if (context.leftTriggerAxisUsed) {
                // Suppress this digital event if an analog trigger is active
                return true;
            }
            context.leftTrigger = (byte)0xFF;
            break;
        case KeyEvent.KEYCODE_BUTTON_R2:
            if (context.rightTriggerAxisUsed) {
                // Suppress this digital event if an analog trigger is active
                return true;
            }
            context.rightTrigger = (byte)0xFF;
            break;
        case KeyEvent.KEYCODE_UNKNOWN:
            // Paddles aren't mapped in any of the Android key layout files,
            // so we need to handle the evdev key codes directly.
            if (context.hasPaddles) {
                switch (event.getScanCode()) {
                    case 0x2c4: // BTN_TRIGGER_HAPPY5
                        context.inputMap |= ControllerPacket.PADDLE1_FLAG;
                        break;
                    case 0x2c5: // BTN_TRIGGER_HAPPY6
                        context.inputMap |= ControllerPacket.PADDLE2_FLAG;
                        break;
                    case 0x2c6: // BTN_TRIGGER_HAPPY7
                        context.inputMap |= ControllerPacket.PADDLE3_FLAG;
                        break;
                    case 0x2c7: // BTN_TRIGGER_HAPPY8
                        context.inputMap |= ControllerPacket.PADDLE4_FLAG;
                        break;
                    default:
                        return false;
                }
            }
            else {
                return false;
            }
            break;
        default:
            return false;
        }

        // Start+Back+LB+RB is the quit combo
        if (context.inputMap == (ControllerPacket.BACK_FLAG | ControllerPacket.PLAY_FLAG |
                                 ControllerPacket.LB_FLAG | ControllerPacket.RB_FLAG)) {
            // Wait for the combo to lift and then finish the activity
            context.pendingExit = true;
        }

        // Start+LB acts like select for controllers with one button
        if (!context.hasSelect) {
            if (context.inputMap == (ControllerPacket.PLAY_FLAG | ControllerPacket.LB_FLAG) ||
                    (context.inputMap == ControllerPacket.PLAY_FLAG &&
                            event.getEventTime() - context.lastLbUpTime <= MAXIMUM_BUMPER_UP_DELAY_MS))
            {
                context.inputMap &= ~(ControllerPacket.PLAY_FLAG | ControllerPacket.LB_FLAG);
                context.inputMap |= ControllerPacket.BACK_FLAG;

                context.emulatingButtonFlags |= ControllerHandler.EMULATING_SELECT;
            }
        }
        else if (context.needsClickpadEmulation) {
            // Select+LB acts like the clickpad when we're faking a PS4 controller for motion support
            if (context.inputMap == (ControllerPacket.BACK_FLAG | ControllerPacket.LB_FLAG) ||
                    (context.inputMap == ControllerPacket.BACK_FLAG &&
                            event.getEventTime() - context.lastLbUpTime <= MAXIMUM_BUMPER_UP_DELAY_MS))
            {
                context.inputMap &= ~(ControllerPacket.BACK_FLAG | ControllerPacket.LB_FLAG);
                context.inputMap |= ControllerPacket.TOUCHPAD_FLAG;

                context.emulatingButtonFlags |= ControllerHandler.EMULATING_TOUCHPAD;
            }
        }

        // If there is a physical select button, we'll use Start+Select as the special button combo
        // otherwise we'll use Start+RB.
        if (!context.hasMode) {
            if (context.hasSelect) {
                if (context.inputMap == (ControllerPacket.PLAY_FLAG | ControllerPacket.BACK_FLAG)) {
                    context.inputMap &= ~(ControllerPacket.PLAY_FLAG | ControllerPacket.BACK_FLAG);
                    context.inputMap |= ControllerPacket.SPECIAL_BUTTON_FLAG;

                    context.emulatingButtonFlags |= ControllerHandler.EMULATING_SPECIAL;
                }
            }
            else {
                if (context.inputMap == (ControllerPacket.PLAY_FLAG | ControllerPacket.RB_FLAG) ||
                        (context.inputMap == ControllerPacket.PLAY_FLAG &&
                                event.getEventTime() - context.lastRbUpTime <= MAXIMUM_BUMPER_UP_DELAY_MS))
                {
                    context.inputMap &= ~(ControllerPacket.PLAY_FLAG | ControllerPacket.RB_FLAG);
                    context.inputMap |= ControllerPacket.SPECIAL_BUTTON_FLAG;

                    context.emulatingButtonFlags |= ControllerHandler.EMULATING_SPECIAL;
                }
            }
        }

        // If another button was pressed while the overlay trigger was pending,
        // immediately send the trigger + new button combo and cancel the overlay timer.
        if (!isPendingTriggerButton && context.pendingOverlayTriggerPressFlag != 0
                && menuButtons.cancel()) {
            context.inputMap |= context.pendingOverlayTriggerPressFlag;
            context.pendingOverlayTriggerPressFlag = 0;
        }

        // We don't need to send repeat key down events, but the platform
        // sends us events that claim to be repeats but they're from different
        // devices, so we just send them all and deal with some duplicates.
        sendControllerInputPacket(context);
        return true;
    }

    public void reportOscState(int buttonFlags,
                               short leftStickX, short leftStickY,
                               short rightStickX, short rightStickY,
                               byte leftTrigger, byte rightTrigger) {
        defaultContext.leftStickX = leftStickX;
        defaultContext.leftStickY = leftStickY;

        defaultContext.rightStickX = rightStickX;
        defaultContext.rightStickY = rightStickY;

        defaultContext.leftTrigger = leftTrigger;
        defaultContext.rightTrigger = rightTrigger;

        defaultContext.inputMap = buttonFlags;

        sendControllerInputPacket(defaultContext);
    }

    @Override
    public void reportControllerState(int controllerId, int buttonFlags,
                                      float leftStickX, float leftStickY,
                                      float rightStickX, float rightStickY,
                                      float leftTrigger, float rightTrigger) {
        GenericControllerContext context = usbDeviceContexts.get(controllerId);
        if (context == null) {
            return;
        }

        Vector2d leftStickVector = populateCachedVector(leftStickX, leftStickY);

        handleDeadZone(leftStickVector);

        context.leftStickX = (short) (leftStickVector.getX() * 0x7FFE);
        context.leftStickY = (short) (-leftStickVector.getY() * 0x7FFE);

        Vector2d rightStickVector = populateCachedVector(rightStickX, rightStickY);

        handleDeadZone(rightStickVector);

        context.rightStickX = (short) (rightStickVector.getX() * 0x7FFE);
        context.rightStickY = (short) (-rightStickVector.getY() * 0x7FFE);

        if (leftTrigger <= context.triggerDeadzone) {
            leftTrigger = 0;
        }
        if (rightTrigger <= context.triggerDeadzone) {
            rightTrigger = 0;
        }

        context.leftTrigger = (byte)(leftTrigger * 0xFF);
        context.rightTrigger = (byte)(rightTrigger * 0xFF);

        context.inputMap = buttonFlags;

        sendControllerInputPacket(context);
    }

    @Override
    public void deviceRemoved(AbstractController controller) {
        UsbDeviceContext context = usbDeviceContexts.get(controller.getControllerId());
        if (context != null) {
            LimeLog.info("Removed controller: "+controller.getControllerId());
            releaseControllerNumber(context, "device_removed");
            context.destroy();
            usbDeviceContexts.remove(controller.getControllerId());
        }
        usbPadCount = usbDeviceContexts.size();
        mainThreadHandler.post(this::refreshRealPadPresence);
    }

    @Override
    public void deviceAdded(AbstractController controller) {
        if (stopped) {
            return;
        }

        UsbDeviceContext context = createUsbDeviceContextForDevice(controller);
        usbDeviceContexts.put(controller.getControllerId(), context);
        usbPadCount = usbDeviceContexts.size();
        // USB driver thread: the refresh runs where the input contexts live.
        mainThreadHandler.post(this::refreshRealPadPresence);
    }

    class GenericControllerContext implements StreamGamepadPolicy.Holder {
        public int id;
        public boolean external;

        public int vendorId;
        public int productId;

        public float triggerDeadzone;

        // MoonWaker: USB and unknown-device contexts behave as real pads.
        public DeviceKind deviceKind = DeviceKind.REAL_PAD;
        public boolean assignedControllerNumber;
        public boolean reservedControllerNumber;
        public short controllerNumber;

        @Override public DeviceKind kind() { return deviceKind; }
        @Override public boolean assigned() { return assignedControllerNumber; }
        @Override public void releaseNeutral() {
            resetControllerInput(this);
            releaseControllerNumber(this, "real_pad_connected");
            assignedControllerNumber = false;
            reservedControllerNumber = false;
        }

        public int inputMap = 0;
        public byte leftTrigger = 0x00;
        public byte rightTrigger = 0x00;
        public short rightStickX = 0x0000;
        public short rightStickY = 0x0000;
        public short leftStickX = 0x0000;
        public short leftStickY = 0x0000;

        public boolean mouseEmulationActive;
        public int mouseEmulationLastInputMap;
        public final int mouseEmulationReportPeriod = 50;

        public final Runnable mouseEmulationRunnable = new Runnable() {
            @Override
            public void run() {
                if (!mouseEmulationActive) {
                    return;
                }

                // Send mouse events from analog sticks
                if (prefConfig.analogStickForScrolling == PreferenceConfiguration.AnalogStickForScrolling.RIGHT) {
                    sendEmulatedMouseMove(leftStickX, leftStickY);
                    sendEmulatedMouseScroll(rightStickX, rightStickY);
                }
                else if (prefConfig.analogStickForScrolling == PreferenceConfiguration.AnalogStickForScrolling.LEFT) {
                    sendEmulatedMouseMove(rightStickX, rightStickY);
                    sendEmulatedMouseScroll(leftStickX, leftStickY);
                }
                else {
                    sendEmulatedMouseMove(leftStickX, leftStickY);
                    sendEmulatedMouseMove(rightStickX, rightStickY);
                }

                // Requeue the callback
                mainThreadHandler.postDelayed(this, mouseEmulationReportPeriod);
            }
        };

        public void toggleMouseEmulation() {
            mainThreadHandler.removeCallbacks(mouseEmulationRunnable);
            mouseEmulationActive = !mouseEmulationActive;
            Toast.makeText(activityContext, "Mouse emulation is: " + (mouseEmulationActive ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();

            if (mouseEmulationActive) {
                mainThreadHandler.postDelayed(mouseEmulationRunnable, mouseEmulationReportPeriod);
            }
        }

        public void destroy() {
            mouseEmulationActive = false;
            mainThreadHandler.removeCallbacks(mouseEmulationRunnable);
        }

        public void sendControllerArrival() {}
    }

    class InputDeviceContext extends GenericControllerContext {
        public String name;
        public VibratorManager vibratorManager;
        public Vibrator vibrator;
        public boolean quadVibrators;
        public short lowFreqMotor, highFreqMotor;
        public short leftTriggerMotor, rightTriggerMotor;

        public SensorManager sensorManager;
        // Whether the host was told this pad has motion sensors (only its arrival tells it).
        public boolean motionAnnounced;
        public SensorEventListener gyroListener;
        public short gyroReportRateHz;
        public SensorEventListener accelListener;
        public short accelReportRateHz;

        public InputDevice inputDevice;

        public boolean hasRgbLed;
        public LightsManager.LightsSession lightsSession;
        public int ledArgbValue;

        // These are BatteryState values, not Moonlight values
        public volatile int lastReportedBatteryStatus = BatteryState.STATUS_UNKNOWN;
        public volatile float lastReportedBatteryCapacity = Float.NaN;
        public boolean batteryStateReportedToHost;
        public int lastBatteryWarningLevel;

        public int leftStickXAxis = -1;
        public int leftStickYAxis = -1;

        public int rightStickXAxis = -1;
        public int rightStickYAxis = -1;

        public int leftTriggerAxis = -1;
        public int rightTriggerAxis = -1;
        public boolean triggersIdleNegative;
        public boolean leftTriggerAxisUsed, rightTriggerAxisUsed;

        public int hatXAxis = -1;
        public int hatYAxis = -1;
        public boolean hatXAxisUsed, hatYAxisUsed;

        InputDevice.MotionRange touchpadXRange;
        InputDevice.MotionRange touchpadYRange;
        InputDevice.MotionRange touchpadPressureRange;

        public boolean isNonStandardDualShock4;
        public boolean usesLinuxGamepadStandardFaceButtons;
        public boolean isNonStandardXboxBtController;
        public boolean isServal;
        public boolean backIsStart;
        public boolean modeIsSelect;
        public boolean searchIsMode;
        public boolean hasJoystickAxes;
        public boolean pendingExit;
        public boolean pendingQuit;
        public boolean isDualShockStandaloneTouchpad;

        public int emulatingButtonFlags = 0;
        public boolean hasSelect;
        public boolean hasMode;
        public int pendingOverlayTriggerPressFlag;
        public boolean hasPaddles;
        public boolean hasShare;
        public boolean needsClickpadEmulation;

        // Used for OUYA bumper state tracking since they force all buttons
        // up when the OUYA button goes down. We watch the last time we get
        // a bumper up and compare that to our maximum delay when we receive
        // a Start button press to see if we should activate one of our
        // emulated button combos.
        public long lastLbUpTime = 0;
        public long lastRbUpTime = 0;

        public long startDownTime = 0;

        public final Runnable batteryStateUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                sendControllerBatteryPacket(InputDeviceContext.this);

                // Requeue the callback
                backgroundThreadHandler.postDelayed(this, BATTERY_RECHECK_INTERVAL_MS);
            }
        };

        public final Runnable enableSensorRunnable = new Runnable() {
            @Override
            public void run() {
                if (!gyroAim.forwardsMotionToHost()) {
                    enableGyroAimSensors();
                    return;
                }
                // Turn back on any sensors that should be reporting but are currently unregistered
                if (accelReportRateHz != 0 && accelListener == null) {
                    handleSetMotionEventState(controllerNumber, MoonBridge.LI_MOTION_TYPE_ACCEL, accelReportRateHz);
                }
                if (gyroReportRateHz != 0 && gyroListener == null) {
                    handleSetMotionEventState(controllerNumber, MoonBridge.LI_MOTION_TYPE_GYRO, gyroReportRateHz);
                }
            }
        };

        public final Runnable setLedStateRunnable = new Runnable() {
            @TargetApi(Build.VERSION_CODES.S)
            @Override
            public void run() {
                LightState lightState = new LightState.Builder().setColor(ledArgbValue).build();

                // Set the RGB value for each RGB-controllable LED on the device
                boolean hasRgbLight = false;
                LightsRequest.Builder lightsRequestBuilder = new LightsRequest.Builder();
                for (Light light : inputDevice.getLightsManager().getLights()) {
                    if (light.hasRgbControl()) {
                        lightsRequestBuilder.addLight(light, lightState);
                        hasRgbLight = true;
                    }
                }

                if (hasRgbLight) {
                    // Create a new light session if one doesn't already exist
                    if (lightsSession == null) {
                        lightsSession = inputDevice.getLightsManager().openSession();
                    }

                    // Apply the LED changes
                    lightsSession.requestLights(lightsRequestBuilder.build());
                }
            }
        };

        @Override
        public void destroy() {
            super.destroy();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibratorManager != null) {
                vibratorManager.cancel();
            }
            else if (vibrator != null) {
                vibrator.cancel();
            }

            backgroundThreadHandler.removeCallbacks(enableSensorRunnable);
            backgroundThreadHandler.removeCallbacks(setLedStateRunnable);

            if (gyroListener != null) {
                sensorManager.unregisterListener(gyroListener);
            }
            if (accelListener != null) {
                sensorManager.unregisterListener(accelListener);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (lightsSession != null) {
                    try {
                        lightsSession.close();
                    } catch (RuntimeException e) {
                        LimeLog.warning("Error closing LightsSession: " + e.getMessage());
                    }
                }
            }

            backgroundThreadHandler.removeCallbacks(batteryStateUpdateRunnable);
        }

        @Override
        public void sendControllerArrival() {
            byte type;
            switch (inputDevice.getVendorId()) {
                case 0x045e: // Microsoft
                    type = MoonBridge.LI_CTYPE_XBOX;
                    break;
                case 0x054c: // Sony
                    type = MoonBridge.LI_CTYPE_PS;
                    break;
                case 0x057e: // Nintendo
                    type = MoonBridge.LI_CTYPE_NINTENDO;
                    break;
                default:
                    // Consult SDL's controller type list to see if it knows
                    type = MoonBridge.guessControllerType(inputDevice.getVendorId(), inputDevice.getProductId());
                    break;
            }

            int supportedButtonFlags = 0;
            for (Map.Entry<Integer, Integer> entry : ANDROID_TO_LI_BUTTON_MAP.entrySet()) {
                if (inputDevice.hasKeys(entry.getKey())[0]) {
                    supportedButtonFlags |= entry.getValue();
                }
            }

            // Add non-standard button flags that may not be mapped in the Android kl file
            if (hasPaddles) {
                supportedButtonFlags |=
                        ControllerPacket.PADDLE1_FLAG |
                        ControllerPacket.PADDLE2_FLAG |
                        ControllerPacket.PADDLE3_FLAG |
                        ControllerPacket.PADDLE4_FLAG;
            }
            if (hasShare) {
                supportedButtonFlags |= ControllerPacket.MISC_FLAG;
            }

            if (getMotionRangeForJoystickAxis(inputDevice, MotionEvent.AXIS_HAT_X) != null) {
                supportedButtonFlags |= ControllerPacket.LEFT_FLAG | ControllerPacket.RIGHT_FLAG;
            }
            if (getMotionRangeForJoystickAxis(inputDevice, MotionEvent.AXIS_HAT_Y) != null) {
                supportedButtonFlags |= ControllerPacket.UP_FLAG | ControllerPacket.DOWN_FLAG;
            }

            short capabilities = 0;

            // Most of the advanced InputDevice capabilities came in Android S
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (quadVibrators) {
                    capabilities |= MoonBridge.LI_CCAP_RUMBLE | MoonBridge.LI_CCAP_TRIGGER_RUMBLE;
                }
                else if (vibratorManager != null || vibrator != null) {
                    capabilities |= MoonBridge.LI_CCAP_RUMBLE;
                }

                // Calling InputDevice.getBatteryState() to see if a battery is present
                // performs a Binder transaction that can cause ANRs on some devices.
                // To avoid this, we will just claim we can report battery state for all
                // external gamepad devices on Android S. If it turns out that no battery
                // is actually present, we'll just report unknown battery state to the host.
                if (external) {
                    capabilities |= MoonBridge.LI_CCAP_BATTERY_STATE;
                }

                if (hasRgbLed) {
                    capabilities |= MoonBridge.LI_CCAP_RGB_LED;
                }
            }

            // Report analog triggers if we have at least one trigger axis
            if (leftTriggerAxis != -1 || rightTriggerAxis != -1) {
                capabilities |= MoonBridge.LI_CCAP_ANALOG_TRIGGERS;
            }

            // Report sensors if the input device has them or we're using built-in sensors for a built-in controller
            if (sensorManager != null && sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
                capabilities |= MoonBridge.LI_CCAP_ACCEL;
            }
            if (sensorManager != null && sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
                capabilities |= MoonBridge.LI_CCAP_GYRO;
            }

            byte reportedType;
            if (type != MoonBridge.LI_CTYPE_PS && sensorManager != null) {
                // Override the detected controller type if we're emulating motion sensors on an Xbox controller
                Toast.makeText(activityContext, activityContext.getResources().getText(R.string.toast_controller_type_changed), Toast.LENGTH_LONG).show();
                reportedType = MoonBridge.LI_CTYPE_UNKNOWN;

                // Remember that we should enable the clickpad emulation combo (Select+LB) for this device
                needsClickpadEmulation = true;
            }
            else {
                // Report the true type to the host PC if we're not emulating motion sensors
                reportedType = type;
            }

            // We can perform basic rumble with any vibrator
            if (vibrator != null) {
                capabilities |= MoonBridge.LI_CCAP_RUMBLE;
            }

            // Shield controllers use special APIs for rumble and battery state
            if (sceManager.isRecognizedDevice(inputDevice)) {
                capabilities |= MoonBridge.LI_CCAP_RUMBLE | MoonBridge.LI_CCAP_BATTERY_STATE;
            }

            if ((inputDevice.getSources() & InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD) {
                capabilities |= MoonBridge.LI_CCAP_TOUCHPAD;

                // Use the platform API or internal heuristics to determine if this has a clickpad
                if (hasButtonUnderTouchpad(inputDevice, type)) {
                    supportedButtonFlags |= ControllerPacket.TOUCHPAD_FLAG;
                }
            }

            conn.sendControllerArrivalEvent((byte)controllerNumber, getActiveControllerMask(),
                    reportedType, supportedButtonFlags, capabilities);
            motionAnnounced = (capabilities & (MoonBridge.LI_CCAP_ACCEL | MoonBridge.LI_CCAP_GYRO)) != 0;

            // Aim mode registers the pad sensors itself instead of waiting for a host request.
            if (!gyroAim.forwardsMotionToHost()) enableSensors();

            // After reporting arrival to the host, send initial battery state and begin monitoring
            backgroundThreadHandler.post(batteryStateUpdateRunnable);
        }

        public void migrateContext(InputDeviceContext oldContext) {
            // Take ownership of the sensor and light sessions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                this.lightsSession = oldContext.lightsSession;
                oldContext.lightsSession = null;
            }
            this.ledArgbValue = oldContext.ledArgbValue;
            this.gyroReportRateHz = oldContext.gyroReportRateHz;
            this.accelReportRateHz = oldContext.accelReportRateHz;

            // Don't release the controller number, because we will carry it over if it is present.
            // We also want to make sure the change is invisible to the host PC to avoid an add/remove
            // cycle for the gamepad which may break some games.
            oldContext.destroy();

            // Copy over existing controller number state
            this.assignedControllerNumber = oldContext.assignedControllerNumber;
            this.reservedControllerNumber = oldContext.reservedControllerNumber;
            this.controllerNumber = oldContext.controllerNumber;

            // Preserve the last known battery state while Android rebuilds the logical
            // InputDevice (DualSense touchpad/sensor changes can trigger this migration).
            this.lastReportedBatteryStatus = oldContext.lastReportedBatteryStatus;
            this.lastReportedBatteryCapacity = oldContext.lastReportedBatteryCapacity;
            this.batteryStateReportedToHost = oldContext.batteryStateReportedToHost;
            this.lastBatteryWarningLevel = oldContext.lastBatteryWarningLevel;

            // We may have set this device to use the built-in sensor manager. If so, do that again.
            if (oldContext.sensorManager == deviceSensorManager) {
                this.sensorManager = deviceSensorManager;
            }

            // Copy state initialized in reportControllerArrival()
            this.needsClickpadEmulation = oldContext.needsClickpadEmulation;
            this.motionAnnounced = oldContext.motionAnnounced;

            // Re-enable sensors on the new context
            enableSensors();

            // Refresh the LED state if it's set. We don't know if there was a pending LED state
            // callback when the context was migrated, so we'll reset it just to be safe.
            if (this.lightsSession != null) {
                backgroundThreadHandler.post(setLedStateRunnable);
            }

            // Refresh battery state and start the battery state polling again
            backgroundThreadHandler.post(batteryStateUpdateRunnable);
        }

        public void disableSensors() {
            // Stop any pending enablement
            backgroundThreadHandler.removeCallbacks(enableSensorRunnable);

            // Unregister all sensor listeners
            if (gyroListener != null) {
                sensorManager.unregisterListener(gyroListener);
                gyroListener = null;

                // Send a gyro event to ensure the virtual controller is stationary
                if (gyroAim.forwardsMotionToHost()) {
                    conn.sendControllerMotionEvent((byte) controllerNumber, MoonBridge.LI_MOTION_TYPE_GYRO, 0.f, 0.f, 0.f);
                }
                else if (gyroAim.pause(controllerNumber)) {
                    sendControllerInputPacket(this);
                }
            }
            if (accelListener != null) {
                sensorManager.unregisterListener(accelListener);
                accelListener = null;

                // We leave the acceleration as-is to preserve the attitude of the controller
            }
        }

        private void enableGyroAimSensors() {
            // Runs on the background handler; samples are delivered on the main thread, which owns
            // the contexts. The device's own sensors (fallback) never aim: the TV does not move.
            if (sensorManager == null || sensorManager == deviceSensorManager) return;
            Sensor gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            if (gyro == null) return;
            if (gyroListener == null) {
                gyroListener = createGyroAimListener(this, true);
                sensorManager.registerListener(gyroListener, gyro, GYRO_AIM_GYRO_PERIOD_US, mainThreadHandler);
            }
            Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelListener == null && accel != null) {
                accelListener = createGyroAimListener(this, false);
                sensorManager.registerListener(accelListener, accel, GYRO_AIM_ACCEL_PERIOD_US, mainThreadHandler);
            }
        }

        public void enableSensors() {
            // We allow 1 second for the input device to settle before re-enabling sensors.
            // Pointer capture can cause the input device to change, which can cause
            // InputDeviceSensorManager to crash due to missing null checks on the InputDevice.
            backgroundThreadHandler.postDelayed(enableSensorRunnable, 1000);
        }
    }

    class UsbDeviceContext extends GenericControllerContext {
        public AbstractController device;
        // The game's last rumble, for audio haptics to re-apply.
        public short lowFreqMotor, highFreqMotor;

        @Override
        public void destroy() {
            super.destroy();

            // Nothing for now
        }

        @Override
        public void sendControllerArrival() {
            conn.sendControllerArrivalEvent((byte)controllerNumber, getActiveControllerMask(),
                    device.getType(), device.getSupportedButtonFlags(), device.getCapabilities());
        }
    }
}
