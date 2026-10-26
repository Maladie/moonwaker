package com.limelight.console;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;

/**
 * A text field for console modals: the query line above the MoonWaker on-screen keyboard
 * ({@link EmbeddedTvKeyboardView}). The container owns focus and routes keys to the keyboard, so
 * no system IME ever opens. The keyboard's send key (and R2) runs {@code onSubmit}.
 */
final class ConsoleTextEntry extends LinearLayout {
    private static final String CARET = "│";
    private static final int ERROR = 0xFFFF8F88;

    private final TextView field;
    private final TextView error;
    private final EmbeddedTvKeyboardView keyboard;
    private final CharSequence hint;
    private final int hintColor;
    private final boolean numeric;
    private final int maxLength;
    private String text;
    private int cursor;

    ConsoleTextEntry(Context context, String initial, CharSequence hint, boolean numeric,
                     int maxLength, CharSequence submitLabel, Runnable onSubmit) {
        super(context);
        this.hint = hint == null ? "" : hint;
        this.numeric = numeric;
        this.maxLength = maxLength;
        text = initial == null ? "" : initial;
        cursor = text.length();
        setOrientation(VERTICAL);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setSoundEffectsEnabled(false);
        hintColor = context.getResources().getColor(R.color.console_text_secondary);
        // Spoken as a text field; every edit of the line is announced.
        setContentDescription(this.hint);

        field = new TextView(context);
        field.setTextSize(17);
        field.setTextColor(ConsolePanel.TEXT);
        field.setSingleLine(true);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(16), 0, dp(16), 0);
        field.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        addView(field, new LayoutParams(LayoutParams.MATCH_PARENT, dp(54)));

        error = new TextView(context);
        error.setTextSize(ConsolePanel.CAPTION_SP);
        error.setTextColor(ERROR);
        error.setPadding(dp(16), dp(6), dp(16), 0);
        error.setVisibility(GONE);
        addView(error, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        keyboard = new EmbeddedTvKeyboardView(context, new EmbeddedTvKeyboardView.Callback() {
            @Override public void onText(String value) {
                insert(value);
            }

            @Override public void onBackspace() {
                edit(EmbeddedTvKeyboardModel.backspace(text, cursor, cursor));
            }

            @Override public void onMoveCursor(int direction) {
                cursor = EmbeddedTvKeyboardModel.moveCursorByCodePoints(text, cursor, direction);
                show();
            }

            @Override public void onMicrophone() { }

            @Override public void onSend() {
                onSubmit.run();
            }

            @Override public void onOpenVisibleAdditionalContent() { }
        }, "", context.getString(R.string.discord_dm_keyboard_space), submitLabel.toString(),
                context.getString(R.string.discord_dm_keyboard_shift_legend),
                context.getString(R.string.discord_dm_keyboard_backspace_legend),
                context.getString(R.string.discord_dm_keyboard_cursor_legend), "", "", "", false);
        if (numeric) keyboard.showNumbers();
        LayoutParams keyboardParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        keyboardParams.topMargin = dp(10);
        addView(keyboard, keyboardParams);
        show();
    }

    String value() {
        return text;
    }

    void setError(CharSequence message) {
        error.setText(message);
        error.setVisibility(message == null ? GONE : VISIBLE);
    }

    /** Whether {@code event} was taken: an accent choice's cancel, or any key the keyboard uses. */
    boolean handleKey(KeyEvent event) {
        return keyboard.handleAccentCancel(event) || keyboard.handleNavigationKey(event);
    }

    @Override protected void onFocusChanged(boolean gainFocus, int direction,
                                            android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (!gainFocus) keyboard.cancelKeyboardHold();
        show();
    }

    private void insert(String value) {
        String accepted = value;
        if (numeric) accepted = value.replaceAll("[^0-9]", "");
        if (accepted.isEmpty()) return;
        if (maxLength > 0 && text.length() + accepted.length() > maxLength) return;
        edit(EmbeddedTvKeyboardModel.insert(text, cursor, cursor, accepted));
    }

    private void edit(EmbeddedTvKeyboardModel.Edit edit) {
        text = edit.text;
        cursor = edit.selection;
        setError(null);
        show();
    }

    private void show() {
        if (text.isEmpty()) {
            SpannableString empty = new SpannableString(CARET + hint);
            empty.setSpan(new ForegroundColorSpan(hintColor), 1, empty.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            field.setText(empty);
        } else {
            field.setText(text.substring(0, cursor) + CARET + text.substring(cursor));
        }
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF111820, 0xFF0A0F14});
        background.setCornerRadius(dp(10));
        background.setStroke(dp(isFocused() ? 2 : 1), isFocused() ? 0xFFBDEBFF : 0x66788A96);
        field.setBackground(background);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
