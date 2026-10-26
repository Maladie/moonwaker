package com.limelight.console;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Mockup 23a's age stops, and Settings building a category's rows only once focus rests. */
@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
public class ConsoleSettingsStepsTest {
    @Test public void stopsStepOneStopPerPressWithoutOkAndLeaveAtTheEnds() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ConsolePanel panel = new ConsolePanel(activity, new LockableScrollView(activity),
                new ConsoleMenuBackdrop(activity, false), true);
        List<Integer> chosen = new ArrayList<>();
        View row = panel.stops("Age", 0, new String[]{"Manual", "3+", "7+", "12+", "16+",
                "All"}, index -> "v" + index, "Category", chosen::add);
        activity.setContentView(row);
        row.requestFocus();
        ShadowLooper.idleMainLooper();
        assertTrue(ConsolePanel.isDirectStepper(row));

        // Right steps at once, without OK, one stop even on a repeat.
        assertTrue(key(row, KeyEvent.KEYCODE_DPAD_RIGHT, 0));
        assertTrue(key(row, KeyEvent.KEYCODE_DPAD_RIGHT, 8));
        assertEquals(Arrays.asList(1, 2), chosen);
        // OK starts no editing mode, so Back and left keep their meaning.
        row.performClick();
        assertFalse(ConsolePanel.isEditing(row));
        // At the first stop a new press of left navigates; a held one stops there.
        assertTrue(key(row, KeyEvent.KEYCODE_DPAD_LEFT, 0));
        assertTrue(key(row, KeyEvent.KEYCODE_DPAD_LEFT, 0));
        assertTrue(key(row, KeyEvent.KEYCODE_DPAD_LEFT, 3));
        assertFalse(key(row, KeyEvent.KEYCODE_DPAD_LEFT, 0));
        assertEquals(Arrays.asList(1, 2, 1, 0), chosen);
    }

    @Test public void aCategoryBuildsItsRowsOnceTheFocusRests() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ConsoleSettingsScreen screen = new ConsoleSettingsScreen(activity, true, false);
        int[] built = new int[3];
        List<ConsoleSettingsScreen.Category> categories = new ArrayList<>();
        for (int index = 0; index < built.length; index++) {
            int category = index;
            categories.add(new ConsoleSettingsScreen.Category("C" + index, panel -> {
                built[category]++;
                return new View[]{panel.action("row " + category)};
            }));
        }
        screen.show(categories, 0);
        ShadowLooper.idleMainLooper();
        assertArrayEquals(new int[]{1, 0, 0}, built);

        View root = screen.window().getDecorView();
        categoryRow(root, "C1").requestFocus();
        categoryRow(root, "C2").requestFocus();
        assertArrayEquals(new int[]{1, 0, 0}, built);
        ShadowLooper.idleMainLooper(
                ConsoleSettingsScreen.CATEGORY_RENDER_DELAY_MS, TimeUnit.MILLISECONDS);
        // Passing over C1 built nothing; resting on C2 built it once.
        assertArrayEquals(new int[]{1, 0, 1}, built);
        screen.dismiss();
    }

    private static boolean key(View row, int keyCode, int repeat) {
        KeyEvent down = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, repeat);
        return row.dispatchKeyEvent(down);
    }

    private static View categoryRow(View view, String title) {
        if (view instanceof TextView && title.equals(((TextView) view).getText().toString())
                && view.isFocusable()) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = categoryRow(group.getChildAt(index), title);
                if (found != null) return found;
            }
        }
        return null;
    }
}
