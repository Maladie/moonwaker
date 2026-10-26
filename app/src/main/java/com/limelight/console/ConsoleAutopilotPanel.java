package com.limelight.console;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.StateSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.binding.input.GyroAim;
import com.limelight.stream.LiveStreamSettings;
import com.limelight.stream.StreamingAutopilot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Streaming Autopilot after its analysis, on the settings screen (mockups C1–C3): 1 · picture,
 * the variants as a table with their link load; 2 · controls, the gyro proposal (only for a
 * game, with a gyro pad and a fitting genre); 3 · the test in the game. The global entry has
 * step 1 only. This class owns the steps and their rows; the caller saves and launches.
 */
final class ConsoleAutopilotPanel {
    private static final int WARN = 0xFFFFB74D;
    private static final int OK = 0xFF69F0AE;

    interface Actions {
        /** Keeps {@code variant}, and {@code gyro} unless null, without a test. */
        void save(StreamingAutopilot.Variant variant, GyroAim.Settings gyro);

        /** Keeps {@code gyro} unless null and starts the game's test of {@code variant}. */
        void playTest(StreamingAutopilot.Variant variant, GyroAim.Settings gyro);
    }

    /** Step 2's facts: the game's genres, the pad with a gyro, the settings it starts from. */
    static final class GyroOffer {
        final String genres;
        final String pad;
        final GyroAim.Settings base;

        GyroOffer(String genres, String pad, GyroAim.Settings base) {
            this.genres = genres;
            this.pad = pad;
            this.base = base;
        }
    }

    private final Activity activity;
    private final ConsoleSettingsScreen screen;
    private final int accent;
    private final int secondary;
    private ConsoleSettingsScreen.Category category;
    private boolean forGame;
    private boolean pushed;
    private List<StreamingAutopilot.Variant> variants;
    private int budgetKbps;
    private String current;
    private String link;
    private String display;
    private GyroOffer gyro;
    private Actions actions;
    private int step;
    private StreamingAutopilot.Variant chosen;
    private GyroAim.Settings gyroChoice;
    /** The row the step focuses once rendered. */
    private View focus;

    ConsoleAutopilotPanel(Activity activity, ConsoleSettingsScreen screen) {
        this.activity = activity;
        this.screen = screen;
        accent = activity.getResources().getColor(R.color.console_accent);
        secondary = activity.getResources().getColor(R.color.console_text_secondary);
    }

    /**
     * Opens step 1 for {@code target}: over Settings (pushed, Back returns there) or on its own
     * screen. {@code current} names the settings now, {@code link} the measured link (null when
     * not measured), {@code gyro} null when step 2 does not apply.
     */
    void open(String crumb, String target, boolean forGame, StreamingAutopilotController.Analysis
            analysis, String current, String link, GyroOffer gyro, Actions actions) {
        this.forGame = forGame;
        variants = analysis.variants;
        budgetKbps = analysis.safeBitrateKbps.orElse(0);
        this.current = current;
        this.link = link;
        display = displayModes(analysis.clientCapabilities);
        this.gyro = forGame ? gyro : null;
        this.actions = actions;
        step = 1;
        chosen = variants.get(0);
        gyroChoice = this.gyro == null ? null : GyroAim.aimProposal(this.gyro.base,
                GyroAim.Output.MOUSE);
        category = new ConsoleSettingsScreen.Category(
                s(R.string.console_streaming_autopilot_global), this::rows);
        category.back = this::back;
        describeStep();
        List<ConsoleSettingsScreen.Category> categories = Collections.singletonList(category);
        String eyebrow = s(forGame ? R.string.console_autopilot_eyebrow_game
                : R.string.console_autopilot_eyebrow_global);
        pushed = screen.isShowing();
        if (pushed) screen.push(crumb, target, eyebrow, categories, 0);
        else screen.show(crumb, target, eyebrow, categories, 0);
        focusStep();
    }

    private void describeStep() {
        if (step == 1) {
            category.heading = s(R.string.console_autopilot_step_picture_title);
            category.legend = s(forGame ? R.string.console_autopilot_legend_picture_game
                    : R.string.console_autopilot_legend_picture_global);
        } else if (step == 2) {
            category.heading = s(R.string.console_autopilot_gyro_title);
            category.legend = s(R.string.console_autopilot_legend_controls);
        } else {
            category.heading = s(R.string.console_autopilot_step_test_title);
            category.legend = s(R.string.console_autopilot_legend_test);
        }
    }

    private void goTo(int next) {
        step = next;
        describeStep();
        screen.refresh();
        focusStep();
    }

    private void focusStep() {
        View target = focus;
        if (target != null) target.post(target::requestFocus);
    }

    /** Back steps back through the flow; on step 1 the screen closes (or returns) as usual. */
    private boolean back() {
        if (step == 1) return false;
        goTo(step == 3 && gyro != null ? 2 : 1);
        return true;
    }

    private void close() {
        if (pushed) screen.pop();
        else screen.dismiss();
    }

    private View[] rows(ConsolePanel panel) {
        List<View> rows = new ArrayList<>();
        if (forGame) rows.add(stepper());
        if (step == 1) {
            rows.add(facts(fact(R.string.console_autopilot_fact_current, current),
                    link == null ? fact(R.string.console_autopilot_fact_link_unmeasured, "")
                            : fact(R.string.console_autopilot_fact_link, link),
                    fact(R.string.console_autopilot_fact_tv, display)));
            focus = null;
            for (StreamingAutopilot.Variant variant : variants) {
                View row = variantRow(panel, variant);
                if (variant == chosen) focus = row;
                rows.add(row);
            }
        } else if (step == 2) {
            rows.add(panel.note(activity.getString(R.string.console_autopilot_gyro_details,
                    gyro.genres, gyro.pad)));
            GyroAim.Settings mouse = GyroAim.aimProposal(gyro.base, GyroAim.Output.MOUSE);
            GyroAim.Settings stick = GyroAim.aimProposal(gyro.base, GyroAim.Output.STICK);
            GyroAim.Settings none = gyro.base.toBuilder().aim(false).build();
            focus = gyroRow(panel, rows, R.drawable.ic_overlay_mouse,
                    recommended(s(R.string.console_autopilot_gyro_mouse)),
                    R.string.console_autopilot_gyro_mouse_desc, mouse);
            View stickRow = gyroRow(panel, rows, R.drawable.ic_overlay_gamepad,
                    s(R.string.console_autopilot_gyro_stick),
                    R.string.console_autopilot_gyro_stick_desc, stick);
            View noneRow = gyroRow(panel, rows, R.drawable.ic_overlay_close,
                    s(R.string.console_autopilot_gyro_none),
                    R.string.console_autopilot_gyro_none_desc, none);
            if (gyroChoice != null && gyroChoice.aim
                    && gyroChoice.output == GyroAim.Output.STICK) focus = stickRow;
            if (gyroChoice != null && !gyroChoice.aim) focus = noneRow;
            rows.add(panel.note(s(R.string.console_autopilot_gyro_unsure)));
        } else {
            List<View> facts = new ArrayList<>();
            facts.add(fact(R.string.console_autopilot_fact_picture,
                    activity.getString(R.string.console_autopilot_variant,
                            s(StreamingAutopilotController.variantName(chosen.kind)),
                            settings(activity, chosen))));
            if (gyro != null) {
                facts.add(fact(R.string.console_autopilot_fact_gyro, gyroChoice.aim
                        ? s(gyroChoice.output == GyroAim.Output.MOUSE
                        ? R.string.console_autopilot_gyro_short_mouse
                        : R.string.console_autopilot_gyro_short_stick)
                        : s(R.string.console_autopilot_gyro_short_none)));
            }
            rows.add(facts(facts.toArray(new View[0])));
            TextView play = choice(panel, R.drawable.ic_console_play,
                    recommended(s(R.string.console_autopilot_play_test)),
                    s(R.string.console_autopilot_play_test_desc), true);
            play.setOnClickListener(view -> {
                close();
                actions.playTest(chosen, gyroChoice);
            });
            TextView save = choice(panel, R.drawable.ic_overlay_save,
                    s(R.string.console_autopilot_save_untested),
                    s(R.string.console_autopilot_save_untested_desc), false);
            save.setOnClickListener(view -> {
                close();
                actions.save(chosen, gyroChoice);
            });
            rows.add(play);
            rows.add(save);
            focus = play;
        }
        return rows.toArray(new View[0]);
    }

    /** "1 · PICTURE ✓  2 · CONTROLS  3 · TEST IN THE GAME": done steps ticked, this one lit. */
    private View stepper() {
        LinearLayout chips = row();
        int[] names = {R.string.console_autopilot_step_picture,
                R.string.console_autopilot_step_controls, R.string.console_autopilot_step_test};
        int number = 0;
        for (int index = 0; index < names.length; index++) {
            if (index == 1 && gyro == null) continue;
            number++;
            boolean on = index + 1 == step;
            boolean done = index + 1 < step;
            TextView chip = chip(number + " · " + s(names[index])
                    + (done ? " ✓" : ""), on ? 0xFF000000 : secondary, on ? accent : 0,
                    on ? accent : 0x1FFFFFFF);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setLetterSpacing(0.08f);
            chip.setAllCaps(true);
            chips.addView(chip, chipParams());
        }
        return chips;
    }

    /** The info chips, wrapping onto a second line when the panel is too narrow for them. */
    private View facts(View... facts) {
        Flow flow = new Flow(activity, dp(6));
        flow.setPadding(dp(ConsolePanel.ROW_INSET_DP), dp(4), dp(ConsolePanel.ROW_INSET_DP),
                dp(6));
        flow.setFocusable(false);
        for (View fact : facts) flow.addView(fact);
        return flow;
    }

    /** "Now 1080p · 60 · 20 Mb/s": the grey label, the value in white. */
    private TextView fact(int label, String value) {
        SpannableStringBuilder text = new SpannableStringBuilder(s(label));
        if (!value.isEmpty()) {
            text.append("  ");
            int start = text.length();
            text.append(value);
            text.setSpan(new ForegroundColorSpan(ConsolePanel.TEXT), start, text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new StyleSpan(Typeface.BOLD), start, text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return chip(text, secondary, 0x0FFFFFFF, 0x1FFFFFFF);
    }

    private TextView chip(CharSequence text, int color, int fill, int stroke) {
        TextView chip = new TextView(activity);
        chip.setText(text);
        chip.setTextSize(ConsolePanel.CAPTION_SP - 1);
        chip.setTextColor(color);
        chip.setSingleLine(true);
        chip.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(15));
        background.setStroke(dp(1), stroke);
        chip.setBackground(background);
        chip.setFocusable(false);
        return chip;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ConsolePanel.ROW_INSET_DP), dp(6), dp(ConsolePanel.ROW_INSET_DP),
                dp(4));
        row.setFocusable(false);
        return row;
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(6);
        return params;
    }

    /**
     * A variant's row of the table: name and badge, the mode with what it trades for, and the
     * link load (orange from 95%). OK picks it; for a game X keeps it without a test.
     */
    private View variantRow(ConsolePanel panel, StreamingAutopilot.Variant variant) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setId(View.generateViewId());
        row.setFocusable(true);
        row.setClickable(true);
        row.setSoundEffectsEnabled(false);
        row.setBackground(panel.focusFill(16));
        row.setPadding(dp(ConsolePanel.ROW_INSET_DP), dp(7), dp(ConsolePanel.ROW_INSET_DP),
                dp(7));
        ColorStateList primary = ConsolePanel.rowColors(ConsolePanel.TEXT);
        ColorStateList muted = ConsolePanel.rowColors(secondary);

        LinearLayout name = new LinearLayout(activity);
        name.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(s(StreamingAutopilotController.variantName(variant.kind)),
                ConsolePanel.LABEL_SP + 1, primary);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        name.addView(title);
        if (variant.kind == StreamingAutopilot.VariantKind.BALANCED) {
            TextView badge = chip(s(R.string.console_autopilot_badge_recommended), 0xFF000000,
                    accent, accent);
            badge.setTextSize(9);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setAllCaps(true);
            badge.setPadding(dp(5), dp(1), dp(5), dp(1));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeParams.topMargin = dp(3);
            name.addView(badge, badgeParams);
        }
        row.addView(name, new LinearLayout.LayoutParams(dp(140),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout mode = new LinearLayout(activity);
        mode.setOrientation(LinearLayout.VERTICAL);
        TextView settings = text(settings(activity, variant), ConsolePanel.VALUE_SP, primary);
        settings.setTypeface(Typeface.DEFAULT_BOLD);
        mode.addView(settings);
        TextView trade = text(s(StreamingAutopilotController.variantTrade(variant.kind)),
                ConsolePanel.CAPTION_SP, muted);
        trade.setLineSpacing(0f, 1.1f);
        LinearLayout.LayoutParams tradeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tradeParams.topMargin = dp(2);
        mode.addView(trade, tradeParams);
        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        modeParams.leftMargin = dp(10);
        row.addView(mode, modeParams);

        if (budgetKbps > 0) {
            int load = StreamingAutopilotController.loadPercent(variant.bitrateKbps, budgetKbps);
            LinearLayout loadColumn = new LinearLayout(activity);
            loadColumn.setOrientation(LinearLayout.VERTICAL);
            loadColumn.addView(text(activity.getString(R.string.console_autopilot_load, load),
                    ConsolePanel.CAPTION_SP - 1, muted));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
            barParams.topMargin = dp(4);
            loadColumn.addView(new LoadBar(activity, load), barParams);
            LinearLayout.LayoutParams loadParams = new LinearLayout.LayoutParams(dp(110),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            loadParams.leftMargin = dp(12);
            row.addView(loadColumn, loadParams);
        }

        row.setContentDescription(title.getText() + ". " + settings.getText() + ". "
                + trade.getText());
        row.setOnClickListener(view -> {
            chosen = variant;
            if (!forGame) {
                close();
                actions.save(variant, null);
            } else {
                goTo(gyro != null ? 2 : 3);
            }
        });
        if (forGame) {
            row.setOnKeyListener((view, keyCode, event) -> {
                if (keyCode != KeyEvent.KEYCODE_BUTTON_X) return false;
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    close();
                    actions.save(variant, null);
                }
                return true;
            });
        }
        return row;
    }

    private View gyroRow(ConsolePanel panel, List<View> rows, int icon, CharSequence label,
                         int description, GyroAim.Settings setting) {
        boolean chosenNow = gyroChoice != null && gyroChoice.aim == setting.aim
                && (!setting.aim || gyroChoice.output == setting.output);
        TextView row = choice(panel, icon, label, s(description), chosenNow);
        row.setOnClickListener(view -> {
            gyroChoice = setting;
            goTo(3);
        });
        rows.add(row);
        return row;
    }

    /** A radio row with its line icon and what it means under the label (mockups C2, C3). */
    private TextView choice(ConsolePanel panel, int icon, CharSequence label, String description,
                            boolean chosenNow) {
        TextView row = panel.icon(panel.choice(label, chosenNow, false), icon);
        // ConsolePanel.described would flatten the label's badge; the same layout, spans kept.
        SpannableStringBuilder text = new SpannableStringBuilder(label).append('\n');
        int start = text.length();
        text.append(description);
        text.setSpan(new AbsoluteSizeSpan(Math.round(ConsolePanel.CAPTION_SP
                        * activity.getResources().getDisplayMetrics().scaledDensity)), start,
                text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(secondary), start, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        row.setText(text);
        row.setMaxLines(Integer.MAX_VALUE);
        row.setLineSpacing(dp(3), 1f);
        return row;
    }

    /** "Label  RECOMMENDED": the badge in the accent, readable on the focus fill too. */
    private CharSequence recommended(String label) {
        SpannableStringBuilder text = new SpannableStringBuilder(label).append("  ");
        int start = text.length();
        text.append(' ').append(s(R.string.console_autopilot_badge_recommended)
                .toUpperCase(java.util.Locale.ROOT)).append(' ');
        text.setSpan(new BackgroundColorSpan(accent), start, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(0xFF000000), start, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new RelativeSizeSpan(0.62f), start, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new StyleSpan(Typeface.BOLD), start, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private TextView text(CharSequence value, float sp, ColorStateList colors) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(colors);
        text.setIncludeFontPadding(false);
        text.setDuplicateParentStateEnabled(true);
        return text;
    }

    /** "1440p · 60 FPS · 40 Mb/s". */
    static String settings(Context context, StreamingAutopilot.Variant variant) {
        return settings(context, variant.width, variant.height, variant.fps,
                variant.bitrateKbps);
    }

    static String settings(Context context, int width, int height, int fps, int bitrateKbps) {
        return LiveStreamSettings.resolutionName(width, height) + " · "
                + context.getString(R.string.overlay_stream_fps, fps) + " · "
                + context.getString(R.string.overlay_bitrate_current,
                Math.round(bitrateKbps / 1000f));
    }

    /** "4K 60 · 1440p 120": the largest mode and, when it differs, the fastest one. */
    static String displayModes(StreamingAutopilot.Capabilities client) {
        StreamingAutopilot.Mode largest = null;
        StreamingAutopilot.Mode fastest = null;
        for (StreamingAutopilot.Mode mode : client.modes) {
            long pixels = (long) mode.width * mode.height;
            if (largest == null || pixels > (long) largest.width * largest.height
                    || (pixels == (long) largest.width * largest.height
                    && mode.fps > largest.fps)) largest = mode;
            if (fastest == null || mode.fps > fastest.fps || (mode.fps == fastest.fps
                    && pixels > (long) fastest.width * fastest.height)) fastest = mode;
        }
        if (largest == null) return "";
        String text = LiveStreamSettings.resolutionName(largest.width, largest.height) + " "
                + largest.fps;
        return fastest.fps > largest.fps ? text + " · "
                + LiveStreamSettings.resolutionName(fastest.width, fastest.height) + " "
                + fastest.fps : text;
    }

    private String s(int resource) {
        return activity.getString(resource);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    /** Children left to right with {@code gap} between them, wrapping to further lines. */
    private static final class Flow extends ViewGroup {
        private final int gap;

        Flow(Context context, int gap) {
            super(context);
            this.gap = gap;
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
            int x = 0;
            int y = 0;
            int line = 0;
            for (int index = 0; index < getChildCount(); index++) {
                View child = getChildAt(index);
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                        MeasureSpec.UNSPECIFIED);
                if (x > 0 && x + child.getMeasuredWidth() > width) {
                    x = 0;
                    y += line + gap;
                    line = 0;
                }
                x += child.getMeasuredWidth() + gap;
                line = Math.max(line, child.getMeasuredHeight());
            }
            setMeasuredDimension(MeasureSpec.getSize(widthSpec),
                    y + line + getPaddingTop() + getPaddingBottom());
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l - getPaddingLeft() - getPaddingRight();
            int x = 0;
            int y = 0;
            int line = 0;
            for (int index = 0; index < getChildCount(); index++) {
                View child = getChildAt(index);
                if (x > 0 && x + child.getMeasuredWidth() > width) {
                    x = 0;
                    y += line + gap;
                    line = 0;
                }
                child.layout(getPaddingLeft() + x, getPaddingTop() + y,
                        getPaddingLeft() + x + child.getMeasuredWidth(),
                        getPaddingTop() + y + child.getMeasuredHeight());
                x += child.getMeasuredWidth() + gap;
                line = Math.max(line, child.getMeasuredHeight());
            }
        }
    }

    /** The link load: a thin track and the share used, orange from 95%. */
    private static final class LoadBar extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private final int load;

        LoadBar(Context context, int load) {
            super(context);
            this.load = load;
            setDuplicateParentStateEnabled(true);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            boolean focused = StateSet.stateSetMatches(
                    new int[]{android.R.attr.state_focused}, getDrawableState());
            float radius = getHeight() / 2f;
            bounds.set(0, 0, getWidth(), getHeight());
            paint.setColor(focused ? 0x26000000 : 0x1FFFFFFF);
            canvas.drawRoundRect(bounds, radius, radius, paint);
            bounds.right = getWidth() * Math.min(100, load) / 100f;
            paint.setColor(load >= 95 ? WARN : OK);
            canvas.drawRoundRect(bounds, radius, radius, paint);
        }
    }
}
