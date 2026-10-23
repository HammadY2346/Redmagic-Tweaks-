package com.hammad.redmagictweaks;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;

/**
 * The lock screen video clip (LockFeatures.VIDEO): imported by ui/LockVideoActivity, served read-only to System UI by
 * LockVideoProvider, played by xposed/LockVideoHooks.
 *
 * The chosen part (at most 20 s) is cut out of the picked video without re-encoding (MediaExtractor -> MediaMuxer):
 * the cut starts at the key frame before the chosen start and runs to the key frame after the chosen end, and the
 * clip's own start / end (in its timeline) are kept in clip.json so playback shows exactly the chosen part. A video the
 * MP4 muxer can't take (WebM / VP9 …) is copied whole instead (up to 400 MB) and played from start to end.
 */
public final class LockVideo {
    private LockVideo() {}

    public static final int MAX_MS = 20000, MIN_MS = 1000;
    private static final long COPY_LIMIT = 400L * 1024 * 1024;
    /** Settings key bumped whenever the clip changes: System UI reloads the clip when it differs (no call per wake). */
    public static final String VERSION_KEY = "lock_video_version";

    /** What System UI needs to play the clip (all times in the clip's own timeline, ms). */
    public static final class Meta {
        public long version;
        public int startMs, endMs, durationMs, width, height;
        public boolean trimmed;
    }

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "lockvideo");
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
            m.startMs = o.optInt("start");
            m.endMs = o.optInt("end");
            m.durationMs = o.optInt("duration");
            m.width = o.optInt("width");
            m.height = o.optInt("height");
            m.trimmed = o.optBoolean("trimmed");
            return m.endMs > m.startMs ? m : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeMeta(Context c, Meta m) throws Exception {
        JSONObject o = new JSONObject();
        o.put("version", m.version).put("start", m.startMs).put("end", m.endMs).put("duration", m.durationMs)
                .put("width", m.width).put("height", m.height).put("trimmed", m.trimmed);
        try (OutputStream out = new FileOutputStream(metaFile(c))) { out.write(o.toString().getBytes("UTF-8")); }
    }

    /** Value text of the "Lock screen video…" row. */
    public static String label(Context c) {
        Meta m = meta(c);
        if (m == null) return "Pick a video";
        return String.format(Locale.US, "%.1f s clip", (m.endMs - m.startMs) / 1000f);
    }

    public static synchronized void remove(Context c) {
        //noinspection ResultOfMethodCallIgnored
        clip(c).delete();
        //noinspection ResultOfMethodCallIgnored
        metaFile(c).delete();
        Prefs.set(c, LockFeatures.VIDEO_ON, false);
        Prefs.setStr(c, VERSION_KEY, String.valueOf(System.currentTimeMillis()));
    }

    /** Length, size (as shown, rotation applied) of a video: {durationMs, width, height, rotation}. */
    public static int[] probe(Context c, Uri uri) throws IOException {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(c, uri);
            int dur = parse(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            int w = parse(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int h = parse(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            int rot = parse(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
            if (!"yes".equals(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))) throw new IOException("This file has no video.");
            if (rot == 90 || rot == 270) { int t = w; w = h; h = t; }
            return new int[]{dur, w, h, rot};
        } catch (RuntimeException e) {
            throw new IOException("Can't read this video (" + e.getMessage() + ").");
        } finally {
            try { r.release(); } catch (Exception ignored) { }
        }
    }

    private static int parse(String s) {
        try { return s == null ? 0 : (int) Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    /**
     * Makes [startMs, endMs] of the video at uri the lock screen clip and turns the video on. Worker thread; throws
     * with a message that can be shown.
     */
    public static synchronized Meta importClip(Context c, Uri uri, int startMs, int endMs) throws Exception {
        int[] p = probe(c, uri);
        int dur = p[0];
        if (dur > 0) endMs = Math.min(endMs, dur);
        if (endMs - startMs > MAX_MS) endMs = startMs + MAX_MS;
        if (endMs - startMs < MIN_MS) throw new IOException("Choose at least one second.");
        File tmp = new File(dir(c), "clip.tmp");
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
        Meta m = new Meta();
        m.width = p[1];
        m.height = p[2];
        try {
            long[] cut = remux(c, uri, startMs * 1000L, endMs * 1000L, p[3], tmp);
            m.startMs = (int) ((startMs * 1000L - cut[0]) / 1000);
            m.endMs = (int) ((endMs * 1000L - cut[0]) / 1000);
            m.durationMs = (int) (cut[1] / 1000);
            m.trimmed = true;
            if (m.durationMs > 0) m.endMs = Math.min(m.endMs, m.durationMs);
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            copyWhole(c, uri, tmp);
            m.startMs = startMs;
            m.endMs = endMs;
            m.durationMs = dur;
            m.trimmed = false;
        }
        File clip = clip(c);
        //noinspection ResultOfMethodCallIgnored
        clip.delete();
        if (!tmp.renameTo(clip)) throw new IOException("Can't save the clip.");
        //noinspection ResultOfMethodCallIgnored
        clip.setReadable(true, false);
        m.version = System.currentTimeMillis();
        writeMeta(c, m);
        Prefs.set(c, LockFeatures.VIDEO_ON, true);
        Prefs.setStr(c, VERSION_KEY, String.valueOf(m.version));
        return m;
    }

    /** Copies samples of the video (and its first audio track) from the key frame before startUs to the one after endUs.
     *  Returns {clip start in the source timeline (µs), clip duration (µs)}. */
    private static long[] remux(Context c, Uri uri, long startUs, long endUs, int rotation, File out) throws Exception {
        MediaExtractor probe = new MediaExtractor();
        long t0;
        int videoTrack = -1;
        try {
            probe.setDataSource(c, uri, null);
            for (int i = 0; i < probe.getTrackCount(); i++) {
                String mime = probe.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) { videoTrack = i; break; }
            }
            if (videoTrack < 0) throw new IOException("no video track");
            probe.selectTrack(videoTrack);
            probe.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            t0 = probe.getSampleTime();
            if (t0 < 0) throw new IOException("can't seek");
        } finally {
            probe.release();
        }

        MediaExtractor ex = new MediaExtractor();
        MediaMuxer mux = null;
        boolean started = false;
        try {
            ex.setDataSource(c, uri, null);
            mux = new MediaMuxer(out.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int n = ex.getTrackCount();
            int[] map = new int[n];
            Arrays.fill(map, -1);
            boolean[] isVideo = new boolean[n];
            int maxIn = 2 << 20, selected = 0;
            boolean audio = false;
            for (int i = 0; i < n; i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                if (i == videoTrack) {
                    map[i] = mux.addTrack(f);
                    isVideo[i] = true;
                } else if (mime.startsWith("audio/") && !audio) {
                    try { map[i] = mux.addTrack(f); audio = true; } catch (Exception unsupported) { continue; }
                } else continue;
                ex.selectTrack(i);
                selected++;
                if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) maxIn = Math.max(maxIn, f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
                if (i == videoTrack && f.containsKey(MediaFormat.KEY_ROTATION)) rotation = f.getInteger(MediaFormat.KEY_ROTATION);
            }
            if (rotation != 0) mux.setOrientationHint(rotation);
            ex.seekTo(t0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            mux.start();
            started = true;
            ByteBuffer buf = ByteBuffer.allocateDirect(maxIn);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long last = 0;
            int written = 0;
            while (selected > 0) {
                int ti = ex.getSampleTrackIndex();
                if (ti < 0) break;
                long t = ex.getSampleTime();
                if (map[ti] < 0) { ex.advance(); continue; }
                boolean sync = (ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0;
                // video: up to the next key frame after the end (B-frames before it may need it); audio: half a second past
                if (isVideo[ti] ? (t > endUs && sync) : t > endUs + 500_000) {
                    ex.unselectTrack(ti);
                    map[ti] = -1;
                    selected--;
                    continue;
                }
                if (t < t0) { ex.advance(); continue; }
                info.offset = 0;
                info.size = ex.readSampleData(buf, 0);
                if (info.size < 0) break;
                info.presentationTimeUs = t - t0;
                info.flags = sync ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                mux.writeSampleData(map[ti], buf, info);
                last = Math.max(last, t - t0);
                written++;
                ex.advance();
            }
            if (written == 0) throw new IOException("nothing to cut");
            mux.stop();
            started = false;
            return new long[]{t0, last};
        } finally {
            try { if (started) mux.stop(); } catch (Exception ignored) { }
            try { if (mux != null) mux.release(); } catch (Exception ignored) { }
            ex.release();
        }
    }

    private static void copyWhole(Context c, Uri uri, File out) throws IOException {
        try (AssetFileDescriptor afd = c.getContentResolver().openAssetFileDescriptor(uri, "r")) {
            if (afd == null) throw new IOException("Can't open this video.");
            long len = afd.getLength();
            if (len > COPY_LIMIT) throw new IOException("This video can't be cut here and is too big to use whole. Trim it to 20 seconds in a video editor first.");
            try (InputStream in = afd.createInputStream(); OutputStream o = new FileOutputStream(out)) {
                byte[] b = new byte[1 << 16];
                long total = 0;
                int r;
                while ((r = in.read(b)) > 0) {
                    total += r;
                    if (total > COPY_LIMIT) throw new IOException("This video is too big to use whole. Trim it to 20 seconds in a video editor first.");
                    o.write(b, 0, r);
                }
            }
        }
    }
}
