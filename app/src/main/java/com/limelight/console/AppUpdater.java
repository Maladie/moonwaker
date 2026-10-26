package com.limelight.console;

import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.limelight.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * App updates on the TV (#56 U2/U3): what Home and Settings say about {@link ReleaseUpdates},
 * and installing a verified release APK. The APK is downloaded to the cache, checked for its
 * size, SHA-256, package and version against the signed manifest, and handed to Android's
 * package installer, which asks the user to confirm. Android itself refuses an APK signed
 * with another key than the installed app.
 */
final class AppUpdater {
    private static final int TIMEOUT_MS = 30_000;

    /** A Home notice; {@code finding} keeps it to once, {@code apk} adds "Update". */
    static final class Notice {
        final String finding;
        final String title;
        final String details;
        final ReleaseUpdates.Apk apk;

        private Notice(String finding, String title, String details, ReleaseUpdates.Apk apk) {
            this.finding = finding;
            this.title = title;
            this.details = details;
            this.apk = apk;
        }
    }

    /** TV and host disagree, or a newer app is out; null when there is nothing to say. */
    static Notice notice(Context context, ReleaseUpdates updates, long installedVersionCode,
                         String installedVersionName) {
        ReleaseUpdates.Apk apk = updates.appUpdate(installedVersionCode) ? updates.apk : null;
        ReleaseUpdates.Compatibility compatibility = updates.compatibility();
        if (compatibility == ReleaseUpdates.Compatibility.HOST_OLDER) {
            return new Notice("host_older:" + updates.hostVersion,
                    context.getString(R.string.app_update_host_older_title),
                    context.getString(R.string.app_update_host_older_details,
                            updates.hostVersion, installedVersionName), apk);
        }
        if (compatibility == ReleaseUpdates.Compatibility.APP_OLDER) {
            return new Notice("app_older:" + updates.hostProtocol,
                    context.getString(R.string.app_update_app_older_title),
                    context.getString(R.string.app_update_app_older_details,
                            updates.hostVersion, installedVersionName), apk);
        }
        if (apk == null) return null;
        return new Notice("apk:" + apk.versionCode,
                context.getString(R.string.app_update_available_title, apk.versionName),
                context.getString(R.string.app_update_available_details, installedVersionName),
                apk);
    }

    /** Settings › About: the app and this host, each with its state. */
    static List<View> settingsRows(Context context, ConsolePanel panel, ReleaseUpdates updates,
                                   long installedVersionCode,
                                   Consumer<ReleaseUpdates.Apk> update) {
        // Only the app row acts (it installs); the others are facts without a chevron or focus.
        String appLabel = context.getString(R.string.app_update_row_app);
        LinearLayout app;
        if (updates.appUpdate(installedVersionCode)) {
            app = panel.value(appLabel, context.getString(R.string.app_update_row_available,
                    updates.apk.versionName));
            app.setOnClickListener(view -> update.accept(updates.apk));
        } else {
            app = panel.fact(appLabel, context.getString(updates.apk == null
                    ? R.string.app_update_row_unknown : R.string.app_update_row_current_app),
                    ConsolePanel.HEADER);
        }
        ReleaseUpdates.Compatibility compatibility = updates.compatibility();
        String hostState = compatibility == ReleaseUpdates.Compatibility.HOST_OLDER
                ? context.getString(R.string.app_update_row_host_older)
                : compatibility == ReleaseUpdates.Compatibility.APP_OLDER
                ? context.getString(R.string.app_update_row_app_older)
                : updates.hostUpdate() ? context.getString(R.string.app_update_row_available,
                updates.releaseHostVersion)
                : updates.releaseHostVersion.isEmpty()
                ? context.getString(R.string.app_update_row_unknown)
                : context.getString(R.string.app_update_row_current_host);
        // Until the host updates itself (U4) the TV can only say how.
        boolean hostAction = updates.hostUpdate()
                || compatibility == ReleaseUpdates.Compatibility.HOST_OLDER;
        LinearLayout host = panel.fact(context.getString(R.string.app_update_row_host,
                updates.hostVersion), hostState, hostAction
                || compatibility == ReleaseUpdates.Compatibility.APP_OLDER
                ? ConsolePanel.TEXT : ConsolePanel.HEADER);
        if (hostAction) panel.described(host, context.getString(R.string.app_update_row_host_how));
        return Arrays.asList(panel.section(context.getString(R.string.app_update_eyebrow)),
                app, host);
    }

    private final Context context;
    private final Executor executor;
    private final Handler mainHandler;
    private boolean running;

    AppUpdater(Context context, Executor executor, Handler mainHandler) {
        this.context = context.getApplicationContext();
        this.executor = executor;
        this.mainHandler = mainHandler;
    }

    /** Android TV asks once whether MoonWaker may install apps ("unknown sources"). */
    boolean canInstall() {
        return context.getPackageManager().canRequestPackageInstalls();
    }

    /** Opens that setting for MoonWaker; false when this TV has no such screen. */
    boolean openInstallPermission(Context activity) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName())));
            return true;
        } catch (ActivityNotFoundException noScreen) {
            return false;
        }
    }

    /**
     * {@code finished} runs on the main thread with 0 once Android has the APK (its own dialog
     * confirms next), or with the message saying why not.
     */
    void install(ReleaseUpdates.Apk apk, IntConsumer finished) {
        if (running) return;
        running = true;
        executor.execute(() -> {
            int failure = 0;
            File file = new File(context.getCacheDir(), "update.apk");
            try {
                download(apk, file);
                PackageInfo info = context.getPackageManager()
                        .getPackageArchiveInfo(file.getPath(), 0);
                if (info == null || !context.getPackageName().equals(info.packageName)
                        || info.getLongVersionCode() != apk.versionCode) {
                    failure = R.string.app_update_failed_verify;
                } else {
                    commit(file);
                }
            } catch (SecurityException verification) {
                failure = R.string.app_update_failed_verify;
            } catch (IOException download) {
                failure = R.string.app_update_failed_download;
            } catch (RuntimeException installer) {
                failure = R.string.app_update_failed_install;
            } finally {
                if (!file.delete()) file.deleteOnExit();
            }
            int message = failure;
            mainHandler.post(() -> {
                running = false;
                finished.accept(message);
            });
        });
    }

    private static void download(ReleaseUpdates.Apk apk, File file) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(apk.url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        MessageDigest sha256 = sha256();
        long total = 0;
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + connection.getResponseCode());
            }
        } catch (IOException | RuntimeException failure) {
            connection.disconnect();
            throw failure;
        }
        try (InputStream in = connection.getInputStream();
             OutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = in.read(buffer)) > 0; ) {
                total += read;
                if (total > apk.size) throw new SecurityException("larger than the manifest says");
                sha256.update(buffer, 0, read);
                out.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }
        if (total != apk.size || !hex(sha256.digest()).equalsIgnoreCase(apk.sha256)) {
            throw new SecurityException("not the APK the manifest names");
        }
    }

    private void commit(File file) throws IOException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        params.setSize(file.length());
        int sessionId = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (InputStream in = new FileInputStream(file);
                 OutputStream out = session.openWrite("base.apk", 0, file.length())) {
                byte[] buffer = new byte[64 * 1024];
                for (int read; (read = in.read(buffer)) > 0; ) out.write(buffer, 0, read);
                session.fsync(out);
            }
            // Mutable: the installer adds its status and confirmation intent as extras.
            session.commit(PendingIntent.getBroadcast(context, sessionId,
                    new Intent(context, Result.class).setPackage(context.getPackageName()),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE)
                    .getIntentSender());
        } catch (IOException | RuntimeException failure) {
            installer.abandonSession(sessionId);
            throw failure;
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) text.append(String.format("%02x", value));
        return text.toString();
    }

    /** The installer's answer: its confirmation screen, or why it did not install. */
    public static final class Result extends BroadcastReceiver {
        @Override
        @SuppressWarnings("deprecation") // getParcelableExtra(String, Class) needs API 33.
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                }
            } else if (status != PackageInstaller.STATUS_SUCCESS
                    && status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                Toast.makeText(context, R.string.app_update_failed_install,
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
