package com.limelight.diagnostics;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The TV half of "Zgłoś problem": report.json plus the TV's text logs, redacted and bounded,
 * as the zip the Gateway merges with the host's logs (host_diagnostics.py mirrors the
 * redaction).
 */
public final class ProblemReportBundle {
    /** The window around the marked moment (or now) the report covers. */
    public static final long WINDOW_BEFORE_MS = 15L * 60_000L;
    public static final long WINDOW_AFTER_MS = 5L * 60_000L;
    public static final int EVENTS_MAX_BYTES = 4 * 1024 * 1024;
    public static final int LOGCAT_MAX_BYTES = 2 * 1024 * 1024;
    /** Each entry and their sum stay below the Gateway's 8 MiB body even uncompressed. */
    static final int ENTRY_MAX_BYTES = 4 * 1024 * 1024;
    static final int TOTAL_MAX_BYTES = 7 * 1024 * 1024;

    private static final String KEY = "[A-Za-z0-9_]*(?:token|secret|password|passwd|pin|"
            + "authorization|private_key|certificate|cert|pairing_code|code_sha256|webhook)"
            + "[A-Za-z0-9_]*";
    private static final Pattern[] PATTERNS = {
            Pattern.compile("-----BEGIN [A-Z0-9 ]+-----[\\s\\S]*?-----END [A-Z0-9 ]+-----"),
            Pattern.compile("(?i)https://(?:(?:ptb|canary)\\.)?discord(?:app)?\\.com/api/webhooks/\\S+"),
            Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+"),
            Pattern.compile("(?i)(\"" + KEY + "\"\\s*:\\s*)\"(?:[^\"\\\\]|\\\\.)*\""),
            Pattern.compile("(?i)\\b(" + KEY + ")(\\s*[=:]\\s*)[^\\s,;&\"'}]+"),
            Pattern.compile("(?i)\\bPIN\\b[^0-9\\n]{0,12}[0-9]{4,8}\\b"),
            Pattern.compile("(?i)([A-Z]:[\\\\/]+(?:Users|Documents and Settings)[\\\\/]+)"
                    + "[^\\\\/\\s\"':*?<>|]+"),
            Pattern.compile("(/(?:home|Users)/)[^/\\s\"']+"),
            Pattern.compile("\\b[0-9A-Fa-f]{128,}\\b"),
            Pattern.compile("[A-Za-z0-9+/]{200,}={0,2}"),
    };
    private static final String[] REPLACEMENTS = {
            "[REDACTED PEM]", "[REDACTED WEBHOOK]", "Bearer [REDACTED]", "$1\"[REDACTED]\"",
            "$1$2[REDACTED]", "PIN [REDACTED]", "$1<user>", "$1<user>", "[REDACTED HEX]",
            "[REDACTED BASE64]",
    };

    private ProblemReportBundle() { }

    /** Tokens, Bearer values, PINs, pairing secrets, certificates, webhooks, account names. */
    public static String redact(String text) {
        String result = text == null ? "" : text;
        for (int index = 0; index < PATTERNS.length; index++) {
            result = PATTERNS[index].matcher(result).replaceAll(REPLACEMENTS[index]);
        }
        return result;
    }

    /** [from, to] in epoch milliseconds around {@code anchorMs}, ending no later than now. */
    public static long[] window(long anchorMs, long nowMs) {
        return new long[]{anchorMs - WINDOW_BEFORE_MS, Math.min(nowMs, anchorMs + WINDOW_AFTER_MS)};
    }

    /** The newest whole lines of {@code text} within {@code limitBytes} UTF-8 bytes. */
    public static String tail(String text, int limitBytes) {
        if (text == null) return "";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= limitBytes) return text;
        String cut = new String(bytes, bytes.length - limitBytes, limitBytes, StandardCharsets.UTF_8);
        int newline = cut.indexOf('\n');
        return newline < 0 ? "" : cut.substring(newline + 1);
    }

    /** The zip: report.json first, then each entry redacted and bounded to its newest lines. */
    public static byte[] zip(JSONObject report, Map<String, String> entries) throws IOException {
        Map<String, String> bounded = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String text = tail(redact(entry.getValue()),
                    Math.min(ENTRY_MAX_BYTES, Math.max(0, TOTAL_MAX_BYTES - total)));
            total += text.getBytes(StandardCharsets.UTF_8).length;
            bounded.put(entry.getKey(), text);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            put(zip, "report.json", report.toString(1));
            for (Map.Entry<String, String> entry : bounded.entrySet()) {
                put(zip, entry.getKey(), entry.getValue());
            }
        } catch (org.json.JSONException impossible) {
            throw new IOException(impossible);
        }
        return output.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
