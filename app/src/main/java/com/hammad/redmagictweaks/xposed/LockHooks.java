package com.hammad.redmagictweaks.xposed;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.hammad.redmagictweaks.LockFeatures;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * com.android.systemui (main process) — lock screen status bar, unseen-notification filter, double-tap
 * to sleep, unlock hint, charging readout and Always-on display tweaks. Every target was checked
 * against the firmware 11.0.11 decompile; the declaration is quoted above each hook.
 */
final class LockHooks {
    private LockHooks() {}

    private static final String TAG_CHARGE = "rmtweaks_lock_charge";
    private static WeakReference<Object> surfaces = new WeakReference<>(null);   // CentralSurfacesImpl

    static void install(ClassLoader cl) {
        hideStatusBar(cl);
        keepNotifications(cl);
        doubleTap(cl);
        unlockHint(cl);
        chargeInfo(cl);
        aod(cl);
        try { lockClock(cl); } catch (Throwable t) { Lx.e("Lock: clock/date hooks", t); }
        try { widgets(cl); } catch (Throwable t) { Lx.e("Lock: widget hooks", t); }
        Lx.i("Lock: hooks ready");
    }

    // ------------------------------------------------------------------ lock screen status bar

    private static void hideStatusBar(ClassLoader cl) {
        Class<?> ksb = H.cls(cl, "com.android.systemui.statusbar.phone.KeyguardStatusBarView");
        if (ksb == null) return;
        // public void setVisibility(int i)  — KeyguardStatusBarView.java:294 (controller shows it with VISIBLE on every keyguard show)
        H.hook(H.find(ksb, "setVisibility", int.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (Integer.valueOf(View.VISIBLE).equals(p.args[0]) && Cfg.on(LockFeatures.LOCK_HIDE_STATUSBAR)) p.args[0] = View.INVISIBLE;
            }
        });
        // public void onFinishInflate()  — :184
        H.hook(H.find(ksb, "onFinishInflate"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (Cfg.on(LockFeatures.LOCK_HIDE_STATUSBAR)) ((View) p.thisObject).setVisibility(View.INVISIBLE);
            }
        });
    }

    // ------------------------------------------------------------------ keep notifications after unlock

    private static void keepNotifications(ClassLoader cl) {
        // OriginalUnseenKeyguardCoordinator.java:141 "public final NotifFilter unseenNotifFilter = new NotifFilter() { ...
        //   public boolean shouldFilterOut(NotificationEntry entry, long now) (:160) }" — true hides already-seen notifications on the keyguard.
        Class<?> filter = H.cls(cl, "com.android.systemui.statusbar.notification.collection.coordinator.OriginalUnseenKeyguardCoordinator$unseenNotifFilter$1");
        if (filter == null) return;
        final StatusBarHooks.Flag keep = new StatusBarHooks.Flag(LockFeatures.LOCK_KEEP_NOTIFS);
        H.hookAll(filter, "shouldFilterOut", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { if (keep.get()) p.setResult(false); }
        });
    }

    // ------------------------------------------------------------------ double tap to sleep

    private static final class DoubleTap {
        private long at; private float x, y;
        void reset() { at = 0; }
        /** True on the second ACTION_DOWN within 300 ms and 80 px of the first. */
        boolean hit(MotionEvent e) {
            if (e == null || e.getActionMasked() != MotionEvent.ACTION_DOWN) { reset(); return false; }
            long now = e.getEventTime();
            boolean ok = at != 0 && now - at < 300 && Math.abs(e.getX() - x) < 80 && Math.abs(e.getY() - y) < 80;
            at = ok ? 0 : now;
            x = e.getX(); y = e.getY();
            return ok;
        }
    }

    static void goToSleep(Context c, String why) {
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            XposedHelpers.callMethod(pm, "goToSleep", SystemClock.uptimeMillis());
            Lx.i("Lock: screen off (" + why + ")");
        } catch (Throwable t) { Lx.e("Lock: goToSleep", t); }
    }

    /** True when a visible clickable / long-clickable view (notification, button, PIN key…) sits under (x, y) of v. */
    static boolean clickableAt(View v, float x, float y) {
        if (v == null || v.getVisibility() != View.VISIBLE) return false;
        if (v.isClickable() || v.isLongClickable()) return true;
        if (!(v instanceof ViewGroup)) return false;
        ViewGroup g = (ViewGroup) v;
        for (int i = g.getChildCount() - 1; i >= 0; i--) {
            View c = g.getChildAt(i);
            float cx = x + g.getScrollX() - c.getLeft() - c.getTranslationX();
            float cy = y + g.getScrollY() - c.getTop() - c.getTranslationY();
            if (cx >= 0 && cy >= 0 && cx < c.getWidth() && cy < c.getHeight() && clickableAt(c, cx, cy)) return true;
        }
        return false;
    }

    /** Lock screen (not bouncer / shade / AOD) according to the captured CentralSurfacesImpl; false when unknown. */
    private static boolean onPlainLockScreen() {
        Object cs = surfaces.get();
        if (cs == null) return false;
        try {
            if (!Boolean.TRUE.equals(XposedHelpers.callMethod(cs, "isKeyguardShowing"))) return false;
            if (Boolean.TRUE.equals(XposedHelpers.callMethod(cs, "isBouncerShowing"))) return false;
            Object ssc = XposedHelpers.getObjectField(cs, "mStatusBarStateController");
            if (ssc == null) return false;
            if (!Integer.valueOf(1).equals(XposedHelpers.callMethod(ssc, "getState"))) return false;   // 1 = KEYGUARD
            return !Boolean.TRUE.equals(XposedHelpers.callMethod(ssc, "isDozing"));
        } catch (Throwable t) { Lx.w("Lock: keyguard state unavailable: " + t); return false; }
    }

    private static void doubleTap(ClassLoader cl) {
        Class<?> csi = H.cls(cl, "com.android.systemui.statusbar.phone.CentralSurfacesImpl");
        if (csi != null) {
            // public void makeStatusBarView(RegisterStatusBarResult)  — CentralSurfacesImpl.java:2080 ; isKeyguardShowing() (:1820), isBouncerShowing() (:1775),
            // "public final SysuiStatusBarStateController mStatusBarStateController" (:354)
            H.hookAll(csi, "makeStatusBarView", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { surfaces = new WeakReference<>(p.thisObject); }
            });
        }
        final StatusBarHooks.Flag lockTap = new StatusBarHooks.Flag(LockFeatures.LOCK_DT_SLEEP);
        final DoubleTap lockDt = new DoubleTap();
        Class<?> shade = H.cls(cl, "com.android.systemui.shade.NotificationShadeWindowView");
        if (shade != null) {
            // public boolean dispatchTouchEvent(MotionEvent)  — NotificationShadeWindowView.java:393 (every lock screen touch passes here)
            H.hook(H.find(shade, "dispatchTouchEvent", MotionEvent.class), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        MotionEvent e = (MotionEvent) p.args[0];
                        if (e == null || e.getActionMasked() != MotionEvent.ACTION_DOWN || !lockTap.get()) return;
                        View root = (View) p.thisObject;
                        if (!onPlainLockScreen() || clickableAt(root, e.getX(), e.getY())) { lockDt.reset(); return; }
                        if (lockDt.hit(e)) { goToSleep(root.getContext(), "lock screen double-tap"); p.setResult(true); }
                    } catch (Throwable t) { Lx.e("Lock: double-tap", t); }
                }
            });
        }
        final StatusBarHooks.Flag barTap = new StatusBarHooks.Flag(LockFeatures.LOCK_DT_STATUSBAR);
        final DoubleTap barDt = new DoubleTap();
        Class<?> psb = H.cls(cl, "com.android.systemui.statusbar.phone.PhoneStatusBarView");
        if (psb != null) {
            // public boolean dispatchTouchEvent(MotionEvent)  — PhoneStatusBarView.java:78
            H.hook(H.find(psb, "dispatchTouchEvent", MotionEvent.class), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        MotionEvent e = (MotionEvent) p.args[0];
                        if (e == null || e.getActionMasked() != MotionEvent.ACTION_DOWN || !barTap.get()) return;
                        if (barDt.hit(e)) { goToSleep(((View) p.thisObject).getContext(), "status bar double-tap"); p.setResult(true); }
                    } catch (Throwable t) { Lx.e("Lock: status bar double-tap", t); }
                }
            });
        }
    }

    // ------------------------------------------------------------------ unlock hint

    private static void unlockHint(ClassLoader cl) {
        Class<?> kic = H.cls(cl, "com.android.systemui.statusbar.KeyguardIndicationController");
        if (kic == null) return;
        // public void showActionToUnlock()  — KeyguardIndicationController.java:1100 ("swipe up to unlock" / face+fingerprint prompts)
        H.hook(H.find(kic, "showActionToUnlock"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { if (Cfg.on(LockFeatures.LOCK_HIDE_UNLOCK_HINT)) p.setResult(null); }
        });
    }

    // ------------------------------------------------------------------ charging readout

    private static void chargeInfo(ClassLoader cl) {
        Class<?> kic = H.cls(cl, "com.android.systemui.statusbar.KeyguardIndicationController");
        if (kic == null) return;
        // public void setIndicationArea(ViewGroup)  — KeyguardIndicationController.java:1016 ; public ColorStateList getInitialTextColorState() (:791)
        H.hook(H.find(kic, "setIndicationArea", ViewGroup.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try { attachChargeBox((ViewGroup) p.args[0], p.thisObject); } catch (Throwable t) { Lx.e("Lock: charging readout", t); }
            }
        });
    }

    private static void attachChargeBox(ViewGroup area, Object controller) {
        if (area == null) { Lx.w("Lock: indication area is null — charging readout skipped"); return; }
        if (area.findViewWithTag(TAG_CHARGE) != null) return;
        Context c = area.getContext();
        LinearLayout box = new LinearLayout(c);
        box.setTag(TAG_CHARGE);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = StatusBarHooks.dp(c, 4);
        box.setPadding(pad, pad, pad, pad);
        box.setVisibility(View.GONE);
        for (int i = 0; i < 2; i++) {
            TextView tv = new TextView(c);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tv.setTextColor(0xFFFFFFFF);
            tv.setGravity(Gravity.CENTER_HORIZONTAL);
            tv.setSingleLine(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i == 1) lp.topMargin = StatusBarHooks.dp(c, 2);
            box.addView(tv, lp);
        }
        ViewGroup.LayoutParams lp;
        if (area instanceof LinearLayout) lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        else if (area instanceof FrameLayout) lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        else lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        area.addView(box, lp);
        new ChargePoller(box, controller).start();
        Lx.i("Lock: charging readout attached to " + area.getClass().getSimpleName());
    }

    private static final class ChargePoller implements Runnable, View.OnAttachStateChangeListener {
        private final LinearLayout box; private final Object controller;
        private final Handler main = new Handler(Looper.getMainLooper());
        private boolean stopped;
        ChargePoller(LinearLayout box, Object controller) { this.box = box; this.controller = controller; }

        void start() {
            box.addOnAttachStateChangeListener(this);
            if (box.isAttachedToWindow()) main.post(this);
        }
        @Override public void onViewAttachedToWindow(View v) { stopped = false; main.removeCallbacks(this); main.post(this); }
        @Override public void onViewDetachedFromWindow(View v) { stopped = true; main.removeCallbacks(this); }

        private boolean locked() {
            Object cs = surfaces.get();
            try {
                if (cs != null) return Boolean.TRUE.equals(XposedHelpers.callMethod(cs, "isKeyguardShowing"));
                KeyguardManager km = (KeyguardManager) box.getContext().getSystemService(Context.KEYGUARD_SERVICE);
                return km != null && km.isKeyguardLocked();
            } catch (Throwable t) { return true; }
        }

        @Override public void run() {
            if (stopped) return;
            final int mode = Cfg.choice(LockFeatures.LOCK_CHARGE_INFO);
            if (mode <= 0 || !locked()) {
                if (box.getVisibility() != View.GONE) box.setVisibility(View.GONE);
                main.postDelayed(this, mode <= 0 ? 5000 : 2000);
                return;
            }
            final int sec = StatusBarHooks.interval(LockFeatures.LOCK_CHARGE_INTERVAL, LockFeatures.INTERVAL_SECONDS);
            StatusBarHooks.bg().post(() -> {
                final StatusBarHooks.Battery b = StatusBarHooks.readBattery();
                main.post(() -> {
                    if (stopped) return;
                    try {
                        if (!b.charging) {
                            if (box.getVisibility() != View.GONE) box.setVisibility(View.GONE);
                        } else {
                            TextView l1 = (TextView) box.getChildAt(0), l2 = (TextView) box.getChildAt(1);
                            boolean temp = mode == 1 || mode == 3, elec = mode == 2 || mode == 3;
                            l1.setText(temp ? b.temp(1) : "");
                            l1.setVisibility(temp ? View.VISIBLE : View.GONE);
                            l2.setText(elec ? b.current() + " · " + b.voltage() + " · " + b.power(2) : "");
                            l2.setVisibility(elec ? View.VISIBLE : View.GONE);
                            int color = 0xFFFFFFFF;
                            try {
                                Object csl = XposedHelpers.callMethod(controller, "getInitialTextColorState");
                                if (csl instanceof ColorStateList) color = ((ColorStateList) csl).getDefaultColor();
                            } catch (Throwable ignored) { }
                            l1.setTextColor(color); l2.setTextColor(color);
                            if (box.getVisibility() != View.VISIBLE) box.setVisibility(View.VISIBLE);
                        }
                    } catch (Throwable t) { Lx.e("Lock: charging readout update", t); }
                    main.postDelayed(this, sec * 1000L);
                });
            });
        }
    }

    // ------------------------------------------------------------------ Always-on display

    /** "H:mm" -> "H:mm:ss"; patterns that already carry seconds are returned unchanged. */
    static String withSeconds(CharSequence f) {
        if (f == null) return null;
        String s = f.toString();
        if (s.contains("ss")) return s;
        if (s.contains(":mm")) return s.replace(":mm", ":mm:ss");
        int i = s.indexOf("mm");
        return i < 0 ? s : s.substring(0, i) + "mm:ss" + s.substring(i + 2);
    }

    private static boolean isAodClockView(View v) {
        try {
            int id = v.getId();
            if (id <= 0) return false;
            return "clock_view".equals(v.getResources().getResourceEntryName(id));
        } catch (Throwable t) { return false; }
    }

    private static void patchFormats(Object view) {
        for (String f : new String[]{"mFormat12", "mFormat24"}) {
            Object cur = XposedHelpers.getObjectField(view, f);
            if (cur != null) XposedHelpers.setObjectField(view, f, withSeconds((CharSequence) cur));
        }
    }

    private static void aod(ClassLoader cl) {
        // --- nubia AOD styles: com.zte.feature.doze.AodClock.nubia.NubiaAodTextClock
        Class<?> nubia = H.cls(cl, "com.zte.feature.doze.AodClock.nubia.NubiaAodTextClock");
        if (nubia != null) {
            // public final void chooseFormat()  — NubiaAodTextClock.java:119 ; fields "public CharSequence mFormat12 / mFormat24" (:39-40)
            H.hook(H.find(nubia, "chooseFormat"), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!Cfg.on(LockFeatures.AOD_SECONDS)) return;
                    try { patchFormats(p.thisObject); } catch (Throwable t) { Lx.e("AOD: nubia clock format", t); }
                }
            });
            // public void onVisibilityAggregated(boolean z)  — :227 ; "public final Runnable mTicker" (:49) re-posts itself every second but is never
            // started by the class itself (time comes from dozeTimeTick once a minute), so we start / stop it with visibility.
            H.hook(H.find(nubia, "onVisibilityAggregated", boolean.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        View v = (View) p.thisObject;
                        Runnable ticker = (Runnable) XposedHelpers.getObjectField(v, "mTicker");
                        if (ticker == null) return;
                        v.removeCallbacks(ticker);
                        if (Boolean.TRUE.equals(p.args[0]) && Cfg.on(LockFeatures.AOD_SECONDS)) v.post(ticker);
                    } catch (Throwable t) { Lx.e("AOD: nubia clock ticker", t); }
                }
            });
        }
        // --- Mifavor keyguard AOD styles use com.zte.mifavor.views.TextClock (own ticker, honours seconds in the format)
        Class<?> mfvClock = H.cls(cl, "com.zte.mifavor.views.TextClock");
        if (mfvClock != null) {
            // public final void chooseFormat()  — TextClock.java:171 ; fields mFormat12 / mFormat24 (:50-51); only the AOD clock (id "clock_view") is touched
            H.hook(H.find(mfvClock, "chooseFormat"), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!Cfg.on(LockFeatures.AOD_SECONDS) || !isAodClockView((View) p.thisObject)) return;
                    try { patchFormats(p.thisObject); } catch (Throwable t) { Lx.e("AOD: mifavor clock format", t); }
                }
            });
        }
        // --- classic AOD styles keep an android.widget.TextClock in "public TextClock mScreenOffClock", assigned in each subclass' onFinishInflate
        //     (AodClockStyleHorizen.java:60, AodClockDefault.java:89, AodClockBehavior.java:196, AodClockVideo.java:463, AodClockCoolRunner.java:544, AodClockCalender.java:165)
        int hooked = 0;
        for (String name : new String[]{"AodClockStyleHorizen", "AodClockDefault", "AodClockBehavior", "AodClockVideo", "AodClockCoolRunner", "AodClockCalender"}) {
            Class<?> k = XposedHelpers.findClassIfExists("com.zte.feature.doze.AodClock.AodClockStyle." + name, cl);
            if (k == null) { Lx.w("AOD: style class missing: " + name); continue; }
            final boolean horizen = name.equals("AodClockStyleHorizen");
            java.lang.reflect.Method inflate;
            try { inflate = k.getDeclaredMethod("onFinishInflate"); }   // only the subclass' own override (the base one runs before the field is set)
            catch (NoSuchMethodException e) { Lx.w("AOD: " + name + " has no own onFinishInflate"); continue; }
            if (H.hook(inflate, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!Cfg.on(LockFeatures.AOD_SECONDS)) return;
                    try {
                        Object clock = XposedHelpers.getObjectField(p.thisObject, "mScreenOffClock");
                        if (clock instanceof TextClock) {
                            TextClock tc = (TextClock) clock;
                            tc.setFormat24Hour(withSeconds(tc.getFormat24Hour() == null ? "H:mm" : tc.getFormat24Hour()));
                            tc.setFormat12Hour(withSeconds(tc.getFormat12Hour() == null ? "h:mm" : tc.getFormat12Hour()));
                        }
                        if (horizen) {
                            // RedMagic Helper widens the horizontal style so the extra ":ss" fits
                            View v = (View) p.thisObject;
                            ViewGroup.LayoutParams lp = v.getLayoutParams();
                            if (lp != null && lp.width != ViewGroup.LayoutParams.WRAP_CONTENT) { lp.width = ViewGroup.LayoutParams.WRAP_CONTENT; v.setLayoutParams(lp); }
                        }
                    } catch (Throwable t) { Lx.e("AOD: " + p.thisObject.getClass().getSimpleName() + " seconds", t); }
                }
            })) hooked++;
        }
        Lx.i("AOD: seconds hooked on " + hooked + " classic clock styles");
        // --- public boolean isShowDateTime()  — MyAodStyleModel.java:165 (StyleData): false hides the date/time line of a style
        Class<?> styleData = H.cls(cl, "com.zte.feature.doze.AodClock.AodClockStyle.MyAodStyleModel$StyleData");
        if (styleData != null) {
            H.hook(H.find(styleData, "isShowDateTime"), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { if (Cfg.on(LockFeatures.AOD_DATETIME)) p.setResult(true); }
            });
        }
    }

    // ------------------------------------------------------------------ lock screen clock & date (nubia-ly SurfaceBackend groups 2 / 3)

    private static final String BASE_CLOCK = "com.zte.mifavor.keyguard.settings.BaseLockScreenClock";
    private static final String STYLE_ITEM = "com.zte.mifavor.keyguard.settings.LockScreenClockStyleItem";
    private static final String HOST_ZTE = "com.zte.mifavor.keyguard.KeyguardStatusView";
    private static final String HOST_AOSP = "com.android.keyguard.KeyguardStatusView";
    private static final String CONTENT = "com.zte.mifavor.keyguard.personalclock.ClockContentView";
    private static final String NUBIA_STATUS = "cn.nubia.lockscreen.NubiaKeyguardStatusView";

    /** Container fields are preferred; a child of another chosen view is skipped so transforms never compound. */
    private static final String[] CLOCK_FIELDS = {"mClockArea", "mClockView", "mClockHourView", "mClockMinuteView", "mClockMinuteMaskView", "mClipDividerView",
        "mImageView1", "mImageView2", "mImageView3", "mImageView4", "mClockHours", "mClockMinute", "mDivider", "mAmPm", "mHour", "mMinute", "mBackground"};
    private static final String[] DATE_FIELDS = {"mDateViewArea", "mDateView", "mDayDate", "mMonthDate", "mHorizLunarDateWidget"};
    /** Dial hands are rotated by the clock itself every minute; never add our tilt to them. */
    private static final List<String> NO_ROTATION = Arrays.asList("mHour", "mMinute", "mBackground");

    /** Stock transform of a view, captured the first time a non-stock look is applied; restored once when everything is stock again. */
    private static final class Stock { float sx, sy, a, tx, ty, r; int vis; }
    private static final Map<View, Stock> stocks = new WeakHashMap<>();

    private static final class Look {
        final boolean hide; final int scale, opacity, dx, dy, rot;
        Look(boolean clock) {
            hide = Cfg.on(clock ? LockFeatures.CLOCK_HIDE : LockFeatures.DATE_HIDE);
            scale = pick(clock ? LockFeatures.CLOCK_SIZE : LockFeatures.DATE_SIZE, LockFeatures.SCALE_PERCENT);
            opacity = pick(clock ? LockFeatures.CLOCK_OPACITY : LockFeatures.DATE_OPACITY, LockFeatures.OPACITY_PERCENT);
            dx = pick(clock ? LockFeatures.CLOCK_DX : LockFeatures.DATE_DX, LockFeatures.OFFSET_DP);
            dy = pick(clock ? LockFeatures.CLOCK_DY : LockFeatures.DATE_DY, LockFeatures.OFFSET_DP);
            rot = pick(clock ? LockFeatures.CLOCK_ROT : LockFeatures.DATE_ROT, LockFeatures.ROTATION_DEG);
        }
        boolean any() { return hide || scale != 100 || opacity != 100 || dx != 0 || dy != 0 || rot != 0; }
        private static int pick(String key, int[] values) { int i = Cfg.choice(key); return i > 0 && i < values.length ? values[i] : values[0]; }
    }

    private static boolean near(float a, float b) { return Math.abs(a - b) < 0.001f; }

    private static void paint(View v, Look k, boolean allowRotation) {
        if (v == null) return;
        Stock s = stocks.get(v);
        if (!k.any()) {
            if (s == null) return;
            v.setScaleX(s.sx); v.setScaleY(s.sy); v.setAlpha(s.a); v.setTranslationX(s.tx); v.setTranslationY(s.ty);
            if (allowRotation) v.setRotation(s.r);
            if (v.getVisibility() == View.GONE && s.vis != View.GONE) v.setVisibility(s.vis);
            stocks.remove(v);
            return;
        }
        if (s == null) {
            s = new Stock();
            s.sx = v.getScaleX(); s.sy = v.getScaleY(); s.a = v.getAlpha(); s.tx = v.getTranslationX(); s.ty = v.getTranslationY(); s.r = v.getRotation(); s.vis = v.getVisibility();
            stocks.put(v, s);
        }
        float d = v.getResources().getDisplayMetrics().density;
        float sx = s.sx * k.scale / 100f, sy = s.sy * k.scale / 100f, a = s.a * k.opacity / 100f, tx = s.tx + k.dx * d, ty = s.ty + k.dy * d, r = s.r + k.rot;
        if (!near(v.getScaleX(), sx)) v.setScaleX(sx);
        if (!near(v.getScaleY(), sy)) v.setScaleY(sy);
        if (!near(v.getAlpha(), a)) v.setAlpha(a);
        if (!near(v.getTranslationX(), tx)) v.setTranslationX(tx);
        if (!near(v.getTranslationY(), ty)) v.setTranslationY(ty);
        if (allowRotation && !near(v.getRotation(), r)) v.setRotation(r);
        if (k.hide) { if (v.getVisibility() != View.GONE) v.setVisibility(View.GONE); }
        else if (v.getVisibility() == View.GONE && s.vis != View.GONE) v.setVisibility(s.vis);
    }

    private static View fieldView(Object o, String name) {
        for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            try { Field f = k.getDeclaredField(name); f.setAccessible(true); Object v = f.get(o); return v instanceof View ? (View) v : null; }
            catch (NoSuchFieldException ignored) { } catch (Throwable t) { return null; }
        }
        return null;
    }

    private static boolean descendantOf(View v, View ancestor) {
        for (android.view.ViewParent p = v.getParent(); p instanceof View; p = ((View) p).getParent()) if (p == ancestor) return true;
        return false;
    }

    private static final class Picked { final View view; final String name; Picked(View v, String n) { view = v; name = n; } }

    /** The views of one group inside a clock style, containers first; a descendant of an already chosen view is dropped. */
    private static List<Picked> pickViews(Object style, String[] names) {
        List<Picked> out = new ArrayList<>();
        for (String n : names) {
            View v = fieldView(style, n);
            if (v == null) continue;
            boolean nested = false;
            for (Picked c : out) if (c.view == v || descendantOf(v, c.view)) { nested = true; break; }
            if (!nested) out.add(new Picked(v, n));
        }
        return out;
    }

    /** True for a clock style that is the live lock screen (scene 1 under a KeyguardStatusView host, not a chooser preview). */
    private static boolean liveStyle(Object style) {
        try {
            if (!(style instanceof View)) return false;
            if (XposedHelpers.getIntField(style, "mScene") != 1) return false;
            View cur = (View) style;
            for (int depth = 0; cur != null && depth < 24; depth++) {
                String n = cur.getClass().getName();
                if (n.equals(CONTENT)) return false;
                if (n.equals(HOST_ZTE) || n.equals(HOST_AOSP)) return true;
                if (n.equals(STYLE_ITEM) && Boolean.TRUE.equals(XposedHelpers.getObjectField(cur, "mIsSmall"))) return false;
                android.view.ViewParent p = cur.getParent();
                cur = p instanceof View ? (View) p : null;
            }
        } catch (Throwable t) { Lx.w("Lock: clock style state unreadable: " + t); }
        return false;
    }

    private static void applyStyle(Object style) {
        if (style == null || !liveStyle(style)) return;
        if (OneUiLockHooks.clockOn()) return;   // the One UI clock replaces the stock style (OneUiLockHooks)
        try {
            Look clock = new Look(true), date = new Look(false);
            for (Picked k : pickViews(style, CLOCK_FIELDS)) paint(k.view, clock, !NO_ROTATION.contains(k.name));
            for (Picked k : pickViews(style, DATE_FIELDS)) paint(k.view, date, true);
        } catch (Throwable t) { Lx.e("Lock: clock/date apply", t); }
    }

    private static void applyLater(final Object style) {
        if (!(style instanceof View)) return;
        ((View) style).postDelayed(() -> applyStyle(style), 350);
    }

    private static void lockClock(ClassLoader cl) {
        Class<?> item = H.cls(cl, STYLE_ITEM);
        Class<?> base = H.cls(cl, BASE_CLOCK);
        if (item != null) {
            // public void addClockView(int, boolean, boolean)  — LockScreenClockStyleItem.java:62 (inflates the style into "public LockScreenClockStyleInterface mClock");
            // public void refreshTime() (:228) and public void updateColor(int) (:257) run whenever the keyguard (re)appears.
            XC_MethodHook after = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Object clock = XposedHelpers.getObjectField(p.thisObject, "mClock");
                        applyStyle(clock);
                        if (p.method.getName().equals("addClockView")) applyLater(clock);
                    } catch (Throwable t) { Lx.e("Lock: style item", t); }
                }
            };
            H.hookAll(item, "addClockView", after);
            H.hookAll(item, "refreshTime", after);
            H.hookAll(item, "updateColor", after);
        }
        if (base != null) {
            // public void onAttachedToWindow()  — BaseLockScreenClock.java:78 ; public void setScene(int) (:220) ; public void updateStyleData(StyleData) (:278)
            XC_MethodHook after = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { applyStyle(p.thisObject); applyLater(p.thisObject); }
            };
            H.hookAll(base, "onAttachedToWindow", after);
            H.hookAll(base, "setScene", after);
            H.hookAll(base, "updateStyleData", after);
        }
        // poster lock screen: cn.nubia.lockscreen.NubiaKeyguardStatusView — "public TextClock mClockView" (:39), "public NubiaTextClock mDateView" (:42),
        // "public boolean mPulsing" (:53), "public float mDarkAmount" (:40); public void onFinishInflate() (:239), refreshTime() (:264), setPulsing(boolean) (:328)
        Class<?> nubia = H.cls(cl, NUBIA_STATUS);
        if (nubia != null) {
            XC_MethodHook after = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Object o = p.thisObject;
                        if (Boolean.TRUE.equals(XposedHelpers.getObjectField(o, "mPulsing")) || XposedHelpers.getFloatField(o, "mDarkAmount") != 0f) return;
                        paint(fieldView(o, "mClockView"), new Look(true), true);
                        paint(fieldView(o, "mDateView"), new Look(false), true);
                    } catch (Throwable t) { Lx.e("Lock: poster clock", t); }
                }
            };
            H.hookAll(nubia, "onFinishInflate", after);
            H.hookAll(nubia, "refreshTime", after);
            H.hookAll(nubia, "setPulsing", after);
        }
        Lx.i("Lock: clock/date appearance hooks ready");
    }

    // ------------------------------------------------------------------ lock screen widgets (nubia-ly SurfaceBackend groups 4..6)

    private static final String WIDGET_BASE = "com.zte.feature.keyguardwidget.BaseKeyguardWidgetView";
    private static final String WIDGET_CONTAINER = "com.zte.feature.keyguardwidget.KeyguardWidgetContainer";
    private static final Map<View, Boolean> hiddenWidgets = new WeakHashMap<>();

    /** The hide switch for a widget class (WeatherWidgetView*, AlarmWidgetView*, BatteryWidgetView*), or null. */
    private static String widgetKey(View w) {
        String n = w.getClass().getSimpleName();
        if (n.startsWith("WeatherWidgetView")) return LockFeatures.HIDE_WEATHER;
        if (n.startsWith("AlarmWidgetView")) return LockFeatures.HIDE_ALARM;
        if (n.startsWith("BatteryWidgetView")) return LockFeatures.HIDE_BATTERY;
        return null;
    }

    private static void applyWidget(View w) {
        try {
            String key = widgetKey(w);
            if (key == null) return;
            // "public int mType" — 0 = the live keyguard container (KeyguardWidgetFeature.addKeyguardWidgetContainer scene 1), 1 / 2 = chooser previews
            int type = XposedHelpers.getIntField(w, "mType");
            if (type != 0) return;
            if (Cfg.on(key)) {
                if (w.getVisibility() != View.GONE) { w.setVisibility(View.GONE); hiddenWidgets.put(w, Boolean.TRUE); }
            } else if (hiddenWidgets.remove(w) != null && w.getVisibility() == View.GONE) {
                w.setVisibility(View.VISIBLE);
            }
        } catch (Throwable t) { Lx.e("Lock: widget", t); }
    }

    private static void widgets(ClassLoader cl) {
        Class<?> base = H.cls(cl, WIDGET_BASE);
        if (base != null) {
            // public void onAttachedToWindow()  — BaseKeyguardWidgetView.java:175
            H.hookAll(base, "onAttachedToWindow", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { applyWidget((View) p.thisObject); }
            });
        }
        Class<?> container = H.cls(cl, WIDGET_CONTAINER);
        if (container != null) {
            // public void updateLayout() (:283), public void addWidget(int) (:50), public void updateVisible() (:314) — re-check every child widget
            XC_MethodHook after = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        ViewGroup g = (ViewGroup) p.thisObject;
                        for (int i = 0; i < g.getChildCount(); i++) applyWidget(g.getChildAt(i));
                    } catch (Throwable t) { Lx.e("Lock: widget container", t); }
                }
            };
            H.hookAll(container, "updateLayout", after);
            H.hookAll(container, "addWidget", after);
            H.hookAll(container, "updateVisible", after);
        }
        Lx.i("Lock: widget hooks ready");
    }
}
