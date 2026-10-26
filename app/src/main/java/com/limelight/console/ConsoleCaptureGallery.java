package com.limelight.console;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.gateway.GatewayConnection;
import com.limelight.ui.ControllerGlyphs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Settings › Library › Screenshots and clips (#51, mockups 25g-25l): the profile's captures on
 * the PC as a page of the settings screen, the full-screen viewer and deletion. The host lists
 * only the actor's own captures (a child sees its own) and moves a deleted file to the PC's
 * Recycle Bin. No network on the UI thread.
 */
final class ConsoleCaptureGallery implements ConsoleCaptureViewer.Owner {
    private static final String LEGEND_SEPARATOR = "  ·  ";

    private final Activity activity;
    private final HostGatewayClient client;
    private final ExecutorService executor;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ConsoleCaptureViews views;
    /** Thumbnails of the shown page, by capture id. */
    private final Map<String, Bitmap> thumbnails = new ConcurrentHashMap<>();
    private final Map<String, ConsoleCaptureViews.Tile> tiles = new HashMap<>();
    private ConsoleSettingsScreen settings;
    private GatewayConnection connection;
    private String back;
    private CaptureList list;
    /** A view of the page shown last; its page is gone once it is detached. */
    private View pageMark;
    /** Bumped by every new page and on leaving it, so a late answer is dropped. */
    private volatile int generation;
    /** The page's thumbnail loader; stopped when the page goes. */
    private Future<?> loader;

    ConsoleCaptureGallery(Activity activity, HostGatewayClient client, ExecutorService executor) {
        this.activity = activity;
        this.client = client;
        this.executor = executor;
        views = new ConsoleCaptureViews(activity);
    }

    /** Opens the page at once with the skeleton (25i); the host's list fills it. */
    void show(ConsoleSettingsScreen settings, GatewayConnection connection, String back) {
        this.settings = settings;
        this.connection = connection;
        this.back = back;
        thumbnails.clear();
        int page = ++generation;
        List<View> rows = new ArrayList<>();
        for (int section = 0; section < 2; section++) {
            for (View row : views.skeletonSection()) rows.add(row);
        }
        mark(rows.get(0));
        settings.showPage(title(), activity.getString(R.string.console_captures_loading),
                rows.toArray(new View[0]), 0, back,
                activity.getString(R.string.console_legend_back_to, back));
        executor.execute(() -> {
            CaptureList result;
            String reason = "";
            try {
                result = client.captures(connection);
            } catch (IOException | RuntimeException error) {
                result = null;
                reason = StreamCaptures.reason(error);
            }
            CaptureList answer = result;
            String failure = reason;
            main.post(() -> {
                if (!current(page)) return;
                if (answer == null) failed(failure);
                else render(answer, null);
            });
        });
    }

    /** The list did not come; a drive that is gone says so, for the adult to pick another. */
    private void failed(String reason) {
        list = null;
        tiles.clear();
        TextView retry = settings.panel().action(activity.getString(R.string.disk_space_retry));
        retry.setOnClickListener(v -> show(settings, connection, back));
        mark(retry);
        settings.showPage(title(), activity.getString("capture_drive_unavailable".equals(reason)
                        ? R.string.console_captures_drive_gone
                        : R.string.console_captures_unavailable), new View[]{retry}, 0, back);
    }

    private void render(CaptureList answer, String focusId) {
        list = answer;
        tiles.clear();
        ConsolePanel panel = settings.panel();
        String folder = answer.folder.isEmpty() ? ""
                : activity.getString(R.string.console_captures_folder, answer.folder);
        if (answer.captures.isEmpty()) {
            TextView refresh = panel.action(activity.getString(R.string.console_captures_refresh));
            refresh.setOnClickListener(v -> show(settings, connection, back));
            mark(refresh);
            settings.showPage(title(), folder, new View[]{views.empty(
                    activity.getString(R.string.console_captures_empty_title),
                    activity.getString(R.string.console_captures_empty_hint)), refresh}, 1, back);
            return;
        }
        List<View> rows = new ArrayList<>();
        int focusRow = 1;
        ConsoleCaptureViews.Tile focus = null;
        for (CaptureList.Section section : answer.sections(System.currentTimeMillis(),
                TimeZone.getDefault())) {
            rows.add(panel.section(dayLabel(activity, section.day, section.dayStartMs)));
            for (int start = 0; start < section.captures.size(); start += 3) {
                List<View> line = new ArrayList<>();
                for (CaptureList.Capture capture : section.captures.subList(start,
                        Math.min(start + 3, section.captures.size()))) {
                    ConsoleCaptureViews.Tile tile = tile(capture);
                    if (capture.id.equals(focusId)) {
                        focus = tile;
                        focusRow = rows.size();
                    }
                    line.add(tile.view);
                }
                rows.add(views.row(line));
            }
        }
        mark(rows.get(0));
        settings.showPage(title(), folder, rows.toArray(new View[0]), focusRow, back, legend());
        if (focus != null) focus.view.requestFocus();
        loadThumbnails(generation, new ArrayList<>(answer.captures));
    }

    private ConsoleCaptureViews.Tile tile(CaptureList.Capture capture) {
        String detail = time(activity, capture.createdMs) + LEGEND_SEPARATOR
                + activity.getString(capture.clip ? R.string.console_captures_clip
                : R.string.console_captures_screenshot);
        ConsoleCaptureViews.Tile tile = views.tile(capture, name(activity, capture), detail,
                () -> new ConsoleCaptureViewer(activity, client, connection, executor, list,
                        list.indexOf(capture.id), this).show(),
                () -> delete(capture, null));
        Bitmap thumbnail = thumbnails.get(capture.id);
        if (thumbnail != null) tile.setPicture(thumbnail);
        tiles.put(capture.id, tile);
        return tile;
    }

    /**
     * The page's thumbnails one after another, newest first.
     * ponytail: every thumbnail of the list (at most 200 JPEGs of 480 px, RGB_565 about 260 KB
     * each) stays in memory while the page lives; load by scroll position if that gets tight.
     */
    private void loadThumbnails(int page, List<CaptureList.Capture> captures) {
        if (loader != null) loader.cancel(true);
        loader = executor.submit(() -> {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            for (CaptureList.Capture capture : captures) {
                if (page != generation) return;
                if (thumbnails.containsKey(capture.id)) continue;
                Bitmap bitmap;
                try {
                    byte[] bytes = client.captureImage(connection, capture.id, false);
                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                } catch (IOException | RuntimeException unavailable) {
                    continue;
                }
                if (bitmap == null) continue;
                main.post(() -> {
                    if (page != generation) return;
                    thumbnails.put(capture.id, bitmap);
                    ConsoleCaptureViews.Tile tile = tiles.get(capture.id);
                    if (tile != null) tile.setPicture(bitmap);
                });
            }
        });
    }

    // ------------------------------------------------------------------ viewer and deletion

    @Override
    public Bitmap thumbnail(String captureId) {
        return thumbnails.get(captureId);
    }

    @Override
    public void closed(String captureId) {
        ConsoleCaptureViews.Tile tile = tiles.get(captureId);
        if (tile != null && tile.view.isAttachedToWindow()) tile.view.requestFocus();
    }

    /** Mockup 25l: the focus on Cancel; the file goes to the PC's Recycle Bin. */
    @Override
    public void delete(CaptureList.Capture capture, Runnable deleted) {
        String message = activity.getString(R.string.console_captures_delete_message,
                name(activity, capture), when(activity, capture, false));
        ConsoleConfirmDialog.showDestructive(activity, activity.getString(capture.clip
                        ? R.string.console_captures_delete_clip_title
                        : R.string.console_captures_delete_screenshot_title), message,
                activity.getString(R.string.console_cancel),
                activity.getString(R.string.console_captures_delete),
                () -> deleteOnHost(capture, deleted),
                views.confirmPicture(thumbnails.get(capture.id)), null);
    }

    private void deleteOnHost(CaptureList.Capture capture, Runnable deleted) {
        int page = generation;
        executor.execute(() -> {
            String reason;
            try {
                client.deleteCapture(connection, capture.id);
                reason = "";
            } catch (IOException | RuntimeException error) {
                reason = StreamCaptures.reason(error);
            }
            String answer = reason;
            main.post(() -> {
                if (!current(page)) return;
                if ("capture_not_found".equals(answer)) {
                    // Already gone on the PC: no viewer on it, the list as the host has it now.
                    if (deleted != null) deleted.run();
                    show(settings, connection, back);
                    return;
                }
                if (!answer.isEmpty()) {
                    ConsoleUiFeedback.makeText(activity, "recycle_bin_unavailable".equals(answer)
                            ? R.string.console_captures_delete_no_bin
                            : R.string.console_captures_delete_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                int index = list.indexOf(capture.id);
                CaptureList rest = list.without(capture.id);
                thumbnails.remove(capture.id);
                String next = rest.captures.isEmpty() ? null
                        : rest.captures.get(Math.min(index, rest.captures.size() - 1)).id;
                render(rest, next);
                if (deleted != null) deleted.run();
            });
        });
    }

    /** {@code view} stands for the shown page: gone without a successor, the page is left. */
    private void mark(View view) {
        pageMark = view;
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View attached) {
            }

            @Override public void onViewDetachedFromWindow(View detached) {
                detached.removeOnAttachStateChangeListener(this);
                // A new page of the gallery has its own mark by then; anything else left it.
                main.post(() -> {
                    if (pageMark != null && !pageMark.isAttachedToWindow()) leave();
                });
            }
        });
    }

    /** Off the page: no more fetching, and its thumbnails go. */
    private void leave() {
        generation++;
        pageMark = null;
        if (loader != null) loader.cancel(true);
        loader = null;
        thumbnails.clear();
        tiles.clear();
    }

    private boolean current(int page) {
        return page == generation && settings.isShowing() && pageMark != null
                && pageMark.isAttachedToWindow();
    }

    // ------------------------------------------------------------------ words

    private String title() {
        return activity.getString(R.string.console_captures_title);
    }

    /** "OK Otwórz  ·  ☰ Usuń z PC  ·  Wstecz Biblioteka". */
    private CharSequence legend() {
        return TextUtils.concat(activity.getString(R.string.console_captures_legend_open),
                LEGEND_SEPARATOR, ControllerGlyphs.chord(activity, "%1$s "
                        + activity.getString(R.string.console_captures_delete),
                        ControllerGlyphs.hasPlayStationController(),
                        ControllerGlyphs.Button.MENU),
                LEGEND_SEPARATOR, activity.getString(R.string.console_legend_back_to, back));
    }

    static String name(Context context, CaptureList.Capture capture) {
        return capture.gameName.isEmpty()
                ? context.getString(R.string.console_captures_desktop) : capture.gameName;
    }

    static String dayLabel(Context context, CaptureList.Day day, long dayMs) {
        switch (day) {
            case TODAY:
                return context.getString(R.string.console_captures_today);
            case YESTERDAY:
                return context.getString(R.string.console_captures_yesterday);
            default:
                return DateUtils.formatDateTime(context, dayMs,
                        DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR);
        }
    }

    static String time(Context context, long ms) {
        return DateFormat.getTimeFormat(context).format(new Date(ms));
    }

    /** "Dziś, 21:47" in the viewer, "dziś 21:47" inside a sentence. */
    static String when(Context context, CaptureList.Capture capture, boolean heading) {
        CaptureList.Day kind = CaptureList.day(capture.createdMs, System.currentTimeMillis(),
                TimeZone.getDefault());
        String day = dayLabel(context, kind, capture.createdMs);
        String time = time(context, capture.createdMs);
        if (heading) return context.getString(R.string.console_captures_day_time, day, time);
        // "Today"/"Yesterday" are sentence words there; a date keeps its own case.
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        return (kind == CaptureList.Day.EARLIER ? day : day.toLowerCase(locale)) + " " + time;
    }
}
