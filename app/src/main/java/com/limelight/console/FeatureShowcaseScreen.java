package com.limelight.console;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.console.FeatureShowcase.Feature;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.ControllerGlyphs;

import java.util.List;
import java.util.Locale;

/**
 * "What MoonWaker can do" / "What's new" on the entry screens' shell (mockups 08c, 08d): a row
 * of illustrated cards and, on OK, one feature's page with a scene of how it works, its steps,
 * where to find it and, for gyro and haptics, the global default. Standalone (Settings › About,
 * the update announcement) or as a wizard step: the caller passes {@code onNext}, which X runs
 * after closing; Back only closes.
 */
final class FeatureShowcaseScreen {
    /** The global setting behind a card's "Turn on by default"; the caller owns the storage. */
    interface Defaults {
        boolean available(Feature feature);

        boolean isOn(Feature feature);

        void set(Feature feature, boolean on);
    }

    static final int CARD_WIDTH_DP = 165;
    static final int CARD_HEIGHT_DP = 220;
    static final int ART_HEIGHT_DP = 110;
    static final int CORNER_DP = 14;
    /** Card to card, as in mockup 08c (330 px cards, 34 px apart). */
    static final float PITCH_DP = 182f;
    /** The first card's left edge (150 px). */
    static final int ROW_START_DP = 75;
    static final float FOCUS_SCALE = 1.05f;
    static final int SCENE_WIDTH_DP = 440;
    static final int SCENE_HEIGHT_DP = 232;
    static final int SIDE_WIDTH_DP = 380;
    static final int COLUMN_GAP_DP = 20;
    /** Room between the header and the legend. */
    static final int BODY_HEIGHT_DP = 284;
    private static final int CARD_FILL = 0xB3141826;
    private static final int SIDE_FILL = 0xB3141826;
    private static final int SIDE_EDGE = 0x22FFFFFF;
    private static final int NEW_INK = 0xFF6EE7F5;
    private static final int EXPERIMENT_INK = 0xFFFFC857;
    private static final int DOT = 0x45FFFFFF;
    private static final int DOT_CURRENT = 0xE6FFFFFF;

    private final Activity activity;
    private final Dialog dialog;
    private final ConsoleEntryShell shell;
    private final ConsoleEntryShell.Page carouselPage;
    private final ConsoleEntryShell.Page detailPage;
    private final boolean reducedMotion;
    private final int background;
    private final HorizontalScrollView cardScroll;
    private final LinearLayout cards;
    private final LinearLayout dots;
    private final LinearLayout detail;
    private final ImageView scene;
    private final TextView where;
    private final LockableScrollView side;
    private final LinearLayout sideRows;
    private final ConsolePanel panel;
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private List<Feature> features;
    private Defaults defaults;
    private Runnable onNext;
    private String eyebrow = "";
    private boolean pad;
    private boolean playStation;
    private Feature open;

    FeatureShowcaseScreen(Activity activity, boolean reducedMotion) {
        this.activity = activity;
        this.reducedMotion = reducedMotion;
        background = 0xFF000000 | activity.getResources().getColor(R.color.console_background);
        shell = new ConsoleEntryShell(activity);
        shell.setReducedMotion(reducedMotion);

        carouselPage = shell.addPage();
        LinearLayout carousel = new LinearLayout(activity);
        carousel.setOrientation(LinearLayout.VERTICAL);
        carousel.setClipChildren(false);
        cardScroll = new HorizontalScrollView(activity);
        cardScroll.setHorizontalScrollBarEnabled(false);
        cardScroll.setClipToPadding(false);
        cardScroll.setClipChildren(false);
        cardScroll.setPadding(dp(ROW_START_DP) - inset(), 0, dp(ROW_START_DP) - inset(), 0);
        cards = new LinearLayout(activity);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setClipChildren(false);
        cardScroll.addView(cards);
        carousel.addView(cardScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(CARD_HEIGHT_DP) + 2 * inset()));
        dots = new LinearLayout(activity);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dotsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        dotsParams.topMargin = dp(22);
        carousel.addView(dots, dotsParams);
        carouselPage.setContent(carousel);

        detailPage = shell.addPage();
        detail = new LinearLayout(activity);
        detail.setOrientation(LinearLayout.HORIZONTAL);
        detail.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        // Holds the focus on a page without a toggle, so no card behind it takes OK.
        detail.setFocusable(true);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        scene = new ImageView(activity);
        scene.setScaleType(ImageView.ScaleType.FIT_XY);
        scene.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        column.addView(scene, new LinearLayout.LayoutParams(dp(SCENE_WIDTH_DP),
                dp(SCENE_HEIGHT_DP)));
        where = text("", ConsolePanel.CAPTION_SP, ConsoleEntryShell.TEXT_SECONDARY, Typeface.DEFAULT);
        where.setCompoundDrawablePadding(dp(8));
        where.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams whereParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        whereParams.topMargin = dp(12);
        whereParams.leftMargin = dp(4);
        column.addView(where, whereParams);
        detail.addView(column, new LinearLayout.LayoutParams(dp(SCENE_WIDTH_DP),
                dp(BODY_HEIGHT_DP)));

        side = new LockableScrollView(activity);
        side.setVerticalScrollBarEnabled(false);
        side.setClipToPadding(false);
        GradientDrawable sideCard = new GradientDrawable();
        sideCard.setColor(SIDE_FILL);
        sideCard.setCornerRadius(dp(16));
        sideCard.setStroke(Math.max(1, dp(1)), SIDE_EDGE);
        side.setBackground(sideCard);
        side.setPadding(dp(8), dp(14), dp(8), dp(10));
        sideRows = new LinearLayout(activity);
        sideRows.setOrientation(LinearLayout.VERTICAL);
        side.addView(sideRows, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sideParams = new LinearLayout.LayoutParams(dp(SIDE_WIDTH_DP),
                dp(BODY_HEIGHT_DP));
        sideParams.leftMargin = dp(COLUMN_GAP_DP);
        detail.addView(side, sideParams);
        panel = new ConsolePanel(activity, side, new ConsoleMenuBackdrop(activity, false),
                reducedMotion, true);
        detailPage.setContent(detail);

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(owner -> ConsoleUiFeedback.showNotificationsIn(activity, null));
        dialog.setOnKeyListener((owner, keyCode, event) -> onKey(keyCode, event));
    }

    private boolean onKey(int keyCode, KeyEvent event) {
        boolean browse = open != null && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT);
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return keyCode == KeyEvent.KEYCODE_BACK || isNextKey(keyCode) || browse;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            back();
            return true;
        }
        if (isNextKey(keyCode) && onNext != null) {
            dialog.dismiss();
            onNext.run();
            return true;
        }
        if (browse) {
            int next = features.indexOf(open) + (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
            if (next >= 0 && next < features.size()) showDetail(features.get(next));
            return true;
        }
        return false;
    }

    private static boolean isNextKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BUTTON_X || keyCode == KeyEvent.KEYCODE_X;
    }

    /**
     * Shows {@code features}. {@code eyebrow} (a wizard's "STEP 9") may be empty;
     * {@code whatsNew} picks the heading; {@code defaults} and {@code onNext} may be null;
     * {@code pad}: a gamepad is connected, so the legend shows its glyphs.
     */
    void show(List<Feature> features, String eyebrow, boolean whatsNew, Defaults defaults,
              Runnable onNext, boolean pad) {
        this.features = features;
        this.defaults = defaults;
        this.onNext = onNext;
        this.pad = pad;
        playStation = ControllerGlyphs.hasPlayStationController();
        this.eyebrow = (eyebrow.isEmpty() ? activity.getString(R.string.feature_showcase_eyebrow)
                : eyebrow).toUpperCase(Locale.getDefault());
        open = null;
        carouselPage.setHeader(this.eyebrow, activity.getString(whatsNew
                        ? R.string.feature_whats_new_title : R.string.feature_showcase_title),
                activity.getString(whatsNew ? R.string.feature_whats_new_subtitle
                        : R.string.feature_showcase_subtitle));
        cards.removeAllViews();
        for (Feature feature : features) cards.addView(card(feature));
        renderDots(0);
        carouselLegend();
        carouselPage.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        shell.show(carouselPage);
        if (!dialog.isShowing()) {
            dialog.show();
            makeOpaque(dialog, background);
            ConsoleUiFeedback.showNotificationsIn(activity, shell);
        }
        View first = cards.getChildAt(0);
        if (first != null) first.post(first::requestFocus);
    }

    boolean isShowing() {
        return dialog.isShowing();
    }

    void dismiss() {
        dialog.dismiss();
    }

    private View card(Feature feature) {
        FrameLayout frame = new FrameLayout(activity);
        frame.setFocusable(true);
        frame.setClickable(true);
        frame.setClipChildren(false);
        frame.setClipToPadding(false);
        frame.setPadding(inset(), inset(), inset(), inset());
        ConsoleCardFocus focus = new ConsoleCardFocus(activity.getResources(), inset(),
                dp(CORNER_DP));
        int accent = accent(feature);
        focus.setGlowColor(accent);
        frame.setBackground(focus);

        FrameLayout card = new FrameLayout(activity);
        int corner = dp(CORNER_DP);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), corner);
            }
        });
        card.setClipToOutline(true);
        ConsoleHostSelectView.GlassEdge edge = new ConsoleHostSelectView.GlassEdge(corner,
                activity.getResources().getDisplayMetrics().density, CARD_FILL);
        card.setBackground(edge.fillDrawable());
        card.setForeground(edge);
        frame.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        ImageView art = new ImageView(activity);
        art.setImageResource(feature.card);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        content.addView(art, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ART_HEIGHT_DP)));
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(12), dp(8));
        TextView title = text(activity.getString(feature.title), ConsolePanel.LABEL_SP - 1,
                ConsoleEntryShell.TEXT, medium);
        title.setMaxLines(2);
        body.addView(title, wrap(0, 7));
        TextView summary = text(activity.getString(feature.summary), ConsolePanel.CAPTION_SP,
                ConsoleEntryShell.TEXT_SECONDARY, Typeface.DEFAULT);
        summary.setLineSpacing(dp(2), 1f);
        body.addView(summary, wrap(0, 0));
        content.addView(body);
        card.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView badge = badge(feature);
        if (badge != null) {
            FrameLayout.LayoutParams at = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(15), Gravity.TOP | Gravity.START);
            at.setMargins(dp(8), dp(8), 0, 0);
            card.addView(badge, at);
        }

        frame.setOnFocusChangeListener((view, focused) -> {
            focus.setFocused(focused);
            ConsoleCardFocus.scale(frame, focused ? FOCUS_SCALE : 1f, reducedMotion);
            if (!focused) return;
            shell.setAccent(accent);
            renderDots(features.indexOf(feature));
        });
        frame.setOnClickListener(view -> showDetail(feature));
        frame.setContentDescription(activity.getString(feature.title) + ". "
                + activity.getString(feature.summary));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(CARD_WIDTH_DP) + 2 * inset(), dp(CARD_HEIGHT_DP) + 2 * inset());
        frame.setLayoutParams(params);
        return frame;
    }

    private TextView badge(Feature feature) {
        if (!feature.isNew && !feature.experimental) return null;
        // Experimental outranks NEW: the card shows one badge.
        boolean experiment = feature.experimental;
        TextView badge = text(activity.getString(experiment ? R.string.feature_badge_experimental
                : R.string.feature_badge_new), 8.5f, experiment ? EXPERIMENT_INK : NEW_INK, medium);
        badge.setLetterSpacing(.04f);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xC80A0C12);
        pill.setCornerRadius(dp(8));
        badge.setBackground(pill);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(6), 0, dp(6), 0);
        return badge;
    }

    private void renderDots(int current) {
        dots.removeAllViews();
        if (features.size() < 2) return;
        for (int index = 0; index < features.size(); index++) {
            View dot = new View(activity);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(index == current ? DOT_CURRENT : DOT);
            dot.setBackground(shape);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(4), dp(4));
            params.leftMargin = index == 0 ? 0 : dp(7);
            dots.addView(dot, params);
        }
        dots.setContentDescription((current + 1) + " / " + features.size());
    }

    private void showDetail(Feature feature) {
        open = feature;
        boolean toggle = feature.hasDefaultToggle() && defaults != null
                && defaults.available(feature);
        detailPage.setHeader(eyebrow, activity.getString(feature.heading),
                activity.getString(feature.needs));
        scene.setImageResource(feature.scene);
        where.setText(feature == Feature.HAPTICS
                ? activity.getString(feature.where) + "\n" + HapticsPadSupport.describe(activity)
                : activity.getString(feature.where));
        where.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_console_info),
                null, null, null);

        sideRows.removeAllViews();
        TextView heading = text(activity.getString(R.string.feature_how_it_works),
                ConsolePanel.LABEL_SP, ConsoleEntryShell.TEXT, medium);
        heading.setPadding(dp(14), 0, dp(14), 0);
        sideRows.addView(heading, wrap(0, 10));
        int accent = accent(feature);
        // A step may name how the stream menu opens with the user's settings.
        String trigger = PreferenceConfiguration.menuGesture(activity);
        for (int index = 0; index < feature.steps.length; index++) {
            sideRows.addView(step(index + 1, activity.getString(feature.steps[index], trigger),
                    accent));
        }
        View focusTarget = detail;
        if (toggle) {
            LinearLayout row = panel.toggle(activity.getString(R.string.feature_default_toggle),
                    defaults.isOn(feature));
            row.setOnClickListener(view -> {
                defaults.set(feature, !defaults.isOn(feature));
                panel.setChecked(row, defaults.isOn(feature));
            });
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dp(4);
            sideRows.addView(row, rowParams);
            focusTarget = row;
        }
        side.scrollTo(0, 0);
        detailLegend(toggle);
        shell.setAccent(accent);
        carouselPage.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        shell.show(detailPage);
        View target = focusTarget;
        target.post(target::requestFocus);
    }

    /** One numbered step: the number in the scene's colour, as on its frame. */
    private View step(int number, String value, int accent) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(14), 0, dp(14), dp(10));
        TextView digit = text(String.valueOf(number), 10f, ConsolePanel.INK, medium);
        digit.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(accent);
        digit.setBackground(circle);
        LinearLayout.LayoutParams digitParams = new LinearLayout.LayoutParams(dp(17), dp(17));
        digitParams.topMargin = dp(1);
        row.addView(digit, digitParams);
        TextView body = text(value, ConsolePanel.SECONDARY_SP, ConsoleEntryShell.TEXT_SECONDARY,
                Typeface.DEFAULT);
        body.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bodyParams.leftMargin = dp(10);
        row.addView(body, bodyParams);
        return row;
    }

    private void back() {
        if (open != null) {
            int index = features.indexOf(open);
            open = null;
            carouselPage.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            carouselLegend();
            shell.show(carouselPage);
            View card = cards.getChildAt(Math.max(0, index));
            if (card != null) card.post(card::requestFocus);
            return;
        }
        dialog.dismiss();
    }

    private void carouselLegend() {
        carouselPage.clearLegend();
        confirmKey(carouselPage, R.string.feature_legend_more);
        if (onNext != null && pad) {
            carouselPage.addLegendKey(ControllerGlyphs.text(playStation,
                    ControllerGlyphs.Button.WEST), true,
                    activity.getString(R.string.feature_legend_next));
        }
        backKey(carouselPage, R.string.feature_legend_back);
    }

    private void detailLegend(boolean toggle) {
        detailPage.clearLegend();
        if (toggle) confirmKey(detailPage, R.string.feature_legend_toggle);
        if (features.size() > 1) {
            detailPage.addLegendKey("◄ ►", false,
                    activity.getString(R.string.feature_legend_browse));
        }
        backKey(detailPage, R.string.feature_legend_back_features);
    }

    private void confirmKey(ConsoleEntryShell.Page page, int label) {
        if (pad) {
            page.addLegendKey(ControllerGlyphs.text(playStation, ControllerGlyphs.Button.CONFIRM),
                    true, activity.getString(label));
        } else {
            page.addLegendKey("OK", false, activity.getString(label));
        }
    }

    private void backKey(ConsoleEntryShell.Page page, int label) {
        if (pad) {
            page.addLegendKey(ControllerGlyphs.text(playStation, ControllerGlyphs.Button.CANCEL),
                    true, activity.getString(label));
        } else {
            page.addLegendIcon(R.drawable.ic_console_back, activity.getString(label));
        }
    }

    private static int accent(Feature feature) {
        return Color.HSVToColor(new float[]{feature.hue * 360f, .5f, 1f});
    }

    private android.graphics.drawable.Drawable icon(int id) {
        android.graphics.drawable.Drawable drawable = activity.getDrawable(id).mutate();
        drawable.setTint(ConsoleEntryShell.TEXT_SECONDARY);
        drawable.setBounds(0, 0, dp(14), dp(14));
        return drawable;
    }

    /** The entry screens' full-screen dialog: opaque, undimmed, edge to edge. */
    static void makeOpaque(Dialog dialog, int background) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(background));
        window.setFormat(PixelFormat.OPAQUE);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND
                | WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.alpha = 1f;
        attributes.dimAmount = 0f;
        attributes.width = ViewGroup.LayoutParams.MATCH_PARENT;
        attributes.height = ViewGroup.LayoutParams.MATCH_PARENT;
        window.setAttributes(attributes);
    }

    private TextView text(String value, float size, int color, Typeface typeface) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(typeface);
        view.setIncludeFontPadding(false);
        return view;
    }

    private LinearLayout.LayoutParams wrap(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(top), 0, dp(bottom));
        return params;
    }

    /** Frame padding around a card: half of the pitch beyond its width, room for the ring. */
    private int inset() {
        return Math.round((PITCH_DP - CARD_WIDTH_DP) / 2f
                * activity.getResources().getDisplayMetrics().density);
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
