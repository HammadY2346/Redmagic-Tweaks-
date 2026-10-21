package com.hammad.redmagictweaks;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * Receives log lines from hooked processes: resolver.call(URI, "log", line, null), and serves frame /
 * font files to them. Only packages in the module scope (plus ourselves) are accepted; everything else is dropped.
 *
 * <pre>
 *   call "frame_count"    arg=set  -> {count}   charging frames of a built-in set or "custom"
 *   call "fp_frame_count" arg=set  -> {count, icon}   fingerprint frames of a built-in set or "custom"; icon = 1 when the set has an idle icon
 *   call "font_present"            -> {present} an imported lock-clock font exists
 *   openAssetFile content://authority/charging/&lt;set&gt;/&lt;index&gt;
 *   openAssetFile content://authority/fingerprint/&lt;set&gt;/&lt;index&gt;   (index wraps)
 *   openAssetFile content://authority/fingerprint/&lt;set&gt;/icon
 *   openAssetFile content://authority/font/lockclock
 * </pre>
 */
public final class LogProvider extends ContentProvider {
    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".log";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_LOG = "log";
    public static final String METHOD_FRAME_COUNT = "frame_count";
    public static final String METHOD_FP_FRAME_COUNT = "fp_frame_count";
    public static final String METHOD_FONT_PRESENT = "font_present";

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Context c = getContext();
        if (c == null) return null;
        String caller = callerPackage(c);
        if (caller == null) return null;
        if (METHOD_FRAME_COUNT.equals(method)) {
            Bundle b = new Bundle();
            String set = arg == null ? Frames.CUSTOM : arg;
            b.putInt("count", Frames.CUSTOM.equals(set) ? Frames.count(c) : Frames.assetCount(c, set));
            return b;
        }
        if (METHOD_FP_FRAME_COUNT.equals(method)) {
            Bundle b = new Bundle();
            String set = arg == null ? Frames.CUSTOM : arg;
            if (Frames.CUSTOM.equals(set)) {
                b.putInt("count", Frames.fpCount(c));
                b.putInt("icon", Frames.fpIcon(c) != null ? 1 : 0);
            } else if (set.matches("[a-z_]+")) {
                b.putInt("count", Frames.fpAssetCount(c, set));
                b.putInt("icon", Frames.fpAssetIcon(c, set) != null ? 1 : 0);
            } else {
                b.putInt("count", 0);
                b.putInt("icon", 0);
            }
            return b;
        }
        if (METHOD_FONT_PRESENT.equals(method)) {
            Bundle b = new Bundle();
            b.putInt("present", Frames.hasFont(c) ? 1 : 0);
            return b;
        }
        if (!METHOD_LOG.equals(method) || arg == null) return null;
        if (arg.length() > 4000) arg = arg.substring(0, 4000);
        LogStore.append(c, LogStore.stamp() + " [" + caller + "] " + arg.replace('\n', ' '));
        return Bundle.EMPTY;
    }

    private String callerPackage(Context c) {
        int uid = Binder.getCallingUid();
        PackageManager pm = c.getPackageManager();
        String[] pkgs = pm.getPackagesForUid(uid);
        if (pkgs == null) return null;
        String[] scope = c.getResources().getStringArray(R.array.xposed_scope);
        for (String p : pkgs) {
            if (p.equals(c.getPackageName())) return p;
            for (String s : scope) if (s.equals(p)) return p;
        }
        return null;
    }

    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        Context c = getContext();
        if (c == null || callerPackage(c) == null) throw new FileNotFoundException("not allowed");
        List<String> seg = uri.getPathSegments();
        if (seg.size() == 2 && "font".equals(seg.get(0)) && "lockclock".equals(seg.get(1))) {
            File f = Frames.fontFile(c);
            if (f.length() == 0) throw new FileNotFoundException("no font");
            return file(f);
        }
        if (seg.size() != 3) throw new FileNotFoundException(uri.toString());
        String kind = seg.get(0), set = seg.get(1), what = seg.get(2);
        boolean charging = "charging".equals(kind), fingerprint = "fingerprint".equals(kind);
        if (!charging && !fingerprint) throw new FileNotFoundException(uri.toString());
        if (fingerprint && "icon".equals(what)) {
            if (Frames.CUSTOM.equals(set)) {
                File f = Frames.fpIcon(c);
                if (f == null) throw new FileNotFoundException("no icon");
                return file(f);
            }
            if (!set.matches("[a-z_]+")) throw new FileNotFoundException(set);
            String path = Frames.fpAssetIcon(c, set);
            if (path == null) throw new FileNotFoundException("no icon in " + set);
            return asset(c, path);
        }
        int idx;
        try { idx = Integer.parseInt(what); } catch (NumberFormatException e) { throw new FileNotFoundException(uri.toString()); }
        if (Frames.CUSTOM.equals(set)) {
            File f = charging ? Frames.frame(c, idx) : Frames.fpFrame(c, idx);
            if (f == null) throw new FileNotFoundException("no frames");
            return file(f);
        }
        if (!set.matches("[a-z_]+")) throw new FileNotFoundException(set);
        String path = charging ? Frames.assetPath(c, set, idx) : Frames.fpAssetPath(c, set, idx);
        if (path == null) throw new FileNotFoundException("no such set " + set);
        return asset(c, path);
    }

    private static AssetFileDescriptor file(File f) throws FileNotFoundException {
        return new AssetFileDescriptor(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY), 0, -1);
    }

    /** Serve an APK asset; compressed assets are streamed through a pipe. */
    private static AssetFileDescriptor asset(Context c, String path) throws FileNotFoundException {
        try { return c.getAssets().openFd(path); } catch (java.io.IOException compressed) { /* fall through */ }
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            final java.io.InputStream in = c.getAssets().open(path);
            final ParcelFileDescriptor write = pipe[1];
            new Thread(() -> {
                try (java.io.OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(write); java.io.InputStream i2 = in) {
                    byte[] buf = new byte[65536]; int r;
                    while ((r = i2.read(buf)) > 0) out.write(buf, 0, r);
                } catch (java.io.IOException ignored) { }
            }).start();
            return new AssetFileDescriptor(pipe[0], 0, -1);
        } catch (java.io.IOException e) { throw new FileNotFoundException(path); }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
