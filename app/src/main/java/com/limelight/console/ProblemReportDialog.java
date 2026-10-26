package com.limelight.console;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.BuildConfig;
import com.limelight.R;
import com.limelight.diagnostics.MoonWakerDiagnostics;
import com.limelight.diagnostics.ProblemReportBundle;
import com.limelight.gateway.GatewayConnection;
import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Zgłoś problem": one card, usable over the stream and the console, that sends a category,
 * an optional description and this TV's diagnostics through the paired host, which adds its
 * own logs and posts the report to the owner's Discord. "Zaznacz moment" marks the time a
 * later report covers. Every failure leaves the card open with a retry. The card closes, its
 * background work stops and it lets go of the Activity when that Activity finishes.
 */
public final class ProblemReportDialog {
    private static final String MARK_PREFERENCE = "problem_report_mark_ms";
    /** A mark older than this is no longer offered. */
    private static final long MARK_LIFETIME_MS = 3L * 60L * 60_000L;
    private static final int WIDTH_DP = 560;
    private static final int ACTION_HEIGHT_DP = 44;
    private static final String[] CATEGORIES = {"stream", "controls", "launch", "audio", "other"};
    private static final int[] CATEGORY_LABELS = {R.string.problem_report_category_stream,
            R.string.problem_report_category_controls, R.string.problem_report_category_launch,
            R.string.problem_report_category_audio, R.string.problem_report_category_other};
    /** The stream settings a report describes; never anything secret. */
    private static final String[] SETTINGS = {PreferenceConfiguration.RESOLUTION_PREF_STRING,
            PreferenceConfiguration.FPS_PREF_STRING, PreferenceConfiguration.BITRATE_PREF_STRING,
            PreferenceConfiguration.UNLOCK_FPS_STRING, PreferenceConfiguration.AUDIO_HAPTICS_PREF_STRING,
            "video_format", "checkbox_enable_hdr", "checkbox_full_range", "frame_pacing",
            "checkbox_ultra_low_latency", "checkbox_enable_sops", "checkbox_reduce_refresh_rate",
            "checkbox_stretch_video", PreferenceConfiguration.CREATE_BUTTON_PREF_STRING,
            PreferenceConfiguration.EMERGENCY_MODE_PREF_STRING, "list_audio_config",
            "checkbox_enable_audiofx", "checkbox_host_audio", "checkbox_multi_controller",
            "checkbox_usb_driver", "checkbox_gamepad_motion_sensors"};

    /** The hosting Activity and the card's views; all null once the card has closed. */
    private Activity activity;
    private Dialog dialog;
    private TextView[] categoryRows;
    private EditText description;
    private TextView markRow;
    private TextView contents;
    private TextView status;
    private TextView send;
    /** What the background work uses: never the Activity. */
    private final Context app;
    private final GatewayConnection connection;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newCachedThreadPool();
    private final long markMs;
    /** None until the tester picks one: Send stays disabled until then. */
    private int category = -1;
    private boolean useMark;
    private boolean sending;
    private boolean sent;
    /** Kept for a retry after a transport failure, whose outcome on the host is unknown. */
    private String pendingRequestId;
    private final Application.ActivityLifecycleCallbacks lifecycle =
            new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityPaused(Activity paused) {
                    if (paused.isFinishing()) close();
                }
                @Override public void onActivityDestroyed(Activity destroyed) { close(); }
                @Override public void onActivityCreated(Activity created, Bundle state) { }
                @Override public void onActivityStarted(Activity started) { }
                @Override public void onActivityResumed(Activity resumed) { }
                @Override public void onActivityStopped(Activity stopped) { }
                @Override public void onActivitySaveInstanceState(Activity saved, Bundle state) { }
            };

    private ProblemReportDialog(Activity activity, GatewayConnection connection) {
        this.activity = activity;
        this.app = activity.getApplicationContext();
        this.connection = connection;
        long mark = prefs(app).getLong(MARK_PREFERENCE, 0L);
        markMs = System.currentTimeMillis() - mark <= MARK_LIFETIME_MS ? mark : 0L;
        useMark = markMs > 0L;
    }

    /** Opens the card; {@code connection} is the selected host's Gateway, null without one. */
    public static void show(Activity activity, GatewayConnection connection) {
        new ProblemReportDialog(activity, connection).open();
    }

    /** "Zaznacz moment": the time a later report is centred on. */
    public static void markMoment(Context context) {
        prefs(context).edit().putLong(MARK_PREFERENCE, System.currentTimeMillis()).apply();
        MoonWakerDiagnostics.record("INFO", "android.report", "report.mark");
        Toast.makeText(context, R.string.problem_report_marked, Toast.LENGTH_LONG).show();
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    private void open() {
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(28), dp(22), dp(28), dp(18));
        GradientDrawable glass = new GradientDrawable();
        glass.setColor(ConsoleGlass.SOLID);
        glass.setCornerRadius(dp(ConsoleGlass.RADIUS_DP));
        glass.setStroke(Math.max(1, dp(1)), ConsoleGlass.HAIRLINE_TOP);
        card.setBackground(glass);

        card.addView(label(s(R.string.problem_report_title), ConsolePanel.TITLE_SP,
                ConsolePanel.TEXT, true));
        TextView subtitle = label(s(R.string.problem_report_subtitle), ConsolePanel.SECONDARY_SP,
                ConsolePanel.HEADER, false);
        card.addView(subtitle, margins(4, 12));

        card.addView(label(s(R.string.problem_report_category), ConsolePanel.CAPTION_SP,
                ConsolePanel.HEADER, true));
        LinearLayout categories = new LinearLayout(activity);
        categories.setOrientation(LinearLayout.HORIZONTAL);
        categoryRows = new TextView[CATEGORIES.length];
        for (int index = 0; index < CATEGORIES.length; index++) {
            int chosen = index;
            categoryRows[index] = pill(s(CATEGORY_LABELS[index]));
            categoryRows[index].setOnClickListener(view -> chooseCategory(chosen));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            params.rightMargin = dp(6);
            categories.addView(categoryRows[index], params);
        }
        card.addView(categories, margins(6, 12));

        description = new EditText(activity);
        description.setHint(R.string.problem_report_description_hint);
        description.setHintTextColor(ConsolePanel.HEADER);
        description.setTextColor(ConsolePanel.TEXT);
        description.setTextSize(ConsolePanel.LABEL_SP);
        description.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        description.setMinLines(2);
        description.setMaxLines(4);
        description.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(2000)});
        description.setPadding(dp(12), dp(8), dp(12), dp(8));
        description.setBackground(fieldBackground());
        card.addView(description, margins(0, 10));

        if (markMs > 0L) {
            markRow = pill("");
            markRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            markRow.setOnClickListener(view -> {
                useMark = !useMark;
                renderMark();
                preview();
            });
            renderMark();
            LinearLayout.LayoutParams params = margins(0, 10);
            params.height = dp(36);
            card.addView(markRow, params);
        }

        contents = label("", ConsolePanel.CAPTION_SP, ConsolePanel.HEADER, false);
        contents.setLineSpacing(0f, 1.15f);
        card.addView(contents, margins(0, 8));
        status = label("", ConsolePanel.LABEL_SP, ConsolePanel.TEXT, false);
        card.addView(status, margins(0, 14));

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        TextView cancel = pill(s(R.string.console_cancel));
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(view -> dialog.dismiss());
        send = pill(s(R.string.problem_report_send));
        send.setGravity(Gravity.CENTER);
        send.setOnClickListener(view -> {
            if (sent) dialog.dismiss();
            else send();
        });
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(ACTION_HEIGHT_DP));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(ACTION_HEIGHT_DP));
        sendParams.leftMargin = dp(10);
        actions.addView(cancel, cancelParams);
        actions.addView(send, sendParams);
        card.addView(actions, margins(0, 0));

        dialog.setContentView(card);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(ignored -> close());
        activity.registerActivityLifecycleCallbacks(lifecycle);
        dialog.setOnKeyListener((ignored, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BUTTON_B) {
                dialog.dismiss();
                return true;
            }
            return false;
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(.6f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setLayout(dp(WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        categoryRows[0].requestFocus();
        updateSend();
        if (connection == null) setStatus(s(R.string.problem_report_no_host), true);
        preview();
    }

    /** Dismissed, or its Activity finishing: stops the background work and lets go of the
     *  Activity and the views. A report already on its way finishes without the card. */
    private void close() {
        if (activity == null) return;
        Activity host = activity;
        Dialog shown = dialog;
        activity = null;
        dialog = null;
        categoryRows = null;
        description = null;
        markRow = null;
        contents = null;
        status = null;
        send = null;
        host.unregisterActivityLifecycleCallbacks(lifecycle);
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
        if (shown.isShowing()) shown.dismiss();
    }

    private void chooseCategory(int index) {
        category = index;
        for (int row = 0; row < categoryRows.length; row++) {
            categoryRows[row].setText((row == index ? "✓ " : "") + s(CATEGORY_LABELS[row]));
        }
        updateSend();
    }

    /** Send needs a host, a chosen category and no report on its way; Close after sending. */
    private void updateSend() {
        boolean enabled = sent || (connection != null && category >= 0 && !sending);
        send.setEnabled(enabled);
        send.setFocusable(enabled);
        send.setAlpha(enabled ? 1f : .45f);
    }

    private void renderMark() {
        String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(markMs));
        markRow.setText((useMark ? "☑  " : "☐  ")
                + activity.getString(R.string.problem_report_use_mark, time));
    }

    private long anchor() {
        return useMark && markMs > 0L ? markMs : System.currentTimeMillis();
    }

    /** What will be sent and about how much, from a bundle built off the main thread. */
    private void preview() {
        long anchor = anchor();
        contents.setText(R.string.problem_report_preparing);
        worker.execute(() -> {
            long bytes;
            try {
                bytes = bundle(anchor, "", "other").length;
            } catch (IOException | JSONException | RuntimeException error) {
                bytes = -1L;
            }
            long size = bytes;
            main.post(() -> {
                if (activity == null || anchor != anchor()) return;
                String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(anchor));
                contents.setText(activity.getString(R.string.problem_report_contents,
                        (int) ((ProblemReportBundle.WINDOW_BEFORE_MS
                                + ProblemReportBundle.WINDOW_AFTER_MS) / 60_000L), time,
                        size < 0 ? "?" : android.text.format.Formatter.formatShortFileSize(
                                activity, size)));
            });
        });
    }

    private void send() {
        if (sending || connection == null || category < 0) return;
        sending = true;
        updateSend();
        setStatus(s(R.string.problem_report_sending), false);
        String text = description.getText().toString();
        String chosen = CATEGORIES[category];
        long anchor = anchor();
        String requestId = pendingRequestId != null ? pendingRequestId
                : "report-" + UUID.randomUUID();
        pendingRequestId = requestId;
        worker.execute(() -> {
            HostGatewayClient.ReportResult result;
            try {
                // Built now, with this moment's logcat: the preview's is long stale.
                result = new HostGatewayClient().sendProblemReport(
                        connection, bundle(anchor, text, chosen), requestId);
            } catch (IOException | JSONException | RuntimeException error) {
                result = null;
            }
            boolean success = result != null && result.sent;
            MoonWakerDiagnostics.record(success ? "INFO" : "WARN", "android.report",
                    success ? "report.sent" : "report.failed", "request_id", requestId);
            HostGatewayClient.ReportResult answer = result;
            main.post(() -> {
                if (activity == null) return;
                sending = false;
                // A transport failure leaves the outcome unknown: a retry reuses the ID.
                if (answer != null) pendingRequestId = null;
                sent = success;
                updateSend();
                send.setText(success ? R.string.problem_report_close : R.string.problem_report_retry);
                setStatus(answer == null ? s(R.string.problem_report_network)
                        : success ? s(R.string.problem_report_sent) : refusal(answer), !success);
                send.requestFocus();
            });
        });
    }

    private String refusal(HostGatewayClient.ReportResult result) {
        switch (result.reason) {
            case "report_not_configured":
                return s(R.string.problem_report_not_configured);
            case "rate_limited":
                return activity.getString(R.string.problem_report_rate_limited,
                        Math.max(1, result.retryAfterSeconds));
            case "webhook_failed":
                return activity.getString(R.string.problem_report_webhook_failed, result.detail);
            default:
                return activity.getString(R.string.problem_report_failed, result.reason);
        }
    }

    /** This TV's half of the report for the window around {@code anchorMs}. */
    private byte[] bundle(long anchorMs, String text, String chosenCategory)
            throws IOException, JSONException {
        long now = System.currentTimeMillis();
        long[] window = ProblemReportBundle.window(anchorMs, now);
        JSONObject settings = new JSONObject();
        Map<String, ?> stored = prefs(app).getAll();
        for (String key : SETTINGS) {
            Object value = stored.get(key);
            if (value instanceof String || value instanceof Boolean || value instanceof Integer) {
                settings.put(key, value);
            }
        }
        JSONObject report = new JSONObject()
                .put("category", chosenCategory)
                .put("description", text)
                .put("app_version", BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
                .put("device", Build.MANUFACTURER + " " + Build.MODEL)
                .put("android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
                .put("created_at", now / 1000L)
                .put("window_from", window[0] / 1000L)
                .put("window_to", (window[1] + 999L) / 1000L)
                .put("mark_used", useMark && markMs > 0L)
                .put("settings", settings);
        if (markMs > 0L) report.put("mark_at", markMs / 1000L);
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("events.jsonl", MoonWakerDiagnostics.window(window[0], window[1],
                ProblemReportBundle.EVENTS_MAX_BYTES));
        entries.put("logcat.txt", logcat());
        return ProblemReportBundle.zip(report, entries);
    }

    /** This app's own log now; Android lets an app read only its own process's lines. */
    private static String logcat() {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.Arrays.asList(
                "logcat", "-d", "-v", "threadtime", "-t", "20000"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            command.add("--pid=" + android.os.Process.myPid());
        }
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    output.write(buffer, 0, read);
                    if (output.size() > 4 * ProblemReportBundle.LOGCAT_MAX_BYTES) break;
                }
            }
            process.destroy();
            return ProblemReportBundle.tail(new String(output.toByteArray(),
                    StandardCharsets.UTF_8), ProblemReportBundle.LOGCAT_MAX_BYTES);
        } catch (IOException | RuntimeException unavailable) {
            return "logcat unavailable: " + unavailable.getClass().getSimpleName() + "\n";
        }
    }

    private void setStatus(String text, boolean problem) {
        status.setText(text);
        status.setTextColor(problem ? activity.getResources().getColor(R.color.console_state_error)
                : ConsolePanel.TEXT);
    }

    /** A focusable glass capsule; focused, the near-white fill with dark ink. */
    private TextView pill(String text) {
        TextView view = label(text, ConsolePanel.LABEL_SP, ConsolePanel.TEXT, true);
        view.setTextColor(ConsolePanel.rowColors(ConsolePanel.TEXT));
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setPadding(dp(16), 0, dp(16), 0);
        view.setFocusable(true);
        view.setClickable(true);
        view.setSoundEffectsEnabled(false);
        int radius = dp(ACTION_HEIGHT_DP) / 2;
        GradientDrawable rest = new GradientDrawable();
        rest.setColor(0x1EFFFFFF);
        rest.setCornerRadius(radius);
        rest.setStroke(Math.max(1, dp(1)), 0x28FFFFFF);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(ConsolePanel.FOCUS_FILL);
        focused.setCornerRadius(radius);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused}, focused);
        background.addState(new int[0], rest);
        view.setBackground(background);
        return view;
    }

    private android.graphics.drawable.Drawable fieldBackground() {
        GradientDrawable rest = new GradientDrawable();
        rest.setColor(0x14FFFFFF);
        rest.setCornerRadius(dp(12));
        rest.setStroke(Math.max(1, dp(1)), 0x28FFFFFF);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0x24FFFFFF);
        focused.setCornerRadius(dp(12));
        focused.setStroke(dp(2), ConsolePanel.FOCUS_FILL);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused}, focused);
        background.addState(new int[0], rest);
        return background;
    }

    private TextView label(String text, float size, int color, boolean medium) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (medium) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    private LinearLayout.LayoutParams margins(int topDp, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topDp);
        params.bottomMargin = dp(bottomDp);
        return params;
    }

    private String s(int resource) {
        return activity.getString(resource);
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
