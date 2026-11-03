package com.limelight;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static com.limelight.SourceText.assertOrdered;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Every stream failure ends somewhere usable: the cover's Retry/Cancel or a kept session. */
public class StreamActivityRecoveryContractTest {
    private static final String GAME = SourceText.main("Game.java");

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        assertTrue(start, from >= 0);
        return source.substring(from, source.indexOf(end, from));
    }

    /** B-1: a failed start shows the host's text on the cover; no modal dialog finishes Game. */
    @Test public void stageFailureGoesToTheCoverError() {
        String failed = between(GAME, "private void stageFailed(final String stage,",
                "private static int streamStageCode(");
        assertOrdered(failed, "if (failureHandled || isFinishing() || isDestroyed()) return;",
                "transitionController.error(transitionSpec.id, text);");
        assertFalse(failed.contains("finish"));
        assertFalse(GAME.contains("Dialog.displayDialog("));
        assertFalse(GAME.contains("testClientConnectivity"));
    }

    /** B-2: a lost transport keeps the session for Play, revealed or not, with a notice. */
    @Test public void lostTransportKeepsTheSessionForPlay() {
        String terminated = between(GAME, "public void connectionTerminated(",
                "private void keepSessionForReconnect(");
        // R2-3: the user's close (read on the native thread) is never kept for reconnect.
        assertTrue(GAME.contains("private volatile boolean userInitiatedDisconnect"));
        assertOrdered(terminated, "boolean lost = errorCode != MoonBridge.ML_ERROR_GRACEFUL_TERMINATION",
                "if (lost || hasOwnRetainedSession() && !userInitiatedDisconnect) {",
                "keepSessionForReconnect(lost)",
                // R2-2: a destroyed presentation has no cover left to await.
                "if (failureHandled || isDestroyed()) return;", "awaitCover(");
        String keep = between(GAME, "private void keepSessionForReconnect(",
                "private void onDecoderFailed(");
        assertOrdered(keep, "if (failureHandled || userInitiatedDisconnect) return;",
                "boolean wasParked = backgroundStreamParked;", "backgroundStreamParked = false;",
                "saveReconnect(true);",
                "new SessionEvent.TransportLost(runtime, streamSessionId)",
                "if (notice && !wasParked) {",
                "R.string.stream_transport_lost_notice", "stopConnection(() -> finish());");
    }

    /**
     * V-1: only an unrevealed start's provider cleanup ends at the cover's error; after a
     * reveal the cover cannot return, so a lost transport keeps the session as above.
     */
    @Test public void providerCleanupCoverErrorOnlyBeforeReveal() {
        String terminated = between(GAME, "public void connectionTerminated(",
                "private void keepSessionForReconnect(");
        String cleanup = between(terminated, "coordinator.providerCleanupRequested()",
                "boolean lost = ");
        assertOrdered(cleanup, "&& !streamEverRevealed", "transitionController.error(", "return;");
    }

    /** B-8: a decoder failure never reaches the native thread; one rebuild, then a lost stream. */
    @Test public void decoderFailureRebuildsOnceThenKeepsTheSession() {
        String renderer = SourceText.main("binding/video/MediaCodecDecoderRenderer.java");
        String submit = between(renderer, "public int submitDecodeUnit(",
                "private int decodeUnit(");
        assertOrdered(submit, "try {", "catch (RuntimeException e) {", "fail(e);",
                "return MoonBridge.DR_OK;");
        assertOrdered(between(renderer, "public void doFrame(", "private void renderDueFrame("),
                "catch (RuntimeException e) {", "fail(e);", "return;",
                "postFrameCallback(this)");
        assertTrue(between(renderer, "rendererThread = new Thread()", "private void renderLoop()")
                .contains("fail(e);"));
        String failed = between(GAME, "private void onDecoderFailed(",
                "public void connectionStatusUpdate(");
        // R2-1: the failed codec leaves the Surface before its replacement configures on it.
        assertOrdered(failed, "if (failed != decoderRenderer", "if (decoderRebuilt) {",
                "keepSessionForReconnect(true);", "decoderRebuilt = true;",
                "if (!runtime.surfaceLost(attachment)) {", "keepSessionForReconnect(true);",
                "decoderDetachedBySurfaceLoss = true;", "restoreParkedStream(");
        // R2-1: after a rebuild no new presentation reconnects (and rebuilds) in a loop.
        assertOrdered(between(GAME, "private void reconnectRetainedStream() {",
                        "private void closeStreamWithPrivacy("),
                "if (decoderRebuilt) {", "keepSessionForReconnect(true);", "return;",
                "new SessionEvent.ReconnectSaved(");
        // R2-5: an invalid Surface during codec recovery fails the decoder, never freezes it.
        String recovery = between(renderer, "private boolean doCodecRecoveryIfRequired(",
                "public void doFrame(");
        assertFalse(recovery.contains("stopping = true;"));
        assertEquals(4, recovery.split("throw new RendererException\\(this, e\\);", -1).length - 1);
    }

    /** R2-2: a close after onDestroy (TV Home during a Suspend) closes without the cover. */
    @Test public void closeAfterDestroyNeverTouchesTheCover() {
        String close = between(GAME, "private void closeStreamWithPrivacy(",
                "private void closeConnection(");
        assertOrdered(close, "userInitiatedDisconnect = true;",
                "if (isDestroyed() || consoleLoadingView == null) {",
                "closeConnection(() -> { });", "return;", "awaitCover(");
    }

    /** B-10: any runtime failure of the start thread fails the stage, never the process. */
    @Test public void startThreadFailsTheStageOnAnyRuntimeException() {
        String connection = SourceText.main("nvstream/NvConnection.java");
        String start = between(connection, "public void start(final AudioRenderer",
                "startGate.acquire();");
        assertOrdered(start, "catch (RuntimeException e) {",
                "context.connListener.stageFailed(appName, 0, 0);");
        assertFalse(connection.contains("controlBinding == null) {"));
    }

    /** B-3, B-9: no external quit broadcast and no upstream quit gesture bypass the table. */
    @Test public void noQuitPathBypassesTheOrchestrator() {
        String manifest = SourceText.module("src/main/AndroidManifest.xml");
        assertFalse(manifest.contains("StreamControlReceiver"));
        assertFalse(manifest.contains("ShortcutTrampoline"));
        assertFalse(manifest.contains("QuickLaunchShortcutPicker"));
        assertFalse(GAME.contains("registerReceiver("));
        assertFalse(GAME.contains("case KeyEvent.KEYCODE_Q:"));
        String controller = SourceText.main("binding/input/ControllerHandler.java");
        assertFalse(controller.contains("pendingExit"));
        assertFalse(controller.contains("activityContext.finish()"));
        // B-15: a custom command's disconnect leaves the stream as Home does.
        assertTrue(between(GAME, "case CustomCommand.POST_ACTION_DISCONNECT:",
                "case CustomCommand.POST_ACTION_QUIT:")
                .contains("runNavigationIntent(SessionOrchestrator.UserIntent.HOME)"));
    }

    /** B-7: a surface of a Game that finished before taking it is ignored, never thrown on. */
    @Test public void surfaceCallbacksOfAFinishingGameDoNotThrow() {
        assertFalse(between(GAME, "public void surfaceChanged(", "private void startConnectionIfReady(")
                .contains("throw "));
        assertFalse(between(GAME, "public void surfaceDestroyed(", "public void mouseMove(")
                .contains("throw "));
    }

    /** B-14: a notification without a reconnect record opens the console, not a bare Game. */
    @Test public void notificationWithoutRecordOpensTheConsole() {
        String service = SourceText.main("stream/BackgroundStreamService.java");
        assertOrdered(service, "if (resume == null) {",
                "resume = new Intent(this, ConsoleActivity.class);");
        assertFalse(service.contains("Game.class"));
    }

    /** B-13, C-18: MoonWaker talks only to its LAN host; no third-party probe or STUN. */
    @Test public void noThirdPartyNetworkProbe() throws IOException {
        try (Stream<Path> files = Files.walk(SourceText.MAIN_JAVA)) {
            long probes = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> SourceText.read(p).contains("moonlight-stream.org"))
                    .count();
            assertEquals(0, probes);
        }
    }
}
