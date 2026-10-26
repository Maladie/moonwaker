package com.limelight;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameStreamingAutopilotCalibrationContractTest {
    @Test public void runtimeCandidateAndStructuredStatsFeedCalibration() throws IOException {
        String game = source("src/main/java/com/limelight/Game.java");
        String renderer = source(
                "src/main/java/com/limelight/binding/video/MediaCodecDecoderRenderer.java");
        String initialize = between(game,
                "private boolean initializeStreamingAutopilotCalibration(",
                "private void onStreamingAutopilotVideoStats(");

        assertTrue(initialize.contains("prefConfig.width = width"));
        assertTrue(initialize.contains("prefConfig.height = height"));
        assertTrue(initialize.contains("prefConfig.fps = fps"));
        assertTrue(initialize.contains("prefConfig.bitrate = runtimeBitrateKbps"));
        assertTrue(game.contains("renderer.setVideoStatsListener("));
        assertTrue(renderer.contains("new StreamingAutopilotCalibration.Sample("));
        assertTrue(renderer.contains("rttInfo < 0 ? 0"));
        assertTrue(renderer.contains("framesWithHostProcessingLatency / received"));
    }

    @Test public void playniteWaitsForGameReadyAndVisibleStream() throws IOException {
        String game = source("src/main/java/com/limelight/Game.java");
        String arm = between(game,
                "private void maybeArmStreamingAutopilotCalibration()",
                "private void showAutopilotHud(");
        String transition = between(game,
                "private void applyTransitionSnapshot(",
                "static boolean shouldShowTransitionOverlay(");

        assertTrue(arm.contains("!streamEverRevealed"));
        assertTrue(arm.contains("playniteGame && !autopilotGameReadySeen"));
        assertTrue(transition.contains("LaunchTransitionState.GAME_READY"));
        assertTrue(transition.contains("autopilotGameReadySeen = true"));
        assertTrue(transition.contains("maybeArmStreamingAutopilotCalibration()"));
        assertFalse(arm.contains("AlertDialog"));
        assertTrue(arm.contains("showAutopilotHud(calibration"));
        // The readout is its own view: the stats overlay is never taken over.
        assertFalse(game.contains("autopilotPreviousPerf"));
    }

    @Test public void resultRequiresConsentAndCancellationNeverPersists() throws IOException {
        String game = source("src/main/java/com/limelight/Game.java");
        String result = between(game,
                "private void showStreamingAutopilotCalibrationResult(",
                "private void finishAutopilotResult(");
        String cancel = between(game,
                "private void cancelStreamingAutopilotCalibration()",
                "private LaunchTransitionSpec readTransitionSpec()");

        String overlay = source("src/main/java/com/limelight/ui/overlay/OverlayMenuView.java");
        String finish = between(game, "private void finishAutopilotResult(",
                "private void startStreamingAutopilotTest()");

        // The result is the overlay's own page; its buttons wait before they act.
        assertTrue(overlay.contains("RESULT_ACTION_DELAY_MS = 2_000L"));
        assertTrue(overlay.contains("SystemClock.uptimeMillis() < promptActionsAtMs"));
        assertTrue(result.contains("completed.adjustedSettings(result)"));
        assertTrue(result.contains("showPromptMidGame(new OverlayMenuView.Prompt("));
        assertTrue(result.contains("setInputGrabState(false)"));
        assertTrue(result.contains("controllerHandler.releaseAllControllerInputsAndSuppress()"));
        assertTrue(result.contains("setInputGrabState(promptRestoreGrab)"));
        // Whether input reaches the host again is LaunchTransitionController's, as of now.
        assertTrue(result.contains("setInputSuppressed(isTransitionInputBlocked())"));
        // Discarding (Back, or the menu closing unanswered) keeps nothing.
        assertTrue(finish.indexOf("OverlayMenuView.PROMPT_DISMISSED")
                < finish.indexOf("AppPreferences.applyStreamSettings"));
        assertTrue(overlay.contains("actionListener.onPromptChoice(PROMPT_DISMISSED)"));
        assertFalse(cancel.contains("AppPreferences"));
        assertTrue(game.contains("protected void onStop()"));
        assertTrue(game.contains("public void connectionTerminated(final int errorCode)"));
        assertTrue(cancel.contains("hideAutopilotHud()"));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        return source.substring(from, source.indexOf(end, from));
    }

    private static String source(String path) throws IOException {
        Path source = Paths.get(path);
        return SourceText.read(source);
    }
}
