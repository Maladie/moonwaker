package com.limelight.console;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.BuildConfig;
import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.ControllerGlyphs;

import java.util.Locale;

/**
 * Tester builds only ({@code -Ptesting}): the welcome of mockups tester-welcome 01 and 02 on the
 * entry screens' shell, what to report and how. Shown once ({@link #isDue}, stored on show) and
 * reopened from Settings › About ({@link #settingsRow}). OK moves on, Back skips page 1 and goes
 * back from page 2.
 */
final class TesterWelcomeScreen {
    static final String PREF_SEEN = "tester_welcome_seen";
    static final int CARD_WIDTH_DP = 270;
    static final int GAP_DP = 15;
    static final int WHAT_HEIGHT_DP = 100;
    static final int WHERE_HEIGHT_DP = 96;
    static final int TESTER_INK = 0xFFFFC857;
    private static final int CARD_FILL = 0xB3141826;
    private static final int CARD_EDGE = 0x22FFFFFF;
    private static final int BADGE_FILL = 0x1AFFFFFF;
    private static final int BADGE_EDGE = 0x46FFFFFF;
    private static final int DOT = 0x5AFFFFFF;

    private final Activity activity;
    private final SharedPreferences preferences;
    private final Dialog dialog;
    private final ConsoleEntryShell shell;
    private final ConsoleEntryShell.Page what;
    private final ConsoleEntryShell.Page how;
    private final FrameLayout whatContent;
    private final FrameLayout howContent;
    private final LinearLayout dots;
    private final int background;
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private Runnable onClose;
    private boolean pad;
    private boolean onHow;
    private View whatFocus;
    private View howFocus;

    TesterWelcomeScreen(Activity activity, SharedPreferences preferences, boolean reducedMotion) {
        this.activity = activity;
        this.preferences = preferences;
        background = 0xFF000000 | activity.getResources().getColor(R.color.console_background);
        shell = new ConsoleEntryShell(activity);
        shell.setReducedMotion(reducedMotion);
        what = shell.addPage();
        how = shell.addPage();
        whatContent = new FrameLayout(activity);
        what.setContent(whatContent);
        howContent = new FrameLayout(activity);
        how.setContent(howContent);
        dots = new LinearLayout(activity);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Mockup: the page dots centred on y = 100 px.
        FrameLayout dotsLayer = new FrameLayout(activity);
        FrameLayout.LayoutParams dotsParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(6),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        dotsParams.topMargin = dp(47);
        dotsLayer.addView(dots, dotsParams);
        shell.addBelowClock(dotsLayer);

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(owner -> {
            ConsoleUiFeedback.showNotificationsIn(activity, null);
            Runnable then = onClose;
            onClose = null;
            if (then != null) then.run();
        });
        dialog.setOnKeyListener((owner, keyCode, event) -> onKey(keyCode, event));
    }

    /** Once per TV, and only in a tester build. */
    static boolean isDue(boolean testing, SharedPreferences preferences) {
        return testing && !preferences.getBoolean(PREF_SEEN, false);
    }

    /** Settings › About's "Tester welcome" row, or null outside a tester build. */
    static TextView settingsRow(ConsolePanel panel, Context context, boolean testing,
                                Runnable open) {
        if (!testing) return null;
        TextView row = panel.navigation(context.getString(R.string.tester_welcome_settings_entry));
        row.setOnClickListener(view -> open.run());
        return row;
    }

    /** The version line of Settings › About; a tester build adds its TESTER badge. */
    static CharSequence versionNote(Context context, boolean testing) {
        SpannableStringBuilder note = new SpannableStringBuilder(
                context.getString(R.string.console_settings_version, BuildConfig.VERSION_NAME));
        if (!testing) return note;
        note.append("   ");
        int start = note.length();
        note.append(context.getString(R.string.tester_welcome_badge));
        note.setSpan(new ForegroundColorSpan(TESTER_INK), start, note.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        note.setSpan(new StyleSpan(Typeface.BOLD), start, note.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return note;
    }

    /**
     * Opens on page 1 and stores it as seen. {@code pad}: the legend shows controller glyphs;
     * {@code onClose} (may be null) runs once the screen is closed.
     */
    void show(boolean pad, Runnable onClose) {
        this.pad = pad;
        this.onClose = onClose;
        preferences.edit().putBoolean(PREF_SEEN, true).apply();
        buildWhat();
        buildHow(PreferenceConfiguration.menuGesture(activity));
        showPage(false);
        if (!dialog.isShowing()) {
            dialog.show();
            FeatureShowcaseScreen.makeOpaque(dialog, background);
            ConsoleUiFeedback.showNotificationsIn(activity, shell);
        }
    }

    boolean isShowing() {
        return dialog.isShowing();
    }

    private boolean onKey(int keyCode, KeyEvent event) {
        boolean confirm = keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A;
        if (keyCode != KeyEvent.KEYCODE_BACK && !confirm) return false;
        if (event.getAction() != KeyEvent.ACTION_UP) return true;
        // OK: page 1 goes on, page 2 starts; Back: page 1 skips, page 2 goes back.
        if (confirm != onHow) showPage(confirm);
        else dialog.dismiss();
        return true;
    }

    private void showPage(boolean second) {
        onHow = second;
        shell.show(second ? how : what);
        renderDots(second ? 1 : 0);
        View target = second ? howFocus : whatFocus;
        target.post(target::requestFocus);
    }

    // --- Page 1: what to report ------------------------------------------------------------

    private void buildWhat() {
        what.setHeader(eyebrow(), s(R.string.tester_welcome_title),
                s(R.string.tester_welcome_subtitle));
        LinearLayout body = column();
        // Holds the focus so nothing behind the dialog takes OK.
        body.setFocusable(true);
        whatFocus = body;
        body.addView(section(R.string.tester_welcome_what_heading), wrap(0));
        int[][] items = {
                {R.drawable.ic_console_bug, R.string.tester_welcome_bugs_title,
                        R.string.tester_welcome_bugs_detail},
                {R.drawable.ic_console_question, R.string.tester_welcome_odd_title,
                        R.string.tester_welcome_odd_detail},
                {R.drawable.ic_console_zigzag, R.string.tester_welcome_awkward_title,
                        R.string.tester_welcome_awkward_detail},
                {R.drawable.ic_console_missing, R.string.tester_welcome_missing_title,
                        R.string.tester_welcome_missing_detail},
                {R.drawable.ic_console_pencil, R.string.tester_welcome_unfinished_title,
                        R.string.tester_welcome_unfinished_detail},
                {R.drawable.ic_console_idea, R.string.tester_welcome_ideas_title,
                        R.string.tester_welcome_ideas_detail}};
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(activity);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 3; col++) {
                int[] item = items[row * 3 + col];
                line.addView(card(item[0], 0, s(item[1]), s(item[2]), null),
                        sized(CARD_WIDTH_DP, WHAT_HEIGHT_DP, col == 0 ? 0 : GAP_DP, 0));
            }
            body.addView(line, wrap(row == 0 ? 10 : GAP_DP));
        }
        TextView closing = text(s(R.string.tester_welcome_closing), 16f, ConsoleEntryShell.TEXT,
                medium);
        body.addView(closing, wrap(14));
        body.addView(text(s(R.string.tester_welcome_closing_detail), 12f,
                ConsoleEntryShell.TEXT_SECONDARY, Typeface.DEFAULT), wrap(5));
        whatContent.removeAllViews();
        whatContent.addView(body);
        what.clearLegend();
        if (pad) {
            what.addLegendKey(ControllerGlyphs.text(ControllerGlyphs.hasPlayStationController(),
                    ControllerGlyphs.Button.CONFIRM), true, s(R.string.tester_welcome_next));
        } else {
            what.addLegendKey("OK", false, s(R.string.tester_welcome_next));
        }
        backKey(what, R.string.tester_welcome_skip);
    }

    // --- Page 2: how to report -------------------------------------------------------------

    /** {@code gesture}: how the stream menu opens, as "press Create" or "hold Start". */
    private void buildHow(String gesture) {
        how.setHeader(eyebrow(), s(R.string.tester_welcome_how_title),
                s(R.string.tester_welcome_how_subtitle));
        LinearLayout body = column();
        body.addView(section(R.string.tester_welcome_where_heading), wrap(0));
        String hold = Character.toUpperCase(gesture.charAt(0)) + gesture.substring(1);
        String report = s(R.string.problem_report_title);
        LinearLayout ways = new LinearLayout(activity);
        ways.setOrientation(LinearLayout.HORIZONTAL);
        ways.addView(card(R.drawable.ic_console_monitor, 0, s(R.string.tester_welcome_in_game),
                hold + "\n› " + s(R.string.overlay_menu_more) + " › " + report,
                s(R.string.tester_welcome_remote_hint)), sized(CARD_WIDTH_DP, WHERE_HEIGHT_DP,
                0, 0));
        ways.addView(card(R.drawable.ic_console_report, 0, s(R.string.tester_welcome_host_title),
                s(R.string.tester_welcome_host_panel) + " › " + s(R.string.console_section_more)
                        + "\n› " + report, null), sized(CARD_WIDTH_DP, WHERE_HEIGHT_DP, GAP_DP, 0));
        ways.addView(card(R.drawable.ic_console_settings, 0,
                s(R.string.tester_welcome_settings_title), s(R.string.console_settings_title)
                        + "\n› " + s(R.string.console_settings_about) + "\n› " + report, null),
                sized(CARD_WIDTH_DP, WHERE_HEIGHT_DP, GAP_DP, 0));
        body.addView(ways, wrap(8));

        int wide = 3 * CARD_WIDTH_DP + 2 * GAP_DP;
        String moment = activity.getString(R.string.tester_welcome_moment_detail, gesture,
                s(R.string.overlay_menu_more), s(R.string.problem_report_mark));
        body.addView(card(R.drawable.ic_console_star, TESTER_INK,
                s(R.string.problem_report_mark), moment, null), sized(wide,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 10));

        LinearLayout privacy = new LinearLayout(activity);
        privacy.setOrientation(LinearLayout.HORIZONTAL);
        privacy.setGravity(Gravity.CENTER_VERTICAL);
        privacy.setBackground(cardBackground(0x80141826));
        privacy.setPadding(dp(22), dp(7), dp(16), dp(7));
        privacy.addView(icon(R.drawable.ic_console_lock, ConsoleEntryShell.TEXT_SECONDARY),
                new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView privacyText = detail(s(R.string.tester_welcome_privacy));
        LinearLayout.LayoutParams privacyTextParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        privacyTextParams.leftMargin = dp(17);
        privacy.addView(privacyText, privacyTextParams);
        body.addView(privacy, sized(wide, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8));

        TextView start = text(s(R.string.tester_welcome_start), 14f, ConsolePanel.INK, medium);
        start.setGravity(Gravity.CENTER);
        start.setPadding(dp(25), 0, dp(25), 0);
        start.setMinWidth(dp(120));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xFCF6F7FA);
        pill.setCornerRadius(dp(17));
        start.setBackground(pill);
        start.setFocusable(true);
        start.setClickable(true);
        howFocus = start;
        start.setOnClickListener(view -> dialog.dismiss());
        body.addView(start, sized(ViewGroup.LayoutParams.WRAP_CONTENT, 34, 0, 9));
        howContent.removeAllViews();
        howContent.addView(body);
        how.clearLegend();
        backKey(how, R.string.tester_welcome_back);
    }

    // --- Pieces ----------------------------------------------------------------------------

    /**
     * A glass card: the icon on a round badge (tinted {@code tint}, 0: white), the title, the
     * detail and an optional smaller hint.
     */
    private View card(int iconRes, int tint, String title, String detail, String hint) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(cardBackground(CARD_FILL));
        card.setPadding(dp(13), dp(9), dp(12), dp(9));
        FrameLayout badge = new FrameLayout(activity);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(BADGE_FILL);
        circle.setStroke(Math.max(1, dp(1)), BADGE_EDGE);
        badge.setBackground(circle);
        badge.addView(icon(iconRes, tint == 0 ? ConsoleEntryShell.TEXT : tint),
                new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        card.addView(badge, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout lines = new LinearLayout(activity);
        lines.setOrientation(LinearLayout.VERTICAL);
        lines.addView(text(title, 15f, ConsoleEntryShell.TEXT, medium), wrap(0));
        lines.addView(detail(detail), wrap(5));
        if (hint != null) {
            lines.addView(text(hint, 10f, ConsoleEntryShell.TEXT_SECONDARY, Typeface.DEFAULT),
                    wrap(4));
        }
        LinearLayout.LayoutParams linesParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        linesParams.leftMargin = dp(10);
        card.addView(lines, linesParams);
        return card;
    }

    private GradientDrawable cardBackground(int fill) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(15));
        shape.setStroke(Math.max(1, dp(1)), CARD_EDGE);
        return shape;
    }

    private ImageView icon(int res, int color) {
        ImageView view = new ImageView(activity);
        view.setImageResource(res);
        view.setColorFilter(color);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    private TextView section(int label) {
        TextView view = text(s(label).toUpperCase(Locale.getDefault()), 11f,
                ConsoleEntryShell.HEADER, medium);
        view.setLetterSpacing(.04f);
        return view;
    }

    private TextView detail(String value) {
        TextView view = text(value, 11.5f, ConsoleEntryShell.TEXT_SECONDARY, Typeface.DEFAULT);
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    private LinearLayout column() {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        return body;
    }

    private String eyebrow() {
        return s(R.string.tester_welcome_eyebrow).toUpperCase(Locale.getDefault());
    }

    private void renderDots(int current) {
        dots.removeAllViews();
        for (int index = 0; index < 2; index++) {
            View dot = new View(activity);
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(index == current ? ConsoleEntryShell.TEXT : DOT);
            shape.setCornerRadius(dp(3));
            dot.setBackground(shape);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(index == current ? 14 : 5), dp(index == current ? 6 : 5));
            params.leftMargin = index == 0 ? 0 : dp(10);
            params.gravity = Gravity.CENTER_VERTICAL;
            dots.addView(dot, params);
        }
    }

    private void backKey(ConsoleEntryShell.Page page, int label) {
        if (pad) {
            page.addLegendKey(ControllerGlyphs.text(ControllerGlyphs.hasPlayStationController(),
                    ControllerGlyphs.Button.CANCEL), true, s(label));
        } else {
            page.addLegendIcon(R.drawable.ic_console_back, s(label));
        }
    }

    private TextView text(String value, float size, int color, Typeface typeface) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(typeface);
        view.setIncludeFontPadding(false);
        view.setGravity(Gravity.START);
        return view;
    }

    private LinearLayout.LayoutParams wrap(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(top);
        return params;
    }

    /** Width and height in dp (or a ViewGroup size constant), left and top margins in dp. */
    private LinearLayout.LayoutParams sized(int width, int height, int left, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                width < 0 ? width : dp(width), height < 0 ? height : dp(height));
        params.leftMargin = dp(left);
        params.topMargin = dp(top);
        return params;
    }

    private String s(int id) {
        return activity.getString(id);
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
