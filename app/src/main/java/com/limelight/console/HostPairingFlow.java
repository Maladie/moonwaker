package com.limelight.console;

import android.content.Context;
import android.os.Build;
import android.os.Handler;

import com.limelight.R;
import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerService;
import com.limelight.gateway.GatewayConnection;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.utils.ServerHelper;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Pairs this TV with a host: the Gateway with Host Control's six-digit code, then streaming
 * (Vibepollo) by a ticket, or streaming alone by Vibepollo's own PIN. The caller shows the
 * code entry, progress and errors (the first-run wizard, the console's renewal and repair
 * prompts); this flow owns the requests and what they leave stored. Listener calls run on the
 * main thread.
 */
final class HostPairingFlow {
    interface Listener {
        /** The step now running, e.g. "Vibepollo"; a progress line, not a result. */
        void onStage(String stage);

        void onPaired(ComputerDetails host);

        void onFailed(ComputerDetails host, Failure failure);

        /** Manual pairing: the PIN to enter in Vibepollo on the PC. */
        default void onManualPin(String pin) { }
    }

    /** What the user can do about a failure: see FIRST_RUN_WIZARD_DESIGN_PL.md section 4. */
    enum Problem {
        /** The code was wrong, expired or no pairing was open: make a new one on the PC. */
        CODE,
        /** The Gateway did not answer: same network, firewall, address. */
        UNREACHABLE,
        /** Vibepollo was not running or did not grant the client its permissions. */
        VIBEPOLLO,
        OTHER
    }

    static final class Failure {
        final Problem problem;
        final String detail;

        Failure(Problem problem, String detail) {
            this.problem = problem;
            this.detail = detail;
        }
    }

    private final Context context;
    private final ExecutorService executor;
    private final Handler main;
    private final HostGatewayClient client;
    private final HostGatewayStore store;
    private final Supplier<ComputerManagerService.ComputerManagerBinder> binder;
    private final BiConsumer<String, HostGatewayClient.IntegrationProfiles> saveProfiles;

    HostPairingFlow(Context context, ExecutorService executor, Handler main,
                    HostGatewayClient client, HostGatewayStore store,
                    Supplier<ComputerManagerService.ComputerManagerBinder> binder,
                    BiConsumer<String, HostGatewayClient.IntegrationProfiles> saveProfiles) {
        this.context = context;
        this.executor = executor;
        this.main = main;
        this.client = client;
        this.store = store;
        this.binder = binder;
        this.saveProfiles = saveProfiles;
    }

    /**
     * Pairs the Gateway with {@code code}, keeps the connection and the profiles the host
     * granted, then pairs streaming with the ticket that came with it. The connection carries
     * the host's suggested profile; the caller lets the user pick another afterwards.
     */
    void pairWithCode(ComputerDetails host, String code, Listener listener) {
        listener.onStage(context.getString(R.string.console_pair_host_gateway));
        executor.execute(() -> {
            HostGatewayClient.Pairing pairing;
            try {
                String endpoint = HostGatewayClient.endpointForHost(host.activeAddress.address);
                pairing = client.pair(endpoint, code, clientName());
                store.save(host.uuid, pairing.connection);
                saveProfiles.accept(host.uuid, pairing.profiles);
            } catch (HostGatewayClient.GatewayException error) {
                boolean rejected = error.statusCode == 401 || error.statusCode == 403;
                fail(host, listener, () -> true, new Failure(rejected ? Problem.CODE
                        : Problem.OTHER, deepestPairingError(error)));
                return;
            } catch (IOException | RuntimeException error) {
                fail(host, listener, () -> true,
                        new Failure(Problem.UNREACHABLE, deepestPairingError(error)));
                return;
            }
            pairStream(host, pairing.connection, pairing.streamPairTicket, false, () -> true,
                    listener);
        });
    }

    /**
     * Pairs streaming through the Gateway: a ticket (the given one, or a fresh one), this TV's
     * certificate offered to Vibepollo with a generated PIN, and the permissions checked.
     * {@code renew} replaces an existing pairing (child repair), which needs the pinned
     * certificate; {@code current} is checked between steps and before reporting, so a renewal
     * started for a host or profile no longer selected neither continues nor reports.
     */
    void pairStream(ComputerDetails host, GatewayConnection connection, String initialTicket,
                    boolean renew, BooleanSupplier current, Listener listener) {
        final ComputerManagerService.ComputerManagerBinder pairingBinder = binder.get();
        final X509Certificate pinnedServerCert = host.serverCert;
        if (pairingBinder == null || (renew && pinnedServerCert == null)) {
            fail(host, listener, () -> true, new Failure(Problem.OTHER, pairingBinder == null
                    ? "Host manager is unavailable."
                    : "Authenticated host renewal requires the pinned host certificate."));
            return;
        }
        executor.execute(() -> {
            Future<PairingManager.PairState> pairFuture = null;
            NvHTTP http = null;
            String pairingStage = context.getString(R.string.console_pair_stage_ticket);
            BooleanSupplier stillCurrent = renew ? current : () -> true;
            try {
                stage(listener, pairingStage);
                requireCurrent(stillCurrent);
                String ticket = initialTicket == null || initialTicket.isEmpty()
                        ? client.requestVibepolloPairingTicket(connection)
                        : initialTicket;
                requireCurrent(stillCurrent);
                pairingStage = context.getString(R.string.console_pair_stage_host);
                stage(listener, pairingStage);
                http = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(host),
                        host.httpsPort, pairingBinder.getUniqueId(),
                        pinnedServerCert,
                        PlatformBinding.getCryptoProvider(context));
                PairingManager.PairState pairState = http.getPairState();
                if (pairState == PairingManager.PairState.PAIRED && !renew) {
                    succeed(host, listener, stillCurrent);
                    return;
                }
                if (renew && pairState == PairingManager.PairState.PAIRED) {
                    if (!http.unpairAuthenticated()) {
                        throw new IOException("The authenticated host pairing could not be renewed.");
                    }
                    PairingManager.PairState afterUnpair = http.getPairState();
                    if (afterUnpair != PairingManager.PairState.NOT_PAIRED) {
                        throw new IOException("The host still has an authenticated client after renewal.");
                    }
                }
                requireCurrent(stillCurrent);
                String pin = PairingManager.generatePinString();
                PairingManager pairing = http.getPairingManager();
                String serverInfo = http.getServerInfo(true);
                pairFuture = executor.submit(() -> pairing.pair(serverInfo, pin));
                requireCurrent(stillCurrent);
                pairingStage = context.getString(R.string.console_pair_stage_vibepollo);
                stage(listener, pairingStage);
                JSONObject pairedClient = client.pairVibepolloClient(
                        connection, ticket, pin, clientName());
                int permissions = pairedClient.optInt("permissions", 0);
                if ((permissions & HostGatewayClient.REQUIRED_GAMEPLAY_PERMISSIONS)
                        != HostGatewayClient.REQUIRED_GAMEPLAY_PERMISSIONS) {
                    throw new IOException("Vibepollo did not grant the required client permissions.");
                }
                pairingStage = context.getString(R.string.console_pair_stage_confirmation);
                stage(listener, pairingStage);
                PairingManager.PairState state = pairFuture.get(12, TimeUnit.SECONDS);
                if (state != PairingManager.PairState.PAIRED) {
                    throw new IOException(state == PairingManager.PairState.PIN_WRONG
                            ? context.getString(R.string.pair_incorrect_pin)
                            : context.getString(R.string.pair_fail));
                }
                if (pinnedServerCert != null && pairing.getPairedCert() != null
                        && !pinnedServerCert.equals(pairing.getPairedCert())) {
                    throw new IOException("The host certificate changed during pairing.");
                }
                requireCurrent(stillCurrent);
                ComputerDetails managed = pairingBinder.getComputer(host.uuid);
                if (managed != null) managed.serverCert = pairing.getPairedCert();
                pairingBinder.invalidateStateForComputer(host.uuid);
                succeed(host, listener, stillCurrent);
            } catch (TimeoutException error) {
                if (pairFuture != null) pairFuture.cancel(true);
                if (pairFuture != null) cancelPendingPairing(http);
                fail(host, listener, stillCurrent, new Failure(problemAt(pairingStage),
                        context.getString(R.string.console_pair_failure_stage, pairingStage,
                                context.getString(R.string.console_pair_host_timeout))));
            } catch (Exception error) {
                if (pairFuture != null && !pairFuture.isDone()) pairFuture.cancel(true);
                if (pairFuture != null) cancelPendingPairing(http);
                fail(host, listener, stillCurrent, new Failure(problemAt(pairingStage),
                        context.getString(R.string.console_pair_failure_stage, pairingStage,
                                deepestPairingError(error))));
            }
        });
    }

    private Problem problemAt(String stage) {
        return context.getString(R.string.console_pair_stage_vibepollo).equals(stage)
                ? Problem.VIBEPOLLO : Problem.OTHER;
    }

    private static void requireCurrent(BooleanSupplier current) throws IOException {
        if (!current.getAsBoolean()) {
            throw new IOException("Host or profile changed during pairing renewal.");
        }
    }

    /**
     * Streaming alone, the Moonlight way: the PIN is shown ({@link Listener#onManualPin}) and
     * entered in Vibepollo on the PC. For a host whose Gateway cannot issue a ticket.
     */
    void pairManually(ComputerDetails host, Listener listener) {
        ComputerManagerService.ComputerManagerBinder pairingBinder = binder.get();
        if (pairingBinder == null) {
            fail(host, listener, () -> true, new Failure(Problem.OTHER,
                    context.getString(R.string.error_manager_not_running)));
            return;
        }
        listener.onStage(context.getString(R.string.pairing));
        executor.execute(() -> {
            String message = null;
            try {
                NvHTTP http = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(host),
                        host.httpsPort, pairingBinder.getUniqueId(), host.serverCert,
                        PlatformBinding.getCryptoProvider(context));
                if (http.getPairState() != PairingManager.PairState.PAIRED) {
                    String pin = PairingManager.generatePinString();
                    main.post(() -> listener.onManualPin(pin));
                    PairingManager pairing = http.getPairingManager();
                    PairingManager.PairState state = pairing.pair(http.getServerInfo(true), pin);
                    if (state == PairingManager.PairState.PAIRED) {
                        ComputerDetails managed = pairingBinder.getComputer(host.uuid);
                        if (managed != null) managed.serverCert = pairing.getPairedCert();
                        pairingBinder.invalidateStateForComputer(host.uuid);
                    } else if (state == PairingManager.PairState.PIN_WRONG) {
                        message = context.getString(R.string.pair_incorrect_pin);
                    } else if (state == PairingManager.PairState.ALREADY_IN_PROGRESS) {
                        message = context.getString(R.string.pair_already_in_progress);
                    } else {
                        message = context.getString(host.runningGameId != 0
                                ? R.string.pair_pc_ingame : R.string.pair_fail);
                    }
                }
            } catch (UnknownHostException error) {
                message = context.getString(R.string.error_unknown_host);
            } catch (FileNotFoundException error) {
                message = context.getString(R.string.error_404);
            } catch (IOException | XmlPullParserException error) {
                message = error.getMessage();
            }
            if (message == null) {
                succeed(host, listener, () -> true);
            } else {
                fail(host, listener, () -> true, new Failure(Problem.OTHER,
                        message.isEmpty() ? context.getString(R.string.pair_fail) : message));
            }
        });
    }

    private void stage(Listener listener, String stage) {
        main.post(() -> listener.onStage(stage));
    }

    private void succeed(ComputerDetails host, Listener listener, BooleanSupplier current) {
        main.post(() -> {
            if (current.getAsBoolean()) listener.onPaired(host);
        });
    }

    private void fail(ComputerDetails host, Listener listener, BooleanSupplier current,
                      Failure failure) {
        main.post(() -> {
            if (current.getAsBoolean()) listener.onFailed(host, failure);
        });
    }

    static String deepestPairingError(Throwable error) {
        Throwable current = error;
        String detail = "";
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().trim().isEmpty()) {
                detail = current.getMessage().trim();
            }
            if (current.getCause() == current) break;
            current = current.getCause();
        }
        return detail.isEmpty() ? "Unknown pairing error." : detail;
    }

    private static void cancelPendingPairing(NvHTTP http) {
        if (http == null) return;
        try { http.unpair(); } catch (Exception ignored) { }
    }

    /** "MoonWaker <model> <id suffix>": how the host lists this TV. */
    String clientName() {
        ComputerManagerService.ComputerManagerBinder current = binder.get();
        String model = Build.MODEL == null ? "Android-TV" : Build.MODEL;
        model = model.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        String uniqueId = current != null ? current.getUniqueId() : "client";
        String suffix = uniqueId.length() > 6
                ? uniqueId.substring(uniqueId.length() - 6) : uniqueId;
        String result = "MoonWaker " + (model.isEmpty() ? "Android-TV" : model) + " " + suffix;
        return result.length() > 80 ? result.substring(0, 80) : result;
    }
}
