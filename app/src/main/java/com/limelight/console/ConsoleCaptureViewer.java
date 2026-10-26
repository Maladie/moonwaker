package com.limelight.console;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.limelight.R;
import com.limelight.gateway.GatewayConnection;
import com.limelight.ui.ControllerGlyphs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * A capture full screen (#51, mockups 25j-25k): a screenshot's preview JPEG, or a clip played
 * in a {@link VideoView} from a downloaded cache file. The hints (title, counter, time bar,
 * legend) fade after a few seconds and come back on any key. Back returns to the gallery.
 */
final class ConsoleCaptureViewer {
    private static final long HINTS_MS = 4_000;
    private static final int SEEK_MS = 5_000;
    private static final String CLIP_PREFIX = "capture-clip-";

    /** The gallery behind the viewer. */
    interface Owner {
        Bitmap thumbnail(String captureId);
        /** Asks, then deletes on the PC; {@code deleted} runs once the host did. */
        void delete(CaptureList.Capture capture, Runnable deleted);
        /** The viewer closed on {@code captureId}. */
        void closed(String captureId);
    }

    private final Activity activity;
    private final HostGatewayClient client;
    private final GatewayConnection connection;
    private final ExecutorService executor;
    private final CaptureList list;
    private final Owner owner;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Dialog dialog;
    private final ImageView image;
    private final VideoView video;
    private final View hints;
    private final TextView title;
    private final TextView line;
    private final TextView counter;
    private final LinearLayout timeBar;
    private final TextView played;
    private final TextView length;
    private final ConsoleTileProgressBar progress;
    private final LinearLayout legend;
    private final Runnable hideHints = this::hideHints;
    private final Runnable tick = this::tick;
    private int index;
    /** Bumped per shown capture, so a late download or preview is dropped. */
    private int token;
    /** The shown capture's preview or clip download; stopped on the next one or on Back. */
    private Future<?> loading;

    ConsoleCaptureViewer(Activity activity, HostGatewayClient client,
                         GatewayConnection connection, ExecutorService executor,
                         CaptureList list,
                         int index, Owner owner) {
        this.activity = activity;
        this.client = client;
        this.connection = connection;
        this.executor = executor;
        this.list = list;
        this.index = Math.max(0, index);
        this.owner = owner;

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.BLACK);
        image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, match());
        video = new VideoView(activity);
        root.addView(video, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        FrameLayout overlay = new FrameLayout(activity);
        hints = overlay;
        root.addView(overlay, match());
        overlay.addView(scrim(GradientDrawable.Orientation.TOP_BOTTOM),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150),
                        Gravity.TOP));
        overlay.addView(scrim(GradientDrawable.Orientation.BOTTOM_TOP),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200),
                        Gravity.BOTTOM));
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        title = text(22, ConsolePanel.TEXT, true);
        header.addView(title);
        line = text(13, activity.getColor(R.color.console_text_secondary), false);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lineParams.topMargin = dp(10);
        header.addView(line, lineParams);
        FrameLayout.LayoutParams headerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        headerParams.setMargins(dp(48), dp(37), dp(48), 0);
        overlay.addView(header, headerParams);
        counter = text(13, activity.getColor(R.color.console_text_secondary), false);
        FrameLayout.LayoutParams counterParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        counterParams.setMargins(0, dp(42), dp(48), 0);
        overlay.addView(counter, counterParams);

        timeBar = new LinearLayout(activity);
        timeBar.setGravity(Gravity.CENTER_VERTICAL);
        played = text(13, ConsolePanel.TEXT, false);
        timeBar.addView(played);
        progress = new ConsoleTileProgressBar(activity);
        progress.setFillColor(ConsoleTileBadge.BLUE);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(0, dp(4), 1f);
        barParams.setMargins(dp(12), 0, dp(12), 0);
        timeBar.addView(progress, barParams);
        length = text(13, activity.getColor(R.color.console_text_secondary), false);
        timeBar.addView(length);
        FrameLayout.LayoutParams timeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        timeParams.setMargins(dp(48), 0, dp(48), dp(80));
        overlay.addView(timeBar, timeParams);

        legend = new LinearLayout(activity);
        legend.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams legendParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        legendParams.bottomMargin = dp(36);
        overlay.addView(legend, legendParams);

        dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.setOnKeyListener((ignored, keyCode, event) -> onKey(keyCode, event));
        dialog.setOnDismissListener(ignored -> {
            token++;
            stopLoading();
            main.removeCallbacks(hideHints);
            main.removeCallbacks(tick);
            video.stopPlayback();
            deleteClipFiles();
            owner.closed(current().id);
        });
        video.setOnPreparedListener(player -> {
            length.setText(CaptureList.duration(player.getDuration()));
            video.start();
            tick();
        });
        video.setOnCompletionListener(player -> showHints());
        video.setOnErrorListener((player, what, extra) -> {
            ConsoleUiFeedback.makeText(activity, R.string.console_captures_open_failed,
                    Toast.LENGTH_LONG).show();
            return true;
        });
    }

    void show() {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        open();
    }

    private CaptureList.Capture current() {
        return list.captures.get(index);
    }

    private void open() {
        int shown = ++token;
        CaptureList.Capture capture = current();
        stopLoading();
        video.stopPlayback();
        deleteClipFiles();
        title.setText(ConsoleCaptureGallery.name(activity, capture));
        counter.setText((index + 1) + " / " + list.captures.size());
        image.setImageBitmap(owner.thumbnail(capture.id));
        image.setVisibility(View.VISIBLE);
        video.setVisibility(View.GONE);
        timeBar.setVisibility(capture.clip ? View.VISIBLE : View.INVISIBLE);
        fillLegend(capture.clip);
        showHints();
        String when = ConsoleCaptureGallery.when(activity, capture, true);
        if (capture.clip) {
            line.setText(activity.getString(R.string.console_captures_downloading));
            played.setText(CaptureList.duration(0));
            length.setText(CaptureList.duration(capture.durationMs));
            progress.setProgress(0, true);
            File file = new File(activity.getCacheDir(), CLIP_PREFIX + shown + ".mp4");
            loading = executor.submit(() -> {
                boolean ok;
                try {
                    client.downloadCaptureClip(connection, capture.id, file);
                    ok = true;
                } catch (IOException | RuntimeException error) {
                    ok = false;
                }
                boolean downloaded = ok;
                main.post(() -> {
                    if (shown != token || !dialog.isShowing()) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                        return;
                    }
                    line.setText(when + "  ·  " + activity.getString(
                            R.string.console_captures_clip_length,
                            CaptureList.duration(capture.durationMs)));
                    if (!downloaded) {
                        failed();
                        return;
                    }
                    image.setVisibility(View.GONE);
                    video.setVisibility(View.VISIBLE);
                    video.setVideoURI(Uri.fromFile(file));
                });
            });
            return;
        }
        line.setText(when + "  ·  " + activity.getString(R.string.console_captures_screenshot)
                + (capture.width > 0 && capture.height > 0
                ? "  ·  " + capture.width + " × " + capture.height : ""));
        loading = executor.submit(() -> {
            Bitmap bitmap;
            try {
                byte[] bytes = client.captureImage(connection, capture.id, true);
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } catch (IOException | RuntimeException error) {
                bitmap = null;
            }
            Bitmap preview = bitmap;
            main.post(() -> {
                if (shown != token || !dialog.isShowing()) return;
                if (preview == null) failed();
                else image.setImageBitmap(preview);
            });
        });
    }

    /** An interrupted download leaves no file (GatewayTransport.downloadToFile). */
    private void stopLoading() {
        if (loading != null) loading.cancel(true);
        loading = null;
    }

    private void failed() {
        ConsoleUiFeedback.makeText(activity, R.string.console_captures_open_failed,
                Toast.LENGTH_LONG).show();
    }

    private boolean onKey(int keyCode, KeyEvent event) {
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        if (down) showHints();
        boolean clip = current().clip;
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_BUTTON_B:
                if (up) dialog.dismiss();
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_BUTTON_START:
                if (up) owner.delete(current(), dialog::dismiss);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!down) return true;
                int step = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                if (clip) {
                    video.seekTo(Math.max(0, video.getCurrentPosition() + step * SEEK_MS));
                    tick();
                } else if (event.getRepeatCount() == 0 && index + step >= 0
                        && index + step < list.captures.size()) {
                    index += step;
                    open();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (up && clip) {
                    if (video.isPlaying()) video.pause();
                    else video.start();
                    fillLegend(true);
                    tick();
                }
                return true;
            default:
                return false;
        }
    }

    /** Mockups 25j/25k: what the keys do here. */
    private void fillLegend(boolean clip) {
        boolean playStation = ControllerGlyphs.hasPlayStationController();
        List<DiscordCommunityLegend.Item> items = new ArrayList<>();
        if (clip) {
            items.add(new DiscordCommunityLegend.Item(ControllerGlyphs.text(playStation,
                    ControllerGlyphs.Button.CONFIRM), true, activity.getString(video.isPlaying()
                    || !video.isShown() ? R.string.console_captures_pause
                    : R.string.console_captures_play)));
            items.add(new DiscordCommunityLegend.Item("◀ ▶", false,
                    activity.getString(R.string.console_captures_seek)));
        } else {
            items.add(new DiscordCommunityLegend.Item("◀ ▶", false,
                    activity.getString(R.string.console_captures_prev_next)));
        }
        items.add(new DiscordCommunityLegend.Item(ControllerGlyphs.text(playStation,
                ControllerGlyphs.Button.MENU), true,
                activity.getString(R.string.console_captures_delete)));
        items.add(new DiscordCommunityLegend.Item(ControllerGlyphs.text(playStation,
                ControllerGlyphs.Button.CANCEL), true,
                activity.getString(R.string.console_captures_gallery)));
        DiscordCommunityLegend.fillRow(legend, items);
    }

    private void tick() {
        main.removeCallbacks(tick);
        if (!dialog.isShowing() || !current().clip) return;
        int duration = video.getDuration();
        int position = video.getCurrentPosition();
        if (duration > 0) {
            played.setText(CaptureList.duration(position));
            progress.setProgress(Math.min(100, position * 100 / duration), true);
        }
        if (video.isPlaying()) main.postDelayed(tick, 250);
    }

    private void showHints() {
        hints.animate().cancel();
        hints.setAlpha(1f);
        main.removeCallbacks(hideHints);
        main.postDelayed(hideHints, HINTS_MS);
    }

    private void hideHints() {
        hints.animate().alpha(0f).setDuration(400).start();
    }

    private void deleteClipFiles() {
        File[] files = activity.getCacheDir().listFiles(
                (directory, name) -> name.startsWith(CLIP_PREFIX));
        if (files == null) return;
        for (File file : files) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private View scrim(GradientDrawable.Orientation orientation) {
        View scrim = new View(activity);
        scrim.setBackground(new GradientDrawable(orientation, new int[]{0xB3000000, 0}));
        return scrim;
    }

    private TextView text(float sp, int color, boolean medium) {
        TextView view = new TextView(activity);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        if (medium) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
