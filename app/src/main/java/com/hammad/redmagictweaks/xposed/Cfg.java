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
        try { XSharedPreferences x = p(); return !off(x, key) && x.getBoolean(key, false); } catch (Throwable t) { return false; }
    }

    public static int choice(String key) {
        try {
            XSharedPreferences x = p();
            if (off(x, key)) return 0;
            int v = x.getInt(key, 0);
            return v > 0 && hiddenOption(key, v) ? 0 : v;
        } catch (Throwable t) { return 0; }
    }

    /** An option the build flavour does not offer (the public build's One UI looks) reads as stock. */
    private static boolean hiddenOption(String key, int v) {
        try {
            Features.Feature f = feature(key);
            return f != null && !f.offered(v);
        } catch (Throwable t) { return false; }
    }

    private static java.util.Map<String, Features.Feature> byKey;
    private static synchronized Features.Feature feature(String key) {
        if (byKey == null) {
            java.util.Map<String, Features.Feature> m = new java.util.HashMap<>();
            for (Features.Section s : Features.SECTIONS) for (Features.Feature f : s.features) m.put(f.key, f);
            byKey = m;
        }
        return byKey.get(key);
    }

    /** A choice's typed value (Features.Num) when one is in use, else {@code fallback} (the preset's value). */
    public static int num(String key, int fallback) {
        try {
            XSharedPreferences x = p();
            if (off(x, key) || !x.getBoolean(key + Features.CUSTOM, false)) return fallback;
            return x.getInt(key + Features.VALUE, fallback);
        } catch (Throwable t) { return fallback; }
    }

    /** True when a typed value is in use for the choice. */
    public static boolean hasNum(String key) {
        try { XSharedPreferences x = p(); return !off(x, key) && x.getBoolean(key + Features.CUSTOM, false); } catch (Throwable t) { return false; }
    }

    /**
     * The custom colour of a colour option (ColorPick): {c1}, {c1, c2} for a gradient, or null while a preset is in use
     * (the choice then holds the preset; with a custom colour it holds the nearest preset).
     */
    public static int[] color(String key) {
        try {
            XSharedPreferences x = p();
            if (off(x, key) || !x.getBoolean(key + com.hammad.redmagictweaks.ColorPick.ON, false)) return null;
            int c1 = x.getInt(key + com.hammad.redmagictweaks.ColorPick.C1, 0xFFFFFFFF);
            if (x.getBoolean(key + com.hammad.redmagictweaks.ColorPick.GRAD, false)) return new int[]{c1, x.getInt(key + com.hammad.redmagictweaks.ColorPick.C2, c1)};
            return new int[]{c1};
        } catch (Throwable t) { return null; }
    }

    /** {@link #color(String)} as {start, end} (a plain colour twice), or null. */
    public static int[] colorPair(String key) { return com.hammad.redmagictweaks.ColorPick.pair(color(key)); }

    /** Same values as an int, for change checks (0 = no custom colour). */
    public static int colorHash(int[] c) { return c == null ? 0 : c.length > 1 ? (c[0] * 31 + c[1]) | 1 : c[0] | 1; }

    /** China-only tweaks are off while "Global tweaks only" is on (always in the public build): hidden rows never run. */
    private static boolean off(XSharedPreferences x, String key) {
        try {
            if (com.hammad.redmagictweaks.Region.leftOut(key)) return true;      // not in the public build
            if (!com.hammad.redmagictweaks.Region.cnOnly(key)) return false;
            return com.hammad.redmagictweaks.FlavorBits.globalOnly() || x.getBoolean(com.hammad.redmagictweaks.Region.GLOBAL_ONLY, false);
        } catch (Throwable t) { return false; }
    }

    /** A text setting (an icon pack's package name, a typed value…), or null. */
    public static String str(String key) {
        try { return p().getString(key, null); } catch (Throwable t) { return null; }
    }

    public static String dump() {
        try { return String.valueOf(p().getAll()); } catch (Throwable t) { return "<unreadable: " + t + ">"; }
    }
}
