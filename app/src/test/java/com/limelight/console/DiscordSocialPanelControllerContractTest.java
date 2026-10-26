package com.limelight.console;

import com.limelight.SourceText;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class DiscordSocialPanelControllerContractTest {
    @Test public void staleVoiceCallbacksCannotUpdateTheConsoleIndicator() throws Exception {
        String source = source();
        assertVoiceUpdateFollowsGenerationGuard(source, "private void join(",
                "    private void refreshVoice(");
        assertVoiceUpdateFollowsGenerationGuard(source, "private void refreshVoice(",
                "    private HostGatewayClient.DiscordChannel findVoiceChannel(");
        assertVoiceUpdateFollowsGenerationGuard(source, "private void voiceAction(",
                "    private DiscordCommunityPresentation.Destination joinedVoiceDestination(");
    }

    @Test public void discordOptionsLiveInSettingsAndBackReturnsToCommunity() throws Exception {
        String activity = SourceText.read(Paths.get(
                "src/main/java/com/limelight/console/ConsoleActivity.java"));

        assertTrue(source().contains("public void onOptions() { ui.openDiscordSettings(); }"));
        assertTrue(activity.contains(
                "showSettings(SETTINGS_DISCORD, ConsoleActivity.this::showDiscordPanel)"));
        assertTrue(activity.contains("discordSettings.hostRows("));
    }

    private static void assertVoiceUpdateFollowsGenerationGuard(String source,
                                                                  String startMarker,
                                                                  String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0 && end > start);
        String body = source.substring(start, end);
        int guard = body.indexOf("if (!visible || expected != generation) return;");
        int update = body.indexOf("ui.voiceChanged(");
        assertTrue(guard >= 0 && update > guard);
    }

    private static String source() throws Exception {
        Path path = Paths.get("src/main/java/com/limelight/console/"
                + "DiscordSocialPanelController.java");
        return SourceText.read(path);
    }
}
