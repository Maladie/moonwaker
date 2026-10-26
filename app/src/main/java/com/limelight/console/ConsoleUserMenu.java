package com.limelight.console;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.gateway.GatewayConnection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * The user menu under the top-bar avatar (#47, .claude/handoffs/USER_MENU_DESIGN_PL.md): who
 * is playing, the state of the profile's connected accounts on the host, and what can be done
 * now. It owns the accounts read and its rendering; nothing here is a setting (those live in
 * Settings › Profile and host). The session actions come from their owners as plain actions.
 */
final class ConsoleUserMenu {
    /** The console side panel: shows a page, and tells whether a page is the one on screen. */
    interface Presenter {
        void show(String eyebrow, String title, String details, View... rows);

        boolean showing(String eyebrow, String title);
    }

    static final class Action {
        final String label;
        final boolean enabled;
        final Runnable run;

        Action(String label, boolean enabled, Runnable run) {
            this.label = label;
            this.enabled = enabled;
            this.run = run;
        }
    }

    /** Who the menu is for, as the caller knows it when the avatar is pressed. */
    static final class Subject {
        final String hostUuid;
        final String hostName;
        final String profileName;
        final boolean child;
        final String stateLine;
        final boolean remoteSignInGranted;
        final String remoteSignInState;
        /** The adult profile's Gateway connection; null leaves the accounts unknown. */
        final GatewayConnection connection;

        Subject(String hostUuid, String hostName, String profileName, boolean child,
                String stateLine, boolean remoteSignInGranted, String remoteSignInState,
                GatewayConnection connection) {
            this.hostUuid = hostUuid;
            this.hostName = hostName;
            this.profileName = profileName;
            this.child = child;
            this.stateLine = stateLine;
            this.remoteSignInGranted = remoteSignInGranted;
            this.remoteSignInState = remoteSignInState;
            this.connection = connection;
        }
    }

    private static final int QR_DP = 120;
    /** Legendary's Epic sign-in page; after login it shows the JSON with the code. */
    static final String EPIC_LOGIN_URL = "https://legendary.gl/epiclogin";

    private final Activity activity;
    private final ConsolePanel panel;
    private final HostGatewayClient client;
    private final Executor executor;
    private final Handler main;
    private final Presenter presenter;
    private final Runnable openDiscordSettings;
    private final Runnable openProfileSettings;
    private Subject subject;
    private List<Action> actions = new ArrayList<>();
    private HostGatewayClient.SetupFacts setup;
    private boolean loading;
    /** A key or code on its way to the host; the card waits for it. */
    private boolean saving;
    private String inputError = "";
    /** The card's field, so a re-render (a fresh read arriving) keeps what was pasted. */
    private EditText input;
    private UserMenuAccounts.Kind inputKind;
    private int generation;

    ConsoleUserMenu(Activity activity, ConsolePanel panel, HostGatewayClient client,
                    Executor executor, Handler main, Presenter presenter,
                    Runnable openDiscordSettings, Runnable openProfileSettings) {
        this.activity = activity;
        this.panel = panel;
        this.client = client;
        this.executor = executor;
        this.main = main;
        this.presenter = presenter;
        this.openDiscordSettings = openDiscordSettings;
        this.openProfileSettings = openProfileSettings;
    }

    /** Opens the menu and reads the host's account facts once. */
    void show(Subject subject, List<Action> actions) {
        this.subject = subject;
        this.actions = actions;
        setup = null;
        saving = false;
        inputError = "";
        input = null;
        renderMenu();
        load();
    }

    private void load() {
        Subject forSubject = subject;
        int token = ++generation;
        if (forSubject.child || forSubject.connection == null) {
            loading = false;
            return;
        }
        loading = true;
        executor.execute(() -> {
            HostGatewayClient.Capabilities read;
            try {
                read = client.getCapabilities(forSubject.connection);
            } catch (IOException | RuntimeException unavailable) {
                read = null;
            }
            HostGatewayClient.SetupFacts facts = read == null ? null : read.setup;
            main.post(() -> {
                // A reply for another opening, host or profile is stale.
                if (token != generation || forSubject != subject || activity.isDestroyed()) return;
                loading = false;
                setup = facts;
                refreshShown();
            });
        });
    }

    private void refreshShown() {
        if (presenter.showing(eyebrow(), subject.profileName)) {
            renderMenu();
            return;
        }
        for (UserMenuAccounts.Row row : rows()) {
            if (presenter.showing(s(R.string.user_menu_accounts), accountName(row.kind))) {
                renderFix(row.kind);
                return;
            }
        }
    }

    private String eyebrow() {
        return s(subject.child ? R.string.user_menu_child : R.string.user_menu_profile);
    }

    private List<UserMenuAccounts.Row> rows() {
        return UserMenuAccounts.rows(setup, subject.remoteSignInGranted,
                subject.remoteSignInState);
    }

    private void renderMenu() {
        List<View> views = new ArrayList<>();
        if (!subject.child) {
            views.add(panel.section(s(R.string.user_menu_accounts_for, subject.hostName)));
            for (UserMenuAccounts.Row row : rows()) {
                LinearLayout view = panel.value(accountName(row.kind), accountValue(row));
                view.setTag("user_menu.account:" + row.kind);
                view.setOnClickListener(v -> {
                    if (row.kind == UserMenuAccounts.Kind.DISCORD) openDiscordSettings.run();
                    else renderFix(row.kind);
                });
                views.add(view);
            }
        }
        views.add(panel.section(s(R.string.user_menu_session)));
        for (Action action : actions) {
            TextView view = panel.action(action.label, action.enabled);
            view.setOnClickListener(v -> action.run.run());
            views.add(view);
        }
        TextView settings = panel.navigation(s(R.string.console_settings_profile_settings));
        settings.setOnClickListener(v -> openProfileSettings.run());
        views.add(settings);
        presenter.show(eyebrow(), subject.profileName, subject.stateLine,
                views.toArray(new View[0]));
    }

    /**
     * The fix card. Steam and a signed-out Epic take the value right here: a QR opens the
     * page on a phone, and the copied key or code is pasted into the field (e.g. with the
     * Google TV phone app). The Windows sign-in keeps the wizard's Host Control fix.
     */
    private void renderFix(UserMenuAccounts.Kind kind) {
        UserMenuAccounts.Row row = null;
        for (UserMenuAccounts.Row candidate : rows()) if (candidate.kind == kind) row = candidate;
        if (row == null) return;
        String draft = input != null && inputKind == kind ? input.getText().toString() : "";
        input = null;
        List<View> views = new ArrayList<>();
        boolean steam = kind == UserMenuAccounts.Kind.STEAM;
        boolean epicSignedIn = kind == UserMenuAccounts.Kind.EPIC && !row.name.isEmpty();
        if (kind == UserMenuAccounts.Kind.WINDOWS_SIGN_IN || epicSignedIn) {
            views.add(panel.note(epicSignedIn ? s(R.string.first_run_epic_signed_in, row.name)
                    : !subject.remoteSignInGranted ? s(R.string.user_menu_windows_grant_fix)
                    : activity.getResources().getStringArray(R.array.first_run_check_fixes)
                    [check(kind).ordinal()]));
        } else {
            views.add(panel.note(s(steam ? R.string.user_menu_steam_steps
                    : R.string.user_menu_epic_steps)));
            views.add(qr(steam ? FirstRunWizard.STEAM_KEY_URL : EPIC_LOGIN_URL));
            EditText field = panel.textField(s(steam ? R.string.user_menu_paste_key
                    : R.string.user_menu_paste_code));
            field.setText(draft);
            field.setEnabled(!saving);
            if (!inputError.isEmpty()) field.setError(inputError);
            input = field;
            inputKind = kind;
            views.add(field);
            TextView save = panel.action(s(saving ? R.string.user_menu_saving
                    : steam ? R.string.user_menu_save_key : R.string.user_menu_sign_in), !saving);
            save.setOnClickListener(v -> submit(kind, field));
            views.add(save);
        }
        TextView recheck = panel.action(s(R.string.user_menu_recheck), !loading && !saving);
        recheck.setOnClickListener(v -> {
            load();
            renderFix(kind);
        });
        views.add(recheck);
        presenter.show(s(R.string.user_menu_accounts), accountName(kind), accountState(row),
                views.toArray(new View[0]));
        if (input != null && !inputError.isEmpty()) input.requestFocus();
    }

    private void submit(UserMenuAccounts.Kind kind, EditText field) {
        boolean steam = kind == UserMenuAccounts.Kind.STEAM;
        String value = UserMenuAccounts.pastedSecret(field.getText().toString());
        if (value == null) {
            field.setError(s(steam ? R.string.user_menu_steam_invalid
                    : R.string.user_menu_epic_invalid));
            return;
        }
        Subject forSubject = subject;
        if (forSubject.connection == null) {
            field.setError(s(R.string.user_menu_save_failed));
            return;
        }
        saving = true;
        inputError = "";
        renderFix(kind);
        String requestId = UUID.randomUUID().toString();
        executor.execute(() -> {
            String reason;
            try {
                client.setupAccount(forSubject.connection, steam, value, requestId);
                reason = null;
            } catch (IOException | RuntimeException failed) {
                reason = String.valueOf(failed.getMessage());
            }
            String failure = reason;
            main.post(() -> {
                if (forSubject != subject || activity.isDestroyed()) return;
                saving = false;
                if (failure == null) {
                    input = null;
                    load();
                } else {
                    inputError = failureText(failure);
                }
                if (presenter.showing(s(R.string.user_menu_accounts), accountName(kind))) {
                    renderFix(kind);
                }
            });
        });
    }

    private String failureText(String reason) {
        switch (reason) {
            case "steam_api_key_invalid": return s(R.string.user_menu_steam_invalid);
            case "epic_code_invalid": return s(R.string.user_menu_epic_invalid);
            case "epic_sign_in_failed": return s(R.string.user_menu_epic_failed);
            case "legendary_unavailable":
            case "legendary_disabled": return s(R.string.user_menu_legendary_missing);
            default: return s(R.string.user_menu_save_failed);
        }
    }

    private View qr(String url) {
        LinearLayout line = new LinearLayout(activity);
        line.setGravity(Gravity.CENTER_VERTICAL);
        int inset = dp(ConsolePanel.ROW_INSET_DP);
        line.setPadding(inset, dp(8), inset, dp(12));
        ImageView code = new ImageView(activity);
        code.setImageBitmap(FirstRunWizardPage.qrBitmap(url, dp(QR_DP)));
        code.setContentDescription(url);
        GradientDrawable plate = new GradientDrawable();
        plate.setColor(Color.WHITE);
        plate.setCornerRadius(dp(8));
        code.setBackground(plate);
        int pad = dp(8);
        code.setPadding(pad, pad, pad, pad);
        line.addView(code, new LinearLayout.LayoutParams(dp(QR_DP), dp(QR_DP)));
        line.addView(panel.note(url.replaceFirst("^https://", "")), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        line.setFocusable(false);
        return line;
    }

    private static FirstRunPlan.Check check(UserMenuAccounts.Kind kind) {
        switch (kind) {
            case STEAM: return FirstRunPlan.Check.STEAM_LIBRARY;
            case EPIC: return FirstRunPlan.Check.EPIC;
            default: return FirstRunPlan.Check.REMOTE_SIGN_IN;
        }
    }

    private String accountName(UserMenuAccounts.Kind kind) {
        return activity.getResources().getStringArray(R.array.user_menu_account_names)
                [kind.ordinal()];
    }

    private String accountValue(UserMenuAccounts.Row row) {
        if (row.state == UserMenuAccounts.State.UNKNOWN) {
            return s(loading ? R.string.user_menu_checking : R.string.user_menu_unknown);
        }
        if (row.state == UserMenuAccounts.State.READY && !row.name.isEmpty()) return row.name;
        return activity.getResources().getStringArray(R.array.user_menu_account_values)
                [row.kind.ordinal() * 2 + (row.state == UserMenuAccounts.State.READY ? 0 : 1)];
    }

    /** The fix card's subtitle: the wizard's long state text for the same check. */
    private String accountState(UserMenuAccounts.Row row) {
        if (row.state == UserMenuAccounts.State.UNKNOWN) {
            return s(loading ? R.string.user_menu_checking : R.string.first_run_check_unknown);
        }
        return activity.getResources().getStringArray(R.array.first_run_check_states)
                [check(row.kind).ordinal() * 2
                + (row.state == UserMenuAccounts.State.READY ? 0 : 1)];
    }

    private String s(int id, Object... args) {
        return activity.getString(id, args);
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
