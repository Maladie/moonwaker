package com.limelight.console;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;

import com.limelight.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A game's age rating in the parent's library (AGE_RATINGS_DESIGN_PL.md 3.2, 3.4a), shown in the
 * rating's own system ("ESRB Teen", never a converted "PEGI 12"). The badge is MoonWaker's own
 * neutral one (3.6): the label in a rounded box coloured by the minimum age, no official logo.
 */
final class GameAgeRating {
    static final int GREEN = 0xFF3FA866;
    static final int YELLOW = 0xFFE2C044;
    static final int ORANGE = 0xFFE5872F;
    static final int RED = 0xFFDC4B4B;
    static final int GREY = 0xFF7D8694;
    private static final int MAX_DESCRIPTORS = 12;

    final String system;
    final String label;
    final int minAge;
    final List<String> descriptors;

    private GameAgeRating(String system, String label, int minAge, List<String> descriptors) {
        this.system = system;
        this.label = label;
        this.minAge = minAge;
        this.descriptors = Collections.unmodifiableList(descriptors);
    }

    /** The {@code ageRating} object of a library record or of sharing/get; null: unrated. */
    static GameAgeRating parse(JSONObject value) {
        if (value == null) return null;
        String system = value.optString("system", "").trim().toLowerCase(Locale.ROOT);
        String label = value.optString("label", "").trim();
        Object age = value.opt("minAge");
        if (!system.matches("pegi|esrb|usk|iarc") || label.isEmpty() || label.length() > 8
                || !(age instanceof Number)) return null;
        int minAge = ((Number) age).intValue();
        if (minAge < 0 || minAge > 21) return null;
        List<String> descriptors = new ArrayList<>();
        JSONArray values = value.optJSONArray("descriptors");
        for (int index = 0; values != null && index < values.length()
                && descriptors.size() < MAX_DESCRIPTORS; index++) {
            String descriptor = values.optString(index, "").trim();
            if (!descriptor.isEmpty() && descriptor.length() <= 80) descriptors.add(descriptor);
        }
        return new GameAgeRating(system, label, minAge, descriptors);
    }

    /** The same shape {@link #parse} reads, for the library cache. */
    JSONObject toJson() throws JSONException {
        return new JSONObject().put("system", system).put("label", label).put("minAge", minAge)
                .put("descriptors", new JSONArray(descriptors));
    }

    /** "PEGI 16", "USK 12", "IARC 7", or ESRB's full name: "ESRB Everyone 10+". */
    String systemText() {
        if (!"esrb".equals(system)) return system.toUpperCase(Locale.ROOT) + " " + label;
        switch (label.toUpperCase(Locale.ROOT)) {
            case "EC": return "ESRB Early Childhood";
            case "E": return "ESRB Everyone";
            case "E10+": return "ESRB Everyone 10+";
            case "T": return "ESRB Teen";
            case "M": return "ESRB Mature";
            case "AO": return "ESRB Adults Only";
            default: return "ESRB " + label;
        }
    }

    /** Badge colour by minimum age: up to 7 green, 12 yellow, 16 orange, older red; none grey. */
    static int color(GameAgeRating rating) {
        if (rating == null) return GREY;
        if (rating.minAge <= 7) return GREEN;
        if (rating.minAge <= 12) return YELLOW;
        if (rating.minAge <= 16) return ORANGE;
        return RED;
    }

    /** The badge alone: the rating's label, "?" when unrated. */
    static CharSequence badge(GameAgeRating rating) {
        String text = rating == null ? "?" : rating.label;
        SpannableStringBuilder value = new SpannableStringBuilder(text);
        value.setSpan(new BadgeSpan(color(rating)), 0, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return value;
    }

    /** Badge and system ("[16] PEGI 16"), the home hero chip (mockup 23c). */
    static CharSequence chip(GameAgeRating rating) {
        return new SpannableStringBuilder(badge(rating)).append("  ")
                .append(rating.systemText());
    }

    /**
     * Badge, system and the content descriptors on one line ("[E10+] ESRB Everyone 10+ · Fantasy
     * violence"), the game options panel (mockup 23d); "[?] Unrated" without a rating.
     */
    static CharSequence summary(Context context, GameAgeRating rating) {
        SpannableStringBuilder value = new SpannableStringBuilder(badge(rating)).append("  ");
        if (rating == null) return value.append(context.getString(R.string.age_rating_unrated));
        value.append(rating.systemText());
        if (!rating.descriptors.isEmpty()) {
            value.append(" · ").append(TextUtils.join(", ", rating.descriptors));
        }
        return value;
    }

    /** Badge, system and minimum age ("[T] ESRB Teen · from 13"), mockup 23e. */
    static CharSequence heading(Context context, GameAgeRating rating) {
        SpannableStringBuilder value = new SpannableStringBuilder(badge(rating)).append("  ");
        if (rating == null) {
            return value.append(context.getString(R.string.age_rating_unrated_manual));
        }
        return value.append(rating.systemText()).append(" · ")
                .append(context.getString(R.string.age_rating_min_age, rating.minAge));
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof GameAgeRating)) return false;
        GameAgeRating rating = (GameAgeRating) other;
        return minAge == rating.minAge && system.equals(rating.system)
                && label.equals(rating.label) && descriptors.equals(rating.descriptors);
    }

    @Override public int hashCode() {
        return Objects.hash(system, label, minAge, descriptors);
    }

    /** The neutral badge drawn in the text line: the label in a box of the rating's colour. */
    private static final class BadgeSpan extends ReplacementSpan {
        private final int color;
        private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();

        BadgeSpan(int color) {
            this.color = color;
        }

        private Paint text(Paint paint) {
            badgePaint.set(paint);
            badgePaint.setTypeface(Typeface.create(paint.getTypeface(), Typeface.BOLD));
            badgePaint.setTextSize(paint.getTextSize() * .82f);
            return badgePaint;
        }

        @Override public int getSize(Paint paint, CharSequence text, int start, int end,
                                     Paint.FontMetricsInt metrics) {
            if (metrics != null) paint.getFontMetricsInt(metrics);
            float pad = paint.getTextSize() * .32f;
            float width = text(paint).measureText(text, start, end) + 2 * pad;
            return Math.round(Math.max(width, paint.getTextSize() * 1.3f));
        }

        @Override public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                                   int top, int y, int bottom, Paint paint) {
            Paint.FontMetrics line = paint.getFontMetrics();
            float width = getSize(paint, text, start, end, null);
            box.set(x, y + line.ascent, x + width, y + line.descent);
            Paint fill = text(paint);
            fill.setColor(color);
            float radius = paint.getTextSize() * .22f;
            canvas.drawRoundRect(box, radius, radius, fill);
            Paint label = text(paint);
            label.setColor(color == YELLOW ? 0xFF1A1505 : 0xFFFFFFFF);
            label.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics labelLine = label.getFontMetrics();
            float baseline = box.centerY() - (labelLine.ascent + labelLine.descent) / 2f;
            canvas.drawText(text, start, end, box.centerX(), baseline, label);
        }
    }
}
