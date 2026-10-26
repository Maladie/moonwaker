package com.limelight.console;

import com.limelight.nvstream.http.NvApp;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ConsoleAppListsTest {
    private final List<String> parsed = new ArrayList<>();
    private final List<String> cacheReads = new ArrayList<>();
    private String cached = "";

    /** Raw "b,A,c" parses to apps named b, A, c with ids 1, 2, 3. */
    private final ConsoleAppLists lists = new ConsoleAppLists(new ConsoleAppLists.Source() {
        @Override public String cachedRaw(String hostUuid) {
            cacheReads.add(hostUuid);
            return cached;
        }

        @Override public List<NvApp> parse(String raw) {
            parsed.add(raw);
            List<NvApp> apps = new ArrayList<>();
            String[] names = raw.split(",");
            for (int index = 0; index < names.length; index++) {
                apps.add(new NvApp(names[index], index + 1, false));
            }
            return apps;
        }
    });

    @Test public void parsesOncePerRawListAndSortsByName() {
        assertTrue(lists.apps("host").isEmpty());
        assertTrue(lists.observe("host", "b,A,c"));

        List<NvApp> first = lists.load("host");
        assertEquals(Arrays.asList("A", "b", "c"), names(first));
        assertFalse(lists.observe("host", new String("b,A,c")));
        assertSame(first, lists.load("host"));
        assertSame(first, lists.apps("host"));
        assertEquals(Collections.singletonList("b,A,c"), parsed);

        assertTrue(lists.observe("host", "z"));
        assertEquals(Collections.singletonList("z"), names(lists.load("host")));
        assertEquals(2, parsed.size());
        assertTrue(cacheReads.isEmpty());
    }

    @Test public void theCacheStandsInOnceUntilTheHostSendsAList() {
        cached = "c,a";
        assertTrue(lists.observe("host", null));
        assertEquals(Arrays.asList("a", "c"), names(lists.load("host")));
        // Without a raw list the memo answers; the cache is not read again.
        assertFalse(lists.observe("host", ""));
        lists.load("host");
        assertEquals(Collections.singletonList("host"), cacheReads);
        // The host's same list is not parsed again; a new one is.
        assertFalse(lists.observe("host", "c,a"));
        assertEquals(1, parsed.size());
        lists.observe("host", "d");
        lists.load("host");
        assertEquals(Collections.singletonList("d"), names(lists.apps("host")));
    }

    /** Loads queued for older lists, or running late, never move the memo back. */
    @Test public void outOfOrderLoadsKeepTheLatestList() {
        lists.observe("host", "old");
        lists.observe("host", "new");
        // The load queued for "old" runs first: it loads the latest list.
        assertEquals(Collections.singletonList("new"), names(lists.load("host")));
        // An empty report keeps the latest list; the load queued for "new" changes nothing.
        assertFalse(lists.observe("host", ""));
        lists.load("host");
        assertEquals(Collections.singletonList("new"), names(lists.apps("host")));
        assertEquals(Collections.singletonList("new"), parsed);
    }

    @Test public void aMissingHostIsNeverStale() {
        assertFalse(lists.observe(null, "a"));
        assertTrue(lists.apps(null).isEmpty());
    }

    @Test public void hiddenAppsAreFilteredById() {
        List<NvApp> all = loaded("host", "a,b,c");
        assertSame(all, ConsoleAppLists.withoutHidden(all, Collections.emptySet()));
        assertEquals(Arrays.asList("a", "c"), names(ConsoleAppLists.withoutHidden(all,
                new HashSet<>(Collections.singletonList("2")))));
    }

    private List<NvApp> loaded(String host, String raw) {
        lists.observe(host, raw);
        return lists.load(host);
    }

    private static List<String> names(List<NvApp> apps) {
        List<String> names = new ArrayList<>();
        for (NvApp app : apps) names.add(app.getAppName());
        return names;
    }
}
