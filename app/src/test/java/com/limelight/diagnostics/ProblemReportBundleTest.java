package com.limelight.diagnostics;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProblemReportBundleTest {
    private static final String FORTY_A = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Test public void redactsSecretsAndAccountNames() {
        String text = String.join("\n",
                "Authorization: Bearer abc.def-123",
                "{\"token\":\"t0k3n\",\"pin_required\":true,\"request_id\":\"r-1\"}",
                "pairing_code=123456 pin=4321",
                "Entered PIN 2468 for pairing",
                "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----",
                "C:\\Users\\Marek\\AppData\\x.json",
                "/home/marek/file",
                "https://discord.com/api/webhooks/1234567/" + FORTY_A);
        String redacted = ProblemReportBundle.redact(text);
        for (String secret : new String[]{"abc.def-123", "t0k3n", "123456", "4321", "2468",
                "MIIB", "Marek", "marek", FORTY_A}) {
            assertFalse(secret, redacted.contains(secret));
        }
        assertTrue(redacted.contains("\"pin_required\":true"));
        assertTrue(redacted.contains("\"request_id\":\"r-1\""));
        assertTrue(redacted.contains("C:\\Users\\<user>\\AppData"));
    }

    @Test public void windowEndsAtNow() {
        long[] marked = ProblemReportBundle.window(1_000_000L, 2_000_000L);
        assertArrayEquals(new long[]{1_000_000L - ProblemReportBundle.WINDOW_BEFORE_MS,
                1_000_000L + ProblemReportBundle.WINDOW_AFTER_MS}, marked);
        assertEquals(5_000L, ProblemReportBundle.window(5_000L, 5_000L)[1]);
    }

    @Test public void tailKeepsNewestWholeLines() {
        assertEquals("c\nd\n", ProblemReportBundle.tail("a\nb\nc\nd\n", 5));
        assertEquals("short", ProblemReportBundle.tail("short", 100));
    }

    @Test public void bundleStaysBelowTheGatewayBoundWithIncompressibleLogs() throws Exception {
        Random random = new Random(3);
        StringBuilder noise = new StringBuilder();
        while (noise.length() < 12 * 1024 * 1024) {
            noise.append(Long.toHexString(random.nextLong())).append(' ');
            if (random.nextInt(8) == 0) noise.append('\n');
        }
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("events.jsonl", noise.toString());
        entries.put("logcat.txt", noise.toString() + "\nNEWEST Bearer xyz\n");
        byte[] zip = ProblemReportBundle.zip(new JSONObject().put("category", "stream"), entries);
        assertTrue(zip.length < 8 * 1024 * 1024);

        List<String> names = new ArrayList<>();
        String logcat = "";
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                names.add(entry.getName());
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                byte[] buffer = new byte[65536];
                int read;
                while ((read = input.read(buffer)) > 0) data.write(buffer, 0, read);
                if ("logcat.txt".equals(entry.getName())) {
                    logcat = new String(data.toByteArray(), StandardCharsets.UTF_8);
                }
            }
        }
        assertEquals(java.util.Arrays.asList("report.json", "events.jsonl", "logcat.txt"), names);
        assertTrue(logcat.endsWith("NEWEST Bearer [REDACTED]\n"));
    }
}
