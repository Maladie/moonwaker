package com.limelight.discord;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.limelight.gateway.GatewayConnection;
import com.limelight.gateway.GatewayTransport;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-wide view of one host profile's Discord friends and direct messages. The host runs
 * the Discord Social SDK and owns the account link; this class polls the Gateway, shows the
 * host's device-flow code as a QR and keeps the TV-local DM event lease. UI reads immutable
 * snapshots only.
 */
public final class DiscordSocialClient {
    private static final String STATE_PATH = "/api/v1/discord/social";
    private static final String ACTION_PATH = "/api/v1/discord/social/";
    private static final long POLL_MS = 1000L;
    private static final int READ_TIMEOUT_MS = 12000;
    private static final int MAX_MESSAGE_CHANNELS = 512;
    private static final int MAX_JAVA_MESSAGE_EVENTS = 256;
    private static final GatewayTransport TRANSPORT = new GatewayTransport();
    private static final Object stateLock = new Object();
    private static final Object messageLock = new Object();
    private static final Object messageObserverLock = new Object();
    private static final AtomicLong nextDirectMessageRequestId = new AtomicLong();
    private static final Snapshot EMPTY = new Snapshot(0, "Not connected", false, false,
            "", "", "", Collections.emptyList());

    private static Handler handler;
    private static Context appContext;
    private static GatewayConnection connection;
    private static long generation;
    private static long eventCursor = -1;
    private static volatile boolean available;
    private static volatile boolean linked;
    private static volatile boolean flowActive;
    private static volatile Snapshot snapshot = EMPTY;
    private static Dialog deviceDialog;
    private static WeakReference<Activity> dialogActivity = new WeakReference<>(null);
    private static final Deque<MessageEvent> pendingMessageEvents = new ArrayDeque<>();
    /** Message → DM channel, so "open in Discord" can build the message link. */
    private static final LinkedHashMap<Long, Long> messageChannels =
            new LinkedHashMap<Long, Long>(16, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest) {
                    return size() > MAX_MESSAGE_CHANNELS;
                }
            };
    private static final LinkedHashMap<ObserverToken, MessageEventObserver> messageEventObservers =
            new LinkedHashMap<>();
    private static long nextMessageEventLeaseEpoch;
    private static MessageEventLease activeMessageEventLease;

    private DiscordSocialClient() {}

    private interface GatewayCall {
        void run(GatewayConnection gateway, long generation) throws Exception;
    }

    /** True once the attached host reported that it runs Discord friends and messages. */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Follows {@code gateway}'s host profile from now on. Another host or profile starts from an
     * empty state; null stops polling.
     */
    public static void attach(Context context, GatewayConnection gateway) {
        synchronized (stateLock) {
            appContext = context.getApplicationContext();
            if (sameProfile(connection, gateway)) return;
            connection = gateway;
            generation++;
            eventCursor = -1;
            available = false;
            linked = false;
            flowActive = false;
            if (handler == null) {
                HandlerThread thread = new HandlerThread("DiscordSocialGateway");
                thread.start();
                handler = new Handler(thread.getLooper());
            }
            long current = generation;
            if (gateway != null) handler.post(() -> pollLoop(current));
        }
        synchronized (messageLock) {
            pendingMessageEvents.clear();
            messageChannels.clear();
        }
        dismissDeviceDialog();
        replaceSnapshot("Not connected", false, false, "", "", Collections.emptyList());
    }

    private static boolean sameProfile(GatewayConnection a, GatewayConnection b) {
        return a == b || (a != null && b != null && a.endpoint().equals(b.endpoint())
                && a.profileId().equals(b.profileId()) && a.token().equals(b.token()));
    }

    /** Asks the host for a Discord code and shows it as a QR; a linked host only reconnects. */
    public static void authorize(Activity activity) {
        WeakReference<Activity> activityReference = new WeakReference<>(activity);
        notifySocialStateObservers(snapshot, true);
        post((gateway, current) -> {
            JSONObject result = TRANSPORT.postJson(gateway, ACTION_PATH + "authorize",
                    new JSONObject(), READ_TIMEOUT_MS);
            String userCode = result.optString("user_code");
            if (result.optBoolean("linked") || userCode.isEmpty() || !isCurrent(current)) {
                notifyCurrentSocialState();
                return;
            }
            String url = result.optString("verification_url", "https://discord.com/activate");
            flowActive = true;
            Activity target = activityReference.get();
            if (target != null) target.runOnUiThread(() ->
                    showDeviceDialog(activityReference, current, url, userCode));
            setStatus("Awaiting Discord approval");
        });
    }

    public static void cancelAuthorization() {
        boolean wasActive = flowActive;
        flowActive = false;
        dismissDeviceDialog();
        if (wasActive) {
            post((gateway, current) -> TRANSPORT.postJson(gateway,
                    ACTION_PATH + "authorize/cancel", new JSONObject(), READ_TIMEOUT_MS));
            setStatus("Authorization canceled");
        } else {
            notifyCurrentSocialState();
        }
    }

    /** Forgets the host profile's Discord link; the host revokes it in the background. */
    public static void unlink() {
        cancelAuthorization();
        linked = false;
        replaceSnapshot("Discord authorization required", false, true, "", "",
                Collections.emptyList());
        post((gateway, current) -> TRANSPORT.postJson(gateway, ACTION_PATH + "unlink",
                new JSONObject(), READ_TIMEOUT_MS));
    }

    /** Returns the last immutable state. It never does I/O. */
    public static Snapshot getSnapshot() {
        return snapshot;
    }

    public static boolean canRetry(String status) {
        return status.startsWith("Authorization canceled")
                || status.startsWith("Authorization denied")
                || status.startsWith("Discord code expired")
                || status.startsWith("Unable to ");
    }

    public static boolean canRetry(Snapshot snapshot) {
        return snapshot.authorizationRequired || canRetry(snapshot.status);
    }

    public static boolean canUseDirectMessages() {
        return linked;
    }

    /** Conversation identity is the other participant, not MessageHandle.RecipientId blindly. */
    public static long directMessagePeer(long currentUserId, long authorId, long recipientId) {
        return authorId == currentUserId ? recipientId : authorId;
    }

    /** Requests at most 30 recent messages; results arrive as HISTORY_* events. */
    public static void requestUserMessages(long recipientId, long requestId) {
        if (recipientId <= 0 || requestId <= 0 || !canUseDirectMessages()) return;
        postAction("history", recipientId, requestId, null);
    }

    /** Sends only following an explicit UI action; callers own retry policy and drafts. */
    public static void sendUserMessage(long recipientId, long requestId, String content) {
        if (recipientId <= 0 || requestId <= 0 || content == null || content.isEmpty()
                || content.length() > 2000 || !canUseDirectMessages()) return;
        postAction("send", recipientId, requestId, content);
    }

    /** Opens non-text message content in Discord on this TV, or in its browser. */
    public static void openMessageInDiscord(long messageId) {
        if (messageId <= 0 || !canUseDirectMessages()) return;
        Long channelId;
        Context context;
        synchronized (messageLock) {
            channelId = messageChannels.get(messageId);
        }
        synchronized (stateLock) {
            context = appContext;
        }
        boolean opened = false;
        if (channelId != null && context != null) {
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://discord.com/channels/@me/" + channelId + "/" + messageId))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                opened = true;
            } catch (ActivityNotFoundException ignored) {
                // Reported below as an unsuccessful open.
            }
        }
        List<MessageEvent> result = new ArrayList<>();
        result.add(new MessageEvent(MessageEvent.Type.OPEN_MESSAGE_RESULT, 0, 0, messageId, 0, 0,
                "", 0, 0, "", "", 0, false, opened, false, 0, opened ? "" : "NoViewer"));
        enqueueMessageEvents(result);
    }

    /**
     * Acquires the process-wide consumer lease for direct-message events and chat presence.
     * A null result means another presentation currently owns both.
     */
    public static MessageEventLease tryAcquireMessageEventLease() {
        synchronized (messageLock) {
            if (activeMessageEventLease != null) return null;
            nextMessageEventLeaseEpoch = nextPositiveValue(nextMessageEventLeaseEpoch);
            activeMessageEventLease = new MessageEventLease(nextMessageEventLeaseEpoch);
            return activeMessageEventLease;
        }
    }

    /** Releases a lease only when it is still the active token, and clears chat presence. */
    public static void releaseMessageEventLease(MessageEventLease lease) {
        synchronized (messageLock) {
            if (!ownsMessageEventLeaseLocked(lease)) return;
            // Queue false before allowing a new owner to queue true, so a late old-owner
            // callback cannot turn off the replacement presentation's chat state.
            postChat(false);
            activeMessageEventLease = null;
        }
    }

    /** Updates chat presence only for the active direct-message event consumer. */
    public static void setShowingChat(MessageEventLease lease, boolean showing) {
        synchronized (messageLock) {
            if (!ownsMessageEventLeaseLocked(lease)) return;
            // Posting under the lease lock preserves ordering with release/handoff.
            postChat(showing);
        }
    }

    /** Returns and clears already-parsed events only for the active lease holder. */
    public static List<MessageEvent> drainMessageEvents(MessageEventLease lease) {
        synchronized (messageLock) {
            if (!ownsMessageEventLeaseLocked(lease)) return Collections.emptyList();
            if (pendingMessageEvents.isEmpty()) return Collections.emptyList();
            List<MessageEvent> result = new ArrayList<>(pendingMessageEvents);
            pendingMessageEvents.clear();
            return Collections.unmodifiableList(result);
        }
    }

    /** Observes accepted immutable events without taking ownership of the DM queue. */
    public interface MessageEventObserver {
        void onMessageEvent(MessageEvent event);
        default void onSocialStateChanged(Snapshot snapshot, boolean authorizationActive) { }
    }

    /** Opaque registration token; observer identity is never used for removal. */
    public static final class ObserverToken {
        private ObserverToken() { }
    }

    public static ObserverToken addMessageEventObserver(MessageEventObserver observer) {
        if (observer == null) throw new NullPointerException("observer");
        ObserverToken token = new ObserverToken();
        synchronized (messageObserverLock) {
            messageEventObservers.put(token, observer);
        }
        try { observer.onSocialStateChanged(snapshot, flowActive); }
        catch (RuntimeException ignored) { }
        return token;
    }

    public static void removeMessageEventObserver(ObserverToken token) {
        if (token == null) return;
        synchronized (messageObserverLock) {
            messageEventObservers.remove(token);
        }
    }

    /** Allocates a non-zero, positive request ID shared by all DM history and send requests. */
    public static long nextDirectMessageRequestId() {
        while (true) {
            long current = nextDirectMessageRequestId.get();
            long next = nextPositiveValue(current);
            if (nextDirectMessageRequestId.compareAndSet(current, next)) return next;
        }
    }

    private static boolean ownsMessageEventLeaseLocked(MessageEventLease lease) {
        return lease != null && lease == activeMessageEventLease
                && lease.epoch == activeMessageEventLease.epoch;
    }

    static long nextPositiveValue(long current) {
        return current >= Long.MAX_VALUE || current < 0 ? 1L : current + 1L;
    }

    /** Opaque token for the sole process-local DM consumer. */
    public static final class MessageEventLease {
        private final long epoch;

        private MessageEventLease(long epoch) {
            this.epoch = epoch;
        }
    }

    // Gateway ---------------------------------------------------------------------------------

    private static boolean isCurrent(long expected) {
        synchronized (stateLock) {
            return expected == generation;
        }
    }

    /** Runs {@code call} on the Gateway thread, in order with polls and other actions. */
    private static void post(GatewayCall call) {
        GatewayConnection gateway;
        long current;
        Handler target;
        synchronized (stateLock) {
            gateway = connection;
            current = generation;
            target = handler;
        }
        if (gateway == null || target == null) {
            setStatus("Unable to reach the host");
            return;
        }
        target.post(() -> {
            try {
                call.run(gateway, current);
            } catch (Exception ignored) {
                if (isCurrent(current)) setStatus("Unable to reach the host");
            }
        });
    }

    private static void postAction(String action, long recipientId, long requestId, String content) {
        post((gateway, current) -> {
            JSONObject body = new JSONObject().put("recipient_id", recipientId)
                    .put("request_id", requestId);
            if (content != null) body.put("content", content);
            TRANSPORT.postJson(gateway, ACTION_PATH + action, body, READ_TIMEOUT_MS);
        });
    }

    private static void postChat(boolean showing) {
        synchronized (stateLock) {
            if (connection == null) return;
        }
        post((gateway, current) -> TRANSPORT.postJson(gateway, ACTION_PATH + "chat",
                new JSONObject().put("showing", showing), READ_TIMEOUT_MS));
    }

    private static void pollLoop(long current) {
        GatewayConnection gateway;
        long after;
        synchronized (stateLock) {
            if (current != generation) return;
            gateway = connection;
            after = eventCursor;
        }
        try {
            JSONObject state = TRANSPORT.getJson(gateway, STATE_PATH + "?after=" + after,
                    READ_TIMEOUT_MS);
            apply(current, state);
        } catch (Exception ignored) {
            if (isCurrent(current)) setStatus("Unable to reach the host");
        }
        synchronized (stateLock) {
            if (current == generation) handler.postDelayed(() -> pollLoop(current), POLL_MS);
        }
    }

    private static void apply(long current, JSONObject state) {
        JSONObject authorization = state.optJSONObject("authorization");
        boolean active = authorization != null && authorization.optBoolean("active");
        List<MessageEvent> events = new ArrayList<>();
        JSONArray records = state.optJSONArray("events");
        for (int index = 0; records != null && index < records.length(); index++) {
            JSONObject record = records.optJSONObject(index);
            events.add(record == null ? null : MessageEvent.parse(record));
        }
        synchronized (stateLock) {
            if (current != generation) return;
            available = state.optBoolean("available");
            linked = available && state.optBoolean("linked");
            eventCursor = state.optLong("next", eventCursor);
        }
        if (flowActive && !active) {
            flowActive = false;
            dismissDeviceDialog();
        }
        publishSnapshot(Snapshot.fromGateway(state, snapshot.revision + 1));
        enqueueMessageEvents(events);
    }

    // Snapshot and events ---------------------------------------------------------------------

    private static void enqueueMessageEvents(List<MessageEvent> events) {
        if (events.isEmpty()) return;
        List<MessageEvent> accepted = new ArrayList<>(events.size());
        synchronized (messageLock) {
            for (MessageEvent event : events) {
                if (event == null) event = MessageEvent.overflow();
                if (event.type == MessageEvent.Type.OVERFLOW
                        || pendingMessageEvents.size() >= MAX_JAVA_MESSAGE_EVENTS) {
                    // The only safe response to a dropped/invalid event is a bounded reload by
                    // the active conversation owner; do not present a partially current model.
                    pendingMessageEvents.clear();
                    accepted.clear();
                    event = MessageEvent.overflow();
                    pendingMessageEvents.addLast(event);
                    accepted.add(event);
                    continue;
                }
                if (event.messageId > 0 && event.channelId > 0) {
                    messageChannels.put(event.messageId, event.channelId);
                }
                pendingMessageEvents.addLast(event);
                accepted.add(event);
            }
        }
        notifyMessageEventObservers(accepted);
    }

    private static void notifyMessageEventObservers(List<MessageEvent> events) {
        if (events.isEmpty()) return;
        List<MessageEventObserver> observers;
        synchronized (messageObserverLock) {
            if (messageEventObservers.isEmpty()) return;
            observers = new ArrayList<>(messageEventObservers.values());
        }
        for (MessageEvent event : events) {
            for (MessageEventObserver observer : observers) {
                try {
                    observer.onMessageEvent(event);
                } catch (RuntimeException ignored) {
                    // A presentation observer cannot damage queue ownership or other observers.
                }
            }
        }
    }

    private static void notifyCurrentSocialState() {
        notifySocialStateObservers(snapshot, flowActive);
    }

    private static void notifySocialStateObservers(Snapshot current,
                                                   boolean authorizationActive) {
        List<MessageEventObserver> observers;
        synchronized (messageObserverLock) {
            if (messageEventObservers.isEmpty()) return;
            observers = new ArrayList<>(messageEventObservers.values());
        }
        for (MessageEventObserver observer : observers) {
            try { observer.onSocialStateChanged(current, authorizationActive); }
            catch (RuntimeException ignored) { }
        }
    }

    /** Test seam: the JSON records the Gateway sends; unparsable ones become an overflow. */
    static void enqueueMessageEventsForTest(String... records) {
        List<MessageEvent> events = new ArrayList<>();
        for (String record : records) {
            try {
                events.add(MessageEvent.parse(new JSONObject(record)));
            } catch (JSONException malformed) {
                events.add(null);
            }
        }
        enqueueMessageEvents(events);
    }

    static boolean messageLockHeldByCurrentThreadForTest() {
        return Thread.holdsLock(messageLock);
    }

    static int messageEventObserverCountForTest() {
        synchronized (messageObserverLock) {
            return messageEventObservers.size();
        }
    }

    private static void setStatus(String status) {
        Snapshot current = snapshot;
        replaceSnapshot(status, current.connected, current.authorizationRequired,
                current.userId, current.displayName, current.friendDetails);
    }

    private static void replaceSnapshot(String status, boolean connected, boolean authorizationRequired,
                                        String userId, String displayName, List<Friend> friends) {
        Snapshot previous = snapshot;
        publishSnapshot(new Snapshot(previous.revision + 1, status, connected, authorizationRequired,
                userId, displayName, userId.equals(previous.userId) ? previous.avatarUrl : "", friends));
    }

    private static void publishSnapshot(Snapshot next) {
        Snapshot previous = snapshot;
        if (next.sameData(previous)) return;
        snapshot = next;
        notifyCurrentSocialState();
    }

    // Device-flow dialog ----------------------------------------------------------------------

    private static void showDeviceDialog(WeakReference<Activity> activityReference, long expected,
                                         String verificationUrl, String userCode) {
        Activity activity = activityReference.get();
        if (!flowActive || !isCurrent(expected) || activity == null || activity.isFinishing()
                || activity.isDestroyed()) {
            if (flowActive) cancelAuthorization();
            return;
        }
        int padding = dp(activity, 28);
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(padding, padding, padding, dp(activity, 22));
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1B252D, 0xFF0B1015});
        background.setCornerRadius(dp(activity, 22));
        background.setStroke(dp(activity, 1), 0x99768B99);
        card.setBackground(background);
        card.addView(label(activity, "Połącz Discord", 25, Color.WHITE, true), match());
        TextView help = label(activity, "Zeskanuj kod QR telefonem i potwierdź w Discordzie.",
                15, 0xFFC4CED5, false);
        LinearLayout.LayoutParams helpParams = match();
        helpParams.topMargin = dp(activity, 8);
        card.addView(help, helpParams);
        ImageView qr = new ImageView(activity);
        qr.setImageBitmap(qrCode(verificationUrl, dp(activity, 240)));
        qr.setContentDescription("Kod QR Discord");
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(dp(activity, 240), dp(activity, 240));
        qrParams.topMargin = dp(activity, 18);
        card.addView(qr, qrParams);
        TextView code = label(activity, userCode, 28, Color.WHITE, true);
        code.setGravity(Gravity.CENTER);
        code.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams codeParams = match();
        codeParams.topMargin = dp(activity, 12);
        card.addView(code, codeParams);
        TextView fallback = label(activity, "Lub wejdź na discord.com/activate", 14, 0xFFC4CED5, false);
        fallback.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fallbackParams = match();
        fallbackParams.topMargin = dp(activity, 6);
        card.addView(fallback, fallbackParams);
        TextView cancel = label(activity, "Anuluj", 15, Color.WHITE, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setFocusable(true);
        cancel.setClickable(true);
        cancel.setPadding(dp(activity, 24), 0, dp(activity, 24), 0);
        cancel.setBackground(buttonBackground(activity, false));
        cancel.setOnFocusChangeListener((view, focused) -> cancel.setBackground(buttonBackground(activity, focused)));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(dp(activity, 180), dp(activity, 50));
        cancelParams.topMargin = dp(activity, 20);
        card.addView(cancel, cancelParams);
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(card);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(ignored -> {
            boolean ownsDialog;
            synchronized (stateLock) {
                ownsDialog = deviceDialog == dialog;
                if (ownsDialog) {
                    deviceDialog = null;
                    dialogActivity = new WeakReference<>(null);
                }
            }
            if (ownsDialog) cancelAuthorization();
        });
        cancel.setOnClickListener(view -> dialog.dismiss());
        dialog.setOnKeyListener((ignored, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_BACK
                    || keyCode == KeyEvent.KEYCODE_BUTTON_B)) {
                dialog.dismiss();
                return true;
            }
            return false;
        });
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.width = dp(activity, 680);
            attributes.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attributes.gravity = Gravity.CENTER;
            attributes.dimAmount = .72f;
            window.setAttributes(attributes);
        }
        synchronized (stateLock) {
            deviceDialog = dialog;
            dialogActivity = activityReference;
        }
        dialog.show();
        if (window != null) window.setLayout(dp(activity, 680), WindowManager.LayoutParams.WRAP_CONTENT);
        cancel.requestFocus();
    }

    private static void dismissDeviceDialog() {
        final Dialog dialog;
        final Activity activity;
        synchronized (stateLock) {
            dialog = deviceDialog;
            activity = dialogActivity.get();
            deviceDialog = null;
            dialogActivity = new WeakReference<>(null);
        }
        if (dialog == null || activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        activity.runOnUiThread(dialog::dismiss);
    }

    private static Bitmap qrCode(String value, int size) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size);
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++)
                pixels[y * size + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
            return bitmap;
        } catch (WriterException e) {
            throw new IllegalStateException("Unable to create QR code", e);
        }
    }

    private static TextView label(Activity activity, String text, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private static LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static GradientDrawable buttonBackground(Activity activity, boolean focused) {
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{focused ? 0xFF32444F : 0xC0232C33, focused ? 0xFF172129 : 0xD0141A1F});
        background.setCornerRadius(dp(activity, 12));
        background.setStroke(dp(activity, focused ? 2 : 1), focused ? 0xFFDDF5FF : 0x55788A96);
        return background;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    /** Immutable direct-message event as the host's Gateway reports it. */
    public static final class MessageEvent {
        public enum Type {
            HISTORY_BEGIN, HISTORY_MESSAGE, HISTORY_RESULT, CREATED, UPDATED, DELETED,
            SEND_RESULT, OPEN_MESSAGE_RESULT, OVERFLOW
        }

        public final Type type;
        public final long recipientId;
        public final long requestId;
        public final long messageId;
        public final long channelId;
        public final long authorId;
        public final String content;
        public final long sentTimestampMs;
        public final long editedTimestampMs;
        public final String additionalContentType;
        public final String additionalContentTitle;
        public final int additionalContentCount;
        public final boolean disclosure;
        public final boolean successful;
        public final boolean retryable;
        public final float retryAfterSeconds;
        public final String errorType;

        MessageEvent(Type type, long recipientId, long requestId, long messageId, long channelId,
                     long authorId, String content, long sentTimestampMs, long editedTimestampMs,
                     String additionalContentType, String additionalContentTitle,
                     int additionalContentCount, boolean disclosure, boolean successful,
                     boolean retryable, float retryAfterSeconds, String errorType) {
            this.type = type;
            this.recipientId = recipientId;
            this.requestId = requestId;
            this.messageId = messageId;
            this.channelId = channelId;
            this.authorId = authorId;
            this.content = content;
            this.sentTimestampMs = sentTimestampMs;
            this.editedTimestampMs = editedTimestampMs;
            this.additionalContentType = additionalContentType;
            this.additionalContentTitle = additionalContentTitle;
            this.additionalContentCount = additionalContentCount;
            this.disclosure = disclosure;
            this.successful = successful;
            this.retryable = retryable;
            this.retryAfterSeconds = retryAfterSeconds;
            this.errorType = errorType;
        }

        static MessageEvent overflow() {
            return new MessageEvent(Type.OVERFLOW, 0, 0, 0, 0, 0, "", 0, 0, "", "", 0,
                    false, false, false, 0, "");
        }

        /** Null for an unknown type or negative fields; callers treat that as an overflow. */
        public static MessageEvent parse(JSONObject record) {
            try {
                Type type = Type.valueOf(record.getString("type"));
                MessageEvent event = new MessageEvent(type, record.optLong("recipient_id"),
                        record.optLong("request_id"), record.optLong("message_id"),
                        record.optLong("channel_id"), record.optLong("author_id"),
                        record.optString("content"), record.optLong("sent"), record.optLong("edited"),
                        record.optString("additional_type"), record.optString("additional_title"),
                        record.optInt("additional_count"), record.optBoolean("disclosure"),
                        record.optBoolean("successful"), record.optBoolean("retryable"),
                        (float) record.optDouble("retry_after", 0), record.optString("error"));
                return event.recipientId < 0 || event.requestId < 0 || event.messageId < 0
                        || event.authorId < 0 || event.additionalContentCount < 0
                        || !(event.retryAfterSeconds >= 0) ? null : event;
            } catch (JSONException | IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public static final class Friend {
        public enum Group { PLAYING, ONLINE, OFFLINE }
        public final String userId;
        public final String displayName;
        public final Group group;
        public final String activityName;

        public final String avatarUrl;

        Friend(String userId, String displayName, Group group, String activityName,
               String avatarUrl) {
            this.userId = userId;
            this.displayName = displayName;
            this.group = group;
            this.activityName = activityName;
            this.avatarUrl = avatarUrl;
        }

        @Override public String toString() { return displayName; }

        @Override public boolean equals(Object value) {
            if (!(value instanceof Friend)) return false;
            Friend other = (Friend) value;
            return userId.equals(other.userId) && displayName.equals(other.displayName)
                    && group == other.group && activityName.equals(other.activityName)
                    && avatarUrl.equals(other.avatarUrl);
        }

        @Override public int hashCode() {
            return ((((userId.hashCode() * 31) + displayName.hashCode()) * 31 + group.hashCode()) * 31
                    + activityName.hashCode()) * 31 + avatarUrl.hashCode();
        }
    }

    public static final class Snapshot {
        public final long revision;
        public final String status;
        public final boolean connected;
        public final boolean authorizationRequired;
        public final String userId;
        public final String displayName;
        public final String avatarUrl;
        public final List<Friend> friendDetails;
        /** Friend names, for the compact panel. */
        public final List<String> friends;

        Snapshot(long revision, String status, boolean connected, boolean authorizationRequired,
                 String userId, String displayName, String avatarUrl, List<Friend> friendDetails) {
            this.revision = revision;
            this.status = status;
            this.connected = connected;
            this.authorizationRequired = authorizationRequired;
            this.userId = userId;
            this.displayName = displayName;
            this.avatarUrl = avatarUrl;
            this.friendDetails = Collections.unmodifiableList(new ArrayList<>(friendDetails));
            List<String> names = new ArrayList<>();
            for (Friend friend : friendDetails) names.add(friend.displayName);
            this.friends = Collections.unmodifiableList(names);
        }

        /** The Gateway's {@code /discord/social} answer; unknown friend groups are skipped. */
        static Snapshot fromGateway(JSONObject state, long revision) {
            if (!state.optBoolean("available")) {
                return new Snapshot(revision, "Unavailable on this host", false, false,
                        "", "", "", Collections.emptyList());
            }
            JSONObject authorization = state.optJSONObject("authorization");
            boolean active = authorization != null && authorization.optBoolean("active");
            String flow = authorization == null ? "" : authorization.optString("status");
            boolean linked = state.optBoolean("linked");
            String status = active ? "Awaiting Discord approval"
                    : linked ? state.optString("status", "Connecting")
                    : "denied".equals(flow) ? "Authorization denied"
                    : "expired".equals(flow) ? "Discord code expired"
                    : "failed".equals(flow) ? "Unable to contact Discord"
                    : "Discord authorization required";
            JSONObject user = state.optJSONObject("user");
            List<Friend> friends = new ArrayList<>();
            JSONArray records = state.optJSONArray("friends");
            for (int index = 0; records != null && index < records.length(); index++) {
                JSONObject friend = records.optJSONObject(index);
                if (friend == null) continue;
                try {
                    friends.add(new Friend(friend.optString("id"), friend.optString("name"),
                            Friend.Group.valueOf(friend.optString("group")),
                            friend.optString("activity"), friend.optString("avatar")));
                } catch (IllegalArgumentException ignored) {
                    // A group this TV does not know is not shown.
                }
            }
            return new Snapshot(revision, status, linked && state.optBoolean("connected"),
                    !linked && !active, user == null ? "" : user.optString("id"),
                    user == null ? "" : user.optString("name"),
                    user == null ? "" : user.optString("avatar"), friends);
        }

        boolean sameData(Snapshot other) {
            return status.equals(other.status) && connected == other.connected
                    && authorizationRequired == other.authorizationRequired && userId.equals(other.userId)
                    && displayName.equals(other.displayName) && avatarUrl.equals(other.avatarUrl)
                    && friendDetails.equals(other.friendDetails);
        }
    }
}
