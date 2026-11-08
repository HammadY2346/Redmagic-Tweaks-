package com.hammad.redmagictweaks.xposed;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.hammad.redmagictweaks.AodFeatures;
import com.hammad.redmagictweaks.AodVideo;
import com.hammad.redmagictweaks.LockVideoProvider;

import java.lang.ref.WeakReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * com.android.systemui — a video behind the always-on display (AodFeatures.VIDEO_ON).
 *
 * ZTE's com.android.systemui.doze.AodViewController owns the AOD window: mAodView is its full-screen root, the clock sits
 * in mPositionView / mAodContentView inside it, and onDreamingStarted / onDreamingStopped (also hideAodView) show and
 * hide it. Checked against global SystemUI_Redmagic_abroad (RedMagic OS 10.5.8 GB); AodHooks uses the same class on
 * China 11.0.11. A FrameLayout (TextureView + an ImageView for the first / last frame) goes in at index 0 of mAodView,
 * behind the clock, so the clock, notification icons, burn-in shift and the AOD options keep working.
 *
 * Doze lets the CPU sleep, which would freeze the clip: AodViewController.acquireWakeLock(ms), ZTE's own call for its
 * animated styles, keeps it awake for the clip's length (and again each round when looping). At the end the box fades
 * back to the normal AOD, keeps the last frame, or loops (AodFeatures.VIDEO_END). The clip comes from the module
 * (LockVideoProvider "aod_info" / .../aod), reloaded only when the "aod_video_version" setting changes.
 */
final class AodVideoHooks {
    private AodVideoHooks() {}

    private static final String CTL = "com.android.systemui.doze.AodViewController";
    private static final String TAG = "rmtweaks_aodvideo";
    private static final Uri URI = Uri.parse("content://" + LockVideoProvider.AUTHORITY);
    private static final int END_ONCE = 0, END_LAST = 1, END_LOOP = 2;

    private static Handler main, work;
    private static Context ctx;
    private static WeakReference<Object> controller = new WeakReference<>(null);
    private static FrameLayout box;
    private static TextureView tex;
    private static ImageView still;

    private static boolean visible, pendingPlay, loggedPlay, loggedRoot;

    // the clip (main thread only after loading)
    private static ParcelFileDescriptor pfd;
    private static String loadedVersion, loadingVersion;
    private static int durationMs, videoW, videoH;
    private static Bitmap firstFrame, lastFrame;

    private static MediaPlayer mp;
    private static Surface surface;

    static void install(ClassLoader cl) {
        main = new Handler(Looper.getMainLooper());
        Class<?> ctl = H.cls(cl, CTL);
        if (ctl == null) { Lx.w("AodVideo: " + CTL + " not found"); return; }
        int n = 0;
        n += H.hookAll(ctl, "onDreamingStarted", new XC_MethodHook() {
            @Override protected void afterHookedMethod(final MethodHookParam p) {
                final Object c = p.thisObject;
                post(() -> { try { show(c); } catch (Throwable t) { Lx.e("AodVideo: show", t); } });
            }
        });
        XC_MethodHook hide = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                post(() -> { try { hide(); } catch (Throwable t) { Lx.e("AodVideo: hide", t); } });
            }
        };
        n += H.hookAll(ctl, "onDreamingStopped", hide);
        n += H.hookAll(ctl, "hideAodView", hide);
        n += H.hookAll(ctl, "onDestroy", hide);
        Lx.i("AodVideo: hooks ready (" + n + ")");
    }

    private static void post(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run(); else main.post(r);
    }

    // ------------------------------------------------------------------ show / hide

    private static void show(Object ctl) {
        if (!Cfg.on(AodFeatures.VIDEO_ON)) { if (visible) hide(); return; }
        Object v = XposedHelpers.getObjectField(ctl, "mAodView");
        if (!(v instanceof ViewGroup) || v instanceof LinearLayout) {
            if (!loggedRoot) { loggedRoot = true; Lx.w("AodVideo: mAodView is " + (v == null ? "null" : v.getClass().getName()) + ", no room for a video"); }
            return;
        }
        controller = new WeakReference<>(ctl);
        attach((ViewGroup) v);
        visible = true;
        checkClip();
        if (pfd != null) start();
    }

    private static void hide() {
        visible = false;
        pendingPlay = false;
        main.removeCallbacks(FADE_OUT);
        releasePlayer();
        if (box != null) {
            box.animate().cancel();
            box.setVisibility(View.GONE);
            box.setAlpha(1f);
            still.setImageBitmap(null);
        }
    }

    /** The box at index 0 of the AOD root (again when ZTE builds a new AOD window). */
    private static void attach(ViewGroup root) {
        if (box == null) {
            ctx = root.getContext();
            box = new FrameLayout(ctx);
            box.setTag(TAG);
            box.setBackgroundColor(Color.BLACK);
            box.setVisibility(View.GONE);
            box.setClickable(false);
            box.setFocusable(false);
            box.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            tex = new TextureView(ctx);
            tex.setOpaque(false);
            tex.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) { transform(); if (pendingPlay) startPlayer(); }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { transform(); }
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { releasePlayer(); return true; }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }
            });
            box.addView(tex, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            still = new ImageView(ctx);
            box.addView(still, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (box.getParent() == root && root.indexOfChild(box) == 0) return;
        if (box.getParent() instanceof ViewGroup) ((ViewGroup) box.getParent()).removeView(box);
        root.addView(box, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Lx.i("AodVideo: view added behind the AOD clock (" + root.getClass().getSimpleName() + ")");
    }

    private static void start() {
        if (!visible || box == null || pfd == null) return;
        main.removeCallbacks(FADE_OUT);
        box.animate().cancel();
        box.setAlpha(1f);
        box.setVisibility(View.VISIBLE);
        boolean fill = Cfg.choice(AodFeatures.VIDEO_FIT) == 1;
        still.setScaleType(fill ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        still.setImageBitmap(firstFrame);
        still.setVisibility(firstFrame != null ? View.VISIBLE : View.GONE);
        transform();
        if (tex.getSurfaceTexture() != null) startPlayer(); else pendingPlay = true;
    }

    /** TextureView stretches the video to the view: scale it back to the video's shape (crop to fill, or fit). */
    private static void transform() {
        if (tex == null || videoW <= 0 || videoH <= 0) return;
        float vw = tex.getWidth(), vh = tex.getHeight();
        if (vw <= 0 || vh <= 0) return;
        float aspect = (float) videoW / videoH;
        boolean fill = Cfg.choice(AodFeatures.VIDEO_FIT) == 1;
        float sx = 1f, sy = 1f;
        boolean wider = vw / vh > aspect;
        if (fill) { if (wider) sy = (vw / aspect) / vh; else sx = (vh * aspect) / vw; }
        else { if (wider) sx = (vh * aspect) / vw; else sy = (vw / aspect) / vh; }
        Matrix m = new Matrix();
        m.setScale(sx, sy, vw / 2f, vh / 2f);
        tex.setTransform(m);
    }

    // ------------------------------------------------------------------ the player

    /** ZTE's own AOD wake lock, so doze doesn't freeze the clip; ms = how long. */
    private static void stayAwake(int ms) {
        Object c = controller.get();
        if (c == null || ms <= 0) return;
        try { XposedHelpers.callMethod(c, "acquireWakeLock", ms); } catch (Throwable t) { Lx.w("AodVideo: no wake lock (" + t + ")"); }
    }

    private static void startPlayer() {
        pendingPlay = false;
        if (!visible || pfd == null || mp != null) return;
        SurfaceTexture st = tex.getSurfaceTexture();
        if (st == null) { pendingPlay = true; return; }
        try {
            surface = new Surface(st);
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
            mp.setDataSource(pfd.getFileDescriptor());
            mp.setSurface(surface);
            mp.setVolume(0f, 0f);
            mp.setLooping(false);
            mp.setOnPreparedListener(p -> {
                if (mp != p) return;
                stayAwake(durationMs + 600);
                p.start();
                if (!loggedPlay) { loggedPlay = true; Lx.i("AodVideo: playing " + durationMs + " ms (" + videoW + "x" + videoH + ", end mode " + Cfg.choice(AodFeatures.VIDEO_END) + ")"); }
            });
            mp.setOnInfoListener((p, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && mp == p) still.setVisibility(View.GONE);
                return false;
            });
            mp.setOnCompletionListener(p -> { if (mp == p) atEnd(); });
            mp.setOnErrorListener((p, what, extra) -> {
                Lx.w("AodVideo: player error " + what + "/" + extra);
                releasePlayer();
                if (visible) fadeOut();
                return true;
            });
            mp.prepareAsync();
        } catch (Throwable t) {
            Lx.e("AodVideo: can't play", t);
            releasePlayer();
        }
    }

    private static void atEnd() {
        if (mp == null) return;
        int mode = Cfg.choice(AodFeatures.VIDEO_END);
        if (mode == END_LOOP && visible) {
            try {
                stayAwake(durationMs + 600);
                mp.seekTo(0);
                mp.start();
                return;
            } catch (Throwable t) { releasePlayer(); }
        }
        if (mode == END_LAST) {
            // keep the last frame as a still (decoder released, nothing runs while it shows)
            if (lastFrame != null) { still.setImageBitmap(lastFrame); still.setVisibility(View.VISIBLE); }
            releasePlayer();
            return;
        }
        releasePlayer();
        fadeOut();
    }

    private static final Runnable FADE_OUT = AodVideoHooks::fadeOut;

    /** Play once: back to the normal (black) AOD under the clock. */
    private static void fadeOut() {
        if (box == null) return;
        box.animate().alpha(0f).setDuration(400).withEndAction(() -> {
            if (box == null) return;
            box.setVisibility(View.GONE);
            box.setAlpha(1f);
            still.setImageBitmap(null);
        }).start();
    }

    private static void releasePlayer() {
        MediaPlayer p = mp;
        mp = null;
        if (p != null) try { p.release(); } catch (Throwable ignored) { }
        if (surface != null) { try { surface.release(); } catch (Throwable ignored) { } surface = null; }
    }

    // ------------------------------------------------------------------ the clip

    /** Reloads the clip in the background when the module's clip version differs from the loaded one. */
    private static void checkClip() {
        if (ctx == null) return;
        String v = Cfg.str(AodVideo.VERSION_KEY);
        if (v == null) v = "";
        if (v.equals(loadedVersion) || v.equals(loadingVersion)) return;
        loadingVersion = v;
        if (work == null) {
            HandlerThread t = new HandlerThread("RMTweaks-aodvideo");
            t.start();
            work = new Handler(t.getLooper());
        }
        final String version = v;
        work.post(() -> load(version));
    }

    private static void load(String version) {
        ParcelFileDescriptor fd = null;
        Bitmap first = null, last = null;
        int d = 0, w = 0, h = 0;
        try {
            Bundle b = ctx.getContentResolver().call(URI, "aod_info", null, null);
            if (b != null && b.getInt("present") == 1) {
                d = b.getInt("duration"); w = b.getInt("width"); h = b.getInt("height");
                fd = ctx.getContentResolver().openFileDescriptor(Uri.withAppendedPath(URI, "aod"), "r");
                if (fd != null) {
                    DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                    int tw = Math.max(1, dm.widthPixels / 2), th = Math.max(1, dm.heightPixels / 2);
                    MediaMetadataRetriever r = new MediaMetadataRetriever();
                    try {
                        r.setDataSource(fd.getFileDescriptor());
                        MediaMetadataRetriever.BitmapParams bp = new MediaMetadataRetriever.BitmapParams();
                        bp.setPreferredConfig(Bitmap.Config.RGB_565);
                        first = r.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST, tw, th, bp);
                        last = r.getScaledFrameAtTime(Math.max(0, d - 40) * 1000L, MediaMetadataRetriever.OPTION_CLOSEST, tw, th, bp);
                    } catch (Throwable t) {
                        Lx.w("AodVideo: no still frames: " + t);
                    } finally {
                        try { r.release(); } catch (Throwable ignored) { }
                    }
                }
            }
        } catch (Throwable t) {
            Lx.e("AodVideo: clip not readable", t);
        }
        final ParcelFileDescriptor nfd = fd;
        final Bitmap f1 = first, f2 = last;
        final int fdur = d, fw = w, fh = h;
        main.post(() -> {
            if (!version.equals(loadingVersion)) { close(nfd); return; }   // a newer clip is on its way
            releasePlayer();
            close(pfd);
            pfd = nfd;
            durationMs = fdur; videoW = fw; videoH = fh;
            firstFrame = f1; lastFrame = f2;
            loadedVersion = version;
            loadingVersion = null;
            loggedPlay = false;
            Lx.i("AodVideo: clip " + (nfd == null ? "none" : fdur + " ms, " + fw + "x" + fh + (f1 != null ? ", stills ready" : "")));
            if (visible) start();     // the AOD is up already: play now
        });
    }

    private static void close(ParcelFileDescriptor fd) {
        if (fd != null) try { fd.close(); } catch (Throwable ignored) { }
    }
}
