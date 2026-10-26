package com.limelight.console;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowInstrumentation;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Game mode's window at 1080p (960 x 540 dp) per language, against mockups 24a-24d: the
 * skeleton, the loaded list with "Close and play" focused, a focused unchecked tile and the
 * closing state. Fails on cut text outside the program names (those ellipsize by design).
 * With GAME_MODE_SHOTS_DIR set it writes each state there as a PNG.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = 34)
public class GameModeWindowCaptureTest {
    private static final String SCREEN = "-w960dp-h540dp-land-television-xhdpi";

    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    @Test @Config(qualifiers = "pl" + SCREEN)
    public void polish() throws IOException {
        capture("pl");
    }

    @Test @Config(qualifiers = "en" + SCREEN)
    public void english() throws IOException {
        capture("en");
    }

    @Test @Config(qualifiers = "de" + SCREEN)
    public void german() throws IOException {
        capture("de");
    }

    private void capture(String language) throws IOException {
        ShadowInstrumentation.getInstrumentation().setInTouchMode(false);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<List<String>> closed = new ArrayList<>();
        List<String> forced = new ArrayList<>();
        int[] skipped = {0};
        GameModeWindow window = GameModeWindow.show(activity, "Dead Space",
                artwork("cover.png", 216, 288, 0xFF3A2A22, 0xFF0C0C10),
                artwork("background.png", 640, 360, 0xFF5A3A2C, 0xFF101420), true, true,
                new GameModeWindow.Listener() {
                    @Override public void closeAndPlay(List<String> programIds) {
                        closed.add(programIds);
                    }

                    @Override public void playWithoutClosing() {
                        skipped[0]++;
                    }

                    @Override public void forceClose(String programId, String name) {
                        forced.add(programId + "=" + name);
                    }
                }, Runnable::run);
        Dialog dialog = ShadowDialog.getLatestDialog();
        View root = dialog.getWindow().getDecorView();
        List<String> problems = new ArrayList<>();
        FeatureShowcaseCaptureTest.idle();
        shoot(root, language, "24b-skeleton", problems);

        window.showPrograms(Arrays.asList(
                program("10-100", "YouTube — Mozilla Firefox", "firefox.exe", 0xFFE8742C),
                program("11-100", "Spotify Premium", "spotify.exe", 0xFF2CC46A),
                program("12-100", "Dokument1 — Word", "winword.exe", 0xFF2C64D8),
                new GameModeStep.Program("13-100", "Kalkulator budżetu", "budget.exe",
                        new byte[0]),
                program("14-100", "OBS Studio 30.2", "obs64.exe", 0xFF6A4AC8)));
        FeatureShowcaseCaptureTest.idle();
        shoot(root, language, "24a-loaded", problems);
        TextView counter = text(root, "5");
        assertTrue(language + ": counter " + counter.getText(), counter != null);

        View obs = tile(root, "OBS Studio 30.2");
        obs.requestFocus();
        obs.performClick();
        FeatureShowcaseCaptureTest.idle();
        shoot(root, language, "24c-tile", problems);
        assertTrue(language + ": counter after uncheck", text(root, "4") != null);

        View close = actionFor(root, activity.getString(
                com.limelight.R.string.console_game_mode_close_and_play));
        close.performClick();
        FeatureShowcaseCaptureTest.idle();
        shoot(root, language, "24d-closing", problems);
        // Programs are closing: Back no longer cancels the start.
        FeatureShowcaseCaptureTest.press(dialog, android.view.KeyEvent.KEYCODE_BACK, root);
        assertTrue(window.isShowing());
        assertEquals(1, closed.size());
        assertEquals(Arrays.asList("10-100", "11-100", "12-100", "13-100"), closed.get(0));
        assertFalse(close.isShown());
        assertEquals(0, skipped[0]);

        // Mockup 24i: Word asks to save (still open), the calculator hangs.
        window.showResult(Arrays.asList(
                new GameModeStep.Remaining("12-100", "Dokument1 — Word", true),
                new GameModeStep.Remaining("13-100", "Kalkulator budżetu", false)));
        FeatureShowcaseCaptureTest.idle();
        shoot(root, language, "24i-result", problems);
        View hung = tile(root, "Kalkulator budżetu");
        assertTrue(hung.isFocused());
        assertFalse(tile(root, "Dokument1 — Word").isFocusable());
        hung.performClick();
        assertEquals(Arrays.asList("13-100=Kalkulator budżetu"), forced);
        window.showForceResult("13-100", "closed");
        FeatureShowcaseCaptureTest.idle();
        assertFalse(hung.isFocusable());
        View done = actionFor(root, activity.getString(com.limelight.R.string.console_game_mode_done));
        assertTrue(done.isFocused());

        // Mockup 24j: the destructive confirmation starts on Cancel.
        int[] confirmed = {0};
        Dialog confirm = ConsoleConfirmDialog.showDestructive(activity,
                activity.getString(com.limelight.R.string.console_game_mode_force_title,
                        "Kalkulator budżetu"),
                activity.getString(com.limelight.R.string.console_game_mode_force_message,
                        "Kalkulator budżetu"),
                activity.getString(com.limelight.R.string.console_cancel),
                activity.getString(com.limelight.R.string.console_game_mode_force_close),
                () -> confirmed[0]++, null, null);
        FeatureShowcaseCaptureTest.idle();
        View focused = confirm.getCurrentFocus();
        assertEquals(activity.getString(com.limelight.R.string.console_cancel),
                ((TextView) focused).getText().toString());
        focused.performClick();
        assertEquals(0, confirmed[0]);
        assertTrue(language + ": " + problems, problems.isEmpty());
        window.dismiss();
    }

    private GameModeStep.Program program(String id, String name, String image, int color) {
        Bitmap icon = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        new Canvas(icon).drawRoundRect(4, 4, 60, 60, 14, 14, paint);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        icon.compress(Bitmap.CompressFormat.PNG, 100, png);
        return new GameModeStep.Program(id, name, image, png.toByteArray());
    }

    private File artwork(String name, int width, int height, int top, int bottom)
            throws IOException {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint();
        paint.setShader(new LinearGradient(0, 0, width, height, top, bottom,
                Shader.TileMode.CLAMP));
        Canvas canvas = new Canvas(bitmap);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
        paint.setColor(Color.argb(160, 230, 220, 200));
        canvas.drawCircle(width * .7f, height * .35f, height * .12f, paint);
        File file = folder.newFile(name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        return file;
    }

    private static void shoot(View root, String language, String name, List<String> problems)
            throws IOException {
        FeatureShowcaseCaptureTest.layout(root);
        FeatureShowcaseCaptureTest.idle();
        FeatureShowcaseCaptureTest.layout(root);
        check(root, name, problems);
        String dir = System.getenv("GAME_MODE_SHOTS_DIR");
        if (dir == null) return;
        Bitmap bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        File file = new File(dir, language + "-" + name + ".png");
        file.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    /** Text other than a program name or exe keeps all its lines inside the screen. */
    private static void check(View view, String screen, List<String> problems) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView && ((TextView) view).length() > 0
                && !(view.getParent() instanceof android.widget.FrameLayout)) {
            TextView text = (TextView) view;
            android.text.Layout layout = text.getLayout();
            String what = screen + " \"" + text.getText() + "\"";
            if (layout == null) {
                problems.add(what + ": not laid out");
            } else {
                int room = text.getHeight() - text.getCompoundPaddingTop()
                        - text.getCompoundPaddingBottom();
                if (layout.getHeight() > room + 1) problems.add(what + ": lines cut");
                for (int line = 0; line < layout.getLineCount(); line++) {
                    if (layout.getEllipsisCount(line) > 0) problems.add(what + ": ellipsized");
                }
                int[] at = new int[2];
                text.getLocationInWindow(at);
                if (at[0] + text.getWidth() > 1920 + 1 || at[1] + text.getHeight() > 1080 + 1) {
                    problems.add(what + ": off screen");
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                check(group.getChildAt(index), screen, problems);
            }
        }
    }

    private static TextView text(View view, String containing) {
        if (view instanceof TextView && view.isShown()
                && ((TextView) view).getText().toString().matches(".*\\b" + containing + "\\b.*")
                && ((TextView) view).getCurrentTextColor() == 0xFF73D7FF) {
            return (TextView) view;
        }
        if (!(view instanceof ViewGroup)) return null;
        for (int index = 0; index < ((ViewGroup) view).getChildCount(); index++) {
            TextView found = text(((ViewGroup) view).getChildAt(index), containing);
            if (found != null) return found;
        }
        return null;
    }

    private static View tile(View view, String name) {
        if (name.contentEquals(view.getContentDescription() == null ? ""
                : view.getContentDescription())) {
            return view;
        }
        if (!(view instanceof ViewGroup)) return null;
        for (int index = 0; index < ((ViewGroup) view).getChildCount(); index++) {
            View found = tile(((ViewGroup) view).getChildAt(index), name);
            if (found != null) return found;
        }
        return null;
    }

    private static View actionFor(View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) {
            return view;
        }
        if (!(view instanceof ViewGroup)) return null;
        for (int index = 0; index < ((ViewGroup) view).getChildCount(); index++) {
            View found = actionFor(((ViewGroup) view).getChildAt(index), label);
            if (found != null) return found;
        }
        return null;
    }
}
