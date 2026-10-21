package com.hammad.redmagictweaks;

import android.content.Context;
import android.content.SharedPreferences;

/** App-only presentation settings (not shared with the hooks). */
public final class UiPrefs {
    private UiPrefs() {}
    private static SharedPreferences p(Context c) { return c.getSharedPreferences("ui", Context.MODE_PRIVATE); }
    /** Clean layout: compact cards without descriptions (long-press a card to read it), no section hints, slimmer home. */
    public static boolean clean(Context c) { return p(c).getBoolean("clean", false); }
    public static void setClean(Context c, boolean v) { p(c).edit().putBoolean("clean", v).apply(); }
    /** Home screen grouped by topic (Battery & charging, Lock screen, ...) instead of by the app each tweak patches. */
    public static boolean byTopic(Context c) { return p(c).getBoolean("by_topic", false); }
    public static void setByTopic(Context c, boolean v) { p(c).edit().putBoolean("by_topic", v).apply(); }
}
