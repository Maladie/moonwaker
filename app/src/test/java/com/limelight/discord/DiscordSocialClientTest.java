package com.limelight.discord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class DiscordSocialClientTest {
    private static final String OVERFLOW = "{\"type\":\"OVERFLOW\"}";

    private static DiscordSocialClient.Snapshot snapshot(String json) throws Exception {
        return DiscordSocialClient.Snapshot.fromGateway(new JSONObject(json), 1);
    }

    private static String linked(String friends) {
        return "{\"available\":true,\"linked\":true,\"status\":\"Ready\",\"connected\":true,"
                + "\"authorization\":{\"active\":false,\"status\":\"none\"},"
                + "\"user\":{\"id\":1,\"name\":\"me\",\"avatar\":\"me.png\"},"
                + "\"friends\":[" + friends + "]}";
    }

    @Test
    public void hostWithoutSocialIsUnavailableNotAnAuthorizationPrompt() throws Exception {
        DiscordSocialClient.Snapshot state = snapshot("{\"available\":false}");
        assertEquals("Unavailable on this host", state.status);
        assertFalse(state.authorizationRequired);
        assertFalse(state.connected);
    }

    @Test
    public void mapsTheHostDeviceFlowToStatuses() throws Exception {
        String unlinked = "{\"available\":true,\"linked\":false,\"authorization\":"
                + "{\"active\":%s,\"status\":\"%s\"}}";
        DiscordSocialClient.Snapshot pending = snapshot(String.format(unlinked, "true", "pending"));
        assertEquals("Awaiting Discord approval", pending.status);
        assertFalse(pending.authorizationRequired);
        assertEquals("Authorization denied",
                snapshot(String.format(unlinked, "false", "denied")).status);
        assertEquals("Discord code expired",
                snapshot(String.format(unlinked, "false", "expired")).status);
        assertEquals("Unable to contact Discord",
                snapshot(String.format(unlinked, "false", "failed")).status);
        DiscordSocialClient.Snapshot required = snapshot(String.format(unlinked, "false", "none"));
        assertEquals("Discord authorization required", required.status);
        assertTrue(required.authorizationRequired);
        assertTrue(DiscordSocialClient.canRetry(required));
    }

    @Test
    public void linkedSnapshotCarriesAccountAndFriends() throws Exception {
        DiscordSocialClient.Snapshot state = snapshot(linked(
                "{\"id\":2,\"name\":\"friend\",\"group\":\"PLAYING\",\"activity\":\"MoonWaker\","
                        + "\"avatar\":\"f.png\"},{\"id\":3,\"name\":\"x\",\"group\":\"FUTURE\"}"));
        assertTrue(state.connected);
        assertEquals("1", state.userId);
        assertEquals("me.png", state.avatarUrl);
        assertEquals(Collections.singletonList("friend"), state.friends);
        assertEquals("2", state.friendDetails.get(0).userId);
        assertEquals(DiscordSocialClient.Friend.Group.PLAYING, state.friendDetails.get(0).group);
    }

    @Test
    public void activityAndAvatarChangesParticipateInSnapshotEquality() throws Exception {
        DiscordSocialClient.Snapshot idle = snapshot(linked(
                "{\"id\":2,\"name\":\"friend\",\"group\":\"ONLINE\",\"avatar\":\"a.png\"}"));
        DiscordSocialClient.Snapshot playing = snapshot(linked(
                "{\"id\":2,\"name\":\"friend\",\"group\":\"PLAYING\",\"activity\":\"MoonWaker\","
                        + "\"avatar\":\"a.png\"}"));
        DiscordSocialClient.Snapshot avatar = snapshot(linked(
                "{\"id\":2,\"name\":\"friend\",\"group\":\"ONLINE\",\"avatar\":\"b.png\"}"));
        assertFalse(idle.sameData(playing));
        assertFalse(idle.sameData(avatar));
        assertTrue(idle.sameData(snapshot(linked(
                "{\"id\":2,\"name\":\"friend\",\"group\":\"ONLINE\",\"avatar\":\"a.png\"}"))));
    }

    @Test
    public void messageEventsPreserveUnicodeAndAdditionalContent() throws Exception {
        String content = "zażółć\n| : 😀";
        DiscordSocialClient.MessageEvent event = DiscordSocialClient.MessageEvent.parse(
                new JSONObject().put("type", "HISTORY_MESSAGE").put("recipient_id", 42)
                        .put("request_id", 1).put("message_id", 9).put("channel_id", 5)
                        .put("author_id", 7).put("content", content).put("sent", 100)
                        .put("additional_type", "Poll").put("additional_title", "Which one?")
                        .put("additional_count", 2));
        assertNotNull(event);
        assertEquals(DiscordSocialClient.MessageEvent.Type.HISTORY_MESSAGE, event.type);
        assertEquals(content, event.content);
        assertEquals(42L, event.recipientId);
        assertEquals(9L, event.messageId);
        assertEquals(5L, event.channelId);
        assertEquals("Poll", event.additionalContentType);
        assertEquals("Which one?", event.additionalContentTitle);
        assertEquals(2, event.additionalContentCount);
    }

    @Test
    public void unknownOrInvalidEventsAreRejected() throws Exception {
        assertNull(DiscordSocialClient.MessageEvent.parse(new JSONObject("{\"type\":\"NEW\"}")));
        assertNull(DiscordSocialClient.MessageEvent.parse(new JSONObject("{}")));
        assertNull(DiscordSocialClient.MessageEvent.parse(
                new JSONObject("{\"type\":\"CREATED\",\"message_id\":-1}")));
        assertNull(DiscordSocialClient.MessageEvent.parse(
                new JSONObject("{\"type\":\"SEND_RESULT\",\"retry_after\":-1}")));
    }

    @Test
    public void directMessagePeerIsTheOtherParticipantInBothDirections() {
        assertEquals(42L, DiscordSocialClient.directMessagePeer(7L, 42L, 7L));
        assertEquals(42L, DiscordSocialClient.directMessagePeer(7L, 7L, 42L));
    }

    @Test
    public void directMessageEventsHaveExactlyOneConsumerLease() {
        DiscordSocialClient.MessageEventLease first = DiscordSocialClient.tryAcquireMessageEventLease();
        assertNotNull(first);
        try {
            assertNull(DiscordSocialClient.tryAcquireMessageEventLease());
        } finally {
            DiscordSocialClient.releaseMessageEventLease(first);
        }
    }

    @Test
    public void staleLeaseCannotDrainOrReleaseAfterHandoff() {
        DiscordSocialClient.MessageEventLease first = DiscordSocialClient.tryAcquireMessageEventLease();
        assertNotNull(first);
        DiscordSocialClient.releaseMessageEventLease(first);

        DiscordSocialClient.MessageEventLease second = DiscordSocialClient.tryAcquireMessageEventLease();
        assertNotNull(second);
        try {
            DiscordSocialClient.enqueueMessageEventsForTest(OVERFLOW);
            assertTrue(DiscordSocialClient.drainMessageEvents(first).isEmpty());
            DiscordSocialClient.releaseMessageEventLease(first);
            assertEquals(1, DiscordSocialClient.drainMessageEvents(second).size());
        } finally {
            DiscordSocialClient.releaseMessageEventLease(second);
        }
    }

    @Test
    public void eventsRemainQueuedUntilAConsumerOwnsALease() {
        DiscordSocialClient.enqueueMessageEventsForTest(OVERFLOW);
        assertTrue(DiscordSocialClient.drainMessageEvents(null).isEmpty());

        DiscordSocialClient.MessageEventLease lease = DiscordSocialClient.tryAcquireMessageEventLease();
        assertNotNull(lease);
        try {
            List<DiscordSocialClient.MessageEvent> events = DiscordSocialClient.drainMessageEvents(lease);
            assertEquals(1, events.size());
            assertEquals(DiscordSocialClient.MessageEvent.Type.OVERFLOW, events.get(0).type);
        } finally {
            DiscordSocialClient.releaseMessageEventLease(lease);
        }
    }

    @Test
    public void directMessageRequestIdsAreGloballyPositiveUniqueAndIncreasing() {
        Set<Long> ids = new HashSet<>();
        long previous = 0;
        for (int index = 0; index < 32; index++) {
            long requestId = DiscordSocialClient.nextDirectMessageRequestId();
            assertTrue(requestId > 0);
            assertTrue(requestId > previous);
            assertTrue(ids.add(requestId));
            previous = requestId;
        }
    }

    @Test
    public void requestIdOverflowSkipsZeroAndNegativeValues() {
        assertEquals(1L, DiscordSocialClient.nextPositiveValue(Long.MAX_VALUE));
        assertEquals(1L, DiscordSocialClient.nextPositiveValue(-1L));
    }

    @Test
    public void messageObserversRunExactlyOnceOutsideTheQueueLock() {
        AtomicInteger calls = new AtomicInteger();
        DiscordSocialClient.ObserverToken token = DiscordSocialClient.addMessageEventObserver(event -> {
            assertFalse(DiscordSocialClient.messageLockHeldByCurrentThreadForTest());
            calls.incrementAndGet();
        });
        try {
            DiscordSocialClient.enqueueMessageEventsForTest(OVERFLOW);
            assertEquals(1, calls.get());
        } finally {
            DiscordSocialClient.removeMessageEventObserver(token);
            drainQueuedEvents();
        }
    }

    @Test
    public void observerFailureIsolatedAndRemovalStopsDelivery() {
        AtomicInteger calls = new AtomicInteger();
        DiscordSocialClient.ObserverToken failing = DiscordSocialClient.addMessageEventObserver(
                event -> { throw new IllegalStateException("expected"); });
        DiscordSocialClient.ObserverToken healthy = DiscordSocialClient.addMessageEventObserver(
                event -> calls.incrementAndGet());
        try {
            DiscordSocialClient.enqueueMessageEventsForTest(OVERFLOW);
            assertEquals(1, calls.get());
            DiscordSocialClient.removeMessageEventObserver(healthy);
            DiscordSocialClient.enqueueMessageEventsForTest(OVERFLOW);
            assertEquals(1, calls.get());
        } finally {
            DiscordSocialClient.removeMessageEventObserver(failing);
            DiscordSocialClient.removeMessageEventObserver(healthy);
            drainQueuedEvents();
        }
    }

    @Test
    public void observerDoesNotReceiveEventsDiscardedByOverflow() {
        List<DiscordSocialClient.MessageEvent.Type> types = new ArrayList<>();
        DiscordSocialClient.ObserverToken observer = DiscordSocialClient.addMessageEventObserver(
                event -> types.add(event.type));
        try {
            DiscordSocialClient.enqueueMessageEventsForTest(
                    "{\"type\":\"CREATED\",\"recipient_id\":42,\"message_id\":9,\"content\":\"hello\"}",
                    "malformed");
            assertEquals(Collections.singletonList(DiscordSocialClient.MessageEvent.Type.OVERFLOW),
                    types);
        } finally {
            DiscordSocialClient.removeMessageEventObserver(observer);
            drainQueuedEvents();
        }
    }

    @Test
    public void observerDoesNotChangeLeaseOrDrainOwnership() {
        AtomicInteger calls = new AtomicInteger();
        DiscordSocialClient.ObserverToken observer = DiscordSocialClient.addMessageEventObserver(
                event -> calls.incrementAndGet());
        DiscordSocialClient.MessageEventLease owner = DiscordSocialClient.tryAcquireMessageEventLease();
        assertNotNull(owner);
        try {
            DiscordSocialClient.enqueueMessageEventsForTest(OVERFLOW);
            assertNull(DiscordSocialClient.tryAcquireMessageEventLease());
            List<DiscordSocialClient.MessageEvent> drained =
                    DiscordSocialClient.drainMessageEvents(owner);
            assertEquals(1, calls.get());
            assertEquals(1, drained.size());
            assertEquals(DiscordSocialClient.MessageEvent.Type.OVERFLOW, drained.get(0).type);
        } finally {
            DiscordSocialClient.removeMessageEventObserver(observer);
            DiscordSocialClient.releaseMessageEventLease(owner);
        }
    }

    private static void drainQueuedEvents() {
        DiscordSocialClient.MessageEventLease lease =
                DiscordSocialClient.tryAcquireMessageEventLease();
        if (lease == null) return;
        try {
            DiscordSocialClient.drainMessageEvents(lease);
        } finally {
            DiscordSocialClient.releaseMessageEventLease(lease);
        }
    }
}
