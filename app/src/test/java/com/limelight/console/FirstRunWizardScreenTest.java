package com.limelight.console;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Layout;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.limelight.gateway.GatewayConnection;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.stream.StreamingAutopilot;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The first-run wizard drawn at 1920x1080 (xhdpi TV) in Polish, English and German: every text
 * fits its frame without ellipsis or clipping, the body stays between header and legend, and
 * the renders land in build/first-run-shots for comparison with the mockups. Also: Back from
 * every step, resume after recreation, refresh in place without moving focus.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Robolectric's Conscrypt provider would stay JVM-wide and stall later plain TLS tests.
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi",
        shadows = FirstRunWizardScreenTest.NoNativeBridge.class,
        instrumentedPackages = "com.limelight.nvstream.jni")
public class FirstRunWizardScreenTest {
    /** The stream settings read MoonBridge's constants; its native library is not loaded. */
    @Implements(MoonBridge.class)
    public static class NoNativeBridge {
        @Implementation
        protected static void __staticInitializer__() {
        }
    }

    private static final String HOST = "host-1";
    private static final String OTHER = "host-2";
    private static final File SHOTS = new File("build/first-run-shots");

    /** The console's side, faked: two hosts, a pad, nothing ever sent anywhere. */
    private static final class FakeHost implements FirstRunWizard.Host {
        final Map<String, ComputerDetails> hosts = new LinkedHashMap<>();
        boolean pad;
        boolean playStation = true;
        String finishedHost = "unset";
        int addByAddress;

        @Override public Collection<ComputerDetails> hosts() { return hosts.values(); }
        @Override public ComputerDetails host(String uuid) { return hosts.get(uuid); }
        @Override public void addHostByAddress() { addByAddress++; }
        @Override public void wake(ComputerDetails host) { }
        @Override public void sleep(ComputerDetails host, GatewayConnection connection) { }
        @Override public void createAdultProfile(ComputerDetails host,
                HostGatewayClient.IntegrationProfile actor, Consumer<String> done) { }
        @Override public void setAdultPin(ComputerDetails host,
                HostGatewayClient.IntegrationProfile profile, GatewayConnection connection,
                Runnable done) { }
        @Override public void saveProfiles(String hostId,
                HostGatewayClient.IntegrationProfiles profiles) { }
        @Override public void paired(String hostId) { }
        @Override public void pairController() { }
        @Override public void todoChanged() { }
        @Override public String uniqueId() { return "tv"; }
        @Override public String autoLoginHost() { return HOST; }
        @Override public void setAutoLoginHost(String hostId) { }
        @Override public boolean menuSounds() { return true; }
        @Override public void setMenuSounds(boolean on) { }
        @Override public boolean playStationButtons() { return playStation; }
        @Override public boolean controllerPresent() { return pad; }
        @Override public boolean controllerInput() { return pad; }
        @Override public void showFeatures(HostGatewayClient.Capabilities capabilities,
                String eyebrow, Runnable onNext) { }
        @Override public void finished(String hostId, String profileId, boolean parentPanel) {
            finishedHost = hostId;
        }
    }

    /** Drops every task: nothing reaches a network in a test. */
    private static final class NoExecutor extends AbstractExecutorService {
        @Override public void execute(Runnable command) { }
        @Override public void shutdown() { }
        @Override public List<Runnable> shutdownNow() { return Collections.emptyList(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }

    private Activity activity;
    private FakeHost host;
    private HostGatewayStore store;
    private FirstRunWizard wizard;
    private ConsoleEntryShell shell;
    private final List<String> problems = new ArrayList<>();

    private void start(String language) {
        RuntimeEnvironment.setQualifiers(language + "-w960dp-h540dp-land-television-xhdpi");
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        shell = new ConsoleEntryShell(activity);
        shell.setReducedMotion(true);
        root.addView(shell);
        activity.setContentView(root);
        host = new FakeHost();
        host.hosts.put(HOST, computer(HOST, "Komputer Ani"));
        store = new HostGatewayStore(activity);
        store.save(HOST, new GatewayConnection("https://192.168.1.20:47984", "token",
                "ab".repeat(32), "ania"));
        store.saveProfiles(HOST, new HostGatewayClient.IntegrationProfiles(Arrays.asList(
                profile("ania", "Ania", false), profile("tomek", "Tomek", false)), "ania"));
        wizard = newWizard();
    }

    private FirstRunWizard newWizard() {
        SharedPreferences console = activity.getSharedPreferences("console", Context.MODE_PRIVATE);
        return new FirstRunWizard(activity, host, console, store, new HostGatewayClient(), null,
                new ConsoleUiFeedback(activity, (FrameLayout) shell.getParent(), null, true),
                new NoExecutor(), new Handler(Looper.getMainLooper()), shell, true);
    }

    private static ComputerDetails computer(String uuid, String name) {
        ComputerDetails computer = new ComputerDetails();
        computer.uuid = uuid;
        computer.name = name;
        computer.state = ComputerDetails.State.ONLINE;
        computer.activeAddress = new ComputerDetails.AddressTuple("192.168.1.20", 47989);
        computer.pairState = PairingManager.PairState.PAIRED;
        computer.macAddress = "00:11:22:33:44:55";
        return computer;
    }

    private static HostGatewayClient.IntegrationProfile profile(String id, String name,
                                                                boolean pin) {
        return new HostGatewayClient.IntegrationProfile(id, name, false, false, false, true,
                true, true, true, true, "ready", "ready", pin, true);
    }

    // --- Renders ----------------------------------------------------------------------------

    @Test
    public void everyStepFitsAndMatchesTheMockupLayoutInEachLanguage() throws Exception {
        for (String language : new String[]{"pl", "en", "de"}) {
            start(language);
            renderAll(language);
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    private void renderAll(String language) throws Exception {
        FirstRunPlan.Entry first = FirstRunPlan.Entry.FIRST_RUN;
        wizard.openAt(first, null, FirstRunPlan.Step.WELCOME);
        shot(language, "01-witaj");

        host.hosts.clear();
        wizard.openAt(first, null, FirstRunPlan.Step.INSTALL);
        shot(language, "02-komputer");
        host.hosts.put(HOST, computer(HOST, "Komputer Ani"));
        wizard.refresh();
        shot(language, "02-komputer-znaleziony");
        key(KeyEvent.KEYCODE_BUTTON_Y);
        shot(language, "02b-nie-widze-komputera");

        ComputerDetails fresh = computer(OTHER, "Komputer Ani");
        fresh.pairState = PairingManager.PairState.NOT_PAIRED;
        host.hosts.clear();
        host.hosts.put(OTHER, fresh);
        wizard.openAt(first, OTHER, FirstRunPlan.Step.PAIR);
        key(KeyEvent.KEYCODE_4);
        key(KeyEvent.KEYCODE_8);
        key(KeyEvent.KEYCODE_2);
        shot(language, "03-parowanie-kod");
        set("pairing", enumValue("Pairing", "BUSY"));
        set("pairingStage", 2);
        wizard.refresh();
        shot(language, "03b-parowanie-postep");
        set("pairing", enumValue("Pairing", "FAILED"));
        set("pairingFailure", failure("VIBEPOLLO",
                "connection refused 127.0.0.1:47990"));
        wizard.refresh();
        shot(language, "03c-parowanie-blad");
        host.hosts.put(HOST, computer(HOST, "Komputer Tomka"));
        set("pairing", enumValue("Pairing", "IDLE"));
        set("hostId", null);
        set("chooseHost", true);
        wizard.refresh();
        shot(language, "03d-wybor-komputera");
        host.hosts.remove(OTHER);

        wizard.openAt(first, HOST, FirstRunPlan.Step.PAIR);
        shot(language, "03e-sparowano");

        wizard.openAt(first, HOST, FirstRunPlan.Step.PROFILE);
        shot(language, "04-profil");

        wizard.openAt(first, HOST, FirstRunPlan.Step.READINESS);
        set("capabilities", capabilities());
        wizard.refresh();
        shot(language, "05-gotowosc");
        set("wake", FirstRunPlan.WakePhase.WAKING);
        set("wakePhaseAt", SystemClock.elapsedRealtime() - 12_000L);
        wizard.refresh();
        shot(language, "05b-test-budzenia");

        wizard.openAt(first, HOST, FirstRunPlan.Step.NETWORK);
        set("capabilities", capabilities());
        wizard.refresh();
        set("analysing", false);
        set("analysisError", null);
        set("analysis", analysis());
        wizard.refresh();
        shot(language, "06-obraz");
        set("analysis", null);
        set("analysing", true);
        wizard.refresh();
        shot(language, "06b-obraz-pomiar");

        wizard.openAt(first, HOST, FirstRunPlan.Step.FAMILY);
        set("capabilities", capabilities());
        wizard.refresh();
        shot(language, "07-rodzina");

        wizard.openAt(first, HOST, FirstRunPlan.Step.EXTRAS);
        set("capabilities", capabilities());
        wizard.refresh();
        shot(language, "08-dodatki");

        host.pad = true;
        wizard.openAt(first, HOST, FirstRunPlan.Step.CONTROLS);
        set("capabilities", capabilities());
        wizard.refresh();
        shot(language, "08b-sterowanie-ps");
        host.playStation = false;
        wizard.refresh();
        shot(language, "08b-sterowanie-xbox");
        host.pad = false;
        host.playStation = true;
        wizard.refresh();
        shot(language, "08b-sterowanie-pilot");

        activity.getSharedPreferences(FirstRunWizard.PREFS, Context.MODE_PRIVATE).edit()
                .putStringSet("todo." + HOST, new java.util.HashSet<>(Arrays.asList(
                        "READINESS", "EXTRAS"))).apply();
        wizard.openAt(first, HOST, FirstRunPlan.Step.DONE);
        set("capabilities", capabilities());
        wizard.refresh();
        shot(language, "09-gotowe");

        wizard.openAt(FirstRunPlan.Entry.SETTINGS, HOST, FirstRunPlan.Step.WELCOME);
        host.pad = true;
        wizard.refresh();
        shot(language, "10-ustawienia-ponownie");
        host.pad = false;
    }

    private void shot(String language, String name) throws Exception {
        View root = activity.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1920, 1080);
        Bitmap bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        File dir = new File(SHOTS, language);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        checkFits(language + "/" + name, root);
    }

    /** No ellipsis, no line past maxLines, no text past its view, the body inside its band. */
    private void checkFits(String where, View root) {
        Rect screen = new Rect(0, 0, 1920, 1080);
        walk(root, view -> {
            if (!(view instanceof TextView) || !view.isShown()) return;
            TextView text = (TextView) view;
            if (text.getText().length() == 0) return;
            Layout layout = text.getLayout();
            String label = where + " \"" + text.getText() + "\"";
            if (layout == null) return;
            int lines = layout.getLineCount();
            for (int line = 0; line < lines; line++) {
                if (layout.getEllipsisCount(line) > 0) problems.add(label + ": ellipsized");
                if (layout.getLineMax(line) > layout.getWidth() + 1) {
                    problems.add(label + ": wider than its view");
                }
            }
            if (text.getMaxLines() > 0 && lines > text.getMaxLines()) {
                problems.add(label + ": " + lines + " lines, max " + text.getMaxLines());
            }
            int shown = Math.min(lines, Math.max(1, text.getMaxLines() > 0 ? text.getMaxLines()
                    : lines));
            int room = text.getHeight() - text.getTotalPaddingTop() - text.getTotalPaddingBottom();
            if (layout.getLineBottom(shown - 1) - layout.getLineTop(0) > room + 2) {
                problems.add(label + ": taller than its view");
            }
            Rect bounds = bounds(text);
            if (!screen.contains(bounds)) problems.add(label + ": off screen " + bounds);
            for (Object parent = text.getParent(); parent instanceof ViewGroup;
                 parent = ((ViewGroup) parent).getParent()) {
                ViewGroup group = (ViewGroup) parent;
                if (group.getClipChildren() && group.getWidth() > 0
                        && !contains(bounds(group), bounds)) {
                    problems.add(label + ": clipped by " + group.getClass().getSimpleName());
                    break;
                }
            }
        });
        View body = bodyOf();
        if (body != null) {
            Rect band = bounds((View) body.getParent());
            walk(body, part -> {
                if (!part.isShown() || part.getWidth() == 0) return;
                Rect content = bounds(part);
                if (content.top < band.top - 2 || content.bottom > band.bottom + 2) {
                    problems.add(where + ": " + part.getClass().getSimpleName() + " "
                            + part.getTag() + " " + content + " outside the body band " + band);
                }
            });
        }
    }

    private static boolean contains(Rect outer, Rect inner) {
        return inner.left >= outer.left - 1 && inner.top >= outer.top - 1
                && inner.right <= outer.right + 1 && inner.bottom <= outer.bottom + 1;
    }

    private static Rect bounds(View view) {
        int[] at = new int[2];
        view.getLocationInWindow(at);
        return new Rect(at[0], at[1], at[0] + view.getWidth(), at[1] + view.getHeight());
    }

    private static void walk(View view, Consumer<View> visit) {
        visit.accept(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                walk(group.getChildAt(index), visit);
            }
        }
    }

    // --- Behaviour --------------------------------------------------------------------------

    @Test
    public void backFromEveryStepGoesToThePreviousStepAndFromTheFirstClosesTheWizard()
            throws Exception {
        start("en");
        set("capabilities", capabilities());
        List<FirstRunPlan.Step> steps = FirstRunPlan.steps(FirstRunPlan.Entry.FIRST_RUN,
                new FirstRunPlan.Facts(true, true, false, true, true));
        for (int index = steps.size() - 1; index > 0; index--) {
            wizard.openAt(FirstRunPlan.Entry.FIRST_RUN, HOST, steps.get(index));
            set("capabilities", capabilities());
            key(KeyEvent.KEYCODE_BACK);
            assertEquals("Back from " + steps.get(index), steps.get(index - 1), get("step"));
        }
        wizard.openAt(FirstRunPlan.Entry.FIRST_RUN, HOST, FirstRunPlan.Step.WELCOME);
        key(KeyEvent.KEYCODE_BACK);
        assertTrue(!wizard.isShowing());
        assertEquals(HOST, host.finishedHost);
    }

    @Test
    public void backClosesWhatIsOpenOverAStepBeforeLeavingIt() throws Exception {
        start("en");
        wizard.openAt(FirstRunPlan.Entry.FIRST_RUN, null, FirstRunPlan.Step.INSTALL);
        key(KeyEvent.KEYCODE_BUTTON_Y);
        assertEquals("notfound", page().shape());
        key(KeyEvent.KEYCODE_BACK);
        assertEquals(FirstRunPlan.Step.INSTALL, get("step"));
        assertEquals("B", page().shape());
    }

    @Test
    public void addHostOpensStepBWhoseNextLeadsToPairingAndBackReturns() throws Exception {
        start("en");
        wizard.open(FirstRunPlan.Entry.ADD_HOST, null);
        assertEquals(FirstRunPlan.Step.INSTALL, get("step"));
        assertEquals("b.next", focused().getTag());
        focused().performClick();
        assertEquals(FirstRunPlan.Step.PAIR, get("step"));
        key(KeyEvent.KEYCODE_BACK);
        assertEquals(FirstRunPlan.Step.INSTALL, get("step"));
        // "Enter IP address" from "I can't see my computer" opens the console's address screen.
        key(KeyEvent.KEYCODE_BUTTON_Y);
        page().find("notfound.address").performClick();
        assertEquals(1, host.addByAddress);
    }

    @Test
    public void aRecreatedConsoleResumesTheSameStepHostAndProfile() throws Exception {
        start("en");
        wizard.openAt(FirstRunPlan.Entry.SETTINGS, HOST, FirstRunPlan.Step.PROFILE);
        page().find("d.profile:tomek").performClick();
        assertEquals(FirstRunPlan.Step.READINESS, get("step"));

        // A language change recreates the activity: a new wizard reads the resume record.
        FirstRunWizard recreated = newWizard();
        assertTrue(recreated.autoStart(true));
        wizard = recreated;
        assertEquals(FirstRunPlan.Step.READINESS, get("step"));
        assertEquals(FirstRunPlan.Entry.SETTINGS, get("entry"));
        assertEquals(HOST, get("hostId"));
        assertEquals("tomek", get("chosenProfileId"));
        assertTrue(recreated.isShowing());
    }

    @Test
    public void settingsReRunOpensOnWelcomeWithTheCurrentLanguageFocused() throws Exception {
        start("en");
        wizard.openAt(FirstRunPlan.Entry.SETTINGS, HOST,
                FirstRunPlan.first(FirstRunPlan.Entry.SETTINGS));
        assertEquals(FirstRunPlan.Step.WELCOME, get("step"));
        assertTrue(String.valueOf(focused().getTag()).startsWith("a.lang:"));
    }

    @Test
    public void aRefreshUpdatesInPlaceWithoutRebuildingOrMovingFocus() throws Exception {
        start("en");
        wizard.openAt(FirstRunPlan.Entry.FIRST_RUN, HOST, FirstRunPlan.Step.READINESS);
        View focused = focused();
        assertEquals("e.next", focused.getTag());
        View vibepollo = page().find("e.check:VIBEPOLLO");
        set("capabilities", capabilities());
        wizard.refresh();
        assertSame(vibepollo, page().find("e.check:VIBEPOLLO"));
        assertSame(focused, focused());

        // Discovery in B only lights Next; focus and step stay.
        host.hosts.clear();
        wizard.openAt(FirstRunPlan.Entry.FIRST_RUN, null, FirstRunPlan.Step.INSTALL);
        page().find("b.missing").requestFocus();
        host.hosts.put(HOST, computer(HOST, "Komputer Ani"));
        wizard.refresh();
        assertEquals(FirstRunPlan.Step.INSTALL, get("step"));
        assertEquals("b.missing", focused().getTag());
        assertTrue(page().find("b.next").isActivated());
    }

    @Test
    public void aComputerFoundWhileChoosingJoinsTheListWithoutMovingOn() throws Exception {
        start("en");
        host.hosts.clear();
        wizard.openAt(FirstRunPlan.Entry.FIRST_RUN, null, FirstRunPlan.Step.PAIR);
        assertEquals("c.missing", focused().getTag());
        ComputerDetails fresh = computer(OTHER, "New PC");
        fresh.pairState = PairingManager.PairState.NOT_PAIRED;
        host.hosts.put(OTHER, fresh);
        wizard.refresh();
        assertNull(get("hostId"));
        assertTrue(page().shape().startsWith("C:list"));
        assertEquals("c.missing", focused().getTag());
    }

    @Test
    public void focusNeverLeavesTheWizardBody() throws Exception {
        start("en");
        wizard.openAt(FirstRunPlan.Entry.FIRST_RUN, HOST, FirstRunPlan.Step.FAMILY);
        View focused = focused();
        assertNotNull(focused);
        for (int direction : new int[]{View.FOCUS_UP, View.FOCUS_DOWN, View.FOCUS_LEFT,
                View.FOCUS_RIGHT}) {
            View next = focused.focusSearch(direction);
            assertNotNull(next);
            assertTrue(FirstRunWizardPage.isInside(bodyOf().getParent() instanceof View
                    ? (View) bodyOf().getParent() : bodyOf(), next));
        }
    }

    @Test
    public void typingTheCodeFillsTheCellsAndDeleteRemovesTheLastDigit() throws Exception {
        start("en");
        ComputerDetails fresh = computer(OTHER, "New PC");
        fresh.pairState = PairingManager.PairState.NOT_PAIRED;
        host.hosts.put(OTHER, fresh);
        wizard.openAt(FirstRunPlan.Entry.FIRST_RUN, OTHER, FirstRunPlan.Step.PAIR);
        View body = bodyOf();
        key(KeyEvent.KEYCODE_1);
        key(KeyEvent.KEYCODE_2);
        key(KeyEvent.KEYCODE_DEL);
        assertEquals("1", get("code").toString());
        assertSame(body, bodyOf());
    }

    // --- Helpers ----------------------------------------------------------------------------

    private View focused() {
        return activity.getWindow().getDecorView().findFocus();
    }

    private void key(int code) {
        wizard.handleKey(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0));
        wizard.handleKey(new KeyEvent(0, 0, KeyEvent.ACTION_UP, code, 0));
    }

    private FirstRunWizardPage page() throws Exception {
        return (FirstRunWizardPage) field("page").get(wizard);
    }

    private View bodyOf() {
        try {
            Field shown = FirstRunWizardPage.class.getDeclaredField("shown");
            shown.setAccessible(true);
            FirstRunWizardPage.Screen screen = (FirstRunWizardPage.Screen) shown.get(page());
            return screen == null ? null : screen.body;
        } catch (Exception impossible) {
            throw new AssertionError(impossible);
        }
    }

    private Object get(String name) throws Exception {
        return field(name).get(wizard);
    }

    private void set(String name, Object value) throws Exception {
        field(name).set(wizard, value);
    }

    private static Field field(String name) throws Exception {
        Field field = FirstRunWizard.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(String type, String value) throws Exception {
        Class<?> owner = Class.forName(FirstRunWizard.class.getName() + "$" + type);
        return Enum.valueOf((Class) owner, value);
    }

    private static HostPairingFlow.Failure failure(String problem, String detail)
            throws Exception {
        return new HostPairingFlow.Failure(HostPairingFlow.Problem.valueOf(problem), detail);
    }

    private static HostGatewayClient.Capabilities capabilities() {
        return new HostGatewayClient.Capabilities(true, true, true, true, true, true, true, true,
                true, new HostGatewayClient.SetupFacts(false, true, false, "", "linked"));
    }

    /** A finished analysis: Wi-Fi 412 Mb/s, a 4K60 TV, 4K60 at 80 Mb/s proposed. */
    private static StreamingAutopilotController.Analysis analysis() throws Exception {
        StreamingAutopilotController.Analysis analysis =
                allocate(StreamingAutopilotController.Analysis.class);
        StreamingAutopilot.Recommendation recommendation =
                allocate(StreamingAutopilot.Recommendation.class);
        put(recommendation, "width", 3840);
        put(recommendation, "height", 2160);
        put(recommendation, "fps", 60);
        put(recommendation, "bitrateKbps", 80_000);
        put(analysis, "clientCapabilities", new StreamingAutopilot.Capabilities(3840, 2160, 60));
        put(analysis, "networkStatus", StreamingAutopilotController.NetworkStatus.MEASURED);
        put(analysis, "goodputKbps", OptionalLong.of(412_000));
        put(analysis, "safeBitrateKbps", OptionalInt.of(80_000));
        put(analysis, "recommendation", Optional.of(recommendation));
        return analysis;
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Method allocate = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (T) allocate.invoke(unsafe, type);
    }

    private static void put(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
