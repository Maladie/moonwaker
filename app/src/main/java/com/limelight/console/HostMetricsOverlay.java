package com.limelight.console;

import android.content.Context;
import android.os.SystemClock;

import com.limelight.R;
import com.limelight.gateway.GatewayConnection;
import com.limelight.gateway.GatewayTransport;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Host load lines for the stream statistics overlay. The overlay's own updates drive the
 * reads, so nothing is polled while it shows no frames; a field the host does not report,
 * or a read older than {@link #STALE_MS}, leaves its line out.
 */
public final class HostMetricsOverlay {
    static final long POLL_MS = 2_000L;
    static final long STALE_MS = 6_000L;
    private static final int READ_TIMEOUT_MS = 1_500;

    static final class Metrics {
        final int cpuPercent;
        final int gpuPercent;
        final int encoderPercent;
        final int gpuTemperatureC;
        final int vramUsedMb;
        final int vramTotalMb;

        Metrics(int cpuPercent, int gpuPercent, int encoderPercent, int gpuTemperatureC,
                int vramUsedMb, int vramTotalMb) {
            this.cpuPercent = cpuPercent;
            this.gpuPercent = gpuPercent;
            this.encoderPercent = encoderPercent;
            this.gpuTemperatureC = gpuTemperatureC;
            this.vramUsedMb = vramUsedMb;
            this.vramTotalMb = vramTotalMb;
        }
    }

    private final Context context;
    private final GatewayConnection connection;
    private final GatewayTransport transport = new GatewayTransport();
    private boolean inFlight;
    private long requestedAtMs = Long.MIN_VALUE / 2;
    private long receivedAtMs = Long.MIN_VALUE / 2;
    private String text = "";

    public HostMetricsOverlay(Context context, GatewayConnection connection) {
        this.context = context.getApplicationContext();
        this.connection = connection;
    }

    /** The current lines, empty when there are none; starts a read when one is due. */
    public synchronized String text(long nowMs) {
        if (!inFlight && nowMs - requestedAtMs >= POLL_MS) {
            inFlight = true;
            requestedAtMs = nowMs;
            new Thread(this::read, "MoonWaker-HostMetrics").start();
        }
        return nowMs - receivedAtMs <= STALE_MS ? text : "";
    }

    private void read() {
        String lines = null;
        try {
            lines = format(parse(transport.getJson(connection,
                    "/api/v1/session/host-metrics", READ_TIMEOUT_MS)));
        } catch (IOException | RuntimeException ignored) {
            // No line rather than an error: the next overlay update asks again.
        }
        synchronized (this) {
            inFlight = false;
            if (lines != null) {
                text = lines;
                receivedAtMs = SystemClock.uptimeMillis();
            }
        }
    }

    static Metrics parse(JSONObject response) {
        JSONObject metrics = response == null || !response.optBoolean("ok", false)
                ? null : response.optJSONObject("metrics");
        if (metrics == null) metrics = new JSONObject();
        return new Metrics(bounded(metrics, "cpu_percent", 0, 100),
                bounded(metrics, "gpu_percent", 0, 100),
                bounded(metrics, "encoder_percent", 0, 100),
                bounded(metrics, "gpu_temperature_c", 1, 150),
                bounded(metrics, "vram_used_mb", 0, 1 << 20),
                bounded(metrics, "vram_total_mb", 1, 1 << 20));
    }

    private static int bounded(JSONObject metrics, String key, int min, int max) {
        Object value = metrics.opt(key);
        if (!(value instanceof Integer)) return -1;
        int number = (Integer) value;
        return number < min || number > max ? -1 : number;
    }

    private String format(Metrics metrics) {
        List<String> lines = new ArrayList<>();
        List<String> gpu = new ArrayList<>();
        if (metrics.gpuPercent >= 0) {
            gpu.add(context.getString(R.string.perf_overlay_host_gpu, metrics.gpuPercent));
        }
        if (metrics.encoderPercent >= 0) {
            gpu.add(context.getString(R.string.perf_overlay_host_encoder,
                    metrics.encoderPercent));
        }
        if (metrics.gpuTemperatureC >= 0) {
            gpu.add(context.getString(R.string.perf_overlay_host_gpu_temperature,
                    metrics.gpuTemperatureC));
        }
        if (!gpu.isEmpty()) lines.add(String.join(" · ", gpu));
        if (metrics.vramUsedMb >= 0) {
            lines.add(metrics.vramTotalMb > 0
                    ? context.getString(R.string.perf_overlay_host_vram,
                    metrics.vramUsedMb / 1024f, metrics.vramTotalMb / 1024f)
                    : context.getString(R.string.perf_overlay_host_vram_used,
                    metrics.vramUsedMb / 1024f));
        }
        if (metrics.cpuPercent >= 0) {
            lines.add(context.getString(R.string.perf_overlay_host_cpu, metrics.cpuPercent));
        }
        return String.join("\n", lines);
    }
}
