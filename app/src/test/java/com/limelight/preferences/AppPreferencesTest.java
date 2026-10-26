package com.limelight.preferences;

import com.limelight.binding.input.GyroAim;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class AppPreferencesTest {
    @Test public void streamSettingsCopyPreservesUnrelatedAppSettings() {
        AppPreferences.AppSettings current = new AppPreferences.AppSettings(
                "1280x720", 30, "smoothness", 10_000, 119.88,
                "true", "false", true);
        current.audioHaptics = "music";
        current.audioHapticsStrength = 150;

        AppPreferences.AppSettings updated = AppPreferences.copyWithStreamSettings(
                current, 2560, 1440, 120, 40_000);

        assertEquals("2560x1440", updated.resolution);
        assertEquals(120, updated.fps);
        assertEquals(40_000, updated.bitrate);
        assertFalse(updated.useGlobalSettings);
        assertEquals("smoothness", updated.framePacing);
        assertEquals(119.88, updated.actualDisplayRefreshRate, 0);
        assertEquals("true", updated.enableHdr);
        assertEquals("false", updated.enablePerfOverlay);
        assertEquals("music", updated.audioHaptics);
        assertEquals(150, updated.audioHapticsStrength);
    }

    @Test public void gyroResolvesQuickLaunchThenAppThenDefaults() throws Exception {
        GyroAim.Settings global = GyroAim.Settings.DEFAULT.toBuilder()
                .activation(GyroAim.Activation.ALWAYS).build();
        AppPreferences.AppSettings app = new AppPreferences.AppSettings();
        assertSame(global, AppPreferences.effectiveGyro(null, app, global));

        app.gyro = new JSONObject().put("aim", true).put("output", "STICK");
        GyroAim.Settings fromApp = AppPreferences.effectiveGyro(
                new AppPreferences.AppSettings(), app, global);
        assertTrue(fromApp.aim);
        assertEquals(GyroAim.Output.STICK, fromApp.output);
        // Keys the game never set keep the global value.
        assertEquals(GyroAim.Activation.ALWAYS, fromApp.activation);
        assertEquals(GyroAim.Settings.DEFAULT.stickDeadzone, fromApp.stickDeadzone, 0);

        AppPreferences.AppSettings quick = new AppPreferences.AppSettings();
        quick.gyro = AppPreferences.gyroToJson(GyroAim.Settings.DEFAULT);
        assertFalse(AppPreferences.effectiveGyro(quick, app, global).aim);
    }

    @Test public void gyroSurvivesJsonAndStreamSettingsCopy() throws Exception {
        AppPreferences.AppSettings current = new AppPreferences.AppSettings();
        current.gyro = AppPreferences.gyroToJson(new GyroAim.Builder().aim(true)
                .output(GyroAim.Output.STICK).activation(GyroAim.Activation.TOGGLE)
                .button(GyroAim.Button.R3).buttonGyroOnly(true).sensX(2.5f).sensY(0.5f)
                .invertX(true).axis(GyroAim.Axis.ROLL).stickDeadzone(0.22f)
                .acceleration(GyroAim.Acceleration.HIGH).stickPausesGyro(true).stickCurve(2.2f)
                .stickTurnSpeed(420).mouseCountsPer360(7200)
                .build());
        AppPreferences.AppSettings parsed = AppPreferences.AppSettings.fromJson(current.toJson());
        AppPreferences.AppSettings copied = AppPreferences.copyWithStreamSettings(
                parsed, 1280, 720, 60, 10_000);
        GyroAim.Settings g = AppPreferences.gyroFromJson(copied.gyro, GyroAim.Settings.DEFAULT);
        assertEquals(GyroAim.Activation.TOGGLE, g.activation);
        assertEquals(GyroAim.Button.R3, g.button);
        assertTrue(g.buttonGyroOnly);
        assertEquals(2.5f, g.sensX, 1e-6f);
        assertTrue(g.invertX);
        assertEquals(GyroAim.Axis.ROLL, g.axis);
        assertEquals(0.22f, g.stickDeadzone, 1e-6f);
        assertEquals(GyroAim.Acceleration.HIGH, g.acceleration);
        assertTrue(g.stickPausesGyro);
        assertEquals(2.2f, g.stickCurve, 1e-6f);
        assertEquals(420, g.stickTurnSpeed);
        assertEquals(7200, g.mouseCountsPer360);
    }

    @Test public void streamSettingsKeyIsTheKeyConsoleOpensForTheRunningGame() {
        String game = AppPreferences.playniteGameKey(" HOST ", " game-1 ");
        assertEquals("playnite:HOST:game-1", game);
        // A Playnite game owns its key on any target, over a stale launch-time Quick Launch key.
        assertEquals(game, AppPreferences.streamSettingsKey("HOST", 7, "game-1",
                "playnite:HOST:old-game", true));
        assertEquals(game, AppPreferences.streamSettingsKey("HOST", 7, "game-1", null, false));
        // The neutral target without a game keeps nothing: its app is every game's parent.
        assertNull(AppPreferences.streamSettingsKey("HOST", 7, "", "playnite:HOST:old-game",
                true));
        assertEquals("HOST:7:123", AppPreferences.streamSettingsKey("HOST", 7, "",
                "HOST:7:123", false));
        assertEquals("HOST:7", AppPreferences.streamSettingsKey("HOST", 7, null, null, false));
    }

    @Test public void audioHapticsOwnerIsTheLayerConsoleShows() {
        AppPreferences.AppSettings app = new AppPreferences.AppSettings();
        AppPreferences.AppSettings game = new AppPreferences.AppSettings();
        assertNull(AppPreferences.audioHapticsOwner("H:1", app, "playnite:H:g", game,
                AppPreferences.HAPTICS_MODE));
        // A value kept under "use global settings" decides nothing.
        game.audioHaptics = "music";
        app.audioHaptics = "game";
        assertNull(AppPreferences.audioHapticsOwner("H:1", app, "playnite:H:g", game,
                AppPreferences.HAPTICS_MODE));
        app.useGlobalSettings = false;
        assertEquals("H:1", AppPreferences.audioHapticsOwner("H:1", app, "playnite:H:g", game,
                AppPreferences.HAPTICS_MODE));
        assertEquals("H:1", AppPreferences.audioHapticsOwner("H:1", app, null, null,
                AppPreferences.HAPTICS_MODE));
        game.useGlobalSettings = false;
        assertEquals("playnite:H:g",
                AppPreferences.audioHapticsOwner("H:1", app, "playnite:H:g", game,
                AppPreferences.HAPTICS_MODE));
        // Strength has its own owner: no layer set one, so Settings › Controllers keeps it.
        assertNull(AppPreferences.audioHapticsOwner("H:1", app, "playnite:H:g", game,
                AppPreferences.HAPTICS_STRENGTH));
        app.audioHapticsStrength = 150;
        assertEquals("H:1", AppPreferences.audioHapticsOwner("H:1", app, "playnite:H:g", game,
                AppPreferences.HAPTICS_STRENGTH));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBitrateBelowMinimum() {
        AppPreferences.copyWithStreamSettings(
                new AppPreferences.AppSettings(), 1280, 720, 60, 499);
    }
}
