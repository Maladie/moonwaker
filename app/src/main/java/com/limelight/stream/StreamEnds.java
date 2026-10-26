package com.limelight.stream;

import android.os.SystemClock;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When this client last ended a stream on each host; one per process, owned by the
 * SessionModel. After it the host's streamer tears the stream down and stops answering for a
 * while although the host is up; the console reads this fact
 * (ConsoleHostStateController#settling), whichever screen ended the stream and whichever
 * console instance shows the host afterwards.
 */
public final class StreamEnds {
    private final Map<String, Long> endedAt = new ConcurrentHashMap<>();

    /**
     * A stream on {@code hostId} ends now: its transport stopped, or a host-side close of it
     * began or finished. Any thread.
     */
    public void ended(String hostId) {
        if (hostId == null || hostId.trim().isEmpty()) return;
        endedAt.put(key(hostId), SystemClock.uptimeMillis());
    }

    /** Uptime of the last end on {@code hostId}; Long.MIN_VALUE when none in this process. */
    public long lastEndedAt(String hostId) {
        Long at = hostId == null ? null : endedAt.get(key(hostId));
        return at == null ? Long.MIN_VALUE : at;
    }

    private static String key(String hostId) {
        return hostId.trim().toLowerCase(Locale.ROOT);
    }
}
