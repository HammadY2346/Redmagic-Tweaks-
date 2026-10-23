package com.hammad.redmagictweaks.xposed;

import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import com.hammad.redmagictweaks.SurfaceFeatures;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * com.android.systemui — notification cards (NotificationBackend), automatic heads-up suppression
 * (NotificationBehaviorBackend), status-bar notification icon limit / overflow count (NotificationIconBackend),
 * volume sliders (VolumeBackend) and power-menu buttons (PowerMenuBackend), ported from nubia-ly. All hooks read
 * the module settings at call time (snapshot refreshed once per second); drawing lives in SurfaceArt.
 */
final class SurfaceHooks {
    private SurfaceHooks() {}

    private static final String NOTIF_BG = "com.android.systemui.statusbar.notification.row.NotificationBackgroundView";
    private static final String NOTIF_ROW = "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow";
    private static final String NOTIF_BASE = "com.android.systemui.statusbar.notification.row.ActivatableNotificationView";
    private static final String HEADS_UP = "com.android.systemui.statusbar.notification.headsup.HeadsUpManagerImpl";
    private static final String NOTIF_ENTRY = "com.android.systemui.statusbar.notification.collection.NotificationEntry";
    private static final String ICON_CONTAINER = "com.android.systemui.statusbar.phone.NotificationIconContainer";
    private static final String ICON_VIEW = "com.android.systemui.statusbar.StatusBarIconView";
    private static final String STATUS_BAR_VIEW = "com.android.systemui.statusbar.phone.PhoneStatusBarView";
    private static final String VOLUME_DIALOG = "com.zte.feature.volume.MfvVolumeDialogImpl";
    private static final String VOLUME_BASE = "com.android.systemui.volume.VolumeDialogImpl";
    private static final String VOLUME_ROW = VOLUME_BASE + "$VolumeRow";
    private static final String VOLUME_SLIDER = "com.zte.mifavor.widget.ColorSeekBarZTE";
    private static final String POWER_DIALOG = "com.zte.feature.globalaction.MfvGlobalActionsDialog$MFVActionsDialog";
    private static final String VOLUME_BG = "com.zte.mifavor.views.ClipRectsDrawable";

    // ------------------------------------------------------------------------------------------- settings snapshot

    static final class S {
        int notifStyle, notifOpacity, notifCorner, iconLimit, volume, power;
        int notifTint, notifGapDp, notifSideDp;               // tint = QsArt.PANEL_COLORS index; gap / side = dp added to stock
        int[] notifTintCustom;                                // custom tint {start, end} (ColorPick), or null
        int pwrPosDp, pwrLayout, pwrDim = -1;                 // power menu: offset, SurfaceFeatures.PWR_LAYOUTS index, dim % (-1 = stock)
        boolean noAutoHeadsUp, overflowCount, volOneUi, pwrOneUi;
        /** the module draws the volume bar (One UI panel, or RedMagic's bar restyled), and changes the panel's size */
        boolean volCustom, volLayout;
        OneUiVolumeArt.Look one = OneUiVolumeArt.look(new OneUiVolumeArt.Src() {
            @Override public int choice(String key) { return 0; }
            @Override public boolean on(String key) { return false; }
        });
    }
    private static final OneUiVolumeArt.Src CFG = new OneUiVolumeArt.Src() {
        @Override public int choice(String key) { return Cfg.choice(key); }
        @Override public boolean on(String key) { return Cfg.on(key); }
    };
    private static volatile S snap = new S();
    private static volatile long snapAt;

    static S s() {
        long now = System.currentTimeMillis();
        if (now - snapAt > 1000) {
            S n = new S();
            n.notifStyle = clamp(Cfg.choice(SurfaceFeatures.NOTIF_STYLE), SurfaceArt.NOTIF_STYLES.length - 1);
            n.notifOpacity = Cfg.num(SurfaceFeatures.NOTIF_OPACITY, SurfaceArt.opacity(Cfg.choice(SurfaceFeatures.NOTIF_OPACITY)));
            if (n.notifOpacity >= 100) n.notifOpacity = 0;
            n.notifCorner = Cfg.num(SurfaceFeatures.NOTIF_CORNERS, SurfaceArt.cornerDp(Cfg.choice(SurfaceFeatures.NOTIF_CORNERS)));
            n.notifTint = clamp(Cfg.choice(SurfaceFeatures.NOTIF_TINT), QsArt.PANEL_COLORS.length - 1);
            n.notifTintCustom = Cfg.colorPair(SurfaceFeatures.NOTIF_TINT);
            if (n.notifTintCustom != null && n.notifTint <= 0) n.notifTint = 2;
            n.notifGapDp = Cfg.num(SurfaceFeatures.NOTIF_GAP, SurfaceFeatures.pick(SurfaceFeatures.NOTIF_GAP_DP, Cfg.choice(SurfaceFeatures.NOTIF_GAP)));
            n.notifSideDp = Cfg.num(SurfaceFeatures.NOTIF_SIDE, SurfaceFeatures.pick(SurfaceFeatures.NOTIF_SIDE_DP, Cfg.choice(SurfaceFeatures.NOTIF_SIDE)));
            n.pwrPosDp = Cfg.num(SurfaceFeatures.PWR_POSITION, SurfaceFeatures.pick(SurfaceFeatures.PWR_POSITION_DP, Cfg.choice(SurfaceFeatures.PWR_POSITION)));
            n.pwrLayout = clamp(Cfg.choice(SurfaceFeatures.PWR_LAYOUT), SurfaceFeatures.PWR_LAYOUTS.length - 1);
            n.pwrDim = Cfg.num(SurfaceFeatures.PWR_DIM, SurfaceFeatures.pick(SurfaceFeatures.PWR_DIM_PCT, Cfg.choice(SurfaceFeatures.PWR_DIM)));
            n.noAutoHeadsUp = Cfg.on(SurfaceFeatures.NOTIF_NO_AUTO_HEADSUP);
            n.overflowCount = Cfg.on(SurfaceFeatures.NOTIF_OVERFLOW_COUNT);
            n.iconLimit = Cfg.num(SurfaceFeatures.NOTIF_ICON_LIMIT, SurfaceFeatures.iconLimit(Cfg.choice(SurfaceFeatures.NOTIF_ICON_LIMIT)));
            n.volume = clamp(Cfg.choice(SurfaceFeatures.VOL_STYLE), SurfaceArt.VOLUME_STYLES.length - 1);
            n.power = clamp(Cfg.choice(SurfaceFeatures.PWR_STYLE), SurfaceArt.CARD_STYLES.length - 1);
            n.volOneUi = Cfg.on(SurfaceFeatures.VOL_ONEUI);
            n.pwrOneUi = Cfg.on(SurfaceFeatures.PWR_ONEUI);
            n.one = OneUiVolumeArt.look(CFG);
            n.volCustom = OneUiVolumeArt.custom(n.one);
            n.volLayout = OneUiVolumeArt.customLayout(n.one);
            S old = snap;
            snap = n; snapAt = now;
            if (old.iconLimit != n.iconLimit || old.overflowCount != n.overflowCount) refreshIcons();
        }
        return snap;
    }
    private static int clamp(int v, int max) { return v < 0 || v > max ? 0 : v; }
    private static boolean main() { return Looper.myLooper() == Looper.getMainLooper(); }
    private static float density(View v) { try { return v.getResources().getDisplayMetrics().density; } catch (Throwable t) { return 3f; } }
    private static boolean dark(View v) { try { return (v.getResources().getConfiguration().uiMode & 0x30) == 0x20; } catch (Throwable t) { return true; } }
    private static View parentView(View v) { ViewParent p = v.getParent(); return p instanceof View ? (View) p : null; }

    static void install(ClassLoader cl) {
        try { notification(cl); } catch (Throwable t) { Lx.e("Surface: notification install", t); }
        try { notifSpacing(cl); } catch (Throwable t) { Lx.e("Surface: notification spacing install", t); }
        try { headsUp(cl); } catch (Throwable t) { Lx.e("Surface: heads-up install", t); }
        try { icons(cl); } catch (Throwable t) { Lx.e("Surface: notification icons install", t); }
        try { volume(cl); } catch (Throwable t) { Lx.e("Surface: volume install", t); }
        try { power(cl); } catch (Throwable t) { Lx.e("Surface: power menu install", t); }
        Lx.i("Surface: hooks ready");
    }

    // ============================================================================================= notification cards

    /** Only the normal background of an ExpandableNotificationRow (not guts, not the dismiss / menu backgrounds). */
    private static boolean notifEligible(View view) {
        if (view == null || !view.isAttachedToWindow() || !view.getClass().getName().equals(NOTIF_BG)) return false;
        View owner = parentView(view);
        for (int depth = 0; depth < 12 && owner != null; depth++, owner = parentView(owner))
            if (owner.getClass().getName().equals(NOTIF_ROW)) { try { return XposedHelpers.getObjectField(owner, "mBackgroundNormal") == view; } catch (Throwable t) { return false; } }
        return false;
    }

    private static void notification(ClassLoader cl) {
        Class<?> bg = H.cls(cl, NOTIF_BG);
        if (bg == null) return;
        if (H.cls(cl, NOTIF_ROW) == null || H.cls(cl, NOTIF_BASE) == null) Lx.w("Surface: notification row classes missing, card styling may not match");
        H.hook(H.find(bg, "draw", Canvas.class, Drawable.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(final MethodHookParam p) {
                S s = s();
                if (s.notifStyle <= 0 && s.notifOpacity <= 0 && s.notifCorner <= 0 && s.notifTint <= 0) return;
                if (!main() || !(p.thisObject instanceof View)) return;
                final View view = (View) p.thisObject;
                if (!notifEligible(view)) return;
                boolean nativeDrawn = false;
                try {
                    if (p.args[1] == null || XposedHelpers.getObjectField(view, "mBackground") != p.args[1] || !(p.args[1] instanceof Drawable)) return;
                    Drawable nativeBg = (Drawable) p.args[1];
                    Canvas canvas = (Canvas) p.args[0];
                    float density = density(view);
                    int saved = canvas.saveLayerAlpha(0, 0, view.getWidth(), view.getHeight(), s.notifOpacity == 0 ? 255 : s.notifOpacity * 255 / 100);
                    try {
                        XposedBridge.invokeOriginalMethod(p.method, p.thisObject, p.args);
                        nativeDrawn = true;
                        RectF bounds = new RectF(nativeBg.getBounds());
                        float[] radii = (float[]) XposedHelpers.getObjectField(view, "mCornerRadii");
                        if (s.notifStyle > 0 && radii != null) SurfaceArt.drawCard(s.notifStyle, canvas, bounds, radii, density, dark(view));
                        if (s.notifTint > 0 && !bounds.isEmpty()) {
                            // wash only what the card painted (SRC_ATOP on this background-only layer)
                            int[] tint = s.notifTintCustom != null ? s.notifTintCustom.clone() : QsArt.panelColors(view.getContext(), s.notifTint, dark(view));
                            if (tint != null) {
                                Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
                                QsArt.fillPaint(tp, bounds, tint);
                                tp.setAlpha(SurfaceFeatures.NOTIF_TINT_ALPHA);
                                tp.setXfermode(new android.graphics.PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
                                canvas.drawRect(bounds, tp);
                            }
                        }
                        if (s.notifCorner != 0 && !bounds.isEmpty()) {
                            Path path = new Path(); path.addRoundRect(bounds, s.notifCorner * density, s.notifCorner * density, Path.Direction.CW);
                            // Clear outside the extra corner clip on this background-only layer.
                            int clip = canvas.save();
                            try { canvas.clipOutPath(path); canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); } finally { canvas.restoreToCount(clip); }
                        }
                    } finally { canvas.restoreToCount(saved); }
                    p.setResult(null);
                } catch (Throwable t) { Lx.e("Surface: notification draw", t); if (nativeDrawn) p.setResult(null); }
            }
        });
        Lx.i("Surface: notification card hook ready");
    }

    // ============================================================================================= heads-up

    // ============================================================================================= notification spacing

    private static final String STACK_ALGO = "com.android.systemui.statusbar.notification.stack.StackScrollAlgorithm";
    private static final String STACK_LAYOUT = "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout";
    /** object -> {stock value, value we wrote} per field, so re-running a resource loader never adds twice. */
    private static final Map<Object, int[]> gapSeen = new WeakHashMap<>(), sideSeen = new WeakHashMap<>();

    /**
     * Space between notification cards (mPaddingBetweenElements, read by StackScrollAlgorithm when it stacks the rows,
     * and kept on NotificationStackScrollLayout for its height maths) and the side margin of the stack (mSidePaddings,
     * recomputed by updateSidePadding on every measure). AOSP names; the resource loaders run on SystemUI start and
     * config changes, so the card gap needs a SystemUI restart, the side margin follows the next measure.
     */
    private static void notifSpacing(ClassLoader cl) {
        Class<?> algo = H.cls(cl, STACK_ALGO), stack = H.cls(cl, STACK_LAYOUT);
        if (algo == null || stack == null) { Lx.w("Surface: notification stack classes missing, card gap / side margin off"); return; }
        XC_MethodHook gap = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || p.thisObject == null) return;
                float density = p.thisObject instanceof View ? density((View) p.thisObject)
                        : p.args.length > 0 && p.args[0] instanceof android.content.Context ? ((android.content.Context) p.args[0]).getResources().getDisplayMetrics().density
                        : android.content.res.Resources.getSystem().getDisplayMetrics().density;
                bump(gapSeen, p.thisObject, "mPaddingBetweenElements", Math.round(s().notifGapDp * density), 0);
            }
        };
        int n = H.hookAll(algo, "initView", gap) + H.hookAll(algo, "updateResources", gap);
        int m = H.hookAll(stack, "initView", gap) + H.hookAll(stack, "reinitView", gap);
        int side = H.hookAll(stack, "updateSidePadding", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !(p.thisObject instanceof View)) return;
                bump(sideSeen, p.thisObject, "mSidePaddings", dp((View) p.thisObject, s().notifSideDp), 0);
            }
        });
        Lx.i("Surface: notification spacing hooks algo=" + n + " stack=" + m + " side=" + side);
    }
    private static final java.util.Set<String> bumpMissing = new java.util.HashSet<>();
    private static void bump(Map<Object, int[]> seen, Object o, String field, int deltaPx, int min) {
        try {
            // int on most builds, float on RedMagic OS 11 (StackScrollAlgorithm / MfvStackScrollAlgorithm)
            boolean isFloat = XposedHelpers.findField(o.getClass(), field).getType() == float.class;
            int cur = isFloat ? Math.round(XposedHelpers.getFloatField(o, field)) : XposedHelpers.getIntField(o, field);
            int[] st;
            synchronized (seen) {
                st = seen.get(o);
                if (st == null && deltaPx == 0) return;                    // never touched, nothing to add
                if (st == null || cur != st[1]) { st = new int[]{cur, cur}; seen.put(o, st); }  // the loader reset it
            }
            int want = Math.max(min, st[0] + deltaPx);
            if (want != cur) { if (isFloat) XposedHelpers.setFloatField(o, field, want); else XposedHelpers.setIntField(o, field, want); }
            st[1] = want;
        } catch (Throwable t) {
            String key = o.getClass().getName() + "." + field;
            synchronized (bumpMissing) { if (!bumpMissing.add(key)) return; }
            Lx.w("Surface: " + key + " not adjustable (" + t + ")");
        }
    }

    private static void headsUp(ClassLoader cl) {
        Class<?> owner = H.cls(cl, HEADS_UP), entry = H.cls(cl, NOTIF_ENTRY);
        if (owner == null || entry == null) return;
        // public void showNotification(NotificationEntry, boolean pinnedByUser)  — HeadsUpManagerImpl.java:1422
        H.hook(H.find(owner, "showNotification", entry, boolean.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (s().noAutoHeadsUp && Boolean.FALSE.equals(p.args[1])) p.setResult(null);
            }
        });
        Lx.i("Surface: heads-up hook ready");
    }

    // ============================================================================================= status-bar notification icons

    private static final Map<ViewGroup, Boolean> iconOwners = new WeakHashMap<>();
    private static Handler mainHandler;

    private static boolean iconsEligible(ViewGroup owner) {
        if (!owner.getClass().getName().equals(ICON_CONTAINER) || !owner.isAttachedToWindow() || XposedHelpers.getObjectField(owner, "mIsolatedIcon") != null) return false;
        View view = owner;
        for (int n = 0; n < 16 && view != null; n++) { if (view.getClass().getName().equals(STATUS_BAR_VIEW)) return true; view = parentView(view); }
        return false;
    }

    private static void icons(ClassLoader cl) {
        Class<?> owner = H.cls(cl, ICON_CONTAINER), icon = H.cls(cl, ICON_VIEW);
        if (owner == null || icon == null) return;
        H.hook(H.find(owner, "calculateIconXTranslations"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!p.hasThrowable() && main() && p.thisObject instanceof ViewGroup) iconOwners.put((ViewGroup) p.thisObject, Boolean.TRUE);
            }
        });
        // public boolean shouldForceOverflow(int index, int speedBump, float appearAmount, int maxIcons)  — :713
        H.hook(H.find(owner, "shouldForceOverflow", int.class, int.class, float.class, int.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                int limit = s().iconLimit;
                if (limit == 0 || !main() || !(p.thisObject instanceof ViewGroup)) return;
                try { if (iconsEligible((ViewGroup) p.thisObject)) p.args[3] = limit; } catch (Throwable t) { Lx.e("Surface: icon limit", t); }
            }
        });
        // Draws "+N" instead of the overflow dot on the icon that is in the dot state (StatusBarIconView.onDraw :458).
        H.hook(H.find(icon, "onDraw", Canvas.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!s().overflowCount || !main() || !(p.thisObject instanceof View)) return;
                View iconView = (View) p.thisObject;
                if (!(iconView.getParent() instanceof ViewGroup)) return;
                ViewGroup container = (ViewGroup) iconView.getParent();
                try {
                    if (!iconsEligible(container) || !iconView.getClass().getName().equals(ICON_VIEW) || !Boolean.TRUE.equals(XposedHelpers.callMethod(iconView, "isNotification")) || ((Number) XposedHelpers.callMethod(iconView, "getVisibleState")).intValue() != 1) return;
                    Object raw = XposedHelpers.getObjectField(container, "mIconStates");
                    if (!(raw instanceof Map)) return;
                    Map<?, ?> states = (Map<?, ?>) raw;
                    int hidden = 0;
                    for (int n = 0; n < container.getChildCount(); n++) {
                        View child = container.getChildAt(n);
                        if (!child.getClass().getName().equals(ICON_VIEW) || !Boolean.TRUE.equals(XposedHelpers.callMethod(child, "isNotification")) || Boolean.TRUE.equals(XposedHelpers.callMethod(child, "isIconBlocked"))) continue;
                        Object state = states.get(child);
                        if (state != null && !Boolean.TRUE.equals(XposedHelpers.getObjectField(state, "hidden")) && ((Number) XposedHelpers.getObjectField(state, "iconAppearAmount")).floatValue() > 0 && ((Number) XposedHelpers.getObjectField(state, "visibleState")).intValue() != 0) hidden++;
                    }
                    if (hidden < 1 || iconView.getWidth() <= 0 || iconView.getHeight() <= 0) return;
                    float appearance = ((Number) XposedHelpers.getObjectField(iconView, "mDotAppearAmount")).floatValue();
                    if (!Float.isFinite(appearance) || appearance <= 0) return;
                    int color = ((Number) XposedHelpers.getObjectField(iconView, "mDecorColor")).intValue();
                    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    paint.setColor(color); paint.setAlpha(Math.round(Color.alpha(color) * Math.min(1f, appearance)));
                    paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(iconView.getHeight() * .52f);
                    String text = hidden > 99 ? "99+" : "+" + hidden;
                    float width = iconView.getWidth() * .95f, measured = paint.measureText(text);
                    if (measured > width) paint.setTextSize(paint.getTextSize() * width / measured);
                    Canvas c = (Canvas) p.args[0];
                    int save = c.save();
                    try { c.clipRect(0, 0, iconView.getWidth(), iconView.getHeight()); Paint.FontMetrics fm = paint.getFontMetrics(); c.drawText(text, iconView.getWidth() / 2f, (iconView.getHeight() - fm.ascent - fm.descent) / 2f, paint); }
                    finally { c.restoreToCount(save); }
                    p.setResult(null);
                } catch (Throwable t) { Lx.e("Surface: overflow count", t); }
            }
        });
        Lx.i("Surface: notification icon hooks ready");
    }

    /** Setting changed: let every known icon row recompute its overflow (nubia-ly NotificationIconBackend.refresh). */
    private static void refreshIcons() {
        if (iconOwners.isEmpty()) return;
        try {
            if (mainHandler == null) mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> {
                for (ViewGroup owner : new ArrayList<>(iconOwners.keySet())) {
                    try { if (iconsEligible(owner)) { XposedHelpers.callMethod(owner, "updateState"); owner.requestLayout(); owner.invalidate(); } } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    // ============================================================================================= volume sliders

    private static final class RowEntry {
        final WeakReference<Object> dialog, row;
        RowEntry(Object dialog, Object row) { this.dialog = new WeakReference<>(dialog); this.row = new WeakReference<>(row); }
    }
    private static final Map<Object, Boolean> volumeDialogs = new WeakHashMap<>();
    private static final Map<SeekBar, RowEntry> volumeRows = new WeakHashMap<>();

    private static void volume(ClassLoader cl) {
        Class<?> dialog = H.cls(cl, VOLUME_DIALOG), base = H.cls(cl, VOLUME_BASE), row = H.cls(cl, VOLUME_ROW), slider = H.cls(cl, VOLUME_SLIDER);
        if (dialog == null || base == null || row == null || slider == null) return;
        try {
            for (String f : new String[]{"mLeftPadding", "mTopPadding", "mLength", "mTrackSize", "mRadii"}) slider.getDeclaredField(f);
            row.getDeclaredField("slider"); row.getDeclaredField("view"); base.getDeclaredField("mRows");
        } catch (Throwable t) { Lx.w("Surface: volume slider geometry fields missing (" + t + "), volume style disabled"); return; }
        XC_MethodHook discover = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !main() || !p.thisObject.getClass().getName().equals(VOLUME_DIALOG)) return;
                try {
                    volumeDialogs.put(p.thisObject, Boolean.TRUE); scanRows(p.thisObject);
                    S cur = s();
                    for (Map.Entry<SeekBar, RowEntry> e : new ArrayList<>(volumeRows.entrySet()))
                        if (e.getValue().dialog.get() == p.thisObject) { if (cur.volCustom) oneUiGeometry(e.getKey()); else stockGeometry(e.getKey(), e.getValue()); }
                    if (cur.volLayout && volStockFields.containsKey(p.thisObject)) volLayout(p.thisObject);   // rows added since the last layout
                } catch (Throwable t) { Lx.e("Surface: volume rows", t); }
            }
        };
        H.hook(H.find(dialog, "initRow", row, int.class, int.class, int.class, boolean.class, boolean.class), discover);
        H.hook(H.find(dialog, "showH", int.class, boolean.class, int.class), discover);
        // side and distance from the edge: RedMagic sets the window to RIGHT|CENTER_VERTICAL at the cut-out inset
        // (MfvVolumeDialogImpl.updateWindowParams); its show / hide slide follows isWindowGravityLeft()
        H.hook(H.find(dialog, "updateWindowParams", android.view.WindowManager.LayoutParams.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !main() || !(p.args[0] instanceof android.view.WindowManager.LayoutParams)) return;
                try { volSide((android.view.WindowManager.LayoutParams) p.args[0], p.thisObject); } catch (Throwable t) { Lx.e("Surface: volume side", t); }
            }
        });
        H.hook(H.find(base, "isWindowGravityLeft"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !main() || !p.thisObject.getClass().getName().equals(VOLUME_DIALOG)) return;
                if (s().one.left) p.setResult(Boolean.TRUE);
            }
        });
        // position up / down: the pop-up window's y from RedMagic's centred spot (gravity RIGHT|CENTER_VERTICAL)
        H.hook(H.find(dialog, "showH", int.class, boolean.class, int.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!main()) return;
                try { volPosition(p.thisObject); } catch (Throwable t) { Lx.e("Surface: volume position", t); }
            }
        });
        // how long it stays: VolumeDialogImpl.computeTimeoutH (RedMagic's rescheduleTimeout goes through it)
        H.hook(H.find(base, "computeTimeoutH"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !main()) return;
                int ms = s().one.timeoutMs;
                if (ms > 0 && p.getResult() instanceof Integer) p.setResult(ms);
            }
        });
        H.hook(H.find(slider, "onDraw", Canvas.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(final MethodHookParam p) {
                S cur = s();
                int style = cur.volume;
                if ((style <= 0 && !cur.volCustom) || !main() || !(p.thisObject instanceof SeekBar)) return;
                SeekBar sb = (SeekBar) p.thisObject;
                RowEntry entry = volumeRows.get(sb);
                if (!rowEligible(sb, entry)) return;
                if (cur.volCustom) {
                    try { if (drawOneUiVolume(sb, entry, (Canvas) p.args[0])) p.setResult(null); } catch (Throwable t) { Lx.e("Surface: volume bar draw", t); }
                    return;
                }
                try {
                    float left = ((Number) XposedHelpers.getObjectField(sb, "mLeftPadding")).floatValue(), top = ((Number) XposedHelpers.getObjectField(sb, "mTopPadding")).floatValue();
                    float width = ((Number) XposedHelpers.getObjectField(sb, "mLength")).floatValue(), height = ((Number) XposedHelpers.getObjectField(sb, "mTrackSize")).floatValue();
                    if (!Float.isFinite(left) || !Float.isFinite(top) || !Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0) return;
                    float[] radii = (float[]) XposedHelpers.getObjectField(sb, "mRadii");
                    if (radii == null) { radii = new float[8]; Arrays.fill(radii, height / 2); }
                    if (SurfaceArt.drawVolume(style, (Canvas) p.args[0], new RectF(left, top, left + width, top + height), radii, density(sb),
                            () -> XposedBridge.invokeOriginalMethod(p.method, p.thisObject, p.args))) p.setResult(null);
                } catch (Throwable t) { Lx.e("Surface: volume draw", t); }
            }
        });
        // One UI 8 panel size: the column width ZTE uses for collapsed rows, then the views it does not size itself
        Method rowsWidth = H.find(dialog, "updateRowsWidth");
        if (rowsWidth != null) {
            H.hook(rowsWidth, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!main()) return;
                    try { volFields(p.thisObject); } catch (Throwable t) { Lx.e("Surface: volume column", t); }
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable() || !main()) return;
                    try { volLayout(p.thisObject); } catch (Throwable t) { Lx.e("Surface: volume layout", t); }
                }
            });
        } else Lx.w("Surface: updateRowsWidth missing, One UI volume size stays stock");
        // the blurred column and ringer button take the bar's shape
        Class<?> bg = H.cls(cl, VOLUME_BG);
        Method rects = bg == null ? null : H.find(bg, "setRects", RectF.class, RectF.class, RectF.class);
        if (rects != null) {
            H.hook(rects, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    S cur = s();
                    if (!cur.volOneUi) return;
                    try { volNarrow(p.args, cur.one); } catch (Throwable t) { Lx.e("Surface: volume panel width", t); }
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    S cur = s();
                    if (p.hasThrowable() || !cur.volCustom || OneUiVolumeArt.panelRadii(cur.one, 40, 1f) == null) return;
                    try { volPanelShape(p.thisObject, cur.one); } catch (Throwable t) { Lx.e("Surface: volume panel shape", t); }
                }
            });
        }
        Lx.i("Surface: volume hooks ready");
    }
    private static void scanRows(Object dialog) {
        Object value = XposedHelpers.getObjectField(dialog, "mRows");
        if (!(value instanceof List) || ((List<?>) value).size() > 32) return;
        volumeRows.entrySet().removeIf(e -> e.getValue().dialog.get() == dialog);
        for (Object row : (List<?>) value) {
            if (row == null || !row.getClass().getName().equals(VOLUME_ROW)) continue;
            Object candidate = XposedHelpers.getObjectField(row, "slider");
            if (candidate instanceof SeekBar && candidate.getClass().getName().equals(VOLUME_SLIDER)) volumeRows.put((SeekBar) candidate, new RowEntry(dialog, row));
        }
    }
    private static boolean rowEligible(SeekBar slider, RowEntry entry) {
        if (slider == null || entry == null || !slider.isAttachedToWindow()) return false;
        Object dialog = entry.dialog.get(), row = entry.row.get();
        if (dialog == null || row == null || !dialog.getClass().getName().equals(VOLUME_DIALOG)) return false;
        try {
            Object list = XposedHelpers.getObjectField(dialog, "mRows");
            if (!(list instanceof List) || ((List<?>) list).size() > 32 || !((List<?>) list).contains(row) || XposedHelpers.getObjectField(row, "slider") != slider) return false;
            Object root = XposedHelpers.getObjectField(row, "view");
            View current = slider;
            for (int depth = 0; depth < 12 && current != null; depth++) { if (current == root) return true; current = parentView(current); }
            return false;
        } catch (Throwable t) { return false; }
    }

    // ------------------------------------------------------------------------------------------- One UI 8 volume panel

    /** slider -> {stock mTrackSize}, its stock corner radii, and the row icon we moved */
    private static final Map<SeekBar, int[]> volStockSize = new WeakHashMap<>();
    private static final Map<SeekBar, float[]> volStockRadii = new WeakHashMap<>();
    private static final Map<View, Boolean> volMovedIcons = new WeakHashMap<>();

    private static int dp(View v, float dp) { return Math.round(dp * density(v)); }

    /**
     * ZTE's row (volume_dialog_row_mfv): a ColorSeekBarZTE (20 dp thick; its measured height is its track) rotated 270
     * degrees in a 56 dp column, a thumb dot, and the stream icon under it. One UI 8: the bar is as thick as the chosen
     * width (the column is 4 dp wider, see volFields), corners per the chosen shape. The cached track path is dropped.
     */
    private static void oneUiGeometry(SeekBar sb) {
        View frame = parentView(sb);
        OneUiVolumeArt.Look l = s().one;
        int column = frame != null && frame.getWidth() > 0 ? frame.getWidth() : dp(sb, 56);
        int cur = XposedHelpers.getIntField(sb, "mTrackSize");
        if (!volStockSize.containsKey(sb)) {
            volStockSize.put(sb, new int[]{cur});
            Object r = XposedHelpers.getObjectField(sb, "mRadii");
            volStockRadii.put(sb, r instanceof float[] ? ((float[]) r).clone() : null);
        }
        int stockThick = volStockSize.get(sb)[0];
        int want = !l.oneUi && l.width == 0 && stockThick > 0 ? stockThick      // RedMagic's own thickness
                : Math.max(dp(sb, 4), Math.min(dp(sb, OneUiVolumeArt.thickDp(l)), column - dp(sb, 4)));
        if (cur == want) return;
        XposedHelpers.setIntField(sb, "mTrackSize", want);
        XposedHelpers.setObjectField(sb, "mRadii", OneUiVolumeArt.barRadii(l, want, density(sb)));
        XposedHelpers.setObjectField(sb, "mPath", null);
        sb.requestLayout(); sb.invalidate();
    }
    private static void stockGeometry(SeekBar sb, RowEntry entry) {
        int[] size = volStockSize.remove(sb);
        float[] radii = volStockRadii.remove(sb);
        if (size != null) {
            XposedHelpers.setIntField(sb, "mTrackSize", size[0]);
            if (radii != null) XposedHelpers.setObjectField(sb, "mRadii", radii);
            XposedHelpers.setObjectField(sb, "mPath", null);
            sb.requestLayout(); sb.invalidate();
        }
        Object row = entry == null ? null : entry.row.get();
        Object icon = row == null ? null : XposedHelpers.getObjectField(row, "icon");
        if (icon instanceof ImageView && volMovedIcons.remove(icon) != null) { ((View) icon).setTranslationY(0f); ((ImageView) icon).setImageTintList(null); }
    }

    // --- panel size -------------------------------------------------------------------------------------------------

    /** dialog -> stock {mRowWidth, mDialogMiniWidthWithPadding, mDialogMiniWidthWithPaddingTowPanel} */
    private static final Map<Object, int[]> volStockFields = new WeakHashMap<>();
    /** view -> stock {layout width, layout height, min width, min height, top margin} */
    private static final Map<View, int[]> volStockLp = new WeakHashMap<>();
    private static final int KEEP = Integer.MIN_VALUE;

    /**
     * Before updateRowsWidth. RedMagic sizes the collapsed column (56 dp) itself and its rows only stay centred at that
     * width, so the column keeps its stock width and the narrower One UI panel is drawn instead (volNarrow). This only
     * puts the stock width back where an older build changed it.
     */
    private static void volFields(Object dlg) {
        int[] st = volStockFields.get(dlg);
        if (st == null) {
            st = new int[]{XposedHelpers.getIntField(dlg, "mRowWidth"), XposedHelpers.getIntField(dlg, "mDialogMiniWidthWithPadding"),
                    XposedHelpers.getIntField(dlg, "mDialogMiniWidthWithPaddingTowPanel")};
            if (st[0] <= 0) return;
            volStockFields.put(dlg, st);
        }
        if (XposedHelpers.getIntField(dlg, "mRowWidth") == st[0]) return;
        XposedHelpers.setIntField(dlg, "mRowWidth", st[0]);
        XposedHelpers.setIntField(dlg, "mDialogMiniWidthWithPadding", st[1]);
        XposedHelpers.setIntField(dlg, "mDialogMiniWidthWithPaddingTowPanel", st[2]);
    }

    /** Display density of the volume dialog (the background drawable has no view of its own). */
    private static volatile float volDensity;

    /**
     * Before ClipRectsDrawable.setRects: the blurred column (and the second column) is drawn as wide as the chosen panel
     * width, centred on RedMagic's column, and the ringer button as a circle of that width. The wide expanded panel
     * (wider than 64 dp) is left alone.
     */
    private static void volNarrow(Object[] args, OneUiVolumeArt.Look l) {
        float d = volDensity > 0 ? volDensity : android.content.res.Resources.getSystem().getDisplayMetrics().density;
        float col = OneUiVolumeArt.columnDp(l) * d;
        for (int i = 0; i < 3 && i < args.length; i++) {
            if (!(args[i] instanceof RectF)) continue;
            RectF r = (RectF) args[i];
            if (r.isEmpty() || r.width() > 64 * d || r.width() <= col) continue;
            RectF n = new RectF(r);
            float dx = (r.width() - col) / 2f;
            n.left += dx; n.right -= dx;
            if (i == 2 && r.height() <= 64 * d) { float dy = Math.max(0, (r.height() - col) / 2f); n.top += dy; n.bottom -= dy; }
            args[i] = n;
        }
    }

    /**
     * After updateRowsWidth: makes the panel taller (the slider, its frame, the row, the column, the second column and
     * the dialog by the same amount), lets the slider fill its frame, folds the empty icon slot into the bar when the icon
     * sits inside it, and narrows the expand button and (collapsed) the ringer button to the column. Everything goes
     * back to stock when the One UI panel is off.
     */
    private static final Map<android.view.Window, Integer> volStockY = new java.util.WeakHashMap<>();
    private static int lastPos = Integer.MIN_VALUE;

    private static void volPosition(Object dlg) {
        Object wo = XposedHelpers.getObjectField(dlg, "mWindow");
        if (!(wo instanceof android.view.Window)) return;
        android.view.Window w = (android.view.Window) wo;
        android.view.WindowManager.LayoutParams lp = w.getAttributes();
        Integer stock = volStockY.get(w);
        if (stock == null) { stock = lp.y; volStockY.put(w, stock); }
        int off = s().one.posDp;
        int want = stock + Math.round(off * w.getContext().getResources().getDisplayMetrics().density);
        int g = lp.gravity, x = lp.x;
        volSide(lp, dlg);
        if (lp.y != want || lp.gravity != g || lp.x != x) {
            lp.y = want;
            w.setAttributes(lp);
            if (lastPos != off) { lastPos = off; Lx.i("Surface: volume panel moved " + off + " dp (window y " + stock + " -> " + want + ", gravity " + lp.gravity + ")"); }
        }
    }

    /** RedMagic's own window gravity / x (right edge, cut-out inset), per dialog, to go back to. */
    private static final Map<Object, int[]> volStockSide = new java.util.WeakHashMap<>();
    private static String lastSide = "";

    /** Panel side (left / right) and distance from that edge on the pop-up window's layout params. */
    private static void volSide(android.view.WindowManager.LayoutParams lp, Object dlg) {
        OneUiVolumeArt.Look l = s().one;
        int[] st = volStockSide.get(dlg);
        if (st == null) {
            st = new int[]{lp.gravity, lp.x};
            volStockSide.put(dlg, st);
        }
        if (!l.left && l.marginDp <= 0) {
            lp.gravity = st[0]; lp.x = st[1];
            return;
        }
        android.content.Context ctx = (android.content.Context) XposedHelpers.getObjectField(dlg, "mContext");
        float density = ctx.getResources().getDisplayMetrics().density;
        int inset = 0;
        try {
            android.view.DisplayCutout cut = ctx.getDisplay().getCutout();
            if (cut != null) inset = l.left ? cut.getSafeInsetLeft() : cut.getSafeInsetRight();
        } catch (Throwable ignored) { }
        int vertical = st[0] & android.view.Gravity.VERTICAL_GRAVITY_MASK;
        lp.gravity = vertical | (l.left ? android.view.Gravity.LEFT : android.view.Gravity.RIGHT);
        lp.x = inset + Math.round(l.marginDp * density);
        String sig = l.left + ":" + l.marginDp;
        if (!sig.equals(lastSide)) { lastSide = sig; Lx.i("Surface: volume panel on the " + (l.left ? "left" : "right") + ", " + l.marginDp + " dp from the edge"); }
    }

    private static void volLayout(Object dlg) {
        S cur = s();
        boolean on = cur.volLayout;
        Object dvo = XposedHelpers.getObjectField(dlg, "mDialogView");
        if (!(dvo instanceof View)) return;
        View dv = (View) dvo;
        int[] stF = volStockFields.get(dlg);
        int stockCol = stF != null ? stF[0] : dp(dv, 56);
        int col = on ? XposedHelpers.getIntField(dlg, "mRowWidth") : stockCol;
        boolean expanded = XposedHelpers.getBooleanField(dlg, "mExpanded");
        int extra = 0;
        if (on) {
            extra = dp(dv, OneUiVolumeArt.extraDp(cur.one));
            int stockH = volStockLp.containsKey(dv) ? volStockLp.get(dv)[1] : dv.getLayoutParams() != null ? dv.getLayoutParams().height : -1;
            if (stockH <= 0) stockH = dp(dv, 326);
            int room = dv.getResources().getDisplayMetrics().heightPixels - stockH - dp(dv, 64);
            extra = Math.max(0, Math.min(extra, room));
        }
        StringBuilder geo = new StringBuilder();
        boolean inside = on && OneUiVolumeArt.iconInside(cur.one);
        Object list = XposedHelpers.getObjectField(dlg, "mRows");
        if (list instanceof List && ((List<?>) list).size() <= 32) {
            for (Object row : (List<?>) list) {
                if (row == null) continue;
                Object so = XposedHelpers.getObjectField(row, "slider"), io = XposedHelpers.getObjectField(row, "icon");
                if (!(so instanceof SeekBar) || !so.getClass().getName().equals(VOLUME_SLIDER)) continue;
                View sb = (View) so, frame = parentView(sb), inner = frame == null ? null : parentView(frame);
                if (frame == null || inner == null) continue;
                View icon = io instanceof View && parentView((View) io) == inner ? (View) io : null;
                int fold = 0;
                if (inside && icon != null && icon.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    int[] ist = stockLp(icon);
                    ViewGroup.MarginLayoutParams ilp = (ViewGroup.MarginLayoutParams) icon.getLayoutParams();
                    fold = Math.max(0, (ist[1] > 0 ? ist[1] : dp(dv, 24)) + ilp.bottomMargin - dp(dv, 6));
                }
                int[] fst = stockLp(frame), sst = stockLp(sb);
                int slider = on && fst[1] > 0 && sst[0] > 0 ? fst[1] + extra + fold - dp(dv, 6) - sst[0] : 0;
                fit(sb, on ? slider : 0, KEEP, -1, -1, 0);
                fit(frame, KEEP, extra + fold, -1, -1, 0);
                fit(inner, KEEP, extra, -1, -1, 0);
                View root = viewField(row, "view");
                if (on && sb.isShown()) geo.append(" row[root ").append(root == null ? -1 : root.getWidth()).append('/').append(root == null || root.getLayoutParams() == null ? -9 : root.getLayoutParams().width)
                        .append(" inner ").append(inner.getLeft()).append('+').append(inner.getWidth()).append(" frame ").append(frame.getWidth()).append('x').append(frame.getHeight())
                        .append(" slider ").append(sb.getLeft()).append(',').append(sb.getTop()).append(' ').append(sb.getWidth()).append('x').append(sb.getHeight()).append(']');
                if (icon != null) fit(icon, KEEP, KEEP, -1, -1, -fold);
                if (!on && icon != null) { icon.setTranslationY(0f); if (volMovedIcons.remove(icon) != null && icon instanceof ImageView) ((ImageView) icon).setImageTintList(null); }
                else if (on && !inside && icon != null && volMovedIcons.remove(icon) != null) { icon.setTranslationY(0f); if (icon instanceof ImageView) ((ImageView) icon).setImageTintList(null); }
            }
        }
        View mainV = viewField(dlg, "mDialogMain"), rows = viewField(dlg, "mDialogRowsView"), rows2 = viewField(dlg, "mDialogRowsView2");
        // heights only; widths stay RedMagic's (older builds changed minimum widths and the buttons: put those back)
        fit(mainV, KEEP, extra, -1, -1, 0);
        fit(rows, KEEP, KEEP, -1, -1, 0);
        fit(rows2, KEEP, extra, -1, -1, 0);
        fit(dv, KEEP, extra, -1, -1, 0);
        fit(viewField(dlg, "mExpandButton"), 0, KEEP, -1, -1, 0);
        fit(viewField(dlg, "mRinger"), KEEP, 0, -1, -1, 0);
        volDensity = density(dv);
        if (on) {
            geo.insert(0, "Surface: volume geometry d=" + density(dv) + " col=" + col + " stockCol=" + stockCol + " expanded=" + expanded + " extra=" + extra
                    + " rows=" + (rows == null ? -1 : rows.getWidth()) + " main=" + (mainV == null ? -1 : mainV.getWidth()) + " dialog=" + dv.getWidth());
            String g = geo.toString();
            if (!g.equals(lastGeo)) { lastGeo = g; Lx.i(g); }
        }
    }

    private static String lastGeo = "";

    private static View viewField(Object o, String name) {
        try { Object v = XposedHelpers.getObjectField(o, name); return v instanceof View ? (View) v : null; } catch (Throwable t) { return null; }
    }

    private static int[] stockLp(View v) {
        int[] st = volStockLp.get(v);
        if (st == null) {
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            st = new int[]{lp == null ? 0 : lp.width, lp == null ? 0 : lp.height, v.getMinimumWidth(), v.getMinimumHeight(),
                    lp instanceof ViewGroup.MarginLayoutParams ? ((ViewGroup.MarginLayoutParams) lp).topMargin : 0};
            volStockLp.put(v, st);
        }
        return st;
    }

    /**
     * Sets a fixed layout width / height to stock + dw / dh (KEEP = leave it; a size ZTE sets itself is never touched),
     * the minimum width / height to minW / minH (-1 = stock) and the top margin to stock + dTop. A view that was never
     * changed and needs no change is left alone.
     */
    private static void fit(View v, int dw, int dh, int minW, int minH, int dTop) {
        if (v == null || v.getLayoutParams() == null) return;
        boolean wants = (dw != KEEP && dw != 0) || (dh != KEEP && dh != 0) || minW >= 0 || minH >= 0 || dTop != 0;
        if (!wants && !volStockLp.containsKey(v)) return;
        int[] st = stockLp(v);
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        boolean changed = false;
        if (dw != KEEP && st[0] > 0) { int w = Math.max(1, st[0] + dw); if (lp.width != w) { lp.width = w; changed = true; } }
        if (dh != KEEP && st[1] > 0) { int h = Math.max(1, st[1] + dh); if (lp.height != h) { lp.height = h; changed = true; } }
        int mw = minW >= 0 ? minW : st[2], mh = minH >= 0 ? minH : st[3];
        if (v.getMinimumWidth() != mw) { v.setMinimumWidth(mw); changed = true; }
        if (v.getMinimumHeight() != mh) { v.setMinimumHeight(mh); changed = true; }
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
            if (m.topMargin != st[4] + dTop) { m.topMargin = st[4] + dTop; changed = true; }
        }
        if (changed) v.setLayoutParams(lp);
    }

    /** The blurred column, second column and ringer button get the bar's corners (the wide expanded panel stays stock). */
    private static void volPanelShape(Object drawable, OneUiVolumeArt.Look l) {
        float d = android.content.res.Resources.getSystem().getDisplayMetrics().density;
        Path all = (Path) XposedHelpers.getObjectField(drawable, "path");
        String[][] parts = {{"rowsRect", "rowsPath"}, {"rows2Rect", "rows2Path"}, {"ringerRect", "ringerPath"}};
        boolean any = false;
        for (String[] part : parts) {
            RectF r = (RectF) XposedHelpers.getObjectField(drawable, part[0]);
            if (r == null || r.isEmpty() || r.width() > 64 * d) continue;
            float[] radii = OneUiVolumeArt.panelRadii(l, r.width(), d);
            if (radii == null) return;
            Path p = (Path) XposedHelpers.getObjectField(drawable, part[1]);
            p.reset();
            p.addRoundRect(r, radii, Path.Direction.CW);
            any = true;
        }
        if (!any || all == null) return;
        all.reset();
        for (String[] part : parts) {
            RectF r = (RectF) XposedHelpers.getObjectField(drawable, part[0]);
            if (r != null && !r.isEmpty()) all.addPath((Path) XposedHelpers.getObjectField(drawable, part[1]));
        }
    }

    // --- drawing ----------------------------------------------------------------------------------------------------

    /**
     * Track, fill (a circle at 0 % that grows to the whole bar at 100 %, from the start of the slider: the bottom, as
     * the bar is rotated 270 degrees), colours, shape and level number as chosen. The row icon is moved into that circle
     * and tinted to read over the fill (or left under the bar). Returns false (stock drawing) when the view is not the
     * shape we expect.
     */
    private static boolean drawOneUiVolume(SeekBar sb, RowEntry entry, Canvas canvas) {
        if (!volStockSize.containsKey(sb)) oneUiGeometry(sb);
        float left = Math.max(0, XposedHelpers.getIntField(sb, "mLeftPadding")), top = Math.max(0, XposedHelpers.getIntField(sb, "mTopPadding"));
        float length = XposedHelpers.getIntField(sb, "mLength"), thick = XposedHelpers.getIntField(sb, "mTrackSize");
        if (length <= 0 || thick <= 0 || length < thick) return false;
        float min = XposedHelpers.getFloatField(sb, "mMin"), max = XposedHelpers.getFloatField(sb, "mMax"), progress = XposedHelpers.getFloatField(sb, "mProgress");
        float fraction = max > min ? (progress - min) / (max - min) : 0f;
        boolean dark = dark(sb);
        OneUiVolumeArt.Look l = s().one;
        int[] fill = OneUiVolumeArt.fill(sb.getContext(), l, dark);
        OneUiVolumeArt.drawBar(canvas, new RectF(left, top, left + length, top + thick), fraction, l, fill, dark, sb.isEnabled(), density(sb));
        if (OneUiVolumeArt.iconInside(l)) placeIcon(sb, entry, thick, left, OneUiVolumeArt.over(fill[0]));
        return true;
    }

    /** Puts the row's stream icon (the mute button) into the start circle of the slider and tints it. */
    private static void placeIcon(SeekBar sb, RowEntry entry, float thick, float left, int tint) {
        Object row = entry == null ? null : entry.row.get();
        Object o = row == null ? null : XposedHelpers.getObjectField(row, "icon");
        if (!(o instanceof ImageView)) return;
        ImageView icon = (ImageView) o;
        View frame = parentView(sb);
        if (frame == null || parentView(icon) != parentView(frame)) return;
        float rot = ((sb.getRotation() % 360f) + 360f) % 360f;
        if (Math.abs(rot - 270f) > 1f) return;                       // only the stock bottom-to-top bar
        // rotated 270 degrees about its centre: the slider's start (left) edge is at the bottom of what we see
        float centre = frame.getTop() + sb.getTop() + sb.getHeight() / 2f;
        float startCircle = centre + sb.getWidth() / 2f - left - thick / 2f;
        float want = startCircle - (icon.getTop() + icon.getHeight() / 2f);
        if (Math.abs(icon.getTranslationY() - want) > 0.5f) icon.setTranslationY(want);
        volMovedIcons.put(icon, Boolean.TRUE);
        android.content.res.ColorStateList cur = icon.getImageTintList();
        if (cur == null || cur.getDefaultColor() != tint) icon.setImageTintList(android.content.res.ColorStateList.valueOf(tint));
        if (icon.getAlpha() != 1f) icon.setAlpha(1f);
    }

    // ============================================================================================= power menu

    private static final Map<Dialog, Boolean> powerDialogs = new WeakHashMap<>();

    private static void power(ClassLoader cl) {
        Class<?> owner = H.cls(cl, POWER_DIALOG);
        if (owner == null) return;
        try { owner.getDeclaredField("mContentView"); owner.getDeclaredField("mPowerContent"); owner.getDeclaredField("mResetContent"); }
        catch (Throwable t) { Lx.w("Surface: power dialog fields missing (" + t + "), power-menu style disabled"); return; }
        H.hook(H.find(owner, "initView"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !main() || !(p.thisObject instanceof Dialog)) return;
                powerDialogs.put((Dialog) p.thisObject, Boolean.TRUE);
                if (s().pwrOneUi) { try { oneUiPower(XposedHelpers.getObjectField(p.thisObject, "mContentView")); } catch (Throwable t) { Lx.e("Surface: One UI power menu", t); } }
                try { powerExtras(p.thisObject); } catch (Throwable t) { Lx.e("Surface: power menu position / layout / dim", t); }
            }
        });
        // ZTE's software Gauss blur sets the blurred wallpaper (with a 20 % black filter) as the background later, async.
        int blurHooks = H.hookAll(owner, "lambda$applyBlur$5", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                int dim = s().pwrDim;
                if (dim < 0 || p.hasThrowable() || !main()) return;
                try { Object c = XposedHelpers.getObjectField(p.thisObject, "mContentView"); if (c instanceof View) powerDim((View) c, dim); }
                catch (Throwable t) { Lx.e("Surface: power blur dim", t); }
            }
        });
        if (blurHooks == 0) Lx.i("Surface: power blur callback not found, dim applies to the window-blur path only");
        // Decorates the native circular ImageButtons after their backgrounds and before their glyphs.
        H.hook(H.find(ImageView.class, "onDraw", Canvas.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                int style = s().power;
                if (style <= 0 || s().pwrOneUi || powerDialogs.isEmpty() || !main() || !(p.thisObject instanceof ImageView)) return;
                ImageView view = (ImageView) p.thisObject;
                try {
                    if (!powerEligible(view)) return;
                    float diameter = Math.min(view.getWidth(), view.getHeight());
                    float left = (view.getWidth() - diameter) / 2f, top = (view.getHeight() - diameter) / 2f;
                    RectF bounds = new RectF(left, top, left + diameter, top + diameter);
                    float[] corners = new float[8]; Arrays.fill(corners, diameter / 2);
                    SurfaceArt.drawCard(style, (Canvas) p.args[0], bounds, corners, density(view));
                } catch (Throwable t) { Lx.e("Surface: power draw", t); }
            }
        });
        Lx.i("Surface: power menu hooks ready");
    }
    /**
     * One UI 8 power menu over ZTE's (global_dialog_simplified_*): the round ImageButtons (tag "image_button") get a solid
     * button in the chosen colours (One UI: red for Power off, green for Restart, grey for the rest), shape and size
     * (72 dp instead of 60 by default); the names under them (tag "text_view") get the chosen label style. ZTE's blur
     * and its tap-twice confirmation stay.
     */
    private static void oneUiPower(Object content) {
        if (!(content instanceof ViewGroup)) return;
        OneUiVolumeArt.Look l = s().one;
        List<View> all = new ArrayList<>();
        collect((ViewGroup) content, all);
        for (View v : all) {
            Object tag = v.getTag();
            if (v instanceof ImageButton && "image_button".equals(tag)) {
                String id = "";
                try { id = v.getResources().getResourceEntryName(v.getId()); } catch (Throwable ignored) { }
                int[] colors = OneUiVolumeArt.pwrColors(v.getContext(), l, id);
                int size = dp(v, OneUiVolumeArt.pwrSizeDp(l));
                android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                float corner = OneUiVolumeArt.pwrCorner(l, size);
                if (corner < 0) shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                else { shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE); shape.setCornerRadius(corner); }
                shape.setColor(colors[0]);
                if (colors[2] != 0) shape.setStroke(Math.max(1, dp(v, 1)), colors[2]);
                v.setBackground(shape);
                v.setAlpha(1f);
                ((ImageButton) v).setImageTintList(android.content.res.ColorStateList.valueOf(colors[1]));
                ViewGroup.LayoutParams lp = v.getLayoutParams();
                if (lp != null && (lp.width != size || lp.height != size)) { lp.width = size; lp.height = size; v.setLayoutParams(lp); }
                int pad = Math.round(size * 0.28f);
                v.setPadding(pad, pad, pad, pad);
                ((ImageButton) v).setScaleType(ImageView.ScaleType.FIT_CENTER);
            } else if (v instanceof android.widget.TextView && "text_view".equals(tag)) {
                android.widget.TextView t = (android.widget.TextView) v;
                t.setTypeface(Typeface.create(t.getTypeface(), OneUiVolumeArt.pwrLabelBold(l) ? Typeface.BOLD : Typeface.NORMAL));
                t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, OneUiVolumeArt.pwrLabelSp(l));
                t.setTextColor(OneUiVolumeArt.pwrLabelColor(l));
                ViewGroup.LayoutParams lp = t.getLayoutParams();
                if (lp instanceof ViewGroup.MarginLayoutParams) { ((ViewGroup.MarginLayoutParams) lp).topMargin = dp(t, 12); t.setLayoutParams(lp); }
            }
        }
    }
    /**
     * Position, button layout and dim for ZTE's power menu (any style). mLlActions is a RelativeLayout holding one
     * RelativeLayout per action (button + label) placed with sibling rules; the layout option swaps those rules for plain
     * top/left margins inside the same parent, so ZTE's tap-twice animation (which centres the picked action from its
     * getLeft / getTop) still lines up. The position moves mLlActions' parent so the power-off-alarm block follows.
     */
    private static void powerExtras(Object dialog) {
        S s = s();
        Object content = XposedHelpers.getObjectField(dialog, "mContentView");
        if (!(content instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) content;
        if (s.pwrDim >= 0) powerDim(root, s.pwrDim);
        if (s.pwrPosDp == 0 && s.pwrLayout <= 0) return;
        Object acts = null;
        try { acts = XposedHelpers.getObjectField(dialog, "mLlActions"); } catch (Throwable ignored) { }
        if (!(acts instanceof ViewGroup)) { Lx.w("Surface: power actions (mLlActions) not found, position / layout skipped"); return; }
        ViewGroup host = (ViewGroup) acts;
        if (s.pwrLayout > 0) powerLayout(host, SurfaceFeatures.PWR_LAYOUT_COLS[s.pwrLayout]);
        if (s.pwrPosDp != 0) {
            View mover = host.getParent() instanceof View && host.getParent() != root ? (View) host.getParent() : host;
            mover.setTranslationY(dp(mover, s.pwrPosDp));
        }
    }

    /** Dim behind the power menu: the content view's background alpha (stock is a 20 % black ColorDrawable or the blurred bitmap). */
    private static void powerDim(View root, int pct) {
        int a = Math.max(0, Math.min(100, pct)) * 255 / 100;
        Drawable bg = root.getBackground();
        if (bg instanceof android.graphics.drawable.BitmapDrawable) {
            if (a == 0) bg.clearColorFilter();
            else bg.setColorFilter(new android.graphics.PorterDuffColorFilter(a << 24, PorterDuff.Mode.SRC_OVER));
            root.invalidate();
        } else {
            int rgb = bg instanceof android.graphics.drawable.ColorDrawable ? ((android.graphics.drawable.ColorDrawable) bg).getColor() & 0xFFFFFF : 0;
            root.setBackground(new android.graphics.drawable.ColorDrawable((a << 24) | rgb));
        }
    }

    private static final Map<View, int[]> powerHosts = new WeakHashMap<>();
    private static final Map<View, Boolean> powerPlaced = new WeakHashMap<>();

    /** cols: -1 = everything on one row, 1 = vertical list, n = grid of n columns. */
    private static void powerLayout(final ViewGroup host, int cols) {
        if (!(host instanceof RelativeLayout)) { Lx.w("Surface: power actions host is " + host.getClass().getName() + ", layout skipped"); return; }
        ViewGroup.LayoutParams hl = host.getLayoutParams();
        if (hl != null && (hl.height != ViewGroup.LayoutParams.WRAP_CONTENT || hl.width != ViewGroup.LayoutParams.MATCH_PARENT)) {
            hl.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            hl.width = ViewGroup.LayoutParams.MATCH_PARENT;
            host.setLayoutParams(hl);
        }
        List<View> items = powerItems(host);
        if (items.isEmpty()) { Lx.w("Surface: no power actions with image_button in " + host.getClass().getName() + ", layout skipped"); return; }
        for (View v : items) placePower(v, 0, 0);       // drop the sibling rules now; real margins land after the first layout
        int[] state = powerHosts.get(host);
        if (state != null) { state[0] = cols; return; }
        final int[] st = {cols};
        powerHosts.put(host, st);
        host.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            try { arrangePower(host, st[0]); } catch (Throwable e) { Lx.e("Surface: power layout", e); }
        });
    }
    private static List<View> powerItems(ViewGroup host) {
        List<View> items = new ArrayList<>();
        for (int i = 0; i < host.getChildCount(); i++) {
            View c = host.getChildAt(i);
            if (c.getVisibility() == View.GONE || !(c instanceof ViewGroup)) continue;
            ViewGroup g = (ViewGroup) c;
            for (int j = 0; j < g.getChildCount(); j++)
                if (g.getChildAt(j) instanceof ImageButton && "image_button".equals(g.getChildAt(j).getTag())) { items.add(c); break; }
        }
        return items;
    }
    private static void arrangePower(final ViewGroup host, int cols) {
        final List<View> items = powerItems(host);
        int n = items.size(), width = host.getWidth();
        if (n == 0 || width <= 0) return;
        int c = cols < 0 ? n : Math.max(1, Math.min(cols, n));
        int iw = 0, ih = 0;
        for (View v : items) { iw = Math.max(iw, v.getMeasuredWidth()); ih = Math.max(ih, v.getMeasuredHeight()); }
        if (iw <= 0 || ih <= 0) return;
        int gapX = dp(host, 28), gapY = dp(host, 24);
        final int[] xs = new int[n], ys = new int[n];
        boolean changed = false;
        for (int k = 0; k < n; k++) {
            int row = k / c, col = k % c, inRow = Math.min(c, n - row * c), gx = gapX;
            int rowW = inRow * iw + (inRow - 1) * gx;
            if (rowW > width && inRow > 1) { gx = Math.max(0, (width - inRow * iw) / (inRow - 1)); rowW = inRow * iw + (inRow - 1) * gx; }
            View v = items.get(k);
            xs[k] = Math.max(0, (width - rowW) / 2 + col * (iw + gx) + (iw - v.getMeasuredWidth()) / 2);
            ys[k] = row * (ih + gapY);
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (powerPlaced.get(v) == null || !(lp instanceof RelativeLayout.LayoutParams)
                    || ((RelativeLayout.LayoutParams) lp).leftMargin != xs[k] || ((RelativeLayout.LayoutParams) lp).topMargin != ys[k]) changed = true;
        }
        if (!changed) return;
        // Not inside the layout pass.
        host.post(() -> { for (int k = 0; k < items.size(); k++) placePower(items.get(k), xs[k], ys[k]); });
    }
    private static void placePower(View v, int x, int y) {
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        lp.leftMargin = x; lp.topMargin = y;
        v.setLayoutParams(lp);
        powerPlaced.put(v, Boolean.TRUE);
    }

    private static void collect(ViewGroup g, List<View> out) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            out.add(c);
            if (c instanceof ViewGroup) collect((ViewGroup) c, out);
        }
    }

    private static boolean powerEligible(ImageView view) {
        if (!view.isAttachedToWindow() || view.getClass() != ImageButton.class || !(view.getParent() instanceof RelativeLayout)) return false;
        RelativeLayout container = (RelativeLayout) view.getParent();
        String id;
        try { if (!"com.android.systemui".equals(view.getResources().getResourcePackageName(view.getId()))) return false; id = view.getResources().getResourceEntryName(view.getId()); } catch (Throwable absent) { return false; }
        String fieldName = id.equals("power") ? "mPowerContent" : id.equals("reset") ? "mResetContent" : null;
        if (fieldName == null) return false;
        for (Dialog dialog : new ArrayList<>(powerDialogs.keySet())) {
            if (!dialog.isShowing() || XposedHelpers.getObjectField(dialog, fieldName) != container) continue;
            Object root = XposedHelpers.getObjectField(dialog, "mContentView");
            if (!(root instanceof ViewGroup)) continue;
            for (View current = container; current != null; current = parentView(current)) if (current == root) return true;
        }
        return false;
    }
}
