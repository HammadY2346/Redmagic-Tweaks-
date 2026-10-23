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
        int brightness, tapTime, lowBattery, burnIn, fade, battery, notifications, lunar, notifCount, notifSize, clockColor, clockSize, clockPct, clockPos;
        boolean powerSaving, appIcons, oneUi;
        /** Custom clock colours {top, bottom} (ColorPick), or null for the CLOCK_COLORS preset. */
        int[] clockCustom;
        /** Typed values (Features.Num); -1 = the preset applies. */
        int brightPct = -1, tapMs = -1, lowPct = -1, fadeMs = -1, notifCountV = -1, notifPct = -1;
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
            n.clockCustom = Cfg.colorPair(AodFeatures.CLOCK_COLOR);
            if (n.clockCustom != null && n.clockColor <= 0) n.clockColor = 1;
            n.clockSize = Cfg.choice(AodFeatures.CLOCK_SIZE);
            n.clockPct = Cfg.hasNum(AodFeatures.CLOCK_SIZE) ? Cfg.num(AodFeatures.CLOCK_SIZE, 100) : 0;
            int pi = Cfg.choice(AodFeatures.CLOCK_POSITION);
            n.clockPos = Cfg.num(AodFeatures.CLOCK_POSITION, AodFeatures.CLOCK_POSITION_DP[pi > 0 && pi < AodFeatures.CLOCK_POSITION_DP.length ? pi : 0]);
            n.oneUi = Cfg.on(AodFeatures.ONEUI_CLOCK);
            if (Cfg.hasNum(AodFeatures.BRIGHTNESS)) n.brightPct = Math.max(1, Math.min(100, Cfg.num(AodFeatures.BRIGHTNESS, 100)));
            if (Cfg.hasNum(AodFeatures.TAP_TIME)) n.tapMs = Math.max(1, Math.min(600, Cfg.num(AodFeatures.TAP_TIME, 10))) * 1000;
            if (Cfg.hasNum(AodFeatures.LOW_BATTERY)) n.lowPct = Math.max(0, Math.min(100, Cfg.num(AodFeatures.LOW_BATTERY, 10)));
            if (Cfg.hasNum(AodFeatures.FADE)) n.fadeMs = Math.max(0, Math.min(10000, Cfg.num(AodFeatures.FADE, 1000)));
            if (Cfg.hasNum(AodFeatures.NOTIF_COUNT)) n.notifCountV = Math.max(1, Math.min(20, Cfg.num(AodFeatures.NOTIF_COUNT, 4)));
            if (Cfg.hasNum(AodFeatures.NOTIF_SIZE)) n.notifPct = Math.max(20, Math.min(300, Cfg.num(AodFeatures.NOTIF_SIZE, 100)));
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
                S cur = s();
                int b = cur.brightness;
                if ((b <= 0 && cur.brightPct < 0) || p.hasThrowable()) return;
                boolean three = false;
                try { three = XposedHelpers.getBooleanField(p.thisObject, "isSupportThreeLevel"); } catch (Throwable ignored) { }
                threeLevel = three;
                if (cur.brightPct > 0) { p.setResult(pctLevel(cur.brightPct, three)); applyAlpha(); return; }
                int level = b == 1 ? (three ? 0 : 1) : b == 2 ? 1 : 2;
                p.setResult(level);
            }
        });
    }

    /**
     * A typed brightness: the panel only has 2 or 3 AOD backlight levels (DozeScreenBrightnessAdapt), so the nearest level
     * at or above the value is used and the AOD content is dimmed to the rest (OLED: dimmer pixels draw less power).
     */
    private static volatile boolean threeLevel;

    private static int pctLevel(int pct, boolean three) {
        if (three) return pct <= 33 ? 0 : pct <= 66 ? 1 : 2;
        return pct <= 50 ? 1 : 2;
    }

    private static float pctAlpha(int pct, boolean three) {
        float top = three ? (pct <= 33 ? 33 : pct <= 66 ? 66 : 100) : (pct <= 50 ? 50 : 100);
        return Math.max(0.05f, Math.min(1f, pct / top));
    }

    /** The live (screen-off) AodContentView, for the brightness dimming and the minute redraw. */
    private static java.lang.ref.WeakReference<ViewGroup> liveContent = new java.lang.ref.WeakReference<>(null);

    private static void applyAlpha() {
        ViewGroup content = liveContent.get();
        if (content == null) return;
        int pct = s().brightPct;
        float a = pct > 0 ? pctAlpha(pct, threeLevel) : 1f;
        content.post(() -> { if (Math.abs(content.getAlpha() - a) > 0.01f) content.setAlpha(a); });
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
                S cur = s();
                if (cur.tapMs > 0) { p.args[0] = cur.tapMs; return; }
                int t = cur.tapTime;
                if (t > 0 && t < TAP_MS.length) p.args[0] = TAP_MS[t];
            }
        });
        // public void handleBatteryLevelChanged(int level): the AOD is "low battery" at <= 10 %
        H.hook(H.find(doze, "handleBatteryLevelChanged", int.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                S cur = s();
                int c = cur.lowBattery;
                if ((cur.lowPct < 0 && (c <= 0 || c >= LOW_PCT.length)) || p.hasThrowable()) return;
                try {
                    int level = (Integer) p.args[0];
                    int cut = cur.lowPct >= 0 ? (cur.lowPct == 0 ? -1 : cur.lowPct) : LOW_PCT[c];   // typed 0 % = never
                    boolean low = cut >= 0 && level <= cut;
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

    /** AodViewController's own mDefaultTopMargin (the clock's resting spot), before the position option moved it. */
    private static final Map<Object, Integer> stockTop = new WeakHashMap<>();
    private static int lastPos = Integer.MIN_VALUE;

    private static void motion(ClassLoader cl) {
        Class<?> ctl = H.cls(cl, "com.android.systemui.doze.AodViewController");
        if (ctl != null) {
            // clock position: onDreamingStarted() puts the clock at mDefaultTopMargin (mPositionView.setTranslationY) and the
            // burn-in shift moves it from there (mMinTopMargin = getMinTopMargin(mDefaultTopMargin)); moving that spot moves both
            H.hookAll(ctl, "onDreamingStarted", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        Object o = p.thisObject;
                        Integer st = stockTop.get(o);
                        if (st == null) { st = XposedHelpers.getIntField(o, "mDefaultTopMargin"); stockTop.put(o, st); }
                        int off = s().clockPos;
                        int want = Math.max(0, st + Math.round(off * android.content.res.Resources.getSystem().getDisplayMetrics().density));
                        if (XposedHelpers.getIntField(o, "mDefaultTopMargin") != want) XposedHelpers.setIntField(o, "mDefaultTopMargin", want);
                        if (lastPos != off) { lastPos = off; Lx.i("AOD: clock position " + off + " dp (top " + st + " -> " + want + " px)"); }
                    } catch (Throwable t) { Lx.e("AOD: clock position", t); }
                }
            });
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
                    S cur = s();
                    if (cur.fadeMs >= 0) { p.args[2] = Math.max(1, cur.fadeMs); return; }   // 0 ms = instant (1 ms, as "Instant")
                    int f = cur.fade;
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
                S cur = s();
                int c = cur.notifCount;
                int want = cur.notifCountV > 0 ? cur.notifCountV : c > 0 && c < NOTIF_COUNT.length ? NOTIF_COUNT[c] : -1;
                if (want > 0) { try { XposedHelpers.setIntField(p.thisObject, "MAX_ICONS", want); } catch (Throwable t) { Lx.e("AOD: icon count", t); } }
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                S cur = s();
                int z = cur.notifSize;
                float sc = cur.notifPct > 0 ? cur.notifPct / 100f : z > 0 && z < NOTIF_SCALE.length ? NOTIF_SCALE[z] : -1f;
                if (sc <= 0 || p.hasThrowable()) return;
                try {
                    Object row = XposedHelpers.getObjectField(p.thisObject, "mIconContainer");
                    if (row instanceof View) { ((View) row).setScaleX(sc); ((View) row).setScaleY(sc); }
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
        // loadStyle(StyleData) builds the style's view into mClock (an AodStyleInterface view); initAodView(type, index)
        // does the same for the default style (loadStyle(null) goes through it)
        XC_MethodHook styled = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable()) return;
                S cur = s();
                try {
                    Object clock = XposedHelpers.getObjectField(p.thisObject, "mClock");
                    if (!(clock instanceof View)) return;
                    View v = (View) clock;
                    if (cur.clockColor > 0) {
                        Drawable mask = colourMask(v, cur.clockColor, cur.clockCustom);
                        if (mask != null) XposedHelpers.callMethod(clock, "setMaskDrawable", mask);
                    }
                    float sc = cur.clockPct > 0 ? cur.clockPct / 100f : cur.clockSize > 0 && cur.clockSize < CLOCK_SCALE.length ? CLOCK_SCALE[cur.clockSize] : 1f;
                    v.setScaleX(sc); v.setScaleY(sc);
                } catch (Throwable t) { Lx.e("AOD: clock look", t); }
                try {
                    ViewGroup content = (ViewGroup) p.thisObject;
                    if (XposedHelpers.getIntField(content, "mScence") != 1) return;       // not the settings previews
                    liveContent = new java.lang.ref.WeakReference<>(content);
                    applyAlpha();
                    oneUi(content, cur);
                } catch (Throwable t) { Lx.e("AOD: One UI clock", t); }
            }
        };
        H.hook(H.find(content, "loadStyle", data), styled);
        H.hook(H.find(content, "initAodView", int.class, int.class), styled);
        // once a minute (the AOD's own time tick, no extra wake-ups): new time, date and battery
        XC_MethodHook tick = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    View ours = ((ViewGroup) p.thisObject).findViewWithTag(TAG_AOD);
                    if (ours instanceof OneUiAod) ((OneUiAod) ours).refresh(s());
                } catch (Throwable t) { Lx.e("AOD: One UI clock tick", t); }
            }
        };
        H.hook(H.find(content, "dozeTimeTick"), tick);
        H.hook(H.find(content, "updateView", boolean.class), tick);
    }

    // ------------------------------------------------------------------ One UI clock on the AOD

    private static final String TAG_AOD = "rmtweaks_oneui_aod";
    /** Video / animated / online-custom AOD styles fill the screen themselves: the One UI clock is not laid over them. */
    private static final java.util.Set<Integer> FULL_SCREEN_TYPES = new java.util.HashSet<>(java.util.Arrays.asList(4, 7, 8, 10, 11, 12, 13));
    private static boolean loggedOneUi;

    /**
     * The RedMagic style view is hidden and a One UI clock (OneUiLockHooks.FaceView with the AOD's face / size, then the
     * date, battery and the style's own notification icon row moved under it) takes its place inside AodContentView, so
     * AodViewController's burn-in shift and the clock position option move it as before. AodContentView.reset() (each
     * loadStyle) removes it again, so turning the switch off needs nothing more.
     */
    private static void oneUi(ViewGroup content, S cur) {
        View ours = content.findViewWithTag(TAG_AOD);
        Object clock = XposedHelpers.getObjectField(content, "mClock");
        if (!cur.oneUi || !(clock instanceof View)) {
            if (ours != null) {   // switched off without a new style: show the style again
                content.removeView(ours);
                if (clock instanceof View) ((View) clock).setVisibility(View.VISIBLE);
            }
            return;
        }
        int type = -1;
        try { type = (Integer) XposedHelpers.callMethod(clock, "getClockType"); } catch (Throwable ignored) { }
        if (FULL_SCREEN_TYPES.contains(type)) {
            if (!loggedOneUi) { loggedOneUi = true; Lx.i("AOD: One UI clock skipped on a full-screen style (type " + type + ")"); }
            return;
        }
        android.content.Context ctx = content.getContext();
        if (!(ours instanceof OneUiAod)) {
            ours = new OneUiAod(ctx);
            content.addView(ours, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        OneUiAod aod = (OneUiAod) ours;
        View style = (View) clock;
        // the notification icon row of the style moves under our clock (NotificationDozeIcon keeps filling it)
        try {
            Object root = XposedHelpers.callMethod(content, "getNotificationRoot");
            if (root instanceof View && ((View) root).getParent() != aod) {
                View row = (View) root;
                if (row.getParent() instanceof ViewGroup) ((ViewGroup) row.getParent()).removeView(row);
                android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
                lp.topMargin = Math.round(14 * ctx.getResources().getDisplayMetrics().density);
                aod.addView(row, lp);
            }
        } catch (Throwable t) { Lx.w("AOD: One UI clock: notification row not moved (" + t + ")"); }
        if (style.getVisibility() != View.GONE) style.setVisibility(View.GONE);
        // our height, not the style's fixed one; no style padding
        content.setPadding(0, 0, 0, 0);
        ViewGroup.LayoutParams lp = content.getLayoutParams();
        if (lp != null && lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) { lp.height = ViewGroup.LayoutParams.WRAP_CONTENT; content.setLayoutParams(lp); }
        aod.refresh(cur);
        if (!loggedOneUi) { loggedOneUi = true; Lx.i("AOD: One UI clock shown (style type " + type + ")"); }
    }

    /** The One UI AOD: clock face, date, battery (charging or always), then the moved notification row. */
    static final class OneUiAod extends android.widget.LinearLayout {
        final OneUiLockHooks.FaceView face;
        final android.widget.TextView date, battery;

        OneUiAod(android.content.Context ctx) {
            super(ctx);
            setTag(TAG_AOD);
            setOrientation(VERTICAL);
            setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            setClipChildren(false);
            setClipToPadding(false);
            float d = ctx.getResources().getDisplayMetrics().density;
            face = new OneUiLockHooks.FaceView(ctx, true);
            addView(face, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            date = line(ctx, 16f);
            LayoutParams dl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            dl.topMargin = Math.round(10 * d);
            addView(date, dl);
            battery = line(ctx, 14f);
            LayoutParams bl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            bl.topMargin = Math.round(6 * d);
            addView(battery, bl);
        }

        private static android.widget.TextView line(android.content.Context ctx, float sp) {
            android.widget.TextView t = new android.widget.TextView(ctx);
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
            t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            t.setIncludeFontPadding(false);
            t.setGravity(android.view.Gravity.CENTER);
            t.setSingleLine(true);
            return t;
        }

        void refresh(S cur) {
            face.configure();
            int[] c = cur.clockCustom != null ? cur.clockCustom : AodFeatures.clockColors(getContext(), cur.clockColor);
            int col = c != null ? c[0] | 0xff000000 : 0xFFFFFFFF;
            face.setColor(col);
            int sub = (col & 0x00ffffff) | 0xCC000000;
            date.setTextColor(sub);
            battery.setTextColor(sub);
            java.util.Locale loc = java.util.Locale.getDefault();
            date.setText(android.text.format.DateFormat.format(android.text.format.DateFormat.getBestDateTimePattern(loc, "EEEMMMd"), System.currentTimeMillis()));
            int level = -1; boolean charging = false;
            try {
                android.content.IntentFilter f = new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED);
                android.content.Intent i = getContext().registerReceiver(null, f);   // the sticky value only: nothing is registered
                if (i != null) {
                    int l = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1), sc = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
                    level = sc > 0 && l >= 0 ? l * 100 / sc : l;
                    int st = i.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, 0);
                    charging = st == android.os.BatteryManager.BATTERY_STATUS_CHARGING || st == android.os.BatteryManager.BATTERY_STATUS_FULL;
                }
            } catch (Throwable ignored) { }
            boolean show = level >= 0 && (cur.battery == 1 || (cur.battery != 2 && charging));
            battery.setVisibility(show ? VISIBLE : GONE);
            if (show) battery.setText((charging ? "⚡ " : "") + level + "%");
            face.invalidate();
        }
    }

    /** The clock colour as the mask drawable ZTE fills the clock digits with (a plain colour or a top-to-bottom gradient). */
    static Drawable colourMask(View v, int choice, int[] custom) {
        int[] c = custom != null ? custom : AodFeatures.clockColors(v.getContext(), choice);
        if (c == null) return null;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, c[0] == c[1] ? new int[]{c[0], c[0]} : c);
        g.setShape(GradientDrawable.RECTANGLE);
        return g;
    }
}
