package com.limelight.console;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.FocusFinder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.limelight.R;
import com.limelight.ui.ControllerGlyphs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The first-run wizard's screen (mockups first-run/01-09): a page of the entry shell, so it
 * shares the entry screens' background, header, clock and legend, and the console's PIN page
 * and side panels open over it and close back to it. Progress dots on top, then the step's body
 * built from the parts below: pills, glass cards, list rows with a status mark or a switch,
 * avatars, choice cards, a code keypad.
 *
 * <p>A step is drawn once per {@link Screen#shape}; a later render of the same shape only runs
 * the shown screen's binds, which update texts and marks in place, so a refresh never rebuilds
 * the page or moves focus. Focus stays inside the body: D-pad search never leaves it.
 */
final class FirstRunWizardPage {
    static final int TEXT = ConsoleEntryShell.TEXT;
    static final int TEXT_SECONDARY = ConsoleEntryShell.TEXT_SECONDARY;
    static final int HEADER = ConsoleEntryShell.HEADER;
    static final int INK = ConsolePanel.INK;
    static final int INK_SECONDARY = ConsolePanel.INK_SECONDARY;
    static final int FOCUS_FILL = 0xFFF2F3F7;
    static final int CARD_FILL = 0xD90F121C;
    static final int CARD_EDGE = 0x24FFFFFF;
    static final int PILL_FILL = 0xB3161A26;
    static final int PILL_EDGE = 0x3DFFFFFF;
    static final int OK = 0xFF34D399;
    static final int WARN = 0xFFF7C548;
    static final int ACCENT = 0xFF5AC8FA;
    static final int RING = 0x8CFFFFFF;
    /** The body's band: under the header's subtitle, above the legend (y 340-948 px). */
    static final int BODY_TOP_DP = 170;
    static final int BODY_HEIGHT_DP = 304;
    static final int PILL_HEIGHT_DP = 34;
    static final int ROW_RADIUS_DP = 12;
    static final int CARD_RADIUS_DP = 18;
    static final int AVATAR_DP = 88;

    /** One step as drawn: header, body, initial focus, in-place updates and legend. */
    static final class Screen {
        /** Same shape, same views: a render only runs {@link #binds}. */
        final String shape;
        String eyebrow = "";
        String title = "";
        String subtitle = "";
        View body;
        /** Focus when the step opens or its shape changes and the focused part is gone. */
        View focus;
        final List<Runnable> binds = new ArrayList<>();
        final List<Key> legend = new ArrayList<>();

        Screen(String shape) {
            this.shape = shape;
        }

        /** Runs now and on every later render of this shape. */
        void bind(Runnable bind) {
            binds.add(bind);
            bind.run();
        }

        void chip(String key, String label) {
            legend.add(new Key(key, null, 0, label));
        }

        void glyph(String glyph, String label) {
            legend.add(new Key(null, glyph, 0, label));
        }

        void icon(int icon, String label) {
            legend.add(new Key(null, null, icon, label));
        }
    }

    /** One legend entry: a key chip ("OK"), a controller glyph or an icon. */
    static final class Key {
        final String chip;
        final String glyph;
        final int icon;
        final String label;

        Key(String chip, String glyph, int icon, String label) {
            this.chip = chip;
            this.glyph = glyph;
            this.icon = icon;
            this.label = label;
        }

        String signature() {
            return chip + "|" + glyph + "|" + icon + "|" + label;
        }
    }

    /** A list row's parts, for binds. */
    static final class Row {
        final LinearLayout view;
        final TextView title;
        final TextView detail;
        final Mark mark;
        final Switch toggle;

        Row(LinearLayout view, TextView title, TextView detail, Mark mark, Switch toggle) {
            this.view = view;
            this.title = title;
            this.detail = detail;
            this.mark = mark;
            this.toggle = toggle;
        }
    }

    private final Activity activity;
    private final ConsoleEntryShell shell;
    private final ConsoleEntryShell.Page page;
    private final LinearLayout dots;
    private final Trap body;
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private Screen shown;
    private String dotsShown = "";
    private String legendShown = "";
    private boolean showing;
    private boolean ownsShell;
    private boolean reducedMotion;

    FirstRunWizardPage(Activity activity, ConsoleEntryShell shell, boolean reducedMotion) {
        this.activity = activity;
        this.shell = shell;
        this.reducedMotion = reducedMotion;
        page = shell.addPage();

        dots = new LinearLayout(activity);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams dotsParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(12), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        // Mockup: dots centred on y = 100 px.
        dotsParams.topMargin = dp(44);
        page.addView(dots, dotsParams);

        body = new Trap(activity);
        body.setClipChildren(false);
        body.setClipToPadding(false);
        FrameLayout.LayoutParams bodyParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(BODY_HEIGHT_DP), Gravity.TOP);
        bodyParams.topMargin = dp(BODY_TOP_DP);
        page.addView(body, bodyParams);
    }

    void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
    }

    // --- Rendering --------------------------------------------------------------------------

    /**
     * Draws a step. The same shape as on screen only runs its binds. A new shape replaces the
     * body; focus then goes to the step's initial part when the user moved ({@code userMove}),
     * else stays on the part with the focused part's tag, and is never taken from outside the
     * page (a console screen over it).
     */
    void render(int steps, int current, Screen screen, boolean userMove) {
        renderDots(steps, current);
        page.setHeader(screen.eyebrow, screen.title, screen.subtitle);
        renderLegend(screen.legend);
        if (!showing) show();
        if (shown != null && shown.shape.equals(screen.shape) && screen.body != null) {
            for (Runnable bind : shown.binds) bind.run();
            return;
        }
        View focused = activity.getWindow().getDecorView().findFocus();
        boolean focusHere = focused == null || isInside(body, focused)
                || !isInside(activity.getWindow().getDecorView(), focused);
        Object tag = focused != null && isInside(body, focused) ? focused.getTag() : null;
        body.removeAllViews();
        shown = screen;
        body.addView(screen.body, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        if (!userMove && !focusHere) return;
        View target = !userMove && tag != null ? screen.body.findViewWithTag(tag) : null;
        if (target == null || !target.isFocusable() || !target.isEnabled()) target = screen.focus;
        if (target != null) target.requestFocus();
    }

    /** The shape on screen, "" when none. */
    String shape() {
        return shown == null ? "" : shown.shape;
    }

    /** The view with {@code tag} on screen, e.g. to focus it again after a console screen. */
    View find(String tag) {
        return body.findViewWithTag(tag);
    }

    /** Takes focus back into the body when it is elsewhere, e.g. after a dialog. */
    void reclaimFocus() {
        View focused = activity.getWindow().getDecorView().findFocus();
        if (shown == null || shown.focus == null) return;
        if (focused == null || !isInside(body, focused)) shown.focus.requestFocus();
    }

    private void renderDots(int steps, int current) {
        String key = steps + ":" + current;
        if (key.equals(dotsShown)) return;
        dotsShown = key;
        dots.removeAllViews();
        for (int index = 0; index < steps; index++) {
            View dot = new View(activity);
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(dp(4));
            if (index <= current) shape.setColor(index == current ? TEXT : 0xD9FFFFFF);
            else shape.setStroke(Math.max(1, dp(.75f)), 0x99FFFFFF);
            dot.setBackground(shape);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(index == current ? 14 : 5), dp(index == current ? 6 : 5));
            params.leftMargin = index == 0 ? 0 : dp(10);
            dots.addView(dot, params);
        }
        dots.setContentDescription((current + 1) + " / " + steps);
    }

    private void renderLegend(List<Key> legend) {
        StringBuilder signature = new StringBuilder();
        for (Key key : legend) signature.append(key.signature()).append(';');
        if (signature.toString().equals(legendShown)) return;
        legendShown = signature.toString();
        page.clearLegend();
        for (Key key : legend) {
            if (key.icon != 0) page.addLegendIcon(key.icon, key.label);
            else if (key.glyph != null) page.addLegendKey(key.glyph, true, key.label);
            else page.addLegendKey(key.chip, false, key.label);
        }
    }

    /** Shows the page again, e.g. after the console's PIN page that opened over it. */
    void show() {
        if (!showing) {
            showing = true;
            ownsShell = shell.getVisibility() != View.VISIBLE;
        }
        shell.show(page);
    }

    /** Leaves the shell to whatever the caller shows next, or hides it if it was hidden. */
    void hide() {
        if (!showing) return;
        showing = false;
        shown = null;
        dotsShown = "";
        legendShown = "";
        body.removeAllViews();
        if (ownsShell) shell.setVisibility(View.GONE);
        ownsShell = false;
    }

    boolean isShowing() {
        return showing;
    }

    /** True while this page, not a PIN page or another entry screen, is on the shell. */
    boolean onScreen() {
        return showing && shell.shows(page);
    }

    // --- Parts ------------------------------------------------------------------------------

    TextView text(CharSequence value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(bold ? medium : Typeface.DEFAULT);
        view.setIncludeFontPadding(false);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    /** Text that wraps to at most {@code lines} lines of {@code widthDp}. */
    TextView wrapped(CharSequence value, float sizeSp, int color, boolean bold, int lines) {
        TextView view = text(value, sizeSp, color, bold);
        view.setMaxLines(lines);
        view.setLineSpacing(dp(3), 1f);
        return view;
    }

    LinearLayout column(int gravity) {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(gravity);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        return column;
    }

    LinearLayout line(int gravity) {
        LinearLayout line = column(gravity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        return line;
    }

    /** Adds {@code child} {@code widthDp} wide (or wrapped/matched) with a top margin. */
    <T extends View> T add(ViewGroup parent, T child, int widthDp, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                widthDp > 0 ? dp(widthDp) : widthDp, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topDp);
        parent.addView(child, params);
        return child;
    }

    /** Adds {@code child} beside the previous one with a left margin; 0 width takes the rest. */
    <T extends View> T beside(ViewGroup parent, T child, int widthDp, int leftDp) {
        LinearLayout.LayoutParams params = widthDp == 0
                ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                : new LinearLayout.LayoutParams(widthDp > 0 ? dp(widthDp) : widthDp,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(leftDp);
        params.gravity = Gravity.CENTER_VERTICAL;
        parent.addView(child, params);
        return child;
    }

    /** A rounded translucent glass card (mockup "card"). */
    LinearLayout card(int padXDp, int padYDp) {
        LinearLayout card = column(Gravity.START);
        card.setBackground(glass(CARD_RADIUS_DP, CARD_FILL, CARD_EDGE));
        card.setPadding(dp(padXDp), dp(padYDp), dp(padXDp), dp(padYDp));
        return card;
    }

    /** A centred section label, e.g. "Language". */
    TextView label(String value) {
        TextView label = text(value, 13, HEADER, true);
        label.setGravity(Gravity.CENTER);
        return label;
    }

    /**
     * A pill button (mockup "pill"): glass at rest, white with dark ink when focused;
     * {@link #highlight} draws it in the accent before it has focus.
     */
    TextView pill(String key, CharSequence label, int icon, Runnable click) {
        TextView pill = text(label, 15, TEXT, true);
        pill.setMaxLines(1);
        pill.setTag(key);
        pill.setGravity(Gravity.CENTER);
        pill.setMinWidth(dp(110));
        pill.setPadding(dp(22), 0, dp(22), 0);
        pill.setHeight(dp(PILL_HEIGHT_DP));
        pill.setTextColor(new ColorStateList(new int[][]{
                {android.R.attr.state_focused}, {android.R.attr.state_activated}, {}},
                new int[]{INK, ACCENT, TEXT}));
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused},
                glass(PILL_HEIGHT_DP / 2f, FOCUS_FILL, 0));
        background.addState(new int[]{android.R.attr.state_activated},
                glass(PILL_HEIGHT_DP / 2f, 0x33288CE6, ACCENT));
        background.addState(new int[]{}, glass(PILL_HEIGHT_DP / 2f, PILL_FILL, PILL_EDGE));
        pill.setBackground(background);
        if (icon != 0) {
            Drawable drawable = activity.getDrawable(icon).mutate();
            drawable.setTintList(pill.getTextColors());
            drawable.setBounds(0, 0, dp(16), dp(16));
            pill.setCompoundDrawablesRelative(drawable, null, null, null);
            pill.setCompoundDrawablePadding(dp(10));
        }
        focusable(pill, click);
        return pill;
    }

    void highlight(TextView pill, boolean on) {
        pill.setActivated(on);
    }

    /** Pills side by side, centred, in reading (and D-pad) order. */
    LinearLayout pills(View... items) {
        LinearLayout row = line(Gravity.CENTER);
        for (View item : items) {
            if (item == null) continue;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(PILL_HEIGHT_DP));
            params.leftMargin = row.getChildCount() == 0 ? 0 : dp(24);
            row.addView(item, params);
        }
        return row;
    }

    /**
     * A list row (mockups 05, 08): an optional mark, a title over a detail line and an optional
     * switch; white with dark ink when focused. A null {@code click} leaves it out of focus.
     */
    Row row(String key, boolean withMark, CharSequence title, CharSequence detail,
            boolean withSwitch, Runnable click) {
        LinearLayout view = line(Gravity.CENTER_VERTICAL);
        view.setTag(key);
        view.setPadding(dp(14), dp(6), dp(14), dp(6));
        view.setMinimumHeight(dp(38));
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused},
                glass(ROW_RADIUS_DP, FOCUS_FILL, 0));
        view.setBackground(background);
        Mark mark = null;
        if (withMark) {
            mark = new Mark(this);
            mark.setDuplicateParentStateEnabled(true);
            view.addView(mark, new LinearLayout.LayoutParams(dp(18), dp(18)));
        }
        LinearLayout words = column(Gravity.START);
        TextView titleView = wrapped(title, 15, TEXT, true, 2);
        titleView.setTextColor(inked(TEXT, INK));
        titleView.setDuplicateParentStateEnabled(true);
        words.addView(titleView);
        TextView detailView = null;
        if (detail != null) {
            detailView = wrapped(detail, 12, TEXT_SECONDARY, false, 2);
            detailView.setTextColor(inked(TEXT_SECONDARY, INK_SECONDARY));
            detailView.setDuplicateParentStateEnabled(true);
            add(words, detailView, ViewGroup.LayoutParams.MATCH_PARENT, 3);
        }
        words.setDuplicateParentStateEnabled(true);
        beside(view, words, 0, withMark ? 14 : 0);
        Switch toggle = null;
        if (withSwitch) {
            toggle = new Switch(activity);
            toggle.setDuplicateParentStateEnabled(true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(32), dp(18));
            params.leftMargin = dp(16);
            view.addView(toggle, params);
        }
        if (click != null) focusable(view, click);
        return new Row(view, titleView, detailView, mark, toggle);
    }

    /** Text colours that turn to ink on the focused row's white fill. */
    ColorStateList inked(int rest, int focused) {
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{focused, rest});
    }

    /** A numbered step badge (mockup 02): a white disc with a dark number. */
    TextView number(int value) {
        TextView badge = text(String.valueOf(value), 13, INK, true);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(FOCUS_FILL);
        badge.setBackground(disc);
        return badge;
    }

    /** A key chip as in the legend ("PS", "MENU"). */
    TextView keyChip(String key) {
        TextView chip = text(key, 12, TEXT, true);
        chip.setMaxLines(1);
        chip.setGravity(Gravity.CENTER);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(12));
        shape.setColor(0x1EFFFFFF);
        shape.setStroke(Math.max(1, dp(.75f)), 0x50FFFFFF);
        chip.setBackground(shape);
        chip.setMinWidth(dp(26));
        chip.setPadding(dp(7), dp(4), dp(7), dp(4));
        return chip;
    }

    /** A controller button in the glyph font. */
    TextView controllerGlyph(String glyph) {
        TextView view = text(glyph, 22, TEXT, false);
        view.setTypeface(ControllerGlyphs.typeface(activity));
        view.setGravity(Gravity.CENTER);
        return view;
    }

    ImageView icon(int icon, int color) {
        ImageView view = new ImageView(activity);
        view.setImageResource(icon);
        view.setColorFilter(color);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return view;
    }

    /** A QR code on a white plate, for a link the user opens on a phone. */
    ImageView qr(String url, int sizeDp) {
        ImageView code = new ImageView(activity);
        code.setImageBitmap(qrBitmap(url, dp(sizeDp)));
        code.setContentDescription(url);
        code.setBackground(glass(10, Color.WHITE, 0));
        int pad = dp(sizeDp / 14f);
        code.setPadding(pad, pad, pad, pad);
        code.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return code;
    }

    /**
     * An avatar column (mockup 04): the profile's round art (or a plus), its name and a line
     * under it; a white ring and glow when focused.
     */
    LinearLayout avatar(String key, HostGatewayClient.IntegrationProfile profile, String name,
                        String detail, Runnable click) {
        LinearLayout column = column(Gravity.CENTER_HORIZONTAL);
        column.setTag(key);
        int margin = dp(ConsoleCardFocus.MARGIN_DP);
        FrameLayout frame = new FrameLayout(activity);
        frame.setPadding(margin, margin, margin, margin);
        frame.setClipChildren(false);
        frame.setClipToPadding(false);
        ConsoleCardFocus focus = new ConsoleCardFocus(activity.getResources(), margin,
                dp(AVATAR_DP) / 2f);
        focus.setRing(3f, 2f);
        if (profile != null) focus.setGlowColor(HostTileArt.accent(profile.id));
        frame.setBackground(focus);
        if (profile != null) {
            ConsoleProfileSelectView.addAvatar(frame, profile, AVATAR_DP);
        } else {
            View disc = new View(activity);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(0x66141826);
            shape.setStroke(Math.max(1, dp(1)), 0x33FFFFFF);
            disc.setBackground(shape);
            frame.addView(disc);
            ImageView plus = icon(R.drawable.ic_console_plus, TEXT);
            frame.addView(plus, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
        }
        int size = dp(AVATAR_DP) + 2 * margin;
        column.addView(frame, new LinearLayout.LayoutParams(size, size));
        TextView nameView = text(name, 16, TEXT, true);
        nameView.setMaxLines(1);
        nameView.setGravity(Gravity.CENTER);
        add(column, nameView, ViewGroup.LayoutParams.MATCH_PARENT, 12);
        TextView detailView = wrapped(detail, 12, TEXT_SECONDARY, false, 2);
        detailView.setGravity(Gravity.CENTER);
        add(column, detailView, ViewGroup.LayoutParams.MATCH_PARENT, 5);
        focusable(column, click);
        column.setOnFocusChangeListener((view, focused) -> {
            focus.setFocused(focused);
            ConsoleCardFocus.scale(frame, focused ? 1.06f : 1f, reducedMotion);
        });
        return column;
    }

    /**
     * A choice card (mockup 07): an icon, a title and two lines; a lighter fill, a white ring
     * and glow when focused. A null {@code click} dims it out of focus.
     */
    FrameLayout choiceCard(String key, int icon, String title, String detail, Runnable click) {
        int margin = dp(ConsoleCardFocus.MARGIN_DP);
        FrameLayout frame = new FrameLayout(activity);
        frame.setTag(key);
        frame.setPadding(margin, margin, margin, margin);
        frame.setClipChildren(false);
        frame.setClipToPadding(false);
        ConsoleCardFocus focus = new ConsoleCardFocus(activity.getResources(), margin,
                dp(CARD_RADIUS_DP));
        focus.setRing(3f, 2f);
        focus.setGlowColor(0xFF6FC3FF);
        frame.setBackground(focus);
        LinearLayout card = column(Gravity.CENTER_HORIZONTAL);
        StateListDrawable fill = new StateListDrawable();
        fill.addState(new int[]{android.R.attr.state_focused},
                glass(CARD_RADIUS_DP, 0xF2213444, CARD_EDGE));
        fill.addState(new int[]{}, glass(CARD_RADIUS_DP, CARD_FILL, CARD_EDGE));
        card.setBackground(fill);
        card.setDuplicateParentStateEnabled(true);
        card.setPadding(dp(16), dp(30), dp(16), dp(24));
        card.addView(icon(icon, TEXT), new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView titleView = wrapped(title, 16, TEXT, true, 2);
        titleView.setGravity(Gravity.CENTER);
        add(card, titleView, ViewGroup.LayoutParams.MATCH_PARENT, 18);
        TextView detailView = wrapped(detail, 12, TEXT_SECONDARY, false, 3);
        detailView.setGravity(Gravity.CENTER);
        add(card, detailView, ViewGroup.LayoutParams.MATCH_PARENT, 9);
        frame.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (click == null) {
            frame.setAlpha(.45f);
        } else {
            focusable(frame, click);
            frame.setOnFocusChangeListener((view, focused) -> focus.setFocused(focused));
        }
        return frame;
    }

    /** A square digit cell of the pairing code (mockup 03); the next one to fill is lit. */
    TextView digit() {
        TextView cell = text("", 24, TEXT, true);
        cell.setGravity(Gravity.CENTER);
        StateListDrawable fill = new StateListDrawable();
        fill.addState(new int[]{android.R.attr.state_activated},
                glass(10, 0x5C3A4258, 0xF2FFFFFF, 1.5f));
        fill.addState(new int[]{}, glass(10, 0x99161A26, PILL_EDGE));
        cell.setBackground(fill);
        return cell;
    }

    /** A keypad key: a digit, or an icon when {@code icon} is not 0. */
    TextView keypadKey(String key, String label, int icon, Runnable click) {
        TextView pad = text(label, 18, TEXT, false);
        pad.setTag(key);
        pad.setGravity(Gravity.CENTER);
        pad.setTextColor(inked(TEXT, INK));
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused}, glass(10, FOCUS_FILL, 0));
        background.addState(new int[]{}, glass(10, 0x99161A26, PILL_EDGE));
        pad.setBackground(background);
        if (icon != 0) {
            Drawable drawable = activity.getDrawable(icon).mutate();
            drawable.setTintList(pad.getTextColors());
            drawable.setBounds(0, 0, dp(18), dp(18));
            pad.setCompoundDrawablesRelative(drawable, null, null, null);
            pad.setPadding(dp(21), 0, 0, 0);
            pad.setGravity(Gravity.CENTER_VERTICAL);
        }
        focusable(pad, click);
        return pad;
    }

    /** A big round status sign (mockup 09): a green disc with a dark check. */
    Mark bigCheck() {
        Mark mark = new Mark(this);
        mark.set(Mark.OK);
        return mark;
    }

    /** A host tile with its art and monitor, ringed by the wake test's progress (mockup 05b). */
    HostTile hostTile(String hostId) {
        HostTile tile = new HostTile(this, HostTileArt.bitmap(hostId));
        return tile;
    }

    /** Clickable, focusable, D-pad reachable; OK runs {@code click}. */
    void focusable(View view, Runnable click) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setClickable(true);
        view.setOnClickListener(ignored -> click.run());
    }

    GradientDrawable glass(float radiusDp, int fill, int edge) {
        return glass(radiusDp, fill, edge, 1f);
    }

    GradientDrawable glass(float radiusDp, int fill, int edge, float edgeDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(radiusDp));
        shape.setColor(fill);
        if (edge != 0) shape.setStroke(Math.max(1, dp(edgeDp)), edge);
        return shape;
    }

    static Bitmap qrBitmap(String value, int size) {
        Bitmap bitmap;
        try {
            BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size,
                    Collections.singletonMap(EncodeHintType.MARGIN, 0));
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    pixels[y * size + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
        } catch (WriterException | IllegalArgumentException impossible) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }
        return bitmap;
    }

    static boolean isInside(View ancestor, View view) {
        for (Object parent = view; parent instanceof View; parent = ((View) parent).getParent()) {
            if (parent == ancestor) return true;
        }
        return false;
    }

    int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    boolean reducedMotion() {
        return reducedMotion;
    }

    /** The body: D-pad search stays inside it, so focus never reaches screens behind. */
    private static final class Trap extends FrameLayout {
        Trap(Activity activity) {
            super(activity);
        }

        @Override
        public View focusSearch(View focused, int direction) {
            View next = FocusFinder.getInstance().findNextFocus(this, focused, direction);
            return next != null ? next : focused;
        }
    }

    /**
     * A round status sign: a green check, a yellow "!", an empty ring, a turning arc or a small
     * accent bullet. In a focused row the ring and bullet take the row's ink.
     */
    static final class Mark extends View {
        static final int OK = 0;
        static final int ATTENTION = 1;
        static final int EMPTY = 2;
        static final int SPINNER = 3;
        static final int BULLET = 4;

        private final FirstRunWizardPage page;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path check = new Path();
        private final RectF oval = new RectF();
        private int kind = EMPTY;

        Mark(FirstRunWizardPage page) {
            super(page.activity);
            this.page = page;
        }

        void set(int kind) {
            if (this.kind == kind) return;
            this.kind = kind;
            invalidate();
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float size = Math.min(getWidth(), getHeight());
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = size / 2f;
            boolean inked = isDuplicateParentStateEnabled() && isPressedOrFocused();
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            switch (kind) {
                case OK:
                    paint.setColor(FirstRunWizardPage.OK);
                    canvas.drawCircle(cx, cy, radius, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(size * .11f);
                    paint.setColor(0xFF0B1A14);
                    check.reset();
                    check.moveTo(cx - size * .22f, cy + size * .01f);
                    check.lineTo(cx - size * .06f, cy + size * .16f);
                    check.lineTo(cx + size * .23f, cy - size * .15f);
                    canvas.drawPath(check, paint);
                    break;
                case ATTENTION:
                    paint.setColor(WARN);
                    canvas.drawCircle(cx, cy, radius, paint);
                    paint.setColor(0xFF231A05);
                    paint.setStrokeWidth(size * .13f);
                    paint.setStyle(Paint.Style.STROKE);
                    canvas.drawLine(cx, cy - size * .24f, cx, cy + size * .06f, paint);
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(cx, cy + size * .24f, size * .075f, paint);
                    break;
                case SPINNER:
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(size * .1f);
                    paint.setColor(0x33FFFFFF);
                    float inset = size * .07f;
                    oval.set(cx - radius + inset, cy - radius + inset, cx + radius - inset,
                            cy + radius - inset);
                    canvas.drawArc(oval, 0, 360, false, paint);
                    paint.setColor(ACCENT);
                    float start = page.reducedMotion ? -90f
                            : (AnimationUtils.currentAnimationTimeMillis() % 1100L) / 1100f * 360f;
                    canvas.drawArc(oval, start, 250, false, paint);
                    if (!page.reducedMotion && isShown()) postInvalidateOnAnimation();
                    break;
                case BULLET:
                    paint.setColor(inked ? INK : ACCENT);
                    canvas.drawCircle(cx, cy, size * .2f, paint);
                    break;
                default:
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(1f, size * .075f));
                    paint.setColor(inked ? INK_SECONDARY : RING);
                    canvas.drawCircle(cx, cy, radius - size * .06f, paint);
                    break;
            }
        }

        private boolean isPressedOrFocused() {
            for (int state : getDrawableState()) {
                if (state == android.R.attr.state_focused) return true;
            }
            return false;
        }
    }

    /** A row's switch (mockup 08): accent when on, grey when off, dark on a focused row. */
    static final class Switch extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF track = new RectF();
        private boolean on;

        Switch(Activity activity) {
            super(activity);
        }

        void setOn(boolean on) {
            if (this.on == on) return;
            this.on = on;
            invalidate();
        }

        boolean isOn() {
            return on;
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            boolean focused = false;
            for (int state : getDrawableState()) {
                if (state == android.R.attr.state_focused) focused = true;
            }
            float height = getHeight();
            track.set(0, 0, getWidth(), height);
            paint.setColor(on ? (focused ? INK : ACCENT) : (focused ? 0x55111319 : 0x4DFFFFFF));
            canvas.drawRoundRect(track, height / 2f, height / 2f, paint);
            float knob = height / 2f - height * .14f;
            paint.setColor(on ? Color.WHITE : 0xFFD9DCE3);
            canvas.drawCircle(on ? getWidth() - height / 2f : height / 2f, height / 2f, knob,
                    paint);
        }
    }

    /** The host's tile with the wake test's progress drawn along its edge in amber. */
    static final class HostTile extends View {
        private final FirstRunWizardPage page;
        private final Bitmap art;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path outline = new Path();
        private final Path part = new Path();
        private final Drawable monitor;
        private float progress;

        HostTile(FirstRunWizardPage page, Bitmap art) {
            super(page.activity);
            this.page = page;
            this.art = art;
            monitor = page.activity.getDrawable(R.drawable.ic_console_monitor_tile);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeCap(Paint.Cap.ROUND);
        }

        /** 0 to 1 around the edge. */
        void setProgress(float progress) {
            if (this.progress == progress) return;
            this.progress = progress;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float stroke = page.dp(3);
            float gap = page.dp(4);
            float corner = page.dp(18);
            rect.set(stroke / 2f, stroke / 2f, getWidth() - stroke / 2f, getHeight() - stroke / 2f);
            edge.setStrokeWidth(stroke);
            edge.setColor(0x33FFFFFF);
            canvas.drawRoundRect(rect, corner + gap, corner + gap, edge);
            outline.reset();
            outline.addRoundRect(rect, corner + gap, corner + gap, Path.Direction.CW);
            PathMeasure measure = new PathMeasure(outline, true);
            part.reset();
            measure.getSegment(0, measure.getLength() * progress, part, true);
            edge.setColor(WARN);
            canvas.drawPath(part, edge);
            float inset = stroke + gap;
            rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
            BitmapShader shader = new BitmapShader(art, Shader.TileMode.CLAMP,
                    Shader.TileMode.CLAMP);
            Matrix matrix = new Matrix();
            matrix.setRectToRect(new RectF(0, 0, art.getWidth(), art.getHeight()), rect,
                    Matrix.ScaleToFit.FILL);
            shader.setLocalMatrix(matrix);
            paint.setShader(shader);
            canvas.drawRoundRect(rect, corner, corner, paint);
            int icon = Math.round(rect.width() * .32f);
            int left = Math.round(rect.centerX() - icon / 2f);
            int top = Math.round(rect.centerY() - icon / 2f);
            monitor.setBounds(left, top, left + icon, top + icon);
            monitor.draw(canvas);
        }
    }
}
