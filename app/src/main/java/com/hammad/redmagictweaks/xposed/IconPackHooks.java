package com.hammad.redmagictweaks.xposed;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.hammad.redmagictweaks.ClockFeatures;
import com.hammad.redmagictweaks.CosmeticsFeatures;
import com.hammad.redmagictweaks.Features;
import com.hammad.redmagictweaks.IconPackFeatures;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import android.content.res.XModuleResources;
import de.robv.android.xposed.XposedHelpers;

/**
 * com.android.systemui — status bar / Quick Settings icon looks, ported from nubia-ly's SystemIconPackBackend
 * (draw-only icon pack substitution), ConnectivityBackend (Wi-Fi / signal artwork) and StatusBarBackend
 * (icon tint, scale, opacity, offsets, hiding). Native drawables and models are never mutated: artwork is
 * swapped at draw time in ImageView.onDraw, and group transforms are applied to the stock icon containers
 * (StatusIconContainer / NotificationIconContainer / BatteryMeterView) so the per-icon ViewState animations
 * of System UI keep working. Settings are re-checked on every status bar clock tick (ClockHooks.tick).
 *
 * Targets checked against the 11.0.11 decompile:
 *   StatusBarIconView.onDarkChanged(ArrayList,float,int) (:448), isNotification() (:401), updateDrawable(boolean) (:782),
 *     fields mIcon (StatusBarIcon, :74), mCurrentSetColor (:65); StatusBarIconViewAdapt.donotSetIconColor() (:21)
 *   ModernStatusBarView.onDarkChanged(ArrayList,float,int) (:100); ModernStatusBarWifiView / ModernStatusBarMobileView
 *   IconViewBinder.bind(Icon, ImageView) (:14); Icon$Resource.getRes() (:110)
 *   QSIconViewImpl.updateIcon(ImageView, QSTile$State, boolean) (:294), fields mLastIcon (:45), mTint (:48)
 *   QSTileImpl$ResourceIcon.mResId (:280), QSTileImpl$DrawableIconWithRes.mId (:154)
 *   SoundVibrateTile.handleUpdateState(BooleanState, Object) (:204), field mCurrentMode (:46)
 *   SignalDrawable.mForegroundPaint (:42); PhoneStatusBarView.onFinishInflate() (:140)
 *   RedMagicFunctionIconFeature$CoolingFanIcon.updateCoolingFansState / $LiquidCoolingIcon.updateLiquidCoolingState /
 *     $RefreshRateIcon.updateRefreshRate, outer fields coolingFanView / liquidCoolingView / rateRefreshView
 */
final class IconPackHooks {
    private IconPackHooks() {}

    private static final String ROOT = "com.android.systemui.statusbar.phone.PhoneStatusBarView";
    private static final String ICON = "com.android.systemui.statusbar.StatusBarIconView";
    private static final String MODERN = "com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView";
    private static final String MODERN_WIFI = "com.android.systemui.statusbar.pipeline.wifi.ui.view.ModernStatusBarWifiView";
    private static final String MODERN_MOBILE = "com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView";
    private static final String STATUS_ICONS = "com.android.systemui.statusbar.phone.StatusIconContainer";
    private static final String NOTIF_ICONS = "com.android.systemui.statusbar.phone.NotificationIconContainer";
    private static final String BATTERY = "com.android.systemui.battery.BatteryMeterView";
    private static final String QS_ICON = "com.android.systemui.qs.tileimpl.QSIconViewImpl";
    private static final String SIGNAL_DRAWABLE = "com.android.settingslib.graph.SignalDrawable";
    private static final String RM_FEATURE = "com.zte.feature.statusbar.RedMagicFunctionIconFeature";

    // ------------------------------------------------------------------------------------------- settings snapshot

    static final class S {
        int pack, wifi, signal, color, scale, opacity, offX, offY, notifScale, notifOpacity, batteryScale;
        boolean hideSystem, hideNotif, hideFan, hideLiquid, hideRefresh, hideCharge;
        int hash;
        boolean draws() { return pack > 0 || wifi > 0 || signal > 0; }
    }
    private static volatile S snap = new S();
    private static volatile long snapAt;

    static S s() {
        long now = System.currentTimeMillis();
        if (now - snapAt > 1000) {
            S n = new S();
            n.pack = idx(Cfg.choice(IconPackFeatures.SBI_PACK), IconPackFeatures.PACKS.length);
            n.wifi = idx(Cfg.choice(IconPackFeatures.SBI_WIFI), IconPackFeatures.CONNECTIVITY.length);
            n.signal = idx(Cfg.choice(IconPackFeatures.SBI_SIGNAL), IconPackFeatures.CONNECTIVITY.length);
            n.color = idx(Cfg.choice(IconPackFeatures.SBI_COLOR), ClockFeatures.COLOR_VALUES.length);
            n.scale = idx(Cfg.choice(IconPackFeatures.SBI_SCALE), IconPackFeatures.SCALE_PERCENT.length);
            n.opacity = idx(Cfg.choice(IconPackFeatures.SBI_OPACITY), IconPackFeatures.OPACITY_PERCENT.length);
            n.offX = idx(Cfg.choice(IconPackFeatures.SBI_OFFSET_X), ClockFeatures.OFFSETS_X_DP.length);
            n.offY = idx(Cfg.choice(IconPackFeatures.SBI_OFFSET_Y), ClockFeatures.OFFSETS_Y_DP.length);
            n.notifScale = idx(Cfg.choice(IconPackFeatures.SBI_NOTIF_SCALE), IconPackFeatures.SCALE_PERCENT.length);
            n.notifOpacity = idx(Cfg.choice(IconPackFeatures.SBI_NOTIF_OPACITY), IconPackFeatures.OPACITY_PERCENT.length);
            n.batteryScale = idx(Cfg.choice(CosmeticsFeatures.COS_BATTERY_SCALE), IconPackFeatures.SCALE_PERCENT.length);
            n.hideSystem = Cfg.on(IconPackFeatures.SBI_HIDE_SYSTEM);
            n.hideNotif = Cfg.on(IconPackFeatures.SBI_HIDE_NOTIF);
            n.hideFan = Cfg.on(IconPackFeatures.SBI_HIDE_FAN);
            n.hideLiquid = Cfg.on(IconPackFeatures.SBI_HIDE_LIQUID);
            n.hideRefresh = Cfg.on(IconPackFeatures.SBI_HIDE_REFRESH);
            n.hideCharge = Cfg.on(CosmeticsFeatures.COS_BATTERY_HIDE_CHARGE);
            int h = n.pack; h = h * 31 + n.wifi; h = h * 31 + n.signal; h = h * 31 + n.color; h = h * 31 + n.scale; h = h * 31 + n.opacity;
            h = h * 31 + n.offX; h = h * 31 + n.offY; h = h * 31 + n.notifScale; h = h * 31 + n.notifOpacity; h = h * 31 + n.batteryScale;
            h = h * 31 + (n.hideSystem ? 1 : 0) + (n.hideNotif ? 2 : 0) + (n.hideFan ? 4 : 0) + (n.hideLiquid ? 8 : 0) + (n.hideRefresh ? 16 : 0) + (n.hideCharge ? 32 : 0);
            n.hash = h;
            snap = n; snapAt = now;
        }
        return snap;
    }

    private static int idx(int v, int len) { return v > 0 && v < len ? v : 0; }
    private static boolean main() { return Looper.myLooper() == Looper.getMainLooper(); }

    // ------------------------------------------------------------------------------------------- module artwork

    private static Resources modRes;
    private static final Map<String, Integer> artIds = new HashMap<>();
    private static final Map<String, Boolean> artMissing = new HashMap<>();

    private static synchronized Resources res() {
        if (modRes != null) return modRes;
        try { if (Entry.modulePath != null) modRes = XModuleResources.createInstance(Entry.modulePath, null); } catch (Throwable t) { Lx.w("Icons: module resources via path failed: " + t); }
        if (modRes == null) {
            try { Context c = Lx.context(); if (c != null) modRes = c.getPackageManager().getResourcesForApplication(Features.PKG); } catch (Throwable t) { Lx.w("Icons: module resources via PackageManager failed: " + t); }
        }
        return modRes;
    }

    /** A fresh, mutated drawable from our APK by name, or null (missing names are logged once). */
    static synchronized Drawable art(String name) {
        Resources r = res();
        if (r == null || name == null) return null;
        try {
            Integer id = artIds.get(name);
            if (id == null) { id = r.getIdentifier(name, "drawable", Features.PKG); artIds.put(name, id); }
            if (id == 0) { if (artMissing.put(name, Boolean.TRUE) == null && artMissing.size() < 40) Lx.w("Icons: no artwork " + name); return null; }
            Drawable d = r.getDrawable(id, null);
            return d == null ? null : d.mutate();
        } catch (Throwable t) { return null; }
    }

    static Drawable packArt(int pack, String key) {
        if (pack <= 0 || key == null) return null;
        if (pack != IconPackFeatures.PACK_ONE_UI8 && IconPackFeatures.oneUi8Only(key)) return null;
        return art(IconPackFeatures.packArtName(pack, key));
    }
    static Drawable connectivityArt(boolean wifi, int style, int level) {
        return style <= 0 || level < 0 || level > 4 ? null : art("rising_" + (wifi ? "wifi" : "signal") + "_" + style + "_" + level);
    }

    // ------------------------------------------------------------------------------------------- install

    static void install(ClassLoader cl) {
        try { statusBar(cl); } catch (Throwable t) { Lx.e("Icons: status bar", t); }
        try { tint(cl); } catch (Throwable t) { Lx.e("Icons: tint", t); }
        try { bindings(cl); } catch (Throwable t) { Lx.e("Icons: bindings", t); }
        try { draw(cl); } catch (Throwable t) { Lx.e("Icons: draw", t); }
        try { vendorIndicators(cl); } catch (Throwable t) { Lx.e("Icons: vendor indicators", t); }
        Lx.i("Icons: hooks ready");
    }

    // ------------------------------------------------------------------------------------------- bar containers

    private static final class Bar {
        WeakReference<View> system, notif, battery;
        int hash = Integer.MIN_VALUE; int colorWas; boolean chargeWas, scanned;
    }
    private static final Map<ViewGroup, Bar> bars = new WeakHashMap<>();
    /** stock {scaleX, scaleY, alpha, translationX, translationY, visibility} of a container we changed */
    private static final Map<View, float[]> stock = new WeakHashMap<>();

    private static ViewGroup barRoot(View v) {
        View cur = v;
        for (int i = 0; i < 24 && cur != null; i++) {
            if (cur.getClass().getName().equals(ROOT) && cur instanceof ViewGroup) return (ViewGroup) cur;
            cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
        }
        return null;
    }

    private static void statusBar(ClassLoader cl) {
        Class<?> root = H.cls(cl, ROOT);
        if (root == null) return;
        H.hook(H.find(root, "onFinishInflate"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                final ViewGroup bar = (ViewGroup) p.thisObject;
                bar.post(() -> { try { style(bar, s(), true); } catch (Throwable t) { Lx.e("Icons: style bar", t); } });
            }
        });
    }

    /** Called from ClockHooks on every status bar clock tick: re-style when a setting changed. */
    static void tick(View clock) {
        try {
            ViewGroup root = barRoot(clock);
            if (root == null) return;
            S s = s();
            Bar b;
            synchronized (bars) { b = bars.get(root); if (b == null) { b = new Bar(); bars.put(root, b); } }
            boolean changed = b.hash != s.hash;
            if (changed || !b.scanned) style(root, s, changed);
            if (b.colorWas > 0 && s.color == 0) untint(root, clock);
            if (b.chargeWas != s.hideCharge) CosmeticsHooks.refreshChargeIndicators(s.hideCharge);
            if (changed) { vendorRefresh(s); invalidateAll(root); }
            b.colorWas = s.color; b.chargeWas = s.hideCharge; b.hash = s.hash;
        } catch (Throwable t) { Lx.e("Icons: tick", t); }
    }

    private static void style(ViewGroup root, S s, boolean force) {
        Bar b;
        synchronized (bars) { b = bars.get(root); if (b == null) { b = new Bar(); bars.put(root, b); } }
        boolean stale = b.system == null || b.system.get() == null || b.notif == null || b.notif.get() == null || b.battery == null || b.battery.get() == null;
        if (stale && (!b.scanned || force)) {
            b.system = b.notif = b.battery = null;
            ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root); int count = 0;
            while (!queue.isEmpty() && count++ < 600) {
                View v = queue.removeFirst();
                String n = v.getClass().getName();
                if (n.equals(STATUS_ICONS) && b.system == null) b.system = new WeakReference<>(v);
                else if (n.equals(NOTIF_ICONS) && b.notif == null) b.notif = new WeakReference<>(v);
                else if (n.equals(BATTERY) && b.battery == null) b.battery = new WeakReference<>(v);
                if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) queue.addLast(g.getChildAt(i)); }
            }
            if (!b.scanned) {
                if (b.system == null) Lx.w("Icons: StatusIconContainer not found in the status bar");
                if (b.notif == null) Lx.w("Icons: NotificationIconContainer not found in the status bar");
                if (b.battery == null) Lx.w("Icons: BatteryMeterView not found in the status bar");
            }
            b.scanned = true;
        }
        float density = root.getResources().getDisplayMetrics().density;
        View sys = b.system == null ? null : b.system.get();
        if (sys != null) transform(sys, IconPackFeatures.SCALE_PERCENT[s.scale], IconPackFeatures.OPACITY_PERCENT[s.opacity],
                ClockFeatures.OFFSETS_X_DP[s.offX] * density, ClockFeatures.OFFSETS_Y_DP[s.offY] * density, s.hideSystem);
        View notif = b.notif == null ? null : b.notif.get();
        if (notif != null) transform(notif, IconPackFeatures.SCALE_PERCENT[s.notifScale], IconPackFeatures.OPACITY_PERCENT[s.notifOpacity], 0, 0, s.hideNotif);
        View battery = b.battery == null ? null : b.battery.get();
        if (battery != null) transform(battery, IconPackFeatures.SCALE_PERCENT[s.batteryScale], 100, 0, 0, false);
        if (force) b.hash = s.hash;
    }

    /** Scale / alpha / translation / visibility on top of the recorded stock values; all stock → restore. */
    private static void transform(View v, int scale, int opacity, float dx, float dy, boolean hide) {
        boolean any = scale != 100 || opacity != 100 || dx != 0 || dy != 0 || hide;
        float[] st;
        synchronized (stock) { st = stock.get(v); }
        if (!any) {
            if (st == null) return;
            v.setScaleX(st[0]); v.setScaleY(st[1]); v.setAlpha(st[2]); v.setTranslationX(st[3]); v.setTranslationY(st[4]);
            if (st[5] != View.GONE || v.getVisibility() == View.GONE) v.setVisibility((int) st[5]);
            synchronized (stock) { stock.remove(v); }
            return;
        }
        if (st == null) {
            st = new float[]{v.getScaleX(), v.getScaleY(), v.getAlpha(), v.getTranslationX(), v.getTranslationY(), v.getVisibility()};
            synchronized (stock) { stock.put(v, st); }
        }
        float sx = st[0] * scale / 100f, sy = st[1] * scale / 100f, a = st[2] * opacity / 100f;
        if (Math.abs(v.getScaleX() - sx) > 0.001f) v.setScaleX(sx);
        if (Math.abs(v.getScaleY() - sy) > 0.001f) v.setScaleY(sy);
        if (Math.abs(v.getAlpha() - a) > 0.001f) v.setAlpha(a);
        if (Math.abs(v.getTranslationX() - st[3] - dx) > 0.01f) v.setTranslationX(st[3] + dx);
        if (Math.abs(v.getTranslationY() - st[4] - dy) > 0.01f) v.setTranslationY(st[4] + dy);
        if (hide) { if (v.getVisibility() != View.GONE) v.setVisibility(View.GONE); }
        else if (v.getVisibility() == View.GONE && st[5] != View.GONE) v.setVisibility((int) st[5]);
    }

    private static void invalidateAll(ViewGroup root) {
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root); int count = 0;
        while (!queue.isEmpty() && count++ < 600) {
            View v = queue.removeFirst();
            if (v instanceof ImageView) v.invalidate();
            if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) queue.addLast(g.getChildAt(i)); }
        }
        synchronized (sources) { sources.clear(); }
        synchronized (artwork) { artwork.clear(); }
    }

    // ------------------------------------------------------------------------------------------- icon tint

    private static final Map<ImageView, Boolean> tinted = new WeakHashMap<>();

    private static void tint(ClassLoader cl) {
        Class<?> icon = H.cls(cl, ICON);
        if (icon != null) {
            H.hook(H.find(icon, "onDarkChanged", ArrayList.class, float.class, int.class), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (s().color <= 0) return;
                    try {
                        Object adapt = XposedHelpers.callMethod(p.thisObject, "getAdapt");
                        boolean accepts = Boolean.FALSE.equals(XposedHelpers.callMethod(adapt, "donotSetIconColor"));
                        if (accepts) p.setObjectExtra("rmt:accepts", Boolean.TRUE);
                    } catch (Throwable t) { p.setObjectExtra("rmt:accepts", Boolean.TRUE); }
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    S s = s();
                    if (s.color <= 0 || p.getObjectExtra("rmt:accepts") == null || p.hasThrowable()) return;
                    try {
                        ImageView v = (ImageView) p.thisObject;
                        if (Boolean.TRUE.equals(XposedHelpers.callMethod(v, "isNotification")) || barRoot(v) == null) return;
                        v.setImageTintList(ColorStateList.valueOf(ClockFeatures.COLOR_VALUES[s.color]));
                        synchronized (tinted) { tinted.put(v, Boolean.TRUE); }
                    } catch (Throwable t) { Lx.e("Icons: tint icon", t); }
                }
            });
        }
        Class<?> modern = H.cls(cl, MODERN);
        if (modern != null) {
            H.hook(H.find(modern, "onDarkChanged", ArrayList.class, float.class, int.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    S s = s();
                    if (s.color <= 0 || p.hasThrowable()) return;
                    try {
                        ViewGroup v = (ViewGroup) p.thisObject;
                        if (barRoot(v) == null) return;
                        tintChildren(v, ClockFeatures.COLOR_VALUES[s.color]);
                    } catch (Throwable t) { Lx.e("Icons: tint modern", t); }
                }
            });
        }
    }

    private static void tintChildren(ViewGroup g, int color) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c instanceof ImageView) { ((ImageView) c).setImageTintList(ColorStateList.valueOf(color)); synchronized (tinted) { tinted.put((ImageView) c, Boolean.TRUE); } }
            else if (c instanceof TextView) ((TextView) c).setTextColor(color);
            else if (c instanceof ViewGroup) tintChildren((ViewGroup) c, color);
        }
    }

    /** Colour switched back to stock: give every icon we coloured the clock's tint until the next real dark change. */
    private static void untint(ViewGroup root, View clock) {
        int tintColor = 0;
        try { tintColor = XposedHelpers.getIntField(clock, "mNonAdaptedColor"); } catch (Throwable ignored) { }
        if (tintColor == 0 && clock instanceof TextView) tintColor = ((TextView) clock).getCurrentTextColor();
        if (tintColor == 0) return;
        ArrayList<ImageView> views;
        synchronized (tinted) { views = new ArrayList<>(tinted.keySet()); tinted.clear(); }
        for (ImageView v : views) {
            try {
                if (barRoot(v) != root) continue;
                v.setImageTintList(ColorStateList.valueOf(tintColor));
                if (v.getParent() instanceof ViewGroup && !v.getClass().getName().equals(ICON)) {
                    ViewGroup g = (ViewGroup) v.getParent();
                    for (int i = 0; i < g.getChildCount(); i++) if (g.getChildAt(i) instanceof TextView) ((TextView) g.getChildAt(i)).setTextColor(tintColor);
                }
            } catch (Throwable ignored) { }
        }
    }

    // ------------------------------------------------------------------------------------------- bindings (what is drawn where)

    private static final class Source { final Drawable drawable; final String key; Source(Drawable d, String k) { drawable = d; key = k; } }
    private static final class Tile { final String spec; final boolean transientState; final String stateKey; Tile(String s, boolean t, String k) { spec = s; transientState = t; stateKey = k; } }
    private static final class ResState { final Drawable drawable; final int level; ResState(Drawable d, int l) { drawable = d; level = l; } }
    private static final class Art { final String key; final Drawable drawable; Art(String k, Drawable d) { key = k; drawable = d; } }
    private static final class Scope { final WeakReference<Object> parent; final int kind; Scope(Object p, int k) { parent = new WeakReference<>(p); kind = k; } }

    private static final Map<Object, String> soundModes = new WeakHashMap<>();
    private static final Map<ImageView, Tile> tiles = new WeakHashMap<>();
    private static final Map<Drawable, String> drawableKeys = new WeakHashMap<>();
    private static final Map<Drawable.ConstantState, String> stateKeys = new WeakHashMap<>();
    private static final Map<ImageView, Source> sources = new WeakHashMap<>();
    private static final Map<ImageView, Art> artwork = new WeakHashMap<>();
    private static final Map<ImageView, ResState> levels = new WeakHashMap<>();
    /** kind: 0 none, 1 wifi strength, 2 mobile strength, 3 icon-pack target */
    private static final Map<ImageView, Scope> scopes = new WeakHashMap<>();
    private static final Map<Integer, String> resourceKeys = new HashMap<>();
    private static final Map<Integer, String> idNames = new HashMap<>();
    private static Class<?> qsIconClass, resourceIconClass, drawableResourceIconClass, statusIconClass;

    private static String resourceKey(Resources res, int id) {
        try {
            synchronized (resourceKeys) { if (resourceKeys.containsKey(id)) return resourceKeys.get(id); }
            String pkg = res.getResourcePackageName(id);
            if (!pkg.equals("com.android.systemui") && !pkg.equals("android")) return null;
            String key = IconKeys.key(res.getResourceEntryName(id));
            synchronized (resourceKeys) { if (resourceKeys.size() < 4096) resourceKeys.put(id, key); }
            return key;
        } catch (Throwable t) { return null; }
    }

    private static String idName(View v) {
        int id = v.getId();
        if (id == View.NO_ID) return "";
        synchronized (idNames) { String n = idNames.get(id); if (n != null) return n; }
        String n = "";
        try { if (v.getResources().getResourcePackageName(id).equals("com.android.systemui")) n = v.getResources().getResourceEntryName(id); } catch (Throwable ignored) { }
        synchronized (idNames) { if (idNames.size() < 4096) idNames.put(id, n); }
        return n;
    }

    private static void remember(Drawable d, String key) {
        if (d == null || key == null) return;
        synchronized (drawableKeys) { drawableKeys.put(d, key); Drawable.ConstantState cs = d.getConstantState(); if (cs != null) stateKeys.put(cs, key); }
    }
    private static String rememberedKey(Drawable d) {
        if (d == null) return null;
        synchronized (drawableKeys) { String k = drawableKeys.get(d); return k != null ? k : stateKeys.get(d.getConstantState()); }
    }

    private static void bindings(ClassLoader cl) {
        qsIconClass = H.cls(cl, QS_ICON);
        resourceIconClass = H.cls(cl, "com.android.systemui.qs.tileimpl.QSTileImpl$ResourceIcon");
        drawableResourceIconClass = H.cls(cl, "com.android.systemui.qs.tileimpl.QSTileImpl$DrawableIconWithRes");
        statusIconClass = H.cls(cl, ICON);
        Class<?> state = H.cls(cl, "com.android.systemui.plugins.qs.QSTile$State");
        if (qsIconClass != null && state != null) {
            H.hook(H.find(qsIconClass, "updateIcon", ImageView.class, state, boolean.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!main() || p.hasThrowable()) return;
                    ImageView image = (ImageView) p.args[0];
                    try {
                        Object st = p.args[1];
                        String spec = (String) XposedHelpers.getObjectField(st, "spec");
                        String stateKey; synchronized (soundModes) { stateKey = soundModes.get(st); }
                        if ("rotation".equals(spec)) { try { stateKey = Boolean.TRUE.equals(XposedHelpers.getObjectField(st, "value")) ? "rotate" : "portrait"; } catch (Throwable ignored) { } }
                        boolean transientState = Boolean.TRUE.equals(XposedHelpers.getObjectField(st, "isTransient"));
                        synchronized (tiles) { tiles.put(image, new Tile(spec, transientState, stateKey)); }
                    } catch (Throwable t) { synchronized (tiles) { tiles.remove(image); } }
                    boundChanged(image);
                }
            });
        }
        if (statusIconClass != null) {
            H.hook(H.find(statusIconClass, "updateDrawable", boolean.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { if (main() && !p.hasThrowable()) boundChanged((ImageView) p.thisObject); }
            });
        }
        // Drawables System UI loads before we see their ImageView: remember id → key by identity / constant state.
        H.hook(H.find(Resources.class, "getDrawableForDensity", int.class, int.class, Resources.Theme.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (s().pack <= 0 || !main() || p.hasThrowable() || !(p.getResult() instanceof Drawable)) return;
                try { remember((Drawable) p.getResult(), resourceKey((Resources) p.thisObject, (Integer) p.args[0])); } catch (Throwable ignored) { }
            }
        });
        H.hook(H.find(ImageView.class, "setImageResource", int.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!main() || p.hasThrowable()) return;
                ImageView image = (ImageView) p.thisObject;
                try {
                    int resId = (Integer) p.args[0];
                    // strength levels are recorded even while everything is stock, so a style switched on later
                    // has a level for the icon that is already showing (nubia-ly records unconditionally too)
                    int kind = kind(image);
                    if (kind == 1 || kind == 2) { recordLevel(image, kind, resId); return; }
                    if (s().pack <= 0) return;
                    String k = resourceKey(image.getResources(), resId);
                    if (k != null) { remember(image.getDrawable(), k); synchronized (sources) { sources.remove(image); } }
                } catch (Throwable ignored) { }
            }
        });
        // New-pipeline Wi-Fi / mobile strength icons are bound through IconViewBinder.bind(Icon, ImageView).
        Class<?> binder = H.cls(cl, "com.android.systemui.common.ui.binder.IconViewBinder");
        Class<?> icon = H.cls(cl, "com.android.systemui.common.shared.model.Icon");
        final Class<?> resource = H.cls(cl, "com.android.systemui.common.shared.model.Icon$Resource");
        if (binder != null && icon != null && resource != null) {
            H.hook(H.find(binder, "bind", icon, ImageView.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!main() || p.hasThrowable() || !(p.args[1] instanceof ImageView)) return;
                    ImageView image = (ImageView) p.args[1];
                    try {
                        int kind = kind(image);
                        if (kind != 1 && kind != 2) return;
                        synchronized (levels) { levels.remove(image); }
                        if (p.args[0] != null && p.args[0].getClass() == resource) recordLevel(image, kind, ((Number) XposedHelpers.callMethod(p.args[0], "getRes")).intValue());
                    } catch (Throwable ignored) { }
                }
            });
        }
        Class<?> sound = XposedHelpers.findClassIfExists("com.zte.qs.tiles.SoundVibrateTile", cl);
        Class<?> booleanState = H.cls(cl, "com.android.systemui.plugins.qs.QSTile$BooleanState");
        if (sound != null && booleanState != null) {
            H.hook(H.find(sound, "handleUpdateState", booleanState, Object.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable()) return;
                    try {
                        int mode = XposedHelpers.getIntField(p.thisObject, "mCurrentMode");
                        synchronized (soundModes) { soundModes.put(p.args[0], mode == 0 ? "silent" : mode == 1 ? "vibrate" : "ring"); }
                    } catch (Throwable ignored) { }
                }
            });
        } else Lx.w("Icons: SoundVibrateTile not found — ringer tile keeps native artwork");
    }

    private static void boundChanged(ImageView image) {
        synchronized (sources) { sources.remove(image); }
        if (s().pack > 0 && image.isAttachedToWindow()) image.invalidate();
    }

    private static void recordLevel(ImageView image, int kind, int resId) {
        synchronized (levels) { levels.remove(image); }
        try {
            String name = image.getResources().getResourceEntryName(resId);
            int level = IconKeys.level(kind == 1, name);
            if (level < 0 && kind == 1) { int cut = IconKeys.wifiNoInternet(name); if (cut >= 0) level = NO_INTERNET + cut; }
            if (level >= 0) synchronized (levels) { levels.put(image, new ResState(image.getDrawable(), level)); }
            else if (fallbackLogged.size() < 8 && fallbackLogged.put(name, Boolean.TRUE) == null) Lx.w("Icons: strength resource without a level: " + name);
        } catch (Throwable ignored) { }
    }
    private static final Map<String, Boolean> fallbackLogged = new HashMap<>();
    /** ResState.level offset marking a Wi-Fi "connected, no internet" resource (only the One UI 8 pack draws those). */
    private static final int NO_INTERNET = 100;

    /** 1 = Wi-Fi strength icon, 2 = mobile strength icon (both only inside PhoneStatusBarView), 3 = icon-pack target, 0 = none. Cached per view and parent. */
    private static int kind(ImageView v) {
        Object parent = v.getParent();
        synchronized (scopes) { Scope sc = scopes.get(v); if (sc != null && sc.parent.get() == parent) return sc.kind; }
        int kind = 0;
        String id = idName(v);
        boolean strength = id.equals("wifi_signal") || id.equals("mobile_signal");
        boolean owner = false, qs = false, inBar = false, notif = false;
        View cur = v;
        for (int i = 0; i < 18 && cur != null; i++) {
            String n = cur.getClass().getName();
            if (strength && n.equals(id.equals("wifi_signal") ? MODERN_WIFI : MODERN_MOBILE)) owner = true;
            if (n.equals(NOTIF_ICONS)) notif = true;
            if (qsIconClass != null && qsIconClass.isInstance(cur)) qs = true;
            if (n.equals(ROOT)) { inBar = true; break; }
            cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
        }
        if (strength) kind = inBar && owner ? (id.equals("wifi_signal") ? 1 : 2) : 0;
        else if (!notif && (qs || inBar)) kind = 3;
        synchronized (scopes) { scopes.put(v, new Scope(parent, kind)); }
        return kind;
    }

    // ------------------------------------------------------------------------------------------- draw substitution

    private static void draw(ClassLoader cl) {
        H.hook(H.find(ImageView.class, "onDraw", Canvas.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                S s = s();
                if (!s.draws() || !main()) return;
                ImageView v = (ImageView) p.thisObject;
                if (!v.isAttachedToWindow()) return;
                Drawable nativeIcon = v.getDrawable();
                if (nativeIcon == null) return;
                int kind;
                try { kind = kind(v); } catch (Throwable t) { return; }
                if (kind == 0) return;
                try {
                    if (kind == 3) { if (s.pack > 0 && drawPack(v, nativeIcon, (Canvas) p.args[0], s)) p.setResult(null); }
                    else if (drawStrength(v, nativeIcon, (Canvas) p.args[0], kind, s)) p.setResult(null);
                } catch (Throwable t) {
                    synchronized (artwork) { artwork.remove(v); }   // isolated artwork failure keeps the native icon
                }
            }
        });
    }

    private static String boundKey(ImageView image) {
        try {
            if (statusIconClass != null && statusIconClass.isInstance(image)) {
                Object model = XposedHelpers.getObjectField(image, "mIcon");
                Object icon = model == null ? null : XposedHelpers.getObjectField(model, "icon");
                if (icon != null && ((Number) XposedHelpers.callMethod(icon, "getType")).intValue() == 2) {
                    String pkg = (String) XposedHelpers.callMethod(icon, "getResPackage");
                    if (!"com.android.systemui".equals(pkg) && !"android".equals(pkg)) return null;
                    return resourceKey(image.getResources(), ((Number) XposedHelpers.callMethod(icon, "getResId")).intValue());
                }
                return null;
            }
            View v = image;
            for (int i = 0; i < 8 && v != null; i++) {
                if (qsIconClass != null && qsIconClass.isInstance(v)) {
                    Object icon = XposedHelpers.getObjectField(v, "mLastIcon");
                    if (icon == null) return null;
                    int res;
                    if (resourceIconClass != null && resourceIconClass.isInstance(icon)) res = XposedHelpers.getIntField(icon, "mResId");
                    else if (drawableResourceIconClass != null && drawableResourceIconClass.isInstance(icon)) res = XposedHelpers.getIntField(icon, "mId");
                    else return null;
                    return resourceKey(image.getResources(), res);
                }
                v = v.getParent() instanceof View ? (View) v.getParent() : null;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static Integer boundColor(ImageView image) {
        try {
            if (statusIconClass != null && statusIconClass.isInstance(image)) { int c = XposedHelpers.getIntField(image, "mCurrentSetColor"); return c == 0 ? null : c; }
            View v = image;
            for (int i = 0; i < 8 && v != null; i++) {
                if (qsIconClass != null && qsIconClass.isInstance(v)) { int c = XposedHelpers.getIntField(v, "mTint"); return c == 0 ? null : c; }
                v = v.getParent() instanceof View ? (View) v.getParent() : null;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static boolean drawPack(ImageView image, Drawable nativeIcon, Canvas canvas, S s) {
        Source source;
        synchronized (sources) { source = sources.get(image); }
        if (source == null || source.drawable != nativeIcon) {
            String nativeKey = boundKey(image);
            if (nativeKey == null) nativeKey = rememberedKey(nativeIcon);
            Tile tile; synchronized (tiles) { tile = tiles.get(image); }
            String key = tile == null ? nativeKey : IconKeys.tileKey(tile.spec, tile.transientState, tile.stateKey != null ? tile.stateKey : nativeKey);
            source = new Source(nativeIcon, key);
            synchronized (sources) { sources.put(image, source); }
        }
        if (source.key == null) return false;
        Art cached; synchronized (artwork) { cached = artwork.get(image); }
        String want = s.pack + ":" + source.key;
        if (cached == null || !cached.key.equals(want)) {
            Drawable d = packArt(s.pack, source.key);
            if (d == null) return false;
            cached = new Art(want, d);
            synchronized (artwork) { artwork.put(image, cached); }
        }
        Drawable d = cached.drawable;
        d.setColorFilter(null);
        if (image.getImageTintList() != null) d.setTint(image.getImageTintList().getColorForState(image.getDrawableState(), image.getImageTintList().getDefaultColor()));
        else {
            ColorFilter filter = image.getColorFilter();
            if (filter == null) filter = nativeIcon.getColorFilter();
            if (filter == null) { Integer color = boundColor(image); if (color == null) return false; d.setTint(color); }
            else { d.setTintList(null); d.setColorFilter(filter); }
        }
        return paint(image, nativeIcon, d, canvas);
    }

    private static boolean drawStrength(ImageView v, Drawable nativeIcon, Canvas canvas, int kind, S s) {
        int style = kind == 1 ? s.wifi : s.signal;
        if (style == 0 && s.pack == 0) return false;
        int level; Integer color = null;
        boolean oneUi8 = style == 0 && s.pack == IconPackFeatures.PACK_ONE_UI8;
        String variant = kind == 1 ? "wifi_" : "signal_";
        if (kind == 2 && nativeIcon.getClass().getName().equals(SIGNAL_DRAWABLE)) {
            int packed = nativeIcon.getLevel();
            int state = packed >>> 16;   // SignalDrawable.getState: 2 = cut (no data / no service), 3 = carrier change
            if (state != 0 && !(state == 2 && oneUi8)) return false;
            if (state == 2) variant = "signal_cut_";
            int count = (packed >>> 8) & 255, raw = packed & 255;
            if (count < 2 || count > 6 || raw >= count) return false;
            level = Math.round(raw * 4f / (count - 1));
            try { Object fg = XposedHelpers.getObjectField(nativeIcon, "mForegroundPaint"); if (fg instanceof Paint) color = ((Paint) fg).getColor(); } catch (Throwable ignored) { }
        } else {
            ResState st; synchronized (levels) { st = levels.get(v); }
            if (st == null || st.drawable != nativeIcon) return false;
            level = st.level;
            if (level >= NO_INTERNET) {
                if (!oneUi8 || kind != 1) return false;   // other packs / styles keep the stock "no internet" artwork
                level -= NO_INTERNET; variant = "wifi_noinet_";
            }
        }
        Art cached; synchronized (artwork) { cached = artwork.get(v); }
        String want = (kind == 1 ? "w" : "m") + style + ":" + s.pack + ":" + variant + level;
        if (cached == null || !cached.key.equals(want)) {
            Drawable loaded = style == 0 ? packArt(s.pack, variant + level) : connectivityArt(kind == 1, style, level);
            if (loaded == null) return false;
            cached = new Art(want, loaded);
            synchronized (artwork) { artwork.put(v, cached); }
        }
        Drawable art = cached.drawable;
        if (v.getImageTintList() != null) color = v.getImageTintList().getColorForState(v.getDrawableState(), v.getImageTintList().getDefaultColor());
        ColorFilter filter = v.getColorFilter();
        if (color == null && filter == null) filter = nativeIcon.getColorFilter();
        if (color == null && filter == null) return false;
        art.setColorFilter(null);
        if (color != null) art.setTint(color); else { art.setTintList(null); art.setColorFilter(filter); }
        return paint(v, nativeIcon, art, canvas);
    }

    /** Draws our artwork exactly where ImageView would draw the native one (bounds, padding, image matrix, alpha). */
    private static boolean paint(ImageView image, Drawable nativeIcon, Drawable d, Canvas canvas) {
        Rect bounds = nativeIcon.getBounds();
        if (bounds.isEmpty()) return false;
        d.setBounds(bounds);
        d.setAlpha(image.getImageAlpha());
        d.setState(image.getDrawableState());
        d.setLayoutDirection(image.getLayoutDirection());
        int save = canvas.save();
        try {
            canvas.translate(image.getPaddingLeft(), image.getPaddingTop());
            if (image.getImageMatrix() != null) canvas.concat(image.getImageMatrix());
            d.draw(canvas);
        } finally { canvas.restoreToCount(save); }
        return true;
    }

    // ------------------------------------------------------------------------------------------- RedMagic fan / liquid / refresh-rate indicators

    private static final WeakReference<?>[] vendor = new WeakReference<?>[3];
    private static final String[] VENDOR_INNER = {"$CoolingFanIcon", "$LiquidCoolingIcon", "$RefreshRateIcon"};
    private static final String[] VENDOR_METHOD = {"updateCoolingFansState", "updateLiquidCoolingState", "updateRefreshRate"};
    private static final String[] VENDOR_FIELD = {"coolingFanView", "liquidCoolingView", "rateRefreshView"};

    private static boolean vendorHidden(S s, int i) { return i == 0 ? s.hideFan : i == 1 ? s.hideLiquid : s.hideRefresh; }

    private static void vendorIndicators(ClassLoader cl) {
        for (int i = 0; i < 3; i++) {
            final int which = i;
            Class<?> inner = H.cls(cl, RM_FEATURE + VENDOR_INNER[i]);
            if (inner == null) continue;
            H.hook(H.find(inner, VENDOR_METHOD[i]), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    vendor[which] = new WeakReference<>(p.thisObject);
                    if (!vendorHidden(s(), which)) return;
                    try {
                        Object outer = XposedHelpers.getSurroundingThis(p.thisObject);
                        Object v = XposedHelpers.getObjectField(outer, VENDOR_FIELD[which]);
                        if (v instanceof View && ((View) v).getVisibility() != View.GONE) ((View) v).setVisibility(View.GONE);
                    } catch (Throwable t) { Lx.e("Icons: hide " + VENDOR_FIELD[which], t); }
                }
            });
        }
    }

    /** Settings changed: let the vendor recompute each indicator (our after-hook hides it again if still wanted). */
    private static void vendorRefresh(S s) {
        for (int i = 0; i < 3; i++) {
            WeakReference<?> ref = vendor[i];
            Object inner = ref == null ? null : ref.get();
            if (inner == null) continue;
            try { XposedHelpers.callMethod(inner, VENDOR_METHOD[i]); } catch (Throwable t) { Lx.w("Icons: " + VENDOR_METHOD[i] + " failed: " + t); }
        }
    }
}
