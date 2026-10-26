package com.limelight.console;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.gateway.GatewayConnection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowDialog;

/** The report card: no category is chosen for the tester, and it closes with its Activity. */
@RunWith(RobolectricTestRunner.class)
// Robolectric's Conscrypt provider would stay JVM-wide and stall later plain TLS tests.
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
public class ProblemReportDialogTest {
    private static final GatewayConnection HOST = new GatewayConnection(
            "https://192.0.2.10:8785", "token", "ab".repeat(32), "default");

    @Test public void sendWaitsForAChosenCategory() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        ProblemReportDialog.show(activity, HOST);
        Dialog dialog = ShadowDialog.getLatestDialog();
        View root = dialog.getWindow().getDecorView();
        TextView send = require(root, activity.getString(R.string.problem_report_send));
        TextView stream = require(root, activity.getString(R.string.problem_report_category_stream));

        // Nothing chosen and Send out of the focus order: focus starts on the categories.
        assertFalse(send.isEnabled() || send.isFocusable());
        assertTrue(stream.getText().toString().equals(
                activity.getString(R.string.problem_report_category_stream)));
        stream.performClick();
        assertTrue(send.isEnabled() && send.isFocusable());
        assertTrue(stream.getText().toString().startsWith("✓"));
        dialog.dismiss();
    }

    @Test public void theCardClosesWhenItsActivityFinishes() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        ProblemReportDialog.show(controller.get(), HOST);
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertTrue(dialog.isShowing());
        controller.get().finish();
        controller.pause();
        assertFalse(dialog.isShowing());
    }

    @Test public void theCardClosesWhenItsActivityIsDestroyed() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        ProblemReportDialog.show(controller.get(), null);
        Dialog dialog = ShadowDialog.getLatestDialog();
        controller.pause().stop().destroy();
        assertFalse(dialog.isShowing());
    }

    private static TextView find(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView found = find(group.getChildAt(index), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextView require(View root, String text) {
        TextView found = find(root, text);
        assertNotNull(text, found);
        return found;
    }
}
