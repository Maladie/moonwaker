package com.limelight.console;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PinnedGamesTest {
    /** A minimal in-memory SharedPreferences: only what PinnedGames reads and writes. */
    private static SharedPreferences memory() {
        Map<String, Object> values = new HashMap<>();
        return (SharedPreferences) java.lang.reflect.Proxy.newProxyInstance(
                PinnedGamesTest.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getBoolean":
                        case "getStringSet":
                            return values.containsKey(args[0]) ? values.get(args[0]) : args[1];
                        case "edit":
                            return editor(values);
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    private static SharedPreferences.Editor editor(Map<String, Object> values) {
        return (SharedPreferences.Editor) java.lang.reflect.Proxy.newProxyInstance(
                PinnedGamesTest.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.Editor.class}, (proxy, method, args) -> {
                    if (method.getName().startsWith("put")) values.put((String) args[0], args[1]);
                    return method.getName().equals("apply") ? null : proxy;
                });
    }

    @Test public void pinsAreKeptPerHostAndProfile() {
        SharedPreferences p = memory();
        PinnedGames.set(p, "Host", "kid", "g1", true);
        assertEquals(Collections.singleton("g1"), PinnedGames.ids(p, "host", "kid"));
        assertTrue(PinnedGames.ids(p, "host", "parent").isEmpty());
        assertTrue(PinnedGames.ids(p, "other", "kid").isEmpty());
        PinnedGames.set(p, "host", "kid", "g1", false);
        assertTrue(PinnedGames.ids(p, "host", "kid").isEmpty());
    }

    @Test public void switchingPinsOffHidesButKeepsThem() {
        SharedPreferences p = memory();
        assertTrue(PinnedGames.pinsEnabled(p));
        assertTrue(PinnedGames.relativePlayed(p));
        PinnedGames.set(p, "h", "p", "g1", true);
        PinnedGames.setPinsEnabled(p, false);
        assertFalse(PinnedGames.pinsEnabled(p));
        assertTrue(PinnedGames.ids(p, "h", "p").isEmpty());
        PinnedGames.setPinsEnabled(p, true);
        Set<String> expected = new HashSet<>(Collections.singleton("g1"));
        assertEquals(expected, PinnedGames.ids(p, "h", "p"));
    }
}
