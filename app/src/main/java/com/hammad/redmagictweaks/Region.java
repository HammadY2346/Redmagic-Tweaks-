package com.hammad.redmagictweaks;

import android.content.Context;
import android.os.Build;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * China-only tweaks. A static check of every hook target against the global (EU 11.0.4) firmware (21 Oct 2026,
 * docs/global-a16-check.md) found these targets missing there: the app is not on global firmware (package installer and
 * permission controller are Google's, Phone Manager, Magic Voice, screen cast) or the code differs (the power-saving
 * refresh-rate option, the readable-number fix for China's hollow battery number). The battery icon restyle works on
 * both: the global battery layout is an older one, read through fallbacks (CosmeticsHooks.showMode / tintOf). Everything else hooks code that is the same on both.
 *
 * "Global only" hides these rows and the hooks skip them (Cfg reads them as off), so nothing hidden keeps running.
 * RM Tweaks covers both: it follows the firmware it runs on (globalFirmware) unless the menu switch was set by hand,
 * so on a global phone it shows every tweak that works there and on China firmware all of them. The public build
 * (RedMagic Tweaks) is always global only. Re-checked 22 Oct 2026 with Game Lab speed (same toy classes on global)
 * and volume boost (plain Android audio APIs). A build can add its own China-only keys (FlavorBits.cnOnlyKeys).
 */
public final class Region {
    private Region() {}

    /** Shared setting the hooks read too (Cfg): 1 = China-only tweaks are off and hidden. */
    public static final String GLOBAL_ONLY = "ui_global_only";
    /** UI-only: the user picked a mode by hand (otherwise it follows the firmware). */
    private static final String PICKED = "region_picked";

    private static final Set<String> CN_SECTIONS = new HashSet<>(Arrays.asList("installer"));
    private static final Set<String> CN_KEYS = new HashSet<>(Arrays.asList(
            "set_any_launcher",                                     // permission controller: Google's on global
            "mv_calls",                                             // Magic Voice: not on global
            "cast_high_fps", "cast_all_features",                   // screen cast (touping / Lelink): not on global
            "hs_no_ads", "hs_landscape",                            // Phone Manager (heartyservice): not on global
            "bat_refresh_rate",                                     // FeatureConfig.isHideRefreshRate: not on global
            "fw_google_intl",                                       // cn.google.services is only on China firmware
            "cos_battery_stock_readable"                            // fixes the hollow number that only China firmware draws
    ));
    private static final Set<String> SECTION_KEYS = new HashSet<>();
    static {
        CN_KEYS.addAll(FlavorBits.cnOnlyKeys());
        for (Features.Section s : Features.SECTIONS) if (CN_SECTIONS.contains(s.id)) for (Features.Feature f : s.features) SECTION_KEYS.add(f.key);
    }

    /** True when the tweak only works on China firmware. */
    public static boolean cnOnly(String key) {
        if (key == null) return false;
        if (CN_KEYS.contains(key) || SECTION_KEYS.contains(key)) return true;
        // derived settings (custom values) follow their setting
        int cut = key.endsWith(Features.CUSTOM) || key.endsWith(Features.VALUE) ? key.length() - 2 : -1;
        return cut > 0 && (CN_KEYS.contains(key.substring(0, cut)) || SECTION_KEYS.contains(key.substring(0, cut)));
    }

    /** True on global (non-China) firmware: EU / global builds carry a region in the product name or a non-Chinese default locale. */
    public static boolean globalFirmware() {
        String prod = String.valueOf(Build.PRODUCT).toUpperCase(Locale.ROOT);
        int dash = prod.lastIndexOf('-');
        if (dash > 0 && Arrays.asList("EEA", "EU", "UN", "GLOBAL", "ROW", "IN", "JP", "RU", "KR", "TW").contains(prod.substring(dash + 1))) return true;
        String disp = String.valueOf(Build.DISPLAY).toUpperCase(Locale.ROOT);
        if (disp.contains("_EU") || disp.contains("_GLOBAL") || disp.contains("_UN") || disp.contains("_ROW") || disp.contains("_EEA")) return true;
        String loc = prop("ro.product.locale");
        return loc != null && !loc.isEmpty() && !loc.toLowerCase(Locale.ROOT).startsWith("zh");
    }

    /** China-only tweaks are hidden (and off): always in the public build, else the user's choice (default: by firmware). */
    public static boolean globalOnly(Context c) {
        if (FlavorBits.globalOnly()) return true;
        if (UiPrefs.p(c).getBoolean(PICKED, false)) return Prefs.on(c, GLOBAL_ONLY);
        return globalFirmware();
    }

    public static void setGlobalOnly(Context c, boolean v) {
        UiPrefs.p(c).edit().putBoolean(PICKED, true).apply();
        Prefs.set(c, GLOBAL_ONLY, v);
    }

    /** Writes the effective mode where the hooks can read it (first start, firmware default). */
    public static void sync(Context c) {
        boolean v = globalOnly(c);
        if (Prefs.on(c, GLOBAL_ONLY) != v) Prefs.set(c, GLOBAL_ONLY, v);
    }

    /** Hidden in the list right now: China-only while global only, or left out of the public build. */
    public static boolean hidden(Context c, String key) { return cnOnly(key) && globalOnly(c) || leftOut(key); }

    /** Left out of the public build (FlavorBits.leftOut), including a typed value's own keys. */
    public static boolean leftOut(String key) {
        if (key == null) return false;
        if (FlavorBits.leftOut(key)) return true;
        int cut = key.endsWith(Features.CUSTOM) || key.endsWith(Features.VALUE) ? key.length() - 2 : -1;
        return cut > 0 && FlavorBits.leftOut(key.substring(0, cut));
    }

    private static String prop(String k) {
        try { return (String) Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, k); }
        catch (Throwable t) { return null; }
    }
}
