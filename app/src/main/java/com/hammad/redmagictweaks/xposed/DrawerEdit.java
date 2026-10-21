package com.hammad.redmagictweaks.xposed;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.view.Window;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XposedHelpers;

/**
 * One UI-style drawer editing on top of the paged drawer, implemented entirely with our own views:
 * <ul>
 *   <li>a bottom strip reserved under the grid ({@link #reserveBottom()}, added to the RecyclerView's bottom padding by
 *       LauncherHooks before the Auto row count is derived) that holds the page dots and the pill ("Edit" / "Done" +
 *       "Hidden"), both drawn in the RecyclerView's overlay and hit-tested by us. The pill takes the dots' place while
 *       it is visible. Gestures: tap the strip = toggle the pill (it then stays until Done or a tap elsewhere); tap empty
 *       drawer space = show the pill for 3 s (or hide it when it is up); long-press empty drawer space = edit mode;
 *       the pill also shows for 3 s when the drawer opens while the dots are off;</li>
 *   <li>touch handling fed by LauncherHooks' hooks on AllAppsRecyclerView#onInterceptTouchEvent and
 *       RecyclerView#onTouchEvent (an OnItemTouchListener would never see ACTION_DOWN when no child is under the finger,
 *       which is exactly where the pill lives). In edit mode a DOWN on a cell only remembers the cell and disables the
 *       parents' intercept (DragLayer swipe-to-close), it is NOT claimed: horizontal swipes still reach the RecyclerView
 *       and turn pages, taps still reach the icon. A drag starts on (a) the launcher's own long-press of the cell (our
 *       OnLongClickListener, set only in edit mode) or (b) a mostly vertical move past touch slop. From then on every
 *       event is consumed (the child gets ACTION_CANCEL from ViewGroup): bitmap shadow in the overlay, hover over a
 *       cell's edge ~300 ms = reorder, hover over an app's centre ~250 ms = folder target (target grows), hover at the
 *       left/right edge = flip page, drop on the pill = hide app / remove folder. In fixed-slot mode (DrawerKeys.KEY_FIXED_SLOTS)
 *       cells keep their grid slot: hovering an empty slot (~120 ms) moves the dragged cell there and leaves a gap behind,
 *       hovering an occupied cell pushes that page's cells towards its next gap, and holding at the right edge of the last
 *       page adds a new empty page (dropped again when editing ends if it stays empty);</li>
 *   <li>folder cells: the launcher's own BubbleTextView of the folder's first app, re-skinned with a composite icon +
 *       folder name and our click/long-click listeners (the stock ones are restored when the view is rebound to an app);</li>
 *   <li>a Dialog for open folders (editable name, 4-column icon grid, long-press an app to move it out) and PopupMenus.</li>
 * </ul>
 * Outside edit mode the launcher's own long-press (shortcut popup / drag to home) is untouched: the stock listeners stay
 * on app cells and we consume nothing but pill taps.
 * The androidx ItemTouchHelper that ships in the launcher cannot be used: its Callback is an abstract class in the
 * host's classloader (androidx.recyclerview.widget.h$e), which a module cannot subclass without generating dex.
 */
final class DrawerEdit {
    private static final long HOVER_FOLDER_MS = 260, HOVER_MOVE_MS = 320, HOVER_EMPTY_MS = 120, EDGE_FLIP_MS = 520, EDGE_REPEAT_MS = 900;
    private static final int ZONE_NONE = 0, ZONE_EDGE = 1, ZONE_CENTER = 2, ZONE_EMPTY = 3;
    /** Height of the bottom strip: dots only / with the pill (the pill is ~36 dp tall and replaces the dots while shown). */
    private static final int STRIP_DOTS_DP = 32, STRIP_PILL_DP = 44;

    private LauncherHooks.State st;
    private final WeakReference<View> rvRef;
    private final Context ctx;
    private final float density;
    private final int slop;

    boolean editing;
    /** Cell key currently being dragged (app key or "folder:<id>"); that cell is drawn with alpha 0. */
    String dragKey;

    // pill overlay
    private LinearLayout pill;
    private TextView pillMain, pillHidden;
    private boolean pillAttached;
    private int pressedPill = -1;

    // press / drag
    private View pressedChild;
    private int pressVisual = -1;
    private float downX, downY, lastX, lastY;
    private boolean dragging;
    /** An icon is being dragged (LauncherHooks must not re-align the pages under the finger). */
    boolean isDragging() { return dragging; }
    private int curVisual = -1;
    private Drawable shadow;
    private int shadowW, shadowH;
    private int hoverVisual = -1, hoverZone = ZONE_NONE, folderTarget = -1, edgeZone = 0;
    private WeakReference<View> highlighted;
    private static final int MODE_STOCK = 0, MODE_FOLDER = 1, MODE_EDIT = 2;
    private final Map<View, Integer> styled = new WeakHashMap<>();      // which listeners a cell view currently carries
    private int dragStartVisual = -1;
    private boolean overPill, loggedStockLongMissing;
    private long seenDownTime = -1;
    /** DOWN landed in the bottom strip (dots / pill area) / on empty drawer space; the empty long-press already fired. */
    private boolean stripDown, emptyLongFired;
    /** Long-press on empty drawer space (no icon under the finger) -> edit mode. */
    private final Runnable emptyLongPress = new Runnable() {
        @Override public void run() {
            View rv = rv();
            if (rv == null || dragging || pressedChild != null || pressedPill >= 0 || !customOn()) return;
            emptyLongFired = true;
            if (editing) return;
            Lx.i("Drawer: long-press on empty space -> edit mode");
            try { rv.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); } catch (Throwable ignored) { }
            setEditing(true);
        }
    };
    /** Our own long-press timer for presses the BubbleTextView refused (its onTouchEvent returns false outside the icon bounds). */
    private final Runnable longPressFallback = new Runnable() {
        @Override public void run() {
            View rv = rv();
            if (rv == null || !editing || dragging || pressedChild == null) return;
            View child = pressedChild; int v = pressVisual;
            pressedChild = null;
            Lx.i("Drawer: long-press (timer) on cell " + v + " -> drag start");
            startDrag(rv, child, v, lastX, lastY);
        }
    };
    private final Map<View, Boolean> dimmed = new WeakHashMap<>();
    private final Map<String, Bitmap> folderIconCache = new HashMap<>();
    private Dialog openDialog;

    private static Method btvSetIcon; private static java.lang.reflect.Constructor<?> fbdCtor; private static boolean iconApiResolved;

    private final Runnable hoverFire = new Runnable() { @Override public void run() { onHoverFire(); } };
    private final Runnable edgeFlip = new Runnable() { @Override public void run() { onEdgeFlip(); } };

    DrawerEdit(LauncherHooks.State st, View rv) {
        this.st = st;
        this.rvRef = new WeakReference<>(rv);
        this.ctx = rv.getContext();
        this.density = rv.getResources().getDisplayMetrics().density;
        this.slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
    }

    /** Long-press on an app cell in edit mode (installed by applyAppLook) -> drag. */
    private final View.OnLongClickListener editLong = new View.OnLongClickListener() {
        @Override public boolean onLongClick(View v) { return startDragFromLongPress(v); }
    };

    private View rv() { return rvRef.get(); }
    private int dp(float v) { return Math.round(v * density); }
    boolean customOn() { return Cfg.on(LauncherHooks.KEY_CUSTOM_ORDER) && st.customActive; }

    /** The launcher re-installed a layout manager on the same drawer: follow the new state. */
    void rebind(LauncherHooks.State s) {
        View rv = rv();
        if (rv != null && dragging) endDrag(rv, false);
        resetPress();
        st = s;
    }

    // ------------------------------------------------------------------ touch (fed by the hooks in LauncherHooks)

    /**
     * Every MotionEvent the drawer RecyclerView sees, from AllAppsRecyclerView#onInterceptTouchEvent ({@code intercept})
     * and RecyclerView#onTouchEvent. Returns true to consume it (the hooked method is then skipped and returns true).
     */
    boolean onTouch(MotionEvent ev, boolean intercept) {
        View rv = rv();
        if (rv == null) return false;
        int action = ev.getActionMasked();
        float x = ev.getX(), y = ev.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                if (ev.getDownTime() == seenDownTime) {
                    // second delivery of the same DOWN: no child accepted it (BubbleTextView refuses touches outside the icon
                    // bounds), the RecyclerView itself is now the target -> the icon's long-press will never fire, use ours
                    if (editing && pressedChild != null && !dragging) { rv.removeCallbacks(longPressFallback); rv.postDelayed(longPressFallback, ViewConfiguration.getLongPressTimeout()); }
                    return pressedPill >= 0;
                }
                seenDownTime = ev.getDownTime();
                resetPress();
                lastX = downX = x; lastY = downY = y;
                if (!customOn()) { if (editing) setEditing(false); return false; }
                int hit = pillHit(x, y);
                if (hit >= 0) { pressedPill = hit; showPill(false); return true; }  // the pill is ours (overlay, not a child)
                View child = childUnder(rv, x, y);
                stripDown = child == null && inStrip(rv, y);                        // tap on the dots strip toggles the pill
                emptyDown = child == null;                                          // tap on empty drawer space shows / hides the pill
                if (pillShown && !pillSticky) showPill(true);                       // any touch restarts the auto-hide timer
                if (child == null) { rv.removeCallbacks(emptyLongPress); rv.postDelayed(emptyLongPress, ViewConfiguration.getLongPressTimeout()); }
                if (!editing) return false;
                int v = child == null ? -1 : visualOf(rv, child);
                if (!st.hasCell(v)) return false;                                  // blank cell / empty slot / empty area: plain paging
                pressedChild = child; pressVisual = v;
                DrawerOrder.Cell c = cellAt(v);
                Lx.i("Drawer: edit down on cell " + v + " (" + (c == null ? "?" : c.isFolder() ? "folder " + c.folder.name : c.app) + ")");
                disallowParents(rv);                                                // keep the DragLayer's swipe-to-close out of this gesture
                return false;                                                       // not claimed: horizontal swipes still page, taps still click
            }
            case MotionEvent.ACTION_MOVE: {
                lastX = x; lastY = y;
                if (dragging) { updateDrag(rv, x, y); return true; }
                if (pressedPill >= 0) return true;
                if (emptyDown && Math.hypot(x - downX, y - downY) > slop) { rv.removeCallbacks(emptyLongPress); emptyDown = false; stripDown = false; }
                if (editing && pressedChild != null) {
                    float dx = x - downX, dy = y - downY;
                    if (Math.hypot(dx, dy) > slop) {
                        rv.removeCallbacks(longPressFallback);
                        if (Math.abs(dy) > Math.abs(dx) * 1.3f) {                    // (b) mostly vertical pull: drag without waiting
                            View child = pressedChild; int v = pressVisual;
                            pressedChild = null;
                            Lx.i("Drawer: vertical move on cell " + v + " -> drag start");
                            startDrag(rv, child, v, x, y);
                            return true;
                        }
                        pressedChild = null;                                        // horizontal: it is a page swipe, let it through
                    }
                }
                return false;
            }
            case MotionEvent.ACTION_UP: {
                lastX = x; lastY = y;
                boolean consumed = false;
                if (pressedPill >= 0) { int hit = pillHit(x, y); if (hit == pressedPill) pillAction(hit); else showPill(!pillSticky); consumed = true; }
                else if (dragging) { endDrag(rv, true); consumed = true; }
                else if (emptyLongFired) consumed = true;                           // the long-press already acted: swallow the tap
                else if (emptyDown && Math.hypot(x - downX, y - downY) <= slop) {
                    if (stripDown) {                                                // dots strip: toggle the (sticky) pill
                        if (pillShown && !editing) { Lx.i("Drawer: strip tap -> pill hidden"); hidePill(true); }
                        else if (!pillShown) { Lx.i("Drawer: strip tap -> pill shown"); pillSticky = true; showPill(false); }
                        consumed = true;
                    } else if (pillShown && !editing) hidePill(true);              // tapped away
                    else if (!pillShown) showPill(true);                            // empty space: brief hint, auto-hides
                }
                resetPress();
                return consumed;
            }
            case MotionEvent.ACTION_CANCEL: {
                boolean consumed = dragging || pressedPill >= 0;
                if (dragging) endDrag(rv, false);
                resetPress();
                return consumed;
            }
            default:
                return dragging || pressedPill >= 0;
        }
    }

    private void resetPress() {
        View rv = rv();
        if (rv != null) { rv.removeCallbacks(hoverFire); rv.removeCallbacks(edgeFlip); rv.removeCallbacks(longPressFallback); rv.removeCallbacks(emptyLongPress); }
        pressedPill = -1; pressedChild = null; pressVisual = -1; dragging = false; emptyDown = false; stripDown = false; emptyLongFired = false;
    }

    /** True when y (RecyclerView-local) is inside the bottom strip reserved for dots / pill (whole bottom padding). */
    private boolean inStrip(View rv, float y) {
        if (reserveBottom() <= 0) return false;
        int top = rv.getHeight() - rv.getPaddingBottom();
        return y >= top - dp(4);
    }

    /** Pixels LauncherHooks adds to the drawer's bottom padding for our strip (dots and/or pill); 0 when neither is on. */
    int reserveBottom() {
        boolean dots = Cfg.on(LauncherHooks.KEY_DOTS), custom = Cfg.on(LauncherHooks.KEY_CUSTOM_ORDER);
        if (!dots && !custom) return 0;
        return dp(custom ? STRIP_PILL_DP : STRIP_DOTS_DP);
    }

    private static void disallowParents(View rv) {
        try { if (rv.getParent() != null) rv.getParent().requestDisallowInterceptTouchEvent(true); } catch (Throwable ignored) { }
    }

    private boolean loggedChildApi;
    private View childUnder(View rv, float x, float y) {
        try { Object c = XposedHelpers.callMethod(rv, "findChildViewUnder", x, y); return c instanceof View ? (View) c : null; }
        catch (Throwable t) { if (!loggedChildApi) { loggedChildApi = true; Lx.w("Drawer: RecyclerView.findChildViewUnder failed: " + t); } return null; }
    }

    private int visualOf(View rv, View child) {
        try {
            int pos = (Integer) XposedHelpers.callMethod(rv, "getChildAdapterPosition", child);
            if (pos < 0) return -1;
            return st.visual(pos);
        } catch (Throwable t) { if (!loggedChildApi) { loggedChildApi = true; Lx.w("Drawer: RecyclerView.getChildAdapterPosition failed: " + t); } return -1; }
    }

    private View viewForVisual(View rv, int v) {
        try {
            if (v < 0 || v >= st.iconPositions.length) return null;
            Object vh = XposedHelpers.callMethod(rv, "findViewHolderForAdapterPosition", st.glmPos(v));   // RecyclerView-side position, not the launcher's
            return vh == null ? null : (View) XposedHelpers.getObjectField(vh, "itemView");
        } catch (Throwable t) { return null; }
    }

    // ------------------------------------------------------------------ long press -> drag

    /** (a) the launcher's own long-press detection fired on a cell while editing: start the drag at the last finger position. */
    private boolean startDragFromLongPress(View v) {
        View rv = rv();
        if (rv == null || !editing || dragging) return editing;
        int vis = visualOf(rv, v);
        if (vis < 0 || vis >= st.cells.size()) { Lx.w("Drawer: long-press on unknown cell (visual " + vis + ")"); return true; }
        Lx.i("Drawer: long-press on cell " + vis + " -> drag start");
        pressedChild = null;
        startDrag(rv, v, vis, lastX, lastY);
        return true;
    }

    private DrawerOrder.Cell cellAt(int v) {
        List<DrawerOrder.Cell> cells = st.cells;
        return v >= 0 && v < cells.size() ? cells.get(v) : null;
    }

    private void showCellMenu(final View anchor, final DrawerOrder.Cell c) {
        if (c == null) return;
        try {
            PopupMenu pm = new PopupMenu(ctx, anchor);
            if (c.isFolder()) {
                pm.getMenu().add(0, 1, 0, "Open folder");
                pm.getMenu().add(0, 2, 1, "Rename folder");
                pm.getMenu().add(0, 6, 2, "Folder colour…");
                pm.getMenu().add(0, 7, 3, "Folder shape…");
                pm.getMenu().add(0, 3, 4, "Remove folder (keep apps)");
            } else {
                pm.getMenu().add(0, 4, 0, "Hide from drawer");
            }
            if (!editing) pm.getMenu().add(0, 5, 5, "Edit drawer layout");
            pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override public boolean onMenuItemClick(android.view.MenuItem item) {
                    try {
                        switch (item.getItemId()) {
                            case 1: openFolder(c.folder, anchor); break;
                            case 2: renameDialog(c.folder); break;
                            case 3: st.order.dissolve(c.folder); LauncherHooks.rebuild(st); break;
                            case 4: st.order.hide(c.app); LauncherHooks.rebuild(st); updatePill(); toast("Hidden. Use Edit > Hidden to bring it back."); break;
                            case 5: setEditing(true); break;
                            case 6: pickStyle(c.folder, true); break;
                            case 7: pickStyle(c.folder, false); break;
                            default: break;
                        }
                    } catch (Throwable t) { Lx.e("Drawer: menu", t); }
                    return true;
                }
            });
            pm.show();
        } catch (Throwable t) { Lx.e("Drawer: cell menu", t); }
    }

    // ------------------------------------------------------------------ drag

    private void startDrag(View rv, View child, int v, float x, float y) {
        DrawerOrder.Cell c = cellAt(v);
        if (c == null) return;
        try {
            int w = Math.max(1, child.getWidth()), h = Math.max(1, child.getHeight());
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            child.draw(new Canvas(b));
            shadow = new BitmapDrawable(rv.getResources(), b);
            shadowW = Math.round(w * 1.08f); shadowH = Math.round(h * 1.08f);
            shadow.setAlpha(230);
            rv.getOverlay().add(shadow);
        } catch (Throwable t) { Lx.e("Drawer: drag shadow", t); shadow = null; }
        dragKey = c.isFolder() ? "folder:" + c.folder.id : c.app;
        curVisual = v; dragStartVisual = v;
        dragging = true; overPill = false;
        hoverVisual = -1; hoverZone = ZONE_NONE; folderTarget = -1; edgeZone = 0;
        child.setPressed(false);
        child.setAlpha(0f);
        dimmed.put(child, Boolean.TRUE);
        disallowParents(rv);
        try { XposedHelpers.callMethod(rv, "stopScroll"); } catch (Throwable ignored) { }
        try { child.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); } catch (Throwable ignored) { }
        updatePill();
        showPill(false);
        updateDrag(rv, x, y);
    }

    private void updateDrag(View rv, float x, float y) {
        if (shadow != null) {
            int l = Math.round(x - shadowW / 2f), t = Math.round(y - shadowH / 2f);
            shadow.setBounds(l, t, l + shadowW, t + shadowH);
            rv.invalidate();
        }
        // the pill is a drop target ("hide" / "remove folder") while dragging
        boolean onPill = pillHit(x, y) >= 0;
        if (onPill != overPill) { overPill = onPill; updatePill(); }
        if (onPill) { setHover(rv, -1, ZONE_NONE); rv.removeCallbacks(edgeFlip); edgeZone = 0; return; }
        // page flip at the viewport edges
        int w = rv.getWidth();
        int edgeW = Math.max(dp(28), w / 14);
        int zone = x < rv.getPaddingLeft() + edgeW ? -1 : x > w - rv.getPaddingRight() - edgeW ? 1 : 0;
        if (zone != edgeZone) {
            edgeZone = zone;
            rv.removeCallbacks(edgeFlip);
            if (zone != 0) rv.postDelayed(edgeFlip, EDGE_FLIP_MS);
        }
        if (zone != 0) { setHover(rv, -1, ZONE_NONE); return; }
        // hover target
        View child = childUnder(rv, x, y);
        if (child == null) { setHover(rv, -1, ZONE_NONE); return; }
        int v = visualOf(rv, child);
        if (v < 0) { setHover(rv, -1, ZONE_NONE); return; }
        int n = st.cells.size();
        int z;
        DrawerOrder.Cell dragged = cellAt(curVisual), target = cellAt(v);
        if (fixedSlots()) {
            if (target == null) z = ZONE_EMPTY;                                     // empty slot (gap or padding): take it
            else {
                boolean canFold = dragged != null && !dragged.isFolder();
                float cx = child.getLeft() + child.getWidth() / 2f, cy = child.getTop() + child.getHeight() / 2f;
                boolean center = Math.abs(x - cx) < child.getWidth() * 0.30f && Math.abs(y - cy) < child.getHeight() * 0.36f;
                z = canFold && center ? ZONE_CENTER : ZONE_EDGE;                     // edge of an occupied cell: push that page's cells
            }
        } else if (v >= n) { v = n - 1; z = ZONE_EDGE; }                            // compact: blank cell at the end -> move to last
        else {
            boolean canFold = dragged != null && !dragged.isFolder() && target != null;
            float cx = child.getLeft() + child.getWidth() / 2f, cy = child.getTop() + child.getHeight() / 2f;
            boolean center = Math.abs(x - cx) < child.getWidth() * 0.30f && Math.abs(y - cy) < child.getHeight() * 0.36f;
            z = canFold && center ? ZONE_CENTER : ZONE_EDGE;
        }
        if (v == curVisual) { setHover(rv, -1, ZONE_NONE); return; }
        setHover(rv, v, z);
    }

    private boolean fixedSlots() { return st.order != null && st.order.isFixed(); }

    private void setHover(View rv, int v, int zone) {
        if (v == hoverVisual && zone == hoverZone) return;
        rv.removeCallbacks(hoverFire);
        if (folderTarget >= 0 && (v != folderTarget || zone != ZONE_CENTER)) { unhighlight(); folderTarget = -1; }
        hoverVisual = v; hoverZone = zone;
        if (v >= 0 && zone != ZONE_NONE) rv.postDelayed(hoverFire, zone == ZONE_CENTER ? HOVER_FOLDER_MS : zone == ZONE_EMPTY ? HOVER_EMPTY_MS : HOVER_MOVE_MS);
    }

    private void onHoverFire() {
        View rv = rv();
        if (rv == null || !dragging || hoverVisual < 0) return;
        try {
            if (hoverZone == ZONE_CENTER) {
                folderTarget = hoverVisual;
                View target = viewForVisual(rv, hoverVisual);
                if (target != null) { highlighted = new WeakReference<>(target); target.animate().scaleX(1.18f).scaleY(1.18f).setDuration(120).start(); }
            } else if (hoverZone == ZONE_EDGE || hoverZone == ZONE_EMPTY) {
                int to = hoverVisual;
                if (st.order.move(curVisual, to)) {
                    curVisual = to;
                    LauncherHooks.rebuild(st);
                } else if (fixedSlots() && hoverZone == ZONE_EDGE) Lx.i("Drawer: page full, cannot push cells to make room at slot " + to);
                hoverVisual = -1; hoverZone = ZONE_NONE;
            }
        } catch (Throwable t) { Lx.e("Drawer: hover", t); }
    }

    private void onEdgeFlip() {
        View rv = rv();
        if (rv == null || !dragging || edgeZone == 0) return;
        try {
            int pageW = LauncherHooks.pageWidth(rv, st);
            if (pageW > 0) {
                int offset = LauncherHooks.scrollX(rv, st);                         // real px (GridLayoutManager's offset is an estimate)
                int maxPage = LauncherHooks.pageCount(st) - 1;
                int page = Math.round((float) offset / pageW) + edgeZone;
                if (page > maxPage && edgeZone > 0 && editing && fixedSlots() && st.order.lastPageUsed()) {
                    // fixed slots: holding at the right edge of the last page adds a new empty page and moves onto it
                    final int np = st.order.addPage();
                    if (np >= 0) {
                        LauncherHooks.rebuild(st);
                        rv.post(new Runnable() { @Override public void run() { LauncherHooks.scrollToPage(rv, st, np, true); } });
                        toast("New page added");
                        Lx.i("Drawer: drag past the last page -> new page " + np);
                    }
                    setHover(rv, -1, ZONE_NONE);
                    rv.postDelayed(edgeFlip, EDGE_REPEAT_MS);
                    return;
                }
                page = Math.max(0, Math.min(maxPage, page));
                int dx = page * pageW - offset;
                if (dx != 0) { st.snapping = true; XposedHelpers.callMethod(rv, "smoothScrollBy", dx, 0); Lx.i("Drawer: page flip -> " + page); }
            }
            setHover(rv, -1, ZONE_NONE);
            rv.postDelayed(edgeFlip, EDGE_REPEAT_MS);
        } catch (Throwable t) { Lx.e("Drawer: edge flip", t); }
    }

    private void endDrag(View rv, boolean drop) {
        rv.removeCallbacks(hoverFire); rv.removeCallbacks(edgeFlip);
        try { if (shadow != null) rv.getOverlay().remove(shadow); } catch (Throwable ignored) { }
        shadow = null;
        String what = "no change";
        try {
            DrawerOrder.Cell dragged = cellAt(curVisual);
            if (drop && overPill && dragged != null) {
                if (dragged.isFolder()) { st.order.dissolve(dragged.folder); what = "dropped on pill -> folder '" + dragged.folder.name + "' removed (apps kept)"; }
                else { st.order.hide(dragged.app); what = "dropped on pill -> hidden " + dragged.app; toast("Hidden. Tap Hidden to bring it back."); }
            } else if (drop && folderTarget >= 0 && folderTarget != curVisual) {
                DrawerOrder.Cell target = cellAt(folderTarget);
                boolean existing = target != null && target.isFolder();
                DrawerOrder.Folder f = st.order.combine(curVisual, folderTarget);
                if (f != null) { folderIconCache.remove(f.id); what = (existing ? "added to folder '" : "folder created '") + f.name + "' (" + f.apps.size() + " apps)"; }
            } else if (drop && dragStartVisual >= 0 && dragStartVisual != curVisual) {
                what = "moved " + dragStartVisual + " to " + curVisual;
            } else if (!drop) what = "cancelled";
        } catch (Throwable t) { Lx.e("Drawer: drop", t); }
        Lx.i("Drawer: drag end -> " + what);
        unhighlight();
        folderTarget = -1; hoverVisual = -1; hoverZone = ZONE_NONE; edgeZone = 0; overPill = false;
        dragKey = null; dragging = false; curVisual = -1; dragStartVisual = -1;
        for (View v : new ArrayList<>(dimmed.keySet())) { try { v.setAlpha(1f); } catch (Throwable ignored) { } }
        dimmed.clear();
        updatePill();
        showPill(true);
        LauncherHooks.rebuild(st);
        rv.invalidate();
    }

    private void unhighlight() {
        View h = highlighted == null ? null : highlighted.get();
        highlighted = null;
        if (h != null) { try { h.animate().cancel(); h.setScaleX(1f); h.setScaleY(1f); } catch (Throwable ignored) { } }
    }

    // ------------------------------------------------------------------ cell looks (called from the bind hook, after the stock bind)

    /** Global choices + per-folder overrides -> tile style. */
    private DrawerArt.Style styleFor(DrawerOrder.Folder f) {
        return new DrawerArt.Style(f.shape >= 0 ? f.shape : Cfg.choice(DrawerArt.KEY_SHAPE), f.color >= 0 ? f.color : Cfg.choice(DrawerArt.KEY_COLOR),
                Cfg.choice(DrawerArt.KEY_OPACITY), Cfg.choice(DrawerArt.KEY_GRID));
    }

    /** Folder cell: composite icon + name + our listeners on the launcher's BubbleTextView. */
    void applyFolderLook(View item, DrawerOrder.Folder f) {
        try {
            int label = DrawerArt.clamp(Cfg.choice(DrawerArt.KEY_LABEL), DrawerArt.LABELS.length);
            if (item instanceof TextView) {
                TextView tv = (TextView) item;
                tv.setText(f.name);
                if (label == 1) tv.setTextColor(Color.WHITE);
                else if (label == 2) tv.setTextColor(Color.BLACK);
            }
            setTextVisible(item, label != 3);
            item.setContentDescription(f.name);
            Bitmap icon = folderBitmap(item, f);
            if (icon != null) setIcon(item, icon);
            final DrawerOrder.Folder folder = f;
            item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openFolder(folder, v); } });
            item.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    if (editing) return startDragFromLongPress(v);
                    showCellMenu(v, new DrawerOrder.Cell(folder));
                    return true;
                }
            });
            styled.put(item, MODE_FOLDER);
            applyDragState(item, "folder:" + f.id);
        } catch (Throwable t) { Lx.e("Drawer: folder look", t); }
    }

    private static boolean loggedTextVis;
    /** BubbleTextView.setTextVisibility(boolean) (kept name); falls back to leaving the label alone. */
    private void setTextVisible(View item, boolean visible) {
        try { XposedHelpers.callMethod(item, "setTextVisibility", visible); }
        catch (Throwable t) { if (!loggedTextVis) { loggedTextVis = true; Lx.w("Drawer: BubbleTextView.setTextVisibility not available (" + t + "), folder label cannot be hidden"); } }
    }

    /**
     * App cell: in edit mode its long-press becomes our drag (the stock shortcut popup is suppressed only then); otherwise
     * the launcher's own click/long-click listeners are (re)installed on views that used to be folder or edit cells.
     */
    void applyAppLook(View item, String key) {
        try {
            Integer curObj = styled.get(item);
            int cur = curObj == null ? MODE_STOCK : curObj;
            int want = editing ? MODE_EDIT : MODE_STOCK;
            if (cur != want) {
                if (cur == MODE_FOLDER) { item.setOnClickListener(st.stockClick); setTextVisible(item, true); }
                if (want == MODE_EDIT) item.setOnLongClickListener(editLong);
                else {
                    item.setOnLongClickListener(st.stockLong);
                    if (st.stockLong == null && !loggedStockLongMissing) { loggedStockLongMissing = true; Lx.w("Drawer: stock long-click listener unknown; app cells that were edited/folders lose the shortcut popup until recycled"); }
                }
                if (want == MODE_STOCK) styled.remove(item); else styled.put(item, want);
            }
            applyDragState(item, key);
        } catch (Throwable t) { Lx.e("Drawer: app look", t); }
    }

    private void applyDragState(View item, String key) {
        View h = highlighted == null ? null : highlighted.get();
        if (item != h && (item.getScaleX() != 1f || item.getScaleY() != 1f)) { item.setScaleX(1f); item.setScaleY(1f); }
        if (dragKey != null && dragKey.equals(key)) { item.setAlpha(0f); dimmed.put(item, Boolean.TRUE); }
        else if (dimmed.remove(item) != null) item.setAlpha(1f);
    }

    private Bitmap folderBitmap(View item, DrawerOrder.Folder f) {
        int size = st.iconSize > 0 ? st.iconSize : dp(56);
        DrawerArt.Style style = styleFor(f);
        int want = style.grid == 1 ? 9 : style.grid == 2 ? 1 : 4;
        StringBuilder sig = new StringBuilder(f.id).append('|').append(size).append('|').append(style.key()).append('|').append(f.apps.size());
        for (int i = 0; i < Math.min(want, f.apps.size()); i++) sig.append('|').append(f.apps.get(i));
        String key = sig.toString();
        Bitmap cached = folderIconCache.get(f.id);
        if (cached != null && key.equals(folderIconSig.get(f.id)) && !cached.isRecycled()) return cached;
        List<Bitmap> icons = new ArrayList<>();
        for (int i = 0; i < Math.min(want, f.apps.size()); i++) icons.add(DrawerApps.iconOf(st.infoByKey.get(f.apps.get(i))));
        Bitmap out = DrawerArt.folderIcon(icons, f.apps.size(), size, style);
        folderIconCache.put(f.id, out);
        folderIconSig.put(f.id, key);
        return out;
    }
    private final Map<String, String> folderIconSig = new HashMap<>();

    /** BubbleTextView.setIcon(FastBitmapDrawable) when resolvable (keeps the launcher's icon pipeline), else a compound drawable. */
    private void setIcon(View item, Bitmap bmp) {
        try {
            if (!iconApiResolved) {
                iconApiResolved = true;
                try {
                    Method getIcon = item.getClass().getMethod("getIcon");
                    Class<?> fbd = getIcon.getReturnType();
                    fbdCtor = fbd.getConstructor(Bitmap.class);
                    btvSetIcon = H.find(item.getClass(), "setIcon", fbd);
                    Lx.i("Drawer: folder icon via " + (btvSetIcon != null ? "BubbleTextView.setIcon(" + fbd.getSimpleName() + ")" : "compound drawable"));
                } catch (Throwable t) { Lx.w("Drawer: FastBitmapDrawable API not resolved (" + t + "), using compound drawable"); }
            }
            if (btvSetIcon != null && fbdCtor != null) {
                btvSetIcon.invoke(item, fbdCtor.newInstance(bmp));
                return;
            }
        } catch (Throwable t) { Lx.w("Drawer: setIcon failed: " + t); }
        try {
            BitmapDrawable d = new BitmapDrawable(item.getResources(), bmp);
            int sz = st.iconSize > 0 ? st.iconSize : bmp.getWidth();
            d.setBounds(0, 0, sz, sz);
            if (item instanceof TextView) ((TextView) item).setCompoundDrawables(null, d, null, null);
        } catch (Throwable t) { Lx.e("Drawer: compound drawable", t); }
    }

    // ------------------------------------------------------------------ overlay: pill (auto-hiding) + page dots

    private static final long PILL_HIDE_MS = 3000, DRAW_GAP_MS = 700;
    private DotsView dots;
    private boolean dotsAttached, pillShown, emptyDown, geometryLogged;
    /** Pill shown by a tap on the dots strip: no auto-hide, stays until Done / tapped away. */
    private boolean pillSticky;
    private long lastDrawAt;
    private int lastPillTop = Integer.MIN_VALUE, lastDotsTop = Integer.MIN_VALUE;
    private final Runnable pillHider = new Runnable() { @Override public void run() { hidePill(false); } };

    /** One UI-style page indicator: dim round dots, the current page as a longer bright pill, soft shadow for light wallpapers. */
    private static final class DotsView extends View {
        final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final float d;
        int count = 1, current;
        DotsView(Context c, float density) { super(c); d = density; paint.setShadowLayer(2f * density, 0, 1f * density, 0x66000000); }
        boolean set(int n, int cur) {
            n = Math.max(1, n); cur = Math.max(0, Math.min(n - 1, cur));
            if (n == count && cur == current) return false;
            count = n; current = cur; invalidate(); return true;
        }
        @Override protected void onDraw(android.graphics.Canvas c) {
            if (count <= 1) return;
            float dot = 6f * d, wide = 18f * d, gap = 7f * d;
            float total = (count - 1) * (dot + gap) + wide;
            float x = (getWidth() - total) / 2f, cy = getHeight() / 2f, r = dot / 2f;
            for (int i = 0; i < count; i++) {
                if (i == current) { paint.setColor(0xFFFFFFFF); c.drawRoundRect(new android.graphics.RectF(x, cy - r, x + wide, cy + r), r, r, paint); x += wide + gap; }
                else { paint.setColor(0x80FFFFFF); c.drawCircle(x + r, cy, r, paint); x += dot + gap; }
            }
        }
    }

    /** (Re)place pill and dots; call after layout/size changes, mode changes and switch toggles. */
    void onLayout() {
        View rv = rv();
        if (rv == null) return;
        try {
            ViewGroupOverlay ov = (ViewGroupOverlay) ((ViewGroup) rv).getOverlay();
            boolean sized = rv.getWidth() > 0 && rv.getHeight() > 0;
            boolean wantPill = sized && customOn();
            boolean wantDots = sized && Cfg.on(LauncherHooks.KEY_DOTS);
            if (!wantPill) {
                if (pillAttached) { ov.remove(pill); pillAttached = false; pillShown = false; rv.removeCallbacks(pillHider); }
                if (editing) setEditing(false);
            }
            if (!wantDots && dotsAttached) { ov.remove(dots); dotsAttached = false; }
            if (!wantPill && !wantDots) return;

            int h = rv.getHeight(), w = rv.getWidth();
            int padB = rv.getPaddingBottom();
            int navB = navBarOverlap(rv);
            int reserve = reserveBottom();
            int free = padB - navB;                                                 // empty strip under the grid, above the nav bar
            // our strip = the top `reserve` px of the bottom padding (LauncherHooks adds them; until that padding is applied
            // the strip overlaps the last row for one layout pass, then onLayout runs again)
            int stripTop = h - padB, stripH = reserve > 0 ? reserve : dp(STRIP_DOTS_DP), dotsH = dp(16), pillH = 0, pw = 0;
            if (wantPill) {
                if (pill == null) buildPill();
                if (!pillAttached) { ov.add(pill); pillAttached = true; pill.setAlpha(0f); pillShown = false; }
                updatePill();
                pill.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST));
                pw = pill.getMeasuredWidth(); pillH = pill.getMeasuredHeight();
            }
            int dotsTop = stripTop + Math.max(0, (stripH - dotsH) / 2);
            int pillTop = stripTop + Math.max(0, (stripH - pillH) / 2);
            if (pillTop + pillH > h - navB) pillTop = Math.max(0, h - navB - pillH);  // never under the nav bar
            if (wantDots) {
                if (dots == null) dots = new DotsView(ctx, density);
                if (!dotsAttached) { ov.add(dots); dotsAttached = true; Lx.i("Drawer: page dots on"); }
                dots.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dotsH, View.MeasureSpec.EXACTLY));
                dots.layout(0, dotsTop, w, dotsTop + dotsH);
                dots.setAlpha(pillShown ? 0f : 1f);                                 // the pill takes the dots' place while it is up
                updateDots(rv);
            }
            if (wantPill) {
                int left = (w - pw) / 2;
                pill.layout(left, pillTop, left + pw, pillTop + pillH);
                if (editing || dragging) showPill(false);
            }
            if (!geometryLogged || pillTop != lastPillTop || dotsTop != lastDotsTop) {
                geometryLogged = true; lastPillTop = pillTop; lastDotsTop = dotsTop;
                Lx.i("Drawer: overlay rv=" + w + "x" + h + " padBottom=" + padB + " navOverlap=" + navB + " strip=" + stripTop + "+" + stripH + " (reserve " + reserve + ")"
                        + (wantDots ? " dots@" + dotsTop : "") + (wantPill ? " pill@" + pillTop : "") + (free >= reserve ? " (in padding)" : " (over grid, padding pending)"));
            }
            rv.invalidate();
        } catch (Throwable t) { Lx.e("Drawer: overlay layout", t); }
    }

    /** Navigation-bar pixels overlapping the bottom of the RecyclerView (0 when it ends above the bar). */
    private static int navBarOverlap(View rv) {
        try {
            android.view.WindowInsets wi = rv.getRootWindowInsets();
            if (wi == null) return 0;
            int nav;
            try { nav = wi.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom; }
            catch (Throwable t) { nav = wi.getSystemWindowInsetBottom(); }
            int[] loc = new int[2];
            rv.getLocationInWindow(loc);
            View root = rv.getRootView();
            int winH = root != null ? root.getHeight() : 0;
            int gap = winH > 0 ? winH - (loc[1] + rv.getHeight()) : 0;
            return Math.max(0, nav - Math.max(0, gap));
        } catch (Throwable t) { return 0; }
    }

    // ---- page dots

    private int currentPage(View rv) {
        try { return LauncherHooks.currentPage(rv, st); } catch (Throwable t) { return 0; }
    }

    private void updateDots(View rv) {
        if (!dotsAttached || dots == null) return;
        int n = st.cells.size();
        int pages = Math.max(1, (n + st.page() - 1) / st.page());
        if (dots.set(pages, currentPage(rv))) rv.invalidate();
    }

    /** AllAppsRecyclerView#onScrolled: move the dot; a visible auto-hiding pill gets its 3 s again (scrolling never brings it up). */
    void onScrolled() {
        View rv = rv();
        if (rv == null) return;
        if (dotsAttached) updateDots(rv);
        if (pillAttached && pillShown && !pillSticky) showPill(true);
    }

    /** Cell list / page count changed (refresh). */
    void onPagesChanged() {
        View rv = rv();
        if (rv != null && dotsAttached) updateDots(rv);
    }

    /**
     * AllAppsRecyclerView#onDraw: a first frame after a pause means the drawer was (re)opened -> show the pill briefly as a
     * hint, but only while the dots are off (with dots the strip belongs to them; tap it to get the pill).
     */
    void onDrawn() {
        long now = android.os.SystemClock.uptimeMillis();
        boolean reopened = now - lastDrawAt > DRAW_GAP_MS;
        lastDrawAt = now;
        if (reopened && pillAttached) {
            View rv = rv();
            if (rv != null && dotsAttached) updateDots(rv);
            if (pillShown && pillSticky && !editing) hidePill(true);                 // a sticky pill does not survive closing the drawer
            else if (!dotsAttached && !pillShown) showPill(true);
        }
    }

    // ---- pill visibility

    /** Fade the pill in (the dots fade out); with {@code autoHide} it fades out again after ~3 s unless editing/dragging/sticky. */
    private void showPill(boolean autoHide) {
        View rv = rv();
        if (rv == null || !pillAttached || pill == null) return;
        rv.removeCallbacks(pillHider);
        if (!pillShown) {
            pillShown = true;
            fade(pill, 1f, 160);
            if (dotsAttached && dots != null) fade(dots, 0f, 160);
        }
        if (autoHide && !editing && !dragging && !pillSticky) rv.postDelayed(pillHider, PILL_HIDE_MS);
    }

    private void hidePill(boolean force) {
        View rv = rv();
        if (rv != null) rv.removeCallbacks(pillHider);
        if (!pillShown || pill == null) return;
        if (!force && (editing || dragging)) return;
        pillShown = false; pillSticky = false;
        fade(pill, 0f, 220);
        if (dotsAttached && dots != null) fade(dots, 1f, 220);
    }

    private void fade(final View v, float alpha, long ms) {
        try { v.animate().cancel(); v.animate().alpha(alpha).setDuration(ms).start(); }
        catch (Throwable t) { try { v.setAlpha(alpha); } catch (Throwable ignored) { } }
        View rv = rv();
        if (rv != null) rv.invalidate();
    }

    private void buildPill() {
        pill = new LinearLayout(ctx);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pillMain = pillButton();
        pillHidden = pillButton();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.leftMargin = dp(8);
        pill.addView(pillMain, lp);
        pill.addView(pillHidden, lp2);
    }

    private TextView pillButton() {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTextColor(Color.WHITE);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(dp(18), dp(9), dp(18), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC202124);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), 0x40FFFFFF);
        t.setBackground(bg);
        return t;
    }

    private void updatePill() {
        if (pill == null) return;
        if (dragging) {
            DrawerOrder.Cell c = cellAt(curVisual);
            pillMain.setText(c != null && c.isFolder() ? "Drop here to remove folder" : "Drop here to hide");
            pillHidden.setVisibility(View.GONE);
        } else {
            pillMain.setText(editing ? "Done" : "Edit");
            int n = st.order == null ? 0 : st.order.hiddenCount();
            pillHidden.setText(n > 0 ? "Hidden (" + n + ")" : "Hidden");
            pillHidden.setVisibility(editing ? View.VISIBLE : View.GONE);
        }
        try { ((GradientDrawable) pillMain.getBackground()).setColor(dragging && overPill ? 0xE0B3261E : 0xCC202124); } catch (Throwable ignored) { }
        View rv = rv();
        if (rv != null && pillAttached) {
            int ws = View.MeasureSpec.makeMeasureSpec(rv.getWidth(), View.MeasureSpec.AT_MOST);
            int hs = View.MeasureSpec.makeMeasureSpec(rv.getHeight(), View.MeasureSpec.AT_MOST);
            pill.measure(ws, hs);
            int pw = pill.getMeasuredWidth(), ph = pill.getMeasuredHeight();
            int left = (rv.getWidth() - pw) / 2;
            pill.layout(left, pill.getTop(), left + pw, pill.getTop() + ph);
            rv.invalidate();
        }
    }

    /** 0 = main button, 1 = hidden button, -1 = none. Coordinates are RecyclerView-local. */
    private int pillHit(float x, float y) {
        if (!pillAttached || pill == null || !pillShown) return -1;
        int px = pill.getLeft(), py = pill.getTop();
        int pad = dp(6);
        for (int i = 0; i < pill.getChildCount(); i++) {
            View c = pill.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE) continue;
            int l = px + c.getLeft() - pad, t = py + c.getTop() - pad, r = px + c.getRight() + pad, b = py + c.getBottom() + pad;
            if (x >= l && x <= r && y >= t && y <= b) return i;
        }
        return -1;
    }

    private void pillAction(int idx) {
        if (idx == 0) setEditing(!editing);
        else if (idx == 1) hiddenDialog();
    }

    void setEditing(boolean on) {
        if (editing == on) return;
        editing = on;
        Lx.i("Drawer: edit mode " + (on ? "on" : "off") + (fixedSlots() ? " (fixed slots)" : " (compact)"));
        View rv = rv();
        if (rv != null) {
            if (!on && dragging) endDrag(rv, false);
            resetPress();
        }
        try { if (st.order != null) st.order.setEditing(on); } catch (Throwable t) { Lx.e("Drawer: order edit state", t); }   // off: trailing empty pages go
        onLayout();
        if (on) { pillSticky = true; showPill(false); }                             // stays while editing
        else { pillSticky = false; hidePill(true); }                                // Done: the dots are back
        int pageBefore = rv != null ? currentPage(rv) : 0;
        LauncherHooks.rebuild(st);                                                  // rebind visible cells with edit/stock long-press listeners
        if (!on && rv != null) {                                                    // a trailing empty page may just have gone: stay on a real page
            int pages = Math.max(1, (st.cells.size() + st.page() - 1) / st.page());
            if (pageBefore >= pages) {
                final int last = pages - 1;
                final View r = rv;
                rv.post(new Runnable() { @Override public void run() { LauncherHooks.scrollToPage(r, st, last, true); } });
            }
        }
        if (on) toast(fixedSlots()
                ? "Hold an icon, then drag it to any empty slot (also on other pages; hold at the right edge of the last page for a new page). Drop it on another app to make a folder, or on the button below to hide it."
                : "Hold an icon, then drag it. Drop it on another app to make a folder, or on the button below to hide it.");
    }

    // ------------------------------------------------------------------ dialogs

    private Activity activity() {
        Context c = ctx;
        for (int i = 0; i < 8 && c != null; i++) {
            if (c instanceof Activity) return (Activity) c;
            if (!(c instanceof ContextWrapper)) break;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private Context dialogContext() { Activity a = activity(); return a != null ? a : ctx; }

    private void toast(String s) { try { Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) { } }

    private void renameDialog(final DrawerOrder.Folder f) {
        try {
            final EditText et = new EditText(dialogContext());
            et.setText(f.name);
            et.setSelectAllOnFocus(true);
            et.setSingleLine(true);
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            int p = dp(20);
            android.widget.FrameLayout box = new android.widget.FrameLayout(dialogContext());
            box.setPadding(p, dp(8), p, 0);
            box.addView(et, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            new AlertDialog.Builder(dialogContext())
                    .setTitle("Folder name")
                    .setView(box)
                    .setPositiveButton("Rename", new android.content.DialogInterface.OnClickListener() {
                        @Override public void onClick(android.content.DialogInterface d, int w) {
                            st.order.rename(f, et.getText().toString());
                            LauncherHooks.rebuild(st);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Throwable t) { Lx.e("Drawer: rename dialog", t); }
    }

    /** Per-folder colour / shape override picked from the same lists as the global settings. */
    private void pickStyle(final DrawerOrder.Folder f, final boolean color) { pickStyle(f, color, null); }

    private void pickStyle(final DrawerOrder.Folder f, final boolean color, final Runnable after) {
        try {
            final String[] names = color ? DrawerArt.COLORS : DrawerArt.SHAPES;
            final CharSequence[] labels = new CharSequence[names.length + 1];
            labels[0] = "Use the setting (" + names[DrawerArt.clamp(Cfg.choice(color ? DrawerArt.KEY_COLOR : DrawerArt.KEY_SHAPE), names.length)] + ")";
            System.arraycopy(names, 0, labels, 1, names.length);
            new AlertDialog.Builder(dialogContext())
                    .setTitle(color ? "Folder colour: " + f.name : "Folder shape: " + f.name)
                    .setItems(labels, new android.content.DialogInterface.OnClickListener() {
                        @Override public void onClick(android.content.DialogInterface d, int which) {
                            int v = which - 1;
                            if (color) st.order.setStyle(f, f.shape, v); else st.order.setStyle(f, v, f.color);
                            folderIconCache.remove(f.id);
                            Lx.i("Drawer: folder '" + f.name + "' " + (color ? "colour" : "shape") + " -> " + (v < 0 ? "default" : names[v]));
                            LauncherHooks.rebuild(st);
                            if (after != null) after.run();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Throwable t) { Lx.e("Drawer: style picker", t); }
    }

    void hiddenDialog() {
        try {
            Set<String> hidden = st.order.hidden();
            if (hidden.isEmpty()) { toast("No hidden apps. Hold an app in edit mode to hide it."); return; }
            final List<String> keys = new ArrayList<>(hidden);
            final CharSequence[] labels = new CharSequence[keys.size()];
            for (int i = 0; i < keys.size(); i++) {
                Object info = st.infoByKey.get(keys.get(i));
                CharSequence t = info == null ? null : DrawerApps.titleOf(info);
                labels[i] = TextUtils.isEmpty(t) ? keys.get(i) : t;
            }
            new AlertDialog.Builder(dialogContext())
                    .setTitle("Hidden apps (tap to show again)")
                    .setItems(labels, new android.content.DialogInterface.OnClickListener() {
                        @Override public void onClick(android.content.DialogInterface d, int which) {
                            st.order.unhide(keys.get(which));
                            LauncherHooks.rebuild(st);
                            updatePill();
                        }
                    })
                    .setNegativeButton("Close", null)
                    .show();
        } catch (Throwable t) { Lx.e("Drawer: hidden dialog", t); }
    }

    /** Our folder view: dark rounded card, editable title, 4-column icon grid; tap = launch, long-press = move out. */
    void openFolder(final DrawerOrder.Folder f, View anchor) {
        try {
            if (openDialog != null && openDialog.isShowing()) openDialog.dismiss();
            hidePill(true);
            if (DrawerKeys.folderOneUi()) { openFolderOneUi(f, anchor); return; }
            final Context dc = dialogContext();
            final Dialog d = new Dialog(dc);
            d.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout root = new LinearLayout(dc);
            root.setOrientation(LinearLayout.VERTICAL);
            int p = dp(18);
            root.setPadding(p, dp(14), p, p);
            int bgChoice = Cfg.choice(DrawerArt.KEY_VIEW_BG);
            final int textColor = DrawerArt.viewTextColor(bgChoice);
            boolean noNames = Cfg.on(DrawerArt.KEY_VIEW_NO_NAMES);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(DrawerArt.viewCardColor(bgChoice));
            bg.setCornerRadius(dp(DrawerArt.viewRadiusDp(Cfg.choice(DrawerArt.KEY_VIEW_RADIUS))));
            root.setBackground(bg);

            final EditText title = new EditText(dc);
            title.setText(f.name);
            title.setTextColor(textColor);
            title.setHintTextColor((textColor & 0x00FFFFFF) | 0x80000000);
            title.setHint(DrawerOrder.DEFAULT_FOLDER_NAME);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
            title.setGravity(Gravity.CENTER);
            title.setSingleLine(true);
            title.setBackground(null);
            title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            title.setPadding(0, 0, 0, dp(10));
            root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            GridLayout grid = new GridLayout(dc);
            int colsN = Math.max(1, Math.min(DrawerArt.viewColumns(Cfg.choice(DrawerArt.KEY_VIEW_COLS)), f.apps.size()));
            grid.setColumnCount(colsN);
            int iconSz = st.iconSize > 0 ? st.iconSize : dp(56);
            for (final String key : new ArrayList<>(f.apps)) {
                final Object info = st.infoByKey.get(key);
                LinearLayout cell = new LinearLayout(dc);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(dp(4), dp(10), dp(4), dp(6));
                ImageView iv = new ImageView(dc);
                Bitmap b = DrawerApps.iconOf(info);
                if (b != null) iv.setImageBitmap(b);
                cell.addView(iv, new LinearLayout.LayoutParams(iconSz, iconSz));
                CharSequence t = DrawerApps.titleOf(info);
                cell.setContentDescription(TextUtils.isEmpty(t) ? key : t);
                if (!noNames) {
                    TextView tv = new TextView(dc);
                    tv.setText(TextUtils.isEmpty(t) ? key : t);
                    tv.setTextColor(textColor);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                    tv.setGravity(Gravity.CENTER_HORIZONTAL);
                    tv.setMaxLines(2);
                    tv.setEllipsize(TextUtils.TruncateAt.END);
                    tv.setPadding(0, dp(4), 0, 0);
                    cell.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                cell.setClickable(true);
                cell.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        int[] loc = new int[2]; v.getLocationOnScreen(loc);
                        Rect r = new Rect(loc[0], loc[1], loc[0] + v.getWidth(), loc[1] + v.getHeight());
                        d.dismiss();
                        if (info == null || !DrawerApps.launch(ctx, info, r)) toast("App not available");
                    }
                });
                cell.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View v) {
                        PopupMenu pm = new PopupMenu(dc, v);
                        pm.getMenu().add(0, 1, 0, "Move out of folder");
                        pm.getMenu().add(0, 2, 1, "Hide from drawer");
                        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            @Override public boolean onMenuItemClick(android.view.MenuItem item) {
                                if (item.getItemId() == 1) st.order.removeFromFolder(f, key); else st.order.hide(key);
                                folderIconCache.remove(f.id);
                                d.dismiss();
                                LauncherHooks.rebuild(st);
                                updatePill();
                                return true;
                            }
                        });
                        pm.show();
                        return true;
                    }
                });
                GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
                glp.width = 0; glp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                grid.addView(cell, glp);
            }
            root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            d.setContentView(root);
            Window w = d.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                int sw = ctx.getResources().getDisplayMetrics().widthPixels;
                w.setLayout(Math.min(Math.round(sw * 0.86f), dp(420)), ViewGroup.LayoutParams.WRAP_CONTENT);
                w.setDimAmount(0.45f);
            }
            d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                @Override public void onDismiss(android.content.DialogInterface di) {
                    String n = title.getText().toString().trim();
                    if (!n.equals(f.name)) { st.order.rename(f, n); LauncherHooks.rebuild(st); }
                    if (openDialog == d) openDialog = null;
                    if (editing) { pillSticky = true; showPill(false); }
                }
            });
            openDialog = d;
            d.show();
        } catch (Throwable t) { Lx.e("Drawer: open folder", t); toast("Could not open folder"); }
    }


    // ------------------------------------------------------------------ One UI full-screen folder (DrawerKeys.KEY_FOLDER_OPEN)

    private static final android.view.animation.Interpolator ONEUI_EASE = new android.view.animation.PathInterpolator(0.22f, 0.25f, 0f, 1f);

    /**
     * Samsung-style opened folder: full-screen window over a blurred + dimmed launcher, the folder name big and centred
     * near the top (tap to rename), "+" (add apps) and a colour dot above the right end of a rounded translucent panel
     * holding a 4-column grid. The panel grows out of the folder icon and shrinks back into it on close.
     */
    private void openFolderOneUi(final DrawerOrder.Folder f, final View anchor) {
        final Context dc = dialogContext();
        final Dialog d = new Dialog(dc, android.R.style.Theme_Translucent_NoTitleBar);
        final android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        final int sh = dm.heightPixels;
        int bgChoice = Cfg.choice(DrawerArt.KEY_VIEW_BG);
        final boolean light = DrawerArt.clamp(bgChoice, DrawerArt.VIEW_BGS.length) == 1;
        final int panelColor = light ? 0xF2F7F7F9 : (DrawerArt.clamp(bgChoice, DrawerArt.VIEW_BGS.length) == 2 ? 0x8C17181B : 0xD91E1F22);
        final int cellText = light ? 0xFF1F1F1F : 0xFFFFFFFF;
        final boolean noNames = Cfg.on(DrawerArt.KEY_VIEW_NO_NAMES);
        final boolean[] closing = {false};

        final android.widget.FrameLayout root = new android.widget.FrameLayout(dc);
        final LinearLayout col = new LinearLayout(dc);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int statusH = 0;
        try { int id = android.content.res.Resources.getSystem().getIdentifier("status_bar_height", "dimen", "android"); if (id != 0) statusH = android.content.res.Resources.getSystem().getDimensionPixelSize(id); } catch (Throwable ignored) { }
        col.setPadding(dp(16), statusH, dp(16), LauncherHooks.navBarRes() + dp(20));
        root.addView(col, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // big title
        final EditText title = new EditText(dc);
        title.setText(f.name);
        title.setTextColor(0xFFFFFFFF);
        title.setHintTextColor(0x80FFFFFF);
        title.setHint(DrawerOrder.DEFAULT_FOLDER_NAME);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        title.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setBackground(null);
        title.setCursorVisible(false);
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        title.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        title.setShadowLayer(dp(6), 0, dp(1), 0x40000000);
        title.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) { title.setCursorVisible(has); if (has) title.selectAll(); }
        });
        title.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent e) {
                title.clearFocus();
                try { ((android.view.inputmethod.InputMethodManager) dc.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(title.getWindowToken(), 0); } catch (Throwable ignored) { }
                String n = title.getText().toString().trim();
                if (!n.equals(f.name)) { st.order.rename(f, n); folderIconCache.remove(f.id); LauncherHooks.rebuild(st); }
                return true;
            }
        });
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Math.round(sh * 0.085f);
        col.addView(title, tlp);
        col.addView(new View(dc), new LinearLayout.LayoutParams(1, 0, 1f));

        // "+" and the colour dot, right-aligned above the panel
        LinearLayout row = new LinearLayout(dc);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, dp(14), 0);
        TextView plus = new TextView(dc);
        plus.setText("+");
        plus.setTextColor(0xFFFFFFFF);
        plus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        plus.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        plus.setGravity(Gravity.CENTER);
        plus.setContentDescription("Add apps");
        row.addView(plus, new LinearLayout.LayoutParams(dp(48), dp(48)));
        final View dot = new View(dc);
        final GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(DrawerArt.tileColor(styleFor(f)) | 0xFF000000);
        dotBg.setStroke(Math.max(1, dp(1.5f)), 0xB3FFFFFF);
        dot.setBackground(dotBg);
        dot.setContentDescription("Folder colour");
        android.widget.FrameLayout dotBox = new android.widget.FrameLayout(dc);
        android.widget.FrameLayout.LayoutParams dlp = new android.widget.FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER);
        dotBox.addView(dot, dlp);
        row.addView(dotBox, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = dp(6);
        col.addView(row, rlp);

        // rounded panel with the grid (scrolls when the folder is taller than ~60 % of the screen)
        final LinearLayout panel = new LinearLayout(dc);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(panelColor);
        pbg.setCornerRadius(dp(26));
        panel.setBackground(pbg);
        panel.setPadding(dp(10), dp(14), dp(10), dp(14));
        panel.setClickable(true);
        final int maxPanel = Math.round(sh * 0.6f);
        android.widget.ScrollView scroll = new android.widget.ScrollView(dc) {
            @Override protected void onMeasure(int wSpec, int hSpec) {
                super.onMeasure(wSpec, View.MeasureSpec.makeMeasureSpec(maxPanel, View.MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        GridLayout grid = new GridLayout(dc);
        int colsN = Math.max(1, DrawerArt.viewColumns(Cfg.choice(DrawerArt.KEY_VIEW_COLS)));
        grid.setColumnCount(colsN);
        int iconSz = st.iconSize > 0 ? st.iconSize : dp(56);
        for (final String key : new ArrayList<>(f.apps)) {
            final Object info = st.infoByKey.get(key);
            LinearLayout cell = new LinearLayout(dc);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(2), dp(12), dp(2), dp(12));
            ImageView iv = new ImageView(dc);
            Bitmap b = DrawerApps.iconOf(info);
            if (b != null) iv.setImageBitmap(b);
            cell.addView(iv, new LinearLayout.LayoutParams(iconSz, iconSz));
            CharSequence t = DrawerApps.titleOf(info);
            cell.setContentDescription(TextUtils.isEmpty(t) ? key : t);
            if (!noNames) {
                TextView tv = new TextView(dc);
                tv.setText(TextUtils.isEmpty(t) ? key : t);
                tv.setTextColor(cellText);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
                tv.setGravity(Gravity.CENTER_HORIZONTAL);
                tv.setMaxLines(2);
                tv.setLineSpacing(0, 0.92f);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setPadding(0, dp(6), 0, 0);
                cell.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            cell.setClickable(true);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    int[] loc = new int[2]; v.getLocationOnScreen(loc);
                    Rect r = new Rect(loc[0], loc[1], loc[0] + v.getWidth(), loc[1] + v.getHeight());
                    d.dismiss();
                    if (info == null || !DrawerApps.launch(ctx, info, r)) toast("App not available");
                }
            });
            cell.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    PopupMenu pm = new PopupMenu(dc, v);
                    pm.getMenu().add(0, 1, 0, "Move out of folder");
                    pm.getMenu().add(0, 2, 1, "Hide from drawer");
                    pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override public boolean onMenuItemClick(android.view.MenuItem item) {
                            if (item.getItemId() == 1) st.order.removeFromFolder(f, key); else st.order.hide(key);
                            folderIconCache.remove(f.id);
                            d.dismiss();
                            LauncherHooks.rebuild(st);
                            updatePill();
                            return true;
                        }
                    });
                    pm.show();
                    return true;
                }
            });
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0; glp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            grid.addView(cell, glp);
        }
        scroll.addView(grid, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(panel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(new View(dc), new LinearLayout.LayoutParams(1, 0, 0.45f));

        // origin of the open/close animation: centre of the folder icon on screen (screen centre when it is gone)
        final float[] origin = {dm.widthPixels / 2f, sh / 2f};
        if (anchor != null && anchor.isAttachedToWindow()) {
            int[] loc = new int[2]; anchor.getLocationOnScreen(loc);
            origin[0] = loc[0] + anchor.getWidth() / 2f; origin[1] = loc[1] + anchor.getHeight() / 2f;
        }
        final View[] chrome = {title, row};
        final Runnable close = new Runnable() {
            @Override public void run() {
                if (closing[0]) return;
                closing[0] = true;
                try {
                    int[] pl = new int[2]; panel.getLocationOnScreen(pl);
                    panel.setPivotX(origin[0] - pl[0]); panel.setPivotY(origin[1] - pl[1]);
                    for (View c : chrome) c.animate().alpha(0f).setDuration(120).start();
                    panel.animate().scaleX(0.2f).scaleY(0.2f).alpha(0f).setDuration(220).setInterpolator(ONEUI_EASE)
                            .withEndAction(new Runnable() { @Override public void run() { try { d.dismiss(); } catch (Throwable ignored) { } } }).start();
                } catch (Throwable t) { d.dismiss(); }
            }
        };
        root.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            if (title.hasFocus()) { title.clearFocus(); try { ((android.view.inputmethod.InputMethodManager) dc.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(title.getWindowToken(), 0); } catch (Throwable ignored) { } return; }
            close.run();
        } });
        col.setClickable(false);
        plus.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { addAppsDialog(f, d, anchor); } });
        dot.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            pickStyle(f, true, new Runnable() { @Override public void run() { dotBg.setColor(DrawerArt.tileColor(styleFor(f)) | 0xFF000000); } });
        } });
        d.setOnKeyListener(new android.content.DialogInterface.OnKeyListener() {
            @Override public boolean onKey(android.content.DialogInterface di, int code, android.view.KeyEvent e) {
                if (code != android.view.KeyEvent.KEYCODE_BACK) return false;
                if (e.getAction() == android.view.KeyEvent.ACTION_UP) close.run();
                return true;
            }
        });

        d.setContentView(root);
        root.setFocusable(true);                        // keep the initial focus off the title (no selection / caret on open)
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setWindowAnimations(0);
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND | android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
            w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            try { w.setDecorFitsSystemWindows(false); } catch (Throwable ignored) { }
            android.view.WindowManager.LayoutParams a = w.getAttributes();
            a.layoutInDisplayCutoutMode = 3;   // LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            boolean blur = false;
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                try {
                    android.view.WindowManager wm = (android.view.WindowManager) dc.getSystemService(Context.WINDOW_SERVICE);
                    blur = wm != null && wm.isCrossWindowBlurEnabled();
                } catch (Throwable ignored) { }
                if (blur) { w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND); a.setBlurBehindRadius(dp(36)); }
            }
            a.dimAmount = blur ? 0.32f : 0.62f;
            w.setAttributes(a);
        }
        d.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override public void onShow(android.content.DialogInterface di) {
                panel.post(new Runnable() { @Override public void run() {
                    try {
                        int[] pl = new int[2]; panel.getLocationOnScreen(pl);
                        panel.setPivotX(origin[0] - pl[0]); panel.setPivotY(origin[1] - pl[1]);
                        panel.setScaleX(0.2f); panel.setScaleY(0.2f); panel.setAlpha(0f);
                        panel.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(380).setInterpolator(ONEUI_EASE).start();
                        for (View c : chrome) { c.setAlpha(0f); c.animate().alpha(1f).setStartDelay(90).setDuration(220).start(); }
                    } catch (Throwable t) { panel.setScaleX(1f); panel.setScaleY(1f); panel.setAlpha(1f); }
                } });
            }
        });
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface di) {
                String n = title.getText().toString().trim();
                if (!n.equals(f.name)) { st.order.rename(f, n); folderIconCache.remove(f.id); LauncherHooks.rebuild(st); }
                if (openDialog == d) openDialog = null;
                if (editing) { pillSticky = true; showPill(false); }
            }
        });
        openDialog = d;
        d.show();
        Lx.i("Drawer: One UI folder '" + f.name + "' opened (" + f.apps.size() + " apps, " + colsN + " columns)");
    }

    /** "+" in the One UI folder: multi-select of drawer apps (not folders) to move into this folder, then reopen it. */
    private void addAppsDialog(final DrawerOrder.Folder f, final Dialog folderDialog, final View anchor) {
        try {
            final List<String> keys = new ArrayList<>();
            final List<CharSequence> labels = new ArrayList<>();
            List<DrawerOrder.Cell> cells = new ArrayList<>(st.cells);
            final java.text.Collator coll = java.text.Collator.getInstance();
            List<String[]> rows = new ArrayList<>();
            for (DrawerOrder.Cell c : cells) {
                if (c == null || c.isFolder() || c.app == null || f.apps.contains(c.app)) continue;
                Object info = st.infoByKey.get(c.app);
                CharSequence t = info == null ? null : DrawerApps.titleOf(info);
                rows.add(new String[]{c.app, TextUtils.isEmpty(t) ? c.app : t.toString()});
            }
            java.util.Collections.sort(rows, new java.util.Comparator<String[]>() { @Override public int compare(String[] a, String[] b) { return coll.compare(a[1], b[1]); } });
            for (String[] r : rows) { keys.add(r[0]); labels.add(r[1]); }
            if (keys.isEmpty()) { toast("No other apps to add"); return; }
            final boolean[] checked = new boolean[keys.size()];
            new AlertDialog.Builder(dialogContext())
                    .setTitle("Add apps to " + f.name)
                    .setMultiChoiceItems(labels.toArray(new CharSequence[0]), checked, new android.content.DialogInterface.OnMultiChoiceClickListener() {
                        @Override public void onClick(android.content.DialogInterface di, int which, boolean on) { checked[which] = on; }
                    })
                    .setPositiveButton("Add", new android.content.DialogInterface.OnClickListener() {
                        @Override public void onClick(android.content.DialogInterface di, int w) {
                            int n = 0;
                            for (int i = 0; i < keys.size(); i++) if (checked[i] && st.order.addToFolder(f, keys.get(i))) n++;
                            if (n == 0) return;
                            folderIconCache.remove(f.id);
                            Lx.i("Drawer: added " + n + " app(s) to folder '" + f.name + "'");
                            try { folderDialog.dismiss(); } catch (Throwable ignored) { }
                            LauncherHooks.rebuild(st);
                            updatePill();
                            openFolderOneUi(f, anchor);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Throwable t) { Lx.e("Drawer: add apps", t); }
    }
}
