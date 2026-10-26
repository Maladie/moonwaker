package com.limelight.console;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = 34, qualifiers = "pl")
public class TesterWelcomeTest {
    private Activity activity;
    private SharedPreferences preferences;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        preferences = activity.getSharedPreferences("console_test", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
    }

    @Test public void neverDueOutsideATesterBuild() {
        assertFalse(TesterWelcomeScreen.isDue(false, preferences));
    }

    @Test public void dueOnceInATesterBuild() {
        assertTrue(TesterWelcomeScreen.isDue(true, preferences));
        int[] closed = {0};
        TesterWelcomeScreen screen = new TesterWelcomeScreen(activity, preferences, true);
        screen.show(false, () -> closed[0]++);
        assertTrue(screen.isShowing());
        assertFalse(TesterWelcomeScreen.isDue(true, preferences));
        // OK to page 2, OK on "Let's go" closes and runs what comes next exactly once.
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(screen.isShowing());
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(screen.isShowing());
        assertEquals(1, closed[0]);
        assertFalse(TesterWelcomeScreen.isDue(true, preferences));
    }

    @Test public void backSkipsFromPageOneAndReturnsFromPageTwo() {
        TesterWelcomeScreen screen = new TesterWelcomeScreen(activity, preferences, true);
        screen.show(true, null);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        press(KeyEvent.KEYCODE_BACK);
        assertTrue(screen.isShowing());
        press(KeyEvent.KEYCODE_BACK);
        assertFalse(screen.isShowing());
    }

    @Test public void settingsRowAndBadgeOnlyInATesterBuild() {
        ConsolePanel panel = new ConsolePanel(activity, new LockableScrollView(activity),
                new ConsoleMenuBackdrop(activity, false), true);
        assertNull(TesterWelcomeScreen.settingsRow(panel, activity, false, () -> { }));
        boolean[] opened = {false};
        TextView row = TesterWelcomeScreen.settingsRow(panel, activity, true,
                () -> opened[0] = true);
        assertNotNull(row);
        assertEquals("Powitanie testera", row.getText().toString());
        row.performClick();
        assertTrue(opened[0]);
        assertFalse(TesterWelcomeScreen.versionNote(activity, false).toString()
                .contains("TESTER"));
        assertTrue(TesterWelcomeScreen.versionNote(activity, true).toString()
                .endsWith("TESTER"));
    }

    private static void press(int keyCode) {
        FeatureShowcaseCaptureTest.press(ShadowDialog.getLatestDialog(), keyCode,
                ShadowDialog.getLatestDialog().getWindow().getDecorView());
    }
}
