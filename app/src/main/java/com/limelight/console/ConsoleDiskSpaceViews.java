package com.limelight.console;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
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
 * Views of the disk space mockups (.claude/handoffs/mockups/disk-space, 23a-23c): drive cards
 * with a usage bar, game rows with cover, size badge and the library tile focus, and the
 * install picker's target rows. Pure views: data in, callbacks out; no host or network here.
 */
final class ConsoleDiskSpaceViews {
    private static final int CARD_FILL = 0x0EFFFFFF;
    private static final int CARD_FILL_FOCUSED = 0x1AFFFFFF;
    private static final int CARD_EDGE = 0x28FFFFFF;
    private static final int COVER_PLACEHOLDER = 0x22FFFFFF;
    private static final int WARNING_ON_FOCUS = 0xFF966408;
    private static final int CORNER_DP = 11;
    private static final int FOCUS_MARGIN_DP = 5;

    private final Context context;
    private final int secondary;
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    ConsoleDiskSpaceViews(Context context) {
        this.context = context;
        secondary = context.getColor(R.color.console_text_secondary);
    }

    // ------------------------------------------------------------------ drives (mockup 23c)

    /** Drive cards three to a line; not focusable (the game rows are the page's stops). */
    LinearLayout driveStrip(List<DiskSpace.Drive> drives) {
        LinearLayout strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.VERTICAL);
        strip.setFocusable(false);
        strip.setPadding(dp(4), dp(6), dp(4), dp(4));
        setDrives(strip, drives);
        return strip;
    }

    /** Refreshes the cards in place (after an uninstall) without touching the game rows. */
    void setDrives(LinearLayout strip, List<DiskSpace.Drive> drives) {
        strip.removeAllViews();
        LinearLayout line = null;
        for (int index = 0; index < drives.size(); index++) {
            if (index % 3 == 0) {
                line = new LinearLayout(context);
                line.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (index > 0) params.topMargin = dp(8);
                strip.addView(line, params);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (index % 3 > 0) params.leftMargin = dp(8);
            line.addView(driveCard(drives.get(index)), params);
        }
        // Keep the last line's cards the width of a full line's.
        for (int pad = drives.size() % 3; pad > 0 && pad < 3; pad++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.leftMargin = dp(8);
            line.addView(new View(context), params);
        }
    }

    private View driveCard(DiskSpace.Drive drive) {
        boolean low = DiskSpace.low(drive);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(9), dp(11), dp(11));
        card.setBackground(surface(CARD_FILL));
        FrameLayout top = new FrameLayout(context);
        top.addView(text(DiskSpace.driveLabel(drive.path), 17, ConsolePanel.TEXT, true),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL));
        if (low) {
            TextView badge = pill(context.getString(R.string.disk_space_low), 0xFF141006,
                    ConsoleTileBadge.AMBER, 0);
            top.addView(badge, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(20), Gravity.END | Gravity.CENTER_VERTICAL));
        }
        card.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                dp(22)));
        TextView free = text(context.getString(R.string.disk_space_drive_free,
                size(drive.freeBytes), size(drive.totalBytes)), 11,
                low ? ConsoleTileBadge.AMBER : secondary, false);
        LinearLayout.LayoutParams freeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        freeParams.topMargin = dp(6);
        card.addView(free, freeParams);
        card.addView(usageBar(drive, low), barParams(4, 6));
        card.setContentDescription(drive.path + ". " + free.getText());
        return card;
    }

    // ------------------------------------------------------------------ game rows (mockup 23c)

    /** One candidate: focusable, the library tile's ring and glow around a glass card. */
    static final class GameRow {
        final FrameLayout view;
        final String gameId;
        private final ImageView cover;
        private final TextView detail;
        private final ConsoleCardFocus focus;
        private Bitmap bitmap;
        private CharSequence restDetail;

        private GameRow(FrameLayout view, String gameId, ImageView cover, TextView detail,
                        ConsoleCardFocus focus) {
            this.view = view;
            this.gameId = gameId;
            this.cover = cover;
            this.detail = detail;
            this.focus = focus;
        }

        /** A cached cover; its average colour tints the focus glow as on library tiles. */
        void setCover(Bitmap bitmap) {
            if (bitmap == null) return;
            this.bitmap = bitmap;
            cover.setImageBitmap(bitmap);
            Bitmap pixel = Bitmap.createScaledBitmap(bitmap, 1, 1, true);
            focus.setGlowColor(pixel.getPixel(0, 0) | 0xFF000000);
            if (pixel != bitmap) pixel.recycle();
            view.invalidate();
        }

        /** Uninstall started or done: new words, no action, dimmed. */
        void settle(String words) {
            if (restDetail == null) restDetail = detail.getText();
            detail.setText(words);
            view.setAlpha(.6f);
            view.setTag(R.id.console_disk_space_settled, Boolean.TRUE);
        }

        /** The host refused the uninstall: back to the row as it was. */
        void restore() {
            if (restDetail != null) detail.setText(restDetail);
            view.setAlpha(1f);
            view.setTag(R.id.console_disk_space_settled, null);
        }

        /** The cover shown, or null before the cache answered. */
        Bitmap cover() {
            return bitmap;
        }

        boolean settled() {
            return Boolean.TRUE.equals(view.getTag(R.id.console_disk_space_settled));
        }
    }

    /** {@code panel} scrolls the page to the focused row like it does for its own rows. */
    GameRow gameRow(ConsolePanel panel, DiskSpace.Candidate candidate, String detail,
                    View.OnClickListener onClick) {
        FrameLayout frame = new FrameLayout(context);
        int margin = dp(FOCUS_MARGIN_DP);
        frame.setPadding(margin, margin, margin, margin);
        frame.setClipToPadding(false);
        ConsoleCardFocus focus = new ConsoleCardFocus(context.getResources(), margin,
                dp(CORNER_DP));
        frame.setBackground(focus);
        frame.setId(View.generateViewId());
        frame.setFocusable(true);
        frame.setClickable(true);
        frame.setSoundEffectsEnabled(false);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(7), dp(4), dp(12), dp(4));
        GradientDrawable fill = surface(CARD_FILL);
        card.setBackground(fill);
        frame.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView cover = new ImageView(context);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(COVER_PLACEHOLDER);
        rounded(cover, dp(5));
        card.addView(cover, new LinearLayout.LayoutParams(dp(36), dp(48)));

        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(candidate.name, 15, ConsolePanel.TEXT, false);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(name);
        TextView details = text(detail, 11, secondary, false);
        details.setSingleLine(true);
        details.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(4);
        words.addView(details, detailParams);
        LinearLayout.LayoutParams wordsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wordsParams.leftMargin = dp(11);
        wordsParams.rightMargin = dp(10);
        card.addView(words, wordsParams);

        card.addView(pill(size(candidate.sizeBytes), ConsolePanel.TEXT,
                ConsoleTileBadge.OUTLINED_FILL, 0x32FFFFFF),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(20)));

        GameRow row = new GameRow(frame, candidate.gameId, cover, details, focus);
        frame.setContentDescription(candidate.name + ", " + size(candidate.sizeBytes) + ". "
                + detail);
        frame.setOnFocusChangeListener((view, hasFocus) -> {
            focus.setFocused(hasFocus);
            fill.setColor(hasFocus ? CARD_FILL_FOCUSED : CARD_FILL);
            view.invalidate();
            if (hasFocus) panel.reveal(view);
        });
        frame.setOnClickListener(view -> {
            if (!row.settled()) onClick.onClick(view);
        });
        return row;
    }

    // ------------------------------------------------------------------ install picker (23a, 23b)

    /**
     * A target row of the install side panel: label and free space, the host default's path,
     * a usage bar, and under a drive too small for the game a warning. Focus: the panel's
     * near-white fill with dark ink. The game size may arrive after the panel opened.
     */
    final class TargetRow {
        final LinearLayout view;
        private final InstallLocations.Target target;
        private final TextView free;
        private final ConsoleTileProgressBar bar;
        private final TextView warning;

        private TargetRow(LinearLayout view, InstallLocations.Target target, TextView free,
                          ConsoleTileProgressBar bar, TextView warning) {
            this.view = view;
            this.target = target;
            this.free = free;
            this.bar = bar;
            this.warning = warning;
        }

        /** Shows the warning and the amber state when the game does not fit; 0 = unknown. */
        void setGameSize(long sizeBytes) {
            if (target == null) return;
            boolean tooSmall = DiskSpace.tooSmall(sizeBytes, target.freeBytes);
            boolean low = tooSmall || DiskSpace.low(target.freeBytes, target.totalBytes);
            free.setTextColor(colors(low ? ConsoleTileBadge.AMBER : ConsolePanel.TEXT,
                    low ? WARNING_ON_FOCUS : ConsolePanel.INK));
            bar.setFillColor(low ? ConsoleTileBadge.AMBER : ConsoleTileBadge.BLUE);
            bar.invalidate();
            warning.setText(tooSmall ? context.getString(R.string.install_location_too_small,
                    size(sizeBytes)) : "");
            warning.setVisibility(tooSmall ? View.VISIBLE : View.GONE);
        }
    }

    TargetRow targetRow(ConsolePanel panel, String label, String path,
                        InstallLocations.Target target) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setId(View.generateViewId());
        row.setFocusable(true);
        row.setClickable(true);
        row.setSoundEffectsEnabled(false);
        row.setBackground(panel.focusFill(10));
        row.setPadding(dp(ConsolePanel.ROW_INSET_DP), dp(9), dp(ConsolePanel.ROW_INSET_DP),
                dp(9));
        FrameLayout top = new FrameLayout(context);
        TextView title = stateful(text(label, ConsolePanel.LABEL_SP, ConsolePanel.TEXT, false),
                ConsolePanel.rowColors(ConsolePanel.TEXT));
        top.addView(title, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START));
        row.addView(top);
        TextView warning = stateful(text("", 11, ConsoleTileBadge.AMBER, false),
                colors(ConsoleTileBadge.AMBER, WARNING_ON_FOCUS));
        if (target == null) return new TargetRow(row, null, null, null, warning);
        TextView free = text(context.getString(R.string.disk_space_free,
                size(target.freeBytes)), 13, ConsolePanel.TEXT, true);
        free.setDuplicateParentStateEnabled(true);
        top.addView(free, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.BOTTOM));
        FrameLayout second = new FrameLayout(context);
        if (path != null && !path.isEmpty()) {
            second.addView(stateful(text(path, 11, secondary, false),
                    colors(secondary, ConsolePanel.INK_SECONDARY)));
        }
        TextView total = stateful(text(context.getString(R.string.disk_space_of_total,
                size(target.totalBytes)), 11, secondary, false),
                colors(secondary, ConsolePanel.INK_SECONDARY));
        second.addView(total, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END));
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        secondParams.topMargin = dp(3);
        row.addView(second, secondParams);
        ConsoleTileProgressBar bar = usageBar(new DiskSpace.Drive(target.path,
                target.freeBytes, target.totalBytes), false);
        row.addView(bar, barParams(4, 7));
        LinearLayout.LayoutParams warningParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        warningParams.topMargin = dp(6);
        row.addView(warning, warningParams);
        TargetRow result = new TargetRow(row, target, free, bar, warning);
        result.setGameSize(0);
        return result;
    }

    /** The install picker's first row: the game's cover, its size once known, its launcher. */
    final class GameHeader {
        final View view;
        private final ImageView cover;
        private final TextView size;

        private GameHeader(View view, ImageView cover, TextView size) {
            this.view = view;
            this.cover = cover;
            this.size = size;
        }

        void setCover(Bitmap bitmap) {
            cover.setImageBitmap(bitmap);
        }

        void setGameSize(long sizeBytes) {
            size.setText(sizeBytes > 0 ? context.getString(R.string.install_location_size,
                    ConsoleDiskSpaceViews.this.size(sizeBytes)) : "");
            size.setVisibility(sizeBytes > 0 ? View.VISIBLE : View.GONE);
        }
    }

    GameHeader gameHeader(String launcher) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(false);
        row.setPadding(dp(ConsolePanel.ROW_INSET_DP), dp(2), dp(ConsolePanel.ROW_INSET_DP),
                dp(10));
        ImageView cover = new ImageView(context);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(COVER_PLACEHOLDER);
        rounded(cover, dp(6));
        row.addView(cover, new LinearLayout.LayoutParams(dp(42), dp(56)));
        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView size = text("", 14, ConsolePanel.TEXT, true);
        LinearLayout.LayoutParams sizeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sizeParams.bottomMargin = dp(4);
        words.addView(size, sizeParams);
        if (launcher != null && !launcher.isEmpty()) {
            words.addView(text(launcher, 12, secondary, false));
        }
        LinearLayout.LayoutParams wordsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wordsParams.leftMargin = dp(12);
        row.addView(words, wordsParams);
        GameHeader header = new GameHeader(row, cover, size);
        header.setGameSize(0);
        return header;
    }

    /** Under the confirmation's message: the drive's bar after the uninstall, then a note. */
    View confirmExtra(DiskSpace.Drive after, String note) {
        LinearLayout extra = new LinearLayout(context);
        extra.setOrientation(LinearLayout.VERTICAL);
        extra.addView(usageBar(after, DiskSpace.low(after)), barParams(4, 10));
        TextView words = text(note, ConsolePanel.SECONDARY_SP, secondary, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        extra.addView(words, params);
        return extra;
    }

    /** The cover of the uninstall confirmation (mockup 23d). */
    ImageView confirmCover(Bitmap bitmap) {
        ImageView cover = new ImageView(context);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(COVER_PLACEHOLDER);
        if (bitmap != null) cover.setImageBitmap(bitmap);
        rounded(cover, dp(7));
        cover.setLayoutParams(new LinearLayout.LayoutParams(dp(75), dp(100)));
        return cover;
    }

    // ------------------------------------------------------------------ parts

    private ConsoleTileProgressBar usageBar(DiskSpace.Drive drive, boolean low) {
        ConsoleTileProgressBar bar = new ConsoleTileProgressBar(context);
        bar.setFillColor(low ? ConsoleTileBadge.AMBER : ConsoleTileBadge.BLUE);
        bar.setProgress(DiskSpace.usedPercent(drive), true);
        return bar;
    }

    private LinearLayout.LayoutParams barParams(int heightDp, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
        params.topMargin = dp(topDp);
        return params;
    }

    private GradientDrawable surface(int color) {
        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(dp(CORNER_DP));
        fill.setColor(color);
        fill.setStroke(Math.max(1, dp(.75f)), CARD_EDGE);
        return fill;
    }

    /** ConsoleTileBadge's outlined pill: dark fill, hairline, centred label. */
    private TextView pill(String label, int textColor, int fill, int edge) {
        TextView pill = text(label, 12, textColor, true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(10));
        background.setColor(fill);
        if (edge != 0) background.setStroke(Math.max(1, dp(.75f)), edge);
        pill.setBackground(background);
        ConsoleText.centreOnGlyphs(pill);
        return pill;
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

    private static TextView stateful(TextView view, ColorStateList colors) {
        view.setTextColor(colors);
        view.setDuplicateParentStateEnabled(true);
        return view;
    }

    private static ColorStateList colors(int rest, int focused) {
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{focused, rest});
    }

    private static void rounded(View view, int radius) {
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View target, Outline outline) {
                outline.setRoundRect(0, 0, target.getWidth(), target.getHeight(), radius);
            }
        });
        view.setClipToOutline(true);
    }

    private String size(long bytes) {
        return Formatter.formatShortFileSize(context, bytes);
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
