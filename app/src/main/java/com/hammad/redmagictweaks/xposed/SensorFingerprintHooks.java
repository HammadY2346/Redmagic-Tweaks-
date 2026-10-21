package com.hammad.redmagictweaks.xposed;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;

import com.hammad.redmagictweaks.FingerprintCustomFeatures;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * com.fingerprint.sensorservice — custom under-display fingerprint animation and idle icon, hook targets ported
 * from nubia-ly's SensorAnimationBackend / SensorFingerprintBackend (validated on this firmware).
 *
 * <p>Animation: {@code view.AnimationView.drawView()} (AnimationView.java:88) blits {@code mBitmaps[mCurrentIndex]} with
 * {@code canvas.drawBitmap(bitmap, src, dst, mPaint)} onto its SurfaceView every {@code mDuration} ms until
 * {@code totalCount}. We do not touch the stock bitmaps: a before-hook on drawView picks our frame for the current
 * progress and a hook on {@code Canvas.drawBitmap(Bitmap, Rect, Rect, Paint)} swaps the bitmap / source rectangle
 * for that one call (thread-local, same thread). Position and duration therefore stay the phone's own.
 *
 * <p>Icon: {@code view.FloatWindowSensorView} sets the idle picture with {@code mSensorView.setBackgroundResource(...)}
 * from {@code setSensorViewIconForRedMagic(boolean fromSystemUi, int keyguardColor, String style)} (:373),
 * {@code updateSensorViewIcon(int keyguardColor, boolean fromSystemUi)} (:1073) and
 * {@code updateSensorViewIconForStyle(int, boolean, String)} (:1100); after each one we replace (or clear) the
 * background when the sensor is shown for the keyguard — never for in-app prompts, quick-app or doze.
 */
final class SensorFingerprintHooks {
    private SensorFingerprintHooks() {}

    private static final String BASE = "com.fingerprint.sensorservice.";
    private static final String ANIMATION_VIEW = BASE + "view.AnimationView";
    private static final String SENSOR_VIEW = BASE + "view.FloatWindowSensorView";
    private static final String SERVICE = BASE + "UdfpsMessageService";

    private static WeakReference<Object> service = new WeakReference<>(null);

    static void install(ClassLoader cl) {
        serviceRef(cl);
        animation(cl);
        icon(cl);
    }

    private static Context ctx(Object view) {
        Context c = Lx.context();
        if (c == null && view instanceof View) c = ((View) view).getContext();
        return c;
    }

    // ------------------------------------------------------------------------------------------------ service

    private static void serviceRef(ClassLoader cl) {
        Class<?> svc = H.cls(cl, SERVICE);
        if (svc == null) return;
        // public void onCreate() / onDestroy() — UdfpsMessageService; fields "private boolean mIsDoze", "private boolean isFingerprintDisabled"
        H.hookAll(svc, "onCreate", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { service = new WeakReference<>(p.thisObject); }
        });
        H.hookAll(svc, "onDestroy", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { service = new WeakReference<>(null); FpArt.drop(); }
        });
    }

    /** nubia-ly gate: no custom graphic while dozing or when the vendor disabled the fingerprint. */
    private static boolean serviceAllows() {
        Object s = service.get();
        if (s == null) return true;    // not observed yet: the setters only run for a live sensor window anyway
        try {
            if (Boolean.TRUE.equals(XposedHelpers.getObjectField(s, "mIsDoze"))) return false;
            if (Boolean.TRUE.equals(XposedHelpers.getObjectField(s, "isFingerprintDisabled"))) return false;
        } catch (Throwable t) { Lx.w("fingerprint: service state unreadable: " + t); }
        return true;
    }

    // ------------------------------------------------------------------------------------------------ animation

    /** One substituted draw: set in drawView's before-hook, consumed by the Canvas.drawBitmap hook on the same thread. */
    private static final class Draw {
        final Bitmap original, replacement; final Paint paint; final Rect source; final int percent;
        Draw(Bitmap original, Paint paint, Bitmap replacement, Rect source, int percent) {
            this.original = original; this.paint = paint; this.replacement = replacement; this.source = source; this.percent = percent;
        }
    }
    private static final ThreadLocal<Draw> draw = new ThreadLocal<>();
    private static long drawn, lastLog;

    private static void animation(ClassLoader cl) {
        final Class<?> av = H.cls(cl, ANIMATION_VIEW);
        if (av == null) return;
        Object lockObj = null;
        try { lockObj = XposedHelpers.getStaticObjectField(av, "M_RESOURCE_LOCK"); } catch (Throwable t) { Lx.w("fingerprint: M_RESOURCE_LOCK missing: " + t); }
        final Object lock = lockObj;

        // private void drawView()  — AnimationView.java:88
        boolean ok = H.hook(H.find(av, "drawView"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                draw.remove();
                try {
                    if (!Cfg.on(FingerprintCustomFeatures.FP_ANIM)) { FpArt.drop(); return; }
                    View view = (View) p.thisObject;
                    if (Boolean.TRUE.equals(XposedHelpers.getObjectField(view, "isDestroyed")) || !Boolean.TRUE.equals(XposedHelpers.getObjectField(view, "isStart"))) return;
                    FpArt.Clip clip = FpArt.clip(ctx(view));
                    if (clip == null) return;
                    int index = XposedHelpers.getIntField(view, "mCurrentIndex");
                    int total = XposedHelpers.getIntField(view, "totalCount");
                    int duration = XposedHelpers.getIntField(view, "mDuration");
                    if (index < 0 || index >= total) return;
                    Bitmap original;
                    Object l = lock != null ? lock : view;
                    synchronized (l) {
                        Object list = XposedHelpers.getObjectField(view, "mBitmaps");
                        if (!(list instanceof ArrayList) || index >= ((ArrayList<?>) list).size()) return;
                        Object b = ((ArrayList<?>) list).get(index);
                        if (!(b instanceof Bitmap) || ((Bitmap) b).isRecycled()) return;
                        original = (Bitmap) b;
                    }
                    Object paint = XposedHelpers.getObjectField(view, "mPaint");
                    if (!(paint instanceof Paint)) return;
                    int frame = clip.frameFor(index, total, duration);
                    if (frame < 0) return;
                    Bitmap replacement = clip.bitmap(frame);
                    if (replacement == null || replacement.isRecycled()) return;
                    draw.set(new Draw(original, (Paint) paint, replacement, clip.source(frame), FpArt.animSizePercent()));
                } catch (Throwable t) { Lx.e("fingerprint: drawView", t); }
            }
            @Override protected void afterHookedMethod(MethodHookParam p) { draw.remove(); }
        });
        if (!ok) return;

        // public void drawBitmap(Bitmap, Rect src, Rect dst, Paint)  — android.graphics.Canvas; only the call issued by drawView is touched
        try {
            XposedHelpers.findAndHookMethod(Canvas.class, "drawBitmap", Bitmap.class, Rect.class, Rect.class, Paint.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Draw d = draw.get();
                    if (d == null) return;
                    try {
                        if (p.args[0] != d.original || p.args[3] != d.paint || d.replacement.isRecycled()) return;
                        p.args[0] = d.replacement;
                        p.args[1] = d.source;
                        if (d.percent != 100 && p.args[2] instanceof Rect) {
                            Rect dst = (Rect) p.args[2];
                            float cx = dst.exactCenterX(), cy = dst.exactCenterY();
                            float w = dst.width() * d.percent / 200f, h = dst.height() * d.percent / 200f;
                            p.args[2] = new Rect(Math.round(cx - w), Math.round(cy - h), Math.round(cx + w), Math.round(cy + h));
                        }
                        drawn++;
                        long now = System.currentTimeMillis();
                        if (now - lastLog > 60000) { lastLog = now; Lx.i("fingerprint: custom frames drawn so far: " + drawn); }
                    } catch (Throwable t) { Lx.e("fingerprint: drawBitmap", t); }
                }
            });
        } catch (Throwable t) { Lx.e("fingerprint: Canvas.drawBitmap hook", t); return; }

        // public void start()  — :232, called right before each playback; decode our frames on the view's own handler
        // thread (field "private Handler handler") so they are ready before the first MSG_START_DRAW.
        H.hook(H.find(av, "start"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    if (!Cfg.on(FingerprintCustomFeatures.FP_ANIM)) return;
                    final Object view = p.thisObject;
                    Object h = XposedHelpers.getObjectField(view, "handler");
                    if (h instanceof Handler) ((Handler) h).post(() -> { try { FpArt.clip(ctx(view)); } catch (Throwable t) { Lx.e("fingerprint: preload", t); } });
                } catch (Throwable t) { Lx.e("fingerprint: start", t); }
            }
        });
        // private void prepareBitmaps()  — :178, runs on the handler thread when a stock style is set: warm our cache too
        H.hook(H.find(av, "prepareBitmaps"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try { if (Cfg.on(FingerprintCustomFeatures.FP_ANIM)) FpArt.clip(ctx(p.thisObject)); } catch (Throwable t) { Lx.e("fingerprint: prepare", t); }
            }
        });
        Lx.i("Fingerprint: animation hooks ready");
    }

    // ------------------------------------------------------------------------------------------------ idle icon

    private static void icon(ClassLoader cl) {
        Class<?> sv = H.cls(cl, SENSOR_VIEW);
        if (sv == null) return;
        XC_MethodHook after = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!FpArt.iconActive()) return;
                try {
                    boolean fromSystemUi; int keyguardColor;
                    String name = p.method.getName();
                    if (name.equals("setSensorViewIconForRedMagic")) { fromSystemUi = Boolean.TRUE.equals(p.args[0]); keyguardColor = (Integer) p.args[1]; }
                    else { keyguardColor = (Integer) p.args[0]; fromSystemUi = Boolean.TRUE.equals(p.args[1]); }
                    if (!fromSystemUi) return;                                                     // in-app prompt / enrollment: stock
                    if (Boolean.TRUE.equals(XposedHelpers.getObjectField(p.thisObject, "isQuickShow"))) return;
                    if (!serviceAllows()) return;
                    Object iv = XposedHelpers.getObjectField(p.thisObject, "mSensorView");
                    if (!(iv instanceof ImageView)) return;
                    apply((ImageView) iv, keyguardColor);
                } catch (Throwable t) { Lx.e("fingerprint: icon", t); }
            }
        };
        int n = 0;
        n += H.hookAll(sv, "setSensorViewIconForRedMagic", after);
        n += H.hookAll(sv, "updateSensorViewIconForStyle", after);
        n += H.hookAll(sv, "updateSensorViewIcon", after);
        Lx.i("Fingerprint: idle icon hooks ready (" + n + " setters)");
    }

    private static void background(ImageView view, Drawable d) {
        int l = view.getPaddingLeft(), t = view.getPaddingTop(), r = view.getPaddingRight(), b = view.getPaddingBottom();
        view.setBackground(d);
        view.setPadding(l, t, r, b);
    }

    private static void apply(final ImageView view, final int keyguardColor) {
        final Context c = ctx(view);
        if (!FpArt.iconNeedsProvider()) {
            background(view, FpArt.icon(c, keyguardColor));           // module resources / code-drawn: instant, no flicker
            return;
        }
        // frame-store pictures come from the module app's provider: off the main thread, then swap on the main thread
        new Thread(() -> {
            try {
                final Drawable d = FpArt.icon(c, keyguardColor);
                view.post(() -> { if (FpArt.iconActive()) background(view, d); });
            } catch (Throwable t) { Lx.e("fingerprint: icon load", t); }
        }, "rmtweaks-fp-icon").start();
    }
}
