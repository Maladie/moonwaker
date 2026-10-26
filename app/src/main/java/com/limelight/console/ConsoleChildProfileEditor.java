package com.limelight.console;

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.binding.audio.UsbMicrophoneService;
import com.limelight.session.WatchNextTile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One child profile on the settings screen (mockups 20c-20e, 23a-23b): "Profile" holds the name,
 * avatar and switches, "Play time" a row per weekday that opens the day's page, "Games" the age
 * category with a preview of the parent's library. Times and limits step
 * by {@link #STEP_MINUTES} within bounds that follow the other values, so the draft is always
 * valid. The editor is a nested screen of Settings › Children; Back to that list hands the
 * draft to {@link Host#closed}. The caller owns authorization and persistence.
 */
final class ConsoleChildProfileEditor {
    static final int MINUTES_PER_DAY = 1440;
    static final int STEP_MINUTES = 5;
    private static final int DAY_COUNT = 7;
    private static final int WORKDAYS = 5;
    private static final int[] DAY_LABELS = {
            R.string.console_child_day_mon,
            R.string.console_child_day_tue,
            R.string.console_child_day_wed,
            R.string.console_child_day_thu,
            R.string.console_child_day_fri,
            R.string.console_child_day_sat,
            R.string.console_child_day_sun
    };

    interface Host {
        /** Asks for the child's name; {@code onName} gets a non-empty one. */
        void editName(String current, Consumer<String> onName);
        /** The avatar grid of the host's images; {@code onPick} gets the chosen id. */
        View avatarPicker(String profileId, String name, String current,
                          Consumer<String> onPick);
        /** Asks to delete the profile being edited. */
        void delete(Draft draft);
        /** Back closed the editor; {@code changed}: the draft differs from the saved profile. */
        void closed(Draft draft, boolean changed);
        /** The host whose child is edited (device-local settings are kept per host). */
        String hostId();
        /** The parent's loaded library for the "Games" counts; null when none is loaded. */
        List<PlayniteLibraryGame> parentLibrary();
        /**
         * A count was chosen: leave the editor for the parent's library filtered to {@code group}
         * of this child (mockup 23g); {@code changed}: the draft is to be saved first.
         */
        void showGames(Draft draft, boolean changed, ChildLibraryFilter.Group group);
    }

    static final class Day {
        final boolean enabled;
        final int startMinute;
        final int endMinute;
        final int dailyLimitSeconds;

        Day(boolean enabled, int startMinute, int endMinute, int dailyLimitSeconds) {
            this.enabled = enabled;
            this.startMinute = startMinute;
            this.endMinute = endMinute;
            this.dailyLimitSeconds = dailyLimitSeconds;
        }

        static Day disabled() {
            return new Day(false, 0, MINUTES_PER_DAY, 0);
        }

        int limitMinutes() {
            return dailyLimitSeconds / 60;
        }

        int windowMinutes() {
            return endMinute - startMinute;
        }

        /** Whole-window windows count as "no limit"; a new day starts there. */
        boolean unlimited() {
            return limitMinutes() >= windowMinutes();
        }

        /**
         * The day with the window moved to {@code start}-{@code end}: a limit that covered the
         * old window covers the new one, any other is cut to fit it.
         */
        Day withWindow(int start, int end) {
            int limit = unlimited() ? end - start : Math.min(limitMinutes(), end - start);
            return new Day(enabled, start, end, Math.max(STEP_MINUTES, limit) * 60);
        }

        Day withEnabled(boolean allowed) {
            // A day without time (the host's disabled default) opens without a limit.
            return new Day(allowed, startMinute, endMinute,
                    dailyLimitSeconds == 0 ? windowMinutes() * 60 : dailyLimitSeconds);
        }

        Day withLimit(int minutes) {
            return new Day(enabled, startMinute, endMinute, minutes * 60);
        }
    }

    static final class Draft {
        final String profileId;
        final String name;
        final String avatar;
        final boolean enabled;
        final List<Day> weekdays;
        /** {@link ChildGameAccess#AGE_LIMITS}; saved with the draft. */
        final String ageLimit;
        /**
         * The host's manual lists, for the "Games" preview only: the draft never sends them
         * (sharing is per game, AGE_RATINGS_DESIGN_PL.md 3.4a).
         */
        final Set<String> allowedGameIds;
        final Set<String> blockedGameIds;
        final int expectedRevision;
        final boolean grantCurrentDevice;
        /** Game mode before the child's games: the parent's choice, enforced by the host. */
        final boolean gameMode;
        /** Its options for the child's streams, applied by the host from the parent's choice. */
        final boolean gameModeWindows;
        final boolean gameModeMute;

        Draft(String profileId, String name, String avatar, boolean enabled,
              List<Day> weekdays, String ageLimit, Set<String> allowedGameIds,
              Set<String> blockedGameIds, int expectedRevision, boolean grantCurrentDevice,
              boolean gameMode, boolean gameModeWindows, boolean gameModeMute) {
            if (weekdays == null || weekdays.size() != DAY_COUNT) {
                throw new IllegalArgumentException("weekdays must contain Monday through Sunday");
            }
            this.profileId = profileId == null ? "" : profileId;
            this.name = name == null ? "" : name;
            this.avatar = avatar == null ? "" : avatar;
            this.enabled = enabled;
            ArrayList<Day> days = new ArrayList<>(DAY_COUNT);
            for (Day day : weekdays) {
                if (day == null) throw new IllegalArgumentException("weekday must not be null");
                days.add(new Day(day.enabled, day.startMinute, day.endMinute,
                        day.dailyLimitSeconds));
            }
            this.weekdays = Collections.unmodifiableList(days);
            this.ageLimit = ChildGameAccess.ageLimit(ageLimit);
            this.allowedGameIds = Collections.unmodifiableSet(new HashSet<>(allowedGameIds));
            this.blockedGameIds = Collections.unmodifiableSet(new HashSet<>(blockedGameIds));
            this.expectedRevision = expectedRevision;
            this.grantCurrentDevice = grantCurrentDevice && this.profileId.isEmpty();
            this.gameMode = gameMode;
            this.gameModeWindows = gameModeWindows;
            this.gameModeMute = gameModeMute;
        }

        static Draft newChild(int expectedRevision) {
            ArrayList<Day> days = new ArrayList<>(DAY_COUNT);
            for (int index = 0; index < DAY_COUNT; index++) days.add(Day.disabled());
            return new Draft("", "", "", true, days, ChildGameAccess.MANUAL,
                    Collections.emptySet(), Collections.emptySet(), expectedRevision, true,
                    false, true, true);
        }

        /** Every saved field, for change detection and the mutation's request ID. */
        String signature() {
            StringBuilder signature = new StringBuilder(profileId).append('|')
                    .append(expectedRevision).append('|').append(name).append('|')
                    .append(avatar).append('|').append(enabled).append('|')
                    .append(grantCurrentDevice).append('|').append(ageLimit).append('|')
                    .append(gameMode).append('|').append(gameModeWindows).append('|')
                    .append(gameModeMute);
            for (Day day : weekdays) {
                signature.append('|').append(day.enabled).append(':').append(day.startMinute)
                        .append(':').append(day.endMinute).append(':')
                        .append(day.dailyLimitSeconds);
            }
            return signature.toString();
        }
    }

    private final Activity activity;
    private final ConsoleSettingsScreen screen;
    private final Host host;
    private final Day[] days = new Day[DAY_COUNT];
    private String profileId;
    private String name;
    private String avatar;
    private boolean enabled;
    private boolean grantCurrentDevice;
    private boolean gameMode;
    private boolean gameModeWindows;
    private boolean gameModeMute;
    private String ageLimit;
    private Set<String> allowedGameIds;
    private Set<String> blockedGameIds;
    private int revision;
    private String savedSignature;
    private ConsoleSettingsScreen.Category scheduleCategory;
    private ConsoleSettingsScreen.Category gamesCategory;

    ConsoleChildProfileEditor(Activity activity, ConsoleSettingsScreen screen, Host host) {
        this.activity = activity;
        this.screen = screen;
        this.host = host;
    }

    /** Opens {@code draft}; {@code unsaved}: it already differs from the saved profile. */
    void open(Draft draft, boolean unsaved) {
        profileId = draft.profileId;
        name = draft.name;
        avatar = draft.avatar;
        enabled = draft.enabled;
        grantCurrentDevice = draft.grantCurrentDevice;
        gameMode = draft.gameMode;
        gameModeWindows = draft.gameModeWindows;
        gameModeMute = draft.gameModeMute;
        ageLimit = draft.ageLimit;
        allowedGameIds = draft.allowedGameIds;
        blockedGameIds = draft.blockedGameIds;
        revision = draft.expectedRevision;
        for (int index = 0; index < DAY_COUNT; index++) days[index] = draft.weekdays.get(index);
        savedSignature = unsaved ? "" : draft().signature();
        gamesCategory = new ConsoleSettingsScreen.Category(s(R.string.child_games_category),
                s(R.string.child_games_details), this::gamesRows);
        gamesCategory.value = ChildGameAccess.stopText(activity, ageLimit);
        scheduleCategory = new ConsoleSettingsScreen.Category(
                s(R.string.console_child_profile_step_schedule),
                s(R.string.console_child_profile_time_warning), this::weekRows);
        scheduleCategory.value = allowedDays();
        screen.push(s(R.string.console_child_management_title), title(),
                s(R.string.console_child_profile_title), Arrays.asList(
                        new ConsoleSettingsScreen.Category(
                                s(R.string.console_child_profile_category),
                                s(R.string.console_child_profile_shared_info), this::profileRows),
                        scheduleCategory, gamesCategory),
                0, () -> {
                    Draft current = draft();
                    host.closed(current, !current.signature().equals(savedSignature));
                });
        if (name.isEmpty()) editName();
    }

    Draft draft() {
        return new Draft(profileId, name, avatar, enabled, Arrays.asList(days), ageLimit,
                allowedGameIds, blockedGameIds, revision, grantCurrentDevice, gameMode,
                gameModeWindows, gameModeMute);
    }

    private String displayName() {
        return name.isEmpty() ? s(R.string.console_child_profile_title) : name;
    }

    /** "Play time"'s value (mockup 23a: "5 dni"): the days the child may play. */
    private String allowedDays() {
        int allowed = 0;
        for (Day day : days) if (day.enabled) allowed++;
        return activity.getResources().getQuantityString(R.plurals.child_schedule_days,
                allowed, allowed);
    }

    /**
     * Mockups 23a/23b: the age category as six named stops on a slider (◄ ► one stop while
     * focused), a note under it ("No limit" warns), then what the child would see of the loaded
     * parent library. The
     * category moves the counts in place; without a loaded library the counts are left out. A
     * saved child's counts open the library filtered to them.
     */
    private View[] gamesRows(ConsolePanel panel) {
        List<View> rows = new ArrayList<>();
        List<PlayniteLibraryGame> library = host.parentLibrary();
        TextView note = panel.note("");
        LinearLayout[] counts = new LinearLayout[4];
        Runnable update = () -> {
            boolean all = ChildGameAccess.ALL.equals(ageLimit);
            note.setText(all ? activity.getString(R.string.child_games_all_warning, displayName())
                    : s(R.string.child_games_note));
            note.setTextColor(all ? ConsoleTileBadge.AMBER
                    : activity.getResources().getColor(R.color.console_text_secondary));
            if (library == null) return;
            ChildGameAccess.Preview preview = ChildGameAccess.preview(ageLimit, allowedGameIds,
                    blockedGameIds, library);
            ConsolePanel.setValue(counts[0], activity.getResources().getQuantityString(
                    R.plurals.child_games_count, preview.visible, preview.visible));
            ConsolePanel.setValue(counts[1], String.valueOf(preview.granted));
            ConsolePanel.setValue(counts[2], String.valueOf(preview.blocked));
            ConsolePanel.setValue(counts[3], activity.getString(
                    R.string.child_games_unrated_value, preview.unrated));
            counts[3].setVisibility(preview.unrated > 0 ? View.VISIBLE : View.GONE);
        };
        String[] stops = new String[ChildGameAccess.AGE_LIMITS.size()];
        for (int index = 0; index < stops.length; index++) {
            stops[index] = ChildGameAccess.stopText(activity,
                    ChildGameAccess.AGE_LIMITS.get(index));
        }
        rows.add(panel.stops(s(R.string.child_games_age_limit),
                ChildGameAccess.AGE_LIMITS.indexOf(ageLimit), stops,
                index -> ChildGameAccess.limitText(activity,
                        ChildGameAccess.AGE_LIMITS.get(index)),
                s(R.string.child_games_age_step), index -> {
                    ageLimit = ChildGameAccess.AGE_LIMITS.get(index);
                    screen.setCategoryValue(gamesCategory,
                            ChildGameAccess.stopText(activity, ageLimit));
                    update.run();
                }));
        rows.add(note);
        if (library != null) {
            // Mockup 23a names the child ("Biblioteka Oli"); a colon keeps any name undeclined.
            rows.add(panel.section(activity.getString(R.string.child_games_library,
                    displayName())));
            counts[0] = count(panel, activity.getString(R.string.child_games_visible,
                    displayName()), ConsolePanel.TEXT, ChildLibraryFilter.Group.VISIBLE);
            counts[1] = count(panel, s(R.string.child_games_granted), ConsolePanel.TEXT,
                    ChildLibraryFilter.Group.GRANTED);
            counts[2] = count(panel, s(R.string.child_games_blocked), ConsolePanel.TEXT,
                    ChildLibraryFilter.Group.BLOCKED);
            counts[3] = count(panel, s(R.string.child_games_unrated), ConsoleTileBadge.AMBER,
                    ChildLibraryFilter.Group.UNRATED);
            Collections.addAll(rows, counts);
        }
        update.run();
        return rows.toArray(new View[0]);
    }

    /** A count row; a saved child's opens its games, a new child's is only a fact. */
    private LinearLayout count(ConsolePanel panel, String label, int color,
                               ChildLibraryFilter.Group group) {
        if (profileId.isEmpty()) return panel.fact(label, "", color);
        LinearLayout row = panel.value(label, "");
        ((TextView) row.getChildAt(1)).setTextColor(ConsolePanel.rowColors(color));
        row.setOnClickListener(view -> {
            Draft current = draft();
            host.showGames(current, !current.signature().equals(savedSignature), group);
        });
        return row;
    }

    private String title() {
        return name.isEmpty() ? s(R.string.console_child_management_add) : name;
    }

    private View[] profileRows(ConsolePanel panel) {
        List<View> rows = new ArrayList<>();
        LinearLayout nameRow = panel.value(s(R.string.console_child_profile_name),
                name.isEmpty() ? s(R.string.console_child_profile_name_missing) : name);
        nameRow.setOnClickListener(view -> editName());
        rows.add(nameRow);

        rows.add(panel.section(s(R.string.console_child_profile_avatar)));
        rows.add(host.avatarPicker(profileId, name, avatar, chosen -> avatar = chosen));

        LinearLayout enabledRow = panel.toggle(s(R.string.console_child_profile_enabled), enabled);
        enabledRow.setOnClickListener(view -> {
            enabled = !enabled;
            panel.setChecked(enabledRow, enabled);
        });
        rows.add(enabledRow);
        // Host Plus (mockup 24g). The host enforces all three: without the first, the child's
        // game mode is refused; the other two are the host's values for the child's streams.
        rows.add(panel.section(s(R.string.console_game_mode_label)));
        LinearLayout gameModeRow = panel.toggle(s(R.string.console_game_mode_label), gameMode);
        gameModeRow.setOnClickListener(view -> {
            gameMode = !gameMode;
            panel.setChecked(gameModeRow, gameMode);
        });
        panel.described(gameModeRow, s(R.string.console_game_mode_child_description));
        rows.add(gameModeRow);
        LinearLayout windowsRow = panel.toggle(s(R.string.console_game_mode_windows_label),
                gameModeWindows);
        windowsRow.setOnClickListener(view -> {
            gameModeWindows = !gameModeWindows;
            panel.setChecked(windowsRow, gameModeWindows);
        });
        panel.described(windowsRow, s(R.string.console_game_mode_windows_description));
        rows.add(windowsRow);
        LinearLayout muteRow = panel.toggle(s(R.string.console_game_mode_mute_label),
                gameModeMute);
        muteRow.setOnClickListener(view -> {
            gameModeMute = !gameModeMute;
            panel.setChecked(muteRow, gameModeMute);
        });
        panel.described(muteRow, s(R.string.console_game_mode_mute_description));
        rows.add(muteRow);
        if (profileId.isEmpty()) {
            LinearLayout device = panel.toggle(s(R.string.console_child_profile_grant_device),
                    grantCurrentDevice);
            device.setOnClickListener(view -> {
                grantCurrentDevice = !grantCurrentDevice;
                panel.setChecked(device, grantCurrentDevice);
            });
            rows.add(device);
        } else {
            // Device-local, applied at once: the parent decides the child's TV home tile.
            SharedPreferences consent = activity.getSharedPreferences(
                    UsbMicrophoneService.CONSOLE_PREFS, Activity.MODE_PRIVATE);
            LinearLayout tile = panel.toggle(s(R.string.watch_next_tile_label),
                    WatchNextTile.consented(consent, host.hostId(), profileId));
            tile.setOnClickListener(view -> {
                boolean next = !WatchNextTile.consented(consent, host.hostId(), profileId);
                WatchNextTile.setConsent(consent, host.hostId(), profileId, next);
                panel.setChecked(tile, next);
            });
            panel.described(tile, activity.getString(R.string.watch_next_tile_description,
                    title()));
            rows.add(tile);
            TextView delete = panel.destructive(panel.action(activity.getString(
                    R.string.console_child_management_delete_named, title())));
            delete.setOnClickListener(view -> host.delete(draft()));
            rows.add(delete);
        }
        return rows.toArray(new View[0]);
    }

    private View[] weekRows(ConsolePanel panel) {
        View[] rows = new View[DAY_COUNT];
        for (int index = 0; index < DAY_COUNT; index++) {
            int day = index;
            LinearLayout row = panel.value(s(DAY_LABELS[index]), summary(days[index]));
            row.setOnClickListener(view -> showDay(day, 0));
            rows[index] = row;
        }
        return rows;
    }

    /**
     * One day's page. A step moves the other rows' ranges in place (a rebuild flickers the whole
     * card); only switching the day on or off rebuilds it, since rows appear or go.
     */
    private void showDay(int index, int focus) {
        ConsolePanel panel = screen.panel();
        Day day = days[index];
        List<View> rows = new ArrayList<>();
        LinearLayout allowed = panel.toggle(s(R.string.console_child_day_enabled), day.enabled);
        allowed.setOnClickListener(view -> {
            days[index] = days[index].withEnabled(!days[index].enabled);
            screen.setCategoryValue(scheduleCategory, allowedDays());
            showDay(index, 0);
        });
        rows.add(allowed);
        if (day.enabled) {
            String step = activity.getString(R.string.console_child_step, STEP_MINUTES);
            LinearLayout[] steppers = new LinearLayout[3];
            Runnable bound = () -> {
                Day current = days[index];
                int window = current.windowMinutes();
                ConsolePanel.setStepper(steppers[0], 0, current.endMinute - STEP_MINUTES,
                        current.startMinute);
                ConsolePanel.setStepper(steppers[1], current.startMinute + STEP_MINUTES,
                        MINUTES_PER_DAY, current.endMinute);
                ConsolePanel.setStepper(steppers[2], Math.min(STEP_MINUTES, window), window,
                        Math.min(current.limitMinutes(), window));
            };
            steppers[0] = panel.stepper(s(R.string.console_child_day_start), day.startMinute,
                    0, day.endMinute - STEP_MINUTES, STEP_MINUTES,
                    ConsoleChildProfileEditor::clock, step, new int[0], start -> {
                        days[index] = days[index].withWindow(start, days[index].endMinute);
                        bound.run();
                    });
            steppers[1] = panel.stepper(s(R.string.console_child_day_end), day.endMinute,
                    day.startMinute + STEP_MINUTES, MINUTES_PER_DAY, STEP_MINUTES,
                    ConsoleChildProfileEditor::clock, step, new int[0], end -> {
                        days[index] = days[index].withWindow(days[index].startMinute, end);
                        bound.run();
                    });
            int window = day.windowMinutes();
            steppers[2] = panel.stepper(s(R.string.console_child_day_limit),
                    Math.min(day.limitMinutes(), window), Math.min(STEP_MINUTES, window), window,
                    STEP_MINUTES, minutes -> minutes >= days[index].windowMinutes()
                            ? s(R.string.console_child_day_unlimited) : duration(minutes),
                    step, new int[0], limit -> days[index] = days[index].withLimit(limit));
            Collections.addAll(rows, steppers);
        }
        rows.add(panel.section(s(R.string.console_child_day_copy)));
        TextView workdays = panel.action(s(R.string.console_child_day_copy_workdays));
        workdays.setOnClickListener(view -> copy(index, WORKDAYS));
        rows.add(workdays);
        TextView week = panel.action(s(R.string.console_child_day_copy_week));
        week.setOnClickListener(view -> copy(index, DAY_COUNT));
        rows.add(week);
        screen.showPage(s(DAY_LABELS[index]), s(R.string.console_child_day_details),
                rows.toArray(new View[0]),
                focus, s(R.string.console_child_profile_step_schedule));
    }

    private void copy(int from, int through) {
        for (int index = 0; index < through; index++) days[index] = days[from];
        screen.setCategoryValue(scheduleCategory, allowedDays());
        ConsoleUiFeedback.makeText(activity, R.string.console_child_day_copied,
                android.widget.Toast.LENGTH_SHORT).show();
    }

    private void editName() {
        host.editName(name, value -> {
            name = value;
            screen.setTitle(title());
            screen.refresh();
        });
    }

    private String summary(Day day) {
        if (!day.enabled) return s(R.string.console_child_day_summary_disabled);
        return activity.getString(R.string.console_child_day_summary_enabled,
                clock(day.startMinute), clock(day.endMinute), day.unlimited()
                        ? s(R.string.console_child_day_unlimited) : duration(day.limitMinutes()));
    }

    /** Today's plan for the list of children, e.g. "Today 16:00-19:00 · 1 h". */
    static String todaySummary(android.content.Context context, Draft draft, int today) {
        if (!draft.enabled) return context.getString(R.string.console_child_profile_disabled);
        Day day = draft.weekdays.get(today);
        if (!day.enabled) return context.getString(R.string.console_child_day_today_disabled);
        return context.getString(R.string.console_child_day_today, clock(day.startMinute),
                clock(day.endMinute), day.unlimited()
                        ? context.getString(R.string.console_child_day_unlimited)
                        : duration(context, day.limitMinutes()));
    }

    static String clock(int minutes) {
        int value = Math.max(0, Math.min(MINUTES_PER_DAY, minutes));
        return String.format(Locale.ROOT, "%02d:%02d", value / 60, value % 60);
    }

    private String duration(int minutes) {
        return duration(activity, minutes);
    }

    private static String duration(android.content.Context context, int minutes) {
        return PlaynitePlaytimeFormatter.minutes(minutes, new PlaynitePlaytimeFormatter.Labels("",
                context.getString(R.string.playnite_hours_short),
                context.getString(R.string.playnite_minutes_short)));
    }

    static boolean validSchedule(int start, int end, int limitMinutes) {
        return start >= 0 && start < end && end <= MINUTES_PER_DAY
                && limitMinutes >= 0 && limitMinutes <= end - start;
    }

    private String s(int resource) {
        return activity.getString(resource);
    }
}
