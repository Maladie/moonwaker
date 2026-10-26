package com.limelight.console;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.ControllerGlyphs;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * The game mode window before a start (mockups .claude/handoffs/mockups/game-mode/24a-24d): the
 * game's blurred background artwork, its cover and name on the left with the count of programs
 * to close and the two actions, the PC's programs as glass tiles with their icon (or a
 * monogram) and a console switch on the right. It opens with skeleton tiles while the host
 * answers; "Play without closing" works from the first frame. Toggling a tile changes that tile
 * and the count only.
 */
final class GameModeWindow {
    interface Listener {
        void closeAndPlay(List<String> programIds);
        void playWithoutClosing();
        /** A hung program's tile after the close: the user asks to end it (not yet confirmed). */
        void forceClose(String programId, String name);
    }

    /** One program tile's views, changed in place by the result of a close. */
    private static final class Tile {
        final String name;
        final FrameLayout frame;
        final View icon;
        final TextView label;
        final TextView detail;
        final View toggle;
        final TextView badge;

        Tile(String name, FrameLayout frame, View icon, TextView label, TextView detail,
             View toggle, TextView badge) {
            this.name = name;
            this.frame = frame;
            this.icon = icon;
            this.label = label;
            this.detail = detail;
            this.toggle = toggle;
            this.badge = badge;
        }
    }

    private static final int CYAN = 0xFF73D7FF;
    private static final int AMBER = ConsoleTileBadge.AMBER;
    private static final int DANGER_TEXT = 0xFFFF7078;
    private static final int MUTED = 0xFF969EB0;
    private static final int SECONDARY = 0xFFC8CCD6;
    private static final int TILE_W_DP = 176;
    private static final int TILE_H_DP = 88;
    private static final int TILE_CORNER_DP = 12;
    private static final int COLUMNS = 3;
    private static final int SKELETON_TILES = 6;
    /** Sampled this small, the artwork scaled to the screen is its own blur. */
    private static final int BACKDROP_SAMPLE_PX = 96;

    private final Activity activity;
    private final Dialog dialog;
    private final Listener listener;
    private final LinearLayout grid;
    private final TextView header;
    private final TextView counter;
    private final TextView counterNote;
    private final TextView close;
    private final TextView skip;
    private final View closing;
    private final Set<String> checked = new LinkedHashSet<>();
    private final java.util.Map<String, Tile> tiles = new java.util.LinkedHashMap<>();
    private final TextView question;
    private boolean listed;

    private GameModeWindow(Activity activity, String gameName, File cover, File backdrop,
                           boolean windowsGameMode, boolean muteOthers, Listener listener,
                           Executor background) {
        this.activity = activity;
        this.listener = listener;
        dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(0xFF06080C);
        ImageView art = new ImageView(activity);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(art, match());
        View scrim = new View(activity);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xC706080C, 0x9906080C, 0x5906080C}));
        root.addView(scrim, match());

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setPadding(dp(62), dp(44), dp(20), dp(24));
        root.addView(content, match());

        LinearLayout left = new LinearLayout(activity);
        left.setOrientation(LinearLayout.VERTICAL);
        content.addView(left, new LinearLayout.LayoutParams(dp(290),
                ViewGroup.LayoutParams.MATCH_PARENT));
        left.addView(eyebrow(s(R.string.console_game_mode_label)), wrap());
        ImageView poster = new ImageView(activity);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(0x22FFFFFF);
        clipRound(poster, dp(10));
        poster.setElevation(dp(6));
        LinearLayout.LayoutParams posterParams = new LinearLayout.LayoutParams(dp(96), dp(128));
        posterParams.topMargin = dp(14);
        left.addView(poster, posterParams);
        TextView title = text(gameName, 25f, ConsolePanel.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        left.addView(title, margins(wrap(), 0, dp(26), 0, 0));
        question = text(s(R.string.console_game_mode_question), 13.5f, SECONDARY, false);
        left.addView(question, margins(wrap(), 0, dp(2), 0, 0));
        counter = text(s(R.string.console_game_mode_checking), 16f, SECONDARY, true);
        left.addView(counter, margins(wrap(), 0, dp(24), 0, 0));
        counterNote = text(s(R.string.console_game_mode_checking_note), 11f, MUTED, false);
        left.addView(counterNote, margins(wrap(), 0, dp(2), 0, 0));
        LinearLayout chips = new LinearLayout(activity);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        if (windowsGameMode) chips.addView(chip(s(R.string.console_game_mode_windows_chip)));
        if (muteOthers) chips.addView(chip(s(R.string.console_game_mode_mute_chip)));
        left.addView(chips, margins(wrap(), 0, dp(14), 0, 0));
        View spacer = new View(activity);
        left.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));
        close = ConsoleConfirmDialog.action(activity, s(R.string.console_game_mode_close_and_play));
        skip = ConsoleConfirmDialog.action(activity, s(R.string.console_game_mode_play_without));
        left.addView(close, new LinearLayout.LayoutParams(dp(218), dp(38)));
        left.addView(skip, margins(new LinearLayout.LayoutParams(dp(218), dp(38)),
                0, dp(10), 0, 0));
        closing = closingRow();
        closing.setVisibility(View.GONE);
        left.addView(closing, new LinearLayout.LayoutParams(dp(260), dp(38)));
        LinearLayout legend = new LinearLayout(activity);
        legend.setGravity(Gravity.CENTER_VERTICAL);
        boolean playStation = ControllerGlyphs.hasPlayStationController();
        List<DiscordCommunityLegend.Item> items = new ArrayList<>();
        items.add(new DiscordCommunityLegend.Item(ControllerGlyphs.text(playStation,
                ControllerGlyphs.Button.CONFIRM), true, s(R.string.console_select_hint)));
        items.add(new DiscordCommunityLegend.Item(ControllerGlyphs.text(playStation,
                ControllerGlyphs.Button.CANCEL), true, s(R.string.console_cancel)));
        DiscordCommunityLegend.fillRow(legend, items);
        left.addView(legend, margins(wrap(), 0, dp(18), 0, 0));

        LinearLayout right = new LinearLayout(activity);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        rightParams.leftMargin = dp(3);
        content.addView(right, rightParams);
        header = eyebrow(s(R.string.console_game_mode_open_programs));
        right.addView(header, margins(wrap(), dp(10), dp(30), 0, dp(4)));
        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(24));
        grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(grid, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        right.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f));
        for (int index = 0; index < SKELETON_TILES; index++) addTile(skeletonTile(), index);

        close.setAlpha(.5f);
        close.setOnClickListener(view -> {
            if (!listed) return;
            showClosing();
            listener.closeAndPlay(new ArrayList<>(checked));
        });
        skip.setOnClickListener(view -> {
            dialog.dismiss();
            listener.playWithoutClosing();
        });
        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(false);
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        loadArtwork(background, art, backdrop, BACKDROP_SAMPLE_PX);
        loadArtwork(background, poster, cover, dp(108));
        dialog.show();
        close.requestFocus();
    }

    static GameModeWindow show(Activity activity, String gameName, File cover, File backdrop,
                               boolean windowsGameMode, boolean muteOthers, Listener listener,
                               Executor background) {
        return new GameModeWindow(activity, gameName, cover, backdrop, windowsGameMode,
                muteOthers, listener, background);
    }

    boolean isShowing() {
        return dialog.isShowing();
    }

    void dismiss() {
        if (dialog.isShowing()) dialog.dismiss();
    }

    /** Replaces the skeleton with the programs, all checked; focus stays on "Close and play". */
    void showPrograms(List<GameModeStep.Program> programs) {
        listed = true;
        grid.removeAllViews();
        checked.clear();
        for (int index = 0; index < programs.size(); index++) {
            GameModeStep.Program program = programs.get(index);
            checked.add(program.id);
            addTile(programTile(program), index);
        }
        header.setText(activity.getString(R.string.console_game_mode_open_count,
                s(R.string.console_game_mode_open_programs), programs.size())
                .toUpperCase(java.util.Locale.getDefault()));
        counterNote.setText(s(R.string.console_game_mode_unsaved_note));
        close.setAlpha(1f);
        updateCounter();
        if (!close.isFocused() && !skip.isFocused()) close.requestFocus();
    }

    void showClosing() {
        // Once programs close, Back no longer cancels: the game starts either way (24d).
        dialog.setCancelable(false);
        close.setVisibility(View.GONE);
        skip.setVisibility(View.GONE);
        closing.setVisibility(View.VISIBLE);
        closing.requestFocus();
        for (int index = 0; index < grid.getChildCount(); index++) {
            ViewGroup row = (ViewGroup) grid.getChildAt(index);
            for (int column = 0; column < row.getChildCount(); column++) {
                row.getChildAt(column).setFocusable(false);
            }
        }
    }

    /**
     * After the close, while the game already starts (mockup 24i): closed programs dim, those
     * still open say so, and each hung one offers "Force close"; "Done" closes the window.
     */
    void showResult(List<GameModeStep.Remaining> remaining) {
        java.util.Map<String, GameModeStep.Remaining> open = new java.util.HashMap<>();
        List<String> hung = new ArrayList<>();
        for (GameModeStep.Remaining program : remaining) {
            open.put(program.id, program);
            if (!program.responding) {
                hung.add(activity.getString(R.string.console_game_mode_hung, program.name));
            }
        }
        question.setText(s(R.string.console_game_mode_starting));
        counter.setText(activity.getString(R.string.console_game_mode_not_closed,
                TextUtils.join(", ", hung)));
        counter.setTextColor(AMBER);
        counterNote.setText(s(R.string.console_game_mode_result_note));
        closing.setVisibility(View.GONE);
        dialog.setCancelable(true);
        skip.setText(s(R.string.console_game_mode_done));
        skip.setOnClickListener(view -> dialog.dismiss());
        skip.setVisibility(View.VISIBLE);
        View first = null;
        for (java.util.Map.Entry<String, Tile> entry : tiles.entrySet()) {
            Tile tile = entry.getValue();
            GameModeStep.Remaining program = open.get(entry.getKey());
            tile.toggle.setVisibility(View.GONE);
            if (program == null) {
                if (checked.contains(entry.getKey())) markClosed(tile);
                continue;
            }
            if (program.responding) {
                tile.detail.setText(s(R.string.console_game_mode_still_open));
                continue;
            }
            tile.badge.setVisibility(View.VISIBLE);
            tile.detail.setText(s(R.string.console_game_mode_force_close));
            tile.detail.setTextColor(DANGER_TEXT);
            ((ConsoleCardFocus) tile.frame.getBackground()).setGlowColor(AMBER);
            tile.frame.setFocusable(true);
            tile.frame.setClickable(true);
            String id = entry.getKey();
            tile.frame.setOnClickListener(view -> listener.forceClose(id, tile.name));
            if (first == null) first = tile.frame;
        }
        (first != null ? first : skip).requestFocus();
    }

    /** The answer to a confirmed force close of {@code programId}. */
    void showForceResult(String programId, String outcome) {
        Tile tile = tiles.get(programId);
        if (tile == null) return;
        boolean ended = "closed".equals(outcome) || "gone".equals(outcome);
        if (ended || "responding".equals(outcome)) {
            tile.badge.setVisibility(View.GONE);
            tile.frame.setOnClickListener(null);
            tile.frame.setFocusable(false);
            tile.frame.setClickable(false);
            if (ended) {
                markClosed(tile);
            } else {
                tile.detail.setText(s(R.string.console_game_mode_responding_again));
                tile.detail.setTextColor(MUTED);
            }
            if (!hasFocusedTile()) skip.requestFocus();
        } else {
            tile.detail.setText(s(R.string.console_game_mode_force_failed));
        }
    }

    private boolean hasFocusedTile() {
        for (Tile tile : tiles.values()) if (tile.frame.isFocusable()) return true;
        return false;
    }

    private void markClosed(Tile tile) {
        tile.icon.setAlpha(.35f);
        tile.label.setAlpha(.55f);
        tile.detail.setText(s(R.string.console_game_mode_closed));
        tile.detail.setTextColor(MUTED);
    }

    private void updateCounter() {
        int count = checked.size();
        counter.setText(count == 0 ? s(R.string.console_game_mode_nothing_checked)
                : activity.getResources().getQuantityString(R.plurals.console_game_mode_count,
                count, count));
        counter.setTextColor(count == 0 ? SECONDARY : CYAN);
    }

    private void addTile(View tile, int index) {
        LinearLayout row;
        if (index % COLUMNS == 0) {
            row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            grid.addView(row, wrap());
        } else {
            row = (LinearLayout) grid.getChildAt(grid.getChildCount() - 1);
        }
        int margin = dp(ConsoleCardFocus.MARGIN_DP);
        row.addView(tile, new LinearLayout.LayoutParams(dp(TILE_W_DP) + 2 * margin,
                dp(TILE_H_DP) + 2 * margin));
    }

    /** A glass card inside a ConsoleCardFocus frame (glow and ring when focused). */
    private FrameLayout frame(boolean focusable) {
        FrameLayout frame = new FrameLayout(activity);
        int margin = dp(ConsoleCardFocus.MARGIN_DP);
        frame.setPadding(margin, margin, margin, margin);
        frame.setClipToPadding(false);
        ConsoleCardFocus focus = new ConsoleCardFocus(activity.getResources(), margin,
                dp(TILE_CORNER_DP));
        focus.setGlowColor(0xFF7896C8);
        focus.setShadowAlpha(.25f);
        frame.setBackground(focus);
        frame.setFocusable(focusable);
        frame.setClickable(focusable);
        frame.setSoundEffectsEnabled(false);
        FrameLayout card = new FrameLayout(activity);
        GradientDrawable glass = new GradientDrawable();
        glass.setColor(0x14FFFFFF);
        glass.setCornerRadius(dp(TILE_CORNER_DP));
        glass.setStroke(Math.max(1, dp(1) * 3 / 4), 0x2CFFFFFF);
        card.setBackground(glass);
        frame.addView(card, match());
        frame.setOnFocusChangeListener((view, hasFocus) -> {
            focus.setFocused(hasFocus);
            glass.setColor(hasFocus ? 0x22FFFFFF : 0x14FFFFFF);
        });
        return frame;
    }

    private View skeletonTile() {
        FrameLayout frame = frame(false);
        FrameLayout card = (FrameLayout) frame.getChildAt(0);
        card.addView(bone(8), boneAt(12, 12, 32, 32));
        card.addView(bone(4), boneAt(12, 58, 118, 8));
        card.addView(bone(3), boneAt(12, 73, 63, 6));
        return frame;
    }

    private View programTile(GameModeStep.Program program) {
        FrameLayout frame = frame(true);
        FrameLayout card = (FrameLayout) frame.getChildAt(0);
        View icon = programIcon(program);
        card.addView(icon, boneAt(12, 12, 32, 32));
        TextView name = text(program.name, 13.5f, ConsolePanel.TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams nameParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        nameParams.setMargins(dp(12), 0, dp(10), dp(22));
        card.addView(name, nameParams);
        TextView image = text(program.image, 10f, MUTED, false);
        image.setSingleLine(true);
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        imageParams.setMargins(dp(12), 0, dp(10), dp(8));
        card.addView(image, imageParams);
        TileSwitch toggle = new TileSwitch(activity);
        FrameLayout.LayoutParams toggleParams = new FrameLayout.LayoutParams(
                dp(ConsolePanel.TOGGLE_WIDTH_DP), dp(ConsolePanel.TOGGLE_HEIGHT_DP),
                Gravity.END | Gravity.TOP);
        toggleParams.setMargins(0, dp(14), dp(12), 0);
        card.addView(toggle, toggleParams);
        toggle.setChecked(true);
        TextView badge = text(s(R.string.console_game_mode_not_responding), 9.5f,
                ConsolePanel.INK, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(AMBER);
        pill.setCornerRadius(dp(9));
        badge.setBackground(pill);
        badge.setVisibility(View.GONE);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(18), Gravity.END | Gravity.TOP);
        badgeParams.setMargins(0, dp(13), dp(12), 0);
        card.addView(badge, badgeParams);
        tiles.put(program.id, new Tile(program.name, frame, icon, name, image, toggle, badge));
        frame.setContentDescription(program.name);
        frame.setOnClickListener(view -> {
            boolean on = !checked.remove(program.id);
            if (on) checked.add(program.id);
            toggle.setChecked(on);
            float alpha = on ? 1f : .5f;
            icon.setAlpha(alpha);
            name.setAlpha(alpha);
            updateCounter();
        });
        return frame;
    }

    private View programIcon(GameModeStep.Program program) {
        Bitmap bitmap = program.icon.length == 0 ? null
                : BitmapFactory.decodeByteArray(program.icon, 0, program.icon.length);
        if (bitmap != null) {
            ImageView view = new ImageView(activity);
            view.setImageBitmap(bitmap);
            return view;
        }
        String letter = program.name.isEmpty() ? "?"
                : program.name.substring(0, program.name.offsetByCodePoints(0, 1))
                .toUpperCase(java.util.Locale.ROOT);
        TextView view = text(letter, 15f, ConsolePanel.TEXT, true);
        view.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        float hue = (program.name.hashCode() & 0x7FFFFFFF) % 360;
        circle.setColor(Color.HSVToColor(new float[]{hue, .45f, .55f}));
        view.setBackground(circle);
        return view;
    }

    private View closingRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        ProgressBar progress = new ProgressBar(activity);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(ColorStateList.valueOf(CYAN));
        row.addView(progress, new LinearLayout.LayoutParams(dp(28), dp(28)));
        row.addView(text(s(R.string.console_game_mode_closing), 15f, ConsolePanel.TEXT, true),
                margins(wrap(), dp(12), 0, 0, 0));
        return row;
    }

    private TextView chip(String label) {
        TextView chip = text(label, 11f, ConsolePanel.TEXT, false);
        chip.setSingleLine(true);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(10), 0, dp(12), 0);
        chip.setCompoundDrawablesRelativeWithIntrinsicBounds(new CheckDot(dp(9)), null, null,
                null);
        chip.setCompoundDrawablePadding(dp(6));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(ConsoleTileBadge.OUTLINED_FILL & 0xC8FFFFFF);
        pill.setCornerRadius(dp(11));
        pill.setStroke(Math.max(1, dp(1) * 3 / 4), 0x2EFFFFFF);
        chip.setBackground(pill);
        chip.setLayoutParams(margins(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(22)), 0, 0, dp(6), 0));
        return chip;
    }

    private View bone(int cornerDp) {
        View bone = new View(activity);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0x1CFFFFFF);
        shape.setCornerRadius(dp(cornerDp));
        bone.setBackground(shape);
        return bone;
    }

    private FrameLayout.LayoutParams boneAt(int leftDp, int topDp, int widthDp, int heightDp) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(widthDp), dp(heightDp));
        params.setMargins(dp(leftDp), dp(topDp), 0, 0);
        return params;
    }

    private void loadArtwork(Executor background, ImageView target, File file, int widthPx) {
        if (file == null) return;
        background.execute(() -> {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, bounds.outWidth / Math.max(1, widthPx));
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap != null) target.post(() -> target.setImageBitmap(bitmap));
        });
    }

    private TextView eyebrow(String label) {
        TextView view = text(label.toUpperCase(java.util.Locale.getDefault()), 12f,
                ConsolePanel.HEADER, true);
        view.setLetterSpacing(.12f);
        return view;
    }

    private TextView text(String value, float sp, int color, boolean medium) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (medium) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    private static void clipRound(View view, int radius) {
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View target, Outline outline) {
                outline.setRoundRect(0, 0, target.getWidth(), target.getHeight(), radius);
            }
        });
        view.setClipToOutline(true);
    }

    private static <T extends ViewGroup.MarginLayoutParams> T margins(T params, int left,
                                                                       int top, int right,
                                                                       int bottom) {
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private String s(int resource) {
        return activity.getString(resource);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    /** The console switch of mockup 18 on a dark tile: accent track when on, ink knob. */
    private static final class TileSwitch extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF track = new RectF();
        private boolean on;

        TileSwitch(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setChecked(boolean checked) {
            if (on == checked) return;
            on = checked;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float height = getHeight();
            float radius = height / 2f;
            track.set(0, 0, getWidth(), height);
            paint.setColor(on ? CYAN : 0xFF5A606E);
            canvas.drawRoundRect(track, radius, radius, paint);
            paint.setColor(ConsolePanel.INK);
            canvas.drawCircle(on ? getWidth() - radius : radius, radius, height * 12f / 34f,
                    paint);
        }
    }

    /** The chip's "on" mark: an accent dot with an ink check. */
    private static final class CheckDot extends android.graphics.drawable.Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;

        CheckDot(int size) {
            this.size = size;
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override public int getIntrinsicWidth() {
            return size;
        }

        @Override public int getIntrinsicHeight() {
            return size;
        }

        @Override public void draw(Canvas canvas) {
            float r = getBounds().width() / 2f;
            float cx = getBounds().exactCenterX();
            float cy = getBounds().exactCenterY();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(CYAN);
            canvas.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(r / 3.2f);
            paint.setColor(ConsolePanel.INK);
            canvas.drawLine(cx - r * .45f, cy, cx - r * .1f, cy + r * .35f, paint);
            canvas.drawLine(cx - r * .1f, cy + r * .35f, cx + r * .5f, cy - r * .35f, paint);
        }

        @Override public void setAlpha(int alpha) {
        }

        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
        }

        @Override public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }
}
