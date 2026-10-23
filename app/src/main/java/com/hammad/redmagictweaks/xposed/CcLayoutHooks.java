package com.hammad.redmagictweaks.xposed;

import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import com.hammad.redmagictweaks.QsFeatures;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Control Center (com.zte.controlcenter, the second page of the split shade): where the brightness and volume bars sit,
 * plus the icon colour of the One UI 8 slider style.
 *
 * ControlCenterTopPanel (cc_top_panel.xml) lays out, in portrait, the media player (when shown) and the four big tiles in
 * two columns, and the brightness bar (CCBrightnessToggleSliderView, cc_toggle_slider.xml) and the volume bar (FrameLayout
 * volume_slider) as 62 x 142 dp vertical bars in the right column. Each bar is a SeekBar rotated 270 degrees inside a
 * FrameLayout with its icon at the bottom. Touch reaches the SeekBar through normal child dispatch (or
 * CCToggleSliderView.handleSliderTouchEvent, which applies the slider's inverse matrix), so un-rotating it is enough to
 * get a working horizontal bar.
 *
 * Placements (QsFeatures.QS_CC_SLIDER_POS): 0 stock; 1 vertical bars on the left (the stock layout mirrored);
 * 2 / 3 full-width horizontal bars under / above the big tiles (the panel's onMeasure / onLayout are redone);
 * 4 the bars are re-parented into a holder under the small-tile grid (TileContentContainer) and moved back for any
 * other choice or in landscape. The brightness mirror shown while dragging gets the same orientation and size.
 * Everything is restored to the recorded stock geometry when the option goes back to stock.
 */
final class CcLayoutHooks {
    private CcLayoutHooks() {}

    private static final String TOP = "com.zte.controlcenter.view.ControlCenterTopPanel";
    private static final String CONTAINER = "com.zte.controlcenter.view.TileContentContainer";
    static final String HOLDER_TAG = "rmtweaks:cc-sliders";

    /** ONEUI = the One UI 8 page (CcOneUiHooks): two pills in the panel, the bars in its slider card under the grid. */
    static final int STOCK = 0, LEFT = 1, BELOW = 2, ABOVE = 3, BOTTOM = 4, ONEUI = 5;

    // ------------------------------------------------------------------------------------------ settings snapshot

    private static volatile int pos, sizeDp = QsFeatures.CC_SLIDER_SIZE_DP[0];
    private static volatile long at;
    private static boolean disabled;

    private static void refresh() {
        long now = System.currentTimeMillis();
        if (now - at < 1000) return;
        at = now;
        int p = Cfg.choice(QsFeatures.QS_CC_SLIDER_POS);
        if (p < 0 || p > BOTTOM) p = STOCK;
        int sz = Math.max(24, Math.min(120, Cfg.num(QsFeatures.QS_CC_SLIDER_SIZE, QsFeatures.sliderSizeDp(Cfg.choice(QsFeatures.QS_CC_SLIDER_SIZE)))));
        pos = p; sizeDp = sz;
    }

    /** What the panel should show: the chosen placement, or stock in landscape / when the panel looks unexpected. */
    private static int mode(ViewGroup top) {
        if (disabled) return STOCK;
        refresh();
        int m = CcOneUiHooks.layoutOn() ? ONEUI : pos;
        if (m == STOCK) return STOCK;
        try {
            if (top.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) return STOCK;
            if (bar(top, "brightness") == null || bar(top, "volume") == null || volumeSlider(top) == null) return STOCK;
            if (tiles(top).size() < (m == ONEUI ? 2 : 4)) return STOCK;
            if (m == ONEUI && container(top) == null) return STOCK;
        } catch (Throwable t) { return STOCK; }
        return m;
    }
    /** For CcOneUiHooks: the placement this top panel is in right now. */
    static int modeOf(ViewGroup top) { try { return mode(top); } catch (Throwable t) { return STOCK; } }

    private static void fail(String where, Throwable t) {
        if (!disabled) Lx.e("QS: Control Center slider placement (" + where + "), back to stock", t);
        disabled = true;
    }

    // ------------------------------------------------------------------------------------------ install

    private static final Map<ViewGroup, Boolean> panels = new WeakHashMap<>();
    /** panel -> key of what was last applied (placement, thickness, styles) */
    private static final Map<ViewGroup, Integer> applied = new WeakHashMap<>();

    static void install(ClassLoader cl) {
        Class<?> top = H.cls(cl, TOP);
        if (top == null) { Lx.w("QS: ControlCenterTopPanel missing, brightness/volume placement disabled"); return; }
        Method measure, layout, listen = null;
        try {
            for (String f : new String[]{"brightness", "volume", "player", "mVolumeSlider", "firstRowHeight"}) top.getDeclaredField(f);
            for (String f : new String[]{"mCellWidth", "mCellHeight", "mCellMarginVertical", "mRecords"}) XposedHelpers.findField(top, f);
            top.getDeclaredMethod("getTileTop", int.class);
            measure = top.getDeclaredMethod("onMeasure", int.class, int.class);
            layout = top.getDeclaredMethod("onLayout", boolean.class, int.class, int.class, int.class, int.class);
            try { listen = top.getDeclaredMethod("setListening", boolean.class); } catch (NoSuchMethodException ignored) { }
        } catch (Throwable t) { Lx.w("QS: Control Center panel differs from 11.0.11 (" + t + "), brightness/volume placement disabled"); return; }
        H.hook(measure, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!main()) return;
                try { beforeMeasure((ViewGroup) p.thisObject); } catch (Throwable t) { fail("measure", t); }
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!main() || p.hasThrowable()) return;
                try { afterMeasure((ViewGroup) p.thisObject); } catch (Throwable t) { fail("measure", t); }
            }
        });
        H.hook(layout, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!main()) return;
                try { if (beforeLayout((ViewGroup) p.thisObject)) p.setResult(null); } catch (Throwable t) { fail("layout", t); }
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!main() || p.hasThrowable()) return;
                try { afterLayout((ViewGroup) p.thisObject); } catch (Throwable t) { fail("layout", t); }
            }
        });
        if (listen != null) H.hook(listen, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!main() || !Boolean.TRUE.equals(p.args[0])) return;
                try { onOpen((ViewGroup) p.thisObject); } catch (Throwable t) { Lx.e("QS: Control Center slider refresh", t); }
            }
        });
        guardTouchChecks(cl);
        Lx.i("QS: Control Center brightness/volume placement hooks ready");
    }

    /**
     * MfvNestedScrollView / ControlPanelWindowView.startOnBrightnessOrVolumeView look the bars up with
     * topPanel.requireViewById(...), which throws once a bar lives outside the top panel (bottom placement, One UI 8
     * slider card). Answered here from the bars themselves, wherever they are.
     */
    private static void guardTouchChecks(ClassLoader cl) {
        for (String name : new String[]{"com.zte.controlcenter.view.MfvNestedScrollView", "com.zte.controlcenter.ControlPanelWindowView"}) {
            Class<?> c = H.cls(cl, name);
            Method m = c == null ? null : H.find(c, "startOnBrightnessOrVolumeView", float.class, float.class);
            if (m == null) { Lx.w("QS: " + name + "#startOnBrightnessOrVolumeView not found"); continue; }
            H.hook(m, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        View root = (View) p.thisObject;
                        float x = (Float) p.args[0], y = (Float) p.args[1];
                        for (ViewGroup top : new ArrayList<>(panels.keySet())) {
                            if (top == null || !top.isAttachedToWindow() || !within(top, root)) continue;
                            p.setResult(inside(bar(top, "brightness"), x, y) || inside(bar(top, "volume"), x, y));
                            return;
                        }
                        try { p.setResult(de.robv.android.xposed.XposedBridge.invokeOriginalMethod(p.method, p.thisObject, p.args)); }
                        catch (Throwable t) { p.setResult(false); }
                    } catch (Throwable t) { p.setResult(false); }
                }
            });
        }
    }
    private static boolean within(View v, View ancestor) {
        for (ViewParent p = v.getParent(); p != null; p = p.getParent()) if (p == ancestor) return true;
        return false;
    }
    private static boolean inside(View v, float x, float y) {
        if (v == null || !v.isShown()) return false;
        int[] l = new int[2];
        v.getLocationOnScreen(l);
        return x >= l[0] && x <= l[0] + v.getWidth() && y >= l[1] && y <= l[1] + v.getHeight();
    }

    private static boolean main() { return Looper.myLooper() == Looper.getMainLooper(); }

    private static int key(ViewGroup top) {
        QsHooks.S s = QsHooks.s();
        return (((mode(top) * 100 + sizeDp) * 10 + s.brightnessStyle) * 10 + s.ccVolumeStyle) * 4 + CcOneUiHooks.partsKey();
    }

    /** Panel opened: pick up a changed setting (the views are kept between openings, so nothing else re-lays them out). */
    private static void onOpen(ViewGroup top) {
        panels.put(top, Boolean.TRUE);
        refresh();
        Integer was = applied.get(top);
        if (was == null || was != key(top)) top.requestLayout();
    }

    // ------------------------------------------------------------------------------------------ measure / layout

    private static void beforeMeasure(ViewGroup top) {
        panels.put(top, Boolean.TRUE);
        refresh();
        int m = mode(top);
        int h = dp(top, sizeDp);
        boolean horizontal = m >= BELOW;
        View b = bar(top, "brightness");
        orientBrightness(b, horizontal ? h : 0, true);
        orientVolume(top, horizontal ? h : 0);
        syncStructure(top, m);
        // cosmetic parts: a failure here must not switch the placement off
        try { tintIcons(top, b); } catch (Throwable t) { Lx.e("QS: One UI 8 slider icons", t); }
        try { syncMirror(top, b, horizontal, h, m == ONEUI && b != null && b.getWidth() > 0 ? b.getWidth() : 0); } catch (Throwable t) { Lx.e("QS: brightness mirror", t); }
        applied.put(top, key(top));
    }

    private static void afterMeasure(ViewGroup top) {
        int m = mode(top);
        if (m == STOCK || m == LEFT) return;
        G g = geometry(top);
        if (g == null) return;
        boolean held = held(top);
        if (m == BOTTOM && !inHolder(top)) { m = BELOW; held = false; }       // the move to the holder is posted; meanwhile lay them out under the tiles
        if (m == ONEUI && !CcOneUiHooks.inStack(top)) { m = BELOW; held = false; }
        int h = dp(top, sizeDp);
        // Always: the stock onMeasure just measured both bars as 62 x 142 dp, even when they live in the holder (whose
        // own measure may be skipped as unchanged); full width x h is also exactly what the holder gives them. In the One
        // UI slider card they share the row with a button: their current width is kept.
        for (String f : new String[]{"brightness", "volume"}) {
            View x = bar(top, f);
            if (m != ONEUI) x.measure(exactly(g.full), exactly(h));
            else if (x.getWidth() > 0) x.measure(exactly(x.getWidth()), exactly(h));
        }
        View player = player(top);
        if (player != null) player.measure(exactly(g.full), exactly(g.fr));
        int stockHeight = top.getMeasuredHeight();
        int stockBottom = (Integer) XposedHelpers.callMethod(top, "getTileTop", 3) + g.th;
        int slack = Math.max(0, stockHeight - stockBottom);
        int bottom = place(top, m, g, h, held, false);
        XposedHelpers.callMethod(top, "setMeasuredDimension", top.getMeasuredWidth(), bottom + slack);
        if (m == ONEUI) {
            int width = top.getMeasuredWidth();
            try { CcOneUiHooks.sync(top, g.c0, Math.max(0, width - (g.c0 + g.full)), g.gap, h); } catch (Throwable t) { CcOneUiHooks.fail("sync", t); }
        }
    }

    /** Replaces the stock onLayout for the horizontal placements. */
    private static boolean beforeLayout(ViewGroup top) {
        int m = mode(top);
        if (m == STOCK || m == LEFT) return false;
        G g = geometry(top);
        if (g == null) return false;
        boolean held = held(top);
        if (m == BOTTOM && !inHolder(top)) { m = BELOW; held = false; }
        if (m == ONEUI && !CcOneUiHooks.inStack(top)) { m = BELOW; held = false; }
        place(top, m, g, dp(top, sizeDp), held, true);
        return true;
    }

    /** Vertical bars on the left: the stock layout mirrored (bars keep brightness first). */
    private static void afterLayout(ViewGroup top) {
        int mode = mode(top);
        if (mode == STOCK) { fewTiles(top); return; }
        if (mode != LEFT) return;
        G g = geometry(top);
        if (g == null) return;
        int axis = g.c0 + g.c1 + g.cw;
        View b = bar(top, "brightness"), v = bar(top, "volume"), player = player(top);
        if (player != null) flip(player, axis);
        int bl = axis - b.getRight(), vl = axis - v.getRight();
        // after mirroring the volume bar would come first: swap the two x positions
        b.layout(vl, b.getTop(), vl + b.getWidth(), b.getBottom());
        v.layout(bl, v.getTop(), bl + v.getWidth(), v.getBottom());
        if (player == null) for (View t : tiles(top)) flip(t, axis);
    }
    /**
     * Stock onLayout only places the big tiles when there are four (layoutTileRecords). With the One UI 8 split (two big
     * tiles) and the stock layout in use (landscape, or the layout switched off until the tiles are redistributed), the
     * remaining ones are placed with the panel's own slot functions.
     */
    private static void fewTiles(ViewGroup top) {
        Object rec = XposedHelpers.getObjectField(top, "mRecords");
        if (!(rec instanceof List)) return;
        List<?> records = new ArrayList<>((List<?>) rec);
        if (records.isEmpty() || records.size() >= 4) return;
        for (int i = 0; i < records.size(); i++) {
            Object t = XposedHelpers.getObjectField(records.get(i), "tileView");
            if (!(t instanceof View)) continue;
            View v = (View) t;
            int x = (Integer) XposedHelpers.callMethod(top, "getTileLeft", i), y = (Integer) XposedHelpers.callMethod(top, "getTileTop", i);
            v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
        }
    }
    private static void flip(View x, int axis) { int l = axis - x.getRight(); x.layout(l, x.getTop(), l + x.getWidth(), x.getBottom()); }

    /** Positions (and optionally lays out) everything of the panel for a horizontal placement; returns the content bottom. */
    private static int place(ViewGroup top, int m, G g, int h, boolean held, boolean layout) {
        View b = bar(top, "brightness"), v = bar(top, "volume"), player = player(top);
        List<View> tiles = tiles(top);
        int y = g.top;
        if (m == ABOVE && !held) y = bars(b, v, g, h, y, layout) + g.gap;
        if (player != null) {
            if (layout) player.layout(g.c0, y, g.c0 + g.full, y + g.fr);
            y += g.fr + g.gap;
        }
        int n = Math.min(4, tiles.size());
        for (int i = 0; i < n; i++) {
            View t = tiles.get(i);
            int x = i % 2 == 0 ? g.c0 : g.c1, ty = y + (i / 2) * (g.th + g.gap);
            if (layout) t.layout(x, ty, x + t.getMeasuredWidth(), ty + t.getMeasuredHeight());
        }
        int rows = (n + 1) / 2;
        if (rows > 0) y += rows * g.th + (rows - 1) * g.gap;
        if (m != ABOVE && !held) y = bars(b, v, g, h, y + g.gap, layout);
        return y;
    }
    private static int bars(View b, View v, G g, int h, int y, boolean layout) {
        if (layout) b.layout(g.c0, y, g.c0 + g.full, y + h);
        y += h + g.gap;
        if (layout) v.layout(g.c0, y, g.c0 + g.full, y + h);
        return y + h;
    }

    /** Column / row geometry of the panel (ControlCenterTileLayout / MfvTileLayout fields, valid after its own measure). */
    private static final class G { int c0, c1, cw, th, gap, top, fr, full; }
    private static G geometry(ViewGroup top) {
        G g = new G();
        g.c0 = (Integer) XposedHelpers.callMethod(top, "getColumnStart", 0);
        g.c1 = (Integer) XposedHelpers.callMethod(top, "getColumnStart", 1);
        g.cw = XposedHelpers.getIntField(top, "mCellWidth");
        g.th = XposedHelpers.getIntField(top, "mCellHeight");
        g.gap = XposedHelpers.getIntField(top, "mCellMarginVertical");
        g.top = (Integer) XposedHelpers.callMethod(top, "getRowTop", 0);
        g.fr = XposedHelpers.getIntField(top, "firstRowHeight");
        g.full = g.c1 + g.cw - g.c0;
        if (g.full <= 0 || g.th <= 0 || g.cw <= 0) return null;
        return g;
    }

    // ------------------------------------------------------------------------------------------ host views

    private static View bar(ViewGroup top, String field) {
        Object o = XposedHelpers.getObjectField(top, field);
        return o instanceof View ? (View) o : null;
    }
    private static SeekBar volumeSlider(ViewGroup top) {
        Object o = XposedHelpers.getObjectField(top, "mVolumeSlider");
        return o instanceof SeekBar ? (SeekBar) o : null;
    }
    /** The media player while it is shown inside the top panel (not when it moved to the One UI 8 media card). */
    private static View player(ViewGroup top) {
        View p = bar(top, "player");
        return p != null && p.getVisibility() == View.VISIBLE && p.getParent() == top ? p : null;
    }
    private static List<View> tiles(ViewGroup top) {
        List<View> out = new ArrayList<>();
        Object rec = XposedHelpers.getObjectField(top, "mRecords");
        if (!(rec instanceof List)) return out;
        for (Object r : new ArrayList<>((List<?>) rec)) {
            if (out.size() == 4) break;
            Object t = r == null ? null : XposedHelpers.getObjectField(r, "tileView");
            if (t instanceof View) out.add((View) t);
        }
        return out;
    }

    /** The Control Center volume SeekBar, wherever it currently lives (QsHooks' slider styles use this once it moved). */
    static boolean isPanelVolume(SeekBar s) {
        if (s == null) return false;
        for (ViewGroup top : new ArrayList<>(panels.keySet())) {
            try { if (top != null && volumeSlider(top) == s) return true; } catch (Throwable ignored) { }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------ bar orientation

    /** Stock geometry of a SeekBar, its icon and (brightness only) its frame, recorded before the first change. */
    private static final class Geo {
        int sw, sh, maxH; float rot; int[] layerGravity;
        boolean icon; int iGravity, iLeft, iRight, iStart, iBottom;
        int frameH = Integer.MIN_VALUE;
    }
    private static final Map<SeekBar, Geo> geo = new WeakHashMap<>();

    private static void orientBrightness(View b, int h, boolean inMeasure) {
        if (b == null) return;
        Object s = XposedHelpers.callMethod(b, "getSlider"), icon = XposedHelpers.callMethod(b, "getMToggle");
        if (!(s instanceof SeekBar)) return;
        SeekBar slider = (SeekBar) s;
        View frame = slider.getParent() instanceof View && slider.getParent() != b ? (View) slider.getParent() : null;
        if (orient(slider, icon instanceof View ? (View) icon : null, frame, h) && !inMeasure) b.requestLayout();
    }
    private static void orientVolume(ViewGroup top, int h) {
        SeekBar s = volumeSlider(top);
        if (s == null) return;
        View icon = null;
        try { Object i = XposedHelpers.getObjectField(s, "mVolumeIcon"); if (i instanceof View) icon = (View) i; } catch (Throwable ignored) { }
        orient(s, icon, null, h);   // the volume FrameLayout itself is sized by the panel / holder
    }

    /** h > 0: un-rotated full-width bar of height h with the icon at the start; h == 0: back to the recorded stock geometry. */
    private static boolean orient(SeekBar s, View icon, View frame, int h) {
        if (!(s.getLayoutParams() instanceof FrameLayout.LayoutParams)) return false;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) s.getLayoutParams();
        Geo g = geo.get(s);
        boolean changed = false;
        if (h > 0) {
            if (g == null) { g = save(s, icon, frame); geo.put(s, g); }
            if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT || lp.height != h) { lp.width = ViewGroup.LayoutParams.MATCH_PARENT; lp.height = h; changed = true; }
            if (s.getRotation() != 0f) { s.setRotation(0f); changed = true; }
            if (s.getMaxHeight() < h) s.setMaxHeight(h);
            changed |= layers(s, g, true);
            if (icon != null && g.icon) {
                FrameLayout.LayoutParams il = (FrameLayout.LayoutParams) icon.getLayoutParams();
                int iw = il.width > 0 ? il.width : icon.getMeasuredWidth();
                int start = Math.max(0, (h - iw) / 2), gravity = Gravity.START | Gravity.CENTER_VERTICAL;
                if (il.gravity != gravity || il.getMarginStart() != start || il.bottomMargin != 0) {
                    il.gravity = gravity; il.leftMargin = start; il.setMarginStart(start); il.bottomMargin = 0; changed = true;
                }
            }
            if (frame != null && frame.getLayoutParams() != null && frame.getLayoutParams().height != h) { frame.getLayoutParams().height = h; changed = true; }
        } else {
            if (g == null) return false;
            geo.remove(s);
            lp.width = g.sw; lp.height = g.sh;
            s.setRotation(g.rot);
            if (s.getMaxHeight() != g.maxH) s.setMaxHeight(g.maxH);
            layers(s, g, false);
            if (icon != null && g.icon && icon.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams il = (FrameLayout.LayoutParams) icon.getLayoutParams();
                il.gravity = g.iGravity; il.leftMargin = g.iLeft; il.rightMargin = g.iRight; il.setMarginStart(g.iStart); il.bottomMargin = g.iBottom;
            }
            if (frame != null && frame.getLayoutParams() != null && g.frameH != Integer.MIN_VALUE) frame.getLayoutParams().height = g.frameH;
            changed = true;
        }
        if (changed) {
            // LayoutParams were edited in place (we may be inside the panel's measure): force the subtree to re-measure.
            s.forceLayout();
            if (icon != null) icon.forceLayout();
            for (ViewParent p = s.getParent(); p instanceof View; p = p.getParent()) {
                View pv = (View) p;
                pv.forceLayout();
                if (pv.getClass().getName().equals(TOP) || HOLDER_TAG.equals(pv.getTag())) break;
            }
            s.invalidate();
        }
        return changed;
    }

    private static Geo save(SeekBar s, View icon, View frame) {
        Geo g = new Geo();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) s.getLayoutParams();
        g.sw = lp.width; g.sh = lp.height; g.rot = s.getRotation(); g.maxH = s.getMaxHeight();
        Drawable d = s.getProgressDrawable();
        if (d instanceof LayerDrawable) {
            LayerDrawable l = (LayerDrawable) d;
            g.layerGravity = new int[l.getNumberOfLayers()];
            for (int i = 0; i < g.layerGravity.length; i++) g.layerGravity[i] = l.getLayerGravity(i);
        }
        if (icon != null && icon.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams il = (FrameLayout.LayoutParams) icon.getLayoutParams();
            g.icon = true; g.iGravity = il.gravity; g.iLeft = il.leftMargin; g.iRight = il.rightMargin; g.iStart = il.getMarginStart(); g.iBottom = il.bottomMargin;
        }
        if (frame != null && frame.getLayoutParams() != null) g.frameH = frame.getLayoutParams().height;
        return g;
    }

    /**
     * The track / fill layers of toggle_slider_progress are centred at their intrinsic 62 dp height (the stock bar
     * thickness). Horizontal bars of another thickness need them to fill the track instead. Re-applied every measure
     * because ControlCenterTopPanel.onConfigurationChanged installs a fresh volume drawable.
     */
    private static boolean layers(SeekBar s, Geo g, boolean fill) {
        Drawable d = s.getProgressDrawable();
        if (!(d instanceof LayerDrawable)) return false;
        LayerDrawable l = (LayerDrawable) d;
        boolean changed = false;
        for (int i = 0; i < l.getNumberOfLayers(); i++) {
            int want = fill ? Gravity.FILL : (g.layerGravity != null && i < g.layerGravity.length ? g.layerGravity[i] : l.getLayerGravity(i));
            if (l.getLayerGravity(i) != want) { l.setLayerGravity(i, want); changed = true; }
        }
        if (changed) {
            Rect r = l.copyBounds();
            l.setBounds(0, 0, 0, 0);   // setLayerGravity does not re-run the layer bounds; a real bounds change does
            l.setBounds(r);
        }
        return changed;
    }

    // ------------------------------------------------------------------------------------------ One UI 8 icon colour

    /** view -> {stock tint list, stock alpha} while we tint it */
    private static final Map<View, Object[]> tinted = new WeakHashMap<>();

    private static void tintIcons(ViewGroup top, View b) {
        QsHooks.S s = QsHooks.s();
        boolean dark = (top.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int color = QsArt.oneUiIconOverFill(dark);
        boolean brightness = s.brightnessStyle == QsArt.ONE_UI8_BRIGHTNESS;
        if (b != null) {
            tint(XposedHelpers.callMethod(b, "getMToggle"), brightness, color, false);
            Object mirror = null;
            try { mirror = XposedHelpers.callMethod(b, "getMirror"); } catch (Throwable ignored) { }
            if (mirror != null) { try { tint(XposedHelpers.callMethod(mirror, "getMToggle"), brightness, color, false); } catch (Throwable ignored) { } }
        }
        SeekBar vs = volumeSlider(top);
        if (vs != null) {
            Object icon = null;
            try { icon = XposedHelpers.getObjectField(vs, "mVolumeIcon"); } catch (Throwable ignored) { }
            tint(icon, s.ccVolumeStyle == QsArt.ONE_UI8_BRIGHTNESS, color, true);
        }
    }
    private static void tint(Object o, boolean on, int color, boolean image) {
        if (!(o instanceof View)) return;
        View v = (View) o;
        Object[] st = tinted.get(v);
        if (on) {
            if (st == null) { st = new Object[]{image && v instanceof ImageView ? ((ImageView) v).getImageTintList() : v.getBackgroundTintList(), v.getAlpha()}; tinted.put(v, st); }
            ColorStateList want = ColorStateList.valueOf(color);
            if (image && v instanceof ImageView) { ColorStateList cur = ((ImageView) v).getImageTintList(); if (cur == null || cur.getDefaultColor() != color) ((ImageView) v).setImageTintList(want); }
            else { ColorStateList cur = v.getBackgroundTintList(); if (cur == null || cur.getDefaultColor() != color) v.setBackgroundTintList(want); }
            if (v.getAlpha() != 1f) v.setAlpha(1f);
        } else if (st != null) {
            tinted.remove(v);
            if (image && v instanceof ImageView) ((ImageView) v).setImageTintList((ColorStateList) st[0]); else v.setBackgroundTintList((ColorStateList) st[0]);
            v.setAlpha((Float) st[1]);
        }
    }

    // ------------------------------------------------------------------------------------------ "bottom of the panel"

    private static final Map<ViewGroup, LinearLayout> holders = new WeakHashMap<>();
    /** bar -> {stock LayoutParams in the panel, stock child index} */
    private static final Map<View, Object[]> homes = new WeakHashMap<>();
    private static final Map<ViewGroup, Boolean> pending = new WeakHashMap<>();

    private static ViewGroup container(ViewGroup top) {
        ViewParent p = top.getParent();
        return p instanceof ViewGroup && p.getClass().getName().equals(CONTAINER) ? (ViewGroup) p : null;
    }
    /** The bars live outside the top panel (holder or One UI 8 slider card). */
    private static boolean held(ViewGroup top) { return inHolder(top) || CcOneUiHooks.inStack(top); }
    private static boolean inHolder(ViewGroup top) {
        View b = bar(top, "brightness");
        LinearLayout holder = holders.get(top);
        return b != null && holder != null && b.getParent() == holder;
    }

    /** Bars must move (in a posted step: never change the hierarchy inside a measure pass); or keep the holder in shape. */
    private static void syncStructure(final ViewGroup top, int m) {
        boolean hasContainer = container(top) != null;
        boolean wantHolder = m == BOTTOM && hasContainer, wantStack = m == ONEUI && hasContainer;
        boolean inHolder = inHolder(top), inStack = CcOneUiHooks.inStack(top);
        if (wantHolder != inHolder || wantStack != inStack || (wantStack && !CcOneUiHooks.settled(top))) { post(top); return; }
        if (inHolder) syncHolder(top);
        else {
            LinearLayout holder = holders.get(top);
            if (holder != null && holder.getChildCount() == 0 && holder.getParent() != null) post(top);
        }
        if (!inStack && CcOneUiHooks.hasStack(top)) post(top);
    }
    private static void post(final ViewGroup top) {
        if (pending.containsKey(top)) return;
        pending.put(top, Boolean.TRUE);
        top.post(() -> {
            pending.remove(top);
            try { restructure(top); } catch (Throwable t) { fail("move bars", t); try { restructure(top); } catch (Throwable ignored) { } }
        });
    }

    private static void restructure(ViewGroup top) {
        View b = bar(top, "brightness"), v = bar(top, "volume");
        if (b == null || v == null) return;
        ViewGroup container = container(top);
        int m = mode(top);
        boolean want = m == BOTTOM && container != null;
        LinearLayout holder = holders.get(top);
        if (m == ONEUI && container != null) {
            ViewGroup[] rows;
            try { rows = CcOneUiHooks.attachStack(top, container); }
            catch (Throwable t) { CcOneUiHooks.fail("build", t); rows = null; }
            if (rows != null) {
                int h = dp(top, sizeDp);
                View[] bars = {b, v};
                for (int i = 0; i < 2; i++) {
                    View x = bars[i];
                    if (x.getParent() == rows[i]) continue;
                    ViewParent from = x.getParent();
                    if (from == top) homes.put(x, new Object[]{x.getLayoutParams(), top.indexOfChild(x)});
                    if (from instanceof ViewGroup) ((ViewGroup) from).removeView(x);
                    rows[i].addView(x, 0, new LinearLayout.LayoutParams(0, h, 1f));
                }
                if (holder != null && holder.getParent() instanceof ViewGroup) ((ViewGroup) holder.getParent()).removeView(holder);
                top.requestLayout();
                return;
            }
            want = false;
        }
        if (want) {
            try { CcOneUiHooks.detachStack(top); } catch (Throwable t) { Lx.e("QS: One UI 8 stack removal", t); }
            if (holder == null) {
                holder = new LinearLayout(top.getContext());
                holder.setOrientation(LinearLayout.VERTICAL);
                holder.setTag(HOLDER_TAG);
                holder.setClipChildren(false);
                holder.setClipToPadding(false);
                holders.put(top, holder);
            }
            if (holder.getParent() != container) {
                if (holder.getParent() instanceof ViewGroup) ((ViewGroup) holder.getParent()).removeView(holder);
                container.addView(holder, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            int h = dp(top, sizeDp);
            for (View x : new View[]{b, v}) {
                if (x.getParent() == holder) continue;
                ViewParent from = x.getParent();
                if (from == top) homes.put(x, new Object[]{x.getLayoutParams(), top.indexOfChild(x)});
                if (from instanceof ViewGroup) ((ViewGroup) from).removeView(x);
                holder.addView(x, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
            }
            syncHolder(top);
        } else {
            for (View x : new View[]{b, v}) {
                if (x.getParent() == top) continue;
                if (x.getParent() instanceof ViewGroup) ((ViewGroup) x.getParent()).removeView(x);
                Object[] home = homes.remove(x);
                ViewGroup.LayoutParams lp = home != null && home[0] instanceof ViewGroup.LayoutParams ? (ViewGroup.LayoutParams) home[0]
                        : new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                int index = home != null && home[1] instanceof Integer ? (Integer) home[1] : top.getChildCount();
                top.addView(x, Math.max(0, Math.min(index, top.getChildCount())), lp);
            }
            if (holder != null && holder.getParent() instanceof ViewGroup) ((ViewGroup) holder.getParent()).removeView(holder);
            try { CcOneUiHooks.detachStack(top); } catch (Throwable t) { Lx.e("QS: One UI 8 stack removal", t); }
        }
        top.requestLayout();
    }

    /** Holder padding follows the panel's columns so the bars line up with the big tiles; bar height = chosen thickness. */
    private static void syncHolder(ViewGroup top) {
        LinearLayout holder = holders.get(top);
        if (holder == null) return;
        G g = geometry(top);
        if (g == null) return;
        int h = dp(top, sizeDp);
        int width = top.getMeasuredWidth() > 0 ? top.getMeasuredWidth() : top.getWidth();
        int right = Math.max(0, width - (g.c0 + g.full));
        if (holder.getPaddingLeft() != g.c0 || holder.getPaddingRight() != right || holder.getPaddingTop() != g.gap) holder.setPadding(g.c0, g.gap, right, g.gap);
        for (int i = 0; i < holder.getChildCount(); i++) {
            View x = holder.getChildAt(i);
            if (!(x.getLayoutParams() instanceof LinearLayout.LayoutParams)) continue;
            LinearLayout.LayoutParams ll = (LinearLayout.LayoutParams) x.getLayoutParams();
            int bottom = i == holder.getChildCount() - 1 ? 0 : g.gap;
            if (ll.width != ViewGroup.LayoutParams.MATCH_PARENT || ll.height != h || ll.bottomMargin != bottom) {
                ll.width = ViewGroup.LayoutParams.MATCH_PARENT; ll.height = h; ll.bottomMargin = bottom;
                x.setLayoutParams(ll);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ brightness mirror

    /** mirror bar -> {stock width, stock height, shadow width, shadow height} */
    private static final Map<View, int[]> mirrorStock = new WeakHashMap<>();
    /** mirror bar -> applied key (0 = stock) */
    private static final Map<View, Integer> mirrorKey = new WeakHashMap<>();

    /**
     * While the brightness bar is dragged the panel fades out and CCBrightnessMirrorController shows a copy
     * (cc_brightness_mirror.xml: a fake-shadow View + another cc_toggle_slider) at the bar's position; the bar forwards
     * its touches to it. The copy must have the same orientation and size.
     */
    private static void syncMirror(final ViewGroup top, View b, final boolean horizontal, final int h, int barWidth) {
        if (b == null) return;
        Object o = null;
        try { o = XposedHelpers.callMethod(b, "getMirror"); } catch (Throwable ignored) { }
        if (!(o instanceof View)) return;
        final View mirror = (View) o;
        G g = horizontal ? geometry(top) : null;
        final int w = barWidth > 0 ? barWidth : g == null ? 0 : g.full;
        int want = horizontal ? w * 1000 + h : 0;
        Integer was = mirrorKey.get(mirror);
        if ((was == null ? 0 : was) == want) return;
        mirrorKey.put(mirror, want);
        top.post(() -> { try { applyMirror(mirror, horizontal && w > 0, w, h); } catch (Throwable t) { Lx.e("QS: brightness mirror layout", t); } });
    }
    private static void applyMirror(View mirror, boolean horizontal, int w, int h) {
        orientBrightness(mirror, horizontal ? h : 0, false);
        ViewGroup.LayoutParams ml = mirror.getLayoutParams();
        if (ml == null) return;
        View shadow = null;
        if (mirror.getParent() instanceof ViewGroup) {
            ViewGroup box = (ViewGroup) mirror.getParent();
            for (int i = 0; i < box.getChildCount(); i++) if (box.getChildAt(i) != mirror) { shadow = box.getChildAt(i); break; }
        }
        ViewGroup.LayoutParams sl = shadow == null ? null : shadow.getLayoutParams();
        int[] st = mirrorStock.get(mirror);
        if (horizontal) {
            if (st == null) { st = new int[]{ml.width, ml.height, sl == null ? 0 : sl.width, sl == null ? 0 : sl.height}; mirrorStock.put(mirror, st); }
            ml.width = w; ml.height = h; mirror.setLayoutParams(ml);
            if (sl != null) { sl.width = w; sl.height = h; shadow.setLayoutParams(sl); }
        } else if (st != null) {
            mirrorStock.remove(mirror);
            ml.width = st[0]; ml.height = st[1]; mirror.setLayoutParams(ml);
            if (sl != null) { sl.width = st[2]; sl.height = st[3]; shadow.setLayoutParams(sl); }
        }
        mirror.requestLayout();
    }

    // ------------------------------------------------------------------------------------------ misc

    private static int exactly(int px) { return View.MeasureSpec.makeMeasureSpec(Math.max(0, px), View.MeasureSpec.EXACTLY); }
    private static int dp(View v, int dp) { return Math.round(dp * v.getResources().getDisplayMetrics().density); }
}
