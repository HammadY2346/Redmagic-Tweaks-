package com.hammad.redmagictweaks;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * The always-on display video (AodFeatures.VIDEO): picked in the app (ui/CategoryActivity), copied whole into the app's
 * files, served read-only to System UI by LockVideoProvider ("aod_info" / content://…lockvideo/aod) and played behind the
 * AOD clock by xposed/AodVideoHooks. Short clips only: the AOD keeps the phone awake while a clip plays.
 */
public final class AodVideo {
    private AodVideo() {}

    public static final long MAX_BYTES = 100L * 1024 * 1024;
    public static final int MAX_MS = 60000;
    /** Settings key bumped whenever the clip changes: System UI reloads the clip when it differs. */
    public static final String VERSION_KEY = "aod_video_version";

    public static final class Meta {
        public long version;
        public int durationMs, width, height;
        public String name;
    }

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "aodvideo");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }
    public static File clip(Context c) { return new File(dir(c), "clip.mp4"); }
    private static File metaFile(Context c) { return new File(dir(c), "clip.json"); }

    public static synchronized Meta meta(Context c) {
        File f = metaFile(c);
        if (!f.isFile() || clip(c).length() == 0) return null;
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), 8192)];
            int n = in.read(b);
            JSONObject o = new JSONObject(new String(b, 0, Math.max(0, n), "UTF-8"));
            Meta m = new Meta();
            m.version = o.optLong("version");
            m.durationMs = o.optInt("duration");
            m.width = o.optInt("width");
            m.height = o.optInt("height");
            m.name = o.optString("name", "");
            return m.durationMs > 0 ? m : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Button text of the action row. */
    public static String label(Context c) {
        Meta m = meta(c);
        if (m == null) return "Pick a video";
        return String.format(Locale.US, "%.1f s video", m.durationMs / 1000f);
    }

    /** Copies the picked video in and turns the AOD video on. Null when it worked, otherwise the reason. Not on the UI thread. */
    public static synchronized String importFrom(Context c, Uri uri) {
        File tmp = new File(dir(c), "import.tmp");
        try (InputStream in = c.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) return "The video can't be opened.";
            byte[] buf = new byte[1 << 16];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BYTES) {
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                    return "The video is bigger than 100 MB. Pick a shorter clip.";
                }
                out.write(buf, 0, n);
            }
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return "Copying the video failed: " + e.getMessage();
        }
        int dur, w, h;
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(tmp.getAbsolutePath());
            if (!"yes".equals(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                return "That file has no video in it.";
            }
            dur = num(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            w = num(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            h = num(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            int rot = num(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
            if (rot == 90 || rot == 270) { int t = w; w = h; h = t; }
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return "Android can't read this video (" + e.getMessage() + ").";
        } finally {
            try { r.release(); } catch (Exception ignored) { }
        }
        if (dur <= 0) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return "The video has no length.";
        }
        if (dur > MAX_MS) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return String.format(Locale.US, "The video is %.0f s long; the AOD video can be at most 60 s.", dur / 1000f);
        }
        File clip = clip(c);
        //noinspection ResultOfMethodCallIgnored
        clip.delete();
        if (!tmp.renameTo(clip)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return "The video could not be saved.";
        }
        Meta m = new Meta();
        m.version = System.currentTimeMillis();
        m.durationMs = dur; m.width = w; m.height = h;
        m.name = displayName(c, uri);
        try (OutputStream out = new FileOutputStream(metaFile(c))) {
            JSONObject o = new JSONObject();
            o.put("version", m.version).put("duration", m.durationMs).put("width", m.width).put("height", m.height).put("name", m.name);
            out.write(o.toString().getBytes("UTF-8"));
        } catch (Exception e) {
            return "The video could not be saved.";
        }
        Prefs.set(c, AodFeatures.VIDEO_ON, true);
        Prefs.setStr(c, VERSION_KEY, String.valueOf(m.version));
        return null;
    }

    /** Forgets the clip and turns the AOD video off. */
    public static synchronized void remove(Context c) {
        //noinspection ResultOfMethodCallIgnored
        clip(c).delete();
        //noinspection ResultOfMethodCallIgnored
        metaFile(c).delete();
        Prefs.set(c, AodFeatures.VIDEO_ON, false);
        Prefs.setStr(c, VERSION_KEY, String.valueOf(System.currentTimeMillis()));
    }

    private static int num(String s) {
        try { return s == null ? 0 : (int) Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }

    private static String displayName(Context c, Uri uri) {
        try (Cursor cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception ignored) { }
        String p = uri.getLastPathSegment();
        return p == null ? "" : p;
    }
}
