package com.hammad.redmagictweaks.xposed;

import com.hammad.redmagictweaks.Features;

import de.robv.android.xposed.XSharedPreferences;

/** Read-only view of the module settings inside hooked processes. */
public final class Cfg {
    private Cfg() {}
    private static XSharedPreferences prefs;

    private static XSharedPreferences p() {
        if (prefs == null) {
            prefs = new XSharedPreferences(Features.PKG, Features.PREFS);
            prefs.makeWorldReadable();
        } else {
            prefs.reload();
        }
        return prefs;
    }

    public static boolean on(String key) {
        try { return p().getBoolean(key, false); } catch (Throwable t) { return false; }
    }

    public static int choice(String key) {
        try { return p().getInt(key, 0); } catch (Throwable t) { return 0; }
    }

    public static String dump() {
        try { return String.valueOf(p().getAll()); } catch (Throwable t) { return "<unreadable: " + t + ">"; }
    }
}
