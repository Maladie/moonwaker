package com.limelight.console;

import com.limelight.nvstream.http.ComputerDetails;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

/** Owns transient host presentation state that isn't part of Moonlight discovery. */
final class ConsoleHostStateController {
    interface Clock {
        long now();
    }

    private final Map<String, Long> waking = new LinkedHashMap<>();
    private final Map<String, ComputerDetails.State> lastConfirmed = new LinkedHashMap<>();
    /** Hosts a poll has answered for since the start, so their state and pairing are real. */
    private final Set<String> polled = new HashSet<>();
    /**
     * As a stream ends the host's streamer tears it down and re-initializes its capture, and
     * its serverinfo stops answering for a while although the host is up (TV, old streamer:
     * about 12 s after "Close session"). A poll reports OFFLINE only after several failed
     * tries, so the window also covers that delay. ponytail: one fixed window, counted from the
     * latest end event; tune it on the TV (Vibepollo's virtual display may need longer).
     */
    static final long STREAM_END_SETTLE_MS = 30_000L;

    private final Clock clock;
    /** Uptime of the last stream end on a host (StreamEnds), Long.MIN_VALUE for none. */
    private final ToLongFunction<String> streamEndedAt;

    ConsoleHostStateController(Clock clock, ToLongFunction<String> streamEndedAt) {
        this.clock = clock;
        this.streamEndedAt = streamEndedAt;
    }

    /**
     * Whether a fresh poll's "offline" is only the streamer settling after a stream: within
     * {@link #STREAM_END_SETTLE_MS} of this client's last stream end on that host, for a host
     * last confirmed online. Such a poll is no observation; the host keeps its last confirmed
     * state.
     */
    boolean settling(ComputerDetails host) {
        if (host == null || host.state != ComputerDetails.State.OFFLINE
                || lastConfirmed.get(host.uuid) != ComputerDetails.State.ONLINE) return false;
        long endedAt = streamEndedAt.applyAsLong(host.uuid);
        return endedAt != Long.MIN_VALUE && clock.now() - endedAt < STREAM_END_SETTLE_MS;
    }

    long beginWaking(String hostUuid) {
        if (hostUuid == null || hostUuid.isEmpty() || waking.containsKey(hostUuid)) return -1L;
        long token = clock.now();
        waking.put(hostUuid, token);
        return token;
    }

    boolean finishWaking(String hostUuid, long token) {
        Long current = waking.get(hostUuid);
        if (current == null || current.longValue() != token) return false;
        waking.remove(hostUuid);
        return true;
    }

    void remember(String hostUuid, ComputerDetails.State state) {
        if (hostUuid == null || hostUuid.isEmpty() || state == null
                || state == ComputerDetails.State.UNKNOWN) return;
        lastConfirmed.put(hostUuid, state);
    }

    boolean observe(ComputerDetails host) {
        return observe(host, true);
    }

    boolean observe(ComputerDetails host, boolean fresh) {
        if (host == null) return false;
        if (fresh) {
            if (host.uuid != null) polled.add(host.uuid);
            remember(host.uuid, host.state);
            return host.state == ComputerDetails.State.ONLINE
                    && waking.remove(host.uuid) != null;
        }
        ComputerDetails.State remembered = lastConfirmed.get(host.uuid);
        if (remembered != null) host.state = remembered;
        return false;
    }

    boolean isWaking(String hostUuid) {
        return hostUuid != null && waking.containsKey(hostUuid);
    }

    /** Uptime at which this client started waking the host, or -1. */
    long wakingSince(String hostUuid) {
        Long started = hostUuid == null ? null : waking.get(hostUuid);
        return started == null ? -1L : started;
    }

    ConsoleHostPresentation.State state(ComputerDetails host) {
        return ConsoleHostPresentation.state(host,
                host != null && isWaking(host.uuid), host != null && polled.contains(host.uuid));
    }
}
