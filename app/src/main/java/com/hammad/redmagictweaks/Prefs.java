package com.hammad.redmagictweaks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

/** World-readable settings file (LSPosed redirects it so hooked apps can read it via XSharedPreferences). */
public final class Prefs {
    private Prefs() {}
    private static SharedPreferences sp;
    private static boolean worldReadable;

    @SuppressLint("WorldReadableFiles")
    public static synchronized SharedPreferences get(Context c) {
        if (sp == null) {
            Context app = c.getApplicationContext();
            try {
                //noinspection deprecation
                sp = app.getSharedPreferences(Features.PREFS, Context.MODE_WORLD_READABLE);
                worldReadable = true;
            } catch (SecurityException e) {
                sp = app.getSharedPreferences(Features.PREFS, Context.MODE_PRIVATE);
                worldReadable = false;
            }
        }
        return sp;
    }

    public static boolean isWorldReadable(Context c) { get(c); return worldReadable; }

    /** Keys whose hooks were replaced by the nubia-ly port (v0.3). A stale value would make the old and new hooks fight. */
    private static final String[] RETIRED = {"cos_qs_shape", "cos_qs_shape_large", "cos_qs_style", "cos_brightness_style", "cos_notif_style",
            "cos_notif_opacity", "cos_power_style", "cos_volume_style", "sb_qs_cols", "sb_qs_rows", "sb_notif_max"};
    public static void migrate(Context c) {
        SharedPreferences p = get(c);
        SharedPreferences.Editor e = p.edit(); boolean dirty = false;
        for (String k : RETIRED) if (p.contains(k)) { e.remove(k); dirty = true; }
        if (dirty) e.commit();
    }

    public static boolean on(Context c, String key) { return get(c).getBoolean(key, false); }
    public static int choice(Context c, String key) { return get(c).getInt(key, 0); }
    public static void set(Context c, String key, boolean v) { get(c).edit().putBoolean(key, v).commit(); }
    public static void set(Context c, String key, int v) { get(c).edit().putInt(key, v).commit(); }
    public static String str(Context c, String key) { return get(c).getString(key, null); }
    /** null removes the setting. */
    public static void setStr(Context c, String key, String v) {
        if (v == null) get(c).edit().remove(key).commit(); else get(c).edit().putString(key, v).commit();
    }

    public static int enabledCount(Context c, Features.Section s) {
        int n = 0;
        for (Features.Feature f : s.features) {
            if (f.type == Features.Type.ACTION) continue;
            if (f.type == Features.Type.SWITCH ? on(c, f.key) : choice(c, f.key) > 0 || on(c, f.key + Features.CUSTOM)) n++;
        }
        return n;
    }
}
