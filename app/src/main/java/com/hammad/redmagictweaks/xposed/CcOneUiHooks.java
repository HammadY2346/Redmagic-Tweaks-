package com.hammad.redmagictweaks.xposed;

import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hammad.redmagictweaks.QsFeatures;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * "One UI 8" layout of the Control Center page (QsFeatures.QS_CC_LAYOUT = 1), portrait only.
 *
 * Built on the stock views, nothing of the panel is replaced:
 * <ul>
 *   <li>the top panel (ControlCenterTopPanel) keeps only the first two big tiles (Wi-Fi, Bluetooth): QsDimenUtils.getQuickTileNums()
 *       answers 2 inside ControlCenterTileLayout.setTiles, so the other big tiles start the round-tile grid; CcLayoutHooks
 *       lays the two pills out side by side (mode ONEUI);</li>
 *   <li>the round-tile grid (ControlCenterTileLayout, tileType 0) gets a frosted card behind it with a small handle, its
 *       edges lined up with the pills; the navigation-bar inset it normally adds at its bottom moves under our cards;</li>
 *   <li>a stack of our own views is added to TileContentContainer right after the grid: the slider card (the stock
 *       brightness and volume bars, re-parented by CcLayoutHooks, each followed by a round button: Dark mode and the
 *       sound mode), the media card (RedMagic's player when it is switched on, otherwise "Play music" + "Media output")
 *       and four shortcut pills (Device Center, Screen cast, Modes, Share);</li>
 *   <li>the header (CCHeaderView, Control Center type only) shows Edit / Power / Settings instead of the ⋮ menu and a
 *       small clock in the status-icon row instead of the big clock and the date; its height shrinks to fit.</li>
 * </ul>
 * Every step is reversible: the stock positions, colours, sizes and visibilities are recorded before they are changed and
 * put back as soon as the layout is switched off, the phone is rotated or anything fails (then the page stays stock
 * until System UI restarts).
 */
final class CcOneUiHooks {
    private CcOneUiHooks() {}

    static final String TOP = "com.zte.controlcenter.view.ControlCenterTopPanel";
    static final String TILE_LAYOUT = "com.zte.controlcenter.view.ControlCenterTileLayout";
    static final String CONTAINER = "com.zte.controlcenter.view.TileContentContainer";
    static final String PANEL_VIEW = "com.zte.controlcenter.view.ControlCenterPanelView";
    static final String HEADER = "com.zte.controlcenter.widget.CCHeaderView";
    private static final String DIMEN = "com.zte.utils.QsDimenUtils";
    private static final String CONTROL_CENTER = "com.zte.controlcenter.ControlCenter";
    private static final String DEPENDENCY = "com.android.systemui.Dependency";
    private static final String COMMAND_QUEUE = "com.android.systemui.statusbar.CommandQueue";
    private static final String ACTIVITY_STARTER = "com.android.systemui.plugins.ActivityStarter";

    /** RedMagic Share (ZTE share) and Google Quick Share tiles; the Share pill clicks whichever is in the tile list. */
    private static final String[] SHARE_SPECS = {"custom(com.zte.cn.zteshare/.qstile.ZTEShareTileService)", "custom(com.google.android.gms/.nearby.sharing.SharingTileService)"};
    private static final String ZTE_SHARE = "com.zte.cn.zteshare";
    private static final String[] MUSIC_APPS = {"com.nubia.music", "com.google.android.apps.youtube.music", "com.spotify.music", "com.apple.android.music", "com.amazon.mp3", "deezer.android.app"};

    private static ClassLoader loader;
    private static Class<?> dimenClass;
    private static Handler handler;

    // ------------------------------------------------------------------------------------------ settings

    private static volatile boolean layout, bigClock, noHeader, noMedia, noShortcuts, failed;
    private static volatile long at;

    private static void refresh() {
        long now = System.currentTimeMillis();
        if (now - at < 1000) return;
        at = now;
        layout = Cfg.choice(QsFeatures.QS_CC_LAYOUT) == 1;
        bigClock = Cfg.on(QsFeatures.QS_CC_ONEUI_BIG_CLOCK);
        noHeader = Cfg.on(QsFeatures.QS_CC_ONEUI_NO_HEADER);
        noMedia = Cfg.on(QsFeatures.QS_CC_ONEUI_NO_MEDIA);
        noShortcuts = Cfg.on(QsFeatures.QS_CC_ONEUI_NO_SHORTCUTS);
    }

    /** The layout setting (not orientation): true = One UI 8 page wanted. */
    static boolean layoutOn() { refresh(); return layout && !failed; }
    /** Structure-relevant parts, folded into CcLayoutHooks' "what was applied" key. */
    static int partsKey() { refresh(); return (noMedia ? 1 : 0) | (noShortcuts ? 2 : 0); }

    static void fail(String where, Throwable t) {
        if (!failed) Lx.e("QS: One UI 8 Control Center (" + where + ") failed, the page stays stock until System UI restarts", t);
        failed = true;
    }

    private static boolean main() { return Looper.myLooper() == Looper.getMainLooper(); }
    private static boolean portrait(View v) { return v.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE; }
    private static boolean dark(View v) { return (v.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES; }
    private static int dp(View v, float dp) { return Math.round(dp * v.getResources().getDisplayMetrics().density); }
    private static int dp(Context c, float dp) { return Math.round(dp * c.getResources().getDisplayMetrics().density); }
    private static Object field(Object o, String name) { try { return XposedHelpers.getObjectField(o, name); } catch (Throwable t) { return null; } }
    private static View view(Object o, String name) { Object v = field(o, name); return v instanceof View ? (View) v : null; }
    private static void post(Runnable r) { if (handler == null) handler = new Handler(Looper.getMainLooper()); handler.post(r); }
    private static void postDelayed(Runnable r, long ms) { if (handler == null) handler = new Handler(Looper.getMainLooper()); handler.postDelayed(r, ms); }

    // ------------------------------------------------------------------------------------------ install

    private static final ThreadLocal<Boolean> inCcSetTiles = new ThreadLocal<>();

    static void install(ClassLoader cl) {
        loader = cl;
        dimenClass = H.cls(cl, DIMEN);
        int n = 0;
        Class<?> tiles = H.cls(cl, TILE_LAYOUT);
        if (tiles == null) { Lx.w("QS: ControlCenterTileLayout missing, One UI 8 Control Center disabled"); failed = true; return; }

        // Two big tiles instead of four: the quick-tile count read while the Control Center distributes its tiles.
        Method setTiles = null;
        for (Method m : tiles.getDeclaredMethods()) if (m.getName().equals("setTiles") && m.getParameterTypes().length == 1) setTiles = m;
        if (setTiles != null && H.hook(setTiles, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { inCcSetTiles.set(Boolean.TRUE); }
            @Override protected void afterHookedMethod(MethodHookParam p) { inCcSetTiles.remove(); }
        })) n++;
        XC_MethodHook count = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !Boolean.TRUE.equals(inCcSetTiles.get()) || !layoutOn()) return;
                Object r = p.getResult();
                if (r instanceof Integer && (Integer) r > 2) p.setResult(2);
            }
        };
        for (String name : new String[]{DIMEN, DIMEN + "$Companion"}) {
            Class<?> d = H.cls(cl, name);
            if (d != null && H.hook(H.find(d, "getQuickTileNums"), count)) n++;
        }

        // The round-tile grid: its bottom inset moves under our cards; our stack animates as one more row after its rows.
        Method measure = null, animMap = null;
        for (Method m : tiles.getDeclaredMethods()) {
            if (m.getName().equals("onMeasure") && m.getParameterTypes().length == 2) measure = m;
            if (m.getName().equals("getAnimViewsMap") && m.getParameterTypes().length == 0) animMap = m;
        }
        if (measure != null && H.hook(measure, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!main() || !isGrid(p.thisObject)) return;
                try {
                    if (!active((View) p.thisObject)) return;
                    int inset = XposedHelpers.getIntField(p.thisObject, "mBottomInset");
                    if (inset == 0) return;
                    p.setObjectExtra("rmtweaks:inset", inset);
                    XposedHelpers.setIntField(p.thisObject, "mBottomInset", 0);
                } catch (Throwable t) { fail("grid inset", t); }
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Object inset = p.getObjectExtra("rmtweaks:inset");
                if (inset instanceof Integer) { try { XposedHelpers.setIntField(p.thisObject, "mBottomInset", (Integer) inset); } catch (Throwable ignored) { } }
            }
        })) n++;
        if (animMap != null && H.hook(animMap, new XC_MethodHook() {
            @SuppressWarnings("unchecked")
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !isGrid(p.thisObject) || !(p.getResult() instanceof Map)) return;
                try {
                    ViewGroup top = topOf((View) p.thisObject);
                    Stack s = top == null ? null : stacks.get(top);
                    if (s == null || s.root.getParent() == null || !inStack(top)) return;
                    Map<Integer, List<View>> map = new LinkedHashMap<>((Map<Integer, List<View>>) p.getResult());
                    int next = 0;
                    for (Integer k : map.keySet()) if (k != null && k >= next) next = k + 1;
                    List<View> row = new ArrayList<>();
                    row.add(s.root);
                    map.put(next, row);
                    p.setResult(map);
                } catch (Throwable t) { Lx.e("QS: One UI 8 animation rows", t); }
            }
        })) n++;

        Class<?> topClass = H.cls(cl, TOP);
        if (topClass != null) {
            // While the bars / player live in our stack they animate with it, not on their own.
            XC_MethodHook views = new XC_MethodHook() {
                @SuppressWarnings("unchecked")
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable() || !(p.thisObject instanceof ViewGroup)) return;
                    try {
                        ViewGroup top = (ViewGroup) p.thisObject;
                        if (!inStack(top)) return;
                        Object r = p.getResult();
                        if (r instanceof List) {
                            List<View> out = new ArrayList<>((List<View>) r);
                            out.removeIf(v -> v != null && v.getParent() != top);
                            p.setResult(out);
                        } else if (r instanceof Map) {
                            Map<Integer, List<View>> out = new LinkedHashMap<>();
                            for (Map.Entry<Integer, List<View>> e : ((Map<Integer, List<View>>) r).entrySet()) {
                                List<View> l = new ArrayList<>(e.getValue());
                                l.removeIf(v -> v != null && v.getParent() != top);
                                out.put(e.getKey(), l);
                            }
                            p.setResult(out);
                        }
                    } catch (Throwable t) { Lx.e("QS: One UI 8 animation views", t); }
                }
            };
            for (String name : new String[]{"getAnimViews", "getAnimViewsMap"}) {
                Method m = null;
                for (Method x : topClass.getDeclaredMethods()) if (x.getName().equals(name) && x.getParameterTypes().length == 0) m = x;
                if (m != null && H.hook(m, views)) n++;
            }
            Method listen = null;
            for (Method x : topClass.getDeclaredMethods()) if (x.getName().equals("setListening") && x.getParameterTypes().length == 1) listen = x;
            if (listen != null && H.hook(listen, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!main() || !Boolean.TRUE.equals(p.args[0]) || !(p.thisObject instanceof ViewGroup)) return;
                    try { onOpen((ViewGroup) p.thisObject); } catch (Throwable t) { Lx.e("QS: One UI 8 Control Center refresh", t); }
                }
            })) n++;
        }

        // Header: compact height while the small clock is used.
        Class<?> panel = H.cls(cl, PANEL_VIEW);
        if (panel != null && H.hook(H.find(panel, "checkTopPanel"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!main()) return;
                try { View h = view(p.thisObject, "ccHeaderView"); if (h != null) headerHeight(h, false); } catch (Throwable t) { Lx.e("QS: One UI 8 header height", t); }
            }
        })) n++;
        Class<?> header = H.cls(cl, HEADER);
        if (header != null) {
            Method onLayout = null;
            for (Method m : header.getDeclaredMethods()) if (m.getName().equals("onLayout") && m.getParameterTypes().length == 5) onLayout = m;
            if (onLayout == null) { try { onLayout = android.widget.RelativeLayout.class.getDeclaredMethod("onLayout", boolean.class, int.class, int.class, int.class, int.class); } catch (Throwable ignored) { } }
            final Class<?> headerClass = header;
            if (onLayout != null && H.hook(onLayout, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!main() || p.hasThrowable() || !headerClass.isInstance(p.thisObject)) return;
                    try { headerHeight((View) p.thisObject, true); } catch (Throwable t) { Lx.e("QS: One UI 8 header height", t); }
                }
            })) n++;
            Method vis = H.find(header, "updateVisibilities");
            if (vis != null && H.hook(vis, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!main() || !(p.thisObject instanceof View)) return;
                    try { QsHooks.applyCcHeader((View) p.thisObject); } catch (Throwable t) { Lx.e("QS: One UI 8 header", t); }
                }
            })) n++;
        }
        Lx.i("QS: One UI 8 Control Center hooks ready (" + n + ")");
    }

    private static boolean isGrid(Object o) {
        if (!(o instanceof ViewGroup) || !o.getClass().getName().equals(TILE_LAYOUT)) return false;
        try { return XposedHelpers.getIntField(o, "tileType") == 0; } catch (Throwable t) { return false; }
    }

    /** The Control Center top panel next to {@code v} (v = the grid or anything else directly in TileContentContainer). */
    private static ViewGroup topOf(View v) {
        ViewParent p = v.getParent();
        if (!(p instanceof ViewGroup) || !p.getClass().getName().equals(CONTAINER)) return null;
        ViewGroup c = (ViewGroup) p;
        for (int i = 0; i < c.getChildCount(); i++) if (c.getChildAt(i).getClass().getName().equals(TOP)) return (ViewGroup) c.getChildAt(i);
        return null;
    }
    private static boolean active(View grid) {
        ViewGroup top = topOf(grid);
        return top != null && CcLayoutHooks.modeOf(top) == CcLayoutHooks.ONEUI;
    }
    private static ViewGroup gridOf(ViewGroup container) {
        for (int i = 0; i < container.getChildCount(); i++) if (isGrid(container.getChildAt(i))) return (ViewGroup) container.getChildAt(i);
        return null;
    }

    // ------------------------------------------------------------------------------------------ the stack (our views)

    static final class Pill {
        final int kind;
        LinearLayout view; GradientDrawable bg, circle; CcGlyph glyph; TextView title, sub;
        Intent intent; Object tile; boolean available;
        Pill(int kind) { this.kind = kind; }
    }
    static final class Stack {
        final WeakReference<ViewGroup> top;
        LinearLayout root, sliderCard, row1, row2, mediaCard, shortcuts, shortRow1, shortRow2;
        FrameLayout mediaSlot;
        ImageView darkBtn, ringBtn, note;
        CcGlyph darkGlyph, ringGlyph, noteGlyph;
        GradientDrawable darkBg, ringBg, sliderBg, mediaBg, outputBg;
        TextView mediaTitle, output;
        final List<Pill> pills = new ArrayList<>();
        int scheme = -1;
        String mediaPkg;
        /** player -> {stock LayoutParams in the top panel, stock index} while it lives in the media slot */
        Object[] playerHome;
        Stack(ViewGroup t) { top = new WeakReference<>(t); }
    }
    private static final Map<ViewGroup, Stack> stacks = new WeakHashMap<>();

    /** True while the brightness bar sits in our slider card. */
    static boolean inStack(ViewGroup top) {
        Stack s = stacks.get(top);
        if (s == null) return false;
        Object b = field(top, "brightness");
        return b instanceof View && ((View) b).getParent() == s.row1;
    }
    /** Hierarchy matches the wanted state (only checked while the bars are in the stack). */
    static boolean settled(ViewGroup top) {
        Stack s = stacks.get(top);
        if (s == null || !(s.root.getParent() instanceof ViewGroup)) return false;
        ViewGroup container = (ViewGroup) s.root.getParent();
        ViewGroup grid = gridOf(container);
        if (grid != null && container.indexOfChild(s.root) != container.indexOfChild(grid) + 1) return false;
        View player = view(top, "player");
        if (player == null) return true;
        refresh();
        return noMedia ? player.getParent() == top : player.getParent() == s.mediaSlot;
    }
    static boolean hasStack(ViewGroup top) { Stack s = stacks.get(top); return s != null && s.root.getParent() != null; }

    /** Puts the stack after the grid (creating it on first use) and returns the two slider rows for the bars. */
    static ViewGroup[] attachStack(ViewGroup top, ViewGroup container) {
        Stack s = stacks.get(top);
        if (s == null) { s = build(top); stacks.put(top, s); }
        if (s.root.getParent() != container && s.root.getParent() instanceof ViewGroup) ((ViewGroup) s.root.getParent()).removeView(s.root);
        if (s.root.getParent() == container) container.removeView(s.root);
        ViewGroup grid = gridOf(container);
        int at = grid != null ? container.indexOfChild(grid) + 1 : container.getChildCount();
        container.addView(s.root, Math.max(0, Math.min(at, container.getChildCount())), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // RedMagic's own media player: into the media slot (it then replaces our compact card), or back home
        refresh();
        View player = view(top, "player");
        if (player != null) {
            if (!noMedia && player.getParent() != s.mediaSlot) {
                if (player.getParent() == top) s.playerHome = new Object[]{player.getLayoutParams(), top.indexOfChild(player)};
                if (player.getParent() instanceof ViewGroup) ((ViewGroup) player.getParent()).removeView(player);
                int ph = ViewGroup.LayoutParams.WRAP_CONTENT;
                try { ph = XposedHelpers.getIntField(top, "firstRowHeight"); } catch (Throwable ignored) { }
                s.mediaSlot.addView(player, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ph));
            } else if (noMedia && player.getParent() != top) {
                playerHome(top, s, player);
            }
        }
        neutral(top);
        updateAnimator(top);
        return new ViewGroup[]{s.row1, s.row2};
    }

    /** Removes our stack (bars already moved home by CcLayoutHooks), puts the player and the grid look back. */
    static void detachStack(ViewGroup top) {
        Stack s = stacks.get(top);
        if (s == null) return;
        View player = view(top, "player");
        if (player != null && player.getParent() != top) playerHome(top, s, player);
        ViewGroup container = s.root.getParent() instanceof ViewGroup ? (ViewGroup) s.root.getParent() : null;
        if (container != null) {
            container.removeView(s.root);
            ViewGroup grid = gridOf(container);
            if (grid != null) restoreGrid(grid);
        }
        updateAnimator(top);
    }

    private static void playerHome(ViewGroup top, Stack s, View player) {
        if (player.getParent() instanceof ViewGroup) ((ViewGroup) player.getParent()).removeView(player);
        Object[] home = s.playerHome;
        s.playerHome = null;
        ViewGroup.LayoutParams lp = home != null && home[0] instanceof ViewGroup.LayoutParams ? (ViewGroup.LayoutParams) home[0]
                : new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int index = home != null && home[1] instanceof Integer ? (Integer) home[1] : 0;
        top.addView(player, Math.max(0, Math.min(index, top.getChildCount())), lp);
    }

    /** Bars / player that the panel animator left half-way (they were animated before they moved) are reset. */
    private static void neutral(ViewGroup top) {
        for (String f : new String[]{"brightness", "volume", "player"}) {
            View v = view(top, f);
            if (v == null || v.getParent() == top) continue;
            v.setAlpha(1f); v.setScaleX(1f); v.setScaleY(1f); v.setTranslationX(0f); v.setTranslationY(0f);
        }
    }

    /** Let the panel animator rebuild its rows (our stack joins them, the moved bars leave them). */
    private static void updateAnimator(ViewGroup top) {
        try {
            for (ViewParent p = top.getParent(); p != null; p = p.getParent()) {
                if (!p.getClass().getName().equals(PANEL_VIEW)) continue;
                Object controller = field(p, "panelViewController");
                Object animator = controller == null ? null : XposedHelpers.callMethod(controller, "getPanelAnimator");
                if (animator != null) XposedHelpers.callMethod(animator, "updateViews");
                return;
            }
        } catch (Throwable t) { Lx.w("QS: One UI 8 animator refresh skipped (" + t + ")"); }
    }

    /**
     * Inside the top panel's measure: keep our views in shape (no hierarchy changes here). {@code left} / {@code right} are
     * the pill row's outer edges as paddings of the container, {@code gap} the panel's row gap, {@code h} the bar height.
     */
    static void sync(ViewGroup top, int left, int right, int gap, int h) {
        Stack s = stacks.get(top);
        if (s == null || !(s.root.getParent() instanceof ViewGroup)) return;
        ViewGroup container = (ViewGroup) s.root.getParent();
        refresh();
        boolean dark = dark(top);
        int scheme = dark ? 1 : 0;
        if (scheme != s.scheme) { s.scheme = scheme; colour(s, dark); }
        int inset = 0;
        ViewGroup grid = gridOf(container);
        if (grid != null) {
            try { inset = XposedHelpers.getIntField(grid, "mBottomInset"); } catch (Throwable ignored) { }
            styleGrid(grid, left, right, dark);
        }
        int space = dp(top, 12);
        if (s.root.getPaddingLeft() != left || s.root.getPaddingRight() != right || s.root.getPaddingBottom() != inset + dp(top, 16) || s.root.getPaddingTop() != space)
            s.root.setPadding(left, space, right, inset + dp(top, 16));
        for (LinearLayout row : new LinearLayout[]{s.row1, s.row2}) {
            for (int i = 0; i < row.getChildCount(); i++) {
                View x = row.getChildAt(i);
                if (x == s.darkBtn || x == s.ringBtn) continue;
                if (!(x.getLayoutParams() instanceof LinearLayout.LayoutParams)) continue;
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) x.getLayoutParams();
                if (lp.width != 0 || lp.height != h || lp.weight != 1f) { lp.width = 0; lp.height = h; lp.weight = 1f; x.setLayoutParams(lp); }
            }
        }
        View player = view(top, "player");
        boolean playerShown = player != null && player.getParent() == s.mediaSlot && player.getVisibility() == View.VISIBLE;
        vis(s.mediaSlot, noMedia ? View.GONE : View.VISIBLE);
        vis(s.mediaCard, playerShown ? View.GONE : View.VISIBLE);
        boolean anyPill = false;
        for (Pill p : s.pills) anyPill |= p.available;
        vis(s.shortcuts, noShortcuts || !anyPill ? View.GONE : View.VISIBLE);
    }

    private static void vis(View v, int want) { if (v != null && v.getVisibility() != want) v.setVisibility(want); }

    // ---- grid card

    /** grid -> {stock background, stock left margin, stock right margin} */
    private static final Map<View, Object[]> gridStock = new WeakHashMap<>();

    private static void styleGrid(ViewGroup grid, int left, int right, boolean dark) {
        if (!(grid.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) grid.getLayoutParams();
        Object[] st = gridStock.get(grid);
        if (st == null) { st = new Object[]{grid.getBackground(), lp.leftMargin, lp.rightMargin}; gridStock.put(grid, st); }
        if (lp.leftMargin != left || lp.rightMargin != right) { lp.leftMargin = left; lp.rightMargin = right; lp.setMarginStart(left); lp.setMarginEnd(right); }
        Drawable bg = grid.getBackground();
        if (!(bg instanceof Card) || ((Card) bg).dark != dark) grid.setBackground(new Card(QsArt.oneUi8Card(dark), dp(grid, 26), dark, grid.getResources().getDisplayMetrics().density));
    }
    private static void restoreGrid(ViewGroup grid) {
        Object[] st = gridStock.remove(grid);
        if (st == null) return;
        grid.setBackground((Drawable) st[0]);
        if (grid.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) grid.getLayoutParams();
            lp.leftMargin = (Integer) st[1]; lp.rightMargin = (Integer) st[2]; lp.setMarginStart(lp.leftMargin); lp.setMarginEnd(lp.rightMargin);
            grid.setLayoutParams(lp);
        }
    }

    /** Rounded frosted card with the small One UI handle centred near its bottom edge. */
    static final class Card extends Drawable {
        final int color; final float radius; final boolean dark; final float density;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Card(int color, float radius, boolean dark, float density) { this.color = color; this.radius = radius; this.dark = dark; this.density = density; }
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            paint.setColor(color);
            c.drawRoundRect(new RectF(b), radius, radius, paint);
            float w = 26 * density, hh = 4 * density, cx = b.exactCenterX(), y = b.bottom - 10 * density;
            paint.setColor(dark ? 0x59FFFFFF : 0x40000000);
            c.drawRoundRect(new RectF(cx - w / 2, y - hh, cx + w / 2, y), hh / 2, hh / 2, paint);
        }
        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ---- building

    private static GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }
    private static GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }
    private static ImageView glyphButton(Context ctx, CcGlyph glyph, GradientDrawable bg, int pad) {
        ImageView v = new ImageView(ctx);
        v.setBackground(bg);
        v.setImageDrawable(glyph);
        v.setPadding(pad, pad, pad, pad);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    private static Stack build(ViewGroup top) {
        Context ctx = top.getContext();
        Stack s = new Stack(top);
        s.root = new LinearLayout(ctx);
        s.root.setOrientation(LinearLayout.VERTICAL);
        s.root.setTag(CcLayoutHooks.HOLDER_TAG);
        s.root.setClipChildren(false);
        s.root.setClipToPadding(false);

        // slider card: [brightness bar][Dark mode] / [volume bar][sound mode]
        s.sliderCard = new LinearLayout(ctx);
        s.sliderCard.setOrientation(LinearLayout.VERTICAL);
        s.sliderBg = round(0, dp(ctx, 26));
        s.sliderCard.setBackground(s.sliderBg);
        int pad = dp(ctx, 14);
        s.sliderCard.setPadding(pad, pad, pad, pad);
        s.row1 = row(ctx); s.row2 = row(ctx);
        s.darkGlyph = new CcGlyph(CcGlyph.MOON, 0xFFFFFFFF);
        s.darkBg = oval(0);
        s.darkBtn = glyphButton(ctx, s.darkGlyph, s.darkBg, dp(ctx, 13));
        s.darkBtn.setContentDescription("Dark mode");
        s.ringGlyph = new CcGlyph(CcGlyph.SPEAKER, 0xFF1C1C1E);
        s.ringBg = oval(0);
        s.ringBtn = glyphButton(ctx, s.ringGlyph, s.ringBg, dp(ctx, 13));
        s.ringBtn.setContentDescription("Sound mode");
        s.row1.addView(s.darkBtn, buttonParams(ctx));
        s.row2.addView(s.ringBtn, buttonParams(ctx));
        s.sliderCard.addView(s.row1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams r2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2.topMargin = dp(ctx, 10);
        s.sliderCard.addView(s.row2, r2);
        s.root.addView(s.sliderCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // media card: RedMagic's player when shown, else ♪ Play music … [Media output]
        s.mediaSlot = new FrameLayout(ctx);
        LinearLayout.LayoutParams ms = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ms.topMargin = dp(ctx, 12);
        s.root.addView(s.mediaSlot, ms);
        s.mediaCard = new LinearLayout(ctx);
        s.mediaCard.setOrientation(LinearLayout.HORIZONTAL);
        s.mediaCard.setGravity(Gravity.CENTER_VERTICAL);
        s.mediaBg = round(0, dp(ctx, 28));
        s.mediaCard.setBackground(s.mediaBg);
        s.mediaCard.setPadding(dp(ctx, 18), 0, dp(ctx, 8), 0);
        s.mediaCard.setClickable(true);
        s.noteGlyph = new CcGlyph(CcGlyph.NOTE, 0xFFFFFFFF);
        s.note = new ImageView(ctx);
        s.note.setImageDrawable(s.noteGlyph);
        s.mediaCard.addView(s.note, new LinearLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18)));
        s.mediaTitle = new TextView(ctx);
        s.mediaTitle.setText("Play music");
        s.mediaTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        s.mediaTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        s.mediaTitle.setSingleLine(true);
        s.mediaTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.setMarginStart(dp(ctx, 10)); tl.setMarginEnd(dp(ctx, 8));
        s.mediaCard.addView(s.mediaTitle, tl);
        s.output = new TextView(ctx);
        s.output.setText("Media output");
        s.output.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        s.output.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        s.output.setGravity(Gravity.CENTER);
        s.output.setSingleLine(true);
        s.outputBg = round(0, dp(ctx, 20));
        s.output.setBackground(s.outputBg);
        s.output.setPadding(dp(ctx, 16), 0, dp(ctx, 16), 0);
        s.output.setClickable(true);
        s.mediaCard.addView(s.output, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(ctx, 40)));
        s.mediaSlot.addView(s.mediaCard, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 56)));

        // shortcut pills
        s.shortcuts = new LinearLayout(ctx);
        s.shortcuts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sc = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sc.topMargin = dp(ctx, 12);
        s.root.addView(s.shortcuts, sc);
        s.shortRow1 = pillRow(ctx); s.shortRow2 = pillRow(ctx);
        s.shortcuts.addView(s.shortRow1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sr2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sr2.topMargin = dp(ctx, 12);
        s.shortcuts.addView(s.shortRow2, sr2);
        String[][] text = {{"Device Center", "Connected devices"}, {"Screen cast", "Mirror screen"}, {"Modes", "Do Not Disturb"}, {"Share", "Share nearby"}};
        int[] glyphs = {CcGlyph.DEVICES, CcGlyph.CAST, CcGlyph.MODES, CcGlyph.SHARE};
        for (int k = 0; k < 4; k++) {
            Pill p = pill(ctx, k, glyphs[k], text[k][0], text[k][1]);
            s.pills.add(p);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(ctx, 64), 1f);
            if (k % 2 == 0) lp.setMarginEnd(dp(ctx, 6)); else lp.setMarginStart(dp(ctx, 6));
            (k < 2 ? s.shortRow1 : s.shortRow2).addView(p.view, lp);
        }

        listeners(s);
        colour(s, dark(top));
        s.scheme = dark(top) ? 1 : 0;
        return s;
    }

    private static LinearLayout row(Context ctx) {
        LinearLayout r = new LinearLayout(ctx);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        return r;
    }
    private static LinearLayout pillRow(Context ctx) {
        LinearLayout r = new LinearLayout(ctx);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }
    private static LinearLayout.LayoutParams buttonParams(Context ctx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(ctx, 48), dp(ctx, 48));
        lp.setMarginStart(dp(ctx, 10));
        return lp;
    }
    private static Pill pill(Context ctx, int kind, int glyph, String title, String sub) {
        Pill p = new Pill(kind);
        p.view = new LinearLayout(ctx);
        p.view.setOrientation(LinearLayout.HORIZONTAL);
        p.view.setGravity(Gravity.CENTER_VERTICAL);
        p.bg = round(0, dp(ctx, 32));
        p.view.setBackground(p.bg);
        p.view.setPadding(dp(ctx, 12), 0, dp(ctx, 12), 0);
        p.view.setClickable(true);
        p.circle = oval(0);
        p.glyph = new CcGlyph(glyph, 0xFFFFFFFF);
        ImageView icon = glyphButton(ctx, p.glyph, p.circle, dp(ctx, 9));
        icon.setClickable(false);
        icon.setFocusable(false);
        p.view.addView(icon, new LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 40)));
        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.setMarginStart(dp(ctx, 10));
        p.view.addView(texts, tl);
        p.title = new TextView(ctx);
        p.title.setText(title);
        p.title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        p.title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.title.setSingleLine(true);
        p.title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(p.title);
        p.sub = new TextView(ctx);
        p.sub.setText(sub);
        p.sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        p.sub.setSingleLine(true);
        p.sub.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(p.sub);
        p.view.setContentDescription(title);
        return p;
    }

    /** Colours for dark (frosted dark) or light panels. */
    private static void colour(Stack s, boolean dark) {
        int card = QsArt.oneUi8Card(dark), soft = QsArt.oneUi8Soft(dark), text = QsArt.oneUi8Text(dark), sub = (text & 0x00FFFFFF) | 0xB3000000;
        s.sliderBg.setColor(card);
        s.mediaBg.setColor(card);
        s.outputBg.setColor(soft);
        s.mediaTitle.setTextColor(text);
        s.output.setTextColor(text);
        s.noteGlyph.setColor(text);
        for (Pill p : s.pills) {
            p.bg.setColor(card);
            p.circle.setColor(soft);
            p.glyph.setColor(text);
            p.title.setTextColor(text);
            p.sub.setTextColor(sub);
        }
        ViewGroup top = s.top.get();
        if (top != null) buttons(s, top.getContext());
    }

    // ------------------------------------------------------------------------------------------ actions

    private static void listeners(final Stack s) {
        s.darkBtn.setOnClickListener(v -> {
            try {
                boolean night = (v.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                UiModeManager um = (UiModeManager) v.getContext().getSystemService(Context.UI_MODE_SERVICE);
                try { XposedHelpers.callMethod(um, "setNightModeActivated", !night); }
                catch (Throwable t) { um.setNightMode(night ? UiModeManager.MODE_NIGHT_NO : UiModeManager.MODE_NIGHT_YES); }
                s.darkBg.setColor(!night ? onColor(v) : QsArt.oneUi8Soft(dark(v)));
            } catch (Throwable t) { Lx.e("QS: One UI 8 dark mode button", t); }
        });
        s.ringBtn.setOnClickListener(v -> {
            try {
                AudioManager am = (AudioManager) v.getContext().getSystemService(Context.AUDIO_SERVICE);
                int mode = ringerMode(am);
                boolean vib = false;
                try { Vibrator vb = (Vibrator) v.getContext().getSystemService(Context.VIBRATOR_SERVICE); vib = vb != null && vb.hasVibrator(); } catch (Throwable ignored) { }
                int next = mode == AudioManager.RINGER_MODE_NORMAL ? (vib ? AudioManager.RINGER_MODE_VIBRATE : AudioManager.RINGER_MODE_SILENT)
                        : mode == AudioManager.RINGER_MODE_VIBRATE ? AudioManager.RINGER_MODE_SILENT : AudioManager.RINGER_MODE_NORMAL;
                try { XposedHelpers.callMethod(am, "setRingerModeInternal", next); } catch (Throwable t) { am.setRingerMode(next); }
                buttons(s, v.getContext());
            } catch (Throwable t) { Lx.e("QS: One UI 8 sound mode button", t); }
        });
        s.mediaCard.setOnClickListener(v -> {
            try {
                PackageManager pm = v.getContext().getPackageManager();
                Intent i = s.mediaPkg == null ? null : pm.getLaunchIntentForPackage(s.mediaPkg);
                if (i == null) {
                    Intent sel = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC);
                    if (resolves(pm, sel)) i = sel;
                }
                for (int k = 0; i == null && k < MUSIC_APPS.length; k++) i = pm.getLaunchIntentForPackage(MUSIC_APPS[k]);
                if (i != null) start(v, i); else Lx.w("QS: One UI 8 media card: no music app found");
            } catch (Throwable t) { Lx.e("QS: One UI 8 media card", t); }
        });
        s.output.setOnClickListener(v -> {
            try {
                final Context ctx = v.getContext().getApplicationContext() != null ? v.getContext().getApplicationContext() : v.getContext();
                final String pkg = s.mediaPkg;
                collapse();
                postDelayed(() -> {
                    try {
                        Intent b = new Intent(pkg != null ? "com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG" : "com.android.systemui.action.LAUNCH_SYSTEM_MEDIA_OUTPUT_DIALOG");
                        b.setPackage("com.android.systemui");
                        if (pkg != null) b.putExtra("package_name", pkg);
                        ctx.sendBroadcast(b);
                    } catch (Throwable t) {
                        try { start(null, new Intent("android.settings.panel.action.MEDIA_OUTPUT")); } catch (Throwable ignored) { }
                    }
                }, 350);
            } catch (Throwable t) { Lx.e("QS: One UI 8 media output", t); }
        });
        for (final Pill p : s.pills) {
            p.view.setOnClickListener(v -> {
                try {
                    if (p.tile != null && clickTile(p.tile)) return;
                    if (p.intent != null) start(v, new Intent(p.intent));
                } catch (Throwable t) { Lx.e("QS: One UI 8 shortcut", t); }
            });
        }
    }

    private static int onColor(View v) { return dark(v) ? 0xFFF4F4F6 : 0xFF26262A; }
    private static int onGlyph(View v) { return dark(v) ? 0xFF1C1C1E : 0xFFFFFFFF; }

    private static int ringerMode(AudioManager am) {
        try { Object r = XposedHelpers.callMethod(am, "getRingerModeInternal"); if (r instanceof Integer) return (Integer) r; } catch (Throwable ignored) { }
        return am.getRingerMode();
    }

    /** Dark mode button: white circle while the dark theme is on; sound button: always white, glyph = ringer mode. */
    private static void buttons(Stack s, Context ctx) {
        ViewGroup top = s.top.get();
        if (top == null) return;
        boolean dark = dark(top);
        s.darkBg.setColor(dark ? onColor(top) : QsArt.oneUi8Soft(false));
        s.darkGlyph.setColor(dark ? onGlyph(top) : QsArt.oneUi8Text(false));
        s.ringBg.setColor(onColor(top));
        int glyph = CcGlyph.SPEAKER;
        try {
            int mode = ringerMode((AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE));
            glyph = mode == AudioManager.RINGER_MODE_VIBRATE ? CcGlyph.VIBRATE : mode == AudioManager.RINGER_MODE_SILENT ? CcGlyph.MUTE : CcGlyph.SPEAKER;
        } catch (Throwable ignored) { }
        if (glyph != glyphOf(s.ringGlyph)) {
            s.ringGlyph = new CcGlyph(glyph, onGlyph(top));
            s.ringBtn.setImageDrawable(s.ringGlyph);
        } else s.ringGlyph.setColor(onGlyph(top));
    }
    private static int glyphOf(CcGlyph g) { return g == null ? -1 : g.type(); }

    private static boolean resolves(PackageManager pm, Intent i) {
        try { return pm.resolveActivity(i, 0) != null; } catch (Throwable t) { return false; }
    }

    private static Object dependency(String name) throws Throwable {
        Class<?> dep = XposedHelpers.findClass(DEPENDENCY, loader), c = XposedHelpers.findClass(name, loader);
        Method get = dep.getDeclaredMethod("get", Class.class);
        get.setAccessible(true);
        return get.invoke(null, c);
    }

    /** Collapses the panel and starts {@code intent} the way the header buttons do (keyguard dismissed first). */
    static void start(View from, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { Object cc = dependency(CONTROL_CENTER); XposedHelpers.callMethod(cc, "postStartActivityDismissingKeyguard", intent); return; }
        catch (Throwable t) { Lx.w("QS: ControlCenter starter unavailable (" + t + ")"); }
        try { Object as = dependency(ACTIVITY_STARTER); XposedHelpers.callMethod(as, "postStartActivityDismissingKeyguard", intent, 0); return; }
        catch (Throwable t) { Lx.w("QS: ActivityStarter unavailable (" + t + ")"); }
        if (from != null) from.getContext().startActivity(intent);
    }
    private static void collapse() {
        try { XposedHelpers.callMethod(dependency(CONTROL_CENTER), "collapse", true); } catch (Throwable t) { Lx.w("QS: Control Center collapse failed (" + t + ")"); }
    }
    private static void powerMenu() {
        try { XposedHelpers.callMethod(dependency(COMMAND_QUEUE), "showGlobalActionsMenu"); } catch (Throwable t) { Lx.e("QS: power menu", t); }
    }

    private static boolean clickTile(Object tile) throws Throwable {
        for (Class<?> c = tile.getClass(); c != null && c != Object.class; c = c.getSuperclass())
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("click") || m.getParameterTypes().length != 1 || m.getParameterTypes()[0].isPrimitive()) continue;
                m.setAccessible(true);
                m.invoke(tile, (Object) null);
                return true;
            }
        return false;
    }
    private static Object findTile(ViewGroup top, String[] specs) {
        try {
            Object host = field(top, "mHost");
            Object tiles = host == null ? null : XposedHelpers.callMethod(host, "getTiles");
            if (!(tiles instanceof Collection)) return null;
            for (String want : specs)
                for (Object t : new ArrayList<>((Collection<?>) tiles)) {
                    Object spec = XposedHelpers.callMethod(t, "getTileSpec");
                    if (want.equals(spec)) return t;
                }
        } catch (Throwable ignored) { }
        return null;
    }

    // ------------------------------------------------------------------------------------------ panel opened

    private static final Map<ViewGroup, Long> reloaded = new WeakHashMap<>();

    /** Panel opened: tile split, button states, media title and shortcut targets are brought up to date. */
    private static void onOpen(final ViewGroup top) {
        checkTileSplit(top);
        Stack s = stacks.get(top);
        if (s == null || !inStack(top)) return;
        neutral(top);
        Context ctx = top.getContext();
        buttons(s, ctx);
        media(s, ctx);
        pills(s, top);
    }

    /** Two big tiles for One UI 8, the stock count otherwise: re-distribute the host's tiles when the panel holds the wrong number. */
    private static void checkTileSplit(final ViewGroup top) {
        try {
            Object records = field(top, "mRecords");
            Object host = field(top, "mHost");
            if (!(records instanceof List) || host == null) return;
            Object all = XposedHelpers.callMethod(host, "getTiles");
            if (!(all instanceof Collection)) return;
            int total = ((Collection<?>) all).size(), have = ((List<?>) records).size();
            int stock = 4;
            try { if (dimenClass != null) stock = (Integer) XposedHelpers.callStaticMethod(dimenClass, "getQuickTileNums"); } catch (Throwable ignored) { }
            int want = Math.min(total, layoutOn() ? Math.min(2, stock) : stock);
            if (have == want) return;
            Long last = reloaded.get(top);
            long now = System.currentTimeMillis();
            if (last != null && now - last < 5000) return;   // one attempt per opening; never loop
            reloaded.put(top, now);
            ViewParent container = top.getParent();
            final ViewGroup grid = container instanceof ViewGroup ? gridOf((ViewGroup) container) : null;
            post(() -> {
                try {
                    XposedHelpers.callMethod(top, "onTilesChanged");
                    if (grid != null) XposedHelpers.callMethod(grid, "onTilesChanged");
                    Lx.i("QS: Control Center big tiles " + have + " -> " + want);
                    top.requestLayout();
                } catch (Throwable t) { fail("tile split", t); }
            });
        } catch (Throwable t) { Lx.e("QS: One UI 8 tile split", t); }
    }

    private static void media(Stack s, Context ctx) {
        String title = null, pkg = null;
        try {
            MediaSessionManager msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> list = msm == null ? null : msm.getActiveSessions(null);
            MediaController best = null;
            if (list != null) for (MediaController mc : list) {
                PlaybackState ps = mc.getPlaybackState();
                if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) { best = mc; break; }
                if (best == null) best = mc;
            }
            if (best != null) {
                pkg = best.getPackageName();
                MediaMetadata md = best.getMetadata();
                CharSequence t = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_TITLE);
                if (!TextUtils.isEmpty(t)) title = t.toString();
            }
        } catch (Throwable t) { Lx.w("QS: One UI 8 media sessions unavailable (" + t + ")"); }
        s.mediaPkg = pkg;
        String text = title != null ? title : "Play music";
        if (!text.contentEquals(s.mediaTitle.getText())) s.mediaTitle.setText(text);
    }

    private static void pills(Stack s, ViewGroup top) {
        PackageManager pm = top.getContext().getPackageManager();
        for (Pill p : s.pills) {
            p.intent = null; p.tile = null;
            switch (p.kind) {
                case 0: {
                    Intent i = new Intent("com.zte.nebulagravitation.SHOW_ALL_DEVICES");
                    if (resolves(pm, i)) p.intent = i;
                    break;
                }
                case 1: {
                    Intent[] candidates = {
                            new Intent().setComponent(new ComponentName("cn.nubia.touping", "cn.nubia.touping.SearchDeviceListActivity")),
                            new Intent().setComponent(new ComponentName("cn.nubia.touping", "cn.nubia.touping.HomeActivity")),
                            new Intent().setComponent(new ComponentName("com.zte.smartcast", "com.zte.smartcast.activity.CastMainActivity")),
                            new Intent("android.settings.CAST_SETTINGS")};
                    for (Intent i : candidates) if (resolves(pm, i)) { p.intent = i; break; }
                    break;
                }
                case 2: {
                    Intent i = new Intent("android.settings.ZEN_MODE_SETTINGS");
                    p.intent = resolves(pm, i) ? i : new Intent("android.settings.SOUND_SETTINGS");
                    break;
                }
                default: {
                    p.tile = findTile(top, SHARE_SPECS);
                    if (p.tile != null) {
                        try { Object label = XposedHelpers.callMethod(p.tile, "getTileLabel"); if (label instanceof CharSequence && ((CharSequence) label).length() > 0) p.title.setText((CharSequence) label); } catch (Throwable ignored) { }
                    } else {
                        Intent i = pm.getLaunchIntentForPackage(ZTE_SHARE);
                        if (i != null) { p.intent = i; p.title.setText("RedMagic Share"); }
                    }
                    break;
                }
            }
            p.available = p.intent != null || p.tile != null;
        }
        // a missing pill keeps its half of the row (INVISIBLE) unless the whole row is empty
        for (int r = 0; r < 2; r++) {
            Pill a = s.pills.get(r * 2), b = s.pills.get(r * 2 + 1);
            LinearLayout row = r == 0 ? s.shortRow1 : s.shortRow2;
            vis(row, a.available || b.available ? View.VISIBLE : View.GONE);
            vis(a.view, a.available ? View.VISIBLE : View.INVISIBLE);
            vis(b.view, b.available ? View.VISIBLE : View.INVISIBLE);
        }
        boolean any = false;
        for (Pill p : s.pills) any |= p.available;
        refresh();
        vis(s.shortcuts, noShortcuts || !any ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------------------------------ header

    private static final class HeaderState {
        int clockPx = -1, clockH = Integer.MIN_VALUE, timeTop = Integer.MIN_VALUE;
        int dateVis = -1, menuVis = -1, editVis = -1;
        boolean clockChanged;
        ImageView power;
        int stockHeight = Integer.MIN_VALUE, compact = -1;
    }
    private static final Map<View, HeaderState> headers = new WeakHashMap<>();

    private static boolean ccHeader(View h) {
        try { return XposedHelpers.getIntField(h, "panelType") == XposedHelpers.getStaticIntField(h.getClass(), "QS_CENTER_TYPE"); }
        catch (Throwable t) { return false; }
    }
    private static boolean headerOn(View h) { return layoutOn() && !noHeader && portrait(h) && ccHeader(h); }

    /** Called after QsHooks applied the qs_cc_hide_* switches (so a hidden button stays hidden). */
    static void header(View h) {
        if (!h.getClass().getName().equals(HEADER)) return;
        HeaderState st = headers.get(h);
        if (!headerOn(h)) { if (st != null) { headers.remove(h); restoreHeader(h, st); } return; }
        if (st == null) { st = new HeaderState(); headers.put(h, st); }
        int mask = QsHooks.s().ccHide;
        final View header = h;
        View menu = view(h, "mEditMenuContainer"), edit = view(h, "mEditButton"), settings = view(h, "mSettingsContainer"), date = view(h, "mDateViewContainer");
        if (menu != null) { if (st.menuVis < 0) st.menuVis = menu.getVisibility(); vis(menu, View.GONE); }
        if (edit != null) {
            if (st.editVis < 0) st.editVis = edit.getVisibility();
            boolean show = (mask & (1 << 1)) == 0;
            vis(edit, show ? View.VISIBLE : View.GONE);
            if (show) edit.setOnClickListener(v -> { try { XposedHelpers.callMethod(header, "handleClickEdit", v); } catch (Throwable t) { Lx.e("QS: One UI 8 edit button", t); } });
        }
        power(h, st, settings, (mask & (1 << 3)) == 0);
        refresh();
        if (!bigClock) {
            if (date != null) { if (st.dateVis < 0) st.dateVis = date.getVisibility(); vis(date, View.GONE); }
            smallClock(h, st);
        } else {
            if (date != null && st.dateVis >= 0 && (mask & (1 << 5)) == 0) { vis(date, st.dateVis); st.dateVis = -1; }
            restoreClock(h, st);
        }
    }

    /** Our own power button just before the Settings gear (the firmware's global-action container is operator-only). */
    private static void power(View h, HeaderState st, View settings, boolean show) {
        if (settings == null || !(settings.getParent() instanceof ViewGroup)) return;
        ViewGroup row = (ViewGroup) settings.getParent();
        if (st.power == null) {
            ImageView p = new ImageView(h.getContext());
            View gear = view(h, "mSettingsButton");
            int size = gear != null && gear.getLayoutParams() != null && gear.getLayoutParams().width > 0 ? gear.getLayoutParams().width : dp(h, 32);
            p.setImageDrawable(new CcGlyph(CcGlyph.POWER, headerColor(h)));
            int pad = Math.round(size * 0.14f);
            p.setPadding(pad, pad, pad, pad);
            p.setScaleType(ImageView.ScaleType.FIT_CENTER);
            p.setContentDescription("Power");
            p.setClickable(true);
            p.setOnClickListener(v -> powerMenu());
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(size, size);
            if (settings.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) lp.setMarginStart(((ViewGroup.MarginLayoutParams) settings.getLayoutParams()).getMarginStart());
            ViewGroup.LayoutParams use = row instanceof LinearLayout ? new LinearLayout.LayoutParams(lp) : lp;
            if (use instanceof LinearLayout.LayoutParams) ((LinearLayout.LayoutParams) use).gravity = Gravity.CENTER_VERTICAL;
            row.addView(p, Math.max(0, row.indexOfChild(settings)), use);
            st.power = p;
        } else if (st.power.getParent() != row) {
            if (st.power.getParent() instanceof ViewGroup) ((ViewGroup) st.power.getParent()).removeView(st.power);
            row.addView(st.power, Math.max(0, row.indexOfChild(settings)));
        }
        Drawable d = st.power.getDrawable();
        if (d instanceof CcGlyph) ((CcGlyph) d).setColor(headerColor(h));
        vis(st.power, show ? View.VISIBLE : View.GONE);
    }
    private static int headerColor(View h) {
        View clock = view(h, "mClockView");
        if (clock instanceof TextView) return ((TextView) clock).getCurrentTextColor();
        return dark(h) ? 0xFFFFFFFF : 0xFF1C1C1E;
    }

    /** RedMagic's big clock → status-bar sized clock in the status-icon row. */
    private static void smallClock(View h, HeaderState st) {
        View c = view(h, "mClockView"), time = view(h, "mTimeGroup");
        if (!(c instanceof TextView)) return;
        TextView clock = (TextView) c;
        if (!st.clockChanged) {
            st.clockPx = Math.round(clock.getTextSize());
            st.clockH = clock.getLayoutParams() != null ? clock.getLayoutParams().height : Integer.MIN_VALUE;
            st.timeTop = time != null && time.getLayoutParams() instanceof ViewGroup.MarginLayoutParams ? ((ViewGroup.MarginLayoutParams) time.getLayoutParams()).topMargin : Integer.MIN_VALUE;
            st.clockChanged = true;
        }
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15, h.getResources().getDisplayMetrics());
        if (Math.abs(clock.getTextSize() - px) > 0.5f) clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        View icons = idView(h, "status_icons_group");
        int rowH = icons != null && icons.getLayoutParams() != null && icons.getLayoutParams().height > 0 ? icons.getLayoutParams().height : dp(h, 24);
        int rowTop = icons != null && icons.getLayoutParams() instanceof ViewGroup.MarginLayoutParams ? ((ViewGroup.MarginLayoutParams) icons.getLayoutParams()).topMargin : -1;
        ViewGroup.LayoutParams cl = clock.getLayoutParams();
        if (cl != null && cl.height != rowH) { cl.height = rowH; clock.setLayoutParams(cl); }
        if (rowTop >= 0 && time != null && time.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams tl = (ViewGroup.MarginLayoutParams) time.getLayoutParams();
            if (tl.topMargin != rowTop || tl.bottomMargin != 0) { tl.topMargin = rowTop; tl.bottomMargin = 0; time.setLayoutParams(tl); }
        }
    }
    private static void restoreClock(View h, HeaderState st) {
        if (!st.clockChanged) return;
        st.clockChanged = false;
        View c = view(h, "mClockView"), time = view(h, "mTimeGroup");
        if (c instanceof TextView && st.clockPx > 0) ((TextView) c).setTextSize(TypedValue.COMPLEX_UNIT_PX, st.clockPx);
        if (c != null && c.getLayoutParams() != null && st.clockH != Integer.MIN_VALUE) { c.getLayoutParams().height = st.clockH; c.setLayoutParams(c.getLayoutParams()); }
        if (time != null && time.getLayoutParams() instanceof ViewGroup.MarginLayoutParams && st.timeTop != Integer.MIN_VALUE) {
            ViewGroup.MarginLayoutParams tl = (ViewGroup.MarginLayoutParams) time.getLayoutParams();
            tl.topMargin = st.timeTop;
            try { tl.bottomMargin = h.getResources().getDimensionPixelSize(h.getResources().getIdentifier("control_center_header_time_group_bottom_margin", "dimen", "com.android.systemui")); } catch (Throwable ignored) { }
            time.setLayoutParams(tl);
        }
        st.compact = -1;
        headerHeight(h, false);
    }

    private static void restoreHeader(View h, HeaderState st) {
        View menu = view(h, "mEditMenuContainer"), edit = view(h, "mEditButton"), date = view(h, "mDateViewContainer");
        if (menu != null && st.menuVis >= 0) menu.setVisibility(st.menuVis);
        if (edit != null && st.editVis >= 0) edit.setVisibility(st.editVis);
        if (date != null && st.dateVis >= 0 && (QsHooks.s().ccHide & (1 << 5)) == 0) date.setVisibility(st.dateVis);
        if (st.power != null && st.power.getParent() instanceof ViewGroup) ((ViewGroup) st.power.getParent()).removeView(st.power);
        restoreClock(h, st);
        if (st.stockHeight != Integer.MIN_VALUE && h.getLayoutParams() != null) { h.getLayoutParams().height = st.stockHeight; h.setLayoutParams(h.getLayoutParams()); }
    }

    private static View idView(View h, String name) {
        try { int id = h.getResources().getIdentifier(name, "id", "com.android.systemui"); return id == 0 ? null : h.findViewById(id); } catch (Throwable t) { return null; }
    }

    /**
     * Header height with the small clock: status-icon row + the button row (+ margins) instead of RedMagic's fixed height
     * for the big clock. {@code measured}: called after a layout pass, so the real child positions are known.
     */
    private static void headerHeight(final View h, boolean measured) {
        if (!h.getClass().getName().equals(HEADER) || h.getLayoutParams() == null) return;
        HeaderState st = headers.get(h);
        refresh();
        boolean on = st != null && headerOn(h) && !bigClock;
        final ViewGroup.LayoutParams lp = h.getLayoutParams();
        if (!on) {
            if (st != null && st.stockHeight != Integer.MIN_VALUE && lp.height != st.stockHeight && !measured) { lp.height = st.stockHeight; h.setLayoutParams(lp); }
            return;
        }
        if (!measured) {
            st.stockHeight = lp.height;                     // checkTopPanel just wrote the stock value
            if (st.compact > 0 && lp.height != st.compact) lp.height = st.compact;
            return;
        }
        View icons = idView(h, "status_icons_group"), footer = idView(h, "qs_footer_actions_container"), time = view(h, "mTimeGroup");
        if (icons == null || footer == null || footer.getHeight() <= 0) return;
        int statusBottom = bottomIn(icons, h);
        if (time != null && time.getVisibility() == View.VISIBLE) statusBottom = Math.max(statusBottom, bottomIn(time, h));
        int compact = statusBottom + dp(h, 10) + footer.getHeight() + dp(h, 6) + h.getPaddingBottom();
        if (st.stockHeight == Integer.MIN_VALUE) st.stockHeight = lp.height;
        if (st.stockHeight > 0 && compact >= st.stockHeight) return;   // never taller than stock
        if (compact == st.compact && lp.height == compact) return;
        st.compact = compact;
        post(() -> {
            try {
                if (!headerOn(h) || bigClock || h.getLayoutParams() == null) return;
                if (h.getLayoutParams().height != st.compact) { h.getLayoutParams().height = st.compact; h.setLayoutParams(h.getLayoutParams()); }
            } catch (Throwable t) { Lx.e("QS: One UI 8 header height", t); }
        });
    }
    private static int bottomIn(View v, View ancestor) {
        int y = v.getBottom();
        for (ViewParent p = v.getParent(); p instanceof View && p != ancestor; p = p.getParent()) y += ((View) p).getTop();
        return y;
    }

}
