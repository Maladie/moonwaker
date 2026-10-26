package com.limelight.console;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.util.LruCache;
import android.view.View;

import com.limelight.gateway.GatewayConnection;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Profile avatar images the Gateway serves: each host's catalog (id to revision), a disk copy
 * per revision, decoded bitmaps in memory and the views waiting for them. A profile without an
 * image, or whose image is not loaded yet or not offered any more, keeps its generated art.
 */
final class ProfileAvatars {
    private static final int MAX_EDGE = 256;
    // ponytail: bitmaps are keyed by avatar id, not by host; two hosts with different custom
    // images of the same file name show the one loaded first until the process restarts.
    private static final LruCache<String, Bitmap> MEMORY =
            new LruCache<String, Bitmap>(8 * 1024 * 1024) {
                @Override protected int sizeOf(String key, Bitmap bitmap) {
                    return bitmap.getByteCount();
                }
            };
    /** Views waiting for an image, by the avatar they show now; main thread only. */
    private static final Map<View, Waiting> WAITING = new WeakHashMap<>();

    private final File directory;
    private final HostGatewayClient client;
    private final Executor executor;
    private final Handler main;
    private final Map<String, Map<String, String>> catalogs = new ConcurrentHashMap<>();
    private final Set<String> loading = ConcurrentHashMap.newKeySet();
    /** Ids the host did not offer or serve; tried again after the next catalog read. */
    private final Set<String> unavailable = ConcurrentHashMap.newKeySet();
    private Runnable onLoaded = () -> { };

    ProfileAvatars(Context context, HostGatewayClient client, Executor executor, Handler main) {
        directory = new File(context.getCacheDir(), "profile-avatars");
        this.client = client;
        this.executor = executor;
        this.main = main;
    }

    /**
     * Hands {@code avatarId}'s image to {@code show} now if it is in memory, or once it is
     * loaded while {@code owner} still shows that avatar. Main thread.
     */
    static void bind(View owner, String avatarId, Consumer<Bitmap> show) {
        WAITING.remove(owner);
        if (!HostGatewayClient.isAvatarId(avatarId)) return;
        Bitmap bitmap = MEMORY.get(avatarId);
        if (bitmap != null) show.accept(bitmap);
        else WAITING.put(owner, new Waiting(avatarId, show));
    }

    /** The image in memory, or null. */
    static Bitmap cached(String avatarId) {
        return HostGatewayClient.isAvatarId(avatarId) ? MEMORY.get(avatarId) : null;
    }

    /** Runs on the main thread after any image arrives, for drawings that are not views. */
    void setOnLoaded(Runnable onLoaded) {
        this.onLoaded = onLoaded == null ? () -> { } : onLoaded;
    }

    /** Loads the images not in memory yet, in the background. Main thread. */
    void load(GatewayConnection connection, String hostUuid, Collection<String> avatarIds) {
        if (connection == null || hostDirectory(hostUuid) == null) return;
        for (String id : avatarIds) {
            if (unavailable.contains(id)) drop(id);
            if (!HostGatewayClient.isAvatarId(id) || MEMORY.get(id) != null
                    || unavailable.contains(id) || !loading.add(id)) continue;
            executor.execute(() -> {
                Bitmap bitmap = null;
                try {
                    bitmap = decode(file(connection, hostUuid, id));
                } catch (IOException | RuntimeException failed) {
                    unavailable.add(id);
                }
                Bitmap loaded = bitmap;
                main.post(() -> {
                    loading.remove(id);
                    if (loaded != null) deliver(id, loaded);
                    else drop(id);
                });
            });
        }
    }

    /** Reads the host's catalog again (a picker opening). Background thread. */
    List<HostGatewayClient.Avatar> catalog(GatewayConnection connection, String hostUuid)
            throws IOException {
        List<HostGatewayClient.Avatar> avatars = client.getAvatars(connection);
        catalogs.put(hostUuid, revisions(avatars));
        unavailable.clear();
        return avatars;
    }

    static List<String> avatarIds(List<HostGatewayClient.Avatar> avatars) {
        List<String> ids = new ArrayList<>();
        for (HostGatewayClient.Avatar avatar : avatars) ids.add(avatar.id);
        return ids;
    }

    private static Map<String, String> revisions(List<HostGatewayClient.Avatar> avatars) {
        Map<String, String> revisions = new HashMap<>();
        for (HostGatewayClient.Avatar avatar : avatars) revisions.put(avatar.id, avatar.revision);
        return revisions;
    }

    /** Deletes the copies of a host this device forgets. */
    void forgetHost(String hostUuid) {
        catalogs.remove(hostUuid);
        LocalHostRecords.deleteProfileEntries(directory, hostUuid);
    }

    private File file(GatewayConnection connection, String hostUuid, String id)
            throws IOException {
        Map<String, String> avatars = catalogs.get(hostUuid);
        if (avatars == null || !avatars.containsKey(id)) {
            avatars = revisions(client.getAvatars(connection));
            catalogs.put(hostUuid, avatars);
        }
        String revision = avatars.get(id);
        if (revision == null) throw new IOException("The host does not offer " + id);
        File host = hostDirectory(hostUuid);
        File target = new File(host, id.replace(':', '_') + "_" + revision + ".img");
        if (target.isFile() && target.length() > 0) return target;
        if (!host.isDirectory() && !host.mkdirs() && !host.isDirectory()) {
            throw new IOException("Unable to create the avatar cache");
        }
        byte[] bytes = client.getAvatarImage(connection, id);
        File temporary = new File(host, target.getName() + ".tmp");
        try (BufferedOutputStream output =
                     new BufferedOutputStream(new FileOutputStream(temporary))) {
            output.write(bytes);
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new IOException("Unable to store the avatar");
        }
        return target;
    }

    private File hostDirectory(String hostUuid) {
        if (hostUuid == null || !hostUuid.matches("[A-Za-z0-9._-]{1,128}")) return null;
        return new File(directory, hostUuid.toLowerCase(Locale.ROOT) + "__avatars");
    }

    static Bitmap decode(File file) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2)
                >= MAX_EDGE) {
            options.inSampleSize *= 2;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), options);
        if (bitmap == null) {
            file.delete();
            throw new IOException("The avatar is not an image");
        }
        return bitmap;
    }

    /** Waiting views hold their callbacks strongly: release those of an image that failed. */
    private static void drop(String id) {
        WAITING.values().removeIf(waiting -> waiting.avatarId.equals(id));
    }

    private void deliver(String id, Bitmap bitmap) {
        MEMORY.put(id, bitmap);
        List<Consumer<Bitmap>> ready = new ArrayList<>();
        for (Iterator<Waiting> waiting = WAITING.values().iterator(); waiting.hasNext(); ) {
            Waiting next = waiting.next();
            if (!next.avatarId.equals(id)) continue;
            ready.add(next.show);
            waiting.remove();
        }
        for (Consumer<Bitmap> show : ready) show.accept(bitmap);
        onLoaded.run();
    }

    private static final class Waiting {
        final String avatarId;
        final Consumer<Bitmap> show;

        Waiting(String avatarId, Consumer<Bitmap> show) {
            this.avatarId = avatarId;
            this.show = show;
        }
    }

    static List<String> ids(Collection<HostGatewayClient.IntegrationProfile> profiles) {
        if (profiles == null) return Collections.emptyList();
        List<String> ids = new ArrayList<>();
        for (HostGatewayClient.IntegrationProfile profile : profiles) {
            if (profile != null) ids.add(profile.avatarId);
        }
        return ids;
    }
}
