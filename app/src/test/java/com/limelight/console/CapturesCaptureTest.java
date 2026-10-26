package com.limelight.console;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.gateway.GatewayConnection;
import com.limelight.ui.overlay.OverlayMenuView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowInstrumentation;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * #51 at 1080p per language (mockups 25a-25m): the stream menu's capture rows, the gallery
 * while loading, filled and empty, the delete confirmation, the screenshot viewer and the
 * profile switch, with the text checks of {@link FeatureShowcaseCaptureTest}.
 * SHOWCASE_SHOTS_DIR writes the PNGs.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = 34)
public class CapturesCaptureTest {
    private static final String SCREEN = "-w960dp-h540dp-land-television-xhdpi";
    private static final long MINUTE = 60_000L;

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

    /** The host's answers, from memory. */
    private static final class Host extends HostGatewayClient {
        CaptureList list;

        @Override CaptureList captures(GatewayConnection connection) {
            return list;
        }

        @Override byte[] captureImage(GatewayConnection connection, String id, boolean preview) {
            int width = preview ? 1920 : 480;
            Bitmap bitmap = Bitmap.createBitmap(width, width * 9 / 16, Bitmap.Config.ARGB_8888);
            Paint paint = new Paint();
            int hue = Math.abs(id.hashCode()) % 360;
            paint.setShader(new LinearGradient(0, 0, width, width * 9 / 16f,
                    Color.HSVToColor(new float[]{hue, .6f, .5f}),
                    Color.HSVToColor(new float[]{(hue + 60) % 360, .7f, .2f}),
                    Shader.TileMode.CLAMP));
            new Canvas(bitmap).drawPaint(paint);
            ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, jpeg);
            return jpeg.toByteArray();
        }
    }

    /** Runs each task through {@code executor}; submit() hands it the task's Future. */
    private static ExecutorService service(Executor executor) {
        return new AbstractExecutorService() {
            @Override public void execute(Runnable task) {
                executor.execute(task);
            }

            @Override public void shutdown() {
            }

            @Override public List<Runnable> shutdownNow() {
                return new ArrayList<>();
            }

            @Override public boolean isShutdown() {
                return false;
            }

            @Override public boolean isTerminated() {
                return false;
            }

            @Override public boolean awaitTermination(long timeout, TimeUnit unit) {
                return true;
            }
        };
    }

    private void capture(String language) throws Exception {
        ShadowInstrumentation.getInstrumentation().setInTouchMode(false);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<String> problems = new ArrayList<>();

        for (OverlayMenuView.Captures state : OverlayMenuView.Captures.values()) {
            if (state == OverlayMenuView.Captures.HIDDEN) continue;
            OverlayMenuView menu = new OverlayMenuView(activity);
            FrameLayout root = new FrameLayout(activity);
            root.setBackgroundColor(Color.rgb(30, 40, 90));
            root.addView(menu, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            activity.setContentView(root);
            menu.setSessionInfo("Hollow Knight", "Komputer Mariusza");
            menu.setEndGameAvailable(true);
            menu.setCaptures(state);
            menu.show();
            FeatureShowcaseCaptureTest.shoot(activity.getWindow().getDecorView(),
                    "captures-" + language, "25-menu-" + state.name().toLowerCase(), problems);
        }

        long now = System.currentTimeMillis();
        Host host = new Host();
        host.list = CaptureList.parse(new JSONObject()
                .put("folder", "C:\\Users\\Basia\\Videos\\MoonWaker\\Basia")
                .put("captures", new JSONArray(Arrays.asList(
                        item("Hollow Knight a.png", now - 3 * MINUTE, "Hollow Knight"),
                        item("Hollow Knight b.mp4", now - 18 * MINUTE, "Hollow Knight"),
                        item("Twelve.mp4", now - 60 * MINUTE, "12 is Better Than 6"),
                        item("RE3.mp4", now - 26 * 60 * MINUTE, "Resident Evil 3"),
                        item("RE3 b.png", now - 26 * 60 * MINUTE - 5 * MINUTE, "Resident Evil 3"),
                        item("Cuphead.png", now - 30 * 60 * MINUTE,
                                "Sid Meier's Civilization V: Brave New World Deluxe")))));
        List<Runnable> pending = new ArrayList<>();
        ConsoleCaptureGallery gallery = new ConsoleCaptureGallery(activity, host,
                service(pending::add));
        ConsoleSettingsScreen settings = new ConsoleSettingsScreen(activity, true, false);
        String library = activity.getString(R.string.console_settings_library);
        settings.show(Arrays.asList(new ConsoleSettingsScreen.Category(library, panel -> {
            TextView row = panel.navigation(activity.getString(R.string.console_captures_title));
            return new View[]{panel.navigation(activity.getString(R.string.disk_space_title)),
                    row};
        })), 0);
        FeatureShowcaseCaptureTest.idle();
        GatewayConnection connection = new GatewayConnection("https://192.168.0.2:8785",
                "token", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "basia");
        gallery.show(settings, connection, library);
        View root = settings.window().getDecorView();
        FeatureShowcaseCaptureTest.shoot(root, "captures-" + language, "25i-loading", problems);
        while (!pending.isEmpty()) {
            pending.remove(0).run();
            FeatureShowcaseCaptureTest.idle();
        }
        FeatureShowcaseCaptureTest.shoot(root, "captures-" + language, "25g-gallery", problems);
        String first = host.list.captures.get(0).id;
        assertNotNull(gallery.thumbnail(first));

        gallery.delete(host.list.captures.get(0), null);
        Dialog confirm = ShadowDialog.getLatestDialog();
        FeatureShowcaseCaptureTest.shoot(confirm.getWindow().getDecorView(),
                "captures-" + language, "25l-delete", problems);
        confirm.dismiss();

        new ConsoleCaptureViewer(activity, host, connection, service(Runnable::run), host.list,
                0, gallery).show();
        Dialog viewer = ShadowDialog.getLatestDialog();
        // The full-screen window's own content: its decor has no view root under Robolectric.
        FeatureShowcaseCaptureTest.shoot(((ViewGroup) viewer.findViewById(
                android.R.id.content)).getChildAt(0), "captures-" + language, "25j-viewer",
                problems);
        viewer.dismiss();

        // Closing the viewer stops its clip download.
        new ConsoleCaptureViewer(activity, host, connection, service(pending::add), host.list,
                1, gallery).show();
        ShadowDialog.getLatestDialog().dismiss();
        FeatureShowcaseCaptureTest.idle();
        assertTrue(((Future<?>) pending.remove(0)).isCancelled());

        // Leaving the page lets its thumbnails go and stops a thumbnail loader still running.
        settings.closePage();
        FeatureShowcaseCaptureTest.idle();
        assertNull(gallery.thumbnail(first));
        gallery.show(settings, connection, library);
        pending.remove(0).run();
        FeatureShowcaseCaptureTest.idle();
        settings.closePage();
        FeatureShowcaseCaptureTest.idle();
        assertTrue(((Future<?>) pending.remove(0)).isCancelled());
        assertTrue(pending.isEmpty());

        host.list = CaptureList.parse(new JSONObject().put("folder",
                "C:\\Users\\Basia\\Videos\\MoonWaker\\Basia").put("captures", new JSONArray()));
        gallery.show(settings, connection, library);
        while (!pending.isEmpty()) {
            pending.remove(0).run();
            FeatureShowcaseCaptureTest.idle();
        }
        FeatureShowcaseCaptureTest.shoot(root, "captures-" + language, "25h-empty", problems);
        settings.dismiss();

        ConsoleSettingsScreen profile = new ConsoleSettingsScreen(activity, true, false);
        profile.show(Arrays.asList(new ConsoleSettingsScreen.Category(
                activity.getString(R.string.console_settings_about), panel -> {
                    LinearLayout buffer = panel.toggle(activity.getString(
                            R.string.console_captures_buffer_label), true);
                    panel.described(buffer, activity.getString(
                            R.string.console_captures_buffer_description));
                    return new View[]{panel.section(activity.getString(
                            R.string.console_captures_title)), buffer};
                })), 0);
        FeatureShowcaseCaptureTest.idle();
        FeatureShowcaseCaptureTest.shoot(profile.window().getDecorView(), "captures-" + language,
                "25m-profile", problems);
        profile.dismiss();
        // The menu and the gallery scroll (their last rows fade out at the edge), and a long
        // game name ends in "…" (mockup 25g); everything else must fit.
        problems.removeIf(problem -> problem.contains("scroll content")
                || problem.startsWith("25-menu") && problem.endsWith("outside its parent")
                || problem.contains("Brave New World Deluxe\": ellipsized"));
        assertTrue(language + ": " + problems, problems.isEmpty());
    }

    private static JSONObject item(String id, long created, String game) throws Exception {
        return new JSONObject().put("id", id).put("kind", id.endsWith(".mp4") ? "clip"
                : "screenshot").put("game_name", game).put("created_unix_ms", created)
                .put("duration_ms", 30_000).put("width", 2560).put("height", 1440);
    }
}
