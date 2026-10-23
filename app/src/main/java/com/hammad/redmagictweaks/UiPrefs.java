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
    /** Detailed mode: every setting shows its description under its name. */
    public static boolean detailed(Context c) { return p(c).getBoolean("detailed", false); }
    public static void setDetailed(Context c, boolean v) { p(c).edit().putBoolean("detailed", v).apply(); }

    /** "I use One UI Home": the stock launcher and stock recents settings are hidden (they don't run then). */
    public static boolean oneUiHome(Context c) { return p(c).getBoolean("one_ui_home", false); }
    public static void setOneUiHome(Context c, boolean v) { p(c).edit().putBoolean("one_ui_home", v).apply(); }

    /** The three first-run steps were shown. */
    public static boolean firstRunDone(Context c) { return p(c).getBoolean("first_run_done", false); }
    public static void setFirstRunDone(Context c) { p(c).edit().putBoolean("first_run_done", true).apply(); }

    /** The Advanced category's warning was accepted once. */
    public static boolean advancedWarned(Context c) { return p(c).getBoolean("advanced_warned", false); }
    public static void setAdvancedWarned(Context c) { p(c).edit().putBoolean("advanced_warned", true).apply(); }

    // ---- custom colours: the last 8 used, shared by every colour option ----
    public static int[] recentColors(Context c) {
        String s = p(c).getString("recent_colors", "");
        if (s == null || s.isEmpty()) return new int[0];
        String[] parts = s.split(",");
        int[] out = new int[parts.length];
        int n = 0;
        for (String x : parts) { try { out[n++] = (int) Long.parseLong(x.trim(), 16); } catch (NumberFormatException ignored) { } }
        return java.util.Arrays.copyOf(out, n);
    }
    public static void addRecentColor(Context c, int color) {
        int[] old = recentColors(c);
        StringBuilder b = new StringBuilder(Integer.toHexString(color));
        int n = 1;
        for (int o : old) { if (o == color || n >= 8) continue; b.append(',').append(Integer.toHexString(o)); n++; }
        p(c).edit().putString("recent_colors", b.toString()).apply();
    }

    // ---- "NEW" badges (WhatsNew) ----
    /** 0 = until seen once (default), 1 = 3 days, 2 = 7 days, 3 = 30 days after first seen, 4 = off. */
    public static int newBadges(Context c) { return p(c).getInt("new_badges", 0); }
    public static void setNewBadges(Context c, int v) { p(c).edit().putInt("new_badges", v).apply(); }
    /** When a new setting was first on screen (ms), or 0. */
    public static long firstSeen(Context c, String key) { return seen(c).getLong(key, 0L); }
    public static void markSeen(Context c, String key) {
        SharedPreferences s = seen(c);
        if (s.getLong(key, 0L) == 0L) s.edit().putLong(key, System.currentTimeMillis()).apply();
    }
    public static void markAllSeen(Context c, java.util.Collection<String> keys) {
        SharedPreferences s = seen(c);
        SharedPreferences.Editor e = s.edit();
        long now = System.currentTimeMillis();
        for (String k : keys) e.putLong(k, now - 400L * 86400000L);   // seen long ago: no badge in any mode
        e.apply();
    }
    private static SharedPreferences seen(Context c) { return c.getSharedPreferences("ui_seen", Context.MODE_PRIVATE); }
}
