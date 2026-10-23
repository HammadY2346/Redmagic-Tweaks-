package com.hammad.redmagictweaks;

import android.app.Activity;

/**
 * What one build flavour adds to the shared code. The public build (RedMagic Tweaks) adds no tweaks and leaves some out
 * (see leftOut, hiddenChoice); the private build of the author's own repo has its own copy of this class.
 */
public final class FlavorBits {
    private FlavorBits() {}

    /** Extra rows at the end of a section, or null. */
    public static Features.Feature[] extraFeatures(String sectionId) { return null; }

    /** Extra hooks for a hooked package. */
    public static void installHooks(String pkg, ClassLoader cl) { }

    /** Button text of an action row this flavour handles. */
    public static String actionLabel(String key) { return "Open"; }

    /** Runs an action row this flavour handles; false = not ours. */
    public static boolean runAction(Activity activity, String key) { return false; }

    /** The public build (RedMagic Tweaks). */
    public static boolean publicBuild() { return true; }

    /** China-only keys of this build's own tweaks (Region): none. */
    public static java.util.List<String> cnOnlyKeys() { return java.util.Collections.emptyList(); }

    /**
     * Tweaks the public build leaves out. They stay in RM Tweaks. Hidden rows read as off in the hooks too (Cfg).
     *  - 22 Oct 2026: risky for people who don't know what they do, or untested outside A's own phone.
     *  - 23 Oct 2026, v1.1 (A): the One UI layouts go (the one-tap One UI 8 look, the One UI Control Center layout,
     *    lock screen clock, AOD clock, volume panel, power menu and font, the One UI drawer layout); the customizations
     *    stay. No tweak added after v1.0 comes in, except the lock screen clock / date colours and the Control Center
     *    colours.
     */
    public static boolean leftOut(String key) {
        if (key == null) return false;
        switch (key) {
            case "sb_usb_debug_auto_allow":   // anyone with a cable gets a shell
            case "fw_untrusted_touch":        // overlays can trick taps again
            case "fw_no_72h_lock":            // weakens the lock screen
            case "game_launch_boost":         // experimental, unmeasured
            case "game_launch_boost_games":
            case "game_assist_defer":         // experimental, unmeasured
            case "game_gfrc_all":             // frame interpolation for every game: untested on other phones
            case "game_gfrc_target":
            case "game_ai_speed":             // Game Lab: experimental (works on global firmware too, checked 22 Oct)
            case "fw_volume_boost":           // can damage speakers (works on global firmware too, checked 22 Oct)
            // v1.1: One UI layouts
            case "preset_one_ui": case "preset_one_ui_launcher":
            case "qs_cc_layout": case "qs_cc_oneui_big_clock": case "qs_cc_oneui_no_header": case "qs_cc_oneui_no_media":
            case "qs_cc_oneui_no_shortcuts":
            case "lock_oneui_clock": case "lock_oneui_face": case "lock_oneui_size": case "lock_oneui_position":
            case "lock_oneui_tiles": case "lock_oneui_shortcuts": case "lock_oneui_lock_icon":
            case "aod_oneui_clock": case "aod_oneui_face": case "aod_oneui_size":
            case "vol_oneui8":
            case "pwr_oneui8": case "pwr_oneui_size": case "pwr_oneui_shape": case "pwr_oneui_colors": case "pwr_oneui_labels":
            case "cos_sysui_font":
            case "launcher_drawer_custom_order": case "launcher_drawer_fixed_slots":
            // v1.1: added after v1.0, not in the public build
            case "aod_clock_position": case "bat_health": case "bat_separation_100": case "fw_google_intl": case "game_fullscreen": case "game_view": case "game_view_only_game": case "game_view_gs_stretch": case "game_view_gs_custom": case "game_view_strength": case "game_view_games":
            case "launcher_folder_cols": case "launcher_folder_rows": case "launcher_hide_labels": case "launcher_icon_pack":
            case "launcher_icon_shape": case "launcher_label_size":
            case "lock_video": case "lock_video_on": case "lock_video_when": case "lock_video_end": case "lock_video_sound":
            case "lock_video_fit":
                return true;
            default:
                return false;
        }
    }

    /** v1.1: options inside a setting that are One UI looks; index 0 (stock) is never hidden. */
    private static final java.util.Set<String> HIDDEN_LABELS = new java.util.HashSet<>(java.util.Arrays.asList(
            "One UI 8", "One UI", "One UI 8 (white on)", "One UI 8 (rounded fill)", "One UI (full screen)", "One UI style (Inter)"));
    /** v1.1: plain names for shapes and positions that were named after One UI. */
    private static final java.util.Map<String, String> RENAMED = new java.util.HashMap<>();
    static {
        RENAMED.put("Pill (One UI)", "Pill");
        RENAMED.put("One UI pill", "Full pill");
        RENAMED.put("Default (One UI 40 dp, RedMagic 20 dp)", "Default (20 dp)");
        RENAMED.put("Default (One UI 80 dp taller, RedMagic stock)", "Default (stock height)");
        RENAMED.put("Default (One UI inside, RedMagic under)", "Default (under the bar)");
        RENAMED.put("Between large and small tiles (stock, One UI order)", "Between large and small tiles (stock)");
        RENAMED.put("Horizontal bars under the big tiles (One UI)", "Horizontal bars under the big tiles");
        RENAMED.put("Bottom (One UI)", "Bottom");
        RENAMED.put("Icon in the middle (One UI)", "Icon in the middle");
        RENAMED.put("\"Close all\" pill (One UI)", "\"Close all\" pill");
        RENAMED.put("One UI · 26 dp", "Rounded · 26 dp");
        RENAMED.put("26 dp (One UI)", "26 dp");
        RENAMED.put("One UI blue", "Light blue");
    }

    /** True: option {@code i} of setting {@code key} is not offered (and reads as stock in the hooks). */
    public static boolean hiddenChoice(String key, int i, String label) {
        if (i <= 0 || label == null) return false;
        if ("launcher_folder_shape".equals(key)) return false;            // its "One UI" is a plain shape (renamed)
        return HIDDEN_LABELS.contains(label);
    }

    /** The option names shown (a copy when something is renamed; the hooks use indexes, never names). */
    public static String[] labels(String key, String[] choices) {
        if (choices == null) return null;
        String[] out = null;
        for (int i = 0; i < choices.length; i++) {
            String n = "launcher_folder_shape".equals(key) && "One UI".equals(choices[i]) ? "Rounded squircle" : RENAMED.get(choices[i]);
            if (n == null) continue;
            if (out == null) out = choices.clone();
            out[i] = n;
        }
        return out == null ? choices : out;
    }

    /** A setting's description without the parts about the One UI layouts this build leaves out. */
    public static String desc(String key, String desc) {
        if (desc == null || !desc.contains("One UI")) return desc;
        String d = desc.replaceAll("\\s*\\([^()]*One UI[^()]*\\)", "");
        StringBuilder b = new StringBuilder();
        for (String part : d.split("(?<=[.!?])\\s+")) {
            if (part.contains("One UI")) continue;
            if (b.length() > 0) b.append(' ');
            b.append(part);
        }
        String out = b.toString().trim();
        return out.isEmpty() ? desc.replace("One UI 8", "this").replace("One UI", "this") : out;
    }

    /** Subtitle of a home-screen category. */
    public static String categorySubtitle(String id, String subtitle) {
        if ("looks".equals(id)) return "Stock look and your saved layouts";
        if ("lockscreen".equals(id)) return "Clock, widgets and always-on display";
        return subtitle;
    }

    /**
     * v1.1, once after updating from v1.0: the settings this build left out and the One UI options it hides go back to
     * stock (the hooks already treat them as off), and the restart bar asks for System UI and the hooked apps.
     */
    public static void onStart(final Activity a) {
        android.content.SharedPreferences mark = a.getSharedPreferences("public_upgrade", 0);
        if (mark.getBoolean("v11_cleanup", false)) return;
        mark.edit().putBoolean("v11_cleanup", true).apply();
        android.content.SharedPreferences sp = Prefs.get(a);
        java.util.Map<String, ?> all = sp.getAll();
        android.content.SharedPreferences.Editor e = sp.edit();
        int n = 0;
        for (String k : all.keySet()) {
            int us = k.lastIndexOf('_');
            if (leftOut(k) || (us > 0 && leftOut(k.substring(0, us)))) { e.remove(k); n++; }
        }
        for (Catalog.Category cat : Catalog.all()) for (Catalog.Group g : cat.groups) for (Catalog.Item it : g.items) {
            Features.Feature f = it.feature;
            if (f == null || f.type != Features.Type.CHOICE || f.choices == null) continue;
            Object v = all.get(f.key);
            if (v instanceof Integer && (Integer) v > 0 && (Integer) v < f.choices.length && hiddenChoice(f.key, (Integer) v, f.choices[(Integer) v])) {
                e.putInt(f.key, 0);
                n++;
            }
        }
        if (n == 0) return;
        e.commit();
        Pending.addEverything(a);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(a)
                .setTitle("RedMagic Tweaks 1.1")
                .setMessage("The One UI layouts are no longer part of RedMagic Tweaks (the one-tap One UI 8 look, the One UI "
                        + "Control Center, lock screen and AOD clocks, volume panel, power menu and font). Your other settings stay. "
                        + "Restart System UI and the apps with the bar at the bottom to go back to the stock look.")
                .setPositiveButton("OK", null)
                .show();
    }

    /** True: China-only tweaks are left out (the public build works the same on China and global firmware). */
    public static boolean globalOnly() { return true; }

    /** A short About page {repository link, "made and tested on" line, credited name}, or null for the full one. */
    public static String[] shortAbout() { return null; }
}
