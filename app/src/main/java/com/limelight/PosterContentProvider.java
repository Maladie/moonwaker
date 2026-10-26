package com.limelight;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.limelight.binding.audio.UsbMicrophoneService;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.session.SessionModel;
import com.limelight.session.WatchNextTile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class PosterContentProvider extends ContentProvider {


    public static final String AUTHORITY = "poster." + BuildConfig.APPLICATION_ID;
    public static final String PNG_MIME_TYPE = "image/png";
    public static final int APP_ID_PATH_INDEX = 2;
    public static final int COMPUTER_UUID_PATH_INDEX = 1;
    private DiskAssetLoader mDiskAssetLoader;

    private static final UriMatcher sUriMatcher;
    private static final String BOXART_PATH = "boxart";
    private static final String SESSION_PATH = "session";
    private static final int BOXART_URI_ID = 1;
    private static final int SESSION_URI_ID = 2;

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, BOXART_PATH, BOXART_URI_ID);
        sUriMatcher.addURI(AUTHORITY, SESSION_PATH + "/*", SESSION_URI_ID);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        int match = sUriMatcher.match(uri);
        if (match == SESSION_URI_ID) {
            return openSessionArt(uri, mode);
        }
        return openBoxArtFile(uri, mode);

    }

    /**
     * The Watch Next tile's artwork: served only while that stream still earns its tile, so
     * the URI never reaches another stream's or profile's artwork.
     */
    private ParcelFileDescriptor openSessionArt(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new UnsupportedOperationException("This provider is only for read mode");
        }
        Context context = getContext();
        SharedPreferences consent = context.getSharedPreferences(
                UsbMicrophoneService.CONSOLE_PREFS, Context.MODE_PRIVATE);
        WatchNextTile tile = WatchNextTile.of(SessionModel.of(context).snapshot(),
                (host, profile) -> WatchNextTile.consented(consent, host, profile));
        if (tile == null || !tile.streamSessionId.equals(uri.getLastPathSegment())) {
            throw new FileNotFoundException();
        }
        File file = tile.artworkPath.isEmpty() ? desktopArt(context) : new File(tile.artworkPath);
        if (!file.isFile()) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /** The launcher banner (a vector) as the PNG a launcher can load, rendered once. */
    private static synchronized File desktopArt(Context context) throws FileNotFoundException {
        File file = new File(context.getCacheDir(), "watch_next_desktop.png");
        if (file.isFile()) return file;
        Drawable banner = context.getDrawable(R.drawable.moonwaker_launcher_banner);
        if (banner == null) throw new FileNotFoundException();
        Bitmap bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888);
        banner.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        banner.draw(new Canvas(bitmap));
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException error) {
            file.delete();
            throw new FileNotFoundException(error.getMessage());
        }
        return file;
    }

    public ParcelFileDescriptor openBoxArtFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new UnsupportedOperationException("This provider is only for read mode");
        }

        List<String> segments = uri.getPathSegments();
        if (segments.size() != 3) {
            throw new FileNotFoundException();
        }
        String appId = segments.get(APP_ID_PATH_INDEX);
        String uuid = segments.get(COMPUTER_UUID_PATH_INDEX);
        File file = mDiskAssetLoader.getFile(uuid, Integer.parseInt(appId));
        if (file.exists()) {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        }
        throw new FileNotFoundException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("This provider is only for read mode");
    }

    @Override
    public String getType(Uri uri) {
        return PNG_MIME_TYPE;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("This provider is only for read mode");
    }

    @Override
    public boolean onCreate() {
        mDiskAssetLoader = new DiskAssetLoader(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException("This provider doesn't support query");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException("This provider is support read only");
    }


    public static Uri createSessionArtUri(String streamSessionId) {
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(SESSION_PATH)
                .appendPath(streamSessionId)
                .build();
    }

    public static Uri createBoxArtUri(String uuid, String appId) {
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(BOXART_PATH)
                .appendPath(uuid)
                .appendPath(appId)
                .build();
    }

}
