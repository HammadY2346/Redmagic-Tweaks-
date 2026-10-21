package com.hammad.redmagictweaks.xposed;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.ViewGroup;

import com.hammad.redmagictweaks.AodFeatures;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Always-on display (com.android.systemui, ZTE's com.zte.feature.doze). Every target was checked against the phone's
 * SystemUI (RedMagic OS 11.0.11): brightness level, how long tap-to-show stays, the low-battery cut-off, super power
 * saving, burn-in shift and fades, battery / notification / lunar lines on any style, the notification icons, and the
 * clock's colour and size. Settings are read when the AOD uses them, so most apply the next time it shows.
 */
final class AodHooks {
    private AodHooks() {}

    private static final String DOZE = "com.zte.feature.doze.";
    private static final String STYLE = DOZE + "AodClock.AodClockStyle.";

    // ------------------------------------------------------------------ settings snapshot (some hooks run often)

    private static final class S {
        int brightness, tapTime, lowBattery, burnIn, fade, battery, notifications, lunar, notifCount, notifSize, clockColor, clockSize;
        boolean powerSaving, appIcons;
    }
    private static volatile S snap = new S();
    private static volatile long snapAt;

    static S s() {
        long now = SystemClock.uptimeMillis();
        if (now - snapAt > 1000) {
            S n = new S();
            n.brightness = Cfg.choice(AodFeatures.BRIGHTNESS);
            n.tapTime = Cfg.choice(AodFeatures.TAP_TIME);
            n.lowBattery = Cfg.choice(AodFeatures.LOW_BATTERY);
            n.powerSaving = Cfg.on(AodFeatures.POWER_SAVING);
            n.burnIn = Cfg.choice(AodFeatures.BURN_IN);
            n.fade = Cfg.choice(AodFeatures.FADE);
            n.battery = Cfg.choice(AodFeatures.BATTERY);
            n.notifications = Cfg.choice(AodFeatures.NOTIFICATIONS);
            n.lunar = Cfg.choice(AodFeatures.LUNAR);
            n.notifCount = Cfg.choice(AodFeatures.NOTIF_COUNT);
            n.notifSize = Cfg.choice(AodFeatures.NOTIF_SIZE);
            n.appIcons = Cfg.on(AodFeatures.NOTIF_APP_ICONS);
            n.clockColor = Cfg.choice(AodFeatures.CLOCK_COLOR);
            n.clockSize = Cfg.choice(AodFeatures.CLOCK_SIZE);
            snap = n; snapAt = now;
        }
        return snap;
    }

    static void install(ClassLoader cl) {
        try { brightness(cl); } catch (Throwable t) { Lx.e("AOD: brightness install", t); }
        try { timing(cl); } catch (Throwable t) { Lx.e("AOD: timing install", t); }
        try { motion(cl); } catch (Throwable t) { Lx.e("AOD: burn-in / fade install", t); }
        try { elements(cl); } catch (Throwable t) { Lx.e("AOD: elements install", t); }
        try { notificationIcons(cl); } catch (Throwable t) { Lx.e("AOD: notification icons install", t); }
        try { clock(cl); } catch (Throwable t) { Lx.e("AOD: clock install", t); }
        Lx.i("AOD: customization hooks ready");
    }

    // ------------------------------------------------------------------ brightness

    /**
     * DozeScreenBrightnessAdapt.computeBrightness(int sensorValue) turns the light sensor into an AOD backlight level
     * (-1 keep, then 0 / 1 / 2 with three levels, 1 / 2 with two) that setLightValueToLcd sends to the panel.
     */
    private static void brightness(ClassLoader cl) {
        Class<?> adapt = H.cls(cl, "com.zte.adapt.mifavor.keyguard.DozeScreenBrightnessAdapt");
        if (adapt == null) return;
        H.hook(H.find(adapt, "computeBrightness", int.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                int b = s().brightness;
                if (b <= 0 || p.hasThrowable()) return;
                boolean three = false;
                try { three = XposedHelpers.getBooleanField(p.thisObject, "isSupportThreeLevel"); } catch (Throwable ignored) { }
                int level = b == 1 ? (three ? 0 : 1) : b == 2 ? 1 : 2;
                p.setResult(level);
            }
        });
    }

    // ------------------------------------------------------------------ tap-to-show time, low battery, power saving

    private static final int[] TAP_MS = {0, 5000, 20000, 30000, 60000};
    private static final int[] LOW_PCT = {10, -1, 5, 15, 20, 30};
    private static final ThreadLocal<Boolean> inRefresh = new ThreadLocal<>();

    private static void timing(ClassLoader cl) {
        Class<?> doze = H.cls(cl, DOZE + "DozeFeature");
        if (doze == null) return;
        // public void initShowTimer(int ms): how long the AOD stays after a tap when it is set to "tap to show"
        H.hook(H.find(doze, "initShowTimer", int.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                int t = s().tapTime;
                if (t > 0 && t < TAP_MS.length) p.args[0] = TAP_MS[t];
            }
        });
        // public void handleBatteryLevelChanged(int level): the AOD is "low battery" at <= 10 %
        H.hook(H.find(doze, "handleBatteryLevelChanged", int.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                int c = s().lowBattery;
                if (c <= 0 || c >= LOW_PCT.length || p.hasThrowable()) return;
                try {
                    int level = (Integer) p.args[0];
                    boolean low = LOW_PCT[c] >= 0 && level <= LOW_PCT[c];
                    if (XposedHelpers.getBooleanField(p.thisObject, "mIsLowBattery") == low) return;
                    XposedHelpers.setBooleanField(p.thisObject, "mIsLowBattery", low);
                    if (XposedHelpers.getIntField(p.thisObject, "mDisplayLowBatteryCtl") == 1) {
                        XposedHelpers.callMethod(p.thisObject, "refreshDozeShouldDisplay");
                        XposedHelpers.callMethod(p.thisObject, "updateDozeDisplay");
                    }
                } catch (Throwable t) { Lx.e("AOD: low battery", t); }
            }
        });
        // refreshDozeShouldDisplay() turns the AOD off when SuperPowerSaveFeature.isBatterySaveOn()
        Class<?> save = H.cls(cl, "com.zte.feature.battery.SuperPowerSaveFeature");
        if (save != null) {
            H.hook(H.find(doze, "refreshDozeShouldDisplay"), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) { inRefresh.set(Boolean.TRUE); }
                @Override protected void afterHookedMethod(MethodHookParam p) { inRefresh.remove(); }
            });
            H.hook(H.find(save, "isBatterySaveOn"), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (Boolean.TRUE.equals(inRefresh.get()) && s().powerSaving) p.setResult(false);
                }
            });
        }
    }

    // ------------------------------------------------------------------ burn-in shift and fades

    private static final Map<Object, int[]> stockSteps = new WeakHashMap<>();
    private static final int[] FADE_MS = {0, 1, 300, 2000};

    private static void motion(ClassLoader cl) {
        Class<?> ctl = H.cls(cl, "com.android.systemui.doze.AodViewController");
        if (ctl != null) {
            // shouldChangePosition(): every 60 s the clock moves a step (mVerPositionStep / mHorPositionStep) against burn-in
            H.hook(H.find(ctl, "shouldChangePosition"), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    int b = s().burnIn;
                    if (b <= 0 || p.hasThrowable()) return;
                    if (b == 1) { p.setResult(false); return; }
                    try {
                        Object o = p.thisObject;
                        int[] st = stockSteps.get(o);
                        if (st == null) { st = new int[]{XposedHelpers.getIntField(o, "mVerPositionStep"), XposedHelpers.getIntField(o, "mHorPositionStep")}; stockSteps.put(o, st); }
                        float f = b == 2 ? 0.5f : 1.5f;
                        XposedHelpers.setIntField(o, "mVerPositionStep", Math.max(1, Math.round(st[0] * f)));
                        XposedHelpers.setIntField(o, "mHorPositionStep", Math.max(1, Math.round(st[1] * f)));
                        if (b == 3 && !(Boolean) p.getResult()) {
                            // stronger: every 30 s instead of every 60 s
                            long start = XposedHelpers.getLongField(o, "mVisiableStartTime");
                            Object content = XposedHelpers.getObjectField(o, "mAodContentView"), anim = XposedHelpers.getObjectField(o, "mAodInOutAnimator");
                            if (start > 0 && SystemClock.elapsedRealtime() - start >= 30000 && content != null && anim != null
                                    && (Boolean) XposedHelpers.callMethod(content, "shouldChangePosition") && (Boolean) XposedHelpers.callMethod(anim, "canPositionAnimation"))
                                p.setResult(true);
                        }
                    } catch (Throwable t) { Lx.e("AOD: burn-in", t); }
                }
            });
        }
        Class<?> anim = H.cls(cl, DOZE + "AodInOutAnimator");
        if (anim != null) {
            // startAlphaAnimation(View, float alpha, int ms, Runnable): every AOD fade in and out (1000 ms stock)
            H.hook(H.find(anim, "startAlphaAnimation", View.class, float.class, int.class, Runnable.class), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    int f = s().fade;
                    if (f > 0 && f < FADE_MS.length) p.args[2] = FADE_MS[f];
                }
            });
        }
    }

    // ------------------------------------------------------------------ battery / notifications / lunar on any style

    private static void elements(ClassLoader cl) {
        Class<?> data = H.cls(cl, STYLE + "MyAodStyleModel$StyleData");
        if (data == null) return;
        hookShow(data, "isShowBattery", 0);
        hookShow(data, "isShowNotificaition", 1);
        hookShow(data, "isShowLunar", 2);
    }

    private static void hookShow(Class<?> data, String method, final int which) {
        H.hook(H.find(data, method), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                S cur = s();
                int c = which == 0 ? cur.battery : which == 1 ? cur.notifications : cur.lunar;
                if (c == 1) p.setResult(true); else if (c == 2) p.setResult(false);
            }
        });
    }

    // ------------------------------------------------------------------ notification icons

    private static final int[] NOTIF_COUNT = {4, 3, 6, 8};
    private static final float[] NOTIF_SCALE = {1f, 0.8f, 1.2f, 1.4f};

    private static void notificationIcons(ClassLoader cl) {
        Class<?> icons = H.cls(cl, DOZE + "NotificationDozeIcon");
        if (icons == null) return;
        // inflate(ViewGroup, boolean) creates MAX_ICONS icon views: set the count first, size the row after
        H.hook(H.find(icons, "inflate", ViewGroup.class, boolean.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                int c = s().notifCount;
                if (c > 0 && c < NOTIF_COUNT.length) { try { XposedHelpers.setIntField(p.thisObject, "MAX_ICONS", NOTIF_COUNT[c]); } catch (Throwable t) { Lx.e("AOD: icon count", t); } }
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                int z = s().notifSize;
                if (z <= 0 || z >= NOTIF_SCALE.length || p.hasThrowable()) return;
                try {
                    Object row = XposedHelpers.getObjectField(p.thisObject, "mIconContainer");
                    if (row instanceof View) { ((View) row).setScaleX(NOTIF_SCALE[z]); ((View) row).setScaleY(NOTIF_SCALE[z]); }
                } catch (Throwable t) { Lx.e("AOD: icon size", t); }
            }
        });
        // getIconDrawable(NotificationEntry): the notification's small icon; isIconColorble: tint it with the style colour
        for (Method m : H.findAll(icons, "getIconDrawable")) {
            H.hook(m, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!s().appIcons || p.args[0] == null) return;
                    try {
                        StatusBarNotification sbn = (StatusBarNotification) XposedHelpers.callMethod(p.args[0], "getSbn");
                        Object ctx = XposedHelpers.getObjectField(p.thisObject, "mContext");
                        if (sbn == null || !(ctx instanceof android.content.Context)) return;
                        Drawable app = ((android.content.Context) ctx).getPackageManager().getApplicationIcon(sbn.getPackageName());
                        if (app != null) p.setResult(app);
                    } catch (Throwable ignored) { }
                }
            });
        }
        for (Method m : H.findAll(icons, "isIconColorble")) {
            H.hook(m, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { if (s().appIcons) p.setResult(false); }
            });
        }
    }

    // ------------------------------------------------------------------ clock colour and size

    private static final float[] CLOCK_SCALE = {1f, 0.8f, 0.9f, 1.1f, 1.2f, 1.3f};

    private static void clock(ClassLoader cl) {
        Class<?> content = H.cls(cl, STYLE + "AodContentView");
        Class<?> data = H.cls(cl, STYLE + "MyAodStyleModel$StyleData");
        if (content == null || data == null) return;
        // loadStyle(StyleData) builds the style's view into mClock (an AodStyleInterface view)
        H.hook(H.find(content, "loadStyle", data), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable()) return;
                S cur = s();
                try {
                    Object clock = XposedHelpers.getObjectField(p.thisObject, "mClock");
                    if (!(clock instanceof View)) return;
                    View v = (View) clock;
                    if (cur.clockColor > 0) {
                        Drawable mask = colourMask(v, cur.clockColor);
                        if (mask != null) XposedHelpers.callMethod(clock, "setMaskDrawable", mask);
                    }
                    float sc = cur.clockSize > 0 && cur.clockSize < CLOCK_SCALE.length ? CLOCK_SCALE[cur.clockSize] : 1f;
                    v.setScaleX(sc); v.setScaleY(sc);
                } catch (Throwable t) { Lx.e("AOD: clock look", t); }
            }
        });
    }

    /** The clock colour as the mask drawable ZTE fills the clock digits with (a plain colour or a top-to-bottom gradient). */
    static Drawable colourMask(View v, int choice) {
        int[] c = AodFeatures.clockColors(v.getContext(), choice);
        if (c == null) return null;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, c[0] == c[1] ? new int[]{c[0], c[0]} : c);
        g.setShape(GradientDrawable.RECTANGLE);
        return g;
    }
}
