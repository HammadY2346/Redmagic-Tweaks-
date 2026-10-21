package com.hammad.redmagictweaks.xposed;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Custom app-drawer layout for the paged drawer: slots of apps + our own folders ("groups") + hidden apps.
 *
 * Lives in the LAUNCHER process. Persisted as one JSON string in the launcher's own SharedPreferences file
 * {@code rmtweaks_drawer} (/data/user/0/com.zte.mifavor.launcher/shared_prefs/rmtweaks_drawer.xml), key {@code state}:
 * <pre>
 * {"v":2,
 *  "slots":{ "0":"com.a/.Main", "1":{"f":"g1","n":"Games","a":["com.b/.Main","com.c/.Main#10"],"s":2,"c":9}, "7":"com.d/.Main", ... },
 *  "hidden":["com.e/.Main"]}
 * </pre>
 * (s/c: optional per-folder shape/colour). The slot index is the flat visual index (page * rows*cols + row * cols + col);
 * indices missing from the map are empty slots (gaps). Version 1 ({@code "order":[...]}) is migrated by filling slots
 * sequentially. An app key is {@code ComponentName.flattenToString()} plus {@code #<userSerial>} for non-primary users.
 * Nothing here touches the launcher's database or model; the launcher still believes the drawer is alphabetical.
 *
 * Two placement modes, chosen by the caller on every {@link #resolve}:
 * <ul>
 *   <li><b>compact</b> (switch off): the list never contains gaps, moving a cell shifts the others (classic reorder);</li>
 *   <li><b>fixed slots</b> (One UI / Samsung behaviour): cells keep their slot, moving a cell leaves a gap behind, empty
 *       slots are allowed anywhere, a page may hold a single app; while {@link #setEditing editing} an extra empty page can
 *       be appended ({@link #addPage}); trailing fully-empty pages are dropped when editing ends ({@link #trim}),
 *       interior gaps are kept.</li>
 * </ul>
 * In both modes "visual index" == slot index: {@link #resolve} drops apps that are gone or hidden (compact: removes the
 * slot; fixed: empties it) and appends apps the launcher knows but we don't (new installs) after the last used slot.
 */
final class DrawerOrder {
    private static final String PREFS = "rmtweaks_drawer";
    private static final String KEY = "state";
    static final String DEFAULT_FOLDER_NAME = "Folder";

    static final class Folder {
        final String id;
        String name;
        /** Per-folder overrides of the global folder shape / colour choices; -1 = use the setting. */
        int shape = -1, color = -1;
        final ArrayList<String> apps = new ArrayList<>();
        Folder(String id, String name) { this.id = id; this.name = name == null || name.isEmpty() ? DEFAULT_FOLDER_NAME : name; }
    }

    /** One visible drawer cell: either an app (key) or a folder. A {@code null} Cell in the slot list is an empty slot. */
    static final class Cell {
        final String app;
        final Folder folder;
        Cell(String app) { this.app = app; this.folder = null; }
        Cell(Folder f) { this.app = null; this.folder = f; }
        boolean isFolder() { return folder != null; }
        /** Key of the app whose launcher view backs this cell (first app for folders). */
        String anchorKey() { return folder != null ? (folder.apps.isEmpty() ? null : folder.apps.get(0)) : app; }
    }

    private static DrawerOrder instance;

    static synchronized DrawerOrder get(Context ctx) {
        if (instance == null) instance = new DrawerOrder(ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx);
        return instance;
    }

    private final SharedPreferences prefs;
    /** Slot list; null entries are empty slots (only ever present in fixed-slot mode, or transiently in compact mode). */
    private final ArrayList<Cell> cells = new ArrayList<>();
    private final LinkedHashSet<String> hidden = new LinkedHashSet<>();
    private boolean loaded;
    private int nextFolderId = 1;
    /** Placement mode of the last resolve (see class doc). */
    private boolean fixed;
    /** Edit mode: trailing empty slots/pages are kept (the user may have just added a page). */
    private boolean editing;
    /** Slots per page as last told by the drawer (for addPage / trim). */
    private int pageSize = 30;

    private DrawerOrder(Context ctx) {
        SharedPreferences p = null;
        try { p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); } catch (Throwable t) { Lx.e("DrawerOrder: prefs", t); }
        prefs = p;
        load();
    }

    // ------------------------------------------------------------------ persistence

    private synchronized void load() {
        cells.clear(); hidden.clear();
        loaded = true;
        if (prefs == null) return;
        try {
            String s = prefs.getString(KEY, null);
            if (s == null || s.isEmpty()) { Lx.i("DrawerOrder: no saved layout yet"); return; }
            JSONObject root = new JSONObject(s);
            int v = root.optInt("v", 1);
            JSONObject slots = root.optJSONObject("slots");
            if (slots != null) {
                int max = -1;
                for (Iterator<String> it = slots.keys(); it.hasNext(); ) {
                    try { max = Math.max(max, Integer.parseInt(it.next())); } catch (Throwable ignored) { }
                }
                if (max > 4095) { Lx.w("DrawerOrder: slot index " + max + " too large, clamping"); max = 4095; }
                for (int i = 0; i <= max; i++) cells.add(null);
                for (Iterator<String> it = slots.keys(); it.hasNext(); ) {
                    String k = it.next();
                    int idx;
                    try { idx = Integer.parseInt(k); } catch (Throwable t) { continue; }
                    if (idx < 0 || idx > max) continue;
                    Cell c = parseCell(slots.opt(k));
                    if (c != null) cells.set(idx, c);
                }
            } else {
                JSONArray order = root.optJSONArray("order");                       // v1: sequential list -> slots 0..n-1
                if (order != null) for (int i = 0; i < order.length(); i++) { Cell c = parseCell(order.opt(i)); if (c != null) cells.add(c); }
            }
            JSONArray h = root.optJSONArray("hidden");
            if (h != null) for (int i = 0; i < h.length(); i++) { String key = h.optString(i, null); if (key != null && !key.isEmpty()) hidden.add(key); }
            trimTrailing();
            Lx.i("DrawerOrder: loaded v" + v + " " + used() + " cells in " + cells.size() + " slots, " + hidden.size() + " hidden" + (v < 2 ? " (migrated to v2 slots)" : ""));
        } catch (Throwable t) { Lx.e("DrawerOrder: load", t); }
    }

    private Cell parseCell(Object o) {
        if (o instanceof String) return ((String) o).isEmpty() ? null : new Cell((String) o);
        if (o instanceof JSONObject) {
            JSONObject jo = (JSONObject) o;
            String id = jo.optString("f", "g" + (nextFolderId));
            Folder f = new Folder(id, jo.optString("n", DEFAULT_FOLDER_NAME));
            f.shape = jo.optInt("s", -1);
            f.color = jo.optInt("c", -1);
            JSONArray a = jo.optJSONArray("a");
            if (a != null) for (int k = 0; k < a.length(); k++) { String key = a.optString(k, null); if (key != null && !key.isEmpty()) f.apps.add(key); }
            bumpFolderId(id);
            return new Cell(f);
        }
        return null;
    }

    private void bumpFolderId(String id) {
        try { if (id.startsWith("g")) nextFolderId = Math.max(nextFolderId, Integer.parseInt(id.substring(1)) + 1); } catch (Throwable ignored) { }
    }

    synchronized void save() {
        if (prefs == null) return;
        try {
            JSONObject slots = new JSONObject();
            for (int i = 0; i < cells.size(); i++) {
                Cell c = cells.get(i);
                if (c == null) continue;
                if (c.isFolder()) {
                    JSONObject jo = new JSONObject();
                    jo.put("f", c.folder.id);
                    jo.put("n", c.folder.name);
                    if (c.folder.shape >= 0) jo.put("s", c.folder.shape);
                    if (c.folder.color >= 0) jo.put("c", c.folder.color);
                    JSONArray a = new JSONArray();
                    for (String k : c.folder.apps) a.put(k);
                    jo.put("a", a);
                    slots.put(String.valueOf(i), jo);
                } else slots.put(String.valueOf(i), c.app);
            }
            JSONArray h = new JSONArray();
            for (String k : hidden) h.put(k);
            JSONObject root = new JSONObject();
            root.put("v", 2);
            root.put("slots", slots);
            root.put("hidden", h);
            prefs.edit().putString(KEY, root.toString()).apply();
        } catch (Throwable t) { Lx.e("DrawerOrder: save", t); }
    }

    // ------------------------------------------------------------------ resolve

    /**
     * Reconcile the saved layout with the apps the launcher currently shows (in its own alphabetical order) and return
     * the visible slots (null = empty slot). With {@code custom == false} the result is just the launcher order (no
     * folders, nothing hidden; the saved layout is left untouched so switching the feature off and on again restores it).
     * {@code fixed} selects the placement mode (see class doc); switching fixed -> compact compacts the gaps away.
     * Mutates and persists the saved layout when apps appeared/disappeared.
     */
    synchronized List<Cell> resolve(List<String> present, boolean custom, boolean fixed, int pageSize) {
        if (!loaded) load();
        if (pageSize > 0) this.pageSize = pageSize;
        ArrayList<Cell> out = new ArrayList<>(present.size());
        if (!custom) {
            for (String k : present) out.add(new Cell(k));
            return out;
        }
        boolean modeChanged = this.fixed != fixed;
        this.fixed = fixed;
        Set<String> presentSet = new HashSet<>(present);
        Set<String> placed = new HashSet<>();
        boolean changed = modeChanged;
        for (Cell c : cells) {
            if (c == null) { if (fixed) out.add(null); else changed = true; continue; }
            if (c.isFolder()) {
                Folder f = c.folder;
                for (int i = f.apps.size() - 1; i >= 0; i--) {
                    String k = f.apps.get(i);
                    if (!presentSet.contains(k) || hidden.contains(k) || placed.contains(k)) { f.apps.remove(i); changed = true; }
                    else placed.add(k);
                }
                if (!f.apps.isEmpty()) out.add(c); else { changed = true; if (fixed) out.add(null); }
            } else {
                String k = c.app;
                if (presentSet.contains(k) && !hidden.contains(k) && !placed.contains(k)) { out.add(c); placed.add(k); }
                else { changed = true; if (fixed) out.add(null); }
            }
        }
        int added = 0;
        for (String k : present) {
            if (!placed.contains(k) && !hidden.contains(k)) { appendFree(out, new Cell(k)); placed.add(k); changed = true; added++; }
        }
        if (changed || out.size() != cells.size()) {
            cells.clear(); cells.addAll(out);
            if (!editing) trimTrailing();
            save();
            Lx.i("DrawerOrder: reconciled (" + (fixed ? "fixed slots" : "compact") + ", +" + added + " new) -> " + used() + " cells in " + cells.size() + " slots");
        }
        return new ArrayList<>(cells);
    }

    /** New cell after the last used slot (the first free slot "at the end"). */
    private static void appendFree(ArrayList<Cell> list, Cell c) {
        int last = list.size() - 1;
        while (last >= 0 && list.get(last) == null) last--;
        if (last + 1 < list.size()) list.set(last + 1, c); else list.add(c);
    }

    private void trimTrailing() {
        int n = cells.size();
        while (n > 0 && cells.get(n - 1) == null) n--;
        while (cells.size() > n) cells.remove(cells.size() - 1);
    }

    private int used() { int n = 0; for (Cell c : cells) if (c != null) n++; return n; }

    synchronized int size() { return cells.size(); }
    synchronized Cell cellAt(int i) { return i >= 0 && i < cells.size() ? cells.get(i) : null; }
    synchronized Set<String> hidden() { return new LinkedHashSet<>(hidden); }
    synchronized int hiddenCount() { return hidden.size(); }
    synchronized boolean isFixed() { return fixed; }

    // ------------------------------------------------------------------ edit session (fixed slots)

    /** Edit mode on: trailing empty slots are kept. Off: trailing empty pages are dropped (interior gaps stay). */
    synchronized void setEditing(boolean on) {
        if (editing == on) return;
        editing = on;
        if (!on) { int before = cells.size(); trimTrailing(); if (cells.size() != before) { save(); Lx.i("DrawerOrder: dropped " + (before - cells.size()) + " trailing empty slots"); } }
    }

    /** Pages currently used (at least one, counting trailing empty slots while editing). */
    synchronized int pages() { int p = Math.max(1, pageSize); return Math.max(1, (cells.size() + p - 1) / p); }

    /** True when the last page holds at least one cell (so adding another page makes sense). */
    synchronized boolean lastPageUsed() {
        int p = Math.max(1, pageSize);
        int start = (pages() - 1) * p;
        for (int i = start; i < cells.size(); i++) if (cells.get(i) != null) return true;
        return cells.isEmpty();
    }

    /** Append one empty page (fixed-slot editing: drag past the last page). Returns the new page index or -1. */
    synchronized int addPage() {
        if (!fixed) return -1;
        int p = Math.max(1, pageSize);
        int target = pages() * p;                        // first slot of the new page
        if (cells.size() == 0) target = p;               // an empty drawer: page 0 exists, add page 1
        while (cells.size() < target + p) cells.add(null);
        Lx.i("DrawerOrder: added page " + (pages() - 1) + " (" + cells.size() + " slots)");
        return pages() - 1;
    }

    // ------------------------------------------------------------------ mutations (all persist)

    /**
     * Move the cell at {@code from} to slot {@code to}.
     * Compact: remove + insert (the cells in between shift). Fixed: the origin becomes a gap; an empty target takes the
     * cell, an occupied target pushes the following cells on that page one slot towards the next gap (One UI style);
     * when the page has no gap the move is refused.
     */
    synchronized boolean move(int from, int to) {
        if (from < 0 || from >= cells.size() || cells.get(from) == null) return false;
        if (!fixed) {
            to = Math.max(0, Math.min(cells.size() - 1, to));
            if (from == to) return false;
            Cell c = cells.remove(from);
            cells.add(to, c);
            save();
            return true;
        }
        if (to < 0 || from == to) return false;
        int p = Math.max(1, pageSize);
        while (cells.size() <= to) cells.add(null);
        Cell c = cells.get(from);
        cells.set(from, null);
        if (cells.get(to) == null) { cells.set(to, c); save(); return true; }
        int pageStart = (to / p) * p, pageEnd = pageStart + p;
        while (cells.size() < pageEnd) cells.add(null);
        int gap = -1;
        for (int i = to + 1; i < pageEnd; i++) if (cells.get(i) == null) { gap = i; break; }
        if (gap >= 0) {                                                         // push the run to..gap-1 right by one
            for (int i = gap; i > to; i--) cells.set(i, cells.get(i - 1));
            cells.set(to, c);
            save();
            return true;
        }
        for (int i = to - 1; i >= pageStart; i--) if (cells.get(i) == null) { gap = i; break; }
        if (gap >= 0) {                                                         // push the run gap+1..to left by one
            for (int i = gap; i < to; i++) cells.set(i, cells.get(i + 1));
            cells.set(to, c);
            save();
            return true;
        }
        cells.set(from, c);                                                     // page full: refuse
        return false;
    }

    /**
     * Drop the app cell {@code src} onto cell {@code dst}: creates a folder in dst's place (dst is an app) or adds the app
     * to dst (dst is a folder). Folders cannot be dropped onto anything. Returns the resulting folder or null.
     */
    synchronized Folder combine(int src, int dst) {
        if (src == dst || src < 0 || dst < 0 || src >= cells.size() || dst >= cells.size()) return null;
        Cell s = cells.get(src), d = cells.get(dst);
        if (s == null || d == null || s.isFolder()) return null;
        Folder f;
        if (d.isFolder()) {
            f = d.folder;
            if (!f.apps.contains(s.app)) f.apps.add(s.app);
        } else {
            f = new Folder("g" + (nextFolderId++), DEFAULT_FOLDER_NAME);
            f.apps.add(d.app);
            f.apps.add(s.app);
            cells.set(dst, new Cell(f));
        }
        clearSlot(src);
        save();
        return f;
    }

    /** Empty a slot: compact mode removes it, fixed mode leaves a gap. */
    private void clearSlot(int idx) {
        if (idx < 0 || idx >= cells.size()) return;
        if (fixed) cells.set(idx, null); else cells.remove(idx);
    }

    /** Put a cell right after {@code idx}: compact inserts, fixed uses the first gap after idx (appending when none). */
    private void placeAfter(int idx, Cell c) {
        if (!fixed) { cells.add(Math.min(cells.size(), idx + 1), c); return; }
        for (int i = idx + 1; i < cells.size(); i++) if (cells.get(i) == null) { cells.set(i, c); return; }
        cells.add(c);
    }

    synchronized void rename(Folder f, String name) {
        if (f == null) return;
        f.name = name == null || name.trim().isEmpty() ? DEFAULT_FOLDER_NAME : name.trim();
        save();
    }

    /** Per-folder look override (-1 = follow the global setting). */
    synchronized void setStyle(Folder f, int shape, int color) {
        if (f == null) return;
        f.shape = shape; f.color = color;
        save();
    }

    /** Take one app out of a folder; it lands right after the folder (or in the folder's place when it was the last one). */
    synchronized boolean removeFromFolder(Folder f, String app) {
        int idx = indexOf(f);
        if (idx < 0 || !f.apps.remove(app)) return false;
        if (f.apps.isEmpty()) cells.set(idx, new Cell(app));
        else placeAfter(idx, new Cell(app));
        save();
        return true;
    }

    /** Move an app cell into a folder (the "+" of the One UI folder view); its old slot is emptied. */
    synchronized boolean addToFolder(Folder f, String app) {
        if (f == null || app == null || f.apps.contains(app) || indexOf(f) < 0) return false;
        int idx = indexOfApp(app);
        if (idx < 0) return false;
        f.apps.add(app);
        clearSlot(idx);
        save();
        return true;
    }

    /** Remove a folder, spreading its apps in place (fixed: first app in the folder's slot, the rest into the next gaps). */
    synchronized boolean dissolve(Folder f) {
        int idx = indexOf(f);
        if (idx < 0) return false;
        if (f.apps.isEmpty()) { clearSlot(idx); save(); return true; }
        cells.set(idx, new Cell(f.apps.get(0)));
        if (fixed) { int at = idx; for (int i = 1; i < f.apps.size(); i++) { Cell c = new Cell(f.apps.get(i)); placeAfter(at, c); at = cells.indexOf(c); } }
        else for (int i = 1; i < f.apps.size(); i++) cells.add(idx + i, new Cell(f.apps.get(i)));
        save();
        return true;
    }

    synchronized void hide(String app) {
        if (app == null) return;
        hidden.add(app);
        for (int i = cells.size() - 1; i >= 0; i--) {
            Cell c = cells.get(i);
            if (c == null) continue;
            if (c.isFolder()) { c.folder.apps.remove(app); if (c.folder.apps.isEmpty()) clearSlot(i); }
            else if (app.equals(c.app)) clearSlot(i);
        }
        save();
    }

    synchronized void unhide(String app) {
        if (app == null || !hidden.remove(app)) return;
        appendFree(cells, new Cell(app));
        save();
    }

    synchronized int indexOf(Folder f) {
        for (int i = 0; i < cells.size(); i++) { Cell c = cells.get(i); if (c != null && c.folder == f) return i; }
        return -1;
    }

    synchronized int indexOfApp(String app) {
        for (int i = 0; i < cells.size(); i++) { Cell c = cells.get(i); if (c != null && app != null && app.equals(c.app)) return i; }
        return -1;
    }
}
