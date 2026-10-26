package com.limelight.console;

import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.text.Layout;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.console.FeatureShowcase.Feature;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowInstrumentation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Lays out the carousel and every feature page at 1080p (960 x 540 dp) per language and fails
 * on any text that is cut, ellipsized or pushed outside its parent. With the environment
 * variable SHOWCASE_SHOTS_DIR set it also writes each screen there as a PNG.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Conscrypt would stay installed JVM-wide and hang later TLS tests in the same worker.
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = 34)
public class FeatureShowcaseCaptureTest {
    private static final String SCREEN = "-w960dp-h540dp-land-television-xhdpi";

    @Test @Config(qualifiers = "pl" + SCREEN)
    public void polish() throws IOException {
        capture("pl", true);
    }

    @Test @Config(qualifiers = "en" + SCREEN)
    public void english() throws IOException {
        capture("en", false);
    }

    @Test @Config(qualifiers = "de" + SCREEN)
    public void german() throws IOException {
        capture("de", true);
    }

    private void capture(String language, boolean pad) throws IOException {
        // A TV is never in touch mode; there a card takes focus without focusableInTouchMode.
        ShadowInstrumentation.getInstrumentation().setInTouchMode(false);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FeatureShowcaseScreen screen = new FeatureShowcaseScreen(activity, true);
        Set<Feature> on = EnumSet.of(Feature.HAPTICS);
        screen.show(Arrays.asList(Feature.values()), language.equals("pl") ? "Krok 9 · Funkcje"
                : "", false, new FeatureShowcaseScreen.Defaults() {
            @Override public boolean available(Feature feature) { return true; }
            @Override public boolean isOn(Feature feature) { return on.contains(feature); }
            @Override public void set(Feature feature, boolean value) { }
        }, () -> { }, pad);
        Dialog dialog = ShadowDialog.getLatestDialog();
        View root = dialog.getWindow().getDecorView();
        List<String> problems = new ArrayList<>();
        idle();
        // Mockup 08c: the second card focused. Focus search runs in ViewRootImpl, which a
        // dispatched key does not reach here, so the cards are focused and clicked directly.
        ViewGroup cards = cards(root);
        cards.getChildAt(1).requestFocus();
        idle();
        shoot(root, language, "00-carousel", problems);
        cards.getChildAt(0).requestFocus();
        cards.getChildAt(0).performClick();
        idle();
        Feature[] all = Feature.values();
        for (int index = 0; index < all.length; index++) {
            shoot(root, language, String.format("%02d-%s", index + 1, all[index].id), problems);
            press(dialog, KeyEvent.KEYCODE_DPAD_RIGHT, root);
        }
        assertTrue(language + ": " + problems, problems.isEmpty());
    }

    private static ViewGroup cards(View view) {
        if (view instanceof HorizontalScrollView) return (ViewGroup) ((ViewGroup) view).getChildAt(0);
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            ViewGroup found = cards(group.getChildAt(index));
            if (found != null) return found;
        }
        return null;
    }

    static void press(Dialog dialog, int keyCode, View root) {
        layout(root);
        dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        idle();
    }

    static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    static void layout(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1920, 1080);
    }

    static void shoot(View root, String language, String name, List<String> problems)
            throws IOException {
        layout(root);
        idle();
        layout(root);
        check(root, name, problems);
        String dir = System.getenv("SHOWCASE_SHOTS_DIR");
        if (dir == null) return;
        Bitmap bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        File file = new File(dir, language + "-" + name + ".png");
        file.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    /** Every shown text has all its lines, no ellipsis, and stays inside each parent. */
    private static void check(View view, String screen, List<String> problems) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof ScrollView) {
            ViewGroup scroll = (ViewGroup) view;
            View content = scroll.getChildAt(0);
            int room = scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
            if (content != null && content.getHeight() > room) {
                problems.add(screen + ": scroll content " + content.getHeight() + " > " + room);
            }
        }
        if (view instanceof TextView && ((TextView) view).length() > 0) {
            TextView text = (TextView) view;
            Layout layout = text.getLayout();
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
                if (!insideParents(text)) problems.add(what + ": outside its parent");
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                check(group.getChildAt(index), screen, problems);
            }
        }
    }

    /** Walks up to the window; a horizontal row may scroll, so its own width does not count. */
    private static boolean insideParents(View view) {
        int left = 0, top = 0, right = view.getWidth(), bottom = view.getHeight();
        View child = view;
        while (child.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) child.getParent();
            left += child.getLeft();
            right += child.getLeft();
            top += child.getTop();
            bottom += child.getTop();
            if (parent instanceof HorizontalScrollView) return true;
            if (!(parent instanceof ScrollView)
                    && (left < -1 || top < -1 || right > parent.getWidth() + 1
                    || bottom > parent.getHeight() + 1)) {
                return false;
            }
            child = parent;
        }
        return true;
    }
}
