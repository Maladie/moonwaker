package com.limelight.console;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.limelight.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowInstrumentation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Both tester welcome pages at 1080p per language, with the checks of
 * {@link FeatureShowcaseCaptureTest} (no cut, ellipsized or escaping text) plus: the page's last
 * line stays above the legend. SHOWCASE_SHOTS_DIR writes the PNGs.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = 34)
public class TesterWelcomeCaptureTest {
    private static final String SCREEN = "-w960dp-h540dp-land-television-xhdpi";

    @Test @Config(qualifiers = "pl" + SCREEN)
    public void polish() throws IOException {
        capture("pl", false);
    }

    @Test @Config(qualifiers = "en" + SCREEN)
    public void english() throws IOException {
        capture("en", true);
    }

    @Test @Config(qualifiers = "de" + SCREEN)
    public void german() throws IOException {
        capture("de", true);
    }

    private void capture(String language, boolean pad) throws IOException {
        ShadowInstrumentation.getInstrumentation().setInTouchMode(false);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        // The longest trigger name the in-game paths can carry.
        android.preference.PreferenceManager.getDefaultSharedPreferences(activity).edit()
                .putString("overlay_trigger_button", "lb_rb").commit();
        new TesterWelcomeScreen(activity, activity.getSharedPreferences("console_test",
                Context.MODE_PRIVATE), true).show(pad, null);
        Dialog dialog = ShadowDialog.getLatestDialog();
        View root = dialog.getWindow().getDecorView();
        List<String> problems = new ArrayList<>();
        FeatureShowcaseCaptureTest.idle();
        FeatureShowcaseCaptureTest.shoot(root, "tester-" + language, "01-what", problems);
        aboveLegend(root, activity.getString(R.string.tester_welcome_closing_detail), problems);
        FeatureShowcaseCaptureTest.press(dialog, KeyEvent.KEYCODE_DPAD_CENTER, root);
        FeatureShowcaseCaptureTest.shoot(root, "tester-" + language, "02-how", problems);
        aboveLegend(root, activity.getString(R.string.tester_welcome_start), problems);
        assertTrue(language + ": " + problems, problems.isEmpty());
    }

    private static void aboveLegend(View root, String text, List<String> problems) {
        View view = find(root, text);
        if (view == null) {
            problems.add("\"" + text + "\" not shown");
            return;
        }
        int[] at = new int[2];
        view.getLocationInWindow(at);
        float density = root.getResources().getDisplayMetrics().density;
        float legendTop = (ConsoleEntryShell.LEGEND_CY_DP - ConsoleEntryShell.LEGEND_HEIGHT_DP / 2f)
                * density;
        if (at[1] + view.getHeight() > legendTop) {
            problems.add("\"" + text + "\" ends at " + (at[1] + view.getHeight())
                    + " px, under the legend at " + legendTop);
        }
    }

    private static View find(View view, String text) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) {
            return view;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            View found = find(group.getChildAt(index), text);
            if (found != null) return found;
        }
        return null;
    }
}
