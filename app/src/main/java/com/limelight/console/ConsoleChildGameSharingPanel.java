package com.limelight.console;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which of the parent's children may play one game (mockups 23e/23f): the game's rating on top,
 * then a {@link ConsolePanel} switch per child with the line of what that switch means for the
 * child's age category, saved 5 s after the last change or when the panel closes.
 *
 * The caller supplies the already-authorized child set. This panel never loads
 * profiles or writes a registry; it returns the complete selected ID list.
 */
final class ConsoleChildGameSharingPanel extends LinearLayout {
    interface SaveCallback {
        void save(Draft draft, Completion completion);
    }

    interface Completion {
        void onSaved(int revision);
        void onFailed(CharSequence message, boolean conflict);
    }

    static final class ChildOption {
        final String id;
        final String name;
        final String ageLimit;
        /** The Gateway's state for this child (sharing/get). */
        final ChildGameAccess.State state;

        ChildOption(String id, String name, String ageLimit, ChildGameAccess.State state) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.ageLimit = ChildGameAccess.ageLimit(ageLimit);
            this.state = state;
        }
    }

    static final class Draft {
        final List<String> selectedChildIds;
        final int expectedRevision;
        /** sharing/get's {@code min_age} unchanged, null when unrated: a stale one is a 409. */
        final Integer expectedMinAge;

        Draft(List<String> selectedChildIds, int expectedRevision, Integer expectedMinAge) {
            ArrayList<String> ids = new ArrayList<>();
            if (selectedChildIds != null) {
                for (String id : selectedChildIds) {
                    if (id != null && id.length() > 0 && !ids.contains(id)) ids.add(id);
                }
            }
            this.selectedChildIds = Collections.unmodifiableList(ids);
            this.expectedRevision = expectedRevision;
            this.expectedMinAge = expectedMinAge;
        }
    }

    private static final class OptionRow {
        final ChildOption option;
        final String name;
        final LinearLayout row;
        /** The Gateway's state: sharing/get's, then what each confirmed save made of it. */
        ChildGameAccess.State hostState;
        boolean granted;
        boolean originalGranted;

        OptionRow(ChildOption option, String name, LinearLayout row) {
            this.option = option;
            this.name = name;
            this.row = row;
            this.hostState = option.state;
            this.granted = option.state.access();
            this.originalGranted = granted;
        }
    }

    private final List<OptionRow> optionRows = new ArrayList<>();
    private static final long AUTOSAVE_DELAY_MS = 5000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autosave = this::autosaveDue;
    private final ConsolePanel panel;
    /** sharing/get's {@code min_age}, the one the Gateway compares; null when unrated. */
    private Integer minAge;
    private TextView statusView;
    /** Shown after a failed save: retries it, or refreshes after a conflict. */
    private TextView retryRow;
    private SaveCallback saveCallback;
    private Draft inFlightDraft;
    private int expectedRevision;
    private long lastChangeUptime;
    private String requestSignature = "";
    private String requestId = "";
    private boolean saveInFlight;
    private boolean dismissRequested;
    private Runnable refreshCallback;

    ConsoleChildGameSharingPanel(Context context, ConsolePanel panel) {
        super(context);
        this.panel = panel;
        setVisibility(GONE);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    /** {@code minAge}: sharing/get's {@code min_age}; {@code rating} is only shown. */
    void show(GameAgeRating rating, Integer minAge, List<ChildOption> ownChildren, int revision,
              SaveCallback save) {
        handler.removeCallbacks(autosave);
        expectedRevision = revision;
        saveCallback = save;
        inFlightDraft = null;
        requestSignature = "";
        requestId = "";
        lastChangeUptime = 0L;
        saveInFlight = false;
        dismissRequested = false;
        refreshCallback = null;
        optionRows.clear();
        removeAllViews();
        this.minAge = minAge;
        TextView heading = panel.note(GameAgeRating.heading(getContext(), rating));
        heading.setTextColor(ConsolePanel.TEXT);
        heading.setTextSize(ConsolePanel.LABEL_SP);
        addView(heading);
        if (rating != null && !rating.descriptors.isEmpty()) {
            TextView descriptors = panel.note(TextUtils.join(", ", rating.descriptors));
            descriptors.setPadding(descriptors.getPaddingLeft(), 0,
                    descriptors.getPaddingRight(), descriptors.getPaddingBottom());
            addView(descriptors);
        }
        addView(panel.separator());

        Map<String, ChildOption> unique = new LinkedHashMap<>();
        if (ownChildren != null) {
            for (ChildOption option : ownChildren) {
                if (option == null || option.id.length() == 0 || unique.containsKey(option.id)) continue;
                unique.put(option.id, option);
            }
        }
        if (unique.isEmpty()) {
            addView(panel.note(getContext().getString(R.string.console_child_sharing_empty)));
        } else {
            for (ChildOption option : unique.values()) addOption(option);
            addView(panel.note(getContext().getString(R.string.console_child_sharing_details)));
        }
        statusView = panel.note("");
        statusView.setVisibility(GONE);
        addView(statusView);
        retryRow = panel.action("");
        retryRow.setVisibility(GONE);
        addView(retryRow);

        setVisibility(VISIBLE);
        wireFocusOrder();
        if (!optionRows.isEmpty()) {
            View first = optionRows.get(0).row;
            first.post(first::requestFocus);
        }
    }

    Draft currentDraft() {
        return readDraft();
    }

    /** Queue a dirty snapshot before the activity invalidates this panel context. */
    void prepareForDismiss() {
        dismissRequested = true;
        handler.removeCallbacks(autosave);
        if (!saveInFlight && hasChanges()) startSave();
    }

    boolean isDismissRequested() {
        return dismissRequested;
    }

    /** Resume the panel after its existing view hierarchy was restored from panel history. */
    void resumeAfterRestore() {
        dismissRequested = false;
    }

    void setRefreshCallback(Runnable refresh) {
        refreshCallback = refresh;
    }

    String requestIdFor(String signature) {
        String value = signature == null ? "" : signature;
        if (!value.equals(requestSignature) || requestId.length() == 0) {
            requestSignature = value;
            requestId = UUID.randomUUID().toString();
        }
        return requestId;
    }

    /** Drop callbacks for a host/profile that is no longer selected. */
    void discardPendingSave() {
        handler.removeCallbacks(autosave);
        inFlightDraft = null;
        saveInFlight = false;
    }

    private void addOption(ChildOption option) {
        String name = option.name.trim().isEmpty()
                ? getContext().getString(R.string.console_child_profile_title) : option.name.trim();
        LinearLayout row = panel.toggle(name, option.state.access());
        OptionRow entry = new OptionRow(option, name, row);
        showState(entry);
        row.setOnClickListener(view -> {
            entry.granted = !entry.granted;
            showState(entry);
            lastChangeUptime = SystemClock.uptimeMillis();
            scheduleAutosave();
        });
        addView(row);
        optionRows.add(entry);
    }

    /** The switch and its state line for the access it now shows (mockup 23f). */
    private void showState(OptionRow entry) {
        String limit = entry.option.ageLimit;
        ChildGameAccess.State state = ChildGameAccess.toggled(entry.hostState, entry.granted,
                limit, minAge);
        boolean warns = ChildGameAccess.warns(state, limit, minAge);
        String line = ChildGameAccess.stateLine(getContext(), state, limit, minAge);
        panel.described(entry.row, entry.name, line, warns ? ConsoleTileBadge.AMBER
                : getContext().getResources().getColor(R.color.console_text_secondary));
        ConsolePanel.setToggleColor(entry.row, warns ? ConsoleTileBadge.AMBER
                : getContext().getResources().getColor(R.color.console_accent));
        panel.setChecked(entry.row, entry.granted);
    }

    private void scheduleAutosave() {
        handler.removeCallbacks(autosave);
        if (!hasChanges()) {
            setStatus(0, false);
            return;
        }
        setStatus(R.string.console_child_sharing_pending, true);
        if (dismissRequested) {
            if (!saveInFlight) startSave();
            return;
        }
        long elapsed = lastChangeUptime == 0L
                ? AUTOSAVE_DELAY_MS
                : SystemClock.uptimeMillis() - lastChangeUptime;
        handler.postDelayed(autosave, Math.max(0L, AUTOSAVE_DELAY_MS - elapsed));
    }

    private void autosaveDue() {
        if (!hasChanges()) return;
        if (saveInFlight) {
            return;
        }
        startSave();
    }

    private void startSave() {
        if (saveInFlight || !hasChanges()) return;
        if (saveCallback == null) {
            finishFailed(null, false);
            return;
        }
        saveInFlight = true;
        retryRow.setVisibility(GONE);
        inFlightDraft = readDraft();
        setStatus(R.string.console_child_sharing_saving, true);
        SaveCallback callback = saveCallback;
        try {
            callback.save(inFlightDraft, new Completion() {
                @Override
                public void onSaved(int revision) {
                    finishSaved(revision);
                }

                @Override
                public void onFailed(CharSequence message, boolean conflict) {
                    finishFailed(message, conflict);
                }
            });
        } catch (RuntimeException error) {
            finishFailed(null, false);
        }
    }

    private Draft readDraft() {
        ArrayList<String> selected = new ArrayList<>();
        for (OptionRow row : optionRows) {
            if (row.granted) selected.add(row.option.id);
        }
        return new Draft(selected, expectedRevision, minAge);
    }

    private void finishSaved(int revision) {
        runOnUiThread(() -> {
            if (!saveInFlight) return;
            Draft completed = inFlightDraft;
            inFlightDraft = null;
            saveInFlight = false;
            expectedRevision = Math.max(0, revision);
            if (completed != null) {
                for (OptionRow option : optionRows) {
                    boolean saved = completed.selectedChildIds.contains(option.option.id);
                    // What the Gateway made of the saved access (3.4), so reverting it reads
                    // as the host will: unhiding is "from category", not a manual grant.
                    option.hostState = ChildGameAccess.toggled(option.hostState, saved,
                            option.option.ageLimit, minAge);
                    option.originalGranted = saved;
                    showState(option);
                }
            }
            if (hasChanges()) {
                if (dismissRequested) startSave();
                else scheduleAutosave();
            } else {
                setStatus(R.string.console_child_sharing_saved, true);
            }
        });
    }

    private void finishFailed(CharSequence message, boolean conflict) {
        runOnUiThread(() -> {
            if (!saveInFlight) return;
            inFlightDraft = null;
            saveInFlight = false;
            CharSequence shown = message;
            if (shown == null || shown.length() == 0) {
                shown = getContext().getString(conflict
                        ? R.string.console_child_sharing_save_conflict
                        : R.string.console_child_sharing_save_failed);
            }
            setStatus(shown, false);
            boolean retry = !dismissRequested && (!conflict || refreshCallback != null);
            retryRow.setText(conflict ? R.string.console_child_management_refresh
                    : R.string.console_child_profile_save_retry);
            retryRow.setVisibility(retry ? VISIBLE : GONE);
            retryRow.setOnClickListener(view -> {
                retryRow.setVisibility(GONE);
                wireFocusOrder();
                if (conflict) {
                    if (refreshCallback != null) refreshCallback.run();
                } else if (hasChanges()) startSave();
            });
            wireFocusOrder();
            if (retry) retryRow.requestFocus();
            if (dismissRequested && !isShown()) {
                ConsoleUiFeedback.makeText(getContext(), shown, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void runOnUiThread(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else post(action);
    }

    private void setStatus(int textResource, boolean visible) {
        setStatus(textResource == 0 ? "" : getContext().getString(textResource), visible);
    }

    private void setStatus(CharSequence text, boolean success) {
        if (statusView == null) return;
        statusView.setText(text);
        statusView.setTextColor(success ? 0xFFB7D7C0 : 0xFFFFB4A8);
        statusView.setVisibility(text == null || text.length() == 0 ? GONE : VISIBLE);
    }

    private boolean hasChanges() {
        for (OptionRow option : optionRows) {
            if (option.granted != option.originalGranted) return true;
        }
        return false;
    }

    private void wireFocusOrder() {
        ArrayList<View> order = new ArrayList<>();
        for (OptionRow option : optionRows) order.add(option.row);
        if (retryRow.getVisibility() == VISIBLE) order.add(retryRow);
        for (int index = 0; index < order.size(); index++) {
            View current = order.get(index);
            current.setNextFocusDownId(order.get(Math.min(index + 1, order.size() - 1)).getId());
            current.setNextFocusUpId(order.get(Math.max(0, index - 1)).getId());
        }
    }
}
