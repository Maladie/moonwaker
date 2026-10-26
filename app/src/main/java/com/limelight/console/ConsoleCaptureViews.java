package com.limelight.console;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;

import java.util.List;

/**
 * Views of the captures gallery (.claude/handoffs/mockups/captures, 25g-25i, 25l): 16:9 tiles
 * three to a line with the library tile focus and a clip's length badge, the loading skeleton
 * and the empty page. Pure views: data in, callbacks out; no host or network here.
 */
final class ConsoleCaptureViews {
    private static final int CORNER_DP = 11;
    private static final int FOCUS_MARGIN_DP = 5;
    private static final int GAP_DP = 8;
    private static final int PLACEHOLDER = 0x22FFFFFF;
    private static final int SKELETON = 0x1EFFFFFF;

    private final Context context;
    private final int secondary;
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    ConsoleCaptureViews(Context context) {
        this.context = context;
        secondary = context.getColor(R.color.console_text_secondary);
    }

    /** One capture: the focusable stop of the grid; the ring and glow surround its picture. */
    static final class Tile {
        final LinearLayout view;
        final CaptureList.Capture capture;
        private final ImageView picture;
        private final ConsoleCardFocus focus;
        private Bitmap bitmap;

        private Tile(LinearLayout view, CaptureList.Capture capture, ImageView picture,
                     ConsoleCardFocus focus) {
            this.view = view;
            this.capture = capture;
            this.picture = picture;
            this.focus = focus;
        }

        /** The host's thumbnail; its average colour tints the focus glow. */
        void setPicture(Bitmap bitmap) {
            this.bitmap = bitmap;
            picture.setImageBitmap(bitmap);
            Bitmap pixel = Bitmap.createScaledBitmap(bitmap, 1, 1, true);
            focus.setGlowColor(pixel.getPixel(0, 0) | 0xFF000000);
            if (pixel != bitmap) pixel.recycle();
        }

        /** The thumbnail shown, or null before the host sent it. */
        Bitmap picture() {
            return bitmap;
        }
    }

    /**
     * A tile: {@code open} on OK, {@code delete} on Menu (☰). {@code detail} is the time and
     * the kind, e.g. "21:47  ·  klip".
     */
    Tile tile(CaptureList.Capture capture, String name, String detail, Runnable open,
              Runnable delete) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setId(View.generateViewId());
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setSoundEffectsEnabled(false);
        tile.setClipChildren(false);
        tile.setClipToPadding(false);

        int margin = dp(FOCUS_MARGIN_DP);
        FrameLayout frame = new SixteenNine(context);
        frame.setPadding(margin, margin, margin, margin);
        frame.setClipToPadding(false);
        ConsoleCardFocus focus = new ConsoleCardFocus(context.getResources(), margin,
                dp(CORNER_DP));
        frame.setBackground(focus);
        ImageView picture = new ImageView(context);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        picture.setBackgroundColor(PLACEHOLDER);
        rounded(picture, dp(CORNER_DP));
        frame.addView(picture, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (capture.clip) {
            FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(20), Gravity.END | Gravity.BOTTOM);
            badge.setMargins(0, 0, dp(6), dp(6));
            frame.addView(clipBadge(CaptureList.duration(capture.durationMs)), badge);
        }
        tile.addView(frame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text(name, 15, ConsolePanel.TEXT, false);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(title, words(dp(3)));
        TextView details = text(detail, 12, secondary, false);
        details.setSingleLine(true);
        tile.addView(details, words(0));

        Tile result = new Tile(tile, capture, picture, focus);
        tile.setContentDescription(name + ". " + detail);
        tile.setOnFocusChangeListener((view, hasFocus) -> {
            focus.setFocused(hasFocus);
            frame.invalidate();
        });
        tile.setOnClickListener(view -> open.run());
        tile.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_MENU && keyCode != KeyEvent.KEYCODE_BUTTON_START) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) delete.run();
            return true;
        });
        return result;
    }

    /** Up to three tiles in a line, each as wide as in a full line. */
    LinearLayout row(List<View> tiles) {
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setFocusable(false);
        line.setClipChildren(false);
        line.setPadding(0, 0, 0, dp(9));
        for (int index = 0; index < 3; index++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (index > 0) params.leftMargin = dp(GAP_DP);
            line.addView(index < tiles.size() ? tiles.get(index) : new View(context), params);
        }
        return line;
    }

    /** Mockup 25i: a section's grey label and a line of three grey tiles. */
    View[] skeletonSection() {
        LinearLayout label = new LinearLayout(context);
        label.setFocusable(false);
        label.setPadding(dp(ConsolePanel.ROW_INSET_DP), dp(17), 0, dp(8));
        label.addView(bar(SKELETON), new LinearLayout.LayoutParams(dp(45), dp(8)));
        List<View> tiles = new java.util.ArrayList<>();
        for (int index = 0; index < 3; index++) {
            LinearLayout tile = new LinearLayout(context);
            tile.setOrientation(LinearLayout.VERTICAL);
            int margin = dp(FOCUS_MARGIN_DP);
            FrameLayout frame = new SixteenNine(context);
            frame.setPadding(margin, margin, margin, margin);
            View picture = bar(PLACEHOLDER);
            ((GradientDrawable) picture.getBackground()).setCornerRadius(dp(CORNER_DP));
            frame.addView(picture);
            tile.addView(frame);
            LinearLayout.LayoutParams name = words(dp(5));
            name.width = dp(110);
            name.height = dp(11);
            tile.addView(bar(SKELETON), name);
            LinearLayout.LayoutParams detail = words(dp(7));
            detail.width = dp(60);
            detail.height = dp(9);
            tile.addView(bar(0x16FFFFFF), detail);
            tiles.add(tile);
        }
        return new View[]{label, row(tiles)};
    }

    /** Mockup 25h: nothing saved yet. */
    View empty(String title, String hint) {
        LinearLayout empty = new LinearLayout(context);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        empty.setFocusable(false);
        empty.setPadding(dp(60), dp(14), dp(60), dp(22));
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_overlay_screenshot);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(secondary));
        icon.setImageAlpha(150);
        empty.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView heading = text(title, 17, ConsolePanel.TEXT, true);
        heading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams headingParams = words(dp(18));
        headingParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        empty.addView(heading, headingParams);
        TextView words = text(hint, 13, secondary, false);
        words.setGravity(Gravity.CENTER);
        words.setLineSpacing(0f, 1.2f);
        LinearLayout.LayoutParams wordsParams = words(dp(12));
        wordsParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        empty.addView(words, wordsParams);
        return empty;
    }

    /** The picture of the delete confirmation (mockup 25l), 16:9. */
    ImageView confirmPicture(Bitmap bitmap) {
        ImageView picture = new ImageView(context);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        picture.setBackgroundColor(PLACEHOLDER);
        if (bitmap != null) picture.setImageBitmap(bitmap);
        rounded(picture, dp(7));
        picture.setLayoutParams(new LinearLayout.LayoutParams(dp(150), dp(84)));
        return picture;
    }

    // ------------------------------------------------------------------ parts

    /** ConsoleTileBadge's outlined pill: the play mark and the clip's length. */
    private TextView clipBadge(String length) {
        TextView badge = text(length, 12, ConsolePanel.TEXT, true);
        badge.setGravity(Gravity.CENTER_VERTICAL);
        badge.setPadding(dp(8), 0, dp(8), 0);
        Drawable play = context.getDrawable(R.drawable.ic_overlay_play).mutate();
        play.setTint(ConsolePanel.TEXT);
        play.setBounds(0, 0, dp(11), dp(11));
        badge.setCompoundDrawablesRelative(play, null, null, null);
        badge.setCompoundDrawablePadding(dp(3));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(10));
        background.setColor(ConsoleTileBadge.OUTLINED_FILL);
        background.setStroke(Math.max(1, dp(.75f)), 0x32FFFFFF);
        badge.setBackground(background);
        ConsoleText.centreOnGlyphs(badge);
        return badge;
    }

    private View bar(int color) {
        View bar = new View(context);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(color);
        fill.setCornerRadius(dp(5));
        bar.setBackground(fill);
        return bar;
    }

    private LinearLayout.LayoutParams words(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        params.leftMargin = dp(FOCUS_MARGIN_DP + 2);
        return params;
    }

    private TextView text(CharSequence value, float sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(medium);
        return view;
    }

    private static void rounded(View view, int radius) {
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View target, Outline outline) {
                outline.setRoundRect(0, 0, target.getWidth(), target.getHeight(), radius);
            }
        });
        view.setClipToOutline(true);
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** A frame whose content (inside its padding) is 16:9 of its width. */
    private static final class SixteenNine extends FrameLayout {
        SixteenNine(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int inner = width - getPaddingLeft() - getPaddingRight();
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                    inner * 9 / 16 + getPaddingTop() + getPaddingBottom(), MeasureSpec.EXACTLY));
        }
    }
}
