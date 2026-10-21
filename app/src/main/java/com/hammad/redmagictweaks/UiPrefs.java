package com.hammad.redmagictweaks;

import android.content.Context;
import android.content.SharedPreferences;

/** App-only presentation settings (not shared with the hooks). */
public final class UiPrefs {
    private UiPrefs() {}
    static SharedPreferences p(Context c) { return c.getSharedPreferences("ui", Context.MODE_PRIVATE); }

    /** Advanced mode: fine-tuning rows (nudges, offsets, rotation, the stock lock clock) and the Advanced category. */
    public static boolean advanced(Context c) { return p(c).getBoolean("advanced", false); }
    public static void setAdvanced(Context c, boolean v) { p(c).edit().putBoolean("advanced", v).apply(); }

    /** The three first-run steps were shown. */
    public static boolean firstRunDone(Context c) { return p(c).getBoolean("first_run_done", false); }
    public static void setFirstRunDone(Context c) { p(c).edit().putBoolean("first_run_done", true).apply(); }

    /** The Advanced category's warning was accepted once. */
    public static boolean advancedWarned(Context c) { return p(c).getBoolean("advanced_warned", false); }
    public static void setAdvancedWarned(Context c) { p(c).edit().putBoolean("advanced_warned", true).apply(); }
}
