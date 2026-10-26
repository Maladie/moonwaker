package com.limelight.session;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;
import java.util.Map;

/**
 * Persists the power command MoonWaker sent a host (sleep without a suspended game, restart or
 * shut down) until the host's real state settles it.
 */
public final class HostPowerStateStore {
    private static final String PREFS = "moonwaker_host_power_states";

    public enum Kind { SLEEP, RESTART, SHUTDOWN }

    public static final class State {
        public final Kind kind;
        /** The record's identity: when it was requested, from the host's acceptance on then. */
        public final long requestedAt;
        /** When a poll first saw the host down after the request; 0 until then. */
        public final long downObservedAt;
        /** The actor that sent the command; a restart signs it in again once the host is up. */
        public final String profileId;

        State(Kind kind, long requestedAt, long downObservedAt, String profileId) {
            this.kind = kind;
            this.requestedAt = requestedAt;
            this.downObservedAt = downObservedAt;
            this.profileId = profileId;
        }
    }

    private HostPowerStateStore() {}

    /** How long a requested sleep is awaited down after the host accepted it. */
    public static final long DOWN_TIMEOUT_MS = 30_000L;
    /**
     * How long a restart or shut down may take to go down from the host's acceptance: Windows
     * may install updates first. One never seen down (cancelled on the host) ends after it.
     */
    public static final long POWER_DOWN_TIMEOUT_MS = 5 * 60_000L;
    /** How long a restart is followed from the host's acceptance until its actor signs in. */
    public static final long POWER_CYCLE_TIMEOUT_MS = 20 * 60_000L;

    /**
     * A record, timed from the host's acceptance, past its bound (or whose follow-up died
     * with the process): an online host then shows as it is. A sleep no poll saw down within
     * twice the down wait expires; a restart or shut down not seen down within
     * {@link #POWER_DOWN_TIMEOUT_MS}, or a restart seen down but not settled within
     * {@link #POWER_CYCLE_TIMEOUT_MS}. A host seen asleep or shut down stays so while it is
     * down (the model reads no bound for it then).
     */
    public static boolean requestExpired(State state, long nowMs) {
        long bound = state.kind == Kind.SLEEP ? 2 * DOWN_TIMEOUT_MS
                : state.downObservedAt == 0L ? POWER_DOWN_TIMEOUT_MS : POWER_CYCLE_TIMEOUT_MS;
        return nowMs < state.requestedAt || nowMs - state.requestedAt > bound;
    }

    static void request(SharedPreferences prefs, String hostId, Kind kind, String profileId,
                        long requestedAt) {
        store(prefs, hostId, new State(kind, requestedAt, 0L, profileId));
    }

    static State load(Map<String, ?> stored, String hostId) {
        Object raw = stored.get(key(hostId));
        if (!(raw instanceof String) || ((String) raw).isEmpty()) return null;
        String[] parts = ((String) raw).split(":", 4);
        if (parts.length != 4) return null;
        try {
            return new State(Kind.valueOf(parts[0]), Long.parseLong(parts[1]),
                    Long.parseLong(parts[2]), parts[3]);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /**
     * The host accepted the request: the record is timed from that acceptance from now on, so
     * its bound does not include the request's own round trip. A record cleared or replaced
     * meanwhile is left alone.
     */
    static boolean accept(SharedPreferences prefs, String hostId, long requestedAt,
                          long acceptedAt) {
        State current = load(prefs.getAll(), hostId);
        if (current == null || current.requestedAt != requestedAt) return false;
        store(prefs, hostId, new State(current.kind, acceptedAt, current.downObservedAt,
                current.profileId));
        return true;
    }

    /** Marks exactly the record timed {@code requestedAt} observed down, once. */
    static State markObserved(SharedPreferences prefs, String hostId, long requestedAt) {
        State current = load(prefs.getAll(), hostId);
        if (current == null || current.requestedAt != requestedAt
                || current.downObservedAt > 0L) {
            return current;
        }
        State updated = new State(current.kind, requestedAt, System.currentTimeMillis(),
                current.profileId);
        store(prefs, hostId, updated);
        return updated;
    }

    /** Clears the record; a nonzero {@code requestedAt} clears only that exact request. */
    static boolean clearIfMatches(SharedPreferences prefs, String hostId, long requestedAt) {
        State current = load(prefs.getAll(), hostId);
        if (current == null || (requestedAt != 0L && current.requestedAt != requestedAt)) {
            return false;
        }
        prefs.edit().remove(key(hostId)).apply();
        return true;
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void store(SharedPreferences prefs, String hostId, State state) {
        prefs.edit().putString(key(hostId), state.kind.name() + ":" + state.requestedAt + ":"
                + state.downObservedAt + ":" + (state.profileId == null ? "" : state.profileId))
                .apply();
    }

    private static String key(String hostId) {
        return hostId == null ? "" : hostId.trim().toLowerCase(Locale.ROOT);
    }
}
