package com.limelight.console;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Activity;
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
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Mockup 23f: a saved switch keeps the Gateway's result, so reverting it reads as the host will. */
@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
public class ConsoleChildGameSharingPanelTest {
    @Test public void aSavedHideRevertsToTheCategoryNotToAManualGrant() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ConsolePanel consolePanel = new ConsolePanel(activity, new LockableScrollView(activity),
                new ConsoleMenuBackdrop(activity, false), true);
        ConsoleChildGameSharingPanel panel = new ConsoleChildGameSharingPanel(activity,
                consolePanel);
        List<ConsoleChildGameSharingPanel.Draft> saves = new ArrayList<>();
        List<ConsoleChildGameSharingPanel.Completion> completions = new ArrayList<>();
        // Shared by hand inside Ola's 16+: switching it off hides it by hand (3.4).
        panel.show(null, 12, Collections.singletonList(new ConsoleChildGameSharingPanel
                        .ChildOption("ola", "Ola", "16", ChildGameAccess.State.GRANTED)), 3,
                (draft, completion) -> {
                    saves.add(draft);
                    completions.add(completion);
                });
        View row = find(panel, "settings.toggle:Ola");
        assertEquals(activity.getString(R.string.child_game_state_granted), line(row));

        row.performClick();
        ShadowLooper.idleMainLooper(6, TimeUnit.SECONDS);
        assertEquals(1, saves.size());
        assertEquals(Collections.emptyList(), saves.get(0).selectedChildIds);
        // sharing/get's min_age goes back as it came.
        assertEquals(Integer.valueOf(12), saves.get(0).expectedMinAge);
        completions.get(0).onSaved(4);
        assertEquals(activity.getString(R.string.child_game_state_blocked_within, "16+"),
                line(row));

        row.performClick();
        assertEquals(activity.getString(R.string.child_game_state_auto, "16+"), line(row));
    }

    private static String line(View row) {
        View title = row;
        while (!(title instanceof TextView)) title = ((ViewGroup) title).getChildAt(0);
        String text = ((TextView) title).getText().toString();
        return text.substring(text.indexOf('\n') + 1);
    }

    private static View find(ViewGroup parent, String tag) {
        for (int index = 0; index < parent.getChildCount(); index++) {
            View child = parent.getChildAt(index);
            if (tag.equals(child.getTag())) return child;
        }
        assertNotNull(tag, null);
        return null;
    }
}
