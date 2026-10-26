package com.limelight.console;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Display;

import com.limelight.R;
import com.limelight.gateway.GatewayConnection;
import com.limelight.gateway.GatewayTransport;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.preferences.AppPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.stream.ClientStreamCapabilitiesProbe;
import com.limelight.stream.HostStreamCapabilitiesProbe;
import com.limelight.stream.StreamingAutopilot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Synchronous orchestration for streaming capability and network analysis, and the per-game
 * history of stream quality that later advice is drawn from.
 */
public final class StreamingAutopilotController {
    private StreamingAutopilotController() { }

    public enum NetworkStatus {
        MEASURED,
        UNAVAILABLE,
        FAILED,
        TOO_SLOW
    }

    public static final class NetworkSample {
        public final long bytes;
        public final long elapsedNanos;

        private NetworkSample(long bytes, long elapsedNanos) {
            this.bytes = bytes;
            this.elapsedNanos = elapsedNanos;
        }
    }

    public static final class Analysis {
        public final StreamingAutopilot.Capabilities clientCapabilities;
        public final HostStreamCapabilitiesProbe.Result host;
        public final NetworkStatus networkStatus;
        public final Optional<NetworkSample> networkSample;
        public final OptionalLong goodputKbps;
        public final OptionalInt safeBitrateKbps;
        public final Optional<StreamingAutopilot.Recommendation> recommendation;
        /** BALANCED (the recommendation) first; empty without a recommendation. */
        public final List<StreamingAutopilot.Variant> variants;

        private Analysis(StreamingAutopilot.Capabilities clientCapabilities,
                         HostStreamCapabilitiesProbe.Result host,
                         NetworkEvaluation network) {
            this.clientCapabilities = clientCapabilities;
            this.host = host;
            networkStatus = network.status;
            networkSample = network.sample;
            goodputKbps = network.goodputKbps;
            safeBitrateKbps = network.safeBitrateKbps;
            recommendation = network.recommendation;
            variants = network.variants;
        }
    }

    public static Analysis analyze(Context context, Display display, String glRenderer,
                                   NvHTTP http, String serverInfo,
                                   GatewayConnection gatewayConnection)
            throws IOException, XmlPullParserException {
        StreamingAutopilot.Capabilities client =
                ClientStreamCapabilitiesProbe.probe(context, display, glRenderer);
        HostStreamCapabilitiesProbe.Result host =
                HostStreamCapabilitiesProbe.probe(http, serverInfo);
        PreferenceConfiguration.FormatOption format =
                PreferenceConfiguration.readPreferences(context).videoFormat;

        NetworkEvaluation network;
        if (gatewayConnection == null) {
            network = combine(client, host.capabilities, format,
                    NetworkStatus.UNAVAILABLE, 0, 0);
        } else {
            try {
                GatewayTransport.NetworkDownloadSample sample =
                        new HostGatewayClient().measureAdaptiveNetworkDownload(gatewayConnection);
                network = combine(client, host.capabilities, format, NetworkStatus.MEASURED,
                        sample.bytes, sample.elapsedNanos);
            } catch (IOException e) {
                network = combine(client, host.capabilities, format,
                        NetworkStatus.FAILED, 0, 0);
            }
        }
        return new Analysis(client, host, network);
    }

    public static void applyGlobal(Context context, StreamingAutopilot.Variant variant) {
        PreferenceConfiguration.applyStreamSettings(context, variant.width, variant.height,
                variant.fps, variant.bitrateKbps);
    }

    public static void applyForApp(Context context, String appKey,
                                   StreamingAutopilot.Variant variant) {
        AppPreferences.applyStreamSettings(context, appKey, variant.width, variant.height,
                variant.fps, variant.bitrateKbps);
    }

    public static int variantName(StreamingAutopilot.VariantKind kind) {
        switch (kind) {
            case SHARPNESS: return R.string.console_autopilot_variant_sharpness;
            case SMOOTHNESS: return R.string.console_autopilot_variant_smoothness;
            case CLEAN: return R.string.console_autopilot_variant_clean;
            case STABLE: return R.string.console_autopilot_variant_stable;
            default: return R.string.console_autopilot_variant_balanced;
        }
    }

    /** What the variant trades for, one line. */
    public static int variantTrade(StreamingAutopilot.VariantKind kind) {
        switch (kind) {
            case SHARPNESS: return R.string.console_autopilot_variant_desc_sharpness;
            case SMOOTHNESS: return R.string.console_autopilot_variant_desc_smoothness;
            case CLEAN: return R.string.console_autopilot_variant_desc_clean;
            case STABLE: return R.string.console_autopilot_variant_desc_stable;
            default: return R.string.console_autopilot_variant_desc_balanced;
        }
    }

    /** The variant's line icon in the stream menu (mockup O3). */
    public static int variantIcon(StreamingAutopilot.VariantKind kind) {
        switch (kind) {
            case SHARPNESS: return R.drawable.ic_overlay_hdr;
            case SMOOTHNESS: return R.drawable.ic_overlay_up;
            case CLEAN: return R.drawable.ic_overlay_check;
            default: return R.drawable.ic_overlay_perf;
        }
    }

    /** The share of the safe budget a bitrate uses, in whole percent. */
    public static int loadPercent(int bitrateKbps, int budgetKbps) {
        return Math.round(bitrateKbps * 100f / budgetKbps);
    }

    /** Each host's last measured safe budget, so a stream can offer the analysis' variants. */
    static final String BUDGET_FILE = "StreamingAutopilotBudgets";

    /** Keeps a measured analysis' budget for its host; others leave the last one in place. */
    static void rememberBudget(Context context, String hostUuid, Analysis analysis) {
        if (analysis.networkStatus != NetworkStatus.MEASURED
                || !analysis.safeBitrateKbps.isPresent()) return;
        context.getSharedPreferences(BUDGET_FILE, Context.MODE_PRIVATE).edit()
                .putInt(hostUuid, analysis.safeBitrateKbps.getAsInt()).apply();
    }

    public static OptionalInt lastBudgetKbps(Context context, String hostUuid) {
        SharedPreferences prefs = context.getSharedPreferences(BUDGET_FILE, Context.MODE_PRIVATE);
        return hostUuid != null && prefs.contains(hostUuid)
                ? OptionalInt.of(prefs.getInt(hostUuid, 0)) : OptionalInt.empty();
    }

    /** Playnite genres where aiming with the pad's gyro pays off (owner, 2026-10-23). */
    static boolean proposesGyro(String genres) {
        String text = genres == null ? "" : genres.toLowerCase(java.util.Locale.ROOT);
        return text.contains("shooter") || text.contains("action")
                || text.contains("third-person") || text.contains("third person");
    }

    /** Recent stream summaries, newest last, per game settings key (which names the host). */
    static final String SESSION_HISTORY_FILE = "StreamingAutopilotSessions";

    public static void recordSession(Context context, String settingsKey,
                                     StreamingAutopilot.SessionSummary summary) {
        SharedPreferences prefs = sessionHistory(context);
        prefs.edit().putString(settingsKey,
                appendSession(prefs.getString(settingsKey, null), summary)).apply();
    }

    public static Optional<StreamingAutopilot.SessionAdvice> sessionAdvice(
            Context context, String settingsKey) {
        return StreamingAutopilot.adviseAfterSessions(
                parseSessions(sessionHistory(context).getString(settingsKey, null)));
    }

    /** Writes the advice into the game's own settings; the evidence behind it is spent. */
    public static void acceptSessionAdvice(Context context, String settingsKey,
                                           StreamingAutopilot.SessionAdvice advice) {
        AppPreferences.applyStreamSettings(context, settingsKey, advice.width, advice.height,
                advice.fps, advice.bitrateKbps);
        forgetSessions(context, settingsKey);
    }

    /** Dismissing advice, or changing the game's settings, starts the evidence over. */
    public static void forgetSessions(Context context, String settingsKey) {
        sessionHistory(context).edit().remove(settingsKey).apply();
    }

    private static SharedPreferences sessionHistory(Context context) {
        return context.getSharedPreferences(SESSION_HISTORY_FILE, Context.MODE_PRIVATE);
    }

    static String appendSession(String stored, StreamingAutopilot.SessionSummary summary) {
        List<StreamingAutopilot.SessionSummary> sessions = parseSessions(stored);
        sessions.add(summary);
        JSONArray array = new JSONArray();
        for (StreamingAutopilot.SessionSummary session : sessions.subList(
                Math.max(0, sessions.size() - StreamingAutopilot.SESSION_HISTORY_LIMIT),
                sessions.size())) {
            try {
                array.put(new JSONObject()
                        .put("width", session.width).put("height", session.height)
                        .put("fps", session.fps).put("bitrateKbps", session.bitrateKbps)
                        .put("measuredMs", session.measuredMs)
                        .put("receivedFps", session.receivedFps)
                        .put("renderedFps", session.renderedFps)
                        .put("frameLossPercent", session.frameLossPercent)
                        .put("rttMs", session.rttMs)
                        .put("rttVarianceMs", session.rttVarianceMs)
                        .put("decoderLatencyMs", session.decoderLatencyMs)
                        .put("actualBitrateKbps", session.actualBitrateKbps));
            } catch (JSONException e) {
                throw new IllegalArgumentException("Session summary is not finite", e);
            }
        }
        return array.toString();
    }

    /** Unreadable history is no evidence. */
    static List<StreamingAutopilot.SessionSummary> parseSessions(String stored) {
        List<StreamingAutopilot.SessionSummary> sessions = new ArrayList<>();
        if (stored == null) return sessions;
        try {
            JSONArray array = new JSONArray(stored);
            for (int i = 0; i < array.length(); i++) {
                JSONObject session = array.getJSONObject(i);
                sessions.add(new StreamingAutopilot.SessionSummary(
                        session.getInt("width"), session.getInt("height"),
                        session.getInt("fps"), session.getInt("bitrateKbps"),
                        session.getLong("measuredMs"), session.getDouble("receivedFps"),
                        session.getDouble("renderedFps"),
                        session.getDouble("frameLossPercent"), session.getDouble("rttMs"),
                        session.getDouble("rttVarianceMs"),
                        session.getDouble("decoderLatencyMs"),
                        session.getInt("actualBitrateKbps")));
            }
        } catch (JSONException e) {
            sessions.clear();
        }
        return sessions;
    }

    static NetworkEvaluation combine(StreamingAutopilot.Capabilities client,
                                     StreamingAutopilot.Capabilities host,
                                     PreferenceConfiguration.FormatOption format,
                                     NetworkStatus status, long bytes, long elapsedNanos) {
        if (status == NetworkStatus.MEASURED) {
            int safeBudget = StreamingAutopilot.safeBitrateBudgetKbps(bytes, elapsedNanos);
            NetworkSample sample = new NetworkSample(bytes, elapsedNanos);
            long goodputKbps = (long) (bytes * 8_000_000d / elapsedNanos);
            if (safeBudget == 0) {
                return new NetworkEvaluation(NetworkStatus.TOO_SLOW, Optional.of(sample),
                        OptionalLong.of(goodputKbps), OptionalInt.of(0), Optional.empty(),
                        Collections.emptyList());
            }
            OptionalInt budget = OptionalInt.of(safeBudget);
            return new NetworkEvaluation(NetworkStatus.MEASURED, Optional.of(sample),
                    OptionalLong.of(goodputKbps), budget,
                    Optional.of(StreamingAutopilot.recommend(client, host, budget, format)),
                    StreamingAutopilot.variants(client, host, budget, format));
        }
        if (status == NetworkStatus.TOO_SLOW) {
            throw new IllegalArgumentException("TOO_SLOW is derived from a measured sample");
        }
        return new NetworkEvaluation(status, Optional.empty(), OptionalLong.empty(),
                OptionalInt.empty(), Optional.of(
                        StreamingAutopilot.recommend(client, host, OptionalInt.empty(), format)),
                StreamingAutopilot.variants(client, host, OptionalInt.empty(), format));
    }

    static final class NetworkEvaluation {
        final NetworkStatus status;
        final Optional<NetworkSample> sample;
        final OptionalLong goodputKbps;
        final OptionalInt safeBitrateKbps;
        final Optional<StreamingAutopilot.Recommendation> recommendation;
        final List<StreamingAutopilot.Variant> variants;

        private NetworkEvaluation(NetworkStatus status, Optional<NetworkSample> sample,
                                  OptionalLong goodputKbps, OptionalInt safeBitrateKbps,
                                  Optional<StreamingAutopilot.Recommendation> recommendation,
                                  List<StreamingAutopilot.Variant> variants) {
            this.status = status;
            this.sample = sample;
            this.goodputKbps = goodputKbps;
            this.safeBitrateKbps = safeBitrateKbps;
            this.recommendation = recommendation;
            this.variants = variants;
        }
    }
}
