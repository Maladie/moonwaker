package com.limelight.console;

import com.limelight.nvstream.http.NvApp;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The owner of each host's parsed app list: every app, sorted by name, parsed on a worker
 * once per change of the host's raw list (its cache file stands in until the host sends
 * one). Any thread reads the memo; only {@link #load} reads disk or parses, always for the
 * host's latest raw list.
 */
final class ConsoleAppLists {
    /** Worker-thread access to the raw list. */
    interface Source {
        /** The host's cached raw list; "" or null when there is none. */
        String cachedRaw(String hostUuid) throws IOException;

        List<NvApp> parse(String raw) throws IOException, XmlPullParserException;
    }

    private static final class Entry {
        final String raw;
        final List<NvApp> apps;

        Entry(String raw, List<NvApp> apps) {
            this.raw = raw;
            this.apps = apps;
        }
    }

    private final Source source;
    private final Map<String, Entry> byHost = new ConcurrentHashMap<>();
    /** Each host's latest non-empty raw list, recorded on the main thread. */
    private final Map<String, String> latestRaw = new ConcurrentHashMap<>();

    ConsoleAppLists(Source source) {
        this.source = source;
    }

    /** Any thread: the host's last loaded list, empty before its first {@link #load}. */
    List<NvApp> apps(String hostUuid) {
        Entry entry = hostUuid == null ? null : byHost.get(hostUuid);
        return entry == null ? Collections.emptyList() : entry.apps;
    }

    /**
     * Main thread: records the host's latest raw list (null or "" while it has sent none, which
     * keeps the one recorded before); true when {@link #load} would read or parse for it.
     */
    boolean observe(String hostUuid, String raw) {
        if (hostUuid == null) return false;
        if (raw != null && !raw.isEmpty()) latestRaw.put(hostUuid, raw);
        return stale(hostUuid, latestRaw.get(hostUuid));
    }

    private boolean stale(String hostUuid, String raw) {
        Entry entry = byHost.get(hostUuid);
        return entry == null || (raw != null && !raw.equals(entry.raw));
    }

    /**
     * Worker thread: the host's list for its latest {@link #observe observed} raw list (its
     * cache file before it sent one), the same instance until that raw list changes. A load
     * queued for an older raw list loads the latest instead, so the memo never moves back.
     */
    synchronized List<NvApp> load(String hostUuid) {
        String raw = latestRaw.get(hostUuid);
        if (!stale(hostUuid, raw)) return byHost.get(hostUuid).apps;
        if (raw == null) {
            try {
                raw = source.cachedRaw(hostUuid);
            } catch (IOException missing) {
                raw = null;
            }
        }
        if (raw == null) raw = "";
        List<NvApp> apps;
        try {
            apps = raw.isEmpty() ? new ArrayList<>() : new ArrayList<>(source.parse(raw));
        } catch (IOException | XmlPullParserException invalid) {
            apps = new ArrayList<>();
        }
        apps.sort(Comparator.comparing(NvApp::getAppName, String.CASE_INSENSITIVE_ORDER));
        Entry entry = new Entry(raw, Collections.unmodifiableList(apps));
        byHost.put(hostUuid, entry);
        return entry.apps;
    }

    /** {@code apps} without those whose id is in {@code hidden}. */
    static List<NvApp> withoutHidden(List<NvApp> apps, Set<String> hidden) {
        if (hidden.isEmpty()) return apps;
        List<NvApp> visible = new ArrayList<>(apps.size());
        for (NvApp app : apps) {
            if (!hidden.contains(String.valueOf(app.getAppId()))) visible.add(app);
        }
        return Collections.unmodifiableList(visible);
    }
}
