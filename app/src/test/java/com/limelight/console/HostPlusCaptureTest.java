package com.limelight.console;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;

import com.limelight.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowInstrumentation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A free host (no Host Plus) on the TV at 1080p per language: the game card's Install off with
 * its note, and the game mode and freeze-on-switch rows off with theirs, with the checks of
 * {@link FeatureShowcaseCaptureTest}. SHOWCASE_SHOTS_DIR writes the PNGs.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = 34)
public class HostPlusCaptureTest {
    private static final String SCREEN = "-w960dp-h540dp-land-television-xhdpi";

    @Test @Config(qualifiers = "pl" + SCREEN)
    public void polish() throws Exception {
        capture("pl");
    }

    @Test @Config(qualifiers = "en" + SCREEN)
    public void english() throws Exception {
        capture("en");
    }

    @Test @Config(qualifiers = "de" + SCREEN)
    public void german() throws Exception {
        capture("de");
    }

    private void capture(String language) throws Exception {
        ShadowInstrumentation.getInstrumentation().setInTouchMode(false);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<String> problems = new ArrayList<>();
        String required = activity.getString(R.string.host_plus_required);

        ConsoleGameCard.Facts facts = new ConsoleGameCard.Facts();
        facts.canInstall = true;
        facts.plusRequired = required;
        View[][] card = {null};
        AppUpdateCaptureTest.sidePanel(activity, "plus-" + language, "01-game-card",
                activity.getString(R.string.playnite_game_options), "Cyberpunk 2077",
                "Steam", problems, panel -> card[0] = ConsoleGameCard.menu(facts)
                        .rows(activity, panel, action -> action.icon, action -> { }));
        assertFalse("Install is off", card[0][0].isEnabled());

        ConsoleSettingsScreen settings = new ConsoleSettingsScreen(activity, true, false);
        settings.show(Arrays.asList(new ConsoleSettingsScreen.Category(
                activity.getString(R.string.console_settings_about), panel -> {
                    // As ConsoleActivity builds them for a host without Host Plus.
                    LinearLayout freeze = panel.toggle(
                            activity.getString(R.string.console_freeze_on_switch), false);
                    panel.disabled(freeze, required);
                    LinearLayout gameMode = panel.toggle(
                            activity.getString(R.string.console_game_mode_label), false);
                    panel.disabled(gameMode, required);
                    return new View[]{
                            panel.navigation(activity.getString(R.string.console_options_help)),
                            freeze,
                            panel.note(activity.getString(
                                    R.string.console_freeze_on_switch_description)),
                            panel.section(activity.getString(R.string.console_game_mode_label)),
                            gameMode};
                })), 0);
        FeatureShowcaseCaptureTest.idle();
        FeatureShowcaseCaptureTest.shoot(settings.window().getDecorView(), "plus-" + language,
                "02-settings", problems);
        settings.dismiss();
        assertTrue(language + ": " + problems, problems.isEmpty());
    }
}
