package com.limelight.console;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.limelight.R;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowInstrumentation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

/**
 * The update rows in Settings › About and the update side panels at 1080p per language, with
 * the checks of {@link FeatureShowcaseCaptureTest}. SHOWCASE_SHOTS_DIR writes the PNGs.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = 34)
public class AppUpdateCaptureTest {
    private static final String SCREEN = "-w960dp-h540dp-land-television-xhdpi";
    private static final String PACKAGE = "app.moonwaker.tv";

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
        ReleaseUpdates newer = updates(7, "0.8.1", "0.7.173");

        ConsoleSettingsScreen settings = new ConsoleSettingsScreen(activity, true, false);
        settings.show(Arrays.asList(
                new ConsoleSettingsScreen.Category(
                        activity.getString(R.string.console_settings_about), panel -> {
                    List<View> rows = new ArrayList<>(Arrays.asList(
                            panel.navigation(activity.getString(R.string.console_options_help)),
                            panel.navigation(activity.getString(
                                    R.string.console_options_features))));
                    rows.addAll(AppUpdater.settingsRows(activity, panel, newer, 800, apk -> { }));
                    rows.add(panel.note(TesterWelcomeScreen.versionNote(activity, false)));
                    return rows.toArray(new View[0]);
                })), 0);
        FeatureShowcaseCaptureTest.idle();
        FeatureShowcaseCaptureTest.shoot(settings.window().getDecorView(), "update-" + language,
                "01-settings-about", problems);
        settings.dismiss();

        AppUpdater.Notice notice = AppUpdater.notice(activity, newer, 800, "0.8.0");
        assertNotNull(notice);
        side(activity, language, "02-new-app", notice.title, notice.details, problems,
                activity.getString(R.string.app_update_install, notice.apk.versionName),
                activity.getString(R.string.console_close));

        AppUpdater.Notice older = AppUpdater.notice(activity,
                updates(ReleaseUpdates.MIN_HOST_PROTOCOL - 1, "0.8.0", "0.7.172"), 800, "0.8.0");
        assertNull(older.apk);
        side(activity, language, "03-host-older", older.title, older.details, problems,
                activity.getString(R.string.console_close));

        side(activity, language, "04-permission",
                activity.getString(R.string.app_update_permission_title),
                activity.getString(R.string.app_update_permission_details), problems,
                activity.getString(R.string.app_update_permission_open));

        side(activity, language, "05-downloading",
                activity.getString(R.string.app_update_downloading, "0.8.1"),
                activity.getString(R.string.app_update_downloading_details), problems);

        side(activity, language, "06-failed",
                activity.getString(R.string.app_update_failed_title),
                activity.getString(R.string.app_update_failed_download), problems,
                activity.getString(R.string.console_close));
        assertTrue(language + ": " + problems, problems.isEmpty());
    }

    /** The side panel as ConsoleActivity frames it, over a dark Home. */
    private static void side(Activity activity, String language, String name, String title,
                             String details, List<String> problems, String... actions)
            throws IOException {
        // No actions: the download's loading note, as ConsoleActivity shows it.
        sidePanel(activity, "update-" + language, name, activity.getString(R.string.app_update_eyebrow),
                title, details, problems, panel -> {
                    if (actions.length == 0) {
                        return new View[]{panel.note(activity.getString(R.string.console_loading))};
                    }
                    View[] rows = new View[actions.length];
                    for (int index = 0; index < actions.length; index++) {
                        rows[index] = panel.action(actions[index]);
                    }
                    return rows;
                });
    }

    /** A side panel as ConsoleActivity frames it, over a dark Home; the first row focused. */
    static void sidePanel(Activity activity, String prefix, String name, String eyebrow,
                          String title, CharSequence details, List<String> problems,
                          Function<ConsolePanel, View[]> build) throws IOException {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.rgb(12, 14, 22));
        ConsoleMenuBackdrop backdrop = new ConsoleMenuBackdrop(activity, false);
        root.addView(backdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LockableScrollView scroll = new LockableScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        float density = activity.getResources().getDisplayMetrics().density;
        int margin = Math.round(ConsolePanel.MARGIN_DP * density);
        FrameLayout.LayoutParams frame = new FrameLayout.LayoutParams(
                Math.round(ConsolePanel.WIDTH_DP * density),
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        frame.setMargins(margin, margin, margin, margin);
        root.addView(scroll, frame);
        ConsolePanel panel = new ConsolePanel(activity, scroll, backdrop, true);
        panel.floatOnGlass(content);
        View[] rows = build.apply(panel);
        panel.render(content, eyebrow, title, details, false, rows);
        activity.setContentView(root);
        FeatureShowcaseCaptureTest.idle();
        for (View row : rows) {
            if (row.isFocusable()) {
                row.requestFocus();
                break;
            }
        }
        FeatureShowcaseCaptureTest.shoot(activity.getWindow().getDecorView(), prefix, name,
                problems);
    }

    private static ReleaseUpdates updates(int hostProtocol, String apkVersion, String hostVersion)
            throws Exception {
        Ed25519PrivateKeyParameters owner = new Ed25519PrivateKeyParameters(new SecureRandom());
        byte[] manifest = new JSONObject()
                .put("apk", new JSONObject().put("package", PACKAGE)
                        .put("version_code", 800 + Integer.parseInt(apkVersion.substring(4)))
                        .put("version_name", apkVersion)
                        .put("url", "https://github.com/Maladie/moonwaker/a.apk")
                        .put("sha256", "ab").put("size", 1))
                .put("host", new JSONObject().put("version", hostVersion))
                .toString().getBytes(StandardCharsets.UTF_8);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, owner);
        signer.update(manifest, 0, manifest.length);
        return ReleaseUpdates.parse(new JSONObject()
                        .put("host", new JSONObject().put("version", "0.7.172")
                                .put("protocol_version", hostProtocol))
                        .put("release", new JSONObject()
                                .put("manifest", Base64.getEncoder().encodeToString(manifest))
                                .put("signature", Base64.getEncoder().encodeToString(
                                        signer.generateSignature()))),
                Base64.getEncoder().encodeToString(owner.generatePublicKey().getEncoded()),
                PACKAGE);
    }
}
