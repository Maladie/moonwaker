package com.limelight.console;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

public class HostMetricsOverlayTest {
    @Test
    public void readsBoundedFieldsAndLeavesTheRestOut() throws Exception {
        HostMetricsOverlay.Metrics metrics = HostMetricsOverlay.parse(new JSONObject(
                "{\"ok\":true,\"metrics\":{\"cpu_percent\":13,\"gpu_percent\":101,"
                        + "\"encoder_percent\":\"31\",\"gpu_temperature_c\":64,"
                        + "\"vram_used_mb\":3072,\"vram_total_mb\":0}}"));
        assertEquals(13, metrics.cpuPercent);
        assertEquals(-1, metrics.gpuPercent);
        assertEquals(-1, metrics.encoderPercent);
        assertEquals(64, metrics.gpuTemperatureC);
        assertEquals(3072, metrics.vramUsedMb);
        assertEquals(-1, metrics.vramTotalMb);
    }

    @Test
    public void anotherProfilesStreamOrAFailedAnswerHasNoMetrics() throws Exception {
        for (String body : new String[]{"{\"ok\":true,\"metrics\":{}}",
                "{\"ok\":false,\"metrics\":{\"cpu_percent\":5}}", "{}"}) {
            HostMetricsOverlay.Metrics metrics =
                    HostMetricsOverlay.parse(new JSONObject(body));
            assertEquals(-1, metrics.cpuPercent);
            assertEquals(-1, metrics.vramUsedMb);
        }
    }
}
