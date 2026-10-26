package com.limelight.ui.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * The stream test's readout in the overlay's style (mockups O4, O5): a card with a progress
 * ring, the phase, the time, what is tested and the live metrics, or, collapsed, a pill with
 * the ring, the time and one status dot. The caller decides what it says and when it collapses.
 */
public final class StreamTestHud extends LinearLayout {
    private static final int ACCENT = 0xFF73D7FF;
    private static final int OK = 0xFF69F0AE;
    private static final int WARN = 0xFFFFB74D;

    /** One live value; {@code over} marks it outside its limit. */
    public static final class Metric {
        final String label;
        final String value;
        final boolean over;

        public Metric(String label, String value, boolean over) {
            this.label = label;
            this.value = value;
            this.over = over;
        }
    }

    private final LinearLayout card;
    private final Ring ring;
    private final TextView phase;
    private final TextView time;
    private final TextView subject;
    private final LinearLayout metrics;
    private final TextView foot;
    private final LinearLayout pill;
    private final Ring pillRing;
    private final TextView pillTime;
    private final View pillDot;

    public StreamTestHud(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setGravity(Gravity.END);

        card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setPadding(dp(13), dp(11), dp(13), dp(11));
        card.setBackground(surface(14));
        addView(card, new LayoutParams(dp(380), LayoutParams.WRAP_CONTENT));

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ring = new Ring(context);
        top.addView(ring, new LayoutParams(dp(32), dp(32)));
        LinearLayout clock = new LinearLayout(context);
        clock.setOrientation(VERTICAL);
        phase = text(context, 9, OverlayMenuButton.TEXT_SECONDARY, true);
        phase.setLetterSpacing(0.14f);
        phase.setAllCaps(true);
        phase.setSingleLine(true);
        clock.addView(phase);
        time = text(context, 15, OverlayMenuButton.TEXT, true);
        clock.addView(time);
        LayoutParams clockParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        clockParams.leftMargin = dp(9);
        top.addView(clock, clockParams);
        subject = text(context, 10, OverlayMenuButton.TEXT_SECONDARY, false);
        subject.setGravity(Gravity.END);
        subject.setLineSpacing(0f, 1.2f);
        top.addView(subject);
        card.addView(top);

        metrics = new LinearLayout(context);
        metrics.setOrientation(HORIZONTAL);
        LayoutParams metricsParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        metricsParams.topMargin = dp(8);
        card.addView(metrics, metricsParams);
        foot = text(context, 9.5f, OverlayMenuButton.TEXT_SECONDARY, false);
        LayoutParams footParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        footParams.topMargin = dp(7);
        card.addView(foot, footParams);

        pill = new LinearLayout(context);
        pill.setOrientation(HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(5), 0, dp(11), 0);
        pill.setBackground(surface(15));
        pillRing = new Ring(context);
        pill.addView(pillRing, new LayoutParams(dp(22), dp(22)));
        pillTime = text(context, 11, OverlayMenuButton.TEXT, true);
        LayoutParams pillTimeParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        pillTimeParams.leftMargin = dp(6);
        pill.addView(pillTime, pillTimeParams);
        pillDot = dot(context, OK);
        LayoutParams dotParams = new LayoutParams(dp(6), dp(6));
        dotParams.leftMargin = dp(6);
        pill.addView(pillDot, dotParams);
        addView(pill, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(30)));
    }

    /**
     * The readout: {@code phaseText} with {@code doneMs} of {@code totalMs}, what is tested
     * ({@code name} over {@code mode}), the live {@code values} (empty: none yet) and the
     * {@code hint}; collapsed, the pill only.
     */
    public void show(String phaseText, long doneMs, long totalMs, String name, String mode,
                     List<Metric> values, String hint, boolean expanded) {
        boolean warning = false;
        for (Metric metric : values) warning |= metric.over;
        float progress = totalMs <= 0 ? 0f : Math.min(1f, doneMs / (float) totalMs);
        int ink = warning ? WARN : ACCENT;
        ring.set(progress, ink);
        pillRing.set(progress, ink);
        ((GradientDrawable) pillDot.getBackground()).setColor(warning ? WARN : OK);
        phase.setText(phaseText);
        SpannableStringBuilder clock = new SpannableStringBuilder(clock(doneMs));
        int start = clock.length();
        clock.append("  / ").append(clock(totalMs));
        clock.setSpan(new RelativeSizeSpan(0.73f), start, clock.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        clock.setSpan(new ForegroundColorSpan(OverlayMenuButton.TEXT_SECONDARY), start,
                clock.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        time.setText(clock);
        pillTime.setText(clock(doneMs));
        SpannableStringBuilder what = new SpannableStringBuilder(name);
        what.setSpan(new ForegroundColorSpan(OverlayMenuButton.TEXT), 0, what.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        what.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, what.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (mode != null && !mode.isEmpty()) what.append('\n').append(mode);
        subject.setText(what);
        metrics.removeAllViews();
        for (int index = 0; index < values.size(); index++) {
            LayoutParams params = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            if (index > 0) params.leftMargin = dp(5);
            metrics.addView(chip(values.get(index)), params);
        }
        metrics.setVisibility(values.isEmpty() ? GONE : VISIBLE);
        foot.setText(hint);
        foot.setVisibility(hint == null || hint.isEmpty() ? GONE : VISIBLE);
        card.setVisibility(expanded ? VISIBLE : GONE);
        pill.setVisibility(expanded ? GONE : VISIBLE);
    }

    private View chip(Metric metric) {
        Context context = getContext();
        LinearLayout chip = new LinearLayout(context);
        chip.setOrientation(HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(6), dp(4), dp(6), dp(4));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x0FFFFFFF);
        background.setCornerRadius(dp(7));
        chip.setBackground(background);
        chip.addView(dot(context, metric.over ? WARN : OK), new LayoutParams(dp(6), dp(6)));
        TextView label = text(context, 9, OverlayMenuButton.TEXT_SECONDARY, false);
        label.setText(metric.label);
        label.setSingleLine(true);
        LayoutParams labelParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = dp(4);
        chip.addView(label, labelParams);
        TextView value = text(context, 11, metric.over ? WARN : OverlayMenuButton.TEXT, true);
        value.setText(metric.value);
        value.setSingleLine(true);
        chip.addView(value);
        return chip;
    }

    private static String clock(long ms) {
        long seconds = Math.max(0L, ms) / 1_000L;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private GradientDrawable surface(int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xD60C0E16);
        background.setCornerRadius(dp(radiusDp));
        background.setStroke(dp(1), 0x1FFFFFFF);
        return background;
    }

    private View dot(Context context, int color) {
        View dot = new View(context);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        dot.setBackground(shape);
        return dot;
    }

    private static TextView text(Context context, float sp, int color, boolean bold) {
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        text.setTextColor(color);
        text.setIncludeFontPadding(false);
        if (bold) text.setTypeface(Typeface.DEFAULT_BOLD);
        return text;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** The progress ring: a faint track and the done part from twelve o'clock. */
    private static final class Ring extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private float progress;
        private int color = ACCENT;

        Ring(Context context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        void set(float progress, int color) {
            this.progress = progress;
            this.color = color;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float stroke = getWidth() * 7f / 64f;
            paint.setStrokeWidth(stroke);
            bounds.set(stroke, stroke, getWidth() - stroke, getHeight() - stroke);
            paint.setColor(0x24FFFFFF);
            canvas.drawArc(bounds, 0, 360, false, paint);
            paint.setColor(color);
            canvas.drawArc(bounds, -90, 360 * progress, false, paint);
        }
    }
}
