package com.limelight.ui.overlay;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Custom view representing a single button in the overlay menu.
 * Displays an icon on the left with an optional text label to the right.
 */
public class OverlayMenuButton extends LinearLayout {
    private static final int ICON_SIZE_DP = 24;
    private static final int PADDING_DP = 12;
    private static final int TEXT_SIZE_SP = 12;
    static final int CORNER_RADIUS_DP = 8;
    /** Side panel rows match the approved mockup: 28 dp rows with 18 dp icons. */
    private static final int PANEL_ROW_DP = 28;
    private static final int PANEL_ICON_DP = 18;
    /** The overlay's one palette: dark translucent surface, white focus with black ink. */
    static final int SURFACE = 0xD9000000;
    static final int FOCUS_FILL = 0xC0FFFFFF;
    static final int TEXT = Color.WHITE;
    static final int TEXT_SECONDARY = 0xFFB8BDCC;
    static final int INK = Color.BLACK;
    /** ConsolePanel's disabled row colours. */
    private static final int DISABLED_TEXT = 0xFF6E7482;
    private static final int DISABLED_REASON = 0xFF606674;

    private ImageView iconView;
    /** The label and, under it, the optional detail line; it takes the free width. */
    private LinearLayout textColumn;
    private TextView labelView;
    private TextView detailView;
    private TextView shortcutView;
    private GradientDrawable background;
    private boolean isSelected = false;
    private int strokeWidth;
    private int restFill = SURFACE;
    private int restText = TEXT;
    private int shortcutTextSp = 22;

    public OverlayMenuButton(Context context) {
        super(context);
        init(context);
    }

    public OverlayMenuButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public OverlayMenuButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        float density = context.getResources().getDisplayMetrics().density;
        int padding = (int) (PADDING_DP * density);
        int iconSize = (int) (ICON_SIZE_DP * density);

        // Cache stroke width to avoid recalculating on every appearance update
        strokeWidth = (int) (2 * density);

        setPadding(padding, padding, padding, padding);
        setMinimumHeight(iconSize + 2 * padding);
        setClickable(true);
        setFocusable(true);

        // Create background drawable
        background = surface(context, false, CORNER_RADIUS_DP);
        setBackground(background);

        // Create icon view
        iconView = new ImageView(context);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.gravity = Gravity.CENTER_VERTICAL;
        iconView.setLayoutParams(iconParams);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(iconView);

        textColumn = new LinearLayout(context);
        textColumn.setOrientation(VERTICAL);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        columnParams.leftMargin = (int) (8 * density);
        columnParams.gravity = Gravity.CENTER_VERTICAL;
        addView(textColumn, columnParams);

        labelView = new TextView(context);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP);
        labelView.setTextColor(Color.WHITE);
        labelView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        textColumn.addView(labelView);

        detailView = new TextView(context);
        detailView.setTextColor(TEXT_SECONDARY);
        detailView.setVisibility(GONE);
        textColumn.addView(detailView);

        shortcutView = new TextView(context);
        LinearLayout.LayoutParams shortcutParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        shortcutParams.leftMargin = (int) (8 * density);
        shortcutView.setLayoutParams(shortcutParams);
        shortcutView.setVisibility(GONE);
        addView(shortcutView);
    }

    /**
     * Set the button icon from a drawable resource ID
     */
    public void setIcon(int iconResId) {
        Drawable icon = getContext().getResources().getDrawable(iconResId);
        iconView.setImageDrawable(icon);
    }

    /**
     * Set the button icon from a drawable
     */
    public void setIcon(Drawable icon) {
        iconView.setImageDrawable(icon);
    }

    /**
     * Set the button label text
     */
    public void setLabel(CharSequence text) {
        labelView.setText(text);
        labelView.setVisibility(text != null && text.length() > 0 ? VISIBLE : GONE);
    }

    /** A smaller secondary line under the label (mockups O6–O8: what an action does). */
    public void setDetail(CharSequence text) {
        detailView.setText(text);
        detailView.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelView.getTextSize() * 0.8f);
        detailView.setVisibility(text != null && text.length() > 0 ? VISIBLE : GONE);
    }

    public void setShortcut(Typeface typeface, String glyph) {
        shortcutView.setTypeface(typeface);
        shortcutView.setText(glyph);
        showShortcut();
    }

    /** A Guide chord ({@link com.limelight.ui.ControllerGlyphs#chord}) at the label's own size. */
    public void setGuideChordShortcut(SpannableString value) {
        setValue(value);
    }

    /** A right-aligned value in place of a shortcut glyph, e.g. a bitrate or a key combination. */
    public void setValue(CharSequence value) {
        shortcutView.setTypeface(Typeface.DEFAULT);
        shortcutView.setText(value);
        shortcutView.setSingleLine(true);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) shortcutView.getLayoutParams();
        params.width = LayoutParams.WRAP_CONTENT;
        params.height = LayoutParams.WRAP_CONTENT;
        shortcutView.setLayoutParams(params);
        showShortcut();
        shortcutView.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelView.getTextSize());
    }

    public void clearShortcut() {
        shortcutView.setText("");
        shortcutView.setVisibility(GONE);
    }

    private void showShortcut() {
        shortcutView.setTextSize(TypedValue.COMPLEX_UNIT_SP, shortcutTextSp);
        shortcutView.setTextColor(isSelected ? INK : TEXT);
        shortcutView.setGravity(Gravity.CENTER);
        shortcutView.setVisibility(VISIBLE);
    }

    /**
     * Set whether this button is currently selected
     */
    public void setSelected(boolean selected) {
        // Skip redundant updates to avoid unnecessary redraws
        if (isSelected == selected) {
            return;
        }
        isSelected = selected;
        updateAppearance();
    }

    @Override
    public boolean isSelected() {
        return isSelected;
    }

    /**
     * Update the visual appearance based on selection state
     */
    private void updateAppearance() {
        if (isSelected) {
            // Highlighted state - brighter background
            style(background, true, strokeWidth);
            iconView.setImageTintList(ColorStateList.valueOf(INK));
            shortcutView.setTextColor(INK);
            labelView.setTextColor(INK);
            detailView.setTextColor(0xFF333844);
        } else {
            background.setColor(restFill);
            background.setStroke(0, 0);
            iconView.setImageTintList(ColorStateList.valueOf(restText));
            shortcutView.setTextColor(TEXT);
            labelView.setTextColor(restText);
            detailView.setTextColor(TEXT_SECONDARY);
        }
    }

    /** The overlay surface: {@link #SURFACE} at rest, the white focus fill when focused. */
    static GradientDrawable surface(Context context, boolean focused, int radiusDp) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(radiusDp * density);
        style(drawable, focused, (int) (2 * density));
        return drawable;
    }

    private static void style(GradientDrawable drawable, boolean focused, int strokeWidth) {
        drawable.setColor(focused ? FOCUS_FILL : SURFACE);
        drawable.setStroke(focused ? strokeWidth : 0, focused ? Color.WHITE : 0);
    }

    /** Text that is {@code color} at rest and {@link #INK} on a focused parent's fill. */
    static ColorStateList textColors(int color) {
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{INK, color});
    }

    /**
     * A full-width row of the stream side panel: the panel is the surface, so the row is clear at
     * rest; the label takes the free width and pushes the shortcut glyph to the right edge.
     */
    public void panelRow(int restFillColor, int restTextColor) {
        restFill = restFillColor;
        restText = restTextColor;
        float density = getResources().getDisplayMetrics().density;
        int icon = Math.round(PANEL_ICON_DP * density);
        setPadding(Math.round(10 * density), Math.round(5 * density),
                Math.round(10 * density), Math.round(5 * density));
        setMinimumHeight(Math.round(PANEL_ROW_DP * density));
        LinearLayout.LayoutParams iconParams = (LinearLayout.LayoutParams) iconView.getLayoutParams();
        iconParams.width = icon;
        iconParams.height = icon;
        iconView.setLayoutParams(iconParams);
        LinearLayout.LayoutParams shortcutParams =
                (LinearLayout.LayoutParams) shortcutView.getLayoutParams();
        shortcutParams.width = icon;
        shortcutParams.height = icon;
        shortcutView.setLayoutParams(shortcutParams);
        shortcutTextSp = 15;
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        fitLabel();
        updateAppearance();
    }

    /** A panel row shown off (mockup 25b): greyed label and icon, the reason under it. */
    public void disabledRow() {
        setFocusable(false);
        setClickable(false);
        setEnabled(false);
        restText = DISABLED_TEXT;
        updateAppearance();
        iconView.setImageAlpha(150);
        detailView.setTextColor(DISABLED_REASON);
    }

    /**
     * The label takes the free width and wraps to as many lines as it needs, so a shortcut or
     * value never overflows and no row is cut short.
     */
    public void fitLabel() {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) textColumn.getLayoutParams();
        params.width = 0;
        params.weight = 1f;
        textColumn.setLayoutParams(params);
    }

    public void hideIcon() {
        iconView.setVisibility(GONE);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) textColumn.getLayoutParams();
        params.leftMargin = 0;
        textColumn.setLayoutParams(params);
    }

    public static OverlayMenuButton create(Context context, int iconResId, String label) {
        OverlayMenuButton button = new OverlayMenuButton(context);
        if (iconResId != 0) {
            button.setIcon(iconResId);
        } else {
            button.hideIcon();
        }
        button.setLabel(label);
        return button;
    }

}
