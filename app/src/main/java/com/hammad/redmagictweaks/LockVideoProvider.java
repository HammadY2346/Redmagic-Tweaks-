package com.hammad.redmagictweaks;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * The lock screen video clip for System UI (xposed/LockVideoHooks), read-only, so System UI needs no storage permission:
 * <pre>
 *   call "info"                       -> {present, version, start, end, duration, width, height} (ms / px)
 *   openFile content://authority/clip -> the clip (MP4)
 *   call "aod_info"                   -> {present, version, duration, width, height} of the AOD video (AodVideo)
 *   openFile content://authority/aod  -> the AOD video
 *   openFile content://authority/iconzip -> the launcher icon zip (IconZip), for xposed/LauncherIcons
 * </pre>
 * Only packages in the module scope (and the app itself) get an answer.
 */
public final class LockVideoProvider extends ContentProvider {
    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".lockvideo";

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Context c = getContext();
        if (c == null || !allowed(c)) return null;
        if ("aod_info".equals(method)) {
            Bundle b = new Bundle();
            AodVideo.Meta m = AodVideo.meta(c);
            b.putInt("present", m == null ? 0 : 1);
            if (m != null) {
                b.putLong("version", m.version);
                b.putInt("duration", m.durationMs);
                b.putInt("width", m.width);
                b.putInt("height", m.height);
            }
            return b;
        }
        if (!"info".equals(method)) return null;
        Bundle b = new Bundle();
        LockVideo.Meta m = LockVideo.meta(c);
        b.putInt("present", m == null ? 0 : 1);
        if (m != null) {
            b.putLong("version", m.version);
            b.putInt("start", m.startMs);
            b.putInt("end", m.endMs);
            b.putInt("duration", m.durationMs);
            b.putInt("width", m.width);
            b.putInt("height", m.height);
        }
        return b;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context c = getContext();
        String seg = uri.getLastPathSegment();
        // Icon zips are served to any home app (stock or third-party); lock/AOD clips stay scope-only.
        if (c == null || !allowed(c, "iconzip".equals(seg))) throw new FileNotFoundException("not allowed");
        if (!("clip".equals(seg) || "aod".equals(seg) || "iconzip".equals(seg)) || (mode != null && mode.contains("w"))) throw new FileNotFoundException(uri.toString());
        File f = "aod".equals(seg) ? AodVideo.clip(c) : "iconzip".equals(seg) ? IconZip.pack(c) : LockVideo.clip(c);
        if (f.length() == 0) throw new FileNotFoundException("no file");
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private static boolean allowed(Context c) { return allowed(c, false); }

    private static boolean allowed(Context c, boolean homeOk) {
        String[] pkgs = c.getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (pkgs == null) return false;
        String[] scope = c.getResources().getStringArray(R.array.xposed_scope);
        for (String p : pkgs) {
            if (p.equals(c.getPackageName())) return true;
            for (String s : scope) if (s.equals(p)) return true;
            if (homeOk && Homes.isHome(c, p)) return true;
        }
        return false;
    }

    @Override public String getType(Uri uri) {
        String seg = uri.getLastPathSegment();
        if ("iconzip".equals(seg)) return "application/zip";
        return "clip".equals(seg) || "aod".equals(seg) ? "video/mp4" : null;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
