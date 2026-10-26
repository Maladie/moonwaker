package com.limelight.console;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.binding.PlatformBinding;
import com.limelight.console.FirstRunPlan.Check;
import com.limelight.console.FirstRunPlan.Entry;
import com.limelight.console.FirstRunPlan.Status;
import com.limelight.console.FirstRunPlan.Step;
import com.limelight.console.FirstRunPlan.WakePhase;
import com.limelight.console.FirstRunWizardPage.Mark;
import com.limelight.console.FirstRunWizardPage.Row;
import com.limelight.console.FirstRunWizardPage.Screen;
import com.limelight.gateway.GatewayConnection;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.preferences.GlPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.session.WatchNextTile;
import com.limelight.stream.StreamingAutopilot;
import com.limelight.ui.ControllerGlyphs;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.UiHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The first-run wizard (FIRST_RUN_WIZARD_DESIGN_PL.md): the one owner of its own state, the
 * step on screen, the steps skipped per host ("To finish") and the run to resume after the app
 * was left or recreated (a language change), per TV. Host facts (pairing, profiles,
 * capabilities) are never kept here: every step reads them again from the store and the
 * Gateway. What the console owns (sleep and wake, the PIN page, profile creation, entering a
 * host) it asks for through {@link Host}.
 *
 * <p>Only the user moves between steps and sub-screens; discovery, polling and pairing progress
 * update what is on screen in place and never take focus.
 */
final class FirstRunWizard {
    /** Step B's download link (design 5.2); the QR is generated from it at runtime. */
    static final String INSTALLER_URL = "https://tinyurl.com/2aslg7lk";
    static final String STEAM_KEY_URL = "https://steamcommunity.com/dev/apikey";
    static final String PREFS = "first_run_wizard";
    static final long REFRESH_MS = 3_000L;
    static final long WAKE_TICK_MS = 1_000L;
    /** After this long without a host, step C lights "I can't see the computer". */
    static final long NOT_FOUND_AFTER_MS = 30_000L;
    static final int CODE_LENGTH = 6;
    private static final String NEXT_AFTER_LANGUAGE = "next_after_language";
    private static final String RESUME_ENTRY = "resume_entry";
    private static final String RESUME_STEP = "resume_step";
    private static final String RESUME_HOST = "resume_host";
    private static final String RESUME_PROFILE = "resume_profile";
    private static final String RESUME_PARENT_PANEL = "resume_parent_panel";
    private static final String TODO_PREFIX = "todo.";

    /** What the console does for the wizard. */
    interface Host {
        /** Every host this TV knows, discovered ones included. */
        Collection<ComputerDetails> hosts();

        ComputerDetails host(String uuid);

        /** "Enter IP address": the console's manual address screen. */
        void addHostByAddress();

        void wake(ComputerDetails host);

        /** The console's sleep request, as from the host panel. */
        void sleep(ComputerDetails host, GatewayConnection connection);

        /** #45 "Add profile"; {@code done} gets the new profile ID, or null. */
        void createAdultProfile(ComputerDetails host, HostGatewayClient.IntegrationProfile actor,
                                Consumer<String> done);

        /** #44: the first PIN of an adult profile on the PIN page; {@code done} on any exit. */
        void setAdultPin(ComputerDetails host, HostGatewayClient.IntegrationProfile profile,
                         GatewayConnection connection, Runnable done);

        void saveProfiles(String hostId, HostGatewayClient.IntegrationProfiles profiles);

        /** A host the wizard paired: no longer a "newly discovered" one. */
        void paired(String hostId);

        /** "Connect a pad": the TV's accessory pairing, after Bluetooth access is granted. */
        void pairController();

        /** The "To finish" list of a host changed: its tile's "!" may change. */
        void todoChanged();

        String uniqueId();

        String autoLoginHost();

        void setAutoLoginHost(String hostId);

        boolean menuSounds();

        void setMenuSounds(boolean on);

        boolean playStationButtons();

        /** A pad is connected to the TV. */
        boolean controllerPresent();

        /** The last input came from a pad (else the remote): the legend's glyphs follow it. */
        boolean controllerInput();

        void showFeatures(HostGatewayClient.Capabilities capabilities, String eyebrow,
                          Runnable onNext);

        /**
         * The wizard closed: enter {@code hostId} as {@code profileId} (then open the parent
         * panel), or, with no host, go back to the host screen.
         */
        void finished(String hostId, String profileId, boolean parentPanel);
    }

    /** Where step C stands for the chosen host. */
    private enum Pairing { IDLE, BUSY, FAILED }

    private final Activity activity;
    private final Host host;
    private final SharedPreferences state;
    private final SharedPreferences console;
    private final HostGatewayStore store;
    private final HostGatewayClient client;
    private final HostPairingFlow pairingFlow;
    private final ConsoleUiFeedback feedback;
    private final ExecutorService executor;
    private final Handler main;
    private final FirstRunWizardPage page;
    private final Runnable tick = this::tick;

    private Entry entry = Entry.FIRST_RUN;
    private Step step = Step.WELCOME;
    private String hostId;
    /** Picked in step D; the console switches to it when the wizard ends. */
    private String chosenProfileId;
    private boolean parentPanel;
    /** Facts of the chosen host's last successful reads, for this run only. */
    private HostGatewayClient.Capabilities capabilities;
    private int generation;
    /** The legend's glyph set as drawn: a pad's, else the remote's. */
    private boolean padLegend;

    /** B and C: "I can't see the computer" is open over the step. */
    private boolean notFound;
    private long discoveryStartedAt;
    private boolean chooseHost;
    private Pairing pairing = Pairing.IDLE;
    /** Index of the pairing stage in progress (0 Gateway, 1 ticket, 2 Vibepollo, 3 confirm). */
    private int pairingStage;
    private HostPairingFlow.Failure pairingFailure;
    private final StringBuilder code = new StringBuilder();
    /** Paired by this run; the manager's next poll confirms it. */
    private String justPaired;

    private WakePhase wake = WakePhase.NOT_TESTED;
    private long wakePhaseAt;

    private StreamingAutopilotController.Analysis analysis;
    private String analysisError;
    private boolean analysing;
    private boolean applied;

    FirstRunWizard(Activity activity, Host host, SharedPreferences console,
                   HostGatewayStore store, HostGatewayClient client, HostPairingFlow pairingFlow,
                   ConsoleUiFeedback feedback, ExecutorService executor, Handler main,
                   ConsoleEntryShell shell, boolean reducedMotion) {
        this.activity = activity;
        this.host = host;
        this.console = console;
        this.store = store;
        this.client = client;
        this.pairingFlow = pairingFlow;
        this.feedback = feedback;
        this.executor = executor;
        this.main = main;
        state = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        page = new FirstRunWizardPage(activity, shell, reducedMotion);
    }

    // --- Entering and leaving --------------------------------------------------------------

    /**
     * At start: resumes the run the app was left in (or recreated in, by a language change) on
     * its step, host and profile, or opens the first run on a TV with no paired host. The owner
     * with paired hosts never sees it unless a run is pending.
     */
    boolean autoStart(boolean anyPairedHost) {
        if (!wantsToStart(anyPairedHost)) return false;
        Step resume = stored(RESUME_STEP, Step.class);
        if (resume == null) {
            open(Entry.FIRST_RUN, null);
            return true;
        }
        Entry resumed = stored(RESUME_ENTRY, Entry.class);
        String resumeHost = state.getString(RESUME_HOST, "");
        String resumeProfile = state.getString(RESUME_PROFILE, "");
        openAt(resumed == null ? Entry.FIRST_RUN : resumed,
                resumeHost.isEmpty() ? null : resumeHost, resume);
        if (!resumeProfile.isEmpty()) chosenProfileId = resumeProfile;
        parentPanel = state.getBoolean(RESUME_PARENT_PANEL, false);
        return true;
    }

    boolean wantsToStart(boolean anyPairedHost) {
        return FirstRunPlan.autoShow(anyPairedHost, stored(RESUME_STEP, Step.class));
    }

    void open(Entry entry, String hostId) {
        openAt(entry, hostId, FirstRunPlan.first(entry));
    }

    /** Opens a run at {@code step}, e.g. a "To finish" item from Settings. */
    void openAt(Entry entry, String hostId, Step step) {
        this.entry = entry;
        this.hostId = hostId;
        chosenProfileId = null;
        parentPanel = false;
        capabilities = null;
        go(step);
    }

    boolean isShowing() {
        return page.isShowing();
    }

    void setReducedMotion(boolean reducedMotion) {
        page.setReducedMotion(reducedMotion);
    }

    /**
     * Back on screen (the app resumed, the host list loaded, a console screen over the wizard
     * closed): the step reads its facts again and draws them in place.
     */
    void refresh() {
        if (!page.isShowing()) return;
        if (step == Step.PROFILE) fetchProfiles();
        if (step == Step.READINESS) fetchReadiness();
        if (page.onScreen()) {
            render(false);
            page.reclaimFocus();
        }
        schedule();
    }

    void pause() {
        main.removeCallbacks(tick);
    }

    private void close() {
        ComputerDetails chosen = chosenHost();
        String paired = chosen != null && isPaired(chosen) ? chosen.uuid : null;
        end(paired, paired == null ? null : profileId(), false);
    }

    private void finish() {
        end(hostId, profileId(), parentPanel);
    }

    private void end(String finishedHost, String profileId, boolean openParentPanel) {
        generation++;
        main.removeCallbacks(tick);
        state.edit().remove(RESUME_ENTRY).remove(RESUME_STEP).remove(RESUME_HOST)
                .remove(RESUME_PROFILE).remove(RESUME_PARENT_PANEL).apply();
        page.hide();
        host.finished(finishedHost, profileId, openParentPanel);
    }

    /** The resume record: everything the run needs to come back on the same step. */
    private void saveResume() {
        SharedPreferences.Editor editor = state.edit()
                .putString(RESUME_ENTRY, entry.name()).putString(RESUME_STEP, step.name())
                .putBoolean(RESUME_PARENT_PANEL, parentPanel);
        if (hostId == null) editor.remove(RESUME_HOST);
        else editor.putString(RESUME_HOST, hostId);
        if (chosenProfileId == null) editor.remove(RESUME_PROFILE);
        else editor.putString(RESUME_PROFILE, chosenProfileId);
        editor.apply();
    }

    // --- "To finish" ------------------------------------------------------------------------

    /** Steps still open for {@code hostId}, in wizard order. */
    List<Step> todo(String hostId) {
        List<Step> steps = new ArrayList<>(todoSet(hostId));
        steps.sort(Comparator.naturalOrder());
        return steps;
    }

    boolean hasTodo(String hostId) {
        return !todoSet(hostId).isEmpty();
    }

    private Set<Step> todoSet(String hostId) {
        Set<Step> steps = EnumSet.noneOf(Step.class);
        if (hostId == null) return steps;
        for (String name : state.getStringSet(TODO_PREFIX + hostId, new HashSet<>())) {
            try {
                Step item = Step.valueOf(name);
                if (FirstRunPlan.onTodoList(item)) steps.add(item);
            } catch (IllegalArgumentException ignored) {
                // A step no longer in the wizard leaves the list.
            }
        }
        return steps;
    }

    private void leave() {
        if (hostId == null) return;
        boolean requiredOpen = FirstRunPlan.requiredOpen(readiness());
        Set<Step> before = todoSet(hostId);
        Set<Step> after = FirstRunPlan.afterLeaving(before, step, requiredOpen);
        if (after.equals(before)) return;
        Set<String> names = new HashSet<>();
        for (Step item : after) names.add(item.name());
        state.edit().putStringSet(TODO_PREFIX + hostId, names).apply();
        host.todoChanged();
    }

    /** "Don't remind me": the host's list is cleared; Settings can still run the wizard. */
    void dismissTodo(String hostId) {
        forgetHost(hostId);
        host.todoChanged();
    }

    /** The console forgot {@code hostId}: its list goes with it. */
    void forgetHost(String hostId) {
        if (hostId != null) state.edit().remove(TODO_PREFIX + hostId).apply();
    }

    // --- Navigation -------------------------------------------------------------------------

    private FirstRunPlan.Facts facts() {
        ComputerDetails chosen = chosenHost();
        HostGatewayClient.IntegrationProfile profile = chosenProfile();
        return new FirstRunPlan.Facts(chosen != null && isPaired(chosen), profile != null,
                profile != null && profile.isChild(),
                capabilities != null && capabilities.childProfiles, true);
    }

    private void go(Step target) {
        generation++;
        main.removeCallbacks(tick);
        step = target;
        saveResume();
        enter();
        render(true);
        schedule();
    }

    private void enter() {
        notFound = false;
        chooseHost = false;
        // A run opened on a later step (a "To finish" item) reads the host's facts first: they
        // decide which steps the run has, so its numbering and dots.
        if (capabilities == null && step.compareTo(Step.READINESS) > 0) fetchProfiles();
        switch (step) {
            case INSTALL:
                discoveryStartedAt = SystemClock.elapsedRealtime();
                break;
            case PAIR:
                pairing = Pairing.IDLE;
                pairingFailure = null;
                code.setLength(0);
                discoveryStartedAt = SystemClock.elapsedRealtime();
                // Picked once on entering; a computer found later only joins the list.
                List<ComputerDetails> found = sortedHosts();
                if (hostId == null && found.size() == 1
                        && (entry != Entry.ADD_HOST || !isPaired(found.get(0)))) {
                    hostId = found.get(0).uuid;
                    saveResume();
                }
                break;
            case PROFILE:
                fetchProfiles();
                break;
            case READINESS:
                wake = WakePhase.NOT_TESTED;
                fetchReadiness();
                break;
            case NETWORK:
                analysis = null;
                analysisError = null;
                analysing = false;
                applied = false;
                if (!metered()) analyse();
                break;
            case EXTRAS:
                proposeAutoLogin();
                break;
            case FEATURES:
                host.showFeatures(capabilities, eyebrow(), this::next);
                break;
            default:
                break;
        }
    }

    private void next() {
        if (!FirstRunPlan.canContinue(step, facts())) return;
        leave();
        Step following = FirstRunPlan.next(step, entry, facts());
        if (following == null) finish();
        else go(following);
    }

    private void skip() {
        if (!FirstRunPlan.skippable(step)) return;
        leave();
        Step following = FirstRunPlan.next(step, entry, facts());
        if (following == null) finish();
        else go(following);
    }

    /** Back closes what is open over the step first, then goes to the previous step. */
    private void back() {
        if (notFound) {
            notFound = false;
        } else if (step == Step.PAIR && pairing == Pairing.BUSY) {
            // Cancel: the pairing still running reports to a generation that is gone.
            generation++;
            pairing = Pairing.IDLE;
            code.setLength(0);
        } else if (step == Step.PAIR && pairing == Pairing.FAILED) {
            pairing = Pairing.IDLE;
            code.setLength(0);
        } else if (step == Step.PAIR && chooseHost && hostId != null) {
            chooseHost = false;
        } else if (step == Step.READINESS
                && (wake == WakePhase.SLEEPING || wake == WakePhase.WAKING)) {
            wake = WakePhase.NOT_TESTED;
        } else {
            Step previous = FirstRunPlan.previous(step, entry, facts());
            if (previous == null) close();
            else go(previous);
            return;
        }
        render(true);
        schedule();
    }

    private void showNotFound() {
        if (step != Step.INSTALL && step != Step.PAIR) return;
        notFound = true;
        render(true);
    }

    /**
     * Keys while the wizard is on screen and no dialog is over it: Back steps back, X skips or
     * goes on (deletes a digit while a code is typed), Y opens "I can't see the computer",
     * digits type the pairing code; everything else moves focus.
     */
    boolean handleKey(KeyEvent event) {
        if (!page.onScreen() || feedback.isModalShowing()) return false;
        if (host.controllerInput() != padLegend) render(false);
        int code = event.getKeyCode();
        if (codeShown()) {
            int digit = digit(code);
            boolean delete = code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_BUTTON_X;
            if (digit >= 0 || delete) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (delete) deleteDigit();
                    else typeDigit(digit);
                }
                return true;
            }
        }
        boolean backKey = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_BUTTON_B;
        boolean skipKey = code == KeyEvent.KEYCODE_BUTTON_X;
        boolean helpKey = code == KeyEvent.KEYCODE_BUTTON_Y;
        if (!backKey && !skipKey && !helpKey) return false;
        if (event.getAction() != KeyEvent.ACTION_UP) return true;
        if (backKey) {
            back();
        } else if (skipKey) {
            if (FirstRunPlan.skippable(step)) skip();
            else if (step != Step.PAIR) next();
        } else {
            showNotFound();
        }
        return true;
    }

    private static int digit(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return keyCode - KeyEvent.KEYCODE_0;
        }
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            return keyCode - KeyEvent.KEYCODE_NUMPAD_0;
        }
        return -1;
    }

    private void schedule() {
        main.removeCallbacks(tick);
        if (!page.isShowing()) return;
        boolean wakeTest = step == Step.READINESS
                && (wake == WakePhase.SLEEPING || wake == WakePhase.WAKING);
        boolean polls = step == Step.INSTALL || step == Step.PAIR || step == Step.READINESS
                || (step == Step.PROFILE && profiles().isEmpty());
        if (polls) main.postDelayed(tick, wakeTest ? WAKE_TICK_MS : REFRESH_MS);
    }

    private void tick() {
        if (!page.isShowing()) return;
        if (step == Step.READINESS && (wake == WakePhase.SLEEPING || wake == WakePhase.WAKING)) {
            advanceWakeTest();
            if (wake == WakePhase.OK || wake == WakePhase.FAILED) fetchReadiness();
            if (page.onScreen()) render(false);
        } else if (step == Step.READINESS) {
            fetchReadiness();
        } else if (step == Step.PROFILE) {
            fetchProfiles();
        } else if (page.onScreen()) {
            render(false);
        }
        schedule();
    }

    // --- Facts ------------------------------------------------------------------------------

    private ComputerDetails chosenHost() {
        return hostId == null ? null : host.host(hostId);
    }

    private boolean isPaired(ComputerDetails computer) {
        return store.load(computer.uuid) != null
                && (ConsoleActionCatalog.isRememberedPaired(computer)
                || computer.uuid.equals(justPaired));
    }

    /** The chosen host's Gateway as the chosen profile, or null before pairing. */
    private GatewayConnection connection() {
        ComputerDetails chosen = chosenHost();
        if (chosen == null) return null;
        GatewayConnection connection = store.loadForHost(chosen.uuid,
                chosen.activeAddress == null ? null : chosen.activeAddress.address);
        String profileId = profileId();
        return connection == null || profileId == null ? connection
                : connection.forProfile(profileId);
    }

    private List<HostGatewayClient.IntegrationProfile> profiles() {
        return hostId == null ? new ArrayList<>() : store.profileSelection(hostId).profiles;
    }

    /** Step D's pick, else the profile this TV uses on the host now. */
    private HostGatewayClient.IntegrationProfile chosenProfile() {
        if (hostId == null) return null;
        HostGatewayStore.ProfileSelection selection = store.profileSelection(hostId);
        HostGatewayClient.IntegrationProfile picked = selection.find(chosenProfileId);
        return picked != null ? picked : selection.selected;
    }

    private String profileId() {
        HostGatewayClient.IntegrationProfile profile = chosenProfile();
        return profile == null ? null : profile.id;
    }

    private void fetchProfiles() {
        GatewayConnection connection = connection();
        if (connection == null) return;
        String forHost = hostId;
        int token = generation;
        executor.execute(() -> {
            HostGatewayClient.IntegrationProfiles profiles;
            HostGatewayClient.Capabilities read = null;
            try {
                profiles = client.getIntegrationProfiles(connection);
            } catch (IOException | RuntimeException unavailable) {
                return;
            }
            try {
                read = client.getCapabilities(connection);
            } catch (IOException | RuntimeException unavailable) {
                // The profile list stands on its own.
            }
            HostGatewayClient.Capabilities result = read;
            main.post(() -> {
                if (token != generation || !forHost.equals(hostId)) return;
                host.saveProfiles(forHost, profiles);
                if (result != null) capabilities = result;
                if (page.onScreen()) render(false);
                schedule();
            });
        });
    }

    private void fetchReadiness() {
        GatewayConnection connection = connection();
        if (connection == null) return;
        String forHost = hostId;
        int token = generation;
        executor.execute(() -> {
            HostGatewayClient.Capabilities read;
            HostGatewayClient.IntegrationProfiles profiles = null;
            try {
                read = client.getCapabilities(connection);
            } catch (IOException | RuntimeException unavailable) {
                read = null;
            }
            try {
                profiles = client.getIntegrationProfiles(connection);
            } catch (IOException | RuntimeException unavailable) {
                // The last saved profiles stand.
            }
            HostGatewayClient.Capabilities result = read;
            HostGatewayClient.IntegrationProfiles profileResult = profiles;
            main.post(() -> {
                if (token != generation || !forHost.equals(hostId)) return;
                // While the wake test has the PC asleep, its last answers stand.
                if (result != null || (wake != WakePhase.SLEEPING && wake != WakePhase.WAKING)) {
                    capabilities = result;
                }
                if (profileResult != null) host.saveProfiles(forHost, profileResult);
                if (page.onScreen()) render(false);
            });
        });
    }

    private FirstRunPlan.Readiness readiness() {
        HostGatewayClient.Capabilities read = capabilities;
        HostGatewayClient.SetupFacts setup = read == null ? null : read.setup;
        HostGatewayClient.IntegrationProfile profile = chosenProfile();
        return new FirstRunPlan.Readiness(
                read == null ? null : read.vibepolloFix,
                read == null ? null : read.playnite,
                setup == null ? null : setup.steamApiKeySaved,
                setup == null ? null : setup.epicSignedIn,
                profile == null ? null
                        : profile.remoteSignIn && "ready".equals(profile.remoteSignInState),
                wake);
    }

    // --- Rendering --------------------------------------------------------------------------

    private String eyebrow() {
        if (step == Step.WELCOME) return text(R.string.console_entry_eyebrow);
        List<Step> steps = FirstRunPlan.steps(entry, facts());
        int index = steps.indexOf(step);
        String name = stepName(step);
        boolean welcome = steps.contains(Step.WELCOME);
        int number = welcome ? index : index + 1;
        return (index >= 0 && step != Step.DONE
                ? activity.getString(R.string.first_run_eyebrow, number, name) : name)
                .toUpperCase(Locale.getDefault());
    }

    /** {@code userMove}: the user changed step or sub-screen, so the step's focus applies. */
    private void render(boolean userMove) {
        padLegend = host.controllerInput();
        Screen screen = screen();
        screen.eyebrow = eyebrow();
        List<Step> steps = FirstRunPlan.steps(entry, facts());
        page.render(steps.size(), Math.max(0, steps.indexOf(step)), screen, userMove);
    }

    private Screen screen() {
        switch (step) {
            case WELCOME: return welcome();
            case INSTALL: return notFound ? notFound() : install();
            case PAIR: return notFound ? notFound() : pair();
            case PROFILE: return profile();
            case READINESS: return wake == WakePhase.SLEEPING || wake == WakePhase.WAKING
                    ? wakeTest() : checklist();
            case NETWORK: return network();
            case FAMILY: return family();
            case EXTRAS: return extras();
            case CONTROLS: return controls();
            case FEATURES: return features();
            default: return done();
        }
    }

    private boolean codeShown() {
        return step == Step.PAIR && page.shape().startsWith("C:code");
    }

    // Legend entries: a pad's glyphs when the last input came from a pad, else the remote's.

    private void selectKey(Screen screen, int label) {
        if (padLegend) screen.glyph(glyph(ControllerGlyphs.Button.CONFIRM), text(label));
        else screen.chip("OK", text(label));
    }

    private void skipKey(Screen screen, int label) {
        if (padLegend) screen.glyph(glyph(ControllerGlyphs.Button.WEST), text(label));
    }

    private void backKey(Screen screen, int label) {
        if (padLegend) screen.glyph(glyph(ControllerGlyphs.Button.CANCEL), text(label));
        else screen.icon(R.drawable.ic_console_back, text(label));
    }

    private String glyph(ControllerGlyphs.Button button) {
        return ControllerGlyphs.text(host.playStationButtons(), button);
    }

    private String text(int id, Object... args) {
        return activity.getString(id, args);
    }

    private String[] array(int id) {
        return activity.getResources().getStringArray(id);
    }

    private static <T extends View> T match(T view) {
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    // A (mockup 01): language and controller.
    private Screen welcome() {
        boolean pad = host.controllerPresent();
        Screen screen = new Screen("A:" + pad);
        screen.title = text(R.string.first_run_welcome_title);
        screen.subtitle = text(R.string.first_run_welcome_subtitle);
        LinearLayout body = page.column(Gravity.CENTER_HORIZONTAL);
        page.add(body, page.label(text(R.string.console_stream_language)),
                ViewGroup.LayoutParams.WRAP_CONTENT, 0);
        String[] names = array(R.array.language_names);
        String[] values = array(R.array.language_values);
        String current = UiHelper.currentLanguage(activity);
        List<View> languages = new ArrayList<>();
        for (int index = 0; index < names.length; index++) {
            String value = values[index];
            TextView pill = page.pill("a.lang:" + value, names[index], 0, () -> {
                // With a pad, picking a language moves on to Next; the flag survives the
                // recreation below, welcome() consumes it.
                if (pad) state.edit().putBoolean(NEXT_AFTER_LANGUAGE, true).commit();
                if (value.equals(UiHelper.currentLanguage(activity))) {
                    if (pad) render(false);
                    return;
                }
                // The console is recreated in the new language and resumes this step.
                UiHelper.applyLanguage(activity, value);
            });
            pill.setSelected(value.equals(current));
            if (value.equals(current)) {
                page.highlight(pill, true);
                screen.focus = pill;
            }
            languages.add(pill);
        }
        page.add(body, page.pills(languages.toArray(new View[0])),
                ViewGroup.LayoutParams.WRAP_CONTENT, 14);

        LinearLayout card = page.card(28, 22);
        LinearLayout heading = page.line(Gravity.CENTER_VERTICAL);
        heading.addView(page.icon(R.drawable.ic_console_gamepad, FirstRunWizardPage.TEXT),
                new LinearLayout.LayoutParams(page.dp(30), page.dp(30)));
        LinearLayout words = page.column(Gravity.START);
        words.addView(page.text(text(R.string.first_run_controller_section), 16,
                FirstRunWizardPage.TEXT, true));
        page.add(words, page.wrapped(text(pad ? R.string.first_run_controller_found
                        : R.string.first_run_controller_missing), 13,
                FirstRunWizardPage.TEXT_SECONDARY, false, 2),
                ViewGroup.LayoutParams.MATCH_PARENT, 6);
        page.beside(heading, words, 0, 18);
        page.add(card, heading, ViewGroup.LayoutParams.MATCH_PARENT, 0);
        // One key for "go on" whether a pad is there or not: focus stays when one connects.
        TextView connect = pad ? null : page.pill("a.connect",
                text(R.string.first_run_connect_controller), R.drawable.ic_console_gamepad,
                host::pairController);
        TextView onward = page.pill("a.next", text(pad ? R.string.first_run_next
                : R.string.first_run_skip), 0, this::next);
        page.add(card, page.pills(connect, onward), ViewGroup.LayoutParams.MATCH_PARENT, 18);
        page.add(body, card, 480, 34);
        if (pad && state.getBoolean(NEXT_AFTER_LANGUAGE, false)) {
            state.edit().remove(NEXT_AFTER_LANGUAGE).apply();
            screen.focus = onward;
        }
        if (screen.focus == null) screen.focus = onward;
        screen.body = body;
        selectKey(screen, R.string.console_select_hint);
        backKey(screen, R.string.first_run_back);
        return screen;
    }

    // B (mockup 02): install the host while hosts are searched for.
    private Screen install() {
        Screen screen = new Screen("B");
        screen.title = text(R.string.first_run_install_title);
        screen.subtitle = text(R.string.first_run_install_subtitle);
        LinearLayout body = page.line(Gravity.CENTER_VERTICAL);

        LinearLayout qrCard = page.card(20, 24);
        qrCard.setGravity(Gravity.CENTER_HORIZONTAL);
        qrCard.addView(page.qr(INSTALLER_URL, 170));
        TextView url = page.text(INSTALLER_URL.replaceFirst("^https://", ""), 15,
                FirstRunWizardPage.TEXT, true);
        url.setGravity(Gravity.CENTER);
        url.setMaxLines(1);
        page.add(qrCard, url, ViewGroup.LayoutParams.MATCH_PARENT, 16);
        body.addView(qrCard, new LinearLayout.LayoutParams(page.dp(250),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout steps = page.column(Gravity.START);
        int[][] items = {
                {R.string.first_run_install_step1, R.string.first_run_install_step1_detail},
                {R.string.first_run_install_step2, R.string.first_run_install_step2_detail},
                {R.string.first_run_install_step3, R.string.first_run_install_step3_detail}};
        for (int index = 0; index < items.length; index++) {
            LinearLayout item = page.line(Gravity.TOP);
            item.addView(page.number(index + 1), new LinearLayout.LayoutParams(page.dp(22),
                    page.dp(22)));
            LinearLayout words = page.column(Gravity.START);
            words.addView(match(page.wrapped(text(items[index][0]), 16,
                    FirstRunWizardPage.TEXT, true, 2)));
            page.add(words, page.wrapped(text(items[index][1]), 12,
                    FirstRunWizardPage.TEXT_SECONDARY, false, 2),
                    ViewGroup.LayoutParams.MATCH_PARENT, 5);
            page.beside(item, words, 0, 14);
            ((LinearLayout.LayoutParams) words.getLayoutParams()).gravity = Gravity.TOP;
            page.add(steps, item, ViewGroup.LayoutParams.MATCH_PARENT, index == 0 ? 0 : 18);
        }
        LinearLayout search = page.line(Gravity.CENTER_VERTICAL);
        Mark mark = new Mark(page);
        search.addView(mark, new LinearLayout.LayoutParams(page.dp(18), page.dp(18)));
        TextView searching = page.wrapped("", 13, FirstRunWizardPage.TEXT_SECONDARY, false, 2);
        page.beside(search, searching, 0, 18);
        page.add(steps, search, ViewGroup.LayoutParams.MATCH_PARENT, 24);
        TextView next = page.pill("b.next", text(R.string.first_run_next), 0, this::next);
        TextView missing = page.pill("b.missing", text(R.string.first_run_not_found), 0,
                this::showNotFound);
        LinearLayout actions = page.pills(next, missing);
        actions.setGravity(Gravity.START);
        page.add(steps, actions, ViewGroup.LayoutParams.WRAP_CONTENT, 22);
        page.beside(body, steps, 410, 40);

        // A computer found only lights Next; it never moves on by itself.
        screen.bind(() -> {
            List<ComputerDetails> found = sortedHosts();
            mark.set(found.isEmpty() ? Mark.SPINNER : Mark.OK);
            searching.setText(found.isEmpty() ? text(R.string.first_run_searching)
                    : text(R.string.first_run_found_title, found.get(0).name));
            page.highlight(next, !found.isEmpty());
        });
        screen.body = body;
        screen.focus = next;
        selectKey(screen, R.string.console_select_hint);
        if (padLegend) {
            screen.glyph(glyph(ControllerGlyphs.Button.NORTH), text(R.string.first_run_not_found));
        }
        backKey(screen, R.string.first_run_back);
        return screen;
    }

    private List<ComputerDetails> sortedHosts() {
        List<ComputerDetails> list = new ArrayList<>(host.hosts());
        list.sort(Comparator.comparing((ComputerDetails computer) ->
                        computer.state != ComputerDetails.State.ONLINE)
                .thenComparing(computer -> computer.name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** Mockup 02b: "I can't see the computer", over step B or C. */
    private Screen notFound() {
        Screen screen = new Screen("notfound");
        screen.title = text(R.string.first_run_not_found_title);
        screen.subtitle = text(R.string.first_run_not_found_subtitle);
        LinearLayout body = page.column(Gravity.CENTER_HORIZONTAL);
        LinearLayout card = page.card(28, 22);
        String[] titles = array(R.array.first_run_not_found_reasons);
        String[] details = array(R.array.first_run_not_found_fixes);
        for (int index = 0; index < titles.length; index++) {
            Row row = page.row("notfound." + index, true, titles[index], details[index], false,
                    null);
            row.mark.set(Mark.BULLET);
            row.view.setPadding(0, 0, 0, 0);
            page.add(card, row.view, ViewGroup.LayoutParams.MATCH_PARENT, index == 0 ? 0 : 12);
        }
        page.add(body, card, 540, 0);
        TextView again = page.pill("notfound.again", text(R.string.first_run_search_again), 0,
                () -> {
                    notFound = false;
                    discoveryStartedAt = SystemClock.elapsedRealtime();
                    render(true);
                });
        TextView address = page.pill("notfound.address", text(R.string.first_run_enter_address),
                0, host::addHostByAddress);
        page.add(body, page.pills(again, address), ViewGroup.LayoutParams.WRAP_CONTENT, 20);
        screen.body = body;
        screen.focus = again;
        selectKey(screen, R.string.console_select_hint);
        backKey(screen, R.string.first_run_back);
        return screen;
    }

    // C (mockups 03, 03b, 03c): choose the host and pair it.
    private Screen pair() {
        List<ComputerDetails> found = sortedHosts();
        ComputerDetails chosen = chosenHost();
        if (chosen == null || chooseHost) return hostList(found);
        if (pairing == Pairing.BUSY) return pairingProgress(chosen);
        if (pairing == Pairing.FAILED && pairingFailure != null) return repair(chosen, found);
        if (isPaired(chosen)) return paired(chosen, found);
        GatewayConnection gateway = connection();
        if (chosen.state != ComputerDetails.State.ONLINE) return asleep(chosen, found);
        if (gateway != null) return streamOnly(chosen, gateway, found);
        return codeEntry(chosen);
    }

    private TextView otherHostPill(List<ComputerDetails> found) {
        return found.size() > 1 ? page.pill("c.other", text(R.string.first_run_change_host), 0,
                () -> {
                    chooseHost = true;
                    render(true);
                }) : null;
    }

    private Screen hostList(List<ComputerDetails> found) {
        StringBuilder shape = new StringBuilder("C:list");
        for (ComputerDetails computer : found) shape.append(':').append(computer.uuid);
        Screen screen = new Screen(shape.toString());
        screen.title = text(found.isEmpty() ? R.string.first_run_searching_title
                : R.string.first_run_choose_host_title);
        screen.subtitle = text(R.string.first_run_choose_host_subtitle);
        LinearLayout body = page.column(Gravity.CENTER_HORIZONTAL);
        LinearLayout card = page.card(14, 12);
        if (found.isEmpty()) {
            Row row = page.row("c.searching", true, text(R.string.first_run_searching), null,
                    false, null);
            row.mark.set(Mark.SPINNER);
            card.addView(match(row.view));
        }
        for (ComputerDetails computer : found) {
            String uuid = computer.uuid;
            Row row = page.row("c.host:" + uuid, true, computer.name, "", false, () -> {
                hostId = uuid;
                chooseHost = false;
                go(Step.PAIR);
            });
            screen.bind(() -> {
                ComputerDetails now = host.host(uuid);
                boolean online = now != null && now.state == ComputerDetails.State.ONLINE;
                row.mark.set(online ? Mark.OK : Mark.EMPTY);
                row.detail.setText(text(online ? R.string.console_status_online
                        : R.string.console_status_offline));
            });
            card.addView(match(row.view));
            if (screen.focus == null || uuid.equals(hostId)) screen.focus = row.view;
        }
        page.add(body, card, 460, 0);
        TextView missing = page.pill("c.missing", text(R.string.first_run_not_found), 0,
                this::showNotFound);
        page.add(body, page.pills(missing), ViewGroup.LayoutParams.WRAP_CONTENT, 20);
        // After a while without a computer, "I can't see the computer" lights up.
        screen.bind(() -> page.highlight(missing, found.isEmpty()
                && SystemClock.elapsedRealtime() - discoveryStartedAt > NOT_FOUND_AFTER_MS));
        if (screen.focus == null) screen.focus = missing;
        screen.body = body;
        selectKey(screen, R.string.console_select_hint);
        backKey(screen, R.string.first_run_back);
        return screen;
    }

    private Screen codeEntry(ComputerDetails chosen) {
        Screen screen = new Screen("C:code:" + chosen.uuid);
        screen.title = text(R.string.first_run_pair_title, chosen.name);
        screen.subtitle = text(R.string.first_run_code_subtitle);
        LinearLayout body = page.column(Gravity.CENTER_HORIZONTAL);
        LinearLayout cells = page.line(Gravity.CENTER);
        List<TextView> digits = new ArrayList<>();
        for (int index = 0; index < CODE_LENGTH; index++) {
            TextView cell = page.digit();
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(page.dp(42),
                    page.dp(50));
            params.leftMargin = index == 0 ? 0 : page.dp(9);
            cells.addView(cell, params);
            digits.add(cell);
        }
        body.addView(cells);
        LinearLayout keypad = page.column(Gravity.CENTER_HORIZONTAL);
        String[] labels = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", ""};
        View first = null;
        for (int rowIndex = 0; rowIndex < 4; rowIndex++) {
            LinearLayout keys = page.line(Gravity.CENTER);
            for (int column = 0; column < 3; column++) {
                int index = rowIndex * 3 + column;
                View key;
                if (index == 9) {
                    key = page.keypadKey("c.key.delete", "", R.drawable.ic_console_backspace,
                            this::deleteDigit);
                    key.setContentDescription(text(R.string.first_run_delete));
                } else if (index == 11) {
                    key = new View(activity);
                } else {
                    int value = Integer.parseInt(labels[index]);
                    key = page.keypadKey("c.key." + value, labels[index], 0,
                            () -> typeDigit(value));
                    if (value == 1) first = key;
                }
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(page.dp(60),
                        page.dp(36));
                params.leftMargin = column == 0 ? 0 : page.dp(7);
                keys.addView(key, params);
            }
            page.add(keypad, keys, ViewGroup.LayoutParams.WRAP_CONTENT, rowIndex == 0 ? 0 : 7);
        }
        page.add(body, keypad, ViewGroup.LayoutParams.WRAP_CONTENT, 22);
        TextView expiry = page.wrapped(text(R.string.first_run_code_expiry), 13,
                FirstRunWizardPage.TEXT_SECONDARY, false, 2);
        expiry.setGravity(Gravity.CENTER);
        page.add(body, expiry, 560, 14);
        screen.bind(() -> {
            for (int index = 0; index < CODE_LENGTH; index++) {
                TextView cell = digits.get(index);
                cell.setText(index < code.length() ? String.valueOf(code.charAt(index)) : "");
                cell.setActivated(index == code.length());
            }
        });
        screen.body = body;
        screen.focus = first;
        selectKey(screen, R.string.first_run_type);
        if (padLegend) screen.glyph(glyph(ControllerGlyphs.Button.WEST), text(R.string.first_run_delete));
        else screen.icon(R.drawable.ic_console_backspace, text(R.string.first_run_delete));
        backKey(screen, R.string.first_run_back);
        return screen;
    }

    private void typeDigit(int value) {
        if (!codeShown() || code.length() >= CODE_LENGTH) return;
        code.append(value);
        if (code.length() < CODE_LENGTH) {
            render(false);
            return;
        }
        ComputerDetails current = chosenHost();
        if (current == null || current.activeAddress == null) {
            code.setLength(0);
            feedback.notify(ConsoleUiFeedback.Kind.ERROR, text(R.string.pair_pc_offline));
            render(false);
            return;
        }
        pairing = Pairing.BUSY;
        pairingStage = 0;
        pairingFlow.pairWithCode(current, code.toString(), pairingListener());
        render(true);
    }

    private void deleteDigit() {
        if (!codeShown() || code.length() == 0) return;
        code.setLength(code.length() - 1);
        render(false);
    }

    private Screen pairingProgress(ComputerDetails chosen) {
        Screen screen = new Screen("C:busy:" + chosen.uuid);
        screen.title = text(R.string.first_run_pairing_title);
        screen.subtitle = text(R.string.first_run_pairing_wait);
        LinearLayout body = page.column(Gravity.CENTER_HORIZONTAL);
        LinearLayout card = page.card(28, 18);
        String[] stages = array(R.array.first_run_pairing_stages);
        for (int index = 0; index < stages.length; index++) {
            int stage = index;
            Row row = page.row("c.stage." + index, true, stages[index], null, false, null);
            row.view.setMinimumHeight(page.dp(44));
            screen.bind(() -> {
                row.mark.set(stage < pairingStage ? Mark.OK
                        : stage == pairingStage ? Mark.SPINNER : Mark.EMPTY);
                row.title.setTextColor(stage <= pairingStage ? FirstRunWizardPage.TEXT
                        : FirstRunWizardPage.HEADER);
            });
            card.addView(match(row.view));
        }
        page.add(body, card, 380, 0);
        screen.body = body;
        backKey(screen, R.string.first_run_cancel);
        return screen;
    }

    private Screen repair(ComputerDetails chosen, List<ComputerDetails> found) {
        HostPairingFlow.Failure failure = pairingFailure;
        Screen screen = new Screen("C:failed:" + chosen.uuid + ":" + failure.problem);
        screen.title = text(R.string.first_run_pairing_failed_title);
        screen.subtitle = text(R.string.first_run_pairing_failed_stage,
                array(R.array.first_run_pairing_stages)[pairingStage]);
        String problem;
        String help;
        TextView primary;
        TextView second = null;
        switch (failure.problem) {
            case CODE:
                problem = text(R.string.first_run_code_failed_title);
                help = text(R.string.first_run_code_new_body);
                primary = page.pill("c.retry", text(R.string.first_run_enter_new_code), 0,
                        this::retypeCode);
                break;
            case UNREACHABLE:
                problem = text(R.string.first_run_unreachable_title, chosen.name);
                help = text(R.string.first_run_unreachable_body);
                primary = page.pill("c.retry", text(R.string.first_run_retry), 0,
                        this::retypeCode);
                second = page.pill("c.missing", text(R.string.first_run_not_found), 0,
                        this::showNotFound);
                break;
            case VIBEPOLLO:
                problem = text(R.string.first_run_vibepollo_failed_title);
                help = text(R.string.first_run_vibepollo_help_body);
                primary = page.pill("c.retry", text(R.string.first_run_retry), 0,
                        () -> retryPairing(chosen));
                second = page.pill("c.manual", text(R.string.first_run_pair_manually), 0,
                        () -> pairManually(chosen));
                break;
            default:
                problem = text(R.string.first_run_pairing_other_title);
                help = text(R.string.first_run_pairing_other_body);
                primary = page.pill("c.retry", text(R.string.first_run_retry), 0,
                        () -> retryPairing(chosen));
                second = page.pill("c.manual", text(R.string.first_run_pair_manually), 0,
                        () -> pairManually(chosen));
                break;
        }
        LinearLayout body = page.column(Gravity.CENTER_HORIZONTAL);
        LinearLayout card = page.card(30, 24);
        LinearLayout line = page.line(Gravity.TOP);
        Mark mark = new Mark(page);
        mark.set(Mark.ATTENTION);
        line.addView(mark, new LinearLayout.LayoutParams(page.dp(18), page.dp(18)));
        LinearLayout words = page.column(Gravity.START);
        words.addView(match(page.wrapped(problem, 17, FirstRunWizardPage.TEXT, true, 2)));
        page.add(words, page.wrapped(help, 13, FirstRunWizardPage.TEXT_SECONDARY, false, 4),
                ViewGroup.LayoutParams.MATCH_PARENT, 8);
        page.add(words, page.wrapped(text(R.string.first_run_pairing_details, failure.detail),
                11, FirstRunWizardPage.HEADER, false, 3), ViewGroup.LayoutParams.MATCH_PARENT, 14);
        page.beside(line, words, 0, 14);
        ((LinearLayout.LayoutParams) words.getLayoutParams()).gravity = Gravity.TOP;
        card.addView(line);
        page.add(body, card, 460, 0);
        TextView helpPill = page.pill("c.help", text(R.string.first_run_help), 0,
                this::showPairingHelp);
        page.add(body, page.pills(primary, second, helpPill), ViewGroup.LayoutParams.WRAP_CONTENT,
                22);
        screen.body = body;
        screen.focus = primary;
        selectKey(screen, R.string.console_select_hint);
        backKey(screen, R.string.first_run_back);
        return screen;
    }

    private void retypeCode() {
        pairing = Pairing.IDLE;
        pairingFailure = null;
        code.setLength(0);
        render(true);
    }

    /** A message card with a mark, then pills; for C's simple sub-screens. */
    private LinearLayout messageBody(int markKind, String message, View... pills) {
        LinearLayout body = page.column(Gravity.CENTER_HORIZONTAL);
        LinearLayout card = page.card(26, 20);
        LinearLayout line = page.line(Gravity.CENTER_VERTICAL);
        Mark mark = new Mark(page);
        mark.set(markKind);
        line.addView(mark, new LinearLayout.LayoutParams(page.dp(20), page.dp(20)));
        page.beside(line, page.wrapped(message, 14, FirstRunWizardPage.TEXT, false, 3), 0, 16);
        card.addView(line);
        page.add(body, card, 460, 0);
        page.add(body, page.pills(pills), ViewGroup.LayoutParams.WRAP_CONTENT, 22);
        return body;
    }

    private Screen paired(ComputerDetails chosen, List<ComputerDetails> found) {
        Screen screen = new Screen("C:paired:" + chosen.uuid + ":" + found.size());
        screen.title = text(R.string.first_run_paired_title, chosen.name);
        screen.subtitle = text(R.string.first_run_paired_subtitle);
        TextView next = page.pill("c.next", text(R.string.first_run_next), 0, this::next);
        screen.body = messageBody(Mark.OK, text(R.string.first_run_paired_body, chosen.name),
                next, otherHostPill(found));
        screen.focus = next;
        selectKey(screen, R.string.console_select_hint);
        backKey(screen, R.string.first_run_back);
        return screen;
    }

    private Screen asleep(ComputerDetails chosen, List<ComputerDetails> found) {
        Screen screen = new Screen("C:asleep:" + chosen.uuid + ":" + found.size());
        screen.title = text(R.string.first_run_pair_title, chosen.name);
        screen.subtitle = text(R.string.first_run_host_asleep);
        TextView wakeHost = page.pill("c.wake", text(R.string.first_run_wake_host), 0,
                () -> host.wake(chosen));
        wakeHost.setEnabled(ConsoleHostPresentation.canWake(chosen));
        TextView missing = page.pill("c.missing", text(R.string.first_run_not_found), 0,
                this::showNotFound);
        screen.body = messageBody(Mark.EMPTY, text(R.string.first_run_host_asleep_body),
                wakeHost.isEnabled() ? wakeHost : null, otherHostPill(found), missing);
        screen.focus = wakeHost.isEnabled() ? wakeHost : missing;
        selectKey(screen, R.string.console_select_hint);
        backKey(screen, R.string.first_run_back);
        return screen;
    }

    private Screen streamOnly(ComputerDetails chosen, GatewayConnection gateway,
                              List<ComputerDetails> found) {
        Screen screen = new Screen("C:stream:" + chosen.uuid + ":" + found.size());
        screen.title = text(R.string.first_run_pair_title, chosen.name);
        screen.subtitle = text(R.string.first_run_stream_only);
        TextView finish = page.pill("c.finish", text(R.string.first_run_finish_pairing), 0,
                () -> pairStream(chosen, gateway));
        screen.body = messageBody(Mark.EMPTY, text(R.string.first_run_stream_only_body),
                finish, otherHostPill(found));
        screen.focus = finish;
        selectKey(screen, R.string.console_select_hint);
        backKey(screen, R.string.first_run_back);
        return screen;
    }

    private void showPairingHelp() {
        feedback.showMessage(text(R.string.first_run_help), text(R.string.first_run_pairing_help),
                text(R.string.console_close), null);
    }

    private void retryPairing(ComputerDetails chosen) {
        GatewayConnection gateway = connection();
        if (gateway == null) retypeCode();
        else pairStream(chosen, gateway);
    }

    private void pairStream(ComputerDetails chosen, GatewayConnection gateway) {
        pairing = Pairing.BUSY;
        pairingStage = 1;
        pairingFlow.pairStream(chosen, gateway, null, false, () -> true, pairingListener());
        render(true);
    }

    private void pairManually(ComputerDetails chosen) {
        pairing = Pairing.BUSY;
        pairingStage = 2;
        pairingFlow.pairManually(chosen, pairingListener());
        render(true);
    }

    /** The stage row a progress label belongs to. */
    private int stageIndex(String stage) {
        if (stage.equals(text(R.string.console_pair_host_gateway))) return 0;
        if (stage.equals(text(R.string.console_pair_stage_ticket))) return 1;
        if (stage.equals(text(R.string.console_pair_stage_confirmation))) return 3;
        return 2;
    }

    private HostPairingFlow.Listener pairingListener() {
        int token = generation;
        return new HostPairingFlow.Listener() {
            @Override public void onStage(String stage) {
                if (token != generation) return;
                pairingStage = stageIndex(stage);
                if (page.onScreen()) render(false);
            }

            @Override public void onManualPin(String pin) {
                if (token != generation) return;
                feedback.showMessage(text(R.string.pair_pairing_title),
                        text(R.string.pair_pairing_msg) + " " + pin + "\n\n"
                                + text(R.string.pair_pairing_help),
                        text(R.string.console_close), null);
            }

            @Override public void onPaired(ComputerDetails paired) {
                feedback.dismissModal();
                if (token != generation) return;
                pairing = Pairing.IDLE;
                justPaired = paired.uuid;
                host.paired(paired.uuid);
                render(true);
            }

            @Override public void onFailed(ComputerDetails failed,
                                           HostPairingFlow.Failure failure) {
                feedback.dismissModal();
                if (token != generation) return;
                pairing = Pairing.FAILED;
                pairingFailure = failure;
                render(true);
            }
        };
    }

    // D (mockup 04): who plays.
    private Screen profile() {
        List<HostGatewayClient.IntegrationProfile> profiles = profiles();
        HostGatewayClient.IntegrationProfile chosen = chosenProfile();
        ComputerDetails computer = chosenHost();
        boolean canAdd = computer != null && connection() != null;
        StringBuilder shape = new StringBuilder("D:" + canAdd);
        for (HostGatewayClient.IntegrationProfile profile : profiles) {
            shape.append(':').append(profile.id);
        }
        Screen screen = new Screen(shape.toString());
        screen.title = text(R.string.first_run_profile_title);
        screen.subtitle = text(R.string.first_run_profile_subtitle);
        LinearLayout body = page.column(Gravity.CENTER_HORIZONTAL);
        LinearLayout avatars = page.line(Gravity.TOP);
        int count = profiles.size() + (canAdd ? 1 : 0);
        int column = Math.min(160, 900 / Math.max(1, count));
        HostGatewayClient.IntegrationProfile adult = null;
        boolean onlyChildren = !profiles.isEmpty();
        for (HostGatewayClient.IntegrationProfile profile : profiles) {
            if (!profile.isChild()) {
                onlyChildren = false;
                if (adult == null) adult = profile;
            }
            String id = profile.id;
            LinearLayout avatar = page.avatar("d.profile:" + id, profile,
                    ConsoleProfileSelectView.displayName(activity, profile),
                    text(profile.isChild() ? R.string.first_run_profile_child
                            : R.string.first_run_profile_adult), () -> {
                        chosenProfileId = id;
                        capabilities = null;
                        saveResume();
                        next();
                    });
            avatars.addView(avatar, new LinearLayout.LayoutParams(page.dp(column),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            if (screen.focus == null || chosen != null && chosen.id.equals(id)) {
                screen.focus = avatar;
            }
        }
        if (canAdd) {
            HostGatewayClient.IntegrationProfile actor = adult;
            LinearLayout add = page.avatar("d.add", null, text(R.string.first_run_profile_add),
                    text(R.string.first_run_profile_add_detail),
                    () -> host.createAdultProfile(computer, actor, created -> {
                        if (created != null) {
                            chosenProfileId = created;
                            saveResume();
                        }
                        page.show();
                        refresh();
                        View again = page.find("d.add");
                        if (again != null) again.requestFocus();
                    }));
            avatars.addView(add, new LinearLayout.LayoutParams(page.dp(column),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            if (screen.focus == null) screen.focus = add;
        }
        body.addView(avatars);
        TextView note = page.wrapped(text(onlyChildren ? R.string.first_run_profile_adult_needed
                : profiles.isEmpty() ? R.string.first_run_profile_none
                : R.string.first_run_profile_refresh), 13,
                FirstRunWizardPage.TEXT_SECONDARY, false, 2);
        note.setGravity(Gravity.CENTER);
        page.add(body, note, 640, 26);
        screen.body = body;
        selectKey(screen, R.string.console_select_hint);
        backKey(screen, R.string.first_run_back);
        return screen;
    }

    // E (mockups 05, 05b): readiness checklist and the wake test.
    private Screen checklist() {
        Screen screen = new Screen("E");
        screen.title = text(R.string.first_run_ready_title);
        screen.subtitle = text(R.string.first_run_ready_subtitle);
        LinearLayout body = page.line(Gravity.TOP);
        LinearLayout left = page.column(Gravity.CENTER_HORIZONTAL);
        LinearLayout list = page.card(10, 8);
        String[] names = array(R.array.first_run_check_names);
        InfoCard info = new InfoCard();
        View firstAttention = null;
        for (Check check : Check.values()) {
            Row row = page.row("e.check:" + check.name(), true, names[check.ordinal()], "",
                    false, () -> {
                        Status now = FirstRunPlan.status(check, readiness());
                        if (check == Check.WAKE && now != Status.CHECKING) confirmWakeTest();
                    });
            row.view.setOnFocusChangeListener((view, focused) -> {
                if (focused) info.show(check);
            });
            screen.bind(() -> {
                Status status = FirstRunPlan.status(check, readiness());
                row.mark.set(status == Status.OK ? Mark.OK : status == Status.ATTENTION
                        ? Mark.ATTENTION : status == Status.CHECKING ? Mark.SPINNER : Mark.EMPTY);
                row.detail.setText(statusText(check, status));
            });
            list.addView(match(row.view));
            if (firstAttention == null && FirstRunPlan.status(check, readiness())
                    == Status.ATTENTION) firstAttention = row.view;
        }
        left.addView(list, new LinearLayout.LayoutParams(page.dp(440),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(left);
        // The fix for the focused check, then the way on, beside the list.
        LinearLayout right = page.column(Gravity.CENTER_HORIZONTAL);
        right.addView(info.card, new LinearLayout.LayoutParams(page.dp(330), 0, 1f));
        TextView next = page.pill("e.next", text(R.string.first_run_next), 0, this::next);
        TextView skip = page.pill("e.skip", text(R.string.first_run_skip), 0, this::skip);
        page.add(right, page.pills(next, skip), ViewGroup.LayoutParams.WRAP_CONTENT, 14);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        rightParams.leftMargin = page.dp(20);
        body.addView(right, rightParams);
        // The card follows focus; a refresh redraws the focused check's text in place.
        screen.bind(() -> info.show(info.check));
        screen.body = body;
        screen.focus = firstAttention != null ? firstAttention : next;
        info.show(Check.values()[0]);
        selectKey(screen, R.string.console_select_hint);
        skipKey(screen, R.string.first_run_next);
        backKey(screen, R.string.first_run_back);
        return screen;
    }

    /** E's right-hand card: how to fix the focused check, with a QR code for Steam's key. */
    private final class InfoCard {
        final LinearLayout card = page.card(22, 20);
        final TextView title = page.wrapped("", 16, FirstRunWizardPage.TEXT, true, 2);
        final TextView body = page.wrapped("", 13, FirstRunWizardPage.TEXT_SECONDARY, false, 10);
        final LinearLayout link = page.line(Gravity.CENTER_VERTICAL);
        Check check = Check.VIBEPOLLO;

        InfoCard() {
            card.addView(match(title));
            page.add(card, body, ViewGroup.LayoutParams.MATCH_PARENT, 10);
            link.addView(page.qr(STEAM_KEY_URL, 96));
            LinearLayout words = page.column(Gravity.START);
            words.addView(match(page.wrapped(STEAM_KEY_URL.replaceFirst("^https://", ""), 13,
                    FirstRunWizardPage.TEXT, true, 3)));
            page.add(words, page.wrapped(text(R.string.first_run_steam_key_caption), 11,
                    FirstRunWizardPage.TEXT_SECONDARY, false, 3),
                    ViewGroup.LayoutParams.MATCH_PARENT, 8);
            page.beside(link, words, 0, 14);
            page.add(card, link, ViewGroup.LayoutParams.MATCH_PARENT, 12);
        }

        void show(Check shown) {
            check = shown;
            title.setText(array(R.array.first_run_check_names)[shown.ordinal()]);
            String fix = array(R.array.first_run_check_fixes)[shown.ordinal()];
            if (shown == Check.EPIC && readiness().epicSignedIn == Boolean.TRUE
                    && capabilities != null && capabilities.setup != null
                    && !capabilities.setup.epicDisplayName.isEmpty()) {
                fix = text(R.string.first_run_epic_signed_in, capabilities.setup.epicDisplayName);
            }
            body.setText(fix);
            link.setVisibility(shown == Check.STEAM_LIBRARY ? View.VISIBLE : View.GONE);
        }
    }

    private String statusText(Check check, Status status) {
        if (status == Status.UNKNOWN) return text(R.string.first_run_check_unknown);
        if (status == Status.CHECKING) {
            return text(wake == WakePhase.SLEEPING ? R.string.first_run_wake_sleeping
                    : R.string.first_run_wake_waking);
        }
        int index = check.ordinal() * 2 + (status == Status.OK ? 0 : 1);
        return array(R.array.first_run_check_states)[index];
    }

    private void confirmWakeTest() {
        ComputerDetails computer = chosenHost();
        GatewayConnection gateway = connection();
        if (computer == null || gateway == null) return;
        if (!ConsoleHostPresentation.hasWakeAddress(computer)) {
            feedback.showMessage(text(R.string.first_run_wake_test_title),
                    text(R.string.wol_no_mac), text(R.string.console_close), null);
            return;
        }
        feedback.showConfirm(text(R.string.first_run_wake_test_title),
                text(R.string.first_run_wake_test_confirm, computer.name),
                text(android.R.string.cancel), text(R.string.first_run_wake_test_start), true,
                () -> {
                    wake = WakePhase.SLEEPING;
                    wakePhaseAt = SystemClock.elapsedRealtime();
                    host.sleep(computer, gateway);
                    render(true);
                    schedule();
                });
    }

    private void advanceWakeTest() {
        if (wake != WakePhase.SLEEPING && wake != WakePhase.WAKING) return;
        ComputerDetails computer = chosenHost();
        if (computer == null) return;
        boolean online = computer.state == ComputerDetails.State.ONLINE;
        WakePhase next = FirstRunPlan.advanceWake(wake,
                online, SystemClock.elapsedRealtime() - wakePhaseAt);
        if (next == wake) return;
        wake = next;
        wakePhaseAt = SystemClock.elapsedRealtime();
        if (next == WakePhase.WAKING) host.wake(computer);
        if (next == WakePhase.FAILED) {
            feedback.showMessage(text(R.string.first_run_wake_test_title),
                    text(R.string.first_run_wake_failed), text(R.string.console_close), null);
        }
        // The test is over: back on the checklist, on its wake row.
        if (next == WakePhase.OK || next == WakePhase.FAILED) {
            render(true);
            View row = page.find("e.check:" + Check.WAKE.name());
            if (row != null) row.requestFocus();
        }
    }

    private Screen wakeTest() {
        ComputerDetails computer = chosenHost();
        Screen screen = new Screen("E:wake");
        screen.title = text(R.string.first_run_wake_test_screen_title);
        screen.subtitle = text(R.string.first_run_wake_test_screen_subtitle);
        LinearLayout body = page.column(Gravity.CENTER_HORIZONTAL);
        FirstRunWizardPage.HostTile tile = page.hostTile(hostId == null ? "" : hostId);
        body.addView(tile, new LinearLayout.LayoutParams(page.dp(124), page.dp(124)));
        TextView name = page.text(computer == null ? "" : computer.name, 16,
                FirstRunWizardPage.TEXT, true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);
        page.add(body, name, 400, 14);
        TextView status = page.text("", 13, FirstRunWizardPage.TEXT_SECONDARY, false);
        status.setGravity(Gravity.CENTER);
        status.setMaxLines(1);
        page.add(body, status, 400, 6);
        LinearLayout card = page.card(18, 8);
        Row slept = page.row("e.wake.sleep", true, "", null, false, null);
        Row woke = page.row("e.wake.wake", true, "", null, false, null);
        card.addView(match(slept.view));
        card.addView(match(woke.view));
        page.add(body, card, 360, 18);
        screen.bind(() -> {
            long seconds = (SystemClock.elapsedRealtime() - wakePhaseAt) / 1000L;
            boolean sleeping = wake == WakePhase.SLEEPING;
            long limit = (sleeping ? FirstRunPlan.SLEEP_TIMEOUT_MS
                    : FirstRunPlan.WAKE_TIMEOUT_MS) / 1000L;
            float part = Math.min(1f, seconds / (float) limit);
            tile.setProgress(sleeping ? part / 2f : .5f + part / 2f);
            status.setText(text(sleeping ? R.string.first_run_wake_sleeping_seconds
                    : R.string.first_run_wake_waking_seconds, seconds));
            slept.mark.set(sleeping ? Mark.SPINNER : Mark.OK);
            slept.title.setText(sleeping ? text(R.string.first_run_wake_sleeping_seconds, seconds)
                    : text(R.string.first_run_wake_slept));
            woke.mark.set(sleeping ? Mark.EMPTY : Mark.SPINNER);
            woke.title.setText(sleeping ? text(R.string.first_run_wake_then)
                    : text(R.string.first_run_wake_waking_of, seconds, limit));
            woke.title.setTextColor(sleeping ? FirstRunWizardPage.HEADER : FirstRunWizardPage.TEXT);
        });
        screen.body = body;
        backKey(screen, R.string.first_run_cancel);
        return screen;
    }

    // F (mockup 06): network and picture.
    private boolean metered() {
        ConnectivityManager connectivity = (ConnectivityManager) activity.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        return connectivity != null && connectivity.isActiveNetworkMetered();
    }

    private void analyse() {
        ComputerDetails computer = chosenHost();
        if (computer == null || computer.activeAddress == null) {
            analysisError = text(R.string.console_autopilot_unavailable);
            return;
        }
        GatewayConnection gateway = connection();
        String uniqueId = host.uniqueId();
        Display display = activity.getWindowManager().getDefaultDisplay();
        String glRenderer = GlPreferences.readPreferences(activity).glRenderer;
        ComputerDetails target = new ComputerDetails(computer);
        int token = generation;
        analysing = true;
        executor.execute(() -> {
            StreamingAutopilotController.Analysis result = null;
            String error = null;
            try {
                NvHTTP http = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(target),
                        target.httpsPort, uniqueId, target.serverCert,
                        PlatformBinding.getCryptoProvider(activity));
                result = StreamingAutopilotController.analyze(activity, display, glRenderer,
                        http, http.getServerInfo(true), gateway);
            } catch (Exception failure) {
                error = failure.getMessage() == null
                        ? text(R.string.console_autopilot_error_unknown) : failure.getMessage();
            }
            StreamingAutopilotController.Analysis done = result;
            String failed = error;
            main.post(() -> {
                if (token != generation) return;
                analysing = false;
                analysis = done;
                analysisError = failed;
                if (page.onScreen()) render(false);
            });
        });
    }

    private Screen network() {
        String state = analysing ? "measuring" : analysis == null && analysisError == null
                ? "metered" : analysis == null || !analysis.recommendation.isPresent()
                ? "failed" : "result:" + applied;
        Screen screen = new Screen("F:" + state);
        screen.title = text(R.string.first_run_network_title);
        screen.subtitle = text(R.string.first_run_network_subtitle);
        TextView skip = page.pill("f.skip", text(R.string.first_run_skip), 0, this::skip);
        TextView primary;
        LinearLayout card;
        if (analysing) {
            card = statusCard(Mark.SPINNER, text(R.string.first_run_network_measuring));
            primary = null;
        } else if (analysis == null && analysisError == null) {
            card = statusCard(Mark.EMPTY, text(R.string.first_run_network_metered));
            primary = page.pill("f.primary", text(R.string.console_autopilot_measure), 0, () -> {
                analyse();
                render(true);
            });
        } else if (analysis == null || !analysis.recommendation.isPresent()) {
            card = statusCard(Mark.ATTENTION, analysisError != null
                    ? text(R.string.console_autopilot_error_details, analysisError)
                    : text(R.string.console_autopilot_no_recommendation_title));
            primary = page.pill("f.primary", text(R.string.first_run_retry), 0, () -> {
                analyse();
                render(true);
            });
        } else {
            card = resultCard(analysis);
            primary = applied ? page.pill("f.primary", text(R.string.first_run_next), 0,
                    this::next) : page.pill("f.primary", text(R.string.first_run_apply), 0, () -> {
                // First run applies the recommendation; Settings › Stream offers the others.
                StreamingAutopilotController.applyGlobal(activity, analysis.variants.get(0));
                applied = true;
                render(true);
            });
            if (applied) skip = null;
        }
        LinearLayout body = page.column(Gravity.CENTER_HORIZONTAL);
        page.add(body, card, 600, 0);
        page.add(body, page.pills(primary, skip), ViewGroup.LayoutParams.WRAP_CONTENT, 26);
        screen.body = body;
        screen.focus = primary != null ? primary : skip;
        selectKey(screen, R.string.console_select_hint);
        backKey(screen, R.string.first_run_back);
        return screen;
    }

    private LinearLayout statusCard(int markKind, String message) {
        LinearLayout card = page.card(28, 26);
        LinearLayout line = page.line(Gravity.CENTER_VERTICAL);
        Mark mark = new Mark(page);
        mark.set(markKind);
        line.addView(mark, new LinearLayout.LayoutParams(page.dp(20), page.dp(20)));
        page.beside(line, page.wrapped(message, 14, FirstRunWizardPage.TEXT, false, 4), 0, 16);
        card.addView(line);
        return card;
    }

    private LinearLayout resultCard(StreamingAutopilotController.Analysis result) {
        StreamingAutopilot.Recommendation recommendation = result.recommendation.get();
        LinearLayout card = page.card(24, 26);
        LinearLayout columns = page.line(Gravity.TOP);
        int frequency = wifiFrequencyMhz();
        NetworkLinkAdvice.Link link = NetworkLinkAdvice.classify(wired(), frequency);
        String band = NetworkLinkAdvice.bandGhz(frequency);
        long ethernetMbps = link == NetworkLinkAdvice.Link.WIRED ? ethernetSpeedMbps() : -1;
        String connection = link == NetworkLinkAdvice.Link.WIRED
                ? cableLabel(ethernetMbps) : band == null
                ? text(R.string.first_run_network_wireless)
                : text(R.string.first_run_network_wifi_band, band);
        String speed = result.goodputKbps.isPresent()
                ? text(R.string.first_run_network_speed, result.goodputKbps.getAsLong() / 1000d)
                : text(R.string.first_run_network_unmeasured);
        stat(columns, text(R.string.first_run_network_connection), connection, speed,
                FirstRunWizardPage.TEXT);
        StreamingAutopilot.Capabilities tv = result.clientCapabilities;
        stat(columns, text(R.string.first_run_network_tv), resolution(tv.maxHeight),
                text(R.string.first_run_network_fps_max, tv.maxFps), FirstRunWizardPage.TEXT);
        stat(columns, text(R.string.first_run_network_proposed),
                text(R.string.first_run_network_mode, resolution(recommendation.height),
                        recommendation.fps),
                text(R.string.first_run_network_bitrate, recommendation.bitrateKbps / 1000d),
                FirstRunWizardPage.ACCENT);
        card.addView(columns);
        View divider = new View(activity);
        divider.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, page.dp(.5f)));
        dividerParams.topMargin = page.dp(22);
        card.addView(divider, dividerParams);
        boolean fasterLink = NetworkLinkAdvice.recommendFasterLink(link,
                result.goodputKbps.isPresent() ? result.goodputKbps.getAsLong() : -1,
                ethernetMbps);
        String adviceText = fasterLink
                ? link != NetworkLinkAdvice.Link.WIRED ? text(R.string.first_run_network_wifi_faster)
                : ethernetMbps > 0 ? text(R.string.first_run_network_wired_slow_speed, ethernetMbps)
                : text(R.string.first_run_network_wired_slow)
                : applied ? text(R.string.console_autopilot_applied) : null;
        if (adviceText != null) {
            TextView advice = page.wrapped(adviceText, 13, FirstRunWizardPage.TEXT_SECONDARY,
                    false, 2);
            advice.setGravity(Gravity.CENTER);
            page.add(card, advice, ViewGroup.LayoutParams.MATCH_PARENT, 18);
        }
        return card;
    }

    private void stat(LinearLayout columns, String label, String value, String detail,
                      int valueColor) {
        LinearLayout column = page.column(Gravity.CENTER_HORIZONTAL);
        TextView heading = page.text(label.toUpperCase(Locale.getDefault()), 12,
                FirstRunWizardPage.HEADER, true);
        heading.setLetterSpacing(.1f);
        heading.setMaxLines(1);
        heading.setGravity(Gravity.CENTER);
        column.addView(match(heading));
        TextView big = page.text(value, 22, valueColor, true);
        big.setMaxLines(1);
        big.setGravity(Gravity.CENTER);
        page.add(column, big, ViewGroup.LayoutParams.MATCH_PARENT, 16);
        TextView small = page.wrapped(detail, 12, FirstRunWizardPage.TEXT_SECONDARY, false, 2);
        small.setGravity(Gravity.CENTER);
        page.add(column, small, ViewGroup.LayoutParams.MATCH_PARENT, 10);
        columns.addView(column, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private static String resolution(int height) {
        if (height >= 2160) return "4K";
        if (height >= 1440) return "1440p";
        return height + "p";
    }

    private String cableLabel(long mbps) {
        if (mbps <= 0) return text(R.string.first_run_network_cable);
        return text(R.string.first_run_network_cable_speed, mbps >= 1000
                ? text(R.string.first_run_network_gbps, mbps % 1000 == 0
                        ? Long.toString(mbps / 1000)
                        : String.format(Locale.getDefault(), "%.1f", mbps / 1000d))
                : text(R.string.first_run_network_mbps, mbps));
    }

    /** Negotiated Ethernet link speed in Mb/s from sysfs; -1 when unreadable. */
    private long ethernetSpeedMbps() {
        ConnectivityManager connectivity = (ConnectivityManager) activity.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        Network active = connectivity == null ? null : connectivity.getActiveNetwork();
        LinkProperties properties = active == null ? null : connectivity.getLinkProperties(active);
        String iface = properties == null ? null : properties.getInterfaceName();
        if (iface == null) return -1;
        try {
            return Long.parseLong(new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get("/sys/class/net/" + iface + "/speed")),
                    java.nio.charset.StandardCharsets.UTF_8).trim());
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    private boolean wired() {
        ConnectivityManager connectivity = (ConnectivityManager) activity.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities network = connectivity == null ? null
                : connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        return network != null && network.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    /** Frequency needs no location permission (only the SSID is redacted); -1 if unknown. */
    @SuppressWarnings("deprecation")
    private int wifiFrequencyMhz() {
        WifiManager wifi = (WifiManager) activity.getApplicationContext().getSystemService(
                Context.WIFI_SERVICE);
        WifiInfo info = wifi == null ? null : wifi.getConnectionInfo();
        return info == null ? -1 : info.getFrequency();
    }

    // G (mockup 07): family.
    private Screen family() {
        HostGatewayClient.IntegrationProfile profile = chosenProfile();
        ComputerDetails computer = chosenHost();
        GatewayConnection gateway = connection();
        boolean canSetPin = profile != null && computer != null && gateway != null;
        boolean pinSet = profile != null && profile.pinRequired;
        Screen screen = new Screen("G:" + canSetPin + ":" + pinSet);
        screen.title = text(R.string.first_run_family_title);
        screen.subtitle = text(R.string.first_run_family_subtitle);
        String name = profile == null ? "" : ConsoleProfileSelectView.displayName(activity, profile);
        Runnable setPin = canSetPin && !pinSet ? () -> host.setAdultPin(computer, profile,
                gateway.forProfile(profile.id), () -> {
                    page.show();
                    refresh();
                    View again = page.find("g.pin");
                    if (again != null) again.requestFocus();
                }) : null;
        View pin = page.choiceCard("g.pin", pinSet ? R.drawable.ic_console_check_on
                        : R.drawable.ic_console_lock,
                pinSet ? text(R.string.first_run_family_pin_done) : name.isEmpty()
                        ? text(R.string.console_adult_pin_set)
                        : text(R.string.first_run_family_pin_for, name),
                text(pinSet ? R.string.first_run_family_pin_done_detail
                        : R.string.first_run_family_pin_detail), setPin);
        // A child is added in the parent panel, behind the parent's PIN: set one first.
        Runnable addChild = !canSetPin ? null : () -> {
            if (chosenProfile() != null && chosenProfile().pinRequired) {
                parentPanel = true;
                next();
                return;
            }
            host.setAdultPin(computer, profile, gateway.forProfile(profile.id), () -> {
                page.show();
                refresh();
                HostGatewayClient.IntegrationProfile now = chosenProfile();
                if (now != null && now.pinRequired) {
                    parentPanel = true;
                    next();
                } else {
                    View again = page.find("g.child");
                    if (again != null) again.requestFocus();
                }
            });
        };
        View child = page.choiceCard("g.child", R.drawable.ic_console_plus,
                text(R.string.first_run_family_child), text(R.string.first_run_family_child_detail),
                addChild);
        View skip = page.choiceCard("g.skip", R.drawable.ic_console_chevron,
                text(R.string.first_run_skip), text(R.string.first_run_family_skip_detail),
                this::skip);
        LinearLayout body = page.line(Gravity.TOP);
        for (View card : new View[]{pin, child, skip}) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(page.dp(232),
                    page.dp(196));
            params.leftMargin = body.getChildCount() == 0 ? 0 : page.dp(4);
            body.addView(card, params);
        }
        screen.body = body;
        screen.focus = addChild != null ? child : skip;
        selectKey(screen, R.string.console_select_hint);
        backKey(screen, R.string.first_run_back);
        return screen;
    }

    // H (mockup 08): extras, only what this TV and host offer.
    private void proposeAutoLogin() {
        ComputerDetails computer = chosenHost();
        if (computer != null && host.autoLoginHost().isEmpty() && host.hosts().size() == 1) {
            host.setAutoLoginHost(computer.uuid);
        }
    }

    private Screen extras() {
        ComputerDetails computer = chosenHost();
        HostGatewayClient.IntegrationProfile profile = chosenProfile();
        String uuid = computer == null ? null : computer.uuid;
        String profileName = profile == null ? ""
                : ConsoleProfileSelectView.displayName(activity, profile);
        boolean tile = profile != null && !profile.isChild()
                && activity.getPackageManager().hasSystemFeature("com.google.android.tv");
        boolean discord = profile != null && uuid != null && capabilities != null
                && capabilities.discord && store.discordEligible(uuid, profile.id);
        Screen screen = new Screen("H:" + uuid + ":" + (profile == null ? "" : profile.id) + ":"
                + (profile != null && profile.pinRequired) + ":" + tile + ":" + discord);
        screen.title = text(R.string.first_run_extras_title);
        screen.subtitle = text(R.string.first_run_extras_subtitle);
        LinearLayout body = page.column(Gravity.CENTER_HORIZONTAL);
        LinearLayout card = page.card(12, 0);
        if (uuid != null) {
            toggle(screen, card, "h.host", text(R.string.first_run_extra_host),
                    text(R.string.first_run_extra_host_detail, computer.name),
                    () -> uuid.equals(host.autoLoginHost()),
                    on -> host.setAutoLoginHost(on ? uuid : ""));
            if (profile != null) {
                if (profile.pinRequired) {
                    Row row = page.row("h.profile", false, text(R.string.first_run_auto_profile),
                            text(R.string.first_run_auto_profile_pin), false, null);
                    row.view.setAlpha(.5f);
                    card.addView(match(row.view));
                } else {
                    toggle(screen, card, "h.profile", text(R.string.first_run_auto_profile),
                            text(R.string.first_run_auto_profile_detail, profileName),
                            () -> profile.id.equals(store.automaticIntegrationProfileId(uuid)),
                            on -> store.setAutomaticIntegrationProfileId(uuid,
                                    on ? profile.id : ""));
                }
            }
            toggle(screen, card, "h.warm", text(R.string.first_run_extra_warm),
                    text(R.string.first_run_extra_warm_detail),
                    () -> HostAutoWarmUpPreferences.isEnabled(console, uuid),
                    on -> HostAutoWarmUpPreferences.setEnabled(console, uuid, on));
            toggle(screen, card, "h.freeze", text(R.string.first_run_extra_freeze),
                    null,
                    () -> QuickResumePreferences.freezesOnSwitch(console, uuid),
                    on -> QuickResumePreferences.setFreezesOnSwitch(console, uuid, on));
            if (tile) {
                toggle(screen, card, "h.tile", text(R.string.first_run_extra_tile),
                        text(R.string.first_run_extra_tile_detail),
                        () -> WatchNextTile.consented(console, uuid, profile.id),
                        on -> WatchNextTile.setConsent(console, uuid, profile.id, on));
            }
            if (discord) {
                toggle(screen, card, "h.discord", text(R.string.first_run_discord),
                        text(R.string.first_run_discord_detail),
                        () -> store.isDiscordEnabled(uuid, profile.id),
                        on -> store.setDiscordEnabled(uuid, profile.id, on));
            }
        }
        toggle(screen, card, "h.sounds", text(R.string.first_run_extra_sounds),
                text(R.string.first_run_extra_sounds_detail), host::menuSounds, host::setMenuSounds);
        page.add(body, card, 560, 0);
        TextView next = page.pill("h.next", text(R.string.first_run_next), 0, this::next);
        TextView skip = page.pill("h.skip", text(R.string.first_run_skip), 0, this::skip);
        page.add(body, page.pills(next, skip), ViewGroup.LayoutParams.WRAP_CONTENT, 0);
        screen.body = body;
        screen.focus = card.getChildAt(0);
        selectKey(screen, R.string.first_run_toggle);
        skipKey(screen, R.string.first_run_next);
        backKey(screen, R.string.first_run_back);
        return screen;
    }

    private void toggle(Screen screen, LinearLayout card, String key, String title, String detail,
                        BooleanSupplier value, Consumer<Boolean> set) {
        Row[] row = new Row[1];
        row[0] = page.row(key, false, title, detail, true, () -> {
            set.accept(!value.getAsBoolean());
            row[0].toggle.setOn(value.getAsBoolean());
        });
        screen.bind(() -> row[0].toggle.setOn(value.getAsBoolean()));
        card.addView(match(row[0].view));
    }

    // H2 (mockup 08b): controls, drawn with the last-used pad's buttons.
    private Screen controls() {
        boolean playStation = host.playStationButtons();
        Screen screen = new Screen("H2:" + playStation + ":" + padLegend);
        screen.title = text(R.string.first_run_controls_title);
        screen.subtitle = text(padLegend ? R.string.first_run_controls_pad
                : R.string.first_run_controls_remote);
        LinearLayout body = page.column(Gravity.CENTER_HORIZONTAL);
        LinearLayout card = page.card(26, 14);
        String home = playStation ? "PS" : "Xbox";
        controlRow(card, page.keyChip(home), text(R.string.first_run_controls_home_title, home),
                text(R.string.first_run_controls_home));
        controlRow(card, page.controllerGlyph(ControllerGlyphs.text(playStation,
                        ControllerGlyphs.Button.MENU)), text(R.string.first_run_controls_menu_title),
                text(R.string.first_run_controls_menu));
        controlRow(card, page.icon(R.drawable.ic_console_back, FirstRunWizardPage.TEXT),
                text(R.string.first_run_controls_remote_title),
                text(R.string.first_run_controls_remote_menu));
        controlRow(card, page.controllerGlyph(ControllerGlyphs.text(playStation,
                        ControllerGlyphs.Button.LEFT_BUMPER)),
                text(playStation ? R.string.first_run_controls_tabs_ps
                        : R.string.first_run_controls_tabs_xbox),
                text(R.string.first_run_controls_tabs));
        controlRow(card, page.icon(R.drawable.ic_console_options, FirstRunWizardPage.TEXT),
                text(R.string.first_run_controls_options_title),
                text(R.string.first_run_controls_options));
        page.add(body, card, 640, 0);
        TextView next = page.pill("h2.next", text(R.string.first_run_next), 0, this::next);
        page.add(body, page.pills(next), ViewGroup.LayoutParams.WRAP_CONTENT, 14);
        screen.body = body;
        screen.focus = next;
        selectKey(screen, R.string.first_run_next);
        backKey(screen, R.string.first_run_back);
        return screen;
    }

    private void controlRow(LinearLayout card, View key, String title, String detail) {
        LinearLayout line = page.line(Gravity.CENTER_VERTICAL);
        FrameLayout slot = new FrameLayout(activity);
        boolean icon = !(key instanceof TextView);
        slot.addView(key, new FrameLayout.LayoutParams(
                icon ? page.dp(18) : ViewGroup.LayoutParams.WRAP_CONTENT,
                icon ? page.dp(18) : ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        line.addView(slot, new LinearLayout.LayoutParams(page.dp(52), page.dp(30)));
        LinearLayout words = page.column(Gravity.START);
        words.addView(match(page.wrapped(title, 15, FirstRunWizardPage.TEXT, true, 1)));
        page.add(words, page.wrapped(detail, 12, FirstRunWizardPage.TEXT_SECONDARY, false, 2),
                ViewGroup.LayoutParams.MATCH_PARENT, 3);
        page.beside(line, words, 0, 18);
        page.add(card, line, ViewGroup.LayoutParams.MATCH_PARENT, card.getChildCount() == 0 ? 0 : 10);
    }

    // H3: what MoonWaker can do (the showcase opens over this page).
    private Screen features() {
        Screen screen = new Screen("H3");
        screen.title = text(R.string.console_options_features);
        screen.subtitle = text(R.string.feature_showcase_subtitle);
        TextView open = page.pill("h3.open", text(R.string.first_run_features_open), 0,
                () -> host.showFeatures(capabilities, eyebrow(), this::next));
        TextView next = page.pill("h3.next", text(R.string.first_run_next), 0, this::next);
        TextView skip = page.pill("h3.skip", text(R.string.first_run_skip), 0, this::skip);
        screen.body = page.pills(open, next, skip);
        screen.focus = open;
        selectKey(screen, R.string.console_select_hint);
        skipKey(screen, R.string.first_run_skip);
        backKey(screen, R.string.first_run_back);
        return screen;
    }

    // I (mockup 09): done.
    private Screen done() {
        ComputerDetails computer = chosenHost();
        HostGatewayClient.IntegrationProfile profile = chosenProfile();
        List<Step> open = todo(hostId);
        Screen screen = new Screen("I:" + open + ":" + parentPanel);
        screen.title = profile != null
                ? text(R.string.first_run_done_title_named,
                ConsoleProfileSelectView.displayName(activity, profile))
                : text(R.string.first_run_done_title);
        PreferenceConfiguration stream = PreferenceConfiguration.readPreferences(activity);
        screen.subtitle = computer != null
                ? text(R.string.first_run_done_subtitle, computer.name, resolution(stream.height),
                stream.fps)
                : text(R.string.first_run_done_no_host);
        LinearLayout body = page.column(Gravity.CENTER_HORIZONTAL);
        if (open.size() < 4) {
            body.addView(page.bigCheck(), new LinearLayout.LayoutParams(page.dp(54),
                    page.dp(54)));
        }
        if (!open.isEmpty()) {
            LinearLayout card = page.card(14, 14);
            TextView heading = page.text(text(R.string.first_run_todo_title)
                    .toUpperCase(Locale.getDefault()), 12, FirstRunWizardPage.HEADER, true);
            heading.setLetterSpacing(.1f);
            heading.setPadding(page.dp(12), 0, page.dp(12), 0);
            card.addView(match(heading));
            for (Step item : open) {
                Row row = page.row("i.todo:" + item.name(), true, stepName(item), null, false,
                        () -> go(item));
                row.mark.set(item == Step.READINESS ? Mark.ATTENTION : Mark.EMPTY);
                row.view.setMinimumHeight(page.dp(32));
                page.add(card, row.view, ViewGroup.LayoutParams.MATCH_PARENT, 4);
            }
            TextView where = page.wrapped(text(R.string.first_run_todo_where), 12,
                    FirstRunWizardPage.TEXT_SECONDARY, false, 2);
            where.setPadding(page.dp(12), 0, page.dp(12), 0);
            page.add(card, where, ViewGroup.LayoutParams.MATCH_PARENT, 8);
            page.add(body, card, 380, body.getChildCount() == 0 ? 0 : 16);
        }
        TextView play = page.pill("i.play", text(parentPanel ? R.string.first_run_done_parent_panel
                : R.string.first_run_done_play), R.drawable.ic_console_play, this::finish);
        page.add(body, page.pills(play), ViewGroup.LayoutParams.WRAP_CONTENT, 18);
        screen.body = body;
        screen.focus = play;
        selectKey(screen, R.string.console_select_hint);
        backKey(screen, R.string.first_run_back);
        return screen;
    }

    private <T extends Enum<T>> T stored(String key, Class<T> type) {
        String value = state.getString(key, "");
        if (value == null || value.isEmpty()) return null;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** A step's name, e.g. for a "To finish" row in Settings. */
    String stepName(Step item) {
        return array(R.array.first_run_step_names)[item.ordinal()];
    }
}
