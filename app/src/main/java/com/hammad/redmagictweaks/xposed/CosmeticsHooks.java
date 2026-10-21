package com.hammad.redmagictweaks.xposed;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;

import com.hammad.redmagictweaks.CosmeticsFeatures;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * com.android.systemui — "Look & feel" cosmetics ported from nubia-ly (BatteryStyleBackend, SurfaceBackend's
 * lock-clock font, QsShapeBackend, QsOutlineBackend, NotificationBackend, PowerMenuBackend, BrightnessBackend,
 * VolumeBackend). All rendering lives in CosmeticsArt; this class only finds the vendor views and swaps draws.
 * Draw-path hooks read a settings snapshot refreshed at most once per second (XSharedPreferences reloads are not free).
 */
final class CosmeticsHooks {
    private CosmeticsHooks() {}

    private static final String BATTERY_LAYOUT = "com.zte.mifavor.views.MFVBatteryViewLayout";
    private static final String BATTERY_ICON = "com.zte.mifavor.views.MFVBatteryMeterView";
    private static final String FONT_RES_UTIL = "com.zte.mifavor.keyguard.personalclock.FontResUtil";
    private static final String CLOCK_DEFAULT = "com.zte.mifavor.keyguard.settings.LockScreenClockDefault";
    private static final String QS_BASE = "com.zte.qs.tileimpl.QSTileViewBase";
    private static final String QS_IMPL = "com.android.systemui.qs.tileimpl.QSTileViewImpl";
    private static final String QS_WIDE = "com.zte.qs.tileimpl.QSTileViewWide";
    private static final String QS_CIRCLE = "com.zte.qs.tileimpl.QSTileViewCircle";
    private static final String NOTIF_BG = "com.android.systemui.statusbar.notification.row.NotificationBackgroundView";
    private static final String POWER_DIALOG = "com.zte.feature.globalaction.MfvGlobalActionsDialog$MFVActionsDialog";
    private static final String TOGGLE_SEEKBAR = "com.android.systemui.settings.brightness.ToggleSeekBar";
    private static final String VOLUME_DIALOG = "com.zte.feature.volume.MfvVolumeDialogImpl";
    private static final String VOLUME_ROW = "com.android.systemui.volume.VolumeDialogImpl$VolumeRow";
    private static final String VOLUME_SLIDER = "com.zte.mifavor.widget.ColorSeekBarZTE";

    // ------------------------------------------------------------------------------------------- settings snapshot

    static final class S {
        int battery, clockFont, qsShape, qsShapeLarge, qsStyle, notifStyle, notifOpacity, powerStyle, brightness, volume;
        boolean batteryPercent, batteryNoCharge, batteryStockReadable;
    }
    private static volatile S snap = new S();
    private static volatile long snapAt;

    static S s() {
        long now = System.currentTimeMillis();
        if (now - snapAt > 1000) {
            S n = new S();
            n.battery = Cfg.choice(CosmeticsFeatures.COS_BATTERY_STYLE);
            n.batteryPercent = Cfg.on(CosmeticsFeatures.COS_BATTERY_PERCENT);
            n.batteryNoCharge = Cfg.on(CosmeticsFeatures.COS_BATTERY_HIDE_CHARGE);
            n.batteryStockReadable = Cfg.on(CosmeticsFeatures.COS_BATTERY_STOCK_READABLE);
            n.clockFont = Cfg.choice(CosmeticsFeatures.COS_CLOCK_FONT);
            n.qsShape = Cfg.choice(CosmeticsFeatures.COS_QS_SHAPE);
            n.qsShapeLarge = Cfg.choice(CosmeticsFeatures.COS_QS_SHAPE_LARGE);
            n.qsStyle = Cfg.choice(CosmeticsFeatures.COS_QS_STYLE);
            n.notifStyle = Cfg.choice(CosmeticsFeatures.COS_NOTIF_STYLE);
            n.notifOpacity = Cfg.choice(CosmeticsFeatures.COS_NOTIF_OPACITY);
            n.powerStyle = Cfg.choice(CosmeticsFeatures.COS_POWER_STYLE);
            n.brightness = Cfg.choice(CosmeticsFeatures.COS_BRIGHTNESS_STYLE);
            n.volume = Cfg.choice(CosmeticsFeatures.COS_VOLUME_STYLE);
            snap = n; snapAt = now;
        }
        return snap;
    }

    private static float density(View v) {
        try { return v.getResources().getDisplayMetrics().density; } catch (Throwable t) { return 3f; }
    }

    static void install(ClassLoader cl) {
        try { battery(cl); } catch (Throwable t) { Lx.e("cosmetics: battery", t); }
        try { chargeIndicator(cl); } catch (Throwable t) { Lx.e("cosmetics: charge indicator", t); }
        try { clockFont(cl); } catch (Throwable t) { Lx.e("cosmetics: clock font", t); }
        // QS shapes/finish, notification/power/brightness/volume finishes moved to QsHooks / SurfaceHooks
        // (full nubia-ly port). The old installers below stay for reference but are no longer called,
        // because both would decorate the same views.
        Lx.i("Cosmetics: hooks ready");
    }

    // ============================================================================================= battery icon

    /**
     * Replaces the battery picture after each stock update (status bar, keyguard status bar and QS header
     * variants); level, charging state and the status-bar tint are read from the vendor views at draw time, so the
     * custom shape follows the light / dark icon tint exactly like the stock MFVBatteryMeterView.
     * MFVBatteryViewLayout: onDarkChanged (:541) -> mBatteryColors.mTint (:104) -> updateBatteryIconColor (:627)
     * -> MFVBatteryMeterView.setColorTint (:198) -> updateBattery (:217, hooked here).
     */
    private static void battery(ClassLoader cl) {
        final Class<?> icon = H.cls(cl, BATTERY_ICON);
        if (icon == null) return;
        H.hook(H.find(icon, "updateBattery"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                int style = s().battery;
                try {
                    ImageView view = (ImageView) p.thisObject;
                    if (!eligible(view)) return;
                    ViewGroup layout = layoutOf(view);
                    if (style <= 0 || !CosmeticsArt.batteryStyleValid(style)) {
                        // back to stock: give the percentage views back to the vendor layout
                        if (layout != null && hiddenFlags(layout) != 0) restoreInside(layout);
                        return;
                    }
                    if (XposedHelpers.getIntField(view, "mContentResId") == 0) return;
                    if (layout != null) syncInside(layout, style);
                    if (style == CosmeticsArt.BATTERY_HIDDEN) { view.setImageDrawable(null); view.setBackground(null); return; }
                    Drawable stock = view.getDrawable();
                    int w = stock == null ? -1 : stock.getIntrinsicWidth(), h = stock == null ? -1 : stock.getIntrinsicHeight();
                    view.setBackground(null);
                    view.setImageDrawable(new BatteryDrawable(view, w, h));
                } catch (Throwable t) { Lx.e("cosmetics: battery apply", t); }
            }
        });
        Class<?> layoutClass = H.cls(cl, BATTERY_LAYOUT);
        if (layoutClass != null) {
            // dispatchDraw (:316) punches the stock "hollow" percentage out of whatever the icon drew when
            // mTextHollowEffectAvailable (:95) is set; while a custom shape is on we draw the number ourselves.
            H.hook(H.find(layoutClass, "dispatchDraw", Canvas.class), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        S s = s();
                        boolean custom = s.battery > 0 && CosmeticsArt.batteryStyleValid(s.battery);
                        if (!custom && !s.batteryStockReadable) return;
                        ViewGroup layout = (ViewGroup) p.thisObject;
                        if (!XposedHelpers.getBooleanField(layout, "mTextHollowEffectAvailable")) return;
                        Object iconView = XposedHelpers.getObjectField(layout, "mBatteryIconView");
                        if (!(iconView instanceof ImageView) || !eligible((ImageView) iconView)) return;
                        XposedHelpers.setBooleanField(layout, "mTextHollowEffectAvailable", false);
                        p.setObjectExtra("rmt:hollow", custom ? Boolean.TRUE : Boolean.FALSE);
                    } catch (Throwable t) { Lx.e("cosmetics: battery hollow", t); }
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Object mark = p.getObjectExtra("rmt:hollow");
                    if (mark == null) return;
                    try { XposedHelpers.setBooleanField(p.thisObject, "mTextHollowEffectAvailable", true); } catch (Throwable ignored) { }
                    // stock shape: the inside TextView skips its own draw while the hollow effect is on, so the
                    // number is drawn here once, solid, in a colour that contrasts with the pill under each digit
                    if (Boolean.FALSE.equals(mark)) {
                        try { drawStockDigits((ViewGroup) p.thisObject, (Canvas) p.args[0]); } catch (Throwable t) { Lx.e("cosmetics: battery stock digits", t); }
                    }
                }
            });
            // updateBatteryLayout (:652) re-shows the inside percentage view on every mode change; keep it hidden
            // while a custom shape draws the number itself.
            H.hook(H.find(layoutClass, "updateBatteryLayout", int.class, boolean.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (s().battery <= 0) return;
                        ViewGroup layout = (ViewGroup) p.thisObject;
                        Object iconView = XposedHelpers.getObjectField(layout, "mBatteryIconView");
                        if (iconView instanceof ImageView && eligible((ImageView) iconView)) syncInside(layout, s().battery);
                    } catch (Throwable t) { Lx.e("cosmetics: battery inside", t); }
                }
            });
        }
        Lx.i("Cosmetics: battery style hook ready");
    }

    /** Status bar, keyguard status bar and QS header batteries; AOD and in-call variants keep stock artwork. */
    private static boolean eligible(ImageView view) {
        try {
            Object type = XposedHelpers.getObjectField(view, "mType");
            if (type == null) return false;
            return Boolean.TRUE.equals(XposedHelpers.callMethod(type, "isStatusBar"))
                    || Boolean.TRUE.equals(XposedHelpers.callMethod(type, "isQuickStatusBar"))
                    || Boolean.TRUE.equals(XposedHelpers.callMethod(type, "isKeyguardStatusBar"));
        } catch (Throwable t) { return false; }
    }

    /** The owning MFVBatteryViewLayout (icon -> mBatteryContainer -> layout), or null. */
    private static ViewGroup layoutOf(View icon) {
        View v = icon;
        for (int i = 0; i < 4 && v != null; i++) {
            if (v.getClass().getName().equals(BATTERY_LAYOUT) && v instanceof ViewGroup) return (ViewGroup) v;
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return null;
    }

    /** Current light / dark tint of the layout (mBatteryColors.mTint), falling back to the icon's own colour. */
    static int tintOf(ImageView icon) {
        try {
            ViewGroup layout = layoutOf(icon);
            if (layout != null) {
                Object colors = XposedHelpers.getObjectField(layout, "mBatteryColors");
                if (colors != null) { int t = XposedHelpers.getIntField(colors, "mTint"); if (t != 0) return t; }
            }
        } catch (Throwable ignored) { }
        try { return XposedHelpers.getIntField(icon, "mStatusBarColor"); } catch (Throwable t) { return 0xD8FFFFFF; }
    }

    /** Layouts whose views we hid: bit 1 = inside number (show mode 1), bit 2 = outside number (mode 2), bit 4 = vendor bolt (both One UI 8 only). */
    private static final Map<ViewGroup, Integer> insideHidden = new WeakHashMap<>();

    private static int hiddenFlags(ViewGroup layout) {
        synchronized (insideHidden) { Integer f = insideHidden.get(layout); return f == null ? 0 : f; }
    }

    /**
     * Custom shapes write the number themselves: show mode 1 (number inside) hides the vendor inside TextView; One UI 8
     * always draws the number inside, so in mode 2 (number next to the icon) the outside TextView is hidden as well,
     * and the vendor charging bolt (we draw our own). Anything hidden here comes back in restoreInside / on a style change.
     */
    private static void syncInside(ViewGroup layout, int style) {
        try {
            int mode = XposedHelpers.getIntField(layout, "mShowMode");
            Object inside = XposedHelpers.getObjectField(layout, "mBatteryLevelInsideView");
            Object outside = XposedHelpers.getObjectField(layout, "mBatteryLevelOutsideView");
            boolean oneUi8 = style == CosmeticsArt.BATTERY_ONE_UI8;
            int flags = hiddenFlags(layout), want = 0;
            if (mode == 1 && inside instanceof View) { want |= 1; if (((View) inside).getVisibility() == View.VISIBLE) ((View) inside).setVisibility(View.INVISIBLE); }
            if (mode == 2 && oneUi8 && outside instanceof View) { want |= 2; if (((View) outside).getVisibility() != View.GONE) ((View) outside).setVisibility(View.GONE); }
            if ((flags & 2) != 0 && (want & 2) == 0 && mode == 2 && outside instanceof View) ((View) outside).setVisibility(View.VISIBLE);
            if (oneUi8) { hideVendorBolt(layout); want |= 4; }
            else if ((flags & 4) != 0) showVendorBolt(layout);
            synchronized (insideHidden) { if (want == 0) insideHidden.remove(layout); else insideHidden.put(layout, want); }
        } catch (Throwable t) { Lx.e("cosmetics: battery inside view", t); }
    }

    private static void restoreInside(ViewGroup layout) {
        synchronized (insideHidden) { insideHidden.remove(layout); }
        try {
            int mode = XposedHelpers.getIntField(layout, "mShowMode");
            Object inside = XposedHelpers.getObjectField(layout, "mBatteryLevelInsideView");
            Object outside = XposedHelpers.getObjectField(layout, "mBatteryLevelOutsideView");
            if (mode == 1 && inside instanceof View) ((View) inside).setVisibility(View.VISIBLE);
            if (mode == 2 && outside instanceof View) ((View) outside).setVisibility(View.VISIBLE);
        } catch (Throwable t) { Lx.e("cosmetics: battery inside restore", t); }
        showVendorBolt(layout);
    }

    /** Lets the vendor layout decide its bolt again: ChargeIndicator.updateVisibility(true) shows it only while charging. */
    private static void showVendorBolt(ViewGroup layout) {
        if (s().batteryNoCharge) return;
        try {
            Object current = XposedHelpers.getObjectField(layout, "mCurrentChargeView");
            if (current != null) XposedHelpers.callMethod(current, "updateVisibility", true);
        } catch (Throwable ignored) { }
    }

    private static void hideVendorBolt(ViewGroup layout) {
        for (String f : new String[]{"mInsideChargeView", "mOutsideChargeView"}) {
            try {
                Object v = XposedHelpers.getObjectField(layout, f);
                if (v instanceof View && ((View) v).getVisibility() != View.GONE) ((View) v).setVisibility(View.GONE);
            } catch (Throwable ignored) { }
        }
    }

    /**
     * Readable stock number (CosmeticsFeatures.COS_BATTERY_STOCK_READABLE). With the number inside, RedMagic's
     * MFVBatteryViewLayout.drawTextHollowEffect (:316) punches the digits out of the pill (DST_OUT) and paints them
     * again with BatteryColors.mTextColor; on this firmware that colour is almost the pill colour, and in the Control
     * Center header (type QUICK_STATUS_BAR, fixed colours) it is the same white as the pill, so the number vanishes.
     * Here the pill is drawn normally and the digits once, solid: over the level fill in black / white against the
     * fill colour, over the empty part against the track colour (or in the tint when that part is see-through).
     */
    private static void drawStockDigits(ViewGroup layout, Canvas canvas) {
        Object insideObj = XposedHelpers.getObjectField(layout, "mBatteryLevelInsideView");
        Object containerObj = XposedHelpers.getObjectField(layout, "mBatteryContainer");
        Object iconObj = XposedHelpers.getObjectField(layout, "mBatteryIconView");
        if (!(insideObj instanceof android.widget.TextView) || !(containerObj instanceof View) || !(iconObj instanceof View)) return;
        android.widget.TextView level = (android.widget.TextView) insideObj;
        View container = (View) containerObj, icon = (View) iconObj;
        if (level.getVisibility() != View.VISIBLE || container.getVisibility() != View.VISIBLE) return;
        CharSequence cs = level.getText();
        if (cs == null || cs.length() == 0) return;
        String text = cs.toString();
        Object colors = XposedHelpers.getObjectField(layout, "mBatteryColors");
        int fg = XposedHelpers.getIntField(colors, "mBatteryIconFgColor");
        int bg = XposedHelpers.getIntField(colors, "mBatteryIconBgColor");
        int tint = XposedHelpers.getIntField(colors, "mTint");
        int pct = XposedHelpers.getIntField(layout, "mBateryLevel");
        android.graphics.Paint paint = new android.graphics.Paint(level.getPaint());
        paint.setXfermode(null); paint.setShader(null); paint.setAntiAlias(true);
        paint.setTextAlign(android.graphics.Paint.Align.LEFT);
        float x = container.getLeft() + level.getLeft() + (level.getWidth() - paint.measureText(text)) / 2f;
        float y = container.getTop() + level.getTop() + (level.getHeight() - level.getPaddingBottom()) / 2f - (paint.descent() + paint.ascent()) / 2f;
        float iconLeft = container.getLeft() + icon.getLeft();
        float fillRight = iconLeft + icon.getWidth() * Math.max(0, Math.min(100, pct)) / 100f;
        int sv = canvas.save();
        try { canvas.clipRect(0, 0, fillRight, layout.getHeight()); paint.setColor(CosmeticsArt.readableOn(fg, tint)); canvas.drawText(text, x, y, paint); }
        finally { canvas.restoreToCount(sv); }
        sv = canvas.save();
        try { canvas.clipRect(fillRight, 0, layout.getWidth(), layout.getHeight()); paint.setColor(CosmeticsArt.readableOn(bg, tint)); canvas.drawText(text, x, y, paint); }
        finally { canvas.restoreToCount(sv); }
    }

    /** Draws CosmeticsArt battery shapes with the state of its MFVBatteryMeterView. */
    static final class BatteryDrawable extends Drawable {
        private final WeakReference<ImageView> ref;
        private final int w, h;
        BatteryDrawable(ImageView v, int w, int h) { ref = new WeakReference<>(v); this.w = w; this.h = h; }
        @Override public int getIntrinsicWidth() { return w; }
        @Override public int getIntrinsicHeight() { return h; }
        @Override public void draw(Canvas canvas) {
            ImageView v = ref.get();
            if (v == null) return;
            try {
                S s = s();
                int style = s.battery;
                if (!CosmeticsArt.batteryStyleValid(style)) return;
                int level = XposedHelpers.getIntField(v, "mBateryLevel");
                if (level < 0) level = 0; if (level > 100) level = 100;
                boolean charging = XposedHelpers.getBooleanField(v, "mIsCharing");
                boolean saver = XposedHelpers.getBooleanField(v, "mIsPowerSave");
                int tint = tintOf(v);
                int fill;
                if (style == CosmeticsArt.BATTERY_ONE_UI8) {
                    fill = charging ? CosmeticsArt.ONE_UI8_CHARGING : level <= 15 ? CosmeticsArt.ONE_UI8_LOW : saver ? CosmeticsArt.ONE_UI8_SAVER : tint;
                } else fill = level <= 20 && !charging ? 0xE5FF0000 : tint;   // stock: red below 20 % unless charging
                boolean percent = s.batteryPercent || style == CosmeticsArt.BATTERY_ONE_UI8;
                ViewGroup layout = layoutOf(v);
                if (layout != null) { try { percent |= XposedHelpers.getIntField(layout, "mShowMode") == 1; } catch (Throwable ignored) { } }
                Rect b = getBounds();
                CosmeticsArt.drawBattery(style, canvas, new RectF(b), level, charging && !s.batteryNoCharge, saver, percent, fill, tint);
            } catch (Throwable t) { Lx.e("cosmetics: battery draw", t); }
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ============================================================================================= charging symbol

    private static final String CHARGE_INDICATOR = "com.zte.mifavor.views.ChargeIndicator";
    private static final Map<View, Boolean> chargeViews = new WeakHashMap<>();

    /**
     * nubia-ly BatteryControlBackend "hide charge": the bolt is a separate ChargeIndicator ImageView (inside or
     * outside the battery). ChargeIndicator.updateVisibility() / updateVisibility(boolean) (:72 / :146) decide its
     * visibility on every charging change; we force GONE afterwards.
     */
    private static void chargeIndicator(ClassLoader cl) {
        Class<?> ci = H.cls(cl, CHARGE_INDICATOR);
        if (ci == null) return;
        H.hookAll(ci, "updateVisibility", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    View v = (View) p.thisObject;
                    synchronized (chargeViews) { chargeViews.put(v, Boolean.TRUE); }
                    S s = s();
                    boolean hide = s.batteryNoCharge;
                    if (!hide && s.battery == CosmeticsArt.BATTERY_ONE_UI8) {
                        // One UI 8 draws its own bolt inside the pill — only for the batteries it replaces (not AOD / in-call)
                        ViewGroup layout = layoutOf(v);
                        Object icon = layout == null ? null : XposedHelpers.getObjectField(layout, "mBatteryIconView");
                        hide = icon instanceof ImageView && eligible((ImageView) icon);
                    }
                    if (hide && v.getVisibility() != View.GONE) v.setVisibility(View.GONE);
                } catch (Throwable t) { Lx.e("cosmetics: charge indicator", t); }
            }
        });
        Lx.i("Cosmetics: charge indicator hook ready");
    }

    /** Setting changed: hide every known indicator, or let each recompute its stock visibility. */
    static void refreshChargeIndicators(boolean hide) {
        List<View> views;
        synchronized (chargeViews) { views = new ArrayList<>(chargeViews.keySet()); }
        for (View v : views) {
            try {
                if (hide) { if (v.getVisibility() != View.GONE) v.setVisibility(View.GONE); }
                else XposedHelpers.callMethod(v, "updateVisibility");
            } catch (Throwable ignored) { }
        }
    }

    // ============================================================================================= lock-screen clock font

    private static Typeface fontCache; private static int fontCacheIndex = -1;

    /** Typeface for the chosen clock font, or null for stock / unavailable. */
    static synchronized Typeface clockTypeface() {
        int i = s().clockFont;
        if (i <= 0 || i >= CosmeticsFeatures.CLOCK_FONT_DEFS.length) return null;
        if (fontCacheIndex == i) return fontCache;
        Typeface t = null;
        try {
            if (i == CosmeticsFeatures.CLOCK_FONT_CUSTOM) {
                Context c = Lx.context();
                if (c != null) {
                    File f = new File(c.getCacheDir(), "rmtweaks_lockclock.ttf");
                    try (InputStream in = c.getContentResolver().openInputStream(Uri.parse("content://" + com.hammad.redmagictweaks.LogProvider.AUTHORITY + "/font/lockclock")); FileOutputStream o = new FileOutputStream(f)) {
                        if (in != null) { byte[] buf = new byte[65536]; int r; while ((r = in.read(buf)) > 0) o.write(buf, 0, r); }
                    }
                    if (f.length() > 0) t = Typeface.createFromFile(f);
                    if (t == null || t.equals(Typeface.DEFAULT)) Lx.w(("cosmetics: imported clock font missing or invalid — import a .ttf in " + com.hammad.redmagictweaks.BuildConfig.APP_NAME));
                }
            } else {
                String[] d = CosmeticsFeatures.CLOCK_FONT_DEFS[i];
                t = Typeface.create(d[1], Integer.parseInt(d[2]));
            }
        } catch (Throwable e) { Lx.e("cosmetics: clock font", e); t = null; }
        fontCache = t; fontCacheIndex = i;
        return t;
    }

    private static void clockFont(ClassLoader cl) {
        Class<?> fru = H.cls(cl, FONT_RES_UTIL);
        if (fru != null) {
            // Typeface getClockTypeface(int clockIndex, int typefaceId): every lock-screen / AOD clock style asks here.
            H.hook(H.find(fru, "getClockTypeface", int.class, int.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Typeface t = clockTypeface();
                    if (t != null) p.setResult(t);
                }
            });
        }
        Class<?> def = H.cls(cl, CLOCK_DEFAULT);
        if (def != null) {
            // refreshClockViewFont() re-applies the vendor "red-magic-zdigit" face; put ours back on top.
            H.hook(H.find(def, "refreshClockViewFont"), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Typeface t = clockTypeface();
                    if (t == null) return;
                    try {
                        Object tv = XposedHelpers.getObjectField(p.thisObject, "mClockView");
                        if (tv instanceof android.widget.TextView) ((android.widget.TextView) tv).setTypeface(t);
                    } catch (Throwable e) { Lx.e("cosmetics: clock font apply", e); }
                }
            });
        }
        Lx.i("Cosmetics: lock clock font hooks ready");
    }

    // ============================================================================================= quick settings tiles

    /** GradientDrawable -> owning QS tile view (shape + finish) */
    private static final Map<GradientDrawable, WeakReference<ViewGroup>> qsOwners = new WeakHashMap<>();
    /** GradientDrawable (track or fill layer) -> owning brightness slider */
    private static final Map<GradientDrawable, WeakReference<SeekBar>> brightnessLayers = new WeakHashMap<>();

    private static boolean isTile(Object v) {
        if (v == null) return false;
        String n = v.getClass().getName();
        return n.equals(QS_WIDE) || n.equals(QS_CIRCLE);
    }

    private static void qs(ClassLoader cl) {
        Class<?> base = H.cls(cl, QS_BASE), impl = H.cls(cl, QS_IMPL);
        if (base == null) return;
        XC_MethodHook track = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!isTile(p.thisObject)) return;
                try {
                    ViewGroup tile = (ViewGroup) p.thisObject;
                    Object d = XposedHelpers.callMethod(tile, "getColorBackgroundDrawable");
                    if (!(d instanceof GradientDrawable)) return;
                    synchronized (qsOwners) { qsOwners.put((GradientDrawable) d, new WeakReference<>(tile)); }
                    shape(tile, (GradientDrawable) d, false);
                } catch (Throwable t) { Lx.e("cosmetics: qs track", t); }
            }
        };
        H.hook(H.find(base, "createTileBackground"), track);
        H.hook(H.find(base, "updateResources"), track);
        if (impl != null) H.hook(H.find(impl, "onLayout", boolean.class, int.class, int.class, int.class, int.class), track);
        Lx.i("Cosmetics: QS tile hooks ready");
    }

    /** Apply the chosen corner shape to the tile's background drawable (uses the drawable bounds; retries once after layout). */
    private static void shape(final ViewGroup tile, final GradientDrawable d, boolean retried) {
        boolean wide = tile.getClass().getName().equals(QS_WIDE);
        S s = s();
        int sel = wide ? s.qsShapeLarge : s.qsShape;
        if (sel <= 0 || sel >= CosmeticsFeatures.QS_SHAPE_FACTOR.length) return;
        float factor = CosmeticsFeatures.QS_SHAPE_FACTOR[sel];
        Rect b = d.getBounds();
        int w = b.width(), h = b.height();
        if (w <= 0 || h <= 0) { w = tile.getWidth(); h = tile.getHeight(); }
        if (sel != 1 && (w <= 0 || h <= 0)) {
            if (!retried) tile.post(() -> shape(tile, d, true));
            return;
        }
        float r = factor < 0 ? Math.min(w, h) * 0.5f : Math.min(w, h) * factor;
        float[] corners = factor < 0 ? new float[]{r, r, 0, 0, r, r, 0, 0} : new float[]{r, r, r, r, r, r, r, r};
        float[] cur = d.getCornerRadii();
        if (cur != null && Arrays.equals(cur, corners) && d.getShape() == GradientDrawable.RECTANGLE) return;
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadii(corners);
    }

    /** One hook on GradientDrawable.draw serves the QS tile finish and the brightness slider style. */
    private static void gradientDraw() {
        H.hook(H.find(GradientDrawable.class, "draw", Canvas.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(final MethodHookParam p) {
                final GradientDrawable d = (GradientDrawable) p.thisObject;
                WeakReference<ViewGroup> tileRef; WeakReference<SeekBar> sliderRef;
                synchronized (qsOwners) { tileRef = qsOwners.isEmpty() ? null : qsOwners.get(d); }
                synchronized (brightnessLayers) { sliderRef = brightnessLayers.isEmpty() ? null : brightnessLayers.get(d); }
                if (tileRef == null && sliderRef == null) return;
                try {
                    final Canvas canvas = (Canvas) p.args[0];
                    CosmeticsArt.NativeDraw stock = () -> XposedBridge.invokeOriginalMethod(p.method, p.thisObject, p.args);
                    if (tileRef != null) {
                        ViewGroup tile = tileRef.get();
                        int style = s().qsStyle;
                        if (tile == null || style <= 0 || !tile.isAttachedToWindow()) return;
                        if (CosmeticsArt.drawTile(style, canvas, d, density(tile), stock)) p.setResult(null);
                        return;
                    }
                    SeekBar slider = sliderRef.get();
                    int style = s().brightness;
                    if (slider == null || style <= 0 || !slider.isAttachedToWindow()) return;
                    if (CosmeticsArt.drawBrightness(style, canvas, d, density(slider), stock)) p.setResult(null);
                } catch (Throwable t) {
                    Lx.e("cosmetics: gradient draw", t);
                }
            }
        });
    }

    // ============================================================================================= notification cards

    private static void notification(ClassLoader cl) {
        Class<?> bg = H.cls(cl, NOTIF_BG);
        if (bg == null) return;
        H.hook(H.find(bg, "draw", Canvas.class, Drawable.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(final MethodHookParam p) {
                S s = s();
                int style = s.notifStyle, opacity = s.notifOpacity;
                if (style <= 0 && opacity <= 0) return;
                try {
                    final View view = (View) p.thisObject;
                    final Drawable nativeBg = (Drawable) p.args[1];
                    if (nativeBg == null || XposedHelpers.getObjectField(view, "mBackground") != nativeBg) return;
                    Canvas canvas = (Canvas) p.args[0];
                    int percent = opacity > 0 && opacity < CosmeticsFeatures.NOTIF_OPACITY_PERCENT.length ? CosmeticsFeatures.NOTIF_OPACITY_PERCENT[opacity] : 100;
                    int saved = canvas.saveLayerAlpha(0, 0, view.getWidth(), view.getHeight(), percent * 255 / 100);
                    try {
                        XposedBridge.invokeOriginalMethod(p.method, p.thisObject, p.args);
                        if (style > 0) {
                            float[] radii = (float[]) XposedHelpers.getObjectField(view, "mCornerRadii");
                            CosmeticsArt.drawCard(style, canvas, new RectF(nativeBg.getBounds()), radii, density(view));
                        }
                    } finally { canvas.restoreToCount(saved); }
                    p.setResult(null);
                } catch (Throwable t) { Lx.e("cosmetics: notification draw", t); }
            }
        });
        Lx.i("Cosmetics: notification hook ready");
    }

    // ============================================================================================= power menu

    private static void powerMenu(ClassLoader cl) {
        Class<?> dlg = H.cls(cl, POWER_DIALOG);
        if (dlg == null) return;
        H.hook(H.find(dlg, "initView"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                int style = s().powerStyle;
                try {
                    for (String f : new String[]{"mPowerContent", "mResetContent"}) {
                        Object c = XposedHelpers.getObjectField(p.thisObject, f);
                        if (!(c instanceof ViewGroup)) continue;
                        ViewGroup g = (ViewGroup) c;
                        for (int i = 0; i < g.getChildCount(); i++) {
                            View child = g.getChildAt(i);
                            if (child instanceof ImageButton) decorate((ImageButton) child, style);
                        }
                    }
                } catch (Throwable t) { Lx.e("cosmetics: power menu", t); }
            }
        });
        Lx.i("Cosmetics: power menu hook ready");
    }

    private static void decorate(ImageButton b, int style) {
        Drawable bg = b.getBackground();
        Drawable inner = bg instanceof CardDecor ? ((CardDecor) bg).getDrawable() : bg;
        if (style <= 0) { if (bg instanceof CardDecor) b.setBackground(inner); return; }
        if (inner == null) inner = new ColorDrawable(0);
        b.setBackground(new CardDecor(inner, style, density(b)));
    }

    /** Draws the wrapped background, then a round card finish on top (nubia-ly PowerMenuBackend). */
    static final class CardDecor extends DrawableWrapper {
        private final int style; private final float density;
        CardDecor(Drawable inner, int style, float density) { super(inner); this.style = style; this.density = density; }
        @Override public void draw(Canvas canvas) {
            super.draw(canvas);
            try {
                Rect r = getBounds();
                float diameter = Math.min(r.width(), r.height());
                float left = r.left + (r.width() - diameter) / 2f, top = r.top + (r.height() - diameter) / 2f;
                RectF bounds = new RectF(left, top, left + diameter, top + diameter);
                float[] corners = new float[8]; Arrays.fill(corners, diameter / 2);
                CosmeticsArt.drawCard(style, canvas, bounds, corners, density);
            } catch (Throwable t) { Lx.e("cosmetics: power draw", t); }
        }
    }

    // ============================================================================================= brightness slider

    private static void brightness(ClassLoader cl) {
        Class<?> seek = H.cls(cl, TOGGLE_SEEKBAR);
        if (seek == null) return;
        H.hook(H.find(seek, "onFinishInflate"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try { registerBrightness((SeekBar) p.thisObject); } catch (Throwable t) { Lx.e("cosmetics: brightness track", t); }
            }
        });
        Lx.i("Cosmetics: brightness hook ready");
    }

    private static void registerBrightness(SeekBar slider) {
        Drawable root = slider.getProgressDrawable();
        if (!(root instanceof LayerDrawable)) { Lx.w("cosmetics: brightness progress drawable is " + (root == null ? "null" : root.getClass().getSimpleName()) + ", style unavailable"); return; }
        LayerDrawable layer = (LayerDrawable) root;
        Drawable background = layer.findDrawableByLayerId(android.R.id.background), progress = layer.findDrawableByLayerId(android.R.id.progress);
        List<GradientDrawable> parts = new ArrayList<>();
        if (background instanceof GradientDrawable) parts.add((GradientDrawable) background);
        if (progress instanceof ClipDrawable && ((ClipDrawable) progress).getDrawable() instanceof GradientDrawable) parts.add((GradientDrawable) ((ClipDrawable) progress).getDrawable());
        else if (progress instanceof GradientDrawable) parts.add((GradientDrawable) progress);
        if (parts.isEmpty()) { Lx.w("cosmetics: brightness slider layers are not GradientDrawables, style unavailable"); return; }
        synchronized (brightnessLayers) { for (GradientDrawable g : parts) brightnessLayers.put(g, new WeakReference<>(slider)); }
    }

    // ============================================================================================= volume sliders

    private static final Map<SeekBar, Boolean> volumeSliders = new WeakHashMap<>();

    private static void volume(ClassLoader cl) {
        Class<?> dlg = H.cls(cl, VOLUME_DIALOG), row = H.cls(cl, VOLUME_ROW), slider = H.cls(cl, VOLUME_SLIDER);
        if (dlg == null || row == null || slider == null) return;
        H.hook(H.find(dlg, "initRow", row, int.class, int.class, int.class, boolean.class, boolean.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    Object sb = XposedHelpers.getObjectField(p.args[0], "slider");
                    if (sb instanceof SeekBar) synchronized (volumeSliders) { volumeSliders.put((SeekBar) sb, Boolean.TRUE); }
                } catch (Throwable t) { Lx.e("cosmetics: volume track", t); }
            }
        });
        H.hook(H.find(slider, "onDraw", Canvas.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(final MethodHookParam p) {
                int style = s().volume;
                if (style <= 0) return;
                SeekBar sb = (SeekBar) p.thisObject;
                synchronized (volumeSliders) { if (!volumeSliders.containsKey(sb)) return; }
                try {
                    float left = XposedHelpers.getIntField(sb, "mLeftPadding"), top = XposedHelpers.getIntField(sb, "mTopPadding");
                    float width = XposedHelpers.getIntField(sb, "mLength"), height = XposedHelpers.getIntField(sb, "mTrackSize");
                    if (width <= 0 || height <= 0) return;
                    float[] radii = (float[]) XposedHelpers.getObjectField(sb, "mRadii");
                    if (radii == null) { radii = new float[8]; Arrays.fill(radii, height / 2); }
                    if (CosmeticsArt.drawVolume(style, (Canvas) p.args[0], new RectF(left, top, left + width, top + height), radii, density(sb),
                            () -> XposedBridge.invokeOriginalMethod(p.method, p.thisObject, p.args))) p.setResult(null);
                } catch (Throwable t) { Lx.e("cosmetics: volume draw", t); }
            }
        });
        Lx.i("Cosmetics: volume hooks ready");
    }
}
