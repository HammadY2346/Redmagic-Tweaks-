package com.hammad.redmagictweaks.xposed;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.hammad.redmagictweaks.QsFeatures;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * com.android.systemui — Quick Settings ported from nubia-ly: QsGridBackend (independent portrait / landscape
 * small-tile grids with extended row animators), QsLayoutBackend (large-tile arrangement, label position / lines,
 * brightness placement, sun icons), QsShapeBackend (tile corner shapes), QsOutlineBackend (tile finishes) and
 * BrightnessBackend (slider styles). nubia-ly switches these on/off through begin()/end() calls from its app; here
 * every hook reads the module settings (snapshot refreshed once per second) and the layout backends detect a
 * changed selection on the next shade layout, restore what they changed and re-apply — so everything is live.
 * Drawing lives in QsArt.
 */
final class QsHooks {
    private QsHooks() {}

    private static final String ROOT = "com.zte.mifavor.qs.";
    private static final String PAGER = ROOT + "MfvPagedTileLayout", PAGE = PAGER + "$TilePage", BASE = ROOT + "MfvTileLayout", PANEL = ROOT + "MfvQSPanel", ANIM = ROOT + "MfvQSAnimator";
    private static final String CONTENT = ROOT + "MfvQSContent", QUICK = ROOT + "MfvQuickQSPanel", HEADER = QUICK + "$HeaderTileLayout", CONTAINER = ROOT + "MfvQSContainerImpl", FRAGMENT = ROOT + "MfvQSFragment";
    private static final String DIMEN = "com.zte.utils.QsDimenUtils", DIMEN_COMPANION = DIMEN + "$Companion";
    private static final String LAYOUT_FEATURE = "com.zte.feature.qs.QSPanelLayoutFeature", PANEL_ADAPT = "com.zte.adapt.mifavor.qs.MfvQSPanelAdapt", LAYOUT_ADAPT = "com.zte.adapt.mifavor.qs.MfvTileLayoutAdapt";
    private static final String WIDE = "com.zte.qs.tileimpl.QSTileViewWide", CIRCLE = "com.zte.qs.tileimpl.QSTileViewCircle", TILE_BASE = "com.zte.qs.tileimpl.QSTileViewBase";
    private static final String SLIDER = "com.android.systemui.settings.brightness.ToggleSeekBar", CC_SLIDER = "com.zte.controlcenter.view.CCToggleSeekBar";
    private static final String SLIDER_OWNER = "com.zte.controlcenter.view.ToggleSliderView", CC_SLIDER_OWNER = "com.zte.controlcenter.view.CCBrightnessToggleSliderView";
    private static final String ICON_VIEW = "com.android.systemui.qs.tileimpl.QSIconViewImpl", TILE_STATE = "com.android.systemui.plugins.qs.QSTile$State", THEME = "com.zte.feature.qs.theme.MultiThemeFeature";
    // Control Center (split shade)
    private static final String CC_CONTROLLER = "com.zte.controlcenter.ControlPanelController", CC_TILE_LAYOUT = "com.zte.controlcenter.view.ControlCenterTileLayout", CC_TOP_PANEL = "com.zte.controlcenter.view.ControlCenterTopPanel";
    private static final String CC_VOLUME = "com.zte.controlcenter.view.CCVolumeSliderView", CC_HEADER = "com.zte.controlcenter.widget.CCHeaderView";

    // ------------------------------------------------------------------------------------------- settings snapshot

    static final class S {
        int cols, rows, landCols, landRows, top, labelPos, labelLines, brightnessPos, shapeSmall, shapeLarge, tileStyle, brightnessStyle;
        boolean noSun;
        // Control Center
        boolean ccEnable; int ccCols, ccVolumeStyle, ccHide;   // ccHide: bit mask, see CC_HIDE_KEYS order
        int ccGap;                                            // QsFeatures.CC_TILE_GAPS index
        int ccGapDp = -1;                                     // typed gap in dp, -1 = the preset
        int tileColor, sliderColor;                           // QsArt.PANEL_COLORS indexes (0 = stock)
        int[] tileCustom, sliderCustom;                       // custom colours {start, end} (ColorPick), or null
    }
    static final String[] CC_HIDE_KEYS = {QsFeatures.QS_CC_HIDE_SEARCH, QsFeatures.QS_CC_HIDE_EDIT, QsFeatures.QS_CC_HIDE_USER, QsFeatures.QS_CC_HIDE_POWER, QsFeatures.QS_CC_HIDE_SETTINGS, QsFeatures.QS_CC_HIDE_DATE};
    private static volatile S snap = new S();
    private static volatile long snapAt;

    static S s() {
        long now = System.currentTimeMillis();
        if (now - snapAt > 1000) {
            S n = new S();
            n.cols = Cfg.num(QsFeatures.QS_COLS, QsFeatures.cols(Cfg.choice(QsFeatures.QS_COLS)));
            n.rows = Cfg.num(QsFeatures.QS_ROWS, QsFeatures.rows(Cfg.choice(QsFeatures.QS_ROWS)));
            n.landCols = Cfg.num(QsFeatures.QS_LAND_COLS, QsFeatures.cols(Cfg.choice(QsFeatures.QS_LAND_COLS)));
            n.landRows = Cfg.num(QsFeatures.QS_LAND_ROWS, QsFeatures.rows(Cfg.choice(QsFeatures.QS_LAND_ROWS)));
            n.top = clamp(Cfg.choice(QsFeatures.QS_TOP_LAYOUT), 3);
            n.labelPos = clamp(Cfg.choice(QsFeatures.QS_LABEL_POS), 2);
            n.labelLines = clamp(Cfg.choice(QsFeatures.QS_LABEL_LINES), 3);
            n.brightnessPos = clamp(Cfg.choice(QsFeatures.QS_BRIGHTNESS_POS), 2);
            n.noSun = Cfg.on(QsFeatures.QS_BRIGHTNESS_NO_SUN);
            n.shapeSmall = QsArt.shapeId(Cfg.choice(QsFeatures.QS_SHAPE_SMALL));
            n.shapeLarge = QsArt.shapeId(Cfg.choice(QsFeatures.QS_SHAPE_LARGE));
            n.tileStyle = clamp(Cfg.choice(QsFeatures.QS_TILE_STYLE), QsArt.TILE_STYLES.length - 1);
            n.brightnessStyle = clamp(Cfg.choice(QsFeatures.QS_BRIGHTNESS_STYLE), QsArt.BRIGHTNESS_STYLES.length - 1);
            n.ccEnable = Cfg.on(QsFeatures.QS_CC_ENABLE);
            n.ccCols = Cfg.num(QsFeatures.QS_CC_COLS, QsFeatures.cols(Cfg.choice(QsFeatures.QS_CC_COLS)));
            n.ccVolumeStyle = clamp(Cfg.choice(QsFeatures.QS_CC_VOLUME_STYLE), QsArt.BRIGHTNESS_STYLES.length - 1);
            n.ccGap = QsFeatures.ccGap(Cfg.choice(QsFeatures.QS_CC_TILE_GAP));
            n.ccGapDp = Cfg.hasNum(QsFeatures.QS_CC_TILE_GAP) ? Math.max(0, Math.min(40, Cfg.num(QsFeatures.QS_CC_TILE_GAP, 0))) : -1;
            n.tileColor = clamp(Cfg.choice(QsFeatures.QS_TILE_COLOR), QsArt.PANEL_COLORS.length - 1);
            n.sliderColor = clamp(Cfg.choice(QsFeatures.QS_SLIDER_COLOR), QsArt.PANEL_COLORS.length - 1);
            n.tileCustom = Cfg.colorPair(QsFeatures.QS_TILE_COLOR);
            n.sliderCustom = Cfg.colorPair(QsFeatures.QS_SLIDER_COLOR);
            if (n.tileCustom != null && n.tileColor <= 0) n.tileColor = 2;
            if (n.sliderCustom != null && n.sliderColor <= 0) n.sliderColor = 2;
            for (int i = 0; i < CC_HIDE_KEYS.length; i++) if (Cfg.on(CC_HIDE_KEYS[i])) n.ccHide |= 1 << i;
            S old = snap;
            snap = n; snapAt = now;
            // The One UI finish lives in the tile colours, which the host only recomputes on a state change: push a refresh.
            if ((old.tileStyle != n.tileStyle && (QsArt.oneUiTile(old.tileStyle) || QsArt.oneUiTile(n.tileStyle))) || old.tileColor != n.tileColor || Cfg.colorHash(old.tileCustom) != Cfg.colorHash(n.tileCustom)) refreshTileColors();
            if (old.ccEnable != n.ccEnable) refreshControlCenterSwitch();
            if (old.ccCols != n.ccCols || old.ccHide != n.ccHide || old.ccGap != n.ccGap || old.ccGapDp != n.ccGapDp) refreshControlCenterViews();
        }
        return snap;
    }
    private static int clamp(int v, int max) { return v < 0 || v > max ? 0 : v; }

    private static boolean main() { return Looper.myLooper() == Looper.getMainLooper(); }
    private static boolean dark(View v) { try { return (v.getResources().getConfiguration().uiMode & 0x30) == 0x20; } catch (Throwable t) { return true; } }
    private static int n(Object o, String f) { return ((Number) XposedHelpers.getObjectField(o, f)).intValue(); }
    private static void put(Object o, String f, int v) { XposedHelpers.setIntField(o, f, v); }
    private static Object field(Object o, String name) { return XposedHelpers.getObjectField(o, name); }
    private static float density(View v) { try { return v.getResources().getDisplayMetrics().density; } catch (Throwable t) { return 3f; } }
    private static View parentView(View v) { ViewParent p = v.getParent(); return p instanceof View ? (View) p : null; }

    /** Hook every declared overload of {@code name} found walking up from {@code c}; returns how many. */
    private static int hookUp(Class<?> c, String name, XC_MethodHook cb) {
        int count = 0;
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            boolean found = false;
            for (Method m : k.getDeclaredMethods()) if (m.getName().equals(name)) { found = true; if (H.hook(m, cb)) count++; }
            if (found) break;
        }
        return count;
    }

    static void install(ClassLoader cl) {
        try { grid(cl); } catch (Throwable t) { Lx.e("QS: grid install", t); }
        try { layout(cl); } catch (Throwable t) { Lx.e("QS: layout install", t); }
        try { tiles(cl); } catch (Throwable t) { Lx.e("QS: tile shape/finish install", t); }
        try { brightness(cl); } catch (Throwable t) { Lx.e("QS: brightness install", t); }
        try { gradientDraw(); } catch (Throwable t) { Lx.e("QS: gradient draw install", t); }
        try { controlCenter(cl); } catch (Throwable t) { Lx.e("QS: Control Center install", t); }
        try { CcLayoutHooks.install(cl); } catch (Throwable t) { Lx.e("QS: Control Center slider layout install", t); }
        try { CcOneUiHooks.install(cl); } catch (Throwable t) { Lx.e("QS: One UI 8 Control Center install", t); }
        Lx.i("QS: hooks ready");
    }

    // ============================================================================================= small-tile grid (QsGridBackend)

    private static final Map<ViewGroup, String> pagers = new WeakHashMap<>();
    private static final Set<ViewGroup> gridChanged = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> animators = new WeakHashMap<>();
    private static final class AnimationState {
        final Object originalRows, originalDelays, ownedRows, ownedDelays;
        AnimationState(Object originalRows, Object originalDelays, Object ownedRows, Object ownedDelays) { this.originalRows = originalRows; this.originalDelays = originalDelays; this.ownedRows = ownedRows; this.ownedDelays = ownedDelays; }
    }
    private static final Map<Object, AnimationState> animationStates = new WeakHashMap<>();
    private static final ThreadLocal<ViewGroup> animationOwner = new ThreadLocal<>();
    private static boolean gridReady, gridFailed;

    private static void gridFail(String where, Throwable t) {
        if (!gridFailed) Lx.e("QS grid: " + where + " failed, grid policy stopped until System UI restarts", t);
        gridFailed = true;
    }
    private static ViewGroup pagerOf(View v) {
        for (View current = v; current != null; current = parentView(current)) if (current.getClass().getName().equals(PAGER) && current instanceof ViewGroup) return (ViewGroup) current;
        return null;
    }
    /** Only the exact pager that the normal MfvQSPanel currently uses as its tile layout (not the customizer). */
    private static boolean owned(ViewGroup pager) {
        if (pager == null || !pager.getClass().getName().equals(PAGER) || !pager.isAttachedToWindow()) return false;
        for (View v = pager; v != null; v = parentView(v)) if (v.getClass().getName().equals(PANEL)) return field(v, "mTileLayout") == pager;
        return false;
    }
    private static boolean portrait(View v) { return v.getResources().getConfiguration().orientation == 1; }
    private static int selectedColumns(View v) { S s = s(); return portrait(v) ? s.cols : s.landCols; }
    private static int selectedRows(View v) { S s = s(); return portrait(v) ? s.rows : s.landRows; }
    private static boolean gridApplies(ViewGroup pager) {
        if (!gridReady || gridFailed || !main() || !owned(pager)) return false;
        return pager.getResources().getConfiguration().fontScale <= 1.3f && (selectedColumns(pager) != 0 || selectedRows(pager) != 0);
    }

    private static void grid(ClassLoader cl) {
        Class<?> base = H.cls(cl, BASE), pager = H.cls(cl, PAGER), page = H.cls(cl, PAGE), panel = H.cls(cl, PANEL), anim = H.cls(cl, ANIM);
        if (base == null || pager == null || page == null || panel == null || anim == null) return;
        try {
            if (base.getSuperclass() != ViewGroup.class || page.getSuperclass() != base) { Lx.w("QS grid: tile layout hierarchy changed, grid policy disabled"); return; }
            for (String f : new String[]{"mResourceColumns", "mColumns", "mMaxAllowedRows", "mRows", "mCellWidth", "mCellHeight", "mCellMarginVertical", "mCellMarginTop"}) base.getDeclaredField(f);
            panel.getDeclaredField("mTileLayout"); anim.getDeclaredField("mPagedLayout"); anim.getDeclaredField("mRowAnimators"); anim.getDeclaredField("ROW_START_DELAY"); anim.getDeclaredField("mNeedsAnimatorUpdate");
            for (String f : new String[]{"mExcessHeight", "mPages", "mTiles", "mDistributeTiles"}) pager.getDeclaredField(f);
        } catch (Throwable t) { Lx.w("QS grid: expected field missing (" + t + "), grid policy disabled"); return; }

        H.hook(H.find(base, "updateResources"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !main()) return;
                try {
                    View v = (View) p.thisObject;
                    if (!v.getClass().getName().equals(PAGE)) return;
                    ViewGroup owner = pagerOf(v);
                    if (!gridApplies(owner)) return;
                    configure(v, owner);
                    p.setResult(true);
                } catch (Throwable t) { gridFail("updateResources", t); }
            }
        });
        H.hook(H.find(pager, "createTilePage"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !main() || !gridApplies((ViewGroup) p.thisObject)) return;
                try { Object r = p.getResult(); if (r instanceof View && r.getClass().getName().equals(PAGE)) configure((View) r, (ViewGroup) p.thisObject); }
                catch (Throwable t) { gridFail("createTilePage", t); }
            }
        });
        H.hook(H.find(pager, "onMeasure", int.class, int.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!main()) return;
                try {
                    ViewGroup v = (ViewGroup) p.thisObject;
                    if (!owned(v)) return;
                    if (!pagers.containsKey(v)) pagers.put(v, "");
                    int width = View.MeasureSpec.getSize((Integer) p.args[0]);
                    XposedHelpers.setAdditionalInstanceField(v, "rmtweaks:grid-width", width);
                    Object adapt = XposedHelpers.callMethod(v, "getAdapt");
                    int decor = ((Number) XposedHelpers.callMethod(adapt, "getDecorHeight")).intValue();
                    int budget = Math.max(0, View.MeasureSpec.getSize((Integer) p.args[1]) - n(v, "mExcessHeight") - Math.max(0, decor));
                    XposedHelpers.setAdditionalInstanceField(v, "rmtweaks:grid-height", budget);
                    boolean applies = gridApplies(v);
                    String mode = (applies ? v.getResources().getConfiguration().orientation + ":" + selectedColumns(v) + ":" + selectedRows(v) : "stock") + ":" + width + ":" + budget;
                    // Native configuration (and our settings) may change while enabled: recompute resources on every policy transition.
                    String key = "policy:" + mode;
                    if (!key.equals(XposedHelpers.getAdditionalInstanceField(v, "rmtweaks:grid-policy"))) {
                        XposedHelpers.setAdditionalInstanceField(v, "rmtweaks:grid-policy", key);
                        if (applies || gridChanged.contains(v)) { reflow(v); if (!applies) gridChanged.remove(v); }
                    }
                } catch (Throwable t) { gridFail("onMeasure", t); }
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!main()) return;
                try {
                    ViewGroup v = (ViewGroup) p.thisObject;
                    if (!owned(v)) return;
                    if (p.hasThrowable()) { gridFail("native onMeasure", p.getThrowable()); return; }
                    List<?> pages = (List<?>) field(v, "mPages");
                    if (pages.isEmpty()) return;
                    Object first = pages.get(0);
                    String key = n(first, "mColumns") + ":" + n(first, "mRows") + ":" + pages.size() + ":" + v.getResources().getConfiguration().orientation;
                    if (!key.equals(pagers.put(v, key))) refreshAnimators(v);
                } catch (Throwable t) { gridFail("onMeasure after", t); }
            }
        });
        XC_MethodHook animation = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!main()) return;
                try {
                    Object o = p.thisObject;
                    animators.put(o, Boolean.TRUE);
                    p.setObjectExtra("rmtweaks:grid-animation", animationOwner.get());
                    Object v = field(o, "mPagedLayout");
                    prepareAnimation(o, v instanceof ViewGroup ? (ViewGroup) v : null);
                    animationOwner.set(v instanceof ViewGroup && gridApplies((ViewGroup) v) ? (ViewGroup) v : null);
                } catch (Throwable t) { gridFail("animator", t); }
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (main()) animationOwner.set((ViewGroup) p.getObjectExtra("rmtweaks:grid-animation"));
            }
        };
        H.hook(H.find(anim, "updateAnimators"), animation);
        H.hook(H.find(anim, "updateViews"), animation);
        H.hook(H.find(anim, "setPosition", float.class), animation);
        XC_MethodHook colsHook = dimensionHook(true), rowsHook = dimensionHook(false);
        int hooked = 0;
        for (String name : new String[]{DIMEN, DIMEN_COMPANION}) {
            Class<?> dim = H.cls(cl, name);
            if (dim == null) continue;
            if (H.hook(H.find(dim, "getTileColumns"), colsHook)) hooked++;
            if (H.hook(H.find(dim, "getMaxQsPanelRowCount"), rowsHook)) hooked++;
        }
        if (hooked == 0) Lx.w("QS grid: QsDimenUtils getters not hooked, animations may use stock row counts");
        gridReady = true;
        Lx.i("QS: grid hooks ready");
    }

    /** During an owning animator's update the shared dimension getters report the page's actual columns / rows. */
    private static XC_MethodHook dimensionHook(final boolean col) {
        return new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                ViewGroup v = animationOwner.get();
                if (p.hasThrowable() || v == null || !gridApplies(v)) return;
                try {
                    List<?> pages = (List<?>) field(v, "mPages");
                    if (pages.isEmpty()) return;
                    int value = n(pages.get(0), col ? "mColumns" : "mRows");
                    if (value >= 1 && value <= 6) p.setResult(value);
                } catch (Throwable t) { gridFail("dimension getter", t); }
            }
        };
    }

    private static void configure(View v, ViewGroup pager) {
        if (!gridApplies(pager) || !v.getClass().getName().equals(PAGE) || n(v, "mColumns") < 2 || n(v, "mColumns") > 6) return;
        gridChanged.add(pager);
        int requestedColumns = selectedColumns(pager), requestedRows = selectedRows(pager), effective = requestedColumns;
        if (requestedColumns >= 5) {
            Object measured = XposedHelpers.getAdditionalInstanceField(pager, "rmtweaks:grid-width");
            int width = measured instanceof Integer ? (Integer) measured : pager.getWidth();
            Object adapt = XposedHelpers.callMethod(v, "getAdapt");
            int cell = ((Number) XposedHelpers.callMethod(adapt, "getCellWidth", 0)).intValue();
            int edge = ((Number) XposedHelpers.callMethod(v, "getColumnStart", 0)).intValue();
            int gap = (int) Math.ceil(4 * v.getResources().getDisplayMetrics().density);
            if (cell <= 0 || edge < 0 || width < (long) requestedColumns * cell + 2L * edge + (requestedColumns - 1L) * gap) effective = 0;
        }
        if (effective != 0) { put(v, "mResourceColumns", effective); put(v, "mColumns", effective); }
        if (requestedRows != 0) {
            int cap = Math.min(requestedRows, n(v, "mMaxAllowedRows"));
            if (requestedRows >= 4) {
                Object measured = XposedHelpers.getAdditionalInstanceField(pager, "rmtweaks:grid-height");
                int budget = measured instanceof Integer ? (Integer) measured : 0;
                int cell = n(v, "mCellHeight"), gap = n(v, "mCellMarginVertical"), top = n(v, "mCellMarginTop");
                if (budget > 0 && cell > 0 && gap >= 0) cap = Math.max(1, Math.min(requestedRows, (budget - top + gap) / (cell + gap)));
            }
            put(v, "mMaxAllowedRows", cap);
        }
    }

    /** Four to six actual rows get owned TouchAnimator[row][2] arrays and start delays ending at 0.65 (nubia-ly). */
    private static void prepareAnimation(Object animator, ViewGroup pager) {
        int count = 0;
        if (gridApplies(pager)) { List<?> pages = (List<?>) field(pager, "mPages"); if (!pages.isEmpty()) count = n(pages.get(0), "mRows"); }
        AnimationState state = animationStates.get(animator);
        if (count < 4) { if (state != null) restoreAnimation(animator, state); return; }
        if (count > 6) throw new IllegalStateException("Animation row bound");
        Object current = field(animator, "mRowAnimators"), delays = field(animator, "ROW_START_DELAY");
        if (state != null) {
            if (current != state.ownedRows || delays != state.ownedDelays) throw new IllegalStateException("Animation ownership changed");
            if (Array.getLength(current) == count) return;
            restoreAnimation(animator, state);
            current = field(animator, "mRowAnimators"); delays = field(animator, "ROW_START_DELAY");
        }
        if (!(delays instanceof Float[]) || ((Float[]) delays).length < 3 || Array.getLength(current) < 3) throw new IllegalStateException("Animation capacity");
        Float[] nativeDelays = (Float[]) delays;
        Float[] expandedDelays = Arrays.copyOf(nativeDelays, Math.max(count, nativeDelays.length));
        if (nativeDelays[2] == null || !Float.isFinite(nativeDelays[2]) || nativeDelays[2] < 0 || nativeDelays[2] > .65f) throw new IllegalStateException("Animation delay");
        for (int i = 3; i < count; i++) expandedDelays[i] = nativeDelays[2] + (.65f - nativeDelays[2]) * (i - 2) / (count - 3);
        Object expanded = Array.newInstance(current.getClass().getComponentType(), count);
        for (int i = 0; i < count; i++) Array.set(expanded, i, Array.newInstance(current.getClass().getComponentType().getComponentType(), 2));
        state = new AnimationState(current, delays, expanded, expandedDelays);
        animationStates.put(animator, state);
        XposedHelpers.setObjectField(animator, "mRowAnimators", expanded);
        XposedHelpers.setObjectField(animator, "ROW_START_DELAY", expandedDelays);
        XposedHelpers.setBooleanField(animator, "mNeedsAnimatorUpdate", true);
    }
    private static void restoreAnimation(Object animator, AnimationState state) {
        Object current = field(animator, "mRowAnimators"), delays = field(animator, "ROW_START_DELAY");
        // A native replacement belongs to the firmware; never overwrite it with an old capture.
        if (current == state.ownedRows) XposedHelpers.setObjectField(animator, "mRowAnimators", state.originalRows);
        if (delays == state.ownedDelays) XposedHelpers.setObjectField(animator, "ROW_START_DELAY", state.originalDelays);
        XposedHelpers.setBooleanField(animator, "mNeedsAnimatorUpdate", true);
        animationStates.remove(animator);
    }
    private static void reflow(ViewGroup v) { XposedHelpers.callMethod(v, "updateResources"); XposedHelpers.setBooleanField(v, "mDistributeTiles", true); v.requestLayout(); }
    private static void refreshAnimators(ViewGroup v) {
        for (Object a : new ArrayList<>(animators.keySet())) if (field(a, "mPagedLayout") == v) XposedHelpers.callMethod(a, "updateAnimators");
    }

    // ============================================================================================= normal-shade layout (QsLayoutBackend)

    private static final Map<ViewGroup, Boolean> roots = new WeakHashMap<>();
    private static final Map<ViewGroup, Integer> appliedGeneration = new WeakHashMap<>();
    private static final Map<ViewGroup, Boolean> appliedEligibility = new WeakHashMap<>();
    private static final class CompactHeight {
        final WeakReference<ViewGroup> root, quick; final int delta, configuration;
        CompactHeight(WeakReference<ViewGroup> root, WeakReference<ViewGroup> quick, int delta, int configuration) { this.root = root; this.quick = quick; this.delta = delta; this.configuration = configuration; }
    }
    private static final Map<Object, CompactHeight> compactHeights = new WeakHashMap<>();
    private static final Set<ViewGroup> changedRoots = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ViewGroup> pending = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<View, LabelState> labels = new WeakHashMap<>();
    private static final Map<View, Integer> visibility = new WeakHashMap<>();
    private static final Map<View, Integer> heights = new WeakHashMap<>();
    private static final Map<View, Integer> margins = new WeakHashMap<>();
    private static final Map<View, Integer> marginDeltas = new WeakHashMap<>();
    private static Class<?> dimensions;
    private static final ThreadLocal<ViewGroup> scope = new ThreadLocal<>();
    private static boolean layoutReady, layoutActive, layoutFailed, writing, rebuilding, syncPosted;
    private static int top, labelPosition, labelLines, brightnessPos, generation;
    private static boolean hideIcons;

    private static final class LabelState {
        final ViewGroup container, parent; final TextView text; final int index; int maxLines; boolean moved;
        LabelState(ViewGroup c, ViewGroup p, TextView t) { container = c; parent = p; text = t; index = p.indexOfChild(c); maxLines = t.getMaxLines(); }
    }

    private static void layoutFail(String phase, Throwable t) {
        if (!layoutFailed) Lx.e("QS layout: " + phase + " failed; layout changes paused until the selection changes", t);
        layoutFailed = true;
    }
    private static ViewGroup rootOf(Object object) {
        if (!(object instanceof View)) return null;
        View v = (View) object;
        for (int i = 0; v != null && i < 24; i++) { if (v.getClass().getName().equals(CONTENT) && v instanceof ViewGroup) return (ViewGroup) v; v = parentView(v); }
        return null;
    }
    private static ViewGroup descendantRoot(View v, int depth) {
        if (depth > 12) return null;
        if (v.getClass().getName().equals(CONTENT) && v instanceof ViewGroup) return (ViewGroup) v;
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { ViewGroup r = descendantRoot(g.getChildAt(i), depth + 1); if (r != null) return r; } }
        return null;
    }
    private static ViewGroup contextRoot(Object object) {
        if (object instanceof View) {
            View v = (View) object;
            if (v.getClass().getName().equals(CONTAINER)) { Object content = field(v, "qsContent"); return content instanceof ViewGroup && content.getClass().getName().equals(CONTENT) ? (ViewGroup) content : null; }
            return rootOf(v);
        }
        if (object != null && object.getClass().getName().equals(ANIM)) return rootOf(field(object, "mPagedLayout"));
        if (object != null && object.getClass().getName().equals(FRAGMENT)) { Object v = field(object, "mRootView"); if (v instanceof View) return descendantRoot((View) v, 0); }
        return null;
    }
    private static boolean usable(ViewGroup r) { return r != null && r.isAttachedToWindow() && r.getResources().getConfiguration().orientation == 1 && r.getResources().getConfiguration().fontScale <= 1.3f; }
    private static boolean custom(ViewGroup r) { return layoutActive && !layoutFailed && main() && usable(r); }
    private static View findId(ViewGroup r, String name) { int id = r.getResources().getIdentifier(name, "id", "com.android.systemui"); return id == 0 ? null : r.findViewById(id); }
    private static ViewGroup panel(ViewGroup r, boolean quick) {
        View v = findId(r, quick ? "quick_qs_panel" : "quick_settings_panel");
        return v instanceof ViewGroup && v.getClass().getName().equals(quick ? QUICK : PANEL) && rootOf(v) == r ? (ViewGroup) v : null;
    }
    private static View direct(ViewGroup r, View v) { for (int i = 0; v != null && i < 24; i++) { if (v.getParent() == r) return v; v = parentView(v); } return null; }
    private static int topCount() { return top == 1 ? 2 : top == 2 ? 6 : 4; }
    private static int topRows() { return top == 1 ? 1 : top == 2 ? 3 : 4; }

    private static void layout(ClassLoader cl) {
        dimensions = H.cls(cl, DIMEN);
        Class<?> content = H.cls(cl, CONTENT), quick = H.cls(cl, QUICK), normal = H.cls(cl, PANEL), header = H.cls(cl, HEADER), circle = H.cls(cl, CIRCLE), container = H.cls(cl, CONTAINER), anim = H.cls(cl, ANIM), fragment = H.cls(cl, FRAGMENT);
        Class<?> base = H.cls(cl, BASE), feature = H.cls(cl, LAYOUT_FEATURE), panelAdapt = H.cls(cl, PANEL_ADAPT), layoutAdapt = H.cls(cl, LAYOUT_ADAPT);
        if (dimensions == null || content == null || quick == null || normal == null || header == null || circle == null || container == null || anim == null || fragment == null || base == null || feature == null || panelAdapt == null || layoutAdapt == null) { Lx.w("QS layout: a shade class is missing, large-tile / label / brightness layout disabled"); return; }
        if (content.getSuperclass() != FrameLayout.class || header.getSuperclass() != base) { Lx.w("QS layout: shade hierarchy changed, layout disabled"); return; }

        XC_MethodHook context = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!main() || !layoutActive || layoutFailed || top == 0) return;
                p.setObjectExtra("rmtweaks:layout-context", Boolean.TRUE);
                p.setObjectExtra("rmtweaks:layout-scope", scope.get());
                scope.set(contextRoot(p.thisObject));
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (main() && Boolean.TRUE.equals(p.getObjectExtra("rmtweaks:layout-context"))) scope.set((ViewGroup) p.getObjectExtra("rmtweaks:layout-scope"));
            }
        };
        // Only calls originating in the inspected normal shade can affect the shared dimension getters.
        Set<String> scoped = new HashSet<>(Arrays.asList("onMeasure", "onLayout", "updateResources", "setTiles", "setMaxTiles", "onConfigurationChanged", "updateAnimators", "updateViews"));
        for (Class<?> c : new Class<?>[]{normal, quick, header, container, anim, fragment})
            for (Method m : c.getDeclaredMethods()) if (scoped.contains(m.getName())) H.hook(m, context);
        for (String name : new String[]{DIMEN, DIMEN_COMPANION}) {
            Class<?> dim = H.cls(cl, name);
            if (dim == null) continue;
            for (String getter : new String[]{"getQuickTileNums", "getQuickTileRows"}) {
                final boolean count = getter.equals("getQuickTileNums");
                H.hook(H.find(dim, getter), new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) { if (!p.hasThrowable() && top != 0 && custom(scope.get())) p.setResult(count ? topCount() : topRows()); }
                });
            }
        }
        // The compact container and the notification stack query this native minimum separately from child
        // measurement; its stock padding reserves only two quick-tile rows.
        H.hook(H.find(container, "getMinHeight"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !main() || !layoutActive || layoutFailed || top == 0) return;
                try {
                    CompactHeight state = compactHeights.get(p.thisObject);
                    if (state == null) return;
                    ViewGroup r = state.root.get(), q = state.quick.get();
                    if (!custom(r) || q == null || !Boolean.TRUE.equals(field(p.thisObject, "showQsPanel")) || field(p.thisObject, "quickQSPanel") != q) return;
                    int configuration = r.getResources().getConfiguration().hashCode();
                    if (configuration != state.configuration) { state = new CompactHeight(state.root, state.quick, quickHeightDelta(r), configuration); compactHeights.put(p.thisObject, state); }
                    int nativeHeight = ((Number) p.getResult()).intValue();
                    if (nativeHeight > 0) p.setResult(Math.max(1, nativeHeight + state.delta));
                } catch (Throwable t) { layoutFail("compact height", t); }
            }
        });
        H.hook(H.find(feature, "getZteQuickTilesNum"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { if (!p.hasThrowable() && top != 0 && custom(scope.get())) p.setResult(topCount()); }
        });
        H.hook(H.find(panelAdapt, "getQQSMaxTilesCount", int.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try { Object owner = field(p.thisObject, "mOwner"); if (!p.hasThrowable() && top != 0 && owner instanceof View && owner.getClass().getName().equals(QUICK) && custom(rootOf(owner))) p.setResult(topCount()); }
                catch (Throwable t) { layoutFail("quick count", t); }
            }
        });
        H.hook(H.find(panelAdapt, "getQSPanelTiles", Collection.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try { Object owner = field(p.thisObject, "mOwner"); if (!p.hasThrowable() && top != 0 && owner instanceof View && owner.getClass().getName().equals(PANEL) && custom(rootOf(owner))) p.setResult(remaining((Collection<?>) p.args[0], topCount())); }
                catch (Throwable t) { layoutFail("panel tiles", t); }
            }
        });
        for (String method : new String[]{"getQuickTileCount", "getQuickTileRows", "getQuickTilesColumns"}) {
            final String key = method;
            H.hook(H.find(layoutAdapt, method, int.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Object owner = field(p.thisObject, "mOwner");
                        if (!p.hasThrowable() && top != 0 && owner instanceof View && owner.getClass().getName().equals(HEADER) && custom(rootOf(owner)))
                            p.setResult(key.equals("getQuickTileCount") ? topCount() : key.equals("getQuickTileRows") ? topRows() : topCount() / topRows());
                    } catch (Throwable t) { layoutFail("header dimensions", t); }
                }
            });
        }
        H.hook(H.find(header, "getColumnStart", int.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    View v = (View) p.thisObject;
                    if (!p.hasThrowable() && top == 3 && custom(rootOf(v))) { int width = XposedHelpers.getIntField(v, "mCellWidth"); if (width > 0 && v.getMeasuredWidth() >= width) p.setResult((v.getMeasuredWidth() - width) / 2); }
                } catch (Throwable t) { layoutFail("column start", t); }
            }
        });
        H.hook(H.find(FrameLayout.class, "onLayout", boolean.class, int.class, int.class, int.class, int.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!main() || p.hasThrowable() || !p.thisObject.getClass().getName().equals(CONTENT)) return;
                try {
                    ViewGroup r = (ViewGroup) p.thisObject;
                    syncLayout(r);
                    Boolean previous = roots.put(r, usable(r));
                    if (layoutActive && !layoutFailed && !rebuilding && (previous == null || previous != usable(r) || usable(r) && !Integer.valueOf(generation).equals(appliedGeneration.get(r)))) schedule(r);
                    if (custom(r)) { moveBrightness(r); applyIcons(r); }
                } catch (Throwable t) { layoutFail("content layout", t); }
            }
        });
        H.hook(H.find(circle, "onMeasure", int.class, int.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!main()) return;
                View tile = (View) p.thisObject;
                ViewGroup root = rootOf(tile);
                if (root != null) { try { if (custom(root)) applyLabel(tile); } catch (Throwable t) { layoutFail("label", t); } }
                else {
                    try { applyCcLabel(tile); } catch (Throwable t) { Lx.e("QS: Control Center label", t); }
                    try { applyCcCircle(tile); } catch (Throwable t) { Lx.e("QS: Control Center tile gap", t); }
                }
            }
        });
        H.hook(H.find(View.class, "setLayoutParams", ViewGroup.LayoutParams.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!main() || writing || !layoutActive || (heights.isEmpty() && margins.isEmpty())) return;
                View v = (View) p.thisObject;
                ViewGroup.LayoutParams lp = (ViewGroup.LayoutParams) p.args[0];
                if (lp == null) return;
                if (heights.containsKey(v)) {
                    if (lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) heights.put(v, lp.height);
                    if (custom(rootOf(v)) && top != 0) lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                }
                if (margins.containsKey(v) && lp instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
                    int delta = marginDeltas.containsKey(v) ? marginDeltas.get(v) : 0;
                    if (m.topMargin != margins.get(v) + delta) margins.put(v, m.topMargin);
                    if (custom(rootOf(v))) m.topMargin = margins.get(v) + delta;
                }
            }
        });
        H.hook(H.find(View.class, "setVisibility", int.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!main() || writing || visibility.isEmpty()) return;
                View v = (View) p.thisObject;
                if (visibility.containsKey(v)) { visibility.put(v, (Integer) p.args[0]); if (layoutActive) p.args[0] = View.INVISIBLE; }
            }
        });
        H.hook(H.find(TextView.class, "setMaxLines", int.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!main() || writing || labels.isEmpty()) return;
                for (LabelState s : labels.values()) if (s.text == p.thisObject) { s.maxLines = (Integer) p.args[0]; if (layoutActive && labelLines != 0) p.args[0] = labelLines; }
            }
        });
        layoutReady = true;
        Lx.i("QS: layout hooks ready");
    }

    /** Settings changed since the last apply: restore the previous layout, adopt the new one (nubia-ly end() + begin()). */
    private static void syncLayout(final ViewGroup trigger) {
        if (!layoutReady || syncPosted) return;
        S s = s();
        if (s.top == top && s.labelPos == labelPosition && s.labelLines == labelLines && s.brightnessPos == brightnessPos && s.noSun == hideIcons) return;
        syncPosted = true;
        trigger.post(() -> {
            syncPosted = false;
            try {
                S n = s();
                if (n.top == top && n.labelPos == labelPosition && n.labelLines == labelLines && n.brightnessPos == brightnessPos && n.noSun == hideIcons) return;
                boolean restored = endLayout();
                top = n.top; labelPosition = n.labelPos; labelLines = n.labelLines; brightnessPos = n.brightnessPos; hideIcons = n.noSun;
                layoutActive = top != 0 || labelPosition != 0 || labelLines != 0 || brightnessPos != 0 || hideIcons;
                if (!restored) { Lx.w("QS layout: previous layout could not be fully restored; restart System UI to clear it"); }
                if (!layoutActive) return;
                layoutFailed = false;
                for (ViewGroup r : new ArrayList<>(roots.keySet())) if (usable(r)) rebuild(r);
            } catch (Throwable t) { layoutFail("apply", t); }
        });
    }

    private static void restoreRootViews(ViewGroup r) {
        writing = true;
        try {
            for (Map.Entry<View, Integer> e : new ArrayList<>(visibility.entrySet())) if (rootOf(e.getKey()) == r) { e.getKey().setVisibility(e.getValue()); visibility.remove(e.getKey()); }
            for (Map.Entry<View, LabelState> e : new ArrayList<>(labels.entrySet())) if (rootOf(e.getKey()) == r) { restoreLabel(e.getValue()); labels.remove(e.getKey()); }
        } finally { writing = false; }
    }
    private static void restoreLabel(LabelState s) {
        s.text.setMaxLines(s.maxLines);
        if (s.moved && s.container.getParent() == s.parent) { s.parent.removeView(s.container); s.parent.addView(s.container, Math.min(s.index, s.parent.getChildCount())); }
    }
    static List<?> remaining(Collection<?> all, int count) { List<?> copy = new ArrayList<>(all); return new ArrayList<>(copy.subList(Math.min(count, copy.size()), copy.size())); }
    private static boolean applied(ViewGroup r) { return Integer.valueOf(generation).equals(appliedGeneration.get(r)) && Boolean.valueOf(usable(r)).equals(appliedEligibility.get(r)); }
    private static void schedule(final ViewGroup r) {
        if (layoutFailed || rebuilding || !pending.add(r)) return;
        final int token = generation;
        r.post(() -> {
            pending.remove(r);
            if (token != generation || !layoutActive || layoutFailed || !r.isAttachedToWindow() || applied(r)) return;
            try { rebuild(r); } catch (Throwable t) { layoutFail("queued rebuild", t); }
        });
    }
    private static void rebuild(ViewGroup r) {
        if (rebuilding) return;
        ViewGroup q = panel(r, true), p = panel(r, false);
        if (q == null || p == null) { if (changedRoots.contains(r)) throw new IllegalStateException("QS panels unavailable during restoration"); return; }
        rebuilding = true;
        ViewGroup old = scope.get(); scope.set(r);
        try {
            if (!custom(r)) restoreRootViews(r);
            Object host = field(p, "mHost");
            if (host == null) { if (changedRoots.contains(r)) throw new IllegalStateException("QS host unavailable during restoration"); return; }
            if (custom(r) && top != 0) changedRoots.add(r);
            Object tiles = XposedHelpers.callMethod(host, "getTiles");
            XposedHelpers.callMethod(q, "setMaxTiles", XposedHelpers.getIntField(q, "mMaxTiles"));
            XposedHelpers.callMethod(p, "setTiles", tiles);
            // Native fixed margins depend on the original quick-panel height: shift whole blocks by the row-height delta.
            int delta = quickHeightDelta(r);
            adjustMargin(findId(r, "qs_brightness_dialog"), delta); adjustMargin(direct(r, p), delta);
            Object header = field(q, "mTileLayout");
            XposedHelpers.callMethod(header, "updateResources");
            ViewGroup.LayoutParams lp = q.getLayoutParams();
            if (custom(r) && top != 0) {
                if (!heights.containsKey(q)) heights.put(q, lp.height);
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                writing = true; try { q.setLayoutParams(lp); } finally { writing = false; }
            } else restoreHeight(q);
            XposedHelpers.callMethod(p, "updateResources");
            appliedGeneration.put(r, generation); appliedEligibility.put(r, usable(r));
            cacheCompactHeight(r, q, delta);
            q.requestLayout(); p.requestLayout(); r.requestLayout(); notifyHeight(r);
            if (!custom(r)) changedRoots.remove(r);
        } finally { scope.set(old); rebuilding = false; }
    }
    private static int quickHeightDelta(ViewGroup r) {
        if (!custom(r) || top == 0) return 0;
        ViewGroup previous = scope.get();
        try {
            scope.set(null);
            int stock = ((Number) XposedHelpers.callStaticMethod(dimensions, "getQuickQsPanelHeight")).intValue();
            scope.set(r);
            int selected = ((Number) XposedHelpers.callStaticMethod(dimensions, "getQuickQsPanelHeight")).intValue();
            return selected - stock;
        } finally { scope.set(previous); }
    }
    private static void cacheCompactHeight(ViewGroup r, ViewGroup q, int delta) {
        for (View v = r; v != null; v = parentView(v)) if (v.getClass().getName().equals(CONTAINER)) {
            if (custom(r) && top != 0) compactHeights.put(v, new CompactHeight(new WeakReference<>(r), new WeakReference<>(q), delta, r.getResources().getConfiguration().hashCode()));
            else compactHeights.remove(v);
            return;
        }
    }
    private static void notifyHeight(ViewGroup r) {
        Object qs = field(r, "mQs");
        if (qs != null && qs.getClass().getName().equals(FRAGMENT)) { XposedHelpers.callMethod(qs, "notifyCustomizeChanged"); return; }
        for (View v = r; v != null; v = parentView(v)) if (v.getClass().getName().equals(CONTAINER)) { XposedHelpers.callMethod(v, "updateExpansion"); return; }
    }
    private static void adjustMargin(View v, int delta) {
        if (v == null || !(v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        if (delta == 0) {
            Integer original = margins.get(v);
            if (original != null) { lp.topMargin = original; writing = true; try { v.setLayoutParams(lp); margins.remove(v); marginDeltas.remove(v); } finally { writing = false; } }
            return;
        }
        if (!margins.containsKey(v)) margins.put(v, lp.topMargin);
        marginDeltas.put(v, delta); lp.topMargin = margins.get(v) + delta;
        writing = true; try { v.setLayoutParams(lp); } finally { writing = false; }
    }
    private static void restoreHeight(View q) {
        Integer height = heights.get(q);
        if (height != null) { ViewGroup.LayoutParams lp = q.getLayoutParams(); lp.height = height; writing = true; try { q.setLayoutParams(lp); heights.remove(q); } finally { writing = false; } }
    }
    private static void hide(View view) {
        if (view == null) return;
        if (!visibility.containsKey(view)) visibility.put(view, view.getVisibility());
        writing = true; try { view.setVisibility(View.INVISIBLE); } finally { writing = false; }
    }
    private static void applyIcons(ViewGroup r) {
        if (!hideIcons) return;
        View b = findId(r, "qs_brightness_dialog");
        if (!(b instanceof ViewGroup) || direct(r, b) != b) return;
        ViewGroup group = (ViewGroup) b;
        for (String name : new String[]{"qs_brightness_low", "qs_brightness_high"}) { View v = findId(r, name); if (v != null && direct(group, v) != null) hide(v); }
    }
    private static void applyLabel(View tile) {
        // Exclude the tile customizer and control center, even when the view class is shared.
        boolean page = false;
        for (View v = tile; v != null && rootOf(v) != null; v = parentView(v)) if (v.getClass().getName().equals(PAGE)) { page = true; break; }
        if (!page) return;
        Object c = XposedHelpers.callMethod(tile, "getLabelContainer"), t = XposedHelpers.callMethod(tile, "getLabel");
        if (!(c instanceof ViewGroup) || !(tile instanceof ViewGroup) || ((ViewGroup) c).getParent() != tile || !(t instanceof TextView)) return;
        if (labelPosition == 0 && labelLines == 0) return;
        ViewGroup container = (ViewGroup) c, parent = (ViewGroup) tile; TextView text = (TextView) t;
        LabelState state = labels.get(tile);
        if (state == null) { state = new LabelState(container, parent, text); labels.put(tile, state); }
        if (labelPosition == 1 && parent.indexOfChild(container) != 0) { parent.removeView(container); parent.addView(container, 0); state.moved = true; }
        else if (labelPosition == 2) hide(container);
        if (labelLines != 0) { writing = true; try { text.setMaxLines(labelLines); } finally { writing = false; } }
    }
    private static void moveBrightness(ViewGroup r) {
        if (brightnessPos == 0) return;
        Object qs = field(r, "mQs");
        if (qs != null && Boolean.TRUE.equals(XposedHelpers.callMethod(qs, "isShowingDetail"))) return;
        View q = panel(r, true), p = panel(r, false), b = findId(r, "qs_brightness_dialog"), s = p == null ? null : direct(r, p);
        if (q == null || b == null || s == null || q.getParent() != r || b.getParent() != r || q.getVisibility() != View.VISIBLE || b.getVisibility() != View.VISIBLE || s.getVisibility() != View.VISIBLE) return;
        if (q.getHeight() <= 0 || b.getHeight() <= 0 || s.getHeight() <= 0 || q.getBottom() > b.getTop() || b.getBottom() > s.getTop()) return;
        if (brightnessPos == 1) { int gap = b.getTop() - q.getBottom(), start = q.getTop(), height = b.getHeight(); place(b, start); place(q, start + height + gap); }
        else { int gap = s.getTop() - b.getBottom(), start = b.getTop(), height = s.getHeight(); place(s, start); place(b, start + height + gap); }
    }
    private static void place(View v, int y) { v.layout(v.getLeft(), y, v.getRight(), y + v.getHeight()); }

    /** nubia-ly QsLayoutBackend.end(): put every touched view back and let the native panels rebuild. */
    private static boolean endLayout() {
        if (!main()) return false;
        layoutActive = false; generation++; pending.clear(); appliedGeneration.clear(); appliedEligibility.clear(); compactHeights.clear();
        boolean ok = true;
        writing = true;
        try {
            for (Map.Entry<View, Integer> e : new ArrayList<>(visibility.entrySet())) try { e.getKey().setVisibility(e.getValue()); visibility.remove(e.getKey()); } catch (Throwable t) { ok = false; }
            for (Map.Entry<View, LabelState> e : new ArrayList<>(labels.entrySet())) try { restoreLabel(e.getValue()); labels.remove(e.getKey()); } catch (Throwable t) { ok = false; }
            for (View v : new ArrayList<>(margins.keySet())) try { adjustMargin(v, 0); } catch (Throwable t) { ok = false; }
            for (View v : new ArrayList<>(heights.keySet())) try { restoreHeight(v); } catch (Throwable t) { ok = false; }
            for (ViewGroup r : new ArrayList<>(changedRoots)) try { rebuild(r); } catch (Throwable t) { ok = false; }
            for (ViewGroup r : new ArrayList<>(roots.keySet())) r.requestLayout();
        } finally { writing = false; top = labelPosition = labelLines = brightnessPos = 0; hideIcons = false; }
        layoutFailed = !ok;
        return ok;
    }

    // ============================================================================================= tile shapes + finishes (QsShapeBackend + QsOutlineBackend)

    private static final class TileState {
        final WeakReference<ViewGroup> owner; int shape; float radius; float[] corners; boolean shaped;
        TileState(ViewGroup owner, GradientDrawable d) { this.owner = new WeakReference<>(owner); shape = d.getShape(); radius = d.getCornerRadius(); corners = copy(d.getCornerRadii()); }
    }
    private static float[] copy(float[] v) { return v == null ? null : v.clone(); }
    /** background GradientDrawable -> owning tile (shape capture + finish owner) */
    private static final Map<GradientDrawable, TileState> tileStates = new WeakHashMap<>();
    private static boolean shapeWriting;

    private static boolean isTile(Object v) { if (v == null) return false; String n = v.getClass().getName(); return n.equals(WIDE) || n.equals(CIRCLE); }

    private static void tiles(ClassLoader cl) {
        Class<?> wide = H.cls(cl, WIDE), circle = H.cls(cl, CIRCLE), base = H.cls(cl, TILE_BASE);
        if (wide == null || circle == null) return;
        XC_MethodHook discover = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !main() || !isTile(p.thisObject)) return;
                try { trackTile((ViewGroup) p.thisObject); } catch (Throwable t) { Lx.e("QS: tile track", t); }
            }
        };
        // Inflation precedes attachment; vendor overrides need not dispatch through View.onAttachedToWindow.
        Set<Method> seen = new HashSet<>();
        for (Class<?> owner : new Class<?>[]{wide, circle})
            for (String name : new String[]{"onFinishInflate", "onAttachedToWindow", "onLayout", "createTileBackground", "updateResources"})
                for (Class<?> type = owner; type != null && type != Object.class; type = type.getSuperclass()) {
                    boolean found = false;
                    for (Method m : type.getDeclaredMethods()) if (m.getName().equals(name)) { found = true; if (seen.add(m)) H.hook(m, discover); }
                    if (found) break;
                }
        if (base == null) Lx.w("QS: QSTileViewBase missing");
        // Native geometry writes on a captured background update the capture and are re-shaped (QsShapeBackend).
        XC_MethodHook geometry = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (shapeWriting || tileStates.isEmpty() || !(p.thisObject instanceof GradientDrawable)) return;
                TileState state = tileStates.get(p.thisObject);
                if (state != null) p.setObjectExtra("rmtweaks:qs-shape", state);
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Object extra = p.getObjectExtra("rmtweaks:qs-shape");
                if (!(extra instanceof TileState) || shapeWriting || p.hasThrowable() || !main()) return;
                TileState s = (TileState) extra;
                if (tileStates.get(p.thisObject) != s) return;
                try {
                    GradientDrawable d = (GradientDrawable) p.thisObject;
                    switch (((Method) p.method).getName()) {
                        case "setShape": s.shape = d.getShape(); break;
                        case "setCornerRadius": s.radius = d.getCornerRadius(); s.corners = null; break;
                        case "setCornerRadii": s.corners = copy(d.getCornerRadii()); if (s.corners == null) s.radius = d.getCornerRadius(); break;
                        default: break;
                    }
                    paintShape(d, s);
                } catch (Throwable t) { Lx.e("QS: tile shape", t); }
            }
        };
        H.hook(H.find(GradientDrawable.class, "setShape", int.class), geometry);
        H.hook(H.find(GradientDrawable.class, "setCornerRadius", float.class), geometry);
        H.hook(H.find(GradientDrawable.class, "setCornerRadii", float[].class), geometry);
        H.hook(H.find(Drawable.class, "setBounds", int.class, int.class, int.class, int.class), geometry);
        try { oneUiColors(cl, wide, circle, base); } catch (Throwable t) { Lx.e("QS: One UI colour install", t); }
        Lx.i("QS: tile shape / finish hooks ready");
    }

    // ------------------------------------------------------------------------------------------- One UI tile colours

    /** The shade tile (QSTileViewWide / QSTileViewCircle, not the customizer's CustomizeTileView) that owns {@code v}, or null. */
    private static View tileOf(View v) {
        for (int i = 0; v != null && i < 8; i++, v = parentView(v)) if (isTile(v)) return v;
        return null;
    }
    private static boolean oneUi(View tile) {
        if (tile == null || !main() || !QsArt.oneUiTile(s().tileStyle) || !isTile(tile)) return false;
        try { return !XposedHelpers.getBooleanField(tile, "mIsCustomizeTile"); } catch (Throwable t) { return true; }
    }
    private static boolean isWide(View tile) { return tile.getClass().getName().equals(WIDE); }

    /** {start, end} of the chosen "Colour of tiles that are on" for a shade tile in state 2 (on), else null. */
    private static int[] activeColors(View tile, int state) {
        if (tile == null || state != 2 || !main() || s().tileColor <= 0 || !isTile(tile)) return null;
        try { if (XposedHelpers.getBooleanField(tile, "mIsCustomizeTile")) return null; } catch (Throwable ignored) { }
        S cur = s();
        return cur.tileCustom != null ? cur.tileCustom.clone() : QsArt.panelColors(tile.getContext(), cur.tileColor, dark(tile));
    }

    /**
     * One UI 7/8 tile colours. The firmware takes every tile colour from MultiThemeFeature through overridable getters on
     * QSTileViewBase (background start / end → GradientDrawable.setColors in setGradientBackColors, labels) and from
     * QSIconViewImpl.getColor (icon tint); QSTileViewBase.updateResources / handleStateChanged additionally push the module
     * colours straight into the views through MultiThemeFeature.updateTileIconColor / updateTileViewColor. All of them are
     * answered here with the One UI palette, so the host's own state animation (QSTileViewImpl.singleAnimator) still runs.
     */
    private static void oneUiColors(ClassLoader cl, Class<?> wide, Class<?> circle, Class<?> base) {
        if (base == null) { Lx.w("QS: QSTileViewBase missing, One UI tile colours disabled"); return; }
        XC_MethodHook background = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !(p.thisObject instanceof View)) return;
                int[] ac = p.args.length > 0 && p.args[0] instanceof Integer ? activeColors((View) p.thisObject, (Integer) p.args[0]) : null;
                if (ac != null) { p.setResult(((Method) p.method).getName().contains("End") ? ac[1] : ac[0]); return; }
                if (!oneUi((View) p.thisObject)) return;
                View tile = (View) p.thisObject;
                int c = s().tileStyle == QsArt.ONE_UI8_TILE ? QsArt.oneUi8Background((Integer) p.args[0], isWide(tile), dark(tile)) : QsArt.oneUiBackground((Integer) p.args[0], dark(tile));
                if (c != 0) p.setResult(c);
            }
        };
        XC_MethodHook label = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !(p.thisObject instanceof View)) return;
                boolean secondary = ((Method) p.method).getName().startsWith("getSecondary");
                if (isWide((View) p.thisObject) && p.args.length > 0 && p.args[0] instanceof Integer) {
                    int[] ac = activeColors((View) p.thisObject, (Integer) p.args[0]);
                    if (ac != null) { int on = QsArt.onColor(ac[0]); p.setResult(secondary ? (on & 0x00FFFFFF) | 0xA6000000 : on); return; }
                }
                if (!oneUi((View) p.thisObject)) return;
                View tile = (View) p.thisObject;
                boolean eight = s().tileStyle == QsArt.ONE_UI8_TILE;
                int st = (Integer) p.args[0];
                int c = eight ? (secondary ? QsArt.oneUi8SecondaryLabel(st, dark(tile)) : QsArt.oneUi8Label(st, dark(tile)))
                        : (secondary ? QsArt.oneUiSecondaryLabel(st, dark(tile)) : QsArt.oneUiLabel(st, isWide(tile), dark(tile)));
                if (c != 0) p.setResult(c);
            }
        };
        int hooked = 0;
        for (String name : new String[]{"getBackgroundStartColorForState", "getBackgroundEndColorForState", "getBackgroundColorForState"}) hooked += hookUp(base, name, background);
        for (Class<?> owner : new Class<?>[]{base, wide, circle})
            for (String name : new String[]{"getLabelColorForState", "getSecondaryLabelColorForState"}) {
                // only the overload the class itself declares (hookUp stops at the first declaring class)
                if (owner == null) continue;
                boolean declared = false;
                for (Method m : owner.getDeclaredMethods()) if (m.getName().equals(name)) { declared = true; break; }
                if (declared) hooked += hookUp(owner, name, label);
            }
        if (hooked == 0) { Lx.w("QS: tile colour getters not hooked, One UI tile colours disabled"); return; }

        Class<?> iconView = H.cls(cl, ICON_VIEW), state = H.cls(cl, TILE_STATE), theme = H.cls(cl, THEME);
        if (iconView != null && state != null) {
            H.hook(H.find(iconView, "getColor", state, String.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable() || !(p.thisObject instanceof View) || p.args[0] == null) return;
                    View tile = tileOf((View) p.thisObject);
                    int st = XposedHelpers.getIntField(p.args[0], "state");
                    int[] ac = activeColors(tile, st);
                    if (ac != null) { p.setResult(QsArt.onColor(ac[0])); return; }
                    if (!oneUi(tile)) return;
                    int c = s().tileStyle == QsArt.ONE_UI8_TILE ? QsArt.oneUi8Icon(st, dark(tile)) : QsArt.oneUiIcon(st, dark(tile));
                    if (c != 0) p.setResult(c);
                }
            });
        } else Lx.w("QS: QSIconViewImpl / QSTile.State missing, One UI icon tint follows the stock colour");
        if (theme != null) {
            // updateTileIconColor(Context, ImageView icon, ImageView indicator, int state, boolean customize, String spec)
            H.hook(H.find(theme, "updateTileIconColor", android.content.Context.class, ImageView.class, ImageView.class, int.class, boolean.class, String.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable() || !(p.args[1] instanceof ImageView)) return;
                    View tile = tileOf((View) p.args[1]);
                    int st = (Integer) p.args[3];
                    int[] ac = activeColors(tile, st);
                    if (ac != null) { ((ImageView) p.args[1]).setImageTintList(ColorStateList.valueOf(QsArt.onColor(ac[0]))); return; }
                    if (!oneUi(tile)) return;
                    int c = s().tileStyle == QsArt.ONE_UI8_TILE ? QsArt.oneUi8Icon(st, dark(tile)) : QsArt.oneUiIcon(st, dark(tile));
                    if (c != 0) ((ImageView) p.args[1]).setImageTintList(ColorStateList.valueOf(c));
                }
            });
            // updateTileViewColor(Context, TextView label, TextView secondary, ImageView indicator, int state, boolean customize, boolean wide, String spec)
            H.hook(H.find(theme, "updateTileViewColor", android.content.Context.class, TextView.class, TextView.class, ImageView.class, int.class, boolean.class, boolean.class, String.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable() || !(p.args[1] instanceof TextView)) return;
                    View tile = tileOf((View) p.args[1]);
                    int st = (Integer) p.args[4];
                    int[] ac = tile != null && isWide(tile) ? activeColors(tile, st) : null;
                    if (ac != null) {
                        int on = QsArt.onColor(ac[0]);
                        ((TextView) p.args[1]).setTextColor(on);
                        if (p.args[2] instanceof TextView) ((TextView) p.args[2]).setTextColor((on & 0x00FFFFFF) | 0xA6000000);
                        return;
                    }
                    if (!oneUi(tile)) return;
                    boolean eight = s().tileStyle == QsArt.ONE_UI8_TILE;
                    int c = eight ? QsArt.oneUi8Label(st, dark(tile)) : QsArt.oneUiLabel(st, isWide(tile), dark(tile));
                    int c2 = eight ? QsArt.oneUi8SecondaryLabel(st, dark(tile)) : QsArt.oneUiSecondaryLabel(st, dark(tile));
                    if (c != 0) ((TextView) p.args[1]).setTextColor(c);
                    if (c2 != 0 && p.args[2] instanceof TextView) ((TextView) p.args[2]).setTextColor(c2);
                }
            });
        } else Lx.w("QS: MultiThemeFeature missing, One UI label colours may lag behind a state change");
        Lx.i("QS: One UI tile colour hooks ready");
    }

    /**
     * One UI 8 large pill: the translucent pill (host draw) plus a circle behind the icon — white with a dark icon when the
     * tile is on, translucent with a white icon when off. Drawn as part of the background, so the icon renders on top.
     */
    private static boolean drawIconCircle(ViewGroup tile, Canvas canvas, QsArt.NativeDraw stock) throws Throwable {
        Object o = XposedHelpers.callMethod(tile, "getIcon");
        if (!(o instanceof View)) return false;
        View icon = (View) o;
        if (icon.getWidth() <= 0 || icon.getVisibility() != View.VISIBLE) return false;
        float x = icon.getLeft(), y = icon.getTop();
        for (ViewParent p = icon.getParent(); p instanceof View && p != tile; p = p.getParent()) { x += ((View) p).getLeft(); y += ((View) p).getTop(); }
        int state = 1;
        try { Object st = XposedHelpers.callMethod(tile, "getLastState"); if (st instanceof Integer) state = (Integer) st; } catch (Throwable ignored) { }
        stock.draw();
        float cx = x + icon.getWidth() / 2f, cy = y + icon.getHeight() / 2f;
        float r = Math.max(icon.getWidth(), icon.getHeight()) / 2f + 8f * density(tile);
        r = Math.min(r, tile.getHeight() / 2f - 4f * density(tile));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(QsArt.oneUi8IconCircle(state, dark(tile)));
        canvas.drawCircle(cx, cy, r, paint);
        return true;
    }

    private static Handler mainHandler;
    /** Setting toggled: let every known tile recompute its colours (updateResources → updateColors + icon / label tint). */
    private static void refreshTileColors() {
        if (tileStates.isEmpty()) return;
        try {
            if (mainHandler == null) mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> {
                Set<ViewGroup> owners = new HashSet<>();
                for (TileState s : new ArrayList<>(tileStates.values())) { ViewGroup o = s.owner.get(); if (o != null && o.isAttachedToWindow()) owners.add(o); }
                for (ViewGroup o : owners) { try { XposedHelpers.callMethod(o, "updateResources"); o.invalidate(); } catch (Throwable ignored) { } }
            });
        } catch (Throwable ignored) { }
    }

    private static void trackTile(ViewGroup root) {
        // Inflated-but-unattached tiles are captured too, so the first draw is already shaped.
        Object value = XposedHelpers.callMethod(root, "getColorBackgroundDrawable");
        if (!(value instanceof GradientDrawable)) return;
        GradientDrawable d = (GradientDrawable) value;
        for (GradientDrawable old : new ArrayList<>(tileStates.keySet())) { TileState os = tileStates.get(old); if (os != null && os.owner.get() == root && old != d) { restoreShape(old, os); tileStates.remove(old); } }
        TileState state = tileStates.get(d);
        if (state != null && state.owner.get() != root) { Lx.w("QS: shared tile background instance, shape skipped"); return; }
        if (state == null) { state = new TileState(root, d); tileStates.put(d, state); }
        paintShape(d, state);
    }
    private static void paintShape(GradientDrawable d, TileState state) {
        ViewGroup owner = state.owner.get();
        if (owner == null) return;
        S s = s();
        int id = owner.getClass().getName().equals(WIDE) ? s.shapeLarge : s.shapeSmall;
        if (id == 0) { if (state.shaped) restoreShape(d, state); return; }
        float[] corners = QsArt.shapeCorners(id, d.getBounds().width(), d.getBounds().height());
        if (corners == null) return;
        float[] cur = d.getCornerRadii();
        if (state.shaped && d.getShape() == GradientDrawable.RECTANGLE && cur != null && Arrays.equals(cur, corners)) return;
        shapeWriting = true;
        try { d.setShape(GradientDrawable.RECTANGLE); d.setCornerRadii(corners); state.shaped = true; } finally { shapeWriting = false; }
    }
    private static void restoreShape(GradientDrawable d, TileState s) {
        shapeWriting = true;
        try { d.setShape(s.shape); d.setCornerRadius(s.radius); if (s.corners != null) d.setCornerRadii(copy(s.corners)); s.shaped = false; }
        catch (Throwable t) { Lx.e("QS: tile shape restore", t); }
        finally { shapeWriting = false; }
    }

    // ============================================================================================= brightness slider (BrightnessBackend)

    private static final class SliderEntry {
        final WeakReference<SeekBar> slider; final WeakReference<Drawable> root; final boolean volume;
        SliderEntry(SeekBar s, Drawable r) { slider = new WeakReference<>(s); root = new WeakReference<>(r); volume = s.getClass().getName().equals(CC_VOLUME); }
    }
    /** track / fill GradientDrawable -> slider */
    private static final Map<GradientDrawable, SliderEntry> leaves = new WeakHashMap<>();

    /** Brightness sliders of the shade and the Control Center, plus the Control Center volume slider (same drawable structure). */
    private static boolean sliderName(String name) { return name.equals(SLIDER) || name.equals(CC_SLIDER) || name.equals(CC_VOLUME); }
    private static int styleFor(String className) { S s = s(); return className.equals(CC_VOLUME) ? s.ccVolumeStyle : s.brightnessStyle; }

    private static void brightness(ClassLoader cl) {
        Class<?> slider = H.cls(cl, SLIDER);
        if (slider == null) return;
        if (H.cls(cl, CC_SLIDER) == null) Lx.w("QS: Control Center brightness slider class missing (styling only in the shade)");
        Class<?> volume = H.cls(cl, CC_VOLUME);
        if (volume == null) Lx.w("QS: Control Center volume slider class missing");
        else if (!SeekBar.class.isAssignableFrom(volume)) { Lx.w("QS: CCVolumeSliderView is not a SeekBar, its style is disabled"); volume = null; }
        if (slider.getSuperclass() != SeekBar.class) { Lx.w("QS: ToggleSeekBar is not a SeekBar, brightness style disabled"); return; }
        // ToggleSeekBar / CCVolumeSliderView declare no onDraw of their own: this lands on AbsSeekBar.onDraw, gated by sliderName.
        if (hookUp(slider, "onDraw", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!main()) return;
                String name = p.thisObject.getClass().getName();
                if (!sliderName(name) || (styleFor(name) == 0 && s().sliderColor == 0)) return;
                try { trackSlider((SeekBar) p.thisObject); } catch (Throwable t) { Lx.e("QS: brightness track", t); }
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                // One UI: the small sun inside the start of the track, over the finished track + fill (no thumb on this firmware).
                if (p.hasThrowable() || !main()) return;
                String name = p.thisObject.getClass().getName();
                int style = s().brightnessStyle;
                if (name.equals(CC_VOLUME) || !sliderName(name) || !QsArt.oneUiSlider(style)) return;
                try {
                    SeekBar view = (SeekBar) p.thisObject;
                    if (!tracked(view) || !sliderOwned(view)) return;
                    if (style == QsArt.ONE_UI8_BRIGHTNESS && name.equals(CC_SLIDER) && hasVisibleIcon(view)) return;   // the auto-brightness icon already sits in the fill
                    GradientDrawable[] parts = sliderLayers(view.getProgressDrawable());
                    if (parts == null) return;
                    Rect b = parts[0].getBounds();   // the track leaf carries the real track geometry (layer insets applied)
                    if (b.isEmpty()) return;
                    RectF track = new RectF(b); track.offset(view.getPaddingLeft(), view.getPaddingTop());
                    int range = view.getMax() - view.getMin();
                    float fraction = range <= 0 ? 0f : (view.getProgress() - view.getMin()) / (float) range;
                    if (view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) return;  // glyph placement is LTR only
                    if (style == QsArt.ONE_UI8_BRIGHTNESS && track.width() > 0) fraction = QsArt.oneUi8FillLength(track, fraction) / track.width();  // where the fill ends
                    QsArt.paintBrightnessGlyph((Canvas) p.args[0], track, fraction, dark(view));
                } catch (Throwable t) { Lx.e("QS: brightness glyph", t); }
            }
        }) == 0) { Lx.w("QS: slider onDraw not found, brightness style disabled"); return; }
        Lx.i("QS: brightness hooks ready");
    }
    /** The Control Center slider frame also holds the auto-brightness toggle (CheckBoxZTE) at the start of the track. */
    private static boolean hasVisibleIcon(SeekBar slider) {
        ViewParent parent = slider.getParent();
        if (!(parent instanceof ViewGroup)) return false;
        ViewGroup frame = (ViewGroup) parent;
        for (int i = 0; i < frame.getChildCount(); i++) {
            View c = frame.getChildAt(i);
            if (c != slider && c.getVisibility() == View.VISIBLE && c.getWidth() > 0) return true;
        }
        return false;
    }
    private static boolean tracked(SeekBar slider) {
        for (SliderEntry e : leaves.values()) if (e.slider.get() == slider) return true;
        return false;
    }
    private static boolean sliderOwned(SeekBar slider) {
        if (slider == null || !slider.isAttachedToWindow() || !sliderName(slider.getClass().getName())) return false;
        View view = parentView(slider);
        if (slider.getClass().getName().equals(CC_VOLUME)) {
            // ControlCenterTopPanel.mVolumeSlider is the one in the first row of the Control Center.
            for (int depth = 0; depth < 8 && view != null; depth++, view = parentView(view))
                if (view.getClass().getName().equals(CC_TOP_PANEL)) return field(view, "mVolumeSlider") == slider;
            return CcLayoutHooks.isPanelVolume(slider);   // moved under the small tiles ("bottom of the panel" placement)
        }
        for (int depth = 0; depth < 8 && view != null; depth++, view = parentView(view)) {
            String name = view.getClass().getName();
            boolean expected = name.equals(SLIDER_OWNER) || (slider.getClass().getName().equals(CC_SLIDER) && name.equals(CC_SLIDER_OWNER));
            if (expected) return XposedHelpers.callMethod(view, "getSlider") == slider;
        }
        return false;
    }
    private static GradientDrawable[] sliderLayers(Drawable root) {
        if (!(root instanceof LayerDrawable) || ((LayerDrawable) root).getNumberOfLayers() != 2) return null;
        LayerDrawable layer = (LayerDrawable) root;
        Drawable background = layer.findDrawableByLayerId(android.R.id.background), progress = layer.findDrawableByLayerId(android.R.id.progress);
        if (!(background instanceof GradientDrawable) || !(progress instanceof ClipDrawable) || !(((ClipDrawable) progress).getDrawable() instanceof GradientDrawable)) return null;
        GradientDrawable track = (GradientDrawable) background, fill = (GradientDrawable) ((ClipDrawable) progress).getDrawable();
        if (track == fill || track.getShape() != GradientDrawable.RECTANGLE || fill.getShape() != GradientDrawable.RECTANGLE) return null;
        return new GradientDrawable[]{track, fill};
    }
    private static boolean sliderEligible(SliderEntry entry, GradientDrawable drawable) {
        if (entry == null) return false;
        try {
            SeekBar slider = entry.slider.get(); Drawable root = entry.root.get();
            if (!sliderOwned(slider) || root == null || slider.getProgressDrawable() != root) return false;
            GradientDrawable[] parts = sliderLayers(root);
            return parts != null && (parts[0] == drawable || parts[1] == drawable);
        } catch (Throwable t) { return false; }
    }
    private static void trackSlider(SeekBar slider) {
        Drawable root = slider.getProgressDrawable();
        for (SliderEntry e : leaves.values()) if (e.slider.get() == slider && e.root.get() == root) return;  // already tracked
        leaves.entrySet().removeIf(e -> e.getValue().slider.get() == null || e.getValue().slider.get() == slider);
        if (!sliderOwned(slider)) return;
        GradientDrawable[] parts = sliderLayers(root);
        if (parts == null) { Lx.w("QS: brightness progress drawable is " + (root == null ? "null" : root.getClass().getSimpleName()) + ", style unavailable for " + slider.getClass().getSimpleName()); return; }
        SliderEntry entry = new SliderEntry(slider, root);
        for (GradientDrawable part : parts) { SliderEntry other = leaves.get(part); if (other != null && other.slider.get() != slider) { Lx.w("QS: shared brightness drawable, style skipped"); return; } leaves.put(part, entry); }
        slider.invalidate();
    }

    // ============================================================================================= shared GradientDrawable.draw hook

    /** One hook on GradientDrawable.draw serves the tile finish and the brightness slider style. */
    private static void gradientDraw() {
        H.hook(H.find(GradientDrawable.class, "draw", Canvas.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(final MethodHookParam p) {
                if (tileStates.isEmpty() && leaves.isEmpty()) return;
                if (!main()) return;
                final GradientDrawable d = (GradientDrawable) p.thisObject;
                TileState tile = tileStates.isEmpty() ? null : tileStates.get(d);
                SliderEntry slider = tile != null || leaves.isEmpty() ? null : leaves.get(d);
                if (tile == null && slider == null) return;
                try {
                    final Canvas canvas = (Canvas) p.args[0];
                    QsArt.NativeDraw stock = () -> XposedBridge.invokeOriginalMethod(p.method, p.thisObject, p.args);
                    S s = s();
                    if (tile != null) {
                        ViewGroup owner = tile.owner.get();
                        if (owner == null || s.tileStyle <= 0 || !owner.isAttachedToWindow() || XposedHelpers.callMethod(owner, "getColorBackgroundDrawable") != d) return;
                        if (s.tileStyle == QsArt.ONE_UI8_TILE) { if (isWide(owner) && oneUi(owner) && drawIconCircle(owner, canvas, stock)) p.setResult(null); return; }
                        if (QsArt.drawTile(s.tileStyle, canvas, d, density(owner), stock)) p.setResult(null);
                        return;
                    }
                    int style = slider.volume ? s.ccVolumeStyle : s.brightnessStyle;
                    if ((style <= 0 && s.sliderColor <= 0) || !sliderEligible(slider, d)) return;
                    SeekBar view = slider.slider.get();
                    if (view == null) return;
                    Drawable root = view.getProgressDrawable();
                    GradientDrawable[] parts = sliderLayers(root);
                    boolean fill = parts != null && parts[1] == d;
                    int[] fillColors = s.sliderCustom != null ? s.sliderCustom.clone() : s.sliderColor > 0 ? QsArt.panelColors(view.getContext(), s.sliderColor, dark(view)) : null;
                    if (style == QsArt.ONE_UI8_BRIGHTNESS) {
                        if (fill) { p.setResult(null); return; }   // painted together with the track
                        Rect b = d.getBounds();
                        if (b.isEmpty() || !(root instanceof LayerDrawable)) return;
                        Drawable progress = ((LayerDrawable) root).findDrawableByLayerId(android.R.id.progress);
                        float fraction = progress == null ? 0f : progress.getLevel() / 10000f;
                        QsArt.drawOneUi8Slider(canvas, new RectF(b), fraction, dark(view), fillColors);
                        p.setResult(null);
                        return;
                    }
                    if (style <= 0 && !fill) return;   // only the fill changes colour; the stock track stays
                    if (QsArt.drawBrightness(style, canvas, d, density(view), fill, dark(view), fillColors, stock)) p.setResult(null);
                } catch (Throwable t) { Lx.e("QS: gradient draw", t); }
            }
        });
    }

    // ============================================================================================= Control Center (split shade)

    /**
     * ZTE's two-page shade (com.zte.controlcenter). The firmware switches it with Settings.System "use_control_panel"
     * (ControlPanelController.useControlPanel(), observed live); its tiles are the ordinary QSTileViewCircle / QSTileViewWide,
     * so the shape, finish and One UI colour hooks above already cover them. Added here: forcing the switch on, the
     * small-tile column count (ControlCenterTileLayout fixes 4), icon-only / moved labels for its circle tiles, and the
     * header buttons (CCHeaderView). The volume slider (CCVolumeSliderView) is handled by the brightness machinery.
     */
    private static final Map<Object, Boolean> ccControllers = new WeakHashMap<>();
    private static final Map<ViewGroup, Boolean> ccTileLayouts = new WeakHashMap<>();
    private static final Map<View, Boolean> ccHeaders = new WeakHashMap<>();
    /** header child -> original visibility while we hide it */
    private static final Map<View, Integer> ccHidden = new WeakHashMap<>();
    private static final Map<View, LabelState> ccLabels = new WeakHashMap<>();
    private static final Map<View, Integer> ccLabelVisibility = new WeakHashMap<>();
    private static final String[] CC_HEADER_FIELDS = {"mSearchButton", "mEditButton", "mMultiUserSwitch", "mGlobalActionContainer", "mSettingsContainer", "mDateViewContainer"};
    private static Object qsDimenCompanion;

    private static void controlCenter(ClassLoader cl) {
        Class<?> controller = H.cls(cl, CC_CONTROLLER);
        if (controller != null) {
            H.hook(H.find(controller, "useControlPanel"), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    ccControllers.put(p.thisObject, Boolean.TRUE);
                    if (!p.hasThrowable() && s().ccEnable) p.setResult(true);
                }
            });
        }
        Class<?> layout = H.cls(cl, CC_TILE_LAYOUT);
        if (layout != null) {
            try { layout.getDeclaredField("tileType"); for (String f : new String[]{"mColumns", "mCellMarginHorizontal", "mRecords"}) layout.getSuperclass().getDeclaredField(f); }
            catch (Throwable t) { Lx.w("QS: Control Center tile layout fields missing (" + t + "), column option disabled"); layout = null; }
        }
        if (layout != null) {
            // Only ControlCenterTileLayout's own onMeasure (the top panel overrides it with a fixed layout).
            Method measure = null;
            for (Method m : layout.getDeclaredMethods()) if (m.getName().equals("onMeasure") && m.getParameterTypes().length == 2) measure = m;
            H.hook(measure, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!main() || !(p.thisObject instanceof ViewGroup)) return;
                    try { applyCcColumns((ViewGroup) p.thisObject, View.MeasureSpec.getSize((Integer) p.args[0])); } catch (Throwable t) { Lx.e("QS: Control Center columns", t); }
                }
            });
            Class<?> dim = H.cls(cl, DIMEN);
            try { if (dim != null) qsDimenCompanion = XposedHelpers.getStaticObjectField(dim, "INSTANCE"); } catch (Throwable t) { qsDimenCompanion = null; }
        }
        Class<?> header = H.cls(cl, CC_HEADER);
        if (header != null) {
            XC_MethodHook apply = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!main() || !(p.thisObject instanceof View)) return;
                    try { ccHeaders.put((View) p.thisObject, Boolean.TRUE); applyCcHeader((View) p.thisObject); } catch (Throwable t) { Lx.e("QS: Control Center header", t); }
                }
            };
            int n = 0;
            if (H.hook(H.find(header, "setViewType", int.class), apply)) n++;
            if (H.hook(H.find(header, "setListening", boolean.class), apply)) n++;
            if (H.hook(H.find(header, "updateHeaderResources"), apply)) n++;
            if (n == 0) Lx.w("QS: CCHeaderView methods not found, header options disabled");
        }
        Lx.i("QS: Control Center hooks ready");
    }

    /** Switch flipped: let the firmware re-read "use_control_panel" through the (hooked) getter and rebuild the panel window. */
    private static void refreshControlCenterSwitch() {
        if (ccControllers.isEmpty()) return;
        try {
            if (mainHandler == null) mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> {
                for (Object c : new ArrayList<>(ccControllers.keySet())) {
                    try { XposedHelpers.callMethod(c, "onContentChanged", QsFeatures.CC_SETTING, s().ccEnable ? "1" : "0"); } catch (Throwable t) { Lx.w("QS: Control Center switch not applied live (" + t + "), restart System UI"); }
                }
            });
        } catch (Throwable ignored) { }
    }
    private static void refreshControlCenterViews() {
        try {
            if (mainHandler == null) mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> {
                for (ViewGroup l : new ArrayList<>(ccTileLayouts.keySet())) { try { if (l.isAttachedToWindow()) l.requestLayout(); } catch (Throwable ignored) { } }
                for (View h : new ArrayList<>(ccHeaders.keySet())) { try { if (h.isAttachedToWindow()) applyCcHeader(h); } catch (Throwable ignored) { } }
            });
        } catch (Throwable ignored) { }
    }

    /** Before ControlCenterTileLayout.onMeasure: the selected column count when the fixed-size circle tiles still fit, else stock. */
    private static void applyCcColumns(ViewGroup layout, int width) {
        if (!layout.getClass().getName().equals(CC_TILE_LAYOUT)) return;    // not the top panel (its own class) nor subclasses
        if (XposedHelpers.getIntField(layout, "tileType") != 0) return;     // 0 = the small circle grid
        ccTileLayouts.put(layout, Boolean.TRUE);
        int want = s().ccCols;
        Integer stock = (Integer) XposedHelpers.getAdditionalInstanceField(layout, "rmtweaks:cc-stock-cols");
        int current = n(layout, "mColumns");
        if (stock == null) { stock = current; XposedHelpers.setAdditionalInstanceField(layout, "rmtweaks:cc-stock-cols", stock); }
        int effective = stock;
        if (want >= 2 && want <= 6) {
            int avail = width - layout.getPaddingStart() - layout.getPaddingEnd(), margin = n(layout, "mCellMarginHorizontal");
            int cell = avail > 0 ? (avail - margin * want) / want : 0, min = 0;
            try { if (qsDimenCompanion != null) min = ((Number) XposedHelpers.callMethod(qsDimenCompanion, "getQsPanelCellWidth")).intValue(); } catch (Throwable ignored) { }
            if (want <= 4 || (cell > 0 && cell >= min)) effective = want;
        }
        if (effective != current) put(layout, "mColumns", effective);
        // space between the rows (QsFeatures.QS_CC_TILE_GAP); the circles themselves shrink in applyCcCircle
        try {
            Integer stockV = (Integer) XposedHelpers.getAdditionalInstanceField(layout, "rmtweaks:cc-stock-vgap");
            int curV = n(layout, "mCellMarginVertical");
            if (stockV == null) { stockV = curV; XposedHelpers.setAdditionalInstanceField(layout, "rmtweaks:cc-stock-vgap", stockV); }
            int wantV = stockV + Math.round(QsFeatures.ccGapRowDp(s().ccGap, s().ccGapDp) * layout.getResources().getDisplayMetrics().density);
            if (wantV != curV) put(layout, "mCellMarginVertical", wantV);
        } catch (Throwable t) { Lx.w("QS: Control Center row gap: " + t); }
    }

    /**
     * Control Center round tile: its circle is child 0 (a LinearLayout / FrameLayout of iconBgSize x iconBgSize holding the
     * icon). Scaling that box down leaves clear space between neighbouring circles while the cell, icon and label keep
     * their size. Stock size is remembered per tile, so "Stock" restores it.
     */
    private static void applyCcCircle(View tile) {
        if (!tile.getClass().getName().equals(CIRCLE) || !(tile instanceof ViewGroup)) return;
        View parent = parentView(tile);
        if (parent == null || !parent.getClass().getName().equals(CC_TILE_LAYOUT)) return;
        ViewGroup g = (ViewGroup) tile;
        if (g.getChildCount() == 0) return;
        View box = g.getChildAt(0);
        ViewGroup.LayoutParams lp = box.getLayoutParams();
        if (lp == null || lp.width <= 0 || lp.height <= 0) return;
        Object stockTag = XposedHelpers.getAdditionalInstanceField(box, "rmtweaks:cc-circle");
        int stock = stockTag instanceof Integer ? (Integer) stockTag : lp.width;
        if (!(stockTag instanceof Integer)) XposedHelpers.setAdditionalInstanceField(box, "rmtweaks:cc-circle", stock);
        int want = Math.round(stock * QsFeatures.ccGapScale(s().ccGap, s().ccGapDp));
        if (lp.width == want && lp.height == want) return;
        lp.width = want; lp.height = want;
        box.setLayoutParams(lp);
    }

    /** Circle tiles of the Control Center grid follow the shade's label options (icons only / label above / line limit). */
    private static void applyCcLabel(View tile) {
        if (!tile.getClass().getName().equals(CIRCLE)) return;
        View parent = parentView(tile);
        if (parent == null || !parent.getClass().getName().equals(CC_TILE_LAYOUT)) return;
        S s = s();
        LabelState state = ccLabels.get(tile);
        if (s.labelPos == 0 && s.labelLines == 0) { if (state != null) restoreCcLabel(tile, state); return; }
        Object c = XposedHelpers.callMethod(tile, "getLabelContainer"), t = XposedHelpers.callMethod(tile, "getLabel");
        if (!(c instanceof ViewGroup) || !(tile instanceof ViewGroup) || ((ViewGroup) c).getParent() != tile || !(t instanceof TextView)) return;
        ViewGroup container = (ViewGroup) c, owner = (ViewGroup) tile; TextView text = (TextView) t;
        if (state == null) { state = new LabelState(container, owner, text); ccLabels.put(tile, state); }
        if (s.labelPos == 1 && owner.indexOfChild(container) != 0) { owner.removeView(container); owner.addView(container, 0); state.moved = true; }
        else if (s.labelPos != 1 && state.moved && owner.indexOfChild(container) == 0) { owner.removeView(container); owner.addView(container, Math.min(state.index, owner.getChildCount())); state.moved = false; }
        if (s.labelPos == 2) { if (!ccLabelVisibility.containsKey(container)) ccLabelVisibility.put(container, container.getVisibility()); if (container.getVisibility() != View.INVISIBLE) container.setVisibility(View.INVISIBLE); }
        else { Integer v = ccLabelVisibility.remove(container); if (v != null) container.setVisibility(v); }
        if (s.labelLines != 0) { if (text.getMaxLines() != s.labelLines) text.setMaxLines(s.labelLines); }
        else if (text.getMaxLines() != state.maxLines) text.setMaxLines(state.maxLines);
    }
    private static void restoreCcLabel(View tile, LabelState state) {
        ccLabels.remove(tile);
        Integer v = ccLabelVisibility.remove(state.container);
        if (v != null) state.container.setVisibility(v);
        state.text.setMaxLines(state.maxLines);
        if (state.moved && state.container.getParent() == state.parent) { state.parent.removeView(state.container); state.parent.addView(state.container, Math.min(state.index, state.parent.getChildCount())); }
    }

    /** Hides / restores the header buttons named by the qs_cc_hide_* switches (bit i of S.ccHide = CC_HEADER_FIELDS[i]). */
    static void applyCcHeader(View header) {
        applyCcHideMask(header);
        try { CcOneUiHooks.header(header); } catch (Throwable t) { Lx.e("QS: One UI 8 header", t); }
    }
    private static void applyCcHideMask(View header) {
        if (!header.getClass().getName().equals(CC_HEADER)) return;
        int mask = s().ccHide;
        for (int i = 0; i < CC_HEADER_FIELDS.length; i++) {
            Object o;
            try { o = field(header, CC_HEADER_FIELDS[i]); } catch (Throwable t) { continue; }
            if (!(o instanceof View)) continue;
            View v = (View) o;
            boolean hide = (mask & (1 << i)) != 0;
            if (hide) { if (!ccHidden.containsKey(v)) ccHidden.put(v, v.getVisibility()); if (v.getVisibility() != View.GONE) v.setVisibility(View.GONE); }
            else { Integer original = ccHidden.remove(v); if (original != null && v.getVisibility() == View.GONE) v.setVisibility(original); }
        }
    }
}
