package com.hammad.redmagictweaks;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Changes that still need a restart to show up, for the one restart bar at the bottom of every screen. A change is
 * remembered as "s|key" (System UI), "r|key" (reboot) or "a|package|key" (that app); restarting clears its entries.
 */
public final class Pending {
    private Pending() {}

    private static final String KEY = "pending";

    private static Set<String> get(Context c) { return new HashSet<>(UiPrefs.p(c).getStringSet(KEY, new HashSet<>())); }
    private static void put(Context c, Set<String> s) { UiPrefs.p(c).edit().putStringSet(KEY, s).apply(); }

    /** Remember that {@code it} changed. */
    public static void add(Context c, Catalog.Item it) {
        if (it == null) return;
        Set<String> s = get(c);
        switch (it.restart) {
            case SYSTEM_UI: s.add("s|" + it.key()); break;
            case REBOOT: s.add("r|" + it.key()); break;
            case APP: for (String p : it.packages()) s.add("a|" + p + "|" + it.key()); break;
            default: return;
        }
        put(c, s);
    }

    /** Everything changed at once (a look or a layout applied): System UI and every hooked app, plus a reboot. */
    public static void addEverything(Context c) {
        Set<String> s = get(c);
        s.add("s|*");
        s.add("r|*");
        for (String p : Catalog.allPackages()) if (!"com.android.systemui".equals(p)) s.add("a|" + p + "|*");
        put(c, s);
    }

    /** Marker of a module update the running apps have not loaded yet. */
    public static final String UPDATE = "_update";

    /** These running apps still run an older build of the module: they need a restart to load the installed one. */
    public static void addUpdate(Context c, List<String> packages) {
        Set<String> s = get(c);
        for (String p : packages) {
            if ("com.android.systemui".equals(p)) s.add("s|" + UPDATE);
            else s.add("a|" + p + "|" + UPDATE);
        }
        put(c, s);
    }

    /** True when the waiting restarts include loading a module update. */
    public static boolean update(Context c) {
        for (String e : get(c)) if (e.endsWith("|" + UPDATE)) return true;
        return false;
    }

    public static int systemUi(Context c) { return count(c, "s|"); }
    public static int reboot(Context c) { return count(c, "r|"); }

    private static int count(Context c, String prefix) {
        int n = 0;
        for (String e : get(c)) if (e.startsWith(prefix)) n++;
        return n;
    }

    /** Apps (not System UI) waiting for a restart. */
    public static List<String> apps(Context c) {
        LinkedHashSet<String> l = new LinkedHashSet<>();
        for (String e : get(c)) if (e.startsWith("a|")) l.add(e.split("\\|")[1]);
        return new ArrayList<>(l);
    }

    /** Settings waiting for any app restart. */
    public static int appChanges(Context c) {
        Set<String> keys = new HashSet<>();
        for (String e : get(c)) if (e.startsWith("a|")) keys.add(e.substring(e.lastIndexOf('|') + 1));
        return keys.size();
    }

    public static boolean any(Context c) { return !get(c).isEmpty(); }

    public static void clearSystemUi(Context c) { clear(c, "s|"); }
    public static void clearApps(Context c) { clear(c, "a|"); }
    /** A reboot (or soft reboot) restarts everything. */
    public static void clearAll(Context c) { put(c, new HashSet<>()); }

    private static void clear(Context c, String prefix) {
        Set<String> s = get(c);
        s.removeIf(e -> e.startsWith(prefix));
        put(c, s);
    }

    /** Listen for changes of the pending set (the bar redraws). */
    public static void watch(Context c, SharedPreferences.OnSharedPreferenceChangeListener l) { UiPrefs.p(c).registerOnSharedPreferenceChangeListener(l); }
    public static void unwatch(Context c, SharedPreferences.OnSharedPreferenceChangeListener l) { UiPrefs.p(c).unregisterOnSharedPreferenceChangeListener(l); }
}
