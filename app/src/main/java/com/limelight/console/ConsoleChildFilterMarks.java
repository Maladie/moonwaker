package com.limelight.console;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.limelight.R;

/**
 * Mockup 23g: while the "For child" filter is on, a library card shows the game's rating top
 * right and "by hand" top left when the child has it by a manual grant. The marks are made on
 * the first bind that needs them.
 */
final class ConsoleChildFilterMarks {
    private TextView rating;
    private TextView manual;
    private String key = "";
    private boolean ratingShown;

    /** The rating takes the artwork's top right corner (the status badge moves under it). */
    boolean ratingShown() {
        return ratingShown;
    }

    /** {@code filter} null: no marks; {@code artworkFrame} holds the card's poster. */
    void bind(ViewGroup artworkFrame, PlayniteLibraryGame game,
              ChildLibraryFilter.Selection filter) {
        boolean manualGrant = filter != null
                && filter.child.state(game) == ChildGameAccess.State.GRANTED;
        String next = filter == null ? "" : (game.ageRating == null ? "?"
                : game.ageRating.label + '|' + game.ageRating.minAge) + '|' + manualGrant;
        if (next.equals(key)) return;
        key = next;
        ratingShown = filter != null;
        if (rating == null) {
            if (filter == null) return;
            Context context = artworkFrame.getContext();
            float density = context.getResources().getDisplayMetrics().density;
            int margin = Math.round(6 * density);
            rating = mark(context, "", 13, Color.WHITE);
            artworkFrame.addView(rating, corner(Gravity.TOP | Gravity.END, margin));
            manual = mark(context, context.getString(R.string.child_library_filter_manual), 11,
                    ConsoleTileBadge.AMBER);
            manual.setPadding(Math.round(7 * density), Math.round(2 * density),
                    Math.round(7 * density), Math.round(2 * density));
            GradientDrawable manualFill = new GradientDrawable();
            manualFill.setColor(ConsoleTileBadge.OUTLINED_FILL);
            manualFill.setCornerRadius(10 * density);
            manual.setBackground(manualFill);
            artworkFrame.addView(manual, corner(Gravity.TOP | Gravity.START, margin));
        }
        rating.setVisibility(filter == null ? View.GONE : View.VISIBLE);
        if (filter != null) rating.setText(GameAgeRating.badge(game.ageRating));
        manual.setVisibility(manualGrant ? View.VISIBLE : View.GONE);
    }

    private static TextView mark(Context context, String value, float size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        view.setSoundEffectsEnabled(false);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    private static FrameLayout.LayoutParams corner(int gravity, int margin) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                gravity);
        params.topMargin = margin;
        params.leftMargin = margin;
        params.rightMargin = margin;
        return params;
    }
}
