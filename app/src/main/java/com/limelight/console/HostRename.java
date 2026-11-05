package com.limelight.console;

import android.os.Handler;

import com.limelight.R;
import com.limelight.gateway.GatewayConnection;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Saves the computer's announced name in Vibepollo through the Gateway (adult profile, one
 * request ID per save): the wizard's naming step and the host panel's "Rename computer".
 */
final class HostRename {
    /** The Gateway's limit on a host name. */
    static final int MAX_LENGTH = 32;

    interface Result {
        /** On the main thread: the name the host kept, else null and the message to show. */
        void done(String saved, int error);
    }

    static void save(HostGatewayClient client, Executor executor, Handler main,
                     GatewayConnection gateway, String typed, Result result) {
        String name = typed.trim();
        if (name.isEmpty() || gateway == null) {
            result.done(null, name.isEmpty() ? R.string.first_run_name_invalid
                    : R.string.first_run_name_failed);
            return;
        }
        String requestId = UUID.randomUUID().toString();
        executor.execute(() -> {
            String saved = null;
            int error = R.string.first_run_name_failed;
            try {
                saved = client.setHostName(gateway, name, requestId);
            } catch (IOException | RuntimeException failure) {
                if ("host_name_invalid".equals(failure.getMessage())) {
                    error = R.string.first_run_name_invalid;
                }
            }
            String kept = saved;
            int message = error;
            main.post(() -> result.done(kept, message));
        });
    }

    private HostRename() { }
}
