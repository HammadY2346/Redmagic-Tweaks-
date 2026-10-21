package com.hammad.redmagictweaks.xposed;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.hammad.redmagictweaks.Features;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * com.zte.mifavor.launcher (MiFavor launcher, fw 11.0.11) — config flags + horizontal paged app drawer
 * (+ One UI-style custom order / folders / hidden apps on top of the paged drawer, see {@link DrawerOrder}, {@link DrawerEdit}).
 *
 * Verified against the decompiled launcher:
 *  - The drawer is a stock Launcher3-style {@code AllAppsRecyclerView} (androidx RecyclerView subclass) inside
 *    {@code allapps.r} (ActivityAllAppsContainerView). {@code r.e.g()} does
 *    {@code rv.setLayoutManager(adapter.i()); rv.setAdapter(adapter)} where the adapter is
 *    {@code AllAppsGridAdapter} and the LayoutManager its inner {@code AppsGridLayoutManager}
 *    (extends ScrollableLayoutManager extends androidx GridLayoutManager, VERTICAL, span = columns).
 *    There is NO launcher-native horizontal/paged drawer mode (AllAppsPagedView is only the personal/work tab pager),
 *    so the LayoutManager swap is the right approach.
 *  - androidx RecyclerView public API names are kept (setLayoutManager, setAdapter, fling, smoothScrollBy, notify...,
 *    findChildViewUnder, getChildAdapterPosition, addOnItemTouchListener, ...), but LinearLayoutManager/GridLayoutManager
 *    accessors are R8-renamed: getOrientation()=G(), setOrientation=U(int), getSpanCount()=B0(), setSpanCount=K0(int),
 *    setSpanSizeLookup=L0(d). We use canScrollHorizontally() (kept) for the orientation and resolve B0/K0 by name with a
 *    public-unique-signature fallback.
 *  - The icon layout (all_apps_icon.xml, style BaseIcon.AllApps) is width=match_parent, so in a horizontal RecyclerView
 *    every cell would be page-wide: we force lp.width on bind. BubbleTextView#onMeasure does not honour an EXACTLY
 *    width spec that is wider than the stock all-apps cell (DeviceProfile.I2 ≈ 206 px on this phone vs 1080/5 = 216),
 *    so 5 columns came out ~50 px short and the next page's first column peeked in. Fix: after each layout we compare
 *    the first icon's decorated width with the intended cell width and put the difference into symmetric
 *    MarginLayoutParams margins (GridLayoutManager advances by decorated width incl. margins), plus right padding on the
 *    RecyclerView (clipToPadding) for the integer-division remainder so the viewport is exactly cols * cell wide.
 *  - Adapter: BaseAllAppsAdapter (allapps.O) binds from AlphabeticalAppsList#h() (List<AdapterItem>); item view type 2 =
 *    app icon, AdapterItem.appInfo (model.data.d) has getTargetComponent()/title/user; onBindViewHolder is R8-renamed
 *    o(O$b,int) (found by signature); DeviceProfile.H2 = all-apps cell height, z0() = rows estimate.
 *
 * Paging: new GridLayoutManager(HORIZONTAL, span = rows); adapter positions are remapped column-major -> row-major
 * so pages fill row by row; the last page is padded with invisible cells; fling/settle snap to page edges.
 * Page insets ({@link DrawerKeys#KEY_PAGE_PAD}): cell = (viewport - 2*pad) / cols; the first column of every page gets
 * pad as extra left margin and the last column pad as extra right margin, so the decorated widths of a page still sum to
 * the viewport (= page width for snapping) and neighbouring pages are separated by a 2*pad gap while swiping (One UI).
 * Scroll position: {@link #scrollX} measures it from a laid-out child (decorated bounds + its column). The stock
 * computeHorizontalScrollOffset() is GridLayoutManager's ESTIMATE (items before x average visible item width), exact only
 * when all columns are equally wide; with page insets the edge columns are wider, the estimate drifted a little more on
 * every page and pages settled off-centre with half a column of the neighbours showing (page 3+). Snap, settle, dots and
 * edge flips all use scrollX; after layout passes (rotation, regrid, padding) an idle drawer is re-aligned to its page.
 * Search bar position ({@link DrawerKeys#KEY_SEARCH_POS}): "Bottom (One UI)" re-rules the container's RelativeLayout children
 * before each measure ({@link #placeSearch}): bar ALIGN_PARENT_BOTTOM above the nav bar, the grid from the top down to
 * the bar (page dots end up right above it). While the search field has focus or text everything goes back to stock (top),
 * so typing / results / keyboard work exactly like stock.
 * Bottom strip: when page dots or the custom layout (Edit pill) are on, {@link DrawerEdit#reserveBottom()} px are added
 * to the launcher's own bottom padding BEFORE the Auto row count is computed, so the last row never collides with them.
 * Custom layout (switch {@link #KEY_CUSTOM_ORDER}): the visual cell list comes from {@link DrawerOrder} (persisted
 * slots + folders + hidden) instead of the launcher's alphabetical list; a folder cell is the launcher's BubbleTextView
 * of the folder's first app re-skinned by {@link DrawerEdit}; an empty slot (fixed-slot mode, {@link DrawerKeys#KEY_FIXED_SLOTS})
 * is an invisible cell exactly like the padding cells of the last page. The launcher's own model/database is never touched.
 * Extras verified in the decompiled launcher: the drawer search bar is {@code R.id.search_container_all_apps}
 * (AllAppsMenuBarLayout, added in {@code allapps.r#S()} from onFinishInflate) with the "⋮" {@code R.id.all_apps_menu_btn}
 * inside it; the drawer RecyclerView is laid out BELOW it (RelativeLayout rule), so GONE collapses the space. All-apps
 * app labels are BubbleTextView with iconDisplay == 1 (field f11009p), text size DeviceProfile.N2 set in the constructor.
 * Needs a launcher restart after toggling the paged drawer (the LayoutManager is installed once when the drawer view is created).
 */
final class LauncherHooks {
    private LauncherHooks() {}

    /** Feature key (Features.java is owned by the coordinator; the switch may not exist there yet). */
    static final String KEY_CUSTOM_ORDER = "launcher_drawer_custom_order";
    static final String KEY_DOTS = "launcher_drawer_dots";

    private static final String RV = "androidx.recyclerview.widget.RecyclerView";
    private static final String GLM = "androidx.recyclerview.widget.GridLayoutManager";
    private static final String AARV = "com.android.launcher3.allapps.AllAppsRecyclerView";
    private static final String SEARCH_RV = "com.android.launcher3.allapps.SearchRecyclerView";
    private static final String GRID_ADAPTER = "com.android.launcher3.allapps.AllAppsGridAdapter";
    private static final String APPS_GLM = "com.android.launcher3.allapps.AllAppsGridAdapter$AppsGridLayoutManager";
    private static final String MIFAVOR_CONTEXT = "com.android.launcher3.C1308v4";       // MifavorContext (obfuscated name, fw 11.0.11)
    private static final String RESOURCE_CONTEXT = "com.android.launcher3.resource.Q";   // ResourceContext
    private static final String CONTAINER = "com.android.launcher3.allapps.r";           // ActivityAllAppsContainerView (obfuscated, fw 11.0.11)
    private static final String BUBBLE = "com.android.launcher3.BubbleTextView";
    private static final String BUBBLE_DISPLAY_FIELD = "f11009p";                       // BubbleTextView.mDisplay (1 = all apps)
    private static final String DP_ALLAPPS_TEXT_FIELD = "f14416N2";                     // DeviceProfile.allAppsIconTextSizePx

    private static final String[] NOTIFY_ITEM = {
            "notifyItemChanged", "notifyItemInserted", "notifyItemMoved", "notifyItemRangeChanged",
            "notifyItemRangeInserted", "notifyItemRangeRemoved", "notifyItemRemoved"};

    /** Per-drawer paging state. */
    static final class State {
        final WeakReference<View> recycler;
        final WeakReference<Object> stockLm;      // launcher's own AppsGridLayoutManager (span = columns)
        WeakReference<Object> ourLm;
        int cols = 5, rows = 6;
        boolean autoRows;
        int cellH;                               // stock all-apps cell height (first freshly created icon)
        int iconSize;                            // BubbleTextView.getIconSize() of a bound icon
        WeakReference<Object> adapterRef;        // weak: the State is a value of maps keyed by the RecyclerView/adapter
        Object adapter() { return adapterRef == null ? null : adapterRef.get(); }
        /** Visual cells in page order (apps and folders); a null entry is an empty slot (fixed-slot mode). */
        List<DrawerOrder.Cell> cells = new ArrayList<>();
        int[] iconPositions = new int[0];        // visual index -> adapter position of the cell's (anchor) app, -1 = empty slot
        int[] visualByPos = new int[0];          // adapter position -> visual index, -1 when not shown
        final Map<String, Object> infoByKey = new HashMap<>();   // app key -> launcher AppInfo (for folder icons / popup / launch)
        DrawerOrder order;
        DrawerEdit edit;
        View.OnClickListener stockClick;         // launcher's all-apps icon listeners (restored on views that were folder cells)
        View.OnLongClickListener stockLong;
        boolean customNow;                       // value of the custom-order switch at the last refresh
        boolean fixedNow;                        // value of the fixed-slots switch at the last refresh
        boolean customActive;                    // custom layout really in use (switch on AND the adapter item list was identified)
        int cachedCount = -1;
        boolean dirty = true;
        boolean snapping;
        boolean loggedBind;
        // column fit (see header)
        int slack;                               // px of each cell given to margins because the icon measures narrower
        int fitAttempts;
        int basePadR = -1, padOursR = -1;        // launcher's own right padding / the value we last set (base + remainder)
        int basePadB = -1, padOursB = -1;        // launcher's own bottom padding / the value we last set (base + reserved strip)
        int pagePad;                             // px inset of every page on both sides (DrawerKeys.KEY_PAGE_PAD)
        int reserve;                             // px of the bottom strip (dots / pill), cached by reserveB()
        int lastCellW, lastW, lastH, lastReserve = -1;
        int realignTries;                        // consecutive post-layout re-alignments (bounded, see fit())
        boolean clipSet, fitLogged, pillCustom, dotsShown, searchBottomNow;
        State(View rv, Object lm) { recycler = new WeakReference<>(rv); stockLm = new WeakReference<>(lm); }
        int page() { return Math.max(1, cols * rows); }
        int padded() { int k = iconPositions.length; int p = page(); return ((k + p - 1) / p) * p; }
        /** True when visual index v is an app/folder cell (not a gap, not padding). */
        boolean hasCell(int v) { return v >= 0 && v < iconPositions.length && iconPositions[v] >= 0; }
        /** Column (0..cols-1) of a GLM position. */
        int column(int glmPos) { return (glmPos / Math.max(1, rows)) % Math.max(1, cols); }
        /** GLM (column-major, span = rows) position -> visual (row-major, paged) index. */
        int visual(int pos) {
            int rr = Math.max(1, rows), cc = Math.max(1, cols);
            int c = pos / rr, r = pos % rr;
            int page = c / cc, cp = c % cc;
            return page * (rr * cc) + r * cc + cp;
        }
        /** Inverse of {@link #visual(int)}: visual index -> GLM (RecyclerView-side) position. */
        int glmPos(int v) {
            int rr = Math.max(1, rows), cc = Math.max(1, cols);
            int page = v / (rr * cc), idx = v % (rr * cc);
            int r = idx / cc, cp = idx % cc;
            return (page * cc + cp) * rr + r;
        }
        int visualOfPos(int adapterPos) { return adapterPos >= 0 && adapterPos < visualByPos.length ? visualByPos[adapterPos] : -1; }
    }

    private static final Map<Object, State> byRecycler = Collections.synchronizedMap(new WeakHashMap<>());
    /** The State of the drawer currently on screen (for the ⋮ menu), or null. */
    static State visibleState() {
        synchronized (byRecycler) {
            State any = null;
            for (State st : byRecycler.values()) {
                View rv = st == null ? null : st.recycler.get();
                if (rv == null || st.edit == null || !rv.isAttachedToWindow()) continue;
                if (rv.isShown()) return st;
                any = st;
            }
            return any;
        }
    }

    private static final Map<Object, State> byAdapter = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> ourLms = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> diagSeen = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<Class<?>> hookedAdapters = new HashSet<>();
    private static Method origItemType, origCount;
    private static Method glmGetSpanCount, glmSetSpanCount;
    private static boolean loggedSetLm, loggedSetAdapter;

    static void install(ClassLoader cl) {
        try { configFlags(cl); } catch (Throwable t) { Lx.e("Launcher: config-flag install crashed", t); }
        try { pagedDrawer(cl); } catch (Throwable t) { Lx.e("Launcher: paged-drawer install crashed", t); }
        try { drawerChrome(cl); } catch (Throwable t) { Lx.e("Launcher: drawer-chrome install crashed", t); }
        try { DrawerMenu.install(cl); } catch (Throwable t) { Lx.e("Launcher: drawer-menu install crashed", t); }
        Lx.i("Launcher: hooks ready (paged=" + Cfg.on(Features.LAUNCHER_PAGED) + " rows=" + Cfg.choice(Features.LAUNCHER_PAGED_ROWS)
                + " noFastScroll=" + Cfg.on(Features.LAUNCHER_NO_FASTSCROLL) + " customOrder=" + Cfg.on(KEY_CUSTOM_ORDER)
                + " fixedSlots=" + Cfg.on(DrawerKeys.KEY_FIXED_SLOTS) + " dots=" + Cfg.on(KEY_DOTS) + " pagePad=" + DrawerKeys.pagePadDp() + "dp"
                + " hideSearch=" + Cfg.on(DrawerKeys.KEY_HIDE_SEARCH) + " searchPos=" + DrawerKeys.searchPos() + " hideMenu=" + Cfg.on(DrawerKeys.KEY_HIDE_MENU)
                + " labelSize=" + Cfg.choice(DrawerKeys.KEY_LABEL_SIZE) + ")");
    }

    // ------------------------------------------------------------------ drawer chrome: search bar / menu button / label size

    private static void drawerChrome(ClassLoader cl) {
        int n = 0;
        Class<?> container = XposedHelpers.findClassIfExists(CONTAINER, cl);
        if (container == null) Lx.w("Launcher: " + CONTAINER + " (all-apps container) not found; hide search/menu unavailable");
        else if (H.hook(H.find(container, "onFinishInflate"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    View self = (View) p.thisObject;
                    int searchPos = DrawerKeys.searchPos();
                    if (searchPos == DrawerKeys.SEARCH_BOTTOM && self instanceof ViewGroup) watchSearch((ViewGroup) self);
                    boolean hideSearch = searchPos == DrawerKeys.SEARCH_HIDDEN, hideMenu = Cfg.on(DrawerKeys.KEY_HIDE_MENU);
                    if (!hideSearch && !hideMenu) return;
                    String pkg = self.getContext().getPackageName();
                    android.content.res.Resources res = self.getResources();
                    View bar = null, menu = null;
                    int barId = res.getIdentifier("search_container_all_apps", "id", pkg);
                    int menuId = res.getIdentifier("all_apps_menu_btn", "id", pkg);
                    if (barId != 0) bar = self.findViewById(barId);
                    if (menuId != 0) menu = self.findViewById(menuId);
                    if (hideSearch) {
                        if (bar != null) { bar.setVisibility(View.GONE); Lx.i("Drawer: search bar hidden (" + bar.getClass().getSimpleName() + ")"); }
                        else Lx.w("Drawer: search_container_all_apps not found in " + self.getClass().getName());
                    } else if (hideMenu) {
                        if (menu != null) { menu.setVisibility(View.GONE); Lx.i("Drawer: menu button hidden"); }
                        else Lx.w("Drawer: all_apps_menu_btn not found in " + self.getClass().getName());
                    }
                } catch (Throwable t) { Lx.e("Drawer: hide search/menu", t); }
            }
        })) { n++; } else Lx.w("Launcher: " + CONTAINER + "#onFinishInflate not hooked");

        // search bar position: re-rule the container's children right before RelativeLayout measures them (the container
        // and its super class SpringRelativeLayout both end in RelativeLayout#onMeasure)
        if (container != null) {
            final Class<?> cont = container;
            Method measure = null;
            try { measure = android.widget.RelativeLayout.class.getDeclaredMethod("onMeasure", int.class, int.class); } catch (Throwable t) { Lx.w("Drawer: RelativeLayout#onMeasure not found: " + t); }
            if (measure != null && H.hook(measure, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!cont.isInstance(p.thisObject)) return;
                    try {
                        ViewGroup self = (ViewGroup) p.thisObject;
                        if (placeSearch(self)) self.requestLayout();       // RelativeLayout#requestLayout marks its child graph dirty -> re-sorted now
                    } catch (Throwable t) { Lx.e("Drawer: search position", t); }
                }
            })) n++; else Lx.w("Launcher: RelativeLayout#onMeasure not hooked (search bar position unavailable)");
        }

        Class<?> bubble = XposedHelpers.findClassIfExists(BUBBLE, cl);
        if (bubble == null) Lx.w("Launcher: BubbleTextView not found; drawer label size unavailable");
        else {
            final boolean[] logged = new boolean[2];
            XC_MethodHook cb = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        float scale = DrawerKeys.labelScale();
                        if (scale >= 0.999f) return;
                        android.widget.TextView tv = (android.widget.TextView) p.thisObject;
                        int display = -1;
                        try { display = XposedHelpers.getIntField(tv, BUBBLE_DISPLAY_FIELD); }
                        catch (Throwable t) {
                            if (!logged[1]) { logged[1] = true; Lx.w("Drawer: BubbleTextView." + BUBBLE_DISPLAY_FIELD + " missing (" + t + "), label size uses the DeviceProfile text size instead"); }
                            try {
                                Object dp = deviceProfile(tv);
                                if (dp != null && Math.abs(tv.getTextSize() - XposedHelpers.getIntField(dp, DP_ALLAPPS_TEXT_FIELD)) < 1.5f) display = 1;
                            } catch (Throwable ignored) { }
                        }
                        if (display != 1 || tv.getTextSize() <= 0) return;
                        float px = tv.getTextSize() * scale;
                        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px);
                        if (!logged[0]) { logged[0] = true; Lx.i("Drawer: all-apps label size " + Math.round(tv.getTextSize() / scale) + " -> " + Math.round(px) + " px (x" + scale + ")"); }
                    } catch (Throwable t) { Lx.e("Drawer: label size", t); }
                }
            };
            // only the widest constructor: the others chain into it (this(...)) and would apply the scale twice
            Constructor<?> widest = null;
            for (Constructor<?> k : bubble.getDeclaredConstructors()) if (widest == null || k.getParameterTypes().length > widest.getParameterTypes().length) widest = k;
            boolean ok = false;
            if (widest != null) { try { XposedBridge.hookMethod(widest, cb); ok = true; } catch (Throwable t) { Lx.w("Drawer: BubbleTextView ctor hook failed: " + t); } }
            if (ok) { n++; Lx.i("Drawer: label size hook on BubbleTextView(" + widest.getParameterTypes().length + " args)"); }
            else Lx.w("Launcher: no BubbleTextView constructor hooked (label size unavailable)");
        }
        Lx.i("Launcher: drawer-chrome hooks ready (" + n + "/3)");
    }

    // ------------------------------------------------------------------ search bar position (One UI: bottom)

    /** RelativeLayout verbs we move (index order used by the rule arrays below). */
    private static final int[] VERBS = {android.widget.RelativeLayout.ABOVE, android.widget.RelativeLayout.BELOW, android.widget.RelativeLayout.ALIGN_TOP,
            android.widget.RelativeLayout.ALIGN_PARENT_TOP, android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM};
    private static final int V_ABOVE = 0, V_BELOW = 1, V_ALIGN_TOP = 2, V_PARENT_TOP = 3, V_PARENT_BOTTOM = 4;

    /** Stock vs. our layout params of one container child; per property, so a launcher change to one of them is adopted as the new stock value. */
    private static final class Placement { int[] stockRules, ourRules; int stockTop, stockBottom, ourTop = Integer.MIN_VALUE, ourBottom = Integer.MIN_VALUE; }
    private static final Map<View, Placement> placed = Collections.synchronizedMap(new WeakHashMap<>());
    /** True while the drawer search bar sits at the bottom (the grid then ends above it, so the nav-bar padding is not needed). */
    static volatile boolean searchBottom;
    private static boolean loggedPlace;

    private static int[] rulesOf(android.widget.RelativeLayout.LayoutParams lp) {
        int[] r = new int[VERBS.length];
        for (int i = 0; i < VERBS.length; i++) r[i] = lp.getRule(VERBS[i]);
        return r;
    }

    private static void setRules(android.widget.RelativeLayout.LayoutParams lp, int[] r) {
        for (int i = 0; i < VERBS.length; i++) {
            if (lp.getRule(VERBS[i]) == r[i]) continue;
            if (r[i] == 0) lp.removeRule(VERBS[i]); else lp.addRule(VERBS[i], r[i]);
        }
    }

    /** Navigation-bar inset at the bottom of the window (gesture pill or buttons). */
    static int navInset(View v) {
        try {
            android.view.WindowInsets wi = v.getRootWindowInsets();
            if (wi != null) {
                try { return wi.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom; }
                catch (Throwable t) { return wi.getSystemWindowInsetBottom(); }
            }
        } catch (Throwable ignored) { }
        return navBarRes();
    }

    /** The system navigation_bar_height dimen (what the launcher adds as bottom padding to the drawer grid). */
    static int navBarRes() {
        try {
            android.content.res.Resources sys = android.content.res.Resources.getSystem();
            int id = sys.getIdentifier("navigation_bar_height", "dimen", "android");
            return id == 0 ? 0 : sys.getDimensionPixelSize(id);
        } catch (Throwable t) { return 0; }
    }

    /** Re-measure the container when the search field gains/loses focus or text (bottom <-> top while searching). */
    private static void watchSearch(final ViewGroup root) {
        try {
            root.getViewTreeObserver().addOnGlobalFocusChangeListener(new android.view.ViewTreeObserver.OnGlobalFocusChangeListener() {
                @Override public void onGlobalFocusChanged(View oldFocus, View newFocus) { root.requestLayout(); }
            });
            String pkg = root.getContext().getPackageName();
            int editId = root.getResources().getIdentifier("apps_search_container_layout", "id", pkg);
            View edit = editId != 0 ? root.findViewById(editId) : null;
            if (edit instanceof android.widget.TextView) {
                ((android.widget.TextView) edit).addTextChangedListener(new android.text.TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                    @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                    @Override public void afterTextChanged(android.text.Editable s) { root.requestLayout(); }
                });
            }
            Lx.i("Drawer: search bar at the bottom (One UI), field watcher " + (edit instanceof android.widget.TextView ? "on" : "MISSING (focus only)"));
        } catch (Throwable t) { Lx.e("Drawer: search watcher", t); }
    }

    /**
     * Bottom search bar (One UI): bar ALIGN_PARENT_BOTTOM just above the nav bar, the apps grid (and the work/personal header
     * when the launcher shows one) from the top of the drawer down to the bar. Stock layout while the field has focus or
     * text, and whenever the setting is not "Bottom". Called before every measure of the all-apps container; idempotent.
     * @return true when a layout param changed (the caller then re-sorts the RelativeLayout)
     */
    static boolean placeSearch(ViewGroup root) {
        android.content.res.Resources res = root.getResources();
        String pkg = root.getContext().getPackageName();
        int barId = res.getIdentifier("search_container_all_apps", "id", pkg);
        if (barId == 0) return false;
        int headerId = res.getIdentifier("all_apps_header", "id", pkg);
        int editId = res.getIdentifier("apps_search_container_layout", "id", pkg);
        View bar = root.findViewById(barId);
        boolean barOk = bar != null && bar.getParent() == root && bar.getVisibility() != View.GONE
                && bar.getLayoutParams() instanceof android.widget.RelativeLayout.LayoutParams;
        View edit = barOk && editId != 0 ? bar.findViewById(editId) : null;
        boolean searching = edit != null && (edit.hasFocus() || (edit instanceof android.widget.TextView && ((android.widget.TextView) edit).length() > 0));
        boolean bottom = barOk && !searching && DrawerKeys.searchPosCached() == DrawerKeys.SEARCH_BOTTOM;
        if (!bottom && placed.isEmpty()) { searchBottom = false; return false; }
        boolean changed = false;
        float density = res.getDisplayMetrics().density;
        int statusTop = 0;
        // the bar first: its stock top margin is the status-bar inset the grid needs once the bar has left the top
        List<View> order = new ArrayList<>();
        if (barOk) order.add(bar);
        for (int i = 0; i < root.getChildCount(); i++) { View c = root.getChildAt(i); if (c != bar) order.add(c); }
        for (View c : order) {
            if (!(c.getLayoutParams() instanceof android.widget.RelativeLayout.LayoutParams)) continue;
            android.widget.RelativeLayout.LayoutParams lp = (android.widget.RelativeLayout.LayoutParams) c.getLayoutParams();
            Placement pl = placed.get(c);
            int[] cur = rulesOf(lp);
            if (!bottom) {                                                            // back to stock (only what is still ours)
                if (pl != null) {
                    if (pl.ourRules != null && java.util.Arrays.equals(cur, pl.ourRules)) setRules(lp, pl.stockRules);
                    if (lp.topMargin == pl.ourTop) lp.topMargin = pl.stockTop;
                    if (lp.bottomMargin == pl.ourBottom) lp.bottomMargin = pl.stockBottom;
                    placed.remove(c);
                    changed = true;
                }
                continue;
            }
            if (pl == null) { pl = new Placement(); pl.stockRules = cur; pl.stockTop = lp.topMargin; pl.stockBottom = lp.bottomMargin; }
            else {                                                                    // the launcher re-ruled / re-margined it: new stock
                if (pl.ourRules == null || !java.util.Arrays.equals(cur, pl.ourRules)) pl.stockRules = cur;
                if (lp.topMargin != pl.ourTop) pl.stockTop = lp.topMargin;
                if (lp.bottomMargin != pl.ourBottom) pl.stockBottom = lp.bottomMargin;
            }
            int[] s = pl.stockRules, want = s.clone();
            int top = pl.stockTop, bot = pl.stockBottom;
            int gapTop = Math.round(12 * density);
            if (c == bar) {
                want[V_PARENT_TOP] = 0; want[V_BELOW] = 0; want[V_ALIGN_TOP] = 0; want[V_PARENT_BOTTOM] = android.widget.RelativeLayout.TRUE;
                statusTop = Math.max(0, pl.stockTop);
                top = 0;
                bot = navInset(root) + Math.round(10 * density);
            } else if (s[V_BELOW] == barId) {                                         // apps grid (and the search results list)
                want[V_BELOW] = 0; want[V_PARENT_TOP] = android.widget.RelativeLayout.TRUE; want[V_ABOVE] = barId;
                top = statusTop + gapTop;
            } else if (s[V_ALIGN_TOP] == barId) {                                     // personal/work header
                want[V_ALIGN_TOP] = 0; want[V_PARENT_TOP] = android.widget.RelativeLayout.TRUE;
                top = statusTop + gapTop;
            } else if (headerId != 0 && s[V_BELOW] == headerId) {                     // tab pager under the header
                want[V_ABOVE] = barId;
            } else continue;                                                          // not related to the bar: untouched
            pl.ourRules = want; pl.ourTop = top; pl.ourBottom = bot;
            placed.put(c, pl);
            if (!java.util.Arrays.equals(cur, want)) { setRules(lp, want); changed = true; }
            if (lp.topMargin != top) { lp.topMargin = top; changed = true; }
            if (lp.bottomMargin != bot) { lp.bottomMargin = bot; changed = true; }
        }
        if (searchBottom != bottom) {
            searchBottom = bottom;
            Lx.i("Drawer: search bar " + (bottom ? "at the bottom" : searching ? "back at the top (searching)" : "at the top"));
        }
        if (changed && !loggedPlace && bottom) {
            loggedPlace = true;
            Lx.i("Drawer: bottom search placed (statusTop=" + statusTop + " nav=" + navInset(root) + " children=" + placed.size() + ")");
        }
        return changed;
    }

    // ------------------------------------------------------------------ config flags

    private static Integer override(String key) {
        switch (key) {
            case "config_support_rotation":
            case "config_rotation_default_value":
                return Cfg.on(Features.LAUNCHER_ROTATION) ? 1 : null;
            case "config_num_columns": { int i = Cfg.choice(Features.LAUNCHER_COLS); return i > 0 && i < Features.GRID_COLS.length ? Integer.valueOf(Features.GRID_COLS[i]) : null; }
            case "config_num_rows": { int i = Cfg.choice(Features.LAUNCHER_ROWS); return i > 0 && i < Features.GRID_ROWS.length ? Integer.valueOf(Features.GRID_ROWS[i]) : null; }
            case "config_num_hotseat_icons": { int i = Cfg.choice(Features.LAUNCHER_HOTSEAT); return i > 0 && i < Features.HOTSEAT.length ? Integer.valueOf(Features.HOTSEAT[i]) : null; }
            case "config_show_hotseat_text": return Cfg.on(Features.LAUNCHER_HOTSEAT_TEXT) ? 1 : null;
            case "config_two_line_title": return Cfg.on(Features.LAUNCHER_TWO_LINE) ? 1 : null;
            case "config_circle_scroll_status":
            case "config_circle_scroll_status_internal":
            case "config_show_circle_scroll_switch":
                return Cfg.on(Features.LAUNCHER_CIRCLE) ? 1 : null;
            case "config_show_fast_scroller_bar": return Cfg.on(Features.LAUNCHER_NO_FASTSCROLL) ? 0 : null;
            default: return null;
        }
    }

    private static void configFlags(ClassLoader cl) {
        final Set<String> seen = Collections.synchronizedSet(new HashSet<>());
        XC_MethodHook cb = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    Object key = p.args.length >= 2 ? p.args[1] : p.args[0];
                    if (!(key instanceof String)) return;
                    Integer v = override((String) key);
                    if (v != null) {
                        if (seen.add((String) key)) Lx.i("Launcher: config " + key + " " + p.getResult() + " -> " + v);
                        p.setResult(v);
                    }
                } catch (Throwable t) { Lx.e("Launcher: config override", t); }
            }
        };
        int n = 0;
        Class<?> mc = H.cls(cl, MIFAVOR_CONTEXT);
        if (mc != null) {
            Method m = H.findBySig(mc, int.class, Context.class, String.class, int.class);   // MifavorContext.b(Context,String,int); a() delegates to it
            if (H.hook(m, cb)) { n++; Lx.i("Launcher: hooked " + MIFAVOR_CONTEXT + "#" + m.getName() + "(Context,String,int)"); }
        }
        Class<?> rc = H.cls(cl, RESOURCE_CONTEXT);
        if (rc != null) {
            Method m = H.find(rc, "o", String.class);                                      // ResourceContext.o(String) -> int
            if (m != null && m.getReturnType() == int.class && Modifier.isStatic(m.getModifiers()) && H.hook(m, cb)) {
                n++; Lx.i("Launcher: hooked " + RESOURCE_CONTEXT + "#o(String)");
            } else Lx.w("Launcher: " + RESOURCE_CONTEXT + "#o(String) not found or wrong shape");
        }
        Lx.i("Launcher: config-flag hooks ready (" + n + "/2)");
    }

    // ------------------------------------------------------------------ paged drawer

    private static void pagedDrawer(final ClassLoader cl) {
        final Class<?> rv = H.cls(cl, RV);
        final Class<?> glm = H.cls(cl, GLM);
        final Class<?> aarv = H.cls(cl, AARV);
        final Class<?> searchRv = XposedHelpers.findClassIfExists(SEARCH_RV, cl);
        final Class<?> gridAdapter = XposedHelpers.findClassIfExists(GRID_ADAPTER, cl);
        final Class<?> appsGlm = XposedHelpers.findClassIfExists(APPS_GLM, cl);
        Lx.i("Drawer: classes RecyclerView=" + (rv != null) + " GridLayoutManager=" + (glm != null) + " AllAppsRecyclerView=" + (aarv != null)
                + " SearchRecyclerView=" + (searchRv != null) + " AllAppsGridAdapter=" + (gridAdapter != null) + " AppsGridLayoutManager=" + (appsGlm != null));
        if (rv == null || glm == null || aarv == null) { Lx.w("Drawer: core classes missing, paged drawer disabled"); return; }

        final Class<?> lmClass = XposedHelpers.findClassIfExists(RV + "$LayoutManager", cl);
        final Class<?> adapterClass = XposedHelpers.findClassIfExists(RV + "$Adapter", cl);
        final Class<?> vhClass = XposedHelpers.findClassIfExists(RV + "$ViewHolder", cl);
        if (lmClass == null || adapterClass == null || vhClass == null) { Lx.w("Drawer: RecyclerView inner classes missing (LM=" + lmClass + " Adapter=" + adapterClass + " VH=" + vhClass + ")"); return; }

        final Constructor<?> glmCtor;
        try { glmCtor = glm.getConstructor(Context.class, int.class, int.class, boolean.class); }
        catch (NoSuchMethodException e) { Lx.e("Drawer: GridLayoutManager(Context,int,int,boolean) ctor missing", e); return; }

        // R8-renamed GridLayoutManager accessors (fw 11.0.11): getSpanCount()=B0, setSpanCount(int)=K0.
        glmGetSpanCount = H.find(glm, "B0");
        if (glmGetSpanCount == null || glmGetSpanCount.getReturnType() != int.class) glmGetSpanCount = uniquePublic(glm, int.class);
        glmSetSpanCount = H.find(glm, "K0", int.class);
        if (glmSetSpanCount == null) glmSetSpanCount = uniquePublic(glm, void.class, int.class);
        Lx.i("Drawer: GLM getSpanCount=" + (glmGetSpanCount == null ? "MISSING" : glmGetSpanCount.getName())
                + " setSpanCount=" + (glmSetSpanCount == null ? "MISSING" : glmSetSpanCount.getName()));

        int hooked = 0;

        // 1. swap the layout manager when the drawer RecyclerView gets the launcher's vertical grid
        if (H.hook(H.find(rv, "setLayoutManager", lmClass), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    View self = (View) p.thisObject;
                    if (!aarv.isInstance(self) || (searchRv != null && searchRv.isInstance(self))) return;
                    Object lm = p.args[0];
                    if (lm == null) { byRecycler.remove(self); return; }
                    if (ourLms.containsKey(lm)) return;                                   // already ours
                    boolean on = Cfg.on(Features.LAUNCHER_PAGED);
                    if (!loggedSetLm) {
                        loggedSetLm = true;
                        Lx.i("Drawer: setLayoutManager rv=" + self.getClass().getName() + " lm=" + lm.getClass().getName() + " paged=" + on);
                    }
                    if (!on) { byRecycler.remove(self); return; }
                    if (!glm.isInstance(lm)) { Lx.w("Drawer: stock layout manager is not a GridLayoutManager (" + lm.getClass().getName() + "), skipping"); return; }
                    boolean horizontal = false;
                    try { horizontal = (Boolean) XposedHelpers.callMethod(lm, "canScrollHorizontally"); } catch (Throwable ignored) { }
                    if (horizontal) { Lx.w("Drawer: stock layout manager already horizontal, skipping"); return; }

                    State old = byRecycler.get(self);
                    State st = new State(self, lm);
                    if (old != null) { st.cellH = old.cellH; st.iconSize = old.iconSize; st.slack = old.slack; st.basePadB = old.basePadB; st.padOursB = old.padOursB; st.basePadR = old.basePadR; st.padOursR = old.padOursR; }
                    st.pagePad = Math.round(DrawerKeys.pagePadDp() * self.getResources().getDisplayMetrics().density);
                    st.cols = Math.max(1, colsOf(lm, 5));
                    int ri = Cfg.choice(Features.LAUNCHER_PAGED_ROWS);
                    st.autoRows = !(ri > 0 && ri < Features.PAGED_ROWS.length);
                    try { st.order = DrawerOrder.get(self.getContext()); } catch (Throwable t) { Lx.e("Drawer: DrawerOrder init", t); }
                    try {
                        if (old != null && old.edit != null) { st.edit = old.edit; st.edit.rebind(st); }   // never attach a second touch listener
                        else st.edit = new DrawerEdit(st, self);
                        reserveB(st);                                                              // the bottom strip counts before Auto rows
                    } catch (Throwable t) { Lx.e("Drawer: DrawerEdit init", t); }
                    st.rows = st.autoRows ? autoRows(st, self) : Integer.parseInt(Features.PAGED_ROWS[ri]);
                    Object ours = glmCtor.newInstance(self.getContext(), st.rows, 0 /* HORIZONTAL */, false);
                    ourLms.put(ours, Boolean.TRUE);
                    st.ourLm = new WeakReference<>(ours);
                    p.args[0] = ours;
                    byRecycler.put(self, st);
                    Lx.i("Drawer: paged layout " + st.cols + "x" + st.rows + (st.autoRows ? " (auto rows)" : "") + " pagePad=" + st.pagePad + "px installed on "
                            + self.getClass().getSimpleName() + " (stock lm " + lm.getClass().getSimpleName() + ")");
                } catch (Throwable t) { Lx.e("Drawer: setLayoutManager", t); }
            }
        })) hooked++; else Lx.w("Drawer: RecyclerView.setLayoutManager not hooked");

        // 2. remember the adapter and hook its class once
        if (H.hook(H.find(rv, "setAdapter", adapterClass), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    Object ad = p.args[0];
                    if (!loggedSetAdapter && ad != null && aarv.isInstance(p.thisObject)) {
                        loggedSetAdapter = true;
                        Lx.i("Drawer: setAdapter rv=" + p.thisObject.getClass().getSimpleName() + " adapter=" + ad.getClass().getName());
                    }
                    State st = byRecycler.get(p.thisObject);
                    if (st == null || ad == null) return;
                    st.adapterRef = new WeakReference<>(ad);
                    st.cachedCount = -1;
                    st.dirty = true;
                    byAdapter.put(ad, st);
                    stockListeners(st, ad);
                    hookAdapterClass(ad.getClass(), vhClass);
                } catch (Throwable t) { Lx.e("Drawer: setAdapter", t); }
            }
        })) hooked++; else Lx.w("Drawer: RecyclerView.setAdapter not hooked");

        // 3. keep the RecyclerView consistent: any adapter notification becomes a full refresh of our remapped list
        if (H.hook(H.find(adapterClass, "notifyDataSetChanged"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                State st = byAdapter.get(p.thisObject);
                if (st != null) st.dirty = true;
            }
        })) hooked++;
        for (String name : NOTIFY_ITEM) {
            for (Method m : H.findAll(adapterClass, name)) {
                if (H.hook(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        State st = byAdapter.get(p.thisObject);
                        if (st == null || !Cfg.on(Features.LAUNCHER_PAGED)) return;
                        try {
                            st.dirty = true;
                            XposedHelpers.callMethod(p.thisObject, "notifyDataSetChanged");
                            p.setResult(null);
                        } catch (Throwable t) { Lx.e("Drawer: notify", t); }
                    }
                })) hooked++;
            }
        }

        // 4. page snapping: fling + settle
        if (H.hook(H.find(rv, "fling", int.class, int.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                State st = byRecycler.get(p.thisObject);
                if (st == null) return;
                try {
                    View self = (View) p.thisObject;
                    int vx = (Integer) p.args[0];
                    int pageW = pageWidth(self, st);
                    if (pageW <= 0) return;
                    int offset = scrollX(self, st);                       // real px, not GridLayoutManager's estimate
                    int maxPage = pageCount(st) - 1;
                    int target = Math.round((float) offset / pageW);
                    if (vx > 600) target = (int) Math.floor((float) offset / pageW) + 1;
                    else if (vx < -600) target = (int) Math.ceil((float) offset / pageW) - 1;
                    target = Math.max(0, Math.min(maxPage, target));
                    int dx = target * pageW - offset;
                    if (dx == 0) { p.setResult(false); return; }     // nothing to do: let RecyclerView go IDLE itself
                    st.snapping = true;
                    XposedHelpers.callMethod(self, "smoothScrollBy", dx, 0);
                    p.setResult(true);
                } catch (Throwable t) { Lx.e("Drawer: fling", t); }
            }
        })) hooked++; else Lx.w("Drawer: RecyclerView.fling not hooked");

        if (H.hook(H.find(aarv, "onScrollStateChanged", int.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                State st = byRecycler.get(p.thisObject);
                if (st == null) return;
                int state = (Integer) p.args[0];
                if (state != 0 /* IDLE */) return;
                try {
                    final View self = (View) p.thisObject;
                    int pageW = pageWidth(self, st);
                    if (pageW <= 0) return;
                    int offset = scrollX(self, st);
                    final int page = Math.max(0, Math.min(pageCount(st) - 1, Math.round((float) offset / pageW)));
                    int dx = page * pageW - offset;
                    if (Math.abs(dx) <= 1) { st.snapping = false; st.realignTries = 0; return; }
                    if (!st.snapping) {                                       // a drag ended between pages: animate to the nearest
                        st.snapping = true;
                        XposedHelpers.callMethod(self, "smoothScrollBy", dx, 0);
                    } else {                                                  // our snap ended but is still off (interrupted / re-laid out): finish instantly
                        st.snapping = false;
                        self.post(new Runnable() { @Override public void run() { scrollToPage(self, st, page, false); } });
                        Lx.i("Drawer: settle correction " + dx + "px on page " + page);
                    }
                } catch (Throwable t) { Lx.e("Drawer: settle", t); }
            }
        })) hooked++; else Lx.w("Drawer: AllAppsRecyclerView.onScrollStateChanged not hooked");

        // 5. first-layout diagnostics + re-fit on size changes (rotation / insets)
        if (H.hook(H.find(aarv, "onSizeChanged", int.class, int.class, int.class, int.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    View self = (View) p.thisObject;
                    if (self.getWidth() <= 0 || self.getHeight() <= 0) return;
                    State st = byRecycler.get(self);
                    if (diagSeen.put(self, Boolean.TRUE) == null) {
                        Object lm = null, ad = null; Object count = "?";
                        try { lm = XposedHelpers.callMethod(self, "getLayoutManager"); } catch (Throwable ignored) { }
                        try { ad = XposedHelpers.callMethod(self, "getAdapter"); if (ad != null) count = XposedHelpers.callMethod(ad, "getItemCount"); } catch (Throwable ignored) { }
                        Lx.i("Drawer: first layout rv=" + self.getClass().getName() + " " + self.getWidth() + "x" + self.getHeight()
                                + " pad=" + self.getPaddingLeft() + "/" + self.getPaddingTop() + "/" + self.getPaddingRight() + "/" + self.getPaddingBottom()
                                + " lm=" + (lm == null ? "null" : lm.getClass().getName()) + " adapter=" + (ad == null ? "null" : ad.getClass().getName())
                                + " itemCount=" + count + " paged=" + (st != null) + (st != null ? " grid=" + st.cols + "x" + st.rows : ""));
                    }
                    if (st == null) return;
                    Object stock = st.stockLm.get();
                    int cols = stock != null ? colsOf(stock, st.cols) : st.cols;
                    int rows = st.autoRows ? autoRows(st, self) : st.rows;
                    if (cols != st.cols || rows != st.rows) regrid(st, self, cols, rows, "size change");
                    else st.fitAttempts = 0;
                } catch (Throwable t) { Lx.e("Drawer: onSizeChanged", t); }
            }
        })) hooked++; else Lx.w("Drawer: AllAppsRecyclerView.onSizeChanged not hooked");

        // 5b. after every layout pass: make columns exactly pageWidth/cols wide, keep the viewport an exact page, place the pill
        if (H.hook(H.find(aarv, "onLayout", boolean.class, int.class, int.class, int.class, int.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                State st = byRecycler.get(p.thisObject);
                if (st == null) return;
                try { fit(st, (View) p.thisObject); } catch (Throwable t) { Lx.e("Drawer: fit", t); }
            }
        })) hooked++; else Lx.w("Drawer: RecyclerView.onLayout not hooked (column fit disabled)");

        // 6. the launcher changes its column count via AllAppsGridAdapter.u(int) (setAppsPerRow) on profile changes
        if (gridAdapter != null) {
            Method setAppsPerRow = H.find(gridAdapter, "u", int.class);
            if (setAppsPerRow != null && setAppsPerRow.getReturnType() == void.class && H.hook(setAppsPerRow, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        State st = byAdapter.get(p.thisObject);
                        if (st == null) return;
                        int cols = (Integer) p.args[0];
                        View self = st.recycler.get();
                        if (cols > 0 && cols != st.cols && self != null) regrid(st, self, cols, st.autoRows ? autoRows(st, self) : st.rows, "setAppsPerRow");
                    } catch (Throwable t) { Lx.e("Drawer: setAppsPerRow", t); }
                }
            })) hooked++; else Lx.w("Drawer: AllAppsGridAdapter#u(int) (setAppsPerRow) not hooked");
        }

        // 7. edit-mode touch handling (DrawerEdit.onTouch): every event the drawer RecyclerView sees, both while a child owns the
        //    gesture (onInterceptTouchEvent) and when nothing is under the finger (onTouchEvent). An OnItemTouchListener would
        //    not do: RecyclerView never shows it ACTION_DOWN when there is no child under the finger (where our pill is).
        XC_MethodHook touchIntercept = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                State st = byRecycler.get(p.thisObject);
                if (st == null || st.edit == null) return;
                try { if (st.edit.onTouch((MotionEvent) p.args[0], true)) p.setResult(true); } catch (Throwable t) { Lx.e("Drawer: onInterceptTouchEvent", t); }
            }
        };
        XC_MethodHook touchEvent = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                State st = byRecycler.get(p.thisObject);
                if (st == null || st.edit == null) return;
                try { if (st.edit.onTouch((MotionEvent) p.args[0], false)) p.setResult(true); } catch (Throwable t) { Lx.e("Drawer: onTouchEvent", t); }
            }
        };
        int touchHooks = 0;
        if (H.hook(H.find(aarv, "onInterceptTouchEvent", MotionEvent.class), touchIntercept)) touchHooks++; else Lx.w("Drawer: AllAppsRecyclerView.onInterceptTouchEvent not hooked (edit mode: no drag from icons)");
        if (H.hook(H.find(rv, "onTouchEvent", MotionEvent.class), touchEvent)) touchHooks++; else Lx.w("Drawer: RecyclerView.onTouchEvent not hooked (edit mode: pill taps / drags over empty space unavailable)");
        hooked += touchHooks;
        Lx.i("Drawer: edit touch hooks " + touchHooks + "/2");

        // 8. overlay feedback: page dots follow the scroll; the pill re-appears on scroll / when the drawer is drawn again after a pause
        if (H.hook(H.find(aarv, "onScrolled", int.class, int.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                State st = byRecycler.get(p.thisObject);
                if (st == null || st.edit == null) return;
                try { st.edit.onScrolled(); } catch (Throwable t) { Lx.e("Drawer: onScrolled", t); }
            }
        })) hooked++; else Lx.w("Drawer: AllAppsRecyclerView.onScrolled not hooked (page dots will not follow the scroll)");
        if (H.hook(H.find(aarv, "onDraw", android.graphics.Canvas.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                State st = byRecycler.get(p.thisObject);
                if (st == null || st.edit == null) return;
                try { st.edit.onDrawn(); } catch (Throwable t) { Lx.e("Drawer: onDraw", t); }
            }
        })) hooked++; else Lx.w("Drawer: AllAppsRecyclerView.onDraw not hooked (pill will not show on drawer open, only on touch/scroll)");

        Lx.i("Launcher: paged drawer hooks ready (" + hooked + " methods)");
    }

    /** Public, non-static declared method of c with exactly this shape, or null when zero/several match. */
    private static Method uniquePublic(Class<?> c, Class<?> ret, Class<?>... params) {
        Method hit = null; int n = 0;
        for (Method m : c.getDeclaredMethods()) {
            if (m.isBridge() || m.isSynthetic() || !Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers())) continue;
            if (m.getReturnType() != ret || !java.util.Arrays.equals(m.getParameterTypes(), params)) continue;
            hit = m; n++;
        }
        if (n == 1) { hit.setAccessible(true); return hit; }
        Lx.w("Drawer: " + n + " public candidates for " + c.getSimpleName() + " " + ret.getSimpleName() + java.util.Arrays.toString(params));
        return null;
    }

    /** The launcher's all-apps icon click / long-click listeners (BaseAllAppsAdapter fields of those exact types). */
    private static void stockListeners(State st, Object adapter) {
        try {
            for (Class<?> k = adapter.getClass(); k != null && !k.getName().startsWith("androidx.") && k != Object.class; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    if (f.getType() == View.OnClickListener.class && st.stockClick == null) { f.setAccessible(true); st.stockClick = (View.OnClickListener) f.get(adapter); }
                    else if (f.getType() == View.OnLongClickListener.class && st.stockLong == null) { f.setAccessible(true); st.stockLong = (View.OnLongClickListener) f.get(adapter); }
                }
            }
            Lx.i("Drawer: stock listeners click=" + (st.stockClick != null) + " longClick=" + (st.stockLong != null));
        } catch (Throwable t) { Lx.e("Drawer: stock listeners", t); }
    }

    /** Column count of the launcher's own grid LM (span = columns; no span factors on this firmware), with fallbacks. */
    private static int colsOf(Object stockLm, int fallback) {
        try {
            if (glmGetSpanCount != null) {
                int span = (Integer) glmGetSpanCount.invoke(stockLm);
                if (span > 0 && span <= 12) return span;
            }
        } catch (Throwable ignored) { }
        try {
            // AppsGridLayoutManager is an inner class of AllAppsGridAdapter; adapter.y() / field d = mAppsPerRow
            Object adapter = XposedHelpers.getSurroundingThis(stockLm);
            int v = (Integer) XposedHelpers.callMethod(adapter, "y");
            if (v > 0 && v <= 12) return v;
        } catch (Throwable ignored) { }
        Lx.w("Drawer: could not read column count, using " + fallback);
        return fallback;
    }

    /** The launcher's DeviceProfile (C0749h0) for this drawer: via AllAppsRecyclerView.getActivityContext() or the activity context chain. */
    private static Object deviceProfile(View rv) {
        try {
            Object ac = null;
            try { ac = XposedHelpers.callMethod(rv, "getActivityContext"); } catch (Throwable ignored) { }
            if (ac != null) { try { return XposedHelpers.callMethod(ac, "getDeviceProfile"); } catch (Throwable ignored) { } }
            Object c = rv.getContext();
            for (int i = 0; c != null && i < 6; i++) {
                try { return XposedHelpers.callMethod(c, "getDeviceProfile"); } catch (Throwable ignored) { }
                try { c = XposedHelpers.callMethod(c, "getBaseContext"); } catch (Throwable t) { c = null; }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    /**
     * Rows per page for the "Auto" choice: floor(available height / stock all-apps cell height) once the view has a size;
     * before that the launcher's own estimate DeviceProfile.z0() (= ceil of the same ratio); 6 as a last resort.
     */
    private static int autoRows(State st, View rv) {
        try {
            Object dp = deviceProfile(rv);
            int cellH = st.cellH;
            if (cellH <= 0 && dp != null) { try { cellH = XposedHelpers.getIntField(dp, "H2"); } catch (Throwable ignored) { } }   // allAppsCellHeightPx
            int availH = rv.getHeight() - rv.getPaddingTop() - bottomPad(st, rv);     // bottom = launcher's padding + our reserved strip
            if (availH > 0 && cellH > 0) return Math.max(3, Math.min(12, availH / cellH));
            if (dp != null) {
                int n = (Integer) XposedHelpers.callMethod(dp, "z0");
                if (n >= 3 && n <= 12) return n;
                Lx.w("Drawer: DeviceProfile.z0() = " + n + ", using 6 rows");
            } else Lx.w("Drawer: DeviceProfile not reachable from " + rv.getContext().getClass().getName() + ", using 6 rows");
        } catch (Throwable t) { Lx.e("Drawer: autoRows", t); }
        return 6;
    }

    /** Apply a new cols/rows to a live paged drawer: span count on our LM + full adapter refresh (posted, never inside layout). */
    private static void regrid(final State st, final View rv, int cols, int rows, String why) {
        st.cols = Math.max(1, cols);
        st.rows = Math.max(1, rows);
        st.cachedCount = -1;
        st.dirty = true;
        st.fitAttempts = 0;
        st.realignTries = 0;
        Object lm = st.ourLm == null ? null : st.ourLm.get();
        try { if (lm != null && glmSetSpanCount != null) glmSetSpanCount.invoke(lm, st.rows); }
        catch (Throwable t) { Lx.e("Drawer: setSpanCount", t); }
        Lx.i("Drawer: regrid " + st.cols + "x" + st.rows + " (" + why + ")");
        rv.post(new Runnable() {
            @Override public void run() {
                try { Object ad = st.adapter(); if (ad != null) XposedHelpers.callMethod(ad, "notifyDataSetChanged"); } catch (Throwable t) { Lx.e("Drawer: regrid notify", t); }
            }
        });
    }

    // ------------------------------------------------------------------ geometry

    /** The launcher's own right padding (ours = base + remainder is tracked in padOursR). */
    private static int basePadR(State st, View rv) {
        int cur = rv.getPaddingRight();
        if (st.padOursR < 0 || cur != st.padOursR || st.basePadR < 0) return cur;
        return st.basePadR;
    }

    /** The launcher's own bottom padding (ours = base + reserved strip is tracked in padOursB). */
    private static int basePadB(State st, View rv) {
        int cur = rv.getPaddingBottom();
        if (st.padOursB < 0 || cur != st.padOursB || st.basePadB < 0) return cur;
        return st.basePadB;
    }

    /** Pixels we reserve under the grid for the page dots / Edit pill strip (0 when neither is on); re-read once per layout pass. */
    static int reserveB(State st) {
        try { st.reserve = st.edit != null ? st.edit.reserveBottom() : 0; } catch (Throwable t) { st.reserve = 0; }
        return st.reserve;
    }

    /**
     * The launcher's bottom padding as we keep it: its own value, minus the navigation-bar height it contains when the search
     * bar sits at the bottom (the grid then ends above the bar, which itself clears the nav bar).
     */
    static int launcherPadB(int base) { return searchBottom ? Math.max(0, base - navBarRes()) : base; }

    /** Bottom padding the grid is laid out with: launcher's own + our strip (the value we set, or will set on the next layout). */
    static int bottomPad(State st, View rv) { return launcherPadB(basePadB(st, rv)) + st.reserve; }

    /** Content width available for pages, excluding our own extra right padding. */
    private static int contentWidth(State st, View rv) {
        int w = rv.getWidth() - rv.getPaddingLeft() - basePadR(st, rv);
        if (w <= 0) w = rv.getResources().getDisplayMetrics().widthPixels;
        return w;
    }

    /** Page inset actually used: the setting, capped so that at least 2/3 of the viewport stays for the cells. */
    static int pagePad(State st, View rv) { return Math.max(0, Math.min(st.pagePad, contentWidth(st, rv) / 6)); }

    static int cellWidth(State st, View rv) { return Math.max(1, (contentWidth(st, rv) - 2 * pagePad(st, rv)) / Math.max(1, st.cols)); }

    /** Page width = cols * cell width + 2 * page inset, so snapping and cell sizing never drift apart. */
    static int pageWidth(View rv, State st) {
        if (rv.getWidth() <= 0) return 0;
        return st.cols * cellWidth(st, rv) + 2 * pagePad(st, rv);
    }

    /** Pages in the drawer (the padded cell list is always whole pages). */
    static int pageCount(State st) { return Math.max(1, st.padded() / st.page()); }

    private static boolean boundsApiOk = true, layoutPosApiOk = true;

    /** Decorated left edge incl. margins of a child (RecyclerView#getDecoratedBoundsWithMargins; fallback left - margin). */
    private static int decoratedLeft(View rv, View c, android.graphics.Rect tmp) {
        if (boundsApiOk) {
            try { XposedHelpers.callMethod(rv, "getDecoratedBoundsWithMargins", c, tmp); return tmp.left; }
            catch (Throwable t) { boundsApiOk = false; Lx.w("Drawer: getDecoratedBoundsWithMargins failed (" + t + "), using left - margin"); }
        }
        ViewGroup.LayoutParams lp = c.getLayoutParams();
        return c.getLeft() - (lp instanceof ViewGroup.MarginLayoutParams ? ((ViewGroup.MarginLayoutParams) lp).leftMargin : 0);
    }

    /** Position of a child as of the last layout (matches where it is drawn even while an adapter refresh is pending). */
    private static int layoutPosOf(View rv, View child) {
        if (layoutPosApiOk) {
            try { return (Integer) XposedHelpers.callMethod(rv, "getChildLayoutPosition", child); }
            catch (Throwable t) { layoutPosApiOk = false; Lx.w("Drawer: getChildLayoutPosition failed (" + t + "), using adapter positions"); }
        }
        return glmPosOf(rv, child);
    }

    /**
     * Real horizontal scroll position in px; page n starts at exactly n * pageWidth. Taken from the laid-out child nearest to
     * the viewport start: where its column starts in the ideal page geometry minus how far it actually is from the start.
     * (computeHorizontalScrollOffset() is only an estimate here, see the class header.)
     */
    static int scrollX(View rv, State st) {
        int pageW = pageWidth(rv, st);
        if (pageW > 0 && rv instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) rv;
            int padL = rv.getPaddingLeft(), pad = pagePad(st, rv), cell = cellWidth(st, rv);
            int rows = Math.max(1, st.rows), cols = Math.max(1, st.cols);
            android.graphics.Rect tmp = new android.graphics.Rect();
            int best = Integer.MAX_VALUE, out = 0;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View c = vg.getChildAt(i);
                int pos = layoutPosOf(rv, c);
                if (pos < 0) continue;
                int start = decoratedLeft(rv, c, tmp) - padL;
                int col = pos / rows, page = col / cols, cp = col % cols;
                int x = page * pageW + (cp == 0 ? 0 : pad + cp * cell);
                int d = Math.abs(start);
                if (d < best) { best = d; out = x - start; }
            }
            if (best != Integer.MAX_VALUE) return out;
        }
        try { return (Integer) XposedHelpers.callMethod(rv, "computeHorizontalScrollOffset"); } catch (Throwable t) { return 0; }
    }

    /** Page nearest to the current scroll position. */
    static int currentPage(View rv, State st) {
        int pageW = pageWidth(rv, st);
        if (pageW <= 0) return 0;
        return Math.max(0, Math.min(pageCount(st) - 1, Math.round((float) scrollX(rv, st) / pageW)));
    }

    /** Scroll so that `page` fills the viewport exactly (smooth = animated, else instant). */
    static void scrollToPage(View rv, State st, int page, boolean smooth) {
        int pageW = pageWidth(rv, st);
        if (pageW <= 0) return;
        page = Math.max(0, Math.min(pageCount(st) - 1, page));
        int dx = page * pageW - scrollX(rv, st);
        if (dx == 0) return;
        try {
            if (smooth) { st.snapping = true; XposedHelpers.callMethod(rv, "smoothScrollBy", dx, 0); }
            else rv.scrollBy(dx, 0);
        } catch (Throwable t) { Lx.e("Drawer: scrollToPage", t); }
    }

    private static int scrollState(View rv) {
        try { return (Integer) XposedHelpers.callMethod(rv, "getScrollState"); } catch (Throwable t) { return 0; }
    }

    /**
     * Runs after every layout pass of a paged drawer:
     *  (a) first icon narrower than the intended cell -> move the difference into symmetric margins (decorated width = cell);
     *  (b) integer-division remainder -> extra right padding (+ clipToPadding) so the viewport is exactly cols * cell + 2 * pad;
     *  (c) the bottom strip for dots / pill -> extra bottom padding, then the Auto row count is re-derived from it;
     *  (d) the pill / dots overlay follows size changes.
     */
    private static void fit(final State st, final View rv) {
        if (rv.getWidth() <= 0 || rv.getHeight() <= 0) return;
        int cols = Math.max(1, st.cols);
        int curPadR = rv.getPaddingRight(), curPadB = rv.getPaddingBottom();
        if (st.padOursR < 0 || curPadR != st.padOursR) st.basePadR = curPadR;         // launcher (re)set its own padding
        if (st.padOursB < 0 || curPadB != st.padOursB) st.basePadB = curPadB;
        int w = rv.getWidth() - rv.getPaddingLeft() - st.basePadR;
        if (w <= 0) return;
        final int pad = pagePad(st, rv);
        final int inner = w - 2 * pad;
        final int cellW = Math.max(1, inner / cols), rem = inner - cellW * cols;
        final int wantPadR = st.basePadR + rem;
        final int reserve = reserveB(st);
        final int wantPadB = launcherPadB(st.basePadB) + reserve;
        if (searchBottom != st.searchBottomNow) { st.searchBottomNow = searchBottom; st.lastReserve = -1; }   // log the new strip geometry
        boolean relayout = false;
        if (!st.clipSet) {
            st.clipSet = true;
            try { ((ViewGroup) rv).setClipToPadding(true); } catch (Throwable t) { Lx.w("Drawer: setClipToPadding failed: " + t); }
        }
        if (curPadR != wantPadR || curPadB != wantPadB) {
            st.padOursR = wantPadR; st.padOursB = wantPadB;
            final int l = rv.getPaddingLeft(), t = rv.getPaddingTop();
            if (reserve != st.lastReserve) { st.lastReserve = reserve; Lx.i("Drawer: bottom strip " + reserve + "px reserved (launcher padding " + st.basePadB + " -> " + wantPadB + ")"); }
            rv.post(new Runnable() { @Override public void run() { try { rv.setPadding(l, t, wantPadR, wantPadB); } catch (Throwable e) { Lx.e("Drawer: padding", e); } } });
        } else { st.padOursR = curPadR; st.padOursB = curPadB; }
        // the reserved strip changes the height available for rows: re-derive Auto rows from the padding we are going to have
        if (st.autoRows) {
            int rows = autoRows(st, rv);
            if (rows != st.rows) { regrid(st, rv, st.cols, rows, "bottom padding " + wantPadB); return; }
        }

        // measured vs intended column width (first laid-out child; its column decides the extra page-inset margin)
        int measured = -1, decorated = -1, expected = cellW;
        View first = null;
        ViewGroup vg = (ViewGroup) rv;
        Object lm = null;
        try { lm = XposedHelpers.callMethod(rv, "getLayoutManager"); } catch (Throwable ignored) { }
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            if (c.getWidth() <= 0 || !(c.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) continue;
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) c.getLayoutParams();
            int dw = c.getWidth();
            if (lm != null) { try { dw = (Integer) XposedHelpers.callMethod(lm, "getDecoratedMeasuredWidth", c); } catch (Throwable ignored) { } }
            measured = c.getWidth();
            decorated = dw + lp.leftMargin + lp.rightMargin;
            int col = st.column(glmPosOf(rv, c));
            expected = cellW + (col == 0 ? pad : 0) + (col == cols - 1 ? pad : 0);
            first = c;
            break;
        }
        if (first != null) {
            int deficit = expected - decorated;
            if (Math.abs(deficit) > 1 && st.fitAttempts < 5) {
                st.slack = Math.max(0, Math.min(cellW / 2, st.slack + deficit));
                st.fitAttempts++;
                relayout = true;
                Lx.i("Drawer: fit viewport=" + rv.getWidth() + " pad=" + rv.getPaddingLeft() + "/" + st.basePadR + "(+" + rem + ") pagePad=" + pad + " cols=" + cols
                        + " cell=" + cellW + " measured=" + measured + " decorated=" + decorated + "/" + expected + " -> slack=" + st.slack + " (margins " + (st.slack / 2) + "/" + (st.slack - st.slack / 2) + ")");
            } else if (!st.fitLogged) {
                st.fitLogged = true;
                Lx.i("Drawer: fit ok viewport=" + rv.getWidth() + " pad=" + rv.getPaddingLeft() + "/" + st.basePadR + "(+" + rem + ") pagePad=" + pad + " cols=" + cols
                        + " cell=" + cellW + " measured=" + measured + " decorated=" + decorated + "/" + expected + " slack=" + st.slack + " page=" + (cols * cellW + 2 * pad)
                        + " padBottom=" + wantPadB + " rows=" + st.rows);
            }
        }
        if (cellW != st.lastCellW) { st.lastCellW = cellW; relayout = true; }
        if (relayout) {
            for (int i = 0; i < vg.getChildCount(); i++) { View c = vg.getChildAt(i); size(st, c, glmPosOf(rv, c)); }
            rv.post(new Runnable() { @Override public void run() { rv.requestLayout(); } });
        }
        boolean sw = Cfg.on(KEY_CUSTOM_ORDER);
        boolean fixedSw = Cfg.on(DrawerKeys.KEY_FIXED_SLOTS);
        if ((sw != st.customNow || fixedSw != st.fixedNow) && st.adapter() != null) rv.post(new Runnable() { @Override public void run() { rebuild(st); } });   // switch toggled while running
        boolean custom = sw && st.customActive;
        boolean dotsOn = Cfg.on(KEY_DOTS);
        if (st.edit != null && (rv.getWidth() != st.lastW || rv.getHeight() != st.lastH || curPadR != wantPadR || curPadB != wantPadB || custom != st.pillCustom || dotsOn != st.dotsShown)) {
            st.lastW = rv.getWidth(); st.lastH = rv.getHeight(); st.pillCustom = custom; st.dotsShown = dotsOn;
            rv.post(new Runnable() { @Override public void run() { st.edit.onLayout(); } });
        }
        // an idle drawer always rests exactly on a page: re-align after layout passes that moved the grid (rotation, regrid,
        // padding / cell-width changes keep the anchor child's pixel offset, which is mid-page in the new geometry)
        boolean stable = !relayout && curPadR == wantPadR && curPadB == wantPadB;
        if (stable && !st.snapping && (st.edit == null || !st.edit.isDragging()) && scrollState(rv) == 0) {
            int pageW = pageWidth(rv, st);
            if (pageW > 0) {
                int x = scrollX(rv, st);
                final int page = Math.max(0, Math.min(pageCount(st) - 1, Math.round((float) x / pageW)));
                int dx = page * pageW - x;
                if (Math.abs(dx) > 1 && st.realignTries < 3) {
                    st.realignTries++;
                    Lx.i("Drawer: re-align " + dx + "px to page " + page + " after layout");
                    rv.post(new Runnable() { @Override public void run() { if (scrollState(rv) == 0 && !st.snapping) scrollToPage(rv, st, page, false); } });
                } else if (Math.abs(dx) <= 1) st.realignTries = 0;
            }
        }
    }

    /** GLM (RecyclerView-side) position of a child, -1 when unknown. */
    static int glmPosOf(View rv, View child) {
        try { return (Integer) XposedHelpers.callMethod(rv, "getChildAdapterPosition", child); } catch (Throwable t) { return -1; }
    }

    /** Hook getItemCount / getItemViewType / onBindViewHolder on the concrete adapter class (once per class). */
    private static synchronized void hookAdapterClass(Class<?> adapterCls, Class<?> vhClass) {
        if (hookedAdapters.contains(adapterCls)) return;
        hookedAdapters.add(adapterCls);
        Method count = H.find(adapterCls, "getItemCount");
        Method type = H.find(adapterCls, "getItemViewType", int.class);
        // onBindViewHolder(VH, int) is R8-renamed to o(O$b, int) on this firmware (the kept name is only a bridge).
        Method bind = null;
        for (Class<?> k = adapterCls; k != null && bind == null; k = k.getSuperclass()) {
            Method best = null; int score = -1;
            for (Method m : k.getDeclaredMethods()) {
                Class<?>[] pt = m.getParameterTypes();
                if (m.isBridge() || m.isSynthetic() || pt.length != 2 || pt[1] != int.class || m.getReturnType() != void.class) continue;
                if (!vhClass.isAssignableFrom(pt[0])) continue;
                int sc = m.getName().equals("onBindViewHolder") ? 3 : (!Modifier.isPrivate(m.getModifiers()) && m.getName().length() <= 2 ? 2 : 0);
                if (sc > score) { score = sc; best = m; }
            }
            if (best != null && score > 0) { best.setAccessible(true); bind = best; }
        }
        if (origItemType == null) origItemType = type;
        if (origCount == null) origCount = count;
        Lx.i("Drawer: adapter " + adapterCls.getName() + " methods: getItemCount=" + (count == null ? "MISSING" : count.getDeclaringClass().getSimpleName() + "#" + count.getName())
                + " getItemViewType=" + (type == null ? "MISSING" : type.getDeclaringClass().getSimpleName() + "#" + type.getName())
                + " bind=" + (bind == null ? "MISSING" : bind.getDeclaringClass().getSimpleName() + "#" + bind.getName()));
        if (count == null || type == null || bind == null) { Lx.w("Drawer: adapter methods not found on " + adapterCls.getName() + ", paging inactive"); return; }

        H.hook(count, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                State st = byAdapter.get(p.thisObject);
                if (st == null || !Cfg.on(Features.LAUNCHER_PAGED)) return;
                try {
                    refresh(st, p.thisObject, (Integer) p.getResult());
                    p.setResult(st.padded());
                } catch (Throwable t) { Lx.e("Drawer: getItemCount", t); }
            }
        });
        H.hook(type, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                State st = byAdapter.get(p.thisObject);
                if (st == null || !Cfg.on(Features.LAUNCHER_PAGED)) return;
                int pos = (Integer) p.args[0];
                int v = st.visual(pos);
                if (!st.hasCell(v)) { p.setResult(2); return; }   // padding cell / empty slot -> icon type, hidden at bind
                p.args[0] = st.iconPositions[v];
            }
        });
        H.hook(bind, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                State st = byAdapter.get(p.thisObject);
                if (st == null || !Cfg.on(Features.LAUNCHER_PAGED)) return;
                try {
                    int pos = (Integer) p.args[1];
                    p.setObjectExtra("rm_glm", pos);
                    View item = (View) XposedHelpers.getObjectField(p.args[0], "itemView");
                    ViewGroup.LayoutParams lp = item.getLayoutParams();
                    if (st.cellH <= 0 && lp != null && lp.height > 0) st.cellH = lp.height;   // stock cell height from a fresh holder
                    int v = st.visual(pos);
                    if (!st.hasCell(v)) {                                                     // padding cell / empty slot: blank, skip the real bind
                        item.setVisibility(View.INVISIBLE);
                        size(st, item, pos);
                        p.setObjectExtra("rm_pad", Boolean.TRUE);
                        p.setResult(null);
                        return;
                    }
                    p.setObjectExtra("rm_visual", v);
                    p.args[1] = st.iconPositions[v];
                } catch (Throwable t) { Lx.e("Drawer: bind", t); }
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                State st = byAdapter.get(p.thisObject);
                if (st == null || !Cfg.on(Features.LAUNCHER_PAGED)) return;
                try {
                    View item = (View) XposedHelpers.getObjectField(p.args[0], "itemView");
                    Object g = p.getObjectExtra("rm_glm");
                    size(st, item, g instanceof Integer ? (Integer) g : -1);                   // after the original: it may reset lp.height/width
                    if (p.getObjectExtra("rm_pad") != null) return;
                    if (st.iconSize <= 0) { try { st.iconSize = (Integer) XposedHelpers.callMethod(item, "getIconSize"); } catch (Throwable ignored) { } }
                    Object vo = p.getObjectExtra("rm_visual");
                    int v = vo instanceof Integer ? (Integer) vo : -1;
                    DrawerOrder.Cell cell = v >= 0 && v < st.cells.size() ? st.cells.get(v) : null;
                    if (st.edit != null && cell != null) {
                        if (cell.isFolder()) st.edit.applyFolderLook(item, cell.folder);
                        else st.edit.applyAppLook(item, cell.app);
                    }
                    if (!st.loggedBind) {
                        st.loggedBind = true;
                        ViewGroup.LayoutParams lp = item.getLayoutParams();
                        Lx.i("Drawer: first bind item=" + item.getClass().getSimpleName() + " cell=" + (lp == null ? "?" : lp.width + "x" + lp.height)
                                + " stockCellH=" + st.cellH + " iconSize=" + st.iconSize + " grid=" + st.cols + "x" + st.rows + " cells=" + st.cells.size()
                                + " custom=" + st.customNow);
                    }
                } catch (Throwable t) { Lx.e("Drawer: bind(after)", t); }
            }
        });
        Lx.i("Drawer: adapter " + adapterCls.getSimpleName() + " hooked (bind=" + bind.getName() + ")");
    }

    /**
     * Cell = pageWidth/cols wide (the icon layout is match_parent, i.e. page-wide in a horizontal list) and one row-slot tall.
     * When the icon measures narrower than the cell (see header) the difference goes into symmetric margins.
     */
    private static void size(State st, View item, int glmPos) {
        View rv = st.recycler.get();
        ViewGroup.LayoutParams lp = item.getLayoutParams();
        if (rv == null || lp == null) return;
        int cellW = cellWidth(st, rv);
        int slack = Math.max(0, Math.min(st.slack, cellW / 2));
        lp.width = Math.max(1, cellW - slack);
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
            int l = slack / 2, r = slack - l;
            int pad = pagePad(st, rv);
            if (pad > 0 && glmPos >= 0) {                       // page inset: first column +pad left, last column +pad right
                int col = st.column(glmPos);
                if (col == 0) l += pad;
                if (col == st.cols - 1) r += pad;
            }
            if (mlp.leftMargin != l || mlp.rightMargin != r) { mlp.leftMargin = l; mlp.rightMargin = r; }
        }
        int h = rv.getHeight() - rv.getPaddingTop() - bottomPad(st, rv);
        if (h > 0) {
            int slot = h / Math.max(1, st.rows);
            if (slot >= st.cellH && slot > 0) lp.height = slot;   // spread rows evenly, never shrink below the stock cell
        }
    }

    /** Re-resolve the cell list from the current adapter contents and refresh the RecyclerView (used by DrawerEdit). */
    static void rebuild(final State st) {
        final Object ad = st.adapter();
        final View rv = st.recycler.get();
        if (ad == null) return;
        Runnable r = new Runnable() {
            @Override public void run() {
                try {
                    st.dirty = true;
                    if (origCount != null) refresh(st, ad, (Integer) XposedBridge.invokeOriginalMethod(origCount, ad, new Object[0]));
                    XposedHelpers.callMethod(ad, "notifyDataSetChanged");
                } catch (Throwable t) { Lx.e("Drawer: rebuild", t); }
            }
        };
        boolean computing = false;
        try { if (rv != null) computing = (Boolean) XposedHelpers.callMethod(rv, "isComputingLayout"); } catch (Throwable ignored) { }
        if (computing && rv != null) rv.post(r); else r.run();
    }

    /** Rebuild the visual cell list when the underlying list (or the custom-order switch) changed. */
    private static void refresh(State st, Object adapter, int realCount) {
        boolean custom = Cfg.on(KEY_CUSTOM_ORDER);
        boolean fixed = Cfg.on(DrawerKeys.KEY_FIXED_SLOTS);
        if (!st.dirty && st.cachedCount == realCount && custom == st.customNow && fixed == st.fixedNow) return;
        try {
            View rv = st.recycler.get();
            Context ctx = rv != null ? rv.getContext() : Lx.context();
            List<?> items = DrawerApps.itemsOf(adapter, realCount);
            ArrayList<String> keys = new ArrayList<>(realCount);
            HashMap<String, Integer> posByKey = new HashMap<>();
            st.infoByKey.clear();
            int nIcons = 0;
            for (int i = 0; i < realCount; i++) {
                int t = (Integer) XposedBridge.invokeOriginalMethod(origItemType, adapter, new Object[]{i});
                if (t != 2) continue;
                nIcons++;
                Object info = items != null && i < items.size() ? DrawerApps.appInfoOf(items.get(i)) : null;
                String key = info != null && ctx != null ? DrawerApps.keyOf(ctx, info) : null;
                if (key == null || posByKey.containsKey(key)) key = "pos:" + i;
                keys.add(key);
                posByKey.put(key, i);
                if (info != null) st.infoByKey.put(key, info);
            }
            boolean useCustom = custom && items != null && st.order != null;
            List<DrawerOrder.Cell> cells;
            if (st.order != null) cells = st.order.resolve(keys, useCustom, fixed, st.page());
            else { cells = new ArrayList<>(keys.size()); for (String k : keys) cells.add(new DrawerOrder.Cell(k)); }
            // visual index == slot index: a gap (null) or a stale cell becomes an invisible slot (pos -1), never shifts the rest
            ArrayList<DrawerOrder.Cell> shown = new ArrayList<>(cells.size());
            int[] pos = new int[cells.size()];
            int[] byPos = new int[realCount];
            java.util.Arrays.fill(byPos, -1);
            int n = 0, folders = 0, gaps = 0;
            for (DrawerOrder.Cell c : cells) {
                Integer p = c == null ? null : posByKey.get(c.anchorKey());
                if (p == null) { pos[n] = -1; shown.add(null); n++; gaps++; continue; }   // empty slot (or stale cell: dropped by the next resolve)
                pos[n] = p; byPos[p] = n; shown.add(c); n++;
                if (c.isFolder()) folders++;
            }
            while (n > 0 && pos[n - 1] < 0 && !(useCustom && st.edit != null && st.edit.editing)) { n--; gaps--; shown.remove(n); }   // trailing gaps only matter while editing
            int[] out = new int[n];
            System.arraycopy(pos, 0, out, 0, n);
            boolean changed = out.length != st.iconPositions.length || st.cachedCount != realCount || custom != st.customNow || fixed != st.fixedNow;
            st.iconPositions = out;
            st.visualByPos = byPos;
            st.cells = shown;
            st.cachedCount = realCount;
            st.customNow = custom;
            st.fixedNow = fixed;
            st.customActive = useCustom;
            st.dirty = false;
            if (changed) {
                Lx.i("Drawer: " + nIcons + " icons of " + realCount + " items -> " + (n - gaps) + " cells + " + gaps + " empty slots (" + folders + " folders, "
                        + (st.order == null ? 0 : st.order.hiddenCount()) + " hidden, custom=" + useCustom + (useCustom ? fixed ? ", fixed slots" : ", compact" : "")
                        + ") = " + ((n + st.page() - 1) / st.page()) + " pages of " + st.cols + "x" + st.rows);
                if (st.edit != null && rv != null) rv.post(new Runnable() { @Override public void run() { st.edit.onPagesChanged(); } });
            }
        } catch (Throwable t) { Lx.e("Drawer: refresh", t); }
    }
}
