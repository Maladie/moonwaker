package com.limelight.console;

import static com.limelight.console.ConsoleAppStreamSettings.Key;
import static com.limelight.console.ConsoleAppStreamSettings.Origin;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.limelight.preferences.AppPreferences.AppSettings;

import org.junit.Test;

public class ConsoleAppStreamOriginTest {
    private static AppSettings overriding() {
        AppSettings s = new AppSettings();
        s.useGlobalSettings = false;
        s.resolution = "1920x1080";
        s.fps = 60;
        return s;
    }

    @Test public void gameOverrideWinsOverParentAndDefault() {
        AppSettings parent = overriding();
        assertEquals(Origin.GAME, ConsoleAppStreamSettings.origin(Key.FPS, overriding(), parent, true));
    }

    @Test public void parentThenDefaultThenBuiltIn() {
        AppSettings game = new AppSettings();
        game.useGlobalSettings = false;
        assertEquals(Origin.APP, ConsoleAppStreamSettings.origin(Key.FPS, game, overriding(), true));
        assertEquals(Origin.DEFAULT, ConsoleAppStreamSettings.origin(Key.HDR, game, overriding(), true));
        assertEquals(Origin.DEFAULT, ConsoleAppStreamSettings.origin(Key.FPS, game, null, true));
        assertEquals(Origin.BUILT_IN, ConsoleAppStreamSettings.origin(Key.REFRESH_RATE, game, null, false));
    }

    @Test public void useGlobalIgnoresSavedOverrides() {
        AppSettings game = overriding();
        game.useGlobalSettings = true;
        assertEquals(Origin.DEFAULT, ConsoleAppStreamSettings.origin(Key.FPS, game, null, true));
        AppSettings parent = overriding();
        parent.useGlobalSettings = true;
        assertEquals(Origin.DEFAULT, ConsoleAppStreamSettings.origin(Key.FPS, new AppSettings(), parent, true));
    }

    @Test public void resetClearsOnlyThatKey() {
        AppSettings s = overriding();
        s.bitrate = 20000;
        s.enableHdr = "true";
        for (Key key : Key.values()) {
            AppSettings copy = overriding();
            copy.bitrate = 20000;
            copy.enableHdr = "true";
            copy.framePacing = "balanced";
            copy.actualDisplayRefreshRate = 59.94;
            copy.enablePerfOverlay = "false";
            copy.audioHaptics = "game";
            copy.audioHapticsStrength = 150;
            int before = 0;
            for (Key k : Key.values()) if (ConsoleAppStreamSettings.hasValue(copy, k)) before++;
            ConsoleAppStreamSettings.reset(copy, key);
            int after = 0;
            for (Key k : Key.values()) if (ConsoleAppStreamSettings.hasValue(copy, k)) after++;
            assertFalse(ConsoleAppStreamSettings.hasValue(copy, key));
            assertEquals(before - 1, after);
            assertFalse(copy.useGlobalSettings);
        }
        assertTrue(ConsoleAppStreamSettings.hasValue(s, Key.BITRATE));
    }
}
