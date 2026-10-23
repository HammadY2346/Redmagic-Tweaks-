package com.hammad.redmagictweaks.xposed;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.TrafficStats;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.hammad.redmagictweaks.StatusBarFeatures;

import java.io.BufferedReader;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * com.android.systemui (main process) — status bar clock / battery / network speed / icon hiding,
 * notification icons, Quick Settings layout and a few System UI dialogs.
 * Every target below was checked against the firmware 11.0.11 decompile; the declaration is quoted
 * above each hook.
 */
final class StatusBarHooks {
    private StatusBarHooks() {}

    static final String TAG_INFO = "rmtweaks_sb_battinfo";

    static void install(ClassLoader cl) {
        clock(cl);
        batteryStyle(cl);
        batteryInfo(cl);
        netSpeed(cl);
        hideIcons(cl);
        // notificationIcons(cl) / quickSettings(cl): superseded by SurfaceHooks (notif_icon_limit) and QsHooks (qs_cols/qs_rows)
        notificationAppIcon(cl);
        dialogs(cl);
        StatusBarSizeHooks.install(cl);
        Lx.i("StatusBar: hooks ready");
    }

    // ------------------------------------------------------------------ shared helpers

    /** Boolean pref read at most once a second: for hot paths (touch events, layout passes). */
    static final class Flag {
        private final String key; private long at; private boolean v;
        Flag(String key) { this.key = key; }
        boolean get() {
            long now = SystemClock.uptimeMillis();
            if (now - at > 1000) { v = Cfg.on(key); at = now; }
            return v;
        }
    }

    /** Int (choice) pref read at most once a second. */
    static final class Choice {
        private final String key; private long at; private int v, cv; private boolean custom; private int[] color;
        Choice(String key) { this.key = key; }
        int get() {
            long now = SystemClock.uptimeMillis();
            if (now - at > 1000) {
                v = Cfg.choice(key); custom = Cfg.hasNum(key); cv = custom ? Cfg.num(key, 0) : 0;
                color = key.endsWith("color") ? Cfg.color(key) : null;   // colour options (ColorPick)
                at = now;
            }
            return v;
        }
        /** The custom colour (ColorPick) when one is in use, else null. */
        int[] color() { get(); return color; }
        /** The typed value (Features.Num) when one is in use, else the preset's value. */
        int value(int preset) { get(); return custom ? cv : preset; }
    }

    private static Handler bgHandler;
    /** Background handler for sysfs reads (shared with LockHooks). */
    static synchronized Handler bg() {
        if (bgHandler == null) {
            HandlerThread t = new HandlerThread("rmtweaks-sysfs");
            t.start();
            bgHandler = new Handler(t.getLooper());
        }
        return bgHandler;
    }

    static int dp(Context c, float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()); }

    static int id(Context c, String name) {
        try { return c.getResources().getIdentifier(name, "id", c.getPackageName()); } catch (Throwable t) { return 0; }
    }

    /** Snapshot of /sys/class/power_supply/battery (uevent first, single files as fallback). */
    static final class Battery {
        double tempC = Double.NaN;
        long currentUa = Long.MIN_VALUE;
        long voltageUv = Long.MIN_VALUE;
        boolean charging;
        boolean hasCurrent() { return currentUa != Long.MIN_VALUE; }
        boolean hasVoltage() { return voltageUv != Long.MIN_VALUE; }
        boolean hasTemp() { return !Double.isNaN(tempC); }
        /** Watts, or NaN. */
        double powerW() { return hasCurrent() && hasVoltage() ? (Math.abs(currentUa) / 1e6) * (voltageUv / 1e6) : Double.NaN; }
        String temp(int decimals) { return hasTemp() ? String.format(Locale.US, decimals == 0 ? "%.0f℃" : "%.1f℃", tempC) : "--℃"; }
        String current() {
            if (!hasCurrent()) return "--mA";
            double mA = Math.abs(currentUa) / 1000.0;
            return mA >= 1000 ? String.format(Locale.US, "%.2fA", mA / 1000.0) : String.format(Locale.US, "%.0fmA", mA);
        }
        String voltage() { return hasVoltage() ? String.format(Locale.US, "%.2fV", voltageUv / 1e6) : "--V"; }
        String power(int decimals) { double w = powerW(); return Double.isNaN(w) ? "--W" : String.format(Locale.US, decimals == 1 ? "%.1fW" : "%.2fW", w); }
    }

    private static final String PS = "/sys/class/power_supply/battery/";

    static Battery readBattery() {
        Battery b = new Battery();
        boolean any = false;
        try (BufferedReader r = new BufferedReader(new FileReader(PS + "uevent"))) {
            String line;
            while ((line = r.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String k = line.substring(0, eq), v = line.substring(eq + 1).trim();
                try {
                    switch (k) {
                        case "POWER_SUPPLY_TEMP": b.tempC = Long.parseLong(v) / 10.0; any = true; break;
                        case "POWER_SUPPLY_CURRENT_NOW": b.currentUa = Long.parseLong(v); any = true; break;
                        case "POWER_SUPPLY_VOLTAGE_NOW": b.voltageUv = Long.parseLong(v); any = true; break;
                        case "POWER_SUPPLY_STATUS": b.charging = "Charging".equals(v) || "Full".equals(v); break;
                        default: break;
                    }
                } catch (NumberFormatException ignored) { }
            }
        } catch (Throwable ignored) { }
        if (!any) {
            Long t = readLong(PS + "temp"), c = readLong(PS + "current_now"), v = readLong(PS + "voltage_now");
            if (t != null) b.tempC = t / 10.0;
            if (c != null) b.currentUa = c;
            if (v != null) b.voltageUv = v;
            String s = readLine(PS + "status");
            b.charging = "Charging".equals(s) || "Full".equals(s);
        }
        return b;
    }

    private static String readLine(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) { String l = r.readLine(); return l == null ? null : l.trim(); }
        catch (Throwable t) { return null; }
    }
    private static Long readLong(String path) {
        String s = readLine(path);
        if (s == null) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    /** Styles a TextView like the status bar clock so it follows the status bar look. */
    static TextView makeLabel(Context c, TextView like, float scale) {
        TextView tv = new TextView(c);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        if (like != null) {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, like.getTextSize() * scale);
            tv.setTextColor(like.getCurrentTextColor());
            tv.setTypeface(like.getTypeface());
            tv.setLetterSpacing(like.getLetterSpacing());
        } else {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            tv.setTextColor(0xFFFFFFFF);
        }
        tv.setPaddingRelative(dp(c, 2), 0, dp(c, 2), 0);
        return tv;
    }

    // ------------------------------------------------------------------ clock

    private static final WeakHashMap<Object, Boolean> topClock = new WeakHashMap<>();
    private static final Choice clockFormat = new Choice(StatusBarFeatures.SB_CLOCK_FORMAT);
    private static String fmtPattern; private static SimpleDateFormat fmt;

    /** Only the clock in the top status bar (ClockAdapt.mViewType.isTopStatusBar()), not the QS header copy. */
    static boolean isTopStatusBarClock(Object clock) {
        synchronized (topClock) {
            Boolean b = topClock.get(clock);
            if (b != null) return b;
        }
        boolean top = false;
        try {
            Object adapt = XposedHelpers.callMethod(clock, "getAdapt");
            Object type = adapt == null ? null : XposedHelpers.getObjectField(adapt, "mViewType");
            top = type != null && Boolean.TRUE.equals(XposedHelpers.callMethod(type, "isTopStatusBar"));
        } catch (Throwable t) { Lx.w("StatusBar: clock view type unknown: " + t); }
        synchronized (topClock) { topClock.put(clock, top); }
        return top;
    }

    private static String pattern() {
        int i = clockFormat.get();
        return i > 0 && i < StatusBarFeatures.CLOCK_PATTERNS.length ? StatusBarFeatures.CLOCK_PATTERNS[i] : "";
    }

    private static void clock(ClassLoader cl) {
        Class<?> clock = H.cls(cl, "com.android.systemui.statusbar.policy.Clock");
        if (clock == null) return;
        // public void updateShowSeconds()  — Clock.java:576, field "public boolean mShowSeconds" (:74).
        // ClockAdapt.handleShowSeconds() swallows the tuner value on this firmware, so we force the field.
        H.hook(H.find(clock, "updateShowSeconds"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    if (!isTopStatusBarClock(p.thisObject)) return;
                    if (Cfg.on(StatusBarFeatures.SB_CLOCK_SECONDS) || pattern().contains("ss")) {
                        XposedHelpers.setBooleanField(p.thisObject, "mShowSeconds", true);
                    }
                } catch (Throwable t) { Lx.e("StatusBar: seconds", t); }
            }
        });
        // public final void updateClock()  — Clock.java:557, field "public Calendar mCalendar" (:56)
        H.hook(H.find(clock, "updateClock"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    String pat = pattern();
                    if (pat.isEmpty() || !isTopStatusBarClock(p.thisObject)) return;
                    Calendar cal = (Calendar) XposedHelpers.getObjectField(p.thisObject, "mCalendar");
                    if (cal == null) return;
                    SimpleDateFormat f;
                    synchronized (StatusBarHooks.class) {
                        if (fmt == null || !pat.equals(fmtPattern)) { fmt = new SimpleDateFormat(pat, Locale.getDefault()); fmtPattern = pat; }
                        f = fmt;
                    }
                    String s;
                    synchronized (f) { s = f.format(cal.getTime()); }
                    TextView tv = (TextView) p.thisObject;
                    if (!s.contentEquals(tv.getText())) tv.setText(s);
                } catch (Throwable t) { Lx.e("StatusBar: clock format", t); }
            }
        });
        // public void onDarkChanged(ArrayList<Rect>, float, int)  — Clock.java:398 : propagate the tint to our readout
        H.hookAll(clock, "onDarkChanged", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    View v = (View) p.thisObject;
                    View root = v.getRootView();
                    View box = root == null ? null : root.findViewWithTag(TAG_INFO);
                    if (box instanceof TextView) ((TextView) box).setTextColor(((TextView) v).getCurrentTextColor());
                } catch (Throwable ignored) { }
            }
        });
    }

    // ------------------------------------------------------------------ battery icon style

    private static void batteryStyle(ClassLoader cl) {
        Class<?> bvl = H.cls(cl, "com.zte.mifavor.views.MFVBatteryViewLayout");
        if (bvl == null) return;
        // public int getLevelDisplayMode()  — MFVBatteryViewLayout.java:365 (0 icon only, 1 percent inside, 2 percent outside, 3 hidden)
        H.hook(H.find(bvl, "getLevelDisplayMode"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                switch (Cfg.choice(StatusBarFeatures.SB_BATTERY_STYLE)) {
                    case 1: p.setResult(0); break;
                    case 2: p.setResult(1); break;
                    case 3: case 4: p.setResult(2); break;
                    case 5: p.setResult(3); break;
                    default: break;
                }
            }
        });
        // public final void updateBatteryLayout(int i, boolean z)  — :652; fields mBatteryContainer (:52), mBatteryLevelOutsideView (:59)
        H.hook(H.find(bvl, "updateBatteryLayout", int.class, boolean.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (Cfg.choice(StatusBarFeatures.SB_BATTERY_STYLE) != 4) return;
                try {
                    View container = (View) XposedHelpers.getObjectField(p.thisObject, "mBatteryContainer");
                    View level = (View) XposedHelpers.getObjectField(p.thisObject, "mBatteryLevelOutsideView");
                    if (container != null && container.getVisibility() != View.GONE) container.setVisibility(View.GONE);
                    if (level != null && level.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
                        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) level.getLayoutParams();
                        lp.removeRule(RelativeLayout.END_OF);
                        lp.removeRule(RelativeLayout.START_OF);
                        lp.removeRule(RelativeLayout.LEFT_OF);
                        lp.removeRule(RelativeLayout.RIGHT_OF);
                        lp.addRule(RelativeLayout.ALIGN_PARENT_START);
                        level.setLayoutParams(lp);
                    }
                } catch (Throwable t) { Lx.e("StatusBar: percent-only battery", t); }
            }
        });
    }

    // ------------------------------------------------------------------ battery readout (temp / current / power)

    private static void batteryInfo(ClassLoader cl) {
        Class<?> psb = H.cls(cl, "com.android.systemui.statusbar.phone.PhoneStatusBarView");
        if (psb == null) return;
        // public void onFinishInflate()  — PhoneStatusBarView.java:140 ; views found by id name "clock" / "battery"
        H.hook(H.find(psb, "onFinishInflate"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try { attachInfo((ViewGroup) p.thisObject); } catch (Throwable t) { Lx.e("StatusBar: battery readout", t); }
            }
        });
    }

    private static void attachInfo(ViewGroup bar) {
        Context c = bar.getContext();
        if (bar.findViewWithTag(TAG_INFO) != null) return;
        int clockId = id(c, "clock");
        View clock = clockId == 0 ? null : bar.findViewById(clockId);
        TextView like = clock instanceof TextView ? (TextView) clock : null;
        int pos = Cfg.choice(StatusBarFeatures.SB_BATTERY_INFO_POS);
        View anchor = clock; int offset = 1;
        if (pos == 1) {
            int bid = id(c, "battery");
            View batt = bid == 0 ? null : bar.findViewById(bid);
            if (batt != null) { anchor = batt; offset = 0; }
            else Lx.w("StatusBar: battery view not found, readout goes next to the clock");
        }
        if (anchor == null || !(anchor.getParent() instanceof ViewGroup)) {
            Lx.w("StatusBar: clock view not found in PhoneStatusBarView — battery readout skipped");
            return;
        }
        ViewGroup parent = (ViewGroup) anchor.getParent();
        TextView tv = makeLabel(c, like, 0.85f);
        tv.setTag(TAG_INFO);
        tv.setVisibility(View.GONE);
        ViewGroup.LayoutParams lp = parent instanceof LinearLayout
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
                : new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        if (lp instanceof LinearLayout.LayoutParams) ((LinearLayout.LayoutParams) lp).gravity = Gravity.CENTER_VERTICAL;
        int idx = parent.indexOfChild(anchor);
        parent.addView(tv, idx < 0 ? -1 : idx + offset, lp);
        new InfoPoller(tv).start();
        Lx.i("StatusBar: battery readout view attached (" + (pos == 1 ? "before battery" : "after clock") + ")");
    }

    /** Reads sysfs on the background handler every N seconds and updates the label on the main thread. */
    private static final class InfoPoller implements Runnable, View.OnAttachStateChangeListener {
        private final TextView tv;
        private final Handler main = new Handler(Looper.getMainLooper());
        private boolean stopped;
        InfoPoller(TextView tv) { this.tv = tv; }

        void start() {
            tv.addOnAttachStateChangeListener(this);
            if (tv.isAttachedToWindow()) main.post(this);
        }
        @Override public void onViewAttachedToWindow(View v) { stopped = false; main.removeCallbacks(this); main.post(this); }
        @Override public void onViewDetachedFromWindow(View v) { stopped = true; main.removeCallbacks(this); }

        @Override public void run() {
            if (stopped) return;
            final int mode = Cfg.choice(StatusBarFeatures.SB_BATTERY_INFO);
            if (mode <= 0) {
                if (tv.getVisibility() != View.GONE) tv.setVisibility(View.GONE);
                main.postDelayed(this, 5000);
                return;
            }
            final int sec = interval(StatusBarFeatures.SB_BATTERY_INTERVAL, StatusBarFeatures.INTERVAL_SECONDS);
            bg().post(() -> {
                final Battery b = readBattery();
                main.post(() -> {
                    if (stopped) return;
                    try {
                        StringBuilder s = new StringBuilder();
                        if (mode == 1 || mode == 4 || mode == 5) s.append(b.temp(0));
                        if (mode == 2 || mode == 5) { if (s.length() > 0) s.append(' '); s.append(b.current()); }
                        if (mode == 3 || mode == 4 || mode == 5) { if (s.length() > 0) s.append(' '); s.append(b.power(1)); }
                        tv.setText(s);
                        if (tv.getVisibility() != View.VISIBLE) tv.setVisibility(View.VISIBLE);
                    } catch (Throwable t) { Lx.e("StatusBar: readout update", t); }
                    main.postDelayed(this, sec * 1000L);
                });
            });
        }
    }

    static int interval(String key, int[] table) {
        int i = Cfg.choice(key);
        return Math.max(1, Cfg.num(key, i >= 0 && i < table.length ? table[i] : table[0]));   // preset or typed seconds
    }

    // ------------------------------------------------------------------ network speed

    private static final Choice netStyle = new Choice(StatusBarFeatures.SB_NET_STYLE);
    private static final Choice netColor = new Choice(StatusBarFeatures.SB_NET_COLOR), netDx = new Choice(StatusBarFeatures.SB_NET_DX),
            netDy = new Choice(StatusBarFeatures.SB_NET_DY), clockColor = new Choice(com.hammad.redmagictweaks.ClockFeatures.CLK_COLOR);
    /** The colour System UI last gave a speed TextView (put back when the colour returns to stock). */
    private static final java.util.Map<TextView, Integer> netStockColor = new java.util.WeakHashMap<>();
    private static final java.util.Map<View, Boolean> netMoved = new java.util.WeakHashMap<>();
    private static long lastTx, lastRx, lastAt; private static double txRate, rxRate;

    private static void netSpeed(ClassLoader cl) {
        // SpeedControllerImpl.java:48 "public Runnable mRunnable = new Runnable() { run() { mHandler.postDelayed(mRunnable, 3000L); updateSpeedLevel(); updateVisibility(true); } }"
        Class<?> runnable = H.cls(cl, "com.zte.feature.speed.SpeedControllerImpl$1");
        if (runnable != null) {
            H.hook(H.find(runnable, "run"), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!Cfg.on(StatusBarFeatures.SB_NET_FAST)) return;
                    try {
                        Object ctl = XposedHelpers.getSurroundingThis(p.thisObject);
                        Handler h = (Handler) XposedHelpers.getObjectField(ctl, "mHandler");
                        Runnable r = (Runnable) XposedHelpers.getObjectField(ctl, "mRunnable");
                        h.postDelayed(r, 1000L);
                        XposedHelpers.callMethod(ctl, "updateSpeedLevel");
                        XposedHelpers.callMethod(ctl, "updateVisibility", true);
                        p.setResult(null);
                    } catch (Throwable t) { Lx.e("StatusBar: net speed 1s", t); }
                }
            });
        }
        // SpeedControllerImpl.java:73 "mSpeedLevelObserver = new ContentObserver(mHandler) { onChange(boolean) { mSwitchOn = Settings.System network_speed_display_on == 1; update(true); } }"
        Class<?> observer = H.cls(cl, "com.zte.feature.speed.SpeedControllerImpl$3");
        if (observer != null) {
            H.hook(H.find(observer, "onChange", boolean.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!Cfg.on(StatusBarFeatures.SB_NET_SHOW)) return;
                    try {
                        Object ctl = XposedHelpers.getSurroundingThis(p.thisObject);
                        if (!XposedHelpers.getBooleanField(ctl, "mSwitchOn")) {
                            XposedHelpers.setBooleanField(ctl, "mSwitchOn", true);
                            XposedHelpers.callMethod(ctl, "update", true);
                        }
                    } catch (Throwable t) { Lx.e("StatusBar: net speed switch", t); }
                }
            });
        }
        Class<?> mfv = H.cls(cl, "com.zte.feature.speed.StatusBarNetSpeedMFV");
        if (mfv != null) {
            // private void init()  — StatusBarNetSpeedMFV.java:56 ; fields mSpeedText/mSpeedUnit/mSpeedViewGroup (:32-34), mShowOnStatusBar (:65)
            H.hook(H.find(mfv, "init"), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (Cfg.on(StatusBarFeatures.SB_NET_SHOW)) XposedHelpers.setBooleanField(p.thisObject, "mShowOnStatusBar", true);
                        styleNetView(p.thisObject);
                    } catch (Throwable t) { Lx.e("StatusBar: net speed view", t); }
                }
            });
            // public void refreshNetSpeedView()  — :171 (re-applies the stock text sizes on configuration change)
            H.hook(H.find(mfv, "refreshNetSpeedView"), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try { styleNetView(p.thisObject); } catch (Throwable t) { Lx.e("StatusBar: net speed view refresh", t); }
                    try { netLook(p.thisObject, false); } catch (Throwable t) { Lx.e("StatusBar: net speed look", t); }
                }
            });
            // colour and nudge: System UI tints the text in onDarkChanged(ArrayList, float, int) / setStaticDrawableColor /
            // setDecorColor (same on China and global); the text changes in applyNetSpeedState / updateState
            XC_MethodHook tinted = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try { netLook(p.thisObject, true); } catch (Throwable t) { Lx.e("StatusBar: net speed colour", t); }
                }
            };
            XC_MethodHook updated = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try { netLook(p.thisObject, false); } catch (Throwable t) { Lx.e("StatusBar: net speed look", t); }
                }
            };
            int nl = 0;
            nl += H.hookAll(mfv, "onDarkChanged", tinted);
            nl += H.hookAll(mfv, "setStaticDrawableColor", tinted);
            nl += H.hookAll(mfv, "setDecorColor", tinted);
            nl += H.hookAll(mfv, "applyNetSpeedState", updated);
            nl += H.hookAll(mfv, "updateState", updated);
            Lx.i("StatusBar: net speed colour / nudge hooks " + nl);
        }
        Class<?> policy = H.cls(cl, "com.zte.feature.speed.StatusBarNetSpeedPolicy");
        Class<?> state = H.cls(cl, "com.zte.feature.speed.StatusBarNetSpeedPolicy$NetSpeedState");
        if (policy != null && state != null) {
            // public final void updateNetSpeedDisplay(NetSpeedState)  — StatusBarNetSpeedPolicy.java:84 ; NetSpeedState { String speedText; String speedUnit; boolean visible; } (:19-22)
            H.hook(H.find(policy, "updateNetSpeedDisplay", state), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (netStyle.get() <= 0 || p.args[0] == null) return;
                    try {
                        sample();
                        XposedHelpers.setObjectField(p.args[0], "speedText", "↑" + rate(txRate));
                        XposedHelpers.setObjectField(p.args[0], "speedUnit", "↓" + rate(rxRate));
                    } catch (Throwable t) { Lx.e("StatusBar: net speed rows", t); }
                }
            });
        }
    }

    /** Network speed colour (StatusBarFeatures.NET_COLORS, 0 = stock) as ARGB, or 0 for the stock tint. */
    private static int netColorValue() {
        int c = netColor.get();
        int[] values = com.hammad.redmagictweaks.ClockFeatures.COLOR_VALUES;
        if (netColor.color() != null) return netColor.color()[0];
        if (c == 1) { int k = clockColor.get(); if (clockColor.color() != null) return clockColor.color()[0]; return k > 0 && k < values.length ? values[k] : 0; }
        return c >= 2 && c - 1 < values.length ? values[c - 1] : 0;
    }

    /** Colour and nudge of the network speed readout. {@code stockJustSet}: System UI has just tinted the text. */
    private static void netLook(Object view, boolean stockJustSet) {
        TextView text = (TextView) XposedHelpers.getObjectField(view, "mSpeedText");
        TextView unit = (TextView) XposedHelpers.getObjectField(view, "mSpeedUnit");
        View group = (View) XposedHelpers.getObjectField(view, "mSpeedViewGroup");
        int color = netColorValue();
        for (TextView t : new TextView[]{text, unit}) {
            if (t == null) continue;
            if (stockJustSet) netStockColor.put(t, t.getCurrentTextColor());
            if (color != 0) {
                if (!netStockColor.containsKey(t)) netStockColor.put(t, t.getCurrentTextColor());
                if (t.getCurrentTextColor() != color) t.setTextColor(color);
            } else {
                Integer st = netStockColor.remove(t);
                if (st != null && !stockJustSet && t.getCurrentTextColor() != st) t.setTextColor(st);
            }
        }
        if (group == null) return;
        int ix = netDx.get(), iy = netDy.get();
        int dx = netDx.value(StatusBarFeatures.NET_NUDGE_X_DP[ix > 0 && ix < StatusBarFeatures.NET_NUDGE_X_DP.length ? ix : 0]);
        int dy = netDy.value(StatusBarFeatures.NET_NUDGE_Y_DP[iy > 0 && iy < StatusBarFeatures.NET_NUDGE_Y_DP.length ? iy : 0]);
        if (dx == 0 && dy == 0 && netMoved.remove(group) == null) return;
        float d = group.getResources().getDisplayMetrics().density;
        group.setTranslationX(dx * d);
        group.setTranslationY(dy * d);
        if (dx != 0 || dy != 0) netMoved.put(group, Boolean.TRUE);
    }

    private static void styleNetView(Object view) {
        int style = netStyle.get();
        int pct = StatusBarSizeHooks.netPercent();   // "Network speed text size"
        if (style <= 0 && pct == 100) return;
        TextView text = (TextView) XposedHelpers.getObjectField(view, "mSpeedText");
        TextView unit = (TextView) XposedHelpers.getObjectField(view, "mSpeedUnit");
        View group = (View) XposedHelpers.getObjectField(view, "mSpeedViewGroup");
        if (text == null || unit == null || group == null) { Lx.w("StatusBar: net speed text views missing"); return; }
        if (style > 0) {
            float sp = style == 2 ? 7f : 6f;
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
            unit.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        }
        for (View v : new View[]{group, text, unit}) {
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp != null && lp.width != ViewGroup.LayoutParams.WRAP_CONTENT) { lp.width = ViewGroup.LayoutParams.WRAP_CONTENT; v.setLayoutParams(lp); }
        }
        if (pct != 100) { StatusBarSizeHooks.scaleText(text, pct); StatusBarSizeHooks.scaleText(unit, pct); }
    }

    private static synchronized void sample() {
        long now = SystemClock.elapsedRealtime();
        long tx = TrafficStats.getTotalTxBytes(), rx = TrafficStats.getTotalRxBytes();
        if (lastAt > 0 && now > lastAt) {
            double dt = (now - lastAt) / 1000.0;
            if (dt >= 0.5) {
                txRate = Math.max(0, (tx - lastTx) / dt);
                rxRate = Math.max(0, (rx - lastRx) / dt);
                lastTx = tx; lastRx = rx; lastAt = now;
            }
        } else { lastTx = tx; lastRx = rx; lastAt = now; }
    }

    /** "123B/s", "12.3K/s", "1.2M/s" — at most 4 significant characters so two rows stay narrow. */
    static String rate(double bytesPerSec) {
        String[] u = {"B", "K", "M", "G"};
        double v = bytesPerSec; int i = 0;
        while (v >= 1000 && i < u.length - 1) { v /= 1024; i++; }
        String s = v >= 100 || i == 0 ? String.format(Locale.US, "%.0f", v) : v >= 10 ? String.format(Locale.US, "%.1f", v) : String.format(Locale.US, "%.2f", v);
        return s + u[i] + "/s";
    }

    // ------------------------------------------------------------------ hide status icons

    private static long slotsAt; private static Set<String> hiddenSlots = Collections.emptySet();
    private static boolean hideWifi, hideMobile;

    private static void refreshSlots() {
        long now = SystemClock.uptimeMillis();
        if (now - slotsAt < 2000) return;
        HashSet<String> s = new HashSet<>();
        if (Cfg.on(StatusBarFeatures.SB_HIDE_VPN)) s.add("vpn");
        if (Cfg.on(StatusBarFeatures.SB_HIDE_NFC)) s.add("nfc");
        if (Cfg.on(StatusBarFeatures.SB_HIDE_ALARM)) s.add("alarm_clock");
        if (Cfg.on(StatusBarFeatures.SB_HIDE_VIBRATE)) { s.add("volume"); s.add("zen"); }
        if (Cfg.on(StatusBarFeatures.SB_HIDE_HEADSET)) s.add("headset");
        if (Cfg.on(StatusBarFeatures.SB_HIDE_HD)) { s.add("ims_icon"); s.add("hd"); s.add("volte"); s.add("vowifi"); }
        if (Cfg.on(StatusBarFeatures.SB_HIDE_MOBILE)) s.add("mobile");
        if (Cfg.on(StatusBarFeatures.SB_HIDE_WIFI)) s.add("wifi");
        if (Cfg.on(StatusBarFeatures.SB_HIDE_BLUETOOTH)) s.add("bluetooth");
        if (Cfg.on(StatusBarFeatures.SB_HIDE_HOTSPOT)) s.add("hotspot");
        if (Cfg.on(StatusBarFeatures.SB_HIDE_LOCATION)) s.add("location");
        if (Cfg.on(StatusBarFeatures.SB_HIDE_CAST)) { s.add("cast"); s.add("connected_display"); }
        if (Cfg.on(StatusBarFeatures.SB_HIDE_ROTATE)) s.add("rotate");
        if (Cfg.on(StatusBarFeatures.SB_HIDE_DATA_SAVER)) s.add("data_saver");
        if (Cfg.on(StatusBarFeatures.SB_HIDE_ETHERNET)) s.add("ethernet");
        if (Cfg.on(StatusBarFeatures.SB_HIDE_IME)) s.add("ime");
        if (Cfg.on(StatusBarFeatures.SB_HIDE_WORK)) s.add("managed_profile");
        if (Cfg.on(StatusBarFeatures.SB_HIDE_PRIVACY)) { s.add("camera"); s.add("microphone"); s.add("sensors_off"); s.add("location_privacy"); }
        hideWifi = Cfg.on(StatusBarFeatures.SB_HIDE_WIFI);
        hideMobile = Cfg.on(StatusBarFeatures.SB_HIDE_MOBILE);
        hiddenSlots = s;
        slotsAt = now;
    }

    private static void hideIcons(ClassLoader cl) {
        // StatusIconContainer.calculateIconTranslations (:218) hides a child when isIconBlocked() || !isIconVisible().
        Class<?> siv = H.cls(cl, "com.android.systemui.statusbar.StatusBarIconView");
        if (siv != null) {
            // public boolean isIconBlocked()  — StatusBarIconView.java:391 ; "public String mSlot" (:97)
            H.hook(H.find(siv, "isIconBlocked"), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        refreshSlots();
                        if (hiddenSlots.isEmpty()) return;
                        Object slot = XposedHelpers.getObjectField(p.thisObject, "mSlot");
                        if (slot != null && hiddenSlots.contains(slot)) p.setResult(true);
                    } catch (Throwable ignored) { }
                }
            });
        }
        // Wi-Fi / cellular use the new pipeline views: ModernStatusBarWifiView / ModernStatusBarMobileView extend
        // ModernStatusBarView, "public boolean isIconVisible()" — ModernStatusBarView.java:95
        Class<?> modern = H.cls(cl, "com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView");
        if (modern != null) {
            H.hook(H.find(modern, "isIconVisible"), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        refreshSlots();
                        if (!hideWifi && !hideMobile) return;
                        String n = p.thisObject.getClass().getName();
                        if ((hideWifi && n.contains("Wifi")) || (hideMobile && n.contains("Mobile"))) p.setResult(false);
                    } catch (Throwable ignored) { }
                }
            });
        }
    }

    // ------------------------------------------------------------------ notification icons

    private static int notifLimit() {
        int i = Cfg.choice(StatusBarFeatures.SB_NOTIF_MAX);
        return i > 0 && i < StatusBarFeatures.NOTIF_MAX_VALUES.length ? StatusBarFeatures.NOTIF_MAX_VALUES[i] : 0;
    }

    private static void notificationIcons(ClassLoader cl) {
        Class<?> nic = H.cls(cl, "com.android.systemui.statusbar.phone.NotificationIconContainer");
        if (nic == null) return;
        // public void setMaxIconsAmount(int i)  — NotificationIconContainer.java:693 (called by the view binder with the status bar limit)
        H.hook(H.find(nic, "setMaxIconsAmount", int.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                int n = notifLimit();
                if (n > 0 && p.args[0] instanceof Integer && (Integer) p.args[0] < n) p.args[0] = n;
            }
        });
        // public boolean shouldForceOverflow(int i, int i2, float f, int i3)  — :713 (i3 = max icons)
        H.hook(H.find(nic, "shouldForceOverflow", int.class, int.class, float.class, int.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                int n = notifLimit();
                if (n > 0 && p.args[3] instanceof Integer && (Integer) p.args[3] < n) p.args[3] = n;
            }
        });
        // public final void initResources()  — :495 ; fields mMaxIconsOnAod / mMaxIconsOnLockscreen / mMaxStaticIcons (:58-60)
        H.hook(H.find(nic, "initResources"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                int n = notifLimit();
                if (n <= 0) return;
                for (String f : new String[]{"mMaxStaticIcons", "mMaxIconsOnLockscreen", "mMaxIconsOnAod"}) {
                    try { if (XposedHelpers.getIntField(p.thisObject, f) < n) XposedHelpers.setIntField(p.thisObject, f, n); }
                    catch (Throwable t) { Lx.w("StatusBar: " + f + " missing: " + t); }
                }
            }
        });
    }

    // ------------------------------------------------------------------ quick settings

    private static int qsCols() { int i = Cfg.choice(StatusBarFeatures.SB_QS_COLS); return i > 0 && i < StatusBarFeatures.QS_COLS_VALUES.length ? StatusBarFeatures.QS_COLS_VALUES[i] : 0; }
    private static int qsRows() { int i = Cfg.choice(StatusBarFeatures.SB_QS_ROWS); return i > 0 && i < StatusBarFeatures.QS_ROWS_VALUES.length ? StatusBarFeatures.QS_ROWS_VALUES[i] : 0; }

    private static void quickSettings(ClassLoader cl) {
        XC_MethodHook cols = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { int n = qsCols(); if (n > 0) p.setResult(n); }
        };
        XC_MethodHook rows = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { int n = qsRows(); if (n > 0) p.setResult(n); }
        };
        // public final int getTileColumns()  — QsDimenUtils.java:177 (Companion)
        Class<?> dimen = H.cls(cl, "com.zte.utils.QsDimenUtils$Companion");
        if (dimen != null) H.hook(H.find(dimen, "getTileColumns"), cols);
        // public final int getColumns() { return QsDimenUtils.INSTANCE.getTileColumns(); }  — QsCustomizerModule.java:228 (edit screen)
        Class<?> customizer = H.cls(cl, "com.zte.feature.qs.layout.QsCustomizerModule");
        if (customizer != null) H.hook(H.find(customizer, "getColumns"), cols);
        // MfvTileLayoutAdapt: public final int getTileColumns(int src) (:84) feeds mResourceColumns, public final int getRows(int src) (:80) feeds mMaxAllowedRows
        Class<?> adapt = H.cls(cl, "com.zte.adapt.mifavor.qs.MfvTileLayoutAdapt");
        if (adapt != null) {
            H.hook(H.find(adapt, "getTileColumns", int.class), cols);
            H.hook(H.find(adapt, "getRows", int.class), rows);
        }
        // public boolean updateMaxRows(int i, int i2)  — MfvTileLayout.java:230 ; fields mRows (:33), mColumns (:25); i2 = tile count
        Class<?> tiles = H.cls(cl, "com.zte.mifavor.qs.MfvTileLayout");
        if (tiles != null) {
            H.hook(H.find(tiles, "updateMaxRows", int.class, int.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    int n = qsRows();
                    if (n <= 0) return;
                    try {
                        int columns = Math.max(1, XposedHelpers.getIntField(p.thisObject, "mColumns"));
                        int tileCount = (Integer) p.args[1];
                        int want = Math.max(1, Math.min(n, (tileCount + columns - 1) / columns));
                        if (XposedHelpers.getIntField(p.thisObject, "mRows") != want) {
                            XposedHelpers.setIntField(p.thisObject, "mRows", want);
                            p.setResult(true);
                        }
                    } catch (Throwable t) { Lx.e("StatusBar: QS rows", t); }
                }
            });
        }
        Class<?> header = H.cls(cl, "com.zte.controlcenter.widget.CCHeaderView");
        if (header != null) {
            // public final boolean shouldQsCarrierVisible()  — CCHeaderView.java:765 (operator flags only on stock)
            H.hook(H.find(header, "shouldQsCarrierVisible"), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { if (Cfg.on(StatusBarFeatures.SB_QS_CARRIER)) p.setResult(true); }
            });
            // public final boolean showSearchButton()  — :828 (false when the search app is missing / abroad / power save)
            H.hook(H.find(header, "showSearchButton"), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { if (Cfg.on(StatusBarFeatures.SB_QS_SEARCH)) p.setResult(true); }
            });
        }
    }

    // ------------------------------------------------------------------ notification app icon

    private static void notificationAppIcon(ClassLoader cl) {
        Class<?> util = H.cls(cl, "com.zte.feature.notification.NotificationUtil");
        if (util == null) return;
        // public static boolean shouldShowAppIcon(ApplicationInfo)  — NotificationUtil.java:136 (true = replace the small icon with the launcher icon)
        H.hook(H.find(util, "shouldShowAppIcon", ApplicationInfo.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { if (Cfg.on(StatusBarFeatures.SB_NOTIF_NATIVE_ICON)) p.setResult(false); }
        });
    }

    // ------------------------------------------------------------------ dialogs & misc

    private static void dialogs(ClassLoader cl) {
        Class<?> vd = H.cls(cl, "com.android.systemui.volume.VolumeDialogImpl");
        if (vd != null) {
            // public final void showSafetyWarningH(int i)  — VolumeDialogImpl.java:2370
            H.hook(H.find(vd, "showSafetyWarningH", int.class), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) { if (Cfg.on(StatusBarFeatures.SB_VOL_NO_WARNING)) p.setResult(null); }
            });
        }
        Class<?> vda = H.cls(cl, "com.zte.adapt.mifavor.volume.VolumeDialogImplAdapt");
        if (vda != null) {
            // public void richTapVibrateForVolumeKeyLongPress()  — VolumeDialogImplAdapt.java:332
            H.hook(H.find(vda, "richTapVibrateForVolumeKeyLongPress"), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) { if (Cfg.on(StatusBarFeatures.SB_VOL_NO_VIBRATE)) p.setResult(null); }
            });
        }
        Class<?> clip = H.cls(cl, "com.android.systemui.clipboardoverlay.ClipboardListener");
        if (clip != null) {
            // public static boolean forceSuppressOverlay()  — ClipboardListener.java:66 (returns true on the domestic flavour)
            H.hook(H.find(clip, "forceSuppressOverlay"), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) { if (Cfg.on(StatusBarFeatures.SB_CLIPBOARD_OVERLAY)) p.setResult(false); }
            });
        }
        Class<?> usb = H.cls(cl, "com.android.systemui.usb.UsbDebuggingActivity");
        if (usb != null) {
            // public void onCreate(Bundle)  — UsbDebuggingActivity.java:99 ; "public CheckBox mAlwaysAllow" (:35), "public final void notifyService(boolean, boolean)" (:73)
            H.hook(H.find(usb, "onCreate", Bundle.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!Cfg.on(StatusBarFeatures.SB_USB_AUTO_ALLOW)) return;
                    try {
                        android.app.Activity a = (android.app.Activity) p.thisObject;
                        Object box = XposedHelpers.getObjectField(a, "mAlwaysAllow");
                        if (a.isFinishing() || !(box instanceof CheckBox)) return;
                        ((CheckBox) box).setChecked(true);
                        XposedHelpers.callMethod(a, "notifyService", true, true);
                        a.finish();
                        Lx.i("StatusBar: USB debugging auto-allowed (always)");
                    } catch (Throwable t) { Lx.e("StatusBar: USB debugging auto-allow", t); }
                }
            });
        }
        Class<?> oneHand = H.cls(cl, "com.android.wm.shell.onehanded.OneHandedTutorialHandler");
        if (oneHand != null) {
            // public void createViewAndAttachToWindow(Context)  — OneHandedTutorialHandler.java:80 ; "mTutorialView" is the inflated hint panel (:84)
            H.hook(H.find(oneHand, "createViewAndAttachToWindow", Context.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!Cfg.on(StatusBarFeatures.SB_ONEHAND_NO_HINT)) return;
                    try {
                        Object v = XposedHelpers.getObjectField(p.thisObject, "mTutorialView");
                        if (v instanceof View) ((View) v).setVisibility(View.INVISIBLE);
                    } catch (Throwable t) { Lx.e("StatusBar: one-handed hint", t); }
                }
            });
        }
    }
}
