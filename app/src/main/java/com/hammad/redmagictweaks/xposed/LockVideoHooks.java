package com.hammad.redmagictweaks.xposed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.hammad.redmagictweaks.LockFeatures;
import com.hammad.redmagictweaks.LockVideo;
import com.hammad.redmagictweaks.LockVideoProvider;

import de.robv.android.xposed.XC_MethodHook;

/**
 * com.android.systemui — the lock screen video (LockFeatures.VIDEO_ON), like Samsung's: a TextureView + MediaPlayer
 * inside NotificationShadeWindowView, just below scrim_behind (above the wallpaper, ZTE's keyguard image views and the
 * media backdrop; below the clock, notifications, the bouncer and the unlock reveal). It is shown while the screen is on,
 * the keyguard showing and not occluded, not dozing (the AOD is untouched) and not going away; it never takes touches.
 *
 * State comes from KeyguardStateControllerImpl.notifyKeyguardState / notifyKeyguardGoingAway and
 * StatusBarKeyguardViewManager (onStartedWakingUp, onStartedGoingToSleep, onFinishedGoingToSleep, onDozingChanged,
 * setOccluded, show, hide, keyguardGoingAway, onKeyguardFadedAway), with SCREEN_ON / SCREEN_OFF / USER_PRESENT as a
 * fallback; all the same on China 11.0.11 and global EU 11.0.4.
 *
 * The clip comes from the module (LockVideoProvider): its file descriptor is opened once per clip version (the
 * "lock_video_version" setting, re-read on every wake / sleep, so no call to the module app on wake), and the first and
 * last frames are kept (half screen size) to show at once on wake and after the clip stops. The player only exists
 * while the video is visible; stopping on the last frame releases it.
 */
final class LockVideoHooks {
    private LockVideoHooks() {}

    private static final String SHADE = "com.android.systemui.shade.NotificationShadeWindowView";
    private static final String KSC = "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl";
    private static final String SBKVM = "com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager";
    private static final String TAG = "rmtweaks_lockvideo";
    private static final Uri URI = Uri.parse("content://" + LockVideoProvider.AUTHORITY);

    private static Handler main, work;
    private static Context ctx;
    private static FrameLayout box;
    private static TextureView tex;
    private static ImageView still;

    // keyguard / screen state
    private static boolean showing, occluded, dozing, goingAway, awake = true;
    private static boolean visible, playedSinceUnlock, pendingPlay;

    // the clip (main thread only after loading)
    private static ParcelFileDescriptor pfd;
    private static String loadedVersion, loadingVersion;
    private static int startMs, endMs, durationMs, videoW, videoH;
    private static Bitmap firstFrame, lastFrame;

    private static MediaPlayer mp;
    private static Surface surface;
    private static boolean loggedPlay;

    static void install(ClassLoader cl) {
        main = new Handler(Looper.getMainLooper());
        int n = 0;
        Class<?> shade = H.cls(cl, SHADE);
        if (shade != null) n += H.hookAll(shade, "onAttachedToWindow", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try { attach((ViewGroup) p.thisObject); } catch (Throwable t) { Lx.e("LockVideo: attach", t); }
            }
        });
        Class<?> ksc = H.cls(cl, KSC);
        if (ksc != null) {
            n += H.hookAll(ksc, "notifyKeyguardState", after(p -> {
                boolean s = (Boolean) p.args[0];
                if (showing && !s) playedSinceUnlock = false;          // unlocked: the next locked wake plays again
                showing = s;
                occluded = (Boolean) p.args[1];
                if (s) goingAway = false;
            }));
            n += H.hookAll(ksc, "notifyKeyguardGoingAway", after(p -> goingAway = (Boolean) p.args[0]));
        }
        Class<?> vm = H.cls(cl, SBKVM);
        if (vm != null) {
            n += H.hookAll(vm, "onStartedWakingUp", after(p -> { awake = true; dozing = false; }));
            n += H.hookAll(vm, "onStartedGoingToSleep", after(p -> awake = false));
            n += H.hookAll(vm, "onFinishedGoingToSleep", after(p -> awake = false));
            n += H.hookAll(vm, "onDozingChanged", after(p -> dozing = (Boolean) p.args[0]));
            n += H.hookAll(vm, "setOccluded", after(p -> occluded = (Boolean) p.args[0]));
            n += H.hookAll(vm, "show", after(p -> { showing = true; goingAway = false; }));
            n += H.hookAll(vm, "keyguardGoingAway", after(p -> goingAway = true));
            n += H.hookAll(vm, "onKeyguardFadedAway", after(p -> { goingAway = false; if (showing) playedSinceUnlock = false; showing = false; }));
        }
        Lx.i("LockVideo: hooks ready (" + n + ")");
    }

    private interface State { void apply(XC_MethodHook.MethodHookParam p); }

    /** After the method: update the state, then show / hide the video (on the main thread). */
    private static XC_MethodHook after(final State s) {
        return new XC_MethodHook() {
            @Override protected void afterHookedMethod(final MethodHookParam p) {
                try { s.apply(p); } catch (Throwable t) { Lx.e("LockVideo: state", t); }
                post(LockVideoHooks::update);
            }
        };
    }

    private static void post(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run(); else main.post(r);
    }

    // ------------------------------------------------------------------ the view

    private static void attach(ViewGroup root) {
        if (root.findViewWithTag(TAG) != null) return;
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
        // just below scrim_behind: above ZTE's keyguard image views and the media backdrop, below everything of the keyguard
        int idx = 0;
        int scrim = ctx.getResources().getIdentifier("scrim_behind", "id", ctx.getPackageName());
        if (scrim != 0) {
            View s = root.findViewById(scrim);
            if (s != null && s.getParent() == root) idx = root.indexOfChild(s);
        }
        root.addView(box, idx, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        ctx.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String a = i.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(a)) awake = false;
                else if (Intent.ACTION_SCREEN_ON.equals(a)) awake = true;
                else if (Intent.ACTION_USER_PRESENT.equals(a)) { playedSinceUnlock = false; showing = false; goingAway = false; }
                update();
            }
        }, f, null, main, Context.RECEIVER_NOT_EXPORTED);
        try { awake = ((PowerManager) ctx.getSystemService(Context.POWER_SERVICE)).isInteractive(); } catch (Throwable ignored) { }
        Lx.i("LockVideo: view added to the shade window at " + idx + (scrim != 0 ? " (below scrim_behind)" : ""));
        checkClip();
    }

    /** Show or hide according to the state; the main thread. */
    private static void update() {
        if (box == null) return;
        checkClip();
        boolean want = Cfg.on(LockFeatures.VIDEO_ON) && pfd != null && awake && showing && !occluded && !dozing && !goingAway;
        if (want == visible) return;
        visible = want;
        main.removeCallbacks(START);
        // a moment's wait: a fingerprint wake-and-unlock hides the keyguard right after waking, no flash of the video then
        if (want) main.postDelayed(START, 120); else stop(goingAway);
    }

    private static final Runnable START = () -> { if (visible) start(); };

    private static void start() {
        box.animate().cancel();
        box.setAlpha(1f);
        box.setVisibility(View.VISIBLE);
        boolean fill = Cfg.choice(LockFeatures.VIDEO_FIT) == 0;
        still.setScaleType(fill ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        boolean play = Cfg.choice(LockFeatures.VIDEO_WHEN) == 0 || !playedSinceUnlock;
        if (!play) {
            still.setImageBitmap(lastFrame != null ? lastFrame : firstFrame);
            still.setVisibility(View.VISIBLE);
            return;
        }
        playedSinceUnlock = true;
        still.setImageBitmap(firstFrame);
        still.setVisibility(firstFrame != null ? View.VISIBLE : View.GONE);
        transform();
        if (tex.getSurfaceTexture() != null) startPlayer(); else pendingPlay = true;
    }

    private static void stop(boolean fade) {
        pendingPlay = false;
        main.removeCallbacks(END_CHECK);
        releasePlayer();
        if (fade) {
            box.animate().alpha(0f).setDuration(200).withEndAction(LockVideoHooks::hideNow).start();
        } else hideNow();
    }

    private static void hideNow() {
        if (visible) return;
        box.setVisibility(View.GONE);
        box.setAlpha(1f);
        still.setImageBitmap(null);
    }

    /** TextureView stretches the video to the view: scale it back to the video's shape (crop to fill, or fit). */
    private static void transform() {
        if (tex == null || videoW <= 0 || videoH <= 0) return;
        float vw = tex.getWidth(), vh = tex.getHeight();
        if (vw <= 0 || vh <= 0) return;
        float aspect = (float) videoW / videoH;
        boolean fill = Cfg.choice(LockFeatures.VIDEO_FIT) == 0;
        float sx = 1f, sy = 1f;
        boolean wider = vw / vh > aspect;          // the view is wider than the video
        if (fill) { if (wider) sy = (vw / aspect) / vh; else sx = (vh * aspect) / vw; }
        else { if (wider) sx = (vh * aspect) / vw; else sy = (vw / aspect) / vh; }
        Matrix m = new Matrix();
        m.setScale(sx, sy, vw / 2f, vh / 2f);
        tex.setTransform(m);
    }

    // ------------------------------------------------------------------ the player

    private static void startPlayer() {
        pendingPlay = false;
        if (!visible || pfd == null || mp != null) return;
        SurfaceTexture st = tex.getSurfaceTexture();
        if (st == null) { pendingPlay = true; return; }
        try {
            final boolean sound = Cfg.on(LockFeatures.VIDEO_SOUND);
            final boolean loop = Cfg.choice(LockFeatures.VIDEO_END) == 1;
            surface = new Surface(st);
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
            mp.setDataSource(pfd.getFileDescriptor());
            mp.setSurface(surface);
            mp.setVolume(sound ? 1f : 0f, sound ? 1f : 0f);
            mp.setLooping(loop && startMs <= 0 && (durationMs <= 0 || endMs >= durationMs - 50));
            mp.setOnPreparedListener(p -> {
                if (mp != p) return;
                if (startMs > 0) p.seekTo(startMs, MediaPlayer.SEEK_CLOSEST);
                p.start();
                main.removeCallbacks(END_CHECK);
                main.postDelayed(END_CHECK, 100);
                if (!loggedPlay) { loggedPlay = true; Lx.i("LockVideo: playing " + (endMs - startMs) + " ms (" + videoW + "x" + videoH + (sound ? ", sound" : ", muted") + (loop ? ", loop" : "") + ")"); }
            });
            mp.setOnInfoListener((p, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && mp == p) still.setVisibility(View.GONE);
                return false;
            });
            mp.setOnCompletionListener(p -> { if (mp == p && !p.isLooping()) atEnd(); });
            mp.setOnErrorListener((p, what, extra) -> {
                Lx.w("LockVideo: player error " + what + "/" + extra);
                releasePlayer();
                if (visible) { still.setImageBitmap(lastFrame != null ? lastFrame : firstFrame); still.setVisibility(View.VISIBLE); }
                return true;
            });
            mp.prepareAsync();
        } catch (Throwable t) {
            Lx.e("LockVideo: can't play", t);
            releasePlayer();
        }
    }

    /** Clips that run past the chosen part (cut at key frames, or a whole file): stop or loop at endMs. */
    private static final Runnable END_CHECK = new Runnable() {
        @Override public void run() {
            if (mp == null || !visible) return;
            try {
                if (mp.isPlaying() && mp.getCurrentPosition() >= endMs) { atEnd(); return; }
            } catch (Throwable ignored) { }
            main.postDelayed(this, 80);
        }
    };

    private static void atEnd() {
        if (mp == null) return;
        if (Cfg.choice(LockFeatures.VIDEO_END) == 1) {
            try {
                mp.seekTo(startMs, MediaPlayer.SEEK_CLOSEST);
                if (!mp.isPlaying()) mp.start();
                main.removeCallbacks(END_CHECK);
                main.postDelayed(END_CHECK, 100);
            } catch (Throwable t) { releasePlayer(); }
            return;
        }
        // stop on the last frame: show it, let the decoder go
        main.removeCallbacks(END_CHECK);
        if (lastFrame != null) { still.setImageBitmap(lastFrame); still.setVisibility(View.VISIBLE); }
        else try { mp.pause(); return; } catch (Throwable ignored) { }
        releasePlayer();
    }

    private static void releasePlayer() {
        main.removeCallbacks(END_CHECK);
        MediaPlayer p = mp;
        mp = null;
        if (p != null) try { p.release(); } catch (Throwable ignored) { }
        if (surface != null) { try { surface.release(); } catch (Throwable ignored) { } surface = null; }
    }

    // ------------------------------------------------------------------ the clip

    /** Reloads the clip in the background when the module's clip version differs from the loaded one. */
    private static void checkClip() {
        if (ctx == null || !Cfg.on(LockFeatures.VIDEO_ON)) return;     // nothing to load (no call to the module app at boot)
        String v = Cfg.str(LockVideo.VERSION_KEY);
        if (v == null) v = "";
        if (v.equals(loadedVersion) || v.equals(loadingVersion)) return;
        loadingVersion = v;
        if (work == null) {
            HandlerThread t = new HandlerThread("RMTweaks-lockvideo");
            t.start();
            work = new Handler(t.getLooper());
        }
        final String version = v;
        work.post(() -> load(version));
    }

    private static void load(String version) {
        ParcelFileDescriptor fd = null;
        Bitmap first = null, last = null;
        int s = 0, e = 0, d = 0, w = 0, h = 0;
        try {
            Bundle b = ctx.getContentResolver().call(URI, "info", null, null);
            if (b != null && b.getInt("present") == 1) {
                s = b.getInt("start"); e = b.getInt("end"); d = b.getInt("duration"); w = b.getInt("width"); h = b.getInt("height");
                fd = ctx.getContentResolver().openFileDescriptor(Uri.withAppendedPath(URI, "clip"), "r");
                if (fd != null) {
                    DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                    int tw = Math.max(1, dm.widthPixels / 2), th = Math.max(1, dm.heightPixels / 2);
                    MediaMetadataRetriever r = new MediaMetadataRetriever();
                    try {
                        r.setDataSource(fd.getFileDescriptor());
                        MediaMetadataRetriever.BitmapParams bp = new MediaMetadataRetriever.BitmapParams();
                        bp.setPreferredConfig(Bitmap.Config.RGB_565);
                        first = r.getScaledFrameAtTime(s * 1000L, MediaMetadataRetriever.OPTION_CLOSEST, tw, th, bp);
                        last = r.getScaledFrameAtTime(Math.max(s, e - 40) * 1000L, MediaMetadataRetriever.OPTION_CLOSEST, tw, th, bp);
                    } catch (Throwable t) {
                        Lx.w("LockVideo: no still frames: " + t);
                    } finally {
                        try { r.release(); } catch (Throwable ignored) { }
                    }
                }
            }
        } catch (Throwable t) {
            Lx.e("LockVideo: clip not readable", t);
        }
        final ParcelFileDescriptor nfd = fd;
        final Bitmap f1 = first, f2 = last;
        final int fs = s, fe = e, fdur = d, fw = w, fh = h;
        main.post(() -> {
            if (!version.equals(loadingVersion)) { close(nfd); return; }   // a newer clip is on its way
            if (visible) { visible = false; stop(false); }
            close(pfd);
            pfd = nfd;
            startMs = fs; endMs = fe; durationMs = fdur; videoW = fw; videoH = fh;
            firstFrame = f1; lastFrame = f2;
            loadedVersion = version;
            loadingVersion = null;
            loggedPlay = false;
            Lx.i("LockVideo: clip " + (nfd == null ? "none" : (fe - fs) + " ms, " + fw + "x" + fh + (f1 != null ? ", stills ready" : "")));
            update();
        });
    }

    private static void close(ParcelFileDescriptor fd) {
        if (fd != null) try { fd.close(); } catch (Throwable ignored) { }
    }
}
