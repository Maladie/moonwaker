package com.limelight.console;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.limelight.SourceText;
import com.limelight.nvstream.http.ComputerDetails;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ToLongFunction;

public class ConsoleHostStateControllerTest {
    private static final ToLongFunction<String> NO_END = hostId -> Long.MIN_VALUE;

    @Test
    public void staleTimeoutCannotFinishNewWakeAttempt() {
        long[] now = {10L};
        ConsoleHostStateController controller = new ConsoleHostStateController(() -> now[0], NO_END);
        long first = controller.beginWaking("host");
        assertTrue(controller.finishWaking("host", first));

        now[0] = 20L;
        controller.beginWaking("host");
        assertFalse(controller.finishWaking("host", first));
        assertTrue(controller.isWaking("host"));
    }

    @Test
    public void onlineObservationClearsWakingState() {
        ConsoleHostStateController controller = new ConsoleHostStateController(() -> 1L, NO_END);
        controller.beginWaking("host");
        ComputerDetails host = new ComputerDetails();
        host.uuid = "host";
        host.state = ComputerDetails.State.ONLINE;

        assertTrue(controller.observe(host));
        assertFalse(controller.isWaking("host"));
    }

    @Test
    public void pendingRefreshKeepsLastConfirmedState() {
        ConsoleHostStateController controller = new ConsoleHostStateController(() -> 1L, NO_END);
        ComputerDetails online = host(ComputerDetails.State.ONLINE);
        controller.observe(online, true);

        ComputerDetails pending = host(ComputerDetails.State.UNKNOWN);
        controller.observe(pending, false);
        assertEquals(ComputerDetails.State.ONLINE, pending.state);

        controller.observe(host(ComputerDetails.State.OFFLINE), true);
        pending = host(ComputerDetails.State.UNKNOWN);
        controller.observe(pending, false);
        assertEquals(ComputerDetails.State.OFFLINE, pending.state);
    }

    @Test
    public void offlinePollsRightAfterAStreamKeepAnOnlineHost() {
        long[] now = {1_000L};
        Map<String, Long> ended = new HashMap<>();
        ConsoleHostStateController controller = new ConsoleHostStateController(() -> now[0],
                hostId -> ended.getOrDefault(hostId, Long.MIN_VALUE));
        controller.observe(host(ComputerDetails.State.ONLINE), true);
        assertFalse("no stream ended", controller.settling(host(ComputerDetails.State.OFFLINE)));

        ended.put("host", now[0]);
        assertTrue(controller.settling(host(ComputerDetails.State.OFFLINE)));
        assertFalse(controller.settling(host(ComputerDetails.State.ONLINE)));
        ComputerDetails other = host(ComputerDetails.State.ONLINE);
        other.uuid = "other";
        controller.observe(other, true);
        other.state = ComputerDetails.State.OFFLINE;
        assertFalse("only the host whose stream ended", controller.settling(other));

        now[0] += ConsoleHostStateController.STREAM_END_SETTLE_MS;
        assertFalse("past the window", controller.settling(host(ComputerDetails.State.OFFLINE)));

        controller.observe(host(ComputerDetails.State.OFFLINE), true);
        ended.put("host", now[0]);
        assertFalse("a host confirmed offline stays offline",
                controller.settling(host(ComputerDetails.State.OFFLINE)));
    }

    /**
     * Stream Home and the stream's own close open a fresh console after the stream ended: it
     * reads the process-wide end and its remembered state (loadKnownHosts), so it settles too.
     */
    @Test
    public void consoleCreatedAfterTheStreamEndedStillSettles() {
        long[] now = {50_000L};
        long endedAt = now[0] - 5_000L;
        ConsoleHostStateController fresh = new ConsoleHostStateController(() -> now[0],
                hostId -> "host".equals(hostId) ? endedAt : Long.MIN_VALUE);
        fresh.remember("host", ComputerDetails.State.ONLINE);

        ComputerDetails offline = host(ComputerDetails.State.OFFLINE);
        assertTrue(fresh.settling(offline));
        fresh.observe(offline, false);
        assertEquals(ComputerDetails.State.ONLINE, offline.state);
        assertEquals("no poll answered yet: checking, never offline",
                ConsoleHostPresentation.State.CHECKING, fresh.state(offline));

        now[0] = endedAt + ConsoleHostStateController.STREAM_END_SETTLE_MS;
        assertFalse(fresh.settling(host(ComputerDetails.State.OFFLINE)));
    }

    /**
     * The settle window guards only what passes through this controller. currentHost used to
     * copy the poller's raw host (UNKNOWN after the poll TTL or an invalidation, OFFLINE while
     * the streamer settles) into the shown hosts and renders read Offline after every stream;
     * the next listener call then remembered that raw state as confirmed.
     */
    @Test
    public void onlyTheControllerPathWritesTheShownHosts() {
        String source = SourceText.main("console/ConsoleActivity.java");
        int start = source.indexOf("private ComputerDetails currentHost(String hostUuid) {");
        String currentHost = source.substring(start, source.indexOf("\n    }\n", start));
        assertTrue(currentHost.contains("return hostUuid == null ? null : hosts.get(hostUuid);"));
        assertFalse(currentHost.contains("managerBinder"));

        String listener = source.substring(source.indexOf("computerListener = (details, polled)"),
                source.indexOf("private void reportHostPoll("));
        SourceText.assertOrdered(listener, "hostStateController.settling(copy)",
                "hostStateController.observe(copy, fresh);", "hosts.put(copy.uuid, copy);");
        String known = source.substring(source.indexOf("private void loadKnownHosts()"),
                source.indexOf("private ComputerDetails.State cachedConfirmedHostState("));
        SourceText.assertOrdered(known, "hostStateController.observe(host, false);",
                "hosts.put(host.uuid, host);");
        assertEquals("hosts.put only in the listener and loadKnownHosts", 2,
                source.split("hosts\\.put\\(", -1).length - 1);
    }

    private static ComputerDetails host(ComputerDetails.State state) {
        ComputerDetails host = new ComputerDetails();
        host.uuid = "host";
        host.state = state;
        return host;
    }
}
