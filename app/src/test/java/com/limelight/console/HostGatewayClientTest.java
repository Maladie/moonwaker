package com.limelight.console;

import com.limelight.session.SessionModel;
import com.limelight.session.SessionModelDriver;

import com.limelight.gateway.GatewayTransport;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HostGatewayClientTest {
    private final SessionModelDriver m = new SessionModelDriver();
    @Test public void systemAudioKeepsOnlyWellFormedDevices() throws Exception {
        HostGatewayClient.SystemAudioState audio = HostGatewayClient.parseSystemAudio(
                new JSONObject("{\"audio\": {\"available\": true, \"volume\": 35,"
                        + " \"muted\": true, \"devices\": ["
                        + "{\"id\": \"{0.0.0.00000000}.{a1}\", \"name\": \"TV\","
                        + " \"flow\": \"output\", \"is_default\": true},"
                        + "{\"id\": \"../shutdown\", \"flow\": \"output\"},"
                        + "{\"id\": \"mic\", \"flow\": \"loopback\"}]}}"));

        assertTrue(audio.available);
        assertEquals(35, audio.volume);
        assertTrue(audio.muted);
        assertEquals(1, audio.devices.size());
        assertEquals("TV", audio.devices.get(0).name);
        assertTrue(audio.devices.get(0).current);
        assertFalse(HostGatewayClient.parseSystemAudio(new JSONObject("{}")).available);
    }

    @Test public void coherentObservationRequiresExactSchemaScopeAndEvidence()
            throws Exception {
        long now = 1_700_000_000_000L;
        JSONObject value = observation(now, "boot-a", 2, 7);
        HostGatewayClient.SessionObservation parsed =
                HostGatewayClient.parseSessionObservation(value, "kid", "parent",
                        7, now + 100);
        assertEquals("boot-a", parsed.producerBootId);
        assertEquals(2L, parsed.producerRevision);
        assertEquals(HostGatewayClient.Evidence.VERIFIED, parsed.processEvidence);
        assertTrue(parsed.windowReady);

        value.getJSONObject("scope").put("actor_profile_id", "other");
        assertObservationRejected(value, "observation_correlation_mismatch", now);
    }

    @Test public void malformedTimestampAndMissingGameEvidenceFailClosed() throws Exception {
        long now = 1_700_000_000_000L;
        JSONObject value = observation(now, "boot-a", 2, 7);
        value.getJSONObject("game").put("evidence", "ABSENT");
        assertObservationRejected(value, "inconsistent_observation_evidence", now);
        value = observation(now - 366L * 24L * 60L * 60L * 1_000L,
                "boot-a", 2, 7);
        assertObservationRejected(value, "stale_or_malformed_observation", now);
        value = observation(now, "boot-a", 2, 7);
        value.put("schema_version", 1);
        assertObservationRejected(value, "unsupported_observation_schema", now);
    }

    @Test public void previousLaunchGameIsParsedFromTheWindowBlock() throws Exception {
        long now = 1_700_000_000_000L;
        JSONObject value = observation(now, "boot-a", 2, 7);
        value.getJSONObject("game").put("state", "starting").put("id", "steam:456");
        value.put("process", new JSONObject().put("evidence", "ABSENT")
                .put("process_id", 0).put("process_token", "").put("started_unix_ms", 0));
        value.put("window", new JSONObject().put("evidence", "ABSENT")
                .put("ready", false).put("target_kind", "game").put("stable_samples", 0)
                .put("reason", "previous_launch_game_running")
                .put("previous_launch", new JSONObject().put("game_id", "steam:123")
                        .put("process_id", 77).put("process_token", repeat('b', 64))));

        HostGatewayClient.SessionObservation parsed =
                HostGatewayClient.parseSessionObservation(value, "kid", "parent",
                        7, now + 100);

        assertEquals("previous_launch_game_running", parsed.reason);
        assertEquals("steam:123", parsed.previousLaunchGameId);
        assertEquals(repeat('b', 64), parsed.previousLaunchProcessToken);
        assertEquals("the name is optional", "", parsed.previousLaunchGameName);

        value.getJSONObject("window").getJSONObject("previous_launch")
                .put("name", " Resident Evil 3 ");
        assertEquals("Resident Evil 3", HostGatewayClient.parseSessionObservation(value, "kid",
                "parent", 7, now + 100).previousLaunchGameName);
        value.getJSONObject("window").getJSONObject("previous_launch").put("name", 5);
        assertEquals("a non-text name is ignored, not a rejection", "",
                HostGatewayClient.parseSessionObservation(value, "kid", "parent", 7,
                        now + 100).previousLaunchGameName);

        value.getJSONObject("window").getJSONObject("previous_launch")
                .put("process_token", "short");
        assertObservationRejected(value, "inconsistent_observation_evidence", now);
        value.getJSONObject("window").remove("previous_launch");
        assertObservationRejected(value, "missing_observation_previous_launch", now);
    }

    @Test public void gameUpdateProgressIsRequiredAndBoundedOnlyWhileUpdating() throws Exception {
        long now = 1_700_000_000_000L;
        JSONObject value = observation(now, "boot-a", 2, 7);
        value.getJSONObject("game").put("state", "starting").put("id", "steam:456");
        value.put("process", new JSONObject().put("evidence", "ABSENT")
                .put("process_id", 0).put("process_token", "").put("started_unix_ms", 0));
        value.put("window", new JSONObject().put("evidence", "PENDING")
                .put("ready", false).put("target_kind", "game").put("stable_samples", 0)
                .put("reason", "game_updating")
                .put("update", new JSONObject().put("phase", "patching")
                        .put("progress", 42)));
        assertEquals(42, HostGatewayClient.parseSessionObservation(value, "kid", "parent", 7,
                now + 100).updateProgress);
        assertTrue(HostGatewayClient.parseSessionObservation(value, "kid", "parent", 7,
                now + 100).updatePatching);

        value.getJSONObject("window").getJSONObject("update").put("progress", -1);
        assertEquals("no byte counts: indeterminate", -1,
                HostGatewayClient.parseSessionObservation(value, "kid", "parent", 7,
                        now + 100).updateProgress);

        value.getJSONObject("window").getJSONObject("update").put("progress", 101);
        assertObservationRejected(value, "invalid_observation_progress", now);
        value.getJSONObject("window").remove("update");
        assertObservationRejected(value, "missing_observation_update", now);

        assertEquals(-1, HostGatewayClient.parseSessionObservation(
                observation(now, "boot-a", 2, 7), "kid", "parent", 7, now + 100).updateProgress);
    }

    @Test public void streamAndProcessStartsAreRequiredAndZeroMeansUnknown() throws Exception {
        long now = 1_700_000_000_000L;
        JSONObject value = observation(now, "boot-a", 2, 7);
        HostGatewayClient.SessionObservation unknown = HostGatewayClient.parseSessionObservation(
                value, "kid", "parent", 7, now + 100);
        assertEquals(0L, unknown.streamStartedUnixMs);
        assertEquals(0L, unknown.processStartedUnixMs);

        value.getJSONObject("correlation").put("stream_started_unix_ms", now - 3_600_000L);
        value.getJSONObject("process").put("started_unix_ms", now - 720_000L);
        HostGatewayClient.SessionObservation parsed = HostGatewayClient.parseSessionObservation(
                value, "kid", "parent", 7, now + 100);
        assertEquals(now - 3_600_000L, parsed.streamStartedUnixMs);
        assertEquals(now - 720_000L, parsed.processStartedUnixMs);

        value.getJSONObject("correlation").remove("stream_started_unix_ms");
        assertObservationRejected(value, "missing_observation_stream_started_unix_ms", now);
        value = observation(now, "boot-a", 2, 7);
        value.getJSONObject("process").remove("started_unix_ms");
        assertObservationRejected(value, "missing_observation_started_unix_ms", now);
        value = observation(now, "boot-a", 2, 7);
        value.getJSONObject("correlation").put("stream_started_unix_ms", -1);
        assertObservationRejected(value, "stale_or_malformed_observation", now);
        // A process start belongs to a verified process only.
        value = observation(now, "boot-a", 2, 7);
        value.put("process", new JSONObject().put("evidence", "PENDING")
                .put("process_id", 0).put("process_token", "").put("started_unix_ms", now));
        value.getJSONObject("window").put("evidence", "PENDING").put("ready", false);
        assertObservationRejected(value, "inconsistent_observation_evidence", now);
    }

    @Test public void streamerAppTellsUnknownFromNoApp() throws Exception {
        long now = 1_700_000_000_000L;
        JSONObject value = observation(now, "boot-a", 2, 7);
        assertEquals(42, HostGatewayClient.parseSessionObservation(
                value, "kid", "parent", 7, now + 100).streamerAppId);
        value.put("streamer", new JSONObject().put("evidence", "ABSENT").put("app_id", 0));
        assertEquals(0, HostGatewayClient.parseSessionObservation(
                value, "kid", "parent", 7, now + 100).streamerAppId);
        value.put("streamer", new JSONObject().put("evidence", "UNAVAILABLE").put("app_id", 0));
        assertEquals(SessionSnapshot.STREAMER_APP_UNKNOWN, HostGatewayClient
                .parseSessionObservation(value, "kid", "parent", 7, now + 100).streamerAppId);

        for (JSONObject inconsistent : new JSONObject[] {
                new JSONObject().put("evidence", "VERIFIED").put("app_id", 0),
                new JSONObject().put("evidence", "ABSENT").put("app_id", 7),
                new JSONObject().put("evidence", "UNAVAILABLE").put("app_id", 7),
                new JSONObject().put("evidence", "PENDING").put("app_id", 0)}) {
            value.put("streamer", inconsistent);
            assertObservationRejected(value, "inconsistent_observation_evidence", now);
        }
        value.remove("streamer");
        assertObservationRejected(value, "missing_observation_streamer", now);
    }

    @Test public void frozenSlotIsParsedAsTheActorMaySeeIt() throws Exception {
        long now = 1_700_000_000_000L;
        JSONObject value = observation(now, "boot-a", 2, 7);
        assertEquals(null, HostGatewayClient.parseSessionObservation(
                value, "kid", "parent", 7, now + 100).frozen);

        value.put("frozen", frozen(true, true, "steam:9", "child_time_expired", 5L, "kid"));
        SessionSnapshot.FrozenGame own = HostGatewayClient.parseSessionObservation(
                value, "kid", "parent", 7, now + 100).frozen;
        assertEquals("steam:9", own.gameId);
        assertEquals("steam:9", own.resumableGameId());
        assertEquals("child_time_expired", own.reason);
        assertTrue(own.own);

        // A child's view of a game outside its library: the slot is taken, nothing more.
        value.put("frozen", frozen(false, false, "", "", 0L, ""));
        SessionSnapshot.FrozenGame hidden = HostGatewayClient.parseSessionObservation(
                value, "kid", "parent", 7, now + 100).frozen;
        assertFalse(hidden.entitled);
        assertEquals("", hidden.resumableGameId());

        for (JSONObject inconsistent : new JSONObject[] {
                frozen(false, false, "steam:9", "", 0L, ""),
                frozen(false, true, "", "", 0L, ""),
                frozen(true, false, "", "user_switch", 5L, ""),
                frozen(true, false, "steam:9", "nap", 5L, ""),
                frozen(true, false, "steam:9", "user_switch", 0L, "")}) {
            value.put("frozen", inconsistent);
            assertObservationRejected(value, "inconsistent_observation_frozen", now);
        }
        value.remove("frozen");
        assertObservationRejected(value, "missing_observation_frozen", now);
    }

    @Test public void projectionHoldsTheFrozenGameOnlyFromALiveObservation() throws Exception {
        long now = 1_700_000_000_000L;
        SessionStateResolver.ProjectionOwner owner = new SessionStateResolver.ProjectionOwner(
                (level, component, event, fields) -> true, "host", "kid", "parent");
        long ordinal = owner.nextRequestOrdinal();
        JSONObject value = observation(now, "boot-a", 1, ordinal)
                .put("frozen", frozen(true, false, "steam:9", "host_sleep", 5L, ""));
        SessionSnapshot published = owner.accept(HostGatewayClient.parseSessionObservation(
                value, "kid", "parent", ordinal, now), m.snapshot(), 1_000L);
        assertEquals("steam:9", published.frozenGameId());
        assertEquals("host_sleep", published.frozenGame.reason);
        assertEquals("steam:9", owner.current(m.snapshot(),
                1_000L + SessionStateResolver.OBSERVATION_LIVENESS_MS).frozenGameId());
        assertEquals("an expired observation tells nothing", null, owner.current(m.snapshot(),
                1_001L + SessionStateResolver.OBSERVATION_LIVENESS_MS).frozenGame);

        owner.terminationBoundary(false);
        assertEquals(null, owner.current(m.snapshot(), 2_000L).frozenGame);
    }

    @Test public void quickResumeAnswersCarryTheRefusalAndTheSlotOccupant() throws Exception {
        HostGatewayClient.QuickResumeAnswer frozen = HostGatewayClient.QuickResumeAnswer.of(
                new JSONObject().put("ok", true).put("action", "game/freeze")
                        .put("result", new JSONObject().put("frozen_game_id", "steam:1")));
        assertTrue(frozen.accepted);
        assertEquals("", frozen.reason);
        assertEquals("steam:1", frozen.frozenGameId);

        HostGatewayClient.QuickResumeAnswer occupied = HostGatewayClient.QuickResumeAnswer.of(
                new JSONObject().put("ok", false).put("error", "frozen_slot_occupied")
                        .put("result", new JSONObject().put("accepted", false)
                                .put("reason", "frozen_slot_occupied")
                                .put("frozen_game_id", "steam:2")));
        assertFalse(occupied.accepted);
        assertEquals("frozen_slot_occupied", occupied.reason);
        assertEquals("steam:2", occupied.frozenGameId);

        HostGatewayClient.QuickResumeAnswer bare = HostGatewayClient.QuickResumeAnswer.of(
                new JSONObject().put("ok", false));
        assertFalse(bare.accepted);
        assertEquals("rejected", bare.reason);
    }

    private static JSONObject frozen(boolean entitled, boolean own, String gameId,
                                     String reason, long frozenAt, String actor)
            throws Exception {
        return new JSONObject().put("entitled", entitled).put("own", own)
                .put("game_id", gameId).put("reason", reason)
                .put("frozen_at_unix_ms", frozenAt).put("actor_profile_id", actor);
    }

    @Test public void missingOrWronglyTypedCorrelationIsRejected() throws Exception {
        long now = 1_700_000_000_000L;
        JSONObject value = observation(now, "boot-a", 2, 7);
        value.getJSONObject("correlation").remove("stream_session_id");
        assertObservationRejected(value, "missing_observation_stream_session_id", now);

        value = observation(now, "boot-a", 2, 7);
        value.getJSONObject("correlation").put("control_generation", "4");
        assertObservationRejected(value, "missing_observation_control_generation", now);
    }

    @Test public void providerClockSkewIsDiagnosticAndFreshnessExpiresLocally()
            throws Exception {
        long deviceWall = 1_700_000_000_000L;
        long providerWall = deviceWall - 6L * 60L * 60L * 1_000L;
        SessionStateResolver.ProjectionOwner owner = new SessionStateResolver.ProjectionOwner(
                (level, component, event, fields) -> true, "host", "kid", "parent");
        long ordinal = owner.nextRequestOrdinal();
        HostGatewayClient.SessionObservation parsed =
                HostGatewayClient.parseSessionObservation(
                        observation(providerWall, "boot-skew", 1, ordinal),
                        "kid", "parent", ordinal, deviceWall);
        assertEquals(providerWall, parsed.observedAtUnixMs);
        assertNotNull(owner.accept(parsed, m.snapshot(), 1_000L));

        SessionSnapshot locallyStale = owner.current(m.snapshot(), 7_000L);
        assertEquals("UNAVAILABLE", locallyStale.processEvidence);
        assertEquals(SessionSnapshot.ObservationState.PENDING,
                locallyStale.observationState);
        assertEquals(SessionSnapshot.ObservationState.UNAVAILABLE,
                owner.current(m.snapshot(), 16_000L).observationState);
    }

    private static JSONObject observation(long observedAt, String boot, long revision,
                                          long ordinal) throws Exception {
        return new JSONObject().put("ok", true).put("schema_version", 2)
                .put("producer", new JSONObject().put("boot_id", boot)
                        .put("revision", revision))
                .put("observed_at_unix_ms", observedAt).put("fresh_for_ms", 5_000)
                .put("state_episode", boot + ":1")
                .put("state_since_unix_ms", observedAt)
                .put("scope", new JSONObject().put("actor_profile_id", "kid")
                        .put("execution_profile_id", "parent"))
                .put("correlation", new JSONObject().put("request_ordinal", ordinal)
                        .put("operation_id", "operation-a")
                        .put("stream_session_id", "session-a")
                        .put("control_generation", 4).put("actor_lease_epoch", 5)
                        .put("stream_started_unix_ms", 0))
                .put("streamer", new JSONObject().put("evidence", "VERIFIED").put("app_id", 42))
                .put("provider", new JSONObject().put("state", "running")
                        .put("connector_evidence", "VERIFIED").put("reason", "none"))
                .put("game", new JSONObject().put("state", "running")
                        .put("evidence", "VERIFIED")
                        .put("id", "steam:123"))
                .put("process", new JSONObject().put("evidence", "VERIFIED")
                        .put("process_id", 42).put("process_token", repeat('a', 64))
                        .put("started_unix_ms", 0))
                .put("window", new JSONObject().put("evidence", "VERIFIED")
                        .put("ready", true).put("target_kind", "game")
                        .put("stable_samples", 3).put("reason", "target_window_ready"))
                .put("capabilities", new JSONObject().put("host_guide_allowed", false)
                        .put("requires_connector", true)
                        .put("start_before_stream", false))
                .put("frozen", JSONObject.NULL);
    }

    private static void assertObservationRejected(JSONObject value, String reason, long now)
            throws Exception {
        try {
            HostGatewayClient.parseSessionObservation(value, "kid", "parent", 7, now);
            throw new AssertionError("Observation was accepted");
        } catch (java.io.IOException expected) {
            assertEquals(reason, expected.getMessage());
        }
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    @Test public void identityChallengeUsesTheGatewayDomainAndAllowsBoundedAsciiNonce() {
        assertTrue(HostGatewayClient.isValidIdentityChallenge(
                "moonwaker-vibepollo-identity-v1\nchild:profile:nonce"));
        assertFalse(HostGatewayClient.isValidIdentityChallenge(
                "moonwaker-vibepollo-identity-v1:child:profile:nonce"));
        assertFalse(HostGatewayClient.isValidIdentityChallenge(
                "moonwaker-vibepollo-identity-v1\n"));
        assertTrue(HostGatewayClient.isValidIdentityChallenge(
                "moonwaker-vibepollo-identity-v1\nchild\nprofile"));
        assertFalse(HostGatewayClient.isValidIdentityChallenge(
                "moonwaker-vibepollo-identity-v1\nchild\rprofile"));
    }

    @Test public void windowsSessionResponseKeepsOnlyCoarsePollingState() throws Exception {
        HostGatewayClient.WindowsSession session = HostGatewayClient.parseWindowsSession(
                new JSONObject().put("state", "session_starting")
                        .put("reason", "none")
                        .put("attempt_id", "0123456789abcdef0123456789abcdef")
                        .put("request_id", "android-42")
                        .put("actor_profile_id", "kid")
                        .put("execution_profile_id", "parent")
                        .put("retry_after_ms", 750));

        assertEquals("session_starting", session.state);
        assertEquals("none", session.reason);
        assertEquals("0123456789abcdef0123456789abcdef", session.attemptId);
        assertEquals("android-42", session.requestId);
        assertEquals("kid", session.actorProfileId);
        assertEquals("parent", session.executionProfileId);
        assertEquals(750, session.retryAfterMs);
    }

    @Test public void windowsSessionHttpErrorsMapToCoarseAndroidReasons() {
        assertEquals("remote_sign_in_not_granted", HostGatewayClient.sessionFailure(
                new GatewayTransport.GatewayException("denied", 403)).reason);
        assertEquals("broker_unavailable", HostGatewayClient.sessionFailure(
                new GatewayTransport.GatewayException("unavailable", 503)).reason);
        assertEquals("other_user_active", HostGatewayClient.sessionFailure(
                new GatewayTransport.GatewayException("other_user_active", 409)).reason);
        assertEquals("credential_missing", HostGatewayClient.sessionFailure(
                new GatewayTransport.GatewayException("credential_missing", 409)).reason);
        assertEquals("expired", HostGatewayClient.sessionFailure(
                new GatewayTransport.GatewayException("attempt_expired", 409)).state);
        assertEquals("cancelled", HostGatewayClient.sessionFailure(
                new GatewayTransport.GatewayException("attempt_cancelled", 409)).state);
    }

    @Test public void windowsSessionCancellationPinsOriginalRequestAndAttempt() throws Exception {
        JSONObject body = HostGatewayClient.sessionCancellationBody(
                "android-42-original", "0123456789abcdef0123456789abcdef");

        assertEquals("android-42-original", body.getString("request_id"));
        assertEquals("0123456789abcdef0123456789abcdef",
                body.getString("attempt_id"));
    }

    @Test public void adaptiveNetworkDownloadTargetsEightSecondsAtEightyMegabits() {
        assertEquals(80_000_000, HostGatewayClient.adaptiveNetworkDownloadSize(
                8L * 1024 * 1024, 838_860_800L));
    }

    @Test public void adaptiveNetworkDownloadTargetsEightSecondsAtFiveHundredMegabits() {
        assertEquals(500_000_000, HostGatewayClient.adaptiveNetworkDownloadSize(
                8L * 1024 * 1024, 134_217_728L));
    }

    @Test public void adaptiveNetworkDownloadClampsFasterLinksToFiveHundredTwelveMebibytes() {
        assertEquals(512 * 1024 * 1024, HostGatewayClient.adaptiveNetworkDownloadSize(
                8L * 1024 * 1024, 67_108_864L));
    }

    @Test
    public void gameplayPermissionsAllowLaunchAndInputsWithoutServerCommands() {
        int permissions = HostGatewayClient.REQUIRED_GAMEPLAY_PERMISSIONS;
        assertEquals(0x07000000, permissions & 0x07000000);
        assertEquals(0x00001F00, permissions & 0x00001F00);
        assertEquals(0, permissions & 0x00100000);
    }

    @Test
    public void endpointForIpv4HostUsesDefaultGatewayPort() {
        assertEquals("https://192.0.2.10:8785",
                HostGatewayClient.endpointForHost("192.0.2.10"));
    }

    @Test
    public void endpointForIpv6HostAddsBrackets() {
        assertEquals("https://[2001:db8::10]:8785",
                HostGatewayClient.endpointForHost("2001:db8::10"));
    }

    @Test
    public void discordIdsAcceptOnlySnowflakeDigits() {
        assertTrue(HostGatewayClient.isDiscordId("123456789012345678"));
        assertFalse(HostGatewayClient.isDiscordId("../../shutdown"));
        assertFalse(HostGatewayClient.isDiscordId("1234"));
    }

    @Test
    public void playniteContractUsesSdkSecondsAndPreservesMetadata() throws Exception {
        JSONObject library = new JSONObject("{\"revision\":\"42\",\"api_version\":\"1\"," +
                "\"games\":[{\"id\":\"steam:289070\"," +
                "\"name\":\"Game\",\"isInstalled\":false,\"isInstalling\":true," +
                "\"hidden\":false," +
                "\"playtime\":3600,\"lastActivity\":\"2026-07-20T10:00:00Z\"," +
                "\"source\":\"Steam\",\"provider\":\"steam\"," +
                "\"providerGameId\":\"289070\",\"playniteGameId\":" +
                "\"11223344-5566-7788-99aa-bbccddeeff00\"," +
                "\"libraryKey\":\"steam\",\"libraryName\":\"Steam\"," +
                "\"capabilities\":{\"launch\":true,\"install\":false," +
                "\"uninstall\":true},\"providerCapabilities\":{" +
                "\"requiresConnector\":false,\"streamMode\":\"neutral\"," +
                "\"startBeforeStream\":true}," +
                "\"genres\":[\"Action\",\"RPG\"]," +
                "\"description\":\"Short overview\",\"playCount\":17," +
                "\"installRequiresAttention\":true," +
                "\"installAttentionReason\":\"launcher_prompt\"," +
                "\"installWindowTitle\":\"Choose install location\"," +
                "\"installLauncher\":\"steam.exe\"," +
                "\"cover\":\"cover-hash\",\"background\":\"background-hash\"," +
                "\"hero\":\"hero-hash\"}]}" );

        HostGatewayClient.PlayniteLibrary parsed =
                HostGatewayClient.parsePlayniteLibrary(library);

        assertEquals(1, parsed.games.size());
        assertEquals(3600L, parsed.games.get(0).playtimeSeconds);
        assertEquals("Steam", parsed.games.get(0).source);
        assertEquals("steam", parsed.games.get(0).provider);
        assertEquals("steam:289070", parsed.games.get(0).id);
        assertEquals("289070", parsed.games.get(0).providerGameId);
        assertEquals("steam", parsed.games.get(0).libraryKey);
        assertFalse(parsed.games.get(0).canInstall);
        assertFalse(parsed.games.get(0).requiresConnector);
        assertTrue(parsed.games.get(0).startBeforeStream);
        assertEquals("Action, RPG", parsed.games.get(0).genres);
        assertEquals("Short overview", parsed.games.get(0).description);
        assertEquals(17, parsed.games.get(0).playCount);
        assertTrue(parsed.games.get(0).installing);
        assertTrue(parsed.games.get(0).installRequiresAttention);
        assertEquals("launcher_prompt", parsed.games.get(0).installAttentionReason);
        assertEquals("Choose install location", parsed.games.get(0).installWindowTitle);
        assertEquals("steam.exe", parsed.games.get(0).installLauncher);
        assertEquals("cover-hash", parsed.games.get(0).cover);
        assertEquals("background-hash", parsed.games.get(0).background);
        assertEquals("hero-hash", parsed.games.get(0).hero);
        assertEquals("42", parsed.revision);
    }

    @Test public void epicRecordPreservesExactLegendaryAppName() throws Exception {
        HostGatewayClient.PlayniteLibrary parsed = HostGatewayClient.parsePlayniteLibrary(
                new JSONObject("{\"games\":[{\"id\":\"epic:CelesteApp\"," +
                        "\"name\":\"Celeste\",\"source\":\"Epic\"," +
                        "\"provider\":\"epic\",\"providerGameId\":\"CelesteApp\"," +
                        "\"capabilities\":{\"launch\":true,\"install\":true," +
                        "\"uninstall\":true}}]}"));

        assertEquals("epic:CelesteApp", parsed.games.get(0).id);
        assertEquals("CelesteApp", parsed.games.get(0).providerGameId);
        assertTrue(parsed.games.get(0).canLaunch);
    }

    @Test public void futureProviderUsesDeclaredCapabilitiesWithoutClientAllowlist()
            throws Exception {
        HostGatewayClient.PlayniteGame game = HostGatewayClient.parsePlayniteLibrary(
                new JSONObject("{\"games\":[{\"id\":\"gog:Some_Game\"," +
                        "\"name\":\"Some Game\",\"provider\":\"gog\"," +
                        "\"providerGameId\":\"Some_Game\"," +
                        "\"capabilities\":{\"launch\":true}," +
                        "\"providerCapabilities\":{\"requiresConnector\":false," +
                        "\"streamMode\":\"neutral\"," +
                        "\"startBeforeStream\":true}}]}")).games.get(0);

        assertEquals("gog", game.provider);
        assertTrue(game.canLaunch);
        assertFalse(game.requiresConnector);
        assertTrue(game.startBeforeStream);
    }

    @Test public void legacyExternalGuidHasNoExecutableCapabilities() throws Exception {
        HostGatewayClient.PlayniteGame game = HostGatewayClient.parsePlayniteLibrary(
                new JSONObject("{\"games\":[{\"id\":" +
                        "\"00000001-0000-0000-0000-000000000000\"," +
                        "\"name\":\"Old Steam Game\",\"source\":\"Steam\"}]}"))
                .games.get(0);

        assertEquals("steam", game.provider);
        assertFalse(game.canLaunch);
        assertFalse(game.canInstall);
        assertFalse(game.canUninstall);
    }

    @Test public void profileProjectionPreservesPlayniteConnectorState() throws Exception {
        HostGatewayClient.IntegrationProfiles profiles =
                HostGatewayClient.parseIntegrationProfiles(new JSONObject()
                        .put("profiles", new org.json.JSONArray().put(new JSONObject()
                                .put("id", "default")
                                .put("playnite_bridge_online", true)
                                .put("playnite_connector_connected", true))));

        assertTrue(profiles.find("default").playniteConnectorConnected);
    }

    @Test public void powerStatusParsesAdultDetailsAndChildWarningOnly() throws Exception {
        HostGatewayClient.PowerStatus adult = HostGatewayClient.parsePowerStatus(new JSONObject()
                .put("reboot_required", true).put("wake_from_shutdown_verified", true)
                .put("windows_update", new JSONObject().put("state", "done")
                        .put("available", 3).put("downloaded", 1)));
        assertTrue(adult.rebootRequired);
        assertTrue(adult.wakeFromShutdownVerified);
        assertEquals("done", adult.scanState);
        assertEquals(3, adult.availableUpdates);
        assertEquals(1, adult.downloadedUpdates);

        HostGatewayClient.PowerStatus child = HostGatewayClient.parsePowerStatus(
                new JSONObject().put("reboot_required", true));
        assertTrue(child.rebootRequired);
        assertFalse(child.wakeFromShutdownVerified);
        assertEquals("", child.scanState);
    }

    @Test public void childCapabilityAndManagementPermissionAreParsedTogether() throws Exception {
        HostGatewayClient.Capabilities capabilities = HostGatewayClient.parseCapabilities(
                new JSONObject().put("capabilities", new JSONObject()
                        .put("child_profiles_v1", new JSONObject().put("available", true))
                        .put("control_operations_v2",
                                new JSONObject().put("available", true))));
        assertTrue(capabilities.childProfiles);
        assertTrue(capabilities.controlOperations);
        assertFalse(capabilities.hostPlus);
        assertTrue(HostGatewayClient.parseCapabilities(new JSONObject().put("capabilities",
                new JSONObject().put("host_plus", new JSONObject().put("available", true)
                        .put("state", "plus")))).hostPlus);
        assertFalse(capabilities.captures);
        assertTrue(HostGatewayClient.parseCapabilities(new JSONObject().put("capabilities",
                new JSONObject().put("captures", new JSONObject().put("available", true))))
                .captures);
        // A host before the abandon endpoint and the close refusal reasons is refused.
        assertFalse(HostGatewayClient.parseCapabilities(new JSONObject().put("capabilities",
                new JSONObject().put("control_operations_v1",
                        new JSONObject().put("available", true)))).controlOperations);

        HostGatewayClient.IntegrationProfiles profiles =
                HostGatewayClient.parseIntegrationProfiles(new JSONObject()
                        .put("profiles", new org.json.JSONArray().put(new JSONObject()
                                .put("id", "parent")
                                .put("own_children_count", 2)
                                .put("permissions", new JSONObject()
                                        .put("manage_children", true)))));
        assertTrue(profiles.find("parent").manageChildren);
        assertEquals(2, profiles.find("parent").ownChildrenCount);
    }

    @Test public void setupFactsAreParsedAndAbsentForChildProfiles() throws Exception {
        HostGatewayClient.SetupFacts facts = HostGatewayClient.parseCapabilities(
                new JSONObject().put("setup", new JSONObject()
                        .put("steam", new JSONObject().put("api_key_saved", true)
                                .put("steam_id_available", JSONObject.NULL))
                        .put("epic", new JSONObject().put("signed_in", false)
                                .put("display_name", "Basia"))
                        .put("discord", new JSONObject().put("state", "linked")))).setup;
        assertEquals(Boolean.TRUE, facts.steamApiKeySaved);
        assertNull(facts.steamIdAvailable);
        assertEquals(Boolean.FALSE, facts.epicSignedIn);
        assertEquals("Basia", facts.epicDisplayName);
        assertEquals("linked", facts.discordState);
        assertNull(HostGatewayClient.parseCapabilities(new JSONObject()).setup);
        assertEquals("unknown", HostGatewayClient.parseCapabilities(new JSONObject()
                .put("setup", new JSONObject().put("discord",
                        new JSONObject().put("state", "bogus")))).setup.discordState);
    }

    @Test public void missingControlOperationCapabilityFailsClosed() {
        HostGatewayClient.Capabilities capabilities = HostGatewayClient.parseCapabilities(
                new JSONObject());

        assertFalse(capabilities.controlOperations);
    }

    @Test public void childProfileKeepsActorPolicyMetadataWithoutShowingOpaqueId()
            throws Exception {
        HostGatewayClient.IntegrationProfiles profiles =
                HostGatewayClient.parseIntegrationProfiles(new JSONObject().put("profiles",
                        new org.json.JSONArray()
                                .put(new JSONObject().put("id", "parent")
                                        .put("name", "Parent"))
                                .put(new JSONObject().put("id", "child-1")
                                        .put("kind", "child")
                                        .put("parent_profile_id", "parent")
                                        .put("execution_profile_id", "exec-child-1")
                                        .put("avatar_id", "builtin:thumbs-kai")
                                        .put("enabled", false)
                                        .put("policy_revision", 17)
                                        .put("usage_revision", 21)
                                        .put("remaining_daily_seconds", 900)
                                        .put("playable_now_seconds", 600)
                                        .put("next_allowed_at", "2026-09-05T20:00:00Z")
                                        .put("server_time", "2026-09-05T19:50:00Z")
                                        .put("reason", "outside_schedule")
                                        .put("permissions", new JSONObject()
                                                .put("use_profile", true)
                                                .put("remote_sign_in", false)
                                                .put("manage_children", false)))));

        HostGatewayClient.IntegrationProfile child = profiles.find("child-1");
        assertTrue(child.isChild());
        assertEquals("", child.name);
        assertEquals("parent", child.parentProfileId);
        assertEquals("exec-child-1", child.executionProfileId);
        assertEquals("builtin:thumbs-kai", child.avatarId);
        assertFalse(child.enabled);
        assertEquals(17L, child.policyRevision);
        assertEquals(21L, child.usageRevision);
        assertEquals(900L, child.remainingDailySeconds);
        assertEquals(600L, child.playableNowSeconds);
        assertEquals("outside_schedule", child.reason);
        assertTrue(child.useProfile);
        assertFalse(child.remoteSignIn);
        assertFalse(child.manageChildren);
    }

    @Test public void childGameIdentityAcceptsRawGuidOnlyWithCanonicalPlaynitePrefix() {
        String guid = "11223344-5566-7788-99aa-bbccddeeff00";
        assertEquals("playnite:11223344-5566-7788-99aa-bbccddeeff00",
                ConsoleActivity.canonicalChildGameId(guid,
                        "11223344-5566-7788-99AA-BBCCDDEEFF00"));
        assertTrue(ConsoleActivity.childGameIdsMatch(guid,
                "playnite:11223344-5566-7788-99AA-BBCCDDEEFF00"));
        assertFalse(ConsoleActivity.childGameIdsMatch(guid,
                "playnite:11223344-5566-7788-99aa-bbccddeeff01"));
        assertFalse(ConsoleActivity.childGameIdsMatch("steam:123", "steam:999"));
    }

    @Test public void childDraftSerializerSendsAllSevenDaysAndKeepsGrantOutsideDraft()
            throws Exception {
        List<ConsoleChildProfileEditor.Day> days = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            days.add(new ConsoleChildProfileEditor.Day(index == 1,
                    index == 1 ? 600 : 0, index == 1 ? 1200 : 1440,
                    index == 1 ? 900 : 0));
        }
        ConsoleChildProfileEditor.Draft draft = new ConsoleChildProfileEditor.Draft(
                "", "Child", "builtin:thumbs-kai", true, days, "12", Collections.emptySet(),
                Collections.emptySet(), 4, true, true, false, true);

        JSONObject serialized = HostGatewayClient.childDraftBody(draft);
        JSONObject weekdays = serialized.getJSONObject("schedule").getJSONObject("weekdays");
        assertEquals(7, weekdays.length());
        assertEquals(600, weekdays.getJSONObject("tue").getInt("start_minute"));
        assertEquals(900, weekdays.getJSONObject("tue").getInt("daily_limit_seconds"));
        assertEquals(1440, weekdays.getJSONObject("mon").getInt("end_minute"));
        assertEquals("builtin:thumbs-kai", serialized.getString("avatar_id"));
        assertFalse(serialized.has("allowed_game_keys"));
        assertTrue(serialized.getBoolean("game_mode"));
        assertFalse(serialized.getBoolean("game_mode_windows"));
        assertTrue(serialized.getBoolean("game_mode_mute"));
        assertEquals("12", serialized.getString("age_limit"));
        assertTrue(draft.grantCurrentDevice);
    }

    @Test public void childSharingSerializerPreservesEmptySelectionAndRevision() throws Exception {
        JSONObject request = HostGatewayClient.childSharingRequestBody("parent", "auth-1",
                "11223344-5566-7788-99aa-bbccddeeff00", Collections.emptyList(), 9, null,
                "req-1");

        assertEquals("parent", request.getString("parent_profile_id"));
        assertEquals("auth-1", request.getString("authorization_id"));
        assertEquals(0, request.getJSONArray("selected_child_ids").length());
        assertEquals(9, request.getInt("expected_revision"));
        assertEquals("req-1", request.getString("request_id"));
        // Shown unrated: an explicit null, not a missing key.
        assertTrue(request.has("expected_min_age"));
        assertTrue(request.isNull("expected_min_age"));
    }

    @Test public void childSharingSendsBackTheGatewaysMinimumAge() throws Exception {
        // 3.4a: sharing/get's top-level min_age goes back unchanged, even when the shown rating
        // is one this TV cannot read.
        HostGatewayClient.ChildSharing sharing = HostGatewayClient.parseChildSharing(
                new JSONObject("{\"min_age\":13,\"ageRating\":{\"system\":\"cero\","
                        + "\"label\":\"B\",\"minAge\":12},\"children\":[]}"));
        assertNull(sharing.ageRating);
        JSONObject request = HostGatewayClient.childSharingRequestBody("parent", "auth-1",
                "steam:1145360", Collections.singletonList("ola"), 9, sharing.minAge, "req-1");

        assertEquals(13, request.getInt("expected_min_age"));
        assertEquals("ola", request.getJSONArray("selected_child_ids").getString(0));
    }

    @Test public void pairingSelectsTheOnlyGrantedProfile() throws Exception {
        HostGatewayClient.IntegrationProfiles profiles =
                HostGatewayClient.parseIntegrationProfiles(new JSONObject()
                        .put("profiles", new org.json.JSONArray().put(new JSONObject()
                                .put("id", "p-basia")
                                .put("permissions", new JSONObject()
                                        .put("use_profile", true)))));

        assertEquals("p-basia", HostGatewayClient.pairingProfileId(profiles));
    }

    @Test public void pairingUsesARealProfileWhileMultipleProfilesAwaitSelection() throws Exception {
        HostGatewayClient.IntegrationProfiles profiles =
                HostGatewayClient.parseIntegrationProfiles(new JSONObject()
                        .put("profiles", new org.json.JSONArray()
                                .put(new JSONObject().put("id", "p-basia"))
                                .put(new JSONObject().put("id", "p-guest"))));

        assertEquals("p-basia", HostGatewayClient.pairingProfileId(profiles));
    }

    @Test public void selectorProjectsOnlyUsableProfilesAndHidesForOne() throws Exception {
        org.json.JSONArray values = new org.json.JSONArray()
                .put(new JSONObject().put("id", "denied").put("name", "Denied")
                        .put("permissions", new JSONObject().put("use_profile", false)))
                .put(new JSONObject().put("id", "Basia").put("name", "Basia")
                        .put("permissions", new JSONObject()
                                .put("use_profile", true).put("remote_sign_in", true))
                        .put("pin_required", true)
                        .put("session_state", "unlocked")
                        .put("remote_sign_in_state", "available"))
                .put(new JSONObject().put("id", "Gry").put("name", "Gry")
                        .put("permissions", new JSONObject().put("use_profile", true)));
        HostGatewayClient.IntegrationProfiles parsed =
                HostGatewayClient.parseIntegrationProfiles(
                        new JSONObject().put("profiles", values));

        HostGatewayStore.ProfileSelection multiple = HostGatewayStore.projectProfiles(
                parsed.profiles, "Gry", "Basia");
        assertEquals(2, multiple.profiles.size());
        assertEquals("Gry", multiple.selected.id);
        assertTrue(multiple.showSelector);
        assertTrue(parsed.find("Basia").remoteSignIn);
        assertTrue(parsed.find("Basia").pinRequired);
        assertEquals("unlocked", parsed.find("Basia").sessionState);
        assertEquals("available", parsed.find("Basia").remoteSignInState);

        HostGatewayStore.ProfileSelection single = HostGatewayStore.projectProfiles(
                Collections.singletonList(parsed.find("Basia")), "Basia", "");
        assertFalse(single.showSelector);
    }

    @Test public void gameStartRejectionPreservesTopLevelAndNestedReasons() throws Exception {
        assertEquals("launcher_interaction_required",
                HostGatewayClient.gameStartRejectionReason(new JSONObject()
                        .put("accepted", false)
                        .put("reason", "launcher_interaction_required")));
        assertEquals("host_session_locked",
                HostGatewayClient.gameStartRejectionReason(new JSONObject()
                        .put("result", new JSONObject()
                                .put("accepted", false)
                                .put("reason", "host_session_locked"))));
    }

    @Test public void acceptedGameStartHasNoRejectionReason() throws Exception {
        assertEquals("", HostGatewayClient.gameStartRejectionReason(new JSONObject()
                .put("accepted", true)
                .put("result", new JSONObject().put("accepted", true))));
    }

    @Test public void acceptedGameStartNamesTheBridgeBootThatAcceptedIt() throws Exception {
        assertEquals("0a1b2c", HostGatewayClient.gameStartBootId(new JSONObject()
                .put("result", new JSONObject().put("accepted", true).put("boot_id", "0a1b2c"))));
        try {
            HostGatewayClient.gameStartBootId(new JSONObject()
                    .put("result", new JSONObject().put("accepted", true)));
            fail("an accepted start without its boot is unbound");
        } catch (java.io.IOException expected) {
            assertEquals("game_start_unbound", expected.getMessage());
        }
    }
}
