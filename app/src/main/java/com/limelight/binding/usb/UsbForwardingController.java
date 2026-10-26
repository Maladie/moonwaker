package com.limelight.binding.usb;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.limelight.LimeLog;
import com.limelight.diagnostics.MoonWakerDiagnostics;
import com.limelight.gateway.GatewayConnection;
import com.limelight.gateway.GatewayTransport;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The one owner of forwarded USB devices on this TV. It follows the selected host and
 * profile while MoonWaker is in use (carousel and stream alike; {@code MoonWakerApplication}
 * says which), remembers per host which devices the user shared (owner decision D8) and
 * shares them again when they are plugged in. Each shared device has one worker that asks
 * the Gateway for a lease, opens the tunnel and serves it with {@link UsbIpExporter}. The
 * Gateway decides what may pass; refusals come back as reasons the UI shows.
 */
public final class UsbForwardingController {
    public static final String STATE_PERMISSION = "permission";
    public static final String STATE_CONNECTING = "connecting";
    public static final String STATE_ACTIVE = "active";
    public static final String STATE_WAITING = "waiting";
    public static final String STATE_RECONNECTING = "reconnecting";
    public static final String STATE_REFUSED = "refused";
    public static final String STATE_OFF = "off";

    private static final String ACTION_PERMISSION = "com.limelight.USB_FORWARDING_PERMISSION";
    private static final String PREFS = "usb_forwarding";
    private static final long WAIT_RETRY_MS = 10_000;
    /** An attachment shorter than this did not really work; this many in a row stop retrying. */
    private static final long STABLE_ATTACH_MS = 10_000;
    private static final int MAX_SHORT_ATTACHES = 3;
    private static final long MAX_RETRY_MS = 30_000;

    @SuppressLint("StaticFieldLeak") // The application context.
    private static UsbForwardingController instance;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final UsbManager usbManager;
    private final SharedPreferences remembered;
    private final GatewayTransport transport = new GatewayTransport();
    private final Map<String, Export> exports = new HashMap<>();
    private final Map<String, String> refusals = new HashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final WifiManager.WifiLock wifiLock;
    private Consumer<UsbDevice> driverRelease = device -> { };
    private String hostUuid = "";
    private GatewayConnection connection;

    /** What the UI shows for one USB device of this TV. */
    public static final class Entry {
        public final String deviceName;
        public final String label;
        public final boolean shared;
        public final String state;
        public final String reason;

        Entry(String deviceName, String label, boolean shared, String state, String reason) {
            this.deviceName = deviceName;
            this.label = label;
            this.shared = shared;
            this.state = state;
            this.reason = reason;
        }
    }

    public static synchronized UsbForwardingController get(Context context) {
        if (instance == null) instance = new UsbForwardingController(context.getApplicationContext());
        return instance;
    }

    /** Whether Moonlight's own USB driver must leave this device alone. Main thread. */
    public static boolean isForwarded(UsbDevice device) {
        UsbForwardingController current = instance;
        return current != null && current.exports.containsKey(device.getDeviceName());
    }

    /**
     * Whether a USB audio device is forwarded: Android's USB audio driver lost it, so this
     * TV's own sounds must not go to it. Listeners hear when this changes. Main thread.
     */
    public static boolean forwardsAudio() {
        UsbForwardingController current = instance;
        if (current == null) return false;
        for (Export export : current.exports.values()) {
            for (int index = 0; index < export.device.getInterfaceCount(); index++) {
                if (export.device.getInterface(index).getInterfaceClass()
                        == UsbConstants.USB_CLASS_AUDIO) return true;
            }
        }
        return false;
    }

    private UsbForwardingController(Context context) {
        this.context = context;
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        remembered = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "MoonWaker USB");
        wifiLock.setReferenceCounted(false);
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_PERMISSION);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                onUsbEvent(intent);
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
    }

    /** Moonlight's USB driver registers how it lets go of a device it holds. Main thread. */
    public void setDriverRelease(Consumer<UsbDevice> release) {
        driverRelease = release != null ? release : device -> { };
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /**
     * The host and profile MoonWaker is used with now, or null when it is not used (left,
     * no host, profile gate showing). A change ends every forwarded device; the remembered
     * ones of the new host are shared again. Main thread.
     */
    public void follow(String host, GatewayConnection next) {
        String nextHost = host == null ? "" : host;
        if (!nextHost.equals(hostUuid) || !sameOwner(connection, next)) {
            if (!exports.isEmpty()) {
                MoonWakerDiagnostics.record("INFO", "android.usb", "usb.exports_stopped",
                        "reason", next == null ? "not_in_use" : "owner_changed");
            }
            stopAll();
            hostUuid = nextHost;
            connection = next;
            refusals.clear();
        }
        if (connection != null) shareRemembered();
        notifyListeners();
    }

    private static boolean sameOwner(GatewayConnection current, GatewayConnection next) {
        if (current == null || next == null) return current == next;
        return current.endpoint().equals(next.endpoint())
                && current.profileId().equals(next.profileId())
                && current.token().equals(next.token())
                && current.certificateSha256().equals(next.certificateSha256());
    }

    public boolean available() {
        return connection != null;
    }

    /** Every USB device of this TV with its forwarding state. Main thread. */
    public List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        Set<String> shared = rememberedKeys();
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            Export export = exports.get(device.getDeviceName());
            String refusal = refusals.get(device.getDeviceName());
            entries.add(new Entry(device.getDeviceName(), label(device),
                    shared.contains(deviceKey(device)),
                    export != null ? export.state : refusal != null ? STATE_REFUSED : STATE_OFF,
                    export != null ? export.reason : refusal != null ? refusal : ""));
        }
        Collections.sort(entries, (first, second) -> first.label.compareToIgnoreCase(second.label));
        return entries;
    }

    /** One state for all devices: a shared one wins, then one in progress, then a refusal. */
    public static String summaryState(List<Entry> entries) {
        String summary = STATE_OFF;
        for (Entry entry : entries) {
            if (STATE_ACTIVE.equals(entry.state)) return entry.state;
            if ((STATE_OFF.equals(summary) || STATE_REFUSED.equals(summary))
                    && !STATE_OFF.equals(entry.state)) {
                summary = entry.state;
            }
        }
        return summary;
    }

    public int activeCount() {
        int count = 0;
        for (Export export : exports.values()) if (STATE_ACTIVE.equals(export.state)) count++;
        return count;
    }

    /** The user shares a device: remembered for this host and attached now. Main thread. */
    public void share(String deviceName) {
        UsbDevice device = usbManager.getDeviceList().get(deviceName);
        if (device == null || connection == null) return;
        Set<String> keys = new LinkedHashSet<>(rememberedKeys());
        keys.add(deviceKey(device));
        remembered.edit().putStringSet(hostUuid, keys).apply();
        refusals.remove(deviceName);
        start(device);
        notifyListeners();
    }

    /** "Stop": the device comes back to the TV and is no longer shared with this host. */
    public void stop(String deviceName) {
        UsbDevice device = usbManager.getDeviceList().get(deviceName);
        if (device != null) forget(device);
        Export export = exports.remove(deviceName);
        if (export != null) export.stop();
        refusals.remove(deviceName);
        updateWifiLock();
        notifyListeners();
    }

    /** Every device comes back to the TV; what is remembered stays. Main thread. */
    public void stopAll() {
        for (Export export : exports.values()) export.stop();
        exports.clear();
        updateWifiLock();
    }

    private Set<String> rememberedKeys() {
        Set<String> keys = remembered.getStringSet(hostUuid, null);
        return keys == null ? new HashSet<>() : keys;
    }

    private void forget(UsbDevice device) {
        Set<String> keys = new LinkedHashSet<>(rememberedKeys());
        if (keys.remove(deviceKey(device))) remembered.edit().putStringSet(hostUuid, keys).apply();
    }

    /** The serial number needs a permission; vendor and product identify a model. */
    static String deviceKey(UsbDevice device) {
        return String.format(java.util.Locale.US, "%04x:%04x", device.getVendorId(),
                device.getProductId());
    }

    static String label(UsbDevice device) {
        String name = device.getProductName();
        return name != null && !name.trim().isEmpty() ? name.trim()
                : String.format(java.util.Locale.US, "USB %04x:%04x", device.getVendorId(),
                device.getProductId());
    }

    private void shareRemembered() {
        Set<String> keys = rememberedKeys();
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (keys.contains(deviceKey(device)) && !exports.containsKey(device.getDeviceName())
                    && !refusals.containsKey(device.getDeviceName())) {
                start(device);
            }
        }
    }

    private void start(UsbDevice device) {
        Export previous = exports.remove(device.getDeviceName());
        if (previous != null) previous.stop();
        Export export = new Export(device, connection);
        exports.put(device.getDeviceName(), export);
        if (!usbManager.hasPermission(device)) {
            export.state = STATE_PERMISSION;
            Intent intent = new Intent(ACTION_PERMISSION).setPackage(context.getPackageName());
            // Mutable so that the framework can add the device and the answer.
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? PendingIntent.FLAG_MUTABLE : 0;
            try {
                usbManager.requestPermission(device,
                        PendingIntent.getBroadcast(context, 0, intent, flags));
            } catch (SecurityException blocked) {
                exports.remove(device.getDeviceName());
                refusals.put(device.getDeviceName(), "usb_permission_blocked");
            }
            return;
        }
        // The user chose this device: Moonlight's own Xbox driver lets go of it (D10).
        driverRelease.accept(device);
        export.begin();
    }

    private void onUsbEvent(Intent intent) {
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        String action = intent.getAction();
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            if (connection != null) shareRemembered();
        } else if (device != null && UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            Export export = exports.remove(device.getDeviceName());
            if (export != null) {
                MoonWakerDiagnostics.record("INFO", "android.usb", "usb.exports_stopped",
                        "reason", "unplugged");
            }
            if (export != null) export.stop();
            refusals.remove(device.getDeviceName());
            updateWifiLock();
        } else if (device != null && ACTION_PERMISSION.equals(action)) {
            Export export = exports.get(device.getDeviceName());
            if (export == null || !STATE_PERMISSION.equals(export.state)) return;
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                driverRelease.accept(device);
                export.state = STATE_CONNECTING;
                export.begin();
            } else {
                exports.remove(device.getDeviceName());
                refusals.put(device.getDeviceName(), "usb_permission_denied");
            }
        }
        notifyListeners();
    }

    private void onExportChanged(Export export) {
        if (exports.get(export.device.getDeviceName()) != export) return;
        if (STATE_REFUSED.equals(export.state)) {
            exports.remove(export.device.getDeviceName());
            refusals.put(export.device.getDeviceName(), export.reason);
            // A device the host refuses is not offered again on its own.
            if (isDeviceRefusal(export.reason)) forget(export.device);
        }
        updateWifiLock();
        notifyListeners();
    }

    private void updateWifiLock() {
        boolean active = activeCount() > 0;
        if (active && !wifiLock.isHeld()) wifiLock.acquire();
        else if (!active && wifiLock.isHeld()) wifiLock.release();
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) listener.run();
    }

    /** Which device the lease is for; the Gateway checks the import names the same. */
    static JSONObject declaration(UsbDevice device) throws JSONException {
        return new JSONObject()
                .put("busid", busId(device.getDeviceName()))
                .put("vendor_id", device.getVendorId())
                .put("product_id", device.getProductId())
                .put("name", label(device));
    }

    /** {@code /dev/bus/usb/001/005} is bus 1, device 5: USB/IP bus ID "1-5". */
    static int[] busAndDevice(String deviceName) {
        String[] parts = deviceName.split("/");
        if (parts.length < 2) throw new IllegalArgumentException("Unexpected USB device name");
        return new int[]{Integer.parseInt(parts[parts.length - 2]),
                Integer.parseInt(parts[parts.length - 1])};
    }

    static String busId(String deviceName) {
        int[] numbers = busAndDevice(deviceName);
        return numbers[0] + "-" + numbers[1];
    }

    /** The device did not import as itself: retrying cannot change it. */
    static boolean isDeviceRefusal(String reason) {
        return reason.startsWith("usb_import_");
    }

    /** Refusals that end when the host's situation changes: try again in a while. */
    static boolean isWaitingReason(String reason) {
        return "usb_session_taken_over".equals(reason) || "usb_host_busy".equals(reason)
                || "usb_detach_pending".equals(reason) || "usb_device_busy".equals(reason);
    }

    /** One shared device: a worker that keeps it attached while it is wanted. */
    private final class Export implements Runnable {
        final UsbDevice device;
        final GatewayConnection gateway;
        volatile String state = STATE_CONNECTING;
        volatile String reason = "";
        private volatile boolean stopped;
        private volatile Closeable tunnel;
        /** When the host imported the device in the current attachment; 0 when it did not. */
        private volatile long importedAt;
        private Thread thread;

        Export(UsbDevice device, GatewayConnection gateway) {
            this.device = device;
            this.gateway = gateway;
        }

        void begin() {
            thread = new Thread(this, "MoonWaker-usb-" + busId(device.getDeviceName()));
            thread.start();
        }

        void stop() {
            stopped = true;
            closeQuietly(tunnel);
            if (thread != null) thread.interrupt();
        }

        private void publish(String nextState, String nextReason) {
            state = nextState;
            reason = nextReason;
            main.post(() -> onExportChanged(this));
        }

        @Override public void run() {
            try {
                serve();
            } catch (Throwable unexpected) {
                // A failure of one device's worker must never end MoonWaker; it is that
                // device's state, and choosing the device again retries it.
                LimeLog.warning("USB forwarding failed: " + unexpected);
                if (!stopped) publish(STATE_REFUSED, "usb_failed");
            }
        }

        private void serve() {
            long retryMs = 2_000;
            int shortAttaches = 0;
            while (!stopped) {
                importedAt = 0;
                String ended = attachOnce();
                if (stopped) return;
                long delayMs;
                boolean stable = importedAt != 0
                        && System.currentTimeMillis() - importedAt >= STABLE_ATTACH_MS;
                if (ended.isEmpty() && !stable && ++shortAttaches >= MAX_SHORT_ATTACHES) {
                    // The host keeps dropping it at once (it cannot attach it): stop here
                    // instead of flapping; choosing the device again retries.
                    publish(STATE_REFUSED, "usb_failed");
                    return;
                }
                if (ended.isEmpty()) {
                    if (stable) {
                        // It worked for a while: start the backoff again.
                        shortAttaches = 0;
                        retryMs = 2_000;
                    }
                    delayMs = retryMs;
                    retryMs = Math.min(MAX_RETRY_MS, retryMs * 2);
                    publish(STATE_RECONNECTING, "");
                } else if (isWaitingReason(ended)) {
                    delayMs = WAIT_RETRY_MS;
                    publish(STATE_WAITING, ended);
                } else if (ended.startsWith("usb")
                        || GatewayTransport.LICENSE_REQUIRED.equals(ended)) {
                    // The host or the device refused it: trying again cannot help.
                    publish(STATE_REFUSED, ended);
                    return;
                } else {
                    delayMs = retryMs;
                    retryMs = Math.min(MAX_RETRY_MS, retryMs * 2);
                    publish(STATE_RECONNECTING, ended);
                }
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException interrupted) {
                    return;
                }
            }
        }

        /** Empty when an attachment ran and ended; otherwise why it did not start. */
        private String attachOnce() {
            AndroidUsbIpDevice opened = null;
            GatewayTransport.UsbTunnel open = null;
            try {
                publish(STATE_CONNECTING, "");
                JSONObject lease = transport.postJson(gateway, "/api/v1/usb/attachments",
                        declaration(device), 10_000);
                // Claimed only now, so a device waiting for the host stays usable on the TV.
                try {
                    opened = AndroidUsbIpDevice.open(usbManager, device);
                } catch (IOException unavailable) {
                    return "usb_claim_failed";
                } catch (LinkageError missingNative) {
                    // A build without libmoonwaker-usbfs.so cannot forward any device.
                    LimeLog.warning("USB forwarding is unavailable: " + missingNative);
                    return "usb_unsupported";
                }
                open = transport.openUsbTunnel(gateway, lease.getString("lease_id"));
                tunnel = open;
                if (stopped) return "";
                int[] numbers = busAndDevice(device.getDeviceName());
                // Shared only once the PC really imported it, not when the tunnel opened.
                UsbIpExporter exporter = new UsbIpExporter(opened, numbers[0], numbers[1],
                        open.input(), open.output());
                long started = System.currentTimeMillis();
                try {
                    exporter.run(() -> {
                        importedAt = System.currentTimeMillis();
                        publish(STATE_ACTIVE, "");
                    });
                } finally {
                    List<Object> fields = new ArrayList<>(Arrays.asList(
                            "reason", stopped ? "stopped" : exporter.endReason(),
                            "error_type", exporter.endError().isEmpty() ? null
                                    : exporter.endError(),
                            "duration_ms", System.currentTimeMillis() - started,
                            "stage", importedAt != 0 ? "imported" : "import"));
                    fields.addAll(Arrays.asList(exporter.isoOutFields()));
                    MoonWakerDiagnostics.record("INFO", "android.usb", "usb.attachment_ended",
                            fields.toArray());
                }
                return "";
            } catch (GatewayTransport.GatewayException refused) {
                String why = refused.reason();
                return why == null || why.isEmpty() ? "host_unavailable" : why;
            } catch (IOException | JSONException | RuntimeException failed) {
                if (!stopped) LimeLog.warning("USB forwarding ended: " + failed);
                return open == null ? "host_unavailable" : "";
            } finally {
                tunnel = null;
                closeQuietly(open);
                // Released interfaces go back to Android's own drivers.
                if (opened != null) opened.close();
            }
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Ending anyway.
        }
    }
}
